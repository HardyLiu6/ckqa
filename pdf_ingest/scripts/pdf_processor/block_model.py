#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
Block 数据模型
==============
将 MinerU content_list.json 解析产物统一为内部 Block 结构，
供清洗、聚合、导出等后续流程使用。
"""

from __future__ import annotations

import json
import re
from dataclasses import dataclass, field, asdict
from enum import Enum
from pathlib import Path
from typing import Optional, List, Dict, Any


# ===================== Block 类型枚举 =====================

class BlockType(Enum):
    """统一块类型"""
    TEXT = "text"
    TITLE = "title"
    TABLE = "table"
    IMAGE = "image"
    EQUATION = "equation"
    LIST = "list"
    CODE = "code"
    OTHER = "other"


# MinerU content_list type → BlockType 映射
_MINERU_TYPE_MAP: Dict[str, BlockType] = {
    "text": BlockType.TEXT,
    "image": BlockType.IMAGE,
    "table": BlockType.TABLE,
    "equation": BlockType.EQUATION,
    "list": BlockType.LIST,
    "code": BlockType.CODE,
    "header": BlockType.OTHER,       # 页眉，后续清洗丢弃
    "footer": BlockType.OTHER,       # 页脚，后续清洗丢弃
    "page_number": BlockType.OTHER,  # 页码，后续清洗丢弃
}


# ===================== Block 数据类 =====================

@dataclass
class Block:
    """统一块模型"""
    block_id: str                          # 稳定 ID，如 "os:3:5"
    page: Optional[int]                    # 页码 (0-based from content_list)
    block_type: BlockType                  # 统一块类型
    text: str                              # 块文本（表格/图片会文本化）
    raw_type: str = ""                     # MinerU 原始 type
    text_level: Optional[int] = None       # 标题层级 (1/2/3...)，仅 title 类型
    asset_ref: Optional[str] = None        # 关联资产路径 (图片/表格图等)
    source: str = ""                       # 源文件名
    bbox: Optional[List[float]] = None     # 边界框 [x0, y0, x1, y1]
    extra: Dict[str, Any] = field(default_factory=dict)  # 额外元数据

    def to_dict(self) -> Dict[str, Any]:
        d = asdict(self)
        d["block_type"] = self.block_type.value
        return d


# ===================== 解析函数 =====================

def _extract_text_from_table(raw: Dict[str, Any], semantic_table: bool = False) -> str:
    """
    从 MinerU table 块提取文本。

    - 优先使用 table_body (HTML table)
    - 可选 semantic_table: 将每行转为 "列名=值; ..." 描述
    """
    body_html: str = raw.get("table_body", "")
    if not body_html:
        return "[TABLE] (empty)"

    # 简单 HTML → 纯文本: 去标签，保留单元格分隔
    # 快速处理：用正则
    text = body_html
    # 行分隔
    text = re.sub(r"</tr>", "\n", text, flags=re.I)
    # 单元格分隔
    text = re.sub(r"</t[dh]>", "\t", text, flags=re.I)
    # 去除剩余标签
    text = re.sub(r"<[^>]+>", "", text)
    text = text.strip()

    if semantic_table:
        text = _semanticize_table(body_html, text)

    caption_parts = raw.get("table_caption", [])
    if caption_parts:
        caption = " ".join(str(c) for c in caption_parts if c)
        if caption.strip():
            text = f"[TABLE CAPTION] {caption.strip()}\n{text}"

    return text


def _semanticize_table(body_html: str, plain_fallback: str) -> str:
    """
    将 HTML 表格转换为语义行描述：
      "列A=值1; 列B=值2; ..."
    若解析失败退回 plain_fallback。
    """
    try:
        # 快速解析 HTML table rows
        rows: List[List[str]] = []
        for tr_match in re.finditer(r"<tr[^>]*>(.*?)</tr>", body_html, re.I | re.S):
            cells = re.findall(r"<t[dh][^>]*>(.*?)</t[dh]>", tr_match.group(1), re.I | re.S)
            cells = [re.sub(r"<[^>]+>", "", c).strip() for c in cells]
            rows.append(cells)

        if len(rows) < 2:
            return plain_fallback

        headers = rows[0]
        lines = []
        for row in rows[1:]:
            pairs = []
            for i, val in enumerate(row):
                col_name = headers[i] if i < len(headers) else f"col{i}"
                if val:
                    pairs.append(f"{col_name}={val}")
            if pairs:
                lines.append("; ".join(pairs))
        return "\n".join(lines) if lines else plain_fallback
    except Exception:
        return plain_fallback


def _extract_text_from_image(raw: Dict[str, Any]) -> str:
    """图片 → 占位符文本"""
    img_path = raw.get("img_path", "")
    page = raw.get("page_idx", "?")
    return f"[IMAGE] ref={img_path} page={page}"


def _extract_text_from_list(raw: Dict[str, Any]) -> str:
    """列表块 → 文本"""
    items = raw.get("list_items", [])
    if not items:
        return ""
    return "\n".join(f"- {item}" for item in items)


def _extract_text_from_code(raw: Dict[str, Any]) -> str:
    """代码块 → 文本"""
    body = raw.get("code_body", "")
    lang = raw.get("guess_lang", "")
    caption_parts = raw.get("code_caption", [])
    caption = " ".join(str(c) for c in caption_parts if c).strip()
    parts = []
    if caption:
        parts.append(f"[CODE CAPTION] {caption}")
    parts.append(f"```{lang}\n{body}\n```")
    return "\n".join(parts)


def _extract_text_from_equation(raw: Dict[str, Any]) -> str:
    """公式块 → 文本"""
    text = raw.get("text", "")
    fmt = raw.get("text_format", "")
    if fmt == "latex" or "$" in text:
        return f"$${text}$$"
    return text


def _is_title_block(raw: Dict[str, Any]) -> bool:
    """
    判断是否为标题块：
    1. 有 text_level 字段
    2. raw type == 'text' 且 text_level 存在
    """
    return raw.get("text_level") is not None


def _flatten_mineru_text_content(value: Any) -> str:
    """
    将 MinerU v2 中嵌套的富文本结构拍平成字符串。

    常见输入形态：
    - {"type": "text", "content": "..."}
    - {"type": "equation_inline", "content": "\\equiv"}
    - {"item_content": [...]}
    - [{"type": "text", ...}, ...]
    """
    if value is None:
        return ""

    if isinstance(value, str):
        return value

    if isinstance(value, (int, float)):
        return str(value)

    if isinstance(value, list):
        return "".join(_flatten_mineru_text_content(item) for item in value)

    if isinstance(value, dict):
        if "content" in value:
            return _flatten_mineru_text_content(value.get("content"))

        parts: List[str] = []
        for key, item in value.items():
            if key.endswith("_content"):
                parts.append(_flatten_mineru_text_content(item))
        return "".join(parts)

    return str(value)


def _normalize_text_list(items: Any) -> List[str]:
    """将 MinerU v2 的文本数组归一化为字符串列表。"""
    if not items:
        return []

    if not isinstance(items, list):
        items = [items]

    texts: List[str] = []
    for item in items:
        text = _flatten_mineru_text_content(item).strip()
        if text:
            texts.append(text)
    return texts


def _normalize_list_items(items: Any) -> List[str]:
    """将 MinerU v2 的 list_items 归一化为字符串列表。"""
    if not items:
        return []

    normalized: List[str] = []
    for item in items:
        text = _flatten_mineru_text_content(item).strip()
        if text:
            normalized.append(text)
    return normalized


def _extract_image_path(value: Any) -> str:
    """从 MinerU v2 的 image_source 结构中提取图片路径。"""
    if isinstance(value, dict):
        return str(value.get("path", "")).strip()
    return ""


def _normalize_mineru_v2_block(raw: Dict[str, Any], page_idx: int) -> Dict[str, Any]:
    """
    将 MinerU v2 单个 block 归一化为旧版扁平结构，
    供现有 parse_content_list 继续处理。
    """
    raw_type = raw.get("type", "unknown")
    content = raw.get("content", {})
    normalized: Dict[str, Any] = {
        "bbox": raw.get("bbox"),
        "page_idx": page_idx,
    }

    if raw_type == "title":
        normalized.update({
            "type": "text",
            "text": _flatten_mineru_text_content(content.get("title_content", "")).strip(),
            "text_level": content.get("level"),
        })
    elif raw_type == "paragraph":
        normalized.update({
            "type": "text",
            "text": _flatten_mineru_text_content(content.get("paragraph_content", "")).strip(),
        })
    elif raw_type == "page_header":
        normalized.update({
            "type": "header",
            "text": _flatten_mineru_text_content(content.get("page_header_content", "")).strip(),
        })
    elif raw_type == "page_footer":
        normalized.update({
            "type": "footer",
            "text": _flatten_mineru_text_content(content.get("page_footer_content", "")).strip(),
        })
    elif raw_type == "page_number":
        normalized.update({
            "type": "page_number",
            "text": _flatten_mineru_text_content(content.get("page_number_content", "")).strip(),
        })
    elif raw_type == "list":
        normalized.update({
            "type": "list",
            "list_items": _normalize_list_items(content.get("list_items", [])),
        })
    elif raw_type == "image":
        normalized.update({
            "type": "image",
            "img_path": _extract_image_path(content.get("image_source")),
            "image_caption": _normalize_text_list(content.get("image_caption", [])),
            "image_footnote": _normalize_text_list(content.get("image_footnote", [])),
        })
    elif raw_type == "table":
        normalized.update({
            "type": "table",
            "img_path": _extract_image_path(content.get("image_source")),
            "table_body": content.get("html", ""),
            "table_caption": _normalize_text_list(content.get("table_caption", [])),
            "table_footnote": _normalize_text_list(content.get("table_footnote", [])),
        })
    elif raw_type == "equation_interline":
        normalized.update({
            "type": "equation",
            "text": str(content.get("math_content", "")).strip(),
            "text_format": str(content.get("math_type", "")).strip(),
            "img_path": _extract_image_path(content.get("image_source")),
        })
    elif raw_type == "code":
        normalized.update({
            "type": "code",
            "code_body": _flatten_mineru_text_content(content.get("code_content", "")).strip(),
            "guess_lang": str(content.get("code_language", "")).strip(),
            "code_caption": _normalize_text_list(content.get("code_caption", [])),
        })
    elif raw_type == "algorithm":
        normalized.update({
            "type": "code",
            "code_body": _flatten_mineru_text_content(content.get("algorithm_content", "")).strip(),
            "guess_lang": "algorithm",
            "code_caption": _normalize_text_list(content.get("algorithm_caption", [])),
        })
    else:
        normalized.update({
            "type": raw_type,
            "text": _flatten_mineru_text_content(content).strip(),
        })

    return normalized


def _normalize_content_list_data(data: List[Any]) -> List[Dict[str, Any]]:
    """兼容旧版扁平 content_list 与新版按页分组的 content_list_v2。"""
    if not data:
        return []

    first_item = data[0]
    if isinstance(first_item, dict):
        return data

    if not isinstance(first_item, list):
        raise ValueError(
            f"content_list.json 格式错误: 列表元素需为 dict 或 list，实际 {type(first_item).__name__}"
        )

    normalized: List[Dict[str, Any]] = []
    for page_idx, page_blocks in enumerate(data):
        if not isinstance(page_blocks, list):
            raise ValueError(
                f"content_list.json 格式错误: 第 {page_idx} 页应为 list，实际 {type(page_blocks).__name__}"
            )
        for item in page_blocks:
            if not isinstance(item, dict):
                raise ValueError(
                    "content_list.json 格式错误: page block 应为 dict，"
                    f"实际 {type(item).__name__}"
                )
            normalized.append(_normalize_mineru_v2_block(item, page_idx))
    return normalized


def parse_content_list(
    content_list: List[Dict[str, Any]],
    course_id: str,
    source_file: str = "book.pdf",
    semantic_table: bool = False,
) -> List[Block]:
    """
    将 MinerU content_list.json 解析为统一 Block 列表。

    Args:
        content_list: content_list.json 的完整内容 (Python list)
        course_id: 课程 ID
        source_file: 源 PDF 文件名
        semantic_table: 是否启用表格语义化

    Returns:
        Block 列表 (保持原始顺序)
    """
    blocks: List[Block] = []

    for idx, raw in enumerate(content_list):
        raw_type = raw.get("type", "unknown")
        page_idx = raw.get("page_idx")
        bbox = raw.get("bbox")
        extra: Dict[str, Any] = {}

        # ---- 判断 block_type & 提取文本 ----
        if _is_title_block(raw):
            block_type = BlockType.TITLE
            text = raw.get("text", "")
            text_level = raw.get("text_level")
        elif raw_type == "text":
            block_type = BlockType.TEXT
            text = raw.get("text", "")
            text_level = None
        elif raw_type == "table":
            block_type = BlockType.TABLE
            text = _extract_text_from_table(raw, semantic_table=semantic_table)
            text_level = None
            extra = {
                "table_body": raw.get("table_body", ""),
                "table_caption": raw.get("table_caption", []),
                "table_footnote": raw.get("table_footnote", []),
            }
        elif raw_type == "image":
            block_type = BlockType.IMAGE
            text = _extract_text_from_image(raw)
            text_level = None
            extra = {
                "img_path": raw.get("img_path", ""),
                "image_caption": raw.get("image_caption", []),
                "image_footnote": raw.get("image_footnote", []),
            }
        elif raw_type == "list":
            block_type = BlockType.LIST
            text = _extract_text_from_list(raw)
            text_level = None
            extra = {"list_items": raw.get("list_items", [])}
        elif raw_type == "code":
            block_type = BlockType.CODE
            text = _extract_text_from_code(raw)
            text_level = None
            extra = {
                "code_body": raw.get("code_body", ""),
                "guess_lang": raw.get("guess_lang", ""),
                "code_caption": raw.get("code_caption", []),
            }
        elif raw_type == "equation":
            block_type = BlockType.EQUATION
            text = _extract_text_from_equation(raw)
            text_level = None
            extra = {"text_format": raw.get("text_format", "")}
        else:
            # header / footer / page_number / unknown
            block_type = _MINERU_TYPE_MAP.get(raw_type, BlockType.OTHER)
            text = raw.get("text", "")
            text_level = None

        # ---- 资产引用 ----
        asset_ref = raw.get("img_path")

        # ---- 构造 block_id ----
        block_id = f"{course_id}:{page_idx}:{idx}"

        blocks.append(Block(
            block_id=block_id,
            page=page_idx,
            block_type=block_type,
            text=text,
            raw_type=raw_type,
            text_level=text_level,
            asset_ref=asset_ref,
            source=source_file,
            bbox=bbox,
            extra=extra,
        ))

    return blocks


def load_content_list_file(path: str | Path) -> List[Dict[str, Any]]:
    """安全加载并归一化 content_list.json 文件。"""
    path = Path(path)
    if not path.exists():
        raise FileNotFoundError(f"content_list.json 不存在: {path}")
    with open(path, "r", encoding="utf-8") as f:
        data = json.load(f)
    if not isinstance(data, list):
        raise ValueError(f"content_list.json 格式错误: 期望 list, 实际 {type(data).__name__}")
    return _normalize_content_list_data(data)
