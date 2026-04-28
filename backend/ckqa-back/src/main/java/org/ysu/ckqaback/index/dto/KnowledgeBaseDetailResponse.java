package org.ysu.ckqaback.index.dto;

import lombok.Builder;
import lombok.Getter;
import org.ysu.ckqaback.entity.IndexRuns;
import org.ysu.ckqaback.entity.KnowledgeBases;

import java.time.LocalDateTime;

/**
 * 知识库详情响应。
 */
@Getter
@Builder
public class KnowledgeBaseDetailResponse {

    private final Long id;
    private final String courseId;
    private final String kbCode;
    private final String name;
    private final String status;
    private final Long activeIndexRunId;
    private final String description;
    private final Long latestIndexRunId;
    private final String latestIndexRunStatus;
    private final LocalDateTime createdAt;
    private final LocalDateTime updatedAt;
    private final Long indexRunCount;
    private final Long successIndexRunCount;
    private final String latestIndexRunMetadata;

    public static KnowledgeBaseDetailResponse fromEntity(
            KnowledgeBases knowledgeBase,
            IndexRuns latestIndexRun,
            Long indexRunCount,
            Long successIndexRunCount
    ) {
        return KnowledgeBaseDetailResponse.builder()
                .id(knowledgeBase.getId())
                .courseId(knowledgeBase.getCourseId())
                .kbCode(knowledgeBase.getKbCode())
                .name(knowledgeBase.getName())
                .status(knowledgeBase.getStatus())
                .activeIndexRunId(knowledgeBase.getActiveIndexRunId())
                .description(knowledgeBase.getDescription())
                .latestIndexRunId(latestIndexRun == null ? null : latestIndexRun.getId())
                .latestIndexRunStatus(latestIndexRun == null ? null : latestIndexRun.getStatus())
                .createdAt(knowledgeBase.getCreatedAt())
                .updatedAt(knowledgeBase.getUpdatedAt())
                .indexRunCount(indexRunCount)
                .successIndexRunCount(successIndexRunCount)
                .latestIndexRunMetadata(latestIndexRun == null ? null : latestIndexRun.getRunMetadata())
                .build();
    }
}
