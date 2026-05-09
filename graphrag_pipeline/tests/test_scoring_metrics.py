from __future__ import annotations

import sys
import unittest
from pathlib import Path

_PROJECT_ROOT = Path(__file__).resolve().parent.parent
_SCRIPTS_DIR = _PROJECT_ROOT / "scripts"
if str(_SCRIPTS_DIR) not in sys.path:
    sys.path.insert(0, str(_SCRIPTS_DIR))

from extraction_schema import (
    ExtractionEntity,
    ExtractionRelationship,
    StructuredExtractionResult,
)
from scoring_metrics import (
    DEFAULT_WEIGHTS,
    GATE_THRESHOLD,
    HARD_METRIC_KEYS,
    SOFT_METRIC_KEYS,
    aggregate_candidate_metrics,
    analyze_endpoint_validity,
    can_derive_inverse_relation,
    compute_composite_hard,
    compute_composite_score,
    compute_composite_soft,
    compute_duplicate_entity_rate,
    compute_endpoint_valid_rate,
    compute_entity_type_valid_rate,
    compute_gate_passed,
    compute_noise_entity_rate,
    compute_output_stability,
    compute_parse_success_rate,
    compute_relation_type_valid_rate,
    compute_schema_hit_rate,
    rank_candidates,
    select_top_k,
)


def _result(sample_id: str, status: str) -> StructuredExtractionResult:
    return StructuredExtractionResult(
        sample_id=sample_id,
        candidate="c",
        status=status,
        entities=[],
        relationships=[],
        raw_output="",
    )


class TestParseSuccessRate(unittest.TestCase):
    def test_all_success(self):
        results = [_result("s1", "success"), _result("s2", "success")]
        self.assertEqual(compute_parse_success_rate(results), 1.0)

    def test_mixed(self):
        results = [
            _result("s1", "success"),
            _result("s2", "parse_error"),
            _result("s3", "llm_error"),
            _result("s4", "success"),
        ]
        self.assertEqual(compute_parse_success_rate(results), 0.5)

    def test_empty(self):
        self.assertEqual(compute_parse_success_rate([]), 0.0)


def _success_result(entities, relationships) -> StructuredExtractionResult:
    return StructuredExtractionResult(
        sample_id="s",
        candidate="c",
        status="success",
        entities=[ExtractionEntity(**e) for e in entities],
        relationships=[ExtractionRelationship(**r) for r in relationships],
        raw_output="",
    )


ENTITY_TYPES = {"Course", "Chapter", "Concept"}
RELATION_TYPES = {"contains", "related_to"}


class TestTypeValidity(unittest.TestCase):
    def test_entity_type_valid_rate_mixed(self):
        results = [
            _success_result(
                [
                    {"id": "e1", "title": "OS", "type": "Course"},
                    {"id": "e2", "title": "Ch1", "type": "Chapter"},
                    {"id": "e3", "title": "X", "type": "NotInSchema"},
                ],
                [],
            )
        ]
        self.assertAlmostEqual(
            compute_entity_type_valid_rate(results, ENTITY_TYPES), 2 / 3
        )

    def test_entity_type_valid_rate_ignores_non_success(self):
        results = [
            _result("s1", "parse_error"),
            _success_result(
                [{"id": "e1", "title": "OS", "type": "Course"}], []
            ),
        ]
        self.assertEqual(compute_entity_type_valid_rate(results, ENTITY_TYPES), 1.0)

    def test_entity_type_valid_rate_empty_returns_zero(self):
        self.assertEqual(compute_entity_type_valid_rate([], ENTITY_TYPES), 0.0)

    def test_relation_type_valid_rate(self):
        results = [
            _success_result(
                [],
                [
                    {"source": "a", "target": "b", "type": "contains"},
                    {"source": "a", "target": "c", "type": "nope"},
                ],
            )
        ]
        self.assertEqual(
            compute_relation_type_valid_rate(results, RELATION_TYPES), 0.5
        )

    def test_schema_hit_rate_per_sample(self):
        fully_valid = _success_result(
            [{"id": "e1", "title": "OS", "type": "Course"}],
            [{"source": "OS", "target": "Ch1", "type": "contains"}],
        )
        bad_entity = _success_result(
            [{"id": "e1", "title": "X", "type": "Bad"}],
            [{"source": "X", "target": "Y", "type": "contains"}],
        )
        self.assertEqual(
            compute_schema_hit_rate(
                [fully_valid, bad_entity], ENTITY_TYPES, RELATION_TYPES
            ),
            0.5,
        )

    def test_schema_hit_rate_no_success_returns_zero(self):
        self.assertEqual(
            compute_schema_hit_rate(
                [_result("s1", "parse_error")], ENTITY_TYPES, RELATION_TYPES
            ),
            0.0,
        )


