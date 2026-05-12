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
import re
from dataclasses import dataclass, field
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


@dataclass
class AuditEntry:
    gold_entities: list[dict]
    gold_relations: list[dict]
    gold_seed: bool = False
    gold_seed_version: str = ""
    source_text: str = ""
    metadata_context: dict[str, str] = field(default_factory=dict)


REQUIRED_MANUAL_GOLD_SEED_RELATION_TYPES: tuple[str, ...] = (
    "defined_by",
    "applied_in",
    "depends_on",
)
MANUAL_GOLD_SEED_VERSION = "manual_gold_seed_*"


def _gold_seed_version_matches(actual: str, expected: str) -> bool:
    if expected.endswith("*"):
        return actual.startswith(expected[:-1])
    return actual == expected


def load_audit_index(path: Path) -> dict[str, AuditEntry]:
    payload = json.loads(Path(path).read_text(encoding="utf-8"))
    samples = payload.get("audit_samples") or []
    index: dict[str, AuditEntry] = {}
    for sample in samples:
        sample_id = str(sample.get("source_sample_id") or "").strip()
        if not sample_id:
            continue
        # 构建 metadata context 用于 faithfulness 豁免
        heading_path_list = sample.get("heading_path") or []
        metadata_ctx: dict[str, str] = {}
        if sample.get("course_id"):
            metadata_ctx["course_id"] = str(sample["course_id"])
        if sample.get("source_file"):
            metadata_ctx["source_file"] = str(sample["source_file"])
        if sample.get("chapter"):
            metadata_ctx["chapter"] = str(sample["chapter"])
        if sample.get("section"):
            metadata_ctx["section"] = str(sample["section"])
        if heading_path_list:
            metadata_ctx["heading_path"] = "|".join(str(h) for h in heading_path_list)

        index[sample_id] = AuditEntry(
            gold_entities=list(sample.get("gold_entities") or []),
            gold_relations=list(sample.get("gold_relations") or []),
            gold_seed=bool(sample.get("gold_seed")),
            gold_seed_version=str(sample.get("gold_seed_version") or ""),
            source_text=str(sample.get("text") or ""),
            metadata_context=metadata_ctx,
        )
    return index


def summarize_manual_gold_seed_coverage(
    audit_index: dict[str, AuditEntry],
    *,
    required_relation_types: Sequence[str] = REQUIRED_MANUAL_GOLD_SEED_RELATION_TYPES,
    required_version: str = MANUAL_GOLD_SEED_VERSION,
) -> dict:
    seed_entries = {
        sample_id: entry
        for sample_id, entry in audit_index.items()
        if entry.gold_seed and _gold_seed_version_matches(entry.gold_seed_version, required_version)
    }
    covered_relation_types = sorted(
        {
            str(relation.get("type") or "")
            for entry in seed_entries.values()
            for relation in entry.gold_relations
            if str(relation.get("type") or "")
        }
    )
    required = tuple(required_relation_types)
    missing = [relation_type for relation_type in required if relation_type not in covered_relation_types]
    return {
        "gold_seed_version": required_version,
        "gold_seed_count": len(seed_entries),
        "gold_seed_sample_ids": sorted(seed_entries),
        "gold_seed_relation_types": covered_relation_types,
        "gold_seed_required_relation_types": list(required),
        "gold_seed_missing_relation_types": missing,
        "gold_seed_coverage_passed": bool(seed_entries) and not missing,
    }


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


def _normalize_entity_type(value: str) -> str:
    """实体类型规范化：casefold 后用于比对。

    吸收 GraphRAG 原生 `GraphExtractor._process_result` 对 entity_type 的
    `.upper()`，让 audit gold 和抽取结果在不同大小写下都能对齐。
    """
    return (value or "").strip().casefold()


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
        out.append(ExtCandidate(idx=idx, title_norm=tn, type=_normalize_entity_type(ent.type)))
    return out


