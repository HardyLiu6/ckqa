#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
小规模 audit 抽取校准集构建脚本
================================

目标：
1. 从 prompt tuning 样本集中挑选少量高价值样本。
2. 生成稳定的人工审查模板与标注说明。
3. 为后续规则化自动评测校准、候选 Prompt 接近时的人工裁决提供支撑。

约束：
1. 不修改上游 prompt tuning 样本字段契约。
2. 只做 audit 校准集构建，不提前实现自动评测逻辑。
3. 优先分层抽样；若缺少足够分层字段，则自动降级为随机抽样。
"""

from __future__ import annotations

import argparse
import hashlib
import json
import random
import re
import sys
from collections import Counter, defaultdict
from dataclasses import dataclass, field
from datetime import datetime
from pathlib import Path
from typing import Any, Dict, Iterable, List, Optional, Sequence


PROJECT_ROOT = Path(__file__).resolve().parents[2]
DEFAULT_INPUT_CANDIDATES: tuple[str, ...] = (
    "data/prompt_tuning_samples/samples.json",
    "data/prompt_tuning_samples/prompt_tuning_samples.json",
    "data/prompt_tuning_samples/prompt_tuning_samples.preview.json",
)
DEFAULT_OUTPUT_FILE = PROJECT_ROOT / "data" / "eval" / "audit_extraction_set.json"
DEFAULT_GUIDELINE_FILE = PROJECT_ROOT / "data" / "eval" / "audit_annotation_guidelines.md"
DEFAULT_REPORT_FILE = PROJECT_ROOT / "results" / "reports" / "audit_sampling_report.json"
DEFAULT_ENTITY_SCHEMA = PROJECT_ROOT / "config" / "schema" / "entity_types.json"
DEFAULT_RELATION_SCHEMA = PROJECT_ROOT / "config" / "schema" / "relation_types.json"

DEFAULT_SAMPLE_SIZE = 20
DEFAULT_PRIORITY_FIELDS = (
    "guessed_sample_type",
    "document_type",
    "chapter",
    "heading_level",
    "source_file",
)

_WORD_RE = re.compile(r"[\u4e00-\u9fffA-Za-z][\u4e00-\u9fffA-Za-z0-9\-_+/]*")
_PUNCT_RE = re.compile(r"[，。！？；：、,.!?;:()\[\]（）【】]")
_LIST_ITEM_RE = re.compile(r"(?:^|\n)\s*(?:[-*•]\s*)?(?:\d+[\.\)）、]|第[一二三四五六七八九十]+步)")
_ACRONYM_RE = re.compile(r"\b[A-Z]{2,}\b")
_FORMULA_RE = re.compile(r"(?:公式|定义为|可表示为|记为|定理|推导|=|:=|\$\$)")
_GENERIC_HEADING_RE = re.compile(r"^(?:习题|实验|实验报告|作业|思考题|复习题|课程说明|说明)$")

_TYPE_PRIORITY = {
    "definition_or_formula": 6.8,
    "algorithm_or_method": 6.4,
    "experiment_instruction": 6.5,
    "assignment_requirement": 6.1,
    "chapter_concept_explanation": 5.8,
    "course_requirement": 5.5,
    "reference_material": 4.8,
    "generic_explanation": 4.6,
    "unknown": 4.2,
}

_SAMPLE_TYPE_LABELS = {
    "chapter_concept_explanation": "章节/概念讲解",
    "definition_or_formula": "定义/公式",
    "algorithm_or_method": "方法/算法",
    "experiment_instruction": "实验说明",
    "assignment_requirement": "作业/习题要求",
    "course_requirement": "课程说明",
    "reference_material": "参考材料",
    "generic_explanation": "通用讲解",
    "unknown": "未知类型",
}

_KEYWORD_GROUPS = {
    "definition": ("定义", "定义为", "是指", "含义", "性质"),
    "formula": ("公式", "定理", "判定条件", "表示为", "记为"),
    "method": ("算法", "方法", "步骤", "流程", "策略", "机制", "实现"),
    "experiment": ("实验", "实验目的", "实验步骤", "实验环境", "实验要求"),
    "assignment": ("作业", "习题", "思考题", "提交要求", "评分标准"),
    "question": ("请完成", "请说明", "请分析", "请证明", "试说明", "何谓", "为什么"),
    "relation": ("依赖", "属于", "包含", "组成", "采用", "应用于", "用于", "需要", "包括", "映射"),
    "term": ("又称", "简称", "全称", "缩写", "英文", "别名"),
}

_TAG_LABELS = {
    "definition": "定义信号",
    "formula": "公式信号",
    "method": "方法/步骤信号",
    "experiment": "实验说明信号",
    "assignment": "作业/题组信号",
    "question": "问答/考核信号",
    "relation": "关系表达较密集",
    "term": "术语/别名信号",
    "equation": "含公式或数学表达",
    "step_list": "存在步骤或列表结构",
    "term_dense": "术语/缩写较密集",
    "boundary_case": "结构边界样本",
    "multi_page": "跨页样本",
}


@dataclass
class SchemaType:
    name: str
    label_zh: str = ""
    description: str = ""


@dataclass
class SchemaCatalog:
    entity_types: List[SchemaType]
    relation_types: List[SchemaType]
    entity_schema_path: str
    relation_schema_path: str


@dataclass
class AuditCandidate:
    source_sample_id: str
    source_doc_id: str
    source_file: str
    document_type: str
    course_id: str
    chapter: Optional[str]
    section: Optional[str]
    heading_path: List[str]
    heading_level: int
    text: str
    text_length: int
    page_start: int
    page_end: int
    guessed_sample_type: str
    metadata_summary: Dict[str, Any] = field(default_factory=dict)
    random_key: float = 0.0
    quality_score: float = 0.0
    complexity_tags: List[str] = field(default_factory=list)
    keyword_hits: Dict[str, int] = field(default_factory=dict)
    length_bucket: str = "medium"
    audit_priority: str = "medium"
    audit_reason: str = ""

    @property
    def section_hint(self) -> Optional[str]:
        return self.section or (self.heading_path[-1] if self.heading_path else None)

    @property
    def chapter_bucket(self) -> str:
        return self.chapter or self.section or (self.heading_path[0] if self.heading_path else "__missing__")


def _clean_string(value: Any) -> Optional[str]:
    if not isinstance(value, str):
        return None
    value = value.strip()
    return value or None


def _safe_int(value: Any, default: int = 0) -> int:
    if isinstance(value, bool):
        return default
    if isinstance(value, int):
        return value
    if isinstance(value, str):
        value = value.strip()
        if value.isdigit():
            return int(value)
    return default


def _clean_heading_path(value: Any) -> List[str]:
    if isinstance(value, list):
        items = [_clean_string(item) for item in value]
        return [item for item in items if item]
    if isinstance(value, str):
        if ">" in value:
            parts = [part.strip() for part in value.split(">")]
            return [part for part in parts if part]
        cleaned = value.strip()
        return [cleaned] if cleaned else []
    return []


def _resolve_heading_path(record: Dict[str, Any]) -> List[str]:
    heading_path = _clean_heading_path(record.get("heading_path"))
    if heading_path:
        return heading_path

    heading_path_text = _clean_string(record.get("heading_path_text"))
    if heading_path_text:
        return _clean_heading_path(heading_path_text)

    candidates = [
        _clean_string(record.get("chapter")),
        _clean_string(record.get("section")),
    ]
    return [item for item in candidates if item]


def _resolve_source_doc_id(record: Dict[str, Any], heading_path: List[str], page_start: int, page_end: int) -> str:
    for key in ("source_doc_id", "doc_id", "id"):
        value = _clean_string(record.get(key))
        if value:
            return value

    payload = "|".join(
        [
            _clean_string(record.get("course_id")) or "unknown_course",
            _clean_string(record.get("source_file")) or "unknown_file",
            " > ".join(heading_path) or "untitled",
            str(page_start),
            str(page_end),
        ]
    )
    digest = hashlib.sha1(payload.encode("utf-8")).hexdigest()[:12]
    return f"derived-doc-{digest}"


def _resolve_source_sample_id(record: Dict[str, Any], source_doc_id: str) -> str:
    for key in ("sample_id", "source_sample_id", "id"):
        value = _clean_string(record.get(key))
        if value:
            return value

    digest = hashlib.sha1(source_doc_id.encode("utf-8")).hexdigest()[:10]
    return f"sample-{digest}"


def _guess_sample_type_from_text(record: Dict[str, Any], text: str, heading_path: List[str]) -> str:
    guessed = _clean_string(record.get("guessed_sample_type"))
    if guessed:
        return guessed

    title = " ".join(heading_path[-2:]) if heading_path else ""
    merged = f"{title}\n{text[:1200]}"
    if any(keyword in merged for keyword in ("实验目的", "实验步骤", "实验要求", "实验环境")):
        return "experiment_instruction"
    if any(keyword in merged for keyword in ("作业", "习题", "思考题", "提交要求", "评分标准")):
        return "assignment_requirement"
    if any(keyword in merged for keyword in ("定义", "定理", "公式", "表示为", "记为")):
        return "definition_or_formula"
    if any(keyword in merged for keyword in ("算法", "方法", "步骤", "流程", "策略", "机制")):
        return "algorithm_or_method"
    if any(keyword in merged for keyword in ("课程简介", "课程目标", "学习目标", "课程安排")):
        return "course_requirement"
    if heading_path or _clean_string(record.get("section")) or _clean_string(record.get("chapter")):
        return "chapter_concept_explanation"
    return "unknown"


def _compute_length_bucket(text_length: int) -> str:
    if text_length < 260:
        return "short"
    if text_length < 700:
        return "medium"
    if text_length < 1400:
        return "long"
    return "xlong"


def _extract_keyword_hits(text: str) -> Dict[str, int]:
    hits: Dict[str, int] = {}
    for name, keywords in _KEYWORD_GROUPS.items():
        count = sum(1 for keyword in keywords if keyword in text)
        if count > 0:
            hits[name] = count
    return hits


def _build_complexity_tags(
    text: str,
    heading_path: List[str],
    metadata_summary: Dict[str, Any],
    keyword_hits: Dict[str, int],
    page_start: int,
    page_end: int,
) -> List[str]:
    tags: List[str] = []

    for name in ("definition", "formula", "method", "experiment", "assignment", "question", "relation", "term"):
        if keyword_hits.get(name):
            tags.append(name)

    if metadata_summary.get("has_equation") or _FORMULA_RE.search(text):
        tags.append("equation")
    if _LIST_ITEM_RE.search(text):
        tags.append("step_list")
    if len(_ACRONYM_RE.findall(text)) >= 2:
        tags.append("term_dense")
    if page_end > page_start:
        tags.append("multi_page")

    leaf_heading = heading_path[-1] if heading_path else ""
    if _GENERIC_HEADING_RE.match(leaf_heading) or (heading_path and len(heading_path) >= 2 and len(leaf_heading) <= 6):
        tags.append("boundary_case")

    deduplicated: List[str] = []
    for tag in tags:
        if tag not in deduplicated:
            deduplicated.append(tag)
    return deduplicated


def _compute_quality_score(candidate: AuditCandidate) -> float:
    score = _TYPE_PRIORITY.get(candidate.guessed_sample_type, _TYPE_PRIORITY["unknown"])

    if candidate.length_bucket == "medium":
        score += 1.1
    elif candidate.length_bucket == "long":
        score += 1.5
    elif candidate.length_bucket == "xlong":
        score += 1.0
    else:
        score += 0.4

    if candidate.heading_level == 2:
        score += 0.7
    elif candidate.heading_level == 3:
        score += 0.9
    elif candidate.heading_level >= 4:
        score += 0.5
    else:
        score += 0.2

    word_count = len(_WORD_RE.findall(candidate.text))
    punct_count = len(_PUNCT_RE.findall(candidate.text))
    punctuation_density = punct_count / max(candidate.text_length, 1)
    if word_count >= 90:
        score += 0.4
    if punctuation_density >= 0.08:
        score += 0.3

    score += min(sum(candidate.keyword_hits.values()) * 0.22, 1.8)

    for tag in candidate.complexity_tags:
        if tag in {"equation", "step_list", "term_dense", "boundary_case", "multi_page"}:
            score += 0.35

    if candidate.metadata_summary.get("has_table"):
        score += 0.15
    if candidate.metadata_summary.get("has_image"):
        score += 0.1

    return round(score, 4)


def _determine_audit_priority(candidate: AuditCandidate) -> str:
    score = candidate.quality_score
    tags = set(candidate.complexity_tags)
    if score >= 12.0 or (score >= 10.8 and {"equation", "boundary_case"} & tags and len(tags) >= 3):
        return "high"
    if score >= 9.5 or len(tags) >= 3:
        return "medium"
    return "low"


def _resolve_record_text(record: Dict[str, Any]) -> str:
    for field in ("text", "content"):
        value = record.get(field)
        if isinstance(value, str) and value.strip():
            return value.strip()
    return ""


def discover_input_file(input_file: Optional[str]) -> Path:
    if input_file:
        path = Path(input_file).expanduser()
        if not path.is_absolute():
            path = (Path.cwd() / path).resolve()
        if path.is_dir():
            for candidate_name in ("samples.json", "prompt_tuning_samples.json", "prompt_tuning_samples.preview.json"):
                candidate = path / candidate_name
                if candidate.exists():
                    return candidate
            json_files = sorted(path.glob("*.json"))
            if json_files:
                return json_files[0]
            raise FileNotFoundError(f"输入目录中未发现 JSON 文件: {path}")
        if not path.exists():
            raise FileNotFoundError(f"输入文件不存在: {path}")
        return path

    for candidate_rel in DEFAULT_INPUT_CANDIDATES:
        candidate = PROJECT_ROOT / candidate_rel
        if candidate.exists():
            return candidate

    sample_dir = PROJECT_ROOT / "data" / "prompt_tuning_samples"
    if sample_dir.exists():
        json_files = sorted(sample_dir.glob("*.json"))
        if json_files:
            return json_files[0]

    raise FileNotFoundError(
        "未发现 prompt tuning 样本输入。"
        f"已尝试: {', '.join(DEFAULT_INPUT_CANDIDATES)}"
    )


def load_sample_records(path: Path) -> List[Dict[str, Any]]:
    payload = json.loads(path.read_text(encoding="utf-8"))

    if isinstance(payload, dict):
        for key in ("samples", "records", "data"):
            value = payload.get(key)
            if isinstance(value, list):
                payload = value
                break

    if not isinstance(payload, list):
        raise ValueError(f"{path} 不是样本数组，也不包含 samples/records/data 列表")

    records: List[Dict[str, Any]] = []
    for index, item in enumerate(payload, start=1):
        if not isinstance(item, dict):
            raise ValueError(f"{path} 第 {index} 条记录不是对象")
        records.append(item)
    return records


def adapt_sample_record(record: Dict[str, Any], rng: random.Random) -> tuple[Optional[AuditCandidate], List[str]]:
    errors: List[str] = []

    text = _resolve_record_text(record)
    if not text:
        errors.append("缺少 text/content")

    heading_path = _resolve_heading_path(record)
    if not heading_path:
        errors.append("缺少 heading_path/heading_path_text/chapter/section")

    source_file = _clean_string(record.get("source_file")) or ""
    if not source_file:
        errors.append("缺少 source_file")

    course_id = _clean_string(record.get("course_id")) or ""
    if not course_id:
        errors.append("缺少 course_id")

    page_start = _safe_int(record.get("page_start"), default=0)
    page_end = _safe_int(record.get("page_end"), default=page_start)
    if page_start <= 0:
        errors.append("page_start 非法")
    if page_end <= 0:
        errors.append("page_end 非法")
    if page_start > 0 and page_end > 0 and page_end < page_start:
        errors.append("page_end 小于 page_start")

    if errors:
        return None, errors

    metadata_summary = record.get("metadata_summary")
    if not isinstance(metadata_summary, dict):
        metadata_summary = {}

    source_doc_id = _resolve_source_doc_id(record, heading_path, page_start, page_end)
    source_sample_id = _resolve_source_sample_id(record, source_doc_id)
    guessed_sample_type = _guess_sample_type_from_text(record, text, heading_path)

    candidate = AuditCandidate(
        source_sample_id=source_sample_id,
        source_doc_id=source_doc_id,
        source_file=source_file,
        document_type=_clean_string(record.get("document_type")) or "unknown",
        course_id=course_id,
        chapter=_clean_string(record.get("chapter")),
        section=_clean_string(record.get("section")),
        heading_path=heading_path,
        heading_level=max(1, _safe_int(record.get("heading_level"), default=len(heading_path) or 1)),
        text=text,
        text_length=_safe_int(record.get("text_length"), default=len(text)),
        page_start=page_start,
        page_end=page_end,
        guessed_sample_type=guessed_sample_type,
        metadata_summary=metadata_summary,
        random_key=rng.random(),
    )

    candidate.length_bucket = _compute_length_bucket(candidate.text_length)
    candidate.keyword_hits = _extract_keyword_hits(candidate.text[:2000])
    candidate.complexity_tags = _build_complexity_tags(
        text=candidate.text[:2000],
        heading_path=candidate.heading_path,
        metadata_summary=candidate.metadata_summary,
        keyword_hits=candidate.keyword_hits,
        page_start=candidate.page_start,
        page_end=candidate.page_end,
    )
    candidate.quality_score = _compute_quality_score(candidate)
    candidate.audit_priority = _determine_audit_priority(candidate)
    return candidate, []


def load_audit_candidates(path: Path, random_seed: int) -> tuple[List[AuditCandidate], Counter, int]:
    rng = random.Random(random_seed)
    candidates: List[AuditCandidate] = []
    skipped_reasons: Counter = Counter()

    records = load_sample_records(path)
    for record in records:
        candidate, errors = adapt_sample_record(record, rng=rng)
        if candidate is None:
            for error in errors or ["unknown_validation_failure"]:
                skipped_reasons[error] += 1
            continue
        candidates.append(candidate)

    return candidates, skipped_reasons, len(records)


def _field_value(candidate: AuditCandidate, field_name: str) -> str:
    if field_name == "guessed_sample_type":
        return candidate.guessed_sample_type or "unknown"
    if field_name == "document_type":
        return candidate.document_type or "unknown"
    if field_name == "chapter":
        return candidate.chapter_bucket
    if field_name == "heading_level":
        return str(candidate.heading_level)
    if field_name == "source_file":
        return candidate.source_file or "unknown"
    if field_name == "length_bucket":
        return candidate.length_bucket
    return _clean_string(getattr(candidate, field_name, None)) or "__missing__"


def _parse_priority_fields(values: Optional[Sequence[str]]) -> List[str]:
    if not values:
        return list(DEFAULT_PRIORITY_FIELDS)

    parsed: List[str] = []
    for value in values:
        for part in str(value).split(","):
            part = part.strip()
            if part and part not in parsed:
                parsed.append(part)
    return parsed or list(DEFAULT_PRIORITY_FIELDS)


def _build_stratification_summary(candidates: Sequence[AuditCandidate], priority_fields: Sequence[str]) -> Dict[str, Dict[str, Any]]:
    summary: Dict[str, Dict[str, Any]] = {}
    for field_name in list(priority_fields) + ["length_bucket"]:
        counter = Counter(_field_value(candidate, field_name) for candidate in candidates)
        counter.pop("__missing__", None)
        counter.pop("", None)
        summary[field_name] = {
            "distinct_count": len(counter),
            "distribution": dict(counter.most_common()),
        }
    return summary


def _should_fallback_to_random(
    candidates: Sequence[AuditCandidate],
    priority_fields: Sequence[str],
    prefer_balanced_sampling: bool,
) -> bool:
    if not prefer_balanced_sampling:
        return True
    if not candidates:
        return True

    summary = _build_stratification_summary(candidates, priority_fields)
    active_dimensions = sum(1 for info in summary.values() if info["distinct_count"] > 1)
    return active_dimensions == 0


def _build_selection_reason(candidate: AuditCandidate) -> str:
    reasons: List[str] = []
    reasons.append(f"覆盖样本类型：{_SAMPLE_TYPE_LABELS.get(candidate.guessed_sample_type, candidate.guessed_sample_type)}")
    reasons.append(f"覆盖长度区间：{candidate.length_bucket}")
    reasons.append(f"结构层级：heading_level={candidate.heading_level}")

    if candidate.chapter_bucket and candidate.chapter_bucket != "__missing__":
        reasons.append(f"覆盖章节/标题边界：{candidate.chapter_bucket}")

    high_value_tags = [_TAG_LABELS[tag] for tag in candidate.complexity_tags if tag in _TAG_LABELS]
    if high_value_tags:
        reasons.append("命中高价值信号：" + "、".join(high_value_tags[:4]))

    if candidate.section_hint and candidate.section_hint != candidate.chapter_bucket:
        reasons.append(f"核心片段：{candidate.section_hint}")

    return "；".join(reasons[:5])


def _candidate_adjusted_score(
    candidate: AuditCandidate,
    field_counters: Dict[str, Counter],
    global_counters: Dict[str, Counter],
) -> float:
    score = candidate.quality_score
    for field_name, counter in field_counters.items():
        value = _field_value(candidate, field_name)
        if not value or value == "__missing__":
            continue
        if counter[value] == 0:
            score += 1.1
        else:
            score -= counter[value] * 0.22

        global_count = global_counters[field_name][value]
        if global_count > 0:
            score += 0.55 / global_count

    if candidate.audit_priority == "high":
        score += 0.6
    elif candidate.audit_priority == "medium":
        score += 0.25

    score += candidate.random_key * 0.05
    return score


def _register_selected_candidate(
    candidate: AuditCandidate,
    selected: List[AuditCandidate],
    selected_ids: set[str],
    field_counters: Dict[str, Counter],
) -> None:
    candidate.audit_reason = _build_selection_reason(candidate)
    selected.append(candidate)
    selected_ids.add(candidate.source_sample_id)
    for field_name, counter in field_counters.items():
        counter[_field_value(candidate, field_name)] += 1


def _select_balanced_candidates(
    candidates: Sequence[AuditCandidate],
    sample_size: int,
    priority_fields: Sequence[str],
) -> List[AuditCandidate]:
    ordered = sorted(
        candidates,
        key=lambda item: (-item.quality_score, item.random_key, item.source_sample_id),
    )

    selected: List[AuditCandidate] = []
    selected_ids: set[str] = set()
    field_counters: Dict[str, Counter] = {
        field_name: Counter() for field_name in list(priority_fields) + ["length_bucket"]
    }
    global_counters: Dict[str, Counter] = {
        field_name: Counter(_field_value(candidate, field_name) for candidate in ordered)
        for field_name in field_counters
    }

    # 第一轮：优先确保 sample_type 与长度桶都有覆盖。
    for field_name in ("guessed_sample_type", "length_bucket"):
        if field_name not in field_counters:
            continue
        values = [
            value for value, count in global_counters[field_name].most_common()
            if value not in {"", "__missing__"} and count > 0
        ]
        for value in values:
            if len(selected) >= sample_size:
                return selected
            for candidate in ordered:
                if candidate.source_sample_id in selected_ids:
                    continue
                if _field_value(candidate, field_name) != value:
                    continue
                _register_selected_candidate(candidate, selected, selected_ids, field_counters)
                break

    while len(selected) < sample_size:
        best_candidate: Optional[AuditCandidate] = None
        best_score: Optional[float] = None

        for candidate in ordered:
            if candidate.source_sample_id in selected_ids:
                continue
            score = _candidate_adjusted_score(
                candidate,
                field_counters=field_counters,
                global_counters=global_counters,
            )
            if best_score is None or score > best_score:
                best_candidate = candidate
                best_score = score

        if best_candidate is None:
            break

        _register_selected_candidate(best_candidate, selected, selected_ids, field_counters)

    return selected


def _select_random_candidates(
    candidates: Sequence[AuditCandidate],
    sample_size: int,
) -> List[AuditCandidate]:
    ordered = sorted(candidates, key=lambda item: (item.random_key, item.source_sample_id))
    selected = list(ordered[:sample_size])
    for candidate in selected:
        candidate.audit_reason = (
            "分层字段不足，按随机种子回退为随机抽样；"
            f"保留样本类型={_SAMPLE_TYPE_LABELS.get(candidate.guessed_sample_type, candidate.guessed_sample_type)}；"
            f"长度区间={candidate.length_bucket}"
        )
    return selected


def select_audit_candidates(
    candidates: Sequence[AuditCandidate],
    sample_size: int,
    priority_fields: Sequence[str],
    prefer_balanced_sampling: bool,
) -> tuple[List[AuditCandidate], str, bool]:
    if sample_size <= 0 or not candidates:
        return [], "empty", True

    fallback_to_random = _should_fallback_to_random(
        candidates=candidates,
        priority_fields=priority_fields,
        prefer_balanced_sampling=prefer_balanced_sampling,
    )
    if fallback_to_random:
        return _select_random_candidates(candidates, sample_size), "random_fallback", True

    return _select_balanced_candidates(candidates, sample_size, priority_fields), "balanced_stratified", False


def _make_audit_id(candidate: AuditCandidate, index: int) -> str:
    payload = f"{candidate.source_sample_id}|{candidate.source_doc_id}|{candidate.page_start}|{candidate.page_end}"
    digest = hashlib.sha1(payload.encode("utf-8")).hexdigest()[:10]
    return f"audit-ext-{index:04d}-{digest}"


def _sample_to_output(candidate: AuditCandidate, index: int) -> Dict[str, Any]:
    return {
        "id": _make_audit_id(candidate, index),
        "source_sample_id": candidate.source_sample_id,
        "source_doc_id": candidate.source_doc_id,
        "source_file": candidate.source_file,
        "document_type": candidate.document_type,
        "course_id": candidate.course_id,
        "chapter": candidate.chapter,
        "section": candidate.section,
        "heading_path": list(candidate.heading_path),
        "heading_level": candidate.heading_level,
        "guessed_sample_type": candidate.guessed_sample_type,
        "text": candidate.text,
        "text_length": candidate.text_length,
        "page_start": candidate.page_start,
        "page_end": candidate.page_end,
        "audit_priority": candidate.audit_priority,
        "audit_reason": candidate.audit_reason,
        "gold_entities": [],
        "gold_relations": [],
        "annotation_notes": "",
        "reviewer_decision": "",
        "reviewer_confidence": "",
    }


def _resolve_schema_items(payload: Any, collection_key: str, order_key: str) -> List[SchemaType]:
    if isinstance(payload, dict):
        collection = payload.get(collection_key)
        if isinstance(collection, dict):
            order = payload.get(order_key)
            ordered_names: List[str] = []
            if isinstance(order, list):
                ordered_names.extend([str(item) for item in order if str(item) in collection])
            ordered_names.extend(sorted(name for name in collection.keys() if name not in ordered_names))
            items: List[SchemaType] = []
            for name in ordered_names:
                config = collection.get(name, {})
                if not isinstance(config, dict):
                    config = {}
                items.append(
                    SchemaType(
                        name=name,
                        label_zh=str(config.get("label_zh", "")).strip(),
                        description=str(config.get("description", "")).strip(),
                    )
                )
            return items

        if all(isinstance(value, dict) for value in payload.values()):
            return [
                SchemaType(
                    name=str(name),
                    label_zh=str(value.get("label_zh", "")).strip(),
                    description=str(value.get("description", "")).strip(),
                )
                for name, value in sorted(payload.items())
            ]

    if isinstance(payload, list):
        items: List[SchemaType] = []
        for item in payload:
            if not isinstance(item, dict):
                continue
            name = (
                _clean_string(item.get("name"))
                or _clean_string(item.get("type"))
                or _clean_string(item.get("id"))
            )
            if not name:
                continue
            items.append(
                SchemaType(
                    name=name,
                    label_zh=_clean_string(item.get("label_zh")) or "",
                    description=_clean_string(item.get("description")) or "",
                )
            )
        return items

    return []


def load_schema_catalog(
    entity_schema_path: Path = DEFAULT_ENTITY_SCHEMA,
    relation_schema_path: Path = DEFAULT_RELATION_SCHEMA,
) -> SchemaCatalog:
    entity_payload = json.loads(entity_schema_path.read_text(encoding="utf-8"))
    relation_payload = json.loads(relation_schema_path.read_text(encoding="utf-8"))

    entity_types = _resolve_schema_items(entity_payload, "entity_types", "entity_type_order")
    relation_types = _resolve_schema_items(relation_payload, "relation_types", "relation_type_order")

    return SchemaCatalog(
        entity_types=entity_types,
        relation_types=relation_types,
        entity_schema_path=str(entity_schema_path),
        relation_schema_path=str(relation_schema_path),
    )


def build_annotation_guidelines(schema_catalog: SchemaCatalog, sample_size: int) -> str:
    entity_lines = [
        f"- `{item.name}`"
        + (f"：{item.label_zh}" if item.label_zh else "")
        + (f"。{item.description}" if item.description else "")
        for item in schema_catalog.entity_types
    ]
    relation_lines = [
        f"- `{item.name}`"
        + (f"：{item.label_zh}" if item.label_zh else "")
        + (f"。{item.description}" if item.description else "")
        for item in schema_catalog.relation_types
    ]

    return f"""# Audit 抽取校准集标注说明

