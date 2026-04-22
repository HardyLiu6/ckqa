package org.ysu.ckqaback.controller;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.validation.beanvalidation.LocalValidatorFactoryBean;
import org.ysu.ckqaback.api.ApiPaths;
import org.ysu.ckqaback.exception.GlobalExceptionHandler;
import org.ysu.ckqaback.index.IndexWorkflowService;
import org.ysu.ckqaback.index.dto.IndexRunResponse;

import java.util.List;

import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class KnowledgeBasesControllerWebMvcTest {

    private IndexWorkflowService indexWorkflowService;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        indexWorkflowService = Mockito.mock(IndexWorkflowService.class);
        LocalValidatorFactoryBean validator = new LocalValidatorFactoryBean();
        validator.afterPropertiesSet();
        mockMvc = MockMvcBuilders.standaloneSetup(new KnowledgeBasesController(indexWorkflowService))
                .setControllerAdvice(new GlobalExceptionHandler())
                .setValidator(validator)
                .build();
    }

    @Test
    void shouldCreateIndexRun() throws Exception {
        IndexRunResponse response = IndexRunResponse.of(
                18L,
                5L,
                "graphrag",
                "graphrag-20260421153000",
                "running",
                null,
                null,
                "{}"
        );
        given(indexWorkflowService.createIndexRun(5L)).willReturn(response);

        mockMvc.perform(post(ApiPaths.KNOWLEDGE_BASES + "/5/index-runs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value(18))
                .andExpect(jsonPath("$.data.status").value("running"));
    }

    @Test
    void shouldListIndexRuns() throws Exception {
        given(indexWorkflowService.listIndexRuns(5L)).willReturn(List.of(
                IndexRunResponse.of(
                        18L,
                        5L,
                        "graphrag",
                        "graphrag-20260421153000",
                        "success",
                        null,
                        null,
                        "{}"
                )
        ));

        mockMvc.perform(get(ApiPaths.KNOWLEDGE_BASES + "/5/index-runs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].id").value(18))
                .andExpect(jsonPath("$.data[0].status").value("success"));
    }
}
