from __future__ import annotations

import re
from dataclasses import dataclass, field
from enum import Enum
from typing import Any


class HybridLayer(str, Enum):
    LOW = "low"
    MIXED = "mixed"
    HIGH = "high"


@dataclass(frozen=True, slots=True)
class EvidenceCandidate:
    source: str
    ref: str
    text: str
    score: float
    layer: HybridLayer
    metadata: dict[str, Any] = field(default_factory=dict)

    @property
    def dedup_key(self) -> str:
        return re.sub(r"\s+", " ", self.text).strip().casefold()


@dataclass(frozen=True, slots=True)
class HybridDiagnostics:
    layer: HybridLayer
    classifier_confidence: float
    used_local_fallback: bool = False
    guardrail_status: str = "not_checked"
    guardrail_score: float = 0.0
    low_evidence_count: int = 0
    high_evidence_count: int = 0
    synthesis_attempted: bool = False
    fallback_reasons: list[str] = field(default_factory=list)
    fused_evidence_refs: list[str] = field(default_factory=list)
    fused_evidence_sources: dict[str, int] = field(default_factory=dict)
    synthesis_reason: str = ""
    local_fallback_enabled: bool = False
    elapsed_seconds: float = 0.0
    errors: list[str] = field(default_factory=list)

    def to_dict(self) -> dict[str, Any]:
        return {
            "layer": self.layer.value,
            "classifier_confidence": self.classifier_confidence,
            "used_local_fallback": self.used_local_fallback,
            "guardrail_status": self.guardrail_status,
            "guardrail_score": self.guardrail_score,
            "low_evidence_count": self.low_evidence_count,
            "high_evidence_count": self.high_evidence_count,
            "synthesis_attempted": self.synthesis_attempted,
            "fallback_reasons": list(self.fallback_reasons),
            "fused_evidence_refs": list(self.fused_evidence_refs),
            "fused_evidence_sources": dict(self.fused_evidence_sources),
            "synthesis_reason": self.synthesis_reason,
            "local_fallback_enabled": self.local_fallback_enabled,
            "elapsed_seconds": self.elapsed_seconds,
            "errors": list(self.errors),
        }


@dataclass(frozen=True, slots=True)
class HybridV0Answer:
    answer: str
    diagnostics: HybridDiagnostics
