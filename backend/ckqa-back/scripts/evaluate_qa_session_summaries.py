#!/usr/bin/env python3
from __future__ import annotations

import argparse
import json
from dataclasses import dataclass
from pathlib import Path
from typing import Any, Sequence


DEFAULT_FIXTURE = Path(__file__).resolve().parent / "fixtures" / "qa_summary_eval_20_turns.json"


@dataclass(frozen=True, slots=True)
class SummaryEvalCase:
    case_id: str
    summary: str
    required_terms: list[str]
    forbidden_terms: list[str]


def evaluate_case(case: SummaryEvalCase) -> dict[str, Any]:
    missing = [term for term in case.required_terms if term not in case.summary]
    forbidden_hits = [term for term in case.forbidden_terms if term in case.summary]
    passed = not missing and not forbidden_hits
    return {
        "caseId": case.case_id,
        "passed": passed,
        "missingTerms": missing,
        "forbiddenHits": forbidden_hits,
        "summaryChars": len(case.summary),
    }


def load_cases(path: Path) -> list[SummaryEvalCase]:
    payload = json.loads(path.read_text(encoding="utf-8"))
    raw_cases = payload.get("cases") if isinstance(payload, dict) else payload
    cases: list[SummaryEvalCase] = []
    for raw in raw_cases:
        cases.append(
            SummaryEvalCase(
                case_id=str(raw["id"]),
                summary=str(raw["summary"]),
                required_terms=[str(term) for term in raw.get("requiredTerms", [])],
                forbidden_terms=[str(term) for term in raw.get("forbiddenTerms", [])],
            )
        )
    return cases


def build_report(cases: Sequence[SummaryEvalCase]) -> dict[str, Any]:
    results = [evaluate_case(case) for case in cases]
    passed = sum(1 for result in results if result["passed"])
    return {
        "total": len(results),
        "passed": passed,
        "failed": len(results) - passed,
        "passRate": round(passed / len(results), 4) if results else 0.0,
        "results": results,
    }


def parse_args(argv: Sequence[str] | None = None) -> argparse.Namespace:
    parser = argparse.ArgumentParser(description="低成本评估 QA 会话滚动摘要是否保留关键学习上下文。")
    parser.add_argument("--fixture", type=Path, default=DEFAULT_FIXTURE, help="摘要评估 fixture JSON。")
    parser.add_argument("--json-report", type=Path, default=None, help="写入评估报告 JSON。")
    parser.add_argument("--fail-under", type=float, default=1.0, help="通过率低于该值时返回非 0。")
    return parser.parse_args(argv)


def main(argv: Sequence[str] | None = None) -> int:
    args = parse_args(argv)
    report = build_report(load_cases(args.fixture))
    output = json.dumps(report, ensure_ascii=False, indent=2)
    print(output)
    if args.json_report:
        args.json_report.parent.mkdir(parents=True, exist_ok=True)
        args.json_report.write_text(output, encoding="utf-8")
    return 0 if report["passRate"] >= args.fail_under else 2


if __name__ == "__main__":
    raise SystemExit(main())
