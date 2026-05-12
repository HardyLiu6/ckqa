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

    def test_cli_parser_supports_require_domain_language_flag(self):
        args = prompt_tune_module._build_parser().parse_args(["--require-domain-language"])

        self.assertTrue(args.require_domain_language)

    def test_cli_parser_supports_require_auto_quality_flag(self):
        args = prompt_tune_module._build_parser().parse_args(["--require-auto-quality"])

        self.assertTrue(args.require_auto_quality)

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
            self.assertEqual(
                report["quality_checks"],
                {
                    "domain_explicit": True,
                    "language_explicit": True,
                    "primary_prompt_exists": False,
                    "course_domain_warning": None,
                    "quality_warnings": [],
                },
            )

            manifest = json.loads(manifest_file.read_text(encoding="utf-8"))
            auto_tuned = next(item for item in manifest["candidates"] if item["candidate_name"] == "auto_tuned")
            self.assertEqual(auto_tuned["source_type"], "fallback_default_copy")

    def test_run_prompt_tune_dry_run_reports_missing_domain_language_risk(self):
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
                return CommandExecutionResult(
                    command=command,
                    cwd=str(cwd),
                    exit_code=0,
                    stdout="unexpected",
                    stderr="",
                )

            result = run_prompt_tune(
                root=root,
                config=None,
                output_dir=output_dir,
                log_file=log_file,
                report_file=report_file,
                manifest_file=manifest_file,
                domain=None,
                language=None,
                chunk_size=None,
                discover_only=False,
                dry_run=True,
                no_entity_types=False,
                overwrite=True,
                extra_args=[],
                runner=fake_runner,
            )

            self.assertEqual(result["status"], "dry_run")
            report = json.loads(report_file.read_text(encoding="utf-8"))
            quality_checks = report["quality_checks"]
            self.assertFalse(quality_checks["domain_explicit"])
            self.assertFalse(quality_checks["language_explicit"])
            self.assertFalse(quality_checks["primary_prompt_exists"])
            self.assertIn("未显式传入 domain/language", quality_checks["course_domain_warning"])
            self.assertEqual(len(quality_checks["quality_warnings"]), 2)
            self.assertTrue(any("domain" in item for item in quality_checks["quality_warnings"]))
            self.assertTrue(any("language" in item for item in quality_checks["quality_warnings"]))
            self.assertEqual(report["warnings"], quality_checks["quality_warnings"])

    def test_run_prompt_tune_require_domain_language_fails_before_execution(self):
        with tempfile.TemporaryDirectory() as tmp_dir:
            workspace = Path(tmp_dir)
            root = workspace / "graph"
            self._make_workspace(root)

            output_dir = root / "prompts" / "candidates" / "auto_tuned"
            log_file = root / "results" / "reports" / "prompt_tune_run.log"
            report_file = root / "results" / "reports" / "prompt_tune_report.json"
            manifest_file = root / "prompts" / "candidates" / "manifest.json"
            self._make_manifest(manifest_file)

            calls: list[list[str]] = []

            def fake_runner(command: list[str], cwd: Path, env: dict[str, str]) -> CommandExecutionResult:
                calls.append(command)
                return CommandExecutionResult(
                    command=command,
                    cwd=str(cwd),
                    exit_code=0,
                    stdout="Usage: prompt-tune [OPTIONS]",
                    stderr="",
                )

            with self.assertRaises(PromptTuneError) as context:
                run_prompt_tune(
                    root=root,
                    config=None,
                    output_dir=output_dir,
                    log_file=log_file,
                    report_file=report_file,
                    manifest_file=manifest_file,
                    domain="course knowledge graph",
                    language=None,
                    chunk_size=None,
                    discover_only=False,
                    dry_run=False,
                    no_entity_types=False,
                    overwrite=True,
                    extra_args=[],
                    require_domain_language=True,
                    runner=fake_runner,
                )

            self.assertEqual(calls, [])
            self.assertIn("缺少显式 language", str(context.exception))
            report = json.loads(report_file.read_text(encoding="utf-8"))
            self.assertEqual(report["status"], "failed")
            self.assertIn("缺少显式 language", report["error_summary"])
            self.assertNotIn("Traceback", json.dumps(report, ensure_ascii=False))
            self.assertEqual(report["attempts"], [])
            self.assertTrue(report["quality_checks"]["domain_explicit"])
            self.assertFalse(report["quality_checks"]["language_explicit"])
            self.assertIn("缺少显式 language", report["quality_checks"]["quality_warnings"])

    def test_run_prompt_tune_run_id_uses_isolated_overwritten_log_by_default(self):
        with tempfile.TemporaryDirectory() as tmp_dir:
            workspace = Path(tmp_dir)
            root = workspace / "graph"
            self._make_workspace(root)

            run_id = "material_7_real_20260509"
            shared_log = root / "results" / "reports" / "prompt_tune_run.log"
            shared_log.write_text("old shared traceback\n", encoding="utf-8")
            isolated_log = root / "results" / "prompt_tune_runs" / run_id / "prompt_tune_run.log"
            isolated_log.parent.mkdir(parents=True, exist_ok=True)
            isolated_log.write_text("stale same-run traceback\n", encoding="utf-8")

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
                config=None,
                output_dir=root / "prompts" / "candidates" / "auto_tuned",
                log_file=None,
                report_file=report_file,
                manifest_file=manifest_file,
                domain=None,
                language=None,
                chunk_size=None,
                discover_only=False,
                dry_run=True,
                no_entity_types=False,
                overwrite=True,
                extra_args=[],
                run_id=run_id,
                runner=fake_runner,
            )

            self.assertEqual(result["status"], "dry_run")
            self.assertEqual(shared_log.read_text(encoding="utf-8"), "old shared traceback\n")
            isolated_text = isolated_log.read_text(encoding="utf-8")
            self.assertIn("开始执行 GraphRAG prompt-tune 封装", isolated_text)
            self.assertNotIn("stale same-run traceback", isolated_text)

            report = json.loads(report_file.read_text(encoding="utf-8"))
            self.assertEqual(report["run_id"], run_id)
            self.assertEqual(report["log_file"], str(isolated_log.resolve()))
            self.assertEqual(report["resolved_paths"]["log_file"], str(isolated_log.resolve()))
            self.assertEqual(report["log_policy"]["mode"], "overwrite")
            self.assertFalse(report["log_policy"]["append"])
            self.assertFalse(report["log_policy"]["explicit_log_file"])

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
            self.assertTrue(report["quality_checks"]["domain_explicit"])
            self.assertTrue(report["quality_checks"]["language_explicit"])
            self.assertTrue(report["quality_checks"]["primary_prompt_exists"])
            self.assertEqual(report["quality_checks"]["example_entity_grounding"]["status"], "warning")
            self.assertIn("解析出可检查的示例", report["quality_checks"]["quality_warnings"][0])

    def test_run_prompt_tune_success_warns_on_static_auto_quality_issues(self):
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

                root_path = Path(command[command.index("--root") + 1])
                output_arg = command[command.index("--output") + 1]
                raw_output_dir = Path(output_arg)
                if not raw_output_dir.is_absolute():
                    raw_output_dir = root_path / raw_output_dir
                raw_output_dir.mkdir(parents=True, exist_ok=True)
                (raw_output_dir / "extract_graph.txt").write_text(
                    """-Examples-
Example 1:
text:
本节课程介绍虚拟内存和页面置换算法。
output:
(\"entity\"<|>虚拟内存<|>Concept<|>课程概念)
##
(\"entity\"<|>关系数据库<|>Concept<|>不在示例输入中的实体)
<|COMPLETE|>
""",
                    encoding="utf-8",
                )
                (raw_output_dir / "community_report_graph.txt").write_text(
                    "Generate a concise report about emails, companies, and people.",
                    encoding="utf-8",
                )
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
                chunk_size=None,
                discover_only=False,
                dry_run=False,
                no_entity_types=False,
                overwrite=True,
                extra_args=[],
                runner=fake_runner,
            )

            self.assertEqual(result["status"], "success")
            quality_checks = result["quality_checks"]
            grounding = quality_checks["example_entity_grounding"]
            self.assertEqual(grounding["status"], "warning")
            self.assertEqual(grounding["examples_checked"], 1)
            self.assertEqual(grounding["entities_checked"], 2)
            self.assertEqual(grounding["ungrounded_count"], 1)
            self.assertEqual(grounding["ungrounded_entities"][0]["entity_name"], "关系数据库")

            community_domain = quality_checks["community_report_domain"]
            self.assertEqual(community_domain["status"], "warning")
            self.assertEqual(community_domain["checked_file_count"], 1)
            self.assertEqual(community_domain["failed_file_count"], 1)
            self.assertTrue(any("关系数据库" in item for item in quality_checks["quality_warnings"]))
            self.assertTrue(any("课程域关键词" in item for item in quality_checks["quality_warnings"]))

    def test_run_prompt_tune_require_auto_quality_fails_after_static_checks(self):
        with tempfile.TemporaryDirectory() as tmp_dir:
            workspace = Path(tmp_dir)
            root = workspace / "graph"
            self._make_workspace(root)

            output_dir = root / "prompts" / "candidates" / "auto_tuned"
            log_file = root / "results" / "reports" / "prompt_tune_run.log"
            report_file = root / "results" / "reports" / "prompt_tune_report.json"
            manifest_file = root / "prompts" / "candidates" / "manifest.json"
            self._make_manifest(manifest_file)
            prompt_tune_commands: list[list[str]] = []

            def fake_runner(command: list[str], cwd: Path, env: dict[str, str]) -> CommandExecutionResult:
                if command[-1] == "--help":
                    return CommandExecutionResult(
                        command=command,
                        cwd=str(cwd),
                        exit_code=0,
                        stdout="Usage: prompt-tune [OPTIONS]",
                        stderr="",
                    )

                prompt_tune_commands.append(command)
                root_path = Path(command[command.index("--root") + 1])
                output_arg = command[command.index("--output") + 1]
                raw_output_dir = Path(output_arg)
                if not raw_output_dir.is_absolute():
                    raw_output_dir = root_path / raw_output_dir
                raw_output_dir.mkdir(parents=True, exist_ok=True)
                (raw_output_dir / "extract_graph.txt").write_text(
                    """Example 1:
text:
课程内容只包含进程调度。
output:
(\"entity\"<|>关系数据库<|>Concept<|>不在示例输入中的实体)
""",
                    encoding="utf-8",
                )
                (raw_output_dir / "community_report_graph.txt").write_text(
                    "Generate a report about product releases and investors.",
                    encoding="utf-8",
                )
                return CommandExecutionResult(
                    command=command,
                    cwd=str(cwd),
                    exit_code=0,
                    stdout="prompt-tune succeeded",
                    stderr="",
                )

            with self.assertRaises(PromptTuneError) as context:
                run_prompt_tune(
                    root=root,
                    config=None,
                    output_dir=output_dir,
                    log_file=log_file,
                    report_file=report_file,
                    manifest_file=manifest_file,
                    domain="course knowledge graph",
                    language="Chinese",
                    chunk_size=None,
                    discover_only=False,
                    dry_run=False,
                    no_entity_types=False,
                    overwrite=True,
                    extra_args=[],
                    require_auto_quality=True,
                    runner=fake_runner,
                )

            self.assertEqual(len(prompt_tune_commands), 1)
            self.assertIn("--require-auto-quality", str(context.exception))
            report = json.loads(report_file.read_text(encoding="utf-8"))
            self.assertEqual(report["status"], "failed")
            self.assertEqual(report["quality_checks"]["example_entity_grounding"]["status"], "failed")
            self.assertEqual(report["quality_checks"]["community_report_domain"]["status"], "failed")
            manifest = json.loads(manifest_file.read_text(encoding="utf-8"))
            auto_tuned = next(item for item in manifest["candidates"] if item["candidate_name"] == "auto_tuned")
            self.assertEqual(auto_tuned["source_type"], "fallback_default_copy")

    def test_run_prompt_tune_require_auto_quality_does_not_fail_when_examples_are_unparsed(self):
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

                root_path = Path(command[command.index("--root") + 1])
                output_arg = command[command.index("--output") + 1]
                raw_output_dir = Path(output_arg)
                if not raw_output_dir.is_absolute():
                    raw_output_dir = root_path / raw_output_dir
                raw_output_dir.mkdir(parents=True, exist_ok=True)
                (raw_output_dir / "extract_graph.txt").write_text(
                    "This official prompt variant has no parseable examples.",
                    encoding="utf-8",
                )
                (raw_output_dir / "community_report_graph.txt").write_text(
                    "请围绕课程知识、章节结构和学习目标生成社区报告。",
                    encoding="utf-8",
                )
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
                chunk_size=None,
                discover_only=False,
                dry_run=False,
                no_entity_types=False,
                overwrite=True,
                extra_args=[],
                require_auto_quality=True,
                runner=fake_runner,
            )

            self.assertEqual(result["status"], "success")
            self.assertEqual(result["quality_checks"]["example_entity_grounding"]["status"], "warning")
            self.assertEqual(result["quality_checks"]["example_entity_grounding"]["entities_checked"], 0)
            self.assertEqual(result["quality_checks"]["community_report_domain"]["status"], "passed")


    def test_run_prompt_tune_falls_back_when_response_format_is_unavailable(self):
        with tempfile.TemporaryDirectory() as tmp_dir:
            workspace = Path(tmp_dir)
            root = workspace / "graph"
            self._make_workspace(root)

            output_dir = root / "prompts" / "candidates" / "auto_tuned"
            log_file = root / "results" / "reports" / "prompt_tune_run.log"
            report_file = root / "results" / "reports" / "prompt_tune_report.json"
            manifest_file = root / "prompts" / "candidates" / "manifest.json"
            self._make_manifest(manifest_file)

            prompt_tune_commands: list[list[str]] = []

            def fake_runner(command: list[str], cwd: Path, env: dict[str, str]) -> CommandExecutionResult:
                if command[-1] == "--help":
                    return CommandExecutionResult(
                        command=command,
                        cwd=str(cwd),
                        exit_code=0,
                        stdout="Usage: prompt-tune [OPTIONS]",
                        stderr="",
                    )

                prompt_tune_commands.append(command)
                if "--no-discover-entity-types" not in command:
                    return CommandExecutionResult(
                        command=command,
                        cwd=str(cwd),
                        exit_code=1,
                        stdout='{"error":{"type":"invalid_request_error"}}',
                        stderr="This response_format type is unavailable now",
                    )

                root_path = Path(command[command.index("--root") + 1])
                output_arg = command[command.index("--output") + 1]
                raw_output_dir = Path(output_arg)
                if not raw_output_dir.is_absolute():
                    raw_output_dir = root_path / raw_output_dir
                raw_output_dir.mkdir(parents=True, exist_ok=True)
                (raw_output_dir / "extract_graph.txt").write_text("-Goal-\nFallback tuned\n", encoding="utf-8")
                return CommandExecutionResult(
                    command=command,
                    cwd=str(cwd),
                    exit_code=0,
                    stdout="prompt-tune succeeded without entity type discovery",
                    stderr="",
                )

            result = run_prompt_tune(
                root=root,
                config=None,
                output_dir=output_dir,
                log_file=log_file,
                report_file=report_file,
                manifest_file=manifest_file,
                domain=None,
                language=None,
                chunk_size=None,
                discover_only=False,
                dry_run=False,
                no_entity_types=False,
                overwrite=True,
                extra_args=[],
                runner=fake_runner,
            )

            self.assertEqual(result["status"], "success")
            self.assertEqual(len(prompt_tune_commands), 2)
            self.assertNotIn("--no-discover-entity-types", prompt_tune_commands[0])
            self.assertIn("--no-discover-entity-types", prompt_tune_commands[1])

            report = json.loads(report_file.read_text(encoding="utf-8"))
            self.assertTrue(report["entity_type_discovery_disabled_by_fallback"])
            self.assertIn("response_format", report["fallback_reason"])
            self.assertEqual(len(report["attempts"]), 2)
            self.assertEqual(report["attempts"][0]["returncode"], 1)
            self.assertEqual(report["attempts"][1]["returncode"], 0)
            self.assertIn("--no-discover-entity-types", report["attempts"][1]["command"])

    def test_run_prompt_tune_does_not_retry_when_no_entity_types_is_explicit(self):
        with tempfile.TemporaryDirectory() as tmp_dir:
            workspace = Path(tmp_dir)
            root = workspace / "graph"
            self._make_workspace(root)

            output_dir = root / "prompts" / "candidates" / "auto_tuned"
            log_file = root / "results" / "reports" / "prompt_tune_run.log"
            report_file = root / "results" / "reports" / "prompt_tune_report.json"
            manifest_file = root / "prompts" / "candidates" / "manifest.json"
            self._make_manifest(manifest_file)

            prompt_tune_commands: list[list[str]] = []

            def fake_runner(command: list[str], cwd: Path, env: dict[str, str]) -> CommandExecutionResult:
                if command[-1] == "--help":
                    return CommandExecutionResult(
                        command=command,
                        cwd=str(cwd),
                        exit_code=0,
                        stdout="Usage: prompt-tune [OPTIONS]",
                        stderr="",
                    )

                prompt_tune_commands.append(command)
                return CommandExecutionResult(
                    command=command,
                    cwd=str(cwd),
                    exit_code=1,
                    stdout='{"error":{"type":"invalid_request_error"}}',
                    stderr="This response_format type is unavailable now",
                )

            with self.assertRaises(PromptTuneError):
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
                    discover_only=False,
                    dry_run=False,
                    no_entity_types=True,
                    overwrite=True,
                    extra_args=[],
                    runner=fake_runner,
                )

            self.assertEqual(len(prompt_tune_commands), 1)
            report = json.loads(report_file.read_text(encoding="utf-8"))
            self.assertEqual(report["status"], "failed")
            self.assertFalse(report["entity_type_discovery_disabled_by_fallback"])
            self.assertEqual(len(report["attempts"]), 1)
            self.assertIn("stderr_summary", report["attempts"][0])
            self.assertNotIn("stderr_tail", report["attempts"][0])

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
