package org.ysu.ckqaback.course.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

/**
 * 课程成员查询参数。
 */
@Getter
@Setter
public class CourseMembershipQueryRequest {

    @NotBlank(message = "courseId不能为空")
    @Size(max = 64, message = "courseId长度不能超过64")
    private String courseId;

    @Pattern(regexp = "student|teacher|assistant", message = "membershipRole取值不合法")
    private String membershipRole;

    @Pattern(regexp = "active|pending|suspended|removed", message = "status取值不合法")
    private String status;

    @Size(max = 128, message = "keyword长度不能超过128")
    private String keyword;

    @Positive(message = "page必须大于0")
    private Integer page = 1;

    @Positive(message = "size必须大于0")
    private Integer size = 20;
}
