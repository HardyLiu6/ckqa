from __future__ import annotations

import os
from dataclasses import dataclass
from typing import Any, Sequence

import numpy as np

from graphrag_pipeline.scripts.qa_eval.semantic_similarity import (
    DEFAULT_BGE_M3_MODEL,
    SemanticScoringConfig,
    _encode_dense_chunks,
    split_semantic_chunks,
)


@dataclass(frozen=True, slots=True)
class EvidenceGuardrailConfig:
    similarity_threshold: float = 0.62
    min_supported_ratio: float = 0.75
    max_chunk_chars: int = 220
    bge_m3_model: str | None = None
    bge_device: str | None = None
    bge_use_fp16: bool = False
    bge_batch_size: int = 8


@dataclass(frozen=True, slots=True)
class EvidenceGuardrailResult:
    status: str
    supported_ratio: float
    unsupported_count: int
    answer_chunk_count: int


def _answer_evidence_similarity(
    answer_chunks: list[str],
    evidence_chunks: list[str],
    config: EvidenceGuardrailConfig,
) -> np.ndarray:
    if not answer_chunks or not evidence_chunks:
        return np.empty((len(answer_chunks), len(evidence_chunks)), dtype=np.float32)

    semantic_config = SemanticScoringConfig(
        bge_m3_model=config.bge_m3_model,
        bge_device=config.bge_device,
        bge_use_fp16=config.bge_use_fp16,
        bge_batch_size=config.bge_batch_size,
        max_chunk_chars=config.max_chunk_chars,
        similarity_threshold=config.similarity_threshold,
    )
    model_name = semantic_config.bge_m3_model or os.environ.get("CKQA_BGE_M3_MODEL", DEFAULT_BGE_M3_MODEL)
    device = semantic_config.bge_device or os.environ.get("CKQA_BGE_M3_DEVICE") or None
    answer_vectors = _encode_dense_chunks(
        answer_chunks,
        model_name=model_name,
        max_length=semantic_config.bge_max_length,
        device=device,
        use_fp16=semantic_config.bge_use_fp16,
        batch_size=semantic_config.bge_batch_size,
    )
    evidence_vectors = _encode_dense_chunks(
        evidence_chunks,
        model_name=model_name,
        max_length=semantic_config.bge_max_length,
        device=device,
        use_fp16=semantic_config.bge_use_fp16,
        batch_size=semantic_config.bge_batch_size,
    )
    return answer_vectors @ evidence_vectors.T


def check_answer_supported_by_evidence(
    answer: str,
    evidence: Sequence[Any],
    config: EvidenceGuardrailConfig | None = None,
) -> EvidenceGuardrailResult:
    config = config or EvidenceGuardrailConfig()
    answer_chunks = split_semantic_chunks(answer, max_chunk_chars=config.max_chunk_chars)
    evidence_chunks = [
        chunk
        for item in evidence
        for chunk in split_semantic_chunks(getattr(item, "text", ""), max_chunk_chars=config.max_chunk_chars)
    ]
    matrix = _answer_evidence_similarity(answer_chunks, evidence_chunks, config)

    if matrix.size == 0:
        answer_chunk_count = len(answer_chunks)
        return EvidenceGuardrailResult(
            status="fail",
            supported_ratio=0.0,
            unsupported_count=answer_chunk_count,
            answer_chunk_count=answer_chunk_count,
        )

    best_scores = matrix.max(axis=1)
    supported = best_scores >= config.similarity_threshold
    supported_count = int(np.sum(supported))
    answer_chunk_count = len(answer_chunks)
    supported_ratio = supported_count / answer_chunk_count if answer_chunk_count else 0.0
    status = "pass" if supported_ratio >= config.min_supported_ratio else "fail"
    return EvidenceGuardrailResult(
        status=status,
        supported_ratio=float(supported_ratio),
        unsupported_count=answer_chunk_count - supported_count,
        answer_chunk_count=answer_chunk_count,
    )
