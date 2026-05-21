#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""课程画像 hints 抽取。

从 GraphRAG 输入 `section_docs.json` 或输出 `text_units.parquet` 抽取可复用的
章节来源和关键词提示。该模块只读取原始/标准化文本单元，不读取
entities/community_reports 等派生产物。
"""

from __future__ import annotations

import json
import re
from collections import OrderedDict
from pathlib import Path
from typing import Any, Mapping, Sequence

from pydantic import BaseModel, ConfigDict, Field


_ACRONYM_RE = re.compile(
    r"(?<![A-Za-z0-9])(?:[A-Z][A-Z0-9]{1,12}|[A-Za-z][A-Za-z0-9]*(?:/[A-Za-z0-9]+)+)(?![A-Za-z0-9])"
)
_ACRONYM_WITH_EXPANSION_RE = re.compile(
    r"(?<![A-Za-z0-9])([A-Za-z][A-Za-z0-9/+.-]{1,16})[（(][A-Za-z][^）)]{1,80}[）)]"
)
_CN_WITH_ACRONYM_RE = re.compile(r"([\u4e00-\u9fff]{2,12})[（(]([A-Za-z][A-Za-z0-9/+.-]{1,16})[）)]")
_CN_QUOTED_TERM_RE = re.compile(r"[“\"']([\u4e00-\u9fffA-Za-z/]{2,16})[”\"']")
_CN_TRIGGERED_SHORT_TERM_RE = re.compile(
    r"(?:使用|采用|通过|利用|基于|引入|实现|支持|称为|名为|取名为)([\u4e00-\u9fff]{2,8})(?:的|方式|机制|算法|程序|技术|系统|管理|控制|处理)"
)
_TECH_TERM_RE = re.compile(
    r"[\u4e00-\u9fffA-Za-z/]{2,18}"
    r"(?:系统|机制|算法|管理|结构|方式|程序|处理|控制|接口|通道|寄存器|缓存|索引|模型|函数|协议|调度|转换)"
)
_COORDINATED_SHORT_TERM_RE = re.compile(r"([\u4e00-\u9fff]{2,8})[和或与、]([\u4e00-\u9fff]{2,8})")
_METADATA_RE = re.compile(r"^([A-Za-z_][A-Za-z0-9_]*):\s*(.*?)\.?\s*$")
_COURSE_ID_RE = re.compile(r"^course_id:\s*(.*?)\.?\s*$", re.MULTILINE)
_HEADING_RE = re.compile(r"^heading_path_text:\s*(.*?)\.?\s*$", re.MULTILINE)
_LOW_INFORMATION_KEYWORDS = {
    "操作",
    "系统",
    "操作系统",
    "计算机",
    "计算机系统",
    "计算机操作",
    "计算机操作系统",
    "作用",
    "功能",
    "所需",
    "相同",
    "更多",
    "简单",
    "什么样",
    "哪种",
    "此时",
    "这种",
    "由于",
    "如果",
    "因此",
    "描述",
    "多个",
    "控制",
    "管理",
    "方式",
    "处理",
}
_NOISY_PREFIXES = (
    "本教材",
    "本书",
    "前面",
    "如果",
    "例如",
    "为了",
    "由于",
    "因此",
    "但是",
    "并",
    "以及",
    "对于",
    "一般情况下",
    "它们",
    "其",
    "一个",
)


class CourseProfileHint(BaseModel):
    heading: str = Field(min_length=1)
    keywords: list[str] = Field(default_factory=list)
    sourceType: str
    sourceRef: str = ""
    score: float = 0.0


class CourseProfileHintsResult(BaseModel):
    items: list[CourseProfileHint] = Field(default_factory=list)
    source_counts: dict[str, int] = Field(default_factory=dict, alias="sourceCounts")

    model_config = ConfigDict(populate_by_name=True)


def extract_course_profile_hints(
    *,
    course_id: str,
    section_docs_paths: Sequence[str | Path] | None = None,
    text_units_paths: Sequence[str | Path] | None = None,
    data_dirs: Sequence[str | Path] | None = None,
    seed_keywords: Sequence[str] | None = None,
    max_hints: int = 24,
) -> CourseProfileHintsResult:
    """抽取课程画像 hints。

    参数均为显式路径，便于 Java、CLI、测试和未来流水线复用。
    """

    candidates: list[CourseProfileHint] = []
    source_counts: dict[str, int] = {"section_docs": 0, "text_units": 0}
    seeds = [_normalize_keyword(item) for item in (seed_keywords or []) if _normalize_keyword(item)]

    for raw_path in section_docs_paths or []:
        path = Path(raw_path)
        if path.is_file():
            items = _load_section_docs(path, course_id=course_id, seed_keywords=seeds)
            source_counts["section_docs"] += len(items)
            candidates.extend(items)

    parquet_paths = [Path(item) for item in text_units_paths or []]
    for raw_dir in data_dirs or []:
        parquet_paths.append(Path(raw_dir) / "text_units.parquet")
    for path in parquet_paths:
        if path.is_file():
            items = _load_text_units(path, course_id=course_id, seed_keywords=seeds)
            source_counts["text_units"] += len(items)
            candidates.extend(items)

    return CourseProfileHintsResult(
        items=_rank_and_deduplicate(candidates, max_hints=max(1, max_hints)),
        sourceCounts={key: value for key, value in source_counts.items() if value > 0},
    )


def _load_section_docs(path: Path, *, course_id: str, seed_keywords: Sequence[str]) -> list[CourseProfileHint]:
    raw = json.loads(path.read_text(encoding="utf-8"))
    rows = raw if isinstance(raw, list) else raw.get("documents", []) if isinstance(raw, dict) else []
    hints: list[CourseProfileHint] = []
    for index, row in enumerate(rows):
        if not isinstance(row, Mapping):
            continue
        text = _row_text(row)
        if not _matches_course(row, text, course_id):
            continue
        heading = _row_heading(row, text)
        if _is_ignored_heading(heading):
            continue
        keywords = _extract_keywords(heading, text, seed_keywords=seed_keywords)
        if heading and keywords:
            hints.append(CourseProfileHint(
                heading=heading,
                keywords=keywords,
                sourceType="section_docs",
                sourceRef=str(row.get("id") or row.get("source_id") or index),
                score=_score_hint(heading, keywords, seed_keywords=seed_keywords),
            ))
    return hints


def _load_text_units(path: Path, *, course_id: str, seed_keywords: Sequence[str]) -> list[CourseProfileHint]:
    try:
        import pandas as pd  # type: ignore
    except ImportError as exc:  # pragma: no cover - 取决于运行环境
        raise RuntimeError("读取 text_units.parquet 需要 pandas/pyarrow") from exc

    frame = pd.read_parquet(path)
    hints: list[CourseProfileHint] = []
    for index, row in frame.iterrows():
        row_map = row.to_dict()
        text = str(row_map.get("text") or "")
        if not _matches_course(row_map, text, course_id):
            continue
        heading = _row_heading(row_map, text)
        if _is_ignored_heading(heading):
            continue
        keywords = _extract_keywords(heading, text, seed_keywords=seed_keywords)
        if heading and keywords:
            hints.append(CourseProfileHint(
                heading=heading,
                keywords=keywords,
                sourceType="text_units",
                sourceRef=str(row_map.get("human_readable_id") or row_map.get("id") or index),
                score=_score_hint(heading, keywords, seed_keywords=seed_keywords),
            ))
    return hints


def _matches_course(row: Mapping[str, Any], text: str, course_id: str) -> bool:
    row_course_id = str(row.get("course_id") or row.get("courseId") or "").strip().rstrip(".")
    if not row_course_id:
        match = _COURSE_ID_RE.search(text)
        row_course_id = match.group(1).strip().rstrip(".") if match else ""
    return not row_course_id or row_course_id == course_id


def _row_text(row: Mapping[str, Any]) -> str:
    value = row.get("text")
    if value is None:
        value = row.get("content")
    if isinstance(value, Mapping):
        value = value.get("text") or value.get("markdown") or json.dumps(value, ensure_ascii=False)
    return str(value or "")


def _row_heading(row: Mapping[str, Any], text: str) -> str:
    heading = (
        row.get("heading_path_text")
        or row.get("headingPathText")
        or row.get("heading_path")
        or row.get("title")
        or ""
    )
    if not str(heading).strip():
        match = _HEADING_RE.search(text)
        heading = match.group(1).strip().rstrip(".") if match else ""
    if isinstance(heading, list):
        heading = " > ".join(str(item).strip() for item in heading if str(item).strip())
    return _normalize_heading(str(heading))


def _extract_keywords(heading: str, text: str, *, seed_keywords: Sequence[str]) -> list[str]:
    keywords: "OrderedDict[str, None]" = OrderedDict()
    anchored_keywords: "OrderedDict[str, None]" = OrderedDict()
    seed_set = set(seed_keywords)
    for part in _heading_parts(heading):
        _add_keyword(keywords, part, seed_keywords=seed_set)
        for child in _derive_child_terms(part):
            _add_keyword(keywords, child, seed_keywords=seed_set)
            for grandchild in _derive_child_terms(child):
                _add_keyword(keywords, grandchild, seed_keywords=seed_set)
    for chinese, acronym in _CN_WITH_ACRONYM_RE.findall(text):
        _add_keyword(keywords, chinese, seed_keywords=seed_set)
        _add_keyword(keywords, acronym, seed_keywords=seed_set)
        _add_keyword(anchored_keywords, chinese, seed_keywords=seed_set)
        _add_keyword(anchored_keywords, acronym, seed_keywords=seed_set)
    for acronym in _ACRONYM_WITH_EXPANSION_RE.findall(text):
        _add_keyword(keywords, acronym, seed_keywords=seed_set)
        _add_keyword(anchored_keywords, acronym, seed_keywords=seed_set)
    for match in _CN_QUOTED_TERM_RE.findall(text):
        _add_keyword(keywords, match, seed_keywords=seed_set)
        _add_keyword(anchored_keywords, match, seed_keywords=seed_set)
    for match in _CN_TRIGGERED_SHORT_TERM_RE.findall(text):
        _add_keyword(keywords, match, seed_keywords=seed_set)
        _add_keyword(anchored_keywords, match, seed_keywords=seed_set)
    for match in _ACRONYM_RE.findall(text):
        _add_keyword(keywords, match, seed_keywords=seed_set)
    for match in _TECH_TERM_RE.findall(text):
        term = _trim_noise_prefix(match)
        for child in _derive_child_terms(term):
            _add_keyword(keywords, child, seed_keywords=seed_set)
        _add_keyword(keywords, term, seed_keywords=seed_set)
    for left, right in _COORDINATED_SHORT_TERM_RE.findall(text):
        for term in (left, right):
            for child in _derive_child_terms(term):
                _add_keyword(keywords, child, seed_keywords=seed_set)
            _add_keyword(keywords, term, seed_keywords=seed_set)
            _add_keyword(anchored_keywords, term, seed_keywords=seed_set)
    return list(OrderedDict.fromkeys([*anchored_keywords.keys(), *_rank_keywords(list(keywords.keys()))]))[:16]


def _heading_parts(heading: str) -> list[str]:
    parts: list[str] = []
    for raw_part in re.split(r"\s*>\s*", heading):
        part = re.sub(r"^第[一二三四五六七八九十百零\d]+章\s*", "", raw_part.strip())
        part = re.sub(r"^\d+(?:\.\d+)*\s*", "", part)
        part = part.strip(" .。:：-")
        if part:
            parts.append(part)
    return parts


def _derive_child_terms(term: str) -> list[str]:
    children: list[str] = []
    for separator in ("和", "与", "及", "、", "/"):
        if separator in term:
            children.extend(item for item in term.split(separator) if item)
    for marker in ("常涉及", "涉及", "是一种", "一种", "用于", "提高"):
        if marker in term:
            children.append(term.split(marker)[-1])
    for suffix in ("机构", "处理程序", "程序", "系统", "方式", "机制"):
        if term.endswith(suffix) and len(term) > len(suffix) + 1:
            children.append(term[: -len(suffix)])
    return children


def _rank_and_deduplicate(candidates: Sequence[CourseProfileHint], *, max_hints: int) -> list[CourseProfileHint]:
    merged: "OrderedDict[str, CourseProfileHint]" = OrderedDict()
    for item in sorted(candidates, key=lambda hint: (-hint.score, hint.heading, hint.sourceType, hint.sourceRef)):
        key = item.heading
        if key not in merged:
            merged[key] = item
            continue
        existing = merged[key]
        keywords = _rank_keywords([*existing.keywords, *item.keywords])
        merged[key] = CourseProfileHint(
            heading=existing.heading,
            keywords=keywords[:32],
            sourceType=existing.sourceType,
            sourceRef=existing.sourceRef,
            score=max(existing.score, item.score),
        )
    ranked = list(merged.values())
    selected: list[CourseProfileHint] = []
    selected_headings: set[str] = set()
    section_counts: dict[str, int] = {}
    coverage_slots = max(1, min(max_hints // 4, 6)) if max_hints >= 4 else 0
    primary_limit = max_hints - coverage_slots
    for item in ranked:
        section_key = _section_key(item.heading)
        if section_counts.get(section_key, 0) >= 1:
            continue
        selected.append(item)
        selected_headings.add(item.heading)
        section_counts[section_key] = section_counts.get(section_key, 0) + 1
        if len(selected) >= primary_limit:
            break
    if coverage_slots > 0:
        for item in sorted(
            (item for item in ranked if item.heading not in selected_headings and _coverage_score(item) > 0),
            key=lambda hint: (-_coverage_score(hint), -hint.score, hint.heading),
        ):
            selected.append(item)
            selected_headings.add(item.heading)
            if len(selected) >= max_hints:
                return selected
    if len(selected) >= max_hints:
        return selected
    for item in ranked:
        if item.heading in selected_headings:
            continue
        selected.append(item)
        if len(selected) >= max_hints:
            break
    return selected


def _coverage_score(item: CourseProfileHint) -> float:
    score = 0.0
    for keyword in item.keywords:
        priority = _keyword_priority(keyword)
        if priority >= 5.0:
            score += 1.0
        elif priority >= 3.0:
            score += 0.25
    return score


def _section_key(heading: str) -> str:
    parts = _heading_parts(heading)
    if len(parts) >= 2:
        return " > ".join(parts[:2])
    return parts[0] if parts else _normalize_heading(heading)


def _score_hint(heading: str, keywords: Sequence[str], *, seed_keywords: Sequence[str]) -> float:
    seed_set = set(seed_keywords)
    heading_depth = max(1, len(_heading_parts(heading)))
    score = heading_depth * 2.0
    acronym_score = 0.0
    term_score = 0.0
    short_concept_score = 0.0
    if _normalize_heading(heading) in {"前言", "目录", "参考文献"}:
        score -= 4.0
    for keyword in keywords:
        if keyword in seed_set or keyword in _LOW_INFORMATION_KEYWORDS:
            continue
        if _ACRONYM_RE.fullmatch(keyword):
            acronym_score += _score_acronym(keyword)
        elif re.search(r"[A-Za-z/]", keyword):
            term_score += 2.0
        elif len(keyword) >= 4:
            term_score += 1.5
        else:
            term_score += 0.8
        if len(keyword) <= 4 and re.search(r"[\u4e00-\u9fff]", keyword):
            short_concept_score += 1.0
        if keyword.endswith(("系统", "机制", "算法", "管理", "结构", "方式", "程序", "通道", "寄存器", "转换")):
            term_score += 0.5
    score += min(acronym_score, 7.0) + min(term_score, 18.0) + min(short_concept_score, 3.0)
    if "习题" in _heading_parts(heading):
        score -= 3.0
    return round(max(0.1, score), 3)


def _score_acronym(keyword: str) -> float:
    if "/" in keyword:
        return 2.5
    compact = re.sub(r"[^A-Za-z0-9]", "", keyword)
    if not compact:
        return 0.0
    if any(char.isdigit() for char in compact):
        return 0.5 if len(compact) <= 4 else 0.8
    if len(compact) <= 2:
        return 0.4
    if len(compact) == 3:
        return 1.8
    return 1.2


def _rank_keywords(keywords: Sequence[str]) -> list[str]:
    ordered = list(OrderedDict.fromkeys(keyword for keyword in keywords if keyword))
    return [
        keyword
        for _, index, keyword in sorted(
            (-_keyword_priority(keyword), index, keyword)
            for index, keyword in enumerate(ordered)
        )
    ]


def _keyword_priority(keyword: str) -> float:
    score = 0.0
    has_technical_suffix = keyword.endswith(
        ("系统", "机制", "算法", "管理", "结构", "方式", "程序", "通道", "寄存器", "转换", "控制")
    )
    if keyword in _LOW_INFORMATION_KEYWORDS:
        return -10.0
    if _ACRONYM_RE.fullmatch(keyword):
        compact = re.sub(r"[^A-Za-z0-9]", "", keyword)
        if "/" in keyword:
            score += 5.0
        elif len(compact) == 3:
            score += 4.2
        elif len(compact) <= 2:
            score -= 1.0
        else:
            score += 1.0
    if "/" in keyword:
        score += 1.5
    if re.search(r"[\u4e00-\u9fff]", keyword):
        if len(keyword) <= 4 and not has_technical_suffix:
            score += 8.0
        if re.search(r"[A-Za-z/]", keyword):
            score += 1.0
    if keyword.endswith("程序"):
        score += 8.0
    elif has_technical_suffix:
        score += 3.0
    if keyword.startswith(("的", "了", "在", "当", "该", "被", "与", "并", "其")):
        score -= 4.0
    if re.fullmatch(r"[A-Z]{1,2}\d?", keyword):
        score -= 3.0
    return score


def _add_keyword(
    keywords: "OrderedDict[str, None]",
    keyword: str,
    *,
    seed_keywords: set[str] | None = None,
) -> None:
    normalized = _normalize_keyword(keyword)
    if (
        not normalized
        or _looks_like_metadata(normalized)
        or normalized in _LOW_INFORMATION_KEYWORDS
        or normalized in (seed_keywords or set())
    ):
        return
    keywords[normalized] = None


def _normalize_heading(value: str) -> str:
    value = re.sub(r"\s+", " ", value.strip()).strip(" .。")
    return value


def _normalize_keyword(value: str) -> str:
    value = _trim_noise_prefix(re.sub(r"\s+", " ", str(value).strip()).strip(" .。:：,，;；"))
    if len(value) < 2 or len(value) > 32:
        return ""
    if value.lower() in {"textbook", "course_id", "source_file", "page_start", "page_end"}:
        return ""
    if _is_noisy_keyword(value):
        return ""
    return value


def _trim_noise_prefix(value: str) -> str:
    for prefix in ("通过", "进行", "用于", "提高", "涉及", "实现", "采用", "是一种", "一种"):
        if value.startswith(prefix) and len(value) > len(prefix) + 1:
            return value[len(prefix):]
    return value


def _looks_like_metadata(value: str) -> bool:
    return bool(_METADATA_RE.match(value))


def _is_ignored_heading(heading: str) -> bool:
    parts = _heading_parts(heading)
    return any(part in {"参考文献", "References", "Bibliography"} for part in parts)


def _is_noisy_keyword(value: str) -> bool:
    if "常涉" in value or value.endswith("涉"):
        return True
    if re.match(r"^[A-Za-z][\u4e00-\u9fff]", value) and "/" not in value:
        return True
    if value.startswith(("程序I", "器的I", "这", "由于", "分为", "先将", "只需向", "示出了")):
        return True
    if value.startswith("对I") and "/" not in value:
        return True
    if value.startswith((
        "便",
        "则",
        "若",
        "可将",
        "可以",
        "需要",
        "有关",
        "整个",
    )) and len(value) > 3:
        return True
    if value.startswith("是DMA"):
        return True
    if value.startswith(("的", "了", "在", "当", "该", "被", "与", "并", "其", "每一个")) and len(value) > 3:
        return True
    if value.endswith(("和", "与")):
        return True
    if "章" in value and re.search(r"第[一二三四五六七八九十百零\d]+章", value):
        return True
    return len(value) > 8 and value.startswith(_NOISY_PREFIXES)
