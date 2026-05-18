# Audit 对齐器严格化实施计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 把 audit 对齐器从"宽松子串匹配"改成"严格 exact/alias + 类型相等 + one-to-one 占用"，让三条 audit 软指标反映真实抽取命中；同步升级 `diagnose_step8.py` 报告维度。

**Architecture:** 全部改动限定在评测侧（`scoring_audit.py` / `diagnose_step8.py` + 相应测试）。新增 `ExtCandidate` / `GoldEntity` / `AlignResult` / `MatchMode` 四个数据类型、一个 sample 级 `align_sample()` 主函数、以及 idx 驱动的关系命中路径。公开函数签名保持不变，`score_extraction_results.py` 无感。schema / prompt / gold / composite 权重 / gate 阈值均不动。

**Tech Stack:** Python 3.10+ stdlib，`dataclasses` / `typing.Literal`，`unittest`。依赖 `scoring_metrics._normalize_title` 作为唯一规范化入口。

**参考设计文档：** [docs/superpowers/specs/2026-04-19-audit-aligner-strict-design.md](../specs/2026-04-19-audit-aligner-strict-design.md)

---

## 预检结果（2026-04-19 执行）

全仓搜索 `_align_gold_to_extracted` / `_entity_hit` / `SHORT_GOLD_GUARD_LEN` / `_extracted_aligns_to_gold`：

- 生产调用点：仅 `graphrag_pipeline/scripts/diagnose_step8.py:27,117,118`（已纳入计划）。
- 公共 API 消费者：`score_extraction_results.py:36-41` 与 `tests/test_scoring_audit.py:19-24` 仅依赖 `compute_audit_entity_precision` / `compute_audit_entity_recall` / `compute_audit_relation_recall` / `load_audit_index` 四个公共符号，本计划保留其签名。
- 文档/历史计划中的引用为静态记录，非运行时依赖。

结论：删除集合不存在隐藏调用。

## 文件结构

- Modify: `graphrag_pipeline/scripts/scoring_audit.py` — 完整重写内部对齐与度量
- Modify: `graphrag_pipeline/scripts/diagnose_step8.py` — 切换到 `align_sample`，新增 `align_collision` verdict 与 `match_mode` 列
- Modify: `graphrag_pipeline/tests/test_scoring_audit.py` — 新增严格语义用例；删除/重写基于子串的旧用例
- Modify: `graphrag_pipeline/tests/test_diagnose_step8.py` — 新增 collision / match_mode 用例

---

## Task 1: 引入数据类型与规范化辅助

**Files:**
- Modify: `graphrag_pipeline/scripts/scoring_audit.py`（顶部新增类型与 `canonicalize_gold_aliases`）
- Test: `graphrag_pipeline/tests/test_scoring_audit.py`（新增一段测试）

- [ ] **Step 1: 写失败测试：验证 `canonicalize_gold_aliases` 行为**

在 `tests/test_scoring_audit.py` 文件末尾（`if __name__ == "__main__"` 之前）追加：

```python
class TestCanonicalizeGoldAliases(unittest.TestCase):
    def test_filters_empty_strings_and_preserves_order(self):
        from scoring_audit import canonicalize_gold_aliases
        self.assertEqual(
            canonicalize_gold_aliases(["习题", "", "  ", "IPC"]),
            ("习题", "IPC"),
        )

    def test_normalizes_via_shared_normalizer(self):
        from scoring_audit import canonicalize_gold_aliases
        # 包含半角空格与标点，应被 _normalize_title 清理
        self.assertEqual(
            canonicalize_gold_aliases(["WIMP 技术"]),
            ("wimp技术",),
        )

    def test_empty_input_returns_empty_tuple(self):
        from scoring_audit import canonicalize_gold_aliases
        self.assertEqual(canonicalize_gold_aliases([]), ())
```

- [ ] **Step 2: 跑测试验证失败**

Run: `cd graphrag_pipeline && python -m pytest tests/test_scoring_audit.py::TestCanonicalizeGoldAliases -v`
Expected: FAIL，`ImportError: cannot import name 'canonicalize_gold_aliases'`

- [ ] **Step 3: 在 scoring_audit.py 顶部加入数据类型与辅助**

把 `scoring_audit.py` 开头的 module docstring 改成新版并在 import 之后加入类型定义。替换 docstring 与整个 imports 段，目标形如：

```python
#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""audit 校准集对齐工具（严格版）。

只负责：读取 audit 集，构建索引，计算实体/关系软指标。

对齐规则（2026-04-19 起）：
- 实体：ext.title_norm == gold.name_norm 且 ext.type == gold.type（exact）；
       若 exact 完全无匹配，再考虑 gold.alias 走严格 alias 匹配。
- 同 sample 内 one-to-one 占用；当目标 ext 已被更早 gold 占用时返回
  exact_occupied / alias_occupied，matched_ext_idx=None，不做子串回退。
- 关系：gold 两端都拿到 matched_ext_idx 后，用 (src_idx, rtype, tgt_idx)
  去 extraction 关系扇出得到的 idx triple 集里查命中；不再回退字符串。
"""

from __future__ import annotations

import json
from dataclasses import dataclass
from pathlib import Path
from typing import Literal, Sequence

from extraction_schema import StructuredExtractionResult
from scoring_metrics import _normalize_title


MatchMode = Literal[
    "exact",
    "alias",
    "exact_occupied",
    "alias_occupied",
    "none",
]


@dataclass(frozen=True)
class ExtCandidate:
    idx: int
    title_norm: str
    type: str


@dataclass(frozen=True)
class GoldEntity:
    gold_id: str
    name_norm: str
    alias_norms: tuple[str, ...]
    type: str


@dataclass(frozen=True)
class AlignResult:
    matched_ext_idx: int | None
    match_mode: MatchMode


def canonicalize_gold_aliases(aliases: Sequence[str]) -> tuple[str, ...]:
    """按 _normalize_title 规范化并丢掉空串，保留原顺序。"""
    result: list[str] = []
    for a in aliases or ():
        norm = _normalize_title(a)
        if norm:
            result.append(norm)
    return tuple(result)
```

保留 `AuditEntry` / `load_audit_index` 两个已有的公开符号不变（位置可以挪到类型定义之后）。旧的私有函数 `_extracted_aligns_to_gold` / `_entity_hit` / `_align_gold_to_extracted` 与常量 `SHORT_GOLD_GUARD_LEN` **暂时保留**（后续 Task 8 删）。

- [ ] **Step 4: 跑测试验证通过**

Run: `cd graphrag_pipeline && python -m pytest tests/test_scoring_audit.py::TestCanonicalizeGoldAliases -v`
Expected: 3 passed

- [ ] **Step 5: 跑全量 audit 测试确认无回退**

Run: `cd graphrag_pipeline && python -m pytest tests/test_scoring_audit.py -v`
Expected: 全部现存用例保持原状通过（新类型尚未被使用，只增不改）

- [ ] **Step 6: 提交**

```bash
git add graphrag_pipeline/scripts/scoring_audit.py graphrag_pipeline/tests/test_scoring_audit.py
git commit -m "refactor(scoring_audit): 引入 ExtCandidate/GoldEntity/AlignResult/MatchMode 与 canonicalize_gold_aliases 辅助"
```

---

## Task 2: 实现 `_align_one` 纯函数

**Files:**
- Modify: `graphrag_pipeline/scripts/scoring_audit.py`
- Test: `graphrag_pipeline/tests/test_scoring_audit.py`

- [ ] **Step 1: 写失败测试：exact / alias / *_occupied / none / tie-break**

在 `tests/test_scoring_audit.py` 末尾（Task 1 新增的类之后）追加：

