#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
Prompt 调优样本构建脚本
======================

从已经完成 MinerU 解析、数据清洗、格式规范化的课程文档中，
构建适用于 GraphRAG prompt tuning 的代表性样本集。

设计原则：
1. 不重新定义上游课程标准输出 schema。
2. 优先消费标准化 JSON 或其 GraphRAG 投影格式。
3. 不读取原始 PDF 或 MinerU 原始 block。
4. 以“样本筛选、分层抽样、统计输出”为目标，而不是重复上游清洗。
"""

from __future__ import annotations

import argparse
import hashlib
import importlib.util
import json
import random
import re
import sys
from collections import Counter, defaultdict
from dataclasses import dataclass, field
from datetime import datetime
from pathlib import Path
from typing import Any, Callable, Dict, Iterable, List, Optional, Sequence, Tuple


REPO_ROOT = Path(__file__).resolve().parents[2]
PROJECT_ROOT = Path(__file__).resolve().parents[1]
DEFAULT_OUTPUT_FILE = PROJECT_ROOT / "data" / "prompt_tuning_samples" / "prompt_tuning_samples.json"
DEFAULT_PRIMARY_INPUT = PROJECT_ROOT / "input" / "section_docs.json"

ALLOWED_FILENAMES = (
    "normalized_docs.json",
    "section_docs.json",
    "page_docs.json",
)
FORMAT_PRIORITY = {
    "normalized_docs.json": 0,
    "section_docs.json": 1,
    "page_docs.json": 2,
}
FALLBACK_DISCOVERY_PLAN: Tuple[Tuple[str, Tuple[str, ...]], ...] = (
    ("graphrag_pipeline/data/normalized_docs", ("normalized_docs.json",)),
    ("graphrag_pipeline/tmp_validate", ("normalized_docs.json",)),
    ("graphrag_pipeline/data/section_docs", ("section_docs.json", "page_docs.json")),
    ("pdf_ingest/output/graphrag", ("normalized_docs.json", "section_docs.json", "page_docs.json")),
    ("pdf_ingest/output", ("normalized_docs.json", "section_docs.json", "page_docs.json")),
)

DEFAULT_MAX_SAMPLES = 120
DEFAULT_MIN_LENGTH = 120
DEFAULT_MAX_LENGTH = 2600
TARGET_TEXT_LENGTH = 900

_HEADING_NOISE_RE = re.compile(r"(?:\.{2,}|…{2,}|·{2,})\s*\d+$|\s+\d+\s*$")
_PLACEHOLDER_RE = re.compile(r"\[(?:IMAGE|TABLE)\]")
_ONLY_NOISE_RE = re.compile(r"^[\W_\d\s]+$", re.UNICODE)
_WORD_RE = re.compile(r"[\u4e00-\u9fffA-Za-z][\u4e00-\u9fffA-Za-z0-9\-/+_]*")
_DIRECTORY_ITEM_RE = re.compile(r"(?:^|\n)\s*(?:[-*]?\s*)?(?:\d+(?:\.\d+)+|第[一二三四五六七八九十百零\d]+[章节])")

_METADATA_FIELDS_TO_FLATTEN = [
    "id",
    "course_id",
    "source_file",
    "document_type",
    "chapter",
    "section",
    "subsection",
    "heading_level",
    "heading_path",
    "doc_unit",
    "section_level",
    "page_start",
    "page_end",
    "page_no",
    "has_table",
    "has_equation",
    "has_image",
    "table_count",
    "equation_count",
    "image_count",
    "chunk_index",
    "chunk_total",
    "chunk_char_count",
    "chunk_strategy",
]

_PUBLICATION_NOISE_SIGNALS = (
    "isbn",
    "cip",
    "责任编辑",
    "出版发行",
    "印刷单位",
    "新华书店",
    "中国版本图书馆",
    "电子邮箱",
    "版次",
    "印次",
)
_COURSE_REQUIREMENT_SIGNALS = (
    "课程目标",
    "教学要求",
    "学习目标",
    "考核方式",
    "成绩构成",
    "课程简介",
    "先修",
    "课程安排",
    "培养目标",
)
_EXPERIMENT_SIGNALS = (
    "实验目的",
    "实验内容",
    "实验步骤",
    "实验要求",
    "实验环境",
    "实验原理",
    "实验任务",
    "实验说明",
    "实验报告",
)
_ASSIGNMENT_SIGNALS = (
    "题目要求",
    "作业要求",
    "提交要求",
    "报告要求",
    "评分标准",
    "回答下列问题",
    "请完成",
    "请回答",
    "请分析",
    "请证明",
)
_EXERCISE_PROMPT_SIGNALS = (
    "为什么",
    "何谓",
    "试说明",
    "试分析",
    "试比较",
    "试证明",
    "试画出",
    "试简述",
    "请说明",
    "请分析",
    "请证明",
    "请完成",
)
_EXPERIMENT_TITLE_SIGNALS = (
    "实验",
    "上机",
    "课程项目",
    "项目实践",
    "实验报告",
)
_ASSIGNMENT_TITLE_SIGNALS = (
    "习题",
    "思考题",
    "练习题",
    "复习题",
    "作业要求",
    "课程作业",
    "平时作业",
    "大作业",
    "实验报告",
    "测验",
    "测试",
    "考试",
    "试题",
)
_FORMULA_SIGNALS = (
    "定义为",
    "定义:",
    "定义：",
    "公式",
    "定理",
    "判定条件",
    "表示为",
    "可写为",
)
_METHOD_SIGNALS = (
    "算法",
    "方法",
    "策略",
    "流程",
    "步骤",
    "机制",
    "实现",
    "伪代码",
)
_QUESTION_ITEM_RE = re.compile(r"(?:^|\n)\s*(?:[-*•]\s*)?\d+[\.\)）、]")

_SAMPLE_TYPE_ORDER = (
    "chapter_concept_explanation",
    "definition_or_formula",
    "algorithm_or_method",
    "experiment_instruction",
    "assignment_requirement",
    "course_requirement",
    "reference_material",
    "generic_explanation",
)
_SAMPLE_TYPE_PRIORITY = {
    "chapter_concept_explanation": 5.8,
    "definition_or_formula": 6.4,
    "algorithm_or_method": 6.2,
    "experiment_instruction": 6.5,
    "assignment_requirement": 6.0,
    "course_requirement": 5.4,
    "reference_material": 4.8,
    "generic_explanation": 4.0,
}
_HEADING_LEVEL_BUCKET_LABELS = {
    1: "h1",
    2: "h2",
    3: "h3",
    4: "h4_plus",
}


def _load_normalized_helpers() -> tuple[Callable[[Dict[str, Any]], List[str]], Callable[[List[str], int, Optional[str]], Dict[str, Optional[str]]]]:
    """
    动态加载 pdf_ingest 中的标准文档校验逻辑。

    若加载失败，退回到最小兼容实现，但优先复用现有字段契约。
    """
    module_path = REPO_ROOT / "pdf_ingest" / "scripts" / "pdf_processor" / "normalized_document.py"
    if not module_path.exists():
        return _fallback_validate_normalized_document_dict, _fallback_resolve_heading_fields

    spec = importlib.util.spec_from_file_location(
        "ckqa_normalized_document",
        module_path,
    )
    if spec is None or spec.loader is None:
        return _fallback_validate_normalized_document_dict, _fallback_resolve_heading_fields

    module = importlib.util.module_from_spec(spec)
    sys.modules[spec.name] = module
    spec.loader.exec_module(module)

    validate_func = getattr(module, "validate_normalized_document_dict", _fallback_validate_normalized_document_dict)
    resolve_func = getattr(module, "_resolve_heading_fields", _fallback_resolve_heading_fields)
    return validate_func, resolve_func


def _fallback_validate_normalized_document_dict(payload: Dict[str, Any]) -> List[str]:
    errors: List[str] = []
    required_fields = (
        "id",
        "source_file",
        "document_type",
        "course_id",
        "chapter",
        "section",
        "subsection",
        "heading_level",
        "heading_path",
        "content",
        "page_start",
        "page_end",
        "metadata",
    )
    for field in required_fields:
        if field not in payload:
            errors.append(f"缺少必填字段: {field}")
    return errors


def _fallback_resolve_heading_fields(
    heading_path: List[str],
    heading_level: int,
    doc_unit: Optional[str],
) -> Dict[str, Optional[str]]:
    fields: Dict[str, Optional[str]] = {"chapter": None, "section": None, "subsection": None}
    if doc_unit == "page" or not heading_path:
        return fields

    start_level = max(1, heading_level - len(heading_path) + 1)
    for index, item in enumerate(heading_path):
        level = start_level + index
        if level == 1:
            fields["chapter"] = item
        elif level == 2:
            fields["section"] = item
        elif level == 3:
            fields["subsection"] = item
    return fields


VALIDATE_NORMALIZED_DOCUMENT_DICT, RESOLVE_HEADING_FIELDS = _load_normalized_helpers()


@dataclass
class SourceSelection:
    mode: str
    selected_root: Optional[str]
    selected_format: Optional[str]
    files: List[Path]


@dataclass
class AdaptedDocument:
    source_doc_id: str
    source_file: str
    document_type: str
    course_id: str
    chapter: Optional[str]
    section: Optional[str]
    subsection: Optional[str]
    heading_path: List[str]
    heading_level: int
    text: str
    page_start: int
    page_end: int
    metadata: Dict[str, Any]
    source_format: str
    source_path: str
    guessed_sample_type: str = ""
    text_length: int = 0
    base_score: float = 0.0
    random_key: float = 0.0
    similarity_signature: frozenset[str] = field(default_factory=frozenset)

    @property
    def heading_path_text(self) -> str:
        return " > ".join(self.heading_path)

    @property
    def title(self) -> str:
        return self.heading_path[-1] if self.heading_path else ""


def _parse_doc_types(value: Optional[Sequence[str]]) -> Optional[set[str]]:
    if not value:
        return None
    resolved: set[str] = set()
    for item in value:
        for part in str(item).split(","):
            part = part.strip().lower()
            if part:
                resolved.add(part)
    return resolved or None


def _sort_paths(paths: Iterable[Path]) -> List[Path]:
    return sorted(
        set(paths),
        key=lambda p: (FORMAT_PRIORITY.get(p.name, 99), str(p)),
    )


def _collect_files_from_root(root: Path, filenames: Sequence[str]) -> List[Path]:
    if not root.exists():
        return []
    matches: List[Path] = []
    for filename in filenames:
        matches.extend(root.rglob(filename))
    return _sort_paths(matches)


def _discover_files_from_user_input(input_path: Path) -> SourceSelection:
    if not input_path.exists():
        raise FileNotFoundError(f"输入路径不存在: {input_path}")

    if input_path.is_file():
        return SourceSelection(
            mode="user_provided",
            selected_root=str(input_path.parent.resolve()),
            selected_format=input_path.name,
            files=[input_path.resolve()],
        )

    discovered = _collect_files_from_root(input_path, ALLOWED_FILENAMES)
    if discovered:
        chosen_name = discovered[0].name
        selected = [path for path in discovered if path.name == chosen_name]
        return SourceSelection(
            mode="user_provided",
            selected_root=str(input_path.resolve()),
            selected_format=chosen_name,
            files=_sort_paths(selected),
        )

    fallback = _sort_paths(input_path.rglob("*.json"))
    if not fallback:
        raise FileNotFoundError(f"未在输入目录中发现 JSON 文件: {input_path}")

    return SourceSelection(
        mode="user_provided_fallback",
        selected_root=str(input_path.resolve()),
        selected_format="mixed_json",
        files=fallback,
    )


def discover_input_files(input_dir: Optional[str]) -> SourceSelection:
    """
    发现输入文件。

    优先级：
    1. 显式传入的 --input_dir
    2. 固定主输入 graphrag_pipeline/input/section_docs.json
    3. fallback 到其它标准化 JSON 导出目录

    不回退到原始 PDF 或 MinerU block。
    """
    if input_dir:
        return _discover_files_from_user_input(Path(input_dir).expanduser().resolve())

    if DEFAULT_PRIMARY_INPUT.exists():
        return SourceSelection(
            mode="default_primary_input",
            selected_root=str(DEFAULT_PRIMARY_INPUT.parent.resolve()),
            selected_format=DEFAULT_PRIMARY_INPUT.name,
            files=[DEFAULT_PRIMARY_INPUT.resolve()],
        )

    for root_rel, filenames in FALLBACK_DISCOVERY_PLAN:
        root = REPO_ROOT / root_rel
        files = _collect_files_from_root(root, filenames)
        if files:
            selected_format = files[0].name
            selected_files = [path for path in files if path.name == selected_format]
            return SourceSelection(
                mode="fallback_discover",
                selected_root=str(root.resolve()),
                selected_format=selected_format,
                files=_sort_paths(selected_files),
            )

    raise FileNotFoundError(
        "未发现可用的标准化 JSON 输入。"
        f"已尝试默认主输入 {DEFAULT_PRIMARY_INPUT}，以及 fallback 目录中的 "
        "normalized_docs.json / section_docs.json / page_docs.json。"
        "本脚本不会回退读取原始 PDF 或 MinerU 原始 block。"
    )


def load_json_records(path: Path) -> List[Dict[str, Any]]:
    payload = json.loads(path.read_text(encoding="utf-8"))

    if isinstance(payload, dict):
        payload = [payload]

    if not isinstance(payload, list):
        raise ValueError(f"{path} 不是 JSON 数组或单条对象")

    records: List[Dict[str, Any]] = []
    for index, item in enumerate(payload, start=1):
        if not isinstance(item, dict):
            raise ValueError(f"{path} 第 {index} 条记录不是对象")
        records.append(item)
    return records


def _safe_int(value: Any) -> Optional[int]:
    if isinstance(value, bool):
        return None
    if isinstance(value, int):
        return value
    if isinstance(value, str) and value.strip().isdigit():
        return int(value.strip())
    return None


def _clean_string(value: Any) -> Optional[str]:
    if not isinstance(value, str):
        return None
    value = value.strip()
    return value or None


def _clean_heading_path(value: Any) -> List[str]:
    if not isinstance(value, list):
        return []
    items: List[str] = []
    for item in value:
        cleaned = _clean_string(item)
        if cleaned:
            cleaned = _HEADING_NOISE_RE.sub("", cleaned).strip()
            if cleaned:
                items.append(cleaned)
    return items


def _strip_course_prefix(title: Optional[str], course_id: Optional[str]) -> Optional[str]:
    cleaned = _clean_string(title)
    if not cleaned:
        return None
    if course_id:
        pattern = re.compile(rf"^{re.escape(course_id)}\s*[-_:：]\s*", re.IGNORECASE)
        cleaned = pattern.sub("", cleaned).strip()
    return cleaned or None


def _build_heading_path_text(value: Any) -> str:
    if isinstance(value, list):
        return " > ".join(str(item).strip() for item in value if str(item).strip())
    if isinstance(value, str):
        return value.strip()
    return ""


def _flatten_record(record: Dict[str, Any]) -> Dict[str, Any]:
    flat = dict(record)
    metadata = record.get("metadata", {})
    if isinstance(metadata, dict):
        for field in _METADATA_FIELDS_TO_FLATTEN:
            if field in metadata and field not in flat:
                flat[field] = metadata[field]
    if "heading_path_text" not in flat:
        heading_path_text = _build_heading_path_text(flat.get("heading_path"))
        if heading_path_text:
            flat["heading_path_text"] = heading_path_text
    return flat


def _infer_record_format(record: Dict[str, Any]) -> str:
    if "content" in record and "heading_path" in record:
        return "normalized"
    if "text" in record or "title" in record:
        return "graphrag_projection"
    return "unknown"


def _extract_primary_text(record: Dict[str, Any]) -> str:
    for field in ("content", "text"):
        value = record.get(field, "")
        if isinstance(value, str) and value.strip():
            return value.strip()
    return ""


def _derive_heading_path(flat: Dict[str, Any]) -> List[str]:
    heading_path = _clean_heading_path(flat.get("heading_path"))
    if heading_path:
        return heading_path

    path_candidates = [
        _clean_string(flat.get("chapter")),
        _clean_string(flat.get("section")),
        _clean_string(flat.get("subsection")),
    ]
    heading_path = [item for item in path_candidates if item]
    if heading_path:
        return heading_path

    title = _strip_course_prefix(
        flat.get("title"),
        _clean_string(flat.get("course_id")),
    )
    if title:
        return [title]

    return []


def _derive_heading_level(flat: Dict[str, Any], heading_path: List[str]) -> int:
    heading_level = _safe_int(flat.get("heading_level"))
    if heading_level and heading_level >= 1:
        return heading_level

    section_level = _safe_int(flat.get("section_level"))
    if section_level is not None and section_level >= 0:
        return section_level + 1

    if heading_path:
        return len(heading_path)
    return 1


def _derive_structure_fields(
    flat: Dict[str, Any],
    heading_path: List[str],
    heading_level: int,
) -> Dict[str, Optional[str]]:
    doc_unit = _clean_string(flat.get("doc_unit"))
    resolved = RESOLVE_HEADING_FIELDS(heading_path, heading_level, doc_unit)

    return {
        "chapter": _clean_string(flat.get("chapter")) or resolved.get("chapter"),
        "section": _clean_string(flat.get("section")) or resolved.get("section"),
        "subsection": _clean_string(flat.get("subsection")) or resolved.get("subsection"),
    }


def _build_synthetic_doc_id(
    flat: Dict[str, Any],
    heading_path: List[str],
    page_start: Optional[int],
    page_end: Optional[int],
) -> str:
    course_id = _clean_string(flat.get("course_id")) or "unknown_course"
    source_file = _clean_string(flat.get("source_file")) or "unknown_file"
    heading_text = " > ".join(heading_path) if heading_path else (_strip_course_prefix(flat.get("title"), course_id) or "untitled")
    page_text = f"p{page_start or 'x'}-{page_end or 'x'}"
    payload = f"{course_id}|{source_file}|{heading_text}|{page_text}"
    digest = hashlib.sha1(payload.encode("utf-8")).hexdigest()[:12]
    return f"{course_id}:{source_file}:{digest}"


def _merge_metadata_summary(flat: Dict[str, Any]) -> Dict[str, Any]:
    metadata = dict(flat.get("metadata", {})) if isinstance(flat.get("metadata"), dict) else {}
    for key in (
        "doc_unit",
        "section_level",
        "has_table",
        "has_equation",
        "has_image",
        "table_count",
        "equation_count",
        "image_count",
        "chunk_index",
        "chunk_total",
        "chunk_char_count",
        "chunk_strategy",
    ):
        if key in flat and key not in metadata:
            metadata[key] = flat[key]
    return metadata


def adapt_record(record: Dict[str, Any], source_path: Path) -> tuple[Optional[AdaptedDocument], List[str]]:
    flat = _flatten_record(record)
    source_format = _infer_record_format(flat)

    if source_format == "unknown":
        return None, ["无法识别记录格式"]

    if source_format == "normalized":
        errors = VALIDATE_NORMALIZED_DOCUMENT_DICT(record)
        if errors:
            return None, errors

        metadata = dict(record.get("metadata", {}))
        adapted = AdaptedDocument(
            source_doc_id=record["id"],
            source_file=record["source_file"],
            document_type=record["document_type"],
            course_id=record["course_id"],
            chapter=record["chapter"],
            section=record["section"],
            subsection=record["subsection"],
            heading_path=_clean_heading_path(record["heading_path"]),
            heading_level=record["heading_level"],
            text=str(record["content"]).strip(),
            page_start=record["page_start"],
            page_end=record["page_end"],
            metadata=metadata,
            source_format=source_format,
            source_path=str(source_path),
        )
        return _validate_adapted_document(adapted)

    heading_path = _derive_heading_path(flat)
    heading_level = _derive_heading_level(flat, heading_path)
    structure = _derive_structure_fields(flat, heading_path, heading_level)
    page_start = _safe_int(flat.get("page_start"))
    page_end = _safe_int(flat.get("page_end"))
    metadata = _merge_metadata_summary(flat)
    source_doc_id = _clean_string(flat.get("id")) or _build_synthetic_doc_id(flat, heading_path, page_start, page_end)

    adapted = AdaptedDocument(
        source_doc_id=source_doc_id,
        source_file=_clean_string(flat.get("source_file")) or "",
        document_type=_clean_string(flat.get("document_type")) or "unknown",
        course_id=_clean_string(flat.get("course_id")) or "",
        chapter=structure["chapter"],
        section=structure["section"],
        subsection=structure["subsection"],
        heading_path=heading_path,
        heading_level=heading_level,
        text=_extract_primary_text(flat),
        page_start=page_start or 0,
        page_end=page_end or 0,
        metadata=metadata,
        source_format=source_format,
        source_path=str(source_path),
    )
    return _validate_adapted_document(adapted)


def _validate_adapted_document(doc: AdaptedDocument) -> tuple[Optional[AdaptedDocument], List[str]]:
    errors: List[str] = []
    if not doc.source_doc_id.strip():
        errors.append("缺少 id/source_doc_id")
    if not doc.source_file.strip():
        errors.append("缺少 source_file")
    if not doc.course_id.strip():
        errors.append("缺少 course_id")
    if not doc.heading_path:
        errors.append("缺少 heading_path")
    if doc.heading_level < 1:
        errors.append("heading_level 非法")
    if not doc.text.strip():
        errors.append("缺少 content/text")
    if doc.page_start < 1 or doc.page_end < 1 or doc.page_end < doc.page_start:
        errors.append("page_start/page_end 非法")

    if errors:
        return None, errors

    doc.text_length = len(doc.text)
    return doc, []


def _normalize_text_for_similarity(text: str) -> str:
    text = text.lower()
    text = re.sub(r"\s+", "", text)
    text = re.sub(r"[^\u4e00-\u9fffa-z0-9]+", "", text)
    return text


def _build_similarity_signature(text: str) -> frozenset[str]:
    normalized = _normalize_text_for_similarity(text)
    if not normalized:
        return frozenset()
    if len(normalized) <= 24:
        return frozenset({normalized})

    window = 12
    limit = min(len(normalized) - window + 1, 480)
    step = max(1, limit // 80)
    signature = {
        normalized[index:index + window]
        for index in range(0, limit, step)
        if normalized[index:index + window]
    }
    return frozenset(signature)


def _jaccard_similarity(left: frozenset[str], right: frozenset[str]) -> float:
    if not left or not right:
        return 0.0
    intersection = len(left & right)
    union = len(left | right)
    if union == 0:
        return 0.0
    return intersection / union


def _count_signal_hits(text: str, signals: Sequence[str]) -> int:
    return sum(1 for signal in signals if signal in text)


def _build_sample_type_context(doc: AdaptedDocument) -> tuple[str, str]:
    title_context_parts = [doc.title]
    for field in (doc.chapter, doc.section, doc.subsection):
        cleaned = _clean_string(field)
        if cleaned and cleaned not in title_context_parts:
            title_context_parts.append(cleaned)
    title_context = "\n".join(part for part in title_context_parts if part)
    body_context = doc.text[:1200]
    return title_context, body_context


def _is_experiment_instruction(doc: AdaptedDocument, title_context: str, body_context: str) -> bool:
    if doc.document_type == "lab":
        return True

    leaf_title = doc.title or title_context
    title_hits = _count_signal_hits(leaf_title, _EXPERIMENT_TITLE_SIGNALS)
    body_hits = _count_signal_hits(body_context, _EXPERIMENT_SIGNALS)

    if title_hits >= 1:
        return body_hits >= 1 or doc.document_type in {"slides", "notes"}

    return False


def _is_assignment_requirement(doc: AdaptedDocument, title_context: str, body_context: str) -> bool:
    if doc.document_type == "exam":
        return True

    leaf_title = doc.title or title_context
    title_hits = _count_signal_hits(leaf_title, _ASSIGNMENT_TITLE_SIGNALS)
    body_hits = _count_signal_hits(body_context, _ASSIGNMENT_SIGNALS)

    if title_hits >= 1:
        if body_hits >= 1 or doc.document_type in {"slides", "notes", "syllabus"}:
            return True
        return _looks_like_exercise_question_set(body_context)

    return False


def _looks_like_exercise_question_set(text: str) -> bool:
    question_item_count = len(_QUESTION_ITEM_RE.findall(text))
    prompt_hits = _count_signal_hits(text, _EXERCISE_PROMPT_SIGNALS)

    if question_item_count >= 4:
        return True
    if question_item_count >= 2 and prompt_hits >= 2:
        return True
    return False


def guess_sample_type(doc: AdaptedDocument) -> str:
    title_context, body_context = _build_sample_type_context(doc)
    text_head = f"{title_context}\n{body_context}"
    text_head_lower = text_head.lower()

    if _is_experiment_instruction(doc, title_context, body_context):
        return "experiment_instruction"

    if _is_assignment_requirement(doc, title_context, body_context):
        return "assignment_requirement"

    if doc.document_type == "syllabus" or any(signal in text_head for signal in _COURSE_REQUIREMENT_SIGNALS):
        return "course_requirement"

    if (
        any(signal in text_head for signal in _FORMULA_SIGNALS)
        or bool(doc.metadata.get("has_equation"))
        or "公式：" in text_head
    ):
        return "definition_or_formula"

    if any(signal in text_head for signal in _METHOD_SIGNALS):
        return "algorithm_or_method"

    if doc.document_type == "reference" or "参考文献" in text_head_lower or "参考资料" in text_head:
        return "reference_material"

    if doc.heading_level <= 2 or doc.chapter or doc.section:
        return "chapter_concept_explanation"

    return "generic_explanation"


def compute_base_score(doc: AdaptedDocument) -> float:
    score = _SAMPLE_TYPE_PRIORITY.get(doc.guessed_sample_type, 4.0)

    distance = abs(doc.text_length - TARGET_TEXT_LENGTH)
    score += max(0.0, 2.4 - distance / 450)

    if doc.document_type != "unknown":
        score += 0.6
    if len(doc.heading_path) >= 2:
        score += 0.8
    if doc.heading_level <= 2:
        score += 0.6
    if doc.page_end > doc.page_start:
        score += 0.2

    for field in ("has_table", "has_equation", "has_image"):
        if doc.metadata.get(field):
            score += 0.2

    return round(score, 4)


def _is_directory_residual(doc: AdaptedDocument) -> bool:
    head = f"{doc.title}\n{doc.text[:700]}"
    numbered_entries = len(_DIRECTORY_ITEM_RE.findall(head))
    dot_leader_count = head.count(".....") + head.count("……")
    if "目录" in head[:80] and (numbered_entries >= 3 or dot_leader_count >= 2):
        return True
    if numbered_entries >= 6 and dot_leader_count >= 1:
        return True
    return False


def _is_publication_noise(doc: AdaptedDocument) -> bool:
    text_head = doc.text[:1200].lower()
    signal_hits = sum(1 for signal in _PUBLICATION_NOISE_SIGNALS if signal in text_head)
    return signal_hits >= 3


def _is_media_fragment(doc: AdaptedDocument) -> bool:
    placeholder_count = len(_PLACEHOLDER_RE.findall(doc.text))
    meaningful_words = len(_WORD_RE.findall(doc.text))
    if placeholder_count >= 1 and meaningful_words < 20:
        return True
    if placeholder_count >= 2 and doc.text_length < 180:
        return meaningful_words < 35
    return False


def _is_decorative_noise(doc: AdaptedDocument) -> bool:
    stripped = re.sub(r"\s+", "", doc.text)
    if not stripped:
        return True
    if _ONLY_NOISE_RE.match(stripped):
        return True
    meaningful_words = len(_WORD_RE.findall(doc.text))
    if meaningful_words < 12 and doc.text_length < 120:
        return True
    return False


def _looks_like_heading_fragment(doc: AdaptedDocument, min_length: int) -> bool:
    lines = [line.strip() for line in doc.text.splitlines() if line.strip()]
    if len(lines) <= 2 and doc.text_length < max(min_length, 80):
        return True
    if doc.title and doc.text.startswith(doc.title) and doc.text_length < max(min_length + 40, 120):
        return True
    return False


def filter_document(
    doc: AdaptedDocument,
    min_length: int,
    max_length: int,
    allowed_doc_types: Optional[set[str]],
) -> Optional[str]:
    if allowed_doc_types and doc.document_type not in allowed_doc_types:
        return "doc_type_filtered"
    if doc.text_length < min_length:
        return "too_short"
    if doc.text_length > max_length:
        return "too_long"
    if _is_decorative_noise(doc):
        return "decorative_noise"
    if _is_directory_residual(doc):
        return "toc_residual"
    if _is_publication_noise(doc):
        return "publication_noise"
    if _is_media_fragment(doc):
        return "media_fragment"
    if _looks_like_heading_fragment(doc, min_length):
        return "heading_fragment"
    return None


def deduplicate_candidates(candidates: Sequence[AdaptedDocument]) -> tuple[List[AdaptedDocument], int]:
    kept: List[AdaptedDocument] = []
    exact_seen: set[str] = set()
    buckets: Dict[Tuple[str, str, str], List[AdaptedDocument]] = defaultdict(list)
    removed = 0

    ordered = sorted(
        candidates,
        key=lambda doc: (-doc.base_score, doc.random_key, doc.source_doc_id),
    )

    for doc in ordered:
        normalized_text = _normalize_text_for_similarity(doc.text)
        if normalized_text in exact_seen:
            removed += 1
            continue

        bucket_key = (
            doc.course_id,
            doc.document_type,
            _normalize_text_for_similarity(doc.title)[:40],
        )

        is_duplicate = False
        for existing in buckets[bucket_key]:
            similarity = _jaccard_similarity(doc.similarity_signature, existing.similarity_signature)
            if similarity >= 0.88:
                is_duplicate = True
                break

        if is_duplicate:
            removed += 1
            continue

        exact_seen.add(normalized_text)
        buckets[bucket_key].append(doc)
        kept.append(doc)

    return kept, removed


def _heading_level_bucket(level: int) -> str:
    if level >= 4:
        return _HEADING_LEVEL_BUCKET_LABELS[4]
    return _HEADING_LEVEL_BUCKET_LABELS.get(level, "h_unknown")


def _chapter_bucket(doc: AdaptedDocument) -> str:
    return doc.chapter or doc.section or doc.title or "__root__"


def _is_near_duplicate_to_selected(
    doc: AdaptedDocument,
    selected_buckets: Dict[Tuple[str, str, str], List[AdaptedDocument]],
) -> bool:
    bucket_key = (
        doc.course_id,
        doc.document_type,
        _normalize_text_for_similarity(doc.title)[:40],
    )
    for existing in selected_buckets.get(bucket_key, []):
        if _jaccard_similarity(doc.similarity_signature, existing.similarity_signature) >= 0.88:
            return True
    return False


def _adjusted_selection_score(
    doc: AdaptedDocument,
    sample_type_counter: Counter,
    doc_type_counter: Counter,
    heading_level_counter: Counter,
    chapter_counter: Counter,
    combo_counter: Counter,
) -> float:
    heading_bucket = _heading_level_bucket(doc.heading_level)
    chapter_bucket = _chapter_bucket(doc)
    combo_key = (doc.guessed_sample_type, doc.document_type, heading_bucket)

    score = doc.base_score
    score += 1.0 if sample_type_counter[doc.guessed_sample_type] == 0 else 0.0
    score += 0.6 if doc_type_counter[doc.document_type] == 0 else 0.0
    score += 0.45 if heading_level_counter[heading_bucket] == 0 else 0.0
    score += 0.35 if chapter_counter[chapter_bucket] == 0 else 0.0
    score += 0.4 if combo_counter[combo_key] == 0 else 0.0

    score -= sample_type_counter[doc.guessed_sample_type] * 0.95
    score -= doc_type_counter[doc.document_type] * 0.55
    score -= heading_level_counter[heading_bucket] * 0.28
    score -= chapter_counter[chapter_bucket] * 0.18
    score -= combo_counter[combo_key] * 0.22
    return score


def select_samples(
    candidates: Sequence[AdaptedDocument],
    max_samples: int,
) -> List[AdaptedDocument]:
    if max_samples <= 0 or not candidates:
        return []

    remaining = list(candidates)
    selected: List[AdaptedDocument] = []
    selected_ids: set[str] = set()
    selected_buckets: Dict[Tuple[str, str, str], List[AdaptedDocument]] = defaultdict(list)

    sample_type_counter: Counter = Counter()
    doc_type_counter: Counter = Counter()
    heading_level_counter: Counter = Counter()
    chapter_counter: Counter = Counter()
    combo_counter: Counter = Counter()

    def register(doc: AdaptedDocument) -> None:
        selected.append(doc)
        selected_ids.add(doc.source_doc_id)
        bucket_key = (
            doc.course_id,
            doc.document_type,
            _normalize_text_for_similarity(doc.title)[:40],
        )
        selected_buckets[bucket_key].append(doc)
        sample_type_counter[doc.guessed_sample_type] += 1
        doc_type_counter[doc.document_type] += 1
        heading_level_counter[_heading_level_bucket(doc.heading_level)] += 1
        chapter_counter[_chapter_bucket(doc)] += 1
        combo_counter[(doc.guessed_sample_type, doc.document_type, _heading_level_bucket(doc.heading_level))] += 1

    for sample_type in _SAMPLE_TYPE_ORDER:
        pool = [
            doc for doc in remaining
            if doc.guessed_sample_type == sample_type and doc.source_doc_id not in selected_ids
        ]
        if not pool:
            continue
        pool.sort(key=lambda doc: (-doc.base_score, doc.random_key, doc.source_doc_id))
        for doc in pool:
            if _is_near_duplicate_to_selected(doc, selected_buckets):
                continue
            register(doc)
            break
        if len(selected) >= max_samples:
            return selected

    while len(selected) < max_samples:
        best_doc: Optional[AdaptedDocument] = None
        best_score: Optional[float] = None

        for doc in remaining:
            if doc.source_doc_id in selected_ids:
                continue
            if _is_near_duplicate_to_selected(doc, selected_buckets):
                continue

            score = _adjusted_selection_score(
                doc,
                sample_type_counter=sample_type_counter,
                doc_type_counter=doc_type_counter,
                heading_level_counter=heading_level_counter,
                chapter_counter=chapter_counter,
                combo_counter=combo_counter,
            )
            if best_score is None or score > best_score:
                best_doc = doc
                best_score = score

        if best_doc is None:
            break

        register(best_doc)

    return selected


def build_metadata_summary(doc: AdaptedDocument) -> Dict[str, Any]:
    metadata = doc.metadata if isinstance(doc.metadata, dict) else {}
    summary: Dict[str, Any] = {}
    for key in (
        "doc_unit",
        "section_level",
        "has_table",
        "has_equation",
        "has_image",
        "table_count",
        "equation_count",
        "image_count",
        "chunk_index",
        "chunk_total",
        "chunk_char_count",
        "chunk_strategy",
    ):
        value = metadata.get(key)
        if value is not None:
            summary[key] = value
    return summary


def make_sample_id(doc: AdaptedDocument, index: int) -> str:
    payload = f"{doc.source_doc_id}|{doc.guessed_sample_type}|{doc.page_start}|{doc.page_end}"
    digest = hashlib.sha1(payload.encode("utf-8")).hexdigest()[:10]
    return f"pts-{index:04d}-{digest}"


def sample_to_dict(doc: AdaptedDocument, index: int) -> Dict[str, Any]:
    return {
        "sample_id": make_sample_id(doc, index),
        "source_doc_id": doc.source_doc_id,
        "source_file": doc.source_file,
        "document_type": doc.document_type,
        "course_id": doc.course_id,
        "chapter": doc.chapter,
        "section": doc.section,
        "heading_path": list(doc.heading_path),
        "heading_level": doc.heading_level,
        "text": doc.text,
        "text_length": doc.text_length,
        "page_start": doc.page_start,
        "page_end": doc.page_end,
        "guessed_sample_type": doc.guessed_sample_type,
        "metadata_summary": build_metadata_summary(doc),
    }


def build_stats(
    source_selection: SourceSelection,
    input_documents_total: int,
    validation_failed: int,
    validation_fail_reasons: Counter,
    filtered_reasons: Counter,
    deduplicated_count: int,
    selected_docs: Sequence[AdaptedDocument],
    source_format_counter: Counter,
) -> Dict[str, Any]:
    document_type_counter = Counter(doc.document_type for doc in selected_docs)
    sample_type_counter = Counter(doc.guessed_sample_type for doc in selected_docs)
    heading_level_counter = Counter(_heading_level_bucket(doc.heading_level) for doc in selected_docs)

    return {
        "input_strategy": {
            "mode": source_selection.mode,
            "selected_root": source_selection.selected_root,
            "selected_format": source_selection.selected_format,
            "files_count": len(source_selection.files),
        },
        "input_documents_total": input_documents_total,
        "validation_failed": validation_failed,
        "validation_failure_breakdown": dict(validation_fail_reasons),
        "filtered_total": sum(filtered_reasons.values()),
        "filtered_breakdown": dict(filtered_reasons),
        "deduplicated_count": deduplicated_count,
        "final_sample_count": len(selected_docs),
        "source_format_distribution": dict(source_format_counter),
        "document_type_distribution": dict(document_type_counter),
        "guessed_sample_type_distribution": dict(sample_type_counter),
        "heading_level_distribution": dict(heading_level_counter),
    }


def build_samples(
    source_selection: SourceSelection,
    max_samples: int,
    min_length: int,
    max_length: int,
    allowed_doc_types: Optional[set[str]],
    random_seed: int,
) -> Dict[str, Any]:
    rng = random.Random(random_seed)

    input_documents_total = 0
    validation_failed = 0
    validation_fail_reasons: Counter = Counter()
    filtered_reasons: Counter = Counter()
    source_format_counter: Counter = Counter()

    candidates: List[AdaptedDocument] = []

    for file_path in source_selection.files:
        try:
            records = load_json_records(file_path)
        except Exception as exc:
            validation_failed += 1
            validation_fail_reasons[f"文件读取失败: {file_path.name}"] += 1
            print(f"[警告] 跳过文件 {file_path}: {exc}", file=sys.stderr)
            continue

        input_documents_total += len(records)

        for record in records:
            adapted, errors = adapt_record(record, file_path)
            if adapted is None:
                validation_failed += 1
                if not errors:
                    validation_fail_reasons["unknown_validation_failure"] += 1
                for error in errors or ["unknown_validation_failure"]:
                    validation_fail_reasons[error] += 1
                continue

            adapted.guessed_sample_type = guess_sample_type(adapted)
            adapted.base_score = compute_base_score(adapted)
            adapted.random_key = rng.random()
            adapted.similarity_signature = _build_similarity_signature(adapted.text)
            source_format_counter[adapted.source_format] += 1

            filter_reason = filter_document(
                adapted,
                min_length=min_length,
                max_length=max_length,
                allowed_doc_types=allowed_doc_types,
            )
            if filter_reason:
                filtered_reasons[filter_reason] += 1
                continue

            candidates.append(adapted)

    deduplicated_candidates, deduplicated_count = deduplicate_candidates(candidates)
    selected_docs = select_samples(deduplicated_candidates, max_samples=max_samples)

    output = {
        "schema_version": "v1",
        "generated_at": datetime.now().isoformat(timespec="seconds"),
        "samples": [sample_to_dict(doc, index) for index, doc in enumerate(selected_docs, start=1)],
        "stats": build_stats(
            source_selection=source_selection,
            input_documents_total=input_documents_total,
            validation_failed=validation_failed,
            validation_fail_reasons=validation_fail_reasons,
            filtered_reasons=filtered_reasons,
            deduplicated_count=deduplicated_count,
            selected_docs=selected_docs,
            source_format_counter=source_format_counter,
        ),
        "input_files": [str(path) for path in source_selection.files],
        "selection_parameters": {
            "max_samples": max_samples,
            "min_length": min_length,
            "max_length": max_length,
            "doc_types": sorted(allowed_doc_types) if allowed_doc_types else None,
            "random_seed": random_seed,
        },
    }
    return output


def parse_args(argv: Optional[Sequence[str]] = None) -> argparse.Namespace:
    parser = argparse.ArgumentParser(
        description="从标准化课程文档中构建 GraphRAG prompt tuning 样本集",
        formatter_class=argparse.RawDescriptionHelpFormatter,
        epilog="""
