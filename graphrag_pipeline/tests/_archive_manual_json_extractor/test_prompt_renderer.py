#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""Prompt 渲染器 compact 模式测试。"""

from __future__ import annotations

import json
import sys
import tempfile
import unittest
from pathlib import Path


_PROJECT_ROOT = Path(__file__).resolve().parent.parent
_SCRIPTS_DIR = _PROJECT_ROOT / "scripts"
if str(_SCRIPTS_DIR) not in sys.path:
    sys.path.insert(0, str(_SCRIPTS_DIR))

from prompt_loader import CandidatePrompt, load_schema_catalog
from prompt_renderer import render_extraction_messages


def _write_json(path: Path, payload: object) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(json.dumps(payload, ensure_ascii=False), encoding="utf-8")


class TestPromptRenderer(unittest.TestCase):
    def test_compact_mode_omits_long_tuple_examples_and_injects_sample_once(self):
        with tempfile.TemporaryDirectory() as tmp:
            root = Path(tmp)
            schema_dir = root / "config" / "schema"
            _write_json(
                schema_dir / "entity_types.json",
                {
                    "entity_types": {
                        "Concept": {"label_zh": "概念", "description": "课程概念"},
                        "AlgorithmOrMethod": {"label_zh": "方法", "description": "课程方法"},
                    }
                },
            )
            _write_json(
                schema_dir / "relation_types.json",
                {
                    "relation_types": {
                        "contains": {"label_zh": "包含", "description": "包含"},
                        "related_to": {"label_zh": "相关", "description": "相关"},
                    }
                },
            )
            catalog = load_schema_catalog(
                entity_schema_path=schema_dir / "entity_types.json",
                relation_schema_path=schema_dir / "relation_types.json",
            )
            prompt_text = """-Goal-
识别课程知识实体与关系。

Example 1:
("entity"<|>MPEG<|>file format<|>很长的旧 tuple 示例)

Example 2:
("relationship"<|>A<|>B<|>旧关系示例<|>8)

-Real Data-
text: {input_text}
"""
            sample = {
                "sample_id": "pts-compact",
                "course_id": "os",
                "document_type": "textbook",
                "source_file": "book.pdf",
                "chapter": "第二章 进程",
                "section": "2.1 进程定义",
                "heading_path": ["第二章 进程", "2.1 进程定义"],
                "page_start": 10,
                "page_end": 10,
                "text": "进程是程序的一次执行过程。",
            }
            candidate = CandidatePrompt(
                name="default",
                prompt_text=prompt_text,
                prompt_path=root / "prompt.txt",
                manifest_entry={"candidate_name": "default"},
            )

            messages = render_extraction_messages(
                candidate=candidate,
                sample=sample,
                schema_catalog=catalog,
                max_entities=3,
                max_relationships=2,
            )
            user_message = next(item["content"] for item in messages if item["role"] == "user")

        self.assertNotIn("Example 1", user_message)
        self.assertNotIn("MPEG", user_message)
        self.assertEqual(user_message.count("进程是程序的一次执行过程。"), 1)
        self.assertIn("最多输出 3 个实体、2 条关系", user_message)
        self.assertIn("Concept", user_message)

    def test_compact_mode_keeps_schema_and_compressed_fewshot_after_legacy_examples(self):
        with tempfile.TemporaryDirectory() as tmp:
            root = Path(tmp)
            schema_dir = root / "config" / "schema"
            _write_json(
                schema_dir / "entity_types.json",
                {
                    "entity_types": {
                        "Concept": {"label_zh": "概念", "description": "课程概念"},
                        "Assignment": {"label_zh": "作业", "description": "课程作业"},
                    }
                },
            )
            _write_json(
                schema_dir / "relation_types.json",
                {
                    "relation_types": {
                        "contains": {"label_zh": "包含", "description": "包含"},
                        "evaluated_by": {"label_zh": "评估", "description": "评估"},
                    }
                },
            )
            catalog = load_schema_catalog(
                entity_schema_path=schema_dir / "entity_types.json",
                relation_schema_path=schema_dir / "relation_types.json",
            )
            prompt_text = """-Goal-
识别课程知识实体与关系。

Example 1:
("entity"<|>MPEG<|>file format<|>旧通用示例)

-Schema Constraints-
实体类型必须来自课程 Schema。

-Few-shot 示例-
Example 1 (definition_or_formula)
input:
text:
进程是程序的一次执行过程。
output:
("entity"<|>进程<|>Concept<|>课程概念)

-Real Data-
text: {input_text}
"""
            sample = {
                "sample_id": "pts-compact-schema",
                "course_id": "os",
                "document_type": "textbook",
                "source_file": "book.pdf",
                "chapter": "第二章 进程",
                "section": "2.1 进程定义",
                "heading_path": ["第二章 进程", "2.1 进程定义"],
                "page_start": 10,
                "page_end": 10,
                "text": "作业要求说明进程和线程的区别。",
            }
            candidate = CandidatePrompt(
                name="schema_fewshot",
                prompt_text=prompt_text,
                prompt_path=root / "prompt.txt",
                manifest_entry={"candidate_name": "schema_fewshot"},
            )

            messages = render_extraction_messages(
                candidate=candidate,
                sample=sample,
                schema_catalog=catalog,
            )
            user_message = next(item["content"] for item in messages if item["role"] == "user")

        self.assertNotIn("MPEG", user_message)
        self.assertIn("实体类型必须来自课程 Schema", user_message)
        self.assertIn("Few-shot 示例", user_message)
        self.assertIn("进程是程序的一次执行过程。", user_message)
        self.assertEqual(user_message.count("作业要求说明进程和线程的区别。"), 1)

    def test_relation_summary_includes_endpoint_constraints_when_candidate_is_truncated(self):
        with tempfile.TemporaryDirectory() as tmp:
            root = Path(tmp)
            schema_dir = root / "config" / "schema"
            _write_json(
                schema_dir / "entity_types.json",
                {
                    "entity_types": {
                        "AlgorithmOrMethod": {"label_zh": "方法", "description": "课程方法"},
                        "KnowledgePoint": {"label_zh": "知识点", "description": "课程知识点"},
                        "Concept": {"label_zh": "概念", "description": "课程概念"},
                    }
                },
            )
            _write_json(
                schema_dir / "relation_types.json",
                {
                    "relation_types": {
                        "applied_in": {
                            "label_zh": "应用于",
                            "description": "方法应用场景",
                            "source_types": ["AlgorithmOrMethod"],
                            "target_types": ["KnowledgePoint", "Concept"],
                            "extraction_hint": "用于算法或方法应用到知识主题的明确表达。",
                        }
                    }
                },
            )
            catalog = load_schema_catalog(
                entity_schema_path=schema_dir / "entity_types.json",
                relation_schema_path=schema_dir / "relation_types.json",
            )
            candidate = CandidatePrompt(
                name="schema_aware",
                prompt_text="候选策略开头。" + ("很长的候选说明。" * 200),
                prompt_path=root / "prompt.txt",
                manifest_entry={"candidate_name": "schema_aware"},
            )

            messages = render_extraction_messages(
                candidate=candidate,
                sample={"sample_id": "pts-endpoint", "text": "银行家算法用于死锁避免。"},
                schema_catalog=catalog,
                max_prompt_chars=80,
            )
            user_message = next(item["content"] for item in messages if item["role"] == "user")

        self.assertIn("source_types: AlgorithmOrMethod", user_message)
        self.assertIn("target_types: KnowledgePoint, Concept", user_message)
        self.assertIn("用于算法或方法应用到知识主题的明确表达", user_message)

    def test_rendered_prompt_documents_optional_alias_and_definition_text(self):
        with tempfile.TemporaryDirectory() as tmp:
            root = Path(tmp)
            schema_dir = root / "config" / "schema"
            _write_json(
                schema_dir / "entity_types.json",
                {
                    "entity_types": {
                        "Concept": {"label_zh": "概念", "description": "课程概念"},
                        "FormulaOrDefinition": {"label_zh": "公式定义", "description": "公式或严格定义"},
                    }
                },
            )
            _write_json(
                schema_dir / "relation_types.json",
                {
                    "relation_types": {
                        "related_to": {"label_zh": "相关", "description": "相关"},
                    }
                },
            )
            catalog = load_schema_catalog(
                entity_schema_path=schema_dir / "entity_types.json",
                relation_schema_path=schema_dir / "relation_types.json",
            )
            candidate = CandidatePrompt(
                name="schema_aware",
                prompt_text="识别课程知识实体。",
                prompt_path=root / "prompt.txt",
                manifest_entry={"candidate_name": "schema_aware"},
            )

            messages = render_extraction_messages(
                candidate=candidate,
                sample={"sample_id": "pts-alias", "text": "TLB 是 Translation Lookaside Buffer 的缩写。"},
                schema_catalog=catalog,
            )
            user_message = next(item["content"] for item in messages if item["role"] == "user")

        self.assertIn("alias 用于别名、缩写、英文全称或同义表达", user_message)
        self.assertIn("不作为关系端点类型", user_message)
        self.assertIn("普通概念解释可写 definition_text", user_message)
        self.assertIn("不足以提升为 FormulaOrDefinition", user_message)
        self.assertIn('"alias": [', user_message)
        self.assertIn('"definition_text": "普通概念解释或定义性原文"', user_message)

    def test_real_schema_endpoint_fix_rules_are_visible_in_rendered_prompt(self):
        catalog = load_schema_catalog(
            entity_schema_path=_PROJECT_ROOT / "config" / "schema" / "entity_types.json",
            relation_schema_path=_PROJECT_ROOT / "config" / "schema" / "relation_types.json",
        )
        candidate = CandidatePrompt(
            name="schema_fewshot",
            prompt_text="候选策略会被截断。" + ("额外说明。" * 200),
            prompt_path=_PROJECT_ROOT / "prompts" / "candidates" / "schema_fewshot" / "prompt.txt",
            manifest_entry={"candidate_name": "schema_fewshot"},
        )

        messages = render_extraction_messages(
            candidate=candidate,
            sample={
                "sample_id": "pts-endpoint-fixes",
                "text": "死锁以银行家算法为例，习题考核 TLB。SPOOLing 系统出现在习题中。",
            },
            schema_catalog=catalog,
            max_prompt_chars=40,
        )
        user_message = next(item["content"] for item in messages if item["role"] == "user")

        self.assertIn("Y applied_in X", user_message)
        self.assertIn("不能反向输出 X applied_in Y", user_message)
        self.assertIn("source 必须是出现的知识实体", user_message)
        self.assertIn("禁止 Section/Assignment 反向 appears_in", user_message)
        self.assertIn("别名、简称、缩写、编号、存在标志", user_message)
        self.assertIn("术语本身是考核对象时允许 Term->Assignment", user_message)
        self.assertIn("source/target 必须都在 entities 中", user_message)
        self.assertIn("缺 target 时补实体或跳过", user_message)


if __name__ == "__main__":
    unittest.main()
