# 候选 Prompt 规则化评测对比报告

## 指标对比

| rank | candidate | composite_score | composite_hard | composite_soft | gate_passed | parse_success_rate | schema_hit_rate | entity_type_valid_rate | relation_type_valid_rate | endpoint_valid_rate | duplicate_entity_rate | noise_entity_rate | output_stability | audit_entity_recall | audit_entity_precision | audit_relation_recall | sample_count | success_count |
|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|
| 1 | schema_fewshot | 0.8996 | 0.9676 | 0.5144 | False | 1.0000 | 1.0000 | 1.0000 | 1.0000 | 0.8163 | 0.0000 | 0.0000 | 0.6711 | 0.6607 | 0.5417 | 0.0000 | 5 | 5 |
| 2 | schema_aware | 0.8880 | 0.9559 | 0.5032 | False | 1.0000 | 1.0000 | 1.0000 | 1.0000 | 0.7500 | 0.0000 | 0.0000 | 0.6793 | 0.6607 | 0.5000 | 0.0000 | 5 | 5 |
| 3 | default | 0.9077 | 0.9836 | 0.4775 | False | 1.0000 | 1.0000 | 1.0000 | 1.0000 | 0.9070 | 0.0000 | 0.0000 | 0.6730 | 0.5357 | 0.4917 | 0.0000 | 5 | 5 |
| 4 | auto_tuned | 0.9055 | 0.9840 | 0.4606 | False | 1.0000 | 1.0000 | 1.0000 | 1.0000 | 0.9091 | 0.0000 | 0.0000 | 0.6289 | 0.5893 | 0.4583 | 0.0000 | 5 | 5 |

## Top Candidates (k=2)

- **schema_fewshot**（rank=1, composite_score=0.8996, gate=fail, hard=0.9676, soft=0.5144）
- **schema_aware**（rank=2, composite_score=0.8880, gate=fail, hard=0.9559, soft=0.5032）

## 权重

- `parse_success_rate`：0.2
- `schema_hit_rate`：0.1
- `entity_type_valid_rate`：0.15
- `relation_type_valid_rate`：0.15
- `endpoint_valid_rate`：0.15
- `duplicate_complement`：0.05
- `noise_complement`：0.05
- `output_stability`：0.05
- `audit_entity_recall`：0.025
- `audit_entity_precision`：0.05
- `audit_relation_recall`：0.025
