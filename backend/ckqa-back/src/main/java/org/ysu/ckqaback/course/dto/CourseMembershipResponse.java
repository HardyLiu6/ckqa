package org.ysu.ckqaback.course.dto;

import lombok.Builder;
import lombok.Getter;
import org.ysu.ckqaback.entity.CourseMemberships;
import org.ysu.ckqaback.entity.Users;

import java.time.LocalDateTime;

/**
 * 课程成员响应。
 */
@Getter
@Builder
public class CourseMembershipResponse {

    private final Long id;
    private final String courseId;
    private final Long userId;
    private final String userCode;
    private final String username;
    private final String displayName;
    private final String membershipRole;
    private final String status;
    private final String accessSource;
    private final String sourceRefType;
    private final String sourceRefId;
    private final LocalDateTime joinedAt;
    private final LocalDateTime effectiveFrom;
    private final LocalDateTime effectiveTo;
    private final LocalDateTime expiresAt;
    private final Long grantedByUserId;
    private final String grantedByDisplayName;
    private final Long revokedByUserId;
    private final String changeReason;
    private final boolean accessGranted;
    private final LocalDateTime updatedAt;

    public static CourseMembershipResponse fromEntity(
            CourseMemberships membership,
            Users user,
            Users grantedBy,
            boolean accessGranted
    ) {
        return CourseMembershipResponse.builder()
                .id(membership.getId())
                .courseId(membership.getCourseId())
                .userId(membership.getUserId())
                .userCode(user == null ? null : user.getUserCode())
                .username(user == null ? null : user.getUsername())
                .displayName(user == null ? null : user.getDisplayName())
                .membershipRole(membership.getMembershipRole())
                .status(membership.getStatus())
                .accessSource(membership.getAccessSource())
                .sourceRefType(membership.getSourceRefType())
                .sourceRefId(membership.getSourceRefId())
                .joinedAt(membership.getJoinedAt())
                .effectiveFrom(membership.getEffectiveFrom())
                .effectiveTo(membership.getEffectiveTo())
                .expiresAt(membership.getExpiresAt())
                .grantedByUserId(membership.getGrantedByUserId())
                .grantedByDisplayName(grantedBy == null ? null : grantedBy.getDisplayName())
                .revokedByUserId(membership.getRevokedByUserId())
                .changeReason(membership.getChangeReason())
                .accessGranted(accessGranted)
                .updatedAt(membership.getUpdatedAt())
                .build();
    }
}
