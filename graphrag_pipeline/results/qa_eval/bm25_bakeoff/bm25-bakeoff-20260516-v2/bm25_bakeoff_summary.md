# BM25 Retrieval Bakeoff

## 数据来源

- test set: `graphrag_pipeline/data/eval/qa_test_set.jsonl`
- text units: `graphrag_pipeline/output/auto_tuned_crs-20260506-r4slkr_material7_20260513_001047/text_units.parquet`
- questions: `32`
- THUOCL source: `https://github.com/thunlp/THUOCL`
- THUOCL cache: `graphrag_pipeline/.cache/thuocl/THUOCL_IT.txt`
- course terms: `300`
- filtered THUOCL IT terms: `1497`

## best config

- config: `jieba_course_terms_thuocl_multi_rrf_filtered`
- k1/b: `1.2` / `0.75`
- recall_at_3: `0.5568`
- rr: `0.6829`
- ndcg_at_5: `0.5929`

## baseline config

- config: `jieba_baseline`
- k1/b: `1.5` / `0.75`
- recall_at_3: `0.3833`
- rr: `0.4982`
- ndcg_at_5: `0.402`

## 结论

- recall_at_3 delta vs baseline: `+0.1735`
- best config 相比 baseline 的 recall_at_3 有提升，可进入小规模 one-shot smoke。

## overall scores

