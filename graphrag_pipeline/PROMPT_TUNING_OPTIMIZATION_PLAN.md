# Prompt 调优流水线优化计划

## 背景

本计划基于 `material_7_real_20260508` 真实提示词调优、候选抽取评分、`schema_aware` 实验性固化，以及后续索引运行中暴露的问题整理。

当前流水线已经能完成：

1. 从课程材料生成 prompt tuning samples。
2. 抽取小规模 audit set。
3. 运行 GraphRAG 官方 `prompt-tune` 生成 `auto_tuned`。
4. 生成 `default`、`auto_tuned`、`schema_aware`、`schema_fewshot` 四类候选。
5. 对候选执行 real LLM 抽取、规则化评分和实验性固化。
6. 使用固化 Prompt 进入 GraphRAG index。

但本轮审查也确认：当前结果只能说明流水线真实可跑、`schema_aware` 是相对最优实验基线；还不能说明它已经达到正式自动验收标准。

## 关键结论

1. 流水线顺序总体合理：`fetch_input -> build_samples -> build_audit -> prompt_tune_real_run -> generate_candidates -> extract/score smoke -> extract/score full`。
2. `prompt-tune` 放在候选生成前是正确的，因为 `schema_aware` 和 `schema_fewshot` 会优先以 `auto_tuned` 为底稿增强。
3. `material_7_samples.json` 的 80 条样本覆盖 4 类样本形态，适合作为本轮候选生成和抽取评测输入。
4. `audit set` 20 条采用平衡抽样，适合作为小规模校准集，但不是完整 gold benchmark。
5. 四个候选构成合理，覆盖基线、官方自动调优、schema 约束、schema + few-shot 四个层次。
6. `schema_aware` 在规则化评分中排名第一，但严格门禁未通过，主要卡在 `endpoint_valid_rate`。
7. `schema_aware` 的固化应被视为实验性激活；当前 `active_prompt.json` 未绑定 scoring report，审计闭环还不够硬。

## 现有问题

### P0：候选固化证据链不完整

现象：

- `schema_aware` 已被固化为 active prompt。
- 后续 rescore 支持它是当前最佳候选。
- 但 active 记录中的 `scoring_binding` 为 `null`，不能证明固化动作绑定了指定评分报告、manifest hash 和 scoring hash。

影响：

- 后续复盘时无法严格回答“这次索引用的是哪一份评分报告支持的 Prompt”。
- 未过门候选容易被误读为正式达标。

改进：

1. 固化正式候选时必须绑定 scoring run。
2. 未过 `gate_passed` 的候选只能标注为 `experimental`。
3. `active_prompt.json` 增加清晰字段：`activation_policy`、`gate_passed`、`scoring_run_id`、`manifest_hash`、`scoring_hash`。

验收：

- 无 scoring binding 时，正式固化命令失败。
- 使用 `--allow-failed-scoring-gate` 时，报告明确写出 `experimental=true` 和失败原因。

### P0：评分门禁失败原因需要转化为可修复规则

现象：

- 四个候选都 `gate_passed=false`。
- `schema_aware` 的 `endpoint_valid_rate=0.8142`，低于 0.95 门槛。
- 典型问题包括 `defined_by` 端点错配、`belongs_to` 把概念归到概念、弱关系缺 target、`defined_by -> Term` 缺少符号或变量语义线索。

影响：

- 当前 Prompt 约束能保证类型命中，但还不能稳定保证关系端点合法。
- 关系抽取容易把“语义相关”错写成强关系。

改进：

1. 在 Prompt 中为 `defined_by`、`belongs_to`、`appears_in`、`related_to` 增加正例和反例。
2. 在 schema 配置中为高风险关系补充 endpoint repair hints。
3. 在评分报告中聚合 top endpoint errors，生成“下一轮 Prompt 修复建议”。
4. 对 `related_to` 增加“必须有明确 target，不能用作缺失关系占位”的硬约束。

验收：

