package org.ysu.ckqaback.service;

import com.baomidou.mybatisplus.extension.service.IService;
import org.ysu.ckqaback.entity.KnowledgeBaseBuildRuns;

import java.util.List;
import java.util.Optional;

/**
 * <p>
 * 知识库构建流水线表 服务类
 * </p>
 *
 * @author codex
 * @since 2026-05-05
 */
public interface KnowledgeBaseBuildRunsService extends IService<KnowledgeBaseBuildRuns> {

    KnowledgeBaseBuildRuns getRequiredById(Long id);

    List<KnowledgeBaseBuildRuns> listByKnowledgeBaseId(Long knowledgeBaseId);

    Optional<KnowledgeBaseBuildRuns> findActivePendingOrRunning(Long knowledgeBaseId);

    void clearActiveIndexRunMarkers(Long knowledgeBaseId);
}
