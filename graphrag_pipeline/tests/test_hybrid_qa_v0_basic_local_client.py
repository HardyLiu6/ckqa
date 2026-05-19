from __future__ import annotations

from graphrag_pipeline.scripts.hybrid_qa.basic_local_client import GraphRagDraft
from graphrag_pipeline.scripts.hybrid_qa.types import HybridLayer


def test_graphrag_draft_as_candidate_uses_mode_as_high_layer_ref():
    draft = GraphRagDraft(
        mode="basic",
        answer="操作系统是第一层软件。",
        elapsed_seconds=1.2,
    )

    candidate = draft.as_candidate()

    assert candidate.source == "basic-draft"
    assert candidate.ref == "basic"
    assert candidate.text == "操作系统是第一层软件。"
    assert candidate.layer == HybridLayer.HIGH
    assert candidate.metadata["elapsed_seconds"] == 1.2
