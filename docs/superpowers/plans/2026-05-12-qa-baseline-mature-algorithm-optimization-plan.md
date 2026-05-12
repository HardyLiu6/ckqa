# QA Baseline 成熟算法优化实施计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 在不推翻 `2026-05-12-qa-baseline-test-set-and-eval.md` 的前提下，复用当前 `graphrag_pipeline/output/` 查询产物与 baseline raw 结果，引入成熟算法与现成评测库，降低人工出题和自研评分成本，并让 GraphRAG 四模式对比结果更可复现、更适合作为后续 hybrid 路由依据。

**Architecture:** 仍以 `graphrag_pipeline/data/eval/qa_test_set.jsonl` 和 `results/qa_eval/runs/<run_id>/` 为主入口，但新增一条算法增强支线：用 TF-IDF + KMeans + MMR 从现有 `output/text_units.parquet` / `output/relationships.parquet` / `output/community_reports.parquet` 生成候选题材覆盖清单；用 `bert-score` 补充非 LLM 语义相似度；用 `ir-measures` 基于 `gold_text_unit_ids` 计算标准 IR 指标；用 paired bootstrap 生成置信区间和模式两两显著性对比；保留一个可选 RAGAS 适配器，把本地产物导出成 RAGAS 数据集并在依赖可用时计算 RAGAS 指标。默认路径不新增常驻服务，不改变 GraphRAG 索引和四模式 API，也不从 `results/extraction_eval/` 直接替代问答评测输入。

**Tech Stack:** Python 3.10+、Pydantic 2、pandas、numpy、scikit-learn、bert-score、ir-measures、requests、pytest；RAGAS 作为可选 extra，不作为默认必装依赖。

**成熟算法 / 第三方能力选型：**

| 目标 | 采用方案 | 用途 | 为什么不用自研 |
| --- | --- | --- | --- |
| 测试集覆盖 | TF-IDF + KMeans + MMR | 从 text units 中挑出章节/概念分布均衡的候选题材 | 已有 scikit-learn，简单稳定，避免人工只围绕少数章节出题 |
| 语义正确性旁路 | BERTScore F1 | 对 `answer` 与 `gold_answer_summary` 做非 LLM 语义相似度 | 比字面命中更抗同义改写，比 LLM judge 更便宜可复现 |
| 检索质量 | ir-measures 口径的 Recall@k / MRR@k / nDCG@k / MAP | 基于 `gold_text_unit_ids` 与答案引用顺序评估引用/检索命中 | IR 指标有成熟定义，避免自造 `retrieval_precision` 后语义含混 |
| RAG 质量对齐 | RAGAS 兼容导出，可选运行 faithfulness / answer_correctness / context_precision | 与主流 RAG 评测口径对齐 | 默认不绑定依赖，避免 LangChain / provider 适配拖慢 MVP |
| 四模式可信比较 | paired bootstrap | 输出均值 95% CI、pairwise win-rate、差值置信区间 | 30~50 题样本较小，单看均值容易误判 |

**前置依赖：**

- 已有或同时实施 `2026-05-12-qa-baseline-test-set-and-eval.md` 中的基础 schema、runner、raw 输出和报告目录结构，且基础计划的现有 `output/` 完整性检查与四模式 smoke query 已通过。
- `graphrag_pipeline/output/text_units.parquet` 必须存在，至少包含 `id` 与 `text` 列；完整 QA baseline 仍依赖 `output/*.parquet` 与 `output/lancedb/`。
- `output/relationships.parquet` 与 `output/community_reports.parquet` 可选；缺失时候选题材生成器只生成 factual / chapter 类候选。
- 本计划不要求、也不允许隐式运行 `graphrag index`。如果需要修复缺失产物或重建索引，必须单独确认。
- `results/extraction_eval/runs/.../*.json` 与 `results/extraction_raw/runs/.../*.jsonl` 只能作为抽取质量参考，不能直接替代四模式问答评测需要的 GraphRAG `output/` 查询产物。
- BERTScore 首次运行会下载默认模型；离线环境可通过 `CKQA_BERTSCORE_MODEL` 指向本地模型目录。

**完成判据：**

1. `graphrag_pipeline/pyproject.toml` 新增 `eval` optional extra，包含 `bert-score` 与 `ir-measures`；`ragas` 独立为可选 extra。
2. `python -m graphrag_pipeline.scripts.qa_eval.algorithmic_seed_builder --output graphrag_pipeline/data/eval/qa_candidate_seeds.jsonl --max-items 80` 能从当前 `output/text_units.parquet` 生成候选题材，且每条含 `source_text_unit_ids`。
3. `python -m graphrag_pipeline.scripts.qa_eval.algorithmic_scorer --run-dir <run_dir>` 能产出 `algorithmic_scoring.{csv,json,md}`，包含 `bertscore_f1 / citation_recall_at_3 / citation_mrr / citation_ndcg_at_5`。
4. `python -m graphrag_pipeline.scripts.qa_eval.significance_reporter --run-dir <run_dir>` 能产出 `significance.md`，包含每类题型下四模式均值 95% CI 与 pairwise comparison。
5. `python -m graphrag_pipeline.scripts.qa_eval.ragas_exporter --run-dir <run_dir>` 能产出 `ragas_dataset.jsonl`；未安装 RAGAS 时不会失败，只提示如何安装。
6. `pytest graphrag_pipeline/tests/test_qa_eval_algorithmic_*.py -v` 全绿。
7. `.gitignore` 覆盖 `graphrag_pipeline/results/qa_eval/runs/*/ragas_raw/` 与大体积模型/缓存产物，不提交下载模型或 raw judge 结果。

---

## File Structure

