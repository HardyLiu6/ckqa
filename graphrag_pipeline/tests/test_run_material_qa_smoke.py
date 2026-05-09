#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""material QA smoke 执行脚本测试。"""

from __future__ import annotations

import json
import subprocess
import sys
import tempfile
import unittest
from pathlib import Path


_PROJECT_ROOT = Path(__file__).resolve().parent.parent
_SCRIPTS_DIR = _PROJECT_ROOT / "scripts"
if str(_SCRIPTS_DIR) not in sys.path:
    sys.path.insert(0, str(_SCRIPTS_DIR))

from prompt_tuning.run_material_qa_smoke import run_material_qa_smoke


class TestRunMaterialQaSmoke(unittest.TestCase):
    def test_qa_smoke_records_keyword_hits_and_failures(self):
        with tempfile.TemporaryDirectory() as tmp:
            root = Path(tmp)
            qa_file = root / "data" / "eval" / "material_7_qa_smoke.json"
            qa_file.parent.mkdir(parents=True)
            qa_file.write_text(
                json.dumps(
                    {
                        "questions": [
                            {
                                "id": "q1",
                                "question": "进程是什么？",
                                "expected_keywords": ["进程", "资源分配"],
                                "evidence_heading": "第二章",
                            },
                            {
                                "id": "q2",
                                "question": "虚拟存储器特征？",
                                "expected_keywords": ["离散性"],
                                "expected_keyword_groups": [
                                    {
                                        "label": "离散性",
                                        "keywords": ["离散性", "离散分配"],
                                    }
                                ],
                                "evidence_heading": "第五章",
                            },
                        ]
                    },
                    ensure_ascii=False,
                ),
                encoding="utf-8",
            )

            def fake_runner(command, cwd):
                question = command[-1]
                if "进程" in question:
                    return subprocess.CompletedProcess(command, 0, stdout="进程是资源分配的基本单位", stderr="")
                return subprocess.CompletedProcess(command, 0, stdout="采用离散分配方式", stderr="")

            summary = run_material_qa_smoke(
                root=root,
                qa_file=qa_file,
                output_file=root / "results" / "qa_eval" / "material_7_smoke.json",
                method="local",
                query_timeout_seconds=30,
                runner=fake_runner,
            )

            self.assertEqual(summary["status"], "success")
            self.assertEqual(summary["passed"], 2)
            self.assertEqual(summary["failed"], 0)
            report = json.loads(Path(summary["output_file"]).read_text(encoding="utf-8"))
            self.assertTrue(report["results"][0]["passed"])
            self.assertTrue(report["results"][1]["passed"])
            self.assertEqual(report["results"][0]["matched_keywords"], ["进程", "资源分配"])
            self.assertEqual(report["results"][1]["matched_keywords"], [])
            self.assertEqual(report["results"][1]["matched_keyword_groups"], ["离散性"])
            self.assertEqual(report["query_timeout_seconds"], 30)

    def test_qa_smoke_report_binds_index_context(self):
        with tempfile.TemporaryDirectory() as tmp:
            root = Path(tmp)
            qa_file = root / "data" / "eval" / "material_7_qa_smoke.json"
            qa_file.parent.mkdir(parents=True)
            qa_file.write_text(
                json.dumps(
                    {
                        "questions": [
                            {
                                "id": "q1",
                                "question": "索引绑定验证？",
                                "expected_keywords": ["绑定"],
                            }
                        ]
                    },
                    ensure_ascii=False,
                ),
                encoding="utf-8",
            )
            active_prompt = root / "prompts" / "final" / "active_prompt.json"
            active_prompt.parent.mkdir(parents=True)
            active_prompt.write_text(
                json.dumps({"candidate": "schema_aware", "run_id": "material_7_full"}, ensure_ascii=False),
                encoding="utf-8",
            )
            output_dir = root / "output"
            output_dir.mkdir()
            (output_dir / "stats.json").write_text(
                json.dumps(
                    {
                        "num_documents": 4,
                        "update_documents": 0,
                        "workflows": {"create_community_reports": {"overall": 3.0}},
                    },
                    ensure_ascii=False,
                ),
                encoding="utf-8",
            )
            (output_dir / "entities.parquet").write_text("", encoding="utf-8")
            index_report_file = root / "results" / "reports" / "index_runs" / "idx-001.json"

            def fake_runner(command, cwd):
                return subprocess.CompletedProcess(command, 0, stdout="绑定完成", stderr="")

            summary = run_material_qa_smoke(
                root=root,
                qa_file=qa_file,
                output_file=root / "results" / "qa_eval" / "material_7_smoke.json",
                method="local",
                index_run_id="idx-001",
                index_report_file=index_report_file,
                runner=fake_runner,
            )

            self.assertEqual(summary["index_run_id"], "idx-001")
            self.assertEqual(summary["index_report_file"], str(index_report_file.resolve()))
            report = json.loads(Path(summary["output_file"]).read_text(encoding="utf-8"))
            self.assertEqual(report["index_run_id"], "idx-001")
            self.assertEqual(report["index_report_file"], str(index_report_file.resolve()))
            self.assertTrue(report["active_prompt_snapshot"]["exists"])
            self.assertEqual(report["active_prompt_snapshot"]["payload"]["candidate"], "schema_aware")
            self.assertEqual(report["index_stats"]["summary"]["num_documents"], 4)
            self.assertEqual(report["index_stats"]["summary"]["workflow_count"], 1)
            self.assertTrue(report["output_artifacts"]["entities.parquet"]["exists"])
            self.assertFalse(report["output_artifacts"]["relationships.parquet"]["exists"])

    def test_cli_accepts_index_binding_arguments(self):
        from prompt_tuning.run_material_qa_smoke import build_parser

        args = build_parser().parse_args(
            [
                "--root",
                ".",
                "--qa-file",
                "data/eval/material_7_qa_smoke.json",
                "--output-file",
                "results/qa_eval/material_7_smoke.json",
                "--index-run-id",
                "idx-001",
                "--index-report-file",
                "results/reports/index_runs/idx-001.json",
                "--query-timeout",
                "45",
            ]
        )

        self.assertEqual(args.index_run_id, "idx-001")
        self.assertEqual(args.index_report_file, "results/reports/index_runs/idx-001.json")
        self.assertEqual(args.query_timeout, 45)


if __name__ == "__main__":
    unittest.main()
