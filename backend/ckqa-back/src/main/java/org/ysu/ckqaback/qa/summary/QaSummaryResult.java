package org.ysu.ckqaback.qa.summary;

public record QaSummaryResult(
        boolean success,
        String summaryText,
        String errorMessage,
        String model,
        Long durationMs,
        Integer inputCharCount,
        Integer outputCharCount
) {
    public static QaSummaryResult success(String summaryText) {
        return success(summaryText, null, null, null, summaryText == null ? null : summaryText.length());
    }

    public static QaSummaryResult success(
            String summaryText,
            String model,
            Long durationMs,
            Integer inputCharCount,
            Integer outputCharCount
    ) {
        return new QaSummaryResult(true, summaryText, null, model, durationMs, inputCharCount, outputCharCount);
    }

    public static QaSummaryResult failure(String errorMessage) {
        return failure(errorMessage, null, null, null);
    }

    public static QaSummaryResult failure(String errorMessage, String model, Long durationMs, Integer inputCharCount) {
        return new QaSummaryResult(false, null, errorMessage, model, durationMs, inputCharCount, null);
    }
}
