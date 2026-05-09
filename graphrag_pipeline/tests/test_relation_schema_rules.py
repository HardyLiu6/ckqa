from __future__ import annotations

import json
import unittest
from pathlib import Path


PROJECT_ROOT = Path(__file__).resolve().parent.parent


class TestRelationSchemaRules(unittest.TestCase):
    def test_material_7_schema_keeps_hard_relation_boundaries(self):
        payload = json.loads(
            (PROJECT_ROOT / "config" / "schema" / "relation_types.json").read_text(
                encoding="utf-8"
            )
        )
        relation_types = payload["relation_types"]

        contains = relation_types["contains"]
        self.assertIn("KnowledgePoint", contains["source_types"])
        self.assertIn("Concept", contains["source_types"])
        self.assertIn("AlgorithmOrMethod", contains["source_types"])
        self.assertEqual(
            contains["derivation_constraints"]["derive_inverse_only_when_source_types"],
            ["Course", "Chapter", "Section"],
        )

        self.assertEqual(
            relation_types["belongs_to"]["target_types"],
            ["Course", "Chapter", "Section"],
        )
        self.assertEqual(
            relation_types["appears_in"]["target_types"],
            ["Course", "Chapter", "Section", "Experiment", "Assignment"],
        )
        self.assertIn("Term", relation_types["defined_by"]["target_types"])
        self.assertNotIn("Concept", relation_types["defined_by"]["target_types"])
        defined_by_hint = relation_types["defined_by"]["extraction_hint"]
        self.assertIn("禁止 Concept->Concept", defined_by_hint)
        self.assertIn("禁止 Term->Concept", defined_by_hint)
        self.assertIn("符号、变量、参数或公式记号", defined_by_hint)
        self.assertIn(
            "Concept->Concept: 进程 defined_by 线程",
            relation_types["defined_by"]["negative_examples"],
        )
        self.assertIn(
            "Term->Concept: PCB defined_by 进程控制块",
            relation_types["defined_by"]["negative_examples"],
        )

        belongs_to_hint = relation_types["belongs_to"]["extraction_hint"]
        self.assertIn("目标只能是 Course/Chapter/Section", belongs_to_hint)
        self.assertIn("知识对象之间不要用 belongs_to", belongs_to_hint)
        self.assertIn(
            "Concept->Concept: 死锁 belongs_to 资源分配图",
            relation_types["belongs_to"]["negative_examples"],
        )

        appears_in_hint = relation_types["appears_in"]["extraction_hint"]
        self.assertIn("目标必须是 Course/Chapter/Section/Experiment/Assignment", appears_in_hint)
        self.assertIn("禁止反向 Section appears_in Concept", appears_in_hint)
        self.assertIn(
            "Section->Concept: 第三章 存储器管理 appears_in TLB",
            relation_types["appears_in"]["negative_examples"],
        )

        related_to_hint = relation_types["related_to"]["extraction_hint"]
        self.assertIn("必须先补齐 target 实体", related_to_hint)
        self.assertIn("不能用作缺失关系占位", related_to_hint)
        self.assertIn(
            "Concept->missing: 磁盘高速缓存 related_to 文件访问速度",
            relation_types["related_to"]["negative_examples"],
        )

        implemented_by_hint = relation_types["implemented_by"]["extraction_hint"]
        self.assertIn("目标只能是 AlgorithmOrMethod 或 ToolOrPlatform", implemented_by_hint)
        self.assertIn(
            "AlgorithmOrMethod->Concept: 高响应比优先调度算法 implemented_by 动态优先级",
            relation_types["implemented_by"]["negative_examples"],
        )

    def test_extraction_rules_document_defined_by_alias_and_contains_boundaries(self):
        text = (PROJECT_ROOT / "config" / "schema" / "extraction_rules.md").read_text(
            encoding="utf-8"
        )
        self.assertIn("知识对象之间的 `contains`", text)
        self.assertIn("不派生反向 `belongs_to`", text)
        self.assertIn("PCB defined_by Process Control Block", text)
        self.assertIn("alias", text)
        self.assertIn("端点完整性", text)
        self.assertIn("所有关系的 `source` 和 `target` 必须能在 `entities` 中找到", text)
        self.assertIn("不能输出 `<missing>`", text)
        self.assertIn("Concept->Concept", text)
        self.assertIn("Term->Concept", text)
        self.assertIn("Section appears_in Concept", text)
        self.assertIn("不能用 `related_to` 代替缺失端点", text)
        self.assertIn("`implemented_by` 的目标必须是可执行方法或工具平台", text)


if __name__ == "__main__":
    unittest.main()
