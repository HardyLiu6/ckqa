from __future__ import annotations

import pandas as pd

from graphrag_pipeline.scripts.hybrid_qa.bm25_text_units import build_text_unit_bm25
from graphrag_pipeline.scripts.hybrid_qa.types import HybridLayer


def _write_text_units(path):
    pd.DataFrame(
        [
            {"id": "tu-001", "text": "操作系统是计算机硬件之上的第一层软件。"},
            {"id": "tu-002", "text": "DBSCAN 使用 eps 和 MinPts 描述邻域密度。"},
            {"id": "tu-003", "text": "K-Means 聚类通过迭代更新质心划分样本。"},
        ]
    ).to_parquet(path)


def test_text_unit_bm25_search_returns_low_layer_bm25_candidates(tmp_path):
    parquet_path = tmp_path / "text_units.parquet"
    _write_text_units(parquet_path)

    index = build_text_unit_bm25(parquet_path, cache_dir=tmp_path / "cache")
    results = index.search("操作系统 第一层软件", top_k=2)

    assert results[0].ref == "tu-001"
    assert results[0].layer is HybridLayer.LOW
    assert results[0].source == "bm25"


def test_text_unit_bm25_reuses_cache_on_rebuild(tmp_path):
    parquet_path = tmp_path / "text_units.parquet"
    cache_dir = tmp_path / "cache"
    _write_text_units(parquet_path)

    first = build_text_unit_bm25(parquet_path, cache_dir=cache_dir)
    second = build_text_unit_bm25(parquet_path, cache_dir=cache_dir)

    assert first.size == 3
    assert second.size == 3
    assert (cache_dir / "text_unit_bm25.pkl").exists()
