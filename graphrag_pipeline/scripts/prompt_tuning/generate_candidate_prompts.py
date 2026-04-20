#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
候选 Prompt 生成脚本
===================

负责统一组织“课程知识图谱抽取”任务的多组候选 Prompt。

职责边界：
1. 读取默认 Prompt、schema、样本与 audit 数据。
2. 生成 default / auto_tuned / schema_aware / schema_fewshot 四类候选 Prompt。
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
AUTO_TUNED_MANIFEST_PASSTHROUGH_FIELDS = (
    "generation_method",
    "graphrag_invocation",
    "root_path",
    "config_path",
    "output_path",
    "status",
    "collected_files",
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
FEWSHOT_TYPE_PRIORITY = {
    "definition_or_formula": 0,
    "chapter_concept_explanation": 1,
    "algorithm_or_method": 2,
    "experiment_instruction": 3,
    "assignment_requirement": 4,
}
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
    source_types: List[str] = field(default_factory=list)
    target_types: List[str] = field(default_factory=list)


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


def _replace_last_entity_types(text: str, entity_names: str) -> str:
    pattern = re.compile(r"entity_types:\s*\[[^\]]*\]")
    matches = list(pattern.finditer(text))
    if not matches:
        return text
    match = matches[-1]
    return f"{text[:match.start()]}entity_types: [{entity_names}]{text[match.end():]}"


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
            source_types=[_clean_string(value) for value in item.get("source_types", []) if _clean_string(value)],
            target_types=[_clean_string(value) for value in item.get("target_types", []) if _clean_string(value)],
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
        entity_schema_path=str(entity_path.resolve()),
        relation_schema_path=str(relation_path.resolve()),
        extraction_rules_path=str(rules_path.resolve()),
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
        notes=[f"默认 Prompt 来源：{prompt_path.resolve()}"],
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
            f"auto-tuned Prompt 来源：{prompt_path.resolve()}",
        ],
    )


def _summarize_entity_items(entity_items: Sequence[SchemaItem]) -> str:
    lines = []
    for item in entity_items:
        summary = _shorten_text(item.description or ENTITY_TYPE_DESCRIPTION_OVERRIDES.get(item.name, item.label_zh), 44)
        name_rule = _shorten_text(item.canonical_name_rule, 34) if item.canonical_name_rule else "沿用课程内稳定命名"
        lines.append(f"- `{item.name}`（{item.label_zh}）：{summary}；命名规则：{name_rule}")
    return "\n".join(lines)


def _summarize_relation_items(relation_items: Sequence[SchemaItem]) -> str:
    lines = []
    for item in relation_items:
        summary = _shorten_text(item.description, 42)
        hint = _shorten_text(item.extraction_hint, 38) if item.extraction_hint else "按最具体语义关系判断"
        lines.append(f"- `{item.name}`（{item.label_zh}）：{summary}；抽取提示：{hint}")
    return "\n".join(lines)


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
Format each entity as ("entity"{{tuple_delimiter}}<entity_name>{{tuple_delimiter}}<entity_type>{{tuple_delimiter}}<entity_description>)

2. 在已识别实体之间识别明确且稳定的关系。对每条关系输出：
- source_entity: 源实体名称
- target_entity: 目标实体名称
- relationship_description: 必须以 [type=<relation_type>] 开头，其中 <relation_type> 必须来自 [{_relation_names(schema_catalog)}]；
  其后用中文解释关系证据与语义，避免空泛描述
- relationship_strength: 1 到 10 的整数分值，表示关系强度
Format each relationship as ("relationship"{{tuple_delimiter}}<source_entity>{{tuple_delimiter}}<target_entity>{{tuple_delimiter}}<relationship_description>{{tuple_delimiter}}<relationship_strength>)

