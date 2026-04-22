package org.ysu.ckqaback.qa.dto;

import lombok.Getter;

import java.time.LocalDateTime;

/**
 * 异步问答任务提交响应。
 */
@Getter
public class QaTaskSubmissionResponse {

    private final QaMessageResponse userMessage;
    private final Long taskId;
    private final String taskStatus;
    private final String progressStage;
    private final String retrievalStatus;
    private final LocalDateTime createdAt;

    private QaTaskSubmissionResponse(
            QaMessageResponse userMessage,
            Long taskId,
            String taskStatus,
            String progressStage,
            String retrievalStatus,
            LocalDateTime createdAt
    ) {
        this.userMessage = userMessage;
        this.taskId = taskId;
        this.taskStatus = taskStatus;
        this.progressStage = progressStage;
        this.retrievalStatus = retrievalStatus;
        this.createdAt = createdAt;
    }

    public static QaTaskSubmissionResponse of(
            QaMessageResponse userMessage,
            Long taskId,
            String taskStatus,
            String progressStage,
            String retrievalStatus,
            LocalDateTime createdAt
    ) {
        return new QaTaskSubmissionResponse(userMessage, taskId, taskStatus, progressStage, retrievalStatus, createdAt);
    }
}
