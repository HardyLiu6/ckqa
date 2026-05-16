package org.ysu.ckqaback.index;

import org.springframework.stereotype.Component;
import org.ysu.ckqaback.index.dto.CandidateResponse.TraitInfo;

import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 03 步候选提示词的"前端展示层元数据"硬编码查表。
 *
 * <p>本期硬编码 4 个候选；Phase 7+ 引入 {@code GET /relation-schemas} 时
 * 一并迁移到 manifest 配置或 schema 配置层。详见 spec § 风险 #3
 * "候选译名硬编码与候选数量增长"。</p>
 *
 * <p><strong>不</strong>包含算法产物字段（schemaUsed / fewshotExampleCount /
 * generationTime 等），那些从 manifest.json 直接透传，由 {@link CandidateManifestReader} 负责。</p>
 */
@Component
public class CandidateMetadataLookup {

    private static final Map<String, String> DISPLAY_NAME_ZH = Map.of(
            "default", "默认基线",
            "auto_tuned", "GraphRAG 自动调优",
            "schema_aware_directional_v2", "图谱感知",
            "schema_fewshot_distilled_v2_strict_tuple", "图谱感知 + 蒸馏样例"
    );

    private static final Map<String, String> CATEGORY = Map.of(
            "default", "baseline",
            "auto_tuned", "auto_tuned",
            "schema_aware_directional_v2", "schema_aware",
            "schema_fewshot_distilled_v2_strict_tuple", "schema_fewshot"
    );

    private static final Map<String, String> DESCRIPTION = Map.of(
            "default", "基线 · 课程域微调",
            "auto_tuned", "GraphRAG 官方 prompt-tune 自动产物",
            "schema_aware_directional_v2", "注入 schema + 方向卡 + 失败族守卫",
            "schema_fewshot_distilled_v2_strict_tuple", "注入 schema + few-shot 蒸馏 + 严格 tuple 约束"
    );

    private static final Set<String> RECOMMENDED_CANDIDATES = Set.of(
            "schema_fewshot_distilled_v2_strict_tuple"
    );

    private static final Map<String, List<TraitInfo>> TRAITS = Map.of(
            "default", List.of(
                    trait("baseline",   "课程基线"),
                    trait("no_schema",  "无 schema 注入"),
                    trait("no_fewshot", "无 few-shot")
            ),
            "auto_tuned", List.of(
                    trait("auto_tuned", "自动调优"),
                    trait("no_schema",  "无 schema 注入"),
                    trait("no_fewshot", "无 few-shot")
            ),
            "schema_aware_directional_v2", List.of(
                    trait("schema_injected",  "schema 注入"),
                    trait("directional_card", "方向卡"),
                    trait("failure_guard",    "失败族守卫")
            ),
            "schema_fewshot_distilled_v2_strict_tuple", List.of(
                    trait("schema_injected",    "schema 注入"),
                    trait("few_shot_distilled", "few-shot 蒸馏"),
                    trait("strict_tuple",       "严格 tuple")
            )
    );

    public boolean isKnown(String candidateId) {
        return DISPLAY_NAME_ZH.containsKey(candidateId);
    }

    public String displayNameZh(String candidateId) {
        return DISPLAY_NAME_ZH.get(candidateId);
    }

    public String category(String candidateId) {
        return CATEGORY.get(candidateId);
    }

    public String description(String candidateId) {
        return DESCRIPTION.get(candidateId);
    }

    public boolean isRecommended(String candidateId) {
        return RECOMMENDED_CANDIDATES.contains(candidateId);
    }

    public List<TraitInfo> traits(String candidateId) {
        return TRAITS.getOrDefault(candidateId, List.of());
    }

    public Set<String> knownCandidateIds() {
        return DISPLAY_NAME_ZH.keySet();
    }

    private static TraitInfo trait(String key, String label) {
        return TraitInfo.builder().key(key).label(label).build();
    }
}
