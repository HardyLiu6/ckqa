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
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * <p>
 * 索引运行表 前端控制器
 * </p>
 *
 * @author codex
 * @since 2026-04-21
 */
@Validated
@RestController
@RequiredArgsConstructor
@RequestMapping(ApiPaths.INDEX_RUNS)
public class IndexRunsController {

    private final IndexWorkflowService indexWorkflowService;

    @GetMapping("/{id}")
    public ApiResponse<IndexRunResponse> getIndexRun(@PathVariable @Positive(message = "id必须大于0") Long id) {
        return ApiResponseUtils.success(indexWorkflowService.getIndexRun(id));
    }
}
