package org.ysu.ckqaback.index;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 尾随 GraphRAG 官方 prompt-tune 日志文件 {@code graphrag/reports/prompt-tuning.log}，
 * 实时把"阶段切换"和"日志行"反馈给上层。
 * <p>
 * 日志格式（5 月 8 日和 5 月 14 日两次实跑印证稳定）：
 * <pre>
 * 2026-05-14 13:45:39.0949 - INFO - graphrag.api.prompt_tune - Chunking documents...
 * 2026-05-14 13:46:34.0561 - INFO - graphrag.api.prompt_tune - Generating entity relationship examples...
 * </pre>
 * 我们只关注 logger 名包含 {@code graphrag.api.prompt_tune} 或 {@code graphrag.cli.prompt_tune}
 * 的 INFO 行；其它行喂到 onLog 但不会切换阶段。
 * <p>
 * <strong>线程模型</strong>：本类的 {@link #run()} 由调用方在独立线程里跑，自己阻塞 200ms
 * 轮询；外部通过 {@link #stop()} 通知它退出。
 */
public class PromptTuneLogTailer implements Runnable {

    private static final Logger log = LoggerFactory.getLogger(PromptTuneLogTailer.class);

    /**
     * graphrag 输出格式：{@code <date> <time> - <LEVEL> - <logger> - <message>}。
     * 由于 timestamp 内部含有 "-"，分隔不能用 {@code [^-]}；改成显式匹配
     * "{@code  - }"（两侧带空格）。
     */
    private static final Pattern LOG_LINE_PATTERN = Pattern.compile(
            "^.+? - (?<level>[A-Z]+) - (?<logger>\\S+) - (?<message>.*)$"
    );

    /**
     * 我们关心的 logger 名前缀。其它（例如 graphrag_llm.middleware.with_logging）忽略。
     */
    private static final String[] RELEVANT_LOGGERS = {
            "graphrag.api.prompt_tune",
            "graphrag.cli.prompt_tune",
    };

    private final Path logFile;
    private final long startOffset;
    private final PhaseListener phaseListener;
    private final LogLineListener logLineListener;
    private final long pollIntervalMillis;
    private final AtomicBoolean stopRequested = new AtomicBoolean(false);

    public PromptTuneLogTailer(
            Path logFile,
            long startOffset,
            PhaseListener phaseListener,
            LogLineListener logLineListener,
            long pollIntervalMillis
    ) {
        this.logFile = logFile;
        this.startOffset = Math.max(startOffset, 0L);
        this.phaseListener = phaseListener == null ? phase -> {} : phaseListener;
        this.logLineListener = logLineListener == null ? line -> {} : logLineListener;
        this.pollIntervalMillis = Math.max(pollIntervalMillis, 50L);
    }

    public void stop() {
        stopRequested.set(true);
    }

    /**
     * 把 startOffset 设置为目标文件当前长度，本类构造时调用。
     * 文件不存在时返回 0（接下来 run() 会等文件出现）。
     */
    public static long currentSize(Path logFile) {
        try {
            return Files.exists(logFile) ? Files.size(logFile) : 0L;
        } catch (IOException exception) {
            log.warn("读取 prompt-tune 日志文件大小失败 {}: {}", logFile, exception.getMessage());
            return 0L;
        }
    }

    /**
     * 解析单行日志：返回 message 部分（未匹配格式时返回空）。
     */
    static Optional<String> parseRelevantMessage(String line) {
        Matcher matcher = LOG_LINE_PATTERN.matcher(line);
        if (!matcher.matches()) {
            return Optional.empty();
        }
        if (!"INFO".equals(matcher.group("level"))) {
            return Optional.empty();
        }
        String logger = matcher.group("logger");
        boolean relevant = false;
        for (String prefix : RELEVANT_LOGGERS) {
            if (logger.startsWith(prefix)) {
                relevant = true;
                break;
            }
        }
        if (!relevant) {
            return Optional.empty();
        }
        return Optional.ofNullable(matcher.group("message"));
    }

    @Override
    public void run() {
        long offset = startOffset;
        StringBuilder pending = new StringBuilder();
        PromptTunePhase lastPhase = null;
        while (!stopRequested.get()) {
            try {
                long currentSize = Files.exists(logFile) ? Files.size(logFile) : 0L;
                if (currentSize < offset) {
                    // 日志被 truncate / 轮转：从头读，但避免回放已经处理过的旧内容造成阶段倒退。
                    offset = currentSize;
                    pending.setLength(0);
                }
                if (currentSize > offset) {
                    try (RandomAccessFile raf = new RandomAccessFile(logFile.toFile(), "r")) {
                        raf.seek(offset);
                        long toRead = currentSize - offset;
                        byte[] buf = new byte[(int) Math.min(toRead, 64 * 1024)];
                        int actual = raf.read(buf);
                        if (actual > 0) {
                            offset += actual;
                            pending.append(new String(buf, 0, actual, StandardCharsets.UTF_8));
                        }
                    }
                    lastPhase = drainPending(pending, lastPhase);
                }
            } catch (IOException exception) {
                log.warn("尾随 prompt-tune 日志失败 {}: {}", logFile, exception.getMessage());
            }
            sleepQuietly(pollIntervalMillis);
        }
        // 退出前最后一次冲刷（防止子进程已经写完最后几行但还没被我们读到）。
        try {
            long currentSize = Files.exists(logFile) ? Files.size(logFile) : 0L;
            if (currentSize > offset) {
                try (RandomAccessFile raf = new RandomAccessFile(logFile.toFile(), "r")) {
                    raf.seek(offset);
                    long toRead = currentSize - offset;
                    byte[] buf = new byte[(int) Math.min(toRead, 64 * 1024)];
                    int actual = raf.read(buf);
                    if (actual > 0) {
                        pending.append(new String(buf, 0, actual, StandardCharsets.UTF_8));
                    }
                }
                drainPending(pending, lastPhase);
            }
        } catch (IOException ignored) {
            // 退出阶段的失败不影响业务
        }
    }

    private PromptTunePhase drainPending(StringBuilder pending, PromptTunePhase lastPhase) {
        int newlineIdx;
        while ((newlineIdx = pending.indexOf("\n")) >= 0) {
            String line = pending.substring(0, newlineIdx);
            pending.delete(0, newlineIdx + 1);
            if (line.endsWith("\r")) {
                line = line.substring(0, line.length() - 1);
            }
            if (line.isBlank()) {
                continue;
            }
            try {
                logLineListener.onLine(line);
            } catch (RuntimeException callbackException) {
                log.warn("prompt-tune 日志行回调失败：{}", callbackException.getMessage());
            }
            Optional<String> message = parseRelevantMessage(line);
            if (message.isEmpty()) {
                continue;
            }
            Optional<PromptTunePhase> phase = PromptTunePhase.match(message.get());
            if (phase.isEmpty()) {
                continue;
            }
            // 只在阶段真正前进时回调；同时不允许回退（防止其它 run 的旧日志干扰）。
            if (lastPhase == null || phase.get().getProgressPercentage() > lastPhase.getProgressPercentage()) {
                lastPhase = phase.get();
                try {
                    phaseListener.onPhase(lastPhase);
                } catch (RuntimeException callbackException) {
                    log.warn("prompt-tune 阶段回调失败：{}", callbackException.getMessage());
                }
            }
        }
        return lastPhase;
    }

    private void sleepQuietly(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            stopRequested.set(true);
        }
    }

    @FunctionalInterface
    public interface PhaseListener {
        void onPhase(PromptTunePhase phase);
    }

    @FunctionalInterface
    public interface LogLineListener {
        void onLine(String line);
    }
}