## 1. audit 集用途

本 audit 集是一个**小规模、高价值、可复用**的抽取校准集，主要用于：

1. 规则化自动评测结果的人工校准与误差解释。
2. 候选 Prompt 分数接近时的人工裁决。
3. 在论文与实验报告中，为评测可信度提供小规模人工支撑。

注意：

1. 本数据集不是全量 gold set。
2. 本数据集不是全量 silver set。
3. 本数据集不替代后续问答评测集。
4. 当前推荐审查规模约为 `{sample_size}` 条，强调“小而精”，而不是追求数量。

## 2. 实体标注原则

1. 只标注对课程问答、知识建图和教学评测真正有价值的实体。
2. 优先标注稳定、可复用、可归一化的教学结构实体、知识实体、学习活动实体和工具平台实体。
3. 优先使用课程正文里的正式名称，不要把整句解释直接当实体名。
4. 若同一文本片段中同时出现全称与缩写，应合并为同一实体，缩写放入 `alias`。
5. 如果某个片段只是排版、引导语或装饰性标题，不应强行标注为实体。

## 3. 关系标注原则

1. 先确认实体边界，再标关系。
2. 只标有明确语义支撑的关系，不把普通共现都标成关系。
3. 优先选择最具体的关系类型；只有无法确定更强语义时，才使用保底关系。
4. `evidence_text` 应尽量截取最能支撑关系的短证据，而不是整段全文复制。

