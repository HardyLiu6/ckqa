from __future__ import annotations

import json
from dataclasses import dataclass
from pathlib import Path
from typing import Any

import pandas as pd

from graphrag_pipeline.scripts.qa_eval.test_set_schema import QaTestItem, TEXT_UNIT_ID_PREFIX_LEN


DEFAULT_TEXT_UNITS_PATH = Path("graphrag_pipeline/output/text_units.parquet")


@dataclass(frozen=True, slots=True)
class RawModeAnswer:
    answer: str
    elapsed_seconds: float
    error_count: int
    error_type: str = ""
    error_message: str = ""


def safe_mode(mode: str) -> str:
    return mode.replace(":", "-").replace("/", "-")


def load_run_meta(run_dir: Path) -> dict[str, Any]:
    return json.loads((run_dir / "run_meta.json").read_text(encoding="utf-8"))


def load_test_set(path: Path) -> dict[str, QaTestItem]:
    items: dict[str, QaTestItem] = {}
    for raw in path.read_text(encoding="utf-8").splitlines():
        if raw.strip():
            item = QaTestItem.model_validate_json(raw)
            items[item.id] = item
    return items


def load_raw_mode_answer(run_dir: Path, question_id: str, mode: str) -> RawModeAnswer:
    current_path = run_dir / "raw" / f"{question_id}.json"
    if current_path.exists():
        payload = json.loads(current_path.read_text(encoding="utf-8"))
        modes = payload.get("modes", {})
        if mode not in modes:
            return RawModeAnswer(
                answer="",
                elapsed_seconds=0.0,
                error_count=1,
                error_type="missing_mode",
                error_message=f"raw answer exists for {question_id}, but mode is missing: {mode}",
            )
        mode_payload = modes.get(mode, {})
        error = str(mode_payload.get("error", "") or "")
        return RawModeAnswer(
            answer=str(mode_payload.get("answer", "")),
            elapsed_seconds=float(mode_payload.get("elapsed_seconds") or 0.0),
            error_count=1 if error else 0,
            error_type=str(mode_payload.get("error_type", "") or ("error" if error else "")),
            error_message=error,
        )

    legacy_path = run_dir / "raw" / f"{question_id}_{safe_mode(mode)}.json"
    if not legacy_path.exists():
        return RawModeAnswer(
            answer="",
            elapsed_seconds=0.0,
            error_count=1,
            error_type="missing_raw",
            error_message=f"raw answer file is missing for {question_id} / {mode}",
        )
    payload = json.loads(legacy_path.read_text(encoding="utf-8"))
    error = str(payload.get("error", "") or "")
    return RawModeAnswer(
        answer=str(payload.get("answer", "")),
        elapsed_seconds=float(payload.get("elapsed_seconds") or 0.0),
        error_count=1 if error else 0,
        error_type=str(payload.get("error_type", "") or ("error" if error else "")),
        error_message=error,
    )


def infer_question_ids_from_raw(run_dir: Path) -> list[str]:
    raw_dir = run_dir / "raw"
    if not raw_dir.exists():
        return []
    question_ids: list[str] = []
    for raw_path in sorted(raw_dir.glob("Q*.json")):
        question_id = raw_path.stem.split("_", 1)[0]
        if question_id and question_id not in question_ids:
            question_ids.append(question_id)
    return question_ids


def coerce_question_ids(value: Any) -> list[str]:
    if not isinstance(value, list):
        return []
    return [str(item).strip() for item in value if str(item).strip()]


def fallback_question_ids_from_limits(items: dict[str, Any], meta: dict[str, Any]) -> list[str]:
    limit = meta.get("total_items", meta.get("max_items"))
    try:
        count = int(limit)
    except (TypeError, ValueError):
        return []
    if count <= 0:
        return []
    return list(items)[:count]


def select_question_ids_for_run(
    items: dict[str, Any],
    meta: dict[str, Any],
    run_dir: Path,
) -> list[str]:
    question_ids = coerce_question_ids(meta.get("question_ids"))
    if not question_ids:
        question_ids = infer_question_ids_from_raw(run_dir)
    if not question_ids:
        question_ids = fallback_question_ids_from_limits(items, meta)
    if not question_ids:
        question_ids = list(items)
    return [question_id for question_id in question_ids if question_id in items]


def resolve_text_units_path(run_dir: Path, explicit_path: Path | None = None) -> Path:
    if explicit_path is not None:
        resolved = explicit_path
    else:
        meta = load_run_meta(run_dir)
        index_output_dir = str(meta.get("index_output_dir") or "").strip()
        resolved = Path(index_output_dir) / "text_units.parquet" if index_output_dir else DEFAULT_TEXT_UNITS_PATH
    if not resolved.exists():
        raise FileNotFoundError(f"resolved text_units.parquet does not exist: {resolved}")
    return resolved


def audit_text_unit_prefix_collisions(text_units_path: Path) -> dict[str, list[str]]:
    frame = pd.read_parquet(text_units_path, columns=["id"])
    buckets: dict[str, list[str]] = {}
    for raw_id in frame["id"].astype(str):
        prefix = raw_id[:TEXT_UNIT_ID_PREFIX_LEN]
        bucket = buckets.setdefault(prefix, [])
        if raw_id not in bucket:
            bucket.append(raw_id)
    return {prefix: ids for prefix, ids in buckets.items() if len(ids) > 1}
