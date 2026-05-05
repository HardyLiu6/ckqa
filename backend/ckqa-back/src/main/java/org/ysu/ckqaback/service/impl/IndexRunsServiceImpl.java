package org.ysu.ckqaback.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.ysu.ckqaback.entity.IndexRuns;
import org.ysu.ckqaback.api.ApiResultCode;
import org.ysu.ckqaback.exception.BusinessException;
import org.ysu.ckqaback.integration.graphrag.IndexRunMetadata;
import org.ysu.ckqaback.mapper.IndexRunsMapper;
import org.ysu.ckqaback.service.IndexRunsService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * <p>
 * 索引运行表 服务实现类
 * </p>
 *
 * @author codex
 * @since 2026-04-21
 */
@Service
public class IndexRunsServiceImpl extends ServiceImpl<IndexRunsMapper, IndexRuns> implements IndexRunsService {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Override
    public IndexRuns getRequiredById(Long id) {
        IndexRuns indexRun = getById(id);
        if (indexRun == null) {
            throw new BusinessException(ApiResultCode.INDEX_RUN_NOT_FOUND, HttpStatus.NOT_FOUND);
        }
        return indexRun;
    }

    @Override
    public Optional<IndexRuns> findActiveRunningByKnowledgeBaseId(Long knowledgeBaseId) {
        LambdaQueryWrapper<IndexRuns> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(IndexRuns::getKnowledgeBaseId, knowledgeBaseId)
                .eq(IndexRuns::getStatus, "running")
                .orderByDesc(IndexRuns::getCreatedAt)
                .last("LIMIT 1");
        return Optional.ofNullable(getOne(queryWrapper, false));
    }

    @Override
    public List<IndexRuns> listByKnowledgeBaseId(Long knowledgeBaseId) {
        LambdaQueryWrapper<IndexRuns> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(IndexRuns::getKnowledgeBaseId, knowledgeBaseId)
                .orderByDesc(IndexRuns::getCreatedAt);
        return list(queryWrapper);
    }

    @Override
    public List<IndexRuns> recoverStaleRunningRuns(Long knowledgeBaseId, Duration staleThreshold) {
        LocalDateTime deadline = LocalDateTime.now().minusSeconds(staleThreshold.toSeconds());
        LambdaQueryWrapper<IndexRuns> queryWrapper = staleRunningQuery(deadline);
        queryWrapper.eq(IndexRuns::getKnowledgeBaseId, knowledgeBaseId);
        List<IndexRuns> staleRuns = list(queryWrapper);
        staleRuns.forEach(run -> markFailed(run.getId(), staleMetadataJson()));
        return staleRuns;
    }

    @Override
    public List<IndexRuns> recoverStaleRunningRuns(Duration staleThreshold) {
        LocalDateTime deadline = LocalDateTime.now().minusSeconds(staleThreshold.toSeconds());
        List<IndexRuns> staleRuns = list(staleRunningQuery(deadline));
        staleRuns.forEach(run -> markFailed(run.getId(), staleMetadataJson()));
        return staleRuns;
    }

    @Override
    public IndexRuns createPendingRun(Long knowledgeBaseId, String indexVersion) {
        return createPendingRun(knowledgeBaseId, null, indexVersion);
    }

    @Override
    public IndexRuns createPendingRun(Long knowledgeBaseId, Long buildRunId, String indexVersion) {
        IndexRuns indexRun = new IndexRuns();
        indexRun.setKnowledgeBaseId(knowledgeBaseId);
        indexRun.setBuildRunId(buildRunId);
        indexRun.setEngine("graphrag");
        indexRun.setIndexVersion(indexVersion);
        indexRun.setStatus("pending");
        save(indexRun);
        return indexRun;
    }

    @Override
    public void markRunning(Long id) {
        LambdaUpdateWrapper<IndexRuns> wrapper = new LambdaUpdateWrapper<>();
        wrapper.eq(IndexRuns::getId, id)
                .eq(IndexRuns::getStatus, "pending")
                .set(IndexRuns::getStatus, "running")
                .setSql("started_at = NOW(), finished_at = NULL");
        baseMapper.update(null, wrapper);
    }

    @Override
    public void markSuccess(Long id, String metadataJson) {
        LambdaUpdateWrapper<IndexRuns> wrapper = new LambdaUpdateWrapper<>();
        wrapper.eq(IndexRuns::getId, id)
                .set(IndexRuns::getStatus, "success")
                .set(IndexRuns::getRunMetadata, metadataJson)
                .setSql("started_at = IFNULL(started_at, NOW()), finished_at = NOW()");
        baseMapper.update(null, wrapper);
    }

    @Override
    public void markFailed(Long id, String metadataJson) {
        LambdaUpdateWrapper<IndexRuns> wrapper = new LambdaUpdateWrapper<>();
        wrapper.eq(IndexRuns::getId, id)
                .set(IndexRuns::getStatus, "failed")
                .set(IndexRuns::getRunMetadata, metadataJson)
                .setSql("started_at = IFNULL(started_at, NOW()), finished_at = NOW()");
        baseMapper.update(null, wrapper);
    }

    private LambdaQueryWrapper<IndexRuns> staleRunningQuery(LocalDateTime deadline) {
        LambdaQueryWrapper<IndexRuns> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(IndexRuns::getStatus, "running")
                .and(wrapper -> wrapper.isNull(IndexRuns::getStartedAt).or().lt(IndexRuns::getStartedAt, deadline))
                .orderByAsc(IndexRuns::getStartedAt);
        return queryWrapper;
    }

    private String staleMetadataJson() {
        try {
            return objectMapper.writeValueAsString(IndexRunMetadata.builder()
                    .command("stale-recovery")
                    .elapsedSeconds(null)
                    .exitCode(null)
                    .errorSummary("索引任务超过陈旧阈值，已自动恢复为失败状态")
                    .staleTimeoutRecovered(true)
                    .build());
        } catch (Exception exception) {
            return "{\"command\":\"stale-recovery\",\"errorSummary\":\"索引任务超过陈旧阈值，已自动恢复为失败状态\",\"staleTimeoutRecovered\":true}";
        }
    }
}
