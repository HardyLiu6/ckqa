#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""步骤 8 诊断脚本：endpoint 失败模式 + audit relation 命中差距。

用途：一次性阅读工具，辅助决策下一步分叉（Prompt / schema / audit 扩量 / gate 调整）。
读完报告即可，不承担持续诊断基础设施职责。

依赖 private 函数（工具脚本可接受）：
- scoring_audit.align_sample（严格对齐，返回 AlignResult.match_mode ∈ 五值闭集）
- scoring_audit._build_ext_candidates / _build_gold_entities（输入适配器）
- scoring_metrics._normalize_title（title 归一化规则）
"""

from __future__ import annotations

import argparse
import collections
import datetime as dt
import json
from pathlib import Path
from typing import Any, Sequence

from .extraction_schema import (
    ExtractionEntity,
    ExtractionRelationship,
    StructuredExtractionResult,
)
from .scoring_audit import (
    AlignResult,
    _build_ext_candidates,
    _build_gold_entities,
    align_sample,
    load_audit_index,
)
from .scoring_metrics import _normalize_title


PROJECT_ROOT = Path(__file__).resolve().parents[2]
DEFAULT_EVAL_DIR = "results/extraction_eval"
DEFAULT_RELATION_SCHEMA = "config/schema/relation_types.json"
DEFAULT_AUDIT_PATH = "data/eval/audit_extraction_set.json"
DEFAULT_OUTPUT_DIR = "results/reports/extraction_scoring/diagnostics"


def _resolve(path, *, root: Path, default: str | None) -> Path | None:
    target = path if path is not None else default
    if target is None:
        return None
    candidate = Path(target)
    return candidate if candidate.is_absolute() else (root / candidate).resolve()


def _load_eval_file(path: Path) -> tuple[str, list[StructuredExtractionResult]]:
    payload = json.loads(path.read_text(encoding="utf-8"))
    candidate = str(payload.get("candidate") or path.stem).strip()
    results: list[StructuredExtractionResult] = []
    for raw in payload.get("results") or []:
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


def classify_endpoint(rel, entities_by_title_norm: dict[str, str], relation_schema: dict):
    """返回 (category, src_type, tgt_type)。5 类互斥穷尽。"""
    if rel.type not in relation_schema:
        return "invalid_type", None, None
    src_type = entities_by_title_norm.get(_normalize_title(rel.source))
    tgt_type = entities_by_title_norm.get(_normalize_title(rel.target))
    if src_type is None:
        return "unresolved_src", None, tgt_type
    if tgt_type is None:
        return "unresolved_tgt", src_type, None
    constraints = relation_schema[rel.type]
    source_types = set(constraints.get("source_types") or [])
    target_types = set(constraints.get("target_types") or [])
    if src_type in source_types and tgt_type in target_types:
        return "valid", src_type, tgt_type
    return "type_mismatch", src_type, tgt_type


def diagnose_endpoint_for_results(results, relation_schema):
    """返回 (counts, mismatch_patterns, mismatch_examples)。"""
    counts: collections.Counter = collections.Counter()
    patterns: collections.Counter = collections.Counter()
    examples: dict[tuple, list] = {}
    for item in results:
        if item.status != "success":
            continue
        entities_by_title = {
            _normalize_title(e.title): e.type for e in item.entities
        }
        for rel in item.relationships:
            category, src_type, tgt_type = classify_endpoint(
                rel, entities_by_title, relation_schema
            )
            counts[category] += 1
            if category == "type_mismatch":
                pat = (src_type, rel.type, tgt_type)
                patterns[pat] += 1
                examples.setdefault(pat, []).append((rel.source, rel.target))
    return counts, patterns, examples


def classify_audit_relation(gold_rel, aligned, id_to_name, idx_triples):
    """按 7 类 verdict 判定 gold relation 命中情况。

    verdict 闭集：hit / type_mismatch / triple_not_in_ext /
                  align_fail_src / align_fail_tgt / align_fail_both /
                  align_collision（新增）。
    """
    src_id = str(gold_rel.get("source_entity_id", "") or "")
    tgt_id = str(gold_rel.get("target_entity_id", "") or "")
    rtype = gold_rel.get("type", "")
    src_name = id_to_name.get(src_id, "")
    tgt_name = id_to_name.get(tgt_id, "")
    src_align: AlignResult | None = aligned.get(src_id)
    tgt_align: AlignResult | None = aligned.get(tgt_id)
    src_mode = src_align.match_mode if src_align else "none"
    tgt_mode = tgt_align.match_mode if tgt_align else "none"
    src_idx = src_align.matched_ext_idx if src_align else None
    tgt_idx = tgt_align.matched_ext_idx if tgt_align else None

    occupied_modes = {"exact_occupied", "alias_occupied"}
    if src_mode in occupied_modes or tgt_mode in occupied_modes:
        verdict = "align_collision"
        ext_types: list[str] = []
    elif src_idx is None and tgt_idx is None:
        verdict = "align_fail_both"
        ext_types = []
    elif src_idx is None:
        verdict = "align_fail_src"
        ext_types = []
    elif tgt_idx is None:
        verdict = "align_fail_tgt"
        ext_types = []
    else:
        ext_types_set = idx_triples.get((src_idx, tgt_idx), set())
        ext_types = sorted(ext_types_set)
        if not ext_types_set:
            verdict = "triple_not_in_ext"
        elif rtype in ext_types_set:
            verdict = "hit"
        else:
            verdict = "type_mismatch"
    return {
        "verdict": verdict,
        "gold_src": src_name,
        "gold_tgt": tgt_name,
        "gold_type": rtype,
        "aligned_src_idx": src_idx,
        "aligned_tgt_idx": tgt_idx,
        "src_match_mode": src_mode,
        "tgt_match_mode": tgt_mode,
        "ext_types": ext_types,
    }


def diagnose_audit_for_results(results, audit_index):
    per_sample: list[tuple[str, list[dict]]] = []
    verdicts: collections.Counter = collections.Counter()
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
        id_to_name = {
            str(ge.get("entity_id", "") or ""): ge.get("name", "")
            for ge in entry.gold_entities
        }

        # title_norm -> list[ext_idx]，用于把 extraction 关系扇出成 idx triple
        title_to_idxs: dict[str, list[int]] = {}
        for cand in cands:
            title_to_idxs.setdefault(cand.title_norm, []).append(cand.idx)
        # (src_idx, tgt_idx) -> set of rel types
        idx_triples: dict[tuple[int, int], set[str]] = collections.defaultdict(set)
        for rel in item.relationships:
            for s in title_to_idxs.get(_normalize_title(rel.source), ()):
                for t in title_to_idxs.get(_normalize_title(rel.target), ()):
                    idx_triples[(s, t)].add(rel.type)

        diagnoses: list[dict] = []
        for g in entry.gold_relations:
            d = classify_audit_relation(g, aligned, id_to_name, idx_triples)
            verdicts[d["verdict"]] += 1
            diagnoses.append(d)
        per_sample.append((item.sample_id, diagnoses))
    return per_sample, verdicts


CATEGORIES = ("valid", "invalid_type", "unresolved_src", "unresolved_tgt", "type_mismatch")
VERDICT_KEYS = ("hit", "type_mismatch", "triple_not_in_ext",
                "align_fail_src", "align_fail_tgt", "align_fail_both",
                "align_collision")


def render_markdown(*, timestamp, inputs_summary, per_candidate_endpoint, per_candidate_audit):
    out: list[str] = []
    out.append(f"# 步骤 8 诊断报告 {timestamp}")
    out.append("")
    out.append("## 输入")
    out.append("")
    for k, v in inputs_summary.items():
        out.append(f"- **{k}**：{v}")
    out.append("")
    out.append("## A. 端点失败模式归因")
    out.append("")
    out.append("### A.1 按候选分类计数")
    out.append("")
    out.append("| candidate | " + " | ".join(CATEGORIES) + " | total |")
    out.append("|" + "|".join(["---"] * (len(CATEGORIES) + 2)) + "|")
    for cand, (counts, _, _) in per_candidate_endpoint.items():
        total = sum(counts.values())
        row = [cand] + [str(counts.get(c, 0)) for c in CATEGORIES] + [str(total)]
        out.append("| " + " | ".join(row) + " |")
    out.append("")
    out.append("### A.2 type_mismatch Top-5 模式（每候选）")
    out.append("")
    for cand, (_, patterns, examples) in per_candidate_endpoint.items():
        out.append(f"#### {cand}")
        out.append("")
        if not patterns:
            out.append("_（无 type_mismatch）_")
            out.append("")
            continue
        out.append("| (src.type, rel.type, tgt.type) | count | 示例实体对 |")
        out.append("|---|---|---|")
        for pat, count in patterns.most_common(5):
            ex = examples.get(pat, [])[:2]
            ex_str = "; ".join(f"{a} → {b}" for a, b in ex) if ex else ""
            out.append(f"| `{pat}` | {count} | {ex_str} |")
        out.append("")
    out.append("## B. audit relation 命中诊断")
    out.append("")
    out.append("### B.1 总体 verdict 计数")
    out.append("")
    out.append("| candidate | " + " | ".join(VERDICT_KEYS) + " | total |")
    out.append("|" + "|".join(["---"] * (len(VERDICT_KEYS) + 2)) + "|")
    for cand, (_, verdicts) in per_candidate_audit.items():
        total = sum(verdicts.values())
        row = [cand] + [str(verdicts.get(k, 0)) for k in VERDICT_KEYS] + [str(total)]
        out.append("| " + " | ".join(row) + " |")
    out.append("")
    out.append("### B.2 逐条明细（按 sample × candidate）")
    out.append("")
    for cand, (per_sample, _) in per_candidate_audit.items():
        for sample_id, diagnoses in per_sample:
            out.append(f"#### {sample_id} · {cand}")
            out.append("")
            if not diagnoses:
                out.append("_（无 gold relation）_")
                out.append("")
                continue
            out.append("| gold | aligned_idx (src / tgt) | match_mode (src / tgt) | ext_types | verdict |")
            out.append("|---|---|---|---|---|")
            for d in diagnoses:
                gold = f"{d['gold_src']} --[{d['gold_type']}]--> {d['gold_tgt']}"
                src_idx = d['aligned_src_idx']
                tgt_idx = d['aligned_tgt_idx']
                aligned = (
                    f"{src_idx if src_idx is not None else '—'} / "
                    f"{tgt_idx if tgt_idx is not None else '—'}"
                )
                mode = f"{d['src_match_mode']} / {d['tgt_match_mode']}"
                ext_types = ", ".join(d["ext_types"]) if d["ext_types"] else "—"
                out.append(f"| {gold} | {aligned} | {mode} | {ext_types} | {d['verdict']} |")
            out.append("")
    return "\n".join(out) + "\n"


def diagnose_step8(*, root, eval_dir, relation_schema_path, audit_path, output_dir, overwrite):
    root = Path(root).resolve()
    eval_root = _resolve(eval_dir, root=root, default=DEFAULT_EVAL_DIR)
    relation_schema_file = _resolve(relation_schema_path, root=root, default=DEFAULT_RELATION_SCHEMA)
    audit_file = _resolve(audit_path, root=root, default=DEFAULT_AUDIT_PATH)
    output_root = _resolve(output_dir, root=root, default=DEFAULT_OUTPUT_DIR)
    if eval_root is None or not eval_root.exists():
        raise FileNotFoundError(f"未找到评测输入目录：{eval_root}")
    eval_files = sorted(p for p in eval_root.glob("*.json") if p.is_file())
    if not eval_files:
        raise ValueError(f"评测输入目录无 JSON：{eval_root}")
    if not relation_schema_file or not relation_schema_file.exists():
        raise FileNotFoundError(f"关系 schema 不存在：{relation_schema_file}")
    relation_payload = json.loads(relation_schema_file.read_text(encoding="utf-8"))
    relation_schema = relation_payload.get("relation_types") or {}
    audit_index: dict = {}
    if audit_file and audit_file.exists():
        audit_index = load_audit_index(audit_file)
    per_candidate_endpoint: dict = {}
    per_candidate_audit: dict = {}
    candidates: list[str] = []
    for ef in eval_files:
        cand, results = _load_eval_file(ef)
        candidates.append(cand)
        per_candidate_endpoint[cand] = diagnose_endpoint_for_results(results, relation_schema)
        if audit_index:
            per_candidate_audit[cand] = diagnose_audit_for_results(results, audit_index)
        else:
            per_candidate_audit[cand] = ([], collections.Counter())
    now = dt.datetime.now()
    timestamp = now.strftime("%Y-%m-%dT%H%M%S")
    output_root.mkdir(parents=True, exist_ok=True)
    out_path = output_root / f"{timestamp}.md"
    if out_path.exists() and not overwrite:
        raise FileExistsError(f"诊断文件已存在：{out_path}（传 --overwrite 覆盖）")
    inputs_summary = {
        "eval_dir": str(eval_root),
        "relation_schema": str(relation_schema_file),
        "audit_path": str(audit_file) if audit_file else "（未提供）",
        "candidates": ", ".join(candidates),
    }
    content = render_markdown(
        timestamp=timestamp,
        inputs_summary=inputs_summary,
        per_candidate_endpoint=per_candidate_endpoint,
        per_candidate_audit=per_candidate_audit,
    )
    out_path.write_text(content, encoding="utf-8")
    return {"output": str(out_path), "candidates": candidates, "timestamp": timestamp}


def _build_parser():
    p = argparse.ArgumentParser(description="步骤 8 诊断（A 端点失败模式 + B audit relation）")
    p.add_argument("--eval-dir", help="候选抽取结果目录，默认 results/extraction_eval")
    p.add_argument("--relation-schema", help="关系 schema JSON 路径")
    p.add_argument("--audit", help="audit 集 JSON 路径；不传则仅跑 A")
    p.add_argument("--output-dir", help="诊断报告输出目录")
    p.add_argument("--overwrite", action="store_true", help="覆盖已存在的 <timestamp>.md")
    return p


def main(argv=None):
    args = _build_parser().parse_args(argv)
    summary = diagnose_step8(
        root=PROJECT_ROOT,
        eval_dir=args.eval_dir,
        relation_schema_path=args.relation_schema,
        audit_path=args.audit,
        output_dir=args.output_dir,
        overwrite=args.overwrite,
    )
    print(json.dumps(summary, ensure_ascii=False, indent=2))
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
