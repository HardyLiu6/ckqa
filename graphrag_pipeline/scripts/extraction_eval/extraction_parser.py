#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
抽取输出解析器
==============
优先解析统一 JSON，必要时对旧 tuple 输出做保底兼容。
"""

from __future__ import annotations

import hashlib
import json
import re
from typing import Any

from .extraction_schema import (
    ExtractionEntity,
    ExtractionRelationship,
    SchemaCatalog,
    StructuredExtractionPayload,
    StructuredExtractionResult,
)


_CODE_FENCE_RE = re.compile(r"```(?:json)?\s*(.*?)```", re.IGNORECASE | re.DOTALL)
_RELATION_TYPE_RE = re.compile(r"^\s*\[type=([A-Za-z0-9_:-]+)\]\s*", re.IGNORECASE)
TRUNCATED_JSON = "truncated_json"
INVALID_JSON = "invalid_json"
EMPTY_OUTPUT = "empty_output"


def parse_extraction_output(
    raw_output: str,
    *,
    sample_id: str,
    candidate: str,
    schema_catalog: SchemaCatalog,
) -> StructuredExtractionResult:
    """将模型输出归一化为统一结构。"""

    json_errors: list[str] = []

    for candidate_text in _extract_json_candidates(raw_output):
        for variant in _dedupe_texts([candidate_text, _repair_json_text(candidate_text)]):
            if not variant:
                continue
            try:
                payload = json.loads(variant)
            except json.JSONDecodeError as exc:
                json_errors.append(str(exc))
                continue

            root_payload = _extract_payload_root(payload)
            if root_payload is None:
                json_errors.append("JSON 中未找到 entities/relationships 根对象")
                continue
            return _build_success_result(
                raw_payload=root_payload,
                raw_output=raw_output,
                sample_id=sample_id,
                candidate=candidate,
                schema_catalog=schema_catalog,
            )

    tuple_payload = _parse_tuple_output(raw_output)
    if tuple_payload is not None:
        return _build_success_result(
            raw_payload=tuple_payload,
            raw_output=raw_output,
            sample_id=sample_id,
            candidate=candidate,
            schema_catalog=schema_catalog,
        )

    error_code, error_message = _classify_parse_error(raw_output, json_errors)
    return StructuredExtractionResult(
        sample_id=sample_id,
        candidate=candidate,
        status="parse_error",
        entities=[],
        relationships=[],
        raw_output=raw_output,
        error=error_message,
        parser_error_code=error_code,
    )


def _build_success_result(
    *,
    raw_payload: dict[str, Any],
    raw_output: str,
    sample_id: str,
    candidate: str,
    schema_catalog: SchemaCatalog,
) -> StructuredExtractionResult:
    payload = StructuredExtractionPayload.model_validate(raw_payload)
    entities = _normalize_entities(payload.entities, schema_catalog)
    relationships = _normalize_relationships(payload.relationships, schema_catalog)

    return StructuredExtractionResult(
        sample_id=sample_id,
        candidate=candidate,
        status="success",
        entities=entities,
        relationships=relationships,
        raw_output=raw_output,
        error=None,
    )


def _normalize_entities(raw_entities: list[dict[str, Any]], schema_catalog: SchemaCatalog) -> list[ExtractionEntity]:
    type_aliases = _build_type_aliases(schema_catalog.entity_types)
    entities: list[ExtractionEntity] = []

    for index, item in enumerate(raw_entities, start=1):
        if not isinstance(item, dict):
            continue
        title = _clean_text(item.get("title") or item.get("name") or item.get("entity_name"))
        if not title:
            continue
        entity_type = _normalize_type(item.get("type") or item.get("entity_type"), type_aliases)
        if not entity_type:
            continue
        description = _clean_text(item.get("description") or item.get("entity_description") or item.get("summary"))
        evidence = _clean_text(item.get("evidence") or item.get("span_text") or item.get("quote"))
        entity_id = _clean_text(item.get("id") or item.get("entity_id")) or _make_entity_id(title, entity_type, index)
        entities.append(
            ExtractionEntity(
                id=entity_id,
                title=title,
                type=entity_type,
                description=description,
                evidence=evidence,
            )
        )

    return entities


def _normalize_relationships(
    raw_relationships: list[dict[str, Any]],
    schema_catalog: SchemaCatalog,
) -> list[ExtractionRelationship]:
    type_aliases = _build_type_aliases(schema_catalog.relation_types)
    relationships: list[ExtractionRelationship] = []
    default_relation_type = "related_to" if "related_to" in schema_catalog.relation_type_names else ""

    for item in raw_relationships:
        if not isinstance(item, dict):
            continue
        source = _clean_text(item.get("source") or item.get("source_entity"))
        target = _clean_text(item.get("target") or item.get("target_entity"))
        if not source or not target:
            continue
        description = _clean_text(item.get("description") or item.get("relationship_description"))
        evidence = _clean_text(item.get("evidence") or item.get("quote"))
        relation_type = _normalize_type(item.get("type") or item.get("relation_type"), type_aliases)

        extracted_type, cleaned_description = _extract_relation_type(description)
        description = cleaned_description
        if not relation_type and extracted_type:
            relation_type = _normalize_type(extracted_type, type_aliases)
        if not relation_type:
            relation_type = default_relation_type
        if not relation_type:
            continue

        relationships.append(
            ExtractionRelationship(
                source=source,
                target=target,
                type=relation_type,
                description=description,
                evidence=evidence,
            )
        )

    return relationships


def _extract_json_candidates(raw_output: str) -> list[str]:
    candidates = [raw_output.strip()]

    for block in _CODE_FENCE_RE.findall(raw_output):
        candidates.append(block.strip())

    first_object = raw_output.find("{")
    last_object = raw_output.rfind("}")
    if first_object >= 0 and last_object > first_object:
        candidates.append(raw_output[first_object : last_object + 1].strip())

    return _dedupe_texts(candidates)


def _repair_json_text(text: str) -> str:
    repaired = text.strip().replace("\ufeff", "")
    repaired = re.sub(r"^json\s*", "", repaired, flags=re.IGNORECASE)

    first_object = repaired.find("{")
    last_object = repaired.rfind("}")
    if first_object >= 0 and last_object > first_object:
        repaired = repaired[first_object : last_object + 1]

    repaired = repaired.replace("“", '"').replace("”", '"').replace("’", "'")
    repaired = re.sub(r",(\s*[}\]])", r"\1", repaired)
    repaired = _escape_invalid_json_backslashes(repaired)
    return repaired.strip()


def _escape_invalid_json_backslashes(text: str) -> str:
    r"""修复模型证据中的 LaTeX 风格单反斜杠，例如 ``\Delta``。

    JSON 只允许有限转义字符；教材内容常带 LaTeX 命令，模型偶尔会输出
    未双写的 ``\Delta`` / ``\mathbf``。这里只补非法单反斜杠，不改动
    ``\n``、``\uXXXX``、``\\`` 等合法 JSON 转义。
    """

    valid_escape_next = {'"', "\\", "/", "b", "f", "n", "r", "t", "u"}
    output: list[str] = []
    index = 0
    while index < len(text):
        char = text[index]
        if char != "\\":
            output.append(char)
            index += 1
            continue

        next_char = text[index + 1] if index + 1 < len(text) else ""
        if next_char in valid_escape_next:
            output.append(char)
            output.append(next_char)
            index += 2
        else:
            output.append("\\\\")
            index += 1
    return "".join(output)


def _extract_payload_root(payload: Any) -> dict[str, Any] | None:
    if isinstance(payload, dict):
        if isinstance(payload.get("entities"), list) and isinstance(payload.get("relationships"), list):
            return {"entities": payload["entities"], "relationships": payload["relationships"]}
        for key in ("result", "data", "output", "payload"):
            nested = payload.get(key)
            root = _extract_payload_root(nested)
            if root is not None:
                return root
    return None


def _classify_parse_error(raw_output: str, json_errors: list[str]) -> tuple[str, str]:
    stripped = (raw_output or "").strip()
    if not stripped:
        return EMPTY_OUTPUT, "模型输出为空"
    if _looks_truncated_json(stripped):
        detail = json_errors[-1] if json_errors else "未识别到完整 JSON 结束位置"
        return TRUNCATED_JSON, f"JSON 输出疑似被截断：{detail}"
    if json_errors:
        return INVALID_JSON, json_errors[-1]
    return INVALID_JSON, "未识别到合法 JSON 或 tuple 抽取结果"


def _looks_truncated_json(text: str) -> bool:
    repaired = _repair_json_text(text)
    if not repaired:
        return False
    if repaired.count("{") > repaired.count("}"):
        return True
    if repaired.count("[") > repaired.count("]"):
        return True
    return repaired.rstrip().endswith((':', ',', '"'))


def _parse_tuple_output(raw_output: str) -> dict[str, Any] | None:
    text = raw_output.replace("```", "")
    text = text.replace("{record_delimiter}", "\n").replace("##", "\n").replace("<|COMPLETE|>", "\n")
    entities: list[dict[str, str]] = []
    relationships: list[dict[str, str]] = []

    for line in text.splitlines():
        stripped = line.strip().rstrip(",")
        if not stripped.startswith("(") or not stripped.endswith(")"):
            continue
        delimiter = _detect_tuple_delimiter(stripped)
        if delimiter is None:
            continue
        parts = [_strip_quotes(part) for part in stripped[1:-1].split(delimiter)]
        if not parts:
            continue
        tuple_kind = parts[0].lower()
        if tuple_kind == "entity" and len(parts) >= 4:
            description = delimiter.join(parts[3:]).strip()
            entities.append(
                {
                    "title": parts[1],
                    "type": parts[2],
                    "description": description,
                    "evidence": "",
                }
            )
        elif tuple_kind == "relationship" and len(parts) >= 5:
            description = delimiter.join(parts[3:-1]).strip() if len(parts) > 5 else parts[3]
            relation_type, cleaned_description = _extract_relation_type(description)
            relationships.append(
                {
                    "source": parts[1],
                    "target": parts[2],
                    "type": relation_type or "related_to",
                    "description": cleaned_description or description,
                    "evidence": "",
                }
            )

    if not entities and not relationships:
        return None
    return {"entities": entities, "relationships": relationships}


def _detect_tuple_delimiter(text: str) -> str | None:
    for delimiter in ("{tuple_delimiter}", "<|>", "|"):
        if delimiter in text:
            return delimiter
    return None


def _strip_quotes(text: str) -> str:
    cleaned = text.strip()
    if cleaned.startswith(("'", '"')) and cleaned.endswith(("'", '"')) and len(cleaned) >= 2:
        return cleaned[1:-1].strip()
    return cleaned


def _build_type_aliases(items: list[Any]) -> dict[str, str]:
    aliases: dict[str, str] = {}
    for item in items:
        aliases[item.name.casefold()] = item.name
        if item.label_zh:
            aliases[item.label_zh.casefold()] = item.name
    return aliases


def _normalize_type(raw_type: Any, aliases: dict[str, str]) -> str:
    cleaned = _clean_text(raw_type)
    if not cleaned:
        return ""
    return aliases.get(cleaned.casefold(), "")


def _extract_relation_type(description: str) -> tuple[str, str]:
    match = _RELATION_TYPE_RE.match(description or "")
    if not match:
        return "", (description or "").strip()
    relation_type = match.group(1).strip()
    cleaned_description = (description or "")[match.end() :].strip()
    return relation_type, cleaned_description


def _make_entity_id(title: str, entity_type: str, index: int) -> str:
    digest = hashlib.md5(f"{entity_type}:{title}:{index}".encode("utf-8")).hexdigest()[:12]
    return f"ent-{digest}"


def _clean_text(value: Any) -> str:
    if value is None:
        return ""
    if isinstance(value, str):
        return value.strip()
    return str(value).strip()


def _dedupe_texts(values: list[str]) -> list[str]:
    result: list[str] = []
    seen: set[str] = set()
    for value in values:
        cleaned = value.strip()
        if not cleaned or cleaned in seen:
            continue
        result.append(cleaned)
        seen.add(cleaned)
    return result
