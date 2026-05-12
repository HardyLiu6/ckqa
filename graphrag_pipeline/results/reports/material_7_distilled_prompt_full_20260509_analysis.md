# material_7 distilled prompt full extraction 分析

## 执行概览

- run id：`material_7_distilled_prompt_full_20260509`
- 抽取范围：`auto_tuned`、`schema_aware_directional`、`schema_fewshot_distilled`
- 样本：`data/eval/material_7_audit_extraction_set.json`，20 条
- 运行方式：真实 LLM，`candidate_view=full`
- 抽取结果：3 个候选均为 20/20 success，0 parse error，0 LLM error
- 评分报告：`results/reports/extraction_scoring/runs/material_7_distilled_prompt_full_20260509/`

## 全量评分结论

| rank | candidate | composite_score | endpoint_valid_rate | audit_relation_recall | mean_total_tokens |
|---|---:|---:|---:|---:|---:|
| 1 | `schema_fewshot_distilled` | 0.8995 | 0.7810 | 0.1393 | 9068.3 |
| 2 | `schema_aware_directional` | 0.8795 | 0.7426 | 0.0536 | 8747.9 |
| 3 | `auto_tuned` | 0.8633 | 0.7000 | 0.0536 | 7493.6 |

`schema_fewshot_distilled` 的全量综合分最高，说明方向规则和 micro-example 对实体召回、实体精度、稳定性和关系召回有正向作用；但优势主要体现在全量/overlap 视角，不足以直接进入固定提示词和索引。

## Holdout 验收检查

| candidate | holdout endpoint | holdout relation recall | holdout mean tokens | token ratio vs auto_tuned |
|---|---:|---:|---:|---:|
| `auto_tuned` | 0.6909 | 0.1250 | 7224.9 | 1.00x |
| `schema_aware_directional` | 0.8246 | 0.1250 | 8181.0 | 1.13x |
| `schema_fewshot_distilled` | 0.7419 | 0.1250 | 8795.8 | 1.22x |

固定提示词并索引的前置条件未满足：

- holdout endpoint `>= 0.95`：未满足，最好的是 `schema_aware_directional=0.8246`
- holdout relation recall 比 `auto_tuned` 高至少 `0.10`：未满足，两者均为 `0.1250`
- 平均 total tokens 不超过 `auto_tuned` 的 `1.35x`：满足，`schema_fewshot_distilled=1.22x`
- 真实 full 抽取无 parse/LLM error：满足

## 主要错误原因

1. `schema_fewshot_distilled` 仍出现 `evaluated_by` 反向：`Assignment -> Concept` 共 7 条。说明仅在方向卡片里写正向规则还不够，习题列表场景下模型仍倾向把“习题”当 source。
2. `appears_in` 仍出现 `Section/Assignment -> Concept/AlgorithmOrMethod` 反向。尤其“习题”场景既可能是位置，也可能是考核载体，当前 prompt 没有强制先判断 `evaluated_by`，再退回 `appears_in`。
3. `defined_by` 的 Concept->Concept、Concept->Term、Term->Concept 仍有残留。部分样本其实是别名/简称/存在标志或公式参数，模型还在把解释性语句误当定义关系。

## 下一轮建议

- 先不要固定 prompt，也不要索引。
- 下一轮候选优先做 `schema_fewshot_distilled_v2`：加入反向负例 micro-example，特别是 `evaluated_by: Assignment -> Concept` 与 `appears_in: Section/Assignment -> Concept`。
- 对“习题/作业/练习题”上下文增加决策规则：若题目考核某知识点，输出 `Concept/KnowledgePoint/AlgorithmOrMethod -> Assignment` 的 `evaluated_by`；不要输出 `Assignment -> Concept`。
- 对 `defined_by` 增加更硬的跳过规则：别名、简称、英文全称、存在标志、背景解释不建边；只有公式、符号、判定条件或显式定义对象才保留。
- 若 prompt v2 仍无法把 holdout endpoint 拉到 0.95，建议增加后处理 validation：对端点类型不合法的关系直接过滤或按明确可逆规则修复，再重新评分。
