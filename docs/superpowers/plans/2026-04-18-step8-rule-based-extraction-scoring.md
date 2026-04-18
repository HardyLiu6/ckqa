# 步骤 8：规则化自动评测 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 在不改动步骤 7 产物契约的前提下，为 `graphrag_pipeline` 候选 Prompt 抽取结果实现一条「零 LLM 成本、可复现」的规则化自动评测链路：读取 `results/extraction_eval/*.json`，计算 8 项核心指标 + 2 项 audit 校准指标，加权得到 composite score，输出 CSV / Markdown / top-k JSON 三份比较报告，完成候选 Prompt 自动排序与 top-k 筛选。

**Architecture:** 沿用步骤 7 已落地的模块化风格（`extraction_schema.py` / `extraction_parser.py` / `result_writer.py`），把「度量计算」「audit 对齐」「报告写盘」「CLI 编排」拆成 4 个模块，保证纯函数可单测、CLI 只做 IO 与编排。指标全部在候选层聚合，不做 sample 级加权；排序用 composite score，权重可通过 JSON 配置覆盖。审核数据只做轻量校准（按 `source_sample_id` 交集），不强制覆盖全量样本。

**Tech Stack:** Python 3.10+ (`statistics` 标准库做 CV，`csv` 标准库写 CSV)，Pydantic v2（沿用 `extraction_schema.py` 既有模型），pytest。不引入新的第三方依赖。

---

## 文件结构与职责

- 新增：`graphrag_pipeline/scripts/scoring_metrics.py`
  纯函数度量层。输入为 `list[StructuredExtractionResult]` + `SchemaCatalog`，输出每候选的 8 项硬指标字典；不做 IO，不依赖 audit 集。

- 新增：`graphrag_pipeline/scripts/scoring_audit.py`
  audit 校准层。负责读取 `data/eval/audit_extraction_set.json`，构建 `source_sample_id → gold` 索引，计算 `audit_entity_recall` / `audit_relation_recall` 两项软指标。

- 新增：`graphrag_pipeline/scripts/scoring_report.py`
  报告落盘层。负责写 `results/reports/extraction_compare.csv` / `extraction_compare.md` / `top_candidates.json`。与度量层解耦，不重复计算，只消费聚合结果。

- 新增：`graphrag_pipeline/scripts/score_extraction_results.py`
  CLI 主入口（argparse）。发现 `results/extraction_eval/*.json`，载入 schema，调度度量层与 audit 层，做 composite score / ranking / top-k，调用报告层写盘，控制台输出摘要 JSON。

- 新增：`graphrag_pipeline/tests/test_scoring_metrics.py`
  每项硬指标各自独立单测，避免端到端测试掩盖算法错误。

- 新增：`graphrag_pipeline/tests/test_scoring_audit.py`
  audit 索引构建、entity/relation recall 软对齐单测。

- 新增：`graphrag_pipeline/tests/test_scoring_report.py`
  验证 csv/md/json 三种产物的结构稳定性（不做断言文本色调，仅固化列、表头、字段顺序）。

- 新增：`graphrag_pipeline/tests/test_score_extraction_results.py`
  端到端用例：构造 2 个候选的最小 eval 目录，跑 CLI，断言排序、top-k、composite score、3 份产物均落盘且内容一致。

- 修改：`graphrag_pipeline/CLAUDE.md`
  在 Common Commands 增加 `python scripts/score_extraction_results.py` 行。

- 修改：`graphrag_pipeline/PROMPT_TUNING_PIPELINE.md`
  在「当前已落地脚本」一节补充步骤 8 的评测脚本说明。

---

## 指标定义（锁死，不要再临时改动）

指标在**候选层**聚合，不对 sample 做加权平均，除非该指标天然是 per-sample（例如 `schema_hit_rate` 与 audit recall）。

| 指标 | 定义 | 分母 |
|---|---|---|
| `parse_success_rate` | `count(status=="success") / total` | 候选下全部样本 |
| `schema_hit_rate` | per-sample 布尔：该样本**全部**实体类型 ∈ schema 且**全部**关系类型 ∈ schema；取成功样本里的平均值 | 候选下 `status=="success"` 的样本数 |
| `entity_type_valid_rate` | `sum(entities with type ∈ entity_type_names) / sum(all entities)` | 候选下成功样本实体总数 |
| `relation_type_valid_rate` | `sum(relations with type ∈ relation_type_names) / sum(all relations)` | 候选下成功样本关系总数 |
| `endpoint_valid_rate` | 同时满足：(1) `source`/`target` 归一化后可在**同一样本抽取实体**中找到；(2) 这两个实体类型对 ∈ `schema.relation_types[type].source_types × target_types`。分子是合法端点的关系数 | 候选下**关系类型合法**的关系总数（若为 0，则指标按 0.0 记） |
| `duplicate_entity_rate` | 同一样本内 `(normalized_title, type)` 出现次数 > 1 的所有「多余」记录数 / 实体总数 | 候选下成功样本实体总数 |
| `noise_entity_rate` | 命中噪声规则的实体数 / 实体总数。规则（任一命中即算）：title 去空白后为空 / 长度<2 且非英文缩写 / 仅标点 / 纯数字 / 命中停用词表 `{"无","本章","本节","如下","图","表","见下图","见图"}` | 候选下成功样本实体总数 |
| `output_stability` | `1 - min(1, cv_entity + cv_relation)`，其中 `cv = stddev/mean`（对候选下各成功样本的实体/关系计数）。分母为 0 时对应 CV 记为 0；成功样本 < 2 时该指标记为 1.0 | 候选下成功样本计数序列 |
| `audit_entity_recall`（软） | 对 audit 交集样本：每个 gold 实体若能在抽取结果里按 `normalize(title) == normalize(gold.name)` 或 `gold.name ∈ normalize(extracted.title)` 命中则 +1；分子求和除以 gold 总数，再按样本数求平均 | 交集样本的 gold 实体总数 |
| `audit_relation_recall`（软） | 对 audit 交集样本：先把 gold 关系的 `source_entity_id/target_entity_id` 映射到 gold 实体 name；若 (src_name, type, tgt_name) 能在抽取结果里按实体 title 匹配命中则 +1 | 交集样本的 gold 关系总数 |

归一化规则 `_normalize_title`：`str(x).strip()` → 小写 → 去除 Unicode 空白与半角标点（`.,;:!?,。；：！？` 与 `'"()[]{}（）【】《》`）。

### Composite Score 默认权重

```python
DEFAULT_WEIGHTS = {
    "parse_success_rate": 0.20,
    "schema_hit_rate": 0.10,
    "entity_type_valid_rate": 0.15,
    "relation_type_valid_rate": 0.15,
    "endpoint_valid_rate": 0.15,
    "duplicate_complement": 0.05,   # 1 - duplicate_entity_rate
    "noise_complement": 0.05,       # 1 - noise_entity_rate
    "output_stability": 0.05,
    "audit_entity_recall": 0.05,
    "audit_relation_recall": 0.05,
}
# 和 = 1.00；audit 缺失时，把对应两项权重按比例平摊回其他 8 项
```

排序 tie-breaker 顺序：`composite_score` → `parse_success_rate` → `endpoint_valid_rate` → `candidate name` 字典序。

---

### Task 1: 指标模块骨架 + `parse_success_rate`

**Files:**
- Create: `graphrag_pipeline/scripts/scoring_metrics.py`
- Create: `graphrag_pipeline/tests/test_scoring_metrics.py`

- [ ] **Step 1: 写第一个失败测试（parse_success_rate）**

测试文件头部的 sys.path 补丁参考已有 `tests/test_run_candidate_extraction.py` 的写法。

```python
# graphrag_pipeline/tests/test_scoring_metrics.py
from __future__ import annotations

import sys
import unittest
from pathlib import Path

_PROJECT_ROOT = Path(__file__).resolve().parent.parent
_SCRIPTS_DIR = _PROJECT_ROOT / "scripts"
if str(_SCRIPTS_DIR) not in sys.path:
    sys.path.insert(0, str(_SCRIPTS_DIR))

from extraction_schema import StructuredExtractionResult
from scoring_metrics import compute_parse_success_rate


def _result(sample_id: str, status: str) -> StructuredExtractionResult:
    return StructuredExtractionResult(
        sample_id=sample_id,
        candidate="c",
        status=status,
        entities=[],
        relationships=[],
        raw_output="",
    )


class TestParseSuccessRate(unittest.TestCase):
    def test_all_success(self):
        results = [_result("s1", "success"), _result("s2", "success")]
        self.assertEqual(compute_parse_success_rate(results), 1.0)

    def test_mixed(self):
        results = [
            _result("s1", "success"),
            _result("s2", "parse_error"),
            _result("s3", "llm_error"),
            _result("s4", "success"),
        ]
        self.assertEqual(compute_parse_success_rate(results), 0.5)

    def test_empty(self):
        self.assertEqual(compute_parse_success_rate([]), 0.0)


if __name__ == "__main__":
    unittest.main()
```

