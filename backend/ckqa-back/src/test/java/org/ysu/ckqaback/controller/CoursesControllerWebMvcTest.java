package org.ysu.ckqaback.controller;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.ysu.ckqaback.api.ApiResultCode;
import org.ysu.ckqaback.api.ApiPageData;
import org.ysu.ckqaback.api.ApiPaths;
import org.ysu.ckqaback.course.CourseCommandService;
import org.ysu.ckqaback.course.CourseLookupService;
import org.ysu.ckqaback.course.dto.CourseDetailResponse;
import org.ysu.ckqaback.course.dto.CoursePdfFileSummaryResponse;
import org.ysu.ckqaback.course.dto.CourseQueryRequest;
import org.ysu.ckqaback.course.dto.CourseSummaryResponse;
import org.ysu.ckqaback.course.dto.CourseTeacherResponse;
import org.ysu.ckqaback.exception.BusinessException;
import org.ysu.ckqaback.exception.GlobalExceptionHandler;

import java.time.LocalDateTime;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class CoursesControllerWebMvcTest {

    private CourseLookupService courseLookupService;
    private CourseCommandService courseCommandService;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        courseLookupService = Mockito.mock(CourseLookupService.class);
        courseCommandService = Mockito.mock(CourseCommandService.class);
        mockMvc = MockMvcBuilders.standaloneSetup(new CoursesController(courseLookupService, courseCommandService))
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    void shouldListCourses() throws Exception {
        given(courseLookupService.listCourses(any(CourseQueryRequest.class))).willReturn(new ApiPageData<>(
                List.of(CourseSummaryResponse.builder()
                        .id(1L)
                        .courseId("os")
                        .courseName("操作系统")
                        .status("active")
                        .materialCount(2L)
                        .parsedMaterialCount(1L)
                        .failedMaterialCount(0L)
                        .knowledgeBaseCount(1L)
                        .activeKnowledgeBaseCount(1L)
                        .latestIndexRunId(9L)
                        .latestIndexRunStatus("success")
                        .updatedAt(LocalDateTime.of(2026, 4, 28, 9, 30))
                        .build()),
                1,
                20,
                1,
                1
        ));

        mockMvc.perform(get(ApiPaths.COURSES)
                        .param("page", "1")
                        .param("size", "20"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items[0].courseId").value("os"))
                .andExpect(jsonPath("$.data.current").value(1))
                .andExpect(jsonPath("$.data.total").value(1));
    }

    @Test
    void shouldGetCourseDetail() throws Exception {
        given(courseLookupService.getCourseDetail("os")).willReturn(CourseDetailResponse.builder()
                .id(1L)
                .courseId("os")
                .courseName("操作系统")
                .status("active")
                .materialCount(2L)
                .knowledgeBaseCount(1L)
                .memberCount(null)
                .build());

        mockMvc.perform(get(ApiPaths.COURSES + "/os"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.courseId").value("os"))
                .andExpect(jsonPath("$.data.courseName").value("操作系统"));
    }

    @Test
    void shouldCreateCourse() throws Exception {
        given(courseCommandService.createCourse(any())).willReturn(CourseDetailResponse.builder()
                .id(3L)
                .courseId("crs-20260504-7f3k2a")
                .courseName("数据库系统")
                .status("active")
                .accessPolicy("restricted")
                .teachers(List.of(CourseTeacherResponse.builder()
                        .userId(8L)
                        .userCode("t008")
                        .username("zhang")
                        .displayName("张老师")
                        .build()))
                .teacherCount(1L)
                .materialCount(0L)
                .knowledgeBaseCount(0L)
                .build());

        mockMvc.perform(post(ApiPaths.COURSES)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "courseName": "数据库系统",
                                  "description": "数据库课程资料",
                                  "accessPolicy": "restricted",
                                  "teacherUserId": 8
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.courseId").value("crs-20260504-7f3k2a"))
                .andExpect(jsonPath("$.data.courseName").value("数据库系统"))
                .andExpect(jsonPath("$.data.status").value("active"))
                .andExpect(jsonPath("$.data.teachers[0].userId").value(8));
    }

    @Test
    void shouldRejectCreateCourseWhenTeacherUserIdMissing() throws Exception {
        mockMvc.perform(post(ApiPaths.COURSES)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "courseName": "数据库系统",
                                  "description": "数据库课程资料",
                                  "accessPolicy": "restricted"
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(4001))
                .andExpect(jsonPath("$.message").value("参数校验失败"));
    }

    @Test
    void shouldReturnNotFoundWhenCourseMissing() throws Exception {
        willThrow(new BusinessException(ApiResultCode.BAD_REQUEST, HttpStatus.NOT_FOUND, "课程不存在"))
                .given(courseLookupService).getCourseDetail("missing");

        mockMvc.perform(get(ApiPaths.COURSES + "/missing"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value(4000))
                .andExpect(jsonPath("$.message").value("课程不存在"));
    }

    @Test
    void shouldListCoursePdfFiles() throws Exception {
        given(courseLookupService.listCoursePdfFiles("os")).willReturn(List.of(
                CoursePdfFileSummaryResponse.of(7L, 7L, 17L, "book.pdf", "done")
        ));

        mockMvc.perform(get(ApiPaths.COURSES + "/os/pdf-files"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].id").value(7))
                .andExpect(jsonPath("$.data[0].materialId").value(7))
                .andExpect(jsonPath("$.data[0].materialObjectId").value(17))
                .andExpect(jsonPath("$.data[0].fileName").value("book.pdf"));
    }
}
