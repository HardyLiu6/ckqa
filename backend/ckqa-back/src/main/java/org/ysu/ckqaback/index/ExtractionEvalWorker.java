package org.ysu.ckqaback.index;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.ysu.ckqaback.entity.KnowledgeBaseBuildRuns;
import org.ysu.ckqaback.entity.PromptTuneExtractionEvalRuns;
import org.ysu.ckqaback.integration.config.CkqaIntegrationProperties;
import org.ysu.ckqaback.service.KnowledgeBaseBuildRunsService;
import org.ysu.ckqaback.service.PromptTuneExtractionEvalRunsService;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executor;
import java.util.stream.Collectors;

/**
 * 04 步评分异步 worker。
 *
 * <p>状态机：</p>
 * <pre>
 *   pending → running ─ for each candidate ─→ runSingleCandidateExtract → 心跳推进
 *           ↓ all done             ↓ error                ↓ cancelling 检测
 *           runScoring             markFailed             markCancelled
 *           ↓                      ↓                      ↓
 *           markSuccess + 写 reportJson
 * </pre>
 *
 * <p>磁盘约定：脚本输出仍落 {@code <GRAPHRAG_ROOT>/results/extraction_eval/runs/<runId>/} 共享路径，
 * worker 跑完后把产物复制到 build run workspace 的 {@code eval/<evalRunId>/} 下，并清掉共享路径。</p>
 */
@Service
@RequiredArgsConstructor
public class ExtractionEvalWorker {

    private static final Logger log = LoggerFactory.getLogger(ExtractionEvalWorker.class);

    /** 课程域抽取实体类型，与 Phase 3 SingleSampleExtractionOrchestrator 保持一致。
     *  TODO Phase 7+：从 schema 配置文件动态读取，避免与 graphrag_pipeline/config/schema/entity_types.json 漂移。 */
    private static final String DEFAULT_ENTITY_TYPES =
            "Course,Concept,Term,KnowledgePoint,FormulaOrDefinition,AlgorithmOrMethod,Theorem,Property,Example,Topic";

    private final PromptTuneExtractionEvalRunsService evalRunsService;
    private final KnowledgeBaseBuildRunsService buildRunsService;
    private final BuildRunWorkspaceService workspaceService;
    private final ExtractionEvalOrchestrator orchestrator;
    private final CkqaIntegrationProperties properties;
    private final CandidateMetadataLookup metadataLookup;
    private final ObjectMapper objectMapper;

    @Qualifier("extractionEvalExecutor")
    private final Executor extractionEvalExecutor;

    /**
     * 异步派发：把任务塞进线程池。
     */
    public void dispatch(Long evalRunId) {
        extractionEvalExecutor.execute(() -> {
            try {
                runInternal(evalRunId);
            } catch (RuntimeException exception) {
                log.error("评分任务异常 evalRunId={}", evalRunId, exception);
                markFailed(evalRunId, exception.getMessage());
            }
        });
    }

    /**
     * Phase 5.1：仅重跑「评分汇总」阶段。
     *
     * <p>触发条件：上次任务已 failed 在 scoring 阶段（progress_stage=scoring，已有部分 finishedCandidates），
     * 抽取产物（{@code sharedExtractDir/<runId>_<candidateId>.json}）仍在共享磁盘上。直接重跑
     * {@code orchestrator.runScoring(...)} → 复制产物 → markSuccess，跳过抽取阶段（30+ 分钟）。
     * 抽取产物缺失时降级抛 IllegalStateException 让 caller 走 trigger 全量重跑。</p>
     */
    public void dispatchScoringOnly(Long evalRunId) {
        extractionEvalExecutor.execute(() -> {
            try {
                runScoringOnly(evalRunId);
            } catch (RuntimeException exception) {
                log.error("评分仅重跑 scoring 异常 evalRunId={}", evalRunId, exception);
                markFailed(evalRunId, exception.getMessage());
            }
        });
    }

