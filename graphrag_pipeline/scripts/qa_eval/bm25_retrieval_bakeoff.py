from __future__ import annotations

import argparse
import csv
import json
import math
import os
import re
from dataclasses import dataclass
from datetime import datetime
from enum import Enum
from pathlib import Path
from typing import Callable
from urllib.request import urlretrieve

import jieba.analyse
import numpy as np
import pandas as pd

from graphrag_pipeline.scripts.hybrid_qa.bm25_text_units import (
    TextUnitBm25Config,
    build_text_unit_bm25_from_texts,
    build_text_unit_bm25_from_tokens,
)
from graphrag_pipeline.scripts.qa_eval.semantic_similarity import DEFAULT_BGE_M3_MODEL, _encode_dense_chunks
from graphrag_pipeline.scripts.qa_eval.test_set_schema import QaTestItem, TEXT_UNIT_ID_PREFIX_LEN


DEFAULT_TEST_SET = Path("graphrag_pipeline/data/eval/qa_test_set.jsonl")
DEFAULT_OUTPUT_ROOT = Path("graphrag_pipeline/results/qa_eval/bm25_bakeoff")
DEFAULT_THUOCL_CACHE = Path("graphrag_pipeline/.cache/thuocl")
THUOCL_IT_SOURCE = "https://github.com/thunlp/THUOCL"
THUOCL_IT_RAW_URL = "https://raw.githubusercontent.com/thunlp/THUOCL/master/data/THUOCL_IT.txt"


class DenseRerankPolicy(str, Enum):
    NONE = "none"
    ALL = "all"
    FACTUAL_RELATION = "factual_relation"
    TOP10_MISS = "top10_miss"


class QueryDecompositionPolicy(str, Enum):
    NONE = "none"
    CHAPTER_GLOBAL = "chapter_global"


@dataclass(frozen=True, slots=True)
class Bm25BakeoffConfig:
    test_set_path: Path = DEFAULT_TEST_SET
    text_units_path: Path | None = None
    output_dir: Path = DEFAULT_OUTPUT_ROOT
    run_id: str | None = None
    top_k: int = 10
    max_course_terms: int = 300
    max_thuocl_terms: int = 5000
    download_thuocl_it: bool = False
    thuocl_cache_dir: Path = DEFAULT_THUOCL_CACHE
    thuocl_it_path: Path | None = None
    k1_values: tuple[float, ...] = (0.9, 1.2, 1.5)
    b_values: tuple[float, ...] = (0.55, 0.75, 0.9)
    enable_gold_context_expansion: bool = True
    section_heading_weight: int = 4
    enable_dense_rerank: bool = False
    dense_rerank_policy: DenseRerankPolicy = DenseRerankPolicy.NONE
    dense_rerank_candidate_pool_k: int = 20
    dense_rerank_model: str | None = None
    dense_rerank_device: str | None = None
    dense_rerank_use_fp16: bool = False
    dense_rerank_batch_size: int = 16
    dense_rerank_max_length: int = 8192
    enable_noise_filter: bool = False
    noise_blacklist_path: Path | None = None
    write_noise_blacklist: bool = False
    query_decomposition_policy: QueryDecompositionPolicy = QueryDecompositionPolicy.NONE
    write_gold_section_audit: bool = False


@dataclass(frozen=True, slots=True)
class Bm25BakeoffResult:
    run_dir: Path
    best_config: dict[str, object]
    baseline_config: dict[str, object]
    question_count: int


@dataclass(frozen=True, slots=True)
class TextUnitRow:
    ref: str
    text: str


@dataclass(frozen=True, slots=True)
class BakeoffSearchConfig:
    name: str
    user_terms: list[str]
    exclude_exercises: bool = False
    multi_query_rrf: bool = False
    section_aware: bool = False


@dataclass(frozen=True, slots=True)
class HeadingContextIndex:
    ref_to_path: dict[str, str]
    path_to_refs: dict[str, list[str]]


@dataclass(frozen=True, slots=True)
class SearchQuestionResult:
    refs: list[str]
    subqueries: list[str]
    subquery_top_refs: dict[str, list[str]]
    rrf_sources: dict[str, list[str]]


DenseEncoder = Callable[[list[str]], np.ndarray]


def run_bm25_bakeoff(config: Bm25BakeoffConfig) -> Bm25BakeoffResult:
    run_id = config.run_id or f"bm25-bakeoff-{datetime.now().strftime('%Y%m%d-%H%M%S')}"
    run_dir = config.output_dir / run_id
    run_dir.mkdir(parents=True, exist_ok=True)

    text_units_path = resolve_text_units_path(config.text_units_path)
    items = load_test_items(config.test_set_path)
    text_units = load_text_units(text_units_path)
    text_by_ref = {row.ref: row.text for row in text_units}
    heading_context = index_heading_context(text_units)
    noise_findings = audit_text_unit_noise(text_units)
    generated_noise_blacklist = generate_noise_blacklist(noise_findings)
    loaded_noise_blacklist = load_noise_blacklist(config.noise_blacklist_path) if config.noise_blacklist_path else set()
    noise_blacklist_refs = loaded_noise_blacklist or load_noise_blacklist(generated_noise_blacklist)
    search_text_units = (
        [row for row in text_units if row.ref not in noise_blacklist_refs]
        if config.enable_noise_filter
        else text_units
    )
    corpus_text = "\n".join([*(row.text for row in text_units), *(item.question for item in items)])

    course_terms = extract_course_terms(items, text_units, limit=config.max_course_terms)
    thuocl_path = resolve_thuocl_path(config)
    filtered_thuocl = filter_thuocl_terms(
        thuocl_path,
        corpus_text=corpus_text,
        limit=config.max_thuocl_terms,
    )

    write_json(run_dir / "course_terms.json", course_terms)
    write_json(run_dir / "text_unit_noise_audit.json", noise_findings)
    write_noise_audit(run_dir / "text_unit_noise_audit.md", noise_findings)
    if config.enable_noise_filter or config.write_noise_blacklist:
        write_json(run_dir / "text_unit_noise_blacklist.json", generated_noise_blacklist)
        write_noise_filter_report(
            run_dir / "noise_filter_report.md",
            generated_noise_blacklist=generated_noise_blacklist,
            active_blacklist_refs=noise_blacklist_refs,
            text_unit_count=len(text_units),
            search_text_unit_count=len(search_text_units),
        )
    (run_dir / "filtered_thuocl_it_terms.txt").write_text(
        "\n".join(filtered_thuocl) + ("\n" if filtered_thuocl else ""),
        encoding="utf-8",
    )

    configs = bakeoff_token_configs(course_terms, filtered_thuocl)
    by_question_rows: list[dict[str, object]] = []
    score_rows: list[dict[str, object]] = []

    for search_config in configs:
        token_config = TextUnitBm25Config(
            user_terms=tuple(search_config.user_terms),
            k1=float(config.k1_values[0]),
            b=float(config.b_values[0]),
            enable_query_expansion=True,
            exclude_exercises=search_config.exclude_exercises,
        )
        tokenized_index = build_text_unit_bm25_from_texts(
            refs=[row.ref for row in search_text_units],
            texts=build_search_texts(
                search_text_units,
                section_aware=search_config.section_aware,
                heading_weight=config.section_heading_weight,
            ),
            config=token_config,
        )
        for k1 in config.k1_values:
            for b in config.b_values:
                index = build_text_unit_bm25_from_tokens(
                    refs=tokenized_index.refs,
                    texts=tokenized_index.texts,
                    tokens=tokenized_index.tokens,
                    config=TextUnitBm25Config(
                        user_terms=tuple(search_config.user_terms),
                        k1=float(k1),
                        b=float(b),
                        enable_query_expansion=True,
                        exclude_exercises=search_config.exclude_exercises,
                    ),
                )
                combo_rows: list[dict[str, object]] = []
                for item in items:
                    search_result = search_question_result(
                        index=index,
                        item=item,
                        top_k=config.top_k,
                        multi_query_rrf=search_config.multi_query_rrf,
                        query_decomposition_policy=config.query_decomposition_policy,
                    )
                    combo_rows.append(
                        score_question(
                            item=item,
                            ranked_refs=search_result.refs,
                            text_by_ref=text_by_ref,
                            heading_context=heading_context,
                            enable_gold_context_expansion=config.enable_gold_context_expansion,
                            config_name=search_config.name,
                            k1=float(k1),
                            b=float(b),
                            subqueries=search_result.subqueries,
                            subquery_top_refs=search_result.subquery_top_refs,
                            rrf_sources=search_result.rrf_sources,
                        )
                    )
                by_question_rows.extend(combo_rows)
                score_rows.extend(aggregate_rows(combo_rows, config_name=search_config.name, k1=float(k1), b=float(b)))

    if config.enable_dense_rerank and config.dense_rerank_policy != DenseRerankPolicy.NONE:
        preliminary_best = select_best_overall(score_rows)
        dense_rows = score_dense_rerank_candidate(
            config=config,
            search_config=find_search_config(configs, str(preliminary_best["config_name"])),
            k1=float(preliminary_best["k1"]),
            b=float(preliminary_best["b"]),
            items=items,
            text_units=search_text_units,
            text_by_ref=text_by_ref,
            heading_context=heading_context,
        )
        if dense_rows:
            by_question_rows.extend(dense_rows)
            score_rows.extend(
                aggregate_rows(
                    dense_rows,
                    config_name=str(dense_rows[0]["config_name"]),
                    k1=float(preliminary_best["k1"]),
                    b=float(preliminary_best["b"]),
                )
            )

    write_csv(run_dir / "bm25_bakeoff_by_question.csv", by_question_rows)
    write_csv(run_dir / "bm25_bakeoff_scores.csv", score_rows)

    baseline = find_baseline_score(score_rows, k1_values=config.k1_values, b_values=config.b_values)
    best = select_best_overall(score_rows)
    write_summary(
        run_dir / "bm25_bakeoff_summary.md",
        config=config,
        text_units_path=text_units_path,
        thuocl_path=thuocl_path,
        course_terms=course_terms,
        filtered_thuocl=filtered_thuocl,
        noise_findings=noise_findings,
        active_noise_blacklist_count=len(noise_blacklist_refs) if config.enable_noise_filter else 0,
        search_text_unit_count=len(search_text_units),
        baseline=baseline,
        best=best,
        score_rows=score_rows,
        question_count=len(items),
    )
    write_failures(
        run_dir / "bm25_bakeoff_failures.md",
        best=best,
        by_question_rows=by_question_rows,
    )
    if config.write_gold_section_audit:
        best_question_rows = {
            str(row["question_id"]): str(row["top_refs"]).split(",") if str(row["top_refs"]) else []
            for row in by_question_rows
            if row["config_name"] == best["config_name"]
            and float(row["k1"]) == float(best["k1"])
            and float(row["b"]) == float(best["b"])
        }
        audit_rows = gold_section_audit_rows(
            items=items,
            text_units=text_units,
            ranked_refs_by_question=best_question_rows,
        )
        write_csv(run_dir / "gold_section_audit.csv", audit_rows)
        write_gold_section_audit(run_dir / "gold_section_audit.md", audit_rows)
    return Bm25BakeoffResult(
        run_dir=run_dir,
        best_config=dict(best),
        baseline_config=dict(baseline),
        question_count=len(items),
    )


