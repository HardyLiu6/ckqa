#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""material 级 QA smoke 验证脚本。"""

from __future__ import annotations

import argparse
import json
import subprocess
from datetime import datetime, timezone
from pathlib import Path
from typing import Any, Callable, Sequence

try:
    from run_graphrag_index import (
        build_active_prompt_snapshot,
        build_output_artifacts_snapshot,
        build_output_stats_snapshot,
    )
except ModuleNotFoundError:
    import sys

    scripts_root = Path(__file__).resolve().parents[1]
    if str(scripts_root) not in sys.path:
        sys.path.insert(0, str(scripts_root))
    from run_graphrag_index import (
        build_active_prompt_snapshot,
        build_output_artifacts_snapshot,
        build_output_stats_snapshot,
    )


PROJECT_ROOT = Path(__file__).resolve().parents[2]
DEFAULT_QA_FILE = Path("data") / "eval" / "material_7_qa_smoke.json"
DEFAULT_OUTPUT_FILE = Path("results") / "qa_eval" / "material_7_smoke.json"

Runner = Callable[[Sequence[str], Path], subprocess.CompletedProcess[str]]


def _now_iso() -> str:
    return datetime.now(timezone.utc).astimezone().isoformat(timespec="seconds")


def _default_runner(command: Sequence[str], cwd: Path) -> subprocess.CompletedProcess[str]:
    return subprocess.run(
        list(command),
        cwd=str(cwd),
        text=True,
        capture_output=True,
        check=False,
    )


def _run_query(
    *,
    command: Sequence[str],
    cwd: Path,
    query_timeout_seconds: float,
    runner: Runner | None,
) -> subprocess.CompletedProcess[str]:
    if runner is not None:
        return runner(command, cwd)
    try:
        return subprocess.run(
            list(command),
            cwd=str(cwd),
            text=True,
            capture_output=True,
            check=False,
            timeout=query_timeout_seconds,
        )
    except subprocess.TimeoutExpired as exc:
        return subprocess.CompletedProcess(
            list(command),
            124,
            stdout=exc.stdout or "",
            stderr=f"query timeout after {query_timeout_seconds:g}s",
        )


def _resolve(path: str | Path, *, root: Path) -> Path:
    candidate = path if isinstance(path, Path) else Path(path)
    return candidate.resolve() if candidate.is_absolute() else (root / candidate).resolve()


def _load_questions(path: Path) -> list[dict[str, Any]]:
    payload = json.loads(path.read_text(encoding="utf-8"))
    questions = payload.get("questions")
    if not isinstance(questions, list) or not questions:
        raise ValueError(f"QA smoke 文件缺少 questions 列表：{path}")
    return [item for item in questions if isinstance(item, dict)]


def _match_keywords(answer: str, expected_keywords: Sequence[str]) -> list[str]:
    return [keyword for keyword in expected_keywords if keyword and keyword in answer]


def _load_expected_keyword_groups(question: dict[str, Any], expected_keywords: Sequence[str]) -> list[dict[str, Any]]:
    groups = question.get("expected_keyword_groups")
    if isinstance(groups, list) and groups:
        normalized: list[dict[str, Any]] = []
        for group in groups:
            if not isinstance(group, dict):
                continue
            label = str(group.get("label") or "").strip()
            keywords = [
                str(item).strip()
                for item in (group.get("keywords") or [])
                if str(item).strip()
            ]
            if label and keywords:
                normalized.append({"label": label, "keywords": keywords})
        if normalized:
            return normalized
    return [{"label": keyword, "keywords": [keyword]} for keyword in expected_keywords]


def _match_keyword_groups(answer: str, groups: Sequence[dict[str, Any]]) -> tuple[list[str], list[str]]:
    matched: list[str] = []
    missing: list[str] = []
    for group in groups:
        label = str(group.get("label") or "").strip()
        keywords = [str(item).strip() for item in group.get("keywords") or [] if str(item).strip()]
        if not label:
            continue
        if any(keyword in answer for keyword in keywords):
            matched.append(label)
        else:
            missing.append(label)
    return matched, missing