    void runScoringOnly(Long evalRunId) {
        PromptTuneExtractionEvalRuns run = evalRunsService.getRequiredById(evalRunId);
        markRunning(run.getId());

        KnowledgeBaseBuildRuns buildRun;
        try {
            buildRun = buildRunsService.getRequiredById(run.getBuildRunId());
        } catch (RuntimeException exception) {
            markFailed(run.getId(), "构建运行不存在: " + exception.getMessage());
            return;
        }

        Path workspace = workspaceService.resolve(buildRun.getWorkspaceUri());
        Path candidatesDir = workspace.resolve("prompt/candidates");
        Path auditFile = candidatesDir.resolve("audit_with_gold.json");
        Path evalDir = workspace.resolve("eval").resolve(String.valueOf(run.getId()));
        Path workerLogsDir = evalDir.resolve("logs");
        try {
            Files.createDirectories(workerLogsDir);
        } catch (IOException e) {
            markFailed(run.getId(), "创建评分目录失败: " + e.getMessage());
            return;
        }

        String runId = "eval_" + buildRun.getId() + "_" + run.getId();
        Path sharedExtractDir = Path.of(properties.getGraphrag().getRoot())
                .resolve("results/extraction_eval/runs").resolve(runId);
        Path sharedReportDir = Path.of(properties.getGraphrag().getRoot())
                .resolve("results/reports/extraction_scoring/runs").resolve(runId);

        // 校验抽取产物仍在；缺失则不允许"仅重跑"，让 caller 走 trigger 全量重跑
        if (!Files.exists(sharedExtractDir)) {
            markFailed(run.getId(), "抽取产物已不存在，无法仅重跑评分汇总：" + sharedExtractDir);
            return;
        }

        // 清理旧的 scoring 报告，避免与新输出混淆
        try {
            deleteIfExists(sharedReportDir);
        } catch (IOException e) {
            markFailed(run.getId(), "清理旧 scoring 报告失败: " + e.getMessage());
            return;
        }

        // ---- scoring 阶段 ----
        List<String> finished = parseSelectedIds(run.getFinishedCandidates());
        updateStage(run.getId(), "scoring", null, finished);
        try {
            orchestrator.runScoring(runId, auditFile, workerLogsDir);
        } catch (Exception e) {
            markFailed(run.getId(), "评分汇总失败: " + (e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName()));
            return;
        }

        // ---- 复制产物到 evalDir + 清理共享磁盘路径 ----
        try {
            copyDirectory(sharedExtractDir, evalDir.resolve("extraction_eval"));
            copyDirectory(sharedReportDir, evalDir.resolve("scoring_report"));
            deleteIfExists(sharedExtractDir);
            deleteIfExists(sharedReportDir);
        } catch (IOException e) {
            log.warn("复制评分产物到 build run workspace 失败 evalRunId={}: {}", run.getId(), e.getMessage());
        }

        Path reportFile = evalDir.resolve("scoring_report/top_candidates.json");
        String reportJson = "";
        try {
            if (Files.exists(reportFile)) {
                reportJson = Files.readString(reportFile, StandardCharsets.UTF_8);
            }
        } catch (IOException e) {
            log.warn("读取 top_candidates.json 失败 evalRunId={}: {}", run.getId(), e.getMessage());
        }
        markSuccess(run.getId(), evalDir, reportJson, finished);
        log.info("评分仅重跑 scoring 成功 evalRunId={}, finished={}", run.getId(), finished);
    }

