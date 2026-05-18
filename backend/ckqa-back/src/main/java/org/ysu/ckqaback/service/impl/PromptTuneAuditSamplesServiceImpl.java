package org.ysu.ckqaback.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.springframework.stereotype.Service;
import org.ysu.ckqaback.entity.PromptTuneAuditSamples;
import org.ysu.ckqaback.mapper.PromptTuneAuditSamplesMapper;
import org.ysu.ckqaback.service.PromptTuneAuditSamplesService;

import java.util.List;

/**
 * 手动调优标注样本 Service 实现。
 */
@Service
public class PromptTuneAuditSamplesServiceImpl
        extends ServiceImpl<PromptTuneAuditSamplesMapper, PromptTuneAuditSamples>
        implements PromptTuneAuditSamplesService {

    @Override
    public List<PromptTuneAuditSamples> listByBuildRunId(Long buildRunId) {
        LambdaQueryWrapper<PromptTuneAuditSamples> wrapper = new LambdaQueryWrapper<>();
        // audit_priority 是 MySQL enum('high','medium','low')，按枚举定义索引值排序：
        // high=1 < medium=2 < low=3，因此用 ASC 实现 "high 优先" 的业务语义。
        wrapper.eq(PromptTuneAuditSamples::getBuildRunId, buildRunId)
                .orderByAsc(PromptTuneAuditSamples::getAuditPriority)
                .orderByAsc(PromptTuneAuditSamples::getSourceSampleId);
        return list(wrapper);
    }

    @Override
    public List<PromptTuneAuditSamples> findCompletedByStableKeys(Long knowledgeBaseId, List<String> stableKeys) {
        if (stableKeys == null || stableKeys.isEmpty()) {
            return List.of();
        }
        LambdaQueryWrapper<PromptTuneAuditSamples> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(PromptTuneAuditSamples::getKnowledgeBaseId, knowledgeBaseId)
                .eq(PromptTuneAuditSamples::getReviewerDecision, "completed")
                .in(PromptTuneAuditSamples::getGoldStableKey, stableKeys);
        return list(wrapper);
    }
}
