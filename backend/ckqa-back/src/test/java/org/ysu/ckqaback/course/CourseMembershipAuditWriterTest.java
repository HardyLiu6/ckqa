package org.ysu.ckqaback.course;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.ysu.ckqaback.entity.CourseMembershipEvents;
import org.ysu.ckqaback.service.CourseMembershipEventsService;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class CourseMembershipAuditWriterTest {

    private CourseMembershipEventsService courseMembershipEventsService;
    private CourseMembershipAuditWriter writer;

    @BeforeEach
    void setUp() {
        courseMembershipEventsService = mock(CourseMembershipEventsService.class);
        writer = new CourseMembershipAuditWriter(courseMembershipEventsService);
    }

    @Test
    void shouldMapInitialTeacherBoundEventToAuditEntity() {
        CourseMembershipAuditEvent event = new CourseMembershipAuditEvent(
                21L,
                "crs-20260504-7f3k2a",
                8L,
                null
        );

        assertThatCode(() -> writer.writeInitialTeacherBoundEventSafely(event))
                .doesNotThrowAnyException();

        ArgumentCaptor<CourseMembershipEvents> eventCaptor = ArgumentCaptor.forClass(CourseMembershipEvents.class);
        verify(courseMembershipEventsService).save(eventCaptor.capture());
        CourseMembershipEvents saved = eventCaptor.getValue();
        assertThat(saved.getCourseMembershipId()).isEqualTo(21L);
        assertThat(saved.getEventType()).isEqualTo("initial_teacher_bound");
        assertThat(saved.getNewStatus()).isEqualTo("active");
        assertThat(saved.getChangeReason()).isEqualTo("COURSE_CREATION_INITIAL_TEACHER");
        assertThat(saved.getOperatorUserId()).isNull();
        assertThat(saved.getEventPayload()).contains("\"courseId\":\"crs-20260504-7f3k2a\"");
        assertThat(saved.getEventPayload()).contains("\"teacherUserId\":8");
    }

    @Test
    void shouldSwallowAuditWriteFailure() {
        CourseMembershipAuditEvent event = new CourseMembershipAuditEvent(
                21L,
                "crs-20260504-7f3k2a",
                8L,
                null
        );
        doThrow(new RuntimeException("audit table unavailable"))
                .when(courseMembershipEventsService)
                .save(any(CourseMembershipEvents.class));

        assertThatCode(() -> writer.writeInitialTeacherBoundEventSafely(event))
                .doesNotThrowAnyException();
    }
}
