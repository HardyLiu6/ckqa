# 原生抽取器实验对比

- `run_id`：exp4_vs_exp5_precision_rule_20260512
- 条目数：4

## Audit 指标（每条 eval run 的 rank 1 候选）

| 实验 | candidate | entity_recall | entity_precision | relation_recall | entity_type_valid | endpoint_valid | parse_success |
|---|---|---:|---:|---:|---:|---:|---:|
| exp4 strict_tuple（无精度规则） | native_v2_strict_tuple | 0.4518 | 0.2649 | 0.2080 | 1.0000 | 1.0000 | 1.0000 |
| exp4 + metadata-closure | native_v2_strict_tuple | 0.5873 | 0.2781 | 0.3217 | 1.0000 | 1.0000 | 1.0000 |
| exp5 strict_tuple + 精度规则（裸） | native_v2_strict_tuple_precision | 0.2468 | 0.1563 | 0.1164 | 1.0000 | 1.0000 | 1.0000 |
| exp5 + metadata-closure | native_v2_strict_tuple_precision | 0.3703 | 0.2866 | 0.2063 | 1.0000 | 1.0000 | 1.0000 |

## 格式缺陷统计（直接反映 prompt 在生产 tuple 解析器下的稳定性）

| 实验 | 样本 | 实体 | 关系 | low_parse(≤2) | title 带引号 | type 带引号 | desc 含 ##/<\|> | desc 括号不平衡 |
|---|---:|---:|---:|---:|---:|---:|---:|---:|
| exp4 strict_tuple（无精度规则） | 20 | 409 | 633 | 1 | 0 | 0 | 0 | 0 |
| exp4 + metadata-closure | 20 | 474 | 883 | 0 | 0 | 0 | 0 | 0 |
| exp5 strict_tuple + 精度规则（裸） | 20 | 229 | 371 | 8 | 0 | 0 | 0 | 0 |
| exp5 + metadata-closure | 20 | 290 | 539 | 0 | 0 | 0 | 0 | 0 |

## 数据源

### exp4 strict_tuple（无精度规则）
- eval_run_dir：`/home/sunlight/Projects/ckqa/graphrag_pipeline/results/extraction_eval/runs/native_exp4_strict_tuple_full20_20260511`
- scoring_run_id：`native_exp4_strict_tuple_full20_score`
- eval 产物 sha256（用于复现校验）：
  - `native_v2_strict_tuple.json`（295454 bytes）sha256=`26fd6a30671f760d8fb3706f4f347a3737d1bf4178b2abab964c1d44f03c1574`

### exp4 + metadata-closure
- eval_run_dir：`/home/sunlight/Projects/ckqa/graphrag_pipeline/results/extraction_eval/runs/native_exp4_strict_tuple_full20_metadata_closure`
- scoring_run_id：`native_exp4_strict_tuple_full20_metadata_closure_score`
- eval 产物 sha256（用于复现校验）：
  - `native_v2_strict_tuple.json`（398111 bytes）sha256=`1855713e31c173ea6cef5d94e5e6575b584fdba4877791c2dffdd3c5e757ee81`

### exp5 strict_tuple + 精度规则（裸）
- eval_run_dir：`/home/sunlight/Projects/ckqa/graphrag_pipeline/results/extraction_eval/runs/native_exp5_precision_suppression_full20_20260511`
- scoring_run_id：`native_exp5_precision_full20_score`
- eval 产物 sha256（用于复现校验）：
  - `native_v2_strict_tuple_precision.json`（173841 bytes）sha256=`2d51a94c2a6e1aeb447926486a7f82040be01d7abd9ce3dac54b8dbf88e3156d`

### exp5 + metadata-closure
- eval_run_dir：`/home/sunlight/Projects/ckqa/graphrag_pipeline/results/extraction_eval/runs/native_exp5_precision_full20_metadata_closure`
- scoring_run_id：`native_exp5_precision_full20_metadata_closure_score`
- eval 产物 sha256（用于复现校验）：
  - `native_v2_strict_tuple_precision.json`（252024 bytes）sha256=`33f832e37021d1a89f8b049f88ad7ab79c7ad1d51ec76a15fed47091f8464d0c`

