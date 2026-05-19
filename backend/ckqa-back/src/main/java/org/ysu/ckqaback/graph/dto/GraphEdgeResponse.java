package org.ysu.ckqaback.graph.dto;

import lombok.Getter;

/**
 * 学生端图谱接口关系（边）响应体。
 * <p>
 * description 字段在子图响应中已经截断；如需全文请走实体 / 社区详情接口。
 * </p>
 */
@Getter
public class GraphEdgeResponse {

    private final String id;
    private final String source;
    private final String target;
    private final double weight;
    private final String description;

    private GraphEdgeResponse(String id, String source, String target, double weight, String description) {
        this.id = id;
        this.source = source;
        this.target = target;
        this.weight = weight;
        this.description = description;
    }

    public static GraphEdgeResponse of(String id, String source, String target, double weight, String description) {
        return new GraphEdgeResponse(id, source, target, weight, description);
    }
}
