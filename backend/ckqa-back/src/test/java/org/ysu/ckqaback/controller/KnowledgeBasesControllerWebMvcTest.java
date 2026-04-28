package org.ysu.ckqaback.controller;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.http.HttpStatus;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.validation.beanvalidation.LocalValidatorFactoryBean;
import org.ysu.ckqaback.api.ApiPaths;
import org.ysu.ckqaback.api.ApiPageData;
import org.ysu.ckqaback.api.ApiResultCode;
import org.ysu.ckqaback.exception.BusinessException;
import org.ysu.ckqaback.exception.GlobalExceptionHandler;
import org.ysu.ckqaback.index.KnowledgeBaseLookupService;
import org.ysu.ckqaback.index.IndexWorkflowService;
import org.ysu.ckqaback.index.dto.IndexRunResponse;
import org.ysu.ckqaback.index.dto.KnowledgeBaseDetailResponse;
import org.ysu.ckqaback.index.dto.KnowledgeBaseQueryRequest;
import org.ysu.ckqaback.index.dto.KnowledgeBaseSummaryResponse;

import java.time.LocalDateTime;
import java.util.List;

import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class KnowledgeBasesControllerWebMvcTest {

    private IndexWorkflowService indexWorkflowService;
    private KnowledgeBaseLookupService knowledgeBaseLookupService;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        indexWorkflowService = Mockito.mock(IndexWorkflowService.class);
        knowledgeBaseLookupService = Mockito.mock(KnowledgeBaseLookupService.class);
        LocalValidatorFactoryBean validator = new LocalValidatorFactoryBean();
        validator.afterPropertiesSet();
        mockMvc = MockMvcBuilders.standaloneSetup(new KnowledgeBasesController(indexWorkflowService, knowledgeBaseLookupService))
                .setControllerAdvice(new GlobalExceptionHandler())
                .setValidator(validator)
                .build();
    }

    @Test
    void shouldListKnowledgeBases() throws Exception {
        given(knowledgeBaseLookupService.listKnowledgeBases(Mockito.any(KnowledgeBaseQueryRequest.class)))
                .willReturn(new ApiPageData<>(List.of(KnowledgeBaseSummaryResponse.builder()
                        .id(5L)
                        .courseId("os")
                        .kbCode("os-main")
                        .name("操作系统主知识库")
                        .status("active")
                        .activeIndexRunId(18L)
                        .description("课程主库")
                        .latestIndexRunId(18L)
                        .latestIndexRunStatus("success")
                        .createdAt(LocalDateTime.of(2026, 4, 28, 9, 0))
                        .updatedAt(LocalDateTime.of(2026, 4, 28, 10, 0))
                        .build()), 1, 20, 1, 1));

        mockMvc.perform(get(ApiPaths.KNOWLEDGE_BASES))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items[0].kbCode").value("os-main"));
    }

    @Test
    void shouldGetKnowledgeBaseDetail() throws Exception {
        given(knowledgeBaseLookupService.getKnowledgeBase(5L)).willReturn(KnowledgeBaseDetailResponse.builder()
                .id(5L)
                .courseId("os")
                .kbCode("os-main")
                .name("操作系统主知识库")
                .status("active")
                .activeIndexRunId(18L)
                .description("课程主库")
                .latestIndexRunId(18L)
                .latestIndexRunStatus("success")
                .createdAt(LocalDateTime.of(2026, 4, 28, 9, 0))
                .updatedAt(LocalDateTime.of(2026, 4, 28, 10, 0))
                .indexRunCount(2L)
                .successIndexRunCount(1L)
                .latestIndexRunMetadata("{\"documents\":12}")
                .build());

        mockMvc.perform(get(ApiPaths.KNOWLEDGE_BASES + "/5"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value(5))
                .andExpect(jsonPath("$.data.activeIndexRunId").value(18));
    }

    @Test
    void shouldReturnKnowledgeBaseNotFoundCode() throws Exception {
        given(knowledgeBaseLookupService.getKnowledgeBase(404L))
                .willThrow(new BusinessException(ApiResultCode.KNOWLEDGE_BASE_NOT_FOUND, HttpStatus.NOT_FOUND));

        mockMvc.perform(get(ApiPaths.KNOWLEDGE_BASES + "/404"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value(ApiResultCode.KNOWLEDGE_BASE_NOT_FOUND.getCode()))
                .andExpect(jsonPath("$.message").value("知识库不存在"));
    }

    @Test
    void shouldValidateKnowledgeBasePagination() throws Exception {
        mockMvc.perform(get(ApiPaths.KNOWLEDGE_BASES).queryParam("page", "0"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(ApiResultCode.VALIDATION_ERROR.getCode()));
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
