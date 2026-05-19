from __future__ import annotations

import json
import subprocess
from pathlib import Path

from graphrag_pipeline.scripts.qa_eval.run_global_cli_tuning import (
    GLOBAL_MODE,
    REQUIRED_INDEX_OUTPUT_PATHS,
    GlobalCliTuningRunner,
    GlobalSearchConfig,
    _is_timeout_like,
    apply_global_search_config,
    extract_cli_answer,
)
from graphrag_pipeline.scripts.qa_eval.test_set_validator import ValidationReport


def _write_settings(path: Path) -> None:
    path.write_text(
        "\n".join(
            [
                "concurrent_requests: 20",
                "",
                "local_search:",
                "  completion_model_id: query_completion_model",
                "",
                "global_search:",
                "  completion_model_id: query_completion_model",
                "  max_context_tokens: 24000",
                "  data_max_tokens: 3000",
                "  map_max_length: 250",
                "  reduce_max_length: 600",
                "",
                "drift_search:",
                "  completion_model_id: query_completion_model",
            ]
        )
        + "\n",
        encoding="utf-8",
    )


def _write_test_set(path: Path, count: int = 2) -> None:
    rows = []
    for index in range(1, count + 1):
        rows.append(
            {
                "id": f"Q{index:03d}",
                "category": "factual_lookup",
                "question": f"第 {index} 个问题是什么？",
                "gold_answer_summary": "参考答案",
                "gold_entities": ["参考"],
                "gold_text_unit_ids": ["d244f9016ac8"],
                "must_cite_terms": [],
                "negative_terms": [],
            }
        )
    path.write_text(
        "".join(json.dumps(row, ensure_ascii=False) + "\n" for row in rows),
        encoding="utf-8",
    )


def _write_core_index_output(path: Path) -> None:
    for relative_path in REQUIRED_INDEX_OUTPUT_PATHS:
        target = path / relative_path
        target.parent.mkdir(parents=True, exist_ok=True)
        target.write_text("stub", encoding="utf-8")


def test_apply_global_search_config_preserves_surrounding_settings() -> None:
    settings = "\n".join(
        [
            "concurrent_requests: 20",
            "async_mode: threaded",
            "",
            "global_search:",
            "  completion_model_id: query_completion_model",
            "  max_context_tokens: 24000",
            "  data_max_tokens: 3000",
            "  map_max_length: 250",
            "  reduce_max_length: 600",
            "",
            "basic_search:",
            "  completion_model_id: query_completion_model",
        ]
    )

    updated = apply_global_search_config(
        settings + "\n",
        GlobalSearchConfig(
            concurrent_requests=30,
            max_context_tokens=18000,
            data_max_tokens=3000,
            map_max_length=200,
            reduce_max_length=500,
        ),
    )

    assert "concurrent_requests: 30\n" in updated
    assert "async_mode: threaded\n" in updated
    assert "  max_context_tokens: 18000\n" in updated
    assert "  data_max_tokens: 3000\n" in updated
    assert "  map_max_length: 200\n" in updated
    assert "  reduce_max_length: 500\n" in updated
    assert "basic_search:\n" in updated


def test_extract_cli_answer_prefers_global_success_marker() -> None:
    stdout = (
        "INFO: starting\n"
        "SUCCESS: Global Search Response:\n"
        "操作系统是第一层软件。\n"
    )

    assert extract_cli_answer(stdout) == "操作系统是第一层软件。"


def test_arrearage_trace_with_timeout_kwarg_is_not_timeout_like() -> None:
    error = "BadRequestError: Access denied Arrearage completion_kwargs={'timeout': None}"

    assert _is_timeout_like(error, "CalledProcessError") is False


def test_runner_records_timeouts_and_restores_settings(tmp_path, monkeypatch) -> None:
    from graphrag_pipeline.scripts.qa_eval import run_global_cli_tuning

    graphrag_root = tmp_path / "graphrag_pipeline"
    graphrag_root.mkdir()
    settings_path = graphrag_root / "settings.yaml"
    env_file = graphrag_root / ".env"
    test_set = tmp_path / "qa_test_set.jsonl"
    index_output_dir = tmp_path / "output" / "index"
    output_root = tmp_path / "runs"
    _write_settings(settings_path)
    original_settings = settings_path.read_text(encoding="utf-8")
    env_file.write_text(
        "GRAPHRAG_API_BASE=http://127.0.0.1:3301/v1\n"
        "GRAPHRAG_CHAT_API_KEY=test-key\n",
        encoding="utf-8",
    )
    _write_test_set(test_set, count=3)
    _write_core_index_output(index_output_dir)

    def _fake_run(*args, **kwargs):
        raise subprocess.TimeoutExpired(cmd=kwargs.get("args", ["graphrag"]), timeout=1)

    monkeypatch.setattr(
        run_global_cli_tuning,
        "validate_jsonl",
        lambda path: ValidationReport(ok=True, total=3),
    )
    monkeypatch.setattr(run_global_cli_tuning.subprocess, "run", _fake_run)

    runner = GlobalCliTuningRunner(
        graphrag_root=graphrag_root,
        test_set_path=test_set,
        index_output_dir=index_output_dir,
        output_root=output_root,
        settings_path=settings_path,
        env_file=env_file,
        groups=["G0"],
        question_ids=["Q001", "Q002", "Q003"],
        run_id_prefix="test-global",
        python_executable="/fake/python",
        request_timeout_seconds=1,
        stop_after_timeout_count=2,
    )

    run_dirs = runner.run()

    assert settings_path.read_text(encoding="utf-8") == original_settings
    assert len(run_dirs) == 1
    meta = json.loads((run_dirs[0] / "run_meta.json").read_text(encoding="utf-8"))
    assert meta["group"] == "G0"
    assert meta["stopped_early"] is True
    assert meta["executed_items"] == 2
    raw_files = sorted(path.name for path in (run_dirs[0] / "raw").glob("Q*.json"))
    assert raw_files == ["Q001.json", "Q002.json"]
    raw = json.loads((run_dirs[0] / "raw" / "Q001.json").read_text(encoding="utf-8"))
    assert raw["modes"][GLOBAL_MODE]["error_type"] == "TimeoutExpired"
