#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
候选 Prompt 生成脚本
===================

负责统一组织“课程知识图谱抽取”任务的多组候选 Prompt。

职责边界：
1. 读取默认 Prompt、schema、样本与 audit 数据。
2. 生成 default / default_guarded / auto_tuned / schema_aware / schema_fewshot /
   schema_aware_directional / schema_fewshot_distilled /
   schema_fewshot_distilled_v2 等候选 Prompt。
3. 把候选 Prompt、说明文件、manifest 与报告写入标准目录。

非职责：
1. 不执行实际抽取。
2. 不实现自动评分逻辑。
3. 不调用 BenchmarkQED 或后续 QA 验证。
"""

from __future__ import annotations

import argparse
import json
import re
from dataclasses import dataclass, field
from datetime import datetime, timezone
from pathlib import Path
from typing import Any, Dict, Iterable, List, Optional, Sequence


PROJECT_ROOT = Path(__file__).resolve().parents[2]

DEFAULT_SCHEMA_DIR = PROJECT_ROOT / "config" / "schema"
DEFAULT_OUTPUT_DIR = PROJECT_ROOT / "prompts" / "candidates"
DEFAULT_DEFAULT_PROMPT_DIR = PROJECT_ROOT / "prompts"
DEFAULT_AUTO_TUNED_PROMPT_DIR = DEFAULT_OUTPUT_DIR / "auto_tuned"
DEFAULT_REPORT_FILE = PROJECT_ROOT / "results" / "reports" / "prompt_generation_report.json"

DEFAULT_SAMPLES_CANDIDATES: tuple[Path, ...] = (
    PROJECT_ROOT / "data" / "prompt_tuning_samples" / "samples.json",
    PROJECT_ROOT / "data" / "prompt_tuning_samples" / "prompt_tuning_samples.json",
    PROJECT_ROOT / "data" / "prompt_tuning_samples" / "prompt_tuning_samples.preview.json",
)
DEFAULT_AUDIT_CANDIDATES: tuple[Path, ...] = (
    PROJECT_ROOT / "data" / "eval" / "audit_extraction_set.json",
)
DEFAULT_FEWSHOT_K = 3
DEFAULT_LANGUAGE = "zh"
DEFAULT_FEWSHOT_INPUT_MAX_CHARS = 450
DEFAULT_FEWSHOT_MAX_ENTITIES = 3
DEFAULT_FEWSHOT_MAX_RELATIONS = 1
MAX_RELATION_EXAMPLE_COUNT = 2
MAX_RELATION_EXAMPLE_CHARS = 36
DISTILLED_MICRO_EXAMPLE_MAX_CHARS = 96
DISTILLED_MAX_MICRO_EXAMPLES = 8
DISTILLED_MAX_SCHEMA_AWARE_RATIO = 1.35
RELATED_TO_GENERIC_NEGATIVE_EXAMPLE = "不能用 related_to 代替缺失端点或更具体关系"
DISTILLED_V2_NEGATIVE_RULES: tuple[dict[str, str], ...] = (
    {
        "relation_type": "evaluated_by",
        "rule": "不要输出 Assignment -> Concept/Term 的 evaluated_by；正确方向：Concept/Term/KnowledgePoint/AlgorithmOrMethod -> Assignment；Term 只有被题目直接考核时才输出。",
    },
    {
        "relation_type": "appears_in",
        "rule": "不要输出 Section/Assignment -> Concept 的 appears_in；正确方向：知识实体 -> 位置容器或平台上下文。",
    },
    {
        "relation_type": "defined_by",
        "rule": "别名、简称、英文全称、存在标志、背景解释不用 defined_by；只保留公式、符号或判定条件。",
    },
    {
        "relation_type": "applied_in",
        "rule": "不要用 Concept -> AlgorithmOrMethod 表达“通过算法处理”；target 必须是主题、实验、作业或平台场景。",
    },
    {
        "relation_type": "related_to",
        "rule": "端点缺失时跳过 related_to；不能补 missing、unknown、N/A 或临时占位实体。",
    },
)
DISTILLED_V2_NEGATIVE_RULE_POLICY = {
    "strategy": "short_directional_negative_rules",
    "audit_text_policy": "omit_full_audit_text",
    "covered_relation_types": [
        "evaluated_by",
        "appears_in",
        "defined_by",
        "applied_in",
        "related_to",
    ],
}
V3_FAILURE_FAMILY_POLICY = {
    "strategy": "material_7_failure_family_guard",
    "audit_text_policy": "omit_full_audit_text",
    "families": [
        "appears_in_reverse_location",
        "belongs_to_concept_taxonomy",
        "defined_by_term_without_symbol_cue",
        "missing_or_unmatched_endpoint",
    ],
}
V3_TOKEN_BUDGET_POLICY = {
    "relative_to": "default",
    "max_ratio": 1.1,
    "audit_text_policy": "omit_full_audit_text",
}
TUPLE_DELIMITER = "<|>"
RECORD_DELIMITER = "##"
COMPLETION_DELIMITER = "<|COMPLETE|>"
DELIMITER_PLACEHOLDER_REPLACEMENTS = {
    "{tuple_delimiter}": TUPLE_DELIMITER,
    "{record_delimiter}": RECORD_DELIMITER,
    "{completion_delimiter}": COMPLETION_DELIMITER,
}
AUTO_TUNED_MANIFEST_PASSTHROUGH_FIELDS = (
    "generation_method",
    "graphrag_invocation",
    "status",
    "collected_files",
)
FEWSHOT_COMPRESSION_CAVEAT = (
    "本轮压缩参数优先控制 Prompt 长度；few-shot 关系覆盖不足需要后续真实抽取/评分验证，不能解读为抽取质量提升。"
)

DEFAULT_PROMPT_FILE_NAMES: tuple[str, ...] = (
    "extract_graph.txt",
    "prompt.txt",
    "extract_graph.prompt.txt",
)
AUTO_TUNED_FILE_KEYWORDS: tuple[str, ...] = (
    "extract_graph",
    "entity",
    "graph",
    "prompt",
)
OFFICIAL_AUTO_TUNED_SOURCE_TYPES = {"graphrag_prompt_tune"}
OFFICIAL_AUTO_TUNED_GENERATION_METHODS = {"graphrag_official_prompt_tune"}

FEWSHOT_CONCEPT_TYPES = {"definition_or_formula", "chapter_concept_explanation"}
FEWSHOT_ACTIVITY_TYPES = {
    "algorithm_or_method",
    "experiment_instruction",
    "assignment_requirement",
}
FEWSHOT_COVERAGE_SELECTION_STRATEGY = "greedy_relation_entity_coverage"
FEWSHOT_TYPE_PRIORITY = {
    "definition_or_formula": 0,
    "chapter_concept_explanation": 1,
    "algorithm_or_method": 2,
    "experiment_instruction": 3,
    "assignment_requirement": 4,
}
FEWSHOT_ENTITY_TYPE_PRIORITY = {
    "KnowledgePoint": 0,
    "Concept": 1,
    "FormulaOrDefinition": 2,
    "AlgorithmOrMethod": 3,
    "Experiment": 4,
    "Assignment": 5,
    "Term": 6,
    "ToolOrPlatform": 7,
    "Section": 8,
    "Chapter": 9,
    "Course": 10,
}
FEWSHOT_RELATION_TYPE_PRIORITY = {
    "defined_by": 0,
    "implemented_by": 1,
    "applied_in": 2,
    "evaluated_by": 3,
    "depends_on": 4,
    "prerequisite_of": 5,
    "contains": 6,
    "belongs_to": 7,
    "appears_in": 8,
    "related_to": 99,
}
DIRECTIONAL_RELATION_NAMES = ("applied_in", "appears_in", "defined_by", "evaluated_by", "related_to")
DIRECTIONAL_RELATION_HINTS = {
    "applied_in": {
        "source": "source 是被应用的知识/方法/公式",
        "target": "target 是知识主题、实验、作业或平台操作场景",
        "negative": "反例：不要写成“实验/作业 -> 算法”；弱共现仍用 related_to 或跳过。",
    },
    "appears_in": {
        "source": "source 是出现的实体",
        "target": "target 是 Course/Chapter/Section/Experiment/Assignment/ToolOrPlatform 上下文",
        "negative": "反例：不要写成“章节 -> 知识点”；结构包含优先用 contains。",
    },
    "defined_by": {
        "source": "source 是被定义对象",
        "target": "target 是定义、公式、判定条件或符号",
        "negative": "反例：不要写成“定义/公式 -> 概念”，也不要用 Concept->Concept 承接背景解释。",
    },
    "evaluated_by": {
        "source": "source 是被考核或评估的知识、概念、术语或方法",
        "target": "target 是 Assignment/Experiment 等考核载体",
        "negative": "反例：不要写成“作业/实验 -> 知识点”；普通出现位置优先用 appears_in。",
    },
    "related_to": {
        "source": "source 与 target 都必须是已抽取且端点完整的实体",
        "target": "target 不是 missing、unknown 或临时占位",
        "negative": "反例：不能承接 missing 端点，也不能替代 defined_by / applied_in / appears_in 等更具体关系。",
    },
}
MICRO_RELATION_DIRECTION_HINTS = {
    "defined_by": "被定义对象 -> 定义/公式",
    "applied_in": "被应用对象 -> 应用场景",
    "evaluated_by": "被考核对象 -> 作业/实验",
    "appears_in": "出现实体 -> 位置容器",
    "related_to": "完整实体之间的弱关联",
}
NOISE_ENTITY_NAMES = {"无", "本章", "本节", "如下", "图", "表", "见图", "见下图"}
ENTITY_TYPE_DESCRIPTION_OVERRIDES = {
    "Course": "课程顶层对象",
    "Chapter": "课程结构章节",
    "Section": "课程结构单元",
    "KnowledgePoint": "课程知识点",
    "Concept": "课程概念",
    "Term": "课程术语",
    "FormulaOrDefinition": "课程中的定义或公式",
    "AlgorithmOrMethod": "课程中的方法或算法",
    "Experiment": "课程实验任务",
    "Assignment": "课程作业或题组",
    "ToolOrPlatform": "课程使用的工具或平台",
}


@dataclass(frozen=True)
class SchemaItem:
    name: str
    label_zh: str
    description: str
    canonical_name_rule: str = ""
    extraction_hint: str = ""
    positive_signals: List[str] = field(default_factory=list)
    negative_signals: List[str] = field(default_factory=list)
    examples: List[str] = field(default_factory=list)
    source_types: List[str] = field(default_factory=list)
    target_types: List[str] = field(default_factory=list)
    negative_examples: List[str] = field(default_factory=list)


@dataclass(frozen=True)
class SchemaCatalog:
    schema_version: str
    entity_types: List[SchemaItem]
    relation_types: List[SchemaItem]
    entity_schema_path: str
    relation_schema_path: str
    extraction_rules_path: str
    extraction_rules_text: str


@dataclass(frozen=True)
class PromptSource:
    path: Optional[Path]
    text: str
    source_type: str
    notes: List[str]


@dataclass(frozen=True)
class FewShotExample:
    example_id: str
    source_kind: str
    guessed_sample_type: str
    source_sample_id: str
    input_text: str
    output_text: str
    note: str


def _now_iso() -> str:
    return datetime.now(timezone.utc).astimezone().isoformat(timespec="seconds")


def _clean_string(value: Any) -> str:
    return value.strip() if isinstance(value, str) else ""


def _shorten_text(text: str, max_length: int) -> str:
    cleaned = re.sub(r"\s+", " ", text).strip()
    if len(cleaned) <= max_length:
        return cleaned
    return f"{cleaned[: max_length - 1].rstrip()}…"


def _read_json(path: Path) -> Dict[str, Any]:
    return json.loads(path.read_text(encoding="utf-8"))


def _load_existing_manifest(path: Path) -> Dict[str, Any]:
    if not path.exists():
        return {}
    try:
        payload = _read_json(path)
    except json.JSONDecodeError:
        return {}
    return payload if isinstance(payload, dict) else {}


def _load_json_records(path: Path, list_key_candidates: Sequence[str]) -> List[Dict[str, Any]]:
    payload = _read_json(path)
    if isinstance(payload, list):
        return [item for item in payload if isinstance(item, dict)]
    for key in list_key_candidates:
        value = payload.get(key)
        if isinstance(value, list):
            return [item for item in value if isinstance(item, dict)]
    raise ValueError(f"无法从 {path} 识别记录列表字段")


def _resolve_optional_path(path: Path | None, candidates: Sequence[Path]) -> Optional[Path]:
    if path is not None:
        return path
    for candidate in candidates:
        if candidate.exists():
            return candidate
    return None


def _safe_relpath(path: Optional[Path]) -> Optional[str]:
    if path is None:
        return None
    try:
        return str(path.resolve().relative_to(PROJECT_ROOT.resolve()))
    except ValueError:
        return str(path.resolve())


def _ensure_parent(path: Path) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)


def _write_text(path: Path, text: str, overwrite: bool) -> bool:
    if path.exists() and not overwrite:
        return False
    _ensure_parent(path)
    path.write_text(text, encoding="utf-8")
    return True


def _write_json(path: Path, payload: Dict[str, Any], overwrite: bool) -> bool:
    text = json.dumps(payload, ensure_ascii=False, indent=2) + "\n"
    return _write_text(path, text, overwrite=overwrite)


def _dedupe_preserve_order(values: Sequence[str]) -> List[str]:
    ordered: List[str] = []
    seen: set[str] = set()
    for value in values:
        cleaned = _clean_string(value)
        if not cleaned or cleaned in seen:
            continue
        ordered.append(cleaned)
        seen.add(cleaned)
    return ordered


def _render_delimiter_placeholders(text: str) -> str:
    rendered = text
    for placeholder, delimiter in DELIMITER_PLACEHOLDER_REPLACEMENTS.items():
        rendered = rendered.replace(placeholder, delimiter)
    return rendered


def _strip_legacy_examples(text: str) -> str:
    """移除默认 GraphRAG Prompt 中与当前课程 schema 无关的旧示例块。"""

    if not text.strip():
        return text

    real_data_match = re.search(r"\n\s*-Real Data-", text, flags=re.IGNORECASE)
    example_starts = [
        index
        for index in (
            text.find("\n-Examples-"),
            text.find("\nExamples:"),
            text.find("\nExample 1:"),
            text.find("\nExample One:"),
        )
        if index >= 0
    ]
    if not example_starts:
        return text

    start = min(example_starts)
    if real_data_match and start < real_data_match.start():
        return f"{text[:start].rstrip()}\n\n{text[real_data_match.start():].lstrip()}"
    return text


def _replace_entity_types(text: str, entity_names: str) -> str:
    text = re.sub(r"entity_types:\s*\[[^\]]*\]", f"entity_types: [{entity_names}]", text)
    return re.sub(
        r"entity_type:\s*One of the following types:\s*\[[^\]]*\]",
        f"entity_type: One of the following types: [{entity_names}]",
        text,
    )


def _insert_before_real_data(text: str, block: str) -> str:
    marker = "\n-Real Data-"
    index = text.rfind(marker)
    if index >= 0:
        return f"{text[:index].rstrip()}\n\n{block.rstrip()}\n{text[index:]}"
    return f"{text.rstrip()}\n\n{block.rstrip()}\n"


def _build_course_baseline_block(schema_catalog: SchemaCatalog) -> str:
    return f"""-Course Baseline Constraints-
