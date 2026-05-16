from __future__ import annotations

import argparse
import json
import re
from dataclasses import replace
from pathlib import Path
from typing import Any

import pandas as pd

from graphrag_pipeline.scripts.qa_eval.citation_extractor import extract_text_unit_refs
from graphrag_pipeline.scripts.qa_eval.ir_metrics import score_ranked_citations
from graphrag_pipeline.scripts.qa_eval.run_loader import (
    audit_text_unit_prefix_collisions,
    load_raw_mode_answer,
    load_run_meta,
    load_test_set,
    resolve_text_units_path,
    select_question_ids_for_run,
)
from graphrag_pipeline.scripts.qa_eval.semantic_similarity import SemanticScoringConfig, score_semantic_similarity
from graphrag_pipeline.scripts.qa_eval.test_set_schema import TEXT_UNIT_ID_PREFIX_LEN
from graphrag_pipeline.scripts.qa_eval.text_unit_lookup import DataCitationLookup, load_data_citation_lookup


_DIAGNOSTIC_REF_RE = re.compile(r"^[A-Za-z0-9][A-Za-z0-9_-]{7,}$")


def _hit_rate(answer: str, terms: list[str]) -> float:
    if not terms:
        return 1.0
    hits = sum(1 for term in terms if term and term in answer)
    return round(hits / len(terms), 4)


def _negative_hit(answer: str, terms: list[str]) -> float:
    if not terms:
        return 0.0
    return 1.0 if any(term and term in answer for term in terms) else 0.0


def _hybrid_diagnostic_columns(diagnostics: dict[str, Any]) -> dict[str, Any]:
    if not diagnostics:
        return {
            "hybrid_used_local_fallback": None,
            "hybrid_guardrail_score": None,
            "hybrid_low_evidence_count": None,
            "hybrid_high_evidence_count": None,
            "hybrid_synthesis_attempted": None,
            "hybrid_fallback_reasons": None,
            "hybrid_fused_evidence_refs": None,
            "hybrid_fused_evidence_sources": None,
            "hybrid_synthesis_reason": None,
            "hybrid_local_fallback_enabled": None,
        }
    fallback_reasons = diagnostics.get("fallback_reasons") or []
    fused_refs = _normalized_diagnostic_refs(diagnostics.get("fused_evidence_refs"))
    return {
        "hybrid_used_local_fallback": diagnostics.get("used_local_fallback"),
        "hybrid_guardrail_score": diagnostics.get("guardrail_score"),
        "hybrid_low_evidence_count": diagnostics.get("low_evidence_count"),
        "hybrid_high_evidence_count": diagnostics.get("high_evidence_count"),
        "hybrid_synthesis_attempted": diagnostics.get("synthesis_attempted"),
        "hybrid_fallback_reasons": ",".join(str(reason) for reason in fallback_reasons),
        "hybrid_fused_evidence_refs": ",".join(str(ref) for ref in fused_refs),
        "hybrid_fused_evidence_sources": _format_source_counts(diagnostics.get("fused_evidence_sources")),
        "hybrid_synthesis_reason": diagnostics.get("synthesis_reason"),
        "hybrid_local_fallback_enabled": diagnostics.get("local_fallback_enabled"),
    }


def _selected_evidence_columns(
    *,
    question_id: str,
    diagnostics: dict[str, Any],
    gold_refs: list[str],
) -> dict[str, float | None]:
    refs = _normalized_diagnostic_refs(diagnostics.get("fused_evidence_refs"))
    if not refs:
        return {
            "selected_evidence_recall_at_3": None,
            "selected_evidence_rr": None,
            "selected_evidence_ndcg_at_5": None,
        }
    scores = score_ranked_citations(
        question_id=question_id,
        ranked_refs=refs,
        gold_refs=gold_refs,
        cutoffs=[3, 5],
    )
    return {
        "selected_evidence_recall_at_3": float(scores["citation_recall_at_3"]),
        "selected_evidence_rr": float(scores["citation_rr"]),
        "selected_evidence_ndcg_at_5": float(scores["citation_ndcg_at_5"]),
    }


