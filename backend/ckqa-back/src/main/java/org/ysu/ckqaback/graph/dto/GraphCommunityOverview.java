package org.ysu.ckqaback.graph.dto;

import lombok.Getter;

import java.util.List;

/**
 * overview 接口里每个顶层社区的轻量摘要。
 */
@Getter
public class GraphCommunityOverview {

    private final Long communityId;
    private final String title;
    private final double rank;
    private final String summary;
    private final List<GraphNodeResponse> topEntities;

    public GraphCommunityOverview(Long communityId, String title, double rank, String summary,
                                  List<GraphNodeResponse> topEntities) {
        this.communityId = communityId;
        this.title = title;
        this.rank = rank;
        this.summary = summary;
        this.topEntities = topEntities;
    }
}
