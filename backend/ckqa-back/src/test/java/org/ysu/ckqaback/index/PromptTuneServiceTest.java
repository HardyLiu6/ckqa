package org.ysu.ckqaback.index;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.ysu.ckqaback.entity.KnowledgeBaseBuildRuns;
import org.ysu.ckqaback.entity.PromptTuneRuns;
import org.ysu.ckqaback.index.dto.PromptTuneRunResponse;
import org.ysu.ckqaback.service.KnowledgeBaseBuildRunsService;
import org.ysu.ckqaback.service.PromptTuneRunsService;

import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;

class PromptTuneServiceTest {

    @TempDir
    Path tempDir;

    private KnowledgeBaseBuildRunsService buildRunsStore;
    private PromptTuneRunsService promptTuneRunsService;
    private PromptTuneCacheKeyResolver cacheKeyResolver;
    private BuildRunWorkspaceService workspaceService;
    private PromptTuneWorker worker;

    private PromptTuneService service;

    @BeforeEach
    void setUp() {
        buildRunsStore = mock(KnowledgeBaseBuildRunsService.class);
        promptTuneRunsService = mock(PromptTuneRunsService.class);
        cacheKeyResolver = mock(PromptTuneCacheKeyResolver.class);
        workspaceService = new BuildRunWorkspaceService(tempDir.toString());
        worker = mock(PromptTuneWorker.class);

        service = new PromptTuneService(
                buildRunsStore,
                promptTuneRunsService,
                cacheKeyResolver,
                workspaceService,
                worker
        );
    }

    @Test
    void trigger_cacheHit_returnsExistingRunWithoutDispatch() {
        KnowledgeBaseBuildRuns buildRun = newBuildRun(18L, "[7]");
        given(buildRunsStore.getRequiredById(18L)).willReturn(buildRun);
        var ctx = new PromptTuneCacheKeyResolver.PromptTuneCacheContext(
                List.of(7L),
                java.util.Map.of(7L, "md5_a"),
                "deadbeef"
        );
        given(cacheKeyResolver.resolve("[7]", "os")).willReturn(ctx);

        PromptTuneRuns success = newSuccessRun(99L, "deadbeef");
        given(promptTuneRunsService.findLatestSuccessByCacheKey("deadbeef")).willReturn(Optional.of(success));

        PromptTuneRunResponse response = service.trigger(18L, false);

        assertThat(response.getId()).isEqualTo(99L);
        assertThat(response.getCacheHit()).isTrue();
        assertThat(response.getStatus()).isEqualTo("success");
        then(worker).should(never()).dispatch(anyLong(), anyList());
        then(promptTuneRunsService).should(never()).save(any());
    }

    @Test
    void trigger_runningTask_returnsActiveStatusAndRebindsBuildRun() {
        KnowledgeBaseBuildRuns buildRun = newBuildRun(19L, "[7]");
        given(buildRunsStore.getRequiredById(19L)).willReturn(buildRun);
        var ctx = new PromptTuneCacheKeyResolver.PromptTuneCacheContext(
                List.of(7L),
                java.util.Map.of(7L, "md5_a"),
                "key_active"
        );
        given(cacheKeyResolver.resolve("[7]", "os")).willReturn(ctx);
        given(promptTuneRunsService.findLatestSuccessByCacheKey("key_active")).willReturn(Optional.empty());

        PromptTuneRuns active = newRunningRun(50L, "key_active", 17L);
        given(promptTuneRunsService.findActiveByCacheKey("key_active")).willReturn(Optional.of(active));

        PromptTuneRunResponse response = service.trigger(19L, false);

        assertThat(response.getId()).isEqualTo(50L);
        assertThat(response.getStatus()).isEqualTo("running");
        assertThat(response.getCacheHit()).isFalse();
        then(worker).should(never()).dispatch(anyLong(), anyList());
        then(promptTuneRunsService).should().updateById(active);
        assertThat(active.getBuildRunId()).isEqualTo(19L);
    }

