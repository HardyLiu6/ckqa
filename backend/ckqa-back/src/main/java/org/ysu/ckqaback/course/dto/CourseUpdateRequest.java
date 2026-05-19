package org.ysu.ckqaback.course.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

import java.util.List;

/**
 * 课程基础信息更新请求体。
 * <p>
 * 当前 PUT 端点保留全量替换语义；新增的可选字段（category / tags / objectives / audience /
 * difficulty / estimatedHours）传 {@code null} 时不会被强制清空，由 service 按是否提供决定写库。
 * </p>
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
     * 课程分类（自由输入）。
     */
    @Size(max = 64, message = "category长度不能超过64")
    private String category;

    /**
     * 课程标签数组，最多 20 个，每项最长 32 字符。
     */
    @Size(max = 20, message = "tags最多 20 个")
    private List<@Size(max = 32, message = "单个 tag 长度不能超过 32") String> tags;

    /**
     * 学习目标，最多 12 条，每条最长 200 字符。
     */
    @Size(max = 12, message = "objectives最多 12 条")
    private List<@Size(max = 200, message = "单条 objective 长度不能超过 200") String> objectives;

    /**
     * 适合人群，最多 10 条，每条最长 100 字符。
     */
    @Size(max = 10, message = "audience最多 10 条")
    private List<@Size(max = 100, message = "单条 audience 长度不能超过 100") String> audience;

    /**
     * 难度级别。
     */
    @Pattern(regexp = "beginner|intermediate|advanced", message = "difficulty取值不合法")
    private String difficulty;

    /**
     * 预计学习时长（小时）。
     */
    @PositiveOrZero(message = "estimatedHours不能为负数")
    private Integer estimatedHours;

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