def resolve_text_units_path(explicit_path: Path | None = None) -> Path:
    if explicit_path is not None:
        if not explicit_path.exists():
            raise FileNotFoundError(f"text_units.parquet does not exist: {explicit_path}")
        return explicit_path
    direct = Path("graphrag_pipeline/output/text_units.parquet")
    if direct.exists():
        return direct
    candidates = sorted(
        Path("graphrag_pipeline/output").glob("*/text_units.parquet"),
        key=lambda path: path.stat().st_mtime_ns,
        reverse=True,
    )
    if not candidates:
        raise FileNotFoundError("cannot find text_units.parquet under graphrag_pipeline/output")
    return candidates[0]


def load_test_items(path: Path) -> list[QaTestItem]:
    return [
        QaTestItem.model_validate_json(line)
        for line in path.read_text(encoding="utf-8").splitlines()
        if line.strip()
    ]


def load_text_units(path: Path) -> list[TextUnitRow]:
    frame = pd.read_parquet(path, columns=["id", "text"])
    rows: list[TextUnitRow] = []
    for _, row in frame.iterrows():
        raw_id = str(row["id"])
        text = "" if pd.isna(row["text"]) else str(row["text"])
        rows.append(TextUnitRow(ref=raw_id[:TEXT_UNIT_ID_PREFIX_LEN], text=text))
    return rows


def extract_course_terms(
    items: list[QaTestItem],
    text_units: list[TextUnitRow],
    *,
    limit: int,
) -> list[str]:
    source_text = "\n".join(row.text for row in text_units)
    qa_text = "\n".join(
        [
            *(item.question for item in items),
            *(term for item in items for term in [*item.gold_entities, *item.must_cite_terms]),
        ]
    )
    terms: list[str] = []
    for item in items:
        for term in [*item.gold_entities, *item.must_cite_terms]:
            append_term(terms, term, corpus_text=f"{source_text}\n{qa_text}")
    for token in jieba.analyse.extract_tags(f"{source_text}\n{qa_text}", topK=max(limit * 4, limit)):
        append_term(terms, token, corpus_text=f"{source_text}\n{qa_text}")
        if len(terms) >= limit:
            break
    return terms[:limit]


def resolve_thuocl_path(config: Bm25BakeoffConfig) -> Path | None:
    if config.thuocl_it_path is not None:
        return config.thuocl_it_path if config.thuocl_it_path.exists() else None
    if config.download_thuocl_it:
        return download_thuocl_it(config.thuocl_cache_dir)
    cached = config.thuocl_cache_dir / "THUOCL_IT.txt"
    return cached if cached.exists() else None


def download_thuocl_it(cache_dir: Path, *, url: str = THUOCL_IT_RAW_URL) -> Path:
    cache_dir.mkdir(parents=True, exist_ok=True)
    target = cache_dir / "THUOCL_IT.txt"
    if target.exists():
        return target
    urlretrieve(url, str(target))
    return target


def filter_thuocl_terms(path: Path | None, *, corpus_text: str, limit: int) -> list[str]:
    if path is None or not path.exists():
        return []
    corpus = corpus_text.casefold()
    terms: list[str] = []
    for raw in path.read_text(encoding="utf-8").splitlines():
        term = raw.strip().split()[0] if raw.strip() else ""
        append_term(terms, term, corpus_text=corpus)
        if len(terms) >= limit:
            break
    return terms