    @Test
    void trigger_freshRun_savesPendingAndDispatches() {
        KnowledgeBaseBuildRuns buildRun = newBuildRun(20L, "[7,8]");
        given(buildRunsStore.getRequiredById(20L)).willReturn(buildRun);
        var ctx = new PromptTuneCacheKeyResolver.PromptTuneCacheContext(
                List.of(7L, 8L),
                java.util.Map.of(7L, "md5_a", 8L, "md5_b"),
                "fresh_key"
        );
        given(cacheKeyResolver.resolve("[7,8]", "os")).willReturn(ctx);
        given(promptTuneRunsService.findLatestSuccessByCacheKey("fresh_key")).willReturn(Optional.empty());
        given(promptTuneRunsService.findActiveByCacheKey("fresh_key")).willReturn(Optional.empty());
        given(promptTuneRunsService.save(any(PromptTuneRuns.class))).willAnswer(invocation -> {
            PromptTuneRuns saved = invocation.getArgument(0);
            saved.setId(120L);
            return true;
        });

        PromptTuneRunResponse response = service.trigger(20L, false);

        assertThat(response.getId()).isEqualTo(120L);
        assertThat(response.getStatus()).isEqualTo("pending");
        assertThat(response.getCacheHit()).isFalse();
        then(worker).should().dispatch(120L, List.of(7L, 8L));
    }

    @Test
    void trigger_forceTrue_ignoresCacheHit() {
        KnowledgeBaseBuildRuns buildRun = newBuildRun(21L, "[7]");
        given(buildRunsStore.getRequiredById(21L)).willReturn(buildRun);
        var ctx = new PromptTuneCacheKeyResolver.PromptTuneCacheContext(
                List.of(7L),
                java.util.Map.of(7L, "md5_a"),
                "force_key"
        );
        given(cacheKeyResolver.resolve("[7]", "os")).willReturn(ctx);
        given(promptTuneRunsService.findActiveByCacheKey("force_key")).willReturn(Optional.empty());
        given(promptTuneRunsService.save(any(PromptTuneRuns.class))).willAnswer(invocation -> {
            PromptTuneRuns saved = invocation.getArgument(0);
            saved.setId(200L);
            return true;
        });

        PromptTuneRunResponse response = service.trigger(21L, true);

        assertThat(response.getId()).isEqualTo(200L);
        assertThat(response.getStatus()).isEqualTo("pending");
        // findLatestSuccessByCacheKey 不应该被调用，因为 force=true 直接跳过缓存检查
        then(promptTuneRunsService).should(never()).findLatestSuccessByCacheKey("force_key");
        then(worker).should().dispatch(200L, List.of(7L));
    }

    @Test
    void getLatestStatus_runningPriorityOverSuccess() {
        // 即使 cache 中已有 success，但当前正在 running 时也应优先返回 running 状态。
        KnowledgeBaseBuildRuns buildRun = newBuildRun(22L, "[7]");
        given(buildRunsStore.getRequiredById(22L)).willReturn(buildRun);
        var ctx = new PromptTuneCacheKeyResolver.PromptTuneCacheContext(
                List.of(7L),
                java.util.Map.of(7L, "md5_a"),
                "status_key"
        );
        given(cacheKeyResolver.resolve("[7]", "os")).willReturn(ctx);
        given(promptTuneRunsService.findActiveByCacheKey("status_key"))
                .willReturn(Optional.of(newRunningRun(75L, "status_key", 22L)));

        PromptTuneRunResponse response = service.getLatestStatus(22L);

        assertThat(response.getStatus()).isEqualTo("running");
        assertThat(response.getId()).isEqualTo(75L);
    }

    @Test
    void getLatestStatus_notStarted_returnsIdleStatus() {
        KnowledgeBaseBuildRuns buildRun = newBuildRun(23L, "[7]");
        given(buildRunsStore.getRequiredById(23L)).willReturn(buildRun);
        var ctx = new PromptTuneCacheKeyResolver.PromptTuneCacheContext(
                List.of(7L),
                java.util.Map.of(7L, "md5_a"),
                "idle_key"
        );
        given(cacheKeyResolver.resolve("[7]", "os")).willReturn(ctx);
        given(promptTuneRunsService.findActiveByCacheKey("idle_key")).willReturn(Optional.empty());
        given(promptTuneRunsService.findLatestSuccessByCacheKey("idle_key")).willReturn(Optional.empty());

        PromptTuneRunResponse response = service.getLatestStatus(23L);

        assertThat(response.getStatus()).isEqualTo("not_started");
        assertThat(response.getCacheHit()).isFalse();
        assertThat(response.getCacheKey()).isEqualTo("idle_key");
    }

