package org.ysu.ckqaback.course.dto;

import lombok.Builder;
import lombok.Getter;

/**
 * 课程授课教师摘要响应。
 */
@Getter
@Builder
public class CourseTeacherResponse {

    private final Long userId;
    private final String userCode;
    private final String username;
    private final String displayName;
}
