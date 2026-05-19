# QA Baseline 成熟算法优化实施计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 在不推翻 `2026-05-12-qa-baseline-test-set-and-eval.md` 的前提下，复用当前 `graphrag_pipeline/output/` 查询产物与 baseline raw 结果，引入成熟算法与现成评测库，降低人工出题和自研评分成本，并让 GraphRAG 四模式对比结果更可复现、更适合作为后续 hybrid 路由依据。

**Architecture:** 仍以 `graphrag_pipeline/data/eval/qa_test_set.jsonl` 和 `results/qa_eval/runs/<run_id>/` 为主入口，但新增一条算法增强支线：用 TF-IDF + KMeans + MMR 从现有 `output/text_units.parquet` / `output/relationships.parquet` / `output/community_reports.parquet` 生成候选题材覆盖清单；用 BGE-M3 分块语义覆盖作为长答案主语义指标；用 ROUGE-Lsum 与 keyword recall 作为极低成本 baseline；通过 `ir-measures` 基于 `gold_text_unit_ids` 和解析后的 GraphRAG citation 计算标准 IR 指标；用 paired bootstrap 生成置信区间和模式两两显著性对比；保留 BERTScore 兼容指标、RAGAS 导出、SummaC / AlignScore / SCALE factuality extra 的可选适配。默认路径不新增常驻服务，不改变 GraphRAG 索引和四模式 API，也不从 `results/extraction_eval/` 直接替代问答评测输入。日常路由评测主线只依赖规则指标、IR 指标、BGE-M3 语义覆盖、latency/error 与 bootstrap；LLM judge 降级为抽样校准。

**Tech Stack:** Python 3.10+、Pydantic 2、pandas、numpy、scikit-learn、FlagEmbedding、rouge-score、jieba、ir-measures、requests、pytest；BERTScore、RAGAS、SummaC / AlignScore / SCALE 作为可选 extra，不作为默认必装依赖。

**成熟算法 / 第三方能力选型：**

| 目标 | 采用方案 | 用途 | 为什么不用自研 |
| --- | --- | --- | --- |
| 测试集覆盖 | TF-IDF + KMeans + MMR | 从 text units 中挑出章节/概念分布均衡的候选题材 | 已有 scikit-learn，简单稳定，避免人工只围绕少数章节出题 |
| 长答案语义覆盖 | BGE-M3 chunked semantic coverage | 将 `answer` 与 `gold_answer_summary` 分块后计算 coverage precision / recall / F1 | 比整段 BERTScore 更适合中文长答案、多知识点覆盖和后续路由比较 |
| 极低成本 baseline | ROUGE-Lsum + keyword recall | 不加载大模型时快速检查答案与参考摘要的字面覆盖 | 可用于 CI、快速回归和 BGE-M3 异常时的兜底信号 |
| 检索质量 | `ir-measures` 的 R@k / RR / nDCG@k / AP | 基于 `gold_text_unit_ids` 与答案引用顺序评估引用/检索命中 | 直接调用成熟 IR 库，避免手写公式和文档承诺不一致 |
| 兼容语义指标 | BERTScore F1（可选） | 与旧计划或外部报告保持可比性 | 不放入默认 `eval` 依赖，避免模型下载和 512 token 限制影响日常评测 |
| RAG 质量对齐 | RAGAS 兼容导出，可选运行 faithfulness / answer_correctness / context_precision | 与主流 RAG 评测口径对齐 | 默认不绑定依赖，避免 LangChain / provider 适配拖慢 MVP |
| Factuality extra | SummaC / AlignScore / SCALE 可选适配 | 对长答案事实一致性做额外复核 | 英文偏置和依赖较重，只用于专项复核，不进入默认路由评分 |
| 四模式可信比较 | paired bootstrap | 输出均值 95% CI、pairwise win-rate、差值置信区间 | 30~50 题样本较小，单看均值容易误判 |

**前置依赖：**

- 已有或同时实施 `2026-05-12-qa-baseline-test-set-and-eval.md` 中的基础 schema、runner、raw 输出和报告目录结构，且基础计划的现有 `output/` 完整性检查与四模式 smoke query 已通过。
- `graphrag_pipeline/output/text_units.parquet` 必须存在，候选题材生成至少依赖 `id` 与 `text` 列；完整 citation 映射还依赖 `human_readable_id` 列。完整 QA baseline 仍依赖 `output/*.parquet` 与 `output/lancedb/`。
- `output/relationships.parquet` 与 `output/community_reports.parquet` 可选；缺失时候选题材生成器只生成 factual / chapter 类候选。
- 基础计划已经提供 `graphrag_pipeline/scripts/qa_eval/test_set_schema.py`：`QaTestItem.category` 是 `QuestionCategory` 枚举，使用 `.value` 输出；`gold_text_unit_ids` 会按 `TEXT_UNIT_ID_PREFIX_LEN = 12` 归一化为 text unit 前缀。本计划必须复用该常量并增加前缀碰撞审计，不能在算法层单独改为 16 位或全 ID。
- 引用抽取必须支持当前 GraphRAG 输出里的 `Text Units`、`Sources`、`Entities`、`Relationships`、`Reports` citation。已有 `graphrag_pipeline/scripts/qa_eval/text_unit_lookup.py` 是本计划的前置公共接口，不在本计划中重写；它必须提供 `DataCitationLookup(sources_by_human_id, entities_by_human_id, relationships_by_human_id, reports_by_human_id)`、`resolve_answer_refs(answer: str) -> list[str]`、`extract_data_citations_from_answer(answer: str)` 与 `load_data_citation_lookup(text_units_parquet_path: Path) -> DataCitationLookup`。`load_data_citation_lookup` 以 `text_units.parquet` 为入口，并读取同目录的 `entities.parquet`、`relationships.parquet`、`community_reports.parquet`、`communities.parquet` 来把 Reports / Sources / Entities / Relationships 映射回 text unit 前缀；可选 parquet 缺失时对应映射为空，但 `text_units.parquet` 必须存在。
- 本计划不要求、也不允许隐式运行 `graphrag index`。如果需要修复缺失产物或重建索引，必须单独确认。
- `results/extraction_eval/runs/.../*.json` 与 `results/extraction_raw/runs/.../*.jsonl` 只能作为抽取质量参考，不能直接替代四模式问答评测需要的 GraphRAG `output/` 查询产物。
- BGE-M3 首次运行会下载模型；离线环境可通过 `CKQA_BGE_M3_MODEL` 指向本地模型目录。BERTScore 仅在安装 `semantic-compat` extra 且显式开启时运行，可通过 `CKQA_BERTSCORE_MODEL` 指向本地模型目录。

**完成判据：**

1. `graphrag_pipeline/pyproject.toml` 新增 `eval` optional extra，包含 `FlagEmbedding`、`rouge-score`、`jieba` 与 `ir-measures`；`semantic-compat` 独立包含 `bert-score`；`ragas` 与 `factuality-extra` 独立为可选 extra。
2. `python -m graphrag_pipeline.scripts.qa_eval.algorithmic_seed_builder --output graphrag_pipeline/data/eval/qa_candidate_seeds.jsonl --max-items 80` 能从当前 `output/text_units.parquet` 生成候选题材，且每条含 `source_text_unit_ids`。
3. `python -m graphrag_pipeline.scripts.qa_eval.algorithmic_scorer --run-dir <run_dir>` 能产出 `algorithmic_scoring.{csv,json,md}`，包含 `semantic_coverage_precision / semantic_coverage_recall / semantic_coverage_f1 / rouge_lsum / keyword_recall / citation_recall_at_3 / citation_rr / citation_ndcg_at_5 / elapsed_seconds / error_count / effective_score_experimental`；首次启用 BGE-M3 时需在 run 目录或评测记录中说明 0.50 / 0.60 / 0.65 / 0.70 阈值抽样校准结论，默认 `0.62` 只能作为初始候选值。
4. `python -m graphrag_pipeline.scripts.qa_eval.significance_reporter --run-dir <run_dir>` 能产出 `significance.md`，包含四模式均值 95% CI、pairwise comparison，以及 category 样本数低于 15 时的明确警告；小样本警告不能静默省略。
5. `python -m graphrag_pipeline.scripts.qa_eval.ragas_exporter --run-dir <run_dir>` 能产出 `ragas_dataset.jsonl`；`factuality_extra_exporter` 能产出 SummaC / AlignScore / SCALE 兼容输入；未安装可选依赖时不会失败，只提示如何安装。
6. `pytest graphrag_pipeline/tests/test_qa_eval_algorithmic_*.py graphrag_pipeline/tests/test_qa_eval_bootstrap_stats.py graphrag_pipeline/tests/test_qa_eval_ragas_exporter.py graphrag_pipeline/tests/test_qa_eval_factuality_extra_exporter.py -v` 全绿。
7. `.gitignore` 覆盖 `graphrag_pipeline/results/qa_eval/runs/*/ragas_raw/`、`factuality_raw/` 与大体积模型/缓存产物，不提交下载模型或 raw judge 结果。

