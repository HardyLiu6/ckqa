package org.ysu.ckqaback.qa.stream;

import org.ysu.ckqaback.qa.dto.ContextSizeEstimateResponse;
import org.ysu.ckqaback.qa.dto.QaTaskDetailResponse;

import java.time.LocalDateTime;
import java.util.List;

/**
 * SSE 中对学生端安全暴露的任务状态快照。
 */
public record QaTaskStreamStatusEvent(
        Long taskId,
        Long userMessageId,
        Long assistantMessageId,
        String taskStatus,
        String progressStage,
        String retrievalStatus,
        String mode,
        List<String> latestLogs,
        LocalDateTime startedAt,
        LocalDateTime lastHeartbeatAt,
        LocalDateTime finishedAt,
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

    public static QaTaskStreamStatusEvent from(QaTaskDetailResponse detail) {
        return new QaTaskStreamStatusEvent(
                detail.getTaskId(),
                detail.getUserMessageId(),
                detail.getAssistantMessageId(),
                detail.getTaskStatus(),
                detail.getProgressStage(),
                detail.getRetrievalStatus(),
                detail.getMode(),
                detail.getLatestLogs(),
                detail.getStartedAt(),
                detail.getLastHeartbeatAt(),
                detail.getFinishedAt(),
                detail.getRecommendedPollingIntervalSeconds(),
                detail.getStaleTimeoutSeconds(),
                detail.getTimeoutMessage(),
                detail.getContextApplied(),
                detail.getContextStrategy(),
                detail.getContextSizeEstimate(),
                detail.getMemoryApplied(),
                detail.getMemoryStrategy(),
                detail.getMemoryScope(),
                detail.getMemorySourceCount(),
                detail.getMemorySizeEstimate(),
                detail.getPartialResponseText(),
                detail.getStreamEventSeq()
        );
    }
}