def bakeoff_token_configs(course_terms: list[str], filtered_thuocl: list[str]) -> list[BakeoffSearchConfig]:
    course_thuocl = dedupe_terms([*course_terms, *filtered_thuocl])
    return [
        BakeoffSearchConfig("jieba_baseline", []),
        BakeoffSearchConfig("jieba_course_terms", course_terms),
        BakeoffSearchConfig("jieba_thuocl_it_filtered", filtered_thuocl),
        BakeoffSearchConfig("jieba_course_terms_thuocl", course_thuocl),
        BakeoffSearchConfig("jieba_course_terms_filtered", course_terms, exclude_exercises=True),
        BakeoffSearchConfig(
            "jieba_course_terms_filtered_section_aware",
            course_terms,
            exclude_exercises=True,
            section_aware=True,
        ),
        BakeoffSearchConfig(
            "jieba_course_terms_multi_rrf_filtered",
            course_terms,
            exclude_exercises=True,
            multi_query_rrf=True,
        ),
        BakeoffSearchConfig(
            "jieba_course_terms_multi_rrf_filtered_section_aware",
            course_terms,
            exclude_exercises=True,
            multi_query_rrf=True,
            section_aware=True,
        ),
        BakeoffSearchConfig("jieba_course_terms_thuocl_filtered", course_thuocl, exclude_exercises=True),
        BakeoffSearchConfig(
            "jieba_course_terms_thuocl_filtered_section_aware",
            course_thuocl,
            exclude_exercises=True,
            section_aware=True,
        ),
        BakeoffSearchConfig(
            "jieba_course_terms_thuocl_multi_rrf_filtered",
            course_thuocl,
            exclude_exercises=True,
            multi_query_rrf=True,
        ),
        BakeoffSearchConfig(
            "jieba_course_terms_thuocl_multi_rrf_filtered_section_aware",
            course_thuocl,
            exclude_exercises=True,
            multi_query_rrf=True,
            section_aware=True,
        ),
    ]


def build_search_texts(
    rows: list[TextUnitRow],
    *,
    section_aware: bool,
    heading_weight: int = 4,
) -> list[str]:
    if not section_aware:
        return [row.text for row in rows]
    weighted: list[str] = []
    repeat_count = max(1, heading_weight)
    for row in rows:
        heading_text = " ".join(
            value
            for value in [
                extract_metadata_field(row.text, "chapter"),
                extract_metadata_field(row.text, "section"),
                extract_metadata_field(row.text, "subsection"),
                extract_metadata_field(row.text, "heading_path_text"),
            ]
            if value
        )
        if heading_text:
            weighted.append(f"{row.text}\n{(' ' + heading_text) * repeat_count}")
        else:
            weighted.append(row.text)
    return weighted


def search_question_refs(
    *,
    index,
    question: str,
    top_k: int,
    multi_query_rrf: bool,
) -> list[str]:
    if not multi_query_rrf:
        return [candidate.ref for candidate in index.search(question, top_k=top_k)]
    ranked_lists = [
        [candidate.ref for candidate in index.search(query, top_k=max(top_k, 12))]
        for query in build_multi_queries(question)
    ]
    return rrf_fuse_ranked_refs(ranked_lists, top_k=top_k)


def search_question_result(
    *,
    index,
    item: QaTestItem,
    top_k: int,
    multi_query_rrf: bool,
    query_decomposition_policy: QueryDecompositionPolicy,
) -> SearchQuestionResult:
    should_decompose = (
        query_decomposition_policy is QueryDecompositionPolicy.CHAPTER_GLOBAL
        and item.category.value in {"chapter_summary", "global_overview"}
    )
    if not should_decompose:
        return SearchQuestionResult(
            refs=search_question_refs(
                index=index,
                question=item.question,
                top_k=top_k,
                multi_query_rrf=multi_query_rrf,
            ),
            subqueries=[],
            subquery_top_refs={},
            rrf_sources={},
        )

    subqueries = build_query_decomposition(item)
    ranked_lists: list[list[str]] = []
    subquery_top_refs: dict[str, list[str]] = {}
    rrf_sources: dict[str, list[str]] = {}
    for query in subqueries:
        refs = [candidate.ref for candidate in index.search(query, top_k=max(top_k, 10))]
        ranked_lists.append(refs)
        subquery_top_refs[query] = refs[:top_k]
        for ref in refs[:top_k]:
            rrf_sources.setdefault(ref, []).append(query)
    return SearchQuestionResult(
        refs=rrf_fuse_ranked_refs(ranked_lists, top_k=top_k),
        subqueries=subqueries,
        subquery_top_refs=subquery_top_refs,
        rrf_sources=rrf_sources,
    )


def build_query_decomposition(item: QaTestItem) -> list[str]:
    queries = build_multi_queries(item.question)
    for term in [*item.gold_entities, *item.must_cite_terms]:
        _append_query(queries, str(term))
    if item.category.value == "chapter_summary":
        for match in re.finditer(r"第\s*[一二三四五六七八九十\d]+\s*章", item.question):
            _append_query(queries, match.group(0))
    return queries


def score_dense_rerank_candidate(
    *,
    config: Bm25BakeoffConfig,
    search_config: BakeoffSearchConfig,
    k1: float,
    b: float,
    items: list[QaTestItem],
    text_units: list[TextUnitRow],
    text_by_ref: dict[str, str],
    heading_context: HeadingContextIndex,
) -> list[dict[str, object]]:
    tokenized_index = build_text_unit_bm25_from_texts(
        refs=[row.ref for row in text_units],
        texts=build_search_texts(
            text_units,
            section_aware=search_config.section_aware,
            heading_weight=config.section_heading_weight,
        ),
        config=TextUnitBm25Config(
            user_terms=tuple(search_config.user_terms),
            k1=float(k1),
            b=float(b),
            enable_query_expansion=True,
            exclude_exercises=search_config.exclude_exercises,
        ),
    )
    index = build_text_unit_bm25_from_tokens(
        refs=tokenized_index.refs,
        texts=tokenized_index.texts,
        tokens=tokenized_index.tokens,
        config=TextUnitBm25Config(
            user_terms=tuple(search_config.user_terms),
            k1=float(k1),
            b=float(b),
            enable_query_expansion=True,
            exclude_exercises=search_config.exclude_exercises,
        ),
    )
    encoder = make_dense_rerank_encoder(config)
    rerank_name = f"{search_config.name}_dense_rerank_top{config.dense_rerank_candidate_pool_k}"
    candidates_by_question: dict[str, list[str]] = {
        item.id: search_question_refs(
            index=index,
            question=item.question,
            top_k=max(config.top_k, config.dense_rerank_candidate_pool_k),
            multi_query_rrf=search_config.multi_query_rrf,
        )
        for item in items
    }
    attempted_items = [
        item
        for item in items
        if should_dense_rerank_item(
            item,
            candidates_by_question[item.id],
            config.dense_rerank_policy,
        )
    ]
    attempted_ids = {item.id for item in attempted_items}
    reranked_by_question: dict[str, list[str]] = {
        item.id: candidates_by_question[item.id][: config.top_k]
        for item in items
    }
    if attempted_items:
        attempted_candidates = {item.id: candidates_by_question[item.id] for item in attempted_items}
        question_vectors, ref_vectors = encode_dense_rerank_inputs(
            items=attempted_items,
            candidates_by_question=attempted_candidates,
            text_by_ref=text_by_ref,
            encoder=encoder,
        )
        for item in attempted_items:
            reranked_by_question[item.id] = rerank_refs_by_dense_vectors(
                ranked_refs=candidates_by_question[item.id],
                query_vector=question_vectors[item.id],
                ref_vectors=ref_vectors,
                top_k=config.top_k,
            )

    rows: list[dict[str, object]] = []
    for item in items:
        base_score = score_refs(
            dedupe_refs(candidates_by_question[item.id])[: config.top_k],
            dedupe_refs(item.gold_text_unit_ids),
        )
        scored = score_question(
            item=item,
            ranked_refs=reranked_by_question[item.id],
            text_by_ref=text_by_ref,
            heading_context=heading_context,
            enable_gold_context_expansion=config.enable_gold_context_expansion,
            config_name=rerank_name,
            k1=float(k1),
            b=float(b),
        )
        delta_recall_at_3 = float(scored["recall_at_3"]) - float(base_score["recall_at_3"])
        scored.update(
            {
                "dense_rerank_attempted": item.id in attempted_ids,
                "dense_rerank_base_recall_at_3": base_score["recall_at_3"],
                "dense_rerank_delta_recall_at_3": round(delta_recall_at_3, 4),
                "dense_rerank_helped": delta_recall_at_3 > 0,
                "dense_rerank_hurt": delta_recall_at_3 < 0,
            }
        )
        rows.append(scored)
    return rows


