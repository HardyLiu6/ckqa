# 步骤 8 规则化评测分析

**生成时间：** 2026-04-18
**对应 run：** `2026-04-18T182652`（见 [extraction_scoring/runs/2026-04-18T182652/](extraction_scoring/runs/2026-04-18T182652/)）
**输入产物：** [extraction_compare.csv](extraction_scoring/runs/2026-04-18T182652/extraction_compare.csv) / [extraction_compare.md](extraction_scoring/runs/2026-04-18T182652/extraction_compare.md) / [top_candidates.json](extraction_scoring/runs/2026-04-18T182652/top_candidates.json)
**跨 run 明细：** [extraction_scoring/history.csv](extraction_scoring/history.csv)
**最新 run 指针：** [extraction_scoring/latest.json](extraction_scoring/latest.json)
**评测脚本：** [score_extraction_results.py](../../scripts/score_extraction_results.py)

## 1. 评测规则

### 1.1 指标（10 项）

| 指标 | 公式 | 分母口径 |
|---|---|---|
| `parse_success_rate` | `#(status==success) / 样本总数` | 全部样本 |
| `schema_hit_rate` | 成功样本里**实体类型全合法且关系类型全合法**的样本占比 | 成功样本 |
| `entity_type_valid_rate` | `type ∈ schema` 的实体数 / 实体总数 | 成功样本实体合计 |
| `relation_type_valid_rate` | `type ∈ schema` 的关系数 / 关系总数 | 成功样本关系合计 |
| `endpoint_valid_rate` | 端点可在同样本中解析为实体且 `(src.type, tgt.type) ∈ schema.source_types × target_types` 的关系数 / 类型合法的关系数 | 类型合法的关系（类型非法不计入分母） |
| `duplicate_entity_rate` | 同样本内 `(归一化title, type)` 的多余记录数 / 实体总数 | 同上 |
| `noise_entity_rate` | 命中噪声规则的实体数 / 实体总数。规则：空/短<2 非英文/纯数字/仅标点/停用词表 | 同上 |
| `output_stability` | `1 - min(1, CV(实体数)+CV(关系数))`，CV=pstdev/mean；成功样本<2 时记 1.0 | 成功样本计数序列 |
| `audit_entity_recall`（软） | 每样本 gold 实体归一化标题与抽取实体的精确/子串匹配率，再按样本平均 | audit ∩ eval 的交集 |
| `audit_relation_recall`（软） | 把 gold `entity_id→name`，比对 `(src_norm, type, tgt_norm)` 三元组是否在抽取结果里出现 | 同上 |

### 1.2 打分与排序

- **composite_score** = Σ(weight × metric)，权重合计 1.00；`audit_*` 为 `None` 时按比例摊回剩余 8 项。
- **默认权重**：解析(0.20) / 类型(0.15+0.15) / 端点(0.15) / schema 命中(0.10) / 重复+噪声+稳定(各 0.05) / audit(0.05+0.05)。
- **排序 tie-breaker**：composite → parse_success → endpoint_valid → 名称字典序。

## 2. 实际产物分析

### 2.1 关键数据

| # | candidate | composite | endpoint | stability | audit_ent | audit_rel |
|---|---|---|---|---|---|---|
| 1 | auto_tuned | 0.8973 | **0.9091** | 0.6289 | 0.5893 | 0.0000 |
| 2 | default | 0.8965 | 0.9070 | 0.6730 | 0.5357 | 0.0000 |
| 3 | schema_fewshot | 0.8890 | 0.8163 | 0.6711 | **0.6607** | 0.0000 |
| 4 | schema_aware | 0.8795 | 0.7500 | 0.6793 | **0.6607** | 0.0000 |

所有候选 `parse_success / schema_hit / entity_type_valid / relation_type_valid` 均为 1.0，重复与噪声均为 0——说明步骤 7 的解析与 schema 约束已经把**硬合规性问题**清干净，当前排序完全由**软指标**决定。

### 2.2 三个值得关注的诊断

#### 诊断 A：四者差距极小（0.018）

Top-1 与 Top-4 仅差 1.8 个百分点；`auto_tuned` 主要靠端点命中略高挤下 `default`。这种小差距在 5 样本下容易受噪声影响，单次排名不足以作为定论。

#### 诊断 B：`audit_relation_recall` 全员 0

audit 集有 20 条样本，eval 集仅 5 条，交集 = 2 条（`pts-0004-ac3447c62d` 与 `pts-0005-a0753fd9ff`）。典型对照：

