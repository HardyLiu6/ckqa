# schema_fewshot

## 作用

- 候选名称：`schema_fewshot`
- 生成时间：`2026-04-17T00:06:51+08:00`
- 来源类型：`schema_fewshot`
- 基础 Prompt 来源：`/home/sunlight/Projects/ckqa/graphrag_pipeline/prompts/candidates/auto_tuned/extract_graph.txt`
- 是否注入 schema：`yes`
- 是否包含 few-shot：`yes`
- few-shot 策略：`audit_gold`

## 用途

- 供后续候选 Prompt 抽取执行、规则化自动评测、top-k Prompt 筛选和问答级验证使用。

## 备注

- schema_fewshot 继承 schema_aware，并继续沿用 官方 auto_tuned Prompt 作为底稿。
- few-shot 示例优先复用了 audit_extraction_set.json 中带 gold 标注的样本。
- few-shot 来源样本：pts-0031-05cd31e343, pts-0108-5643e4f01a, pts-0066-80a157e83b
