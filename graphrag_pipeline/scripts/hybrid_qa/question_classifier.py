from __future__ import annotations

import re
from dataclasses import dataclass
from typing import Any

from graphrag_pipeline.scripts.hybrid_qa.types import HybridLayer


@dataclass(frozen=True, slots=True)
class QuestionClassification:
    layer: HybridLayer
    confidence: float
    diagnostics: dict[str, Any]


_RULE_PATTERNS: dict[HybridLayer, tuple[str, ...]] = {
    HybridLayer.LOW: (
        "定义",
        "是什么",
        "参数",
        "章节页",
        "哪里",
        "哪一页",
        "列出",
        "指出",
    ),
    HybridLayer.MIXED: (
        "区别",
        "差异",
        "不同",
        "联系",
        "关系",
        "对比",
        "为什么",
        "处理思路",
        "总结第",
        "衔接",
        "串联",
        "贯通",
        "协同",
        "共同服务",
        "共同体现",
    ),
    HybridLayer.HIGH: (
        "整体",
        "总体",
        "全局",
        "贯穿",
        "主线",
        "方法论",
        "这门课",
    ),
}

_SUMMARY_VERBS_RE = r"(?:总结|概括|归纳)"
_CHAPTER_RE = r"第\s*[一二三四五六七八九十百千万\d]+\s*章"
_SECTION_MARKER_RE = re.compile(r"(?:\d+\.\d+|第\s*[一二三四五六七八九十百千万\d]+\s*节)")
_CHAPTER_SUMMARY_RE = re.compile(rf"{_SUMMARY_VERBS_RE}.{{0,12}}{_CHAPTER_RE}|{_CHAPTER_RE}.{{0,12}}{_SUMMARY_VERBS_RE}")


def classify_question(question: str) -> QuestionClassification:
    normalized_question = question.strip()
    rule_hits = _collect_rule_hits(normalized_question)
    hit_counts = {
        layer: sum(1 for hit in rule_hits if hit["layer"] == layer.value)
        for layer in HybridLayer
    }
    total_hits = len(rule_hits)

    layer = _select_layer(hit_counts)
    confidence = 0.5 if total_hits == 0 else min(0.95, 0.55 + 0.2 * total_hits)

    return QuestionClassification(
        layer=layer,
        confidence=confidence,
        diagnostics={
            "rule_hits": rule_hits,
            "hit_counts": {layer.value: count for layer, count in hit_counts.items()},
        },
    )


def _collect_rule_hits(question: str) -> list[dict[str, str]]:
    hits = [
        {"layer": layer.value, "pattern": pattern}
        for layer, patterns in _RULE_PATTERNS.items()
        for pattern in patterns
        if pattern in question
    ]
    hits.extend(_chapter_summary_rule_hits(question))
    return hits


def _chapter_summary_rule_hits(question: str) -> list[dict[str, str]]:
    if not _CHAPTER_SUMMARY_RE.search(question):
        return []
    if _SECTION_MARKER_RE.search(question):
        return [{"layer": HybridLayer.MIXED.value, "pattern": "section_summary"}]
    return [{"layer": HybridLayer.HIGH.value, "pattern": "broad_chapter_summary"}]


def _select_layer(hit_counts: dict[HybridLayer, int]) -> HybridLayer:
    if not any(hit_counts.values()):
        return HybridLayer.LOW

    if hit_counts[HybridLayer.HIGH] > 0 and hit_counts[HybridLayer.LOW] == 0:
        return HybridLayer.HIGH

    return max(
        HybridLayer,
        key=lambda layer: (
            hit_counts[layer],
            _TIE_BREAK_PRIORITY[layer],
        ),
    )


_TIE_BREAK_PRIORITY: dict[HybridLayer, int] = {
    HybridLayer.HIGH: 3,
    HybridLayer.MIXED: 2,
    HybridLayer.LOW: 1,
}


__all__ = ["QuestionClassification", "classify_question"]
