from __future__ import annotations

import json
import os
from pathlib import Path

import pandas as pd
import pytest

from graphrag_pipeline.scripts.qa_eval import algorithmic_seed_builder as seed_builder
from graphrag_pipeline.scripts.qa_eval.algorithmic_seed_builder import SeedBuilderConfig


def _write_text_units(tmp_path: Path) -> Path:
    df = pd.DataFrame(
        [
            {"id": "tu-001", "text": "DBSCAN 使用 eps 和 MinPts 描述密度可达关系。", "document_ids": ["doc-1"]},
            {"id": "tu-002", "text": "K-Means 通过簇中心迭代优化样本划分。", "document_ids": ["doc-1"]},
            {"id": "tu-003", "text": "监督学习依赖带标签数据，常用于分类与回归。", "document_ids": ["doc-2"]},
            {"id": "tu-004", "text": "模型评估包含准确率、召回率、F1 等指标。", "document_ids": ["doc-2"]},
            {"id": "tu-005", "text": "聚类算法用于无监督学习中的结构发现。", "document_ids": ["doc-3"]},
            {"id": "tu-006", "text": "课程总结强调数据、模型、评估和部署的完整流程。", "document_ids": ["doc-3"]},
        ]
    )
    path = tmp_path / "text_units.parquet"
    df.to_parquet(path)
    return path


def _write_text_units_with_prefix(path: Path, prefix: str) -> Path:
    path.parent.mkdir(parents=True, exist_ok=True)
    df = pd.DataFrame(
        [
            {"id": f"{prefix}-001", "text": f"{prefix} DBSCAN 使用 eps 和 MinPts 描述密度可达关系。"},
            {"id": f"{prefix}-002", "text": f"{prefix} K-Means 通过簇中心迭代优化样本划分。"},
            {"id": f"{prefix}-003", "text": f"{prefix} 监督学习依赖带标签数据。"},
            {"id": f"{prefix}-004", "text": f"{prefix} 模型评估包含准确率、召回率、F1 等指标。"},
        ]
    )
    df.to_parquet(path)
    return path


def test_build_candidate_seeds_returns_diverse_sources(tmp_path: Path):
    text_units = _write_text_units(tmp_path)
    output = tmp_path / "qa_candidate_seeds.jsonl"

    seeds = seed_builder.build_candidate_seeds(
        SeedBuilderConfig(
            text_units_path=text_units,
            output_path=output,
            max_items=4,
            cluster_count=3,
            random_seed=7,
        )
    )

    assert len(seeds) == 4
    assert output.exists()
    loaded = [json.loads(line) for line in output.read_text(encoding="utf-8").splitlines()]
    assert all(item["source_text_unit_ids"] for item in loaded)
    assert len({item["coverage_cluster"] for item in loaded}) >= 2
    assert {item["suggested_category"] for item in loaded} <= {
        "factual_lookup",
        "relation_reasoning",
        "chapter_summary",
        "global_overview",
    }


def test_build_candidate_seeds_uses_latest_default_nested_text_units(
    tmp_path: Path, monkeypatch: pytest.MonkeyPatch
):
    output_root = tmp_path / "output"
    top_level = _write_text_units_with_prefix(output_root / "text_units.parquet", "old-top")
    nested = _write_text_units_with_prefix(output_root / "run-20260513" / "text_units.parquet", "new-nested")
    os.utime(top_level, (1_700_000_000, 1_700_000_000))
    os.utime(nested, (1_700_000_100, 1_700_000_100))
    monkeypatch.setattr(seed_builder, "DEFAULT_OUTPUT_ROOT", output_root)

    assert SeedBuilderConfig(output_path=tmp_path / "seeds.jsonl").text_units_path is None

    seeds = seed_builder.build_candidate_seeds(
        SeedBuilderConfig(output_path=tmp_path / "seeds.jsonl", max_items=1, cluster_count=1)
    )

    assert seeds[0].source_text_unit_ids[0].startswith("new-nested-")
    assert "new-nested" in seeds[0].source_preview


