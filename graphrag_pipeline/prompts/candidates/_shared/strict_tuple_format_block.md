# 严格 tuple 输出格式约束（共享规则模块）

这段约束是为了让模型在 GraphRAG 原生 `GraphExtractor` 下产生**解析器无歧义**的 tuple 输出。原生解析器按 `<|>` / `##` / 尾部 `)` 做严格正则切分，不会剥引号，不容忍括号失衡。

**back-port 到其它候选 prompt 时，把下面这段完整复制进去**，位置建议：放在 "必要输出约束" 之后、"-Base Prompt Note-" 或任何示例段之前。

---

-严格 tuple 输出格式约束（关键）-
以下是 GraphRAG 原生 tuple 解析器的严格要求，适配层不会帮你修正，这些规则必须在你的输出中满足：
1. 每条 record 必须是完整的一行，形如 `("entity"<|>NAME<|>TYPE<|>DESC)` 或 `("relationship"<|>SRC<|>TGT<|>DESC<|>STRENGTH)`；record 之间只用 `##` 分隔。
2. **不要**在 entity_name、entity_type、source_entity、target_entity 外面加任何双引号或单引号，也不要加中文引号“”‘’；这几个字段必须是裸文本。解析器不会剥引号。
3. 每条 record 必须以 `(` 开头、以 `)` 结尾，确保括号严格成对；description 或 evidence 内部不得出现独立的 `)`、`##` 或 `<|>`，否则会被误判为 record 边界。
4. 如需在 description 中引用原文，不要使用引号包裹；若必须引用，改为用顿号或破折号分隔，避免字符串歧义。
5. 整段输出结束时只输出一次 `<|COMPLETE|>`，前后不要加任何额外文字、解释或 markdown。
6. 如果某个 record 内容不确定或格式不完整，**不要输出这条 record**；宁可漏抽也不要输出破损的 tuple。
7. entity_type 从给定类型列表里取；大小写会被解析器规范化，但请尽量保持列表字面（PascalCase），不要用同义词、不要加空格。

---

## 历史

- **2026-05-11 引入**：桥接验证（`experiments/native_extraction_bridge/`）发现 `schema_fewshot_distilled_v2` 原版在原生 tuple 抽取器下 21/101 实体 title 带引号、3/7 样本解析级联失败；这段约束一次性根治格式缺陷。
- **适用范围**：所有使用 GraphRAG 原生 tuple 格式输出的 prompt（目前仓库所有候选都是 tuple 格式底稿）。
- **不适用范围**：如果将来出现 JSON 格式输出的 prompt，这段约束应替换为对应的 JSON 严格性约束。
