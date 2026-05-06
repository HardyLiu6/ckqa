package org.ysu.ckqaback.course;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.ysu.ckqaback.api.ApiPageData;
import org.ysu.ckqaback.course.dto.CourseMembershipCreateRequest;
import org.ysu.ckqaback.course.dto.CourseMembershipQueryRequest;
import org.ysu.ckqaback.course.dto.CourseMembershipResponse;
import org.ysu.ckqaback.course.dto.CourseMembershipUpdateRequest;
import org.ysu.ckqaback.entity.CourseMemberships;
import org.ysu.ckqaback.entity.Users;
import org.ysu.ckqaback.service.CourseMembershipsService;
import org.ysu.ckqaback.service.UsersService;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class CourseMembershipManagementServiceTest {

    private CourseMembershipsService courseMembershipsService;
    private UsersService usersService;
    private CourseAccessService courseAccessService;
    private CourseMembershipManagementService service;

    @BeforeEach
    void setUp() {
        courseMembershipsService = mock(CourseMembershipsService.class);
        usersService = mock(UsersService.class);
        courseAccessService = mock(CourseAccessService.class);
        service = new CourseMembershipManagementService(courseMembershipsService, usersService, courseAccessService);
    }

    @Test
    void shouldListCourseMembersWithUserInformationAndAccessState() {
        CourseMembershipQueryRequest request = new CourseMembershipQueryRequest();
        request.setPage(1);
        request.setSize(20);
        when(courseMembershipsService.listByCourseId("os")).thenReturn(List.of(
                membership(21L, 8L, "os", "teacher", "active"),
                membership(22L, 9L, "os", "student", "pending")
        ));
        when(courseAccessService.isActiveMembership(any())).thenAnswer(invocation -> {
            CourseMemberships membership = invocation.getArgument(0);
            return "active".equals(membership.getStatus());
        });
        when(usersService.listByIds(List.of(8L, 9L))).thenReturn(List.of(
                user(8L, "TCH2026001", "teacher.zhangwb", "张文博"),
                user(9L, "STU2026001", "student.zhouzh", "周子涵")
        ));

        ApiPageData<CourseMembershipResponse> page = service.listCourseMembers("os", request, "ADM2026001");

        assertThat(page.getItems()).hasSize(2);
        assertThat(page.getItems().getFirst().getDisplayName()).isEqualTo("张文博");
        assertThat(page.getItems().getFirst().isAccessGranted()).isTrue();
        assertThat(page.getItems().get(1).getDisplayName()).isEqualTo("周子涵");
        assertThat(page.getItems().get(1).isAccessGranted()).isFalse();
        verify(courseAccessService).assertCourseReadable("os", "ADM2026001");
    }

    @Test
    void shouldCreateMembershipWithActorAsGrantor() {
        CourseMembershipCreateRequest request = new CourseMembershipCreateRequest();
        request.setUserId(9L);
        request.setMembershipRole("student");
        request.setStatus("active");
        request.setAccessSource("manual");
        when(usersService.getRequiredById(9L)).thenReturn(user(9L, "STU2026001", "student.zhouzh", "周子涵"));
        when(courseAccessService.findActiveUserByCode("ADM2026001"))
                .thenReturn(user(1L, "ADM2026001", "admin.heqh", "何启航"));
        when(courseMembershipsService.listByCourseId("os")).thenReturn(List.of());
        when(courseMembershipsService.save(any())).thenAnswer(invocation -> {
            CourseMemberships saved = invocation.getArgument(0);
            saved.setId(33L);
            return true;
        });

        CourseMembershipResponse response = service.createCourseMember("os", request, "ADM2026001");

        assertThat(response.getId()).isEqualTo(33L);
        assertThat(response.getUserCode()).isEqualTo("STU2026001");
        assertThat(response.getGrantedByUserId()).isEqualTo(1L);
        verify(courseAccessService).assertCourseMembershipWritable("os", "ADM2026001");
    }

    @Test
    void shouldUpdateExistingMembershipInsteadOfCreatingDuplicate() {
        CourseMembershipCreateRequest request = new CourseMembershipCreateRequest();
        request.setUserId(9L);
        request.setMembershipRole("assistant");
        request.setStatus("active");
        CourseMemberships existing = membership(22L, 9L, "os", "student", "pending");
        when(usersService.getRequiredById(9L)).thenReturn(user(9L, "STU2026001", "student.zhouzh", "周子涵"));
        when(courseMembershipsService.listByCourseId("os")).thenReturn(List.of(existing));

        CourseMembershipResponse response = service.createCourseMember("os", request, "ADM2026001");

        assertThat(response.getId()).isEqualTo(22L);
        assertThat(response.getMembershipRole()).isEqualTo("assistant");
        assertThat(response.getStatus()).isEqualTo("active");
        verify(courseMembershipsService).updateById(existing);
    }

    @Test
    void shouldMarkMembershipRemovedWhenUpdatingStatusToRemoved() {
        CourseMembershipUpdateRequest request = new CourseMembershipUpdateRequest();
        request.setStatus("removed");
        CourseMemberships existing = membership(22L, 9L, "os", "student", "active");
        when(courseMembershipsService.getById(22L)).thenReturn(existing);
        when(courseAccessService.findActiveUserByCode("ADM2026001"))
                .thenReturn(user(1L, "ADM2026001", "admin.heqh", "何启航"));
        when(usersService.listByIds(List.of(9L, 1L))).thenReturn(List.of(
                user(9L, "STU2026001", "student.zhouzh", "周子涵"),
                user(1L, "ADM2026001", "admin.heqh", "何启航")
        ));

        CourseMembershipResponse response = service.updateCourseMember("os", 22L, request, "ADM2026001");

        assertThat(response.getStatus()).isEqualTo("removed");
        assertThat(response.getRevokedByUserId()).isEqualTo(1L);
        assertThat(existing.getEffectiveTo()).isNotNull();
        verify(courseMembershipsService).updateById(existing);
    }

    @Test
    void shouldResolveCourseIdFromMembershipWhenUpdatingById() {
        CourseMembershipUpdateRequest request = new CourseMembershipUpdateRequest();
        request.setStatus("active");
        CourseMemberships existing = membership(22L, 9L, "os", "student", "pending");
        when(courseMembershipsService.getById(22L)).thenReturn(existing);
        when(usersService.listByIds(List.of(9L))).thenReturn(List.of(
                user(9L, "STU2026001", "student.zhouzh", "周子涵")
        ));

        CourseMembershipResponse response = service.updateCourseMember(null, 22L, request, "ADM2026001");

        assertThat(response.getStatus()).isEqualTo("active");
        verify(courseAccessService).assertCourseMembershipWritable("os", "ADM2026001");
        verify(courseMembershipsService).updateById(existing);
    }

    private CourseMemberships membership(Long id, Long userId, String courseId, String role, String status) {
        CourseMemberships membership = new CourseMemberships();
        membership.setId(id);
        membership.setUserId(userId);
        membership.setCourseId(courseId);
        membership.setMembershipRole(role);
        membership.setStatus(status);
        membership.setAccessSource("manual");
        membership.setJoinedAt(LocalDateTime.of(2026, 5, 6, 10, 0));
        membership.setEffectiveFrom(LocalDateTime.of(2026, 5, 6, 10, 0));
        membership.setUpdatedAt(LocalDateTime.of(2026, 5, 6, 10, 0));
        return membership;
    }

    private Users user(Long id, String userCode, String username, String displayName) {
        Users user = new Users();
        user.setId(id);
        user.setUserCode(userCode);
        user.setUsername(username);
        user.setDisplayName(displayName);
        user.setStatus("active");
        return user;
    }
}
