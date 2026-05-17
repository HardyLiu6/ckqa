package org.ysu.ckqaback.controller;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Positive;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.ysu.ckqaback.api.ApiPaths;
import org.ysu.ckqaback.api.ApiResponse;
import org.ysu.ckqaback.api.ApiResponseUtils;
import org.ysu.ckqaback.api.ApiResultCode;
import org.ysu.ckqaback.exception.BusinessException;
import org.ysu.ckqaback.index.AiSuggestionService;
import org.ysu.ckqaback.index.AuditSampleService;
import org.ysu.ckqaback.index.CandidateService;
import org.ysu.ckqaback.index.ExtractionEvalService;
import org.ysu.ckqaback.index.FinalizePromptService;
import org.ysu.ckqaback.index.PromptDraftListService;
import org.ysu.ckqaback.index.SeedAvailabilityService;
import org.ysu.ckqaback.index.dto.AiSuggestionResponse;
import org.ysu.ckqaback.index.dto.AuditSampleResponse;
import org.ysu.ckqaback.index.dto.AuditSampleUpdateRequest;
import org.ysu.ckqaback.index.dto.BuildRunDetailResponse;
import org.ysu.ckqaback.index.dto.CandidateResponse;
import org.ysu.ckqaback.index.dto.ExtractionEvalRequest;
import org.ysu.ckqaback.index.dto.ExtractionEvalReportResponse;
import org.ysu.ckqaback.index.dto.ExtractionEvalRunStartedResponse;
import org.ysu.ckqaback.index.dto.ExtractionEvalStatusResponse;
import org.ysu.ckqaback.index.dto.FinalizePromptRequest;
import org.ysu.ckqaback.index.dto.PipelineStepResponse;
import org.ysu.ckqaback.index.dto.PromptDraftResponse;
import org.ysu.ckqaback.index.dto.SeedAvailabilityResponse;

import java.util.List;
import java.util.Map;

/**
 * 手动调优提示词流水线控制器（Phase 2a 占位）。
 * <p>
 * 本期所有端点统一返回 HTTP 501，2b–2e 分阶段落地具体实现：
 * <ul>
 *   <li>2b：02 步标注 API（audit-set / audit-samples / 更新 / ai-suggestions）</li>
 *   <li>2c：03 步候选 API（candidates / 候选预览相关）</li>
 *   <li>2d：04 步评分 API（extraction-eval 三件套）</li>
 *   <li>2e：05 步保存 + 历史草稿（finalize / prompt-drafts）+ relation-schemas</li>
 * </ul>
 * </p>
 */
@Validated
@RestController
@RequiredArgsConstructor
public class PromptTunePipelineController {

    private final AuditSampleService auditSampleService;
    private final AiSuggestionService aiSuggestionService;
    private final CandidateService candidateService;
    private final SeedAvailabilityService seedAvailabilityService;
    private final ExtractionEvalService extractionEvalService;
    private final FinalizePromptService finalizePromptService;
    private final PromptDraftListService promptDraftListService;

    // ------------------------------------------------------------
    // 02 步：构建准备材料
    // ------------------------------------------------------------

    /**
     * 触发"仅 02.1 生成调优样本集"。本期不实现：
     * 前端始终通过 {@link #generateAuditSet} 串跑 02.1+02.2，
     * 该端点暂保留 501 占位以维持 API 兼容。
     */
    @PostMapping(ApiPaths.KNOWLEDGE_BASE_BUILD_RUNS + "/{id}/prompt-tune-samples")
    public ApiResponse<PipelineStepResponse> triggerPromptTuneSamples(
            @PathVariable @Positive(message = "id必须大于0") Long id
    ) {
        throw notImplemented();
    }

    @PostMapping(ApiPaths.KNOWLEDGE_BASE_BUILD_RUNS + "/{id}/audit-set")
    public ApiResponse<List<AuditSampleResponse>> generateAuditSet(
            @PathVariable("id") @Positive(message = "id必须大于0") Long buildRunId,
            @RequestParam(name = "force", defaultValue = "false") boolean force
    ) {
        return ApiResponseUtils.success(auditSampleService.regenerateAuditSet(buildRunId, force));
    }

    @GetMapping(ApiPaths.KNOWLEDGE_BASE_BUILD_RUNS + "/{id}/audit-samples")
    public ApiResponse<List<AuditSampleResponse>> listAuditSamples(
            @PathVariable("id") @Positive(message = "id必须大于0") Long buildRunId
    ) {
        return ApiResponseUtils.success(auditSampleService.listSamples(buildRunId));
    }