当前任务聚焦“课程知识图谱抽取”，不是通用信息抽取。
请优先抽取课程结构、概念/术语、方法/算法、实验、作业等稳定对象。
当前课程实体类型基线：[{_entity_names(schema_catalog)}]
当前课程关系类型基线：[{_relation_names(schema_catalog)}]
避免抽取课程通知、图表残片、页码、无关行政信息和重复实体。
"""


def _base_prompt_note(base_label: str) -> str:
    return f"""-Base Prompt Note-
- 当前候选版本以 {base_label} 为底稿自动增强，尽量保留原始 Prompt 的结构、示例和 tuple 输出风格。
"""


def _score_prompt_path(path: Path) -> tuple[int, int, str]:
    lowered = path.name.lower()
    keyword_hits = sum(1 for keyword in AUTO_TUNED_FILE_KEYWORDS if keyword in lowered)
    extract_graph_bonus = 1 if "extract_graph" in lowered else 0
    return (-extract_graph_bonus, -keyword_hits, str(path))


def _find_prompt_file(prompt_dir: Path, *, recursive: bool) -> Optional[Path]:
    if prompt_dir.is_file():
        return prompt_dir

    for name in DEFAULT_PROMPT_FILE_NAMES:
        candidate = prompt_dir / name
        if candidate.is_file():
            return candidate

    if not recursive:
        return None

    matched: List[Path] = []
    for path in prompt_dir.rglob("*.txt"):
        if not path.is_file():
            continue
        lowered = path.name.lower()
        if path.parts and "candidates" in path.parts:
            continue
        if any(keyword in lowered for keyword in AUTO_TUNED_FILE_KEYWORDS):
            matched.append(path)

    if not matched:
        return None

    return sorted(matched, key=_score_prompt_path)[0]


def load_schema_catalog(schema_dir: Path) -> SchemaCatalog:
    entity_path = schema_dir / "entity_types.json"
    relation_path = schema_dir / "relation_types.json"
    rules_path = schema_dir / "extraction_rules.md"

    entity_payload = _read_json(entity_path)
    relation_payload = _read_json(relation_path)
    rules_text = rules_path.read_text(encoding="utf-8")

    entity_order = entity_payload.get("entity_type_order", [])
    relation_order = relation_payload.get("relation_type_order", [])
    entity_catalog = entity_payload.get("entity_types", {})
    relation_catalog = relation_payload.get("relation_types", {})

    entity_items = [
        SchemaItem(
            name=name,
            label_zh=_clean_string(item.get("label_zh")),
            description=_clean_string(item.get("description")),
            canonical_name_rule=_clean_string(item.get("canonical_name_rule")),
            positive_signals=[_clean_string(signal) for signal in item.get("positive_signals", []) if _clean_string(signal)],
            negative_signals=[_clean_string(signal) for signal in item.get("negative_signals", []) if _clean_string(signal)],
        )
        for name in entity_order
        for item in [entity_catalog.get(name, {})]
        if isinstance(item, dict)
    ]
    relation_items = [
        SchemaItem(
            name=name,
            label_zh=_clean_string(item.get("label_zh")),
            description=_clean_string(item.get("description")),
            extraction_hint=_clean_string(item.get("extraction_hint")),
            examples=[
                _clean_string(value)
                for value in item.get("examples", [])
                if _clean_string(value)
            ],
            source_types=[_clean_string(value) for value in item.get("source_types", []) if _clean_string(value)],
            target_types=[_clean_string(value) for value in item.get("target_types", []) if _clean_string(value)],
            negative_examples=[
                _clean_string(value)
                for value in item.get("negative_examples", [])
                if _clean_string(value)
            ],
        )
        for name in relation_order
        for item in [relation_catalog.get(name, {})]
        if isinstance(item, dict)
    ]

    if not entity_items or not relation_items:
        raise ValueError("schema 配置不完整，无法生成候选 Prompt")

    return SchemaCatalog(
        schema_version=_clean_string(entity_payload.get("schema_version") or relation_payload.get("schema_version")) or "unknown",
        entity_types=entity_items,
        relation_types=relation_items,
        entity_schema_path=_safe_relpath(entity_path),
        relation_schema_path=_safe_relpath(relation_path),
        extraction_rules_path=_safe_relpath(rules_path),
        extraction_rules_text=rules_text,
    )


def load_default_prompt_source(default_prompt_dir: Path) -> PromptSource:
    prompt_path = _find_prompt_file(default_prompt_dir, recursive=False)
    if prompt_path is None:
        return PromptSource(
            path=None,
            text="",
            source_type="generated_minimal_default",
            notes=["未找到可复用的默认 Prompt，将生成最小课程基线版本。"],
        )
    return PromptSource(
        path=prompt_path,
        text=prompt_path.read_text(encoding="utf-8"),
        source_type="default_file",
        notes=[f"默认 Prompt 来源：{_safe_relpath(prompt_path)}"],
    )


def _is_official_auto_tuned_entry(entry: Optional[Dict[str, Any]]) -> bool:
    if not isinstance(entry, dict):
        return False
    source_type = _clean_string(entry.get("source_type"))
    generation_method = _clean_string(entry.get("generation_method"))
    return (
        source_type in OFFICIAL_AUTO_TUNED_SOURCE_TYPES
        or generation_method in OFFICIAL_AUTO_TUNED_GENERATION_METHODS
    )


def load_auto_tuned_prompt_source(
    auto_tuned_prompt_dir: Path,
    *,
    manifest_entry: Optional[Dict[str, Any]] = None,
) -> PromptSource:
    if isinstance(manifest_entry, dict) and not _is_official_auto_tuned_entry(manifest_entry):
        placeholder_note = (
            "官方 auto_tuned 占位候选尚未被真实 GraphRAG prompt-tune 结果覆盖，将继续回退到 default。"
            if auto_tuned_prompt_dir.exists()
            else "GraphRAG 官方 auto-tuned 输出不存在，将保留占位目录并回退到 default 候选 Prompt。"
        )
        return PromptSource(
            path=None,
            text="",
            source_type="missing_auto_tuned",
            notes=[placeholder_note],
        )

    prompt_path = _find_prompt_file(auto_tuned_prompt_dir, recursive=True)
    if prompt_path is None:
        return PromptSource(
            path=None,
            text="",
            source_type="missing_auto_tuned",
            notes=["GraphRAG 官方 auto-tuned 输出不存在，将保留占位目录并回退到 default 候选 Prompt。"],
        )
    return PromptSource(
        path=prompt_path,
        text=prompt_path.read_text(encoding="utf-8"),
        source_type="graphrag_prompt_tune",
        notes=[
            "auto_tuned 候选由 GraphRAG 官方 prompt-tune 生成。",
            f"主要 Prompt 文件：{prompt_path.name}",
            f"auto-tuned Prompt 来源：{_safe_relpath(prompt_path)}",
        ],
    )


def _summarize_entity_items(entity_items: Sequence[SchemaItem]) -> str:
    lines = []
    for item in entity_items:
        summary = _shorten_text(item.description or ENTITY_TYPE_DESCRIPTION_OVERRIDES.get(item.name, item.label_zh), 44)
        name_rule = _shorten_text(item.canonical_name_rule, 34) if item.canonical_name_rule else "沿用课程内稳定命名"
        lines.append(f"- `{item.name}`（{item.label_zh}）：{summary}；命名规则：{name_rule}")
    return "\n".join(lines)


def _compact_examples(values: Sequence[str]) -> str:
    return "、".join(
        _shorten_text(value, MAX_RELATION_EXAMPLE_CHARS)
        for value in values[:MAX_RELATION_EXAMPLE_COUNT]
    )


def _relation_negative_examples(item: SchemaItem) -> List[str]:
    examples = list(item.negative_examples)
    if item.name == "related_to" and RELATED_TO_GENERIC_NEGATIVE_EXAMPLE not in examples:
        examples.append(RELATED_TO_GENERIC_NEGATIVE_EXAMPLE)
    return examples


def _summarize_relation_items(relation_items: Sequence[SchemaItem]) -> str:
    lines = []
    for item in relation_items:
        summary = _shorten_text(item.description, 42)
        hint = _shorten_text(item.extraction_hint, 38) if item.extraction_hint else "按最具体语义关系判断"
        positive_examples = f"；正例：{_compact_examples(item.examples)}" if item.examples else ""
        negative_values = _relation_negative_examples(item)
        negative_examples = f"；禁例：{_compact_examples(negative_values)}" if negative_values else ""
        lines.append(f"- `{item.name}`（{item.label_zh}）：{summary}；抽取提示：{hint}{positive_examples}{negative_examples}")
    return "\n".join(lines)


def _extract_endpoint_integrity_summary(rules_text: str) -> str:
    if not (
        "关系端点完整性" in rules_text
        or ("source" in rules_text and "target" in rules_text and "entities" in rules_text)
    ):
        return ""
    return "关系端点完整性：所有关系的 source 和 target 必须能在 entities 中找到；如果无法补齐端点实体，应跳过该关系。"


def _summarize_rules(rules_text: str) -> str:
    priority_match = re.search(
        r"优先级如下：\s*(.*?)\n\n",
        rules_text,
        re.DOTALL,
    )
    priority_summary = ""
    if priority_match:
        priority_lines = [re.sub(r"^\d+\.\s*", "", line.strip()) for line in priority_match.group(1).splitlines() if line.strip()]
        if priority_lines:
            priority_summary = " > ".join(priority_lines[:6])

    bullets: List[str] = [
        "只抽取对课程问答、知识建图和教学评测有稳定价值的实体与关系。",
        "仅在文本显式陈述或同一结构单元内可稳定推断时建立关系，不因简单共现连边。",
        "课程通知、无意义短语、图表残片、页眉页脚和无关行政信息默认不抽。",
        "结构实体要保留有效章节编号；实体默认按课程内边界去重，不做跨课程自动合并。",
    ]
    if priority_summary:
        bullets.insert(1, f"关系优先级参考：{priority_summary}")
    endpoint_summary = _extract_endpoint_integrity_summary(rules_text)
    if endpoint_summary:
        bullets.append(endpoint_summary)
    if "related_to" in rules_text:
        bullets.append(f"related_to 是保底关系；{RELATED_TO_GENERIC_NEGATIVE_EXAMPLE}。")

    return "\n".join(f"- {bullet}" for bullet in bullets)


def _entity_names(schema_catalog: SchemaCatalog) -> str:
    return ", ".join(item.name for item in schema_catalog.entity_types)


def _relation_names(schema_catalog: SchemaCatalog) -> str:
    return ", ".join(item.name for item in schema_catalog.relation_types)


def _build_prompt_header(schema_catalog: SchemaCatalog) -> str:
    return f"""-Goal-