---

## File Structure

| 路径 | 责任 |
| --- | --- |
| `graphrag_pipeline/pyproject.toml` | 新增 `eval` / `semantic-compat` / `ragas` / `factuality-extra` optional extras |
| `graphrag_pipeline/scripts/qa_eval/algorithmic_seed_builder.py` | TF-IDF + KMeans + MMR 候选题材生成器 |
| `graphrag_pipeline/scripts/qa_eval/run_loader.py` | 读取 run meta、raw mode answer、测试集 schema、索引产物路径，并审计 text unit 前缀碰撞 |
| `graphrag_pipeline/scripts/qa_eval/text_unit_lookup.py` | 既有基础模块；提供 `DataCitationLookup`、`extract_data_citations_from_answer`、`load_data_citation_lookup`，把 Reports / Sources / Entities / Relationships citation 映射回 text unit 前缀 |
| `graphrag_pipeline/scripts/qa_eval/citation_extractor.py` | 从 GraphRAG 回答中抽取 Text Unit / Sources / Entities / Relationships / Reports 引用顺序并映射到 text unit 前缀 |
| `graphrag_pipeline/scripts/qa_eval/ir_metrics.py` | 调用 `ir-measures` 计算 R@k / RR / nDCG@k / AP |
| `graphrag_pipeline/scripts/qa_eval/semantic_similarity.py` | BGE-M3 分块语义覆盖、ROUGE-Lsum、keyword recall 与可选 BERTScore 兼容指标 |
| `graphrag_pipeline/scripts/qa_eval/algorithmic_scorer.py` | 汇总规则指标 + IR 指标 + BGE-M3 语义覆盖 + latency/error，输出 algorithmic_scoring；`effective_score_experimental` 只作为待校准候选路由分 |
| `graphrag_pipeline/scripts/qa_eval/bootstrap_stats.py` | paired bootstrap 均值 CI 与模式差异比较；保留普通百分位法并暴露小样本限制 |
| `graphrag_pipeline/scripts/qa_eval/significance_reporter.py` | 输出 `significance.md`，低样本 category 明确警告 |
| `graphrag_pipeline/scripts/qa_eval/ragas_exporter.py` | 导出 RAGAS 兼容数据集，可选运行 RAGAS |
| `graphrag_pipeline/scripts/qa_eval/factuality_extra_exporter.py` | 导出 SummaC / AlignScore / SCALE 兼容输入，可选运行已安装的 factuality extra |
| `graphrag_pipeline/data/eval/qa_candidate_seeds.jsonl` | 机器生成候选题材，不作为 gold set |
| `graphrag_pipeline/results/qa_eval/runs/<run_id>/algorithmic_scoring.{csv,json,md}` | 算法指标产物 |
| `graphrag_pipeline/results/qa_eval/runs/<run_id>/semantic_threshold_calibration.md` | BGE-M3 阈值抽样校准记录，首次启用或换语料时生成 |
| `graphrag_pipeline/results/qa_eval/runs/<run_id>/significance.md` | 四模式显著性报告 |
| `graphrag_pipeline/results/qa_eval/runs/<run_id>/ragas_dataset.jsonl` | RAGAS 兼容导出 |
| `graphrag_pipeline/results/qa_eval/runs/<run_id>/factuality_extra_dataset.jsonl` | factuality extra 兼容导出 |
| `graphrag_pipeline/tests/test_qa_eval_algorithmic_seed_builder.py` | 候选题材生成测试 |
| `graphrag_pipeline/tests/test_qa_eval_algorithmic_metrics.py` | 引用抽取、IR 指标、BGE-M3 mock、ROUGE / keyword baseline 与可选 BERTScore 测试 |
| `graphrag_pipeline/tests/test_qa_eval_bootstrap_stats.py` | bootstrap 显著性测试 |
| `graphrag_pipeline/tests/test_qa_eval_ragas_exporter.py` | RAGAS 导出测试 |
| `graphrag_pipeline/tests/test_qa_eval_factuality_extra_exporter.py` | factuality extra 导出测试 |
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
    assert "FlagEmbedding" in eval_extra
    assert "rouge-score" in eval_extra
    assert "jieba" in eval_extra
    assert "ir-measures" in eval_extra


def test_heavy_and_compat_metrics_are_kept_optional():
    pyproject = tomllib.loads(Path("graphrag_pipeline/pyproject.toml").read_text(encoding="utf-8"))
    extras = pyproject["project"]["optional-dependencies"]

    eval_extra = "\n".join(extras["eval"])
    assert "bert-score" not in eval_extra
    assert "bert-score" in "\n".join(extras["semantic-compat"])
    assert "ragas" in extras
    assert "factuality-extra" in extras
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
    "FlagEmbedding>=1.2.10",
    "rouge-score>=0.1.2",
    "jieba>=0.42.1",
    "ir-measures>=0.3.5",
]
semantic-compat = [
    # Optional compatibility metric for historical BERTScore reports
    "bert-score>=0.3.13",
]
ragas = [
    # Optional RAG evaluation compatibility layer
    "ragas>=0.2.0",
]
factuality-extra = [
    # SCALE has a PyPI package; SummaC / AlignScore are usually installed from their upstream repos.
    "scale-score>=0.2.0",
]
all = [
    "graphrag-pipeline[ml,graph,scraper,eval]",
]
```

保留既有 `ml` / `graph` / `scraper` 内容，只把 `all` 改为包含 `eval`。

> 后续 Task 3/4/5 的测试会实际导入 `ir-measures`、`rouge-score`、`jieba` 等 `eval` 依赖。若当前环境尚未安装，先运行 `conda run -n graphrag-oneapi python -m pip install -e ".[eval]"`；离线环境需要提前准备本地 wheel/cache 或本地模型目录。

- [ ] **Step 1.4: 更新 `.gitignore`**

追加：

```gitignore
# QA eval optional third-party outputs
graphrag_pipeline/results/qa_eval/runs/*/ragas_raw/
graphrag_pipeline/results/qa_eval/runs/*/ragas_scoring.json
graphrag_pipeline/results/qa_eval/runs/*/factuality_raw/
graphrag_pipeline/results/qa_eval/runs/*/factuality_scoring.json
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
    parser.add_argument("--text-units", type=Path, default=Path("graphrag_pipeline/output/text_units.parquet"))
    parser.add_argument("--output", type=Path, default=Path("graphrag_pipeline/data/eval/qa_candidate_seeds.jsonl"))
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
- Existing: `graphrag_pipeline/scripts/qa_eval/text_unit_lookup.py`
- Create: `graphrag_pipeline/scripts/qa_eval/citation_extractor.py`
- Create: `graphrag_pipeline/scripts/qa_eval/ir_metrics.py`
- Create: `graphrag_pipeline/tests/test_qa_eval_algorithmic_metrics.py`

