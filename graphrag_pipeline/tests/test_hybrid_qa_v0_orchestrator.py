from __future__ import annotations

from types import SimpleNamespace
from unittest.mock import MagicMock

from graphrag_pipeline.scripts.hybrid_qa.basic_local_client import GraphRagDraft
from graphrag_pipeline.scripts.hybrid_qa.evidence_fusion import FusedEvidencePack
from graphrag_pipeline.scripts.hybrid_qa.orchestrator_v0 import HybridFallbackPolicy, HybridV0Orchestrator
from graphrag_pipeline.scripts.hybrid_qa.types import EvidenceCandidate, HybridLayer


BASIC_LONG_ANSWER = (
    "操作系统是配置在计算机硬件上的第一层软件，是对硬件系统的首次扩充。"
    "它负责管理处理机、存储器、设备和文件等资源，并向用户和应用程序提供接口。"
    "这些职责共同说明操作系统处在硬件与上层软件之间，承担资源管理和抽象服务。"
)


def test_orchestrator_uses_basic_without_local_when_guardrail_passes():
    low_candidate = EvidenceCandidate(
        source="bm25",
        ref="tu-1",
        text="操作系统是第一层软件。",
        score=0.7,
        layer=HybridLayer.LOW,
    )
    bm25 = MagicMock()
    bm25.search.return_value = [low_candidate]
    graph_client = MagicMock()
    graph_client.query_basic.return_value = GraphRagDraft(
        "basic",
        BASIC_LONG_ANSWER + "[Data: Text Units (tu-1)]",
        1.0,
    )
    guardrail = MagicMock(return_value=SimpleNamespace(status="pass", supported_ratio=1.0))
    llm_complete = MagicMock(return_value="最终答案 [Data: Hybrid(tu-1)]")
    fusion = MagicMock(
        return_value=FusedEvidencePack(
            candidates=[low_candidate],
            refs=["tu-1"],
            source_counts={"bm25": 1, "basic-citation": 1},
            fusion_debug=[{"ref": "tu-1"}],
        )
    )

    orchestrator = HybridV0Orchestrator(
        bm25=bm25,
        graph_client=graph_client,
        guardrail=guardrail,
        llm_complete=llm_complete,
        evidence_fusion=fusion,
    )

    result = orchestrator.answer("操作系统是什么？")

    assert result.answer == BASIC_LONG_ANSWER + "[Data: Text Units (tu-1)]"
    assert result.diagnostics.used_local_fallback is False
    assert result.diagnostics.guardrail_status == "pass"
    assert result.diagnostics.guardrail_score == 1.0
    assert result.diagnostics.low_evidence_count == 1
    assert result.diagnostics.high_evidence_count == 1
    assert result.diagnostics.fused_evidence_refs == ["tu-1"]
    assert result.diagnostics.fused_evidence_sources == {"bm25": 1, "basic-citation": 1}
    assert result.diagnostics.local_fallback_enabled is False
    llm_complete.assert_not_called()
    graph_client.query_local.assert_not_called()


def test_orchestrator_synthesizes_when_basic_lacks_data_citation_without_local_by_default():
    low_candidate = EvidenceCandidate(
        source="bm25",
        ref="tu-1",
        text="操作系统是第一层软件。",
        score=0.7,
        layer=HybridLayer.LOW,
    )
    bm25 = MagicMock()
    bm25.search.return_value = [low_candidate]
    graph_client = MagicMock()
    graph_client.query_basic.return_value = GraphRagDraft(
        "basic",
        BASIC_LONG_ANSWER,
        1.0,
    )
    guardrail = MagicMock(return_value=SimpleNamespace(status="pass", supported_ratio=1.0))
    llm_complete = MagicMock(return_value=BASIC_LONG_ANSWER + "[Data: Hybrid(tu-1)]")
    fusion = MagicMock(
        return_value=FusedEvidencePack(
            candidates=[low_candidate],
            refs=["tu-1"],
            source_counts={"bm25": 1},
            fusion_debug=[],
        )
    )

    orchestrator = HybridV0Orchestrator(
        bm25=bm25,
        graph_client=graph_client,
        guardrail=guardrail,
        llm_complete=llm_complete,
        evidence_fusion=fusion,
    )

    result = orchestrator.answer("操作系统是什么？")

    assert result.answer == BASIC_LONG_ANSWER + "[Data: Hybrid(tu-1)]"
    assert result.diagnostics.used_local_fallback is False
    assert result.diagnostics.synthesis_attempted is True
    assert result.diagnostics.synthesis_reason == "basic_missing_data_citation"
    assert result.diagnostics.fallback_reasons == ["basic_missing_data_citation"]
    llm_complete.assert_called_once()
    graph_client.query_local.assert_not_called()


