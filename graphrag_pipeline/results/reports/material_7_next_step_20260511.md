# material_7 提示词调优下一步优化方向（2026-05-11）

本报告基于以下新增产物：

- `results/reports/extraction_postprocess/runs/material_7_v3_full_20260510_structured_closure/summary.md`
  ——20 样本 full 上首次 strict-closure 后处理。
- `results/reports/extraction_scoring/runs/material_7_v3_full_20260510_structured_closure_score/extraction_compare.md`
  ——full closure 打分。
- `results/reports/extraction_missing_relations/runs/material_7_v3_full_20260510_raw_missing/summary.md`
  ——raw 20 样本 full 的 gold 侧缺失归因。
- `results/reports/extraction_missing_relations/runs/material_7_v3_full_20260510_closure_missing/summary.md`
  ——closure 20 样本 full 的 gold 侧缺失归因。

## 1. 验证 closure 在 full 上的真实影响

既然 smoke(7) 上 `audit_relation_recall` 几乎为 0，需要看 full(20) 是否还是这样。

关键指标对比（20 样本 full，7 候选平均由各候选行观察）：

| 维度 | raw baseline | closure | 结论 |
|---|---|---|---|
| 端点合法率 | 0.78–0.88 | 1.00 | closure 把 gate 完全拉直 |
| `audit_entity_precision` | 0.35–0.39 | 0.35–0.39 | closure 的 `add_missing_target_entity` 没污染精度 |
| `audit_relation_recall` 最好值 | 0.179（default） | 0.185（schema_fewshot_distilled_v2, holdout 0.224） | 有正向但很小 |
| `audit_relation_recall` 所有候选平均 | 0.122 | 0.156 | 小幅整体回升 |
| gold 侧 `hit` 总数 | 83 | 103 | +20 |
| gold 侧 `wrong_type` | 46 | 21 | -25（被 closure 的 `convert_belongs_to_taxonomy_to_contains` 吸收） |
| gold 侧 `source/target_endpoint_missing` 合计 | 203 | 205 | 基本不变 |
| gold 侧 `both_endpoints_missing` | 217 | 215 | 基本不变 |

解读：closure 在 full 上的真实贡献，几乎**全部来自 `belongs_to → contains` 的方向翻转**（把 wrong_type 归入 hit）。`add_missing_target_entity`（只有 12 条）对关系召回贡献可以忽略；它只是把 drop-invalid 原本要丢掉的关系保留下来，用“伪实体”占位，对 audit 指标无效。

前一轮结论可以更新为：

- ✅ prompt-only 已经不是最优方向。full 上所有 `schema_*` 家族 `audit_relation_recall` 仍在 0.08–0.18 之间，和 default/auto_tuned 同量级。
- ✅ strict-closure 能解决 endpoint gate，且不会污染实体精度。
- ⚠️ strict-closure 对关系召回的正面收益仅来自方向翻转族（21 条 `belongs_to`），其余修复行为对 audit 无影响。继续扩展结构化修复规则只有在扩规则能命中新的“方向错/类型错”族时才值得。

## 2. gold 侧缺失归因（567 条 gold × 7 候选）

跨候选汇总（closure 口径，raw 相差 < 1%）：

| 类别 | count | 占比 |
|---|---:|---:|
| hit | 103 | 18.2% |
| direction_reversed | 1 | 0.2% |
| wrong_type | 21 | 3.7% |
| both_endpoints_present_but_not_connected | 22 | 3.9% |
| source_endpoint_missing | 99 | 17.5% |
| target_endpoint_missing | 106 | 18.7% |
| both_endpoints_missing | 215 | 37.9% |

**结论（这是本次分析最关键的单张表）**：
当前 gold 关系中 **74.1% 落在端点缺失族**（single miss + both miss），**22.1% 是命中/方向错/类型错三族的总和**，真正“端点都在但没连边”的只有 3.9%。

按关系类型观察瓶颈：

| relation_type | total | 主导缺失类型 | 注解 |
|---|---:|---|---|
| `contains` | 210 | both_miss 43 + src_miss 65 + tgt_miss 22 | 章节链路（Course/Chapter/Section/媒体容器）大量未抽到 |
| `depends_on` | 84 | both_miss 33 + src_miss 10 + tgt_miss 6 | 多媒体/存储器上下游概念缺失 |
| `evaluated_by` | 77 | both_miss 53 + tgt_miss 24 | 章节习题、题号级实体未被识别 |
| `appears_in` | 42 | tgt_miss 20 + both_miss 17 | 位置容器（Section/Chapter/Experiment）缺失 |
| `defined_by` | 42 | tgt_miss 27 + both_miss 15 | 公式/定义载体未被独立抽实体 |
| `prerequisite_of` | 14 | wrong_type 8 + both_miss 6 | 被模型当作 `related_to` 或 `depends_on` 输出 |
| `implemented_by` | 21 | both_miss 21 | 几乎完全抽不出对应算法实例 |
| `belongs_to` | 21 | wrong_type 10 + src_miss 9 | 被模型当作 `contains` 反向输出，closure 把 10 条翻正 |
| `applied_in` | 21 | both_miss 14 + src_miss 6 | 算法→应用场景链路缺 |
| `related_to` | 35 | both_miss 13 + hit 9 | 表现最接近 default，唯一有反向被抓到的关系 |

