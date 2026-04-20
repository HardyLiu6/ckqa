#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""候选 Prompt 抽取结果的规则化度量函数集合。

本模块只负责纯计算：输入 StructuredExtractionResult 列表与 schema，
输出候选级指标。不做 IO，不依赖 audit 集。
"""

from __future__ import annotations

import re
import statistics
import unicodedata
from typing import Iterable, Sequence

from .extraction_schema import StructuredExtractionResult


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


NOISE_STOPWORDS = {"无", "本章", "本节", "如下", "图", "表", "见下图", "见图"}


def compute_duplicate_entity_rate(
    results: Sequence[StructuredExtractionResult],
) -> float:
    total = 0
    dupes = 0
    for item in _iter_success(results):
        seen: set[tuple[str, str]] = set()
        for ent in item.entities:
            total += 1
            key = (_normalize_title(ent.title), ent.type)
            if key in seen:
                dupes += 1
            else:
                seen.add(key)
    return dupes / total if total else 0.0


def _is_noise_entity(title: str) -> bool:
    stripped = (title or "").strip()
    if not stripped:
        return True
    if stripped in NOISE_STOPWORDS:
        return True
    normalized = _normalize_title(stripped)
    if not normalized:
        return True
    if normalized.isdigit():
        return True
    if len(normalized) < 2 and not normalized.isascii():
        return True
    return False


def compute_noise_entity_rate(
    results: Sequence[StructuredExtractionResult],
) -> float:
    total = 0
    noise = 0
    for item in _iter_success(results):
        for ent in item.entities:
            total += 1
            if _is_noise_entity(ent.title):
                noise += 1
    return noise / total if total else 0.0


def _coefficient_of_variation(values: Sequence[int]) -> float:
    if len(values) < 2:
        return 0.0
    mean = statistics.fmean(values)
    if mean == 0:
        return 0.0
    stdev = statistics.pstdev(values)
    return stdev / mean


def compute_output_stability(results: Sequence[StructuredExtractionResult]) -> float:
    success_items = [item for item in results if item.status == "success"]
    if len(success_items) < 2:
        return 1.0
    entity_counts = [len(item.entities) for item in success_items]
    relation_counts = [len(item.relationships) for item in success_items]
    cv = _coefficient_of_variation(entity_counts) + _coefficient_of_variation(relation_counts)
    return max(0.0, 1.0 - min(1.0, cv))


DEFAULT_WEIGHTS: dict[str, float] = {
    "parse_success_rate": 0.20,
    "schema_hit_rate": 0.10,
    "entity_type_valid_rate": 0.15,
    "relation_type_valid_rate": 0.15,
    "endpoint_valid_rate": 0.15,
    "duplicate_complement": 0.05,
    "noise_complement": 0.05,
    "output_stability": 0.05,
    "audit_entity_recall": 0.025,
    "audit_entity_precision": 0.05,
    "audit_relation_recall": 0.025,
}


HARD_METRIC_KEYS: tuple[str, ...] = (
    "parse_success_rate",
    "schema_hit_rate",
    "entity_type_valid_rate",
    "relation_type_valid_rate",
    "endpoint_valid_rate",
    "duplicate_complement",
    "noise_complement",
)

SOFT_METRIC_KEYS: tuple[str, ...] = (
    "output_stability",
    "audit_entity_recall",
    "audit_entity_precision",
    "audit_relation_recall",
)

GATE_THRESHOLD: float = 0.95


def _subset_composite(
    metrics: dict[str, float | None],
    weights: dict[str, float],
    subset_keys: Iterable[str],
) -> float:
    """在指定子集键上计算加权平均得分，归一到 [0, 1]。

    None 值按子集内剩余键摊回（同 compute_composite_score 的 bonus 规则）。
    """
    keys = [k for k in subset_keys if k in weights]
    present_keys = [k for k in keys if metrics.get(k) is not None]
    missing_keys = [k for k in keys if metrics.get(k) is None]
    present_total = sum(weights[k] for k in present_keys)
    missing_total = sum(weights[k] for k in missing_keys)
    total = present_total + missing_total
    if present_total == 0 or total == 0:
        return 0.0
    bonus = missing_total / present_total
    score = 0.0
    for key in present_keys:
        score += weights[key] * (1.0 + bonus) * float(metrics[key])
    return score / total


def compute_composite_hard(
    metrics: dict[str, float | None],
    weights: dict[str, float],
) -> float:
    return _subset_composite(metrics, weights, HARD_METRIC_KEYS)


def compute_composite_soft(
    metrics: dict[str, float | None],
    weights: dict[str, float],
) -> float:
    return _subset_composite(metrics, weights, SOFT_METRIC_KEYS)


def compute_gate_passed(metrics: dict[str, float | None]) -> bool:
    """所有硬指标非空且 >= GATE_THRESHOLD 才算过门槛。"""
    for key in HARD_METRIC_KEYS:
        value = metrics.get(key)
        if value is None:
            return False
        try:
            if float(value) < GATE_THRESHOLD:
                return False
        except (TypeError, ValueError):
            return False
    return True


def aggregate_candidate_metrics(
    results: Sequence[StructuredExtractionResult],
    *,
    entity_type_names: Iterable[str],
    relation_type_names: Iterable[str],
    relation_schema: dict,
    audit_entity_recall: float | None,
    audit_relation_recall: float | None,
    audit_entity_precision: float | None = None,
    weights: dict[str, float] | None = None,
) -> dict[str, float | int | None]:
    duplicate_rate = compute_duplicate_entity_rate(results)
    noise_rate = compute_noise_entity_rate(results)
    effective_weights = weights if weights is not None else DEFAULT_WEIGHTS
    base: dict[str, float | int | None] = {
        "sample_count": len(results),
        "success_count": sum(1 for r in results if r.status == "success"),
        "parse_success_rate": compute_parse_success_rate(results),
        "schema_hit_rate": compute_schema_hit_rate(
            results, entity_type_names, relation_type_names
        ),
        "entity_type_valid_rate": compute_entity_type_valid_rate(
            results, entity_type_names
        ),
        "relation_type_valid_rate": compute_relation_type_valid_rate(
            results, relation_type_names
        ),
        "endpoint_valid_rate": compute_endpoint_valid_rate(results, relation_schema),
        "duplicate_entity_rate": duplicate_rate,
        "noise_entity_rate": noise_rate,
        "duplicate_complement": 1.0 - duplicate_rate,
        "noise_complement": 1.0 - noise_rate,
        "output_stability": compute_output_stability(results),
        "audit_entity_recall": audit_entity_recall,
        "audit_entity_precision": audit_entity_precision,
        "audit_relation_recall": audit_relation_recall,
    }
    base["composite_hard"] = compute_composite_hard(base, effective_weights)
    base["composite_soft"] = compute_composite_soft(base, effective_weights)
    base["gate_passed"] = compute_gate_passed(base)
    return base


def compute_composite_score(
    metrics: dict[str, float | None],
    weights: dict[str, float],
) -> float:
    present_keys = [k for k in weights if metrics.get(k) is not None]
    missing_keys = [k for k in weights if metrics.get(k) is None]
    missing_total = sum(weights[k] for k in missing_keys)
    present_total = sum(weights[k] for k in present_keys)
    if present_total == 0:
        return 0.0
    bonus = missing_total / present_total
    score = 0.0
    for key in present_keys:
        effective = weights[key] * (1.0 + bonus)
        score += effective * float(metrics[key])
    return score


def rank_candidates(
    metrics_by_candidate: dict[str, dict[str, float]],
) -> list[dict]:
    def sort_key(item: tuple[str, dict]):
        name, metrics = item
        return (
            not bool(metrics.get("gate_passed", False)),
            -float(metrics.get("composite_soft", 0.0)),
            -float(metrics.get("composite_score", 0.0)),
            -float(metrics.get("parse_success_rate", 0.0)),
            -float(metrics.get("endpoint_valid_rate", 0.0)),
            name,
        )

    ordered = sorted(metrics_by_candidate.items(), key=sort_key)
    return [
        {"rank": idx + 1, "candidate": name, **metrics}
        for idx, (name, metrics) in enumerate(ordered)
    ]


def select_top_k(ranked: list[dict], *, k: int) -> list[dict]:
    if k <= 0:
        return []
    return list(ranked[:k])
