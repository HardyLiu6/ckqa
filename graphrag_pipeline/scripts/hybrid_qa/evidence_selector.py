from __future__ import annotations

import os
from collections.abc import Callable
from dataclasses import dataclass
from pathlib import Path

import numpy as np

from graphrag_pipeline.scripts.hybrid_qa.bm25_text_units import (
    TextUnitBm25,
    TextUnitBm25Config,
    build_text_unit_bm25_from_texts,
)
from graphrag_pipeline.scripts.hybrid_qa.question_classifier import classify_question
from graphrag_pipeline.scripts.hybrid_qa.types import EvidenceCandidate, HybridLayer
from graphrag_pipeline.scripts.qa_eval.bm25_retrieval_bakeoff import (
    DEFAULT_THUOCL_CACHE,
    TextUnitRow,
    audit_text_unit_noise,
    build_multi_queries,
    extract_course_terms,
    filter_thuocl_terms,
    generate_noise_blacklist,
    load_noise_blacklist,
    load_text_units,
    rrf_fuse_ranked_refs,
)
from graphrag_pipeline.scripts.qa_eval.semantic_similarity import DEFAULT_BGE_M3_MODEL, _encode_dense_chunks


DenseEncoder = Callable[[list[str]], np.ndarray]


@dataclass(frozen=True, slots=True)
class V6EvidenceSelectorConfig:
    top_k: int = 8
    user_terms: tuple[str, ...] = ()
    thuocl_it_path: Path | None = None
    thuocl_cache_dir: Path = DEFAULT_THUOCL_CACHE
    max_course_terms: int = 300
    max_thuocl_terms: int = 5000
    k1: float = 1.5
    b: float = 0.75
    enable_noise_filter: bool = True
    exclude_exercises: bool = True
    enable_multi_query_rrf: bool = True
    enable_dense_rerank: bool = False
    dense_rerank_candidate_pool_k: int = 20
    dense_rerank_model: str | None = None
    dense_rerank_device: str | None = None
    dense_rerank_use_fp16: bool = False
    dense_rerank_batch_size: int = 16
    dense_rerank_max_length: int = 8192
    dense_encoder: DenseEncoder | None = None


@dataclass(slots=True)
class V6HybridEvidenceSelector:
    index: TextUnitBm25
    text_by_ref: dict[str, str]
    config: V6EvidenceSelectorConfig

    def search(self, query: str, top_k: int = 10) -> list[EvidenceCandidate]:
        limit = top_k or self.config.top_k
        pool_k = max(limit, self.config.dense_rerank_candidate_pool_k)
        refs = self._bm25_refs(query, top_k=pool_k)
        source = "bm25-v6"
        if self.config.enable_dense_rerank and _should_dense_rerank_query(query):
            refs = _rerank_refs_by_dense_similarity(
                question=query,
                ranked_refs=refs,
                text_by_ref=self.text_by_ref,
                encoder=self.config.dense_encoder or self._dense_encoder(),
                top_k=limit,
            )
            source = "bm25-v6+dense"
        else:
            refs = refs[:limit]
        return [
            EvidenceCandidate(
                source=source,
                ref=ref,
                text=self.text_by_ref.get(ref, ""),
                score=float(len(refs) - index),
                layer=HybridLayer.LOW,
                metadata={"strategy": "v6", "rank": index + 1},
            )
            for index, ref in enumerate(refs)
            if self.text_by_ref.get(ref)
        ]

    def _bm25_refs(self, query: str, *, top_k: int) -> list[str]:
        if not self.config.enable_multi_query_rrf:
            return [candidate.ref for candidate in self.index.search(query, top_k=top_k)]
        ranked_lists = [
            [candidate.ref for candidate in self.index.search(subquery, top_k=max(top_k, 10))]
            for subquery in build_multi_queries(query)
        ]
        return rrf_fuse_ranked_refs(ranked_lists, top_k=top_k)

    def _dense_encoder(self) -> DenseEncoder:
        model_name = self.config.dense_rerank_model or os.environ.get("CKQA_BGE_M3_MODEL", DEFAULT_BGE_M3_MODEL)
        device = self.config.dense_rerank_device or os.environ.get("CKQA_BGE_M3_DEVICE") or None

        def encode(texts: list[str]) -> np.ndarray:
            return _encode_dense_chunks(
                texts,
                model_name=model_name,
                max_length=self.config.dense_rerank_max_length,
                device=device,
                use_fp16=self.config.dense_rerank_use_fp16,
                batch_size=self.config.dense_rerank_batch_size,
            )

        return encode


def build_v6_hybrid_evidence_selector(
    text_units_path: Path,
    *,
    config: V6EvidenceSelectorConfig | None = None,
) -> V6HybridEvidenceSelector:
    return build_v6_hybrid_evidence_selector_from_rows(load_text_units(text_units_path), config=config)


