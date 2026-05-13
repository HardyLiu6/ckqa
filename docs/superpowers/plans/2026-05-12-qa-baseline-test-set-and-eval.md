# QA Baseline 测试集与 LGB 全量 + DRIFT 抽样评测实施计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 基于现有 GraphRAG 查询产物（允许位于 `graphrag_pipeline/output/<index_run>/` 子目录），跳过重新索引阶段，建立 30~50 道课程问答测试集（覆盖事实定位 / 关系理解 / 章节总结 / 全局概览四类），并搭建一套「规则评分 + LLM 裁判」双层的评测框架：规则层算字面命中、引用格式合规、长度合理性与信息密度；裁判层用轻量 LLM 算 `semantic_correctness` 与 `faithfulness`，并基于 `gold_text_unit_ids` 计算 `retrieval_precision`。评测在运行前先写「假设清单」，运行后按「假设验证」格式给结论，把 GraphRAG 的 LGB（local / global / basic）全量回答与 DRIFT 抽样结果沉淀为对比报表，为后续 hybrid 模式提供基线与路由依据。

**Architecture:** 测试集落盘为 `graphrag_pipeline/data/eval/qa_test_set.jsonl`，由 Pydantic schema 校验。端到端运行前先检查目标索引目录的 parquet / LanceDB 完整性，并通过四模式 smoke query 证明当前产物可查询；本计划不执行 `graphrag index`。运行器通过 OpenAI 兼容协议直连本地 GraphRAG API，对每道题串行打 LGB 三个模型 id（`local / global / basic`）并把原始响应写入 `runs/<run_id>/raw/`；DRIFT 仅保留 smoke 或少量抽样 run，不并入同步全量 baseline。`run_baseline_eval` 通过 `--index-output-dir` 把本轮索引目录写入 `run_meta.json`，供后续 `judge_scorer` 自动解析 `text_units.parquet`。规则评分器读取 raw 目录，对照 gold 实体名、必引术语、负面术语、题型期望长度等，输出 `rule_scoring.*`。LLM 裁判模块共用同一套 OpenAI 兼容客户端但允许独立指定 `GRAPHRAG_JUDGE_MODEL`，对 `semantic_correctness`、`faithfulness` 各跑一遍，原始响应写入 `judge_raw/`，结果汇为 `judge_scoring.*`。报告生成器把两层合并，按 (题型 × 模式) 分组聚合并自动注入到 manual_review.md。

**Tech Stack:** Python 3.10+、Pydantic 2、pandas、requests、pytest。裁判 LLM 复用 GraphRAG 已有的 OpenAI 兼容 API；不引入新服务依赖。

**前置依赖：**

- `graphrag_pipeline/output/` 下已有完整查询产物：`entities.parquet` / `text_units.parquet` / `community_reports.parquet` / `documents.parquet` / `relationships.parquet` / `communities.parquet` / `stats.json`，以及 `output/lancedb/entity_description.lance`、`output/lancedb/text_unit_text.lance`、`output/lancedb/community_full_content.lance`。
- `prompts/final/active_prompt.json` 不是硬前置：如果存在，用其中候选名生成 `index_run_label`；如果不存在，用 `output/stats.json` 生成 `existing-output-docs<num_documents>@<date>` 标签。
- 本计划明确跳过索引阶段，不运行 `graphrag index`。若完整性检查或 smoke query 失败，停止并单独确认是否需要修复产物或重建索引。
- 本地能跑起来 `cd graphrag_pipeline && GRAPHRAG_OUTPUT_DIR=<index_output_dir> GRAPHRAG_STORAGE_DIR=<index_output_dir> GRAPHRAG_LANCEDB_URI=<index_output_dir>/lancedb python utils/main.py`，`/v1/models` 返回 4 个 GraphRAG 模型 id。
- 一台可访问的 OpenAI 兼容判官 LLM：默认走 `GRAPHRAG_API_BASE` 同一端点；`GRAPHRAG_JUDGE_MODEL` 单独指定（当前默认 `deepseek-v4-flash`，也可切到其他同等量级模型）。一次完整评测（30 题 × 4 模式 × 2 个裁判指标 ≈ 240 次调用，每次 ~600 tokens）预估 $0.5~$2。

**完成判据：**

1. `graphrag_pipeline/data/eval/qa_test_set.jsonl` 至少 30 条，四类问题均覆盖（每类 ≥ 6 条），`gold_text_unit_ids` 字段非空率 ≥ 80%。
2. `graphrag_pipeline/results/qa_eval/hypotheses.md` 在运行 baseline 前撰写完成，含 ≥ 4 条形如「[题类] 期望 [模式] 在 [指标] 上优于其他模式，理由 …」的可证伪假设。
3. baseline 运行前完成目标索引目录完整性检查与 local / global / drift / basic 四模式 smoke query；过程不执行 `graphrag index`。
4. `python -m graphrag_pipeline.scripts.qa_eval.run_baseline_eval --run-id <id> --index-output-dir <dir> --modes local global basic` 跑完全部题面 × 3 模式，产物在 `results/qa_eval/runs/<run_id>/raw/`；DRIFT 另存为 smoke / lite 抽样 run。
5. `python -m graphrag_pipeline.scripts.qa_eval.baseline_scorer --run-dir <run_dir>` 与 `python -m graphrag_pipeline.scripts.qa_eval.judge_scorer --run-dir <run_dir>` 都能跑完，分别产出 `rule_scoring.{csv,json,md}` 与 `judge_scoring.{csv,json,md}`。
6. `pytest graphrag_pipeline/tests/test_qa_eval_*.py -v` 全绿。
7. `manual_review.md` 总结段按「假设 H1：[验证 / 部分验证 / 不支持]，数据依据：…」格式填写，并给出至少 4 条形如「factual_lookup → local（依据 entity_hit_rate=0.X，semantic_correctness=0.X）」的路由建议。
8. `.gitignore` 显式覆盖 `graphrag_pipeline/results/qa_eval/runs/*/raw/`、`graphrag_pipeline/results/qa_eval/runs/*/judge_raw/`、`graphrag_pipeline/results/qa_eval/runs/*/scoring*.json`。

---

## File Structure

| 路径 | 责任 |
| --- | --- |
| `graphrag_pipeline/data/eval/qa_test_set.jsonl` | 测试集主文件 |
| `graphrag_pipeline/data/eval/qa_test_set_schema.md` | 题面字段、分类标准、撰写守则、评分字段说明 |
| `graphrag_pipeline/scripts/qa_eval/__init__.py` | 包入口 |
| `graphrag_pipeline/scripts/qa_eval/test_set_schema.py` | Pydantic 模型 `QaTestItem`、`QuestionCategory` |
| `graphrag_pipeline/scripts/qa_eval/test_set_validator.py` | jsonl 校验（含错误截断） |
| `graphrag_pipeline/scripts/qa_eval/graphrag_client.py` | OpenAI 兼容客户端 |
| `graphrag_pipeline/scripts/qa_eval/run_baseline_eval.py` | 跑四模式 |
| `graphrag_pipeline/scripts/qa_eval/category_thresholds.py` | 题型 → 长度区间（too_short / expected_min / expected_max / too_long） |
| `graphrag_pipeline/scripts/qa_eval/baseline_scorer.py` | 规则评分：字面命中、引用格式、长度合理性、信息密度 |
| `graphrag_pipeline/scripts/qa_eval/text_unit_lookup.py` | 从 text_units.parquet 加载 12 位前缀 → 全文 |
| `graphrag_pipeline/scripts/qa_eval/judge_prompts.py` | 裁判 LLM 的两个 prompt 模板 |
| `graphrag_pipeline/scripts/qa_eval/judge_scorer.py` | LLM 裁判：semantic_correctness、faithfulness、retrieval_precision |
| `graphrag_pipeline/scripts/qa_eval/baseline_reporter.py` | 合并规则+裁判分数，按 (题型 × 模式) 聚合 |
| `graphrag_pipeline/scripts/qa_eval/manual_review_template.py` | 生成 manual_review.md（含假设验证 + 量化槽位 + 路由建议槽位） |
| `graphrag_pipeline/results/qa_eval/hypotheses.md` | 评测前撰写的假设清单 |
| `graphrag_pipeline/results/qa_eval/runs/<run_id>/raw/` | 模型原始回答（不入库） |
| `graphrag_pipeline/results/qa_eval/runs/<run_id>/judge_raw/` | 裁判 LLM 原始响应（不入库） |
| `graphrag_pipeline/results/qa_eval/runs/<run_id>/{rule_scoring,judge_scoring}.{csv,md,json}` | 评分产物 |
| `graphrag_pipeline/results/qa_eval/runs/<run_id>/run_meta.json` | 含 modes 有序列表、judge_model、index_run_label |
| `graphrag_pipeline/results/qa_eval/runs/<run_id>/manual_review.md` | 人工复核 |
| `graphrag_pipeline/tests/test_qa_eval_*.py` | 单测 |
| `.gitignore` | 新增 raw / judge_raw / scoring*.json 忽略规则 |

---

### Task 1: Schema + validator

**Files:**
- Create: `graphrag_pipeline/scripts/qa_eval/__init__.py`
- Create: `graphrag_pipeline/scripts/qa_eval/test_set_schema.py`
- Create: `graphrag_pipeline/scripts/qa_eval/test_set_validator.py`
- Create: `graphrag_pipeline/tests/test_qa_eval_schema.py`

- [ ] **Step 1.1: 写 schema 失败测试**

写到 `graphrag_pipeline/tests/test_qa_eval_schema.py`：

```python
from __future__ import annotations

import json
from pathlib import Path

import pytest
from pydantic import ValidationError

from graphrag_pipeline.scripts.qa_eval.test_set_schema import (
    QaTestItem,
    QuestionCategory,
)
from graphrag_pipeline.scripts.qa_eval.test_set_validator import (
    ValidationReport,
    validate_jsonl,
)


def _make_valid_payload(**overrides):
    base = {
        "id": "Q001",
        "category": "factual_lookup",
        "question": "课程的第 1 章第 2 节标题是什么？",
        "gold_answer_summary": "数据采集与预处理",
        "gold_entities": ["数据采集与预处理"],
        "gold_text_unit_ids": ["d244f9016ac8"],
        "must_cite_terms": ["第 1 章", "第 2 节"],
        "negative_terms": [],
        "notes": "对应 documents.parquet 中 chapter=1, section=2",
    }
    base.update(overrides)
    return base


def test_qa_test_item_accepts_valid_payload():
    item = QaTestItem.model_validate(_make_valid_payload())
    assert item.category is QuestionCategory.FACTUAL_LOOKUP


def test_qa_test_item_id_pattern_rejects_too_long():
    payload = _make_valid_payload(id="Q123456")
    with pytest.raises(ValidationError):
        QaTestItem.model_validate(payload)


def test_qa_test_item_id_pattern_rejects_too_short():
    payload = _make_valid_payload(id="Q12")
    with pytest.raises(ValidationError):
        QaTestItem.model_validate(payload)


def test_qa_test_item_rejects_unknown_category():
    payload = _make_valid_payload(category="bogus_category")
    with pytest.raises(ValidationError):
        QaTestItem.model_validate(payload)


def test_qa_test_item_requires_question_min_length():
    payload = _make_valid_payload(question="?")
    with pytest.raises(ValidationError):
        QaTestItem.model_validate(payload)


def test_qa_test_item_normalizes_text_unit_ids_to_12_chars():
    payload = _make_valid_payload(
        gold_text_unit_ids=["  d244f9016ac84a55a7435cb6 ", "81d99ad61e36"]
    )
    item = QaTestItem.model_validate(payload)
    assert item.gold_text_unit_ids == ["d244f9016ac8", "81d99ad61e36"]


def test_validate_jsonl_flags_duplicate_id(tmp_path: Path):
    file = tmp_path / "set.jsonl"
    payload = _make_valid_payload()
    file.write_text(
        json.dumps(payload, ensure_ascii=False)
        + "\n"
        + json.dumps(payload, ensure_ascii=False)
        + "\n",
        encoding="utf-8",
    )
    report: ValidationReport = validate_jsonl(file)
    assert not report.ok
    assert any("Q001" in err for err in report.errors)


def test_validate_jsonl_truncates_errors_at_max(tmp_path: Path):
    file = tmp_path / "set.jsonl"
    payloads = []
    for i in range(40):
        # 故意每条都缺 category，制造 40 条错误
        bad = _make_valid_payload(id=f"Q{i:03d}")
        bad.pop("category")
        payloads.append(json.dumps(bad, ensure_ascii=False))
    file.write_text("\n".join(payloads) + "\n", encoding="utf-8")
    report = validate_jsonl(file, max_errors=10)
    assert not report.ok
    assert len(report.errors) <= 11  # 10 条 + 一条 truncation 提示
    assert any("及更多" in err for err in report.errors)


def test_validate_jsonl_flags_missing_category(tmp_path: Path):
    file = tmp_path / "set.jsonl"
    items = [
        _make_valid_payload(id=f"Q{i:03d}", category="factual_lookup")
        for i in range(8)
    ]
    file.write_text(
        "\n".join(json.dumps(it, ensure_ascii=False) for it in items)
        + "\n",
        encoding="utf-8",
    )
    report = validate_jsonl(file)
    assert not report.ok
    assert any("relation_reasoning" in err for err in report.errors)
```

- [ ] **Step 1.2: 跑测试确认失败**

Run: `pytest graphrag_pipeline/tests/test_qa_eval_schema.py -v`
Expected: 全部 fail，ImportError on `graphrag_pipeline.scripts.qa_eval.test_set_schema`。

- [ ] **Step 1.3: 写 schema 实现**

写到 `graphrag_pipeline/scripts/qa_eval/__init__.py`：

```python
"""QA evaluation harness for GraphRAG baseline modes."""
```

写到 `graphrag_pipeline/scripts/qa_eval/test_set_schema.py`：

```python
from __future__ import annotations

from enum import Enum
from typing import List, Optional

from pydantic import BaseModel, Field, field_validator


TEXT_UNIT_ID_PREFIX_LEN = 12


class QuestionCategory(str, Enum):
    FACTUAL_LOOKUP = "factual_lookup"
    RELATION_REASONING = "relation_reasoning"
    CHAPTER_SUMMARY = "chapter_summary"
    GLOBAL_OVERVIEW = "global_overview"


class QaTestItem(BaseModel):
    # id 格式：Q + 3~5 位数字。pattern 即终极约束，故不再加 min/max_length 重复约束。
    id: str = Field(pattern=r"^Q\d{3,5}$")
    category: QuestionCategory
    question: str = Field(min_length=4, max_length=400)
    gold_answer_summary: str = Field(min_length=2, max_length=600)
    gold_entities: List[str] = Field(default_factory=list)
    # 写入时取每个 id 的前 12 个字符（与 text_units.parquet 的 hash id 前缀对齐），
    # 用于 retrieval_precision 计算与 faithfulness 取证。
    gold_text_unit_ids: List[str] = Field(default_factory=list)
    must_cite_terms: List[str] = Field(default_factory=list)
    negative_terms: List[str] = Field(default_factory=list)
    notes: Optional[str] = Field(default=None, max_length=600)

    @field_validator("gold_entities", "must_cite_terms", "negative_terms")
    @classmethod
    def _strip_terms(cls, value: List[str]) -> List[str]:
        return [v.strip() for v in value if v and v.strip()]

    @field_validator("gold_text_unit_ids")
    @classmethod
    def _normalize_text_unit_ids(cls, value: List[str]) -> List[str]:
        out: List[str] = []
        for raw in value:
            if not raw:
                continue
            trimmed = raw.strip()
            if not trimmed:
                continue
            out.append(trimmed[:TEXT_UNIT_ID_PREFIX_LEN])
        return out
```

