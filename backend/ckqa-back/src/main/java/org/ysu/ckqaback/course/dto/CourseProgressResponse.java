package org.ysu.ckqaback.course.dto;

import lombok.Getter;

import java.util.List;

/**
 * 当前学生的课程学习进度响应（占位）。
 * <p>
 * 完整学习进度依赖二期 {@code course_lessons} / {@code course_lesson_progress} 表，
 * 本接口当前只回包结构化的 {@code coming-soon} 占位，并透出 {@code enrolled} 用于前端区分
 * "已加入但功能未开放"和"未加入课程"两种降级文案。
 * </p>
 */
@Getter
public class CourseProgressResponse {

    private final boolean enrolled;
    private final List<Object> lessonProgress;
    private final String featureStatus;
    private final String message;

    private CourseProgressResponse(boolean enrolled, String featureStatus, String message) {
        this.enrolled = enrolled;
        this.lessonProgress = List.of();
        this.featureStatus = featureStatus;
        this.message = message;
    }

    public static CourseProgressResponse comingSoon(boolean enrolled) {
        return new CourseProgressResponse(
                enrolled,
                "coming-soon",
                enrolled
                        ? "学习进度功能即将开放，敬请期待"
                        : "尚未加入该课程，加入后即可查看学习进度"
        );
    }
}
