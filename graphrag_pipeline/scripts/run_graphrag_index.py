#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""成本优先的 GraphRAG index 入口。

默认保留 GraphRAG cache，避免意外重跑已经成功的 LLM 抽图请求。
"""

from __future__ import annotations

import argparse
import json
import subprocess
import sys
from datetime import datetime, timezone
from pathlib import Path
from typing import Any, Callable, Sequence


Runner = Callable[[Sequence[str], Path], subprocess.CompletedProcess[object]]

KEY_OUTPUT_ARTIFACTS = (
    "stats.json",
    "documents.parquet",
    "text_units.parquet",
    "entities.parquet",
    "relationships.parquet",
    "communities.parquet",
    "community_reports.parquet",
    "lancedb",
)


def _read_env_value(env_file: Path, key: str) -> str | None:
    if not env_file.exists():
        return None
    for line in env_file.read_text(encoding="utf-8").splitlines():
        stripped = line.strip()
        if not stripped or stripped.startswith("#") or "=" not in stripped:
            continue
        name, value = stripped.split("=", 1)
        if name.strip() != key:
            continue
        value = value.strip()
        if "#" in value and not value.startswith(("'", '"')):
            value = value.split("#", 1)[0].strip()
        if len(value) >= 2 and value[0] == value[-1] and value[0] in {"'", '"'}:
            value = value[1:-1]
        return value
    return None


def _resolve_root(path: str) -> Path:
    return Path(path).expanduser().resolve()


def _resolve_child_or_absolute(root: Path, raw_path: str) -> Path:
    path = Path(raw_path).expanduser()
    if path.is_absolute():
        return path.resolve()
    return (root / path).resolve()


def _count_extract_graph_cache_files(cache_dir: Path) -> int:
    extract_graph_dir = cache_dir / "extract_graph"
    if not extract_graph_dir.exists():
        return 0
    return sum(1 for path in extract_graph_dir.rglob("*") if path.is_file())


def _existing_output_files(output_dir: Path) -> list[str]:
    if not output_dir.exists():
        return []
    return sorted(path.name for path in output_dir.iterdir())


def _now_iso() -> str:
    return datetime.now(timezone.utc).astimezone().isoformat(timespec="seconds")


def _default_run_id() -> str:
    return "index_" + datetime.now(timezone.utc).strftime("%Y%m%dT%H%M%SZ")


def _read_json(path: Path) -> tuple[Any | None, str | None]:
    try:
        return json.loads(path.read_text(encoding="utf-8")), None
    except OSError as exc:
        return None, f"{type(exc).__name__}: {exc}"
    except json.JSONDecodeError as exc:
        return None, f"{type(exc).__name__}: {exc}"


def _summarize_stats(payload: Any) -> dict[str, object]:
    if not isinstance(payload, dict):
        return {}

    summary: dict[str, object] = {}
    for key in ("num_documents", "update_documents", "total_runtime", "input_load_time"):
        if key in payload:
            summary[key] = payload[key]

    workflows = payload.get("workflows")
    if isinstance(workflows, dict):
        workflow_names = sorted(str(name) for name in workflows.keys())
        summary["workflow_count"] = len(workflow_names)
        summary["workflow_names"] = workflow_names
        runtime_by_workflow: dict[str, object] = {}
        for name, workflow in workflows.items():
            if isinstance(workflow, dict) and "overall" in workflow:
                runtime_by_workflow[str(name)] = workflow["overall"]
        if runtime_by_workflow:
            summary["workflow_runtime_seconds"] = runtime_by_workflow
    return summary


def build_active_prompt_snapshot(root: Path) -> dict[str, object]:
    path = root / "prompts" / "final" / "active_prompt.json"
    snapshot: dict[str, object] = {
        "path": str(path.resolve()),
        "exists": path.exists(),
    }
    if not path.exists():
        return snapshot

    payload, error = _read_json(path)
    if error:
        snapshot["error"] = error
    else:
        snapshot["payload"] = payload
    return snapshot


def build_output_stats_snapshot(root: Path) -> dict[str, object]:
    path = root / "output" / "stats.json"
    snapshot: dict[str, object] = {
        "path": str(path.resolve()),
        "exists": path.exists(),
    }
    if not path.exists():
        return snapshot

    payload, error = _read_json(path)
    if error:
        snapshot["error"] = error
    else:
        snapshot["summary"] = _summarize_stats(payload)
    return snapshot


def build_output_artifacts_snapshot(root: Path) -> dict[str, dict[str, object]]:
    output_dir = root / "output"
    artifacts: dict[str, dict[str, object]] = {}
    for name in KEY_OUTPUT_ARTIFACTS:
        path = output_dir / name
        artifacts[name] = {
            "path": str(path.resolve()),
            "exists": path.exists(),
            "type": "directory" if path.is_dir() else "file" if path.exists() else None,
        }
    return artifacts


def build_index_report(
    *,
    root: Path,
    run_id: str,
    command: Sequence[str],
    returncode: int,
    dry_run: bool,
    preflight_summary: dict[str, object],
) -> dict[str, object]:
    return {
        "task": "graphrag_index",
        "run_id": run_id,
        "generated_at": _now_iso(),
        "root": str(root),
        "dry_run": dry_run,
        "command": list(command),
        "returncode": returncode,
        "preflight": preflight_summary,
        "active_prompt_snapshot": build_active_prompt_snapshot(root),
        "output_stats": build_output_stats_snapshot(root),
        "output_artifacts": build_output_artifacts_snapshot(root),
    }


def _write_report(report_file: Path, report: dict[str, object]) -> None:
    report_file.parent.mkdir(parents=True, exist_ok=True)
    report_file.write_text(
        json.dumps(report, ensure_ascii=False, indent=2) + "\n",
        encoding="utf-8",
    )


def build_preflight_summary(root: Path, command: Sequence[str]) -> dict[str, object]:
    env_cache_dir = _read_env_value(root / ".env", "GRAPHRAG_CACHE_DIR")
    cache_dir = _resolve_child_or_absolute(root, env_cache_dir or "cache")
    output_dir = root / "output"
    return {
        "root": str(root),
        "cache_dir": str(cache_dir),
        "extract_graph_cache_count": _count_extract_graph_cache_files(cache_dir),
        "output_dir": str(output_dir.resolve()),
        "existing_output_files": _existing_output_files(output_dir),
        "command": list(command),
    }


def _build_parser() -> argparse.ArgumentParser:
    parser = argparse.ArgumentParser(
        description="以保留 cache 为默认策略执行 graphrag index。",
        allow_abbrev=False,
    )
    parser.add_argument("--root", default=".", help="GraphRAG 项目根目录，默认当前目录。")
    parser.add_argument("--method", help="透传给 graphrag index 的可选 method 参数。")
    parser.add_argument("--dry-run", action="store_true", help="只打印预检摘要，不执行 index。")
    parser.add_argument("--verbose", action="store_true", help="打印额外执行信息。")
    parser.add_argument("--run-id", help="写入 index report 的运行 ID。")
    parser.add_argument("--report-file", help="index report 输出路径；相对路径按 --root 解析。")
    parser.add_argument(
        "--allow-no-cache",
        action="store_true",
        help="显式允许透传 --no-cache；默认拒绝以保护 LLM cache。",
    )
    return parser


def _default_runner(command: Sequence[str], cwd: Path) -> subprocess.CompletedProcess[object]:
    return subprocess.run(list(command), cwd=str(cwd), check=False)


def run_graphrag_index(
    argv: Sequence[str] | None = None,
    *,
    runner: Runner | None = None,
) -> int:
    parser = _build_parser()
    args, extra_args = parser.parse_known_args(argv)
    if extra_args and extra_args[0] == "--":
        extra_args = extra_args[1:]

    if "--no-cache" in extra_args and not args.allow_no_cache:
        print(
            "错误：默认禁止使用 --no-cache，以避免重跑已成功的 LLM 抽图请求；"
            "如确需禁用 cache，请显式添加 --allow-no-cache。",
            file=sys.stderr,
        )
        return 2

    root = _resolve_root(args.root)
    run_id = args.run_id or _default_run_id()
    report_file = (
        _resolve_child_or_absolute(root, args.report_file)
        if args.report_file
        else None
    )
    command = ["graphrag", "index", "--root", str(root)]
    if args.method:
        command.extend(["--method", args.method])
    command.extend(extra_args)

    summary = build_preflight_summary(root, command)
    print(json.dumps(summary, ensure_ascii=False, sort_keys=True))

    if args.dry_run:
        if args.verbose:
            print("dry-run：未执行 graphrag index。", file=sys.stderr)
        if report_file:
            _write_report(
                report_file,
                build_index_report(
                    root=root,
                    run_id=run_id,
                    command=command,
                    returncode=0,
                    dry_run=True,
                    preflight_summary=summary,
                ),
            )
        return 0

    execute = runner or _default_runner
    completed = execute(command, root)
    returncode = int(completed.returncode)
    if report_file:
        _write_report(
            report_file,
            build_index_report(
                root=root,
                run_id=run_id,
                command=command,
                returncode=returncode,
                dry_run=False,
                preflight_summary=summary,
            ),
        )
    return returncode


def main() -> int:
    return run_graphrag_index()


if __name__ == "__main__":
    raise SystemExit(main())
