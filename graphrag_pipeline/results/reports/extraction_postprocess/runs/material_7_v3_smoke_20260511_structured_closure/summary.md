# 关系结构化后处理诊断

- `mode`：strict-closure
- `source_eval_dir`：`/home/sunlight/Projects/ckqa/graphrag_pipeline/results/extraction_eval/runs/material_7_v3_smoke_20260511`
- `output_eval_dir`：`/home/sunlight/Projects/ckqa/graphrag_pipeline/results/extraction_eval/runs/material_7_v3_smoke_20260511_structured_closure`

## 候选摘要

| candidate | original | kept | repaired | dropped | top_actions | top_drop_reasons |
|---|---:|---:|---:|---:|---|---|
| default | 52 | 48 | 3 | 4 | drop_invalid_relationship:4, convert_belongs_to_taxonomy_to_contains:3 | endpoint_type_mismatch:3, semantic_defined_by_term_needs_symbol_cue:1 |
| default_guarded | 56 | 54 | 11 | 2 | add_missing_target_entity:8, convert_belongs_to_taxonomy_to_contains:2, drop_invalid_relationship:2 | endpoint_type_mismatch:1, semantic_defined_by_term_needs_symbol_cue:1 |
| schema_aware_directional_v2 | 48 | 46 | 9 | 2 | add_missing_target_entity:5, convert_belongs_to_taxonomy_to_contains:3, drop_invalid_relationship:2 | endpoint_type_mismatch:2 |
| schema_fewshot_distilled_v3 | 47 | 45 | 9 | 2 | add_missing_target_entity:4, convert_belongs_to_taxonomy_to_contains:4, drop_invalid_relationship:2 | endpoint_type_mismatch:2 |

## 汇总

```json
{
  "original_relationship_count": 203,
  "kept_relationship_count": 193,
  "dropped_relationship_count": 10,
  "repaired_relationship_count": 32,
  "actions": {
    "drop_invalid_relationship": 10,
    "convert_belongs_to_taxonomy_to_contains": 12,
    "add_missing_target_entity": 17
  },
  "dropped_by_reason": {
    "endpoint_type_mismatch": 8,
    "semantic_defined_by_term_needs_symbol_cue": 2
  }
}
```
