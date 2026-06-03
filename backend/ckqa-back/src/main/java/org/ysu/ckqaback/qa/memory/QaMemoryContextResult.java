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
        String historyFallbackReason,
        String memoryGovernanceVersion,
        int memoryLongTermCount,
        int memoryRecentHistoryCount,
        String memoryInjectionReason,
        String memorySourcesJson
) {

    public QaMemoryContextResult {
        strategy = strategy == null ? "none" : strategy;
        conversationHistory = conversationHistory == null ? List.of() : List.copyOf(conversationHistory);
        memoryGovernanceVersion = memoryGovernanceVersion == null
                ? QaMemoryGovernanceSnapshot.VERSION
                : memoryGovernanceVersion;
        memoryLongTermCount = Math.max(0, memoryLongTermCount);
        memoryRecentHistoryCount = Math.max(0, memoryRecentHistoryCount);
        memorySourcesJson = memorySourcesJson == null ? "[]" : memorySourcesJson;
    }

    public QaMemoryContextResult(
            boolean memoryApplied,
            String strategy,
            String scope,
            int sourceCount,
            int sizeEstimate,
            List<GraphRagConversationMessage> conversationHistory,
            String historyFallbackReason
    ) {
        this(
                memoryApplied,
                strategy,
                scope,
                sourceCount,
                sizeEstimate,
                conversationHistory,
                historyFallbackReason,
                QaMemoryGovernanceSnapshot.VERSION,
                0,
                0,
                historyFallbackReason,
                "[]"
        );
    }

    public static QaMemoryContextResult notApplied(String reason) {
        return new QaMemoryContextResult(
                false,
                "none",
                null,
                0,
                0,
                List.of(),
                reason,
                QaMemoryGovernanceSnapshot.VERSION,
                0,
                0,
                reason,
                "[]"
        );
    }
}
