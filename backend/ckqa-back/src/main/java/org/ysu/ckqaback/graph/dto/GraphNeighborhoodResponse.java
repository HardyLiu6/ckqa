package org.ysu.ckqaback.graph.dto;

import lombok.Getter;

import java.util.List;

/**
 * 学生端知识图谱「实体邻域」接口响应体。
 */
@Getter
public class GraphNeighborhoodResponse {

    private final String centerId;
    private final List<GraphNodeResponse> nodes;
    private final List<GraphEdgeResponse> edges;
    private final GraphLimitInfo limits;

    public GraphNeighborhoodResponse(String centerId, List<GraphNodeResponse> nodes,
                                     List<GraphEdgeResponse> edges, GraphLimitInfo limits) {
        this.centerId = centerId;
        this.nodes = nodes;
        this.edges = edges;
        this.limits = limits;
    }
}
