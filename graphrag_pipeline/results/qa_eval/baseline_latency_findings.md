# QA Baseline 耗时诊断结论

## 评测范围

- 索引来源：复用现有 `graphrag_pipeline/output/`，未执行 `graphrag index`。
- LGB 诊断 run：`results/qa_eval/runs/baseline-diagnostic-lgb-20260512-2120`
- DRIFT 探针 run：`results/qa_eval/runs/baseline-diagnostic-drift-20260512-2154`
- 测试集：`data/eval/qa_test_set.jsonl`，共 32 题；本次耗时诊断先取前 4 道 factual_lookup 小样本，DRIFT 单题 90s 超时探针。

## 结论

| 模式 | 样本数 | 成功数 | 平均耗时 | p95 | 当前判断 |
| --- | ---: | ---: | ---: | ---: | --- |
| basic | 4 | 4 | 31.7949s | 56.4833s | 可作为同步查询的轻量基线，但 Q004 仍会被长答案拖慢 |
| local | 4 | 4 | 36.4711s | 44.8259s | 稳定慢于 basic，主要受实体召回、关系/社区上下文拼装和 LLM 生成影响 |
| global | 4 | 4 | 120.7850s | 138.5347s | 明确瓶颈，map-reduce 处理社区报告导致耗时约为 local 的 3.3 倍 |
| drift | 1 | 0 | 90.0912s | 90.0912s | 默认参数下 90s 超时，不适合直接进入全量同步 baseline |

## 慢在哪里

1. `basic` 主要是 text unit 向量召回后拼上下文生成，链路最短；当前慢点更像最终回答生成长度和模型服务时延。
2. `local` 会从实体向量召回切入，再拼 text units、relationships、community reports，默认 `top_k_entities=10`、`top_k_relationships=10`、`max_context_tokens=12000`，比 basic 多一层图谱上下文扩展。
3. `global` 使用 community reports 的 map-reduce；官方文档也说明低层级、更详细的 reports 会增加耗时和 LLM 资源。本次 4 题最慢 Top 4 全部是 global。
4. `drift` 默认 `drift_k_followups=20`、`primer_folds=5`、`n_depth=3`，会先做 primer，再做多轮 local follow-up，因此默认配置比 local/global 更容易超过交互式请求窗口。

## 下一步优先级

已补充执行 `settings.yaml` 查询参数 A/B，结论见 `results/qa_eval/settings_ab_latency_findings.md`。

1. 先调 `settings.yaml` 查询参数，不重建索引：
   - `global_search`: 降低 `max_context_tokens/data_max_tokens/map_max_length/reduce_max_length`。
   - `drift_search`: 降低 `drift_k_followups`、`primer_folds`、`n_depth`、`local_search_max_data_tokens`。
   - `local_search/basic_search`: 降低 `top_k` 与 `max_context_tokens`。
2. 再用同一批 4 题重跑 A/B 耗时诊断，确认时延下降是否牺牲过多规则评分。
3. DRIFT 在调参前不要跑 32 题全量；先以单题或 2 题超时探针验证能稳定落盘。

## 参考资料

- GraphRAG Local Search: https://microsoft.github.io/graphrag/query/local_search/
- GraphRAG Global Search: https://microsoft.github.io/graphrag/query/global_search/
- GraphRAG DRIFT Search: https://microsoft.github.io/graphrag/query/drift_search/
- GraphRAG YAML Query Config: https://microsoft.github.io/graphrag/config/yaml/#query
- Microsoft Research dynamic community selection: https://www.microsoft.com/en-us/research/blog/graphrag-improving-global-search-via-dynamic-community-selection/
