# schema_fewshot

## 作用

- 候选名称：`schema_fewshot`
- 生成时间：`2026-05-08T14:40:52+08:00`
- 来源类型：`schema_fewshot`
- 基础 Prompt 来源：`prompts/extract_graph.txt`
- 是否注入 schema：`yes`
- 是否包含 few-shot：`yes`
- few-shot 策略：`minimal_manual_examples`

## 用途

- 供后续候选 Prompt 抽取执行、规则化自动评测、top-k Prompt 筛选和问答级验证使用。

## 备注

- schema_fewshot 继承 schema_aware，并继续沿用 默认 GraphRAG Prompt 作为底稿。
- 未发现可直接复用的 audit gold 标注，将退回手写最小 few-shot 示例。
- 当前 few-shot 使用手写最小示例，仅作为 audit gold 缺失时的降级方案。
