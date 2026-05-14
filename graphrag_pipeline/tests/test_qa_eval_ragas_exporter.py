from __future__ import annotations

import json
import subprocess
import sys
from pathlib import Path

from graphrag_pipeline.scripts.qa_eval.ragas_exporter import export_ragas_dataset


def _write_tmp_run(tmp_path: Path) -> Path:
    test_set = tmp_path / "qa_test_set.jsonl"
    test_set.write_text(
        json.dumps(
            {
                "id": "Q001",
                "category": "factual_lookup",
                "question": "DBSCAN 的核心参数是什么？",
                "gold_answer_summary": "DBSCAN 的核心参数是 eps 和 MinPts。",
                "gold_entities": ["DBSCAN", "eps", "MinPts"],
                "gold_text_unit_ids": ["d244f9016ac8abcdef"],
                "must_cite_terms": ["eps", "MinPts"],
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
                "modes": ["graphrag-local-search:latest"],
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
                    "graphrag-local-search:latest": {
                        "answer": "DBSCAN 的核心参数是 eps 和 MinPts。",
                        "elapsed_seconds": 1.25,
                    }
                },
            },
            ensure_ascii=False,
        ),
        encoding="utf-8",
    )
    return run_dir


def test_export_ragas_dataset_writes_jsonl_from_aggregated_raw(tmp_path: Path):
    run_dir = _write_tmp_run(tmp_path)

    output = export_ragas_dataset(
        run_dir,
        contexts_by_question={"Q001": ["教材片段 A", "教材片段 B"]},
    )

    assert output == run_dir / "ragas_dataset.jsonl"
    rows = [json.loads(line) for line in output.read_text(encoding="utf-8").splitlines()]
    assert rows == [
        {
            "question_id": "Q001",
            "mode": "graphrag-local-search:latest",
            "question": "DBSCAN 的核心参数是什么？",
            "answer": "DBSCAN 的核心参数是 eps 和 MinPts。",
            "contexts": ["教材片段 A", "教材片段 B"],
            "ground_truth": "DBSCAN 的核心参数是 eps 和 MinPts。",
            "gold_text_unit_ids": ["d244f9016ac8"],
        }
    ]


def test_ragas_cli_run_ragas_missing_dependency_does_not_fail(tmp_path: Path):
    run_dir = _write_tmp_run(tmp_path)

    result = subprocess.run(
        [
            sys.executable,
            "-m",
            "graphrag_pipeline.scripts.qa_eval.ragas_exporter",
            "--run-dir",
            str(run_dir),
            "--run-ragas",
        ],
        cwd=Path(__file__).resolve().parents[2],
        text=True,
        capture_output=True,
        check=False,
    )

    assert result.returncode == 0
    assert (run_dir / "ragas_dataset.jsonl").exists()
    assert "ragas_dataset.jsonl" in result.stdout