RELATION_SCHEMA = {
    "contains": {"source_types": ["Course", "Chapter"], "target_types": ["Chapter", "Concept"]},
    "related_to": {"source_types": ["Concept"], "target_types": ["Concept"]},
    "defined_by": {"source_types": ["Concept"], "target_types": ["FormulaOrDefinition", "Term"]},
}


class TestEndpointValidRate(unittest.TestCase):
    def test_both_endpoints_match_schema(self):
        results = [
            _success_result(
                [
                    {"id": "e1", "title": "操作系统", "type": "Course"},
                    {"id": "e2", "title": "第一章", "type": "Chapter"},
                ],
                [{"source": "操作系统", "target": "第一章", "type": "contains"}],
            )
        ]
        self.assertEqual(compute_endpoint_valid_rate(results, RELATION_SCHEMA), 1.0)

    def test_endpoint_type_mismatch_rejected(self):
        results = [
            _success_result(
                [
                    {"id": "e1", "title": "进程", "type": "Concept"},
                    {"id": "e2", "title": "第一章", "type": "Chapter"},
                ],
                [{"source": "进程", "target": "第一章", "type": "contains"}],
            )
        ]
        self.assertEqual(compute_endpoint_valid_rate(results, RELATION_SCHEMA), 0.0)

    def test_unresolved_endpoint_rejected(self):
        results = [
            _success_result(
                [{"id": "e1", "title": "操作系统", "type": "Course"}],
                [{"source": "操作系统", "target": "某个幻觉实体", "type": "contains"}],
            )
        ]
        self.assertEqual(compute_endpoint_valid_rate(results, RELATION_SCHEMA), 0.0)

    def test_missing_endpoint_reasons_take_priority_over_schema_candidate(self):
        schema = {
            "contains": {
                "source_types": [
                    "Course",
                    "Chapter",
                    "KnowledgePoint",
                    "Concept",
                    "AlgorithmOrMethod",
                ],
                "target_types": [
                    "Chapter",
                    "KnowledgePoint",
                    "Concept",
                    "AlgorithmOrMethod",
                ],
            }
        }
        results = [
            _success_result(
                [{"id": "e1", "title": "LRU页面置换算法", "type": "AlgorithmOrMethod"}],
                [
                    {
                        "source": "LRU页面置换算法",
                        "target": "最近最少使用原则",
                        "type": "contains",
                        "description": "算法包含最近最少使用原则。",
                        "evidence": "LRU页面置换算法依据最近最少使用原则。",
                    },
                    {
                        "source": "缺失的算法实体",
                        "target": "LRU页面置换算法",
                        "type": "contains",
                        "description": "缺失源实体的关系。",
                        "evidence": "示例证据。",
                    },
                    {
                        "source": "缺失的源实体",
                        "target": "缺失的目标实体",
                        "type": "contains",
                        "description": "两端都缺失的关系。",
                        "evidence": "示例证据。",
                    },
                ],
            )
        ]

        analysis = analyze_endpoint_validity(results, schema)

        self.assertEqual(analysis["invalid_count"], 3)
        combos_by_reason = {
            combo["reason"]: combo for combo in analysis["invalid_combinations"]
        }
        self.assertEqual(
            set(combos_by_reason),
            {"missing_source", "missing_target", "missing_endpoint"},
        )
        self.assertEqual(
            combos_by_reason["missing_target"]["source_type"], "AlgorithmOrMethod"
        )
        self.assertIsNone(combos_by_reason["missing_target"]["target_type"])
        for combo in combos_by_reason.values():
            self.assertEqual(
                combo["suggested_action"], "complete_entity_or_skip_relation"
            )

    def test_invalid_relation_type_excluded_from_denominator(self):
        results = [
            _success_result(
                [{"id": "e1", "title": "A", "type": "Concept"}],
                [{"source": "A", "target": "A", "type": "totally_bogus"}],
            )
        ]
        self.assertEqual(compute_endpoint_valid_rate(results, RELATION_SCHEMA), 0.0)

    def test_normalization_matches_titles(self):
        results = [
            _success_result(
                [
                    {"id": "e1", "title": " 操作系统 ", "type": "Course"},
                    {"id": "e2", "title": "第一章", "type": "Chapter"},
                ],
                [{"source": "操作系统", "target": " 第一章", "type": "contains"}],
            )
        ]
        self.assertEqual(compute_endpoint_valid_rate(results, RELATION_SCHEMA), 1.0)

    def test_defined_by_term_symbol_endpoint_is_valid(self):
        results = [
            _success_result(
                [
                    {"id": "e1", "title": "工作集", "type": "Concept"},
                    {"id": "e2", "title": "Δ", "type": "Term"},
                ],
                [
                    {
                        "source": "工作集",
                        "target": "Δ",
                        "type": "defined_by",
                        "description": "工作集由窗口尺寸符号 Δ 参数化定义。",
                        "evidence": "变量 Δ 称为工作集的窗口尺寸。",
                    }
                ],
            )
        ]
        self.assertEqual(compute_endpoint_valid_rate(results, RELATION_SCHEMA), 1.0)

    def test_defined_by_term_alias_endpoint_is_invalid(self):
        results = [
            _success_result(
                [
                    {"id": "e1", "title": "TLB", "type": "Concept"},
                    {"id": "e2", "title": "Translation Lookaside Buffer", "type": "Term"},
                ],
                [
                    {
                        "source": "TLB",
                        "target": "Translation Lookaside Buffer",
                        "type": "defined_by",
                        "description": "TLB 是 Translation Lookaside Buffer 的缩写。",
                        "evidence": "Translation Lookaside Buffer，TLB",
                    }
                ],
            )
        ]
        analysis = analyze_endpoint_validity(results, RELATION_SCHEMA)
        self.assertEqual(analysis["valid_rate"], 0.0)
        self.assertEqual(analysis["invalid_count"], 1)
        self.assertEqual(
            analysis["invalid_combinations"][0]["suggested_action"],
            "alias_not_relation",
        )

    def test_endpoint_analysis_groups_invalid_combinations_with_suggested_action(self):
        results = [
            _success_result(
                [
                    {"id": "e1", "title": "关键字", "type": "Concept"},
                    {"id": "e2", "title": "记录", "type": "Concept"},
                ],
                [
                    {
                        "source": "关键字",
                        "target": "记录",
                        "type": "contains",
                        "description": "关键字和记录在同一段出现。",
                        "evidence": "关键字是唯一能标识一个记录的数据项。",
                    }
                ],
            )
        ]
        analysis = analyze_endpoint_validity(results, RELATION_SCHEMA)
        self.assertEqual(analysis["total"], 1)
        self.assertEqual(analysis["valid"], 0)
        self.assertEqual(analysis["invalid_combinations"][0]["relation_type"], "contains")
        self.assertIn("suggested_action", analysis["invalid_combinations"][0])

    def test_contains_inverse_derivation_is_limited_to_structural_sources(self):
        schema = {
            "contains": {
                "inverse_of": "belongs_to",
                "derivation_constraints": {
                    "derive_inverse_only_when_source_types": ["Course", "Chapter", "Section"]
                },
            }
        }
        self.assertTrue(can_derive_inverse_relation("contains", "Course", schema))
        self.assertFalse(can_derive_inverse_relation("contains", "Concept", schema))


