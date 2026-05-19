# 路由仿真模拟实验记录

> **结论：** 基于当前 canonical 32 题评分矩阵，复杂四模式路由暂时没有可观收益。`basic_only` 已经达到 `effective_score_experimental=0.7500`、平均延迟 `49.85s`、`error_count=0`；离线 oracle 每题择优也只提升 `+0.0037`，且平均延迟略增。Hybrid v0 应先保守落地为 `basic` 主路径，并把 `local` 保留为后续 evidence guardrail 失败后的 fallback；`global` 和 `drift` 不进入学生交互默认路由。

## 实验目标

本实验不新增真实 GraphRAG 调用，只复用已有 `algorithmic_scoring.csv/json`，模拟不同路由策略在同一题集上的预期质量、延迟、错误率和引用命中表现。

要回答的问题：

1. 是否存在一个低成本规则路由，明显优于当前 `basic` 主路径。
2. `local` 是否值得作为默认替代路径，还是只适合作为 fallback。
3. `global` 在高超时预算修补后，是否应该重新进入默认路由。
4. 如果每题都能选到历史最佳模式，理论上限有多高。

## 数据来源

主数据只使用 canonical run：

- run：`graphrag_pipeline/results/qa_eval/runs/baseline-lgb-canonical-20260513-1835`
- 输入：`algorithmic_scoring.csv` / `algorithmic_scoring.json`
- 覆盖：`32` 题、`3` 个模式、共 `96` 行
- 模式：
  - `graphrag-basic-search:latest`
  - `graphrag-local-search:latest`
  - `graphrag-global-search:latest`

敏感性分析使用高超时预算 repair run：

- run：`graphrag_pipeline/results/qa_eval/runs/global-repair-high-timeout-20260515-g0-20260515-204944`
- 输入：`algorithmic_scoring.csv` / `algorithmic_scoring.json`
- 覆盖：仅 `Q025/Q026/Q029` 的 `global`
- 用途：只解释 high-timeout 条件下 `global` 的可答性，不覆盖 canonical 失败。

## 仿真原理

每个策略本质上是在历史评分矩阵中为每道题选择一个已有模式结果，然后对被选中的 `32` 行重新聚合：

- `mean_effective`：平均 `effective_score_experimental`
- `mean_semantic_f1`：平均 `semantic_coverage_f1`
- `citation_recall_at_3`：平均 `citation_recall_at_3`
- `citation_rr`：平均 `citation_rr`
- `mean_elapsed_s`：平均 `elapsed_seconds`
- `error_count`：`error_count` 总和
- `delta_effective_vs_basic`：相对 `basic_only` 的综合分差值
- `delta_elapsed_vs_basic`：相对 `basic_only` 的平均耗时差值

该仿真不是重新生成答案，也不是训练路由器；它只回答“如果路由器选择了历史上这些模式，会得到怎样的指标组合”。

## 策略集合

| policy | 选择规则 | 是否可部署 | 说明 |
| --- | --- | --- | --- |
| `basic_only` | 所有题都选 `basic` | 可作为 v0 基线 | 当前最稳主路径 |
| `local_only` | 所有题都选 `local` | 不推荐默认 | 更慢且综合分更低，可做 fallback |
| `global_only_canonical` | 所有题都选 canonical `global` | 不可默认 | 保留 canonical timeout/error 风险 |
| `category_best_static` | 每个题型选择 canonical 均值最高模式 | 离线参考 | 本轮所有题型均选择 `basic` |
| `v0_conservative` | 所有题先走 `basic` | 可作为 v0 起点 | 后续叠加 Local fallback 与 evidence guardrail |
| `oracle_best_per_question` | 每题选择历史最高 `effective_score_experimental` | 不可部署 | 理论上限，需要知道答案后的结果 |
| `global_repair_sensitivity` | 全部选 `global`，但 `Q025/Q026/Q029` 替换为 high-timeout repair 结果 | 不参与主结论 | 只评估 global 给足预算后的可答性 |

## 策略汇总结果

| policy | n | mean_effective | delta_effective_vs_basic | mean_semantic_f1 | citation_recall_at_3 | citation_rr | mean_elapsed_s | delta_elapsed_vs_basic | error_count | deploy_note |
| --- | ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: | --- |
| `basic_only` | 32 | `0.7500` | `+0.0000` | `0.9903` | `0.5432` | `0.6972` | `49.85` | `+0.00` | `0` | 推荐作为 v0 主基线 |
| `local_only` | 32 | `0.6224` | `-0.1276` | `0.9835` | `0.1990` | `0.3149` | `74.34` | `+24.49` | `0` | 质量低且更慢，只适合 fallback |
| `global_only_canonical` | 32 | `0.4589` | `-0.2911` | `0.8640` | `0.0531` | `0.0860` | `247.56` | `+197.72` | `3` | 保留 timeout/error 风险，不适合作默认 |
| `category_best_static` | 32 | `0.7500` | `+0.0000` | `0.9903` | `0.5432` | `0.6972` | `49.85` | `+0.00` | `0` | 本轮各题型均选择 basic，等价于 `basic_only` |
| `v0_conservative` | 32 | `0.7500` | `+0.0000` | `0.9903` | `0.5432` | `0.6972` | `49.85` | `+0.00` | `0` | 等价 `basic_only`，后续叠加 guardrail/fallback |
| `oracle_best_per_question` | 32 | `0.7537` | `+0.0037` | `0.9903` | `0.5536` | `0.7076` | `50.57` | `+0.72` | `0` | 理论上限，不可部署 |
| `global_repair_sensitivity` | 32 | `0.5048` | `-0.2452` | `0.9542` | `0.0609` | `0.0988` | `199.57` | `+149.72` | `0` | 敏感性分析，不参与主结论 |

