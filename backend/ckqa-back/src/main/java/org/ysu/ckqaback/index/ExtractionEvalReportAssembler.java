package org.ysu.ckqaback.index;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.ysu.ckqaback.entity.PromptTuneExtractionEvalRuns;
import org.ysu.ckqaback.index.dto.ExtractionEvalReportResponse;
import org.ysu.ckqaback.index.dto.ExtractionEvalReportResponse.CandidateReport;
import org.ysu.ckqaback.index.dto.ExtractionEvalReportResponse.Gate;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 把 DB report_json（{@code top_candidates.json} 序列化）投影成 {@link ExtractionEvalReportResponse}。
 *
 * <p>核心职责：</p>
 * <ul>
 *   <li>注入 displayNameZh（来自 {@link CandidateMetadataLookup}）</li>
 *   <li>按 spec § 04 详情抽屉阈值（0.8 / 0.5 / 0.5 / 0.95）重新计算 gates，不直接用脚本 gate_passed</li>
 *   <li>F1 由 recall + precision 重算</li>
 *   <li>过滤未在 CandidateMetadataLookup 白名单中的候选</li>
 *   <li>把 entity {@code candidate_failures} 列反序列化成 failedCandidates 数组（风险 1）</li>
 * </ul>
 */
@Service
@RequiredArgsConstructor
public class ExtractionEvalReportAssembler {

    private static final Logger log = LoggerFactory.getLogger(ExtractionEvalReportAssembler.class);

    /** 与 spec § 04 详情抽屉阈值对齐。 */
    private static final BigDecimal PARSE_SUCCESS_THRESHOLD = new BigDecimal("0.80");
    private static final BigDecimal RECALL_THRESHOLD = new BigDecimal("0.50");
    private static final BigDecimal PRECISION_THRESHOLD = new BigDecimal("0.50");
    /** relation_direction 用脚本严格阈值。 */
    private static final BigDecimal RELATION_DIRECTION_THRESHOLD = new BigDecimal("0.95");

    private final CandidateMetadataLookup metadataLookup;
    private final ObjectMapper objectMapper;

    public ExtractionEvalReportResponse assemble(PromptTuneExtractionEvalRuns run) {
        return ExtractionEvalReportResponse.builder()
                .evalRunId(run.getId())
                .generatedAt(run.getFinishedAt())
                .seed(run.getSeed())
                .candidates(parseCandidates(run.getReportJson(), run.getSeed()))
                .failedCandidates(parseFailedCandidates(run.getCandidateFailures()))
                .build();
    }

    /**
     * 把 entity {@code candidate_failures} 列（worker 写入的 JSON 数组）解析成 DTO。
     * <p>解析失败 / 列为空 时返回空 List，永不抛错——失败信息缺失不应让 report 接口失败。</p>
     */
    private List<ExtractionEvalReportResponse.CandidateFailure> parseFailedCandidates(String json) {
        if (json == null || json.isBlank()) return List.of();
        try {
            List<Map<String, Object>> raw = objectMapper.readValue(json, new TypeReference<>() {});
            List<ExtractionEvalReportResponse.CandidateFailure> result = new ArrayList<>();
            for (Map<String, Object> entry : raw) {
                String candidateId = stringField(entry, "candidateId");
                if (candidateId == null) continue;
                result.add(ExtractionEvalReportResponse.CandidateFailure.builder()
                        .candidateId(candidateId)
                        .displayNameZh(metadataLookup.isKnown(candidateId)
                                ? metadataLookup.displayNameZh(candidateId)
                                : candidateId)
                        .stage(stringField(entry, "stage"))
                        .reason(stringField(entry, "reason"))
                        .build());
            }
            return result;
        } catch (Exception e) {
            log.warn("解析 candidate_failures 失败，按空列表返回: {}", e.getMessage());
            return List.of();
        }
    }

    private List<CandidateReport> parseCandidates(String reportJson, String seed) {
        if (reportJson == null || reportJson.isBlank()) return List.of();
        Map<String, Object> root;
        try {
            root = objectMapper.readValue(reportJson, new TypeReference<>() {});
        } catch (Exception e) {
            log.warn("解析 report_json 失败：{}", e.getMessage());
            return List.of();
        }
        Object node = root.get("all_candidates_ranked");
        if (!(node instanceof List<?> rawList)) return List.of();

        // Phase 5.2：按 seed 过滤冗余基线候选——历史 evalRun（Phase 5 时期生成）的 reportJson 里
        // 仍有 4 个候选（含 fallback_default_copy 的 auto_tuned 与 default 同分对照），
        // 投影时按当前 evalRun.seed 过滤回放，让历史报告也只显示 3 候选；
        // seed=null 的老 evalRun（Phase 4 兼容）不过滤，全 4 候选透传。
        java.util.Set<String> seedAllowed = CandidateSeedFilter.allowedCandidatesForSeed(seed);

        List<CandidateReport> result = new ArrayList<>();
        for (Object item : rawList) {
            if (!(item instanceof Map<?, ?> rawMap)) continue;
            @SuppressWarnings("unchecked")
            Map<String, Object> entry = (Map<String, Object>) rawMap;
            String candidateId = stringField(entry, "candidate");
            if (candidateId == null || !metadataLookup.isKnown(candidateId)) {
                if (candidateId != null) {
                    log.warn("评分报告含未知候选 {}，已跳过", candidateId);
                }
                continue;
            }
            if (!seedAllowed.contains(candidateId)) {
                continue;  // 被 seed-aware 规则排除（如 system_default 时的 auto_tuned）
            }
            result.add(buildCandidateReport(candidateId, entry));
        }
        return result;
    }

