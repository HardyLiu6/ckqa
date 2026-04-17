#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
结果落盘工具
============
"""

from __future__ import annotations

import json
from pathlib import Path
from typing import Any, Iterable

from extraction_schema import StructuredExtractionResult


def ensure_candidate_output_paths(*, root: Path, candidate_name: str, overwrite: bool) -> dict[str, Path]:
    paths = {
        "eval": (root / "results" / "extraction_eval" / f"{candidate_name}.json").resolve(),
        "raw": (root / "results" / "extraction_raw" / f"{candidate_name}.jsonl").resolve(),
        "log": (root / "results" / "logs" / f"extraction_{candidate_name}.log").resolve(),
    }
    _ensure_writable((paths["eval"], paths["raw"]), overwrite=overwrite)
    return paths


def ensure_parse_error_path(*, root: Path, overwrite: bool) -> Path:
    path = (root / "results" / "errors" / "extraction_parse_errors.jsonl").resolve()
    _ensure_writable([path], overwrite=overwrite)
    return path


def write_candidate_outputs(
    *,
    root: Path,
    candidate_name: str,
    results: list[StructuredExtractionResult],
    samples_file: Path,
    manifest_file: Path,
    model: str,
    overwrite: bool,
) -> dict[str, Any]:
    paths = ensure_candidate_output_paths(root=root, candidate_name=candidate_name, overwrite=overwrite)

    summary = _summarize_results(results)
    eval_payload = {
        "task": "candidate_extraction",
        "candidate": candidate_name,
        "model": model,
        "samples_file": str(samples_file.resolve()),
        "manifest_file": str(manifest_file.resolve()),
        "summary": summary,
        "results": [result.model_dump(mode="json") for result in results],
    }
    _write_json(paths["eval"], eval_payload)
    _write_jsonl(paths["raw"], _iter_raw_records(results))
    return {"paths": paths, "summary": summary}


def write_parse_errors(
    *,
    path: Path,
    results: list[StructuredExtractionResult],
) -> None:
    parse_errors = [
        {
            "sample_id": result.sample_id,
            "candidate": result.candidate,
            "status": result.status,
            "error": result.error,
            "parser_error_code": result.parser_error_code,
            "llm_debug": result.llm_debug,
            "raw_output": result.raw_output,
        }
        for result in results
        if result.status == "parse_error"
    ]
    _write_jsonl(path, parse_errors)


def _iter_raw_records(results: list[StructuredExtractionResult]) -> Iterable[dict[str, Any]]:
    for result in results:
        yield {
            "sample_id": result.sample_id,
            "candidate": result.candidate,
            "status": result.status,
            "parser_error_code": result.parser_error_code,
            "llm_debug": result.llm_debug,
            "raw_output": result.raw_output,
            "error": result.error,
        }


def _summarize_results(results: list[StructuredExtractionResult]) -> dict[str, int]:
    summary = {"total": len(results), "success": 0, "parse_error": 0, "llm_error": 0}
    for result in results:
        if result.status in summary:
            summary[result.status] += 1
    return summary


def _ensure_writable(paths: Iterable[Path], *, overwrite: bool) -> None:
    for path in paths:
        path.parent.mkdir(parents=True, exist_ok=True)
        if path.exists() and not overwrite:
            raise FileExistsError(f"目标文件已存在，若要覆盖请传 --overwrite：{path}")


def _write_json(path: Path, payload: dict[str, Any]) -> None:
    path.write_text(json.dumps(payload, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")


def _write_jsonl(path: Path, records: Iterable[dict[str, Any]]) -> None:
    with path.open("w", encoding="utf-8") as handle:
        for record in records:
            handle.write(json.dumps(record, ensure_ascii=False) + "\n")
