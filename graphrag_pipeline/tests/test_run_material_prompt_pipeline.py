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

from prompt_tuning.run_material_prompt_pipeline import run_material_prompt_pipeline


class TestRunMaterialPromptPipeline(unittest.TestCase):
    def test_dry_run_builds_smoke_pipeline_commands_without_executing(self):
        with tempfile.TemporaryDirectory() as tmp:
            root = Path(tmp)

            summary = run_material_prompt_pipeline(
                root=root,
                course_id="crs-20260506-r4slkr",
                material_id=7,
                json_file="section_docs.json",
                run_id="material_7_full",
                dry_run=True,
                mode="smoke",
            )

            self.assertEqual(summary["status"], "dry_run")
            self.assertEqual(summary["step_count"], 7)
            names = [step["name"] for step in summary["steps"]]
            self.assertEqual(
                names,
                [
                    "fetch_input",
                    "build_samples",
                    "build_audit",
                    "prompt_tune_dry_run",
                    "generate_candidates",
                    "extract_smoke",
                    "score_smoke",
                ],
            )
            joined_commands = [" ".join(step["command"]) for step in summary["steps"]]
            self.assertIn("--material-id 7", joined_commands[0])
            self.assertIn("--json-file section_docs.json", joined_commands[0])
            self.assertIn("--run-id material_7_full_smoke", joined_commands[-1])
            self.assertIn("--run-id material_7_full_smoke", joined_commands[-2])

    def test_full_pipeline_extracts_full_run_from_audit_set(self):
        with tempfile.TemporaryDirectory() as tmp:
            root = Path(tmp)

            summary = run_material_prompt_pipeline(
                root=root,
                course_id="crs-20260506-r4slkr",
                material_id=7,
                json_file="section_docs.json",
                run_id="material_7_full",
                dry_run=True,
                mode="full",
                prompt_tune_mode="skip",
            )

            steps = {step["name"]: " ".join(step["command"]) for step in summary["steps"]}
            self.assertIn("--samples data/prompt_tuning_samples/material_7_samples.json", steps["extract_smoke"])
            self.assertIn("--samples data/eval/material_7_audit_extraction_set.json", steps["extract_full"])

    def test_finalize_best_stops_when_top_candidate_fails_gate(self):
        with tempfile.TemporaryDirectory() as tmp:
            root = Path(tmp)
            smoke_dir = (
                root
                / "results"
                / "reports"
                / "extraction_scoring"
                / "runs"
                / "material_7_full_smoke"
            )
            smoke_dir.mkdir(parents=True)
            (smoke_dir / "top_candidates.json").write_text(
                json.dumps(
                    {
                        "top_candidates": [
                            {
                                "candidate": "default",
                                "parse_success_rate": 1.0,
                                "gate_passed": True,
                            }
                        ]
                    },
                    ensure_ascii=False,
                ),
                encoding="utf-8",
            )
            run_dir = (
                root
                / "results"
                / "reports"
                / "extraction_scoring"
                / "runs"
                / "material_7_full"
            )
            run_dir.mkdir(parents=True)
            (run_dir / "top_candidates.json").write_text(
                json.dumps(
                    {
                        "top_candidates": [
                            {
                                "candidate": "default",
                                "parse_success_rate": 0.0,
                                "gate_passed": False,
                            }
                        ]
                    },
                    ensure_ascii=False,
                ),
                encoding="utf-8",
            )
            executed: list[list[str]] = []

            def fake_runner(command, cwd):
                executed.append(list(command))
                return subprocess.CompletedProcess(command, 0, stdout="ok", stderr="")

            summary = run_material_prompt_pipeline(
                root=root,
                course_id="crs-20260506-r4slkr",
                material_id=7,
                json_file="section_docs.json",
                run_id="material_7_full",
                mode="full",
                prompt_tune_mode="skip",
                finalize_best=True,
                runner=fake_runner,
            )

            self.assertEqual(summary["status"], "failed")
            self.assertEqual(summary["failed_step"], "select_best_candidate_for_finalization")
            self.assertIn("未达固化门槛", summary["error"])
            self.assertFalse(any("finalize_candidate_prompt.py" in " ".join(command) for command in executed))

    def test_full_mode_stops_before_full_extraction_when_smoke_gate_fails(self):
        with tempfile.TemporaryDirectory() as tmp:
            root = Path(tmp)
            smoke_dir = (
                root
                / "results"
                / "reports"
                / "extraction_scoring"
                / "runs"
                / "material_7_full_smoke"
            )
            smoke_dir.mkdir(parents=True)
            (smoke_dir / "top_candidates.json").write_text(
                json.dumps(
                    {
                        "top_candidates": [
                            {
                                "candidate": "default",
                                "parse_success_rate": 0.0,
                                "gate_passed": False,
                            }
                        ]
                    },
                    ensure_ascii=False,
                ),
                encoding="utf-8",
            )
            executed: list[str] = []

            def fake_runner(command, cwd):
                executed.append(" ".join(command))
                return subprocess.CompletedProcess(command, 0, stdout="ok", stderr="")

            summary = run_material_prompt_pipeline(
                root=root,
                course_id="crs-20260506-r4slkr",
                material_id=7,
                json_file="section_docs.json",
                run_id="material_7_full",
                mode="full",
                prompt_tune_mode="skip",
                runner=fake_runner,
            )

            self.assertEqual(summary["status"], "failed")
            self.assertEqual(summary["failed_step"], "smoke_gate")
            self.assertFalse(any("--run-id material_7_full " in command for command in executed))

    def test_full_mode_allows_full_extraction_when_smoke_parse_passes_without_gate(self):
        with tempfile.TemporaryDirectory() as tmp:
            root = Path(tmp)
            smoke_dir = (
                root
                / "results"
                / "reports"
                / "extraction_scoring"
                / "runs"
                / "material_7_full_smoke"
            )
            smoke_dir.mkdir(parents=True)
            (smoke_dir / "top_candidates.json").write_text(
                json.dumps(
                    {
                        "top_candidates": [
                            {
                                "candidate": "schema_aware",
                                "parse_success_rate": 1.0,
                                "gate_passed": False,
                            }
                        ]
                    },
                    ensure_ascii=False,
                ),
                encoding="utf-8",
            )
            executed: list[str] = []

            def fake_runner(command, cwd):
                executed.append(" ".join(command))
                return subprocess.CompletedProcess(command, 0, stdout="ok", stderr="")

            summary = run_material_prompt_pipeline(
                root=root,
                course_id="crs-20260506-r4slkr",
                material_id=7,
                json_file="section_docs.json",
                run_id="material_7_full",
                mode="full",
                prompt_tune_mode="skip",
                runner=fake_runner,
            )

            self.assertEqual(summary["status"], "success")
            self.assertTrue(any("--samples data/eval/material_7_audit_extraction_set.json" in command for command in executed))

    def test_finalize_best_passes_scoring_binding_and_runs_qa_after_index(self):
        with tempfile.TemporaryDirectory() as tmp:
            root = Path(tmp)
            smoke_dir = (
                root
                / "results"
                / "reports"
                / "extraction_scoring"
                / "runs"
                / "material_7_full_smoke"
            )
            smoke_dir.mkdir(parents=True)
            (smoke_dir / "top_candidates.json").write_text(
                json.dumps(
                    {
                        "inputs": {"gold_seed_coverage_passed": True},
                        "top_candidates": [
                            {
                                "candidate": "default",
                                "parse_success_rate": 1.0,
                                "gate_passed": True,
                            }
                        ],
                    },
                    ensure_ascii=False,
                ),
                encoding="utf-8",
            )
            run_dir = (
                root
                / "results"
                / "reports"
                / "extraction_scoring"
                / "runs"
                / "material_7_full"
            )
            run_dir.mkdir(parents=True)
            binding = {
                "run_id": "material_7_full",
                "manifest_sha256": "a" * 64,
                "scoring_result_sha256": "b" * 64,
                "eval_file_sha256s": [],
            }
            (run_dir / "top_candidates.json").write_text(
                json.dumps(
                    {
                        "inputs": {"gold_seed_coverage_passed": True},
                        "artifact_binding": binding,
                        "top_candidates": [
                            {
                                "candidate": "default",
                                "parse_success_rate": 1.0,
                                "gate_passed": True,
                                "artifact_binding": {**binding, "candidate_id": "default"},
                            }
                        ],
                    },
                    ensure_ascii=False,
                ),
                encoding="utf-8",
            )
            executed: list[list[str]] = []

            def fake_runner(command, cwd):
                executed.append(list(command))
                return subprocess.CompletedProcess(command, 0, stdout="ok", stderr="")

            summary = run_material_prompt_pipeline(
                root=root,
                course_id="crs-20260506-r4slkr",
                material_id=7,
                json_file="section_docs.json",
                run_id="material_7_full",
                mode="full",
                prompt_tune_mode="skip",
                finalize_best=True,
                index_after_finalize=True,
                runner=fake_runner,
            )

            self.assertEqual(summary["status"], "success")
            joined = [" ".join(command) for command in executed]
            finalize_command = next(command for command in joined if "finalize_candidate_prompt.py" in command)
            self.assertIn("--scoring-run-id material_7_full", finalize_command)
            self.assertIn("--expected-manifest-sha256 " + "a" * 64, finalize_command)
            self.assertIn("--expected-scoring-result-sha256 " + "b" * 64, finalize_command)
            self.assertLess(
                next(i for i, command in enumerate(joined) if command.startswith("graphrag index")),
                next(i for i, command in enumerate(joined) if "run_material_qa_smoke.py" in command),
            )

    def test_real_prompt_tune_stale_primary_prompt_fails_before_candidate_generation(self):
        with tempfile.TemporaryDirectory() as tmp:
            root = Path(tmp)
            prompt_file = root / "prompts" / "candidates" / "auto_tuned" / "prompt.txt"
            prompt_file.parent.mkdir(parents=True, exist_ok=True)
            prompt_file.write_text("old prompt\n", encoding="utf-8")
            manifest_file = root / "prompts" / "candidates" / "manifest.json"
            manifest_file.write_text(
                json.dumps(
                    {
                        "candidates": [
                            {
                                "candidate_name": "auto_tuned",
                                "source_type": "graphrag_prompt_tune",
                                "files": {"prompt": str(prompt_file)},
                            }
                        ]
                    },
                    ensure_ascii=False,
                ),
                encoding="utf-8",
            )
            executed: list[str] = []

            def fake_runner(command, cwd):
                command_text = " ".join(command)
                executed.append(command_text)
                if "run_graphrag_prompt_tune.py" in command_text:
                    report_path = root / "results" / "reports" / "material_7_prompt_tune_report.json"
                    report_path.parent.mkdir(parents=True, exist_ok=True)
                    report_path.write_text(
                        json.dumps(
                            {
                                "status": "success",
                                "start_time": "2099-01-01T00:00:00+00:00",
                                "primary_prompt_path": str(prompt_file),
                                "resolved_paths": {"manifest_file": str(manifest_file)},
                            },
                            ensure_ascii=False,
                        ),
                        encoding="utf-8",
                    )
                return subprocess.CompletedProcess(command, 0, stdout="ok", stderr="")

            summary = run_material_prompt_pipeline(
                root=root,
                course_id="crs-20260506-r4slkr",
                material_id=7,
                json_file="section_docs.json",
                run_id="material_7_full",
                mode="smoke",
                prompt_tune_mode="real",
                runner=fake_runner,
            )

            self.assertEqual(summary["status"], "failed")
            self.assertEqual(summary["failed_step"], "prompt_tune_real_validation")
            self.assertFalse(any("generate_candidate_prompts.py" in command for command in executed))


if __name__ == "__main__":
    unittest.main()
