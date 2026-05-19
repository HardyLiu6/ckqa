from __future__ import annotations

import argparse
import csv
import json
import re
import sys
from collections import defaultdict
from pathlib import Path
from statistics import mean
from typing import Any


TIMEOUT_ERROR_PATTERN = re.compile(
    r"\b(readtimeout|timeouterror|timeoutexpired)\b|\bread timed out\b|\btimed out\b"
)


def generate_latency_report(run_dir: Path | str) -> dict[str, Any]:
    run_path = Path(run_dir)
    meta = _read_json(run_path / "run_meta.json")
    modes = [str(mode) for mode in meta.get("modes", [])]
    if not modes:
        raise ValueError(f"run_meta.json has no modes list: {run_path / 'run_meta.json'}")

    rows = _load_rows(run_path / "raw", modes)
    payload = {
        "run_id": meta.get("run_id", run_path.name),
        "total_items": meta.get("total_items", len({row["question_id"] for row in rows})),
        "modes": modes,
        "per_mode": _summarize_by_key(rows, modes, "mode"),
        "per_category_mode": _summarize_by_category_mode(rows, modes),
        "slowest_rows": sorted(rows, key=lambda row: row["elapsed_seconds"], reverse=True)[:20],
    }
    _write_json(run_path / "latency_breakdown.json", payload)
    _write_csv(run_path / "latency_breakdown.csv", rows)
    _write_markdown(run_path / "latency_breakdown.md", payload)
    return payload


def _load_rows(raw_dir: Path, modes: list[str]) -> list[dict[str, Any]]:
    raw_files = sorted(raw_dir.glob("Q*.json"))
    if not raw_files:
        raise FileNotFoundError(f"no raw items under {raw_dir}")
    rows: list[dict[str, Any]] = []
    for raw_file in raw_files:
        item = _read_json(raw_file)
        question_id = str(item.get("id") or item.get("question_id") or raw_file.stem)
        category = str(item.get("category", ""))
        for mode in modes:
            payload = item.get("modes", {}).get(mode, {})
            error = str(payload.get("error", ""))
            error_type = str(payload.get("error_type", ""))
            rows.append(
                {
                    "question_id": question_id,
                    "category": category,
                    "mode": mode,
                    "elapsed_seconds": round(float(payload.get("elapsed_seconds") or 0.0), 4),
                    "success": not bool(error),
                    "error": error,
                    "error_type": error_type,
                    "timeout_like": _is_timeout_like(error, error_type),
                    "answer_chars": len(str(payload.get("answer") or "")),
                    "total_tokens": payload.get("total_tokens"),
                }
            )
    return rows


def _summarize_by_key(rows: list[dict[str, Any]], modes: list[str], key: str) -> dict[str, dict[str, Any]]:
    grouped: dict[str, list[dict[str, Any]]] = defaultdict(list)
    for row in rows:
        grouped[str(row[key])].append(row)
    return {mode: _summarize_rows(grouped.get(mode, [])) for mode in modes}


def _summarize_by_category_mode(rows: list[dict[str, Any]], modes: list[str]) -> dict[str, dict[str, Any]]:
    categories = sorted({str(row["category"]) for row in rows})
    out: dict[str, dict[str, Any]] = {}
    for category in categories:
        category_rows = [row for row in rows if row["category"] == category]
        out[category] = {
            mode: _summarize_rows([row for row in category_rows if row["mode"] == mode])
            for mode in modes
        }
    return out


