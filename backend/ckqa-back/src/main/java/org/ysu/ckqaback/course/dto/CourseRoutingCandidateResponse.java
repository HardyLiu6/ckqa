package org.ysu.ckqaback.course.dto;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Getter;

/**
 * 课程路由候选项。
 */
@Getter
public class CourseRoutingCandidateResponse {

    private final String courseId;
    private final String courseName;
    private final Double confidence;
    private final String reason;

    @JsonCreator
    public CourseRoutingCandidateResponse(
            @JsonProperty("courseId") String courseId,
            @JsonProperty("courseName") String courseName,
            @JsonProperty("confidence") Double confidence,
            @JsonProperty("reason") String reason
    ) {
        this.courseId = courseId;
        this.courseName = courseName;
        this.confidence = confidence == null ? 0D : confidence;
        this.reason = reason;
    }

    public static CourseRoutingCandidateResponse of(
            String courseId,
            String courseName,
            Double confidence,
            String reason
    ) {
        return new CourseRoutingCandidateResponse(courseId, courseName, confidence, reason);
    }
}