- 下一轮 full scoring 中 `endpoint_valid_rate` 至少提升到 0.90。
- 正式验收目标仍为 0.95。
- `defined_by` 和 `belongs_to` 的端点错配数量下降。

### P1：`auto_tuned` 可生成但课程域贴合不足

现象：

- GraphRAG 官方 `prompt-tune` 已真实成功运行。
- 本轮使用了 `--no-discover-entity-types`，解决了 One API 对 `response_format` 的兼容问题。
- 但 `domain`、`language`、`chunk_size` 未显式传入，自动生成内容存在偏题和示例不贴合风险。

影响：

- `auto_tuned` 适合作为语料自适应底稿，不适合直接作为最终课程抽取 Prompt。
- 如果底稿偏题，`schema_aware` 需要用更强约束抵消底稿噪声。

改进：

1. 真实调优时显式传入课程领域说明，例如“计算机操作系统课程教材知识图谱抽取”。
2. 显式指定中文语境。
3. 增加 auto-tuned prompt 自检：示例输出实体必须能从示例输入文本中找到证据。
4. 对 community report prompt 增加课程报告语境校验，避免漂到通用社会网络或开源社区叙事。

验收：

- auto-tuned 示例通过输入输出一致性检查。
- community report prompt 不再出现与课程材料无关的领域叙事。

### P1：few-shot gold 覆盖不足

现象：

- 当前 few-shot 使用 3 条 audit gold。
- 已有 gold seed 只覆盖部分关系类型，缺少 `belongs_to`、`prerequisite_of`、`related_to`、`implemented_by`、`appears_in`。
- 实体类型也未充分覆盖 `Course`、`Experiment`。

影响：

- `schema_fewshot` 的格式指导有价值，但覆盖窄，未能在评分中稳定超过 `schema_aware`。
- audit recall 仍偏低，关系召回尤其弱。

改进：

1. 补齐 10 到 20 条高质量 gold seed。
2. 优先覆盖缺失关系类型和高风险端点组合。
3. 为 `Course`、`Experiment`、`ToolOrPlatform` 补独立案例。
4. few-shot 选择从“固定 3 条”升级为按关系覆盖贪心选择。

验收：

- few-shot 示例覆盖至少 8 类关系。
- `schema_fewshot` 的 `audit_relation_recall` 明显高于当前版本。
- 若 `schema_fewshot` 仍未超过 `schema_aware`，需要输出原因分析，而不是盲目增加示例。

### P1：运行日志和 run 证据容易混淆

现象：

- `material_7_prompt_tune_run.log` 是追加式混合日志。
- 同一文件同时包含历史失败 Traceback 和后续成功运行。

影响：

- 人工判断本轮是否成功时容易误读。
- 失败 Traceback 可能包含请求上下文，不适合提交或传播。

改进：

1. 每个 run-id 使用独立日志路径。
2. 默认运行前截断本 run 的日志。
3. 对错误报告做摘要化，不在报告中保留敏感请求上下文。
4. 将成功报告作为判断依据，日志只作为排障材料。

验收：

- 每次 prompt-tune report 指向唯一 run log。
- 成功报告中不会混入旧 Traceback。

### P2：解释器和运行环境需要显式化

现象：

- 编排脚本默认使用 `sys.executable`。
- 成功运行实际依赖 `graphrag-oneapi` 环境。

影响：

- 从不同 shell 启动可能落到 base Python。
- dry-run 和真实运行的 Python 可能不一致。

改进：

1. 在 full real pipeline 命令中强制显式 `--python /home/sunlight/miniconda3/envs/graphrag-oneapi/bin/python`。
2. 在 dry-run 报告中输出 Python 解释器路径。
3. 如果发现解释器不是 `graphrag-oneapi`，给出警告。

验收：

- dry-run 和真实 report 中的 Python 路径一致。
- 错环境运行时能提前失败或明确告警。

### P2：gold 保留键不够稳

现象：

