package org.ysu.ckqaback.course;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 课程访问判定结果。
 */
@Getter
@AllArgsConstructor
public class CourseAccessDecision {

    private final boolean granted;
    private final String courseId;
    private final Long userId;
    private final String userCode;
    private final Long membershipId;
    private final String membershipRole;
    private final String reason;

    public static CourseAccessDecision granted(
            String courseId,
            Long userId,
            String userCode,
            Long membershipId,
            String membershipRole,
            String reason
    ) {
        return new CourseAccessDecision(true, courseId, userId, userCode, membershipId, membershipRole, reason);
    }

    public static CourseAccessDecision denied(String courseId, Long userId, String userCode, String reason) {
        return new CourseAccessDecision(false, courseId, userId, userCode, null, null, reason);
    }
}
