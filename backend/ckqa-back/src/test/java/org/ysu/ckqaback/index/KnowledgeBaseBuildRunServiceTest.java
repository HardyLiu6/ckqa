package org.ysu.ckqaback.index;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.ArgumentCaptor;
import org.ysu.ckqaback.api.ApiResultCode;
import org.ysu.ckqaback.entity.CourseMaterials;
import org.ysu.ckqaback.entity.KnowledgeBaseBuildRuns;
import org.ysu.ckqaback.entity.KnowledgeBases;
import org.ysu.ckqaback.entity.IndexRuns;
import org.ysu.ckqaback.exception.BusinessException;
import org.ysu.ckqaback.index.dto.BuildRunCreateRequest;
import org.ysu.ckqaback.index.dto.BuildRunCustomPromptDraftRequest;
import org.ysu.ckqaback.index.dto.BuildRunGraphInputRequest;
import org.ysu.ckqaback.index.dto.BuildRunMaterialSelectionRequest;
import org.ysu.ckqaback.index.dto.BuildRunQaSmokeRequest;
import org.ysu.ckqaback.integration.config.CkqaIntegrationProperties;
import org.ysu.ckqaback.qa.QaWorkflowService;
import org.ysu.ckqaback.qa.dto.ContextSizeEstimateResponse;
import org.ysu.ckqaback.qa.dto.CreateQaMessageRequest;
import org.ysu.ckqaback.qa.dto.CreateQaSessionRequest;
import org.ysu.ckqaback.qa.dto.QaMessageResponse;
import org.ysu.ckqaback.qa.dto.QaSessionResponse;
import org.ysu.ckqaback.qa.dto.QaTaskDetailResponse;
import org.ysu.ckqaback.qa.dto.QaTaskSubmissionResponse;
import org.ysu.ckqaback.service.CourseMaterialsService;
import org.ysu.ckqaback.service.IndexRunsService;
import org.ysu.ckqaback.service.KnowledgeBaseBuildRunsService;
import org.ysu.ckqaback.service.KnowledgeBasesService;
import org.ysu.ckqaback.service.ParseResultsService;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class KnowledgeBaseBuildRunServiceTest {

    @TempDir
    Path tempDir;

    private KnowledgeBasesService knowledgeBasesService;
    private KnowledgeBaseBuildRunsService buildRunsStore;
    private BuildRunWorkspaceService workspaceService;
    private CkqaIntegrationProperties properties;
    private QaWorkflowService qaWorkflowService;
    private IndexRunsService indexRunsService;
    private CourseMaterialsService courseMaterialsService;
    private ParseResultsService parseResultsService;
    private IndexProgressParser indexProgressParser;
    private KnowledgeBaseBuildRunService service;

    @BeforeEach
    void setUp() {
        knowledgeBasesService = mock(KnowledgeBasesService.class);
        buildRunsStore = mock(KnowledgeBaseBuildRunsService.class);
        workspaceService = new BuildRunWorkspaceService(tempDir.toString());
        properties = new CkqaIntegrationProperties();
        properties.getGraphrag().setBuildRunsRoot(tempDir.toString());
        qaWorkflowService = mock(QaWorkflowService.class);
        indexRunsService = mock(IndexRunsService.class);
        courseMaterialsService = mock(CourseMaterialsService.class);
        parseResultsService = mock(ParseResultsService.class);
        indexProgressParser = new IndexProgressParser();
        service = new KnowledgeBaseBuildRunService(
                knowledgeBasesService,
                buildRunsStore,
                workspaceService,
                properties,
                qaWorkflowService,
                indexRunsService,
                courseMaterialsService,
                parseResultsService,
                indexProgressParser
        );
    }

    @Test
    void shouldBuildWorkspaceUriAndRejectTraversal() throws Exception {
        String uri = workspaceService.workspaceUri(null, 5L, 18L);

        assertThat(uri).isEqualTo("user_0/kb_5/build_18");
        assertThat(workspaceService.resolve(uri)).isEqualTo(tempDir.resolve(uri).normalize());
        assertThatThrownBy(() -> workspaceService.resolve("../escape"))
                .isInstanceOf(BusinessException.class)
                .hasMessage("构建工作区路径非法")
                .extracting("code")
                .isEqualTo(ApiResultCode.BAD_REQUEST.getCode());
    }

    @Test
    void shouldCreateBuildRunWorkspaceAndSelectedMaterialsFile() throws Exception {
        KnowledgeBases knowledgeBase = knowledgeBase();
        when(knowledgeBasesService.getRequiredById(5L)).thenReturn(knowledgeBase);
        when(buildRunsStore.save(any(KnowledgeBaseBuildRuns.class))).thenAnswer(invocation -> {
            KnowledgeBaseBuildRuns buildRun = invocation.getArgument(0);
            buildRun.setId(18L);
            buildRun.setCreatedAt(LocalDateTime.of(2026, 5, 5, 9, 0));
            buildRun.setUpdatedAt(LocalDateTime.of(2026, 5, 5, 9, 0));
            return true;
        });
        when(buildRunsStore.updateById(any(KnowledgeBaseBuildRuns.class))).thenReturn(true);
        when(buildRunsStore.getRequiredById(18L)).thenAnswer(invocation -> {
            KnowledgeBaseBuildRuns refreshed = new KnowledgeBaseBuildRuns();
            refreshed.setId(18L);
            refreshed.setKnowledgeBaseId(5L);
            refreshed.setCourseId("os");
            refreshed.setRequestedByUserId(7L);
            refreshed.setBuildVersion("kb5-20260505090000000-abcd");
            refreshed.setStatus("pending");
            refreshed.setCurrentStage("material_selection");
            refreshed.setQaStatus("skipped");
            refreshed.setActivationPolicy("index_success");
            refreshed.setSelectedMaterialIds("[11,12]");
            refreshed.setWorkspaceUri("user_7/kb_5/build_18");
            refreshed.setCreatedAt(LocalDateTime.of(2026, 5, 5, 9, 0));
            refreshed.setUpdatedAt(LocalDateTime.of(2026, 5, 5, 9, 0));
            return refreshed;
        });

        BuildRunCreateRequest request = new BuildRunCreateRequest();
        request.setRequestedByUserId(7L);
        request.setMaterialIds(List.of(11L, 12L));
        var response = service.createBuildRun(5L, request);

        assertThat(response.getId()).isEqualTo(18L);
        assertThat(response.getWorkspaceUri()).isEqualTo("user_7/kb_5/build_18");
        assertThat(response.getBuildVersion()).startsWith("kb5-");
        assertThat(response.getCurrentStage()).isEqualTo("material_selection");
        assertThat(response.getQaStatus()).isEqualTo("skipped");
        ArgumentCaptor<KnowledgeBaseBuildRuns> savedCaptor = ArgumentCaptor.forClass(KnowledgeBaseBuildRuns.class);
        verify(buildRunsStore).save(savedCaptor.capture());
        assertThat(savedCaptor.getValue().getBuildVersion()).matches("kb5-\\d{17}-[0-9a-f]{4}");
        assertThat(savedCaptor.getValue().getCurrentStage()).isEqualTo("material_selection");
        assertThat(savedCaptor.getValue().getQaStatus()).isEqualTo("skipped");
        assertThat(savedCaptor.getValue().getActivationPolicy()).isEqualTo("index_success");
        Path workspace = tempDir.resolve("user_7/kb_5/build_18");
        assertThat(Files.isDirectory(workspace.resolve("index/output"))).isTrue();
        assertThat(Files.readString(workspace.resolve("selection/selected_materials.json"))).contains("11", "12");
        verify(buildRunsStore).updateById(any(KnowledgeBaseBuildRuns.class));
    }

    @Test
    void shouldRejectCreateWhenConcurrencyDisabledAndBuildRunActive() {
        properties.getGraphrag().setConcurrentBuildsEnabled(false);
        when(knowledgeBasesService.getRequiredById(5L)).thenReturn(knowledgeBase());
        when(buildRunsStore.findActivePendingOrRunning(5L)).thenReturn(Optional.of(new KnowledgeBaseBuildRuns()));

        BuildRunCreateRequest request = new BuildRunCreateRequest();
        request.setMaterialIds(List.of(11L));

        assertThatThrownBy(() -> service.createBuildRun(5L, request))
                .isInstanceOf(BusinessException.class)
                .hasMessage("当前知识库已有构建流水线未完成")
                .extracting("code")
                .isEqualTo(ApiResultCode.KNOWLEDGE_BASE_BUILD_RUN_ALREADY_RUNNING.getCode());
        verify(buildRunsStore, never()).save(any());
    }

    @Test
    void shouldRejectMaterialSelectionWhenMaterialBelongsToOtherCourse() {
        KnowledgeBaseBuildRuns buildRun = buildRunWithoutActiveIndex();
        when(buildRunsStore.getRequiredById(27L)).thenReturn(buildRun);
        when(courseMaterialsService.getRequiredById(88L)).thenReturn(courseMaterial(88L, "ds"));

        BuildRunMaterialSelectionRequest request = new BuildRunMaterialSelectionRequest();
        request.setMaterialIds(List.of(88L));

        assertThatThrownBy(() -> service.updateMaterialSelection(27L, request))
                .isInstanceOf(BusinessException.class)
                .hasMessage("资料不属于当前知识库课程")
                .extracting("code")
                .isEqualTo(ApiResultCode.BAD_REQUEST.getCode());
        verify(buildRunsStore, never()).updateById(any());
    }

    @Test
    void shouldRejectGraphInputConfirmationWhenExportArtifactsMissing() {
        KnowledgeBaseBuildRuns buildRun = buildRunWithoutActiveIndex();
        buildRun.setSelectedMaterialIds("[31,32]");
        when(buildRunsStore.getRequiredById(27L)).thenReturn(buildRun);
        when(courseMaterialsService.getRequiredById(31L)).thenReturn(courseMaterial(31L, "os"));
        when(courseMaterialsService.getRequiredById(32L)).thenReturn(courseMaterial(32L, "os"));
        when(parseResultsService.hasCompleteGraphRagExport(31L, "section", true)).thenReturn(true);
        when(parseResultsService.hasCompleteGraphRagExport(32L, "section", true)).thenReturn(false);

        BuildRunGraphInputRequest request = new BuildRunGraphInputRequest();
        request.setJsonFile("section_docs.json");
        request.setExportMissing(false);

        assertThatThrownBy(() -> service.syncGraphInput(27L, request))
                .isInstanceOf(BusinessException.class)
                .hasMessage("图谱输入产物缺失，请先生成缺失图谱输入")
                .extracting("code")
                .isEqualTo(ApiResultCode.BAD_REQUEST.getCode());
        verify(buildRunsStore, never()).updateById(any());
    }

    @Test
    void shouldConfirmGraphInputWhenSelectedMaterialExportsAreComplete() {
        KnowledgeBaseBuildRuns buildRun = buildRunWithoutActiveIndex();
        buildRun.setSelectedMaterialIds("[31]");
        when(buildRunsStore.getRequiredById(27L)).thenReturn(buildRun);
        when(buildRunsStore.updateById(any(KnowledgeBaseBuildRuns.class))).thenReturn(true);
        when(courseMaterialsService.getRequiredById(31L)).thenReturn(courseMaterial(31L, "os"));
        when(parseResultsService.hasCompleteGraphRagExport(31L, "section", true)).thenReturn(true);

        BuildRunGraphInputRequest request = new BuildRunGraphInputRequest();
        request.setJsonFile("section_docs.json");
        request.setExportMissing(false);

        service.syncGraphInput(27L, request);

        assertThat(buildRun.getCurrentStage()).isEqualTo("graph_input_export");
        assertThat(buildRun.getBuildMetadata()).contains("\"exportMissing\":false");
        verify(buildRunsStore).updateById(buildRun);
    }

    @Test
    void shouldSubmitQaSmokeAndPersistRequestSnapshot() throws Exception {
        KnowledgeBaseBuildRuns buildRun = new KnowledgeBaseBuildRuns();
        buildRun.setId(27L);
        buildRun.setKnowledgeBaseId(5L);
        buildRun.setCourseId("os");
        buildRun.setRequestedByUserId(7L);
        buildRun.setStatus("success");
        buildRun.setCurrentStage("done");
        buildRun.setQaStatus("skipped");
        buildRun.setActiveIndexRunId(18L);
        buildRun.setWorkspaceUri("user_7/kb_5/build_27");
        workspaceService.createLayout(buildRun.getWorkspaceUri());

        when(buildRunsStore.getRequiredById(27L)).thenReturn(buildRun);
        when(buildRunsStore.updateById(any(KnowledgeBaseBuildRuns.class))).thenReturn(true);
        when(qaWorkflowService.createSession(any(CreateQaSessionRequest.class)))
                .thenReturn(QaSessionResponse.of(300L, "qa-smoke", 7L, "os", 5L, "smoke", "知识库构建冒烟验证", "active", null, LocalDateTime.now()));
        when(qaWorkflowService.sendMessage(any(Long.class), any(CreateQaMessageRequest.class), any(Long.class)))
                .thenReturn(QaTaskSubmissionResponse.of(
                        QaMessageResponse.of(400L, 300L, "user", 1, "操作系统讲了什么？", LocalDateTime.now(), "pending", "queued"),
                        9001L,
                        "pending",
                        "queued",
                        null,
                        LocalDateTime.now(),
                        "basic",
                        10L,
                        300L,
                        "basic 模式任务心跳超过 300 秒未更新后会被标记为 stale。"
                ));

        BuildRunQaSmokeRequest request = new BuildRunQaSmokeRequest();
        request.setQuestion("操作系统讲了什么？");
        request.setMode("basic");

        service.runQaSmoke(27L, request);

        Path requestFile = tempDir.resolve("user_7/kb_5/build_27/qa-smoke/request.json");
        assertThat(Files.readString(requestFile)).contains("操作系统讲了什么？", "\"activeIndexRunId\":18");
        verify(qaWorkflowService).createSession(any(CreateQaSessionRequest.class));
        verify(qaWorkflowService).sendMessage(any(Long.class), any(CreateQaMessageRequest.class), any(Long.class));
        verify(buildRunsStore, atLeastOnce()).updateById(any(KnowledgeBaseBuildRuns.class));
    }

    @Test
    void shouldFallbackToLatestSuccessIndexRunForQaSmoke() throws Exception {
        KnowledgeBaseBuildRuns buildRun = buildRunWithoutActiveIndex();
        workspaceService.createLayout(buildRun.getWorkspaceUri());
        IndexRuns older = successIndexRun(18L, 27L, LocalDateTime.of(2026, 5, 5, 9, 0));
        IndexRuns latest = successIndexRun(19L, 27L, LocalDateTime.of(2026, 5, 5, 10, 0));

        when(buildRunsStore.getRequiredById(27L)).thenReturn(buildRun);
        when(indexRunsService.listByKnowledgeBaseId(5L)).thenReturn(List.of(older, latest));
        when(qaWorkflowService.createSession(any(CreateQaSessionRequest.class)))
                .thenReturn(QaSessionResponse.of(300L, "qa-smoke", 7L, "os", 5L, "smoke", "知识库构建冒烟验证", "active", null, LocalDateTime.now()));
        when(qaWorkflowService.sendMessage(any(Long.class), any(CreateQaMessageRequest.class), any(Long.class)))
                .thenReturn(QaTaskSubmissionResponse.of(
                        QaMessageResponse.of(400L, 300L, "user", 1, "问题", LocalDateTime.now(), "pending", "queued"),
                        9001L,
                        "pending",
                        "queued",
                        null,
                        LocalDateTime.now(),
                        "basic",
                        10L,
                        300L,
                        "timeout"
                ));

        service.runQaSmoke(27L, new BuildRunQaSmokeRequest());

        verify(qaWorkflowService).sendMessage(any(Long.class), any(CreateQaMessageRequest.class), org.mockito.Mockito.eq(19L));
        assertThat(Files.readString(tempDir.resolve("user_7/kb_5/build_27/qa-smoke/request.json")))
                .contains("\"activeIndexRunId\":19");
    }

    @Test
    void shouldWriteQaSmokeResponseAndMarkDoneWhenTaskIsTerminal() throws Exception {
        KnowledgeBaseBuildRuns buildRun = buildRunWithActiveIndex();
        workspaceService.createLayout(buildRun.getWorkspaceUri());
        when(buildRunsStore.getRequiredById(27L)).thenReturn(buildRun);
        when(qaWorkflowService.createSession(any(CreateQaSessionRequest.class)))
                .thenReturn(QaSessionResponse.of(300L, "qa-smoke", 7L, "os", 5L, "smoke", "知识库构建冒烟验证", "active", null, LocalDateTime.now()));
        when(qaWorkflowService.sendMessage(any(Long.class), any(CreateQaMessageRequest.class), any(Long.class)))
                .thenReturn(QaTaskSubmissionResponse.of(
                        QaMessageResponse.of(400L, 300L, "user", 1, "问题", LocalDateTime.now(), "success", "done"),
                        9001L,
                        "success",
                        "done",
                        null,
                        LocalDateTime.now(),
                        "basic",
                        10L,
                        300L,
                        "timeout"
                ));
        when(qaWorkflowService.getTaskDetail(300L, 9001L)).thenReturn(QaTaskDetailResponse.of(
                9001L,
                400L,
                401L,
                "success",
                "done",
                "success",
                "basic",
                "smart",
                "basic",
                "问题",
                List.of("done"),
                List.of(),
                LocalDateTime.now(),
                LocalDateTime.now(),
                LocalDateTime.now(),
                QaMessageResponse.of(401L, 300L, "assistant", 2, "回答", LocalDateTime.now(), null, null),
                null,
                10L,
                300L,
                "timeout",
                false,
                "none",
                ContextSizeEstimateResponse.of(0),
                false,
                "none",
                null,
                0,
                0,
                null,
                0L
        ));

        service.runQaSmoke(27L, new BuildRunQaSmokeRequest());

        assertThat(buildRun.getCurrentStage()).isEqualTo("done");
        assertThat(buildRun.getQaStatus()).isEqualTo("success");
        assertThat(Files.readString(tempDir.resolve("user_7/kb_5/build_27/qa-smoke/response.json")))
                .contains("\"taskStatus\":\"success\"", "\"requestedMode\":\"smart\"", "\"resolvedMode\":\"basic\"", "回答");
        com.fasterxml.jackson.databind.JsonNode metadata =
                new com.fasterxml.jackson.databind.ObjectMapper().readTree(buildRun.getBuildMetadata());
        assertThat(metadata.get("requestedMode").asText()).isEqualTo("smart");
        assertThat(metadata.get("resolvedMode").asText()).isEqualTo("basic");
    }

    private KnowledgeBases knowledgeBase() {
        KnowledgeBases knowledgeBase = new KnowledgeBases();
        knowledgeBase.setId(5L);
        knowledgeBase.setCourseId("os");
        knowledgeBase.setKbCode("os-main");
        knowledgeBase.setName("操作系统主知识库");
        return knowledgeBase;
    }

    private CourseMaterials courseMaterial(Long id, String courseId) {
        CourseMaterials material = new CourseMaterials();
        material.setId(id);
        material.setCourseId(courseId);
        material.setDisplayName("资料 " + id);
        material.setParseStatus("done");
        return material;
    }

    private KnowledgeBaseBuildRuns buildRunWithActiveIndex() {
        KnowledgeBaseBuildRuns buildRun = buildRunWithoutActiveIndex();
        buildRun.setActiveIndexRunId(18L);
        return buildRun;
    }

    private KnowledgeBaseBuildRuns buildRunWithoutActiveIndex() {
        KnowledgeBaseBuildRuns buildRun = new KnowledgeBaseBuildRuns();
        buildRun.setId(27L);
        buildRun.setKnowledgeBaseId(5L);
        buildRun.setCourseId("os");
        buildRun.setRequestedByUserId(7L);
        buildRun.setStatus("success");
        buildRun.setCurrentStage("done");
        buildRun.setQaStatus("skipped");
        buildRun.setWorkspaceUri("user_7/kb_5/build_27");
        return buildRun;
    }

    @Test
    void mergeStageMetadata_preservesPersistKeysAcrossStages() throws Exception {
        KnowledgeBaseBuildRuns existing = new KnowledgeBaseBuildRuns();
        existing.setBuildMetadata("{\"stage\":\"prompt\",\"promptConfirmed\":true,\"promptStrategy\":\"custom_pipeline\",\"customPromptDraft\":{\"seed\":\"graphrag_tuned\"}}");

        String merged = service.mergeStageMetadata(
                existing,
                "index_build",
                java.util.Map.of("indexRunId", 99L),
                java.util.List.of("customPromptDraft", "promptStrategy", "promptConfirmed")
        );

        com.fasterxml.jackson.databind.JsonNode node = new com.fasterxml.jackson.databind.ObjectMapper().readTree(merged);
        assertThat(node.get("stage").asText()).isEqualTo("index_build");
        assertThat(node.get("indexRunId").asLong()).isEqualTo(99L);
        assertThat(node.get("promptConfirmed").asBoolean()).isTrue();
        assertThat(node.get("promptStrategy").asText()).isEqualTo("custom_pipeline");
        assertThat(node.get("customPromptDraft").get("seed").asText()).isEqualTo("graphrag_tuned");
    }

    @Test
    void mergeStageMetadata_emptyExistingMetadataYieldsOnlyStageAndExtras() throws Exception {
        KnowledgeBaseBuildRuns empty = new KnowledgeBaseBuildRuns();
        empty.setBuildMetadata(null);

        String merged = service.mergeStageMetadata(
                empty, "prompt", java.util.Map.of("foo", "bar"), java.util.List.of("customPromptDraft")
        );

        com.fasterxml.jackson.databind.JsonNode node = new com.fasterxml.jackson.databind.ObjectMapper().readTree(merged);
        assertThat(node.get("stage").asText()).isEqualTo("prompt");
        assertThat(node.get("foo").asText()).isEqualTo("bar");
        assertThat(node.has("customPromptDraft")).isFalse();
    }

    @Test
    void mergeStageMetadata_extrasTakePrecedenceOverPersistedKeys() throws Exception {
        KnowledgeBaseBuildRuns existing = new KnowledgeBaseBuildRuns();
        existing.setBuildMetadata("{\"promptConfirmed\":true,\"promptStrategy\":\"custom_pipeline\"}");

        String merged = service.mergeStageMetadata(existing, "prompt",
                java.util.Map.of("promptConfirmed", false),  // extras 覆盖旧值
                java.util.List.of("promptConfirmed", "promptStrategy"));

        com.fasterxml.jackson.databind.JsonNode node = new com.fasterxml.jackson.databind.ObjectMapper().readTree(merged);
        assertThat(node.get("promptConfirmed").asBoolean()).isFalse();  // 被 extras 覆盖
        assertThat(node.get("promptStrategy").asText()).isEqualTo("custom_pipeline");  // 保留
    }

    @Test
    void normalizeStrategy_acceptsThreeNewValues() {
        assertThat(service.normalizeStrategy("default")).isEqualTo("default");
        assertThat(service.normalizeStrategy("graphrag_tuned")).isEqualTo("graphrag_tuned");
        assertThat(service.normalizeStrategy("custom_pipeline")).isEqualTo("custom_pipeline");
    }

    @Test
    void normalizeStrategy_mapsLegacyActiveToDefault() {
        assertThat(service.normalizeStrategy("active")).isEqualTo("default");
        assertThat(service.normalizeStrategy("ACTIVE")).isEqualTo("default");
        assertThat(service.normalizeStrategy(" active ")).isEqualTo("default");
    }

    @Test
    void normalizeStrategy_nullAndBlankReturnDefault() {
        assertThat(service.normalizeStrategy(null)).isEqualTo("default");
        assertThat(service.normalizeStrategy("")).isEqualTo("default");
        assertThat(service.normalizeStrategy("   ")).isEqualTo("default");
    }

    @Test
    void normalizeStrategy_unknownThrowsBusinessException() {
        assertThatThrownBy(() -> service.normalizeStrategy("invalid_strategy"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("未知的提示词策略");
    }

    @Test
    void confirmPrompt_confirmedTrueWithDefaultStrategy_writesMetadata() throws Exception {
        KnowledgeBaseBuildRuns buildRun = newBuildRunPersisted();
        org.ysu.ckqaback.index.dto.BuildRunPromptConfirmationRequest req = new org.ysu.ckqaback.index.dto.BuildRunPromptConfirmationRequest();
        req.setConfirmed(true);
        req.setPromptStrategy("default");

        service.confirmPrompt(buildRun.getId(), req);

        KnowledgeBaseBuildRuns updated = buildRunsStore.getRequiredById(buildRun.getId());
        com.fasterxml.jackson.databind.JsonNode node = new com.fasterxml.jackson.databind.ObjectMapper().readTree(updated.getBuildMetadata());
        assertThat(node.get("stage").asText()).isEqualTo("prompt");
        assertThat(node.get("promptConfirmed").asBoolean()).isTrue();
        assertThat(node.get("promptStrategy").asText()).isEqualTo("default");
    }

    @Test
    void confirmPrompt_confirmedFalseResetsWithoutDraftCheck() throws Exception {
        KnowledgeBaseBuildRuns buildRun = newBuildRunPersistedWithMetadata(
            "{\"stage\":\"prompt\",\"promptConfirmed\":true,\"promptStrategy\":\"custom_pipeline\","
            + "\"customPromptDraft\":{\"seed\":\"graphrag_tuned\",\"prompts\":{\"extract_graph\":{\"content\":\"x\"}}}}"
        );
        org.ysu.ckqaback.index.dto.BuildRunPromptConfirmationRequest req = new org.ysu.ckqaback.index.dto.BuildRunPromptConfirmationRequest();
        req.setConfirmed(false);
        req.setPromptStrategy("default");

        service.confirmPrompt(buildRun.getId(), req);

        KnowledgeBaseBuildRuns updated = buildRunsStore.getRequiredById(buildRun.getId());
        com.fasterxml.jackson.databind.JsonNode node = new com.fasterxml.jackson.databind.ObjectMapper().readTree(updated.getBuildMetadata());
        assertThat(node.get("promptConfirmed").asBoolean()).isFalse();
        assertThat(node.get("promptStrategy").asText()).isEqualTo("default");
        assertThat(node.get("customPromptDraft").get("seed").asText()).isEqualTo("graphrag_tuned");  // 草稿保留
    }

    @Test
    void confirmPrompt_legacyActiveStrategyNormalizedToDefault() throws Exception {
        KnowledgeBaseBuildRuns buildRun = newBuildRunPersisted();
        org.ysu.ckqaback.index.dto.BuildRunPromptConfirmationRequest req = new org.ysu.ckqaback.index.dto.BuildRunPromptConfirmationRequest();
        req.setConfirmed(true);
        req.setPromptStrategy("active");

        service.confirmPrompt(buildRun.getId(), req);

        KnowledgeBaseBuildRuns updated = buildRunsStore.getRequiredById(buildRun.getId());
        com.fasterxml.jackson.databind.JsonNode node = new com.fasterxml.jackson.databind.ObjectMapper().readTree(updated.getBuildMetadata());
        assertThat(node.get("promptStrategy").asText()).isEqualTo("default");
    }

    @Test
    void saveCustomPromptDraft_writesDraftStrategyAndClearsConfirmation() throws Exception {
        KnowledgeBaseBuildRuns buildRun = newBuildRunPersistedWithMetadata(
            "{\"stage\":\"prompt\",\"promptConfirmed\":true,\"promptStrategy\":\"default\"}"
        );

        BuildRunCustomPromptDraftRequest req = new BuildRunCustomPromptDraftRequest();
        req.setSeed("system_default");
        BuildRunCustomPromptDraftRequest.PromptBlock block = new BuildRunCustomPromptDraftRequest.PromptBlock();
        block.setContent("-Goal-\nExtract entities.");
        req.setPrompts(java.util.Map.of("extract_graph", block));

        service.saveCustomPromptDraft(buildRun.getId(), req);

        KnowledgeBaseBuildRuns updated = buildRunsStore.getRequiredById(buildRun.getId());
        com.fasterxml.jackson.databind.JsonNode node = new com.fasterxml.jackson.databind.ObjectMapper().readTree(updated.getBuildMetadata());
        assertThat(node.get("promptStrategy").asText()).isEqualTo("custom_pipeline");
        assertThat(node.get("promptConfirmed").asBoolean()).isFalse();  // 原子清除
        com.fasterxml.jackson.databind.JsonNode draft = node.get("customPromptDraft");
        assertThat(draft.get("seed").asText()).isEqualTo("system_default");
        assertThat(draft.get("prompts").get("extract_graph").get("content").asText())
                .isEqualTo("-Goal-\nExtract entities.");
        assertThat(draft.get("updatedAt").isTextual()).isTrue();
        assertThat(draft.get("seedSnapshotAt").isTextual()).isTrue();
        assertThat(draft.get("prompts").get("extract_graph").get("modifiedAt").isTextual()).isTrue();
        assertThat(draft.get("prompts").get("extract_graph").get("baseHash").asText())
                .startsWith("sha256:");
    }

    @Test
    void saveCustomPromptDraft_preservesPriorStageKeys() throws Exception {
        // 验证 saveCustomPromptDraft 不会抹掉前序阶段写入的 exportConfirmed / graphInputConfirmed
        KnowledgeBaseBuildRuns buildRun = newBuildRunPersistedWithMetadata(
            "{\"stage\":\"prompt\",\"exportConfirmed\":true,\"graphInputConfirmed\":true,"
            + "\"promptConfirmed\":true,\"promptStrategy\":\"default\"}"
        );

        BuildRunCustomPromptDraftRequest req = new BuildRunCustomPromptDraftRequest();
        req.setSeed("graphrag_tuned");
        BuildRunCustomPromptDraftRequest.PromptBlock block = new BuildRunCustomPromptDraftRequest.PromptBlock();
        block.setContent("-Goal-\nDo extraction.");
        req.setPrompts(java.util.Map.of("extract_graph", block));

        service.saveCustomPromptDraft(buildRun.getId(), req);

        KnowledgeBaseBuildRuns updated = buildRunsStore.getRequiredById(buildRun.getId());
        com.fasterxml.jackson.databind.JsonNode node = new com.fasterxml.jackson.databind.ObjectMapper().readTree(updated.getBuildMetadata());
        // 前序阶段键被保留
        assertThat(node.get("exportConfirmed").asBoolean()).isTrue();
        assertThat(node.get("graphInputConfirmed").asBoolean()).isTrue();
        // 当前阶段键被 extras 覆盖
        assertThat(node.get("promptStrategy").asText()).isEqualTo("custom_pipeline");
        assertThat(node.get("promptConfirmed").asBoolean()).isFalse();
        assertThat(node.has("customPromptDraft")).isTrue();
    }

    @Test
    void saveCustomPromptDraft_seedHistoryDraftRejected() {
        KnowledgeBaseBuildRuns buildRun = newBuildRunPersisted();
        BuildRunCustomPromptDraftRequest req = newDraftRequest("history_draft", "valid content");

        assertThatThrownBy(() -> service.saveCustomPromptDraft(buildRun.getId(), req))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("暂未开放");
    }

    @Test
    void saveCustomPromptDraft_unknownSeedRejected() {
        KnowledgeBaseBuildRuns buildRun = newBuildRunPersisted();
        BuildRunCustomPromptDraftRequest req = newDraftRequest("invalid_seed", "valid content");

        assertThatThrownBy(() -> service.saveCustomPromptDraft(buildRun.getId(), req))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("未知的种子模板");
    }

    @Test
    void saveCustomPromptDraft_blankContentRejected() {
        KnowledgeBaseBuildRuns buildRun = newBuildRunPersisted();
        BuildRunCustomPromptDraftRequest req = newDraftRequest("system_default", "   \n\t  ");

        assertThatThrownBy(() -> service.saveCustomPromptDraft(buildRun.getId(), req))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("提示词内容不能为空");
    }

    @Test
    void saveCustomPromptDraft_oversizeContentRejected() {
        KnowledgeBaseBuildRuns buildRun = newBuildRunPersisted();
        String oversize = "a".repeat(32 * 1024 + 1);  // 32 KB + 1 字节
        BuildRunCustomPromptDraftRequest req = newDraftRequest("system_default", oversize);

        assertThatThrownBy(() -> service.saveCustomPromptDraft(buildRun.getId(), req))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("32");
    }

    @Test
    void saveCustomPromptDraft_chineseContentUsesByteLength() {
        KnowledgeBaseBuildRuns buildRun = newBuildRunPersisted();
        String chinese = "你好".repeat(5500);  // 每字符 3 字节，约 33 KB 字节
        BuildRunCustomPromptDraftRequest req = newDraftRequest("system_default", chinese);

        assertThatThrownBy(() -> service.saveCustomPromptDraft(buildRun.getId(), req))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("32");
    }

    @Test
    void saveCustomPromptDraft_partialUpdateSeedOnly_preservesPrompts() throws Exception {
        // 已有完整 draft，PUT 仅传 seed → 旧 prompts.extract_graph.content 保留，seed 被刷新
        KnowledgeBaseBuildRuns buildRun = newBuildRunPersistedWithMetadata(
            "{\"stage\":\"prompt\",\"customPromptDraft\":{\"seed\":\"system_default\","
            + "\"seedSnapshotAt\":\"2026-05-17T10:00:00\",\"updatedAt\":\"2026-05-17T10:00:00\","
            + "\"prompts\":{\"extract_graph\":{\"content\":\"-Goal-\\nKeep me.\","
            + "\"modifiedAt\":\"2026-05-17T10:00:00\",\"baseHash\":\"sha256:legacyhash\"}}}}"
        );

        BuildRunCustomPromptDraftRequest req = new BuildRunCustomPromptDraftRequest();
        req.setSeed("graphrag_tuned");
        // 故意不调 setPrompts → 部分更新

        service.saveCustomPromptDraft(buildRun.getId(), req);

        KnowledgeBaseBuildRuns updated = buildRunsStore.getRequiredById(buildRun.getId());
        com.fasterxml.jackson.databind.JsonNode draft = new com.fasterxml.jackson.databind.ObjectMapper()
                .readTree(updated.getBuildMetadata())
                .get("customPromptDraft");
        assertThat(draft.get("seed").asText()).isEqualTo("graphrag_tuned");
        // seed 变化时 seedSnapshotAt 必须刷新
        assertThat(draft.get("seedSnapshotAt").asText()).isNotEqualTo("2026-05-17T10:00:00");
        // prompts 内容保留
        com.fasterxml.jackson.databind.JsonNode extract = draft.get("prompts").get("extract_graph");
        assertThat(extract.get("content").asText()).isEqualTo("-Goal-\nKeep me.");
        // 部分更新时 modifiedAt / baseHash 保持旧值，反映 prompts 实质未变
        assertThat(extract.get("modifiedAt").asText()).isEqualTo("2026-05-17T10:00:00");
        assertThat(extract.get("baseHash").asText()).isEqualTo("sha256:legacyhash");
    }

    @Test
    void saveCustomPromptDraft_partialUpdateSeedOnly_noPriorPrompts() throws Exception {
        // build run 中没有 customPromptDraft，PUT 仅传 seed → 写入仅含 seed 的 draft，不报错
        KnowledgeBaseBuildRuns buildRun = newBuildRunPersistedWithMetadata(
            "{\"stage\":\"prompt\",\"promptStrategy\":\"default\"}"
        );

        BuildRunCustomPromptDraftRequest req = new BuildRunCustomPromptDraftRequest();
        req.setSeed("system_default");

        service.saveCustomPromptDraft(buildRun.getId(), req);

        KnowledgeBaseBuildRuns updated = buildRunsStore.getRequiredById(buildRun.getId());
        com.fasterxml.jackson.databind.JsonNode node = new com.fasterxml.jackson.databind.ObjectMapper()
                .readTree(updated.getBuildMetadata());
        com.fasterxml.jackson.databind.JsonNode draft = node.get("customPromptDraft");
        assertThat(draft.get("seed").asText()).isEqualTo("system_default");
        assertThat(draft.get("seedSnapshotAt").isTextual()).isTrue();
        assertThat(draft.get("updatedAt").isTextual()).isTrue();
        // 既无旧 prompts 又无新 prompts，draft.prompts 不应落盘
        assertThat(draft.has("prompts")).isFalse();
        // 当前阶段键仍正常写入
        assertThat(node.get("promptStrategy").asText()).isEqualTo("custom_pipeline");
        assertThat(node.get("promptConfirmed").asBoolean()).isFalse();
    }

    @Test
    void saveCustomPromptDraft_fullUpdateOverwritesPrompts() throws Exception {
        // 已有完整 draft，PUT 同时传 seed 和 prompts → 新 content 覆盖旧 content（保持 Phase 1 行为）
        KnowledgeBaseBuildRuns buildRun = newBuildRunPersistedWithMetadata(
            "{\"stage\":\"prompt\",\"customPromptDraft\":{\"seed\":\"system_default\","
            + "\"seedSnapshotAt\":\"2026-05-17T10:00:00\",\"updatedAt\":\"2026-05-17T10:00:00\","
            + "\"prompts\":{\"extract_graph\":{\"content\":\"-Goal-\\nOLD.\","
            + "\"modifiedAt\":\"2026-05-17T10:00:00\",\"baseHash\":\"sha256:legacyhash\"}}}}"
        );

        BuildRunCustomPromptDraftRequest req = newDraftRequest("graphrag_tuned", "-Goal-\nNEW.");

        service.saveCustomPromptDraft(buildRun.getId(), req);

        KnowledgeBaseBuildRuns updated = buildRunsStore.getRequiredById(buildRun.getId());
        com.fasterxml.jackson.databind.JsonNode extract = new com.fasterxml.jackson.databind.ObjectMapper()
                .readTree(updated.getBuildMetadata())
                .get("customPromptDraft").get("prompts").get("extract_graph");
        assertThat(extract.get("content").asText()).isEqualTo("-Goal-\nNEW.");
        // 全量更新时 modifiedAt 必须刷新
        assertThat(extract.get("modifiedAt").asText()).isNotEqualTo("2026-05-17T10:00:00");
        // 全量更新时 baseHash 跟随 seed 重算（这里 seed=graphrag_tuned）
        assertThat(extract.get("baseHash").asText()).isNotEqualTo("sha256:legacyhash");
        assertThat(extract.get("baseHash").asText()).startsWith("sha256:");
    }

    private BuildRunCustomPromptDraftRequest newDraftRequest(String seed, String content) {
        BuildRunCustomPromptDraftRequest req = new BuildRunCustomPromptDraftRequest();
        req.setSeed(seed);
        BuildRunCustomPromptDraftRequest.PromptBlock block = new BuildRunCustomPromptDraftRequest.PromptBlock();
        block.setContent(content);
        req.setPrompts(java.util.Map.of("extract_graph", block));
        return req;
    }

    private KnowledgeBaseBuildRuns newBuildRunPersisted() {
        return newBuildRunPersistedWithMetadata("{\"stage\":\"graph_input_export\"}");
    }

    private KnowledgeBaseBuildRuns newBuildRunPersistedWithMetadata(String metadata) {
        KnowledgeBaseBuildRuns run = new KnowledgeBaseBuildRuns();
        run.setId(1L);
        run.setKnowledgeBaseId(10L);
        run.setBuildMetadata(metadata);
        run.setCurrentStage("graph_input_export");
        when(buildRunsStore.getRequiredById(1L)).thenReturn(run);
        when(buildRunsStore.updateById(any())).thenReturn(true);
        return run;
    }

    private IndexRuns successIndexRun(Long id, Long buildRunId, LocalDateTime finishedAt) {
        IndexRuns run = new IndexRuns();
        run.setId(id);
        run.setKnowledgeBaseId(5L);
        run.setBuildRunId(buildRunId);
        run.setStatus("success");
        run.setFinishedAt(finishedAt);
        return run;
    }
}
