package org.ysu.ckqaback.index;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.ArgumentCaptor;
import org.ysu.ckqaback.api.ApiResultCode;
import org.ysu.ckqaback.entity.KnowledgeBaseBuildRuns;
import org.ysu.ckqaback.entity.PromptTuneAuditSamples;
import org.ysu.ckqaback.entity.PromptTuneExtractionEvalRuns;
import org.ysu.ckqaback.exception.BusinessException;
import org.ysu.ckqaback.index.dto.ExtractionEvalRequest;
import org.ysu.ckqaback.index.dto.ExtractionEvalRunStartedResponse;
import org.ysu.ckqaback.index.dto.ExtractionEvalStatusResponse;
import org.ysu.ckqaback.service.KnowledgeBaseBuildRunsService;
import org.ysu.ckqaback.service.PromptTuneAuditSamplesService;
import org.ysu.ckqaback.service.PromptTuneExtractionEvalRunsService;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ExtractionEvalServiceTest {

    private KnowledgeBaseBuildRunsService buildRunsService;
    private PromptTuneAuditSamplesService samplesService;
    private PromptTuneExtractionEvalRunsService evalRunsService;
    private CandidateManifestReader manifestReader;
    private BuildRunWorkspaceService workspaceService;
    private ExtractionEvalReportAssembler reportAssembler;
    private ExtractionEvalWorker worker;
    private ObjectMapper objectMapper;
    private ExtractionEvalService service;

    private Path workspaceDir;

    @BeforeEach
    void setUp(@TempDir Path tmp) throws Exception {
        buildRunsService = mock(KnowledgeBaseBuildRunsService.class);
        samplesService = mock(PromptTuneAuditSamplesService.class);
        evalRunsService = mock(PromptTuneExtractionEvalRunsService.class);
        manifestReader = mock(CandidateManifestReader.class);
        workspaceService = mock(BuildRunWorkspaceService.class);
        reportAssembler = mock(ExtractionEvalReportAssembler.class);
        worker = mock(ExtractionEvalWorker.class);
        objectMapper = new ObjectMapper();

        service = new ExtractionEvalService(
                buildRunsService,
                samplesService,
                evalRunsService,
                manifestReader,
                workspaceService,
                reportAssembler,
                worker,
                objectMapper
        );

        workspaceDir = tmp.resolve("kb-build-runs/user_0/kb_5/build_18");
        Files.createDirectories(workspaceDir.resolve("prompt/candidates"));
        when(workspaceService.resolve(any())).thenReturn(workspaceDir);
        when(buildRunsService.getRequiredById(18L)).thenReturn(newBuildRun(18L));
    }

    // ----- trigger -----

    @Test
    void triggerThrows4104WhenNoCompletedSamples() {
        when(samplesService.listByBuildRunId(18L)).thenReturn(List.of(
                newSample("pending"), newSample("in_progress")
        ));

        ExtractionEvalRequest req = new ExtractionEvalRequest();
        req.setSelectedCandidates(List.of("default"));

        assertThatThrownBy(() -> service.trigger(18L, req))
                .isInstanceOfSatisfying(BusinessException.class, e ->
                        assertThat(e.getCode()).isEqualTo(ApiResultCode.CANDIDATE_REQUIRES_AUDIT_COMPLETED.getCode()));
    }

    @Test
    void triggerThrows4105WhenCandidatesNotGenerated() throws Exception {
        when(samplesService.listByBuildRunId(18L)).thenReturn(List.of(newSample("completed")));
        when(manifestReader.read(any())).thenReturn(List.of());

        ExtractionEvalRequest req = new ExtractionEvalRequest();
        req.setSelectedCandidates(List.of("default"));

        assertThatThrownBy(() -> service.trigger(18L, req))
                .isInstanceOfSatisfying(BusinessException.class, e ->
                        assertThat(e.getCode()).isEqualTo(ApiResultCode.CANDIDATES_NOT_GENERATED.getCode()));
    }

    @Test
    void triggerThrows4108WhenSelectedCandidateIdNotInManifest() throws Exception {
        when(samplesService.listByBuildRunId(18L)).thenReturn(List.of(newSample("completed")));
        when(manifestReader.read(any())).thenReturn(List.of(stubCandidateResponse("default")));

        ExtractionEvalRequest req = new ExtractionEvalRequest();
        req.setSelectedCandidates(List.of("default", "phantom_candidate"));

        assertThatThrownBy(() -> service.trigger(18L, req))
                .isInstanceOfSatisfying(BusinessException.class, e -> {
                    assertThat(e.getCode()).isEqualTo(ApiResultCode.INVALID_EVAL_CANDIDATE_SELECTION.getCode());
                    assertThat(e.getMessage()).contains("phantom_candidate");
                });
    }

    @Test
    void triggerReturnsExistingActiveRunInsteadOfCreatingNew() throws Exception {
        when(samplesService.listByBuildRunId(18L)).thenReturn(List.of(newSample("completed")));
        when(manifestReader.read(any())).thenReturn(List.of(stubCandidateResponse("default")));
        PromptTuneExtractionEvalRuns active = newRun(7L, "running", "[\"default\"]");
        when(evalRunsService.findActiveByBuildRunId(18L)).thenReturn(Optional.of(active));

        ExtractionEvalRequest req = new ExtractionEvalRequest();
        req.setSelectedCandidates(List.of("default"));

        ExtractionEvalRunStartedResponse response = service.trigger(18L, req);

        assertThat(response.getEvalRunId()).isEqualTo(7L);
        assertThat(response.getReusedActiveRun()).isTrue();
        verify(worker, never()).dispatch(any());
    }

    @Test
    void triggerCreatesPendingRunAndDispatchesAfterCommit() throws Exception {
        when(samplesService.listByBuildRunId(18L)).thenReturn(List.of(newSample("completed")));
        when(manifestReader.read(any())).thenReturn(List.of(stubCandidateResponse("default")));
        when(evalRunsService.findActiveByBuildRunId(18L)).thenReturn(Optional.empty());
        when(evalRunsService.save(any())).thenAnswer(inv -> {
            PromptTuneExtractionEvalRuns r = inv.getArgument(0);
            r.setId(123L);
            return true;
        });

        ExtractionEvalRequest req = new ExtractionEvalRequest();
        req.setSelectedCandidates(List.of("default"));

        ExtractionEvalRunStartedResponse response = service.trigger(18L, req);

        assertThat(response.getEvalRunId()).isEqualTo(123L);
        assertThat(response.getReusedActiveRun()).isFalse();
        // 测试环境无事务上下文 → 直接 dispatch
        verify(worker).dispatch(123L);

        // pending 行字段
        ArgumentCaptor<PromptTuneExtractionEvalRuns> captor = ArgumentCaptor.forClass(PromptTuneExtractionEvalRuns.class);
        verify(evalRunsService).save(captor.capture());
        assertThat(captor.getValue().getStatus()).isEqualTo("pending");
        assertThat(captor.getValue().getProgressStage()).isEqualTo("queued");
        assertThat(captor.getValue().getSelectedCandidateIds()).contains("default");
    }

    @Test
    void triggerWritesSeedSnapshotFromBuildRunMetadata() throws Exception {
        // Phase 4.5 引入：启动评分时把 build run metadata.customPromptDraft.seed 写入 eval run
        when(samplesService.listByBuildRunId(18L)).thenReturn(List.of(newSample("completed")));
        when(manifestReader.read(any())).thenReturn(List.of(stubCandidateResponse("default")));
        when(evalRunsService.findActiveByBuildRunId(18L)).thenReturn(Optional.empty());
        when(evalRunsService.save(any())).thenAnswer(inv -> {
            PromptTuneExtractionEvalRuns r = inv.getArgument(0);
            r.setId(124L);
            return true;
        });
        // 覆盖默认 newBuildRun(18L)，注入含 seed 的 metadata
        KnowledgeBaseBuildRuns withSeed = newBuildRun(18L);
        withSeed.setBuildMetadata("{\"customPromptDraft\":{\"seed\":\"graphrag_tuned\"}}");
        when(buildRunsService.getRequiredById(18L)).thenReturn(withSeed);

        ExtractionEvalRequest req = new ExtractionEvalRequest();
        req.setSelectedCandidates(List.of("default"));
        service.trigger(18L, req);

        ArgumentCaptor<PromptTuneExtractionEvalRuns> captor = ArgumentCaptor.forClass(PromptTuneExtractionEvalRuns.class);
        verify(evalRunsService).save(captor.capture());
        assertThat(captor.getValue().getSeed()).isEqualTo("graphrag_tuned");
    }

    @Test
    void triggerWritesNullSeedWhenMetadataMissing() throws Exception {
        // 兼容路径：build run 没有 customPromptDraft.seed 字段时 seed 写 null
        when(samplesService.listByBuildRunId(18L)).thenReturn(List.of(newSample("completed")));
        when(manifestReader.read(any())).thenReturn(List.of(stubCandidateResponse("default")));
        when(evalRunsService.findActiveByBuildRunId(18L)).thenReturn(Optional.empty());
        when(evalRunsService.save(any())).thenAnswer(inv -> {
            PromptTuneExtractionEvalRuns r = inv.getArgument(0);
            r.setId(125L);
            return true;
        });

        ExtractionEvalRequest req = new ExtractionEvalRequest();
        req.setSelectedCandidates(List.of("default"));
        service.trigger(18L, req);

        ArgumentCaptor<PromptTuneExtractionEvalRuns> captor = ArgumentCaptor.forClass(PromptTuneExtractionEvalRuns.class);
        verify(evalRunsService).save(captor.capture());
        assertThat(captor.getValue().getSeed()).isNull();
    }

    // ----- status -----

    @Test
    void getStatusThrows4106WhenNoRunExists() {
        when(evalRunsService.findLatestByBuildRunId(18L)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.getStatus(18L))
                .isInstanceOfSatisfying(BusinessException.class, e ->
                        assertThat(e.getCode()).isEqualTo(ApiResultCode.EXTRACTION_EVAL_NOT_STARTED.getCode()));
    }

    @Test
    void getStatusReturnsCandidateProgressMatchingFinished() {
        PromptTuneExtractionEvalRuns run = newRun(3L, "running", "[\"default\",\"auto_tuned\"]");
        run.setProgressStage("extracting");
        run.setExtractingCandidateId("auto_tuned");
        run.setFinishedCandidates("[\"default\"]");
        when(evalRunsService.findLatestByBuildRunId(18L)).thenReturn(Optional.of(run));

        ExtractionEvalStatusResponse response = service.getStatus(18L);

        assertThat(response.getEvalRunId()).isEqualTo(3L);
        assertThat(response.getStatus()).isEqualTo("running");
        assertThat(response.getCandidates()).hasSize(2);
        assertThat(response.getCandidates().get(0).getCandidateId()).isEqualTo("default");
        assertThat(response.getCandidates().get(0).getStatus()).isEqualTo("done");
        assertThat(response.getCandidates().get(1).getCandidateId()).isEqualTo("auto_tuned");
        assertThat(response.getCandidates().get(1).getStatus()).isEqualTo("extracting");
    }

    // ----- cancel -----

    @Test
    void cancelTransitionsRunningToCancelling() {
        PromptTuneExtractionEvalRuns run = newRun(3L, "running", "[\"default\"]");
        when(evalRunsService.findActiveByBuildRunId(18L)).thenReturn(Optional.of(run));

        service.cancel(18L);

        verify(evalRunsService).updateById(argThat(r -> "cancelling".equals(r.getStatus())));
    }

    @Test
    void cancelIsNoOpWhenNoActiveRun() {
        when(evalRunsService.findActiveByBuildRunId(18L)).thenReturn(Optional.empty());
        // 不抛错（幂等）
        service.cancel(18L);
        verify(evalRunsService, never()).updateById(any());
    }

    // ----- helpers -----

    private static PromptTuneAuditSamples newSample(String decision) {
        PromptTuneAuditSamples s = new PromptTuneAuditSamples();
        s.setReviewerDecision(decision);
        return s;
    }

    private static KnowledgeBaseBuildRuns newBuildRun(Long id) {
        KnowledgeBaseBuildRuns r = new KnowledgeBaseBuildRuns();
        r.setId(id);
        r.setKnowledgeBaseId(5L);
        r.setRequestedByUserId(0L);
        r.setWorkspaceUri("user_0/kb_5/build_18");
        return r;
    }

    private static PromptTuneExtractionEvalRuns newRun(Long id, String status, String selectedJson) {
        PromptTuneExtractionEvalRuns r = new PromptTuneExtractionEvalRuns();
        r.setId(id);
        r.setBuildRunId(18L);
        r.setKnowledgeBaseId(5L);
        r.setSelectedCandidateIds(selectedJson);
        r.setStatus(status);
        r.setProgressStage("queued");
        return r;
    }

    private static org.ysu.ckqaback.index.dto.CandidateResponse stubCandidateResponse(String id) {
        return org.ysu.ckqaback.index.dto.CandidateResponse.builder().candidateId(id).build();
    }
}
