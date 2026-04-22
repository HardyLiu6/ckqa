package org.ysu.ckqaback.qa.dto;

import lombok.Getter;
import org.ysu.ckqaback.entity.QaMessages;

import java.time.LocalDateTime;

/**
 * 问答消息响应体。
 */
@Getter
public class QaMessageResponse {

    private final Long id;
    private final Long sessionId;
    private final String role;
    private final Integer sequenceNo;
    private final String content;
    private final LocalDateTime createdAt;
    private final String taskStatus;
    private final String progressStage;

    private QaMessageResponse(
            Long id,
            Long sessionId,
            String role,
            Integer sequenceNo,
            String content,
            LocalDateTime createdAt,
            String taskStatus,
            String progressStage
    ) {
        this.id = id;
        this.sessionId = sessionId;
        this.role = role;
        this.sequenceNo = sequenceNo;
        this.content = content;
        this.createdAt = createdAt;
        this.taskStatus = taskStatus;
        this.progressStage = progressStage;
    }

    public static QaMessageResponse of(
            Long id,
            Long sessionId,
            String role,
            Integer sequenceNo,
            String content,
            LocalDateTime createdAt,
            String taskStatus,
            String progressStage
    ) {
        return new QaMessageResponse(id, sessionId, role, sequenceNo, content, createdAt, taskStatus, progressStage);
    }

    public static QaMessageResponse fromEntity(QaMessages message) {
        return of(
                message.getId(),
                message.getSessionId(),
                message.getRole(),
                message.getSequenceNo(),
                message.getContent(),
                message.getCreatedAt(),
                null,
                null
        );
    }
}
