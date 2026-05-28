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
    private final List<QaProgressEventResponse> progressEvents;
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
    private final String partialResponseText;
    private final Long streamEventSeq;

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
            List<QaProgressEventResponse> progressEvents,
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
            Integer memorySizeEstimate,
            String partialResponseText,
            Long streamEventSeq
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
        this.progressEvents = progressEvents == null ? List.of() : List.copyOf(progressEvents);
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
        this.partialResponseText = partialResponseText;
        this.streamEventSeq = streamEventSeq == null ? 0L : streamEventSeq;
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
                latestLogs, List.of(), startedAt, lastHeartbeatAt, finishedAt, assistantMessage, errorMessage,
                recommendedPollingIntervalSeconds, staleTimeoutSeconds, timeoutMessage, false, "none",
                ContextSizeEstimateResponse.of(0), false, "none", null, 0, 0, null, 0L);
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
                latestLogs, List.of(), startedAt, lastHeartbeatAt, finishedAt, assistantMessage, errorMessage,
                recommendedPollingIntervalSeconds, staleTimeoutSeconds, timeoutMessage, contextApplied, contextStrategy,
                contextSizeEstimate, false, "none", null, 0, 0, null, 0L);
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
            Integer memorySizeEstimate,
            String partialResponseText,
            Long streamEventSeq
    ) {
        return of(taskId, userMessageId, assistantMessageId, taskStatus, progressStage, retrievalStatus, mode, queryText,
                latestLogs, List.of(), startedAt, lastHeartbeatAt, finishedAt, assistantMessage, errorMessage,
                recommendedPollingIntervalSeconds, staleTimeoutSeconds, timeoutMessage, contextApplied, contextStrategy,
                contextSizeEstimate, memoryApplied, memoryStrategy, memoryScope, memorySourceCount, memorySizeEstimate,
                partialResponseText, streamEventSeq);
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
            List<QaProgressEventResponse> progressEvents,
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
            Integer memorySizeEstimate,
            String partialResponseText,
            Long streamEventSeq
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
                progressEvents,
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
                memorySizeEstimate,
                partialResponseText,
                streamEventSeq
        );
    }
}