    @Test
    void trigger_freshRun_dispatchesOnlyAfterTransactionCommit() {
        // 回归测试：worker.dispatch 必须等事务提交后再执行，否则在 worker 线程里查不到刚插入的记录。
        KnowledgeBaseBuildRuns buildRun = newBuildRun(99L, "[7]");
        given(buildRunsStore.getRequiredById(99L)).willReturn(buildRun);
        var ctx = new PromptTuneCacheKeyResolver.PromptTuneCacheContext(
                List.of(7L),
                java.util.Map.of(7L, "md5_a"),
                "txn_key"
        );
        given(cacheKeyResolver.resolve("[7]", "os")).willReturn(ctx);
        given(promptTuneRunsService.findLatestSuccessByCacheKey("txn_key")).willReturn(Optional.empty());
        given(promptTuneRunsService.findActiveByCacheKey("txn_key")).willReturn(Optional.empty());
        given(promptTuneRunsService.save(any(PromptTuneRuns.class))).willAnswer(invocation -> {
            PromptTuneRuns saved = invocation.getArgument(0);
            saved.setId(321L);
            return true;
        });

        java.util.List<org.springframework.transaction.support.TransactionSynchronization> registered = new java.util.ArrayList<>();
        org.springframework.transaction.support.TransactionSynchronizationManager.initSynchronization();
        try {
            // 注册一个 mock 同步器拦截器：触发 service 时，service 自己注册的 synchronization 会写到当前线程的 List 中。
            // 我们直接读 TransactionSynchronizationManager.getSynchronizations()。
            service.trigger(99L, false);

            // 关键断言：trigger 返回时，worker.dispatch 不应被立即调用
            then(worker).should(never()).dispatch(anyLong(), anyList());

            registered.addAll(org.springframework.transaction.support.TransactionSynchronizationManager.getSynchronizations());
            org.assertj.core.api.Assertions.assertThat(registered)
                    .as("trigger 必须注册 afterCommit 回调，把 dispatch 推迟到事务提交后")
                    .isNotEmpty();

            // 模拟事务提交：执行所有 afterCommit 回调
            for (var sync : registered) {
                sync.afterCommit();
            }

            // 提交后才应触发 dispatch
            then(worker).should().dispatch(321L, List.of(7L));
        } finally {
            org.springframework.transaction.support.TransactionSynchronizationManager.clearSynchronization();
        }
    }

    private KnowledgeBaseBuildRuns newBuildRun(Long id, String selectedMaterialIds) {
        KnowledgeBaseBuildRuns buildRun = new KnowledgeBaseBuildRuns();
        buildRun.setId(id);
        buildRun.setKnowledgeBaseId(5L);
        buildRun.setCourseId("os");
        buildRun.setSelectedMaterialIds(selectedMaterialIds);
        return buildRun;
    }

    private PromptTuneRuns newSuccessRun(Long id, String cacheKey) {
        PromptTuneRuns run = new PromptTuneRuns();
        run.setId(id);
        run.setKnowledgeBaseId(5L);
        run.setCacheKey(cacheKey);
        run.setStatus("success");
        run.setProgressStage("done");
        run.setCandidateDir("prompt-tune-cache/" + cacheKey + "/run_" + id);
        run.setPromptSha256("sha256:abcd");
        run.setFinishedAt(LocalDateTime.now());
        return run;
    }

    private PromptTuneRuns newRunningRun(Long id, String cacheKey, Long buildRunId) {
        PromptTuneRuns run = new PromptTuneRuns();
        run.setId(id);
        run.setKnowledgeBaseId(5L);
        run.setBuildRunId(buildRunId);
        run.setCacheKey(cacheKey);
        run.setStatus("running");
        run.setProgressStage("prompt_tune");
        run.setCandidateDir("prompt-tune-cache/" + cacheKey + "/run_" + id);
        run.setStartedAt(LocalDateTime.now().minusMinutes(2));
        run.setLastHeartbeatAt(LocalDateTime.now());
        return run;
    }
}
