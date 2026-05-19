from __future__ import annotations

import argparse
import csv
import json
import re
import sys
from collections import defaultdict
from dataclasses import dataclass, field
from pathlib import Path
from statistics import mean
from typing import Any

from graphrag_pipeline.scripts.qa_eval.category_thresholds import length_score
from graphrag_pipeline.scripts.qa_eval.test_set_schema import QuestionCategory


CITATION_PATTERNS: tuple[re.Pattern[str], ...] = tuple(
    re.compile(pattern)
    for pattern in (r"\[Data:", r"\[来源[:：]", r"\[出处", r"\[Sources?")
)

PER_QUESTION_METRICS: tuple[str, ...] = (
    "entity_hit_rate",
    "must_cite_hit",
    "citation_format_present",
    "negative_hit",
    "answer_chars",
    "length_score",
    "info_density",
    "elapsed_seconds",
)


@dataclass
class ScoringSummary:
    per_question: dict[str, dict[str, dict[str, Any]]] = field(default_factory=dict)
    per_mode: dict[str, dict[str, float]] = field(default_factory=dict)
    per_category_mode: dict[str, dict[str, dict[str, float]]] = field(default_factory=dict)
    rows: list[dict[str, Any]] = field(default_factory=list)
    modes: list[str] = field(default_factory=list)


def score_baseline_run(run_dir: Path | str) -> ScoringSummary:
    run_path = Path(run_dir)
    raw_dir = run_path / "raw"
    if not raw_dir.exists():
        raise FileNotFoundError(f"missing raw dir: {raw_dir}")

    modes = _load_modes_from_meta(run_path)
    raw_files = sorted(raw_dir.glob("Q*.json"))
    if not raw_files:
        raise FileNotFoundError(f"no question files under {raw_dir}")

    summary = ScoringSummary(modes=modes)
    per_mode_values: dict[str, dict[str, list[float]]] = {
        mode: {metric: [] for metric in PER_QUESTION_METRICS} for mode in modes
    }
    per_category_values: dict[str, dict[str, dict[str, list[float]]]] = defaultdict(
        lambda: {mode: {metric: [] for metric in PER_QUESTION_METRICS} for mode in modes}
    )
    error_counts: dict[str, int] = {mode: 0 for mode in modes}
    category_error_counts: dict[str, dict[str, int]] = defaultdict(lambda: {mode: 0 for mode in modes})

    for raw_file in raw_files:
        item = _read_json(raw_file)
        item_id = str(item.get("id") or item.get("question_id") or raw_file.stem)
        category = str(item["category"])
        summary.per_question[item_id] = {}
        for mode in modes:
            row = _score_mode(item=item, item_id=item_id, mode=mode)
            summary.per_question[item_id][mode] = row
            summary.rows.append(row)
            if row["error"]:
                error_counts[mode] += 1
                category_error_counts[category][mode] += 1
                elapsed = float(row.get("elapsed_seconds") or 0.0)
                if elapsed > 0:
                    per_mode_values[mode]["elapsed_seconds"].append(elapsed)
                    per_category_values[category][mode]["elapsed_seconds"].append(elapsed)
                continue
            for metric in PER_QUESTION_METRICS:
                value = float(row.get(metric, 0.0))
                per_mode_values[mode][metric].append(value)
                per_category_values[category][mode][metric].append(value)

    for mode in modes:
        summary.per_mode[mode] = _summarize_metric_lists(per_mode_values[mode])
        summary.per_mode[mode]["error_count"] = float(error_counts[mode])

    for category, mode_values in per_category_values.items():
        summary.per_category_mode[category] = {}
        for mode in modes:
            summary.per_category_mode[category][mode] = _summarize_metric_lists(mode_values[mode])
            summary.per_category_mode[category][mode]["error_count"] = float(
                category_error_counts[category][mode]
            )

    _write_outputs(run_path, summary)
    return summary


def score_run(run_dir: Path | str) -> dict[str, Any]:
    """Compatibility wrapper for earlier local drafts; prefer score_baseline_run()."""
    summary = score_baseline_run(run_dir)
    return _summary_payload(summary)