- [ ] **Step 2: 运行确认失败**

Run: `cd graphrag_pipeline && python -m pytest tests/test_scoring_metrics.py -v`
Expected: `ModuleNotFoundError: scoring_metrics`

- [ ] **Step 3: 创建骨架并实现 `compute_parse_success_rate`**

```python
# graphrag_pipeline/scripts/scoring_metrics.py
#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""候选 Prompt 抽取结果的规则化度量函数集合。

本模块只负责纯计算：输入 StructuredExtractionResult 列表与 schema，
输出候选级指标。不做 IO，不依赖 audit 集。
"""

from __future__ import annotations

from typing import Iterable, Sequence

from extraction_schema import StructuredExtractionResult


def compute_parse_success_rate(results: Sequence[StructuredExtractionResult]) -> float:
    """返回 status == "success" 的样本占比；空列表返回 0.0。"""
    if not results:
        return 0.0
    success = sum(1 for item in results if item.status == "success")
    return success / len(results)
```

- [ ] **Step 4: 运行确认通过**

Run: `cd graphrag_pipeline && python -m pytest tests/test_scoring_metrics.py -v`
Expected: 3 passed

- [ ] **Step 5: 提交**

```bash
git add graphrag_pipeline/scripts/scoring_metrics.py graphrag_pipeline/tests/test_scoring_metrics.py
git commit -m "feat: 新增 scoring_metrics 骨架与 parse_success_rate 指标"
```

---

### Task 2: 类型合法性 3 指标（entity / relation / schema_hit）

**Files:**
- Modify: `graphrag_pipeline/scripts/scoring_metrics.py`
- Modify: `graphrag_pipeline/tests/test_scoring_metrics.py`

- [ ] **Step 1: 先写 3 条失败测试**

追加到 `test_scoring_metrics.py`：

```python
from extraction_schema import ExtractionEntity, ExtractionRelationship
from scoring_metrics import (
    compute_entity_type_valid_rate,
    compute_relation_type_valid_rate,
    compute_schema_hit_rate,
)


def _success_result(entities, relationships) -> StructuredExtractionResult:
    return StructuredExtractionResult(
        sample_id="s",
        candidate="c",
        status="success",
        entities=[ExtractionEntity(**e) for e in entities],
        relationships=[ExtractionRelationship(**r) for r in relationships],
        raw_output="",
    )


ENTITY_TYPES = {"Course", "Chapter", "Concept"}
RELATION_TYPES = {"contains", "related_to"}


class TestTypeValidity(unittest.TestCase):
    def test_entity_type_valid_rate_mixed(self):
        results = [
            _success_result(
                [
                    {"id": "e1", "title": "OS", "type": "Course"},
                    {"id": "e2", "title": "Ch1", "type": "Chapter"},
                    {"id": "e3", "title": "X", "type": "NotInSchema"},
                ],
                [],
            )
        ]
        self.assertAlmostEqual(
            compute_entity_type_valid_rate(results, ENTITY_TYPES), 2 / 3
        )

    def test_entity_type_valid_rate_ignores_non_success(self):
        results = [
            _result("s1", "parse_error"),
            _success_result(
                [{"id": "e1", "title": "OS", "type": "Course"}], []
            ),
        ]
        self.assertEqual(compute_entity_type_valid_rate(results, ENTITY_TYPES), 1.0)

    def test_entity_type_valid_rate_empty_returns_zero(self):
        self.assertEqual(compute_entity_type_valid_rate([], ENTITY_TYPES), 0.0)

    def test_relation_type_valid_rate(self):
        results = [
            _success_result(
                [],
                [
                    {"source": "a", "target": "b", "type": "contains"},
                    {"source": "a", "target": "c", "type": "nope"},
                ],
            )
        ]
        self.assertEqual(
            compute_relation_type_valid_rate(results, RELATION_TYPES), 0.5
        )

    def test_schema_hit_rate_per_sample(self):
        fully_valid = _success_result(
            [{"id": "e1", "title": "OS", "type": "Course"}],
            [{"source": "OS", "target": "Ch1", "type": "contains"}],
        )
        bad_entity = _success_result(
            [{"id": "e1", "title": "X", "type": "Bad"}],
            [{"source": "X", "target": "Y", "type": "contains"}],
        )
        self.assertEqual(
            compute_schema_hit_rate(
                [fully_valid, bad_entity], ENTITY_TYPES, RELATION_TYPES
            ),
            0.5,
        )

    def test_schema_hit_rate_no_success_returns_zero(self):
        self.assertEqual(
            compute_schema_hit_rate(
                [_result("s1", "parse_error")], ENTITY_TYPES, RELATION_TYPES
            ),
            0.0,
        )
```

- [ ] **Step 2: 运行确认失败**

Run: `cd graphrag_pipeline && python -m pytest tests/test_scoring_metrics.py -v`
Expected: `ImportError: cannot import name 'compute_entity_type_valid_rate'`

- [ ] **Step 3: 实现 3 个函数**

追加到 `scoring_metrics.py`：

```python
def _iter_success(results: Sequence[StructuredExtractionResult]):
    return (item for item in results if item.status == "success")


def compute_entity_type_valid_rate(
    results: Sequence[StructuredExtractionResult],
    entity_type_names: Iterable[str],
) -> float:
    allowed = set(entity_type_names)
    total = 0
    valid = 0
    for item in _iter_success(results):
        for ent in item.entities:
            total += 1
            if ent.type in allowed:
                valid += 1
    return valid / total if total else 0.0


def compute_relation_type_valid_rate(
    results: Sequence[StructuredExtractionResult],
    relation_type_names: Iterable[str],
) -> float:
    allowed = set(relation_type_names)
    total = 0
    valid = 0
    for item in _iter_success(results):
        for rel in item.relationships:
            total += 1
            if rel.type in allowed:
                valid += 1
    return valid / total if total else 0.0


def compute_schema_hit_rate(
    results: Sequence[StructuredExtractionResult],
    entity_type_names: Iterable[str],
    relation_type_names: Iterable[str],
) -> float:
    entity_allowed = set(entity_type_names)
    relation_allowed = set(relation_type_names)
    success_items = list(_iter_success(results))
    if not success_items:
        return 0.0
    hits = 0
    for item in success_items:
        entities_ok = all(ent.type in entity_allowed for ent in item.entities)
        relations_ok = all(rel.type in relation_allowed for rel in item.relationships)
        if entities_ok and relations_ok:
            hits += 1
    return hits / len(success_items)
```

- [ ] **Step 4: 运行确认通过**

Run: `cd graphrag_pipeline && python -m pytest tests/test_scoring_metrics.py -v`
Expected: 所有用例通过（9 passed）

- [ ] **Step 5: 提交**

```bash
git add graphrag_pipeline/scripts/scoring_metrics.py graphrag_pipeline/tests/test_scoring_metrics.py
git commit -m "feat: 新增实体/关系类型合法性与 schema_hit_rate 指标"
```

---

### Task 3: 关系端点有效性 `endpoint_valid_rate`

**Files:**
- Modify: `graphrag_pipeline/scripts/scoring_metrics.py`
- Modify: `graphrag_pipeline/tests/test_scoring_metrics.py`

端点校验需要用到 `relation_types.json` 里每个关系类型的 `source_types` / `target_types`。`SchemaCatalog` 只保留了 name/label/description，不包含端点约束。本步骤**不**改 `SchemaCatalog`，直接让主入口把 raw relation schema dict 透给度量层，保持 Pydantic 模型稳定。

- [ ] **Step 1: 先写失败测试**

追加到 `test_scoring_metrics.py`：

