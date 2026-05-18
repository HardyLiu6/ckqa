package org.ysu.ckqaback.index;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.DefaultApplicationArguments;
import org.ysu.ckqaback.entity.PromptTuneExtractionEvalRuns;
import org.ysu.ckqaback.service.PromptTuneExtractionEvalRunsService;

import java.time.LocalDateTime;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ExtractionEvalStartupRecoveryTest {

    private PromptTuneExtractionEvalRunsService evalRunsService;
    private ExtractionEvalWorker worker;
    private ExtractionEvalStartupRecovery recovery;

    @BeforeEach
    void setUp() {
        evalRunsService = mock(PromptTuneExtractionEvalRunsService.class);
        worker = mock(ExtractionEvalWorker.class);
        recovery = new ExtractionEvalStartupRecovery(evalRunsService, worker);
    }

    @Test
    void redispatchesPendingTasks() {
        PromptTuneExtractionEvalRuns pending = newRun(5L, "pending");
        when(evalRunsService.listAllActive()).thenReturn(List.of(pending));

        recovery.run(new DefaultApplicationArguments());

        // pending 不动 DB，由 worker.dispatch 重新派发
        verify(worker).dispatch(eq(5L));
        verify(evalRunsService, never()).updateById(any());
    }

    @Test
    void marksRunningTasksAsFailed() {
        PromptTuneExtractionEvalRuns running = newRun(7L, "running");
        running.setLastHeartbeatAt(LocalDateTime.now().minusMinutes(10));
        when(evalRunsService.listAllActive()).thenReturn(List.of(running));

        recovery.run(new DefaultApplicationArguments());

        verify(worker, never()).dispatch(any());
        verify(evalRunsService).updateById(argThat(r ->
                "failed".equals(r.getStatus())
                        && r.getErrorMessage() != null
                        && r.getErrorMessage().contains("服务重启")
                        && r.getFinishedAt() != null
        ));
    }

    @Test
    void marksCancellingTasksAsCancelled() {
        // 用户已请求取消但 worker 没来得及收尾就被服务重启 → 直接落 cancelled
        PromptTuneExtractionEvalRuns cancelling = newRun(9L, "cancelling");
        when(evalRunsService.listAllActive()).thenReturn(List.of(cancelling));

        recovery.run(new DefaultApplicationArguments());

        verify(worker, never()).dispatch(any());
        verify(evalRunsService).updateById(argThat(r ->
                "cancelled".equals(r.getStatus())
                        && r.getFinishedAt() != null
        ));
    }

    @Test
    void doesNothingWhenNoActiveTasks() {
        when(evalRunsService.listAllActive()).thenReturn(List.of());
        recovery.run(new DefaultApplicationArguments());
        verify(worker, never()).dispatch(any());
        verify(evalRunsService, never()).updateById(any());
    }

    private static PromptTuneExtractionEvalRuns newRun(Long id, String status) {
        PromptTuneExtractionEvalRuns r = new PromptTuneExtractionEvalRuns();
        r.setId(id);
        r.setStatus(status);
        return r;
    }
}