    void runInternal(Long evalRunId) {
        PromptTuneExtractionEvalRuns run = evalRunsService.getRequiredById(evalRunId);
        markRunning(run.getId());

        KnowledgeBaseBuildRuns buildRun;
        try {
            buildRun = buildRunsService.getRequiredById(run.getBuildRunId());
        } catch (RuntimeException exception) {
            markFailed(run.getId(), "构建运行不存在: " + exception.getMessage());
            return;
        }

        Path workspace = workspaceService.resolve(buildRun.getWorkspaceUri());
        Path candidatesDir = workspace.resolve("prompt/candidates");
        Path auditFile = candidatesDir.resolve("audit_with_gold.json");
        Path evalDir = workspace.resolve("eval").resolve(String.valueOf(run.getId()));
        Path workerLogsDir = evalDir.resolve("logs");
        try {
            Files.createDirectories(workerLogsDir);
        } catch (IOException e) {
            markFailed(run.getId(), "创建评分目录失败: " + e.getMessage());
            return;
        }

        // 回填本次评分实际使用的 audit 样本数：
        // build_audit_extraction_set.py --sample_size 默认 20，但当原始 prompt_tuning_samples
        // 总条数 < 20 时实际只会含 N (N<20) 条；不回填的话 service 投影 status 时会一直按
        // 硬编码 20 算分母，UI 会显示 0/20 永远跑不到 20。
        int sampleTotal = readAuditSampleCount(auditFile);
        if (sampleTotal > 0) {
            updateSampleTotal(run.getId(), sampleTotal);
        }

        List<String> selected = parseSelectedIds(run.getSelectedCandidateIds());
        // 校验候选 ID（worker 层兜底；正常 Service 已经过滤）
        for (String candidateId : selected) {
            if (!metadataLookup.isKnown(candidateId)) {
                markFailed(run.getId(), "selectedCandidateIds 含 unknown candidate: " + candidateId);
                return;
            }
        }
        if (selected.isEmpty()) {
            markFailed(run.getId(), "selectedCandidateIds 为空");
            return;
        }

        String runId = "eval_" + buildRun.getId() + "_" + run.getId();

        // 共享磁盘路径，worker 跑完会复制到 evalDir 后清理
        Path sharedExtractDir = Path.of(properties.getGraphrag().getRoot())
                .resolve("results/extraction_eval/runs").resolve(runId);
        Path sharedReportDir = Path.of(properties.getGraphrag().getRoot())
                .resolve("results/reports/extraction_scoring/runs").resolve(runId);

        // 启动前清理可能存在的半成品
        try {
            deleteIfExists(sharedExtractDir);
            deleteIfExists(sharedReportDir);
        } catch (IOException e) {
            markFailed(run.getId(), "清理共享磁盘路径失败: " + e.getMessage());
            return;
        }

        // ---- 抽取阶段：串行跑每个候选；单候选失败不阻断剩余候选 ----
        updateStage(run.getId(), "extracting", null, List.of());
        List<String> finished = new ArrayList<>();
        // 结构化失败清单（持久化到 candidate_failures 列）：
        // [{"candidateId":"default","stage":"extract","reason":"timeout"}, ...]
        List<Map<String, String>> failures = new ArrayList<>();
        for (String candidateId : selected) {
            // 每候选前检查 cancelling（候选边界软取消，决策 6）
            PromptTuneExtractionEvalRuns reloaded = evalRunsService.getRequiredById(run.getId());
            if ("cancelling".equals(reloaded.getStatus())) {
                markCancelled(run.getId(), finished);
                return;
            }
            updateStage(run.getId(), "extracting", candidateId, finished);
            Path promptFile = candidatesDir.resolve(candidateId).resolve("prompt.txt");
            if (!Files.exists(promptFile)) {
                recordFailure(run.getId(), failures, candidateId, "extract", "候选 prompt 文件不存在");
                continue;
            }
            try {
                orchestrator.runSingleCandidateExtract(
                        candidateId,
                        auditFile,
                        promptFile,
                        DEFAULT_ENTITY_TYPES,
                        runId,
                        workerLogsDir
                );
                finished.add(candidateId);
            } catch (Exception e) {
                // 单候选失败：记录但不抛；剩余候选继续跑
                Throwable rootCause = e;
                while (rootCause.getCause() != null) rootCause = rootCause.getCause();
                String reason = e.getMessage() != null ? e.getMessage() : rootCause.getClass().getSimpleName();
                recordFailure(run.getId(), failures, candidateId, "extract", reason);
            }
        }

        // 整体终态判定（风险 1）：finished 全空才整体 failed
        if (finished.isEmpty()) {
            String failedIds = failures.stream()
                    .map(f -> f.get("candidateId"))
                    .collect(Collectors.joining(", "));
            markFailed(run.getId(), "全部候选抽取失败：" + failedIds);
            return;
        }

        // ---- scoring 阶段；scoring 之前再检查一次 cancelling ----
        PromptTuneExtractionEvalRuns reloaded = evalRunsService.getRequiredById(run.getId());
        if ("cancelling".equals(reloaded.getStatus())) {
            markCancelled(run.getId(), finished);
            return;
        }
        updateStage(run.getId(), "scoring", null, finished);
        try {
            orchestrator.runScoring(runId, auditFile, workerLogsDir);
        } catch (Exception e) {
            markFailed(run.getId(), "评分汇总失败: " + (e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName()));
            return;
        }

        // ---- 复制产物到 evalDir + 清理共享磁盘路径 ----
        try {
            copyDirectory(sharedExtractDir, evalDir.resolve("extraction_eval"));
            copyDirectory(sharedReportDir, evalDir.resolve("scoring_report"));
            deleteIfExists(sharedExtractDir);
            deleteIfExists(sharedReportDir);
        } catch (IOException e) {
            log.warn("复制评分产物到 build run workspace 失败 evalRunId={}: {}", run.getId(), e.getMessage());
            // 不强制失败：磁盘清理失败不影响任务结果，下次启动 sweep 时会再尝试
        }

        // ---- 读 top_candidates.json 写 reportJson ----
        Path reportFile = evalDir.resolve("scoring_report/top_candidates.json");
        String reportJson = "";
        try {
            if (Files.exists(reportFile)) {
                reportJson = Files.readString(reportFile, StandardCharsets.UTF_8);
            }
        } catch (IOException e) {
            log.warn("读取 top_candidates.json 失败 evalRunId={}: {}", run.getId(), e.getMessage());
        }
        markSuccess(run.getId(), evalDir, reportJson, finished);
    }

