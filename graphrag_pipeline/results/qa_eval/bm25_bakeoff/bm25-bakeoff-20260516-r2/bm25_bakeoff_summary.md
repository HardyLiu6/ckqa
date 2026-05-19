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

- config: `jieba_course_terms`
- k1/b: `1.2` / `0.9`
- recall_at_3: `0.4771`
- rr: `0.5972`
- ndcg_at_5: `0.4925`

## baseline config

- config: `jieba_baseline`
- k1/b: `1.5` / `0.75`
- recall_at_3: `0.3615`
- rr: `0.4646`
- ndcg_at_5: `0.3891`

## 结论

- recall_at_3 delta vs baseline: `+0.1156`
- best config 相比 baseline 的 recall_at_3 有提升，可进入小规模 one-shot smoke。

## overall scores

| config | k1 | b | r@1 | r@3 | r@5 | r@10 | rr | ndcg@5 |
| --- | ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: |
| jieba_course_terms | 1.20 | 0.90 | 0.2198 | 0.4771 | 0.5495 | 0.7052 | 0.5972 | 0.4925 |
| jieba_course_terms | 1.50 | 0.90 | 0.2198 | 0.4719 | 0.5495 | 0.7052 | 0.6024 | 0.4972 |
| jieba_course_terms_thuocl | 1.20 | 0.90 | 0.2510 | 0.4708 | 0.5693 | 0.6911 | 0.5962 | 0.5139 |
| jieba_course_terms_thuocl | 1.50 | 0.90 | 0.2510 | 0.4656 | 0.5693 | 0.6661 | 0.5996 | 0.5186 |
| jieba_course_terms | 1.20 | 0.55 | 0.2172 | 0.4630 | 0.5120 | 0.7130 | 0.5710 | 0.4595 |
| jieba_course_terms_thuocl | 0.90 | 0.75 | 0.2198 | 0.4630 | 0.5380 | 0.6771 | 0.5636 | 0.4804 |
| jieba_course_terms | 1.50 | 0.75 | 0.2198 | 0.4615 | 0.5495 | 0.6974 | 0.5868 | 0.4847 |
| jieba_course_terms_thuocl | 0.90 | 0.90 | 0.2510 | 0.4552 | 0.5589 | 0.6536 | 0.5821 | 0.5041 |
| jieba_course_terms | 0.90 | 0.90 | 0.2198 | 0.4552 | 0.5432 | 0.6677 | 0.5767 | 0.4817 |
| jieba_course_terms_thuocl | 1.20 | 0.75 | 0.2198 | 0.4552 | 0.5693 | 0.6833 | 0.5722 | 0.4987 |
| jieba_course_terms | 0.90 | 0.75 | 0.2198 | 0.4526 | 0.5224 | 0.6599 | 0.5639 | 0.4638 |
| jieba_course_terms_thuocl | 1.50 | 0.55 | 0.2198 | 0.4526 | 0.5328 | 0.6615 | 0.5625 | 0.4750 |
| jieba_course_terms_thuocl | 0.90 | 0.55 | 0.2172 | 0.4474 | 0.5302 | 0.6615 | 0.5548 | 0.4713 |
| jieba_course_terms_thuocl | 1.20 | 0.55 | 0.2276 | 0.4422 | 0.5484 | 0.6693 | 0.5766 | 0.4859 |
| jieba_course_terms | 1.50 | 0.55 | 0.2094 | 0.4422 | 0.5224 | 0.6740 | 0.5519 | 0.4595 |
| jieba_course_terms | 0.90 | 0.55 | 0.2068 | 0.4370 | 0.4859 | 0.6833 | 0.5465 | 0.4380 |
| jieba_thuocl_it_filtered | 0.90 | 0.90 | 0.2484 | 0.4344 | 0.5328 | 0.6432 | 0.5806 | 0.4879 |
| jieba_course_terms_thuocl | 1.50 | 0.75 | 0.2198 | 0.4344 | 0.5693 | 0.6521 | 0.5749 | 0.4995 |
| jieba_thuocl_it_filtered | 0.90 | 0.75 | 0.1755 | 0.4318 | 0.5120 | 0.6432 | 0.5172 | 0.4372 |
| jieba_course_terms | 1.20 | 0.75 | 0.2198 | 0.4292 | 0.5432 | 0.6974 | 0.5698 | 0.4746 |
| jieba_thuocl_it_filtered | 1.20 | 0.55 | 0.1755 | 0.4266 | 0.5016 | 0.6432 | 0.5013 | 0.4218 |
| jieba_thuocl_it_filtered | 1.20 | 0.90 | 0.2510 | 0.4187 | 0.5224 | 0.6807 | 0.5839 | 0.4800 |
| jieba_thuocl_it_filtered | 1.20 | 0.75 | 0.1885 | 0.4161 | 0.5224 | 0.6432 | 0.5373 | 0.4526 |
| jieba_thuocl_it_filtered | 1.50 | 0.90 | 0.2510 | 0.4135 | 0.5146 | 0.6521 | 0.5787 | 0.4774 |
| jieba_thuocl_it_filtered | 1.50 | 0.55 | 0.1859 | 0.4057 | 0.4964 | 0.6432 | 0.5172 | 0.4243 |
| jieba_thuocl_it_filtered | 1.50 | 0.75 | 0.2198 | 0.4031 | 0.5224 | 0.6729 | 0.5627 | 0.4627 |
| jieba_thuocl_it_filtered | 0.90 | 0.55 | 0.1755 | 0.3849 | 0.5120 | 0.6328 | 0.4917 | 0.4232 |
| jieba_baseline | 0.90 | 0.75 | 0.1182 | 0.3745 | 0.4234 | 0.6182 | 0.4402 | 0.3501 |
| jieba_baseline | 1.20 | 0.55 | 0.1286 | 0.3693 | 0.4234 | 0.6078 | 0.4486 | 0.3525 |
| jieba_baseline | 1.50 | 0.90 | 0.1625 | 0.3615 | 0.4740 | 0.6089 | 0.4874 | 0.4085 |
| jieba_baseline | 1.50 | 0.75 | 0.1521 | 0.3615 | 0.4661 | 0.6167 | 0.4646 | 0.3891 |
| jieba_baseline | 0.90 | 0.55 | 0.1182 | 0.3589 | 0.4391 | 0.6078 | 0.4258 | 0.3517 |
| jieba_baseline | 0.90 | 0.90 | 0.1599 | 0.3562 | 0.4505 | 0.6182 | 0.4935 | 0.3936 |
| jieba_baseline | 1.20 | 0.90 | 0.1625 | 0.3562 | 0.4505 | 0.6245 | 0.4931 | 0.3945 |
| jieba_baseline | 1.20 | 0.75 | 0.1286 | 0.3510 | 0.4505 | 0.6167 | 0.4617 | 0.3741 |
| jieba_baseline | 1.50 | 0.55 | 0.1443 | 0.3484 | 0.4339 | 0.6313 | 0.4684 | 0.3641 |

