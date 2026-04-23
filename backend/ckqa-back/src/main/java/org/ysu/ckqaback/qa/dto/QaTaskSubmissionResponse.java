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
    private final String mode;
    private final Long recommendedPollingIntervalSeconds;
    private final Long staleTimeoutSeconds;
    private final String timeoutMessage;

    private QaTaskSubmissionResponse(
            QaMessageResponse userMessage,
            Long taskId,
            String taskStatus,
            String progressStage,
            String retrievalStatus,
            LocalDateTime createdAt,
            String mode,
            Long recommendedPollingIntervalSeconds,
            Long staleTimeoutSeconds,
            String timeoutMessage
    ) {
        this.userMessage = userMessage;
        this.taskId = taskId;
        this.taskStatus = taskStatus;
        this.progressStage = progressStage;
        this.retrievalStatus = retrievalStatus;
        this.createdAt = createdAt;
        this.mode = mode;
        this.recommendedPollingIntervalSeconds = recommendedPollingIntervalSeconds;
        this.staleTimeoutSeconds = staleTimeoutSeconds;
        this.timeoutMessage = timeoutMessage;
    }

    public static QaTaskSubmissionResponse of(
            QaMessageResponse userMessage,
            Long taskId,
            String taskStatus,
            String progressStage,
            String retrievalStatus,
            LocalDateTime createdAt,
            String mode,
            Long recommendedPollingIntervalSeconds,
            Long staleTimeoutSeconds,
            String timeoutMessage
    ) {
        return new QaTaskSubmissionResponse(
                userMessage,
                taskId,
                taskStatus,
                progressStage,
                retrievalStatus,
                createdAt,
                mode,
                recommendedPollingIntervalSeconds,
                staleTimeoutSeconds,
                timeoutMessage
        );
    }
}
