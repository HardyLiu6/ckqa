package org.ysu.ckqaback.qa.context;

/**
 * LLM 独立问题改写结果。
 */
public record QaLlmQuestionRewriteResult(
        boolean success,
        String standaloneQueryText,
        double confidence,
        String reason,
        String errorMessage,
        String model
) {
    public static QaLlmQuestionRewriteResult success(
            String standaloneQueryText,
            double confidence,
            String reason,
            String model
    ) {
        return new QaLlmQuestionRewriteResult(true, standaloneQueryText, confidence, reason, null, model);
    }

    public static QaLlmQuestionRewriteResult failure(String errorMessage, String model) {
        return new QaLlmQuestionRewriteResult(false, "", 0D, "", errorMessage, model);
    }
}
