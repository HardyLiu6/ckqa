#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""Few-shot 示例智能选择器。

基于 TF-IDF 向量相似度 + MMR（Maximal Marginal Relevance）多样性采样，
从 audit gold_seed 样本中选择最优的 few-shot 示例组合。

设计依据：
- FsPONER (2024): TF-IDF 向量选择 few-shot 示例用于 NER，F1 提升 7.3%
- RAG-based dynamic prompting (2025): TF-IDF/SBERT 检索 few-shot 示例
- ALLabel (2025): 三阶段 active learning 选择最有信息量的示例
- MMR (Carbonell & Goldstein 1998): 平衡相关性和多样性

使用方式：
  from prompt_tuning.fewshot_selector import select_fewshot_by_tfidf_mmr

  selected = select_fewshot_by_tfidf_mmr(
      candidates=audit_gold_records,
      target_texts=eval_sample_texts,
      k=3,
      lambda_param=0.5,
  )
"""

from __future__ import annotations

import re
from typing import Any, Dict, List, Optional, Sequence

import numpy as np


# ---------------------------------------------------------------------------
# 中文文本预处理
# ---------------------------------------------------------------------------

def _tokenize_chinese(text: str) -> str:
    """对中文文本进行分词预处理，用于 TF-IDF 向量化。

    使用 jieba 精准模式分词，过滤停用词和单字符 token。
    """
    try:
        import jieba
        import logging
        jieba.setLogLevel(logging.CRITICAL)
        tokens = jieba.cut(text, cut_all=False)
    except ImportError:
        # 回退：按字符 bigram 切分
        tokens = _fallback_bigram_tokenize(text)
        return " ".join(tokens)

    # 过滤停用词和过短 token
    filtered = [
        t.strip() for t in tokens
        if t.strip() and len(t.strip()) >= 2 and t.strip() not in _STOPWORDS
    ]
    return " ".join(filtered)


def _fallback_bigram_tokenize(text: str) -> list[str]:
    """回退分词：中文 bigram + 英文单词。"""
    cn_text = re.sub(r"[^\u4e00-\u9fff]", " ", text)
    bigrams = [cn_text[i:i+2] for i in range(len(cn_text) - 1) if " " not in cn_text[i:i+2]]
    en_words = re.findall(r"[a-zA-Z]{2,}", text)
    return bigrams + en_words


_STOPWORDS = frozenset(
    "的 了 在 是 我 有 和 就 不 人 都 一 一个 上 也 很 到 说 要 去 你 会 着 没有 看 好 "
    "自己 这 他 她 它 们 那 些 什么 怎么 如何 为什么 可以 能 应该 需要 进行 使用 通过 "
    "以及 或者 但是 因此 所以 如果 虽然 然而 而且 并且 其中 对于 关于 由于 根据 按照 "
    "等 中 与 及 或 之 为 以 从 被 把 将 让 使 对 而 但 也 都 就 又 所 这 那 它 他 她 "
    "个 了 地 得".split()
)


# ---------------------------------------------------------------------------
# TF-IDF 向量化
# ---------------------------------------------------------------------------

def build_tfidf_matrix(
    texts: Sequence[str],
    max_features: int = 5000,
) -> tuple[Any, Any]:
    """构建 TF-IDF 矩阵。

    返回 (tfidf_matrix, vectorizer)。
    """
    from sklearn.feature_extraction.text import TfidfVectorizer

    # 先分词
    tokenized = [_tokenize_chinese(t) for t in texts]

    vectorizer = TfidfVectorizer(
        max_features=max_features,
        token_pattern=r"(?u)\b\w+\b",
        sublinear_tf=True,
    )
    matrix = vectorizer.fit_transform(tokenized)
    return matrix, vectorizer


def compute_cosine_similarity(matrix: Any, query_vec: Any) -> np.ndarray:
    """计算 query 向量与矩阵中每行的余弦相似度。"""
    from sklearn.metrics.pairwise import cosine_similarity
    return cosine_similarity(query_vec, matrix).flatten()


# ---------------------------------------------------------------------------
# MMR 选择算法
# ---------------------------------------------------------------------------

def mmr_select(
    similarity_to_query: np.ndarray,
    candidate_matrix: Any,
    k: int,
    lambda_param: float = 0.5,
) -> list[int]:
    """Maximal Marginal Relevance 选择。

    MMR = λ * sim(candidate, query) - (1-λ) * max(sim(candidate, selected))

    参数：
      similarity_to_query: 每个候选与目标的相似度向量
      candidate_matrix: 候选的 TF-IDF 矩阵（用于计算候选间相似度）
      k: 选择数量
      lambda_param: 相关性-多样性权衡（0=纯多样性，1=纯相关性）

    返回：选中的候选索引列表
    """
    from sklearn.metrics.pairwise import cosine_similarity

    n = len(similarity_to_query)
    if k >= n:
        return list(range(n))

    selected: list[int] = []
    remaining = set(range(n))

    for _ in range(k):
        best_idx = -1
        best_score = -float("inf")

        for idx in remaining:
            relevance = similarity_to_query[idx]

            # 计算与已选集合的最大相似度
            if selected:
                selected_vecs = candidate_matrix[selected]
                candidate_vec = candidate_matrix[[idx]]
                inter_sim = cosine_similarity(
                    candidate_vec,
                    selected_vecs,
                ).max()
            else:
                inter_sim = 0.0

            mmr_score = lambda_param * relevance - (1 - lambda_param) * inter_sim

            if mmr_score > best_score:
                best_score = mmr_score
                best_idx = idx

        if best_idx >= 0:
            selected.append(best_idx)
            remaining.discard(best_idx)

    return selected


# ---------------------------------------------------------------------------
# 主选择函数
# ---------------------------------------------------------------------------

def select_fewshot_by_tfidf_mmr(
    candidates: Sequence[Dict[str, Any]],
    target_texts: Sequence[str] | None = None,
    k: int = 3,
    lambda_param: float = 0.5,
    text_field: str = "text",
    max_features: int = 3000,
) -> list[Dict[str, Any]]:
    """基于 TF-IDF + MMR 选择最优 few-shot 示例。

    参数：
      candidates: 候选样本列表（需包含 text 字段）
      target_texts: 目标文本列表（待抽取的文本）。若为 None，则使用
                    所有候选文本的质心作为目标（选择最具代表性的样本）。
      k: 选择数量
      lambda_param: MMR 权衡参数（0.5=平衡，0.7=偏相关性，0.3=偏多样性）
      text_field: 候选样本中文本字段名
      max_features: TF-IDF 最大特征数

    返回：选中的候选样本列表（保持原始 dict 结构）
    """
    if not candidates:
        return []
    if k <= 0:
        return []
    if k >= len(candidates):
        return list(candidates)

    # 提取候选文本
    candidate_texts = [str(c.get(text_field) or "") for c in candidates]

    # 构建 TF-IDF 矩阵
    if target_texts:
        all_texts = candidate_texts + list(target_texts)
    else:
        all_texts = candidate_texts

    matrix, vectorizer = build_tfidf_matrix(all_texts, max_features=max_features)

    # 分离候选矩阵和目标矩阵
    n_candidates = len(candidate_texts)
    candidate_matrix = matrix[:n_candidates]

    if target_texts:
        target_matrix = matrix[n_candidates:]
        # 计算目标质心
        query_vec = np.asarray(target_matrix.mean(axis=0))
    else:
        # 无目标时，用所有候选的质心作为目标（选最具代表性的）
        query_vec = np.asarray(candidate_matrix.mean(axis=0))

    # 计算每个候选与目标的相似度
    similarity = compute_cosine_similarity(candidate_matrix, query_vec)

    # MMR 选择
    selected_indices = mmr_select(
        similarity_to_query=similarity,
        candidate_matrix=candidate_matrix,
        k=k,
        lambda_param=lambda_param,
    )

    return [candidates[i] for i in selected_indices]


def select_fewshot_for_sample(
    candidates: Sequence[Dict[str, Any]],
    sample_text: str,
    k: int = 3,
    lambda_param: float = 0.7,
    text_field: str = "text",
    max_features: int = 3000,
) -> list[Dict[str, Any]]:
    """为单个待抽取样本选择最相关的 few-shot 示例。

    与 select_fewshot_by_tfidf_mmr 的区别：
    - 针对单个样本优化（lambda 偏向相关性）
    - 适用于动态 few-shot 场景

    参数：
      candidates: 候选 few-shot 样本列表
      sample_text: 待抽取的文本
      k: 选择数量
      lambda_param: 默认 0.7（偏相关性，因为是针对单样本）
      text_field: 候选样本中文本字段名

    返回：选中的候选样本列表
    """
    return select_fewshot_by_tfidf_mmr(
        candidates=candidates,
        target_texts=[sample_text],
        k=k,
        lambda_param=lambda_param,
        text_field=text_field,
        max_features=max_features,
    )


# ---------------------------------------------------------------------------
# 评估工具：对比不同选择策略的覆盖度
# ---------------------------------------------------------------------------

def evaluate_selection_coverage(
    selected: Sequence[Dict[str, Any]],
    entity_type_names: Sequence[str],
    relation_type_names: Sequence[str],
) -> Dict[str, Any]:
    """评估选中样本的 schema 类型覆盖度。"""
    covered_entity_types: set[str] = set()
    covered_relation_types: set[str] = set()

    for record in selected:
        for entity in record.get("gold_entities") or []:
            etype = str(entity.get("type") or "").strip()
            if etype in set(entity_type_names):
                covered_entity_types.add(etype)
        for relation in record.get("gold_relations") or []:
            rtype = str(relation.get("type") or "").strip()
            if rtype in set(relation_type_names):
                covered_relation_types.add(rtype)

    return {
        "selected_count": len(selected),
        "entity_type_coverage": len(covered_entity_types) / max(1, len(entity_type_names)),
        "relation_type_coverage": len(covered_relation_types) / max(1, len(relation_type_names)),
        "covered_entity_types": sorted(covered_entity_types),
        "covered_relation_types": sorted(covered_relation_types),
        "missing_entity_types": sorted(set(entity_type_names) - covered_entity_types),
        "missing_relation_types": sorted(set(relation_type_names) - covered_relation_types),
    }



# ---------------------------------------------------------------------------
# 混合策略：TF-IDF 相关性 + 贪心类型覆盖
# ---------------------------------------------------------------------------

def select_fewshot_hybrid(
    candidates: Sequence[Dict[str, Any]],
    target_texts: Sequence[str] | None = None,
    k: int = 3,
    prefilter_ratio: float = 0.6,
    text_field: str = "text",
    entity_type_names: Sequence[str] = (),
    relation_type_names: Sequence[str] = (),
    max_features: int = 3000,
) -> tuple[list[Dict[str, Any]], Dict[str, Any]]:
    """混合策略：TF-IDF 相关性预筛选 + 贪心类型覆盖。

    步骤：
      1. 用 TF-IDF 计算所有候选与目标文本的相似度
      2. 取 top-N（N = max(k*2, len*prefilter_ratio)）作为预筛选池
      3. 在预筛选池中用贪心策略选择覆盖最多关系/实体类型的 k 个样本

    这样既保证了内容相关性，又保证了类型覆盖度。

    参数：
      candidates: 候选样本列表
      target_texts: 目标文本列表（若为 None 则跳过预筛选）
      k: 最终选择数量
      prefilter_ratio: 预筛选保留比例（相对于候选总数）
      text_field: 文本字段名
      entity_type_names: schema 实体类型列表（用于覆盖度计算）
      relation_type_names: schema 关系类型列表（用于覆盖度计算）

    返回：(选中样本列表, 选择报告)
    """
    if not candidates or k <= 0:
        return [], {"strategy": "empty", "reason": "no candidates or k<=0"}

    if k >= len(candidates):
        return list(candidates), {"strategy": "all", "reason": "k >= len(candidates)"}

    # 步骤 1-2：TF-IDF 预筛选
    if target_texts:
        candidate_texts = [str(c.get(text_field) or "") for c in candidates]
        all_texts = candidate_texts + list(target_texts)
        matrix, vectorizer = build_tfidf_matrix(all_texts, max_features=max_features)

        n_candidates = len(candidate_texts)
        candidate_matrix = matrix[:n_candidates]
        target_matrix = matrix[n_candidates:]
        query_vec = np.asarray(target_matrix.mean(axis=0))

        similarity = compute_cosine_similarity(candidate_matrix, query_vec)

        # 预筛选：取 top-N
        prefilter_n = max(k * 2, int(len(candidates) * prefilter_ratio))
        prefilter_n = min(prefilter_n, len(candidates))
        top_indices = np.argsort(similarity)[::-1][:prefilter_n]
        pool = [candidates[i] for i in top_indices]
        pool_similarities = {id(candidates[i]): float(similarity[i]) for i in top_indices}
    else:
        pool = list(candidates)
        pool_similarities = {}

    # 步骤 3：在预筛选池中贪心选择覆盖最多类型的样本
    entity_type_set = set(entity_type_names)
    relation_type_set = set(relation_type_names)

    selected: list[Dict[str, Any]] = []
    covered_entity_types: set[str] = set()
    covered_relation_types: set[str] = set()
    remaining_pool = list(pool)

    for _ in range(k):
        if not remaining_pool:
            break

        best_record = None
        best_score = (-1, -1, -1.0)

        for record in remaining_pool:
            # 计算新增覆盖
            record_entity_types = {
                str(e.get("type") or "").strip()
                for e in record.get("gold_entities") or []
                if str(e.get("type") or "").strip() in entity_type_set
            }
            record_relation_types = {
                str(r.get("type") or "").strip()
                for r in record.get("gold_relations") or []
                if str(r.get("type") or "").strip() in relation_type_set
            }
            new_relations = len(record_relation_types - covered_relation_types)
            new_entities = len(record_entity_types - covered_entity_types)
            sim_score = pool_similarities.get(id(record), 0.0)

            score = (new_relations, new_entities, sim_score)
            if score > best_score:
                best_score = score
                best_record = record

        if best_record is not None:
            selected.append(best_record)
            remaining_pool.remove(best_record)
            # 更新覆盖
            for e in best_record.get("gold_entities") or []:
                etype = str(e.get("type") or "").strip()
                if etype in entity_type_set:
                    covered_entity_types.add(etype)
            for r in best_record.get("gold_relations") or []:
                rtype = str(r.get("type") or "").strip()
                if rtype in relation_type_set:
                    covered_relation_types.add(rtype)

    report = {
        "strategy": "hybrid_tfidf_greedy",
        "prefilter_pool_size": len(pool),
        "selected_count": len(selected),
        "selected_sample_ids": [s.get("source_sample_id") for s in selected],
        "covered_entity_types": sorted(covered_entity_types),
        "covered_relation_types": sorted(covered_relation_types),
        "entity_type_coverage": len(covered_entity_types) / max(1, len(entity_type_names)),
        "relation_type_coverage": len(covered_relation_types) / max(1, len(relation_type_names)),
    }

    return selected, report
