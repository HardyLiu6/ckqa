package org.ysu.ckqaback.index.dto;

import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 03 步候选提示词响应。
 * <p>
 * 字段来自 {@code graphrag_pipeline/scripts/prompt_tuning/manifest.json}。
 * </p>
 */
@Getter
@Builder
public class CandidateResponse {

    /** 稳定标识符，如 schema_fewshot_distilled_v2_strict_tuple。 */
    private final String candidateId;

    /** 中文译名；后端优先从 manifest 读取，缺失时前端有 hardcode fallback。 */
    private final String displayNameZh;

    /** baseline / auto_tuned / schema_aware / schema_fewshot。 */
    private final String category;

    /** 是否为推荐候选（manifest.notes 标注或上一次评分历史决定）。 */
    private final Boolean isRecommended;

    /** 特性标签，如 ["schema_injected", "directional_card", "few_shot_distilled"]。 */
    private final List<String> traits;

    private final Integer estimatedTokenPerCall;
    private final Integer promptSizeBytes;
    private final String schemaUsed;
    private final Integer fewshotExampleCount;
    private final String fewshotStrategy;
    private final String basePromptSource;
    private final LocalDateTime generationTime;
}
