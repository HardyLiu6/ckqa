# 原生抽取器桥接验证（2026-05-11）

## 目标

验证"**手工 JSON 抽取器**上调优过的 prompt 迁移到**生产 graphrag index（原生 tuple 抽取器）**时，是否会因为格式差异导致真实表现严重退化。如果退化明显，必须在原生抽取器下重新校准 prompt。

## 背景

- 原本的 prompt 调优流水线用的是 `scripts/run_candidate_extraction.py`（手工 JSON 抽取器 + 自定义容错解析器）。
- 生产环境用 `graphrag index`，内部调用 `graphrag.index.operations.extract_graph.graph_extractor.GraphExtractor`（tuple 格式 + gleaning + `.upper()` 规范化）。
- 之前我们达到了 `audit_relation_recall=0.35`（metadata-closure 加持），Gate B 已达成；但这些数字都是手工抽取器下的。

## 实验设计

跑 3 个 smoke 实验（每个 7 样本，单候选）+ 1 个 full 实验。统一使用：

- 模型：`deepseek-v4-flash`
- Samples：`data/eval/material_7_audit_extraction_set.json`
- Entity types：`Course,Chapter,Section,KnowledgePoint,Concept,Term,FormulaOrDefinition,AlgorithmOrMethod,Experiment,Assignment,ToolOrPlatform`
- `max_gleanings=1`

| 实验 | Prompt | 适配层 strict | 样本 | 目的 |
|---|---|---|---:|---|
| exp1 | `schema_fewshot_distilled_v2`（原版） | 开启（不剥引号） | 7 | 暴露 prompt 在 tuple 格式下的真实缺陷 |
| exp2 | 同上 | 关闭（剥引号） | 7 | 观察容错层能掩盖多少缺陷 |
| exp3 | `schema_fewshot_distilled_v2_strict_tuple`（新变体） | 开启 | 7 | 验证硬格式约束能否根治 prompt 缺陷 |
| exp4 | 同 exp3 | 开启 | 20 | full 校准，给 finalize 做数据支撑 |

`schema_fewshot_distilled_v2_strict_tuple` 是在原版基础上新增一段 7 条硬约束，只聚焦 **模型自己能控制、且适配层无法救回** 的三类 prompt 缺陷：引号包裹、括号不平衡、description 里出现 `##` / `<|>` / 孤立 `)`。

## 配套工程修复

跑实验前先修了两处与 schema / 原生抽取器的不匹配：

1. **scoring 层大小写不敏感**（`scoring_metrics.py` / `scoring_audit.py`）：原生 `_process_result` 强制对 entity_type 做 `.upper()`，让 PascalCase schema 全部查不到。改成 casefold 比对，schema 文件保持原样。
2. **适配层 type 反向映射**（`run_native_extraction.py::_canonicalize_entity_type`）：`COURSE → Course` 这种确定性映射始终开启（不算掩盖缺陷）；引号剥离才受 `--strict` 控制。

详见 commit `1639516 feat(schema-native): 适配 GraphRAG 原生抽取器的 type 大小写规范化`。

## 运行命令