两个显著信号：

1. **Chapter/Section/Experiment/Assignment 等“容器型/组织型”实体抽取严重不足**，直接导致 `contains` / `appears_in` / `evaluated_by` 的端点缺失总量占全部 gold 的 ≥ 50%。
2. **公式/定义/算法实例类实体（FormulaOrDefinition、AlgorithmOrMethod）**也成体系地缺失，导致 `defined_by` / `implemented_by` / `applied_in` 几乎无命中。

这比“关系 prompt 怎么写”更上游。当前卡点是**实体召回**（特别是结构化/成块型实体），**不是关系类型识别**。

## 3. 下一步优化方向（按 ROI）

### P0. 切换投入重心到实体抽取层

不要再迭代 prompt 的关系规则或负例。改为做两件具体事：

1. **容器型实体抽取增强**  
   当前抽取对“第 N 章 X / N.M 小节 Y / 第 N 章习题 / 实验 K”这类容器型实体召回率极低。建议用两种低成本路径任选其一（优先 b，因为不依赖 LLM）：
   - (a) 在抽取前阶段给 LLM 一段“文档头部抽取”示例：把 `heading_level=1/2/3` 的前几行固定作为 Chapter/Section 实体强制注入。
   - (b) 在 pdf_ingest 的 normalized_docs → graphrag input 管线里，把已识别的 section 标题直接作为 `Chapter` / `Section` / `Assignment` 实体写入 chunk 的元数据，交由 extractor 做 seed 实体（zero-shot 注入）。这会直接把 `contains/appears_in/evaluated_by` 端点缺失降下来，不改 prompt。
2. **公式/定义实体的切粒度**  
   类似 `页号和页内地址计算公式` 这类实体当前从不出现在抽取结果里。建议在 chunking 或 prompt 里明确要求“遇到编号公式、命名定义、算法步骤清单时，把载体（如 `页号和页内地址计算公式`）作为独立 FormulaOrDefinition 实体”，并在 `extraction_rules.md` 里补一条硬约束。

期望效果：`audit_entity_recall` 从当前 0.40 推到 ≥ 0.55；传递到 `audit_relation_recall`（端点出现 ⇒ 关系才可能命中）带来 0.05–0.10 的结构性抬升。

### P1. 两阶段抽取（实体 pass + 关系 pass）

- 阶段 1：仅要求抽实体 + 类型，不要求关系。Prompt 更短、成本更低，可复用 default 骨架。
- 阶段 2：以阶段 1 实体为候选池，对每个文档 chunk 逐对判断有无指定类型的关系；大幅降低模型“同时判实体 + 类型 + 方向”的认知负担。

适合在 P0 已把实体召回提起来之后再做；否则阶段 2 的候选池依然稀疏。

### P2. 扩充 `extraction_rules.md` 的结构化修复规则库

closure 现在唯一真正生效的修复族是 `belongs_to → contains` 翻转。可低成本新增的族：

- `contains` 反向（`Concept -> Chapter/Section` → 翻成 `Chapter/Section -> Concept`）。
- `defined_by` 反向（`FormulaOrDefinition -> Concept` → 翻成 `Concept -> FormulaOrDefinition`）。
- `appears_in` 反向（`Chapter/Section -> Concept` → 翻成 `Concept -> Chapter/Section`）。

这些规则每加一条，gold hit 预期增加数条到十几条，边际成本低；是 prompt 不改、实体层也还没动之前的“填坑”步骤。

**[2026-05-11 更新] 已落地 closure v2（`swap_reversed_endpoints` + `retype_container_appears_in_to_contains`），A/B 结果**：

| 维度 | closure v1 | closure v2 |
|---|---|---|
| `drop_invalid_relationship` | 134 | 81（-53） |
| `endpoint_type_mismatch` | 111 | 51 |
| 新增规则命中 | `belongs_to→contains` 37 | 同 37 + `swap_reversed_endpoints` 22 + `retype_container_appears_in_to_contains` 38 |
| gold 侧 hit | 103 | 103 |
| gold 侧 wrong_type / direction_reversed / both_present_no_edge | 21 / 1 / 22 | 21 / 1 / 22 |
| 最佳 `audit_relation_recall`（全量） | 0.1847 | 0.1847 |
| 最佳 `composite_score` | 0.9085 | 0.9104 |
| `default` 的 `output_stability` | 0.4538 | 0.5310 |
| `default` 的 `endpoint_total_count` | 171 | 183 |

