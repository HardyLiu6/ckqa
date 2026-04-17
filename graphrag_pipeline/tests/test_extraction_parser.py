#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
抽取解析器测试
==============
聚焦 JSON 容错与截断识别。
"""

from __future__ import annotations

import sys
import unittest
from pathlib import Path


_PROJECT_ROOT = Path(__file__).resolve().parent.parent
_SCRIPTS_DIR = _PROJECT_ROOT / "scripts"
if str(_SCRIPTS_DIR) not in sys.path:
    sys.path.insert(0, str(_SCRIPTS_DIR))

from extraction_parser import parse_extraction_output
from extraction_schema import SchemaCatalog, SchemaTypeInfo


def _build_schema_catalog() -> SchemaCatalog:
    return SchemaCatalog(
        schema_version="v1",
        entity_types=[
            SchemaTypeInfo(name="Concept", label_zh="概念", description="课程概念"),
            SchemaTypeInfo(name="Chapter", label_zh="章节", description="课程章节"),
        ],
        relation_types=[
            SchemaTypeInfo(name="contains", label_zh="包含", description="结构包含"),
            SchemaTypeInfo(name="related_to", label_zh="相关", description="保底关系"),
        ],
        entity_schema_path="/tmp/entity_types.json",
        relation_schema_path="/tmp/relation_types.json",
    )


class TestExtractionParser(unittest.TestCase):
    def test_parse_incomplete_json_reports_truncation(self):
        result = parse_extraction_output(
            '{"entities":[{"id":"e1","title":"进程","type":"Concept"}],"relationships":[{"source":"进程"',
            sample_id="pts-001",
            candidate="default",
            schema_catalog=_build_schema_catalog(),
        )

        self.assertEqual(result.status, "parse_error")
        self.assertEqual(result.parser_error_code, "truncated_json")
        self.assertIn("截断", result.error or "")


if __name__ == "__main__":
    unittest.main()
