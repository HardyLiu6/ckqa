from __future__ import annotations

import json
from pathlib import Path

from graphrag_pipeline.scripts.qa_eval.hybrid_diagnostics_report import (
    build_hybrid_diagnostics_rows,
    write_hybrid_diagnostics_report,
)


def _write_raw(run_dir: Path, question_id: str, diagnostics: dict[str, object]) -> None:
    raw_dir = run_dir / "raw"
    raw_dir.mkdir(parents=True, exist_ok=True)
    (raw_dir / f"{question_id}.json").write_text(
        json.dumps(
            {
                "question_id": question_id,
                "category": "relation_reasoning",
                "question": "进程和线程有什么区别？",
                "modes": {
                    "graphrag-hybrid-v0-search:latest": {
                        "answer": "线程是调度单位，进程是资源拥有单位。",
                        "elapsed_seconds": 12.5,
                        "hybrid_diagnostics": diagnostics,
                    }
                },
            },
            ensure_ascii=False,
        ),
        encoding="utf-8",
    )


def test_build_hybrid_diagnostics_rows_explains_no_fallback(tmp_path: Path):
    run_dir = tmp_path / "run"
    _write_raw(
        run_dir,
        "Q009",
        {
            "layer": "mixed",
            "classifier_confidence": 0.95,
            "used_local_fallback": False,
            "guardrail_status": "pass",
            "guardrail_score": 1.0,
            "low_evidence_count": 8,
            "high_evidence_count": 1,
            "fused_evidence_refs": ["aaaabbbbcccc", "149", "+more", "ddddeeeeffff"],
            "fused_evidence_sources": {"bm25": 2, "basic-citation": 1},
            "synthesis_reason": "",
            "local_fallback_enabled": False,
            "errors": [],
        },
    )

    rows = build_hybrid_diagnostics_rows([run_dir])

    assert rows[0]["question_id"] == "Q009"
    assert rows[0]["bm25_evidence_count"] == 8
    assert rows[0]["fused_evidence_count"] == 2
    assert rows[0]["fused_evidence_sources"] == "bm25=2,basic-citation=1"
    assert rows[0]["local_fallback_enabled"] is False
    assert rows[0]["used_local_fallback"] is False
    assert "guardrail_status=pass" in rows[0]["fallback_reason"]
    assert "未触发 Local fallback" in rows[0]["fallback_reason"]


def test_write_hybrid_diagnostics_report_renders_table(tmp_path: Path):
    run_dir = tmp_path / "run"
    _write_raw(
        run_dir,
        "Q001",
        {
            "layer": "low",
            "classifier_confidence": 0.75,
            "used_local_fallback": True,
            "guardrail_status": "pass",
            "guardrail_score": 0.8,
            "low_evidence_count": 4,
            "high_evidence_count": 2,
            "fused_evidence_refs": ["d244f9016ac8"],
            "fused_evidence_sources": {"bm25": 1},
            "synthesis_reason": "basic_missing_data_citation",
            "local_fallback_enabled": False,
            "fallback_reasons": ["synthesis_missing_data_citation"],
            "errors": [],
        },
    )

    output = write_hybrid_diagnostics_report([run_dir], output_path=tmp_path / "report.md")

    text = output.read_text(encoding="utf-8")
    assert "# Hybrid v0 Diagnostics Report" in text
    assert "Q001" in text
    assert "已触发 Local fallback" in text
    assert "basic_missing_data_citation" in text
    assert "bm25=1" in text
    assert "synthesis_missing_data_citation" in text
    assert "| Q001 |" in text
