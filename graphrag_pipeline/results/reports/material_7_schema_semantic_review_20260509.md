# Material 7 Schema 语义审查记录

## 结论

本轮发现两处 schema 语义约束与人工 audit gold 不一致，属于“裁判标准”问题，需要先修正，否则后续 prompt 会持续被错误规则带偏。

## 已修正的问题

### 1. `evaluated_by` 不应一刀切禁止 `Term->Assignment`

旧规则要求术语被习题考核时先升格为 `Concept` / `KnowledgePoint`，否则跳过 `evaluated_by`。这会误伤课程问答里的真实考核关系。

证据：

- audit 样本 `pts-0032-8c474610bc` 中，习题直接询问 `PCB` 的作用、信息内容和组织方式。
- `PCB` 在实体 schema 中是典型 `Term`，但它本身就是题目考核对象。

修正：

- `evaluated_by.source_types` 增加 `Term`。
- 允许 `Term->Assignment`，但仅限术语本身被题目直接考核。
- 继续禁止反向 `Assignment->Concept/Term`。
- 继续禁止把别名展开、英文全称解释或普通出现误判为 `evaluated_by`。

### 2. `appears_in` 需要允许平台上下文作为目标

旧规则只允许 `appears_in` 指向 `Course/Chapter/Section/Experiment/Assignment`，不允许指向 `ToolOrPlatform`。这会误伤平台专属内容里的弱定位关系。

证据：

- audit 样本 `pts-0031-a008da8a10` 是 `Linux 系统调用` 小节。
- `系统调用` 是 `Concept`，`Linux` 是 `ToolOrPlatform`，人工 gold 中保留了“该小节介绍 Linux 系统调用”的平台上下文。

修正：

- `appears_in.target_types` 增加 `ToolOrPlatform`。
- 允许 `Concept/Term/AlgorithmOrMethod -> ToolOrPlatform` 表达平台上下文，例如 `系统调用 appears_in Linux`。
- 继续禁止反向 `Section/Assignment appears_in 知识对象`。
- 继续禁止 `ToolOrPlatform->Concept` 这类反向输出。

## 未放宽的边界

- `defined_by` 仍不允许别名、简称、英文全称、编号、存在标志承接关系。
- `related_to` 仍要求 source/target 都能在 `entities` 中找到，不能作为缺失端点占位。
- `applied_in` 仍保持“被应用对象 -> 应用场景”的方向，不允许为了适配错误输出放宽为反向关系。

## 对下一轮实验的影响

这次修正会改变候选 prompt 的 schema 摘要和方向规则，尤其影响：

- `schema_aware`
- `schema_aware_directional`
- `schema_fewshot`
- `schema_fewshot_distilled`
- `schema_fewshot_distilled_v2`

因此本轮已重新生成候选 prompt 和轻量 prompt generation report。下一轮真实 full extraction 应基于这些新候选产物重新跑，先比较 holdout endpoint 是否因为 schema 对齐而回升，再决定是否继续做 prompt v3 或后处理校验。