class TestDuplicateAndNoise(unittest.TestCase):
    def test_duplicate_rate_counts_extras(self):
        results = [
            _success_result(
                [
                    {"id": "e1", "title": "进程", "type": "Concept"},
                    {"id": "e2", "title": "进程", "type": "Concept"},
                    {"id": "e3", "title": "进程 ", "type": "Concept"},
                    {"id": "e4", "title": "线程", "type": "Concept"},
                ],
                [],
            )
        ]
        self.assertEqual(compute_duplicate_entity_rate(results), 0.5)

    def test_duplicate_rate_same_title_different_type_ok(self):
        results = [
            _success_result(
                [
                    {"id": "e1", "title": "PCB", "type": "Term"},
                    {"id": "e2", "title": "PCB", "type": "Concept"},
                ],
                [],
            )
        ]
        self.assertEqual(compute_duplicate_entity_rate(results), 0.0)

    def test_duplicate_rate_per_sample_only(self):
        results = [
            _success_result(
                [{"id": "e1", "title": "进程", "type": "Concept"}], []
            ),
            _success_result(
                [{"id": "e2", "title": "进程", "type": "Concept"}], []
            ),
        ]
        self.assertEqual(compute_duplicate_entity_rate(results), 0.0)

    def test_noise_rate_empty_title(self):
        results = [
            _success_result(
                [
                    {"id": "e1", "title": "   ", "type": "Term"},
                    {"id": "e2", "title": "操作系统", "type": "Course"},
                ],
                [],
            )
        ]
        self.assertEqual(compute_noise_entity_rate(results), 0.5)

    def test_noise_rate_numeric_and_punct_and_stopword(self):
        results = [
            _success_result(
                [
                    {"id": "e1", "title": "123", "type": "Term"},
                    {"id": "e2", "title": "。。。", "type": "Term"},
                    {"id": "e3", "title": "本章", "type": "Concept"},
                    {"id": "e4", "title": "图", "type": "Concept"},
                    {"id": "e5", "title": "操作系统", "type": "Course"},
                ],
                [],
            )
        ]
        self.assertAlmostEqual(compute_noise_entity_rate(results), 4 / 5)

    def test_noise_rate_allows_english_abbrev_of_len_2(self):
        results = [
            _success_result(
                [{"id": "e1", "title": "OS", "type": "Course"}], []
            )
        ]
        self.assertEqual(compute_noise_entity_rate(results), 0.0)