| 路径 | 责任 |
| --- | --- |
| `graphrag_pipeline/pyproject.toml` | 新增 `eval` / `ragas` optional extras |
| `graphrag_pipeline/scripts/qa_eval/algorithmic_seed_builder.py` | TF-IDF + KMeans + MMR 候选题材生成器 |
| `graphrag_pipeline/scripts/qa_eval/citation_extractor.py` | 从 GraphRAG 回答中抽取 Text Unit 引用顺序 |
| `graphrag_pipeline/scripts/qa_eval/ir_metrics.py` | 按 ir-measures 口径计算 Recall@k / MRR / nDCG / MAP |
| `graphrag_pipeline/scripts/qa_eval/semantic_similarity.py` | 用 bert-score 计算非 LLM 语义相似度 |
| `graphrag_pipeline/scripts/qa_eval/algorithmic_scorer.py` | 汇总 BERTScore + IR 指标，输出 algorithmic_scoring |
| `graphrag_pipeline/scripts/qa_eval/bootstrap_stats.py` | paired bootstrap 均值 CI 与模式差异比较 |
| `graphrag_pipeline/scripts/qa_eval/significance_reporter.py` | 输出 `significance.md` |
| `graphrag_pipeline/scripts/qa_eval/ragas_exporter.py` | 导出 RAGAS 兼容数据集，可选运行 RAGAS |
| `graphrag_pipeline/data/eval/qa_candidate_seeds.jsonl` | 机器生成候选题材，不作为 gold set |
| `graphrag_pipeline/results/qa_eval/runs/<run_id>/algorithmic_scoring.{csv,json,md}` | 算法指标产物 |
| `graphrag_pipeline/results/qa_eval/runs/<run_id>/significance.md` | 四模式显著性报告 |
| `graphrag_pipeline/results/qa_eval/runs/<run_id>/ragas_dataset.jsonl` | RAGAS 兼容导出 |
| `graphrag_pipeline/tests/test_qa_eval_algorithmic_seed_builder.py` | 候选题材生成测试 |
| `graphrag_pipeline/tests/test_qa_eval_algorithmic_metrics.py` | 引用抽取、IR 指标、BERTScore mock 测试 |
| `graphrag_pipeline/tests/test_qa_eval_bootstrap_stats.py` | bootstrap 显著性测试 |
| `graphrag_pipeline/tests/test_qa_eval_ragas_exporter.py` | RAGAS 导出测试 |
| `.gitignore` | 忽略 RAGAS raw、临时缓存和大文件产物 |

---

### Task 1: 依赖与指标命名边界

**Files:**
- Modify: `graphrag_pipeline/pyproject.toml`
- Modify: `.gitignore`
- Create: `graphrag_pipeline/tests/test_qa_eval_algorithmic_dependencies.py`

- [ ] **Step 1.1: 写依赖约束测试**

写到 `graphrag_pipeline/tests/test_qa_eval_algorithmic_dependencies.py`：

```python
from __future__ import annotations

import tomllib
from pathlib import Path


def test_eval_extra_declares_algorithmic_metric_dependencies():
    pyproject = tomllib.loads(Path("graphrag_pipeline/pyproject.toml").read_text(encoding="utf-8"))
    extras = pyproject["project"]["optional-dependencies"]

    eval_extra = "\n".join(extras["eval"])
    assert "bert-score" in eval_extra
    assert "ir-measures" in eval_extra


def test_ragas_extra_is_kept_optional():
    pyproject = tomllib.loads(Path("graphrag_pipeline/pyproject.toml").read_text(encoding="utf-8"))
    extras = pyproject["project"]["optional-dependencies"]

    assert "ragas" in extras
    assert "ragas" not in "\n".join(extras["eval"])
```

- [ ] **Step 1.2: 跑测试确认失败**

Run: `pytest graphrag_pipeline/tests/test_qa_eval_algorithmic_dependencies.py -v`
Expected: FAIL，`KeyError: 'eval'`。

- [ ] **Step 1.3: 新增 optional extras**

修改 `graphrag_pipeline/pyproject.toml` 的 `[project.optional-dependencies]`：

```toml
eval = [
    # QA baseline algorithmic evaluation
    "bert-score>=0.3.13",
    "ir-measures>=0.3.5",
]
ragas = [
    # Optional RAG evaluation compatibility layer
    "ragas>=0.2.0",
]
all = [
    "graphrag-pipeline[ml,graph,scraper,eval]",
]
```

保留既有 `ml` / `graph` / `scraper` 内容，只把 `all` 改为包含 `eval`。

- [ ] **Step 1.4: 更新 `.gitignore`**

追加：

```gitignore
# QA eval optional third-party outputs
graphrag_pipeline/results/qa_eval/runs/*/ragas_raw/
graphrag_pipeline/results/qa_eval/runs/*/ragas_scoring.json
graphrag_pipeline/results/qa_eval/runs/*/algorithmic_scoring.json
```

- [ ] **Step 1.5: 跑测试通过**

Run: `pytest graphrag_pipeline/tests/test_qa_eval_algorithmic_dependencies.py -v`
Expected: 2 PASS。

- [ ] **Step 1.6: 提交**

```bash
git add graphrag_pipeline/pyproject.toml .gitignore \
        graphrag_pipeline/tests/test_qa_eval_algorithmic_dependencies.py
git commit -m "feat(qa_eval): 增加成熟算法评测依赖边界"
```

---

### Task 2: TF-IDF + KMeans + MMR 候选题材生成

**Files:**
- Create: `graphrag_pipeline/scripts/qa_eval/algorithmic_seed_builder.py`
- Create: `graphrag_pipeline/tests/test_qa_eval_algorithmic_seed_builder.py`

目标不是自动写最终 gold QA，而是生成人工出题清单，降低遗漏章节和概念的风险。

- [ ] **Step 2.1: 写候选题材生成失败测试**

写到 `graphrag_pipeline/tests/test_qa_eval_algorithmic_seed_builder.py`：

```python
from __future__ import annotations

import json
from pathlib import Path

import pandas as pd

from graphrag_pipeline.scripts.qa_eval.algorithmic_seed_builder import (
    SeedBuilderConfig,
    build_candidate_seeds,
)


def _write_text_units(tmp_path: Path) -> Path:
    df = pd.DataFrame(
        [
            {"id": "tu-001", "text": "DBSCAN 使用 eps 和 MinPts 描述密度可达关系。", "document_ids": ["doc-1"]},
            {"id": "tu-002", "text": "K-Means 通过簇中心迭代优化样本划分。", "document_ids": ["doc-1"]},
            {"id": "tu-003", "text": "监督学习依赖带标签数据，常用于分类与回归。", "document_ids": ["doc-2"]},
            {"id": "tu-004", "text": "模型评估包含准确率、召回率、F1 等指标。", "document_ids": ["doc-2"]},
            {"id": "tu-005", "text": "聚类算法用于无监督学习中的结构发现。", "document_ids": ["doc-3"]},
            {"id": "tu-006", "text": "课程总结强调数据、模型、评估和部署的完整流程。", "document_ids": ["doc-3"]},
        ]
    )
    path = tmp_path / "text_units.parquet"
    df.to_parquet(path)
    return path


def test_build_candidate_seeds_returns_diverse_sources(tmp_path: Path):
    text_units = _write_text_units(tmp_path)
    output = tmp_path / "qa_candidate_seeds.jsonl"

    seeds = build_candidate_seeds(
        SeedBuilderConfig(
            text_units_path=text_units,
            output_path=output,
            max_items=4,
            cluster_count=3,
            random_seed=7,
        )
    )

    assert len(seeds) == 4
    assert output.exists()
    loaded = [json.loads(line) for line in output.read_text(encoding="utf-8").splitlines()]
    assert all(item["source_text_unit_ids"] for item in loaded)
    assert len({item["coverage_cluster"] for item in loaded}) >= 2
    assert {item["suggested_category"] for item in loaded} <= {
        "factual_lookup",
        "relation_reasoning",
        "chapter_summary",
        "global_overview",
    }
```

