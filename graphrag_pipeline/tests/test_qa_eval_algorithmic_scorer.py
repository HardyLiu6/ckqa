from __future__ import annotations

import json
from pathlib import Path
from unittest.mock import patch

import pandas as pd
import pytest

from graphrag_pipeline.scripts.qa_eval.semantic_similarity import SemanticScoringConfig
from graphrag_pipeline.scripts.qa_eval.algorithmic_scorer import score_run_algorithmically
from graphrag_pipeline.scripts.qa_eval.run_loader import (
    audit_text_unit_prefix_collisions,
    load_raw_mode_answer,
    load_test_set,
    resolve_text_units_path,
)


def _write_test_set(path: Path) -> None:
    path.write_text(
        json.dumps(
            {
                "id": "Q001",
                "category": "factual_lookup",
                "question": "DBSCAN 的核心参数是什么？",
                "gold_answer_summary": "DBSCAN 的核心参数是 eps 和 MinPts。",
                "gold_entities": ["DBSCAN", "eps", "MinPts"],
                "gold_text_unit_ids": ["d244f9016ac8"],
                "must_cite_terms": ["eps", "MinPts"],
                "negative_terms": [],
            },
            ensure_ascii=False,
        )
        + "\n",
        encoding="utf-8",
    )


def _write_two_item_test_set(path: Path) -> None:
    items = [
        {
            "id": "Q001",
            "category": "factual_lookup",
            "question": "DBSCAN 的核心参数是什么？",
            "gold_answer_summary": "DBSCAN 的核心参数是 eps 和 MinPts。",
            "gold_entities": ["DBSCAN", "eps", "MinPts"],
            "gold_text_unit_ids": ["d244f9016ac8"],
            "must_cite_terms": ["eps", "MinPts"],
            "negative_terms": [],
        },
        {
            "id": "Q002",
            "category": "relation_reasoning",
            "question": "为什么 eps 会影响聚类结果？",
            "gold_answer_summary": "eps 决定邻域半径，会影响密度可达关系。",
            "gold_entities": ["eps"],
            "gold_text_unit_ids": ["aaaaaaaaaaaa"],
            "must_cite_terms": ["邻域"],
            "negative_terms": [],
        },
    ]
    path.write_text(
        "\n".join(json.dumps(item, ensure_ascii=False) for item in items) + "\n",
        encoding="utf-8",
    )


def _write_run(tmp_path: Path, *, index_output_dir: Path | None = None) -> tuple[Path, Path]:
    test_set = tmp_path / "qa_test_set.jsonl"
    _write_test_set(test_set)
    run_dir = tmp_path / "run"
    raw_dir = run_dir / "raw"
    raw_dir.mkdir(parents=True)
    meta = {"modes": ["graphrag-local-search:latest"], "test_set_path": str(test_set)}
    if index_output_dir is not None:
        meta["index_output_dir"] = str(index_output_dir)
    (run_dir / "run_meta.json").write_text(json.dumps(meta), encoding="utf-8")
    (raw_dir / "Q001.json").write_text(
        json.dumps(
            {
                "question_id": "Q001",
                "modes": {
                    "graphrag-local-search:latest": {
                        "answer": "DBSCAN 的核心参数是 eps 和 MinPts。[Data: Text Units (d244f9016ac8)]",
                        "elapsed_seconds": 1.5,
                    }
                },
            },
            ensure_ascii=False,
        ),
        encoding="utf-8",
    )
    return run_dir, test_set


def _write_text_units(index_output_dir: Path) -> Path:
    index_output_dir.mkdir(parents=True)
    text_units_path = index_output_dir / "text_units.parquet"
    pd.DataFrame(
        [{"id": "d244f9016ac8abcdef", "human_readable_id": 1, "text": "DBSCAN 的核心参数是 eps 和 MinPts。"}]
    ).to_parquet(text_units_path)
    return text_units_path


def _patched_semantic():
    return patch(
        "graphrag_pipeline.scripts.qa_eval.algorithmic_scorer.score_semantic_similarity",
        return_value={
            "semantic_coverage_precision": 0.9,
            "semantic_coverage_recall": 0.8,
            "semantic_coverage_f1": 0.8471,
            "rouge_lsum": 0.8,
            "keyword_recall": 1.0,
            "bertscore_precision": None,
            "bertscore_recall": None,
            "bertscore_f1": None,
            "semantic_model": "mock",
        },
    )