结论：**closure v2 新规则命中的 60 条关系全部不在 audit gold 里**，它们只负责“关系保留卫生”，对 audit 召回零增量。继续在 closure 上加翻转规则已经达到局部最优，**P2 方向可关闭**。v2 仍保留为 closure 默认（更干净的关系卫生、更少 drop），但**不要**再从它身上寻求 audit 提升。

复现命令：

- `python scripts/postprocess_extraction_results.py --eval-dir results/extraction_eval/runs/material_7_schema_v1_final_full_20260510 --output-run-id material_7_v3_full_20260510_structured_closure_v2 --mode strict-closure --overwrite`
- `python scripts/score_extraction_results.py --eval-dir results/extraction_eval/runs/material_7_v3_full_20260510_structured_closure_v2 --audit data/eval/material_7_audit_extraction_set.json --run-id material_7_v3_full_20260510_structured_closure_v2_score --top-k 7 --overwrite`
- `python scripts/diagnose_gold_missing_relations.py --eval-dir results/extraction_eval/runs/material_7_v3_full_20260510_structured_closure_v2 --run-id material_7_v3_full_20260510_closure_v2_missing --overwrite`

### P3. 扩容审计集 + 重平衡评分权重

- 当前审计 20 样本、10 关系类型，有 4 种类型 gold 数 ≤ 14，评价噪声大。建议扩到 40–60 条，保证每种关系类型 ≥ 20 gold，抽样按 `relation_type` 分层。
- 当前权重里 `audit_*` 合计只有 0.10，其他 0.90 全部给到“硬约束 + 稳定性”。在 endpoint 已由后处理兜底的现实下，应重平衡为：
  - `parse_success_rate` 0.15
  - `schema_hit_rate` 0.05
  - `entity_type_valid_rate` 0.10
  - `relation_type_valid_rate` 0.10
  - `endpoint_valid_rate` 0.05（closure 后几乎恒为 1.0）
  - `duplicate_complement` 0.025
  - `noise_complement` 0.025
  - `output_stability` 0.05
  - `audit_entity_recall` 0.10
  - `audit_entity_precision` 0.15
  - `audit_relation_recall` 0.20

这样候选排名不再被 `output_stability`（当前排第 1 的 `schema_aware_directional` 靠它领先）主导，而是被真实对齐召回主导。

### P4. 定 finalize gate 规则

在 P0 和 P1 达成以下任一前，禁止 `finalize_candidate_prompt.py` 或重建 `graphrag index`：

- **Gate A（结构型）**：对 20 样本 full，最佳候选的 `audit_entity_recall (holdout) ≥ 0.55` 且 `audit_entity_precision (holdout) ≥ 0.45`。
- **Gate B（关系型）**：对 20 样本 full，最佳候选的 `audit_relation_recall (holdout) ≥ 0.25`，且 gold 侧 `both_endpoints_missing` 占比 ≤ 25%。

P3 若先完成（扩容到 40–60 条），门槛仍按同样百分比执行。

## 4. 立即可执行的事项（2026-05-11 晚间更新）

本日已完成的事项：

1. ✅ gold-side 缺失诊断（`scripts/diagnose_gold_missing_relations.py`）——已落。
2. ✅ P2 方向翻转规则扩展（`swap_reversed_endpoints` + `retype_container_appears_in_to_contains`）——已落，结论：对 audit 召回零增量，P2 关闭。
3. ✅ P3 权重重平衡方案固化（`config/scoring/weights_audit_heavy_v1.json`）——评分 CLI 现支持带 metadata 的权重文件，已验证。
4. ✅ P0(b) 离线上界模拟（`scripts/simulate_container_seed_injection.py`）。

### P0(b) 上界模拟结果（关键）

| 方案 | hit | both_endpoints_missing | both_endpoints_present_but_not_connected |
|---|---:|---:|---:|
| raw | 83 | 217 | 17 |
| closure v2 | 103 | 215 | 22 |
| +seed 注入（P0(b) 最小版） | 103 | 100 | 197 |
| **+seed 注入 + metadata-driven contains（P0(b) + P2.5）** | **177** | **100** | **101** |

关键结论：