| 来源 | 三元组 |
|---|---|
| gold (pts-0004) | `第九章 操作系统接口 --[contains]--> 第九章 操作系统接口习题` |
| auto_tuned | `第九章 操作系统接口 --[contains]--> 习题` 与 `习题 --[contains]--> 操作系统接口习题集` |

**实体召回**因用子串匹配能拿到 0.53–0.66；**关系召回**要求 `(src, type, tgt)` 三元组归一化精确相等——任何一端名称漂移就 0 分。这是当前规则下的**已知刚性假阴性**，不代表候选真的零召回。

#### 诊断 C：`output_stability` 全部落在 0.63–0.68

说明 5 条样本间抽取数量波动较大（CV ≈ 0.3+）。长文本样本抽得多、短文本抽得少是正常现象，但把 CV 直接当"稳定度"会对长度差异敏感。当前权重下稳定度只占 5%，影响有限；若后续要加权重需先按样本长度分桶再算 CV。

### 2.3 Top-2 结论

`auto_tuned` 与 `default` 硬合规满分 + 端点有效性 > 0.9，可作为步骤 9（QA 评测）的入场候选。`schema_aware` / `schema_fewshot` 在**单样本 audit 实体召回**上更高（0.66），但端点有效性掉到 0.75 / 0.82，暗示它们更激进地造实体/造边，需在 QA 侧再验证是否有用。

## 3. 下一步建议

按"成本由低到高、收益由高到低"排序：

### 建议 1：在进入步骤 9 之前，先补 2 条便宜的 audit 修正

1. **把 eval 样本集对齐到 audit 集**。现在 5 条 eval 样本里仅 2 条有 gold，audit 软指标几乎没数据。最简做法是**让下次步骤 7 直接跑 audit 集的 20 条样本**（传 `--samples data/eval/audit_extraction_set.json`，同时更新 `prompt_loader.load_samples` 让它能从 audit payload 的 `audit_samples[*].text` 读正文；或者单独写一个小转换脚本把 audit → samples 格式）。这会一次性把 audit_* 两项指标从 2 样本变 20 样本，统计可信度大幅提高。

2. **把 `audit_relation_recall` 的精确三元组匹配放宽**。两个低成本方案：
   - 端点改成**子串匹配**（与实体 recall 口径一致）：`gold_src_norm ∈ extracted_src_norm` 或反之。
   - 或者引入**实体先对齐**：先按实体 recall 的同样规则把 gold entity 对齐到 extracted entity，再用对齐后的 title 去查三元组。
   这两项都只改 `scoring_audit.py` 的 `compute_audit_relation_recall`，加 2-3 个测试就能收尾。

### 建议 2：决定 Top-2 之后是否还要继续生候选

当前 4 候选的 composite 差距 < 2%，且硬合规都满分。继续往下调 Prompt 的边际收益已经很小。务实策略是：

- **直接进入步骤 9**，用 QA 任务把 `auto_tuned` 与 `default` 拉一次真实对比（QA 才是最终目标，不是抽取完美度）。
- 如果 QA 分不出差距，就**固化 `auto_tuned`**（它是目前排序第一且由 GraphRAG 官方 prompt-tune 生成，链路可复现）。
- 如果 QA 分得开，再按 QA 结论回头修 Prompt。

### 建议 3：步骤 8 自身的两个小优化（非阻塞）

- `output_stability` 改为**按样本长度分桶后再算 CV**，避免把"长文本抽得多"算成不稳定。这不紧急，因为当前权重只占 5%。
- 在 `extraction_compare.md` 里加一列"相对 Top-1 的差距"（百分比），报告更直观。可以用 `--weights` 参数或者在 `scoring_report.py` 里加一行。

### 建议 4：不建议做的

- ❌ **不要**在当前 5 样本 + audit_rel=0 的状态下给指标加更多权重或改排序规则。数据量没起来，调权重只是把噪声挤来挤去。
- ❌ **不要**现在重新生成候选 Prompt。Top-2 的 composite 差距 0.0008（千分之一），生成新候选的成本远高于它能带来的排名改变。

### 执行顺序

```
1. 扩大 eval 样本到 audit 全量（20 条） ─┐
                                         ├─ 让 audit 指标先变可信
2. 放宽 audit_relation_recall 的匹配口径 ─┘

3. 跑一次 score_extraction_results.py，看排序是否稳定

4. 进入步骤 9：QA 评测，对 Top-2 做实战对比

5. 根据 QA 结果决定是否回头调整步骤 8 指标或 Prompt
```

每一步都能独立出 PR，互不阻塞。