- [ ] **Step 3.1: 写引用抽取和 IR 失败测试**

写到 `graphrag_pipeline/tests/test_qa_eval_algorithmic_metrics.py`：

```python
from __future__ import annotations

from pathlib import Path

import pandas as pd

from graphrag_pipeline.scripts.qa_eval.citation_extractor import extract_text_unit_refs
from graphrag_pipeline.scripts.qa_eval.ir_metrics import score_ranked_citations
from graphrag_pipeline.scripts.qa_eval.text_unit_lookup import DataCitationLookup, load_data_citation_lookup


def test_extract_text_unit_refs_handles_graphrag_data_format():
    answer = "DBSCAN 的参数是 eps 和 MinPts。[Data: Text Units (d244f9016ac8, 81d99ad61e36)]"
    assert extract_text_unit_refs(answer) == ["d244f9016ac8", "81d99ad61e36"]


def test_extract_text_unit_refs_deduplicates_in_order():
    answer = "[Data: Text Units (aaaabbbbcccc, aaaabbbbcccc, ddddeeeeffff)]"
    assert extract_text_unit_refs(answer) == ["aaaabbbbcccc", "ddddeeeeffff"]


def test_extract_text_unit_refs_resolves_reports_sources_entities_and_relationships():
    lookup = DataCitationLookup(
        reports_by_human_id={"21": ["report111111", "shared000000"]},
        sources_by_human_id={"5": ["source222222"]},
        entities_by_human_id={"7": ["entity333333"]},
        relationships_by_human_id={"9": ["shared000000", "rel444444444"]},
    )
    answer = "总结 [Data: Reports (21); Sources (5); Entities (7); Relationships (9)]"

    assert extract_text_unit_refs(answer, data_citation_lookup=lookup) == [
        "report111111",
        "shared000000",
        "source222222",
        "entity333333",
        "rel444444444",
    ]


def test_load_data_citation_lookup_public_contract_maps_existing_reference_kinds(tmp_path: Path):
    text_units = tmp_path / "text_units.parquet"
    pd.DataFrame(
        [
            {"id": "222222222222abcdef", "human_readable_id": 5, "text": "来源 text unit"},
        ]
    ).to_parquet(text_units)
    pd.DataFrame(
        [
            {"human_readable_id": 7, "text_unit_ids": ["333333333333abcdef"]},
        ]
    ).to_parquet(tmp_path / "entities.parquet")
    pd.DataFrame(
        [
            {"human_readable_id": 9, "text_unit_ids": ["000000000000abcdef", "444444444444abcdef"]},
        ]
    ).to_parquet(tmp_path / "relationships.parquet")
    pd.DataFrame(
        [
            {"community": 21, "text_unit_ids": ["111111111111abcdef", "000000000000abcdef"]},
        ]
    ).to_parquet(tmp_path / "communities.parquet")
    pd.DataFrame(
        [
            {"human_readable_id": 21, "community": 21},
        ]
    ).to_parquet(tmp_path / "community_reports.parquet")

    lookup = load_data_citation_lookup(text_units)
    refs = lookup.resolve_answer_refs(
        "总结 [Data: Reports (21); Sources (5); Entities (7); Relationships (9)]"
    )

    assert refs == [
        "111111111111",
        "000000000000",
        "222222222222",
        "333333333333",
        "444444444444",
    ]


def test_score_ranked_citations_computes_recall_rr_ndcg():
    scores = score_ranked_citations(
        question_id="Q001",
        ranked_refs=["a11111111111", "b22222222222", "c33333333333"],
        gold_refs=["b22222222222", "x99999999999"],
        cutoffs=[1, 3, 5],
    )
    assert scores["citation_recall_at_1"] == 0.0
    assert scores["citation_recall_at_3"] == 0.5
    assert scores["citation_rr"] == 0.5
    assert 0.0 < scores["citation_ndcg_at_5"] <= 1.0


def test_score_ranked_citations_returns_zeroes_for_empty_run_with_gold_refs():
    scores = score_ranked_citations(
        question_id="Q002",
        ranked_refs=[],
        gold_refs=["b22222222222"],
        cutoffs=[1, 3],
    )

    assert scores["citation_recall_at_1"] == 0.0
    assert scores["citation_recall_at_3"] == 0.0
    assert scores["citation_rr"] == 0.0
    assert scores["citation_map"] == 0.0
```

- [ ] **Step 3.2: 跑测试确认失败**

Run: `pytest graphrag_pipeline/tests/test_qa_eval_algorithmic_metrics.py -v`
Expected: FAIL，ImportError。

- [ ] **Step 3.3: 实现引用抽取**

写到 `graphrag_pipeline/scripts/qa_eval/citation_extractor.py`：

```python
from __future__ import annotations

import re

from graphrag_pipeline.scripts.qa_eval.test_set_schema import TEXT_UNIT_ID_PREFIX_LEN
from graphrag_pipeline.scripts.qa_eval.text_unit_lookup import DataCitationLookup


TEXT_UNITS_BLOCK_RE = re.compile(r"Text Units?\s*\(([^)]*)\)", re.IGNORECASE)
HEXISH_RE = re.compile(r"[A-Za-z0-9][A-Za-z0-9_-]{7,}")


def _append_unique(refs: list[str], raw: str) -> None:
    normalized = (raw or "").strip()[:TEXT_UNIT_ID_PREFIX_LEN]
    if normalized and normalized not in refs:
        refs.append(normalized)


def extract_text_unit_refs(
    answer: str,
    *,
    data_citation_lookup: DataCitationLookup | None = None,
) -> list[str]:
    refs: list[str] = []
    for block in TEXT_UNITS_BLOCK_RE.findall(answer or ""):
        for match in HEXISH_RE.findall(block):
            _append_unique(refs, match)

    if data_citation_lookup is not None:
        for prefix in data_citation_lookup.resolve_answer_refs(answer):
            _append_unique(refs, prefix)
    return refs
```

- [ ] **Step 3.4: 实现 IR 指标**

写到 `graphrag_pipeline/scripts/qa_eval/ir_metrics.py`：

```python
from __future__ import annotations

from typing import Iterable

import ir_measures
from ir_measures import AP, Qrel, RR, R, ScoredDoc, nDCG

from graphrag_pipeline.scripts.qa_eval.test_set_schema import TEXT_UNIT_ID_PREFIX_LEN


def _prefix_set(values: Iterable[str]) -> set[str]:
    return {str(value).strip()[:TEXT_UNIT_ID_PREFIX_LEN] for value in values if str(value).strip()}


def score_ranked_citations(
    *,
    question_id: str,
    ranked_refs: list[str],
    gold_refs: list[str],
    cutoffs: list[int] | None = None,
) -> dict[str, float | str]:
    cutoffs = cutoffs or [1, 3, 5]
    gold = _prefix_set(gold_refs)
    ranked = [ref[:TEXT_UNIT_ID_PREFIX_LEN] for ref in ranked_refs if ref[:TEXT_UNIT_ID_PREFIX_LEN]]
    out: dict[str, float | str] = {"question_id": question_id}

    if not gold:
        for cutoff in cutoffs:
            out[f"citation_recall_at_{cutoff}"] = 0.0
            out[f"citation_ndcg_at_{cutoff}"] = 0.0
        out["citation_rr"] = 0.0
        out["citation_map"] = 0.0
        return out

    if not ranked:
        for cutoff in cutoffs:
            out[f"citation_recall_at_{cutoff}"] = 0.0
            out[f"citation_ndcg_at_{cutoff}"] = 0.0
        out["citation_rr"] = 0.0
        out["citation_map"] = 0.0
        return out

    qrels = [Qrel(question_id, ref, 1) for ref in gold]
    run = [
        ScoredDoc(question_id, ref, float(len(ranked) - index))
        for index, ref in enumerate(ranked)
    ]
    metrics = []
    for cutoff in cutoffs:
        metrics.extend([R @ cutoff, nDCG @ cutoff])
    metrics.extend([RR, AP])
    values = ir_measures.calc_aggregate(metrics, qrels, run)

    for cutoff in cutoffs:
        out[f"citation_recall_at_{cutoff}"] = round(float(values[R @ cutoff]), 4)
        out[f"citation_ndcg_at_{cutoff}"] = round(float(values[nDCG @ cutoff]), 4)
    out["citation_rr"] = round(float(values[RR]), 4)
    out["citation_map"] = round(float(values[AP]), 4)
    return out
```