    // -----------------------------------------------------------------
    // 状态机操作
    // -----------------------------------------------------------------

    @Transactional
    protected void markRunning(Long id) {
        PromptTuneExtractionEvalRuns run = evalRunsService.getRequiredById(id);
        if ("running".equals(run.getStatus())) return;
        run.setStatus("running");
        run.setStartedAt(LocalDateTime.now());
        run.setLastHeartbeatAt(LocalDateTime.now());
        run.setUpdatedAt(LocalDateTime.now());
        evalRunsService.updateById(run);
    }

    @Transactional
    protected void updateStage(Long id, String stage, String currentCandidateId, List<String> finished) {
        PromptTuneExtractionEvalRuns run = evalRunsService.getById(id);
        if (run == null) return;
        run.setProgressStage(stage);
        run.setExtractingCandidateId(currentCandidateId);
        run.setFinishedCandidates(serializeIds(finished));
        run.setLastHeartbeatAt(LocalDateTime.now());
        run.setUpdatedAt(LocalDateTime.now());
        evalRunsService.updateById(run);
    }

    @Transactional
    protected void markSuccess(Long id, Path evalDir, String reportJson, List<String> finished) {
        PromptTuneExtractionEvalRuns run = evalRunsService.getById(id);
        if (run == null) return;
        run.setStatus("success");
        run.setProgressStage("done");
        run.setExtractingCandidateId(null);
        run.setFinishedCandidates(serializeIds(finished));
        run.setEvalDir(toRelativeWorkspaceUri(evalDir));
        run.setReportJson(reportJson);
        run.setFinishedAt(LocalDateTime.now());
        run.setLastHeartbeatAt(LocalDateTime.now());
        run.setUpdatedAt(LocalDateTime.now());
        evalRunsService.updateById(run);
        log.info("评分任务成功 evalRunId={}, finishedCandidates={}", id, finished);
    }