```bash
cd graphrag_pipeline

# 实验 1：原版 prompt + strict 适配层（暴露缺陷）
python scripts/run_native_extraction.py \
    --samples-file data/eval/material_7_audit_extraction_set.json \
    --prompt prompts/candidates/schema_fewshot_distilled_v2/prompt.txt \
    --entity-types "Course,Chapter,Section,KnowledgePoint,Concept,Term,FormulaOrDefinition,AlgorithmOrMethod,Experiment,Assignment,ToolOrPlatform" \
    --candidate-name native_v2_strict \
    --max-gleanings 1 --limit 7 \
    --run-id native_exp1_v2_strict_20260511 --overwrite --strict

# 实验 2：原版 prompt + tolerant 适配层
python scripts/run_native_extraction.py \
    --samples-file data/eval/material_7_audit_extraction_set.json \
    --prompt prompts/candidates/schema_fewshot_distilled_v2/prompt.txt \
    --entity-types "Course,Chapter,Section,KnowledgePoint,Concept,Term,FormulaOrDefinition,AlgorithmOrMethod,Experiment,Assignment,ToolOrPlatform" \
    --candidate-name native_v2_tolerant \
    --max-gleanings 1 --limit 7 \
    --run-id native_exp2_v2_tolerant_20260511 --overwrite

# 实验 3：strict_tuple 变体 + strict 适配层
python scripts/run_native_extraction.py \
    --samples-file data/eval/material_7_audit_extraction_set.json \
    --prompt prompts/candidates/schema_fewshot_distilled_v2_strict_tuple/prompt.txt \
    --entity-types "Course,Chapter,Section,KnowledgePoint,Concept,Term,FormulaOrDefinition,AlgorithmOrMethod,Experiment,Assignment,ToolOrPlatform" \
    --candidate-name native_v2_strict_tuple \
    --max-gleanings 1 --limit 7 \
    --run-id native_exp3_strict_tuple_20260511 --overwrite --strict

# 实验 4：strict_tuple 变体 + 20 样本 full
python scripts/run_native_extraction.py \
    --samples-file data/eval/material_7_audit_extraction_set.json \
    --prompt prompts/candidates/schema_fewshot_distilled_v2_strict_tuple/prompt.txt \
    --entity-types "Course,Chapter,Section,KnowledgePoint,Concept,Term,FormulaOrDefinition,AlgorithmOrMethod,Experiment,Assignment,ToolOrPlatform" \
    --candidate-name native_v2_strict_tuple \
    --max-gleanings 1 \
    --run-id native_exp4_strict_tuple_full20_20260511 --overwrite --strict

# 打分（drop-invalid 模式，不覆盖 latest 指针）
for run_id in native_exp1_v2_strict_rescored native_exp2_v2_tolerant_rescored native_exp3_strict_tuple_scored; do
    eval_run=$(echo $run_id | sed 's/_rescored//; s/_scored//' | sed 's/native_exp/native_exp/')
    python scripts/score_extraction_results.py \
        --eval-dir results/extraction_eval/runs/${eval_run}_20260511 \
        --audit data/eval/material_7_audit_extraction_set.json \
        --run-id ${run_id} --top-k 1 --overwrite \
        --relation-validation-mode drop-invalid
done

# 对比报告
python scripts/compare_native_extraction_runs.py \
    --run-id native_bridge_7samples_20260511 \
    --entry "exp1 v2 原版 + strict|results/extraction_eval/runs/native_exp1_v2_strict_20260511|native_exp1_v2_strict_rescored" \
    --entry "exp2 v2 原版 + tolerant|results/extraction_eval/runs/native_exp2_v2_tolerant_20260511|native_exp2_v2_tolerant_rescored" \
    --entry "exp3 v2+strict_tuple + strict|results/extraction_eval/runs/native_exp3_strict_tuple_20260511|native_exp3_strict_tuple_scored" \
    --overwrite
```

## 7 样本 smoke 对比（exp1/2/3）

详见 `results/reports/native_extraction_comparisons/runs/native_bridge_7samples_20260511/summary.md`。

### Audit 指标

| 实验 | entity_recall | entity_precision | relation_recall |
|---|---:|---:|---:|
| exp1 v2 原版 + strict | 0.3795 | 0.2412 | 0.1535 |
| exp2 v2 原版 + tolerant | 0.2571 | 0.1038 | 0.0686 |
| **exp3 v2+strict_tuple + strict** | **0.6167** | **0.3619** | **0.3507** |

### 格式缺陷统计

| 实验 | 实体 | 关系 | low_parse(≤2) | title 带引号 | type 带引号 |
|---|---:|---:|---:|---:|---:|
| exp1 v2 原版 + strict | 101 | 135 | 3 | 21 | 0 |
| exp2 v2 原版 + tolerant | 68 | 102 | 4 | 0 | 0 |
| **exp3 v2+strict_tuple + strict** | **117** | **186** | **0** | **0** | **0** |

## 结论

1. **prompt 在 tuple 格式下确实有严重缺陷**：原版 v2 在 strict 适配层下 21/101 实体 title 带引号、3/7 样本解析级联失败；不是适配层的问题，是 prompt 自己没写清楚格式要求。
2. **容错层会掩盖缺陷并污染 audit 数据**：exp2 表面看"引号 0"是因为适配层剥掉了，但实体数从 101 掉到 68、low_parse 反而从 3 涨到 4（容错把整条破损 tuple 切成多份反而丢失信息），audit 指标全面下滑。**容错不是救命稻草，是统计失真**。
3. **硬格式约束根治 prompt 缺陷**：exp3 引号 0、括号不平衡 0、low_parse 0，实体数从 101 → 117（多出的是合法抽取不是污染），audit 指标全面跃升——`relation_recall` 从 0.15 → 0.35，**+128%**，一下子超过了手工抽取器 + metadata-closure 的最佳水平（0.35）。
4. **schema 大小写不匹配是一个独立的工程问题**：修完之后 `entity_type_valid_rate=1.0`、`endpoint_valid_rate=1.0`，确认这不是 prompt 要解决的事。

## 下一步

- exp4（20 样本 full）跑完后，验证 strict_tuple 变体在 full 下是否同样稳定。
- 若稳定，把 `strict_tuple` 的硬格式约束 back-port 到 `default_guarded` / `schema_aware_directional_v2` 等其它候选，形成统一的格式约束基线。
- 在 exp4 之上叠加 metadata-closure，看真实生产天花板能到哪里。
- Finalize 前确认 audit_entity_precision（holdout）≥ 0.45，目前 smoke 是 0.36，需要看 full 是否改观。
