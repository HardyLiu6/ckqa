package org.ysu.ckqaback.index;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.InOrder;
import org.ysu.ckqaback.entity.KnowledgeBaseBuildRuns;
import org.ysu.ckqaback.entity.PromptTuneExtractionEvalRuns;
import org.ysu.ckqaback.integration.config.CkqaIntegrationProperties;
import org.ysu.ckqaback.service.KnowledgeBaseBuildRunsService;
import org.ysu.ckqaback.service.PromptTuneExtractionEvalRunsService;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Worker 同步路径单测；不验证 Spring 异步派发，直接调 runInternal。
 *
 * 关键测试点：
 * 1. 候选按顺序串行调用 runSingleCandidateExtract
 * 2. 全部完成后调 runScoring
 * 3. cancelling 状态时立即终止（候选边界软取消）
 * 4. 单个候选失败时仅追加 latestLogs 并继续跑剩余候选；finished 全空才整体 failed
 * 5. 成功时把 top_candidates.json 内容写入 reportJson 列
 */
class ExtractionEvalWorkerTest {

    private PromptTuneExtractionEvalRunsService evalRunsService;
    private KnowledgeBaseBuildRunsService buildRunsService;
    private BuildRunWorkspaceService workspaceService;
    private ExtractionEvalOrchestrator orchestrator;
    private CkqaIntegrationProperties properties;
    private CandidateMetadataLookup metadataLookup;
    private Executor inlineExecutor;
    private ExtractionEvalWorker worker;
    private Path workspaceDir;

    @BeforeEach
    void setUp(@TempDir Path tmp) throws Exception {
        evalRunsService = mock(PromptTuneExtractionEvalRunsService.class);
        buildRunsService = mock(KnowledgeBaseBuildRunsService.class);
        workspaceService = mock(BuildRunWorkspaceService.class);
        orchestrator = mock(ExtractionEvalOrchestrator.class);
        metadataLookup = new CandidateMetadataLookup();
        properties = new CkqaIntegrationProperties();
        properties.getGraphrag().setRoot(tmp.resolve("graphrag").toString());
        properties.getGraphrag().setBuildRunsRoot(tmp.resolve("kb-build-runs").toString());
        Files.createDirectories(tmp.resolve("graphrag/results/extraction_eval/runs/eval_18_3"));
        Files.createDirectories(tmp.resolve("graphrag/results/reports/extraction_scoring/runs/eval_18_3"));
        Files.writeString(
                tmp.resolve("graphrag/results/reports/extraction_scoring/runs/eval_18_3/top_candidates.json"),
                "{\"all_candidates_ranked\": []}"
        );

        workspaceDir = tmp.resolve("kb-build-runs/user_0/kb_5/build_18");
        Files.createDirectories(workspaceDir.resolve("prompt/candidates/default"));
        Files.writeString(workspaceDir.resolve("prompt/candidates/default/prompt.txt"), "default prompt");
        Files.createDirectories(workspaceDir.resolve("prompt/candidates/auto_tuned"));
        Files.writeString(workspaceDir.resolve("prompt/candidates/auto_tuned/prompt.txt"), "auto prompt");
        Files.writeString(workspaceDir.resolve("prompt/candidates/audit_with_gold.json"), "{\"audit_samples\":[]}");
        when(workspaceService.resolve(any())).thenReturn(workspaceDir);

        inlineExecutor = Runnable::run;  // 同步执行

        worker = new ExtractionEvalWorker(
                evalRunsService,
                buildRunsService,
                workspaceService,
                orchestrator,
                properties,
                metadataLookup,
                new ObjectMapper(),
                inlineExecutor
        );
    }

