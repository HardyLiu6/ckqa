from __future__ import annotations

import json
from pathlib import Path
from unittest.mock import MagicMock

import pandas as pd

from graphrag_pipeline.scripts.qa_eval.graphrag_client import QueryResult
from graphrag_pipeline.scripts.qa_eval.judge_scorer import (
    JUDGE_FALLBACK_SENTINEL,
    JudgeRunner,
    extract_text_unit_refs_from_answer,
    score_run_with_judge,
)
from graphrag_pipeline.scripts.qa_eval.run_baseline_eval import BASELINE_MODES
from graphrag_pipeline.scripts.qa_eval.text_unit_lookup import (
    DataCitationLookup,
    TextUnitLookup,
    extract_data_citations_from_answer,
    load_data_citation_lookup,
)


def _seed_run(
    tmp_path: Path,
    *,
    answer: str = "DBSCAN 的两个核心超参数是 ε-邻域半径与最小邻居数。",
    question_ids: tuple[str, ...] = ("Q001",),
) -> Path:
    run_dir = tmp_path / "rid"
    raw = run_dir / "raw"
    raw.mkdir(parents=True)
    for index, question_id in enumerate(question_ids, start=1):
        item = {
            "id": question_id,
            "category": "factual_lookup",
            "question": f"DBSCAN 的两个核心超参数是什么？（{question_id}）",
            "gold_answer_summary": "eps 和 MinPts",
            "gold_entities": ["DBSCAN", "eps", "MinPts"],
            "gold_text_unit_ids": ["d244f9016ac8"],
            "must_cite_terms": [],
            "negative_terms": [],
            "modes": {
                mode: {
                    "answer": f"{answer} [{question_id}-{mode}-{index}]",
                    "total_tokens": 10,
                    "elapsed_seconds": 0.1,
                }
                for mode in BASELINE_MODES
            },
        }
        (raw / f"{question_id}.json").write_text(json.dumps(item, ensure_ascii=False), encoding="utf-8")
    (run_dir / "run_meta.json").write_text(
        json.dumps(
            {
                "run_id": "rid",
                "index_run_label": "course",
                "total_items": len(question_ids),
                "modes": list(BASELINE_MODES),
            },
            ensure_ascii=False,
        ),
        encoding="utf-8",
    )
    return run_dir


def _lookup() -> TextUnitLookup:
    return TextUnitLookup(by_prefix={"d244f9016ac8": "DBSCAN: 邻域半径 eps 与最小点数 MinPts。"})


def _judge_runner(
    client: MagicMock,
    *,
    data_citation_lookup: DataCitationLookup | None = None,
    max_parse_retries: int = 2,
) -> JudgeRunner:
    return JudgeRunner(
        client=client,
        text_unit_lookup=_lookup(),
        data_citation_lookup=data_citation_lookup,
        judge_model="gpt-4o-mini",
        api_key=None,
        max_parse_retries=max_parse_retries,
    )


def _data_citation_lookup() -> DataCitationLookup:
    return DataCitationLookup(
        sources_by_human_id={"26": ["d244f9016ac8"]},
        entities_by_human_id={"245": ["d244f9016ac8"]},
        relationships_by_human_id={"432": ["d244f9016ac8"]},
        reports_by_human_id={"645": ["d244f9016ac8"]},
    )


def test_extract_text_unit_refs_supports_data_blocks() -> None:
    answer = "回答 ... [Data: Text Units (d244f9016ac8, foo-bar-bazz123, 81d99ad61e36)]"
    refs = extract_text_unit_refs_from_answer(answer)
    assert "d244f9016ac8" in refs
    assert "81d99ad61e36" in refs


def test_extract_data_citations_from_answer_supports_existing_reference_kinds() -> None:
    answer = (
        "总结内容 [Data: Reports (645, 292, +more); Sources (26); "
        "Entities (245); Relationships (432, 50)]"
    )

    citations = extract_data_citations_from_answer(answer)

    assert citations == {
        "reports": ["645", "292"],
        "sources": ["26"],
        "entities": ["245"],
        "relationships": ["432", "50"],
    }


def test_semantic_correctness_uses_judge_decision(tmp_path: Path) -> None:
    run_dir = _seed_run(tmp_path)
    judge_client = MagicMock()
    judge_client.query.side_effect = [
        QueryResult(answer='{"score": 1.0, "rationale": "同义"}', total_tokens=42, elapsed_seconds=0.5, raw={}),
        QueryResult(answer='{"score": 1.0, "unsupported_claims": []}', total_tokens=42, elapsed_seconds=0.5, raw={}),
        QueryResult(answer='{"score": 1.0, "rationale": "充分利用证据"}', total_tokens=42, elapsed_seconds=0.5, raw={}),
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
        QueryResult(answer='{"score": 0.0, "rationale": "没有利用 gold 证据"}', total_tokens=10, elapsed_seconds=0.1, raw={}),
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
        QueryResult(answer='{"score": 1.0, "unsupported_claims": []}', total_tokens=10, elapsed_seconds=0.1, raw={}),
        QueryResult(answer='{"score": 0.0, "rationale": "未体现 gold 证据"}', total_tokens=10, elapsed_seconds=0.1, raw={}),
    ] * len(BASELINE_MODES)

    summary = score_run_with_judge(run_dir, runner=_judge_runner(judge_client))
    row = summary.per_question["Q001"]["graphrag-local-search:latest"]

    assert row["retrieval_precision"] == 0.0