- [ ] **Step 2.2: 跑测试确认失败**

Run: `pytest graphrag_pipeline/tests/test_qa_eval_algorithmic_seed_builder.py -v`
Expected: FAIL，ImportError on `algorithmic_seed_builder`。

- [ ] **Step 2.3: 实现生成器**

写到 `graphrag_pipeline/scripts/qa_eval/algorithmic_seed_builder.py`：

```python
from __future__ import annotations

import argparse
import json
from dataclasses import asdict, dataclass
from pathlib import Path
from typing import Iterable, List

import numpy as np
import pandas as pd
from sklearn.cluster import KMeans
from sklearn.feature_extraction.text import TfidfVectorizer
from sklearn.metrics.pairwise import cosine_similarity


@dataclass(slots=True)
class SeedBuilderConfig:
    text_units_path: Path = Path("graphrag_pipeline/output/text_units.parquet")
    output_path: Path = Path("graphrag_pipeline/data/eval/qa_candidate_seeds.jsonl")
    max_items: int = 80
    cluster_count: int = 12
    random_seed: int = 42


@dataclass(slots=True)
class CandidateSeed:
    id: str
    suggested_category: str
    source_text_unit_ids: List[str]
    source_preview: str
    coverage_cluster: int
    selection_score: float
    writing_hint: str


def _normalize_text(value: object) -> str:
    return str(value or "").strip().replace("\n", " ")


def _suggest_category(text: str) -> str:
    if any(token in text for token in ("总结", "概述", "流程", "体系", "方法论")):
        return "global_overview"
    if any(token in text for token in ("关系", "依赖", "对比", "区别", "联系")):
        return "relation_reasoning"
    if any(token in text for token in ("章", "节", "本章", "小结")):
        return "chapter_summary"
    return "factual_lookup"


def _mmr_order(vectors, candidate_indices: Iterable[int], *, limit: int, lambda_mult: float = 0.7) -> list[int]:
    candidates = list(candidate_indices)
    if not candidates:
        return []
    centroid = np.asarray(vectors[candidates].mean(axis=0)).reshape(1, -1)
    relevance = cosine_similarity(vectors[candidates], centroid).reshape(-1)
    selected: list[int] = []
    selected_local: list[int] = []
    while candidates and len(selected) < limit:
        if not selected:
            best_local = int(np.argmax(relevance))
        else:
            similarity_to_selected = cosine_similarity(
                vectors[candidates],
                vectors[selected],
            ).max(axis=1)
            mmr = lambda_mult * relevance - (1.0 - lambda_mult) * similarity_to_selected
            best_local = int(np.argmax(mmr))
        selected.append(candidates.pop(best_local))
        selected_local.append(best_local)
        relevance = np.delete(relevance, best_local)
    return selected


def build_candidate_seeds(config: SeedBuilderConfig) -> list[CandidateSeed]:
    df = pd.read_parquet(config.text_units_path)
    rows = [
        {"id": str(row["id"])[:12], "text": _normalize_text(row["text"])}
        for _, row in df.iterrows()
        if _normalize_text(row.get("text"))
    ]
    if not rows:
        raise ValueError(f"no usable text rows in {config.text_units_path}")

    texts = [row["text"] for row in rows]
    vectorizer = TfidfVectorizer(analyzer="char_wb", ngram_range=(2, 4), max_features=8000)
    vectors = vectorizer.fit_transform(texts)
    cluster_count = max(1, min(config.cluster_count, len(rows)))
    labels = KMeans(n_clusters=cluster_count, random_state=config.random_seed, n_init="auto").fit_predict(vectors)

    per_cluster_limit = max(1, int(np.ceil(config.max_items / cluster_count)))
    selected_indices: list[int] = []
    for cluster in range(cluster_count):
        indices = [idx for idx, label in enumerate(labels) if label == cluster]
        selected_indices.extend(_mmr_order(vectors, indices, limit=per_cluster_limit))
    selected_indices = selected_indices[: config.max_items]

    seeds: list[CandidateSeed] = []
    for serial, idx in enumerate(selected_indices, start=1):
        text = rows[idx]["text"]
        seed = CandidateSeed(
            id=f"S{serial:03d}",
            suggested_category=_suggest_category(text),
            source_text_unit_ids=[rows[idx]["id"]],
            source_preview=text[:220],
            coverage_cluster=int(labels[idx]),
            selection_score=1.0,
            writing_hint=f"围绕该 text unit 写 1 道 { _suggest_category(text) } 题，并补充 gold_answer_summary。",
        )
        seeds.append(seed)

    config.output_path.parent.mkdir(parents=True, exist_ok=True)
    config.output_path.write_text(
        "\n".join(json.dumps(asdict(seed), ensure_ascii=False) for seed in seeds) + "\n",
        encoding="utf-8",
    )
    return seeds


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--text-units", type=Path, default=SeedBuilderConfig.text_units_path)
    parser.add_argument("--output", type=Path, default=SeedBuilderConfig.output_path)
    parser.add_argument("--max-items", type=int, default=80)
    parser.add_argument("--cluster-count", type=int, default=12)
    args = parser.parse_args()
    seeds = build_candidate_seeds(
        SeedBuilderConfig(
            text_units_path=args.text_units,
            output_path=args.output,
            max_items=args.max_items,
            cluster_count=args.cluster_count,
        )
    )
    print(f"wrote {len(seeds)} candidate seeds to {args.output}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
```

- [ ] **Step 2.4: 跑测试通过**

Run: `pytest graphrag_pipeline/tests/test_qa_eval_algorithmic_seed_builder.py -v`
Expected: 1 PASS。

- [ ] **Step 2.5: 在现有 output 产物上生成候选题材**

Run:

```bash
python -m graphrag_pipeline.scripts.qa_eval.algorithmic_seed_builder \
    --output graphrag_pipeline/data/eval/qa_candidate_seeds.jsonl \
    --max-items 80 \
    --cluster-count 12
```

Expected: 输出 `wrote ... candidate seeds`，且 `qa_candidate_seeds.jsonl` 每行都含 `source_text_unit_ids`。本步骤只读取 `graphrag_pipeline/output/text_units.parquet`，不运行 `graphrag index`。

- [ ] **Step 2.6: 提交**

```bash
git add graphrag_pipeline/scripts/qa_eval/algorithmic_seed_builder.py \
        graphrag_pipeline/tests/test_qa_eval_algorithmic_seed_builder.py \
        graphrag_pipeline/data/eval/qa_candidate_seeds.jsonl
git commit -m "feat(qa_eval): 增加算法辅助候选题材生成"
```

---

### Task 3: 引用抽取与标准 IR 指标

**Files:**
- Create: `graphrag_pipeline/scripts/qa_eval/citation_extractor.py`
- Create: `graphrag_pipeline/scripts/qa_eval/ir_metrics.py`
- Create: `graphrag_pipeline/tests/test_qa_eval_algorithmic_metrics.py`

