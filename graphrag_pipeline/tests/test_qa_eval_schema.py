from __future__ import annotations

import json
from pathlib import Path

import pytest
from pydantic import ValidationError

from graphrag_pipeline.scripts.qa_eval.test_set_schema import (
    QaTestItem,
    QuestionCategory,
)
from graphrag_pipeline.scripts.qa_eval.test_set_validator import (
    ValidationReport,
    main,
    validate_jsonl,
)


def _make_valid_payload(**overrides):
    base = {
        "id": "Q001",
        "category": "factual_lookup",
        "question": "课程的第 1 章第 2 节标题是什么？",
        "gold_answer_summary": "数据采集与预处理",
        "gold_entities": ["数据采集与预处理"],
        "gold_text_unit_ids": ["d244f9016ac8"],
        "must_cite_terms": ["第 1 章", "第 2 节"],
        "negative_terms": [],
        "notes": "对应 documents.parquet 中 chapter=1, section=2",
    }
    base.update(overrides)
    return base


def test_qa_test_item_accepts_valid_payload():
    item = QaTestItem.model_validate(_make_valid_payload())
    assert item.category is QuestionCategory.FACTUAL_LOOKUP


def test_qa_test_item_id_pattern_rejects_too_long():
    payload = _make_valid_payload(id="Q123456")
    with pytest.raises(ValidationError):
        QaTestItem.model_validate(payload)


def test_qa_test_item_id_pattern_rejects_too_short():
    payload = _make_valid_payload(id="Q12")
    with pytest.raises(ValidationError):
        QaTestItem.model_validate(payload)


def test_qa_test_item_rejects_unknown_category():
    payload = _make_valid_payload(category="bogus_category")
    with pytest.raises(ValidationError):
        QaTestItem.model_validate(payload)


def test_qa_test_item_requires_question_min_length():
    payload = _make_valid_payload(question="?")
    with pytest.raises(ValidationError):
        QaTestItem.model_validate(payload)


def test_qa_test_item_strips_list_terms_and_filters_empty_values():
    payload = _make_valid_payload(
        gold_entities=[" 数据采集 ", "", "   ", "预处理"],
        must_cite_terms=[" 第 1 章 ", None, "第 2 节"],
        negative_terms=["  跑偏术语  ", ""],
    )
    item = QaTestItem.model_validate(payload)
    assert item.gold_entities == ["数据采集", "预处理"]
    assert item.must_cite_terms == ["第 1 章", "第 2 节"]
    assert item.negative_terms == ["跑偏术语"]


def test_qa_test_item_normalizes_text_unit_ids_to_12_chars():
    payload = _make_valid_payload(
        gold_text_unit_ids=["  d244f9016ac84a55a7435cb6 ", "81d99ad61e36"]
    )
    item = QaTestItem.model_validate(payload)
    assert item.gold_text_unit_ids == ["d244f9016ac8", "81d99ad61e36"]


def test_validate_jsonl_flags_duplicate_id(tmp_path: Path):
    file = tmp_path / "set.jsonl"
    payload = _make_valid_payload()
    file.write_text(
        json.dumps(payload, ensure_ascii=False)
        + "\n"
        + json.dumps(payload, ensure_ascii=False)
        + "\n",
        encoding="utf-8",
    )
    report: ValidationReport = validate_jsonl(file)
    assert not report.ok
    assert any("Q001" in err for err in report.errors)


def test_validate_jsonl_flags_invalid_json(tmp_path: Path):
    file = tmp_path / "set.jsonl"
    file.write_text("{not-json}\n", encoding="utf-8")
    report = validate_jsonl(file)
    assert not report.ok
    assert any("invalid json" in err for err in report.errors)


def test_validate_jsonl_truncates_errors_at_max(tmp_path: Path):
    file = tmp_path / "set.jsonl"
    payloads = []
    for i in range(40):
        bad = _make_valid_payload(id=f"Q{i:03d}")
        bad.pop("category")
        payloads.append(json.dumps(bad, ensure_ascii=False))
    file.write_text("\n".join(payloads) + "\n", encoding="utf-8")
    report = validate_jsonl(file, max_errors=10)
    assert not report.ok
    assert len(report.errors) <= 11
    assert any("及更多" in err for err in report.errors)


def test_validate_jsonl_flags_missing_category(tmp_path: Path):
    file = tmp_path / "set.jsonl"
    items = [
        _make_valid_payload(id=f"Q{i:03d}", category="factual_lookup")
        for i in range(8)
    ]
    file.write_text(
        "\n".join(json.dumps(it, ensure_ascii=False) for it in items)
        + "\n",
        encoding="utf-8",
    )
    report = validate_jsonl(file)
    assert not report.ok
    assert any("relation_reasoning" in err for err in report.errors)


def test_validator_cli_returns_one_for_invalid_file(tmp_path: Path, capsys):
    file = tmp_path / "set.jsonl"
    file.write_text("{not-json}\n", encoding="utf-8")
    exit_code = main([str(file), "--max-errors", "2"])
    captured = capsys.readouterr()
    assert exit_code == 1
    assert "invalid json" in captured.out
