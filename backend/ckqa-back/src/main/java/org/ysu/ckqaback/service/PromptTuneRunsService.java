package org.ysu.ckqaback.service;

import com.baomidou.mybatisplus.extension.service.IService;
import org.ysu.ckqaback.entity.PromptTuneRuns;

import java.time.Duration;
import java.util.List;
import java.util.Optional;

/**
 * 提示词自动调优运行表 Service。
 */
public interface PromptTuneRunsService extends IService<PromptTuneRuns> {

    /**
     * 找到与 cacheKey 完全匹配的 success 记录（最新一条）。
     */
    Optional<PromptTuneRuns> findLatestSuccessByCacheKey(String cacheKey);

    /**
     * 找到指定 cacheKey 当前正在 pending / running 的记录。
     */
    Optional<PromptTuneRuns> findActiveByCacheKey(String cacheKey);

    /**
     * 找到指定 build run 关联的最新一条调优记录。
     */
    Optional<PromptTuneRuns> findLatestByBuildRunId(Long buildRunId);

    /**
     * 找到指定知识库的所有调优记录（按创建时间倒序）。
     */
    List<PromptTuneRuns> listByKnowledgeBaseId(Long knowledgeBaseId);

    /**
     * 把超过阈值仍处于 running 状态的记录标 failed，返回被恢复的记录列表。
     */
    List<PromptTuneRuns> recoverStaleRunningRuns(Duration staleThreshold);

    /**
     * 必须能取到 ID 对应的记录，否则抛 NOT_FOUND。
     */
    PromptTuneRuns getRequiredById(Long id);
}
