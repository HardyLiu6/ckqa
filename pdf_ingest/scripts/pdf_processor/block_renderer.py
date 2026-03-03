#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
Block 渲染模块 (插件式架构)
============================
将 Block 渲染为可检索文本 + 结构化元数据。

设计原则:
    - 每种 BlockType 对应独立的 Renderer (单一职责)
    - 通过 BlockRendererRegistry 注册和查找 (开闭原则)
    - 未知 block_type 使用 FallbackRenderer (永不抛错)
    - 所有 render 异常内部捕获, 降级处理

用法:
    registry = BlockRendererRegistry.create_default(prefer_markdown=True)
    result = registry.render(block)
    # result.text     -> 拼接到文档的可检索文本
    # result.metadata -> 结构化信息 (table/image 等)
"""

from __future__ import annotations

import html as html_mod
import logging
import re
from abc import ABC, abstractmethod
from dataclasses import dataclass, field
from typing import Any, Dict, List, Optional

from block_model import Block, BlockType

logger = logging.getLogger("BlockRenderer")


# ===================== 渲染结果 =====================

@dataclass
class RenderResult:
    """单个 block 的渲染输出。"""
    text: str                                               # 可检索文本
    metadata: Dict[str, Any] = field(default_factory=dict)  # 结构化元数据


# ===================== 渲染器接口 =====================

class BlockRenderer(ABC):
    """渲染器基类。子类必须实现 render()。"""

    @abstractmethod
    def render(self, block: Block) -> str:
        """将 block 渲染为可检索文本。"""
        ...

    def render_metadata(self, block: Block) -> Dict[str, Any]:
        """提取结构化元数据 (子类可选覆盖)。"""
        return {}

    def render_full(self, block: Block) -> RenderResult:
        """render() + render_metadata(), 内部捕获异常。"""
        try:
            text = self.render(block)
        except Exception as e:
            logger.warning("render() 失败 block=%s: %s", block.block_id, e)
            text = _safe_text(block)
        try:
            metadata = self.render_metadata(block)
        except Exception as e:
            logger.warning("render_metadata() 失败 block=%s: %s", block.block_id, e)
            metadata = {}
        return RenderResult(text=text, metadata=metadata)


# ===================== 辅助函数 =====================

def _safe_text(block: Block) -> str:
    """安全获取 block.text, 永不抛错。"""
    try:
        return (block.text or "").strip()
    except Exception:
        return ""


def _join_list_field(items: Any) -> str:
    """将 caption/footnote 列表字段拼接为字符串。"""
    if not items:
        return ""
    if isinstance(items, str):
        return items.strip()
    if isinstance(items, list):
        return " ".join(str(c) for c in items if c).strip()
    return str(items).strip()


# ===================== 具体渲染器 =====================

class TextRenderer(BlockRenderer):
    """TEXT: 直接使用 text。"""

    def render(self, block: Block) -> str:
        return _safe_text(block)


class TitleRenderer(BlockRenderer):
    """TITLE: 可选添加 Markdown heading 标记。"""

    def __init__(self, *, use_heading_marker: bool = False) -> None:
        self.use_heading_marker = use_heading_marker

    def render(self, block: Block) -> str:
        text = _safe_text(block)
        if self.use_heading_marker and block.text_level and text:
            prefix = "#" * min(block.text_level, 6)
            return f"{prefix} {text}"
        return text


class ListRenderer(BlockRenderer):
    """LIST: 使用预处理文本, 或从 extra.list_items 重建。"""

    def render(self, block: Block) -> str:
        text = _safe_text(block)
        if text:
            return text
        items = block.extra.get("list_items", [])
        if items:
            return "\n".join(f"- {item}" for item in items)
        return ""


class CodeRenderer(BlockRenderer):
    """CODE: 保留 code fence。"""

    def render(self, block: Block) -> str:
        text = _safe_text(block)
        if text:
            return text
        body = block.extra.get("code_body", "")
        lang = block.extra.get("guess_lang", "")
        if body:
            parts: List[str] = []
            caption_parts = block.extra.get("code_caption", [])
            caption = _join_list_field(caption_parts)
            if caption:
                parts.append(f"[CODE CAPTION] {caption}")
            parts.append(f"```{lang}\n{body}\n```")
            return "\n".join(parts)
        return ""


class EquationRenderer(BlockRenderer):
    """EQUATION: 保留公式文本。"""

    def render(self, block: Block) -> str:
        return _safe_text(block)


class ImageRenderer(BlockRenderer):
    """IMAGE: 输出 [IMAGE] 占位符 + ref/page。"""

    def render(self, block: Block) -> str:
        img_path = block.asset_ref or block.extra.get("img_path", "")
        page = block.page if block.page is not None else "?"
        return f"[IMAGE] ref={img_path} page={page}"

    def render_metadata(self, block: Block) -> Dict[str, Any]:
        return {
            "type": "image",
            "img_path": block.asset_ref or block.extra.get("img_path", ""),
            "page_idx": block.page,
            "block_id": block.block_id,
        }


class TableRenderer(BlockRenderer):
    """
    TABLE: 多级降级渲染。

    渲染策略:
        1. 输出 [TABLE] ref=<img_path> page=<page_idx> 占位符
        2. 附加 caption (若有)
        3. 内容:
           a) 若 extra.table_body 存在 → 尝试转为 Markdown (可选)
              → 失败则转为纯文本 (TSV)
              → 再失败保留原 HTML
           b) 否则使用 block.text (block_model 已提取的文本)
           c) 都没有 → 仅保留占位符
        4. 附加 footnote (若有)
        5. render_metadata() 输出结构化表格信息
    """

    def __init__(self, *, prefer_markdown: bool = True) -> None:
        self.prefer_markdown = prefer_markdown

    def render(self, block: Block) -> str:
        parts: List[str] = []

        # ---- 占位符 ----
        img_path = block.asset_ref or block.extra.get("img_path", "")
        page = block.page if block.page is not None else "?"
        parts.append(f"[TABLE] ref={img_path} page={page}")

        # ---- Caption ----
        caption = _join_list_field(block.extra.get("table_caption", []))
        if caption:
            parts.append(f"Caption: {caption}")

        # ---- 表格内容 ----
        #   优先级: block.text (语义文本) > table_body HTML 转换
        table_body: str = block.extra.get("table_body", "")
        block_text = _safe_text(block)
        has_semantic_text = (
            block_text
            and not block_text.startswith("[TABLE]")
        )

        if has_semantic_text:
            # block.text 已有语义文本 (semantic_table=True 等场景)
            parts.append(block_text)
        elif table_body:
            content = self._convert_html(table_body)
            if content:
                parts.append(content)
        # else: 仅保留占位符

        # ---- Footnote ----
        footnote = _join_list_field(block.extra.get("table_footnote", []))
        if footnote:
            parts.append(f"Footnote: {footnote}")

        return "\n".join(parts)

    def render_metadata(self, block: Block) -> Dict[str, Any]:
        table_body: str = block.extra.get("table_body", "")
        caption = _join_list_field(block.extra.get("table_caption", []))
        footnote = _join_list_field(block.extra.get("table_footnote", []))

        md = ""
        if table_body and self.prefer_markdown:
            md = html_table_to_markdown(table_body)

        return {
            "type": "table",
            "img_path": block.asset_ref or block.extra.get("img_path", ""),
            "page_idx": block.page,
            "block_id": block.block_id,
            "caption": caption,
            "footnote": footnote,
            "html": table_body,
            "markdown": md,
        }

    def _convert_html(self, html_str: str) -> str:
        """HTML 表格 → 可检索文本 (带降级链)。"""
        if self.prefer_markdown:
            md = html_table_to_markdown(html_str)
            if md:
                return md
        # 降级: 纯文本 (TSV 风格)
        plain = html_table_to_plain_text(html_str)
        if plain:
            return plain
        # 最终降级: 原始 HTML
        return html_str.strip()


class FallbackRenderer(BlockRenderer):
    """未知 block_type 的兜底渲染器, 永不抛错。"""

    def render(self, block: Block) -> str:
        text = _safe_text(block)
        if text:
            return text
        return f"[{block.block_type.value.upper()}] block_id={block.block_id}"


# ===================== HTML 表格转换 =====================

def html_table_to_markdown(html_str: str) -> str:
    """
    HTML table → Markdown table。

    尝试顺序:
        1. pandas.read_html (可选依赖, 最高质量)
        2. bs4.BeautifulSoup (可选依赖)
        3. 正则解析 (纯标准库)
    """
    md = _try_pandas_conversion(html_str)
    if md:
        return md

    md = _try_bs4_conversion(html_str)
    if md:
        return md

    return _regex_html_to_markdown(html_str)


def html_table_to_plain_text(html_str: str) -> str:
    """HTML table → 纯文本 (TSV 风格), 仅标准库。"""
    if not html_str:
        return ""
    text = html_str
    text = re.sub(r"</tr>", "\n", text, flags=re.I)
    text = re.sub(r"</t[dh]>", "\t", text, flags=re.I)
    text = re.sub(r"<[^>]+>", "", text)
    text = html_mod.unescape(text)
    return text.strip()


# ---- 可选依赖: pandas ----

def _try_pandas_conversion(html_str: str) -> str:
    """使用 pandas.read_html 将 HTML 表格转为 Markdown (可选依赖)。"""
    try:
        import pandas as pd  # type: ignore[import-untyped]
    except ImportError:
        return ""
    try:
        dfs = pd.read_html(html_str)
        if not dfs:
            return ""
        return dfs[0].to_markdown(index=False)  # type: ignore[no-any-return]
    except Exception as e:
        logger.debug("pandas 转换失败: %s", e)
        return ""


# ---- 可选依赖: BeautifulSoup ----

def _try_bs4_conversion(html_str: str) -> str:
    """使用 BeautifulSoup 解析 HTML 表格并转为 Markdown (可选依赖)。"""
    try:
        from bs4 import BeautifulSoup  # type: ignore[import-untyped]
    except ImportError:
        return ""
    try:
        soup = BeautifulSoup(html_str, "html.parser")
        table = soup.find("table")
        if not table:
            return ""
        rows: List[List[str]] = []
        for tr in table.find_all("tr"):
            cells = [td.get_text(strip=True) for td in tr.find_all(["td", "th"])]
            rows.append(cells)
        if not rows:
            return ""
        return _rows_to_markdown(rows)
    except Exception as e:
        logger.debug("bs4 转换失败: %s", e)
        return ""


# ---- 纯标准库: 正则 ----

def _regex_html_to_markdown(html_str: str) -> str:
    """使用正则解析 HTML 表格并转为 Markdown (纯标准库)。"""
    if not html_str:
        return ""
    try:
        rows: List[List[str]] = []
        for tr_match in re.finditer(r"<tr[^>]*>(.*?)</tr>", html_str, re.I | re.S):
            cells = re.findall(
                r"<t[dh][^>]*>(.*?)</t[dh]>", tr_match.group(1), re.I | re.S
            )
            cells = [html_mod.unescape(re.sub(r"<[^>]+>", "", c).strip()) for c in cells]
            rows.append(cells)
        if not rows:
            return ""
        return _rows_to_markdown(rows)
    except Exception as e:
        logger.debug("regex 转换失败: %s", e)
        return ""


def _rows_to_markdown(rows: List[List[str]]) -> str:
    """将二维行列表转为 Markdown 表格字符串。"""
    if not rows:
        return ""

    # 统一列数
    max_cols = max(len(r) for r in rows)
    for r in rows:
        while len(r) < max_cols:
            r.append("")

    # 列宽 (至少 3, 满足 Markdown 分隔符最小宽度)
    widths = [3] * max_cols
    for r in rows:
        for i, cell in enumerate(r):
            widths[i] = max(widths[i], len(cell))

    lines: List[str] = []
    # 表头
    header = rows[0]
    lines.append(
        "| " + " | ".join(cell.ljust(widths[i]) for i, cell in enumerate(header)) + " |"
    )
    # 分隔符
    lines.append(
        "| " + " | ".join("-" * widths[i] for i in range(max_cols)) + " |"
    )
    # 数据行
    for row in rows[1:]:
        lines.append(
            "| " + " | ".join(cell.ljust(widths[i]) for i, cell in enumerate(row)) + " |"
        )
    return "\n".join(lines)


# ===================== 渲染器注册表 =====================

class BlockRendererRegistry:
    """
    BlockType → BlockRenderer 映射注册表。

    - register(block_type, renderer): 注册渲染器
    - get(block_type): 查找渲染器 (未知类型返回 FallbackRenderer)
    - render(block): 安全渲染, 内部捕获所有异常
    - create_default(): 工厂方法, 创建含所有默认渲染器的注册表
    """

    def __init__(self) -> None:
        self._renderers: Dict[BlockType, BlockRenderer] = {}
        self._fallback: BlockRenderer = FallbackRenderer()

    def register(self, block_type: BlockType, renderer: BlockRenderer) -> None:
        """注册 block_type 对应的渲染器。"""
        self._renderers[block_type] = renderer
        logger.debug("注册渲染器: %s -> %s", block_type.value, type(renderer).__name__)

    def get(self, block_type: BlockType) -> BlockRenderer:
        """查找渲染器, 未知类型返回 FallbackRenderer。"""
        return self._renderers.get(block_type, self._fallback)

    def render(self, block: Block) -> RenderResult:
        """
        安全渲染单个 block, 永不抛错。

        若目标渲染器抛出异常, 自动降级到 FallbackRenderer。
        """
        renderer = self.get(block.block_type)
        try:
            return renderer.render_full(block)
        except Exception as e:
            logger.error(
                "渲染器 %s 异常 (block=%s), 降级到 Fallback: %s",
                type(renderer).__name__, block.block_id, e,
            )
            return self._fallback.render_full(block)

    @classmethod
    def create_default(
        cls,
        *,
        prefer_markdown: bool = True,
        use_heading_marker: bool = False,
    ) -> "BlockRendererRegistry":
        """
        创建包含所有默认渲染器的注册表。

        Args:
            prefer_markdown: TABLE 是否优先输出 Markdown 格式
            use_heading_marker: TITLE 是否添加 # 前缀
        """
        registry = cls()
        registry.register(BlockType.TEXT, TextRenderer())
        registry.register(BlockType.TITLE, TitleRenderer(use_heading_marker=use_heading_marker))
        registry.register(BlockType.LIST, ListRenderer())
        registry.register(BlockType.CODE, CodeRenderer())
        registry.register(BlockType.IMAGE, ImageRenderer())
        registry.register(BlockType.TABLE, TableRenderer(prefer_markdown=prefer_markdown))
        registry.register(BlockType.EQUATION, EquationRenderer())
        registry.register(BlockType.OTHER, FallbackRenderer())
        return registry
