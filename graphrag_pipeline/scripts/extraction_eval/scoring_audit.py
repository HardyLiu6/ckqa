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

from .extraction_schema import StructuredExtractionResult
from .scoring_metrics import _normalize_title


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


def _align_one(
    gold: GoldEntity,
    candidates: Sequence[ExtCandidate],
    claimed: set[int],
) -> AlignResult:
    """把一条 gold 对齐到 ext 候选。纯函数，只读 `claimed`。

    规则：
      Phase 1 exact：按 idx 升序扫，找第一条 title_norm == gold.name_norm
                     且 type == gold.type 的未占用候选则命中；若存在匹配候选
                     但全被占用，返回 exact_occupied 并吞掉 Phase 2。
      Phase 2 alias：当且仅当 Phase 1 完全无匹配时执行；按 gold.alias_norms
                     顺序扫，候选需同时 title_norm == alias 且 type == gold.type；
                     若未占用则命中；若存在匹配候选但全被占用，返回 alias_occupied。
      其余：none。
    """
    sorted_cands = sorted(candidates, key=lambda c: c.idx)

    # Phase 1: exact
    exact_unclaimed: int | None = None
    exact_has_any = False
    for cand in sorted_cands:
        if cand.title_norm == gold.name_norm and cand.type == gold.type:
            exact_has_any = True
            if cand.idx not in claimed:
                exact_unclaimed = cand.idx
                break
    if exact_unclaimed is not None:
        return AlignResult(exact_unclaimed, "exact")
    if exact_has_any:
        return AlignResult(None, "exact_occupied")

    # Phase 2: alias
    alias_has_any = False
    for alias in gold.alias_norms:
        for cand in sorted_cands:
            if cand.title_norm == alias and cand.type == gold.type:
                alias_has_any = True
                if cand.idx not in claimed:
                    return AlignResult(cand.idx, "alias")
    if alias_has_any:
        return AlignResult(None, "alias_occupied")

    return AlignResult(None, "none")


def align_sample(
    gold_entities: Sequence[GoldEntity],
    ext_candidates: Sequence[ExtCandidate],
) -> dict[str, AlignResult]:
    """把一个 sample 的 gold_entities 对齐到 ext_candidates。

    规则：
      - 对每条 gold，按优先级 exact → alias 搜索（见 _align_one）。
      - ext_candidates 使用 ExtCandidate.idx 作为占用键；同一 sample 内 one-to-one。
      - gold 遍历顺序固定为 gold_id 字典序（确定性 tie-break，评测稳定优先于召回最大）；
        候选遍历顺序固定为 ExtCandidate.idx 升序。
      - 无 ext_candidates 时所有 gold 返回 none。

    返回：{gold_id: AlignResult}
    """
    claimed: set[int] = set()
    result: dict[str, AlignResult] = {}
    for gold in sorted(gold_entities, key=lambda g: g.gold_id):
        aligned = _align_one(gold, ext_candidates, claimed)
        if aligned.matched_ext_idx is not None:
            claimed.add(aligned.matched_ext_idx)
        result[gold.gold_id] = aligned
    return result


def _build_ext_candidates(
    item: StructuredExtractionResult,
) -> list[ExtCandidate]:
    """从 ExtractionEntity 列表构建 ExtCandidate 列表。

    idx 对应 item.entities 的原始下标；title_norm 为空的条目被跳过但
    其它条目的 idx 保持原位，保证与 extraction 关系引用的 title 可对齐。
    """
    out: list[ExtCandidate] = []
    for idx, ent in enumerate(item.entities):
        tn = _normalize_title(ent.title)
        if not tn:
            continue
        out.append(ExtCandidate(idx=idx, title_norm=tn, type=ent.type))
    return out


def _build_gold_entities(entry: AuditEntry) -> list[GoldEntity]:
    """从 AuditEntry.gold_entities 构建 GoldEntity 列表。

    跳过 name 归一化后为空的条目；alias 缺失时视为空；alias 经
    canonicalize_gold_aliases 过滤空串并规范化。
    """
    out: list[GoldEntity] = []
    for g in entry.gold_entities:
        name_norm = _normalize_title(g.get("name", ""))
        if not name_norm:
            continue
        out.append(GoldEntity(
            gold_id=str(g.get("entity_id", "") or ""),
            name_norm=name_norm,
            alias_norms=canonicalize_gold_aliases(g.get("alias") or ()),
            type=str(g.get("type", "") or ""),
        ))
    return out


