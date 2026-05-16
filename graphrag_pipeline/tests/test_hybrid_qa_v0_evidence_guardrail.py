from __future__ import annotations

from unittest.mock import patch

import numpy as np

from graphrag_pipeline.scripts.hybrid_qa.evidence_guardrail import (
    EvidenceGuardrailConfig,
    check_answer_supported_by_evidence,
)
from graphrag_pipeline.scripts.hybrid_qa.types import EvidenceCandidate, HybridLayer


def _evidence(text: str) -> EvidenceCandidate:
    return EvidenceCandidate(
        source="local",
        ref="chunk-1",
        text=text,
        score=0.8,
        layer=HybridLayer.LOW,
    )


def test_answer_with_supported_single_evidence_chunk_passes() -> None:
    config = EvidenceGuardrailConfig(similarity_threshold=0.62)

    with patch(
        "graphrag_pipeline.scripts.hybrid_qa.evidence_guardrail._answer_evidence_similarity",
        return_value=np.asarray([[0.8]], dtype=np.float32),
    ):
        result = check_answer_supported_by_evidence(
            answer="动态规划通过状态转移复用子问题结果。",
            evidence=[_evidence("动态规划会保存子问题结果并通过状态转移求解。")],
            config=config,
        )

    assert result.status == "pass"
    assert result.supported_ratio == 1.0


def test_answer_below_min_supported_ratio_fails() -> None:
    config = EvidenceGuardrailConfig(min_supported_ratio=0.75, max_chunk_chars=10)

    with patch(
        "graphrag_pipeline.scripts.hybrid_qa.evidence_guardrail._answer_evidence_similarity",
        return_value=np.asarray([[0.2], [0.7]], dtype=np.float32),
    ):
        result = check_answer_supported_by_evidence(
            answer="第一句缺少证据支持。第二句有证据支撑。",
            evidence=[_evidence("第二句对应的证据内容。")],
            config=config,
        )

    assert result.status == "fail"
    assert result.supported_ratio == 0.5
