# 归档：已被 strict_tuple 替代的旧 tuple 格式候选

## 归档时间

2026-05-11

## 归档原因

桥接验证证明这些候选在原生 tuple 抽取器下存在格式缺陷（引号包裹、括号失衡），且 audit 指标全面低于 `schema_fewshot_distilled_v2_strict_tuple`。保留它们只会增加横评噪声。

## 归档内容

- `schema_aware/`：schema 注入，无方向卡片
- `schema_aware_directional/`：schema + 方向卡片 v1
- `schema_fewshot/`：schema + 完整 audit few-shot（过长，成本高）
- `schema_fewshot_distilled/`：schema + micro-example v1
- `schema_fewshot_distilled_v2/`：schema + micro-example + 负例规则（strict_tuple 的前身）

## 当前活跃候选

- `default/`：GraphRAG 默认 prompt 的课程域微调（基线对照）
- `auto_tuned/`：GraphRAG 官方 prompt-tune 产物（基线对照）
- `schema_aware_directional_v2/`：schema + 方向卡片 v2 + 失败族守卫 + strict_tuple 约束
- `schema_fewshot_distilled_v2_strict_tuple/`：**当前最佳**

## 如果需要恢复

移回 `prompts/candidates/` 即可。建议先合入 `_shared/strict_tuple_format_block.md` 的格式约束再使用。
