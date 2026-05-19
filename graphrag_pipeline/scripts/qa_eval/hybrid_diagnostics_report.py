from __future__ import annotations

import argparse
import json
import re
from pathlib import Path
from typing import Any


HYBRID_V0_MODEL = "graphrag-hybrid-v0-search:latest"
_DIAGNOSTIC_REF_RE = re.compile(r"^[A-Za-z0-9][A-Za-z0-9_-]{7,}$")


def build_hybrid_diagnostics_rows(run_dirs: list[Path]) -> list[dict[str, Any]]:
    rows: list[dict[str, Any]] = []
    for run_dir in run_dirs:
        for raw_path in sorted((Path(run_dir) / "raw").glob("Q*.json")):
            payload = json.loads(raw_path.read_text(encoding="utf-8"))
            mode_payload = payload.get("modes", {}).get(HYBRID_V0_MODEL, {})
            diagnostics = _extract_diagnostics(mode_payload)
            rows.append(
                {
                    "run_id": Path(run_dir).name,
                    "question_id": str(payload.get("question_id") or payload.get("id") or raw_path.stem),
                    "category": str(payload.get("category") or ""),
                    "layer": str(diagnostics.get("layer") or ""),
                    "used_local_fallback": bool(diagnostics.get("used_local_fallback", False)),
                    "guardrail_status": str(diagnostics.get("guardrail_status") or "missing"),
                    "guardrail_score": _float_or_none(diagnostics.get("guardrail_score")),
                    "bm25_evidence_count": _int_or_none(diagnostics.get("low_evidence_count")),
                    "fused_evidence_count": len(_valid_ref_list(diagnostics.get("fused_evidence_refs"))),
                    "fused_evidence_sources": _format_source_counts(diagnostics.get("fused_evidence_sources")),
                    "high_evidence_count": _int_or_none(diagnostics.get("high_evidence_count")),
                    "synthesis_reason": str(diagnostics.get("synthesis_reason") or ""),
                    "local_fallback_enabled": bool(diagnostics.get("local_fallback_enabled", False)),
                    "elapsed_seconds": _float_or_none(mode_payload.get("elapsed_seconds")),
                    "answer_chars": len(str(mode_payload.get("answer") or "")),
                    "fallback_reason": _fallback_reason(diagnostics),
                }
            )
    return rows


def write_hybrid_diagnostics_report(run_dirs: list[Path], *, output_path: Path) -> Path:
    rows = build_hybrid_diagnostics_rows(run_dirs)
    output_path.parent.mkdir(parents=True, exist_ok=True)
    output_path.write_text(_render_markdown(rows, run_dirs), encoding="utf-8")
    return output_path


def _extract_diagnostics(mode_payload: dict[str, Any]) -> dict[str, Any]:
    direct = mode_payload.get("hybrid_diagnostics")
    if isinstance(direct, dict):
        return dict(direct)
    raw = mode_payload.get("raw")
    if isinstance(raw, dict) and isinstance(raw.get("hybrid_diagnostics"), dict):
        return dict(raw["hybrid_diagnostics"])
    return {}


def _fallback_reason(diagnostics: dict[str, Any]) -> str:
    if not diagnostics:
        return "缺少 hybrid_diagnostics，无法判断 fallback 触发原因。"
    errors = diagnostics.get("errors") or []
    policy_reasons = [str(reason) for reason in diagnostics.get("fallback_reasons") or []]
    policy_text = f"触发原因：{', '.join(policy_reasons)}；" if policy_reasons else ""
    if errors:
        return f"diagnostics 记录错误：{'; '.join(str(error) for error in errors)}"
    status = str(diagnostics.get("guardrail_status") or "missing")
    score = _float_or_none(diagnostics.get("guardrail_score"))
    synthesis_reason = str(diagnostics.get("synthesis_reason") or "")
    local_enabled = bool(diagnostics.get("local_fallback_enabled", False))
    if bool(diagnostics.get("used_local_fallback")):
        reason_note = f"synthesis_reason={synthesis_reason}；" if synthesis_reason else ""
        return f"{policy_text}{reason_note}已触发 Local fallback；最终 guardrail_status={status}, score={_format_value(score)}。"
    if policy_reasons and bool(diagnostics.get("synthesis_attempted")):
        local_note = "Local fallback 已启用" if local_enabled else "Local fallback 策略禁用"
        reason_note = f"synthesis_reason={synthesis_reason}；" if synthesis_reason else ""
        return f"{policy_text}{reason_note}已尝试 synthesis，未触发 Local fallback（{local_note}）；最终 guardrail_status={status}, score={_format_value(score)}。"
    if status == "pass":
        return f"guardrail_status=pass，答案-证据支撑分 {_format_value(score)} 达到阈值，未触发 Local fallback。"
    return f"guardrail_status={status}，但未触发 Local fallback；需要检查 orchestration 分支。"


