from __future__ import annotations

from pathlib import Path

from graphrag_pipeline.scripts.hybrid_qa.prompt_builder import build_hybrid_v0_prompt
from graphrag_pipeline.scripts.hybrid_qa.types import EvidenceCandidate, HybridLayer


def test_build_hybrid_v0_prompt_renders_low_high_evidence_and_question(tmp_path):
    template_path = tmp_path / "hybrid-template.txt"
    template_path.write_text(
        "LOW={low_layer_text}\nHIGH={high_layer_text}\nQ={question}",
        encoding="utf-8",
    )
    low_layer = [
        EvidenceCandidate(
            source="bm25",
            ref="tu-1",
            text="操作系统是第一层软件",
            score=0.91,
            layer=HybridLayer.LOW,
        )
    ]
    high_layer = [
        EvidenceCandidate(
            source="basic-draft",
            ref="basic",
            text="操作系统管理硬件资源",
            score=1.0,
            layer=HybridLayer.HIGH,
        )
    ]

    prompt = build_hybrid_v0_prompt(
        question="操作系统是什么？",
        low_layer=low_layer,
        high_layer=high_layer,
        template_path=template_path,
    )

    assert "[bm25:tu-1]" in prompt
    assert "Text Unit Ref: tu-1" in prompt
    assert "[basic-draft:basic]" in prompt
    assert "Q=操作系统是什么？" in prompt


def test_default_template_resolves_when_cwd_is_graphrag_pipeline(monkeypatch):
    monkeypatch.chdir(Path("graphrag_pipeline"))

    prompt = build_hybrid_v0_prompt("操作系统是什么？", [], [])

    assert "用户问题：" in prompt
    assert "操作系统是什么？" in prompt


def test_default_prompt_does_not_force_text_unit_citation_rewrites():
    low_layer = [
        EvidenceCandidate(
            source="bm25",
            ref="d244f9016ac8abcdef",
            text="操作系统是配置在计算机硬件上的第一层软件。",
            score=1.0,
            layer=HybridLayer.LOW,
        )
    ]

    prompt = build_hybrid_v0_prompt("操作系统是什么？", low_layer, [])

    assert "[Data: Text Units (" not in prompt
    assert "优先引用低层证据中的 Text Unit Ref" not in prompt
    assert "[Data: Hybrid(ref1, ref2)]" in prompt
    assert "不要改写为 Text Units" in prompt
    assert "Text Unit Ref: d244f9016ac8abcdef" in prompt
