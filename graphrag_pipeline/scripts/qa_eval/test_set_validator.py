from __future__ import annotations

import argparse
import json
import sys
from collections import Counter
from dataclasses import dataclass, field
from pathlib import Path

from pydantic import ValidationError

from graphrag_pipeline.scripts.qa_eval.test_set_schema import QaTestItem, QuestionCategory


MIN_PER_CATEGORY = 6
DEFAULT_MAX_ERRORS = 20


@dataclass
class ValidationReport:
    ok: bool
    total: int = 0
    by_category: Counter[str] = field(default_factory=Counter)
    errors: list[str] = field(default_factory=list)


def validate_jsonl(path: Path | str, *, max_errors: int = DEFAULT_MAX_ERRORS) -> ValidationReport:
    report = ValidationReport(ok=True)
    items: list[QaTestItem] = []
    seen_ids: set[str] = set()
    raw_error_count = 0
    error_limit = max(0, max_errors)

    def push_error(message: str) -> None:
        nonlocal raw_error_count
        raw_error_count += 1
        report.ok = False
        if len(report.errors) < error_limit:
            report.errors.append(message)

    with Path(path).open("r", encoding="utf-8") as handle:
        for line_no, raw_line in enumerate(handle, start=1):
            raw_line = raw_line.strip()
            if not raw_line:
                continue
            try:
                payload = json.loads(raw_line)
            except json.JSONDecodeError as exc:
                push_error(f"line {line_no}: invalid json - {exc}")
                continue

            try:
                item = QaTestItem.model_validate(payload)
            except ValidationError as exc:
                item_id = payload.get("id", "?") if isinstance(payload, dict) else "?"
                push_error(f"line {line_no} ({item_id}): {exc.errors()[0]}")
                continue

            if item.id in seen_ids:
                push_error(f"line {line_no}: duplicate id {item.id}")
                continue

            seen_ids.add(item.id)
            items.append(item)

    report.total = len(items)
    report.by_category.update(item.category.value for item in items)

    for category in QuestionCategory:
        current = report.by_category[category.value]
        if current < MIN_PER_CATEGORY:
            push_error(
                f"category {category.value} has only {current} items, need >= {MIN_PER_CATEGORY}"
            )

    truncated_count = raw_error_count - len(report.errors)
    if truncated_count > 0:
        report.errors.append(f"... 及更多 {truncated_count} 个错误未列出，请用 --max-errors 调大上限")

    return report


def _format_report(report: ValidationReport) -> str:
    lines = [f"ok: {report.ok}", f"total: {report.total}", "by_category:"]
    for category in QuestionCategory:
        lines.append(f"  {category.value}: {report.by_category[category.value]}")
    if report.errors:
        lines.append("errors:")
        lines.extend(f"  - {error}" for error in report.errors)
    return "\n".join(lines)


def main(argv: list[str] | None = None) -> int:
    parser = argparse.ArgumentParser(description="Validate GraphRAG QA baseline JSONL.")
    parser.add_argument("path", type=Path)
    parser.add_argument("--max-errors", type=int, default=DEFAULT_MAX_ERRORS)
    args = parser.parse_args(argv)

    report = validate_jsonl(args.path, max_errors=args.max_errors)
    print(_format_report(report))
    return 0 if report.ok else 1


if __name__ == "__main__":  # pragma: no cover
    sys.exit(main())
