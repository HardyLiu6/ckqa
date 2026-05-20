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
    private final Boolean contextApplied;
    private final String contextStrategy;
    private final ContextSizeEstimateResponse contextSizeEstimate;
    private final Boolean memoryApplied;
    private final String memoryStrategy;
    private final String memoryScope;
    private final Integer memorySourceCount;
    private final Integer memorySizeEstimate;

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
            String timeoutMessage,
            Boolean contextApplied,
            String contextStrategy,
            ContextSizeEstimateResponse contextSizeEstimate,
            Boolean memoryApplied,
            String memoryStrategy,
            String memoryScope,
            Integer memorySourceCount,
            Integer memorySizeEstimate
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
        this.contextApplied = contextApplied;
        this.contextStrategy = contextStrategy;
        this.contextSizeEstimate = contextSizeEstimate;
        this.memoryApplied = memoryApplied;
        this.memoryStrategy = memoryStrategy;
        this.memoryScope = memoryScope;
        this.memorySourceCount = memorySourceCount;
        this.memorySizeEstimate = memorySizeEstimate;
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
        return of(userMessage, taskId, taskStatus, progressStage, retrievalStatus, createdAt, mode,
                recommendedPollingIntervalSeconds, staleTimeoutSeconds, timeoutMessage, false, "none", ContextSizeEstimateResponse.of(0),
                false, "none", null, 0, 0);
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
            String timeoutMessage,
            Boolean contextApplied,
            String contextStrategy,
            ContextSizeEstimateResponse contextSizeEstimate
    ) {
        return of(userMessage, taskId, taskStatus, progressStage, retrievalStatus, createdAt, mode,
                recommendedPollingIntervalSeconds, staleTimeoutSeconds, timeoutMessage, contextApplied, contextStrategy,
                contextSizeEstimate, false, "none", null, 0, 0);
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
            String timeoutMessage,
            Boolean contextApplied,
            String contextStrategy,
            ContextSizeEstimateResponse contextSizeEstimate,
            Boolean memoryApplied,
            String memoryStrategy,
            String memoryScope,
            Integer memorySourceCount,
            Integer memorySizeEstimate
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
                timeoutMessage,
                contextApplied,
                contextStrategy,
                contextSizeEstimate,
                memoryApplied,
                memoryStrategy,
                memoryScope,
                memorySourceCount,
                memorySizeEstimate
        );
    }
}