- [ ] **Step 3.1: 写引用抽取和 IR 失败测试**

写到 `graphrag_pipeline/tests/test_qa_eval_algorithmic_metrics.py`：

```python
from __future__ import annotations

from graphrag_pipeline.scripts.qa_eval.citation_extractor import extract_text_unit_refs
from graphrag_pipeline.scripts.qa_eval.ir_metrics import score_ranked_citations


def test_extract_text_unit_refs_handles_graphrag_data_format():
    answer = "DBSCAN 的参数是 eps 和 MinPts。[Data: Text Units (d244f9016ac8, 81d99ad61e36)]"
    assert extract_text_unit_refs(answer) == ["d244f9016ac8", "81d99ad61e36"]


def test_extract_text_unit_refs_deduplicates_in_order():
    answer = "[Data: Text Units (aaaabbbbcccc, aaaabbbbcccc, ddddeeeeffff)]"
    assert extract_text_unit_refs(answer) == ["aaaabbbbcccc", "ddddeeeeffff"]


def test_score_ranked_citations_computes_recall_mrr_ndcg():
    scores = score_ranked_citations(
        question_id="Q001",
        ranked_refs=["a11111111111", "b22222222222", "c33333333333"],
        gold_refs=["b22222222222", "x99999999999"],
        cutoffs=[1, 3, 5],
    )
    assert scores["citation_recall_at_1"] == 0.0
    assert scores["citation_recall_at_3"] == 0.5
    assert scores["citation_mrr"] == 0.5
    assert 0.0 < scores["citation_ndcg_at_5"] <= 1.0
```

- [ ] **Step 3.2: 跑测试确认失败**

Run: `pytest graphrag_pipeline/tests/test_qa_eval_algorithmic_metrics.py -v`
Expected: FAIL，ImportError。

- [ ] **Step 3.3: 实现引用抽取**

写到 `graphrag_pipeline/scripts/qa_eval/citation_extractor.py`：

```python
from __future__ import annotations

import re

TEXT_UNIT_PREFIX_LEN = 12
TEXT_UNITS_BLOCK_RE = re.compile(r"Text Units?\s*\(([^)]*)\)", re.IGNORECASE)
HEXISH_RE = re.compile(r"[A-Za-z0-9][A-Za-z0-9_-]{7,}")


def extract_text_unit_refs(answer: str) -> list[str]:
    refs: list[str] = []
    for block in TEXT_UNITS_BLOCK_RE.findall(answer or ""):
        for match in HEXISH_RE.findall(block):
            normalized = match.strip()[:TEXT_UNIT_PREFIX_LEN]
            if normalized and normalized not in refs:
                refs.append(normalized)
    return refs
```

- [ ] **Step 3.4: 实现 IR 指标**

写到 `graphrag_pipeline/scripts/qa_eval/ir_metrics.py`：

```python
from __future__ import annotations

import math
from typing import Iterable


def _prefix_set(values: Iterable[str]) -> set[str]:
    return {str(value).strip()[:12] for value in values if str(value).strip()}


def _dcg(binary_hits: list[int]) -> float:
    return sum(hit / math.log2(rank + 2) for rank, hit in enumerate(binary_hits))


def score_ranked_citations(
    *,
    question_id: str,
    ranked_refs: list[str],
    gold_refs: list[str],
    cutoffs: list[int] | None = None,
) -> dict[str, float | str]:
    cutoffs = cutoffs or [1, 3, 5]
    gold = _prefix_set(gold_refs)
    ranked = [ref[:12] for ref in ranked_refs if ref[:12]]
    out: dict[str, float | str] = {"question_id": question_id}

    if not gold:
        for cutoff in cutoffs:
            out[f"citation_recall_at_{cutoff}"] = 0.0
            out[f"citation_ndcg_at_{cutoff}"] = 0.0
        out["citation_mrr"] = 0.0
        out["citation_map"] = 0.0
        return out

    for cutoff in cutoffs:
        top = ranked[:cutoff]
        hits = [1 if ref in gold else 0 for ref in top]
        out[f"citation_recall_at_{cutoff}"] = round(sum(hits) / len(gold), 4)
        ideal_hits = [1] * min(len(gold), cutoff)
        denom = _dcg(ideal_hits)
        out[f"citation_ndcg_at_{cutoff}"] = round((_dcg(hits) / denom) if denom else 0.0, 4)

    reciprocal_rank = 0.0
    precisions: list[float] = []
    hit_count = 0
    for index, ref in enumerate(ranked, start=1):
        if ref in gold:
            hit_count += 1
            if reciprocal_rank == 0.0:
                reciprocal_rank = 1.0 / index
            precisions.append(hit_count / index)
    out["citation_mrr"] = round(reciprocal_rank, 4)
    out["citation_map"] = round(sum(precisions) / len(gold), 4) if precisions else 0.0
    return out
```

> 说明：这里固定采用 ir-measures 对齐的标准公式，并保持输出字段稳定，便于后续报告和 hybrid 对比直接复用。

- [ ] **Step 3.5: 跑测试通过**

Run: `pytest graphrag_pipeline/tests/test_qa_eval_algorithmic_metrics.py -v`
Expected: 3 PASS。

- [ ] **Step 3.6: 提交**

```bash
git add graphrag_pipeline/scripts/qa_eval/citation_extractor.py \
        graphrag_pipeline/scripts/qa_eval/ir_metrics.py \
        graphrag_pipeline/tests/test_qa_eval_algorithmic_metrics.py
git commit -m "feat(qa_eval): 增加引用抽取与标准检索指标"
```

---

### Task 4: BERTScore 非 LLM 语义相似度

**Files:**
- Create: `graphrag_pipeline/scripts/qa_eval/semantic_similarity.py`
- Modify: `graphrag_pipeline/tests/test_qa_eval_algorithmic_metrics.py`

- [ ] **Step 4.1: 写 BERTScore wrapper 测试**

追加到 `graphrag_pipeline/tests/test_qa_eval_algorithmic_metrics.py`：

```python
from unittest.mock import patch

from graphrag_pipeline.scripts.qa_eval.semantic_similarity import score_semantic_similarity


def test_score_semantic_similarity_uses_bertscore_f1():
    class _FakeTensor:
        def __init__(self, value: float):
            self._value = value

        def item(self) -> float:
            return self._value

    with patch(
        "graphrag_pipeline.scripts.qa_eval.semantic_similarity.bert_score.score",
        return_value=([_FakeTensor(0.7)], [_FakeTensor(0.8)], [_FakeTensor(0.75)]),
    ):
        result = score_semantic_similarity(
            answer="DBSCAN 的核心参数是 eps 和 MinPts。",
            reference="DBSCAN 的两个核心超参数是 eps 和 MinPts。",
            model_type="bert-base-chinese",
        )
    assert result["bertscore_precision"] == 0.7
    assert result["bertscore_recall"] == 0.8
    assert result["bertscore_f1"] == 0.75
```

