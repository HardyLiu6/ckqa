# 原生抽取器实验对比

- `run_id`：native_bridge_full_20260511
- 条目数：5

## Audit 指标（每条 eval run 的 rank 1 候选）

| 实验 | candidate | entity_recall | entity_precision | relation_recall | entity_type_valid | endpoint_valid | parse_success |
|---|---|---:|---:|---:|---:|---:|---:|
| 手工抽取 v2 原版 + metadata-closure（历史 baseline） | schema_fewshot_distilled_v2 | 0.5203 | 0.3677 | 0.2865 | 1.0000 | 1.0000 | 1.0000 |
| exp1 v2 原版 + 原生 strict（7 样本） | native_v2_strict | 0.3795 | 0.2412 | 0.1535 | 1.0000 | 1.0000 | 1.0000 |
| exp3 v2+strict_tuple + 原生 strict（7 样本） | native_v2_strict_tuple | 0.6167 | 0.3619 | 0.3507 | 1.0000 | 1.0000 | 1.0000 |
| exp4 v2+strict_tuple + 原生 strict（20 样本 full 裸） | native_v2_strict_tuple | 0.4518 | 0.2649 | 0.2080 | 1.0000 | 1.0000 | 1.0000 |
| exp4 + metadata-closure（20 样本 full） | native_v2_strict_tuple | 0.5873 | 0.2781 | 0.3217 | 1.0000 | 1.0000 | 1.0000 |

## 格式缺陷统计（直接反映 prompt 在生产 tuple 解析器下的稳定性）

| 实验 | 样本 | 实体 | 关系 | low_parse(≤2) | title 带引号 | type 带引号 | desc 含 ##/<\|> | desc 括号不平衡 |
|---|---:|---:|---:|---:|---:|---:|---:|---:|
| 手工抽取 v2 原版 + metadata-closure（历史 baseline） | 140 | 1742 | 2646 | 2 | 0 | 0 | 0 | 0 |
| exp1 v2 原版 + 原生 strict（7 样本） | 7 | 101 | 135 | 3 | 21 | 0 | 0 | 0 |
| exp3 v2+strict_tuple + 原生 strict（7 样本） | 7 | 117 | 186 | 0 | 0 | 0 | 0 | 0 |
| exp4 v2+strict_tuple + 原生 strict（20 样本 full 裸） | 20 | 409 | 633 | 1 | 0 | 0 | 0 | 0 |
| exp4 + metadata-closure（20 样本 full） | 20 | 474 | 883 | 0 | 0 | 0 | 0 | 0 |

## 数据源

### 手工抽取 v2 原版 + metadata-closure（历史 baseline）
- eval_run_dir：`/home/sunlight/Projects/ckqa/graphrag_pipeline/results/extraction_eval/runs/material_7_v3_full_20260510_metadata_closure`
- scoring_run_id：`material_7_v3_full_20260510_metadata_closure_score`
- eval 产物 sha256（用于复现校验）：
  - `auto_tuned.json`（388106 bytes）sha256=`c0e52c7a6f56e7ea7700d7b64805ffe1a4d6db0adabf15a4cbaade17e9686246`
  - `default.json`（400649 bytes）sha256=`98e9d0036a06ab0e8fed7959dd2183e2c6c6f290eb8110d7acea56619f1fb7a5`
  - `schema_aware.json`（383516 bytes）sha256=`227e11dd605263af84c37d4a5f80bc3968a7b45af9d59e67451c2476c16c4dd5`
  - `schema_aware_directional.json`（394854 bytes）sha256=`0caf1005eee7a1cec59b83da1cd00f0afbf20c74bfdfde8e70a600f4cc8e434f`
  - `schema_fewshot.json`（391907 bytes）sha256=`6788f78b64b15925b7fe55b82159addd5bb832fb47a08d515bc9d67763719a70`
  - `schema_fewshot_distilled.json`（399571 bytes）sha256=`2012caaab889b80bef7fe6d7c0c5dce5f3f89f916e5bb73429c9fcd2220d0586`
  - `schema_fewshot_distilled_v2.json`（412176 bytes）sha256=`ec4f827ec0f9f6527390b84c16cf7bb1b09785f2bcb42498647a3a86d74ac952`

### exp1 v2 原版 + 原生 strict（7 样本）
- eval_run_dir：`/home/sunlight/Projects/ckqa/graphrag_pipeline/results/extraction_eval/runs/native_exp1_v2_strict_20260511`
- scoring_run_id：`native_exp1_v2_strict_rescored`
- eval 产物 sha256（用于复现校验）：
  - `native_v2_strict.json`（75001 bytes）sha256=`66bb4a8beb4cf8b0683be1be0017a64621969b6f33edba04ceaf7ced311601aa`

### exp3 v2+strict_tuple + 原生 strict（7 样本）
- eval_run_dir：`/home/sunlight/Projects/ckqa/graphrag_pipeline/results/extraction_eval/runs/native_exp3_strict_tuple_20260511`
- scoring_run_id：`native_exp3_strict_tuple_scored`
- eval 产物 sha256（用于复现校验）：
  - `native_v2_strict_tuple.json`（89478 bytes）sha256=`b5a679b3e695bce7defe48102715f58330f6529b3746053fe9d056aedae13188`

### exp4 v2+strict_tuple + 原生 strict（20 样本 full 裸）
- eval_run_dir：`/home/sunlight/Projects/ckqa/graphrag_pipeline/results/extraction_eval/runs/native_exp4_strict_tuple_full20_20260511`
- scoring_run_id：`native_exp4_strict_tuple_full20_score`
- eval 产物 sha256（用于复现校验）：
  - `native_v2_strict_tuple.json`（295454 bytes）sha256=`26fd6a30671f760d8fb3706f4f347a3737d1bf4178b2abab964c1d44f03c1574`

### exp4 + metadata-closure（20 样本 full）
- eval_run_dir：`/home/sunlight/Projects/ckqa/graphrag_pipeline/results/extraction_eval/runs/native_exp4_strict_tuple_full20_metadata_closure`
- scoring_run_id：`native_exp4_strict_tuple_full20_metadata_closure_score`
- eval 产物 sha256（用于复现校验）：
  - `native_v2_strict_tuple.json`（398111 bytes）sha256=`1855713e31c173ea6cef5d94e5e6575b584fdba4877791c2dffdd3c5e757ee81`

