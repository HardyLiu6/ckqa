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


if __name__ == "__main__":
    unittest.main()
