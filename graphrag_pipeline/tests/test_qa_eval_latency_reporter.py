from __future__ import annotations

import json
from pathlib import Path

from graphrag_pipeline.scripts.qa_eval.latency_reporter import generate_latency_report


def _write_json(path: Path, payload: dict) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(json.dumps(payload, ensure_ascii=False, indent=2), encoding="utf-8")


def test_generate_latency_report_counts_success_errors_and_timeout_like_failures(tmp_path: Path):
    run_dir = tmp_path / "rid"
    _write_json(
        run_dir / "run_meta.json",
        {
            "run_id": "rid",
            "modes": ["graphrag-local-search:latest", "graphrag-drift-search:latest"],
            "total_items": 2,
        },
    )
    _write_json(
        run_dir / "raw" / "Q001.json",
        {
            "id": "Q001",
            "category": "factual_lookup",
            "modes": {
                "graphrag-local-search:latest": {"answer": "ok", "elapsed_seconds": 1.2},
                "graphrag-drift-search:latest": {
                    "error": "Read timed out",
                    "error_type": "ReadTimeout",
                    "elapsed_seconds": 90.0,
                },
            },
        },
    )
    _write_json(
        run_dir / "raw" / "Q002.json",
        {
            "id": "Q002",
            "category": "global_overview",
            "modes": {
                "graphrag-local-search:latest": {"answer": "ok", "elapsed_seconds": 2.8},
                "graphrag-drift-search:latest": {"answer": "ok", "elapsed_seconds": 12.5},
            },
        },
    )

    payload = generate_latency_report(run_dir)

    local = payload["per_mode"]["graphrag-local-search:latest"]
    drift = payload["per_mode"]["graphrag-drift-search:latest"]
    assert local["success_count"] == 2
    assert local["error_count"] == 0
    assert local["mean_seconds"] == 2.0
    assert drift["success_count"] == 1
    assert drift["error_count"] == 1
    assert drift["timeout_like_error_count"] == 1
    assert drift["max_seconds"] == 90.0
    assert (run_dir / "latency_breakdown.json").exists()
    assert (run_dir / "latency_breakdown.csv").exists()
    assert "graphrag-drift-search:latest" in (run_dir / "latency_breakdown.md").read_text(
        encoding="utf-8"
    )