def _build_gold_entities(entry: AuditEntry) -> list[GoldEntity]:
    """从 AuditEntry.gold_entities 构建 GoldEntity 列表。

    跳过 name 归一化后为空的条目；alias 缺失时视为空；alias 经
    canonicalize_gold_aliases 过滤空串并规范化。
    type 做 casefold 规范化，用于大小写不敏感的对齐。
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
            type=_normalize_entity_type(g.get("type", "")),
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
            rel_type_norm = _normalize_entity_type(rel.type)
            if not rel_type_norm:
                continue
            for s in title_to_idxs.get(src_norm, ()):
                for t in title_to_idxs.get(tgt_norm, ()):
                    extracted_triples.add((s, rel_type_norm, t))

        hits = 0
        for g in entry.gold_relations:
            src_id = str(g.get("source_entity_id", "") or "")
            tgt_id = str(g.get("target_entity_id", "") or "")
            rtype = _normalize_entity_type(g.get("type", ""))
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


# ---------------------------------------------------------------------------
# Faithfulness Gate：基于源文本的忠实度评估
# ---------------------------------------------------------------------------
# 不依赖 gold 标注密度，只判定抽取实体是否在源文本中有依据。
# 参考：ARGUS (2025) hallucination/omission 分离、FinReflectKG-EvalBench
# (NeurIPS 2025) faithfulness 维度、PU Learning NER 评估修正。
# ---------------------------------------------------------------------------

# 合法实体类型集合（casefold），用于类型合规检查
_VALID_ENTITY_TYPES: frozenset[str] = frozenset(
    t.casefold() for t in [
        "Course", "Chapter", "Section", "KnowledgePoint", "Concept",
        "Term", "FormulaOrDefinition", "AlgorithmOrMethod",
        "Experiment", "Assignment", "ToolOrPlatform",
    ]
)

# 碎片检测正则：纯标点、纯数字、单字符、OCR 残片
_FRAGMENT_PATTERN = re.compile(
    r"^[\s\d\W]{0,2}$"  # 长度 <= 2 且全是空白/数字/标点
    r"|^[^\w]+$"         # 全是非单词字符
    r"|^.$",             # 单字符
    re.UNICODE,
)


@dataclass
class FaithfulnessVerdict:
    """单个实体的忠实度判定结果。"""
    entity_title: str
    entity_type: str
    is_faithful: bool
    failure_reasons: list[str] = field(default_factory=list)


def _is_entity_name_fragment(name: str) -> bool:
    """判断实体名是否为碎片（过短、纯标点、纯数字等）。"""
    if not name or len(name.strip()) < 2:
        return True
    return bool(_FRAGMENT_PATTERN.match(name.strip()))


def _entity_has_text_grounding(entity_title: str, source_text: str) -> bool:
    """判断实体名是否在源文本中有 span 依据。

    使用多级匹配策略：
      Level 1: 完整子串匹配（忽略空格）
      Level 2: casefold 子串匹配（处理英文大小写）
      Level 3: 核心词素匹配——实体名拆分后，主要词素在源文本中出现
               （处理"响应比公式"这类组合名称，源文本有"响应比"和"公式"）
    """
    if not entity_title or not source_text:
        return False
    # 规范化：去除多余空格
    title_clean = re.sub(r"\s+", "", entity_title.strip())
    text_clean = re.sub(r"\s+", "", source_text)

    # Level 1: 直接子串匹配
    if title_clean in text_clean:
        return True

    # Level 2: casefold 匹配
    if title_clean.casefold() in text_clean.casefold():
        return True

    # Level 3: 核心词素匹配
    # 将实体名按中文/英文/数字边界拆分为词素
    morphemes = _split_morphemes(title_clean)
    if len(morphemes) >= 2:
        # 要求至少 2/3 的词素（且至少 2 个）在源文本中出现
        text_lower = text_clean.casefold()
        hits = sum(1 for m in morphemes if m.casefold() in text_lower)
        threshold = max(2, len(morphemes) * 2 // 3)
        if hits >= threshold:
            return True

    return False


def _split_morphemes(text: str) -> list[str]:
    """将实体名拆分为有意义的词素。

    拆分规则：
      - 中文连续字符序列（>= 2 字符）作为一个词素
      - 英文/数字连续序列作为一个词素
      - 单个中文字符不作为独立词素（避免"的""和"等虚词）
    """
    # 按中文/非中文边界拆分
    parts = re.findall(r"[\u4e00-\u9fff]{2,}|[a-zA-Z0-9]+", text)
    return [p for p in parts if len(p) >= 2]


def judge_entity_faithfulness(
    entity_title: str,
    entity_type: str,
    source_text: str,
    *,
    metadata_context: dict[str, str] | None = None,
) -> FaithfulnessVerdict:
    """对单个抽取实体进行忠实度判定。

    判定规则（确定性，不依赖语义推理）：
      1. 名称碎片检测：过短、纯标点、纯数字 → unfaithful
      2. 类型合规检测：类型不在 schema 中 → unfaithful
      3. 文本依据检测：实体名在源文本或文档元数据中无 span 对应 → unfaithful

    metadata_context 用于豁免 metadata-closure 注入的实体（如 course_id、
    章节标题、节标题），这些实体来自文档结构元数据而非正文。
    """
    reasons: list[str] = []
    title = entity_title.strip() if entity_title else ""
    etype = entity_type.strip() if entity_type else ""

    # 规则 1：碎片检测
    if _is_entity_name_fragment(title):
        reasons.append("name_fragment")

    # 规则 2：类型合规
    if _normalize_entity_type(etype) not in _VALID_ENTITY_TYPES:
        reasons.append("invalid_type")

    # 规则 3：文本依据（含 metadata 豁免）
    has_grounding = _entity_has_text_grounding(title, source_text)
    if not has_grounding and metadata_context:
        # 检查是否在文档元数据中有依据
        has_grounding = _entity_in_metadata_context(title, etype, metadata_context)
    if not has_grounding:
        reasons.append("no_text_grounding")

    return FaithfulnessVerdict(
        entity_title=title,
        entity_type=etype,
        is_faithful=len(reasons) == 0,
        failure_reasons=reasons,
    )


def _entity_in_metadata_context(
    entity_title: str,
    entity_type: str,
    metadata: dict[str, str],
) -> bool:
    """检查实体是否来自文档元数据（metadata-closure 注入）。

    豁免条件：
      - Course 类型且名称匹配 course_id 或 source_file
      - Chapter 类型且名称匹配 chapter 元数据
      - Section 类型且名称匹配 section 或 heading_path 中的元素
    """
    etype_norm = _normalize_entity_type(entity_type)
    title_norm = re.sub(r"\s+", "", entity_title.strip()).casefold()

    if etype_norm == "course":
        for key in ("course_id", "source_file", "course_name"):
            val = metadata.get(key, "")
            if val and re.sub(r"\s+", "", val).casefold() == title_norm:
                return True
            # course_id 模式匹配（如 crs-YYYYMMDD-XXXXXX）
            if val and title_norm == re.sub(r"\s+", "", val).casefold():
                return True
        return False

    if etype_norm == "chapter":
        chapter_val = metadata.get("chapter", "")
        if chapter_val and re.sub(r"\s+", "", chapter_val).casefold() == title_norm:
            return True
        # heading_path 中的第一个元素通常是章
        heading_path = metadata.get("heading_path", "")
        if heading_path:
            for h in heading_path.split("|"):
                if re.sub(r"\s+", "", h).casefold() == title_norm:
                    return True
        return False

    if etype_norm == "section":
        section_val = metadata.get("section", "")
        if section_val and re.sub(r"\s+", "", section_val).casefold() == title_norm:
            return True
        heading_path = metadata.get("heading_path", "")
        if heading_path:
            for h in heading_path.split("|"):
                if re.sub(r"\s+", "", h).casefold() == title_norm:
                    return True
        return False

    return False


@dataclass
class FaithfulnessSampleResult:
    """单个样本的忠实度评估结果。"""
    sample_id: str
    total_entities: int
    faithful_count: int
    unfaithful_count: int
    error_rate: float
    verdicts: list[FaithfulnessVerdict]


def compute_sample_faithfulness(
    item: StructuredExtractionResult,
    source_text: str,
    metadata_context: dict[str, str] | None = None,
) -> FaithfulnessSampleResult:
    """对单个样本的所有抽取实体进行忠实度评估。"""
    verdicts: list[FaithfulnessVerdict] = []
    for ent in item.entities:
        if not ent.title.strip():
            continue
        verdict = judge_entity_faithfulness(
            ent.title, ent.type, source_text,
            metadata_context=metadata_context,
        )
        verdicts.append(verdict)

    total = len(verdicts)
    unfaithful = sum(1 for v in verdicts if not v.is_faithful)
    faithful = total - unfaithful
    error_rate = unfaithful / total if total > 0 else 0.0

    return FaithfulnessSampleResult(
        sample_id=item.sample_id,
        total_entities=total,
        faithful_count=faithful,
        unfaithful_count=unfaithful,
        error_rate=error_rate,
        verdicts=verdicts,
    )


def compute_faithfulness_error_rate(
    results: Sequence[StructuredExtractionResult],
    audit_index: dict[str, AuditEntry],
) -> float:
    """计算所有成功样本的平均忠实度错误率。

    faithfulness_error_rate = avg(unfaithful_entities / total_entities)

    不依赖 gold 标注密度，只依赖源文本和文档元数据。
    当样本无源文本或无抽取实体时跳过。

    返回值范围 [0.0, 1.0]，越低越好。
    """
    rates: list[float] = []
    for item in results:
        if item.status != "success":
            continue
        entry = audit_index.get(item.sample_id)
        if entry is None or not entry.source_text:
            continue
        result = compute_sample_faithfulness(item, entry.source_text, entry.metadata_context)
        if result.total_entities == 0:
            continue
        rates.append(result.error_rate)
    if not rates:
        return 0.0
    return sum(rates) / len(rates)


def compute_faithfulness_details(
    results: Sequence[StructuredExtractionResult],
    audit_index: dict[str, AuditEntry],
) -> dict:
    """计算忠实度评估的详细报告，包含分类统计和样本级明细。"""
    sample_results: list[FaithfulnessSampleResult] = []
    failure_reason_counts: dict[str, int] = {}

    for item in results:
        if item.status != "success":
            continue
        entry = audit_index.get(item.sample_id)
        if entry is None or not entry.source_text:
            continue
        result = compute_sample_faithfulness(item, entry.source_text, entry.metadata_context)
        if result.total_entities == 0:
            continue
        sample_results.append(result)
        for v in result.verdicts:
            for reason in v.failure_reasons:
                failure_reason_counts[reason] = failure_reason_counts.get(reason, 0) + 1

    if not sample_results:
        return {
            "faithfulness_error_rate": 0.0,
            "sample_count": 0,
            "total_entities_evaluated": 0,
            "total_unfaithful": 0,
            "failure_reason_counts": {},
            "per_sample": [],
        }

    avg_error_rate = sum(r.error_rate for r in sample_results) / len(sample_results)
    total_entities = sum(r.total_entities for r in sample_results)
    total_unfaithful = sum(r.unfaithful_count for r in sample_results)

    return {
        "faithfulness_error_rate": round(avg_error_rate, 4),
        "sample_count": len(sample_results),
        "total_entities_evaluated": total_entities,
        "total_unfaithful": total_unfaithful,
        "global_error_rate": round(total_unfaithful / total_entities, 4) if total_entities else 0.0,
        "failure_reason_counts": dict(sorted(failure_reason_counts.items(), key=lambda x: -x[1])),
        "per_sample": [
            {
                "sample_id": r.sample_id,
                "total": r.total_entities,
                "unfaithful": r.unfaithful_count,
                "error_rate": round(r.error_rate, 4),
                "unfaithful_entities": [
                    {"title": v.entity_title, "type": v.entity_type, "reasons": v.failure_reasons}
                    for v in r.verdicts if not v.is_faithful
                ],
            }
            for r in sample_results
        ],
    }
