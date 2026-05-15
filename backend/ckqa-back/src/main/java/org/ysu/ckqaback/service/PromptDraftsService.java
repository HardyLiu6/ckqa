package org.ysu.ckqaback.service;

import com.baomidou.mybatisplus.extension.service.IService;
import org.ysu.ckqaback.entity.PromptDrafts;

import java.util.List;

/**
 * 手动调优历史草稿 Service。
 */
public interface PromptDraftsService extends IService<PromptDrafts> {

    /**
     * 按知识库查询历史草稿，按创建时间倒序。
     */
    List<PromptDrafts> listByKnowledgeBaseId(Long knowledgeBaseId);
}
