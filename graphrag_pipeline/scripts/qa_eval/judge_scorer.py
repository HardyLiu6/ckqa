from __future__ import annotations

import argparse
import csv
import json
import logging
import os
import re
import sys
from collections import defaultdict
from dataclasses import dataclass, field
from pathlib import Path
from statistics import mean
from typing import Any

from graphrag_pipeline.scripts.qa_eval.baseline_scorer import _load_modes_from_meta
from graphrag_pipeline.scripts.qa_eval.graphrag_client import (
    GraphRagApiError,
    OpenAICompatibleClient,
    QueryResult,
)
from graphrag_pipeline.scripts.qa_eval.judge_prompts import (
    FAITHFULNESS_PROMPT,
    SEMANTIC_PROMPT,
)
from graphrag_pipeline.scripts.qa_eval.test_set_schema import TEXT_UNIT_ID_PREFIX_LEN
from graphrag_pipeline.scripts.qa_eval.text_unit_lookup import (
    TextUnitLookup,
    load_text_unit_lookup,
)


LOGGER = logging.getLogger(__name__)
JUDGE_FALLBACK_SENTINEL = -1.0

JUDGE_METRICS: tuple[str, ...] = (
    "semantic_correctness",
    "faithfulness",
    "retrieval_precision",
)

_REF_BLOCK_PATTERNS: tuple[re.Pattern[str], ...] = (
    re.compile(r"Text Units?\s*\(([^)]*)\)", re.IGNORECASE),
    re.compile(r"text_units?\s*\(([^)]*)\)", re.IGNORECASE),
)
_REF_TOKEN_RE = re.compile(r"[0-9a-fA-F]{8,}")


@dataclass
class JudgeSummary:
    per_question: dict[str, dict[str, dict[str, Any]]] = field(default_factory=dict)
    per_mode: dict[str, dict[str, float]] = field(default_factory=dict)
    per_category_mode: dict[str, dict[str, dict[str, float]]] = field(default_factory=dict)
    modes: list[str] = field(default_factory=list)


@dataclass(slots=True)
class JudgeRunner:
    client: OpenAICompatibleClient
    text_unit_lookup: TextUnitLookup
    judge_model: str
    api_key: str | None
    max_parse_retries: int = 2

    def score_pair(
        self,
        *,
        prompt: str,
        kind: str,
        question_id: str,
        mode: str,
        judge_raw_dir: Path,
    ) -> dict[str, Any] | None:
        attempts = 0
        last_raw: QueryResult | None = None
        while attempts <= self.max_parse_retries:
            attempts += 1
            try:
                result = self.client.query(
                    model=self.judge_model,
                    prompt=prompt,
                    api_key=self.api_key,
                )
            except (GraphRagApiError, ValueError) as exc:
                LOGGER.warning("judge call failed (%s/%s/%s): %s", question_id, mode, kind, exc)
                continue
            last_raw = result
            try:
                payload = json.loads(_extract_json(result.answer))
            except (json.JSONDecodeError, ValueError) as exc:
                LOGGER.warning(
                    "judge parse failed (%s/%s/%s) attempt=%d: %s",
                    question_id,
                    mode,
                    kind,
                    attempts,
                    exc,
                )
                continue
            _write_judge_raw(
                judge_raw_dir,
                question_id=question_id,
                mode=mode,
                kind=kind,
                prompt=prompt,
                response=result.answer,
                parsed=payload,
            )
            return payload

        if last_raw is not None:
            _write_judge_raw(
                judge_raw_dir,
                question_id=question_id,
                mode=mode,
                kind=kind,
                prompt=prompt,
                response=last_raw.answer,
                parsed=None,
            )
        return None


def extract_text_unit_refs_from_answer(answer: str) -> list[str]:
    out: list[str] = []
    for pattern in _REF_BLOCK_PATTERNS:
        for block in pattern.findall(answer):
            for token in _REF_TOKEN_RE.findall(block):
                prefix = token[:TEXT_UNIT_ID_PREFIX_LEN]
                if prefix and prefix not in out:
                    out.append(prefix)
    return out