def encode_dense_rerank_inputs(
    *,
    items: list[QaTestItem],
    candidates_by_question: dict[str, list[str]],
    text_by_ref: dict[str, str],
    encoder: DenseEncoder,
) -> tuple[dict[str, np.ndarray], dict[str, np.ndarray]]:
    keys: list[tuple[str, str]] = []
    texts: list[str] = []
    for item in items:
        keys.append(("question", item.id))
        texts.append(item.question)

    seen_refs: set[str] = set()
    for refs in candidates_by_question.values():
        for ref in dedupe_refs(refs):
            if ref in seen_refs or not text_by_ref.get(ref):
                continue
            seen_refs.add(ref)
            keys.append(("ref", ref))
            texts.append(text_by_ref[ref])

    vectors = _normalize_vectors(np.asarray(encoder(texts), dtype=np.float32))
    if vectors.ndim != 2 or vectors.shape[0] != len(texts):
        raise ValueError(f"dense encoder returned invalid shape: {vectors.shape}, expected {len(texts)} rows")

    question_vectors: dict[str, np.ndarray] = {}
    ref_vectors: dict[str, np.ndarray] = {}
    for key, vector in zip(keys, vectors, strict=True):
        kind, value = key
        if kind == "question":
            question_vectors[value] = vector
        else:
            ref_vectors[value] = vector
    return question_vectors, ref_vectors


def rerank_refs_by_dense_similarity(
    *,
    question: str,
    ranked_refs: list[str],
    text_by_ref: dict[str, str],
    encoder: DenseEncoder,
    top_k: int,
) -> list[str]:
    refs = [ref for ref in dedupe_refs(ranked_refs) if text_by_ref.get(ref)]
    if not refs or top_k <= 0:
        return []
    texts = [question, *(text_by_ref[ref] for ref in refs)]
    vectors = _normalize_vectors(np.asarray(encoder(texts), dtype=np.float32))
    if vectors.ndim != 2 or vectors.shape[0] != len(texts):
        raise ValueError(f"dense encoder returned invalid shape: {vectors.shape}, expected {len(texts)} rows")
    scores = vectors[1:] @ vectors[0]
    ordered = sorted(range(len(refs)), key=lambda index: (-float(scores[index]), index, refs[index]))
    return [refs[index] for index in ordered[:top_k]]


def rerank_refs_by_dense_vectors(
    *,
    ranked_refs: list[str],
    query_vector: np.ndarray,
    ref_vectors: dict[str, np.ndarray],
    top_k: int,
) -> list[str]:
    refs = [ref for ref in dedupe_refs(ranked_refs) if ref in ref_vectors]
    if not refs or top_k <= 0:
        return []
    query = np.asarray(query_vector, dtype=np.float32)
    scores = {ref: float(np.asarray(ref_vectors[ref], dtype=np.float32) @ query) for ref in refs}
    ordered = sorted(range(len(refs)), key=lambda index: (-scores[refs[index]], index, refs[index]))
    return [refs[index] for index in ordered[:top_k]]


def make_dense_rerank_encoder(config: Bm25BakeoffConfig) -> DenseEncoder:
    model_name = config.dense_rerank_model or os.environ.get("CKQA_BGE_M3_MODEL", DEFAULT_BGE_M3_MODEL)
    device = config.dense_rerank_device or os.environ.get("CKQA_BGE_M3_DEVICE") or None

    def encode(texts: list[str]) -> np.ndarray:
        return _encode_dense_chunks(
            texts,
            model_name=model_name,
            max_length=config.dense_rerank_max_length,
            device=device,
            use_fp16=config.dense_rerank_use_fp16,
            batch_size=config.dense_rerank_batch_size,
        )

    return encode


def _normalize_vectors(vectors: np.ndarray) -> np.ndarray:
    if vectors.ndim != 2:
        return vectors
    norms = np.linalg.norm(vectors, axis=1, keepdims=True)
    return vectors / np.maximum(norms, 1e-12)


def build_multi_queries(question: str) -> list[str]:
    cleaned = _strip_question_marks(question)
    original = (question or "").strip()
    queries = [original or cleaned]
    for part in re.split(r"[、，,；;]", cleaned):
        for subpart in re.split(r"(?:和|与|以及)", part):
            _append_query(queries, _clean_subquery(subpart))
    for match in re.finditer(r"从(.+?)扩展到(.+)", cleaned):
        _append_query(queries, _clean_subquery(match.group(1)))
        for subpart in re.split(r"(?:和|与|以及|、|，|,)", match.group(2)):
            _append_query(queries, _clean_subquery(subpart))
    return queries


def rrf_fuse_ranked_refs(
    ranked_lists: list[list[str]],
    *,
    top_k: int,
    rrf_k: int = 60,
) -> list[str]:
    scores: dict[str, float] = {}
    best_rank: dict[str, int] = {}
    for ranked in ranked_lists:
        for rank, ref in enumerate(dedupe_refs(ranked), start=1):
            scores[ref] = scores.get(ref, 0.0) + 1.0 / (rrf_k + rank)
            best_rank[ref] = min(best_rank.get(ref, rank), rank)
    ordered = sorted(scores, key=lambda ref: (-scores[ref], best_rank[ref], ref))
    return ordered[:top_k]


def score_question(
    *,
    item: QaTestItem,
    ranked_refs: list[str],
    text_by_ref: dict[str, str],
    heading_context: HeadingContextIndex,
    enable_gold_context_expansion: bool,
    config_name: str,
    k1: float,
    b: float,
    subqueries: list[str] | None = None,
    subquery_top_refs: dict[str, list[str]] | None = None,
    rrf_sources: dict[str, list[str]] | None = None,
) -> dict[str, object]:
    ranked = dedupe_refs(ranked_refs)
    raw_gold = dedupe_refs(item.gold_text_unit_ids)
    expanded_gold = (
        expand_gold_refs_with_heading_context(raw_gold, heading_context)
        if enable_gold_context_expansion
        else raw_gold
    )
    metrics = score_refs(ranked, raw_gold)
    expanded_metrics = {
        f"expanded_{key}": value
        for key, value in score_refs(ranked, expanded_gold).items()
    }
    return {
        "config_name": config_name,
        "k1": k1,
        "b": b,
        "question_id": item.id,
        "category": item.category.value,
        "question": item.question,
        "raw_gold_refs": ",".join(raw_gold),
        "expanded_gold_refs": ",".join(expanded_gold),
        "gold_refs": ",".join(raw_gold),
        "top_refs": ",".join(ranked),
        "top_previews": " || ".join(preview(text_by_ref.get(ref, "")) for ref in ranked[:10]),
        "subqueries": json.dumps(subqueries or [], ensure_ascii=False),
        "subquery_top_refs": json.dumps(subquery_top_refs or {}, ensure_ascii=False),
        "rrf_sources": json.dumps(rrf_sources or {}, ensure_ascii=False),
        **metrics,
        **expanded_metrics,
    }


def score_refs(ranked_refs: list[str], gold_refs: list[str]) -> dict[str, float | int | str]:
    gold = set(gold_refs)
    first_rank = 0
    for index, ref in enumerate(ranked_refs, start=1):
        if ref in gold:
            first_rank = index
            break

    out: dict[str, float | int | str] = {"first_gold_rank": first_rank}
    for cutoff in (1, 3, 5, 10):
        hits = len(set(ranked_refs[:cutoff]) & gold)
        out[f"recall_at_{cutoff}"] = round(hits / len(gold), 4) if gold else 0.0
    out["rr"] = round(1 / first_rank, 4) if first_rank else 0.0
    out["ndcg_at_5"] = round(ndcg_at_k(ranked_refs, gold_refs, 5), 4)
    return out