- [ ] **Step 4.2: 跑测试确认失败**

Run: `pytest graphrag_pipeline/tests/test_qa_eval_algorithmic_metrics.py::test_score_semantic_similarity_uses_bertscore_f1 -v`
Expected: FAIL，ImportError。

- [ ] **Step 4.3: 实现 BERTScore wrapper**

写到 `graphrag_pipeline/scripts/qa_eval/semantic_similarity.py`：

```python
from __future__ import annotations

import os

from bert_score import score as bert_score


DEFAULT_BERTSCORE_MODEL = "bert-base-chinese"


def score_semantic_similarity(
    *,
    answer: str,
    reference: str,
    model_type: str | None = None,
) -> dict[str, float]:
    if not (answer or "").strip() or not (reference or "").strip():
        return {
            "bertscore_precision": 0.0,
            "bertscore_recall": 0.0,
            "bertscore_f1": 0.0,
        }

    model = model_type or os.environ.get("CKQA_BERTSCORE_MODEL", DEFAULT_BERTSCORE_MODEL)
    precision, recall, f1 = bert_score(
        [answer],
        [reference],
        lang="zh",
        model_type=model,
        verbose=False,
        rescale_with_baseline=False,
    )
    return {
        "bertscore_precision": round(float(precision[0].item()), 4),
        "bertscore_recall": round(float(recall[0].item()), 4),
        "bertscore_f1": round(float(f1[0].item()), 4),
    }
```

- [ ] **Step 4.4: 跑测试通过**

Run: `pytest graphrag_pipeline/tests/test_qa_eval_algorithmic_metrics.py -v`
Expected: 全部 PASS。

- [ ] **Step 4.5: 提交**

```bash
git add graphrag_pipeline/scripts/qa_eval/semantic_similarity.py \
        graphrag_pipeline/tests/test_qa_eval_algorithmic_metrics.py
git commit -m "feat(qa_eval): 增加 BERTScore 语义相似度指标"
```

---

### Task 5: algorithmic_scorer 汇总器

**Files:**
- Create: `graphrag_pipeline/scripts/qa_eval/algorithmic_scorer.py`
- Create: `graphrag_pipeline/tests/test_qa_eval_algorithmic_scorer.py`

该脚本读取 baseline run 的 `raw/`、`run_meta.json` 与 `qa_test_set.jsonl`，输出算法层评分。

- [ ] **Step 5.1: 写汇总器失败测试**

写到 `graphrag_pipeline/tests/test_qa_eval_algorithmic_scorer.py`：

```python
from __future__ import annotations

import json
from pathlib import Path
from unittest.mock import patch

from graphrag_pipeline.scripts.qa_eval.algorithmic_scorer import score_run_algorithmically


def _write_run(tmp_path: Path) -> tuple[Path, Path]:
    test_set = tmp_path / "qa_test_set.jsonl"
    test_set.write_text(
        json.dumps(
            {
                "id": "Q001",
                "category": "factual_lookup",
                "question": "DBSCAN 的核心参数是什么？",
                "gold_answer_summary": "DBSCAN 的核心参数是 eps 和 MinPts。",
                "gold_entities": ["DBSCAN", "eps", "MinPts"],
                "gold_text_unit_ids": ["d244f9016ac8"],
                "must_cite_terms": ["eps", "MinPts"],
                "negative_terms": [],
            },
            ensure_ascii=False,
        )
        + "\n",
        encoding="utf-8",
    )
    run_dir = tmp_path / "run"
    raw_dir = run_dir / "raw"
    raw_dir.mkdir(parents=True)
    (run_dir / "run_meta.json").write_text(
        json.dumps({"modes": ["graphrag-local-search:latest"], "test_set_path": str(test_set)}),
        encoding="utf-8",
    )
    (raw_dir / "Q001_graphrag-local-search-latest.json").write_text(
        json.dumps(
            {
                "question_id": "Q001",
                "mode": "graphrag-local-search:latest",
                "answer": "DBSCAN 的核心参数是 eps 和 MinPts。[Data: Text Units (d244f9016ac8)]",
            },
            ensure_ascii=False,
        ),
        encoding="utf-8",
    )
    return run_dir, test_set


def test_score_run_algorithmically_writes_outputs(tmp_path: Path):
    run_dir, test_set = _write_run(tmp_path)
    with patch(
        "graphrag_pipeline.scripts.qa_eval.algorithmic_scorer.score_semantic_similarity",
        return_value={"bertscore_precision": 0.9, "bertscore_recall": 0.8, "bertscore_f1": 0.85},
    ):
        summary = score_run_algorithmically(run_dir, test_set_path=test_set)

    assert summary["rows"][0]["bertscore_f1"] == 0.85
    assert summary["rows"][0]["citation_recall_at_3"] == 1.0
    assert (run_dir / "algorithmic_scoring.csv").exists()
    assert (run_dir / "algorithmic_scoring.json").exists()
    assert (run_dir / "algorithmic_scoring.md").exists()
```

- [ ] **Step 5.2: 跑测试确认失败**

Run: `pytest graphrag_pipeline/tests/test_qa_eval_algorithmic_scorer.py -v`
Expected: FAIL，ImportError。

- [ ] **Step 5.3: 实现汇总器**

写到 `graphrag_pipeline/scripts/qa_eval/algorithmic_scorer.py`：

