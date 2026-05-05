package org.ysu.ckqaback.index.dto;

import lombok.Builder;
import lombok.Getter;

/**
 * 构建流水线清理结果。
 */
@Getter
@Builder
public class BuildRunGcResponse {

    private final Long knowledgeBaseId;
    private final int deletedBuildRunCount;
    private final int deletedWorkspaceCount;
    private final boolean dryRun;
}
