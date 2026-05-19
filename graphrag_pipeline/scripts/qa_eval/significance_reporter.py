from __future__ import annotations

import argparse
from pathlib import Path
from typing import Any

import pandas as pd

from graphrag_pipeline.scripts.qa_eval.bootstrap_stats import (
    bootstrap_mean_ci,
    pairwise_bootstrap_table,
)


DEFAULT_METRICS = [
    "effective_score_experimental",
    "semantic_coverage_f1",
    "citation_recall_at_3",
    "citation_rr",
    "citation_ndcg_at_5",
    "rouge_lsum",
    "keyword_recall",
]
MIN_CATEGORY_BOOTSTRAP_N = 15


def category_sample_warnings(df: pd.DataFrame, *, min_sample_size: int = MIN_CATEGORY_BOOTSTRAP_N) -> list[str]:
    warnings: list[str] = []
    if not {"category", "mode", "question_id"} <= set(df.columns):
        return warnings

    for (category, mode), group in df.groupby(["category", "mode"], dropna=False):
        count = int(group["question_id"].nunique())
        if count < min_sample_size:
            warnings.append(
                f"- WARNING: category={category}, mode={mode} 只有 {count} 题，低于 {min_sample_size}；"
                "该分层 CI 只能作探索性参考，不能作为上线判据。"
            )
    return warnings


def write_significance_report(
    run_dir: Path,
    *,
    metrics: list[str] | None = None,
    min_sample_size: int = MIN_CATEGORY_BOOTSTRAP_N,
) -> Path:
    metrics = metrics or DEFAULT_METRICS
    df = pd.read_csv(run_dir / "algorithmic_scoring.csv")
    lines = ["# 四模式显著性对比", ""]

    warnings = category_sample_warnings(df, min_sample_size=min_sample_size)
    if warnings:
        lines += ["## 小样本警告", "", *warnings, ""]

    lines += [
        "> 当前实现使用普通百分位 bootstrap。若后续要比较很小的 route margin，需增加 BCa bootstrap 或扩大测试集后再下结论。",
        "",
    ]

    for metric in metrics:
        if metric not in df.columns:
            continue
        lines += [f"## {metric}", ""]
        lines += ["### 按题型 x 模式", ""]
        lines += _format_table(
            ["category", "mode", "ci_low", "mean", "ci_high"],
            _category_mode_ci_rows(df, metric),
        )
        lines += ["", "### Pairwise", ""]
        lines += _format_table(
            ["mode_a", "mode_b", "mean_diff", "ci_low", "ci_high", "win_rate"],
            _pairwise_rows(df, metric),
        )
        lines.append("")

    output = run_dir / "significance.md"
    output.write_text("\n".join(lines), encoding="utf-8")
    return output


def _category_mode_ci_rows(df: pd.DataFrame, metric: str) -> list[list[Any]]:
    rows: list[list[Any]] = []
    for (category, mode), group in df.groupby(["category", "mode"], dropna=False):
        low, mean, high = bootstrap_mean_ci(group[metric].tolist())
        rows.append([category, mode, f"{low:.4f}", f"{mean:.4f}", f"{high:.4f}"])
    return rows


def _pairwise_rows(df: pd.DataFrame, metric: str) -> list[list[Any]]:
    rows: list[list[Any]] = []
    for row in pairwise_bootstrap_table(df, metric=metric):
        rows.append(
            [
                row["mode_a"],
                row["mode_b"],
                f"{row['mean_diff']:.4f}",
                f"{row['ci_low']:.4f}",
                f"{row['ci_high']:.4f}",
                f"{row['win_rate']:.4f}",
            ]
        )
    return rows


def _format_table(header: list[str], rows: list[list[Any]]) -> list[str]:
    lines = ["| " + " | ".join(header) + " |", "| " + " | ".join(["---"] * len(header)) + " |"]
    for row in rows:
        lines.append("| " + " | ".join(str(value) for value in row) + " |")
    return lines


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--run-dir", type=Path, required=True)
    args = parser.parse_args()
    output = write_significance_report(args.run_dir)
    print(f"wrote {output}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