```python
from scoring_metrics import compute_endpoint_valid_rate


RELATION_SCHEMA = {
    "contains": {"source_types": ["Course", "Chapter"], "target_types": ["Chapter", "Concept"]},
    "related_to": {"source_types": ["Concept"], "target_types": ["Concept"]},
}


class TestEndpointValidRate(unittest.TestCase):
    def test_both_endpoints_match_schema(self):
        results = [
            _success_result(
                [
                    {"id": "e1", "title": "操作系统", "type": "Course"},
                    {"id": "e2", "title": "第一章", "type": "Chapter"},
                ],
                [{"source": "操作系统", "target": "第一章", "type": "contains"}],
            )
        ]
        self.assertEqual(compute_endpoint_valid_rate(results, RELATION_SCHEMA), 1.0)

    def test_endpoint_type_mismatch_rejected(self):
        results = [
            _success_result(
                [
                    {"id": "e1", "title": "进程", "type": "Concept"},
                    {"id": "e2", "title": "第一章", "type": "Chapter"},
                ],
                # Concept -> Chapter 不在 contains 的端点约束里
                [{"source": "进程", "target": "第一章", "type": "contains"}],
            )
        ]
        self.assertEqual(compute_endpoint_valid_rate(results, RELATION_SCHEMA), 0.0)

    def test_unresolved_endpoint_rejected(self):
        results = [
            _success_result(
                [{"id": "e1", "title": "操作系统", "type": "Course"}],
                [{"source": "操作系统", "target": "某个幻觉实体", "type": "contains"}],
            )
        ]
        self.assertEqual(compute_endpoint_valid_rate(results, RELATION_SCHEMA), 0.0)

    def test_invalid_relation_type_excluded_from_denominator(self):
        # type 合法性检查在上一层；端点指标分母只算 type 合法的关系，type 非法不计入
        results = [
            _success_result(
                [{"id": "e1", "title": "A", "type": "Concept"}],
                [{"source": "A", "target": "A", "type": "totally_bogus"}],
            )
        ]
        self.assertEqual(compute_endpoint_valid_rate(results, RELATION_SCHEMA), 0.0)

    def test_normalization_matches_titles(self):
        # 归一化后应忽略大小写与首尾空白
        results = [
            _success_result(
                [
                    {"id": "e1", "title": " 操作系统 ", "type": "Course"},
                    {"id": "e2", "title": "第一章", "type": "Chapter"},
                ],
                [{"source": "操作系统", "target": " 第一章", "type": "contains"}],
            )
        ]
        self.assertEqual(compute_endpoint_valid_rate(results, RELATION_SCHEMA), 1.0)
```

- [ ] **Step 2: 运行确认失败**

Run: `cd graphrag_pipeline && python -m pytest tests/test_scoring_metrics.py::TestEndpointValidRate -v`
Expected: `ImportError: cannot import name 'compute_endpoint_valid_rate'`

- [ ] **Step 3: 实现归一化工具和端点校验**

追加到 `scoring_metrics.py` 顶部（`_iter_success` 之前）：

```python
import re
import unicodedata

_PUNCT_RE = re.compile(r"[\s\.,;:!?，。；：！？'\"\(\)\[\]\{\}（）【】《》]+")


def _normalize_title(value: str) -> str:
    if value is None:
        return ""
    text = unicodedata.normalize("NFKC", str(value)).strip().lower()
    return _PUNCT_RE.sub("", text)
```

追加函数：

```python
def compute_endpoint_valid_rate(
    results: Sequence[StructuredExtractionResult],
    relation_schema: dict,
) -> float:
    total = 0
    valid = 0
    for item in _iter_success(results):
        title_to_type: dict[str, str] = {
            _normalize_title(ent.title): ent.type for ent in item.entities
        }
        for rel in item.relationships:
            constraints = relation_schema.get(rel.type)
            if not constraints:
                # 关系类型非法：不计入端点分母
                continue
            total += 1
            src_type = title_to_type.get(_normalize_title(rel.source))
            tgt_type = title_to_type.get(_normalize_title(rel.target))
            if src_type is None or tgt_type is None:
                continue
            source_types = set(constraints.get("source_types") or [])
            target_types = set(constraints.get("target_types") or [])
            if src_type in source_types and tgt_type in target_types:
                valid += 1
    return valid / total if total else 0.0
```

- [ ] **Step 4: 运行确认通过**

Run: `cd graphrag_pipeline && python -m pytest tests/test_scoring_metrics.py -v`
Expected: 14 passed

- [ ] **Step 5: 提交**

```bash
git add graphrag_pipeline/scripts/scoring_metrics.py graphrag_pipeline/tests/test_scoring_metrics.py
git commit -m "feat: 新增关系端点有效性指标 endpoint_valid_rate"
```

---

### Task 4: 重复实体与噪声实体指标

**Files:**
- Modify: `graphrag_pipeline/scripts/scoring_metrics.py`
- Modify: `graphrag_pipeline/tests/test_scoring_metrics.py`

- [ ] **Step 1: 先写失败测试**

追加到 `test_scoring_metrics.py`：

```python
from scoring_metrics import (
    compute_duplicate_entity_rate,
    compute_noise_entity_rate,
)


class TestDuplicateAndNoise(unittest.TestCase):
    def test_duplicate_rate_counts_extras(self):
        results = [
            _success_result(
                [
                    {"id": "e1", "title": "进程", "type": "Concept"},
                    {"id": "e2", "title": "进程", "type": "Concept"},  # duplicate
                    {"id": "e3", "title": "进程 ", "type": "Concept"},  # duplicate (normalized)
                    {"id": "e4", "title": "线程", "type": "Concept"},
                ],
                [],
            )
        ]
        # 4 实体中多出的 2 条视为重复 → 2/4 = 0.5
        self.assertEqual(compute_duplicate_entity_rate(results), 0.5)

    def test_duplicate_rate_same_title_different_type_ok(self):
        results = [
            _success_result(
                [
                    {"id": "e1", "title": "PCB", "type": "Term"},
                    {"id": "e2", "title": "PCB", "type": "Concept"},
                ],
                [],
            )
        ]
        self.assertEqual(compute_duplicate_entity_rate(results), 0.0)

    def test_duplicate_rate_per_sample_only(self):
        # 样本 A 与样本 B 内部互不影响
        results = [
            _success_result(
                [{"id": "e1", "title": "进程", "type": "Concept"}], []
            ),
            _success_result(
                [{"id": "e2", "title": "进程", "type": "Concept"}], []
            ),
        ]
        self.assertEqual(compute_duplicate_entity_rate(results), 0.0)

    def test_noise_rate_empty_title(self):
        results = [
            _success_result(
                [
                    {"id": "e1", "title": "   ", "type": "Term"},
                    {"id": "e2", "title": "操作系统", "type": "Course"},
                ],
                [],
            )
        ]
        self.assertEqual(compute_noise_entity_rate(results), 0.5)

    def test_noise_rate_numeric_and_punct_and_stopword(self):
        results = [
            _success_result(
                [
                    {"id": "e1", "title": "123", "type": "Term"},
                    {"id": "e2", "title": "。。。", "type": "Term"},
                    {"id": "e3", "title": "本章", "type": "Concept"},
                    {"id": "e4", "title": "图", "type": "Concept"},
                    {"id": "e5", "title": "操作系统", "type": "Course"},
                ],
                [],
            )
        ]
        self.assertAlmostEqual(compute_noise_entity_rate(results), 4 / 5)

    def test_noise_rate_allows_english_abbrev_of_len_2(self):
        # 2 字符英文缩写（如 OS）不应判为噪声
        results = [
            _success_result(
                [{"id": "e1", "title": "OS", "type": "Course"}], []
            )
        ]
        self.assertEqual(compute_noise_entity_rate(results), 0.0)
```

- [ ] **Step 2: 运行确认失败**

Run: `cd graphrag_pipeline && python -m pytest tests/test_scoring_metrics.py::TestDuplicateAndNoise -v`
Expected: `ImportError: cannot import name 'compute_duplicate_entity_rate'`

- [ ] **Step 3: 实现两个指标**

追加到 `scoring_metrics.py`：

```python
NOISE_STOPWORDS = {"无", "本章", "本节", "如下", "图", "表", "见下图", "见图"}


def compute_duplicate_entity_rate(
    results: Sequence[StructuredExtractionResult],
) -> float:
    total = 0
    dupes = 0
    for item in _iter_success(results):
        seen: set[tuple[str, str]] = set()
        for ent in item.entities:
            total += 1
            key = (_normalize_title(ent.title), ent.type)
            if key in seen:
                dupes += 1
            else:
                seen.add(key)
    return dupes / total if total else 0.0


def _is_noise_entity(title: str) -> bool:
    stripped = (title or "").strip()
    if not stripped:
        return True
    if stripped in NOISE_STOPWORDS:
        return True
    normalized = _normalize_title(stripped)
    if not normalized:
        return True  # 仅标点/空白
    if normalized.isdigit():
        return True
    if len(normalized) < 2 and not normalized.isascii():
        return True
    return False


def compute_noise_entity_rate(
    results: Sequence[StructuredExtractionResult],
) -> float:
    total = 0
    noise = 0
    for item in _iter_success(results):
        for ent in item.entities:
            total += 1
            if _is_noise_entity(ent.title):
                noise += 1
    return noise / total if total else 0.0
```

- [ ] **Step 4: 运行确认通过**

Run: `cd graphrag_pipeline && python -m pytest tests/test_scoring_metrics.py -v`
Expected: 20 passed

- [ ] **Step 5: 提交**

```bash
git add graphrag_pipeline/scripts/scoring_metrics.py graphrag_pipeline/tests/test_scoring_metrics.py
git commit -m "feat: 新增重复实体与噪声实体两项规则化指标"
```

---

### Task 5: 输出稳定性 `output_stability`

**Files:**
- Modify: `graphrag_pipeline/scripts/scoring_metrics.py`
- Modify: `graphrag_pipeline/tests/test_scoring_metrics.py`

- [ ] **Step 1: 先写失败测试**