def ndcg_at_k(ranked_refs: list[str], gold_refs: list[str], k: int) -> float:
    gold = set(gold_refs)
    if not gold:
        return 0.0
    dcg = 0.0
    for rank, ref in enumerate(ranked_refs[:k], start=1):
        if ref in gold:
            dcg += 1.0 / math.log2(rank + 1)
    ideal_hits = min(len(gold), k)
    idcg = sum(1.0 / math.log2(rank + 1) for rank in range(1, ideal_hits + 1))
    return 0.0 if idcg == 0 else dcg / idcg


def aggregate_rows(
    rows: list[dict[str, object]],
    *,
    config_name: str,
    k1: float,
    b: float,
) -> list[dict[str, object]]:
    out = [aggregate_group(rows, config_name=config_name, k1=k1, b=b, category="__overall__")]
    for category in sorted({str(row["category"]) for row in rows}):
        group = [row for row in rows if row["category"] == category]
        out.append(aggregate_group(group, config_name=config_name, k1=k1, b=b, category=category))
    return out


def aggregate_group(
    rows: list[dict[str, object]],
    *,
    config_name: str,
    k1: float,
    b: float,
    category: str,
) -> dict[str, object]:
    metrics = ["recall_at_1", "recall_at_3", "recall_at_5", "recall_at_10", "rr", "ndcg_at_5"]
    out = {
        "config_name": config_name,
        "k1": k1,
        "b": b,
        "category": category,
        "n": len(rows),
        **{metric: round(mean(float(row[metric]) for row in rows), 4) for metric in metrics},
        "mean_first_gold_rank": round(mean_first_rank(rows), 4),
    }
    expanded_metrics = [f"expanded_{metric}" for metric in metrics]
    if rows and all(metric in rows[0] for metric in expanded_metrics):
        out.update(
            {
                metric: round(mean(float(row[metric]) for row in rows), 4)
                for metric in expanded_metrics
            }
        )
        out["expanded_mean_first_gold_rank"] = round(mean_first_rank(rows, key="expanded_first_gold_rank"), 4)
    if rows and "dense_rerank_attempted" in rows[0]:
        attempted = [row for row in rows if bool(row.get("dense_rerank_attempted"))]
        out.update(
            {
                "dense_rerank_attempted_count": len(attempted),
                "dense_rerank_helped_count": sum(1 for row in attempted if bool(row.get("dense_rerank_helped"))),
                "dense_rerank_hurt_count": sum(1 for row in attempted if bool(row.get("dense_rerank_hurt"))),
                "dense_rerank_mean_delta_recall_at_3": round(
                    mean(float(row.get("dense_rerank_delta_recall_at_3", 0.0)) for row in attempted),
                    4,
                ),
            }
        )
    return out


def should_dense_rerank_item(
    item: QaTestItem,
    ranked_refs: list[str],
    policy: DenseRerankPolicy,
) -> bool:
    if policy is DenseRerankPolicy.NONE:
        return False
    if policy is DenseRerankPolicy.ALL:
        return True
    if policy is DenseRerankPolicy.FACTUAL_RELATION:
        return item.category.value in {"factual_lookup", "relation_reasoning"}
    if policy is DenseRerankPolicy.TOP10_MISS:
        gold = set(dedupe_refs(item.gold_text_unit_ids))
        return not bool(set(dedupe_refs(ranked_refs)[:10]) & gold)
    raise ValueError(f"unsupported dense rerank policy: {policy}")


def write_summary(
    path: Path,
    *,
    config: Bm25BakeoffConfig,
    text_units_path: Path,
    thuocl_path: Path | None,
    course_terms: list[str],
    filtered_thuocl: list[str],
    noise_findings: list[dict[str, object]],
    active_noise_blacklist_count: int,
    search_text_unit_count: int,
    baseline: dict[str, object],
    best: dict[str, object],
    score_rows: list[dict[str, object]],
    question_count: int,
) -> None:
    delta = float(best["recall_at_3"]) - float(baseline["recall_at_3"])
    recommendation = (
        "best config 相比 baseline 的 recall_at_3 有提升，可进入小规模 one-shot smoke。"
        if delta > 0
        else "best config 相比 baseline 没有 recall_at_3 提升，不建议进入真实 API smoke。"
    )
    overall = [row for row in score_rows if row["category"] == "__overall__"]
    lines = [
        "# BM25 Retrieval Bakeoff",
        "",
        "## 数据来源",
        "",
        f"- test set: `{config.test_set_path}`",
        f"- text units: `{text_units_path}`",
        f"- questions: `{question_count}`",
        f"- THUOCL source: `{THUOCL_IT_SOURCE}`",
        f"- THUOCL cache: `{thuocl_path or 'not available'}`",
        f"- course terms: `{len(course_terms)}`",
        f"- filtered THUOCL IT terms: `{len(filtered_thuocl)}`",
        f"- gold context expansion: `{'enabled' if config.enable_gold_context_expansion else 'disabled'}`",
        f"- section-aware heading weight: `{config.section_heading_weight}`",
        f"- dense rerank: `{'enabled' if config.enable_dense_rerank else 'disabled'}`",
        f"- dense rerank policy: `{config.dense_rerank_policy.value}`",
        f"- dense rerank candidate pool: `{config.dense_rerank_candidate_pool_k}`",
        f"- dense rerank model: `{config.dense_rerank_model or os.environ.get('CKQA_BGE_M3_MODEL', DEFAULT_BGE_M3_MODEL)}`",
        f"- dense rerank device: `{config.dense_rerank_device or os.environ.get('CKQA_BGE_M3_DEVICE') or 'auto'}`",
        f"- noise audit findings: `{len(noise_findings)}`",
        f"- noise filter: `{'enabled' if config.enable_noise_filter else 'disabled'}`",
        f"- active noise blacklist refs: `{active_noise_blacklist_count}`",
        f"- text units used by retrieval: `{search_text_unit_count}`",
        f"- query decomposition policy: `{config.query_decomposition_policy.value}`",
        "",
        "## best config",
        "",
        f"- config: `{best['config_name']}`",
        f"- k1/b: `{best['k1']}` / `{best['b']}`",
        f"- recall_at_3: `{best['recall_at_3']}`",
        f"- expanded_recall_at_3: `{best.get('expanded_recall_at_3', 'n/a')}`",
        f"- rr: `{best['rr']}`",
        f"- expanded_rr: `{best.get('expanded_rr', 'n/a')}`",
        f"- ndcg_at_5: `{best['ndcg_at_5']}`",
        f"- expanded_ndcg_at_5: `{best.get('expanded_ndcg_at_5', 'n/a')}`",
        "",
        "## baseline config",
        "",
        f"- config: `{baseline['config_name']}`",
        f"- k1/b: `{baseline['k1']}` / `{baseline['b']}`",
        f"- recall_at_3: `{baseline['recall_at_3']}`",
        f"- expanded_recall_at_3: `{baseline.get('expanded_recall_at_3', 'n/a')}`",
        f"- rr: `{baseline['rr']}`",
        f"- expanded_rr: `{baseline.get('expanded_rr', 'n/a')}`",
        f"- ndcg_at_5: `{baseline['ndcg_at_5']}`",
        f"- expanded_ndcg_at_5: `{baseline.get('expanded_ndcg_at_5', 'n/a')}`",
        "",
        "## 结论",
        "",
        f"- recall_at_3 delta vs baseline: `{delta:+.4f}`",
        f"- {recommendation}",
        "",
        "## overall scores",
        "",
        "| config | k1 | b | r@1 | r@3 | r@5 | r@10 | rr | ndcg@5 | expanded r@3 | expanded rr |",
        "| --- | ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: |",
    ]
    for row in sorted(overall, key=lambda item: (-float(item["recall_at_3"]), -float(item["rr"]), str(item["config_name"]))):
        lines.append(
            f"| {row['config_name']} | {float(row['k1']):.2f} | {float(row['b']):.2f} | "
            f"{float(row['recall_at_1']):.4f} | {float(row['recall_at_3']):.4f} | "
            f"{float(row['recall_at_5']):.4f} | {float(row['recall_at_10']):.4f} | "
            f"{float(row['rr']):.4f} | {float(row['ndcg_at_5']):.4f} | "
            f"{float(row.get('expanded_recall_at_3', 0.0)):.4f} | {float(row.get('expanded_rr', 0.0)):.4f} |"
        )
    dense_overall = [
        row
        for row in overall
        if str(row["config_name"]).endswith(f"_dense_rerank_top{config.dense_rerank_candidate_pool_k}")
    ]
    if dense_overall:
        lines.extend(
            [
                "",
                "## dense rerank diagnostics",
                "",
                "| config | attempted | helped | hurt | mean delta r@3 |",
                "| --- | ---: | ---: | ---: | ---: |",
            ]
        )
        for row in dense_overall:
            lines.append(
                f"| {row['config_name']} | {int(row.get('dense_rerank_attempted_count', 0))} | "
                f"{int(row.get('dense_rerank_helped_count', 0))} | "
                f"{int(row.get('dense_rerank_hurt_count', 0))} | "
                f"{float(row.get('dense_rerank_mean_delta_recall_at_3', 0.0)):.4f} |"
            )
    lines.extend(
        [
            "",
            "## 实验结论与下一步建议",
            "",
            f"- 主指标 best vs baseline recall_at_3 delta 为 `{delta:+.4f}`。",
            f"- {recommendation}",
            "- 本报告只评估离线 evidence retrieval；不能直接证明最终问答质量。",
        ]
    )
    if config.enable_noise_filter:
        lines.append("- sidecar 噪声过滤已启用，未修改 GraphRAG 原始 output 产物。")
    if config.dense_rerank_policy is not DenseRerankPolicy.NONE:
        lines.append("- dense rerank 为诊断性 gated 策略；若 hurt_count 不为 0，不建议默认进入真实问答链路。")
    if config.query_decomposition_policy is QueryDecompositionPolicy.CHAPTER_GLOBAL:
        lines.append("- chapter/global query decomposition 仅用于离线召回验证；真实 prompt 注入仍需单独 smoke 批准。")
    path.write_text("\n".join(lines) + "\n", encoding="utf-8")


