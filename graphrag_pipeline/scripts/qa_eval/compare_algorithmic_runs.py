from __future__ import annotations

import argparse
from pathlib import Path

import pandas as pd


HYBRID_V0_MODEL = "graphrag-hybrid-v0-search:latest"
DEFAULT_METRICS: tuple[str, ...] = (
    "effective_score_experimental",
    "semantic_coverage_f1",
    "citation_recall_at_3",
    "citation_rr",
    "selected_evidence_recall_at_3",
    "selected_evidence_rr",
    "selected_evidence_ndcg_at_5",
    "elapsed_seconds",
    "error_count",
)


def compare_algorithmic_runs(
    *,
    baseline_run_dir: Path,
    hybrid_run_dir: Path,
    output_path: Path | None = None,
    metrics: tuple[str, ...] = DEFAULT_METRICS,
) -> str:
    frames = [
        _load_run_frame(Path(baseline_run_dir), "baseline"),
        _load_run_frame(Path(hybrid_run_dir), "hybrid"),
    ]
    combined = pd.concat(frames, ignore_index=True)
    available_metrics = [metric for metric in metrics if metric in combined.columns]
    if not available_metrics:
        raise ValueError("no comparable metric columns found in algorithmic_scoring.csv files")

    grouped = (
        combined.groupby(["run", "mode"], as_index=False)[available_metrics]
        .mean(numeric_only=True)
        .sort_values(["run", "mode"])
    )
    markdown = "# Hybrid v0 算法评分对比\n\n" + _dataframe_to_markdown(grouped) + "\n"
    if output_path is not None:
        Path(output_path).write_text(markdown, encoding="utf-8")
    return markdown


def _load_run_frame(run_dir: Path, run_label: str) -> pd.DataFrame:
    csv_path = run_dir / "algorithmic_scoring.csv"
    if not csv_path.exists():
        raise FileNotFoundError(f"missing algorithmic scoring file: {csv_path}")
    frame = pd.read_csv(csv_path)
    if "mode" not in frame.columns:
        raise ValueError(f"{csv_path} is missing required column: mode")
    frame = frame.copy()
    frame.insert(0, "run", run_label)
    frame["mode"] = frame["mode"].map(_normalize_mode_label)
    return frame


def _normalize_mode_label(mode: object) -> str:
    value = str(mode)
    if value == HYBRID_V0_MODEL:
        return "hybrid_v0"
    return value


def _dataframe_to_markdown(frame: pd.DataFrame) -> str:
    if frame.empty:
        return "（暂无评分数据）"
    headers = [str(column) for column in frame.columns]
    lines = [
        "| " + " | ".join(headers) + " |",
        "| " + " | ".join(["---"] * len(headers)) + " |",
    ]
    for _, row in frame.iterrows():
        values = [_format_value(row[column]) for column in frame.columns]
        lines.append("| " + " | ".join(values) + " |")
    return "\n".join(lines)


def _format_value(value: object) -> str:
    if isinstance(value, float):
        return f"{value:.4f}".rstrip("0").rstrip(".")
    return str(value)


def main(argv: list[str] | None = None) -> int:
    parser = argparse.ArgumentParser(description="Compare baseline and Hybrid v0 algorithmic QA eval runs.")
    parser.add_argument("--baseline-run-dir", type=Path, required=True)
    parser.add_argument("--hybrid-run-dir", type=Path, required=True)
    parser.add_argument("--output", type=Path, default=None)
    args = parser.parse_args(argv)

    markdown = compare_algorithmic_runs(
        baseline_run_dir=args.baseline_run_dir,
        hybrid_run_dir=args.hybrid_run_dir,
        output_path=args.output,
    )
    if args.output is None:
        print(markdown, end="")
    return 0


if __name__ == "__main__":  # pragma: no cover
    raise SystemExit(main())
