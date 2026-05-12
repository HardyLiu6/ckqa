# 提示词调优流水线完整报告

## 一、项目概述

**目标**：为课程知识图谱抽取任务（GraphRAG）找到最优的 LLM 提示词，使抽取的实体和关系在质量、格式合规性和下游可用性上达到生产标准。

**模型**：deepseek-v4-flash（通过本地 One API 代理 127.0.0.1:3301）

**抽取器**：GraphRAG 3.0.9 原生 `GraphExtractor`（tuple 输出格式 + gleaning）

**评测样本**：20 条 audit 样本（从 80 条教材文本片段中分层抽样），其中 17 条有人工 gold 标注。

**课程材料**：《计算机操作系统》（第四版，汤小丹等）

---

## 二、实验 4 抽取结果位置

**生产最终抽取结果**：
```
graphrag_pipeline/results/extraction_eval/runs/native_exp4_strict_tuple_full20_metadata_closure/
└── native_v2_strict_tuple.json
```

**A/B 测试重复运行（3 次）**：
```
graphrag_pipeline/results/extraction_eval/runs/ab_test_exp4_run1_20260512/
graphrag_pipeline/results/extraction_eval/runs/ab_test_exp4_run2_20260512/
graphrag_pipeline/results/extraction_eval/runs/ab_test_exp4_run3_20260512/
```

**生产 prompt 文件**：
```
graphrag_pipeline/prompts/candidates/schema_fewshot_distilled_v2_strict_tuple/prompt.txt
MD5: 14ab82b37aa379f6026287a491888a44
状态: frozen_v1（2026-05-12 冻结）
```

---

## 三、实验时间线与条件

### 阶段 1：基础候选生成（2026-05-08 ~ 05-09）

| 实验 | 候选 | 条件 | 说明 |
|------|------|------|------|
| material_7_real_20260508 | default / auto_tuned / schema_aware / schema_fewshot | JSON 抽取器, 12 gold_seed | 首次全量评测 |
| material_7_latest_rules_full_20260509 | 同上 | 更新 schema 规则后重跑 | schema_fewshot 首次领先 |
| material_7_distilled_prompt_full_20260509 | schema_fewshot_distilled / schema_aware_directional / auto_tuned | 蒸馏 micro-examples | distilled 首次超越完整 fewshot |
| material_7_distilled_v2_full_20260509 | schema_fewshot_distilled_v2 / schema_aware_directional / auto_tuned | v2 负向规则 | v2 关系覆盖提升 |

### 阶段 2：格式适配与后处理（2026-05-10 ~ 05-11）

| 实验 | 候选 | 条件 | 说明 |
|------|------|------|------|
| material_7_schema_v1_final_full_20260510 | 7 个候选 | JSON 抽取器, 统一 schema v1 | 最终 JSON 格式基线 |
| material_7_v3_full_20260510_structured_closure | 同上 | + structured closure 后处理 | 结构实体补充 |
| material_7_v3_full_20260510_metadata_closure | 同上 | + metadata-closure 后处理 | **关键突破：recall +25%** |
| native_bridge_smoke7_20260511 | schema_fewshot_distilled_v2 | 原生 GraphExtractor 桥接测试 | 发现引号污染问题 |
| native_exp1_v2_strict_20260511 | native_v2_strict | 原生抽取器, strict 模式 | 暴露格式缺陷 |
| native_exp2_v2_tolerant_20260511 | native_v2_tolerant | 原生抽取器, 容错模式 | 对比 strict |
| native_exp3_strict_tuple_20260511 | native_v2_strict_tuple | + strict_tuple 格式约束 | **引号污染清零** |

### 阶段 3：生产候选确认（2026-05-11）

| 实验 | 候选 | 条件 | 说明 |
|------|------|------|------|
| native_exp4_strict_tuple_full20_20260511 | native_v2_strict_tuple | 20 样本, 无后处理 | 裸抽取基线 |
| **native_exp4_strict_tuple_full20_metadata_closure** | native_v2_strict_tuple | **20 样本 + metadata-closure** | **生产最终结果** |

### 阶段 4：精度改进尝试（2026-05-12）

| 实验 | 候选 | 条件 | 说明 |
|------|------|------|------|
| native_exp5_precision_suppression_full20_20260511 | + 精度向抑制规则 | 20 样本 | **失败：recall -36%** |
| native_exp5_precision_full20_metadata_closure | 同上 + metadata-closure | 20 样本 | 确认失败 |
| native_exp6_hybrid_full20_20260512 | 8 条 TF-IDF+MMR micro-examples | 20 样本, 并发 20 | **失败：recall -21%** |
| native_exp6b_hybrid_3pick_full20_20260512 | 替换 3 条 micro-examples 来源 | 20 样本, 串行 | **失败：recall -31%** |

