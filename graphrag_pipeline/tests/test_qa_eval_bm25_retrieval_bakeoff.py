from __future__ import annotations

import json
from pathlib import Path

import numpy as np
import pandas as pd

from graphrag_pipeline.scripts.qa_eval.bm25_retrieval_bakeoff import (
    Bm25BakeoffConfig,
    DenseRerankPolicy,
    QueryDecompositionPolicy,
    TextUnitRow,
    audit_text_unit_noise,
    build_multi_queries,
    build_query_decomposition,
    build_search_texts,
    download_thuocl_it,
    expand_gold_refs_with_heading_context,
    generate_noise_blacklist,
    gold_section_audit_rows,
    index_heading_context,
    load_noise_blacklist,
    rerank_refs_by_dense_similarity,
    rrf_fuse_ranked_refs,
    run_bm25_bakeoff,
    score_question,
    should_dense_rerank_item,
)


def _write_text_units(path: Path) -> None:
    pd.DataFrame(
        [
            {"id": "gold11111111aaaa", "text": "蓝鲸调度器负责把作业分配到处理机。"},
            {"id": "gold22222222bbbb", "text": "文件控制块和索引结点用于描述文件目录结构。"},
            {"id": "noise3333333cccc", "text": "普通调度器按照优先级选择进程。"},
            {"id": "noise4444444dddd", "text": "第三章 处理机调度与死锁 gogogogogogogogogogogogo。"},
        ]
    ).to_parquet(path)


def _write_test_set(path: Path) -> None:
    rows = [
        {
            "id": "Q001",
            "category": "factual_lookup",
            "question": "蓝鲸调度器负责什么？",
            "gold_answer_summary": "蓝鲸调度器负责作业分配。",
            "gold_entities": ["蓝鲸调度器"],
            "gold_text_unit_ids": ["gold11111111aaaa"],
            "must_cite_terms": ["作业分配"],
            "negative_terms": [],
        },
        {
            "id": "Q002",
            "category": "chapter_summary",
            "question": "文件系统的文件目录依赖哪些结构？",
            "gold_answer_summary": "文件目录依赖文件控制块和索引结点。",
            "gold_entities": ["文件控制块", "索引结点"],
            "gold_text_unit_ids": ["gold22222222bbbb"],
            "must_cite_terms": ["文件控制块"],
            "negative_terms": [],
        },
    ]
    path.write_text("\n".join(json.dumps(row, ensure_ascii=False) for row in rows) + "\n", encoding="utf-8")


def test_run_bm25_bakeoff_writes_summary_scores_and_failures(tmp_path: Path):
    text_units = tmp_path / "text_units.parquet"
    test_set = tmp_path / "qa_test_set.jsonl"
    _write_text_units(text_units)
    _write_test_set(test_set)

    result = run_bm25_bakeoff(
        Bm25BakeoffConfig(
            test_set_path=test_set,
            text_units_path=text_units,
            output_dir=tmp_path / "out",
            run_id="unit",
            k1_values=(1.2,),
            b_values=(0.75,),
            max_course_terms=20,
            max_thuocl_terms=20,
        )
    )

    assert result.run_dir == tmp_path / "out" / "unit"
    assert (result.run_dir / "bm25_bakeoff_summary.md").exists()
    assert (result.run_dir / "bm25_bakeoff_scores.csv").exists()
    assert (result.run_dir / "bm25_bakeoff_by_question.csv").exists()
    assert (result.run_dir / "bm25_bakeoff_failures.md").exists()
    assert (result.run_dir / "text_unit_noise_audit.md").exists()
    assert (result.run_dir / "text_unit_noise_audit.json").exists()
    assert (result.run_dir / "course_terms.json").exists()
    assert (result.run_dir / "filtered_thuocl_it_terms.txt").exists()

    summary = (result.run_dir / "bm25_bakeoff_summary.md").read_text(encoding="utf-8")
    assert "jieba_baseline" in summary
    assert "gold context expansion" in summary
    assert "section-aware" in summary
    assert "best config" in summary
    assert "text_units.parquet" in summary

    noise_report = (result.run_dir / "text_unit_noise_audit.md").read_text(encoding="utf-8")
    assert "noise4444444" in noise_report