class TestOutputStability(unittest.TestCase):
    def test_constant_counts_is_one(self):
        results = [
            _success_result(
                [{"id": "e1", "title": "A", "type": "Course"},
                 {"id": "e2", "title": "B", "type": "Course"}],
                [{"source": "A", "target": "B", "type": "contains"}],
            ),
            _success_result(
                [{"id": "e3", "title": "C", "type": "Course"},
                 {"id": "e4", "title": "D", "type": "Course"}],
                [{"source": "C", "target": "D", "type": "contains"}],
            ),
        ]
        self.assertEqual(compute_output_stability(results), 1.0)

    def test_single_success_sample_returns_one(self):
        results = [_success_result(
            [{"id": "e1", "title": "A", "type": "Course"}], []
        )]
        self.assertEqual(compute_output_stability(results), 1.0)

    def test_empty_returns_one(self):
        self.assertEqual(compute_output_stability([]), 1.0)

    def test_high_variance_lowers_score(self):
        results = [
            _success_result(
                [{"id": f"e{i}", "title": f"T{i}", "type": "Course"} for i in range(10)],
                [],
            ),
            _success_result(
                [{"id": "e1", "title": "A", "type": "Course"}], []
            ),
        ]
        self.assertLess(compute_output_stability(results), 1.0)
        self.assertGreaterEqual(compute_output_stability(results), 0.0)


