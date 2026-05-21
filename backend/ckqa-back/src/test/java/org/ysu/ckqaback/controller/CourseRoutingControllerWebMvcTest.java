package org.ysu.ckqaback.controller;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.validation.beanvalidation.LocalValidatorFactoryBean;
import org.ysu.ckqaback.api.ApiPaths;
import org.ysu.ckqaback.auth.AuthConstants;
import org.ysu.ckqaback.auth.AuthenticatedUser;
import org.ysu.ckqaback.course.dto.CourseRoutingCandidateResponse;
import org.ysu.ckqaback.course.dto.CourseRoutingRecommendResponse;
import org.ysu.ckqaback.course.routing.CourseRoutingService;
import org.ysu.ckqaback.exception.GlobalExceptionHandler;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class CourseRoutingControllerWebMvcTest {

    private CourseRoutingService courseRoutingService;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        courseRoutingService = Mockito.mock(CourseRoutingService.class);
        LocalValidatorFactoryBean validator = new LocalValidatorFactoryBean();
        validator.afterPropertiesSet();
        mockMvc = MockMvcBuilders.standaloneSetup(new CourseRoutingController(courseRoutingService))
                .setControllerAdvice(new GlobalExceptionHandler())
                .setValidator(validator)
                .build();
    }

    @Test
    void shouldRecommendCourseForAuthenticatedStudent() throws Exception {
        given(courseRoutingService.recommend(any(), eq(student()))).willReturn(CourseRoutingRecommendResponse.of(
                "matched",
                "os",
                0.74D,
                0.12D,
                List.of(CourseRoutingCandidateResponse.of("os", "操作系统", 0.74D, "课程画像相似度 0.740"))
        ));

        mockMvc.perform(post(ApiPaths.COURSE_ROUTING + "/recommend")
                        .requestAttr(AuthConstants.REQUEST_USER_ATTRIBUTE, student())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "question": "什么是进程",
                                  "userId": 3,
                                  "limit": 3
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("matched"))
                .andExpect(jsonPath("$.data.selectedCourseId").value("os"))
                .andExpect(jsonPath("$.data.confidence").value(0.74))
                .andExpect(jsonPath("$.data.margin").value(0.12))
                .andExpect(jsonPath("$.data.candidates[0].courseName").value("操作系统"));

        then(courseRoutingService).should().recommend(any(), eq(student()));
    }

    @Test
    void shouldRequireAuthForCourseRouting() throws Exception {
        mockMvc.perform(post(ApiPaths.COURSE_ROUTING + "/recommend")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "question": "什么是进程"
                                }
                                """))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value("请先登录"));
    }

    @Test
    void shouldValidateQuestion() throws Exception {
        mockMvc.perform(post(ApiPaths.COURSE_ROUTING + "/recommend")
                        .requestAttr(AuthConstants.REQUEST_USER_ATTRIBUTE, student())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "question": "",
                                  "limit": 3
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("参数校验失败"));
    }

    private AuthenticatedUser student() {
        return new AuthenticatedUser(7L, "student.zhouzh", "student.zhouzh", "周同学", List.of("student"), List.of());
    }
}