- [ ] **Step 1.4: 写 validator 实现**

写到 `graphrag_pipeline/scripts/qa_eval/test_set_validator.py`：

```python
from __future__ import annotations

import argparse
import json
import sys
from collections import Counter
from dataclasses import dataclass, field
from pathlib import Path
from typing import List

from pydantic import ValidationError

from graphrag_pipeline.scripts.qa_eval.test_set_schema import (
    QaTestItem,
    QuestionCategory,
)

MIN_PER_CATEGORY = 6
DEFAULT_MAX_ERRORS = 20


@dataclass
class ValidationReport:
    ok: bool
    total: int = 0
    by_category: Counter = field(default_factory=Counter)
    errors: List[str] = field(default_factory=list)


def validate_jsonl(path: Path, *, max_errors: int = DEFAULT_MAX_ERRORS) -> ValidationReport:
    report = ValidationReport(ok=True)
    seen_ids: set[str] = set()
    items: List[QaTestItem] = []
    raw_error_count = 0

    def push_error(msg: str) -> None:
        nonlocal raw_error_count
        raw_error_count += 1
        if len(report.errors) < max_errors:
            report.errors.append(msg)

    with path.open("r", encoding="utf-8") as fh:
        for line_no, raw in enumerate(fh, start=1):
            raw = raw.strip()
            if not raw:
                continue
            try:
                payload = json.loads(raw)
            except json.JSONDecodeError as exc:
                report.ok = False
                push_error(f"line {line_no}: invalid json - {exc}")
                continue
            try:
                item = QaTestItem.model_validate(payload)
            except ValidationError as exc:
                report.ok = False
                push_error(
                    f"line {line_no} ({payload.get('id', '?')}): {exc.errors()[0]}"
                )
                continue
            if item.id in seen_ids:
                report.ok = False
                push_error(f"line {line_no}: duplicate id {item.id}")
                continue
            seen_ids.add(item.id)
            items.append(item)

    report.total = len(items)
    report.by_category.update(it.category.value for it in items)
    for category in QuestionCategory:
        if report.by_category[category.value] < MIN_PER_CATEGORY:
            report.ok = False
            push_error(
                f"category {category.value} has only {report.by_category[category.value]} items, "
                f"need ≥ {MIN_PER_CATEGORY}"
            )

    truncated = raw_error_count - len(report.errors)
    if truncated > 0:
        report.errors.append(f"... 及更多 {truncated} 个错误未列出，请用 --max-errors 调大上限")

    return report


def _format_report(report: ValidationReport) -> str:
    lines = [f"total: {report.total}", "by_category:"]
    for category in QuestionCategory:
        lines.append(f"  {category.value}: {report.by_category[category.value]}")
    if report.errors:
        lines.append("errors:")
        lines.extend(f"  - {err}" for err in report.errors)
    return "\n".join(lines)


def main(argv: List[str] | None = None) -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("path", type=Path)
    parser.add_argument("--max-errors", type=int, default=DEFAULT_MAX_ERRORS)
    args = parser.parse_args(argv)
    report = validate_jsonl(args.path, max_errors=args.max_errors)
    print(_format_report(report))
    return 0 if report.ok else 1


if __name__ == "__main__":  # pragma: no cover
    sys.exit(main())
```

- [ ] **Step 1.5: 跑测试通过**

Run: `pytest graphrag_pipeline/tests/test_qa_eval_schema.py -v`
Expected: 8 PASS。

- [ ] **Step 1.6: 提交**

```bash
git add graphrag_pipeline/scripts/qa_eval/__init__.py \
        graphrag_pipeline/scripts/qa_eval/test_set_schema.py \
        graphrag_pipeline/scripts/qa_eval/test_set_validator.py \
        graphrag_pipeline/tests/test_qa_eval_schema.py
git commit -m "feat(qa_eval): 新增测试集 schema 与 jsonl validator（含错误截断）"
```

---

### Task 2: 撰写测试集 schema 文档与起始 8 题骨架

**Files:**
- Create: `graphrag_pipeline/data/eval/qa_test_set_schema.md`
- Create: `graphrag_pipeline/data/eval/qa_test_set.jsonl`

- [ ] **Step 2.1: 写 schema 文档**

写到 `graphrag_pipeline/data/eval/qa_test_set_schema.md`：

````markdown
# 课程问答测试集说明

## 1. 用途

本测试集用于评测 GraphRAG 在「智课问答」场景下的四种检索模式（local / global / drift / basic）以及后续 hybrid 模式的回答质量。题目对应 `graphrag_pipeline/output/` 下当前已经存在的查询产物，本计划默认**复用现有 output，跳过重新索引**；只有当换课、替换 `output/` 或确认重建索引后，才需要重新撰写题面，不要跨课程沿用。

## 2. 字段约束

| 字段 | 类型 | 必填 | 评分用途 | 说明 |
| --- | --- | --- | --- | --- |
| `id` | string | ✅ | — | 形如 `Q001`，3~5 位数字。题目超过 999 条时升至 5 位（`Q00001`） |
| `category` | enum | ✅ | 全部指标按题型分组 | `factual_lookup` / `relation_reasoning` / `chapter_summary` / `global_overview` |
| `question` | string | ✅ | — | 4~400 字，学生口吻 |
| `gold_answer_summary` | string | ✅ | 裁判 LLM 的参考答案 | 1~2 句话总结正确答案，不要复述整段原文 |
| `gold_entities` | string[] | 强烈建议 | `entity_hit_rate`（规则） | 答案必须出现的实体名（章节、术语、公式名等） |
| `gold_text_unit_ids` | string[] | 强烈建议 | `retrieval_precision` + `faithfulness`（裁判） | `text_units.parquet` 中相关 `id` 的**前 12 位**。**非空率需 ≥ 80%**，否则评测会失去检索维度信号 |
| `must_cite_terms` | string[] |  | `must_cite_hit`（规则） | 答案中必须出现的字面 token |
| `negative_terms` | string[] |  | `negative_hit`（规则） | 一旦出现就说明跑偏 / 幻觉的 token |
| `notes` | string |  | — | 出题人备注，可写来源 PDF 页码 |

## 3. 四类题型撰写守则

### 3.1 factual_lookup（事实定位题）

- 直接问课程里的事实：章节标题、定义、年份、公式名、缩写全称。
- 答案在原文里能 ctrl-F 找到。
- `gold_entities` 通常 1~2 个，`must_cite_terms` 写章节号或编号。
- **期望长度** 80~250 字。

例：
```json
{"id":"Q001","category":"factual_lookup","question":"DBSCAN 算法的两个核心超参数是什么？","gold_answer_summary":"eps 和 MinPts","gold_entities":["DBSCAN","eps","MinPts"],"gold_text_unit_ids":["d244f9016ac8"],"must_cite_terms":["eps","MinPts"]}
```

### 3.2 relation_reasoning（关系理解题）

- 问两个或多个概念 / 实体的关系。
- 答案至少跨 2 个 text_unit 或 1 个 community 节点。
- **期望长度** 150~450 字。

### 3.3 chapter_summary（章节总结题）

- 要求对一个章 / 节做提纲式归纳，3~5 个要点。
- 答案跨整章 text_unit，`gold_entities` 至少 3 个。
- **期望长度** 300~800 字。

### 3.4 global_overview（全局概览题）

- 跨章节的综合问题。
- 答案需要触达 community_reports 才能答完整。
- **期望长度** 300~700 字。

## 4. 撰写流程

1. 先确认 `graphrag_pipeline/output/` 是本轮要评测的现有产物，并且不要在出题过程中运行 `graphrag index`。
2. 打开 `graphrag_pipeline/output/documents.parquet` 与 `text_units.parquet`，列出章节结构。
3. 按 4 类题型各出 8~12 题，保证均衡（每类 ≥ 6）。
4. 把答案对应的 text_units.parquet `id` 前 12 位填入 `gold_text_unit_ids`，建议每题填 1~3 条。
5. 执行：

```bash
python -m graphrag_pipeline.scripts.qa_eval.test_set_validator \
    graphrag_pipeline/data/eval/qa_test_set.jsonl
```

   全部通过才算定稿。

6. 若 validator 报某类不足，**补题**而不是降阈值。

## 5. 指标-字段对照速查

| 评测层 | 指标 | 来源字段 | 备注 |
| --- | --- | --- | --- |
| 规则 | `entity_hit_rate` | `gold_entities` | 字面子串匹配，作为词汇命中信号 |
| 规则 | `must_cite_hit` | `must_cite_terms` | 全部命中得 1，否则 0 |
| 规则 | `citation_format_present` | — | 仅检测 `[Data:` / `[来源:` / `[出处` 格式是否出现；不代表答案真有依据 |
| 规则 | `negative_hit` | `negative_terms` | 越低越好 |
| 规则 | `length_score` | `category` | 在期望区间记 1.0，临界 0.5，越界 0.0 |
| 规则 | `info_density` | `gold_entities` + 答案字符数 | hit 数 / 答案字符数 × 1000，跨题型方向一致（越高越好） |
| 裁判 | `semantic_correctness` | `gold_answer_summary` | 0 / 0.5 / 1 |
| 裁判 | `faithfulness` | `gold_text_unit_ids` 取出的原文 | 0 / 1 |
| 裁判 | `retrieval_precision` | `gold_text_unit_ids` | 命中数 / gold 数 |
````

- [ ] **Step 2.2: 落地起始 8 题骨架**

写到 `graphrag_pipeline/data/eval/qa_test_set.jsonl`：

```jsonl
{"id":"Q001","category":"factual_lookup","question":"<请替换：直接问章节标题/定义/公式名/年份>","gold_answer_summary":"<参考答案 1~2 句>","gold_entities":["<gold实体1>"],"gold_text_unit_ids":[],"must_cite_terms":["<必须出现的字面 token>"],"negative_terms":[],"notes":"factual_lookup 起始模板，待替换"}
{"id":"Q002","category":"factual_lookup","question":"<请替换>","gold_answer_summary":"<参考答案>","gold_entities":[],"gold_text_unit_ids":[],"must_cite_terms":[],"negative_terms":[],"notes":""}
{"id":"Q003","category":"relation_reasoning","question":"<请替换：A 与 B 在 X 维度的差别 / A 是 B 的什么>","gold_answer_summary":"<参考答案>","gold_entities":[],"gold_text_unit_ids":[],"must_cite_terms":[],"negative_terms":[],"notes":""}
{"id":"Q004","category":"relation_reasoning","question":"<请替换>","gold_answer_summary":"<参考答案>","gold_entities":[],"gold_text_unit_ids":[],"must_cite_terms":[],"negative_terms":[],"notes":""}
{"id":"Q005","category":"chapter_summary","question":"<请替换：请总结第 X 章 ...>","gold_answer_summary":"<参考答案>","gold_entities":[],"gold_text_unit_ids":[],"must_cite_terms":["第"],"negative_terms":[],"notes":""}
{"id":"Q006","category":"chapter_summary","question":"<请替换>","gold_answer_summary":"<参考答案>","gold_entities":[],"gold_text_unit_ids":[],"must_cite_terms":[],"negative_terms":[],"notes":""}
{"id":"Q007","category":"global_overview","question":"<请替换：这门课程贯穿始终的方法论主线是什么？>","gold_answer_summary":"<参考答案>","gold_entities":[],"gold_text_unit_ids":[],"must_cite_terms":[],"negative_terms":[],"notes":""}
{"id":"Q008","category":"global_overview","question":"<请替换>","gold_answer_summary":"<参考答案>","gold_entities":[],"gold_text_unit_ids":[],"must_cite_terms":[],"negative_terms":[],"notes":""}
```

> 骨架只为占位，**正式撰写 30+ 题在 Task 10 完成**。

- [ ] **Step 2.3: 跑 validator（骨架阶段允许 fail）**

Run: `python -m graphrag_pipeline.scripts.qa_eval.test_set_validator graphrag_pipeline/data/eval/qa_test_set.jsonl`
Expected: 因每类 < 6 条而 fail；只需确认 validator 走通。

- [ ] **Step 2.4: 提交**

```bash
git add graphrag_pipeline/data/eval/qa_test_set_schema.md \
        graphrag_pipeline/data/eval/qa_test_set.jsonl
git commit -m "feat(qa_eval): 新增测试集 schema 文档与起始 8 题骨架"
```

---

### Task 3: GraphRAG 兼容客户端

**Files:**
- Create: `graphrag_pipeline/scripts/qa_eval/graphrag_client.py`
- Create: `graphrag_pipeline/tests/test_qa_eval_client.py`

> 该客户端同时被 baseline runner 与 judge_scorer 使用，因此抽象层包装为「按 model id 路由」。

- [ ] **Step 3.1: 写客户端失败测试**

写到 `graphrag_pipeline/tests/test_qa_eval_client.py`：

```python
from __future__ import annotations

import json
from unittest.mock import patch

import pytest
import requests

from graphrag_pipeline.scripts.qa_eval.graphrag_client import (
    GraphRagApiError,
    OpenAICompatibleClient,
    QueryResult,
)


class _FakeResponse:
    def __init__(self, status_code: int, payload: dict):
        self.status_code = status_code
        self._payload = payload
        self.text = json.dumps(payload)

    def json(self) -> dict:
        return self._payload

    def raise_for_status(self) -> None:
        if self.status_code >= 400:
            raise requests.HTTPError(self.text)


def test_client_returns_answer_text():
    payload = {
        "choices": [
            {"message": {"role": "assistant", "content": "DBSCAN 的核心超参数是 eps 和 MinPts。"}}
        ],
        "usage": {"total_tokens": 128},
    }
    with patch.object(requests.Session, "post", return_value=_FakeResponse(200, payload)) as mock:
        client = OpenAICompatibleClient(
            base_url="http://127.0.0.1:8000",
            request_timeout_seconds=5,
            allow_arbitrary_models=False,
        )
        result: QueryResult = client.query(model="graphrag-local-search:latest", prompt="测试")
        assert result.answer.startswith("DBSCAN")
        assert result.total_tokens == 128
        assert mock.call_count == 1


def test_client_raises_after_max_retries():
    with patch.object(requests.Session, "post", return_value=_FakeResponse(500, {"error": "boom"})):
        client = OpenAICompatibleClient(
            base_url="http://127.0.0.1:8000",
            request_timeout_seconds=5,
            max_retries=2,
            backoff_seconds=0.0,
            allow_arbitrary_models=False,
        )
        with pytest.raises(GraphRagApiError):
            client.query(model="graphrag-local-search:latest", prompt="测试")


def test_client_rejects_unknown_graphrag_model():
    client = OpenAICompatibleClient(
        base_url="http://127.0.0.1:8000",
        allow_arbitrary_models=False,
    )
    with pytest.raises(ValueError):
        client.query(model="not-a-real-model", prompt="测试")


def test_client_allows_arbitrary_judge_models():
    payload = {
        "choices": [{"message": {"role": "assistant", "content": "{\"score\":1}"}}],
        "usage": {"total_tokens": 50},
    }
    with patch.object(requests.Session, "post", return_value=_FakeResponse(200, payload)):
        judge_client = OpenAICompatibleClient(
            base_url="http://judge.example.com",
            request_timeout_seconds=10,
            allow_arbitrary_models=True,
        )
        result = judge_client.query(model="deepseek-v4-flash", prompt="...", api_key="sk-test")
        assert result.answer == "{\"score\":1}"
```

