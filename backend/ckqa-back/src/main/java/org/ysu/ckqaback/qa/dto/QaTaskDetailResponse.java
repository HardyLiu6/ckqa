package org.ysu.ckqaback.qa.dto;

import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.Getter;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 异步问答任务详情响应。
 */
@Getter
public class QaTaskDetailResponse {

    private final Long taskId;
    private final Long userMessageId;
    private final Long assistantMessageId;
    private final String taskStatus;
    private final String progressStage;
    private final String retrievalStatus;
    private final String mode;
    private final String queryText;
    private final List<String> latestLogs;
    private final LocalDateTime startedAt;
    private final LocalDateTime lastHeartbeatAt;
    private final LocalDateTime finishedAt;
    private final QaMessageResponse assistantMessage;
    private final String errorMessage;
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

    private QaTaskDetailResponse(
            Long taskId,
            Long userMessageId,
            Long assistantMessageId,
            String taskStatus,
            String progressStage,
            String retrievalStatus,
            String mode,
            String queryText,
            List<String> latestLogs,
            LocalDateTime startedAt,
            LocalDateTime lastHeartbeatAt,
            LocalDateTime finishedAt,
            QaMessageResponse assistantMessage,
            String errorMessage,
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
        this.taskId = taskId;
        this.userMessageId = userMessageId;
        this.assistantMessageId = assistantMessageId;
        this.taskStatus = taskStatus;
        this.progressStage = progressStage;
        this.retrievalStatus = retrievalStatus;
        this.mode = mode;
        this.queryText = queryText;
        this.latestLogs = latestLogs;
        this.startedAt = startedAt;
        this.lastHeartbeatAt = lastHeartbeatAt;
        this.finishedAt = finishedAt;
        this.assistantMessage = assistantMessage;
        this.errorMessage = errorMessage;
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

    @JsonIgnore
    public String getQueryText() {
        return queryText;
    }

    public static QaTaskDetailResponse of(
            Long taskId,
            Long userMessageId,
            Long assistantMessageId,
            String taskStatus,
            String progressStage,
            String retrievalStatus,
            String mode,
            String queryText,
            List<String> latestLogs,
            LocalDateTime startedAt,
            LocalDateTime lastHeartbeatAt,
            LocalDateTime finishedAt,
            QaMessageResponse assistantMessage,
            String errorMessage,
            Long recommendedPollingIntervalSeconds,
            Long staleTimeoutSeconds,
            String timeoutMessage
    ) {
        return of(taskId, userMessageId, assistantMessageId, taskStatus, progressStage, retrievalStatus, mode, queryText,
                latestLogs, startedAt, lastHeartbeatAt, finishedAt, assistantMessage, errorMessage,
                recommendedPollingIntervalSeconds, staleTimeoutSeconds, timeoutMessage, false, "none",
                ContextSizeEstimateResponse.of(0), false, "none", null, 0, 0);
    }

    public static QaTaskDetailResponse of(
            Long taskId,
            Long userMessageId,
            Long assistantMessageId,
            String taskStatus,
            String progressStage,
            String retrievalStatus,
            String mode,
            String queryText,
            List<String> latestLogs,
            LocalDateTime startedAt,
            LocalDateTime lastHeartbeatAt,
            LocalDateTime finishedAt,
            QaMessageResponse assistantMessage,
            String errorMessage,
            Long recommendedPollingIntervalSeconds,
            Long staleTimeoutSeconds,
            String timeoutMessage,
            Boolean contextApplied,
            String contextStrategy,
            ContextSizeEstimateResponse contextSizeEstimate
    ) {
        return of(taskId, userMessageId, assistantMessageId, taskStatus, progressStage, retrievalStatus, mode, queryText,
                latestLogs, startedAt, lastHeartbeatAt, finishedAt, assistantMessage, errorMessage,
                recommendedPollingIntervalSeconds, staleTimeoutSeconds, timeoutMessage, contextApplied, contextStrategy,
                contextSizeEstimate, false, "none", null, 0, 0);
    }

    public static QaTaskDetailResponse of(
            Long taskId,
            Long userMessageId,
            Long assistantMessageId,
            String taskStatus,
            String progressStage,
            String retrievalStatus,
            String mode,
            String queryText,
            List<String> latestLogs,
            LocalDateTime startedAt,
            LocalDateTime lastHeartbeatAt,
            LocalDateTime finishedAt,
            QaMessageResponse assistantMessage,
            String errorMessage,
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
        return new QaTaskDetailResponse(
                taskId,
                userMessageId,
                assistantMessageId,
                taskStatus,
                progressStage,
                retrievalStatus,
                mode,
                queryText,
                latestLogs,
                startedAt,
                lastHeartbeatAt,
                finishedAt,
                assistantMessage,
                errorMessage,
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