def test_thuocl_download_is_cached_and_filterable(tmp_path: Path, monkeypatch):
    calls: list[str] = []

    def fake_urlretrieve(url: str, filename: str):
        calls.append(url)
        Path(filename).write_text("蓝鲸调度器 100\n无关财经词 50\n文件控制块 20\n", encoding="utf-8")
        return filename, None

    monkeypatch.setattr(
        "graphrag_pipeline.scripts.qa_eval.bm25_retrieval_bakeoff.urlretrieve",
        fake_urlretrieve,
    )

    first = download_thuocl_it(tmp_path)
    second = download_thuocl_it(tmp_path)

    assert first == second
    assert len(calls) == 1
    assert "THUOCL_IT.txt" in first.name


def test_bakeoff_uses_thuocl_terms_when_cache_is_present(tmp_path: Path):
    text_units = tmp_path / "text_units.parquet"
    test_set = tmp_path / "qa_test_set.jsonl"
    thuocl = tmp_path / "THUOCL_IT.txt"
    _write_text_units(text_units)
    _write_test_set(test_set)
    thuocl.write_text("蓝鲸调度器 100\n无关术语 50\n文件控制块 20\n", encoding="utf-8")

    result = run_bm25_bakeoff(
        Bm25BakeoffConfig(
            test_set_path=test_set,
            text_units_path=text_units,
            output_dir=tmp_path / "out",
            run_id="with-thuocl",
            thuocl_it_path=thuocl,
            k1_values=(1.2,),
            b_values=(0.75,),
            max_thuocl_terms=10,
        )
    )

    filtered = (result.run_dir / "filtered_thuocl_it_terms.txt").read_text(encoding="utf-8")
    assert "蓝鲸调度器" in filtered
    assert "文件控制块" in filtered
    assert "无关术语" not in filtered


def test_multi_query_builder_splits_composite_overview_question():
    queries = build_multi_queries("I/O 管理、磁盘调度和文件系统在课程中如何衔接？")

    assert queries[0] == "I/O 管理、磁盘调度和文件系统在课程中如何衔接？"
    assert "I/O 管理" in queries
    assert "磁盘调度" in queries
    assert "文件系统" in queries


def test_rrf_fuse_ranked_refs_promotes_refs_found_by_subqueries():
    fused = rrf_fuse_ranked_refs(
        [
            ["noise11111111", "gold22222222"],
            ["gold22222222", "noise33333333"],
            ["gold44444444"],
        ],
        top_k=3,
    )

    assert fused[:2] == ["gold22222222", "gold44444444"]


def test_bakeoff_outputs_second_round_filtered_multi_query_config(tmp_path: Path):
    text_units = tmp_path / "text_units.parquet"
    test_set = tmp_path / "qa_test_set.jsonl"
    _write_text_units(text_units)
    _write_test_set(test_set)

    result = run_bm25_bakeoff(
        Bm25BakeoffConfig(
            test_set_path=test_set,
            text_units_path=text_units,
            output_dir=tmp_path / "out",
            run_id="v2",
            k1_values=(1.2,),
            b_values=(0.75,),
        )
    )

    scores = (result.run_dir / "bm25_bakeoff_scores.csv").read_text(encoding="utf-8")
    assert "jieba_course_terms_filtered" in scores
    assert "jieba_course_terms_multi_rrf_filtered" in scores
    assert "jieba_course_terms_thuocl_multi_rrf_filtered_section_aware" in scores


def test_noise_audit_flags_repeated_placeholder_text():
    rows = [
        TextUnitRow(ref="clean1111111", text="第三章 处理机调度与死锁介绍调度算法。"),
        TextUnitRow(ref="dirty222222", text="第三章 处理机调度与死锁 gogogogogogogogogogogo。"),
    ]

    findings = audit_text_unit_noise(rows)

    assert findings[0]["ref"] == "dirty222222"
    assert findings[0]["issue"] == "repeated_placeholder_text"