> 说明：本任务必须真正调用 `ir-measures`，不能只手写“口径相同”的公式。GraphRAG 的 global search 常引用 `Reports`，所以 citation 抽取必须通过 `DataCitationLookup` 映射回 text unit 前缀，否则会系统性低估 global mode。单题输出字段使用 `citation_rr`；后续对多题求均值时，mean(`citation_rr`) 才是 MRR。

- [ ] **Step 3.5: 跑测试通过**

Run: `pytest graphrag_pipeline/tests/test_qa_eval_algorithmic_metrics.py -v`
Expected: 6 PASS。

- [ ] **Step 3.6: 提交**

```bash
git add graphrag_pipeline/scripts/qa_eval/citation_extractor.py \
        graphrag_pipeline/scripts/qa_eval/ir_metrics.py \
        graphrag_pipeline/tests/test_qa_eval_algorithmic_metrics.py
git commit -m "feat(qa_eval): 增加引用抽取与标准检索指标"
```

---

### Task 4: BGE-M3 分块语义覆盖与低成本 baseline

**Files:**
- Create: `graphrag_pipeline/scripts/qa_eval/semantic_similarity.py`
- Modify: `graphrag_pipeline/tests/test_qa_eval_algorithmic_metrics.py`

- [ ] **Step 4.1: 写 BGE-M3 coverage 与 cheap baseline 测试**

追加到 `graphrag_pipeline/tests/test_qa_eval_algorithmic_metrics.py`：

```python
from unittest.mock import patch

from graphrag_pipeline.scripts.qa_eval.semantic_similarity import (
    SemanticScoringConfig,
    score_semantic_similarity,
    split_semantic_chunks,
)


def test_split_semantic_chunks_keeps_long_answers_bounded():
    chunks = split_semantic_chunks(
        "DBSCAN 使用 eps 描述邻域。MinPts 表示核心对象所需的最少样本数。聚类会扩展密度可达对象。",
        max_chunk_chars=24,
    )

    assert len(chunks) >= 2
    assert all(len(chunk) <= 24 for chunk in chunks)


def test_score_semantic_similarity_uses_bge_m3_chunked_coverage():
    with patch(
        "graphrag_pipeline.scripts.qa_eval.semantic_similarity._score_bge_m3_coverage",
        return_value={
            "semantic_coverage_precision": 0.7,
            "semantic_coverage_recall": 0.8,
            "semantic_coverage_f1": 0.7467,
        },
    ):
        result = score_semantic_similarity(
            answer="DBSCAN 的核心参数是 eps 和 MinPts。",
            reference="DBSCAN 的两个核心超参数是 eps 和 MinPts。",
            config=SemanticScoringConfig(enable_bge_m3=True, enable_bertscore=False),
        )

    assert result["semantic_coverage_precision"] == 0.7
    assert result["semantic_coverage_recall"] == 0.8
    assert result["semantic_coverage_f1"] == 0.7467
    assert result["rouge_lsum"] > 0
    assert result["keyword_recall"] > 0


def test_score_semantic_similarity_can_run_without_bge_for_fast_baseline():
    result = score_semantic_similarity(
        answer="操作系统管理处理机、存储器、设备和文件。",
        reference="操作系统负责管理处理机、存储器、I/O 设备和文件资源。",
        config=SemanticScoringConfig(enable_bge_m3=False, enable_bertscore=False),
    )

    assert result["semantic_coverage_f1"] == 0.0
    assert result["rouge_lsum"] > 0
    assert result["keyword_recall"] > 0
```

- [ ] **Step 4.2: 跑测试确认失败**

Run: `pytest graphrag_pipeline/tests/test_qa_eval_algorithmic_metrics.py::test_score_semantic_similarity_uses_bge_m3_chunked_coverage -v`
Expected: FAIL，ImportError。

- [ ] **Step 4.3: 实现 BGE-M3 分块 coverage、ROUGE-Lsum 与 keyword recall**

写到 `graphrag_pipeline/scripts/qa_eval/semantic_similarity.py`：

