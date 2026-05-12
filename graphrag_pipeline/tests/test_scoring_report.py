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
        "composite_score": 0.82, "composite_hard": 0.95, "composite_soft": 0.60,
        "gate_passed": True,
        "parse_success_rate": 1.0,
        "schema_hit_rate": 0.8, "entity_type_valid_rate": 0.9,
        "relation_type_valid_rate": 0.9, "endpoint_valid_rate": 0.85,
        "endpoint_total_count": 20, "endpoint_invalid_count": 3,
        "duplicate_entity_rate": 0.05, "noise_entity_rate": 0.02,
        "duplicate_complement": 0.95, "noise_complement": 0.98,
        "output_stability": 0.9,
        "audit_entity_recall": 0.7, "audit_entity_precision": 0.65,
        "audit_relation_recall": 0.6,
        "parse_error_count": 0, "llm_error_count": 0,
        "strict_output_retry_count": 1, "output_leak_flag_count": 0,
        "sample_count": 5, "success_count": 5,
    },
    {
        "rank": 2, "candidate": "alpha",
        "composite_score": 0.60, "composite_hard": 0.70, "composite_soft": 0.38,
        "gate_passed": False,
        "parse_success_rate": 0.8,
        "schema_hit_rate": 0.6, "entity_type_valid_rate": 0.7,
        "relation_type_valid_rate": 0.7, "endpoint_valid_rate": 0.5,
        "endpoint_total_count": 16, "endpoint_invalid_count": 8,
        "duplicate_entity_rate": 0.1, "noise_entity_rate": 0.1,
        "duplicate_complement": 0.9, "noise_complement": 0.9,
        "output_stability": 0.7,
        "audit_entity_recall": 0.4, "audit_entity_precision": 0.35,
        "audit_relation_recall": 0.3,
        "parse_error_count": 1, "llm_error_count": 0,
        "strict_output_retry_count": 0, "output_leak_flag_count": 1,
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

    def test_append_history_csv_replaces_existing_rows_for_same_run_id(self):
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
                run_id="run-A",
                timestamp="2026-04-18T13:00:00",
                ranked=[RANKED[0]],
            )
            with path.open(encoding="utf-8") as fp:
                rows = list(csv.reader(fp))

        self.assertEqual(len([row for row in rows[1:] if row[0] == "run-A"]), 1)
        self.assertEqual(rows[1][1], "2026-04-18T13:00:00")

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


class TestNewColumnsInReport(unittest.TestCase):
    """Step 1 新增 audit_entity_precision 列应该出现在 CSV / history 里。"""

    def test_csv_contains_audit_entity_precision(self):
        with tempfile.TemporaryDirectory() as tmp:
            path = Path(tmp) / "compare.csv"
            write_extraction_compare_csv(path, RANKED)
            with path.open(encoding="utf-8") as fp:
                rows = list(csv.reader(fp))
        header = rows[0]
        self.assertIn("audit_entity_precision", header)
        # 数据行的该列应当有 0.65 和 0.35
        prec_idx = header.index("audit_entity_precision")
        self.assertEqual(rows[1][prec_idx], "0.6500")
        self.assertEqual(rows[2][prec_idx], "0.3500")

    def test_history_contains_audit_entity_precision(self):
        with tempfile.TemporaryDirectory() as tmp:
            path = Path(tmp) / "history.csv"
            append_history_csv(
                path,
                run_id="run-A",
                timestamp="2026-04-18T12:00:00",
                ranked=RANKED,
            )
            with path.open(encoding="utf-8") as fp:
                rows = list(csv.reader(fp))
        header = rows[0]
        self.assertIn("audit_entity_precision", header)

    def test_csv_contains_endpoint_counts_and_parse_leak_flags(self):
        with tempfile.TemporaryDirectory() as tmp:
            path = Path(tmp) / "compare.csv"
            write_extraction_compare_csv(path, RANKED)
            with path.open(encoding="utf-8") as fp:
                rows = list(csv.reader(fp))
        header = rows[0]
        for column in (
            "endpoint_total_count",
            "endpoint_invalid_count",
            "parse_error_count",
            "llm_error_count",
            "strict_output_retry_count",
            "output_leak_flag_count",
        ):
            self.assertIn(column, header)
        self.assertEqual(rows[1][header.index("endpoint_total_count")], "20")
        self.assertEqual(rows[2][header.index("output_leak_flag_count")], "1")