```python
from scoring_metrics import compute_output_stability


class TestOutputStability(unittest.TestCase):
    def test_constant_counts_is_one(self):
        # 每个样本抽到相同数量 → CV=0 → 稳定度 1.0
        results = [
            _success_result(
                [{"id": "e1", "title": "A", "type": "Course"},
                 {"id": "e2", "title": "B", "type": "Course"}],
                [{"source": "A", "target": "B", "type": "contains"}],
            ),
            _success_result(
                [{"id": "e3", "title": "C", "type": "Course"},
                 {"id": "e4", "title": "D", "type": "Course"}],
                [{"source": "C", "target": "D", "type": "contains"}],
            ),
        ]
        self.assertEqual(compute_output_stability(results), 1.0)

    def test_single_success_sample_returns_one(self):
        results = [_success_result(
            [{"id": "e1", "title": "A", "type": "Course"}], []
        )]
        self.assertEqual(compute_output_stability(results), 1.0)

    def test_empty_returns_one(self):
        self.assertEqual(compute_output_stability([]), 1.0)

    def test_high_variance_lowers_score(self):
        # 实体数量差异极大 → 稳定度 < 1
        results = [
            _success_result(
                [{"id": f"e{i}", "title": f"T{i}", "type": "Course"} for i in range(10)],
                [],
            ),
            _success_result(
                [{"id": "e1", "title": "A", "type": "Course"}], []
            ),
        ]
        self.assertLess(compute_output_stability(results), 1.0)
        self.assertGreaterEqual(compute_output_stability(results), 0.0)
```

- [ ] **Step 2: 运行确认失败**

Run: `cd graphrag_pipeline && python -m pytest tests/test_scoring_metrics.py::TestOutputStability -v`
Expected: `ImportError: cannot import name 'compute_output_stability'`

- [ ] **Step 3: 实现稳定性指标**

```python
import statistics


def _coefficient_of_variation(values: Sequence[int]) -> float:
    if len(values) < 2:
        return 0.0
    mean = statistics.fmean(values)
    if mean == 0:
        return 0.0
    stdev = statistics.pstdev(values)
    return stdev / mean


def compute_output_stability(results: Sequence[StructuredExtractionResult]) -> float:
    success_items = [item for item in results if item.status == "success"]
    if len(success_items) < 2:
        return 1.0
    entity_counts = [len(item.entities) for item in success_items]
    relation_counts = [len(item.relationships) for item in success_items]
    cv = _coefficient_of_variation(entity_counts) + _coefficient_of_variation(relation_counts)
    return max(0.0, 1.0 - min(1.0, cv))
```

- [ ] **Step 4: 运行确认通过**

Run: `cd graphrag_pipeline && python -m pytest tests/test_scoring_metrics.py -v`
Expected: 24 passed

- [ ] **Step 5: 提交**

```bash
git add graphrag_pipeline/scripts/scoring_metrics.py graphrag_pipeline/tests/test_scoring_metrics.py
git commit -m "feat: 新增输出稳定性指标 output_stability"
```

---

### Task 6: audit 校准（entity/relation recall）

**Files:**
- Create: `graphrag_pipeline/scripts/scoring_audit.py`
- Create: `graphrag_pipeline/tests/test_scoring_audit.py`

audit 结构回忆：
- 顶层有 `audit_samples`，每条含 `source_sample_id`、`gold_entities`（`entity_id/name/type/alias/normalized_name`）、`gold_relations`（`relation_id/source_entity_id/target_entity_id/type/evidence_text`）。
- 抽取结果 relationships 里的 `source`/`target` 是实体 title 而不是 id，所以必须先 `gold entity_id → gold name`，再匹配抽取关系的 title。

- [ ] **Step 1: 先写失败测试**

```python
# graphrag_pipeline/tests/test_scoring_audit.py
from __future__ import annotations

import json
import sys
import tempfile
import unittest
from pathlib import Path

_PROJECT_ROOT = Path(__file__).resolve().parent.parent
_SCRIPTS_DIR = _PROJECT_ROOT / "scripts"
if str(_SCRIPTS_DIR) not in sys.path:
    sys.path.insert(0, str(_SCRIPTS_DIR))

from extraction_schema import (
    ExtractionEntity,
    ExtractionRelationship,
    StructuredExtractionResult,
)
from scoring_audit import (
    compute_audit_entity_recall,
    compute_audit_relation_recall,
    load_audit_index,
)


AUDIT_PAYLOAD = {
    "audit_samples": [
        {
            "source_sample_id": "s1",
            "gold_entities": [
                {"entity_id": "ent-1", "name": "操作系统", "type": "Course",
                 "alias": [], "normalized_name": "操作系统"},
                {"entity_id": "ent-2", "name": "第一章 引论", "type": "Chapter",
                 "alias": [], "normalized_name": "第一章 引论"},
            ],
            "gold_relations": [
                {"relation_id": "rel-1", "source_entity_id": "ent-1",
                 "target_entity_id": "ent-2", "type": "contains"}
            ],
        }
    ]
}


def _success_result(sample_id, entities, relationships):
    return StructuredExtractionResult(
        sample_id=sample_id,
        candidate="c",
        status="success",
        entities=[ExtractionEntity(**e) for e in entities],
        relationships=[ExtractionRelationship(**r) for r in relationships],
        raw_output="",
    )


class TestAuditAlignment(unittest.TestCase):
    def setUp(self):
        self.tmpdir = tempfile.TemporaryDirectory()
        self.audit_path = Path(self.tmpdir.name) / "audit.json"
        self.audit_path.write_text(
            json.dumps(AUDIT_PAYLOAD, ensure_ascii=False), encoding="utf-8"
        )
        self.index = load_audit_index(self.audit_path)

    def tearDown(self):
        self.tmpdir.cleanup()

    def test_entity_recall_full_hit(self):
        results = [
            _success_result(
                "s1",
                [
                    {"id": "e1", "title": "操作系统", "type": "Course"},
                    {"id": "e2", "title": "第一章 引论", "type": "Chapter"},
                ],
                [],
            )
        ]
        self.assertEqual(compute_audit_entity_recall(results, self.index), 1.0)

    def test_entity_recall_half_hit(self):
        results = [
            _success_result(
                "s1",
                [{"id": "e1", "title": "操作系统", "type": "Course"}],
                [],
            )
        ]
        self.assertEqual(compute_audit_entity_recall(results, self.index), 0.5)

    def test_entity_recall_missing_sample_excluded(self):
        results = [_success_result("s_not_in_audit", [], [])]
        self.assertEqual(compute_audit_entity_recall(results, self.index), 0.0)

    def test_relation_recall_hit(self):
        results = [
            _success_result(
                "s1",
                [
                    {"id": "e1", "title": "操作系统", "type": "Course"},
                    {"id": "e2", "title": "第一章 引论", "type": "Chapter"},
                ],
                [{"source": "操作系统", "target": "第一章 引论", "type": "contains"}],
            )
        ]
        self.assertEqual(compute_audit_relation_recall(results, self.index), 1.0)

    def test_relation_recall_requires_type_match(self):
        results = [
            _success_result(
                "s1",
                [
                    {"id": "e1", "title": "操作系统", "type": "Course"},
                    {"id": "e2", "title": "第一章 引论", "type": "Chapter"},
                ],
                [{"source": "操作系统", "target": "第一章 引论", "type": "related_to"}],
            )
        ]
        self.assertEqual(compute_audit_relation_recall(results, self.index), 0.0)


if __name__ == "__main__":
    unittest.main()
```

- [ ] **Step 2: 运行确认失败**

Run: `cd graphrag_pipeline && python -m pytest tests/test_scoring_audit.py -v`
Expected: `ModuleNotFoundError: scoring_audit`

- [ ] **Step 3: 实现 audit 对齐**

