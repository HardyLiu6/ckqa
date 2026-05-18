package org.ysu.ckqaback.qa.dto;

import lombok.Getter;
import org.ysu.ckqaback.entity.QaMessages;

import java.time.LocalDateTime;
import java.util.List;

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
    private final List<QaSourceResponse> sources;
    private final QaFeedbackResponse feedback;

    private QaMessageResponse(
            Long id,
            Long sessionId,
            String role,
            Integer sequenceNo,
            String content,
            LocalDateTime createdAt,
            String taskStatus,
            String progressStage,
            List<QaSourceResponse> sources,
            QaFeedbackResponse feedback
    ) {
        this.id = id;
        this.sessionId = sessionId;
        this.role = role;
        this.sequenceNo = sequenceNo;
        this.content = content;
        this.createdAt = createdAt;
        this.taskStatus = taskStatus;
        this.progressStage = progressStage;
        this.sources = sources == null ? List.of() : List.copyOf(sources);
        this.feedback = feedback;
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
        return of(id, sessionId, role, sequenceNo, content, createdAt, taskStatus, progressStage, List.of(), null);
    }

    public static QaMessageResponse of(
            Long id,
            Long sessionId,
            String role,
            Integer sequenceNo,
            String content,
            LocalDateTime createdAt,
            String taskStatus,
            String progressStage,
            List<QaSourceResponse> sources
    ) {
        return of(id, sessionId, role, sequenceNo, content, createdAt, taskStatus, progressStage, sources, null);
    }

    public static QaMessageResponse of(
            Long id,
            Long sessionId,
            String role,
            Integer sequenceNo,
            String content,
            LocalDateTime createdAt,
            String taskStatus,
            String progressStage,
            List<QaSourceResponse> sources,
            QaFeedbackResponse feedback
    ) {
        return new QaMessageResponse(
                id,
                sessionId,
                role,
                sequenceNo,
                content,
                createdAt,
                taskStatus,
                progressStage,
                sources,
                feedback
        );
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

    public static QaMessageResponse fromEntity(QaMessages message, List<QaSourceResponse> sources) {
        return fromEntity(message, sources, null);
    }

    public static QaMessageResponse fromEntity(
            QaMessages message,
            List<QaSourceResponse> sources,
            QaFeedbackResponse feedback
    ) {
        return of(
                message.getId(),
                message.getSessionId(),
                message.getRole(),
                message.getSequenceNo(),
                message.getContent(),
                message.getCreatedAt(),
                null,
                null,
                sources,
                feedback
        );
    }
}
