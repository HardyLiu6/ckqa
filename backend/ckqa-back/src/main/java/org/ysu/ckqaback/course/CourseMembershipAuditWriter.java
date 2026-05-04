package org.ysu.ckqaback.course;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;
import org.ysu.ckqaback.entity.CourseMembershipEvents;
import org.ysu.ckqaback.service.CourseMembershipEventsService;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 课程成员关系审计事件写入器。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class CourseMembershipAuditWriter {

    private static final String INITIAL_TEACHER_BOUND = "initial_teacher_bound";
    private static final String ACTIVE = "active";
    private static final String INITIAL_TEACHER_CHANGE_REASON = "COURSE_CREATION_INITIAL_TEACHER";

    private final CourseMembershipEventsService courseMembershipEventsService;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void writeInitialTeacherBoundEventSafely(CourseMembershipAuditEvent event) {
        try {
            CourseMembershipEvents auditEvent = new CourseMembershipEvents();
            auditEvent.setCourseMembershipId(event.courseMembershipId());
            auditEvent.setEventType(INITIAL_TEACHER_BOUND);
            auditEvent.setNewStatus(ACTIVE);
            auditEvent.setOperatorUserId(event.operatorUserId());
            auditEvent.setChangeReason(INITIAL_TEACHER_CHANGE_REASON);
            auditEvent.setEventPayload(buildPayload(event));
            courseMembershipEventsService.save(auditEvent);
        } catch (RuntimeException ex) {
            log.warn("写入课程初始教师绑定审计事件失败: courseId={}, membershipId={}",
                    event.courseId(), event.courseMembershipId(), ex);
        }
    }

    private String buildPayload(CourseMembershipAuditEvent event) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("courseId", event.courseId());
        payload.put("teacherUserId", event.teacherUserId());
        try {
            return objectMapper.writeValueAsString(payload);
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("课程成员审计事件载荷序列化失败", ex);
        }
    }
}
