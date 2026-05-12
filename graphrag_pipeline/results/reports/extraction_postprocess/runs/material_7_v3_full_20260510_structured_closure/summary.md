# 关系结构化后处理诊断

- `mode`：strict-closure
- `source_eval_dir`：`/home/sunlight/Projects/ckqa/graphrag_pipeline/results/extraction_eval/runs/material_7_schema_v1_final_full_20260510`
- `output_eval_dir`：`/home/sunlight/Projects/ckqa/graphrag_pipeline/results/extraction_eval/runs/material_7_v3_full_20260510_structured_closure`

## 候选摘要

| candidate | original | kept | repaired | dropped | top_actions | top_drop_reasons |
|---|---:|---:|---:|---:|---|---|
| auto_tuned | 174 | 152 | 1 | 22 | drop_invalid_relationship:22, convert_belongs_to_taxonomy_to_contains:1 | endpoint_type_mismatch:18, semantic_defined_by_term_needs_symbol_cue:4 |
| default | 191 | 171 | 3 | 20 | drop_invalid_relationship:20, convert_belongs_to_taxonomy_to_contains:3 | endpoint_type_mismatch:17, semantic_defined_by_term_needs_symbol_cue:3 |
| schema_aware | 180 | 165 | 12 | 15 | drop_invalid_relationship:15, convert_belongs_to_taxonomy_to_contains:9, add_missing_target_entity:2 | endpoint_type_mismatch:12, semantic_defined_by_term_needs_symbol_cue:2, missing_target:1 |
| schema_aware_directional | 190 | 175 | 7 | 15 | drop_invalid_relationship:15, convert_belongs_to_taxonomy_to_contains:7 | endpoint_type_mismatch:12, semantic_defined_by_term_needs_symbol_cue:3 |
| schema_fewshot | 188 | 173 | 8 | 15 | drop_invalid_relationship:15, convert_belongs_to_taxonomy_to_contains:8 | endpoint_type_mismatch:13, semantic_defined_by_term_needs_symbol_cue:2 |
| schema_fewshot_distilled | 189 | 169 | 15 | 20 | drop_invalid_relationship:20, add_missing_target_entity:6, convert_belongs_to_taxonomy_to_contains:2 | endpoint_type_mismatch:16, semantic_defined_by_term_needs_symbol_cue:3, missing_target:1 |
| schema_fewshot_distilled_v2 | 196 | 169 | 13 | 27 | drop_invalid_relationship:27, convert_belongs_to_taxonomy_to_contains:7, add_missing_target_entity:4 | endpoint_type_mismatch:23, semantic_defined_by_term_needs_symbol_cue:4 |

## 汇总

```json
{
  "original_relationship_count": 1308,
  "kept_relationship_count": 1174,
  "dropped_relationship_count": 134,
  "repaired_relationship_count": 59,
  "actions": {
    "drop_invalid_relationship": 134,
    "convert_belongs_to_taxonomy_to_contains": 37,
    "add_missing_target_entity": 12
  },
  "dropped_by_reason": {
    "endpoint_type_mismatch": 111,
    "semantic_defined_by_term_needs_symbol_cue": 21,
    "missing_target": 2
  }
}
```