    @Transactional
    protected void markFailed(Long id, String error) {
        PromptTuneExtractionEvalRuns run = evalRunsService.getById(id);
        if (run == null) return;
        run.setStatus("failed");
        run.setProgressStage("done");
        run.setExtractingCandidateId(null);
        run.setErrorMessage(truncate(error, 1000));
        run.setFinishedAt(LocalDateTime.now());
        run.setLastHeartbeatAt(LocalDateTime.now());
        run.setUpdatedAt(LocalDateTime.now());
        evalRunsService.updateById(run);
        log.warn("评分任务失败 evalRunId={}, error={}", id, error);
    }

    @Transactional
    protected void markCancelled(Long id, List<String> finishedSoFar) {
        PromptTuneExtractionEvalRuns run = evalRunsService.getById(id);
        if (run == null) return;
        run.setStatus("cancelled");
        run.setProgressStage("done");
        run.setExtractingCandidateId(null);
        run.setFinishedCandidates(serializeIds(finishedSoFar));
        run.setFinishedAt(LocalDateTime.now());
        run.setLastHeartbeatAt(LocalDateTime.now());
        run.setUpdatedAt(LocalDateTime.now());
        evalRunsService.updateById(run);
        log.info("评分任务已取消 evalRunId={}", id);
    }

    /**
     * 记录单候选失败：往结构化 candidate_failures 列追加 JSON 项 + 同时写一条 latestLogs 行
     * （前者给前端 report.failedCandidates 用，后者给运维快速排障用）。
     *
     * <p>用于"单候选 failed 但整体仍可能 success"场景的审计闭环。
     * stage 取 "extract" / "scoring"；reason 简短，长字符串会被截断到 500 字节。</p>
     */
    @Transactional
    protected void recordFailure(Long id,
                                 List<Map<String, String>> inMemoryFailures,
                                 String candidateId,
                                 String stage,
                                 String reason) {
        Map<String, String> entry = new LinkedHashMap<>();
        entry.put("candidateId", candidateId);
        entry.put("stage", stage);
        entry.put("reason", truncate(reason, 500));
        inMemoryFailures.add(entry);

        PromptTuneExtractionEvalRuns run = evalRunsService.getById(id);
        if (run == null) return;
        try {
            run.setCandidateFailures(objectMapper.writeValueAsString(inMemoryFailures));
        } catch (JsonProcessingException e) {
            // 序列化失败时仅记日志，不让审计写失败阻断主流程
            log.warn("写入 candidate_failures 失败 evalRunId={}: {}", id, e.getMessage());
        }
        String prevLogs = run.getLatestLogs() == null ? "" : run.getLatestLogs();
        String nextLogs = (prevLogs + "\n[" + LocalDateTime.now() + "] [" + stage + "] " + candidateId + ": " + reason).strip();
        run.setLatestLogs(truncate(nextLogs, 4000));
        run.setLastHeartbeatAt(LocalDateTime.now());
        run.setUpdatedAt(LocalDateTime.now());
        evalRunsService.updateById(run);
        log.warn("候选异常 evalRunId={} candidate={} stage={}: {}", id, candidateId, stage, reason);
    }

    // -----------------------------------------------------------------
    // 辅助方法
    // -----------------------------------------------------------------

    private List<String> parseSelectedIds(String json) {
        if (json == null || json.isBlank()) return List.of();
        try {
            return objectMapper.readValue(json, new TypeReference<List<String>>() {});
        } catch (JsonProcessingException e) {
            return List.of();
        }
    }

