# Hybrid v0 Q021 Evidence Ordering 离线修复记录

## 实验目标

在不调用 GraphRAG API / LLM 的前提下，解释 Q021 的 fused evidence 为什么已经包含 gold-like refs，却排在第 4/5 位以后，并做最小修复。修复必须避免牺牲此前表现较好的 Q017。

## 数据来源

- test set: `graphrag_pipeline/data/eval/qa_test_set.jsonl`
- text units: `graphrag_pipeline/output/auto_tuned_crs-20260506-r4slkr_material7_20260513_001047/text_units.parquet`
- smoke run: `graphrag_pipeline/results/qa_eval/runs/hybrid-v0-citation-contract-smoke-q017-q021-20260517`

## 根因

Q021 问题是：

> 请概括第五章「虚拟存储器」的核心内容。

旧分层器没有命中 `概括第五章`，默认把它判成 `low`。v6 selector 的 dense rerank gate 是 `LOW/MIXED` 才启用，因此 Q021 被误送入 factual/relation 风格的 dense rerank。离线复现显示：

- dense off: `a2b896fc708e, 039ce7bf8cc6, e698d871994b, ...`
- dense on: `a2b896fc708e, e0e32cd74351, 039ce7bf8cc6, d8440049cb5f, ...`

也就是说，BM25 lexical ranking 原本已经把 `e698d871994b` 排到 top3，但 dense rerank 把它挤出了 top8，导致 Q021 的 selected evidence 指标和答案 citation 顺序变差。

## 修复

新增章节总结分层规则：

- `broad_chapter_summary`: 命中 `总结/概括/归纳 + 第X章` 且没有 `1.4` / `第X节` 这类具体小节标记时，判为 `HIGH`，从而跳过 dense rerank。
- `section_summary`: 命中章节总结且含具体小节标记时，判为 `MIXED`，保留 dense rerank 机会，避免影响 Q017 这种具体小节题。

该修复不是 Q021 专用 ref boost，也没有写死任何 gold ID；它调整的是问题分层和 dense gate 边界。

## 离线验证

使用真实 `text_units.parquet`、v6 selector、CUDA BGE-M3 配置做离线候选复查：

| question | layer after fix | gold refs | selector top8 | top3 raw recall | top5 raw recall |
| --- | --- | --- | --- | ---: | ---: |
| Q017 | mixed | `9213459b0aa8,014964257784,09957405da60,616795a1c017,c2d570d976d8` | `9213459b0aa8,616795a1c017,014964257784,f55c02f07061,be67038427c3,5064b027cd17,769fc023aace,09957405da60` | 0.6000 | 0.6000 |
| Q021 | high | `d8440049cb5f,e698d871994b,039ce7bf8cc6` | `a2b896fc708e,039ce7bf8cc6,e698d871994b,554afa0f800c,a6f8f2655d64,a69ecafce350,e0e32cd74351,079991b6aa91` | 0.6667 | 0.6667 |

对比旧 Q021 smoke 的 diagnostics：

- old fused top3: `a2b896fc708e,e0e32cd74351,19ce758b2756`
- old selected_evidence_recall_at_3: `0.0000`
- new selector top3: `a2b896fc708e,039ce7bf8cc6,e698d871994b`
- expected selected evidence top3 raw recall if rerun: `0.6667`

## 防过拟合检查

- 修复逻辑基于问题形态，不基于 Q021 的 gold ref。
- Q017 仍为 `mixed`，dense rerank 仍启用，top3 recall 保持 `0.6000`。
- relation / factual 问题仍按原 LOW/MIXED gate 走 dense rerank，不受 broad chapter summary 规则影响。
- global/mixed 结构题如 Q029 不会被该规则重写，仍需要单独的 multi-facet / section-aware 逻辑评估。

## 下一步建议

先不要扩大真实评测。建议只重跑 Q021 一题 one-shot smoke，验证新 evidence 顺序是否让答案 citation 更早引用 `039ce7bf8cc6/e698d871994b`，并人工确认答案是否仍自然完整。如果 Q021 改善且 Q017 不需要重跑，就再考虑把同类 broad chapter summary 题纳入 4 题小样本。
