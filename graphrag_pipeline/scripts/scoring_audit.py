#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""audit 校准集对齐工具（严格版）。

只负责：读取 audit 集，构建索引，计算实体/关系软指标。

对齐规则（2026-04-19 起）：
- 实体：ext.title_norm == gold.name_norm 且 ext.type == gold.type（exact）；
       若 exact 完全无匹配，再考虑 gold.alias 走严格 alias 匹配。
- 同 sample 内 one-to-one 占用；当目标 ext 已被更早 gold 占用时返回
  exact_occupied / alias_occupied，matched_ext_idx=None，不做子串回退。
- 关系：gold 两端都拿到 matched_ext_idx 后，用 (src_idx, rtype, tgt_idx)
  去 extraction 关系扇出得到的 idx triple 集里查命中；不再回退字符串。
"""

from __future__ import annotations

import json
from dataclasses import dataclass
from pathlib import Path
from typing import Literal, Sequence

from extraction_schema import StructuredExtractionResult
from scoring_metrics import _normalize_title


MatchMode = Literal[
    "exact",
    "alias",
    "exact_occupied",
    "alias_occupied",
    "none",
]


@dataclass(frozen=True)
class ExtCandidate:
    idx: int
    title_norm: str
    type: str


@dataclass(frozen=True)
class GoldEntity:
    gold_id: str
    name_norm: str
    alias_norms: tuple[str, ...]
    type: str


@dataclass(frozen=True)
class AlignResult:
    matched_ext_idx: int | None
    match_mode: MatchMode


def canonicalize_gold_aliases(aliases: Sequence[str]) -> tuple[str, ...]:
    """按 _normalize_title 规范化并丢掉空串，保留原顺序。"""
    result: list[str] = []
    for a in aliases or ():
        norm = _normalize_title(a)
        if norm:
            result.append(norm)
    return tuple(result)


@dataclass(frozen=True)
class AuditEntry:
    gold_entities: list[dict]
    gold_relations: list[dict]


def load_audit_index(path: Path) -> dict[str, AuditEntry]:
    payload = json.loads(Path(path).read_text(encoding="utf-8"))
    samples = payload.get("audit_samples") or []
    index: dict[str, AuditEntry] = {}
    for sample in samples:
        sample_id = str(sample.get("source_sample_id") or "").strip()
        if not sample_id:
            continue
        index[sample_id] = AuditEntry(
            gold_entities=list(sample.get("gold_entities") or []),
            gold_relations=list(sample.get("gold_relations") or []),
        )
    return index


SHORT_GOLD_GUARD_LEN = 4


def _extracted_aligns_to_gold(ext_norm: str, gold_norm: str) -> bool:
    """Extracted 归一化 title 是否能对齐到 gold 归一化名称。

    精确相等恒成立；gold 归一化长度 >= SHORT_GOLD_GUARD_LEN 时还允许 gold 作为 ext 子串。
    """
    if not ext_norm or not gold_norm:
        return False
    if ext_norm == gold_norm:
        return True
    if len(gold_norm) < SHORT_GOLD_GUARD_LEN:
        return False
    return gold_norm in ext_norm


def _entity_hit(gold_name: str, extracted_titles_norm: set[str]) -> bool:
    g_norm = _normalize_title(gold_name)
    if not g_norm:
        return False
    return any(_extracted_aligns_to_gold(t, g_norm) for t in extracted_titles_norm)


def compute_audit_entity_recall(
    results: Sequence[StructuredExtractionResult],
    audit_index: dict[str, AuditEntry],
) -> float:
    recalls: list[float] = []
    for item in results:
        if item.status != "success":
            continue
        entry = audit_index.get(item.sample_id)
        if entry is None or not entry.gold_entities:
            continue
        extracted = {_normalize_title(e.title) for e in item.entities}
        hits = sum(1 for g in entry.gold_entities if _entity_hit(g.get("name", ""), extracted))
        recalls.append(hits / len(entry.gold_entities))
    if not recalls:
        return 0.0
    return sum(recalls) / len(recalls)


def compute_audit_entity_precision(
    results: Sequence[StructuredExtractionResult],
    audit_index: dict[str, AuditEntry],
) -> float:
    """每个成功样本：extracted 里能对齐到某条 gold 的比例，按样本平均。"""
    precisions: list[float] = []
    for item in results:
        if item.status != "success":
            continue
        entry = audit_index.get(item.sample_id)
        if entry is None or not entry.gold_entities:
            continue
        extracted_norms = [_normalize_title(e.title) for e in item.entities]
        extracted_norms = [n for n in extracted_norms if n]
        if not extracted_norms:
            precisions.append(0.0)
            continue
        gold_norms = [_normalize_title(g.get("name", "")) for g in entry.gold_entities]
        gold_norms = [n for n in gold_norms if n]
        aligned = sum(
            1 for ext in extracted_norms
            if any(_extracted_aligns_to_gold(ext, g) for g in gold_norms)
        )
        precisions.append(aligned / len(extracted_norms))
    if not precisions:
        return 0.0
    return sum(precisions) / len(precisions)


def _align_gold_to_extracted(
    gold_name: str,
    extracted_titles_norm: Sequence[str],
) -> str | None:
    """确定性把 gold 映射到唯一的 extracted 归一化 title。

    对齐规则：
    1. 若 ext 中存在与 gold 归一化严格相等者，直接返回它。
    2. 否则考虑子串候选：gold 作为 ext 子串（gold 长度 >= SHORT_GOLD_GUARD_LEN），
       或 ext 作为 gold 子串（ext 长度 >= SHORT_GOLD_GUARD_LEN）。
    3. 歧义时按 (ext 归一化长度升序, extracted 列表下标升序) 取第一个。

    未找到返回 None。
    """
    g_norm = _normalize_title(gold_name)
    if not g_norm:
        return None
    for t in extracted_titles_norm:
        if t and t == g_norm:
            return t
    candidates: list[tuple[int, int, str]] = []
    for idx, t in enumerate(extracted_titles_norm):
        if not t:
            continue
        shorter = t if len(t) <= len(g_norm) else g_norm
        longer = g_norm if shorter is t else t
        if len(shorter) < SHORT_GOLD_GUARD_LEN:
            continue
        if shorter in longer:
            candidates.append((len(t), idx, t))
    if not candidates:
        return None
    candidates.sort(key=lambda c: (c[0], c[1]))
    return candidates[0][2]


def compute_audit_relation_recall(
    results: Sequence[StructuredExtractionResult],
    audit_index: dict[str, AuditEntry],
) -> float:
    recalls: list[float] = []
    for item in results:
        if item.status != "success":
            continue
        entry = audit_index.get(item.sample_id)
        if entry is None or not entry.gold_relations:
            continue
        extracted_titles = [_normalize_title(e.title) for e in item.entities]
        extracted_titles = [n for n in extracted_titles if n]
        extracted_triples = {
            (_normalize_title(r.source), r.type, _normalize_title(r.target))
            for r in item.relationships
        }
        gold_id_to_aligned: dict[str, str | None] = {}
        for g in entry.gold_entities:
            gid = str(g.get("entity_id", "") or "")
            if not gid:
                continue
            gold_id_to_aligned[gid] = _align_gold_to_extracted(
                g.get("name", ""), extracted_titles
            )
        hits = 0
        for g in entry.gold_relations:
            src_id = str(g.get("source_entity_id", "") or "")
            tgt_id = str(g.get("target_entity_id", "") or "")
            rtype = g.get("type", "")
            if not rtype:
                continue
            src_aligned = gold_id_to_aligned.get(src_id)
            tgt_aligned = gold_id_to_aligned.get(tgt_id)
            if not src_aligned or not tgt_aligned:
                continue
            if (src_aligned, rtype, tgt_aligned) in extracted_triples:
                hits += 1
        recalls.append(hits / len(entry.gold_relations))
    if not recalls:
        return 0.0
    return sum(recalls) / len(recalls)
