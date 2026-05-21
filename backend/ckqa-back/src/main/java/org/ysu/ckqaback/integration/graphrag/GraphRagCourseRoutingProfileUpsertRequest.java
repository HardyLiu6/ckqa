package org.ysu.ckqaback.integration.graphrag;

import java.util.List;
import java.util.Map;

public record GraphRagCourseRoutingProfileUpsertRequest(List<Item> profiles) {

    public record Item(
            String courseId,
            String courseName,
            String profileText,
            String profileHash,
            Map<String, Object> metadata
    ) {
    }
}