def test_gold_context_expansion_includes_parent_and_child_sections():
    rows = [
        TextUnitRow(
            ref="parent111111",
            text=(
                "chapter: 第一章 操作系统引论. section: 1.1 操作系统的目标和作用. "
                "heading_level: 2. heading_path_text: 第一章 操作系统引论 > 1.1 操作系统的目标和作用. "
                "1.1 操作系统的目标和作用"
            ),
        ),
        TextUnitRow(
            ref="child1111111",
            text=(
                "chapter: 第一章 操作系统引论. section: 1.1 操作系统的目标和作用. "
                "subsection: 1.1.1 操作系统的目标. heading_level: 3. "
                "heading_path_text: 第一章 操作系统引论 > 1.1 操作系统的目标和作用 > 1.1.1 操作系统的目标. "
                "方便性、有效性、可扩充性和开放性。"
            ),
        ),
        TextUnitRow(
            ref="other1111111",
            text=(
                "chapter: 第二章 进程的描述与控制. section: 2.1 前趋图. "
                "heading_level: 2. heading_path_text: 第二章 进程的描述与控制 > 2.1 前趋图."
            ),
        ),
    ]
    context = index_heading_context(rows)

    expanded = expand_gold_refs_with_heading_context(["child1111111"], context)

    assert expanded[:2] == ["child1111111", "parent111111"]
    assert "other1111111" not in expanded


def test_section_aware_search_text_repeats_heading_metadata():
    rows = [
        TextUnitRow(
            ref="target111111",
            text=(
                "chapter: 第一章 操作系统引论. section: 1.1 操作系统的目标和作用. "
                "subsection: 1.1.1 操作系统的目标. heading_path_text: 第一章 操作系统引论 > 1.1 操作系统的目标和作用 > 1.1.1 操作系统的目标. "
                "正文：方便性、有效性、可扩充性和开放性。"
            ),
        )
    ]

    plain = build_search_texts(rows, section_aware=False)
    section_aware = build_search_texts(rows, section_aware=True, heading_weight=4)

    assert section_aware[0].count("操作系统的目标") > plain[0].count("操作系统的目标")


def test_score_question_keeps_raw_gold_metrics_and_adds_expanded_metrics():
    item = _qa_item(
        {
            "id": "Q100",
            "category": "factual_lookup",
            "question": "操作系统的目标是什么？",
            "gold_answer_summary": "操作系统的目标包括方便性和有效性。",
            "gold_entities": ["操作系统"],
            "gold_text_unit_ids": ["child1111111"],
            "must_cite_terms": ["方便性"],
            "negative_terms": [],
        }
    )
    rows = [
        TextUnitRow(
            ref="parent111111",
            text=(
                "chapter: 第一章 操作系统引论. section: 1.1 操作系统的目标和作用. "
                "heading_level: 2. heading_path_text: 第一章 操作系统引论 > 1.1 操作系统的目标和作用. "
            ),
        ),
        TextUnitRow(
            ref="child1111111",
            text=(
                "chapter: 第一章 操作系统引论. section: 1.1 操作系统的目标和作用. "
                "subsection: 1.1.1 操作系统的目标. heading_level: 3. "
                "heading_path_text: 第一章 操作系统引论 > 1.1 操作系统的目标和作用 > 1.1.1 操作系统的目标. "
            ),
        ),
    ]

    scored = score_question(
        item=item,
        ranked_refs=["parent111111"],
        text_by_ref={row.ref: row.text for row in rows},
        heading_context=index_heading_context(rows),
        enable_gold_context_expansion=True,
        config_name="unit",
        k1=1.2,
        b=0.75,
    )

    assert scored["recall_at_3"] == 0.0
    assert scored["expanded_recall_at_3"] > 0.0
    assert scored["raw_gold_refs"] == "child1111111"
    assert scored["expanded_gold_refs"] == "child1111111,parent111111"


def test_dense_rerank_promotes_semantic_match_without_losing_tie_order():
    text_by_ref = {
        "noise1111111": "文件目录使用文件控制块描述目录项。",
        "gold2222222": "处理机调度负责在就绪进程之间分配处理机。",
        "tie33333333": "处理机管理包含进程调度、作业调度和线程调度。",
    }

    def fake_encoder(texts: list[str]) -> np.ndarray:
        vectors = {
            "处理机调度的作用是什么？": [1.0, 0.0],
            text_by_ref["noise1111111"]: [0.0, 1.0],
            text_by_ref["gold2222222"]: [0.95, 0.05],
            text_by_ref["tie33333333"]: [0.95, 0.05],
        }
        return np.asarray([vectors[text] for text in texts], dtype=np.float32)

    reranked = rerank_refs_by_dense_similarity(
        question="处理机调度的作用是什么？",
        ranked_refs=["noise1111111", "tie33333333", "gold2222222"],
        text_by_ref=text_by_ref,
        encoder=fake_encoder,
        top_k=3,
    )

    assert reranked == ["tie33333333", "gold2222222", "noise1111111"]