- 只做 seed 注入（P0(b) 最小版）不够。模型拿到锚点实体也不会自动补 `contains` / `appears_in` / `evaluated_by` 这些结构边，`both_miss` 的 115 条全部平移到 `both_present_no_edge`。
- seed 注入 + metadata-driven contains 派生一起上，hit 能从 103 推到 177（+74, +72%）。这是自实验启动以来单次最大的 audit 召回增量。
- 剩余天花板：`source_endpoint_missing` 100 + `target_endpoint_missing` 45 + `both_miss` 100 + `both_present_no_edge` 101 = 346 条（61%）；主要死在非 Course/Chapter/Section 的容器（Experiment/Assignment）与高细粒度实体（公式、Term、AlgorithmOrMethod）上，这部分 metadata 派生覆盖不到。
- `wrong_type` 从 21 回升到 43：metadata-contains 在一些 gold 位置派生了 contains，而 gold 那里本该是 depends_on/applied_in；这是上界模拟的派生副作用，真实落地时可用更严格的 gold 类型规则或 sample 内相关性过滤兜底。

### 下一步行动排序

1. **P0(b) 最小版真实落地**（下一循环的第一优先）：改 `pdf_ingest/scripts/pdf_processor/graphrag_exporter.py::_build_projection_text`，在 chunk 正文前面加一行容器锚点（格式：`[章节锚点] Course: xxx | Chapter: yyy | Section: zzz`），同时在 `_project_normalized_document` 的 `metadata` 里保留这些字段供下游引用。不改 prompt，不改 extractor。这会显著提升 Chapter/Section 的实体召回。
2. **P2.5 metadata-driven contains 派生规则进 closure**：把 `simulate_container_seed_injection.py::_inject_metadata_contains` 的派生逻辑改写进 `relationship_postprocessor.py`，从每个样本的 result `llm_debug.metadata` 或新加一个 document metadata 字段读 heading_path，按层级派生 `contains`。这一步不依赖模型行为。
3. 两步同时生效后重跑 `diagnose_gold_missing_relations.py`，验证 hit 能稳定在 ≥ 150（上界 177 的 85%）。
4. 若第 3 步通过，按 P3 扩容审计集到 40–60 条（分层抽样确保每种关系类型 ≥ 20 gold），同时把评分切到 `weights_audit_heavy_v1.json`。
5. Gate A/B 都达成后再进 P4（finalize + 重建 index）。

P1 两阶段抽取仍是备选，但先看 P0(b) + P2.5 的真实落地收益。

## 5. 产物清单

新落库的可复现诊断产物：

- `results/extraction_eval/runs/material_7_v3_full_20260510_structured_closure/` — closure v1 后 eval
- `results/extraction_eval/runs/material_7_v3_full_20260510_structured_closure_v2/` — closure v2（扩展翻转规则）后 eval
- `results/extraction_eval/runs/material_7_v3_full_20260510_closure_v2_seed_sim/` — P0(b) 最小版上界模拟
- `results/extraction_eval/runs/material_7_v3_full_20260510_closure_v2_seed_meta_sim/` — P0(b) + P2.5 联合上界模拟
- `results/reports/extraction_postprocess/runs/material_7_v3_full_20260510_structured_closure/summary.*`
- `results/reports/extraction_postprocess/runs/material_7_v3_full_20260510_structured_closure_v2/summary.*`
- `results/reports/extraction_scoring/runs/material_7_v3_full_20260510_structured_closure_score/`
- `results/reports/extraction_scoring/runs/material_7_v3_full_20260510_structured_closure_v2_score/`
- `results/reports/extraction_scoring/runs/material_7_v3_full_20260510_closure_v2_audit_heavy/` — audit_heavy 权重档位
- `results/reports/extraction_missing_relations/runs/material_7_v3_full_20260510_raw_missing/`
- `results/reports/extraction_missing_relations/runs/material_7_v3_full_20260510_closure_missing/`
- `results/reports/extraction_missing_relations/runs/material_7_v3_full_20260510_closure_v2_missing/`
- `results/reports/extraction_missing_relations/runs/material_7_v3_full_20260510_closure_v2_seed_sim_missing/`
- `results/reports/extraction_missing_relations/runs/material_7_v3_full_20260510_closure_v2_seed_meta_sim_missing/`

新工具：

- `scripts/extraction_eval/diagnose_gold_missing_relations.py` + `scripts/diagnose_gold_missing_relations.py`
- `scripts/extraction_eval/simulate_container_seed_injection.py` + `scripts/simulate_container_seed_injection.py`
- `scripts/extraction_eval/relationship_postprocessor.py` 新增 `swap_reversed_endpoints` / `retype_container_appears_in_to_contains` 两条 closure 规则
- `scripts/extraction_eval/score_extraction_results.py` 的 `_load_weights_from_path`：支持带 schema_version/profile/notes 元数据的权重 JSON
- `config/scoring/weights_audit_heavy_v1.json`：P3 建议权重档位
- `tests/test_diagnose_gold_missing_relations.py`、`tests/test_simulate_container_seed_injection.py`、更新后的 `tests/test_relationship_postprocessor.py` 和 `tests/test_score_extraction_results.py`
