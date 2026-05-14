from __future__ import annotations

import os
import re
from dataclasses import dataclass
from functools import lru_cache
from typing import Any

import numpy as np


DEFAULT_BGE_M3_MODEL = "BAAI/bge-m3"
DEFAULT_BERTSCORE_MODEL = "bert-base-chinese"


@dataclass(slots=True)
class SemanticScoringConfig:
    bge_m3_model: str | None = None
    bge_max_length: int = 8192
    max_chunk_chars: int = 260
    similarity_threshold: float = 0.62
    enable_bge_m3: bool = True
    enable_bertscore: bool = False
    bertscore_model: str | None = None


def split_semantic_chunks(text: str, *, max_chunk_chars: int = 260) -> list[str]:
    parts = [part.strip() for part in re.split(r"(?<=[。！？；;.!?\n])", text or "") if part.strip()]
    if not parts:
        return []

    chunks: list[str] = []
    current = ""
    for part in parts:
        if not current:
            current = part
        elif len(current) + len(part) <= max_chunk_chars:
            current += part
        else:
            chunks.extend(_hard_wrap(current, max_chunk_chars))
            current = part
    if current:
        chunks.extend(_hard_wrap(current, max_chunk_chars))
    return chunks


def _hard_wrap(text: str, max_chunk_chars: int) -> list[str]:
    if len(text) <= max_chunk_chars:
        return [text]
    return [text[index : index + max_chunk_chars] for index in range(0, len(text), max_chunk_chars)]


def _score_cheap_baselines(answer: str, reference: str) -> dict[str, float]:
    if not (answer or "").strip() or not (reference or "").strip():
        return {"rouge_lsum": 0.0, "keyword_recall": 0.0}

    import jieba
    import jieba.analyse
    from rouge_score import rouge_scorer

    scorer = rouge_scorer.RougeScorer(["rougeLsum"], use_stemmer=False)
    reference_rouge, answer_rouge = _encode_tokens_for_rouge(reference, answer, jieba)
    rouge = scorer.score(reference_rouge, answer_rouge)["rougeLsum"].fmeasure
    keywords = [word for word in jieba.analyse.extract_tags(reference, topK=12) if len(word.strip()) >= 2]
    keyword_recall = 0.0 if not keywords else sum(1 for word in keywords if word in answer) / len(keywords)
    return {
        "rouge_lsum": round(float(rouge), 4),
        "keyword_recall": round(float(keyword_recall), 4),
    }


def _encode_tokens_for_rouge(reference: str, answer: str, jieba_module: Any) -> tuple[str, str]:
    reference_tokens = _tokenize_for_rouge(reference, jieba_module)
    answer_tokens = _tokenize_for_rouge(answer, jieba_module)
    vocabulary: dict[str, str] = {}
    for token in reference_tokens + answer_tokens:
        vocabulary.setdefault(token, f"tok{len(vocabulary)}")
    return (
        " ".join(vocabulary[token] for token in reference_tokens),
        " ".join(vocabulary[token] for token in answer_tokens),
    )


def _tokenize_for_rouge(text: str, jieba_module: Any) -> list[str]:
    return [token for token in jieba_module.lcut(text or "") if token.strip()]


@lru_cache(maxsize=2)
def _load_bge_m3_model(model_name: str) -> Any:
    try:
        from FlagEmbedding import BGEM3FlagModel
    except ImportError as exc:
        raise RuntimeError('BGE-M3 scoring requires `pip install -e ".[eval]"`.') from exc
    return BGEM3FlagModel(model_name, use_fp16=False)


def _encode_dense_chunks(chunks: list[str], *, model_name: str, max_length: int) -> np.ndarray:
    model = _load_bge_m3_model(model_name)
    encoded = model.encode(
        chunks,
        batch_size=8,
        max_length=max_length,
        return_dense=True,
        return_sparse=False,
        return_colbert_vecs=False,
    )
    vectors = np.asarray(encoded["dense_vecs"], dtype=np.float32)
    norms = np.linalg.norm(vectors, axis=1, keepdims=True)
    return vectors / np.maximum(norms, 1e-12)


