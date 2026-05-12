#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""评测报告写盘工具。

消费 rank_candidates() 的返回结构，不重复计算。写出三种产物：
- extraction_compare.csv：每候选一行，列按固定顺序。
- extraction_compare.md：表格 + Top-K 区块 + 权重说明。
- top_candidates.json：top-k 结构化结果，带权重与输入路径回溯。
"""

from __future__ import annotations

import csv
import hashlib
import json
from pathlib import Path
from typing import Any

CSV_COLUMNS: tuple[str, ...] = (
    "rank",
    "candidate",
    "composite_score",
    "composite_hard",
    "composite_soft",
    "gate_passed",
    "parse_success_rate",
    "schema_hit_rate",
    "entity_type_valid_rate",
    "relation_type_valid_rate",
    "endpoint_valid_rate",
    "endpoint_total_count",
    "endpoint_invalid_count",
    "duplicate_entity_rate",
    "noise_entity_rate",
    "output_stability",
    "audit_entity_recall",
    "audit_entity_precision",
    "audit_relation_recall",
    "faithfulness_error_rate",
    "parse_error_count",
    "llm_error_count",
    "strict_output_retry_count",
    "output_leak_flag_count",
    "sample_count",
    "success_count",
)


def _format_value(value: Any) -> str:
    if value is None:
        return ""
    if isinstance(value, float):
        return f"{value:.4f}"
    return str(value)


def write_extraction_compare_csv(path: Path, ranked: list[dict]) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    with path.open("w", encoding="utf-8", newline="") as handle:
        writer = csv.writer(handle)
        writer.writerow(list(CSV_COLUMNS))
        for row in ranked:
            writer.writerow([_format_value(row.get(col)) for col in CSV_COLUMNS])


def write_extraction_compare_markdown(
    path: Path,
    ranked: list[dict],
    *,
    weights: dict[str, float],
    top_k: int,
) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    lines: list[str] = []
    lines.append("# 候选 Prompt 规则化评测对比报告")
    lines.append("")
    lines.append("## 指标对比")
    lines.append("")
    lines.append("| " + " | ".join(CSV_COLUMNS) + " |")
    lines.append("|" + "|".join(["---"] * len(CSV_COLUMNS)) + "|")
    for row in ranked:
        lines.append(
            "| " + " | ".join(_format_value(row.get(col)) for col in CSV_COLUMNS) + " |"
        )
    lines.append("")
    lines.append(f"## Top Candidates (k={top_k})")
    lines.append("")
    for row in ranked[:top_k]:
        gate = "pass" if row.get("gate_passed") else "fail"
        lines.append(
            f"- **{row['candidate']}**（rank={row.get('rank')}, "
            f"composite_score={_format_value(row.get('composite_score'))}, "
            f"gate={gate}, "
            f"hard={_format_value(row.get('composite_hard'))}, "
            f"soft={_format_value(row.get('composite_soft'))}）"
        )
    lines.append("")
    lines.append("## 权重")
    lines.append("")
    for key, weight in weights.items():
        lines.append(f"- `{key}`：{weight}")
    lines.append("")
    path.write_text("\n".join(lines), encoding="utf-8")


def write_top_candidates_json(
    path: Path,
    *,
    ranked: list[dict],
    k: int,
    weights: dict[str, float],
    inputs: dict[str, Any],
    artifact_binding: dict[str, Any] | None = None,
) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    binding = dict(artifact_binding or {})
    binding.pop("scoring_result_sha256", None)
    payload = {
        "task": "extraction_score_top_candidates",
        "k": k,
        "weights": weights,
        "inputs": inputs,
        "artifact_binding": binding,
        "top_candidates": _with_candidate_bindings(list(ranked[:k]), binding),
        "all_candidates_ranked": _with_candidate_bindings(list(ranked), binding),
    }
    scoring_hash = _payload_sha256(payload)
    payload["artifact_binding"]["scoring_result_sha256"] = scoring_hash
    for row in payload["top_candidates"]:
        row["artifact_binding"]["scoring_result_sha256"] = scoring_hash
    for row in payload["all_candidates_ranked"]:
        row["artifact_binding"]["scoring_result_sha256"] = scoring_hash
    path.write_text(json.dumps(payload, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")


def _payload_sha256(payload: dict[str, Any]) -> str:
    canonical = json.dumps(
        payload,
        ensure_ascii=False,
        sort_keys=True,
        separators=(",", ":"),
    ).encode("utf-8")
    return hashlib.sha256(canonical).hexdigest()


def _with_candidate_bindings(rows: list[dict], binding: dict[str, Any]) -> list[dict]:
    result: list[dict] = []
    for row in rows:
        copied = dict(row)
        copied["artifact_binding"] = {
            **binding,
            "candidate_id": str(row.get("candidate") or ""),
        }
        result.append(copied)
    return result


def write_run_meta(
    path: Path,
    *,
    run_id: str,
    timestamp: str,
    git_sha: str | None,
    inputs: dict[str, Any],
    weights: dict[str, float],
    top_k: int,
    top_candidates: list[str],
    total_candidates: int,
    artifact_binding: dict[str, Any] | None = None,
) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    payload = {
        "task": "extraction_scoring_run",
        "run_id": run_id,
        "timestamp": timestamp,
        "git_sha": git_sha,
        "top_k": top_k,
        "total_candidates": total_candidates,
        "top_candidates": list(top_candidates),
        "weights": weights,
        "inputs": inputs,
        "artifact_binding": artifact_binding or {},
    }
    path.write_text(json.dumps(payload, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")


HISTORY_COLUMNS: tuple[str, ...] = ("run_id", "timestamp", *CSV_COLUMNS)


def append_history_csv(
    path: Path,
    *,
    run_id: str,
    timestamp: str,
    ranked: list[dict],
) -> None:
    """追加 run 明细到 history.csv。

    表头与 `HISTORY_COLUMNS` 不一致时做一次性迁移：按列名映射把旧行补到新 schema
    （老 schema 没有的列填空字符串），再写回新表头 + 旧行 + 新行。
    """
    path.parent.mkdir(parents=True, exist_ok=True)
    new_rows: list[list[str]] = []
    for row in ranked:
        record = [run_id, timestamp] + [_format_value(row.get(col)) for col in CSV_COLUMNS]
        new_rows.append(record)

    current_columns = list(HISTORY_COLUMNS)

    existing_header: list[str] = []
    existing_rows: list[list[str]] = []
    if path.exists() and path.stat().st_size > 0:
        with path.open(encoding="utf-8", newline="") as handle:
            reader = csv.reader(handle)
            try:
                existing_header = next(reader)
            except StopIteration:
                existing_header = []
            existing_rows = list(reader)

    if not existing_header:
        with path.open("w", encoding="utf-8", newline="") as handle:
            writer = csv.writer(handle)
            writer.writerow(current_columns)
            for record in new_rows:
                writer.writerow(record)
        return

    migrated_rows: list[list[str]] = []
    for row in existing_rows:
        if existing_header == current_columns and len(row) == len(current_columns):
            mapping = dict(zip(current_columns, row))
        else:
            mapping = dict(zip(existing_header, row))
        migrated = [mapping.get(col, "") for col in current_columns]
        if migrated and migrated[0] == run_id:
            continue
        migrated_rows.append(migrated)

    with path.open("w", encoding="utf-8", newline="") as handle:
        writer = csv.writer(handle)
        writer.writerow(current_columns)
        for record in migrated_rows + new_rows:
            writer.writerow(record)


def write_latest_pointer(
    path: Path,
    *,
    run_id: str,
    run_dir: str,
) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    payload = {"run_id": run_id, "run_dir": run_dir}
    path.write_text(json.dumps(payload, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
