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
    aggregate_candidate_metrics,
    compute_composite_score,
    compute_duplicate_entity_rate,
    compute_endpoint_valid_rate,
    compute_entity_type_valid_rate,
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


if __name__ == "__main__":
    unittest.main()
