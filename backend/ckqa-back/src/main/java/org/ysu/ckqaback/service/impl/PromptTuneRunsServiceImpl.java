package org.ysu.ckqaback.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.ysu.ckqaback.api.ApiResultCode;
import org.ysu.ckqaback.entity.PromptTuneRuns;
import org.ysu.ckqaback.exception.BusinessException;
import org.ysu.ckqaback.mapper.PromptTuneRunsMapper;
import org.ysu.ckqaback.service.PromptTuneRunsService;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * 提示词自动调优运行表 服务实现。
 */
@Service
public class PromptTuneRunsServiceImpl
        extends ServiceImpl<PromptTuneRunsMapper, PromptTuneRuns>
        implements PromptTuneRunsService {

    @Override
    public Optional<PromptTuneRuns> findLatestSuccessByCacheKey(String cacheKey) {
        if (cacheKey == null || cacheKey.isBlank()) {
            return Optional.empty();
        }
        LambdaQueryWrapper<PromptTuneRuns> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(PromptTuneRuns::getCacheKey, cacheKey)
                .eq(PromptTuneRuns::getStatus, "success")
                .orderByDesc(PromptTuneRuns::getCreatedAt)
                .last("LIMIT 1");
        return Optional.ofNullable(getOne(wrapper, false));
    }

    @Override
    public Optional<PromptTuneRuns> findActiveByCacheKey(String cacheKey) {
        if (cacheKey == null || cacheKey.isBlank()) {
            return Optional.empty();
        }
        LambdaQueryWrapper<PromptTuneRuns> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(PromptTuneRuns::getCacheKey, cacheKey)
                .in(PromptTuneRuns::getStatus, "pending", "running")
                .orderByDesc(PromptTuneRuns::getCreatedAt)
                .last("LIMIT 1");
        return Optional.ofNullable(getOne(wrapper, false));
    }

    @Override
    public Optional<PromptTuneRuns> findLatestByBuildRunId(Long buildRunId) {
        if (buildRunId == null) {
            return Optional.empty();
        }
        LambdaQueryWrapper<PromptTuneRuns> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(PromptTuneRuns::getBuildRunId, buildRunId)
                .orderByDesc(PromptTuneRuns::getCreatedAt)
                .last("LIMIT 1");
        return Optional.ofNullable(getOne(wrapper, false));
    }

    @Override
    public List<PromptTuneRuns> listByKnowledgeBaseId(Long knowledgeBaseId) {
        LambdaQueryWrapper<PromptTuneRuns> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(PromptTuneRuns::getKnowledgeBaseId, knowledgeBaseId)
                .orderByDesc(PromptTuneRuns::getCreatedAt);
        return list(wrapper);
    }

    @Override
    public List<PromptTuneRuns> recoverStaleRunningRuns(Duration staleThreshold) {
        LocalDateTime deadline = LocalDateTime.now().minusSeconds(staleThreshold.toSeconds());
        LambdaQueryWrapper<PromptTuneRuns> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(PromptTuneRuns::getStatus, "running")
                .lt(PromptTuneRuns::getUpdatedAt, deadline);
        List<PromptTuneRuns> staleRuns = list(wrapper);
        for (PromptTuneRuns run : staleRuns) {
            LambdaUpdateWrapper<PromptTuneRuns> update = new LambdaUpdateWrapper<>();
            update.eq(PromptTuneRuns::getId, run.getId())
                    .eq(PromptTuneRuns::getStatus, "running")
                    .set(PromptTuneRuns::getStatus, "failed")
                    .set(PromptTuneRuns::getErrorMessage, "调优任务超时未完成，已被启动恢复标记为失败")
                    .setSql("finished_at = NOW()");
            baseMapper.update(null, update);
        }
        return staleRuns;
    }

    @Override
    public List<PromptTuneRuns> recoverInconsistentRunningRuns() {
        // 终态字段（finished_at + error_message 二选一）已写、但 status 仍 running 的记录：
        // 这类肯定是 worker 已经 mark 了 success/failed，但被 tailer 滞后写入覆盖。
        LambdaQueryWrapper<PromptTuneRuns> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(PromptTuneRuns::getStatus, "running")
                .isNotNull(PromptTuneRuns::getFinishedAt);
        List<PromptTuneRuns> stuckRuns = list(wrapper);
        for (PromptTuneRuns run : stuckRuns) {
            LambdaUpdateWrapper<PromptTuneRuns> update = new LambdaUpdateWrapper<>();
            update.eq(PromptTuneRuns::getId, run.getId())
                    .eq(PromptTuneRuns::getStatus, "running")
                    .set(PromptTuneRuns::getStatus, "failed")
                    .set(PromptTuneRuns::getProgressStage, "done")
                    .set(
                            PromptTuneRuns::getErrorMessage,
                            run.getErrorMessage() != null && !run.getErrorMessage().isBlank()
                                    ? run.getErrorMessage()
                                    : "调优结束但状态未及时翻转，已由启动恢复修正"
                    );
            baseMapper.update(null, update);
        }
        return stuckRuns;
    }

    @Override
    public int markRunning(Long id) {
        LambdaUpdateWrapper<PromptTuneRuns> update = new LambdaUpdateWrapper<>();
        update.eq(PromptTuneRuns::getId, id)
                .in(PromptTuneRuns::getStatus, "pending", "running")
                .set(PromptTuneRuns::getStatus, "running")
                .set(PromptTuneRuns::getProgressStage, "queued")
                .setSql("started_at = COALESCE(started_at, NOW())")
                .setSql("last_heartbeat_at = NOW()")
                .setSql("updated_at = NOW()");
        return baseMapper.update(null, update);
    }

    @Override
    public int updateProgressStage(Long id, String stage, String latestLogs) {
        LambdaUpdateWrapper<PromptTuneRuns> update = new LambdaUpdateWrapper<>();
        update.eq(PromptTuneRuns::getId, id)
                .eq(PromptTuneRuns::getStatus, "running")
                .set(PromptTuneRuns::getProgressStage, stage)
                .set(PromptTuneRuns::getLatestLogs, latestLogs)
                .setSql("last_heartbeat_at = NOW()")
                .setSql("updated_at = NOW()");
        return baseMapper.update(null, update);
    }

    @Override
    public int appendLatestLogs(Long id, String latestLogs) {
        LambdaUpdateWrapper<PromptTuneRuns> update = new LambdaUpdateWrapper<>();
        update.eq(PromptTuneRuns::getId, id)
                .eq(PromptTuneRuns::getStatus, "running")
                .set(PromptTuneRuns::getLatestLogs, latestLogs)
                .setSql("last_heartbeat_at = NOW()")
                .setSql("updated_at = NOW()");
        return baseMapper.update(null, update);
    }

    @Override
    public int markSuccess(Long id, String promptSha256, String latestLogs) {
        LambdaUpdateWrapper<PromptTuneRuns> update = new LambdaUpdateWrapper<>();
        update.eq(PromptTuneRuns::getId, id)
                .in(PromptTuneRuns::getStatus, "pending", "running")
                .set(PromptTuneRuns::getStatus, "success")
                .set(PromptTuneRuns::getProgressStage, "done")
                .set(PromptTuneRuns::getPromptSha256, promptSha256)
                .set(PromptTuneRuns::getLatestLogs, latestLogs)
                .setSql("finished_at = NOW()")
                .setSql("last_heartbeat_at = NOW()")
                .setSql("updated_at = NOW()");
        return baseMapper.update(null, update);
    }

    @Override
    public int markFailed(Long id, String errorMessage, String latestLogs) {
        LambdaUpdateWrapper<PromptTuneRuns> update = new LambdaUpdateWrapper<>();
        update.eq(PromptTuneRuns::getId, id)
                .in(PromptTuneRuns::getStatus, "pending", "running")
                .set(PromptTuneRuns::getStatus, "failed")
                .set(PromptTuneRuns::getProgressStage, "done")
                .set(PromptTuneRuns::getErrorMessage, errorMessage)
                .setSql("finished_at = NOW()")
                .setSql("last_heartbeat_at = NOW()")
                .setSql("updated_at = NOW()");
        if (latestLogs != null) {
            update.set(PromptTuneRuns::getLatestLogs, latestLogs);
        }
        return baseMapper.update(null, update);
    }

    @Override
    public PromptTuneRuns getRequiredById(Long id) {
        PromptTuneRuns run = getById(id);
        if (run == null) {
            throw new BusinessException(ApiResultCode.PROMPT_TUNE_RUN_NOT_FOUND, HttpStatus.NOT_FOUND);
        }
        return run;
    }
}
