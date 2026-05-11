# 关系结构化后处理诊断

- `mode`：strict
- `source_eval_dir`：`/home/sunlight/Projects/ckqa/graphrag_pipeline/results/extraction_eval/runs/material_7_v3_smoke_20260511`
- `output_eval_dir`：`/home/sunlight/Projects/ckqa/graphrag_pipeline/results/extraction_eval/runs/material_7_v3_smoke_20260511_structured_strict`

## 候选摘要

| candidate | original | kept | repaired | dropped | top_actions | top_drop_reasons |
|---|---:|---:|---:|---:|---|---|
| default | 52 | 48 | 3 | 4 | drop_invalid_relationship:4, convert_belongs_to_taxonomy_to_contains:3 | endpoint_type_mismatch:3, semantic_defined_by_term_needs_symbol_cue:1 |
| default_guarded | 56 | 45 | 2 | 11 | drop_invalid_relationship:11, convert_belongs_to_taxonomy_to_contains:2 | missing_target:9, endpoint_type_mismatch:1, semantic_defined_by_term_needs_symbol_cue:1 |
| schema_aware_directional_v2 | 48 | 41 | 3 | 7 | drop_invalid_relationship:7, convert_belongs_to_taxonomy_to_contains:3 | missing_target:5, endpoint_type_mismatch:2 |
| schema_fewshot_distilled_v3 | 47 | 41 | 4 | 6 | drop_invalid_relationship:6, convert_belongs_to_taxonomy_to_contains:4 | missing_target:4, endpoint_type_mismatch:2 |

## 汇总

```json
{
  "original_relationship_count": 203,
  "kept_relationship_count": 175,
  "dropped_relationship_count": 28,
  "repaired_relationship_count": 12,
  "actions": {
    "drop_invalid_relationship": 28,
    "convert_belongs_to_taxonomy_to_contains": 12
  },
  "dropped_by_reason": {
    "endpoint_type_mismatch": 8,
    "semantic_defined_by_term_needs_symbol_cue": 2,
    "missing_target": 18
  }
}
```
