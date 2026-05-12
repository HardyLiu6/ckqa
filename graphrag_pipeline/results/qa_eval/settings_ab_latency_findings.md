# GraphRAG 查询参数 A/B 耗时结论

## B 组配置

本轮只修改 `settings.yaml` 的 query 参数，不重建索引。

`global_search` 最终采用第二档配置：

```yaml
global_search:
  max_context_tokens: 24000
  data_max_tokens: 3000
  map_max_length: 250
  reduce_max_length: 600
```

`drift_search` 最终采用第三档极限配置：

```yaml
drift_search:
  data_max_tokens: 4000
  drift_k_followups: 1
  primer_folds: 1
  primer_llm_max_tokens: 3000
  n_depth: 0
  local_search_top_k_mapped_entities: 4
  local_search_top_k_relationships: 4
  local_search_max_data_tokens: 3000
```

## A/B 结果

| 对比项 | A 组 | B 组 | 变化 |
| --- | ---: | ---: | ---: |
| global 平均耗时，前 4 题 | 120.7850s | 106.3997s | 下降 11.91% |
| global p95，前 4 题 | 138.5347s | 113.0781s | 下降 18.38% |
| global max，前 4 题 | 138.5347s | 113.0781s | 下降 18.38% |
| drift 90s 探针 | 超时 | 仍超时 | 未改善到可用窗口 |

## 运行记录

- A 组 LGB：`results/qa_eval/runs/baseline-diagnostic-lgb-20260512-2120`
- A 组 DRIFT：`results/qa_eval/runs/baseline-diagnostic-drift-20260512-2154`
- B1 global 失败探针：`results/qa_eval/runs/baseline-b-global-tuned-20260512-2205`
- B2 global 单题探针：`results/qa_eval/runs/baseline-b2-global-probe-20260512-2213`
- B2 global 前 4 题：`results/qa_eval/runs/baseline-b2-global-tuned-20260512-2217`
- B2 drift 探针：`results/qa_eval/runs/baseline-b2-drift-tuned-20260512-2228`
- B3 drift 极限探针：`results/qa_eval/runs/baseline-b3-drift-tuned-20260512-2233`

## 关键发现

1. `global_search.max_context_tokens` 不能简单向下压。GraphRAG global 会把 community reports 拆成 map batch，第一档把 `max_context_tokens` 从 12000 降到 6000 后，Q001 反而 180s 超时；更可能是 batch 数变多拖慢。
2. 第二档把 `max_context_tokens` 提到 24000，同时压 `data_max_tokens/map_max_length/reduce_max_length`，global 前 4 题平均耗时从 120.7850s 降到 106.3997s。
3. `drift_search` 在 GraphRAG 3.0.9 中的主要慢点不是 local follow-up，而是 primer 阶段。即使 `drift_k_followups=1`、`n_depth=0`，90s 内仍未返回。
4. 源码检查显示 `drift_search.data_max_tokens`、`primer_llm_max_tokens` 这类配置字段在当前 DRIFT 查询实现中没有实质接入，因此继续只调 settings 对 drift 的收益有限。

## 下一步建议

1. 保留当前 B 组 `global_search` 配置，继续用更多题型验证质量是否下降。
2. 暂时不要把 `drift` 放入同步 QA baseline 全量跑批；它会拖垮总耗时。
3. 若必须优化 drift，需要进入代码/prompt 层：
   - 缩短 DRIFT primer prompt 要求的 2000 字 intermediate answer。
   - 为 `utils/main.py` 增加 drift 专用响应类型或超时控制。
   - 或实现一个 CKQA 自定义 `drift-lite`，复用 local/global 的轻量上下文，而不是直接走官方 DRIFT primer。
