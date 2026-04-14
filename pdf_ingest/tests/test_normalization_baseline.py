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
from graphrag_exporter import ExportOptions, GraphRAGDocument, GraphRAGExporter
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
        self.assertGreaterEqual(len(cleaned), 7)
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


class TestFutureNormalizationContracts(unittest.TestCase):
    """未来目标契约，先用 expectedFailure 固化。"""

    @unittest.expectedFailure
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

    @unittest.expectedFailure
    def test_graphrag_projection_should_eventually_include_standard_fields(self):
        normalized_payload = _load_json_fixture("normalized_doc_v1.json")
        doc = GraphRAGDocument(
            title="os-2.1 前趋图和程序执行",
            text=normalized_payload["content"],
            metadata=normalized_payload,
        )

        payload = doc.to_graphrag_dict()

        self.assertIn("document_type", payload)
        self.assertIn("chapter", payload)
        self.assertIn("section", payload)
        self.assertIn("subsection", payload)
        self.assertIn("metadata", payload)
