#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
候选 Prompt 固化脚本测试
======================
验证：
1. 候选 Prompt 可被复制到 prompts/final 并激活为当前活动 Prompt。
2. 缺失的可选 Prompt 文件会回退到默认路径。
3. manifest 中不存在的候选会报出清晰错误。
"""

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

from finalize_candidate_prompt import (
    PromptFinalizationError,
    build_parser,
    compute_file_sha256,
    compute_scoring_report_sha256,
    finalize_candidate_prompt,
)


def _write_json(path: Path, payload: object) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(json.dumps(payload, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")


class TestFinalizeCandidatePrompt(unittest.TestCase):
    def _make_workspace(self, root: Path) -> None:
        (root / "prompts" / "candidates").mkdir(parents=True, exist_ok=True)
        (root / "prompts" / "final").mkdir(parents=True, exist_ok=True)
        (root / "prompts" / "community_report_text.txt").write_text("default text prompt\n", encoding="utf-8")
        (root / "prompts" / "extract_claims.txt").write_text("default claim prompt\n", encoding="utf-8")
        (root / "prompts" / "summarize_descriptions.txt").write_text("default summarize prompt\n", encoding="utf-8")
        (root / "prompts" / "community_report_graph.txt").write_text("default graph prompt\n", encoding="utf-8")
        (root / "prompts" / "extract_graph.txt").write_text("default extract prompt\n", encoding="utf-8")
        (root / ".env").write_text(
            "\n".join(
                [
                    "GRAPHRAG_ENTITY_EXTRACTION_PROMPT_FILE=prompts/extract_graph.txt",
                    "GRAPHRAG_SUMMARIZE_DESCRIPTIONS_PROMPT_FILE=prompts/summarize_descriptions.txt",
                    "GRAPHRAG_CLAIM_EXTRACTION_PROMPT_FILE=prompts/extract_claims.txt",
                    "GRAPHRAG_COMMUNITY_REPORT_GRAPH_PROMPT_FILE=prompts/community_report_graph.txt",
                    "GRAPHRAG_COMMUNITY_REPORT_TEXT_PROMPT_FILE=prompts/community_report_text.txt",
                ]
            )
            + "\n",
            encoding="utf-8",
        )

    def _make_manifest(self, root: Path, entries: list[dict[str, object]]) -> None:
        _write_json(
            root / "prompts" / "candidates" / "manifest.json",
            {
                "task": "candidate_prompt_generation",
                "generated_at": "2026-04-20T15:00:00+08:00",
                "candidates": entries,
            },
        )

    def test_finalize_auto_tuned_updates_env_and_activation_record(self) -> None:
        with tempfile.TemporaryDirectory() as tmp_dir:
            root = Path(tmp_dir)
            self._make_workspace(root)

            candidate_dir = root / "prompts" / "candidates" / "auto_tuned"
            candidate_dir.mkdir(parents=True, exist_ok=True)
            (candidate_dir / "prompt.txt").write_text("candidate prompt\n", encoding="utf-8")
            (candidate_dir / "extract_graph.txt").write_text("candidate extract\n", encoding="utf-8")
            (candidate_dir / "summarize_descriptions.txt").write_text("candidate summarize\n", encoding="utf-8")
            (candidate_dir / "community_report_graph.txt").write_text("candidate graph\n", encoding="utf-8")
            (candidate_dir / "README.md").write_text("# auto_tuned\n", encoding="utf-8")

            self._make_manifest(
                root,
                [
                    {
                        "candidate_name": "auto_tuned",
                        "files": {
                            "prompt": str((candidate_dir / "prompt.txt").resolve()),
                            "readme": str((candidate_dir / "README.md").resolve()),
                        },
                    }
                ],
            )

            report = finalize_candidate_prompt(root=root, candidate_name="auto_tuned")

            final_dir = root / "prompts" / "final" / "auto_tuned"
            self.assertTrue((final_dir / "extract_graph.txt").exists())
            self.assertTrue((final_dir / "summarize_descriptions.txt").exists())
            self.assertTrue((final_dir / "community_report_graph.txt").exists())
            self.assertTrue((final_dir / "README.md").exists())
            self.assertEqual(report["candidate_name"], "auto_tuned")

            env_text = (root / ".env").read_text(encoding="utf-8")
            self.assertIn("GRAPHRAG_ACTIVE_PROMPT_CANDIDATE=auto_tuned", env_text)
            self.assertIn(
                "GRAPHRAG_ENTITY_EXTRACTION_PROMPT_FILE=prompts/final/auto_tuned/extract_graph.txt",
                env_text,
            )
            self.assertIn(
                "GRAPHRAG_SUMMARIZE_DESCRIPTIONS_PROMPT_FILE=prompts/final/auto_tuned/summarize_descriptions.txt",
                env_text,
            )
            self.assertIn(
                "GRAPHRAG_COMMUNITY_REPORT_GRAPH_PROMPT_FILE=prompts/final/auto_tuned/community_report_graph.txt",
                env_text,
            )
            self.assertIn(
                "GRAPHRAG_COMMUNITY_REPORT_TEXT_PROMPT_FILE=prompts/community_report_text.txt",
                env_text,
            )
            self.assertIn(
                "GRAPHRAG_CLAIM_EXTRACTION_PROMPT_FILE=prompts/extract_claims.txt",
                env_text,
            )

            active_payload = json.loads((root / "prompts" / "final" / "active_prompt.json").read_text(encoding="utf-8"))
            self.assertEqual(active_payload["candidate_name"], "auto_tuned")
            self.assertEqual(
                active_payload["active_prompt_paths"]["extract_graph.txt"],
                "prompts/final/auto_tuned/extract_graph.txt",
            )
            self.assertEqual(active_payload["activation_policy"], "experimental_unbound")
            self.assertFalse(active_payload["formal_activation_ready"])
            self.assertTrue(active_payload["experimental"])
            self.assertIn("scoring binding", active_payload["experimental_reason"])

    def test_parser_keeps_scoring_binding_requirement_opt_in(self) -> None:
        parser = build_parser()

        default_args = parser.parse_args(["--candidate", "default"])
        required_args = parser.parse_args(["--candidate", "default", "--require-scoring-binding"])

        self.assertFalse(default_args.require_scoring_binding)
        self.assertTrue(required_args.require_scoring_binding)

    def test_finalize_falls_back_to_default_paths_for_missing_optional_files(self) -> None:
        with tempfile.TemporaryDirectory() as tmp_dir:
            root = Path(tmp_dir)
            self._make_workspace(root)

            candidate_dir = root / "prompts" / "candidates" / "default"
            candidate_dir.mkdir(parents=True, exist_ok=True)
            (candidate_dir / "prompt.txt").write_text("default candidate prompt\n", encoding="utf-8")
            (candidate_dir / "README.md").write_text("# default\n", encoding="utf-8")

            self._make_manifest(
                root,
                [
                    {
                        "candidate_name": "default",
                        "files": {
                            "prompt": str((candidate_dir / "prompt.txt").resolve()),
                        },
                    }
                ],
            )

            report = finalize_candidate_prompt(root=root, candidate_name="default")

            final_dir = root / "prompts" / "final" / "default"
            self.assertTrue((final_dir / "prompt.txt").exists())
            self.assertTrue((final_dir / "extract_graph.txt").exists())
            self.assertEqual(report["candidate_name"], "default")

            env_text = (root / ".env").read_text(encoding="utf-8")
            self.assertIn(
                "GRAPHRAG_ENTITY_EXTRACTION_PROMPT_FILE=prompts/final/default/extract_graph.txt",
                env_text,
            )
            self.assertIn(
                "GRAPHRAG_SUMMARIZE_DESCRIPTIONS_PROMPT_FILE=prompts/summarize_descriptions.txt",
                env_text,
            )
            self.assertIn(
                "GRAPHRAG_COMMUNITY_REPORT_GRAPH_PROMPT_FILE=prompts/community_report_graph.txt",
                env_text,
            )

    def test_finalize_raises_clear_error_when_candidate_missing(self) -> None:
        with tempfile.TemporaryDirectory() as tmp_dir:
            root = Path(tmp_dir)
            self._make_workspace(root)
            self._make_manifest(root, [])

            with self.assertRaises(PromptFinalizationError) as ctx:
                finalize_candidate_prompt(root=root, candidate_name="missing")

            self.assertIn("missing", str(ctx.exception))

    def test_finalize_requires_scoring_binding_before_any_mutation(self) -> None:
        with tempfile.TemporaryDirectory() as tmp_dir:
            root = Path(tmp_dir)
            self._make_workspace(root)
            candidate_dir = root / "prompts" / "candidates" / "default"
            candidate_dir.mkdir(parents=True, exist_ok=True)
            (candidate_dir / "prompt.txt").write_text("candidate prompt\n", encoding="utf-8")
            self._make_manifest(
                root,
                [
                    {
                        "candidate_name": "default",
                        "files": {"prompt": str((candidate_dir / "prompt.txt").resolve())},
                    }
                ],
            )
            original_env = (root / ".env").read_text(encoding="utf-8")

            with self.assertRaises(PromptFinalizationError) as ctx:
                finalize_candidate_prompt(
                    root=root,
                    candidate_name="default",
                    require_scoring_binding=True,
                )

            self.assertIn("scoring binding", str(ctx.exception))
            self.assertEqual((root / ".env").read_text(encoding="utf-8"), original_env)
            self.assertFalse((root / "prompts" / "final" / "default").exists())
            self.assertFalse((root / "prompts" / "final" / "active_prompt.json").exists())

    def test_finalize_validates_scoring_artifact_binding_before_mutating_env(self) -> None:
        with tempfile.TemporaryDirectory() as tmp_dir:
            root = Path(tmp_dir)
            self._make_workspace(root)
            candidate_dir = root / "prompts" / "candidates" / "default"
            candidate_dir.mkdir(parents=True, exist_ok=True)
            (candidate_dir / "prompt.txt").write_text("bound candidate prompt\n", encoding="utf-8")
            self._make_manifest(
                root,
                [
                    {
                        "candidate_name": "default",
                        "files": {"prompt": str((candidate_dir / "prompt.txt").resolve())},
                    }
                ],
            )
            manifest_path = root / "prompts" / "candidates" / "manifest.json"
            manifest_hash = compute_file_sha256(manifest_path)
            scoring_report = root / "results" / "reports" / "extraction_scoring" / "runs" / "run-a" / "top_candidates.json"
            binding = {
                "run_id": "run-a",
                "manifest_path": str(manifest_path.resolve()),
                "manifest_sha256": manifest_hash,
                "eval_file_sha256s": [],
            }
            _write_json(
                scoring_report,
                {
                    "task": "extraction_score_top_candidates",
                    "artifact_binding": dict(binding),
                    "top_candidates": [
                        {
                            "candidate": "default",
                            "parse_success_rate": 1.0,
                            "gate_passed": True,
                            "artifact_binding": {**binding, "candidate_id": "default"},
                        }
                    ],
                    "all_candidates_ranked": [],
                },
            )
            scoring_hash = compute_scoring_report_sha256(scoring_report)
            payload = json.loads(scoring_report.read_text(encoding="utf-8"))
            payload["artifact_binding"]["scoring_result_sha256"] = scoring_hash
            payload["top_candidates"][0]["artifact_binding"]["scoring_result_sha256"] = scoring_hash
            _write_json(scoring_report, payload)

            report = finalize_candidate_prompt(
                root=root,
                candidate_name="default",
                scoring_run_id="run-a",
                scoring_report=scoring_report,
                expected_manifest_sha256=manifest_hash,
                expected_scoring_result_sha256=scoring_hash,
                require_scoring_binding=True,
            )

            self.assertEqual(report["scoring_binding"]["run_id"], "run-a")
            self.assertEqual(report["scoring_binding"]["scoring_result_sha256"], scoring_hash)
            self.assertEqual(report["activation_policy"], "formal_scoring_bound")
            self.assertTrue(report["formal_activation_ready"])
            self.assertFalse(report["experimental"])
            self.assertIsNone(report["experimental_reason"])
            self.assertTrue(report["gate_passed"])
            self.assertEqual(report["scoring_run_id"], "run-a")
            self.assertEqual(report["manifest_sha256"], manifest_hash)
            self.assertEqual(report["scoring_result_sha256"], scoring_hash)
            env_text = (root / ".env").read_text(encoding="utf-8")
            self.assertIn("GRAPHRAG_ACTIVE_PROMPT_CANDIDATE=default", env_text)

    def test_finalize_requires_explicit_allow_when_scoring_gate_failed(self) -> None:
        with tempfile.TemporaryDirectory() as tmp_dir:
            root = Path(tmp_dir)
            self._make_workspace(root)
            candidate_dir = root / "prompts" / "candidates" / "default"
            candidate_dir.mkdir(parents=True, exist_ok=True)
            (candidate_dir / "prompt.txt").write_text("bound candidate prompt\n", encoding="utf-8")
            self._make_manifest(
                root,
                [
                    {
                        "candidate_name": "default",
                        "files": {"prompt": str((candidate_dir / "prompt.txt").resolve())},
                    }
                ],
            )
            original_env = (root / ".env").read_text(encoding="utf-8")
            manifest_path = root / "prompts" / "candidates" / "manifest.json"
            manifest_hash = compute_file_sha256(manifest_path)
            scoring_report = root / "results" / "reports" / "extraction_scoring" / "runs" / "run-a" / "top_candidates.json"
            binding = {
                "run_id": "run-a",
                "manifest_path": str(manifest_path.resolve()),
                "manifest_sha256": manifest_hash,
                "eval_file_sha256s": [],
            }
            _write_json(
                scoring_report,
                {
                    "task": "extraction_score_top_candidates",
                    "artifact_binding": dict(binding),
                    "top_candidates": [
                        {
                            "candidate": "default",
                            "parse_success_rate": 1.0,
                            "gate_passed": False,
                            "artifact_binding": {**binding, "candidate_id": "default"},
                        }
                    ],
                    "all_candidates_ranked": [],
                },
            )
            scoring_hash = compute_scoring_report_sha256(scoring_report)
            payload = json.loads(scoring_report.read_text(encoding="utf-8"))
            payload["artifact_binding"]["scoring_result_sha256"] = scoring_hash
            payload["top_candidates"][0]["artifact_binding"]["scoring_result_sha256"] = scoring_hash
            _write_json(scoring_report, payload)

            with self.assertRaises(PromptFinalizationError) as ctx:
                finalize_candidate_prompt(
                    root=root,
                    candidate_name="default",
                    scoring_run_id="run-a",
                    scoring_report=scoring_report,
                    expected_manifest_sha256=manifest_hash,
                    expected_scoring_result_sha256=scoring_hash,
                )

            self.assertIn("gate_passed=False", str(ctx.exception))
            self.assertEqual((root / ".env").read_text(encoding="utf-8"), original_env)

            report = finalize_candidate_prompt(
                root=root,
                candidate_name="default",
                scoring_run_id="run-a",
                scoring_report=scoring_report,
                expected_manifest_sha256=manifest_hash,
                expected_scoring_result_sha256=scoring_hash,
                allow_failed_scoring_gate=True,
            )

            self.assertFalse(report["scoring_binding"]["gate_passed"])
            self.assertTrue(report["scoring_binding"]["allow_failed_scoring_gate"])
            self.assertEqual(report["activation_policy"], "experimental_scoring_gate_failed")
            self.assertFalse(report["formal_activation_ready"])
            self.assertTrue(report["experimental"])
            self.assertIn("gate", report["experimental_reason"])
            env_text = (root / ".env").read_text(encoding="utf-8")
            self.assertIn("GRAPHRAG_ACTIVE_PROMPT_CANDIDATE=default", env_text)

    def test_finalize_rejects_stale_manifest_binding_without_env_change(self) -> None:
        with tempfile.TemporaryDirectory() as tmp_dir:
            root = Path(tmp_dir)
            self._make_workspace(root)
            candidate_dir = root / "prompts" / "candidates" / "default"
            candidate_dir.mkdir(parents=True, exist_ok=True)
            (candidate_dir / "prompt.txt").write_text("bound candidate prompt\n", encoding="utf-8")
            self._make_manifest(
                root,
                [
                    {
                        "candidate_name": "default",
                        "files": {"prompt": str((candidate_dir / "prompt.txt").resolve())},
                    }
                ],
            )
            original_env = (root / ".env").read_text(encoding="utf-8")
            manifest_path = root / "prompts" / "candidates" / "manifest.json"
            scoring_report = root / "results" / "reports" / "extraction_scoring" / "runs" / "run-a" / "top_candidates.json"
            _write_json(
                scoring_report,
                {
                    "artifact_binding": {
                        "run_id": "run-a",
                        "manifest_path": str(manifest_path.resolve()),
                        "manifest_sha256": "0" * 64,
                        "scoring_result_sha256": "1" * 64,
                        "eval_file_sha256s": [],
                    },
                    "top_candidates": [
                        {
                            "candidate": "default",
                            "parse_success_rate": 1.0,
                            "gate_passed": True,
                            "artifact_binding": {
                                "run_id": "run-a",
                                "candidate_id": "default",
                                "manifest_sha256": "0" * 64,
                                "scoring_result_sha256": "1" * 64,
                            },
                        }
                    ],
                },
            )

            with self.assertRaises(PromptFinalizationError) as ctx:
                finalize_candidate_prompt(
                    root=root,
                    candidate_name="default",
                    scoring_run_id="run-a",
                    scoring_report=scoring_report,
                    expected_manifest_sha256="0" * 64,
                    expected_scoring_result_sha256="1" * 64,
                )

            self.assertIn("manifest hash", str(ctx.exception))
            self.assertEqual((root / ".env").read_text(encoding="utf-8"), original_env)
            self.assertFalse((root / "prompts" / "final" / "default" / "prompt.txt").exists())


if __name__ == "__main__":
    unittest.main()
