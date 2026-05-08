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
                return subprocess.CompletedProcess(command, 0, stdout="未命中", stderr="")

            summary = run_material_qa_smoke(
                root=root,
                qa_file=qa_file,
                output_file=root / "results" / "qa_eval" / "material_7_smoke.json",
                method="local",
                runner=fake_runner,
            )

            self.assertEqual(summary["status"], "success")
            self.assertEqual(summary["passed"], 1)
            self.assertEqual(summary["failed"], 1)
            report = json.loads(Path(summary["output_file"]).read_text(encoding="utf-8"))
            self.assertTrue(report["results"][0]["passed"])
            self.assertFalse(report["results"][1]["passed"])
            self.assertEqual(report["results"][0]["matched_keywords"], ["进程", "资源分配"])


if __name__ == "__main__":
    unittest.main()
