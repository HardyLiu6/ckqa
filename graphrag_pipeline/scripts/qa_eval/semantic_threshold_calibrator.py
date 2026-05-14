from __future__ import annotations

import argparse
import json
import os
from dataclasses import dataclass
from pathlib import Path
from statistics import mean
from typing import Any

import numpy as np

from graphrag_pipeline.scripts.qa_eval.run_loader import (
    load_raw_mode_answer,
    load_run_meta,
    load_test_set,
    select_question_ids_for_run,
)
from graphrag_pipeline.scripts.qa_eval.semantic_similarity import (
    DEFAULT_BGE_M3_MODEL,
    SemanticScoringConfig,
    _coverage_from_similarity,
    _encode_dense_chunks,
    split_semantic_chunks,
)


DEFAULT_THRESHOLDS = [0.50, 0.60, 0.62, 0.65, 0.70]


@dataclass(frozen=True, slots=True)
class CalibrationPair:
    question_id: str
    category: str
    mode: str
    answer: str
    reference: str


def calibrate_semantic_thresholds(
    run_dir: Path,
    *,
    thresholds: list[float] | None = None,
    max_questions: int = 10,
    config: SemanticScoringConfig | None = None,
    output_path: Path | None = None,
) -> dict[str, Any]:
    thresholds = thresholds or DEFAULT_THRESHOLDS
    config = config or SemanticScoringConfig()
    pairs = _load_calibration_pairs(run_dir, max_questions=max_questions)
    evaluated: list[dict[str, Any]] = []
    skipped = 0

    for pair in pairs:
        if not pair.answer.strip() or not pair.reference.strip():
            skipped += 1
            continue
        matrix = _similarity_matrix(pair.answer, pair.reference, config)
        for threshold in thresholds:
            scores = _coverage_from_similarity(matrix, threshold=threshold)
            evaluated.append(
                {
                    "question_id": pair.question_id,
                    "category": pair.category,
                    "mode": pair.mode,
                    "threshold": threshold,
                    **scores,
                }
            )

    summary = {
        "run_dir": str(run_dir),
        "model": config.bge_m3_model or os.environ.get("CKQA_BGE_M3_MODEL", DEFAULT_BGE_M3_MODEL),
        "max_questions": max_questions,
        "pairs_total": len(pairs),
        "pairs_evaluated": len({(row["question_id"], row["mode"]) for row in evaluated}),
        "pairs_skipped": skipped,
        "thresholds": thresholds,
        "summary_rows": _summarize_by_threshold(evaluated, thresholds),
        "rows": evaluated,
    }
    output = output_path or run_dir / "semantic_threshold_calibration.md"
    output.write_text(_render_markdown(summary), encoding="utf-8")
    summary["output_path"] = str(output)
    return summary


def _load_calibration_pairs(run_dir: Path, *, max_questions: int) -> list[CalibrationPair]:
    meta = load_run_meta(run_dir)
    items = load_test_set(Path(meta["test_set_path"]))
    question_ids = select_question_ids_for_run(items, meta, run_dir)[:max_questions]

    pairs: list[CalibrationPair] = []
    for question_id in question_ids:
        item = items[question_id]
        for mode in meta.get("modes", []):
            raw = load_raw_mode_answer(run_dir, question_id, str(mode))
            pairs.append(
                CalibrationPair(
                    question_id=question_id,
                    category=item.category.value,
                    mode=str(mode),
                    answer=raw.answer,
                    reference=item.gold_answer_summary,
                )
            )
    return pairs


def _similarity_matrix(answer: str, reference: str, config: SemanticScoringConfig) -> np.ndarray:
    answer_chunks = split_semantic_chunks(answer, max_chunk_chars=config.max_chunk_chars)
    reference_chunks = split_semantic_chunks(reference, max_chunk_chars=config.max_chunk_chars)
    if not answer_chunks or not reference_chunks:
        return np.empty((0, 0))
    model_name = config.bge_m3_model or os.environ.get("CKQA_BGE_M3_MODEL", DEFAULT_BGE_M3_MODEL)
    device = config.bge_device or os.environ.get("CKQA_BGE_M3_DEVICE") or None
    answer_vectors = _encode_dense_chunks(
        answer_chunks,
        model_name=model_name,
        max_length=config.bge_max_length,
        device=device,
        use_fp16=config.bge_use_fp16,
        batch_size=config.bge_batch_size,
    )
    reference_vectors = _encode_dense_chunks(
        reference_chunks,
        model_name=model_name,
        max_length=config.bge_max_length,
        device=device,
        use_fp16=config.bge_use_fp16,
        batch_size=config.bge_batch_size,
    )
    return answer_vectors @ reference_vectors.T


