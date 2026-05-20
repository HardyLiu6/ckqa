from __future__ import annotations

import json
import sys
from pathlib import Path

PROJECT_ROOT = Path(__file__).resolve().parent.parent
SCRIPTS_DIR = PROJECT_ROOT / "scripts"
if str(SCRIPTS_DIR) not in sys.path:
    sys.path.insert(0, str(SCRIPTS_DIR))

from build_os_eval_sets import build_eval_sets
from graphrag_pipeline.scripts.qa_eval.test_set_schema import QaTestItem


def _read_jsonl(path: Path) -> list[dict]:
    return [json.loads(line) for line in path.read_text(encoding="utf-8").splitlines() if line.strip()]


def test_build_eval_sets_normalizes_external_items_and_drops_redundant_fields(tmp_path: Path) -> None:
    source = tmp_path / "source.jsonl"
    qa_output = tmp_path / "qa.jsonl"
    routing_output = tmp_path / "routing.jsonl"
    source.write_text(
        "\n".join(
            [
                json.dumps(
                    {
                        "id": "OS001",
                        "topic": "操作系统概述",
                        "question_type": "概念题",
                        "difficulty": "基础",
                        "question": "什么是操作系统？",
                        "reference_answer": "操作系统是管理硬件资源并提供用户接口的系统软件。",
                        "rubric": "冗余评分说明",
                        "source_basis": "[1][2]",
                    },
                    ensure_ascii=False,
                ),
                json.dumps(
                    {
                        "id": "OS002",
                        "topic": "进程与线程",
                        "question_type": "比较题",
                        "difficulty": "进阶",
                        "question": "请比较进程与线程的主要区别。",
                        "reference_answer": "进程拥有独立资源，线程共享进程资源且更轻量。",
                        "rubric": "冗余评分说明",
                        "source_basis": "[2]",
                    },
                    ensure_ascii=False,
                ),
            ]
        )
        + "\n",
        encoding="utf-8",
    )

    summary = build_eval_sets(source, qa_output, routing_output)

    assert summary["qa_items"] == 2
    qa_rows = _read_jsonl(qa_output)
    assert set(qa_rows[0]) == {
        "id",
        "category",
        "question",
        "gold_answer_summary",
        "gold_entities",
        "gold_text_unit_ids",
        "must_cite_terms",
        "negative_terms",
        "notes",
    }
    assert qa_rows[0]["id"] == "Q2001"
    assert qa_rows[0]["category"] == "factual_lookup"
    assert "rubric" not in qa_rows[0]
    assert "source_basis" not in qa_rows[0]
    QaTestItem.model_validate(qa_rows[0])

    routing_rows = _read_jsonl(routing_output)
    assert {"id", "question", "expectedMode", "acceptableModes", "betaHybridEnabled", "hasConversationContext"} == set(routing_rows[0])
    assert any(row["id"] == "os-hybrid-002" and row["expectedMode"] == "hybrid_v0" for row in routing_rows)
    assert any(row["id"] == "os-hybrid-gated-002" and row["expectedMode"] == "local" for row in routing_rows)
