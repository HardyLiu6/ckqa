# 原生抽取器实验对比

- `run_id`：native_bridge_7samples_20260511
- 条目数：3

## Audit 指标（每条 eval run 的 rank 1 候选）

| 实验 | candidate | entity_recall | entity_precision | relation_recall | entity_type_valid | endpoint_valid | parse_success |
|---|---|---:|---:|---:|---:|---:|---:|
| exp1 v2 原版 + strict | native_v2_strict | 0.3795 | 0.2412 | 0.1535 | 1.0000 | 1.0000 | 1.0000 |
| exp2 v2 原版 + tolerant | native_v2_tolerant | 0.2571 | 0.1038 | 0.0686 | 1.0000 | 1.0000 | 1.0000 |
| exp3 v2+strict_tuple + strict | native_v2_strict_tuple | 0.6167 | 0.3619 | 0.3507 | 1.0000 | 1.0000 | 1.0000 |

## 格式缺陷统计（直接反映 prompt 在生产 tuple 解析器下的稳定性）

| 实验 | 样本 | 实体 | 关系 | low_parse(≤2) | title 带引号 | type 带引号 | desc 含 ##/<\|> | desc 括号不平衡 |
|---|---:|---:|---:|---:|---:|---:|---:|---:|
| exp1 v2 原版 + strict | 7 | 101 | 135 | 3 | 21 | 0 | 0 | 0 |
| exp2 v2 原版 + tolerant | 7 | 68 | 102 | 4 | 0 | 0 | 0 | 0 |
| exp3 v2+strict_tuple + strict | 7 | 117 | 186 | 0 | 0 | 0 | 0 | 0 |

## 数据源

### exp1 v2 原版 + strict
- eval_run_dir：`/home/sunlight/Projects/ckqa/graphrag_pipeline/results/extraction_eval/runs/native_exp1_v2_strict_20260511`
- scoring_run_id：`native_exp1_v2_strict_rescored`
- eval 产物 sha256（用于复现校验）：
  - `native_v2_strict.json`（75001 bytes）sha256=`66bb4a8beb4cf8b0683be1be0017a64621969b6f33edba04ceaf7ced311601aa`

### exp2 v2 原版 + tolerant
- eval_run_dir：`/home/sunlight/Projects/ckqa/graphrag_pipeline/results/extraction_eval/runs/native_exp2_v2_tolerant_20260511`
- scoring_run_id：`native_exp2_v2_tolerant_rescored`
- eval 产物 sha256（用于复现校验）：
  - `native_v2_tolerant.json`（51499 bytes）sha256=`52b5e013b2bed4bc66df04fbf0685cdb3fa365eb0188901e8cbbfae17f4b2981`

### exp3 v2+strict_tuple + strict
- eval_run_dir：`/home/sunlight/Projects/ckqa/graphrag_pipeline/results/extraction_eval/runs/native_exp3_strict_tuple_20260511`
- scoring_run_id：`native_exp3_strict_tuple_scored`
- eval 产物 sha256（用于复现校验）：
  - `native_v2_strict_tuple.json`（89478 bytes）sha256=`b5a679b3e695bce7defe48102715f58330f6529b3746053fe9d056aedae13188`

