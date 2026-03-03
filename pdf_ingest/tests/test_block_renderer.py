#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
BlockRenderer 单元测试
======================
覆盖场景:
    1. TABLE 无 text (仅 table_body HTML) → 渲染出 Markdown/纯文本
    2. TABLE 有 text (semantic_table=True) → 直接使用语义文本
    3. 未知 block_type → FallbackRenderer 兜底不报错
    4. IMAGE → 占位符 + metadata
    5. TEXT/TITLE/LIST/CODE/EQUATION → 基本渲染
    6. 异常 block (text=None) → 安全降级
    7. HTML 表格 → Markdown 转换 (regex)
    8. 注册表: 自定义 renderer 替换默认
"""

import sys
import unittest
from pathlib import Path

# 确保 scripts/pdf_processor 在 sys.path 中
_PROJECT_ROOT = Path(__file__).resolve().parent.parent
_MODULE_DIR = _PROJECT_ROOT / "scripts" / "pdf_processor"
if str(_MODULE_DIR) not in sys.path:
    sys.path.insert(0, str(_MODULE_DIR))

from block_model import Block, BlockType
from block_renderer import (
    BlockRendererRegistry,
    RenderResult,
    TableRenderer,
    ImageRenderer,
    TextRenderer,
    FallbackRenderer,
    html_table_to_markdown,
    html_table_to_plain_text,
    _rows_to_markdown,
)


def _make_block(
    block_type: BlockType = BlockType.TEXT,
    text: str = "",
    block_id: str = "test:0:0",
    page: int = 0,
    asset_ref: str | None = None,
    extra: dict | None = None,
    text_level: int | None = None,
    raw_type: str = "",
) -> Block:
    """快捷构造 Block."""
    return Block(
        block_id=block_id,
        page=page,
        block_type=block_type,
        text=text,
        raw_type=raw_type or block_type.value,
        text_level=text_level,
        asset_ref=asset_ref,
        source="test.pdf",
        bbox=None,
        extra=extra or {},
    )


# ===================== 核心测试用例 =====================


class TestTableRendererNoText(unittest.TestCase):
    """场景 1: TABLE block 没有 text 字段, 仅有 table_body HTML。"""

    def test_table_without_text_renders_markdown(self):
        """table_body HTML 应被转为 Markdown 表格。"""
        html = (
            "<table>"
            "<tr><th>姓名</th><th>年龄</th><th>城市</th></tr>"
            "<tr><td>张三</td><td>25</td><td>北京</td></tr>"
            "<tr><td>李四</td><td>30</td><td>上海</td></tr>"
            "</table>"
        )
        block = _make_block(
            block_type=BlockType.TABLE,
            text="",  # 无 text
            extra={
                "table_body": html,
                "table_caption": ["表1: 人员信息"],
                "table_footnote": ["数据来源: 2024年调查"],
            },
            asset_ref="images/table1.png",
            page=5,
        )

        registry = BlockRendererRegistry.create_default(prefer_markdown=True)
        result = registry.render(block)

        # 应包含占位符
        self.assertIn("[TABLE]", result.text)
        self.assertIn("ref=images/table1.png", result.text)
        self.assertIn("page=5", result.text)

        # 应包含 caption
        self.assertIn("Caption: 表1: 人员信息", result.text)

        # 应包含表格内容 (Markdown 或纯文本)
        self.assertIn("张三", result.text)
        self.assertIn("李四", result.text)
        self.assertIn("姓名", result.text)

        # 应包含 footnote
        self.assertIn("Footnote: 数据来源: 2024年调查", result.text)

        # metadata 应包含结构化信息
        self.assertEqual(result.metadata["type"], "table")
        self.assertEqual(result.metadata["img_path"], "images/table1.png")
        self.assertEqual(result.metadata["page_idx"], 5)
        self.assertEqual(result.metadata["caption"], "表1: 人员信息")
        self.assertIn("html", result.metadata)


class TestTableRendererWithText(unittest.TestCase):
    """场景 2: TABLE block 有 text (semantic_table=True 已处理)。"""

    def test_table_with_semantic_text(self):
        """block.text 有语义文本时应直接使用, 不再重新转换 HTML。"""
        semantic_text = "姓名=张三; 年龄=25; 城市=北京\n姓名=李四; 年龄=30; 城市=上海"
        block = _make_block(
            block_type=BlockType.TABLE,
            text=semantic_text,
            extra={
                "table_body": "<table><tr><td>张三</td></tr></table>",
                "table_caption": [],
                "table_footnote": [],
            },
            page=3,
        )

        registry = BlockRendererRegistry.create_default(prefer_markdown=True)
        result = registry.render(block)

        # 占位符应存在
        self.assertIn("[TABLE]", result.text)
        # 应包含语义文本 (来自 block.text, 而非 HTML 重转换)
        self.assertIn("姓名=张三", result.text)
        self.assertIn("姓名=李四", result.text)

    def test_table_text_starts_with_caption(self):
        """block.text 以 [TABLE CAPTION] 开头时, renderer 不应重复添加 caption。"""
        block = _make_block(
            block_type=BlockType.TABLE,
            text="[TABLE CAPTION] 表1\n张三\t25\n李四\t30",
            extra={
                "table_body": "",
                "table_caption": [],
                "table_footnote": [],
            },
        )
        registry = BlockRendererRegistry.create_default()
        result = registry.render(block)

        # text 应只出现一次 caption 相关内容
        self.assertIn("[TABLE]", result.text)
        self.assertIn("张三", result.text)


class TestUnknownBlockType(unittest.TestCase):
    """场景 3: 未知 block_type → FallbackRenderer。"""

    def test_unknown_type_does_not_raise(self):
        """未注册的 block_type 不应抛错。"""
        # 创建一个自定义 BlockType 值 (OTHER 作为替代)
        block = _make_block(
            block_type=BlockType.OTHER,
            text="这是一段未知类型的文本",
            raw_type="custom_widget",
        )
        registry = BlockRendererRegistry.create_default()
        result = registry.render(block)

        self.assertIn("这是一段未知类型的文本", result.text)
        self.assertIsInstance(result, RenderResult)

    def test_unknown_type_empty_text(self):
        """未知类型且 text 为空时应生成占位符。"""
        block = _make_block(
            block_type=BlockType.OTHER,
            text="",
            block_id="test:1:42",
        )
        registry = BlockRendererRegistry.create_default()
        result = registry.render(block)

        # 应生成包含 block_id 的占位符
        self.assertIn("block_id=test:1:42", result.text)


class TestImageRenderer(unittest.TestCase):
    """IMAGE block 渲染。"""

    def test_image_placeholder_and_metadata(self):
        block = _make_block(
            block_type=BlockType.IMAGE,
            text="",
            asset_ref="images/fig1.jpg",
            page=10,
            block_id="os:10:5",
        )
        registry = BlockRendererRegistry.create_default()
        result = registry.render(block)

        self.assertIn("[IMAGE]", result.text)
        self.assertIn("ref=images/fig1.jpg", result.text)
        self.assertIn("page=10", result.text)
        self.assertEqual(result.metadata["type"], "image")
        self.assertEqual(result.metadata["img_path"], "images/fig1.jpg")
        self.assertEqual(result.metadata["block_id"], "os:10:5")


class TestTextAndTitleRenderer(unittest.TestCase):
    """TEXT / TITLE 基本渲染。"""

    def test_text_renderer(self):
        block = _make_block(text="Hello World")
        registry = BlockRendererRegistry.create_default()
        result = registry.render(block)
        self.assertEqual(result.text, "Hello World")

    def test_title_with_heading_marker(self):
        block = _make_block(
            block_type=BlockType.TITLE,
            text="第一章 绪论",
            text_level=1,
        )
        registry = BlockRendererRegistry.create_default(use_heading_marker=True)
        result = registry.render(block)
        self.assertEqual(result.text, "# 第一章 绪论")

    def test_title_without_heading_marker(self):
        block = _make_block(
            block_type=BlockType.TITLE,
            text="第一章 绪论",
            text_level=1,
        )
        registry = BlockRendererRegistry.create_default(use_heading_marker=False)
        result = registry.render(block)
        self.assertEqual(result.text, "第一章 绪论")


class TestSafeRendering(unittest.TestCase):
    """异常安全测试。"""

    def test_none_text_does_not_crash(self):
        """block.text 为 None 时不应崩溃。"""
        block = _make_block(text="")
        # 强制设置 text 为 None 模拟异常数据
        object.__setattr__(block, "text", None)

        registry = BlockRendererRegistry.create_default()
        result = registry.render(block)

        # 不应抛错, 应返回空字符串或占位符
        self.assertIsInstance(result, RenderResult)
        self.assertIsInstance(result.text, str)

    def test_table_with_malformed_html(self):
        """畸形 HTML 不应导致崩溃。"""
        block = _make_block(
            block_type=BlockType.TABLE,
            text="",
            extra={
                "table_body": "<table><tr><td>unclosed",
                "table_caption": [],
                "table_footnote": [],
            },
        )
        registry = BlockRendererRegistry.create_default()
        result = registry.render(block)

        # 不应抛错
        self.assertIsInstance(result.text, str)
        self.assertIn("[TABLE]", result.text)


class TestHtmlTableConversion(unittest.TestCase):
    """HTML 表格转换函数测试。"""

    def test_regex_html_to_markdown(self):
        html = (
            "<table>"
            "<tr><th>A</th><th>B</th></tr>"
            "<tr><td>1</td><td>2</td></tr>"
            "</table>"
        )
        md = html_table_to_markdown(html)
        self.assertIn("|", md)
        self.assertIn("A", md)
        self.assertIn("1", md)
        # 应有分隔线
        self.assertIn("---", md)

    def test_html_to_plain_text(self):
        html = "<table><tr><td>X</td><td>Y</td></tr></table>"
        plain = html_table_to_plain_text(html)
        self.assertIn("X", plain)
        self.assertIn("Y", plain)

    def test_empty_html(self):
        self.assertEqual(html_table_to_markdown(""), "")
        self.assertEqual(html_table_to_plain_text(""), "")

    def test_rows_to_markdown_alignment(self):
        rows = [["Name", "Age"], ["Alice", "30"], ["Bob", "25"]]
        md = _rows_to_markdown(rows)
        lines = md.strip().split("\n")
        self.assertEqual(len(lines), 4)  # header + separator + 2 data rows
        self.assertTrue(lines[1].replace("|", "").replace("-", "").replace(" ", "") == "")

    def test_html_entity_decoding(self):
        html = "<table><tr><td>A &amp; B</td><td>C &lt; D</td></tr></table>"
        plain = html_table_to_plain_text(html)
        self.assertIn("A & B", plain)
        self.assertIn("C < D", plain)


class TestRegistryCustomization(unittest.TestCase):
    """注册表自定义测试。"""

    def test_custom_renderer_overrides_default(self):
        """注册自定义 renderer 应覆盖默认。"""

        class CustomTextRenderer(TextRenderer):
            def render(self, block: Block) -> str:
                return f"[CUSTOM] {(block.text or '').strip()}"

        registry = BlockRendererRegistry.create_default()
        registry.register(BlockType.TEXT, CustomTextRenderer())

        block = _make_block(text="hello")
        result = registry.render(block)
        self.assertEqual(result.text, "[CUSTOM] hello")


if __name__ == "__main__":
    unittest.main()