def _score_mode(*, item: dict[str, Any], item_id: str, mode: str) -> dict[str, Any]:
    payload = item.get("modes", {}).get(mode, {})
    base: dict[str, Any] = {
        "question_id": item_id,
        "category": item.get("category", ""),
        "mode": mode,
        "error": bool(payload.get("error")),
        "error_message": str(payload.get("error", "")) if payload.get("error") else "",
    }
    if base["error"]:
        return {
            **base,
            "entity_hit_rate": 0.0,
            "must_cite_hit": 0.0,
            "citation_format_present": 0.0,
            "negative_hit": 0.0,
            "answer_chars": 0.0,
            "length_score": 0.0,
            "info_density": 0.0,
            "elapsed_seconds": float(payload.get("elapsed_seconds") or 0.0),
        }

    answer = str(payload.get("answer") or "")
    answer_chars = len(answer)
    gold_entities = [str(term) for term in item.get("gold_entities", []) if str(term)]
    must_cite_terms = [str(term) for term in item.get("must_cite_terms", []) if str(term)]
    negative_terms = [str(term) for term in item.get("negative_terms", []) if str(term)]

    entity_hit_count = _count_hits(answer, gold_entities)
    must_cite_hit_count = _count_hits(answer, must_cite_terms)
    negative_hit_count = _count_hits(answer, negative_terms)

    return {
        **base,
        "entity_hit_rate": round(_ratio(entity_hit_count, len(gold_entities), empty_value=1.0), 4),
        "must_cite_hit": float(
            1.0 if not must_cite_terms else int(must_cite_hit_count == len(must_cite_terms))
        ),
        "citation_format_present": float(any(pattern.search(answer) for pattern in CITATION_PATTERNS)),
        "negative_hit": float(negative_hit_count > 0),
        "answer_chars": float(answer_chars),
        "length_score": length_score(QuestionCategory(str(item["category"])), answer_chars),
        "info_density": round(entity_hit_count / max(answer_chars, 1) * 1000.0, 4),
        "elapsed_seconds": float(payload.get("elapsed_seconds") or 0.0),
    }


def _load_modes_from_meta(run_dir: Path) -> list[str]:
    meta_path = run_dir / "run_meta.json"
    if not meta_path.exists():
        raise FileNotFoundError(f"missing run_meta.json under {run_dir}")
    meta = _read_json(meta_path)
    modes = meta.get("modes")
    if not isinstance(modes, list) or not modes:
        raise ValueError(f"run_meta.json has no 'modes' list: {meta_path}")
    return [str(mode) for mode in modes]


def _summarize_metric_lists(metrics: dict[str, list[float]]) -> dict[str, float]:
    summary = {
        metric: round(mean(values), 4) if values else 0.0
        for metric, values in metrics.items()
    }
    summary["question_count"] = float(len(next(iter(metrics.values()), [])))
    return summary


def _summary_payload(summary: ScoringSummary) -> dict[str, Any]:
    return {
        "per_question": summary.per_question,
        "per_mode": summary.per_mode,
        "per_category_mode": summary.per_category_mode,
        "rows": summary.rows,
        "modes": summary.modes,
    }


def _write_outputs(run_dir: Path, summary: ScoringSummary) -> None:
    payload = _summary_payload(summary)
    (run_dir / "rule_scoring.json").write_text(
        json.dumps(payload, ensure_ascii=False, indent=2) + "\n",
        encoding="utf-8",
    )
    _write_csv(run_dir / "rule_scoring.csv", summary.rows)
    _write_markdown(run_dir / "rule_scoring.md", summary)


def _write_csv(path: Path, rows: list[dict[str, Any]]) -> None:
    fieldnames = [
        "question_id",
        "category",
        "mode",
        "error",
        "error_message",
        *PER_QUESTION_METRICS,
    ]
    with path.open("w", encoding="utf-8", newline="") as handle:
        writer = csv.DictWriter(handle, fieldnames=fieldnames)
        writer.writeheader()
        writer.writerows(rows)


def _write_markdown(path: Path, summary: ScoringSummary) -> None:
    lines = [
        "# QA Baseline 规则评分",
        "",
        "| mode | error_count | entity_hit_rate | must_cite_hit | citation_format_present | negative_hit | length_score | info_density | elapsed_seconds |",
        "| --- | ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: |",
    ]
    for mode in summary.modes:
        row = summary.per_mode[mode]
        lines.append(
            "| "
            + " | ".join(
                [
                    mode,
                    f"{row['error_count']:.0f}",
                    _fmt(row["entity_hit_rate"]),
                    _fmt(row["must_cite_hit"]),
                    _fmt(row["citation_format_present"]),
                    _fmt(row["negative_hit"]),
                    _fmt(row["length_score"]),
                    _fmt(row["info_density"]),
                    _fmt(row["elapsed_seconds"]),
                ]
            )
            + " |"
        )
    path.write_text("\n".join(lines) + "\n", encoding="utf-8")


def _count_hits(answer: str, terms: list[str]) -> int:
    answer_lc = answer.casefold()
    return sum(1 for term in terms if term.casefold() in answer_lc)


def _ratio(hit_count: int, total: int, *, empty_value: float = 0.0) -> float:
    if total <= 0:
        return empty_value
    return hit_count / total


def _read_json(path: Path) -> dict[str, Any]:
    return json.loads(path.read_text(encoding="utf-8"))


def _fmt(value: float) -> str:
    return f"{value:.4f}"


def main(argv: list[str] | None = None) -> int:
    parser = argparse.ArgumentParser(description="Score a GraphRAG QA baseline run with rules.")
    parser.add_argument("--run-dir", type=Path, required=True)
    args = parser.parse_args(argv)

    summary = score_baseline_run(args.run_dir)
    print(json.dumps(summary.per_mode, ensure_ascii=False, indent=2))
    return 0


if __name__ == "__main__":  # pragma: no cover
    sys.exit(main())