```python
class TestAlignOne(unittest.TestCase):
    def _gold(self, *, name_norm="a", aliases=(), type_="T"):
        from scoring_audit import GoldEntity
        return GoldEntity(gold_id="g", name_norm=name_norm,
                          alias_norms=aliases, type=type_)

    def _cand(self, idx, title_norm, type_):
        from scoring_audit import ExtCandidate
        return ExtCandidate(idx=idx, title_norm=title_norm, type=type_)

    def test_exact_hit_with_matching_type(self):
        from scoring_audit import _align_one
        gold = self._gold(name_norm="进程", type_="Concept")
        cands = [self._cand(0, "进程", "Concept")]
        r = _align_one(gold, cands, claimed=set())
        self.assertEqual(r.matched_ext_idx, 0)
        self.assertEqual(r.match_mode, "exact")

    def test_exact_miss_when_type_differs(self):
        from scoring_audit import _align_one
        gold = self._gold(name_norm="进程", type_="Concept")
        cands = [self._cand(0, "进程", "Term")]
        r = _align_one(gold, cands, claimed=set())
        self.assertIsNone(r.matched_ext_idx)
        self.assertEqual(r.match_mode, "none")

    def test_alias_hit_when_exact_fails(self):
        from scoring_audit import _align_one
        gold = self._gold(name_norm="第九章操作系统接口习题",
                          aliases=("习题",), type_="Assignment")
        cands = [self._cand(0, "习题", "Assignment")]
        r = _align_one(gold, cands, claimed=set())
        self.assertEqual(r.matched_ext_idx, 0)
        self.assertEqual(r.match_mode, "alias")

    def test_alias_requires_type_match(self):
        from scoring_audit import _align_one
        gold = self._gold(name_norm="X", aliases=("习题",), type_="Assignment")
        cands = [self._cand(0, "习题", "Section")]
        r = _align_one(gold, cands, claimed=set())
        self.assertIsNone(r.matched_ext_idx)
        self.assertEqual(r.match_mode, "none")

    def test_exact_occupied_swallows_alias_phase(self):
        from scoring_audit import _align_one
        # 只有一个 exact-matching ext，被 claim；alias 也能匹配但应被吞掉
        gold = self._gold(name_norm="进程", aliases=("其它别名",), type_="Concept")
        cands = [
            self._cand(0, "进程", "Concept"),
            self._cand(1, "其它别名", "Concept"),
        ]
        r = _align_one(gold, cands, claimed={0})
        self.assertIsNone(r.matched_ext_idx)
        self.assertEqual(r.match_mode, "exact_occupied")

    def test_alias_occupied_when_no_exact_exists(self):
        from scoring_audit import _align_one
        gold = self._gold(name_norm="没这个", aliases=("习题",), type_="Assignment")
        cands = [self._cand(0, "习题", "Assignment")]
        r = _align_one(gold, cands, claimed={0})
        self.assertIsNone(r.matched_ext_idx)
        self.assertEqual(r.match_mode, "alias_occupied")

    def test_candidate_iteration_uses_idx_asc_tie_break(self):
        from scoring_audit import _align_one
        # 两个相同 (title_norm, type) ext，idx=2 在列表前面，仍应挑 idx=1
        gold = self._gold(name_norm="进程", type_="Concept")
        cands = [
            self._cand(2, "进程", "Concept"),
            self._cand(1, "进程", "Concept"),
        ]
        r = _align_one(gold, cands, claimed=set())
        self.assertEqual(r.matched_ext_idx, 1)
        self.assertEqual(r.match_mode, "exact")

    def test_empty_candidates_returns_none(self):
        from scoring_audit import _align_one
        gold = self._gold(name_norm="进程", type_="Concept")
        r = _align_one(gold, [], claimed=set())
        self.assertIsNone(r.matched_ext_idx)
        self.assertEqual(r.match_mode, "none")

    def test_empty_alias_list_is_skipped(self):
        from scoring_audit import _align_one
        gold = self._gold(name_norm="不存在", aliases=(), type_="Concept")
        cands = [self._cand(0, "进程", "Concept")]
        r = _align_one(gold, cands, claimed=set())
        self.assertIsNone(r.matched_ext_idx)
        self.assertEqual(r.match_mode, "none")

    def test_alias_iteration_follows_input_order(self):
        from scoring_audit import _align_one
        # 两个 alias 都能命中，应挑前一个
        gold = self._gold(name_norm="X", aliases=("a", "b"), type_="T")
        cands = [
            self._cand(0, "b", "T"),
            self._cand(1, "a", "T"),
        ]
        r = _align_one(gold, cands, claimed=set())
        self.assertEqual(r.matched_ext_idx, 1)  # "a" 命中，idx=1
        self.assertEqual(r.match_mode, "alias")
```

- [ ] **Step 2: 跑测试验证失败**

Run: `cd graphrag_pipeline && python -m pytest tests/test_scoring_audit.py::TestAlignOne -v`
Expected: FAIL，`ImportError: cannot import name '_align_one'`

- [ ] **Step 3: 实现 `_align_one`**

在 `scoring_audit.py` 中 `canonicalize_gold_aliases` 之后加入：

```python
def _align_one(
    gold: GoldEntity,
    candidates: Sequence[ExtCandidate],
    claimed: set[int],
) -> AlignResult:
    """把一条 gold 对齐到 ext 候选。纯函数，只读 `claimed`。

    规则：
      Phase 1 exact：在按 idx 升序排序的候选中，找第一条 title_norm == gold.name_norm
                     且 type == gold.type 的；若未占用则命中；若存在该类候选但全被
                     占用，返回 exact_occupied 并吞掉 Phase 2。
      Phase 2 alias：当且仅当 Phase 1 完全无匹配时执行；按 gold.alias_norms 顺序
                     扫候选，找第一条 title_norm == alias 且 type == gold.type；
                     若未占用则命中；若存在该类候选但全被占用，返回 alias_occupied。
      其余：none。
    """
    sorted_cands = sorted(candidates, key=lambda c: c.idx)

    # Phase 1: exact
    exact_unclaimed: int | None = None
    exact_has_any = False
    for cand in sorted_cands:
        if cand.title_norm == gold.name_norm and cand.type == gold.type:
            exact_has_any = True
            if cand.idx not in claimed:
                exact_unclaimed = cand.idx
                break
    if exact_unclaimed is not None:
        return AlignResult(exact_unclaimed, "exact")
    if exact_has_any:
        return AlignResult(None, "exact_occupied")

    # Phase 2: alias
    alias_has_any = False
    for alias in gold.alias_norms:
        for cand in sorted_cands:
            if cand.title_norm == alias and cand.type == gold.type:
                alias_has_any = True
                if cand.idx not in claimed:
                    return AlignResult(cand.idx, "alias")
    if alias_has_any:
        return AlignResult(None, "alias_occupied")

    return AlignResult(None, "none")
```

- [ ] **Step 4: 跑测试验证通过**

Run: `cd graphrag_pipeline && python -m pytest tests/test_scoring_audit.py::TestAlignOne -v`
Expected: 10 passed

- [ ] **Step 5: 提交**

```bash
git add graphrag_pipeline/scripts/scoring_audit.py graphrag_pipeline/tests/test_scoring_audit.py
git commit -m "feat(scoring_audit): 实现 _align_one 严格对齐单元（exact → alias → *_occupied → none）"
```

---

## Task 3: 实现 `align_sample` 主函数

**Files:**
- Modify: `graphrag_pipeline/scripts/scoring_audit.py`
- Test: `graphrag_pipeline/tests/test_scoring_audit.py`

- [ ] **Step 1: 写失败测试：gold_id 字典序 + claim 传播**

在 `tests/test_scoring_audit.py` 末尾追加：

```python
class TestAlignSample(unittest.TestCase):
    def _gold(self, gid, name_norm, *, aliases=(), type_="T"):
        from scoring_audit import GoldEntity
        return GoldEntity(gold_id=gid, name_norm=name_norm,
                          alias_norms=aliases, type=type_)

    def _cand(self, idx, title_norm, type_):
        from scoring_audit import ExtCandidate
        return ExtCandidate(idx=idx, title_norm=title_norm, type=type_)

    def test_gold_iteration_is_gold_id_asc(self):
        """两条 gold 抢同一 ext，gold_id 字典序靠前者赢，另一条得 exact_occupied。"""
        from scoring_audit import align_sample
        # 故意乱序传入，实际应按 gold_id 升序
        g_z = self._gold("z", "进程", type_="Concept")
        g_a = self._gold("a", "进程", type_="Concept")
        cands = [self._cand(0, "进程", "Concept")]
        result = align_sample([g_z, g_a], cands)
        self.assertEqual(result["a"].matched_ext_idx, 0)
        self.assertEqual(result["a"].match_mode, "exact")
        self.assertIsNone(result["z"].matched_ext_idx)
        self.assertEqual(result["z"].match_mode, "exact_occupied")

    def test_one_to_one_claim_across_golds(self):
        from scoring_audit import align_sample
        g1 = self._gold("g1", "a", type_="T")
        g2 = self._gold("g2", "b", type_="T")
        cands = [
            self._cand(0, "a", "T"),
            self._cand(1, "b", "T"),
        ]
        result = align_sample([g1, g2], cands)
        self.assertEqual(result["g1"].matched_ext_idx, 0)
        self.assertEqual(result["g2"].matched_ext_idx, 1)
        self.assertEqual(result["g1"].match_mode, "exact")
        self.assertEqual(result["g2"].match_mode, "exact")

    def test_chapter_vs_assignment_no_cross_type_capture(self):
        """同前缀的 Chapter gold 与 Assignment gold 不应互相抢 ext。"""
        from scoring_audit import align_sample
        g_chap = self._gold("g1", "第九章操作系统接口", type_="Chapter")
        g_asn = self._gold("g2", "第九章操作系统接口习题",
                           aliases=("习题",), type_="Assignment")
        cands = [
            self._cand(0, "第九章操作系统接口", "Chapter"),
            # Assignment 侧完全没被抽出
        ]
        result = align_sample([g_chap, g_asn], cands)
        self.assertEqual(result["g1"].matched_ext_idx, 0)
        self.assertEqual(result["g1"].match_mode, "exact")
        self.assertIsNone(result["g2"].matched_ext_idx)
        self.assertEqual(result["g2"].match_mode, "none")

    def test_empty_gold_returns_empty_map(self):
        from scoring_audit import align_sample
        cands = [self._cand(0, "a", "T")]
        self.assertEqual(align_sample([], cands), {})

    def test_empty_candidates_all_none(self):
        from scoring_audit import align_sample
        g1 = self._gold("g1", "a")
        g2 = self._gold("g2", "b")
        result = align_sample([g1, g2], [])
        self.assertEqual(result["g1"].match_mode, "none")
        self.assertEqual(result["g2"].match_mode, "none")
        self.assertIsNone(result["g1"].matched_ext_idx)
        self.assertIsNone(result["g2"].matched_ext_idx)
```

