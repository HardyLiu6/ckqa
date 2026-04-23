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

import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class IndexRunsControllerWebMvcTest {

    private IndexWorkflowService indexWorkflowService;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        indexWorkflowService = Mockito.mock(IndexWorkflowService.class);
        LocalValidatorFactoryBean validator = new LocalValidatorFactoryBean();
        validator.afterPropertiesSet();
        mockMvc = MockMvcBuilders.standaloneSetup(new IndexRunsController(indexWorkflowService))
                .setControllerAdvice(new GlobalExceptionHandler())
                .setValidator(validator)
                .build();
    }

    @Test
    void shouldReturnIndexRunDetail() throws Exception {
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
        given(indexWorkflowService.getIndexRun(18L)).willReturn(response);

        mockMvc.perform(get(ApiPaths.INDEX_RUNS + "/18"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value(18))
                .andExpect(jsonPath("$.data.status").value("running"));
    }
}
