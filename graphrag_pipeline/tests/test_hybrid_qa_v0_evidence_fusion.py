from __future__ import annotations

from graphrag_pipeline.scripts.hybrid_qa.evidence_fusion import (
    EvidenceFusionConfig,
    fuse_basic_and_bm25_evidence,
)
from graphrag_pipeline.scripts.hybrid_qa.types import EvidenceCandidate, HybridLayer


class _Lookup:
    def __init__(self, values: dict[str, str]) -> None:
        self.values = values

    def get(self, prefix: str) -> str | None:
        return self.values.get(prefix[:12])


def test_fuse_basic_and_bm25_evidence_uses_rrf_and_deduplicates_refs():
    bm25 = [
        EvidenceCandidate("bm25", "bm25aaa11111ffff", "BM25 第一段", 9.0, HybridLayer.LOW),
        EvidenceCandidate("bm25", "shared222222ffff", "BM25 共享段", 8.0, HybridLayer.LOW),
    ]
    lookup = _Lookup(
        {
            "basic333333": "Basic 引用段",
            "shared222222": "Basic 和 BM25 共同段",
        }
    )

    pack = fuse_basic_and_bm25_evidence(
        question="操作系统是什么？",
        basic_answer="[Data: Sources (7)] [Data: Text Units (shared222222ffff)]",
        bm25_candidates=bm25,
        text_unit_lookup=lookup,
        citation_ref_resolver=lambda answer: ["basic333333"],
        config=EvidenceFusionConfig(fused_top_k=4),
    )

    assert pack.refs == ["shared222222", "basic333333", "bm25aaa11111"]
    assert len(pack.refs) == len(set(pack.refs))
    assert pack.source_counts["bm25"] == 2
    assert pack.source_counts["basic-citation"] == 2
    shared = pack.candidates[0]
    assert shared.ref == "shared222222"
    assert shared.metadata["fusion_sources"] == ["basic-citation", "bm25"]


def test_basic_citation_weight_can_outrank_bm25_top_hit():
    bm25 = [
        EvidenceCandidate("bm25", "bm25aaa11111", "BM25 第一段", 9.0, HybridLayer.LOW),
        EvidenceCandidate("bm25", "basic333333", "BM25 也召回了 basic 引用段", 8.0, HybridLayer.LOW),
    ]
    lookup = _Lookup({"basic333333": "Basic 引用段"})

    pack = fuse_basic_and_bm25_evidence(
        question="操作系统是什么？",
        basic_answer="[Data: Sources (7)]",
        bm25_candidates=bm25,
        text_unit_lookup=lookup,
        citation_ref_resolver=lambda answer: ["basic333333"],
        config=EvidenceFusionConfig(basic_citation_weight=3.0, fused_top_k=2),
    )

    assert pack.refs[0] == "basic333333"


def test_missing_citation_resolver_returns_bm25_only():
    bm25 = [
        EvidenceCandidate("bm25", "bm25aaa11111ffff", "BM25 第一段", 9.0, HybridLayer.LOW),
    ]

    pack = fuse_basic_and_bm25_evidence(
        question="操作系统是什么？",
        basic_answer="[Data: Sources (7)]",
        bm25_candidates=bm25,
        text_unit_lookup=_Lookup({}),
        citation_ref_resolver=None,
        config=EvidenceFusionConfig(),
    )

    assert pack.refs == ["bm25aaa11111"]
    assert pack.source_counts == {"bm25": 1}


def test_bm25_anchor_top_k_keeps_lexical_hits_ahead_of_basic_only_refs():
    bm25 = [
        EvidenceCandidate("bm25", "bm25gold1111", "BM25 命中的关键证据", 9.0, HybridLayer.LOW),
        EvidenceCandidate("bm25", "bm25gold2222", "BM25 命中的第二段关键证据", 8.0, HybridLayer.LOW),
    ]
    lookup = _Lookup(
        {
            "basic111111": "Basic 第一段引用",
            "basic222222": "Basic 第二段引用",
        }
    )

    pack = fuse_basic_and_bm25_evidence(
        question="文件系统如何组织目录？",
        basic_answer="[Data: Text Units (basic111111xxxx, basic222222xxxx)]",
        bm25_candidates=bm25,
        text_unit_lookup=lookup,
        citation_ref_resolver=None,
        config=EvidenceFusionConfig(bm25_anchor_top_k=2, fused_top_k=4),
    )

    assert pack.refs[:2] == ["bm25gold1111", "bm25gold2222"]
