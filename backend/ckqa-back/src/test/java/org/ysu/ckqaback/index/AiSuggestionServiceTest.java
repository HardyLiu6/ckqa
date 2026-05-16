package org.ysu.ckqaback.index;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.ysu.ckqaback.api.ApiResultCode;
import org.ysu.ckqaback.entity.KnowledgeBaseBuildRuns;
import org.ysu.ckqaback.entity.PromptTuneAuditSamples;
import org.ysu.ckqaback.exception.BusinessException;
import org.ysu.ckqaback.index.dto.AiSuggestionResponse;
import org.ysu.ckqaback.integration.config.CkqaIntegrationProperties;
import org.ysu.ckqaback.service.KnowledgeBaseBuildRunsService;
import org.ysu.ckqaback.service.PromptTuneAuditSamplesService;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AiSuggestionServiceTest {

    private PromptTuneAuditSamplesService samplesStore;
    private KnowledgeBaseBuildRunsService buildRunsStore;
    private SingleSampleExtractionOrchestrator orchestrator;
    private BuildRunWorkspaceService workspaceService;
    private AiSuggestionService service;

    @BeforeEach
    void setUp() {
        samplesStore = mock(PromptTuneAuditSamplesService.class);
        buildRunsStore = mock(KnowledgeBaseBuildRunsService.class);
        orchestrator = mock(SingleSampleExtractionOrchestrator.class);
        workspaceService = mock(BuildRunWorkspaceService.class);

        // properties mock：AiSuggestionService.resolvePromptFile 需要从 graphrag.root 拼路径
        CkqaIntegrationProperties properties = mock(CkqaIntegrationProperties.class);
        CkqaIntegrationProperties.GraphRagProperties graphrag = mock(CkqaIntegrationProperties.GraphRagProperties.class);
        when(properties.getGraphrag()).thenReturn(graphrag);
        when(graphrag.getRoot()).thenReturn("/tmp/graphrag-test");

        service = new AiSuggestionService(
                samplesStore, buildRunsStore, orchestrator, workspaceService, properties, new ObjectMapper()
        );
    }

    @Test
    void rejectsWhenSampleNotFound() {
        when(samplesStore.getById(99L)).thenReturn(null);

        assertThatThrownBy(() -> service.generate(10L, 99L))
                .isInstanceOf(BusinessException.class)
                .extracting("code")
                .isEqualTo(ApiResultCode.AUDIT_SAMPLE_NOT_FOUND.getCode());
    }

    @Test
    void rejectsWhenBuildRunIdMismatches() {
        PromptTuneAuditSamples sample = newSample(99L, /*buildRunId*/ 5L);
        when(samplesStore.getById(99L)).thenReturn(sample);

        assertThatThrownBy(() -> service.generate(/*pathBuildRunId*/ 10L, 99L))
                .isInstanceOf(BusinessException.class)
                .extracting("code")
                .isEqualTo(ApiResultCode.AUDIT_SAMPLE_NOT_FOUND.getCode());
    }

    @Test
    void marksAllEntitiesWithAiSuggestedSource() throws Exception {
        PromptTuneAuditSamples sample = newSample(99L, 10L);
        when(samplesStore.getById(99L)).thenReturn(sample);
        KnowledgeBaseBuildRuns buildRun = newBuildRun(10L);
        when(buildRunsStore.getRequiredById(10L)).thenReturn(buildRun);
        when(workspaceService.resolve(any())).thenReturn(java.nio.file.Path.of("/tmp/ws"));

        SingleSampleExtractionOrchestrator.ExtractionResult result =
                SingleSampleExtractionOrchestrator.ExtractionResult.builder()
                        .entities(List.of(
                                Map.of("name", "进程", "type", "Concept", "confidence", 0.92),
                                Map.of("name", "线程", "type", "Concept", "confidence", 0.78)
                        ))
                        .relations(List.of(
                                Map.of("source", "进程", "target", "线程", "type", "contains", "confidence", 0.65)
                        ))
                        .build();
        when(orchestrator.runSingleExtract(any(), any(), any(), any(), any())).thenReturn(result);

        AiSuggestionResponse response = service.generate(10L, 99L);

        assertThat(response.getEntities()).hasSize(2);
        // suggestionSource 是新增字段；name/type 等领域字段透传不变
        assertThat(response.getEntities()).allSatisfy(e -> {
            assertThat(e).containsEntry("suggestionSource", "ai_suggested");
            assertThat(e).containsKey("name");
            assertThat(e).containsKey("type");
        });
        assertThat(response.getRelations()).hasSize(1);
        assertThat(response.getRelations().get(0))
                .containsEntry("suggestionSource", "ai_suggested");
    }

    @Test
    void mapsRelationFieldsToFrontendShape() throws Exception {
        PromptTuneAuditSamples sample = newSample(99L, 10L);
        when(samplesStore.getById(99L)).thenReturn(sample);
        when(buildRunsStore.getRequiredById(10L)).thenReturn(newBuildRun(10L));
        when(workspaceService.resolve(any())).thenReturn(java.nio.file.Path.of("/tmp/ws"));

        // GraphRAG 输出的 relation 字段是 source/target（实体名字符串）
        // service 把这两个字段移到 originalSource/originalTarget，并加 suggestionSource 标记
        SingleSampleExtractionOrchestrator.ExtractionResult result =
                SingleSampleExtractionOrchestrator.ExtractionResult.builder()
                        .entities(List.of())
                        .relations(List.of(
                                Map.of("source", "进程", "target", "线程",
                                       "type", "contains", "description", "结构包含",
                                       "confidence", 0.7)
                        ))
                        .build();
        when(orchestrator.runSingleExtract(any(), any(), any(), any(), any())).thenReturn(result);

        AiSuggestionResponse response = service.generate(10L, 99L);

        Map<String, Object> rel = response.getRelations().get(0);
        // 关系领域字段保持干净：source/target 已被移除（前端的 source 字段语义专留给"关系起点 id"）
        assertThat(rel).doesNotContainKey("source");
        assertThat(rel).doesNotContainKey("target");
        // 原 source/target 改名保留
        assertThat(rel).containsEntry("originalSource", "进程");
        assertThat(rel).containsEntry("originalTarget", "线程");
        // 候选来源用独立字段标记
        assertThat(rel).containsEntry("suggestionSource", "ai_suggested");
        // 其他字段透传
        assertThat(rel).containsEntry("type", "contains");
        assertThat(rel).containsEntry("description", "结构包含");
    }

    private PromptTuneAuditSamples newSample(Long id, Long buildRunId) {
        PromptTuneAuditSamples e = new PromptTuneAuditSamples();
        e.setId(id);
        e.setBuildRunId(buildRunId);
        e.setKnowledgeBaseId(100L);
        e.setSourceSampleId("sample-x");
        e.setText("进程是程序的一次执行过程。");
        e.setHeadingPath("第二章 > 2.1");
        e.setPageStart(34);
        e.setPageEnd(34);
        e.setAuditPriority("high");
        e.setReviewerDecision("pending");
        return e;
    }

    private KnowledgeBaseBuildRuns newBuildRun(Long id) {
        KnowledgeBaseBuildRuns b = new KnowledgeBaseBuildRuns();
        b.setId(id);
        b.setKnowledgeBaseId(100L);
        b.setWorkspaceUri("user_1/kb_100/build_" + id);
        return b;
    }
}
