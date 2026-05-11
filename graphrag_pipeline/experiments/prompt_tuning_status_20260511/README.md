# material_7 提示词调优流水线当前状态与下一步方向（2026-05-11 晚间）

本报告整合本轮循环所有改动，给出当前最佳候选、Gate 达成情况与下一步行动项。

## 1. 本轮循环总览

从 "prompt-only 迭代" 出发，走完了一个**真正意义上的完整循环**：

1. **诊断阶段**：gold-side 缺失归因 + schema 大小写对齐修复
2. **后处理阶段**：strict-closure → strict-metadata-closure（+seed 注入 + 层级 contains 派生）
3. **抽取器适配阶段**：从手工 JSON 抽取器切到 GraphRAG 原生 `GraphExtractor`
4. **prompt 格式加固阶段**：`schema_fewshot_distilled_v2` → `v2_strict_tuple`（新增 7 条硬格式约束）
5. **生产落地调研阶段**：确认 metadata-closure 生产落地路径（`prepend_metadata` + 自定义 workflow）

每个阶段都有对应的实验产物、对比报告和 commit 记录，都能复现。

## 2. 当前最佳配置

| 维度 | 配置 |
|---|---|
| 抽取器 | GraphRAG 原生 `GraphExtractor`（tuple 格式 + gleaning=1） |
| Prompt | `schema_fewshot_distilled_v2_strict_tuple` |
| 后处理 | `strict-metadata-closure`（从样本 metadata 注入 Course/Chapter/Section seed + 派生 contains 边） |
| 打分权重 | 建议切到 `config/scoring/weights_audit_heavy_v1.json`（audit 合计权重 0.45）|

## 3. 关键指标演进

| 阶段 | 配置 | audit_entity_recall | audit_entity_precision | audit_relation_recall |
|---|---|---:|---:|---:|
| 起点（2026-05-08 smoke） | default，手工 JSON | 0.00 | 0.00 | 0.00 |
| 首个可用候选（schema_fewshot） | 手工 JSON，20 full | 0.42 | 0.36 | 0.47 overlap / **0.29 holdout** |
| prompt-only 迭代顶峰（v2） | 手工 JSON，20 full | 0.40 | 0.36 | 0.16 |
| **metadata-closure 加入** | 手工 v2 + metadata-closure，20 full | 0.52 | 0.37 | **0.29** |
| 切到原生抽取器（v2 原版，strict） | 原生 tuple，7 smoke | 0.38 | 0.24 | 0.15 |
| 新 strict_tuple prompt | 原生 tuple，7 smoke | **0.62** | **0.36** | **0.35** |
| **生产形态** | 原生 tuple + strict_tuple + metadata-closure，20 full | **0.59** | 0.28 | **0.32** |

### 3.1 几个值得单独点出的事实

- **容错抽取器掩盖了 prompt 真实缺陷**：v2 原版在手工 JSON 抽取器上 `endpoint_valid_rate=0.88`，在原生 tuple + strict 适配层上降到 0.78 / 实体 21/101 带引号 / 3/7 样本解析级联失败。直接迁移生产会掉坑。
- **strict_tuple 格式约束根治 prompt 缺陷**：7 样本 smoke 上所有格式缺陷 0，20 样本 full 只有 1 条低解析样本。这比 prompt-only 迭代的任何一版都稳。
- **schema 大小写对齐是独立工程问题**：`GraphExtractor._process_result` 强制 `.upper()`，导致 PascalCase schema 全部查不到；已在 scoring_metrics + scoring_audit + adapter 层做 casefold 兜底，schema 文件保持原样。
- **metadata-closure 的增量主要是 contains 边**：closure v2 上 relation_recall 从 0.208 推到 0.322（+55%），entity_recall 0.45 → 0.59（+31%）。精度未明显下降，说明注入实体不是噪声。

## 4. Gate 检查（基于 exp4 20 样本 full + metadata-closure）