## 4. 类型来源说明

实体类型来源于 `{schema_catalog.entity_schema_path}`：

{chr(10).join(entity_lines) if entity_lines else "- 未读取到实体类型，请检查 schema 文件。"}

关系类型来源于 `{schema_catalog.relation_schema_path}`：

{chr(10).join(relation_lines) if relation_lines else "- 未读取到关系类型，请检查 schema 文件。"}

要求：

1. `gold_entities[].type` 必须对齐 `config/schema/entity_types.json`。
2. `gold_relations[].type` 必须对齐 `config/schema/relation_types.json`。
3. 若文本无法支撑某类型，请不要为了凑类型而强行标注。

## 5. 别名与规范名处理

1. `name` 填写当前文本中最自然、最贴近原文的实体名称。
2. `normalized_name` 填写课程内更稳定、可复用的规范名。
3. `alias` 用于记录简称、英文名、缩写、同义说法。
4. 若原文仅出现缩写，但上下文能明确对应全称，可在 `normalized_name` 中写全称，并把缩写保留在 `alias`。

## 6. 重复实体处理

1. 同一条样本内若同一实体多次出现，只保留一个实体对象。
2. 若同名对象在上下文中语义不同，应拆分为不同实体，并在 `notes` 中说明原因。
3. 若实体边界存在争议，优先选择课程内更稳定、更利于问答复用的边界。