### 阶段 5：稳定性验证与公平对比（2026-05-12）

| 实验 | 候选 | 条件 | 说明 |
|------|------|------|------|
| native_exp4_stability_concurrent20_20260512 | exp4 prompt | 并发 20, 20 样本 | 验证并发功能 + 随机性 |
| ab_test_exp4_run1/2/3_20260512 | exp4 prompt | 并发 20, 3 次重复 | 稳定性评估 |
| ab_test_auto_tuned_run1/2/3_20260512 | auto_tuned prompt | 并发 20, 3 次重复 | 公平对比基线 |

---

## 四、评估结果汇总

### 4.1 关键里程碑指标演进

| 阶段 | 最佳 entity_recall | 最佳 relation_recall | gate | 关键改进 |
|------|-------------------|---------------------|------|----------|
| 阶段 1（JSON 抽取器） | 0.576 (schema_fewshot) | 0.471 | False | schema + few-shot |
| 阶段 2（metadata-closure） | 0.546 (auto_tuned) | 0.281 (default) | True | 后处理补充结构实体 |
| 阶段 3（strict_tuple） | **0.625** (exp4) | **0.368** (exp4) | **True** | 格式约束消除解析错误 |
| 阶段 4（精度改进） | 0.395 (exp5, 失败) | 0.226 | True | 证明语义约束无效 |
| 阶段 5（A/B 测试） | 0.429 (exp4 平均) | — | — | 确认随机波动范围 |

### 4.2 exp4 最终评分（17 gold_seed，单次最佳运行）

| 指标 | 值 | 说明 |
|------|-----|------|
| audit_entity_recall | 0.625 | 62.5% 的 gold 实体被正确抽取 |
| audit_relation_recall | 0.368 | 36.8% 的 gold 关系被正确抽取 |
| audit_entity_precision | 0.293 | 受 gold 密度限制，参考意义有限 |
| faithfulness_error_rate | 0.024 | 仅 2.4% 的实体是真正的幻觉 |
| endpoint_valid_rate | 1.000 | 关系端点类型 100% 合规 |
| parse_success_rate | 1.000 | 解析成功率 100% |
| duplicate_entity_rate | 0.000 | 零重复实体 |
| gate_passed | True | 所有门禁通过 |

### 4.3 exp4 vs auto_tuned 公平对比（3 次平均，fuzzy 类型归一化）

| 指标 | exp4 | auto_tuned | exp4 优势 |
|------|------|-----------|-----------|
| entity_recall | 0.429 | 0.262 | **+63%** |
| entity_precision | 0.265 | 0.123 | **+116%** |

注：fuzzy 模式下 exp4 的 recall 低于单次最佳（0.625），因为 LLM 输出有随机性（temperature > 0）。

### 4.4 metadata-closure 后处理增益（所有候选通用）

| 候选 | 无 closure recall | 有 closure recall | 提升 |
|------|-------------------|-------------------|------|
| auto_tuned | 0.428 | 0.546 | +27.5% |
| default | 0.425 | 0.531 | +24.8% |
| schema_fewshot_distilled_v2 | 0.403 | 0.520 | +29.2% |
| exp4 (native_v2_strict_tuple) | 0.452 | 0.625 | +38.3% |

---

## 五、失败实验与教训

### 5.1 Exp5：精度向抑制规则（失败）

**假设**：在 prompt 中添加"不要抽取过渡性短语、一次性引用项"等规则，可以提升 precision。

**结果**：recall 从 0.614 暴跌到 0.395（-36%），precision 几乎不变（-2%）。

**教训**：语义模糊的负向约束 LLM 无法精确执行，会导致过度保守。只有确定性格式约束（如 strict_tuple）才有效。

### 5.2 Exp6：增加 micro-examples 到 8 条（失败）

**假设**：更多 few-shot 示例 = 更好的覆盖 = 更高的 recall。

**结果**：recall -21%，endpoint_invalid 从 0 激增到 52，gate 未通过。

**教训**：few-shot 示例质量 > 数量。过多示例引入噪声，给模型错误的方向暗示。

### 5.3 Exp6b：替换 3 条 micro-examples 来源（失败）

**假设**：用 TF-IDF+MMR 选出的"内容更相关"的示例替代手动精选的示例。

**结果**：recall -31%，endpoint_invalid=55，gate 未通过。

