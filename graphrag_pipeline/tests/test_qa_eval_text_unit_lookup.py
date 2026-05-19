from __future__ import annotations

from pathlib import Path

import pandas as pd

from graphrag_pipeline.scripts.qa_eval.text_unit_lookup import (
    TextUnitLookup,
    load_text_unit_lookup,
)


def _write_parquet(tmp_path: Path) -> Path:
    parquet_path = tmp_path / "text_units.parquet"
    pd.DataFrame(
        [
            {"id": "d244f9016ac84a55a7435cb6466e1b38ae108a95", "text": "第 1 章 绪论"},
            {"id": "81d99ad61e36b8d45bc6265e303c143cf555bd3d", "text": "DBSCAN 与 eps"},
            {"id": "abcabcabcabcabcabcabcabcabcabc1234567890", "text": "实验 3"},
        ]
    ).to_parquet(parquet_path)
    return parquet_path


def test_load_text_unit_lookup_indexes_by_12_char_prefix(tmp_path: Path) -> None:
    lookup: TextUnitLookup = load_text_unit_lookup(_write_parquet(tmp_path))

    assert lookup.get("d244f9016ac8") == "第 1 章 绪论"
    assert lookup.get("81d99ad61e36") == "DBSCAN 与 eps"


def test_fetch_many_keeps_order_and_skips_misses(tmp_path: Path) -> None:
    lookup = load_text_unit_lookup(_write_parquet(tmp_path))

    snippets = lookup.fetch_many(["abcabcabcabc", "deadbeefdead", "d244f9016ac8"])

    assert len(snippets) == 2
    assert snippets[0].prefix == "abcabcabcabc"
    assert snippets[0].text == "实验 3"
    assert snippets[1].prefix == "d244f9016ac8"


def test_join_snippets_renders_as_markdown_with_refs(tmp_path: Path) -> None:
    lookup = load_text_unit_lookup(_write_parquet(tmp_path))

    rendered = lookup.render_for_prompt(["d244f9016ac8", "81d99ad61e36"], max_chars=80)

    assert "[d244f9016ac8]" in rendered
    assert "[81d99ad61e36]" in rendered
    assert "DBSCAN 与 eps" in rendered
