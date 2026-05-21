package org.ysu.ckqaback.integration.graphrag;

import java.util.List;

public record GraphRagCourseRoutingRecommendRequest(
        String question,
        List<String> courseIds,
        Integer limit
) {
}
