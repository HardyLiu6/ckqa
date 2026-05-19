from __future__ import annotations

from pathlib import Path

import pandas as pd


def test_compare_algorithmic_runs_writes_markdown_with_hybrid_v0_alias(tmp_path: Path):
    from graphrag_pipeline.scripts.qa_eval.compare_algorithmic_runs import compare_algorithmic_runs

    baseline_dir = tmp_path / "baseline"
    hybrid_dir = tmp_path / "hybrid"
    baseline_dir.mkdir()
    hybrid_dir.mkdir()
    pd.DataFrame(
        [
            {
                "question_id": "Q001",
                "mode": "basic",
                "effective_score_experimental": 0.62,
                "semantic_coverage_f1": 0.70,
                "citation_recall_at_3": 0.50,
                "citation_rr": 0.45,
                "elapsed_seconds": 8.0,
                "error_count": 0,
            },
            {
                "question_id": "Q002",
                "mode": "basic",
                "effective_score_experimental": 0.58,
                "semantic_coverage_f1": 0.60,
                "citation_recall_at_3": 0.40,
                "citation_rr": 0.35,
                "elapsed_seconds": 10.0,
                "error_count": 1,
            },
        ]
    ).to_csv(baseline_dir / "algorithmic_scoring.csv", index=False)
    pd.DataFrame(
        [
            {
                "question_id": "Q001",
                "mode": "graphrag-hybrid-v0-search:latest",
                "effective_score_experimental": 0.72,
                "semantic_coverage_f1": 0.75,
                "citation_recall_at_3": 0.60,
                "citation_rr": 0.55,
                "elapsed_seconds": 9.0,
                "error_count": 0,
            },
            {
                "question_id": "Q002",
                "mode": "hybrid_v0",
                "effective_score_experimental": 0.68,
                "semantic_coverage_f1": 0.65,
                "citation_recall_at_3": 0.50,
                "citation_rr": 0.45,
                "elapsed_seconds": 11.0,
                "error_count": 0,
            },
        ]
    ).to_csv(hybrid_dir / "algorithmic_scoring.csv", index=False)
    output_path = tmp_path / "compare.md"

    markdown = compare_algorithmic_runs(
        baseline_run_dir=baseline_dir,
        hybrid_run_dir=hybrid_dir,
        output_path=output_path,
    )

    assert output_path.read_text(encoding="utf-8") == markdown
    assert "hybrid_v0" in markdown
    assert "effective_score_experimental" in markdown
    assert "semantic_coverage_f1" in markdown
    assert "| baseline | basic |" in markdown
    assert "| hybrid | hybrid_v0 |" in markdown
