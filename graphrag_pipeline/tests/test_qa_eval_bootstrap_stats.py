from __future__ import annotations

from pathlib import Path

import pandas as pd

from graphrag_pipeline.scripts.qa_eval.bootstrap_stats import (
    bootstrap_mean_ci,
    paired_bootstrap_diff,
)
from graphrag_pipeline.scripts.qa_eval.significance_reporter import (
    category_sample_warnings,
    write_significance_report,
)


def test_bootstrap_mean_ci_is_deterministic_with_seed():
    low, mean, high = bootstrap_mean_ci([0.2, 0.4, 0.6, 0.8], iterations=200, seed=3)

    assert low <= mean <= high
    assert round(mean, 2) == 0.5


def test_paired_bootstrap_diff_prefers_better_mode():
    df = pd.DataFrame(
        [
            {"question_id": "Q001", "mode": "a", "metric": 0.9},
            {"question_id": "Q001", "mode": "b", "metric": 0.2},
            {"question_id": "Q002", "mode": "a", "metric": 0.8},
            {"question_id": "Q002", "mode": "b", "metric": 0.3},
        ]
    )

    result = paired_bootstrap_diff(
        df,
        mode_a="a",
        mode_b="b",
        metric="metric",
        iterations=200,
        seed=7,
    )

    assert result["mean_diff"] > 0
    assert result["win_rate"] > 0.9


def test_category_sample_warnings_flags_small_groups():
    df = pd.DataFrame(
        [
            {"question_id": "Q001", "category": "factual_lookup", "mode": "a", "metric": 0.9},
            {"question_id": "Q002", "category": "factual_lookup", "mode": "a", "metric": 0.8},
        ]
    )

    warnings = category_sample_warnings(df, min_sample_size=15)

    assert warnings
    assert "factual_lookup" in warnings[0]


def test_write_significance_report_includes_small_sample_warning(tmp_path: Path):
    run_dir = tmp_path / "run"
    run_dir.mkdir()
    pd.DataFrame(
        [
            {
                "question_id": "Q001",
                "category": "factual_lookup",
                "mode": "a",
                "effective_score_experimental": 0.8,
                "citation_rr": 1.0,
            },
            {
                "question_id": "Q001",
                "category": "factual_lookup",
                "mode": "b",
                "effective_score_experimental": 0.2,
                "citation_rr": 0.0,
            },
        ]
    ).to_csv(run_dir / "algorithmic_scoring.csv", index=False)

    output = write_significance_report(run_dir, metrics=["effective_score_experimental"], min_sample_size=15)

    report = output.read_text(encoding="utf-8")
    assert "小样本警告" in report
    assert "effective_score_experimental" in report
    assert "Pairwise" in report
