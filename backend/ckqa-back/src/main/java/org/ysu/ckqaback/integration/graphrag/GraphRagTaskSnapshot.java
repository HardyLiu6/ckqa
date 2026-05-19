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
        String resultText,
        String errorMessage,
        Integer returnCode,
        LocalDateTime startedAt,
        LocalDateTime finishedAt,
        List<GraphRagSourceSnapshot> sources
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
                resultText,
                errorMessage,
                returnCode,
                startedAt,
                finishedAt,
                List.of()
        );
    }

    public boolean isTerminal() {
        return "success".equals(taskStatus) || "failed".equals(taskStatus);
    }
}