def compute_audit_entity_recall(
    results: Sequence[StructuredExtractionResult],
    audit_index: dict[str, AuditEntry],
) -> float:
    """每个成功样本：命中 gold 数 / gold 总数，按样本平均。

    零分母规则：
      - 样本级：len(gold_entities) == 0 时样本不计入。
      - 汇总级：无可用样本时整体返回 0.0。
    """
    recalls: list[float] = []
    for item in results:
        if item.status != "success":
            continue
        entry = audit_index.get(item.sample_id)
        if entry is None or not entry.gold_entities:
            continue
        golds = _build_gold_entities(entry)
        if not golds:
            continue
        cands = _build_ext_candidates(item)
        aligned = align_sample(golds, cands)
        hits = sum(1 for r in aligned.values() if r.matched_ext_idx is not None)
        recalls.append(hits / len(golds))
    if not recalls:
        return 0.0
    return sum(recalls) / len(recalls)


def compute_audit_entity_precision(
    results: Sequence[StructuredExtractionResult],
    audit_index: dict[str, AuditEntry],
) -> float:
    """每个成功样本：命中 gold 的 ext 去重后 / ext 总数，按样本平均。

    零分母规则：
      - 样本级：len(ext_candidates) == 0 时样本返回 0.0。
      - 汇总级：无可用样本时整体返回 0.0。
    """
    precisions: list[float] = []
    for item in results:
        if item.status != "success":
            continue
        entry = audit_index.get(item.sample_id)
        if entry is None or not entry.gold_entities:
            continue
        golds = _build_gold_entities(entry)
        if not golds:
            continue
        cands = _build_ext_candidates(item)
        if not cands:
            precisions.append(0.0)
            continue
        aligned = align_sample(golds, cands)
        matched_ext = {r.matched_ext_idx for r in aligned.values()
                       if r.matched_ext_idx is not None}
        precisions.append(len(matched_ext) / len(cands))
    if not precisions:
        return 0.0
    return sum(precisions) / len(precisions)


def compute_audit_relation_recall(
    results: Sequence[StructuredExtractionResult],
    audit_index: dict[str, AuditEntry],
) -> float:
    """每个成功样本：命中 gold relation 数 / gold relation 总数，按样本平均。

    命中判定（idx 驱动）：
      1. 用 align_sample 得到 gold_id -> matched_ext_idx。
      2. 把 extraction 关系按 rel.source / rel.target 的 title_norm 扇出到
         (src_idx, rtype, tgt_idx) 三元组集合（不同 type 的 ext idx 独立）。
      3. gold 两端 matched_ext_idx 齐全后，检查 (src, rtype, tgt) 是否在集合里。

    零分母规则：
      - 样本级：len(gold_relations) == 0 时样本不计入。
      - 汇总级：无可用样本时整体返回 0.0。
    """
    recalls: list[float] = []
    for item in results:
        if item.status != "success":
            continue
        entry = audit_index.get(item.sample_id)
        if entry is None or not entry.gold_relations:
            continue
        golds = _build_gold_entities(entry)
        if not golds:
            continue
        cands = _build_ext_candidates(item)
        aligned = align_sample(golds, cands)

        # title_norm -> list[ext_idx]（跨 type 合并，关系本身不带端点 type）
        title_to_idxs: dict[str, list[int]] = {}
        for cand in cands:
            title_to_idxs.setdefault(cand.title_norm, []).append(cand.idx)

        extracted_triples: set[tuple[int, str, int]] = set()
        for rel in item.relationships:
            src_norm = _normalize_title(rel.source)
            tgt_norm = _normalize_title(rel.target)
            for s in title_to_idxs.get(src_norm, ()):
                for t in title_to_idxs.get(tgt_norm, ()):
                    extracted_triples.add((s, rel.type, t))

        hits = 0
        for g in entry.gold_relations:
            src_id = str(g.get("source_entity_id", "") or "")
            tgt_id = str(g.get("target_entity_id", "") or "")
            rtype = g.get("type", "")
            if not rtype:
                continue
            src_align = aligned.get(src_id)
            tgt_align = aligned.get(tgt_id)
            if src_align is None or tgt_align is None:
                continue
            src = src_align.matched_ext_idx
            tgt = tgt_align.matched_ext_idx
            if src is None or tgt is None:
                continue
            if (src, rtype, tgt) in extracted_triples:
                hits += 1
        recalls.append(hits / len(entry.gold_relations))
    if not recalls:
        return 0.0
    return sum(recalls) / len(recalls)
