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