def write_failures(
    path: Path,
    *,
    best: dict[str, object],
    by_question_rows: list[dict[str, object]],
) -> None:
    focus_questions = {"Q019", "Q025", "Q029", "Q031"}
    rows = [
        row
        for row in by_question_rows
        if row["config_name"] == best["config_name"]
        and float(row["k1"]) == float(best["k1"])
        and float(row["b"]) == float(best["b"])
        and (
            float(row["recall_at_10"]) == 0.0
            or (
                str(row["question_id"]) in focus_questions
                and str(row.get("subqueries", "[]")) not in ("", "[]")
            )
        )
    ]
    lines = [
        "# BM25 Bakeoff Failure Cases",
        "",
        f"- best config: `{best['config_name']}` k1=`{best['k1']}` b=`{best['b']}`",
        f"- failures without gold in top10: `{len(rows)}`",
        f"- failures without expanded gold in top10: `{sum(1 for row in rows if float(row.get('expanded_recall_at_10', 0.0)) == 0.0)}`",
        "",
    ]
    for row in rows:
        lines.extend(
            [
                f"## {row['question_id']} ({row['category']})",
                "",
                f"- question: {row['question']}",
                f"- raw_gold_refs: `{row.get('raw_gold_refs', row['gold_refs'])}`",
                f"- expanded_gold_refs: `{row.get('expanded_gold_refs', row['gold_refs'])}`",
                f"- expanded_recall_at_10: `{row.get('expanded_recall_at_10', 'n/a')}`",
                f"- top_refs: `{row['top_refs']}`",
                f"- top_previews: {row['top_previews']}",
                f"- subqueries: `{row.get('subqueries', '[]')}`",
                f"- subquery_top_refs: `{row.get('subquery_top_refs', '{}')}`",
                f"- rrf_sources: `{row.get('rrf_sources', '{}')}`",
                "",
            ]
        )
    path.write_text("\n".join(lines), encoding="utf-8")


def write_noise_audit(path: Path, findings: list[dict[str, object]]) -> None:
    lines = [
        "# Text Unit Noise Audit",
        "",
        f"- findings: `{len(findings)}`",
        "",
    ]
    for finding in findings:
        lines.extend(
            [
                f"## {finding['ref']}",
                "",
                f"- issue: `{finding['issue']}`",
                f"- preview: {finding['preview']}",
                "",
            ]
        )
    path.write_text("\n".join(lines), encoding="utf-8")


def write_noise_filter_report(
    path: Path,
    *,
    generated_noise_blacklist: list[dict[str, object]],
    active_blacklist_refs: set[str],
    text_unit_count: int,
    search_text_unit_count: int,
) -> None:
    lines = [
        "# Text Unit Noise Filter Report",
        "",
        "- scope: sidecar only; GraphRAG parquet / LanceDB outputs are not modified.",
        f"- original text units: `{text_unit_count}`",
        f"- active blacklist refs: `{len(active_blacklist_refs)}`",
        f"- search text units after filter: `{search_text_unit_count}`",
        "",
        "| ref | issue | preview | active |",
        "| --- | --- | --- | --- |",
    ]
    for item in generated_noise_blacklist:
        ref = str(item["ref"])
        lines.append(
            f"| {ref} | {item['issue']} | {str(item['preview']).replace('|', '/')} | "
            f"{'yes' if ref in active_blacklist_refs else 'no'} |"
        )
    path.write_text("\n".join(lines) + "\n", encoding="utf-8")


def find_score(
    rows: list[dict[str, object]],
    *,
    config_name: str,
    k1: float,
    b: float,
    category: str,
) -> dict[str, object]:
    for row in rows:
        if (
            row["config_name"] == config_name
            and float(row["k1"]) == float(k1)
            and float(row["b"]) == float(b)
            and row["category"] == category
        ):
            return row
    raise ValueError(f"missing score row for {config_name} k1={k1} b={b} category={category}")


def find_baseline_score(
    rows: list[dict[str, object]],
    *,
    k1_values: tuple[float, ...],
    b_values: tuple[float, ...],
) -> dict[str, object]:
    preferred = (1.5, 0.75)
    candidates = [
        row
        for row in rows
        if row["config_name"] == "jieba_baseline" and row["category"] == "__overall__"
    ]
    for row in candidates:
        if float(row["k1"]) == preferred[0] and float(row["b"]) == preferred[1]:
            return row
    if not candidates:
        raise ValueError("missing jieba_baseline overall score row")
    for k1 in k1_values:
        for b in b_values:
            for row in candidates:
                if float(row["k1"]) == float(k1) and float(row["b"]) == float(b):
                    return row
    return candidates[0]


def select_best_overall(rows: list[dict[str, object]]) -> dict[str, object]:
    return max(
        (row for row in rows if row["category"] == "__overall__"),
        key=lambda row: (
            float(row["recall_at_3"]),
            float(row["rr"]),
            float(row["ndcg_at_5"]),
            float(row["recall_at_10"]),
        ),
    )


def find_search_config(configs: list[BakeoffSearchConfig], name: str) -> BakeoffSearchConfig:
    for config in configs:
        if config.name == name:
            return config
    raise ValueError(f"missing bakeoff search config: {name}")


METADATA_FIELD_NAMES = (
    "document_type",
    "chapter",
    "section",
    "subsection",
    "heading_level",
    "heading_path_text",
    "page_start",
    "page_end",
    "section_level",
    "source_file",
    "course_id",
)