## 复盘摘要（2026-05-16）

第一轮 bakeoff 的价值是证明“教材术语自动抽取”确实比原始 `jieba.cut_for_search` 更适合当前课程 text unit 召回。最佳配置 `jieba_course_terms k1=1.2 b=0.9` 将 overall `recall_at_3` 从 baseline 的 `0.3615` 提升到 `0.4771`，`rr` 从 `0.4646` 提升到 `0.5972`，说明在不调用 GraphRAG / LLM 的前提下，词典与 BM25 参数本身已经能改善证据排序。

但这一轮也暴露了三个不需要真实 smoke 就能发现的问题：

1. `Q002` 的 top refs 被“习题”片段严重污染，说明候选 text unit 需要支持 `exclude_exercises` 或类似章节过滤，否则事实题会被教材习题中的泛化关键词吸走。
2. `Q027` 被大量“磁盘调度”片段挤占，根因是 query expansion 对“调度”触发过宽，把处理机调度类问题误扩成磁盘调度。这属于离线检索规则错误，应该先修规则再进入真实问答。
3. THUOCL IT filtered 不是主收益来源；它单独使用时弱于教材术语，和教材术语合并后有局部收益但不稳定。因此长期通用路线应以“教材术语自动抽取”为主，领域词典只作为可选增强。

本轮结论：可以进入第二轮离线优化，但不建议直接真实 smoke。第二轮应优先做 query expansion 修正、习题过滤、multi-query RRF，并继续用 `gold_text_unit_ids` 做零成本验证。
