#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
Prompt 渲染器
===========
负责把候选 Prompt、样本上下文与统一 JSON 约束拼成最终请求消息。
"""

from __future__ import annotations

import json
from typing import Any

from .extraction_schema import SchemaCatalog
from .prompt_loader import CandidatePrompt


PLACEHOLDER_REPLACEMENTS = {
    "{tuple_delimiter}": "<|>",
    "{record_delimiter}": "##",
    "{completion_delimiter}": "<|COMPLETE|>",
}


def render_extraction_messages(
    *,
    candidate: CandidatePrompt,
    sample: dict[str, Any],
    schema_catalog: SchemaCatalog,
    max_entities: int | None = None,
    max_relationships: int | None = None,
    candidate_view_mode: str = "compact",
    max_prompt_chars: int = 1800,
) -> list[dict[str, str]]:
    """构造 OpenAI 兼容 chat.completions 所需消息。"""

    rendered_candidate_prompt = _render_candidate_prompt(
        candidate.prompt_text,
        sample,
        candidate_view_mode=candidate_view_mode,
        max_prompt_chars=max_prompt_chars,
    )
    json_schema_example = _build_json_schema_example(schema_catalog)

    system_message = (
        "你是课程知识图谱抽取器。"
        "请优先遵循用户消息中的候选 Prompt 抽取策略，但最终只能输出一个合法 JSON 对象。"
        "不要输出 Markdown、解释、分析过程或额外前后缀。"
    )

    output_budget_hint = _build_output_budget_hint(
        max_entities=max_entities,
        max_relationships=max_relationships,
    )
    scope_hint = (
        "不要把课程名、资料名、章标题、节标题当作实体批量输出；"
        "只有当原文正在解释这些结构本身时才保留。"
    )

    user_message = f"""你将收到一份候选 Prompt、课程抽取 schema 和一个课程样本文本。

