#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""抽取关系结构化后处理。

该模块用于诊断“如果引入确定性结构后处理，endpoint gate 能挽回多少”。
默认不参与真实 raw 评分；调用方需要显式把后处理结果写成一个新的 eval run。
"""

from __future__ import annotations

import json
import argparse
from collections import Counter, defaultdict
from dataclasses import dataclass, field
from pathlib import Path
from typing import Any, Iterable, Literal, Sequence

from .extraction_schema import ExtractionEntity, ExtractionRelationship, StructuredExtractionResult
from .scoring_metrics import _normalize_title, analyze_endpoint_validity


@dataclass(frozen=True)
class SampleMetadata:
    """样本级容器元数据。

    字段：
    - sample_id：样本唯一 id；
    - course_id：课程 id（作为 Course 类型实体使用）；
    - chapter：章节标题，若为空则跳过 Chapter seed；
    - section：小节标题，若为空则跳过 Section seed；
    - heading_path：从粗到细的标题路径，用于保留推断细节。
    """

    sample_id: str
    course_id: str = ""
    chapter: str = ""
    section: str = ""
    heading_path: tuple[str, ...] = field(default_factory=tuple)


def _clean(value: Any) -> str:
    if value is None:
        return ""
    text = str(value).strip()
    return text


PostprocessMode = Literal[
    "drop-invalid",
    "strict",
    "strict-closure",
    "strict-metadata-closure",
]
KNOWLEDGE_TAXONOMY_TYPES = {
    "KnowledgePoint",
    "Concept",
    "Term",
    "FormulaOrDefinition",
    "AlgorithmOrMethod",
}
# 仅针对方向强敏感的关系族做端点互换修复；depends_on / prerequisite_of / contains
# 这类语义上 source→target 有明确先后/包含顺序的关系不纳入，避免把语义翻反。
SWAP_ENDPOINTS_RELATION_TYPES = frozenset(
    {
        "defined_by",
        "applied_in",
        "implemented_by",
        "evaluated_by",
    }
)
# metadata-closure 用到的结构容器排序；rank 越小越靠外。
_METADATA_CONTAINER_RANK: dict[str, int] = {
    "Course": 0,
    "Chapter": 1,
    "Section": 2,
}
_METADATA_KNOWLEDGE_CONTAINED_TYPES: frozenset[str] = frozenset(
    {
        "KnowledgePoint",
        "Concept",
        "Term",
        "FormulaOrDefinition",
        "AlgorithmOrMethod",
        "ToolOrPlatform",
        "Experiment",
        "Assignment",
    }
)


SampleMetadataMap = dict[str, "SampleMetadata"]


def postprocess_relationships(
    results: Sequence[StructuredExtractionResult],
    relation_schema: dict[str, Any],
    *,
    mode: PostprocessMode = "strict",
    sample_metadata: SampleMetadataMap | None = None,
) -> tuple[list[StructuredExtractionResult], dict[str, Any]]:
    """对抽取结果执行关系后处理，并返回新结果与聚合诊断。

    `drop-invalid` 只丢弃非法 endpoint 关系；`strict` 会先做安全修复：
    1. 关系端点命中实体 alias 时规范化为实体 title。
    2. 知识对象之间误用 `belongs_to` 表达分类时，翻转为 `contains`。
    3. 仍不合法的关系丢弃并记录原因。
    `strict-closure` 在 strict 基础上，允许把 evidence/description 中明确出现、
    且可从 schema 保守推断类型的缺失端点补成实体。
    `strict-metadata-closure` 在 strict-closure 基础上，先按 sample_metadata
    注入 Course/Chapter/Section 这类容器型 seed 实体，并按 heading_path 派生
    `contains` 边（容器层级相邻以及最内层容器 → 已抽取的知识实体），用于
    兑现"元数据已知结构"的确定性收益，无需依赖模型重跑。
    """

    if mode not in {
        "drop-invalid",
        "strict",
        "strict-closure",
        "strict-metadata-closure",
    }:
        raise ValueError(
            "mode 只能是 drop-invalid、strict、strict-closure 或 strict-metadata-closure"
        )
    if mode == "strict-metadata-closure" and sample_metadata is None:
        raise ValueError("strict-metadata-closure 需要提供 sample_metadata")

    actions: Counter[str] = Counter()
    dropped_by_reason: Counter[str] = Counter()
    original_count = 0
    kept_count = 0
    repaired_count = 0
    created_entity_count = 0
    metadata_injected_entities = 0
    metadata_derived_relationships = 0
    processed: list[StructuredExtractionResult] = []

    for item in results:
        if item.status != "success":
            processed.append(item)
            continue

        entities = list(item.entities)
        extra_relationships: list[ExtractionRelationship] = []
        if mode == "strict-metadata-closure" and sample_metadata is not None:
            metadata = sample_metadata.get(item.sample_id)
            if metadata is not None:
                added_entities, new_relationships, injection_stats = _apply_metadata_container_injection(
                    entities=entities,
                    relationships=item.relationships,
                    metadata=metadata,
                )
                if added_entities:
                    entities.extend(added_entities)
                    metadata_injected_entities += len(added_entities)
                    actions["metadata_inject_container_entity"] += len(added_entities)
                if new_relationships:
                    extra_relationships = new_relationships
                    metadata_derived_relationships += len(new_relationships)
                    actions["metadata_derive_contains_relationship"] += len(new_relationships)
                for reason, count in (injection_stats.get("skipped_reasons") or {}).items():
                    if count:
                        actions[f"metadata_skip_{reason}"] += count

        entity_index = _build_entity_index(entities)
        kept_relationships: list[ExtractionRelationship] = []
        sample_actions: Counter[str] = Counter()
        sample_drops: Counter[str] = Counter()
        sample_created = 0

        # 先把 metadata 派生的 contains 关系计入 kept；这些关系由
        # _apply_metadata_container_injection 保证端点合法，不需要再跑 closure。
        for derived in extra_relationships:
            original_count += 1
            kept_count += 1
            repaired_count += 1
            kept_relationships.append(derived)
            sample_actions["metadata_derive_contains_relationship"] += 1

        for relationship in item.relationships:
            original_count += 1
            current = relationship
            changed = False

            if mode in {"strict", "strict-closure", "strict-metadata-closure"}:
                current, alias_actions = _canonicalize_alias_endpoints(current, entity_index)
                if alias_actions:
                    changed = True
                    actions.update(alias_actions)
                    sample_actions.update(alias_actions)

                converted = _convert_taxonomy_belongs_to(current, item, relation_schema)
                if converted is not current:
                    current = converted
                    changed = True
                    actions["convert_belongs_to_taxonomy_to_contains"] += 1
                    sample_actions["convert_belongs_to_taxonomy_to_contains"] += 1

                swapped = _swap_reversed_endpoints(current, item, relation_schema)
                if swapped is not current:
                    current = swapped
                    changed = True
                    actions["swap_reversed_endpoints"] += 1
                    sample_actions["swap_reversed_endpoints"] += 1

                retyped = _retype_container_appears_in_to_contains(current, item, relation_schema)
                if retyped is not current:
                    current = retyped
                    changed = True
                    actions["retype_container_appears_in_to_contains"] += 1
                    sample_actions["retype_container_appears_in_to_contains"] += 1

            if mode in {"strict-closure", "strict-metadata-closure"}:
                completed = _complete_grounded_missing_endpoint(
                    item=item.model_copy(update={"entities": entities}),
                    relationship=current,
                    relation_schema=relation_schema,
                    next_index=len(entities) + 1,
                )
                if completed is not None:
                    new_entity, action = completed
                    entities.append(new_entity)
                    entity_index = _build_entity_index(entities)
                    created_entity_count += 1
                    sample_created += 1
                    actions[action] += 1
                    sample_actions[action] += 1

            invalid_reason = _first_endpoint_invalid_reason(
                item.model_copy(update={"entities": entities}),
                current,
                relation_schema,
            )
            if invalid_reason is None:
                kept_count += 1
                if changed or sample_created:
                    repaired_count += 1
                kept_relationships.append(current)
                continue

            actions["drop_invalid_relationship"] += 1
            sample_actions["drop_invalid_relationship"] += 1
            dropped_by_reason[invalid_reason] += 1
            sample_drops[invalid_reason] += 1

        debug = dict(item.llm_debug or {})
        debug["relationship_postprocess"] = {
            "mode": mode,
            "actions": dict(sample_actions),
            "dropped_by_reason": dict(sample_drops),
            "created_entity_count": sample_created,
            "metadata_injected_entity_count": sum(
                count for action, count in sample_actions.items()
                if action == "metadata_inject_container_entity"
            ),
            "metadata_derived_relationship_count": sum(
                count for action, count in sample_actions.items()
                if action == "metadata_derive_contains_relationship"
            ),
            "original_entity_count": len(item.entities),
            "postprocessed_entity_count": len(entities),
            "original_relationship_count": len(item.relationships),
            "kept_relationship_count": len(kept_relationships),
            "dropped_relationship_count": sum(sample_drops.values()),
        }
        processed.append(item.model_copy(update={"entities": entities, "relationships": kept_relationships, "llm_debug": debug}))

    return processed, {
        "mode": mode,
        "original_relationship_count": original_count,
        "kept_relationship_count": kept_count,
        "dropped_relationship_count": original_count - kept_count,
        "repaired_relationship_count": repaired_count,
        "created_entity_count": created_entity_count,
        "metadata_injected_entity_count": metadata_injected_entities,
        "metadata_derived_relationship_count": metadata_derived_relationships,
        "actions": dict(actions),
        "dropped_by_reason": dict(dropped_by_reason),
    }


def postprocess_eval_dir(
    *,
    root: Path,
    eval_dir: Path,
    relation_schema: dict[str, Any],
    output_run_id: str,
    mode: PostprocessMode = "strict",
    overwrite: bool = False,
    sample_metadata: SampleMetadataMap | None = None,
) -> dict[str, Any]:
    """读取一个 eval run，写出结构化后处理后的新 eval run。"""

    root = root.resolve()
    eval_dir = eval_dir.resolve()
    output_dir = root / "results" / "extraction_eval" / "runs" / output_run_id
    report_dir = root / "results" / "reports" / "extraction_postprocess" / "runs" / output_run_id
    output_dir.mkdir(parents=True, exist_ok=True)
    report_dir.mkdir(parents=True, exist_ok=True)

    eval_files = sorted(eval_dir.glob("*.json"))
    if not eval_files:
        raise ValueError(f"eval_dir 中没有候选结果文件：{eval_dir}")

    candidate_summaries: dict[str, Any] = {}
    for eval_file in eval_files:
        payload = json.loads(eval_file.read_text(encoding="utf-8"))
        candidate = str(payload.get("candidate") or eval_file.stem).strip()
        target_file = output_dir / eval_file.name
        _ensure_writable(target_file, overwrite=overwrite)

        results = _load_results_from_payload(payload, candidate=candidate)
        processed, summary = postprocess_relationships(
            results,
            relation_schema,
            mode=mode,
            sample_metadata=sample_metadata,
        )
        candidate_summaries[candidate] = summary

        output_payload = dict(payload)
        output_payload["run_id"] = output_run_id
        output_payload["task"] = "candidate_extraction_postprocessed"
        output_payload["postprocess"] = {
            "mode": mode,
            "source_eval_file": str(eval_file),
            "summary": summary,
        }
        output_payload["results"] = [result.model_dump(mode="json") for result in processed]
        output_payload["summary"] = _summarize_results(processed)
        target_file.write_text(json.dumps(output_payload, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")

    summary_payload = {
        "task": "relationship_postprocess",
        "mode": mode,
        "source_eval_dir": str(eval_dir),
        "output_eval_dir": str(output_dir),
        "output_run_id": output_run_id,
        "candidate_summaries": candidate_summaries,
        "totals": _merge_summaries(candidate_summaries.values()),
    }
    (report_dir / "summary.json").write_text(
        json.dumps(summary_payload, ensure_ascii=False, indent=2) + "\n",
        encoding="utf-8",
    )
    _write_summary_markdown(report_dir / "summary.md", summary_payload)
    return summary_payload


def load_relation_schema(path: Path) -> dict[str, Any]:
    payload = json.loads(path.read_text(encoding="utf-8"))
    raw = payload.get("relation_types") if isinstance(payload, dict) else {}
    return raw if isinstance(raw, dict) else {}


def run_postprocess_from_args(argv: Sequence[str] | None = None) -> dict[str, Any]:
    parser = _build_parser()
    args = parser.parse_args(argv)
    root = Path(args.root).resolve()
    eval_dir = Path(args.eval_dir)
    if not eval_dir.is_absolute():
        eval_dir = (root / eval_dir).resolve()
    relation_schema_path = Path(args.relation_schema)
    if not relation_schema_path.is_absolute():
        relation_schema_path = (root / relation_schema_path).resolve()
    relation_schema = load_relation_schema(relation_schema_path)

    sample_metadata: SampleMetadataMap | None = None
    if args.samples_file:
        samples_path = Path(args.samples_file)
        if not samples_path.is_absolute():
            samples_path = (root / samples_path).resolve()
        sample_metadata = load_sample_metadata(samples_path)

    if args.mode == "strict-metadata-closure" and not sample_metadata:
        raise ValueError(
            "strict-metadata-closure 模式必须通过 --samples-file 提供样本元数据"
        )

    return postprocess_eval_dir(
        root=root,
        eval_dir=eval_dir,
        relation_schema=relation_schema,
        output_run_id=args.output_run_id,
        mode=args.mode,
        overwrite=args.overwrite,
        sample_metadata=sample_metadata,
    )


def _build_parser() -> argparse.ArgumentParser:
    parser = argparse.ArgumentParser(description="把候选抽取 eval run 写成结构化关系后处理诊断 run")
    parser.add_argument("--root", default=str(Path(__file__).resolve().parents[2]), help="GraphRAG 模块根目录")
    parser.add_argument("--eval-dir", required=True, help="输入 eval run 目录，例如 results/extraction_eval/runs/<run_id>")
    parser.add_argument(
        "--relation-schema",
        default="config/schema/relation_types.json",
        help="关系 schema JSON 路径",
    )
    parser.add_argument("--output-run-id", required=True, help="输出 eval run id")
    parser.add_argument(
        "--mode",
        choices=[
            "drop-invalid",
            "strict",
            "strict-closure",
            "strict-metadata-closure",
        ],
        default="strict",
        help=(
            "后处理模式：drop-invalid 只丢弃非法关系；"
            "strict 先做安全修复再丢弃；"
            "strict-closure 额外补齐可信缺失端点；"
            "strict-metadata-closure 先按 samples_file 提供的 heading_path 元数据"
            "注入容器 seed 实体并派生 contains 边，再走 strict-closure。"
        ),
    )
    parser.add_argument(
        "--samples-file",
        default=None,
        help=(
            "样本元数据 JSON 路径。strict-metadata-closure 模式必填；"
            "支持 audit_extraction_set.json / prompt_tuning_samples.json "
            "两种结构。"
        ),
    )
    parser.add_argument("--overwrite", action="store_true", help="允许覆盖已存在输出文件")
    return parser


def load_sample_metadata(path: Path) -> SampleMetadataMap:
    """从 audit 集或 prompt tuning 样本集加载 sample_id -> SampleMetadata。

    两种文件结构都被兼容：
    - audit set 用 `audit_samples[*].source_sample_id` 作为 key，字段直接挂在
      顶层（chapter/section/heading_path/course_id）。
    - prompt tuning samples 用 `samples[*].sample_id`；字段结构相同。

    仅保留 Course/Chapter/Section 三类结构容器；Experiment/Assignment 这类
    容器由模型或未来 pdf_ingest 阶段单独给出，这里不伪造。
    """

    payload = json.loads(Path(path).read_text(encoding="utf-8"))
    sample_list: Iterable[dict[str, Any]] = ()
    if isinstance(payload, dict):
        if isinstance(payload.get("audit_samples"), list):
            sample_list = payload["audit_samples"]
        elif isinstance(payload.get("samples"), list):
            sample_list = payload["samples"]
    result: SampleMetadataMap = {}
    for entry in sample_list:
        if not isinstance(entry, dict):
            continue
        sample_id = str(entry.get("source_sample_id") or entry.get("sample_id") or "").strip()
        if not sample_id:
            continue
        course_id = _clean(entry.get("course_id"))
        chapter = _clean(entry.get("chapter"))
        section = _clean(entry.get("section"))
        heading_path_raw = entry.get("heading_path") or []
        heading_path = [_clean(segment) for segment in heading_path_raw if _clean(segment)]
        if not (course_id or chapter or section or heading_path):
            continue
        result[sample_id] = SampleMetadata(
            sample_id=sample_id,
            course_id=course_id,
            chapter=chapter,
            section=section,
            heading_path=tuple(heading_path),
        )
    return result


def main(argv: Sequence[str] | None = None) -> int:
    summary = run_postprocess_from_args(argv)
    print(json.dumps(summary, ensure_ascii=False, indent=2))
    return 0


def _build_entity_index(entities: Sequence[ExtractionEntity]) -> dict[str, str]:
    candidates: dict[str, set[str]] = defaultdict(set)
    for entity in entities:
        candidates[_normalize_title(entity.title)].add(entity.title)
        for alias in entity.alias:
            candidates[_normalize_title(alias)].add(entity.title)
    return {
        key: next(iter(values))
        for key, values in candidates.items()
        if key and len(values) == 1
    }


def _canonicalize_alias_endpoints(
    relationship: ExtractionRelationship,
    entity_index: dict[str, str],
) -> tuple[ExtractionRelationship, Counter[str]]:
    updates: dict[str, str] = {}
    actions: Counter[str] = Counter()
    canonical_source = entity_index.get(_normalize_title(relationship.source))
    if canonical_source and canonical_source != relationship.source:
        updates["source"] = canonical_source
        actions["canonicalize_source_alias"] += 1
    canonical_target = entity_index.get(_normalize_title(relationship.target))
    if canonical_target and canonical_target != relationship.target:
        updates["target"] = canonical_target
        actions["canonicalize_target_alias"] += 1
    if not updates:
        return relationship, actions
    return relationship.model_copy(update=updates), actions


def _convert_taxonomy_belongs_to(
    relationship: ExtractionRelationship,
    item: StructuredExtractionResult,
    relation_schema: dict[str, Any],
) -> ExtractionRelationship:
    if relationship.type != "belongs_to":
        return relationship

    title_to_type = {
        _normalize_title(entity.title): entity.type
        for entity in item.entities
    }
    source_type = title_to_type.get(_normalize_title(relationship.source))
    target_type = title_to_type.get(_normalize_title(relationship.target))
    if source_type not in KNOWLEDGE_TAXONOMY_TYPES or target_type not in KNOWLEDGE_TAXONOMY_TYPES:
        return relationship

    contains_schema = relation_schema.get("contains") or {}
    source_types = set(contains_schema.get("source_types") or [])
    target_types = set(contains_schema.get("target_types") or [])
    if target_type not in source_types or source_type not in target_types:
        return relationship

    return relationship.model_copy(
        update={
            "source": relationship.target,
            "target": relationship.source,
            "type": "contains",
        }
    )


def _swap_reversed_endpoints(
    relationship: ExtractionRelationship,
    item: StructuredExtractionResult,
    relation_schema: dict[str, Any],
) -> ExtractionRelationship:
    """方向敏感关系若端点被写反且反向合法、原方向不合法，则端点互换。

    仅覆盖 SWAP_ENDPOINTS_RELATION_TYPES 中的关系，避免把 contains /
    depends_on / prerequisite_of 这类真正的先后顺序语义也翻掉。
    """

    if relationship.type not in SWAP_ENDPOINTS_RELATION_TYPES:
        return relationship

    constraints = relation_schema.get(relationship.type) or {}
    source_types = set(constraints.get("source_types") or [])
    target_types = set(constraints.get("target_types") or [])
    if not source_types or not target_types:
        return relationship

    title_to_type = {
        _normalize_title(entity.title): entity.type
        for entity in item.entities
    }
    source_type = title_to_type.get(_normalize_title(relationship.source))
    target_type = title_to_type.get(_normalize_title(relationship.target))
    if not source_type or not target_type:
        return relationship

    original_ok = source_type in source_types and target_type in target_types
    swapped_ok = target_type in source_types and source_type in target_types
    if original_ok or not swapped_ok:
        return relationship

    return relationship.model_copy(
        update={
            "source": relationship.target,
            "target": relationship.source,
        }
    )


def _retype_container_appears_in_to_contains(
    relationship: ExtractionRelationship,
    item: StructuredExtractionResult,
    relation_schema: dict[str, Any],
) -> ExtractionRelationship:
    """`Course/Chapter/Section/Experiment/Assignment -> 知识实体` 的 appears_in 改写为 contains。

    schema 明确要求结构容器包含知识对象时走 contains，不能走 appears_in；
    此改写是纯端点类型切换 + 关系类型切换，不互换端点，不会改变 source/target。
    """

    if relationship.type != "appears_in":
        return relationship

    contains_schema = relation_schema.get("contains") or {}
    contains_source_types = set(contains_schema.get("source_types") or [])
    contains_target_types = set(contains_schema.get("target_types") or [])
    if not contains_source_types or not contains_target_types:
        return relationship

    title_to_type = {
        _normalize_title(entity.title): entity.type
        for entity in item.entities
    }
    source_type = title_to_type.get(_normalize_title(relationship.source))
    target_type = title_to_type.get(_normalize_title(relationship.target))
    if not source_type or not target_type:
        return relationship

    container_types = {"Course", "Chapter", "Section", "Experiment", "Assignment"}
    if source_type not in container_types:
        return relationship
    if source_type not in contains_source_types or target_type not in contains_target_types:
        return relationship

    return relationship.model_copy(update={"type": "contains"})


def _complete_grounded_missing_endpoint(
    *,
    item: StructuredExtractionResult,
    relationship: ExtractionRelationship,
    relation_schema: dict[str, Any],
    next_index: int,
) -> tuple[ExtractionEntity, str] | None:
    reason = _first_endpoint_invalid_reason(item, relationship, relation_schema)
    if reason not in {"missing_source", "missing_target"}:
        return None

    role = "source" if reason == "missing_source" else "target"
    title = (relationship.source if role == "source" else relationship.target).strip()
    if not title or not _endpoint_text_is_grounded(title, relationship):
        return None

    inferred_type = _infer_missing_endpoint_type(
        item=item,
        relationship=relationship,
        relation_schema=relation_schema,
        role=role,
    )
    if inferred_type is None:
        return None

    entity = ExtractionEntity(
        id=f"post-entity-{next_index}",
        title=title,
        type=inferred_type,
        alias=[],
        definition_text="",
        description="由关系端点结构化补齐的诊断实体。",
        evidence=relationship.evidence or relationship.description,
    )
    return entity, f"add_missing_{role}_entity"


def _endpoint_text_is_grounded(title: str, relationship: ExtractionRelationship) -> bool:
    needle = _normalize_title(title)
    if not needle:
        return False
    haystack = _normalize_title(" ".join([relationship.description, relationship.evidence]))
    return needle in haystack


def _infer_missing_endpoint_type(
    *,
    item: StructuredExtractionResult,
    relationship: ExtractionRelationship,
    relation_schema: dict[str, Any],
    role: Literal["source", "target"],
) -> str | None:
    constraints = relation_schema.get(relationship.type) or {}
    allowed = list(constraints.get(f"{role}_types") or [])
    if not allowed:
        return None

    title_to_type = {
        _normalize_title(entity.title): entity.type
        for entity in item.entities
    }
    counterpart_title = relationship.target if role == "source" else relationship.source
    counterpart_type = title_to_type.get(_normalize_title(counterpart_title))

    if relationship.type in {"related_to", "depends_on"} and counterpart_type in allowed:
        return counterpart_type

    if relationship.type == "appears_in" and role == "target":
        inferred_location = _infer_location_endpoint_type(relationship.target, allowed)
        if inferred_location:
            return inferred_location
        return None

    preferred_by_relation = {
        ("related_to", "source"): ["Concept", "KnowledgePoint", "AlgorithmOrMethod", "Term"],
        ("related_to", "target"): ["Concept", "KnowledgePoint", "AlgorithmOrMethod", "Term"],
        ("depends_on", "source"): ["Concept", "KnowledgePoint", "AlgorithmOrMethod", "Term"],
        ("depends_on", "target"): ["Concept", "KnowledgePoint", "AlgorithmOrMethod", "Term"],
        ("applied_in", "source"): ["AlgorithmOrMethod", "Concept", "KnowledgePoint", "Term"],
        ("applied_in", "target"): ["Concept", "KnowledgePoint", "Experiment", "Assignment", "ToolOrPlatform", "Section"],
    }
    for candidate in preferred_by_relation.get((relationship.type, role), []):
        if candidate in allowed:
            return candidate

    if len(allowed) == 1:
        return allowed[0]
    return None


def _infer_location_endpoint_type(title: str, allowed: Sequence[str]) -> str | None:
    allowed_set = set(allowed)
    normalized = _normalize_title(title)
    if "Chapter" in allowed_set and normalized.startswith("第") and "章" in normalized:
        return "Chapter"
    if "Section" in allowed_set and normalized.startswith("第") and "节" in normalized:
        return "Section"
    platform_cues = ("linux", "unix", "windows", "msdos", "dos", "os", "posix")
    if "ToolOrPlatform" in allowed_set and any(cue in normalized for cue in platform_cues):
        return "ToolOrPlatform"
    if "Course" in allowed_set and "课程" in normalized:
        return "Course"
    return None


def _first_endpoint_invalid_reason(
    item: StructuredExtractionResult,
    relationship: ExtractionRelationship,
    relation_schema: dict[str, Any],
) -> str | None:
    if relationship.type not in relation_schema:
        return None
    single = item.model_copy(update={"relationships": [relationship]})
    analysis = analyze_endpoint_validity([single], relation_schema)
    if int(analysis["invalid_count"]) == 0:
        return None
    combinations = analysis.get("invalid_combinations") or []
    if combinations:
        return str(combinations[0].get("reason") or "endpoint_invalid")
    return "endpoint_invalid"


def _load_results_from_payload(payload: dict[str, Any], *, candidate: str) -> list[StructuredExtractionResult]:
    results: list[StructuredExtractionResult] = []
    for raw in payload.get("results") or []:
        results.append(
            StructuredExtractionResult(
                sample_id=str(raw.get("sample_id") or ""),
                candidate=str(raw.get("candidate") or candidate),
                status=str(raw.get("status") or "parse_error"),
                entities=[ExtractionEntity(**entity) for entity in raw.get("entities") or []],
                relationships=[
                    ExtractionRelationship(**relationship)
                    for relationship in raw.get("relationships") or []
                ],
                raw_output=str(raw.get("raw_output") or ""),
                error=raw.get("error"),
                parser_error_code=raw.get("parser_error_code"),
                llm_debug=raw.get("llm_debug"),
            )
        )
    return results


def _summarize_results(results: Sequence[StructuredExtractionResult]) -> dict[str, int]:
    summary = {"total": len(results), "success": 0, "parse_error": 0, "llm_error": 0}
    for result in results:
        if result.status in summary:
            summary[result.status] += 1
    return summary


def _merge_summaries(summaries: Iterable[dict[str, Any]]) -> dict[str, Any]:
    totals = Counter()
    dropped_by_reason: Counter[str] = Counter()
    actions: Counter[str] = Counter()
    for summary in summaries:
        totals["original_relationship_count"] += int(summary.get("original_relationship_count") or 0)
        totals["kept_relationship_count"] += int(summary.get("kept_relationship_count") or 0)
        totals["dropped_relationship_count"] += int(summary.get("dropped_relationship_count") or 0)
        totals["repaired_relationship_count"] += int(summary.get("repaired_relationship_count") or 0)
        totals["metadata_injected_entity_count"] += int(summary.get("metadata_injected_entity_count") or 0)
        totals["metadata_derived_relationship_count"] += int(summary.get("metadata_derived_relationship_count") or 0)
        dropped_by_reason.update(summary.get("dropped_by_reason") or {})
        actions.update(summary.get("actions") or {})
    return {
        **dict(totals),
        "actions": dict(actions),
        "dropped_by_reason": dict(dropped_by_reason),
    }


def _ensure_writable(path: Path, *, overwrite: bool) -> None:
    if path.exists() and not overwrite:
        raise FileExistsError(f"目标文件已存在，若要覆盖请传 --overwrite：{path}")


def _write_summary_markdown(path: Path, payload: dict[str, Any]) -> None:
    lines = [
        "# 关系结构化后处理诊断",
        "",
        f"- `mode`：{payload['mode']}",
        f"- `source_eval_dir`：`{payload['source_eval_dir']}`",
        f"- `output_eval_dir`：`{payload['output_eval_dir']}`",
        "",
        "## 候选摘要",
        "",
        "| candidate | original | kept | repaired | dropped | top_actions | top_drop_reasons |",
        "|---|---:|---:|---:|---:|---|---|",
    ]
    for candidate, summary in payload["candidate_summaries"].items():
        lines.append(
            "| "
            + " | ".join(
                [
                    candidate,
                    str(summary.get("original_relationship_count", 0)),
                    str(summary.get("kept_relationship_count", 0)),
                    str(summary.get("repaired_relationship_count", 0)),
                    str(summary.get("dropped_relationship_count", 0)),
                    _format_counter_cell(summary.get("actions") or {}),
                    _format_counter_cell(summary.get("dropped_by_reason") or {}),
                ]
            )
            + " |"
        )
    lines.extend(
        [
            "",
            "## 汇总",
            "",
            "```json",
            json.dumps(payload["totals"], ensure_ascii=False, indent=2),
            "```",
        ]
    )
    path.write_text("\n".join(lines) + "\n", encoding="utf-8")


def _format_counter_cell(values: dict[str, Any]) -> str:
    if not values:
        return "-"
    parts = sorted(values.items(), key=lambda item: (-int(item[1]), item[0]))
    return ", ".join(f"{key}:{value}" for key, value in parts[:4])


def _apply_metadata_container_injection(
    *,
    entities: Sequence[ExtractionEntity],
    relationships: Sequence[ExtractionRelationship],
    metadata: SampleMetadata,
) -> tuple[list[ExtractionEntity], list[ExtractionRelationship], dict[str, Any]]:
    """按样本级 metadata 注入容器 seed 实体 + 派生 contains 边。

    规则：
    - 从 metadata 的 course_id / chapter / section 依次生成 Course / Chapter /
      Section 三种 seed 实体，已存在则跳过；
    - 派生 Course→Chapter / Chapter→Section 相邻层级 contains；
    - 让最内层容器（Section > Chapter > Course）对已有知识型实体（Concept /
      Term / KnowledgePoint / FormulaOrDefinition / AlgorithmOrMethod /
      ToolOrPlatform / Experiment / Assignment）派生 contains；
    - 已存在的相同 (source, target, type="contains") 不会被重复生成；
    - 仅当最内层容器 type ∈ {Course, Chapter, Section} 才派生容器→知识 contains，
      避免引入不合法的 Experiment/Assignment 为 source 的 contains。
    """

    existing_titles: dict[str, ExtractionEntity] = {
        _normalize_title(entity.title): entity for entity in entities
    }
    new_entities: list[ExtractionEntity] = []
    added_containers: list[ExtractionEntity] = []
    stats_skipped: Counter[str] = Counter()

    def _add_container(title: str, container_type: str) -> None:
        normalized = _normalize_title(title)
        if not normalized:
            stats_skipped["empty_title"] += 1
            return
        if normalized in existing_titles:
            existing = existing_titles[normalized]
            if existing.type not in _METADATA_CONTAINER_RANK:
                stats_skipped["existing_entity_type_conflict"] += 1
            added_containers.append(existing)
            return
        seed = ExtractionEntity(
            id=f"metadata-seed-{container_type.lower()}-{len(entities) + len(new_entities) + 1}",
            title=title,
            type=container_type,
            alias=[],
            definition_text="",
            description="metadata-closure 从样本元数据注入的容器 seed 实体。",
            evidence="",
        )
        new_entities.append(seed)
        added_containers.append(seed)
        existing_titles[normalized] = seed

    if metadata.course_id:
        _add_container(metadata.course_id, "Course")
    if metadata.chapter:
        _add_container(metadata.chapter, "Chapter")
    if metadata.section:
        _add_container(metadata.section, "Section")

    # 去重排序
    containers: list[ExtractionEntity] = []
    seen: set[str] = set()
    for entity in added_containers:
        key = _normalize_title(entity.title)
        if key in seen:
            continue
        seen.add(key)
        containers.append(entity)
    containers.sort(key=lambda entity: _METADATA_CONTAINER_RANK.get(entity.type, 99))

    existing_contains: set[tuple[str, str]] = {
        (_normalize_title(rel.source), _normalize_title(rel.target))
        for rel in relationships
        if rel.type == "contains"
    }

    new_relationships: list[ExtractionRelationship] = []

    # 相邻容器层级 contains
    for outer, inner in zip(containers, containers[1:]):
        outer_rank = _METADATA_CONTAINER_RANK.get(outer.type, 99)
        inner_rank = _METADATA_CONTAINER_RANK.get(inner.type, 99)
        if outer_rank >= inner_rank:
            continue
        key = (_normalize_title(outer.title), _normalize_title(inner.title))
        if key in existing_contains:
            continue
        new_relationships.append(
            ExtractionRelationship(
                source=outer.title,
                target=inner.title,
                type="contains",
                description="metadata-closure 按样本 heading_path 派生的 contains 边。",
                evidence="",
            )
        )
        existing_contains.add(key)

    # 最内层容器 → 已抽取的知识型实体 contains
    innermost = containers[-1] if containers else None
    if innermost is not None and innermost.type in _METADATA_CONTAINER_RANK:
        for entity in entities:
            if entity.type not in _METADATA_KNOWLEDGE_CONTAINED_TYPES:
                continue
            key = (_normalize_title(innermost.title), _normalize_title(entity.title))
            if key in existing_contains:
                continue
            new_relationships.append(
                ExtractionRelationship(
                    source=innermost.title,
                    target=entity.title,
                    type="contains",
                    description="metadata-closure 最内层容器对已抽取知识实体的 contains 派生。",
                    evidence="",
                )
            )
            existing_contains.add(key)

    return (
        new_entities,
        new_relationships,
        {
            "skipped_reasons": dict(stats_skipped),
        },
    )