```python
from __future__ import annotations

import argparse
import json
from pathlib import Path
from typing import Any

import pandas as pd

from graphrag_pipeline.scripts.qa_eval.citation_extractor import extract_text_unit_refs
from graphrag_pipeline.scripts.qa_eval.ir_metrics import score_ranked_citations
from graphrag_pipeline.scripts.qa_eval.semantic_similarity import score_semantic_similarity
from graphrag_pipeline.scripts.qa_eval.test_set_schema import QaTestItem


def _safe_mode(mode: str) -> str:
    return mode.replace(":", "-").replace("/", "-")


def _load_test_set(path: Path) -> dict[str, QaTestItem]:
    items: dict[str, QaTestItem] = {}
    for raw in path.read_text(encoding="utf-8").splitlines():
        if raw.strip():
            item = QaTestItem.model_validate_json(raw)
            items[item.id] = item
    return items


def _load_raw_answer(run_dir: Path, question_id: str, mode: str) -> str:
    path = run_dir / "raw" / f"{question_id}_{_safe_mode(mode)}.json"
    payload = json.loads(path.read_text(encoding="utf-8"))
    return str(payload.get("answer", ""))


def score_run_algorithmically(run_dir: Path, *, test_set_path: Path | None = None) -> dict[str, Any]:
    meta = json.loads((run_dir / "run_meta.json").read_text(encoding="utf-8"))
    test_set = test_set_path or Path(meta["test_set_path"])
    items = _load_test_set(test_set)
    rows: list[dict[str, Any]] = []
    for item in items.values():
        for mode in meta["modes"]:
            answer = _load_raw_answer(run_dir, item.id, mode)
            refs = extract_text_unit_refs(answer)
            semantic = score_semantic_similarity(
                answer=answer,
                reference=item.gold_answer_summary,
            )
            ir = score_ranked_citations(
                question_id=item.id,
                ranked_refs=refs,
                gold_refs=item.gold_text_unit_ids,
                cutoffs=[1, 3, 5],
            )
            rows.append(
                {
                    "question_id": item.id,
                    "category": item.category.value,
                    "mode": mode,
                    **semantic,
                    **{key: value for key, value in ir.items() if key != "question_id"},
                }
            )

    df = pd.DataFrame(rows)
    run_dir.mkdir(parents=True, exist_ok=True)
    df.to_csv(run_dir / "algorithmic_scoring.csv", index=False)
    grouped = (
        df.groupby(["category", "mode"], as_index=False)
        .mean(numeric_only=True)
        .sort_values(["category", "mode"])
    )
    summary = {
        "rows": rows,
        "per_category_mode": grouped.to_dict(orient="records"),
    }
    (run_dir / "algorithmic_scoring.json").write_text(
        json.dumps(summary, ensure_ascii=False, indent=2),
        encoding="utf-8",
    )
    (run_dir / "algorithmic_scoring.md").write_text(
        "# 算法增强评分\n\n"
        + grouped.to_markdown(index=False)
        + "\n",
        encoding="utf-8",
    )
    return summary


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--run-dir", type=Path, required=True)
    parser.add_argument("--test-set", type=Path, default=None)
    args = parser.parse_args()
    score_run_algorithmically(args.run_dir, test_set_path=args.test_set)
    print(f"wrote algorithmic scoring under {args.run_dir}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
```

- [ ] **Step 5.4: 跑测试通过**

Run: `pytest graphrag_pipeline/tests/test_qa_eval_algorithmic_scorer.py -v`
Expected: 1 PASS。

- [ ] **Step 5.5: 提交**

```bash
git add graphrag_pipeline/scripts/qa_eval/algorithmic_scorer.py \
        graphrag_pipeline/tests/test_qa_eval_algorithmic_scorer.py
git commit -m "feat(qa_eval): 汇总算法增强评分"
```

---

### Task 6: paired bootstrap 显著性分析

**Files:**
- Create: `graphrag_pipeline/scripts/qa_eval/bootstrap_stats.py`
- Create: `graphrag_pipeline/scripts/qa_eval/significance_reporter.py`
- Create: `graphrag_pipeline/tests/test_qa_eval_bootstrap_stats.py`

- [ ] **Step 6.1: 写 bootstrap 失败测试**

写到 `graphrag_pipeline/tests/test_qa_eval_bootstrap_stats.py`：

```python
from __future__ import annotations

import pandas as pd

from graphrag_pipeline.scripts.qa_eval.bootstrap_stats import (
    bootstrap_mean_ci,
    paired_bootstrap_diff,
)


def test_bootstrap_mean_ci_is_deterministic_with_seed():
    low, mean, high = bootstrap_mean_ci([0.2, 0.4, 0.6, 0.8], iterations=200, seed=3)
    assert low <= mean <= high
    assert round(mean, 2) == 0.5


def test_paired_bootstrap_diff_prefers_better_mode():
    df = pd.DataFrame(
        [
            {"question_id": "Q001", "mode": "a", "metric": 0.9},
            {"question_id": "Q001", "mode": "b", "metric": 0.2},
            {"question_id": "Q002", "mode": "a", "metric": 0.8},
            {"question_id": "Q002", "mode": "b", "metric": 0.3},
        ]
    )
    result = paired_bootstrap_diff(
        df,
        mode_a="a",
        mode_b="b",
        metric="metric",
        iterations=200,
        seed=7,
    )
    assert result["mean_diff"] > 0
    assert result["win_rate"] > 0.9
```

- [ ] **Step 6.2: 跑测试确认失败**

Run: `pytest graphrag_pipeline/tests/test_qa_eval_bootstrap_stats.py -v`
Expected: FAIL，ImportError。

- [ ] **Step 6.3: 实现 bootstrap 工具**

写到 `graphrag_pipeline/scripts/qa_eval/bootstrap_stats.py`：

```python
from __future__ import annotations

from itertools import combinations
from typing import Iterable

import numpy as np
import pandas as pd


def bootstrap_mean_ci(
    values: Iterable[float],
    *,
    iterations: int = 2000,
    seed: int = 42,
    alpha: float = 0.05,
) -> tuple[float, float, float]:
    arr = np.asarray([float(v) for v in values], dtype=float)
    if arr.size == 0:
        return 0.0, 0.0, 0.0
    rng = np.random.default_rng(seed)
    means = np.array([rng.choice(arr, size=arr.size, replace=True).mean() for _ in range(iterations)])
    low = float(np.quantile(means, alpha / 2))
    high = float(np.quantile(means, 1 - alpha / 2))
    return round(low, 4), round(float(arr.mean()), 4), round(high, 4)


def paired_bootstrap_diff(
    df: pd.DataFrame,
    *,
    mode_a: str,
    mode_b: str,
    metric: str,
    iterations: int = 2000,
    seed: int = 42,
) -> dict[str, float | str]:
    pivot = df.pivot_table(index="question_id", columns="mode", values=metric, aggfunc="mean").dropna()
    if mode_a not in pivot.columns or mode_b not in pivot.columns or pivot.empty:
        return {"mode_a": mode_a, "mode_b": mode_b, "metric": metric, "mean_diff": 0.0, "win_rate": 0.0, "ci_low": 0.0, "ci_high": 0.0}
    diffs = (pivot[mode_a] - pivot[mode_b]).to_numpy(dtype=float)
    rng = np.random.default_rng(seed)
    sampled = np.array([rng.choice(diffs, size=diffs.size, replace=True).mean() for _ in range(iterations)])
    return {
        "mode_a": mode_a,
        "mode_b": mode_b,
        "metric": metric,
        "mean_diff": round(float(diffs.mean()), 4),
        "win_rate": round(float((sampled > 0).mean()), 4),
        "ci_low": round(float(np.quantile(sampled, 0.025)), 4),
        "ci_high": round(float(np.quantile(sampled, 0.975)), 4),
    }


def pairwise_bootstrap_table(df: pd.DataFrame, *, metric: str) -> list[dict[str, float | str]]:
    modes = sorted(str(mode) for mode in df["mode"].dropna().unique())
    return [
        paired_bootstrap_diff(df, mode_a=a, mode_b=b, metric=metric)
        for a, b in combinations(modes, 2)
    ]
```

- [ ] **Step 6.4: 实现 significance reporter**

