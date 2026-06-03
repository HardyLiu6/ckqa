from __future__ import annotations

import sys
from pathlib import Path

import pandas as pd


_PROJECT_ROOT = Path(__file__).resolve().parent.parent
_MODULE_DIR = _PROJECT_ROOT / "utils"
if str(_MODULE_DIR) not in sys.path:
    sys.path.insert(0, str(_MODULE_DIR))

from query_citation_resolver import resolve_answer_citations


def test_resolves_data_sources_to_student_readable_references(tmp_path):
    output_dir = tmp_path / "index-output"
    output_dir.mkdir()
    pd.DataFrame(
        [
            {
                "id": "text-unit-156-full-id",
                "human_readable_id": 156,
                "text": (
                    "document_type: textbook. chapter: 第三章 处理机调度与死锁. "
                    "section: 3.8 死锁的检测与解除. subsection: 3.8.1 死锁的检测. "
                    "heading_level: 3. heading_path_text: 第三章 处理机调度与死锁 > 3.8 死锁的检测与解除 > 3.8.1 死锁的检测. "
                    "page_start: 123. page_end: 125. section_level: 3. "
                    "source_file: 计算机操作系统教材. course_id: os. "
                    "资源分配图是一种用来描述系统中资源分配状态的有向图。"
                ),
                "n_tokens": 30,
                "document_id": "doc-os",
                "entity_ids": [],
                "relationship_ids": [],
                "covariate_ids": [],
            },
            {
                "id": "text-unit-157-full-id",
                "human_readable_id": 157,
                "text": (
                    "document_type: textbook. chapter: 第三章 处理机调度与死锁. "
                    "section: 3.8 死锁的检测与解除. subsection: 3.8.1 死锁的检测. "
                    "heading_level: 3. heading_path_text: 第三章 处理机调度与死锁 > 3.8 死锁的检测与解除 > 3.8.1 死锁的检测. "
                    "page_start: 123. page_end: 125. section_level: 3. "
                    "source_file: 计算机操作系统教材. course_id: os. "
                    "若资源分配图不可完全简化，则系统处于死锁状态。"
                ),
                "n_tokens": 24,
                "document_id": "doc-os",
                "entity_ids": [],
                "relationship_ids": [],
                "covariate_ids": [],
            },
        ]
    ).to_parquet(output_dir / "text_units.parquet")

    answer = "资源分配图可用于描述死锁状态 [Data: Sources (156, 157)]。"

    resolved = resolve_answer_citations(answer, output_dir)

    assert resolved.display_text.startswith("资源分配图可用于描述死锁状态 [来源 1、2]。")
    assert "参考来源：" in resolved.display_text
    assert "[来源 1] 计算机操作系统教材 · 第三章 处理机调度与死锁 > 3.8 死锁的检测与解除 > 3.8.1 死锁的检测 · p123-125" in resolved.display_text
    assert "[来源 2] 计算机操作系统教材 · 第三章 处理机调度与死锁 > 3.8 死锁的检测与解除 > 3.8.1 死锁的检测 · p123-125" in resolved.display_text
    assert [source.ref for source in resolved.sources] == ["156", "157"]
    assert resolved.sources[0].source_file == "计算机操作系统教材"
    assert resolved.sources[0].heading_path.endswith("3.8.1 死锁的检测")
    assert resolved.sources[0].page_start == 123
    assert resolved.sources[0].page_end == 125
    assert "资源分配图是一种用来描述系统中资源分配状态的有向图" in resolved.sources[0].snippet


def test_keeps_answer_when_text_units_missing(tmp_path):
    answer = "回答没有可解析来源 [Data: Sources (999)]。"

    resolved = resolve_answer_citations(answer, tmp_path)

    assert resolved.display_text == "回答没有可解析来源 [已参考课程知识库]。"
    assert resolved.sources == []


