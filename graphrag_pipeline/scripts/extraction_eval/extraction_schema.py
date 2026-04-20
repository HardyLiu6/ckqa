#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
抽取结果与 schema 模型
====================
统一收口步骤 7 所需的 Pydantic 数据结构。
"""

from __future__ import annotations

from typing import Any, Literal

from pydantic import BaseModel, ConfigDict, Field, field_validator


def _clean_text(value: object) -> str:
    if value is None:
        return ""
    if isinstance(value, str):
        return value.strip()
    return str(value).strip()


class SchemaTypeInfo(BaseModel):
    """Schema 中的实体/关系类型说明。"""

    model_config = ConfigDict(extra="ignore")

    name: str
    label_zh: str = ""
    description: str = ""


class SchemaCatalog(BaseModel):
    """抽取任务使用的 schema 摘要。"""

    model_config = ConfigDict(extra="ignore")

    schema_version: str = "v1"
    entity_types: list[SchemaTypeInfo] = Field(default_factory=list)
    relation_types: list[SchemaTypeInfo] = Field(default_factory=list)
    entity_schema_path: str
    relation_schema_path: str

    @property
    def entity_type_names(self) -> list[str]:
        return [item.name for item in self.entity_types]

    @property
    def relation_type_names(self) -> list[str]:
        return [item.name for item in self.relation_types]

    def render_entity_type_summary(self) -> str:
        return "\n".join(
            f'- `{item.name}`（{item.label_zh or item.name}）：{item.description or "未提供描述"}'
            for item in self.entity_types
        )

    def render_relation_type_summary(self) -> str:
        return "\n".join(
            f'- `{item.name}`（{item.label_zh or item.name}）：{item.description or "未提供描述"}'
            for item in self.relation_types
        )


class ExtractionEntity(BaseModel):
    """统一实体结构。"""

    model_config = ConfigDict(extra="ignore")

    id: str
    title: str
    type: str
    description: str = ""
    evidence: str = ""

    @field_validator("id", "title", "type", "description", "evidence", mode="before")
    @classmethod
    def _normalize_fields(cls, value: object) -> str:
        return _clean_text(value)


class ExtractionRelationship(BaseModel):
    """统一关系结构。"""

    model_config = ConfigDict(extra="ignore")

    source: str
    target: str
    type: str
    description: str = ""
    evidence: str = ""

    @field_validator("source", "target", "type", "description", "evidence", mode="before")
    @classmethod
    def _normalize_fields(cls, value: object) -> str:
        return _clean_text(value)


class StructuredExtractionPayload(BaseModel):
    """模型直接返回的统一 JSON 载荷。"""

    model_config = ConfigDict(extra="ignore")

    entities: list[dict] = Field(default_factory=list)
    relationships: list[dict] = Field(default_factory=list)


class StructuredExtractionResult(BaseModel):
    """步骤 7 对外输出的统一样本结果。"""

    model_config = ConfigDict(extra="ignore")

    sample_id: str
    candidate: str
    status: Literal["success", "parse_error", "llm_error"]
    entities: list[ExtractionEntity] = Field(default_factory=list)
    relationships: list[ExtractionRelationship] = Field(default_factory=list)
    raw_output: str = ""
    error: str | None = None
    parser_error_code: str | None = None
    llm_debug: dict[str, Any] | None = None

    @field_validator("sample_id", "candidate", mode="before")
    @classmethod
    def _normalize_required_text(cls, value: object) -> str:
        return _clean_text(value)

    @field_validator("raw_output", mode="before")
    @classmethod
    def _preserve_raw_output(cls, value: object) -> str:
        if value is None:
            return ""
        return value if isinstance(value, str) else str(value)

    @field_validator("error", mode="before")
    @classmethod
    def _normalize_optional_text(cls, value: object) -> str | None:
        cleaned = _clean_text(value)
        return cleaned or None

    @field_validator("parser_error_code", mode="before")
    @classmethod
    def _normalize_error_code(cls, value: object) -> str | None:
        cleaned = _clean_text(value)
        return cleaned or None