def test_judge_can_assign_partial_grounding_without_text_unit_citations(tmp_path: Path) -> None:
    run_dir = _seed_run(
        tmp_path,
        answer="DBSCAN 主要依赖 eps 和 MinPts，并使用密度可达关系完成聚类。",
    )
    judge_client = MagicMock()
    judge_client.query.side_effect = [
        QueryResult(answer='{"score": 1.0, "rationale": "语义正确"}', total_tokens=10, elapsed_seconds=0.1, raw={}),
        QueryResult(
            answer='{"score": 0.5, "unsupported_claims": ["补入了证据中未直接出现的扩展描述"]}',
            total_tokens=10,
            elapsed_seconds=0.1,
            raw={},
        ),
        QueryResult(answer='{"score": 0.5, "rationale": "部分依据 gold 证据"}', total_tokens=10, elapsed_seconds=0.1, raw={}),
    ] * len(BASELINE_MODES)

    summary = score_run_with_judge(run_dir, runner=_judge_runner(judge_client))
    row = summary.per_question["Q001"]["graphrag-local-search:latest"]

    assert row["semantic_correctness"] == 1.0
    assert row["faithfulness"] == 0.5
    assert row["retrieval_precision"] == 0.5


def test_retrieval_precision_understands_existing_data_citations(tmp_path: Path) -> None:
    run_dir = _seed_run(
        tmp_path,
        answer=(
            "处理机管理的重点包括进程生命周期、同步通信与调度 "
            "[Data: Entities (245); Relationships (432); Sources (26); Reports (645)]"
        ),
    )
    judge_client = MagicMock()
    judge_client.query.side_effect = [
        QueryResult(answer='{"score": 1.0, "rationale": "语义正确"}', total_tokens=10, elapsed_seconds=0.1, raw={}),
        QueryResult(answer='{"score": 0.5, "unsupported_claims": []}', total_tokens=10, elapsed_seconds=0.1, raw={}),
    ] * len(BASELINE_MODES)

    summary = score_run_with_judge(
        run_dir,
        runner=_judge_runner(judge_client, data_citation_lookup=_data_citation_lookup()),
    )
    row = summary.per_question["Q001"]["graphrag-local-search:latest"]

    assert row["retrieval_precision"] == 1.0
    assert judge_client.query.call_count == len(BASELINE_MODES) * 2


def test_score_run_with_judge_can_limit_to_selected_question_ids_in_order(tmp_path: Path) -> None:
    run_dir = _seed_run(tmp_path, question_ids=("Q001", "Q002", "Q003"))
    judge_client = MagicMock()
    judge_client.query.side_effect = [
        QueryResult(answer='{"score": 1.0, "rationale": "ok"}', total_tokens=10, elapsed_seconds=0.1, raw={}),
        QueryResult(answer='{"score": 1.0, "unsupported_claims": []}', total_tokens=10, elapsed_seconds=0.1, raw={}),
        QueryResult(answer='{"score": 0.5, "rationale": "部分利用证据"}', total_tokens=10, elapsed_seconds=0.1, raw={}),
    ] * (len(BASELINE_MODES) * 2)

    summary = score_run_with_judge(
        run_dir,
        runner=_judge_runner(judge_client),
        question_ids=["Q003", "Q001"],
    )

    assert list(summary.per_question.keys()) == ["Q003", "Q001"]
    assert judge_client.query.call_count == len(BASELINE_MODES) * 3 * 2


def test_score_run_with_judge_rejects_unknown_question_ids(tmp_path: Path) -> None:
    run_dir = _seed_run(tmp_path, question_ids=("Q001", "Q002"))

    try:
        score_run_with_judge(
            run_dir,
            runner=_judge_runner(MagicMock()),
            question_ids=["Q001", "Q999"],
        )
    except ValueError as exc:
        assert "unknown question id(s): Q999" in str(exc)
    else:
        raise AssertionError("expected unknown question ids to be rejected")


def test_score_run_with_judge_rejects_duplicate_question_ids(tmp_path: Path) -> None:
    run_dir = _seed_run(tmp_path, question_ids=("Q001", "Q002"))

    try:
        score_run_with_judge(
            run_dir,
            runner=_judge_runner(MagicMock()),
            question_ids=["Q001", "Q001"],
        )
    except ValueError as exc:
        assert "duplicate question id(s): Q001" in str(exc)
    else:
        raise AssertionError("expected duplicate question ids to be rejected")


