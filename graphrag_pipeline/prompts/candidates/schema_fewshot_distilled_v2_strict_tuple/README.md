# schema_fewshot_distilled_v2_strict_tuple

## 来源

基于 `schema_fewshot_distilled_v2` 候选，仅在「必要输出约束」后追加一段**严格 tuple 输出格式约束**，其他内容（schema / few-shot / 方向卡片 / 负例规则）完全保留。

## 动机

桥接验证发现：`schema_fewshot_distilled_v2` 在原生 GraphRAG 抽取器（tuple 格式）下有三类格式缺陷会进到生产：

1. 实体 title 和 entity_type 被模型包裹双引号（`"4.5.1 分页存储管理的基本方法"` / `"SECTION"`）——原生 `clean_str` 不剥引号，会直接污染图谱。
2. description 末尾偶尔出现孤立的 `)`，破坏 tuple 括号平衡，导致下游 record 被误切。
3. 部分样本整段输出被下游解析器丢弃（中间位置格式破损造成 cascade failure）。

手工 JSON 抽取器因为有容错解析把这些问题吞掉了，所以历史上 `schema_fewshot_distilled_v2` 在手工评测下表现良好；但真正 `graphrag index` 时这些缺陷会原样暴露。

**关于实体类型大小写**：GraphRAG 原生 `GraphExtractor._process_result` 对 entity_type 做 `.upper()`（`Course` 变 `COURSE`）——这是解析器的确定性行为，不算 prompt 缺陷。已在 `run_native_extraction.py` 适配层做了 schema-driven 反向映射（`COURSE → Course`），在 scoring 层也做了大小写不敏感比对，因此 prompt 无需强制要求字面一致。

## 变更

新增「-严格 tuple 输出格式约束（关键）-」章节，含 7 条硬约束，仅聚焦模型自身能控制、且适配层无法救回的 prompt 缺陷：

1. record 形态统一
2. **不加引号**（适配层在 `--strict` 模式下不剥，生产严格失败）
3. **括号严格成对**
4. description 内部不得出现 `)` / `##` / `<|>`
5. 整段输出只有一个 `<|COMPLETE|>`
6. 宁可漏抽也不输出破损 record
7. entity_type 尽量保持 schema 列表字面（大小写由适配层规范化，此处不做硬约束）

## 评测

与原版 `schema_fewshot_distilled_v2` 在同一批样本上对比，评估格式约束是否：

- 降低 parse 失败率（实体数 ≥ 2 的样本占比）
- 降低引号污染率（`quote=0` 为目标）
- 不降低 `audit_entity_recall`（格式约束不应让模型漏掉真实实体）
