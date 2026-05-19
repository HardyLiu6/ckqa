from __future__ import annotations

import re
from collections import defaultdict
from collections.abc import Callable, Sequence
from dataclasses import dataclass
from typing import Any, Protocol

from graphrag_pipeline.scripts.hybrid_qa.types import EvidenceCandidate, HybridLayer
from graphrag_pipeline.scripts.qa_eval.test_set_schema import TEXT_UNIT_ID_PREFIX_LEN


CitationRefResolver = Callable[[str], Sequence[str]]


class TextUnitLookupProtocol(Protocol):
    def get(self, prefix: str) -> str | None:
        ...


@dataclass(frozen=True, slots=True)
class EvidenceFusionConfig:
    rrf_k: int = 60
    bm25_weight: float = 1.0
    basic_citation_weight: float = 1.2
    bm25_anchor_top_k: int = 0
    fused_top_k: int = 8
    max_text_chars: int = 700


@dataclass(frozen=True, slots=True)
class FusedEvidencePack:
    candidates: list[EvidenceCandidate]
    refs: list[str]
    source_counts: dict[str, int]
    fusion_debug: list[dict[str, Any]]


_TEXT_UNIT_BLOCK_RE = re.compile(r"(?:Text Units?|Hybrid)\s*\(([^)]*)\)", re.IGNORECASE)
_REF_TOKEN_RE = re.compile(r"[A-Za-z0-9][A-Za-z0-9_-]{7,}")


def fuse_basic_and_bm25_evidence(
    *,
    question: str,
    basic_answer: str,
    bm25_candidates: Sequence[EvidenceCandidate],
    text_unit_lookup: TextUnitLookupProtocol,
    citation_ref_resolver: CitationRefResolver | None,
    config: EvidenceFusionConfig | None = None,
) -> FusedEvidencePack:
    config = config or EvidenceFusionConfig()
    contributions: dict[str, dict[str, Any]] = {}

    for rank, candidate in enumerate(bm25_candidates, start=1):
        ref = _normalize_ref(candidate.ref)
        if not ref:
            continue
        _add_contribution(
            contributions,
            ref=ref,
            source="bm25",
            rank=rank,
            weight=config.bm25_weight,
            rrf_k=config.rrf_k,
            text=candidate.text,
        )

    for rank, ref in enumerate(_extract_basic_text_unit_refs(basic_answer, citation_ref_resolver), start=1):
        text = text_unit_lookup.get(ref) or ""
        _add_contribution(
            contributions,
            ref=ref,
            source="basic-citation",
            rank=rank,
            weight=config.basic_citation_weight,
            rrf_k=config.rrf_k,
            text=text,
        )

    scored_order = sorted(
        contributions.items(),
        key=lambda item: (-float(item[1]["score"]), int(item[1]["best_rank"]), item[0]),
    )
    anchor_refs = _bm25_anchor_refs(bm25_candidates, config.bm25_anchor_top_k)
    ordered_refs = [
        *[ref for ref in anchor_refs if ref in contributions],
        *[ref for ref, _ in scored_order if ref not in anchor_refs],
    ][: max(config.fused_top_k, 0)]

    candidates: list[EvidenceCandidate] = []
    source_counts: dict[str, int] = defaultdict(int)
    fusion_debug: list[dict[str, Any]] = []
    for ref in ordered_refs:
        payload = contributions[ref]
        sources = sorted(payload["sources"])
        for source in sources:
            source_counts[source] += 1
        text = _truncate_text(str(payload.get("text") or text_unit_lookup.get(ref) or ""), config.max_text_chars)
        candidate = EvidenceCandidate(
            source="+".join(sources),
            ref=ref,
            text=text,
            score=float(payload["score"]),
            layer=HybridLayer.LOW,
            metadata={
                "fusion_sources": sources,
                "fusion_score": float(payload["score"]),
                "source_ranks": dict(payload["ranks"]),
            },
        )
        candidates.append(candidate)
        fusion_debug.append(
            {
                "ref": ref,
                "score": round(float(payload["score"]), 6),
                "sources": sources,
                "source_ranks": dict(payload["ranks"]),
            }
        )

    return FusedEvidencePack(
        candidates=candidates,
        refs=[candidate.ref for candidate in candidates],
        source_counts=dict(source_counts),
        fusion_debug=fusion_debug,
    )


def _bm25_anchor_refs(
    bm25_candidates: Sequence[EvidenceCandidate],
    limit: int,
) -> list[str]:
    refs: list[str] = []
    if limit <= 0:
        return refs
    for candidate in bm25_candidates:
        _append_ref(refs, candidate.ref)
        if len(refs) >= limit:
            break
    return refs


def _add_contribution(
    contributions: dict[str, dict[str, Any]],
    *,
    ref: str,
    source: str,
    rank: int,
    weight: float,
    rrf_k: int,
    text: str,
) -> None:
    payload = contributions.setdefault(
        ref,
        {
            "score": 0.0,
            "best_rank": rank,
            "sources": set(),
            "ranks": {},
            "text": "",
        },
    )
    payload["score"] = float(payload["score"]) + weight / (rrf_k + rank)
    payload["best_rank"] = min(int(payload["best_rank"]), rank)
    payload["sources"].add(source)
    payload["ranks"][source] = min(int(payload["ranks"].get(source, rank)), rank)
    if text and not payload["text"]:
        payload["text"] = text


def _extract_basic_text_unit_refs(
    answer: str,
    citation_ref_resolver: CitationRefResolver | None,
) -> list[str]:
    refs: list[str] = []
    for block in _TEXT_UNIT_BLOCK_RE.findall(answer or ""):
        for token in _REF_TOKEN_RE.findall(block):
            _append_ref(refs, token)
    if citation_ref_resolver is not None:
        for token in citation_ref_resolver(answer or ""):
            _append_ref(refs, str(token))
    return refs


def _append_ref(refs: list[str], raw: str) -> None:
    ref = _normalize_ref(raw)
    if ref and ref not in refs:
        refs.append(ref)


def _normalize_ref(raw: str) -> str:
    return (raw or "").strip()[:TEXT_UNIT_ID_PREFIX_LEN]


def _truncate_text(text: str, max_chars: int) -> str:
    stripped = (text or "").strip()
    if max_chars <= 0 or len(stripped) <= max_chars:
        return stripped
    return stripped[: max(0, max_chars - 3)] + "..."