- [ ] **Step 2: 跑测试验证失败**

Run: `cd graphrag_pipeline && python -m pytest tests/test_scoring_audit.py::TestAlignSample -v`
Expected: FAIL，`ImportError: cannot import name 'align_sample'`

- [ ] **Step 3: 实现 `align_sample`**

在 `scoring_audit.py` 中 `_align_one` 之后加入：

```python
def align_sample(
    gold_entities: Sequence[GoldEntity],
    ext_candidates: Sequence[ExtCandidate],
) -> dict[str, AlignResult]:
    """把一个 sample 的 gold_entities 对齐到 ext_candidates。

    规则：
      - 对每条 gold，按优先级 exact → alias 搜索（见 _align_one）。
      - ext_candidates 使用 ExtCandidate.idx 作为占用键；同一 sample 内 one-to-one。
      - gold 遍历顺序固定为 gold_id 字典序（确定性 tie-break，评测稳定优先于召回最大）；
        候选遍历顺序固定为 ExtCandidate.idx 升序。
      - 无 ext_candidates 时所有 gold 返回 none。

    返回：{gold_id: AlignResult}
    """
    claimed: set[int] = set()
    result: dict[str, AlignResult] = {}
    for gold in sorted(gold_entities, key=lambda g: g.gold_id):
        aligned = _align_one(gold, ext_candidates, claimed)
        if aligned.matched_ext_idx is not None:
            claimed.add(aligned.matched_ext_idx)
        result[gold.gold_id] = aligned
    return result
```

- [ ] **Step 4: 跑测试验证通过**

Run: `cd graphrag_pipeline && python -m pytest tests/test_scoring_audit.py::TestAlignSample -v`
Expected: 5 passed

- [ ] **Step 5: 提交**

```bash
git add graphrag_pipeline/scripts/scoring_audit.py graphrag_pipeline/tests/test_scoring_audit.py
git commit -m "feat(scoring_audit): 实现 align_sample，按 gold_id 字典序做 one-to-one 占用"
```

---

## Task 4: 构建 ExtCandidate / GoldEntity 的输入适配器

**Files:**
- Modify: `graphrag_pipeline/scripts/scoring_audit.py`
- Test: `graphrag_pipeline/tests/test_scoring_audit.py`

- [ ] **Step 1: 写失败测试**

在 `tests/test_scoring_audit.py` 末尾追加：

```python
class TestBuildAdapters(unittest.TestCase):
    def test_build_ext_candidates_preserves_order_and_idx(self):
        from scoring_audit import _build_ext_candidates
        result = _success_result(
            "s1",
            [
                {"id": "e1", "title": "操作系统", "type": "Course"},
                {"id": "e2", "title": "第一章 引论", "type": "Chapter"},
                {"id": "e3", "title": "  进程  ", "type": "Concept"},
            ],
            [],
        )
        cands = _build_ext_candidates(result)
        self.assertEqual([c.idx for c in cands], [0, 1, 2])
        self.assertEqual([c.title_norm for c in cands],
                         ["操作系统", "第一章引论", "进程"])
        self.assertEqual([c.type for c in cands],
                         ["Course", "Chapter", "Concept"])

    def test_build_ext_candidates_skips_empty_titles(self):
        from scoring_audit import _build_ext_candidates
        result = _success_result(
            "s1",
            [
                {"id": "e1", "title": "", "type": "Course"},
                {"id": "e2", "title": "   ", "type": "Chapter"},
                {"id": "e3", "title": "进程", "type": "Concept"},
            ],
            [],
        )
        cands = _build_ext_candidates(result)
        # 空 title 被跳过，但保留的 idx 应对应原 entities 下标
        self.assertEqual([c.idx for c in cands], [2])
        self.assertEqual(cands[0].title_norm, "进程")

    def test_build_gold_entities_normalizes_and_canonicalizes(self):
        from scoring_audit import _build_gold_entities, AuditEntry
        entry = AuditEntry(
            gold_entities=[
                {"entity_id": "ent-1", "name": "WIMP 技术",
                 "type": "Concept", "alias": ["WIMP", ""]},
                {"entity_id": "ent-2", "name": "第九章 操作系统接口习题",
                 "type": "Assignment", "alias": ["习题"]},
            ],
            gold_relations=[],
        )
        golds = _build_gold_entities(entry)
        self.assertEqual(golds[0].gold_id, "ent-1")
        self.assertEqual(golds[0].name_norm, "wimp技术")
        self.assertEqual(golds[0].alias_norms, ("wimp",))
        self.assertEqual(golds[0].type, "Concept")
        self.assertEqual(golds[1].gold_id, "ent-2")
        self.assertEqual(golds[1].name_norm, "第九章操作系统接口习题")
        self.assertEqual(golds[1].alias_norms, ("习题",))

    def test_build_gold_entities_missing_alias_key(self):
        """部分 audit 条目没有 alias 字段，应视为空别名。"""
        from scoring_audit import _build_gold_entities, AuditEntry
        entry = AuditEntry(
            gold_entities=[
                {"entity_id": "ent-1", "name": "进程", "type": "Concept"},
            ],
            gold_relations=[],
        )
        golds = _build_gold_entities(entry)
        self.assertEqual(golds[0].alias_norms, ())

    def test_build_gold_entities_skips_empty_name(self):
        from scoring_audit import _build_gold_entities, AuditEntry
        entry = AuditEntry(
            gold_entities=[
                {"entity_id": "ent-1", "name": "", "type": "Concept"},
                {"entity_id": "ent-2", "name": "进程", "type": "Concept"},
            ],
            gold_relations=[],
        )
        golds = _build_gold_entities(entry)
        self.assertEqual([g.gold_id for g in golds], ["ent-2"])
```

- [ ] **Step 2: 跑测试验证失败**

Run: `cd graphrag_pipeline && python -m pytest tests/test_scoring_audit.py::TestBuildAdapters -v`
Expected: FAIL，`ImportError: cannot import name '_build_ext_candidates'`

- [ ] **Step 3: 实现适配器**

在 `scoring_audit.py` 中 `align_sample` 之后加入：

```python
def _build_ext_candidates(
    item: StructuredExtractionResult,
) -> list[ExtCandidate]:
    """从 ExtractionEntity 列表构建 ExtCandidate 列表。

    idx 对应 item.entities 的原始下标；title_norm 为空的条目被跳过但
    其它条目的 idx 保持原位，保证与 extraction 关系引用的 title 可对齐。
    """
    out: list[ExtCandidate] = []
    for idx, ent in enumerate(item.entities):
        tn = _normalize_title(ent.title)
        if not tn:
            continue
        out.append(ExtCandidate(idx=idx, title_norm=tn, type=ent.type))
    return out


def _build_gold_entities(entry: AuditEntry) -> list[GoldEntity]:
    """从 AuditEntry.gold_entities 构建 GoldEntity 列表。

    跳过 name 归一化后为空的条目；alias 缺失时视为空；alias 经
    canonicalize_gold_aliases 过滤空串并规范化。
    """
    out: list[GoldEntity] = []
    for g in entry.gold_entities:
        name_norm = _normalize_title(g.get("name", ""))
        if not name_norm:
            continue
        out.append(GoldEntity(
            gold_id=str(g.get("entity_id", "") or ""),
            name_norm=name_norm,
            alias_norms=canonicalize_gold_aliases(g.get("alias") or ()),
            type=str(g.get("type", "") or ""),
        ))
    return out
```

