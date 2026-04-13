#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
BlockModel 兼容性测试
=====================
覆盖场景:
    1. 旧版扁平 content_list 正常加载
    2. 新版按页分组 content_list_v2 可归一化
    3. 归一化结果可继续被 parse_content_list 解析
"""

import json
import sys
import unittest
from pathlib import Path
from tempfile import TemporaryDirectory

# 确保 scripts/pdf_processor 在 sys.path 中
_PROJECT_ROOT = Path(__file__).resolve().parent.parent
_MODULE_DIR = _PROJECT_ROOT / "scripts" / "pdf_processor"
if str(_MODULE_DIR) not in sys.path:
    sys.path.insert(0, str(_MODULE_DIR))

from block_model import load_content_list_file, parse_content_list, BlockType


class TestLoadContentListCompatibility(unittest.TestCase):
    """content_list 兼容性测试。"""

    def test_load_flat_content_list_keeps_original_shape(self):
        data = [
            {
                "type": "text",
                "text": "第一章 绪论",
                "text_level": 1,
                "page_idx": 0,
                "bbox": [0, 0, 10, 10],
            },
            {
                "type": "text",
                "text": "课程介绍",
                "page_idx": 0,
                "bbox": [0, 10, 10, 20],
            },
        ]

        with TemporaryDirectory() as tmp_dir:
            path = Path(tmp_dir) / "content_list.json"
            path.write_text(json.dumps(data, ensure_ascii=False), encoding="utf-8")

            loaded = load_content_list_file(path)

        self.assertEqual(loaded, data)

    def test_load_page_grouped_content_list_v2_normalizes_to_flat_blocks(self):
        data = [
            [
                {
                    "type": "page_header",
                    "content": {
                        "page_header_content": [{"type": "text", "content": "页眉"}]
                    },
                    "bbox": [0, 0, 10, 10],
                },
                {
                    "type": "title",
                    "content": {
                        "title_content": [{"type": "text", "content": "第一章 绪论"}],
                        "level": 1,
                    },
                    "bbox": [10, 10, 100, 30],
                },
                {
                    "type": "paragraph",
                    "content": {
                        "paragraph_content": [
                            {"type": "text", "content": "段落"},
                            {"type": "equation_inline", "content": "x+y"},
                        ]
                    },
                    "bbox": [10, 40, 100, 60],
                },
                {
                    "type": "list",
                    "content": {
                        "list_type": "text_list",
                        "list_items": [
                            {
                                "item_type": "text",
                                "item_content": [{"type": "text", "content": "条目1"}],
                            },
                            {
                                "item_type": "text",
                                "item_content": [{"type": "text", "content": "条目2"}],
                            },
                        ],
                    },
                    "bbox": [10, 70, 100, 100],
                },
            ],
            [
                {
                    "type": "table",
                    "content": {
                        "image_source": {"path": "images/table1.jpg"},
                        "table_caption": [{"type": "text", "content": "表1"}],
                        "table_footnote": [{"type": "text", "content": "注1"}],
                        "html": "<table><tr><th>姓名</th></tr><tr><td>张三</td></tr></table>",
                    },
                    "bbox": [10, 10, 100, 60],
                },
                {
                    "type": "image",
                    "content": {
                        "image_source": {"path": "images/fig1.jpg"},
                        "image_caption": [{"type": "text", "content": "图1"}],
                        "image_footnote": [{"type": "text", "content": "说明"}],
                    },
                    "bbox": [10, 70, 100, 140],
                },
                {
                    "type": "equation_interline",
                    "content": {
                        "math_content": "a=b",
                        "math_type": "latex",
                        "image_source": {"path": "images/eq1.jpg"},
                    },
                    "bbox": [10, 150, 100, 180],
                },
                {
                    "type": "code",
                    "content": {
                        "code_caption": [{"type": "text", "content": "代码1"}],
                        "code_content": [{"type": "text", "content": "print(1)"}],
                        "code_language": "python",
                    },
                    "bbox": [10, 190, 100, 220],
                },
                {
                    "type": "algorithm",
                    "content": {
                        "algorithm_caption": [{"type": "text", "content": "算法1"}],
                        "algorithm_content": [
                            {"type": "text", "content": "if "},
                            {"type": "equation_inline", "content": "x>0"},
                            {"type": "text", "content": ": pass"},
                        ],
                    },
                    "bbox": [10, 230, 100, 260],
                },
                {
                    "type": "page_number",
                    "content": {
                        "page_number_content": [{"type": "text", "content": "2"}]
                    },
                    "bbox": [90, 280, 100, 290],
                },
            ],
        ]

        with TemporaryDirectory() as tmp_dir:
            path = Path(tmp_dir) / "content_list_v2.json"
            path.write_text(json.dumps(data, ensure_ascii=False), encoding="utf-8")

            loaded = load_content_list_file(path)

        self.assertEqual(len(loaded), 10)

        self.assertEqual(loaded[0]["type"], "header")
        self.assertEqual(loaded[0]["text"], "页眉")
        self.assertEqual(loaded[0]["page_idx"], 0)

        self.assertEqual(loaded[1]["type"], "text")
        self.assertEqual(loaded[1]["text"], "第一章 绪论")
        self.assertEqual(loaded[1]["text_level"], 1)

        self.assertEqual(loaded[2]["text"], "段落x+y")
        self.assertEqual(loaded[3]["list_items"], ["条目1", "条目2"])

        self.assertEqual(loaded[4]["type"], "table")
        self.assertEqual(loaded[4]["img_path"], "images/table1.jpg")
        self.assertEqual(loaded[4]["table_caption"], ["表1"])

        self.assertEqual(loaded[5]["type"], "image")
        self.assertEqual(loaded[5]["image_caption"], ["图1"])
        self.assertEqual(loaded[5]["image_footnote"], ["说明"])

        self.assertEqual(loaded[6]["type"], "equation")
        self.assertEqual(loaded[6]["text"], "a=b")
        self.assertEqual(loaded[6]["text_format"], "latex")

        self.assertEqual(loaded[7]["type"], "code")
        self.assertEqual(loaded[7]["guess_lang"], "python")
        self.assertEqual(loaded[8]["guess_lang"], "algorithm")
        self.assertEqual(loaded[8]["code_body"], "if x>0: pass")

    def test_parse_content_list_accepts_normalized_v2_output(self):
        data = [
            {
                "type": "text",
                "text": "第一章 绪论",
                "text_level": 1,
                "page_idx": 0,
                "bbox": [0, 0, 10, 10],
            },
            {
                "type": "list",
                "list_items": ["条目1", "条目2"],
                "page_idx": 0,
                "bbox": [0, 10, 10, 20],
            },
            {
                "type": "table",
                "table_body": "<table><tr><th>姓名</th></tr><tr><td>张三</td></tr></table>",
                "table_caption": ["表1"],
                "table_footnote": ["注1"],
                "img_path": "images/table1.jpg",
                "page_idx": 1,
                "bbox": [0, 20, 10, 30],
            },
            {
                "type": "image",
                "img_path": "images/fig1.jpg",
                "image_caption": ["图1"],
                "image_footnote": ["说明"],
                "page_idx": 1,
                "bbox": [0, 30, 10, 40],
            },
            {
                "type": "equation",
                "text": "a=b",
                "text_format": "latex",
                "page_idx": 1,
                "bbox": [0, 40, 10, 50],
            },
            {
                "type": "code",
                "code_body": "print(1)",
                "guess_lang": "python",
                "code_caption": ["代码1"],
                "page_idx": 1,
                "bbox": [0, 50, 10, 60],
            },
        ]

        blocks = parse_content_list(data, course_id="os", source_file="book.pdf")

        self.assertEqual(len(blocks), 6)
        self.assertEqual(blocks[0].block_type, BlockType.TITLE)
        self.assertEqual(blocks[1].block_type, BlockType.LIST)
        self.assertEqual(blocks[2].block_type, BlockType.TABLE)
        self.assertEqual(blocks[3].block_type, BlockType.IMAGE)
        self.assertEqual(blocks[4].block_type, BlockType.EQUATION)
        self.assertEqual(blocks[5].block_type, BlockType.CODE)

        self.assertEqual(blocks[0].page, 0)
        self.assertEqual(blocks[2].asset_ref, "images/table1.jpg")
        self.assertEqual(blocks[3].extra["image_caption"], ["图1"])
        self.assertIn("```python", blocks[5].text)


if __name__ == "__main__":
    unittest.main()
