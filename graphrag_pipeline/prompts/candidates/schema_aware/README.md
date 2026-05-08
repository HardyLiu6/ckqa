# schema_aware

## 作用

- 候选名称：`schema_aware`
- 生成时间：`2026-05-08T14:40:52+08:00`
- 来源类型：`schema_augmented`
- 基础 Prompt 来源：`prompts/extract_graph.txt`
- 是否注入 schema：`yes`
- 是否包含 few-shot：`no`
- few-shot 策略：`none`

## 用途

- 供后续候选 Prompt 抽取执行、规则化自动评测、top-k Prompt 筛选和问答级验证使用。

## 备注

- schema_aware 优先基于默认 GraphRAG Prompt自动增强；若 auto_tuned 缺失则回退到 default。
- 在基底 Prompt 上显式注入实体类型、关系类型和关键抽取规则摘要。
- 关系输出仍沿用 GraphRAG tuple 结构，但要求 relationship_description 以 [type=<relation_type>] 开头，便于后续评测解析。
