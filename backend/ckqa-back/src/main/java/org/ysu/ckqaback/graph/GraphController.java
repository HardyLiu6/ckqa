package org.ysu.ckqaback.graph;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.ysu.ckqaback.api.ApiPaths;
import org.ysu.ckqaback.api.ApiResponse;
import org.ysu.ckqaback.api.ApiResponseUtils;
import org.ysu.ckqaback.graph.dto.GraphEntityDetailResponse;
import org.ysu.ckqaback.graph.dto.GraphNeighborhoodResponse;
import org.ysu.ckqaback.graph.dto.GraphOverviewResponse;

/**
 * 学生端知识图谱只读控制器。
 * <p>
 * 仅暴露读接口，所有路径挂在 {@code /api/v1/knowledge-bases/{id}/graph}：
 * </p>
 * <ul>
 *   <li>GET overview：顶层社区 + 每社区 Top-N 实体 + 实体间关系</li>
 *   <li>GET entities/{entityId}/neighborhood：实体 1 跳邻域</li>
 *   <li>GET entities/{entityId}：实体详情（含全文 description / 社区路径 / chunk 数）</li>
 * </ul>
 */
@Validated
@RestController
@RequiredArgsConstructor
@RequestMapping(ApiPaths.KNOWLEDGE_BASES + "/{id}/graph")
public class GraphController {

    private final GraphService graphService;

    @GetMapping("/overview")
    public ApiResponse<GraphOverviewResponse> overview(
            @PathVariable @Positive(message = "id必须大于0") Long id,
            @RequestParam(value = "level", required = false) Integer level,
            @RequestParam(value = "topN", required = false) Integer topN
    ) {
        return ApiResponseUtils.success(graphService.getOverview(id, level, topN));
    }

    @GetMapping("/entities/{entityId}/neighborhood")
    public ApiResponse<GraphNeighborhoodResponse> neighborhood(
            @PathVariable @Positive(message = "id必须大于0") Long id,
            @PathVariable @NotBlank(message = "entityId不能为空") String entityId,
            @RequestParam(value = "depth", required = false) Integer depth,
            @RequestParam(value = "limit", required = false) Integer limit
    ) {
        return ApiResponseUtils.success(graphService.getEntityNeighborhood(id, entityId, depth, limit));
    }

    @GetMapping("/entities/{entityId}")
    public ApiResponse<GraphEntityDetailResponse> entityDetail(
            @PathVariable @Positive(message = "id必须大于0") Long id,
            @PathVariable @NotBlank(message = "entityId不能为空") String entityId
    ) {
        return ApiResponseUtils.success(graphService.getEntityDetail(id, entityId));
    }
}