def _normalized_diagnostic_refs(value: object) -> list[str]:
    if not isinstance(value, list):
        return []
    refs: list[str] = []
    for raw in value:
        token = str(raw).strip()
        if not _DIAGNOSTIC_REF_RE.fullmatch(token):
            continue
        prefix = token[:TEXT_UNIT_ID_PREFIX_LEN]
        if prefix not in refs:
            refs.append(prefix)
    return refs


def _format_source_counts(value: object) -> str:
    if not isinstance(value, dict):
        return ""
    preferred = ["bm25", "basic-citation"]
    ordered = [key for key in preferred if key in value]
    ordered.extend(key for key in sorted(value) if key not in ordered)
    return ",".join(f"{key}={value[key]}" for key in ordered)


def _effective_score_experimental(row: dict[str, Any]) -> float:
    quality = (
        0.42 * float(row["semantic_coverage_f1"])
        + 0.18 * float(row["citation_recall_at_3"])
        + 0.12 * float(row["citation_rr"])
        + 0.12 * float(row["entity_hit_rate"])
        + 0.08 * float(row["keyword_recall"])
        + 0.08 * float(row["rouge_lsum"])
    )
    latency_penalty = min(float(row["elapsed_seconds"]) / 180.0, 1.0) * 0.08
    risk_penalty = float(row["negative_hit"]) * 0.12 + float(row["error_count"]) * 0.25
    return round(max(0.0, min(1.0, quality - latency_penalty - risk_penalty)), 4)


def _load_optional_data_citation_lookup(run_dir: Path, text_units_path: Path | None) -> DataCitationLookup | None:
    try:
        resolved = resolve_text_units_path(run_dir, text_units_path)
    except FileNotFoundError:
        return None
    collisions = audit_text_unit_prefix_collisions(resolved)
    if collisions:
        sample = ", ".join(f"{prefix}: {ids[:2]}" for prefix, ids in list(collisions.items())[:3])
        raise ValueError(f"text unit prefix collision detected; adjust schema prefix length before scoring: {sample}")
    return load_data_citation_lookup(resolved)


def score_run_algorithmically(
    run_dir: Path,
    *,
    test_set_path: Path | None = None,
    text_units_path: Path | None = None,
    semantic_config: SemanticScoringConfig | None = None,
    enable_bge_m3: bool | None = None,
) -> dict[str, Any]:
    meta = load_run_meta(run_dir)
    test_set = test_set_path or Path(meta["test_set_path"])
    items = load_test_set(test_set)
    selected_items = _select_items_for_run(items, meta, run_dir)
    semantic_config = semantic_config or SemanticScoringConfig()
    if enable_bge_m3 is not None:
        semantic_config = replace(semantic_config, enable_bge_m3=enable_bge_m3)
    data_citation_lookup = _load_optional_data_citation_lookup(run_dir, text_units_path)
    rows: list[dict[str, Any]] = []

    for item in selected_items:
        for mode in meta["modes"]:
            raw = load_raw_mode_answer(run_dir, item.id, mode)
            refs = extract_text_unit_refs(raw.answer, data_citation_lookup=data_citation_lookup)
            semantic = score_semantic_similarity(
                answer=raw.answer,
                reference=item.gold_answer_summary,
                config=semantic_config,
            )
            ir = score_ranked_citations(
                question_id=item.id,
                ranked_refs=refs,
                gold_refs=item.gold_text_unit_ids,
                cutoffs=[1, 3, 5],
            )
            row = {
                "question_id": item.id,
                "category": item.category.value,
                "mode": mode,
                "answer_chars": len(raw.answer),
                "entity_hit_rate": _hit_rate(raw.answer, item.gold_entities),
                "must_cite_hit": _hit_rate(raw.answer, item.must_cite_terms),
                "negative_hit": _negative_hit(raw.answer, item.negative_terms),
                "elapsed_seconds": round(raw.elapsed_seconds, 4),
                "error_count": raw.error_count,
                "error_type": raw.error_type,
                "error_message": raw.error_message,
                **_hybrid_diagnostic_columns(raw.hybrid_diagnostics),
                **_selected_evidence_columns(
                    question_id=item.id,
                    diagnostics=raw.hybrid_diagnostics,
                    gold_refs=item.gold_text_unit_ids,
                ),
                **semantic,
                **{key: value for key, value in ir.items() if key != "question_id"},
            }
            row["effective_score_experimental"] = _effective_score_experimental(row)
            rows.append(row)

    run_dir.mkdir(parents=True, exist_ok=True)
    df = pd.DataFrame(rows)
    df.to_csv(run_dir / "algorithmic_scoring.csv", index=False)
    grouped = (
        df.groupby(["category", "mode"], as_index=False)
        .mean(numeric_only=True)
        .sort_values(["category", "mode"])
    )
    summary = {
        "rows": rows,
        "per_category_mode": grouped.to_dict(orient="records"),
    }
    (run_dir / "algorithmic_scoring.json").write_text(
        json.dumps(summary, ensure_ascii=False, indent=2),
        encoding="utf-8",
    )
    (run_dir / "algorithmic_scoring.md").write_text(
        "# 算法增强评分\n\n" + _dataframe_to_markdown(grouped) + "\n",
        encoding="utf-8",
    )
    return summary