def _summarize_rows(rows: list[dict[str, Any]]) -> dict[str, Any]:
    elapsed = [float(row["elapsed_seconds"]) for row in rows]
    success_elapsed = [float(row["elapsed_seconds"]) for row in rows if row["success"]]
    error_elapsed = [float(row["elapsed_seconds"]) for row in rows if not row["success"]]
    return {
        "total_count": len(rows),
        "success_count": sum(1 for row in rows if row["success"]),
        "error_count": sum(1 for row in rows if not row["success"]),
        "timeout_like_error_count": sum(1 for row in rows if row["timeout_like"]),
        "mean_seconds": _rounded_mean(elapsed),
        "success_mean_seconds": _rounded_mean(success_elapsed),
        "error_mean_seconds": _rounded_mean(error_elapsed),
        "p50_seconds": _percentile(elapsed, 0.50),
        "p95_seconds": _percentile(elapsed, 0.95),
        "max_seconds": round(max(elapsed), 4) if elapsed else 0.0,
        "min_seconds": round(min(elapsed), 4) if elapsed else 0.0,
    }


def _rounded_mean(values: list[float]) -> float:
    return round(mean(values), 4) if values else 0.0


def _percentile(values: list[float], fraction: float) -> float:
    if not values:
        return 0.0
    ordered = sorted(values)
    index = min(len(ordered) - 1, max(0, round((len(ordered) - 1) * fraction)))
    return round(ordered[index], 4)


def _is_timeout_like(error: str, error_type: str) -> bool:
    text = f"{error_type} {error}".casefold()
    return bool(TIMEOUT_ERROR_PATTERN.search(text))


def _write_csv(path: Path, rows: list[dict[str, Any]]) -> None:
    fieldnames = [
        "question_id",
        "category",
        "mode",
        "elapsed_seconds",
        "success",
        "timeout_like",
        "answer_chars",
        "total_tokens",
        "error_type",
        "error",
    ]
    with path.open("w", encoding="utf-8", newline="") as handle:
        writer = csv.DictWriter(handle, fieldnames=fieldnames)
        writer.writeheader()
        writer.writerows(rows)


def _write_markdown(path: Path, payload: dict[str, Any]) -> None:
    lines = [
        f"# QA Baseline 耗时分解 - {payload['run_id']}",
        "",
        "## 按模式汇总",
        "",
        "| mode | total | success | error | timeout_like | mean_s | success_mean_s | p95_s | max_s |",
        "| --- | ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: |",
    ]
    for mode in payload["modes"]:
        row = payload["per_mode"][mode]
        lines.append(
            "| "
            + " | ".join(
                [
                    mode,
                    str(row["total_count"]),
                    str(row["success_count"]),
                    str(row["error_count"]),
                    str(row["timeout_like_error_count"]),
                    _fmt(row["mean_seconds"]),
                    _fmt(row["success_mean_seconds"]),
                    _fmt(row["p95_seconds"]),
                    _fmt(row["max_seconds"]),
                ]
            )
            + " |"
        )
    lines += ["", "## 最慢请求 Top 20", ""]
    lines += [
        "| question_id | category | mode | elapsed_s | success | error_type |",
        "| --- | --- | --- | ---: | --- | --- |",
    ]
    for row in payload["slowest_rows"]:
        lines.append(
            "| "
            + " | ".join(
                [
                    str(row["question_id"]),
                    str(row["category"]),
                    str(row["mode"]),
                    _fmt(row["elapsed_seconds"]),
                    str(row["success"]),
                    str(row["error_type"]),
                ]
            )
            + " |"
        )
    path.write_text("\n".join(lines) + "\n", encoding="utf-8")


def _write_json(path: Path, payload: dict[str, Any]) -> None:
    path.write_text(json.dumps(payload, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")


def _read_json(path: Path) -> dict[str, Any]:
    return json.loads(path.read_text(encoding="utf-8"))


def _fmt(value: Any) -> str:
    return f"{float(value):.4f}" if isinstance(value, int | float) else str(value)


def main(argv: list[str] | None = None) -> int:
    parser = argparse.ArgumentParser(description="Generate latency breakdown for a QA baseline run.")
    parser.add_argument("--run-dir", type=Path, required=True)
    args = parser.parse_args(argv)
    payload = generate_latency_report(args.run_dir)
    print(json.dumps(payload["per_mode"], ensure_ascii=False, indent=2))
    return 0


if __name__ == "__main__":  # pragma: no cover
    sys.exit(main())
