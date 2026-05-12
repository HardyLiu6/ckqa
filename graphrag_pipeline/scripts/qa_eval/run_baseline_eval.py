from __future__ import annotations

import argparse
import json
import sys
import time
from collections import OrderedDict
from dataclasses import dataclass
from datetime import datetime, timezone
from pathlib import Path
from typing import Any

from graphrag_pipeline.scripts.qa_eval.graphrag_client import (
    OpenAICompatibleClient,
    QueryResult,
)
from graphrag_pipeline.scripts.qa_eval.test_set_schema import QaTestItem
from graphrag_pipeline.scripts.qa_eval.test_set_validator import validate_jsonl


DEFAULT_OUTPUT_ROOT = Path("graphrag_pipeline/results/qa_eval/runs")
BASELINE_MODES: tuple[str, ...] = (
    "graphrag-local-search:latest",
    "graphrag-global-search:latest",
    "graphrag-drift-search:latest",
    "graphrag-basic-search:latest",
)
MODE_ALIASES: dict[str, str] = {
    "local": "graphrag-local-search:latest",
    "global": "graphrag-global-search:latest",
    "drift": "graphrag-drift-search:latest",
    "basic": "graphrag-basic-search:latest",
}
MODE_LABELS: dict[str, str] = {model: label for label, model in MODE_ALIASES.items()}


@dataclass(slots=True)
class BaselineEvalRunner:
    test_set_path: Path
    run_id: str
    index_run_label: str
    output_root: Path = DEFAULT_OUTPUT_ROOT
    base_url: str = "http://127.0.0.1:8000"
    modes: list[str] | None = None
    request_timeout_seconds: float = 120.0
    max_retries: int = 3
    backoff_seconds: float = 2.0
    max_items: int | None = None

    def run(self) -> Path:
        test_set_path = Path(self.test_set_path)
        report = validate_jsonl(test_set_path)
        if not report.ok:
            details = "\n".join(report.errors) if report.errors else "unknown validation error"
            raise ValueError(f"QA test set validation failed:\n{details}")

        selected_modes = self._selected_modes()
        source_items = _load_test_items(test_set_path)
        items = source_items[: self.max_items] if self.max_items is not None else source_items
        run_dir = Path(self.output_root) / self.run_id
        raw_dir = run_dir / "raw"
        raw_dir.mkdir(parents=True, exist_ok=True)

        _write_json(
            run_dir / "run_meta.json",
            {
                "run_id": self.run_id,
                "created_at": datetime.now(timezone.utc).isoformat(),
                "test_set_path": str(test_set_path),
                "index_run_label": self.index_run_label,
                "base_url": self.base_url,
                "modes": selected_modes,
                "mode_labels": {model: MODE_LABELS[model] for model in selected_modes},
                "total_items": len(items),
                "total_questions": len(items),
                "source_total_questions": len(source_items),
                "max_items": self.max_items,
            },
        )

        client = OpenAICompatibleClient(
            base_url=self.base_url,
            request_timeout_seconds=self.request_timeout_seconds,
            max_retries=self.max_retries,
            backoff_seconds=self.backoff_seconds,
        )
        for item in items:
            _write_json(raw_dir / f"{item.id}.json", self._run_item(item, selected_modes, client))

        return run_dir

    def _selected_modes(self) -> list[str]:
        modes = list(self.modes) if self.modes is not None else list(BASELINE_MODES)
        normalized: list[str] = []
        unsupported: list[str] = []
        duplicates: list[str] = []
        seen: set[str] = set()
        for raw_mode in modes:
            mode = MODE_ALIASES.get(raw_mode, raw_mode)
            if mode not in BASELINE_MODES:
                unsupported.append(mode)
                continue
            if mode in seen:
                duplicates.append(mode)
                continue
            seen.add(mode)
            normalized.append(mode)
        if unsupported:
            raise ValueError(f"unsupported baseline mode(s): {', '.join(unsupported)}")
        if duplicates:
            raise ValueError(f"duplicate baseline mode(s): {', '.join(duplicates)}")
        return normalized

    def _run_item(
        self,
        item: QaTestItem,
        selected_modes: list[str],
        client: OpenAICompatibleClient,
    ) -> dict[str, Any]:
        mode_results: OrderedDict[str, dict[str, Any]] = OrderedDict()
        for model in selected_modes:
            started_at = time.perf_counter()
            try:
                result = client.query(model=model, prompt=item.question)
                mode_results[model] = _serialize_query_result(result)
            except Exception as exc:  # noqa: BLE001 - baseline runner must preserve the full run.
                mode_results[model] = {
                    "error": str(exc),
                    "error_type": type(exc).__name__,
                    "elapsed_seconds": time.perf_counter() - started_at,
                }

        return {
            "id": item.id,
            "question_id": item.id,
            "category": item.category.value,
            "question": item.question,
            "gold_answer_summary": item.gold_answer_summary,
            "gold_entities": item.gold_entities,
            "gold_text_unit_ids": item.gold_text_unit_ids,
            "must_cite_terms": item.must_cite_terms,
            "negative_terms": item.negative_terms,
            "modes": mode_results,
        }


def _load_test_items(path: Path) -> list[QaTestItem]:
    items: list[QaTestItem] = []
    with path.open("r", encoding="utf-8") as handle:
        for raw_line in handle:
            raw_line = raw_line.strip()
            if raw_line:
                items.append(QaTestItem.model_validate_json(raw_line))
    return items


def _serialize_query_result(result: QueryResult) -> dict[str, Any]:
    return {
        "answer": result.answer,
        "total_tokens": result.total_tokens,
        "elapsed_seconds": result.elapsed_seconds,
        "raw": result.raw,
    }


def _write_json(path: Path, payload: dict[str, Any]) -> None:
    path.write_text(
        json.dumps(payload, ensure_ascii=False, indent=2) + "\n",
        encoding="utf-8",
    )


def main(argv: list[str] | None = None) -> int:
    parser = argparse.ArgumentParser(description="Run GraphRAG QA baseline evaluation.")
    parser.add_argument("--test-set", type=Path, required=True)
    parser.add_argument("--output-root", type=Path, default=DEFAULT_OUTPUT_ROOT)
    parser.add_argument("--run-id", required=True)
    parser.add_argument("--index-run-label", required=True)
    parser.add_argument("--base-url", default="http://127.0.0.1:8000")
    parser.add_argument("--modes", nargs="+", default=None)
    parser.add_argument("--request-timeout-seconds", type=float, default=120.0)
    parser.add_argument("--max-retries", type=int, default=3)
    parser.add_argument("--backoff-seconds", type=float, default=2.0)
    parser.add_argument("--max-items", type=int, default=None)
    args = parser.parse_args(argv)

    runner = BaselineEvalRunner(
        test_set_path=args.test_set,
        output_root=args.output_root,
        run_id=args.run_id,
        index_run_label=args.index_run_label,
        base_url=args.base_url,
        modes=args.modes,
        request_timeout_seconds=args.request_timeout_seconds,
        max_retries=args.max_retries,
        backoff_seconds=args.backoff_seconds,
        max_items=args.max_items,
    )
    runner.run()
    return 0


if __name__ == "__main__":  # pragma: no cover
    sys.exit(main())