- [ ] **Step 4: 跑测试验证通过**

Run: `cd graphrag_pipeline && python -m pytest tests/test_scoring_audit.py::TestBuildAdapters -v`
Expected: 5 passed

- [ ] **Step 5: 提交**

```bash
git add graphrag_pipeline/scripts/scoring_audit.py graphrag_pipeline/tests/test_scoring_audit.py
git commit -m "feat(scoring_audit): 实现 _build_ext_candidates / _build_gold_entities 输入适配器"
```

---

## Task 5: 重写 `compute_audit_entity_recall`

**Files:**
- Modify: `graphrag_pipeline/scripts/scoring_audit.py:compute_audit_entity_recall`
- Test: `graphrag_pipeline/tests/test_scoring_audit.py`

- [ ] **Step 1: 写失败测试（新语义 + 零分母语义）**

在 `tests/test_scoring_audit.py` 末尾追加：

```python
class TestEntityRecallStrict(unittest.TestCase):
    def _audit(self, payload, name="audit.json"):
        self.tmpdir = getattr(self, "tmpdir", tempfile.TemporaryDirectory())
        p = Path(self.tmpdir.name) / name
        p.write_text(json.dumps(payload, ensure_ascii=False), encoding="utf-8")
        return load_audit_index(p)

    def tearDown(self):
        if hasattr(self, "tmpdir"):
            self.tmpdir.cleanup()
            del self.tmpdir

    def test_recall_requires_type_equality(self):
        idx = self._audit({"audit_samples": [{
            "source_sample_id": "s1",
            "gold_entities": [
                {"entity_id": "g1", "name": "进程", "type": "Concept"},
            ],
            "gold_relations": [],
        }]})
        # ext 名对但 type 不对，recall 应为 0
        results = [_success_result(
            "s1",
            [{"id": "e1", "title": "进程", "type": "Term"}],
            [],
        )]
        self.assertEqual(compute_audit_entity_recall(results, idx), 0.0)

    def test_recall_uses_alias_when_exact_missing(self):
        idx = self._audit({"audit_samples": [{
            "source_sample_id": "s1",
            "gold_entities": [
                {"entity_id": "g1", "name": "第九章操作系统接口习题",
                 "type": "Assignment", "alias": ["习题"]},
            ],
            "gold_relations": [],
        }]})
        results = [_success_result(
            "s1",
            [{"id": "e1", "title": "习题", "type": "Assignment"}],
            [],
        )]
        self.assertEqual(compute_audit_entity_recall(results, idx), 1.0)

    def test_recall_long_gold_no_substring_fallback(self):
        """长 gold 再也不走双向子串；ext 必须精确或 alias 命中。"""
        idx = self._audit({"audit_samples": [{
            "source_sample_id": "s1",
            "gold_entities": [
                {"entity_id": "g1", "name": "第一章 引论", "type": "Chapter"},
            ],
            "gold_relations": [],
        }]})
        results = [_success_result(
            "s1",
            [{"id": "e1", "title": "第一章 引论 概述", "type": "Chapter"}],
            [],
        )]
        self.assertEqual(compute_audit_entity_recall(results, idx), 0.0)

    def test_recall_empty_gold_sample_excluded(self):
        idx = self._audit({"audit_samples": [{
            "source_sample_id": "s1",
            "gold_entities": [],
            "gold_relations": [],
        }]})
        results = [_success_result("s1", [], [])]
        self.assertEqual(compute_audit_entity_recall(results, idx), 0.0)

    def test_recall_no_valid_samples_returns_zero(self):
        idx = self._audit({"audit_samples": []})
        results = [_success_result("unrelated", [], [])]
        self.assertEqual(compute_audit_entity_recall(results, idx), 0.0)
```

同时删除以下已过时的测试用例（它们断言旧的子串/tie-break 行为，新规则下会永远失败）：

- `TestShortGoldEntityGuard.test_long_gold_keeps_substring_behavior`（整个方法删除；其所在类可保留其余两个用例）
- `TestAuditRelationAlignment.test_relation_hit_via_shorter_extracted_alignment`（方法删除）
- `TestAuditRelationAlignment.test_relation_hit_via_longer_extracted_alignment`（方法删除）
- `TestAuditRelationAlignment.test_alignment_ambiguity_picks_shortest_then_earliest`（方法删除；tie-break 已在 Task 2 `test_candidate_iteration_uses_idx_asc_tie_break` 覆盖）
- `TestAuditRelationAlignment.test_ambiguity_ranking_follows_ext_order_when_same_length`（方法删除）

还要更新：
- `TestShortGoldEntityGuard` class docstring 从 `"""Gold 归一化长度 < 4 时，必须精确相等才算命中..."""` 改成 `"""严格规则下短 gold 与长 gold 都只走 exact/alias；派生词不算对齐。"""`
- `TestAuditRelationAlignment` class docstring 从 `"""Step 2: compute_audit_relation_recall 通过实体先对齐（双向子串 + 确定性歧义解）容忍名称漂移。"""` 改成 `"""compute_audit_relation_recall：两端 gold 须严格对齐，且关系三元组按 idx 精确命中。"""`

- [ ] **Step 2: 跑测试验证失败**

Run: `cd graphrag_pipeline && python -m pytest tests/test_scoring_audit.py::TestEntityRecallStrict -v`
Expected: 至少 `test_recall_requires_type_equality` / `test_recall_uses_alias_when_exact_missing` / `test_recall_long_gold_no_substring_fallback` FAIL（旧实现用子串 + 不看类型）

- [ ] **Step 3: 重写 `compute_audit_entity_recall`**

把 `scoring_audit.py` 里的 `compute_audit_entity_recall` 整体替换为：

```python
def compute_audit_entity_recall(
    results: Sequence[StructuredExtractionResult],
    audit_index: dict[str, AuditEntry],
) -> float:
    """每个成功样本：命中 gold 数 / gold 总数，按样本平均。

    零分母规则：
      - 样本级：len(gold_entities) == 0 时样本不计入。
      - 汇总级：无可用样本时整体返回 0.0。
    """
    recalls: list[float] = []
    for item in results:
        if item.status != "success":
            continue
        entry = audit_index.get(item.sample_id)
        if entry is None or not entry.gold_entities:
            continue
        golds = _build_gold_entities(entry)
        if not golds:
            continue
        cands = _build_ext_candidates(item)
        aligned = align_sample(golds, cands)
        hits = sum(1 for r in aligned.values() if r.matched_ext_idx is not None)
        recalls.append(hits / len(golds))
    if not recalls:
        return 0.0
    return sum(recalls) / len(recalls)
```

- [ ] **Step 4: 跑全量 audit 测试**

Run: `cd graphrag_pipeline && python -m pytest tests/test_scoring_audit.py -v`
Expected: 所有新 / 保留用例通过；已删除用例不再出现

- [ ] **Step 5: 提交**

```bash
git add graphrag_pipeline/scripts/scoring_audit.py graphrag_pipeline/tests/test_scoring_audit.py
git commit -m "refactor(scoring_audit): compute_audit_entity_recall 改为严格对齐 + 零分母语义"
```

---

## Task 6: 重写 `compute_audit_entity_precision`

**Files:**
- Modify: `graphrag_pipeline/scripts/scoring_audit.py:compute_audit_entity_precision`
- Test: `graphrag_pipeline/tests/test_scoring_audit.py`

- [ ] **Step 1: 写失败测试**

在 `tests/test_scoring_audit.py` 末尾追加：

```python
class TestEntityPrecisionStrict(unittest.TestCase):
    def _audit(self, payload, name="audit.json"):
        self.tmpdir = getattr(self, "tmpdir", tempfile.TemporaryDirectory())
        p = Path(self.tmpdir.name) / name
        p.write_text(json.dumps(payload, ensure_ascii=False), encoding="utf-8")
        return load_audit_index(p)

    def tearDown(self):
        if hasattr(self, "tmpdir"):
            self.tmpdir.cleanup()
            del self.tmpdir

    def test_precision_counts_unique_matched_ext(self):
        """两条 gold 都对齐同一条 ext 是不可能的（one-to-one），但 extracted 超集时
        未被任何 gold 占用的 ext 不计入 precision 分子。"""
        idx = self._audit({"audit_samples": [{
            "source_sample_id": "s1",
            "gold_entities": [
                {"entity_id": "g1", "name": "进程", "type": "Concept"},
            ],
            "gold_relations": [],
        }]})
        results = [_success_result(
            "s1",
            [
                {"id": "e1", "title": "进程", "type": "Concept"},
                {"id": "e2", "title": "噪声", "type": "Concept"},
            ],
            [],
        )]
        # 1 条命中 / 2 条总抽取 = 0.5
        self.assertEqual(compute_audit_entity_precision(results, idx), 0.5)

    def test_precision_empty_ext_returns_zero(self):
        idx = self._audit({"audit_samples": [{
            "source_sample_id": "s1",
            "gold_entities": [
                {"entity_id": "g1", "name": "进程", "type": "Concept"},
            ],
            "gold_relations": [],
        }]})
        results = [_success_result("s1", [], [])]
        self.assertEqual(compute_audit_entity_precision(results, idx), 0.0)

    def test_precision_no_valid_samples_returns_zero(self):
        idx = self._audit({"audit_samples": []})
        results = [_success_result("unrelated", [], [])]
        self.assertEqual(compute_audit_entity_precision(results, idx), 0.0)
```

