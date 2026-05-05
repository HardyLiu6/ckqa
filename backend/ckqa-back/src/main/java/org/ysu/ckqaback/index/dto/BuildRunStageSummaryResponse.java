package org.ysu.ckqaback.index.dto;

import lombok.Builder;
import lombok.Getter;
import org.ysu.ckqaback.entity.KnowledgeBaseBuildRuns;

/**
 * 构建阶段动作响应。
 */
@Getter
@Builder
public class BuildRunStageSummaryResponse {

    private final Long id;
    private final String status;
    private final String currentStage;
    private final String buildMetadata;
    private final String workspaceUri;

    public static BuildRunStageSummaryResponse fromEntity(KnowledgeBaseBuildRuns buildRun) {
        return BuildRunStageSummaryResponse.builder()
                .id(buildRun.getId())
                .status(buildRun.getStatus())
                .currentStage(buildRun.getCurrentStage())
                .buildMetadata(buildRun.getBuildMetadata())
                .workspaceUri(buildRun.getWorkspaceUri())
                .build();
    }
}
