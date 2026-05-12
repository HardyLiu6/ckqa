#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""Gold 侧缺失关系诊断。

给定一个已经跑完的 eval run（raw 或 structured-closure 都可以），对每个候选
把审计 gold 的每条关系按“为什么没被抽中/抽中了但不合规”六类做归并：

- both_endpoints_missing：gold 的 source/target 实体都没被抽到；
- source_endpoint_missing：只缺 source 端点；
- target_endpoint_missing：只缺 target 端点；
- both_endpoints_present_but_not_connected：两端都抽到了，但没有 (src, type, tgt) 这条边；
- direction_reversed：两端都抽到了，有 (tgt, type, src) 这条反向边；
- wrong_type：两端都抽到了，有非目标 type 的连边；
- hit：正向命中。

CLI 输出：写入 `results/reports/extraction_missing_relations/runs/<run_id>/summary.(json|md)`。
"""

from __future__ import annotations

import argparse
import json
from collections import Counter, defaultdict
from pathlib import Path
from typing import Any, Iterable, Sequence

from .extraction_schema import (
    ExtractionEntity,
    ExtractionRelationship,
    StructuredExtractionResult,
)
from .scoring_audit import (
    ExtCandidate,
    GoldEntity,
    _build_ext_candidates,
    _build_gold_entities,
    align_sample,
    load_audit_index,
)
from .scoring_metrics import _normalize_title


CATEGORY_ORDER: tuple[str, ...] = (
    "hit",
    "direction_reversed",
    "wrong_type",
    "both_endpoints_present_but_not_connected",
    "source_endpoint_missing",
    "target_endpoint_missing",
    "both_endpoints_missing",
)


def classify_gold_relations(
    *,
    results: Sequence[StructuredExtractionResult],
    audit_index: dict[str, Any],
) -> dict[str, Any]:
    """对一个候选的 eval 结果做 gold 关系归并，返回整体/分 relation_type 统计。"""

    totals: Counter[str] = Counter()
    by_relation_type: dict[str, Counter[str]] = defaultdict(Counter)
    samples_detail: list[dict[str, Any]] = []
    misses_detail: list[dict[str, Any]] = []
    covered_samples = 0

    for item in results:
        entry = audit_index.get(item.sample_id)
        if entry is None or not entry.gold_relations:
            continue
        if item.status != "success":
            # 整条 gold 当作端点缺失记
            for gold in entry.gold_relations:
                rtype = str(gold.get("type") or "")
                if not rtype:
                    continue
                totals["both_endpoints_missing"] += 1
                by_relation_type[rtype]["both_endpoints_missing"] += 1
                misses_detail.append(
                    {
                        "sample_id": item.sample_id,
                        "relation_id": str(gold.get("relation_id") or ""),
                        "relation_type": rtype,
                        "category": "both_endpoints_missing",
                        "source_name": _gold_entity_name(entry.gold_entities, gold.get("source_entity_id")),
                        "target_name": _gold_entity_name(entry.gold_entities, gold.get("target_entity_id")),
                        "evidence_text": str(gold.get("evidence_text") or ""),
                        "notes": "extraction status != success",
                    }
                )
            continue

        covered_samples += 1
        gold_entities = _build_gold_entities(entry)
        ext_candidates = _build_ext_candidates(item)
        aligned = align_sample(gold_entities, ext_candidates)

        # 构建 extraction 可查集合
        title_to_idxs = _title_to_idxs(ext_candidates)
        typed_edges: set[tuple[int, str, int]] = set()
        any_edges: dict[tuple[int, int], set[str]] = defaultdict(set)
        for rel in item.relationships:
            src_norm = _normalize_title(rel.source)
            tgt_norm = _normalize_title(rel.target)
            for s in title_to_idxs.get(src_norm, ()):
                for t in title_to_idxs.get(tgt_norm, ()):
                    typed_edges.add((s, rel.type, t))
                    any_edges[(s, t)].add(rel.type)

        sample_counts: Counter[str] = Counter()

        for gold in entry.gold_relations:
            rtype = str(gold.get("type") or "")
            if not rtype:
                continue
            src_id = str(gold.get("source_entity_id") or "")
            tgt_id = str(gold.get("target_entity_id") or "")
            src_align = aligned.get(src_id)
            tgt_align = aligned.get(tgt_id)
            src_idx = src_align.matched_ext_idx if src_align else None
            tgt_idx = tgt_align.matched_ext_idx if tgt_align else None

            category = _categorize(
                rtype=rtype,
                src_idx=src_idx,
                tgt_idx=tgt_idx,
                typed_edges=typed_edges,
                any_edges=any_edges,
            )
            totals[category] += 1
            by_relation_type[rtype][category] += 1
            sample_counts[category] += 1
            if category != "hit":
                misses_detail.append(
                    {
                        "sample_id": item.sample_id,
                        "relation_id": str(gold.get("relation_id") or ""),
                        "relation_type": rtype,
                        "category": category,
                        "source_name": _gold_entity_name(entry.gold_entities, src_id),
                        "target_name": _gold_entity_name(entry.gold_entities, tgt_id),
                        "source_aligned": bool(src_idx is not None),
                        "target_aligned": bool(tgt_idx is not None),
                        "evidence_text": str(gold.get("evidence_text") or ""),
                    }
                )

        samples_detail.append(
            {
                "sample_id": item.sample_id,
                "gold_relation_count": len(entry.gold_relations),
                "category_counts": dict(sample_counts),
            }
        )

    by_relation_type_payload = {
        rtype: {
            "total": sum(counter.values()),
            "counts": {cat: counter.get(cat, 0) for cat in CATEGORY_ORDER},
            "hit_rate": _safe_rate(counter.get("hit", 0), sum(counter.values())),
        }
        for rtype, counter in sorted(by_relation_type.items())
    }
    total = sum(totals.values())
    return {
        "covered_sample_count": covered_samples,
        "totals": {
            "total": total,
            "counts": {cat: totals.get(cat, 0) for cat in CATEGORY_ORDER},
            "rates": {
                cat: _safe_rate(totals.get(cat, 0), total)
                for cat in CATEGORY_ORDER
            },
        },
        "by_relation_type": by_relation_type_payload,
        "samples": samples_detail,
        "misses": misses_detail,
    }


def diagnose_eval_dir(
    *,
    root: Path,
    eval_dir: Path,
    audit_path: Path,
    run_id: str,
    output_root: Path | None = None,
    overwrite: bool = False,
) -> dict[str, Any]:
    root = root.resolve()
    eval_dir = eval_dir.resolve()
    audit_path = audit_path.resolve()
    if output_root is None:
        output_root = root / "results" / "reports" / "extraction_missing_relations"
    output_root = output_root.resolve()
    output_dir = output_root / "runs" / run_id
    output_dir.mkdir(parents=True, exist_ok=True)

    json_path = output_dir / "summary.json"
    md_path = output_dir / "summary.md"
    if (json_path.exists() or md_path.exists()) and not overwrite:
        raise FileExistsError(
            f"目标报告已存在，若要覆盖请传 --overwrite：{output_dir}"
        )

    audit_index = load_audit_index(audit_path)
    eval_files = sorted(p for p in eval_dir.glob("*.json") if p.is_file())
    if not eval_files:
        raise ValueError(f"eval_dir 中没有候选结果文件：{eval_dir}")

    candidate_reports: dict[str, Any] = {}
    for eval_file in eval_files:
        payload = json.loads(eval_file.read_text(encoding="utf-8"))
        candidate = str(payload.get("candidate") or eval_file.stem).strip()
        results = _load_results_from_payload(payload, candidate=candidate)
        candidate_reports[candidate] = classify_gold_relations(
            results=results,
            audit_index=audit_index,
        )

    summary = {
        "task": "gold_missing_relations_diagnostics",
        "run_id": run_id,
        "source_eval_dir": str(eval_dir),
        "audit_path": str(audit_path),
        "candidate_count": len(candidate_reports),
        "candidates": candidate_reports,
        "aggregate_across_candidates": _aggregate_across_candidates(candidate_reports),
    }
    json_path.write_text(
        json.dumps(summary, ensure_ascii=False, indent=2) + "\n",
        encoding="utf-8",
    )
    _write_markdown(md_path, summary)
    return summary


def _aggregate_across_candidates(candidate_reports: dict[str, Any]) -> dict[str, Any]:
    """跨候选求和，方便一眼看“整体上哪类 miss 占比最大”。"""

    totals: Counter[str] = Counter()
    relation_type_totals: dict[str, Counter[str]] = defaultdict(Counter)
    for report in candidate_reports.values():
        counts = (report.get("totals") or {}).get("counts") or {}
        for key, value in counts.items():
            totals[key] += int(value)
        for rtype, stat in (report.get("by_relation_type") or {}).items():
            for key, value in (stat.get("counts") or {}).items():
                relation_type_totals[rtype][key] += int(value)
    total = sum(totals.values())
    return {
        "total": total,
        "counts": {cat: totals.get(cat, 0) for cat in CATEGORY_ORDER},
        "rates": {
            cat: _safe_rate(totals.get(cat, 0), total)
            for cat in CATEGORY_ORDER
        },
        "by_relation_type": {
            rtype: {
                "total": sum(counter.values()),
                "counts": {cat: counter.get(cat, 0) for cat in CATEGORY_ORDER},
                "hit_rate": _safe_rate(counter.get("hit", 0), sum(counter.values())),
            }
            for rtype, counter in sorted(relation_type_totals.items())
        },
    }


def _title_to_idxs(ext_candidates: Sequence[ExtCandidate]) -> dict[str, list[int]]:
    mapping: dict[str, list[int]] = defaultdict(list)
    for cand in ext_candidates:
        mapping[cand.title_norm].append(cand.idx)
    return mapping


def _categorize(
    *,
    rtype: str,
    src_idx: int | None,
    tgt_idx: int | None,
    typed_edges: set[tuple[int, str, int]],
    any_edges: dict[tuple[int, int], set[str]],
) -> str:
    if src_idx is None and tgt_idx is None:
        return "both_endpoints_missing"
    if src_idx is None:
        return "source_endpoint_missing"
    if tgt_idx is None:
        return "target_endpoint_missing"
    if (src_idx, rtype, tgt_idx) in typed_edges:
        return "hit"
    if (tgt_idx, rtype, src_idx) in typed_edges:
        return "direction_reversed"
    forward_types = any_edges.get((src_idx, tgt_idx)) or set()
    backward_types = any_edges.get((tgt_idx, src_idx)) or set()
    if forward_types or backward_types:
        return "wrong_type"
    return "both_endpoints_present_but_not_connected"


def _gold_entity_name(gold_entities: Iterable[dict[str, Any]], entity_id: Any) -> str:
    target_id = str(entity_id or "")
    if not target_id:
        return ""
    for entity in gold_entities:
        if str(entity.get("entity_id") or "") == target_id:
            return str(entity.get("name") or "")
    return ""


def _safe_rate(numerator: int, denominator: int) -> float | None:
    if denominator <= 0:
        return None
    return numerator / denominator


def _load_results_from_payload(
    payload: dict[str, Any],
    *,
    candidate: str,
) -> list[StructuredExtractionResult]:
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


def _write_markdown(path: Path, summary: dict[str, Any]) -> None:
    lines = [
        "# Gold 侧缺失关系诊断",
        "",
        f"- `run_id`：{summary['run_id']}",
        f"- `source_eval_dir`：`{summary['source_eval_dir']}`",
        f"- `audit_path`：`{summary['audit_path']}`",
        f"- `candidate_count`：{summary['candidate_count']}",
        "",
        "## 跨候选汇总",
        "",
    ]
    aggregate = summary.get("aggregate_across_candidates") or {}
    counts = aggregate.get("counts") or {}
    rates = aggregate.get("rates") or {}
    total = int(aggregate.get("total") or 0)
    lines.append(f"- 所有候选 × gold 关系总数：{total}")
    lines.append("")
    lines.append("| 类别 | count | 占比 |")
    lines.append("|---|---:|---:|")
    for category in CATEGORY_ORDER:
        lines.append(
            f"| {category} | {counts.get(category, 0)} | "
            f"{_format_rate(rates.get(category))} |"
        )

    lines.extend(["", "### 按关系类型（跨候选汇总）", ""])
    lines.append("| relation_type | total | hit | dir_rev | wrong_type | both_present_no_edge | src_miss | tgt_miss | both_miss | hit_rate |")
    lines.append("|---|---:|---:|---:|---:|---:|---:|---:|---:|---:|")
    for rtype, stat in (aggregate.get("by_relation_type") or {}).items():
        cs = stat.get("counts") or {}
        lines.append(
            "| "
            + " | ".join(
                [
                    rtype,
                    str(stat.get("total") or 0),
                    str(cs.get("hit", 0)),
                    str(cs.get("direction_reversed", 0)),
                    str(cs.get("wrong_type", 0)),
                    str(cs.get("both_endpoints_present_but_not_connected", 0)),
                    str(cs.get("source_endpoint_missing", 0)),
                    str(cs.get("target_endpoint_missing", 0)),
                    str(cs.get("both_endpoints_missing", 0)),
                    _format_rate(stat.get("hit_rate")),
                ]
            )
            + " |"
        )

    lines.extend(["", "## 各候选详情", ""])
    lines.append("| candidate | total | hit | dir_rev | wrong_type | both_present_no_edge | src_miss | tgt_miss | both_miss | hit_rate |")
    lines.append("|---|---:|---:|---:|---:|---:|---:|---:|---:|---:|")
    for candidate, report in (summary.get("candidates") or {}).items():
        totals = report.get("totals") or {}
        cs = totals.get("counts") or {}
        total = int(totals.get("total") or 0)
        hit_rate = _safe_rate(cs.get("hit", 0), total)
        lines.append(
            "| "
            + " | ".join(
                [
                    candidate,
                    str(total),
                    str(cs.get("hit", 0)),
                    str(cs.get("direction_reversed", 0)),
                    str(cs.get("wrong_type", 0)),
                    str(cs.get("both_endpoints_present_but_not_connected", 0)),
                    str(cs.get("source_endpoint_missing", 0)),
                    str(cs.get("target_endpoint_missing", 0)),
                    str(cs.get("both_endpoints_missing", 0)),
                    _format_rate(hit_rate),
                ]
            )
            + " |"
        )

    lines.extend(["", "## 典型 miss 样本（每候选最多 20 条）", ""])
    for candidate, report in (summary.get("candidates") or {}).items():
        misses = list(report.get("misses") or [])[:20]
        if not misses:
            continue
        lines.append(f"### {candidate}")
        lines.append("")
        lines.append("| sample_id | relation_id | relation_type | category | source_name | target_name |")
        lines.append("|---|---|---|---|---|---|")
        for miss in misses:
            lines.append(
                "| "
                + " | ".join(
                    [
                        str(miss.get("sample_id") or ""),
                        str(miss.get("relation_id") or ""),
                        str(miss.get("relation_type") or ""),
                        str(miss.get("category") or ""),
                        _escape_pipe(str(miss.get("source_name") or "")),
                        _escape_pipe(str(miss.get("target_name") or "")),
                    ]
                )
                + " |"
            )
        lines.append("")

    path.write_text("\n".join(lines) + "\n", encoding="utf-8")


def _escape_pipe(text: str) -> str:
    return text.replace("|", "\\|")


def _format_rate(value: Any) -> str:
    if value is None:
        return "-"
    try:
        return f"{float(value):.4f}"
    except (TypeError, ValueError):
        return "-"


def _build_parser() -> argparse.ArgumentParser:
    parser = argparse.ArgumentParser(
        description="对一个 eval run 做 gold 侧缺失关系归因诊断"
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
    parser.add_argument("--run-id", required=True, help="诊断报告 run_id")
    parser.add_argument(
        "--output-root",
        default=None,
        help="覆盖默认输出根目录（默认 results/reports/extraction_missing_relations）",
    )
    parser.add_argument("--overwrite", action="store_true", help="覆盖已有诊断报告")
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
    output_root = Path(args.output_root).resolve() if args.output_root else None
    summary = diagnose_eval_dir(
        root=root,
        eval_dir=eval_dir,
        audit_path=audit_path,
        run_id=args.run_id,
        output_root=output_root,
        overwrite=args.overwrite,
    )
    print(json.dumps(
        {
            "status": "success",
            "run_id": summary["run_id"],
            "candidate_count": summary["candidate_count"],
            "totals": summary["aggregate_across_candidates"]["counts"],
        },
        ensure_ascii=False,
        indent=2,
    ))
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