同时删除旧 `TestAuditEntityPrecision.test_precision_short_gold_symmetric_guard`（该用例基于"gold 短词 + ext 派生词"场景断言返回 0.0；在新规则下它仍会返回 0.0，但触发路径是"类型或精确名不符"而非"短 gold 守卫"，语义已偏移）。保留其它 precision 用例。

- [ ] **Step 2: 跑测试验证失败**

Run: `cd graphrag_pipeline && python -m pytest tests/test_scoring_audit.py::TestEntityPrecisionStrict -v`
Expected: 至少 `test_precision_counts_unique_matched_ext` 结果与旧实现不一致（旧实现用子串，`噪声` 不会被对上，但类型判定路径不同会影响边界条件）

- [ ] **Step 3: 重写 `compute_audit_entity_precision`**

替换实现：

```python
def compute_audit_entity_precision(
    results: Sequence[StructuredExtractionResult],
    audit_index: dict[str, AuditEntry],
) -> float:
    """每个成功样本：命中 gold 的 ext 去重后 / ext 总数，按样本平均。

    零分母规则：
      - 样本级：len(ext_candidates) == 0 时样本返回 0.0。
      - 汇总级：无可用样本时整体返回 0.0。
    """
    precisions: list[float] = []
    for item in results:
        if item.status != "success":
            continue
        entry = audit_index.get(item.sample_id)
        if entry is None or not entry.gold_entities:
            continue
        golds = _build_gold_entities(entry)
        if not golds:
            continue
        cands = _build_ext_candidates(item)
        if not cands:
            precisions.append(0.0)
            continue
        aligned = align_sample(golds, cands)
        matched_ext = {r.matched_ext_idx for r in aligned.values()
                       if r.matched_ext_idx is not None}
        precisions.append(len(matched_ext) / len(cands))
    if not precisions:
        return 0.0
    return sum(precisions) / len(precisions)
```

- [ ] **Step 4: 跑测试验证通过**

Run: `cd graphrag_pipeline && python -m pytest tests/test_scoring_audit.py -v`
Expected: 所有 audit 测试通过

- [ ] **Step 5: 提交**

```bash
git add graphrag_pipeline/scripts/scoring_audit.py graphrag_pipeline/tests/test_scoring_audit.py
git commit -m "refactor(scoring_audit): compute_audit_entity_precision 改为严格对齐 + 零分母语义"
```

---

## Task 7: 重写 `compute_audit_relation_recall`（idx 驱动）

**Files:**
- Modify: `graphrag_pipeline/scripts/scoring_audit.py:compute_audit_relation_recall`
- Test: `graphrag_pipeline/tests/test_scoring_audit.py`

- [ ] **Step 1: 写失败测试**

在 `tests/test_scoring_audit.py` 末尾追加：

```python
class TestRelationRecallIdxDriven(unittest.TestCase):
    def _audit(self, payload, name="audit.json"):
        self.tmpdir = getattr(self, "tmpdir", tempfile.TemporaryDirectory())
        p = Path(self.tmpdir.name) / name
        p.write_text(json.dumps(payload, ensure_ascii=False), encoding="utf-8")
        return load_audit_index(p)

    def tearDown(self):
        if hasattr(self, "tmpdir"):
            self.tmpdir.cleanup()
            del self.tmpdir

    def test_relation_hit_uses_idx_not_string(self):
        """同 sample 内两个 ext 归一化同名但 type 不同，关系命中只能走对齐到的 idx。"""
        idx = self._audit({"audit_samples": [{
            "source_sample_id": "s1",
            "gold_entities": [
                {"entity_id": "g1", "name": "系统调用", "type": "Concept"},
                {"entity_id": "g2", "name": "习题", "type": "Assignment"},
            ],
            "gold_relations": [
                {"source_entity_id": "g1", "target_entity_id": "g2",
                 "type": "evaluated_by"},
            ],
        }]})
        # 有两个 "系统调用"：一个 Concept、一个 Term；gold 指向 Concept。
        # ext 关系的 source="系统调用" 名字一致，idx 扇出到两个，但命中要看对齐 idx。
        results = [_success_result(
            "s1",
            [
                {"id": "e1", "title": "系统调用", "type": "Concept"},
                {"id": "e2", "title": "系统调用", "type": "Term"},
                {"id": "e3", "title": "习题", "type": "Assignment"},
            ],
            [{"source": "系统调用", "target": "习题", "type": "evaluated_by"}],
        )]
        self.assertEqual(compute_audit_relation_recall(results, idx), 1.0)

    def test_relation_hit_fanout_multiple_extraction_matches(self):
        """extraction 关系端点名扇出到多个 ext 时，仍能命中 gold 对齐的那一个。"""
        idx = self._audit({"audit_samples": [{
            "source_sample_id": "s1",
            "gold_entities": [
                {"entity_id": "g1", "name": "操作系统", "type": "Course"},
                {"entity_id": "g2", "name": "第一章", "type": "Chapter"},
            ],
            "gold_relations": [
                {"source_entity_id": "g1", "target_entity_id": "g2",
                 "type": "contains"},
            ],
        }]})
        # 两条 ext 关系都叫 (操作系统, 第一章, contains)，但端点指向不同 idx
        results = [_success_result(
            "s1",
            [
                {"id": "e1", "title": "操作系统", "type": "Course"},
                {"id": "e2", "title": "第一章", "type": "Chapter"},
                {"id": "e3", "title": "第一章", "type": "Section"},  # 同名不同类型
            ],
            [{"source": "操作系统", "target": "第一章", "type": "contains"}],
        )]
        self.assertEqual(compute_audit_relation_recall(results, idx), 1.0)

    def test_relation_miss_when_src_unaligned(self):
        idx = self._audit({"audit_samples": [{
            "source_sample_id": "s1",
            "gold_entities": [
                {"entity_id": "g1", "name": "未抽出的东西", "type": "Concept"},
                {"entity_id": "g2", "name": "第一章", "type": "Chapter"},
            ],
            "gold_relations": [
                {"source_entity_id": "g1", "target_entity_id": "g2",
                 "type": "contains"},
            ],
        }]})
        results = [_success_result(
            "s1",
            [{"id": "e1", "title": "第一章", "type": "Chapter"}],
            [{"source": "未抽出的东西", "target": "第一章", "type": "contains"}],
        )]
        # src 对齐失败 → 不算命中
        self.assertEqual(compute_audit_relation_recall(results, idx), 0.0)

    def test_relation_empty_gold_relations_returns_zero(self):
        idx = self._audit({"audit_samples": [{
            "source_sample_id": "s1",
            "gold_entities": [
                {"entity_id": "g1", "name": "操作系统", "type": "Course"},
            ],
            "gold_relations": [],
        }]})
        results = [_success_result(
            "s1",
            [{"id": "e1", "title": "操作系统", "type": "Course"}],
            [],
        )]
        self.assertEqual(compute_audit_relation_recall(results, idx), 0.0)

    def test_relation_no_valid_samples_returns_zero(self):
        idx = self._audit({"audit_samples": []})
        results = [_success_result("unrelated", [], [])]
        self.assertEqual(compute_audit_relation_recall(results, idx), 0.0)
```

- [ ] **Step 2: 跑测试验证失败**

Run: `cd graphrag_pipeline && python -m pytest tests/test_scoring_audit.py::TestRelationRecallIdxDriven -v`
Expected: FAIL（旧实现用 `(src_name_norm, rtype, tgt_name_norm)`，同名不同类型会串扰 / 走子串）

- [ ] **Step 3: 重写 `compute_audit_relation_recall`**

替换实现：