def test_orchestrator_does_not_use_local_when_synthesis_still_lacks_data_citation_by_default():
    low_candidate = EvidenceCandidate(
        source="bm25",
        ref="tu-1",
        text="操作系统是第一层软件。",
        score=0.7,
        layer=HybridLayer.LOW,
    )
    bm25 = MagicMock()
    bm25.search.return_value = [low_candidate]
    graph_client = MagicMock()
    graph_client.query_basic.return_value = GraphRagDraft(
        "basic",
        "操作系统是第一层软件。",
        1.0,
    )
    graph_client.query_local.return_value = GraphRagDraft(
        "local",
        "操作系统是第一层软件。[Data: Text Units (tu-1)]",
        1.4,
    )
    guardrail = MagicMock(return_value=SimpleNamespace(status="pass", supported_ratio=1.0))
    llm_complete = MagicMock(return_value="仍然没有引用的最终答案")
    fusion = MagicMock(
        return_value=FusedEvidencePack(
            candidates=[low_candidate],
            refs=["tu-1"],
            source_counts={"bm25": 1},
            fusion_debug=[],
        )
    )

    orchestrator = HybridV0Orchestrator(
        bm25=bm25,
        graph_client=graph_client,
        guardrail=guardrail,
        llm_complete=llm_complete,
        evidence_fusion=fusion,
    )

    result = orchestrator.answer("操作系统是什么？")

    assert result.diagnostics.used_local_fallback is False
    assert result.diagnostics.synthesis_attempted is True
    assert "synthesis_missing_data_citation" in result.diagnostics.fallback_reasons
    assert result.diagnostics.local_fallback_enabled is False
    graph_client.query_local.assert_not_called()


def test_orchestrator_uses_local_only_when_explicitly_enabled():
    low_candidate = EvidenceCandidate(
        source="bm25",
        ref="tu-1",
        text="操作系统是第一层软件。",
        score=0.7,
        layer=HybridLayer.LOW,
    )
    bm25 = MagicMock()
    bm25.search.return_value = [low_candidate]
    graph_client = MagicMock()
    graph_client.query_basic.return_value = GraphRagDraft("basic", "操作系统是第一层软件。", 1.0)
    graph_client.query_local.return_value = GraphRagDraft(
        "local",
        "操作系统是第一层软件。[Data: Text Units (tu-1)]",
        1.4,
    )
    guardrail = MagicMock(return_value=SimpleNamespace(status="pass", supported_ratio=1.0))
    llm_complete = MagicMock(
        side_effect=[
            "仍然没有引用的最终答案",
            "最终答案：操作系统是第一层软件。[Data: Hybrid(tu-1)]",
        ]
    )
    fusion = MagicMock(
        return_value=FusedEvidencePack(
            candidates=[low_candidate],
            refs=["tu-1"],
            source_counts={"bm25": 1},
            fusion_debug=[],
        )
    )

    orchestrator = HybridV0Orchestrator(
        bm25=bm25,
        graph_client=graph_client,
        guardrail=guardrail,
        llm_complete=llm_complete,
        evidence_fusion=fusion,
        fallback_policy=HybridFallbackPolicy(enable_local_fallback=True),
    )

    result = orchestrator.answer("操作系统是什么？")

    assert result.diagnostics.used_local_fallback is True
    assert result.diagnostics.local_fallback_enabled is True
    graph_client.query_local.assert_called_once_with("操作系统是什么？")


def test_orchestrator_synthesizes_when_direct_refs_do_not_overlap_low_evidence():
    low_candidate = EvidenceCandidate(
        source="bm25",
        ref="aaaabbbbcccc",
        text="操作系统是第一层软件。",
        score=0.7,
        layer=HybridLayer.LOW,
    )
    bm25 = MagicMock()
    bm25.search.return_value = [low_candidate]
    graph_client = MagicMock()
    graph_client.query_basic.return_value = GraphRagDraft(
        "basic",
        "操作系统是第一层软件。[Data: Text Units (xxxxbbbbyyyy)]",
        1.0,
    )
    guardrail = MagicMock(return_value=SimpleNamespace(status="pass", supported_ratio=1.0))
    llm_complete = MagicMock(return_value="最终答案：操作系统是第一层软件。[Data: Hybrid(aaaabbbbcccc)]")
    fusion = MagicMock(
        return_value=FusedEvidencePack(
            candidates=[low_candidate],
            refs=["aaaabbbbcccc"],
            source_counts={"bm25": 1},
            fusion_debug=[],
        )
    )

    orchestrator = HybridV0Orchestrator(
        bm25=bm25,
        graph_client=graph_client,
        guardrail=guardrail,
        llm_complete=llm_complete,
        evidence_fusion=fusion,
    )

    result = orchestrator.answer("操作系统是什么？")

    assert result.diagnostics.used_local_fallback is False
    assert result.diagnostics.synthesis_attempted is True
    assert "basic_low_evidence_ref_overlap_low" in result.diagnostics.fallback_reasons
    llm_complete.assert_called_once()
    graph_client.query_local.assert_not_called()


