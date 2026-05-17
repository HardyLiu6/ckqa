package org.ysu.ckqaback.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.springframework.stereotype.Service;
import org.ysu.ckqaback.entity.PromptDrafts;
import org.ysu.ckqaback.mapper.PromptDraftsMapper;
import org.ysu.ckqaback.service.PromptDraftsService;

import java.util.List;

/**
 * 手动调优历史草稿 Service 实现。
 */
@Service
public class PromptDraftsServiceImpl
        extends ServiceImpl<PromptDraftsMapper, PromptDrafts>
        implements PromptDraftsService {

    @Override
    public List<PromptDrafts> listByKnowledgeBaseId(Long knowledgeBaseId) {
        // 摘要查询：显式 select 列，排除 prompts_json（30 KB × N 条会让列表响应膨胀到 600 KB+）。
        // 详情接口留 Phase 7+ 时新增 GET /knowledge-bases/{kbId}/prompt-drafts/{id}。
        LambdaQueryWrapper<PromptDrafts> wrapper = new LambdaQueryWrapper<>();
        wrapper.select(
                PromptDrafts::getId,
                PromptDrafts::getKnowledgeBaseId,
                PromptDrafts::getName,
                PromptDrafts::getDescription,
                PromptDrafts::getSeed,
                PromptDrafts::getCandidateId,
                PromptDrafts::getSourceBuildRunId,
                PromptDrafts::getCompositeScore,
                PromptDrafts::getCreatedAt,
                PromptDrafts::getUpdatedAt
        );
        wrapper.eq(PromptDrafts::getKnowledgeBaseId, knowledgeBaseId)
                .orderByDesc(PromptDrafts::getCreatedAt);
        return list(wrapper);
    }

    @Override
    public long countByKnowledgeBaseId(Long knowledgeBaseId) {
        LambdaQueryWrapper<PromptDrafts> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(PromptDrafts::getKnowledgeBaseId, knowledgeBaseId);
        return count(wrapper);
    }
}