def _select_items_for_run(
    items: dict[str, Any],
    meta: dict[str, Any],
    run_dir: Path,
) -> list[Any]:
    question_ids = select_question_ids_for_run(items, meta, run_dir)
    return [items[question_id] for question_id in question_ids if question_id in items]


def _dataframe_to_markdown(frame: pd.DataFrame) -> str:
    if frame.empty:
        return "（暂无评分数据）"
    headers = [str(column) for column in frame.columns]
    lines = [
        "| " + " | ".join(headers) + " |",
        "| " + " | ".join(["---"] * len(headers)) + " |",
    ]
    for _, row in frame.iterrows():
        values = [str(row[column]) for column in frame.columns]
        lines.append("| " + " | ".join(values) + " |")
    return "\n".join(lines)


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--run-dir", type=Path, required=True)
    parser.add_argument("--test-set", type=Path, default=None)
    parser.add_argument("--text-units-path", type=Path, default=None)
    parser.add_argument("--bge-model", default=None, help="BGE-M3 model name or local model directory.")
    parser.add_argument("--bge-device", default=None, help="BGE-M3 device, for example `cuda` or `cpu`.")
    parser.add_argument("--bge-batch-size", type=int, default=8, help="BGE-M3 encode batch size.")
    parser.add_argument("--bge-fp16", action="store_true", help="Use fp16 for BGE-M3, recommended on CUDA GPUs.")
    parser.add_argument("--similarity-threshold", type=float, default=0.62, help="BGE-M3 coverage threshold.")
    parser.add_argument("--max-chunk-chars", type=int, default=260, help="Maximum chars per semantic chunk.")
    parser.add_argument(
        "--cheap-only",
        "--disable-bge-m3",
        action="store_true",
        dest="cheap_only",
        help="Disable BGE-M3 semantic coverage and emit cheap baseline metrics only.",
    )
    args = parser.parse_args()
    score_run_algorithmically(
        args.run_dir,
        test_set_path=args.test_set,
        text_units_path=args.text_units_path,
        semantic_config=SemanticScoringConfig(
            bge_m3_model=args.bge_model,
            bge_device=args.bge_device,
            bge_use_fp16=args.bge_fp16,
            bge_batch_size=args.bge_batch_size,
            similarity_threshold=args.similarity_threshold,
            max_chunk_chars=args.max_chunk_chars,
        ),
        enable_bge_m3=not args.cheap_only,
    )
    print(f"wrote algorithmic scoring under {args.run_dir}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
