package org.ysu.ckqaback.service;

import com.baomidou.mybatisplus.extension.service.IService;
import org.ysu.ckqaback.entity.PromptTuneAuditSamples;

import java.util.List;

/**
 * 手动调优标注样本 Service。
 */
public interface PromptTuneAuditSamplesService extends IService<PromptTuneAuditSamples> {

    /**
     * 按 build run 查询所有标注样本，按 audit_priority 倒序。
     */
    List<PromptTuneAuditSamples> listByBuildRunId(Long buildRunId);

    /**
     * 按 knowledge_base_id + gold_stable_key 查找已完成的历史标注（用于跨构建复用）。
     */
    List<PromptTuneAuditSamples> findCompletedByStableKeys(Long knowledgeBaseId, List<String> stableKeys);
}
