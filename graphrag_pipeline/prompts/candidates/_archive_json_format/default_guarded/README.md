# default_guarded

## 作用

- 候选名称：`default_guarded`
- 生成时间：`2026-05-11T11:17:20+08:00`
- 来源类型：`default_guarded`
- 基础 Prompt 来源：`prompts/extract_graph.txt`
- 是否注入 schema：`yes`
- 是否包含 few-shot：`no`
- few-shot 策略：`none`

## 用途

- 供后续候选 Prompt 抽取执行、规则化自动评测、top-k Prompt 筛选和问答级验证使用。

## 备注

- default_guarded 继承 default，只追加严格 JSON 根对象与端点匹配守卫。
- 用于验证强基线在不引入长 few-shot 的情况下能否降低 parse/leak 风险。
