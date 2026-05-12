# 关系结构化后处理诊断

- `mode`：strict-metadata-closure
- `source_eval_dir`：`/home/sunlight/Projects/ckqa/graphrag_pipeline/results/extraction_eval/runs/native_exp4_strict_tuple_full20_20260511`
- `output_eval_dir`：`/home/sunlight/Projects/ckqa/graphrag_pipeline/results/extraction_eval/runs/native_exp4_strict_tuple_full20_metadata_closure`

## 候选摘要

| candidate | original | kept | repaired | dropped | top_actions | top_drop_reasons |
|---|---:|---:|---:|---:|---|---|
| native_v2_strict_tuple | 919 | 883 | 407 | 36 | metadata_derive_contains_relationship:286, metadata_inject_container_entity:50, drop_invalid_relationship:36, convert_belongs_to_taxonomy_to_contains:11 | endpoint_type_mismatch:21, missing_source:10, missing_target:4, semantic_defined_by_term_needs_symbol_cue:1 |

## 汇总

```json
{
  "original_relationship_count": 919,
  "kept_relationship_count": 883,
  "dropped_relationship_count": 36,
  "repaired_relationship_count": 407,
  "metadata_injected_entity_count": 50,
  "metadata_derived_relationship_count": 286,
  "actions": {
    "metadata_inject_container_entity": 50,
    "metadata_derive_contains_relationship": 286,
    "add_missing_source_entity": 7,
    "convert_belongs_to_taxonomy_to_contains": 11,
    "drop_invalid_relationship": 36,
    "add_missing_target_entity": 8,
    "metadata_skip_existing_entity_type_conflict": 3,
    "retype_container_appears_in_to_contains": 2
  },
  "dropped_by_reason": {
    "missing_source": 10,
    "endpoint_type_mismatch": 21,
    "missing_target": 4,
    "semantic_defined_by_term_needs_symbol_cue": 1
  }
}
```
