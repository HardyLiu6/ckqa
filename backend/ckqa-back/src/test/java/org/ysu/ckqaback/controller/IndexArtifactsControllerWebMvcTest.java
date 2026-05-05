package org.ysu.ckqaback.controller;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.validation.beanvalidation.LocalValidatorFactoryBean;
import org.ysu.ckqaback.api.ApiPaths;
import org.ysu.ckqaback.entity.IndexArtifacts;
import org.ysu.ckqaback.exception.GlobalExceptionHandler;
import org.ysu.ckqaback.service.IndexArtifactsService;

import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class IndexArtifactsControllerWebMvcTest {

    private IndexArtifactsService indexArtifactsService;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        indexArtifactsService = Mockito.mock(IndexArtifactsService.class);
        LocalValidatorFactoryBean validator = new LocalValidatorFactoryBean();
        validator.afterPropertiesSet();
        mockMvc = MockMvcBuilders.standaloneSetup(new IndexArtifactsController(indexArtifactsService))
                .setControllerAdvice(new GlobalExceptionHandler())
                .setValidator(validator)
                .build();
    }

    @Test
    void shouldGetIndexArtifact() throws Exception {
        given(indexArtifactsService.getRequiredById(31L)).willReturn(artifact("ready"));

        mockMvc.perform(get(ApiPaths.INDEX_ARTIFACTS + "/31"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value(31))
                .andExpect(jsonPath("$.data.artifactType").value("lancedb"));
    }

    @Test
    void shouldMarkIndexArtifactDeleted() throws Exception {
        given(indexArtifactsService.markDeleted(31L)).willReturn(artifact("deleted"));

        mockMvc.perform(delete(ApiPaths.INDEX_ARTIFACTS + "/31"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.artifactStatus").value("deleted"));
    }

    private IndexArtifacts artifact(String status) {
        IndexArtifacts artifact = new IndexArtifacts();
        artifact.setId(31L);
        artifact.setIndexRunId(18L);
        artifact.setArtifactType("lancedb");
        artifact.setDisplayName("lancedb");
        artifact.setStorageUri("user_2/kb_5/build_27/index/output/lancedb");
        artifact.setStorageScope("local");
        artifact.setArtifactStatus(status);
        artifact.setFileSize(0L);
        return artifact;
    }
}
