#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""规则化自动评测 CLI（步骤 8）。

流程：
1. 发现 results/extraction_eval/*.json。
2. 载入 entity/relation schema 与 audit 集（可选）。
3. 对每个候选：parse 结果 → 聚合 10 项指标 → composite_score。
4. 排序 + top-k → 写 csv/md/json 报告。
"""

from __future__ import annotations

import argparse
import json
from pathlib import Path
from typing import Any, Sequence

from extraction_schema import (
    ExtractionEntity,
    ExtractionRelationship,
    StructuredExtractionResult,
)
from scoring_audit import (
    compute_audit_entity_recall,
    compute_audit_relation_recall,
    load_audit_index,
)
from scoring_metrics import (
    DEFAULT_WEIGHTS,
    aggregate_candidate_metrics,
    compute_composite_score,
    rank_candidates,
    select_top_k,
)
from scoring_report import (
    write_extraction_compare_csv,
    write_extraction_compare_markdown,
    write_top_candidates_json,
)


PROJECT_ROOT = Path(__file__).resolve().parents[1]

DEFAULT_EVAL_DIR = "results/extraction_eval"
DEFAULT_ENTITY_SCHEMA = "config/schema/entity_types.json"
DEFAULT_RELATION_SCHEMA = "config/schema/relation_types.json"
DEFAULT_AUDIT_PATH = "data/eval/audit_extraction_set.json"
DEFAULT_REPORTS_DIR = "results/reports"


def _resolve(path: str | Path | None, *, root: Path, default: str | None) -> Path | None:
    target = path if path is not None else default
    if target is None:
        return None
    candidate = Path(target)
    return candidate if candidate.is_absolute() else (root / candidate).resolve()


def _load_eval_file(path: Path) -> tuple[str, list[StructuredExtractionResult]]:
    payload = json.loads(path.read_text(encoding="utf-8"))
    candidate = str(payload.get("candidate") or path.stem).strip()
    raw_results = payload.get("results") or []
    results: list[StructuredExtractionResult] = []
    for raw in raw_results:
        results.append(
            StructuredExtractionResult(
                sample_id=str(raw.get("sample_id") or ""),
                candidate=candidate,
                status=str(raw.get("status") or "parse_error"),
                entities=[ExtractionEntity(**e) for e in raw.get("entities") or []],
                relationships=[
                    ExtractionRelationship(**r) for r in raw.get("relationships") or []
                ],
                raw_output=str(raw.get("raw_output") or ""),
                error=raw.get("error"),
                parser_error_code=raw.get("parser_error_code"),
                llm_debug=raw.get("llm_debug"),
            )
        )
    return candidate, results


def score_extraction_results(
    *,
    root: Path,
    eval_dir: str | Path | None,
    entity_schema_path: str | Path | None,
    relation_schema_path: str | Path | None,
    audit_path: str | Path | None,
    weights: dict[str, float] | None,
    top_k: int,
    overwrite: bool,
) -> dict[str, Any]:
    root = Path(root).resolve()
    eval_root = _resolve(eval_dir, root=root, default=DEFAULT_EVAL_DIR)
    entity_schema_file = _resolve(
        entity_schema_path, root=root, default=DEFAULT_ENTITY_SCHEMA
    )
    relation_schema_file = _resolve(
        relation_schema_path, root=root, default=DEFAULT_RELATION_SCHEMA
    )
    audit_file = _resolve(audit_path, root=root, default=DEFAULT_AUDIT_PATH)

    if eval_root is None or not eval_root.exists():
        raise FileNotFoundError(f"未找到评测输入目录：{eval_root}")
    eval_files = sorted(p for p in eval_root.glob("*.json") if p.is_file())
    if not eval_files:
        raise ValueError(f"评测输入目录无 JSON 文件：{eval_root}")

    if entity_schema_file is None or not entity_schema_file.exists():
        raise FileNotFoundError(f"实体 schema 不存在：{entity_schema_file}")
    if relation_schema_file is None or not relation_schema_file.exists():
        raise FileNotFoundError(f"关系 schema 不存在：{relation_schema_file}")

    entity_payload = json.loads(entity_schema_file.read_text(encoding="utf-8"))
    relation_payload = json.loads(relation_schema_file.read_text(encoding="utf-8"))
    entity_type_names = list((entity_payload.get("entity_types") or {}).keys())
    relation_type_block = relation_payload.get("relation_types") or {}
    relation_type_names = list(relation_type_block.keys())

    audit_index = {}
    if audit_file and audit_file.exists():
        audit_index = load_audit_index(audit_file)

    effective_weights = dict(weights or DEFAULT_WEIGHTS)

    metrics_by_candidate: dict[str, dict[str, Any]] = {}
    for eval_file in eval_files:
        candidate, results = _load_eval_file(eval_file)
        audit_ent = (
            compute_audit_entity_recall(results, audit_index) if audit_index else None
        )
        audit_rel = (
            compute_audit_relation_recall(results, audit_index) if audit_index else None
        )
        metrics = aggregate_candidate_metrics(
            results,
            entity_type_names=entity_type_names,
            relation_type_names=relation_type_names,
            relation_schema=relation_type_block,
            audit_entity_recall=audit_ent,
            audit_relation_recall=audit_rel,
        )
        metrics["composite_score"] = compute_composite_score(metrics, effective_weights)
        metrics_by_candidate[candidate] = metrics

    ranked = rank_candidates(metrics_by_candidate)
    top = select_top_k(ranked, k=top_k)

    reports_dir = root / DEFAULT_REPORTS_DIR
    csv_path = reports_dir / "extraction_compare.csv"
    md_path = reports_dir / "extraction_compare.md"
    top_path = reports_dir / "top_candidates.json"
    for path in (csv_path, md_path, top_path):
        if path.exists() and not overwrite:
            raise FileExistsError(f"目标产物已存在，若要覆盖请传 --overwrite：{path}")

    write_extraction_compare_csv(csv_path, ranked)
    write_extraction_compare_markdown(
        md_path, ranked, weights=effective_weights, top_k=top_k
    )
    write_top_candidates_json(
        top_path,
        ranked=ranked,
        k=top_k,
        weights=effective_weights,
        inputs={
            "eval_dir": str(eval_root),
            "entity_schema_path": str(entity_schema_file),
            "relation_schema_path": str(relation_schema_file),
            "audit_path": str(audit_file) if audit_file and audit_file.exists() else None,
            "eval_files": [str(p) for p in eval_files],
        },
    )

    return {
        "status": "success",
        "root": str(root),
        "eval_files": [str(p) for p in eval_files],
        "total_candidates": len(ranked),
        "top_k": top_k,
        "top_candidates": [item["candidate"] for item in top],
        "reports": {
            "csv": str(csv_path),
            "markdown": str(md_path),
            "top_candidates_json": str(top_path),
        },
    }


def _build_parser() -> argparse.ArgumentParser:
    parser = argparse.ArgumentParser(
        description="对候选 Prompt 抽取结果做规则化自动评测并输出对比报告"
    )
    parser.add_argument("--eval-dir", help="候选结果目录，默认 results/extraction_eval")
    parser.add_argument("--entity-schema", help="实体 schema JSON 路径")
    parser.add_argument("--relation-schema", help="关系 schema JSON 路径")
    parser.add_argument("--audit", help="audit 集 JSON 路径；不传则软指标为 None")
    parser.add_argument("--weights", help="权重覆盖文件（JSON）")
    parser.add_argument("--top-k", type=int, default=2, help="保留前 K 名候选")
    parser.add_argument("--overwrite", action="store_true", help="覆盖已有报告产物")
    return parser


def main(argv: Sequence[str] | None = None) -> int:
    args = _build_parser().parse_args(argv)
    weights = None
    if args.weights:
        weights = json.loads(Path(args.weights).read_text(encoding="utf-8"))
    summary = score_extraction_results(
        root=PROJECT_ROOT,
        eval_dir=args.eval_dir,
        entity_schema_path=args.entity_schema,
        relation_schema_path=args.relation_schema,
        audit_path=args.audit,
        weights=weights,
        top_k=args.top_k,
        overwrite=args.overwrite,
    )
    print(json.dumps(summary, ensure_ascii=False, indent=2))
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