- [ ] **Step 3.2: 跑测试确认失败**

Run: `pytest graphrag_pipeline/tests/test_qa_eval_client.py -v`
Expected: ImportError。

- [ ] **Step 3.3: 写客户端实现**

写到 `graphrag_pipeline/scripts/qa_eval/graphrag_client.py`：

```python
from __future__ import annotations

import logging
import time
from dataclasses import dataclass
from typing import Optional

import requests

LOGGER = logging.getLogger(__name__)

SUPPORTED_GRAPHRAG_MODELS = frozenset(
    {
        "graphrag-local-search:latest",
        "graphrag-global-search:latest",
        "graphrag-drift-search:latest",
        "graphrag-basic-search:latest",
    }
)


class GraphRagApiError(RuntimeError):
    pass


@dataclass(frozen=True, slots=True)
class QueryResult:
    answer: str
    total_tokens: Optional[int]
    elapsed_seconds: float
    raw: dict


class OpenAICompatibleClient:
    def __init__(
        self,
        *,
        base_url: str = "http://127.0.0.1:8000",
        request_timeout_seconds: float = 120.0,
        max_retries: int = 3,
        backoff_seconds: float = 2.0,
        allow_arbitrary_models: bool = False,
    ) -> None:
        self._base_url = base_url.rstrip("/")
        self._timeout = request_timeout_seconds
        self._max_retries = max_retries
        self._backoff = backoff_seconds
        self._allow_arbitrary = allow_arbitrary_models
        self._session = requests.Session()

    def query(
        self,
        *,
        model: str,
        prompt: str,
        api_key: Optional[str] = None,
        temperature: float = 0.0,
    ) -> QueryResult:
        if not self._allow_arbitrary and model not in SUPPORTED_GRAPHRAG_MODELS:
            raise ValueError(f"unsupported model: {model}")

        url = f"{self._base_url}/v1/chat/completions"
        payload = {
            "model": model,
            "messages": [{"role": "user", "content": prompt}],
            "temperature": temperature,
            "stream": False,
        }
        headers: dict[str, str] = {}
        if api_key:
            headers["Authorization"] = f"Bearer {api_key}"

        last_error: Optional[str] = None
        started = time.monotonic()
        for attempt in range(1, self._max_retries + 1):
            try:
                response = self._session.post(
                    url, json=payload, headers=headers, timeout=self._timeout
                )
                response.raise_for_status()
                body = response.json()
                answer = body["choices"][0]["message"]["content"]
                usage = body.get("usage") or {}
                return QueryResult(
                    answer=answer,
                    total_tokens=usage.get("total_tokens"),
                    elapsed_seconds=time.monotonic() - started,
                    raw=body,
                )
            except (requests.RequestException, KeyError, ValueError) as exc:
                last_error = str(exc)
                LOGGER.warning(
                    "query failed model=%s attempt=%d error=%s",
                    model,
                    attempt,
                    last_error,
                )
                if attempt < self._max_retries:
                    time.sleep(self._backoff * attempt)

        raise GraphRagApiError(
            f"query failed after {self._max_retries} attempts: {last_error}"
        )


# 向后兼容别名：基线 runner 旧叫法
GraphRagClient = OpenAICompatibleClient
```

- [ ] **Step 3.4: 跑测试通过**

Run: `pytest graphrag_pipeline/tests/test_qa_eval_client.py -v`
Expected: 4 PASS。

- [ ] **Step 3.5: 提交**

```bash
git add graphrag_pipeline/scripts/qa_eval/graphrag_client.py \
        graphrag_pipeline/tests/test_qa_eval_client.py
git commit -m "feat(qa_eval): 新增 OpenAI 兼容客户端（裁判模型可选）"
```

---

### Task 4: Baseline 评测运行器

**Files:**
- Create: `graphrag_pipeline/scripts/qa_eval/run_baseline_eval.py`
- Create: `graphrag_pipeline/tests/test_qa_eval_runner.py`

`run_meta.json` 必须显式记录 `modes` 顺序，下游 scorer / reporter 直接读它而不是从某条 raw json 里推。

- [ ] **Step 4.1: 写运行器失败测试**

写到 `graphrag_pipeline/tests/test_qa_eval_runner.py`：

```python
from __future__ import annotations

import json
from pathlib import Path
from unittest.mock import MagicMock

import pytest

from graphrag_pipeline.scripts.qa_eval.graphrag_client import QueryResult
from graphrag_pipeline.scripts.qa_eval.run_baseline_eval import (
    BASELINE_MODES,
    BaselineRunner,
    RunMetadata,
)
from graphrag_pipeline.scripts.qa_eval.test_set_schema import QaTestItem


def _make_item(idx: int, category: str = "factual_lookup") -> QaTestItem:
    return QaTestItem.model_validate(
        {
            "id": f"Q{idx:03d}",
            "category": category,
            "question": f"测试问题 {idx}",
            "gold_answer_summary": "示例答案",
            "gold_entities": ["示例"],
        }
    )


def test_runner_writes_per_question_artifacts_and_meta(tmp_path: Path):
    items = [_make_item(1), _make_item(2, category="global_overview")]

    fake_client = MagicMock()
    fake_client.query.side_effect = [
        QueryResult(answer=f"answer-{m}-{i}", total_tokens=10, elapsed_seconds=0.1, raw={})
        for i in range(1, 3)
        for m in BASELINE_MODES
    ]

    runner = BaselineRunner(
        client=fake_client,
        output_root=tmp_path,
        run_id="rid",
        index_run_label="course-x@2026-05-12",
    )
    metadata = runner.run(items)

    raw_dir = tmp_path / "rid" / "raw"
    assert (raw_dir / "Q001.json").exists()
    payload = json.loads((raw_dir / "Q001.json").read_text(encoding="utf-8"))
    assert list(payload["modes"].keys()) == list(BASELINE_MODES)
    assert isinstance(metadata, RunMetadata)
    assert metadata.total_items == 2

    meta = json.loads((tmp_path / "rid" / "run_meta.json").read_text(encoding="utf-8"))
    assert meta["modes"] == list(BASELINE_MODES)
    assert meta["index_run_label"] == "course-x@2026-05-12"


def test_runner_records_errors_and_continues(tmp_path: Path):
    from graphrag_pipeline.scripts.qa_eval.graphrag_client import GraphRagApiError

    items = [_make_item(1)]

    fake_client = MagicMock()
    fake_client.query.side_effect = [
        QueryResult(answer="ok-local", total_tokens=1, elapsed_seconds=0.1, raw={}),
        GraphRagApiError("boom"),
        QueryResult(answer="ok-drift", total_tokens=1, elapsed_seconds=0.1, raw={}),
        QueryResult(answer="ok-basic", total_tokens=1, elapsed_seconds=0.1, raw={}),
    ]

    runner = BaselineRunner(
        client=fake_client,
        output_root=tmp_path,
        run_id="rid",
        index_run_label="course-x",
    )
    runner.run(items)

    payload = json.loads((tmp_path / "rid" / "raw" / "Q001.json").read_text(encoding="utf-8"))
    assert payload["modes"]["graphrag-global-search:latest"]["error"]
    assert payload["modes"]["graphrag-local-search:latest"]["answer"] == "ok-local"
```

- [ ] **Step 4.2: 跑测试确认失败**

Run: `pytest graphrag_pipeline/tests/test_qa_eval_runner.py -v`
Expected: ImportError。

- [ ] **Step 4.3: 写运行器实现**

写到 `graphrag_pipeline/scripts/qa_eval/run_baseline_eval.py`：

```python
from __future__ import annotations

import argparse
import json
import logging
import sys
import time
from dataclasses import asdict, dataclass
from datetime import datetime, timezone
from pathlib import Path
from typing import Iterable, List, Sequence

from graphrag_pipeline.scripts.qa_eval.graphrag_client import (
    GraphRagApiError,
    OpenAICompatibleClient,
    QueryResult,
)
from graphrag_pipeline.scripts.qa_eval.test_set_schema import QaTestItem
from graphrag_pipeline.scripts.qa_eval.test_set_validator import validate_jsonl

LOGGER = logging.getLogger(__name__)

BASELINE_MODES: Sequence[str] = (
    "graphrag-local-search:latest",
    "graphrag-global-search:latest",
    "graphrag-drift-search:latest",
    "graphrag-basic-search:latest",
)


@dataclass(frozen=True, slots=True)
class RunMetadata:
    run_id: str
    index_run_label: str
    total_items: int
    started_at: str
    finished_at: str
    modes: List[str]


class BaselineRunner:
    def __init__(
        self,
        *,
        client: OpenAICompatibleClient,
        output_root: Path,
        run_id: str,
        index_run_label: str,
        modes: Sequence[str] = BASELINE_MODES,
    ) -> None:
        self._client = client
        self._run_dir = output_root / run_id
        self._raw_dir = self._run_dir / "raw"
        self._raw_dir.mkdir(parents=True, exist_ok=True)
        self._run_id = run_id
        self._index_run_label = index_run_label
        self._modes: tuple[str, ...] = tuple(modes)

    def run(self, items: Iterable[QaTestItem]) -> RunMetadata:
        started_at = datetime.now(tz=timezone.utc).isoformat()
        items = list(items)
        for item in items:
            self._run_one(item)
        finished_at = datetime.now(tz=timezone.utc).isoformat()
        metadata = RunMetadata(
            run_id=self._run_id,
            index_run_label=self._index_run_label,
            total_items=len(items),
            started_at=started_at,
            finished_at=finished_at,
            modes=list(self._modes),
        )
        (self._run_dir / "run_meta.json").write_text(
            json.dumps(asdict(metadata), ensure_ascii=False, indent=2),
            encoding="utf-8",
        )
        return metadata

    def _run_one(self, item: QaTestItem) -> None:
        per_mode: dict[str, dict] = {}
        for mode in self._modes:
            LOGGER.info("running %s @ %s", item.id, mode)
            try:
                result: QueryResult = self._client.query(model=mode, prompt=item.question)
            except GraphRagApiError as exc:
                per_mode[mode] = {"error": str(exc)}
                continue
            per_mode[mode] = {
                "answer": result.answer,
                "total_tokens": result.total_tokens,
                "elapsed_seconds": result.elapsed_seconds,
            }
        out = {
            "id": item.id,
            "category": item.category.value,
            "question": item.question,
            "gold_answer_summary": item.gold_answer_summary,
            "gold_entities": item.gold_entities,
            "gold_text_unit_ids": item.gold_text_unit_ids,
            "must_cite_terms": item.must_cite_terms,
            "negative_terms": item.negative_terms,
            "modes": per_mode,
        }
        (self._raw_dir / f"{item.id}.json").write_text(
            json.dumps(out, ensure_ascii=False, indent=2),
            encoding="utf-8",
        )


def _load_items(path: Path) -> List[QaTestItem]:
    report = validate_jsonl(path)
    if not report.ok:
        raise SystemExit("\n".join(["test set validation failed:", *report.errors]))
    items: List[QaTestItem] = []
    for line in path.read_text(encoding="utf-8").splitlines():
        line = line.strip()
        if not line:
            continue
        items.append(QaTestItem.model_validate(json.loads(line)))
    return items


def main(argv: List[str] | None = None) -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument(
        "--test-set",
        type=Path,
        default=Path("graphrag_pipeline/data/eval/qa_test_set.jsonl"),
    )
    parser.add_argument(
        "--output-root",
        type=Path,
        default=Path("graphrag_pipeline/results/qa_eval/runs"),
    )
    parser.add_argument(
        "--run-id",
        default=time.strftime("%Y%m%d-%H%M%S"),
    )
    parser.add_argument(
        "--index-run-label",
        required=True,
    )
    parser.add_argument("--base-url", default="http://127.0.0.1:8000")
    parser.add_argument("--request-timeout", type=float, default=180.0)
    parser.add_argument("--max-retries", type=int, default=3)
    args = parser.parse_args(argv)

    logging.basicConfig(level=logging.INFO, format="%(asctime)s %(levelname)s %(message)s")
    items = _load_items(args.test_set)
    client = OpenAICompatibleClient(
        base_url=args.base_url,
        request_timeout_seconds=args.request_timeout,
        max_retries=args.max_retries,
        allow_arbitrary_models=False,
    )
    runner = BaselineRunner(
        client=client,
        output_root=args.output_root,
        run_id=args.run_id,
        index_run_label=args.index_run_label,
    )
    metadata = runner.run(items)
    print(json.dumps(asdict(metadata), ensure_ascii=False, indent=2))
    return 0


if __name__ == "__main__":  # pragma: no cover
    sys.exit(main())
```

- [ ] **Step 4.4: 跑测试通过**

Run: `pytest graphrag_pipeline/tests/test_qa_eval_runner.py -v`
Expected: 2 PASS。

- [ ] **Step 4.5: 提交**

```bash
git add graphrag_pipeline/scripts/qa_eval/run_baseline_eval.py \
        graphrag_pipeline/tests/test_qa_eval_runner.py
git commit -m "feat(qa_eval): 新增四模式 baseline 评测运行器"
```

---

### Task 5: 规则评分器

**Files:**
- Create: `graphrag_pipeline/scripts/qa_eval/category_thresholds.py`
- Create: `graphrag_pipeline/scripts/qa_eval/baseline_scorer.py`
- Create: `graphrag_pipeline/tests/test_qa_eval_scorer.py`

打分维度：

| 指标 | 取值 | 说明 |
| --- | --- | --- |
| `entity_hit_rate` | 0~1 | `gold_entities` 字面命中比例（词汇信号，不能等同语义） |
| `must_cite_hit` | 0/1 | `must_cite_terms` 全部出现记 1 |
| `citation_format_present` | 0/1 | 检测 `[Data:` / `[来源:` / `[出处` 格式；**与是否有真实依据无关** |
| `negative_hit` | 0/1 | `negative_terms` 至少出现一项记 1（越低越好） |
| `answer_chars` | int | 原始字符数（仅作为诊断变量） |
| `length_score` | 0/0.5/1 | 按题型期望区间打分 |
| `info_density` | float | `entity_hit_count / max(answer_chars, 1) × 1000` |

