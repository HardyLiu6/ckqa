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
import org.springframework.web.bind.annotation.RestController;
import org.ysu.ckqaback.api.ApiPaths;
import org.ysu.ckqaback.api.ApiResponse;
import org.ysu.ckqaback.api.ApiResultCode;
import org.ysu.ckqaback.exception.BusinessException;
import org.ysu.ckqaback.index.dto.AuditSampleResponse;
import org.ysu.ckqaback.index.dto.AuditSampleUpdateRequest;
import org.ysu.ckqaback.index.dto.BuildRunDetailResponse;
import org.ysu.ckqaback.index.dto.CandidateResponse;
import org.ysu.ckqaback.index.dto.ExtractionEvalRequest;
import org.ysu.ckqaback.index.dto.ExtractionEvalReportResponse;
import org.ysu.ckqaback.index.dto.ExtractionEvalStatusResponse;
import org.ysu.ckqaback.index.dto.FinalizePromptRequest;
import org.ysu.ckqaback.index.dto.PipelineStepResponse;
import org.ysu.ckqaback.index.dto.PromptDraftResponse;

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

    // ------------------------------------------------------------
    // 02 步：构建准备材料
    // ------------------------------------------------------------

    @PostMapping(ApiPaths.KNOWLEDGE_BASE_BUILD_RUNS + "/{id}/prompt-tune-samples")
    public ApiResponse<PipelineStepResponse> triggerPromptTuneSamples(
            @PathVariable @Positive(message = "id必须大于0") Long id
    ) {
        throw notImplemented();
    }

    @PostMapping(ApiPaths.KNOWLEDGE_BASE_BUILD_RUNS + "/{id}/audit-set")
    public ApiResponse<List<AuditSampleResponse>> generateAuditSet(
            @PathVariable @Positive(message = "id必须大于0") Long id
    ) {
        throw notImplemented();
    }

    @GetMapping(ApiPaths.KNOWLEDGE_BASE_BUILD_RUNS + "/{id}/audit-samples")
    public ApiResponse<List<AuditSampleResponse>> listAuditSamples(
            @PathVariable @Positive(message = "id必须大于0") Long id
    ) {
        throw notImplemented();
    }

    @PutMapping(ApiPaths.KNOWLEDGE_BASE_BUILD_RUNS + "/{id}/audit-samples/{sampleId}")
    public ApiResponse<AuditSampleResponse> updateAuditSample(
            @PathVariable @Positive(message = "id必须大于0") Long id,
            @PathVariable @Positive(message = "sampleId必须大于0") Long sampleId,
            @Valid @RequestBody AuditSampleUpdateRequest request
    ) {
        throw notImplemented();
    }

    @PostMapping(ApiPaths.KNOWLEDGE_BASE_BUILD_RUNS + "/{id}/audit-samples/{sampleId}/ai-suggestions")
    public ApiResponse<Map<String, Object>> requestAuditSampleAiSuggestions(
            @PathVariable @Positive(message = "id必须大于0") Long id,
            @PathVariable @Positive(message = "sampleId必须大于0") Long sampleId
    ) {
        throw notImplemented();
    }

    // ------------------------------------------------------------
    // 03 步：生成候选提示词
    // ------------------------------------------------------------

    @PostMapping(ApiPaths.KNOWLEDGE_BASE_BUILD_RUNS + "/{id}/candidates")
    public ApiResponse<PipelineStepResponse> generateCandidates(
            @PathVariable @Positive(message = "id必须大于0") Long id
    ) {
        throw notImplemented();
    }

    @GetMapping(ApiPaths.KNOWLEDGE_BASE_BUILD_RUNS + "/{id}/candidates")
    public ApiResponse<List<CandidateResponse>> listCandidates(
            @PathVariable @Positive(message = "id必须大于0") Long id
    ) {
        throw notImplemented();
    }

    // ------------------------------------------------------------
    // 04 步：抽取评分
    // ------------------------------------------------------------

    @PostMapping(ApiPaths.KNOWLEDGE_BASE_BUILD_RUNS + "/{id}/extraction-eval")
    public ApiResponse<PipelineStepResponse> startExtractionEval(
            @PathVariable @Positive(message = "id必须大于0") Long id,
            @Valid @RequestBody ExtractionEvalRequest request
    ) {
        throw notImplemented();
    }

    @GetMapping(ApiPaths.KNOWLEDGE_BASE_BUILD_RUNS + "/{id}/extraction-eval/status")
    public ApiResponse<ExtractionEvalStatusResponse> getExtractionEvalStatus(
            @PathVariable @Positive(message = "id必须大于0") Long id
    ) {
        throw notImplemented();
    }

    @GetMapping(ApiPaths.KNOWLEDGE_BASE_BUILD_RUNS + "/{id}/extraction-eval/report")
    public ApiResponse<ExtractionEvalReportResponse> getExtractionEvalReport(
            @PathVariable @Positive(message = "id必须大于0") Long id
    ) {
        throw notImplemented();
    }

    // ------------------------------------------------------------
    // 05 步：预览保存
    // ------------------------------------------------------------

    @PostMapping(ApiPaths.KNOWLEDGE_BASE_BUILD_RUNS + "/{id}/finalize")
    public ApiResponse<BuildRunDetailResponse> finalizePrompt(
            @PathVariable @Positive(message = "id必须大于0") Long id,
            @Valid @RequestBody FinalizePromptRequest request
    ) {
        throw notImplemented();
    }

    @GetMapping(ApiPaths.KNOWLEDGE_BASES + "/{kbId}/prompt-drafts")
    public ApiResponse<List<PromptDraftResponse>> listPromptDrafts(
            @PathVariable @Positive(message = "kbId必须大于0") Long kbId
    ) {
        throw notImplemented();
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
