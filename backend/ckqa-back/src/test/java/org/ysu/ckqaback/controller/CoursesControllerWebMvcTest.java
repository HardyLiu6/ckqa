package org.ysu.ckqaback.controller;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.http.HttpStatus;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.ysu.ckqaback.api.ApiResultCode;
import org.ysu.ckqaback.api.ApiPageData;
import org.ysu.ckqaback.api.ApiPaths;
import org.ysu.ckqaback.course.CourseLookupService;
import org.ysu.ckqaback.course.dto.CourseDetailResponse;
import org.ysu.ckqaback.course.dto.CoursePdfFileSummaryResponse;
import org.ysu.ckqaback.course.dto.CourseQueryRequest;
import org.ysu.ckqaback.course.dto.CourseSummaryResponse;
import org.ysu.ckqaback.exception.BusinessException;
import org.ysu.ckqaback.exception.GlobalExceptionHandler;

import java.time.LocalDateTime;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class CoursesControllerWebMvcTest {

    private CourseLookupService courseLookupService;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        courseLookupService = Mockito.mock(CourseLookupService.class);
        mockMvc = MockMvcBuilders.standaloneSetup(new CoursesController(courseLookupService))
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
