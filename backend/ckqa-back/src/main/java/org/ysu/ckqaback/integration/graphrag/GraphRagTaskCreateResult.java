package org.ysu.ckqaback.integration.graphrag;

import java.time.LocalDateTime;

/**
 * GraphRAG 异步查询任务创建结果。
 */
public record GraphRagTaskCreateResult(
        String pythonTaskId,
        String taskStatus,
        String progressStage,
        LocalDateTime createdAt
) {
}