def _render_markdown(rows: list[dict[str, Any]], run_dirs: list[Path]) -> str:
    fallback_count = sum(1 for row in rows if row["used_local_fallback"])
    lines = [
        "# Hybrid v0 Diagnostics Report",
        "",
        "## 数据来源",
        "",
        *[f"- `{Path(run_dir)}`" for run_dir in run_dirs],
        "",
        "## 摘要",
        "",
        f"- questions: {len(rows)}",
        f"- local fallback: {fallback_count}",
        "",
        "## Per-question Diagnostics",
        "",
        "| question_id | category | layer | local_fallback | guardrail | BM25 evidence | high evidence | elapsed_s | answer_chars | reason |",
        "| --- | --- | --- | ---: | ---: | ---: | ---: | ---: | ---: | --- |",
    ]
    for row in rows:
        lines.append(
            "| {question_id} | {category} | {layer} | {fallback} | {guardrail} | {bm25} / fused {fused} ({sources}) | {high} | {elapsed} | {chars} | {reason} |".format(
                question_id=row["question_id"],
                category=row["category"],
                layer=row["layer"],
                fallback=str(row["used_local_fallback"]).lower(),
                guardrail=_format_value(row["guardrail_score"]),
                bm25=_format_value(row["bm25_evidence_count"]),
                fused=_format_value(row["fused_evidence_count"]),
                sources=row["fused_evidence_sources"],
                high=_format_value(row["high_evidence_count"]),
                elapsed=_format_value(row["elapsed_seconds"]),
                chars=_format_value(row["answer_chars"]),
                reason=str(row["fallback_reason"]).replace("|", "/"),
            )
        )
    lines.append("")
    return "\n".join(lines)


def _float_or_none(value: object) -> float | None:
    try:
        return float(value)  # type: ignore[arg-type]
    except (TypeError, ValueError):
        return None


def _int_or_none(value: object) -> int | None:
    try:
        return int(value)  # type: ignore[arg-type]
    except (TypeError, ValueError):
        return None


def _list_or_empty(value: object) -> list[object]:
    return value if isinstance(value, list) else []


def _valid_ref_list(value: object) -> list[str]:
    return [str(ref).strip() for ref in _list_or_empty(value) if _DIAGNOSTIC_REF_RE.fullmatch(str(ref).strip())]


def _format_source_counts(value: object) -> str:
    if not isinstance(value, dict):
        return ""
    preferred = ["bm25", "basic-citation"]
    ordered = [key for key in preferred if key in value]
    ordered.extend(key for key in sorted(value) if key not in ordered)
    return ",".join(f"{key}={value[key]}" for key in ordered)


def _format_value(value: object) -> str:
    if value is None:
        return ""
    if isinstance(value, float):
        return f"{value:.4f}".rstrip("0").rstrip(".")
    return str(value)


def main(argv: list[str] | None = None) -> int:
    parser = argparse.ArgumentParser(description="Write Hybrid v0 diagnostics markdown report.")
    parser.add_argument("--run-dir", type=Path, nargs="+", required=True)
    parser.add_argument("--output", type=Path, required=True)
    args = parser.parse_args(argv)
    output = write_hybrid_diagnostics_report(args.run_dir, output_path=args.output)
    print(f"wrote {output}")
    return 0


if __name__ == "__main__":  # pragma: no cover
    raise SystemExit(main())
