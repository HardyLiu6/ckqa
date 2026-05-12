from __future__ import annotations

import json
from pathlib import Path
from unittest.mock import MagicMock

from graphrag_pipeline.scripts.qa_eval.graphrag_client import QueryResult
from graphrag_pipeline.scripts.qa_eval.judge_scorer import (
    JUDGE_FALLBACK_SENTINEL,
    JudgeRunner,
    extract_text_unit_refs_from_answer,
    score_run_with_judge,
)
from graphrag_pipeline.scripts.qa_eval.run_baseline_eval import BASELINE_MODES
from graphrag_pipeline.scripts.qa_eval.text_unit_lookup import TextUnitLookup


def _seed_run(
    tmp_path: Path,
    *,
    answer: str = "DBSCAN 的两个核心超参数是 ε-邻域半径与最小邻居数。",
) -> Path:
    run_dir = tmp_path / "rid"
    raw = run_dir / "raw"
    raw.mkdir(parents=True)
    item = {
        "id": "Q001",
        "category": "factual_lookup",
        "question": "DBSCAN 的两个核心超参数是什么？",
        "gold_answer_summary": "eps 和 MinPts",
        "gold_entities": ["DBSCAN", "eps", "MinPts"],
        "gold_text_unit_ids": ["d244f9016ac8"],
        "must_cite_terms": [],
        "negative_terms": [],
        "modes": {
            mode: {"answer": answer, "total_tokens": 10, "elapsed_seconds": 0.1}
            for mode in BASELINE_MODES
        },
    }
    (raw / "Q001.json").write_text(json.dumps(item, ensure_ascii=False), encoding="utf-8")
    (run_dir / "run_meta.json").write_text(
        json.dumps(
            {
                "run_id": "rid",
                "index_run_label": "course",
                "total_items": 1,
                "modes": list(BASELINE_MODES),
            },
            ensure_ascii=False,
        ),
        encoding="utf-8",
    )
    return run_dir


def _lookup() -> TextUnitLookup:
    return TextUnitLookup(by_prefix={"d244f9016ac8": "DBSCAN: 邻域半径 eps 与最小点数 MinPts。"})


def _judge_runner(client: MagicMock, *, max_parse_retries: int = 2) -> JudgeRunner:
    return JudgeRunner(
        client=client,
        text_unit_lookup=_lookup(),
        judge_model="gpt-4o-mini",
        api_key=None,
        max_parse_retries=max_parse_retries,
    )


def test_extract_text_unit_refs_supports_data_blocks() -> None:
    answer = "回答 ... [Data: Text Units (d244f9016ac8, foo-bar-bazz123, 81d99ad61e36)]"
    refs = extract_text_unit_refs_from_answer(answer)
    assert "d244f9016ac8" in refs
    assert "81d99ad61e36" in refs


def test_semantic_correctness_uses_judge_decision(tmp_path: Path) -> None:
    run_dir = _seed_run(tmp_path)
    judge_client = MagicMock()
    judge_client.query.side_effect = [
        QueryResult(answer='{"score": 1.0, "rationale": "同义"}', total_tokens=42, elapsed_seconds=0.5, raw={}),
        QueryResult(answer='{"is_supported": true, "unsupported_claims": []}', total_tokens=42, elapsed_seconds=0.5, raw={}),
    ] * len(BASELINE_MODES)

    summary = score_run_with_judge(run_dir, runner=_judge_runner(judge_client))
    row = summary.per_question["Q001"]["graphrag-local-search:latest"]

    assert row["semantic_correctness"] == 1.0
    assert row["faithfulness"] == 1.0
    assert (run_dir / "judge_scoring.json").exists()


def test_faithfulness_falls_back_when_judge_returns_garbage(tmp_path: Path) -> None:
    run_dir = _seed_run(tmp_path)
    judge_client = MagicMock()
    judge_client.query.side_effect = [
        QueryResult(answer='{"score": 1.0, "rationale": "ok"}', total_tokens=10, elapsed_seconds=0.1, raw={}),
        QueryResult(answer="not a json", total_tokens=10, elapsed_seconds=0.1, raw={}),
        QueryResult(answer="not a json", total_tokens=10, elapsed_seconds=0.1, raw={}),
    ] * len(BASELINE_MODES)

    summary = score_run_with_judge(run_dir, runner=_judge_runner(judge_client, max_parse_retries=1))
    row = summary.per_question["Q001"]["graphrag-local-search:latest"]

    assert row["faithfulness"] == JUDGE_FALLBACK_SENTINEL
    assert (run_dir / "judge_raw" / "Q001_graphrag-local-search-latest_faithfulness.json").exists()


def test_retrieval_precision_uses_gold_text_unit_ids(tmp_path: Path) -> None:
    run_dir = _seed_run(
        tmp_path,
        answer=(
            "DBSCAN 的两个核心超参数是 eps 与 MinPts "
            "[Data: Text Units (d244f9016ac8, 99999999dead)]"
        ),
    )
    judge_client = MagicMock()
    judge_client.query.side_effect = [
        QueryResult(answer='{"score": 1.0, "rationale": "ok"}', total_tokens=20, elapsed_seconds=0.1, raw={}),
        QueryResult(answer='{"is_supported": true, "unsupported_claims": []}', total_tokens=20, elapsed_seconds=0.1, raw={}),
    ] * len(BASELINE_MODES)

    summary = score_run_with_judge(run_dir, runner=_judge_runner(judge_client))
    row = summary.per_question["Q001"]["graphrag-local-search:latest"]

    assert row["retrieval_precision"] == 1.0


def test_retrieval_precision_zero_when_answer_has_no_refs(tmp_path: Path) -> None:
    run_dir = _seed_run(tmp_path, answer="DBSCAN 的核心超参数是 eps 和 MinPts。")
    judge_client = MagicMock()
    judge_client.query.side_effect = [
        QueryResult(answer='{"score": 1.0, "rationale": "ok"}', total_tokens=10, elapsed_seconds=0.1, raw={}),
        QueryResult(answer='{"is_supported": true, "unsupported_claims": []}', total_tokens=10, elapsed_seconds=0.1, raw={}),
    ] * len(BASELINE_MODES)

    summary = score_run_with_judge(run_dir, runner=_judge_runner(judge_client))
    row = summary.per_question["Q001"]["graphrag-local-search:latest"]

    assert row["retrieval_precision"] == 0.0
