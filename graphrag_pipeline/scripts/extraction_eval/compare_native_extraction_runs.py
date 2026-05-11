#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""对原生抽取器产出的多个 eval run 做并排对比，同时输出格式缺陷诊断。

用途：
- 把「prompt A / prompt B / 同 prompt 不同 strict 开关」等多轮实验拉到一张
  markdown 表里，便于找出最适合生产索引的 prompt/抽取配置；
- 额外做一份**格式缺陷统计**（引号污染、括号不平衡、单样本解析失败），
  因为 audit 指标本身不能完全反映 prompt 在真实 graphrag index 流水线里
  的稳定性。

输入：
- 每个参与对比的 eval run 需要两样东西：
  1. eval 产物目录（含 `<candidate>.json`）
  2. 对应的 scoring 目录（含 `extraction_compare.csv`，已经跑过
     `score_extraction_results.py --relation-validation-mode drop-invalid`）

输出：
- 写 `results/reports/native_extraction_comparisons/runs/<run_id>/summary.md`
- 同时在 stdout 打印表格，便于快速观察

CLI 示例：
    python scripts/compare_native_extraction_runs.py \\
        --run-id my_compare \\
        --entry "exp1 v2 原版 + strict|native_exp1_v2_strict_20260511|native_exp1_v2_strict_rescored" \\
        --entry "exp3 v2+strict_tuple + strict|native_exp3_strict_tuple_20260511|native_exp3_strict_tuple_scored" \\
        --overwrite

