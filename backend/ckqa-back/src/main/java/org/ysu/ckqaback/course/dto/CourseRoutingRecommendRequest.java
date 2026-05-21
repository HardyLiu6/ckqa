package org.ysu.ckqaback.course.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

/**
 * 学生端无显式课程时的课程路由请求。
 */
@Getter
@Setter
public class CourseRoutingRecommendRequest {

    @NotBlank(message = "question不能为空")
    @Size(max = 2000, message = "question长度不能超过2000")
    private String question;

    @Positive(message = "userId必须大于0")
    private Long userId;

    @Min(value = 1, message = "limit必须大于0")
    @Max(value = 10, message = "limit不能超过10")
    private Integer limit = 3;
}