def audit_text_unit_noise(rows: list[TextUnitRow]) -> list[dict[str, object]]:
    findings: list[dict[str, object]] = []
    repeated_placeholder = re.compile(r"([A-Za-z]{2,5})\1{3,}")
    for row in rows:
        compact = re.sub(r"\s+", "", row.text or "")
        if repeated_placeholder.search(compact):
            findings.append(
                {
                    "ref": row.ref,
                    "issue": "repeated_placeholder_text",
                    "preview": preview(row.text, limit=160),
                }
            )
            continue
        if "\ufffd" in row.text:
            findings.append(
                {
                    "ref": row.ref,
                    "issue": "replacement_character",
                    "preview": preview(row.text, limit=160),
                }
            )
    return findings


def generate_noise_blacklist(findings: list[dict[str, object]]) -> list[dict[str, object]]:
    allowed = {"repeated_placeholder_text", "replacement_character"}
    blacklist: list[dict[str, object]] = []
    for finding in findings:
        issue = str(finding.get("issue", ""))
        ref = str(finding.get("ref", ""))[:TEXT_UNIT_ID_PREFIX_LEN]
        if issue not in allowed or not ref:
            continue
        blacklist.append(
            {
                "ref": ref,
                "issue": issue,
                "preview": str(finding.get("preview", "")),
            }
        )
    return blacklist


def load_noise_blacklist(source: Path | list[dict[str, object]] | list[str] | None) -> set[str]:
    if source is None:
        return set()
    if isinstance(source, Path):
        if not source.exists():
            return set()
        payload = json.loads(source.read_text(encoding="utf-8"))
    else:
        payload = source
    refs: set[str] = set()
    for item in payload:
        raw_ref = item.get("ref", "") if isinstance(item, dict) else item
        ref = str(raw_ref or "").strip()[:TEXT_UNIT_ID_PREFIX_LEN]
        if ref:
            refs.add(ref)
    return refs


def index_heading_context(rows: list[TextUnitRow]) -> HeadingContextIndex:
    ref_to_path: dict[str, str] = {}
    path_to_refs: dict[str, list[str]] = {}
    for row in rows:
        path = heading_path_for_text(row.text)
        if not path:
            continue
        ref_to_path[row.ref] = path
        path_to_refs.setdefault(path, []).append(row.ref)
    return HeadingContextIndex(ref_to_path=ref_to_path, path_to_refs=path_to_refs)


def expand_gold_refs_with_heading_context(
    gold_refs: list[str],
    context: HeadingContextIndex,
    *,
    max_refs: int = 12,
) -> list[str]:
    expanded = dedupe_refs(gold_refs)
    for raw_ref in dedupe_refs(gold_refs):
        path = context.ref_to_path.get(raw_ref)
        if not path:
            continue
        parts = [part.strip() for part in path.split(">") if part.strip()]
        parent_paths = [" > ".join(parts[:index]) for index in range(len(parts) - 1, 0, -1)]
        child_paths = [
            candidate
            for candidate in sorted(context.path_to_refs)
            if candidate != path and candidate.startswith(f"{path} > ")
        ]
        same_and_related_paths = [path, *parent_paths, *child_paths]
        for related_path in same_and_related_paths:
            for related_ref in context.path_to_refs.get(related_path, []):
                if related_ref not in expanded:
                    expanded.append(related_ref)
                if len(expanded) >= max_refs:
                    return expanded
    return expanded


def gold_section_audit_rows(
    *,
    items: list[QaTestItem],
    text_units: list[TextUnitRow],
    ranked_refs_by_question: dict[str, list[str]],
) -> list[dict[str, object]]:
    context = index_heading_context(text_units)
    rows: list[dict[str, object]] = []
    for item in items:
        raw_gold = dedupe_refs(item.gold_text_unit_ids)
        ranked = dedupe_refs(ranked_refs_by_question.get(item.id, []))
        parent_refs, sibling_refs, child_refs = related_section_refs(raw_gold, context)
        raw_hit = bool(set(ranked[:10]) & set(raw_gold))
        parent_hit = bool(set(ranked[:10]) & set(parent_refs))
        sibling_hit = bool(set(ranked[:10]) & set(sibling_refs))
        child_hit = bool(set(ranked[:10]) & set(child_refs))
        expanded_refs = dedupe_refs([*raw_gold, *parent_refs, *sibling_refs, *child_refs])
        expanded_hit = bool(set(ranked[:10]) & set(expanded_refs))
        if raw_hit:
            label = "ok"
        elif parent_hit or sibling_hit or child_hit:
            label = "gold_maybe_too_narrow"
        elif item.category.value in {"chapter_summary", "global_overview"}:
            label = "question_too_broad"
        else:
            label = "retrieval_miss"
        rows.append(
            {
                "question_id": item.id,
                "category": item.category.value,
                "question": item.question,
                "raw_gold_refs": ",".join(raw_gold),
                "heading_path": " || ".join(context.ref_to_path.get(ref, "") for ref in raw_gold if context.ref_to_path.get(ref)),
                "parent_refs": ",".join(parent_refs),
                "same_section_sibling_refs": ",".join(sibling_refs),
                "child_refs": ",".join(child_refs),
                "top10_refs": ",".join(ranked[:10]),
                "raw_top10_hit": raw_hit,
                "expanded_top10_hit": expanded_hit,
                "parent_top10_hit": parent_hit,
                "sibling_top10_hit": sibling_hit,
                "child_top10_hit": child_hit,
                "audit_label": label,
            }
        )
    return rows


def related_section_refs(
    raw_gold: list[str],
    context: HeadingContextIndex,
) -> tuple[list[str], list[str], list[str]]:
    parent_refs: list[str] = []
    sibling_refs: list[str] = []
    child_refs: list[str] = []
    for ref in raw_gold:
        path = context.ref_to_path.get(ref, "")
        if not path:
            continue
        parts = [part.strip() for part in path.split(">") if part.strip()]
        parent_paths = [" > ".join(parts[:index]) for index in range(len(parts) - 1, 0, -1)]
        for parent_path in parent_paths:
            for parent_ref in context.path_to_refs.get(parent_path, []):
                if parent_ref not in raw_gold and parent_ref not in parent_refs:
                    parent_refs.append(parent_ref)
        for sibling_ref in context.path_to_refs.get(path, []):
            if sibling_ref not in raw_gold and sibling_ref not in sibling_refs:
                sibling_refs.append(sibling_ref)
        for candidate_path in sorted(context.path_to_refs):
            if candidate_path != path and candidate_path.startswith(f"{path} > "):
                for child_ref in context.path_to_refs[candidate_path]:
                    if child_ref not in raw_gold and child_ref not in child_refs:
                        child_refs.append(child_ref)
    return parent_refs, sibling_refs, child_refs


def write_gold_section_audit(path: Path, rows: list[dict[str, object]]) -> None:
    lines = [
        "# Gold Section Audit",
        "",
        "- purpose: audit whether raw gold refs are too narrow for section-level questions.",
        "- note: this report does not modify `qa_test_set.jsonl`.",
        "",
        "| question_id | category | audit_label | raw_hit | expanded_hit | parent_hit | sibling_hit | child_hit |",
        "| --- | --- | --- | ---: | ---: | ---: | ---: | ---: |",
    ]
    for row in rows:
        lines.append(
            f"| {row['question_id']} | {row['category']} | {row['audit_label']} | "
            f"{row['raw_top10_hit']} | {row['expanded_top10_hit']} | {row['parent_top10_hit']} | "
            f"{row['sibling_top10_hit']} | {row['child_top10_hit']} |"
        )
    lines.extend(["", "## Details", ""])
    for row in rows:
        lines.extend(
            [
                f"### {row['question_id']} ({row['category']})",
                "",
                f"- question: {row['question']}",
                f"- audit_label: `{row['audit_label']}`",
                f"- raw_gold_refs: `{row['raw_gold_refs']}`",
                f"- heading_path: `{row['heading_path']}`",
                f"- parent_refs: `{row['parent_refs']}`",
                f"- same_section_sibling_refs: `{row['same_section_sibling_refs']}`",
                f"- child_refs: `{row['child_refs']}`",
                f"- top10_refs: `{row['top10_refs']}`",
                "",
            ]
        )
    path.write_text("\n".join(lines), encoding="utf-8")


