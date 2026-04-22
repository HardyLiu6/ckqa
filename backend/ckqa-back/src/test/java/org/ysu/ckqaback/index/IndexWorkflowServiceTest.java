package org.ysu.ckqaback.index;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.ysu.ckqaback.entity.IndexRuns;
import org.ysu.ckqaback.entity.KnowledgeBases;
import org.ysu.ckqaback.exception.BusinessException;
import org.ysu.ckqaback.integration.graphrag.GraphRagIndexOrchestrator;
import org.ysu.ckqaback.integration.process.ProcessExecutionResult;
import org.ysu.ckqaback.service.IndexRunsService;
import org.ysu.ckqaback.service.KnowledgeBasesService;

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
}
