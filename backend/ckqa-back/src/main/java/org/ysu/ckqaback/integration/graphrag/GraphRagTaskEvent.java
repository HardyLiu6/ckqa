package org.ysu.ckqaback.integration.graphrag;

import com.fasterxml.jackson.databind.JsonNode;

/**
 * Python GraphRAG task SSE 事件。
 */
public record GraphRagTaskEvent(
        String eventName,
        JsonNode data,
        Long eventSeq
) {
    public GraphRagTaskEvent(String eventName, JsonNode data) {
        this(eventName, data, null);
    }
}