Given a course text document that is potentially relevant to this activity and the course schema,
extract stable entities and relationships for course knowledge graph construction.

-Task Context-
当前任务是“课程知识图谱抽取”，目标是从教材、课件、课程大纲、实验指导、课堂笔记、作业或试题文本中，
抽取稳定、可复用、可解析的课程领域实体与关系，用于后续 GraphRAG 建图与问答验证。

-Steps-
1. 识别所有稳定实体。对每个实体输出：
- entity_name: 实体名称，优先使用课程内稳定名称并保留章节编号
- entity_type: 必须是以下类型之一：[{_entity_names(schema_catalog)}]
- entity_description: 中文简要说明，聚焦课程语义，不要输出无关废话
Format each entity as ("entity"{TUPLE_DELIMITER}<entity_name>{TUPLE_DELIMITER}<entity_type>{TUPLE_DELIMITER}<entity_description>)

2. 在已识别实体之间识别明确且稳定的关系。对每条关系输出：
- source_entity: 源实体名称
- target_entity: 目标实体名称
- relationship_description: 必须以 [type=<relation_type>] 开头，其中 <relation_type> 必须来自 [{_relation_names(schema_catalog)}]；
  其后用中文解释关系证据与语义，避免空泛描述
- relationship_strength: 1 到 10 的整数分值，表示关系强度
Format each relationship as ("relationship"{TUPLE_DELIMITER}<source_entity>{TUPLE_DELIMITER}<target_entity>{TUPLE_DELIMITER}<relationship_description>{TUPLE_DELIMITER}<relationship_strength>)

3. 返回单一列表，实体和关系统一使用 {RECORD_DELIMITER} 分隔，并在结束时输出 {COMPLETION_DELIMITER}。

4. 输出必须是中文说明；不要添加题外解释、推理过程、免责声明或额外 Markdown。

5. 只抽稳定、可复用、可建图的课程知识；不要输出重复实体、非法类型、噪声关系或与课程问答无关的信息。

6. 若证据不足以支撑实体或关系，请宁缺毋滥，直接跳过。

"""


def build_minimal_default_prompt(schema_catalog: SchemaCatalog, language: str) -> str:
    _ = language  # 当前仅输出中文版本，保留参数以兼容未来扩展
    return (
        _build_prompt_header(schema_catalog)
        + f"""-Quality Constraints-
- 当前任务聚焦课程知识图谱抽取，不是通用信息抽取。
- 实体类型必须来自课程 Schema，不要自造类型。
- 关系说明必须以 [type=<relation_type>] 开头，关系类型只能来自课程 Schema。
- 优先保留课程结构、概念/术语、方法/算法、实验、作业相关对象。
- 对课程通知、图表碎片、页码、无关行政信息、空泛话语默认忽略。

-Real Data-
entity_types: [{_entity_names(schema_catalog)}]
relation_types: [{_relation_names(schema_catalog)}]
text: {{input_text}}
output:
"""
    )


def build_default_candidate_prompt(
    schema_catalog: SchemaCatalog,
    language: str,
    base_prompt_text: str,
) -> str:
    if not base_prompt_text.strip():
        return build_minimal_default_prompt(schema_catalog, language)

    prompt = _replace_entity_types(base_prompt_text.strip(), _entity_names(schema_catalog))
    prompt = _insert_before_real_data(prompt, _build_course_baseline_block(schema_catalog))
    return prompt.rstrip() + "\n"


def build_schema_aware_prompt(
    schema_catalog: SchemaCatalog,
    language: str,
    base_prompt_text: str,
    base_label: str,
) -> str:
    _ = language
    prompt = build_default_candidate_prompt(schema_catalog, language, base_prompt_text)
    prompt = _insert_before_real_data(
        prompt,
        f"""
-Schema Constraints-
实体类型必须来自以下课程 Schema：
{_summarize_entity_items(schema_catalog.entity_types)}

关系类型必须来自以下课程 Schema：
{_summarize_relation_items(schema_catalog.relation_types)}

关键抽取规则摘要：
{_summarize_rules(schema_catalog.extraction_rules_text)}

必要输出约束：
- 关系说明必须以 [type=<relation_type>] 开头，并且 <relation_type> 必须来自课程 Schema。
- 同一课程内相同实体应合并，不要重复输出同义或缩写碎片。
- 只有在无法判断更具体关系时才使用 `related_to`。
- 不能用 `related_to` 代替缺失端点或更具体关系；如果端点无法补齐，应跳过关系。
- 如果文本仅提供位置出现信息，优先考虑是否存在更强的结构/应用/考核关系。
- 若输入文本中带有 course_id、document_type、chapter、section、heading_path、page_start、page_end 等字段，应将其作为判断上下文，而不是原样重复输出。
{_base_prompt_note(base_label)}
""",
    )
    return prompt.rstrip() + "\n"


def _relation_item_by_name(schema_catalog: SchemaCatalog) -> Dict[str, SchemaItem]:
    return {item.name: item for item in schema_catalog.relation_types}


def _build_relation_direction_cards(schema_catalog: SchemaCatalog) -> str:
    relation_by_name = _relation_item_by_name(schema_catalog)
    lines = ["-关系方向卡片-", "只补充高风险关系的方向判断，不嵌入完整 audit 样本文本。"]

    for relation_name in DIRECTIONAL_RELATION_NAMES:
        item = relation_by_name.get(relation_name)
        if item is None:
            continue
        hint = DIRECTIONAL_RELATION_HINTS[relation_name]
        lines.append(
            f"- `{relation_name}`：{hint['source']}，{hint['target']}；{hint['negative']}"
        )

    if len(lines) == 2:
        return ""
    return "\n".join(lines)


def build_schema_aware_directional_prompt(schema_catalog: SchemaCatalog, schema_aware_prompt: str) -> str:
    direction_block = _build_relation_direction_cards(schema_catalog)
    if not direction_block:
        return schema_aware_prompt.rstrip() + "\n"
    return _insert_before_real_data(schema_aware_prompt, direction_block).rstrip() + "\n"


def _strict_json_output_guard_block() -> str:
    return """-Strict JSON Output Guard-
- 最终只返回一个 JSON 对象，根对象只包含 `entities` 与 `relationships` 两个数组。
- 不输出额外说明、指标、指令、延迟字段、Markdown code fence 或前后缀文本。
- 如果没有足够证据，返回空数组；不要编造实体、关系或临时占位端点。
- 每条 relationship 的 source 和 target 必须逐字匹配 entities[].title。
"""


def _material_7_v3_failure_family_guard_block() -> str:
    return """-Material 7 v3 Failure-family Guard-
