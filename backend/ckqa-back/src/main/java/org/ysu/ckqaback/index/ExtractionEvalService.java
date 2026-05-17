package org.ysu.ckqaback.index;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.ysu.ckqaback.api.ApiResultCode;
import org.ysu.ckqaback.entity.KnowledgeBaseBuildRuns;
import org.ysu.ckqaback.entity.PromptTuneAuditSamples;
import org.ysu.ckqaback.entity.PromptTuneExtractionEvalRuns;
import org.ysu.ckqaback.exception.BusinessException;
import org.ysu.ckqaback.index.dto.CandidateResponse;
import org.ysu.ckqaback.index.dto.ExtractionEvalReportResponse;
import org.ysu.ckqaback.index.dto.ExtractionEvalRequest;
import org.ysu.ckqaback.index.dto.ExtractionEvalRunStartedResponse;
import org.ysu.ckqaback.index.dto.ExtractionEvalStatusResponse;
import org.ysu.ckqaback.service.KnowledgeBaseBuildRunsService;
import org.ysu.ckqaback.service.PromptTuneAuditSamplesService;
import org.ysu.ckqaback.service.PromptTuneExtractionEvalRunsService;

import java.io.IOException;
import java.nio.file.Path;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * 04 步评分业务编排：
 * <ul>
 *   <li>{@link #trigger}：门控（02 完成 / 候选已生成 / 选定候选合法）→ 复用 active / 创建 pending → after-commit dispatch</li>
 *   <li>{@link #getStatus}：从 DB 投影最新 run 的进度</li>
 *   <li>{@link #getReport}：从 DB report_json 投影报告</li>
 *   <li>{@link #cancel}：把 active run 切换到 cancelling，worker 自感知后落终态</li>
 * </ul>
 */
@Service
@RequiredArgsConstructor
public class ExtractionEvalService {

    private static final Logger log = LoggerFactory.getLogger(ExtractionEvalService.class);

    private static final int POLLING_INTERVAL_MS = 1500;

    /** 单候选预估每样本 token，乘以 sampleTotal 得到候选总 token 估算。 */
    private static final int ESTIMATED_TOKEN_PER_CANDIDATE = 5000 * 20;

    /** 标准 20 样本规模下单候选预估总耗时秒数（5-15 分钟，取中位 8 分钟）；service 会按 sampleTotal 比例缩放。 */
    private static final int ESTIMATED_SECONDS_PER_CANDIDATE = 8 * 60;

    private final KnowledgeBaseBuildRunsService buildRunsService;
    private final PromptTuneAuditSamplesService samplesService;
    private final PromptTuneExtractionEvalRunsService evalRunsService;
    private final CandidateManifestReader manifestReader;
    private final BuildRunWorkspaceService workspaceService;
    private final ExtractionEvalReportAssembler reportAssembler;
    private final ExtractionEvalWorker worker;
    private final ObjectMapper objectMapper;

    @Transactional
    public ExtractionEvalRunStartedResponse trigger(Long buildRunId, ExtractionEvalRequest request) {
        KnowledgeBaseBuildRuns buildRun = buildRunsService.getRequiredById(buildRunId);

        // 门控 1：02 步至少 1 条 completed
        List<PromptTuneAuditSamples> samples = samplesService.listByBuildRunId(buildRunId);
        boolean hasCompleted = samples.stream()
                .anyMatch(s -> "completed".equals(s.getReviewerDecision()));
        if (!hasCompleted) {
            throw new BusinessException(
                    ApiResultCode.CANDIDATE_REQUIRES_AUDIT_COMPLETED,
                    HttpStatus.BAD_REQUEST
            );
        }

        // 门控 2：03 步候选已生成
        Path candidatesDir = workspaceService.resolve(buildRun.getWorkspaceUri())
                .resolve("prompt").resolve("candidates");
        List<CandidateResponse> candidates;
        try {
            candidates = manifestReader.read(candidatesDir);
        } catch (IOException e) {
            throw new BusinessException(
                    ApiResultCode.CANDIDATE_GENERATION_FAILED,
                    HttpStatus.INTERNAL_SERVER_ERROR,
                    "读取候选 manifest 失败: " + e.getMessage()
            );
        }
        if (candidates.isEmpty()) {
            throw new BusinessException(
                    ApiResultCode.CANDIDATES_NOT_GENERATED,
                    HttpStatus.BAD_REQUEST
            );
        }

        // 门控 3：selectedCandidates ⊆ 当前 seed 下允许的候选白名单
        // Phase 5.2：candidates 透出按 seed 过滤后只剩 3 个；trigger 校验同步收紧，
        // 防止用户从 URL 或绕过前端门控提交"被冗余基线规则排除"的 candidateId。
        String currentSeed = resolveSeedFromMetadata(buildRun);
        Set<String> seedAllowed = CandidateSeedFilter.allowedCandidatesForSeed(currentSeed);
        Set<String> generatedIds = new LinkedHashSet<>();
        for (CandidateResponse c : candidates) {
            if (seedAllowed.contains(c.getCandidateId())) {
                generatedIds.add(c.getCandidateId());
            }
        }
        List<String> selected = request.getSelectedCandidates();
        List<String> unknown = new ArrayList<>();
        for (String id : selected) {
            if (!generatedIds.contains(id)) unknown.add(id);
        }
        if (!unknown.isEmpty()) {
            throw new BusinessException(
                    ApiResultCode.INVALID_EVAL_CANDIDATE_SELECTION,
                    HttpStatus.BAD_REQUEST,
                    "未识别或已被 seed 规则排除的候选 ID：" + String.join(", ", unknown)
            );
        }

        // 复用 active：避免双开任务
        Optional<PromptTuneExtractionEvalRuns> active = evalRunsService.findActiveByBuildRunId(buildRunId);
        if (active.isPresent()) {
            return toStartedResponse(active.get(), true);
        }

        // 创建 pending + after-commit dispatch
        PromptTuneExtractionEvalRuns run = createPending(buildRun, selected);
        dispatchAfterCommit(run.getId());
        return toStartedResponse(run, false);
    }

    public ExtractionEvalStatusResponse getStatus(Long buildRunId) {
        Optional<PromptTuneExtractionEvalRuns> latest =
                evalRunsService.findLatestByBuildRunId(buildRunId);
        if (latest.isEmpty()) {
            throw new BusinessException(
                    ApiResultCode.EXTRACTION_EVAL_NOT_STARTED,
                    HttpStatus.NOT_FOUND
            );
        }
        return projectStatus(latest.get());
    }

    public ExtractionEvalReportResponse getReport(Long buildRunId) {
        Optional<PromptTuneExtractionEvalRuns> latest =
                evalRunsService.findLatestByBuildRunId(buildRunId);
        if (latest.isEmpty()) {
            throw new BusinessException(
                    ApiResultCode.EXTRACTION_EVAL_NOT_STARTED,
                    HttpStatus.NOT_FOUND
            );
        }
        PromptTuneExtractionEvalRuns run = latest.get();
        if (!"success".equals(run.getStatus())) {
            // 评分未成功 → 让前端继续轮询 status；不返回半成品报告
            throw new BusinessException(
                    ApiResultCode.EXTRACTION_EVAL_NOT_STARTED,
                    HttpStatus.NOT_FOUND,
                    "评分未完成，当前状态：" + run.getStatus()
            );
        }
        return reportAssembler.assemble(run);
    }

    /**
     * 按指定 evalRunId 取报告（供失败/取消终态下"查看上次评分结果"使用）。
     * <p>校验该 evalRun 属于传入的 buildRunId、status=success；否则抛 4106。</p>
     */
    public ExtractionEvalReportResponse getReportByEvalRunId(Long buildRunId, Long evalRunId) {
        PromptTuneExtractionEvalRuns run = evalRunsService.getRequiredById(evalRunId);
        if (!run.getBuildRunId().equals(buildRunId)) {
            throw new BusinessException(
                    ApiResultCode.EXTRACTION_EVAL_NOT_STARTED,
                    HttpStatus.NOT_FOUND,
                    "评分任务不属于当前构建运行"
            );
        }
        if (!"success".equals(run.getStatus())) {
            throw new BusinessException(
                    ApiResultCode.EXTRACTION_EVAL_NOT_STARTED,
                    HttpStatus.NOT_FOUND,
                    "目标评分任务未成功完成，当前状态：" + run.getStatus()
            );
        }
        return reportAssembler.assemble(run);
    }

    @Transactional
    public void cancel(Long buildRunId) {
        Optional<PromptTuneExtractionEvalRuns> active =
                evalRunsService.findActiveByBuildRunId(buildRunId);
        if (active.isEmpty()) return;  // 幂等
        PromptTuneExtractionEvalRuns run = active.get();
        if ("cancelling".equals(run.getStatus())) return;
        run.setStatus("cancelling");
        run.setUpdatedAt(LocalDateTime.now());
        evalRunsService.updateById(run);
        log.info("评分任务请求取消 evalRunId={}", run.getId());
    }

    /**
     * Phase 5.1：仅重跑「评分汇总」。
     *
     * <p>找最新 run；若 status=failed、progress_stage=scoring、有 finishedCandidates，
     * 则把该 run 重置为 running + progress_stage=scoring，dispatch 一个仅跑 scoring 的轻量任务，
     * 跳过 30+ 分钟的抽取阶段。否则（含产物已被清理 / 任务非 failed / 还没进 scoring 阶段）
     * 抛 IllegalStateException 让 caller 走 trigger 全量重跑。
     *
     * @param buildRunId 构建运行 id
     * @param targetEvalRunId 可选：指定具体 evalRun（必须属于该 buildRun，且 status ∈ {failed, cancelled} +
     *                       progress_stage='scoring' + finished_candidates 非空）。null 时按"最新 run"行为。
     *                       用于"最新 run 是 cancelled 但更早的 run 抽取已完成"场景，让前端按 status.recoverableScoringEvalRunId
     *                       传入历史可恢复 run。
     * @return 复用的 evalRunId
     */
    @Transactional
    public Long retryScoring(Long buildRunId, Long targetEvalRunId) {
        PromptTuneExtractionEvalRuns run;
        if (targetEvalRunId != null && targetEvalRunId > 0) {
            run = evalRunsService.getRequiredById(targetEvalRunId);
            if (!run.getBuildRunId().equals(buildRunId)) {
                throw new BusinessException(
                        ApiResultCode.EXTRACTION_EVAL_NOT_STARTED,
                        HttpStatus.NOT_FOUND,
                        "评分任务不属于当前构建运行"
                );
            }
        } else {
            Optional<PromptTuneExtractionEvalRuns> latestOpt =
                    evalRunsService.findLatestByBuildRunId(buildRunId);
            if (latestOpt.isEmpty()) {
                throw new BusinessException(
                        ApiResultCode.EXTRACTION_EVAL_NOT_STARTED,
                        HttpStatus.NOT_FOUND
                );
            }
            run = latestOpt.get();
        }

        // 复用产物的判定：(failed 或 cancelled) + 有 finished 候选（finished_candidates 非空）。
        // 不依赖 progress_stage——markFailed/markCancelled 把它重置为 'done' 丢失阶段信息。
        boolean canRetryScoring =
                ("failed".equals(run.getStatus()) || "cancelled".equals(run.getStatus()))
                && run.getFinishedCandidates() != null
                && !parseSelectedIds(run.getFinishedCandidates()).isEmpty();
        if (!canRetryScoring) {
            throw new BusinessException(
                    ApiResultCode.EXTRACTION_EVAL_NOT_STARTED,
                    HttpStatus.CONFLICT,
                    "目标任务无可复用的抽取产物，请重新触发完整评分"
            );
        }

        // 同时确保 buildRun 上没有 active 任务（防与正在跑的任务并发）
        Optional<PromptTuneExtractionEvalRuns> active = evalRunsService.findActiveByBuildRunId(buildRunId);
        if (active.isPresent() && !active.get().getId().equals(run.getId())) {
            throw new BusinessException(
                    ApiResultCode.EXTRACTION_EVAL_NOT_STARTED,
                    HttpStatus.CONFLICT,
                    "已有活跃评分任务，请先等待或中止后再补跑"
            );
        }

        run.setStatus("running");
        run.setProgressStage("scoring");
        run.setErrorMessage(null);
        run.setFinishedAt(null);
        run.setLastHeartbeatAt(LocalDateTime.now());
        run.setUpdatedAt(LocalDateTime.now());
        evalRunsService.updateById(run);
        Long evalRunId = run.getId();

        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            worker.dispatchScoringOnly(evalRunId);
        } else {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    worker.dispatchScoringOnly(evalRunId);
                }
            });
        }
        log.info("评分仅重跑 scoring 已派发 evalRunId={}", evalRunId);
        return evalRunId;
    }

    /**
     * 兼容旧签名（无 targetEvalRunId）。
     */
    @Transactional
    public Long retryScoring(Long buildRunId) {
        return retryScoring(buildRunId, null);
    }

    // -----------------------------------------------------------------
    // 辅助
    // -----------------------------------------------------------------

    private PromptTuneExtractionEvalRuns createPending(KnowledgeBaseBuildRuns buildRun, List<String> selected) {
        PromptTuneExtractionEvalRuns run = new PromptTuneExtractionEvalRuns();
        run.setBuildRunId(buildRun.getId());
        run.setKnowledgeBaseId(buildRun.getKnowledgeBaseId());
        run.setSelectedCandidateIds(serialize(selected));
        run.setSeed(resolveSeedFromMetadata(buildRun));  // Phase 4.5 引入：seed 快照
        run.setStatus("pending");
        run.setProgressStage("queued");
        run.setTriggeredByUserId(buildRun.getRequestedByUserId());
        LocalDateTime now = LocalDateTime.now();
        run.setCreatedAt(now);
        run.setUpdatedAt(now);
        evalRunsService.save(run);
        return run;
    }

    private void dispatchAfterCommit(Long evalRunId) {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            worker.dispatch(evalRunId);
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                worker.dispatch(evalRunId);
            }
        });
    }

    private ExtractionEvalRunStartedResponse toStartedResponse(PromptTuneExtractionEvalRuns run, boolean reused) {
        return ExtractionEvalRunStartedResponse.builder()
                .evalRunId(run.getId())
                .buildRunId(run.getBuildRunId())
                .selectedCandidateIds(parseSelectedIds(run.getSelectedCandidateIds()))
                .status(run.getStatus())
                .reusedActiveRun(reused)
                .startedAt(run.getStartedAt())
                .recommendedPollingIntervalMillis(POLLING_INTERVAL_MS)
                .build();
    }

    private ExtractionEvalStatusResponse projectStatus(PromptTuneExtractionEvalRuns run) {
        List<String> selected = parseSelectedIds(run.getSelectedCandidateIds());
        List<String> finished = parseSelectedIds(run.getFinishedCandidates());
        // sampleTotal：build_audit_extraction_set.py 的实际产出条数，由 worker 在
        // ExtractionEvalWorker.runInternal 启动时回填。null（旧记录 / worker 还没回填）按 20 兜底。
        int sampleTotal = run.getSampleTotal() != null && run.getSampleTotal() > 0
                ? run.getSampleTotal()
                : 20;
        // 单候选估算秒数与样本数同步缩放，避免 sample_total<20 时 estimate 严重高估。
        int estimatedCandidateSeconds = ESTIMATED_SECONDS_PER_CANDIDATE * sampleTotal / 20;

        // 整体已用秒数（无 startedAt 时为 null）
        Integer elapsedSeconds = null;
        if (run.getStartedAt() != null) {
            LocalDateTime end = run.getFinishedAt() != null ? run.getFinishedAt() : LocalDateTime.now();
            elapsedSeconds = (int) Duration.between(run.getStartedAt(), end).toSeconds();
        }

        // 估算当前正在抽取的候选完成进度（基于 elapsedSeconds，限 0..sampleTotal-1，永远不到 sampleTotal）：
        // 为什么需要：worker 在 runSingleCandidateExtract 内部阻塞跑 sampleTotal 个样本，
        // 期间不更新 DB；如果不补估算，前端拿到的 extract.finished 长时间是 0，看上去后端卡死。
        // 推算逻辑：当前候选已用秒数 = elapsedSeconds - finished.size() * estimatedCandidateSeconds
        // 估算 finished = 当前候选已用秒数 / 单样本预估秒数（限 0..sampleTotal-1）。
        boolean isExtractingPhase = "extracting".equals(run.getProgressStage())
                && run.getExtractingCandidateId() != null;
        int estimatedCurrentExtractFinished = 0;
        if (isExtractingPhase && elapsedSeconds != null) {
            int currentCandidateElapsed = Math.max(0, elapsedSeconds - finished.size() * estimatedCandidateSeconds);
            int perSampleSec = Math.max(1, estimatedCandidateSeconds / Math.max(1, sampleTotal));
            estimatedCurrentExtractFinished = Math.min(Math.max(0, sampleTotal - 1), currentCandidateElapsed / perSampleSec);
        }

        List<ExtractionEvalStatusResponse.CandidateProgress> progresses = new ArrayList<>();
        CandidateMetadataLookup metadataLookup = new CandidateMetadataLookup();
        for (String id : selected) {
            String candidateStatus;
            if (finished.contains(id)) {
                candidateStatus = "done";
            } else if (id.equals(run.getExtractingCandidateId())) {
                candidateStatus = "scoring".equals(run.getProgressStage()) ? "scoring" : "extracting";
            } else if ("failed".equals(run.getStatus())) {
                candidateStatus = "failed";
            } else {
                candidateStatus = "queued";
            }

            int extractFinished;
            boolean extractEstimated = false;
            if (finished.contains(id)) {
                extractFinished = sampleTotal;
            } else if (id.equals(run.getExtractingCandidateId()) && isExtractingPhase) {
                extractFinished = estimatedCurrentExtractFinished;
                extractEstimated = true;
            } else {
                extractFinished = 0;
            }

            progresses.add(ExtractionEvalStatusResponse.CandidateProgress.builder()
                    .candidateId(id)
                    .displayNameZh(metadataLookup.displayNameZh(id))
                    .status(candidateStatus)
                    .extract(ExtractionEvalStatusResponse.Stage.builder()
                            .finished(extractFinished)
                            .total(sampleTotal)
                            .currentSampleId(null)
                            .estimated(extractEstimated ? Boolean.TRUE : null)
                            .build())
                    .score(ExtractionEvalStatusResponse.Stage.builder()
                            .finished(finished.contains(id) ? 1 : 0)
                            .total(1)
                            .currentSampleId(null)
                            .estimated(null)
                            .build())
                    .build());
        }

        int totalCandidates = selected.size();
        // overall.finishedCalls：已完成候选的真实贡献 + 当前候选的估算贡献
        int finishedCalls = finished.size() * sampleTotal + estimatedCurrentExtractFinished;
        int totalCalls = totalCandidates * sampleTotal;

        Integer estimatedRemainingSeconds = null;
        if (elapsedSeconds != null && finished.size() < totalCandidates) {
            int remaining = (totalCandidates - finished.size()) * estimatedCandidateSeconds;
            estimatedRemainingSeconds = remaining;
        } else if (finished.size() == totalCandidates) {
            estimatedRemainingSeconds = 0;
        }

        // tokens 估算同步缩放：原口径基于 20 样本，sampleTotal 不同则按比例折算
        int tokensPerCandidate = ESTIMATED_TOKEN_PER_CANDIDATE * sampleTotal / 20;
        ExtractionEvalStatusResponse.Overall overall = ExtractionEvalStatusResponse.Overall.builder()
                .finishedCalls(finishedCalls)
                .totalCalls(totalCalls)
                .elapsedSeconds(elapsedSeconds)
                .estimatedRemainingSeconds(estimatedRemainingSeconds)
                .tokensUsed(finished.size() * tokensPerCandidate)
                .estimatedTotalTokens(totalCandidates * tokensPerCandidate)
                .build();

        boolean terminal = "success".equals(run.getStatus())
                || "failed".equals(run.getStatus())
                || "cancelled".equals(run.getStatus());

        // Phase 5.1：失败 + 有完成候选 → 前端可显示「仅重跑评分」按钮。
        // 不依赖 progress_stage——markFailed/markCancelled 会把它重置为 'done'。
        // 仅此处保留 failed-only（cancelled 走更宽的 recoverableScoringEvalRunId 路径），
        // 因为 cancelled 是用户主动终止，不应默认推"仅重跑评分"按钮，需让用户显式按"按上次产物补跑评分"。
        boolean recoverableScoringOnly =
                "failed".equals(run.getStatus())
                && !finished.isEmpty();

        // 失败 / 取消 / 中止终态：附带最近一次 success 的 evalRunId，让前端给「查看上次评分结果」入口。
        // 只在非 success 终态查 DB，减少正常路径的开销。当前 run 自身就是 success 时直接复用其 id。
        Long lastSuccessfulEvalRunId = null;
        if ("success".equals(run.getStatus())) {
            lastSuccessfulEvalRunId = run.getId();
        } else if (terminal) {
            lastSuccessfulEvalRunId = evalRunsService
                    .findLatestSuccessByBuildRunId(run.getBuildRunId())
                    .map(PromptTuneExtractionEvalRuns::getId)
                    .orElse(null);
        }

        // 终态下查"最近一条抽取已基本完成、可补跑评分"的 evalRun。
        // 优先指当前 run（恰好满足 recoverableScoringOnly 同样条件），否则查历史。
        // 用于"最新 run 是 cancelled 但更早的 run 抽取已完成"这类场景，让用户不必重跑抽取阶段。
        Long recoverableScoringEvalRunId = null;
        if (terminal) {
            if (recoverableScoringOnly) {
                recoverableScoringEvalRunId = run.getId();
            } else {
                recoverableScoringEvalRunId = evalRunsService
                        .findLatestRecoverableScoringByBuildRunId(run.getBuildRunId())
                        .map(PromptTuneExtractionEvalRuns::getId)
                        .orElse(null);
            }
        }

        return ExtractionEvalStatusResponse.builder()
                .evalRunId(run.getId())
                .status(run.getStatus())
                .progressStage(run.getProgressStage())
                .errorMessage(run.getErrorMessage())
                .recommendedPollingIntervalMillis(terminal ? null : POLLING_INTERVAL_MS)
                .startedAt(run.getStartedAt())
                .finishedAt(run.getFinishedAt())
                .lastHeartbeatAt(run.getLastHeartbeatAt())
                .recoverableScoringOnly(recoverableScoringOnly ? Boolean.TRUE : null)
                .recoverableScoringEvalRunId(recoverableScoringEvalRunId)
                .lastSuccessfulEvalRunId(lastSuccessfulEvalRunId)
                .overall(overall)
                .candidates(progresses)
                .build();
    }

    private List<String> parseSelectedIds(String json) {
        if (json == null || json.isBlank()) return List.of();
        try {
            return objectMapper.readValue(json, new TypeReference<>() {});
        } catch (JsonProcessingException e) {
            return List.of();
        }
    }

    private String serialize(List<String> ids) {
        try {
            return objectMapper.writeValueAsString(ids);
        } catch (JsonProcessingException e) {
            throw new BusinessException(
                    ApiResultCode.INTERNAL_ERROR,
                    HttpStatus.INTERNAL_SERVER_ERROR,
                    "序列化候选 ID 失败"
            );
        }
    }

    /**
     * Phase 4.5 引入：从 build run metadata 取 seed 写入 eval run 快照。
     * 缺失时返回 null（按"未指定种子"对待，与 Phase 4 兼容）。
     */
    private String resolveSeedFromMetadata(KnowledgeBaseBuildRuns buildRun) {
        String metadata = buildRun.getBuildMetadata();
        if (metadata == null || metadata.isBlank()) return null;
        try {
            JsonNode root = objectMapper.readTree(metadata);
            JsonNode seed = root.path("customPromptDraft").path("seed");
            return seed.isTextual() ? seed.asText() : null;
        } catch (Exception e) {
            return null;
        }
    }
}
