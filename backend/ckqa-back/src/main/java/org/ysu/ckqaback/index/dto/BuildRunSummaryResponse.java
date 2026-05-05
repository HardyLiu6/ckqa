package org.ysu.ckqaback.index.dto;

import lombok.Builder;
import lombok.Getter;
import org.ysu.ckqaback.entity.KnowledgeBaseBuildRuns;

import java.time.LocalDateTime;

/**
 * 构建流水线摘要响应。
 */
@Getter
@Builder
public class BuildRunSummaryResponse {

    private final Long id;
    private final Long knowledgeBaseId;
    private final String courseId;
    private final String buildVersion;
    private final String status;
    private final String currentStage;
    private final String qaStatus;
    private final Long activeIndexRunId;
    private final String workspaceUri;
    private final LocalDateTime createdAt;
    private final LocalDateTime updatedAt;

    public static BuildRunSummaryResponse fromEntity(KnowledgeBaseBuildRuns buildRun) {
        return BuildRunSummaryResponse.builder()
                .id(buildRun.getId())
                .knowledgeBaseId(buildRun.getKnowledgeBaseId())
                .courseId(buildRun.getCourseId())
                .buildVersion(buildRun.getBuildVersion())
                .status(buildRun.getStatus())
                .currentStage(buildRun.getCurrentStage())
                .qaStatus(buildRun.getQaStatus())
                .activeIndexRunId(buildRun.getActiveIndexRunId())
                .workspaceUri(buildRun.getWorkspaceUri())
                .createdAt(buildRun.getCreatedAt())
                .updatedAt(buildRun.getUpdatedAt())
                .build();
    }
}
