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


def run_material_qa_smoke(
    *,
    root: Path,
    qa_file: str | Path,
    output_file: str | Path,
    method: str = "local",
    runner: Runner | None = None,
) -> dict[str, Any]:
    """运行 material QA smoke 并写出关键词命中报告。"""

    project_root = root.resolve()
    qa_path = _resolve(qa_file, root=project_root)
    output_path = _resolve(output_file, root=project_root)
    questions = _load_questions(qa_path)
    execute = runner or _default_runner

    results: list[dict[str, Any]] = []
    for question in questions:
        question_id = str(question.get("id") or "").strip()
        question_text = str(question.get("question") or "").strip()
        expected_keywords = [
            str(item).strip()
            for item in (question.get("expected_keywords") or [])
            if str(item).strip()
        ]
        command = ["graphrag", "query", "--root", ".", "--method", method, question_text]
        completed = execute(command, project_root)
        answer = completed.stdout.strip()
        matched_keywords = _match_keywords(answer, expected_keywords)
        passed = completed.returncode == 0 and len(matched_keywords) == len(expected_keywords)
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
        "total": len(results),
        "passed": passed_count,
        "failed": failed_count,
        "results": results,
    }
    output_path.parent.mkdir(parents=True, exist_ok=True)
    output_path.write_text(json.dumps(report, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    return {
        "status": "success",
        "root": str(project_root),
        "qa_file": str(qa_path),
        "output_file": str(output_path),
        "method": method,
        "total": len(results),
        "passed": passed_count,
        "failed": failed_count,
    }


def build_parser() -> argparse.ArgumentParser:
    parser = argparse.ArgumentParser(description="运行 material 级 QA smoke 验证")
    parser.add_argument("--root", default=".", help="GraphRAG 项目根目录")
    parser.add_argument("--qa-file", default=str(DEFAULT_QA_FILE), help="QA smoke JSON 文件")
    parser.add_argument("--output-file", default=str(DEFAULT_OUTPUT_FILE), help="报告输出 JSON 文件")
    parser.add_argument("--method", default="local", choices=["local", "global", "drift", "basic"], help="GraphRAG 查询方法")
    return parser


def main(argv: Sequence[str] | None = None) -> int:
    args = build_parser().parse_args(argv)
    summary = run_material_qa_smoke(
        root=Path(args.root),
        qa_file=args.qa_file,
        output_file=args.output_file,
        method=args.method,
    )
    print(json.dumps(summary, ensure_ascii=False, indent=2))
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