    /**
     * 读 audit_with_gold.json 数组长度作为本次评分实际样本数。读失败返回 -1，调用方按"未知"兜底。
     * 兼容根级数组与 {@code {"audit_samples": [...]}} 两种 schema（与 build_audit_extraction_set.py 输出对齐）。
     */
    private int readAuditSampleCount(Path auditFile) {
        if (!Files.exists(auditFile)) return -1;
        try {
            com.fasterxml.jackson.databind.JsonNode node = objectMapper.readTree(auditFile.toFile());
            if (node.isArray()) return node.size();
            com.fasterxml.jackson.databind.JsonNode list = node.path("audit_samples");
            if (list.isArray()) return list.size();
            return -1;
        } catch (IOException e) {
            log.warn("读取 audit_with_gold.json 大小失败 path={}: {}", auditFile, e.getMessage());
            return -1;
        }
    }

    @Transactional
    protected void updateSampleTotal(Long id, int sampleTotal) {
        PromptTuneExtractionEvalRuns run = evalRunsService.getById(id);
        if (run == null) return;
        run.setSampleTotal(sampleTotal);
        run.setUpdatedAt(LocalDateTime.now());
        evalRunsService.updateById(run);
    }

    private String serializeIds(List<String> ids) {
        try {
            return objectMapper.writeValueAsString(ids);
        } catch (JsonProcessingException e) {
            return "[]";
        }
    }

    /**
     * 把 worker 中的绝对 Path 转成相对 GRAPHRAG_BUILD_RUNS_ROOT 的工作区 URI（与 evalDir 字段注释一致）。
     * <p>形式：{@code user_X/kb_Y/build_Z/eval/<evalRunId>}。这样 DB 不灌机器绝对路径，
     * 真实部署可在 {@code GRAPHRAG_BUILD_RUNS_ROOT} 切换路径时无需迁移数据。</p>
     */
    private String toRelativeWorkspaceUri(Path absolute) {
        String runsRootStr = properties.getGraphrag().getBuildRunsRoot();
        if (!StringUtils.hasText(runsRootStr)) {
            // 配置缺失时退化为绝对路径（不应发生）
            log.warn("GRAPHRAG_BUILD_RUNS_ROOT 未配置，evalDir 退化为绝对路径");
            return absolute.toAbsolutePath().normalize().toString();
        }
        Path runsRoot = Path.of(runsRootStr).toAbsolutePath().normalize();
        Path normalized = absolute.toAbsolutePath().normalize();
        if (!normalized.startsWith(runsRoot)) {
            // 不在 build runs root 下：兜底退化为绝对路径（极少见，只在测试或异常部署时发生）
            log.warn("evalDir 不在 GRAPHRAG_BUILD_RUNS_ROOT 下，退化为绝对路径 abs={}, root={}",
                    normalized, runsRoot);
            return normalized.toString();
        }
        return runsRoot.relativize(normalized).toString().replace('\\', '/');
    }

    private static String truncate(String text, int max) {
        if (text == null) return null;
        return text.length() > max ? text.substring(0, max) + "..." : text;
    }

    private static void deleteIfExists(Path dir) throws IOException {
        if (!Files.exists(dir)) return;
        try (var stream = Files.walk(dir)) {
            stream.sorted(Comparator.reverseOrder())
                    .forEach(p -> {
                        try { Files.deleteIfExists(p); } catch (IOException ignore) { }
                    });
        }
    }

    private static void copyDirectory(Path source, Path target) throws IOException {
        if (!Files.exists(source)) return;
        Files.createDirectories(target);
        try (var stream = Files.walk(source)) {
            stream.forEach(p -> {
                try {
                    Path rel = source.relativize(p);
                    Path dst = target.resolve(rel.toString());
                    if (Files.isDirectory(p)) {
                        Files.createDirectories(dst);
                    } else {
                        Files.createDirectories(dst.getParent());
                        Files.copy(p, dst, StandardCopyOption.REPLACE_EXISTING);
                    }
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
            });
        }
    }
}
