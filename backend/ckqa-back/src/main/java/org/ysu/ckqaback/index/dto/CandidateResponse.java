package org.ysu.ckqaback.index.dto;

import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 03 步候选提示词响应。
 * <p>
 * 字段来源混合：算法产物字段从 {@code graphrag_pipeline/prompts/candidates/manifest.json} 透传，
 * 前端展示层字段（displayNameZh / description / isRecommended / traits / category）由
 * {@code CandidateMetadataLookup} 后端硬编码注入。
 * </p>
 */
@Getter
@Builder
public class CandidateResponse {

    /** 稳定标识符，如 schema_fewshot_distilled_v2_strict_tuple。 */
    private final String candidateId;

    /** 中文译名（后端硬编码 Map）。 */
    private final String displayNameZh;

    /** baseline / auto_tuned / schema_aware / schema_fewshot（后端硬编码 Map）。 */
    private final String category;

    /** 一句话描述（如 "基线 · 课程域微调"），后端硬编码 Map。 */
    private final String description;

    /** 是否为推荐候选（manifest.notes 标注或上一次评分历史决定，本期硬编码）。 */
    private final Boolean isRecommended;

    /** 特性标签数组，后端硬编码 Map<id, List<TraitInfo>>。 */
    private final List<TraitInfo> traits;

    private final Integer estimatedTokenPerCall;
    private final Integer promptSizeBytes;
    private final Boolean schemaUsed;
    private final Integer fewshotExampleCount;
    private final String fewshotStrategy;
    private final String basePromptSource;
    private final LocalDateTime generationTime;

    @Getter
    @Builder
    public static class TraitInfo {
        /** 稳定 key，用于前端渲染 chip 时取色等。 */
        private final String key;
        /** 中文 label，前端直接显示。 */
        private final String label;
    }
}