## 7. 哪些内容不应标注

以下内容默认不应标注：

1. 无意义残片。
2. 页码、页眉页脚、目录残留。
3. 纯装饰性文本、排版占位、OCR 乱码。
4. 过短且无语义信息的片段。
5. 没有稳定名称的图号、表号、零散公式碎片。
6. 与课程知识目标无关的出版信息、版权信息、通知碎片。

## 8. 建议审核方式

1. 先审实体，再审关系。
2. 优先审查 `audit_priority=high` 的样本。
3. 若规则化自动评分与人工直觉冲突，应在 `annotation_notes` 中记录冲突原因。
4. 若实体类型与关系类型都不确定，先记录实体，再暂缓关系。
5. 若样本存在结构边界歧义，应在 `reviewer_decision` 或 `annotation_notes` 中显式说明。

## 9. 模板字段建议

### 9.1 实体标注字段

```json
{{
  "entity_id": "ent-001",
  "name": "进程",
  "type": "Concept",
  "alias": ["process"],
  "span_text": "进程",
  "normalized_name": "进程",
  "notes": ""
}}
```

### 9.2 关系标注字段

```json
{{
  "relation_id": "rel-001",
  "source_entity_id": "ent-001",
  "target_entity_id": "ent-002",
  "type": "defined_by",
  "evidence_text": "所谓工作集，是指在某段时间间隔里进程实际要访问页面的集合。",
  "notes": ""
}}
```

