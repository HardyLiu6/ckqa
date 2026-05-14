package org.ysu.ckqaback.index;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.ysu.ckqaback.api.ApiResultCode;
import org.ysu.ckqaback.entity.KnowledgeBaseBuildRuns;
import org.ysu.ckqaback.entity.PromptTuneRuns;
import org.ysu.ckqaback.exception.BusinessException;
import org.ysu.ckqaback.index.dto.PromptTuneRunResponse;
import org.ysu.ckqaback.service.KnowledgeBaseBuildRunsService;
import org.ysu.ckqaback.service.PromptTuneRunsService;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * 自动调优业务编排：
 * <ol>
 *   <li>按 build run 解析 cacheKey；</li>
 *   <li>命中 success 缓存：直接返回；</li>
 *   <li>命中 active 任务：返回当前进度（不允许重复触发）；</li>
 *   <li>否则插一条 pending 记录并交给 worker 异步执行。</li>
 * </ol>
 */
@Service
@RequiredArgsConstructor
public class PromptTuneService {

    private final KnowledgeBaseBuildRunsService buildRunsStore;
    private final PromptTuneRunsService promptTuneRunsService;
    private final PromptTuneCacheKeyResolver cacheKeyResolver;
    private final BuildRunWorkspaceService workspaceService;
    private final PromptTuneWorker worker;
    private final ObjectMapper objectMapper = new ObjectMapper();

    /**
     * 触发或复用 prompt-tune。
     *
     * @param force true 时强制重跑（即使已有 success 缓存也忽略）
     */
    @Transactional
    public PromptTuneRunResponse trigger(Long buildRunId, boolean force) {
        KnowledgeBaseBuildRuns buildRun = buildRunsStore.getRequiredById(buildRunId);
        PromptTuneCacheKeyResolver.PromptTuneCacheContext context =
                cacheKeyResolver.resolve(buildRun.getSelectedMaterialIds(), buildRun.getCourseId());

        if (!force) {
            Optional<PromptTuneRuns> hit = promptTuneRunsService.findLatestSuccessByCacheKey(context.cacheKey());
            if (hit.isPresent()) {
                return PromptTuneRunResponse.fromEntity(hit.get(), true);
            }
        }
        Optional<PromptTuneRuns> active = promptTuneRunsService.findActiveByCacheKey(context.cacheKey());
        if (active.isPresent()) {
            // 复用同一 cacheKey 的运行中任务：不会再起新进程，直接返回它的进度。
            // build_run_id 关联到当前最新触发方，便于前端按 build run 查询。
            attachBuildRun(active.get(), buildRunId);
            return PromptTuneRunResponse.fromEntity(active.get(), false);
        }

        PromptTuneRuns run = createPendingRun(buildRun, context);
        dispatchAfterCommit(run.getId(), context.materialIds());
        return PromptTuneRunResponse.fromEntity(run, false);
    }

