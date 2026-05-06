package org.ysu.ckqaback.controller;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.ysu.ckqaback.api.ApiPaths;
import org.ysu.ckqaback.course.CourseCoverService;
import org.ysu.ckqaback.course.dto.CourseCoverContent;

import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class CourseCoversControllerWebMvcTest {

    private CourseCoverService courseCoverService;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        courseCoverService = Mockito.mock(CourseCoverService.class);
        mockMvc = MockMvcBuilders.standaloneSetup(new CourseCoversController(courseCoverService)).build();
    }

    @Test
    void shouldReadCourseCoverThroughApiPath() throws Exception {
        given(courseCoverService.load("os.png")).willReturn(CourseCoverContent.builder()
                .bytes(new byte[]{1, 2, 3})
                .contentType("image/png")
                .fileSize(3L)
                .build());

        mockMvc.perform(get(ApiPaths.API_V1 + "/course-covers/os.png"))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Type", "image/png"))
                .andExpect(content().bytes(new byte[]{1, 2, 3}));
    }
}
