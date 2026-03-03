#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
文本清洗模块
============
对 Block 列表进行去噪、合并、去重等清洗操作，
为 GraphRAG 导出提供干净的输入。
"""

from __future__ import annotations

import re
import unicodedata
from collections import Counter
from typing import List, Optional, Set

from block_model import Block, BlockType


# ===================== 配置默认值 =====================

DEFAULT_MIN_CHARS = 5          # 最短有效文本长度
DEFAULT_REPEAT_THRESHOLD = 3   # 页眉页脚重复阈值（跨页出现次数）
DEFAULT_MAX_HEADER_LEN = 80    # 页眉页脚候选最大长度


# ===================== 单块过滤 =====================

def is_blank_or_symbol(text: str) -> bool:
    """判断文本是否为空白/纯符号/纯标点"""
    stripped = text.strip()
    if not stripped:
        return True
    # 全部由标点、符号、空白组成
    cleaned = re.sub(
        r"[\s!-/:-@\[-`{-~。，、；：？！""''【】（）《》…—·～‐–—―•‣⁃▪▸►▶→←↑↓℃°§©®™±×÷≤≥≠≈∞∑∫√∂∇]",
        "", stripped,
    )
    return len(cleaned) == 0


def is_pure_digit_noise(text: str, min_len: int = 6) -> bool:
    """
    检测纯数字噪声文本。
    MinerU 有时会将 PDF 中的装饰性元素(页面横线、花纹等) OCR 成长串数字
    (如 "99999999999")。这类文本不是有意义的内容，需要过滤。

    策略:
    - 去除空白后全部由数字组成
    - 长度 >= min_len
    - 或虽然短但是重复单一数字 (如 "9999")
    """
    stripped = text.strip()
    if not stripped:
        return False
    # 全部为数字
    if not stripped.isdigit():
        return False
    # 长数字串直接判定为噪声
    if len(stripped) >= min_len:
        return True
    # 短数字串: 如果是单一数字重复 (如 "999", "0000")
    if len(set(stripped)) == 1 and len(stripped) >= 3:
        return True
    return False


def _normalize(text: str) -> str:
    """归一化文本用于比较（去空白、小写）"""
    return re.sub(r"\s+", "", text.strip().lower())


# ===================== 页眉页脚检测 =====================

def detect_header_footer_texts(
    blocks: List[Block],
    max_len: int = DEFAULT_MAX_HEADER_LEN,
    repeat_threshold: int = DEFAULT_REPEAT_THRESHOLD,
) -> Set[str]:
    """
    检测页眉/页脚/页码文本。
    
    策略：
    1. MinerU 原生 header/footer/page_number → 直接标记
    2. 短文本跨页重复达到阈值 → 视为噪声

    Returns:
        需要过滤掉的归一化文本集合
    """
    noise_texts: Set[str] = set()

    # 1) 原生标记
    for b in blocks:
        if b.raw_type in ("header", "footer", "page_number"):
            norm = _normalize(b.text)
            if norm:
                noise_texts.add(norm)

    # 2) 跨页重复短文本
    page_text_counter: Counter = Counter()
    seen_per_page: dict[Optional[int], set] = {}

    for b in blocks:
        if b.block_type in (BlockType.OTHER, BlockType.TEXT):
            text = b.text.strip()
            if 0 < len(text) <= max_len:
                norm = _normalize(text)
                if norm:
                    page = b.page
                    if page not in seen_per_page:
                        seen_per_page[page] = set()
                    if norm not in seen_per_page[page]:
                        seen_per_page[page].add(norm)
                        page_text_counter[norm] += 1

    for norm_text, count in page_text_counter.items():
        if count >= repeat_threshold:
            noise_texts.add(norm_text)

    return noise_texts


# ===================== 合并断行 =====================

_CJK_RANGES = (
    "\u4e00-\u9fff"    # CJK Unified Ideographs
    "\u3400-\u4dbf"    # CJK Extension A
    "\uf900-\ufaff"    # CJK Compatibility
    "\U00020000-\U0002a6df"
)
_CJK_RE = re.compile(f"[{_CJK_RANGES}]")

# 中文标点 + 英文句末标点
_SENTENCE_END_RE = re.compile(r"[。！？；…\.\!\?\;]$")


def merge_broken_lines(text: str) -> str:
    """
    合并断行：
    - 行末无句末标点且下一行非空 → 合并（中文直接拼接，英文加空格）
    - 保留段落间空行
    """
    lines = text.split("\n")
    if len(lines) <= 1:
        return text

    merged: List[str] = []
    i = 0
    while i < len(lines):
        line = lines[i]

        # 空行 → 段落分隔
        if not line.strip():
            merged.append("")
            i += 1
            continue

        # 尝试与下一行合并
        while i + 1 < len(lines):
            next_line = lines[i + 1].strip()
            if not next_line:
                break  # 遇到空行，段落结束

            current_stripped = line.rstrip()
            if _SENTENCE_END_RE.search(current_stripped):
                break  # 当前行有句末标点，不合并

            # 判断中英文
            last_char = current_stripped[-1] if current_stripped else ""
            first_next = next_line[0] if next_line else ""
            if _CJK_RE.match(last_char) or _CJK_RE.match(first_next):
                line = current_stripped + next_line
            else:
                line = current_stripped + " " + next_line
            i += 1

        merged.append(line)
        i += 1

    return "\n".join(merged)


# ===================== 连续重复块去重 =====================

def deduplicate_consecutive(blocks: List[Block]) -> List[Block]:
    """去除连续重复文本块（相同文本、相同类型）"""
    if not blocks:
        return blocks

    result: List[Block] = [blocks[0]]
    for b in blocks[1:]:
        prev = result[-1]
        if b.block_type == prev.block_type and _normalize(b.text) == _normalize(prev.text):
            continue
        result.append(b)
    return result


# ===================== 主清洗流程 =====================

def clean_blocks(
    blocks: List[Block],
    min_chars: int = DEFAULT_MIN_CHARS,
    repeat_threshold: int = DEFAULT_REPEAT_THRESHOLD,
    max_header_len: int = DEFAULT_MAX_HEADER_LEN,
    merge_lines: bool = True,
) -> List[Block]:
    """
    对 Block 列表执行完整清洗流程：

    1. 丢弃页眉/页脚/页码（raw_type = header/footer/page_number）
    2. 丢弃空白/纯符号块
    3. 丢弃过短文本（< min_chars），但保留 title/table/image/equation
    4. 跨页重复短文本过滤（页眉页脚启发式）
    5. 合并断行
    6. 连续重复块去重

    Args:
        blocks: 原始 Block 列表
        min_chars: 最短有效字符数
        repeat_threshold: 页眉页脚跨页重复阈值
        max_header_len: 页眉页脚候选最大字符长度
        merge_lines: 是否合并断行

    Returns:
        清洗后的 Block 列表
    """
    # Step 1: 检测页眉页脚
    noise_texts = detect_header_footer_texts(
        blocks, max_len=max_header_len, repeat_threshold=repeat_threshold
    )

    cleaned: List[Block] = []
    for b in blocks:
        # Step 1a: 丢弃原生 header/footer/page_number
        if b.raw_type in ("header", "footer", "page_number"):
            continue

        text = b.text.strip()

        # Step 2: 丢弃空白/纯符号
        if is_blank_or_symbol(text):
            continue

        # Step 2b: 丢弃纯数字噪声 (如 MinerU OCR 装饰线为 "99999999...")
        if is_pure_digit_noise(text):
            continue

        # Step 3: 丢弃过短（但保留特殊类型）
        if b.block_type in (BlockType.TEXT, BlockType.OTHER, BlockType.LIST):
            if len(text) < min_chars:
                continue

        # Step 4: 跨页重复检测
        norm = _normalize(text)
        if norm in noise_texts:
            continue

        # Step 5: 合并断行
        if merge_lines and b.block_type in (BlockType.TEXT, BlockType.LIST):
            text = merge_broken_lines(text)
            b = Block(
                block_id=b.block_id,
                page=b.page,
                block_type=b.block_type,
                text=text,
                raw_type=b.raw_type,
                text_level=b.text_level,
                asset_ref=b.asset_ref,
                source=b.source,
                bbox=b.bbox,
                extra=b.extra,
            )

        cleaned.append(b)

    # Step 6: 连续重复去重
    cleaned = deduplicate_consecutive(cleaned)

    return cleaned
