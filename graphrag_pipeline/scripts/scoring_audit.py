#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""audit 校准集对齐工具。

只负责：读取 audit 集，构建索引，计算实体/关系 recall 软指标。
对齐策略：
- 实体：归一化 title == 归一化 gold.name 或 gold.name 出现在归一化 title 中。
- 关系：gold (src_id, type, tgt_id) 先映射为 (src_name, type, tgt_name)，再检查
  抽取结果里是否存在同 (归一化 src_name, type, 归一化 tgt_name) 的关系。
"""

from __future__ import annotations

import json
from dataclasses import dataclass
from pathlib import Path
from typing import Sequence

from extraction_schema import StructuredExtractionResult
from scoring_metrics import _normalize_title


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


def _entity_hit(gold_name: str, extracted_titles_norm: set[str]) -> bool:
    g_norm = _normalize_title(gold_name)
    if not g_norm:
        return False
    if g_norm in extracted_titles_norm:
        return True
    return any(g_norm in title for title in extracted_titles_norm)


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
        id_to_name = {g["entity_id"]: g.get("name", "") for g in entry.gold_entities}
        extracted_triples = {
            (_normalize_title(r.source), r.type, _normalize_title(r.target))
            for r in item.relationships
        }
        hits = 0
        for g in entry.gold_relations:
            src = _normalize_title(id_to_name.get(g.get("source_entity_id", ""), ""))
            tgt = _normalize_title(id_to_name.get(g.get("target_entity_id", ""), ""))
            rtype = g.get("type", "")
            if not src or not tgt or not rtype:
                continue
            if (src, rtype, tgt) in extracted_triples:
                hits += 1
        recalls.append(hits / len(entry.gold_relations))
    if not recalls:
        return 0.0
    return sum(recalls) / len(recalls)