3. 返回单一列表，实体和关系统一使用 {{record_delimiter}} 分隔，并在结束时输出 {{completion_delimiter}}。

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

    prompt = _replace_last_entity_types(base_prompt_text.strip(), _entity_names(schema_catalog))
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
- 如果文本仅提供位置出现信息，优先考虑是否存在更强的结构/应用/考核关系。
- 若输入文本中带有 course_id、document_type、chapter、section、heading_path、page_start、page_end 等字段，应将其作为判断上下文，而不是原样重复输出。
{_base_prompt_note(base_label)}
""",
    )
    return prompt.rstrip() + "\n"


def _format_input_block(record: Dict[str, Any], text_key: str = "text") -> str:
    lines: List[str] = []
    for key in ("course_id", "document_type", "source_file", "chapter", "section", "page_start", "page_end"):
        value = record.get(key)
        if value is None or value == "":
            continue
        lines.append(f"{key}: {value}.")
    lines.append("text:")
    lines.append(_clean_string(record.get(text_key)))
    return "\n".join(lines).strip()


def _build_entity_description(entity: Dict[str, Any], record: Dict[str, Any]) -> str:
    entity_type = _clean_string(entity.get("type"))
    name = _clean_string(entity.get("name")) or _clean_string(entity.get("normalized_name")) or "未命名实体"
    section = _clean_string(record.get("section"))
    chapter = _clean_string(record.get("chapter"))
    anchor = section or chapter or "当前课程片段"
    base = ENTITY_TYPE_DESCRIPTION_OVERRIDES.get(entity_type, f"课程中的 {entity_type}")
    return f"{base}“{name}”，出现在 {anchor} 中。"


def _format_fewshot_output(record: Dict[str, Any]) -> str:
    entities = [item for item in record.get("gold_entities", []) if isinstance(item, dict)]
    relations = [item for item in record.get("gold_relations", []) if isinstance(item, dict)]
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
            f"(\"entity\"{{tuple_delimiter}}{name}{{tuple_delimiter}}{entity_type}{{tuple_delimiter}}{description})"
        )

    for relation in relations:
        source_name = entity_name_map.get(_clean_string(relation.get("source_entity_id")), "未知源实体")
        target_name = entity_name_map.get(_clean_string(relation.get("target_entity_id")), "未知目标实体")
        relation_type = _clean_string(relation.get("type")) or "related_to"
        evidence = _shorten_text(_clean_string(relation.get("evidence_text")), 110)
        description = f"[type={relation_type}] {evidence or '同一结构单元内存在稳定语义关联'}"
        rendered.append(
            f"(\"relationship\"{{tuple_delimiter}}{source_name}{{tuple_delimiter}}{target_name}{{tuple_delimiter}}{description}{{tuple_delimiter}}8)"
        )

    rendered.append("{completion_delimiter}")
    return "\n{record_delimiter}\n".join(rendered)


def _audit_record_sort_key(record: Dict[str, Any]) -> tuple[int, int, int, str]:
    priority = _clean_string(record.get("audit_priority"))
    priority_rank = {"high": 0, "medium": 1, "low": 2}.get(priority, 3)
    sample_type = _clean_string(record.get("guessed_sample_type"))
    type_rank = FEWSHOT_TYPE_PRIORITY.get(sample_type, 99)
    text_length = int(record.get("text_length") or len(_clean_string(record.get("text"))))
    return (priority_rank, type_rank, abs(text_length - 260), _clean_string(record.get("id")))


def _build_fewshot_example_from_audit(record: Dict[str, Any]) -> FewShotExample:
    return FewShotExample(
        example_id=_clean_string(record.get("id")) or _clean_string(record.get("source_sample_id")) or "audit-example",
        source_kind="audit_gold",
        guessed_sample_type=_clean_string(record.get("guessed_sample_type")) or "unknown",
        source_sample_id=_clean_string(record.get("source_sample_id")) or "",
        input_text=_format_input_block(record),
        output_text=_format_fewshot_output(record),
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
                "(\"entity\"{tuple_delimiter}第二章 进程管理{tuple_delimiter}Chapter{tuple_delimiter}课程结构章节“第二章 进程管理”，出现在 2.1 进程的定义 中。)\n"
                "{record_delimiter}\n"
                "(\"entity\"{tuple_delimiter}进程{tuple_delimiter}Concept{tuple_delimiter}课程概念“进程”，出现在 2.1 进程的定义 中。)\n"
                "{record_delimiter}\n"
                f"(\"relationship\"{{tuple_delimiter}}第二章 进程管理{{tuple_delimiter}}进程{{tuple_delimiter}}[type={contains_name}] 本章介绍进程概念{{tuple_delimiter}}8)\n"
                "{record_delimiter}\n"
                f"(\"relationship\"{{tuple_delimiter}}进程{{tuple_delimiter}}进程定义{{tuple_delimiter}}[type={defined_by_name}] 文本给出了进程的正式定义{{tuple_delimiter}}9)\n"
                "{completion_delimiter}"
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
                "(\"entity\"{tuple_delimiter}实验一 进程调度{tuple_delimiter}Experiment{tuple_delimiter}课程实验任务“实验一 进程调度”，出现在 实验一 进程调度 中。)\n"
                "{record_delimiter}\n"
                "(\"entity\"{tuple_delimiter}时间片轮转调度算法{tuple_delimiter}AlgorithmOrMethod{tuple_delimiter}课程中的方法或算法“时间片轮转调度算法”，出现在 实验一 进程调度 中。)\n"
                "{record_delimiter}\n"
                "(\"entity\"{tuple_delimiter}实验报告{tuple_delimiter}Assignment{tuple_delimiter}课程作业或题组“实验报告”，出现在 实验一 进程调度 中。)\n"
                "{record_delimiter}\n"
                f"(\"relationship\"{{tuple_delimiter}}实验一 进程调度{{tuple_delimiter}}时间片轮转调度算法{{tuple_delimiter}}[type={implemented_by_name}] 实验要求实现该算法{{tuple_delimiter}}9)\n"
                "{record_delimiter}\n"
                f"(\"relationship\"{{tuple_delimiter}}时间片轮转调度算法{{tuple_delimiter}}实验报告{{tuple_delimiter}}[type={evaluated_by_name}] 实验报告用于评估算法实现效果{{tuple_delimiter}}8)\n"
                "{completion_delimiter}"
            ),
            note="手写最小 few-shot 示例：方法/实验/作业型样本。",
        ),
    ]

    return examples[: max(limit, 0)]


def _select_fewshot_examples(
    audit_records: Sequence[Dict[str, Any]],
    fewshot_k: int,
) -> tuple[List[FewShotExample], str, List[str], List[str]]:
    if fewshot_k <= 0:
        return [], "disabled", [], ["fewshot_k <= 0，未生成 few-shot 示例。"]

    usable = [
        record
        for record in audit_records
        if record.get("gold_entities")
        and record.get("gold_relations")
        and _clean_string(record.get("text"))
    ]
    if not usable:
        return [], "missing_audit_gold", [], ["未发现可直接复用的 audit gold 标注，将退回手写最小 few-shot 示例。"]

    ordered = sorted(usable, key=_audit_record_sort_key)
    selected: List[Dict[str, Any]] = []
    selected_ids: set[str] = set()

    def pick_by_types(type_names: set[str]) -> None:
        for record in ordered:
            record_id = _clean_string(record.get("id"))
            sample_type = _clean_string(record.get("guessed_sample_type"))
            if record_id in selected_ids:
                continue
            if sample_type in type_names:
                selected.append(record)
                selected_ids.add(record_id)
                return

    pick_by_types(FEWSHOT_CONCEPT_TYPES)
    pick_by_types(FEWSHOT_ACTIVITY_TYPES)

    for record in ordered:
        if len(selected) >= fewshot_k:
            break
        record_id = _clean_string(record.get("id"))
        if record_id in selected_ids:
            continue
        selected.append(record)
        selected_ids.add(record_id)

    examples = [_build_fewshot_example_from_audit(record) for record in selected[:fewshot_k]]
    source_ids = [example.source_sample_id for example in examples if example.source_sample_id]
    return examples, "audit_gold", source_ids, ["few-shot 示例优先复用了 audit_extraction_set.json 中带 gold 标注的样本。"]


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
) -> Dict[str, Any]:
    return {
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
            "prompt": str((output_dir / candidate_name / "prompt.txt").resolve()),
            "readme": str((output_dir / candidate_name / "README.md").resolve()),
        },
        "notes": list(notes),
    }


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

    schema_aware_base_source = auto_tuned_prompt_source.path or default_prompt_source.path
    schema_aware_base_text = auto_tuned_prompt_source.text or default_prompt_source.text
    schema_aware_base_label = "官方 auto_tuned Prompt" if auto_tuned_prompt_source.text else "默认 GraphRAG Prompt"
    schema_aware_prompt_text = build_schema_aware_prompt(
        schema_catalog=schema_catalog,
        language=language,
        base_prompt_text=schema_aware_base_text,
        base_label=schema_aware_base_label,
    )

    fewshot_examples, fewshot_strategy, fewshot_source_ids, fewshot_notes = _select_fewshot_examples(
        audit_records=audit_records,
        fewshot_k=fewshot_k,
    )
    if fewshot_strategy != "audit_gold":
        manual_examples = _manual_fewshot_examples(schema_catalog, fewshot_k)
        fewshot_examples = manual_examples[:fewshot_k]
    schema_fewshot_prompt_text = build_schema_fewshot_prompt(
        schema_catalog=schema_catalog,
        language=language,
        schema_aware_prompt=schema_aware_prompt_text,
        examples=fewshot_examples,
    )

    auto_tuned_prompt_text = auto_tuned_prompt_source.text
    auto_tuned_source_type = auto_tuned_prompt_source.source_type
    auto_tuned_notes = list(auto_tuned_prompt_source.notes)
    if not auto_tuned_prompt_text:
        auto_tuned_prompt_text = default_prompt_text
        auto_tuned_source_type = "fallback_default_copy"
        auto_tuned_notes.append("由于未发现实际 auto-tuned Prompt，当前候选内容回退为 default 候选 Prompt，以保证目录结构和后续切换流程可运行。")

    output_dir.mkdir(parents=True, exist_ok=True)

    default_candidate_notes = list(default_notes)
    if sample_records:
        default_candidate_notes.append(f"检测到 prompt tuning 样本 {len(sample_records)} 条，可供后续 few-shot/评测使用。")
    default_candidate_notes = _dedupe_preserve_order(default_candidate_notes)

    schema_aware_notes = [
        f"schema_aware 优先基于{schema_aware_base_label}自动增强；若 auto_tuned 缺失则回退到 default。",
        "在基底 Prompt 上显式注入实体类型、关系类型和关键抽取规则摘要。",
        "关系输出仍沿用 GraphRAG tuple 结构，但要求 relationship_description 以 [type=<relation_type>] 开头，便于后续评测解析。",
    ]
    schema_aware_notes = _dedupe_preserve_order(schema_aware_notes)
    schema_fewshot_notes = list(fewshot_notes)
    schema_fewshot_notes.insert(0, f"schema_fewshot 继承 schema_aware，并继续沿用 {schema_aware_base_label} 作为底稿。")
    if fewshot_source_ids:
        schema_fewshot_notes.append(f"few-shot 来源样本：{', '.join(fewshot_source_ids)}")
    if fewshot_examples and fewshot_examples[0].source_kind == "manual_minimal":
        schema_fewshot_notes.append("当前 few-shot 使用手写最小示例，仅作为 audit gold 缺失时的降级方案。")
    schema_fewshot_notes = _dedupe_preserve_order(schema_fewshot_notes)
    auto_tuned_notes = _dedupe_preserve_order(auto_tuned_notes)

    candidates = [
        {
            "name": "default",
            "prompt_text": default_prompt_text,
            "source_type": default_source_type,
            "base_prompt_source": str(default_prompt_source.path.resolve()) if default_prompt_source.path else None,
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
            "base_prompt_source": str(auto_tuned_prompt_source.path.resolve()) if auto_tuned_prompt_source.path else None,
            "schema_used": False,
            "audit_used": False,
            "fewshot_used": False,
            "fewshot_example_count": 0,
            "fewshot_strategy": None,
            "notes": auto_tuned_notes,
        },
        {
            "name": "schema_aware",
            "prompt_text": schema_aware_prompt_text,
            "source_type": "schema_augmented",
            "base_prompt_source": str(schema_aware_base_source.resolve()) if schema_aware_base_source else None,
            "schema_used": True,
            "audit_used": False,
            "fewshot_used": False,
            "fewshot_example_count": 0,
            "fewshot_strategy": None,
            "notes": schema_aware_notes,
        },
        {
            "name": "schema_fewshot",
            "prompt_text": schema_fewshot_prompt_text,
            "source_type": "schema_fewshot",
            "base_prompt_source": str(schema_aware_base_source.resolve()) if schema_aware_base_source else None,
            "schema_used": True,
            "audit_used": bool(audit_records),
            "fewshot_used": bool(fewshot_examples),
            "fewshot_example_count": len(fewshot_examples),
            "fewshot_strategy": "audit_gold" if fewshot_strategy == "audit_gold" else "minimal_manual_examples",
            "notes": schema_fewshot_notes,
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
            notes=candidate["notes"],
            output_dir=output_dir,
            generated_at=generated_at,
        )
        existing_entry = existing_candidate_map.get(candidate["name"], {})
        manifest_candidates.append(_merge_manifest_entry(existing_entry, generated_entry))

    manifest = {
        **{k: v for k, v in existing_manifest.items() if k != "candidates"},
        "task": "candidate_prompt_generation",
        "schema_version": schema_catalog.schema_version,
        "generated_at": generated_at,
        "language": language,
        "output_dir": str(output_dir.resolve()),
        "inputs": {
            "schema_dir": str(schema_dir.resolve()),
            "entity_schema_path": schema_catalog.entity_schema_path,
            "relation_schema_path": schema_catalog.relation_schema_path,
            "extraction_rules_path": schema_catalog.extraction_rules_path,
            "samples_file": str(resolved_samples_file.resolve()) if resolved_samples_file else None,
            "audit_file": str(resolved_audit_file.resolve()) if resolved_audit_file else None,
            "default_prompt_dir": str(default_prompt_dir.resolve()),
            "default_prompt_source": str(default_prompt_source.path.resolve()) if default_prompt_source.path else None,
            "auto_tuned_prompt_dir": str(auto_tuned_prompt_dir.resolve()),
            "auto_tuned_prompt_source": str(auto_tuned_prompt_source.path.resolve()) if auto_tuned_prompt_source.path else None,
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
        },
        "candidate_names": [candidate["candidate_name"] for candidate in manifest_candidates],
        "manifest_path": str(manifest_path.resolve()),
        "notes": [
            "default 候选 Prompt 会优先沿用默认 GraphRAG extract_graph Prompt 文本，再做轻量课程域微调。",
            "schema_aware / schema_fewshot 会优先以 auto_tuned Prompt 为底稿自动增强；若 auto_tuned 缺失则回退到 default。",
            "schema 增强仍通过 relationship_description 的 [type=<relation_type>] 前缀表达关系类型，避免改坏现有 tuple 结构。",
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
        language=args.language,
        overwrite=args.overwrite,
        report_file=Path(args.report_file).resolve() if args.report_file else None,
    )

    print(
        f"[完成] 已生成 4 类候选 Prompt，manifest: {result['manifest_path']}，"
        f"report: {result['report_path']}"
    )
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