def score_run_with_judge(run_dir: Path | str, *, runner: JudgeRunner) -> JudgeSummary:
    run_path = Path(run_dir)
    raw_dir = run_path / "raw"
    raw_files = sorted(raw_dir.glob("Q*.json"))
    if not raw_files:
        raise FileNotFoundError(f"no raw items under {raw_dir}")

    modes = _load_modes_from_meta(run_path)
    judge_raw_dir = run_path / "judge_raw"
    summary = JudgeSummary(modes=modes)
    per_mode_values: dict[str, dict[str, list[float]]] = {
        mode: {metric: [] for metric in JUDGE_METRICS} for mode in modes
    }
    per_category_values: dict[str, dict[str, dict[str, list[float]]]] = defaultdict(
        lambda: {mode: {metric: [] for metric in JUDGE_METRICS} for mode in modes}
    )

    for raw_file in raw_files:
        item = json.loads(raw_file.read_text(encoding="utf-8"))
        item_id = str(item.get("id") or item.get("question_id") or raw_file.stem)
        category = str(item["category"])
        summary.per_question[item_id] = {}
        for mode in modes:
            scores = _score_item(item=item, item_id=item_id, mode=mode, runner=runner, judge_raw_dir=judge_raw_dir)
            summary.per_question[item_id][mode] = scores
            for metric in JUDGE_METRICS:
                value = float(scores[metric])
                if value == JUDGE_FALLBACK_SENTINEL:
                    continue
                per_mode_values[mode][metric].append(value)
                per_category_values[category][mode][metric].append(value)

    for mode in modes:
        summary.per_mode[mode] = _summarize(per_mode_values[mode])
    for category, mode_values in per_category_values.items():
        summary.per_category_mode[category] = {
            mode: _summarize(metrics) for mode, metrics in mode_values.items()
        }

    _write_outputs(run_path, summary, runner.judge_model)
    return summary


def _score_item(
    *,
    item: dict[str, Any],
    item_id: str,
    mode: str,
    runner: JudgeRunner,
    judge_raw_dir: Path,
) -> dict[str, Any]:
    payload = item.get("modes", {}).get(mode, {})
    if payload.get("error"):
        return {
            "semantic_correctness": JUDGE_FALLBACK_SENTINEL,
            "faithfulness": JUDGE_FALLBACK_SENTINEL,
            "retrieval_precision": 0.0,
            "error": True,
        }

    answer = str(payload.get("answer") or "")
    question = str(item.get("question") or "")
    gold_answer_summary = str(item.get("gold_answer_summary") or "")
    gold_refs = [str(ref)[:TEXT_UNIT_ID_PREFIX_LEN] for ref in item.get("gold_text_unit_ids", []) if str(ref)]

    semantic_payload = runner.score_pair(
        prompt=SEMANTIC_PROMPT.format(
            question=question,
            gold_answer_summary=gold_answer_summary,
            answer=answer,
        ),
        kind="semantic",
        question_id=item_id,
        mode=mode,
        judge_raw_dir=judge_raw_dir,
    )
    semantic_score = _coerce_semantic_score(semantic_payload)

    if gold_refs:
        faithfulness_payload = runner.score_pair(
            prompt=FAITHFULNESS_PROMPT.format(
                question=question,
                evidence=runner.text_unit_lookup.render_for_prompt(gold_refs),
                answer=answer,
            ),
            kind="faithfulness",
            question_id=item_id,
            mode=mode,
            judge_raw_dir=judge_raw_dir,
        )
        faithfulness = _coerce_faithfulness(faithfulness_payload)
    else:
        faithfulness = JUDGE_FALLBACK_SENTINEL

    return {
        "semantic_correctness": semantic_score,
        "faithfulness": faithfulness,
        "retrieval_precision": _retrieval_precision(answer, gold_refs),
        "error": False,
    }


def _retrieval_precision(answer: str, gold_refs: list[str]) -> float:
    if not gold_refs:
        return 0.0
    answer_refs = set(extract_text_unit_refs_from_answer(answer))
    gold_set = set(gold_refs)
    return round(len(answer_refs & gold_set) / len(gold_set), 4)


def _coerce_semantic_score(payload: dict[str, Any] | None) -> float:
    if payload is None:
        return JUDGE_FALLBACK_SENTINEL
    try:
        value = float(payload.get("score", JUDGE_FALLBACK_SENTINEL))
    except (TypeError, ValueError):
        return JUDGE_FALLBACK_SENTINEL
    return value if value in {0.0, 0.5, 1.0} else JUDGE_FALLBACK_SENTINEL


def _coerce_faithfulness(payload: dict[str, Any] | None) -> float:
    if payload is None:
        return JUDGE_FALLBACK_SENTINEL
    value = payload.get("is_supported")
    if isinstance(value, bool):
        return 1.0 if value else 0.0
    return JUDGE_FALLBACK_SENTINEL


def _summarize(metrics: dict[str, list[float]]) -> dict[str, float]:
    return {
        metric: round(mean(values), 4) if values else 0.0
        for metric, values in metrics.items()
    }


def _extract_json(text: str) -> str:
    cleaned = text.strip()
    if cleaned.startswith("```"):
        cleaned = re.sub(r"^```[a-zA-Z]*", "", cleaned).strip()
        if cleaned.endswith("```"):
            cleaned = cleaned[:-3].strip()
    start = cleaned.find("{")
    end = cleaned.rfind("}")
    if start < 0 or end <= start:
        raise ValueError("no JSON object found")
    return cleaned[start : end + 1]