def test_score_run_algorithmically_writes_outputs(tmp_path: Path):
    run_dir, test_set = _write_run(tmp_path)
    with patch(
        "graphrag_pipeline.scripts.qa_eval.algorithmic_scorer.score_semantic_similarity",
        return_value={
            "semantic_coverage_precision": 0.9,
            "semantic_coverage_recall": 0.8,
            "semantic_coverage_f1": 0.8471,
            "rouge_lsum": 0.8,
            "keyword_recall": 1.0,
            "bertscore_precision": None,
            "bertscore_recall": None,
            "bertscore_f1": None,
            "semantic_model": "BAAI/bge-m3",
        },
    ):
        summary = score_run_algorithmically(run_dir, test_set_path=test_set)

    assert summary["rows"][0]["semantic_coverage_f1"] == 0.8471
    assert summary["rows"][0]["citation_recall_at_3"] == 1.0
    assert summary["rows"][0]["citation_rr"] == 1.0
    assert summary["rows"][0]["entity_hit_rate"] == 1.0
    assert summary["rows"][0]["elapsed_seconds"] == 1.5
    assert summary["rows"][0]["error_count"] == 0
    assert 0 < summary["rows"][0]["effective_score_experimental"] <= 1
    assert (run_dir / "algorithmic_scoring.csv").exists()
    assert (run_dir / "algorithmic_scoring.json").exists()
    assert (run_dir / "algorithmic_scoring.md").exists()


def test_score_run_algorithmically_surfaces_hybrid_diagnostics(tmp_path: Path):
    test_set = tmp_path / "qa_test_set.jsonl"
    _write_test_set(test_set)
    run_dir = tmp_path / "run"
    raw_dir = run_dir / "raw"
    raw_dir.mkdir(parents=True)
    mode = "graphrag-hybrid-v0-search:latest"
    (run_dir / "run_meta.json").write_text(
        json.dumps({"modes": [mode], "test_set_path": str(test_set), "question_ids": ["Q001"]}),
        encoding="utf-8",
    )
    (raw_dir / "Q001.json").write_text(
        json.dumps(
            {
                "question_id": "Q001",
                "modes": {
                    mode: {
                        "answer": "DBSCAN 的核心参数是 eps 和 MinPts。[Data: Text Units (d244f9016ac8)]",
                        "elapsed_seconds": 1.5,
                        "hybrid_diagnostics": {
                            "used_local_fallback": True,
                            "guardrail_score": 0.42,
                            "low_evidence_count": 3,
                            "high_evidence_count": 2,
                            "synthesis_attempted": True,
                            "fallback_reasons": ["basic_missing_data_citation"],
                            "fused_evidence_refs": ["d244f9016ac8", "xxxxxxxxxxxx"],
                            "fused_evidence_sources": {"bm25": 2, "basic-citation": 1},
                            "synthesis_reason": "basic_missing_data_citation",
                            "local_fallback_enabled": False,
                        },
                    }
                },
            },
            ensure_ascii=False,
        ),
        encoding="utf-8",
    )

    with _patched_semantic():
        summary = score_run_algorithmically(run_dir, test_set_path=test_set)

    row = summary["rows"][0]
    assert row["hybrid_used_local_fallback"] is True
    assert row["hybrid_guardrail_score"] == 0.42
    assert row["hybrid_low_evidence_count"] == 3
    assert row["hybrid_high_evidence_count"] == 2
    assert row["hybrid_synthesis_attempted"] is True
    assert row["hybrid_fallback_reasons"] == "basic_missing_data_citation"
    assert row["hybrid_fused_evidence_refs"] == "d244f9016ac8,xxxxxxxxxxxx"
    assert row["hybrid_fused_evidence_sources"] == "bm25=2,basic-citation=1"
    assert row["hybrid_synthesis_reason"] == "basic_missing_data_citation"
    assert row["hybrid_local_fallback_enabled"] is False
    assert row["selected_evidence_recall_at_3"] == 1.0
    assert row["selected_evidence_rr"] == 1.0
    assert row["selected_evidence_ndcg_at_5"] == 1.0
    csv = pd.read_csv(run_dir / "algorithmic_scoring.csv")
    assert "hybrid_guardrail_score" in csv.columns
    assert "hybrid_fallback_reasons" in csv.columns
    assert "selected_evidence_recall_at_3" in csv.columns


def test_score_run_algorithmically_leaves_selected_evidence_metrics_empty_for_non_hybrid(tmp_path: Path):
    run_dir, test_set = _write_run(tmp_path)

    with _patched_semantic():
        summary = score_run_algorithmically(run_dir, test_set_path=test_set)

    row = summary["rows"][0]
    assert row["selected_evidence_recall_at_3"] is None
    assert row["selected_evidence_rr"] is None
    assert row["selected_evidence_ndcg_at_5"] is None


