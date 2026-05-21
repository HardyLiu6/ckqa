package org.ysu.ckqaback.course.routing;

/**
 * 课程路由阈值判定结果。
 */
public record CourseRoutingDecision(
        String status,
        String selectedCourseId,
        double confidence,
        double margin
) {
}
