package org.ysu.ckqaback.qa.summary;

public record QaSummaryResult(
        boolean success,
        String summaryText,
        String errorMessage
) {
    public static QaSummaryResult success(String summaryText) {
        return new QaSummaryResult(true, summaryText, null);
    }

    public static QaSummaryResult failure(String errorMessage) {
        return new QaSummaryResult(false, null, errorMessage);
    }
}
