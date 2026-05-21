package org.ysu.ckqaback.integration.graphrag;

import java.util.List;

public record GraphRagCourseRoutingProfileUpsertResponse(List<Item> items) {

    public record Item(
            String courseId,
            String courseName,
            String profileHash,
            String vectorId
    ) {
    }
}
