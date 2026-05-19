from __future__ import annotations

import argparse
import csv
import json
import logging
import sys
from pathlib import Path
from typing import Any

from graphrag_pipeline.scripts.qa_eval.baseline_scorer import (
    PER_QUESTION_METRICS,
    ScoringSummary,
    score_baseline_run,
)
from graphrag_pipeline.scripts.qa_eval.judge_scorer import JUDGE_METRICS


LOGGER = logging.getLogger(__name__)


def generate_report(run_dir: Path | str) -> None:
    run_path = Path(run_dir)
    rule_summary = score_baseline_run(run_path)
    judge_payload = _load_optional_json(run_path / "judge_scoring.json")
    _write_combined_csv(run_path / "combined.csv", rule_summary, judge_payload)
    _write_markdown(run_path / "scoring.md", rule_summary, judge_payload)


def _write_combined_csv(
    path: Path,
    rule_summary: ScoringSummary,
    judge_payload: dict[str, Any] | None,
) -> None:
    header = [
        "question_id",
        "category",
        "mode",
        *PER_QUESTION_METRICS,
        *JUDGE_METRICS,
        "error",
    ]
    with path.open("w", encoding="utf-8", newline="") as handle:
        writer = csv.DictWriter(handle, fieldnames=header)
        writer.writeheader()
        judge_per_question = (judge_payload or {}).get("per_question", {})
        for row in rule_summary.rows:
            question_id = str(row["question_id"])
            mode = str(row["mode"])
            judge_row = judge_per_question.get(question_id, {}).get(mode, {})
            writer.writerow(
                {
                    "question_id": question_id,
                    "category": row.get("category", ""),
                    "mode": mode,
                    **{metric: row.get(metric, 0.0) for metric in PER_QUESTION_METRICS},
                    **{metric: judge_row.get(metric, "") for metric in JUDGE_METRICS},
                    "error": int(bool(row.get("error")) or bool(judge_row.get("error"))),
                }
            )


def _write_markdown(
    path: Path,
    rule_summary: ScoringSummary,
    judge_payload: dict[str, Any] | None,
) -> None:
    lines: list[str] = [
        f"# {path.parent.name} scoring",
        "",
        "## 规则层 - 模式总均值",
        "",
    ]
    rule_metrics = (
        "entity_hit_rate",
        "must_cite_hit",
        "citation_format_present",
        "negative_hit",
        "length_score",
        "info_density",
        "answer_chars",
        "elapsed_seconds",
        "error_count",
    )
    lines.extend(
        _format_table(
            ["mode", *rule_metrics],
            [
                [mode] + [_format_cell(rule_summary.per_mode[mode].get(metric, 0.0)) for metric in rule_metrics]
                for mode in rule_summary.modes
            ],
        )
    )

    lines += ["", "## 规则层 - 按题型 x 模式", ""]
    for category, modes_dict in rule_summary.per_category_mode.items():
        lines += [f"### {category}", ""]
        lines.extend(
            _format_table(
                ["mode", *rule_metrics],
                [
                    [mode] + [_format_cell(modes_dict[mode].get(metric, 0.0)) for metric in rule_metrics]
                    for mode in rule_summary.modes
                ],
            )
        )
        lines.append("")

    if judge_payload:
        lines += ["## 裁判层 - 模式总均值", ""]
        lines.extend(
            _format_table(
                ["mode", *JUDGE_METRICS],
                [
                    [mode] + [_format_cell(judge_payload["per_mode"][mode].get(metric, 0.0)) for metric in JUDGE_METRICS]
                    for mode in rule_summary.modes
                ],
            )
        )
        lines += ["", f"裁判模型：`{judge_payload.get('judge_model', '?')}`", ""]
        lines += ["## 裁判层 - 按题型 x 模式", ""]
        for category, modes_dict in judge_payload.get("per_category_mode", {}).items():
            lines += [f"### {category}", ""]
            lines.extend(
                _format_table(
                    ["mode", *JUDGE_METRICS],
                    [
                        [mode] + [_format_cell(modes_dict.get(mode, {}).get(metric, 0.0)) for metric in JUDGE_METRICS]
                        for mode in rule_summary.modes
                    ],
                )
            )
            lines.append("")
    else:
        lines += ["", "_未发现 `judge_scoring.json`，本次报告只包含规则层评分。_", ""]

    path.write_text("\n".join(lines) + "\n", encoding="utf-8")


def _format_table(header: list[str], rows: list[list[str]]) -> list[str]:
    lines = ["| " + " | ".join(header) + " |", "| " + " | ".join(["---"] * len(header)) + " |"]
    for row in rows:
        lines.append("| " + " | ".join(row) + " |")
    return lines


def _format_cell(value: Any) -> str:
    if isinstance(value, int | float):
        return f"{float(value):.4f}"
    return str(value)


def _load_optional_json(path: Path) -> dict[str, Any] | None:
    if not path.exists():
        return None
    return json.loads(path.read_text(encoding="utf-8"))


def main(argv: list[str] | None = None) -> int:
    parser = argparse.ArgumentParser(description="Generate QA baseline scoring reports.")
    parser.add_argument("--run-dir", type=Path, required=True)
    args = parser.parse_args(argv)

    logging.basicConfig(level=logging.INFO, format="%(asctime)s %(levelname)s %(message)s")
    generate_report(args.run_dir)
    return 0


if __name__ == "__main__":  # pragma: no cover
    sys.exit(main())
