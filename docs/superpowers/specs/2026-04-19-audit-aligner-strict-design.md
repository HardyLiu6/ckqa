# Audit 对齐器严格化设计

- 日期：2026-04-19
- 范围：`graphrag_pipeline/scripts/scoring_audit.py`、`graphrag_pipeline/scripts/diagnose_step8.py` 与对应测试
- 目标：让 audit 命中率反映真实抽取命中，而不是字符串碰巧相像；消除"选短子串"、"同名撞车"、"不读 alias"三条系统性偏差。

## 1. 背景

### 1.1 当前诊断现象

`2026-04-19T005540.md` 步骤 8 诊断报告显示：

- 四个候选（`auto_tuned` / `default` / `schema_aware` / `schema_fewshot`）在 14 条 gold relation 上均 `hit = 0`。
- 大量条目 verdict 为 `triple_not_in_ext`，而实际上是两端之一被对齐到错的 ext。
- 典型错配：gold `第九章 操作系统接口习题`（Assignment）被折叠到 ext `第九章操作系统接口`（Chapter）。
- `auto_tuned` 仅以 40:39 的 valid 计数微胜 `default`，`composite_score` 排名无法反映真实质量差异。

### 1.2 根因

现行 `_align_gold_to_extracted`（[scoring_audit.py:118-151](../../../graphrag_pipeline/scripts/scoring_audit.py#L118-L151)）有三条病根：

1. **选短不选对**：子串候选按 `(len(ext), idx)` 升序取首，较短的 ext 总是赢，长 gold 被折叠。
2. **不看类型**：Chapter gold 与 Assignment gold 若共享前缀就会命中同一 ext，造成自环 / 类型错位。
3. **不读 alias**：gold 标注了 `alias` 字段，对齐器从未消费。

`diagnose_step8.py` 与 `_entity_hit` 都复用这段逻辑，所以三条软指标（`audit_entity_recall` / `audit_entity_precision` / `audit_relation_recall`）全部带同一偏差。

### 1.3 范围约束

- **只改评测侧**：不改 `config/schema/`、不改 `prompts/candidates/`、不改 `data/eval/audit_extraction_set.json`、不改 `scoring_metrics.py` 中的权重与 gate 阈值。
- 对外公开函数签名保持不变，`score_extraction_results.py` 无感。

## 2. 设计原则

优先级自上而下：

1. **不误命中**：严格相等优先，宁可召回下降，不允许假命中。
2. **可解释**：每条 align 结果带 `match_mode`，诊断表能直接读出。
3. **可复现**：同一输入任意次运行结果一致，tie-break 规则显式写进 docstring。
4. **召回**：最后才考虑，不为此放松前三条。

## 3. 数据结构与接口

### 3.1 核心类型

```python
from dataclasses import dataclass
from typing import Literal

@dataclass(frozen=True)
class ExtCandidate:
    idx: int            # sample 内稳定下标，用作 one-to-one 占用键
    title_norm: str     # _normalize_title(entity.title)
    type: str           # entity.type 原值（未经白名单过滤）

@dataclass(frozen=True)
class GoldEntity:
    gold_id: str
    name_norm: str
    alias_norms: tuple[str, ...]
    type: str

MatchMode = Literal[
    "exact",
    "alias",
    "exact_occupied",
    "alias_occupied",
    "none",
]

@dataclass(frozen=True)
class AlignResult:
    matched_ext_idx: int | None   # None 当且仅当 match_mode == "none"/"*_occupied"
    match_mode: MatchMode
```

`MatchMode` 为五值闭集，任何诊断表 / 测试 / 报告渲染都必须严格使用这组字面量。`*_occupied` 表示"本可命中，但该 ext 已被更早的 gold 占用"，`matched_ext_idx = None`。

### 3.2 规范化入口（单一来源）

`scoring_metrics._normalize_title` 是唯一规范化函数，gold.name / gold.alias / ext.title 三路入口必须且只能走它。

新增薄包装：

```python
def canonicalize_gold_aliases(aliases: Sequence[str]) -> tuple[str, ...]:
    """Normalize + 丢掉空串，保留原顺序。"""
```

### 3.3 sample 级对齐主函数

```python
def align_sample(
    gold_entities: Sequence[GoldEntity],
    ext_candidates: Sequence[ExtCandidate],
) -> dict[str, AlignResult]:
    """把一个 sample 的 gold_entities 对齐到 ext_candidates。

    规则：
      优先级 1：ext.title_norm == gold.name_norm 且 ext.type == gold.type（exact）
      优先级 2：存在 a in gold.alias_norms 使 ext.title_norm == a 且 ext.type == gold.type（alias）
      其余：none

    约束：
      - ext_candidates 使用 ExtCandidate.idx 作为占用键；同一 sample 内 one-to-one。
      - 当 exact 阶段存在匹配项但全部被占用，返回 exact_occupied 并吞掉后续 alias 阶段；
        当 exact 阶段完全无匹配再进 alias，alias 阶段同类情形返回 alias_occupied。
        具体算法见 3.4。
      - gold 遍历顺序固定为 gold_id 字典序；候选遍历顺序固定为 ExtCandidate.idx 升序。
        这是确定性 tie-break，不是语义最优，评测稳定优先于召回最大。
      - 无 ext_candidates 时所有 gold 返回 none。
    """
```

#### 3.3.1 为什么单条 gold 命中 `*_occupied` 不再向下找别的 ext

这一设计是评测决策，不是实现偷懒。其理由：

- `*_occupied` 在 strict 规则下意味着：存在一个 ext，其 `(title_norm, type)` 与当前 gold 精确一致但已被更早的 gold 占用。这种情况要么是 gold 标注有歧义，要么是 ext 侧出现同名同类型的多实例。
- 若此时继续向下找下一个"名字不同但可能相关"的 ext，就重新引入了我们刚才拆掉的模糊匹配。
- 保留 `*_occupied` 作为独立状态、不再继续搜索，能让诊断表直接把这类冲突暴露出来，便于后续决定是否去 schema 层或 gold 层修边。

### 3.4 aligner 级别函数（为 align_sample 服务）

`align_sample` 内部调用：

```python
def _align_one(
    gold: GoldEntity,
    candidates: Sequence[ExtCandidate],
    claimed: set[int],
) -> AlignResult
```

算法定义（按 `ExtCandidate.idx` 升序遍历候选）：

```
# Phase 1: exact
exact_unclaimed_idx = first cand.idx where
    cand.title_norm == gold.name_norm AND cand.type == gold.type
    AND cand.idx not in claimed
exact_has_any = exists cand where
    cand.title_norm == gold.name_norm AND cand.type == gold.type

if exact_unclaimed_idx is not None:
    return AlignResult(exact_unclaimed_idx, "exact")
if exact_has_any:
    return AlignResult(None, "exact_occupied")   # 不再进入 alias 阶段

# Phase 2: alias
for each a in gold.alias_norms (顺序):
    alias_unclaimed_idx = first cand.idx where
        cand.title_norm == a AND cand.type == gold.type
        AND cand.idx not in claimed
    if alias_unclaimed_idx is not None:
        return AlignResult(alias_unclaimed_idx, "alias")

alias_has_any = exists a in gold.alias_norms, exists cand where
    cand.title_norm == a AND cand.type == gold.type
if alias_has_any:
    return AlignResult(None, "alias_occupied")

return AlignResult(None, "none")
```

关键语义：

- **exact_occupied 会吞掉 alias 路径**：只要名字能精确对上某个已占用的 ext，就不再尝试 alias。这防止 gold A 因 gold B 抢了自己的正主，却顺势去抢另一条 gold C 的别名 ext。
- **真正的 `claimed.add(idx)` 只发生在 `align_sample` 内**：`_align_one` 是纯函数，只读 `claimed`。
- **候选与 alias 都按给定顺序遍历**：候选按 `idx` 升序，alias 按 `gold.alias_norms` 原顺序（规范化前的原 list 顺序经 `canonicalize_gold_aliases` 保留）。

## 4. 度量改造

### 4.1 audit_entity_recall

- 样本级：`len([r for r in results if r.matched_ext_idx is not None]) / len(gold_entities)`
- **0 分母**：`len(gold_entities) == 0` 时样本不计入；汇总级无可用样本时整体返回 `0.0`。

### 4.2 audit_entity_precision

- 样本级：`|{r.matched_ext_idx for r in results if r.matched_ext_idx is not None}| / len(ext_candidates)`
- **0 分母**：`len(ext_candidates) == 0` 时样本级返回 `0.0`；汇总级无可用样本时整体返回 `0.0`。

### 4.3 audit_relation_recall（全程 idx 驱动）

关系命中不再回退到字符串。实现：

```python
# Step A: 构建 (title_norm, type) -> idx 列表
title_type_to_idxs: dict[tuple[str, str], list[int]] = {}
for cand in ext_candidates:
    title_type_to_idxs.setdefault((cand.title_norm, cand.type), []).append(cand.idx)

# Step B: 把 extraction 关系扇出成 idx triple
# 关系本身不带端点 type，所以对 src/tgt 分别按 title_norm 扇出到所有可能的 ext idx
extracted_triples: set[tuple[int, str, int]] = set()
for rel in item.relationships:
    src_norm = _normalize_title(rel.source)
    tgt_norm = _normalize_title(rel.target)
    src_idxs = [i for (tn, _t), idxs in title_type_to_idxs.items()
                if tn == src_norm for i in idxs]
    tgt_idxs = [i for (tn, _t), idxs in title_type_to_idxs.items()
                if tn == tgt_norm for i in idxs]
    for s in src_idxs:
        for t in tgt_idxs:
            extracted_triples.add((s, rel.type, t))

# Step C: 命中判定
for g in entry.gold_relations:
    src = aligned[g.src_id].matched_ext_idx
    tgt = aligned[g.tgt_id].matched_ext_idx
    if src is None or tgt is None:
        continue
    if (src, g.type, tgt) in extracted_triples:
        hits += 1
```

效果：

- 同 sample 若有两个归一化同名但 type 不同的 ext，各有独立 idx 互不串扰。
- gold 通过严格类型对齐锁定某个 idx，关系命中只能用那个 idx 回查。
- extraction 关系 `rel.source` 名字同时命中多个 ext 时扇出为多条 idx triple，gold 对哪个 idx 对上就命中哪条，不会因同名退回字符串判定。

**0 分母**：`len(gold_relations) == 0` 时样本不计入；汇总级无可用样本时整体返回 `0.0`。

## 5. 调用端改造

### 5.1 scoring_audit.py

- 删除：`_extracted_aligns_to_gold`、`_align_gold_to_extracted`、`_entity_hit`、`SHORT_GOLD_GUARD_LEN`。
- 新增：`ExtCandidate` / `GoldEntity` / `MatchMode` / `AlignResult`、`canonicalize_gold_aliases`、`_build_ext_candidates(result)`、`_build_gold_entities(entry)`、`align_sample`。
- `compute_audit_entity_recall` / `compute_audit_entity_precision` / `compute_audit_relation_recall` 全部改为先调 `align_sample`，再消费结果。
- 对外公开函数签名不变（参数为 `results`, `audit_index`，返回 `float`）；内部增加 `_EMPTY_AUDIT = ({}, {})` 等常量仅供复用。

### 5.2 diagnose_step8.py

- 复用 `align_sample`，读 `AlignResult` 替换原 `aligned_src / aligned_tgt` 字符串。
- verdict 扩展一项 `align_collision`：当 `src` 或 `tgt` 的 `match_mode ∈ {"exact_occupied", "alias_occupied"}` 时打该标签；其余 verdict（`hit` / `type_mismatch` / `triple_not_in_ext` / `align_fail_src` / `align_fail_tgt` / `align_fail_both`）保留。
- 诊断表新增列：`match_mode (src / tgt)`，值从 `AlignResult.match_mode` 直接取。
- B.1 汇总表新增列 `align_collision`；B.2 明细表新增上面那列。
- 新的空样本列标签："—" 与原来保持一致。

### 5.3 不改的东西

- `scoring_metrics.py`：`DEFAULT_WEIGHTS`、`HARD_METRIC_KEYS`、`SOFT_METRIC_KEYS`、`GATE_THRESHOLD`、`compute_composite_*` 全部不动。
- `score_extraction_results.py`：无感。
- schema / gold / 候选 prompt：不动。

## 6. 可观测的行为变化

- 当前报告里大量 `triple_not_in_ext` 会重分类为 `align_fail_tgt` / `align_fail_src` / `align_collision`，**更如实反映"模型根本没抽出这个实体"**。
- `audit_entity_recall` / `audit_entity_precision` / `audit_relation_recall` 三条软指标预期**会下降**（之前虚高）。
- 三条 audit 指标在 soft 桶合计权重 0.10，`composite_score` 排名大概率不会被搅翻，`gate_passed` 不受影响。
- 诊断报告新增 `match_mode` 列和 `align_collision` 统计，后续若决定调 schema / prompt，可以直接基于 exact / alias 命中比例判断 LLM 产出名字与 gold 别名贴合度。

## 7. 测试策略

### 7.1 tests/test_scoring_audit.py

新增 / 改造用例（命名示意）：

- `test_align_sample_exact_hit_uses_type`：同名不同 type 时，不同 type 的 ext 不命中
- `test_align_sample_alias_hit`：gold.alias 规范化后等于 ext.title_norm 则命中 `alias`
- `test_align_sample_exact_occupied_marks_status`：两条 gold 抢同一 ext，后者得到 `exact_occupied`、`matched_ext_idx=None`
- `test_align_sample_alias_occupied_marks_status`：alias 路径下的同类冲突
- `test_align_sample_empty_alias_is_skipped`
- `test_align_sample_no_candidates_returns_all_none`
- `test_audit_entity_recall_empty_gold_returns_zero`
- `test_audit_entity_precision_empty_ext_returns_zero`
- `test_audit_relation_recall_empty_gold_relations_returns_zero`
- `test_audit_*_no_valid_samples_returns_zero`
- `test_audit_relation_recall_idx_disambiguation`：同 sample 内两个 ext `(title_norm="系统调用", type)` 不同 type，gold 指向其中一种类型，关系命中只能用对应 idx
- `test_audit_relation_recall_fanout_multi_matches`：extraction 关系端点名对应多个 ext，扇出后仍能命中

原本依赖子串命中的断言全部改为 strict 语义预期。

### 7.2 tests/test_diagnose_step8.py

- `test_diagnose_align_collision_classification`：两条 gold 抢同一 ext 时新增 `align_collision`
- `test_diagnose_match_mode_column_present`：明细表包含 `match_mode` 列，值为五值闭集之一
- `test_diagnose_b1_summary_contains_align_collision`：B.1 汇总表新增列

### 7.3 执行

```bash
cd graphrag_pipeline
python -m pytest tests/test_scoring_audit.py tests/test_diagnose_step8.py -v
python -m pytest tests/test_score_extraction_results.py -v   # 保证公共签名未回退
```

## 8. 风险与缓解

| 风险 | 缓解 |
|---|---|
| 现有 baseline 的 audit 指标下降，被误读为"流水线退化" | 在实施 PR 说明 + 后续诊断报告扉页注明指标口径变更；保留一个旧报告归档做对照 |
| `align_collision` 语义被使用方误当成"命中的一种" | `MatchMode` 用 `Literal` 收口；诊断表给 `align_collision` 独立列，不混入 hit 计数 |
| `title_type_to_idxs` 在极大 sample 下扇出膨胀 | extraction 单样本实体 / 关系量级小（数十量级），O(n²) 扇出可忽略；无需优化 |
| 别的脚本意外依赖被删的 `_align_gold_to_extracted` | 实施时用 `rg` 全仓搜一次引用；仅 `scoring_audit.py` 和 `diagnose_step8.py` 使用 |

## 9. 不在本次范围内

明确留作下一轮：

- schema 中 Assignment 对"第X章…习题"合并写法的 canonical_name 规则补全
- `前言` / `序章` / `目录` 等 book-part 是否需要独立 entity type
- 候选 prompt 在 `evaluated_by` 端点类型上的引导
- composite score 是否提升 audit 三项权重 / 设硬门槛
- gold 集标注是否需要对"习题"引入更清晰的 alias

本次只解决评测侧"度量本身是否可信"的问题。
