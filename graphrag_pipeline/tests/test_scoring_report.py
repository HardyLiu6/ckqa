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

from scoring_report import (
    append_history_csv,
    write_extraction_compare_csv,
    write_extraction_compare_markdown,
    write_latest_pointer,
    write_run_meta,
    write_top_candidates_json,
)


RANKED = [
    {
        "rank": 1, "candidate": "beta",
        "composite_score": 0.82, "parse_success_rate": 1.0,
        "schema_hit_rate": 0.8, "entity_type_valid_rate": 0.9,
        "relation_type_valid_rate": 0.9, "endpoint_valid_rate": 0.85,
        "duplicate_entity_rate": 0.05, "noise_entity_rate": 0.02,
        "duplicate_complement": 0.95, "noise_complement": 0.98,
        "output_stability": 0.9,
        "audit_entity_recall": 0.7, "audit_relation_recall": 0.6,
        "sample_count": 5, "success_count": 5,
    },
    {
        "rank": 2, "candidate": "alpha",
        "composite_score": 0.60, "parse_success_rate": 0.8,
        "schema_hit_rate": 0.6, "entity_type_valid_rate": 0.7,
        "relation_type_valid_rate": 0.7, "endpoint_valid_rate": 0.5,
        "duplicate_entity_rate": 0.1, "noise_entity_rate": 0.1,
        "duplicate_complement": 0.9, "noise_complement": 0.9,
        "output_stability": 0.7,
        "audit_entity_recall": 0.4, "audit_relation_recall": 0.3,
        "sample_count": 5, "success_count": 4,
    },
]


class TestReportWriters(unittest.TestCase):
    def test_write_csv_columns_and_order(self):
        with tempfile.TemporaryDirectory() as tmp:
            path = Path(tmp) / "compare.csv"
            write_extraction_compare_csv(path, RANKED)
            with path.open(encoding="utf-8") as fp:
                rows = list(csv.reader(fp))
            self.assertEqual(rows[0][:3], ["rank", "candidate", "composite_score"])
            self.assertEqual(rows[1][1], "beta")
            self.assertEqual(rows[2][1], "alpha")

    def test_write_markdown_contains_table_and_top_section(self):
        with tempfile.TemporaryDirectory() as tmp:
            path = Path(tmp) / "compare.md"
            write_extraction_compare_markdown(
                path, RANKED, weights={"parse_success_rate": 1.0}, top_k=1
            )
            content = path.read_text(encoding="utf-8")
        self.assertIn("| rank | candidate |", content)
        self.assertIn("## Top Candidates", content)
        self.assertIn("beta", content)

    def test_write_top_candidates_json_structure(self):
        with tempfile.TemporaryDirectory() as tmp:
            path = Path(tmp) / "top.json"
            write_top_candidates_json(
                path,
                ranked=RANKED,
                k=1,
                weights={"parse_success_rate": 1.0},
                inputs={"samples_file": "samples.json"},
            )
            data = json.loads(path.read_text(encoding="utf-8"))
        self.assertEqual(data["k"], 1)
        self.assertEqual(len(data["top_candidates"]), 1)
        self.assertEqual(data["top_candidates"][0]["candidate"], "beta")
        self.assertIn("weights", data)
        self.assertIn("inputs", data)


class TestRunMetaAndHistory(unittest.TestCase):
    def test_write_run_meta_contains_required_fields(self):
        with tempfile.TemporaryDirectory() as tmp:
            path = Path(tmp) / "run_meta.json"
            write_run_meta(
                path,
                run_id="2026-04-18T120000",
                timestamp="2026-04-18T12:00:00",
                git_sha="abc1234",
                inputs={"eval_dir": "x"},
                weights={"parse_success_rate": 1.0},
                top_k=2,
                top_candidates=["beta", "alpha"],
                total_candidates=2,
            )
            data = json.loads(path.read_text(encoding="utf-8"))
        self.assertEqual(data["run_id"], "2026-04-18T120000")
        self.assertEqual(data["timestamp"], "2026-04-18T12:00:00")
        self.assertEqual(data["git_sha"], "abc1234")
        self.assertEqual(data["top_k"], 2)
        self.assertEqual(data["top_candidates"], ["beta", "alpha"])
        self.assertEqual(data["total_candidates"], 2)
        self.assertIn("inputs", data)
        self.assertIn("weights", data)

    def test_append_history_csv_creates_header_on_first_write(self):
        with tempfile.TemporaryDirectory() as tmp:
            path = Path(tmp) / "history.csv"
            append_history_csv(
                path,
                run_id="2026-04-18T120000",
                timestamp="2026-04-18T12:00:00",
                ranked=RANKED,
            )
            with path.open(encoding="utf-8") as fp:
                rows = list(csv.reader(fp))
        self.assertEqual(rows[0][:3], ["run_id", "timestamp", "rank"])
        self.assertEqual(rows[1][0], "2026-04-18T120000")
        self.assertEqual(rows[1][3], "beta")  # candidate 列
        self.assertEqual(len(rows), 1 + len(RANKED))

    def test_append_history_csv_appends_without_rewriting_header(self):
        with tempfile.TemporaryDirectory() as tmp:
            path = Path(tmp) / "history.csv"
            append_history_csv(
                path,
                run_id="run-A",
                timestamp="2026-04-18T12:00:00",
                ranked=RANKED,
            )
            append_history_csv(
                path,
                run_id="run-B",
                timestamp="2026-04-18T13:00:00",
                ranked=RANKED,
            )
            with path.open(encoding="utf-8") as fp:
                rows = list(csv.reader(fp))
        self.assertEqual(rows[0][:1], ["run_id"])
        self.assertEqual(len([r for r in rows if r[0] == "run-A"]), len(RANKED))
        self.assertEqual(len([r for r in rows if r[0] == "run-B"]), len(RANKED))
        # 只有一个表头行
        self.assertEqual(len(rows), 1 + 2 * len(RANKED))

    def test_write_latest_pointer(self):
        with tempfile.TemporaryDirectory() as tmp:
            path = Path(tmp) / "latest.json"
            write_latest_pointer(
                path,
                run_id="2026-04-18T120000",
                run_dir="runs/2026-04-18T120000",
            )
            data = json.loads(path.read_text(encoding="utf-8"))
        self.assertEqual(data["run_id"], "2026-04-18T120000")
        self.assertEqual(data["run_dir"], "runs/2026-04-18T120000")


if __name__ == "__main__":
    unittest.main()
