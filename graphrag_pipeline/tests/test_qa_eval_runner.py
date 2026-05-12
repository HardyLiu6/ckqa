from __future__ import annotations

import json
import time
from pathlib import Path

import pytest

from graphrag_pipeline.scripts.qa_eval.graphrag_client import QueryResult
from graphrag_pipeline.scripts.qa_eval.test_set_validator import ValidationReport


BASELINE_MODELS = [
    "graphrag-local-search:latest",
    "graphrag-global-search:latest",
    "graphrag-drift-search:latest",
    "graphrag-basic-search:latest",
]


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


def _write_two_item_test_set(path: Path) -> None:
    payloads = []
    for index in range(1, 3):
        payloads.append(
            {
                "id": f"Q{index:03d}",
                "category": "factual_lookup",
                "question": f"第 {index} 个问题是什么？",
                "gold_answer_summary": "参考答案",
                "gold_entities": ["参考"],
                "gold_text_unit_ids": ["d244f9016ac84a55a7435cb6"],
                "must_cite_terms": [],
                "negative_terms": [],
            }
        )
    path.write_text(
        "".join(json.dumps(payload, ensure_ascii=False) + "\n" for payload in payloads),
        encoding="utf-8",
    )


class _FakeClient:
    calls: list[tuple[str, str]] = []
    failures: set[str] = set()

    def __init__(self, **kwargs):
        self.kwargs = kwargs

    def query(self, *, model: str, prompt: str) -> QueryResult:
        self.calls.append((model, prompt))
        if model in self.failures:
            raise RuntimeError(f"{model} boom")
        return QueryResult(
            answer=f"{model} answer",
            total_tokens=42,
            elapsed_seconds=0.25,
            raw={"model": model, "ok": True},
        )


def test_runner_writes_meta_and_raw_results_with_stable_modes(tmp_path, monkeypatch):
    from graphrag_pipeline.scripts.qa_eval import run_baseline_eval
    from graphrag_pipeline.scripts.qa_eval.run_baseline_eval import BaselineEvalRunner

    test_set = tmp_path / "test_set.jsonl"
    _write_single_item_test_set(test_set)
    monkeypatch.setattr(
        run_baseline_eval,
        "validate_jsonl",
        lambda path: ValidationReport(ok=True, total=1),
    )
    _FakeClient.calls = []
    _FakeClient.failures = set()
    monkeypatch.setattr(run_baseline_eval, "OpenAICompatibleClient", _FakeClient)

    runner = BaselineEvalRunner(
        test_set_path=test_set,
        output_root=tmp_path / "runs",
        run_id="run-001",
        index_run_label="index-A",
        base_url="http://127.0.0.1:8000",
        request_timeout_seconds=5,
        max_retries=1,
        backoff_seconds=0,
    )

    run_dir = runner.run()

    assert run_dir == tmp_path / "runs" / "run-001"
    meta = json.loads((run_dir / "run_meta.json").read_text(encoding="utf-8"))
    assert meta["run_id"] == "run-001"
    assert meta["test_set_path"] == str(test_set)
    assert meta["index_run_label"] == "index-A"
    assert meta["base_url"] == "http://127.0.0.1:8000"
    assert meta["modes"] == BASELINE_MODELS
    assert meta["mode_labels"] == {
        "graphrag-local-search:latest": "local",
        "graphrag-global-search:latest": "global",
        "graphrag-drift-search:latest": "drift",
        "graphrag-basic-search:latest": "basic",
    }
    assert meta["total_items"] == 1
    assert meta["total_questions"] == 1
    assert isinstance(meta["created_at"], str)

    raw = json.loads((run_dir / "raw" / "Q001.json").read_text(encoding="utf-8"))
    assert raw["id"] == "Q001"
    assert raw["question_id"] == "Q001"
    assert raw["category"] == "factual_lookup"
    assert raw["question"] == "课程的第 1 章第 2 节标题是什么？"
    assert raw["gold_answer_summary"] == "数据采集与预处理"
    assert raw["gold_entities"] == ["数据采集"]
    assert raw["gold_text_unit_ids"] == ["d244f9016ac8"]
    assert raw["must_cite_terms"] == ["第 1 章"]
    assert raw["negative_terms"] == ["错误章节"]
    assert list(raw["modes"]) == BASELINE_MODELS
    assert raw["modes"]["graphrag-local-search:latest"]["answer"] == (
        "graphrag-local-search:latest answer"
    )
    assert raw["modes"]["graphrag-local-search:latest"]["total_tokens"] == 42
    assert raw["modes"]["graphrag-local-search:latest"]["elapsed_seconds"] == 0.25
    assert raw["modes"]["graphrag-local-search:latest"]["raw"] == {
        "model": "graphrag-local-search:latest",
        "ok": True,
    }
    assert [call[0] for call in _FakeClient.calls] == BASELINE_MODELS


