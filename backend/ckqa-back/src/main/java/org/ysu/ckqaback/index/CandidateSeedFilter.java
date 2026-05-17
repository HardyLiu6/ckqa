package org.ysu.ckqaback.index;

import java.util.Set;

/**
 * 03 步候选 seed-aware 过滤（Phase 5.2 落地）。
 *
 * <p>背景：spec § 决策 4 早期固定生成 4 个候选（default / auto_tuned / schema_aware_directional_v2 /
 * schema_fewshot_distilled_v2_strict_tuple），但实际语义上只有 1 个有意义的"基线对照组"——
 * 用户选 seed=system_default 时 auto_tuned 候选会回退（fallback_default_copy）到与 default 完全相同的
 * prompt 文本，评分排行榜里看到的 auto_tuned vs default 不是真实对比，浪费 ~25% LLM 调用。
 * 用户选 seed=graphrag_tuned 时反过来——default 候选不依赖 seed，是冗余对照。</p>
 *
 * <p>方案：保留 Python 脚本一次性生成 4 个候选 prompt 文件（不动脚本），由 Java 层在
 * <ul>
 *   <li>03 步候选列表透传给前端时</li>
 *   <li>04 步评分 trigger 时校验 selectedCandidates 白名单时</li>
 * </ul>
 * 按 seed 排除冗余基线，永远只透出 3 个候选给前端。</p>
 *
 * <p>规则：</p>
 * <table border="1">
 *   <tr><th>seed</th><th>排除候选</th><th>剩余候选</th></tr>
 *   <tr><td>system_default</td><td>auto_tuned（fallback 等同 default）</td><td>default + schema_aware + schema_fewshot</td></tr>
 *   <tr><td>graphrag_tuned</td><td>default（不 base on seed）</td><td>auto_tuned + schema_aware + schema_fewshot</td></tr>
 *   <tr><td>history_draft</td><td>auto_tuned（与 system_default 同语义对照）</td><td>default + schema_aware + schema_fewshot</td></tr>
 *   <tr><td>null / 未知</td><td>不过滤（向后兼容 Phase 4 老 build run）</td><td>全部 4 个</td></tr>
 * </table>
 */
public final class CandidateSeedFilter {

    private CandidateSeedFilter() {}

    private static final Set<String> ALL_CANDIDATES = Set.of(
            "default",
            "auto_tuned",
            "schema_aware_directional_v2",
            "schema_fewshot_distilled_v2_strict_tuple"
    );

    /**
     * 给定 seed 返回该 seed 下允许出现的候选 ID 白名单。
     * @param seed null / 未知值 → 不过滤（返回全 4 候选白名单，与 Phase 4 老 build run 兼容）
     */
    public static Set<String> allowedCandidatesForSeed(String seed) {
        if (seed == null) return ALL_CANDIDATES;
        return switch (seed) {
            case "system_default", "history_draft" -> Set.of(
                    "default",
                    "schema_aware_directional_v2",
                    "schema_fewshot_distilled_v2_strict_tuple"
            );
            case "graphrag_tuned" -> Set.of(
                    "auto_tuned",
                    "schema_aware_directional_v2",
                    "schema_fewshot_distilled_v2_strict_tuple"
            );
            default -> ALL_CANDIDATES;
        };
    }

    /**
     * 判定指定 candidateId 在当前 seed 下是否应被透出（即未被冗余基线规则排除）。
     */
    public static boolean isAllowed(String seed, String candidateId) {
        return allowedCandidatesForSeed(seed).contains(candidateId);
    }
}