| config | k1 | b | r@1 | r@3 | r@5 | r@10 | rr | ndcg@5 |
| --- | ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: |
| jieba_course_terms_thuocl_multi_rrf_filtered | 1.20 | 0.75 | 0.3083 | 0.5568 | 0.6318 | 0.7536 | 0.6829 | 0.5929 |
| jieba_course_terms_thuocl_multi_rrf_filtered | 0.90 | 0.75 | 0.2458 | 0.5568 | 0.6318 | 0.7536 | 0.6498 | 0.5693 |
| jieba_course_terms_thuocl_multi_rrf_filtered | 1.50 | 0.75 | 0.2927 | 0.5307 | 0.6630 | 0.7536 | 0.6842 | 0.5932 |
| jieba_course_terms_filtered | 1.20 | 0.90 | 0.2771 | 0.5266 | 0.6260 | 0.7365 | 0.7102 | 0.5811 |
| jieba_course_terms_thuocl_multi_rrf_filtered | 1.20 | 0.90 | 0.3240 | 0.5255 | 0.6552 | 0.7224 | 0.6941 | 0.6064 |
| jieba_course_terms_thuocl_multi_rrf_filtered | 0.90 | 0.90 | 0.3083 | 0.5255 | 0.6161 | 0.7536 | 0.6758 | 0.5817 |
| jieba_course_terms_multi_rrf_filtered | 1.50 | 0.75 | 0.2771 | 0.5214 | 0.6911 | 0.7755 | 0.6738 | 0.5996 |
| jieba_course_terms_thuocl_filtered | 1.20 | 0.90 | 0.2927 | 0.5151 | 0.6005 | 0.7130 | 0.6910 | 0.5675 |
| jieba_course_terms_filtered | 1.50 | 0.90 | 0.2771 | 0.5109 | 0.6573 | 0.7286 | 0.7139 | 0.5974 |
| jieba_course_terms_filtered | 0.90 | 0.90 | 0.2771 | 0.5109 | 0.6104 | 0.7365 | 0.7017 | 0.5707 |
| jieba_course_terms_multi_rrf_filtered | 1.50 | 0.55 | 0.2458 | 0.5109 | 0.6495 | 0.7365 | 0.6565 | 0.5642 |
| jieba_course_terms_thuocl_multi_rrf_filtered | 1.50 | 0.90 | 0.3240 | 0.5099 | 0.6865 | 0.7365 | 0.6925 | 0.6169 |
| jieba_course_terms_thuocl_filtered | 1.20 | 0.75 | 0.2615 | 0.5073 | 0.6005 | 0.7224 | 0.6678 | 0.5533 |
| jieba_course_terms_multi_rrf_filtered | 0.90 | 0.90 | 0.2849 | 0.5057 | 0.6495 | 0.7521 | 0.6849 | 0.5847 |
| jieba_course_terms_multi_rrf_filtered | 1.50 | 0.90 | 0.2849 | 0.5057 | 0.6964 | 0.7443 | 0.6847 | 0.6069 |
| jieba_course_terms_multi_rrf_filtered | 1.20 | 0.90 | 0.2849 | 0.5057 | 0.6651 | 0.7521 | 0.6811 | 0.5917 |
| jieba_course_terms_multi_rrf_filtered | 0.90 | 0.75 | 0.2458 | 0.5057 | 0.6536 | 0.7599 | 0.6417 | 0.5645 |
| jieba_course_terms | 1.20 | 0.90 | 0.2432 | 0.5005 | 0.5948 | 0.7286 | 0.6500 | 0.5348 |
| jieba_course_terms | 0.90 | 0.90 | 0.2432 | 0.5005 | 0.5885 | 0.6974 | 0.6405 | 0.5280 |
| jieba_course_terms_multi_rrf_filtered | 1.20 | 0.55 | 0.2458 | 0.5005 | 0.6354 | 0.7365 | 0.6385 | 0.5489 |
| jieba_course_terms_thuocl_filtered | 1.50 | 0.90 | 0.2927 | 0.4995 | 0.6318 | 0.7130 | 0.6946 | 0.5838 |
| jieba_course_terms_thuocl_filtered | 0.90 | 0.90 | 0.2927 | 0.4995 | 0.5823 | 0.7224 | 0.6829 | 0.5536 |
| jieba_course_terms_thuocl_multi_rrf_filtered | 1.50 | 0.55 | 0.2458 | 0.4995 | 0.6057 | 0.7146 | 0.6396 | 0.5393 |
| jieba_course_terms_thuocl_multi_rrf_filtered | 1.20 | 0.55 | 0.2458 | 0.4995 | 0.6292 | 0.7083 | 0.6354 | 0.5500 |
| jieba_course_terms_thuocl_filtered | 0.90 | 0.75 | 0.2615 | 0.4969 | 0.5823 | 0.7224 | 0.6608 | 0.5391 |
| jieba_course_terms | 1.50 | 0.90 | 0.2432 | 0.4953 | 0.5948 | 0.7286 | 0.6552 | 0.5396 |
| jieba_course_terms_filtered | 1.20 | 0.55 | 0.2771 | 0.4927 | 0.6078 | 0.7365 | 0.6974 | 0.5560 |
| jieba_course_terms | 1.20 | 0.55 | 0.2406 | 0.4927 | 0.5417 | 0.7208 | 0.6350 | 0.4962 |
| jieba_course_terms_multi_rrf_filtered | 1.20 | 0.75 | 0.2771 | 0.4901 | 0.6599 | 0.7599 | 0.6570 | 0.5802 |
| jieba_course_terms_thuocl_multi_rrf_filtered | 0.90 | 0.55 | 0.2458 | 0.4891 | 0.6031 | 0.7318 | 0.6346 | 0.5387 |
| jieba_course_terms_filtered | 1.50 | 0.75 | 0.2771 | 0.4849 | 0.6651 | 0.7443 | 0.7102 | 0.5866 |
| jieba_course_terms | 1.50 | 0.75 | 0.2432 | 0.4849 | 0.5792 | 0.7208 | 0.6448 | 0.5206 |
| jieba_course_terms_filtered | 0.90 | 0.75 | 0.2771 | 0.4823 | 0.6042 | 0.7443 | 0.6877 | 0.5527 |
| jieba_course_terms | 0.90 | 0.75 | 0.2432 | 0.4823 | 0.5677 | 0.6896 | 0.6279 | 0.5079 |
| jieba_course_terms_thuocl | 1.20 | 0.90 | 0.2589 | 0.4786 | 0.5927 | 0.6990 | 0.6310 | 0.5335 |
| jieba_course_terms_thuocl_filtered | 1.50 | 0.55 | 0.2615 | 0.4760 | 0.5979 | 0.7458 | 0.6692 | 0.5424 |
| jieba_course_terms_thuocl | 1.50 | 0.90 | 0.2589 | 0.4734 | 0.5927 | 0.6740 | 0.6336 | 0.5382 |
| jieba_course_terms_filtered | 1.50 | 0.55 | 0.2771 | 0.4719 | 0.6000 | 0.7208 | 0.6972 | 0.5522 |
| jieba_course_terms | 1.50 | 0.55 | 0.2328 | 0.4719 | 0.5521 | 0.6818 | 0.6159 | 0.4962 |
| jieba_course_terms_thuocl_filtered | 0.90 | 0.55 | 0.2615 | 0.4708 | 0.5641 | 0.7146 | 0.6532 | 0.5249 |
| jieba_course_terms_thuocl | 0.90 | 0.75 | 0.2276 | 0.4708 | 0.5615 | 0.6911 | 0.6016 | 0.5000 |
| jieba_course_terms_filtered | 1.20 | 0.75 | 0.2771 | 0.4693 | 0.6026 | 0.7443 | 0.6933 | 0.5549 |
| jieba_course_terms_multi_rrf_filtered | 0.90 | 0.55 | 0.2458 | 0.4693 | 0.6250 | 0.7599 | 0.6414 | 0.5464 |
| jieba_course_terms_thuocl_filtered | 1.50 | 0.75 | 0.2615 | 0.4682 | 0.6318 | 0.7224 | 0.6764 | 0.5668 |
| jieba_course_terms_filtered | 0.90 | 0.55 | 0.2771 | 0.4667 | 0.5911 | 0.7365 | 0.6857 | 0.5412 |
| jieba_course_terms_thuocl_filtered | 1.20 | 0.55 | 0.2615 | 0.4656 | 0.5901 | 0.7146 | 0.6627 | 0.5401 |
| jieba_course_terms_thuocl | 0.90 | 0.90 | 0.2589 | 0.4630 | 0.5823 | 0.6677 | 0.6204 | 0.5237 |
| jieba_course_terms_thuocl | 1.20 | 0.75 | 0.2276 | 0.4630 | 0.5927 | 0.6911 | 0.6070 | 0.5183 |
| jieba_course_terms | 0.90 | 0.55 | 0.2302 | 0.4604 | 0.5156 | 0.7130 | 0.6085 | 0.4740 |
| jieba_course_terms_thuocl | 1.50 | 0.55 | 0.2276 | 0.4604 | 0.5563 | 0.6755 | 0.6016 | 0.4955 |
| jieba_course_terms | 1.20 | 0.75 | 0.2432 | 0.4589 | 0.5729 | 0.7208 | 0.6338 | 0.5112 |
| jieba_course_terms_thuocl | 0.90 | 0.55 | 0.2250 | 0.4552 | 0.5536 | 0.6693 | 0.5888 | 0.4910 |
| jieba_course_terms_thuocl | 1.20 | 0.55 | 0.2354 | 0.4500 | 0.5719 | 0.6833 | 0.6148 | 0.5064 |
| jieba_course_terms_thuocl | 1.50 | 0.75 | 0.2276 | 0.4422 | 0.5927 | 0.6599 | 0.6102 | 0.5191 |
| jieba_thuocl_it_filtered | 0.90 | 0.90 | 0.2484 | 0.4422 | 0.5563 | 0.6729 | 0.6020 | 0.5030 |
| jieba_thuocl_it_filtered | 0.90 | 0.75 | 0.1755 | 0.4396 | 0.5354 | 0.6667 | 0.5352 | 0.4523 |
| jieba_thuocl_it_filtered | 1.20 | 0.55 | 0.1755 | 0.4344 | 0.5250 | 0.6510 | 0.5203 | 0.4378 |
| jieba_thuocl_it_filtered | 1.20 | 0.90 | 0.2510 | 0.4266 | 0.5458 | 0.7042 | 0.6019 | 0.4951 |
| jieba_thuocl_it_filtered | 1.20 | 0.75 | 0.1885 | 0.4240 | 0.5458 | 0.6729 | 0.5588 | 0.4677 |
| jieba_thuocl_it_filtered | 1.50 | 0.90 | 0.2510 | 0.4214 | 0.5380 | 0.6755 | 0.5967 | 0.4925 |
| jieba_thuocl_it_filtered | 1.50 | 0.55 | 0.1859 | 0.4135 | 0.5198 | 0.6510 | 0.5310 | 0.4387 |
| jieba_thuocl_it_filtered | 1.50 | 0.75 | 0.2198 | 0.4109 | 0.5458 | 0.6807 | 0.5807 | 0.4778 |
| jieba_baseline | 0.90 | 0.90 | 0.1755 | 0.3937 | 0.4661 | 0.6479 | 0.5279 | 0.4162 |
| jieba_thuocl_it_filtered | 0.90 | 0.55 | 0.1755 | 0.3927 | 0.5354 | 0.6406 | 0.5091 | 0.4383 |
| jieba_baseline | 0.90 | 0.75 | 0.1339 | 0.3901 | 0.4453 | 0.6417 | 0.4730 | 0.3746 |
| jieba_baseline | 1.20 | 0.55 | 0.1443 | 0.3849 | 0.4297 | 0.6313 | 0.4832 | 0.3688 |
| jieba_baseline | 1.50 | 0.90 | 0.1781 | 0.3833 | 0.4896 | 0.6323 | 0.5194 | 0.4292 |
| jieba_baseline | 1.50 | 0.75 | 0.1677 | 0.3833 | 0.4661 | 0.6401 | 0.4982 | 0.4020 |
| jieba_baseline | 1.20 | 0.90 | 0.1781 | 0.3781 | 0.4661 | 0.6479 | 0.5268 | 0.4157 |
| jieba_baseline | 0.90 | 0.55 | 0.1339 | 0.3745 | 0.4453 | 0.6313 | 0.4588 | 0.3676 |
| jieba_baseline | 1.20 | 0.75 | 0.1443 | 0.3729 | 0.4661 | 0.6339 | 0.4953 | 0.3953 |
| jieba_baseline | 1.50 | 0.55 | 0.1599 | 0.3641 | 0.4401 | 0.6391 | 0.5025 | 0.3804 |

