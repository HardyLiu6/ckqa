# schema_aware_directional_v2

## 作用

- 候选名称：`schema_aware_directional_v2`
- 生成时间：`2026-05-11T11:17:20+08:00`
- 来源类型：`schema_directional_v2`
- 基础 Prompt 来源：`prompts/candidates/auto_tuned/extract_graph.txt`
- 是否注入 schema：`yes`
- 是否包含 few-shot：`no`
- few-shot 策略：`none`

## 用途

- 供后续候选 Prompt 抽取执行、规则化自动评测、top-k Prompt 筛选和问答级验证使用。

## 备注

- schema_aware_directional_v2 继承 schema_aware_directional，并追加 material_7 高频失败族守卫。
- v2 聚焦 appears_in 反向位置、belongs_to 概念分类误用、defined_by 术语符号线索和端点逐字匹配。
- 不嵌入完整 audit 样本文本，作为低成本 prompt-only 对照。

## 2026-05-11 变更：合入严格 tuple 输出格式约束

桥接验证（`experiments/native_extraction_bridge/`）发现原生 GraphRAG tuple 抽取器对引号 / 括号失衡 / description 边界字符高度敏感，prompt 层需显式约束才能避免格式缺陷。本候选已把 `prompts/candidates/_shared/strict_tuple_format_block.md` 中的 7 条硬约束完整合入，位置在 `-Material 7 v3 Failure-family Guard-` 之后、`-Real Data-` 之前。
