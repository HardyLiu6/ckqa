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

    def test_parse_json_repairs_latex_style_invalid_backslash_escape(self):
        result = parse_extraction_output(
            r"""
{
  "entities": [
    {
      "id": "e1",
      "title": "工作集",
      "type": "Concept",
      "description": "工作集定义使用窗口尺寸变量",
      "evidence": "某段时间间隔 \Delta 里访问页面的集合"
    }
  ],
  "relationships": []
}
""",
            sample_id="pts-002",
            candidate="schema_aware",
            schema_catalog=_build_schema_catalog(),
        )

        self.assertEqual(result.status, "success")
        self.assertEqual(result.entities[0].evidence, r"某段时间间隔 \Delta 里访问页面的集合")


if __name__ == "__main__":
    unittest.main()
