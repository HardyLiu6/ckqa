from __future__ import annotations

import argparse
import importlib.util
import json
from pathlib import Path
from typing import Any

from graphrag_pipeline.scripts.qa_eval.run_loader import (
    infer_question_ids_from_raw,
    load_raw_mode_answer,
    load_run_meta,
    load_test_set,
)


OUTPUT_NAME = "ragas_dataset.jsonl"


def export_ragas_dataset(
    run_dir: Path,
    contexts_by_question: dict[str, list[str] | str] | None = None,
) -> Path:
    run_dir = Path(run_dir)
    meta = load_run_meta(run_dir)
    test_set = load_test_set(Path(meta["test_set_path"]))
    modes = [str(mode) for mode in meta.get("modes", [])]
    question_ids = _question_ids(meta, run_dir)
    contexts_by_question = contexts_by_question or {}

    rows: list[dict[str, Any]] = []
    for question_id in question_ids:
        item = test_set.get(question_id)
        if item is None:
            continue
        contexts = _contexts_for(question_id, contexts_by_question)
        for mode in modes:
            raw = load_raw_mode_answer(run_dir, question_id, mode)
            rows.append(
                {
                    "question_id": question_id,
                    "mode": mode,
                    "question": item.question,
                    "answer": raw.answer,
                    "contexts": contexts,
                    "ground_truth": item.gold_answer_summary,
                    "gold_text_unit_ids": item.gold_text_unit_ids,
                }
            )

    output = run_dir / OUTPUT_NAME
    _write_jsonl(output, rows)
    return output


def _question_ids(meta: dict[str, Any], run_dir: Path) -> list[str]:
    raw_ids = meta.get("question_ids")
    if isinstance(raw_ids, list) and raw_ids:
        return [str(question_id) for question_id in raw_ids]
    return infer_question_ids_from_raw(run_dir)


def _contexts_for(question_id: str, contexts_by_question: dict[str, list[str] | str]) -> list[str]:
    value = contexts_by_question.get(question_id, [])
    if isinstance(value, str):
        return [value] if value else []
    return [str(context) for context in value if str(context)]


def _write_jsonl(path: Path, rows: list[dict[str, Any]]) -> None:
    path.write_text(
        "".join(json.dumps(row, ensure_ascii=False) + "\n" for row in rows),
        encoding="utf-8",
    )


def _report_optional_ragas() -> None:
    if importlib.util.find_spec("ragas") is None:
        print("ragas 未安装，已仅导出 ragas_dataset.jsonl；如需运行 RAGAS，请先安装可选依赖。")
        return
    print("ragas 已安装；Task 7 默认只生成稳定 JSONL 数据集，请用 ragas_dataset.jsonl 执行外部评分。")


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--run-dir", type=Path, required=True)
    parser.add_argument("--run-ragas", action="store_true", help="检测 ragas 可选依赖；缺失时只提示不失败。")
    args = parser.parse_args()

    output = export_ragas_dataset(args.run_dir)
    print(f"wrote {output}")
    if args.run_ragas:
        _report_optional_ragas()
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
