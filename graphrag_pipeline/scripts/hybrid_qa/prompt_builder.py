from __future__ import annotations

import re
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
    generation_context: str | None = None,
    max_generation_context_chars: int = 1200,
) -> str:
    template = template_path.read_text(encoding="utf-8")
    prompt = template.format(
        low_layer_text=_render_low_layer_evidence(low_layer),
        high_layer_text=_render_evidence(high_layer),
        question=question,
    )
    rendered_context = _render_generation_context(generation_context, max_generation_context_chars)
    if not rendered_context:
        return prompt
    return (
        f"{prompt.rstrip()}\n\n"
        "---CONVERSATION_CONTEXT---\n"
        f"{rendered_context}\n\n"
        "上下文使用规则：仅用于理解追问指代，不得补充证据中没有的课程知识。"
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
        "Hybrid(...) 中的 ref 必须逐字复制上方 evidence label 或 Text Unit Ref 行里的 12 位 Text Unit Ref；"
        "禁止使用页码、章节号、列表编号、+more、LOCAL_BM25_EVIDENCE 或其他说明性文本作为 ref。"
    )


def _render_low_layer_evidence(candidates: Sequence[EvidenceCandidate]) -> str:
    if not candidates:
        return "（无证据）"

    return "\n\n".join(
        f"[{candidate.source}:{candidate.ref}] score={candidate.score:.4f}\n"
        f"Text Unit Ref: {candidate.ref}\n"
        f"{candidate.text}"
        for candidate in _ordered_low_layer_for_prompt(candidates)
    )


def _render_evidence(candidates: Sequence[EvidenceCandidate]) -> str:
    if not candidates:
        return "（无证据）"

    return "\n\n".join(
        f"[{candidate.source}:{candidate.ref}] score={candidate.score:.4f}\n{candidate.text}"
        for candidate in candidates
    )


def _ordered_low_layer_for_prompt(candidates: Sequence[EvidenceCandidate]) -> list[EvidenceCandidate]:
    return [
        candidate
        for _, candidate in sorted(
            enumerate(candidates),
            key=lambda item: (_evidence_prompt_rank(item[1]), item[0]),
        )
    ]


def _evidence_prompt_rank(candidate: EvidenceCandidate) -> int:
    text = candidate.text or ""
    if "subsection:" in text:
        return 0
    heading_level = _extract_heading_level(text)
    if heading_level is None:
        return 0
    if heading_level >= 3:
        return 0
    return 2


def _extract_heading_level(text: str) -> int | None:
    match = re.search(r"heading_level:\s*(\d+)", text)
    if not match:
        return None
    return int(match.group(1))


def _render_generation_context(context: str | None, max_chars: int) -> str:
    normalized = re.sub(r"\s+", " ", str(context or "")).strip()
    if not normalized:
        return ""
    limit = max(max_chars, 0)
    if not limit:
        return ""
    if len(normalized) <= limit:
        return normalized
    return normalized[: max(limit - 1, 0)].rstrip() + "…"