def test_runner_records_single_mode_error_and_continues(tmp_path, monkeypatch):
    from graphrag_pipeline.scripts.qa_eval import run_baseline_eval
    from graphrag_pipeline.scripts.qa_eval.run_baseline_eval import BaselineEvalRunner

    test_set = tmp_path / "test_set.jsonl"
    _write_single_item_test_set(test_set)
    monkeypatch.setattr(
        run_baseline_eval,
        "validate_jsonl",
        lambda path: ValidationReport(ok=True, total=1),
    )
    _FakeClient.calls = []
    _FakeClient.failures = {"graphrag-global-search:latest"}
    monkeypatch.setattr(run_baseline_eval, "OpenAICompatibleClient", _FakeClient)

    run_dir = BaselineEvalRunner(
        test_set_path=test_set,
        output_root=tmp_path / "runs",
        run_id="run-err",
        index_run_label="index-A",
    ).run()

    raw = json.loads((run_dir / "raw" / "Q001.json").read_text(encoding="utf-8"))
    assert (
        "graphrag-global-search:latest boom"
        in raw["modes"]["graphrag-global-search:latest"]["error"]
    )
    assert raw["modes"]["graphrag-global-search:latest"]["error_type"] == "RuntimeError"
    assert raw["modes"]["graphrag-global-search:latest"]["elapsed_seconds"] >= 0.0
    assert raw["modes"]["graphrag-local-search:latest"]["answer"] == (
        "graphrag-local-search:latest answer"
    )
    assert raw["modes"]["graphrag-drift-search:latest"]["answer"] == (
        "graphrag-drift-search:latest answer"
    )
    assert raw["modes"]["graphrag-basic-search:latest"]["answer"] == (
        "graphrag-basic-search:latest answer"
    )


def test_runner_records_error_elapsed_time(tmp_path, monkeypatch):
    from graphrag_pipeline.scripts.qa_eval import run_baseline_eval
    from graphrag_pipeline.scripts.qa_eval.run_baseline_eval import BaselineEvalRunner

    class SlowFailClient:
        def __init__(self, **kwargs):
            self.kwargs = kwargs

        def query(self, *, model: str, prompt: str) -> QueryResult:
            time.sleep(0.02)
            raise TimeoutError("mock timeout")

    test_set = tmp_path / "test_set.jsonl"
    _write_single_item_test_set(test_set)
    monkeypatch.setattr(
        run_baseline_eval,
        "validate_jsonl",
        lambda path: ValidationReport(ok=True, total=1),
    )
    monkeypatch.setattr(run_baseline_eval, "OpenAICompatibleClient", SlowFailClient)

    run_dir = BaselineEvalRunner(
        test_set_path=test_set,
        output_root=tmp_path / "runs",
        run_id="run-slow-error",
        index_run_label="index-A",
        modes=["drift"],
        max_retries=1,
    ).run()

    raw = json.loads((run_dir / "raw" / "Q001.json").read_text(encoding="utf-8"))
    drift = raw["modes"]["graphrag-drift-search:latest"]
    assert drift["error_type"] == "TimeoutError"
    assert drift["elapsed_seconds"] >= 0.02


def test_runner_can_limit_loaded_items_after_validation_for_diagnostics(tmp_path, monkeypatch):
    from graphrag_pipeline.scripts.qa_eval import run_baseline_eval
    from graphrag_pipeline.scripts.qa_eval.run_baseline_eval import BaselineEvalRunner

    test_set = tmp_path / "test_set.jsonl"
    _write_two_item_test_set(test_set)
    monkeypatch.setattr(
        run_baseline_eval,
        "validate_jsonl",
        lambda path: ValidationReport(ok=True, total=2),
    )
    _FakeClient.calls = []
    _FakeClient.failures = set()
    monkeypatch.setattr(run_baseline_eval, "OpenAICompatibleClient", _FakeClient)

    run_dir = BaselineEvalRunner(
        test_set_path=test_set,
        output_root=tmp_path / "runs",
        run_id="run-limited",
        index_run_label="index-A",
        modes=["local"],
        max_items=1,
    ).run()

    meta = json.loads((run_dir / "run_meta.json").read_text(encoding="utf-8"))
    assert meta["total_questions"] == 1
    assert meta["source_total_questions"] == 2
    assert sorted(path.name for path in (run_dir / "raw").glob("Q*.json")) == ["Q001.json"]


