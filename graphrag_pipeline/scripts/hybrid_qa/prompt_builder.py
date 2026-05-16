from __future__ import annotations

from collections.abc import Sequence
from pathlib import Path

from graphrag_pipeline.scripts.hybrid_qa.types import EvidenceCandidate


PACKAGE_ROOT = Path(__file__).resolve().parents[2]
DEFAULT_TEMPLATE = PACKAGE_ROOT / "prompts" / "hybrid_v0_system_prompt.txt"


def build_hybrid_v0_prompt(
    question: str,
    low_layer: Sequence[EvidenceCandidate],
    high_layer: Sequence[EvidenceCandidate],
    template_path: Path = DEFAULT_TEMPLATE,
) -> str:
    template = template_path.read_text(encoding="utf-8")
    return template.format(
        low_layer_text=_render_low_layer_evidence(low_layer),
        high_layer_text=_render_evidence(high_layer),
        question=question,
    )


def build_hybrid_v0_basic_injection_prompt(
    question: str,
    low_layer: Sequence[EvidenceCandidate],
) -> str:
    return (
        "请回答下面的课程问题。你可以优先参考 LOCAL_BM25_EVIDENCE 中的课程原文片段，"
        "但仍需保持 GraphRAG Basic 原有检索和回答能力。\n\n"
        "---LOCAL_BM25_EVIDENCE---\n"
        f"{_render_low_layer_evidence(low_layer)}\n\n"
        "---QUESTION---\n"
        f"{question}\n\n"
        "回答要求：如果使用 LOCAL_BM25_EVIDENCE，请在答案末尾保留 "
        "[Data: Hybrid(ref1, ref2)] 形式的引用；也可以保留 GraphRAG 原始 Data 引用。"
    )


def _render_low_layer_evidence(candidates: Sequence[EvidenceCandidate]) -> str:
    if not candidates:
        return "（无证据）"

    return "\n\n".join(
        f"[{candidate.source}:{candidate.ref}] score={candidate.score:.4f}\n"
        f"Text Unit Ref: {candidate.ref}\n"
        f"{candidate.text}"
        for candidate in candidates
    )


def _render_evidence(candidates: Sequence[EvidenceCandidate]) -> str:
    if not candidates:
        return "（无证据）"

    return "\n\n".join(
        f"[{candidate.source}:{candidate.ref}] score={candidate.score:.4f}\n{candidate.text}"
        for candidate in candidates
    )
