package org.ysu.ckqaback.course.routing;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.ArrayList;
import java.util.List;

/**
 * 课程画像路由配置。
 */
@Getter
@Setter
@ConfigurationProperties(prefix = "ckqa.course-routing")
public class CourseRoutingProperties {

    private boolean enabled = true;
    private int topK = 3;
    private double scoreThreshold = 0.35D;
    private double marginThreshold = 0.06D;
    private String embeddingModel = "text-embedding-v4";
    private int embeddingDimensions = 1024;
    private String lancedbTable = "course_profiles_text_embedding_v4";
    private boolean profileHintsEnabled = true;
    private int profileHintsMaxHints = 24;
    private List<String> excludedCourseIds = new ArrayList<>();
    private List<String> excludedCourseTags = new ArrayList<>(List.of("course-routing-excluded", "internal"));
}
