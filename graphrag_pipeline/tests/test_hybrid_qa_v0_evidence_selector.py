from __future__ import annotations

import numpy as np

from graphrag_pipeline.scripts.hybrid_qa.evidence_selector import (
    V6EvidenceSelectorConfig,
    build_v6_hybrid_evidence_selector_from_rows,
)
from graphrag_pipeline.scripts.qa_eval.bm25_retrieval_bakeoff import TextUnitRow


def test_v6_selector_filters_noise_and_uses_multi_query_rrf():
    rows = [
        TextUnitRow("gold11111111", "文件系统通过文件控制块和索引结点组织文件目录。"),
        TextUnitRow("noise2222222", "第八章 磁盘存储器的管理 gogogogogogogogogogo。"),
        TextUnitRow("other3333333", "处理机调度负责在就绪进程之间分配处理机。"),
        TextUnitRow("other4444444", "信号量机制用于处理进程同步和互斥。"),
        TextUnitRow("other5555555", "请求分页系统支持虚拟存储器。"),
    ]

    selector = build_v6_hybrid_evidence_selector_from_rows(
        rows,
        config=V6EvidenceSelectorConfig(
            user_terms=("文件控制块", "索引结点"),
            enable_dense_rerank=False,
        ),
    )

    refs = [candidate.ref for candidate in selector.search("文件系统和文件目录如何衔接？", top_k=3)]

    assert refs[0] == "gold11111111"
    assert "noise2222222" not in refs


def test_v6_selector_uses_section_heading_text_for_broad_queries():
    rows = [
        TextUnitRow(
            "heading111111",
            "document_type: textbook. chapter: 第七章 文件管理. section: 7.3 文件目录. "
            "heading_path_text: 第七章 文件管理 > 7.3 文件目录. 正文介绍目录项的组织方式。",
        ),
        TextUnitRow("other2222222", "处理机调度负责在就绪进程之间分配处理机。"),
        TextUnitRow("other3333333", "银行家算法用于避免死锁。"),
    ]

    selector = build_v6_hybrid_evidence_selector_from_rows(
        rows,
        config=V6EvidenceSelectorConfig(
            user_terms=("文件管理", "文件目录"),
            enable_section_aware_text=True,
            enable_dense_rerank=False,
        ),
    )

    refs = [candidate.ref for candidate in selector.search("文件管理中的文件目录如何组织？", top_k=1)]

    assert refs == ["heading111111"]


def test_v6_selector_preserves_multi_facet_anchors_after_dense_rerank():
    rows = [
        TextUnitRow("io1111111111", "I/O 管理负责设备分配、缓冲和设备处理。"),
        TextUnitRow("disk22222222", "磁盘调度通过减少寻道时间优化磁盘访问性能。"),
        TextUnitRow("file33333333", "文件系统使用文件控制块和索引结点组织文件目录。"),
        TextUnitRow("broad4444444", "I/O 管理、磁盘调度和文件系统在课程中如何衔接的综合说明。"),
    ]

    def fake_encoder(texts: list[str]) -> np.ndarray:
        vectors = []
        for text in texts:
            if "综合说明" in text:
                vectors.append([1.0, 0.0])
            elif "文件控制块" in text:
                vectors.append([0.8, 0.2])
            else:
                vectors.append([0.0, 1.0])
        return np.asarray(vectors, dtype=np.float32)

    selector = build_v6_hybrid_evidence_selector_from_rows(
        rows,
        config=V6EvidenceSelectorConfig(
            user_terms=("I/O", "磁盘调度", "文件系统", "文件控制块", "索引结点"),
            enable_dense_rerank=True,
            dense_encoder=fake_encoder,
            dense_rerank_candidate_pool_k=4,
        ),
    )

    refs = [candidate.ref for candidate in selector.search("I/O 管理、磁盘调度和文件系统在课程中如何衔接？", top_k=3)]

    assert refs == ["io1111111111", "disk22222222", "file33333333"]


def test_v6_selector_dense_rerank_is_gated_to_low_and_mixed_questions():
    rows = [
        TextUnitRow("lexical11111", "操作系统是第一层软件。"),
        TextUnitRow("semantic2222", "操作系统负责管理处理机、存储器、设备和文件。"),
        TextUnitRow("other3333333", "银行家算法用于避免死锁。"),
        TextUnitRow("other4444444", "文件控制块描述文件属性。"),
    ]
    encoder_calls: list[list[str]] = []

    def fake_encoder(texts: list[str]) -> np.ndarray:
        encoder_calls.append(texts)
        vectors = []
        for text in texts:
            if "负责什么" in text or "管理处理机" in text:
                vectors.append([1.0, 0.0])
            else:
                vectors.append([0.0, 1.0])
        return np.asarray(vectors, dtype=np.float32)

    selector = build_v6_hybrid_evidence_selector_from_rows(
        rows,
        config=V6EvidenceSelectorConfig(
            user_terms=("操作系统", "管理处理机"),
            enable_dense_rerank=True,
            dense_encoder=fake_encoder,
            dense_rerank_candidate_pool_k=2,
        ),
    )

    low_refs = [candidate.ref for candidate in selector.search("操作系统负责什么？", top_k=2)]
    high_refs = [candidate.ref for candidate in selector.search("这门课程贯穿始终的方法论主线是什么？", top_k=2)]

    assert low_refs[0] == "semantic2222"
    assert high_refs
    assert len(encoder_calls) == 1