mode 顺序：从 `run_meta.json` 的 `modes` 字段读取，**而不是**从某条 raw json 里推。

- [ ] **Step 5.1: 写题型阈值**

写到 `graphrag_pipeline/scripts/qa_eval/category_thresholds.py`：

```python
from __future__ import annotations

from dataclasses import dataclass
from typing import Mapping

from graphrag_pipeline.scripts.qa_eval.test_set_schema import QuestionCategory


@dataclass(frozen=True, slots=True)
class LengthThreshold:
    too_short: int
    expected_min: int
    expected_max: int
    too_long: int


CATEGORY_LENGTH_THRESHOLDS: Mapping[QuestionCategory, LengthThreshold] = {
    QuestionCategory.FACTUAL_LOOKUP: LengthThreshold(20, 80, 250, 400),
    QuestionCategory.RELATION_REASONING: LengthThreshold(60, 150, 450, 600),
    QuestionCategory.CHAPTER_SUMMARY: LengthThreshold(120, 300, 800, 1200),
    QuestionCategory.GLOBAL_OVERVIEW: LengthThreshold(120, 300, 700, 1000),
}


def length_score(category: QuestionCategory, chars: int) -> float:
    t = CATEGORY_LENGTH_THRESHOLDS[category]
    if chars <= t.too_short or chars >= t.too_long:
        return 0.0
    if t.expected_min <= chars <= t.expected_max:
        return 1.0
    return 0.5
```

- [ ] **Step 5.2: 写 scorer 失败测试**

写到 `graphrag_pipeline/tests/test_qa_eval_scorer.py`：

```python
from __future__ import annotations

import json
from pathlib import Path

import pytest

from graphrag_pipeline.scripts.qa_eval.baseline_scorer import (
    PER_QUESTION_METRICS,
    ScoringSummary,
    score_baseline_run,
)
from graphrag_pipeline.scripts.qa_eval.run_baseline_eval import BASELINE_MODES


def _write_item(raw_dir: Path, qid: str, payload: dict) -> None:
    raw_dir.mkdir(parents=True, exist_ok=True)
    (raw_dir / f"{qid}.json").write_text(
        json.dumps(payload, ensure_ascii=False, indent=2),
        encoding="utf-8",
    )


def _write_meta(run_dir: Path, modes: list[str]) -> None:
    (run_dir / "run_meta.json").write_text(
        json.dumps(
            {
                "run_id": run_dir.name,
                "index_run_label": "course-x",
                "total_items": 1,
                "modes": modes,
            },
            ensure_ascii=False,
            indent=2,
        ),
        encoding="utf-8",
    )


def test_score_baseline_run_computes_entity_hit_and_citation_format(tmp_path: Path):
    run_dir = tmp_path / "rid"
    _write_item(
        run_dir / "raw",
        "Q001",
        {
            "id": "Q001",
            "category": "factual_lookup",
            "question": "DBSCAN 的两个核心超参数是什么？",
            "gold_answer_summary": "eps 和 MinPts",
            "gold_entities": ["DBSCAN", "eps", "MinPts"],
            "gold_text_unit_ids": [],
            "must_cite_terms": ["eps", "MinPts"],
            "negative_terms": ["KMeans"],
            "modes": {
                "graphrag-local-search:latest": {
                    "answer": "DBSCAN 的两个核心超参数是 eps 和 MinPts [Data: Entities (12)]。",
                    "total_tokens": 50,
                    "elapsed_seconds": 1.2,
                },
                "graphrag-global-search:latest": {
                    "answer": "DBSCAN 是一种聚类算法，与 KMeans 不同。",
                    "total_tokens": 60,
                    "elapsed_seconds": 1.5,
                },
                "graphrag-drift-search:latest": {"error": "timeout"},
                "graphrag-basic-search:latest": {
                    "answer": "eps 和 MinPts。",
                    "total_tokens": 20,
                    "elapsed_seconds": 0.8,
                },
            },
        },
    )
    _write_meta(run_dir, list(BASELINE_MODES))

    summary: ScoringSummary = score_baseline_run(run_dir)

    local = summary.per_question["Q001"]["graphrag-local-search:latest"]
    assert local["entity_hit_rate"] == 1.0
    assert local["must_cite_hit"] == 1
    assert local["citation_format_present"] == 1
    assert local["negative_hit"] == 0
    assert "info_density" in local
    assert "length_score" in local

    global_row = summary.per_question["Q001"]["graphrag-global-search:latest"]
    assert global_row["entity_hit_rate"] < 1.0
    assert global_row["negative_hit"] == 1
    assert global_row["citation_format_present"] == 0

    drift_row = summary.per_question["Q001"]["graphrag-drift-search:latest"]
    assert drift_row["error"] is True


def test_modes_order_from_run_meta(tmp_path: Path):
    run_dir = tmp_path / "rid"
    _write_item(
        run_dir / "raw",
        "Q001",
        {
            "id": "Q001",
            "category": "factual_lookup",
            "question": "?",
            "gold_answer_summary": "a",
            "gold_entities": ["foo"],
            "must_cite_terms": [],
            "negative_terms": [],
            # 故意把 modes 顺序写反
            "modes": {
                "graphrag-basic-search:latest": {"answer": "foo", "total_tokens": 1, "elapsed_seconds": 0.1},
                "graphrag-global-search:latest": {"answer": "bar", "total_tokens": 1, "elapsed_seconds": 0.1},
                "graphrag-drift-search:latest": {"answer": "foo", "total_tokens": 1, "elapsed_seconds": 0.1},
                "graphrag-local-search:latest": {"answer": "foo", "total_tokens": 1, "elapsed_seconds": 0.1},
            },
        },
    )
    _write_meta(run_dir, list(BASELINE_MODES))
    summary = score_baseline_run(run_dir)
    assert summary.modes == list(BASELINE_MODES)


def test_length_score_uses_category_thresholds(tmp_path: Path):
    run_dir = tmp_path / "rid"
    _write_item(
        run_dir / "raw",
        "Q001",
        {
            "id": "Q001",
            "category": "factual_lookup",
            "question": "?",
            "gold_answer_summary": "a",
            "gold_entities": ["foo"],
            "must_cite_terms": [],
            "negative_terms": [],
            "modes": {
                "graphrag-local-search:latest": {
                    "answer": "foo " * 50,  # ~ 200 chars，处于 factual_lookup 期望区间
                    "total_tokens": 50,
                    "elapsed_seconds": 0.1,
                },
                "graphrag-global-search:latest": {
                    "answer": "foo",  # 太短
                    "total_tokens": 1,
                    "elapsed_seconds": 0.1,
                },
                "graphrag-drift-search:latest": {
                    "answer": "x" * 800,  # 超长
                    "total_tokens": 800,
                    "elapsed_seconds": 0.1,
                },
                "graphrag-basic-search:latest": {
                    "answer": "x" * 260,  # 略超期望但未到 too_long
                    "total_tokens": 260,
                    "elapsed_seconds": 0.1,
                },
            },
        },
    )
    _write_meta(run_dir, list(BASELINE_MODES))
    summary = score_baseline_run(run_dir)
    assert summary.per_question["Q001"]["graphrag-local-search:latest"]["length_score"] == 1.0
    assert summary.per_question["Q001"]["graphrag-global-search:latest"]["length_score"] == 0.0
    assert summary.per_question["Q001"]["graphrag-drift-search:latest"]["length_score"] == 0.0
    assert summary.per_question["Q001"]["graphrag-basic-search:latest"]["length_score"] == 0.5


def test_per_mode_aggregation_groups_by_category(tmp_path: Path):
    run_dir = tmp_path / "rid"
    _write_item(
        run_dir / "raw",
        "Q001",
        {
            "id": "Q001",
            "category": "factual_lookup",
            "question": "?",
            "gold_answer_summary": "a",
            "gold_entities": ["foo"],
            "must_cite_terms": [],
            "negative_terms": [],
            "modes": {
                m: {"answer": "foo", "total_tokens": 1, "elapsed_seconds": 0.1}
                for m in BASELINE_MODES
            },
        },
    )
    _write_item(
        run_dir / "raw",
        "Q002",
        {
            "id": "Q002",
            "category": "global_overview",
            "question": "?",
            "gold_answer_summary": "a",
            "gold_entities": ["bar"],
            "must_cite_terms": [],
            "negative_terms": [],
            "modes": {
                m: {"answer": "bar", "total_tokens": 1, "elapsed_seconds": 0.1}
                for m in BASELINE_MODES
            },
        },
    )
    _write_meta(run_dir, list(BASELINE_MODES))
    summary = score_baseline_run(run_dir)
    assert "factual_lookup" in summary.per_category_mode
    assert "global_overview" in summary.per_category_mode
    assert summary.per_category_mode["factual_lookup"]["graphrag-local-search:latest"]["entity_hit_rate"] == 1.0
```

- [ ] **Step 5.3: 跑测试确认失败**

Run: `pytest graphrag_pipeline/tests/test_qa_eval_scorer.py -v`
Expected: ImportError。

- [ ] **Step 5.4: 写 scorer 实现**

写到 `graphrag_pipeline/scripts/qa_eval/baseline_scorer.py`：

```python
from __future__ import annotations

import argparse
import json
import logging
import re
import sys
from collections import defaultdict
from dataclasses import dataclass, field
from pathlib import Path
from statistics import mean
from typing import Dict, List

from graphrag_pipeline.scripts.qa_eval.category_thresholds import length_score
from graphrag_pipeline.scripts.qa_eval.test_set_schema import QuestionCategory

LOGGER = logging.getLogger(__name__)

CITATION_PATTERNS: tuple[re.Pattern[str], ...] = tuple(
    re.compile(p)
    for p in (r"\[Data:", r"\[来源[:：]", r"\[出处", r"\[Sources?")
)

PER_QUESTION_METRICS: tuple[str, ...] = (
    "entity_hit_rate",
    "must_cite_hit",
    "citation_format_present",
    "negative_hit",
    "answer_chars",
    "length_score",
    "info_density",
    "elapsed_seconds",
)


@dataclass
class ScoringSummary:
    per_question: Dict[str, Dict[str, Dict[str, float]]] = field(default_factory=dict)
    per_mode: Dict[str, Dict[str, float]] = field(default_factory=dict)
    per_category_mode: Dict[str, Dict[str, Dict[str, float]]] = field(default_factory=dict)
    modes: List[str] = field(default_factory=list)


def _score_answer(
    *,
    category: QuestionCategory,
    answer: str,
    gold_entities: List[str],
    must_cite: List[str],
    negatives: List[str],
) -> Dict[str, float]:
    answer_lc = answer.casefold()

    if gold_entities:
        hit_count = sum(1 for ent in gold_entities if ent.casefold() in answer_lc)
        entity_hit_rate = hit_count / len(gold_entities)
    else:
        hit_count = 0
        entity_hit_rate = 1.0

    must_cite_hit = (
        1 if not must_cite else int(all(t.casefold() in answer_lc for t in must_cite))
    )
    citation_format_present = int(any(pat.search(answer) for pat in CITATION_PATTERNS))
    negative_hit = (
        int(any(t.casefold() in answer_lc for t in negatives)) if negatives else 0
    )
    chars = len(answer)
    density = (hit_count / chars * 1000.0) if chars else 0.0

    return {
        "entity_hit_rate": round(entity_hit_rate, 4),
        "must_cite_hit": must_cite_hit,
        "citation_format_present": citation_format_present,
        "negative_hit": negative_hit,
        "answer_chars": float(chars),
        "length_score": length_score(category, chars),
        "info_density": round(density, 4),
    }


def _score_one(item: dict, mode: str) -> Dict[str, float]:
    payload = item["modes"].get(mode, {})
    if "error" in payload:
        return {
            "error": True,
            "entity_hit_rate": 0.0,
            "must_cite_hit": 0,
            "citation_format_present": 0,
            "negative_hit": 0,
            "answer_chars": 0.0,
            "length_score": 0.0,
            "info_density": 0.0,
            "elapsed_seconds": 0.0,
        }
    scores = _score_answer(
        category=QuestionCategory(item["category"]),
        answer=payload.get("answer", ""),
        gold_entities=item.get("gold_entities", []),
        must_cite=item.get("must_cite_terms", []),
        negatives=item.get("negative_terms", []),
    )
    scores["elapsed_seconds"] = float(payload.get("elapsed_seconds") or 0.0)
    scores["error"] = False
    return scores


def _load_modes_from_meta(run_dir: Path) -> List[str]:
    meta_path = run_dir / "run_meta.json"
    if not meta_path.exists():
        raise FileNotFoundError(f"missing run_meta.json under {run_dir}")
    meta = json.loads(meta_path.read_text(encoding="utf-8"))
    modes = meta.get("modes")
    if not isinstance(modes, list) or not modes:
        raise ValueError(f"run_meta.json has no 'modes' list: {meta_path}")
    return [str(m) for m in modes]


def score_baseline_run(run_dir: Path) -> ScoringSummary:
    raw_dir = run_dir / "raw"
    if not raw_dir.exists():
        raise FileNotFoundError(f"missing raw dir: {raw_dir}")

    files = sorted(raw_dir.glob("Q*.json"))
    if not files:
        raise FileNotFoundError(f"no question files under {raw_dir}")

    modes = _load_modes_from_meta(run_dir)
    summary = ScoringSummary(modes=modes)

    per_category_buckets: Dict[str, Dict[str, Dict[str, List[float]]]] = defaultdict(
        lambda: {mode: {metric: [] for metric in PER_QUESTION_METRICS} for mode in modes}
    )
    per_mode_buckets: Dict[str, Dict[str, List[float]]] = {
        mode: {metric: [] for metric in PER_QUESTION_METRICS} for mode in modes
    }
    error_counters: Dict[str, int] = {mode: 0 for mode in modes}

    for path in files:
        item = json.loads(path.read_text(encoding="utf-8"))
        category = item["category"]
        per_mode_scores: Dict[str, Dict[str, float]] = {}
        for mode in modes:
            row = _score_one(item, mode)
            per_mode_scores[mode] = row
            if row.get("error"):
                error_counters[mode] += 1
                continue
            for metric in PER_QUESTION_METRICS:
                value = float(row.get(metric, 0.0))
                per_mode_buckets[mode][metric].append(value)
                per_category_buckets[category][mode][metric].append(value)
        summary.per_question[item["id"]] = per_mode_scores

    for mode in modes:
        summary.per_mode[mode] = {
            metric: round(mean(values), 4) if values else 0.0
            for metric, values in per_mode_buckets[mode].items()
        }
        summary.per_mode[mode]["error_count"] = float(error_counters[mode])

    for category, buckets in per_category_buckets.items():
        summary.per_category_mode[category] = {}
        for mode, metrics in buckets.items():
            summary.per_category_mode[category][mode] = {
                metric: round(mean(values), 4) if values else 0.0
                for metric, values in metrics.items()
            }

    (run_dir / "rule_scoring.json").write_text(
        json.dumps(
            {
                "per_question": summary.per_question,
                "per_mode": summary.per_mode,
                "per_category_mode": summary.per_category_mode,
            },
            ensure_ascii=False,
            indent=2,
        ),
        encoding="utf-8",
    )
    return summary


def main(argv: List[str] | None = None) -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--run-dir", type=Path, required=True)
    args = parser.parse_args(argv)
    logging.basicConfig(level=logging.INFO, format="%(asctime)s %(levelname)s %(message)s")
    summary = score_baseline_run(args.run_dir)
    print(json.dumps(summary.per_mode, ensure_ascii=False, indent=2))
    return 0


if __name__ == "__main__":  # pragma: no cover
    sys.exit(main())
```

