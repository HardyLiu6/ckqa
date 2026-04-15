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
from typing import Dict, List, Optional, Set

from block_model import Block, BlockType


# ===================== 配置默认值 =====================

DEFAULT_MIN_CHARS = 5          # 最短有效文本长度
DEFAULT_REPEAT_THRESHOLD = 3   # 页眉页脚重复阈值（跨页出现次数）
DEFAULT_MAX_HEADER_LEN = 80    # 页眉页脚候选最大长度
DEFAULT_FRONT_MATTER_SCAN_PAGES = 6   # 仅扫描前几页的出版/版权噪声


# 目录页检测
_TOC_MARKER_RE = re.compile(r"^(目录|contents|table\s+of\s+contents)$", re.I)
_TOC_ENTRY_RE = re.compile(
    r"(?:^|\n)\s*(?:[-•]\s*)?(?:"
    r"第[一二三四五六七八九十百千\d]+[章节篇][^\n]{0,80}?"
    r"|(?:\d+\.)+\d*[^\n]{0,80}?"
    r"|习题[^\n]{0,20}?"
    r"|参考文献[^\n]{0,20}?"
    r")(?:[\.\s·…]{1,12}\d+)\s*$",
    re.I | re.M,
)

# 出版/版权前置页检测
_FRONT_MATTER_MARKER_RE = re.compile(
    r"(?:"
    r"图书在版编目|cip|isbn|版权(?:所有|声明)?|copyright"
    r"|出版社|出版发行|责任编辑|版次|印次|定价|开本|印张|字数"
    r"|侵权必究|邮编|联系电话|传真"
    r")",
    re.I,
)
_FRONT_MATTER_TITLE_RE = re.compile(
    r"^(?:"
    r"图书在版编目(?:\(cip\))?数据"
    r"|版权页|版权声明|出版说明|作者简介|内容简介|内容提要"
    r")$",
    re.I,
)
_FRONT_MATTER_KEEP_TITLE_RE = re.compile(
    r"^(?:"
    r"前言|序言|摘要|abstract|引言|绪论"
    r"|课程简介|课程说明|课程目标|学习目标|考核方式"
    r"|实验目的|实验要求|实验原理"
    r")$",
    re.I,
)


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


def count_toc_like_entries(text: str) -> int:
    """统计文本中类似目录项的条目数量。"""
    if not text:
        return 0
    return len(list(_TOC_ENTRY_RE.finditer(text)))


def detect_toc_pages(blocks: List[Block]) -> Set[Optional[int]]:
    """
    检测目录页。

    启发式规则：
    1. 页面出现“目录 / contents”等显式标记，且同时存在多条目录项
    2. 页面没有显式标记，但目录项密度很高，且缺少长段落正文

    返回：
        需要整体过滤掉的页码集合
    """
    page_texts: Dict[Optional[int], List[str]] = {}
    for block in blocks:
        text = (block.text or "").strip()
        if not text:
            continue
        page_texts.setdefault(block.page, []).append(text)

    toc_pages: Set[Optional[int]] = set()
    for page, texts in page_texts.items():
        joined = "\n".join(texts)
        toc_hits = count_toc_like_entries(joined)
        has_toc_marker = any(_TOC_MARKER_RE.match(text.strip()) for text in texts)
        non_toc_chars = sum(
            len(text)
            for text in texts
            if not _TOC_MARKER_RE.match(text.strip()) and count_toc_like_entries(text) == 0
        )

        if has_toc_marker and toc_hits >= 2:
            toc_pages.add(page)
            continue

        if toc_hits >= 3 and non_toc_chars <= 40:
            toc_pages.add(page)

    return toc_pages


def detect_front_matter_noise_pages(
    blocks: List[Block],
    scan_page_limit: int = DEFAULT_FRONT_MATTER_SCAN_PAGES,
) -> Set[Optional[int]]:
    """
    检测前置出版/版权噪声页。

    设计原则：
    1. 仅扫描文档最前面的少量页面，避免误伤正文
    2. 只有命中多个强信号时才整页过滤
    3. 对“前言/摘要/课程说明/实验目的”等有效导读页保持保守

    返回：
        需要整体过滤掉的页码集合
    """
    page_texts: Dict[Optional[int], List[str]] = {}
    page_title_texts: Dict[Optional[int], List[str]] = {}

    for block in blocks:
        if block.page is None or block.page < 0 or block.page > scan_page_limit:
            continue

        text = (block.text or "").strip()
        if not text:
            continue

        page_texts.setdefault(block.page, []).append(text)
        if block.block_type == BlockType.TITLE:
            page_title_texts.setdefault(block.page, []).append(text)

    noise_pages: Set[Optional[int]] = set()
    for page, texts in page_texts.items():
        joined = "\n".join(texts)
        marker_hits = len(_FRONT_MATTER_MARKER_RE.findall(joined))
        short_line_count = sum(1 for text in texts if len(text) <= 40)
        long_paragraph_chars = sum(len(text) for text in texts if len(text) >= 80)
        title_texts = page_title_texts.get(page, [])

        has_keep_title = any(
            _FRONT_MATTER_KEEP_TITLE_RE.match(text.strip())
            for text in title_texts
        )
        if has_keep_title:
            continue

        has_front_matter_title = any(
            _FRONT_MATTER_TITLE_RE.match(text.strip())
            for text in title_texts + texts
        )

        if has_front_matter_title and marker_hits >= 1:
            noise_pages.add(page)
            continue

        if marker_hits >= 3 and long_paragraph_chars < 600:
            noise_pages.add(page)
            continue

        if marker_hits >= 2 and short_line_count >= 4 and long_paragraph_chars < 180:
            noise_pages.add(page)

    return noise_pages


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


def _should_preserve_structural_title(block: Block) -> bool:
    """
    判断一个块是否应当在“重复页眉/页脚”过滤后仍被保留。

    真实教材里经常会同时出现：
    1. 章/节标题的正式 TITLE 块
    2. 同文本的跨页页眉（通常被解析为 TEXT/OTHER）

    如果仅按“短文本跨页重复”做全局过滤，正式标题也会被误杀，
    进而导致后续 heading_path 丢失章级锚点。
    """
    if block.block_type == BlockType.TITLE:
        return True
    return block.text_level is not None and block.text_level > 0


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
    2. 丢弃目录页
    3. 丢弃空白/纯符号块
    4. 丢弃过短文本（< min_chars），但保留 title/table/image/equation
    5. 跨页重复短文本过滤（页眉页脚启发式）
    6. 合并断行
    7. 连续重复块去重

    Args:
        blocks: 原始 Block 列表
        min_chars: 最短有效字符数
        repeat_threshold: 页眉页脚跨页重复阈值
        max_header_len: 页眉页脚候选最大字符长度
        merge_lines: 是否合并断行

    Returns:
        清洗后的 Block 列表
    """
    # Step 1: 检测页眉页脚和目录页
    noise_texts = detect_header_footer_texts(
        blocks, max_len=max_header_len, repeat_threshold=repeat_threshold
    )
    toc_pages = detect_toc_pages(blocks)
    front_matter_pages = detect_front_matter_noise_pages(blocks)

    cleaned: List[Block] = []
    for b in blocks:
        # Step 1a: 丢弃原生 header/footer/page_number
        if b.raw_type in ("header", "footer", "page_number"):
            continue

        # Step 1b: 丢弃目录页
        if b.page in toc_pages:
            continue

        # Step 1c: 丢弃出版/版权等前置噪声页
        if b.page in front_matter_pages:
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
            if not _should_preserve_structural_title(b):
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
