package org.ysu.ckqaback.index;

import lombok.Builder;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.ysu.ckqaback.entity.IndexRuns;
import org.ysu.ckqaback.entity.KnowledgeBases;
import org.ysu.ckqaback.integration.config.CkqaIntegrationProperties;
import org.ysu.ckqaback.integration.graphrag.GraphRagIndexOrchestrator;
import org.ysu.ckqaback.integration.process.ProcessExecutionResult;
import org.ysu.ckqaback.integration.process.PythonCommandResolver;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

/**
 * 包装 {@code scripts/run_graphrag_prompt_tune.py} 子进程，专为长耗时调优场景设计。
 * <p>
 * 与 {@link org.ysu.ckqaback.integration.process.ProcessRunner} 不同：本 orchestrator
 * 流式读取 stdout/stderr，每读一行就回调外部 {@link Consumer}，便于 worker 实时刷新
 * {@code last_heartbeat_at} 和 {@code latest_logs}。
 */
@Service
@RequiredArgsConstructor
public class PromptTuneOrchestrator {

    private static final Logger log = LoggerFactory.getLogger(PromptTuneOrchestrator.class);

    /**
     * 单条日志最大长度，超过部分按字符截断，避免某些异常 traceback 把单行日志撑得很长。
     */
    private static final int MAX_LINE_CHARS = 1000;

    private final CkqaIntegrationProperties properties;
    private final GraphRagIndexOrchestrator graphRagIndexOrchestrator;
    private final ExecutorService streamReaderExecutor =
            Executors.newCachedThreadPool(runnable -> {
                Thread thread = new Thread(runnable, "prompt-tune-stream-reader-" + System.nanoTime());
                thread.setDaemon(true);
                return thread;
            });

    /**
     * 把 build run 选中的资料从 MinIO 拉到 prompt-tune 输入目录，每个资料一份 JSON。
     *
     * @param run             prompt-tune 创建出的占位 IndexRuns（仅用于日志和 ProcessContext，可为空）
     * @param knowledgeBase   知识库（用于 courseId）
     * @param materialIds     选中的资料 ID 列表
     * @param promptTuneInputDir 目标目录，调用方负责 createDirectories
     * @return 每份资料的执行结果（按调用顺序）
     */
    public List<ProcessExecutionResult> fetchInputs(
            IndexRuns run,
            KnowledgeBases knowledgeBase,
            List<Long> materialIds,
            Path promptTuneInputDir
    ) throws IOException, InterruptedException {
        Files.createDirectories(promptTuneInputDir);
        // 调优前先清理目录，避免历史残留资料污染缓存键之外的输入。
        cleanDirectory(promptTuneInputDir);

        List<ProcessExecutionResult> results = new ArrayList<>();
        for (Long materialId : materialIds) {
            String outputFile = "material_" + materialId + ".section_docs.json";
            ProcessExecutionResult result = graphRagIndexOrchestrator.fetchMaterialInput(
                    run,
                    knowledgeBase,
                    materialId,
                    promptTuneInputDir,
                    "section_docs.json",
                    outputFile
            );
            results.add(result);
            if (result.isTimedOut() || result.getExitCode() != 0) {
                return results;
            }
        }
        return results;
    }