def test_bakeoff_can_append_dense_rerank_candidate_from_preliminary_best(tmp_path: Path, monkeypatch):
    text_units = tmp_path / "text_units.parquet"
    test_set = tmp_path / "qa_test_set.jsonl"
    _write_text_units(text_units)
    _write_test_set(test_set)

    encoder_calls: list[list[str]] = []

    def fake_encoder(texts: list[str]) -> np.ndarray:
        encoder_calls.append(texts)
        vectors = []
        for text in texts:
            if "蓝鲸" in text or "文件系统" in text or "文件目录" in text:
                vectors.append([1.0, 0.0])
            else:
                vectors.append([0.0, 1.0])
        return np.asarray(vectors, dtype=np.float32)

    monkeypatch.setattr(
        "graphrag_pipeline.scripts.qa_eval.bm25_retrieval_bakeoff.make_dense_rerank_encoder",
        lambda config: fake_encoder,
    )

    result = run_bm25_bakeoff(
        Bm25BakeoffConfig(
            test_set_path=test_set,
            text_units_path=text_units,
            output_dir=tmp_path / "out",
            run_id="dense",
            k1_values=(1.2,),
            b_values=(0.75,),
            enable_dense_rerank=True,
            dense_rerank_policy=DenseRerankPolicy.ALL,
            dense_rerank_candidate_pool_k=3,
        )
    )

    scores = (result.run_dir / "bm25_bakeoff_scores.csv").read_text(encoding="utf-8")
    summary = (result.run_dir / "bm25_bakeoff_summary.md").read_text(encoding="utf-8")

    assert "_dense_rerank_top3" in scores
    assert "dense rerank: `enabled`" in summary
    assert len(encoder_calls) == 1


def test_noise_filter_keeps_graphrag_outputs_untouched(tmp_path: Path):
    text_units = tmp_path / "text_units.parquet"
    test_set = tmp_path / "qa_test_set.jsonl"
    _write_text_units(text_units)
    _write_test_set(test_set)
    before = text_units.read_bytes()

    result = run_bm25_bakeoff(
        Bm25BakeoffConfig(
            test_set_path=test_set,
            text_units_path=text_units,
            output_dir=tmp_path / "out",
            run_id="noise-filter",
            k1_values=(1.2,),
            b_values=(0.75,),
            enable_noise_filter=True,
            write_noise_blacklist=True,
        )
    )

    assert text_units.read_bytes() == before
    assert (result.run_dir / "text_unit_noise_blacklist.json").exists()
    assert (result.run_dir / "noise_filter_report.md").exists()

    by_question = pd.read_csv(result.run_dir / "bm25_bakeoff_by_question.csv")
    assert not by_question["top_refs"].fillna("").str.contains("noise4444444").any()


def test_noise_blacklist_roundtrip():
    rows = [
        TextUnitRow(ref="dirty1111111", text="第三章 gogogogogogogogogogogo。"),
        TextUnitRow(ref="dirty2222222", text="第六章 缓冲池含有坏字�符。"),
        TextUnitRow(ref="clean3333333", text="文件系统使用文件控制块。"),
    ]
    blacklist = generate_noise_blacklist(audit_text_unit_noise(rows))

    assert [item["ref"] for item in blacklist] == ["dirty1111111", "dirty2222222"]
    assert load_noise_blacklist(blacklist) == {"dirty1111111", "dirty2222222"}


def test_dense_rerank_policy_only_reranks_factual_relation():
    factual = _qa_item(
        {
            "id": "Q101",
            "category": "factual_lookup",
            "question": "蓝鲸调度器负责什么？",
            "gold_answer_summary": "蓝鲸调度器负责作业分配。",
            "gold_entities": ["蓝鲸调度器"],
            "gold_text_unit_ids": ["gold11111111"],
            "must_cite_terms": [],
            "negative_terms": [],
        }
    )
    chapter = _qa_item(
        {
            "id": "Q102",
            "category": "chapter_summary",
            "question": "第三章主要讲什么？",
            "gold_answer_summary": "第三章讲处理机调度与死锁。",
            "gold_entities": ["处理机调度"],
            "gold_text_unit_ids": ["chapter11111"],
            "must_cite_terms": [],
            "negative_terms": [],
        }
    )

    assert should_dense_rerank_item(factual, ["noise"], DenseRerankPolicy.FACTUAL_RELATION) is True
    assert should_dense_rerank_item(chapter, ["noise"], DenseRerankPolicy.FACTUAL_RELATION) is False


