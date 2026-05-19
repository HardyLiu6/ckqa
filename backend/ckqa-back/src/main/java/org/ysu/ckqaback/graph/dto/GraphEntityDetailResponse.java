package org.ysu.ckqaback.graph.dto;

import lombok.Getter;

import java.util.List;

/**
 * 学生端知识图谱「实体详情」接口响应体。
 * <p>
 * description 在此接口里返回全文（不再截断）。chunk 文本不在 MVP 范围，仅返回数量。
 * </p>
 */
@Getter
public class GraphEntityDetailResponse {

    private final String id;
    private final String name;
    private final String type;
    private final String description;
    private final Long humanReadableId;
    private final List<GraphCommunityRef> communityPath;
    private final long chunkCount;

    public GraphEntityDetailResponse(String id, String name, String type, String description,
                                     Long humanReadableId, List<GraphCommunityRef> communityPath,
                                     long chunkCount) {
        this.id = id;
        this.name = name;
        this.type = type;
        this.description = description;
        this.humanReadableId = humanReadableId;
        this.communityPath = communityPath;
        this.chunkCount = chunkCount;
    }
}
