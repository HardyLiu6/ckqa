package org.ysu.ckqaback.course.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

/**
 * 课程创建请求体。
 */
@Getter
@Setter
public class CourseCreateRequest {

    /**
     * 课程名称。
     */
    @NotBlank(message = "courseName不能为空")
    @Size(max = 255, message = "courseName长度不能超过255")
    private String courseName;

    /**
     * 课程描述。
     */
    @Size(max = 2000, message = "description长度不能超过2000")
    private String description;

    /**
     * 课程状态。
     */
    @Pattern(regexp = "active|inactive|archived", message = "status取值不合法")
    private String status;

    /**
     * 访问策略。
     */
    @Pattern(regexp = "restricted|public", message = "accessPolicy取值不合法")
    private String accessPolicy;

    /**
     * 初始授课教师用户 ID。
     */
    @NotNull(message = "请选择授课教师")
    @Positive(message = "teacherUserId必须大于0")
    private Long teacherUserId;
}
