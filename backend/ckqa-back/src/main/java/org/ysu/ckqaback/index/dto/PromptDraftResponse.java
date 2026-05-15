package org.ysu.ckqaback.index.dto;

import lombok.Builder;
import lombok.Getter;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * GET /knowledge-bases/{kbId}/prompt-drafts 历史草稿响应。
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

    /** 多 key prompt 内容快照，JSON 字符串形态（前端按需解析）。 */
    private final String promptsJson;

    private final Long sourceBuildRunId;
    private final BigDecimal compositeScore;
    private final LocalDateTime createdAt;
    private final LocalDateTime updatedAt;
}
