package org.ysu.ckqaback.index.dto;

import lombok.Builder;
import lombok.Getter;

import java.util.List;
import java.util.Map;

/**
 * AI 候选生成响应。
 * <p>
 * 候选实体/关系由 GraphRAG 单样本抽取得到。前端把它们落到 sample.aiSuggestedEntities /
 * aiSuggestedRelations，等待用户**逐条审阅**——spec § 风险 #1 明确禁止"全部采纳"操作。
 * </p>
 */
@Getter
@Builder
public class AiSuggestionResponse {

    /**
     * 候选实体列表，每项含：
     * <ul>
     *   <li>{@code name} / {@code type} / {@code description} / {@code confidence}（GraphRAG 透传）</li>
     *   <li>{@code suggestionSource}: {@code "ai_suggested"}（service 添加，标记候选来源；不与
     *       {@code source/target} 这种关系领域字段冲突）</li>
     * </ul>
     */
    private final List<Map<String, Object>> entities;

    /**
     * 候选关系列表，每项含：
     * <ul>
     *   <li>{@code originalSource}/{@code originalTarget}（实体名字符串；从 GraphRAG 的 source/target 改名而来）</li>
     *   <li>{@code type} / {@code description} / {@code evidence} / {@code confidence}</li>
     *   <li>{@code suggestionSource}: {@code "ai_suggested"}</li>
     * </ul>
     * 前端采纳时优先查 {@code aiEntityNameToGoldId} 映射表把 originalSource/originalTarget
     * 解析为 sample.goldEntities 中的 entity id。
     */
    private final List<Map<String, Object>> relations;
}
