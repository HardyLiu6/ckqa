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
