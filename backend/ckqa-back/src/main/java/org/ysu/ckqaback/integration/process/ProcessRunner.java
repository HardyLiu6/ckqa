package org.ysu.ckqaback.integration.process;

import org.springframework.beans.factory.DisposableBean;
import org.springframework.stereotype.Component;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.locks.ReentrantLock;

/**
 * 统一执行外部命令并跟踪活跃子进程。
 * <p>stdout/stderr 按行边读边 tee 写入 {@link ProcessContext#getLogFile()}，
 * 因此长任务（如 graphrag index）跑到一半时也能从日志文件读到进度。</p>
 */
@Component
public class ProcessRunner implements DisposableBean {

    private final ConcurrentMap<Long, Process> activeProcesses = new ConcurrentHashMap<>();
    private final AtomicLong sequence = new AtomicLong(1L);
    private final AtomicBoolean shutdownRequested = new AtomicBoolean(false);
    private final ExecutorService streamReaderExecutor = Executors.newCachedThreadPool();

    public ProcessExecutionResult run(
            List<String> command,
            Path workDir,
            Map<String, String> environment,
            Duration timeout,
            ProcessContext context
    ) throws IOException, InterruptedException {
        long key = sequence.getAndIncrement();
        long startedAt = System.nanoTime();

        Path logFile = context == null ? null : context.getLogFile();
        if (logFile != null) {
            Path parent = logFile.getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }
            // 同一 indexRun 重跑时，旧日志要先清掉，避免解析到上一次的 Workflow complete
            Files.writeString(
                    logFile,
                    "",
                    StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE,
                    StandardOpenOption.TRUNCATE_EXISTING,
                    StandardOpenOption.WRITE
            );
        }
        ReentrantLock logLock = new ReentrantLock();

        ProcessBuilder builder = new ProcessBuilder(command);
        builder.directory(workDir.toFile());
        builder.environment().putAll(environment);

        Process process = builder.start();
        activeProcesses.put(key, process);

        Future<String> stdoutFuture = streamReaderExecutor.submit(() ->
                drainAndTee(process.getInputStream(), "stdout", logFile, logLock));
        Future<String> stderrFuture = streamReaderExecutor.submit(() ->
                drainAndTee(process.getErrorStream(), "stderr", logFile, logLock));

        try {
            boolean finished = process.waitFor(timeout.toMillis(), TimeUnit.MILLISECONDS);
            if (!finished) {
                process.destroyForcibly();
            }

            String stdout = stdoutFuture.get();
            String stderr = stderrFuture.get();

            return ProcessExecutionResult.builder()
                    .command(command)
                    .exitCode(finished ? process.exitValue() : -1)
                    .stdout(stdout)
                    .stderr(stderr)
                    .elapsedSeconds(Duration.ofNanos(System.nanoTime() - startedAt).toSeconds())
                    .timedOut(!finished)
                    .terminatedByShutdown(shutdownRequested.get())
                    .build();
        } catch (ExecutionException ex) {
            throw new IOException("读取进程输出失败", ex);
        } finally {
            activeProcesses.remove(key);
        }
    }

    /**
     * 同步读取一条输入流：每读到一行，加锁追加到日志文件并累加到返回字符串。
     */
    private String drainAndTee(InputStream stream, String streamName, Path logFile, ReentrantLock lock) throws IOException {
        StringBuilder accumulator = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(stream, StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                accumulator.append(line).append(System.lineSeparator());
                if (logFile == null) {
                    continue;
                }
                String logLine = "[" + streamName + "] " + line + System.lineSeparator();
                lock.lock();
                try (BufferedWriter writer = Files.newBufferedWriter(
                        logFile,
                        StandardCharsets.UTF_8,
                        StandardOpenOption.WRITE,
                        StandardOpenOption.APPEND)) {
                    writer.write(logLine);
                } finally {
                    lock.unlock();
                }
            }
        }
        return accumulator.toString();
    }

    int activeProcessCount() {
        return activeProcesses.size();
    }

    @Override
    public void destroy() {
        shutdownRequested.set(true);
        activeProcesses.values().forEach(process -> {
            process.destroy();
            try {
                if (!process.waitFor(1, TimeUnit.SECONDS)) {
                    process.destroyForcibly();
                }
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
                process.destroyForcibly();
            }
        });
        activeProcesses.clear();
        streamReaderExecutor.shutdownNow();
    }
}
