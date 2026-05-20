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
import org.ysu.ckqaback.exception.GlobalExceptionHandler;
import org.ysu.ckqaback.qa.memory.QaMemoryService;
import org.ysu.ckqaback.qa.memory.dto.QaMemoryItemResponse;
import org.ysu.ckqaback.qa.memory.dto.QaMemoryPreferenceResponse;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class QaMemoryControllerWebMvcTest {

    private QaMemoryService qaMemoryService;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        qaMemoryService = Mockito.mock(QaMemoryService.class);
        LocalValidatorFactoryBean validator = new LocalValidatorFactoryBean();
        validator.afterPropertiesSet();
        mockMvc = MockMvcBuilders.standaloneSetup(new QaMemoryController(qaMemoryService))
                .setControllerAdvice(new GlobalExceptionHandler())
                .setValidator(validator)
                .build();
    }

    @Test
    void shouldGetPreferenceForCurrentUserScope() throws Exception {
        given(qaMemoryService.getPreferences("os", 3L, student()))
                .willReturn(QaMemoryPreferenceResponse.of("os", 3L, 17L, true));

        mockMvc.perform(get(ApiPaths.QA_MEMORY + "/preferences")
                        .requestAttr(AuthConstants.REQUEST_USER_ATTRIBUTE, student())
                        .param("courseId", "os")
                        .param("knowledgeBaseId", "3"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.courseId").value("os"))
                .andExpect(jsonPath("$.data.knowledgeBaseId").value(3))
                .andExpect(jsonPath("$.data.indexRunId").value(17))
                .andExpect(jsonPath("$.data.enabled").value(true));
    }

    @Test
    void shouldUpdatePreferenceForCurrentUserScope() throws Exception {
        given(qaMemoryService.updatePreferences(any(), eq(student())))
                .willReturn(QaMemoryPreferenceResponse.of("os", 3L, 17L, true));

        mockMvc.perform(put(ApiPaths.QA_MEMORY + "/preferences")
                        .requestAttr(AuthConstants.REQUEST_USER_ATTRIBUTE, student())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "courseId": "os",
                                  "knowledgeBaseId": 3,
                                  "enabled": true
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.enabled").value(true));
    }

    @Test
    void shouldListItemsWithoutMemoryText() throws Exception {
        given(qaMemoryService.listItems("os", 3L, student()))
                .willReturn(List.of(QaMemoryItemResponse.of(101L, "learning_topic", 5L, 12L, "active", null, null)));

        mockMvc.perform(get(ApiPaths.QA_MEMORY + "/items")
                        .requestAttr(AuthConstants.REQUEST_USER_ATTRIBUTE, student())
                        .param("courseId", "os")
                        .param("knowledgeBaseId", "3"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].id").value(101))
                .andExpect(jsonPath("$.data[0].memoryType").value("learning_topic"))
                .andExpect(jsonPath("$.data[0].memoryText").doesNotExist());
    }

    @Test
    void shouldSoftDeleteOwnItem() throws Exception {
        mockMvc.perform(delete(ApiPaths.QA_MEMORY + "/items/101")
                        .requestAttr(AuthConstants.REQUEST_USER_ATTRIBUTE, student()))
                .andExpect(status().isOk());

        then(qaMemoryService).should().deleteItem(101L, student());
    }

    @Test
    void shouldRequireAuth() throws Exception {
        mockMvc.perform(get(ApiPaths.QA_MEMORY + "/items")
                        .param("courseId", "os")
                        .param("knowledgeBaseId", "3"))
                .andExpect(status().isUnauthorized());
    }

    private AuthenticatedUser student() {
        return new AuthenticatedUser(7L, "student.zhouzh", "student.zhouzh", "周同学", List.of("student"), List.of());
    }
}
