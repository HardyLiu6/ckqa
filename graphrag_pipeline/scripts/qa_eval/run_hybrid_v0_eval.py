from __future__ import annotations

import argparse
import sys
import time
from collections import OrderedDict
from dataclasses import dataclass
from datetime import datetime, timezone
from pathlib import Path
from typing import Any

from graphrag_pipeline.scripts.qa_eval.graphrag_client import OpenAICompatibleClient
from graphrag_pipeline.scripts.qa_eval.run_baseline_eval import (
    DEFAULT_OUTPUT_ROOT,
    REQUIRED_INDEX_OUTPUT_PATHS,
    _find_duplicates,
    _load_test_items,
    _serialize_query_result,
    _write_json,
)
from graphrag_pipeline.scripts.qa_eval.test_set_schema import QaTestItem
from graphrag_pipeline.scripts.qa_eval.test_set_validator import validate_jsonl


HYBRID_V0_MODEL = "graphrag-hybrid-v0-search:latest"
HYBRID_V0_MODE_LABELS = {HYBRID_V0_MODEL: "hybrid_v0"}
DEFAULT_TEST_SET = Path("graphrag_pipeline/data/eval/qa_test_set.jsonl")


@dataclass(slots=True)
class HybridV0EvalRunner:
    test_set_path: Path
    run_id: str
    index_run_label: str = "hybrid-v0"
    index_output_dir: Path | None = None
    output_root: Path = DEFAULT_OUTPUT_ROOT
    base_url: str = "http://127.0.0.1:8000"
    request_timeout_seconds: float = 120.0
    max_retries: int = 3
    backoff_seconds: float = 2.0
    max_items: int | None = None
    question_ids: list[str] | None = None

    def run(self) -> Path:
        test_set_path = Path(self.test_set_path)
        report = validate_jsonl(test_set_path)
        if not report.ok:
            details = "\n".join(report.errors) if report.errors else "unknown validation error"
            raise ValueError(f"QA test set validation failed:\n{details}")

        index_output_dir = self._validated_index_output_dir()
        source_items = _load_test_items(test_set_path)
        items = self._selected_items(source_items)
        items = items[: self.max_items] if self.max_items is not None else items
        executed_question_ids = [item.id for item in items] if self.question_ids is not None else None
        selected_modes = [HYBRID_V0_MODEL]
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
                "index_output_dir": str(index_output_dir) if index_output_dir is not None else None,
                "base_url": self.base_url,
                "modes": selected_modes,
                "mode_labels": HYBRID_V0_MODE_LABELS,
                "total_items": len(items),
                "total_questions": len(items),
                "source_total_questions": len(source_items),
                "max_items": self.max_items,
                "question_ids": executed_question_ids,
            },
        )

        client = OpenAICompatibleClient(
            base_url=self.base_url,
            request_timeout_seconds=self.request_timeout_seconds,
            max_retries=self.max_retries,
            backoff_seconds=self.backoff_seconds,
            allow_arbitrary_models=True,
        )
        for item in items:
            _write_json(raw_dir / f"{item.id}.json", self._run_item(item, client))

        return run_dir

    def _validated_index_output_dir(self) -> Path | None:
        if self.index_output_dir is None:
            return None

        index_output_dir = Path(self.index_output_dir).expanduser().resolve()
        missing = [
            relative_path.as_posix()
            for relative_path in REQUIRED_INDEX_OUTPUT_PATHS
            if not (index_output_dir / relative_path).exists()
        ]
        if missing:
            raise FileNotFoundError(
                "index output dir is missing required GraphRAG output file(s): "
                + ", ".join(missing)
            )
        return index_output_dir

    def _selected_items(self, source_items: list[QaTestItem]) -> list[QaTestItem]:
        if self.question_ids is None:
            return source_items

        duplicates = _find_duplicates(self.question_ids)
        if duplicates:
            raise ValueError(f"duplicate question id(s): {', '.join(duplicates)}")

        items_by_id = {item.id: item for item in source_items}
        missing = [question_id for question_id in self.question_ids if question_id not in items_by_id]
        if missing:
            raise ValueError(f"unknown question id(s): {', '.join(missing)}")

        return [items_by_id[question_id] for question_id in self.question_ids]

    def _run_item(self, item: QaTestItem, client: OpenAICompatibleClient) -> dict[str, Any]:
        mode_results: OrderedDict[str, dict[str, Any]] = OrderedDict()
        started_at = time.perf_counter()
        try:
            result = client.query(model=HYBRID_V0_MODEL, prompt=item.question)
            mode_results[HYBRID_V0_MODEL] = _serialize_query_result(result)
        except Exception as exc:  # noqa: BLE001 - eval runner must preserve partial runs.
            mode_results[HYBRID_V0_MODEL] = {
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


def main(argv: list[str] | None = None) -> int:
    parser = argparse.ArgumentParser(description="Run GraphRAG Hybrid v0 QA evaluation.")
    parser.add_argument("--test-set", type=Path, default=DEFAULT_TEST_SET)
    parser.add_argument("--run-id", required=True)
    parser.add_argument("--output-root", type=Path, default=DEFAULT_OUTPUT_ROOT)
    parser.add_argument("--index-run-label", default="hybrid-v0")
    parser.add_argument("--index-output-dir", type=Path, default=None)
    parser.add_argument("--base-url", default="http://127.0.0.1:8000")
    parser.add_argument("--request-timeout-seconds", type=float, default=120.0)
    parser.add_argument("--max-retries", type=int, default=3)
    parser.add_argument("--backoff-seconds", type=float, default=2.0)
    parser.add_argument("--max-items", type=int, default=None)
    parser.add_argument("--question-ids", nargs="+", default=None)
    args = parser.parse_args(argv)

    runner = HybridV0EvalRunner(
        test_set_path=args.test_set,
        run_id=args.run_id,
        output_root=args.output_root,
        index_run_label=args.index_run_label,
        index_output_dir=args.index_output_dir,
        base_url=args.base_url,
        request_timeout_seconds=args.request_timeout_seconds,
        max_retries=args.max_retries,
        backoff_seconds=args.backoff_seconds,
        max_items=args.max_items,
        question_ids=args.question_ids,
    )
    runner.run()
    return 0


if __name__ == "__main__":  # pragma: no cover
    sys.exit(main())