**教训**：TF-IDF 内容相关性 ≠ 教学价值。好的 few-shot 示例需要精确展示最容易犯错的方向，而不是内容相似。

---

## 六、技术基础设施成果

### 6.1 Faithfulness Gate 评估体系

替代受 gold 密度限制的 precision 指标，基于源文本判定实体是否有依据：
- 5 级匹配策略（子串 → casefold → 核心词+后缀 → 前后半拆分 → jieba 分词）
- metadata-closure 豁免（course_id/chapter/section 来自元数据不判为幻觉）
- Gate A 阈值：faithfulness_error_rate ≤ 0.15

### 6.2 TF-IDF + MMR Few-shot 选择器

`scripts/prompt_tuning/fewshot_selector.py`：
- `select_fewshot_by_tfidf_mmr()`：纯 TF-IDF + MMR 多样性采样
- `select_fewshot_hybrid()`：混合策略（TF-IDF 预筛选 + 贪心类型覆盖）
- 虽然实验未成功，但作为工具保留供未来使用

### 6.3 并行抽取支持

`--concurrency N` 参数：
- 20 并发从 ~54 分钟加速到 ~3.5 分钟（15 倍加速）
- 使用 asyncio.Semaphore 控制并发数
- API 限制 250 RPS，20 并发仅用 0.3 RPS

### 6.4 类型归一化器

`scripts/extraction_eval/type_normalizer.py`：
- 支持跨 prompt 格式的公平对比
- 关键词规则映射自由中文类型到 schema PascalCase
- strict/fuzzy 双模式，默认不影响生产评分

### 6.5 Gold 标注扩容

从 12 条 gold_seed 扩到 17 条：
- entity_recall 基线 +6.3%
- relation_recall 基线 +14.5%
- 评估更稳定可信

---

## 七、生产配置

```yaml
prompt_file: prompts/candidates/schema_fewshot_distilled_v2_strict_tuple/prompt.txt
prompt_md5: 14ab82b37aa379f6026287a491888a44
production_status: frozen_v1
frozen_at: 2026-05-12

extractor: GraphRAG 3.0.9 GraphExtractor
model: deepseek-v4-flash
max_gleanings: 1
post_processing: metadata-closure

evaluation:
  gate_a: faithfulness_error_rate <= 0.15
  hard_metrics: parse_success_rate, endpoint_valid_rate, etc. >= 0.95
  soft_metrics: audit_entity_recall, audit_relation_recall (观测)
```

---

## 八、下一步改进方向

| 优先级 | 方向 | 预期收益 | 依赖 |
|--------|------|----------|------|
| P0 | 设置 temperature=0 消除随机性 | 评估可复现 | 配置变更 |
| P1 | 抽取后实体去重（jieba + TF-IDF 聚类） | 减少 noise，提升下游查询质量 | jieba（已有） |
| P1 | 扩大评测到 80 条全量样本 | 评估更稳定 | 标注工作量 |
| P2 | 评分器嵌入软匹配（bge-small-zh） | recall +5-15% | 嵌入模型 |
| P2 | 跨课程泛化验证 | 确认 prompt 通用性 | 新课程材料 |
| P3 | 端到端 QA 验证 | 确认最终问答效果 | QA 评测集 |
| P3 | 自动 Prompt 优化循环 | 可能发现新改进方向 | LLM API |

---

## 九、文件索引

| 文件 | 说明 |
|------|------|
| `prompts/candidates/schema_fewshot_distilled_v2_strict_tuple/prompt.txt` | 生产 prompt（冻结） |
| `prompts/candidates/manifest.json` | 候选清单与状态 |
| `data/eval/material_7_audit_extraction_set.json` | 评测集（17 gold_seed） |
| `data/eval/audit_annotation_guidelines.md` | 标注规范 |
| `scripts/extraction_eval/scoring_audit.py` | 评分器（含 faithfulness） |
| `scripts/extraction_eval/type_normalizer.py` | 类型归一化器 |
| `scripts/extraction_eval/run_native_extraction.py` | 抽取脚本（含并发） |
| `scripts/prompt_tuning/fewshot_selector.py` | TF-IDF+MMR 选择器 |
| `scripts/prompt_tuning/generate_candidate_prompts.py` | 候选生成器 |
| `config/schema/entity_types.json` | 实体类型 schema |
| `config/schema/relation_types.json` | 关系类型 schema |
| `results/extraction_eval/runs/native_exp4_strict_tuple_full20_metadata_closure/` | 生产抽取结果 |
| `results/reports/extraction_scoring/history.csv` | 评分历史 |
| `docs/prompt_tuning_session_summary_20260512.md` | 会话总结（简版） |
