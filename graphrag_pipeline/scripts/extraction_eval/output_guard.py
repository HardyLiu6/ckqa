#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""抽取输出守卫。

用于识别模型输出是否偏离“只返回 entities/relationships JSON 根对象”的
约束。这里不修改解析结果，只提供重试与评分诊断所需的轻量信号。
"""

from __future__ import annotations

import json
import re
from typing import Any

from .extraction_schema import StructuredExtractionResult


_CODE_FENCE_RE = re.compile(r"```(?:json)?\s*(.*?)```", re.IGNORECASE | re.DOTALL)
_SUSPICIOUS_TOP_LEVEL_KEYS = {
    "analysis",
    "instructions",
    "instruction",
    "calculated_metrics",
    "metrics",
    "delay",
    "ignored_count",
    "user_latency",
}
_SUSPICIOUS_TEXT_PATTERNS = (
    "coffee or tea",
    "ignored count",
    "user latency",
    "re-engagement",
    "delay the user",
)


def analyze_output_guard_issue(
    raw_output: str,
    *,
    parsed_result: StructuredExtractionResult | None = None,
) -> dict[str, Any] | None:
    """返回输出守卫问题；没有问题返回 None。"""

    if parsed_result is not None and parsed_result.status == "parse_error":
        message = parsed_result.error or ""
        if "entities/relationships" in message or "根对象" in message:
            return {
                "code": "missing_payload_root",
                "reason": "JSON 中未找到 entities/relationships 根对象",
            }

    payload = _load_first_json_object(raw_output)
    if isinstance(payload, dict):
        keys = {str(key).strip() for key in payload}
        has_entities = isinstance(payload.get("entities"), list)
        has_relationships = isinstance(payload.get("relationships"), list)
        suspicious = sorted(key for key in keys if key.lower() in _SUSPICIOUS_TOP_LEVEL_KEYS)
        if suspicious:
            return {
                "code": "suspicious_top_level_keys",
                "reason": "JSON 顶层包含非抽取任务字段",
                "keys": suspicious,
            }
        if keys and not (has_entities and has_relationships):
            return {
                "code": "missing_payload_root",
                "reason": "JSON 中未找到 entities/relationships 根对象",
                "keys": sorted(keys),
            }

    lowered = (raw_output or "").lower()
    matched_patterns = [pattern for pattern in _SUSPICIOUS_TEXT_PATTERNS if pattern in lowered]
    if matched_patterns:
        return {
            "code": "suspicious_non_domain_text",
            "reason": "输出疑似混入非课程抽取内容",
            "patterns": matched_patterns,
        }
    return None


def has_output_leak_flag(raw_output: str) -> bool:
    """只统计真正可疑的非领域输出，不把普通 JSON 缺根对象全算成 leak。"""

    issue = analyze_output_guard_issue(raw_output)
    if issue is None:
        return False
    return issue.get("code") in {"suspicious_top_level_keys", "suspicious_non_domain_text"}


def _load_first_json_object(raw_output: str) -> Any | None:
    for candidate in _json_candidates(raw_output):
        try:
            return json.loads(_repair_json_text(candidate))
        except json.JSONDecodeError:
            continue
    return None


def _json_candidates(raw_output: str) -> list[str]:
    text = (raw_output or "").strip()
    candidates = [text] if text else []
    candidates.extend(block.strip() for block in _CODE_FENCE_RE.findall(raw_output or ""))
    first_object = (raw_output or "").find("{")
    last_object = (raw_output or "").rfind("}")
    if first_object >= 0 and last_object > first_object:
        candidates.append((raw_output or "")[first_object : last_object + 1].strip())
    return _dedupe(candidates)


def _repair_json_text(text: str) -> str:
    repaired = text.strip().replace("\ufeff", "")
    repaired = re.sub(r"^json\s*", "", repaired, flags=re.IGNORECASE)
    first_object = repaired.find("{")
    last_object = repaired.rfind("}")
    if first_object >= 0 and last_object > first_object:
        repaired = repaired[first_object : last_object + 1]
    repaired = repaired.replace("“", '"').replace("”", '"').replace("’", "'")
    repaired = re.sub(r",(\s*[}\]])", r"\1", repaired)
    return repaired.strip()


def _dedupe(values: list[str]) -> list[str]:
    out: list[str] = []
    seen: set[str] = set()
    for value in values:
        if not value or value in seen:
            continue
        seen.add(value)
        out.append(value)
    return out
