package org.ysu.ckqaback.index.dto;

import lombok.Builder;
import lombok.Getter;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * GET /knowledge-bases/{kbId}/prompt-drafts 历史草稿<b>摘要</b>响应。
 * <p>本期作为列表场景使用，不含 {@code promptsJson} 正文（30 KB × N 条会让列表响应膨胀到 600 KB+）。
 * 如需读取草稿正文，留 Phase 7+ 新增详情接口承担。</p>
 */
@Getter
@Builder
public class PromptDraftResponse {

    private final Long id;
    private final Long knowledgeBaseId;
    private final String name;
    private final String description;

    /** system_default / graphrag_tuned / prompt_draft:N。 */
    private final String seed;
    private final String candidateId;

    // 注意：Phase 6 把 PromptDraftResponse 重新定义为列表摘要语义。
    // promptsJson 字段已删除，避免列表响应携带 30 KB × N 条正文。
    // 详情接口将在 Phase 7+ 新增（GET /knowledge-bases/{kbId}/prompt-drafts/{id}）并定义 PromptDraftDetailResponse。

    private final Long sourceBuildRunId;
    private final BigDecimal compositeScore;
    private final LocalDateTime createdAt;
    private final LocalDateTime updatedAt;
}