```python
# graphrag_pipeline/scripts/scoring_audit.py
#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""audit 校准集对齐工具。

只负责：读取 audit 集，构建索引，计算实体/关系 recall 软指标。
对齐策略：
- 实体：归一化 title == 归一化 gold.name 或 gold.name 出现在归一化 title 中。
- 关系：gold (src_id, type, tgt_id) 先映射为 (src_name, type, tgt_name)，再检查
  抽取结果里是否存在同 (归一化 src_name, type, 归一化 tgt_name) 的关系。
"""

from __future__ import annotations

import json
from dataclasses import dataclass
from pathlib import Path
from typing import Sequence

from extraction_schema import StructuredExtractionResult
from scoring_metrics import _normalize_title


@dataclass(frozen=True)
class AuditEntry:
    gold_entities: list[dict]
    gold_relations: list[dict]


def load_audit_index(path: Path) -> dict[str, AuditEntry]:
    payload = json.loads(Path(path).read_text(encoding="utf-8"))
    samples = payload.get("audit_samples") or []
    index: dict[str, AuditEntry] = {}
    for sample in samples:
        sample_id = str(sample.get("source_sample_id") or "").strip()
        if not sample_id:
            continue
        index[sample_id] = AuditEntry(
            gold_entities=list(sample.get("gold_entities") or []),
            gold_relations=list(sample.get("gold_relations") or []),
        )
    return index


def _entity_hit(gold_name: str, extracted_titles_norm: set[str]) -> bool:
    g_norm = _normalize_title(gold_name)
    if not g_norm:
        return False
    if g_norm in extracted_titles_norm:
        return True
    # 子串宽松匹配：抽取到更长的标题仍算命中
    return any(g_norm in title for title in extracted_titles_norm)


def compute_audit_entity_recall(
    results: Sequence[StructuredExtractionResult],
    audit_index: dict[str, AuditEntry],
) -> float:
    recalls: list[float] = []
    for item in results:
        if item.status != "success":
            continue
        entry = audit_index.get(item.sample_id)
        if entry is None or not entry.gold_entities:
            continue
        extracted = {_normalize_title(e.title) for e in item.entities}
        hits = sum(1 for g in entry.gold_entities if _entity_hit(g.get("name", ""), extracted))
        recalls.append(hits / len(entry.gold_entities))
    if not recalls:
        return 0.0
    return sum(recalls) / len(recalls)


def compute_audit_relation_recall(
    results: Sequence[StructuredExtractionResult],
    audit_index: dict[str, AuditEntry],
) -> float:
    recalls: list[float] = []
    for item in results:
        if item.status != "success":
            continue
        entry = audit_index.get(item.sample_id)
        if entry is None or not entry.gold_relations:
            continue
        id_to_name = {g["entity_id"]: g.get("name", "") for g in entry.gold_entities}
        extracted_triples = {
            (_normalize_title(r.source), r.type, _normalize_title(r.target))
            for r in item.relationships
        }
        hits = 0
        for g in entry.gold_relations:
            src = _normalize_title(id_to_name.get(g.get("source_entity_id", ""), ""))
            tgt = _normalize_title(id_to_name.get(g.get("target_entity_id", ""), ""))
            rtype = g.get("type", "")
            if not src or not tgt or not rtype:
                continue
            if (src, rtype, tgt) in extracted_triples:
                hits += 1
        recalls.append(hits / len(entry.gold_relations))
    if not recalls:
        return 0.0
    return sum(recalls) / len(recalls)
```

- [ ] **Step 4: 运行确认通过**

Run: `cd graphrag_pipeline && python -m pytest tests/test_scoring_audit.py -v`
Expected: 5 passed

- [ ] **Step 5: 提交**

```bash
git add graphrag_pipeline/scripts/scoring_audit.py graphrag_pipeline/tests/test_scoring_audit.py
git commit -m "feat: 新增 audit 校准模块 scoring_audit"
```

---

### Task 7: 候选聚合 + composite score + ranking + top-k

**Files:**
- Modify: `graphrag_pipeline/scripts/scoring_metrics.py`
- Modify: `graphrag_pipeline/tests/test_scoring_metrics.py`

保持 scoring_metrics.py 承担「纯计算」职责；聚合、权重、排序也视为计算，不做 IO。

- [ ] **Step 1: 先写失败测试**

```python
from scoring_metrics import (
    DEFAULT_WEIGHTS,
    aggregate_candidate_metrics,
    compute_composite_score,
    rank_candidates,
    select_top_k,
)


class TestAggregateAndRank(unittest.TestCase):
    def test_composite_score_all_ones(self):
        metrics = {name: 1.0 for name in [
            "parse_success_rate", "schema_hit_rate",
            "entity_type_valid_rate", "relation_type_valid_rate",
            "endpoint_valid_rate", "duplicate_complement",
            "noise_complement", "output_stability",
            "audit_entity_recall", "audit_relation_recall",
        ]}
        self.assertAlmostEqual(compute_composite_score(metrics, DEFAULT_WEIGHTS), 1.0)

    def test_composite_score_weights_missing_audit_redistributes(self):
        # 若 metrics 中 audit_* 为 None，则对应权重按比例摊回其他 8 项
        metrics = {
            "parse_success_rate": 1.0,
            "schema_hit_rate": 1.0,
            "entity_type_valid_rate": 1.0,
            "relation_type_valid_rate": 1.0,
            "endpoint_valid_rate": 1.0,
            "duplicate_complement": 1.0,
            "noise_complement": 1.0,
            "output_stability": 1.0,
            "audit_entity_recall": None,
            "audit_relation_recall": None,
        }
        self.assertAlmostEqual(
            compute_composite_score(metrics, DEFAULT_WEIGHTS), 1.0
        )

    def test_rank_candidates_sorts_by_score_desc(self):
        summaries = {
            "alpha": {"composite_score": 0.6, "parse_success_rate": 1.0,
                      "endpoint_valid_rate": 0.9},
            "beta":  {"composite_score": 0.8, "parse_success_rate": 0.9,
                      "endpoint_valid_rate": 0.9},
            "gamma": {"composite_score": 0.6, "parse_success_rate": 1.0,
                      "endpoint_valid_rate": 0.95},
        }
        ranked = rank_candidates(summaries)
        self.assertEqual([r["candidate"] for r in ranked], ["beta", "gamma", "alpha"])

    def test_select_top_k_k_limits(self):
        summaries = {
            "a": {"composite_score": 0.1, "parse_success_rate": 0.0,
                  "endpoint_valid_rate": 0.0},
            "b": {"composite_score": 0.9, "parse_success_rate": 1.0,
                  "endpoint_valid_rate": 1.0},
        }
        ranked = rank_candidates(summaries)
        top = select_top_k(ranked, k=1)
        self.assertEqual(len(top), 1)
        self.assertEqual(top[0]["candidate"], "b")

    def test_aggregate_candidate_metrics_returns_all_required_fields(self):
        # 最小可执行聚合：构造 schema 字典 + 1 个成功样本
        entity_type_names = ["Course", "Chapter"]
        relation_schema = {
            "contains": {"source_types": ["Course"], "target_types": ["Chapter"]}
        }
        results = [
            _success_result(
                [
                    {"id": "e1", "title": "OS", "type": "Course"},
                    {"id": "e2", "title": "Ch1", "type": "Chapter"},
                ],
                [{"source": "OS", "target": "Ch1", "type": "contains"}],
            )
        ]
        metrics = aggregate_candidate_metrics(
            results,
            entity_type_names=entity_type_names,
            relation_type_names=list(relation_schema),
            relation_schema=relation_schema,
            audit_entity_recall=None,
            audit_relation_recall=None,
        )
        expected_keys = {
            "parse_success_rate", "schema_hit_rate", "entity_type_valid_rate",
            "relation_type_valid_rate", "endpoint_valid_rate",
            "duplicate_entity_rate", "noise_entity_rate", "output_stability",
            "duplicate_complement", "noise_complement",
            "audit_entity_recall", "audit_relation_recall",
            "sample_count", "success_count",
        }
        self.assertTrue(expected_keys.issubset(metrics.keys()))
        self.assertEqual(metrics["parse_success_rate"], 1.0)
        self.assertEqual(metrics["endpoint_valid_rate"], 1.0)
```

- [ ] **Step 2: 运行确认失败**

Run: `cd graphrag_pipeline && python -m pytest tests/test_scoring_metrics.py::TestAggregateAndRank -v`
Expected: `ImportError`

- [ ] **Step 3: 实现聚合/权重/排序**

追加到 `scoring_metrics.py`：

