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


def test_structural_linking_question_routes_to_mixed():
    classification = classify_question("I/O 管理、磁盘调度和文件系统在课程中如何衔接？")

    assert classification.layer is HybridLayer.MIXED
    assert any(hit["pattern"] == "衔接" for hit in classification.diagnostics["rule_hits"])


def test_shared_goal_question_routes_to_mixed():
    classification = classify_question("并发控制、同步机制和死锁处理共同服务于什么课程目标？")

    assert classification.layer is HybridLayer.MIXED


def test_course_mainline_question_routes_to_high():
    classification = classify_question("这门课程贯穿始终的方法论主线是什么？")

    assert classification.layer is HybridLayer.HIGH


def test_summary_question_records_rule_hits():
    classification = classify_question("请总结第 3 章的主要内容")

    assert classification.diagnostics["rule_hits"]


def test_broad_chapter_summary_routes_to_high():
    classification = classify_question("请概括第五章「虚拟存储器」的核心内容。")

    assert classification.layer is HybridLayer.HIGH
    assert any(hit["pattern"] == "broad_chapter_summary" for hit in classification.diagnostics["rule_hits"])


def test_specific_section_summary_stays_mixed():
    classification = classify_question("请概括第一章 1.4「操作系统的主要功能」的学习重点。")

    assert classification.layer is HybridLayer.MIXED
    assert any(hit["pattern"] == "section_summary" for hit in classification.diagnostics["rule_hits"])


def test_unmatched_question_defaults_to_low():
    classification = classify_question("请详细说明这个概念")

    assert classification.layer is HybridLayer.LOW
    assert classification.confidence == 0.5
