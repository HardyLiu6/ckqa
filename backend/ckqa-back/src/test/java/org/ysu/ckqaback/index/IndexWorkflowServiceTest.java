package org.ysu.ckqaback.index;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.api.Test;
import org.ysu.ckqaback.entity.IndexRuns;
import org.ysu.ckqaback.entity.KnowledgeBases;
import org.ysu.ckqaback.exception.BusinessException;
import org.ysu.ckqaback.index.dto.BuildRunDetailResponse;
import org.ysu.ckqaback.index.dto.BuildRunIndexRequest;
import org.ysu.ckqaback.integration.graphrag.GraphRagIndexOrchestrator;
import org.ysu.ckqaback.integration.process.ProcessExecutionResult;
import org.ysu.ckqaback.service.IndexRunsService;
import org.ysu.ckqaback.service.KnowledgeBasesService;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;

class IndexWorkflowServiceTest {

    @TempDir
    Path tempDir;

    @Test
    void shouldRecoverStaleRunningRunBeforeCreatingNewOne() throws Exception {
        IndexRunsService indexRunsService = mock(IndexRunsService.class);
        KnowledgeBasesService knowledgeBasesService = mock(KnowledgeBasesService.class);
        GraphRagIndexOrchestrator orchestrator = mock(GraphRagIndexOrchestrator.class);

        IndexWorkflowService workflowService = new IndexWorkflowService(
                indexRunsService,
                knowledgeBasesService,
                orchestrator,
                new ObjectMapper(),
                Duration.ofSeconds(2400)
        );

        KnowledgeBases kb = new KnowledgeBases();
        kb.setId(5L);
        kb.setCourseId("os");
        kb.setName("操作系统");

        IndexRuns stale = new IndexRuns();
        stale.setId(12L);
        stale.setKnowledgeBaseId(5L);
        stale.setStatus("running");

        IndexRuns pendingRun = new IndexRuns();
        pendingRun.setId(18L);
        pendingRun.setKnowledgeBaseId(5L);
        pendingRun.setEngine("graphrag");
        pendingRun.setIndexVersion("graphrag-20260421153000");
        pendingRun.setStatus("pending");

        IndexRuns refreshedRun = new IndexRuns();
        refreshedRun.setId(18L);
        refreshedRun.setKnowledgeBaseId(5L);
        refreshedRun.setEngine("graphrag");
        refreshedRun.setIndexVersion("graphrag-20260421153000");
        refreshedRun.setStatus("success");
        refreshedRun.setRunMetadata("{}");

        given(knowledgeBasesService.getRequiredById(5L)).willReturn(kb);
        given(indexRunsService.recoverStaleRunningRuns(5L, Duration.ofSeconds(2400))).willReturn(List.of(stale));
        given(indexRunsService.findActiveRunningByKnowledgeBaseId(5L)).willReturn(Optional.empty());
        given(indexRunsService.createPendingRun(eq(5L), anyString())).willReturn(pendingRun);
        given(orchestrator.fetchInput(pendingRun, kb)).willReturn(successResult(List.of("python", "utils/fetch_from_minio.py")));
        given(orchestrator.runIndex(pendingRun)).willReturn(successResult(List.of("python", "-m", "graphrag", "index", "--root", ".")));
        given(indexRunsService.getRequiredById(18L)).willReturn(refreshedRun);

        workflowService.createIndexRun(5L);

        then(indexRunsService).should().recoverStaleRunningRuns(5L, Duration.ofSeconds(2400));
        then(knowledgeBasesService).should().updateActiveIndexRunId(5L, 18L);
    }

    @Test
    void shouldFailRunWhenFetchInputReturnsNonZeroExitCode() throws Exception {
        IndexRunsService indexRunsService = mock(IndexRunsService.class);
        KnowledgeBasesService knowledgeBasesService = mock(KnowledgeBasesService.class);
        GraphRagIndexOrchestrator orchestrator = mock(GraphRagIndexOrchestrator.class);

        IndexWorkflowService workflowService = new IndexWorkflowService(
                indexRunsService,
                knowledgeBasesService,
                orchestrator,
                new ObjectMapper(),
                Duration.ofSeconds(2400)
        );

        KnowledgeBases kb = new KnowledgeBases();
        kb.setId(5L);
        kb.setCourseId("os");

        IndexRuns run = new IndexRuns();
        run.setId(18L);
        run.setKnowledgeBaseId(5L);
        run.setEngine("graphrag");
        run.setIndexVersion("graphrag-20260421153000");
        run.setStatus("pending");

        given(knowledgeBasesService.getRequiredById(5L)).willReturn(kb);
        given(indexRunsService.recoverStaleRunningRuns(5L, Duration.ofSeconds(2400))).willReturn(List.of());
        given(indexRunsService.findActiveRunningByKnowledgeBaseId(5L)).willReturn(Optional.empty());
        given(indexRunsService.createPendingRun(eq(5L), anyString())).willReturn(run);
        given(orchestrator.fetchInput(run, kb)).willReturn(ProcessExecutionResult.builder()
                .command(List.of("python", "utils/fetch_from_minio.py"))
                .exitCode(2)
                .stdout("")
                .stderr("minio fetch failed")
                .elapsedSeconds(3L)
                .timedOut(false)
                .terminatedByShutdown(false)
                .build());

        assertThatThrownBy(() -> workflowService.createIndexRun(5L))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("索引输入拉取失败");

        then(orchestrator).should(never()).runIndex(any());
        then(indexRunsService).should().markFailed(eq(18L), contains("minio fetch failed"));
    }