## 复盘摘要（2026-05-16）

第二轮 bakeoff 在第一轮基础上加入 query expansion 修正、习题过滤和 multi-query RRF。最佳配置为 `jieba_course_terms_thuocl_multi_rrf_filtered k1=1.2 b=0.75`，overall `recall_at_3=0.5568`、`rr=0.6829`、`ndcg_at_5=0.5929`。相对本轮 baseline `jieba_baseline k1=1.5 b=0.75` 的 `recall_at_3=0.3833`，提升 `+0.1735`；相对第一轮最佳 `0.4771`，也提升到 `0.5568`。这说明第二轮改动不是微调噪声，而是对证据召回有实质收益。

分题型看，收益最明显的是 `factual_lookup` 与 `relation_reasoning`：

| category | r@3 | 观察 |
| --- | ---: | --- |
| factual_lookup | 0.7500 | 术语词典和习题过滤很有效，适合进入 one-shot Basic 注入验证 |
| relation_reasoning | 0.7083 | multi-query RRF 对多概念问题有帮助 |
| chapter_summary | 0.4458 | 部分章节题仍需要标题/章节层级感知 |
| global_overview | 0.3229 | 仍是纯词法 BM25 的弱项，不能只靠 BM25 解决全局概括 |

关键题目变化：

- `Q025` 从第一轮 best 的 `r@3=0.0000 / first_gold_rank=10` 提升到 `r@3=1.0000 / first_gold_rank=1`，说明 multi-query RRF 对全书结构类问题有明确价值。
- `Q027` 从完全未命中提升到 `first_gold_rank=3`，验证“调度”误扩展修复有效，但 top3 仍只覆盖 1/4 gold，后续需要章节/主线级补全。
- `Q029` 保持 `r@3=0.5000`，且 top10 覆盖 3/4 gold；如果进入真实问答，应使用 top8 而不是 top3 证据，避免过早截断。
- `Q011` 与 `Q019` 出现回退。`Q011` top5 实际包含“避免死锁”总述和“死锁处理方法”等合理证据，但 gold 只标了更窄的 `3.6` 与 `3.7.2`；这提示评测可能需要父子章节等价或 gold 扩展。`Q019` 的 gold 章标题 text unit 中出现 `gogogogog...` 异常文本，应先清理/审计数据再把它当作模型失败。
- `Q002`、`Q031` 仍无 gold 进入 top10。`Q002` 的目标短语过于泛化，当前 BM25 更容易被“OS 结构/多处理机系统目标/系统功能”等片段吸走；`Q031` 的“并发控制”被第八章数据一致性控制吸走，而题目 gold 指向操作系统基本特性、进程同步、死锁处理，说明需要语义/章节约束而不只是词表。

