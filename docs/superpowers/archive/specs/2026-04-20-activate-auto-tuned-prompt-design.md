# GraphRAG Auto-Tuned Prompt 固化设计

- 日期：2026-04-20
- 范围：`graphrag_pipeline/settings.yaml`、`graphrag_pipeline/.env`、`graphrag_pipeline/scripts/prompt_tuning/`、`graphrag_pipeline/tests/`
- 目标：把 `prompts/candidates/auto_tuned/` 正式固化为当前 GraphRAG 索引链路使用的活动 Prompt，并完成一次首版索引构建。

## 1. 背景

当前仓库已经有 GraphRAG 官方 `prompt-tune` 产出的候选目录 `prompts/candidates/auto_tuned/`，但 `graphrag index --root .` 实际读取的仍是 `settings.yaml` 中写死的 `prompts/extract_graph.txt`、`prompts/summarize_descriptions.txt`、`prompts/community_report_graph.txt` 等活动 Prompt 路径。

这意味着“候选 Prompt 已选定”为事实，但“索引真正使用该 Prompt”还没有落到运行链路里。

## 2. 设计目标

本次只做最小可运行闭环：

1. 增加一个命令行脚本，把任意候选 Prompt 固化到 `prompts/final/<candidate>/`。
2. 用 `.env` 记录当前活动 Prompt 路径和候选名，避免继续把“选中哪个候选”留在口头约定里。
3. 让 `settings.yaml` 从 `.env` 读取索引阶段用到的 Prompt 文件路径。
4. 执行一次 `auto_tuned` 激活与首版 GraphRAG 索引构建。

## 3. 方案选型

### 3.1 不采用：直接覆盖 `prompts/*.txt`

- 优点：实现最快。
- 缺点：覆盖默认 Prompt 后难以追踪来源，不利于回滚，也弱化 `prompts/final/` 的意义。

### 3.2 采用：候选固化 + `.env` 激活

- 新增 `finalize_candidate_prompt.py` 脚本。
- 激活时把候选文件复制到 `prompts/final/<candidate>/`。
- 在 `.env` 中更新活动 Prompt 路径，`settings.yaml` 改为读取这些变量。
- 额外写一份 `prompts/final/active_prompt.json` 记录当前激活元信息。

这是当前最稳妥、复杂度最低、也最容易回滚的做法。

## 4. 详细设计

### 4.1 新脚本职责

脚本入口放在：

- 实现：`graphrag_pipeline/scripts/prompt_tuning/finalize_candidate_prompt.py`
- 兼容入口：`graphrag_pipeline/scripts/finalize_candidate_prompt.py`

脚本职责：

1. 从 `prompts/candidates/manifest.json` 校验候选存在。
2. 解析候选目录中的可用 Prompt 文件。
3. 复制候选文件到 `prompts/final/<candidate>/`。
4. 更新 `.env` 中活动 Prompt 变量：
   - `GRAPHRAG_ACTIVE_PROMPT_CANDIDATE`
   - `GRAPHRAG_ENTITY_EXTRACTION_PROMPT_FILE`
   - `GRAPHRAG_SUMMARIZE_DESCRIPTIONS_PROMPT_FILE`
   - `GRAPHRAG_COMMUNITY_REPORT_GRAPH_PROMPT_FILE`
   - `GRAPHRAG_COMMUNITY_REPORT_TEXT_PROMPT_FILE`
   - `GRAPHRAG_CLAIM_EXTRACTION_PROMPT_FILE`
5. 写出 `prompts/final/active_prompt.json`，记录激活时间、候选名、目标文件与保留默认文件。

### 4.2 文件映射规则

最小规则如下：

- `extract_graph.txt`：优先取候选目录中的 `extract_graph.txt`，若不存在则回退 `prompt.txt`
- `summarize_descriptions.txt`：候选有则激活，否则保留默认 `prompts/summarize_descriptions.txt`
- `community_report_graph.txt`：候选有则激活，否则保留默认 `prompts/community_report_graph.txt`
- `community_report_text.txt`：当前候选未提供，继续保留默认 `prompts/community_report_text.txt`
- `extract_claims.txt`：当前候选未提供，继续保留默认 `prompts/extract_claims.txt`

这样既能让 `auto_tuned` 生效，也不要求所有候选目录都具备完整 Prompt 集。

### 4.3 配置改造

`settings.yaml` 中下列字段改为读取 `.env`：

- `extract_graph.prompt`
- `summarize_descriptions.prompt`
- `extract_claims.prompt`
- `community_reports.graph_prompt`
- `community_reports.text_prompt`

这样索引阶段到底读取哪套 Prompt，就由 `.env` 指向的“活动 Prompt”决定，而不是继续写死在 YAML 中。

### 4.4 测试范围

至少覆盖：

1. 候选目录包含 `extract_graph.txt` / `summarize_descriptions.txt` 时，脚本能正确复制并更新 `.env`
2. 候选缺少部分文件时，脚本会保留默认 Prompt 路径
3. 候选不存在时，脚本报清晰错误
4. `settings.yaml` 已改为环境变量驱动，避免激活后索引仍读取老路径

## 5. 验证策略

验证顺序：

1. 运行新增脚本测试
2. 运行相关回归测试
3. 执行 `python scripts/finalize_candidate_prompt.py --candidate auto_tuned`
4. 执行 `python -m graphrag index --root .`
5. 确认 `output/` 中出现本轮索引产物，并记录实际结果与阻塞项

## 6. 风险与边界

- 本次不改候选 Prompt 内容本身，也不重新跑 prompt-tune。
- 本次不引入多环境 Prompt 选择器，只支持“激活一个候选为当前活动 Prompt”。
- 如果本地 OpenAI 兼容接口不可用，索引构建可能失败；这属于运行环境问题，不属于本次激活脚本逻辑错误。
