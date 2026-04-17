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

from extraction_schema import SchemaCatalog
from prompt_loader import CandidatePrompt


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
) -> list[dict[str, str]]:
    """构造 OpenAI 兼容 chat.completions 所需消息。"""

    rendered_candidate_prompt = _render_candidate_prompt(candidate.prompt_text, sample)
    json_schema_example = _build_json_schema_example(schema_catalog)

    system_message = (
        "你是课程知识图谱抽取器。"
        "请优先遵循用户消息中的候选 Prompt 抽取策略，但最终只能输出一个合法 JSON 对象。"
        "不要输出 Markdown、解释、分析过程或额外前后缀。"
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

输出 JSON 结构示例：
```json
{json_schema_example}
```

实体类型摘要：
{schema_catalog.render_entity_type_summary()}

关系类型摘要：
{schema_catalog.render_relation_type_summary()}

候选 Prompt（已注入当前样本文本）：
----- CANDIDATE PROMPT START -----
{rendered_candidate_prompt}
----- CANDIDATE PROMPT END -----
"""

    return [
        {"role": "system", "content": system_message},
        {"role": "user", "content": user_message},
    ]


def _render_candidate_prompt(template: str, sample: dict[str, Any]) -> str:
    rendered = template
    for source, target in PLACEHOLDER_REPLACEMENTS.items():
        rendered = rendered.replace(source, target)
    rendered = rendered.replace("{input_text}", _format_sample_context(sample))
    return rendered


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
