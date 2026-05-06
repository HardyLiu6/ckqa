package org.ysu.ckqaback.course.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

/**
 * 课程基础信息更新请求体。
 */
@Getter
@Setter
public class CourseUpdateRequest {

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
    @NotBlank(message = "status不能为空")
    @Pattern(regexp = "active|inactive|archived", message = "status取值不合法")
    private String status;

    /**
     * 访问策略。
     */
    @NotBlank(message = "accessPolicy不能为空")
    @Pattern(regexp = "restricted|public", message = "accessPolicy取值不合法")
    private String accessPolicy;
}
