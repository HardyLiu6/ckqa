package org.ysu.ckqaback.index;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.ArgumentCaptor;
import org.ysu.ckqaback.api.ApiResultCode;
import org.ysu.ckqaback.entity.KnowledgeBaseBuildRuns;
import org.ysu.ckqaback.entity.PromptDrafts;
import org.ysu.ckqaback.entity.PromptTuneExtractionEvalRuns;
import org.ysu.ckqaback.exception.BusinessException;
import org.ysu.ckqaback.index.dto.FinalizePromptRequest;
import org.ysu.ckqaback.service.KnowledgeBaseBuildRunsService;
import org.ysu.ckqaback.service.PromptDraftsService;
import org.ysu.ckqaback.service.PromptTuneExtractionEvalRunsService;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class FinalizePromptServiceTest {

    private KnowledgeBaseBuildRunsService buildRunsService;
    private PromptTuneExtractionEvalRunsService evalRunsService;
    private PromptDraftsService promptDraftsService;
    private BuildRunWorkspaceService workspaceService;
    private ObjectMapper objectMapper;
    private FinalizePromptService service;

    private Path workspaceDir;
    private AtomicReference<KnowledgeBaseBuildRuns> persistedBuildRun;

    @BeforeEach
    void setUp(@TempDir Path tmp) throws Exception {
        buildRunsService = mock(KnowledgeBaseBuildRunsService.class);
        evalRunsService = mock(PromptTuneExtractionEvalRunsService.class);
        promptDraftsService = mock(PromptDraftsService.class);
        workspaceService = mock(BuildRunWorkspaceService.class);
        objectMapper = new ObjectMapper();

        service = new FinalizePromptService(
                buildRunsService,
                evalRunsService,
                promptDraftsService,
                workspaceService,
                objectMapper
        );

        workspaceDir = tmp.resolve("kb-build-runs/user_0/kb_5/build_18");
        Files.createDirectories(workspaceDir.resolve("prompt/candidates/default"));
        Files.writeString(workspaceDir.resolve("prompt/candidates/default/prompt.txt"),
                "-Goal-\nExtract entities.\n");
        when(workspaceService.resolve(any())).thenReturn(workspaceDir);

        // 默认 build run；测试可在体内覆写 buildMetadata
        persistedBuildRun = new AtomicReference<>(newBuildRun(18L, 5L));
        when(buildRunsService.getRequiredById(18L)).thenAnswer(inv -> persistedBuildRun.get());
        when(buildRunsService.updateById(any(KnowledgeBaseBuildRuns.class))).thenAnswer(inv -> {
            persistedBuildRun.set(inv.getArgument(0));
            return true;
        });
    }

    @Test
    void rejectsWhenNoEvalRunExists() {
        when(evalRunsService.findLatestByBuildRunId(18L)).thenReturn(Optional.empty());

        FinalizePromptRequest req = new FinalizePromptRequest();
        req.setCandidateId("default");

        assertThatThrownBy(() -> service.finalizePrompt(18L, req))
                .isInstanceOfSatisfying(BusinessException.class, e ->
                        assertThat(e.getCode()).isEqualTo(ApiResultCode.EXTRACTION_EVAL_NOT_SUCCESS.getCode()));
    }

    @Test
    void rejectsWhenLatestEvalRunNotSuccess() {
        when(evalRunsService.findLatestByBuildRunId(18L))
                .thenReturn(Optional.of(newEvalRun(7L, "running", null)));

        FinalizePromptRequest req = new FinalizePromptRequest();
        req.setCandidateId("default");

        assertThatThrownBy(() -> service.finalizePrompt(18L, req))
                .isInstanceOfSatisfying(BusinessException.class, e ->
                        assertThat(e.getCode()).isEqualTo(ApiResultCode.EXTRACTION_EVAL_NOT_SUCCESS.getCode()));
    }

    @Test
    void rejectsWhenCandidateIdNotInReport() {
        // 报告中只有 default，但用户传 phantom → 4111
        when(evalRunsService.findLatestByBuildRunId(18L)).thenReturn(Optional.of(newEvalRun(7L, "success", """
                {"all_candidates_ranked": [
                  {"candidate":"default","rank":1,"composite_score":0.7}
                ]}
                """)));

        FinalizePromptRequest req = new FinalizePromptRequest();
        req.setCandidateId("phantom_x");

        assertThatThrownBy(() -> service.finalizePrompt(18L, req))
                .isInstanceOfSatisfying(BusinessException.class, e -> {
                    assertThat(e.getCode()).isEqualTo(ApiResultCode.INVALID_FINALIZE_CANDIDATE.getCode());
                    assertThat(e.getMessage()).contains("phantom_x");
                });
    }

    @Test
    void raises5000WhenReportJsonIsCorrupted() {
        // reportJson 解析失败时应该抛 5000 INTERNAL_ERROR（服务端数据异常），不能误抛 4111
        when(evalRunsService.findLatestByBuildRunId(18L))
                .thenReturn(Optional.of(newEvalRun(7L, "success", "not-a-valid-json")));

        FinalizePromptRequest req = new FinalizePromptRequest();
        req.setCandidateId("default");

        assertThatThrownBy(() -> service.finalizePrompt(18L, req))
                .isInstanceOfSatisfying(BusinessException.class, e -> {
                    assertThat(e.getCode()).isEqualTo(ApiResultCode.INTERNAL_ERROR.getCode());
                    assertThat(e.getMessage()).containsAnyOf("解析", "JSON");
                });
    }

    @Test
    void writesCustomPromptDraftWithCompleteSnapshotWhenSaveAsDraftFalse() throws Exception {
        // saveAsDraft=false → 仅写 customPromptDraft 完整快照（不入库历史草稿）
        when(evalRunsService.findLatestByBuildRunId(18L)).thenReturn(Optional.of(newEvalRun(7L, "success", """
                {"all_candidates_ranked": [
                  {"candidate":"default","rank":1,"composite_score":0.7}
                ]}
                """)));

        FinalizePromptRequest req = new FinalizePromptRequest();
        req.setCandidateId("default");
        req.setSaveAsDraft(false);

        service.finalizePrompt(18L, req);

        // build run.buildMetadata.customPromptDraft 含完整快照
        ArgumentCaptor<KnowledgeBaseBuildRuns> captor = ArgumentCaptor.forClass(KnowledgeBaseBuildRuns.class);
        verify(buildRunsService).updateById(captor.capture());
        JsonNode draft = objectMapper.readTree(captor.getValue().getBuildMetadata())
                .path("customPromptDraft");
        assertThat(draft.path("selectedCandidateId").asText()).isEqualTo("default");
        assertThat(draft.path("compositeScore").asText()).isEqualTo("0.7");
        assertThat(draft.path("sourceEvalRunId").asLong()).isEqualTo(7L);
        assertThat(draft.path("finalizedAt").asText()).matches("^\\d{4}-\\d{2}-\\d{2}T.*[+-]\\d{2}:\\d{2}$");
        assertThat(draft.path("prompts").path("extract_graph").path("content").asText())
                .contains("Extract entities");

        verify(promptDraftsService, never()).save(any());
    }

    @Test
    void writesCustomPromptDraftAndInsertsHistoryDraftWhenSaveAsDraftTrue() throws Exception {
        when(evalRunsService.findLatestByBuildRunId(18L)).thenReturn(Optional.of(newEvalRun(7L, "success", """
                {"all_candidates_ranked": [
                  {"candidate":"default","rank":1,"composite_score":0.7}
                ]}
                """)));

        FinalizePromptRequest req = new FinalizePromptRequest();
        req.setCandidateId("default");
        req.setSaveAsDraft(true);
        req.setDraftName("课程名 · 默认基线 · 2026-05-17");
        req.setDraftDescription("经过 20 条校准集评估，综合分 0.70");
        when(promptDraftsService.save(any())).thenReturn(true);

        service.finalizePrompt(18L, req);

        ArgumentCaptor<PromptDrafts> draftCaptor = ArgumentCaptor.forClass(PromptDrafts.class);
        verify(promptDraftsService).save(draftCaptor.capture());
        PromptDrafts saved = draftCaptor.getValue();
        assertThat(saved.getKnowledgeBaseId()).isEqualTo(5L);
        assertThat(saved.getName()).isEqualTo("课程名 · 默认基线 · 2026-05-17");
        assertThat(saved.getDescription()).contains("0.70");
        assertThat(saved.getCandidateId()).isEqualTo("default");
        assertThat(saved.getSourceBuildRunId()).isEqualTo(18L);
        assertThat(saved.getCompositeScore()).isNotNull();
        assertThat(saved.getPromptsJson()).contains("Extract entities");
    }

    @Test
    void rollsBackWhenPromptDraftSaveReturnsFalse() {
        // 审阅意见 #2：save 返回 false 不抛错时应该主动抛 5000，让 @Transactional 回滚
        when(evalRunsService.findLatestByBuildRunId(18L)).thenReturn(Optional.of(newEvalRun(7L, "success", """
                {"all_candidates_ranked": [
                  {"candidate":"default","rank":1,"composite_score":0.7}
                ]}
                """)));
        when(promptDraftsService.save(any())).thenReturn(false);

        FinalizePromptRequest req = new FinalizePromptRequest();
        req.setCandidateId("default");
        req.setSaveAsDraft(true);
        req.setDraftName("draft");

        assertThatThrownBy(() -> service.finalizePrompt(18L, req))
                .isInstanceOfSatisfying(BusinessException.class, e -> {
                    assertThat(e.getCode()).isEqualTo(ApiResultCode.INTERNAL_ERROR.getCode());
                    assertThat(e.getMessage()).contains("回滚");
                });
    }

    @Test
    void usesCurrentBuildRunSeedSnapshotInDraftAndCustomPromptDraft() throws Exception {
        // build run metadata 中有 seed=graphrag_tuned，draft 与 customPromptDraft 都应继承
        KnowledgeBaseBuildRuns buildRun = newBuildRun(18L, 5L);
        buildRun.setBuildMetadata("{\"customPromptDraft\":{\"seed\":\"graphrag_tuned\"}}");
        persistedBuildRun.set(buildRun);

        when(evalRunsService.findLatestByBuildRunId(18L)).thenReturn(Optional.of(newEvalRun(7L, "success", """
                {"all_candidates_ranked": [
                  {"candidate":"default","rank":1,"composite_score":0.7}
                ]}
                """)));
        when(promptDraftsService.save(any())).thenReturn(true);

        FinalizePromptRequest req = new FinalizePromptRequest();
        req.setCandidateId("default");
        req.setSaveAsDraft(true);
        req.setDraftName("draft");

        service.finalizePrompt(18L, req);

        ArgumentCaptor<PromptDrafts> draftCaptor = ArgumentCaptor.forClass(PromptDrafts.class);
        verify(promptDraftsService).save(draftCaptor.capture());
        assertThat(draftCaptor.getValue().getSeed()).isEqualTo("graphrag_tuned");

        ArgumentCaptor<KnowledgeBaseBuildRuns> brCaptor = ArgumentCaptor.forClass(KnowledgeBaseBuildRuns.class);
        verify(buildRunsService).updateById(brCaptor.capture());
        JsonNode draft = objectMapper.readTree(brCaptor.getValue().getBuildMetadata())
                .path("customPromptDraft");
        assertThat(draft.path("seed").asText()).isEqualTo("graphrag_tuned");
    }

    private static KnowledgeBaseBuildRuns newBuildRun(Long id, Long kbId) {
        KnowledgeBaseBuildRuns r = new KnowledgeBaseBuildRuns();
        r.setId(id);
        r.setKnowledgeBaseId(kbId);
        r.setRequestedByUserId(0L);
        r.setWorkspaceUri("user_0/kb_5/build_18");
        return r;
    }

    private static PromptTuneExtractionEvalRuns newEvalRun(Long id, String status, String reportJson) {
        PromptTuneExtractionEvalRuns r = new PromptTuneExtractionEvalRuns();
        r.setId(id);
        r.setBuildRunId(18L);
        r.setKnowledgeBaseId(5L);
        r.setStatus(status);
        r.setReportJson(reportJson);
        return r;
    }
}