```python
from __future__ import annotations

import os
import re
from dataclasses import dataclass
from functools import lru_cache
from typing import Any

import jieba
import jieba.analyse
import numpy as np
from rouge_score import rouge_scorer


DEFAULT_BGE_M3_MODEL = "BAAI/bge-m3"
DEFAULT_BERTSCORE_MODEL = "bert-base-chinese"


@dataclass(slots=True)
class SemanticScoringConfig:
    bge_m3_model: str | None = None
    bge_max_length: int = 8192
    max_chunk_chars: int = 260
    # 初始候选阈值；首次用于新语料前必须按本任务的阈值校准步骤验证。
    similarity_threshold: float = 0.62
    enable_bge_m3: bool = True
    enable_bertscore: bool = False
    bertscore_model: str | None = None


def split_semantic_chunks(text: str, *, max_chunk_chars: int = 260) -> list[str]:
    parts = [part.strip() for part in re.split(r"(?<=[。！？；;.!?\n])", text or "") if part.strip()]
    if not parts:
        return []
    chunks: list[str] = []
    current = ""
    for part in parts:
        if not current:
            current = part
        elif len(current) + len(part) <= max_chunk_chars:
            current += part
        else:
            chunks.append(current[:max_chunk_chars])
            current = part
    if current:
        chunks.append(current[:max_chunk_chars])
    return chunks


def _tokenize_for_rouge(text: str) -> str:
    return " ".join(token for token in jieba.lcut(text or "") if token.strip())


def _score_cheap_baselines(answer: str, reference: str) -> dict[str, float]:
    if not (answer or "").strip() or not (reference or "").strip():
        return {"rouge_lsum": 0.0, "keyword_recall": 0.0}

    # 中文先用 jieba 分词再交给 rouge-score；这里没有保留换行句界，
    # 因此 rougeLsum 实际接近单段 rougeL，只作为低成本回归信号。
    scorer = rouge_scorer.RougeScorer(["rougeLsum"], use_stemmer=False)
    rouge = scorer.score(_tokenize_for_rouge(reference), _tokenize_for_rouge(answer))["rougeLsum"].fmeasure
    keywords = [word for word in jieba.analyse.extract_tags(reference, topK=12) if len(word.strip()) >= 2]
    if not keywords:
        keyword_recall = 0.0
    else:
        keyword_recall = sum(1 for word in keywords if word in answer) / len(keywords)
    return {
        "rouge_lsum": round(float(rouge), 4),
        "keyword_recall": round(float(keyword_recall), 4),
    }


@lru_cache(maxsize=2)
def _load_bge_m3_model(model_name: str) -> Any:
    try:
        from FlagEmbedding import BGEM3FlagModel
    except ImportError as exc:
        raise RuntimeError('BGE-M3 scoring requires `pip install -e ".[eval]"`.') from exc
    return BGEM3FlagModel(model_name, use_fp16=False)


def _encode_dense_chunks(chunks: list[str], *, model_name: str, max_length: int) -> np.ndarray:
    model = _load_bge_m3_model(model_name)
    encoded = model.encode(
        chunks,
        batch_size=8,
        max_length=max_length,
        return_dense=True,
        return_sparse=False,
        return_colbert_vecs=False,
    )
    vectors = np.asarray(encoded["dense_vecs"], dtype=np.float32)
    norms = np.linalg.norm(vectors, axis=1, keepdims=True)
    return vectors / np.maximum(norms, 1e-12)


def _coverage_from_similarity(matrix: np.ndarray, *, threshold: float) -> dict[str, float]:
    if matrix.size == 0:
        return {
            "semantic_coverage_precision": 0.0,
            "semantic_coverage_recall": 0.0,
            "semantic_coverage_f1": 0.0,
        }
    answer_best = matrix.max(axis=1)
    reference_best = matrix.max(axis=0)
    precision = float(np.mean(answer_best >= threshold))
    recall = float(np.mean(reference_best >= threshold))
    f1 = 0.0 if precision + recall == 0 else 2 * precision * recall / (precision + recall)
    return {
        "semantic_coverage_precision": round(precision, 4),
        "semantic_coverage_recall": round(recall, 4),
        "semantic_coverage_f1": round(f1, 4),
    }


def _score_bge_m3_coverage(
    *,
    answer_chunks: list[str],
    reference_chunks: list[str],
    config: SemanticScoringConfig,
) -> dict[str, float]:
    if not answer_chunks or not reference_chunks:
        return _coverage_from_similarity(np.empty((0, 0)), threshold=config.similarity_threshold)
    model_name = config.bge_m3_model or os.environ.get("CKQA_BGE_M3_MODEL", DEFAULT_BGE_M3_MODEL)
    answer_vectors = _encode_dense_chunks(answer_chunks, model_name=model_name, max_length=config.bge_max_length)
    reference_vectors = _encode_dense_chunks(reference_chunks, model_name=model_name, max_length=config.bge_max_length)
    return _coverage_from_similarity(answer_vectors @ reference_vectors.T, threshold=config.similarity_threshold)


def _score_optional_bertscore(answer: str, reference: str, config: SemanticScoringConfig) -> dict[str, float | None]:
    if not config.enable_bertscore:
        return {
            "bertscore_precision": None,
            "bertscore_recall": None,
            "bertscore_f1": None,
        }
    try:
        from bert_score import score as bert_score
    except ImportError as exc:
        raise RuntimeError('BERTScore requires `pip install -e ".[semantic-compat]"`.') from exc

    model = config.bertscore_model or os.environ.get("CKQA_BERTSCORE_MODEL", DEFAULT_BERTSCORE_MODEL)
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


def score_semantic_similarity(
    *,
    answer: str,
    reference: str,
    config: SemanticScoringConfig | None = None,
) -> dict[str, float | str | None]:
    config = config or SemanticScoringConfig()
    if not (answer or "").strip() or not (reference or "").strip():
        return {
            "semantic_coverage_precision": 0.0,
            "semantic_coverage_recall": 0.0,
            "semantic_coverage_f1": 0.0,
            "rouge_lsum": 0.0,
            "keyword_recall": 0.0,
            "bertscore_precision": None,
            "bertscore_recall": None,
            "bertscore_f1": None,
            "semantic_model": "none",
        }

    answer_chunks = split_semantic_chunks(answer, max_chunk_chars=config.max_chunk_chars)
    reference_chunks = split_semantic_chunks(reference, max_chunk_chars=config.max_chunk_chars)
    cheap = _score_cheap_baselines(answer, reference)
    if config.enable_bge_m3:
        coverage = _score_bge_m3_coverage(
            answer_chunks=answer_chunks,
            reference_chunks=reference_chunks,
            config=config,
        )
        semantic_model = config.bge_m3_model or os.environ.get("CKQA_BGE_M3_MODEL", DEFAULT_BGE_M3_MODEL)
    else:
        coverage = {
            "semantic_coverage_precision": 0.0,
            "semantic_coverage_recall": 0.0,
            "semantic_coverage_f1": 0.0,
        }
        semantic_model = "cheap-baseline-only"
    return {
        **coverage,
        **cheap,
        **_score_optional_bertscore(answer, reference, config),
        "semantic_model": semantic_model,
    }
```

- [ ] **Step 4.4: 跑测试通过**

Run: `pytest graphrag_pipeline/tests/test_qa_eval_algorithmic_metrics.py -v`
Expected: 全部 PASS。

- [ ] **Step 4.5: 记录 BGE-M3 阈值校准结论**

首次在当前课程语料上启用 BGE-M3 语义覆盖时，抽取至少 10 条人工已知质量的样例（建议好答案 ≥5、差答案 ≥5，优先覆盖 factual / relation / summary / overview 四类），分别用 `SemanticScoringConfig(similarity_threshold=0.50 / 0.60 / 0.65 / 0.70)` 跑 `score_semantic_similarity`，在 `<run_dir>/semantic_threshold_calibration.md` 或同等评测记录中写明：

- 各阈值下好答案与差答案的 `semantic_coverage_f1` 分布。
- 默认 `0.62` 是否能稳定拉开好/差答案；若不能，记录采用的新阈值与原因。
- 该阈值仍是当前语料的经验参数，后续换教材、换模型或换 chunk 策略时需要重新校准。

本步骤不要求把人工校准样例提交为 gold set，也不进入 CI；它是防止 `semantic_coverage_f1` 因阈值过松或过紧而失去区分度的上线前守卫。

- [ ] **Step 4.6: 提交**

```bash
git add graphrag_pipeline/scripts/qa_eval/semantic_similarity.py \
        graphrag_pipeline/tests/test_qa_eval_algorithmic_metrics.py
git commit -m "feat(qa_eval): 增加 BGE-M3 分块语义覆盖指标"
```

---

### Task 5: algorithmic_scorer 汇总器