    @Test
    void runsSelectedCandidatesInOrderThenScores() throws Exception {
        PromptTuneExtractionEvalRuns run = newRun(3L, 18L, "running", "[\"default\",\"auto_tuned\"]");
        when(evalRunsService.getRequiredById(3L)).thenReturn(run);
        when(buildRunsService.getRequiredById(18L)).thenReturn(newBuildRun(18L));

        worker.runInternal(3L);

        InOrder inOrder = inOrder(orchestrator);
        inOrder.verify(orchestrator).runSingleCandidateExtract(
                eq("default"), any(), any(), anyString(), eq("eval_18_3"), any()
        );
        inOrder.verify(orchestrator).runSingleCandidateExtract(
                eq("auto_tuned"), any(), any(), anyString(), eq("eval_18_3"), any()
        );
        inOrder.verify(orchestrator).runScoring(eq("eval_18_3"), any(), any());
    }

    @Test
    void marksSuccessAndPersistsReportJson() throws Exception {
        PromptTuneExtractionEvalRuns run = newRun(3L, 18L, "running", "[\"default\"]");
        when(evalRunsService.getRequiredById(3L)).thenReturn(run);
        when(evalRunsService.getById(3L)).thenReturn(run);
        when(buildRunsService.getRequiredById(18L)).thenReturn(newBuildRun(18L));
        // mock orchestrator.runScoring：worker 内部清理共享目录后调它，需要重新写 top_candidates.json
        org.mockito.Mockito.doAnswer(inv -> {
            Path sharedReportDir = Path.of(properties.getGraphrag().getRoot())
                    .resolve("results/reports/extraction_scoring/runs/eval_18_3");
            Files.createDirectories(sharedReportDir);
            Files.writeString(sharedReportDir.resolve("top_candidates.json"),
                    "{\"all_candidates_ranked\": [{\"candidate\":\"default\",\"rank\":1}]}");
            return null;
        }).when(orchestrator).runScoring(anyString(), any(), any());

        worker.runInternal(3L);

        // 抓 final updateById 的 entity
        AtomicReference<PromptTuneExtractionEvalRuns> finalState = new AtomicReference<>();
        verify(evalRunsService, atLeastOnce()).updateById(argThat(r -> {
            if ("success".equals(r.getStatus())) {
                finalState.set(r);
                return true;
            }
            return false;
        }));
        assertThat(finalState.get()).isNotNull();
        assertThat(finalState.get().getProgressStage()).isEqualTo("done");
        assertThat(finalState.get().getReportJson()).contains("all_candidates_ranked");
        assertThat(finalState.get().getFinishedAt()).isNotNull();
    }

    @Test
    void marksFailedOnlyWhenAllCandidatesFail() throws Exception {
        // 仅一个候选且抛错 → finished 为空 → 整体 failed（保留原"全失败"语义）
        PromptTuneExtractionEvalRuns run = newRun(3L, 18L, "running", "[\"default\"]");
        when(evalRunsService.getRequiredById(3L)).thenReturn(run);
        when(evalRunsService.getById(3L)).thenReturn(run);
        when(buildRunsService.getRequiredById(18L)).thenReturn(newBuildRun(18L));
        doThrow(new RuntimeException("extract boom"))
                .when(orchestrator).runSingleCandidateExtract(
                        eq("default"), any(), any(), anyString(), anyString(), any()
                );

        worker.runInternal(3L);

        verify(evalRunsService, atLeastOnce()).updateById(argThat(r ->
                "failed".equals(r.getStatus()) && r.getErrorMessage() != null && r.getErrorMessage().contains("全部候选抽取失败")
        ));
        // 不应该尝试跑 scoring
        verify(orchestrator, never()).runScoring(anyString(), any(), any());
    }

