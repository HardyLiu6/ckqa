from __future__ import annotations

import pandas as pd

from graphrag_pipeline.scripts.hybrid_qa.bm25_text_units import (
    TextUnitBm25Config,
    build_text_unit_bm25,
)
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


def test_text_unit_bm25_expands_file_system_query_to_directory_terms(tmp_path):
    parquet_path = tmp_path / "text_units.parquet"
    pd.DataFrame(
        [
            {"id": "tu-filesystem", "text": "文件系统提供普通文件访问接口。"},
            {"id": "tu-fcb", "text": "文件控制块和索引结点用于描述文件目录结构。"},
            {"id": "tu-process", "text": "进程调度根据优先级分配处理机。"},
            {"id": "tu-memory", "text": "虚拟存储器通过页面置换管理内存。"},
        ]
    ).to_parquet(parquet_path)

    index = build_text_unit_bm25(parquet_path, cache_dir=tmp_path / "cache")
    results = index.search("文件系统", top_k=2)

    assert results[0].ref == "tu-fcb"


def test_text_unit_bm25_does_not_expand_plain_scheduling_to_disk_terms(tmp_path):
    parquet_path = tmp_path / "text_units.parquet"
    pd.DataFrame(
        [
            {"id": "tu-process", "text": "处理机管理从进程扩展到线程，并讨论作业调度、进程调度和线程调度。"},
            {"id": "tu-disk", "text": "磁盘调度算法关注寻道时间、旋转延迟、传输时间和磁盘 I/O 速度。"},
            {"id": "tu-memory", "text": "虚拟存储器通过页面置换管理内存。"},
        ]
    ).to_parquet(parquet_path)

    index = build_text_unit_bm25(parquet_path, cache_dir=tmp_path / "cache")
    expanded = index.expanded_query_for_debug("处理机管理这条主线如何从进程扩展到线程和调度？")

    assert "磁盘调度算法" not in expanded
    assert "进程调度" in expanded
    assert index.search("处理机管理这条主线如何从进程扩展到线程和调度？", top_k=1)[0].ref == "tu-process"


def test_text_unit_bm25_can_filter_exercise_sections(tmp_path):
    parquet_path = tmp_path / "text_units.parquet"
    pd.DataFrame(
        [
            {
                "id": "tu-exercise",
                "text": "document_type: textbook. chapter: 第一章. section: 习题. 操作系统主要目标 操作系统主要目标 操作系统主要目标。",
            },
            {
                "id": "tu-goal",
                "text": "document_type: textbook. chapter: 第一章. section: 1.2 操作系统的目标. 操作系统主要目标包括方便性、有效性、可扩充性和开放性。",
            },
            {"id": "tu-other", "text": "进程同步关注临界资源和信号量。"},
        ]
    ).to_parquet(parquet_path)

    index = build_text_unit_bm25(
        parquet_path,
        cache_dir=tmp_path / "cache",
        config=TextUnitBm25Config(exclude_exercises=True),
    )

    results = index.search("教材列出的操作系统主要目标有哪些？", top_k=2)
    assert results[0].ref == "tu-goal"
    assert all(result.ref != "tu-exercise" for result in results)


def test_text_unit_bm25_accepts_user_terms_without_polluting_global_jieba(tmp_path):
    parquet_path = tmp_path / "text_units.parquet"
    pd.DataFrame(
        [
            {"id": "tu-custom", "text": "蓝鲸调度器负责把作业分配到处理机。"},
            {"id": "tu-other", "text": "普通调度器按照优先级选择进程。"},
            {"id": "tu-memory", "text": "分页管理把逻辑地址映射到物理地址。"},
        ]
    ).to_parquet(parquet_path)

    index = build_text_unit_bm25(
        parquet_path,
        cache_dir=tmp_path / "cache",
        config=TextUnitBm25Config(user_terms=("蓝鲸调度器",), enable_query_expansion=False),
    )

    assert "蓝鲸调度器" not in index.global_tokenize_for_debug("蓝鲸调度器")
    assert "蓝鲸调度器" in index.tokenize_for_debug("蓝鲸调度器")
    assert index.search("蓝鲸调度器", top_k=1)[0].ref == "tu-custom"


def test_text_unit_bm25_cache_is_separated_by_bm25_parameters(tmp_path):
    parquet_path = tmp_path / "text_units.parquet"
    cache_dir = tmp_path / "cache"
    _write_text_units(parquet_path)

    first = build_text_unit_bm25(
        parquet_path,
        cache_dir=cache_dir,
        config=TextUnitBm25Config(k1=0.9, b=0.55),
    )
    second = build_text_unit_bm25(
        parquet_path,
        cache_dir=cache_dir,
        config=TextUnitBm25Config(k1=1.5, b=0.9),
    )

    assert first.config.k1 == 0.9
    assert second.config.k1 == 1.5
    assert first.config.b == 0.55
    assert second.config.b == 0.9
