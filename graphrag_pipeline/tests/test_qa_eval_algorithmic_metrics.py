from __future__ import annotations

from pathlib import Path
from unittest.mock import patch

import pandas as pd

from graphrag_pipeline.scripts.qa_eval.citation_extractor import extract_text_unit_refs
from graphrag_pipeline.scripts.qa_eval.ir_metrics import score_ranked_citations
from graphrag_pipeline.scripts.qa_eval.semantic_similarity import (
    SemanticScoringConfig,
    score_semantic_similarity,
    split_semantic_chunks,
)
from graphrag_pipeline.scripts.qa_eval.text_unit_lookup import (
    DataCitationLookup,
    load_data_citation_lookup,
)


def test_extract_text_unit_refs_handles_graphrag_data_format():
    answer = "DBSCAN 的参数是 eps 和 MinPts。[Data: Text Units (d244f9016ac8, 81d99ad61e36)]"

    assert extract_text_unit_refs(answer) == ["d244f9016ac8", "81d99ad61e36"]


def test_extract_text_unit_refs_deduplicates_in_order():
    answer = "[Data: Text Units (aaaabbbbcccc, aaaabbbbcccc, ddddeeeeffff)]"

    assert extract_text_unit_refs(answer) == ["aaaabbbbcccc", "ddddeeeeffff"]


def test_extract_text_unit_refs_resolves_reports_sources_entities_and_relationships():
    lookup = DataCitationLookup(
        reports_by_human_id={"21": ["report111111", "shared000000"]},
        sources_by_human_id={"5": ["source222222"]},
        entities_by_human_id={"7": ["entity333333"]},
        relationships_by_human_id={"9": ["shared000000", "rel444444444"]},
    )
    answer = "总结 [Data: Reports (21); Sources (5); Entities (7); Relationships (9)]"

    assert extract_text_unit_refs(answer, data_citation_lookup=lookup) == [
        "report111111",
        "shared000000",
        "source222222",
        "entity333333",
        "rel444444444",
    ]


def test_load_data_citation_lookup_public_contract_maps_existing_reference_kinds(tmp_path: Path):
    text_units = tmp_path / "text_units.parquet"
    pd.DataFrame(
        [
            {"id": "222222222222abcdef", "human_readable_id": 5, "text": "来源 text unit"},
        ]
    ).to_parquet(text_units)
    pd.DataFrame(
        [
            {"human_readable_id": 7, "text_unit_ids": ["333333333333abcdef"]},
        ]
    ).to_parquet(tmp_path / "entities.parquet")
    pd.DataFrame(
        [
            {"human_readable_id": 9, "text_unit_ids": ["000000000000abcdef", "444444444444abcdef"]},
        ]
    ).to_parquet(tmp_path / "relationships.parquet")
    pd.DataFrame(
        [
            {"community": 21, "text_unit_ids": ["111111111111abcdef", "000000000000abcdef"]},
        ]
    ).to_parquet(tmp_path / "communities.parquet")
    pd.DataFrame(
        [
            {"human_readable_id": 21, "community": 21},
        ]
    ).to_parquet(tmp_path / "community_reports.parquet")

    lookup = load_data_citation_lookup(text_units)
    refs = lookup.resolve_answer_refs("总结 [Data: Reports (21); Sources (5); Entities (7); Relationships (9)]")

    assert refs == [
        "111111111111",
        "000000000000",
        "222222222222",
        "333333333333",
        "444444444444",
    ]


def test_score_ranked_citations_computes_recall_rr_ndcg():
    scores = score_ranked_citations(
        question_id="Q001",
        ranked_refs=["a11111111111", "b22222222222", "c33333333333"],
        gold_refs=["b22222222222", "x99999999999"],
        cutoffs=[1, 3, 5],
    )

    assert scores["citation_recall_at_1"] == 0.0
    assert scores["citation_recall_at_3"] == 0.5
    assert scores["citation_rr"] == 0.5
    assert 0.0 < scores["citation_ndcg_at_5"] <= 1.0


def test_score_ranked_citations_returns_zeroes_for_empty_run_with_gold_refs():
    scores = score_ranked_citations(
        question_id="Q002",
        ranked_refs=[],
        gold_refs=["b22222222222"],
        cutoffs=[1, 3],
    )

    assert scores["citation_recall_at_1"] == 0.0
    assert scores["citation_recall_at_3"] == 0.0
    assert scores["citation_rr"] == 0.0
    assert scores["citation_map"] == 0.0


def test_score_ranked_citations_returns_zeroes_for_empty_gold_refs():
    scores = score_ranked_citations(
        question_id="Q003",
        ranked_refs=["b22222222222"],
        gold_refs=[],
        cutoffs=[1, 3],
    )

    assert scores["citation_recall_at_1"] == 0.0
    assert scores["citation_ndcg_at_1"] == 0.0
    assert scores["citation_recall_at_3"] == 0.0
    assert scores["citation_ndcg_at_3"] == 0.0
    assert scores["citation_rr"] == 0.0
    assert scores["citation_map"] == 0.0


def test_split_semantic_chunks_keeps_long_answers_bounded():
    chunks = split_semantic_chunks(
        "DBSCAN 使用 eps 描述邻域。MinPts 表示核心对象所需的最少样本数。聚类会扩展密度可达对象。",
        max_chunk_chars=24,
    )

    assert len(chunks) >= 2
    assert all(len(chunk) <= 24 for chunk in chunks)


def test_score_semantic_similarity_uses_bge_m3_chunked_coverage():
    with patch(
        "graphrag_pipeline.scripts.qa_eval.semantic_similarity._score_bge_m3_coverage",
        return_value={
            "semantic_coverage_precision": 0.7,
            "semantic_coverage_recall": 0.8,
            "semantic_coverage_f1": 0.7467,
        },
    ):
        result = score_semantic_similarity(
            answer="DBSCAN 的核心参数是 eps 和 MinPts。",
            reference="DBSCAN 的两个核心超参数是 eps 和 MinPts。",
            config=SemanticScoringConfig(enable_bge_m3=True, enable_bertscore=False),
        )

    assert result["semantic_coverage_precision"] == 0.7
    assert result["semantic_coverage_recall"] == 0.8
    assert result["semantic_coverage_f1"] == 0.7467
    assert result["rouge_lsum"] > 0
    assert result["keyword_recall"] > 0


def test_score_semantic_similarity_can_run_without_bge_for_fast_baseline():
    result = score_semantic_similarity(
        answer="操作系统管理处理机、存储器、设备和文件。",
        reference="操作系统负责管理处理机、存储器、I/O 设备和文件资源。",
        config=SemanticScoringConfig(enable_bge_m3=False, enable_bertscore=False),
    )

    assert result["semantic_coverage_f1"] == 0.0
    assert result["rouge_lsum"] > 0
    assert result["keyword_recall"] > 0