class TestHistoryCsvMigration(unittest.TestCase):
    """history.csv 已存在但表头是旧 schema 时，append_history_csv 应迁移表头并补空列。"""

    def test_migrates_old_header_and_pads_missing_columns(self):
        old_header = [
            "run_id", "timestamp", "rank", "candidate", "composite_score",
            "parse_success_rate", "schema_hit_rate", "entity_type_valid_rate",
            "relation_type_valid_rate", "endpoint_valid_rate",
            "duplicate_entity_rate", "noise_entity_rate", "output_stability",
            "audit_entity_recall", "audit_relation_recall",
            "sample_count", "success_count",
        ]
        old_row = [
            "run-0", "2026-04-10T00:00:00", "1", "legacy", "0.7000",
            "1.0000", "1.0000", "1.0000", "1.0000", "0.9000",
            "0.0000", "0.0000", "0.8000",
            "0.6000", "0.0000",
            "5", "5",
        ]
        with tempfile.TemporaryDirectory() as tmp:
            path = Path(tmp) / "history.csv"
            with path.open("w", encoding="utf-8", newline="") as fp:
                writer = csv.writer(fp)
                writer.writerow(old_header)
                writer.writerow(old_row)
            append_history_csv(
                path, run_id="run-A", timestamp="2026-04-18T12:00:00", ranked=RANKED
            )
            with path.open(encoding="utf-8") as fp:
                rows = list(csv.reader(fp))
        self.assertIn("audit_entity_precision", rows[0])
        prec_idx = rows[0].index("audit_entity_precision")
        # 旧行在新列位置应为空
        legacy_row = [r for r in rows[1:] if r[0] == "run-0"][0]
        self.assertEqual(legacy_row[prec_idx], "")
        # 旧行其他列内容保留
        cand_idx = rows[0].index("candidate")
        self.assertEqual(legacy_row[cand_idx], "legacy")
        # 新行该列有值
        new_rows = [r for r in rows[1:] if r[0] == "run-A"]
        self.assertEqual(len(new_rows), len(RANKED))
        self.assertEqual(new_rows[0][prec_idx], "0.6500")

    def test_reshapes_corrupted_rows_written_before_header_migration(self):
        """
        历史遗留：Step 1 开发期间曾用新 CSV_COLUMNS 向 17 列 header 追加 18 列行，
        出现列数错位。本测试验证 append_history_csv 能识别 "行的列数恰好等于当前 schema"
        的情况并按当前 schema 解释。Step 3 之后当前 schema 超出 18 列，这个 elif 分支
        失效，但行为不应 crash —— 能按旧 header zip 尽力映射即可。
        """
        old_header = [
            "run_id", "timestamp", "rank", "candidate", "composite_score",
            "parse_success_rate", "schema_hit_rate", "entity_type_valid_rate",
            "relation_type_valid_rate", "endpoint_valid_rate",
            "duplicate_entity_rate", "noise_entity_rate", "output_stability",
            "audit_entity_recall", "audit_relation_recall",
            "sample_count", "success_count",
        ]
        corrupted_row = [
            "run-X", "2026-04-18T23:00:00", "1", "default", "0.9077",
            "1.0000", "1.0000", "1.0000", "1.0000", "0.9070",
            "0.0000", "0.0000", "0.6730",
            "0.5357", "0.0000",
            "5", "5",
        ]
        with tempfile.TemporaryDirectory() as tmp:
            path = Path(tmp) / "history.csv"
            with path.open("w", encoding="utf-8", newline="") as fp:
                writer = csv.writer(fp)
                writer.writerow(old_header)
                writer.writerow(corrupted_row)
            append_history_csv(
                path, run_id="run-A", timestamp="2026-04-18T23:30:00", ranked=RANKED
            )
            with path.open(encoding="utf-8") as fp:
                rows = list(csv.reader(fp))
        # 迁移后 header 必须包含当前 CSV_COLUMNS 全部列
        for col in ("audit_entity_precision", "composite_hard", "composite_soft", "gate_passed"):
            self.assertIn(col, rows[0])
        # run-X 原有字段保留，新列填空
        cand_idx = rows[0].index("candidate")
        run_x_row = [r for r in rows[1:] if r[0] == "run-X"][0]
        self.assertEqual(run_x_row[cand_idx], "default")
        hard_idx = rows[0].index("composite_hard")
        self.assertEqual(run_x_row[hard_idx], "")


class TestStepThreeColumnsInReport(unittest.TestCase):
    """Step 3 新增 composite_hard / composite_soft / gate_passed 列。"""

    def test_csv_contains_gate_and_composite_columns(self):
        with tempfile.TemporaryDirectory() as tmp:
            path = Path(tmp) / "compare.csv"
            write_extraction_compare_csv(path, RANKED)
            with path.open(encoding="utf-8") as fp:
                rows = list(csv.reader(fp))
        header = rows[0]
        for col in ("composite_hard", "composite_soft", "gate_passed"):
            self.assertIn(col, header)
        gate_idx = header.index("gate_passed")
        self.assertEqual(rows[1][gate_idx], "True")
        self.assertEqual(rows[2][gate_idx], "False")
        hard_idx = header.index("composite_hard")
        self.assertEqual(rows[1][hard_idx], "0.9500")

    def test_markdown_top_section_shows_gate_and_hard_soft(self):
        with tempfile.TemporaryDirectory() as tmp:
            path = Path(tmp) / "compare.md"
            write_extraction_compare_markdown(
                path, RANKED, weights={"parse_success_rate": 1.0}, top_k=1
            )
            content = path.read_text(encoding="utf-8")
        self.assertIn("gate=", content)
        self.assertIn("hard=", content)
        self.assertIn("soft=", content)


if __name__ == "__main__":
    unittest.main()
