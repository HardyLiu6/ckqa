from __future__ import annotations

import argparse
import json
from dataclasses import replace
from pathlib import Path
from typing import Any

import pandas as pd

from graphrag_pipeline.scripts.qa_eval.citation_extractor import extract_text_unit_refs
from graphrag_pipeline.scripts.qa_eval.ir_metrics import score_ranked_citations
from graphrag_pipeline.scripts.qa_eval.run_loader import (
    audit_text_unit_prefix_collisions,
    infer_question_ids_from_raw,
    load_raw_mode_answer,
    load_run_meta,
    load_test_set,
    resolve_text_units_path,
)
from graphrag_pipeline.scripts.qa_eval.semantic_similarity import SemanticScoringConfig, score_semantic_similarity
from graphrag_pipeline.scripts.qa_eval.text_unit_lookup import DataCitationLookup, load_data_citation_lookup


def _hit_rate(answer: str, terms: list[str]) -> float:
    if not terms:
        return 1.0
    hits = sum(1 for term in terms if term and term in answer)
    return round(hits / len(terms), 4)


def _negative_hit(answer: str, terms: list[str]) -> float:
    if not terms:
        return 0.0
    return 1.0 if any(term and term in answer for term in terms) else 0.0


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
    question_ids = _coerce_question_ids(meta.get("question_ids"))
    if not question_ids:
        question_ids = infer_question_ids_from_raw(run_dir)
    if not question_ids:
        question_ids = _fallback_question_ids_from_limits(items, meta)
    if not question_ids:
        return list(items.values())
    return [items[question_id] for question_id in question_ids if question_id in items]


def _coerce_question_ids(value: Any) -> list[str]:
    if not isinstance(value, list):
        return []
    return [str(item).strip() for item in value if str(item).strip()]


def _fallback_question_ids_from_limits(items: dict[str, Any], meta: dict[str, Any]) -> list[str]:
    limit = meta.get("total_items", meta.get("max_items"))
    try:
        count = int(limit)
    except (TypeError, ValueError):
        return []
    if count <= 0:
        return []
    return list(items)[:count]


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
        enable_bge_m3=not args.cheap_only,
    )
    print(f"wrote algorithmic scoring under {args.run_dir}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
