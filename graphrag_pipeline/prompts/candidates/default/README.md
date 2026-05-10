# default

## 作用

- 候选名称：`default`
- 生成时间：`2026-05-10T12:03:26+08:00`
- 来源类型：`default_adapted`
- 基础 Prompt 来源：`prompts/extract_graph.txt`
- 是否注入 schema：`no`
- 是否包含 few-shot：`no`
- few-shot 策略：`none`

## 用途

- 供后续候选 Prompt 抽取执行、规则化自动评测、top-k Prompt 筛选和问答级验证使用。

## 备注

- 默认 Prompt 来源：prompts/extract_graph.txt
- default 候选直接基于当前 GraphRAG 默认 extract_graph Prompt 做轻量课程域微调，并保留原始结构与输出格式。
- 检测到 prompt tuning 样本 80 条，可供后续 few-shot/评测使用。
