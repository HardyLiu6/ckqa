from __future__ import annotations

import csv
import json
import sys
import tempfile
import unittest
from pathlib import Path

_PROJECT_ROOT = Path(__file__).resolve().parent.parent
_SCRIPTS_DIR = _PROJECT_ROOT / "scripts"
if str(_SCRIPTS_DIR) not in sys.path:
    sys.path.insert(0, str(_SCRIPTS_DIR))

from score_extraction_results import score_extraction_results


ENTITY_SCHEMA = {
    "schema_version": "v1",
    "entity_type_order": ["Course", "Chapter"],
    "entity_types": {
        "Course": {"label_zh": "课程", "description": "课程"},
        "Chapter": {"label_zh": "章节", "description": "章节"},
    },
}

RELATION_SCHEMA = {
    "schema_version": "v1",
    "relation_type_order": ["contains"],
    "relation_types": {
        "contains": {
            "label_zh": "包含", "description": "包含",
            "source_types": ["Course"], "target_types": ["Chapter"],
        }
    },
}


def _make_eval(candidate: str, success_entities: int) -> dict:
    entities = [
        {"id": "e1", "title": "操作系统", "type": "Course",
         "description": "", "evidence": ""}
    ]
    relationships = []
    for i in range(success_entities):
        chapter_title = f"第{i + 1}章"
        entities.append(
            {"id": f"e{i + 2}", "title": chapter_title, "type": "Chapter",
             "description": "", "evidence": ""}
        )
        relationships.append(
            {"source": "操作系统", "target": chapter_title, "type": "contains",
             "description": "", "evidence": ""}
        )
    return {
        "task": "candidate_extraction",
        "candidate": candidate,
        "model": "test",
        "samples_file": "samples.json",
        "manifest_file": "manifest.json",
        "summary": {"total": 1, "success": 1, "parse_error": 0, "llm_error": 0},
        "results": [
            {
                "sample_id": "s1",
                "candidate": candidate,
                "status": "success",
                "entities": entities,
                "relationships": relationships,
                "raw_output": "",
                "error": None,
                "parser_error_code": None,
                "llm_debug": None,
            }
        ],
    }


class TestEndToEnd(unittest.TestCase):
    def test_two_candidates_ranked_and_reports_written(self):
        with tempfile.TemporaryDirectory() as tmp:
            root = Path(tmp)
            (root / "results" / "extraction_eval").mkdir(parents=True)
            (root / "config" / "schema").mkdir(parents=True)
            (root / "results" / "reports").mkdir(parents=True)

            (root / "config" / "schema" / "entity_types.json").write_text(
                json.dumps(ENTITY_SCHEMA, ensure_ascii=False), encoding="utf-8"
            )
            (root / "config" / "schema" / "relation_types.json").write_text(
                json.dumps(RELATION_SCHEMA, ensure_ascii=False), encoding="utf-8"
            )
            (root / "results" / "extraction_eval" / "alpha.json").write_text(
                json.dumps(_make_eval("alpha", 1), ensure_ascii=False),
                encoding="utf-8",
            )
            (root / "results" / "extraction_eval" / "beta.json").write_text(
                json.dumps(_make_eval("beta", 3), ensure_ascii=False),
                encoding="utf-8",
            )

            summary = score_extraction_results(
                root=root,
                eval_dir=None,
                entity_schema_path=None,
                relation_schema_path=None,
                audit_path=None,
                weights=None,
                top_k=1,
                overwrite=True,
            )

            self.assertEqual(summary["status"], "success")
            csv_path = root / "results" / "reports" / "extraction_compare.csv"
            md_path = root / "results" / "reports" / "extraction_compare.md"
            top_path = root / "results" / "reports" / "top_candidates.json"
            self.assertTrue(csv_path.exists())
            self.assertTrue(md_path.exists())
            self.assertTrue(top_path.exists())

            with csv_path.open(encoding="utf-8") as fp:
                rows = list(csv.reader(fp))
            self.assertEqual(rows[0][:2], ["rank", "candidate"])
            self.assertEqual(len(rows), 3)

            top_payload = json.loads(top_path.read_text(encoding="utf-8"))
            self.assertEqual(top_payload["k"], 1)
            self.assertEqual(len(top_payload["top_candidates"]), 1)


if __name__ == "__main__":
    unittest.main()