    @PutMapping(ApiPaths.KNOWLEDGE_BASE_BUILD_RUNS + "/{id}/audit-samples/{sampleId}")
    public ApiResponse<AuditSampleResponse> updateAuditSample(
            @PathVariable("id") @Positive(message = "id必须大于0") Long buildRunId,
            @PathVariable("sampleId") @Positive(message = "sampleId必须大于0") Long sampleId,
            @Valid @RequestBody AuditSampleUpdateRequest request
    ) {
        return ApiResponseUtils.success(auditSampleService.updateSample(buildRunId, sampleId, request));
    }

    /**
     * AI 预填实体/关系候选（Phase 3 智能能力 A）。
     * <p>
     * 同步调用 GraphRAG 单样本抽取（典型耗时 10-30 秒），返回候选实体/关系。
     * 候选不入 DB——前端落到 sample.aiSuggestedEntities/aiSuggestedRelations 局部状态，
     * 用户逐条审阅，被采纳后才进 goldEntities/goldRelations。
     * </p>
     */
    @PostMapping(ApiPaths.KNOWLEDGE_BASE_BUILD_RUNS + "/{id}/audit-samples/{sampleId}/ai-suggestions")
    public ApiResponse<AiSuggestionResponse> requestAuditSampleAiSuggestions(
            @PathVariable("id") @Positive(message = "id必须大于0") Long buildRunId,
            @PathVariable("sampleId") @Positive(message = "sampleId必须大于0") Long sampleId
    ) {
        return ApiResponseUtils.success(aiSuggestionService.generate(buildRunId, sampleId));
    }

    // ------------------------------------------------------------
    // 03 步：生成候选提示词
    // ------------------------------------------------------------

    /**
     * Phase 4.5：返回 01 步 3 个种子选项各自的可用状态。
     */
    @GetMapping(ApiPaths.KNOWLEDGE_BASE_BUILD_RUNS + "/{id}/seed-availability")
    public ApiResponse<SeedAvailabilityResponse> getSeedAvailability(
            @PathVariable("id") @Positive(message = "id必须大于0") Long buildRunId
    ) {
        return ApiResponseUtils.success(seedAvailabilityService.evaluate(buildRunId));
    }

    /**
     * 03 步：触发候选 prompt 生成。
     *
     * <p>覆盖式：每次调用都会重新执行 {@code generate_candidate_prompts.py}（脚本实测 ~66 ms），
     * 把含 DB gold 的 audit JSON 喂给脚本，输出 4 个候选到 build run workspace 下的 prompt/candidates 目录。</p>
     */
    @PostMapping(ApiPaths.KNOWLEDGE_BASE_BUILD_RUNS + "/{id}/candidates")
    public ApiResponse<List<CandidateResponse>> generateCandidates(
            @PathVariable("id") @Positive(message = "id必须大于0") Long buildRunId
    ) {
        return ApiResponseUtils.success(candidateService.generate(buildRunId));
    }

    /**
     * 03 步：纯只读列出当前 build run 已生成的候选。
     * <p>未生成时抛 4105，前端据此显示空态 + "立即生成"按钮。</p>
     */
    @GetMapping(ApiPaths.KNOWLEDGE_BASE_BUILD_RUNS + "/{id}/candidates")
    public ApiResponse<List<CandidateResponse>> listCandidates(
            @PathVariable("id") @Positive(message = "id必须大于0") Long buildRunId
    ) {
        return ApiResponseUtils.success(candidateService.list(buildRunId));
    }

    /**
     * 03 步：抽屉懒加载某个候选的 prompt 文件全文。
     * candidateId 必须 ∈ 4 个白名单值。
     */
    @GetMapping(ApiPaths.KNOWLEDGE_BASE_BUILD_RUNS + "/{id}/candidates/{candidateId}/prompt")
    public ApiResponse<String> getCandidatePromptText(
            @PathVariable("id") @Positive(message = "id必须大于0") Long buildRunId,
            @PathVariable("candidateId") String candidateId
    ) {
        return ApiResponseUtils.success(candidateService.loadPromptText(buildRunId, candidateId));
    }

    // ------------------------------------------------------------
    // 04 步：抽取评分
    // ------------------------------------------------------------