def test_score_run_algorithmically_filters_items_by_run_meta_question_ids(tmp_path: Path):
    test_set = tmp_path / "qa_test_set.jsonl"
    _write_two_item_test_set(test_set)
    run_dir = tmp_path / "run"
    raw_dir = run_dir / "raw"
    raw_dir.mkdir(parents=True)
    (run_dir / "run_meta.json").write_text(
        json.dumps(
            {
                "modes": ["local"],
                "question_ids": ["Q001"],
                "test_set_path": str(test_set),
            }
        ),
        encoding="utf-8",
    )
    (raw_dir / "Q001.json").write_text(
        json.dumps({"question_id": "Q001", "modes": {"local": {"answer": "DBSCAN eps", "elapsed_seconds": 1.0}}}),
        encoding="utf-8",
    )

    with _patched_semantic():
        summary = score_run_algorithmically(run_dir, test_set_path=test_set)

    assert [row["question_id"] for row in summary["rows"]] == ["Q001"]


def test_score_run_algorithmically_falls_back_to_raw_question_ids_for_partial_run(tmp_path: Path):
    test_set = tmp_path / "qa_test_set.jsonl"
    _write_two_item_test_set(test_set)
    run_dir = tmp_path / "run"
    raw_dir = run_dir / "raw"
    raw_dir.mkdir(parents=True)
    (run_dir / "run_meta.json").write_text(
        json.dumps({"modes": ["local"], "test_set_path": str(test_set)}),
        encoding="utf-8",
    )
    (raw_dir / "Q001.json").write_text(
        json.dumps({"question_id": "Q001", "modes": {"local": {"answer": "DBSCAN eps", "elapsed_seconds": 1.0}}}),
        encoding="utf-8",
    )

    with _patched_semantic():
        summary = score_run_algorithmically(run_dir, test_set_path=test_set)

    assert [row["question_id"] for row in summary["rows"]] == ["Q001"]


def test_score_run_algorithmically_marks_missing_aggregated_mode_as_error(tmp_path: Path):
    test_set = tmp_path / "qa_test_set.jsonl"
    _write_test_set(test_set)
    run_dir = tmp_path / "run"
    raw_dir = run_dir / "raw"
    raw_dir.mkdir(parents=True)
    (run_dir / "run_meta.json").write_text(
        json.dumps({"modes": ["local", "global"], "test_set_path": str(test_set), "question_ids": ["Q001"]}),
        encoding="utf-8",
    )
    (raw_dir / "Q001.json").write_text(
        json.dumps({"question_id": "Q001", "modes": {"local": {"answer": "DBSCAN eps", "elapsed_seconds": 1.0}}}),
        encoding="utf-8",
    )

    with _patched_semantic():
        summary = score_run_algorithmically(run_dir, test_set_path=test_set)

    rows_by_mode = {row["mode"]: row for row in summary["rows"]}
    assert rows_by_mode["local"]["error_count"] == 0
    assert rows_by_mode["global"]["answer_chars"] == 0
    assert rows_by_mode["global"]["error_count"] == 1
    assert rows_by_mode["global"]["error_type"] == "missing_mode"
    assert "mode is missing" in rows_by_mode["global"]["error_message"]


def test_score_run_algorithmically_marks_missing_raw_as_error(tmp_path: Path):
    test_set = tmp_path / "qa_test_set.jsonl"
    _write_test_set(test_set)
    run_dir = tmp_path / "run"
    (run_dir / "raw").mkdir(parents=True)
    (run_dir / "run_meta.json").write_text(
        json.dumps({"modes": ["local"], "test_set_path": str(test_set), "question_ids": ["Q001"]}),
        encoding="utf-8",
    )

    with _patched_semantic():
        summary = score_run_algorithmically(run_dir, test_set_path=test_set)

    row = summary["rows"][0]
    assert row["answer_chars"] == 0
    assert row["error_count"] == 1
    assert row["error_type"] == "missing_raw"
    assert "raw answer file is missing" in row["error_message"]