def run_material_qa_smoke(
    *,
    root: Path,
    qa_file: str | Path,
    output_file: str | Path,
    method: str = "local",
    index_run_id: str | None = None,
    index_report_file: str | Path | None = None,
    query_timeout_seconds: float = 180,
    runner: Runner | None = None,
) -> dict[str, Any]:
    """运行 material QA smoke 并写出关键词命中报告。"""

    project_root = root.resolve()
    qa_path = _resolve(qa_file, root=project_root)
    output_path = _resolve(output_file, root=project_root)
    resolved_index_report_file = (
        _resolve(index_report_file, root=project_root)
        if index_report_file is not None
        else None
    )
    questions = _load_questions(qa_path)

    results: list[dict[str, Any]] = []
    for question in questions:
        question_id = str(question.get("id") or "").strip()
        question_text = str(question.get("question") or "").strip()
        expected_keywords = [
            str(item).strip()
            for item in (question.get("expected_keywords") or [])
            if str(item).strip()
        ]
        expected_keyword_groups = _load_expected_keyword_groups(question, expected_keywords)
        command = ["graphrag", "query", "--root", ".", "--method", method, question_text]
        completed = _run_query(
            command=command,
            cwd=project_root,
            query_timeout_seconds=query_timeout_seconds,
            runner=runner,
        )
        answer = completed.stdout.strip()
        matched_keywords = _match_keywords(answer, expected_keywords)
        matched_keyword_groups, missing_keyword_groups = _match_keyword_groups(
            answer,
            expected_keyword_groups,
        )
        passed = completed.returncode == 0 and not missing_keyword_groups
        results.append(
            {
                "id": question_id,
                "question": question_text,
                "method": method,
                "expected_keywords": expected_keywords,
                "matched_keywords": matched_keywords,
                "missing_keywords": [
                    keyword for keyword in expected_keywords if keyword not in matched_keywords
                ],
                "expected_keyword_groups": expected_keyword_groups,
                "matched_keyword_groups": matched_keyword_groups,
                "missing_keyword_groups": missing_keyword_groups,
                "evidence_heading": question.get("evidence_heading"),
                "passed": passed,
                "returncode": completed.returncode,
                "answer": answer,
                "stderr_tail": completed.stderr[-1200:] if completed.stderr else "",
                "command": command,
            }
        )

    passed_count = sum(1 for item in results if item["passed"])
    failed_count = len(results) - passed_count
    report = {
        "task": "material_qa_smoke",
        "status": "success",
        "generated_at": _now_iso(),
        "root": str(project_root),
        "qa_file": str(qa_path),
        "method": method,
        "query_timeout_seconds": query_timeout_seconds,
        "total": len(results),
        "passed": passed_count,
        "failed": failed_count,
        "active_prompt_snapshot": build_active_prompt_snapshot(project_root),
        "index_stats": build_output_stats_snapshot(project_root),
        "output_artifacts": build_output_artifacts_snapshot(project_root),
        "results": results,
    }
    if index_run_id is not None:
        report["index_run_id"] = index_run_id
    if resolved_index_report_file is not None:
        report["index_report_file"] = str(resolved_index_report_file)
    output_path.parent.mkdir(parents=True, exist_ok=True)
    output_path.write_text(json.dumps(report, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    summary = {
        "status": "success",
        "root": str(project_root),
        "qa_file": str(qa_path),
        "output_file": str(output_path),
        "method": method,
        "query_timeout_seconds": query_timeout_seconds,
        "total": len(results),
        "passed": passed_count,
        "failed": failed_count,
    }
    if index_run_id is not None:
        summary["index_run_id"] = index_run_id
    if resolved_index_report_file is not None:
        summary["index_report_file"] = str(resolved_index_report_file)
    return summary


def build_parser() -> argparse.ArgumentParser:
    parser = argparse.ArgumentParser(description="运行 material 级 QA smoke 验证")
    parser.add_argument("--root", default=".", help="GraphRAG 项目根目录")
    parser.add_argument("--qa-file", default=str(DEFAULT_QA_FILE), help="QA smoke JSON 文件")
    parser.add_argument("--output-file", default=str(DEFAULT_OUTPUT_FILE), help="报告输出 JSON 文件")
    parser.add_argument("--method", default="local", choices=["local", "global", "drift", "basic"], help="GraphRAG 查询方法")
    parser.add_argument("--index-run-id", help="绑定到本次 QA smoke 的 index run ID")
    parser.add_argument("--index-report-file", help="绑定到本次 QA smoke 的 index report 路径")
    parser.add_argument("--query-timeout", type=float, default=180, help="单个 graphrag query 超时秒数，默认 180")
    return parser


def main(argv: Sequence[str] | None = None) -> int:
    args = build_parser().parse_args(argv)
    summary = run_material_qa_smoke(
        root=Path(args.root),
        qa_file=args.qa_file,
        output_file=args.output_file,
        method=args.method,
        index_run_id=args.index_run_id,
        index_report_file=args.index_report_file,
        query_timeout_seconds=args.query_timeout,
    )
    print(json.dumps(summary, ensure_ascii=False, indent=2))
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
