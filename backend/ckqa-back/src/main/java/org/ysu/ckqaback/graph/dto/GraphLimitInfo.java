package org.ysu.ckqaback.graph.dto;

import lombok.Getter;

/**
 * 子图响应里的限流信息。
 * <p>
 * 让前端清晰看到：本次查询返回的 nodes / edges 数量上限是多少，
 * 后端是否按上限截断返回。前端可据此提示"结果较多"。
 * </p>
 */
@Getter
public class GraphLimitInfo {

    private final int nodeCount;
    private final int edgeCount;
    private final int nodeLimit;
    private final int edgeLimit;

    public GraphLimitInfo(int nodeCount, int edgeCount, int nodeLimit, int edgeLimit) {
        this.nodeCount = nodeCount;
        this.edgeCount = edgeCount;
        this.nodeLimit = nodeLimit;
        this.edgeLimit = edgeLimit;
    }
}