- `appears_in`：不要输出 Section/Assignment -> Concept 的 appears_in；位置关系只能是知识实体 -> Course/Chapter/Section/Experiment/Assignment/ToolOrPlatform。
- `belongs_to`：不要输出 Concept -> Concept 的 belongs_to；概念分类若原文明确父子/组成，优先用 parent Concept contains child Concept，否则跳过。
- `defined_by`：target 为 Term 时，只有符号、变量、公式名或明确判定条件才保留；PCB/TCB/FCFS/SPOOLing 等简称优先放 alias，不作为 defined_by 关系。
- `endpoint`：source 和 target 必须逐字匹配 entities；无法补齐实体时跳过关系，不输出 missing/unknown/N/A/空字符串。
"""


def build_default_guarded_prompt(default_prompt: str) -> str:
    return _insert_before_real_data(default_prompt, _strict_json_output_guard_block()).rstrip() + "\n"


def build_schema_aware_directional_v2_prompt(schema_aware_directional_prompt: str) -> str:
    return _insert_before_real_data(
        schema_aware_directional_prompt,
        _material_7_v3_failure_family_guard_block(),
    ).rstrip() + "\n"


def build_schema_fewshot_distilled_v3_prompt(micro_example_lines: Sequence[str]) -> str:
    compact_examples = list(micro_example_lines[:1])
    prompt = (
        "-Material 7 v3 Failure-family Guard-\n"
        "- 只返回一个 JSON 对象；source 和 target 必须逐字匹配 entities。\n"
        "- 不要输出 Section/Assignment -> Concept 的 appears_in；Concept -> Concept 的 belongs_to 跳过或改显式 contains。\n"
        "- defined_by -> Term 只有符号、变量、公式名或明确判定条件才保留；简称放 alias。\n"
        "-Micro-examples-\n"
    )
    if compact_examples:
        prompt += "\n".join(compact_examples) + "\n"
    else:
        prompt += "- 按失败族规则保留端点和类型，不嵌完整 audit text。\n"
    return prompt


def _compact_assignment_text(text: str, *, max_items: int = 6) -> str:
    lines: List[str] = []
    for raw_line in text.splitlines():
        stripped = raw_line.strip()
        if not stripped:
            continue
        if stripped in {"习题", "练习题", "思考题", "复习题"}:
            lines.append(stripped)
            continue
        if re.match(r"^[-*•]?\s*\d+[\.\)）、]", stripped):
            lines.append(stripped)
        if len(lines) >= max_items + 1:
            break
    return "\n".join(lines)


def _compress_fewshot_input_text(record: Dict[str, Any], text: str, max_chars: int) -> str:
    if max_chars <= 0:
        return text.strip()

    sample_type = _clean_string(record.get("guessed_sample_type"))
    compact = ""
    if sample_type == "assignment_requirement":
        compact = _compact_assignment_text(text)
    if not compact:
        compact = re.sub(r"\n{3,}", "\n\n", text.strip())

    if len(compact) <= max_chars:
        return compact
    return f"{compact[: max_chars - 1].rstrip()}…"


def _format_input_block(
    record: Dict[str, Any],
    text_key: str = "text",
    *,
    max_text_chars: int | None = None,
) -> str:
    lines: List[str] = []
    for key in ("course_id", "document_type", "source_file", "chapter", "section", "page_start", "page_end"):
        value = record.get(key)
        if value is None or value == "":
            continue
        lines.append(f"{key}: {value}.")
    lines.append("text:")
    text = _clean_string(record.get(text_key))
    if max_text_chars is not None:
        text = _compress_fewshot_input_text(record, text, max_text_chars)
    lines.append(text)
    return "\n".join(lines).strip()


def _build_entity_description(entity: Dict[str, Any], record: Dict[str, Any]) -> str:
    entity_type = _clean_string(entity.get("type"))
    name = _clean_string(entity.get("name")) or _clean_string(entity.get("normalized_name")) or "未命名实体"
    section = _clean_string(record.get("section"))
    chapter = _clean_string(record.get("chapter"))
    anchor = section or chapter or "当前课程片段"
    base = ENTITY_TYPE_DESCRIPTION_OVERRIDES.get(entity_type, f"课程中的 {entity_type}")
    return f"{base}“{name}”，出现在 {anchor} 中。"


def _is_noise_entity_name(name: str) -> bool:
    stripped = name.strip()
    if not stripped or stripped in NOISE_ENTITY_NAMES:
        return True
    normalized = re.sub(r"[\s\W_]+", "", stripped, flags=re.UNICODE)
    return not normalized or normalized.isdigit()


def _entity_sort_key(entity: Dict[str, Any], index: int) -> tuple[int, int, int]:
    entity_type = _clean_string(entity.get("type"))
    name = _clean_string(entity.get("name")) or _clean_string(entity.get("normalized_name"))
    noise_penalty = 1 if _is_noise_entity_name(name) else 0
    type_rank = FEWSHOT_ENTITY_TYPE_PRIORITY.get(entity_type, 50)
    return (noise_penalty, type_rank, index)


def _relation_sort_key(relation: Dict[str, Any], index: int) -> tuple[int, int, int]:
    relation_type = _clean_string(relation.get("type")) or "related_to"
    related_penalty = 1 if relation_type == "related_to" else 0
    type_rank = FEWSHOT_RELATION_TYPE_PRIORITY.get(relation_type, 50)
    return (related_penalty, type_rank, index)


def _select_fewshot_gold_items(
    entities: Sequence[Dict[str, Any]],
    relations: Sequence[Dict[str, Any]],
    *,
    max_entities: int,
    max_relations: int,
) -> tuple[List[Dict[str, Any]], List[Dict[str, Any]]]:
    indexed_entities = [
        (index, item)
        for index, item in enumerate(entities)
        if _clean_string(item.get("entity_id"))
    ]
    entity_by_id = {_clean_string(item.get("entity_id")): item for _, item in indexed_entities}
    selected_entity_ids: set[str] = set()
    selected_relations: List[Dict[str, Any]] = []

    for _, relation in sorted(
        enumerate(relations),
        key=lambda pair: _relation_sort_key(pair[1], pair[0]),
    ):
        if len(selected_relations) >= max_relations:
            break
        source_id = _clean_string(relation.get("source_entity_id"))
        target_id = _clean_string(relation.get("target_entity_id"))
        if source_id not in entity_by_id or target_id not in entity_by_id:
            continue
        needed = {source_id, target_id} - selected_entity_ids
        if len(selected_entity_ids) + len(needed) > max_entities:
            continue
        selected_relations.append(relation)
        selected_entity_ids.update(needed)

    for index, entity in sorted(indexed_entities, key=lambda pair: _entity_sort_key(pair[1], pair[0])):
        if len(selected_entity_ids) >= max_entities:
            break
        entity_id = _clean_string(entity.get("entity_id"))
        if entity_id in selected_entity_ids:
            continue
        selected_entity_ids.add(entity_id)

    selected_entities = [item for _, item in indexed_entities if _clean_string(item.get("entity_id")) in selected_entity_ids]
    return selected_entities, selected_relations


def _format_fewshot_output(
    record: Dict[str, Any],
    *,
    max_entities: int = DEFAULT_FEWSHOT_MAX_ENTITIES,
    max_relations: int = DEFAULT_FEWSHOT_MAX_RELATIONS,
) -> str:
    entities = [item for item in record.get("gold_entities", []) if isinstance(item, dict)]
    relations = [item for item in record.get("gold_relations", []) if isinstance(item, dict)]
    entities, relations = _select_fewshot_gold_items(
        entities,
        relations,
        max_entities=max(0, max_entities),
        max_relations=max(0, max_relations),
    )
    entity_name_map = {
        _clean_string(item.get("entity_id")): _clean_string(item.get("name")) or _clean_string(item.get("normalized_name"))
        for item in entities
    }

    rendered: List[str] = []
    for entity in entities:
        name = _clean_string(entity.get("name")) or _clean_string(entity.get("normalized_name"))
        entity_type = _clean_string(entity.get("type"))
        description = _build_entity_description(entity, record)
        rendered.append(
            f"(\"entity\"{TUPLE_DELIMITER}{name}{TUPLE_DELIMITER}{entity_type}{TUPLE_DELIMITER}{description})"
        )

    for relation in relations:
        source_name = entity_name_map.get(_clean_string(relation.get("source_entity_id")), "未知源实体")
        target_name = entity_name_map.get(_clean_string(relation.get("target_entity_id")), "未知目标实体")
        relation_type = _clean_string(relation.get("type")) or "related_to"
        evidence = _shorten_text(_clean_string(relation.get("evidence_text")), 110)
        description = f"[type={relation_type}] {evidence or '同一结构单元内存在稳定语义关联'}"
        rendered.append(
            f"(\"relationship\"{TUPLE_DELIMITER}{source_name}{TUPLE_DELIMITER}{target_name}{TUPLE_DELIMITER}{description}{TUPLE_DELIMITER}8)"
        )

    rendered.append(COMPLETION_DELIMITER)
    return f"\n{RECORD_DELIMITER}\n".join(rendered)


def _audit_record_sort_key(record: Dict[str, Any]) -> tuple[int, int, int, str]:
    priority = _clean_string(record.get("audit_priority"))
    priority_rank = {"high": 0, "medium": 1, "low": 2}.get(priority, 3)
    sample_type = _clean_string(record.get("guessed_sample_type"))
    type_rank = FEWSHOT_TYPE_PRIORITY.get(sample_type, 99)
    text_length = int(record.get("text_length") or len(_clean_string(record.get("text"))))
    return (priority_rank, type_rank, abs(text_length - 260), _clean_string(record.get("id")))


def _schema_ordered_names(items: Sequence[SchemaItem]) -> List[str]:
    return [item.name for item in items]


def _ordered_schema_subset(schema_names: Sequence[str], covered_names: set[str]) -> List[str]:
    return [name for name in schema_names if name in covered_names]


def _record_rendered_gold_types(
    record: Dict[str, Any],
    *,
    schema_entity_names: set[str],
    schema_relation_names: set[str],
    max_entities: int,
    max_relations: int,
) -> tuple[set[str], set[str]]:
    entities = [item for item in record.get("gold_entities", []) if isinstance(item, dict)]
    relations = [item for item in record.get("gold_relations", []) if isinstance(item, dict)]
    selected_entities, selected_relations = _select_fewshot_gold_items(
        entities,
        relations,
        max_entities=max(0, max_entities),
        max_relations=max(0, max_relations),
    )
    entity_types = {
        entity_type
        for entity in selected_entities
        for entity_type in [_clean_string(entity.get("type"))]
        if entity_type in schema_entity_names
    }
    relation_types = {
        relation_type
        for relation in selected_relations
        for relation_type in [_clean_string(relation.get("type")) or "related_to"]
        if relation_type in schema_relation_names
    }
    return entity_types, relation_types


def _build_fewshot_coverage_report(
    *,
    schema_catalog: SchemaCatalog,
    selected_records: Sequence[Dict[str, Any]],
    selected_example_ids: Sequence[str],
    selection_strategy: str,
    max_entities: int,
    max_relations: int,
) -> Dict[str, Any]:
    entity_schema_names = _schema_ordered_names(schema_catalog.entity_types)
    relation_schema_names = _schema_ordered_names(schema_catalog.relation_types)
    entity_name_set = set(entity_schema_names)
    relation_name_set = set(relation_schema_names)

    covered_entity_types: set[str] = set()
    covered_relation_types: set[str] = set()
    for record in selected_records:
        entity_types, relation_types = _record_rendered_gold_types(
            record,
            schema_entity_names=entity_name_set,
            schema_relation_names=relation_name_set,
            max_entities=max_entities,
            max_relations=max_relations,
        )
        covered_entity_types.update(entity_types)
        covered_relation_types.update(relation_types)

    covered_relations = _ordered_schema_subset(relation_schema_names, covered_relation_types)
    covered_entities = _ordered_schema_subset(entity_schema_names, covered_entity_types)
    return {
        "selected_example_ids": list(selected_example_ids),
        "covered_relation_types": covered_relations,
        "missing_relation_types": [name for name in relation_schema_names if name not in covered_relation_types],
        "covered_entity_types": covered_entities,
        "missing_entity_types": [name for name in entity_schema_names if name not in covered_entity_types],
        "selection_strategy": selection_strategy,
    }


def _format_fewshot_coverage_summary(coverage: Dict[str, Any]) -> str:
    covered_relations = coverage.get("covered_relation_types", [])
    missing_relations = coverage.get("missing_relation_types", [])
    covered_entities = coverage.get("covered_entity_types", [])
    missing_entities = coverage.get("missing_entity_types", [])
    relation_total = len(covered_relations) + len(missing_relations)
    entity_total = len(covered_entities) + len(missing_entities)
    missing_relation_text = ", ".join(missing_relations[:5]) if missing_relations else "无"
    if len(missing_relations) > 5:
        missing_relation_text += " ..."
    return (
        f"few-shot 覆盖摘要：关系 {len(covered_relations)}/{relation_total}，"
        f"实体 {len(covered_entities)}/{entity_total}；缺失关系：{missing_relation_text}。"
    )


def _build_fewshot_example_from_audit(
    record: Dict[str, Any],
    *,
    input_max_chars: int,
    max_entities: int,
    max_relations: int,
) -> FewShotExample:
    return FewShotExample(
        example_id=_clean_string(record.get("id")) or _clean_string(record.get("source_sample_id")) or "audit-example",
        source_kind="audit_gold",
        guessed_sample_type=_clean_string(record.get("guessed_sample_type")) or "unknown",
        source_sample_id=_clean_string(record.get("source_sample_id")) or "",
        input_text=_format_input_block(record, max_text_chars=input_max_chars),
        output_text=_format_fewshot_output(
            record,
            max_entities=max_entities,
            max_relations=max_relations,
        ),
        note="基于 audit gold_entities / gold_relations 直接转换得到。",
    )


def _manual_fewshot_examples(schema_catalog: SchemaCatalog, limit: int) -> List[FewShotExample]:
    relation_names = {item.name for item in schema_catalog.relation_types}
    contains_name = "contains" if "contains" in relation_names else next(iter(relation_names))
    defined_by_name = "defined_by" if "defined_by" in relation_names else contains_name
    implemented_by_name = "implemented_by" if "implemented_by" in relation_names else contains_name
    evaluated_by_name = "evaluated_by" if "evaluated_by" in relation_names else contains_name

    examples = [
        FewShotExample(
            example_id="manual-definition",
            source_kind="manual_minimal",
            guessed_sample_type="definition_or_formula",
            source_sample_id="manual-definition",
            input_text=(
                "course_id: os.\n"
                "document_type: textbook.\n"
                "chapter: 第二章 进程管理.\n"
                "section: 2.1 进程的定义.\n"
                "text:\n"
                "进程是程序的一次执行过程，是系统进行资源分配和调度的基本单位。"
            ),
            output_text=(
                f"(\"entity\"{TUPLE_DELIMITER}第二章 进程管理{TUPLE_DELIMITER}Chapter{TUPLE_DELIMITER}课程结构章节“第二章 进程管理”，出现在 2.1 进程的定义 中。)\n"
                f"{RECORD_DELIMITER}\n"
                f"(\"entity\"{TUPLE_DELIMITER}进程{TUPLE_DELIMITER}Concept{TUPLE_DELIMITER}课程概念“进程”，出现在 2.1 进程的定义 中。)\n"
                f"{RECORD_DELIMITER}\n"
                f"(\"relationship\"{TUPLE_DELIMITER}第二章 进程管理{TUPLE_DELIMITER}进程{TUPLE_DELIMITER}[type={contains_name}] 本章介绍进程概念{TUPLE_DELIMITER}8)\n"
                f"{RECORD_DELIMITER}\n"
                f"(\"relationship\"{TUPLE_DELIMITER}进程{TUPLE_DELIMITER}进程定义{TUPLE_DELIMITER}[type={defined_by_name}] 文本给出了进程的正式定义{TUPLE_DELIMITER}9)\n"
                f"{COMPLETION_DELIMITER}"
            ),
            note="手写最小 few-shot 示例：概念/定义型样本。",
        ),
        FewShotExample(
            example_id="manual-method",
            source_kind="manual_minimal",
            guessed_sample_type="experiment_instruction",
            source_sample_id="manual-method",
            input_text=(
                "course_id: os.\n"
                "document_type: lab.\n"
                "chapter: 第三章 调度.\n"
                "section: 实验一 进程调度.\n"
                "text:\n"
                "实验要求：实现时间片轮转调度算法，并提交实验报告分析不同时间片长度的影响。"
            ),
            output_text=(
                f"(\"entity\"{TUPLE_DELIMITER}实验一 进程调度{TUPLE_DELIMITER}Experiment{TUPLE_DELIMITER}课程实验任务“实验一 进程调度”，出现在 实验一 进程调度 中。)\n"
                f"{RECORD_DELIMITER}\n"
                f"(\"entity\"{TUPLE_DELIMITER}时间片轮转调度算法{TUPLE_DELIMITER}AlgorithmOrMethod{TUPLE_DELIMITER}课程中的方法或算法“时间片轮转调度算法”，出现在 实验一 进程调度 中。)\n"
                f"{RECORD_DELIMITER}\n"
                f"(\"entity\"{TUPLE_DELIMITER}实验报告{TUPLE_DELIMITER}Assignment{TUPLE_DELIMITER}课程作业或题组“实验报告”，出现在 实验一 进程调度 中。)\n"
                f"{RECORD_DELIMITER}\n"
                f"(\"relationship\"{TUPLE_DELIMITER}实验一 进程调度{TUPLE_DELIMITER}时间片轮转调度算法{TUPLE_DELIMITER}[type={implemented_by_name}] 实验要求实现该算法{TUPLE_DELIMITER}9)\n"
                f"{RECORD_DELIMITER}\n"
                f"(\"relationship\"{TUPLE_DELIMITER}时间片轮转调度算法{TUPLE_DELIMITER}实验报告{TUPLE_DELIMITER}[type={evaluated_by_name}] 实验报告用于评估算法实现效果{TUPLE_DELIMITER}8)\n"
                f"{COMPLETION_DELIMITER}"
            ),
            note="手写最小 few-shot 示例：方法/实验/作业型样本。",
        ),
    ]

    return examples[: max(limit, 0)]


def _select_fewshot_examples(
    audit_records: Sequence[Dict[str, Any]],
    fewshot_k: int,
    *,
    schema_catalog: SchemaCatalog,
    input_max_chars: int,
    max_entities: int,
    max_relations: int,
) -> tuple[List[FewShotExample], str, List[str], List[str], List[Dict[str, Any]]]:
    if fewshot_k <= 0:
        return [], "disabled", [], ["fewshot_k <= 0，未生成 few-shot 示例。"], []

    usable = [
        record
        for record in audit_records
        if record.get("gold_entities")
        and record.get("gold_relations")
        and _clean_string(record.get("text"))
    ]
    if not usable:
        return [], "missing_audit_gold", [], ["未发现可直接复用的 audit gold 标注，将退回手写最小 few-shot 示例。"], []

    entity_schema_names = {item.name for item in schema_catalog.entity_types}
    relation_schema_names = {item.name for item in schema_catalog.relation_types}
    ordered = sorted(enumerate(usable), key=lambda pair: _audit_record_sort_key(pair[1]))
    remaining = list(ordered)
    selected_pairs: List[tuple[int, Dict[str, Any]]] = []
    covered_entity_types: set[str] = set()
    covered_relation_types: set[str] = set()

    while remaining and len(selected_pairs) < fewshot_k:
        def coverage_sort_key(pair: tuple[int, Dict[str, Any]]) -> tuple[int, int, tuple[int, int, int, str], int]:
            index, record = pair
            entity_types, relation_types = _record_rendered_gold_types(
                record,
                schema_entity_names=entity_schema_names,
                schema_relation_names=relation_schema_names,
                max_entities=max_entities,
                max_relations=max_relations,
            )
            new_relation_count = len(relation_types - covered_relation_types)
            new_entity_count = len(entity_types - covered_entity_types)
            return (-new_relation_count, -new_entity_count, _audit_record_sort_key(record), index)

        best_pair = min(remaining, key=coverage_sort_key)
        remaining.remove(best_pair)
        selected_pairs.append(best_pair)
        entity_types, relation_types = _record_rendered_gold_types(
            best_pair[1],
            schema_entity_names=entity_schema_names,
            schema_relation_names=relation_schema_names,
            max_entities=max_entities,
            max_relations=max_relations,
        )
        covered_entity_types.update(entity_types)
        covered_relation_types.update(relation_types)

    selected = [record for _, record in selected_pairs]

    examples = [
        _build_fewshot_example_from_audit(
            record,
            input_max_chars=input_max_chars,
            max_entities=max_entities,
            max_relations=max_relations,
        )
        for record in selected[:fewshot_k]
    ]
    source_ids = [example.source_sample_id for example in examples if example.source_sample_id]
    notes = [
        "few-shot 示例按关系类型覆盖优先贪心选择；同等覆盖下再比较实体类型覆盖和原有样本优先级。"
    ]
    return examples, "audit_gold", source_ids, notes, selected[:fewshot_k]


def _select_fewshot_examples_hybrid(
    audit_records: Sequence[Dict[str, Any]],
    fewshot_k: int,
    *,
    schema_catalog: SchemaCatalog,
    input_max_chars: int,
    max_entities: int,
    max_relations: int,
    target_texts: Sequence[str] | None = None,
) -> tuple[List[FewShotExample], str, List[str], List[str], List[Dict[str, Any]]]:
    """混合策略 few-shot 选择：TF-IDF 相关性预筛选 + 贪心类型覆盖。

    相比 _select_fewshot_examples（纯贪心覆盖），本函数额外考虑
    候选样本与目标文本的内容相似度，在保证类型覆盖的同时选择
    内容更相关的示例。

    参数：
      target_texts: 待抽取的目标文本列表。若提供，则用 TF-IDF 预筛选；
                    若为 None，则退回纯贪心策略。
    """
    if fewshot_k <= 0:
        return [], "disabled", [], ["fewshot_k <= 0，未生成 few-shot 示例。"], []

    usable = [
        record
        for record in audit_records
        if record.get("gold_entities")
        and record.get("gold_relations")
        and _clean_string(record.get("text"))
    ]
    if not usable:
        return [], "missing_audit_gold", [], ["未发现可直接复用的 audit gold 标注，将退回手写最小 few-shot 示例。"], []

    # 如果没有目标文本或候选太少，退回纯贪心
    if not target_texts or len(usable) <= fewshot_k:
        return _select_fewshot_examples(
            audit_records, fewshot_k,
            schema_catalog=schema_catalog,
            input_max_chars=input_max_chars,
            max_entities=max_entities,
            max_relations=max_relations,
        )

    # 使用混合策略
    try:
        from .fewshot_selector import select_fewshot_hybrid
    except ImportError:
        # sklearn 不可用时退回纯贪心
        return _select_fewshot_examples(
            audit_records, fewshot_k,
            schema_catalog=schema_catalog,
            input_max_chars=input_max_chars,
            max_entities=max_entities,
            max_relations=max_relations,
        )

    entity_type_names = [item.name for item in schema_catalog.entity_types]
    relation_type_names = [item.name for item in schema_catalog.relation_types]

    selected, report = select_fewshot_hybrid(
        candidates=usable,
        target_texts=target_texts,
        k=fewshot_k,
        entity_type_names=entity_type_names,
        relation_type_names=relation_type_names,
    )

    if not selected:
        return _select_fewshot_examples(
            audit_records, fewshot_k,
            schema_catalog=schema_catalog,
            input_max_chars=input_max_chars,
            max_entities=max_entities,
            max_relations=max_relations,
        )

    examples = [
        _build_fewshot_example_from_audit(
            record,
            input_max_chars=input_max_chars,
            max_entities=max_entities,
            max_relations=max_relations,
        )
        for record in selected[:fewshot_k]
    ]
    source_ids = [example.source_sample_id for example in examples if example.source_sample_id]
    notes = [
        f"few-shot 示例由混合策略选择（TF-IDF 预筛选 + 贪心类型覆盖）；"
        f"预筛选池 {report.get('prefilter_pool_size', '?')} 个，"
        f"关系覆盖 {report.get('relation_type_coverage', 0):.0%}。"
    ]
    return examples, "hybrid_tfidf_greedy", source_ids, notes, selected[:fewshot_k]


def build_schema_fewshot_prompt(
    schema_catalog: SchemaCatalog,
    language: str,
    schema_aware_prompt: str,
    examples: Sequence[FewShotExample],
) -> str:
    prompt = schema_aware_prompt
    prompt += "\n-Few-shot 示例-\n"
    if not examples:
        prompt += "- 当前未附加 few-shot 示例。\n"
        return prompt

    for index, example in enumerate(examples, start=1):
        prompt += (
            f"\nExample {index} ({example.guessed_sample_type})\n"
            "input:\n"
            f"{example.input_text}\n"
            "output:\n"
            f"{example.output_text}\n"
            f"# note: {example.note}\n"
        )
    return prompt


def _format_distilled_micro_example_line(
    record: Dict[str, Any],
    relation: Dict[str, Any],
    entity_name_map: Dict[str, str],
    *,
    max_chars: int,
) -> str:
    source_name = entity_name_map.get(_clean_string(relation.get("source_entity_id")), "未知源实体")
    target_name = entity_name_map.get(_clean_string(relation.get("target_entity_id")), "未知目标实体")
    relation_type = _clean_string(relation.get("type")) or "related_to"
    direction_hint = MICRO_RELATION_DIRECTION_HINTS.get(relation_type, "source -> target")
    source_id = _clean_string(record.get("source_sample_id")) or _clean_string(record.get("id")) or "unknown-source"

    line = (
        f"- {source_id}: {source_name} -> {target_name}: "
        f"[type={relation_type}] {direction_hint}"
    )
    if len(line) <= max_chars:
        return line

    compact_line = (
        f"- {source_id}: {_shorten_text(source_name, 14)} -> {_shorten_text(target_name, 14)}: "
        f"[type={relation_type}] {direction_hint}"
    )
    if len(compact_line) <= max_chars:
        return compact_line

    minimal_line = (
        f"- {source_id}: {_shorten_text(source_name, 10)} -> {_shorten_text(target_name, 10)}: "
        f"[type={relation_type}]"
    )
    return _shorten_text(minimal_line, max_chars)


def _build_distilled_micro_example_items(
    selected_records: Sequence[Dict[str, Any]],
    *,
    max_entities: int,
    max_relations: int,
    max_chars: int,
    max_examples: int = DISTILLED_MAX_MICRO_EXAMPLES,
) -> List[Dict[str, str]]:
    candidates: List[Dict[str, str]] = []
    for record in selected_records:
        entities = [item for item in record.get("gold_entities", []) if isinstance(item, dict)]
        relations = [item for item in record.get("gold_relations", []) if isinstance(item, dict)]
        selected_entities, selected_relations = _select_fewshot_gold_items(
            entities,
            relations,
            max_entities=max_entities,
            max_relations=max_relations,
        )
        entity_name_map = {
            _clean_string(item.get("entity_id")): _clean_string(item.get("name")) or _clean_string(item.get("normalized_name"))
            for item in selected_entities
        }
        source_id = _clean_string(record.get("source_sample_id")) or _clean_string(record.get("id")) or "unknown-source"
        for relation in selected_relations:
            relation_type = _clean_string(relation.get("type")) or "related_to"
            candidates.append({
                "relation_type": relation_type,
                "source_sample_id": source_id,
                "line": _format_distilled_micro_example_line(
                    record,
                    relation,
                    entity_name_map,
                    max_chars=max_chars,
                ),
            })

    if max_examples <= 0:
        return []

    selected_items: List[Dict[str, str]] = []
    selected_line_values: set[str] = set()
    covered_relation_types: set[str] = set()

    for item in candidates:
        relation_type = item["relation_type"]
        line = item["line"]
        if relation_type in covered_relation_types:
            continue
        selected_items.append(item)
        selected_line_values.add(line)
        covered_relation_types.add(relation_type)
        if len(selected_items) >= max_examples:
            return selected_items

    for item in candidates:
        line = item["line"]
        if line in selected_line_values:
            continue
        selected_items.append(item)
        selected_line_values.add(line)
        if len(selected_items) >= max_examples:
            return selected_items

    return selected_items


def _build_distilled_micro_example_lines(
    selected_records: Sequence[Dict[str, Any]],
    *,
    max_entities: int,
    max_relations: int,
    max_chars: int,
    max_examples: int = DISTILLED_MAX_MICRO_EXAMPLES,
) -> List[str]:
    return [
        item["line"]
        for item in _build_distilled_micro_example_items(
            selected_records,
            max_entities=max_entities,
            max_relations=max_relations,
            max_chars=max_chars,
            max_examples=max_examples,
        )
    ]


def _build_rendered_micro_example_coverage(
    schema_catalog: SchemaCatalog,
    micro_example_items: Sequence[Dict[str, str]],
) -> Dict[str, Any]:
    covered_relation_types = _dedupe_preserve_order([
        item.get("relation_type", "")
        for item in micro_example_items
        if item.get("relation_type")
    ])
    relation_order = [item.name for item in schema_catalog.relation_types]
    missing_relation_types = [
        relation_type
        for relation_type in relation_order
        if relation_type not in set(covered_relation_types)
    ]
    return {
        "selection_strategy": "first_distilled_micro_example_per_relation",
        "rendered_micro_example_count": len(micro_example_items),
        "covered_relation_types": covered_relation_types,
        "missing_relation_types": missing_relation_types,
        "source_sample_ids": _dedupe_preserve_order([
            item.get("source_sample_id", "")
            for item in micro_example_items
            if item.get("source_sample_id")
        ]),
    }


def _format_rendered_micro_example_coverage_summary(coverage: Dict[str, Any]) -> str:
    covered_relation_count = len(coverage.get("covered_relation_types") or [])
    missing_relation_count = len(coverage.get("missing_relation_types") or [])
    total_relation_count = covered_relation_count + missing_relation_count
    return (
        "distilled 渲染覆盖摘要："
        f"关系 {covered_relation_count}/{total_relation_count}，"
        f"micro-example {coverage.get('rendered_micro_example_count', 0)} 条"
    )


def build_schema_fewshot_distilled_prompt(
    schema_catalog: SchemaCatalog,
    language: str,
    schema_aware_directional_prompt: str,
    selected_records: Sequence[Dict[str, Any]],
    *,
    max_entities: int,
    max_relations: int,
    max_micro_example_chars: int = DISTILLED_MICRO_EXAMPLE_MAX_CHARS,
    micro_example_lines: Sequence[str] | None = None,
) -> str:
    _ = (schema_catalog, language)
    prompt = schema_aware_directional_prompt.rstrip()
    if micro_example_lines is None:
        micro_example_lines = _build_distilled_micro_example_lines(
            selected_records,
            max_entities=max_entities,
            max_relations=max_relations,
            max_chars=max_micro_example_chars,
            max_examples=DISTILLED_MAX_MICRO_EXAMPLES,
        )

    prompt += (
        "\n\n-Micro-examples-\n"
        "只蒸馏端点和类型，不嵌完整 audit text。\n"
    )
    if not micro_example_lines:
        prompt += "- 当前无可蒸馏 micro-example；继续依赖 schema 与方向卡片。\n"
    else:
        prompt += "\n".join(micro_example_lines) + "\n"
    return prompt


def _format_distilled_v2_negative_rules() -> str:
    lines = [
        "-Short negative direction rules-",
    ]
    for item in DISTILLED_V2_NEGATIVE_RULES:
        lines.append(f"- `{item['relation_type']}`：{item['rule']}")
    return "\n".join(lines) + "\n"


def build_schema_fewshot_distilled_v2_prompt(schema_fewshot_distilled_prompt: str) -> str:
    return schema_fewshot_distilled_prompt.rstrip() + "\n\n" + _format_distilled_v2_negative_rules()


STRICT_TUPLE_FORMAT_BLOCK = """-严格 tuple 输出格式约束（关键）-
以下是 GraphRAG 原生 tuple 解析器的严格要求，适配层不会帮你修正，这些规则必须在你的输出中满足：
1. 每条 record 必须是完整的一行，形如 `("entity"<|>NAME<|>TYPE<|>DESC)` 或 `("relationship"<|>SRC<|>TGT<|>DESC<|>STRENGTH)`；record 之间只用 `##` 分隔。
2. **不要**在 entity_name、entity_type、source_entity、target_entity 外面加任何双引号或单引号，也不要加中文引号“”‘’；这几个字段必须是裸文本。解析器不会剥引号。
3. 每条 record 必须以 `(` 开头、以 `)` 结尾，确保括号严格成对；description 或 evidence 内部不得出现独立的 `)`、`##` 或 `<|>`，否则会被误判为 record 边界。
4. 如需在 description 中引用原文，不要使用引号包裹；若必须引用，改为用顿号或破折号分隔，避免字符串歧义。
5. 整段输出结束时只输出一次 `<|COMPLETE|>`，前后不要加任何额外文字、解释或 markdown。
6. 如果某个 record 内容不确定或格式不完整，**不要输出这条 record**；宁可漏抽也不要输出破损的 tuple。
7. entity_type 从给定类型列表里取；大小写会被解析器规范化，但请尽量保持列表字面（PascalCase），不要用同义词、不要加空格。
"""


PRECISION_SUPPRESSION_BLOCK = """-精度向抑制规则（关键，避免过度抽取）-
以下内容**必须**跳过，不抽取为实体或关系端点。它们不是 audit gold 的覆盖对象，也不服务于课程问答目标：
- 页眉页脚、页码、目录索引号（如“第 156 页”“Contents”）；
- 章节通用前言/结语性短语（如“本章小结”“习题与思考”之类的非稳定知识容器）；
- 模型用来连接话题的过渡性短语（如“接下来介绍”“需要注意的是”）；
- 辅助说明性短语（如“如图所示”“如下表”“见下文”）；
- 单独出现的动词、形容词或连接词；
- 无命名实体指向的代词、数字、符号；
- 看似实体但在当前样本中没有被实际讨论的引用项（例如样本里只出现一次名字、没有任何属性描述的对象）；
- 已作为某实体 alias 出现的简称或缩写（应并入主实体，不单独成实体）；
- 课程结构外的通用词（如“方法”“算法”“概念”等不带具体语义的通用名词）。

