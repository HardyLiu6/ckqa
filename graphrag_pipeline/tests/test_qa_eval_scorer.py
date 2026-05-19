from __future__ import annotations

import json
from pathlib import Path

import pytest

from graphrag_pipeline.scripts.qa_eval.baseline_scorer import (
    PER_QUESTION_METRICS,
    ScoringSummary,
    score_baseline_run,
)
from graphrag_pipeline.scripts.qa_eval.category_thresholds import length_score
from graphrag_pipeline.scripts.qa_eval.run_baseline_eval import BASELINE_MODES
from graphrag_pipeline.scripts.qa_eval.test_set_schema import QuestionCategory


def _write_json(path: Path, payload: dict) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(json.dumps(payload, ensure_ascii=False, indent=2), encoding="utf-8")


def _write_meta(run_dir: Path, modes: list[str] | None = None) -> None:
    _write_json(
        run_dir / "run_meta.json",
        {
            "run_id": run_dir.name,
            "index_run_label": "course-x",
            "total_items": 1,
            "modes": modes or list(BASELINE_MODES),
        },
    )


def _write_item(run_dir: Path, qid: str, payload: dict) -> None:
    _write_json(run_dir / "raw" / f"{qid}.json", payload)


def test_length_score_uses_category_thresholds() -> None:
    assert length_score(QuestionCategory.FACTUAL_LOOKUP, 20) == 0.0
    assert length_score(QuestionCategory.FACTUAL_LOOKUP, 80) == 1.0
    assert length_score(QuestionCategory.FACTUAL_LOOKUP, 250) == 1.0
    assert length_score(QuestionCategory.FACTUAL_LOOKUP, 399) == 0.5
    assert length_score(QuestionCategory.FACTUAL_LOOKUP, 400) == 0.0


def test_score_baseline_run_computes_rule_metrics_and_outputs_files(tmp_path: Path):
    run_dir = tmp_path / "rid"
    _write_meta(run_dir)
    answer = "DBSCAN 的两个核心超参数是 eps 和 MinPts [Data: Entities (12)]。"
    _write_item(
        run_dir,
        "Q001",
        {
            "id": "Q001",
            "question_id": "Q001",
            "category": "factual_lookup",
            "question": "DBSCAN 的两个核心超参数是什么？",
            "gold_answer_summary": "eps 和 MinPts",
            "gold_entities": ["DBSCAN", "eps", "MinPts"],
            "gold_text_unit_ids": [],
            "must_cite_terms": ["eps", "MinPts"],
            "negative_terms": ["KMeans"],
            "modes": {
                "graphrag-local-search:latest": {
                    "answer": answer,
                    "total_tokens": 50,
                    "elapsed_seconds": 1.2,
                },
                "graphrag-global-search:latest": {
                    "answer": "DBSCAN 是一种聚类算法，与 KMeans 不同。",
                    "total_tokens": 60,
                    "elapsed_seconds": 1.5,
                },
                "graphrag-drift-search:latest": {"error": "timeout", "elapsed_seconds": 42.3},
                "graphrag-basic-search:latest": {
                    "answer": "eps 和 MinPts。",
                    "total_tokens": 20,
                    "elapsed_seconds": 0.8,
                },
            },
        },
    )

    summary: ScoringSummary = score_baseline_run(run_dir)

    local = summary.per_question["Q001"]["graphrag-local-search:latest"]
    assert local["entity_hit_rate"] == 1.0
    assert local["must_cite_hit"] == 1.0
    assert local["citation_format_present"] == 1.0
    assert local["negative_hit"] == 0.0
    assert local["answer_chars"] == float(len(answer))
    assert local["info_density"] == pytest.approx(3 / len(answer) * 1000)
    assert set(PER_QUESTION_METRICS) <= set(local)

    global_row = summary.per_question["Q001"]["graphrag-global-search:latest"]
    assert global_row["entity_hit_rate"] < 1.0
    assert global_row["negative_hit"] == 1.0
    assert global_row["citation_format_present"] == 0.0

    drift_row = summary.per_question["Q001"]["graphrag-drift-search:latest"]
    assert drift_row["error"] is True
    assert drift_row["entity_hit_rate"] == 0.0
    assert drift_row["elapsed_seconds"] == 42.3

    assert "factual_lookup" in summary.per_category_mode
    assert summary.per_mode["graphrag-drift-search:latest"]["error_count"] == 1.0
    assert summary.per_mode["graphrag-drift-search:latest"]["elapsed_seconds"] == 42.3
    assert (run_dir / "rule_scoring.json").exists()
    assert (run_dir / "rule_scoring.csv").exists()
    assert (run_dir / "rule_scoring.md").exists()


def test_modes_order_from_run_meta_and_error_rows_do_not_drag_averages(tmp_path: Path):
    run_dir = tmp_path / "rid"
    modes = ["graphrag-basic-search:latest", "graphrag-local-search:latest"]
    _write_meta(run_dir, modes)
    _write_item(
        run_dir,
        "Q001",
        {
            "id": "Q001",
            "category": "factual_lookup",
            "question": "什么是 foo？",
            "gold_answer_summary": "foo",
            "gold_entities": ["foo"],
            "must_cite_terms": [],
            "negative_terms": [],
            "modes": {
                "graphrag-basic-search:latest": {"error": "timeout"},
                "graphrag-local-search:latest": {
                    "answer": "foo " * 40,
                    "total_tokens": 10,
                    "elapsed_seconds": 0.1,
                },
            },
        },
    )
    _write_item(
        run_dir,
        "Q002",
        {
            "id": "Q002",
            "category": "factual_lookup",
            "question": "什么是 foo？",
            "gold_answer_summary": "foo",
            "gold_entities": ["foo"],
            "must_cite_terms": [],
            "negative_terms": [],
            "modes": {
                "graphrag-basic-search:latest": {
                    "answer": "foo " * 40,
                    "total_tokens": 10,
                    "elapsed_seconds": 0.1,
                },
                "graphrag-local-search:latest": {
                    "answer": "foo " * 40,
                    "total_tokens": 10,
                    "elapsed_seconds": 0.1,
                },
            },
        },
    )

    summary = score_baseline_run(run_dir)

    assert summary.modes == modes
    assert [row["mode"] for row in summary.rows[:2]] == modes
    assert summary.per_mode["graphrag-basic-search:latest"]["entity_hit_rate"] == 1.0
    assert summary.per_mode["graphrag-basic-search:latest"]["error_count"] == 1.0


def test_scorer_cli_prints_per_mode_json(tmp_path: Path, capsys) -> None:
    from graphrag_pipeline.scripts.qa_eval import baseline_scorer

    run_dir = tmp_path / "rid"
    _write_meta(run_dir, ["graphrag-local-search:latest"])
    _write_item(
        run_dir,
        "Q001",
        {
            "id": "Q001",
            "category": "factual_lookup",
            "question": "什么是 foo？",
            "gold_answer_summary": "foo",
            "gold_entities": ["foo"],
            "must_cite_terms": [],
            "negative_terms": [],
            "modes": {
                "graphrag-local-search:latest": {
                    "answer": "foo " * 40,
                    "total_tokens": 10,
                    "elapsed_seconds": 0.1,
                }
            },
        },
    )

    exit_code = baseline_scorer.main(["--run-dir", str(run_dir)])

    assert exit_code == 0
    printed = json.loads(capsys.readouterr().out)
    assert printed == json.loads((run_dir / "rule_scoring.json").read_text(encoding="utf-8"))[
        "per_mode"
    ]