def test_dense_rerank_policy_only_reranks_top10_miss():
    item = _qa_item(
        {
            "id": "Q101",
            "category": "factual_lookup",
            "question": "蓝鲸调度器负责什么？",
            "gold_answer_summary": "蓝鲸调度器负责作业分配。",
            "gold_entities": ["蓝鲸调度器"],
            "gold_text_unit_ids": ["gold11111111"],
            "must_cite_terms": [],
            "negative_terms": [],
        }
    )

    assert should_dense_rerank_item(item, ["noise1111111", "gold11111111"], DenseRerankPolicy.TOP10_MISS) is False
    assert should_dense_rerank_item(item, ["noise1111111", "noise2222222"], DenseRerankPolicy.TOP10_MISS) is True


def test_chapter_global_query_decomposition_records_subquery_sources(tmp_path: Path):
    text_units = tmp_path / "text_units.parquet"
    test_set = tmp_path / "qa_test_set.jsonl"
    _write_text_units(text_units)
    _write_test_set(test_set)

    item = _qa_item(
        {
            "id": "Q103",
            "category": "global_overview",
            "question": "I/O 管理、磁盘调度和文件系统在课程中如何衔接？",
            "gold_answer_summary": "三者共同支撑外存和设备管理。",
            "gold_entities": ["I/O 管理", "磁盘调度", "文件系统"],
            "gold_text_unit_ids": ["gold22222222bbbb"],
            "must_cite_terms": [],
            "negative_terms": [],
        }
    )

    subqueries = build_query_decomposition(item)

    assert "I/O 管理" in subqueries
    assert "磁盘调度" in subqueries
    assert "文件系统" in subqueries

    result = run_bm25_bakeoff(
        Bm25BakeoffConfig(
            test_set_path=test_set,
            text_units_path=text_units,
            output_dir=tmp_path / "out",
            run_id="decomposition",
            k1_values=(1.2,),
            b_values=(0.75,),
            query_decomposition_policy=QueryDecompositionPolicy.CHAPTER_GLOBAL,
        )
    )
    by_question = pd.read_csv(result.run_dir / "bm25_bakeoff_by_question.csv")

    assert "subqueries" in by_question.columns
    assert "subquery_top_refs" in by_question.columns
    assert "rrf_sources" in by_question.columns


def test_gold_section_audit_flags_narrow_gold(tmp_path: Path):
    rows = [
        TextUnitRow(
            ref="parent111111",
            text=(
                "chapter: 第一章 操作系统引论. section: 1.1 操作系统的目标和作用. "
                "heading_level: 2. heading_path_text: 第一章 操作系统引论 > 1.1 操作系统的目标和作用. "
            ),
        ),
        TextUnitRow(
            ref="child1111111",
            text=(
                "chapter: 第一章 操作系统引论. section: 1.1 操作系统的目标和作用. "
                "subsection: 1.1.1 操作系统的目标. heading_level: 3. "
                "heading_path_text: 第一章 操作系统引论 > 1.1 操作系统的目标和作用 > 1.1.1 操作系统的目标. "
            ),
        ),
    ]
    item = _qa_item(
        {
            "id": "Q100",
            "category": "chapter_summary",
            "question": "操作系统的目标是什么？",
            "gold_answer_summary": "操作系统目标包括方便性和有效性。",
            "gold_entities": ["操作系统"],
            "gold_text_unit_ids": ["child1111111"],
            "must_cite_terms": [],
            "negative_terms": [],
        }
    )

    audit_rows = gold_section_audit_rows(
        items=[item],
        text_units=rows,
        ranked_refs_by_question={"Q100": ["parent111111"]},
    )

    assert audit_rows[0]["audit_label"] == "gold_maybe_too_narrow"
    assert audit_rows[0]["parent_refs"] == "parent111111"


def _qa_item(payload: dict[str, object]):
    from graphrag_pipeline.scripts.qa_eval.test_set_schema import QaTestItem

    return QaTestItem.model_validate(payload)
