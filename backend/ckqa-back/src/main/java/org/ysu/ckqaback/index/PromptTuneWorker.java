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
            PromptTuneOrchestrator.PromptTuneCommand command = PromptTuneOrchestrator.PromptTuneCommand.builder()
                    .inputDir(inputDir)
                    .candidateDir(autoTunedDir)
                    .reportFile(reportFile)
                    .runId("prompt_tune_run_" + run.getId())
                    .domain(DEFAULT_DOMAIN)
                    .language(DEFAULT_LANGUAGE)
                    .noEntityTypes(true)
                    .build();

            PromptTuneOrchestrator.PromptTuneExecutionResult execResult =
                    orchestrator.runPromptTune(command, line -> appendLog(run.getId(), logBuffer, line));

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

    @Transactional
    protected void markRunning(Long id) {
        PromptTuneRuns run = promptTuneRunsService.getRequiredById(id);
        if ("running".equals(run.getStatus())) {
            return;
        }
        run.setStatus("running");
        run.setProgressStage("queued");
        run.setStartedAt(LocalDateTime.now());
        run.setLastHeartbeatAt(LocalDateTime.now());
        run.setUpdatedAt(LocalDateTime.now());
        promptTuneRunsService.updateById(run);
    }

    @Transactional
    protected void updateProgress(Long id, String stage, Deque<String> logBuffer) {
        PromptTuneRuns run = promptTuneRunsService.getById(id);
        if (run == null) {
            return;
        }
        run.setProgressStage(stage);
        run.setLatestLogs(joinLogs(logBuffer));
        run.setLastHeartbeatAt(LocalDateTime.now());
        run.setUpdatedAt(LocalDateTime.now());
        promptTuneRunsService.updateById(run);
    }

    @Transactional
    protected void appendLog(Long id, Deque<String> buffer, String line) {
        synchronized (buffer) {
            buffer.addLast(line);
            while (buffer.size() > MAX_LOG_LINES) {
                buffer.removeFirst();
            }
        }
        // 心跳更新放在锁外，DB 写入不阻塞流式读取。
        PromptTuneRuns run = promptTuneRunsService.getById(id);
        if (run == null) {
            return;
        }
        run.setLatestLogs(joinLogs(buffer));
        run.setLastHeartbeatAt(LocalDateTime.now());
        run.setUpdatedAt(LocalDateTime.now());
        promptTuneRunsService.updateById(run);
    }

    @Transactional
    protected void markSuccess(Long id, String promptSha256, String logs) {
        PromptTuneRuns run = promptTuneRunsService.getById(id);
        if (run == null) {
            return;
        }
        run.setStatus("success");
        run.setProgressStage("done");
        run.setPromptSha256(promptSha256);
        run.setLatestLogs(logs);
        run.setFinishedAt(LocalDateTime.now());
        run.setLastHeartbeatAt(LocalDateTime.now());
        run.setUpdatedAt(LocalDateTime.now());
        promptTuneRunsService.updateById(run);
    }

    @Transactional
    protected void markFailed(Long id, String error, String logs) {
        PromptTuneRuns run = promptTuneRunsService.getById(id);
        if (run == null) {
            return;
        }
        run.setStatus("failed");
        run.setProgressStage("done");
        run.setErrorMessage(firstSummary(error, "调优失败"));
        if (logs != null) {
            run.setLatestLogs(logs);
        }
        run.setFinishedAt(LocalDateTime.now());
        run.setLastHeartbeatAt(LocalDateTime.now());
        run.setUpdatedAt(LocalDateTime.now());
        promptTuneRunsService.updateById(run);
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
