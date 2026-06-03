package org.ysu.ckqaback.qa.context;

/**
 * 持久化到 qa_retrieval_logs 的上下文与改写诊断信息。
 */
public record QaRetrievalLogContext(
        String originalQueryText,
        String retrievalQueryText,
        String standaloneQueryText,
        String contextSnapshotText,
        String contextStrategy,
        String contextMessageRange,
        int contextCharCount,
        boolean rewriteApplied,
        String rewriteReason,
        String rewriteSourceMessageRange,
        String rewriteMethod,
        String rewriteModel,
        Double rewriteConfidence,
        String contextSnapshotVersion,
        Double routingConfidence,
        String routingConfidenceBand,
        String routingReviewPriority,
        String routingSnapshotJson,
        boolean memoryApplied,
        String memoryStrategy,
        String memoryScope,
        Integer memorySourceCount,
        Integer memorySizeChars,
        String queryEngineStrategy,
        String historyFallbackReason,
        String memoryHistoryJson,
        String requestedMode,
        String resolvedMode,
        String resolvedTopic,
        String topicSource,
        Double topicConfidence,
        String topicStackJson
) {
}