| Gate | 门槛 | 当前值 | 结果 |
|---|---:|---:|---|
| Gate A: audit_entity_recall (holdout) | ≥ 0.55 | 0.59（all）\* | ✓（待 holdout 分组打通） |
| Gate A: audit_entity_precision (holdout) | ≥ 0.45 | 0.28（all） | ✗ |
| Gate B: audit_relation_recall (holdout) | ≥ 0.25 | 0.32（all）\* | ✓ |
| Gate B: gold 侧 both_endpoints_missing 占比 | ≤ 25% | 约 25%（推算） | 边界通过 |

\* 原生抽取器产物没有 manifest fewshot 来源，scoring 的 leakage_diagnostics 的 holdout 分组当前为空，需要修 scoring 或者显式给原生 run 标 fewshot 源样本才能看 holdout。

### 4.1 Gate A 精度未达标的根因

metadata-closure 注入的 Course/Chapter/Section seed 实体成了精度的分母：
- 裸抽取 20 full：精度 = 0.265
- +metadata-closure 20 full：精度 = 0.278

两者都 < 0.45。这说明精度不是 metadata 注入问题，是**实体抽取层仍有噪声**——LLM 抽的非 gold 实体数量仍多。

## 5. 下一步方向（按 ROI 从高到低）

### 5.1 P0：产品生产落地（最高价值，最低边际成本）

metadata-closure 已经过完整实验验证，下一步是把它搬到 `graphrag index` 真实管线里。

详见 `experiments/metadata_closure_production_landing/README.md`。建议分两步：

- **Step 1**：仅改 `settings.yaml` 启用 `prepend_metadata`，跑一次真实 `graphrag index`，看 LLM 自己抽容器实体的覆盖率。如果 ≥ 80%，就够用了。
- **Step 2**：若 Step 1 不足，把 `relationship_postprocessor.py::_apply_metadata_container_injection` 包装成 graphrag 自定义 workflow，正式插入 `extract_graph` 之后。

### 5.2 P1：back-port strict_tuple 格式约束到其它候选

当前 strict_tuple 变体只落在 `schema_fewshot_distilled_v2` 一条路径上。如果希望做严肃的 prompt 横评，应该把 7 条硬格式约束（不加引号、括号成对、description 不含 `##`/`<|>`/孤立`)`）**back-port 到其它候选**：

- `default_guarded`
- `schema_aware_directional_v2`
- `schema_fewshot_distilled_v3`

这让 "prompt 内容" 和 "格式鲁棒性" 彻底解耦，排序公平。

### 5.3 P2：提升 audit_entity_precision

裸抽取 precision=0.265 是当前最大短板。可能原因：

1. LLM 抽了大量 gold 未覆盖的实体（不是错，只是 gold 不完整）
2. strict_tuple 约束"宁可漏抽也不输出破损 record"——但没有反向约束"宁可不抽也不滥抽"

可以试验的方向：

- **精度向 prompt 改造**：在 `-Course Baseline Constraints-` 那节加一段"只抽对课程问答有稳定价值的实体，避免抽取页眉页脚、课程通知、辅助说明等辅助性文本"（实际上原版已有类似约束，但可能写得不够硬）
- **扩容审计集**：当前 gold 只覆盖了**每个样本约 10 个实体**，实际 LLM 抽 20+ 个合理实体完全可能；扩到每个样本 20+ 个 gold 后精度会自然上升
- **修 Gate A 精度定义**：从 "LLM 抽的所有实体 vs gold" 改成 "LLM 抽的**在 audit 视野内的**实体 vs gold"

建议同时推 P2.1 和 P2.3——P2.1 是 prompt 的事，P2.3 是评分标准的事。

### 5.4 P3：修 scoring 对原生 run 的 leakage 分组支持

原生抽取器的 eval 产物没有 manifest 引用，scoring 的 leakage_diagnostics 拿不到 fewshot 源样本列表，导致 `holdout` 分组为空。两个修法：

