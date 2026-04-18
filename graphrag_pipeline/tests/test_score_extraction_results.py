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

            # 旧产物仍然落到 results/reports/ 根目录（兼容层）
            legacy_csv = root / "results" / "reports" / "extraction_compare.csv"
            legacy_md = root / "results" / "reports" / "extraction_compare.md"
            legacy_top = root / "results" / "reports" / "top_candidates.json"
            self.assertTrue(legacy_csv.exists())
            self.assertTrue(legacy_md.exists())
            self.assertTrue(legacy_top.exists())

            # 新布局：extraction_scoring/runs/<run_id>/
            scoring_root = root / "results" / "reports" / "extraction_scoring"
            runs_dir = scoring_root / "runs"
            self.assertTrue(runs_dir.exists())
            run_subdirs = list(runs_dir.iterdir())
            self.assertEqual(len(run_subdirs), 1)
            run_dir = run_subdirs[0]
            self.assertTrue((run_dir / "extraction_compare.csv").exists())
            self.assertTrue((run_dir / "extraction_compare.md").exists())
            self.assertTrue((run_dir / "top_candidates.json").exists())
            self.assertTrue((run_dir / "run_meta.json").exists())

            # history.csv：表头 + 每候选一行
            history_path = scoring_root / "history.csv"
            self.assertTrue(history_path.exists())
            with history_path.open(encoding="utf-8") as fp:
                rows = list(csv.reader(fp))
            self.assertEqual(rows[0][:3], ["run_id", "timestamp", "rank"])
            self.assertEqual(len(rows), 1 + 2)  # 2 candidates

            # latest.json 指向刚才的 run
            latest_path = scoring_root / "latest.json"
            self.assertTrue(latest_path.exists())
            latest = json.loads(latest_path.read_text(encoding="utf-8"))
            self.assertEqual(latest["run_id"], run_dir.name)

            # summary 中暴露 run_id 与新路径
            self.assertIn("run_id", summary)
            self.assertEqual(summary["run_id"], run_dir.name)
            self.assertIn("run_dir", summary["reports"])

    def test_second_run_appends_history_and_updates_latest(self):
        with tempfile.TemporaryDirectory() as tmp:
            root = Path(tmp)
            (root / "results" / "extraction_eval").mkdir(parents=True)
            (root / "config" / "schema").mkdir(parents=True)

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

            summary_a = score_extraction_results(
                root=root, eval_dir=None, entity_schema_path=None,
                relation_schema_path=None, audit_path=None, weights=None,
                top_k=1, overwrite=True, run_id="2026-04-18T120000",
            )
            summary_b = score_extraction_results(
                root=root, eval_dir=None, entity_schema_path=None,
                relation_schema_path=None, audit_path=None, weights=None,
                top_k=1, overwrite=True, run_id="2026-04-18T130000",
            )

            scoring_root = root / "results" / "reports" / "extraction_scoring"
            run_dirs = sorted((scoring_root / "runs").iterdir())
            self.assertEqual(len(run_dirs), 2)
            self.assertEqual([p.name for p in run_dirs],
                             ["2026-04-18T120000", "2026-04-18T130000"])

            history_path = scoring_root / "history.csv"
            with history_path.open(encoding="utf-8") as fp:
                rows = list(csv.reader(fp))
            self.assertEqual(len(rows), 1 + 2)  # 两次 run，各 1 candidate

            latest = json.loads((scoring_root / "latest.json").read_text(encoding="utf-8"))
            self.assertEqual(latest["run_id"], "2026-04-18T130000")
            self.assertEqual(summary_a["run_id"], "2026-04-18T120000")
            self.assertEqual(summary_b["run_id"], "2026-04-18T130000")

    def test_run_meta_captures_git_sha_when_available(self):
        # 仓库内执行时应采集 git HEAD SHA；不是 git 仓库时应落到 None，不抛错
        with tempfile.TemporaryDirectory() as tmp:
            root = Path(tmp)
            (root / "results" / "extraction_eval").mkdir(parents=True)
            (root / "config" / "schema").mkdir(parents=True)
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

            summary = score_extraction_results(
                root=root, eval_dir=None, entity_schema_path=None,
                relation_schema_path=None, audit_path=None, weights=None,
                top_k=1, overwrite=True, run_id="2026-04-18T140000",
            )
            meta_path = Path(summary["reports"]["run_meta"])
            meta = json.loads(meta_path.read_text(encoding="utf-8"))
            # 非 git 根，git_sha 应为 None
            self.assertIn("git_sha", meta)
            self.assertIsNone(meta["git_sha"])


if __name__ == "__main__":
    unittest.main()
