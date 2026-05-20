package org.ysu.ckqaback.graph.dto;

import lombok.Getter;

/**
 * 学生端图谱接口节点节点轻量响应体。
 * <p>
 * 仅包含前端绘制必要字段：id、name、type、所属社区、度数。详情字段（description / summary / chunk）
 * 留给独立的实体详情 / 社区详情接口返回。
 * </p>
 */
@Getter
public class GraphNodeResponse {

    private final String id;
    private final String name;
    private final String type;
    private final Long communityId;
    private final int degree;

    private GraphNodeResponse(String id, String name, String type, Long communityId, int degree) {
        this.id = id;
        this.name = name;
        this.type = type;
        this.communityId = communityId;
        this.degree = degree;
    }

    public static GraphNodeResponse of(String id, String name, String type, Long communityId, int degree) {
        return new GraphNodeResponse(id, name, type, communityId, degree);
    }
}
