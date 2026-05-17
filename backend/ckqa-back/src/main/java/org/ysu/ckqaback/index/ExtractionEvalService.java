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

    /** 单候选预估 token（与 candidates 列表中估算保持口径一致），用于 overall.estimatedTotalTokens。 */
    private static final int ESTIMATED_TOKEN_PER_CANDIDATE = 5000 * 20;

    /** 单候选 20 样本预估总耗时秒数（5-15 分钟，取中位 8 分钟）。 */
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

        // 门控 3：selectedCandidates ⊆ 已生成候选
        Set<String> generatedIds = new LinkedHashSet<>();
        for (CandidateResponse c : candidates) generatedIds.add(c.getCandidateId());
        List<String> selected = request.getSelectedCandidates();
        List<String> unknown = new ArrayList<>();
        for (String id : selected) {
            if (!generatedIds.contains(id)) unknown.add(id);
        }
        if (!unknown.isEmpty()) {
            throw new BusinessException(
                    ApiResultCode.INVALID_EVAL_CANDIDATE_SELECTION,
                    HttpStatus.BAD_REQUEST,
                    "未识别的候选 ID：" + String.join(", ", unknown)
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
            int extractFinished = finished.contains(id) ? 20 : 0;
            progresses.add(ExtractionEvalStatusResponse.CandidateProgress.builder()
                    .candidateId(id)
                    .displayNameZh(metadataLookup.displayNameZh(id))
                    .status(candidateStatus)
                    .extract(ExtractionEvalStatusResponse.Stage.builder()
                            .finished(extractFinished)
                            .total(20)
                            .currentSampleId(null)
                            .build())
                    .score(ExtractionEvalStatusResponse.Stage.builder()
                            .finished(finished.contains(id) ? 1 : 0)
                            .total(1)
                            .currentSampleId(null)
                            .build())
                    .build());
        }

        int totalCandidates = selected.size();
        int finishedCalls = finished.size() * 20;
        int totalCalls = totalCandidates * 20;

        Integer elapsedSeconds = null;
        if (run.getStartedAt() != null) {
            LocalDateTime end = run.getFinishedAt() != null ? run.getFinishedAt() : LocalDateTime.now();
            elapsedSeconds = (int) Duration.between(run.getStartedAt(), end).toSeconds();
        }
        Integer estimatedRemainingSeconds = null;
        if (elapsedSeconds != null && finished.size() < totalCandidates) {
            int remaining = (totalCandidates - finished.size()) * ESTIMATED_SECONDS_PER_CANDIDATE;
            estimatedRemainingSeconds = remaining;
        } else if (finished.size() == totalCandidates) {
            estimatedRemainingSeconds = 0;
        }

        ExtractionEvalStatusResponse.Overall overall = ExtractionEvalStatusResponse.Overall.builder()
                .finishedCalls(finishedCalls)
                .totalCalls(totalCalls)
                .elapsedSeconds(elapsedSeconds)
                .estimatedRemainingSeconds(estimatedRemainingSeconds)
                .tokensUsed(finished.size() * ESTIMATED_TOKEN_PER_CANDIDATE)
                .estimatedTotalTokens(totalCandidates * ESTIMATED_TOKEN_PER_CANDIDATE)
                .build();

        boolean terminal = "success".equals(run.getStatus())
                || "failed".equals(run.getStatus())
                || "cancelled".equals(run.getStatus());

        return ExtractionEvalStatusResponse.builder()
                .evalRunId(run.getId())
                .status(run.getStatus())
                .progressStage(run.getProgressStage())
                .errorMessage(run.getErrorMessage())
                .recommendedPollingIntervalMillis(terminal ? null : POLLING_INTERVAL_MS)
                .startedAt(run.getStartedAt())
                .finishedAt(run.getFinishedAt())
                .lastHeartbeatAt(run.getLastHeartbeatAt())
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
