from __future__ import annotations

import tomllib
from pathlib import Path


from graphrag_pipeline.scripts.hybrid_qa.types import (
    EvidenceCandidate,
    HybridDiagnostics,
    HybridLayer,
)


PYPROJECT_PATH = Path(__file__).resolve().parents[1] / "pyproject.toml"


def test_hybrid_extra_declares_bm25_dependency():
    pyproject = tomllib.loads(PYPROJECT_PATH.read_text(encoding="utf-8"))
    extras = pyproject["project"]["optional-dependencies"]

    assert "rank-bm25>=0.2.2" in extras["hybrid"]


def test_flag_embedding_stays_in_eval_extra():
    pyproject = tomllib.loads(PYPROJECT_PATH.read_text(encoding="utf-8"))
    extras = pyproject["project"]["optional-dependencies"]

    assert "FlagEmbedding>=1.2.10" in extras["eval"]


def test_bm25_dependency_is_not_required_by_default():
    pyproject = tomllib.loads(PYPROJECT_PATH.read_text(encoding="utf-8"))
    dependencies = pyproject["project"]["dependencies"]

    assert not any(dependency.startswith("rank-bm25") for dependency in dependencies)


def test_evidence_candidate_dedup_key_normalizes_whitespace():
    left = EvidenceCandidate(
        source="basic",
        ref="chunk-1",
        text=" 操作系统\n 是 第一层软件 ",
        score=0.9,
        layer=HybridLayer.LOW,
    )
    right = EvidenceCandidate(
        source="basic",
        ref="chunk-2",
        text="操作系统 是 第一层软件",
        score=0.8,
        layer=HybridLayer.LOW,
    )

    assert left.dedup_key == right.dedup_key


def test_hybrid_diagnostics_defaults_to_not_checked_without_local_fallback():
    diagnostics = HybridDiagnostics(layer=HybridLayer.MIXED, classifier_confidence=0.5)

    assert diagnostics.used_local_fallback is False
    assert diagnostics.guardrail_status == "not_checked"


def test_hybrid_diagnostics_serializes_to_json_safe_dict():
    diagnostics = HybridDiagnostics(
        layer=HybridLayer.LOW,
        classifier_confidence=0.75,
        used_local_fallback=True,
        guardrail_status="pass",
        guardrail_score=0.88,
        low_evidence_count=3,
        high_evidence_count=2,
        synthesis_attempted=True,
        fallback_reasons=["basic_missing_data_citation"],
        elapsed_seconds=12.5,
        errors=["local timeout"],
    )

    assert diagnostics.to_dict() == {
        "layer": "low",
        "classifier_confidence": 0.75,
        "used_local_fallback": True,
        "guardrail_status": "pass",
        "guardrail_score": 0.88,
        "low_evidence_count": 3,
            "high_evidence_count": 2,
            "synthesis_attempted": True,
            "fallback_reasons": ["basic_missing_data_citation"],
            "fused_evidence_refs": [],
            "fused_evidence_sources": {},
            "synthesis_reason": "",
            "local_fallback_enabled": False,
            "elapsed_seconds": 12.5,
            "errors": ["local timeout"],
        }
