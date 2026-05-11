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

## 2026-05-11 晚间变更：新增精度向抑制规则

exp4 的 20 样本 full 显示裸抽取 `audit_entity_precision=0.265`，Gate A 未达标。分析发现 LLM 仍抽出大量 gold 未覆盖的过渡性短语、页眉页脚、辅助说明类"实体"，这些是抽取层噪声。

因此在 `-Course Baseline Constraints-` 之后追加 `-精度向抑制规则-` 章节，硬性拒绝 9 类非抽取对象（详见 prompt.txt）。此改动**不替换 strict_tuple 7 条格式约束，两套约束并行生效**。

预期效果：

- `audit_entity_precision` 提升（减少非 gold 实体被抽出）
- `audit_entity_recall` 不明显下降（因为被排除的是噪声，不是真 gold）
- 单样本实体数从 15+ 降到 10-12（更接近 gold 规模）

验证方法：重跑 20 样本 full 原生抽取 + 打分，对比 exp4 的 `native_exp4_strict_tuple_full20_score`。

## 2026-05-12 凌晨变更：回滚精度向抑制规则

exp5（20 样本 full，native_exp5_precision_suppression_full20_20260511）验证：

| 指标（holdout） | exp4（无精度规则） | exp5（有精度规则） | 变化 |
|---|---:|---:|---|
| audit_entity_recall | 0.614 | 0.395 | **-36%** |
| audit_entity_precision | 0.303 | 0.298 | -2%（几乎不变） |
| audit_relation_recall | 0.347 | 0.226 | **-35%** |
| 平均实体数 | 20.45 | 11.45 | -44% |

**结论**：精度向抑制规则的设计初衷（拒绝过渡性短语、页眉页脚等噪声）没有实现，模型
把"不确定是否为噪声"的实体**一并跳过**了，导致 recall 崩盘 36%，而 precision 几乎
没有改善（0.303 → 0.298）。

**根因**：9 类抑制对象中有 3 类需要复杂的上下文推理（"无命名实体指向的代词"、"无
属性描述的一次性引用项"、"课程结构外的通用词"），LLM 对这类负向约束无法精准执行，
只能一刀切。

**行动**：已从 prompt.txt 删除「-精度向抑制规则-」章节，恢复到 strict_tuple 单约束
形态。
