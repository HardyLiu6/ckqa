package org.ysu.ckqaback.controller;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.ysu.ckqaback.api.ApiPaths;
import org.ysu.ckqaback.course.CourseLookupService;
import org.ysu.ckqaback.course.dto.CoursePdfFileSummaryResponse;
import org.ysu.ckqaback.exception.GlobalExceptionHandler;

import java.util.List;

import static org.mockito.BDDMockito.given;
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
