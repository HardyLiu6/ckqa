package org.ysu.ckqaback.index;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.ysu.ckqaback.entity.KnowledgeBases;
import org.ysu.ckqaback.entity.PromptTuneRuns;
import org.ysu.ckqaback.integration.process.ProcessExecutionResult;
import org.ysu.ckqaback.service.KnowledgeBasesService;
import org.ysu.ckqaback.service.PromptTuneRunsService;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executor;
import java.util.stream.Collectors;

/**
 * 提示词自动调优后台 worker。
 * <p>
 * 接受外部 service 的 dispatch，实际跑：
 * <pre>
 *   1. 把 selected materials 拉到 ${BUILD_RUNS_ROOT}/prompt-tune-cache/&lt;cacheKey&gt;/input/
 *   2. 调 scripts/run_graphrag_prompt_tune.py，输出到 .../auto_tuned/
 *   3. 把 auto_tuned/extract_graph.txt 视为成品，写 manifest.json
 *   4. 更新 prompt_tune_runs 状态为 success / failed
 * </pre>
 * 期间通过 {@link PromptTuneOrchestrator} 流式回调更新 latest_logs / last_heartbeat_at。
 */
@Service
@RequiredArgsConstructor
public class PromptTuneWorker {

    private static final Logger log = LoggerFactory.getLogger(PromptTuneWorker.class);

    /**
     * latest_logs 字段保留的最近行数与字符上限，与 QaTaskWorker 风格保持一致。
     */
    private static final int MAX_LOG_LINES = 30;
    private static final int MAX_LOG_CHARS = 8000;

    /**
     * graphrag prompt-tune 默认 domain / language；与 run_material_prompt_pipeline.py 保持一致。
     */
    private static final String DEFAULT_DOMAIN = "课程教材知识图谱抽取";
    private static final String DEFAULT_LANGUAGE = "中文";

    /**
     * graphrag 官方 prompt-tune 默认采样数（{@code --limit}）。
     * <p>当输入资料 chunk 数 ≥ 该值时直接走默认行为，不传 --limit；否则按实际文档数下调。</p>
     */
    private static final int GRAPHRAG_PROMPT_TUNE_DEFAULT_LIMIT = 15;

    private final PromptTuneRunsService promptTuneRunsService;
    private final KnowledgeBasesService knowledgeBasesService;
    private final PromptTuneOrchestrator orchestrator;
    private final BuildRunWorkspaceService workspaceService;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Qualifier("promptTuneExecutor")
    private final Executor promptTuneExecutor;

    /**
     * 异步执行：把 prompt-tune 任务放进线程池。
     */
    public void dispatch(Long promptTuneRunId, List<Long> materialIds) {
        promptTuneExecutor.execute(() -> {
            try {
                runInternal(promptTuneRunId, materialIds);
            } catch (RuntimeException exception) {
                log.error("prompt-tune 任务异常 runId={}", promptTuneRunId, exception);
                markFailed(promptTuneRunId, exception.getMessage(), null);
            }
        });
    }

