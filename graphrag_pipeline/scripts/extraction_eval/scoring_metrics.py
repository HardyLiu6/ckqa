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
_ALIAS_CUE_RE = re.compile(
    r"(又称|简称|全称|英文|英文名|缩写|别名|alias|abbreviation|full\s*name)",
    re.IGNORECASE,
)
_SYMBOL_CUE_RE = re.compile(
    r"(变量|参数|符号|记为|表示为|公式|函数|窗口尺寸|计算|取值|满足|定义为)",
    re.IGNORECASE,
)
_SYMBOL_LIKE_RE = re.compile(r"^[A-Za-zΑ-Ωα-ωΔδΩωλμσΣ_][A-Za-z0-9Α-Ωα-ωΔδΩωλμσΣ_{}\\()（）,\s-]{0,12}$")

STRUCTURAL_CONTAINER_TYPES = {"Course", "Chapter", "Section"}
KNOWLEDGE_CONTAINS_SOURCE_TYPES = {"KnowledgePoint", "Concept", "AlgorithmOrMethod"}


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
    return float(analyze_endpoint_validity(results, relation_schema)["valid_rate"])


def can_derive_inverse_relation(
    relation_type: str,
    source_type: str,
    relation_schema: dict,
) -> bool:
    """判断某关系是否允许派生反向边。

    当前主要用于 `contains -> belongs_to`：知识对象之间的 contains 可以表达
    分类/组成，但不能派生破坏容器边界的 belongs_to。
    """

    constraints = relation_schema.get(relation_type)
    if not isinstance(constraints, dict):
        return False
    inverse = constraints.get("inverse_of")
    if not inverse:
        return False
    derivation_constraints = constraints.get("derivation_constraints")
    if isinstance(derivation_constraints, dict):
        allowed_sources = derivation_constraints.get("derive_inverse_only_when_source_types")
        if isinstance(allowed_sources, list) and allowed_sources:
            return source_type in {str(item) for item in allowed_sources}
    return bool(constraints.get("can_be_derived"))


def _relation_semantic_error(
    rel,
    *,
    source_type: str,
    target_type: str,
) -> tuple[str, str] | None:
    if rel.type != "defined_by" or target_type != "Term":
        return None

    text = " ".join(
        part for part in (rel.source, rel.target, rel.description, rel.evidence) if part
    )
    if _ALIAS_CUE_RE.search(text):
        return ("semantic_defined_by_term_alias", "alias_not_relation")
    if _SYMBOL_CUE_RE.search(text) and _SYMBOL_LIKE_RE.match(rel.target.strip()):
        return None
    return ("semantic_defined_by_term_needs_symbol_cue", "tighten_prompt")


def _suggest_endpoint_action(
    *,
    relation_type: str,
    reason: str,
    source_type: str | None,
    target_type: str | None,
) -> str:
    if reason == "semantic_defined_by_term_alias":
        return "alias_not_relation"
    if reason in {"missing_source", "missing_target", "missing_endpoint"}:
        return "complete_entity_or_skip_relation"
    if relation_type == "contains" and source_type in KNOWLEDGE_CONTAINS_SOURCE_TYPES:
        return "schema_candidate"
    if relation_type in {"belongs_to", "appears_in"}:
        return "tighten_prompt"
    if reason.startswith("semantic_"):
        return "tighten_prompt"
    return "keep_invalid"


def _endpoint_example(item: StructuredExtractionResult, rel, source_type: str | None, target_type: str | None) -> dict:
    return {
        "sample_id": item.sample_id,
        "source": rel.source,
        "source_type": source_type,
        "target": rel.target,
        "target_type": target_type,
        "relation_type": rel.type,
        "description": rel.description,
        "evidence": rel.evidence,
    }


def analyze_endpoint_validity(
    results: Sequence[StructuredExtractionResult],
    relation_schema: dict,
    *,
    max_examples_per_combination: int = 3,
) -> dict:
    """返回 endpoint 有效率与失败组合诊断。

    分母沿用历史规则：只统计 schema 中存在的关系类型；未知关系类型交给
    relation_type_valid_rate 负责。
    """

    total = 0
    valid = 0
    invalid_groups: dict[tuple[str, str | None, str | None, str], dict] = {}
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
                if src_type is None and tgt_type is None:
                    reason = "missing_endpoint"
                elif src_type is None:
                    reason = "missing_source"
                else:
                    reason = "missing_target"
                key = (rel.type, src_type, tgt_type, reason)
                group = invalid_groups.setdefault(
                    key,
                    {
                        "relation_type": rel.type,
                        "source_type": src_type,
                        "target_type": tgt_type,
                        "reason": reason,
                        "count": 0,
                        "suggested_action": _suggest_endpoint_action(
                            relation_type=rel.type,
                            reason=reason,
                            source_type=src_type,
                            target_type=tgt_type,
                        ),
                        "examples": [],
                    },
                )
                group["count"] += 1
                if len(group["examples"]) < max_examples_per_combination:
                    group["examples"].append(_endpoint_example(item, rel, src_type, tgt_type))
                continue
            source_types = set(constraints.get("source_types") or [])
            target_types = set(constraints.get("target_types") or [])
            type_valid = src_type in source_types and tgt_type in target_types
            semantic_error = (
                _relation_semantic_error(rel, source_type=src_type, target_type=tgt_type)
                if type_valid
                else None
            )
            if type_valid and semantic_error is None:
                valid += 1
                continue

            reason, action = semantic_error if semantic_error else ("endpoint_type_mismatch", "")
            key = (rel.type, src_type, tgt_type, reason)
            group = invalid_groups.setdefault(
                key,
                {
                    "relation_type": rel.type,
                    "source_type": src_type,
                    "target_type": tgt_type,
                    "reason": reason,
                    "count": 0,
                    "suggested_action": action
                    or _suggest_endpoint_action(
                        relation_type=rel.type,
                        reason=reason,
                        source_type=src_type,
                        target_type=tgt_type,
                    ),
                    "examples": [],
                },
            )
            group["count"] += 1
            if len(group["examples"]) < max_examples_per_combination:
                group["examples"].append(_endpoint_example(item, rel, src_type, tgt_type))

    invalid_combinations = sorted(
        invalid_groups.values(),
        key=lambda item: (-int(item["count"]), item["relation_type"], str(item["source_type"]), str(item["target_type"]), item["reason"]),
    )
    return {
        "total": total,
        "valid": valid,
        "invalid_count": total - valid,
        "valid_rate": valid / total if total else 0.0,
        "invalid_combinations": invalid_combinations,
    }


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
    endpoint_analysis = analyze_endpoint_validity(results, relation_schema)
    base["endpoint_total_count"] = endpoint_analysis["total"]
    base["endpoint_invalid_count"] = endpoint_analysis["invalid_count"]
    base["invalid_endpoint_combinations"] = endpoint_analysis["invalid_combinations"]
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
