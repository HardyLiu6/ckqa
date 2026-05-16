from __future__ import annotations

from dataclasses import dataclass

from graphrag_pipeline.scripts.hybrid_qa.types import EvidenceCandidate, HybridLayer


@dataclass(frozen=True, slots=True)
class GraphRagDraft:
    mode: str
    answer: str
    elapsed_seconds: float
    error: str = ""

    def as_candidate(self) -> EvidenceCandidate:
        return EvidenceCandidate(
            source=f"{self.mode}-draft",
            ref=self.mode,
            text=self.answer,
            score=0.0,
            layer=HybridLayer.HIGH,
            metadata={
                "elapsed_seconds": self.elapsed_seconds,
                "error": self.error,
            },
        )