    void runInternal(Long promptTuneRunId, List<Long> materialIds) {
        PromptTuneRuns run = promptTuneRunsService.getRequiredById(promptTuneRunId);
        markRunning(run.getId());

        Path candidateDir;
        try {
            candidateDir = workspaceService.resolve(run.getCandidateDir());
        } catch (RuntimeException exception) {
            markFailed(run.getId(), "调优产物目录非法: " + exception.getMessage(), null);
            return;
        }

        Path inputDir = candidateDir.resolve("input");
        Path autoTunedDir = candidateDir.resolve("auto_tuned");
        Path reportFile = candidateDir.resolve("report.json");

        Deque<String> logBuffer = new ArrayDeque<>();
        try {
            Files.createDirectories(autoTunedDir);

            // ---- 1. fetch input ----
            updateProgress(run.getId(), "fetch_input", logBuffer);
            KnowledgeBases knowledgeBase = knowledgeBasesService.getRequiredById(run.getKnowledgeBaseId());
            List<ProcessExecutionResult> fetchResults = orchestrator.fetchInputs(
                    null,
                    knowledgeBase,
                    materialIds,
                    inputDir
            );
            ProcessExecutionResult firstFailure = fetchResults.stream()
                    .filter(r -> r.isTimedOut() || r.getExitCode() != 0)
                    .findFirst()
                    .orElse(null);
            if (firstFailure != null) {
                String msg = firstFailure.isTimedOut()
                        ? "拉取调优输入资料超时"
                        : firstSummary(firstFailure.getStderr(), "拉取调优输入资料失败");
                markFailed(run.getId(), msg, joinLogs(logBuffer));
                return;
            }

            // ---- 2. run prompt-tune ----
            updateProgress(run.getId(), "prompt_tune", logBuffer);
            // per-run 报告目录：让 graphrag 的 logging 落到这里而不是仓库共享 reports/。
            Path reportingDir = candidateDir.resolve("reports");
            Files.createDirectories(reportingDir);
            Path graphragLogFile = reportingDir.resolve("prompt-tuning.log");

            // 自适应 --limit：扫输入目录 json 估算文档数，避免 graphrag 默认 limit=15
            // 与小体量资料不匹配时抛 "Cannot take a larger sample than population"。
            // 经验：每个 section_doc 经 graphrag chunking 通常 chunk_count >= doc_count，
            // 取 doc_count 是保守下界；不足 GRAPHRAG_PROMPT_TUNE_DEFAULT_LIMIT 才下调。
            int inputDocCount = estimateInputDocCount(inputDir);
            int adaptiveLimit = Math.max(1, Math.min(GRAPHRAG_PROMPT_TUNE_DEFAULT_LIMIT, inputDocCount));
            List<String> extraArgs = new java.util.ArrayList<>();
            if (inputDocCount > 0 && inputDocCount < GRAPHRAG_PROMPT_TUNE_DEFAULT_LIMIT) {
                extraArgs.add("--limit=" + adaptiveLimit);
                log.info(
                        "prompt-tune 自适应采样：runId={}, inputDocs={}, --limit={}",
                        run.getId(), inputDocCount, adaptiveLimit
                );
            }

            PromptTuneOrchestrator.PromptTuneCommand command = PromptTuneOrchestrator.PromptTuneCommand.builder()
                    .inputDir(inputDir)
                    .candidateDir(autoTunedDir)
                    .reportFile(reportFile)
                    .reportingDir(reportingDir)
                    .runId("prompt_tune_run_" + run.getId())
                    .domain(DEFAULT_DOMAIN)
                    .language(DEFAULT_LANGUAGE)
                    .noEntityTypes(true)
                    .extraArgs(extraArgs)
                    .build();

            // 启动日志 tailer：尾随 per-run 隔离的 prompt-tuning.log。
            // 因为是 per-run 独立文件，offset 从 0 开始；tailer 会在子进程创建文件后开始读。
            PromptTuneLogTailer tailer = new PromptTuneLogTailer(
                    graphragLogFile,
                    0L,
                    phase -> updateProgress(run.getId(), phase.getStageKey(), logBuffer),
                    line -> appendLog(run.getId(), logBuffer, line),
                    300L
            );
            Thread tailerThread = new Thread(tailer, "prompt-tune-tailer-" + run.getId());
            tailerThread.setDaemon(true);
            tailerThread.start();

            PromptTuneOrchestrator.PromptTuneExecutionResult execResult;
            try {
                execResult = orchestrator.runPromptTune(
                        command,
                        line -> appendLog(run.getId(), logBuffer, line)
                );
            } finally {
                tailer.stop();
                try {
                    // 把 tailer 线程退出等待时间从 2s 拉长到 8s。
                    // 给 tailer 充分时间消化最后一段 buffer，避免在主线程进入
                    // markFailed/markSuccess 后 tailer 滞后写入造成丢失更新。
                    // 即使没等到也无害：service 层已经用 WHERE status='running' 守护。
                    tailerThread.join(8000);
                } catch (InterruptedException interrupted) {
                    Thread.currentThread().interrupt();
                }
            }

            if (execResult.isTimedOut() || execResult.getExitCode() != 0) {
                String msg = execResult.isTimedOut()
                        ? "调优命令执行超时"
                        : firstSummary(execResult.getStderr(), "调优命令执行失败");
                markFailed(run.getId(), msg, joinLogs(logBuffer));
                return;
            }

            // ---- 3. locate output ----
            Path generatedPrompt = orchestrator.locateGeneratedExtractGraphPrompt(autoTunedDir);
            if (generatedPrompt == null) {
                markFailed(run.getId(), "调优结束后未找到 extract_graph.txt，请检查 candidate_dir", joinLogs(logBuffer));
                return;
            }

            // 标准化：把它复制到 candidate_dir 顶层，BuildRunPromptMaterializer 直接按 cacheKey 读取这一份。
            Path standardized = candidateDir.resolve("extract_graph.txt");
            orchestrator.copyFile(generatedPrompt, standardized);
            String content = Files.readString(standardized, StandardCharsets.UTF_8);
            String sha = sha256Hex(content);
            writeManifest(candidateDir, run, sha);

            markSuccess(run.getId(), sha, joinLogs(logBuffer));
            log.info("prompt-tune 调优成功 runId={}, sha={}", run.getId(), sha);
        } catch (IOException | InterruptedException exception) {
            if (exception instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            markFailed(run.getId(), exception.getMessage(), joinLogs(logBuffer));
        }
    }

    private void writeManifest(Path candidateDir, PromptTuneRuns run, String sha) throws IOException {
        Map<String, Object> manifest = new LinkedHashMap<>();
        manifest.put("promptTuneRunId", run.getId());
        manifest.put("knowledgeBaseId", run.getKnowledgeBaseId());
        manifest.put("courseId", run.getCourseId());
        manifest.put("cacheKey", run.getCacheKey());
        manifest.put("selectedMaterialIds", run.getSelectedMaterialIds());
        manifest.put("promptSha256", sha);
        manifest.put("generatedAt", LocalDateTime.now().toString());
        Files.writeString(
                candidateDir.resolve("manifest.json"),
                objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(manifest),
                StandardCharsets.UTF_8
        );
    }

    /**
     * 估算输入目录里所有 GraphRAG 输入 json 文档总数。
     * <p>graphrag 的 chunking 步会把每个 section_doc 切成若干 chunk，
     * 一般 chunk_count >= doc_count，所以 doc_count 是采样数 {@code --limit} 的保守下界。</p>
     * <p>解析失败时返回 0，调用方应据此跳过 --limit 注入，让 graphrag 走默认行为。</p>
     */
    int estimateInputDocCount(Path inputDir) {
        if (inputDir == null || !Files.isDirectory(inputDir)) {
            return 0;
        }
        int total = 0;
        try (var stream = Files.newDirectoryStream(inputDir, "*.json")) {
            for (Path jsonFile : stream) {
                try {
                    Object parsed = objectMapper.readValue(jsonFile.toFile(), Object.class);
                    if (parsed instanceof List<?> list) {
                        total += list.size();
                    } else if (parsed instanceof Map<?, ?>) {
                        total += 1;
                    }
                } catch (IOException ioException) {
                    log.warn("解析 prompt-tune 输入 json 失败 file={}, msg={}",
                            jsonFile.getFileName(), ioException.getMessage());
                }
            }
        } catch (IOException ioException) {
            log.warn("扫描 prompt-tune 输入目录失败 dir={}, msg={}", inputDir, ioException.getMessage());
            return 0;
        }
        return total;
    }

    @Transactional
    protected void markRunning(Long id) {
        // 条件 SQL：仅在 status IN (pending, running) 时翻为 running，避免覆盖终态。
        promptTuneRunsService.markRunning(id);
    }

    @Transactional
    protected void updateProgress(Long id, String stage, Deque<String> logBuffer) {
        // 条件 SQL：WHERE status='running'。worker 主线程已 mark success/failed 后，
        // tailer 滞后回调进来 update 行数为 0，不会覆盖终态字段。
        promptTuneRunsService.updateProgressStage(id, stage, joinLogs(logBuffer));
    }

    @Transactional
    protected void appendLog(Long id, Deque<String> buffer, String line) {
        synchronized (buffer) {
            buffer.addLast(line);
            while (buffer.size() > MAX_LOG_LINES) {
                buffer.removeFirst();
            }
        }
        // 条件 SQL：WHERE status='running'，与 updateProgress 同理。
        promptTuneRunsService.appendLatestLogs(id, joinLogs(buffer));
    }

    @Transactional
    protected void markSuccess(Long id, String promptSha256, String logs) {
        // 条件 SQL：WHERE status IN (pending, running)，确保不会从已 failed 翻回 success。
        promptTuneRunsService.markSuccess(id, promptSha256, logs);
    }

    @Transactional
    protected void markFailed(Long id, String error, String logs) {
        // 条件 SQL：WHERE status IN (pending, running)，确保不会从 success 翻回 failed，
        // 且不会被 tailer 滞后写入覆盖。
        promptTuneRunsService.markFailed(id, firstSummary(error, "调优失败"), logs);
    }

    private String joinLogs(Deque<String> buffer) {
        synchronized (buffer) {
            String joined = buffer.stream().collect(Collectors.joining("\n"));
            if (joined.length() > MAX_LOG_CHARS) {
                return joined.substring(joined.length() - MAX_LOG_CHARS);
            }
            return joined;
        }
    }

    private String firstSummary(String text, String fallback) {
        if (text == null || text.isBlank()) {
            return fallback;
        }
        String trimmed = text.trim();
        return trimmed.length() > 500 ? trimmed.substring(0, 500) : trimmed;
    }

    private static String sha256Hex(String content) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] bytes = digest.digest(content.getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder("sha256:");
            for (byte b : bytes) {
                hex.append(String.format("%02x", b));
            }
            return hex.toString();
        } catch (NoSuchAlgorithmException exception) {
            return "sha256:unavailable";
        }
    }
}
