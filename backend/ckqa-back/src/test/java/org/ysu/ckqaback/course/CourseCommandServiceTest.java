package org.ysu.ckqaback.course;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.ysu.ckqaback.api.ApiResultCode;
import org.ysu.ckqaback.course.dto.CourseCreateRequest;
import org.ysu.ckqaback.course.dto.CourseDetailResponse;
import org.ysu.ckqaback.entity.CourseMemberships;
import org.ysu.ckqaback.entity.Courses;
import org.ysu.ckqaback.entity.Users;
import org.ysu.ckqaback.exception.BusinessException;
import org.ysu.ckqaback.service.CourseMembershipsService;
import org.ysu.ckqaback.service.CoursesService;
import org.ysu.ckqaback.service.UsersService;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class CourseCommandServiceTest {

    private CoursesService coursesService;
    private CourseMembershipsService courseMembershipsService;
    private UsersService usersService;
    private CourseIdGenerator courseIdGenerator;
    private ApplicationEventPublisher eventPublisher;
    private CourseCommandService service;

    @BeforeEach
    void setUp() {
        coursesService = mock(CoursesService.class);
        courseMembershipsService = mock(CourseMembershipsService.class);
        usersService = mock(UsersService.class);
        courseIdGenerator = mock(CourseIdGenerator.class);
        eventPublisher = mock(ApplicationEventPublisher.class);
        service = new CourseCommandService(
                coursesService,
                courseMembershipsService,
                usersService,
                courseIdGenerator,
                eventPublisher
        );
    }

    @Test
    void shouldCreateCourseWithoutClientProvidedCourseIdAndBindInitialTeacher() {
        CourseCreateRequest request = createRequest();
        when(usersService.getRequiredById(8L)).thenReturn(activeTeacher());
        when(usersService.hasRole(8L, "teacher")).thenReturn(true);
        when(courseIdGenerator.generate()).thenReturn("crs-20260504-7f3k2a");
        when(coursesService.count(any())).thenReturn(0L);
        when(coursesService.save(any(Courses.class))).thenAnswer(invocation -> {
            Courses saved = invocation.getArgument(0);
            saved.setId(12L);
            return true;
        });
        when(courseMembershipsService.save(any(CourseMemberships.class))).thenAnswer(invocation -> {
            CourseMemberships saved = invocation.getArgument(0);
            saved.setId(21L);
            return true;
        });

        CourseDetailResponse response = service.createCourse(request);

        assertThat(response.getId()).isEqualTo(12L);
        assertThat(response.getCourseId()).matches("^crs-[0-9]{8}-[a-z0-9]{6}$");
        assertThat(response.getTeachers()).singleElement()
                .satisfies(teacher -> assertThat(teacher.getUserId()).isEqualTo(8L));

        InOrder saveOrder = inOrder(coursesService, courseMembershipsService, eventPublisher);

        ArgumentCaptor<Courses> courseCaptor = ArgumentCaptor.forClass(Courses.class);
        saveOrder.verify(coursesService).save(courseCaptor.capture());
        assertThat(courseCaptor.getValue().getCourseId()).isEqualTo("crs-20260504-7f3k2a");

        ArgumentCaptor<CourseMemberships> membershipCaptor = ArgumentCaptor.forClass(CourseMemberships.class);
        saveOrder.verify(courseMembershipsService).save(membershipCaptor.capture());
        CourseMemberships membership = membershipCaptor.getValue();
        assertThat(membership.getCourseId()).isEqualTo("crs-20260504-7f3k2a");
        assertThat(membership.getUserId()).isEqualTo(8L);
        assertThat(membership.getMembershipRole()).isEqualTo("teacher");
        assertThat(membership.getStatus()).isEqualTo("active");
        assertThat(membership.getAccessSource()).isEqualTo("manual");
        assertThat(membership.getChangeReason()).isEqualTo("COURSE_CREATION_INITIAL_TEACHER");

        ArgumentCaptor<CourseMembershipAuditEvent> eventCaptor = ArgumentCaptor.forClass(CourseMembershipAuditEvent.class);
        saveOrder.verify(eventPublisher).publishEvent(eventCaptor.capture());
        assertThat(eventCaptor.getValue().courseMembershipId()).isEqualTo(21L);
        assertThat(eventCaptor.getValue().courseId()).isEqualTo("crs-20260504-7f3k2a");
        assertThat(eventCaptor.getValue().teacherUserId()).isEqualTo(8L);
        assertThat(eventCaptor.getValue().operatorUserId()).isNull();
    }

    @Test
    void shouldRejectUserWithoutTeacherRoleWhenCreatingCourse() {
        CourseCreateRequest request = createRequest();
        when(usersService.getRequiredById(8L)).thenReturn(activeTeacher());
        when(usersService.hasRole(8L, "teacher")).thenReturn(false);

        assertThatThrownBy(() -> service.createCourse(request))
                .isInstanceOf(BusinessException.class)
                .hasMessage("初始教师必须拥有teacher角色")
                .extracting("status")
                .isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void shouldRejectDisabledTeacherWhenCreatingCourse() {
        CourseCreateRequest request = createRequest();
        when(usersService.getRequiredById(8L)).thenReturn(disabledTeacher());
        when(usersService.hasRole(8L, "teacher")).thenReturn(true);

        assertThatThrownBy(() -> service.createCourse(request))
                .isInstanceOf(BusinessException.class)
                .hasMessage("初始教师状态不可用")
                .extracting("status")
                .isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void shouldPropagateMembershipSaveFailureForTransactionRollback() {
        CourseCreateRequest request = createRequest();
        when(usersService.getRequiredById(8L)).thenReturn(activeTeacher());
        when(usersService.hasRole(8L, "teacher")).thenReturn(true);
        when(courseIdGenerator.generate()).thenReturn("crs-20260504-7f3k2a");
        when(coursesService.count(any())).thenReturn(0L);
        when(coursesService.save(any(Courses.class))).thenAnswer(invocation -> {
            Courses saved = invocation.getArgument(0);
            saved.setId(12L);
            return true;
        });
        doThrow(new RuntimeException("DB error")).when(courseMembershipsService).save(any(CourseMemberships.class));

        assertThatThrownBy(() -> service.createCourse(request))
                .isInstanceOf(RuntimeException.class)
                .hasMessage("DB error");
        verify(eventPublisher, never()).publishEvent(any());
    }

    @Test
    void shouldRetryWhenCourseSaveFailsWithCourseIdUniqueIndexConflict() {
        CourseCreateRequest request = createRequest();
        when(usersService.getRequiredById(8L)).thenReturn(activeTeacher());
        when(usersService.hasRole(8L, "teacher")).thenReturn(true);
        when(courseIdGenerator.generate())
                .thenReturn("crs-20260504-aaaaaa")
                .thenReturn("crs-20260504-bbbbbb");
        when(coursesService.count(any())).thenReturn(0L);
        when(coursesService.save(any(Courses.class)))
                .thenThrow(new DataIntegrityViolationException(
                        "Duplicate entry for key",
                        new RuntimeException("courses.uk_course_id")
                ))
                .thenAnswer(invocation -> {
                    Courses saved = invocation.getArgument(0);
                    saved.setId(12L);
                    return true;
                });
        when(courseMembershipsService.save(any(CourseMemberships.class))).thenAnswer(invocation -> {
            CourseMemberships saved = invocation.getArgument(0);
            saved.setId(21L);
            return true;
        });

        CourseDetailResponse response = service.createCourse(request);

        assertThat(response.getCourseId()).isEqualTo("crs-20260504-bbbbbb");
        verify(coursesService, times(2)).save(any(Courses.class));
        ArgumentCaptor<CourseMemberships> membershipCaptor = ArgumentCaptor.forClass(CourseMemberships.class);
        verify(courseMembershipsService).save(membershipCaptor.capture());
        assertThat(membershipCaptor.getValue().getCourseId()).isEqualTo("crs-20260504-bbbbbb");
        verify(eventPublisher).publishEvent(any(CourseMembershipAuditEvent.class));
    }

    @Test
    void shouldPropagateNonCourseIdUniqueIndexSaveFailureWithoutRetryingMembership() {
        CourseCreateRequest request = createRequest();
        DataIntegrityViolationException exception = new DataIntegrityViolationException("Duplicate entry for key 'uk_course_name'");
        when(usersService.getRequiredById(8L)).thenReturn(activeTeacher());
        when(usersService.hasRole(8L, "teacher")).thenReturn(true);
        when(courseIdGenerator.generate()).thenReturn("crs-20260504-7f3k2a");
        when(coursesService.count(any())).thenReturn(0L);
        when(coursesService.save(any(Courses.class))).thenThrow(exception);

        assertThatThrownBy(() -> service.createCourse(request))
                .isSameAs(exception);
        verify(coursesService).save(any(Courses.class));
        verify(courseMembershipsService, never()).save(any(CourseMemberships.class));
        verify(eventPublisher, never()).publishEvent(any());
    }

    @Test
    void shouldReturnConflictWhenGeneratedCourseIdCollidesFiveTimes() {
        CourseCreateRequest request = createRequest();
        when(usersService.getRequiredById(8L)).thenReturn(activeTeacher());
        when(usersService.hasRole(8L, "teacher")).thenReturn(true);
        when(courseIdGenerator.generate())
                .thenReturn("crs-20260504-aaaaaa")
                .thenReturn("crs-20260504-bbbbbb")
                .thenReturn("crs-20260504-cccccc")
                .thenReturn("crs-20260504-dddddd")
                .thenReturn("crs-20260504-eeeeee");
        when(coursesService.count(any())).thenReturn(1L);

        assertThatThrownBy(() -> service.createCourse(request))
                .isInstanceOf(BusinessException.class)
                .hasMessage("课程标识生成冲突，请稍后重试")
                .extracting("code")
                .isEqualTo(ApiResultCode.COURSE_ID_EXISTS.getCode());
    }

    private CourseCreateRequest createRequest() {
        CourseCreateRequest request = new CourseCreateRequest();
        request.setCourseName("数据库系统");
        request.setTeacherUserId(8L);
        request.setAccessPolicy("restricted");
        return request;
    }

    private Users activeTeacher() {
        Users user = new Users();
        user.setId(8L);
        user.setUserCode("t008");
        user.setUsername("zhang");
        user.setDisplayName("张老师");
        user.setStatus("active");
        user.setCreatedAt(LocalDateTime.of(2026, 5, 4, 9, 0));
        user.setUpdatedAt(LocalDateTime.of(2026, 5, 4, 9, 0));
        return user;
    }

    private Users disabledTeacher() {
        Users user = activeTeacher();
        user.setStatus("disabled");
        return user;
    }
}
