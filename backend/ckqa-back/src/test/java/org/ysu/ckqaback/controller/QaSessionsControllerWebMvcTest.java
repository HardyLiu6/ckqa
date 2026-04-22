package org.ysu.ckqaback.controller;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.validation.beanvalidation.LocalValidatorFactoryBean;
import org.ysu.ckqaback.api.ApiPaths;
import org.ysu.ckqaback.exception.GlobalExceptionHandler;
import org.ysu.ckqaback.qa.QaWorkflowService;
import org.ysu.ckqaback.qa.dto.QaMessageResponse;
import org.ysu.ckqaback.qa.dto.QaRoundResponse;
import org.ysu.ckqaback.qa.dto.QaSessionResponse;

import java.time.LocalDateTime;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class QaSessionsControllerWebMvcTest {

    private QaWorkflowService qaWorkflowService;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        qaWorkflowService = Mockito.mock(QaWorkflowService.class);
        LocalValidatorFactoryBean validator = new LocalValidatorFactoryBean();
        validator.afterPropertiesSet();
        mockMvc = MockMvcBuilders.standaloneSetup(new QaSessionsController(qaWorkflowService))
                .setControllerAdvice(new GlobalExceptionHandler())
                .setValidator(validator)
                .build();
    }

    @Test
    void shouldCreateSession() throws Exception {
        QaSessionResponse response = QaSessionResponse.of(
                5L,
                "qa-0001",
                7L,
                "os",
                3L,
                "操作系统问答",
                "active",
                null,
                LocalDateTime.of(2026, 4, 21, 12, 0)
        );
        given(qaWorkflowService.createSession(any())).willReturn(response);

        mockMvc.perform(post(ApiPaths.QA_SESSIONS)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "userId": 7,
                                  "courseId": "os",
                                  "knowledgeBaseId": 3,
                                  "title": "操作系统问答"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value(5))
                .andExpect(jsonPath("$.data.status").value("active"));
    }

    @Test
    void shouldSendMessage() throws Exception {
        QaRoundResponse response = QaRoundResponse.of(
                QaMessageResponse.of(11L, 5L, "user", 1, "什么是线程", LocalDateTime.of(2026, 4, 21, 12, 1)),
                QaMessageResponse.of(12L, 5L, "assistant", 2, "线程是调度的基本单位", LocalDateTime.of(2026, 4, 21, 12, 1, 5)),
                "success"
        );
        given(qaWorkflowService.sendMessage(eq(5L), any())).willReturn(response);

        mockMvc.perform(post(ApiPaths.QA_SESSIONS + "/5/messages")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "mode": "local",
                                  "content": "什么是线程"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.retrievalStatus").value("success"))
                .andExpect(jsonPath("$.data.assistantMessage.content").value("线程是调度的基本单位"));
    }
}