```python
DEFAULT_WEIGHTS: dict[str, float] = {
    "parse_success_rate": 0.20,
    "schema_hit_rate": 0.10,
    "entity_type_valid_rate": 0.15,
    "relation_type_valid_rate": 0.15,
    "endpoint_valid_rate": 0.15,
    "duplicate_complement": 0.05,
    "noise_complement": 0.05,
    "output_stability": 0.05,
    "audit_entity_recall": 0.05,
    "audit_relation_recall": 0.05,
}


def aggregate_candidate_metrics(
    results: Sequence[StructuredExtractionResult],
    *,
    entity_type_names: Iterable[str],
    relation_type_names: Iterable[str],
    relation_schema: dict,
    audit_entity_recall: float | None,
    audit_relation_recall: float | None,
) -> dict[str, float | int | None]:
    duplicate_rate = compute_duplicate_entity_rate(results)
    noise_rate = compute_noise_entity_rate(results)
    return {
        "sample_count": len(results),
        "success_count": sum(1 for r in results if r.status == "success"),
        "parse_success_rate": compute_parse_success_rate(results),
        "schema_hit_rate": compute_schema_hit_rate(
            results, entity_type_names, relation_type_names
        ),
        "entity_type_valid_rate": compute_entity_type_valid_rate(
            results, entity_type_names
        ),
        "relation_type_valid_rate": compute_relation_type_valid_rate(
            results, relation_type_names
        ),
        "endpoint_valid_rate": compute_endpoint_valid_rate(results, relation_schema),
        "duplicate_entity_rate": duplicate_rate,
        "noise_entity_rate": noise_rate,
        "duplicate_complement": 1.0 - duplicate_rate,
        "noise_complement": 1.0 - noise_rate,
        "output_stability": compute_output_stability(results),
        "audit_entity_recall": audit_entity_recall,
        "audit_relation_recall": audit_relation_recall,
    }


def compute_composite_score(
    metrics: dict[str, float | None],
    weights: dict[str, float],
) -> float:
    present_keys = [k for k in weights if metrics.get(k) is not None]
    missing_keys = [k for k in weights if metrics.get(k) is None]
    missing_total = sum(weights[k] for k in missing_keys)
    present_total = sum(weights[k] for k in present_keys)
    if present_total == 0:
        return 0.0
    bonus = missing_total / present_total  # 比例摊回
    score = 0.0
    for key in present_keys:
        effective = weights[key] * (1.0 + bonus)
        score += effective * float(metrics[key])
    return score


def rank_candidates(
    metrics_by_candidate: dict[str, dict[str, float]],
) -> list[dict]:
    def sort_key(item: tuple[str, dict]):
        name, metrics = item
        return (
            -float(metrics.get("composite_score", 0.0)),
            -float(metrics.get("parse_success_rate", 0.0)),
            -float(metrics.get("endpoint_valid_rate", 0.0)),
            name,
        )

    ordered = sorted(metrics_by_candidate.items(), key=sort_key)
    return [
        {"rank": idx + 1, "candidate": name, **metrics}
        for idx, (name, metrics) in enumerate(ordered)
    ]


def select_top_k(ranked: list[dict], *, k: int) -> list[dict]:
    if k <= 0:
        return []
    return list(ranked[:k])
```

- [ ] **Step 4: 运行确认通过**

Run: `cd graphrag_pipeline && python -m pytest tests/test_scoring_metrics.py -v`
Expected: 29 passed

- [ ] **Step 5: 提交**

```bash
git add graphrag_pipeline/scripts/scoring_metrics.py graphrag_pipeline/tests/test_scoring_metrics.py
git commit -m "feat: 新增候选指标聚合、composite score 与排序工具"
```

---

### Task 8: 报告写盘（csv / md / top_candidates.json）

**Files:**
- Create: `graphrag_pipeline/scripts/scoring_report.py`
- Create: `graphrag_pipeline/tests/test_scoring_report.py`

- [ ] **Step 1: 先写失败测试**

```python
# graphrag_pipeline/tests/test_scoring_report.py
from __future__ import annotations

import csv
import json
import sys
import tempfile
import unittest
from pathlib import Path

_PROJECT_ROOT = Path(__file__).resolve().parent.parent
_SCRIPTS_DIR = _PROJECT_ROOT / "scripts"
if str(_SCRIPTS_DIR) not in sys.path:
    sys.path.insert(0, str(_SCRIPTS_DIR))

from scoring_report import (
    write_extraction_compare_csv,
    write_extraction_compare_markdown,
    write_top_candidates_json,
)


RANKED = [
    {
        "rank": 1, "candidate": "beta",
        "composite_score": 0.82, "parse_success_rate": 1.0,
        "schema_hit_rate": 0.8, "entity_type_valid_rate": 0.9,
        "relation_type_valid_rate": 0.9, "endpoint_valid_rate": 0.85,
        "duplicate_entity_rate": 0.05, "noise_entity_rate": 0.02,
        "duplicate_complement": 0.95, "noise_complement": 0.98,
        "output_stability": 0.9,
        "audit_entity_recall": 0.7, "audit_relation_recall": 0.6,
        "sample_count": 5, "success_count": 5,
    },
    {
        "rank": 2, "candidate": "alpha",
        "composite_score": 0.60, "parse_success_rate": 0.8,
        "schema_hit_rate": 0.6, "entity_type_valid_rate": 0.7,
        "relation_type_valid_rate": 0.7, "endpoint_valid_rate": 0.5,
        "duplicate_entity_rate": 0.1, "noise_entity_rate": 0.1,
        "duplicate_complement": 0.9, "noise_complement": 0.9,
        "output_stability": 0.7,
        "audit_entity_recall": 0.4, "audit_relation_recall": 0.3,
        "sample_count": 5, "success_count": 4,
    },
]


class TestReportWriters(unittest.TestCase):
    def test_write_csv_columns_and_order(self):
        with tempfile.TemporaryDirectory() as tmp:
            path = Path(tmp) / "compare.csv"
            write_extraction_compare_csv(path, RANKED)
            with path.open(encoding="utf-8") as fp:
                rows = list(csv.reader(fp))
            self.assertEqual(rows[0][:3], ["rank", "candidate", "composite_score"])
            self.assertEqual(rows[1][1], "beta")
            self.assertEqual(rows[2][1], "alpha")

    def test_write_markdown_contains_table_and_top_section(self):
        with tempfile.TemporaryDirectory() as tmp:
            path = Path(tmp) / "compare.md"
            write_extraction_compare_markdown(
                path, RANKED, weights={"parse_success_rate": 1.0}, top_k=1
            )
            content = path.read_text(encoding="utf-8")
        self.assertIn("| rank | candidate |", content)
        self.assertIn("## Top Candidates", content)
        self.assertIn("beta", content)

    def test_write_top_candidates_json_structure(self):
        with tempfile.TemporaryDirectory() as tmp:
            path = Path(tmp) / "top.json"
            write_top_candidates_json(
                path,
                ranked=RANKED,
                k=1,
                weights={"parse_success_rate": 1.0},
                inputs={"samples_file": "samples.json"},
            )
            data = json.loads(path.read_text(encoding="utf-8"))
        self.assertEqual(data["k"], 1)
        self.assertEqual(len(data["top_candidates"]), 1)
        self.assertEqual(data["top_candidates"][0]["candidate"], "beta")
        self.assertIn("weights", data)
        self.assertIn("inputs", data)


if __name__ == "__main__":
    unittest.main()
```

- [ ] **Step 2: 运行确认失败**

Run: `cd graphrag_pipeline && python -m pytest tests/test_scoring_report.py -v`
Expected: `ModuleNotFoundError: scoring_report`

- [ ] **Step 3: 实现 3 种产物写盘**

```python
# graphrag_pipeline/scripts/scoring_report.py
#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""评测报告写盘工具。

消费 rank_candidates() 的返回结构，不重复计算。写出三种产物：
- extraction_compare.csv：每候选一行，列按固定顺序。
- extraction_compare.md：表格 + Top-K 区块 + 权重说明。
- top_candidates.json：top-k 结构化结果，带权重与输入路径回溯。
"""

from __future__ import annotations

import csv
import json
from pathlib import Path
from typing import Any, Iterable

CSV_COLUMNS: tuple[str, ...] = (
    "rank",
    "candidate",
    "composite_score",
    "parse_success_rate",
    "schema_hit_rate",
    "entity_type_valid_rate",
    "relation_type_valid_rate",
    "endpoint_valid_rate",
    "duplicate_entity_rate",
    "noise_entity_rate",
    "output_stability",
    "audit_entity_recall",
    "audit_relation_recall",
    "sample_count",
    "success_count",
)


def _format_value(value: Any) -> str:
    if value is None:
        return ""
    if isinstance(value, float):
        return f"{value:.4f}"
    return str(value)


def write_extraction_compare_csv(path: Path, ranked: list[dict]) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    with path.open("w", encoding="utf-8", newline="") as handle:
        writer = csv.writer(handle)
        writer.writerow(list(CSV_COLUMNS))
        for row in ranked:
            writer.writerow([_format_value(row.get(col)) for col in CSV_COLUMNS])


def write_extraction_compare_markdown(
    path: Path,
    ranked: list[dict],
    *,
    weights: dict[str, float],
    top_k: int,
) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    lines: list[str] = []
    lines.append("# 候选 Prompt 规则化评测对比报告")
    lines.append("")
    lines.append("## 指标对比")
    lines.append("")
    lines.append("| " + " | ".join(CSV_COLUMNS) + " |")
    lines.append("|" + "|".join(["---"] * len(CSV_COLUMNS)) + "|")
    for row in ranked:
        lines.append(
            "| " + " | ".join(_format_value(row.get(col)) for col in CSV_COLUMNS) + " |"
        )
    lines.append("")
    lines.append(f"## Top Candidates (k={top_k})")
    lines.append("")
    for row in ranked[:top_k]:
        lines.append(
            f"- **{row['candidate']}**（rank={row.get('rank')}, "
            f"composite_score={_format_value(row.get('composite_score'))}）"
        )
    lines.append("")
    lines.append("## 权重")
    lines.append("")
    for key, weight in weights.items():
        lines.append(f"- `{key}`：{weight}")
    lines.append("")
    path.write_text("\n".join(lines), encoding="utf-8")


def write_top_candidates_json(
    path: Path,
    *,
    ranked: list[dict],
    k: int,
    weights: dict[str, float],
    inputs: dict[str, Any],
) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    payload = {
        "task": "extraction_score_top_candidates",
        "k": k,
        "weights": weights,
        "inputs": inputs,
        "top_candidates": list(ranked[:k]),
        "all_candidates_ranked": list(ranked),
    }
    path.write_text(json.dumps(payload, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
```