每个 `--entry` 的格式是 `标签|eval_run_dir|scoring_run_id`。
"""

from __future__ import annotations

import argparse
import csv
import glob
import json
from dataclasses import dataclass
from pathlib import Path
from typing import Any, Sequence


PROJECT_ROOT = Path(__file__).resolve().parents[2]

# 哪些 description 里的字符视为"破坏 tuple 边界的格式缺陷"；
# graphrag 原生 `_process_result` 会按 `<|>` / `##` / 尾部 `)` 切分，
# 所以这些字符如果出现在 description 正文里（不是包裹 record 的外层括号）
# 就会让下游解析把单条 record 拆成多条。
TUPLE_FRAGILE_MARKERS = ("##", "<|>")


@dataclass
class ComparisonEntry:
    label: str
    eval_run_dir: Path
    scoring_run_id: str


def _parse_entry(raw: str, *, root: Path) -> ComparisonEntry:
    parts = raw.split("|")
    if len(parts) != 3:
        raise ValueError(
            f"--entry 期望格式 '标签|eval_run_dir|scoring_run_id'，但收到：{raw!r}"
        )
    label, eval_run_dir_str, scoring_run_id = [p.strip() for p in parts]
    eval_run_dir = Path(eval_run_dir_str)
    if not eval_run_dir.is_absolute():
        eval_run_dir = (root / eval_run_dir).resolve()
    return ComparisonEntry(
        label=label,
        eval_run_dir=eval_run_dir,
        scoring_run_id=scoring_run_id,
    )


def _load_scoring_top_row(root: Path, scoring_run_id: str) -> dict[str, Any]:
    """读 extraction_compare.csv 第一行（rank=1 的候选）。"""
    csv_path = (
        root
        / "results"
        / "reports"
        / "extraction_scoring"
        / "runs"
        / scoring_run_id
        / "extraction_compare.csv"
    )
    with csv_path.open(encoding="utf-8", newline="") as f:
        reader = csv.DictReader(f)
        for row in reader:
            return dict(row)
    raise ValueError(f"scoring 目录缺少数据行：{csv_path}")


def _sha256_of(path: Path) -> str:
    import hashlib
    digest = hashlib.sha256()
    with path.open("rb") as f:
        for chunk in iter(lambda: f.read(1 << 20), b""):
            digest.update(chunk)
    return digest.hexdigest()


def _eval_run_sha256s(eval_run_dir: Path) -> list[dict[str, str]]:
    """收集 eval run 目录下所有 *.json 的 sha256，便于复现时校验。"""
    out: list[dict[str, str]] = []
    for path in sorted(eval_run_dir.glob("*.json")):
        out.append({
            "file": path.name,
            "sha256": _sha256_of(path),
            "size_bytes": path.stat().st_size,
        })
    return out


def _count_format_defects(eval_run_dir: Path) -> dict[str, int]:
    """统计本 eval run 里的 tuple 格式缺陷。

    - total_samples：样本总数
    - total_entities / total_relationships：实体 / 关系总数
    - low_parse_samples：实体数 ≤ 2 的样本数（疑似解析级联失败）
    - quote_wrapped_titles：title 以任何引号开头或结尾（`"`、`'`、`"`、`'` 等）的实体
    - quote_wrapped_types：entity_type 以引号开头或结尾的实体
    - description_boundary_chars：关系 description 里出现 `##` 或 `<|>`
    - trailing_paren_descriptions：关系 description 末尾出现裸 `)`（但整条不在括号里）
    """
    total_samples = 0
    total_entities = 0
    total_relationships = 0
    low_parse = 0
    quote_titles = 0
    quote_types = 0
    boundary_desc = 0
    trailing_paren = 0

    for path in sorted(eval_run_dir.glob("*.json")):
        payload = json.loads(path.read_text(encoding="utf-8"))
        for r in payload.get("results") or []:
            total_samples += 1
            entities = r.get("entities") or []
            relationships = r.get("relationships") or []
            total_entities += len(entities)
            total_relationships += len(relationships)
            if len(entities) <= 2:
                low_parse += 1
            for e in entities:
                title = str(e.get("title") or "")
                etype = str(e.get("type") or "")
                if title and (title[0] in '"\'“”‘’' or title[-1] in '"\'“”‘’'):
                    quote_titles += 1
                if etype and (etype[0] in '"\'“”‘’' or etype[-1] in '"\'“”‘’'):
                    quote_types += 1
            for rel in relationships:
                desc = str(rel.get("description") or "")
                if any(marker in desc for marker in TUPLE_FRAGILE_MARKERS):
                    boundary_desc += 1
                # 末尾孤立 `)`：description 末尾有 `)` 但开头没有配对的 `(`
                stripped = desc.rstrip()
                if stripped.endswith(")") and stripped.count("(") < stripped.count(")"):
                    trailing_paren += 1

    return {
        "total_samples": total_samples,
        "total_entities": total_entities,
        "total_relationships": total_relationships,
        "low_parse_samples": low_parse,
        "quote_wrapped_titles": quote_titles,
        "quote_wrapped_types": quote_types,
        "description_boundary_chars": boundary_desc,
        "trailing_paren_descriptions": trailing_paren,
    }


def _format_metric(value: Any, digits: int = 4) -> str:
    try:
        return f"{float(value):.{digits}f}"
    except (TypeError, ValueError):
        return str(value) if value not in (None, "") else "-"


def compare_runs(
    *,
    root: Path,
    run_id: str,
    entries: Sequence[ComparisonEntry],
    overwrite: bool = False,
) -> dict[str, Any]:
    root = root.resolve()
    report_dir = (
        root
        / "results"
        / "reports"
        / "native_extraction_comparisons"
        / "runs"
        / run_id
    )
    report_dir.mkdir(parents=True, exist_ok=True)
    summary_path = report_dir / "summary.md"
    json_path = report_dir / "summary.json"
    if (summary_path.exists() or json_path.exists()) and not overwrite:
        raise FileExistsError(
            f"对比报告已存在，若要覆盖请传 --overwrite：{report_dir}"
        )

    rows: list[dict[str, Any]] = []
    for entry in entries:
        metrics = _load_scoring_top_row(root, entry.scoring_run_id)
        defects = _count_format_defects(entry.eval_run_dir)
        eval_sha256s = _eval_run_sha256s(entry.eval_run_dir)
        rows.append({
            "label": entry.label,
            "eval_run_dir": str(entry.eval_run_dir),
            "scoring_run_id": entry.scoring_run_id,
            "metrics": metrics,
            "defects": defects,
            "eval_artifacts": eval_sha256s,
        })

    json_path.write_text(
        json.dumps(
            {
                "task": "native_extraction_run_comparison",
                "run_id": run_id,
                "rows": rows,
            },
            ensure_ascii=False,
            indent=2,
        )
        + "\n",
        encoding="utf-8",
    )

    # markdown
    lines: list[str] = [
        "# 原生抽取器实验对比",
        "",
        f"- `run_id`：{run_id}",
        f"- 条目数：{len(rows)}",
        "",
        "## Audit 指标（每条 eval run 的 rank 1 候选）",
        "",
        "| 实验 | candidate | entity_recall | entity_precision | relation_recall | entity_type_valid | endpoint_valid | parse_success |",
        "|---|---|---:|---:|---:|---:|---:|---:|",
    ]
    for row in rows:
        m = row["metrics"]
        lines.append(
            "| "
            + " | ".join([
                row["label"],
                m.get("candidate", ""),
                _format_metric(m.get("audit_entity_recall")),
                _format_metric(m.get("audit_entity_precision")),
                _format_metric(m.get("audit_relation_recall")),
                _format_metric(m.get("entity_type_valid_rate")),
                _format_metric(m.get("endpoint_valid_rate")),
                _format_metric(m.get("parse_success_rate")),
            ])
            + " |"
        )

    lines.extend([
        "",
        "## 格式缺陷统计（直接反映 prompt 在生产 tuple 解析器下的稳定性）",
        "",
        "| 实验 | 样本 | 实体 | 关系 | low_parse(≤2) | title 带引号 | type 带引号 | desc 含 ##/<\\|> | desc 括号不平衡 |",
        "|---|---:|---:|---:|---:|---:|---:|---:|---:|",
    ])
    for row in rows:
        d = row["defects"]
        lines.append(
            "| "
            + " | ".join([
                row["label"],
                str(d["total_samples"]),
                str(d["total_entities"]),
                str(d["total_relationships"]),
                str(d["low_parse_samples"]),
                str(d["quote_wrapped_titles"]),
                str(d["quote_wrapped_types"]),
                str(d["description_boundary_chars"]),
                str(d["trailing_paren_descriptions"]),
            ])
            + " |"
        )

    lines.extend([
        "",
        "## 数据源",
        "",
    ])
    for row in rows:
        lines.append(f"### {row['label']}")
        lines.append(f"- eval_run_dir：`{row['eval_run_dir']}`")
        lines.append(f"- scoring_run_id：`{row['scoring_run_id']}`")
        if row.get("eval_artifacts"):
            lines.append("- eval 产物 sha256（用于复现校验）：")
            for item in row["eval_artifacts"]:
                lines.append(
                    f"  - `{item['file']}`（{item['size_bytes']} bytes）sha256=`{item['sha256']}`"
                )
        lines.append("")

    summary_path.write_text("\n".join(lines) + "\n", encoding="utf-8")

    # stdout
    print("\n".join(lines))

    return {
        "status": "success",
        "run_id": run_id,
        "report_dir": str(report_dir),
        "entry_count": len(rows),
    }


def _build_parser() -> argparse.ArgumentParser:
    parser = argparse.ArgumentParser(
        description="对原生抽取器多个 eval run 做并排对比（含 audit 指标和格式缺陷诊断）"
    )
    parser.add_argument(
        "--root",
        default=str(PROJECT_ROOT),
        help="GraphRAG 模块根目录",
    )
    parser.add_argument("--run-id", required=True, help="对比报告 run_id")
    parser.add_argument(
        "--entry",
        action="append",
        required=True,
        help=(
            "对比条目，格式 '标签|eval_run_dir|scoring_run_id'；可重复传入。"
            "eval_run_dir 可以是 results/extraction_eval/runs/<xxx> 的绝对或相对路径，"
            "scoring_run_id 对应 results/reports/extraction_scoring/runs/<yyy>。"
        ),
    )
    parser.add_argument("--overwrite", action="store_true", help="允许覆盖已有对比报告")
    return parser


def main(argv: Sequence[str] | None = None) -> int:
    args = _build_parser().parse_args(argv)
    root = Path(args.root).resolve()
    entries = [_parse_entry(raw, root=root) for raw in args.entry]
    summary = compare_runs(
        root=root,
        run_id=args.run_id,
        entries=entries,
        overwrite=args.overwrite,
    )
    print(json.dumps(summary, ensure_ascii=False, indent=2))
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
