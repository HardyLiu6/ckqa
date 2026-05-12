# schema_fewshot_distilled

## 作用

- 候选名称：`schema_fewshot_distilled`
- 生成时间：`2026-05-11T11:17:20+08:00`
- 来源类型：`schema_fewshot_distilled`
- 基础 Prompt 来源：`prompts/candidates/auto_tuned/extract_graph.txt`
- 是否注入 schema：`yes`
- 是否包含 few-shot：`yes`
- few-shot 策略：`distilled_relation_micro_examples`

## 用途

- 供后续候选 Prompt 抽取执行、规则化自动评测、top-k Prompt 筛选和问答级验证使用。

## 备注

- schema_fewshot_distilled 继承 schema_aware_directional，只保留关系方向 micro-examples。
- distilled micro-examples 来源于已选 audit gold，但省略完整输入文本，降低 overlap/holdout 泄漏风险。
- 长度目标接近 schema_aware，避免回到长 few-shot Prompt。
- few-shot 覆盖摘要：关系 3/10，实体 7/11；缺失关系：contains, belongs_to, prerequisite_of, depends_on, applied_in ...。
- distilled 渲染覆盖摘要：关系 3/10，micro-example 3 条
- 本轮压缩参数优先控制 Prompt 长度；few-shot 关系覆盖不足需要后续真实抽取/评分验证，不能解读为抽取质量提升。
