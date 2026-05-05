package org.ysu.ckqaback.controller;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Positive;
import lombok.RequiredArgsConstructor;
import org.ysu.ckqaback.api.ApiPageData;
import org.ysu.ckqaback.api.ApiPaths;
import org.ysu.ckqaback.api.ApiResponse;
import org.ysu.ckqaback.api.ApiResponseUtils;
import org.ysu.ckqaback.index.IndexWorkflowService;
import org.ysu.ckqaback.index.ActiveIndexRunService;
import org.ysu.ckqaback.index.KnowledgeBaseBuildRunService;
import org.ysu.ckqaback.index.KnowledgeBaseLookupService;
import org.ysu.ckqaback.index.dto.ActiveIndexRunRequest;
import org.ysu.ckqaback.index.dto.ActiveIndexRunResponse;
import org.ysu.ckqaback.index.dto.BuildRunCreateRequest;
import org.ysu.ckqaback.index.dto.BuildRunDetailResponse;
import org.ysu.ckqaback.index.dto.BuildRunGcRequest;
import org.ysu.ckqaback.index.dto.BuildRunGcResponse;
import org.ysu.ckqaback.index.dto.BuildRunSummaryResponse;
import org.ysu.ckqaback.index.dto.IndexRunResponse;
import org.ysu.ckqaback.index.dto.KnowledgeBaseCreateRequest;
import org.ysu.ckqaback.index.dto.KnowledgeBaseDetailResponse;
import org.ysu.ckqaback.index.dto.KnowledgeBaseQueryRequest;
import org.ysu.ckqaback.index.dto.KnowledgeBaseSummaryResponse;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;
import java.util.List;

/**
 * <p>
 * 课程知识库表 前端控制器
 * </p>
 *
 * @author codex
 * @since 2026-04-21
 */
@Validated
@RestController
@RequiredArgsConstructor
@RequestMapping(ApiPaths.KNOWLEDGE_BASES)
public class KnowledgeBasesController {

    private final IndexWorkflowService indexWorkflowService;
    private final KnowledgeBaseLookupService knowledgeBaseLookupService;
    private final KnowledgeBaseBuildRunService buildRunService;
    private final ActiveIndexRunService activeIndexRunService;

    @GetMapping
    public ApiResponse<ApiPageData<KnowledgeBaseSummaryResponse>> listKnowledgeBases(
            @Valid @ModelAttribute KnowledgeBaseQueryRequest request
    ) {
        return ApiResponseUtils.success(knowledgeBaseLookupService.listKnowledgeBases(request));
    }

    @PostMapping
    public ApiResponse<KnowledgeBaseDetailResponse> createKnowledgeBase(
            @Valid @RequestBody KnowledgeBaseCreateRequest request
    ) {
        return ApiResponseUtils.success(knowledgeBaseLookupService.createKnowledgeBase(request));
    }

    @GetMapping("/{id}")
    public ApiResponse<KnowledgeBaseDetailResponse> getKnowledgeBase(
            @PathVariable @Positive(message = "id必须大于0") Long id
    ) {
        return ApiResponseUtils.success(knowledgeBaseLookupService.getKnowledgeBase(id));
    }

    @PostMapping("/{id}/index-runs")
    public ApiResponse<IndexRunResponse> createIndexRun(@PathVariable @Positive(message = "id必须大于0") Long id)
            throws IOException, InterruptedException {
        return ApiResponseUtils.success(indexWorkflowService.createIndexRun(id));
    }

    @GetMapping("/{id}/index-runs")
    public ApiResponse<List<IndexRunResponse>> listIndexRuns(@PathVariable @Positive(message = "id必须大于0") Long id) {
        return ApiResponseUtils.success(indexWorkflowService.listIndexRuns(id));
    }

    @PostMapping("/{id}/build-runs")
    public ApiResponse<BuildRunDetailResponse> createBuildRun(
            @PathVariable @Positive(message = "id必须大于0") Long id,
            @Valid @RequestBody BuildRunCreateRequest request
    ) {
        return ApiResponseUtils.success(buildRunService.createBuildRun(id, request));
    }

    @GetMapping("/{id}/build-runs")
    public ApiResponse<ApiPageData<BuildRunSummaryResponse>> listBuildRuns(
            @PathVariable @Positive(message = "id必须大于0") Long id,
            @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "1") Long page,
            @RequestParam(defaultValue = "20") Long size
    ) {
        return ApiResponseUtils.success(buildRunService.listBuildRuns(id, status, page, size));
    }

    @PostMapping("/{id}/build-runs/gc")
    public ApiResponse<BuildRunGcResponse> gcBuildRuns(
            @PathVariable @Positive(message = "id必须大于0") Long id,
            @Valid @RequestBody(required = false) BuildRunGcRequest request
    ) {
        return ApiResponseUtils.success(buildRunService.gcBuildRuns(id, request));
    }

    @PostMapping("/{id}/active-index-run")
    public ApiResponse<ActiveIndexRunResponse> activateIndexRun(
            @PathVariable @Positive(message = "id必须大于0") Long id,
            @Valid @RequestBody ActiveIndexRunRequest request
    ) {
        return ApiResponseUtils.success(activeIndexRunService.activate(id, request.getIndexRunId(), true));
    }
}