    /**
     * 跑 {@code python scripts/run_graphrag_prompt_tune.py}。
     */
    public PromptTuneExecutionResult runPromptTune(PromptTuneCommand command, Consumer<String> logLineConsumer)
            throws IOException, InterruptedException {
        List<String> argv = new ArrayList<>(PythonCommandResolver.resolve(
                properties.getGraphrag().getPython(),
                properties.getGraphrag().getManagedApi().getCondaEnv()
        ));
        argv.add("scripts/run_graphrag_prompt_tune.py");
        argv.add("--root");
        argv.add(properties.getGraphrag().getRoot());
        argv.add("--output");
        argv.add(command.candidateDir.toAbsolutePath().toString());
        argv.add("--report_file");
        argv.add(command.reportFile.toAbsolutePath().toString());
        if (StringUtils.hasText(command.runId)) {
            argv.add("--run-id");
            argv.add(command.runId);
        }
        if (StringUtils.hasText(command.domain)) {
            argv.add("--domain");
            argv.add(command.domain);
        }
        if (StringUtils.hasText(command.language)) {
            argv.add("--language");
            argv.add(command.language);
        }
        argv.add("--overwrite");
        if (command.noEntityTypes) {
            argv.add("--no_entity_types");
        }

        Map<String, String> env = new LinkedHashMap<>();
        // 关键：覆盖 graphrag 自身解析的输入目录，让官方 prompt-tune 看到我们的临时输入目录。
        env.put("GRAPHRAG_INPUT_DIR", command.inputDir.toAbsolutePath().toString());
        if (command.reportingDir != null) {
            // 把 graphrag 内部 logging 重定向到 per-run reports 目录，避免跨 run 共享一份
            // graphrag_pipeline/reports/prompt-tuning.log 造成日志污染。
            Files.createDirectories(command.reportingDir);
            env.put("GRAPHRAG_REPORTING_DIR", command.reportingDir.toAbsolutePath().toString());
        }

        ProcessBuilder builder = new ProcessBuilder(argv);
        builder.directory(Path.of(properties.getGraphrag().getRoot()).toFile());
        builder.environment().putAll(env);
        builder.redirectErrorStream(false);

        long startedAt = System.nanoTime();
        Process process = builder.start();
        StringBuilder stdoutBuf = new StringBuilder();
        StringBuilder stderrBuf = new StringBuilder();

        Future<?> stdoutFuture = streamReaderExecutor.submit(
                () -> drain(process.getInputStream(), stdoutBuf, logLineConsumer)
        );
        Future<?> stderrFuture = streamReaderExecutor.submit(
                () -> drain(process.getErrorStream(), stderrBuf, logLineConsumer)
        );

        Duration timeout = Duration.ofSeconds(properties.getTimeout().getPromptTuneSeconds());
        boolean finished = process.waitFor(timeout.toMillis(), TimeUnit.MILLISECONDS);
        if (!finished) {
            process.destroyForcibly();
        }
        try {
            stdoutFuture.get(5, TimeUnit.SECONDS);
            stderrFuture.get(5, TimeUnit.SECONDS);
        } catch (Exception exception) {
            log.warn("等待 prompt-tune 流读取线程结束失败：{}", exception.getMessage());
        }
        long elapsedSeconds = Duration.ofNanos(System.nanoTime() - startedAt).toSeconds();

        return PromptTuneExecutionResult.builder()
                .command(argv)
                .exitCode(finished ? process.exitValue() : -1)
                .stdout(stdoutBuf.toString())
                .stderr(stderrBuf.toString())
                .elapsedSeconds(elapsedSeconds)
                .timedOut(!finished)
                .build();
    }

    /**
     * 把 candidate_dir 下的 extract_graph.txt 复制为 build run 工作区可消费的标准位置。
     * <p>
     * 调用方应在调用前确认 candidate_dir 存在 extract_graph.txt 文件。
     */
    public Path locateGeneratedExtractGraphPrompt(Path candidateDir) {
        Path autoTunedExtract = candidateDir.resolve("extract_graph.txt");
        if (Files.isRegularFile(autoTunedExtract)) {
            return autoTunedExtract;
        }
        // graphrag prompt-tune 历史版本可能写到 entity_extraction.txt；做一次兼容查找。
        Path fallback = candidateDir.resolve("entity_extraction.txt");
        if (Files.isRegularFile(fallback)) {
            return fallback;
        }
        return null;
    }

    /**
     * 以 UTF-8 把 src 复制到 dst（覆盖）。
     */
    public void copyFile(Path src, Path dst) throws IOException {
        Files.createDirectories(dst.getParent());
        Files.copy(src, dst, StandardCopyOption.REPLACE_EXISTING);
    }

    private void drain(InputStream stream, StringBuilder buffer, Consumer<String> consumer) {
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(stream, StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                if (line.length() > MAX_LINE_CHARS) {
                    line = line.substring(0, MAX_LINE_CHARS) + "…";
                }
                buffer.append(line).append('\n');
                if (consumer != null && !line.isBlank()) {
                    try {
                        consumer.accept(line);
                    } catch (RuntimeException callbackException) {
                        log.warn("prompt-tune 日志回调失败：{}", callbackException.getMessage());
                    }
                }
            }
        } catch (IOException exception) {
            log.warn("prompt-tune stream 读取失败：{}", exception.getMessage());
        }
    }

    private void cleanDirectory(Path directory) throws IOException {
        if (!Files.isDirectory(directory)) {
            return;
        }
        try (var stream = Files.newDirectoryStream(directory)) {
            for (Path path : stream) {
                if (Files.isDirectory(path)) {
                    cleanDirectory(path);
                }
                Files.deleteIfExists(path);
            }
        }
    }

    /**
     * prompt-tune 调用参数。
     */
    @Getter
    @Builder
    public static class PromptTuneCommand {
        private final Path inputDir;
        private final Path candidateDir;
        private final Path reportFile;
        /**
         * graphrag 内部 logging 写入目录；为空时沿用 graphrag {@code .env} 默认值。
         * 注入后日志会落到 {@code <reportingDir>/prompt-tuning.log}，方便 per-run 隔离。
         */
        private final Path reportingDir;
        private final String runId;
        private final String domain;
        private final String language;
        private final boolean noEntityTypes;
    }

    /**
     * prompt-tune 执行结果。
     */
    @Getter
    @Builder
    public static class PromptTuneExecutionResult {
        private final List<String> command;
        private final int exitCode;
        private final String stdout;
        private final String stderr;
        private final long elapsedSeconds;
        private final boolean timedOut;
    }
}