    /**
     * 04 步：触发候选 prompt 评分。
     *
     * <p>异步：立即返回 evalRunId + status=pending（或复用已有 active 任务），前端轮询
     * status 接口跟踪进度。门控：02 ≥1 completed + 03 候选已生成 + selectedCandidates ⊆ 已生成。</p>
     */
    @PostMapping(ApiPaths.KNOWLEDGE_BASE_BUILD_RUNS + "/{id}/extraction-eval")
    public ApiResponse<ExtractionEvalRunStartedResponse> startExtractionEval(
            @PathVariable("id") @Positive(message = "id必须大于0") Long buildRunId,
            @Valid @RequestBody ExtractionEvalRequest request
    ) {
        return ApiResponseUtils.success(extractionEvalService.trigger(buildRunId, request));
    }

    /**
     * 04 步：查询评分进度。前端按 recommendedPollingIntervalMillis 轮询；
     * 任务终态时该字段为 null，前端停止轮询。
     */
    @GetMapping(ApiPaths.KNOWLEDGE_BASE_BUILD_RUNS + "/{id}/extraction-eval/status")
    public ApiResponse<ExtractionEvalStatusResponse> getExtractionEvalStatus(
            @PathVariable("id") @Positive(message = "id必须大于0") Long buildRunId
    ) {
        return ApiResponseUtils.success(extractionEvalService.getStatus(buildRunId));
    }

    /**
     * 04 步：评分完成后获取排行榜与候选指标详情。
     */
    @GetMapping(ApiPaths.KNOWLEDGE_BASE_BUILD_RUNS + "/{id}/extraction-eval/report")
    public ApiResponse<ExtractionEvalReportResponse> getExtractionEvalReport(
            @PathVariable("id") @Positive(message = "id必须大于0") Long buildRunId
    ) {
        return ApiResponseUtils.success(extractionEvalService.getReport(buildRunId));
    }

    /**
     * 04 步：中止评分。
     *
     * <p>软取消：把 active 任务状态切到 cancelling，worker 在下一个候选切换前自感知后落
     * 终态 cancelled。无 active 任务时幂等返回 200。</p>
     */
    @PostMapping(ApiPaths.KNOWLEDGE_BASE_BUILD_RUNS + "/{id}/extraction-eval/cancel")
    public ApiResponse<Void> cancelExtractionEval(
            @PathVariable("id") @Positive(message = "id必须大于0") Long buildRunId
    ) {
        extractionEvalService.cancel(buildRunId);
        return ApiResponseUtils.success(null);
    }

    /**
     * Phase 5.1：仅重跑「评分汇总」。前端在 status.recoverableScoringOnly=true 时点击
     * 「仅重跑评分」按钮触发；不满足条件返回 4106 + CONFLICT 让 caller 走 trigger 全量重跑。
     */
    @PostMapping(ApiPaths.KNOWLEDGE_BASE_BUILD_RUNS + "/{id}/extraction-eval/retry-scoring")
    public ApiResponse<Long> retryExtractionEvalScoring(
            @PathVariable("id") @Positive(message = "id必须大于0") Long buildRunId
    ) {
        return ApiResponseUtils.success(extractionEvalService.retryScoring(buildRunId));
    }

    // ------------------------------------------------------------
    // 05 步：预览保存
    // ------------------------------------------------------------

    @PostMapping(ApiPaths.KNOWLEDGE_BASE_BUILD_RUNS + "/{id}/finalize")
    public ApiResponse<BuildRunDetailResponse> finalizePrompt(
            @PathVariable("id") @Positive(message = "id必须大于0") Long buildRunId,
            @Valid @RequestBody FinalizePromptRequest request
    ) {
        return ApiResponseUtils.success(finalizePromptService.finalizePrompt(buildRunId, request));
    }

    @GetMapping(ApiPaths.KNOWLEDGE_BASES + "/{kbId}/prompt-drafts")
    public ApiResponse<List<PromptDraftResponse>> listPromptDrafts(
            @PathVariable("kbId") @Positive(message = "kbId必须大于0") Long knowledgeBaseId
    ) {
        return ApiResponseUtils.success(promptDraftListService.list(knowledgeBaseId));
    }

    // ------------------------------------------------------------
    // 通用辅助：02 步标注下拉过滤数据
    // ------------------------------------------------------------

    @GetMapping(ApiPaths.RELATION_SCHEMAS)
    public ApiResponse<Map<String, Object>> listRelationSchemas() {
        throw notImplemented();
    }

    // ------------------------------------------------------------
    // 私有辅助
    // ------------------------------------------------------------

    private static BusinessException notImplemented() {
        return new BusinessException(ApiResultCode.PIPELINE_NOT_IMPLEMENTED, HttpStatus.NOT_IMPLEMENTED);
    }
}
