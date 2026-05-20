#!/usr/bin/env python3
# -*- coding: utf-8 -*-

from __future__ import annotations

import json
import logging
import tempfile
import unittest
from pathlib import Path
from types import SimpleNamespace

_PROJECT_ROOT = Path(__file__).resolve().parent.parent
import sys

scripts_dir = _PROJECT_ROOT / "scripts"
if str(scripts_dir) not in sys.path:
    sys.path.insert(0, str(scripts_dir))

import run_graphrag_history_poc_eval as history_eval  # noqa: E402


class TestRunGraphRagHistoryPocEval(unittest.TestCase):
    def test_dry_run_generates_versioned_report_without_live_calls(self):
        with tempfile.TemporaryDirectory() as temp_dir:
            eval_path = Path(temp_dir) / "eval.jsonl"
            eval_path.write_text(
                "\n".join(
                    [
                        json.dumps(
                            {
                                "id": "Q001",
                                "question": "它和资源分配图有什么关系？",
                                "gold_answer_summary": "死锁可通过资源分配图判断。",
                                "gold_entities": ["死锁", "资源分配图"],
                            },
                            ensure_ascii=False,
                        ),
                        json.dumps(
                            {
                                "id": "Q002",
                                "question": "这个算法有什么作用？",
                                "gold_answer_summary": "调度算法用于选择进程。",
                                "gold_entities": ["调度算法"],
                            },
                            ensure_ascii=False,
                        ),
                    ]
                ),
                encoding="utf-8",
            )
            output_root = Path(temp_dir) / "reports"

            report_dir = history_eval.main(
                [
                    "--dry-run",
                    "--eval-jsonl",
                    str(eval_path),
                    "--output-root",
                    str(output_root),
                    "--run-label",
                    "unit",
                    "--limit",
                    "2",
                ]
            )

            report_path = report_dir / "report.json"
            summary_path = report_dir / "summary.md"
            self.assertTrue(report_path.exists())
            self.assertTrue(summary_path.exists())
            payload = json.loads(report_path.read_text(encoding="utf-8"))
            self.assertFalse(payload["live"])
            self.assertEqual(payload["caseCount"], 2)
            self.assertEqual(payload["cases"][0]["localHistory"]["status"], "dry_run_skipped")
            self.assertEqual(payload["cases"][0]["ckqaFormalHybrid"]["status"], "not_executed")
            self.assertIn("qualitySignals", payload["cases"][0])
            summary_text = summary_path.read_text(encoding="utf-8")
            self.assertIn("GraphRAG History PoC", summary_text)
            self.assertIn("hybrid ms", summary_text)
            self.assertIn("token warnings", summary_text)

    def test_live_report_case_adds_status_for_summary(self):
        class FakeAdapter:
            config = SimpleNamespace(max_turns=3)

            def __init__(self):
                self.kwargs = None

            def query(self, **_kwargs):
                self.kwargs = _kwargs
                return SimpleNamespace(
                    to_dict=lambda: {
                        "enabled": True,
                        "supported": True,
                        "answer": "回答",
                        "errorMessage": None,
                    }
                )

        adapter = FakeAdapter()
        item = history_eval.build_report_case(
            {
                "id": "Q001",
                "question": "什么是死锁？",
                "gold_entities": ["死锁", "资源分配图"],
            },
            live=True,
            adapter=adapter,
            data_dir_uri="user_12/kb_5/build_20/index/output",
        )

        self.assertEqual(item["localHistory"]["status"], "success")
        self.assertEqual(item["question"], "它和资源分配图有什么关系？")
        self.assertEqual(item["ckqaFormalHybrid"]["status"], "not_executed")
        self.assertEqual(item["qualitySignals"]["localHistory"]["mustCiteHit"], 1.0)
        self.assertNotIn("max_turns", adapter.kwargs)

    def test_live_report_case_records_token_limit_warnings(self):
        class FakeAdapter:
            def query(self, **_kwargs):
                logging.getLogger("graphrag.query.structured_search.local_search.mixed_context").warning(
                    "Reached token limit - reverting to previous context state"
                )
                return SimpleNamespace(
                    to_dict=lambda: {
                        "enabled": True,
                        "supported": True,
                        "answer": "回答",
                        "diagnostics": {},
                        "errorMessage": None,
                    }
                )

        item = history_eval.build_report_case(
            {
                "id": "Q001",
                "question": "什么是死锁？",
                "gold_entities": ["死锁", "资源分配图"],
            },
            live=True,
            adapter=FakeAdapter(),
            data_dir_uri="user_12/kb_5/build_20/index/output",
        )

        diagnostics = item["localHistory"]["diagnostics"]
        self.assertEqual(diagnostics["tokenLimitWarningCount"], 1)
        self.assertIn("Reached token limit", diagnostics["warningMessages"][0])

    def test_quality_signals_score_answer_terms_and_source_recall(self):
        signals = history_eval.score_answer_quality(
            {
                "must_cite_terms": ["死锁", "资源分配图"],
                "negative_terms": ["银行家算法"],
                "gold_text_unit_ids": ["abcde1234567", "deadbeef0000"],
            },
            "死锁可以通过资源分配图分析。",
            [{"chunk_id": "abcde1234567xxxx"}],
        )

        self.assertEqual(signals["mustCiteHit"], 1.0)
        self.assertEqual(signals["negativeHit"], 0.0)
        self.assertEqual(signals["sourceRecallAt3"], 0.5)

    def test_live_report_case_can_run_hybrid_baseline_when_requested(self):
        class FakeAdapter:
            def query(self, **_kwargs):
                return SimpleNamespace(
                    to_dict=lambda: {
                        "enabled": True,
                        "supported": True,
                        "answer": "LocalSearch 回答包含死锁",
                        "sources": [],
                        "errorMessage": None,
                    }
                )

        def fake_hybrid(**_kwargs):
            return {
                "status": "success",
                "answer": "Hybrid 回答包含死锁和资源分配图",
                "sources": [{"chunk_id": "abcde1234567"}],
                "elapsedMs": 1234,
            }

        item = history_eval.build_report_case(
            {
                "id": "Q001",
                "question": "什么是死锁？",
                "gold_entities": ["死锁", "资源分配图"],
                "must_cite_terms": ["死锁", "资源分配图"],
                "gold_text_unit_ids": ["abcde1234567"],
            },
            live=True,
            adapter=FakeAdapter(),
            data_dir_uri="user_12/kb_5/build_20/index/output",
            run_hybrid_baseline=True,
            hybrid_baseline_runner=fake_hybrid,
        )

        self.assertEqual(item["ckqaFormalHybrid"]["status"], "success")
        self.assertEqual(item["ckqaFormalHybrid"]["elapsedMs"], 1234)
        self.assertEqual(item["qualitySignals"]["ckqaFormalHybrid"]["sourceRecallAt3"], 1.0)


if __name__ == "__main__":
    unittest.main()
