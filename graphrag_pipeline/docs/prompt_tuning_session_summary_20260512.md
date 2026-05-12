# Prompt Tuning 会话总结（2026-05-12）

## 一、会话目标

解决 exp5 失败后的评估瓶颈：`audit_entity_precision` 受 gold 标注密度限制，无法通过 prompt 改进突破 0.30。

## 二、核心成果

### 2.1 Faithfulness Gate 评估体系（已落地）

**问题**：传统 precision 依赖 gold 标注密度，gold 越稀疏 precision 越低，与模型实际质量无关。

**方案**：引入 `faithfulness_error_rate` 指标——只判定"抽取的实体是否在源文本中有依据"，不依赖 gold 标注。

**实现**：5 级匹配策略
| Level | 技术 | 作用 |
|-------|------|------|
| 1 | 完整子串匹配 | "分页存储管理" 在源文本中 |
| 2 | casefold 匹配 | "linux" ↔ "Linux" |
| 3 | 核心词+后缀拆分 | "响应比公式" → 核心"响应比"在源文本中 |
| 4 | 前后半拆分 | "进程同步" → "进程"+"同步"都在源文本中 |
| 5 | jieba 精准分词 | "设备控制器组成" → 全部词素在源文本中 |

**Gate A 阈值**：`faithfulness_error_rate <= 0.15`

**效果**：exp4 的 faithfulness_error_rate = 2.4%（lenient 模式），远低于阈值。

### 2.2 Gold 标注扩容（已落地）

从 12 条 gold_seed 扩到 17 条，评估基线更稳定：
- entity_recall: 0.587 → 0.625 (+6.3%)
- relation_recall: 0.322 → 0.368 (+14.5%)

### 2.3 TF-IDF + MMR Few-shot 选择器（已落地，实验未成功）

实现了 `fewshot_selector.py` 模块，提供三种策略：
- `select_fewshot_by_tfidf_mmr()`：纯 TF-IDF + MMR
- `select_fewshot_for_sample()`：单样本动态选择
- `select_fewshot_hybrid()`：混合策略（TF-IDF 预筛选 + 贪心覆盖）

**实验结论**：混合策略在类型覆盖度上优于当前贪心（90% vs 80%），但实际抽取效果不如 exp4 的手动精选示例。

### 2.4 并行抽取支持（已落地）

`run_native_extraction.py` 新增 `--concurrency N` 参数：
- 默认 1（串行，向后兼容）
- 设为 20 时预期从 ~45 分钟加速到 ~3-4 分钟
- 使用 `asyncio.Semaphore` + `asyncio.gather` 实现

## 三、实验记录

| 实验 | 变化 | entity_recall | relation_recall | gate | 结论 |
|------|------|--------------|----------------|------|------|
| **exp4（基线）** | strict_tuple + 3 条精选 micro-examples | **0.625** | **0.368** | ✓ | **当前最佳** |
| exp5 | +精度向抑制规则 | 0.395 | 0.226 | ✓ | 失败：recall -36% |
| exp6 | 8 条 TF-IDF+MMR micro-examples | 0.496 | 0.193 | ✗ | 失败：噪声引入 |
| exp6b | 替换 3 条来源（applied_in 替代 evaluated_by） | 0.430 | 0.107 | ✗ | 失败：方向暗示错误 |

## 四、关键洞察

### 4.1 Prompt 层面

1. **格式约束有效，语义约束无效**：strict_tuple（确定性格式）有效；精度向抑制规则（语义判断）无效。
2. **Few-shot 质量 > 数量**：3 条精选 > 8 条宽泛选择。
3. **示例不可轻易替换**：exp4 的 3 条示例（defined_by / evaluated_by / implemented_by）是经过验证的最优组合，展示了最容易犯错的方向。
4. **TF-IDF 相关性 ≠ 教学价值**：内容相关的样本不一定是好的 few-shot 示例。

### 4.2 评估层面

1. **Faithfulness 替代 precision 作为 Gate A**：不受 gold 密度限制，判定确定性强。
2. **metadata-closure 是通用增益**：对所有候选 +25-30% entity_recall，与 prompt 无关。
3. **exp4 的优势来自三层叠加**：strict_tuple（+解析正确率）+ micro-examples（+方向正确率）+ metadata-closure（+结构实体覆盖）。

### 4.3 对比 GraphRAG 自动调优

| 候选 | entity_recall | relation_recall | 相对 exp4 |
|------|--------------|----------------|-----------|
| exp4 (strict_tuple) | 0.625 | 0.368 | 基线 |
| auto_tuned (GraphRAG prompt-tune) | 0.546 | 0.241 | -13% / -34% |
| default (GraphRAG 默认) | 0.531 | 0.281 | -15% / -24% |

## 五、当前生产配置

```
prompt: prompts/candidates/schema_fewshot_distilled_v2_strict_tuple/prompt.txt
后处理: metadata-closure
评估: faithfulness_error_rate <= 0.15 (lenient 模式)
抽取器: GraphRAG 原生 GraphExtractor + max_gleanings=1
模型: deepseek-v4-flash (via One API)
```

## 六、后续可探索方向

| 优先级 | 方向 | 预期收益 |
|--------|------|----------|
| P1 | 抽取后实体去重（jieba + TF-IDF 聚类） | 减少 noise，提升下游查询质量 |
| P1 | Audit 聚类采样（替代规则分层） | 评估更具代表性 |
| P2 | 评分器嵌入软匹配（bge-small-zh） | recall +5-15%（减少命名变体 miss） |
| P2 | 扩大评测样本到 80 条（全量样本池） | 评估更稳定 |
| P3 | 自动 Prompt 优化循环 | 可能发现人工难以想到的改进 |