- [ ] **Step 4: 运行确认通过**

Run: `cd graphrag_pipeline && python -m pytest tests/test_scoring_report.py -v`
Expected: 3 passed

- [ ] **Step 5: 提交**

```bash
git add graphrag_pipeline/scripts/scoring_report.py graphrag_pipeline/tests/test_scoring_report.py
git commit -m "feat: 新增规则化评测报告写盘模块 scoring_report"
```

---

### Task 9: CLI 主入口 + 端到端测试

**Files:**
- Create: `graphrag_pipeline/scripts/score_extraction_results.py`
- Create: `graphrag_pipeline/tests/test_score_extraction_results.py`

- [ ] **Step 1: 先写端到端失败测试**

```python
# graphrag_pipeline/tests/test_score_extraction_results.py
from __future__ import annotations

import csv
import json
import sys
import tempfile
import unittest
from pathlib import Path

_PROJECT_ROOT = Path(__file__).resolve().parent.parent
_SCRIPTS_DIR = _PROJECT_ROOT / "scripts"
if str(_SCRIPTS_DIR) not in sys.path:
    sys.path.insert(0, str(_SCRIPTS_DIR))

from score_extraction_results import score_extraction_results


ENTITY_SCHEMA = {
    "schema_version": "v1",
    "entity_type_order": ["Course", "Chapter"],
    "entity_types": {
        "Course": {"label_zh": "课程", "description": "课程"},
        "Chapter": {"label_zh": "章节", "description": "章节"},
    },
}

RELATION_SCHEMA = {
    "schema_version": "v1",
    "relation_type_order": ["contains"],
    "relation_types": {
        "contains": {
            "label_zh": "包含", "description": "包含",
            "source_types": ["Course"], "target_types": ["Chapter"],
        }
    },
}


def _make_eval(candidate: str, success_entities: int) -> dict:
    entities = [
        {"id": "e1", "title": "操作系统", "type": "Course",
         "description": "", "evidence": ""}
    ]
    relationships = []
    for i in range(success_entities):
        chapter_title = f"第{i + 1}章"
        entities.append(
            {"id": f"e{i + 2}", "title": chapter_title, "type": "Chapter",
             "description": "", "evidence": ""}
        )
        relationships.append(
            {"source": "操作系统", "target": chapter_title, "type": "contains",
             "description": "", "evidence": ""}
        )
    return {
        "task": "candidate_extraction",
        "candidate": candidate,
        "model": "test",
        "samples_file": "samples.json",
        "manifest_file": "manifest.json",
        "summary": {"total": 1, "success": 1, "parse_error": 0, "llm_error": 0},
        "results": [
            {
                "sample_id": "s1",
                "candidate": candidate,
                "status": "success",
                "entities": entities,
                "relationships": relationships,
                "raw_output": "",
                "error": None,
                "parser_error_code": None,
                "llm_debug": None,
            }
        ],
    }


class TestEndToEnd(unittest.TestCase):
    def test_two_candidates_ranked_and_reports_written(self):
        with tempfile.TemporaryDirectory() as tmp:
            root = Path(tmp)
            (root / "results" / "extraction_eval").mkdir(parents=True)
            (root / "config" / "schema").mkdir(parents=True)
            (root / "results" / "reports").mkdir(parents=True)

            (root / "config" / "schema" / "entity_types.json").write_text(
                json.dumps(ENTITY_SCHEMA, ensure_ascii=False), encoding="utf-8"
            )
            (root / "config" / "schema" / "relation_types.json").write_text(
                json.dumps(RELATION_SCHEMA, ensure_ascii=False), encoding="utf-8"
            )
            (root / "results" / "extraction_eval" / "alpha.json").write_text(
                json.dumps(_make_eval("alpha", 1), ensure_ascii=False),
                encoding="utf-8",
            )
            (root / "results" / "extraction_eval" / "beta.json").write_text(
                json.dumps(_make_eval("beta", 3), ensure_ascii=False),
                encoding="utf-8",
            )

            summary = score_extraction_results(
                root=root,
                eval_dir=None,
                entity_schema_path=None,
                relation_schema_path=None,
                audit_path=None,
                weights=None,
                top_k=1,
                overwrite=True,
            )

            self.assertEqual(summary["status"], "success")
            csv_path = root / "results" / "reports" / "extraction_compare.csv"
            md_path = root / "results" / "reports" / "extraction_compare.md"
            top_path = root / "results" / "reports" / "top_candidates.json"
            self.assertTrue(csv_path.exists())
            self.assertTrue(md_path.exists())
            self.assertTrue(top_path.exists())

            with csv_path.open(encoding="utf-8") as fp:
                rows = list(csv.reader(fp))
            self.assertEqual(rows[0][:2], ["rank", "candidate"])
            # 两个候选，表头 + 2 行
            self.assertEqual(len(rows), 3)

            top_payload = json.loads(top_path.read_text(encoding="utf-8"))
            self.assertEqual(top_payload["k"], 1)
            self.assertEqual(len(top_payload["top_candidates"]), 1)


if __name__ == "__main__":
    unittest.main()
```

- [ ] **Step 2: 运行确认失败**

Run: `cd graphrag_pipeline && python -m pytest tests/test_score_extraction_results.py -v`
Expected: `ModuleNotFoundError: score_extraction_results`

- [ ] **Step 3: 实现 CLI 主入口**

