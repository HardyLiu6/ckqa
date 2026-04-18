#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""候选 Prompt 抽取结果的规则化度量函数集合。

本模块只负责纯计算：输入 StructuredExtractionResult 列表与 schema，
输出候选级指标。不做 IO，不依赖 audit 集。
"""

from __future__ import annotations

import re
import unicodedata
from typing import Iterable, Sequence

from extraction_schema import StructuredExtractionResult


_PUNCT_RE = re.compile(r"[\s\.,;:!?，。；：！？'\"\(\)\[\]\{\}（）【】《》]+")


def _normalize_title(value: str) -> str:
    if value is None:
        return ""
    text = unicodedata.normalize("NFKC", str(value)).strip().lower()
    return _PUNCT_RE.sub("", text)


def compute_parse_success_rate(results: Sequence[StructuredExtractionResult]) -> float:
    """返回 status == "success" 的样本占比；空列表返回 0.0。"""
    if not results:
        return 0.0
    success = sum(1 for item in results if item.status == "success")
    return success / len(results)


def _iter_success(results: Sequence[StructuredExtractionResult]):
    return (item for item in results if item.status == "success")


def compute_entity_type_valid_rate(
    results: Sequence[StructuredExtractionResult],
    entity_type_names: Iterable[str],
) -> float:
    allowed = set(entity_type_names)
    total = 0
    valid = 0
    for item in _iter_success(results):
        for ent in item.entities:
            total += 1
            if ent.type in allowed:
                valid += 1
    return valid / total if total else 0.0


def compute_relation_type_valid_rate(
    results: Sequence[StructuredExtractionResult],
    relation_type_names: Iterable[str],
) -> float:
    allowed = set(relation_type_names)
    total = 0
    valid = 0
    for item in _iter_success(results):
        for rel in item.relationships:
            total += 1
            if rel.type in allowed:
                valid += 1
    return valid / total if total else 0.0


def compute_schema_hit_rate(
    results: Sequence[StructuredExtractionResult],
    entity_type_names: Iterable[str],
    relation_type_names: Iterable[str],
) -> float:
    entity_allowed = set(entity_type_names)
    relation_allowed = set(relation_type_names)
    success_items = list(_iter_success(results))
    if not success_items:
        return 0.0
    hits = 0
    for item in success_items:
        entities_ok = all(ent.type in entity_allowed for ent in item.entities)
        relations_ok = all(rel.type in relation_allowed for rel in item.relationships)
        if entities_ok and relations_ok:
            hits += 1
    return hits / len(success_items)


def compute_endpoint_valid_rate(
    results: Sequence[StructuredExtractionResult],
    relation_schema: dict,
) -> float:
    total = 0
    valid = 0
    for item in _iter_success(results):
        title_to_type: dict[str, str] = {
            _normalize_title(ent.title): ent.type for ent in item.entities
        }
        for rel in item.relationships:
            constraints = relation_schema.get(rel.type)
            if not constraints:
                continue
            total += 1
            src_type = title_to_type.get(_normalize_title(rel.source))
            tgt_type = title_to_type.get(_normalize_title(rel.target))
            if src_type is None or tgt_type is None:
                continue
            source_types = set(constraints.get("source_types") or [])
            target_types = set(constraints.get("target_types") or [])
            if src_type in source_types and tgt_type in target_types:
                valid += 1
    return valid / total if total else 0.0
