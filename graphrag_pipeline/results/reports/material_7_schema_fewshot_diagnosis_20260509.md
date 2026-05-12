# material_7 schema_fewshot / schema_aware 真实抽取诊断报告

## 背景

本报告基于两批真实抽取评分结果：

- `material_7_latest_rules_full_20260509`：包含 `auto_tuned`、`default`、`schema_aware`、`schema_fewshot`
- `material_7_distilled_prompt_full_20260509`：包含 `auto_tuned`、`schema_aware_directional`、`schema_fewshot_distilled`

本轮目标不是固定 prompt 或索引，而是解释 `schema_fewshot` 的收益来源、剩余错误原因，以及 `schema_aware` 的退化原因，并据此确定下一轮调优方向。

## 1. schema_fewshot 效果好的原因与过拟合判断

`schema_fewshot` 在 `material_7_latest_rules_full_20260509` 中综合分最高：

| candidate | all endpoint | all relation recall | all mean tokens | holdout endpoint | holdout relation recall | holdout mean tokens |
|---|---:|---:|---:|---:|---:|---:|
| `auto_tuned` | 0.8365 | 0.0869 | 7361.4 | 0.8448 | 0.1250 | 7186.3 |
| `schema_aware` | 0.7347 | 0.0702 | 8207.3 | 0.7736 | 0.1250 | 8217.5 |
| `schema_fewshot` | 0.9286 | 0.4713 | 14051.4 | 0.9552 | 0.2857 | 13881.3 |

可以判断：`schema_fewshot` 不是纯粹依赖 overlap 的记忆型过拟合。它在 holdout 上仍显著强于 `auto_tuned`，尤其 endpoint valid rate 从 `0.8448` 提升到 `0.9552`，relation recall 从 `0.1250` 提升到 `0.2857`。

但它存在明显的 few-shot 贴合收益与成本风险：

- overlap relation recall 为 `0.5641`，holdout relation recall 为 `0.2857`，说明 audit 示例确实增强了相似样本表现。
- holdout 平均 tokens 为 `13881.3`，约为 `auto_tuned` 的 `1.93x`，超过 `1.35x` 成本线。
- prompt 中直接嵌入较多 audit gold 内容，后续扩大材料范围时可能放大样本分布依赖。

结论：`schema_fewshot` 证明 few-shot 方向是有效的，但 full audit 示例过长，不能直接固定为图谱 prompt。下一步应保留其“关系方向教学”和“端点类型约束”的收益，把完整样本文本替换为短负例和方向规则。

## 2. schema_fewshot 剩余错误原因与修改方案

`schema_fewshot` 剩余端点错误主要集中在以下类型：

| relation | source type | target type | count | 原因 |
|---|---|---|---:|---|
| `applied_in` | `Concept` | `AlgorithmOrMethod` | 2 | 把算法/方法误当应用场景 |
| `appears_in` | `Section` | `ToolOrPlatform` | 1 | 位置关系反向 |
| `applied_in` | `FormulaOrDefinition` | `AlgorithmOrMethod` | 1 | 公式/定义与算法场景混淆 |
| `defined_by` | `Concept` | `Concept` | 1 | 背景解释误判为正式定义 |
| `defined_by` | `Concept` | `Term` | 1 | 术语/参数角色不够明确 |
| `evaluated_by` | `Term` | `Assignment` | 1 | 被考核对象类型过窄或实体类型归类错误 |
| `related_to` | `Concept` | missing | 1 | 缺端点仍生成关系 |

修改方案：

- `applied_in`：明确 source 是被应用对象，target 是知识主题、实验、作业或平台操作场景；不要输出 `Concept -> AlgorithmOrMethod` 来表达“通过某算法实现/处理”。
- `appears_in`：禁止 `Section/Assignment/Experiment -> 知识实体`；若只有位置关系，必须是“知识实体 -> 位置容器”。
- `defined_by`：别名、简称、英文全称、存在标志、背景解释不建边；只有公式、符号、判定条件或显式定义对象才保留。
- `evaluated_by`：如果术语被考核，应先抽成 `Concept/KnowledgePoint`，否则跳过 `evaluated_by`。
- `related_to`：source/target 任一端点缺失时直接跳过，不补 unknown、missing 或临时占位。

## 3. schema_aware 退化原因与改进策略

`schema_aware` 相比 `auto_tuned` 发生退化：

- all endpoint：`0.7347`，低于 `auto_tuned=0.8365`
- all relation recall：`0.0702`，低于 `auto_tuned=0.0869`
- holdout endpoint：`0.7736`，低于 `auto_tuned=0.8448`
- holdout relation recall：`0.1250`，与 `auto_tuned` 持平

核心原因是：`schema_aware` 只注入 schema 与规则，没有提供足够具体的方向示范和反例。模型看到更多关系类型后，更容易把“共现/解释/位置/考核”都建成关系；规则文本增加了约束数量，但缺少可模仿的端点方向案例，导致端点合法率下降。

改进策略：

- 不继续扩大裸 schema 文本；改用短方向卡片和反向负例。
- 将 `schema_aware_directional` 作为比 `schema_aware` 更稳的中间基线，但它仍不够达标。
- 下一轮生成 `schema_fewshot_distilled_v2`：不嵌完整 audit 文本，只保留短负例、方向规则、端点非法时跳过规则。
- 如果 v2 仍不能把 holdout endpoint 提升到 `0.95`，引入后处理 validation：对非法端点关系过滤，或对可确定反向关系做规则化修复后再评分。

## 下一步

实现 `schema_fewshot_distilled_v2`：

- 保留 `schema_fewshot` 的方向教学收益。
- 用短负例替代完整 audit 示例。
- 控制 prompt 长度接近 `schema_aware_directional`，目标不超过 `auto_tuned` tokens 的 `1.35x`。
- 新一轮真实抽取前不 finalize、不写 `.env`、不跑 `graphrag index`。
