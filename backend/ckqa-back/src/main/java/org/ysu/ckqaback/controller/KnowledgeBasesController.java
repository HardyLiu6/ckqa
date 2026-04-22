package org.ysu.ckqaback.controller;

import jakarta.validation.constraints.Positive;
import lombok.RequiredArgsConstructor;
import org.ysu.ckqaback.api.ApiPaths;
import org.ysu.ckqaback.api.ApiResponse;
import org.ysu.ckqaback.api.ApiResponseUtils;
import org.ysu.ckqaback.index.IndexWorkflowService;
import org.ysu.ckqaback.index.dto.IndexRunResponse;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
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

    @PostMapping("/{id}/index-runs")
    public ApiResponse<IndexRunResponse> createIndexRun(@PathVariable @Positive(message = "id必须大于0") Long id)
            throws IOException, InterruptedException {
        return ApiResponseUtils.success(indexWorkflowService.createIndexRun(id));
    }

    @GetMapping("/{id}/index-runs")
    public ApiResponse<List<IndexRunResponse>> listIndexRuns(@PathVariable @Positive(message = "id必须大于0") Long id) {
        return ApiResponseUtils.success(indexWorkflowService.listIndexRuns(id));
    }
}
