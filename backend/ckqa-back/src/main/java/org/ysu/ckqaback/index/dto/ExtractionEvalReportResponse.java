package org.ysu.ckqaback.index.dto;

import lombok.Builder;
import lombok.Getter;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * GET /extraction-eval/report 评分排行榜响应。
 *
 * <p>评分完成后从 DB report_json 列读出。candidates 按 composite_score 倒序，
 * 含 spec § 04 详情抽屉所需的指标 + 质量门控 + 失败样本。</p>
 */
@Getter
@Builder
public class ExtractionEvalReportResponse {

    private final Long evalRunId;
    private final LocalDateTime generatedAt;
    /**
     * 本次评分启动时 build run 的 seed 快照（system_default / graphrag_tuned / null）。
     * <p>由 Phase 4.5 引入；前端用于展示"本次评分基于哪个种子的候选"。</p>
     */
    private final String seed;
    /** 进入排行榜的候选（成功跑完抽取 + scoring）。 */
    private final List<CandidateReport> candidates;
    /**
     * 未进入排行榜的失败候选（结构化）。
     * <p>来源：worker 把 entity 的 {@code candidate_failures} JSON 列反序列化得到。
     * 排行榜区域不展示这些候选，由前端在排行榜下方"未进入排名"区域单独渲染。
     * 整体 status=success 时也可能存在；只有 finished 全空时整体才 status=failed。</p>
     */
    private final List<CandidateFailure> failedCandidates;

    @Getter
    @Builder
    public static class CandidateReport {
        private final String candidateId;
        private final String displayNameZh;
        private final Integer rank;

        /** composite_score = 0.6 × hard + 0.4 × soft（脚本默认权重）。 */
        private final BigDecimal compositeScore;
        private final BigDecimal parseSuccessRate;
        /** spec 称为"召回率"，对应脚本 audit_entity_recall（可空：audit gold 缺失时）。 */
        private final BigDecimal recall;
        /** spec 称为"准确率"，对应脚本 audit_entity_precision（可空）。 */
        private final BigDecimal precision;
        /** F1 = 2RP/(R+P)，可空（recall/precision 任一为空时）。 */
        private final BigDecimal f1;
        private final BigDecimal entityCountAvg;
        private final BigDecimal relationCountAvg;
        private final Integer tokensUsed;
        private final Integer elapsedSeconds;
        private final List<Gate> gates;
        private final List<FailedSample> failedSamples;
    }

    @Getter
    @Builder
    public static class Gate {
        /** parse_success / audit_recall / audit_precision / relation_direction。 */
        private final String name;
        /** 阈值，relation_direction 时为空（前端按 X/Y 渲染）。 */
        private final BigDecimal threshold;
        /** 实测值，audit 系列指标 audit gold 缺失时为空。 */
        private final BigDecimal value;
        /** value 缺失或 audit 集为空时为 null（前端按"未评估"灰色）。 */
        private final Boolean passed;
        /**
         * 仅 relation_direction 用：关系端点总数（候选输出关系数 × 2）。
         * 其他 gate 返回 null。前端按 (endpointTotalCount - endpointInvalidCount) / endpointTotalCount 渲染分子分母 "X / Y"。
         */
        private final Integer endpointTotalCount;
        /**
         * 仅 relation_direction 用：方向无效的端点数（同上）；其他 gate 返回 null。
         */
        private final Integer endpointInvalidCount;
    }

    @Getter
    @Builder
    public static class FailedSample {
        private final String sampleId;
        private final String reason;
    }

    /**
     * 失败候选审计条目，对应 entity {@code candidate_failures} JSON 列中的一项。
     */
    @Getter
    @Builder
    public static class CandidateFailure {
        private final String candidateId;
        /** 中文展示名（CandidateMetadataLookup 注入），未知 candidate 返回 candidateId 原样。 */
        private final String displayNameZh;
        /** "extract" / "scoring"。 */
        private final String stage;
        private final String reason;
    }
}
