from __future__ import annotations

import json
from pathlib import Path

from graphrag_pipeline.scripts.qa_eval.baseline_reporter import generate_report
from graphrag_pipeline.scripts.qa_eval.manual_review_template import write_manual_review_template
from graphrag_pipeline.scripts.qa_eval.run_baseline_eval import BASELINE_MODES


def _seed_run_with_two_categories(tmp_path: Path) -> Path:
    run_dir = tmp_path / "rid"
    raw = run_dir / "raw"
    raw.mkdir(parents=True)
    item1 = {
        "id": "Q001",
        "category": "factual_lookup",
        "question": "DBSCAN 的两个核心超参数是什么？",
        "gold_answer_summary": "eps 和 MinPts",
        "gold_entities": ["DBSCAN", "eps", "MinPts"],
        "gold_text_unit_ids": ["d244f9016ac8"],
        "must_cite_terms": ["eps"],
        "negative_terms": [],
        "modes": {
            mode: {
                "answer": "DBSCAN 的核心超参数是 eps 和 MinPts。",
                "total_tokens": 10,
                "elapsed_seconds": 0.1,
            }
            for mode in BASELINE_MODES
        },
    }
    item2 = {
        "id": "Q002",
        "category": "global_overview",
        "question": "课程方法论主线是什么？",
        "gold_answer_summary": "数据到特征到模型到评估",
        "gold_entities": ["数据", "特征", "模型", "评估"],
        "gold_text_unit_ids": [],
        "must_cite_terms": [],
        "negative_terms": [],
        "modes": {
            mode: {
                "answer": "整体方法论是数据到特征到模型到评估。",
                "total_tokens": 30,
                "elapsed_seconds": 1.0,
            }
            for mode in BASELINE_MODES
        },
    }
    (raw / "Q001.json").write_text(json.dumps(item1, ensure_ascii=False), encoding="utf-8")
    (raw / "Q002.json").write_text(json.dumps(item2, ensure_ascii=False), encoding="utf-8")
    (run_dir / "run_meta.json").write_text(
        json.dumps(
            {
                "run_id": "rid",
                "index_run_label": "course-x",
                "total_items": 2,
                "modes": list(BASELINE_MODES),
            },
            ensure_ascii=False,
        ),
        encoding="utf-8",
    )
    return run_dir


def test_generate_report_writes_csv_and_md_with_categories(tmp_path: Path) -> None:
    run_dir = _seed_run_with_two_categories(tmp_path)
    generate_report(run_dir)

    md = (run_dir / "scoring.md").read_text(encoding="utf-8")
    csv = (run_dir / "combined.csv").read_text(encoding="utf-8")

    assert "factual_lookup" in md
    assert "global_overview" in md
    assert "entity_hit_rate" in md
    assert "graphrag-local-search:latest" in csv
    assert "semantic_correctness" in csv


def test_manual_review_template_injects_metric_means_and_routing_slots(tmp_path: Path) -> None:
    run_dir = _seed_run_with_two_categories(tmp_path)
    generate_report(run_dir)
    hypotheses_path = tmp_path / "hypotheses.md"
    hypotheses_path.write_text(
        "# 评测假设\n\n- H1: factual_lookup 类问题，basic / local 在 entity_hit_rate 上优于 global\n- H2: ...\n",
        encoding="utf-8",
    )

    write_manual_review_template(run_dir, hypotheses_path=hypotheses_path)
    text = (run_dir / "manual_review.md").read_text(encoding="utf-8")

    assert "H1" in text
    assert "假设验证" in text
    assert "路由建议" in text
    assert "factual_lookup" in text
    assert "entity_hit_rate" in text