def heading_path_for_text(text: str) -> str:
    explicit = extract_metadata_field(text, "heading_path_text")
    if explicit:
        return explicit.split(". ")[0].strip().rstrip(" .。")
    parts = [
        extract_metadata_field(text, "chapter"),
        extract_metadata_field(text, "section"),
        extract_metadata_field(text, "subsection"),
    ]
    return " > ".join(part for part in parts if part)


def extract_metadata_field(text: str, field: str) -> str:
    if field not in METADATA_FIELD_NAMES:
        return ""
    next_fields = "|".join(re.escape(name) for name in METADATA_FIELD_NAMES if name != field)
    pattern = re.compile(rf"{re.escape(field)}:\s*(.*?)(?=\.\s+(?:{next_fields}):|$)")
    match = pattern.search(text or "")
    return match.group(1).strip() if match else ""


def append_term(terms: list[str], raw: str, *, corpus_text: str) -> None:
    term = str(raw or "").strip()
    if not is_valid_term(term):
        return
    if term.casefold() not in corpus_text.casefold():
        return
    if term not in terms:
        terms.append(term)


def _strip_question_marks(text: str) -> str:
    return (text or "").strip().strip("？?。.")


def _clean_subquery(text: str) -> str:
    cleaned = _strip_question_marks(text)
    cleaned = re.sub(r"^(请|教材|课程|本书|这门课)", "", cleaned)
    cleaned = re.sub(r"(在课程中)?如何衔接.*$", "", cleaned)
    cleaned = re.sub(r"这条主线如何$", "", cleaned)
    cleaned = re.sub(r"分别解决.*$", "", cleaned)
    cleaned = re.sub(r"的?(主要)?(内容|重点|主线|路径|问题)$", "", cleaned)
    return cleaned.strip(" ：:，,、")


def _append_query(queries: list[str], raw: str) -> None:
    query = raw.strip()
    if 2 <= len(query) <= 40 and query not in queries:
        queries.append(query)


def is_valid_term(term: str) -> bool:
    if not (2 <= len(term) <= 24):
        return False
    return any(char.isalnum() for char in term)


def dedupe_terms(values: list[str]) -> list[str]:
    terms: list[str] = []
    for value in values:
        if value and value not in terms:
            terms.append(value)
    return terms


def dedupe_refs(values: list[str]) -> list[str]:
    refs: list[str] = []
    for value in values:
        ref = str(value or "").strip()[:TEXT_UNIT_ID_PREFIX_LEN]
        if ref and ref not in refs:
            refs.append(ref)
    return refs


def preview(text: str, limit: int = 80) -> str:
    normalized = " ".join((text or "").split())
    return normalized if len(normalized) <= limit else normalized[: limit - 3] + "..."


def mean(values) -> float:
    numbers = list(values)
    return sum(numbers) / len(numbers) if numbers else 0.0


def mean_first_rank(rows: list[dict[str, object]], *, key: str = "first_gold_rank") -> float:
    ranks = [int(row[key]) for row in rows if int(row[key]) > 0]
    return sum(ranks) / len(ranks) if ranks else 0.0


def write_csv(path: Path, rows: list[dict[str, object]]) -> None:
    if not rows:
        path.write_text("", encoding="utf-8")
        return
    fieldnames: list[str] = []
    for row in rows:
        for key in row:
            if key not in fieldnames:
                fieldnames.append(key)
    with path.open("w", encoding="utf-8", newline="") as file:
        writer = csv.DictWriter(file, fieldnames=fieldnames, extrasaction="ignore")
        writer.writeheader()
        writer.writerows(rows)


def write_json(path: Path, value: object) -> None:
    path.write_text(json.dumps(value, ensure_ascii=False, indent=2), encoding="utf-8")


def main() -> int:
    parser = argparse.ArgumentParser(description="Run offline BM25 text-unit retrieval bakeoff.")
    parser.add_argument("--test-set", type=Path, default=DEFAULT_TEST_SET)
    parser.add_argument("--text-units", type=Path, default=None)
    parser.add_argument("--output-dir", type=Path, default=DEFAULT_OUTPUT_ROOT)
    parser.add_argument("--run-id", type=str, default=None)
    parser.add_argument("--top-k", type=int, default=10)
    parser.add_argument("--max-course-terms", type=int, default=300)
    parser.add_argument("--max-thuocl-terms", type=int, default=5000)
    parser.add_argument("--download-thuocl-it", action="store_true")
    parser.add_argument("--thuocl-cache-dir", type=Path, default=DEFAULT_THUOCL_CACHE)
    parser.add_argument("--thuocl-it-path", type=Path, default=None)
    parser.add_argument("--disable-gold-context-expansion", action="store_true")
    parser.add_argument("--section-heading-weight", type=int, default=4)
    parser.add_argument("--enable-dense-rerank", action="store_true")
    parser.add_argument(
        "--dense-rerank-policy",
        choices=[policy.value for policy in DenseRerankPolicy],
        default=DenseRerankPolicy.NONE.value,
    )
    parser.add_argument("--dense-rerank-candidate-pool-k", type=int, default=20)
    parser.add_argument("--dense-rerank-model", type=str, default=None)
    parser.add_argument("--dense-rerank-device", type=str, default=None)
    parser.add_argument("--dense-rerank-fp16", action="store_true")
    parser.add_argument("--dense-rerank-batch-size", type=int, default=16)
    parser.add_argument("--dense-rerank-max-length", type=int, default=8192)
    parser.add_argument("--enable-noise-filter", action="store_true")
    parser.add_argument("--noise-blacklist-path", type=Path, default=None)
    parser.add_argument("--write-noise-blacklist", action="store_true")
    parser.add_argument(
        "--query-decomposition-policy",
        choices=[policy.value for policy in QueryDecompositionPolicy],
        default=QueryDecompositionPolicy.NONE.value,
    )
    parser.add_argument("--write-gold-section-audit", action="store_true")
    args = parser.parse_args()
    result = run_bm25_bakeoff(
        Bm25BakeoffConfig(
            test_set_path=args.test_set,
            text_units_path=args.text_units,
            output_dir=args.output_dir,
            run_id=args.run_id,
            top_k=args.top_k,
            max_course_terms=args.max_course_terms,
            max_thuocl_terms=args.max_thuocl_terms,
            download_thuocl_it=args.download_thuocl_it,
            thuocl_cache_dir=args.thuocl_cache_dir,
            thuocl_it_path=args.thuocl_it_path,
            enable_gold_context_expansion=not args.disable_gold_context_expansion,
            section_heading_weight=args.section_heading_weight,
            enable_dense_rerank=args.enable_dense_rerank,
            dense_rerank_policy=DenseRerankPolicy(args.dense_rerank_policy),
            dense_rerank_candidate_pool_k=args.dense_rerank_candidate_pool_k,
            dense_rerank_model=args.dense_rerank_model,
            dense_rerank_device=args.dense_rerank_device,
            dense_rerank_use_fp16=args.dense_rerank_fp16,
            dense_rerank_batch_size=args.dense_rerank_batch_size,
            dense_rerank_max_length=args.dense_rerank_max_length,
            enable_noise_filter=args.enable_noise_filter,
            noise_blacklist_path=args.noise_blacklist_path,
            write_noise_blacklist=args.write_noise_blacklist,
            query_decomposition_policy=QueryDecompositionPolicy(args.query_decomposition_policy),
            write_gold_section_audit=args.write_gold_section_audit,
        )
    )
    print(f"wrote BM25 bakeoff to {result.run_dir}")
    print(f"best config: {result.best_config['config_name']} k1={result.best_config['k1']} b={result.best_config['b']}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
