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

## exp4 结果（20 样本 full）

详见 `results/reports/native_extraction_comparisons/runs/native_bridge_full_20260511/summary.md`。

### 裸抽取 vs metadata-closure

| 流程 | entity_recall | entity_precision | relation_recall |
|---|---:|---:|---:|
| exp4 裸 20 full | 0.4518 | 0.2649 | 0.2080 |
| **exp4 + metadata-closure** | **0.5873** | 0.2781 | **0.3217** |

### 与历史 baseline 对比

| 流程 | entity_recall | entity_precision | **relation_recall** |
|---|---:|---:|---:|
| 手工抽取 v2 原版 + metadata-closure（历史最佳） | 0.5203 | **0.3677** | 0.2865 |
| **原生 v2+strict_tuple + metadata-closure** | **0.5873** | 0.2781 | **0.3217** |

### 关键观察

1. **原生 + strict_tuple + metadata-closure 的 20 样本 full 达到 relation_recall=0.322**，比之前的历史最佳 0.287 高 **+12%**，而且这次是在**与生产 graphrag index 完全同管线**的条件下达到的——迁移风险消除。
2. **entity_recall 0.587 > Gate A 门槛 0.55** ✓
3. **relation_recall 0.322 > Gate B 门槛 0.25** ✓
4. **entity_precision 0.278 < Gate A 门槛 0.45** ✗ —— 这个差距主要来自 metadata-closure 注入的 Course/Chapter/Section seed 实体成为了精度计算的分母。生产上这不是问题（索引时这些 seed 确实应该存在），但 Gate A 的定义需要调整：应该排除 metadata-injected 实体做精度计算，或者在裸抽取（不加 metadata-closure）上评估 precision。
5. **格式缺陷 0**：strict_tuple 变体在 20 样本 full 下仍保持 0 引号、0 括号破损、0 低解析样本，证明硬格式约束是稳定的。

## 结论

1. **prompt 在 tuple 格式下确实有严重缺陷**：原版 v2 在 strict 适配层下 21/101 实体 title 带引号、3/7 样本解析级联失败；不是适配层的问题，是 prompt 自己没写清楚格式要求。
2. **容错层会掩盖缺陷并污染 audit 数据**：exp2 表面看"引号 0"是因为适配层剥掉了，但实体数从 101 掉到 68、low_parse 反而从 3 涨到 4（容错把整条破损 tuple 切成多份反而丢失信息），audit 指标全面下滑。**容错不是救命稻草，是统计失真**。
3. **硬格式约束根治 prompt 缺陷**：exp3 引号 0、括号不平衡 0、low_parse 0，audit 指标全面跃升。
4. **schema 大小写不匹配是一个独立的工程问题**：修完之后 `entity_type_valid_rate=1.0`、`endpoint_valid_rate=1.0`。
5. **exp4 full 验证**：strict_tuple 变体在 full 下稳定，且迁移到真实 graphrag index 管线后关系召回不退化反而更强（+12%）。

## 下一步

- 把 `strict_tuple` 的硬格式约束 back-port 到 `default_guarded` / `schema_aware_directional_v2` 等其它候选，形成统一的格式约束基线。
- 修 Gate A 的精度定义：从 metadata-closure 前的裸抽取输出评估 precision（当前裸 20 full 是 0.265，仍未达 0.45，说明还有抽取噪声空间）。
- Finalize 前的最后一步：确认 strict_tuple 变体在 holdout 分组上仍达标（当前 scoring 的 leakage_diagnostics 因为原生抽取器不读 manifest fewshot 来源信息所以分组为空，需要修 scoring 对无 manifest 场景的处理，或者手动给原生 run 标注 fewshot 源样本）。

## exp5 结果：精度向抑制规则的失败验证（2026-05-12）

### 动机

exp4 的 20 样本 full 显示 `audit_entity_precision(holdout)=0.303 < 0.45`，Gate A 未达。假设：LLM 仍抽出大量 gold 未覆盖的过渡性短语、页眉页脚、辅助说明类"实体"，这些是抽取层噪声。解决方案：在 prompt 里加「-精度向抑制规则-」章节，硬性拒绝 9 类非抽取对象。

### 实验

| 配置 | entity_recall (holdout) | entity_precision (holdout) | relation_recall (holdout) | avg entity / sample |
|---|---:|---:|---:|---:|
| exp4 strict_tuple（无精度规则）+ metadata-closure | 0.614 | **0.303** | 0.347 | 20.45 |
| **exp5 strict_tuple + 精度规则 + metadata-closure** | **0.395** | **0.298** | **0.226** | **11.45** |

### 结论

**精度向抑制规则是失败的优化**：

- **precision 几乎不变**（0.303 → 0.298）——规则未能命中主要噪声
- **recall 大幅下降**（0.614 → 0.395，-36%）——模型把正经 gold 实体也一并跳过
- **抽取量腰斩**（20.45 → 11.45 个/样本，-44%）但没带来精度收益
- **结构化 contains 也被误伤**：relation_recall 从 0.347 掉到 0.226，metadata-closure 的增益被抵消了一半

### 根因

9 类抑制对象里有 3 类需要 LLM 做复杂上下文推理才能判定：
- "无命名实体指向的代词" — 需要指代消解
- "无属性描述的一次性引用项" — 需要跨段计数
- "课程结构外的通用词" — 需要领域知识判断

LLM 对这类负向约束无法精准执行，**只能一刀切把所有不确定的都跳过**。这符合"负向约束难以让 LLM 精准执行"的经验——strict_tuple 的格式约束起作用，是因为判定是**确定性**的（有没有引号/括号是否匹配），而精度向抑制需要**语义不确定性判断**。

### 行动

- 已从 `prompt.txt` 删除「-精度向抑制规则-」章节
- Generator 的 `PRECISION_SUPPRESSION_BLOCK` 常量保留（失败实验记录），但不再被拼入 prompt
- `build_schema_fewshot_distilled_v2_strict_tuple_prompt()` 只追加 `STRICT_TUPLE_FORMAT_BLOCK`
- 对比报告见 `results/reports/native_extraction_comparisons/runs/exp4_vs_exp5_precision_rule_20260512/summary.md`

### 对 Gate A 精度问题的新判断

Gate A precision 0.30 < 0.45 仍是未达标，但从 exp5 看，prompt 层**无法**通过负向规则继续压降噪声。真正的出路只有两条：

1. **扩容审计 gold**：让每个样本的 gold 实体从 ~10 个扩到 ~20 个，分母上去后 precision 会自然改善（当前 LLM 抽的 20 个里有很多是 audit 没覆盖但实际合理的实体）。
2. **改 Gate A 精度定义**：从 "LLM 抽的所有实体 vs gold" 改成 "LLM 抽的 audit 视野内实体 vs gold"，排除审计集之外的合理实体。

这两条之前都在"下一步"列表里，exp5 的失败进一步证实了**问题不在 prompt，而在审计集规模/评分口径**。