只保留：原文**正在讨论、解释、应用或考核**的课程结构、知识点、概念、术语、公式、方法、实验、作业、工具平台。
"""


def build_schema_fewshot_distilled_v2_strict_tuple_prompt(
    schema_fewshot_distilled_v2_prompt: str,
) -> str:
    """在 schema_fewshot_distilled_v2 末尾追加严格 tuple 格式约束。

    来源：`prompts/candidates/_shared/strict_tuple_format_block.md`

    注意：这个候选**不经过** `_render_delimiter_placeholders`，因为 strict_tuple 块
    里故意保留了 `<|>` / `{` 等字面符号，作为对模型的格式演示，不是模板占位。

    关于精度向抑制规则：2026-05-11 exp5 验证发现该规则让 recall 从 0.61 掉到 0.39，
    precision 几乎不变（0.303 → 0.298），净效果是负的——LLM 把正经的 gold 实体也
    一并跳过了。因此该规则已回滚，不再进入 prompt。详见
    `experiments/native_extraction_bridge/README.md` 的 exp5 记录。
    """

    return (
        schema_fewshot_distilled_v2_prompt.rstrip()
        + "\n\n"
        + STRICT_TUPLE_FORMAT_BLOCK.rstrip()
        + "\n"
    )


def _candidate_readme(
    name: str,
    generated_at: str,
    source_type: str,
    base_prompt_source: Optional[str],
    notes: Sequence[str],
    schema_used: bool,
    fewshot_used: bool,
    fewshot_strategy: Optional[str],
) -> str:
    note_lines = "\n".join(f"- {note}" for note in notes) if notes else "- 无"
    return f"""# {name}