def _safe(text: str) -> str:
    return re.sub(r"[^a-zA-Z0-9._-]+", "-", text)


def _write_judge_raw(
    judge_raw_dir: Path,
    *,
    question_id: str,
    mode: str,
    kind: str,
    prompt: str,
    response: str,
    parsed: dict[str, Any] | None,
) -> None:
    judge_raw_dir.mkdir(parents=True, exist_ok=True)
    (judge_raw_dir / f"{question_id}_{_safe(mode)}_{kind}.json").write_text(
        json.dumps(
            {"prompt": prompt, "response": response, "parsed": parsed},
            ensure_ascii=False,
            indent=2,
        )
        + "\n",
        encoding="utf-8",
    )


def _write_outputs(run_dir: Path, summary: JudgeSummary, judge_model: str) -> None:
    payload = {
        "per_question": summary.per_question,
        "per_mode": summary.per_mode,
        "per_category_mode": summary.per_category_mode,
        "modes": summary.modes,
        "judge_model": judge_model,
        "fallback_sentinel": JUDGE_FALLBACK_SENTINEL,
    }
    (run_dir / "judge_scoring.json").write_text(
        json.dumps(payload, ensure_ascii=False, indent=2) + "\n",
        encoding="utf-8",
    )
    _write_csv(run_dir / "judge_scoring.csv", summary)
    _write_markdown(run_dir / "judge_scoring.md", summary, judge_model)


def _write_csv(path: Path, summary: JudgeSummary) -> None:
    with path.open("w", encoding="utf-8", newline="") as handle:
        writer = csv.writer(handle)
        writer.writerow(["question_id", "mode", *JUDGE_METRICS, "error"])
        for question_id, mode_rows in summary.per_question.items():
            for mode in summary.modes:
                row = mode_rows[mode]
                writer.writerow(
                    [question_id, mode]
                    + [row.get(metric, 0.0) for metric in JUDGE_METRICS]
                    + [int(bool(row.get("error")))]
                )


def _write_markdown(path: Path, summary: JudgeSummary, judge_model: str) -> None:
    lines = [
        "# QA Baseline 裁判评分",
        "",
        f"裁判模型：`{judge_model}`",
        "",
        "| mode | semantic_correctness | faithfulness | retrieval_precision |",
        "| --- | ---: | ---: | ---: |",
    ]
    for mode in summary.modes:
        row = summary.per_mode[mode]
        lines.append(
            f"| {mode} | {row['semantic_correctness']:.4f} | {row['faithfulness']:.4f} | {row['retrieval_precision']:.4f} |"
        )
    path.write_text("\n".join(lines) + "\n", encoding="utf-8")


def main(argv: list[str] | None = None) -> int:
    parser = argparse.ArgumentParser(description="Score a QA baseline run with an LLM judge.")
    parser.add_argument("--run-dir", type=Path, required=True)
    parser.add_argument(
        "--text-units-path",
        type=Path,
        default=Path("graphrag_pipeline/output/text_units.parquet"),
    )
    parser.add_argument(
        "--judge-base-url",
        default=os.environ.get("GRAPHRAG_JUDGE_BASE_URL", os.environ.get("GRAPHRAG_API_BASE", "")),
    )
    parser.add_argument("--judge-model", default=os.environ.get("GRAPHRAG_JUDGE_MODEL", "gpt-4o-mini"))
    parser.add_argument(
        "--judge-api-key",
        default=os.environ.get("GRAPHRAG_JUDGE_API_KEY", os.environ.get("GRAPHRAG_CHAT_API_KEY", "")),
    )
    parser.add_argument("--max-retries", type=int, default=3)
    parser.add_argument("--request-timeout", type=float, default=120.0)
    parser.add_argument("--max-parse-retries", type=int, default=2)
    args = parser.parse_args(argv)

    logging.basicConfig(level=logging.INFO, format="%(asctime)s %(levelname)s %(message)s")
    if not args.judge_base_url:
        raise SystemExit("missing judge base url; set GRAPHRAG_JUDGE_BASE_URL or pass --judge-base-url")

    client = OpenAICompatibleClient(
        base_url=args.judge_base_url,
        request_timeout_seconds=args.request_timeout,
        max_retries=args.max_retries,
        allow_arbitrary_models=True,
    )
    runner = JudgeRunner(
        client=client,
        text_unit_lookup=load_text_unit_lookup(args.text_units_path),
        judge_model=args.judge_model,
        api_key=args.judge_api_key or None,
        max_parse_retries=args.max_parse_retries,
    )
    summary = score_run_with_judge(args.run_dir, runner=runner)
    print(json.dumps(summary.per_mode, ensure_ascii=False, indent=2))
    return 0


if __name__ == "__main__":  # pragma: no cover
    sys.exit(main())
