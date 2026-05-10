# schema_fewshot_distilled_v2

## 作用

- 候选名称：`schema_fewshot_distilled_v2`
- 生成时间：`2026-05-09T22:15:34+08:00`
- 来源类型：`schema_fewshot_distilled_v2`
- 基础 Prompt 来源：`prompts/candidates/auto_tuned/extract_graph.txt`
- 是否注入 schema：`yes`
- 是否包含 few-shot：`yes`
- few-shot 策略：`distilled_negative_direction_rules`

## 用途

- 供后续候选 Prompt 抽取执行、规则化自动评测、top-k Prompt 筛选和问答级验证使用。

## 备注

- schema_fewshot_distilled_v2 继承 schema_fewshot_distilled，并追加短反向负例和缺端点跳过规则。
- v2 不嵌入完整 audit 样本文本，重点压制 evaluated_by / appears_in / defined_by / applied_in / related_to 的高频残留错误。
- 长度目标仍接近 schema_aware_directional，避免回到 full schema_fewshot 的高成本形态。
- distilled 渲染覆盖摘要：关系 8/10，micro-example 8 条