## 按题型静态最优

按 `category + mode` 的 canonical 均值选择模式，四类题型都选择 `basic`：

| category | selected_mode | 说明 |
| --- | --- | --- |
| `factual_lookup` | `graphrag-basic-search:latest` | `basic` 综合分和 citation 指标明显领先 |
| `relation_reasoning` | `graphrag-basic-search:latest` | `basic` 比 `local/global` 更高且更快 |
| `chapter_summary` | `graphrag-basic-search:latest` | `basic` 仍优于 `local/global` |
| `global_overview` | `graphrag-basic-search:latest` | 即使考虑 global 的长答案能力，canonical 稳定性和引用命中仍不足 |

因此，当前样本下基于真实 `category` 的静态路由与 `basic_only` 完全等价。

## Oracle 上限分析

`oracle_best_per_question` 每题选择历史最高 `effective_score_experimental`，模式分布如下：

| mode | selected_questions |
| --- | ---: |
| `graphrag-basic-search:latest` | `30` |
| `graphrag-local-search:latest` | `2` |
| `graphrag-global-search:latest` | `0` |

按题型拆分：

| category | basic | local | global |
| --- | ---: | ---: | ---: |
| `factual_lookup` | `8` | `0` | `0` |
| `relation_reasoning` | `8` | `0` | `0` |
| `chapter_summary` | `7` | `1` | `0` |
| `global_overview` | `7` | `1` | `0` |

这说明即使使用事后最优选择，收益也很小：

- `mean_effective` 只从 `0.7500` 提升到 `0.7537`
- 平均耗时从 `49.85s` 增加到 `50.57s`
- 只额外选择 `local` 两题，没有任何题选择 `global`

因此，现阶段不值得为了这点离线上限引入复杂四模式路由。

## Global Repair 敏感性分析

`global_repair_sensitivity` 只将 canonical global 中的 `Q025/Q026/Q029` 替换为 high-timeout repair 结果。该策略用于回答：“如果 global 给足预算并跑完，整体会不会变成好选择？”

repair 三题结果：

| question_id | semantic_coverage_f1 | keyword_recall | citation_recall_at_3 | citation_rr | elapsed_seconds | effective_score_experimental |
| --- | ---: | ---: | ---: | ---: | ---: | ---: |
| `Q025` | `1.0000` | `0.4167` | `0.0000` | `0.0769` | `236.8949` | `0.4553` |
| `Q026` | `0.9524` | `0.8333` | `0.0000` | `0.0000` | `216.2702` | `0.5096` |
| `Q029` | `0.9333` | `0.6667` | `0.2500` | `0.3333` | `190.7918` | `0.5028` |

替换后，`global_repair_sensitivity` 的整体结果为：

- `mean_effective=0.5048`
- `mean_semantic_f1=0.9542`
- `citation_recall_at_3=0.0609`
- `citation_rr=0.0988`
- `mean_elapsed_s=199.57`
- `error_count=0`

解释：

1. high-timeout repair 证明 `global` 给足预算可以回答这些题，且语义覆盖不差。
2. 但整体耗时仍远高于学生问答可接受范围，引用命中仍弱。
3. repair 不能改变 canonical 中 `global` 的稳定性评价，只能作为可答性上限证据。

## 结论规则判定

| 规则 | 判定 |
| --- | --- |
| 若策略比 `basic_only` 提升小于 `0.02` 但更慢，不推荐进入 v0 | `oracle_best_per_question` 只提升 `+0.0037` 且更慢，不推荐 |
| 若策略存在 error 或平均延迟超过 `93s`，不推荐作为学生交互默认路由 | `global_only_canonical`、`global_repair_sensitivity` 均不推荐 |
| `oracle_best_per_question` 只能作为潜在收益上限 | 本实验仅将其作为上限参考 |
| repair 数据只能解释 global 可答性，不改变 canonical 稳定性评价 | 本实验保留 canonical 失败，不覆盖矩阵 |

## 最终结论

1. 当前最合理的 v0 路由不是“四模式智能选择”，而是 **`basic` 主路径 + Local fallback + answer-vs-evidence 语义支撑检测**。
2. `local` 不适合默认替代 `basic`：综合分低 `0.1276`，平均延迟高 `24.49s`。
3. `global` 即使修补 timeout 后仍不适合默认学生交互：平均耗时约 `199.57s`，citation 指标低。
4. 离线 oracle 几乎没有收益，说明当前数据不支持训练复杂路由模型。
5. 下一步应先实现 Hybrid v0 可验证计划中的保守版本，再用真实 hybrid-v0 run 与 `basic_only` 对比，而不是直接引入四模式路由器。

## 下一步建议

1. 按 `2026-05-15-hybrid-qa-v0-verifiable-plan.md` 实现 v0，但把默认路径固定为 `basic`。
2. 在线语义检测只做 answer-vs-evidence guardrail，不使用 gold answer。
3. Local fallback 只在 guardrail 失败、答案过短、或 citation/evidence 支撑不足时触发。
4. Global 保留为离线报告生成或人工复核路径，不进入默认学生问答路由。
5. 若后续累计更多真实 hybrid-v0 样本，再考虑把本实验固化为 `simulate_routing_policies.py`，并引入简单可解释模型。
