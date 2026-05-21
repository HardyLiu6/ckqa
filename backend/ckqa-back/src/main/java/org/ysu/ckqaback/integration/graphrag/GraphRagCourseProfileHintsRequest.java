package org.ysu.ckqaback.integration.graphrag;

import java.util.List;

public record GraphRagCourseProfileHintsRequest(
        String courseId,
        List<String> dataDirUris,
        Integer maxHints,
        List<String> seedKeywords
) {
}
