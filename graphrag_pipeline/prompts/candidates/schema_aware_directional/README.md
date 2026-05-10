# schema_aware_directional

## 作用

- 候选名称：`schema_aware_directional`
- 生成时间：`2026-05-10T16:35:33+08:00`
- 来源类型：`schema_directional`
- 基础 Prompt 来源：`prompts/candidates/auto_tuned/extract_graph.txt`
- 是否注入 schema：`yes`
- 是否包含 few-shot：`no`
- few-shot 策略：`none`

## 用途

- 供后续候选 Prompt 抽取执行、规则化自动评测、top-k Prompt 筛选和问答级验证使用。

## 备注

- schema_aware_directional 继承 schema_aware，并额外加入短关系方向卡片。
- 方向卡片覆盖 applied_in / appears_in / defined_by / evaluated_by / related_to 等高风险关系，不嵌入完整 audit 样本文本。
- 用于降低关系反向、related_to 滥用和缺失端点占位风险。
