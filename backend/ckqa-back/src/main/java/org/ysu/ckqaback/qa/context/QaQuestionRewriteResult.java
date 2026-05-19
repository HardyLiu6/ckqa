package org.ysu.ckqaback.qa.context;

/**
 * 追问改写结果，retrievalQueryText 会作为 GraphRAG CLI 的 prompt。
 */
public record QaQuestionRewriteResult(
        String retrievalQueryText,
        String standaloneQueryText,
        boolean rewriteApplied,
        String rewriteReason,
        String rewriteSourceMessageRange,
        String rewriteMethod,
        String rewriteModel,
        Double rewriteConfidence
) {
}
