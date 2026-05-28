package org.ysu.ckqaback.integration.graphrag;

import java.time.LocalDateTime;
import java.util.List;

/**
 * GraphRAG 异步查询任务快照。
 */
public record GraphRagTaskSnapshot(
        String pythonTaskId,
        String taskStatus,
        String progressStage,
        boolean processAlive,
        LocalDateTime lastHeartbeatAt,
        List<String> latestLogs,
        List<GraphRagProgressEventSnapshot> progressEvents,
        String resultText,
        String errorMessage,
        Integer returnCode,
        LocalDateTime startedAt,
        LocalDateTime finishedAt,
        List<GraphRagSourceSnapshot> sources,
        String queryEngineStrategy,
        String historyFallbackReason,
        Boolean historyApplied,
        Integer historyTurnsUsed,
        Boolean streamingEnabled,
        String streamingProvider,
        String streamingFallbackReason,
        Integer streamedTextLength,
        String partialResultText,
        Long streamEventSeq
) {

    public GraphRagTaskSnapshot(
            String pythonTaskId,
            String taskStatus,
            String progressStage,
            boolean processAlive,
            LocalDateTime lastHeartbeatAt,
            List<String> latestLogs,
            String resultText,
            String errorMessage,
            Integer returnCode,
            LocalDateTime startedAt,
            LocalDateTime finishedAt
    ) {
        this(
                pythonTaskId,
                taskStatus,
                progressStage,
                processAlive,
                lastHeartbeatAt,
                latestLogs,
                List.of(),
                resultText,
                errorMessage,
                returnCode,
                startedAt,
                finishedAt,
                List.of(),
                null,
                null,
                false,
                0,
                false,
                null,
                null,
                0,
                null,
                0L
        );
    }

    public GraphRagTaskSnapshot(
            String pythonTaskId,
            String taskStatus,
            String progressStage,
            boolean processAlive,
            LocalDateTime lastHeartbeatAt,
            List<String> latestLogs,
            String resultText,
            String errorMessage,
            Integer returnCode,
            LocalDateTime startedAt,
            LocalDateTime finishedAt,
            List<GraphRagSourceSnapshot> sources
    ) {
        this(
                pythonTaskId,
                taskStatus,
                progressStage,
                processAlive,
                lastHeartbeatAt,
                latestLogs,
                List.of(),
                resultText,
                errorMessage,
                returnCode,
                startedAt,
                finishedAt,
                sources,
                null,
                null,
                false,
                0,
                false,
                null,
                null,
                0,
                null,
                0L
        );
    }

    public GraphRagTaskSnapshot {
        progressEvents = progressEvents == null ? List.of() : List.copyOf(progressEvents);
        sources = sources == null ? List.of() : List.copyOf(sources);
        historyApplied = Boolean.TRUE.equals(historyApplied);
        historyTurnsUsed = historyTurnsUsed == null ? 0 : historyTurnsUsed;
        streamingEnabled = Boolean.TRUE.equals(streamingEnabled);
        streamedTextLength = streamedTextLength == null ? 0 : streamedTextLength;
        streamEventSeq = streamEventSeq == null ? 0L : streamEventSeq;
    }

    public boolean isTerminal() {
        return "success".equals(taskStatus) || "failed".equals(taskStatus);
    }
}
