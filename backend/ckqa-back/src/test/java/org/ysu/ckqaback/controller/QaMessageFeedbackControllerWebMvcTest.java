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
import org.ysu.ckqaback.qa.dto.QaFeedbackResponse;
import org.ysu.ckqaback.service.QaMessageFeedbackService;

import java.time.LocalDateTime;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class QaMessageFeedbackControllerWebMvcTest {

    private QaMessageFeedbackService feedbackService;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        feedbackService = Mockito.mock(QaMessageFeedbackService.class);
        LocalValidatorFactoryBean validator = new LocalValidatorFactoryBean();
        validator.afterPropertiesSet();
        mockMvc = MockMvcBuilders.standaloneSetup(new QaMessageFeedbackController(feedbackService))
                .setControllerAdvice(new GlobalExceptionHandler())
                .setValidator(validator)
                .build();
    }

    @Test
    void shouldSubmitFeedbackForCurrentStudent() throws Exception {
        given(feedbackService.upsertFeedback(any(), eq(authenticatedStudent()))).willReturn(QaFeedbackResponse.of(
                3L,
                33L,
                9L,
                "needs_improvement",
                List.of("source_irrelevant"),
                "",
                LocalDateTime.of(2026, 5, 18, 10, 0),
                LocalDateTime.of(2026, 5, 18, 10, 0)
        ));

        mockMvc.perform(post(ApiPaths.QA_MESSAGE_FEEDBACK)
                        .requestAttr(AuthConstants.REQUEST_USER_ATTRIBUTE, authenticatedStudent())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "messageId": 33,
                                  "rating": "needs_improvement",
                                  "tags": ["source_irrelevant"]
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.messageId").value(33))
                .andExpect(jsonPath("$.data.rating").value("needs_improvement"))
                .andExpect(jsonPath("$.data.tags[0]").value("source_irrelevant"));
    }

    @Test
    void shouldDeleteOwnFeedback() throws Exception {
        mockMvc.perform(delete(ApiPaths.QA_MESSAGE_FEEDBACK + "/33")
                        .requestAttr(AuthConstants.REQUEST_USER_ATTRIBUTE, authenticatedStudent()))
                .andExpect(status().isOk());

        then(feedbackService).should().deleteFeedback(33L, authenticatedStudent());
    }

    @Test
    void shouldRejectInvalidFeedbackRating() throws Exception {
        mockMvc.perform(post(ApiPaths.QA_MESSAGE_FEEDBACK)
                        .requestAttr(AuthConstants.REQUEST_USER_ATTRIBUTE, authenticatedStudent())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "messageId": 33,
                                  "rating": ""
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(4001));
    }

    private AuthenticatedUser authenticatedStudent() {
        return new AuthenticatedUser(7L, "student.zhouzh", "student.zhouzh", "周同学", List.of("student"), List.of());
    }
}
