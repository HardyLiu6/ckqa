package org.ysu.ckqaback.qa.memory.dto;

import lombok.Getter;
import org.ysu.ckqaback.entity.QaLearningMemories;

import java.time.LocalDateTime;

/**
 * 学生端长期记忆条目响应，不返回 memory_text 正文。
 */
@Getter
public class QaMemoryItemResponse {

    private final Long id;
    private final String memoryType;
    private final Long sourceSessionId;
    private final Long sourceMessageId;
    private final String status;
    private final LocalDateTime createdAt;
    private final LocalDateTime updatedAt;

    private QaMemoryItemResponse(
            Long id,
            String memoryType,
            Long sourceSessionId,
            Long sourceMessageId,
            String status,
            LocalDateTime createdAt,
            LocalDateTime updatedAt
    ) {
        this.id = id;
        this.memoryType = memoryType;
        this.sourceSessionId = sourceSessionId;
        this.sourceMessageId = sourceMessageId;
        this.status = status;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

    public static QaMemoryItemResponse fromEntity(QaLearningMemories memory) {
        return of(
                memory.getId(),
                memory.getMemoryType(),
                memory.getSourceSessionId(),
                memory.getSourceMessageId(),
                memory.getStatus(),
                memory.getCreatedAt(),
                memory.getUpdatedAt()
        );
    }

    public static QaMemoryItemResponse of(
            Long id,
            String memoryType,
            Long sourceSessionId,
            Long sourceMessageId,
            String status,
            LocalDateTime createdAt,
            LocalDateTime updatedAt
    ) {
        return new QaMemoryItemResponse(id, memoryType, sourceSessionId, sourceMessageId, status, createdAt, updatedAt);
    }
}
