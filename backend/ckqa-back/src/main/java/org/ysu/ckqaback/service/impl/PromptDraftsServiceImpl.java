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
        LambdaQueryWrapper<PromptDrafts> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(PromptDrafts::getKnowledgeBaseId, knowledgeBaseId)
                .orderByDesc(PromptDrafts::getCreatedAt);
        return list(wrapper);
    }
}
