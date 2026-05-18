package org.ysu.ckqaback.controller;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Positive;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.ysu.ckqaback.api.ApiPaths;
import org.ysu.ckqaback.api.ApiResponse;
import org.ysu.ckqaback.api.ApiResponseUtils;
import org.ysu.ckqaback.index.IndexWorkflowService;
import org.ysu.ckqaback.index.KnowledgeBaseBuildRunService;
import org.ysu.ckqaback.index.dto.BuildRunCustomPromptDraftRequest;
import org.ysu.ckqaback.index.dto.BuildRunDetailResponse;
import org.ysu.ckqaback.index.dto.BuildRunGraphInputRequest;
import org.ysu.ckqaback.index.dto.BuildRunIndexRequest;
import org.ysu.ckqaback.index.dto.BuildRunMaterialSelectionRequest;
import org.ysu.ckqaback.index.dto.BuildRunParseCheckRequest;
import org.ysu.ckqaback.index.dto.BuildRunPromptConfirmationRequest;
import org.ysu.ckqaback.index.dto.BuildRunQaSmokeRequest;
import org.ysu.ckqaback.index.dto.BuildRunUpdateRequest;
import org.ysu.ckqaback.index.dto.IndexRunResponse;

import java.io.IOException;

/**
 * 知识库构建流水线控制器。
 */
@Validated
@RestController
@RequiredArgsConstructor
@RequestMapping(ApiPaths.KNOWLEDGE_BASE_BUILD_RUNS)
public class KnowledgeBaseBuildRunsController {

    private final KnowledgeBaseBuildRunService buildRunService;
    private final IndexWorkflowService indexWorkflowService;
    private final org.ysu.ckqaback.index.PromptTuneService promptTuneService;

    @GetMapping("/{id}")
    public ApiResponse<BuildRunDetailResponse> getBuildRun(
            @PathVariable @Positive(message = "id必须大于0") Long id
    ) {
        return ApiResponseUtils.success(buildRunService.getBuildRun(id));
    }

    @PatchMapping("/{id}")
    public ApiResponse<BuildRunDetailResponse> updateBuildRun(
            @PathVariable @Positive(message = "id必须大于0") Long id,
            @Valid @RequestBody BuildRunUpdateRequest request
    ) {
        return ApiResponseUtils.success(buildRunService.updateBuildRun(id, request));
    }

    @DeleteMapping("/{id}")
    public ApiResponse<BuildRunDetailResponse> archiveBuildRun(
            @PathVariable @Positive(message = "id必须大于0") Long id,
            @RequestParam(defaultValue = "true") boolean keepArtifacts,
            @RequestParam(defaultValue = "false") boolean deleteWorkspace
    ) {
        return ApiResponseUtils.success(buildRunService.archiveBuildRun(id, deleteWorkspace || !keepArtifacts));
    }

    @PutMapping("/{id}/material-selection")
    public ApiResponse<BuildRunDetailResponse> updateMaterialSelection(
            @PathVariable @Positive(message = "id必须大于0") Long id,
            @Valid @RequestBody BuildRunMaterialSelectionRequest request
    ) {
        return ApiResponseUtils.success(buildRunService.updateMaterialSelection(id, request));
    }

    @PostMapping("/{id}/parse-check")
    public ApiResponse<BuildRunDetailResponse> checkParse(
            @PathVariable @Positive(message = "id必须大于0") Long id,
            @Valid @RequestBody(required = false) BuildRunParseCheckRequest request
    ) {
        return ApiResponseUtils.success(buildRunService.checkParse(id, request));
    }

    @PostMapping("/{id}/graph-input")
    public ApiResponse<BuildRunDetailResponse> syncGraphInput(
            @PathVariable @Positive(message = "id必须大于0") Long id,
            @Valid @RequestBody(required = false) BuildRunGraphInputRequest request
    ) {
        return ApiResponseUtils.success(buildRunService.syncGraphInput(id, request));
    }

    @PostMapping("/{id}/prompt-confirmation")
    public ApiResponse<BuildRunDetailResponse> confirmPrompt(
            @PathVariable @Positive(message = "id必须大于0") Long id,
            @Valid @RequestBody(required = false) BuildRunPromptConfirmationRequest request
    ) {
        return ApiResponseUtils.success(buildRunService.confirmPrompt(id, request));
    }

    @PutMapping("/{id}/custom-prompt-draft")
    public ApiResponse<BuildRunDetailResponse> saveCustomPromptDraft(
            @PathVariable @Positive(message = "id必须大于0") Long id,
            @Valid @RequestBody BuildRunCustomPromptDraftRequest request
    ) {
        return ApiResponseUtils.success(buildRunService.saveCustomPromptDraft(id, request));
    }

    @PostMapping("/{id}/index-runs")
    public ApiResponse<IndexRunResponse> createBuildRunIndexRun(
            @PathVariable @Positive(message = "id必须大于0") Long id,
            @Valid @RequestBody(required = false) BuildRunIndexRequest request
    ) throws IOException, InterruptedException {
        return ApiResponseUtils.success(indexWorkflowService.createBuildRunIndexRun(id, request == null ? new BuildRunIndexRequest() : request));
    }

    @PostMapping("/{id}/qa-smoke")
    public ApiResponse<BuildRunDetailResponse> runQaSmoke(
            @PathVariable @Positive(message = "id必须大于0") Long id,
            @Valid @RequestBody(required = false) BuildRunQaSmokeRequest request
    ) {
        return ApiResponseUtils.success(buildRunService.runQaSmoke(id, request));
    }

    @PostMapping("/{id}/prompt-tune")
    public ApiResponse<org.ysu.ckqaback.index.dto.PromptTuneRunResponse> triggerPromptTune(
            @PathVariable @Positive(message = "id必须大于0") Long id,
            @RequestBody(required = false) org.ysu.ckqaback.index.dto.PromptTuneTriggerRequest request
    ) {
        boolean force = request != null && Boolean.TRUE.equals(request.getForce());
        return ApiResponseUtils.success(promptTuneService.trigger(id, force));
    }

    @GetMapping("/{id}/prompt-tune")
    public ApiResponse<org.ysu.ckqaback.index.dto.PromptTuneRunResponse> getPromptTuneStatus(
            @PathVariable @Positive(message = "id必须大于0") Long id
    ) {
        return ApiResponseUtils.success(promptTuneService.getLatestStatus(id));
    }
}