示例:
  python scripts/build_prompt_tuning_samples.py
  python scripts/build_prompt_tuning_samples.py --input_dir ./input --output_file ./data/prompt_tuning_samples/os.json
  python scripts/build_prompt_tuning_samples.py --input_dir ../pdf_ingest/output/graphrag --max_samples 80 --doc_types textbook lab
        """,
    )
    parser.add_argument(
        "--input_dir",
        default=None,
        help="输入文件或目录。未提供时自动发现标准化 JSON 输入。",
    )
    parser.add_argument(
        "--output_file",
        default=str(DEFAULT_OUTPUT_FILE),
        help=f"输出 JSON 文件路径，默认 {DEFAULT_OUTPUT_FILE}",
    )
    parser.add_argument(
        "--max_samples",
        type=int,
        default=DEFAULT_MAX_SAMPLES,
        help=f"最大样本数，默认 {DEFAULT_MAX_SAMPLES}",
    )
    parser.add_argument(
        "--min_length",
        type=int,
        default=DEFAULT_MIN_LENGTH,
        help=f"最小正文长度，默认 {DEFAULT_MIN_LENGTH}",
    )
    parser.add_argument(
        "--max_length",
        type=int,
        default=DEFAULT_MAX_LENGTH,
        help=f"最大正文长度，默认 {DEFAULT_MAX_LENGTH}",
    )
    parser.add_argument(
        "--doc_types",
        nargs="*",
        default=None,
        help="允许的 document_type 列表，可空格分隔，也支持逗号分隔",
    )
    parser.add_argument(
        "--random_seed",
        type=int,
        default=42,
        help="随机种子，默认 42",
    )
    return parser.parse_args(argv)


def _print_summary(result: Dict[str, Any], output_file: Path) -> None:
    stats = result["stats"]
    print("[样本构建] 完成")
    print(f"  输出文件: {output_file}")
    print(f"  输入文档总数: {stats['input_documents_total']}")
    print(f"  字段校验失败: {stats['validation_failed']}")
    print(f"  过滤数量: {stats['filtered_total']}")
    print(f"  去重数量: {stats['deduplicated_count']}")
    print(f"  最终采样数量: {stats['final_sample_count']}")
    print(f"  document_type 分布: {stats['document_type_distribution']}")
    print(f"  guessed_sample_type 分布: {stats['guessed_sample_type_distribution']}")
    print(f"  heading_level 分布: {stats['heading_level_distribution']}")


def main(argv: Optional[Sequence[str]] = None) -> int:
    args = parse_args(argv)

    if args.max_samples <= 0:
        raise ValueError("--max_samples 必须大于 0")
    if args.min_length <= 0:
        raise ValueError("--min_length 必须大于 0")
    if args.max_length <= args.min_length:
        raise ValueError("--max_length 必须大于 --min_length")

    allowed_doc_types = _parse_doc_types(args.doc_types)
    source_selection = discover_input_files(args.input_dir)

    result = build_samples(
        source_selection=source_selection,
        max_samples=args.max_samples,
        min_length=args.min_length,
        max_length=args.max_length,
        allowed_doc_types=allowed_doc_types,
        random_seed=args.random_seed,
    )

    output_file = Path(args.output_file).expanduser()
    if not output_file.is_absolute():
        output_file = (Path.cwd() / output_file).resolve()
    output_file.parent.mkdir(parents=True, exist_ok=True)
    output_file.write_text(
        json.dumps(result, ensure_ascii=False, indent=2),
        encoding="utf-8",
    )

    _print_summary(result, output_file)
    return 0


if __name__ == "__main__":
    sys.exit(main())