## 10. 建议标注流程

1. 阅读样本标题、章节和 `audit_reason`，先判断该样本为什么被选入。
2. 通读正文，抽取稳定实体，补齐 `gold_entities`。
3. 复核实体边界与类型，再补齐 `gold_relations`。
4. 记录疑难点与人工判断，在 `annotation_notes` 中保留说明。
5. 完成后填写 `reviewer_decision` 与 `reviewer_confidence`。
"""


def _schema_reference(schema_catalog: SchemaCatalog) -> Dict[str, Any]:
    return {
        "entity_schema_path": schema_catalog.entity_schema_path,
        "relation_schema_path": schema_catalog.relation_schema_path,
        "entity_type_names": [item.name for item in schema_catalog.entity_types],
        "relation_type_names": [item.name for item in schema_catalog.relation_types],
    }


def build_audit_dataset(
    input_file: Path,
    sample_size: int,
    random_seed: int,
    prefer_balanced_sampling: bool,
    priority_fields: Sequence[str],
    schema_catalog: SchemaCatalog,
) -> tuple[Dict[str, Any], Dict[str, Any], str]:
    candidates, skipped_reasons, input_sample_count = load_audit_candidates(
        path=input_file,
        random_seed=random_seed,
    )
    selected, selection_strategy, fallback_to_random = select_audit_candidates(
        candidates=candidates,
        sample_size=sample_size,
        priority_fields=priority_fields,
        prefer_balanced_sampling=prefer_balanced_sampling,
    )
    stratification_summary = _build_stratification_summary(candidates, priority_fields)

    dataset = {
        "schema_version": "v1",
        "generated_at": datetime.now().isoformat(timespec="seconds"),
        "task": "audit_extraction_set",
        "source_input": str(input_file),
        "selection_parameters": {
            "sample_size": sample_size,
            "random_seed": random_seed,
            "prefer_balanced_sampling": prefer_balanced_sampling,
            "priority_fields": list(priority_fields),
        },
        "schema_reference": _schema_reference(schema_catalog),
        "stats": {
            "input_sample_count": input_sample_count,
            "valid_candidate_count": len(candidates),
            "selected_count": len(selected),
            "skipped_count": sum(skipped_reasons.values()),
            "skipped_reasons": dict(skipped_reasons),
            "selection_strategy": selection_strategy,
            "fallback_to_random": fallback_to_random,
            "priority_distribution": dict(Counter(item.audit_priority for item in selected)),
            "guessed_sample_type_distribution": dict(Counter(item.guessed_sample_type for item in selected)),
            "document_type_distribution": dict(Counter(item.document_type for item in selected)),
            "source_file_distribution": dict(Counter(item.source_file for item in selected)),
            "heading_level_distribution": dict(Counter(str(item.heading_level) for item in selected)),
            "length_bucket_distribution": dict(Counter(item.length_bucket for item in selected)),
            "stratification_summary": stratification_summary,
        },
        "audit_samples": [_sample_to_output(candidate, index) for index, candidate in enumerate(selected, start=1)],
    }

    report = {
        "schema_version": "v1",
        "generated_at": dataset["generated_at"],
        "input_file": str(input_file),
        "selection_strategy": selection_strategy,
        "fallback_to_random": fallback_to_random,
        "selection_parameters": dataset["selection_parameters"],
        "stats": dataset["stats"],
        "selected_examples": [
            {
                "id": _make_audit_id(candidate, index),
                "source_sample_id": candidate.source_sample_id,
                "guessed_sample_type": candidate.guessed_sample_type,
                "audit_priority": candidate.audit_priority,
                "length_bucket": candidate.length_bucket,
                "audit_reason": candidate.audit_reason,
            }
            for index, candidate in enumerate(selected[:10], start=1)
        ],
    }
    guidelines = build_annotation_guidelines(schema_catalog=schema_catalog, sample_size=len(selected))
    return dataset, report, guidelines


def parse_args(argv: Optional[Sequence[str]] = None) -> argparse.Namespace:
    parser = argparse.ArgumentParser(
        description="从 prompt tuning 样本中构建小规模 audit 抽取校准集",
        formatter_class=argparse.RawDescriptionHelpFormatter,
        epilog="""
