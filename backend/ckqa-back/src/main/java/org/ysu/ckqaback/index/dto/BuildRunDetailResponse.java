package org.ysu.ckqaback.index.dto;

import lombok.Builder;
import lombok.Getter;
import org.ysu.ckqaback.entity.KnowledgeBaseBuildRuns;

import java.time.LocalDateTime;

/**
 * 构建流水线详情响应。
 */
@Getter
@Builder
public class BuildRunDetailResponse {

    private final Long id;
    private final Long knowledgeBaseId;
    private final String courseId;
    private final Long requestedByUserId;
    private final String buildVersion;
    private final String status;
    private final String currentStage;
    private final String qaStatus;
    private final String activationPolicy;
    private final String selectedMaterialIds;
    private final Long activeIndexRunId;
    private final String workspaceUri;
    private final String buildMetadata;
    private final LocalDateTime startedAt;
    private final LocalDateTime finishedAt;
    private final LocalDateTime createdAt;
    private final LocalDateTime updatedAt;

    public static BuildRunDetailResponse fromEntity(KnowledgeBaseBuildRuns buildRun) {
        return BuildRunDetailResponse.builder()
                .id(buildRun.getId())
                .knowledgeBaseId(buildRun.getKnowledgeBaseId())
                .courseId(buildRun.getCourseId())
                .requestedByUserId(buildRun.getRequestedByUserId())
                .buildVersion(buildRun.getBuildVersion())
                .status(buildRun.getStatus())
                .currentStage(buildRun.getCurrentStage())
                .qaStatus(buildRun.getQaStatus())
                .activationPolicy(buildRun.getActivationPolicy())
                .selectedMaterialIds(buildRun.getSelectedMaterialIds())
                .activeIndexRunId(buildRun.getActiveIndexRunId())
                .workspaceUri(buildRun.getWorkspaceUri())
                .buildMetadata(buildRun.getBuildMetadata())
                .startedAt(buildRun.getStartedAt())
                .finishedAt(buildRun.getFinishedAt())
                .createdAt(buildRun.getCreatedAt())
                .updatedAt(buildRun.getUpdatedAt())
                .build();
    }
}