def build_v6_hybrid_evidence_selector_from_rows(
    rows: list[TextUnitRow],
    *,
    config: V6EvidenceSelectorConfig | None = None,
) -> V6HybridEvidenceSelector:
    config = config or V6EvidenceSelectorConfig()
    filtered_rows = _filter_noise(rows) if config.enable_noise_filter else rows
    user_terms = config.user_terms or tuple(_build_runtime_terms(rows, config))
    index = build_text_unit_bm25_from_texts(
        refs=[row.ref for row in filtered_rows],
        texts=[row.text for row in filtered_rows],
        config=TextUnitBm25Config(
            user_terms=user_terms,
            k1=config.k1,
            b=config.b,
            enable_query_expansion=True,
            exclude_exercises=config.exclude_exercises,
        ),
    )
    return V6HybridEvidenceSelector(
        index=index,
        text_by_ref={row.ref: row.text for row in filtered_rows},
        config=config if config.user_terms else _replace_user_terms(config, user_terms),
    )


def _build_runtime_terms(rows: list[TextUnitRow], config: V6EvidenceSelectorConfig) -> list[str]:
    # Hybrid runtime 没有 gold test items；这里仅复用教材正文自动术语和本地 THUOCL 过滤词。
    course_terms = extract_course_terms([], rows, limit=config.max_course_terms)
    corpus_text = "\n".join(row.text for row in rows)
    thuocl_path = config.thuocl_it_path or config.thuocl_cache_dir / "THUOCL_IT.txt"
    thuocl_terms = filter_thuocl_terms(
        thuocl_path if thuocl_path.exists() else None,
        corpus_text=corpus_text,
        limit=config.max_thuocl_terms,
    )
    return _dedupe([*course_terms, *thuocl_terms])


def _filter_noise(rows: list[TextUnitRow]) -> list[TextUnitRow]:
    blacklist = load_noise_blacklist(generate_noise_blacklist(audit_text_unit_noise(rows)))
    if not blacklist:
        return rows
    return [row for row in rows if row.ref not in blacklist]


def _should_dense_rerank_query(query: str) -> bool:
    return classify_question(query).layer in {HybridLayer.LOW, HybridLayer.MIXED}


def _rerank_refs_by_dense_similarity(
    *,
    question: str,
    ranked_refs: list[str],
    text_by_ref: dict[str, str],
    encoder: DenseEncoder,
    top_k: int,
) -> list[str]:
    refs = [ref for ref in _dedupe(ranked_refs) if text_by_ref.get(ref)]
    if not refs or top_k <= 0:
        return []
    texts = [question, *(text_by_ref[ref] for ref in refs)]
    vectors = _normalize_vectors(np.asarray(encoder(texts), dtype=np.float32))
    if vectors.ndim != 2 or vectors.shape[0] != len(texts):
        raise ValueError(f"dense encoder returned invalid shape: {vectors.shape}, expected {len(texts)} rows")
    scores = vectors[1:] @ vectors[0]
    ordered = sorted(range(len(refs)), key=lambda index: (-float(scores[index]), index, refs[index]))
    return [refs[index] for index in ordered[:top_k]]


def _normalize_vectors(vectors: np.ndarray) -> np.ndarray:
    if vectors.ndim != 2:
        return vectors
    norms = np.linalg.norm(vectors, axis=1, keepdims=True)
    return vectors / np.maximum(norms, 1e-12)


def _dedupe(values: list[str]) -> list[str]:
    out: list[str] = []
    for value in values:
        if value and value not in out:
            out.append(value)
    return out


def _replace_user_terms(
    config: V6EvidenceSelectorConfig,
    user_terms: tuple[str, ...],
) -> V6EvidenceSelectorConfig:
    return V6EvidenceSelectorConfig(
        top_k=config.top_k,
        user_terms=user_terms,
        thuocl_it_path=config.thuocl_it_path,
        thuocl_cache_dir=config.thuocl_cache_dir,
        max_course_terms=config.max_course_terms,
        max_thuocl_terms=config.max_thuocl_terms,
        k1=config.k1,
        b=config.b,
        enable_noise_filter=config.enable_noise_filter,
        exclude_exercises=config.exclude_exercises,
        enable_multi_query_rrf=config.enable_multi_query_rrf,
        enable_dense_rerank=config.enable_dense_rerank,
        dense_rerank_candidate_pool_k=config.dense_rerank_candidate_pool_k,
        dense_rerank_model=config.dense_rerank_model,
        dense_rerank_device=config.dense_rerank_device,
        dense_rerank_use_fp16=config.dense_rerank_use_fp16,
        dense_rerank_batch_size=config.dense_rerank_batch_size,
        dense_rerank_max_length=config.dense_rerank_max_length,
        dense_encoder=config.dense_encoder,
    )
