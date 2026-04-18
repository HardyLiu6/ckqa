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
import json
from pathlib import Path
from typing import Any

CSV_COLUMNS: tuple[str, ...] = (
    "rank",
    "candidate",
    "composite_score",
    "parse_success_rate",
    "schema_hit_rate",
    "entity_type_valid_rate",
    "relation_type_valid_rate",
    "endpoint_valid_rate",
    "duplicate_entity_rate",
    "noise_entity_rate",
    "output_stability",
    "audit_entity_recall",
    "audit_relation_recall",
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
        lines.append(
            f"- **{row['candidate']}**（rank={row.get('rank')}, "
            f"composite_score={_format_value(row.get('composite_score'))}）"
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
) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    payload = {
        "task": "extraction_score_top_candidates",
        "k": k,
        "weights": weights,
        "inputs": inputs,
        "top_candidates": list(ranked[:k]),
        "all_candidates_ranked": list(ranked),
    }
    path.write_text(json.dumps(payload, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
