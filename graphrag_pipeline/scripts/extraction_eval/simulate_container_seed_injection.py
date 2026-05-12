#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""Container seed injection 的离线上界模拟。

P0(b) 计划：在 graphrag 输入投影阶段把 Course/Chapter/Section/Experiment/
Assignment 这类结构容器作为 seed 实体注入。真实落地需要改 pdf_ingest 的
`GraphRAGExporter._build_projection_text` 并重跑抽取，成本较高。

本脚本做两件事：
1. 把 audit gold 中"属于容器型的 gold 实体"视作 seed 实体直接注入到每个候选
   的 eval 结果里（不增加任何关系）。
2. 当 `--inject-metadata-contains` 开启时，按 heading_path 派生
   `Course→Chapter / Chapter→Section / Section→Subsection` 的 `contains` 边，
   以及 `Section/Chapter/Course -> 已抽取的知识实体` 的 `contains` 边，模拟
   P2.5（metadata-driven 结构关系补齐）的上界。

注意：
- 这是**上界评估**，不是真实的抽取改动；不要把模拟产物当成可用于索引的
  eval run。唯一用途是回答：容器型实体注入（以及可选的 metadata-driven
  关系派生）后，gold 侧 hit / miss 占比会如何变化。
"""

from __future__ import annotations

import argparse
import json
from pathlib import Path
from typing import Any, Iterable, Sequence

from .extraction_schema import ExtractionEntity, ExtractionRelationship, StructuredExtractionResult
from .scoring_audit import load_audit_index
from .scoring_metrics import _normalize_title


CONTAINER_TYPES: frozenset[str] = frozenset(
    {"Course", "Chapter", "Section", "Experiment", "Assignment"}
)


def simulate_eval_dir(
    *,
    root: Path,
    eval_dir: Path,
    audit_path: Path,
    output_run_id: str,
    container_types: Iterable[str] = CONTAINER_TYPES,
    overwrite: bool = False,
    inject_metadata_contains: bool = False,
) -> dict[str, Any]:
    root = root.resolve()
    eval_dir = eval_dir.resolve()
    audit_path = audit_path.resolve()
    output_dir = root / "results" / "extraction_eval" / "runs" / output_run_id
    report_dir = root / "results" / "reports" / "extraction_missing_relations" / "runs" / output_run_id
    output_dir.mkdir(parents=True, exist_ok=True)
    report_dir.mkdir(parents=True, exist_ok=True)

    container_type_set = frozenset(container_types)
    audit_index = load_audit_index(audit_path)

    eval_files = sorted(p for p in eval_dir.glob("*.json") if p.is_file())
    if not eval_files:
        raise ValueError(f"eval_dir 中没有候选结果文件：{eval_dir}")

    per_candidate_stats: dict[str, Any] = {}
    grand_total_injected = 0
    grand_total_metadata_relationships = 0

    for eval_file in eval_files:
        payload = json.loads(eval_file.read_text(encoding="utf-8"))
        candidate = str(payload.get("candidate") or eval_file.stem).strip()

        target_file = output_dir / eval_file.name
        if target_file.exists() and not overwrite:
            raise FileExistsError(
                f"目标文件已存在，若要覆盖请传 --overwrite：{target_file}"
            )

        simulated_results: list[StructuredExtractionResult] = []
        injected_count = 0
        injected_metadata_relationships = 0
        for raw in payload.get("results") or []:
            result = StructuredExtractionResult(
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
            entry = audit_index.get(result.sample_id)
            if entry is None or not entry.gold_entities or result.status != "success":
                simulated_results.append(result)
                continue

            existing_titles = {
                _normalize_title(entity.title)
                for entity in result.entities
            }
            added_entities: list[ExtractionEntity] = []
            for gold in entry.gold_entities:
                gold_type = str(gold.get("type") or "")
                if gold_type not in container_type_set:
                    continue
                name = str(gold.get("name") or "").strip()
                if not name:
                    continue
                if _normalize_title(name) in existing_titles:
                    continue
                added_entities.append(
                    ExtractionEntity(
                        id=f"sim-seed-{len(result.entities) + len(added_entities) + 1}",
                        title=name,
                        type=gold_type,
                        alias=list(gold.get("alias") or []),
                        definition_text="",
                        description="simulate_container_seed_injection 注入的容器型 seed 实体。",
                        evidence=str(gold.get("span_text") or name),
                    )
                )
                existing_titles.add(_normalize_title(name))

            injected_count += len(added_entities)
            updated_entities: list[ExtractionEntity] = [*result.entities, *added_entities]
            updated_relationships: list[ExtractionRelationship] = list(result.relationships)
            metadata_rel_added = 0
            if inject_metadata_contains:
                metadata_rel_added = _inject_metadata_contains(
                    updated_entities,
                    updated_relationships,
                    container_type_set=container_type_set,
                )
            injected_metadata_relationships += metadata_rel_added
            simulated_results.append(
                result.model_copy(
                    update={
                        "entities": updated_entities,
                        "relationships": updated_relationships,
                    }
                )
            )

        grand_total_injected += injected_count
        grand_total_metadata_relationships += injected_metadata_relationships
        per_candidate_stats[candidate] = {
            "injected_entity_count": injected_count,
            "injected_metadata_relationship_count": injected_metadata_relationships,
            "original_sample_count": len(payload.get("results") or []),
            "source_eval_file": str(eval_file),
        }
        output_payload = dict(payload)
        output_payload["run_id"] = output_run_id
        output_payload["task"] = "candidate_extraction_simulated_seed_injection"
        output_payload["simulation"] = {
            "strategy": "inject_container_gold_entities",
            "container_types": sorted(container_type_set),
            "audit_path": str(audit_path),
            "source_eval_file": str(eval_file),
            "inject_metadata_contains": bool(inject_metadata_contains),
            "injected_entity_count": injected_count,
            "injected_metadata_relationship_count": injected_metadata_relationships,
        }
        output_payload["results"] = [
            result.model_dump(mode="json") for result in simulated_results
        ]
        target_file.write_text(
            json.dumps(output_payload, ensure_ascii=False, indent=2) + "\n",
            encoding="utf-8",
        )

    summary = {
        "task": "simulate_container_seed_injection",
        "run_id": output_run_id,
        "source_eval_dir": str(eval_dir),
        "audit_path": str(audit_path),
        "output_eval_dir": str(output_dir),
        "container_types": sorted(container_type_set),
        "inject_metadata_contains": bool(inject_metadata_contains),
        "candidate_count": len(per_candidate_stats),
        "total_injected_entity_count": grand_total_injected,
        "total_injected_metadata_relationship_count": grand_total_metadata_relationships,
        "per_candidate": per_candidate_stats,
    }
    (report_dir / "simulation_summary.json").write_text(
        json.dumps(summary, ensure_ascii=False, indent=2) + "\n",
        encoding="utf-8",
    )
    return summary


_CONTAINER_RANK: dict[str, int] = {
    "Course": 0,
    "Chapter": 1,
    "Section": 2,
}
_KNOWLEDGE_CONTAINED_TYPES: frozenset[str] = frozenset(
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


def _inject_metadata_contains(
    entities: list[ExtractionEntity],
    relationships: list[ExtractionRelationship],
    *,
    container_type_set: frozenset[str],
) -> int:
    """按样本内的结构容器层级派生 `contains` 边。

    - Course / Chapter / Section 按 _CONTAINER_RANK 从外到内串联。
    - 最内层的 Chapter 或 Section 向样本内已有知识型实体补 `contains`。
    - 只新增不存在的 `contains` 边；不改端点类型、不改方向。
    """

    title_to_entity: dict[str, ExtractionEntity] = {
        _normalize_title(entity.title): entity
        for entity in entities
    }
    containers = [
        entity
        for entity in entities
        if entity.type in _CONTAINER_RANK
    ]
    containers.sort(key=lambda entity: _CONTAINER_RANK.get(entity.type, 99))

    existing_contains: set[tuple[str, str]] = {
        (
            _normalize_title(rel.source),
            _normalize_title(rel.target),
        )
        for rel in relationships
        if rel.type == "contains"
    }

    added = 0

    for outer, inner in zip(containers, containers[1:]):
        if _CONTAINER_RANK[outer.type] >= _CONTAINER_RANK[inner.type]:
            continue
        key = (_normalize_title(outer.title), _normalize_title(inner.title))
        if key in existing_contains:
            continue
        relationships.append(
            ExtractionRelationship(
                source=outer.title,
                target=inner.title,
                type="contains",
                description="simulate_container_seed_injection 派生的 contains 边。",
                evidence="",
            )
        )
        existing_contains.add(key)
        added += 1

    innermost = containers[-1] if containers else None
    if innermost is not None:
        knowledge_entities = [
            entity
            for entity in entities
            if entity.type in _KNOWLEDGE_CONTAINED_TYPES
        ]
        for knowledge in knowledge_entities:
            key = (_normalize_title(innermost.title), _normalize_title(knowledge.title))
            if key in existing_contains:
                continue
            # Experiment / Assignment 原则上也是 contains 的合法 target，但
            # 它们在 schema 中更多是 contains 的 source；为了保守起见，不再
            # 派生 Experiment/Assignment 的入向 contains，只允许 Course/Chapter/Section
            # 作为派生 contains 的 source，避免 Experiment -> Concept 这类
            # 端点不合法的误派生。
            if innermost.type not in {"Course", "Chapter", "Section"}:
                continue
            relationships.append(
                ExtractionRelationship(
                    source=innermost.title,
                    target=knowledge.title,
                    type="contains",
                    description="simulate_container_seed_injection 派生的 contains 边。",
                    evidence="",
                )
            )
            existing_contains.add(key)
            added += 1

    return added


def _build_parser() -> argparse.ArgumentParser:
    parser = argparse.ArgumentParser(
        description="对一个 eval run 做容器型 seed 实体注入的离线上界模拟"
    )
    parser.add_argument(
        "--root",
        default=str(Path(__file__).resolve().parents[2]),
        help="GraphRAG 模块根目录",
    )
    parser.add_argument("--eval-dir", required=True, help="输入 eval run 目录")
    parser.add_argument(
        "--audit",
        default="data/eval/material_7_audit_extraction_set.json",
        help="审计 gold 集 JSON 路径",
    )
    parser.add_argument(
        "--output-run-id",
        required=True,
        help="模拟产出的 eval run id（写到 results/extraction_eval/runs/）",
    )
    parser.add_argument(
        "--container-types",
        nargs="*",
        default=None,
        help="覆盖默认容器型集合（默认 Course/Chapter/Section/Experiment/Assignment）",
    )
    parser.add_argument("--overwrite", action="store_true", help="允许覆盖已有产物")
    parser.add_argument(
        "--inject-metadata-contains",
        action="store_true",
        help=(
            "在 seed 注入基础上追加 metadata-driven contains 派生（Course→Chapter→Section"
            "→已抽取的知识实体），用于估算 P2.5 的上界。"
        ),
    )
    return parser


def main(argv: Sequence[str] | None = None) -> int:
    args = _build_parser().parse_args(argv)
    root = Path(args.root).resolve()
    eval_dir = Path(args.eval_dir)
    if not eval_dir.is_absolute():
        eval_dir = (root / eval_dir).resolve()
    audit_path = Path(args.audit)
    if not audit_path.is_absolute():
        audit_path = (root / audit_path).resolve()
    summary = simulate_eval_dir(
        root=root,
        eval_dir=eval_dir,
        audit_path=audit_path,
        output_run_id=args.output_run_id,
        container_types=args.container_types or CONTAINER_TYPES,
        overwrite=args.overwrite,
        inject_metadata_contains=args.inject_metadata_contains,
    )
    print(json.dumps(
        {
            "status": "success",
            "run_id": summary["run_id"],
            "total_injected_entity_count": summary["total_injected_entity_count"],
            "total_injected_metadata_relationship_count": summary[
                "total_injected_metadata_relationship_count"
            ],
            "candidate_count": summary["candidate_count"],
        },
        ensure_ascii=False,
        indent=2,
    ))
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
