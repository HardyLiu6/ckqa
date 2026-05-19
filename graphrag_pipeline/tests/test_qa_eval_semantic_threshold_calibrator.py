from __future__ import annotations

import json
from pathlib import Path
from unittest.mock import patch

import numpy as np

from graphrag_pipeline.scripts.qa_eval.semantic_threshold_calibrator import calibrate_semantic_thresholds


def _write_run(tmp_path: Path) -> Path:
    test_set = tmp_path / "qa_test_set.jsonl"
    test_set.write_text(
        json.dumps(
            {
                "id": "Q001",
                "category": "factual_lookup",
                "question": "DBSCAN 的核心参数是什么？",
                "gold_answer_summary": "DBSCAN 的核心参数是 eps 和 MinPts。",
                "gold_entities": ["DBSCAN"],
                "gold_text_unit_ids": ["d244f9016ac8abcdef"],
                "must_cite_terms": ["eps"],
                "negative_terms": [],
            },
            ensure_ascii=False,
        )
        + "\n",
        encoding="utf-8",
    )
    run_dir = tmp_path / "run"
    raw_dir = run_dir / "raw"
    raw_dir.mkdir(parents=True)
    (run_dir / "run_meta.json").write_text(
        json.dumps(
            {
                "modes": ["local", "global"],
                "question_ids": ["Q001"],
                "test_set_path": str(test_set),
            },
            ensure_ascii=False,
        ),
        encoding="utf-8",
    )
    (raw_dir / "Q001.json").write_text(
        json.dumps(
            {
                "question_id": "Q001",
                "modes": {
                    "local": {"answer": "DBSCAN 的核心参数是 eps 和 MinPts。", "elapsed_seconds": 1.0},
                    "global": {"answer": "", "error": "timeout", "elapsed_seconds": 360.0},
                },
            },
            ensure_ascii=False,
        ),
        encoding="utf-8",
    )
    return run_dir


def test_calibrate_semantic_thresholds_writes_markdown_without_real_model(tmp_path: Path):
    run_dir = _write_run(tmp_path)

    with patch(
        "graphrag_pipeline.scripts.qa_eval.semantic_threshold_calibrator._similarity_matrix",
        return_value=np.asarray([[0.66]], dtype=np.float32),
    ):
        summary = calibrate_semantic_thresholds(
            run_dir,
            thresholds=[0.5, 0.7],
            max_questions=1,
        )

    output = run_dir / "semantic_threshold_calibration.md"
    report = output.read_text(encoding="utf-8")
    assert summary["pairs_total"] == 2
    assert summary["pairs_evaluated"] == 1
    assert summary["pairs_skipped"] == 1
    assert "| 0.50 |" in report
    assert "| 0.70 |" in report
    assert "默认 `0.62`" in report
