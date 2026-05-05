#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
fetch_from_minio 字段保留测试
============================
验证 GraphRAG 下游同步脚本不会丢失 pdf_ingest 导出的标准字段。
"""

import json
import sys
import types
import unittest
from tempfile import TemporaryDirectory
from pathlib import Path


_PROJECT_ROOT = Path(__file__).resolve().parent.parent
_MODULE_DIR = _PROJECT_ROOT / "utils"
if str(_MODULE_DIR) not in sys.path:
    sys.path.insert(0, str(_MODULE_DIR))


def _install_optional_dependency_stubs() -> None:
    if "dotenv" not in sys.modules:
        dotenv_module = types.ModuleType("dotenv")

        def _dummy_load_dotenv(*args, **kwargs):
            return False

        dotenv_module.load_dotenv = _dummy_load_dotenv
        sys.modules["dotenv"] = dotenv_module

    if "minio" not in sys.modules:
        minio_module = types.ModuleType("minio")
        minio_error_module = types.ModuleType("minio.error")

        class _DummyMinio:
            def __init__(self, *args, **kwargs):
                self.args = args
                self.kwargs = kwargs

        class _DummyS3Error(Exception):
            code = "NoSuchKey"

        minio_module.Minio = _DummyMinio
        minio_error_module.S3Error = _DummyS3Error
        sys.modules["minio"] = minio_module
        sys.modules["minio.error"] = minio_error_module


_install_optional_dependency_stubs()

from fetch_from_minio import fetch_and_prepare, flatten_record, _extract_primary_text


class TestFetchFromMinioFlatten(unittest.TestCase):
    """标准字段应在同步到 GraphRAG input 时保留。"""

    def test_output_file_overrides_local_filename(self):
        source = [{"id": "doc-1", "text": "hello", "title": "t"}]

        class FakeClient:
            def fget_object(self, bucket_name, object_name, file_path):
                Path(file_path).write_text(json.dumps(source), encoding="utf-8")

        with TemporaryDirectory() as tmp_dir:
            tmp_path = Path(tmp_dir)
            result = fetch_and_prepare(
                course_id="os",
                input_dir=tmp_path,
                clean=False,
                json_filename="section_docs.json",
                material_id=3,
                output_filename="material_3.section_docs.json",
                client=FakeClient(),
                bucket="course-artifacts",
            )

            self.assertTrue(result["output_file"].endswith("material_3.section_docs.json"))
            self.assertTrue((tmp_path / "material_3.section_docs.json").exists())
            self.assertFalse((tmp_path / "section_docs.json").exists())

    def test_flatten_record_preserves_top_level_standard_fields(self):
        record = {
            "title": "os-2.1 前趋图和程序执行",
            "text": "2.1 前趋图和程序执行\n\n正文内容",
            "id": "os:book:ch2__sec2.1",
            "course_id": "os",
            "source_file": "book.pdf",
            "document_type": "textbook",
            "chapter": "第二章 进程的描述与控制",
            "section": "2.1 前趋图和程序执行",
            "subsection": None,
            "heading_level": 2,
            "heading_path": ["第二章 进程的描述与控制", "2.1 前趋图和程序执行"],
            "page_start": 32,
            "page_end": 35,
            "metadata": {
                "doc_unit": "section",
                "section_level": 2,
                "has_table": True,
                "table_count": 1,
            },
        }

        flat = flatten_record(record)

        self.assertEqual(flat["id"], "os:book:ch2__sec2.1")
        self.assertEqual(flat["document_type"], "textbook")
        self.assertEqual(flat["chapter"], "第二章 进程的描述与控制")
        self.assertEqual(flat["section"], "2.1 前趋图和程序执行")
        self.assertEqual(flat["heading_level"], 2)
        self.assertEqual(flat["heading_path"], ["第二章 进程的描述与控制", "2.1 前趋图和程序执行"])
        self.assertEqual(flat["heading_path_text"], "第二章 进程的描述与控制 > 2.1 前趋图和程序执行")
        self.assertEqual(flat["doc_unit"], "section")
        self.assertEqual(flat["section_level"], 2)
        self.assertTrue(flat["has_table"])
        self.assertEqual(flat["table_count"], 1)
        self.assertIn("metadata", flat)

    def test_flatten_record_can_promote_legacy_nested_metadata(self):
        record = {
            "title": "os-前言",
            "text": "前言正文",
            "metadata": {
                "course_id": "os",
                "source_file": "book.pdf",
                "document_type": "textbook",
                "chapter": "前言",
                "heading_level": 1,
                "heading_path": ["前言"],
                "page_start": 1,
                "page_end": 2,
                "chunk_total": 3,
            },
        }

        flat = flatten_record(record)

        self.assertEqual(flat["course_id"], "os")
        self.assertEqual(flat["source_file"], "book.pdf")
        self.assertEqual(flat["document_type"], "textbook")
        self.assertEqual(flat["chapter"], "前言")
        self.assertEqual(flat["heading_level"], 1)
        self.assertEqual(flat["heading_path"], ["前言"])
        self.assertEqual(flat["heading_path_text"], "前言")
        self.assertEqual(flat["page_start"], 1)
        self.assertEqual(flat["page_end"], 2)
        self.assertEqual(flat["chunk_total"], 3)

    def test_flatten_record_keeps_normalized_content_and_derives_title(self):
        record = {
            "id": "os:book:sec1",
            "source_file": "book.pdf",
            "document_type": "textbook",
            "course_id": "os",
            "chapter": "第一章 绪论",
            "section": None,
            "subsection": None,
            "heading_level": 1,
            "heading_path": ["第一章 绪论"],
            "content": "这是标准文档正文。",
            "page_start": 1,
            "page_end": 2,
            "metadata": {"doc_unit": "section"},
        }

        flat = flatten_record(record)

        self.assertEqual(flat["content"], "这是标准文档正文。")
        self.assertEqual(flat["heading_path_text"], "第一章 绪论")
        self.assertEqual(flat["title"], "第一章 绪论")
        self.assertNotIn("text", flat)

    def test_extract_primary_text_supports_text_and_content(self):
        self.assertEqual(
            _extract_primary_text({"text": "正文"}),
            "正文",
        )
        self.assertEqual(
            _extract_primary_text({"content": "标准正文"}),
            "标准正文",
        )
        self.assertEqual(
            _extract_primary_text({"text": "   ", "content": "标准正文"}),
            "标准正文",
        )


class TestGraphRAGSettingsContract(unittest.TestCase):
    """settings.yaml 应声明新的下游 metadata 契约。"""

    def test_settings_yaml_mentions_standard_metadata_fields(self):
        text = (_PROJECT_ROOT / "settings.yaml").read_text(encoding="utf-8")

        self.assertIn("- document_type", text)
        self.assertIn("- chapter", text)
        self.assertIn("- section", text)
        self.assertIn("- subsection", text)
        self.assertIn("- heading_level", text)
        self.assertIn("- heading_path_text", text)
        self.assertIn("- doc_unit", text)


if __name__ == "__main__":
    unittest.main()
