#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
GraphRAG prompt-tune 封装测试
===========================
验证：
1. 命令探测会按优先级选择官方入口。
2. dry_run / discover_only 能写出可追踪报告。
3. 成功执行后会整理输出、生成 README，并增量更新 manifest。
4. 在命令不可用时会报出清晰错误，而不是静默失败。
"""

from __future__ import annotations

import json
import sys
import tempfile
import unittest
from pathlib import Path
from unittest.mock import patch


_PROJECT_ROOT = Path(__file__).resolve().parent.parent
_SCRIPTS_DIR = _PROJECT_ROOT / "scripts"
if str(_SCRIPTS_DIR) not in sys.path:
    sys.path.insert(0, str(_SCRIPTS_DIR))

import run_graphrag_prompt_tune as prompt_tune_module
from run_graphrag_prompt_tune import (
    CommandExecutionResult,
    PromptTuneError,
    find_graphrag_invocation,
    run_prompt_tune,
)


def _write_json(path: Path, payload: object) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(json.dumps(payload, ensure_ascii=False, indent=2), encoding="utf-8")


class TestRunGraphRagPromptTune(unittest.TestCase):
    def _make_workspace(self, root: Path) -> None:
        (root / "prompts").mkdir(parents=True, exist_ok=True)
        (root / "input").mkdir(parents=True, exist_ok=True)
        (root / "results" / "reports").mkdir(parents=True, exist_ok=True)
        (root / "settings.yaml").write_text(
            "input:\n  type: json\nchunking:\n  size: 1000\n",
            encoding="utf-8",
        )
        (root / ".env").write_text("GRAPHRAG_API_BASE=https://example.invalid\n", encoding="utf-8")
        (root / "CLAUDE.md").write_text(
            "## Common Commands\n\n```bash\nconda activate graphrag-oneapi\npip install -e \".[all]\"\n```\n",
            encoding="utf-8",
        )
        (root / "prompts" / "extract_graph.txt").write_text("-Goal-\nDefault prompt\n", encoding="utf-8")
        (root / "input" / "section_docs.json").write_text(
            '[{"id":"doc-1","title":"示例","text":"课程文本"}]\n',
            encoding="utf-8",
        )

    def _make_manifest(self, path: Path) -> None:
        payload = {
            "task": "candidate_prompt_generation",
            "generated_at": "2026-04-16T16:44:16+08:00",
            "candidates": [
                {
                    "candidate_name": "default",
                    "source_type": "default_adapted",
                    "files": {"prompt": "/tmp/default/prompt.txt"},
                    "notes": ["baseline"],
                },
                {
                    "candidate_name": "auto_tuned",
                    "source_type": "fallback_default_copy",
                    "files": {"prompt": "/tmp/old-auto/prompt.txt"},
                    "notes": ["旧占位候选"],
                },
            ],
        }
        _write_json(path, payload)

    def test_find_graphrag_invocation_prefers_python_module(self):
        calls: list[list[str]] = []

        def fake_runner(command: list[str], cwd: Path, env: dict[str, str]) -> CommandExecutionResult:
            calls.append(command)
            if command[:3] == [sys.executable, "-m", "graphrag"] and command[-1] == "--help":
                return CommandExecutionResult(
                    command=command,
                    cwd=str(cwd),
                    exit_code=0,
                    stdout="Usage: prompt-tune [OPTIONS]",
                    stderr="",
                )
            return CommandExecutionResult(
                command=command,
                cwd=str(cwd),
                exit_code=127,
                stdout="",
                stderr="not found",
            )

        invocation, attempts = find_graphrag_invocation(
            root=Path("/tmp/project"),
            runner=fake_runner,
        )

        self.assertEqual(invocation.name, "python_module")
        self.assertEqual(len(attempts), 1)
        self.assertEqual(calls[0][:4], [sys.executable, "-m", "graphrag", "prompt-tune"])

    def test_find_graphrag_invocation_can_fallback_to_claude_conda_env(self):
        with tempfile.TemporaryDirectory() as tmp_dir:
            root = Path(tmp_dir) / "graph"
            self._make_workspace(root)

            calls: list[list[str]] = []
            primary_python = "/home/sunlight/miniconda3/bin/python"
            hinted_python = "/home/sunlight/miniconda3/envs/graphrag-oneapi/bin/python"

            def fake_runner(command: list[str], cwd: Path, env: dict[str, str]) -> CommandExecutionResult:
                calls.append(command)
                if command[:4] == [primary_python, "-m", "graphrag", "prompt-tune"]:
                    return CommandExecutionResult(
                        command=command,
                        cwd=str(cwd),
                        exit_code=1,
                        stdout="",
                        stderr="No module named graphrag",
                    )
                if command[:4] == [hinted_python, "-m", "graphrag", "prompt-tune"]:
                    return CommandExecutionResult(
                        command=command,
                        cwd=str(cwd),
                        exit_code=0,
                        stdout="Usage: prompt-tune [OPTIONS]",
                        stderr="",
                    )
                return CommandExecutionResult(
                    command=command,
                    cwd=str(cwd),
                    exit_code=127,
                    stdout="",
                    stderr="not found",
                )

            with patch.object(prompt_tune_module.sys, "executable", primary_python):
                invocation, attempts = find_graphrag_invocation(
                    root=root,
                    runner=fake_runner,
                )

            self.assertEqual(invocation.name, "python_module_conda_env")
            self.assertEqual(invocation.command_prefix[0], hinted_python)
            self.assertGreaterEqual(len(attempts), 2)
            self.assertEqual(calls[1][:4], [hinted_python, "-m", "graphrag", "prompt-tune"])

    def test_run_prompt_tune_dry_run_supports_external_config_and_writes_report(self):
        with tempfile.TemporaryDirectory() as tmp_dir:
            workspace = Path(tmp_dir)
            root = workspace / "graph"
            self._make_workspace(root)

            external_config = workspace / "configs" / "alt-settings.yaml"
            external_config.parent.mkdir(parents=True, exist_ok=True)
            external_config.write_text("input:\n  type: json\n", encoding="utf-8")

            output_dir = root / "prompts" / "candidates" / "auto_tuned"
            log_file = root / "results" / "reports" / "prompt_tune_run.log"
            report_file = root / "results" / "reports" / "prompt_tune_report.json"
            manifest_file = root / "prompts" / "candidates" / "manifest.json"
            self._make_manifest(manifest_file)

            def fake_runner(command: list[str], cwd: Path, env: dict[str, str]) -> CommandExecutionResult:
                if command[:3] == [sys.executable, "-m", "graphrag"] and command[-1] == "--help":
                    return CommandExecutionResult(
                        command=command,
                        cwd=str(cwd),
                        exit_code=0,
                        stdout="Usage: prompt-tune [OPTIONS]",
                        stderr="",
                    )
                return CommandExecutionResult(
                    command=command,
                    cwd=str(cwd),
                    exit_code=0,
                    stdout="unexpected",
                    stderr="",
                )

            result = run_prompt_tune(
                root=root,
                config=external_config,
                output_dir=output_dir,
                log_file=log_file,
                report_file=report_file,
                manifest_file=manifest_file,
                domain="course knowledge graph",
                language="Chinese",
                chunk_size=256,
                discover_only=False,
                dry_run=True,
                no_entity_types=True,
                overwrite=True,
                extra_args=["--limit=8"],
                runner=fake_runner,
            )

            self.assertEqual(result["status"], "dry_run")
            self.assertTrue(report_file.exists())
            self.assertTrue(log_file.exists())

            report = json.loads(report_file.read_text(encoding="utf-8"))
            self.assertEqual(report["status"], "dry_run")
            self.assertEqual(report["chosen_invocation"]["name"], "python_module")
            self.assertEqual(report["parameters"]["chunk_size"], 256)
            self.assertEqual(report["parameters"]["no_entity_types"], True)
            self.assertIn("--no-discover-entity-types", report["final_command"])
            self.assertIn("--limit=8", report["final_command"])
            self.assertNotEqual(report["resolved_paths"]["invocation_root"], str(root.resolve()))

            manifest = json.loads(manifest_file.read_text(encoding="utf-8"))
            auto_tuned = next(item for item in manifest["candidates"] if item["candidate_name"] == "auto_tuned")
            self.assertEqual(auto_tuned["source_type"], "fallback_default_copy")

    def test_run_prompt_tune_success_collects_files_and_updates_manifest(self):
        with tempfile.TemporaryDirectory() as tmp_dir:
            workspace = Path(tmp_dir)
            root = workspace / "graph"
            self._make_workspace(root)

            output_dir = root / "prompts" / "candidates" / "auto_tuned"
            log_file = root / "results" / "reports" / "prompt_tune_run.log"
            report_file = root / "results" / "reports" / "prompt_tune_report.json"
            manifest_file = root / "prompts" / "candidates" / "manifest.json"
            self._make_manifest(manifest_file)

            def fake_runner(command: list[str], cwd: Path, env: dict[str, str]) -> CommandExecutionResult:
                if command[-1] == "--help":
                    return CommandExecutionResult(
                        command=command,
                        cwd=str(cwd),
                        exit_code=0,
                        stdout="Usage: prompt-tune [OPTIONS]",
                        stderr="",
                    )

                self.assertIn("--output", command)
                root_path = Path(command[command.index("--root") + 1])
                output_arg = command[command.index("--output") + 1]
                raw_output_dir = Path(output_arg)
                if not raw_output_dir.is_absolute():
                    raw_output_dir = root_path / raw_output_dir
                raw_output_dir.mkdir(parents=True, exist_ok=True)
                (raw_output_dir / "extract_graph.txt").write_text("-Goal-\nAuto tuned extract graph\n", encoding="utf-8")
                (raw_output_dir / "summarize_descriptions.txt").write_text("Summaries\n", encoding="utf-8")
                return CommandExecutionResult(
                    command=command,
                    cwd=str(cwd),
                    exit_code=0,
                    stdout="prompt-tune succeeded",
                    stderr="",
                )

            result = run_prompt_tune(
                root=root,
                config=None,
                output_dir=output_dir,
                log_file=log_file,
                report_file=report_file,
                manifest_file=manifest_file,
                domain="course knowledge graph",
                language="Chinese",
                chunk_size=300,
                discover_only=False,
                dry_run=False,
                no_entity_types=False,
                overwrite=True,
                extra_args=[],
                runner=fake_runner,
            )

            self.assertEqual(result["status"], "success")
            self.assertTrue((output_dir / "extract_graph.txt").exists())
            self.assertTrue((output_dir / "summarize_descriptions.txt").exists())
            self.assertTrue((output_dir / "prompt.txt").exists())
            self.assertTrue((output_dir / "README.md").exists())

            readme_text = (output_dir / "README.md").read_text(encoding="utf-8")
            self.assertIn("GraphRAG prompt-tune 自动生成", readme_text)
            self.assertIn("course knowledge graph", readme_text)
            self.assertIn("Chinese", readme_text)

            manifest = json.loads(manifest_file.read_text(encoding="utf-8"))
            auto_tuned = next(item for item in manifest["candidates"] if item["candidate_name"] == "auto_tuned")
            self.assertEqual(auto_tuned["source_type"], "graphrag_prompt_tune")
            self.assertEqual(auto_tuned["generation_method"], "graphrag_official_prompt_tune")
            self.assertEqual(auto_tuned["status"], "success")
            self.assertIn("graphrag_invocation", auto_tuned)
            self.assertIn("extract_graph.txt", auto_tuned["collected_files"])

            report = json.loads(report_file.read_text(encoding="utf-8"))
            self.assertEqual(report["status"], "success")
            self.assertEqual(report["exit_code"], 0)
            self.assertEqual(report["primary_prompt_file"], "extract_graph.txt")
            self.assertIn("extract_graph.txt", report["collected_files"])

    def test_run_prompt_tune_raises_clear_error_when_no_invocation_is_available(self):
        with tempfile.TemporaryDirectory() as tmp_dir:
            workspace = Path(tmp_dir)
            root = workspace / "graph"
            self._make_workspace(root)

            output_dir = root / "prompts" / "candidates" / "auto_tuned"
            log_file = root / "results" / "reports" / "prompt_tune_run.log"
            report_file = root / "results" / "reports" / "prompt_tune_report.json"
            manifest_file = root / "prompts" / "candidates" / "manifest.json"
            self._make_manifest(manifest_file)

            def fake_runner(command: list[str], cwd: Path, env: dict[str, str]) -> CommandExecutionResult:
                if command[:3] == [sys.executable, "-m", "graphrag"]:
                    return CommandExecutionResult(
                        command=command,
                        cwd=str(cwd),
                        exit_code=1,
                        stdout="",
                        stderr="No module named graphrag",
                    )
                return CommandExecutionResult(
                    command=command,
                    cwd=str(cwd),
                    exit_code=127,
                    stdout="",
                    stderr="command not found",
                )

            with self.assertRaises(PromptTuneError) as context:
                run_prompt_tune(
                    root=root,
                    config=None,
                    output_dir=output_dir,
                    log_file=log_file,
                    report_file=report_file,
                    manifest_file=manifest_file,
                    domain=None,
                    language=None,
                    chunk_size=None,
                    discover_only=True,
                    dry_run=False,
                    no_entity_types=False,
                    overwrite=True,
                    extra_args=[],
                    runner=fake_runner,
                )

            message = str(context.exception)
            self.assertIn("未找到可用的 GraphRAG prompt-tune 调用方式", message)
            self.assertIn("python -m graphrag prompt-tune", message)
            self.assertIn("graphrag prompt-tune", message)
            self.assertIn("uv run poe prompt_tune", message)


if __name__ == "__main__":
    unittest.main()
