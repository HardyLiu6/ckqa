#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
仓库活跃文件漂移审计脚本
========================
面向当前仍会被工程师直接参考的入口文档、关键脚本与配置文件，
检查常见的版本认知漂移、错误命令示例、仓库外默认路径和疑似硬编码敏感 token。
"""

from __future__ import annotations

import argparse
import json
import re
import sys
from dataclasses import asdict, dataclass
from pathlib import Path
from typing import Iterable, Sequence


ACTIVE_AUDIT_PATHS = (
    Path("README.md"),
    Path("README.en.md"),
    Path("AGENTS.md"),
    Path(".codex"),
    Path("infra/README.md"),
    Path("infra/docker-compose.yml"),
    Path("infra/.env.example"),
    Path("sql/ocqa.sql"),
    Path("pdf_ingest/README.md"),
    Path("pdf_ingest/CLAUDE.md"),
    Path("pdf_ingest/scripts/cleanup_legacy_course_data.py"),
    Path("graphrag_pipeline/README.md"),
    Path("graphrag_pipeline/CLAUDE.md"),
    Path("graphrag_pipeline/pyproject.toml"),
    Path("graphrag_pipeline/requirements.txt"),
    Path("graphrag_pipeline/scripts/generate_candidate_prompts.py"),
    Path("graphrag_pipeline/scripts/run_graphrag_prompt_tune.py"),
    Path("graphrag_pipeline/utils/main.py"),
    Path("graphrag_pipeline/utils/apiTest.py"),
    Path("graphrag_pipeline/utils/graphrag3dknowledge.py"),
    Path("graphrag_pipeline/utils/neo4jTest.py"),
    Path("graphrag_pipeline/utils/runtime_defaults.py"),
    Path("graphrag_pipeline/utils/api_runtime_config.py"),
    Path("docs/archive/GRAPHRAG_3_0_9_VERIFICATION_CHECKLIST.md"),
    Path(".github/workflows/repo-drift-audit.yml"),
)

_PYPROJECT_VERSION_PATTERN = re.compile(r'"graphrag==([^"]+)"')
_MENTIONED_VERSION_PATTERN = re.compile(r"graphrag==(\d+\.\d+\.\d+)")
_SECRET_PATTERN = re.compile(r"sk-[A-Za-z0-9]{20,}")


@dataclass(frozen=True)
class Finding:
    path: str
    line_no: int
    message: str
    line: str


def _extract_target_graphrag_version(repo_root: Path) -> str:
    pyproject_path = repo_root / "graphrag_pipeline" / "pyproject.toml"
    text = pyproject_path.read_text(encoding="utf-8")
    match = _PYPROJECT_VERSION_PATTERN.search(text)
    if match is None:
        raise RuntimeError(f"无法从 {pyproject_path} 解析 graphrag 版本")
    return match.group(1)


def _iter_target_paths(target_paths: Sequence[Path] | None) -> Sequence[Path]:
    if target_paths is None:
        return ACTIVE_AUDIT_PATHS
    return tuple(target_paths)


def _audit_line(
    rel_path: Path,
    line_no: int,
    line: str,
    target_graphrag_version: str,
) -> Iterable[Finding]:
    if "2.7.0" in line:
        yield Finding(
            path=str(rel_path),
            line_no=line_no,
            message="活跃文件仍包含旧 GraphRAG 基线 `2.7.0`",
            line=line,
        )

    if "/home/sunlight/Projects/graphrag-oneapi-exp" in line:
        yield Finding(
            path=str(rel_path),
            line_no=line_no,
            message="活跃文件仍包含仓库外硬编码路径",
            line=line,
        )

    if "graphrag3dknowledge.py" in line and "--input" in line:
        yield Finding(
            path=str(rel_path),
            line_no=line_no,
            message="`graphrag3dknowledge.py` 当前命令应使用 `--directory` 而不是 `--input`",
            line=line,
        )

    if rel_path != Path("scripts/audit_repo_drift.py") and (
        "internal_api" in line or "graphrag_internal_loader" in line
    ):
        yield Finding(
            path=str(rel_path),
            line_no=line_no,
            message="活跃文件不应再提及旧 internal API 兼容逻辑",
            line=line,
        )

    if _SECRET_PATTERN.search(line):
        yield Finding(
            path=str(rel_path),
            line_no=line_no,
            message="活跃文件中发现疑似硬编码敏感 token",
            line=line,
        )

    for match in _MENTIONED_VERSION_PATTERN.finditer(line):
        mentioned_version = match.group(1)
        if mentioned_version != target_graphrag_version:
            yield Finding(
                path=str(rel_path),
                line_no=line_no,
                message=(
                    "活跃文件中的 graphrag 版本与 "
                    f"`graphrag_pipeline/pyproject.toml` 不一致：{mentioned_version}"
                ),
                line=line,
            )


def audit_repo(
    repo_root: Path,
    target_paths: Sequence[Path] | None = None,
) -> list[Finding]:
    """审计仓库活跃文件的版本与命令漂移。"""
    findings: list[Finding] = []
    target_graphrag_version = _extract_target_graphrag_version(repo_root)

    for rel_path in _iter_target_paths(target_paths):
        abs_path = repo_root / rel_path
        if not abs_path.exists():
            findings.append(
                Finding(
                    path=str(rel_path),
                    line_no=0,
                    message="审计目标文件不存在",
                    line="",
                )
            )
            continue

        text = abs_path.read_text(encoding="utf-8")
        for line_no, line in enumerate(text.splitlines(), start=1):
            findings.extend(
                _audit_line(
                    rel_path=rel_path,
                    line_no=line_no,
                    line=line,
                    target_graphrag_version=target_graphrag_version,
                )
            )

    return findings


def _build_parser() -> argparse.ArgumentParser:
    parser = argparse.ArgumentParser(
        description="审计仓库活跃入口文件与关键脚本，避免版本认知与命令示例漂移",
    )
    parser.add_argument(
        "--root",
        default=".",
        help="仓库根目录，默认当前目录",
    )
    parser.add_argument(
        "--path",
        action="append",
        dest="paths",
        default=None,
        help="仅审计指定相对路径，可重复传入多次",
    )
    parser.add_argument(
        "--json",
        action="store_true",
        help="以 JSON 输出结果",
    )
    parser.add_argument(
        "--strict",
        action="store_true",
        help="若发现漂移则返回非 0 退出码",
    )
    return parser


def main(argv: Sequence[str] | None = None) -> int:
    parser = _build_parser()
    args = parser.parse_args(argv)

    repo_root = Path(args.root).resolve()
    target_paths = [Path(item) for item in args.paths] if args.paths else None
    findings = audit_repo(repo_root, target_paths=target_paths)

    if args.json:
        print(
            json.dumps(
                [asdict(finding) for finding in findings],
                ensure_ascii=False,
                indent=2,
            )
        )
    elif findings:
        print(f"[审计失败] 共发现 {len(findings)} 个漂移问题：")
        for finding in findings:
            location = finding.path if finding.line_no <= 0 else f"{finding.path}:{finding.line_no}"
            print(f"- {location} {finding.message}")
            if finding.line:
                print(f"  {finding.line}")
    else:
        print("[审计通过] 未发现活跃文件版本/路径/命令漂移。")

    if findings and args.strict:
        return 1
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
