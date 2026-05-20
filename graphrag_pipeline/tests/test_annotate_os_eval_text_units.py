from __future__ import annotations

import json
import sys
from pathlib import Path

import pandas as pd

PROJECT_ROOT = Path(__file__).resolve().parent.parent
SCRIPTS_DIR = PROJECT_ROOT / "scripts"
if str(SCRIPTS_DIR) not in sys.path:
    sys.path.insert(0, str(SCRIPTS_DIR))

from annotate_os_eval_text_units import annotate_eval_text_units


def _read_jsonl(path: Path) -> list[dict]:
    return [json.loads(line) for line in path.read_text(encoding="utf-8").splitlines() if line.strip()]


def test_annotate_eval_text_units_adds_ranked_prefixes_and_preserves_manual_labels(tmp_path: Path) -> None:
    eval_path = tmp_path / "eval.jsonl"
    text_units_path = tmp_path / "text_units.parquet"
    report_path = tmp_path / "report.json"

    manual_prefix = "ffffffffffff"
    eval_path.write_text(
        "\n".join(
            [
                json.dumps(
                    {
                        "id": "Q2001",
                        "category": "factual_lookup",
                        "question": "什么是死锁？",
                        "gold_answer_summary": "死锁是多个进程因竞争资源而相互等待。",
                        "gold_entities": ["死锁"],
                        "gold_text_unit_ids": [],
                        "must_cite_terms": ["死锁"],
                        "negative_terms": [],
                        "notes": "source_id=OS001; topic=死锁; type=概念题; difficulty=基础",
                    },
                    ensure_ascii=False,
                ),
                json.dumps(
                    {
                        "id": "Q2002",
                        "category": "factual_lookup",
                        "question": "什么是虚拟内存？",
                        "gold_answer_summary": "虚拟内存通过请求分页等机制扩展逻辑地址空间。",
                        "gold_entities": ["虚拟内存"],
                        "gold_text_unit_ids": [manual_prefix],
                        "must_cite_terms": ["虚拟内存"],
                        "negative_terms": [],
                        "notes": "source_id=OS002; topic=虚拟内存; type=概念题; difficulty=基础",
                    },
                    ensure_ascii=False,
                ),
            ]
        )
        + "\n",
        encoding="utf-8",
    )
    pd.DataFrame(
        [
            {
                "id": "aaaaaaaaaaaa0000",
                "human_readable_id": 1,
                "text": "heading_path_text: 死锁. 死锁是指多个进程因竞争资源而互相等待。",
            },
            {
                "id": "bbbbbbbbbbbb0000",
                "human_readable_id": 2,
                "text": "heading_path_text: 虚拟内存. 请求分页支持虚拟内存和逻辑地址空间。",
            },
            {
                "id": "cccccccccccc0000",
                "human_readable_id": 3,
                "text": "heading_path_text: 文件系统. 文件目录和索引节点。",
            },
        ]
    ).to_parquet(text_units_path)

    summary = annotate_eval_text_units(
        eval_path=eval_path,
        text_units_path=text_units_path,
        output_path=eval_path,
        report_path=report_path,
    )

    rows = _read_jsonl(eval_path)
    assert summary["annotated_items"] == 1
    assert rows[0]["gold_text_unit_ids"] == ["aaaaaaaaaaaa"]
    assert rows[1]["gold_text_unit_ids"] == [manual_prefix]
    report = json.loads(report_path.read_text(encoding="utf-8"))
    assert report["total_items"] == 2
    assert report["existing_items"] == 1
    assert report["items"][0]["candidates"][0]["prefix"] == "aaaaaaaaaaaa"


def test_annotate_eval_text_units_applies_human_audit_overrides(tmp_path: Path) -> None:
    eval_path = tmp_path / "eval.jsonl"
    text_units_path = tmp_path / "text_units.parquet"
    overrides_path = tmp_path / "overrides.json"
    report_path = tmp_path / "report.json"

    eval_path.write_text(
        json.dumps(
            {
                "id": "Q2001",
                "category": "factual_lookup",
                "question": "什么是死锁？",
                "gold_answer_summary": "死锁是多个进程因竞争资源而相互等待。",
                "gold_entities": ["死锁"],
                "gold_text_unit_ids": [],
                "must_cite_terms": ["死锁"],
                "negative_terms": [],
                "notes": "source_id=OS001; topic=死锁; type=概念题; difficulty=基础",
            },
            ensure_ascii=False,
        )
        + "\n",
        encoding="utf-8",
    )
    pd.DataFrame(
        [
            {
                "id": "aaaaaaaaaaaa0000",
                "human_readable_id": 1,
                "text": "heading_path_text: 死锁. 死锁是指多个进程因竞争资源而互相等待。",
            },
            {
                "id": "dddddddddddd0000",
                "human_readable_id": 2,
                "text": "heading_path_text: 死锁处理. 银行家算法用于避免死锁。",
            },
        ]
    ).to_parquet(text_units_path)
    overrides_path.write_text(
        json.dumps({"Q2001": {"gold_text_unit_ids": ["dddddddddddd"]}}, ensure_ascii=False),
        encoding="utf-8",
    )

    summary = annotate_eval_text_units(
        eval_path=eval_path,
        text_units_path=text_units_path,
        output_path=eval_path,
        report_path=report_path,
        overrides_path=overrides_path,
    )

    rows = _read_jsonl(eval_path)
    assert rows[0]["gold_text_unit_ids"] == ["dddddddddddd"]
    assert summary["manual_audit_items"] == 1
    assert json.loads(report_path.read_text(encoding="utf-8"))["items"][0]["action"] == "manual_audit_override"
