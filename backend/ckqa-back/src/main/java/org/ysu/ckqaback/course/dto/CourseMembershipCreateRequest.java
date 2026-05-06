package org.ysu.ckqaback.course.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

/**
 * 创建或恢复课程成员请求。
 */
@Getter
@Setter
public class CourseMembershipCreateRequest {

    @NotBlank(message = "courseId不能为空")
    @Size(max = 64, message = "courseId长度不能超过64")
    private String courseId;

    @NotNull(message = "userId不能为空")
    @Positive(message = "userId必须大于0")
    private Long userId;

    @Pattern(regexp = "student|teacher|assistant", message = "membershipRole取值不合法")
    private String membershipRole = "student";

    @Pattern(regexp = "active|pending|suspended|removed", message = "status取值不合法")
    private String status = "active";

    @Pattern(regexp = "manual|imported|self_join|sync", message = "accessSource取值不合法")
    private String accessSource = "manual";

    @Size(max = 255, message = "changeReason长度不能超过255")
    private String changeReason;
}