- 现有 gold 保留主要依赖 `source_sample_id`。
- `source_sample_id` 含顺序前缀，样本顺序变化时可能导致同一来源文档的旧标注无法保留。

影响：

- 重采样或数据顺序变化后，已有人工标注可能丢失。

改进：

1. gold 保留键升级为 `source_doc_id + page_start + page_end + text_hash`。
2. 为旧 `source_sample_id` 保留兼容匹配。
3. audit build 报告输出 gold 保留命中方式。

验收：

- 重排 samples 后，已有 gold 仍能被保留。
- 报告中能区分 exact id 命中和 stable source hash 命中。

## 分阶段实施计划

### Phase 1：审计闭环硬化

目标：先解决“能复现、能解释、能追责”。

任务：

1. 强制正式固化绑定 scoring report。
2. 为实验性固化增加显式状态字段。
3. run report 中记录 active prompt、scoring run、index run 的绑定关系。
4. 为 prompt-tune 日志改为 per-run 独立文件。

推荐验证：

```bash
cd /home/sunlight/Projects/ckqa/graphrag_pipeline
conda run -n graphrag-oneapi python -m pytest \
  tests/test_finalize_candidate_prompt.py \
  tests/test_run_graphrag_prompt_tune.py \
  tests/test_run_material_prompt_pipeline.py
```

### Phase 2：关系端点修复

目标：把 `endpoint_valid_rate` 从 0.8142 提升到 0.90 以上。

任务：

1. 汇总 `top_candidates.json` 中 endpoint invalid 明细。
2. 为高频错误关系补 Prompt 负例。
3. 修订 relation schema 的 source/target/hint。
4. 增加 endpoint error 聚合报告。

推荐验证：

```bash
cd /home/sunlight/Projects/ckqa/graphrag_pipeline
conda run -n graphrag-oneapi python scripts/score_extraction_results.py \
  --eval-dir ./results/extraction_eval/runs/material_7_real_20260508 \
  --audit data/eval/material_7_audit_extraction_set.json \
  --run-id material_7_endpoint_repair_check \
  --overwrite
```

### Phase 3：gold 和 few-shot 扩容

目标：让 few-shot 的收益可测，而不是只增加 Prompt 长度。

任务：

1. 增补 10 到 20 条 gold seed。
2. 覆盖缺失实体类型和关系类型。
3. few-shot 选择策略改为按关系覆盖最大化。
4. 增加 few-shot 覆盖报告。

推荐验证：

```bash
cd /home/sunlight/Projects/ckqa/graphrag_pipeline
conda run -n graphrag-oneapi python scripts/generate_candidate_prompts.py \
  --samples data/prompt_tuning_samples/material_7_samples.json \
  --audit data/eval/material_7_audit_extraction_set.json
```

### Phase 4：auto-tuned 质量门

目标：让官方 prompt-tune 产物成为可靠底稿。

任务：

1. 显式传入 domain/language。
2. 增加 auto-tuned 输入输出一致性检查。
3. 对 community report prompt 做课程域检查。
4. 失败时允许回退到 default + schema 增强。

推荐验证：

```bash
cd /home/sunlight/Projects/ckqa/graphrag_pipeline
conda run -n graphrag-oneapi python scripts/run_graphrag_prompt_tune.py \
  --root . \
  --output prompts/candidates/auto_tuned \
  --no_entity_types \
  --domain "计算机操作系统课程教材知识图谱抽取" \
  --language "中文"
```

### Phase 5：索引与 QA smoke 闭环

目标：候选 Prompt 不只在抽取评测中相对更好，还要能支撑真实问答。

任务：

1. 索引命令统一走 `scripts/run_graphrag_index.py`，默认保留 cache。
2. `settings.yaml` 保留保守并发：`concurrent_requests: 10`、`async_mode: threaded`。
3. 索引完成后运行 material 级 QA smoke。
4. QA smoke 报告中记录 active prompt 和 index cache 状态。

推荐验证：

