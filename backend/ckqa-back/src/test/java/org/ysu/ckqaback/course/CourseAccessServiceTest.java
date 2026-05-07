package org.ysu.ckqaback.course;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.ysu.ckqaback.entity.CourseMemberships;
import org.ysu.ckqaback.entity.Courses;
import org.ysu.ckqaback.entity.Users;
import org.ysu.ckqaback.service.CourseMembershipsService;
import org.ysu.ckqaback.service.CoursesService;
import org.ysu.ckqaback.service.UsersService;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class CourseAccessServiceTest {

    private CoursesService coursesService;
    private CourseMembershipsService courseMembershipsService;
    private UsersService usersService;
    private CourseAccessService service;

    @BeforeEach
    void setUp() {
        coursesService = mock(CoursesService.class);
        courseMembershipsService = mock(CourseMembershipsService.class);
        usersService = mock(UsersService.class);
        service = new CourseAccessService(coursesService, courseMembershipsService, usersService);
    }

    @Test
    void shouldAllowPublicCourseWithoutActor() {
        when(coursesService.getOne(any())).thenReturn(course("os", "public"));

        CourseAccessDecision decision = service.resolveAccess("os", null);

        assertThat(decision.isGranted()).isTrue();
        assertThat(decision.getReason()).isEqualTo("public-course");
    }

    @Test
    void shouldAllowRestrictedCourseWhenActorHasActiveMembership() {
        when(coursesService.getOne(any())).thenReturn(course("os", "restricted"));
        when(usersService.getOne(any())).thenReturn(user(8L, "TCH2026001", "active"));
        when(usersService.hasRole(8L, "admin")).thenReturn(false);
        when(courseMembershipsService.listByCourseId("os")).thenReturn(List.of(
                membership(21L, 8L, "os", "teacher", "active")
        ));

        CourseAccessDecision decision = service.resolveAccess("os", "TCH2026001");

        assertThat(decision.isGranted()).isTrue();
        assertThat(decision.getMembershipId()).isEqualTo(21L);
        assertThat(decision.getMembershipRole()).isEqualTo("teacher");
        assertThat(decision.getReason()).isEqualTo("active-membership");
    }

    @Test
    void shouldRejectRestrictedCourseWhenMembershipIsSuspended() {
        when(coursesService.getOne(any())).thenReturn(course("os", "restricted"));
        when(usersService.getOne(any())).thenReturn(user(9L, "STU2026001", "active"));
        when(usersService.hasRole(9L, "admin")).thenReturn(false);
        when(courseMembershipsService.listByCourseId("os")).thenReturn(List.of(
                membership(22L, 9L, "os", "student", "suspended")
        ));

        CourseAccessDecision decision = service.resolveAccess("os", "STU2026001");

        assertThat(decision.isGranted()).isFalse();
        assertThat(decision.getReason()).isEqualTo("no-active-membership");
    }

    @Test
    void shouldAllowAdminToAccessRestrictedCourseWithoutMembership() {
        when(coursesService.getOne(any())).thenReturn(course("os", "restricted"));
        when(usersService.getOne(any())).thenReturn(user(1L, "ADM2026001", "active"));
        when(usersService.hasRole(1L, "admin")).thenReturn(true);

        CourseAccessDecision decision = service.resolveAccess("os", "ADM2026001");

        assertThat(decision.isGranted()).isTrue();
        assertThat(decision.getReason()).isEqualTo("admin-override");
    }

    @Test
    void shouldRejectWriteWhenCourseIsArchivedEvenWithoutActor() {
        Courses course = course("os", "restricted");
        course.setStatus("archived");
        when(coursesService.getOne(any())).thenReturn(course);

        assertThatThrownBy(() -> service.assertCourseMembershipWritable("os", null))
                .isInstanceOf(org.ysu.ckqaback.exception.BusinessException.class)
                .hasMessage("已归档课程不可编辑，请先撤销归档");
    }

    @Test
    void shouldIgnoreExpiredMembership() {
        CourseMemberships expired = membership(23L, 10L, "os", "student", "active");
        expired.setEffectiveTo(LocalDateTime.now().minusDays(1));
        when(coursesService.getOne(any())).thenReturn(course("os", "restricted"));
        when(usersService.getOne(any())).thenReturn(user(10L, "STU2026002", "active"));
        when(usersService.hasRole(10L, "admin")).thenReturn(false);
        when(courseMembershipsService.listByCourseId("os")).thenReturn(List.of(expired));

        CourseAccessDecision decision = service.resolveAccess("os", "STU2026002");

        assertThat(decision.isGranted()).isFalse();
        assertThat(decision.getReason()).isEqualTo("no-active-membership");
    }

    private Courses course(String courseId, String accessPolicy) {
        Courses course = new Courses();
        course.setCourseId(courseId);
        course.setCourseName("操作系统");
        course.setAccessPolicy(accessPolicy);
        course.setStatus("active");
        return course;
    }

    private Users user(Long id, String userCode, String status) {
        Users user = new Users();
        user.setId(id);
        user.setUserCode(userCode);
        user.setUsername(userCode.toLowerCase());
        user.setDisplayName(userCode);
        user.setStatus(status);
        return user;
    }

    private CourseMemberships membership(Long id, Long userId, String courseId, String role, String status) {
        CourseMemberships membership = new CourseMemberships();
        membership.setId(id);
        membership.setUserId(userId);
        membership.setCourseId(courseId);
        membership.setMembershipRole(role);
        membership.setStatus(status);
        membership.setEffectiveFrom(LocalDateTime.now().minusDays(1));
        return membership;
    }
}
