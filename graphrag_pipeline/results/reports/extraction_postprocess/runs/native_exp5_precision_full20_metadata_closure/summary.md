# 关系结构化后处理诊断

- `mode`：strict-metadata-closure
- `source_eval_dir`：`/home/sunlight/Projects/ckqa/graphrag_pipeline/results/extraction_eval/runs/native_exp5_precision_suppression_full20_20260511`
- `output_eval_dir`：`/home/sunlight/Projects/ckqa/graphrag_pipeline/results/extraction_eval/runs/native_exp5_precision_full20_metadata_closure`

## 候选摘要

| candidate | original | kept | repaired | dropped | top_actions | top_drop_reasons |
|---|---:|---:|---:|---:|---|---|
| native_v2_strict_tuple_precision | 570 | 539 | 248 | 31 | metadata_derive_contains_relationship:199, metadata_inject_container_entity:54, drop_invalid_relationship:31, add_missing_target_entity:5 | endpoint_type_mismatch:14, missing_source:7, missing_target:6, missing_endpoint:4 |

## 汇总

```json
{
  "original_relationship_count": 570,
  "kept_relationship_count": 539,
  "dropped_relationship_count": 31,
  "repaired_relationship_count": 248,
  "metadata_injected_entity_count": 54,
  "metadata_derived_relationship_count": 199,
  "actions": {
    "metadata_inject_container_entity": 54,
    "metadata_derive_contains_relationship": 199,
    "add_missing_target_entity": 5,
    "drop_invalid_relationship": 31,
    "convert_belongs_to_taxonomy_to_contains": 2,
    "add_missing_source_entity": 2,
    "metadata_skip_existing_entity_type_conflict": 1,
    "swap_reversed_endpoints": 1
  },
  "dropped_by_reason": {
    "missing_target": 6,
    "endpoint_type_mismatch": 14,
    "missing_source": 7,
    "missing_endpoint": 4
  }
}
```
