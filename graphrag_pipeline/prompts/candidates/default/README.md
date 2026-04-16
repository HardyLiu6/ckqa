# default

## 作用

- 候选名称：`default`
- 生成时间：`2026-04-16T16:44:16+08:00`
- 来源类型：`default_adapted`
- 基础 Prompt 来源：`/home/sunlight/Projects/ckqa/graphrag_pipeline/prompts/extract_graph.txt`
- 是否注入 schema：`no`
- 是否包含 few-shot：`no`
- few-shot 策略：`none`

## 用途

- 供后续候选 Prompt 抽取执行、规则化自动评测、top-k Prompt 筛选和问答级验证使用。

## 备注

- 默认 Prompt 来源：/home/sunlight/Projects/ckqa/graphrag_pipeline/prompts/extract_graph.txt
- 已保留当前 extract_graph Prompt 的 tuple 输出风格，并适配为课程 schema 基线版本。
- 检测到 prompt tuning 样本 120 条，可供后续 few-shot/评测使用。
