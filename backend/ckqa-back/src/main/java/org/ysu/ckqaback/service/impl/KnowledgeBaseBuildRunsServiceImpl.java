package org.ysu.ckqaback.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.ysu.ckqaback.api.ApiResultCode;
import org.ysu.ckqaback.entity.KnowledgeBaseBuildRuns;
import org.ysu.ckqaback.exception.BusinessException;
import org.ysu.ckqaback.mapper.KnowledgeBaseBuildRunsMapper;
import org.ysu.ckqaback.service.KnowledgeBaseBuildRunsService;

import java.util.List;
import java.util.Optional;

/**
 * <p>
 * 知识库构建流水线表 服务实现类
 * </p>
 *
 * @author codex
 * @since 2026-05-05
 */
@Service
public class KnowledgeBaseBuildRunsServiceImpl
        extends ServiceImpl<KnowledgeBaseBuildRunsMapper, KnowledgeBaseBuildRuns>
        implements KnowledgeBaseBuildRunsService {

    @Override
    public KnowledgeBaseBuildRuns getRequiredById(Long id) {
        KnowledgeBaseBuildRuns buildRun = getById(id);
        if (buildRun == null) {
            throw new BusinessException(ApiResultCode.KNOWLEDGE_BASE_BUILD_RUN_NOT_FOUND, HttpStatus.NOT_FOUND);
        }
        return buildRun;
    }

    @Override
    public List<KnowledgeBaseBuildRuns> listByKnowledgeBaseId(Long knowledgeBaseId) {
        LambdaQueryWrapper<KnowledgeBaseBuildRuns> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(KnowledgeBaseBuildRuns::getKnowledgeBaseId, knowledgeBaseId)
                .orderByDesc(KnowledgeBaseBuildRuns::getCreatedAt);
        return list(queryWrapper);
    }

    @Override
    public Optional<KnowledgeBaseBuildRuns> findActivePendingOrRunning(Long knowledgeBaseId) {
        LambdaQueryWrapper<KnowledgeBaseBuildRuns> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(KnowledgeBaseBuildRuns::getKnowledgeBaseId, knowledgeBaseId)
                .in(KnowledgeBaseBuildRuns::getStatus, "pending", "running")
                .orderByDesc(KnowledgeBaseBuildRuns::getCreatedAt)
                .last("LIMIT 1");
        return Optional.ofNullable(getOne(queryWrapper, false));
    }

    @Override
    public void clearActiveIndexRunMarkers(Long knowledgeBaseId) {
        LambdaUpdateWrapper<KnowledgeBaseBuildRuns> wrapper = new LambdaUpdateWrapper<>();
        wrapper.eq(KnowledgeBaseBuildRuns::getKnowledgeBaseId, knowledgeBaseId)
                .isNotNull(KnowledgeBaseBuildRuns::getActiveIndexRunId)
                .set(KnowledgeBaseBuildRuns::getActiveIndexRunId, null);
        baseMapper.update(null, wrapper);
    }
}