```python
# graphrag_pipeline/scripts/score_extraction_results.py
#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""规则化自动评测 CLI（步骤 8）。

流程：
1. 发现 results/extraction_eval/*.json。
2. 载入 entity/relation schema 与 audit 集（可选）。
3. 对每个候选：parse 结果 → 聚合 10 项指标 → composite_score。
4. 排序 + top-k → 写 csv/md/json 报告。
"""

from __future__ import annotations

import argparse
import json
import sys
from pathlib import Path
from typing import Any, Sequence

from extraction_schema import (
    ExtractionEntity,
    ExtractionRelationship,
    StructuredExtractionResult,
)
from scoring_audit import (
    compute_audit_entity_recall,
    compute_audit_relation_recall,
    load_audit_index,
)
from scoring_metrics import (
    DEFAULT_WEIGHTS,
    aggregate_candidate_metrics,
    compute_composite_score,
    rank_candidates,
    select_top_k,
)
from scoring_report import (
    write_extraction_compare_csv,
    write_extraction_compare_markdown,
    write_top_candidates_json,
)


PROJECT_ROOT = Path(__file__).resolve().parents[1]

DEFAULT_EVAL_DIR = "results/extraction_eval"
DEFAULT_ENTITY_SCHEMA = "config/schema/entity_types.json"
DEFAULT_RELATION_SCHEMA = "config/schema/relation_types.json"
DEFAULT_AUDIT_PATH = "data/eval/audit_extraction_set.json"
DEFAULT_REPORTS_DIR = "results/reports"


def _resolve(path: str | Path | None, *, root: Path, default: str | None) -> Path | None:
    target = path if path is not None else default
    if target is None:
        return None
    candidate = Path(target)
    return candidate if candidate.is_absolute() else (root / candidate).resolve()


def _load_eval_file(path: Path) -> tuple[str, list[StructuredExtractionResult]]:
    payload = json.loads(path.read_text(encoding="utf-8"))
    candidate = str(payload.get("candidate") or path.stem).strip()
    raw_results = payload.get("results") or []
    results: list[StructuredExtractionResult] = []
    for raw in raw_results:
        results.append(
            StructuredExtractionResult(
                sample_id=str(raw.get("sample_id") or ""),
                candidate=candidate,
                status=str(raw.get("status") or "parse_error"),
                entities=[ExtractionEntity(**e) for e in raw.get("entities") or []],
                relationships=[
                    ExtractionRelationship(**r) for r in raw.get("relationships") or []
                ],
                raw_output=str(raw.get("raw_output") or ""),
                error=raw.get("error"),
                parser_error_code=raw.get("parser_error_code"),
                llm_debug=raw.get("llm_debug"),
            )
        )
    return candidate, results


def score_extraction_results(
    *,
    root: Path,
    eval_dir: str | Path | None,
    entity_schema_path: str | Path | None,
    relation_schema_path: str | Path | None,
    audit_path: str | Path | None,
    weights: dict[str, float] | None,
    top_k: int,
    overwrite: bool,
) -> dict[str, Any]:
    root = Path(root).resolve()
    eval_root = _resolve(eval_dir, root=root, default=DEFAULT_EVAL_DIR)
    entity_schema_file = _resolve(
        entity_schema_path, root=root, default=DEFAULT_ENTITY_SCHEMA
    )
    relation_schema_file = _resolve(
        relation_schema_path, root=root, default=DEFAULT_RELATION_SCHEMA
    )
    audit_file = _resolve(audit_path, root=root, default=DEFAULT_AUDIT_PATH)

    if eval_root is None or not eval_root.exists():
        raise FileNotFoundError(f"未找到评测输入目录：{eval_root}")
    eval_files = sorted(p for p in eval_root.glob("*.json") if p.is_file())
    if not eval_files:
        raise ValueError(f"评测输入目录无 JSON 文件：{eval_root}")

    if entity_schema_file is None or not entity_schema_file.exists():
        raise FileNotFoundError(f"实体 schema 不存在：{entity_schema_file}")
    if relation_schema_file is None or not relation_schema_file.exists():
        raise FileNotFoundError(f"关系 schema 不存在：{relation_schema_file}")

    entity_payload = json.loads(entity_schema_file.read_text(encoding="utf-8"))
    relation_payload = json.loads(relation_schema_file.read_text(encoding="utf-8"))
    entity_type_names = list((entity_payload.get("entity_types") or {}).keys())
    relation_type_block = relation_payload.get("relation_types") or {}
    relation_type_names = list(relation_type_block.keys())

    audit_index = {}
    if audit_file and audit_file.exists():
        audit_index = load_audit_index(audit_file)

    effective_weights = dict(weights or DEFAULT_WEIGHTS)

    metrics_by_candidate: dict[str, dict[str, Any]] = {}
    for eval_file in eval_files:
        candidate, results = _load_eval_file(eval_file)
        audit_ent = (
            compute_audit_entity_recall(results, audit_index) if audit_index else None
        )
        audit_rel = (
            compute_audit_relation_recall(results, audit_index) if audit_index else None
        )
        metrics = aggregate_candidate_metrics(
            results,
            entity_type_names=entity_type_names,
            relation_type_names=relation_type_names,
            relation_schema=relation_type_block,
            audit_entity_recall=audit_ent,
            audit_relation_recall=audit_rel,
        )
        metrics["composite_score"] = compute_composite_score(metrics, effective_weights)
        metrics_by_candidate[candidate] = metrics

    ranked = rank_candidates(metrics_by_candidate)
    top = select_top_k(ranked, k=top_k)

    reports_dir = root / DEFAULT_REPORTS_DIR
    csv_path = reports_dir / "extraction_compare.csv"
    md_path = reports_dir / "extraction_compare.md"
    top_path = reports_dir / "top_candidates.json"
    for path in (csv_path, md_path, top_path):
        if path.exists() and not overwrite:
            raise FileExistsError(f"目标产物已存在，若要覆盖请传 --overwrite：{path}")

    write_extraction_compare_csv(csv_path, ranked)
    write_extraction_compare_markdown(
        md_path, ranked, weights=effective_weights, top_k=top_k
    )
    write_top_candidates_json(
        top_path,
        ranked=ranked,
        k=top_k,
        weights=effective_weights,
        inputs={
            "eval_dir": str(eval_root),
            "entity_schema_path": str(entity_schema_file),
            "relation_schema_path": str(relation_schema_file),
            "audit_path": str(audit_file) if audit_file and audit_file.exists() else None,
            "eval_files": [str(p) for p in eval_files],
        },
    )

    return {
        "status": "success",
        "root": str(root),
        "eval_files": [str(p) for p in eval_files],
        "total_candidates": len(ranked),
        "top_k": top_k,
        "top_candidates": [item["candidate"] for item in top],
        "reports": {
            "csv": str(csv_path),
            "markdown": str(md_path),
            "top_candidates_json": str(top_path),
        },
    }


def _build_parser() -> argparse.ArgumentParser:
    parser = argparse.ArgumentParser(
        description="对候选 Prompt 抽取结果做规则化自动评测并输出对比报告"
    )
    parser.add_argument("--eval-dir", help="候选结果目录，默认 results/extraction_eval")
    parser.add_argument("--entity-schema", help="实体 schema JSON 路径")
    parser.add_argument("--relation-schema", help="关系 schema JSON 路径")
    parser.add_argument("--audit", help="audit 集 JSON 路径；不传则软指标为 None")
    parser.add_argument("--weights", help="权重覆盖文件（JSON）")
    parser.add_argument("--top-k", type=int, default=2, help="保留前 K 名候选")
    parser.add_argument("--overwrite", action="store_true", help="覆盖已有报告产物")
    return parser


def main(argv: Sequence[str] | None = None) -> int:
    args = _build_parser().parse_args(argv)
    weights = None
    if args.weights:
        weights = json.loads(Path(args.weights).read_text(encoding="utf-8"))
    summary = score_extraction_results(
        root=PROJECT_ROOT,
        eval_dir=args.eval_dir,
        entity_schema_path=args.entity_schema,
        relation_schema_path=args.relation_schema,
        audit_path=args.audit,
        weights=weights,
        top_k=args.top_k,
        overwrite=args.overwrite,
    )
    print(json.dumps(summary, ensure_ascii=False, indent=2))
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
```

- [ ] **Step 4: 运行端到端测试确认通过**

Run: `cd graphrag_pipeline && python -m pytest tests/test_score_extraction_results.py -v`
Expected: 1 passed

- [ ] **Step 5: 跑真实 4 候选产物做一次冒烟**

Run: `cd graphrag_pipeline && python scripts/score_extraction_results.py --overwrite`
Expected: 退出码 0，产出如下三文件：
```
results/reports/extraction_compare.csv
results/reports/extraction_compare.md
results/reports/top_candidates.json
```
并且 stdout 打印 `"total_candidates": 4`，`"top_candidates"` 至少含 2 个候选名。

- [ ] **Step 6: 提交**

```bash
git add graphrag_pipeline/scripts/score_extraction_results.py graphrag_pipeline/tests/test_score_extraction_results.py graphrag_pipeline/results/reports/
git commit -m "feat: 新增步骤 8 规则化评测 CLI 与首批真实候选报告"
```

---

### Task 10: 文档与运行说明更新

**Files:**
- Modify: `graphrag_pipeline/CLAUDE.md`
- Modify: `graphrag_pipeline/PROMPT_TUNING_PIPELINE.md`

- [ ] **Step 1: 在 CLAUDE.md 的「Prompt tuning helpers」代码块追加一行**

原位置：`graphrag_pipeline/CLAUDE.md` 中的 Prompt tuning helpers 代码块末尾。追加：

```bash
python scripts/score_extraction_results.py --overwrite
```

- [ ] **Step 2: 在 PROMPT_TUNING_PIPELINE.md「当前已落地脚本」段落追加第 5 条**

定位到 `run_graphrag_prompt_tune.py` 说明之后，插入：

```text
5. `score_extraction_results.py`
   - 读取 `results/extraction_eval/*.json`，对候选 Prompt 做规则化自动评测。
   - 计算 8 项硬指标 + 2 项 audit 软指标，输出 composite score、排序与 top-k。
   - 产物：`results/reports/extraction_compare.csv` / `.md`、`results/reports/top_candidates.json`。
```

- [ ] **Step 3: 运行文档审计（可选但推荐）**

Run: `python scripts/audit_repo_drift.py --strict`
Expected: 通过或仅产生已知噪声（不引入新的高优级漂移项）。

- [ ] **Step 4: 提交**

```bash
git add graphrag_pipeline/CLAUDE.md graphrag_pipeline/PROMPT_TUNING_PIPELINE.md
git commit -m "docs: 在调优流水线文档中固化步骤 8 评测脚本入口"
```

---

## Self-Review 记录

- **Spec coverage:**
  - ✅ 核心指标 8 项 → Task 1~5 一一实现
  - ✅ 结合 audit 集做轻量校准 → Task 6
  - ✅ `scripts/score_extraction_results.py` → Task 9
  - ✅ `results/reports/extraction_compare.csv` / `.md` / `top_candidates.json` → Task 8 写盘 + Task 9 调度
  - ✅ 自动排序 + top-k → `rank_candidates` + `select_top_k`（Task 7），CLI 参数 `--top-k`
  - ✅ 足够支撑版本比较 → CSV/MD 含所有指标 + composite score + rank + sample_count

- **Placeholder scan:** 无 TBD/TODO/"适当处理"等占位项；每个 step 都有完整代码或完整命令。

- **Type consistency:**
  - `StructuredExtractionResult` / `ExtractionEntity` / `ExtractionRelationship` 沿用 `extraction_schema.py` 现有定义。
  - `compute_*` 函数签名在 Task 7 `aggregate_candidate_metrics` 中统一调用，参数名一致。
  - CSV 列名与 md 表头、top_candidates.json 字段均从 `CSV_COLUMNS` 衍生，避免两处漂移。
  - `relation_schema` 始终是 raw dict（`relation_types.json.relation_types` 块），不与 `SchemaCatalog` 混用。