示例:
  python scripts/build_audit_extraction_set.py
  python scripts/build_audit_extraction_set.py --input_file ./data/prompt_tuning_samples/prompt_tuning_samples.json --sample_size 16
  python scripts/build_audit_extraction_set.py --prefer_balanced_sampling --priority_fields guessed_sample_type chapter source_file
        """,
    )
    parser.add_argument(
        "--input_file",
        default=None,
        help="prompt tuning 样本文件或目录。未提供时自动发现 samples.json / prompt_tuning_samples.json / preview 文件。",
    )
    parser.add_argument(
        "--output_file",
        default=str(DEFAULT_OUTPUT_FILE),
        help=f"audit 集 JSON 输出路径，默认 {DEFAULT_OUTPUT_FILE}",
    )
    parser.add_argument(
        "--guideline_file",
        default=str(DEFAULT_GUIDELINE_FILE),
        help=f"标注说明输出路径，默认 {DEFAULT_GUIDELINE_FILE}",
    )
    parser.add_argument(
        "--report_file",
        default=str(DEFAULT_REPORT_FILE),
        help=f"采样报告输出路径，默认 {DEFAULT_REPORT_FILE}",
    )
    parser.add_argument(
        "--sample_size",
        type=int,
        default=DEFAULT_SAMPLE_SIZE,
        help=f"audit 样本规模，默认 {DEFAULT_SAMPLE_SIZE}",
    )
    parser.add_argument(
        "--random_seed",
        type=int,
        default=42,
        help="随机种子，默认 42",
    )
    parser.add_argument(
        "--prefer_balanced_sampling",
        action=argparse.BooleanOptionalAction,
        default=True,
        help="是否优先使用分层均衡抽样，默认开启",
    )
    parser.add_argument(
        "--priority_fields",
        nargs="*",
        default=list(DEFAULT_PRIORITY_FIELDS),
        help="优先用于分层覆盖的字段，支持空格分隔或逗号分隔",
    )
    return parser.parse_args(argv)


def _resolve_output_path(path_value: str) -> Path:
    path = Path(path_value).expanduser()
    if not path.is_absolute():
        path = (Path.cwd() / path).resolve()
    path.parent.mkdir(parents=True, exist_ok=True)
    return path


def _print_summary(dataset: Dict[str, Any], output_file: Path, guideline_file: Path, report_file: Path) -> None:
    stats = dataset["stats"]
    print("[audit 校准集构建] 完成")
    print(f"  输入文件: {dataset['source_input']}")
    print(f"  输出 JSON: {output_file}")
    print(f"  标注说明: {guideline_file}")
    print(f"  采样报告: {report_file}")
    print(f"  输入样本数: {stats['input_sample_count']}")
    print(f"  有效候选数: {stats['valid_candidate_count']}")
    print(f"  最终选中数: {stats['selected_count']}")
    print(f"  采样策略: {stats['selection_strategy']}")
    print(f"  priority 分布: {stats['priority_distribution']}")
    print(f"  sample_type 分布: {stats['guessed_sample_type_distribution']}")
    print(f"  length_bucket 分布: {stats['length_bucket_distribution']}")


def main(argv: Optional[Sequence[str]] = None) -> int:
    args = parse_args(argv)

    if args.sample_size <= 0:
        raise ValueError("--sample_size 必须大于 0")

    input_file = discover_input_file(args.input_file)
    schema_catalog = load_schema_catalog()
    priority_fields = _parse_priority_fields(args.priority_fields)

    dataset, report, guidelines = build_audit_dataset(
        input_file=input_file,
        sample_size=args.sample_size,
        random_seed=args.random_seed,
        prefer_balanced_sampling=args.prefer_balanced_sampling,
        priority_fields=priority_fields,
        schema_catalog=schema_catalog,
    )

    output_file = _resolve_output_path(args.output_file)
    guideline_file = _resolve_output_path(args.guideline_file)
    report_file = _resolve_output_path(args.report_file)

    output_file.write_text(json.dumps(dataset, ensure_ascii=False, indent=2), encoding="utf-8")
    guideline_file.write_text(guidelines, encoding="utf-8")
    report_file.write_text(json.dumps(report, ensure_ascii=False, indent=2), encoding="utf-8")

    _print_summary(dataset, output_file, guideline_file, report_file)
    return 0


if __name__ == "__main__":
    sys.exit(main())
