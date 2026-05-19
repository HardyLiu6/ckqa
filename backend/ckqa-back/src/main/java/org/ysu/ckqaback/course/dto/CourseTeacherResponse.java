package org.ysu.ckqaback.course.dto;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Builder;
import lombok.Getter;

/**
 * 课程授课教师摘要响应。
 */
@Getter
public class CourseTeacherResponse {

    private final Long userId;
    private final String userCode;
    private final String username;
    private final String displayName;
    private final String avatarUrl;
    private final String department;
    private final String title;
    private final String employeeNo;

    @Builder
    @JsonCreator
    public CourseTeacherResponse(
            @JsonProperty("userId") Long userId,
            @JsonProperty("userCode") String userCode,
            @JsonProperty("username") String username,
            @JsonProperty("displayName") String displayName,
            @JsonProperty("avatarUrl") String avatarUrl,
            @JsonProperty("department") String department,
            @JsonProperty("title") String title,
            @JsonProperty("employeeNo") String employeeNo
    ) {
        this.userId = userId;
        this.userCode = userCode;
        this.username = username;
        this.displayName = displayName;
        this.avatarUrl = avatarUrl;
        this.department = department;
        this.title = title;
        this.employeeNo = employeeNo;
    }
}