def test_build_candidate_seeds_respects_explicit_old_top_level_text_units(tmp_path: Path):
    output_root = tmp_path / "output"
    top_level = _write_text_units_with_prefix(output_root / "text_units.parquet", "old-top")
    nested = _write_text_units_with_prefix(output_root / "run-20260513" / "text_units.parquet", "new-nested")
    os.utime(top_level, (1_700_000_000, 1_700_000_000))
    os.utime(nested, (1_700_000_100, 1_700_000_100))

    seeds = seed_builder.build_candidate_seeds(
        SeedBuilderConfig(
            text_units_path=top_level,
            output_path=tmp_path / "seeds.jsonl",
            max_items=1,
            cluster_count=1,
        )
    )

    assert seeds[0].source_text_unit_ids[0].startswith("old-top-")
    assert "old-top" in seeds[0].source_preview


def test_resolve_default_text_units_path_uses_top_level_when_it_is_latest(tmp_path: Path):
    output_root = tmp_path / "output"
    top_level = output_root / "text_units.parquet"
    nested = output_root / "run-20260513" / "text_units.parquet"
    top_level.parent.mkdir(parents=True)
    nested.parent.mkdir(parents=True)
    top_level.write_text("top-level", encoding="utf-8")
    nested.write_text("nested", encoding="utf-8")
    os.utime(nested, (1_700_000_000, 1_700_000_000))
    os.utime(top_level, (1_700_000_100, 1_700_000_100))

    assert seed_builder.resolve_default_text_units_path(output_root) == top_level


def test_resolve_default_text_units_path_uses_nested_when_it_is_newer_than_top_level(tmp_path: Path):
    output_root = tmp_path / "output"
    top_level = output_root / "text_units.parquet"
    nested = output_root / "run-20260513" / "text_units.parquet"
    top_level.parent.mkdir(parents=True)
    nested.parent.mkdir(parents=True)
    top_level.write_text("top-level", encoding="utf-8")
    nested.write_text("nested", encoding="utf-8")
    os.utime(top_level, (1_700_000_000, 1_700_000_000))
    os.utime(nested, (1_700_000_100, 1_700_000_100))

    assert seed_builder.resolve_default_text_units_path(output_root) == nested


def test_resolve_default_text_units_path_uses_only_nested_candidate(tmp_path: Path):
    output_root = tmp_path / "output"
    nested = output_root / "run-20260513" / "text_units.parquet"
    nested.parent.mkdir(parents=True)
    nested.write_text("nested", encoding="utf-8")

    assert seed_builder.resolve_default_text_units_path(output_root) == nested


def test_resolve_default_text_units_path_uses_latest_nested_candidate(tmp_path: Path):
    output_root = tmp_path / "output"
    older = output_root / "run-old" / "text_units.parquet"
    latest = output_root / "run-latest" / "text_units.parquet"
    older.parent.mkdir(parents=True)
    latest.parent.mkdir(parents=True)
    older.write_text("older", encoding="utf-8")
    latest.write_text("latest", encoding="utf-8")
    os.utime(older, (1_700_000_000, 1_700_000_000))
    os.utime(latest, (1_700_000_100, 1_700_000_100))

    assert seed_builder.resolve_default_text_units_path(output_root) == latest


def test_resolve_default_text_units_path_reports_ambiguous_candidates(tmp_path: Path):
    output_root = tmp_path / "output"
    first = output_root / "run-a" / "text_units.parquet"
    second = output_root / "run-b" / "text_units.parquet"
    first.parent.mkdir(parents=True)
    second.parent.mkdir(parents=True)
    first.write_text("first", encoding="utf-8")
    second.write_text("second", encoding="utf-8")
    os.utime(first, (1_700_000_000, 1_700_000_000))
    os.utime(second, (1_700_000_000, 1_700_000_000))

    with pytest.raises(FileNotFoundError, match="--text-units"):
        seed_builder.resolve_default_text_units_path(output_root)
