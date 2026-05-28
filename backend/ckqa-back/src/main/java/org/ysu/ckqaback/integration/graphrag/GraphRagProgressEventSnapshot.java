package org.ysu.ckqaback.integration.graphrag;

import java.util.List;
import java.util.Map;

/**
 * GraphRAG Python 任务返回的结构化检索过程事件。
 */
public record GraphRagProgressEventSnapshot(
        String type,
        String mode,
        String summary,
        Map<String, Object> metrics,
        List<Map<String, Object>> evidence,
        Long eventSeq
) {

    public GraphRagProgressEventSnapshot {
        metrics = metrics == null ? Map.of() : Map.copyOf(metrics);
        evidence = evidence == null ? List.of() : List.copyOf(evidence);
    }
}