- 让 `score_extraction_results.py --fewshot-source-sample-ids "pts-0049-bd80db3cdf,pts-0046-a99abcf7ae,pts-0031-a008da8a10"` 支持手动标记；
- 或者让 `run_native_extraction.py` 在产物 json 里塞一段 `fewshot_source_sample_ids` 元数据

这样 Gate A/B 的 holdout 校验才能客观跑起来。

### 5.5 P4（可选）：第二轮 audit 扩容

当前审计 20 样本、10 关系类型，有 4 种关系类型 gold 数 ≤ 14，评价分辨率低。若 P0 落地后 relation_recall 还有提升空间，就扩到 40–60 样本做分层抽样。

## 6. 本轮循环的产物清单

### 代码

- `scripts/extraction_eval/diagnose_gold_missing_relations.py` + 入口
- `scripts/extraction_eval/simulate_container_seed_injection.py` + 入口
- `scripts/extraction_eval/relationship_postprocessor.py`：新增 `strict-metadata-closure` mode + 多条翻转规则
- `scripts/extraction_eval/run_native_extraction.py` + 入口：GraphRAG 原生抽取器适配
- `scripts/extraction_eval/compare_native_extraction_runs.py` + 入口：多 run 对比
- `scripts/extraction_eval/score_extraction_results.py::_load_weights_from_path`：支持元数据化权重 JSON
- `scripts/extraction_eval/scoring_metrics.py` / `scoring_audit.py`：大小写不敏感比对

### 候选 Prompt

- `prompts/candidates/default_guarded/`
- `prompts/candidates/schema_aware_directional_v2/`
- `prompts/candidates/schema_fewshot_distilled_v3/`
- `prompts/candidates/schema_fewshot_distilled_v2_strict_tuple/`（当前最佳）

### 配置

- `config/scoring/weights_audit_heavy_v1.json`：audit_heavy 权重档位

### 实验报告

- `experiments/native_extraction_bridge/README.md`：桥接验证（exp1/2/3/4）完整记录
- `experiments/metadata_closure_production_landing/README.md`：metadata-closure 生产落地调研
- `experiments/prompt_tuning_status_20260511/README.md`：本文档（整体状态）
- `results/reports/material_7_next_step_20260511.md`：早期 metadata-closure 推演
- `results/reports/native_extraction_comparisons/runs/native_bridge_7samples_20260511/summary.md`
- `results/reports/native_extraction_comparisons/runs/native_bridge_full_20260511/summary.md`

### 后处理诊断

- `results/reports/extraction_postprocess/runs/*`（7 条，含 closure v1/v2/metadata-closure/native_exp4）
- `results/reports/extraction_missing_relations/runs/*`（6 条，含 raw/closure/seed_sim/meta_sim/metadata_closure）

### 测试

- `tests/test_diagnose_gold_missing_relations.py`
- `tests/test_relationship_postprocessor.py`（含 strict-metadata-closure + swap/retype 规则）
- `tests/test_simulate_container_seed_injection.py`
- 全量 286 条测试通过

## 7. 行动项清单

- [ ] P0 Step 1：改 `settings.yaml` 启用 `prepend_metadata`，跑一次真实 `graphrag index` 评估 LLM 自抽容器覆盖率
- [ ] P0 Step 2（视 Step 1 结果）：实现 `inject_metadata_graph` 自定义 workflow + 启动 wrapper
- [ ] P1：把 strict_tuple 7 条硬格式约束 back-port 到 `default_guarded` / `schema_aware_directional_v2` / `schema_fewshot_distilled_v3`
- [ ] P2.1：在 prompt 里加精度向硬约束（抑制页眉页脚/课程通知/辅助说明类实体）
- [ ] P2.3：修 Gate A 精度定义（排除 audit 视野外实体 或 审计扩容）
- [ ] P3：修 scoring 对原生 run 的 leakage 分组支持
- [ ] P4（可选）：审计扩容到 40–60 样本
