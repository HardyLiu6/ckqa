#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
规范化基线样例测试
==================
阶段 2 的目标是先固化问题样例与目标契约，而不是立即修改导出逻辑。

本文件包含两类测试：
1. 通过的基线测试：确保样例能稳定复现当前链路。
2. expectedFailure 契约测试：记录未来应达成、当前尚未满足的行为。
"""

import json
import sys
import types
import unittest
from pathlib import Path

# 确保 scripts/pdf_processor 在 sys.path 中
_PROJECT_ROOT = Path(__file__).resolve().parent.parent
_MODULE_DIR = _PROJECT_ROOT / "scripts" / "pdf_processor"
_FIXTURE_DIR = Path(__file__).resolve().parent / "fixtures" / "normalization"
if str(_MODULE_DIR) not in sys.path:
    sys.path.insert(0, str(_MODULE_DIR))


def _install_optional_dependency_stubs() -> None:
    """为仅做聚合测试的场景注入最小依赖 stub。"""
    if "pymysql" not in sys.modules:
        pymysql_module = types.ModuleType("pymysql")
        cursors_module = types.ModuleType("pymysql.cursors")
        cursors_module.DictCursor = object
        pymysql_module.cursors = cursors_module
        sys.modules["pymysql"] = pymysql_module
        sys.modules["pymysql.cursors"] = cursors_module

    if "dbutils" not in sys.modules:
        dbutils_module = types.ModuleType("dbutils")
        pooled_db_module = types.ModuleType("dbutils.pooled_db")

        class _DummyPooledDB:
            def __init__(self, *args, **kwargs):
                self.args = args
                self.kwargs = kwargs

        pooled_db_module.PooledDB = _DummyPooledDB
        dbutils_module.pooled_db = pooled_db_module
        sys.modules["dbutils"] = dbutils_module
        sys.modules["dbutils.pooled_db"] = pooled_db_module

    if "minio" not in sys.modules:
        minio_module = types.ModuleType("minio")
        minio_error_module = types.ModuleType("minio.error")

        class _DummyMinio:
            def __init__(self, *args, **kwargs):
                self.args = args
                self.kwargs = kwargs

        class _DummyS3Error(Exception):
            pass

        minio_module.Minio = _DummyMinio
        minio_error_module.S3Error = _DummyS3Error
        sys.modules["minio"] = minio_module
        sys.modules["minio.error"] = minio_error_module


_install_optional_dependency_stubs()

from block_model import parse_content_list
from graphrag_exporter import ExportOptions, GraphRAGExporter
from normalized_document import NormalizedDocument, validate_normalized_document_dict
from text_cleaner import clean_blocks


def _load_json_fixture(name: str):
    path = _FIXTURE_DIR / name
    return json.loads(path.read_text(encoding="utf-8"))


def _build_trace() -> dict:
    return {
        "content_list_file_name": "content_list_with_toc.json",
        "content_list_minio_key": "tests/fixtures/normalization/content_list_with_toc.json",
    }


class TestNormalizationBaselineFixture(unittest.TestCase):
    """基线样例应能稳定走通当前导出链路。"""

    def test_fixture_can_be_parsed_and_cleaned(self):
        content_list = _load_json_fixture("content_list_with_toc.json")

        blocks = parse_content_list(
            content_list,
            course_id="os",
            source_file="计算机操作系统.pdf",
            semantic_table=True,
        )
        cleaned = clean_blocks(blocks)

        self.assertEqual(len(blocks), 10)
        self.assertEqual(len(cleaned), 7)
        self.assertTrue(all(block.page != 0 for block in cleaned))
        self.assertTrue(all("目录" not in block.text for block in cleaned))
        self.assertTrue(any(block.text.startswith("第二章") for block in cleaned))
        self.assertTrue(any("前趋图" in block.text for block in cleaned))

    def test_fixture_can_drive_section_aggregation(self):
        content_list = _load_json_fixture("content_list_with_toc.json")
        blocks = parse_content_list(
            content_list,
            course_id="os",
            source_file="计算机操作系统.pdf",
            semantic_table=True,
        )
        cleaned = clean_blocks(blocks)

        exporter = GraphRAGExporter(db=None, storage=None, config=None)
        docs = exporter._aggregate_section(
            cleaned,
            course_id="os",
            file_id=3,
            source_file="计算机操作系统.pdf",
            md_text=None,
            cl_trace=_build_trace(),
            options=ExportOptions(),
        )

        self.assertGreaterEqual(len(docs), 2)
        self.assertTrue(all(doc.title.startswith("os-") for doc in docs))
        self.assertTrue(any("表2-1" in doc.text for doc in docs))
        self.assertTrue(all("[TABLE]" not in doc.text for doc in docs))
        self.assertTrue(all("[IMAGE]" not in doc.text for doc in docs))
        self.assertTrue(any("公式：" in doc.text for doc in docs))

    def test_fixture_can_generate_normalized_section_docs(self):
        content_list = _load_json_fixture("content_list_with_toc.json")
        blocks = parse_content_list(
            content_list,
            course_id="os",
            source_file="计算机操作系统.pdf",
            semantic_table=True,
        )
        cleaned = clean_blocks(blocks)

        exporter = GraphRAGExporter(db=None, storage=None, config=None)
        docs = exporter._aggregate_normalized_section(
            cleaned,
            course_id="os",
            file_id=3,
            source_file="计算机操作系统.pdf",
            md_text=None,
            cl_trace=_build_trace(),
            options=ExportOptions(),
        )

        self.assertGreaterEqual(len(docs), 2)
        self.assertTrue(all(isinstance(doc, NormalizedDocument) for doc in docs))
        self.assertTrue(all(validate_normalized_document_dict(doc.to_dict()) == [] for doc in docs))
        self.assertTrue(any(doc.section == "2.1 前趋图和程序执行" for doc in docs))
        self.assertTrue(any("图2-1 前趋图示意" in doc.content for doc in docs))

    def test_missing_chapter_does_not_cascade_section_hierarchy(self):
        content_list = [
            {
                "type": "text",
                "text": "1.1 第一节",
                "text_level": 2,
                "page_idx": 0,
            },
            {
                "type": "text",
                "text": "第一节的正文内容足够长，可以作为有效语义文本。",
                "page_idx": 0,
            },
            {
                "type": "text",
                "text": "1.1.1 小节",
                "text_level": 3,
                "page_idx": 1,
            },
            {
                "type": "text",
                "text": "小节正文内容同样足够长，用于验证层级构建。",
                "page_idx": 1,
            },
            {
                "type": "text",
                "text": "1.2 第二节",
                "text_level": 2,
                "page_idx": 2,
            },
            {
                "type": "text",
                "text": "第二节正文内容足够长，且不应挂到 1.1 第一节 之下。",
                "page_idx": 2,
            },
        ]

        blocks = parse_content_list(
            content_list,
            course_id="os",
            source_file="讲义.pdf",
            semantic_table=True,
        )
        cleaned = clean_blocks(blocks)

        exporter = GraphRAGExporter(db=None, storage=None, config=None)
        docs = exporter._aggregate_normalized_section(
            cleaned,
            course_id="os",
            file_id=4,
            source_file="讲义.pdf",
            md_text=None,
            cl_trace=_build_trace(),
            options=ExportOptions(),
        )

        second_section = next(doc for doc in docs if doc.section == "1.2 第二节")
        self.assertIsNone(second_section.chapter)
        self.assertEqual(second_section.heading_path, ["1.2 第二节"])

    def test_front_matter_publication_page_is_filtered_but_preface_is_kept(self):
        content_list = [
            {
                "type": "text",
                "text": "图书在版编目(CIP)数据",
                "text_level": 1,
                "page_idx": 0,
            },
            {
                "type": "text",
                "text": "ISBN 978-7-1234-5678-9",
                "page_idx": 0,
            },
            {
                "type": "text",
                "text": "西安电子科技大学出版社",
                "page_idx": 0,
            },
            {
                "type": "text",
                "text": "版权所有 侵权必究",
                "page_idx": 0,
            },
            {
                "type": "text",
                "text": "前言",
                "text_level": 1,
                "page_idx": 1,
            },
            {
                "type": "text",
                "text": "本资料用于介绍课程结构、学习方法与后续章节安排，"
                        "这是一段足够长的有效导读内容，不应被误删。",
                "page_idx": 1,
            },
        ]

        blocks = parse_content_list(
            content_list,
            course_id="os",
            source_file="课程资料.pdf",
            semantic_table=True,
        )
        cleaned = clean_blocks(blocks)

        self.assertTrue(all(block.page != 0 for block in cleaned))
        self.assertTrue(any(block.text == "前言" for block in cleaned))
        self.assertTrue(any("课程结构" in block.text for block in cleaned))

    def test_unnumbered_titles_can_still_split_generic_documents(self):
        content_list = [
            {
                "type": "text",
                "text": "课程概述",
                "text_level": 1,
                "page_idx": 0,
            },
            {
                "type": "text",
                "text": "这里是课程概述部分的正文，用于说明文档整体目标与范围，长度足以保留。",
                "page_idx": 0,
            },
            {
                "type": "text",
                "text": "学习目标",
                "text_level": 1,
                "page_idx": 1,
            },
            {
                "type": "text",
                "text": "这里列出学习目标、知识结构和掌握要求，属于独立章节内容。",
                "page_idx": 1,
            },
            {
                "type": "text",
                "text": "考核方式",
                "text_level": 1,
                "page_idx": 2,
            },
            {
                "type": "text",
                "text": "这里介绍平时成绩、实验报告和期末考核方式，也应独立切分。",
                "page_idx": 2,
            },
        ]

        blocks = parse_content_list(
            content_list,
            course_id="course",
            source_file="课程说明.pdf",
            semantic_table=True,
        )
        cleaned = clean_blocks(blocks)

        exporter = GraphRAGExporter(db=None, storage=None, config=None)
        docs = exporter._aggregate_normalized_section(
            cleaned,
            course_id="course",
            file_id=5,
            source_file="课程说明.pdf",
            md_text=None,
            cl_trace=_build_trace(),
            options=ExportOptions(),
        )

        headings = [doc.heading_path[-1] for doc in docs]
        self.assertEqual(headings, ["课程概述", "学习目标", "考核方式"])

    def test_long_section_is_soft_split_without_explicit_max_chars(self):
        long_paragraph = (
            "这是一个用于测试软切分策略的长段落。"
            "它包含连续的完整句子，适合被按句子边界切分。"
        ) * 80
        content_list = [
            {
                "type": "text",
                "text": "课程总结",
                "text_level": 1,
                "page_idx": 0,
            },
            {
                "type": "text",
                "text": long_paragraph,
                "page_idx": 0,
            },
        ]

        blocks = parse_content_list(
            content_list,
            course_id="course",
            source_file="课程总结.pdf",
            semantic_table=True,
        )
        cleaned = clean_blocks(blocks)

        exporter = GraphRAGExporter(db=None, storage=None, config=None)
        docs = exporter._aggregate_normalized_section(
            cleaned,
            course_id="course",
            file_id=6,
            source_file="课程总结.pdf",
            md_text=None,
            cl_trace=_build_trace(),
            options=ExportOptions(),
        )

        self.assertGreater(len(docs), 1)
        self.assertTrue(all(doc.metadata.get("chunk_total", 1) == len(docs) for doc in docs))
        self.assertTrue(all(doc.metadata.get("chunk_strategy") == "soft" for doc in docs))
        self.assertTrue(all(len(doc.content) <= ExportOptions().soft_max_chars for doc in docs))


class TestFutureNormalizationContracts(unittest.TestCase):
    """未来目标契约，先用 expectedFailure 固化。"""

    def test_section_aggregation_should_filter_toc_titles_and_page_numbers(self):
        content_list = _load_json_fixture("content_list_with_toc.json")
        blocks = parse_content_list(
            content_list,
            course_id="os",
            source_file="计算机操作系统.pdf",
            semantic_table=True,
        )
        cleaned = clean_blocks(blocks)

        exporter = GraphRAGExporter(db=None, storage=None, config=None)
        docs = exporter._aggregate_section(
            cleaned,
            course_id="os",
            file_id=3,
            source_file="计算机操作系统.pdf",
            md_text=None,
            cl_trace=_build_trace(),
            options=ExportOptions(),
        )

        titles = [doc.title for doc in docs]
        self.assertEqual(
            titles,
            [
                "os-第二章 进程的描述与控制",
                "os-2.1 前趋图和程序执行",
            ],
        )

    def test_graphrag_projection_includes_standard_fields(self):
        normalized_payload = _load_json_fixture("normalized_doc_v1.json")
        normalized_doc = NormalizedDocument.from_dict(normalized_payload)
        exporter = GraphRAGExporter(db=None, storage=None, config=None)
        doc = exporter._project_normalized_document(normalized_doc)

        payload = doc.to_graphrag_dict()

        self.assertIn("document_type", payload)
        self.assertIn("chapter", payload)
        self.assertIn("section", payload)
        self.assertIn("subsection", payload)
        self.assertIn("metadata", payload)
        self.assertEqual(payload["document_type"], "textbook")
        self.assertEqual(payload["page_start"], 3)
