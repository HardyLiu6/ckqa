from __future__ import annotations

import json
import sys
import tempfile
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
from scoring_audit import (
    compute_audit_entity_recall,
    compute_audit_relation_recall,
    load_audit_index,
)


AUDIT_PAYLOAD = {
    "audit_samples": [
        {
            "source_sample_id": "s1",
            "gold_entities": [
                {"entity_id": "ent-1", "name": "操作系统", "type": "Course",
                 "alias": [], "normalized_name": "操作系统"},
                {"entity_id": "ent-2", "name": "第一章 引论", "type": "Chapter",
                 "alias": [], "normalized_name": "第一章 引论"},
            ],
            "gold_relations": [
                {"relation_id": "rel-1", "source_entity_id": "ent-1",
                 "target_entity_id": "ent-2", "type": "contains"}
            ],
        }
    ]
}


def _success_result(sample_id, entities, relationships):
    return StructuredExtractionResult(
        sample_id=sample_id,
        candidate="c",
        status="success",
        entities=[ExtractionEntity(**e) for e in entities],
        relationships=[ExtractionRelationship(**r) for r in relationships],
        raw_output="",
    )


class TestAuditAlignment(unittest.TestCase):
    def setUp(self):
        self.tmpdir = tempfile.TemporaryDirectory()
        self.audit_path = Path(self.tmpdir.name) / "audit.json"
        self.audit_path.write_text(
            json.dumps(AUDIT_PAYLOAD, ensure_ascii=False), encoding="utf-8"
        )
        self.index = load_audit_index(self.audit_path)

    def tearDown(self):
        self.tmpdir.cleanup()

    def test_entity_recall_full_hit(self):
        results = [
            _success_result(
                "s1",
                [
                    {"id": "e1", "title": "操作系统", "type": "Course"},
                    {"id": "e2", "title": "第一章 引论", "type": "Chapter"},
                ],
                [],
            )
        ]
        self.assertEqual(compute_audit_entity_recall(results, self.index), 1.0)

    def test_entity_recall_half_hit(self):
        results = [
            _success_result(
                "s1",
                [{"id": "e1", "title": "操作系统", "type": "Course"}],
                [],
            )
        ]
        self.assertEqual(compute_audit_entity_recall(results, self.index), 0.5)

    def test_entity_recall_missing_sample_excluded(self):
        results = [_success_result("s_not_in_audit", [], [])]
        self.assertEqual(compute_audit_entity_recall(results, self.index), 0.0)

    def test_relation_recall_hit(self):
        results = [
            _success_result(
                "s1",
                [
                    {"id": "e1", "title": "操作系统", "type": "Course"},
                    {"id": "e2", "title": "第一章 引论", "type": "Chapter"},
                ],
                [{"source": "操作系统", "target": "第一章 引论", "type": "contains"}],
            )
        ]
        self.assertEqual(compute_audit_relation_recall(results, self.index), 1.0)

    def test_relation_recall_requires_type_match(self):
        results = [
            _success_result(
                "s1",
                [
                    {"id": "e1", "title": "操作系统", "type": "Course"},
                    {"id": "e2", "title": "第一章 引论", "type": "Chapter"},
                ],
                [{"source": "操作系统", "target": "第一章 引论", "type": "related_to"}],
            )
        ]
        self.assertEqual(compute_audit_relation_recall(results, self.index), 0.0)


if __name__ == "__main__":
    unittest.main()
