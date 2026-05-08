#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
候选 Prompt 固化脚本
===================

把 `prompts/candidates/<candidate>/` 中的候选 Prompt 复制到
`prompts/final/<candidate>/`，并更新 `.env` 中当前活动 Prompt 路径。
"""

from __future__ import annotations

import argparse
import hashlib
import json
import shutil
from datetime import datetime, timezone
from pathlib import Path
from typing import Any, Dict, Iterable


DEFAULT_MANIFEST_SUBPATH = Path("prompts") / "candidates" / "manifest.json"
DEFAULT_FINAL_SUBPATH = Path("prompts") / "final"
DEFAULT_ENV_SUBPATH = Path(".env")
ACTIVE_PROMPT_RECORD = "active_prompt.json"

DEFAULT_PROMPT_ENV_PATHS: dict[str, str] = {
    "GRAPHRAG_ENTITY_EXTRACTION_PROMPT_FILE": "prompts/extract_graph.txt",
    "GRAPHRAG_SUMMARIZE_DESCRIPTIONS_PROMPT_FILE": "prompts/summarize_descriptions.txt",
    "GRAPHRAG_CLAIM_EXTRACTION_PROMPT_FILE": "prompts/extract_claims.txt",
    "GRAPHRAG_COMMUNITY_REPORT_GRAPH_PROMPT_FILE": "prompts/community_report_graph.txt",
    "GRAPHRAG_COMMUNITY_REPORT_TEXT_PROMPT_FILE": "prompts/community_report_text.txt",
}

ACTIVE_PROMPT_ENV_MAP: dict[str, str] = {
    "extract_graph.txt": "GRAPHRAG_ENTITY_EXTRACTION_PROMPT_FILE",
    "summarize_descriptions.txt": "GRAPHRAG_SUMMARIZE_DESCRIPTIONS_PROMPT_FILE",
    "extract_claims.txt": "GRAPHRAG_CLAIM_EXTRACTION_PROMPT_FILE",
    "community_report_graph.txt": "GRAPHRAG_COMMUNITY_REPORT_GRAPH_PROMPT_FILE",
    "community_report_text.txt": "GRAPHRAG_COMMUNITY_REPORT_TEXT_PROMPT_FILE",
}


class PromptFinalizationError(RuntimeError):
    """候选 Prompt 固化失败。"""


def _now_iso() -> str:
    return datetime.now(timezone.utc).astimezone().isoformat(timespec="seconds")


def _read_json(path: Path) -> Any:
    return json.loads(path.read_text(encoding="utf-8"))


def _write_json(path: Path, payload: Dict[str, Any]) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(json.dumps(payload, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")


def _resolve_path(value: str | Path | None, *, root: Path) -> Path:
    if value is None:
        raise PromptFinalizationError("缺少必要路径参数")
    path = value if isinstance(value, Path) else Path(value)
    if path.is_absolute():
        return path.resolve()
    return (root / path).resolve()


def compute_file_sha256(path: str | Path) -> str:
    digest = hashlib.sha256()
    with Path(path).open("rb") as handle:
        for chunk in iter(lambda: handle.read(1024 * 1024), b""):
            digest.update(chunk)
    return digest.hexdigest()


def _remove_scoring_hashes(value: Any) -> Any:
    if isinstance(value, dict):
        result: dict[str, Any] = {}
        for key, item in value.items():
            if key == "scoring_result_sha256":
                continue
            result[key] = _remove_scoring_hashes(item)
        return result
    if isinstance(value, list):
        return [_remove_scoring_hashes(item) for item in value]
    return value


def compute_scoring_report_sha256(path: str | Path) -> str:
    payload = _read_json(Path(path))
    stripped = _remove_scoring_hashes(payload)
    canonical = json.dumps(
        stripped,
        ensure_ascii=False,
        sort_keys=True,
        separators=(",", ":"),
    ).encode("utf-8")
    return hashlib.sha256(canonical).hexdigest()


def _as_repo_relative(path: Path, *, root: Path) -> str:
    return path.resolve().relative_to(root.resolve()).as_posix()


def _load_manifest_candidate(*, manifest_path: Path, candidate_name: str) -> dict[str, Any]:
    payload = _read_json(manifest_path)
    raw_candidates = payload.get("candidates")
    if not isinstance(raw_candidates, list):
        raise PromptFinalizationError(f"manifest 缺少 candidates 列表：{manifest_path}")

    for item in raw_candidates:
        if isinstance(item, dict) and str(item.get("candidate_name") or "").strip() == candidate_name:
            return item

    raise PromptFinalizationError(f"manifest 中未找到候选 Prompt：{candidate_name}")


def _validate_scoring_binding(
    *,
    root: Path,
    candidate_name: str,
    manifest_path: Path,
    scoring_run_id: str | None,
    scoring_report: str | Path | None,
    expected_manifest_sha256: str | None,
    expected_scoring_result_sha256: str | None,
    min_parse_success_rate: float,
) -> dict[str, Any] | None:
    provided = [
        scoring_run_id,
        str(scoring_report) if scoring_report else None,
        expected_manifest_sha256,
        expected_scoring_result_sha256,
    ]
    if not any(provided):
        return None
    if not all(provided):
        raise PromptFinalizationError(
            "启用 run artifact 校验时必须同时提供 "
            "--scoring-run-id / --scoring-report / --expected-manifest-sha256 / "
            "--expected-scoring-result-sha256"
        )

    assert scoring_run_id is not None
    assert scoring_report is not None
    assert expected_manifest_sha256 is not None
    assert expected_scoring_result_sha256 is not None

    scoring_path = _resolve_path(scoring_report, root=root)
    if not scoring_path.exists():
        raise PromptFinalizationError(f"scoring report 不存在：{scoring_path}")
    payload = _read_json(scoring_path)
    top_candidates = payload.get("top_candidates")
    if not isinstance(top_candidates, list) or not top_candidates:
        raise PromptFinalizationError(f"scoring report 缺少 top_candidates：{scoring_path}")

    best = top_candidates[0]
    if not isinstance(best, dict):
        raise PromptFinalizationError(f"scoring report top_candidates[0] 结构异常：{scoring_path}")
    if str(best.get("candidate") or "").strip() != candidate_name:
        raise PromptFinalizationError(
            "scoring report 的 top candidate 与待固化候选不一致："
            f"expected={candidate_name}, actual={best.get('candidate')}"
        )

    parse_success_rate = float(best.get("parse_success_rate") or 0.0)
    gate_passed = bool(best.get("gate_passed"))
    if parse_success_rate < min_parse_success_rate or not gate_passed:
        raise PromptFinalizationError(
            "候选未通过 scoring gate，拒绝固化："
            f"candidate={candidate_name}, parse_success_rate={parse_success_rate:.3f}, "
            f"gate_passed={gate_passed}"
        )

    binding = best.get("artifact_binding") or payload.get("artifact_binding") or {}
    if not isinstance(binding, dict):
        raise PromptFinalizationError("scoring report artifact_binding 结构异常")
    if str(binding.get("run_id") or "").strip() != scoring_run_id:
        raise PromptFinalizationError(
            f"scoring run_id 不一致：expected={scoring_run_id}, actual={binding.get('run_id')}"
        )
    if str(binding.get("candidate_id") or candidate_name) != candidate_name:
        raise PromptFinalizationError(
            f"candidate_id 不一致：expected={candidate_name}, actual={binding.get('candidate_id')}"
        )

    recorded_manifest_hash = str(binding.get("manifest_sha256") or "")
    if recorded_manifest_hash != expected_manifest_sha256:
        raise PromptFinalizationError(
            "manifest hash 期望值与 scoring report 不一致："
            f"expected={expected_manifest_sha256}, actual={recorded_manifest_hash}"
        )
    current_manifest_hash = compute_file_sha256(manifest_path)
    if current_manifest_hash != expected_manifest_sha256:
        raise PromptFinalizationError(
            "manifest hash 已变化，拒绝固化："
            f"expected={expected_manifest_sha256}, actual={current_manifest_hash}"
        )

    recorded_scoring_hash = str(binding.get("scoring_result_sha256") or "")
    if recorded_scoring_hash != expected_scoring_result_sha256:
        raise PromptFinalizationError(
            "scoring result hash 期望值与 scoring report 不一致："
            f"expected={expected_scoring_result_sha256}, actual={recorded_scoring_hash}"
        )
    current_scoring_hash = compute_scoring_report_sha256(scoring_path)
    if current_scoring_hash != expected_scoring_result_sha256:
        raise PromptFinalizationError(
            "scoring result hash 已变化，拒绝固化："
            f"expected={expected_scoring_result_sha256}, actual={current_scoring_hash}"
        )

    return {
        **binding,
        "scoring_report": str(scoring_path),
        "parse_success_rate": parse_success_rate,
        "gate_passed": gate_passed,
    }


def _resolve_candidate_dir(*, root: Path, candidate_name: str, manifest_entry: dict[str, Any]) -> Path:
    files_payload = manifest_entry.get("files") if isinstance(manifest_entry.get("files"), dict) else {}

    for raw_path in (files_payload.get("prompt"), files_payload.get("readme"), manifest_entry.get("prompt_file")):
        if not raw_path:
            continue
        resolved = _resolve_path(raw_path, root=root)
        if resolved.exists():
            return resolved.parent

    fallback = (root / "prompts" / "candidates" / candidate_name).resolve()
    if fallback.exists():
        return fallback
    raise PromptFinalizationError(f"无法定位候选 Prompt 目录：{candidate_name}")


def _iter_candidate_files(candidate_dir: Path) -> Iterable[Path]:
    for path in sorted(candidate_dir.iterdir(), key=lambda p: p.name):
        if path.is_file():
            yield path


def _copy_candidate_files(*, candidate_dir: Path, final_candidate_dir: Path) -> list[str]:
    final_candidate_dir.mkdir(parents=True, exist_ok=True)
    copied: list[str] = []

    for source in _iter_candidate_files(candidate_dir):
        destination = final_candidate_dir / source.name
        shutil.copy2(source, destination)
        copied.append(destination.name)

    if "extract_graph.txt" not in copied:
        prompt_txt = final_candidate_dir / "prompt.txt"
        if not prompt_txt.exists():
            raise PromptFinalizationError(
                f"候选目录缺少 extract_graph.txt 与 prompt.txt，无法激活：{candidate_dir}"
            )
        extract_graph = final_candidate_dir / "extract_graph.txt"
        shutil.copy2(prompt_txt, extract_graph)
        copied.append(extract_graph.name)

    return sorted(set(copied))


def _build_active_prompt_paths(*, root: Path, final_candidate_dir: Path) -> dict[str, str]:
    active_paths = {
        "extract_graph.txt": _as_repo_relative(final_candidate_dir / "extract_graph.txt", root=root),
        "summarize_descriptions.txt": DEFAULT_PROMPT_ENV_PATHS["GRAPHRAG_SUMMARIZE_DESCRIPTIONS_PROMPT_FILE"],
        "extract_claims.txt": DEFAULT_PROMPT_ENV_PATHS["GRAPHRAG_CLAIM_EXTRACTION_PROMPT_FILE"],
        "community_report_graph.txt": DEFAULT_PROMPT_ENV_PATHS["GRAPHRAG_COMMUNITY_REPORT_GRAPH_PROMPT_FILE"],
        "community_report_text.txt": DEFAULT_PROMPT_ENV_PATHS["GRAPHRAG_COMMUNITY_REPORT_TEXT_PROMPT_FILE"],
    }

    for prompt_name in (
        "summarize_descriptions.txt",
        "extract_claims.txt",
        "community_report_graph.txt",
        "community_report_text.txt",
    ):
        candidate_file = final_candidate_dir / prompt_name
        if candidate_file.exists():
            active_paths[prompt_name] = _as_repo_relative(candidate_file, root=root)

    return active_paths


def _update_env_file(*, env_file: Path, assignments: dict[str, str]) -> None:
    existing_lines = env_file.read_text(encoding="utf-8").splitlines() if env_file.exists() else []
    updated_lines: list[str] = []
    seen_keys: set[str] = set()

    for line in existing_lines:
        stripped = line.strip()
        if not stripped or stripped.startswith("#") or "=" not in line:
            updated_lines.append(line)
            continue

        key, _old_value = line.split("=", 1)
        if key in assignments:
            updated_lines.append(f"{key}={assignments[key]}")
            seen_keys.add(key)
        else:
            updated_lines.append(line)

    for key, value in assignments.items():
        if key not in seen_keys:
            updated_lines.append(f"{key}={value}")

    env_file.write_text("\n".join(updated_lines).rstrip() + "\n", encoding="utf-8")


def finalize_candidate_prompt(
    *,
    root: Path,
    candidate_name: str,
    manifest_path: str | Path | None = None,
    final_dir: str | Path | None = None,
    env_file: str | Path | None = None,
    scoring_run_id: str | None = None,
    scoring_report: str | Path | None = None,
    expected_manifest_sha256: str | None = None,
    expected_scoring_result_sha256: str | None = None,
    min_parse_success_rate: float = 0.8,
) -> dict[str, Any]:
    """把候选 Prompt 固化为当前活动 Prompt。"""

    project_root = root.resolve()
    manifest = _resolve_path(manifest_path or DEFAULT_MANIFEST_SUBPATH, root=project_root)
    final_root = _resolve_path(final_dir or DEFAULT_FINAL_SUBPATH, root=project_root)
    env_path = _resolve_path(env_file or DEFAULT_ENV_SUBPATH, root=project_root)

    manifest_entry = _load_manifest_candidate(manifest_path=manifest, candidate_name=candidate_name)
    scoring_binding = _validate_scoring_binding(
        root=project_root,
        candidate_name=candidate_name,
        manifest_path=manifest,
        scoring_run_id=scoring_run_id,
        scoring_report=scoring_report,
        expected_manifest_sha256=expected_manifest_sha256,
        expected_scoring_result_sha256=expected_scoring_result_sha256,
        min_parse_success_rate=min_parse_success_rate,
    )
    candidate_dir = _resolve_candidate_dir(
        root=project_root,
        candidate_name=candidate_name,
        manifest_entry=manifest_entry,
    )

    final_candidate_dir = (final_root / candidate_name).resolve()
    copied_files = _copy_candidate_files(candidate_dir=candidate_dir, final_candidate_dir=final_candidate_dir)
    active_prompt_paths = _build_active_prompt_paths(root=project_root, final_candidate_dir=final_candidate_dir)

    env_assignments = dict(DEFAULT_PROMPT_ENV_PATHS)
    env_assignments.update(
        {
            ACTIVE_PROMPT_ENV_MAP[prompt_name]: prompt_path
            for prompt_name, prompt_path in active_prompt_paths.items()
        }
    )
    env_assignments["GRAPHRAG_ACTIVE_PROMPT_CANDIDATE"] = candidate_name

    _update_env_file(env_file=env_path, assignments=env_assignments)

    report = {
        "task": "prompt_candidate_finalization",
        "status": "success",
        "finalized_at": _now_iso(),
        "candidate_name": candidate_name,
        "manifest_path": str(manifest),
        "candidate_dir": str(candidate_dir),
        "final_dir": str(final_candidate_dir),
        "env_file": str(env_path),
        "copied_files": copied_files,
        "active_prompt_paths": active_prompt_paths,
        "scoring_binding": scoring_binding,
    }
    _write_json(final_root / ACTIVE_PROMPT_RECORD, report)
    return report


def build_parser() -> argparse.ArgumentParser:
    parser = argparse.ArgumentParser(description="把候选 Prompt 固化为当前活动 Prompt。")
    parser.add_argument(
        "--candidate",
        required=True,
        help="候选 Prompt 名称，例如 auto_tuned。",
    )
    parser.add_argument(
        "--root",
        default=".",
        help="GraphRAG 项目根目录，默认当前目录。",
    )
    parser.add_argument(
        "--manifest",
        default=str(DEFAULT_MANIFEST_SUBPATH),
        help="候选 manifest 路径，默认 prompts/candidates/manifest.json。",
    )
    parser.add_argument(
        "--final-dir",
        default=str(DEFAULT_FINAL_SUBPATH),
        help="最终 Prompt 归档目录，默认 prompts/final。",
    )
    parser.add_argument(
        "--env-file",
        default=str(DEFAULT_ENV_SUBPATH),
        help=".env 路径，默认 .env。",
    )
    parser.add_argument("--scoring-run-id", help="允许固化的 scoring run_id")
    parser.add_argument("--scoring-report", help="top_candidates.json 路径")
    parser.add_argument("--expected-manifest-sha256", help="评分时记录的 manifest sha256")
    parser.add_argument("--expected-scoring-result-sha256", help="评分结果 sha256")
    parser.add_argument(
        "--min-parse-success-rate",
        type=float,
        default=0.8,
        help="固化所需 parse_success_rate 门槛，默认 0.8",
    )
    return parser


def main(argv: list[str] | None = None) -> int:
    parser = build_parser()
    args = parser.parse_args(argv)

    report = finalize_candidate_prompt(
        root=Path(args.root),
        candidate_name=args.candidate,
        manifest_path=args.manifest,
        final_dir=args.final_dir,
        env_file=args.env_file,
        scoring_run_id=args.scoring_run_id,
        scoring_report=args.scoring_report,
        expected_manifest_sha256=args.expected_manifest_sha256,
        expected_scoring_result_sha256=args.expected_scoring_result_sha256,
        min_parse_success_rate=args.min_parse_success_rate,
    )
    print(json.dumps(report, ensure_ascii=False, indent=2))
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