- [ ] **Step 5.5: 跑测试通过**

Run: `pytest graphrag_pipeline/tests/test_qa_eval_scorer.py -v`
Expected: 4 PASS。

- [ ] **Step 5.6: 提交**

```bash
git add graphrag_pipeline/scripts/qa_eval/category_thresholds.py \
        graphrag_pipeline/scripts/qa_eval/baseline_scorer.py \
        graphrag_pipeline/tests/test_qa_eval_scorer.py
git commit -m "feat(qa_eval): 规则评分器加入题型长度、信息密度、按类聚合"
```

---

### Task 6: text_units 取证工具

**Files:**
- Create: `graphrag_pipeline/scripts/qa_eval/text_unit_lookup.py`
- Create: `graphrag_pipeline/tests/test_qa_eval_text_unit_lookup.py`

- [ ] **Step 6.1: 写失败测试**

写到 `graphrag_pipeline/tests/test_qa_eval_text_unit_lookup.py`：

```python
from __future__ import annotations

from pathlib import Path

import pandas as pd
import pytest

from graphrag_pipeline.scripts.qa_eval.text_unit_lookup import (
    TextUnitLookup,
    load_text_unit_lookup,
)


def _write_parquet(tmp_path: Path) -> Path:
    df = pd.DataFrame(
        [
            {"id": "d244f9016ac84a55a7435cb6466e1b38ae108a95", "text": "第 1 章 绪论"},
            {"id": "81d99ad61e36b8d45bc6265e303c143cf555bd3d", "text": "DBSCAN 与 eps"},
            {"id": "abcabcabcabcabcabcabcabcabcabc1234567890", "text": "实验 3"},
        ]
    )
    path = tmp_path / "text_units.parquet"
    df.to_parquet(path)
    return path


def test_load_text_unit_lookup_indexes_by_12_char_prefix(tmp_path: Path):
    parquet = _write_parquet(tmp_path)
    lookup: TextUnitLookup = load_text_unit_lookup(parquet)
    assert lookup.get("d244f9016ac8") == "第 1 章 绪论"
    assert lookup.get("81d99ad61e36") == "DBSCAN 与 eps"


def test_fetch_many_keeps_order_and_skips_misses(tmp_path: Path):
    parquet = _write_parquet(tmp_path)
    lookup = load_text_unit_lookup(parquet)
    snippets = lookup.fetch_many(["abcabcabcabc", "deadbeefdead", "d244f9016ac8"])
    assert len(snippets) == 2
    assert snippets[0].prefix == "abcabcabcabc"
    assert snippets[0].text == "实验 3"
    assert snippets[1].prefix == "d244f9016ac8"


def test_join_snippets_renders_as_markdown_with_refs(tmp_path: Path):
    parquet = _write_parquet(tmp_path)
    lookup = load_text_unit_lookup(parquet)
    rendered = lookup.render_for_prompt(["d244f9016ac8", "81d99ad61e36"], max_chars=80)
    assert "[d244f9016ac8]" in rendered
    assert "[81d99ad61e36]" in rendered
    assert "DBSCAN 与 eps" in rendered
```

- [ ] **Step 6.2: 跑测试确认失败**

Run: `pytest graphrag_pipeline/tests/test_qa_eval_text_unit_lookup.py -v`
Expected: ImportError。

- [ ] **Step 6.3: 写实现**

写到 `graphrag_pipeline/scripts/qa_eval/text_unit_lookup.py`：

```python
from __future__ import annotations

from dataclasses import dataclass
from pathlib import Path
from typing import Dict, List

import pandas as pd

from graphrag_pipeline.scripts.qa_eval.test_set_schema import TEXT_UNIT_ID_PREFIX_LEN


@dataclass(frozen=True, slots=True)
class TextUnitSnippet:
    prefix: str
    text: str


@dataclass(slots=True)
class TextUnitLookup:
    by_prefix: Dict[str, str]

    def get(self, prefix: str) -> str | None:
        return self.by_prefix.get(prefix[:TEXT_UNIT_ID_PREFIX_LEN])

    def fetch_many(self, prefixes: List[str]) -> List[TextUnitSnippet]:
        out: List[TextUnitSnippet] = []
        for raw in prefixes:
            key = (raw or "").strip()[:TEXT_UNIT_ID_PREFIX_LEN]
            if not key:
                continue
            text = self.by_prefix.get(key)
            if text is None:
                continue
            out.append(TextUnitSnippet(prefix=key, text=text))
        return out

    def render_for_prompt(self, prefixes: List[str], *, max_chars: int = 600) -> str:
        if not prefixes:
            return "（未提供 gold_text_unit_ids）"
        lines: List[str] = []
        for snippet in self.fetch_many(prefixes):
            body = snippet.text.strip()
            if len(body) > max_chars:
                body = body[: max(0, max_chars - 3)] + "..."
            lines.append(f"[{snippet.prefix}] {body}")
        return "\n\n".join(lines) if lines else "（gold_text_unit_ids 在 text_units.parquet 中未命中）"


def load_text_unit_lookup(parquet_path: Path) -> TextUnitLookup:
    df = pd.read_parquet(parquet_path, columns=["id", "text"])
    by_prefix: Dict[str, str] = {}
    for raw_id, text in zip(df["id"].astype(str), df["text"].astype(str)):
        prefix = raw_id[:TEXT_UNIT_ID_PREFIX_LEN]
        by_prefix.setdefault(prefix, text)
    return TextUnitLookup(by_prefix=by_prefix)
```

- [ ] **Step 6.4: 跑测试通过**

Run: `pytest graphrag_pipeline/tests/test_qa_eval_text_unit_lookup.py -v`
Expected: 3 PASS。

- [ ] **Step 6.5: 提交**

```bash
git add graphrag_pipeline/scripts/qa_eval/text_unit_lookup.py \
        graphrag_pipeline/tests/test_qa_eval_text_unit_lookup.py
git commit -m "feat(qa_eval): 新增 text_units 取证工具（12 位前缀索引）"
```

---

### Task 7: LLM 裁判评分器

**Files:**
- Create: `graphrag_pipeline/scripts/qa_eval/judge_prompts.py`
- Create: `graphrag_pipeline/scripts/qa_eval/judge_scorer.py`
- Create: `graphrag_pipeline/tests/test_qa_eval_judge.py`

裁判输出 3 个指标：

| 指标 | 取值 | 计算来源 |
| --- | --- | --- |
| `semantic_correctness` | 0 / 0.5 / 1 | 裁判 LLM 判断答案与 `gold_answer_summary` 是否语义一致 |
| `faithfulness` | 0 / 1 | 裁判 LLM 判断答案是否仅由 `gold_text_unit_ids` 取到的原文支持 |
| `retrieval_precision` | 0~1 | 从答案的 `[Data: Text Units (X, Y, ...)]` 抽取 ID，与 `gold_text_unit_ids[:12]` 取交集 |

裁判 LLM 输出强制 JSON。失败重试 2 次仍解析不出来时记 fallback (-1)，写入 `judge_raw/` 留痕。

- [ ] **Step 7.1: 写 prompt 模板**

写到 `graphrag_pipeline/scripts/qa_eval/judge_prompts.py`：

```python
from __future__ import annotations

from textwrap import dedent

SEMANTIC_PROMPT = dedent(
    """
    你是严格的课程问答评分员。请判断「学生答案」与「参考答案」在语义上是否一致。

    判分规则：
    - 1.0：核心结论与参考答案完全一致，关键实体、数值、关系都对得上。可以接受同义表达（例如「ε-邻域半径」与「eps」、「最小邻居数」与「MinPts」）。
    - 0.5：方向正确但缺少关键要点 / 含可纠正的小错误，或仅部分覆盖参考答案。
    - 0.0：与参考答案矛盾、答非所问、严重缺失要点。

    输出严格 JSON：
    {{
      "score": 1.0 | 0.5 | 0.0,
      "rationale": "<= 60 字"
    }}

    问题：{question}
    参考答案：{gold_answer_summary}
    学生答案：
    \"\"\"
    {answer}
    \"\"\"
    """
).strip()

FAITHFULNESS_PROMPT = dedent(
    """
    你是严格的课程问答忠实性评分员。请判断「学生答案」中的每一条事实陈述是否能在「证据原文」里找到支撑。

    判分规则：
    - 1：所有陈述都能在证据中找到对应。允许语义改写、同义替换。
    - 0：存在至少一条陈述在证据中找不到支撑，或与证据冲突。

    输出严格 JSON：
    {{
      "is_supported": true | false,
      "unsupported_claims": ["<最多 3 条不被支持的陈述>"]
    }}

    问题：{question}
    证据原文：
    {evidence}

    学生答案：
    \"\"\"
    {answer}
    \"\"\"
    """
).strip()
```

- [ ] **Step 7.2: 写 judge 失败测试**

写到 `graphrag_pipeline/tests/test_qa_eval_judge.py`：

```python
from __future__ import annotations

import json
from pathlib import Path
from unittest.mock import MagicMock

import pytest

from graphrag_pipeline.scripts.qa_eval.graphrag_client import QueryResult
from graphrag_pipeline.scripts.qa_eval.judge_scorer import (
    JUDGE_FALLBACK_SENTINEL,
    JudgeRunner,
    extract_text_unit_refs_from_answer,
    score_run_with_judge,
)
from graphrag_pipeline.scripts.qa_eval.run_baseline_eval import BASELINE_MODES
from graphrag_pipeline.scripts.qa_eval.text_unit_lookup import TextUnitLookup


def _seed_run(tmp_path: Path, *, answer: str = "DBSCAN 的两个核心超参数是 ε-邻域半径与最小邻居数。") -> Path:
    run_dir = tmp_path / "rid"
    raw = run_dir / "raw"
    raw.mkdir(parents=True)
    item = {
        "id": "Q001",
        "category": "factual_lookup",
        "question": "DBSCAN 的两个核心超参数是什么？",
        "gold_answer_summary": "eps 和 MinPts",
        "gold_entities": ["DBSCAN", "eps", "MinPts"],
        "gold_text_unit_ids": ["d244f9016ac8"],
        "must_cite_terms": [],
        "negative_terms": [],
        "modes": {
            m: {"answer": answer, "total_tokens": 10, "elapsed_seconds": 0.1}
            for m in BASELINE_MODES
        },
    }
    (raw / "Q001.json").write_text(json.dumps(item, ensure_ascii=False), encoding="utf-8")
    (run_dir / "run_meta.json").write_text(
        json.dumps(
            {"run_id": "rid", "index_run_label": "course", "total_items": 1, "modes": list(BASELINE_MODES)},
            ensure_ascii=False,
        ),
        encoding="utf-8",
    )
    return run_dir


def _lookup() -> TextUnitLookup:
    return TextUnitLookup(by_prefix={"d244f9016ac8": "DBSCAN: 邻域半径 eps 与最小点数 MinPts。"})


def test_extract_text_unit_refs_supports_data_blocks():
    answer = "回答 ... [Data: Text Units (d244f9016ac8, foo-bar-bazz123, 81d99ad61e36)]"
    refs = extract_text_unit_refs_from_answer(answer)
    assert "d244f9016ac8" in refs
    assert "81d99ad61e36" in refs


def test_semantic_correctness_uses_judge_decision(tmp_path: Path):
    run_dir = _seed_run(tmp_path)
    judge_client = MagicMock()
    judge_client.query.return_value = QueryResult(
        answer='{"score": 1.0, "rationale": "同义"}',
        total_tokens=42,
        elapsed_seconds=0.5,
        raw={},
    )
    runner = JudgeRunner(
        client=judge_client,
        text_unit_lookup=_lookup(),
        judge_model="deepseek-v4-flash",
        api_key=None,
    )
    summary = score_run_with_judge(run_dir, runner=runner)
    row = summary.per_question["Q001"]["graphrag-local-search:latest"]
    assert row["semantic_correctness"] == 1.0
    # answer 含 ε-邻域半径 / 最小邻居数 同义表达，规则字面命中可能是 0，
    # 但裁判 LLM 应给 1.0，证明 judge 层补足了字面层。


def test_faithfulness_falls_back_when_judge_returns_garbage(tmp_path: Path):
    run_dir = _seed_run(tmp_path)
    judge_client = MagicMock()
    judge_client.query.return_value = QueryResult(
        answer="not a json",
        total_tokens=10,
        elapsed_seconds=0.1,
        raw={},
    )
    runner = JudgeRunner(
        client=judge_client,
        text_unit_lookup=_lookup(),
        judge_model="deepseek-v4-flash",
        api_key=None,
        max_parse_retries=1,
    )
    summary = score_run_with_judge(run_dir, runner=runner)
    row = summary.per_question["Q001"]["graphrag-local-search:latest"]
    assert row["faithfulness"] == JUDGE_FALLBACK_SENTINEL
    assert (run_dir / "judge_raw" / "Q001_graphrag-local-search-latest_faithfulness.json").exists()


def test_retrieval_precision_uses_gold_text_unit_ids(tmp_path: Path):
    run_dir = _seed_run(
        tmp_path,
        answer=(
            "DBSCAN 的两个核心超参数是 eps 与 MinPts "
            "[Data: Text Units (d244f9016ac8, 99999999dead)]"
        ),
    )
    judge_client = MagicMock()
    judge_client.query.return_value = QueryResult(
        answer='{"score": 1.0, "rationale": "ok"}',
        total_tokens=20,
        elapsed_seconds=0.1,
        raw={},
    )
    runner = JudgeRunner(
        client=judge_client,
        text_unit_lookup=_lookup(),
        judge_model="deepseek-v4-flash",
        api_key=None,
    )
    summary = score_run_with_judge(run_dir, runner=runner)
    row = summary.per_question["Q001"]["graphrag-local-search:latest"]
    # gold 数 = 1, 命中 1 → precision = 1.0
    assert row["retrieval_precision"] == 1.0


def test_retrieval_precision_zero_when_answer_has_no_refs(tmp_path: Path):
    run_dir = _seed_run(tmp_path, answer="DBSCAN 的核心超参数是 eps 和 MinPts。")
    judge_client = MagicMock()
    judge_client.query.return_value = QueryResult(
        answer='{"score": 1.0, "rationale": "ok"}',
        total_tokens=10,
        elapsed_seconds=0.1,
        raw={},
    )
    runner = JudgeRunner(
        client=judge_client,
        text_unit_lookup=_lookup(),
        judge_model="deepseek-v4-flash",
        api_key=None,
    )
    summary = score_run_with_judge(run_dir, runner=runner)
    row = summary.per_question["Q001"]["graphrag-local-search:latest"]
    assert row["retrieval_precision"] == 0.0
```

