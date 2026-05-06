package org.ysu.ckqaback.course.dto;

import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

/**
 * 更新课程成员请求。
 */
@Getter
@Setter
public class CourseMembershipUpdateRequest {

    @Size(max = 64, message = "courseId长度不能超过64")
    private String courseId;

    @Pattern(regexp = "student|teacher|assistant", message = "membershipRole取值不合法")
    private String membershipRole;

    @Pattern(regexp = "active|pending|suspended|removed", message = "status取值不合法")
    private String status;

    @Pattern(regexp = "manual|imported|self_join|sync", message = "accessSource取值不合法")
    private String accessSource;

    @Size(max = 255, message = "changeReason长度不能超过255")
    private String changeReason;
}