    private CandidateReport buildCandidateReport(String candidateId, Map<String, Object> entry) {
        BigDecimal recall = bigDecimalField(entry, "audit_entity_recall");
        BigDecimal precision = bigDecimalField(entry, "audit_entity_precision");
        BigDecimal parseSuccess = bigDecimalField(entry, "parse_success_rate");
        BigDecimal endpointValidRate = bigDecimalField(entry, "endpoint_valid_rate");
        Integer endpointTotalCount = integerField(entry, "endpoint_total_count");
        Integer endpointInvalidCount = integerField(entry, "endpoint_invalid_count");

        return CandidateReport.builder()
                .candidateId(candidateId)
                .displayNameZh(metadataLookup.displayNameZh(candidateId))
                .rank(integerField(entry, "rank"))
                .compositeScore(bigDecimalField(entry, "composite_score"))
                .parseSuccessRate(parseSuccess)
                .recall(recall)
                .precision(precision)
                .f1(computeF1(recall, precision))
                .entityCountAvg(bigDecimalField(entry, "entity_count_avg"))
                .relationCountAvg(bigDecimalField(entry, "relation_count_avg"))
                .tokensUsed(integerField(entry, "tokens_used"))
                .elapsedSeconds(integerField(entry, "elapsed_seconds"))
                .gates(computeGates(parseSuccess, recall, precision, endpointValidRate, endpointTotalCount, endpointInvalidCount))
                .failedSamples(List.of())  // 本期不返回成功候选内的"个别失败样本"明细（≠失败候选），留 Phase 9
                .build();
    }

    private List<Gate> computeGates(
            BigDecimal parseSuccess,
            BigDecimal recall,
            BigDecimal precision,
            BigDecimal endpointValidRate,
            Integer endpointTotalCount,
            Integer endpointInvalidCount
    ) {
        return List.of(
                Gate.builder()
                        .name("parse_success")
                        .threshold(PARSE_SUCCESS_THRESHOLD)
                        .value(parseSuccess)
                        .passed(passed(parseSuccess, PARSE_SUCCESS_THRESHOLD))
                        .build(),
                Gate.builder()
                        .name("audit_recall")
                        .threshold(RECALL_THRESHOLD)
                        .value(recall)
                        .passed(passed(recall, RECALL_THRESHOLD))
                        .build(),
                Gate.builder()
                        .name("audit_precision")
                        .threshold(PRECISION_THRESHOLD)
                        .value(precision)
                        .passed(passed(precision, PRECISION_THRESHOLD))
                        .build(),
                Gate.builder()
                        .name("relation_direction")
                        .threshold(null)  // 前端按 X/Y 文案展示，不显示阈值数值
                        .value(endpointValidRate)
                        .passed(passed(endpointValidRate, RELATION_DIRECTION_THRESHOLD))
                        .endpointTotalCount(endpointTotalCount)
                        .endpointInvalidCount(endpointInvalidCount)
                        .build()
        );
    }

    /** value 为空时返回 null（前端按"未评估"渲染）；否则与阈值比较。 */
    private static Boolean passed(BigDecimal value, BigDecimal threshold) {
        if (value == null) return null;
        return value.compareTo(threshold) >= 0;
    }

    private static BigDecimal computeF1(BigDecimal recall, BigDecimal precision) {
        if (recall == null || precision == null) return null;
        BigDecimal sum = recall.add(precision);
        if (sum.signum() == 0) return BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        return recall.multiply(precision)
                .multiply(new BigDecimal("2"))
                .divide(sum, 4, RoundingMode.HALF_UP);
    }

    private static String stringField(Map<String, Object> entry, String key) {
        Object value = entry.get(key);
        return value instanceof String s ? s : null;
    }

    private static Integer integerField(Map<String, Object> entry, String key) {
        Object value = entry.get(key);
        if (value instanceof Number n) return n.intValue();
        return null;
    }

    private static BigDecimal bigDecimalField(Map<String, Object> entry, String key) {
        Object value = entry.get(key);
        if (value == null) return null;
        if (value instanceof Number n) return new BigDecimal(n.toString());
        if (value instanceof String s && !s.isBlank()) {
            try { return new BigDecimal(s); } catch (NumberFormatException ignore) { }
        }
        return null;
    }
}
