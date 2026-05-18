package org.ysu.ckqaback.index;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.ysu.ckqaback.entity.PromptDrafts;
import org.ysu.ckqaback.index.dto.PromptDraftResponse;
import org.ysu.ckqaback.service.PromptDraftsService;

import java.util.List;

/**
 * 历史草稿列表投影：把 {@link PromptDrafts} entity 转成 {@link PromptDraftResponse} DTO，
 * 按 created_at 倒序（在 service 层已排序）。
 */
@Service
@RequiredArgsConstructor
public class PromptDraftListService {

    private final PromptDraftsService promptDraftsService;

    public List<PromptDraftResponse> list(Long knowledgeBaseId) {
        return promptDraftsService.listByKnowledgeBaseId(knowledgeBaseId).stream()
                .map(this::toResponse)
                .toList();
    }

    private PromptDraftResponse toResponse(PromptDrafts entity) {
        // 注意：Phase 6 把 PromptDraftResponse 重新定义为列表摘要语义，
        // promptsJson 字段已删除（Task 1 Step 6），不再投影；
        // listByKnowledgeBaseId 也已在 mapper 层用 select 排除 prompts_json 列。
        return PromptDraftResponse.builder()
                .id(entity.getId())
                .knowledgeBaseId(entity.getKnowledgeBaseId())
                .name(entity.getName())
                .description(entity.getDescription())
                .seed(entity.getSeed())
                .candidateId(entity.getCandidateId())
                .sourceBuildRunId(entity.getSourceBuildRunId())
                .compositeScore(entity.getCompositeScore())
                .createdAt(entity.getCreatedAt())
                .updatedAt(entity.getUpdatedAt())
                .build();
    }
}