```bash
cd /home/sunlight/Projects/ckqa/graphrag_pipeline
conda run -n graphrag-oneapi python scripts/run_graphrag_index.py --root .
conda run -n graphrag-oneapi python scripts/run_material_qa_smoke.py \
  --root . \
  --qa-file data/eval/material_7_qa_smoke.json \
  --output-file results/qa_eval/material_7_schema_aware_smoke.json \
  --method local
```

## 优化讲义提纲

1. 为什么课程 GraphRAG 需要 schema-aware prompt。
2. 从课程材料到 samples、audit set、candidate prompts 的数据链路。
3. `default`、`auto_tuned`、`schema_aware`、`schema_fewshot` 的设计差异。
4. 为什么 `auto_tuned` 能生成，但不能直接等于课程 schema 达标。
5. 规则化评分体系：硬指标、软指标、综合分和 gate。
6. 为什么本轮 `schema_aware` 排第一但未过门。
7. 端点合法性失败如何转化为 Prompt 负例和 schema hint。
8. 为什么 few-shot 需要覆盖设计，而不是简单堆示例。
9. Prompt 固化、评分报告和索引运行之间的审计绑定。
10. 从抽取指标走向 QA smoke 和课程问答效果评估。

## 下一次执行建议

下一轮不要直接扩大索引规模，先完成：

1. 绑定固化证据链。
2. 修复 endpoint 高风险关系。
3. 扩充 gold seed。
4. 重跑 full extraction + score。
5. 仅当 `endpoint_valid_rate` 接近或超过 0.95 后，再把候选升级为正式 active prompt。

当前 `schema_aware` 可继续作为实验索引基线使用，但对外结论应表述为“当前相对最优、仍未通过严格门禁”。

## 2026-05-09 执行记录：无索引小闭环

本轮按“先修复抽取与评分闭环，不新建索引”的边界执行，未运行 `graphrag index`、`scripts/run_graphrag_index.py` 或 `--index-after-finalize`。

已完成：

1. 刷新 `material_7` audit/candidate/score 产物。
2. 将 audit gold seed 从 5 条扩到 12 条，覆盖 10 类关系：`contains`、`belongs_to`、`depends_on`、`prerequisite_of`、`related_to`、`defined_by`、`applied_in`、`implemented_by`、`evaluated_by`、`appears_in`。
3. 保留 `source_doc_id + page_start + page_end + text_hash` 稳定 gold key，当前 20 条 audit 样本均带 `gold_stable_key`。
4. 强化 `related_to`、`implemented_by` 的端点规则，明确禁止缺失端点占位和错误目标类型。
5. 重新生成 `schema_fewshot`，few-shot 示例从 3 条扩到 8 条，实际覆盖 10/10 类关系；实体类型覆盖 10/11，缺口为当前材料中没有可靠 `Experiment` 样本。
6. 重新评分 `material_7_real_20260508` 既有抽取结果，生成 `material_7_endpoint_repair_check` 报告，并让 scoring 层识别 `manual_gold_seed_*` 多版本 gold seed。

当前结果：

- `schema_aware` 仍排第一，但 `endpoint_valid_rate=0.8142`，`gate_passed=false`。
- `schema_fewshot` 覆盖更完整，但本次评分复用了旧 LLM 抽取结果，不能证明新 Prompt 已提升真实抽取质量。
- `active_prompt.json` 仍保留旧的实验性 `schema_aware` 固化记录；本轮未重新固化 active prompt，避免在未重新抽取/评分通过前改变运行时基线。

下一步：

1. 仅重跑 full extraction + score，不建索引。
2. 观察新 `schema_aware/schema_fewshot` 对 `endpoint_valid_rate` 和 relation recall 的真实影响。
3. 若 `endpoint_valid_rate` 未接近 0.90，继续依据 `endpoint_error_summary.md` 补负例和 schema hint。
4. 只有当评分接近或超过门禁目标后，才考虑实验性或正式固化；若需要新建索引，必须先确认。