class TestAggregateAndRank(unittest.TestCase):
    def test_composite_score_all_ones(self):
        metrics = {name: 1.0 for name in [
            "parse_success_rate", "schema_hit_rate",
            "entity_type_valid_rate", "relation_type_valid_rate",
            "endpoint_valid_rate", "duplicate_complement",
            "noise_complement", "output_stability",
            "audit_entity_recall", "audit_relation_recall",
        ]}
        self.assertAlmostEqual(compute_composite_score(metrics, DEFAULT_WEIGHTS), 1.0)

    def test_composite_score_weights_missing_audit_redistributes(self):
        metrics = {
            "parse_success_rate": 1.0,
            "schema_hit_rate": 1.0,
            "entity_type_valid_rate": 1.0,
            "relation_type_valid_rate": 1.0,
            "endpoint_valid_rate": 1.0,
            "duplicate_complement": 1.0,
            "noise_complement": 1.0,
            "output_stability": 1.0,
            "audit_entity_recall": None,
            "audit_relation_recall": None,
        }
        self.assertAlmostEqual(
            compute_composite_score(metrics, DEFAULT_WEIGHTS), 1.0
        )

    def test_rank_candidates_sorts_by_score_desc(self):
        summaries = {
            "alpha": {"composite_score": 0.6, "parse_success_rate": 1.0,
                      "endpoint_valid_rate": 0.9},
            "beta":  {"composite_score": 0.8, "parse_success_rate": 0.9,
                      "endpoint_valid_rate": 0.9},
            "gamma": {"composite_score": 0.6, "parse_success_rate": 1.0,
                      "endpoint_valid_rate": 0.95},
        }
        ranked = rank_candidates(summaries)
        self.assertEqual([r["candidate"] for r in ranked], ["beta", "gamma", "alpha"])

    def test_select_top_k_k_limits(self):
        summaries = {
            "a": {"composite_score": 0.1, "parse_success_rate": 0.0,
                  "endpoint_valid_rate": 0.0},
            "b": {"composite_score": 0.9, "parse_success_rate": 1.0,
                  "endpoint_valid_rate": 1.0},
        }
        ranked = rank_candidates(summaries)
        top = select_top_k(ranked, k=1)
        self.assertEqual(len(top), 1)
        self.assertEqual(top[0]["candidate"], "b")

    def test_aggregate_candidate_metrics_returns_all_required_fields(self):
        entity_type_names = ["Course", "Chapter"]
        relation_schema = {
            "contains": {"source_types": ["Course"], "target_types": ["Chapter"]}
        }
        results = [
            _success_result(
                [
                    {"id": "e1", "title": "OS", "type": "Course"},
                    {"id": "e2", "title": "Ch1", "type": "Chapter"},
                ],
                [{"source": "OS", "target": "Ch1", "type": "contains"}],
            )
        ]
        metrics = aggregate_candidate_metrics(
            results,
            entity_type_names=entity_type_names,
            relation_type_names=list(relation_schema),
            relation_schema=relation_schema,
            audit_entity_recall=None,
            audit_relation_recall=None,
        )
        expected_keys = {
            "parse_success_rate", "schema_hit_rate", "entity_type_valid_rate",
            "relation_type_valid_rate", "endpoint_valid_rate",
            "duplicate_entity_rate", "noise_entity_rate", "output_stability",
            "duplicate_complement", "noise_complement",
            "audit_entity_recall", "audit_relation_recall",
            "sample_count", "success_count",
        }
        self.assertTrue(expected_keys.issubset(metrics.keys()))
        self.assertEqual(metrics["parse_success_rate"], 1.0)
        self.assertEqual(metrics["endpoint_valid_rate"], 1.0)


class TestCompositeHardSoftSplit(unittest.TestCase):
    """Step 3: 硬指标和软指标分开计算，用于硬门槛 + 软指标排序。"""

    def test_composite_hard_all_ones(self):
        metrics = {k: 1.0 for k in HARD_METRIC_KEYS}
        self.assertAlmostEqual(compute_composite_hard(metrics, DEFAULT_WEIGHTS), 1.0)

    def test_composite_hard_all_half(self):
        metrics = {k: 0.5 for k in HARD_METRIC_KEYS}
        self.assertAlmostEqual(compute_composite_hard(metrics, DEFAULT_WEIGHTS), 0.5)

    def test_composite_hard_ignores_soft_metrics(self):
        # 软指标全为 0 不应该压低 composite_hard
        metrics = {k: 1.0 for k in HARD_METRIC_KEYS}
        for k in SOFT_METRIC_KEYS:
            metrics[k] = 0.0
        self.assertAlmostEqual(compute_composite_hard(metrics, DEFAULT_WEIGHTS), 1.0)

    def test_composite_soft_all_ones(self):
        metrics = {k: 1.0 for k in SOFT_METRIC_KEYS}
        self.assertAlmostEqual(compute_composite_soft(metrics, DEFAULT_WEIGHTS), 1.0)

    def test_composite_soft_with_missing_audit_redistributes(self):
        metrics = {
            "output_stability": 0.8,
            "audit_entity_recall": None,
            "audit_entity_precision": None,
            "audit_relation_recall": None,
        }
        # 其他 None → 全部权重摊到 stability，结果 = 0.8
        self.assertAlmostEqual(compute_composite_soft(metrics, DEFAULT_WEIGHTS), 0.8)

    def test_composite_soft_empty_returns_zero(self):
        metrics: dict[str, float | None] = {k: None for k in SOFT_METRIC_KEYS}
        self.assertEqual(compute_composite_soft(metrics, DEFAULT_WEIGHTS), 0.0)