def test_resolves_hybrid_refs_to_text_units_by_id_prefix(tmp_path):
    output_dir = tmp_path / "index-output"
    output_dir.mkdir()
    pd.DataFrame(
        [
            {
                "id": "d244f9016ac8abcdef",
                "human_readable_id": 116,
                "text": (
                    "document_type: textbook. chapter: 第三章 处理机调度与死锁. "
                    "section: 3.2 作业与作业调度. subsection: 3.2.3 先来先服务和短作业优先. "
                    "heading_path_text: 第三章 处理机调度与死锁 > 3.2 作业与作业调度 > 3.2.3 先来先服务和短作业优先. "
                    "page_start: 88. page_end: 89. source_file: 操作系统教材. "
                    "SJF 调度选择估计运行时间最短的作业。"
                ),
                "document_id": "doc-os",
            },
            {
                "id": "81d99ad61e36ffff",
                "human_readable_id": 117,
                "text": (
                    "document_type: textbook. chapter: 第三章 处理机调度与死锁. "
                    "section: 3.2 作业与作业调度. subsection: 3.2.4 抢占式短作业优先. "
                    "heading_path_text: 第三章 处理机调度与死锁 > 3.2 作业与作业调度 > 3.2.4 抢占式短作业优先. "
                    "page_start: 90. page_end: 91. source_file: 操作系统教材. "
                    "SRTN 会在新短作业到达时抢占剩余时间更长的作业。"
                ),
                "document_id": "doc-os",
            },
        ]
    ).to_parquet(output_dir / "text_units.parquet")

    resolved = resolve_answer_citations(
        "SJF 与 SRTN 的关键差异是是否抢占 [Data: Hybrid(d244f9016ac8, 81d99ad61e36)]。",
        output_dir,
        mode="hybrid_v0",
    )

    assert "SJF 与 SRTN 的关键差异是是否抢占 [来源 1、2]。" in resolved.display_text
    assert "[已参考课程知识库]" not in resolved.display_text
    assert [source.kind for source in resolved.sources] == ["hybrid_text_unit", "hybrid_text_unit"]
    assert resolved.sources[0].source_file == "操作系统教材"
    assert resolved.sources[0].heading_path.endswith("3.2.3 先来先服务和短作业优先")
    assert resolved.sources[1].page_start == 90
    assert resolved.sources[0].to_dict()["source_type"] == "hybrid_text_unit"


def test_resolves_global_report_and_adds_fallback_text_units(tmp_path):
    output_dir = tmp_path / "index-output"
    output_dir.mkdir()
    pd.DataFrame(
        [
            {
                "id": "community-7",
                "human_readable_id": 7,
                "title": "操作系统第一章主题报告",
                "summary": "本报告概括操作系统定义、设计目标和发展历史。",
                "full_content": "更长的报告正文。",
            }
        ]
    ).to_parquet(output_dir / "community_reports.parquet")
    pd.DataFrame(
        [
            {
                "id": "text-unit-21",
                "human_readable_id": 21,
                "text": (
                    "source_file: 操作系统教材. heading_path_text: 第一章 > 操作系统目标. "
                    "page_start: 9. page_end: 10. 操作系统的发展目标包括方便性、有效性、可扩充性和开放性。"
                ),
                "document_id": "doc-os",
            },
            {
                "id": "text-unit-22",
                "human_readable_id": 22,
                "text": (
                    "source_file: 操作系统教材. heading_path_text: 第一章 > 操作系统发展历史. "
                    "page_start: 12. page_end: 13. 操作系统发展经历了单道批处理、多道程序和分时系统等阶段。"
                ),
                "document_id": "doc-os",
            },
        ]
    ).to_parquet(output_dir / "text_units.parquet")

    resolved = resolve_answer_citations(
        "第一章可以从定义、目标和发展历史总结 [Data: Reports (7)]。",
        output_dir,
        mode="global",
        fallback_query="操作系统第一章发展目标",
    )

    assert resolved.display_text.startswith("第一章可以从定义、目标和发展历史总结 [来源 1]。")
    assert [source.kind for source in resolved.sources] == [
        "graphrag_report",
        "global_fallback_text_unit",
        "global_fallback_text_unit",
    ]
    assert resolved.sources[0].source_file == "课程知识图谱报告"
    assert resolved.sources[0].heading_path == "操作系统第一章主题报告"
    assert "操作系统定义、设计目标和发展历史" in resolved.sources[0].snippet
    assert resolved.sources[1].source_file == "操作系统教材"
    assert resolved.sources[1].page_start == 9
    assert resolved.sources[1].to_dict()["source_type"] == "global_fallback_text_unit"


