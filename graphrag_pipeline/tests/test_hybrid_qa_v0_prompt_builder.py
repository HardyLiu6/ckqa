from __future__ import annotations

from pathlib import Path

from graphrag_pipeline.scripts.hybrid_qa.prompt_builder import (
    build_hybrid_v0_basic_injection_prompt,
    build_hybrid_v0_prompt,
)
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
    assert "12 位 Text Unit Ref" in prompt
    assert "页码" in prompt
    assert "章节号" in prompt
    assert "列表编号" in prompt
    assert "+more" in prompt
    assert "Text Unit Ref: d244f9016ac8abcdef" in prompt


def test_basic_injection_prompt_constrains_hybrid_refs_to_text_unit_refs():
    low_layer = [
        EvidenceCandidate(
            source="bm25-v6",
            ref="d244f9016ac8",
            text="操作系统是配置在计算机硬件上的第一层软件。",
            score=1.0,
            layer=HybridLayer.LOW,
        )
    ]

    prompt = build_hybrid_v0_basic_injection_prompt("操作系统是什么？", low_layer)

    assert "[Data: Hybrid(ref1, ref2)]" in prompt
    assert "12 位 Text Unit Ref" in prompt
    assert "逐字复制" in prompt
    assert "页码" in prompt
    assert "章节号" in prompt
    assert "列表编号" in prompt
    assert "+more" in prompt
    assert "LOCAL_BM25_EVIDENCE" in prompt


def test_low_layer_evidence_displays_subsection_before_heading(tmp_path):
    template_path = tmp_path / "hybrid-template.txt"
    template_path.write_text("LOW={low_layer_text}\nQ={question}", encoding="utf-8")
    heading = EvidenceCandidate(
        source="bm25-v6",
        ref="heading111111",
        text="document_type: textbook. chapter: 第五章 虚拟存储器. heading_level: 1. heading_path_text: 第五章 虚拟存储器。",
        score=10.0,
        layer=HybridLayer.LOW,
    )
    subsection = EvidenceCandidate(
        source="bm25-v6",
        ref="subsect22222",
        text="document_type: textbook. chapter: 第五章 虚拟存储器. section: 5.1 虚拟存储器概述. subsection: 5.1.3 虚拟存储器的实现方法. heading_level: 3.",
        score=9.0,
        layer=HybridLayer.LOW,
    )

    prompt = build_hybrid_v0_prompt(
        "请概括第五章「虚拟存储器」的核心内容。",
        [heading, subsection],
        [],
        template_path=template_path,
    )

    assert prompt.index("Text Unit Ref: subsect22222") < prompt.index("Text Unit Ref: heading111111")


def test_low_layer_evidence_keeps_input_order_when_heading_type_unknown(tmp_path):
    template_path = tmp_path / "hybrid-template.txt"
    template_path.write_text("LOW={low_layer_text}\nQ={question}", encoding="utf-8")
    first = EvidenceCandidate("bm25-v6", "first1111111", "第一段普通证据。", 2.0, HybridLayer.LOW)
    second = EvidenceCandidate("bm25-v6", "second222222", "第二段普通证据。", 1.0, HybridLayer.LOW)

    prompt = build_hybrid_v0_prompt("普通问题", [first, second], [], template_path=template_path)

    assert prompt.index("Text Unit Ref: first1111111") < prompt.index("Text Unit Ref: second222222")