class TestGatePassed(unittest.TestCase):
    def test_gate_passed_all_above_threshold(self):
        metrics = {k: 1.0 for k in HARD_METRIC_KEYS}
        self.assertTrue(compute_gate_passed(metrics))

    def test_gate_passed_exactly_at_threshold(self):
        metrics = {k: GATE_THRESHOLD for k in HARD_METRIC_KEYS}
        self.assertTrue(compute_gate_passed(metrics))

    def test_gate_failed_when_any_hard_below_threshold(self):
        metrics = {k: 1.0 for k in HARD_METRIC_KEYS}
        metrics["endpoint_valid_rate"] = GATE_THRESHOLD - 0.01
        self.assertFalse(compute_gate_passed(metrics))

    def test_gate_failed_when_any_hard_missing(self):
        metrics: dict[str, float | None] = {k: 1.0 for k in HARD_METRIC_KEYS}
        metrics["duplicate_complement"] = None
        self.assertFalse(compute_gate_passed(metrics))


class TestAggregateCarriesGateAndComposites(unittest.TestCase):
    def test_aggregate_includes_composite_hard_soft_gate(self):
        entity_type_names = ["Course", "Chapter"]
        relation_schema = {
            "contains": {"source_types": ["Course"], "target_types": ["Chapter"]}
        }
        results = [
            _success_result(
                [
                    {"id": "e1", "title": "OS", "type": "Course"},
                    {"id": "e2", "title": "Ch1", "type": "Chapter"},
                ],
                [{"source": "OS", "target": "Ch1", "type": "contains"}],
            )
        ]
        metrics = aggregate_candidate_metrics(
            results,
            entity_type_names=entity_type_names,
            relation_type_names=list(relation_schema),
            relation_schema=relation_schema,
            audit_entity_recall=None,
            audit_relation_recall=None,
        )
        self.assertIn("composite_hard", metrics)
        self.assertIn("composite_soft", metrics)
        self.assertIn("gate_passed", metrics)
        self.assertTrue(metrics["gate_passed"])
        self.assertAlmostEqual(metrics["composite_hard"], 1.0)


class TestRankWithGate(unittest.TestCase):
    def test_gated_candidate_outranks_failed_even_with_lower_composite(self):
        # A: gate 过 + composite/soft 偏低；B: gate 没过 + composite/soft 更高
        summaries = {
            "A_passed": {
                "gate_passed": True,
                "composite_score": 0.80,
                "composite_soft": 0.30,
                "parse_success_rate": 1.0,
                "endpoint_valid_rate": 1.0,
            },
            "B_failed": {
                "gate_passed": False,
                "composite_score": 0.85,
                "composite_soft": 0.80,
                "parse_success_rate": 1.0,
                "endpoint_valid_rate": 1.0,
            },
        }
        ranked = rank_candidates(summaries)
        self.assertEqual(ranked[0]["candidate"], "A_passed")
        self.assertEqual(ranked[1]["candidate"], "B_failed")

    def test_all_gated_ranked_by_composite_soft(self):
        summaries = {
            "x_lower_soft_higher_score": {
                "gate_passed": True,
                "composite_score": 0.95,
                "composite_soft": 0.40,
                "parse_success_rate": 1.0,
                "endpoint_valid_rate": 1.0,
            },
            "y_higher_soft_lower_score": {
                "gate_passed": True,
                "composite_score": 0.92,
                "composite_soft": 0.60,
                "parse_success_rate": 1.0,
                "endpoint_valid_rate": 1.0,
            },
        }
        ranked = rank_candidates(summaries)
        self.assertEqual(ranked[0]["candidate"], "y_higher_soft_lower_score")

    def test_composite_score_breaks_ties_when_soft_equal(self):
        summaries = {
            "lower_score": {
                "gate_passed": True,
                "composite_score": 0.88,
                "composite_soft": 0.50,
                "parse_success_rate": 1.0,
                "endpoint_valid_rate": 1.0,
            },
            "higher_score": {
                "gate_passed": True,
                "composite_score": 0.92,
                "composite_soft": 0.50,
                "parse_success_rate": 1.0,
                "endpoint_valid_rate": 1.0,
            },
        }
        ranked = rank_candidates(summaries)
        self.assertEqual(ranked[0]["candidate"], "higher_score")


if __name__ == "__main__":
    unittest.main()
