# Exp6 实验结论：混合策略 Few-shot 选择

## 实验目的

验证 TF-IDF + MMR 混合策略选出的 8 条 micro-examples 是否比 exp4 的 3 条手动精选示例带来 recall 提升。

## 实验配置

- 候选 prompt: `schema_fewshot_hybrid_v1_strict_tuple`
- 基础: exp4 的 `schema_fewshot_distilled_v2_strict_tuple`
- 变化: Micro-examples 从 3 条扩到 8 条（TF-IDF+MMR 混合策略选择）
- 模型: deepseek-v4-flash
- 样本: 20 条 audit 样本，max_gleanings=1

## 结果对比

| 指标 | exp4 | exp6 | 变化 |
|------|------|------|------|
| audit_entity_recall | 0.625 | 0.496 | **-20.6%** |
| audit_relation_recall | 0.368 | 0.193 | **-47.7%** |
| audit_entity_precision | 0.293 | 0.296 | +1.1% |
| faithfulness_error_rate | 0.024 | 0.038 | +1.4pp |
| endpoint_valid_rate | 1.000 | 0.917 | -8.3% |
| gate_passed | True | **False** | 未通过 |
| endpoint_invalid_count | 0 | 52 | 新增 52 条无效关系 |
| duplicate_entity_rate | 0.000 | 0.061 | 新增重复 |

## 失败原因分析

1. **示例数量过多引入噪声**：8 条 micro-examples 中部分示例的方向/类型组合给了模型错误暗示
2. **endpoint 错误激增**：52 条关系的 source/target 类型不匹配 schema 约束（exp4 为 0）
3. **重复实体出现**：duplicate_entity_rate 从 0 升到 6%

## 关键洞察

- **micro-examples 不是越多越好**：3 条精选 > 8 条宽泛选择
- **示例质量 > 示例数量**：exp4 的 3 条示例经过人工验证，每条都精确展示一种关系方向
- **TF-IDF 相关性不等于教学价值**：内容相关的样本不一定是好的 few-shot 示例

## 行动

- exp4 仍为当前最佳配置
- 混合策略选择器保留为工具，但不直接用于增加 micro-examples 数量
- 下一步改进方向：用混合策略选择 3 条（而非 8 条），或用于选择更好的 3 条替代当前固定选择
