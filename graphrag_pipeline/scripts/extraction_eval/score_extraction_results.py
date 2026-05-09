#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""规则化自动评测 CLI（步骤 8）。

流程：
1. 发现 results/extraction_eval/*.json。
2. 载入 entity/relation schema 与 audit 集（可选）。
3. 对每个候选：parse 结果 → 聚合 10 项指标 → composite_score。
4. 排序 + top-k → 写 per-run 布局。

输出布局：
  results/reports/extraction_scoring/
    runs/<run_id>/
      extraction_compare.csv
      extraction_compare.md
      top_candidates.json
      run_meta.json
    history.csv        # append-only，跨 run 明细
    latest.json        # 指向最新 run_id
"""

from __future__ import annotations

import argparse
import datetime as dt
import hashlib
import json
import re
import subprocess
from pathlib import Path
from typing import Any, Sequence

from .extraction_schema import (
    ExtractionEntity,
    ExtractionRelationship,
    StructuredExtractionResult,
)
from .prompt_loader import is_fallback_auto_tuned_entry
from .scoring_audit import (
    compute_audit_entity_precision,
    compute_audit_entity_recall,
    compute_audit_relation_recall,
    load_audit_index,
    summarize_manual_gold_seed_coverage,
)
from .scoring_metrics import (
    DEFAULT_WEIGHTS,
    aggregate_candidate_metrics,
    analyze_endpoint_validity,
    compute_composite_score,
    compute_output_stability,
    rank_candidates,
    select_top_k,
)
from .scoring_report import (
    append_history_csv,
    write_extraction_compare_csv,
    write_extraction_compare_markdown,
    write_latest_pointer,
    write_run_meta,
    write_top_candidates_json,
)


PROJECT_ROOT = Path(__file__).resolve().parents[2]

DEFAULT_EVAL_DIR = "results/extraction_eval"
DEFAULT_ENTITY_SCHEMA = "config/schema/entity_types.json"
DEFAULT_RELATION_SCHEMA = "config/schema/relation_types.json"
DEFAULT_AUDIT_PATH = "data/eval/audit_extraction_set.json"
DEFAULT_REPORTS_DIR = "results/reports"
SCORING_SUBDIR = "extraction_scoring"
FEWSHOT_SOURCE_NOTE_RE = re.compile(r"few-?shot\s*来源样本\s*[：:]\s*(.+)", re.IGNORECASE)


def _resolve(path: str | Path | None, *, root: Path, default: str | None) -> Path | None:
    target = path if path is not None else default
    if target is None:
        return None
    candidate = Path(target)
    return candidate if candidate.is_absolute() else (root / candidate).resolve()


def _load_eval_file(path: Path) -> tuple[str, list[StructuredExtractionResult]]:
    payload = json.loads(path.read_text(encoding="utf-8"))
    candidate = str(payload.get("candidate") or path.stem).strip()
    raw_results = payload.get("results") or []
    results: list[StructuredExtractionResult] = []
    for raw in raw_results:
        results.append(
            StructuredExtractionResult(
                sample_id=str(raw.get("sample_id") or ""),
                candidate=candidate,
                status=str(raw.get("status") or "parse_error"),
                entities=[ExtractionEntity(**e) for e in raw.get("entities") or []],
                relationships=[
                    ExtractionRelationship(**r) for r in raw.get("relationships") or []
                ],
                raw_output=str(raw.get("raw_output") or ""),
                error=raw.get("error"),
                parser_error_code=raw.get("parser_error_code"),
                llm_debug=raw.get("llm_debug"),
            )
        )
    return candidate, results


def _candidate_should_be_skipped(eval_file: Path, *, root: Path, include_fallback_auto_tuned: bool) -> str | None:
    if include_fallback_auto_tuned:
        return None
    payload = json.loads(eval_file.read_text(encoding="utf-8"))
    candidate = str(payload.get("candidate") or eval_file.stem).strip()
    if candidate != "auto_tuned":
        return None
    manifest_file = _resolve_manifest_from_eval_payload(payload, root=root)
    if manifest_file is None or not manifest_file.exists():
        return None
    manifest_payload = json.loads(manifest_file.read_text(encoding="utf-8"))
    raw_candidates = manifest_payload.get("candidates") or []
    for entry in raw_candidates:
        if isinstance(entry, dict) and is_fallback_auto_tuned_entry(entry):
            return candidate
    return None


def _resolve_manifest_from_eval_payload(payload: dict[str, Any], *, root: Path) -> Path | None:
    raw_manifest = str(payload.get("manifest_file") or "").strip()
    if not raw_manifest:
        return None
    manifest_path = Path(raw_manifest)
    if manifest_path.is_absolute():
        return manifest_path.resolve()
    return (root / manifest_path).resolve()


def _audit_gold_available(audit_index: dict[str, Any]) -> bool:
    return any(entry.gold_entities or entry.gold_relations for entry in audit_index.values())


def _sha256_file(path: Path) -> str:
    digest = hashlib.sha256()
    with path.open("rb") as handle:
        for chunk in iter(lambda: handle.read(1024 * 1024), b""):
            digest.update(chunk)
    return digest.hexdigest()


def _generate_run_id(now: dt.datetime | None = None) -> str:
    """生成 ISO 紧凑时间戳作为 run_id（便于排序、人读）。"""

    current = now or dt.datetime.now()
    return current.strftime("%Y-%m-%dT%H%M%S")


def _detect_git_sha(root: Path) -> str | None:
    """在 root 执行 git rev-parse；非仓库或命令缺失时返回 None。"""

    try:
        completed = subprocess.run(
            ["git", "rev-parse", "HEAD"],
            cwd=root,
            capture_output=True,
            text=True,
            timeout=3,
            check=False,
        )
    except (FileNotFoundError, subprocess.SubprocessError):
        return None
    if completed.returncode != 0:
        return None
    sha = completed.stdout.strip()
    return sha or None


def _build_artifact_binding(*, root: Path, run_id: str, eval_files: Sequence[Path]) -> dict[str, Any]:
    manifest_path = _resolve_manifest_from_eval_files(root=root, eval_files=eval_files)

    return {
        "run_id": run_id,
        "manifest_path": str(manifest_path) if manifest_path else None,
        "manifest_sha256": _sha256_file(manifest_path) if manifest_path else None,
        "eval_file_sha256s": [
            {
                "path": str(path),
                "sha256": _sha256_file(path),
            }
            for path in eval_files
        ],
    }


def _resolve_manifest_from_eval_files(*, root: Path, eval_files: Sequence[Path]) -> Path | None:
    manifest_path: Path | None = None
    for eval_file in eval_files:
        try:
            payload = json.loads(eval_file.read_text(encoding="utf-8"))
        except json.JSONDecodeError:
            continue
        resolved = _resolve_manifest_from_eval_payload(payload, root=root)
        if resolved and resolved.exists():
            manifest_path = resolved
            break

    default_manifest = (root / "prompts" / "candidates" / "manifest.json").resolve()
    if manifest_path is None and default_manifest.exists():
        manifest_path = default_manifest

    return manifest_path


def _dedupe_preserve_order(values: Sequence[str]) -> list[str]:
    seen: set[str] = set()
    result: list[str] = []
    for value in values:
        text = str(value or "").strip()
        if not text or text in seen:
            continue
        seen.add(text)
        result.append(text)
    return result


def _split_sample_id_text(value: Any) -> list[str]:
    if value is None:
        return []
    if isinstance(value, str):
        return [
            token.strip().strip("`'\"，,。.;；")
            for token in re.split(r"[,，、\s]+", value)
            if token.strip().strip("`'\"，,。.;；")
        ]
    if isinstance(value, (list, tuple, set)):
        out: list[str] = []
        for item in value:
            if isinstance(item, dict):
                out.extend(_collect_source_sample_ids_from_mapping(item))
            else:
                out.extend(_split_sample_id_text(item))
        return out
    return _split_sample_id_text(str(value))


def _collect_source_sample_ids_from_mapping(payload: dict[str, Any]) -> list[str]:
    out: list[str] = []
    direct_keys = (
        "source_sample_id",
        "source_sample_ids",
        "fewshot_source_sample_ids",
        "fewshot_source_ids",
        "selected_source_sample_ids",
        "selected_sample_ids",
    )
    for key in direct_keys:
        if key in payload:
            out.extend(_split_sample_id_text(payload.get(key)))

    coverage = payload.get("fewshot_coverage")
    if isinstance(coverage, dict):
        for key in direct_keys:
            if key in coverage:
                out.extend(_split_sample_id_text(coverage.get(key)))
        records = coverage.get("selected_records") or coverage.get("selected_samples")
        out.extend(_split_sample_id_text(records))

    notes = payload.get("notes") or []
    if isinstance(notes, str):
        notes = [notes]
    for note in notes:
        match = FEWSHOT_SOURCE_NOTE_RE.search(str(note))
        if match:
            out.extend(_split_sample_id_text(match.group(1)))
    return out


def _load_fewshot_source_sample_ids(manifest_path: Path | None) -> list[str]:
    if manifest_path is None or not manifest_path.exists():
        return []
    try:
        payload = json.loads(manifest_path.read_text(encoding="utf-8"))
    except json.JSONDecodeError:
        return []

    out: list[str] = []
    raw_candidates = payload.get("candidates") or []
    for entry in raw_candidates:
        if not isinstance(entry, dict):
            continue
        candidate_name = str(entry.get("candidate_name") or "").strip()
        source_type = str(entry.get("source_type") or "").strip()
        is_fewshot_candidate = candidate_name in {
            "schema_fewshot",
            "schema_fewshot_distilled",
        } or source_type in {
            "schema_fewshot",
            "schema_fewshot_distilled",
        }
        if not is_fewshot_candidate:
            continue
        out.extend(_collect_source_sample_ids_from_mapping(entry))
    return _dedupe_preserve_order(out)


def _normalize_endpoint_cell(value: Any) -> str | None:
    if value is None:
        return None
    text = str(value).strip()
    return text or None


def _build_endpoint_error_rows(ranked: Sequence[dict[str, Any]]) -> list[dict[str, Any]]:
    rows: list[dict[str, Any]] = []
    for candidate_metrics in ranked:
        candidate = str(candidate_metrics.get("candidate") or "").strip()
        for raw_combo in candidate_metrics.get("invalid_endpoint_combinations") or []:
            if not isinstance(raw_combo, dict):
                continue
            count = int(raw_combo.get("count") or 0)
            if count <= 0:
                continue
            rows.append(
                {
                    "candidate": candidate,
                    "relation_type": str(raw_combo.get("relation_type") or "").strip(),
                    "source_type": _normalize_endpoint_cell(raw_combo.get("source_type")),
                    "target_type": _normalize_endpoint_cell(raw_combo.get("target_type")),
                    "count": count,
                    "suggested_action": str(raw_combo.get("suggested_action") or "").strip(),
                    "reason": str(raw_combo.get("reason") or "").strip(),
                    "endpoint_total_count": int(candidate_metrics.get("endpoint_total_count") or 0),
                    "endpoint_invalid_count": int(candidate_metrics.get("endpoint_invalid_count") or 0),
                    "endpoint_valid_rate": candidate_metrics.get("endpoint_valid_rate"),
                    "examples": list(raw_combo.get("examples") or []),
                }
            )
    return sorted(
        rows,
        key=lambda row: (
            -int(row["count"]),
            row["candidate"],
            row["relation_type"],
            str(row["source_type"]),
            str(row["target_type"]),
            row["reason"],
        ),
    )


def _build_endpoint_candidate_totals(ranked: Sequence[dict[str, Any]]) -> list[dict[str, Any]]:
    return [
        {
            "candidate": str(row.get("candidate") or "").strip(),
            "endpoint_total_count": int(row.get("endpoint_total_count") or 0),
            "endpoint_invalid_count": int(row.get("endpoint_invalid_count") or 0),
            "endpoint_valid_rate": row.get("endpoint_valid_rate"),
        }
        for row in ranked
    ]


def _write_endpoint_error_summary_json(
    path: Path,
    *,
    run_id: str,
    ranked: Sequence[dict[str, Any]],
    rows: Sequence[dict[str, Any]],
) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    payload = {
        "task": "endpoint_error_summary",
        "run_id": run_id,
        "row_count": len(rows),
        "total_error_count": sum(int(row["count"]) for row in rows),
        "candidate_endpoint_totals": _build_endpoint_candidate_totals(ranked),
        "rows": list(rows),
    }
    path.write_text(json.dumps(payload, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")


def _format_endpoint_markdown_cell(value: Any) -> str:
    text = "" if value is None else str(value).strip()
    if not text:
        return "-"
    return text.replace("|", "\\|")


def _write_endpoint_error_summary_markdown(
    path: Path,
    *,
    run_id: str,
    rows: Sequence[dict[str, Any]],
) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    lines = [
        "# 关系端点错误摘要",
        "",
        f"- `run_id`：{run_id}",
        f"- `total_error_count`：{sum(int(row['count']) for row in rows)}",
        "",
    ]
    if not rows:
        lines.append("本轮未发现关系端点错误。")
    else:
        columns = (
            "candidate",
            "relation_type",
            "source_type",
            "target_type",
            "count",
            "suggested_action",
            "reason",
        )
        lines.append("| " + " | ".join(columns) + " |")
        lines.append("|" + "|".join(["---"] * len(columns)) + "|")
        for row in rows:
            lines.append(
                "| "
                + " | ".join(_format_endpoint_markdown_cell(row.get(col)) for col in columns)
                + " |"
            )
    lines.append("")
    path.write_text("\n".join(lines), encoding="utf-8")


def _append_endpoint_summary_links_to_compare(
    path: Path,
    *,
    endpoint_summary_paths: dict[str, str],
) -> None:
    with path.open("a", encoding="utf-8") as handle:
        handle.write("\n## 端点诊断产物\n\n")
        handle.write(f"- JSON：`{endpoint_summary_paths['json']}`\n")
        handle.write(f"- Markdown：`{endpoint_summary_paths['markdown']}`\n")


def _append_leakage_diagnostics_links_to_compare(
    path: Path,
    *,
    leakage_diagnostics_paths: dict[str, str],
) -> None:
    with path.open("a", encoding="utf-8") as handle:
        handle.write("\n## 泄漏感知诊断产物\n\n")
        handle.write(f"- JSON：`{leakage_diagnostics_paths['json']}`\n")
        handle.write(f"- Markdown：`{leakage_diagnostics_paths['markdown']}`\n")


def _mean_success_count(results: Sequence[StructuredExtractionResult], attr: str) -> float:
    success_items = [item for item in results if item.status == "success"]
    if not success_items:
        return 0.0
    if attr == "entities":
        total = sum(len(item.entities) for item in success_items)
    else:
        total = sum(len(item.relationships) for item in success_items)
    return total / len(success_items)


def _coerce_numeric_usage(usage: Any) -> dict[str, float]:
    if not isinstance(usage, dict):
        return {}
    out: dict[str, float] = {}
    for key, value in usage.items():
        if isinstance(value, bool):
            continue
        try:
            out[str(key)] = float(value)
        except (TypeError, ValueError):
            continue
    return out


def _summarize_token_usage(results: Sequence[StructuredExtractionResult]) -> dict[str, Any]:
    usages: list[dict[str, float]] = []
    for item in results:
        debug = item.llm_debug if isinstance(item.llm_debug, dict) else {}
        usage = _coerce_numeric_usage(debug.get("usage"))
        if usage:
            usages.append(usage)

    keys = sorted({key for usage in usages for key in usage})
    totals = {
        key: sum(usage.get(key, 0.0) for usage in usages)
        for key in keys
    }
    means = {
        key: (totals[key] / len(usages) if usages else 0.0)
        for key in keys
    }
    return {
        "sample_count_with_usage": len(usages),
        "total": totals,
        "mean": means,
    }


def _build_leakage_group_metrics(
    results: Sequence[StructuredExtractionResult],
    *,
    relation_schema: dict[str, Any],
    audit_index: dict[str, Any],
    audit_metrics_enabled: bool,
    group_available: bool,
) -> dict[str, Any]:
    sample_ids = [item.sample_id for item in results]
    success_count = sum(1 for item in results if item.status == "success")
    if not group_available:
        return {
            "group_available": False,
            "sample_count": 0,
            "success_count": 0,
            "sample_ids": [],
            "endpoint_valid_rate": None,
            "endpoint_total_count": 0,
            "endpoint_invalid_count": 0,
            "audit_entity_recall": None,
            "audit_entity_precision": None,
            "audit_relation_recall": None,
            "output_stability": None,
            "average_entity_count": None,
            "average_relation_count": None,
            "token_usage": _summarize_token_usage([]),
        }

    endpoint_analysis = analyze_endpoint_validity(results, relation_schema)
    audit_ent = (
        compute_audit_entity_recall(results, audit_index) if audit_metrics_enabled else None
    )
    audit_ent_prec = (
        compute_audit_entity_precision(results, audit_index) if audit_metrics_enabled else None
    )
    audit_rel = (
        compute_audit_relation_recall(results, audit_index) if audit_metrics_enabled else None
    )
    return {
        "group_available": True,
        "sample_count": len(results),
        "success_count": success_count,
        "sample_ids": sample_ids,
        "endpoint_valid_rate": endpoint_analysis["valid_rate"],
        "endpoint_total_count": endpoint_analysis["total"],
        "endpoint_invalid_count": endpoint_analysis["invalid_count"],
        "audit_entity_recall": audit_ent,
        "audit_entity_precision": audit_ent_prec,
        "audit_relation_recall": audit_rel,
        "output_stability": compute_output_stability(results) if results else None,
        "average_entity_count": _mean_success_count(results, "entities"),
        "average_relation_count": _mean_success_count(results, "relationships"),
        "token_usage": _summarize_token_usage(results),
    }


def _build_leakage_diagnostics(
    *,
    run_id: str,
    manifest_path: Path | None,
    fewshot_source_sample_ids: Sequence[str],
    results_by_candidate: dict[str, Sequence[StructuredExtractionResult]],
    relation_schema: dict[str, Any],
    audit_index: dict[str, Any],
    audit_metrics_enabled: bool,
) -> dict[str, Any]:
    source_ids = set(fewshot_source_sample_ids)
    partition_available = bool(source_ids)
    candidates: dict[str, Any] = {}
    for candidate in sorted(results_by_candidate):
        results = list(results_by_candidate[candidate])
        overlap = [item for item in results if item.sample_id in source_ids]
        holdout = [item for item in results if item.sample_id not in source_ids]
        candidates[candidate] = {
            "candidate": candidate,
            "groups": {
                "all": _build_leakage_group_metrics(
                    results,
                    relation_schema=relation_schema,
                    audit_index=audit_index,
                    audit_metrics_enabled=audit_metrics_enabled,
                    group_available=True,
                ),
                "fewshot_overlap": _build_leakage_group_metrics(
                    overlap,
                    relation_schema=relation_schema,
                    audit_index=audit_index,
                    audit_metrics_enabled=audit_metrics_enabled,
                    group_available=partition_available,
                ),
                "holdout": _build_leakage_group_metrics(
                    holdout,
                    relation_schema=relation_schema,
                    audit_index=audit_index,
                    audit_metrics_enabled=audit_metrics_enabled,
                    group_available=partition_available,
                ),
            },
        }

    return {
        "task": "leakage_diagnostics",
        "run_id": run_id,
        "manifest_path": str(manifest_path) if manifest_path else None,
        "fewshot_source_sample_ids": list(fewshot_source_sample_ids),
        "fewshot_source_sample_count": len(fewshot_source_sample_ids),
        "partition_available": partition_available,
        "audit_metrics_enabled": audit_metrics_enabled,
        "candidates": candidates,
    }


def _write_leakage_diagnostics_json(path: Path, payload: dict[str, Any]) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(json.dumps(payload, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")


def _write_leakage_diagnostics_markdown(path: Path, payload: dict[str, Any]) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    lines = [
        "# 泄漏感知评分诊断",
        "",
        f"- `run_id`：{payload['run_id']}",
        f"- `manifest_path`：`{payload.get('manifest_path') or ''}`",
        f"- `fewshot_source_sample_count`：{payload['fewshot_source_sample_count']}",
        f"- `partition_available`：{payload['partition_available']}",
        "",
        "## 分组指标",
        "",
    ]
    columns = (
        "candidate",
        "group",
        "sample_count",
        "endpoint_total_count",
        "endpoint_invalid_count",
        "endpoint_valid_rate",
        "audit_entity_recall",
        "audit_entity_precision",
        "audit_relation_recall",
        "output_stability",
        "average_entity_count",
        "average_relation_count",
        "usage_total_tokens",
        "usage_mean_total_tokens",
    )
    lines.append("| " + " | ".join(columns) + " |")
    lines.append("|" + "|".join(["---"] * len(columns)) + "|")
    for candidate, candidate_payload in sorted(payload["candidates"].items()):
        groups = candidate_payload.get("groups") or {}
        for group_name in ("all", "fewshot_overlap", "holdout"):
            group = groups.get(group_name) or {}
            token_usage = group.get("token_usage") or {}
            totals = token_usage.get("total") or {}
            means = token_usage.get("mean") or {}
            row = {
                "candidate": candidate,
                "group": group_name,
                "usage_total_tokens": totals.get("total_tokens"),
                "usage_mean_total_tokens": means.get("total_tokens"),
                **group,
            }
            lines.append(
                "| "
                + " | ".join(_format_endpoint_markdown_cell(_format_value_for_markdown(row.get(col))) for col in columns)
                + " |"
            )
    lines.append("")
    path.write_text("\n".join(lines), encoding="utf-8")


def _format_value_for_markdown(value: Any) -> Any:
    if isinstance(value, float):
        return f"{value:.4f}"
    return value


def score_extraction_results(
    *,
    root: Path,
    eval_dir: str | Path | None,
    entity_schema_path: str | Path | None,
    relation_schema_path: str | Path | None,
    audit_path: str | Path | None,
    weights: dict[str, float] | None,
    top_k: int,
    overwrite: bool,
    run_id: str | None = None,
    include_fallback_auto_tuned: bool = False,
) -> dict[str, Any]:
    root = Path(root).resolve()
    eval_root = _resolve(eval_dir, root=root, default=DEFAULT_EVAL_DIR)
    entity_schema_file = _resolve(
        entity_schema_path, root=root, default=DEFAULT_ENTITY_SCHEMA
    )
    relation_schema_file = _resolve(
        relation_schema_path, root=root, default=DEFAULT_RELATION_SCHEMA
    )
    audit_file = _resolve(audit_path, root=root, default=DEFAULT_AUDIT_PATH)

    if eval_root is None or not eval_root.exists():
        raise FileNotFoundError(f"未找到评测输入目录：{eval_root}")
    eval_files = sorted(p for p in eval_root.glob("*.json") if p.is_file())
    if not eval_files:
        raise ValueError(f"评测输入目录无 JSON 文件：{eval_root}")

    if entity_schema_file is None or not entity_schema_file.exists():
        raise FileNotFoundError(f"实体 schema 不存在：{entity_schema_file}")
    if relation_schema_file is None or not relation_schema_file.exists():
        raise FileNotFoundError(f"关系 schema 不存在：{relation_schema_file}")

    entity_payload = json.loads(entity_schema_file.read_text(encoding="utf-8"))
    relation_payload = json.loads(relation_schema_file.read_text(encoding="utf-8"))
    entity_type_names = list((entity_payload.get("entity_types") or {}).keys())
    relation_type_block = relation_payload.get("relation_types") or {}
    relation_type_names = list(relation_type_block.keys())

    audit_index = {}
    if audit_file and audit_file.exists():
        audit_index = load_audit_index(audit_file)
    audit_gold_available = _audit_gold_available(audit_index) if audit_index else None
    gold_seed_summary = (
        summarize_manual_gold_seed_coverage(audit_index) if audit_index else {
            "gold_seed_version": "manual_gold_seed_v1",
            "gold_seed_count": 0,
            "gold_seed_sample_ids": [],
            "gold_seed_relation_types": [],
            "gold_seed_required_relation_types": ["defined_by", "applied_in", "depends_on"],
            "gold_seed_missing_relation_types": ["defined_by", "applied_in", "depends_on"],
            "gold_seed_coverage_passed": False,
        }
    )
    audit_metrics_enabled = bool(audit_gold_available and gold_seed_summary["gold_seed_coverage_passed"])

    effective_weights = dict(weights or DEFAULT_WEIGHTS)

    metrics_by_candidate: dict[str, dict[str, Any]] = {}
    results_by_candidate: dict[str, list[StructuredExtractionResult]] = {}
    skipped_candidates: list[str] = []
    for eval_file in eval_files:
        skipped_candidate = _candidate_should_be_skipped(
            eval_file,
            root=root,
            include_fallback_auto_tuned=include_fallback_auto_tuned,
        )
        if skipped_candidate:
            skipped_candidates.append(skipped_candidate)
            continue
        candidate, results = _load_eval_file(eval_file)
        audit_ent = (
            compute_audit_entity_recall(results, audit_index) if audit_metrics_enabled else None
        )
        audit_ent_prec = (
            compute_audit_entity_precision(results, audit_index) if audit_metrics_enabled else None
        )
        audit_rel = (
            compute_audit_relation_recall(results, audit_index) if audit_metrics_enabled else None
        )
        metrics = aggregate_candidate_metrics(
            results,
            entity_type_names=entity_type_names,
            relation_type_names=relation_type_names,
            relation_schema=relation_type_block,
            audit_entity_recall=audit_ent,
            audit_relation_recall=audit_rel,
            audit_entity_precision=audit_ent_prec,
        )
        metrics["composite_score"] = compute_composite_score(metrics, effective_weights)
        metrics_by_candidate[candidate] = metrics
        results_by_candidate[candidate] = results

    if not metrics_by_candidate:
        raise ValueError("没有可评分的候选 Prompt；auto_tuned 当前可能只是 fallback 占位")

    ranked = rank_candidates(metrics_by_candidate)
    top = select_top_k(ranked, k=top_k)
    now = dt.datetime.now()
    effective_run_id = run_id or _generate_run_id(now)
    timestamp = now.replace(microsecond=0).isoformat()

    reports_dir = root / DEFAULT_REPORTS_DIR
    scoring_root = reports_dir / SCORING_SUBDIR
    run_dir = scoring_root / "runs" / effective_run_id
    if run_dir.exists() and not overwrite:
        raise FileExistsError(f"run 目录已存在，若要覆盖请传 --overwrite：{run_dir}")

    run_csv = run_dir / "extraction_compare.csv"
    run_md = run_dir / "extraction_compare.md"
    run_top = run_dir / "top_candidates.json"
    run_meta_path = run_dir / "run_meta.json"
    run_endpoint_json = run_dir / "endpoint_error_summary.json"
    run_endpoint_md = run_dir / "endpoint_error_summary.md"
    run_leakage_json = run_dir / "leakage_diagnostics.json"
    run_leakage_md = run_dir / "leakage_diagnostics.md"
    endpoint_summary_paths = {
        "json": str(run_endpoint_json),
        "markdown": str(run_endpoint_md),
    }
    leakage_diagnostics_paths = {
        "json": str(run_leakage_json),
        "markdown": str(run_leakage_md),
    }
    manifest_path = _resolve_manifest_from_eval_files(root=root, eval_files=eval_files)
    fewshot_source_sample_ids = _load_fewshot_source_sample_ids(manifest_path)

    inputs = {
        "eval_dir": str(eval_root),
        "entity_schema_path": str(entity_schema_file),
        "relation_schema_path": str(relation_schema_file),
        "audit_path": str(audit_file) if audit_file and audit_file.exists() else None,
        "audit_gold_available": audit_gold_available,
        "eval_files": [str(p) for p in eval_files],
        "skipped_candidates": skipped_candidates,
        "audit_metrics_enabled": audit_metrics_enabled,
        "endpoint_error_summary_paths": endpoint_summary_paths,
        "leakage_diagnostics_paths": leakage_diagnostics_paths,
        "fewshot_source_sample_ids": fewshot_source_sample_ids,
        **gold_seed_summary,
    }
    artifact_binding = _build_artifact_binding(
        root=root,
        run_id=effective_run_id,
        eval_files=eval_files,
    )
    endpoint_error_rows = _build_endpoint_error_rows(ranked)
    leakage_diagnostics = _build_leakage_diagnostics(
        run_id=effective_run_id,
        manifest_path=manifest_path,
        fewshot_source_sample_ids=fewshot_source_sample_ids,
        results_by_candidate=results_by_candidate,
        relation_schema=relation_type_block,
        audit_index=audit_index,
        audit_metrics_enabled=audit_metrics_enabled,
    )

    write_extraction_compare_csv(run_csv, ranked)
    write_extraction_compare_markdown(
        run_md, ranked, weights=effective_weights, top_k=top_k
    )
    _append_endpoint_summary_links_to_compare(
        run_md,
        endpoint_summary_paths=endpoint_summary_paths,
    )
    _append_leakage_diagnostics_links_to_compare(
        run_md,
        leakage_diagnostics_paths=leakage_diagnostics_paths,
    )
    _write_endpoint_error_summary_json(
        run_endpoint_json,
        run_id=effective_run_id,
        ranked=ranked,
        rows=endpoint_error_rows,
    )
    _write_endpoint_error_summary_markdown(
        run_endpoint_md,
        run_id=effective_run_id,
        rows=endpoint_error_rows,
    )
    _write_leakage_diagnostics_json(run_leakage_json, leakage_diagnostics)
    _write_leakage_diagnostics_markdown(run_leakage_md, leakage_diagnostics)
    write_top_candidates_json(
        run_top, ranked=ranked, k=top_k,
        weights=effective_weights, inputs=inputs,
        artifact_binding=artifact_binding,
    )
    top_payload = json.loads(run_top.read_text(encoding="utf-8"))
    artifact_binding = top_payload.get("artifact_binding") or artifact_binding
    write_run_meta(
        run_meta_path,
        run_id=effective_run_id,
        timestamp=timestamp,
        git_sha=_detect_git_sha(root),
        inputs=inputs,
        weights=effective_weights,
        top_k=top_k,
        top_candidates=[item["candidate"] for item in top],
        total_candidates=len(ranked),
        artifact_binding=artifact_binding,
    )

    history_path = scoring_root / "history.csv"
    append_history_csv(
        history_path, run_id=effective_run_id, timestamp=timestamp, ranked=ranked
    )
    latest_path = scoring_root / "latest.json"
    write_latest_pointer(
        latest_path,
        run_id=effective_run_id,
        run_dir=str(run_dir.relative_to(scoring_root)),
    )

    return {
        "status": "success",
        "root": str(root),
        "run_id": effective_run_id,
        "eval_files": [str(p) for p in eval_files],
        "total_candidates": len(ranked),
        "skipped_candidates": skipped_candidates,
        "top_k": top_k,
        "top_candidates": [item["candidate"] for item in top],
        "reports": {
            "run_dir": str(run_dir),
            "run_meta": str(run_meta_path),
            "history": str(history_path),
            "latest": str(latest_path),
            "csv": str(run_csv),
            "markdown": str(run_md),
            "top_candidates_json": str(run_top),
            "endpoint_error_summary_json": str(run_endpoint_json),
            "endpoint_error_summary_md": str(run_endpoint_md),
            "leakage_diagnostics_json": str(run_leakage_json),
            "leakage_diagnostics_md": str(run_leakage_md),
        },
        "artifact_binding": artifact_binding,
    }


def _build_parser() -> argparse.ArgumentParser:
    parser = argparse.ArgumentParser(
        description="对候选 Prompt 抽取结果做规则化自动评测并输出对比报告"
    )
    parser.add_argument("--eval-dir", help="候选结果目录，默认 results/extraction_eval")
    parser.add_argument("--entity-schema", help="实体 schema JSON 路径")
    parser.add_argument("--relation-schema", help="关系 schema JSON 路径")
    parser.add_argument("--audit", help="audit 集 JSON 路径；不传则软指标为 None")
    parser.add_argument("--weights", help="权重覆盖文件（JSON）")
    parser.add_argument("--top-k", type=int, default=2, help="保留前 K 名候选")
    parser.add_argument("--run-id", help="自定义 run_id，默认按本地时间生成 ISO 紧凑时间戳")
    parser.add_argument(
        "--include-fallback-auto-tuned",
        action="store_true",
        help="默认跳过 fallback_default_copy 的 auto_tuned；传入后才参与评分",
    )
    parser.add_argument("--overwrite", action="store_true", help="覆盖已有报告产物")
    return parser


def main(argv: Sequence[str] | None = None) -> int:
    args = _build_parser().parse_args(argv)
    weights = None
    if args.weights:
        weights = json.loads(Path(args.weights).read_text(encoding="utf-8"))
    summary = score_extraction_results(
        root=PROJECT_ROOT,
        eval_dir=args.eval_dir,
        entity_schema_path=args.entity_schema,
        relation_schema_path=args.relation_schema,
        audit_path=args.audit,
        weights=weights,
        top_k=args.top_k,
        overwrite=args.overwrite,
        run_id=args.run_id,
        include_fallback_auto_tuned=args.include_fallback_auto_tuned,
    )
    print(json.dumps(summary, ensure_ascii=False, indent=2))
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
