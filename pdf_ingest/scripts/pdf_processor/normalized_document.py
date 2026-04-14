#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
标准课程文档模型
================
冻结面向 GraphRAG 建图的标准课程文本 schema。

注意：
1. 本模块只定义目标规范，不直接修改现有导出逻辑。
2. 后续 exporter 改造应先生成 NormalizedDocument，再投影为 GraphRAG 兼容 JSON。
"""

from __future__ import annotations

from dataclasses import dataclass, field
from enum import Enum
from typing import Any, Dict, List, Optional


DOCUMENT_SCHEMA_VERSION = "v1"


class DocumentType(str, Enum):
    """课程文档类型枚举。"""

    TEXTBOOK = "textbook"
    SLIDES = "slides"
    SYLLABUS = "syllabus"
    LAB = "lab"
    NOTES = "notes"
    EXAM = "exam"
    REFERENCE = "reference"
    UNKNOWN = "unknown"


REQUIRED_DOCUMENT_FIELDS = (
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


def _is_non_empty_string(value: Any) -> bool:
    return isinstance(value, str) and bool(value.strip())


def _is_optional_string(value: Any) -> bool:
    return value is None or _is_non_empty_string(value)


def validate_normalized_document_dict(payload: Dict[str, Any]) -> List[str]:
    """
    校验标准课程文档字典，返回错误列表。

    该校验用于冻结阶段 1 的字段契约，为阶段 2 的回归样例提供统一检查口径。
    """
    errors: List[str] = []

    if not isinstance(payload, dict):
        return ["文档对象必须是 dict"]

    for field in REQUIRED_DOCUMENT_FIELDS:
        if field not in payload:
            errors.append(f"缺少必填字段: {field}")

    if errors:
        return errors

    if not _is_non_empty_string(payload["id"]):
        errors.append("id 必须是非空字符串")

    if not _is_non_empty_string(payload["source_file"]):
        errors.append("source_file 必须是非空字符串")

    if not _is_non_empty_string(payload["course_id"]):
        errors.append("course_id 必须是非空字符串")

    doc_type = payload["document_type"]
    valid_types = {member.value for member in DocumentType}
    if doc_type not in valid_types:
        errors.append(
            f"document_type 必须属于 {sorted(valid_types)}，当前值: {doc_type!r}"
        )

    for name in ("chapter", "section", "subsection"):
        if not _is_optional_string(payload[name]):
            errors.append(f"{name} 必须是非空字符串或 null")

    heading_level = payload["heading_level"]
    if not isinstance(heading_level, int) or heading_level < 1:
        errors.append("heading_level 必须是大于等于 1 的整数")

    heading_path = payload["heading_path"]
    if not isinstance(heading_path, list) or not heading_path:
        errors.append("heading_path 必须是非空数组")
    else:
        if not all(_is_non_empty_string(item) for item in heading_path):
            errors.append("heading_path 中的每个元素都必须是非空字符串")
        if isinstance(heading_level, int) and heading_level != len(heading_path):
            errors.append("heading_level 必须与 heading_path 长度一致")

        chapter = payload["chapter"]
        section = payload["section"]
        subsection = payload["subsection"]
        if chapter and heading_path[0] != chapter:
            errors.append("chapter 必须与 heading_path[0] 一致")
        if section and len(heading_path) >= 2 and heading_path[1] != section:
            errors.append("section 必须与 heading_path[1] 一致")
        if subsection and len(heading_path) >= 3 and heading_path[2] != subsection:
            errors.append("subsection 必须与 heading_path[2] 一致")

    if not _is_non_empty_string(payload["content"]):
        errors.append("content 必须是非空字符串")

    page_start = payload["page_start"]
    page_end = payload["page_end"]
    if not isinstance(page_start, int) or page_start < 1:
        errors.append("page_start 必须是大于等于 1 的整数")
    if not isinstance(page_end, int) or page_end < 1:
        errors.append("page_end 必须是大于等于 1 的整数")
    if (
        isinstance(page_start, int)
        and isinstance(page_end, int)
        and page_end < page_start
    ):
        errors.append("page_end 不能小于 page_start")

    if not isinstance(payload["metadata"], dict):
        errors.append("metadata 必须是对象")

    return errors


@dataclass
class NormalizedDocument:
    """标准课程文档单元。"""

    id: str
    source_file: str
    document_type: DocumentType
    course_id: str
    chapter: Optional[str]
    section: Optional[str]
    subsection: Optional[str]
    heading_level: int
    heading_path: List[str]
    content: str
    page_start: int
    page_end: int
    metadata: Dict[str, Any] = field(default_factory=dict)

    def to_dict(self) -> Dict[str, Any]:
        """序列化为标准 JSON 对象。"""
        return {
            "id": self.id,
            "source_file": self.source_file,
            "document_type": self.document_type.value,
            "course_id": self.course_id,
            "chapter": self.chapter,
            "section": self.section,
            "subsection": self.subsection,
            "heading_level": self.heading_level,
            "heading_path": list(self.heading_path),
            "content": self.content,
            "page_start": self.page_start,
            "page_end": self.page_end,
            "metadata": dict(self.metadata),
        }

    @classmethod
    def from_dict(cls, payload: Dict[str, Any]) -> "NormalizedDocument":
        """从字典构造标准课程文档。"""
        errors = validate_normalized_document_dict(payload)
        if errors:
            raise ValueError("NormalizedDocument 校验失败: " + "; ".join(errors))

        return cls(
            id=payload["id"],
            source_file=payload["source_file"],
            document_type=DocumentType(payload["document_type"]),
            course_id=payload["course_id"],
            chapter=payload["chapter"],
            section=payload["section"],
            subsection=payload["subsection"],
            heading_level=payload["heading_level"],
            heading_path=list(payload["heading_path"]),
            content=payload["content"],
            page_start=payload["page_start"],
            page_end=payload["page_end"],
            metadata=dict(payload["metadata"]),
        )
