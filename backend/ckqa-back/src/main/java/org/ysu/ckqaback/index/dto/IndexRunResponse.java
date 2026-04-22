package org.ysu.ckqaback.index.dto;

import lombok.Getter;
import org.ysu.ckqaback.entity.IndexRuns;

import java.time.LocalDateTime;

/**
 * 索引运行响应体。
 */
@Getter
public class IndexRunResponse {

    private final Long id;
    private final Long knowledgeBaseId;
    private final String engine;
    private final String indexVersion;
    private final String status;
    private final LocalDateTime startedAt;
    private final LocalDateTime finishedAt;
    private final String runMetadata;

    private IndexRunResponse(
            Long id,
            Long knowledgeBaseId,
            String engine,
            String indexVersion,
            String status,
            LocalDateTime startedAt,
            LocalDateTime finishedAt,
            String runMetadata
    ) {
        this.id = id;
        this.knowledgeBaseId = knowledgeBaseId;
        this.engine = engine;
        this.indexVersion = indexVersion;
        this.status = status;
        this.startedAt = startedAt;
        this.finishedAt = finishedAt;
        this.runMetadata = runMetadata;
    }

    public static IndexRunResponse of(
            Long id,
            Long knowledgeBaseId,
            String engine,
            String indexVersion,
            String status,
            LocalDateTime startedAt,
            LocalDateTime finishedAt,
            String runMetadata
    ) {
        return new IndexRunResponse(id, knowledgeBaseId, engine, indexVersion, status, startedAt, finishedAt, runMetadata);
    }

    public static IndexRunResponse fromEntity(IndexRuns indexRun) {
        return of(
                indexRun.getId(),
                indexRun.getKnowledgeBaseId(),
                indexRun.getEngine(),
                indexRun.getIndexVersion(),
                indexRun.getStatus(),
                indexRun.getStartedAt(),
                indexRun.getFinishedAt(),
                indexRun.getRunMetadata()
        );
    }
}
