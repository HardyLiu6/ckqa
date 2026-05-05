package org.ysu.ckqaback.index.dto;

import lombok.Builder;
import lombok.Getter;
import org.ysu.ckqaback.entity.IndexArtifacts;

/**
 * 索引产物响应占位。
 */
@Getter
@Builder
public class IndexArtifactResponse {

    private final Long id;
    private final Long indexRunId;
    private final String artifactType;
    private final String displayName;
    private final String storageUri;
    private final String storageScope;
    private final String artifactStatus;
    private final Long fileSize;

    public static IndexArtifactResponse fromEntity(IndexArtifacts artifact) {
        return IndexArtifactResponse.builder()
                .id(artifact.getId())
                .indexRunId(artifact.getIndexRunId())
                .artifactType(artifact.getArtifactType())
                .displayName(artifact.getDisplayName())
                .storageUri(artifact.getStorageUri())
                .storageScope(artifact.getStorageScope())
                .artifactStatus(artifact.getArtifactStatus())
                .fileSize(artifact.getFileSize())
                .build();
    }
}
