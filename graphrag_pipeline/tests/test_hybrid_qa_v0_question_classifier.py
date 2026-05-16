from __future__ import annotations

from graphrag_pipeline.scripts.hybrid_qa.question_classifier import classify_question
from graphrag_pipeline.scripts.hybrid_qa.types import HybridLayer


def test_definition_question_routes_to_low_with_confidence():
    classification = classify_question("操作系统的定义是什么？")

    assert classification.layer is HybridLayer.LOW
    assert classification.confidence >= 0.6


def test_comparison_question_routes_to_mixed():
    classification = classify_question("K-Means 和 DBSCAN 的区别是什么？")

    assert classification.layer is HybridLayer.MIXED


def test_difference_wording_routes_to_mixed():
    classification = classify_question("预防死锁和避免死锁在处理思路上有什么不同？")

    assert classification.layer is HybridLayer.MIXED


def test_course_mainline_question_routes_to_high():
    classification = classify_question("这门课程贯穿始终的方法论主线是什么？")

    assert classification.layer is HybridLayer.HIGH


def test_summary_question_records_rule_hits():
    classification = classify_question("请总结第 3 章的主要内容")

    assert classification.diagnostics["rule_hits"]


def test_unmatched_question_defaults_to_low():
    classification = classify_question("请详细说明这个概念")

    assert classification.layer is HybridLayer.LOW
    assert classification.confidence == 0.5
