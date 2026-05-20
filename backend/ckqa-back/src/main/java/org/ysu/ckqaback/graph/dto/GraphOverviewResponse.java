package org.ysu.ckqaback.graph.dto;

import lombok.Getter;

import java.util.List;

/**
 * 学生端知识图谱「总览」接口响应体。
 */
@Getter
public class GraphOverviewResponse {

    private final Long knowledgeBaseId;
    private final Long indexRunId;
    private final int level;
    private final List<GraphCommunityOverview> communities;
    private final List<GraphNodeResponse> nodes;
    private final List<GraphEdgeResponse> edges;
    private final GraphLimitInfo limits;

    public GraphOverviewResponse(Long knowledgeBaseId, Long indexRunId, int level,
                                 List<GraphCommunityOverview> communities,
                                 List<GraphNodeResponse> nodes,
                                 List<GraphEdgeResponse> edges,
                                 GraphLimitInfo limits) {
        this.knowledgeBaseId = knowledgeBaseId;
        this.indexRunId = indexRunId;
        this.level = level;
        this.communities = communities;
        this.nodes = nodes;
        this.edges = edges;
        this.limits = limits;
    }
}
