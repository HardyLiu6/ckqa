package org.ysu.ckqaback.index;

import java.util.List;
import java.util.Optional;

/**
 * GraphRAG 官方 prompt-tune 阶段枚举。
 * <p>
 * 每个阶段对应官方在 {@code graphrag/reports/prompt-tuning.log} 中输出的
 * 一条形如 {@code "graphrag.api.prompt_tune - <message>"} 的 INFO 行。
 * 我们用 {@link #match(String)} 把消息文本反向映射到阶段，从而向前端反馈
 * "正在做哪一步"。
 * <p>
 * 顺序与官方实现一致；百分比是为前端进度条提供的体感档位（不可能精确）。
 */
public enum PromptTunePhase {

    CHUNKING("chunking", "拆分文档", 5),
    DOMAIN("domain", "识别课程领域", 10),
    LANGUAGE("language", "识别语言", 15),
    PERSONA("persona", "生成专家角色画像", 20),
    COMMUNITY_RANKING("community_ranking", "生成社区报告排序描述", 30),
    ENTITY_TYPES("entity_types", "识别实体类型", 40),
    EXAMPLES("examples", "生成实体关系示例", 60),
    EXTRACT_PROMPT("extract_prompt", "撰写实体抽取提示词", 75),
    SUMMARY_PROMPT("summary_prompt", "撰写实体摘要提示词", 80),
    COMMUNITY_ROLE("community_role", "生成社区报告角色", 85),
    COMMUNITY_SUMMARY("community_summary", "撰写社区摘要提示词", 95),
    WRITING("writing", "保存调优产物", 100);

    /**
     * 关键词到阶段的映射表。<strong>顺序很重要</strong>：更具体的关键词必须在
     * 更宽松的关键词之前（例如 "Generating entity extraction prompt" 必须早于
     * "Generating entity"）。
     * <p>
     * 关键词全部用 {@link String#contains(CharSequence)} 大小写不敏感地匹配
     * 消息文本（前缀的 timestamp / level / logger 已被 detector 剥离）。
     */
    private static final List<Mapping> MAPPINGS = List.of(
            new Mapping("chunking documents", CHUNKING),
            // 准备阶段的两个 INFO 行也归并到 CHUNKING：用户不需要看到内部细节。
            new Mapping("retrieving language model", CHUNKING),
            new Mapping("creating language model", CHUNKING),
            new Mapping("generating community report ranking description", COMMUNITY_RANKING),
            new Mapping("generating community reporter role", COMMUNITY_ROLE),
            new Mapping("generating community summarization prompt", COMMUNITY_SUMMARY),
            new Mapping("generating entity relationship examples", EXAMPLES),
            new Mapping("generating entity extraction prompt", EXTRACT_PROMPT),
            new Mapping("generating entity summarization prompt", SUMMARY_PROMPT),
            new Mapping("generating entity types", ENTITY_TYPES),
            new Mapping("generating domain", DOMAIN),
            new Mapping("detecting language", LANGUAGE),
            new Mapping("generating persona", PERSONA),
            new Mapping("writing prompts to", WRITING),
            new Mapping("prompts written to", WRITING)
    );

    private final String key;
    private final String label;
    private final int progressPercentage;

    PromptTunePhase(String key, String label, int progressPercentage) {
        this.key = key;
        this.label = label;
        this.progressPercentage = progressPercentage;
    }

    public String getKey() {
        return key;
    }

    public String getLabel() {
        return label;
    }

    public int getProgressPercentage() {
        return progressPercentage;
    }

    /**
     * 给 {@code progress_stage} 列用的复合键，前端通过它知道既在 prompt_tune 大阶段
     * 也在某个细分小阶段。
     */
    public String getStageKey() {
        return "prompt_tune_" + key;
    }

    /**
     * 把官方日志一行（已剥离前缀，仅保留 message 部分）反向匹配到阶段。
     * 找不到则返回 {@link Optional#empty()}。
     */
    public static Optional<PromptTunePhase> match(String message) {
        if (message == null || message.isBlank()) {
            return Optional.empty();
        }
        String lowered = message.toLowerCase();
        for (Mapping mapping : MAPPINGS) {
            if (lowered.contains(mapping.keyword)) {
                return Optional.of(mapping.phase);
            }
        }
        return Optional.empty();
    }

    private record Mapping(String keyword, PromptTunePhase phase) {
    }
}