    @Test
    void shouldSkipAutoActivationWhenBuildRunIsNotLatest() throws Exception {
        Fixture fixture = buildRunFixture(27L);
        given(fixture.buildRunService.isLatestBuildRun(27L)).willReturn(false);

        fixture.workflowService.createBuildRunIndexRun(27L, new BuildRunIndexRequest());

        then(fixture.activeIndexRunService).should(never()).activate(any(), any(), eq(false));
        then(fixture.indexRunsService).should().markSuccess(eq(18L), contains("skipped_newer_build_exists"));
    }

    @Test
    void shouldMarkBuildRunDoneAndQaSkippedAfterSuccessfulIndex() throws Exception {
        Fixture fixture = buildRunFixture(28L);
        given(fixture.buildRunService.isLatestBuildRun(28L)).willReturn(true);

        fixture.workflowService.createBuildRunIndexRun(28L, new BuildRunIndexRequest());

        then(fixture.activeIndexRunService).should().activate(5L, 18L, false);
        then(fixture.buildRunService).should().markIndexSuccessDone(28L, "skipped");
    }

    @Test
    void shouldCleanIndexInputBeforeCopyingGraphInput() throws Exception {
        Fixture fixture = buildRunFixture(29L);
        Files.createDirectories(fixture.workspace.resolve("index/input"));
        Files.writeString(fixture.workspace.resolve("index/input/stale.json"), "[{\"text\":\"old\"}]");

        fixture.workflowService.createBuildRunIndexRun(29L, new BuildRunIndexRequest());

        org.assertj.core.api.Assertions.assertThat(fixture.workspace.resolve("index/input/stale.json")).doesNotExist();
        org.assertj.core.api.Assertions.assertThat(fixture.workspace.resolve("index/input/material_3.section_docs.json")).exists();
    }

    private ProcessExecutionResult successResult(List<String> command) {
        return ProcessExecutionResult.builder()
                .command(command)
                .exitCode(0)
                .stdout("ok")
                .stderr("")
                .elapsedSeconds(2L)
                .timedOut(false)
                .terminatedByShutdown(false)
                .build();
    }

    private Fixture buildRunFixture(Long buildRunId) throws Exception {
        IndexRunsService indexRunsService = mock(IndexRunsService.class);
        KnowledgeBasesService knowledgeBasesService = mock(KnowledgeBasesService.class);
        GraphRagIndexOrchestrator orchestrator = mock(GraphRagIndexOrchestrator.class);
        KnowledgeBaseBuildRunService buildRunService = mock(KnowledgeBaseBuildRunService.class);
        IndexArtifactRegistryService artifactRegistryService = mock(IndexArtifactRegistryService.class);
        ActiveIndexRunService activeIndexRunService = mock(ActiveIndexRunService.class);
        BuildRunWorkspaceService workspaceService = new BuildRunWorkspaceService(tempDir.toString());

        String workspaceUri = "user_0/kb_5/build_" + buildRunId;
        Path workspace = tempDir.resolve(workspaceUri);
        Files.createDirectories(workspace.resolve("graph-input"));
        Files.writeString(workspace.resolve("graph-input/material_3.section_docs.json"), "[{\"text\":\"ok\"}]");

        KnowledgeBases kb = new KnowledgeBases();
        kb.setId(5L);
        kb.setCourseId("os");

        IndexRuns pendingRun = new IndexRuns();
        pendingRun.setId(18L);
        pendingRun.setKnowledgeBaseId(5L);
        pendingRun.setBuildRunId(buildRunId);
        pendingRun.setEngine("graphrag");
        pendingRun.setIndexVersion("graphrag-20260505150000");
        pendingRun.setStatus("pending");

        IndexRuns successRun = new IndexRuns();
        successRun.setId(18L);
        successRun.setKnowledgeBaseId(5L);
        successRun.setBuildRunId(buildRunId);
        successRun.setEngine("graphrag");
        successRun.setIndexVersion("graphrag-20260505150000");
        successRun.setStatus("success");

        given(buildRunService.getBuildRun(buildRunId)).willReturn(BuildRunDetailResponse.builder()
                .id(buildRunId)
                .knowledgeBaseId(5L)
                .courseId("os")
                .selectedMaterialIds("[3]")
                .workspaceUri(workspaceUri)
                .build());
        given(knowledgeBasesService.getRequiredById(5L)).willReturn(kb);
        given(indexRunsService.recoverStaleRunningRuns(5L, Duration.ofSeconds(2400))).willReturn(List.of());
        given(indexRunsService.findActiveRunningByKnowledgeBaseId(5L)).willReturn(Optional.empty());
        given(indexRunsService.createPendingRun(eq(5L), eq(buildRunId), anyString())).willReturn(pendingRun);
        given(indexRunsService.getRequiredById(18L)).willReturn(successRun);
        given(orchestrator.runIndex(eq(pendingRun), any(Path.class))).willReturn(successResult(List.of("python", "-m", "graphrag", "index", "--root", ".")));

        IndexWorkflowService workflowService = new IndexWorkflowService(
                indexRunsService,
                knowledgeBasesService,
                orchestrator,
                new ObjectMapper(),
                Duration.ofSeconds(2400),
                buildRunService,
                workspaceService,
                artifactRegistryService,
                activeIndexRunService
        );
        return new Fixture(workflowService, indexRunsService, buildRunService, activeIndexRunService, workspace);
    }

    private record Fixture(
            IndexWorkflowService workflowService,
            IndexRunsService indexRunsService,
            KnowledgeBaseBuildRunService buildRunService,
            ActiveIndexRunService activeIndexRunService,
            Path workspace
    ) {
    }
}
