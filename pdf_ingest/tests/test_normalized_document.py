#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
标准课程文档模型测试
====================
阶段 1: 冻结标准 schema。
阶段 2: 用 fixture 固化字段契约与页码定义。
"""

import json
import sys
import unittest
from pathlib import Path

# 确保 scripts/pdf_processor 在 sys.path 中
_PROJECT_ROOT = Path(__file__).resolve().parent.parent
_MODULE_DIR = _PROJECT_ROOT / "scripts" / "pdf_processor"
_FIXTURE_DIR = Path(__file__).resolve().parent / "fixtures" / "normalization"
if str(_MODULE_DIR) not in sys.path:
    sys.path.insert(0, str(_MODULE_DIR))

from normalized_document import (
    DOCUMENT_SCHEMA_VERSION,
    DocumentType,
    NormalizedDocument,
    validate_normalized_document_dict,
)


def _load_fixture(name: str) -> dict:
    path = _FIXTURE_DIR / name
    return json.loads(path.read_text(encoding="utf-8"))


class TestNormalizedDocumentContract(unittest.TestCase):
    """标准课程文档字段契约测试。"""

    def test_fixture_document_passes_validation(self):
        payload = _load_fixture("normalized_doc_v1.json")

        errors = validate_normalized_document_dict(payload)

        self.assertEqual(errors, [])

    def test_dataclass_roundtrip_keeps_target_fields(self):
        doc = NormalizedDocument(
            id="os:book.pdf:ch1-sec1.1",
            source_file="book.pdf",
            document_type=DocumentType.TEXTBOOK,
            course_id="os",
            chapter="第一章 操作系统引论",
            section="1.1 操作系统的目标和作用",
            subsection=None,
            heading_level=2,
            heading_path=[
                "第一章 操作系统引论",
                "1.1 操作系统的目标和作用",
            ],
            content="操作系统用于管理计算机系统中的硬件与软件资源。",
            page_start=8,
            page_end=9,
            metadata={
                "schema_version": DOCUMENT_SCHEMA_VERSION,
                "source_page_indices": [7, 8],
                "has_table": False,
                "has_equation": False,
                "has_image": False,
                "table_count": 0,
                "equation_count": 0,
                "image_count": 0,
            },
        )

        payload = doc.to_dict()

        self.assertEqual(payload["document_type"], "textbook")
        self.assertEqual(payload["heading_level"], 2)
        self.assertEqual(payload["heading_path"][1], "1.1 操作系统的目标和作用")
        self.assertEqual(validate_normalized_document_dict(payload), [])

    def test_from_dict_accepts_valid_fixture(self):
        payload = _load_fixture("normalized_doc_v1.json")

        doc = NormalizedDocument.from_dict(payload)

        self.assertEqual(doc.document_type, DocumentType.TEXTBOOK)
        self.assertEqual(doc.page_start, 3)
        self.assertEqual(doc.heading_path[-1], "2.1 前趋图和程序执行")

    def test_invalid_document_type_is_rejected(self):
        payload = _load_fixture("normalized_doc_v1.json")
        payload["document_type"] = "paper"

        errors = validate_normalized_document_dict(payload)

        self.assertTrue(any("document_type" in error for error in errors))

    def test_heading_level_cannot_be_smaller_than_heading_path_length(self):
        payload = _load_fixture("normalized_doc_v1.json")
        payload["heading_level"] = 1

        errors = validate_normalized_document_dict(payload)

        self.assertTrue(any("heading_level" in error for error in errors))

    def test_missing_chapter_can_still_use_real_section_level(self):
        payload = {
            "id": "os:book:sec-1.1",
            "source_file": "book.pdf",
            "document_type": "textbook",
            "course_id": "os",
            "chapter": None,
            "section": "1.1 操作系统的目标和作用",
            "subsection": None,
            "heading_level": 2,
            "heading_path": ["1.1 操作系统的目标和作用"],
            "content": "正文",
            "page_start": 9,
            "page_end": 9,
            "metadata": {
                "doc_unit": "section",
                "source_page_indices": [8],
            },
        }

        errors = validate_normalized_document_dict(payload)

        self.assertEqual(errors, [])

    def test_page_range_must_use_positive_one_based_pages(self):
        payload = _load_fixture("normalized_doc_v1.json")
        payload["page_start"] = 0

        errors = validate_normalized_document_dict(payload)

        self.assertTrue(any("page_start" in error for error in errors))