    @Test
    void singleCandidateFailureKeepsOverallSuccess() throws Exception {
        // 第一个候选抛错，第二个候选成功 → 整体 success；scoring 仍然跑（决策与风险 1）
        PromptTuneExtractionEvalRuns run = newRun(3L, 18L, "running", "[\"default\",\"auto_tuned\"]");
        when(evalRunsService.getRequiredById(3L)).thenReturn(run);
        when(evalRunsService.getById(3L)).thenReturn(run);
        when(buildRunsService.getRequiredById(18L)).thenReturn(newBuildRun(18L));
        doThrow(new RuntimeException("default boom"))
                .when(orchestrator).runSingleCandidateExtract(
                        eq("default"), any(), any(), anyString(), anyString(), any()
                );
        // auto_tuned 默认 mock 不抛错，记为成功

        worker.runInternal(3L);

        // scoring 依然被调
        verify(orchestrator, times(1)).runScoring(anyString(), any(), any());
        // 整体最终状态 success
        verify(evalRunsService, atLeastOnce()).updateById(argThat(r -> "success".equals(r.getStatus())));
        // 失败原因被结构化写入 candidate_failures（含 candidateId / stage / reason）
        verify(evalRunsService, atLeastOnce()).updateById(argThat(r ->
                r.getCandidateFailures() != null
                        && r.getCandidateFailures().contains("\"candidateId\":\"default\"")
                        && r.getCandidateFailures().contains("\"stage\":\"extract\"")
                        && r.getCandidateFailures().contains("default boom")
        ));
    }

    @Test
    void respectsCancellationBeforeNextCandidate() throws Exception {
        PromptTuneExtractionEvalRuns first = newRun(3L, 18L, "running", "[\"default\",\"auto_tuned\"]");
        // 第二次 query（在第二个候选前 reload）返回 cancelling
        PromptTuneExtractionEvalRuns cancelling = newRun(3L, 18L, "cancelling", "[\"default\",\"auto_tuned\"]");
        when(evalRunsService.getRequiredById(3L))
                .thenReturn(first)        // initial
                .thenReturn(first)        // markRunning 内部 reload
                .thenReturn(first)        // 第一个候选前 query
                .thenReturn(cancelling);  // 第二个候选前 query → 取消
        when(evalRunsService.getById(3L)).thenReturn(first);
        when(buildRunsService.getRequiredById(18L)).thenReturn(newBuildRun(18L));

        worker.runInternal(3L);

        // 只跑了第一个候选
        verify(orchestrator, times(1)).runSingleCandidateExtract(
                eq("default"), any(), any(), anyString(), anyString(), any()
        );
        verify(orchestrator, never()).runSingleCandidateExtract(
                eq("auto_tuned"), any(), any(), anyString(), anyString(), any()
        );
        // 不跑 scoring
        verify(orchestrator, never()).runScoring(anyString(), any(), any());
        // 状态写为 cancelled
        verify(evalRunsService, atLeastOnce()).updateById(argThat(r ->
                "cancelled".equals(r.getStatus())
        ));
    }

    @Test
    void rejectsUnknownCandidateId() throws Exception {
        // selectedCandidateIds 含不在 manifest 白名单的 ID（绕过 Service 门控）
        PromptTuneExtractionEvalRuns run = newRun(3L, 18L, "running", "[\"unknown_x\"]");
        when(evalRunsService.getRequiredById(3L)).thenReturn(run);
        when(evalRunsService.getById(3L)).thenReturn(run);
        when(buildRunsService.getRequiredById(18L)).thenReturn(newBuildRun(18L));

        worker.runInternal(3L);

        verify(orchestrator, never()).runSingleCandidateExtract(any(), any(), any(), anyString(), anyString(), any());
        verify(evalRunsService, atLeastOnce()).updateById(argThat(r ->
                "failed".equals(r.getStatus()) && r.getErrorMessage() != null && r.getErrorMessage().contains("unknown")
        ));
    }

    private static PromptTuneExtractionEvalRuns newRun(Long id, Long buildRunId, String status, String selectedJson) {
        PromptTuneExtractionEvalRuns run = new PromptTuneExtractionEvalRuns();
        run.setId(id);
        run.setBuildRunId(buildRunId);
        run.setKnowledgeBaseId(5L);
        run.setSelectedCandidateIds(selectedJson);
        run.setStatus(status);
        run.setProgressStage("queued");
        return run;
    }

    private static KnowledgeBaseBuildRuns newBuildRun(Long id) {
        KnowledgeBaseBuildRuns r = new KnowledgeBaseBuildRuns();
        r.setId(id);
        r.setKnowledgeBaseId(5L);
        r.setRequestedByUserId(0L);
        r.setWorkspaceUri("user_0/kb_5/build_18");
        return r;
    }
}