不需要真实 smoke 就能继续发现或修正的问题：

1. 数据清理：检查 text unit 中的异常噪声，例如 `Q019` gold 里的 `gogogogog...`，这会污染章节概括题的 gold 与召回判断。
2. Gold 扩展：对章节题和关系题增加父章节、子章节、相邻标题 text unit 的等价 gold，避免 `Q011` 这类“检索到了合理总述但未命中窄 gold”的假阴性。
3. 字段加权：将 `chapter / section / subsection / heading_path_text` 与正文分开索引或加权，解决 `Q002`、`Q031` 这类泛词被远处正文吸走的问题。
4. 题型策略：`global_overview` 的 `r@3=0.3229` 明显低于事实/关系题，后续真实问答不应把 BM25 作为全局题唯一证据源；至少需要 Basic 原引用、community report 或语义 rerank 辅助。
5. 证据截断：真实注入时建议使用 top8 fused evidence，并在 diagnostics 中记录 `selected_evidence_recall_at_3/5/10`，不要只看 top3。

下一步建议：先做一个“离线修正小任务”，包括异常 text 清理审计、gold 父子扩展评估、section-aware BM25 权重实验；如果这些离线指标稳定，再用 `Q025 / Q027 / Q029` 做极小真实 smoke。暂不建议扩大到全量真实 API。
