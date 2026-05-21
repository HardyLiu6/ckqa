package org.ysu.ckqaback.integration.graphrag;

import java.util.List;
import java.util.Map;

public record GraphRagCourseProfileHintsResponse(
        List<Item> items,
        Map<String, Integer> sourceCounts
) {

    public record Item(
            String heading,
            List<String> keywords,
            String sourceType,
            String sourceRef,
            Double score
    ) {
    }
}
