from __future__ import annotations

import re
import time
from collections.abc import Callable, Sequence
from dataclasses import dataclass
from typing import Any, Protocol

from graphrag_pipeline.scripts.hybrid_qa.basic_local_client import GraphRagDraft
from graphrag_pipeline.scripts.hybrid_qa.evidence_fusion import FusedEvidencePack
from graphrag_pipeline.scripts.hybrid_qa.prompt_builder import (
    build_hybrid_v0_basic_injection_prompt,
    build_hybrid_v0_prompt,
)
from graphrag_pipeline.scripts.hybrid_qa.question_classifier import classify_question
from graphrag_pipeline.scripts.hybrid_qa.types import (
    EvidenceCandidate,
    HybridDiagnostics,
    HybridV0Answer,
)


class Bm25Searcher(Protocol):
    def search(self, query: str, top_k: int = 10) -> list[EvidenceCandidate]:
        ...


class GraphRagClient(Protocol):
    def query_basic(self, question: str) -> GraphRagDraft:
        ...

    def query_local(self, question: str) -> GraphRagDraft:
        ...


GuardrailCallable = Callable[[str, Sequence[EvidenceCandidate]], Any]
LlmCompleteCallable = Callable[[str], str]
CitationRefResolver = Callable[[str], Sequence[str]]
EvidenceFusionCallable = Callable[
    [str, str, Sequence[EvidenceCandidate], CitationRefResolver | None],
    FusedEvidencePack,
]


_DATA_CITATION_RE = re.compile(r"\[Data:\s*[^\]]+\]", re.IGNORECASE)
_DIRECT_REF_BLOCK_RE = re.compile(r"(?:Text Units?|Hybrid)\s*\(([^)]*)\)", re.IGNORECASE)
_DIRECT_REF_TOKEN_RE = re.compile(r"[A-Za-z0-9][A-Za-z0-9_-]{7,}")
_TEXT_UNIT_REF_PREFIX_LEN = 12


@dataclass(frozen=True, slots=True)
class HybridFallbackPolicy:
    min_answer_chars: int = 80
    require_data_citation: bool = True
    require_low_ref_overlap_when_direct_refs: bool = True
    fused_overlap_top_k: int = 3
    enable_basic_evidence_injection: bool = False
    disable_synthesis: bool = False
    enable_local_fallback: bool = False


