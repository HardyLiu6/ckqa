# schema_fewshot

## 作用

- 候选名称：`schema_fewshot`
- 生成时间：`2026-05-09T22:15:34+08:00`
- 来源类型：`schema_fewshot`
- 基础 Prompt 来源：`prompts/candidates/auto_tuned/extract_graph.txt`
- 是否注入 schema：`yes`
- 是否包含 few-shot：`yes`
- few-shot 策略：`audit_gold`

## 用途

- 供后续候选 Prompt 抽取执行、规则化自动评测、top-k Prompt 筛选和问答级验证使用。

## 备注

- schema_fewshot 继承 schema_aware，并继续沿用 官方 auto_tuned Prompt 作为底稿。
- few-shot 示例按关系类型覆盖优先贪心选择；同等覆盖下再比较实体类型覆盖和原有样本优先级。
- few-shot 来源样本：pts-0066-4bacda7c7a, pts-0046-a99abcf7ae, pts-0031-a008da8a10, pts-0005-2d46298ac6, pts-0007-a73bc0db67, pts-0049-bd80db3cdf, pts-0073-6ac17ffc34, pts-0032-8c474610bc
- few-shot 覆盖摘要：关系 10/10，实体 10/11；缺失关系：无。
- few-shot 示例已压缩：限制输入长度、实体数量和关系数量，避免候选 Prompt 过长。