写到 `graphrag_pipeline/scripts/qa_eval/significance_reporter.py`：

```python
from __future__ import annotations

import argparse
from pathlib import Path

import pandas as pd

from graphrag_pipeline.scripts.qa_eval.bootstrap_stats import (
    bootstrap_mean_ci,
    pairwise_bootstrap_table,
)

DEFAULT_METRICS = ["bertscore_f1", "citation_recall_at_3", "citation_mrr", "citation_ndcg_at_5"]


def write_significance_report(run_dir: Path, *, metrics: list[str] | None = None) -> Path:
    metrics = metrics or DEFAULT_METRICS
    df = pd.read_csv(run_dir / "algorithmic_scoring.csv")
    lines = ["# 四模式显著性对比", ""]
    for metric in metrics:
        if metric not in df.columns:
            continue
        lines += [f"## {metric}", "", "| category | mode | ci_low | mean | ci_high |", "| --- | --- | ---: | ---: | ---: |"]
        for (category, mode), group in df.groupby(["category", "mode"]):
            low, mean, high = bootstrap_mean_ci(group[metric].tolist())
            lines.append(f"| {category} | {mode} | {low:.4f} | {mean:.4f} | {high:.4f} |")
        lines += ["", "### Pairwise", "", "| mode_a | mode_b | mean_diff | ci_low | ci_high | win_rate |", "| --- | --- | ---: | ---: | ---: | ---: |"]
        for row in pairwise_bootstrap_table(df, metric=metric):
            lines.append(
                f"| {row['mode_a']} | {row['mode_b']} | {row['mean_diff']:.4f} | "
                f"{row['ci_low']:.4f} | {row['ci_high']:.4f} | {row['win_rate']:.4f} |"
            )
        lines.append("")
    output = run_dir / "significance.md"
    output.write_text("\n".join(lines), encoding="utf-8")
    return output


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--run-dir", type=Path, required=True)
    args = parser.parse_args()
    output = write_significance_report(args.run_dir)
    print(f"wrote {output}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
```

- [ ] **Step 6.5: 跑测试通过**

Run: `pytest graphrag_pipeline/tests/test_qa_eval_bootstrap_stats.py -v`
Expected: 2 PASS。

- [ ] **Step 6.6: 提交**

```bash
git add graphrag_pipeline/scripts/qa_eval/bootstrap_stats.py \
        graphrag_pipeline/scripts/qa_eval/significance_reporter.py \
        graphrag_pipeline/tests/test_qa_eval_bootstrap_stats.py
git commit -m "feat(qa_eval): 增加四模式 bootstrap 显著性报告"
```

---

### Task 7: RAGAS 兼容导出与可选运行

**Files:**
- Create: `graphrag_pipeline/scripts/qa_eval/ragas_exporter.py`
- Create: `graphrag_pipeline/tests/test_qa_eval_ragas_exporter.py`

RAGAS 不作为默认评分闭环的硬依赖。本任务只保证能导出 RAGAS 所需字段，并在安装 RAGAS 时可选运行。

- [ ] **Step 7.1: 写 RAGAS 导出失败测试**

写到 `graphrag_pipeline/tests/test_qa_eval_ragas_exporter.py`：

```python
from __future__ import annotations

import json
from pathlib import Path

from graphrag_pipeline.scripts.qa_eval.ragas_exporter import export_ragas_dataset


def test_export_ragas_dataset_writes_question_answer_contexts(tmp_path: Path):
    run_dir = tmp_path / "run"
    raw_dir = run_dir / "raw"
    raw_dir.mkdir(parents=True)
    test_set = tmp_path / "qa_test_set.jsonl"
    test_set.write_text(
        json.dumps(
            {
                "id": "Q001",
                "category": "factual_lookup",
                "question": "DBSCAN 的核心参数是什么？",
                "gold_answer_summary": "eps 和 MinPts",
                "gold_entities": ["DBSCAN"],
                "gold_text_unit_ids": ["tu-001"],
                "must_cite_terms": ["eps"],
                "negative_terms": [],
            },
            ensure_ascii=False,
        )
        + "\n",
        encoding="utf-8",
    )
    (run_dir / "run_meta.json").write_text(
        json.dumps({"modes": ["graphrag-local-search:latest"], "test_set_path": str(test_set)}),
        encoding="utf-8",
    )
    (raw_dir / "Q001_graphrag-local-search-latest.json").write_text(
        json.dumps(
            {"question_id": "Q001", "mode": "graphrag-local-search:latest", "answer": "答案"},
            ensure_ascii=False,
        ),
        encoding="utf-8",
    )

    output = export_ragas_dataset(run_dir, contexts_by_question={"Q001": ["DBSCAN 使用 eps 和 MinPts。"]})

    rows = [json.loads(line) for line in output.read_text(encoding="utf-8").splitlines()]
    assert rows[0]["question"] == "DBSCAN 的核心参数是什么？"
    assert rows[0]["answer"] == "答案"
    assert rows[0]["contexts"] == ["DBSCAN 使用 eps 和 MinPts。"]
    assert rows[0]["ground_truth"] == "eps 和 MinPts"
```

- [ ] **Step 7.2: 跑测试确认失败**

Run: `pytest graphrag_pipeline/tests/test_qa_eval_ragas_exporter.py -v`
Expected: FAIL，ImportError。

- [ ] **Step 7.3: 实现 RAGAS 导出器**

写到 `graphrag_pipeline/scripts/qa_eval/ragas_exporter.py`：

```python
from __future__ import annotations

import argparse
import importlib.util
import json
from pathlib import Path

from graphrag_pipeline.scripts.qa_eval.algorithmic_scorer import _load_test_set, _safe_mode


def export_ragas_dataset(
    run_dir: Path,
    *,
    contexts_by_question: dict[str, list[str]] | None = None,
) -> Path:
    meta = json.loads((run_dir / "run_meta.json").read_text(encoding="utf-8"))
    items = _load_test_set(Path(meta["test_set_path"]))
    contexts_by_question = contexts_by_question or {}
    rows: list[str] = []
    for item in items.values():
        for mode in meta["modes"]:
            raw_path = run_dir / "raw" / f"{item.id}_{_safe_mode(mode)}.json"
            answer = json.loads(raw_path.read_text(encoding="utf-8")).get("answer", "")
            rows.append(
                json.dumps(
                    {
                        "question_id": item.id,
                        "mode": mode,
                        "question": item.question,
                        "answer": answer,
                        "contexts": contexts_by_question.get(item.id, []),
                        "ground_truth": item.gold_answer_summary,
                        "gold_text_unit_ids": item.gold_text_unit_ids,
                    },
                    ensure_ascii=False,
                )
            )
    output = run_dir / "ragas_dataset.jsonl"
    output.write_text("\n".join(rows) + "\n", encoding="utf-8")
    return output


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--run-dir", type=Path, required=True)
    parser.add_argument("--run-ragas", action="store_true")
    args = parser.parse_args()
    output = export_ragas_dataset(args.run_dir)
    print(f"wrote {output}")
    if args.run_ragas and importlib.util.find_spec("ragas") is None:
        print("ragas is not installed; run `pip install -e 'graphrag_pipeline[ragas]'` to enable optional scoring")
    elif args.run_ragas:
        print("ragas is installed; use ragas_dataset.jsonl as the stable handoff file for provider-specific offline scoring")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
```

