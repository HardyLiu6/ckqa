package org.ysu.ckqaback.course.dto;

import lombok.Getter;

import java.util.List;

/**
 * 课程路由推荐响应。
 */
@Getter
public class CourseRoutingRecommendResponse {

    private final String status;
    private final String selectedCourseId;
    private final Double confidence;
    private final Double margin;
    private final List<CourseRoutingCandidateResponse> candidates;

    public CourseRoutingRecommendResponse(
            String status,
            String selectedCourseId,
            Double confidence,
            Double margin,
            List<CourseRoutingCandidateResponse> candidates
    ) {
        this.status = status;
        this.selectedCourseId = selectedCourseId;
        this.confidence = confidence == null ? 0D : confidence;
        this.margin = margin == null ? 0D : margin;
        this.candidates = candidates == null ? List.of() : List.copyOf(candidates);
    }

    public static CourseRoutingRecommendResponse of(
            String status,
            String selectedCourseId,
            Double confidence,
            Double margin,
            List<CourseRoutingCandidateResponse> candidates
    ) {
        return new CourseRoutingRecommendResponse(status, selectedCourseId, confidence, margin, candidates);
    }
}
