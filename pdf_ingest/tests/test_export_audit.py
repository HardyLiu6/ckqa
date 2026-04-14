#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
export_audit 验收脚本测试
=========================
"""

import json
import sys
import unittest
from pathlib import Path
from tempfile import TemporaryDirectory


_PROJECT_ROOT = Path(__file__).resolve().parent.parent
_MODULE_DIR = _PROJECT_ROOT / "scripts" / "pdf_processor"
if str(_MODULE_DIR) not in sys.path:
    sys.path.insert(0, str(_MODULE_DIR))

from export_audit import (
    audit_export_file,
    build_aggregate_report,
    discover_export_files,
    infer_document_format,
)


class TestExportAudit(unittest.TestCase):
    """验收脚本应能识别格式并报告典型问题。"""

    def test_infer_document_format(self):
        self.assertEqual(
            infer_document_format([{"content": "正文", "heading_path": ["第一章"]}]),
            "normalized",
        )
        self.assertEqual(
            infer_document_format([{"title": "os-第一章", "text": "正文"}]),
            "graphrag",
        )
        self.assertEqual(infer_document_format([]), "empty")

    def test_audit_export_file_for_normalized_docs(self):
        payload = [
            {
                "id": "os:book:ch1",
                "source_file": "book.pdf",
                "document_type": "textbook",
                "course_id": "os",
                "chapter": "第一章 绪论",
                "section": None,
                "subsection": None,
                "heading_level": 1,
                "heading_path": ["第一章 绪论"],
                "content": "这是有效正文内容。" * 20,
                "page_start": 1,
                "page_end": 2,
                "metadata": {"doc_unit": "section"},
            },
            {
                "id": "os:book:bad",
                "source_file": "book.pdf",
                "document_type": "textbook",
                "course_id": "os",
                "chapter": None,
                "section": None,
                "subsection": None,
                "heading_level": 1,
                "heading_path": ["第二章 进程管理 32"],
                "content": "[IMAGE] ref=images/foo.png page=0",
                "page_start": 0,
                "page_end": 0,
                "metadata": {"doc_unit": "section"},
            },
        ]

        with TemporaryDirectory() as tmp_dir:
            path = Path(tmp_dir) / "normalized_docs.json"
            path.write_text(json.dumps(payload, ensure_ascii=False), encoding="utf-8")
            report = audit_export_file(path, sample_size=2)

        self.assertEqual(report["format"], "normalized")
        self.assertEqual(report["documents_count"], 2)
        self.assertGreaterEqual(report["issue_counts"]["image_placeholder"], 1)
        self.assertGreaterEqual(report["issue_counts"]["invalid_page_range"], 1)
        self.assertGreaterEqual(report["issue_counts"]["noisy_heading"], 1)
        self.assertGreaterEqual(report["issue_counts"]["normalized_schema_error"], 1)

    def test_discover_and_aggregate_reports(self):
        graphrag_payload = [
            {
                "title": "os-第二章 进程管理 32",
                "text": "[TABLE] ref=foo",
                "course_id": "os",
                "page_start": 0,
                "page_end": 0,
            }
        ]

        with TemporaryDirectory() as tmp_dir:
            root = Path(tmp_dir)
            nested = root / "courseA"
            nested.mkdir()
            file_path = nested / "section_docs.json"
            file_path.write_text(json.dumps(graphrag_payload, ensure_ascii=False), encoding="utf-8")

            files = discover_export_files([root], filenames=["section_docs.json"])
            self.assertEqual(files, [file_path.resolve()])

            aggregate = build_aggregate_report([audit_export_file(file_path)])

        self.assertEqual(aggregate["files_count"], 1)
        self.assertEqual(aggregate["documents_count"], 1)
        self.assertEqual(aggregate["format_distribution"]["graphrag"], 1)
        self.assertGreaterEqual(aggregate["issue_counts"]["table_placeholder"], 1)


if __name__ == "__main__":
    unittest.main()
