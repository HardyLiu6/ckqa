package org.ysu.ckqaback.controller;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.validation.beanvalidation.LocalValidatorFactoryBean;
import org.ysu.ckqaback.api.ApiPageData;
import org.ysu.ckqaback.api.ApiPaths;
import org.ysu.ckqaback.course.CourseAccessDecision;
import org.ysu.ckqaback.course.CourseAccessService;
import org.ysu.ckqaback.course.CourseMembershipManagementService;
import org.ysu.ckqaback.course.dto.CourseMembershipCreateRequest;
import org.ysu.ckqaback.course.dto.CourseMembershipQueryRequest;
import org.ysu.ckqaback.course.dto.CourseMembershipResponse;
import org.ysu.ckqaback.course.dto.CourseMembershipUpdateRequest;
import org.ysu.ckqaback.exception.GlobalExceptionHandler;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class CourseMembershipsControllerWebMvcTest {

    private CourseMembershipManagementService managementService;
    private CourseAccessService accessService;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        managementService = Mockito.mock(CourseMembershipManagementService.class);
        accessService = Mockito.mock(CourseAccessService.class);
        LocalValidatorFactoryBean validator = new LocalValidatorFactoryBean();
        validator.afterPropertiesSet();
        mockMvc = MockMvcBuilders.standaloneSetup(new CourseMembershipsController(managementService, accessService))
                .setControllerAdvice(new GlobalExceptionHandler())
                .setValidator(validator)
                .build();
    }

    @Test
    void shouldListCourseMembersWithActorHeader() throws Exception {
        given(managementService.listCourseMembers(eq("os"), any(CourseMembershipQueryRequest.class), eq("ADM2026001")))
                .willReturn(new ApiPageData<>(List.of(member()), 1, 20, 1, 1));

        mockMvc.perform(get(ApiPaths.COURSE_MEMBERSHIPS)
                        .header("X-CKQA-User-Code", "ADM2026001")
                        .param("courseId", "os")
                        .param("status", "active")
                        .param("page", "1")
                        .param("size", "20"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items[0].userCode").value("TCH2026001"))
                .andExpect(jsonPath("$.data.items[0].accessGranted").value(true));

        ArgumentCaptor<CourseMembershipQueryRequest> requestCaptor = ArgumentCaptor.forClass(CourseMembershipQueryRequest.class);
        then(managementService).should().listCourseMembers(eq("os"), requestCaptor.capture(), eq("ADM2026001"));
        assertThat(requestCaptor.getValue().getStatus()).isEqualTo("active");
    }

    @Test
    void shouldCreateCourseMember() throws Exception {
        given(managementService.createCourseMember(eq("os"), any(CourseMembershipCreateRequest.class), eq("ADM2026001")))
                .willReturn(member());

        mockMvc.perform(post(ApiPaths.COURSE_MEMBERSHIPS)
                        .header("X-CKQA-User-Code", "ADM2026001")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "courseId": "os",
                                  "userId": 8,
                                  "membershipRole": "teacher",
                                  "status": "active",
                                  "accessSource": "manual"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value(21));

        then(managementService).should().createCourseMember(eq("os"), any(CourseMembershipCreateRequest.class), eq("ADM2026001"));
    }

    @Test
    void shouldUpdateCourseMember() throws Exception {
        given(managementService.updateCourseMember(eq("os"), eq(21L), any(CourseMembershipUpdateRequest.class), eq("ADM2026001")))
                .willReturn(member());

        mockMvc.perform(patch(ApiPaths.COURSE_MEMBERSHIPS + "/21")
                        .header("X-CKQA-User-Code", "ADM2026001")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "courseId": "os",
                                  "status": "suspended"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.userCode").value("TCH2026001"));
    }

    @Test
    void shouldUpdateCourseMemberWithoutCourseIdInBody() throws Exception {
        given(managementService.updateCourseMember(eq(null), eq(21L), any(CourseMembershipUpdateRequest.class), eq("ADM2026001")))
                .willReturn(member());

        mockMvc.perform(patch(ApiPaths.COURSE_MEMBERSHIPS + "/21")
                        .header("X-CKQA-User-Code", "ADM2026001")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "status": "suspended"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.userCode").value("TCH2026001"));

        then(managementService).should().updateCourseMember(eq(null), eq(21L), any(CourseMembershipUpdateRequest.class), eq("ADM2026001"));
    }

    @Test
    void shouldResolveCourseAccess() throws Exception {
        given(accessService.resolveAccess("os", "TCH2026001"))
                .willReturn(CourseAccessDecision.granted("os", 8L, "TCH2026001", 21L, "teacher", "active-membership"));

        mockMvc.perform(get(ApiPaths.COURSE_MEMBERSHIPS + "/access")
                        .header("X-CKQA-User-Code", "TCH2026001")
                        .param("courseId", "os"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.granted").value(true))
                .andExpect(jsonPath("$.data.membershipRole").value("teacher"));
    }

    private CourseMembershipResponse member() {
        return CourseMembershipResponse.builder()
                .id(21L)
                .courseId("os")
                .userId(8L)
                .userCode("TCH2026001")
                .username("teacher.zhangwb")
                .displayName("张文博")
                .membershipRole("teacher")
                .status("active")
                .accessSource("manual")
                .accessGranted(true)
                .updatedAt(LocalDateTime.of(2026, 5, 6, 10, 0))
                .build();
    }
}