def _summarize_by_threshold(evaluated: list[dict[str, Any]], thresholds: list[float]) -> list[dict[str, Any]]:
    rows: list[dict[str, Any]] = []
    for threshold in thresholds:
        subset = [row for row in evaluated if row["threshold"] == threshold]
        if not subset:
            rows.append(
                {
                    "threshold": threshold,
                    "mean_precision": 0.0,
                    "mean_recall": 0.0,
                    "mean_f1": 0.0,
                    "min_f1": 0.0,
                    "max_f1": 0.0,
                }
            )
            continue
        f1_values = [float(row["semantic_coverage_f1"]) for row in subset]
        rows.append(
            {
                "threshold": threshold,
                "mean_precision": round(mean(float(row["semantic_coverage_precision"]) for row in subset), 4),
                "mean_recall": round(mean(float(row["semantic_coverage_recall"]) for row in subset), 4),
                "mean_f1": round(mean(f1_values), 4),
                "min_f1": round(min(f1_values), 4),
                "max_f1": round(max(f1_values), 4),
            }
        )
    return rows


def _render_markdown(summary: dict[str, Any]) -> str:
    lines = [
        "# BGE-M3 语义覆盖阈值校准",
        "",
        f"- run: `{summary['run_dir']}`",
        f"- model: `{summary['model']}`",
        f"- sampled questions: `{summary['max_questions']}`",
        f"- evaluated pairs: `{summary['pairs_evaluated']}`",
        f"- skipped pairs: `{summary['pairs_skipped']}`",
        "",
        "> 本报告没有人工好/差标签，只用于检查当前语料下阈值是否过度饱和或过度严格；上线前仍需结合人工抽样复核。",
        "",
        "| threshold | mean_precision | mean_recall | mean_f1 | min_f1 | max_f1 |",
        "| --- | ---: | ---: | ---: | ---: | ---: |",
    ]
    for row in summary["summary_rows"]:
        lines.append(
            f"| {row['threshold']:.2f} | {row['mean_precision']:.4f} | {row['mean_recall']:.4f} | "
            f"{row['mean_f1']:.4f} | {row['min_f1']:.4f} | {row['max_f1']:.4f} |"
        )
    lines += [
        "",
        "## 结论",
        "",
        "- 若 `0.50` 到 `0.70` 的均值几乎不变，说明当前样本对阈值不敏感，需要增加更难的负例或人工标注样本。",
        "- 若高阈值下 `mean_f1` 大幅塌陷，日常评测应优先使用较低阈值或重新校准分块策略。",
        "- 默认 `0.62` 仍是候选值，不应被解释为已经完成线上路由校准。",
        "",
    ]
    return "\n".join(lines)


def _parse_thresholds(raw: str) -> list[float]:
    return [float(part.strip()) for part in raw.split(",") if part.strip()]


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--run-dir", type=Path, required=True)
    parser.add_argument("--thresholds", default="0.50,0.60,0.62,0.65,0.70")
    parser.add_argument("--max-questions", type=int, default=10)
    parser.add_argument("--bge-model", default=None)
    parser.add_argument("--bge-device", default=None, help="BGE-M3 device, for example `cuda` or `cpu`.")
    parser.add_argument("--bge-batch-size", type=int, default=8)
    parser.add_argument("--bge-fp16", action="store_true", help="Use fp16 for BGE-M3, recommended on CUDA GPUs.")
    parser.add_argument("--max-chunk-chars", type=int, default=260)
    parser.add_argument("--output", type=Path, default=None)
    args = parser.parse_args()

    summary = calibrate_semantic_thresholds(
        args.run_dir,
        thresholds=_parse_thresholds(args.thresholds),
        max_questions=args.max_questions,
        config=SemanticScoringConfig(
            bge_m3_model=args.bge_model,
            bge_device=args.bge_device,
            bge_use_fp16=args.bge_fp16,
            bge_batch_size=args.bge_batch_size,
            max_chunk_chars=args.max_chunk_chars,
        ),
        output_path=args.output,
    )
    print(f"wrote {summary['output_path']}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
