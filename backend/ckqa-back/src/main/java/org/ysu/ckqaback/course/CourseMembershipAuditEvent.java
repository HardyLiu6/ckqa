package org.ysu.ckqaback.course;

/**
 * 课程创建时初始教师绑定审计事件。
 */
public record CourseMembershipAuditEvent(
        Long courseMembershipId,
        String courseId,
        Long teacherUserId,
        Long operatorUserId
) {
}
