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
import org.ysu.ckqaback.index.IndexWorkflowService;
import org.ysu.ckqaback.index.KnowledgeBaseBuildRunService;
import org.ysu.ckqaback.index.dto.BuildRunIndexRequest;
import org.ysu.ckqaback.index.dto.BuildRunCustomPromptDraftRequest;
import org.ysu.ckqaback.index.dto.BuildRunDetailResponse;
import org.ysu.ckqaback.index.dto.IndexRunResponse;

import java.time.LocalDateTime;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class KnowledgeBaseBuildRunsControllerWebMvcTest {

    private KnowledgeBaseBuildRunService buildRunService;
    private IndexWorkflowService indexWorkflowService;
    private org.ysu.ckqaback.index.PromptTuneService promptTuneService;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        buildRunService = Mockito.mock(KnowledgeBaseBuildRunService.class);
        indexWorkflowService = Mockito.mock(IndexWorkflowService.class);
        promptTuneService = Mockito.mock(org.ysu.ckqaback.index.PromptTuneService.class);
        LocalValidatorFactoryBean validator = new LocalValidatorFactoryBean();
        validator.afterPropertiesSet();
        mockMvc = MockMvcBuilders.standaloneSetup(new KnowledgeBaseBuildRunsController(buildRunService, indexWorkflowService, promptTuneService))
                .setControllerAdvice(new GlobalExceptionHandler())
                .setValidator(validator)
                .build();
    }

    @Test
    void shouldGetBuildRunDetail() throws Exception {
        given(buildRunService.getBuildRun(18L)).willReturn(BuildRunDetailResponse.builder()
                .id(18L)
                .knowledgeBaseId(5L)
                .courseId("os")
                .buildVersion("kb5-20260505090000000-abcd")
                .status("pending")
                .currentStage("material_selection")
                .workspaceUri("user_7/kb_5/build_18")
                .selectedMaterialIds("[11,12]")
                .createdAt(LocalDateTime.of(2026, 5, 5, 9, 0))
                .updatedAt(LocalDateTime.of(2026, 5, 5, 9, 0))
                .build());

        mockMvc.perform(get(ApiPaths.KNOWLEDGE_BASE_BUILD_RUNS + "/18"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value(18))
                .andExpect(jsonPath("$.data.workspaceUri").value("user_7/kb_5/build_18"));
    }

    @Test
    void shouldRunParseCheck() throws Exception {
        given(buildRunService.checkParse(Mockito.eq(18L), any())).willReturn(BuildRunDetailResponse.builder()
                .id(18L)
                .status("running")
                .currentStage("parse")
                .buildMetadata("{\"parseCheck\":true}")
                .build());

        mockMvc.perform(post(ApiPaths.KNOWLEDGE_BASE_BUILD_RUNS + "/18/parse-check")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "parseMissing": true
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value(18))
                .andExpect(jsonPath("$.data.currentStage").value("parse"));
    }

    @Test
    void shouldArchiveBuildRun() throws Exception {
        given(buildRunService.archiveBuildRun(18L, false)).willReturn(BuildRunDetailResponse.builder()
                .id(18L)
                .status("archived")
                .currentStage("done")
                .build());

        mockMvc.perform(delete(ApiPaths.KNOWLEDGE_BASE_BUILD_RUNS + "/18")
                        .param("keepArtifacts", "true"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value(18))
                .andExpect(jsonPath("$.data.status").value("archived"));
    }

    @Test
    void shouldCreateBuildRunIndexRun() throws Exception {
        given(indexWorkflowService.createBuildRunIndexRun(Mockito.eq(27L), any(BuildRunIndexRequest.class)))
                .willReturn(IndexRunResponse.of(18L, 5L, "graphrag", "graphrag-20260505150000", "success", null, null, "{}"));

        mockMvc.perform(post(ApiPaths.KNOWLEDGE_BASE_BUILD_RUNS + "/27/index-runs")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "activateOnSuccess": true,
                                  "forceRebuild": false
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value(18))
                .andExpect(jsonPath("$.data.status").value("success"));
    }

    @Test
    void shouldRunQaSmoke() throws Exception {
        given(buildRunService.runQaSmoke(Mockito.eq(27L), any()))
                .willReturn(BuildRunDetailResponse.builder()
                        .id(27L)
                        .status("success")
                        .currentStage("qa_smoke")
                        .qaStatus("running")
                        .activeIndexRunId(18L)
                        .build());

        mockMvc.perform(post(ApiPaths.KNOWLEDGE_BASE_BUILD_RUNS + "/27/qa-smoke")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "question": "操作系统讲了什么？",
                                  "mode": "basic"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value(27))
                .andExpect(jsonPath("$.data.currentStage").value("qa_smoke"))
                .andExpect(jsonPath("$.data.qaStatus").value("running"));
    }

    @Test
    void putCustomPromptDraft_returnsOkAndBuildRunDetail() throws Exception {
        given(buildRunService.saveCustomPromptDraft(eq(1L), any(BuildRunCustomPromptDraftRequest.class)))
                .willReturn(BuildRunDetailResponse.builder()
                        .id(1L)
                        .status("running")
                        .currentStage("prompt")
                        .build());

        String body = """
                {"seed":"system_default","prompts":{"extract_graph":{"content":"x"}}}
                """;

        mockMvc.perform(put(ApiPaths.KNOWLEDGE_BASE_BUILD_RUNS + "/1/custom-prompt-draft")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));
    }

    @Test
    void putCustomPromptDraft_rejectsMissingSeed() throws Exception {
        String body = """
                {"prompts":{"extract_graph":{"content":"x"}}}
                """;

        mockMvc.perform(put(ApiPaths.KNOWLEDGE_BASE_BUILD_RUNS + "/1/custom-prompt-draft")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest());
    }
}
