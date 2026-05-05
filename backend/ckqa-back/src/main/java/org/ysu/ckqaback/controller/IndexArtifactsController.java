package org.ysu.ckqaback.controller;

import jakarta.validation.constraints.Positive;
import lombok.RequiredArgsConstructor;
import org.ysu.ckqaback.api.ApiPaths;
import org.ysu.ckqaback.api.ApiResponse;
import org.ysu.ckqaback.api.ApiResponseUtils;
import org.ysu.ckqaback.index.dto.IndexArtifactResponse;
import org.ysu.ckqaback.service.IndexArtifactsService;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * <p>
 * 索引产物表 前端控制器
 * </p>
 *
 * @author codex
 * @since 2026-04-21
 */
@Validated
@RestController
@RequiredArgsConstructor
@RequestMapping(ApiPaths.INDEX_ARTIFACTS)
public class IndexArtifactsController {

    private final IndexArtifactsService indexArtifactsService;

    @GetMapping("/{id}")
    public ApiResponse<IndexArtifactResponse> getIndexArtifact(
            @PathVariable @Positive(message = "id必须大于0") Long id
    ) {
        return ApiResponseUtils.success(IndexArtifactResponse.fromEntity(indexArtifactsService.getRequiredById(id)));
    }

    @DeleteMapping("/{id}")
    public ApiResponse<IndexArtifactResponse> deleteIndexArtifact(
            @PathVariable @Positive(message = "id必须大于0") Long id
    ) {
        return ApiResponseUtils.success(IndexArtifactResponse.fromEntity(indexArtifactsService.markDeleted(id)));
    }
}