class HybridV0Orchestrator:
    def __init__(
        self,
        bm25: Bm25Searcher,
        graph_client: GraphRagClient,
        guardrail: GuardrailCallable,
        llm_complete: LlmCompleteCallable,
        bm25_top_k: int = 8,
        fallback_policy: HybridFallbackPolicy | None = None,
        citation_ref_resolver: CitationRefResolver | None = None,
        evidence_fusion: EvidenceFusionCallable | None = None,
    ) -> None:
        self.bm25 = bm25
        self.graph_client = graph_client
        self.guardrail = guardrail
        self.llm_complete = llm_complete
        self.bm25_top_k = bm25_top_k
        self.fallback_policy = fallback_policy or HybridFallbackPolicy()
        self.citation_ref_resolver = citation_ref_resolver
        self.evidence_fusion = evidence_fusion or _fallback_fuse_bm25_only

    def answer(self, question: str) -> HybridV0Answer:
        started_at = time.perf_counter()
        classification = classify_question(question)
        low_candidates = self.bm25.search(question, top_k=self.bm25_top_k)
        high_candidates: list[EvidenceCandidate] = []
        errors: list[str] = []

        basic_prompt = (
            build_hybrid_v0_basic_injection_prompt(question, low_candidates)
            if self.fallback_policy.enable_basic_evidence_injection
            else question
        )
        basic_draft = self.graph_client.query_basic(basic_prompt)
        if basic_draft.answer.strip():
            high_candidates.append(basic_draft.as_candidate())
        if basic_draft.error:
            errors.append(basic_draft.error)

        fused_pack = self.evidence_fusion(
            question,
            basic_draft.answer,
            low_candidates,
            self.citation_ref_resolver,
        )
        fused_candidates = fused_pack.candidates
        guardrail_result = self.guardrail(basic_draft.answer, [*fused_candidates, *high_candidates])
        answer = basic_draft.answer
        used_local_fallback = False
        synthesis_attempted = False
        fallback_reasons = self._answer_policy_failure_reasons("basic", answer, fused_pack)
        if basic_draft.error:
            fallback_reasons.append("basic_error")

        if self._guardrail_status(guardrail_result) == "fail":
            fallback_reasons.append("basic_guardrail_fail")

        synthesis_reason = fallback_reasons[0] if fallback_reasons else ""
        if fallback_reasons and not self.fallback_policy.disable_synthesis:
            synthesis_attempted = True
            answer = self._complete(question, fused_candidates, high_candidates)
            guardrail_result = self.guardrail(answer, [*fused_candidates, *high_candidates])
            synthesis_reasons = self._answer_policy_failure_reasons("synthesis", answer, fused_pack)
            fallback_reasons.extend(reason for reason in synthesis_reasons if reason not in fallback_reasons)

            if self._guardrail_status(guardrail_result) == "fail":
                if "synthesis_guardrail_fail" not in fallback_reasons:
                    fallback_reasons.append("synthesis_guardrail_fail")

            if (
                self.fallback_policy.enable_local_fallback
                and (self._guardrail_status(guardrail_result) == "fail" or synthesis_reasons)
            ):
                used_local_fallback = True
                local_draft = self.graph_client.query_local(question)
                if local_draft.error:
                    errors.append(local_draft.error)
                if local_draft.answer.strip():
                    high_candidates.append(local_draft.as_candidate())
                    answer = self._complete(question, fused_candidates, high_candidates)
                    guardrail_result = self.guardrail(answer, [*fused_candidates, *high_candidates])

        diagnostics = HybridDiagnostics(
            layer=classification.layer,
            classifier_confidence=classification.confidence,
            used_local_fallback=used_local_fallback,
            guardrail_status=self._guardrail_status(guardrail_result),
            guardrail_score=self._guardrail_score(guardrail_result),
            low_evidence_count=len(low_candidates),
            high_evidence_count=len(high_candidates),
            synthesis_attempted=synthesis_attempted,
            fallback_reasons=fallback_reasons,
            fused_evidence_refs=fused_pack.refs,
            fused_evidence_sources=fused_pack.source_counts,
            synthesis_reason=synthesis_reason,
            local_fallback_enabled=self.fallback_policy.enable_local_fallback,
            elapsed_seconds=time.perf_counter() - started_at,
            errors=errors,
        )
        return HybridV0Answer(answer=answer, diagnostics=diagnostics)

    def _complete(
        self,
        question: str,
        low_candidates: Sequence[EvidenceCandidate],
        high_candidates: Sequence[EvidenceCandidate],
    ) -> str:
        prompt = build_hybrid_v0_prompt(question, low_candidates, high_candidates)
        return self.llm_complete(prompt)

    @staticmethod
    def _guardrail_status(result: Any) -> str:
        return str(getattr(result, "status", "fail"))

    @staticmethod
    def _guardrail_score(result: Any) -> float:
        return float(getattr(result, "supported_ratio", 0.0))

    def _answer_policy_failure_reasons(
        self,
        stage: str,
        answer: str,
        fused_pack: FusedEvidencePack,
    ) -> list[str]:
        stripped = (answer or "").strip()
        reasons: list[str] = []
        if not stripped:
            reasons.append(f"{stage}_empty_answer")
            return reasons
        if len(stripped) < self.fallback_policy.min_answer_chars:
            reasons.append(f"{stage}_answer_too_short")
        has_data_citation = bool(_DATA_CITATION_RE.search(stripped))
        if self.fallback_policy.require_data_citation and not has_data_citation:
            reasons.append(f"{stage}_missing_data_citation")

        direct_refs = self._resolve_answer_refs(stripped)
        fused_top_refs = set(fused_pack.refs[: self.fallback_policy.fused_overlap_top_k])
        if (
            self.fallback_policy.require_low_ref_overlap_when_direct_refs
            and has_data_citation
            and not direct_refs
        ):
            reasons.append(f"{stage}_missing_resolved_citation_refs")
        elif (
            self.fallback_policy.require_low_ref_overlap_when_direct_refs
            and direct_refs
            and fused_top_refs
            and not set(direct_refs).intersection(fused_top_refs)
        ):
            reasons.append(f"{stage}_low_evidence_ref_overlap_low")
        return reasons

    def _resolve_answer_refs(self, answer: str) -> list[str]:
        refs = _extract_direct_refs(answer)
        if self.citation_ref_resolver is not None:
            for raw in self.citation_ref_resolver(answer):
                normalized = str(raw).strip()[:_TEXT_UNIT_REF_PREFIX_LEN]
                if normalized and normalized not in refs:
                    refs.append(normalized)
        return refs


def _extract_direct_refs(answer: str) -> list[str]:
    refs: list[str] = []
    for block in _DIRECT_REF_BLOCK_RE.findall(answer or ""):
        for raw in _DIRECT_REF_TOKEN_RE.findall(block):
            normalized = raw[:_TEXT_UNIT_REF_PREFIX_LEN]
            if normalized and normalized not in refs:
                refs.append(normalized)
    return refs


def _low_evidence_ref_prefixes(candidates: Sequence[EvidenceCandidate]) -> set[str]:
    return {
        candidate.ref.strip()[:_TEXT_UNIT_REF_PREFIX_LEN]
        for candidate in candidates
        if candidate.ref.strip()
    }


def _fallback_fuse_bm25_only(
    question: str,
    basic_answer: str,
    bm25_candidates: Sequence[EvidenceCandidate],
    citation_ref_resolver: CitationRefResolver | None,
) -> FusedEvidencePack:
    del question, basic_answer, citation_ref_resolver
    candidates = list(bm25_candidates)
    return FusedEvidencePack(
        candidates=candidates,
        refs=[candidate.ref.strip()[:_TEXT_UNIT_REF_PREFIX_LEN] for candidate in candidates if candidate.ref.strip()],
        source_counts={"bm25": len(candidates)} if candidates else {},
        fusion_debug=[],
    )


__all__ = [
    "Bm25Searcher",
    "CitationRefResolver",
    "GraphRagClient",
    "HybridFallbackPolicy",
    "HybridV0Orchestrator",
]