- [ ] **Step 7.4: 跑测试通过**

Run: `pytest graphrag_pipeline/tests/test_qa_eval_ragas_exporter.py -v`
Expected: 1 PASS。

- [ ] **Step 7.5: 提交**

```bash
git add graphrag_pipeline/scripts/qa_eval/ragas_exporter.py \
        graphrag_pipeline/tests/test_qa_eval_ragas_exporter.py
git commit -m "feat(qa_eval): 增加 RAGAS 兼容数据导出"
```

---

### Task 8: 文档、端到端验证与对比报告

**Files:**
- Modify: `graphrag_pipeline/data/eval/qa_test_set_schema.md`
- Modify: `graphrag_pipeline/README.md`
- Modify: `graphrag_pipeline/scripts/README.md`
- Modify: `graphrag_pipeline/scripts/qa_eval/baseline_reporter.py`（若基础计划已落地）

- [ ] **Step 8.1: 更新测试集文档**

在 `graphrag_pipeline/data/eval/qa_test_set_schema.md` 增加：

```markdown
## 算法增强评测字段

- 算法增强层复用基础 baseline 的现有 `output/` 与 `runs/<run_id>/raw/`，不触发索引重建；抽取评测结果只能作为背景参考，不能替代四模式问答 raw。
- `gold_text_unit_ids` 同时用于 LLM faithfulness 取证与标准 IR 指标。
- `qa_candidate_seeds.jsonl` 是机器生成的候选题材清单，不是 gold set；正式题目仍以 `qa_test_set.jsonl` 为准。
- 算法层指标：
  - `bertscore_f1`：答案与 `gold_answer_summary` 的非 LLM 语义相似度。
  - `citation_recall_at_3`：答案引用的前 3 个 text unit 覆盖 gold text unit 的比例。
  - `citation_mrr`：第一个正确引用出现得越靠前越高。
  - `citation_ndcg_at_5`：前 5 个引用的排序质量。
```

- [ ] **Step 8.2: 更新 GraphRAG README**

在 `graphrag_pipeline/README.md` 的 QA 评测段增加：

```markdown
### 算法增强评测

本流程默认复用已经通过 smoke query 的 `graphrag_pipeline/output/` 产物与 baseline run，不运行 `graphrag index`。

安装可复现算法评测依赖：

```bash
pip install -e ".[eval]"
```

生成候选题材：

```bash
python -m graphrag_pipeline.scripts.qa_eval.algorithmic_seed_builder \
  --output graphrag_pipeline/data/eval/qa_candidate_seeds.jsonl \
  --max-items 80
```

在 baseline run 完成后追加算法评分与显著性分析：

```bash
python -m graphrag_pipeline.scripts.qa_eval.algorithmic_scorer --run-dir <run_dir>
python -m graphrag_pipeline.scripts.qa_eval.significance_reporter --run-dir <run_dir>
python -m graphrag_pipeline.scripts.qa_eval.ragas_exporter --run-dir <run_dir>
```
```

- [ ] **Step 8.3: 更新脚本文档**

在 `graphrag_pipeline/scripts/README.md` 增加 `qa_eval` 小节，列出：

```markdown
| 脚本 | 用途 |
| --- | --- |
| `qa_eval/algorithmic_seed_builder.py` | 生成候选题材覆盖清单 |
| `qa_eval/algorithmic_scorer.py` | 生成 BERTScore + IR 指标 |
| `qa_eval/significance_reporter.py` | 生成四模式 bootstrap 对比 |
| `qa_eval/ragas_exporter.py` | 导出 RAGAS 兼容数据集 |
```

- [ ] **Step 8.4: 端到端验证**

Run:

```bash
pytest graphrag_pipeline/tests/test_qa_eval_algorithmic_*.py \
       graphrag_pipeline/tests/test_qa_eval_bootstrap_stats.py \
       graphrag_pipeline/tests/test_qa_eval_ragas_exporter.py -v
```

Expected: 全部 PASS。

如果已有 baseline run：

```bash
python -m graphrag_pipeline.scripts.qa_eval.algorithmic_scorer --run-dir <run_dir>
python -m graphrag_pipeline.scripts.qa_eval.significance_reporter --run-dir <run_dir>
python -m graphrag_pipeline.scripts.qa_eval.ragas_exporter --run-dir <run_dir>
```

Expected:

- `<run_dir>/algorithmic_scoring.csv`
- `<run_dir>/algorithmic_scoring.md`
- `<run_dir>/significance.md`
- `<run_dir>/ragas_dataset.jsonl`

- [ ] **Step 8.5: 提交**

```bash
git add graphrag_pipeline/data/eval/qa_test_set_schema.md \
        graphrag_pipeline/README.md \
        graphrag_pipeline/scripts/README.md
git commit -m "docs(qa_eval): 补充算法增强评测使用说明"
```

---

## 与原 baseline 计划的对比点

| 维度 | 原 baseline 计划 | 本优化计划 |
| --- | --- | --- |
| 出题 | 人工撰写 30~50 题 | TF-IDF + KMeans + MMR 先生成候选题材，再人工定稿 |
| 规则评分 | 字面实体、引用格式、长度、密度 | 保留原规则，额外增加 BERTScore 与标准 IR 指标 |
| LLM 裁判 | 自研 prompt 计算 semantic / faithfulness | 保留自研裁判，同时导出 RAGAS 兼容数据便于主流口径复核 |
| 检索指标 | `retrieval_precision` | 拆为 `citation_recall_at_k / citation_mrr / citation_ndcg_at_k / citation_map` |
| 模式比较 | 均值和人工结论 | 均值 + bootstrap 95% CI + pairwise win-rate |
| 风险 | 自研指标容易语义漂移 | 新增依赖和模型下载成本，需要保持 optional extra 边界 |

## 自检清单

- 依赖不影响默认运行：`eval` / `ragas` 都是 optional extra。
- 不改变 GraphRAG 索引：本计划只读 `output/*.parquet` / `output/lancedb/` 与 baseline raw，绝不把 `results/extraction_eval/` 当作四模式问答输入。
- 不替代人工 gold：`qa_candidate_seeds.jsonl` 明确是候选题材。
- 指标命名更准确：答案引用命中称为 `citation_*`，不冒充完整 retrieval pipeline。
- RAGAS 仅作为兼容导出和可选运行，不阻塞本地 MVP。
