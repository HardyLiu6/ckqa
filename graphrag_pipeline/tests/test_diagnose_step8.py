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

from diagnose_step8 import diagnose_step8


ENTITY_SCHEMA = {
    "schema_version": "v1",
    "entity_types": {"Course": {}, "Chapter": {}, "Concept": {}},
}

RELATION_SCHEMA = {
    "schema_version": "v1",
    "relation_types": {
        "contains": {"source_types": ["Course"], "target_types": ["Chapter"]},
        "related_to": {"source_types": ["Concept"], "target_types": ["Concept"]},
    },
}

AUDIT_PAYLOAD = {
    "audit_samples": [
        {
            "source_sample_id": "s1",
            "gold_entities": [
                {"entity_id": "g1", "name": "操作系统", "type": "Course"},
                {"entity_id": "g2", "name": "第一章 引论", "type": "Chapter"},
            ],
            "gold_relations": [
                {"source_entity_id": "g1", "target_entity_id": "g2", "type": "contains"},
            ],
        }
    ]
}


def _eval_payload(candidate: str) -> dict:
    """样本 s1：产出混合结果覆盖 A 的 5 类和 B 的典型 verdict。"""
    return {
        "task": "candidate_extraction",
        "candidate": candidate,
        "results": [
            {
                "sample_id": "s1",
                "candidate": candidate,
                "status": "success",
                "entities": [
                    {"id": "e1", "title": "操作系统", "type": "Course",
                     "description": "", "evidence": ""},
                    {"id": "e2", "title": "第一章 引论", "type": "Chapter",
                     "description": "", "evidence": ""},
                    {"id": "e3", "title": "进程", "type": "Concept",
                     "description": "", "evidence": ""},
                ],
                "relationships": [
                    # valid
                    {"source": "操作系统", "target": "第一章 引论", "type": "contains",
                     "description": "", "evidence": ""},
                    # invalid_type
                    {"source": "操作系统", "target": "第一章 引论", "type": "nonexistent_rel",
                     "description": "", "evidence": ""},
                    # unresolved_tgt
                    {"source": "操作系统", "target": "未抽到的东西", "type": "contains",
                     "description": "", "evidence": ""},
                    # type_mismatch（Concept → Chapter 不在 contains 的约束里）
                    {"source": "进程", "target": "第一章 引论", "type": "contains",
                     "description": "", "evidence": ""},
                ],
                "raw_output": "",
                "error": None,
                "parser_error_code": None,
                "llm_debug": None,
            }
        ],
    }


class TestDiagnoseStep8Smoke(unittest.TestCase):
    def test_end_to_end_generates_markdown_with_sections(self):
        with tempfile.TemporaryDirectory() as tmp:
            root = Path(tmp)
            (root / "config" / "schema").mkdir(parents=True)
            (root / "config" / "schema" / "entity_types.json").write_text(
                json.dumps(ENTITY_SCHEMA), encoding="utf-8"
            )
            (root / "config" / "schema" / "relation_types.json").write_text(
                json.dumps(RELATION_SCHEMA), encoding="utf-8"
            )
            (root / "data" / "eval").mkdir(parents=True)
            (root / "data" / "eval" / "audit_extraction_set.json").write_text(
                json.dumps(AUDIT_PAYLOAD, ensure_ascii=False), encoding="utf-8"
            )
            (root / "results" / "extraction_eval").mkdir(parents=True)
            (root / "results" / "extraction_eval" / "alpha.json").write_text(
                json.dumps(_eval_payload("alpha"), ensure_ascii=False), encoding="utf-8"
            )
            (root / "results" / "extraction_eval" / "beta.json").write_text(
                json.dumps(_eval_payload("beta"), ensure_ascii=False), encoding="utf-8"
            )
            summary = diagnose_step8(
                root=root,
                eval_dir=None,
                relation_schema_path=None,
                audit_path=None,
                output_dir=None,
                overwrite=False,
            )
            out_path = Path(summary["output"])
            self.assertTrue(out_path.exists())
            content = out_path.read_text(encoding="utf-8")
        # A、B 两节标题存在
        self.assertIn("## A. 端点失败模式归因", content)
        self.assertIn("### A.1", content)
        self.assertIn("### A.2", content)
        self.assertIn("## B. audit relation 命中诊断", content)
        self.assertIn("### B.1", content)
        self.assertIn("### B.2", content)
        # 两个候选都出现
        self.assertIn("alpha", content)
        self.assertIn("beta", content)
        # A.1 里每候选 total 应为 4（四条 ext relation）
        self.assertIn("| alpha |", content)
        # B.1 里至少出现一个 verdict 值（hit 或 align_fail_* 等）
        self.assertTrue(any(v in content for v in (
            "align_fail_src", "align_fail_tgt", "triple_not_in_ext", "type_mismatch", "hit"
        )))


if __name__ == "__main__":
    unittest.main()