- [ ] **Step 7.3: 跑测试确认失败**

Run: `pytest graphrag_pipeline/tests/test_qa_eval_judge.py -v`
Expected: ImportError。

- [ ] **Step 7.4: 写实现**

写到 `graphrag_pipeline/scripts/qa_eval/judge_scorer.py`：

```python
from __future__ import annotations

import argparse
import json
import logging
import os
import re
import sys
from collections import defaultdict
from dataclasses import dataclass, field
from pathlib import Path
from statistics import mean
from typing import Dict, List, Optional

from graphrag_pipeline.scripts.qa_eval.baseline_scorer import _load_modes_from_meta
from graphrag_pipeline.scripts.qa_eval.graphrag_client import (
    GraphRagApiError,
    OpenAICompatibleClient,
    QueryResult,
)
from graphrag_pipeline.scripts.qa_eval.judge_prompts import (
    FAITHFULNESS_PROMPT,
    SEMANTIC_PROMPT,
)
from graphrag_pipeline.scripts.qa_eval.test_set_schema import TEXT_UNIT_ID_PREFIX_LEN
from graphrag_pipeline.scripts.qa_eval.text_unit_lookup import (
    TextUnitLookup,
    load_text_unit_lookup,
)

LOGGER = logging.getLogger(__name__)
JUDGE_FALLBACK_SENTINEL = -1.0

JUDGE_METRICS: tuple[str, ...] = (
    "semantic_correctness",
    "faithfulness",
    "retrieval_precision",
)

# 抽取答案中的 text unit 引用：覆盖常见 GraphRAG 表述
_REF_BLOCK_PATTERNS: tuple[re.Pattern[str], ...] = (
    re.compile(r"Text Units?\s*\(([^)]*)\)", re.IGNORECASE),
    re.compile(r"text_units?\s*\(([^)]*)\)", re.IGNORECASE),
)
_REF_TOKEN_RE = re.compile(r"[0-9a-fA-F]{8,}")


def extract_text_unit_refs_from_answer(answer: str) -> List[str]:
    out: List[str] = []
    for pattern in _REF_BLOCK_PATTERNS:
        for block in pattern.findall(answer):
            for token in _REF_TOKEN_RE.findall(block):
                trimmed = token[:TEXT_UNIT_ID_PREFIX_LEN]
                if trimmed and trimmed not in out:
                    out.append(trimmed)
    return out


@dataclass
class JudgeSummary:
    per_question: Dict[str, Dict[str, Dict[str, float]]] = field(default_factory=dict)
    per_mode: Dict[str, Dict[str, float]] = field(default_factory=dict)
    per_category_mode: Dict[str, Dict[str, Dict[str, float]]] = field(default_factory=dict)
    modes: List[str] = field(default_factory=list)


@dataclass(slots=True)
class JudgeRunner:
    client: OpenAICompatibleClient
    text_unit_lookup: TextUnitLookup
    judge_model: str
    api_key: Optional[str]
    max_parse_retries: int = 2

    def score_pair(
        self,
        *,
        prompt: str,
        kind: str,
        question_id: str,
        mode: str,
        judge_raw_dir: Path,
    ) -> Optional[dict]:
        attempts = 0
        last_raw: Optional[QueryResult] = None
        while attempts <= self.max_parse_retries:
            attempts += 1
            try:
                result = self.client.query(
                    model=self.judge_model, prompt=prompt, api_key=self.api_key
                )
            except (GraphRagApiError, ValueError) as exc:
                LOGGER.warning("judge call failed (%s/%s/%s): %s", question_id, mode, kind, exc)
                continue
            last_raw = result
            try:
                payload = json.loads(_extract_json(result.answer))
                judge_raw_dir.mkdir(parents=True, exist_ok=True)
                (judge_raw_dir / f"{question_id}_{_safe(mode)}_{kind}.json").write_text(
                    json.dumps(
                        {"prompt": prompt, "response": result.answer, "parsed": payload},
                        ensure_ascii=False,
                        indent=2,
                    ),
                    encoding="utf-8",
                )
                return payload
            except (json.JSONDecodeError, ValueError) as exc:
                LOGGER.warning(
                    "judge parse failed (%s/%s/%s) attempt=%d: %s",
                    question_id,
                    mode,
                    kind,
                    attempts,
                    exc,
                )

        if last_raw is not None:
            judge_raw_dir.mkdir(parents=True, exist_ok=True)
            (judge_raw_dir / f"{question_id}_{_safe(mode)}_{kind}.json").write_text(
                json.dumps(
                    {"prompt": prompt, "response": last_raw.answer, "parsed": None},
                    ensure_ascii=False,
                    indent=2,
                ),
                encoding="utf-8",
            )
        return None


def _extract_json(text: str) -> str:
    text = text.strip()
    if text.startswith("```"):
        text = re.sub(r"^```[a-zA-Z]*", "", text).strip()
        if text.endswith("```"):
            text = text[:-3].strip()
    start = text.find("{")
    end = text.rfind("}")
    if start == -1 or end == -1 or end <= start:
        raise ValueError("no JSON object found")
    return text[start : end + 1]


def _safe(text: str) -> str:
    return re.sub(r"[^a-zA-Z0-9._-]+", "-", text)


def _score_item(
    *,
    item: dict,
    mode: str,
    runner: JudgeRunner,
    judge_raw_dir: Path,
) -> Dict[str, float]:
    payload = item["modes"].get(mode, {})
    if "error" in payload:
        return {
            "semantic_correctness": JUDGE_FALLBACK_SENTINEL,
            "faithfulness": JUDGE_FALLBACK_SENTINEL,
            "retrieval_precision": 0.0,
            "error": True,
        }
    answer = payload.get("answer", "")
    gold_summary = item.get("gold_answer_summary", "")
    question = item.get("question", "")
    gold_refs = item.get("gold_text_unit_ids", []) or []

    semantic_payload = runner.score_pair(
        prompt=SEMANTIC_PROMPT.format(
            question=question, gold_answer_summary=gold_summary, answer=answer
        ),
        kind="semantic",
        question_id=item["id"],
        mode=mode,
        judge_raw_dir=judge_raw_dir,
    )
    if semantic_payload is None:
        semantic_score = JUDGE_FALLBACK_SENTINEL
    else:
        try:
            semantic_score = float(semantic_payload.get("score", JUDGE_FALLBACK_SENTINEL))
            if semantic_score not in {0.0, 0.5, 1.0}:
                semantic_score = JUDGE_FALLBACK_SENTINEL
        except (TypeError, ValueError):
            semantic_score = JUDGE_FALLBACK_SENTINEL

    if gold_refs:
        evidence = runner.text_unit_lookup.render_for_prompt(gold_refs)
        faithfulness_payload = runner.score_pair(
            prompt=FAITHFULNESS_PROMPT.format(
                question=question, evidence=evidence, answer=answer
            ),
            kind="faithfulness",
            question_id=item["id"],
            mode=mode,
            judge_raw_dir=judge_raw_dir,
        )
        if faithfulness_payload is None:
            faith_score = JUDGE_FALLBACK_SENTINEL
        else:
            faith_score = 1.0 if bool(faithfulness_payload.get("is_supported")) else 0.0
    else:
        faith_score = JUDGE_FALLBACK_SENTINEL  # 没有 gold ref，无法判断忠实度

    answer_refs = set(extract_text_unit_refs_from_answer(answer))
    if gold_refs:
        gold_set = {r[:TEXT_UNIT_ID_PREFIX_LEN] for r in gold_refs}
        hit = len(answer_refs & gold_set)
        retrieval_precision = hit / len(gold_set)
    else:
        retrieval_precision = 0.0

    return {
        "semantic_correctness": semantic_score,
        "faithfulness": faith_score,
        "retrieval_precision": round(retrieval_precision, 4),
        "error": False,
    }


def score_run_with_judge(run_dir: Path, *, runner: JudgeRunner) -> JudgeSummary:
    raw_dir = run_dir / "raw"
    judge_raw_dir = run_dir / "judge_raw"
    files = sorted(raw_dir.glob("Q*.json"))
    if not files:
        raise FileNotFoundError(f"no raw items under {raw_dir}")
    modes = _load_modes_from_meta(run_dir)
    summary = JudgeSummary(modes=modes)

    per_mode_buckets: Dict[str, Dict[str, List[float]]] = {
        mode: {metric: [] for metric in JUDGE_METRICS} for mode in modes
    }
    per_category_buckets: Dict[str, Dict[str, Dict[str, List[float]]]] = defaultdict(
        lambda: {mode: {metric: [] for metric in JUDGE_METRICS} for mode in modes}
    )

    for path in files:
        item = json.loads(path.read_text(encoding="utf-8"))
        per_mode_scores: Dict[str, Dict[str, float]] = {}
        for mode in modes:
            scores = _score_item(item=item, mode=mode, runner=runner, judge_raw_dir=judge_raw_dir)
            per_mode_scores[mode] = scores
            for metric in JUDGE_METRICS:
                value = scores[metric]
                if value == JUDGE_FALLBACK_SENTINEL:
                    continue
                per_mode_buckets[mode][metric].append(float(value))
                per_category_buckets[item["category"]][mode][metric].append(float(value))
        summary.per_question[item["id"]] = per_mode_scores

    for mode in modes:
        summary.per_mode[mode] = {
            metric: round(mean(values), 4) if values else 0.0
            for metric, values in per_mode_buckets[mode].items()
        }
    for category, buckets in per_category_buckets.items():
        summary.per_category_mode[category] = {}
        for mode, metrics in buckets.items():
            summary.per_category_mode[category][mode] = {
                metric: round(mean(values), 4) if values else 0.0
                for metric, values in metrics.items()
            }

    (run_dir / "judge_scoring.json").write_text(
        json.dumps(
            {
                "per_question": summary.per_question,
                "per_mode": summary.per_mode,
                "per_category_mode": summary.per_category_mode,
                "judge_model": runner.judge_model,
                "fallback_sentinel": JUDGE_FALLBACK_SENTINEL,
            },
            ensure_ascii=False,
            indent=2,
        ),
        encoding="utf-8",
    )
    return summary


def main(argv: List[str] | None = None) -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--run-dir", type=Path, required=True)
    parser.add_argument(
        "--text-units-path",
        type=Path,
        default=Path("graphrag_pipeline/output/text_units.parquet"),
    )
    parser.add_argument("--judge-base-url", default=os.environ.get("GRAPHRAG_JUDGE_BASE_URL", os.environ.get("GRAPHRAG_API_BASE", "")))
    parser.add_argument("--judge-model", default=os.environ.get("GRAPHRAG_JUDGE_MODEL", "deepseek-v4-flash"))
    parser.add_argument("--judge-api-key", default=os.environ.get("GRAPHRAG_JUDGE_API_KEY", os.environ.get("GRAPHRAG_CHAT_API_KEY", "")))
    parser.add_argument("--max-retries", type=int, default=3)
    parser.add_argument("--request-timeout", type=float, default=120.0)
    args = parser.parse_args(argv)

    logging.basicConfig(level=logging.INFO, format="%(asctime)s %(levelname)s %(message)s")

    if not args.judge_base_url:
        raise SystemExit("missing judge base url; set GRAPHRAG_JUDGE_BASE_URL or pass --judge-base-url")

    client = OpenAICompatibleClient(
        base_url=args.judge_base_url,
        request_timeout_seconds=args.request_timeout,
        max_retries=args.max_retries,
        allow_arbitrary_models=True,
    )
    lookup = load_text_unit_lookup(args.text_units_path)
    runner = JudgeRunner(
        client=client,
        text_unit_lookup=lookup,
        judge_model=args.judge_model,
        api_key=args.judge_api_key or None,
    )
    summary = score_run_with_judge(args.run_dir, runner=runner)
    print(json.dumps(summary.per_mode, ensure_ascii=False, indent=2))
    return 0


if __name__ == "__main__":  # pragma: no cover
    sys.exit(main())
```

- [ ] **Step 7.5: 跑测试通过**

Run: `pytest graphrag_pipeline/tests/test_qa_eval_judge.py -v`
Expected: 5 PASS。

- [ ] **Step 7.6: 提交**

```bash
git add graphrag_pipeline/scripts/qa_eval/judge_prompts.py \
        graphrag_pipeline/scripts/qa_eval/judge_scorer.py \
        graphrag_pipeline/tests/test_qa_eval_judge.py
git commit -m "feat(qa_eval): 新增 LLM 裁判评分器（语义/忠实度/检索精度）"
```

---

### Task 8: 报告生成器 + 人工复核模板

**Files:**
- Create: `graphrag_pipeline/scripts/qa_eval/baseline_reporter.py`
- Create: `graphrag_pipeline/scripts/qa_eval/manual_review_template.py`
- Create: `graphrag_pipeline/tests/test_qa_eval_reporter.py`

`baseline_reporter` 同时读取 `rule_scoring.json` 与 `judge_scoring.json`（若存在），合并写出 `combined.csv`、`scoring.md` 与按 (题型 × 模式) 排版的 markdown。

`manual_review_template` 现在自动注入：

1. 按题型 × 模式的规则/裁判指标均值表（从 scoring 直接读）。
2. 假设验证槽位（先把 `hypotheses.md` 内列出的假设抽出来贴成填空模板）。
3. 路由建议槽位（`<category> → <mode>，依据 entity_hit_rate=X, semantic_correctness=Y`）。
4. 「规则高分但语义错误」的一致性核对槽位。

- [ ] **Step 8.1: 写 reporter 失败测试**

写到 `graphrag_pipeline/tests/test_qa_eval_reporter.py`：

```python
from __future__ import annotations

import json
from pathlib import Path

import pytest

from graphrag_pipeline.scripts.qa_eval.baseline_reporter import generate_report
from graphrag_pipeline.scripts.qa_eval.manual_review_template import write_manual_review_template
from graphrag_pipeline.scripts.qa_eval.run_baseline_eval import BASELINE_MODES


