package org.ysu.ckqaback.qa.dto;

import lombok.Getter;

import java.time.LocalDateTime;
import java.util.List;

/**
 * transcript 消息响应体，保留消息来源追踪字段。
 */
@Getter
public class QaTranscriptMessageResponse {

    private final Long id;
    private final Long sessionId;
    private final String role;
    private final Integer sequenceNo;
    private final String content;
    private final LocalDateTime createdAt;
    private final String mode;
    private final Long taskId;
    private final String taskStatus;
    private final String progressStage;
    private final List<QaSourceResponse> sources;
    private final QaFeedbackResponse feedback;
    private final Long copiedFromMessageId;

    private QaTranscriptMessageResponse(QaMessageResponse message, Long copiedFromMessageId) {
        this.id = message.getId();
        this.sessionId = message.getSessionId();
        this.role = message.getRole();
        this.sequenceNo = message.getSequenceNo();
        this.content = message.getContent();
        this.createdAt = message.getCreatedAt();
        this.mode = message.getMode();
        this.taskId = message.getTaskId();
        this.taskStatus = message.getTaskStatus();
        this.progressStage = message.getProgressStage();
        this.sources = message.getSources();
        this.feedback = message.getFeedback();
        this.copiedFromMessageId = copiedFromMessageId;
    }

    public static QaTranscriptMessageResponse fromMessage(QaMessageResponse message, Long copiedFromMessageId) {
        return new QaTranscriptMessageResponse(message, copiedFromMessageId);
    }
}