    /**
     * 把异步派发推迟到事务提交之后，避免 worker 线程在提交前查不到刚 INSERT 的记录。
     * 在没有事务上下文（比如直接被测试调用）时退化为立即派发。
     */
    private void dispatchAfterCommit(Long promptTuneRunId, List<Long> materialIds) {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            worker.dispatch(promptTuneRunId, materialIds);
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                worker.dispatch(promptTuneRunId, materialIds);
            }
        });
    }

    /**
     * 查询 build run 关联的最新调优状态，用于前端轮询。
     */
    public PromptTuneRunResponse getLatestStatus(Long buildRunId) {
        KnowledgeBaseBuildRuns buildRun = buildRunsStore.getRequiredById(buildRunId);
        PromptTuneCacheKeyResolver.PromptTuneCacheContext context =
                cacheKeyResolver.resolve(buildRun.getSelectedMaterialIds(), buildRun.getCourseId());

        Optional<PromptTuneRuns> active = promptTuneRunsService.findActiveByCacheKey(context.cacheKey());
        if (active.isPresent()) {
            return PromptTuneRunResponse.fromEntity(active.get(), false);
        }
        Optional<PromptTuneRuns> hit = promptTuneRunsService.findLatestSuccessByCacheKey(context.cacheKey());
        if (hit.isPresent()) {
            return PromptTuneRunResponse.fromEntity(hit.get(), true);
        }
        // 还没触发过，前端据此显示"开始生成"按钮。
        return PromptTuneRunResponse.notStarted(buildRunId, context.cacheKey());
    }

    /**
     * 查询当前选材是否已经调优过。前端在策略卡上展示状态徽标。
     * <p>不需要 build run，直接由 selected_material_ids 反查缓存。</p>
     */
    public PromptTuneRunResponse probeBySelection(Long knowledgeBaseId, String courseId, List<Long> materialIds) {
        if (materialIds == null || materialIds.isEmpty()) {
            return PromptTuneRunResponse.notStarted(null, null);
        }
        try {
            String json = objectMapper.writeValueAsString(materialIds);
            PromptTuneCacheKeyResolver.PromptTuneCacheContext context =
                    cacheKeyResolver.resolve(json, courseId);
            Optional<PromptTuneRuns> hit = promptTuneRunsService.findLatestSuccessByCacheKey(context.cacheKey());
            if (hit.isPresent()) {
                return PromptTuneRunResponse.fromEntity(hit.get(), true);
            }
            Optional<PromptTuneRuns> active = promptTuneRunsService.findActiveByCacheKey(context.cacheKey());
            if (active.isPresent()) {
                return PromptTuneRunResponse.fromEntity(active.get(), false);
            }
            return PromptTuneRunResponse.notStarted(null, context.cacheKey());
        } catch (JsonProcessingException exception) {
            throw new BusinessException(
                    ApiResultCode.BAD_REQUEST,
                    HttpStatus.BAD_REQUEST,
                    "资料选择序列化失败"
            );
        }
    }

    /**
     * 查询缓存中是否有可用的 success 记录（供 BuildRunPromptMaterializer 在索引时使用）。
     * <p>同步方法、只读。</p>
     */
    public Optional<PromptTuneRuns> findReadyByCacheKey(String cacheKey) {
        return promptTuneRunsService.findLatestSuccessByCacheKey(cacheKey);
    }

    private PromptTuneRuns createPendingRun(
            KnowledgeBaseBuildRuns buildRun,
            PromptTuneCacheKeyResolver.PromptTuneCacheContext context
    ) {
        LocalDateTime now = LocalDateTime.now();
        PromptTuneRuns run = new PromptTuneRuns();
        run.setKnowledgeBaseId(buildRun.getKnowledgeBaseId());
        run.setBuildRunId(buildRun.getId());
        run.setCourseId(buildRun.getCourseId());
        run.setSelectedMaterialIds(serializeSelectedMaterialIds(context.materialIds()));
        run.setCacheKey(context.cacheKey());
        run.setStatus("pending");
        run.setProgressStage("queued");
        run.setTriggeredByUserId(buildRun.getRequestedByUserId());
        run.setCreatedAt(now);
        run.setUpdatedAt(now);
        promptTuneRunsService.save(run);

        String candidateDirUri = "prompt-tune-cache/" + context.cacheKey() + "/run_" + run.getId();
        run.setCandidateDir(candidateDirUri);
        promptTuneRunsService.updateById(run);

        // 提前创建空目录，防止 worker 在拉资料前抛 NoSuchFileException。
        try {
            workspaceService.resolve(candidateDirUri);
        } catch (RuntimeException ignored) {
            // resolve 失败由 worker 处理；此处只是防御性确认路径合法。
        }
        return run;
    }

    private void attachBuildRun(PromptTuneRuns run, Long buildRunId) {
        if (buildRunId.equals(run.getBuildRunId())) {
            return;
        }
        run.setBuildRunId(buildRunId);
        run.setUpdatedAt(LocalDateTime.now());
        promptTuneRunsService.updateById(run);
    }

    private String serializeSelectedMaterialIds(List<Long> ids) {
        try {
            return objectMapper.writeValueAsString(ids);
        } catch (JsonProcessingException exception) {
            throw new BusinessException(
                    ApiResultCode.INTERNAL_ERROR,
                    HttpStatus.INTERNAL_SERVER_ERROR,
                    "资料选择序列化失败"
            );
        }
    }
}