```python
def compute_audit_relation_recall(
    results: Sequence[StructuredExtractionResult],
    audit_index: dict[str, AuditEntry],
) -> float:
    """每个成功样本：命中 gold relation 数 / gold relation 总数，按样本平均。

    命中判定（idx 驱动）：
      1. 用 align_sample 得到 gold_id -> matched_ext_idx。
      2. 把 extraction 关系按 rel.source / rel.target 的 title_norm 扇出到
         (src_idx, rtype, tgt_idx) 三元组集合（不同 type 的 ext idx 独立）。
      3. gold 两端 matched_ext_idx 齐全后，检查 (src, rtype, tgt) 是否在集合里。

    零分母规则：
      - 样本级：len(gold_relations) == 0 时样本不计入。
      - 汇总级：无可用样本时整体返回 0.0。
    """
    recalls: list[float] = []
    for item in results:
        if item.status != "success":
            continue
        entry = audit_index.get(item.sample_id)
        if entry is None or not entry.gold_relations:
            continue
        golds = _build_gold_entities(entry)
        if not golds:
            continue
        cands = _build_ext_candidates(item)
        aligned = align_sample(golds, cands)

        # title_norm -> list[ext_idx]（跨 type 合并，关系本身不带端点 type）
        title_to_idxs: dict[str, list[int]] = {}
        for cand in cands:
            title_to_idxs.setdefault(cand.title_norm, []).append(cand.idx)

        extracted_triples: set[tuple[int, str, int]] = set()
        for rel in item.relationships:
            src_norm = _normalize_title(rel.source)
            tgt_norm = _normalize_title(rel.target)
            for s in title_to_idxs.get(src_norm, ()):
                for t in title_to_idxs.get(tgt_norm, ()):
                    extracted_triples.add((s, rel.type, t))

        hits = 0
        for g in entry.gold_relations:
            src_id = str(g.get("source_entity_id", "") or "")
            tgt_id = str(g.get("target_entity_id", "") or "")
            rtype = g.get("type", "")
            if not rtype:
                continue
            src_align = aligned.get(src_id)
            tgt_align = aligned.get(tgt_id)
            if src_align is None or tgt_align is None:
                continue
            src = src_align.matched_ext_idx
            tgt = tgt_align.matched_ext_idx
            if src is None or tgt is None:
                continue
            if (src, rtype, tgt) in extracted_triples:
                hits += 1
        recalls.append(hits / len(entry.gold_relations))
    if not recalls:
        return 0.0
    return sum(recalls) / len(recalls)
```

- [ ] **Step 4: 跑全量 audit 测试**

Run: `cd graphrag_pipeline && python -m pytest tests/test_scoring_audit.py -v`
Expected: 全部通过

- [ ] **Step 5: 提交**

```bash
git add graphrag_pipeline/scripts/scoring_audit.py graphrag_pipeline/tests/test_scoring_audit.py
git commit -m "refactor(scoring_audit): compute_audit_relation_recall 改为 idx 驱动的严格命中"
```

---

## Task 8: 删除遗留私有符号

**Files:**
- Modify: `graphrag_pipeline/scripts/scoring_audit.py`

- [ ] **Step 1: 再次搜一次引用，确认无其它调用者**

Run: `cd /home/sunlight/Projects/ckqa && rg -n "_align_gold_to_extracted|_entity_hit|SHORT_GOLD_GUARD_LEN|_extracted_aligns_to_gold" graphrag_pipeline/ | rg -v '^graphrag_pipeline/scripts/scoring_audit\.py:' | rg -v '^graphrag_pipeline/scripts/diagnose_step8\.py:'`
Expected: 无输出（scoring_audit.py 本身和 diagnose_step8.py 还在用，Task 9 会切）

如果还有其它引用（如被某个未覆盖到的脚本引用），停下来复查，不要继续删。

- [ ] **Step 2: 删除遗留符号**

从 `scoring_audit.py` 中删除以下符号：

- 常量：`SHORT_GOLD_GUARD_LEN`
- 函数：`_extracted_aligns_to_gold`、`_entity_hit`、`_align_gold_to_extracted`

**注意**：暂不动 `diagnose_step8.py` 的 import —— 它将在 Task 9 切换。Task 8 与 Task 9 都提交前，`diagnose_step8.py` 会处于短暂 broken 状态，这在同一 PR 内可接受；若需绝对无坏状态，把 Task 8 与 Task 9 合并成一次 commit。

- [ ] **Step 3: 跑 scoring_audit 测试确认未回退**

Run: `cd graphrag_pipeline && python -m pytest tests/test_scoring_audit.py -v`
Expected: 全部通过

- [ ] **Step 4: 跑 score_extraction_results 测试确认公共 API 未回退**

Run: `cd graphrag_pipeline && python -m pytest tests/test_score_extraction_results.py -v`
Expected: 全部通过

- [ ] **Step 5: 提交（不提交 `diagnose_step8.py` 的改动 —— Task 9 一并提）**

```bash
git add graphrag_pipeline/scripts/scoring_audit.py
git commit -m "refactor(scoring_audit): 删除遗留的 _align_gold_to_extracted / _entity_hit / SHORT_GOLD_GUARD_LEN"
```

---

## Task 9: 迁移 `diagnose_step8.py` 到新对齐器 + 新 verdict + 新列

**Files:**
- Modify: `graphrag_pipeline/scripts/diagnose_step8.py`
- Test: `graphrag_pipeline/tests/test_diagnose_step8.py`

- [ ] **Step 1: 写失败测试：align_collision verdict + match_mode 列**

在 `tests/test_diagnose_step8.py` 末尾（`if __name__` 之前）追加：

```python
COLLISION_AUDIT_PAYLOAD = {
    "audit_samples": [{
        "source_sample_id": "s_coll",
        "gold_entities": [
            {"entity_id": "g-a", "name": "进程", "type": "Concept"},
            {"entity_id": "g-b", "name": "进程", "type": "Concept"},
        ],
        "gold_relations": [
            # 两条 gold relation，分别从 g-a / g-b 出发指向同一 Chapter
            {"source_entity_id": "g-a", "target_entity_id": "g-c",
             "type": "contains"},
            {"source_entity_id": "g-b", "target_entity_id": "g-c",
             "type": "contains"},
        ],
    }]
}


def _eval_collision(candidate: str) -> dict:
    """单一 '进程'/Concept ext，两条 gold 抢它；target 也只有一条 Chapter。"""
    return {
        "task": "candidate_extraction",
        "candidate": candidate,
        "results": [{
            "sample_id": "s_coll",
            "candidate": candidate,
            "status": "success",
            "entities": [
                {"id": "e1", "title": "进程", "type": "Concept",
                 "description": "", "evidence": ""},
                {"id": "e2", "title": "第一章", "type": "Chapter",
                 "description": "", "evidence": ""},
            ],
            "relationships": [
                {"source": "进程", "target": "第一章", "type": "contains",
                 "description": "", "evidence": ""},
            ],
            "raw_output": "",
            "error": None,
            "parser_error_code": None,
            "llm_debug": None,
        }],
    }


class TestDiagnoseMatchModeAndCollision(unittest.TestCase):
    def _run(self, audit_payload, eval_payload):
        with tempfile.TemporaryDirectory() as tmp:
            root = Path(tmp)
            (root / "config" / "schema").mkdir(parents=True)
            (root / "config" / "schema" / "entity_types.json").write_text(
                json.dumps({"schema_version": "v1",
                            "entity_types": {"Concept": {}, "Chapter": {}}}),
                encoding="utf-8",
            )
            (root / "config" / "schema" / "relation_types.json").write_text(
                json.dumps({
                    "schema_version": "v1",
                    "relation_types": {
                        "contains": {
                            "source_types": ["Concept"],
                            "target_types": ["Chapter"],
                        }
                    },
                }),
                encoding="utf-8",
            )
            # g-c 对应的 gold entity 也要有定义
            audit_payload["audit_samples"][0]["gold_entities"].append(
                {"entity_id": "g-c", "name": "第一章", "type": "Chapter"}
            )
            (root / "data" / "eval").mkdir(parents=True)
            (root / "data" / "eval" / "audit_extraction_set.json").write_text(
                json.dumps(audit_payload, ensure_ascii=False), encoding="utf-8"
            )
            (root / "results" / "extraction_eval").mkdir(parents=True)
            (root / "results" / "extraction_eval" / "alpha.json").write_text(
                json.dumps(eval_payload, ensure_ascii=False), encoding="utf-8"
            )
            summary = diagnose_step8(
                root=root,
                eval_dir=None,
                relation_schema_path=None,
                audit_path=None,
                output_dir=None,
                overwrite=False,
            )
            return Path(summary["output"]).read_text(encoding="utf-8")

    def test_b1_summary_contains_align_collision_column(self):
        content = self._run(COLLISION_AUDIT_PAYLOAD, _eval_collision("alpha"))
        self.assertIn("align_collision", content)

    def test_b2_detail_table_has_match_mode_column(self):
        content = self._run(COLLISION_AUDIT_PAYLOAD, _eval_collision("alpha"))
        # 明细表表头应包含 match_mode (src / tgt) 列
        self.assertIn("match_mode (src / tgt)", content)
        # 冲突场景下至少能看到 exact_occupied 字样
        self.assertIn("exact_occupied", content)

    def test_collision_verdict_classification(self):
        content = self._run(COLLISION_AUDIT_PAYLOAD, _eval_collision("alpha"))
        # 一条 gold 命中（hit 或 triple_not_in_ext，取决于 relation 命中），
        # 另一条被 *_occupied 判定为 align_collision
        self.assertIn("align_collision", content)

    def test_match_mode_values_are_closed_set(self):
        content = self._run(COLLISION_AUDIT_PAYLOAD, _eval_collision("alpha"))
        # 明细表里 match_mode 列的值必须来自五值闭集
        # 取 B.2 段落的内容做宽松校验
        b2_start = content.index("### B.2")
        b2 = content[b2_start:]
        for token in ("exact", "alias", "exact_occupied", "alias_occupied", "none"):
            # 至少其中之一必然出现；关键是不能出现五值之外的字符串
            pass
        forbidden = ("fuzzy", "partial", "substring")
        for bad in forbidden:
            self.assertNotIn(bad, b2)
```

