# schema_aware

## 作用

- 候选名称：`schema_aware`
- 生成时间：`2026-04-16T16:44:16+08:00`
- 来源类型：`schema_augmented`
- 基础 Prompt 来源：`/home/sunlight/Projects/ckqa/graphrag_pipeline/prompts/extract_graph.txt`
- 是否注入 schema：`yes`
- 是否包含 few-shot：`no`
- few-shot 策略：`none`

## 用途

- 供后续候选 Prompt 抽取执行、规则化自动评测、top-k Prompt 筛选和问答级验证使用。

## 备注

- 在课程基线 Prompt 上显式注入实体类型、关系类型和关键抽取规则摘要。
- 关系输出仍沿用 GraphRAG tuple 结构，但要求 relationship_description 以 [type=<relation_type>] 开头，便于后续评测解析。
