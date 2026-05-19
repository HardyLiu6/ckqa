from __future__ import annotations

import json
from pathlib import Path

from graphrag_pipeline.scripts.qa_eval.graphrag_client import QueryResult
from graphrag_pipeline.scripts.qa_eval.test_set_validator import ValidationReport


def _write_single_item_test_set(path: Path) -> None:
    payload = {
        "id": "Q001",
        "category": "factual_lookup",
        "question": "课程的第 1 章第 2 节标题是什么？",
        "gold_answer_summary": "数据采集与预处理",
        "gold_entities": ["数据采集"],
        "gold_text_unit_ids": ["d244f9016ac84a55a7435cb6"],
        "must_cite_terms": ["第 1 章"],
        "negative_terms": ["错误章节"],
    }
    path.write_text(json.dumps(payload, ensure_ascii=False) + "\n", encoding="utf-8")


class _FakeClient:
    init_kwargs: list[dict[str, object]] = []
    calls: list[tuple[str, str]] = []

    def __init__(self, **kwargs):
        self.init_kwargs.append(kwargs)

    def query(self, *, model: str, prompt: str) -> QueryResult:
        self.calls.append((model, prompt))
        return QueryResult(
            answer=f"{model} answer",
            total_tokens=42,
            elapsed_seconds=0.25,
            raw={
                "model": model,
                "ok": True,
                "hybrid_diagnostics": {
                    "used_local_fallback": True,
                    "guardrail_score": 0.42,
                    "low_evidence_count": 3,
                    "high_evidence_count": 2,
                },
            },
        )


def test_hybrid_v0_runner_writes_algorithmic_compatible_run_payload(tmp_path, monkeypatch):
    from graphrag_pipeline.scripts.qa_eval import run_hybrid_v0_eval
    from graphrag_pipeline.scripts.qa_eval.run_hybrid_v0_eval import (
        HYBRID_V0_MODEL,
        HybridV0EvalRunner,
    )

    test_set = tmp_path / "test_set.jsonl"
    _write_single_item_test_set(test_set)
    monkeypatch.setattr(
        run_hybrid_v0_eval,
        "validate_jsonl",
        lambda path: ValidationReport(ok=True, total=1),
    )
    _FakeClient.init_kwargs = []
    _FakeClient.calls = []
    monkeypatch.setattr(run_hybrid_v0_eval, "OpenAICompatibleClient", _FakeClient)

    run_dir = HybridV0EvalRunner(
        test_set_path=test_set,
        output_root=tmp_path / "runs",
        run_id="hybrid-run",
        base_url="http://127.0.0.1:8012",
        request_timeout_seconds=5,
        max_retries=1,
        backoff_seconds=0,
    ).run()

    assert run_dir == tmp_path / "runs" / "hybrid-run"
    assert _FakeClient.init_kwargs == [
        {
            "base_url": "http://127.0.0.1:8012",
            "request_timeout_seconds": 5,
            "max_retries": 1,
            "backoff_seconds": 0,
            "allow_arbitrary_models": True,
        }
    ]
    assert _FakeClient.calls == [(HYBRID_V0_MODEL, "课程的第 1 章第 2 节标题是什么？")]

    meta = json.loads((run_dir / "run_meta.json").read_text(encoding="utf-8"))
    assert meta["run_id"] == "hybrid-run"
    assert meta["index_run_label"] == "hybrid-v0"
    assert meta["index_output_dir"] is None
    assert meta["base_url"] == "http://127.0.0.1:8012"
    assert meta["modes"] == [HYBRID_V0_MODEL]
    assert meta["mode_labels"] == {HYBRID_V0_MODEL: "hybrid_v0"}
    assert meta["total_items"] == 1
    assert meta["source_total_questions"] == 1

    raw = json.loads((run_dir / "raw" / "Q001.json").read_text(encoding="utf-8"))
    assert raw["question_id"] == "Q001"
    assert list(raw["modes"]) == [HYBRID_V0_MODEL]
    assert raw["modes"][HYBRID_V0_MODEL]["answer"] == f"{HYBRID_V0_MODEL} answer"
    assert raw["modes"][HYBRID_V0_MODEL]["hybrid_diagnostics"] == {
        "used_local_fallback": True,
        "guardrail_score": 0.42,
        "low_evidence_count": 3,
        "high_evidence_count": 2,
    }
    assert raw["modes"][HYBRID_V0_MODEL]["raw"] == {
        "model": HYBRID_V0_MODEL,
        "ok": True,
        "hybrid_diagnostics": {
            "used_local_fallback": True,
            "guardrail_score": 0.42,
            "low_evidence_count": 3,
            "high_evidence_count": 2,
        },
    }