## 作用

- 候选名称：`{name}`
- 生成时间：`{generated_at}`
- 来源类型：`{source_type}`
- 基础 Prompt 来源：`{base_prompt_source or "未使用外部文件，脚本内生成"}`
- 是否注入 schema：`{"yes" if schema_used else "no"}`
- 是否包含 few-shot：`{"yes" if fewshot_used else "no"}`
- few-shot 策略：`{fewshot_strategy or "none"}`

## 用途

- 供后续候选 Prompt 抽取执行、规则化自动评测、top-k Prompt 筛选和问答级验证使用。

## 备注

{note_lines}
"""


def _candidate_manifest_entry(
    *,
    candidate_name: str,
    source_type: str,
    base_prompt_source: Optional[str],
    schema_used: bool,
    audit_used: bool,
    fewshot_used: bool,
    fewshot_example_count: int,
    fewshot_strategy: Optional[str],
    notes: Sequence[str],
    output_dir: Path,
    generated_at: str,
    fewshot_compression: Optional[Dict[str, int]] = None,
    fewshot_coverage: Optional[Dict[str, Any]] = None,
    extra_fields: Optional[Dict[str, Any]] = None,
) -> Dict[str, Any]:
    entry = {
        "candidate_name": candidate_name,
        "source_type": source_type,
        "base_prompt_source": base_prompt_source,
        "schema_used": schema_used,
        "audit_used": audit_used,
        "fewshot_used": fewshot_used,
        "fewshot_example_count": fewshot_example_count,
        "fewshot_strategy": fewshot_strategy,
        "generation_time": generated_at,
        "files": {
            "prompt": _safe_relpath(output_dir / candidate_name / "prompt.txt"),
            "readme": _safe_relpath(output_dir / candidate_name / "README.md"),
        },
        "notes": list(notes),
    }
    if fewshot_compression is not None:
        entry["fewshot_compression"] = dict(fewshot_compression)
    if fewshot_coverage is not None:
        entry["fewshot_coverage"] = dict(fewshot_coverage)
    if extra_fields:
        for key, value in extra_fields.items():
            entry[key] = value
    return entry


def _merge_manifest_entry(existing: Dict[str, Any], generated: Dict[str, Any]) -> Dict[str, Any]:
    merged = dict(generated)
    if generated.get("candidate_name") == "auto_tuned" and generated.get("source_type") == "graphrag_prompt_tune":
        for key in AUTO_TUNED_MANIFEST_PASSTHROUGH_FIELDS:
            if key in existing and key not in merged:
                merged[key] = existing[key]

    generated_notes = generated.get("notes", []) if isinstance(generated.get("notes"), list) else []
    merged["notes"] = _dedupe_preserve_order(generated_notes)
    return merged


def generate_candidate_prompts(
    *,
    schema_dir: Path,
    samples_file: Optional[Path],
    audit_file: Optional[Path],
    default_prompt_dir: Path,
    auto_tuned_prompt_dir: Path,
    output_dir: Path,
    fewshot_k: int = DEFAULT_FEWSHOT_K,
    fewshot_input_max_chars: int = DEFAULT_FEWSHOT_INPUT_MAX_CHARS,
    fewshot_max_entities: int = DEFAULT_FEWSHOT_MAX_ENTITIES,
    fewshot_max_relations: int = DEFAULT_FEWSHOT_MAX_RELATIONS,
    language: str = DEFAULT_LANGUAGE,
    overwrite: bool = False,
    report_file: Optional[Path] = None,
) -> Dict[str, Any]:
    generated_at = _now_iso()
    schema_catalog = load_schema_catalog(schema_dir)
    manifest_path = output_dir / "manifest.json"
    existing_manifest = _load_existing_manifest(manifest_path)
    existing_candidate_map = {
        item.get("candidate_name"): item
        for item in existing_manifest.get("candidates", [])
        if isinstance(item, dict) and item.get("candidate_name")
    }

    resolved_samples_file = _resolve_optional_path(samples_file, DEFAULT_SAMPLES_CANDIDATES)
    resolved_audit_file = _resolve_optional_path(audit_file, DEFAULT_AUDIT_CANDIDATES)

    sample_records: List[Dict[str, Any]] = []
    if resolved_samples_file and resolved_samples_file.exists():
        sample_records = _load_json_records(resolved_samples_file, ("samples",))

    audit_records: List[Dict[str, Any]] = []
    if resolved_audit_file and resolved_audit_file.exists():
        audit_records = _load_json_records(resolved_audit_file, ("audit_samples",))

    default_prompt_source = load_default_prompt_source(default_prompt_dir)
    auto_tuned_prompt_source = load_auto_tuned_prompt_source(
        auto_tuned_prompt_dir,
        manifest_entry=existing_candidate_map.get("auto_tuned"),
    )

    default_prompt_text = build_default_candidate_prompt(
        schema_catalog=schema_catalog,
        language=language,
        base_prompt_text=default_prompt_source.text,
    )
    default_source_type = "default_adapted"
    default_notes = list(default_prompt_source.notes)
    if default_prompt_source.path is None:
        default_source_type = "generated_minimal_default"
        default_prompt_text = build_minimal_default_prompt(schema_catalog, language)
    else:
        default_notes.append("default 候选直接基于当前 GraphRAG 默认 extract_graph Prompt 做轻量课程域微调，并保留原始结构与输出格式。")
    default_guarded_prompt_text = build_default_guarded_prompt(default_prompt_text)

    schema_aware_base_source = auto_tuned_prompt_source.path or default_prompt_source.path
    schema_aware_base_text = _strip_legacy_examples(auto_tuned_prompt_source.text or default_prompt_source.text)
    schema_aware_base_label = "官方 auto_tuned Prompt" if auto_tuned_prompt_source.text else "默认 GraphRAG Prompt"
    schema_aware_prompt_text = build_schema_aware_prompt(
        schema_catalog=schema_catalog,
        language=language,
        base_prompt_text=schema_aware_base_text,
        base_label=schema_aware_base_label,
    )
    schema_aware_directional_prompt_text = build_schema_aware_directional_prompt(
        schema_catalog=schema_catalog,
        schema_aware_prompt=schema_aware_prompt_text,
    )
    schema_aware_directional_v2_prompt_text = build_schema_aware_directional_v2_prompt(
        schema_aware_directional_prompt_text
    )

    fewshot_examples, fewshot_strategy, fewshot_source_ids, fewshot_notes, fewshot_selected_records = _select_fewshot_examples(
        audit_records=audit_records,
        fewshot_k=fewshot_k,
        schema_catalog=schema_catalog,
        input_max_chars=fewshot_input_max_chars,
        max_entities=fewshot_max_entities,
        max_relations=fewshot_max_relations,
    )
    if fewshot_strategy != "audit_gold":
        manual_examples = _manual_fewshot_examples(schema_catalog, fewshot_k)
        fewshot_examples = manual_examples[:fewshot_k]
        fewshot_selected_records = []
    schema_fewshot_prompt_text = build_schema_fewshot_prompt(
        schema_catalog=schema_catalog,
        language=language,
        schema_aware_prompt=schema_aware_prompt_text,
        examples=fewshot_examples,
    )
    distilled_micro_example_items = _build_distilled_micro_example_items(
        fewshot_selected_records,
        max_entities=fewshot_max_entities,
        max_relations=fewshot_max_relations,
        max_chars=DISTILLED_MICRO_EXAMPLE_MAX_CHARS,
        max_examples=DISTILLED_MAX_MICRO_EXAMPLES,
    )
    rendered_micro_example_coverage = _build_rendered_micro_example_coverage(
        schema_catalog,
        distilled_micro_example_items,
    )
    schema_fewshot_distilled_prompt_text = build_schema_fewshot_distilled_prompt(
        schema_catalog=schema_catalog,
        language=language,
        schema_aware_directional_prompt=schema_aware_directional_prompt_text,
        selected_records=fewshot_selected_records,
        max_entities=fewshot_max_entities,
        max_relations=fewshot_max_relations,
        micro_example_lines=[item["line"] for item in distilled_micro_example_items],
    )
    schema_fewshot_distilled_v2_prompt_text = build_schema_fewshot_distilled_v2_prompt(
        schema_fewshot_distilled_prompt_text
    )
    schema_fewshot_distilled_v3_prompt_text = build_schema_fewshot_distilled_v3_prompt(
        [item["line"] for item in distilled_micro_example_items]
    )

    # 2026-05-11：在 v2 基础上追加严格 tuple 格式约束和精度向抑制规则；
    # 注意：v2 已经过 delimiter 渲染后才追加格式块，避免 strict_tuple 块里
    # 故意保留的 `<|>` 字面被 _render_delimiter_placeholders 再次替换。
    schema_fewshot_distilled_v2_strict_tuple_prompt_text = (
        build_schema_fewshot_distilled_v2_strict_tuple_prompt(
            _render_delimiter_placeholders(schema_fewshot_distilled_v2_prompt_text)
        )
    )

    auto_tuned_prompt_text = auto_tuned_prompt_source.text
    auto_tuned_source_type = auto_tuned_prompt_source.source_type
    auto_tuned_notes = list(auto_tuned_prompt_source.notes)
    if not auto_tuned_prompt_text:
        auto_tuned_prompt_text = default_prompt_text
        auto_tuned_source_type = "fallback_default_copy"
        auto_tuned_notes.append("由于未发现实际 auto-tuned Prompt，当前候选内容回退为 default 候选 Prompt，以保证目录结构和后续切换流程可运行。")

    default_prompt_text = _render_delimiter_placeholders(default_prompt_text)
    default_guarded_prompt_text = _render_delimiter_placeholders(default_guarded_prompt_text)
    auto_tuned_prompt_text = _render_delimiter_placeholders(auto_tuned_prompt_text)
    schema_aware_prompt_text = _render_delimiter_placeholders(schema_aware_prompt_text)
    schema_fewshot_prompt_text = _render_delimiter_placeholders(schema_fewshot_prompt_text)
    schema_aware_directional_prompt_text = _render_delimiter_placeholders(schema_aware_directional_prompt_text)
    schema_aware_directional_v2_prompt_text = _render_delimiter_placeholders(schema_aware_directional_v2_prompt_text)
    schema_fewshot_distilled_prompt_text = _render_delimiter_placeholders(schema_fewshot_distilled_prompt_text)
    schema_fewshot_distilled_v2_prompt_text = _render_delimiter_placeholders(schema_fewshot_distilled_v2_prompt_text)
    schema_fewshot_distilled_v3_prompt_text = _render_delimiter_placeholders(schema_fewshot_distilled_v3_prompt_text)

    output_dir.mkdir(parents=True, exist_ok=True)

    fewshot_coverage = _build_fewshot_coverage_report(
        schema_catalog=schema_catalog,
        selected_records=fewshot_selected_records,
        selected_example_ids=[example.example_id for example in fewshot_examples],
        selection_strategy=(
            FEWSHOT_COVERAGE_SELECTION_STRATEGY
            if fewshot_strategy == "audit_gold"
            else ("minimal_manual_examples" if fewshot_examples else fewshot_strategy)
        ),
        max_entities=fewshot_max_entities,
        max_relations=fewshot_max_relations,
    )
    fewshot_coverage_summary = _format_fewshot_coverage_summary(fewshot_coverage)
    rendered_micro_example_coverage_summary = _format_rendered_micro_example_coverage_summary(
        rendered_micro_example_coverage
    )

    default_candidate_notes = list(default_notes)
    if sample_records:
        default_candidate_notes.append(f"检测到 prompt tuning 样本 {len(sample_records)} 条，可供后续 few-shot/评测使用。")
    default_candidate_notes = _dedupe_preserve_order(default_candidate_notes)
    default_guarded_notes = _dedupe_preserve_order(
        [
            "default_guarded 继承 default，只追加严格 JSON 根对象与端点匹配守卫。",
            "用于验证强基线在不引入长 few-shot 的情况下能否降低 parse/leak 风险。",
        ]
    )

    schema_aware_notes = [
        f"schema_aware 优先基于{schema_aware_base_label}自动增强；若 auto_tuned 缺失则回退到 default。",
        "在基底 Prompt 上显式注入实体类型、关系类型和关键抽取规则摘要。",
        "关系输出仍沿用 GraphRAG tuple 结构，但要求 relationship_description 以 [type=<relation_type>] 开头，便于后续评测解析。",
    ]
    schema_aware_notes = _dedupe_preserve_order(schema_aware_notes)
    schema_aware_directional_notes = _dedupe_preserve_order(
        [
            "schema_aware_directional 继承 schema_aware，并额外加入短关系方向卡片。",
            "方向卡片覆盖 applied_in / appears_in / defined_by / evaluated_by / related_to 等高风险关系，不嵌入完整 audit 样本文本。",
            "用于降低关系反向、related_to 滥用和缺失端点占位风险。",
        ]
    )
    schema_aware_directional_v2_notes = _dedupe_preserve_order(
        [
            "schema_aware_directional_v2 继承 schema_aware_directional，并追加 material_7 高频失败族守卫。",
            "v2 聚焦 appears_in 反向位置、belongs_to 概念分类误用、defined_by 术语符号线索和端点逐字匹配。",
            "不嵌入完整 audit 样本文本，作为低成本 prompt-only 对照。",
        ]
    )
    schema_fewshot_notes = list(fewshot_notes)
    schema_fewshot_notes.insert(0, f"schema_fewshot 继承 schema_aware，并继续沿用 {schema_aware_base_label} 作为底稿。")
    if fewshot_source_ids:
        schema_fewshot_notes.append(f"few-shot 来源样本：{', '.join(fewshot_source_ids)}")
    if fewshot_examples and fewshot_examples[0].source_kind == "manual_minimal":
        schema_fewshot_notes.append("当前 few-shot 使用手写最小示例，仅作为 audit gold 缺失时的降级方案。")
    schema_fewshot_notes.append(fewshot_coverage_summary)
    schema_fewshot_notes.append(
        "few-shot 示例已压缩：限制输入长度、实体数量和关系数量，避免候选 Prompt 过长。"
    )
    schema_fewshot_notes.append(FEWSHOT_COMPRESSION_CAVEAT)
    schema_fewshot_notes = _dedupe_preserve_order(schema_fewshot_notes)
    schema_fewshot_distilled_notes = _dedupe_preserve_order(
        [
            "schema_fewshot_distilled 继承 schema_aware_directional，只保留关系方向 micro-examples。",
            "distilled micro-examples 来源于已选 audit gold，但省略完整输入文本，降低 overlap/holdout 泄漏风险。",
            "长度目标接近 schema_aware，避免回到长 few-shot Prompt。",
            fewshot_coverage_summary,
            rendered_micro_example_coverage_summary,
            FEWSHOT_COMPRESSION_CAVEAT,
        ]
    )
    schema_fewshot_distilled_v2_notes = _dedupe_preserve_order(
        [
            "schema_fewshot_distilled_v2 继承 schema_fewshot_distilled，并追加短反向负例和缺端点跳过规则。",
            "v2 不嵌入完整 audit 样本文本，重点压制 evaluated_by / appears_in / defined_by / applied_in / related_to 的高频残留错误。",
            "长度目标仍接近 schema_aware_directional，避免回到 full schema_fewshot 的高成本形态。",
            rendered_micro_example_coverage_summary,
            FEWSHOT_COMPRESSION_CAVEAT,
        ]
    )
    schema_fewshot_distilled_v3_notes = _dedupe_preserve_order(
        [
            "schema_fewshot_distilled_v3 采用低成本失败族守卫，不再继承长 schema/few-shot 正文。",
            "v3 继续只使用 1 条 micro-example，不嵌入完整 audit text，目标成本不超过 default 的 1.10 倍。",
            "重点压制 appears_in 反向、belongs_to 概念分类、defined_by 非符号术语和缺失端点。",
            rendered_micro_example_coverage_summary,
            FEWSHOT_COMPRESSION_CAVEAT,
        ]
    )
    auto_tuned_notes = _dedupe_preserve_order(auto_tuned_notes)

    fewshot_compression = {
        "input_max_chars": fewshot_input_max_chars,
        "max_entities": fewshot_max_entities,
        "max_relations": fewshot_max_relations,
    }
    distilled_extra_fields = {
        "source_sample_ids": list(fewshot_source_ids),
        "coverage": dict(rendered_micro_example_coverage),
        "selected_audit_coverage": dict(fewshot_coverage),
        "rendered_micro_example_coverage": dict(rendered_micro_example_coverage),
        "compression": {
            "strategy": "micro_examples_without_full_audit_text",
            "input_text_policy": "omit_full_audit_text",
            "max_micro_example_chars": DISTILLED_MICRO_EXAMPLE_MAX_CHARS,
            "max_micro_examples": DISTILLED_MAX_MICRO_EXAMPLES,
        },
        "length_policy": {
            "target": "near_schema_aware",
            "max_schema_aware_ratio": DISTILLED_MAX_SCHEMA_AWARE_RATIO,
            "base_prompt": "schema_aware_directional",
        },
    }
    distilled_v2_extra_fields = {
        **distilled_extra_fields,
        "negative_rule_policy": dict(DISTILLED_V2_NEGATIVE_RULE_POLICY),
        "length_policy": {
            "target": "near_schema_aware_directional",
            "max_schema_aware_ratio": DISTILLED_MAX_SCHEMA_AWARE_RATIO,
            "base_prompt": "schema_fewshot_distilled",
        },
    }
    v3_extra_fields = {
        **distilled_v2_extra_fields,
        "failure_family_policy": dict(V3_FAILURE_FAMILY_POLICY),
        "token_budget_policy": dict(V3_TOKEN_BUDGET_POLICY),
        "micro_example_policy": {
            "strategy": "failure_family_micro_examples",
            "source": "selected_audit_gold_relation_micro_examples",
            "max_micro_example_chars": DISTILLED_MICRO_EXAMPLE_MAX_CHARS,
            "max_micro_examples": DISTILLED_MAX_MICRO_EXAMPLES,
        },
    }

    # 2026-05-11 归档后只保留 4 个活跃候选；已归档候选的 prompt_text 仍由
    # 上面的构造链生成（schema_aware / schema_aware_directional /
    # schema_fewshot_distilled / schema_fewshot_distilled_v2 作为中间变量
    # 被依赖），只是不再落盘到 prompts/candidates/。
    # 归档清单见 prompts/candidates/_archive_json_format/ 和
    # _archive_superseded_tuple/。
    strict_tuple_format_guard_meta = {
        "applied_at": "2026-05-11",
        "source": "prompts/candidates/_shared/strict_tuple_format_block.md",
    }
    precision_suppression_rules_meta = {
        "applied_at": "2026-05-11",
        "target": "audit_entity_precision",
        "suppressed_categories": [
            "页眉页脚与页码",
            "章节通用前言/结语短语",
            "过渡性短语",
            "辅助说明性短语",
            "单独出现的动词、形容词或连接词",
            "无命名指向的代词、数字、符号",
            "无属性描述的一次性引用项",
            "已作 alias 的简称或缩写",
            "课程结构外的通用名词",
        ],
    }
    schema_fewshot_distilled_v2_strict_tuple_notes = _dedupe_preserve_order(
        [
            "schema_fewshot_distilled_v2_strict_tuple 继承 schema_fewshot_distilled_v2，并合入严格 tuple 输出格式约束（见 prompts/candidates/_shared/strict_tuple_format_block.md）。",
            "2026-05-11 桥接验证显示：v2 原版在原生 tuple 抽取器下有 21/101 引号污染、3/7 样本解析级联失败，加上严格格式约束后全部清零。",
            "2026-05-11 晚间追加「-精度向抑制规则-」章节，硬性拒绝 9 类非抽取对象（页眉页脚、过渡短语、辅助说明等），目标提升 audit_entity_precision。",
            "当前最佳候选，audit_relation_recall(holdout) 已达 0.347（配合 metadata-closure 后处理）。",
            FEWSHOT_COMPRESSION_CAVEAT,
        ]
    )

    candidates = [
        {
            "name": "default",
            "prompt_text": default_prompt_text,
            "source_type": default_source_type,
            "base_prompt_source": _safe_relpath(default_prompt_source.path),
            "schema_used": False,
            "audit_used": False,
            "fewshot_used": False,
            "fewshot_example_count": 0,
            "fewshot_strategy": None,
            "notes": default_candidate_notes,
        },
        {
            "name": "auto_tuned",
            "prompt_text": auto_tuned_prompt_text,
            "source_type": auto_tuned_source_type,
            "base_prompt_source": _safe_relpath(auto_tuned_prompt_source.path),
            "schema_used": False,
            "audit_used": False,
            "fewshot_used": False,
            "fewshot_example_count": 0,
            "fewshot_strategy": None,
            "notes": auto_tuned_notes,
        },
        {
            "name": "schema_aware_directional_v2",
            "prompt_text": schema_aware_directional_v2_prompt_text,
            "source_type": "schema_directional_v2",
            "base_prompt_source": _safe_relpath(schema_aware_base_source),
            "schema_used": True,
            "audit_used": False,
            "fewshot_used": False,
            "fewshot_example_count": 0,
            "fewshot_strategy": None,
            "notes": schema_aware_directional_v2_notes,
            "extra_manifest_fields": {
                "failure_family_policy": dict(V3_FAILURE_FAMILY_POLICY),
                "token_budget_policy": dict(V3_TOKEN_BUDGET_POLICY),
                "strict_tuple_format_guard": dict(strict_tuple_format_guard_meta),
            },
        },
        {
            "name": "schema_fewshot_distilled_v2_strict_tuple",
            "prompt_text": schema_fewshot_distilled_v2_strict_tuple_prompt_text,
            "source_type": "schema_fewshot_distilled_v2_strict_tuple",
            "base_prompt_source": _safe_relpath(schema_aware_base_source),
            "schema_used": True,
            "audit_used": bool(audit_records),
            "fewshot_used": bool(fewshot_selected_records),
            "fewshot_example_count": len(fewshot_selected_records),
            "fewshot_strategy": "distilled_negative_direction_rules_with_strict_tuple_guard",
            "notes": schema_fewshot_distilled_v2_strict_tuple_notes,
            "extra_manifest_fields": {
                **distilled_v2_extra_fields,
                "strict_tuple_format_guard": dict(strict_tuple_format_guard_meta),
                "precision_suppression_rules": dict(precision_suppression_rules_meta),
            },
        },
    ]

    manifest_candidates: List[Dict[str, Any]] = []
    for candidate in candidates:
        candidate_dir = output_dir / candidate["name"]
        candidate_dir.mkdir(parents=True, exist_ok=True)
        preserve_existing_auto_tuned = (
            candidate["name"] == "auto_tuned"
            and candidate["source_type"] == "graphrag_prompt_tune"
            and (candidate_dir / "README.md").exists()
        )
        if preserve_existing_auto_tuned:
            if not (candidate_dir / "prompt.txt").exists():
                _write_text(candidate_dir / "prompt.txt", candidate["prompt_text"], overwrite=True)
        else:
            _write_text(candidate_dir / "prompt.txt", candidate["prompt_text"], overwrite=overwrite)
            readme_text = _candidate_readme(
                name=candidate["name"],
                generated_at=generated_at,
                source_type=candidate["source_type"],
                base_prompt_source=candidate["base_prompt_source"],
                notes=candidate["notes"],
                schema_used=bool(candidate["schema_used"]),
                fewshot_used=bool(candidate["fewshot_used"]),
                fewshot_strategy=candidate["fewshot_strategy"],
            )
            _write_text(candidate_dir / "README.md", readme_text, overwrite=overwrite)

        generated_entry = _candidate_manifest_entry(
            candidate_name=candidate["name"],
            source_type=candidate["source_type"],
            base_prompt_source=candidate["base_prompt_source"],
            schema_used=bool(candidate["schema_used"]),
            audit_used=bool(candidate["audit_used"]),
            fewshot_used=bool(candidate["fewshot_used"]),
            fewshot_example_count=int(candidate["fewshot_example_count"]),
            fewshot_strategy=candidate["fewshot_strategy"],
            fewshot_compression=candidate.get("fewshot_compression"),
            fewshot_coverage=candidate.get("fewshot_coverage"),
            extra_fields=candidate.get("extra_manifest_fields"),
            notes=candidate["notes"],
            output_dir=output_dir,
            generated_at=generated_at,
        )
        existing_entry = existing_candidate_map.get(candidate["name"], {})
        manifest_candidates.append(_merge_manifest_entry(existing_entry, generated_entry))

    manifest = {
        **{k: v for k, v in existing_manifest.items() if k not in {"candidates", "last_updated_at"}},
        "task": "candidate_prompt_generation",
        "schema_version": schema_catalog.schema_version,
        "generated_at": generated_at,
        "last_updated_at": generated_at,
        "language": language,
        "output_dir": _safe_relpath(output_dir),
        "inputs": {
            "schema_dir": _safe_relpath(schema_dir),
            "entity_schema_path": schema_catalog.entity_schema_path,
            "relation_schema_path": schema_catalog.relation_schema_path,
            "extraction_rules_path": schema_catalog.extraction_rules_path,
            "samples_file": _safe_relpath(resolved_samples_file),
            "audit_file": _safe_relpath(resolved_audit_file),
            "default_prompt_dir": _safe_relpath(default_prompt_dir),
            "default_prompt_source": _safe_relpath(default_prompt_source.path),
            "auto_tuned_prompt_dir": _safe_relpath(auto_tuned_prompt_dir),
            "auto_tuned_prompt_source": _safe_relpath(auto_tuned_prompt_source.path),
        },
        "candidates": manifest_candidates,
    }

    _write_json(manifest_path, manifest, overwrite=overwrite)

    report = {
        "task": "candidate_prompt_generation_report",
        "generated_at": generated_at,
        "schema_version": schema_catalog.schema_version,
        "counts": {
            "entity_type_count": len(schema_catalog.entity_types),
            "relation_type_count": len(schema_catalog.relation_types),
            "sample_count": len(sample_records),
            "audit_sample_count": len(audit_records),
            "fewshot_example_count": len(fewshot_examples),
        },
        "fallbacks": {
            "default_prompt_missing": default_prompt_source.path is None,
            "auto_tuned_missing": auto_tuned_prompt_source.path is None,
            "audit_gold_missing": fewshot_strategy != "audit_gold",
        },
        "fewshot": {
            "strategy": "audit_gold" if fewshot_strategy == "audit_gold" else "minimal_manual_examples",
            "source_sample_ids": fewshot_source_ids,
            "example_ids": [example.example_id for example in fewshot_examples],
            "compression": fewshot_compression,
        },
        "distilled": distilled_extra_fields,
        "distilled_v2": distilled_v2_extra_fields,
        "distilled_v3": v3_extra_fields,
        "fewshot_coverage": fewshot_coverage,
        "candidate_names": [candidate["candidate_name"] for candidate in manifest_candidates],
        "manifest_path": _safe_relpath(manifest_path),
        "notes": [
            "default 候选 Prompt 会优先沿用默认 GraphRAG extract_graph Prompt 文本，再做轻量课程域微调。",
            "schema_aware / schema_fewshot 会优先以 auto_tuned Prompt 为底稿自动增强；若 auto_tuned 缺失则回退到 default。",
            "schema_aware_directional / schema_fewshot_distilled / schema_fewshot_distilled_v2 用短方向卡片、micro-examples 和负例规则降低关系方向泄漏风险。",
            "default_guarded / schema_aware_directional_v2 / schema_fewshot_distilled_v3 用 material_7 高频失败族守卫和严格 JSON 输出守卫作为下一轮实验候选。",
            "schema 增强仍通过 relationship_description 的 [type=<relation_type>] 前缀表达关系类型，避免改坏现有 tuple 结构。",
            FEWSHOT_COMPRESSION_CAVEAT,
        ],
    }

    resolved_report_file = report_file or DEFAULT_REPORT_FILE
    _write_json(resolved_report_file, report, overwrite=overwrite)

    return {
        "manifest": manifest,
        "manifest_path": str(manifest_path.resolve()),
        "report": report,
        "report_path": str(Path(resolved_report_file).resolve()),
    }


def _build_parser() -> argparse.ArgumentParser:
    parser = argparse.ArgumentParser(description="生成课程知识图谱抽取候选 Prompt 及 manifest")
    parser.add_argument(
        "--schema_dir",
        default=str(DEFAULT_SCHEMA_DIR),
        help="schema 配置目录，默认 graphrag_pipeline/config/schema",
    )
    parser.add_argument(
        "--samples_file",
        default=None,
        help="prompt tuning 样本文件，默认自动探测 samples.json / prompt_tuning_samples.json",
    )
    parser.add_argument(
        "--audit_file",
        default=None,
        help="audit 校准集文件，默认自动探测 data/eval/audit_extraction_set.json",
    )
    parser.add_argument(
        "--default_prompt_dir",
        default=str(DEFAULT_DEFAULT_PROMPT_DIR),
        help="默认 Prompt 所在目录或文件，默认 graphrag_pipeline/prompts",
    )
    parser.add_argument(
        "--auto_tuned_prompt_dir",
        default=str(DEFAULT_AUTO_TUNED_PROMPT_DIR),
        help="GraphRAG prompt-tune 输出目录或文件，默认 graphrag_pipeline/prompts/auto_tuned",
    )
    parser.add_argument(
        "--output_dir",
        default=str(DEFAULT_OUTPUT_DIR),
        help="候选 Prompt 输出目录，默认 graphrag_pipeline/prompts/candidates",
    )
    parser.add_argument(
        "--fewshot_k",
        type=int,
        default=DEFAULT_FEWSHOT_K,
        help="few-shot 示例数量，默认 3",
    )
    parser.add_argument(
        "--fewshot-input-max-chars",
        type=int,
        default=DEFAULT_FEWSHOT_INPUT_MAX_CHARS,
        help="每条 few-shot 输入文本最大字符数，默认 450",
    )
    parser.add_argument(
        "--fewshot-max-entities",
        type=int,
        default=DEFAULT_FEWSHOT_MAX_ENTITIES,
        help="每条 few-shot 输出最多实体数，默认 3",
    )
    parser.add_argument(
        "--fewshot-max-relations",
        type=int,
        default=DEFAULT_FEWSHOT_MAX_RELATIONS,
        help="每条 few-shot 输出最多关系数，默认 1",
    )
    parser.add_argument(
        "--language",
        default=DEFAULT_LANGUAGE,
        help="Prompt 语言标记，默认 zh",
    )
    parser.add_argument(
        "--overwrite",
        action="store_true",
        help="覆盖已存在的 prompt / manifest / report 文件",
    )
    parser.add_argument(
        "--report_file",
        default=str(DEFAULT_REPORT_FILE),
        help="生成报告输出文件，默认 graphrag_pipeline/results/reports/prompt_generation_report.json",
    )
    return parser


def main(argv: Optional[Sequence[str]] = None) -> int:
    parser = _build_parser()
    args = parser.parse_args(argv)

    result = generate_candidate_prompts(
        schema_dir=Path(args.schema_dir).resolve(),
        samples_file=Path(args.samples_file).resolve() if args.samples_file else None,
        audit_file=Path(args.audit_file).resolve() if args.audit_file else None,
        default_prompt_dir=Path(args.default_prompt_dir).resolve(),
        auto_tuned_prompt_dir=Path(args.auto_tuned_prompt_dir).resolve(),
        output_dir=Path(args.output_dir).resolve(),
        fewshot_k=args.fewshot_k,
        fewshot_input_max_chars=args.fewshot_input_max_chars,
        fewshot_max_entities=args.fewshot_max_entities,
        fewshot_max_relations=args.fewshot_max_relations,
        language=args.language,
        overwrite=args.overwrite,
        report_file=Path(args.report_file).resolve() if args.report_file else None,
    )

    print(
        f"[完成] 已生成 {len(result['manifest']['candidates'])} 类候选 Prompt，manifest: {result['manifest_path']}，"
        f"report: {result['report_path']}"
    )
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