执行要求：
1. 候选 Prompt 用于保留当前候选版本的抽取风格与策略差异。
2. 但最终答案必须只输出合法 JSON，且根对象只能包含 `entities` 与 `relationships` 两个字段。
3. `entities[].type` 必须来自以下实体类型：{", ".join(schema_catalog.entity_type_names)}
4. `relationships[].type` 必须来自以下关系类型：{", ".join(schema_catalog.relation_type_names)}
5. `entities[].evidence` 与 `relationships[].evidence` 必须引用输入文本中的直接证据或短文本片段。
6. `relationships[].source` 和 `relationships[].target` 必须引用实体标题，而不是随机 ID。
7. 如果没有足够证据，可以返回空数组，但不要编造。
8. 若候选 Prompt 中仍出现 tuple/record delimiter 说明，仅把它视为候选策略参考，不要真的输出 tuple。
9. 只输出合法 JSON，不要输出 ```json code fence。
10. {scope_hint}
11. alias 用于别名、缩写、英文全称或同义表达，不作为关系端点类型。
12. 普通概念解释可写 definition_text；不足以提升为 FormulaOrDefinition 时不要强行新增实体。
13. {output_budget_hint}

输出 JSON 结构示例：
```json
{json_schema_example}
```

实体类型摘要：
{schema_catalog.render_entity_type_summary()}

关系类型摘要：
{schema_catalog.render_relation_type_summary()}

候选 Prompt 策略摘要：
----- CANDIDATE PROMPT START -----
{rendered_candidate_prompt}
----- CANDIDATE PROMPT END -----

样本文本上下文：
----- SAMPLE START -----
{_format_sample_context(sample)}
----- SAMPLE END -----
"""

    return [
        {"role": "system", "content": system_message},
        {"role": "user", "content": user_message},
    ]


def _render_candidate_prompt(
    template: str,
    sample: dict[str, Any],
    *,
    candidate_view_mode: str,
    max_prompt_chars: int,
) -> str:
    rendered = template
    for source, target in PLACEHOLDER_REPLACEMENTS.items():
        rendered = rendered.replace(source, target)
    if candidate_view_mode == "full":
        return rendered.replace("{input_text}", _format_sample_context(sample))
    compact = _compact_candidate_prompt(rendered)
    return _truncate_text(compact, max_prompt_chars)


def _compact_candidate_prompt(text: str) -> str:
    """压缩候选 Prompt，只保留策略，不携带长示例和样本文本。"""

    compact = text.replace("{input_text}", "[样本文本见 SAMPLE 区块]")
    real_data_index = compact.lower().find("-real data-")
    if real_data_index >= 0:
        compact = compact[:real_data_index]

    compact = _remove_legacy_examples(compact)

    lines: list[str] = []
    for raw_line in compact.splitlines():
        stripped = raw_line.strip()
        if not stripped:
            if lines and lines[-1]:
                lines.append("")
            continue
        if stripped.startswith("################"):
            continue
        lines.append(raw_line.rstrip())
    return "\n".join(lines).strip()


def _remove_legacy_examples(text: str) -> str:
    """只移除旧通用示例，保留 schema 与压缩 few-shot 策略。"""

    protected_indexes = [
        index
        for index in (
            text.find("\n-Schema Constraints-"),
            text.find("\n-Few-shot 示例-"),
            text.find("\n-Course Baseline Constraints-"),
        )
        if index >= 0
    ]
    protected_start = min(protected_indexes) if protected_indexes else len(text)
    start_candidates = [
        index
        for index in (
            text.find("\n-Examples-"),
            text.find("\nExamples:"),
            re_search_example_start(text),
        )
        if 0 <= index < protected_start
    ]
    if not start_candidates:
        return text

    start = min(start_candidates)
    end_candidates = [index for index in protected_indexes if index > start]
    end = min(end_candidates) if end_candidates else len(text)
    return f"{text[:start].rstrip()}\n\n{text[end:].lstrip()}"


def re_search_example_start(text: str) -> int:
    for marker in ("\nExample 1", "\nExample One", "\n示例 1", "\n示例一"):
        index = text.find(marker)
        if index >= 0:
            return index
    return -1


def _truncate_text(text: str, max_chars: int) -> str:
    if max_chars <= 0 or len(text) <= max_chars:
        return text
    return f"{text[: max_chars - 1].rstrip()}…"


def _format_sample_context(sample: dict[str, Any]) -> str:
    heading_path = sample.get("heading_path")
    if isinstance(heading_path, list):
        heading_path_text = " > ".join(str(item).strip() for item in heading_path if str(item).strip())
    else:
        heading_path_text = str(sample.get("heading_path_text") or "").strip()

    lines = [
        f"sample_id: {sample.get('sample_id', '')}.",
        f"course_id: {sample.get('course_id', '')}.",
        f"document_type: {sample.get('document_type', '')}.",
        f"source_file: {sample.get('source_file', '')}.",
        f"chapter: {sample.get('chapter', '')}.",
        f"section: {sample.get('section', '')}.",
        f"heading_path: {heading_path_text}.",
        f"page_start: {sample.get('page_start', '')}.",
        f"page_end: {sample.get('page_end', '')}.",
        "text:",
        str(sample.get("text") or "").strip(),
    ]
    return "\n".join(lines)


def _build_json_schema_example(schema_catalog: SchemaCatalog) -> str:
    example = {
        "entities": [
            {
                "id": "entity-1",
                "title": "实体标题",
                "type": schema_catalog.entity_type_names[0] if schema_catalog.entity_type_names else "Concept",
                "alias": ["别名或缩写"],
                "definition_text": "普通概念解释或定义性原文",
                "description": "实体的稳定描述",
                "evidence": "来自原文的直接证据",
            }
        ],
        "relationships": [
            {
                "source": "实体标题 A",
                "target": "实体标题 B",
                "type": schema_catalog.relation_type_names[0] if schema_catalog.relation_type_names else "related_to",
                "description": "关系的简要说明",
                "evidence": "来自原文的直接证据",
            }
        ],
    }
    return json.dumps(example, ensure_ascii=False, indent=2)


def _build_output_budget_hint(*, max_entities: int | None, max_relationships: int | None) -> str:
    if max_entities and max_relationships:
        return (
            f"请只保留最核心的实体与关系，最多输出 {max_entities} 个实体、{max_relationships} 条关系。"
            "优先原文直接解释的知识点、机制、方法和约束，忽略目录层级和穷举型条目。"
        )
    return "请只保留最核心的实体与关系，避免为目录层级或枚举型条目生成过多结果。"
