package org.ysu.ckqaback.index.dto;

import lombok.Builder;
import lombok.Getter;

import java.util.List;

/**
 * GET /extraction-eval/status 评分进度响应。
 */
@Getter
@Builder
public class ExtractionEvalStatusResponse {

    private final Overall overall;
    private final List<CandidateProgress> candidates;

    @Getter
    @Builder
    public static class Overall {
        private final Integer totalCalls;
        private final Integer finishedCalls;
        private final Integer elapsedSeconds;
        private final Integer estimatedRemainingSeconds;
        private final Integer tokensUsed;
        private final Integer estimatedTotalTokens;
    }

    @Getter
    @Builder
    public static class CandidateProgress {
        private final String candidateId;
        private final Stage extract;
        private final Stage score;

        /** queued / extracting / scoring / done / failed。 */
        private final String status;
    }

    @Getter
    @Builder
    public static class Stage {
        private final Integer finished;
        private final Integer total;
        private final String currentSampleId;
    }
}
