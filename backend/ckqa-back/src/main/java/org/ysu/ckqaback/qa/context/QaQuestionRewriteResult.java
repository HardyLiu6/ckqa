package org.ysu.ckqaback.qa.context;

/**
 * 规则式追问改写结果，retrievalQueryText 会作为 GraphRAG CLI 的 prompt。
 */
public record QaQuestionRewriteResult(
        String retrievalQueryText,
        boolean rewriteApplied,
        String rewriteReason,
        String rewriteSourceMessageRange
) {
}
