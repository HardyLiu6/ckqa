# 课程问答测试集说明

## 1. 用途

本测试集用于评测 GraphRAG 在「智课问答」场景下的四种检索模式（local / global / drift / basic）以及后续 hybrid 模式的回答质量。题目对应 `graphrag_pipeline/output/` 下当前已经存在的查询产物，本流程默认复用现有 `graphrag_pipeline/output/`，不要在出题或评测准备阶段运行 `graphrag index`。

只有当换课、替换 `graphrag_pipeline/output/`，或明确完成新一轮索引产物重建后，才重新撰写题面与参考答案；不要跨课程或跨 output 复用旧题。

## 2. 字段约束

| 字段 | 类型 | 必填 | 评分用途 | 说明 |
| --- | --- | --- | --- | --- |
| `id` | string | 是 | - | 形如 `Q001`，数字位数为 3 到 5 位，例如 `Q001` / `Q00001`。 |
| `category` | enum | 是 | 全部指标按题型分组 | 只能取 `factual_lookup` / `relation_reasoning` / `chapter_summary` / `global_overview`。 |
| `question` | string | 是 | - | 4 到 400 字，使用学生提问口吻，避免把答案写进问题。 |
| `gold_answer_summary` | string | 是 | `semantic_correctness` | 2 到 600 字，写 1 到 2 句参考答案摘要，不要整段搬运原文。 |
| `gold_entities` | string[] | 否，强烈建议 | `entity_hit_rate`、`info_density` | 答案中应出现的章节名、术语、公式名、方法名等实体。空字符串会被过滤。 |
| `gold_text_unit_ids` | string[] | 否，强烈建议 | `retrieval_precision`、`faithfulness` | `text_units.parquet` 相关 `id` 的前 12 位。建议每题 1 到 3 条，正式测试集非空率应不低于 80%。 |
| `must_cite_terms` | string[] | 否 | `must_cite_hit` | 答案中必须出现的字面 token，例如章节号、关键缩写、公式名。 |
| `negative_terms` | string[] | 否 | `negative_hit` | 一旦出现就提示跑偏或幻觉的 token，越少越好。 |
| `notes` | string/null | 否 | - | 出题人备注，最长 600 字，可记录来源 PDF 页码、章节、text_unit 线索。 |

每一行 JSONL 必须是一个完整 JSON 对象，并符合 `QaTestItem` schema。正式评测前还必须满足每类题不少于 6 条。

## 3. 四类题型撰写守则

### 3.1 factual_lookup（事实定位题）

- 直接询问课程原文中的具体事实，例如章节标题、定义、年份、公式名、缩写全称、算法参数。
- 答案应能在原文或 `text_units.parquet` 中直接定位，适合用 ctrl-F 复核。
- `gold_entities` 通常填 1 到 3 个核心术语，`must_cite_terms` 填必须出现的章节号、参数名或编号。
- 期望回答长度为 80 到 250 字，避免要求大段推理或跨章综合。

### 3.2 relation_reasoning（关系理解题）

- 询问两个或多个概念、方法、流程节点之间的关系，例如差异、依赖、前后衔接、因果联系。
- 答案通常需要跨 2 个以上 text_unit，或依赖同一 community 中的关系信息。
- `gold_entities` 应覆盖关系两端的关键实体，`gold_answer_summary` 要写清关系方向。
- 期望回答长度为 150 到 450 字，避免只问单点事实。

### 3.3 chapter_summary（章节总结题）

- 要求对一个章、节或明确教学单元做提纲式归纳，建议答案包含 3 到 5 个要点。
- 答案应覆盖该章节内多个 text_unit，`gold_entities` 至少填 3 个代表性术语。
- `gold_text_unit_ids` 应尽量覆盖章节开头、核心概念和总结段，避免只绑定一个片段。
- 期望回答长度为 300 到 800 字，适合检查模式能否组织局部结构。

### 3.4 global_overview（全局概览题）

- 询问跨章节、跨主题的课程全局问题，例如方法论主线、知识体系、整体流程、综合应用路径。
- 答案需要结合多个章节或 community_reports 才能完整回答，不应只依赖单个 chunk。
- `gold_entities` 应覆盖不同章节的代表性概念，`gold_answer_summary` 写成高层概括。
- 期望回答长度为 300 到 700 字，适合比较 global / drift / hybrid 的整体组织能力。

## 4. 撰写流程

1. 先确认 `graphrag_pipeline/output/` 是本轮要评测的现有查询产物，并且不要在出题或评测准备阶段运行 `graphrag index`。
2. 检查 `graphrag_pipeline/output/documents.parquet`、`text_units.parquet`、`community_reports.parquet` 等文件，梳理课程章节结构与可用证据。
3. 按四类题型均衡出题，正式测试集每类至少 6 条，总量建议 30 到 50 条。
4. 为每题填写 `gold_answer_summary`、`gold_entities`、`must_cite_terms`，并把相关 `text_units.parquet` 的 `id` 前 12 位写入 `gold_text_unit_ids`。
5. 执行校验命令：

```bash
python -m graphrag_pipeline.scripts.qa_eval.test_set_validator \
    graphrag_pipeline/data/eval/qa_test_set.jsonl
```

6. 如果 validator 报某类题不足，应补题而不是降低阈值；如果换课或替换 output，应重新撰写题面与 gold 字段。

## 5. 指标-字段对照速查

| 评测层 | 指标 | 来源字段 | 备注 |
| --- | --- | --- | --- |
| 规则 | `entity_hit_rate` | `gold_entities` | 统计答案对 gold 实体的字面命中比例。 |
| 规则 | `must_cite_hit` | `must_cite_terms` | 必须引用术语全部命中记为通过。 |
| 规则 | `negative_hit` | `negative_terms` | 命中负面术语表示可能跑偏或幻觉。 |
| 规则 | `length_score` | `category` | 按不同题型的期望长度区间打分。 |
| 规则 | `info_density` | `gold_entities` + 答案字符数 | 用实体命中数与答案长度估算信息密度。 |
| 规则 | `citation_format_present` | 模型答案 | 只检测 `[Data:`、`[来源:`、`[出处` 等格式是否出现，不代表引用真实。 |
| 裁判 | `semantic_correctness` | `gold_answer_summary` | LLM 裁判对语义正确性的 0 / 0.5 / 1 判断。 |
| 裁判 | `faithfulness` | `gold_text_unit_ids` 对应原文 | 判断答案是否忠实于 gold 证据文本。 |
| 检索 | `retrieval_precision` | `gold_text_unit_ids` | 命中 gold text_unit 的数量除以 gold 数量。 |
