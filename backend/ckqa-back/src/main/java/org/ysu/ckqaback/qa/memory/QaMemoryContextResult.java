package org.ysu.ckqaback.qa.memory;

import org.ysu.ckqaback.integration.graphrag.GraphRagConversationMessage;

import java.util.List;

/**
 * 长期记忆注入决策结果。
 */
public record QaMemoryContextResult(
        boolean memoryApplied,
        String strategy,
        String scope,
        int sourceCount,
        int sizeEstimate,
        List<GraphRagConversationMessage> conversationHistory,
        String historyFallbackReason
) {

    public QaMemoryContextResult {
        strategy = strategy == null ? "none" : strategy;
        conversationHistory = conversationHistory == null ? List.of() : List.copyOf(conversationHistory);
    }

    public static QaMemoryContextResult notApplied(String reason) {
        return new QaMemoryContextResult(false, "none", null, 0, 0, List.of(), reason);
    }
}
