package org.ysu.ckqaback.course.dto;

import lombok.Getter;

import java.util.List;

/**
 * 课程章节列表响应（占位）。
 * <p>
 * 完整 LMS（章节 / 课时 / 视频 / 进度）属于 PR 1 之后的二期任务。本响应作为前端调用入口的占位结构，
 * {@code featureStatus} 标识当前功能状态：{@code coming-soon} 表示前端应展示"即将开放"占位卡。
 * </p>
 */
@Getter
public class CourseChaptersResponse {

    private final List<Object> chapters;
    private final String featureStatus;
    private final String message;

    private CourseChaptersResponse(List<Object> chapters, String featureStatus, String message) {
        this.chapters = chapters;
        this.featureStatus = featureStatus;
        this.message = message;
    }

    public static CourseChaptersResponse comingSoon() {
        return new CourseChaptersResponse(
                List.of(),
                "coming-soon",
                "课程章节功能即将开放，敬请期待"
        );
    }
}
