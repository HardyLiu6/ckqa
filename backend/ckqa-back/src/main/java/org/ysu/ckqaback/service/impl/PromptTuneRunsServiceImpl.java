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
    public PromptTuneRuns getRequiredById(Long id) {
        PromptTuneRuns run = getById(id);
        if (run == null) {
            throw new BusinessException(ApiResultCode.PROMPT_TUNE_RUN_NOT_FOUND, HttpStatus.NOT_FOUND);
        }
        return run;
    }
}
