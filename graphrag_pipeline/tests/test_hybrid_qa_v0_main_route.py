#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""Hybrid v0 API 模型注册与路由测试。"""

from __future__ import annotations

import asyncio
import json
import subprocess
import sys
from pathlib import Path
from types import SimpleNamespace
from unittest.mock import AsyncMock, patch


_PROJECT_ROOT = Path(__file__).resolve().parent.parent
_MODULE_DIR = _PROJECT_ROOT / "utils"
if str(_MODULE_DIR) not in sys.path:
    sys.path.insert(0, str(_MODULE_DIR))

from main import (
    ChatCompletionRequest,
    GRAPHRAG_ROOT,
    Message,
    SUPPORTED_QUERY_MODELS,
    _CliGraphRagDraftClient,
    create_app,
    _resolve_query_response,
    format_response,
)
from graphrag_pipeline.scripts.hybrid_qa.types import HybridDiagnostics, HybridLayer, HybridV0Answer


def test_supported_query_models_registers_hybrid_v0():
    assert SUPPORTED_QUERY_MODELS["graphrag-hybrid-v0-search:latest"] == "hybrid_v0"


def test_main_exposes_package_parent_for_lazy_hybrid_imports():
    assert str(GRAPHRAG_ROOT.parent) in sys.path


def test_resolve_query_response_routes_hybrid_v0_without_cli():
    with (
        patch("main._run_hybrid_v0_query", new=AsyncMock(return_value="hybrid answer")) as hybrid_query,
        patch("main.run_graphrag_query_cli", new=AsyncMock(return_value="cli answer")) as cli_query,
    ):
        response = asyncio.run(
            _resolve_query_response("graphrag-hybrid-v0-search:latest", "操作系统是什么？")
        )

    assert response == format_response("hybrid answer")
    hybrid_query.assert_awaited_once_with("操作系统是什么？")
    cli_query.assert_not_awaited()


def test_hybrid_cli_draft_client_returns_error_on_timeout():
    with patch("main.subprocess.run", side_effect=subprocess.TimeoutExpired(cmd=["graphrag"], timeout=0.01)):
        draft = _CliGraphRagDraftClient(timeout_seconds=0.01).query_basic("操作系统是什么？")

    assert draft.mode == "basic"
    assert draft.answer == ""
    assert "超时" in draft.error


def _build_hybrid_orchestrator_with_fake_dependencies(monkeypatch, tmp_path):
    import main
    import graphrag_pipeline.scripts.hybrid_qa.bm25_text_units as bm25_text_units
    import graphrag_pipeline.scripts.hybrid_qa.evidence_selector as evidence_selector
    import graphrag_pipeline.scripts.hybrid_qa.orchestrator_v0 as orchestrator_v0
    import graphrag_pipeline.scripts.qa_eval.text_unit_lookup as text_unit_lookup

    output_dir = tmp_path / "output"
    output_dir.mkdir()
    (output_dir / "text_units.parquet").write_text("placeholder", encoding="utf-8")
    main._HYBRID_V0_ORCHESTRATORS.clear()

    constructed: dict[str, object] = {}

    class FakeLookup:
        def resolve_answer_refs(self, answer: str):
            del answer
            return []

    class FakeOrchestrator:
        def __init__(self, **kwargs):
            constructed.update(kwargs)

    monkeypatch.setattr(main, "_CliGraphRagDraftClient", lambda **kwargs: SimpleNamespace(kind="graph", kwargs=kwargs))
    monkeypatch.setattr(text_unit_lookup, "load_data_citation_lookup", lambda path: FakeLookup())
    monkeypatch.setattr(text_unit_lookup, "load_text_unit_lookup", lambda path: SimpleNamespace(kind="lookup"))
    monkeypatch.setattr(
        bm25_text_units,
        "build_text_unit_bm25",
        lambda path, cache_dir=None: SimpleNamespace(kind="legacy", path=path, cache_dir=cache_dir),
    )
    monkeypatch.setattr(
        evidence_selector,
        "build_v6_hybrid_evidence_selector",
        lambda path, config: SimpleNamespace(kind="v6", path=path, config=config),
    )
    monkeypatch.setattr(orchestrator_v0, "HybridV0Orchestrator", FakeOrchestrator)

    orchestrator = main._get_hybrid_v0_orchestrator(output_dir)
    assert isinstance(orchestrator, FakeOrchestrator)
    return constructed


def test_hybrid_v0_factory_defaults_to_v6_basic_injection_without_synthesis(monkeypatch, tmp_path):
    for name in (
        "CKQA_HYBRID_V0_EVIDENCE_STRATEGY",
        "CKQA_HYBRID_V0_ONE_SHOT_BASIC_INJECTION",
        "CKQA_HYBRID_V0_DISABLE_SYNTHESIS",
        "CKQA_HYBRID_V0_ENABLE_LOCAL_FALLBACK",
    ):
        monkeypatch.delenv(name, raising=False)

    constructed = _build_hybrid_orchestrator_with_fake_dependencies(monkeypatch, tmp_path)

    assert constructed["bm25"].kind == "v6"
    policy = constructed["fallback_policy"]
    assert policy.enable_basic_evidence_injection is True
    assert policy.disable_synthesis is True
    assert policy.enable_local_fallback is False


def test_hybrid_v0_factory_preserves_explicit_legacy_synthesis_overrides(monkeypatch, tmp_path):
    monkeypatch.setenv("CKQA_HYBRID_V0_EVIDENCE_STRATEGY", "legacy")
    monkeypatch.setenv("CKQA_HYBRID_V0_ONE_SHOT_BASIC_INJECTION", "false")
    monkeypatch.setenv("CKQA_HYBRID_V0_DISABLE_SYNTHESIS", "false")
    monkeypatch.setenv("CKQA_HYBRID_V0_ENABLE_LOCAL_FALLBACK", "true")

    constructed = _build_hybrid_orchestrator_with_fake_dependencies(monkeypatch, tmp_path)

    assert constructed["bm25"].kind == "legacy"
    policy = constructed["fallback_policy"]
    assert policy.enable_basic_evidence_injection is False
    assert policy.disable_synthesis is False
    assert policy.enable_local_fallback is True


def _get_route_endpoint(app, path: str, method: str):
    for route in app.routes:
        if getattr(route, "path", None) == path and method in getattr(route, "methods", []):
            return route.endpoint
    raise AssertionError(f"未找到路由 {method} {path}")


def test_chat_completion_response_includes_hybrid_diagnostics():
    app = create_app()
    endpoint = _get_route_endpoint(app, "/v1/chat/completions", "POST")
    diagnostics = HybridDiagnostics(
        layer=HybridLayer.LOW,
        classifier_confidence=0.8,
        used_local_fallback=False,
        guardrail_status="pass",
        guardrail_score=0.91,
        low_evidence_count=4,
        high_evidence_count=1,
    )

    with patch(
        "main._run_hybrid_v0_answer",
        new=AsyncMock(return_value=HybridV0Answer(answer="basic answer", diagnostics=diagnostics)),
    ):
        response = asyncio.run(
            endpoint(
                ChatCompletionRequest(
                    model="graphrag-hybrid-v0-search:latest",
                    messages=[Message(role="user", content="操作系统是什么？")],
                )
            )
        )

    payload = json.loads(response.body)
    assert payload["choices"][0]["message"]["content"] == format_response("basic answer")
    assert payload["hybrid_diagnostics"] == diagnostics.to_dict()