def test_cli_uses_text_units_from_run_meta_index_output_dir(tmp_path: Path, monkeypatch) -> None:
    from graphrag_pipeline.scripts.qa_eval import judge_scorer

    run_dir = _seed_run(tmp_path)
    index_output_dir = tmp_path / "output" / "auto_tuned_index"
    index_output_dir.mkdir(parents=True)
    text_units_path = index_output_dir / "text_units.parquet"
    text_units_path.write_text("stub", encoding="utf-8")
    meta = json.loads((run_dir / "run_meta.json").read_text(encoding="utf-8"))
    meta["index_output_dir"] = str(index_output_dir)
    (run_dir / "run_meta.json").write_text(json.dumps(meta, ensure_ascii=False), encoding="utf-8")
    loaded_paths: list[Path] = []

    def fake_load_text_unit_lookup(path: Path) -> TextUnitLookup:
        loaded_paths.append(path)
        return _lookup()

    monkeypatch.setattr(judge_scorer, "load_text_unit_lookup", fake_load_text_unit_lookup)
    monkeypatch.setattr(judge_scorer, "load_data_citation_lookup", lambda path: DataCitationLookup())
    monkeypatch.setattr(judge_scorer, "OpenAICompatibleClient", lambda **kwargs: MagicMock())
    captured_question_ids: list[str] | None = None

    def fake_score_run_with_judge(run_dir, *, runner, question_ids=None):
        nonlocal captured_question_ids
        captured_question_ids = question_ids
        return judge_scorer.JudgeSummary(per_mode={})

    monkeypatch.setattr(
        judge_scorer,
        "score_run_with_judge",
        fake_score_run_with_judge,
    )

    exit_code = judge_scorer.main(
        [
            "--run-dir",
            str(run_dir),
            "--judge-base-url",
            "http://127.0.0.1:8000",
            "--question-ids",
            "Q001",
        ]
    )

    assert exit_code == 0
    assert loaded_paths == [text_units_path]
    assert captured_question_ids == ["Q001"]


def test_cli_errors_when_resolved_text_units_path_is_missing(tmp_path: Path, monkeypatch) -> None:
    from graphrag_pipeline.scripts.qa_eval import judge_scorer

    run_dir = _seed_run(tmp_path)
    meta = json.loads((run_dir / "run_meta.json").read_text(encoding="utf-8"))
    meta["index_output_dir"] = str(tmp_path / "missing-index")
    (run_dir / "run_meta.json").write_text(json.dumps(meta, ensure_ascii=False), encoding="utf-8")

    monkeypatch.setattr(judge_scorer, "OpenAICompatibleClient", lambda **kwargs: MagicMock())

    try:
        judge_scorer.main(
            [
                "--run-dir",
                str(run_dir),
                "--judge-base-url",
                "http://127.0.0.1:8000",
            ]
        )
    except FileNotFoundError as exc:
        assert "text_units.parquet" in str(exc)
    else:
        raise AssertionError("expected missing text_units.parquet to fail clearly")


def test_text_units_resolution_keeps_previous_default_without_index_output_dir(
    tmp_path: Path,
    monkeypatch,
) -> None:
    from graphrag_pipeline.scripts.qa_eval import judge_scorer

    run_dir = _seed_run(tmp_path)
    fallback_path = tmp_path / "graphrag_pipeline" / "output" / "text_units.parquet"
    fallback_path.parent.mkdir(parents=True)
    fallback_path.write_text("stub", encoding="utf-8")
    monkeypatch.chdir(tmp_path)

    assert judge_scorer._resolve_text_units_path(run_dir, None) == Path(
        "graphrag_pipeline/output/text_units.parquet"
    )


def test_load_data_citation_lookup_maps_reports_entities_sources_and_relationships(tmp_path: Path) -> None:
    index_output_dir = tmp_path / "output"
    index_output_dir.mkdir()
    pd.DataFrame(
        [
            {
                "id": "d244f9016ac84a55a7435cb6466e1b38",
                "human_readable_id": 26,
                "text": "source row",
            }
        ]
    ).to_parquet(index_output_dir / "text_units.parquet")
    pd.DataFrame(
        [{"human_readable_id": 245, "text_unit_ids": ["d244f9016ac84a55a7435cb6466e1b38"]}]
    ).to_parquet(index_output_dir / "entities.parquet")
    pd.DataFrame(
        [{"human_readable_id": 432, "text_unit_ids": ["d244f9016ac84a55a7435cb6466e1b38"]}]
    ).to_parquet(index_output_dir / "relationships.parquet")
    pd.DataFrame([{"community": 923, "text_unit_ids": ["d244f9016ac84a55a7435cb6466e1b38"]}]).to_parquet(
        index_output_dir / "communities.parquet"
    )
    pd.DataFrame([{"human_readable_id": 645, "community": 923}]).to_parquet(
        index_output_dir / "community_reports.parquet"
    )

    lookup = load_data_citation_lookup(index_output_dir / "text_units.parquet")
    refs = lookup.resolve_answer_refs(
        "答案 [Data: Sources (26); Entities (245); Relationships (432); Reports (645)]"
    )

    assert refs == ["d244f9016ac8"]
