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
    compute_endpoint_valid_rate,
    compute_entity_type_valid_rate,
    compute_parse_success_rate,
    compute_relation_type_valid_rate,
    compute_schema_hit_rate,
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


if __name__ == "__main__":
    unittest.main()
