from __future__ import annotations

import json
import unittest
from pathlib import Path


PROJECT_ROOT = Path(__file__).resolve().parent.parent


class TestRelationSchemaRules(unittest.TestCase):
    def test_entity_schema_global_attributes_and_examples(self):
        payload = json.loads(
            (PROJECT_ROOT / "config" / "schema" / "entity_types.json").read_text(
                encoding="utf-8"
            )
        )

        global_attributes = payload["global_entity_attributes"]
        self.assertEqual(global_attributes["alias"]["type"], "List[str]")
        self.assertFalse(global_attributes["alias"]["required"])
        self.assertIn("不进入关系抽取", global_attributes["alias"]["description"])
        self.assertEqual(global_attributes["definition_text"]["type"], "str")
        self.assertFalse(global_attributes["definition_text"]["required"])
        self.assertIn(
            "不足以提升为 FormulaOrDefinition",
            global_attributes["definition_text"]["description"],
        )

        entity_types = payload["entity_types"]
        term_examples = entity_types["Term"]["examples"]
        concept_examples = entity_types["Concept"]["examples"]
        for example in ("PCB", "TLB", "HRN", "FIFO", "Δ", "FCFS", "SPOOLing"):
            self.assertIn(example, term_examples)
        self.assertNotIn("周转时间", term_examples)
        self.assertNotIn("时间片", term_examples)
        self.assertIn("周转时间", concept_examples)
        self.assertIn("时间片", concept_examples)
        self.assertEqual(entity_types["Assignment"]["label_zh"], "作业/评测任务")
        self.assertIn("复习题", entity_types["Assignment"]["description"])
        self.assertIn("普通概念解释", entity_types["FormulaOrDefinition"]["description"])
        self.assertIn("definition_text", entity_types["FormulaOrDefinition"]["canonical_name_rule"])

    def test_material_7_schema_keeps_hard_relation_boundaries(self):
        payload = json.loads(
            (PROJECT_ROOT / "config" / "schema" / "relation_types.json").read_text(
                encoding="utf-8"
            )
        )
        relation_types = payload["relation_types"]

        self.assertEqual(
            payload["relation_type_order"],
            [
                "contains",
                "belongs_to",
                "defined_by",
                "prerequisite_of",
                "depends_on",
                "implemented_by",
                "applied_in",
                "evaluated_by",
                "related_to",
                "appears_in",
            ],
        )

        contains = relation_types["contains"]
        self.assertIn("KnowledgePoint", contains["source_types"])
        self.assertIn("Concept", contains["source_types"])
        self.assertIn("AlgorithmOrMethod", contains["source_types"])
        self.assertIsNone(contains["inverse_of"])
        self.assertEqual(
            contains["derivable_inverse"],
            {
                "relation": "belongs_to",
                "only_when_source_types": ["Course", "Chapter", "Section"],
            },
        )
        self.assertIn("知识对象", contains["extraction_hint"])
        self.assertIn("不自动派生 belongs_to", contains["extraction_hint"])

        depends_on = relation_types["depends_on"]
        prerequisite_of = relation_types["prerequisite_of"]
        self.assertIsNone(depends_on["inverse_of"])
        self.assertIsNone(prerequisite_of["inverse_of"])
        self.assertFalse(prerequisite_of["can_be_derived"])
        self.assertIn("不从 depends_on 自动推导", prerequisite_of["derivation_hint"])
        self.assertNotIn("脚本", prerequisite_of["extraction_hint"])
        self.assertNotIn("depends_on", prerequisite_of["extraction_hint"])
        self.assertNotIn("反推", prerequisite_of["extraction_hint"])
        self.assertNotIn("推导", prerequisite_of["extraction_hint"])

        self.assertEqual(
            relation_types["belongs_to"]["target_types"],
            ["Course", "Chapter", "Section"],
        )
        self.assertEqual(
            relation_types["appears_in"]["target_types"],
            ["Course", "Chapter", "Section", "Experiment", "Assignment", "ToolOrPlatform"],
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
        self.assertIn("目标必须是 Course/Chapter/Section/Experiment/Assignment/ToolOrPlatform", appears_in_hint)
        self.assertIn("禁止反向 Section appears_in Concept", appears_in_hint)
        self.assertIn("Linux 系统调用", appears_in_hint)
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

    def test_remaining_endpoint_error_rules_are_explicit_in_schema(self):
        payload = json.loads(
            (PROJECT_ROOT / "config" / "schema" / "relation_types.json").read_text(
                encoding="utf-8"
            )
        )
        relation_types = payload["relation_types"]

        applied_in = relation_types["applied_in"]
        self.assertIn("AlgorithmOrMethod", applied_in["source_types"])
        self.assertNotIn("AlgorithmOrMethod", applied_in["target_types"])
        self.assertIn("X 以 Y 为例/使用 Y", applied_in["extraction_hint"])
        self.assertIn("Y applied_in X", applied_in["extraction_hint"])
        self.assertIn("X depends_on Y", applied_in["extraction_hint"])
        self.assertIn("不能反向输出 X applied_in Y", applied_in["extraction_hint"])
        self.assertIn(
            "Concept->AlgorithmOrMethod: 死锁以银行家算法为例 applied_in 银行家算法",
            applied_in["negative_examples"],
        )

        appears_in = relation_types["appears_in"]
        self.assertNotIn("Section", appears_in["source_types"])
        self.assertNotIn("Assignment", appears_in["source_types"])
        self.assertIn("source 必须是出现的知识实体", appears_in["extraction_hint"])
        self.assertIn("禁止 Section/Assignment 反向 appears_in", appears_in["extraction_hint"])
        self.assertIn(
            "Assignment->ToolOrPlatform: 习题 1 appears_in SPOOLing 系统",
            appears_in["negative_examples"],
        )

        defined_by_hint = relation_types["defined_by"]["extraction_hint"]
        self.assertIn("别名、简称、缩写、编号、存在标志", defined_by_hint)
        self.assertIn("进入 alias/归一化或跳过", defined_by_hint)

        evaluated_by = relation_types["evaluated_by"]
        self.assertIn("Term", evaluated_by["source_types"])
        self.assertIn("Experiment", evaluated_by["source_types"])
        self.assertEqual(evaluated_by["target_types"], ["Assignment"])
        self.assertIn(
            "术语本身是考核对象时允许 Term->Assignment",
            evaluated_by["extraction_hint"],
        )
        self.assertIn("实验任务本身被作业评估时允许 Experiment->Assignment", evaluated_by["extraction_hint"])
        self.assertNotIn("Experiment", evaluated_by["target_types"])
        self.assertIn(
            "PCB evaluated_by 第二章习题",
            evaluated_by["examples"],
        )

        related_to = relation_types["related_to"]
        for structural_type in ("Course", "Chapter", "Section"):
            self.assertNotIn(structural_type, related_to["source_types"])
            self.assertNotIn(structural_type, related_to["target_types"])

        appears_in = relation_types["appears_in"]
        self.assertEqual(appears_in["derivation_source"], "metadata_first")
        self.assertIn("标准化文档元数据", appears_in["extraction_hint"])
        self.assertIn("LLM 仅在文本明确表达出现", appears_in["extraction_hint"])
        self.assertTrue(
            any(
                "ToolOrPlatform->ToolOrPlatform" in example
                for example in appears_in["negative_examples"]
            )
        )

        related_to_hint = relation_types["related_to"]["extraction_hint"]
        self.assertIn("source/target 必须都在 entities 中", related_to_hint)
        self.assertIn("缺 target 时补实体或跳过", related_to_hint)

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

    def test_extraction_rules_document_entity_schema_updates(self):
        text = (PROJECT_ROOT / "config" / "schema" / "extraction_rules.md").read_text(
            encoding="utf-8"
        )

        self.assertIn("syllabus、课程大纲、学习目标、考核要求", text)
        self.assertIn("正文解释性段落中出现“X 是/指/表示/定义为”", text)
        self.assertIn("两者均满足时，以 `KnowledgePoint` 为准", text)
        self.assertIn("普通概念解释不提升为 `FormulaOrDefinition`", text)
        self.assertIn("稳定名称、被复用/引用、可计算公式/定理/判定条件", text)
        self.assertIn("Section、Experiment 与 Assignment 的双角色处理", text)
        self.assertIn("同时抽取 `Section` 与 `Experiment` / `Assignment`", text)
        self.assertIn("建立 `Section contains Experiment` 或 `Section contains Assignment`", text)
        self.assertIn("作业、习题、报告、测验、考试题组、复习题", text)
        self.assertIn("按 target 场景分组", text)
        self.assertIn("分别统计 `endpoint_valid_rate`", text)
        self.assertIn("不作为 LLM 主动抽取的主要关系", text)
        self.assertIn("不与强语义关系同权计分", text)
        self.assertIn("v1 已知限制", text)
        self.assertIn("暂未显式建模 `Document` / `SourceMaterial`", text)
        self.assertIn("暂未显式建模 is-a / subclass-of", text)
        self.assertIn("course scope", text)
        self.assertIn("不推荐输出 `ToolOrPlatform->ToolOrPlatform appears_in`", text)
        self.assertIn("冗余维护债务", text)

    def test_extraction_rules_document_remaining_endpoint_fixes(self):
        text = (PROJECT_ROOT / "config" / "schema" / "extraction_rules.md").read_text(
            encoding="utf-8"
        )
        self.assertIn("X 以 Y 为例/使用 Y", text)
        self.assertIn("应输出 `Y applied_in X` 或 `X depends_on Y`", text)
        self.assertIn("不能反向输出 `X applied_in Y`", text)
        self.assertIn("source 必须是出现的知识实体", text)
        self.assertIn("禁止 `Section/Assignment appears_in 知识对象`", text)
        self.assertIn("别名、简称、缩写、编号、存在标志不建立 `defined_by`", text)
        self.assertIn("进入 alias / 归一化字段，或直接跳过", text)
        self.assertIn("如果术语本身就是题目考核对象，允许 `Term->Assignment`", text)
        self.assertIn("`PCB evaluated_by 第二章习题`", text)
        self.assertIn("允许 `Concept/Term/AlgorithmOrMethod -> ToolOrPlatform`", text)
        self.assertIn("source/target 必须都在 `entities` 中", text)
        self.assertIn("缺 target 时补实体或跳过", text)


if __name__ == "__main__":
    unittest.main()
