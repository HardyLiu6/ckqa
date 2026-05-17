package org.ysu.ckqaback.index;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.ysu.ckqaback.entity.PromptTuneExtractionEvalRuns;
import org.ysu.ckqaback.index.dto.ExtractionEvalReportResponse;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ExtractionEvalReportAssemblerTest {

    private ExtractionEvalReportAssembler assembler;

    @BeforeEach
    void setUp() {
        assembler = new ExtractionEvalReportAssembler(
                new CandidateMetadataLookup(),
                new ObjectMapper()
        );
    }

    @Test
    void assemblesRankedCandidatesWithDisplayName() {
        PromptTuneExtractionEvalRuns run = newRunWithReport("""
                {
                  "all_candidates_ranked": [
                    {
                      "candidate": "schema_fewshot_distilled_v2_strict_tuple",
                      "rank": 1,
                      "composite_score": 0.71,
                      "parse_success_rate": 0.95,
                      "audit_entity_recall": 0.74,
                      "audit_entity_precision": 0.68,
                      "endpoint_valid_rate": 0.96,
                      "sample_count": 20,
                      "success_count": 19
                    },
                    {
                      "candidate": "default",
                      "rank": 2,
                      "composite_score": 0.42,
                      "parse_success_rate": 0.80,
                      "audit_entity_recall": 0.45,
                      "audit_entity_precision": 0.42,
                      "endpoint_valid_rate": 0.90,
                      "sample_count": 20,
                      "success_count": 16
                    }
                  ]
                }
                """);

        ExtractionEvalReportResponse response = assembler.assemble(run);

        assertThat(response.getEvalRunId()).isEqualTo(run.getId());
        assertThat(response.getCandidates()).hasSize(2);

        ExtractionEvalReportResponse.CandidateReport top = response.getCandidates().get(0);
        assertThat(top.getCandidateId()).isEqualTo("schema_fewshot_distilled_v2_strict_tuple");
        assertThat(top.getDisplayNameZh()).isEqualTo("图谱感知 + 蒸馏样例");
        assertThat(top.getRank()).isEqualTo(1);
        assertThat(top.getCompositeScore()).isEqualByComparingTo(new BigDecimal("0.71"));
    }

    @Test
    void computesGatesAccordingToSpecThresholds() {
        PromptTuneExtractionEvalRuns run = newRunWithReport("""
                {
                  "all_candidates_ranked": [
                    {
                      "candidate": "default",
                      "rank": 1,
                      "composite_score": 0.42,
                      "parse_success_rate": 0.80,
                      "audit_entity_recall": 0.49,
                      "audit_entity_precision": 0.50,
                      "endpoint_valid_rate": 0.96,
                      "endpoint_total_count": 50,
                      "endpoint_invalid_count": 2,
                      "sample_count": 20
                    }
                  ]
                }
                """);

        ExtractionEvalReportResponse response = assembler.assemble(run);
        List<ExtractionEvalReportResponse.Gate> gates = response.getCandidates().get(0).getGates();

        // spec 阈值：parse>=0.8 / recall>=0.5 / precision>=0.5 / endpoint>=0.95
        assertThat(gates).hasSize(4);
        ExtractionEvalReportResponse.Gate parse = findGate(gates, "parse_success");
        assertThat(parse.getThreshold()).isEqualByComparingTo(new BigDecimal("0.80"));
        assertThat(parse.getValue()).isEqualByComparingTo(new BigDecimal("0.80"));
        assertThat(parse.getPassed()).isTrue();
        // 非 relation_direction 时分子分母字段必须为 null
        assertThat(parse.getEndpointTotalCount()).isNull();
        assertThat(parse.getEndpointInvalidCount()).isNull();

        ExtractionEvalReportResponse.Gate recall = findGate(gates, "audit_recall");
        // value=0.49 < 0.50 → 不通过
        assertThat(recall.getPassed()).isFalse();

        ExtractionEvalReportResponse.Gate precision = findGate(gates, "audit_precision");
        // value=0.50 >= 0.50 → 通过
        assertThat(precision.getPassed()).isTrue();

        ExtractionEvalReportResponse.Gate direction = findGate(gates, "relation_direction");
        // value=0.96 >= 0.95 → 通过；threshold 为 null（前端按 X/Y 文案展示）
        assertThat(direction.getPassed()).isTrue();
        assertThat(direction.getThreshold()).isNull();
        // relation_direction 透传 endpoint 分子分母，前端用此渲染 "48 / 50"
        assertThat(direction.getEndpointTotalCount()).isEqualTo(50);
        assertThat(direction.getEndpointInvalidCount()).isEqualTo(2);
    }

    @Test
    void marksAuditGatesAsNullWhenAuditValuesMissing() {
        // 边界：audit 集为空时脚本返回 null
        PromptTuneExtractionEvalRuns run = newRunWithReport("""
                {
                  "all_candidates_ranked": [
                    {
                      "candidate": "default",
                      "rank": 1,
                      "composite_score": 0.42,
                      "parse_success_rate": 0.85,
                      "audit_entity_recall": null,
                      "audit_entity_precision": null,
                      "endpoint_valid_rate": 0.92,
                      "sample_count": 20
                    }
                  ]
                }
                """);

        ExtractionEvalReportResponse response = assembler.assemble(run);
        List<ExtractionEvalReportResponse.Gate> gates = response.getCandidates().get(0).getGates();
        ExtractionEvalReportResponse.Gate recall = findGate(gates, "audit_recall");
        assertThat(recall.getValue()).isNull();
        assertThat(recall.getPassed()).isNull();  // 未评估
    }

    @Test
    void computesF1FromRecallAndPrecision() {
        PromptTuneExtractionEvalRuns run = newRunWithReport("""
                {
                  "all_candidates_ranked": [
                    {
                      "candidate": "default",
                      "rank": 1,
                      "composite_score": 0.42,
                      "parse_success_rate": 0.85,
                      "audit_entity_recall": 0.6,
                      "audit_entity_precision": 0.4,
                      "endpoint_valid_rate": 0.92,
                      "sample_count": 20
                    }
                  ]
                }
                """);

        ExtractionEvalReportResponse response = assembler.assemble(run);
        // F1 = 2*0.6*0.4 / (0.6+0.4) = 0.48
        BigDecimal f1 = response.getCandidates().get(0).getF1();
        assertThat(f1).isEqualByComparingTo(new BigDecimal("0.48"));
    }

    @Test
    void skipsUnknownCandidatesWithWarning() {
        // 边界：脚本输出含未在 CandidateMetadataLookup 中的 candidate（不应该发生但要鲁棒）
        PromptTuneExtractionEvalRuns run = newRunWithReport("""
                {
                  "all_candidates_ranked": [
                    {"candidate": "default", "rank": 1, "composite_score": 0.4, "parse_success_rate": 0.8, "endpoint_valid_rate": 0.9, "sample_count": 20},
                    {"candidate": "mystery_candidate", "rank": 2, "composite_score": 0.3, "parse_success_rate": 0.7, "endpoint_valid_rate": 0.8, "sample_count": 20}
                  ]
                }
                """);

        ExtractionEvalReportResponse response = assembler.assemble(run);
        // 未知 candidate 跳过，剩下 1 条
        assertThat(response.getCandidates()).hasSize(1);
        assertThat(response.getCandidates().get(0).getCandidateId()).isEqualTo("default");
    }

    @Test
    void returnsEmptyListWhenReportJsonEmpty() {
        PromptTuneExtractionEvalRuns run = newRunWithReport(null);
        ExtractionEvalReportResponse response = assembler.assemble(run);
        assertThat(response.getCandidates()).isEmpty();
    }

    @Test
    void exposesSeedFromRunEntity() {
        // Phase 4.5 引入：Report 顶层透传 seed 快照
        PromptTuneExtractionEvalRuns run = newRunWithReport("{\"all_candidates_ranked\":[]}");
        run.setSeed("graphrag_tuned");
        ExtractionEvalReportResponse response = assembler.assemble(run);
        assertThat(response.getSeed()).isEqualTo("graphrag_tuned");
    }

    @Test
    void seedIsNullWhenRunHasNoSeed() {
        PromptTuneExtractionEvalRuns run = newRunWithReport("{\"all_candidates_ranked\":[]}");
        // run.seed 留 null
        ExtractionEvalReportResponse response = assembler.assemble(run);
        assertThat(response.getSeed()).isNull();
    }

    @Test
    void exposesFailedCandidatesFromRunEntity() {
        // 风险 1：单候选失败 → entity.candidate_failures 由 worker 写入；report 投影时透传到 failedCandidates 数组
        PromptTuneExtractionEvalRuns run = newRunWithReport("""
                { "all_candidates_ranked": [] }
                """);
        run.setCandidateFailures("""
                [
                  {"candidateId": "default", "stage": "extract", "reason": "timeout"},
                  {"candidateId": "auto_tuned", "stage": "extract", "reason": "prompt 文件不存在"}
                ]
                """);

        ExtractionEvalReportResponse response = assembler.assemble(run);

        assertThat(response.getFailedCandidates()).hasSize(2);
        ExtractionEvalReportResponse.CandidateFailure first = response.getFailedCandidates().get(0);
        assertThat(first.getCandidateId()).isEqualTo("default");
        // 已知 candidate 注入中文展示名
        assertThat(first.getDisplayNameZh()).isEqualTo("默认基线");
        assertThat(first.getStage()).isEqualTo("extract");
        assertThat(first.getReason()).isEqualTo("timeout");
    }

    @Test
    void emptyFailedCandidatesWhenColumnIsNullOrInvalid() {
        // 边界：未发生失败 / 列为空 / JSON 解析失败 → 空 List，永不抛错
        PromptTuneExtractionEvalRuns run = newRunWithReport("{\"all_candidates_ranked\":[]}");
        // 不 set candidateFailures
        assertThat(assembler.assemble(run).getFailedCandidates()).isEmpty();

        run.setCandidateFailures("not-a-json");
        assertThat(assembler.assemble(run).getFailedCandidates()).isEmpty();
    }

    @Test
    void failedCandidatesPreserveUnknownCandidateIdAsDisplayName() {
        // 不在 metadata lookup 白名单中的 candidate，不能丢失，displayNameZh 退化为原 ID
        PromptTuneExtractionEvalRuns run = newRunWithReport("{\"all_candidates_ranked\":[]}");
        run.setCandidateFailures("""
                [{"candidateId": "phantom_x", "stage": "extract", "reason": "unknown"}]
                """);
        ExtractionEvalReportResponse response = assembler.assemble(run);
        assertThat(response.getFailedCandidates()).hasSize(1);
        assertThat(response.getFailedCandidates().get(0).getDisplayNameZh()).isEqualTo("phantom_x");
    }

    private PromptTuneExtractionEvalRuns newRunWithReport(String reportJson) {
        PromptTuneExtractionEvalRuns r = new PromptTuneExtractionEvalRuns();
        r.setId(99L);
        r.setReportJson(reportJson);
        r.setFinishedAt(LocalDateTime.of(2026, 5, 17, 12, 0));
        return r;
    }

    private ExtractionEvalReportResponse.Gate findGate(
            List<ExtractionEvalReportResponse.Gate> gates, String name
    ) {
        return gates.stream().filter(g -> name.equals(g.getName())).findFirst().orElseThrow();
    }
}
