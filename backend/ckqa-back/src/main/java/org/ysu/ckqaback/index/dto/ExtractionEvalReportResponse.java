package org.ysu.ckqaback.index.dto;

import lombok.Builder;
import lombok.Getter;

import java.math.BigDecimal;
import java.util.List;

/**
 * GET /extraction-eval/report 评分排行榜响应。
 */
@Getter
@Builder
public class ExtractionEvalReportResponse {

    private final List<CandidateReport> candidates;

    @Getter
    @Builder
    public static class CandidateReport {
        private final String candidateId;
        private final Integer rank;
        private final BigDecimal compositeScore;
        private final BigDecimal parseSuccessRate;
        private final BigDecimal recall;
        private final BigDecimal precision;
        private final BigDecimal f1;
        private final BigDecimal entityCountAvg;
        private final BigDecimal relationCountAvg;
        private final Integer tokensUsed;
        private final Integer elapsedSeconds;
        private final List<Gate> gates;
        private final List<FailedSample> failedSamples;
    }

    @Getter
    @Builder
    public static class Gate {
        /** parse_success / audit_recall / audit_precision / relation_direction。 */
        private final String name;
        private final BigDecimal threshold;
        private final BigDecimal value;
        private final Boolean passed;
    }

    @Getter
    @Builder
    public static class FailedSample {
        private final String sampleId;
        private final String reason;
    }
}
