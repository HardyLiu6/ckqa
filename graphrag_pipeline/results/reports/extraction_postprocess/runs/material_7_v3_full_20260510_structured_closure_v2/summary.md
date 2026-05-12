# 关系结构化后处理诊断

- `mode`：strict-closure
- `source_eval_dir`：`/home/sunlight/Projects/ckqa/graphrag_pipeline/results/extraction_eval/runs/material_7_schema_v1_final_full_20260510`
- `output_eval_dir`：`/home/sunlight/Projects/ckqa/graphrag_pipeline/results/extraction_eval/runs/material_7_v3_full_20260510_structured_closure_v2`

## 候选摘要

| candidate | original | kept | repaired | dropped | top_actions | top_drop_reasons |
|---|---:|---:|---:|---:|---|---|
| auto_tuned | 174 | 164 | 13 | 10 | drop_invalid_relationship:10, retype_container_appears_in_to_contains:10, swap_reversed_endpoints:2, convert_belongs_to_taxonomy_to_contains:1 | endpoint_type_mismatch:6, semantic_defined_by_term_needs_symbol_cue:4 |
| default | 191 | 183 | 15 | 8 | retype_container_appears_in_to_contains:10, drop_invalid_relationship:8, convert_belongs_to_taxonomy_to_contains:3, swap_reversed_endpoints:2 | endpoint_type_mismatch:5, semantic_defined_by_term_needs_symbol_cue:3 |
| schema_aware | 180 | 168 | 15 | 12 | drop_invalid_relationship:12, convert_belongs_to_taxonomy_to_contains:9, swap_reversed_endpoints:5, add_missing_target_entity:2 | endpoint_type_mismatch:7, semantic_defined_by_term_needs_symbol_cue:4, missing_target:1 |
| schema_aware_directional | 190 | 176 | 8 | 14 | drop_invalid_relationship:14, convert_belongs_to_taxonomy_to_contains:7, swap_reversed_endpoints:1 | endpoint_type_mismatch:11, semantic_defined_by_term_needs_symbol_cue:3 |
| schema_fewshot | 188 | 177 | 12 | 11 | drop_invalid_relationship:11, convert_belongs_to_taxonomy_to_contains:8, swap_reversed_endpoints:6 | endpoint_type_mismatch:7, semantic_defined_by_term_needs_symbol_cue:4 |
| schema_fewshot_distilled | 189 | 180 | 26 | 9 | retype_container_appears_in_to_contains:10, drop_invalid_relationship:9, add_missing_target_entity:6, convert_belongs_to_taxonomy_to_contains:2 | endpoint_type_mismatch:5, semantic_defined_by_term_needs_symbol_cue:3, missing_target:1 |
| schema_fewshot_distilled_v2 | 196 | 179 | 23 | 17 | drop_invalid_relationship:17, retype_container_appears_in_to_contains:8, convert_belongs_to_taxonomy_to_contains:7, swap_reversed_endpoints:5 | endpoint_type_mismatch:10, semantic_defined_by_term_needs_symbol_cue:7 |

## 汇总

```json
{
  "original_relationship_count": 1308,
  "kept_relationship_count": 1227,
  "dropped_relationship_count": 81,
  "repaired_relationship_count": 112,
  "actions": {
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
