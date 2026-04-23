package org.ysu.ckqaback.service;

import org.ysu.ckqaback.entity.IndexRuns;
import com.baomidou.mybatisplus.extension.service.IService;

/**
 * <p>
 * 索引运行表 服务类
 * </p>
 *
 * @author codex
 * @since 2026-04-21
 */
public interface IndexRunsService extends IService<IndexRuns> {

    IndexRuns getRequiredById(Long id);

    java.util.Optional<IndexRuns> findActiveRunningByKnowledgeBaseId(Long knowledgeBaseId);

    java.util.List<IndexRuns> listByKnowledgeBaseId(Long knowledgeBaseId);

    java.util.List<IndexRuns> recoverStaleRunningRuns(Long knowledgeBaseId, java.time.Duration staleThreshold);

    java.util.List<IndexRuns> recoverStaleRunningRuns(java.time.Duration staleThreshold);

    IndexRuns createPendingRun(Long knowledgeBaseId, String indexVersion);

    void markRunning(Long id);

    void markSuccess(Long id, String metadataJson);

    void markFailed(Long id, String metadataJson);
}
