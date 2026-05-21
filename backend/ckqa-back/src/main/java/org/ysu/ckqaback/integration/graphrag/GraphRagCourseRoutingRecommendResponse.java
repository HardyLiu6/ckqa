package org.ysu.ckqaback.integration.graphrag;

import java.util.List;

public record GraphRagCourseRoutingRecommendResponse(List<Candidate> candidates) {

    public record Candidate(
            String courseId,
            String courseName,
            Double confidence,
            String reason,
            String profileHash
    ) {
    }
}
