# GraphRAG Global Search 高超时预算修补实验记录

> **结论：** `baseline-lgb-canonical-20260513-1835` 中 3 条 `GraphRagApiError` 不是无效样本，也不应覆盖修补；它们代表原 240 秒单次预算和 HTTP 包装条件下的稳定性失败。本修补实验在同一 test set、同一 canonical index、同一 `global_search` G0 参数和同一 One API 入口下，仅把单题 timeout budget 提高到 `7200s`，验证这些题是否能真实跑完。

## 实验边界

- canonical run：`graphrag_pipeline/results/qa_eval/runs/baseline-lgb-canonical-20260513-1835`
- repair run：`graphrag_pipeline/results/qa_eval/runs/global-repair-high-timeout-20260515-g0-20260515-204944`
- 题目：`Q025`、`Q026`、`Q029`
- 模式：`graphrag-global-search:latest`
- test set：`graphrag_pipeline/data/eval/qa_test_set.jsonl`
- index：`graphrag_pipeline/output/auto_tuned_crs-20260506-r4slkr_material7_20260513_001047`
- API base：`http://127.0.0.1:3301/v1`
- 执行入口：GraphRAG CLI，`python -m graphrag query --root . --method global`
- timeout budget：`7200s`
- stop-after-timeout：`0`
- BGE-M3 评分：本地缓存模型，CPU 离线执行；CUDA 不可用，当前环境无 NVIDIA driver

## Global Search 参数

```json
{
  "concurrent_requests": 20,
  "max_context_tokens": 24000,
  "data_max_tokens": 3000,
  "map_max_length": 250,
  "reduce_max_length": 600
}
```

## 执行命令

```bash
/home/sunlight/miniconda3/envs/graphrag-oneapi/bin/python -m graphrag_pipeline.scripts.qa_eval.run_global_cli_tuning \
  --graphrag-root graphrag_pipeline \
  --test-set graphrag_pipeline/data/eval/qa_test_set.jsonl \
  --index-output-dir graphrag_pipeline/output/auto_tuned_crs-20260506-r4slkr_material7_20260513_001047 \
  --groups G0 \
  --question-ids Q025 Q026 Q029 \
  --run-id-prefix global-repair-high-timeout-20260515 \
  --python-executable /home/sunlight/miniconda3/envs/graphrag-oneapi/bin/python \
  --request-timeout-seconds 7200 \
  --stop-after-timeout-count 0
```

算法评分命令：

```bash
HF_HUB_OFFLINE=1 TRANSFORMERS_OFFLINE=1 conda run -n graphrag-oneapi python -m graphrag_pipeline.scripts.qa_eval.algorithmic_scorer \
  --run-dir graphrag_pipeline/results/qa_eval/runs/global-repair-high-timeout-20260515-g0-20260515-204944 \
  --bge-model /home/sunlight/.cache/huggingface/hub/models--BAAI--bge-m3/snapshots/5617a9f61b028005a4858fdac845db406aefb181 \
  --bge-device cpu \
  --bge-batch-size 8
```

## 延迟结果

| question_id | category | success | elapsed_seconds | answer_chars | error_type |
| --- | --- | ---: | ---: | ---: | --- |
| `Q025` | `global_overview` | true | `236.8949` | `1499` |  |
| `Q026` | `global_overview` | true | `216.2702` | `2337` |  |
| `Q029` | `global_overview` | true | `190.7918` | `1655` |  |

汇总：

| total | success | error | timeout_like | mean_s | p95_s | max_s |
| ---: | ---: | ---: | ---: | ---: | ---: | ---: |
| `3` | `3` | `0` | `0` | `214.6523` | `236.8949` | `236.8949` |

## 算法评分结果

| question_id | semantic_coverage_f1 | keyword_recall | citation_recall_at_3 | citation_rr | effective_score_experimental |
| --- | ---: | ---: | ---: | ---: | ---: |
| `Q025` | `1.0000` | `0.4167` | `0.0000` | `0.0769` | `0.4553` |
| `Q026` | `0.9524` | `0.8333` | `0.0000` | `0.0000` | `0.5096` |
| `Q029` | `0.9333` | `0.6667` | `0.2500` | `0.3333` | `0.5028` |

分组均值：

| category | semantic_coverage_f1 | citation_recall_at_3 | citation_rr | elapsed_seconds | error_count | effective_score_experimental |
| --- | ---: | ---: | ---: | ---: | ---: | ---: |
| `global_overview` | `0.9619` | `0.0833` | `0.1367` | `214.6523` | `0.0000` | `0.4892` |

## 有效性解释

1. 本实验没有改 test set、index、GraphRAG `global_search` G0 参数或模型入口，只提高了外层等待预算，因此可以作为 canonical run 的修补证据。
2. 这 3 题在高预算下均能返回，说明 canonical 中的 `GraphRagApiError` 主要来自原 HTTP 包装层 `240s * 3` 超时窗口，而不是题目永久不可答。
3. 但三题耗时仍接近 `190s-237s`，不能证明 global 已适合默认路由；它只证明“给足预算可以跑完”。
4. 语义覆盖较高，说明答案内容与 gold summary 的主语义一致；citation 指标低，说明 Reports 引用映射到 gold text units 的排序命中仍弱，不能单独用本修补结果抬高 global 的路由优先级。
5. canonical run 不应被覆盖。路由决策应同时保留：
   - canonical：反映原始稳定性与超时风险；
   - repair：反映高预算下 global 的可答性上限。

## 对路由决策的影响

- `global` 不应进入默认 v0 路由；三题平均 `214.65s` 对学生问答仍过慢。
- `global_overview` 可以作为离线高质量补充或人工报告生成路径，而不是交互式默认路径。
- 如果后续要让 `global` 进入路由，必须先证明在较低预算内稳定返回，例如 `<=120s` 且 `error_count=0`，并改善 citation 命中。
