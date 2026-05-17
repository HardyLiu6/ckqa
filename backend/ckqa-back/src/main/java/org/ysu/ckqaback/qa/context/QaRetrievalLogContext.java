package org.ysu.ckqaback.qa.context;

/**
 * 持久化到 qa_retrieval_logs 的上下文与改写诊断信息。
 */
public record QaRetrievalLogContext(
        String originalQueryText,
        String retrievalQueryText,
        String contextSnapshotText,
        String contextStrategy,
        String contextMessageRange,
        int contextCharCount,
        boolean rewriteApplied,
        String rewriteReason,
        String rewriteSourceMessageRange
) {
}