def test_runner_raises_value_error_when_validator_fails(tmp_path, monkeypatch):
    from graphrag_pipeline.scripts.qa_eval import run_baseline_eval
    from graphrag_pipeline.scripts.qa_eval.run_baseline_eval import BaselineEvalRunner

    test_set = tmp_path / "test_set.jsonl"
    _write_single_item_test_set(test_set)
    monkeypatch.setattr(
        run_baseline_eval,
        "validate_jsonl",
        lambda path: ValidationReport(ok=False, total=0, errors=["line 1: invalid json"]),
    )

    runner = BaselineEvalRunner(
        test_set_path=test_set,
        output_root=tmp_path / "runs",
        run_id="bad-run",
        index_run_label="index-A",
    )
    with pytest.raises(ValueError, match="line 1: invalid json"):
        runner.run()


def test_runner_validates_test_set_before_mode_parsing(tmp_path, monkeypatch):
    from graphrag_pipeline.scripts.qa_eval import run_baseline_eval
    from graphrag_pipeline.scripts.qa_eval.run_baseline_eval import BaselineEvalRunner

    test_set = tmp_path / "test_set.jsonl"
    _write_single_item_test_set(test_set)
    monkeypatch.setattr(
        run_baseline_eval,
        "validate_jsonl",
        lambda path: ValidationReport(ok=False, total=0, errors=["validator first"]),
    )

    runner = BaselineEvalRunner(
        test_set_path=test_set,
        output_root=tmp_path / "runs",
        run_id="bad-run",
        index_run_label="index-A",
        modes=["unknown-mode"],
    )
    with pytest.raises(ValueError, match="validator first"):
        runner.run()


def test_runner_rejects_duplicate_modes_after_alias_normalization(tmp_path, monkeypatch):
    from graphrag_pipeline.scripts.qa_eval import run_baseline_eval
    from graphrag_pipeline.scripts.qa_eval.run_baseline_eval import BaselineEvalRunner

    test_set = tmp_path / "test_set.jsonl"
    _write_single_item_test_set(test_set)
    monkeypatch.setattr(
        run_baseline_eval,
        "validate_jsonl",
        lambda path: ValidationReport(ok=True, total=1),
    )

    runner = BaselineEvalRunner(
        test_set_path=test_set,
        output_root=tmp_path / "runs",
        run_id="dup-run",
        index_run_label="index-A",
        modes=["local", "graphrag-local-search:latest"],
    )
    with pytest.raises(ValueError, match="duplicate baseline mode"):
        runner.run()


def test_cli_main_wires_args_and_writes_output(tmp_path, monkeypatch):
    from graphrag_pipeline.scripts.qa_eval import run_baseline_eval

    test_set = tmp_path / "test_set.jsonl"
    output_root = tmp_path / "runs"
    _write_single_item_test_set(test_set)
    monkeypatch.setattr(
        run_baseline_eval,
        "validate_jsonl",
        lambda path: ValidationReport(ok=True, total=1),
    )
    _FakeClient.calls = []
    _FakeClient.failures = set()
    monkeypatch.setattr(run_baseline_eval, "OpenAICompatibleClient", _FakeClient)

    exit_code = run_baseline_eval.main(
        [
            "--test-set",
            str(test_set),
            "--output-root",
            str(output_root),
            "--run-id",
            "cli-run",
            "--index-run-label",
            "index-cli",
            "--base-url",
            "http://127.0.0.1:9000",
            "--modes",
            "local",
            "graphrag-basic-search:latest",
            "--request-timeout-seconds",
            "7",
            "--max-retries",
            "1",
            "--backoff-seconds",
            "0",
        ]
    )

    assert exit_code == 0
    meta = json.loads((output_root / "cli-run" / "run_meta.json").read_text(encoding="utf-8"))
    assert meta["run_id"] == "cli-run"
    assert meta["index_run_label"] == "index-cli"
    assert meta["base_url"] == "http://127.0.0.1:9000"
    assert meta["modes"] == [
        "graphrag-local-search:latest",
        "graphrag-basic-search:latest",
    ]
    assert meta["total_items"] == 1
    raw = json.loads((output_root / "cli-run" / "raw" / "Q001.json").read_text(encoding="utf-8"))
    assert list(raw["modes"]) == [
        "graphrag-local-search:latest",
        "graphrag-basic-search:latest",
    ]
    assert [call[0] for call in _FakeClient.calls] == [
        "graphrag-local-search:latest",
        "graphrag-basic-search:latest",
    ]
