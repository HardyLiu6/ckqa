package org.ysu.ckqaback.qa.dto;

import lombok.Getter;

/**
 * 问答反馈汇总。
 */
@Getter
public class QaFeedbackSummaryResponse {
    private final int helpful;
    private final int unhelpful;
    private final int needsImprovement;
    private final int sourceIssue;

    private QaFeedbackSummaryResponse(int helpful, int unhelpful, int needsImprovement, int sourceIssue) {
        this.helpful = helpful;
        this.unhelpful = unhelpful;
        this.needsImprovement = needsImprovement;
        this.sourceIssue = sourceIssue;
    }

    public static QaFeedbackSummaryResponse of(
            Integer helpful,
            Integer unhelpful,
            Integer needsImprovement,
            Integer sourceIssue
    ) {
        return new QaFeedbackSummaryResponse(
                helpful == null ? 0 : helpful,
                unhelpful == null ? 0 : unhelpful,
                needsImprovement == null ? 0 : needsImprovement,
                sourceIssue == null ? 0 : sourceIssue
        );
    }
}
