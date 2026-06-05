package org.ysu.ckqaback.index.dto;

import lombok.Builder;
import lombok.Getter;
import org.ysu.ckqaback.entity.KnowledgeBaseBuildRuns;

import java.time.LocalDateTime;
import java.util.Map;

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
    private final Map<String, Object> qaSmokeResult;
    private final LocalDateTime startedAt;
    private final LocalDateTime finishedAt;
    private final LocalDateTime createdAt;
    private final LocalDateTime updatedAt;
    /**
     * 索引实时进度。仅在 {@code currentStage="index"} 且 {@code status="running"}
     * 时由 service 层从 process.log 解析后注入；其他场景为 null。
     */
    private final IndexProgress indexProgress;

    public static BuildRunDetailResponse fromEntity(KnowledgeBaseBuildRuns buildRun) {
        return fromEntity(buildRun, null);
    }

    public static BuildRunDetailResponse fromEntity(KnowledgeBaseBuildRuns buildRun, IndexProgress indexProgress) {
        return fromEntity(buildRun, indexProgress, null);
    }

    public static BuildRunDetailResponse fromEntity(
            KnowledgeBaseBuildRuns buildRun,
            IndexProgress indexProgress,
            Map<String, Object> qaSmokeResult
    ) {
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
                .qaSmokeResult(qaSmokeResult)
                .startedAt(buildRun.getStartedAt())
                .finishedAt(buildRun.getFinishedAt())
                .createdAt(buildRun.getCreatedAt())
                .updatedAt(buildRun.getUpdatedAt())
                .indexProgress(indexProgress)
                .build();
    }
}