def _coverage_from_similarity(matrix: np.ndarray, *, threshold: float) -> dict[str, float]:
    if matrix.size == 0:
        return {
            "semantic_coverage_precision": 0.0,
            "semantic_coverage_recall": 0.0,
            "semantic_coverage_f1": 0.0,
        }
    answer_best = matrix.max(axis=1)
    reference_best = matrix.max(axis=0)
    precision = float(np.mean(answer_best >= threshold))
    recall = float(np.mean(reference_best >= threshold))
    f1 = 0.0 if precision + recall == 0 else 2 * precision * recall / (precision + recall)
    return {
        "semantic_coverage_precision": round(precision, 4),
        "semantic_coverage_recall": round(recall, 4),
        "semantic_coverage_f1": round(f1, 4),
    }


def _score_bge_m3_coverage(
    *,
    answer_chunks: list[str],
    reference_chunks: list[str],
    config: SemanticScoringConfig,
) -> dict[str, float]:
    if not answer_chunks or not reference_chunks:
        return _coverage_from_similarity(np.empty((0, 0)), threshold=config.similarity_threshold)
    model_name = config.bge_m3_model or os.environ.get("CKQA_BGE_M3_MODEL", DEFAULT_BGE_M3_MODEL)
    answer_vectors = _encode_dense_chunks(answer_chunks, model_name=model_name, max_length=config.bge_max_length)
    reference_vectors = _encode_dense_chunks(reference_chunks, model_name=model_name, max_length=config.bge_max_length)
    return _coverage_from_similarity(answer_vectors @ reference_vectors.T, threshold=config.similarity_threshold)


def _score_optional_bertscore(answer: str, reference: str, config: SemanticScoringConfig) -> dict[str, float | None]:
    if not config.enable_bertscore:
        return {
            "bertscore_precision": None,
            "bertscore_recall": None,
            "bertscore_f1": None,
        }
    try:
        from bert_score import score as bert_score
    except ImportError as exc:
        raise RuntimeError('BERTScore requires `pip install -e ".[semantic-compat]"`.') from exc

    model = config.bertscore_model or os.environ.get("CKQA_BERTSCORE_MODEL", DEFAULT_BERTSCORE_MODEL)
    precision, recall, f1 = bert_score(
        [answer],
        [reference],
        lang="zh",
        model_type=model,
        verbose=False,
        rescale_with_baseline=False,
    )
    return {
        "bertscore_precision": round(float(precision[0].item()), 4),
        "bertscore_recall": round(float(recall[0].item()), 4),
        "bertscore_f1": round(float(f1[0].item()), 4),
    }


def score_semantic_similarity(
    *,
    answer: str,
    reference: str,
    config: SemanticScoringConfig | None = None,
) -> dict[str, float | str | None]:
    config = config or SemanticScoringConfig()
    if not (answer or "").strip() or not (reference or "").strip():
        return {
            "semantic_coverage_precision": 0.0,
            "semantic_coverage_recall": 0.0,
            "semantic_coverage_f1": 0.0,
            "rouge_lsum": 0.0,
            "keyword_recall": 0.0,
            "bertscore_precision": None,
            "bertscore_recall": None,
            "bertscore_f1": None,
            "semantic_model": "none",
        }

    answer_chunks = split_semantic_chunks(answer, max_chunk_chars=config.max_chunk_chars)
    reference_chunks = split_semantic_chunks(reference, max_chunk_chars=config.max_chunk_chars)
    cheap = _score_cheap_baselines(answer, reference)
    if config.enable_bge_m3:
        coverage = _score_bge_m3_coverage(
            answer_chunks=answer_chunks,
            reference_chunks=reference_chunks,
            config=config,
        )
        semantic_model = config.bge_m3_model or os.environ.get("CKQA_BGE_M3_MODEL", DEFAULT_BGE_M3_MODEL)
    else:
        coverage = {
            "semantic_coverage_precision": 0.0,
            "semantic_coverage_recall": 0.0,
            "semantic_coverage_f1": 0.0,
        }
        semantic_model = "cheap-baseline-only"
    return {
        **coverage,
        **cheap,
        **_score_optional_bertscore(answer, reference, config),
        "semantic_model": semantic_model,
    }
