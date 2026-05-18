package org.ysu.ckqaback.service;

import com.baomidou.mybatisplus.extension.service.IService;
import org.ysu.ckqaback.entity.PromptDrafts;

import java.util.List;

/**
 * 手动调优历史草稿 Service。
 */
public interface PromptDraftsService extends IService<PromptDrafts> {

    /**
     * 按知识库查询历史草稿（摘要列），按创建时间倒序。
     * <p><b>不</b>带 {@code prompts_json} 列：列表场景只用 name / score / source build / createdAt
     * 等短字段做选择决策；如需正文走未来的详情接口（Phase 7+ 落地）。</p>
     */
    List<PromptDrafts> listByKnowledgeBaseId(Long knowledgeBaseId);

    /**
     * 计数：该 kb 下历史草稿条数。
     * <p>用于 {@code SeedAvailabilityService} 决定 history_draft 种子卡是否可点。</p>
     */
    long countByKnowledgeBaseId(Long knowledgeBaseId);
}
