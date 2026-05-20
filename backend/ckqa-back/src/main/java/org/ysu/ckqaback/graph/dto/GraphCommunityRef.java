package org.ysu.ckqaback.graph.dto;

import lombok.Getter;

/**
 * 实体详情里所属社区路径的一个层级条目。
 */
@Getter
public class GraphCommunityRef {

    private final int level;
    private final Long communityId;
    private final String title;

    public GraphCommunityRef(int level, Long communityId, String title) {
        this.level = level;
        this.communityId = communityId;
        this.title = title;
    }
}