**Files:**
- Create: `graphrag_pipeline/scripts/qa_eval/run_loader.py`
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
from graphrag_pipeline.scripts.qa_eval.run_loader import load_raw_mode_answer, load_test_set


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
    (raw_dir / "Q001.json").write_text(
        json.dumps(
            {
                "question_id": "Q001",
                "modes": {
                    "graphrag-local-search:latest": {
                        "answer": "DBSCAN 的核心参数是 eps 和 MinPts。[Data: Text Units (d244f9016ac8)]",
                        "elapsed_seconds": 1.5,
                    }
                },
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
        return_value={
            "semantic_coverage_precision": 0.9,
            "semantic_coverage_recall": 0.8,
            "semantic_coverage_f1": 0.8471,
            "rouge_lsum": 0.8,
            "keyword_recall": 1.0,
            "bertscore_precision": None,
            "bertscore_recall": None,
            "bertscore_f1": None,
            "semantic_model": "BAAI/bge-m3",
        },
    ):
        summary = score_run_algorithmically(run_dir, test_set_path=test_set)

    assert summary["rows"][0]["semantic_coverage_f1"] == 0.8471
    assert summary["rows"][0]["citation_recall_at_3"] == 1.0
    assert summary["rows"][0]["entity_hit_rate"] == 1.0
    assert summary["rows"][0]["elapsed_seconds"] == 1.5
    assert summary["rows"][0]["error_count"] == 0
    assert 0 < summary["rows"][0]["effective_score_experimental"] <= 1
    assert (run_dir / "algorithmic_scoring.csv").exists()
    assert (run_dir / "algorithmic_scoring.json").exists()
    assert (run_dir / "algorithmic_scoring.md").exists()


def test_run_loader_exposes_public_helpers(tmp_path: Path):
    run_dir, test_set = _write_run(tmp_path)

    assert list(load_test_set(test_set)) == ["Q001"]
    raw = load_raw_mode_answer(run_dir, "Q001", "graphrag-local-search:latest")
    assert raw.answer.startswith("DBSCAN")
    assert raw.elapsed_seconds == 1.5
```

- [ ] **Step 5.2: 跑测试确认失败**

Run: `pytest graphrag_pipeline/tests/test_qa_eval_algorithmic_scorer.py -v`
Expected: FAIL，ImportError。

- [ ] **Step 5.3: 实现汇总器**

写到 `graphrag_pipeline/scripts/qa_eval/run_loader.py`：

```python
from __future__ import annotations

import json
from dataclasses import dataclass
from pathlib import Path
from typing import Any

import pandas as pd

from graphrag_pipeline.scripts.qa_eval.test_set_schema import QaTestItem, TEXT_UNIT_ID_PREFIX_LEN


DEFAULT_TEXT_UNITS_PATH = Path("graphrag_pipeline/output/text_units.parquet")


@dataclass(frozen=True, slots=True)
class RawModeAnswer:
    answer: str
    elapsed_seconds: float
    error_count: int


def safe_mode(mode: str) -> str:
    return mode.replace(":", "-").replace("/", "-")


def load_run_meta(run_dir: Path) -> dict[str, Any]:
    return json.loads((run_dir / "run_meta.json").read_text(encoding="utf-8"))


def load_test_set(path: Path) -> dict[str, QaTestItem]:
    items: dict[str, QaTestItem] = {}
    for raw in path.read_text(encoding="utf-8").splitlines():
        if raw.strip():
            item = QaTestItem.model_validate_json(raw)
            items[item.id] = item
    return items


def load_raw_mode_answer(run_dir: Path, question_id: str, mode: str) -> RawModeAnswer:
    current_path = run_dir / "raw" / f"{question_id}.json"
    if current_path.exists():
        payload = json.loads(current_path.read_text(encoding="utf-8"))
        mode_payload = payload.get("modes", {}).get(mode, {})
        answer = str(mode_payload.get("answer", ""))
        error = str(mode_payload.get("error", "") or "")
        return RawModeAnswer(
            answer=answer,
            elapsed_seconds=float(mode_payload.get("elapsed_seconds") or 0.0),
            error_count=1 if error else 0,
        )

    legacy_path = run_dir / "raw" / f"{question_id}_{safe_mode(mode)}.json"
    payload = json.loads(legacy_path.read_text(encoding="utf-8"))
    error = str(payload.get("error", "") or "")
    return RawModeAnswer(
        answer=str(payload.get("answer", "")),
        elapsed_seconds=float(payload.get("elapsed_seconds") or 0.0),
        error_count=1 if error else 0,
    )


def resolve_text_units_path(run_dir: Path, explicit_path: Path | None = None) -> Path:
    if explicit_path is not None:
        resolved = explicit_path
    else:
        meta = load_run_meta(run_dir)
        index_output_dir = str(meta.get("index_output_dir") or "").strip()
        resolved = Path(index_output_dir) / "text_units.parquet" if index_output_dir else DEFAULT_TEXT_UNITS_PATH
    if not resolved.exists():
        raise FileNotFoundError(f"resolved text_units.parquet does not exist: {resolved}")
    return resolved


def audit_text_unit_prefix_collisions(text_units_path: Path) -> dict[str, list[str]]:
    frame = pd.read_parquet(text_units_path, columns=["id"])
    buckets: dict[str, list[str]] = {}
    for raw_id in frame["id"].astype(str):
        prefix = raw_id[:TEXT_UNIT_ID_PREFIX_LEN]
        bucket = buckets.setdefault(prefix, [])
        if raw_id not in bucket:
            bucket.append(raw_id)
    return {prefix: ids for prefix, ids in buckets.items() if len(ids) > 1}
```

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
from graphrag_pipeline.scripts.qa_eval.run_loader import (
    audit_text_unit_prefix_collisions,
    load_raw_mode_answer,
    load_run_meta,
    load_test_set,
    resolve_text_units_path,
)
from graphrag_pipeline.scripts.qa_eval.semantic_similarity import score_semantic_similarity
from graphrag_pipeline.scripts.qa_eval.text_unit_lookup import DataCitationLookup, load_data_citation_lookup


def _hit_rate(answer: str, terms: list[str]) -> float:
    if not terms:
        return 1.0
    hits = sum(1 for term in terms if term and term in answer)
    return round(hits / len(terms), 4)


def _negative_hit(answer: str, terms: list[str]) -> float:
    if not terms:
        return 0.0
    return 1.0 if any(term and term in answer for term in terms) else 0.0


def _effective_score_experimental(row: dict[str, Any]) -> float:
    # 占位合成规则：只能作为路由候选信号，必须通过真实路由实验校准后才能上线。
    quality = (
        0.42 * float(row["semantic_coverage_f1"])
        + 0.18 * float(row["citation_recall_at_3"])
        + 0.12 * float(row["citation_rr"])
        + 0.12 * float(row["entity_hit_rate"])
        + 0.08 * float(row["keyword_recall"])
        + 0.08 * float(row["rouge_lsum"])
    )
    latency_penalty = min(float(row["elapsed_seconds"]) / 180.0, 1.0) * 0.08
    risk_penalty = float(row["negative_hit"]) * 0.12 + float(row["error_count"]) * 0.25
    return round(max(0.0, min(1.0, quality - latency_penalty - risk_penalty)), 4)


def _load_optional_data_citation_lookup(run_dir: Path, text_units_path: Path | None) -> DataCitationLookup | None:
    try:
        resolved = resolve_text_units_path(run_dir, text_units_path)
    except FileNotFoundError:
        return None
    collisions = audit_text_unit_prefix_collisions(resolved)
    if collisions:
        sample = ", ".join(f"{prefix}: {ids[:2]}" for prefix, ids in list(collisions.items())[:3])
        raise ValueError(f"text unit prefix collision detected; adjust schema prefix length before scoring: {sample}")
    return load_data_citation_lookup(resolved)


def score_run_algorithmically(
    run_dir: Path,
    *,
    test_set_path: Path | None = None,
    text_units_path: Path | None = None,
) -> dict[str, Any]:
    meta = load_run_meta(run_dir)
    test_set = test_set_path or Path(meta["test_set_path"])
    items = load_test_set(test_set)
    data_citation_lookup = _load_optional_data_citation_lookup(run_dir, text_units_path)
    rows: list[dict[str, Any]] = []
    for item in items.values():
        for mode in meta["modes"]:
            raw = load_raw_mode_answer(run_dir, item.id, mode)
            refs = extract_text_unit_refs(raw.answer, data_citation_lookup=data_citation_lookup)
            semantic = score_semantic_similarity(
                answer=raw.answer,
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
                    "answer_chars": len(raw.answer),
                    "entity_hit_rate": _hit_rate(raw.answer, item.gold_entities),
                    "must_cite_hit": _hit_rate(raw.answer, item.must_cite_terms),
                    "negative_hit": _negative_hit(raw.answer, item.negative_terms),
                    "elapsed_seconds": round(raw.elapsed_seconds, 4),
                    "error_count": raw.error_count,
                    **semantic,
                    **{key: value for key, value in ir.items() if key != "question_id"},
                }
            )
            rows[-1]["effective_score_experimental"] = _effective_score_experimental(rows[-1])

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
    parser.add_argument("--text-units-path", type=Path, default=None)
    args = parser.parse_args()
    score_run_algorithmically(args.run_dir, test_set_path=args.test_set, text_units_path=args.text_units_path)
    print(f"wrote algorithmic scoring under {args.run_dir}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
```

- [ ] **Step 5.4: 跑测试通过**

Run: `pytest graphrag_pipeline/tests/test_qa_eval_algorithmic_scorer.py -v`
Expected: 2 PASS。

- [ ] **Step 5.5: 提交**

```bash
git add graphrag_pipeline/scripts/qa_eval/run_loader.py \
        graphrag_pipeline/scripts/qa_eval/algorithmic_scorer.py \
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
from graphrag_pipeline.scripts.qa_eval.significance_reporter import category_sample_warnings


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


def test_category_sample_warnings_flags_small_groups():
    df = pd.DataFrame(
        [
            {"question_id": "Q001", "category": "factual_lookup", "mode": "a", "metric": 0.9},
            {"question_id": "Q002", "category": "factual_lookup", "mode": "a", "metric": 0.8},
        ]
    )

    warnings = category_sample_warnings(df, min_sample_size=15)

    assert warnings
    assert "factual_lookup" in warnings[0]
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

DEFAULT_METRICS = [
    "effective_score_experimental",
    "semantic_coverage_f1",
    "citation_recall_at_3",
    "citation_rr",
    "citation_ndcg_at_5",
    "rouge_lsum",
    "keyword_recall",
]
MIN_CATEGORY_BOOTSTRAP_N = 15


def category_sample_warnings(df: pd.DataFrame, *, min_sample_size: int = MIN_CATEGORY_BOOTSTRAP_N) -> list[str]:
    warnings: list[str] = []
    for (category, mode), group in df.groupby(["category", "mode"]):
        count = int(group["question_id"].nunique())
        if count < min_sample_size:
            warnings.append(
                f"- WARNING: category={category}, mode={mode} 只有 {count} 题，低于 {min_sample_size}；"
                "该分层 CI 只能作探索性参考，不能作为上线判据。"
            )
    return warnings


def write_significance_report(
    run_dir: Path,
    *,
    metrics: list[str] | None = None,
    min_sample_size: int = MIN_CATEGORY_BOOTSTRAP_N,
) -> Path:
    metrics = metrics or DEFAULT_METRICS
    df = pd.read_csv(run_dir / "algorithmic_scoring.csv")
    lines = ["# 四模式显著性对比", ""]
    warnings = category_sample_warnings(df, min_sample_size=min_sample_size)
    if warnings:
        lines += ["## 小样本警告", "", *warnings, ""]
    lines += [
        "> 当前实现使用普通百分位 bootstrap。若后续要比较很小的 route margin，需增加 BCa bootstrap 或扩大测试集后再下结论。",
        "",
    ]
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
Expected: 3 PASS。

- [ ] **Step 6.6: 提交**

```bash
git add graphrag_pipeline/scripts/qa_eval/bootstrap_stats.py \
        graphrag_pipeline/scripts/qa_eval/significance_reporter.py \
        graphrag_pipeline/tests/test_qa_eval_bootstrap_stats.py
git commit -m "feat(qa_eval): 增加四模式 bootstrap 显著性报告"
```

---

### Task 7: RAGAS 与 factuality extra 兼容导出

**Files:**
- Create: `graphrag_pipeline/scripts/qa_eval/ragas_exporter.py`
- Create: `graphrag_pipeline/scripts/qa_eval/factuality_extra_exporter.py`
- Create: `graphrag_pipeline/tests/test_qa_eval_ragas_exporter.py`
- Create: `graphrag_pipeline/tests/test_qa_eval_factuality_extra_exporter.py`

RAGAS、SummaC、AlignScore、SCALE 不作为默认评分闭环的硬依赖。本任务只保证能导出它们所需的稳定 JSONL 字段，并在安装可选依赖时提示如何离线运行；日常路由评测不依赖这些 heavy extra。

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
    (raw_dir / "Q001.json").write_text(
        json.dumps(
            {
                "question_id": "Q001",
                "modes": {
                    "graphrag-local-search:latest": {
                        "answer": "答案",
                        "elapsed_seconds": 1.0,
                    }
                },
            },
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

from graphrag_pipeline.scripts.qa_eval.run_loader import load_raw_mode_answer, load_test_set


def export_ragas_dataset(
    run_dir: Path,
    *,
    contexts_by_question: dict[str, list[str]] | None = None,
) -> Path:
    meta = json.loads((run_dir / "run_meta.json").read_text(encoding="utf-8"))
    items = load_test_set(Path(meta["test_set_path"]))
    contexts_by_question = contexts_by_question or {}
    rows: list[str] = []
    for item in items.values():
        for mode in meta["modes"]:
            raw = load_raw_mode_answer(run_dir, item.id, mode)
            rows.append(
                json.dumps(
                    {
                        "question_id": item.id,
                        "mode": mode,
                        "question": item.question,
                        "answer": raw.answer,
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

- [ ] **Step 7.5: 写 factuality extra 导出测试**

写到 `graphrag_pipeline/tests/test_qa_eval_factuality_extra_exporter.py`：

```python
from __future__ import annotations

import json
from pathlib import Path

from graphrag_pipeline.scripts.qa_eval.factuality_extra_exporter import export_factuality_extra_dataset


def test_export_factuality_extra_dataset_writes_source_and_claims(tmp_path: Path):
    run_dir = tmp_path / "run"
    raw_dir = run_dir / "raw"
    raw_dir.mkdir(parents=True)
    test_set = tmp_path / "qa_test_set.jsonl"
    test_set.write_text(
        json.dumps(
            {
                "id": "Q001",
                "category": "global_overview",
                "question": "操作系统负责什么？",
                "gold_answer_summary": "操作系统负责管理资源并提供接口。",
                "gold_entities": ["操作系统", "资源", "接口"],
                "gold_text_unit_ids": ["tu-001"],
                "must_cite_terms": ["资源"],
                "negative_terms": [],
            },
            ensure_ascii=False,
        )
        + "\n",
        encoding="utf-8",
    )
    (run_dir / "run_meta.json").write_text(
        json.dumps({"modes": ["graphrag-basic-search:latest"], "test_set_path": str(test_set)}),
        encoding="utf-8",
    )
    (raw_dir / "Q001.json").write_text(
        json.dumps(
            {
                "question_id": "Q001",
                "modes": {
                    "graphrag-basic-search:latest": {
                        "answer": "操作系统管理处理机、存储器和文件，并向用户提供接口。",
                        "elapsed_seconds": 1.0,
                    }
                },
            },
            ensure_ascii=False,
        ),
        encoding="utf-8",
    )

    output = export_factuality_extra_dataset(
        run_dir,
        contexts_by_question={"Q001": ["操作系统是资源管理者，并提供用户接口。"]},
    )

    rows = [json.loads(line) for line in output.read_text(encoding="utf-8").splitlines()]
    assert rows[0]["source"] == "操作系统是资源管理者，并提供用户接口。"
    assert rows[0]["claim"] == "操作系统管理处理机、存储器和文件，并向用户提供接口。"
    assert rows[0]["reference"] == "操作系统负责管理资源并提供接口。"
```

- [ ] **Step 7.6: 实现 factuality extra 导出器**

写到 `graphrag_pipeline/scripts/qa_eval/factuality_extra_exporter.py`：

```python
from __future__ import annotations

import argparse
import importlib.util
import json
from pathlib import Path

from graphrag_pipeline.scripts.qa_eval.run_loader import load_raw_mode_answer, load_test_set


def export_factuality_extra_dataset(
    run_dir: Path,
    *,
    contexts_by_question: dict[str, list[str]] | None = None,
) -> Path:
    meta = json.loads((run_dir / "run_meta.json").read_text(encoding="utf-8"))
    items = load_test_set(Path(meta["test_set_path"]))
    contexts_by_question = contexts_by_question or {}
    rows: list[str] = []
    for item in items.values():
        source = "\n".join(contexts_by_question.get(item.id, [])) or item.gold_answer_summary
        for mode in meta["modes"]:
            raw = load_raw_mode_answer(run_dir, item.id, mode)
            rows.append(
                json.dumps(
                    {
                        "question_id": item.id,
                        "mode": mode,
                        "source": source,
                        "claim": raw.answer,
                        "reference": item.gold_answer_summary,
                        "gold_text_unit_ids": item.gold_text_unit_ids,
                        "target_tools": ["summac", "alignscore", "scale"],
                    },
                    ensure_ascii=False,
                )
            )
    output = run_dir / "factuality_extra_dataset.jsonl"
    output.write_text("\n".join(rows) + "\n", encoding="utf-8")
    return output


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--run-dir", type=Path, required=True)
    parser.add_argument("--run-installed", action="store_true")
    args = parser.parse_args()
    output = export_factuality_extra_dataset(args.run_dir)
    print(f"wrote {output}")
    if args.run_installed:
        installed = [name for name in ("summac", "alignscore", "scale_score") if importlib.util.find_spec(name)]
        if installed:
            print(f"installed factuality extras detected: {', '.join(installed)}; use factuality_extra_dataset.jsonl for offline scoring")
        else:
            print("no factuality extra installed; SummaC / AlignScore usually require upstream repo installs, SCALE can use `pip install -e '.[factuality-extra]'`")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
```

- [ ] **Step 7.7: 跑 factuality extra 测试通过**

Run: `pytest graphrag_pipeline/tests/test_qa_eval_factuality_extra_exporter.py -v`
Expected: 1 PASS。

- [ ] **Step 7.8: 提交**

```bash
git add graphrag_pipeline/scripts/qa_eval/ragas_exporter.py \
        graphrag_pipeline/scripts/qa_eval/factuality_extra_exporter.py \
        graphrag_pipeline/tests/test_qa_eval_ragas_exporter.py \
        graphrag_pipeline/tests/test_qa_eval_factuality_extra_exporter.py
git commit -m "feat(qa_eval): 增加 RAGAS 与 factuality extra 兼容导出"
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
  - `semantic_coverage_precision`：答案分块中能被参考摘要分块覆盖的比例，用于发现跑题或冗余。
  - `semantic_coverage_recall`：参考摘要分块被答案覆盖的比例，用于衡量长答案关键点覆盖。
  - `semantic_coverage_f1`：precision / recall 的综合值，默认由 BGE-M3 dense embedding 相似度矩阵计算；默认相似度阈值只是初始候选值，首次在新语料上使用前必须做抽样校准。
  - `rouge_lsum`：极低成本的字面长摘要覆盖 baseline。
  - `keyword_recall`：参考摘要关键词在答案中的覆盖率。
  - `citation_recall_at_3`：答案引用的前 3 个 text unit 覆盖 gold text unit 的比例。
  - `citation_rr`：单题第一个正确引用出现得越靠前越高；报告层对多题求均值时即为 MRR。
  - `citation_ndcg_at_5`：前 5 个引用的排序质量。
  - `elapsed_seconds` / `error_count`：路由决策必须同时考虑的运行代价与稳定性。
  - `effective_score_experimental`：面向路由的候选综合分，默认合成规则指标、IR 指标、BGE-M3 语义覆盖和 latency/error penalty；该权重是待校准占位值，不能作为上线规则直接使用。
  - `bertscore_f1`：可选兼容指标，只有安装 `semantic-compat` 且显式开启时生成，不作为日常主线。
```

- [ ] **Step 8.2: 更新 GraphRAG README**

在 `graphrag_pipeline/README.md` 的 QA 评测段增加：

````markdown
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
python -m graphrag_pipeline.scripts.qa_eval.factuality_extra_exporter --run-dir <run_dir>
```

BERTScore 兼容指标只在需要与旧报告对齐时安装：

```bash
pip install -e ".[semantic-compat]"
```

SummaC / AlignScore / SCALE 只作为专项 factuality extra；其中 SCALE 可通过 `factuality-extra` extra 安装，SummaC 与 AlignScore 通常按上游仓库说明安装后读取 `factuality_extra_dataset.jsonl` 离线运行。
````

- [ ] **Step 8.3: 更新脚本文档**

在 `graphrag_pipeline/scripts/README.md` 增加 `qa_eval` 小节，列出：

```markdown
| 脚本 | 用途 |
| --- | --- |
| `qa_eval/algorithmic_seed_builder.py` | 生成候选题材覆盖清单 |
| `qa_eval/semantic_similarity.py` | 生成 BGE-M3 分块语义覆盖、ROUGE-Lsum、keyword recall 与可选 BERTScore |
| `qa_eval/run_loader.py` | 读取 run/test/raw 与索引路径，审计 text unit 前缀碰撞 |
| `qa_eval/algorithmic_scorer.py` | 生成规则指标 + IR 指标 + BGE-M3 语义覆盖 + latency/error + effective_score_experimental |
| `qa_eval/significance_reporter.py` | 生成四模式 bootstrap 对比，并在 category 样本过小时输出警告 |
| `qa_eval/ragas_exporter.py` | 导出 RAGAS 兼容数据集 |
| `qa_eval/factuality_extra_exporter.py` | 导出 SummaC / AlignScore / SCALE 兼容数据集 |
```

- [ ] **Step 8.4: 端到端验证**

Run:

```bash
pytest graphrag_pipeline/tests/test_qa_eval_algorithmic_*.py \
       graphrag_pipeline/tests/test_qa_eval_bootstrap_stats.py \
       graphrag_pipeline/tests/test_qa_eval_ragas_exporter.py \
       graphrag_pipeline/tests/test_qa_eval_factuality_extra_exporter.py -v
```

Expected: 全部 PASS。

如果已有 baseline run：

```bash
python -m graphrag_pipeline.scripts.qa_eval.algorithmic_scorer --run-dir <run_dir>
python -m graphrag_pipeline.scripts.qa_eval.significance_reporter --run-dir <run_dir>
python -m graphrag_pipeline.scripts.qa_eval.ragas_exporter --run-dir <run_dir>
python -m graphrag_pipeline.scripts.qa_eval.factuality_extra_exporter --run-dir <run_dir>
```

Expected:

- `<run_dir>/algorithmic_scoring.csv`
- `<run_dir>/algorithmic_scoring.md`
- `<run_dir>/significance.md`
- `<run_dir>/ragas_dataset.jsonl`
- `<run_dir>/factuality_extra_dataset.jsonl`

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
| 规则评分 | 字面实体、引用格式、长度、密度 | 保留原规则，额外增加 BGE-M3 分块语义覆盖、ROUGE-Lsum、keyword recall 与标准 IR 指标 |
| LLM 裁判 | 自研 prompt 计算 semantic / faithfulness | 降级为抽样校准；日常主线依赖规则 + IR + BGE-M3 + latency/error + bootstrap |
| 检索指标 | `retrieval_precision` | 拆为 `citation_recall_at_k / citation_rr / citation_ndcg_at_k / citation_map`；报告层 mean(`citation_rr`) 即 MRR |
| 模式比较 | 均值和人工结论 | 均值 + bootstrap 95% CI + pairwise win-rate；category 样本 <15 时强制显示探索性警告 |
| 路由综合分 | 无 | `effective_score_experimental` 只作为候选信号，权重需真实路由实验校准后才能固化 |
| 可选复核 | 无固定外部口径 | BERTScore 仅作兼容；RAGAS、SummaC、AlignScore、SCALE 仅作专项 extra |
| 风险 | 自研指标容易语义漂移 | BGE-M3 模型体积和可选 factuality 依赖较重，需要保持 optional extra 边界 |

## 自检清单

- 依赖不影响默认运行：`eval` / `semantic-compat` / `ragas` / `factuality-extra` 都是 optional extra。
- 不改变 GraphRAG 索引：本计划只读 `output/*.parquet` / `output/lancedb/` 与 baseline raw，绝不把 `results/extraction_eval/` 当作四模式问答输入。
- 不替代人工 gold：`qa_candidate_seeds.jsonl` 明确是候选题材。
- 指标命名更准确：答案引用命中称为 `citation_*`，不冒充完整 retrieval pipeline。
- 检索指标必须真正调用 `ir-measures`；citation 抽取必须支持 `Reports/Sources/Entities/Relationships` 到 text unit 前缀的映射。
- 当前基础 schema 使用 12 位 text unit 前缀；算法层必须先审计前缀碰撞，发现碰撞时停止评分并提示调整 schema。
- 日常路由主线使用规则指标、IR 指标、BGE-M3 语义覆盖、latency/error 与 bootstrap；LLM judge、BERTScore、RAGAS 和 factuality extra 都不阻塞本地 MVP。
