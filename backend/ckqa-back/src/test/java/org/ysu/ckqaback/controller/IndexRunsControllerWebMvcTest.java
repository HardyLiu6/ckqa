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
import org.ysu.ckqaback.index.dto.IndexArtifactResponse;
import org.ysu.ckqaback.index.dto.IndexRunResponse;
import org.ysu.ckqaback.service.IndexArtifactsService;

import java.util.List;

import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class IndexRunsControllerWebMvcTest {

    private IndexWorkflowService indexWorkflowService;
    private IndexArtifactsService indexArtifactsService;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        indexWorkflowService = Mockito.mock(IndexWorkflowService.class);
        indexArtifactsService = Mockito.mock(IndexArtifactsService.class);
        LocalValidatorFactoryBean validator = new LocalValidatorFactoryBean();
        validator.afterPropertiesSet();
        mockMvc = MockMvcBuilders.standaloneSetup(new IndexRunsController(indexWorkflowService, indexArtifactsService))
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

    @Test
    void shouldListIndexRunArtifacts() throws Exception {
        org.ysu.ckqaback.entity.IndexArtifacts artifact = new org.ysu.ckqaback.entity.IndexArtifacts();
        artifact.setId(31L);
        artifact.setIndexRunId(18L);
        artifact.setArtifactType("lancedb");
        artifact.setStorageUri("user_2/kb_5/build_27/index/output/lancedb");
        artifact.setArtifactStatus("ready");
        given(indexArtifactsService.listByIndexRunId(18L)).willReturn(List.of(artifact));

        mockMvc.perform(get(ApiPaths.INDEX_RUNS + "/18/artifacts"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].id").value(31))
                .andExpect(jsonPath("$.data[0].artifactType").value("lancedb"))
                .andExpect(jsonPath("$.data[0].artifactStatus").value("ready"));
    }
}