def test_drift_adds_query_ranked_fallback_even_when_answer_has_source_refs(tmp_path):
    output_dir = tmp_path / "index-output"
    output_dir.mkdir()
    pd.DataFrame(
        [
            {
                "id": "text-unit-1",
                "human_readable_id": 1,
                "text": (
                    "source_file: 操作系统教材. heading_path_text: 第一章 > 操作系统引论. "
                    "page_start: 9. page_end: 10. 操作系统引论提到处理机调度是资源管理的一部分。"
                ),
                "document_id": "doc-os",
            },
            {
                "id": "text-unit-31",
                "human_readable_id": 31,
                "text": (
                    "source_file: 操作系统教材. heading_path_text: 第三章 > 处理机调度 > 抢占式调度与非抢占式调度. "
                    "page_start: 88. page_end: 90. 抢占式调度允许高优先级进程中断当前进程，响应快但调度开销较高；"
                    "非抢占式调度实现简单但响应性较差。"
                ),
                "document_id": "doc-os",
            },
        ]
    ).to_parquet(output_dir / "text_units.parquet")

    resolved = resolve_answer_citations(
        "抢占式调度响应更快 [Data: Sources (1)]。",
        output_dir,
        mode="drift",
        fallback_query="抢占式调度和非抢占式调度各有什么优缺点？",
        fallback_text_unit_limit=1,
    )

    assert [source.kind for source in resolved.sources] == [
        "global_fallback_text_unit",
        "graphrag_citation",
    ]
    assert resolved.display_text.startswith("抢占式调度响应更快 [来源 2]。")
    assert resolved.sources[0].heading_path == "第三章 > 处理机调度 > 抢占式调度与非抢占式调度"
    assert resolved.sources[0].page_start == 88
    assert resolved.sources[1].heading_path == "第一章 > 操作系统引论"


def test_drift_uses_fallback_only_when_source_refs_cannot_be_resolved(tmp_path):
    output_dir = tmp_path / "index-output"
    output_dir.mkdir()
    pd.DataFrame(
        [
            {
                "id": "text-unit-31",
                "human_readable_id": 31,
                "text": (
                    "source_file: 操作系统教材. heading_path_text: 第三章 > 处理机调度 > 抢占式调度与非抢占式调度. "
                    "page_start: 88. page_end: 90. 抢占式调度允许高优先级进程中断当前进程，响应快但调度开销较高；"
                    "非抢占式调度实现简单但响应性较差。"
                ),
                "document_id": "doc-os",
            },
        ]
    ).to_parquet(output_dir / "text_units.parquet")

    resolved = resolve_answer_citations(
        "抢占式调度响应更快 [Data: Sources (999)]。",
        output_dir,
        mode="drift",
        fallback_query="抢占式调度和非抢占式调度各有什么优缺点？",
        fallback_text_unit_limit=1,
    )

    assert [source.kind for source in resolved.sources] == ["global_fallback_text_unit"]
    assert resolved.display_text.startswith("抢占式调度响应更快 [来源 1]。")
    assert resolved.sources[0].heading_path == "第三章 > 处理机调度 > 抢占式调度与非抢占式调度"


def test_resolves_entity_and_relationship_references(tmp_path):
    output_dir = tmp_path / "index-output"
    output_dir.mkdir()
    pd.DataFrame(
        [
            {
                "id": "entity-os",
                "human_readable_id": 4,
                "title": "操作系统",
                "description": "操作系统是管理计算机资源的核心系统软件。",
            }
        ]
    ).to_parquet(output_dir / "entities.parquet")
    pd.DataFrame(
        [
            {
                "id": "rel-3",
                "human_readable_id": 3,
                "source": "操作系统",
                "target": "资源管理",
                "description": "操作系统通过资源管理支持应用运行。",
            }
        ]
    ).to_parquet(output_dir / "relationships.parquet")

    resolved = resolve_answer_citations(
        "操作系统围绕资源管理展开 [Data: Entities (4); Relationships (3)]。",
        output_dir,
    )

    assert "操作系统围绕资源管理展开 [来源 1、2]。" in resolved.display_text
    assert [source.kind for source in resolved.sources] == ["graphrag_entity", "graphrag_relationship"]
    assert resolved.sources[1].heading_path == "操作系统 -> 资源管理"
