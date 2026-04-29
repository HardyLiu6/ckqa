package org.ysu.ckqaback.course.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
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
     * 课程 ID，用作跨模块稳定业务标识。
     */
    @NotBlank(message = "courseId不能为空")
    @Size(max = 64, message = "courseId长度不能超过64")
    @Pattern(regexp = "[A-Za-z0-9_-]+", message = "courseId只能包含字母、数字、下划线或短横线")
    private String courseId;

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
}
