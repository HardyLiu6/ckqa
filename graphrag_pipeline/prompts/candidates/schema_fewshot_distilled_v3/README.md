# schema_fewshot_distilled_v3

## 作用

- 候选名称：`schema_fewshot_distilled_v3`
- 生成时间：`2026-05-11T11:17:20+08:00`
- 来源类型：`schema_fewshot_distilled_v3`
- 基础 Prompt 来源：`prompts/candidates/auto_tuned/extract_graph.txt`
- 是否注入 schema：`yes`
- 是否包含 few-shot：`yes`
- few-shot 策略：`failure_family_micro_examples`

## 用途

- 供后续候选 Prompt 抽取执行、规则化自动评测、top-k Prompt 筛选和问答级验证使用。

## 备注

- schema_fewshot_distilled_v3 采用低成本失败族守卫，不再继承长 schema/few-shot 正文。
- v3 继续只使用 1 条 micro-example，不嵌入完整 audit text，目标成本不超过 default 的 1.10 倍。
- 重点压制 appears_in 反向、belongs_to 概念分类、defined_by 非符号术语和缺失端点。
- distilled 渲染覆盖摘要：关系 3/10，micro-example 3 条
- 本轮压缩参数优先控制 Prompt 长度；few-shot 关系覆盖不足需要后续真实抽取/评分验证，不能解读为抽取质量提升。