def test_score_run_algorithmically_accepts_semantic_config_for_cheap_baseline(tmp_path: Path):
    run_dir, test_set = _write_run(tmp_path)

    with patch("graphrag_pipeline.scripts.qa_eval.algorithmic_scorer.score_semantic_similarity") as scorer:
        scorer.return_value = {
            "semantic_coverage_precision": 0.0,
            "semantic_coverage_recall": 0.0,
            "semantic_coverage_f1": 0.0,
            "rouge_lsum": 0.8,
            "keyword_recall": 1.0,
            "bertscore_precision": None,
            "bertscore_recall": None,
            "bertscore_f1": None,
            "semantic_model": "cheap-baseline-only",
        }
        score_run_algorithmically(
            run_dir,
            test_set_path=test_set,
            semantic_config=SemanticScoringConfig(enable_bge_m3=False),
        )

    assert scorer.call_args.kwargs["config"].enable_bge_m3 is False


def test_run_loader_exposes_public_helpers(tmp_path: Path):
    run_dir, test_set = _write_run(tmp_path)

    assert list(load_test_set(test_set)) == ["Q001"]
    raw = load_raw_mode_answer(run_dir, "Q001", "graphrag-local-search:latest")
    assert raw.answer.startswith("DBSCAN")
    assert raw.elapsed_seconds == 1.5


def test_load_raw_mode_answer_keeps_legacy_safe_mode_compatibility(tmp_path: Path):
    test_set = tmp_path / "qa_test_set.jsonl"
    _write_test_set(test_set)
    run_dir = tmp_path / "run"
    raw_dir = run_dir / "raw"
    raw_dir.mkdir(parents=True)
    (run_dir / "run_meta.json").write_text(
        json.dumps({"modes": ["graphrag/local:latest"], "test_set_path": str(test_set)}),
        encoding="utf-8",
    )
    (raw_dir / "Q001_graphrag-local-latest.json").write_text(
        json.dumps({"answer": "legacy answer", "elapsed_seconds": 2.25}),
        encoding="utf-8",
    )

    raw = load_raw_mode_answer(run_dir, "Q001", "graphrag/local:latest")

    assert raw.answer == "legacy answer"
    assert raw.elapsed_seconds == 2.25


def test_resolve_text_units_path_prefers_run_meta_index_output_dir_and_audits_collisions(tmp_path: Path):
    index_output_dir = tmp_path / "index-output"
    text_units_path = index_output_dir / "text_units.parquet"
    index_output_dir.mkdir()
    pd.DataFrame(
        [
            {"id": "abcdefabcdef1111", "text": "第一段"},
            {"id": "abcdefabcdef2222", "text": "第二段"},
        ]
    ).to_parquet(text_units_path)
    run_dir, _ = _write_run(tmp_path, index_output_dir=index_output_dir)

    assert resolve_text_units_path(run_dir) == text_units_path
    assert audit_text_unit_prefix_collisions(text_units_path) == {
        "abcdefabcdef": ["abcdefabcdef1111", "abcdefabcdef2222"]
    }


def test_score_run_algorithmically_loads_data_citation_lookup_from_run_meta_index_output_dir(tmp_path: Path):
    index_output_dir = tmp_path / "index-output"
    text_units_path = _write_text_units(index_output_dir)
    run_dir, test_set = _write_run(tmp_path, index_output_dir=index_output_dir)

    with patch(
        "graphrag_pipeline.scripts.qa_eval.algorithmic_scorer.score_semantic_similarity",
        return_value={
            "semantic_coverage_precision": 0.9,
            "semantic_coverage_recall": 0.8,
            "semantic_coverage_f1": 0.8471,
            "rouge_lsum": 0.8,
            "keyword_recall": 1.0,
            "bertscore_precision": None,
            "bertscore_recall": None,
            "bertscore_f1": None,
            "semantic_model": "mock",
        },
    ), patch(
        "graphrag_pipeline.scripts.qa_eval.algorithmic_scorer.load_data_citation_lookup",
    ) as load_lookup:
        summary = score_run_algorithmically(run_dir, test_set_path=test_set)

    load_lookup.assert_called_once_with(text_units_path)
    assert summary["rows"][0]["citation_rr"] == 1.0


def test_score_run_algorithmically_rejects_prefix_collisions(tmp_path: Path):
    index_output_dir = tmp_path / "index-output"
    index_output_dir.mkdir()
    pd.DataFrame(
        [
            {"id": "abcdefabcdef1111", "human_readable_id": 1, "text": "第一段"},
            {"id": "abcdefabcdef2222", "human_readable_id": 2, "text": "第二段"},
        ]
    ).to_parquet(index_output_dir / "text_units.parquet")
    run_dir, test_set = _write_run(tmp_path, index_output_dir=index_output_dir)

    with pytest.raises(ValueError, match="prefix collision"):
        score_run_algorithmically(run_dir, test_set_path=test_set)
