#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""课程画像 hints 抽取测试。"""

from __future__ import annotations

import json
import sys
import tempfile
import unittest
from pathlib import Path

import pandas as pd


_PROJECT_ROOT = Path(__file__).resolve().parent.parent
_MODULE_DIR = _PROJECT_ROOT / "utils"
if str(_MODULE_DIR) not in sys.path:
    sys.path.insert(0, str(_MODULE_DIR))

from course_profile_hints import extract_course_profile_hints


class TestCourseProfileHints(unittest.TestCase):
    def test_extracts_generic_hints_from_section_docs_without_course_specific_rules(self):
        with tempfile.TemporaryDirectory() as tmp:
            section_docs = Path(tmp) / "section_docs.json"
            section_docs.write_text(
                json.dumps(
                    [
                        {
                            "course_id": "course-os",
                            "heading_path_text": "第四章 存储器管理 > 4.5 分页存储管理方式 > 4.5.2 地址变换机构",
                            "text": "快表用于加速页表访问和地址转换，在IBM系统中又取名为TLB(Translation Look aside Buffer)。",
                        },
                        {
                            "course_id": "course-os",
                            "heading_path_text": "第六章 输入输出系统 > 6.3 中断机构和中断处理程序",
                            "text": "设备驱动程序可以通过中断或轮询方式处理 I/O 设备请求。",
                        },
                        {
                            "course_id": "course-db",
                            "heading_path_text": "数据库系统 > B+树索引",
                            "text": "B+树用于数据库索引。",
                        },
                    ],
                    ensure_ascii=False,
                ),
                encoding="utf-8",
            )

            result = extract_course_profile_hints(
                course_id="course-os",
                section_docs_paths=[section_docs],
                max_hints=8,
            )

        rendered = "\n".join(f"{item.heading}：{'、'.join(item.keywords)}" for item in result.items)
        self.assertIn("地址变换机构", rendered)
        self.assertIn("快表", rendered)
        self.assertIn("TLB", rendered)
        self.assertIn("中断机构和中断处理程序", rendered)
        self.assertIn("设备驱动程序", rendered)
        self.assertIn("轮询", rendered)
        self.assertNotIn("B+树", rendered)

    def test_extracts_hints_from_text_units_parquet_under_build_run_output(self):
        with tempfile.TemporaryDirectory() as tmp:
            data_dir = Path(tmp) / "index" / "output"
            data_dir.mkdir(parents=True)
            pd.DataFrame(
                [
                    {
                        "human_readable_id": "188",
                        "text": "\n".join(
                            [
                                "course_id: course-os.",
                                "heading_path_text: 第四章 存储器管理 > 4.5 分页存储管理方式 > 4.5.2 地址变换机构.",
                                "快表（TLB）是一种联想寄存器，用于提高地址转换速度。",
                            ]
                        ),
                    },
                    {
                        "human_readable_id": "249",
                        "text": "\n".join(
                            [
                                "course_id: course-os.",
                                "heading_path_text: 第六章 输入输出系统 > 6.3 中断机构和中断处理程序.",
                                "I/O 设备控制常涉及设备驱动程序、中断和轮询，早期也会使用轮询的可编程 I/O 方式。",
                            ]
                        ),
                    },
                ]
            ).to_parquet(data_dir / "text_units.parquet")

            result = extract_course_profile_hints(
                course_id="course-os",
                data_dirs=[data_dir],
                max_hints=4,
            )

        self.assertEqual(result.source_counts["text_units"], 2)
        keywords = {keyword for item in result.items for keyword in item.keywords}
        self.assertTrue({"TLB", "快表", "联想寄存器"}.issubset(keywords))
        self.assertTrue({"I/O", "设备驱动程序", "中断", "轮询"}.issubset(keywords))

    def test_specific_technical_sections_rank_ahead_of_generic_preface(self):
        with tempfile.TemporaryDirectory() as tmp:
            section_docs = Path(tmp) / "section_docs.json"
            section_docs.write_text(
                json.dumps(
                    [
                        {
                            "course_id": "course-os",
                            "heading_path_text": "前言",
                            "text": "本教材介绍操作系统、计算机操作系统、资源管理系统、应用程序接口、"
                            "第四章和第五章对连续和离散存储器管理方式做概述。",
                        },
                        {
                            "course_id": "course-os",
                            "heading_path_text": "第四章 存储器管理 > 4.5 分页存储管理方式 > 4.5.2 地址变换机构",
                            "text": "快表（TLB）是一种联想寄存器，用于提高地址转换速度。",
                        },
                        {
                            "course_id": "course-os",
                            "heading_path_text": "第六章 输入输出系统 > 6.3 中断机构和中断处理程序",
                            "text": "I/O 设备控制常涉及设备驱动程序、中断和轮询。",
                        },
                    ],
                    ensure_ascii=False,
                ),
                encoding="utf-8",
            )

            result = extract_course_profile_hints(
                course_id="course-os",
                section_docs_paths=[section_docs],
                seed_keywords=["操作系统"],
                max_hints=2,
            )

        rendered = "\n".join(f"{item.heading}：{'、'.join(item.keywords)}" for item in result.items)
        self.assertNotIn("前言", rendered)
        self.assertIn("TLB", rendered)
        self.assertIn("I/O", rendered)

    def test_acronym_dense_references_do_not_outrank_technical_sections(self):
        with tempfile.TemporaryDirectory() as tmp:
            section_docs = Path(tmp) / "section_docs.json"
            section_docs.write_text(
                json.dumps(
                    [
                        {
                            "course_id": "course-os",
                            "heading_path_text": "第十二章 保护和安全 > 参考文献",
                            "text": "SG PH UNIX PYR IEEE XINU PC OS ATM AS PHI ABC DEF GHI。",
                        },
                        {
                            "course_id": "course-os",
                            "heading_path_text": "第四章 存储器管理 > 4.5 分页存储管理方式 > 4.5.2 地址变换机构",
                            "text": "快表用于加速页表访问和地址转换，在IBM系统中又取名为TLB(Translation Look aside Buffer)。",
                        },
                    ],
                    ensure_ascii=False,
                ),
                encoding="utf-8",
            )

            result = extract_course_profile_hints(
                course_id="course-os",
                section_docs_paths=[section_docs],
                max_hints=1,
            )

        rendered = "\n".join(f"{item.heading}：{'、'.join(item.keywords)}" for item in result.items)
        self.assertNotIn("参考文献", rendered)
        self.assertIn("TLB", rendered)

    def test_ranking_diversifies_sections_under_same_chapter(self):
        with tempfile.TemporaryDirectory() as tmp:
            section_docs = Path(tmp) / "section_docs.json"
            section_docs.write_text(
                json.dumps(
                    [
                        {
                            "course_id": "course-os",
                            "heading_path_text": "第六章 输入输出系统 > 6.4 设备驱动程序 > 6.4.1 设备驱动程序概述",
                            "text": "CPU I/O DMA ROM ABC DEF 设备驱动程序负责设备控制。",
                        },
                        {
                            "course_id": "course-os",
                            "heading_path_text": "第六章 输入输出系统 > 6.4 设备驱动程序 > 6.4.3 对I/O设备的控制方式",
                            "text": "CPU I/O DMA CR DR DC ABC DEF 对I/O设备的控制方式。",
                        },
                        {
                            "course_id": "course-os",
                            "heading_path_text": "第六章 输入输出系统 > 6.4 设备驱动程序 > 6.4.4 DMA方式",
                            "text": "CPU I/O DMA CR DR DC MAR ABC DEF DMA方式。",
                        },
                        {
                            "course_id": "course-os",
                            "heading_path_text": "第六章 输入输出系统 > 6.3 中断机构和中断处理程序 > 6.3.1 中断简介",
                            "text": "中断机构和中断处理程序负责处理中断请求。",
                        },
                    ],
                    ensure_ascii=False,
                ),
                encoding="utf-8",
            )

            result = extract_course_profile_hints(
                course_id="course-os",
                section_docs_paths=[section_docs],
                max_hints=2,
            )

        headings = [item.heading for item in result.items]
        self.assertTrue(any("6.4 设备驱动程序" in heading for heading in headings))
        self.assertTrue(any("6.3 中断机构" in heading for heading in headings))

    def test_merges_keywords_from_multiple_chunks_with_same_heading(self):
        with tempfile.TemporaryDirectory() as tmp:
            section_docs = Path(tmp) / "section_docs.json"
            heading = "第六章 输入输出系统 > 6.4 设备驱动程序 > 6.4.3 对I/O设备的控制方式"
            section_docs.write_text(
                json.dumps(
                    [
                        {
                            "course_id": "course-os",
                            "heading_path_text": heading,
                            "text": "CPU I/O DMA CR DR DC MAR ABC DEF 对I/O设备的控制方式，设备驱动程序。",
                        },
                        {
                            "course_id": "course-os",
                            "heading_path_text": heading,
                            "text": "早期是使用轮询的可编程 I/O 方式，后来发展为使用中断的可编程 I/O 方式。",
                        },
                        {
                            "course_id": "course-os",
                            "heading_path_text": heading,
                            "text": "随后又发展出通道程序、命令状态寄存器、设备控制器、DMA 控制和多个 I/O 通道控制术语。",
                        },
                    ],
                    ensure_ascii=False,
                ),
                encoding="utf-8",
            )

            result = extract_course_profile_hints(
                course_id="course-os",
                section_docs_paths=[section_docs],
                max_hints=1,
            )

        keywords = {keyword for item in result.items for keyword in item.keywords}
        self.assertIn("设备驱动程序", keywords)
        self.assertIn("轮询", keywords)
        ordered_keywords = result.items[0].keywords
        self.assertLess(ordered_keywords.index("轮询"), 12)

    def test_preserves_quoted_terms_and_acronym_expansions_in_dense_sections(self):
        with tempfile.TemporaryDirectory() as tmp:
            section_docs = Path(tmp) / "section_docs.json"
            heading = "第四章 存储器管理 > 4.5 分页存储管理方式 > 4.5.2 地址变换机构"
            section_docs.write_text(
                json.dumps(
                    [
                        {
                            "course_id": "course-os",
                            "heading_path_text": heading,
                            "text": (
                                "地址变换机构自动地将页号送入高速缓冲寄存器，基本任务是实现从逻辑地址到物理地址的转换。"
                                "此处还会出现页表寄存器、物理地址寄存器、页面映射表、页号转换和高速缓存等多个术语。"
                            ),
                        },
                        {
                            "course_id": "course-os",
                            "heading_path_text": heading,
                            "text": (
                                "这种具有并行查寻能力的特殊高速缓冲寄存器又称为“联想寄存器”，或称为“快表”，"
                                "在IBM系统中又取名为TLB(Translation Look aside Buffer)，用以存放当前访问的页表项。"
                            ),
                        }
                    ],
                    ensure_ascii=False,
                ),
                encoding="utf-8",
            )

            result = extract_course_profile_hints(
                course_id="course-os",
                section_docs_paths=[section_docs],
                max_hints=1,
            )

        keywords = result.items[0].keywords
        self.assertIn("快表", keywords)
        self.assertIn("TLB", keywords)
        self.assertLess(keywords.index("TLB"), 8)

    def test_keeps_high_value_coverage_hint_when_dense_sections_score_higher(self):
        with tempfile.TemporaryDirectory() as tmp:
            section_docs = Path(tmp) / "section_docs.json"
            noisy_rows = [
                {
                    "course_id": "course-os",
                    "heading_path_text": f"第{i}章 示例章节 > {i}.1 密集术语 > {i}.1.1 长文本",
                    "text": " ".join(
                        f"长术语{j}管理 长术语{j}系统 长术语{j}结构 ABC{j}"
                        for j in range(20)
                    ),
                }
                for i in range(1, 4)
            ]
            target = {
                "course_id": "course-os",
                "heading_path_text": "第六章 输入输出系统 > 6.4 设备驱动程序 > 6.4.3 对I/O设备的控制方式",
                "text": "设备驱动程序通过 I/O、中断和轮询控制设备请求。",
            }
            section_docs.write_text(
                json.dumps([*noisy_rows, target], ensure_ascii=False),
                encoding="utf-8",
            )

            result = extract_course_profile_hints(
                course_id="course-os",
                section_docs_paths=[section_docs],
                max_hints=2,
            )

        rendered = "\n".join(item.heading for item in result.items)
        self.assertIn("对I/O设备的控制方式", rendered)


if __name__ == "__main__":
    unittest.main()