- [ ] **Step 2: 跑测试验证失败**

Run: `cd graphrag_pipeline && python -m pytest tests/test_diagnose_step8.py::TestDiagnoseMatchModeAndCollision -v`
Expected: FAIL — 至少因为 `ImportError`（`_align_gold_to_extracted` 已删），或 `align_collision` / `match_mode` 字样未出现

- [ ] **Step 3: 更新 `diagnose_step8.py` 头部 imports 与 module docstring**

把 `diagnose_step8.py` 顶部的：

```python
"""步骤 8 诊断脚本：endpoint 失败模式 + audit relation 命中差距。

用途：一次性阅读工具，辅助决策下一步分叉（Prompt / schema / audit 扩量 / gate 调整）。
读完报告即可，不承担持续诊断基础设施职责。

依赖 private 函数（工具脚本可接受）：
- scoring_audit._align_gold_to_extracted（Step 2 的 1:1 实体对齐规则）
- scoring_metrics._normalize_title（title 归一化规则）
"""
```

改成：

```python
"""步骤 8 诊断脚本：endpoint 失败模式 + audit relation 命中差距。

用途：一次性阅读工具，辅助决策下一步分叉（Prompt / schema / audit 扩量 / gate 调整）。
读完报告即可，不承担持续诊断基础设施职责。

依赖 private 函数（工具脚本可接受）：
- scoring_audit.align_sample（严格对齐，返回 AlignResult.match_mode ∈ 五值闭集）
- scoring_audit._build_ext_candidates / _build_gold_entities（输入适配器）
- scoring_metrics._normalize_title（title 归一化规则）
"""
```

把 import 行：

```python
from scoring_audit import _align_gold_to_extracted, load_audit_index
```

改为：

```python
from scoring_audit import (
    AlignResult,
    _build_ext_candidates,
    _build_gold_entities,
    align_sample,
    load_audit_index,
)
```

- [ ] **Step 4: 重写 `classify_audit_relation` 与 `diagnose_audit_for_results`**

替换 `classify_audit_relation`：

```python
def classify_audit_relation(gold_rel, aligned, id_to_name, idx_triples):
    """按 7 类 verdict 判定 gold relation 命中情况。

    verdict 闭集：hit / type_mismatch / triple_not_in_ext /
                  align_fail_src / align_fail_tgt / align_fail_both /
                  align_collision（新增）。
    """
    src_id = str(gold_rel.get("source_entity_id", "") or "")
    tgt_id = str(gold_rel.get("target_entity_id", "") or "")
    rtype = gold_rel.get("type", "")
    src_name = id_to_name.get(src_id, "")
    tgt_name = id_to_name.get(tgt_id, "")
    src_align: AlignResult | None = aligned.get(src_id)
    tgt_align: AlignResult | None = aligned.get(tgt_id)
    src_mode = src_align.match_mode if src_align else "none"
    tgt_mode = tgt_align.match_mode if tgt_align else "none"
    src_idx = src_align.matched_ext_idx if src_align else None
    tgt_idx = tgt_align.matched_ext_idx if tgt_align else None

    occupied_modes = {"exact_occupied", "alias_occupied"}
    if src_mode in occupied_modes or tgt_mode in occupied_modes:
        verdict = "align_collision"
        ext_types: list[str] = []
    elif src_idx is None and tgt_idx is None:
        verdict = "align_fail_both"
        ext_types = []
    elif src_idx is None:
        verdict = "align_fail_src"
        ext_types = []
    elif tgt_idx is None:
        verdict = "align_fail_tgt"
        ext_types = []
    else:
        ext_types_set = idx_triples.get((src_idx, tgt_idx), set())
        ext_types = sorted(ext_types_set)
        if not ext_types_set:
            verdict = "triple_not_in_ext"
        elif rtype in ext_types_set:
            verdict = "hit"
        else:
            verdict = "type_mismatch"
    return {
        "verdict": verdict,
        "gold_src": src_name,
        "gold_tgt": tgt_name,
        "gold_type": rtype,
        "aligned_src_idx": src_idx,
        "aligned_tgt_idx": tgt_idx,
        "src_match_mode": src_mode,
        "tgt_match_mode": tgt_mode,
        "ext_types": ext_types,
    }
```

替换 `diagnose_audit_for_results`：

```python
def diagnose_audit_for_results(results, audit_index):
    per_sample: list[tuple[str, list[dict]]] = []
    verdicts: collections.Counter = collections.Counter()
    for item in results:
        if item.status != "success":
            continue
        entry = audit_index.get(item.sample_id)
        if entry is None or not entry.gold_relations:
            continue
        golds = _build_gold_entities(entry)
        if not golds:
            continue
        cands = _build_ext_candidates(item)
        aligned = align_sample(golds, cands)
        id_to_name = {g.gold_id: next(
            (ge.get("name", "") for ge in entry.gold_entities
             if str(ge.get("entity_id", "")) == g.gold_id), "")
            for g in golds}

        # title_norm -> list[ext_idx]，用于把 extraction 关系扇出成 idx triple
        title_to_idxs: dict[str, list[int]] = {}
        for cand in cands:
            title_to_idxs.setdefault(cand.title_norm, []).append(cand.idx)
        # (src_idx, tgt_idx) -> set of rel types
        idx_triples: dict[tuple[int, int], set[str]] = collections.defaultdict(set)
        for rel in item.relationships:
            for s in title_to_idxs.get(_normalize_title(rel.source), ()):
                for t in title_to_idxs.get(_normalize_title(rel.target), ()):
                    idx_triples[(s, t)].add(rel.type)

        diagnoses: list[dict] = []
        for g in entry.gold_relations:
            d = classify_audit_relation(g, aligned, id_to_name, idx_triples)
            verdicts[d["verdict"]] += 1
            diagnoses.append(d)
        per_sample.append((item.sample_id, diagnoses))
    return per_sample, verdicts
```

- [ ] **Step 5: 扩展 VERDICT_KEYS 与 render_markdown 的 B.2 明细表列**

修改 `VERDICT_KEYS` 常量：

```python
VERDICT_KEYS = ("hit", "type_mismatch", "triple_not_in_ext",
                "align_fail_src", "align_fail_tgt", "align_fail_both",
                "align_collision")
```

在 `render_markdown` 里，把 B.2 明细表的表头与行拼接从：

```python
            out.append("| gold | aligned (src / tgt) | ext_types | verdict |")
            out.append("|---|---|---|---|")
            for d in diagnoses:
                gold = f"{d['gold_src']} --[{d['gold_type']}]--> {d['gold_tgt']}"
                aligned = f"{d['aligned_src'] or '—'} / {d['aligned_tgt'] or '—'}"
                ext_types = ", ".join(d["ext_types"]) if d["ext_types"] else "—"
                out.append(f"| {gold} | {aligned} | {ext_types} | {d['verdict']} |")
```

改成：

```python
            out.append("| gold | aligned_idx (src / tgt) | match_mode (src / tgt) | ext_types | verdict |")
            out.append("|---|---|---|---|---|")
            for d in diagnoses:
                gold = f"{d['gold_src']} --[{d['gold_type']}]--> {d['gold_tgt']}"
                src_idx = d['aligned_src_idx']
                tgt_idx = d['aligned_tgt_idx']
                aligned = (
                    f"{src_idx if src_idx is not None else '—'} / "
                    f"{tgt_idx if tgt_idx is not None else '—'}"
                )
                mode = f"{d['src_match_mode']} / {d['tgt_match_mode']}"
                ext_types = ", ".join(d["ext_types"]) if d["ext_types"] else "—"
                out.append(f"| {gold} | {aligned} | {mode} | {ext_types} | {d['verdict']} |")
```