def _seed_run_with_two_categories(tmp_path: Path) -> Path:
    run_dir = tmp_path / "rid"
    raw = run_dir / "raw"
    raw.mkdir(parents=True)
    item1 = {
        "id": "Q001",
        "category": "factual_lookup",
        "question": "DBSCAN 的两个核心超参数是什么？",
        "gold_answer_summary": "eps 和 MinPts",
        "gold_entities": ["DBSCAN", "eps", "MinPts"],
        "gold_text_unit_ids": ["d244f9016ac8"],
        "must_cite_terms": ["eps"],
        "negative_terms": [],
        "modes": {m: {"answer": "DBSCAN 的核心超参数是 eps 和 MinPts。", "total_tokens": 10, "elapsed_seconds": 0.1} for m in BASELINE_MODES},
    }
    item2 = {
        "id": "Q002",
        "category": "global_overview",
        "question": "课程方法论主线是什么？",
        "gold_answer_summary": "数据→特征→模型→评估",
        "gold_entities": ["数据", "特征", "模型", "评估"],
        "gold_text_unit_ids": [],
        "must_cite_terms": [],
        "negative_terms": [],
        "modes": {m: {"answer": "整体方法论是数据→特征→模型→评估。", "total_tokens": 30, "elapsed_seconds": 1.0} for m in BASELINE_MODES},
    }
    (raw / "Q001.json").write_text(json.dumps(item1, ensure_ascii=False), encoding="utf-8")
    (raw / "Q002.json").write_text(json.dumps(item2, ensure_ascii=False), encoding="utf-8")
    (run_dir / "run_meta.json").write_text(
        json.dumps(
            {"run_id": "rid", "index_run_label": "course-x", "total_items": 2, "modes": list(BASELINE_MODES)},
            ensure_ascii=False,
        ),
        encoding="utf-8",
    )
    return run_dir


def test_generate_report_writes_csv_and_md_with_categories(tmp_path: Path):
    run_dir = _seed_run_with_two_categories(tmp_path)
    generate_report(run_dir)
    md = (run_dir / "scoring.md").read_text(encoding="utf-8")
    csv = (run_dir / "rule_scoring.csv").read_text(encoding="utf-8")
    assert "factual_lookup" in md
    assert "global_overview" in md
    assert "entity_hit_rate" in md
    assert "graphrag-local-search:latest" in csv


def test_manual_review_template_injects_metric_means_and_routing_slots(tmp_path: Path):
    run_dir = _seed_run_with_two_categories(tmp_path)
    generate_report(run_dir)
    hypotheses_path = tmp_path / "hypotheses.md"
    hypotheses_path.write_text(
        "# 评测假设\n\n- H1: factual_lookup 类问题，basic / local 在 entity_hit_rate 上优于 global\n- H2: ...\n",
        encoding="utf-8",
    )

    write_manual_review_template(run_dir, hypotheses_path=hypotheses_path)
    text = (run_dir / "manual_review.md").read_text(encoding="utf-8")
    assert "H1" in text
    assert "假设验证" in text
    assert "路由建议" in text
    assert "factual_lookup" in text
    # 自动注入的均值表里应能找到 entity_hit_rate 字段
    assert "entity_hit_rate" in text
```

- [ ] **Step 8.2: 跑测试确认失败**

Run: `pytest graphrag_pipeline/tests/test_qa_eval_reporter.py -v`
Expected: ImportError。

- [ ] **Step 8.3: 写 reporter 实现**

写到 `graphrag_pipeline/scripts/qa_eval/baseline_reporter.py`：

```python
from __future__ import annotations

import argparse
import csv
import json
import logging
import sys
from pathlib import Path
from typing import Dict, List

from graphrag_pipeline.scripts.qa_eval.baseline_scorer import (
    PER_QUESTION_METRICS,
    score_baseline_run,
)
from graphrag_pipeline.scripts.qa_eval.judge_scorer import JUDGE_METRICS

LOGGER = logging.getLogger(__name__)


def _write_rule_csv(run_dir: Path, summary) -> None:
    path = run_dir / "rule_scoring.csv"
    header = ["question_id", "mode", *PER_QUESTION_METRICS, "error"]
    with path.open("w", encoding="utf-8", newline="") as fh:
        writer = csv.writer(fh)
        writer.writerow(header)
        for qid, mode_rows in summary.per_question.items():
            for mode in summary.modes:
                row = mode_rows[mode]
                writer.writerow(
                    [qid, mode]
                    + [row.get(metric, 0) for metric in PER_QUESTION_METRICS]
                    + [int(bool(row.get("error")))]
                )


def _format_table(header: List[str], rows: List[List[str]]) -> List[str]:
    sep = ["---"] * len(header)
    lines = ["| " + " | ".join(header) + " |", "| " + " | ".join(sep) + " |"]
    for row in rows:
        lines.append("| " + " | ".join(row) + " |")
    return lines


def _write_markdown(run_dir: Path, summary, judge_summary_payload: dict | None) -> None:
    lines: List[str] = [f"# {run_dir.name} scoring", "", "## 规则层 - 模式总均值", ""]
    rule_metrics = (
        "entity_hit_rate",
        "must_cite_hit",
        "citation_format_present",
        "negative_hit",
        "length_score",
        "info_density",
        "answer_chars",
        "elapsed_seconds",
        "error_count",
    )
    lines.extend(
        _format_table(
            ["mode", *rule_metrics],
            [
                [mode]
                + [f"{summary.per_mode[mode].get(m, 0):.4f}" for m in rule_metrics]
                for mode in summary.modes
            ],
        )
    )

    lines += ["", "## 规则层 - 按题型 × 模式", ""]
    for category, modes_dict in summary.per_category_mode.items():
        lines += [f"### {category}", ""]
        lines.extend(
            _format_table(
                ["mode", *rule_metrics[:-1]],  # 同上去掉 error_count
                [
                    [mode]
                    + [f"{modes_dict[mode].get(m, 0):.4f}" for m in rule_metrics[:-1]]
                    for mode in summary.modes
                ],
            )
        )
        lines.append("")

    if judge_summary_payload:
        lines += ["## 裁判层 - 模式总均值", ""]
        lines.extend(
            _format_table(
                ["mode", *JUDGE_METRICS],
                [
                    [mode]
                    + [f"{judge_summary_payload['per_mode'][mode].get(m, 0):.4f}" for m in JUDGE_METRICS]
                    for mode in summary.modes
                ],
            )
        )
        lines += ["", f"裁判模型：`{judge_summary_payload.get('judge_model', '?')}`", ""]
        lines += ["## 裁判层 - 按题型 × 模式", ""]
        for category, modes_dict in judge_summary_payload.get("per_category_mode", {}).items():
            lines += [f"### {category}", ""]
            lines.extend(
                _format_table(
                    ["mode", *JUDGE_METRICS],
                    [
                        [mode]
                        + [f"{modes_dict[mode].get(m, 0):.4f}" for m in JUDGE_METRICS]
                        for mode in summary.modes
                    ],
                )
            )
            lines.append("")
    else:
        lines += ["", "_（未发现 judge_scoring.json，已跳过裁判层报表。请先执行 judge_scorer。）_", ""]

    (run_dir / "scoring.md").write_text("\n".join(lines) + "\n", encoding="utf-8")


def generate_report(run_dir: Path) -> None:
    summary = score_baseline_run(run_dir)
    _write_rule_csv(run_dir, summary)
    judge_path = run_dir / "judge_scoring.json"
    judge_payload = json.loads(judge_path.read_text(encoding="utf-8")) if judge_path.exists() else None
    _write_markdown(run_dir, summary, judge_payload)


def main(argv: List[str] | None = None) -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--run-dir", type=Path, required=True)
    args = parser.parse_args(argv)
    logging.basicConfig(level=logging.INFO, format="%(asctime)s %(levelname)s %(message)s")
    generate_report(args.run_dir)
    return 0


if __name__ == "__main__":  # pragma: no cover
    sys.exit(main())
```

- [ ] **Step 8.4: 写 manual review template 实现**

写到 `graphrag_pipeline/scripts/qa_eval/manual_review_template.py`：

```python
from __future__ import annotations

import argparse
import json
import logging
import re
import sys
from pathlib import Path
from typing import List, Optional

from graphrag_pipeline.scripts.qa_eval.baseline_scorer import score_baseline_run

LOGGER = logging.getLogger(__name__)

_HYPOTHESIS_RE = re.compile(r"^\s*[-*]\s*(H\d+)[:：]?\s*(.*)$")


def _extract_hypotheses(path: Optional[Path]) -> List[tuple[str, str]]:
    if not path or not path.exists():
        return []
    out: List[tuple[str, str]] = []
    for line in path.read_text(encoding="utf-8").splitlines():
        match = _HYPOTHESIS_RE.match(line)
        if match:
            out.append((match.group(1), match.group(2).strip()))
    return out


def _render_summary_means(run_dir: Path) -> List[str]:
    summary = score_baseline_run(run_dir)
    judge_path = run_dir / "judge_scoring.json"
    judge_payload = json.loads(judge_path.read_text(encoding="utf-8")) if judge_path.exists() else None

    lines: List[str] = ["### 规则层按题型 × 模式（自动注入）", ""]
    for category, modes_dict in summary.per_category_mode.items():
        lines.append(f"- {category}")
        for mode in summary.modes:
            row = modes_dict[mode]
            lines.append(
                f"  - {mode}: entity_hit_rate={row['entity_hit_rate']:.3f}, "
                f"must_cite_hit={row['must_cite_hit']:.3f}, "
                f"length_score={row['length_score']:.3f}, "
                f"info_density={row['info_density']:.3f}"
            )

    if judge_payload:
        lines += ["", "### 裁判层按题型 × 模式（自动注入）", ""]
        for category, modes_dict in judge_payload.get("per_category_mode", {}).items():
            lines.append(f"- {category}")
            for mode in summary.modes:
                row = modes_dict.get(mode, {})
                lines.append(
                    f"  - {mode}: semantic_correctness={row.get('semantic_correctness', 0):.3f}, "
                    f"faithfulness={row.get('faithfulness', 0):.3f}, "
                    f"retrieval_precision={row.get('retrieval_precision', 0):.3f}"
                )
    return lines


def write_manual_review_template(
    run_dir: Path,
    *,
    hypotheses_path: Optional[Path] = None,
) -> Path:
    raw_dir = run_dir / "raw"
    items = [
        json.loads(p.read_text(encoding="utf-8"))
        for p in sorted(raw_dir.glob("Q*.json"))
    ]
    if not items:
        raise FileNotFoundError(f"no raw items in {raw_dir}")

    hypotheses = _extract_hypotheses(hypotheses_path)

    lines: List[str] = [
        f"# 人工复核 - {run_dir.name}",
        "",
        "## 评分标准",
        "",
        "- `答对?`：题面要求的核心事实是否完整给出（是 / 部分 / 否）。",
        "- `有依据?`：答案是否引用了 `[Data: ...]` 或可追溯的章节/段落。",
        "- `命中正确内容?`：依据指向的内容是否真的支持该结论。",
        "- `幻觉?`：是否出现原文中不存在的实体、章节号、年份、人名。",
        "- `冗长?`：是否大段铺垫无关内容（参考 category_thresholds.py 的题型期望区间）。",
        "",
        "## 总结",
        "",
        "### 假设验证",
        "",
    ]
    if hypotheses:
        for hid, body in hypotheses:
            lines.append(f"- {hid}：[ 验证 / 部分验证 / 不支持 ]，数据依据：__")
            lines.append(f"    原假设：{body}")
    else:
        lines.append("- （未找到 `hypotheses.md`，请先在 Task 9 撰写假设）")
    lines += ["", "### hybrid 路由建议", ""]
    lines += [
        "- factual_lookup → __，依据：entity_hit_rate=__, semantic_correctness=__",
        "- relation_reasoning → __，依据：entity_hit_rate=__, semantic_correctness=__",
        "- chapter_summary → __，依据：semantic_correctness=__, length_score=__",
        "- global_overview → __，依据：semantic_correctness=__, faithfulness=__",
    ]
    lines += ["", "### 规则 vs 语义一致性核对", ""]
    lines += [
        "- 是否存在「规则高分但语义错误」的典型案例？(是 / 否)：__",
        "- 若是，列出 question_id 与模式：__",
    ]

    lines += ["", "## 指标均值", ""]
    lines += _render_summary_means(run_dir)

    lines += ["", "## 单题复核", ""]
    for item in items:
        lines.append(f"### {item['id']} [{item['category']}]")
        lines.append("")
        lines.append(f"**问题**：{item['question']}")
        lines.append("")
        lines.append(f"**参考答案**：{item['gold_answer_summary']}")
        lines.append("")
        for mode, payload in item["modes"].items():
            lines.append(f"#### {mode}")
            if "error" in payload:
                lines.append(f"- 调用失败：`{payload['error']}`")
                lines.append("")
                continue
            answer = payload.get("answer", "")
            lines.append("```text")
            lines.append(answer)
            lines.append("```")
            lines.append("")
            lines.append("- 答对? (是 / 部分 / 否)：")
            lines.append("- 有依据? (是 / 否)：")
            lines.append("- 命中正确内容? (是 / 否)：")
            lines.append("- 幻觉? (是 / 否，若是请贴出错点)：")
            lines.append("- 冗长? (是 / 否)：")
            lines.append("- 备注：")
            lines.append("")

    out = run_dir / "manual_review.md"
    out.write_text("\n".join(lines), encoding="utf-8")
    return out


def main(argv: List[str] | None = None) -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--run-dir", type=Path, required=True)
    parser.add_argument(
        "--hypotheses-path",
        type=Path,
        default=Path("graphrag_pipeline/results/qa_eval/hypotheses.md"),
    )
    args = parser.parse_args(argv)
    logging.basicConfig(level=logging.INFO, format="%(asctime)s %(levelname)s %(message)s")
    write_manual_review_template(args.run_dir, hypotheses_path=args.hypotheses_path)
    return 0


if __name__ == "__main__":  # pragma: no cover
    sys.exit(main())
```

- [ ] **Step 8.5: 跑测试通过**

Run: `pytest graphrag_pipeline/tests/test_qa_eval_reporter.py -v`
Expected: 2 PASS。

- [ ] **Step 8.6: 提交**

```bash
git add graphrag_pipeline/scripts/qa_eval/baseline_reporter.py \
        graphrag_pipeline/scripts/qa_eval/manual_review_template.py \
        graphrag_pipeline/tests/test_qa_eval_reporter.py
git commit -m "feat(qa_eval): 报告器合并规则+裁判，模板按题型注入均值与路由槽位"
```

---

### Task 9: 撰写评测假设清单（评测运行前完成）

**Files:**
- Create: `graphrag_pipeline/results/qa_eval/hypotheses.md`

> 本任务必须在 Task 10 跑 baseline 之前合入。Task 8 测试用例已经断言了 manual_review 会读这个文件。

- [ ] **Step 9.1: 写假设清单**

写到 `graphrag_pipeline/results/qa_eval/hypotheses.md`：

```markdown
# 课程问答 baseline 评测假设清单

> 本文件在评测运行前撰写并提交。运行后，所有假设在 `manual_review.md` 的「假设验证」段中按 [验证 / 部分验证 / 不支持] 三档给出结论，并粘贴具体数据依据。修改假设需重新跑评测。

## 理论依据

