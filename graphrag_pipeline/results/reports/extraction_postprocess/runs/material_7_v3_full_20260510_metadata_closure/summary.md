# 关系结构化后处理诊断

- `mode`：strict-metadata-closure
- `source_eval_dir`：`/home/sunlight/Projects/ckqa/graphrag_pipeline/results/extraction_eval/runs/material_7_schema_v1_final_full_20260510`
- `output_eval_dir`：`/home/sunlight/Projects/ckqa/graphrag_pipeline/results/extraction_eval/runs/material_7_v3_full_20260510_metadata_closure`

## 候选摘要

| candidate | original | kept | repaired | dropped | top_actions | top_drop_reasons |
|---|---:|---:|---:|---:|---|---|
| auto_tuned | 383 | 373 | 222 | 10 | metadata_derive_contains_relationship:209, metadata_inject_container_entity:53, drop_invalid_relationship:10, retype_container_appears_in_to_contains:10 | endpoint_type_mismatch:6, semantic_defined_by_term_needs_symbol_cue:4 |
| default | 403 | 395 | 227 | 8 | metadata_derive_contains_relationship:212, metadata_inject_container_entity:50, retype_container_appears_in_to_contains:10, drop_invalid_relationship:8 | endpoint_type_mismatch:5, semantic_defined_by_term_needs_symbol_cue:3 |
| schema_aware | 364 | 352 | 199 | 12 | metadata_derive_contains_relationship:184, metadata_inject_container_entity:48, drop_invalid_relationship:12, convert_belongs_to_taxonomy_to_contains:9 | endpoint_type_mismatch:7, semantic_defined_by_term_needs_symbol_cue:4, missing_target:1 |
| schema_aware_directional | 384 | 370 | 202 | 14 | metadata_derive_contains_relationship:194, metadata_inject_container_entity:51, drop_invalid_relationship:14, convert_belongs_to_taxonomy_to_contains:7 | endpoint_type_mismatch:11, semantic_defined_by_term_needs_symbol_cue:3 |
| schema_fewshot | 386 | 375 | 210 | 11 | metadata_derive_contains_relationship:198, metadata_inject_container_entity:49, drop_invalid_relationship:11, convert_belongs_to_taxonomy_to_contains:8 | endpoint_type_mismatch:7, semantic_defined_by_term_needs_symbol_cue:4 |
| schema_fewshot_distilled | 396 | 387 | 233 | 9 | metadata_derive_contains_relationship:207, metadata_inject_container_entity:50, retype_container_appears_in_to_contains:10, drop_invalid_relationship:9 | endpoint_type_mismatch:5, semantic_defined_by_term_needs_symbol_cue:3, missing_target:1 |
| schema_fewshot_distilled_v2 | 411 | 394 | 238 | 17 | metadata_derive_contains_relationship:215, metadata_inject_container_entity:52, drop_invalid_relationship:17, retype_container_appears_in_to_contains:8 | endpoint_type_mismatch:10, semantic_defined_by_term_needs_symbol_cue:7 |

## 汇总

```json
{
  "original_relationship_count": 2727,
  "kept_relationship_count": 2646,
  "dropped_relationship_count": 81,
  "repaired_relationship_count": 1531,
  "metadata_injected_entity_count": 353,
  "metadata_derived_relationship_count": 1419,
  "actions": {
    "metadata_inject_container_entity": 353,
    "metadata_derive_contains_relationship": 1419,
    "drop_invalid_relationship": 81,
    "swap_reversed_endpoints": 22,
    "retype_container_appears_in_to_contains": 38,
    "convert_belongs_to_taxonomy_to_contains": 37,
    "add_missing_target_entity": 12
  },
  "dropped_by_reason": {
    "endpoint_type_mismatch": 51,
    "semantic_defined_by_term_needs_symbol_cue": 28,
    "missing_target": 2
  }
}
```