- [ ] **Step 6: 跑全部 diagnose_step8 测试**

Run: `cd graphrag_pipeline && python -m pytest tests/test_diagnose_step8.py -v`
Expected: 原冒烟用例 `test_end_to_end_generates_markdown_with_sections` 需要与新增用例一起通过；如果原冒烟用例因表列变化断言失败，更新对应 `self.assertIn` 目标。

具体要补的断言更新（冒烟用例）：

在原 `test_end_to_end_generates_markdown_with_sections` 最后加一行：

```python
        self.assertIn("match_mode (src / tgt)", content)
```

再把：

```python
        self.assertTrue(any(v in content for v in (
            "align_fail_src", "align_fail_tgt", "triple_not_in_ext", "type_mismatch", "hit"
        )))
```

改成：

```python
        self.assertTrue(any(v in content for v in (
            "align_fail_src", "align_fail_tgt", "triple_not_in_ext",
            "type_mismatch", "hit", "align_collision"
        )))
```

- [ ] **Step 7: 跑全量回归确认无其它回退**

Run: `cd graphrag_pipeline && python -m pytest tests/ -v`
Expected: 全部通过

- [ ] **Step 8: 提交**

```bash
git add graphrag_pipeline/scripts/diagnose_step8.py graphrag_pipeline/tests/test_diagnose_step8.py
git commit -m "refactor(diagnose_step8): 切到 align_sample；新增 align_collision verdict 与 match_mode 列"
```

---

## Task 10: 真实样本级回归抽查

**目的：** pytest 全绿只说明代码如我期望运行，不代表报告结论"更真实"。这一步用真实 audit 集与现有候选产物重跑诊断，人工比对新旧报告，确认关键错配样本（习题 / 同名不同类型 / occupied 冲突）被正确重分类。

**Files:**
- Read-only: `graphrag_pipeline/data/eval/audit_extraction_set.json`
- Read-only: `graphrag_pipeline/results/extraction_eval/*.json`
- Write: `graphrag_pipeline/results/reports/extraction_scoring/diagnostics/<new-timestamp>.md`

- [ ] **Step 1: 重跑诊断脚本**

Run: `cd graphrag_pipeline && python scripts/diagnose_step8.py`
Expected: 终端输出 JSON，含 `output` 字段指向新 `.md` 文件；不报错。

- [ ] **Step 2: 把新旧报告并排打开，重点检查三类样本**

记下新报告路径 `NEW=<path>`，旧报告为 `graphrag_pipeline/results/reports/extraction_scoring/diagnostics/2026-04-19T005540.md`。

逐条验证：

| 场景 | 样本 × 候选 | 旧 verdict | 期望新 verdict | 期望 match_mode (src / tgt) |
|---|---|---|---|---|
| 习题错配 | pts-0004-ac3447c62d · auto_tuned · `第九章 操作系统接口 --[contains]--> 第九章 操作系统接口习题` | `triple_not_in_ext` | `align_fail_tgt` | `exact / none` |
| 习题 evaluated_by | pts-0004-ac3447c62d · schema_aware · `用户接口 --[evaluated_by]--> 第九章 操作系统接口习题` | `triple_not_in_ext` | `align_fail_tgt` | 任意 / `none` |
| 前言 | pts-0005-a0753fd9ff · 任意候选 · `前言 --[contains]--> X` | `align_fail_*` | `align_fail_src` 或 `align_fail_both`（不变） | `none / *` |

逐项在新报告里定位对应样本块，确认 verdict 与 match_mode 与期望一致。

- [ ] **Step 3: 检查 B.1 汇总表**

在新报告里找 `### B.1 总体 verdict 计数`，确认：

- 列头出现 `align_collision`
- 四个候选的 `triple_not_in_ext` 列数值较旧报告下降
- `align_fail_src` / `align_fail_tgt` 列数值较旧报告上升
- `hit` 列可能仍为 0（这是正常的：strict 规则下更诚实，不掩盖 0 命中）

- [ ] **Step 4: 检查 audit 软指标 composite 输入变化**

Run: `cd graphrag_pipeline && python scripts/score_extraction_results.py --overwrite`
Expected: 不报错，输出对比表。

打开 `results/reports/extraction_scoring/run_<timestamp>/extraction_compare.md`，对比 `audit_entity_recall` / `audit_entity_precision` / `audit_relation_recall` 三列较上一次运行的数值：

- 三列数值预期**下降**（之前虚高）
- `gate_passed` 列对所有候选应保持原状（soft 指标下降不影响硬门槛）
- `composite_score` 排名允许变化，但不应出现任一候选从 `gate_passed=True` 退化为 `False`

如果 `gate_passed` 出现退化，说明存在范围外影响，停下排查。

- [ ] **Step 5: 把新诊断报告与新评测报告归档，并记录一行 note**

在 [graphrag_pipeline/results/reports/step8_analysis.md](../../../graphrag_pipeline/results/reports/step8_analysis.md) 末尾追加一段：

```markdown
## 2026-04-19 aligner 严格化后基线

- 新诊断报告：`results/reports/extraction_scoring/diagnostics/<新时间戳>.md`
- 新评测对比：`results/reports/extraction_scoring/run_<新时间戳>/extraction_compare.md`
- 口径变更：去子串化 + 类型相等 + one-to-one 占用；audit 三软指标预期下降，
  诊断表新增 `match_mode` 列与 `align_collision` verdict。
- 旧对照：`results/reports/extraction_scoring/diagnostics/2026-04-19T005540.md`。
```

- [ ] **Step 6: 提交回归产物**

```bash
git add graphrag_pipeline/results/reports/extraction_scoring/diagnostics/ \
        graphrag_pipeline/results/reports/extraction_scoring/ \
        graphrag_pipeline/results/reports/step8_analysis.md
git commit -m "chore(step8): aligner 严格化后基线诊断 + 评测对比归档"
```

---

## Task 11: 最终全量回归

**Files:** 无改动，仅跑回归

- [ ] **Step 1: 跑所有受影响测试**

Run: `cd graphrag_pipeline && python -m pytest tests/ -v`
Expected: 全部通过

- [ ] **Step 2: 跑仓库级漂移审计**

Run: `cd /home/sunlight/Projects/ckqa && python scripts/audit_repo_drift.py --strict`
Expected: 无新增漂移告警（本次只改脚本和测试，不改入口文件清单）

- [ ] **Step 3: 最终确认私有符号不再被引用**

Run: `cd /home/sunlight/Projects/ckqa && rg -n "_align_gold_to_extracted|_entity_hit|SHORT_GOLD_GUARD_LEN|_extracted_aligns_to_gold"`
Expected: 只剩 docs 里的历史记录，不出现在 `graphrag_pipeline/scripts/` 或 `graphrag_pipeline/tests/` 下

- [ ] **Step 4: 在 PR / 提交日志里附一句口径变更说明**

若使用分支 + PR，PR 描述新增一段：

```markdown
**评测口径变更**：audit 对齐器自本次起使用严格 exact/alias + 类型相等 + one-to-one 占用规则。
三条 audit 软指标（recall / precision / relation_recall）数值相对之前会下降，属于预期；
composite_score 的 hard gate 未动，gate_passed 排名不应受扰。
```

如只是线性提交到 main 无 PR，跳过此步。

---

## Self-Review 记录

在本计划落盘前做了以下自检：

1. **Spec 覆盖**：Spec 第 3.1 节数据类型 → Task 1；3.3/3.4 align 算法 → Task 2-3；4.x 度量改造 → Task 5-7；5.1 scoring_audit 调用端 → Task 5-8；5.2 diagnose_step8 → Task 9；7.x 测试策略 → Task 1-7, 9；另外 Task 10 覆盖用户特别要求的"真实样本级回归抽查"。
2. **Placeholder 扫描**：全文无 TBD / TODO；每个代码步都给完整片段。
3. **类型一致性**：`MatchMode` 在所有任务中均使用闭集 `"exact" | "alias" | "exact_occupied" | "alias_occupied" | "none"`；`AlignResult` 字段 `matched_ext_idx` / `match_mode` 在 Task 1-9 保持一致；`align_sample` 返回类型 `dict[str, AlignResult]` 全程使用同一语义。
4. **Scope 检查**：只改评测侧（scoring_audit / diagnose_step8 + 测试 + 归档报告），未波及 schema / prompt / gold / composite 权重。