- GraphRAG（Edge et al., 2024）的 local-global 二分理论：local 侧重实体邻域，global 侧重社区总结，drift 在二者之间漂移，basic 退化到纯向量检索 + 完整 chunk。
- 我们用四类问题对应不同语义粒度：factual_lookup 偏 local-基础事实，chapter_summary 偏 mixed，global_overview 偏 community 报告。

## 假设

- H1：factual_lookup 类问题，local 与 basic 模式在 `entity_hit_rate` 与 `retrieval_precision` 上优于 global / drift。理由：global 的社区摘要会稀释具体术语，basic 直接召回原始 chunk，对术语命中更直接。
- H2：global_overview 类问题，global 模式在 `semantic_correctness` 上优于其他三种模式，但 `faithfulness` 可能下降。理由：community report 已经做过 LLM 汇总，叙述贴合「整体方法论」类问题，但摘要本身脱离原文 chunk，回答的字面依据更难追踪。
- H3：chapter_summary 类问题，local 与 drift 在 `semantic_correctness` 上优于 basic。理由：basic 只能返回单个 chunk，难覆盖整章；local/drift 通过实体/关系把章节内多个 chunk 拼起来。
- H4：relation_reasoning 类问题，drift 模式 `semantic_correctness` 排名第一。理由：drift 设计上就是为「跨社区/跨子图的关系探测」服务。
- H5：整体来看，**规则层 `entity_hit_rate`** 与 **裁判层 `semantic_correctness`** 在 factual_lookup 类上正相关 > 0.6，但在 global_overview 类上正相关 < 0.2。理由：global 类问题的同义改写比例更高，字面命中会失真。

## 失效情形清单（任意一条命中，假设全部需要重审）

- 测试集少于 30 条，或任一类目少于 6 条。
- `gold_text_unit_ids` 非空率 < 80%。
- 裁判 LLM `fallback_sentinel`（-1）出现率 > 15%。
```

- [ ] **Step 9.2: 提交**

```bash
git add graphrag_pipeline/results/qa_eval/hypotheses.md
git commit -m "docs(qa_eval): 提交评测前 5 条可证伪假设与失效情形清单"
```

---

### Task 10: 复用现有索引目录端到端跑通 + 沉淀首份 LGB baseline 结果

**Files:**
- Modify: `.gitignore`
- Modify: `graphrag_pipeline/PROMPT_TUNING_PIPELINE.md`（在末尾追加 QA 评测段落）
- Output: `graphrag_pipeline/results/qa_eval/runs/<run_id>/`

- [ ] **Step 10.1: 扩写测试集到 ≥ 30 题**

按 `qa_test_set_schema.md` 流程，把 `qa_test_set.jsonl` 补到每类 ≥ 6、总数 ≥ 30，**且 `gold_text_unit_ids` 非空率 ≥ 80%**。完成后：

```bash
python -m graphrag_pipeline.scripts.qa_eval.test_set_validator graphrag_pipeline/data/eval/qa_test_set.jsonl
```

Expected：`ok=true`，4 类都 ≥ 6，total ≥ 30。

提交：

```bash
git add graphrag_pipeline/data/eval/qa_test_set.jsonl
git commit -m "feat(qa_eval): 测试集补全至 30 题并通过 validator"
```

- [ ] **Step 10.2: 加 .gitignore 规则**

在仓库根 `.gitignore` 末尾追加：

```text
# QA 评测：原始响应与中间 json 不入库
graphrag_pipeline/results/qa_eval/runs/*/raw/
graphrag_pipeline/results/qa_eval/runs/*/judge_raw/
graphrag_pipeline/results/qa_eval/runs/*/rule_scoring.json
graphrag_pipeline/results/qa_eval/runs/*/judge_scoring.json
```

> `scoring.md`、`rule_scoring.csv`、`run_meta.json`、`manual_review.md`、`hypotheses.md` 体积小且为最终产物，仍然入库。
> 如需共享 raw / judge_raw，建议压缩成 tar.gz 上传到 Release 附件或用 DVC 管理。

```bash
git add .gitignore
git commit -m "chore(qa_eval): 忽略 raw / judge_raw / scoring*.json 等大文件"
```

- [ ] **Step 10.3: 验证现有 GraphRAG output 产物完整性**

```bash
cd graphrag_pipeline

for path in \
  output/entities.parquet \
  output/text_units.parquet \
  output/community_reports.parquet \
  output/documents.parquet \
  output/relationships.parquet \
  output/communities.parquet \
  output/stats.json \
  output/lancedb/entity_description.lance \
  output/lancedb/text_unit_text.lance \
  output/lancedb/community_full_content.lance
do
  test -e "$path" || { echo "missing $path"; exit 1; }
done

python - <<'PY'
import json
from pathlib import Path

stats = json.loads(Path("output/stats.json").read_text(encoding="utf-8"))
print(f"existing-output-docs{stats.get('num_documents', 'unknown')}")
PY
```

Expected：命令退出码为 0，并打印类似 `existing-output-docs393`。如果任何文件缺失，停止本计划，不要自动运行 `graphrag index`。

- [ ] **Step 10.4: 启动 GraphRAG API 并做四模式 smoke query**

```bash
cd graphrag_pipeline
INDEX_OUTPUT_DIR="$PWD/output/auto_tuned_crs-20260506-r4slkr_material7_20260513_001047"
GRAPHRAG_OUTPUT_DIR="$INDEX_OUTPUT_DIR" \
GRAPHRAG_STORAGE_DIR="$INDEX_OUTPUT_DIR" \
GRAPHRAG_LANCEDB_URI="$INDEX_OUTPUT_DIR/lancedb" \
python utils/main.py
```

新开 shell 验证：

```bash
curl -s http://127.0.0.1:8000/v1/models | python -m json.tool
```

Expected：返回 4 个 `graphrag-*-search:latest` 模型 id。

继续用同一套目标索引目录做 smoke query：

```bash
python - <<'PY'
import requests

models = [
    "graphrag-local-search:latest",
    "graphrag-global-search:latest",
    "graphrag-drift-search:latest",
    "graphrag-basic-search:latest",
]
for model in models:
    response = requests.post(
        "http://127.0.0.1:8000/v1/chat/completions",
        json={
            "model": model,
            "messages": [
                {"role": "user", "content": "请用三句话概述这门课程的核心内容。"}
            ],
            "temperature": 0,
        },
        timeout=180,
    )
    response.raise_for_status()
    content = response.json()["choices"][0]["message"]["content"]
    if not content.strip():
        raise RuntimeError(f"{model} returned empty content")
    print(model, "OK", len(content))
PY
```

Expected：四个模型都打印 `OK`。如果任一模式失败，先修复服务 / 产物路径 / 环境变量，不进入 baseline。

- [ ] **Step 10.5: 跑 LGB 全量 baseline + DRIFT 抽样 + 规则评分 + 裁判评分**

```bash
cd /home/sunlight/Projects/ckqa

RUN_ID=baseline-lgb-$(date +%Y%m%d-%H%M%S)
INDEX_OUTPUT_DIR="graphrag_pipeline/output/auto_tuned_crs-20260506-r4slkr_material7_20260513_001047"
INDEX_LABEL="$(python - <<'PY'
import json
from datetime import date
from pathlib import Path

active = Path("graphrag_pipeline/prompts/final/active_prompt.json")
stats = Path("graphrag_pipeline/output/auto_tuned_crs-20260506-r4slkr_material7_20260513_001047/stats.json")

if active.exists():
    payload = json.loads(active.read_text(encoding="utf-8"))
    candidate = payload.get("candidate") or payload.get("name") or "active-prompt"
else:
    stat_payload = json.loads(stats.read_text(encoding="utf-8")) if stats.exists() else {}
    candidate = f"existing-output-docs{stat_payload.get('num_documents', 'unknown')}"

print(f"{candidate}@{date.today().isoformat()}")
PY
)"

python -m graphrag_pipeline.scripts.qa_eval.run_baseline_eval \
    --test-set graphrag_pipeline/data/eval/qa_test_set.jsonl \
    --output-root graphrag_pipeline/results/qa_eval/runs \
    --run-id "$RUN_ID" \
    --index-run-label "$INDEX_LABEL" \
    --index-output-dir "$INDEX_OUTPUT_DIR" \
    --base-url http://127.0.0.1:8000 \
    --modes local global basic \
    --request-timeout-seconds 240

python -m graphrag_pipeline.scripts.qa_eval.baseline_scorer --run-dir "graphrag_pipeline/results/qa_eval/runs/$RUN_ID"
python -m graphrag_pipeline.scripts.qa_eval.latency_reporter --run-dir "graphrag_pipeline/results/qa_eval/runs/$RUN_ID"

# 裁判 LLM 走外部端点：必要时改 --judge-base-url
GRAPHRAG_JUDGE_MODEL=${GRAPHRAG_JUDGE_MODEL:-deepseek-v4-flash}
python -m graphrag_pipeline.scripts.qa_eval.judge_scorer \
    --run-dir "graphrag_pipeline/results/qa_eval/runs/$RUN_ID" \
    --judge-model "$GRAPHRAG_JUDGE_MODEL"

python -m graphrag_pipeline.scripts.qa_eval.baseline_reporter --run-dir "graphrag_pipeline/results/qa_eval/runs/$RUN_ID"
python -m graphrag_pipeline.scripts.qa_eval.manual_review_template \
    --run-dir "graphrag_pipeline/results/qa_eval/runs/$RUN_ID" \
    --hypotheses-path graphrag_pipeline/results/qa_eval/hypotheses.md

# DRIFT 保留为单独抽样 run；例如沿用新索引 smoke / lite 结果，
# 或另外执行 --max-items 1/4 的抽样评测，不并入同步全量 baseline。
```

Expected：`scoring.md`、`rule_scoring.csv`、`latency_breakdown.md`、`manual_review.md` 生成；`judge_scoring.json` 中 `fallback_sentinel` 触发率 < 15%。DRIFT 结果以抽样 run 形式保留，不要求并入本次全量 `combined.csv`。

- [ ] **Step 10.6: 完成假设验证 + 至少 8 条人工复核**

打开 `manual_review.md`：

1. 顶部「假设验证」段：把 5 条假设按 [验证 / 部分验证 / 不支持] 三档填好，每条粘贴 1~2 个具体数值依据（直接从「指标均值」段抄）。
2. 「hybrid 路由建议」段：把 4 条 `<category> → <mode>，依据：...` 填实。
3. 「规则 vs 语义一致性核对」段：列出 ≥ 1 个「规则高分但语义错误」或「规则低分但语义正确」的典型 question_id。
4. 「单题复核」段：从每类各挑 2 题，按模板填 5 项判断。

- [ ] **Step 10.7: 更新 PROMPT_TUNING_PIPELINE.md**

在 `graphrag_pipeline/PROMPT_TUNING_PIPELINE.md` 末尾追加：

```markdown
## QA 评测

`scripts/qa_eval/` 提供「规则 + LLM 裁判」双层评测：

- 本流程默认复用 `graphrag_pipeline/output/` 下已有 parquet 与 LanceDB 查询产物；运行 QA baseline 不会执行 `graphrag index`。
- `data/eval/qa_test_set.jsonl`：≥ 30 道题，由 `test_set_validator.py` 校验；`gold_text_unit_ids` 字段非空率 ≥ 80%。
- `run_baseline_eval.py`：本地 GraphRAG API `/v1/chat/completions` 串行打四模式，写 `runs/<run_id>/raw/`。
- `baseline_scorer.py`：规则评分，输出 `entity_hit_rate / must_cite_hit / citation_format_present / negative_hit / length_score / info_density`，按 (题型 × 模式) 聚合。
- `text_unit_lookup.py` + `judge_scorer.py`：通过外部裁判 LLM（默认 `deepseek-v4-flash`，可通过 `GRAPHRAG_JUDGE_MODEL` 切换）计算 `semantic_correctness / faithfulness / retrieval_precision`。
- `baseline_reporter.py`：合并两层评分，生成 `scoring.md` 与 `rule_scoring.csv`。
- `manual_review_template.py`：自动注入指标均值、读取 `hypotheses.md` 生成「假设验证」与「hybrid 路由建议」槽位，供人工复核。

入门顺序：先确认现有 `output/` 完整并通过四模式 smoke query，再在 `results/qa_eval/hypotheses.md` 写 ≥ 4 条可证伪假设，随后跑 baseline，最后在 `manual_review.md` 按假设验证格式给结论。
```

- [ ] **Step 10.8: 提交首份 baseline 产物**

```bash
git add graphrag_pipeline/PROMPT_TUNING_PIPELINE.md \
        graphrag_pipeline/results/qa_eval/runs/$RUN_ID/scoring.md \
        graphrag_pipeline/results/qa_eval/runs/$RUN_ID/rule_scoring.csv \
        graphrag_pipeline/results/qa_eval/runs/$RUN_ID/run_meta.json \
        graphrag_pipeline/results/qa_eval/runs/$RUN_ID/manual_review.md
git commit -m "chore(qa_eval): 首份 baseline 结果与假设验证结论"
```

---

## 自检清单

- 任务 5（测试集）：schema 强制四类覆盖、`gold_text_unit_ids` 非空率门槛、id pattern 收口 → ✅ Task 1 + Task 2 + Task 10。
- 任务 6（四模式测试）：四模式串行跑 + 规则/裁判双层评分 + 按 (题型 × 模式) 聚合 + 假设验证总结 → ✅ Task 4 + 5 + 7 + 8 + 9 + 10。
- 完成判据：30 题 + 4 类覆盖 + judge fallback < 15% + 路由建议槽位 → ✅ 全 Task 链。
- 反馈对应表：

| 反馈 | 处理 |
| --- | --- |
| P1 语义层缺失 | Task 7 新增 `semantic_correctness`（0/0.5/1） |
| P2 citation 语义退化 | 规则指标重命名为 `citation_format_present`；Task 7 新增 `faithfulness` |
| P3 `gold_text_unit_ids` 未使用 | Task 6 新增 `text_unit_lookup`；Task 7 新增 `retrieval_precision` + schema 文档明示用途 + Task 10 强制非空率 ≥ 80% |
| P4 缺假设驱动 | Task 9 新增 `hypotheses.md`；Task 8 manual_review 自动读假设 |
| P5 长度跨题型不一致 | Task 5 新增 `category_thresholds` 与 `length_score` + `info_density` |
| P6 `.gitignore` 模糊 | Task 10 给出显式规则 |
| P7 id 约束矛盾 | Task 1 schema 删除 `min_length/max_length`，只留 `pattern=r"^Q\d{3,5}$"` |
| P8 validator 错误未截断 | Task 1 新增 `max_errors` 参数与 CLI 开关 |
| P9 mode 顺序不稳 | Task 5 改为从 `run_meta.json` 的 `modes` 字段读 |
| P10 总结段量化不足 | Task 8 模板自动注入指标均值、假设验证、路由建议、一致性核对四类槽位 |

完成本计划后即可进入 `2026-05-12-hybrid-qa-mode.md`，构建 Phase 3-4 的混合检索模式。