def test_orchestrator_synthesizes_when_resolved_source_refs_do_not_overlap_low_evidence():
    low_candidate = EvidenceCandidate(
        source="bm25",
        ref="aaaabbbbcccc",
        text="操作系统是第一层软件。",
        score=0.7,
        layer=HybridLayer.LOW,
    )
    bm25 = MagicMock()
    bm25.search.return_value = [low_candidate]
    graph_client = MagicMock()
    graph_client.query_basic.return_value = GraphRagDraft(
        "basic",
        BASIC_LONG_ANSWER + "[Data: Sources (141)]",
        1.0,
    )
    guardrail = MagicMock(return_value=SimpleNamespace(status="pass", supported_ratio=1.0))
    llm_complete = MagicMock(return_value="最终答案：操作系统是第一层软件。[Data: Hybrid(aaaabbbbcccc)]")
    citation_ref_resolver = MagicMock(return_value=["ddddbbbbcccc"])
    fusion = MagicMock(
        return_value=FusedEvidencePack(
            candidates=[low_candidate],
            refs=["aaaabbbbcccc"],
            source_counts={"bm25": 1},
            fusion_debug=[],
        )
    )

    orchestrator = HybridV0Orchestrator(
        bm25=bm25,
        graph_client=graph_client,
        guardrail=guardrail,
        llm_complete=llm_complete,
        citation_ref_resolver=citation_ref_resolver,
        evidence_fusion=fusion,
    )

    result = orchestrator.answer("操作系统是什么？")

    assert result.diagnostics.synthesis_attempted is True
    assert "basic_low_evidence_ref_overlap_low" in result.diagnostics.fallback_reasons
    citation_ref_resolver.assert_any_call(BASIC_LONG_ANSWER + "[Data: Sources (141)]")
    graph_client.query_local.assert_not_called()


def test_orchestrator_keeps_basic_when_resolved_source_refs_overlap_low_evidence():
    low_candidate = EvidenceCandidate(
        source="bm25",
        ref="aaaabbbbcccc",
        text="操作系统是第一层软件。",
        score=0.7,
        layer=HybridLayer.LOW,
    )
    bm25 = MagicMock()
    bm25.search.return_value = [low_candidate]
    graph_client = MagicMock()
    graph_client.query_basic.return_value = GraphRagDraft(
        "basic",
        BASIC_LONG_ANSWER + "[Data: Sources (141)]",
        1.0,
    )
    guardrail = MagicMock(return_value=SimpleNamespace(status="pass", supported_ratio=1.0))
    llm_complete = MagicMock(return_value="不应调用")
    citation_ref_resolver = MagicMock(return_value=["aaaabbbbcccc"])
    fusion = MagicMock(
        return_value=FusedEvidencePack(
            candidates=[low_candidate],
            refs=["aaaabbbbcccc"],
            source_counts={"bm25": 1, "basic-citation": 1},
            fusion_debug=[],
        )
    )

    orchestrator = HybridV0Orchestrator(
        bm25=bm25,
        graph_client=graph_client,
        guardrail=guardrail,
        llm_complete=llm_complete,
        citation_ref_resolver=citation_ref_resolver,
        evidence_fusion=fusion,
    )

    result = orchestrator.answer("操作系统是什么？")

    assert result.answer == BASIC_LONG_ANSWER + "[Data: Sources (141)]"
    assert result.diagnostics.synthesis_attempted is False
    llm_complete.assert_not_called()
    graph_client.query_local.assert_not_called()


def test_orchestrator_uses_local_fallback_when_guardrail_fails():
    low_candidate = EvidenceCandidate(
        source="bm25",
        ref="tu-1",
        text="操作系统负责管理硬件与软件资源。",
        score=0.7,
        layer=HybridLayer.LOW,
    )
    bm25 = MagicMock()
    bm25.search.return_value = [low_candidate]
    graph_client = MagicMock()
    graph_client.query_basic.return_value = GraphRagDraft(
        "basic",
        "操作系统定义不完整。",
        0.8,
    )
    graph_client.query_local.return_value = GraphRagDraft(
        "local",
        "操作系统负责管理硬件与软件资源。[Data: Text Units (tu-1)]",
        1.4,
    )
    guardrail = MagicMock(
        side_effect=[
            SimpleNamespace(status="fail", supported_ratio=0.0),
            SimpleNamespace(status="fail", supported_ratio=0.0),
            SimpleNamespace(status="pass", supported_ratio=1.0),
        ]
    )
    llm_complete = MagicMock(return_value="不支撑答案")
    fusion = MagicMock(
        return_value=FusedEvidencePack(
            candidates=[low_candidate],
            refs=["tu-1"],
            source_counts={"bm25": 1},
            fusion_debug=[],
        )
    )

    orchestrator = HybridV0Orchestrator(
        bm25=bm25,
        graph_client=graph_client,
        guardrail=guardrail,
        llm_complete=llm_complete,
        evidence_fusion=fusion,
    )

    result = orchestrator.answer("操作系统是什么？")

    assert result.diagnostics.used_local_fallback is False
    assert result.diagnostics.guardrail_status == "fail"
    assert result.diagnostics.guardrail_score == 0.0
    assert result.diagnostics.low_evidence_count == 1
    assert result.diagnostics.high_evidence_count == 1
    assert "synthesis_guardrail_fail" in result.diagnostics.fallback_reasons
    graph_client.query_local.assert_not_called()
