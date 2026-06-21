# 课程问答 Hybrid 模式实施计划（分层 + BM25 + Reranker + 两段证据）

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 在 GraphRAG 原生四模式之外，新增一个 `hybrid` 模式，按问题层级（低层 / 高层 / 混合）路由检索：低层走 Basic + BM25、高层走 Global、混合走 Local + DRIFT + 少量 Global；多路召回汇入候选池后由轻量 reranker 精排取 Top-K；最终用「低层证据 + 高层证据」两段式上下文喂给 LLM，输出更符合课程问答场景的回答。新增 hybrid 模式不动 GraphRAG 索引主流程，仅扩展查询层。

**Architecture:** 新增 `graphrag_pipeline/scripts/hybrid_qa/` 模块，里面包含 4 个独立组件：(1) `Classifier` 基于「规则特征 + 嵌入 kNN」做问题分层；(2) `Bm25Index` 用 `rank_bm25 + jieba` 即时从 `text_units.parquet` 构建索引并缓存到磁盘；(3) `Reranker` 用 `BGE-reranker-v2-m3` 跨编码模型对候选池做二阶段精排；(4) `Orchestrator` 串接分层→多路召回（BM25 / Local / DRIFT / Global）→去重→reranker→两段式 Prompt→LLM。`graphrag_pipeline/utils/main.py` 在 `SUPPORTED_QUERY_MODELS` 中新增 `graphrag-hybrid-search:latest`，命中后走 orchestrator，跳过原 CLI 子进程；Java `QaWorkflowService` 已经按 `model` 字符串透传，无需改动。

**Tech Stack:** Python 3.10+、rank_bm25、jieba、FlagEmbedding（BGE-reranker-v2-m3）、torch、transformers、pandas、scikit-learn（kNN）、Pydantic 2、pytest。

**前置依赖：**

- 已完成 `2026-05-12-qa-baseline-test-set-and-eval.md`，`graphrag_pipeline/data/eval/qa_test_set.jsonl` ≥ 30 条且通过 validator。
- `graphrag_pipeline/output/text_units.parquet` 与 `community_reports.parquet` 已存在，结构同 GraphRAG 3.0.9 默认。
- `graphrag_pipeline/output/lancedb/text_unit_text.lance` 与 `entity_description.lance` 已存在（用于 Local / DRIFT 召回时回退到 GraphRAG CLI）。
- 主机能拉取 `BAAI/bge-reranker-v2-m3` 权重（首次约 2.4GB），如需离线请预先下载到 `~/.cache/huggingface/hub/`。

**完成判据：**

1. `pytest graphrag_pipeline/tests/test_hybrid_qa_*.py -v` 全绿（至少 4 个测试文件）。
2. `curl http://127.0.0.1:8000/v1/models` 返回包含 `graphrag-hybrid-search:latest`。
3. `python -m graphrag_pipeline.scripts.qa_eval.run_hybrid_eval --run-id <id>` 跑完测试集，hybrid 模式与 baseline 四模式可比对。
4. `compare_runs.py` 生成的 `comparison.md` 显示 hybrid 在「事实定位」与「全局概览」类目上的 `entity_hit_rate` 不低于对应 baseline 最优模式。
5. Java 后端 `QaWorkflowService` 在 `properties` 中允许 `hybrid` mode 后，端到端聊天能拿到 hybrid 回答（人工冒烟即可）。

---

## File Structure

| 路径 | 责任 |
| --- | --- |
| `graphrag_pipeline/scripts/hybrid_qa/__init__.py` | 包入口 |
| `graphrag_pipeline/scripts/hybrid_qa/types.py` | `Candidate`、`LayerLabel`、`HybridContext` 等数据类 |
| `graphrag_pipeline/scripts/hybrid_qa/classifier.py` | 问题分层器（规则 + embedding kNN） |
| `graphrag_pipeline/scripts/hybrid_qa/exemplars.jsonl` | 分层器的标注示例集合（≥ 24 条，每层 ≥ 8） |
| `graphrag_pipeline/scripts/hybrid_qa/bm25_index.py` | jieba 分词 + rank_bm25 索引（含磁盘缓存） |
| `graphrag_pipeline/scripts/hybrid_qa/reranker.py` | BGE-reranker-v2-m3 包装 |
| `graphrag_pipeline/scripts/hybrid_qa/graphrag_cli.py` | 调 `python -m graphrag query` 的薄薄包装，返回原始文本 |
| `graphrag_pipeline/scripts/hybrid_qa/orchestrator.py` | 串接所有模块，生成两段证据并调 LLM |
| `graphrag_pipeline/scripts/hybrid_qa/prompt_builder.py` | 两段证据 Prompt 模板装配 |
| `graphrag_pipeline/prompts/hybrid_search_system_prompt.txt` | 两段式提示词模板 |
| `graphrag_pipeline/scripts/qa_eval/run_hybrid_eval.py` | 复用 baseline 客户端，但只跑 hybrid 模式 |
| `graphrag_pipeline/scripts/qa_eval/compare_runs.py` | baseline run vs hybrid run 对比表 |
| `graphrag_pipeline/utils/main.py` | 加 `graphrag-hybrid-search:latest` 到 `SUPPORTED_QUERY_MODELS`，分支调 orchestrator |
| `graphrag_pipeline/requirements.txt` | 新增 `rank_bm25`、`jieba`、`FlagEmbedding`、`scikit-learn`（已存在则跳过） |
| `graphrag_pipeline/tests/test_hybrid_qa_classifier.py` | classifier 单测 |
| `graphrag_pipeline/tests/test_hybrid_qa_bm25.py` | BM25 单测 |
| `graphrag_pipeline/tests/test_hybrid_qa_reranker.py` | reranker 单测（mock 模型） |
| `graphrag_pipeline/tests/test_hybrid_qa_orchestrator.py` | orchestrator 端到端 mock 测试 |
| `graphrag_pipeline/tests/test_hybrid_qa_main_route.py` | main.py 新模式路由测试 |

---

### Task 1: 依赖与基础类型

**Files:**
- Modify: `graphrag_pipeline/requirements.txt`
- Create: `graphrag_pipeline/scripts/hybrid_qa/__init__.py`
- Create: `graphrag_pipeline/scripts/hybrid_qa/types.py`
- Create: `graphrag_pipeline/tests/test_hybrid_qa_types.py`

- [ ] **Step 1.1: 新增依赖到 requirements.txt**

在 `graphrag_pipeline/requirements.txt` 现有 `# Machine Learning & NLP` 段落末尾追加：

```text
# Hybrid QA 模块
rank-bm25>=0.2.2
jieba>=0.42.1
FlagEmbedding>=1.2.10
```

- [ ] **Step 1.2: 安装依赖**

Run: `pip install rank-bm25 jieba FlagEmbedding`
Expected: 成功安装（FlagEmbedding 会带 torch 依赖；当前 requirements 已要求 torch，可复用环境）。

- [ ] **Step 1.3: 写 types 失败测试**

写到 `graphrag_pipeline/tests/test_hybrid_qa_types.py`：

```python
from __future__ import annotations

import pytest

from graphrag_pipeline.scripts.hybrid_qa.types import (
    Candidate,
    HybridContext,
    LayerLabel,
)


def test_layer_label_values():
    assert LayerLabel.LOW.value == "low"
    assert LayerLabel.HIGH.value == "high"
    assert LayerLabel.MIXED.value == "mixed"


def test_candidate_dedup_key_normalizes_text():
    a = Candidate(source="bm25", text=" 第 1 章\n绪论 ", score=0.5, ref="tu#1", layer=LayerLabel.LOW)
    b = Candidate(source="local", text="第 1 章 绪论", score=0.3, ref="tu#2", layer=LayerLabel.MIXED)
    assert a.dedup_key == b.dedup_key


def test_hybrid_context_top_low_and_high_segments():
    ctx = HybridContext(
        question="测试",
        layer=LayerLabel.MIXED,
        low_layer=[
            Candidate(source="bm25", text="低层 1", score=1.0, ref="tu#1", layer=LayerLabel.LOW),
        ],
        high_layer=[
            Candidate(source="global", text="高层 1", score=0.9, ref="cr#1", layer=LayerLabel.HIGH),
        ],
        diagnostics={"classifier_confidence": 0.8},
    )
    assert ctx.low_layer[0].text == "低层 1"
    assert ctx.high_layer[0].text == "高层 1"
    assert ctx.diagnostics["classifier_confidence"] == 0.8
```

- [ ] **Step 1.4: 跑测试确认失败**

Run: `pytest graphrag_pipeline/tests/test_hybrid_qa_types.py -v`
Expected: ImportError on `graphrag_pipeline.scripts.hybrid_qa.types`.

- [ ] **Step 1.5: 写 types 实现**

写到 `graphrag_pipeline/scripts/hybrid_qa/__init__.py`：

```python
"""Hybrid QA orchestrator: classifier + BM25 + reranker + two-layer prompt."""
```

写到 `graphrag_pipeline/scripts/hybrid_qa/types.py`：

```python
from __future__ import annotations

import re
from dataclasses import dataclass, field
from enum import Enum
from typing import Any, Dict, List


class LayerLabel(str, Enum):
    LOW = "low"
    HIGH = "high"
    MIXED = "mixed"


_WHITESPACE_RE = re.compile(r"\s+")


@dataclass(slots=True)
class Candidate:
    source: str  # "bm25" / "local" / "drift" / "global" / "basic"
    text: str
    score: float
    ref: str  # text_unit id / community report id / ...
    layer: LayerLabel
    extra: Dict[str, Any] = field(default_factory=dict)

    @property
    def dedup_key(self) -> str:
        normalized = _WHITESPACE_RE.sub(" ", self.text.strip())
        return normalized.casefold()


@dataclass(slots=True)
class HybridContext:
    question: str
    layer: LayerLabel
    low_layer: List[Candidate] = field(default_factory=list)
    high_layer: List[Candidate] = field(default_factory=list)
    diagnostics: Dict[str, Any] = field(default_factory=dict)
```

- [ ] **Step 1.6: 跑测试通过**

Run: `pytest graphrag_pipeline/tests/test_hybrid_qa_types.py -v`
Expected: 3 PASS。

- [ ] **Step 1.7: 提交**

```bash
git add graphrag_pipeline/requirements.txt \
        graphrag_pipeline/scripts/hybrid_qa/__init__.py \
        graphrag_pipeline/scripts/hybrid_qa/types.py \
        graphrag_pipeline/tests/test_hybrid_qa_types.py
git commit -m "feat(hybrid_qa): 新增依赖与基础数据类型"
```

---

### Task 2: BM25 索引

**Files:**
- Create: `graphrag_pipeline/scripts/hybrid_qa/bm25_index.py`
- Create: `graphrag_pipeline/tests/test_hybrid_qa_bm25.py`

实现思路：

1. 加载 `text_units.parquet` 的 `id` 与 `text` 两列。
2. `jieba.cut_for_search` 分词，过滤纯空白 token。
3. `rank_bm25.BM25Okapi` 构建索引。
4. 索引体积小（< 30MB），直接内存常驻；首次构建时 pickle 到 `graphrag_pipeline/output/hybrid_qa/bm25.pkl`，启动时优先加载。
5. 课程 / 索引轮换：构建时把 `text_units.parquet` 的 mtime 写进 sidecar `bm25.meta.json`，加载时若 mtime 不一致则重建。

- [ ] **Step 2.1: 写 BM25 失败测试**

写到 `graphrag_pipeline/tests/test_hybrid_qa_bm25.py`：

```python
from __future__ import annotations

from pathlib import Path

import pandas as pd
import pytest

from graphrag_pipeline.scripts.hybrid_qa.bm25_index import (
    Bm25Index,
    Bm25Match,
    build_bm25_index,
)


def _write_parquet(tmp_path: Path) -> Path:
    df = pd.DataFrame(
        [
            {"id": "tu-001", "text": "第 1 章 绪论 介绍课程的整体目标"},
            {"id": "tu-002", "text": "DBSCAN 是一种基于密度的聚类算法，参数包含 eps 和 MinPts"},
            {"id": "tu-003", "text": "K-Means 算法依赖于簇中心的迭代更新"},
            {"id": "tu-004", "text": "实验 3：使用 sklearn 实现 DBSCAN 并对比 K-Means"},
        ]
    )
    path = tmp_path / "text_units.parquet"
    df.to_parquet(path)
    return path


def test_build_bm25_index_returns_top_k_by_score(tmp_path: Path):
    parquet = _write_parquet(tmp_path)
    cache_dir = tmp_path / "cache"
    index: Bm25Index = build_bm25_index(parquet_path=parquet, cache_dir=cache_dir)
    matches = index.search("DBSCAN 的 eps 参数", top_k=2)
    assert len(matches) == 2
    assert matches[0].ref in {"tu-002", "tu-004"}
    assert matches[0].score >= matches[1].score


def test_bm25_index_loads_from_cache_when_parquet_unchanged(tmp_path: Path):
    parquet = _write_parquet(tmp_path)
    cache_dir = tmp_path / "cache"
    first = build_bm25_index(parquet_path=parquet, cache_dir=cache_dir)
    cache_file = cache_dir / "bm25.pkl"
    assert cache_file.exists()
    cache_file.touch()
    second = build_bm25_index(parquet_path=parquet, cache_dir=cache_dir)
    assert second.size == first.size


def test_bm25_index_rebuilds_when_parquet_mtime_changes(tmp_path: Path):
    parquet = _write_parquet(tmp_path)
    cache_dir = tmp_path / "cache"
    build_bm25_index(parquet_path=parquet, cache_dir=cache_dir)
    df = pd.read_parquet(parquet)
    df = pd.concat(
        [
            df,
            pd.DataFrame([{"id": "tu-005", "text": "新章节：图聚类与社区检测"}]),
        ],
        ignore_index=True,
    )
    df.to_parquet(parquet)
    refreshed = build_bm25_index(parquet_path=parquet, cache_dir=cache_dir)
    assert refreshed.size == 5
    matches = refreshed.search("社区检测", top_k=1)
    assert matches[0].ref == "tu-005"


def test_search_returns_empty_when_no_token_matches(tmp_path: Path):
    parquet = _write_parquet(tmp_path)
    index = build_bm25_index(parquet_path=parquet, cache_dir=tmp_path / "cache")
    matches = index.search("xxxxx-完全无关词-yyyyy", top_k=3)
    assert all(isinstance(m, Bm25Match) for m in matches)
    assert all(m.score >= 0 for m in matches)
```

- [ ] **Step 2.2: 跑测试确认失败**

Run: `pytest graphrag_pipeline/tests/test_hybrid_qa_bm25.py -v`
Expected: ImportError on `graphrag_pipeline.scripts.hybrid_qa.bm25_index`.

- [ ] **Step 2.3: 写 BM25 实现**

写到 `graphrag_pipeline/scripts/hybrid_qa/bm25_index.py`：

```python
from __future__ import annotations

import json
import logging
import os
import pickle
import re
from dataclasses import dataclass
from pathlib import Path
from typing import List, Sequence

import jieba
import pandas as pd
from rank_bm25 import BM25Okapi

LOGGER = logging.getLogger(__name__)

_TOKEN_FILTER = re.compile(r"^[\s　\.,，。;：:()（）\[\]【】\"'`]+$")


def _tokenize(text: str) -> List[str]:
    tokens: List[str] = []
    for tok in jieba.cut_for_search(text or ""):
        tok = tok.strip()
        if not tok:
            continue
        if _TOKEN_FILTER.match(tok):
            continue
        tokens.append(tok.casefold())
    return tokens


@dataclass(slots=True)
class Bm25Match:
    ref: str
    text: str
    score: float


@dataclass(slots=True)
class Bm25Index:
    refs: List[str]
    texts: List[str]
    tokens: List[List[str]]
    bm25: BM25Okapi

    @property
    def size(self) -> int:
        return len(self.refs)

    def search(self, query: str, *, top_k: int = 10) -> List[Bm25Match]:
        query_tokens = _tokenize(query)
        if not query_tokens:
            return []
        scores = self.bm25.get_scores(query_tokens)
        if scores is None or len(scores) == 0:
            return []
        ranked = sorted(
            zip(range(len(scores)), scores), key=lambda pair: pair[1], reverse=True
        )
        matches: List[Bm25Match] = []
        for idx, score in ranked[:top_k]:
            if score <= 0:
                continue
            matches.append(Bm25Match(ref=self.refs[idx], text=self.texts[idx], score=float(score)))
        return matches


def _cache_paths(cache_dir: Path) -> tuple[Path, Path]:
    return cache_dir / "bm25.pkl", cache_dir / "bm25.meta.json"


def _load_from_cache(parquet_path: Path, cache_dir: Path) -> Bm25Index | None:
    pkl, meta = _cache_paths(cache_dir)
    if not pkl.exists() or not meta.exists():
        return None
    try:
        meta_payload = json.loads(meta.read_text(encoding="utf-8"))
    except json.JSONDecodeError:
        return None
    parquet_mtime = os.path.getmtime(parquet_path)
    if meta_payload.get("parquet_mtime") != parquet_mtime:
        return None
    if meta_payload.get("parquet_path") != str(parquet_path.resolve()):
        return None
    with pkl.open("rb") as fh:
        return pickle.load(fh)


def _store_in_cache(index: Bm25Index, parquet_path: Path, cache_dir: Path) -> None:
    cache_dir.mkdir(parents=True, exist_ok=True)
    pkl, meta = _cache_paths(cache_dir)
    with pkl.open("wb") as fh:
        pickle.dump(index, fh, protocol=pickle.HIGHEST_PROTOCOL)
    meta.write_text(
        json.dumps(
            {
                "parquet_path": str(parquet_path.resolve()),
                "parquet_mtime": os.path.getmtime(parquet_path),
                "size": index.size,
            },
            ensure_ascii=False,
            indent=2,
        ),
        encoding="utf-8",
    )


def build_bm25_index(*, parquet_path: Path, cache_dir: Path) -> Bm25Index:
    cached = _load_from_cache(parquet_path, cache_dir)
    if cached is not None:
        LOGGER.info("bm25 cache hit (size=%d)", cached.size)
        return cached

    LOGGER.info("building bm25 index from %s", parquet_path)
    df = pd.read_parquet(parquet_path, columns=["id", "text"])
    refs: List[str] = df["id"].astype(str).tolist()
    texts: List[str] = df["text"].astype(str).tolist()
    tokens: List[List[str]] = [_tokenize(t) for t in texts]
    bm25 = BM25Okapi(tokens)
    index = Bm25Index(refs=refs, texts=texts, tokens=tokens, bm25=bm25)
    _store_in_cache(index, parquet_path, cache_dir)
    return index


__all__ = ["Bm25Index", "Bm25Match", "build_bm25_index"]
```

- [ ] **Step 2.4: 跑测试通过**

Run: `pytest graphrag_pipeline/tests/test_hybrid_qa_bm25.py -v`
Expected: 4 PASS（jieba 首次加载会输出日志，是正常的）。

- [ ] **Step 2.5: 提交**

```bash
git add graphrag_pipeline/scripts/hybrid_qa/bm25_index.py \
        graphrag_pipeline/tests/test_hybrid_qa_bm25.py
git commit -m "feat(hybrid_qa): 新增 jieba + rank_bm25 索引（含磁盘缓存）"
```

---

### Task 3: 问题分层器

**Files:**
- Create: `graphrag_pipeline/scripts/hybrid_qa/exemplars.jsonl`
- Create: `graphrag_pipeline/scripts/hybrid_qa/classifier.py`
- Create: `graphrag_pipeline/tests/test_hybrid_qa_classifier.py`

实现思路（轻量算法路线）：

1. **规则特征**：是否含「第 X 章 / 第 X 节 / 表 / 公式 / 定义」「与 / 区别 / 联系 / 比较」「总结 / 概括 / 整体 / 总体」等关键词。
2. **示例 kNN**：维护 24+ 条标注示例（每层 ≥ 8），用 `sentence-transformers` 风格的 BGE-small 模型（已随 FlagEmbedding 自带可用接口）对问题与示例编码，取 cosine top-3 投票。本计划提供模型加载接口与离线 fallback：若环境无法加载嵌入模型，则降级到 TF-IDF + 余弦。
3. **融合**：先看规则特征是否给出强信号（命中两条以上），否则用 kNN 结果。
4. 输出 `LayerLabel` 与置信度。

> 评分逻辑明确「规则法优先」，kNN 仅在规则不明朗时兜底，符合 Phase 4 的复杂度控制原则。

- [ ] **Step 3.1: 标注示例集**

写到 `graphrag_pipeline/scripts/hybrid_qa/exemplars.jsonl`（每行一条，每层 ≥ 8 条，**写完后请按你的实际课程把示例改写一遍**，下面是脚手架）：

```jsonl
{"layer":"low","question":"DBSCAN 的两个核心超参数是什么？"}
{"layer":"low","question":"第 1 章第 2 节的标题是什么？"}
{"layer":"low","question":"sklearn.cluster 里的 KMeans 默认初始化方法是什么？"}
{"layer":"low","question":"PCA 的英文全称是什么？"}
{"layer":"low","question":"作业 2 要求实现哪一种聚类算法？"}
{"layer":"low","question":"教材第 12 页的公式是什么意思？"}
{"layer":"low","question":"实验 3 中应使用什么数据集？"}
{"layer":"low","question":"轮廓系数的取值范围是什么？"}
{"layer":"mixed","question":"K-Means 与 DBSCAN 在处理噪声点上的差别是什么？"}
{"layer":"mixed","question":"请总结第 3 章「聚类分析」的主要内容。"}
{"layer":"mixed","question":"层次聚类与 K-Means 的优缺点对比是什么？"}
{"layer":"mixed","question":"为什么在高维数据上 K-Means 容易失效？"}
{"layer":"mixed","question":"分类与聚类的核心区别是什么？"}
{"layer":"mixed","question":"第 2 章和第 3 章在方法论上有什么联系？"}
{"layer":"mixed","question":"模型评估章节里讲了哪几种主要指标？"}
{"layer":"mixed","question":"特征选择和特征提取的差异及各自适用场景是什么？"}
{"layer":"high","question":"这门课程贯穿始终的方法论主线是什么？"}
{"layer":"high","question":"整体来看课程主要解决了哪些类型的实际问题？"}
{"layer":"high","question":"全课程涉及的算法可以按什么标准分类？"}
{"layer":"high","question":"如果让你向新生概述本课程，你会怎么讲？"}
{"layer":"high","question":"本课程在数据科学体系中处在什么位置？"}
{"layer":"high","question":"这学期学到的所有方法可以串成什么知识脉络？"}
{"layer":"high","question":"本课程覆盖的算法整体上有什么共同特点？"}
{"layer":"high","question":"贯穿这门课程的核心建模思想是什么？"}
```

> 题面请按实际课程内容替换为真实问题，避免「假大空」示例。建议把 baseline manual_review 里挑得很准的题目搬过来当示例。

- [ ] **Step 3.2: 写 classifier 失败测试**

写到 `graphrag_pipeline/tests/test_hybrid_qa_classifier.py`：

```python
from __future__ import annotations

from pathlib import Path

import pytest

from graphrag_pipeline.scripts.hybrid_qa.classifier import (
    Classifier,
    ClassifierConfig,
    ClassifierResult,
)
from graphrag_pipeline.scripts.hybrid_qa.types import LayerLabel


@pytest.fixture(scope="module")
def classifier(tmp_path_factory: pytest.TempPathFactory) -> Classifier:
    exemplars = tmp_path_factory.mktemp("exemplars") / "exemplars.jsonl"
    exemplars.write_text(
        "\n".join(
            [
                '{"layer":"low","question":"DBSCAN 的两个核心超参数是什么？"}',
                '{"layer":"low","question":"第 1 章第 2 节的标题是什么？"}',
                '{"layer":"low","question":"PCA 的英文全称是什么？"}',
                '{"layer":"low","question":"实验 3 中应使用什么数据集？"}',
                '{"layer":"mixed","question":"K-Means 与 DBSCAN 的差别是什么？"}',
                '{"layer":"mixed","question":"分类与聚类的核心区别是什么？"}',
                '{"layer":"mixed","question":"请总结第 3 章「聚类分析」的主要内容。"}',
                '{"layer":"mixed","question":"模型评估章节里讲了哪几种主要指标？"}',
                '{"layer":"high","question":"这门课程贯穿始终的方法论主线是什么？"}',
                '{"layer":"high","question":"整体来看课程主要解决哪些问题？"}',
                '{"layer":"high","question":"贯穿这门课程的核心建模思想是什么？"}',
                '{"layer":"high","question":"如果让你向新生概述本课程，你会怎么讲？"}',
            ]
        ),
        encoding="utf-8",
    )
    config = ClassifierConfig(
        exemplars_path=exemplars,
        knn_k=3,
        rule_strong_threshold=2,
        embedding_backend="tfidf",
    )
    return Classifier(config)


def test_rule_matches_chapter_question_to_low(classifier: Classifier):
    result: ClassifierResult = classifier.classify("第 2 章第 3 节里讲了哪些算法？")
    assert result.layer is LayerLabel.LOW
    assert result.source in {"rule", "rule+knn"}


def test_rule_matches_overview_question_to_high(classifier: Classifier):
    result = classifier.classify("从整体方法论上看，本课程的主线是什么？")
    assert result.layer is LayerLabel.HIGH


def test_compare_question_routes_to_mixed(classifier: Classifier):
    result = classifier.classify("K-Means 和 DBSCAN 的区别是什么？")
    assert result.layer is LayerLabel.MIXED


def test_unknown_pattern_falls_back_to_knn(classifier: Classifier):
    result = classifier.classify("这一章的算法体系跟之前学的有何延续？")
    assert result.layer in {LayerLabel.MIXED, LayerLabel.HIGH}
    assert 0.0 < result.confidence <= 1.0


def test_classifier_records_diagnostics(classifier: Classifier):
    result = classifier.classify("DBSCAN 的两个核心超参数是什么？")
    assert result.diagnostics.get("rule_hits") is not None
```

- [ ] **Step 3.3: 跑测试确认失败**

Run: `pytest graphrag_pipeline/tests/test_hybrid_qa_classifier.py -v`
Expected: ImportError on `graphrag_pipeline.scripts.hybrid_qa.classifier`.

- [ ] **Step 3.4: 写 classifier 实现**

写到 `graphrag_pipeline/scripts/hybrid_qa/classifier.py`：

```python
from __future__ import annotations

import json
import logging
import re
from collections import Counter
from dataclasses import dataclass, field
from pathlib import Path
from typing import Iterable, List, Literal, Optional, Sequence

import jieba
from sklearn.feature_extraction.text import TfidfVectorizer
from sklearn.metrics.pairwise import cosine_similarity

from graphrag_pipeline.scripts.hybrid_qa.types import LayerLabel

LOGGER = logging.getLogger(__name__)

LOW_PATTERNS = (
    re.compile(r"第\s*[一二三四五六七八九十百\d]+\s*[章节课讲]"),
    re.compile(r"实验\s*\d+"),
    re.compile(r"作业\s*\d+"),
    re.compile(r"表\s*\d+|图\s*\d+|公式\s*\d+"),
    re.compile(r"定义|全称|缩写|英文|参数|默认值|页码|哪一页|哪里提到"),
)

HIGH_PATTERNS = (
    re.compile(r"整体|总体|总览|全局|纵观|贯穿|主线|脉络|核心思想|框架"),
    re.compile(r"概述|概览|介绍这门课|本课程.*?(主要|整体|核心)"),
    re.compile(r"知识体系|方法论"),
)

MIXED_PATTERNS = (
    re.compile(r"(差异|区别|对比|联系|关系|相同|相似|不同)"),
    re.compile(r"(总结|归纳|概括).*?(章|节)"),
    re.compile(r"为什么|为何"),
)


@dataclass(slots=True)
class ClassifierConfig:
    exemplars_path: Path
    knn_k: int = 3
    rule_strong_threshold: int = 2
    embedding_backend: Literal["bge", "tfidf"] = "bge"
    bge_model_name: str = "BAAI/bge-small-zh-v1.5"


@dataclass(slots=True)
class ClassifierResult:
    layer: LayerLabel
    confidence: float
    source: str
    diagnostics: dict = field(default_factory=dict)


@dataclass(slots=True)
class _Exemplar:
    layer: LayerLabel
    question: str


def _jieba_tokens(text: str) -> List[str]:
    return [t.strip() for t in jieba.cut_for_search(text or "") if t.strip()]


class Classifier:
    def __init__(self, config: ClassifierConfig) -> None:
        self._config = config
        self._exemplars: List[_Exemplar] = self._load_exemplars(config.exemplars_path)
        if not self._exemplars:
            raise ValueError(f"empty exemplars file: {config.exemplars_path}")
        self._backend, self._embed_matrix, self._vectorizer = self._build_backend()

    @staticmethod
    def _load_exemplars(path: Path) -> List[_Exemplar]:
        out: List[_Exemplar] = []
        for line in path.read_text(encoding="utf-8").splitlines():
            line = line.strip()
            if not line:
                continue
            payload = json.loads(line)
            out.append(
                _Exemplar(layer=LayerLabel(payload["layer"]), question=payload["question"])
            )
        return out

    def _build_backend(self):
        questions = [exemplar.question for exemplar in self._exemplars]
        if self._config.embedding_backend == "bge":
            try:
                from FlagEmbedding import FlagModel

                model = FlagModel(self._config.bge_model_name, use_fp16=False)
                matrix = model.encode(questions, normalize_embeddings=True)
                return "bge", matrix, model
            except Exception as exc:  # noqa: BLE001
                LOGGER.warning("falling back to tfidf classifier backend: %s", exc)
        vectorizer = TfidfVectorizer(tokenizer=_jieba_tokens, lowercase=True)
        matrix = vectorizer.fit_transform(questions)
        return "tfidf", matrix, vectorizer

    def _rule_signal(self, question: str) -> tuple[Counter, dict]:
        votes: Counter = Counter()
        diagnostics = {"low": [], "high": [], "mixed": []}
        for pattern in LOW_PATTERNS:
            if pattern.search(question):
                votes[LayerLabel.LOW] += 1
                diagnostics["low"].append(pattern.pattern)
        for pattern in HIGH_PATTERNS:
            if pattern.search(question):
                votes[LayerLabel.HIGH] += 1
                diagnostics["high"].append(pattern.pattern)
        for pattern in MIXED_PATTERNS:
            if pattern.search(question):
                votes[LayerLabel.MIXED] += 1
                diagnostics["mixed"].append(pattern.pattern)
        return votes, diagnostics

    def _knn_vote(self, question: str) -> tuple[LayerLabel, float, Sequence[int]]:
        if self._backend == "bge":
            query_vec = self._vectorizer.encode([question], normalize_embeddings=True)
            similarities = (self._embed_matrix @ query_vec.T).ravel()
        else:
            query_vec = self._vectorizer.transform([question])
            similarities = cosine_similarity(query_vec, self._embed_matrix).ravel()

        order = similarities.argsort()[::-1][: self._config.knn_k]
        votes: Counter = Counter()
        for idx in order:
            votes[self._exemplars[idx].layer] += 1
        winner, count = votes.most_common(1)[0]
        confidence = float(count) / float(self._config.knn_k)
        return winner, confidence, list(order)

    def classify(self, question: str) -> ClassifierResult:
        rule_votes, rule_diagnostics = self._rule_signal(question)
        diagnostics = {"rule_hits": rule_diagnostics, "rule_votes": dict(rule_votes)}

        if rule_votes:
            winner, count = rule_votes.most_common(1)[0]
            if count >= self._config.rule_strong_threshold:
                diagnostics["source"] = "rule"
                return ClassifierResult(
                    layer=winner,
                    confidence=min(1.0, count / max(1, sum(rule_votes.values()))),
                    source="rule",
                    diagnostics=diagnostics,
                )

        knn_label, knn_conf, knn_idx = self._knn_vote(question)
        diagnostics["knn_neighbors"] = knn_idx
        diagnostics["knn_confidence"] = knn_conf

        if rule_votes:
            # 规则有一票时，与 kNN 做投票融合
            rule_votes[knn_label] += 1
            winner, count = rule_votes.most_common(1)[0]
            return ClassifierResult(
                layer=winner,
                confidence=min(1.0, max(knn_conf, count / max(1, sum(rule_votes.values())))),
                source="rule+knn",
                diagnostics=diagnostics,
            )

        return ClassifierResult(
            layer=knn_label,
            confidence=knn_conf,
            source="knn",
            diagnostics=diagnostics,
        )


__all__ = ["Classifier", "ClassifierConfig", "ClassifierResult"]
```

- [ ] **Step 3.5: 跑测试通过**

Run: `pytest graphrag_pipeline/tests/test_hybrid_qa_classifier.py -v`
Expected: 5 PASS（TF-IDF 模式不需要网络）。

- [ ] **Step 3.6: 提交**

```bash
git add graphrag_pipeline/scripts/hybrid_qa/exemplars.jsonl \
        graphrag_pipeline/scripts/hybrid_qa/classifier.py \
        graphrag_pipeline/tests/test_hybrid_qa_classifier.py
git commit -m "feat(hybrid_qa): 新增规则+示例 kNN 问题分层器"
```

---

### Task 4: Reranker 包装

**Files:**
- Create: `graphrag_pipeline/scripts/hybrid_qa/reranker.py`
- Create: `graphrag_pipeline/tests/test_hybrid_qa_reranker.py`

- [ ] **Step 4.1: 写 reranker 失败测试**

写到 `graphrag_pipeline/tests/test_hybrid_qa_reranker.py`：

```python
from __future__ import annotations

from unittest.mock import MagicMock

import pytest

from graphrag_pipeline.scripts.hybrid_qa.reranker import (
    Reranker,
    RerankerScorerProtocol,
)
from graphrag_pipeline.scripts.hybrid_qa.types import Candidate, LayerLabel


class _FakeScorer:
    def __init__(self, scores):
        self._scores = scores
        self.calls = 0

    def compute_score(self, pairs, normalize: bool = True):
        self.calls += 1
        return list(self._scores)


def _make_candidates() -> list[Candidate]:
    return [
        Candidate(source="bm25", text="DBSCAN 是密度聚类", score=0.4, ref="tu#1", layer=LayerLabel.LOW),
        Candidate(source="local", text="K-Means 是迭代质心聚类", score=0.6, ref="tu#2", layer=LayerLabel.LOW),
        Candidate(source="global", text="本课程主线是数据→特征→模型→评估", score=0.5, ref="cr#1", layer=LayerLabel.HIGH),
    ]


def test_reranker_returns_top_n_sorted_descending():
    scorer = _FakeScorer(scores=[0.1, 0.9, 0.5])
    reranker = Reranker(scorer=scorer)
    out = reranker.rerank("DBSCAN 算法是什么？", _make_candidates(), top_n=2)
    assert [c.score for c in out] == [0.9, 0.5]
    assert out[0].ref == "tu#2"
    assert scorer.calls == 1


def test_reranker_skips_when_candidates_below_top_n():
    scorer = _FakeScorer(scores=[0.5, 0.3])
    reranker = Reranker(scorer=scorer)
    candidates = _make_candidates()[:2]
    out = reranker.rerank("x", candidates, top_n=5)
    assert len(out) == 2
    assert out[0].score == 0.5


def test_reranker_preserves_layer_metadata():
    scorer = _FakeScorer(scores=[0.9, 0.8, 0.7])
    reranker = Reranker(scorer=scorer)
    out = reranker.rerank("?", _make_candidates(), top_n=3)
    assert {c.layer for c in out} == {LayerLabel.LOW, LayerLabel.HIGH}
```

- [ ] **Step 4.2: 跑测试确认失败**

Run: `pytest graphrag_pipeline/tests/test_hybrid_qa_reranker.py -v`
Expected: ImportError on `graphrag_pipeline.scripts.hybrid_qa.reranker`.

- [ ] **Step 4.3: 写 reranker 实现**

写到 `graphrag_pipeline/scripts/hybrid_qa/reranker.py`：

```python
from __future__ import annotations

import logging
from dataclasses import dataclass, replace
from typing import List, Protocol, Sequence

from graphrag_pipeline.scripts.hybrid_qa.types import Candidate

LOGGER = logging.getLogger(__name__)


class RerankerScorerProtocol(Protocol):
    def compute_score(self, pairs: List[List[str]], normalize: bool = True) -> List[float]: ...


@dataclass(slots=True)
class Reranker:
    scorer: RerankerScorerProtocol

    def rerank(
        self,
        question: str,
        candidates: Sequence[Candidate],
        *,
        top_n: int = 8,
    ) -> List[Candidate]:
        if not candidates:
            return []
        pairs = [[question, candidate.text] for candidate in candidates]
        scores = self.scorer.compute_score(pairs, normalize=True)
        scored: List[Candidate] = []
        for candidate, score in zip(candidates, scores):
            scored.append(replace(candidate, score=float(score)))
        scored.sort(key=lambda c: c.score, reverse=True)
        return scored[:top_n]


def build_bge_reranker(model_name: str = "BAAI/bge-reranker-v2-m3") -> Reranker:
    from FlagEmbedding import FlagReranker

    LOGGER.info("loading reranker model %s", model_name)
    scorer = FlagReranker(model_name, use_fp16=False)
    return Reranker(scorer=scorer)


__all__ = ["Reranker", "RerankerScorerProtocol", "build_bge_reranker"]
```

- [ ] **Step 4.4: 跑测试通过**

Run: `pytest graphrag_pipeline/tests/test_hybrid_qa_reranker.py -v`
Expected: 3 PASS（mock scorer，不实际加载 BGE）。

- [ ] **Step 4.5: 提交**

```bash
git add graphrag_pipeline/scripts/hybrid_qa/reranker.py \
        graphrag_pipeline/tests/test_hybrid_qa_reranker.py
git commit -m "feat(hybrid_qa): 新增 BGE-reranker 跨编码包装"
```

---

### Task 5: GraphRAG CLI 包装 + 两段证据 Prompt 装配

**Files:**
- Create: `graphrag_pipeline/scripts/hybrid_qa/graphrag_cli.py`
- Create: `graphrag_pipeline/scripts/hybrid_qa/prompt_builder.py`
- Create: `graphrag_pipeline/prompts/hybrid_search_system_prompt.txt`
- Create: `graphrag_pipeline/tests/test_hybrid_qa_prompt_builder.py`

实现思路：

- `graphrag_cli.py`：复用 `utils/main.py` 里的 `run_graphrag_query_cli`，但要返回带 ref 的 `Candidate` 列表而非整段答案。先期我们让 hybrid 流程把 GraphRAG 的回答整段当成「一个 Candidate」放进对应 layer——这对路由已经够用，但 reranker 拿到的是大段文字。后续 step 5.6 解释为什么这种近似可以接受。
- `prompt_builder.py`：把候选拼成两段，明确「先低层证据、再高层证据」「冲突时原文优先」「不得编造原文之外的内容」。
- 系统提示词（hybrid_search_system_prompt.txt）固化语气与引用要求。

- [ ] **Step 5.1: 写 hybrid 系统 prompt**

写到 `graphrag_pipeline/prompts/hybrid_search_system_prompt.txt`：

```text
---Role---

You are a helpful course teaching assistant. You answer student questions using the evidence provided below, which has two layers:

- LOW-LAYER EVIDENCE：来自课程原文（章节段落、章节编号、术语原文），用于回答具体事实与定位。
- HIGH-LAYER EVIDENCE：来自全局综述（章/节小结、社区报告、主题归纳），用于补充背景与跨章节关系。

---Goal---

按下列规则生成回答：

1. 当问题问的是事实、术语、章节定位、公式、参数等具体内容时，**优先且主要使用 LOW-LAYER EVIDENCE 回答**。
2. 当问题涉及总结、对比、整体框架时，可以引用 HIGH-LAYER EVIDENCE 提供背景，但**必须用 LOW-LAYER EVIDENCE 中的具体例子去支撑**。
3. 如果 LOW-LAYER 与 HIGH-LAYER 之间存在矛盾，**以 LOW-LAYER（原文）为准**，并在回答末尾用一句话点明分歧。
4. 不要引用 EVIDENCE 之外的知识。若 EVIDENCE 不足以回答，直接说「依据现有资料无法回答」。
5. 回答末尾必须列引用，格式：`[Data: <source>(refs)]`，其中 source 取 BM25/Local/DRIFT/Global/Basic，refs 是 EVIDENCE 中列出的 ref 字段，按相关度排前 3。

---Output format---

- 简体中文回答，先给结论，再给依据。
- 控制在 350 字以内，避免堆砌。

---LOW-LAYER EVIDENCE---

{low_layer_text}

---HIGH-LAYER EVIDENCE---

{high_layer_text}

---Question---

{question}
```

- [ ] **Step 5.2: 写 prompt_builder 失败测试**

写到 `graphrag_pipeline/tests/test_hybrid_qa_prompt_builder.py`：

```python
from __future__ import annotations

from pathlib import Path

import pytest

from graphrag_pipeline.scripts.hybrid_qa.prompt_builder import (
    PromptBuilder,
    PromptBundle,
)
from graphrag_pipeline.scripts.hybrid_qa.types import (
    Candidate,
    HybridContext,
    LayerLabel,
)


@pytest.fixture()
def template(tmp_path: Path) -> Path:
    p = tmp_path / "tpl.txt"
    p.write_text(
        "LOW={low_layer_text}\nHIGH={high_layer_text}\nQ={question}\n",
        encoding="utf-8",
    )
    return p


def _make_ctx() -> HybridContext:
    return HybridContext(
        question="DBSCAN 的 eps 是什么？",
        layer=LayerLabel.LOW,
        low_layer=[
            Candidate(source="bm25", text="DBSCAN 中的 eps 表示邻域半径。", score=0.9, ref="tu-001", layer=LayerLabel.LOW),
            Candidate(source="basic", text="MinPts 表示密度阈值。", score=0.8, ref="tu-002", layer=LayerLabel.LOW),
        ],
        high_layer=[
            Candidate(source="global", text="本章介绍了密度聚类的发展脉络。", score=0.7, ref="cr-1", layer=LayerLabel.HIGH),
        ],
    )


def test_prompt_builder_renders_two_layers(template: Path):
    builder = PromptBuilder(template_path=template)
    bundle: PromptBundle = builder.build(_make_ctx())
    assert "DBSCAN 中的 eps 表示邻域半径" in bundle.user_prompt
    assert "[bm25:tu-001]" in bundle.user_prompt
    assert "本章介绍了密度聚类" in bundle.user_prompt
    assert bundle.user_prompt.endswith("Q=DBSCAN 的 eps 是什么？\n")


def test_prompt_builder_handles_empty_high_layer(template: Path):
    ctx = _make_ctx()
    ctx.high_layer = []
    bundle = PromptBuilder(template_path=template).build(ctx)
    assert "HIGH=（无高层证据）" in bundle.user_prompt


def test_prompt_builder_truncates_too_long_text(template: Path):
    ctx = _make_ctx()
    ctx.low_layer[0] = Candidate(
        source="bm25",
        text="x" * 5000,
        score=0.9,
        ref="tu-001",
        layer=LayerLabel.LOW,
    )
    bundle = PromptBuilder(template_path=template, max_text_chars=200).build(ctx)
    assert "..." in bundle.user_prompt
    assert "x" * 5000 not in bundle.user_prompt
```

- [ ] **Step 5.3: 跑测试确认失败**

Run: `pytest graphrag_pipeline/tests/test_hybrid_qa_prompt_builder.py -v`
Expected: ImportError on `graphrag_pipeline.scripts.hybrid_qa.prompt_builder`.

- [ ] **Step 5.4: 写 prompt_builder 实现**

写到 `graphrag_pipeline/scripts/hybrid_qa/prompt_builder.py`：

```python
from __future__ import annotations

from dataclasses import dataclass
from pathlib import Path
from typing import List

from graphrag_pipeline.scripts.hybrid_qa.types import Candidate, HybridContext

DEFAULT_TEMPLATE = (
    Path(__file__).resolve().parent.parent.parent
    / "prompts"
    / "hybrid_search_system_prompt.txt"
)


@dataclass(slots=True)
class PromptBundle:
    user_prompt: str
    low_layer_count: int
    high_layer_count: int


def _truncate(text: str, limit: int) -> str:
    text = text.strip()
    if len(text) <= limit:
        return text
    return text[: max(0, limit - 3)] + "..."


def _render_segment(candidates: List[Candidate], *, max_text_chars: int) -> str:
    if not candidates:
        return "（无高层证据）"
    lines: List[str] = []
    for idx, candidate in enumerate(candidates, start=1):
        lines.append(
            f"[{candidate.source}:{candidate.ref}] (score={candidate.score:.3f})\n"
            f"{_truncate(candidate.text, max_text_chars)}"
        )
    return "\n\n".join(lines)


@dataclass(slots=True)
class PromptBuilder:
    template_path: Path = DEFAULT_TEMPLATE
    max_text_chars: int = 600

    def build(self, context: HybridContext) -> PromptBundle:
        template = self.template_path.read_text(encoding="utf-8")
        low_text = _render_segment(context.low_layer, max_text_chars=self.max_text_chars)
        high_text = _render_segment(context.high_layer, max_text_chars=self.max_text_chars)
        rendered = (
            template
            .replace("{low_layer_text}", low_text)
            .replace("{high_layer_text}", high_text)
            .replace("{question}", context.question)
        )
        return PromptBundle(
            user_prompt=rendered,
            low_layer_count=len(context.low_layer),
            high_layer_count=len(context.high_layer),
        )


__all__ = ["PromptBuilder", "PromptBundle", "DEFAULT_TEMPLATE"]
```

- [ ] **Step 5.5: 跑测试通过**

Run: `pytest graphrag_pipeline/tests/test_hybrid_qa_prompt_builder.py -v`
Expected: 3 PASS。

- [ ] **Step 5.6: 写 graphrag_cli 实现**

写到 `graphrag_pipeline/scripts/hybrid_qa/graphrag_cli.py`：

```python
from __future__ import annotations

import asyncio
import logging
import os
import subprocess
import sys
from dataclasses import dataclass
from pathlib import Path
from typing import List, Optional

from graphrag_pipeline.scripts.hybrid_qa.types import Candidate, LayerLabel

LOGGER = logging.getLogger(__name__)

LAYER_BY_MODE = {
    "local": LayerLabel.MIXED,
    "drift": LayerLabel.MIXED,
    "global": LayerLabel.HIGH,
    "basic": LayerLabel.LOW,
}


@dataclass(slots=True)
class GraphRagCliResult:
    answer: str
    elapsed_seconds: float
    error: Optional[str] = None


def call_graphrag_cli(
    *,
    mode: str,
    prompt: str,
    cwd: Path,
    env: Optional[dict] = None,
    timeout_seconds: float = 180.0,
) -> GraphRagCliResult:
    cmd = [
        sys.executable,
        "-m",
        "graphrag",
        "query",
        "--root",
        ".",
        "--method",
        mode,
        prompt,
    ]
    started = asyncio.get_event_loop().time() if asyncio.get_event_loop().is_running() else 0.0
    try:
        completed = subprocess.run(
            cmd,
            cwd=str(cwd),
            env={**os.environ, **(env or {})},
            capture_output=True,
            text=True,
            timeout=timeout_seconds,
        )
    except subprocess.TimeoutExpired as exc:
        return GraphRagCliResult(answer="", elapsed_seconds=timeout_seconds, error=f"timeout: {exc}")
    if completed.returncode != 0:
        return GraphRagCliResult(
            answer="",
            elapsed_seconds=0.0,
            error=completed.stderr.strip() or completed.stdout.strip(),
        )
    return GraphRagCliResult(answer=completed.stdout.strip(), elapsed_seconds=0.0)


def as_candidate(*, mode: str, answer: str) -> Candidate:
    return Candidate(
        source=mode,
        text=answer,
        score=0.0,  # 待 reranker 重新打分
        ref=f"graphrag-{mode}",
        layer=LAYER_BY_MODE.get(mode, LayerLabel.MIXED),
    )


__all__ = ["GraphRagCliResult", "call_graphrag_cli", "as_candidate", "LAYER_BY_MODE"]
```

> Note：把 GraphRAG CLI 当成「整段一条候选」放入 reranker 是有意为之的近似——它使 hybrid 能在不重写 GraphRAG 内部检索器的前提下吸纳 local/drift/global 的能力。后续 V2 才会拆细到 entity / community 粒度。

- [ ] **Step 5.7: 提交**

```bash
git add graphrag_pipeline/scripts/hybrid_qa/graphrag_cli.py \
        graphrag_pipeline/scripts/hybrid_qa/prompt_builder.py \
        graphrag_pipeline/prompts/hybrid_search_system_prompt.txt \
        graphrag_pipeline/tests/test_hybrid_qa_prompt_builder.py
git commit -m "feat(hybrid_qa): 新增两段证据 Prompt 模板与 GraphRAG CLI 包装"
```

---

### Task 6: Orchestrator

**Files:**
- Create: `graphrag_pipeline/scripts/hybrid_qa/orchestrator.py`
- Create: `graphrag_pipeline/tests/test_hybrid_qa_orchestrator.py`

流程：

1. classifier.classify(question) → `LayerLabel`。
2. 召回路由：
   - LOW → BM25 top-15 + GraphRAG basic 一条整段候选。
   - HIGH → GraphRAG global 一条整段候选 + BM25 top-5（兜底）。
   - MIXED → BM25 top-10 + GraphRAG local + GraphRAG drift + GraphRAG global（前三种各一条）。
3. 去重（`Candidate.dedup_key`），候选池上限 20。
4. reranker 重排，取 top_n=8。
5. 按 layer 把 top_n 拆成 low_layer 与 high_layer。
6. prompt_builder.build → 调 LLM（复用 `scripts/extraction_eval/llm_client.LLMClient`）。
7. 返回答案 + diagnostics（layer / classifier_confidence / 候选池 ref 列表 / 重排前后分数）。

- [ ] **Step 6.1: 写 orchestrator 失败测试**

写到 `graphrag_pipeline/tests/test_hybrid_qa_orchestrator.py`：

```python
from __future__ import annotations

from pathlib import Path
from unittest.mock import MagicMock

import pytest

from graphrag_pipeline.scripts.hybrid_qa.classifier import (
    Classifier,
    ClassifierConfig,
    ClassifierResult,
)
from graphrag_pipeline.scripts.hybrid_qa.orchestrator import (
    HybridOrchestrator,
    OrchestratorConfig,
)
from graphrag_pipeline.scripts.hybrid_qa.types import (
    Candidate,
    HybridContext,
    LayerLabel,
)


def _fake_bm25(matches):
    fake = MagicMock()
    fake.search.return_value = matches
    return fake


def _fake_classifier(layer: LayerLabel) -> MagicMock:
    fake = MagicMock(spec=Classifier)
    fake.classify.return_value = ClassifierResult(
        layer=layer, confidence=0.9, source="rule", diagnostics={}
    )
    return fake


def _fake_reranker(candidates):
    fake = MagicMock()
    fake.rerank.return_value = candidates
    return fake


def _fake_cli(answers: dict):
    def call(mode: str, prompt: str):
        return MagicMock(answer=answers[mode], elapsed_seconds=0.1, error=None)

    return call


def _fake_llm(answer: str):
    fake = MagicMock()
    fake.complete.return_value = answer
    return fake


def _bm25_match(ref: str, text: str, score: float):
    fake = MagicMock()
    fake.ref = ref
    fake.text = text
    fake.score = score
    return fake


def test_orchestrator_low_layer_routes_to_bm25_and_basic():
    classifier = _fake_classifier(LayerLabel.LOW)
    bm25 = _fake_bm25([_bm25_match("tu#1", "DBSCAN 中的 eps", 1.0)])
    reranker_passthrough = _fake_reranker([
        Candidate(source="bm25", text="DBSCAN 中的 eps", score=1.0, ref="tu#1", layer=LayerLabel.LOW),
    ])
    cli_calls = []

    def cli_func(mode: str, prompt: str):
        cli_calls.append(mode)
        return MagicMock(answer=f"{mode}-answer", elapsed_seconds=0.1, error=None)

    llm = _fake_llm("最终答案")
    config = OrchestratorConfig(rerank_top_n=4, max_pool=20)
    orchestrator = HybridOrchestrator(
        classifier=classifier,
        bm25=bm25,
        reranker=reranker_passthrough,
        graphrag_cli=cli_func,
        llm=llm,
        config=config,
        prompt_template_path=Path("graphrag_pipeline/prompts/hybrid_search_system_prompt.txt"),
    )
    result = orchestrator.answer("DBSCAN 的 eps 是什么？")
    assert result.answer == "最终答案"
    assert cli_calls == ["basic"]
    assert result.diagnostics["layer"] == "low"


def test_orchestrator_high_layer_routes_to_global():
    classifier = _fake_classifier(LayerLabel.HIGH)
    bm25 = _fake_bm25([_bm25_match("tu#3", "课程主线", 0.4)])
    reranker = _fake_reranker([
        Candidate(source="global", text="这门课围绕数据→特征→模型→评估", score=0.9, ref="graphrag-global", layer=LayerLabel.HIGH),
    ])
    cli_calls = []

    def cli_func(mode: str, prompt: str):
        cli_calls.append(mode)
        return MagicMock(answer=f"{mode}-answer", elapsed_seconds=0.1, error=None)

    orchestrator = HybridOrchestrator(
        classifier=classifier,
        bm25=bm25,
        reranker=reranker,
        graphrag_cli=cli_func,
        llm=_fake_llm("ok"),
        config=OrchestratorConfig(rerank_top_n=4, max_pool=20),
        prompt_template_path=Path("graphrag_pipeline/prompts/hybrid_search_system_prompt.txt"),
    )
    result = orchestrator.answer("整体看课程的方法论是什么？")
    assert "global" in cli_calls
    assert result.diagnostics["layer"] == "high"


def test_orchestrator_mixed_layer_collects_local_drift_global():
    classifier = _fake_classifier(LayerLabel.MIXED)
    bm25 = _fake_bm25([_bm25_match("tu#1", "K-Means", 0.8)])
    reranker = _fake_reranker([
        Candidate(source="local", text="K-Means 与 DBSCAN", score=0.9, ref="graphrag-local", layer=LayerLabel.MIXED),
    ])
    cli_calls = []

    def cli_func(mode: str, prompt: str):
        cli_calls.append(mode)
        return MagicMock(answer=f"{mode}-answer", elapsed_seconds=0.1, error=None)

    orchestrator = HybridOrchestrator(
        classifier=classifier,
        bm25=bm25,
        reranker=reranker,
        graphrag_cli=cli_func,
        llm=_fake_llm("ok"),
        config=OrchestratorConfig(rerank_top_n=4, max_pool=20),
        prompt_template_path=Path("graphrag_pipeline/prompts/hybrid_search_system_prompt.txt"),
    )
    orchestrator.answer("K-Means 与 DBSCAN 的差别是什么？")
    assert set(cli_calls) == {"local", "drift", "global"}


def test_orchestrator_dedups_candidates_before_reranking():
    classifier = _fake_classifier(LayerLabel.MIXED)
    bm25 = _fake_bm25(
        [
            _bm25_match("tu#1", "DBSCAN 是密度聚类", 0.9),
            _bm25_match("tu#2", "DBSCAN 是密度聚类", 0.7),  # 同文本，应该去重
        ]
    )
    captured: list[list[Candidate]] = []

    def rerank(question, candidates, top_n):
        captured.append(list(candidates))
        return candidates[:top_n]

    reranker = MagicMock()
    reranker.rerank.side_effect = rerank

    def cli_func(mode: str, prompt: str):
        return MagicMock(answer=f"{mode}-answer", elapsed_seconds=0.1, error=None)

    orchestrator = HybridOrchestrator(
        classifier=classifier,
        bm25=bm25,
        reranker=reranker,
        graphrag_cli=cli_func,
        llm=_fake_llm("ok"),
        config=OrchestratorConfig(rerank_top_n=4, max_pool=20),
        prompt_template_path=Path("graphrag_pipeline/prompts/hybrid_search_system_prompt.txt"),
    )
    orchestrator.answer("DBSCAN 与 KMeans 的区别")
    seen_keys = {c.dedup_key for c in captured[0]}
    assert len(seen_keys) == len(captured[0])  # 已去重
```

- [ ] **Step 6.2: 跑测试确认失败**

Run: `pytest graphrag_pipeline/tests/test_hybrid_qa_orchestrator.py -v`
Expected: ImportError on `graphrag_pipeline.scripts.hybrid_qa.orchestrator`.

- [ ] **Step 6.3: 写 orchestrator 实现**

写到 `graphrag_pipeline/scripts/hybrid_qa/orchestrator.py`：

```python
from __future__ import annotations

import logging
import time
from dataclasses import dataclass, field
from pathlib import Path
from typing import Any, Callable, Dict, List, Protocol, Sequence

from graphrag_pipeline.scripts.hybrid_qa.bm25_index import Bm25Index
from graphrag_pipeline.scripts.hybrid_qa.classifier import Classifier
from graphrag_pipeline.scripts.hybrid_qa.graphrag_cli import (
    LAYER_BY_MODE,
    GraphRagCliResult,
    as_candidate,
)
from graphrag_pipeline.scripts.hybrid_qa.prompt_builder import (
    PromptBuilder,
    PromptBundle,
)
from graphrag_pipeline.scripts.hybrid_qa.reranker import Reranker
from graphrag_pipeline.scripts.hybrid_qa.types import (
    Candidate,
    HybridContext,
    LayerLabel,
)

LOGGER = logging.getLogger(__name__)


class LLMProtocol(Protocol):
    def complete(self, prompt: str, *, temperature: float = 0.0) -> str: ...


GraphRagCliFunc = Callable[[str, str], GraphRagCliResult]


@dataclass(slots=True)
class OrchestratorConfig:
    bm25_top_k_low: int = 15
    bm25_top_k_high: int = 5
    bm25_top_k_mixed: int = 10
    rerank_top_n: int = 8
    max_pool: int = 20
    answer_temperature: float = 0.0


@dataclass(slots=True)
class HybridAnswer:
    answer: str
    diagnostics: Dict[str, Any] = field(default_factory=dict)


@dataclass(slots=True)
class HybridOrchestrator:
    classifier: Classifier
    bm25: Bm25Index
    reranker: Reranker
    graphrag_cli: GraphRagCliFunc
    llm: LLMProtocol
    config: OrchestratorConfig = field(default_factory=OrchestratorConfig)
    prompt_template_path: Path = field(default_factory=lambda: Path("graphrag_pipeline/prompts/hybrid_search_system_prompt.txt"))

    def answer(self, question: str) -> HybridAnswer:
        started = time.monotonic()
        classification = self.classifier.classify(question)
        layer = classification.layer

        candidates: List[Candidate] = []
        candidates.extend(self._recall_bm25(question, layer))
        candidates.extend(self._recall_graphrag(question, layer))

        deduped = self._dedup(candidates)[: self.config.max_pool]
        reranked = self.reranker.rerank(question, deduped, top_n=self.config.rerank_top_n)

        low_layer: List[Candidate] = []
        high_layer: List[Candidate] = []
        for candidate in reranked:
            (low_layer if candidate.layer in {LayerLabel.LOW, LayerLabel.MIXED} else high_layer).append(candidate)

        context = HybridContext(
            question=question,
            layer=layer,
            low_layer=low_layer,
            high_layer=high_layer,
            diagnostics={
                "classifier_confidence": classification.confidence,
                "classifier_source": classification.source,
                "pool_size": len(deduped),
                "rerank_top_n": len(reranked),
            },
        )

        bundle: PromptBundle = PromptBuilder(template_path=self.prompt_template_path).build(context)
        answer = self.llm.complete(bundle.user_prompt, temperature=self.config.answer_temperature)

        diagnostics = {
            **context.diagnostics,
            "layer": layer.value,
            "low_layer_refs": [c.ref for c in low_layer],
            "high_layer_refs": [c.ref for c in high_layer],
            "elapsed_seconds": time.monotonic() - started,
        }
        return HybridAnswer(answer=answer, diagnostics=diagnostics)

    def _recall_bm25(self, question: str, layer: LayerLabel) -> List[Candidate]:
        if layer is LayerLabel.LOW:
            top_k = self.config.bm25_top_k_low
        elif layer is LayerLabel.HIGH:
            top_k = self.config.bm25_top_k_high
        else:
            top_k = self.config.bm25_top_k_mixed
        matches = self.bm25.search(question, top_k=top_k)
        return [
            Candidate(
                source="bm25",
                text=match.text,
                score=match.score,
                ref=match.ref,
                layer=LayerLabel.LOW,
            )
            for match in matches
        ]

    def _recall_graphrag(self, question: str, layer: LayerLabel) -> List[Candidate]:
        if layer is LayerLabel.LOW:
            modes = ("basic",)
        elif layer is LayerLabel.HIGH:
            modes = ("global",)
        else:
            modes = ("local", "drift", "global")

        out: List[Candidate] = []
        for mode in modes:
            result = self.graphrag_cli(mode, question)
            if result.error or not result.answer:
                LOGGER.warning("graphrag cli mode=%s failed: %s", mode, result.error)
                continue
            out.append(as_candidate(mode=mode, answer=result.answer))
        return out

    @staticmethod
    def _dedup(candidates: Sequence[Candidate]) -> List[Candidate]:
        seen: set[str] = set()
        out: List[Candidate] = []
        for candidate in candidates:
            key = candidate.dedup_key
            if key in seen:
                continue
            seen.add(key)
            out.append(candidate)
        return out


__all__ = ["HybridOrchestrator", "OrchestratorConfig", "HybridAnswer"]
```

- [ ] **Step 6.4: 跑测试通过**

Run: `pytest graphrag_pipeline/tests/test_hybrid_qa_orchestrator.py -v`
Expected: 4 PASS。

- [ ] **Step 6.5: 提交**

```bash
git add graphrag_pipeline/scripts/hybrid_qa/orchestrator.py \
        graphrag_pipeline/tests/test_hybrid_qa_orchestrator.py
git commit -m "feat(hybrid_qa): 新增 hybrid 检索编排器"
```

---

### Task 7: 把 hybrid 模式接入 GraphRAG API 服务器

**Files:**
- Modify: `graphrag_pipeline/utils/main.py`
- Create: `graphrag_pipeline/tests/test_hybrid_qa_main_route.py`

修改原则：

- 沿用 `SUPPORTED_QUERY_MODELS`，新增 `"graphrag-hybrid-search:latest": "hybrid"`。
- `_resolve_query_response` 分支：`hybrid` 走 orchestrator，其他模式继续走 `run_graphrag_query_cli`。
- orchestrator 用「模块级 lazy singleton」，避免每个请求都重建 BM25 与加载 reranker 模型。

- [ ] **Step 7.1: 写路由失败测试**

写到 `graphrag_pipeline/tests/test_hybrid_qa_main_route.py`：

```python
from __future__ import annotations

import asyncio
from unittest.mock import AsyncMock, patch

from graphrag_pipeline.utils.main import (
    SUPPORTED_QUERY_MODELS,
    _resolve_query_response,
)


def test_hybrid_model_id_registered():
    assert SUPPORTED_QUERY_MODELS.get("graphrag-hybrid-search:latest") == "hybrid"


def test_hybrid_path_invokes_orchestrator():
    with patch("graphrag_pipeline.utils.main._run_hybrid_query", new=AsyncMock(return_value="hybrid-answer")) as fake_hybrid, \
         patch("graphrag_pipeline.utils.main.run_graphrag_query_cli", new=AsyncMock(return_value="cli-answer")) as fake_cli:
        answer = asyncio.run(_resolve_query_response("graphrag-hybrid-search:latest", "what is dbscan?"))
        assert "hybrid-answer" in answer
        fake_hybrid.assert_awaited_once_with("what is dbscan?")
        fake_cli.assert_not_awaited()


def test_non_hybrid_path_skips_orchestrator():
    with patch("graphrag_pipeline.utils.main._run_hybrid_query", new=AsyncMock(return_value="hybrid-answer")) as fake_hybrid, \
         patch("graphrag_pipeline.utils.main.run_graphrag_query_cli", new=AsyncMock(return_value="cli-answer")) as fake_cli:
        answer = asyncio.run(_resolve_query_response("graphrag-local-search:latest", "what is dbscan?"))
        assert "cli-answer" in answer
        fake_cli.assert_awaited_once_with("local", "what is dbscan?")
        fake_hybrid.assert_not_awaited()
```

> `_resolve_query_response` 内部会经过 `format_response()` 加工，因此断言只用 `in` 而不是完全相等。

- [ ] **Step 7.2: 写路由实现**

修改 `graphrag_pipeline/utils/main.py`。先在 `SUPPORTED_QUERY_MODELS` 字典里加入新模式：

找到（约第 49 行）：

```python
SUPPORTED_QUERY_MODELS: dict[str, str] = {
    "graphrag-local-search:latest": "local",
    "graphrag-global-search:latest": "global",
    "graphrag-drift-search:latest": "drift",
    "graphrag-basic-search:latest": "basic",
}
```

替换为：

```python
SUPPORTED_QUERY_MODELS: dict[str, str] = {
    "graphrag-local-search:latest": "local",
    "graphrag-global-search:latest": "global",
    "graphrag-drift-search:latest": "drift",
    "graphrag-basic-search:latest": "basic",
    "graphrag-hybrid-search:latest": "hybrid",
}
```

在文件顶部 import 段（约 30 行）末尾，加入：

```python
from graphrag_pipeline.scripts.hybrid_qa.bm25_index import build_bm25_index
from graphrag_pipeline.scripts.hybrid_qa.classifier import (
    Classifier,
    ClassifierConfig,
)
from graphrag_pipeline.scripts.hybrid_qa.graphrag_cli import call_graphrag_cli
from graphrag_pipeline.scripts.hybrid_qa.orchestrator import (
    HybridOrchestrator,
    OrchestratorConfig,
)
from graphrag_pipeline.scripts.hybrid_qa.reranker import build_bge_reranker
```

在 `_resolve_query_response` 之前插入：

```python
_HYBRID_ORCHESTRATOR: HybridOrchestrator | None = None


def _get_hybrid_orchestrator() -> HybridOrchestrator:
    global _HYBRID_ORCHESTRATOR
    if _HYBRID_ORCHESTRATOR is not None:
        return _HYBRID_ORCHESTRATOR

    text_units_path = OUTPUT_DIR / "text_units.parquet"
    cache_dir = OUTPUT_DIR / "hybrid_qa"
    bm25 = build_bm25_index(parquet_path=text_units_path, cache_dir=cache_dir)
    classifier = Classifier(
        ClassifierConfig(
            exemplars_path=GRAPHRAG_ROOT / "scripts" / "hybrid_qa" / "exemplars.jsonl",
            knn_k=3,
            rule_strong_threshold=2,
            embedding_backend="bge",
        )
    )
    reranker = build_bge_reranker()

    from graphrag_pipeline.scripts.extraction_eval.llm_client import (
        OpenAICompatibleLlmClient,
        build_llm_client,
    )

    raw_llm: OpenAICompatibleLlmClient = build_llm_client(
        root=GRAPHRAG_ROOT,
        model=os.environ.get("GRAPHRAG_QUERY_MODEL"),
        timeout_seconds=120,
        retries=2,
    )

    class _LlmAdapter:
        """把 OpenAICompatibleLlmClient 适配成 orchestrator 期望的 .complete(prompt) 接口。"""

        def complete(self, prompt: str, *, temperature: float = 0.0) -> str:
            result = raw_llm.create_chat_completion(
                messages=[{"role": "user", "content": prompt}],
                model=raw_llm.model_name,
                temperature=temperature,
                max_tokens=None,
                metadata={"caller": "hybrid_qa"},
            )
            return result.content

    llm = _LlmAdapter()

    def cli_func(mode: str, prompt: str):
        return call_graphrag_cli(mode=mode, prompt=prompt, cwd=GRAPHRAG_ROOT, env=_build_query_env(None))

    _HYBRID_ORCHESTRATOR = HybridOrchestrator(
        classifier=classifier,
        bm25=bm25,
        reranker=reranker,
        graphrag_cli=cli_func,
        llm=llm,
        config=OrchestratorConfig(),
        prompt_template_path=GRAPHRAG_ROOT / "prompts" / "hybrid_search_system_prompt.txt",
    )
    return _HYBRID_ORCHESTRATOR


async def _run_hybrid_query(prompt: str) -> str:
    orchestrator = _get_hybrid_orchestrator()
    return await asyncio.to_thread(lambda: orchestrator.answer(prompt).answer)
```

然后替换 `_resolve_query_response`：

```python
async def _resolve_query_response(model: str, prompt: str) -> str:
    if model == "full-model:latest":
        raise ValueError("模型 full-model:latest 已归档为后续扩展模式，当前请使用 local、global、drift、basic 或 hybrid")

    method = SUPPORTED_QUERY_MODELS.get(model)
    if method is None:
        raise ValueError(f"不支持的模型: {model}")
    if method == "hybrid":
        return format_response(await _run_hybrid_query(prompt))
    return format_response(await run_graphrag_query_cli(method, prompt))
```

最后把 `/v1/models` 输出里加上 hybrid（约第 380~404 行）：

```python
models = [
    # ...原有 4 项...
    {
        "id": "graphrag-hybrid-search:latest",
        "object": "model",
        "created": 0,
        "owned_by": "ckqa",
    },
]
```

`/health` 输出（约第 415~417 行）追加：

```python
"hybrid_search_ready": True,
```

- [ ] **Step 7.3: 跑路由测试**

Run: `pytest graphrag_pipeline/tests/test_hybrid_qa_main_route.py -v`
Expected: 3 PASS。

- [ ] **Step 7.4: 端到端冒烟**

启动 GraphRAG API：

```bash
python -m uvicorn graphrag_pipeline.utils.main:app --host 127.0.0.1 --port 8000
```

新开 shell：

```bash
curl -s http://127.0.0.1:8000/v1/models | python -m json.tool | grep hybrid
curl -s http://127.0.0.1:8000/v1/chat/completions \
    -H 'Content-Type: application/json' \
    -d '{"model":"graphrag-hybrid-search:latest","messages":[{"role":"user","content":"DBSCAN 的两个核心超参数是什么？"}]}' \
    | python -m json.tool
```

Expected：第 1 条返回 `"id": "graphrag-hybrid-search:latest"`；第 2 条返回正常 JSON，`choices[0].message.content` 是完整答案。

- [ ] **Step 7.5: 提交**

```bash
git add graphrag_pipeline/utils/main.py \
        graphrag_pipeline/tests/test_hybrid_qa_main_route.py
git commit -m "feat(hybrid_qa): 在 GraphRAG API 中接入 hybrid 模式"
```

---

### Task 8: Hybrid 评测 + baseline 对比报告

**Files:**
- Create: `graphrag_pipeline/scripts/qa_eval/run_hybrid_eval.py`
- Create: `graphrag_pipeline/scripts/qa_eval/compare_runs.py`
- Create: `graphrag_pipeline/tests/test_qa_eval_compare.py`

- [ ] **Step 8.1: 写 hybrid 评测运行器**

写到 `graphrag_pipeline/scripts/qa_eval/run_hybrid_eval.py`：

```python
from __future__ import annotations

import argparse
import json
import logging
import sys
import time
from dataclasses import asdict
from pathlib import Path
from typing import List

from graphrag_pipeline.scripts.qa_eval.graphrag_client import OpenAICompatibleClient
from graphrag_pipeline.scripts.qa_eval.run_baseline_eval import (
    BaselineRunner,
    RunMetadata,
)
from graphrag_pipeline.scripts.qa_eval.test_set_schema import QaTestItem
from graphrag_pipeline.scripts.qa_eval.test_set_validator import validate_jsonl

LOGGER = logging.getLogger(__name__)
HYBRID_MODE = "graphrag-hybrid-search:latest"


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
    parser.add_argument("--test-set", type=Path, default=Path("graphrag_pipeline/data/eval/qa_test_set.jsonl"))
    parser.add_argument("--output-root", type=Path, default=Path("graphrag_pipeline/results/qa_eval/runs"))
    parser.add_argument("--run-id", default=time.strftime("hybrid-%Y%m%d-%H%M%S"))
    parser.add_argument("--index-run-label", required=True)
    parser.add_argument("--base-url", default="http://127.0.0.1:8000")
    parser.add_argument("--request-timeout", type=float, default=240.0)
    parser.add_argument("--max-retries", type=int, default=3)
    args = parser.parse_args(argv)

    logging.basicConfig(level=logging.INFO, format="%(asctime)s %(levelname)s %(message)s")
    items = _load_items(args.test_set)
    client = OpenAICompatibleClient(
        base_url=args.base_url,
        request_timeout_seconds=args.request_timeout,
        max_retries=args.max_retries,
        # hybrid 模式 id 不在 baseline 客户端白名单里，需放行任意模型名。
        allow_arbitrary_models=True,
    )
    # 走 BaselineRunner 但把 modes 替换成只有 hybrid 一种，run_meta.json 里也会正确写 [hybrid]。
    runner = BaselineRunner(
        client=client,
        output_root=args.output_root,
        run_id=args.run_id,
        index_run_label=args.index_run_label,
        modes=(HYBRID_MODE,),
    )
    metadata: RunMetadata = runner.run(items)
    print(json.dumps(asdict(metadata), ensure_ascii=False, indent=2))
    return 0


if __name__ == "__main__":  # pragma: no cover
    sys.exit(main())
```

> 注意：`graphrag-hybrid-search:latest` 不在 `OpenAICompatibleClient` baseline 白名单中，因此使用 `allow_arbitrary_models=True`。这与 baseline 模式严格校验不冲突——baseline runner 仍走 `allow_arbitrary_models=False`，只是 hybrid runner 单独放行。

- [ ] **Step 8.2: 写对比报告失败测试**

写到 `graphrag_pipeline/tests/test_qa_eval_compare.py`：

```python
from __future__ import annotations

import json
from pathlib import Path

from graphrag_pipeline.scripts.qa_eval.compare_runs import compare_runs


def _seed(run_dir: Path, qid: str, modes: dict[str, dict]) -> None:
    raw = run_dir / "raw"
    raw.mkdir(parents=True, exist_ok=True)
    (raw / f"{qid}.json").write_text(
        json.dumps(
            {
                "id": qid,
                "category": "factual_lookup",
                "question": "?",
                "gold_answer_summary": "a",
                "gold_entities": ["foo"],
                "must_cite_terms": [],
                "negative_terms": [],
                "modes": modes,
            },
            ensure_ascii=False,
            indent=2,
        ),
        encoding="utf-8",
    )
    (run_dir / "run_meta.json").write_text(
        json.dumps(
            {
                "run_id": run_dir.name,
                "index_run_label": "course-x",
                "total_items": 1,
                "modes": list(modes.keys()),
            },
            ensure_ascii=False,
            indent=2,
        ),
        encoding="utf-8",
    )


def test_compare_runs_writes_markdown(tmp_path: Path):
    baseline_dir = tmp_path / "baseline"
    hybrid_dir = tmp_path / "hybrid"
    _seed(
        baseline_dir,
        "Q001",
        {
            "graphrag-local-search:latest": {"answer": "foo", "total_tokens": 1, "elapsed_seconds": 0.1},
            "graphrag-global-search:latest": {"answer": "bar", "total_tokens": 1, "elapsed_seconds": 0.1},
            "graphrag-drift-search:latest": {"answer": "foo", "total_tokens": 1, "elapsed_seconds": 0.1},
            "graphrag-basic-search:latest": {"answer": "foo", "total_tokens": 1, "elapsed_seconds": 0.1},
        },
    )
    _seed(
        hybrid_dir,
        "Q001",
        {
            "graphrag-hybrid-search:latest": {"answer": "foo", "total_tokens": 1, "elapsed_seconds": 0.5},
        },
    )
    out = compare_runs(baseline_dir=baseline_dir, hybrid_dir=hybrid_dir, output_path=tmp_path / "comparison.md")
    text = out.read_text(encoding="utf-8")
    assert "graphrag-local-search:latest" in text
    assert "graphrag-hybrid-search:latest" in text
    assert "entity_hit_rate" in text
```

- [ ] **Step 8.3: 跑测试确认失败**

Run: `pytest graphrag_pipeline/tests/test_qa_eval_compare.py -v`
Expected: ImportError on `graphrag_pipeline.scripts.qa_eval.compare_runs`.

- [ ] **Step 8.4: 写对比报告实现**

写到 `graphrag_pipeline/scripts/qa_eval/compare_runs.py`：

```python
from __future__ import annotations

import argparse
import json
import logging
import sys
from pathlib import Path
from typing import Dict, List

from graphrag_pipeline.scripts.qa_eval.baseline_scorer import (
    PER_QUESTION_METRICS,
    score_baseline_run,
)

LOGGER = logging.getLogger(__name__)


def _format_row(mode: str, per_mode: Dict[str, float]) -> str:
    cells = [
        mode,
        f"{per_mode.get('entity_hit_rate', 0):.4f}",
        f"{per_mode.get('must_cite_hit', 0):.4f}",
        f"{per_mode.get('citation_format_present', 0):.4f}",
        f"{per_mode.get('negative_hit', 0):.4f}",
        f"{per_mode.get('answer_chars', 0):.1f}",
        f"{per_mode.get('elapsed_seconds', 0):.2f}",
        f"{int(per_mode.get('error_count', 0))}",
    ]
    return "| " + " | ".join(cells) + " |"


def compare_runs(*, baseline_dir: Path, hybrid_dir: Path, output_path: Path) -> Path:
    baseline = score_baseline_run(baseline_dir)
    hybrid = score_baseline_run(hybrid_dir)

    lines: List[str] = [
        "# baseline vs hybrid comparison",
        "",
        f"- baseline run: `{baseline_dir.name}`",
        f"- hybrid run: `{hybrid_dir.name}`",
        "",
        "## 总体均值",
        "",
        "| mode | entity_hit_rate | must_cite_hit | citation_format_present | negative_hit | answer_chars | elapsed_seconds | error_count |",
        "| --- | --- | --- | --- | --- | --- | --- | --- |",
    ]
    for mode in baseline.modes:
        lines.append(_format_row(mode, baseline.per_mode[mode]))
    for mode in hybrid.modes:
        lines.append(_format_row(mode, hybrid.per_mode[mode]))

    lines.extend(["", "## 按题分项（hybrid vs baseline 最优）", ""])
    lines.append("| qid | baseline_best_mode | baseline_best_hit | hybrid_hit | delta |")
    lines.append("| --- | --- | --- | --- | --- |")

    for qid, per_mode in baseline.per_question.items():
        best_mode, best_hit = max(
            ((m, row.get("entity_hit_rate", 0.0)) for m, row in per_mode.items()),
            key=lambda pair: pair[1],
        )
        hybrid_hit = (
            hybrid.per_question.get(qid, {})
            .get("graphrag-hybrid-search:latest", {})
            .get("entity_hit_rate", 0.0)
        )
        delta = hybrid_hit - best_hit
        lines.append(
            f"| {qid} | {best_mode} | {best_hit:.4f} | {hybrid_hit:.4f} | {delta:+.4f} |"
        )

    output_path.parent.mkdir(parents=True, exist_ok=True)
    output_path.write_text("\n".join(lines) + "\n", encoding="utf-8")
    return output_path


def main(argv: List[str] | None = None) -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--baseline-dir", type=Path, required=True)
    parser.add_argument("--hybrid-dir", type=Path, required=True)
    parser.add_argument("--output", type=Path, required=True)
    args = parser.parse_args(argv)
    logging.basicConfig(level=logging.INFO, format="%(asctime)s %(levelname)s %(message)s")
    out = compare_runs(baseline_dir=args.baseline_dir, hybrid_dir=args.hybrid_dir, output_path=args.output)
    print(out)
    return 0


if __name__ == "__main__":  # pragma: no cover
    sys.exit(main())
```

- [ ] **Step 8.5: 跑测试通过**

Run: `pytest graphrag_pipeline/tests/test_qa_eval_compare.py -v`
Expected: 1 PASS。

- [ ] **Step 8.6: 端到端跑 hybrid 评测 + 对比**

```bash
BASELINE_ID=<填 Plan 1 跑出来的 run_id>
HYBRID_ID=hybrid-$(date +%Y%m%d-%H%M%S)
INDEX_LABEL="$(jq -r '.candidate' graphrag_pipeline/prompts/final/active_prompt.json)@$(date +%Y-%m-%d)"

# 1) 跑 hybrid 模式
python -m graphrag_pipeline.scripts.qa_eval.run_hybrid_eval \
    --test-set graphrag_pipeline/data/eval/qa_test_set.jsonl \
    --output-root graphrag_pipeline/results/qa_eval/runs \
    --run-id "$HYBRID_ID" \
    --index-run-label "$INDEX_LABEL"

# 2) 复用 baseline 的规则评分 + 裁判评分 + 报告生成
python -m graphrag_pipeline.scripts.qa_eval.baseline_scorer \
    --run-dir "graphrag_pipeline/results/qa_eval/runs/$HYBRID_ID"

GRAPHRAG_JUDGE_MODEL=${GRAPHRAG_JUDGE_MODEL:-gpt-4o-mini}
python -m graphrag_pipeline.scripts.qa_eval.judge_scorer \
    --run-dir "graphrag_pipeline/results/qa_eval/runs/$HYBRID_ID" \
    --judge-model "$GRAPHRAG_JUDGE_MODEL"

python -m graphrag_pipeline.scripts.qa_eval.baseline_reporter \
    --run-dir "graphrag_pipeline/results/qa_eval/runs/$HYBRID_ID"

# 3) baseline vs hybrid 对比
python -m graphrag_pipeline.scripts.qa_eval.compare_runs \
    --baseline-dir "graphrag_pipeline/results/qa_eval/runs/$BASELINE_ID" \
    --hybrid-dir "graphrag_pipeline/results/qa_eval/runs/$HYBRID_ID" \
    --output "graphrag_pipeline/results/qa_eval/runs/$HYBRID_ID/comparison.md"
```

Expected：`comparison.md` 中 hybrid 在「事实定位」类（前文 manual_review 标注的题号）`entity_hit_rate` 与 `semantic_correctness` 不低于基线最优值；「全局概览」类 `elapsed_seconds` 显著小于 GraphRAG global 模式。

- [ ] **Step 8.7: 提交**

```bash
git add graphrag_pipeline/scripts/qa_eval/run_hybrid_eval.py \
        graphrag_pipeline/scripts/qa_eval/compare_runs.py \
        graphrag_pipeline/tests/test_qa_eval_compare.py \
        graphrag_pipeline/results/qa_eval/runs/$HYBRID_ID/comparison.md \
        graphrag_pipeline/results/qa_eval/runs/$HYBRID_ID/scoring.md \
        graphrag_pipeline/results/qa_eval/runs/$HYBRID_ID/rule_scoring.csv \
        graphrag_pipeline/results/qa_eval/runs/$HYBRID_ID/run_meta.json
git commit -m "feat(qa_eval): 新增 hybrid 评测运行器与 baseline 对比报告"
```

---

### Task 9: Java 后端放行 hybrid 模式

**Files:**
- Modify: `backend/ckqa-back/src/main/java/org/ysu/ckqaback/integration/config/CkqaIntegrationProperties.java`（如果该类里有模式白名单）
- Modify: `backend/ckqa-back/src/main/java/org/ysu/ckqaback/qa/QaWorkflowService.java`（如果 `sendMessage` 内部对 mode 做白名单校验）

- [ ] **Step 9.1: 找当前模式白名单位置**

Run: `grep -RIn "basic\|local\|global\|drift" backend/ckqa-back/src/main/java | grep -i mode | head -20`
Expected：得到一个或多个枚举/常量定义点。

- [ ] **Step 9.2: 把 hybrid 加进白名单**

依据 Step 9.1 的搜索结果，把 `hybrid` 字符串加入对应枚举/常量。常见两种形态：

形态 A（枚举型）：

```java
public enum QueryMode {
    LOCAL("local"),
    GLOBAL("global"),
    DRIFT("drift"),
    BASIC("basic"),
    HYBRID("hybrid");
    // ...
}
```

形态 B（properties.yml 列表）：

```yaml
ckqa:
  integration:
    qa:
      allowed-modes: [local, global, drift, basic, hybrid]
```

按实际项目结构二选一改动，确保新模式不会被 `BusinessException(ApiResultCode.QA_MODE_UNSUPPORTED)` 之类的校验挡掉。

- [ ] **Step 9.3: 跑后端测试**

Run: `cd backend/ckqa-back && ./mvnw test -Dtest=QaWorkflow*`
Expected：原有测试不回归；如有专门测 mode 白名单的用例，加一条 `hybrid` 也能通过的 assertion。

- [ ] **Step 9.4: 端到端冒烟**

按照仓库 README 启 Java 后端，前端切到学生问答页发一条消息（model 选 hybrid），确认 `QaWorkflowService -> GraphRAG API -> orchestrator -> 答案` 全链路返回。

- [ ] **Step 9.5: 提交**

```bash
git add backend/ckqa-back/src/main/java/org/ysu/ckqaback/integration/config/CkqaIntegrationProperties.java \
        backend/ckqa-back/src/main/java/org/ysu/ckqaback/qa/QaWorkflowService.java
git commit -m "feat(qa_back): 放行 hybrid 检索模式"
```

> 如果搜索结果显示后端原本就只透传字符串而不做白名单，则跳过本任务并在计划顶部备注「无需后端改动」。

---

### Task 10: 文档与发布

**Files:**
- Modify: `graphrag_pipeline/PROMPT_TUNING_PIPELINE.md`
- Create: `graphrag_pipeline/scripts/hybrid_qa/README.md`
- Modify: `graphrag_pipeline/README.md`（若仓库根 README 已经罗列模式，要加 hybrid）

- [ ] **Step 10.1: 写 hybrid_qa 模块 README**

写到 `graphrag_pipeline/scripts/hybrid_qa/README.md`：

````markdown
# hybrid_qa 模块

把「问题分层 + BM25 + GraphRAG 多路召回 + 轻量 reranker + 两段证据」整合为一个 hybrid 检索模式，通过 `graphrag-hybrid-search:latest` 模型 id 暴露给上层。

## 设计原则

- **只改查询层，不改索引主流程**：完全消费 GraphRAG 现有 `output/text_units.parquet` 与 `lancedb/`，不重建索引。
- **复杂度可控**：BM25 按需触发；reranker 仅对去重后 ≤ 20 条候选池打分；Global 不作为默认。
- **可解释**：每次回答都附带 `diagnostics`，写出 layer / classifier_confidence / pool_size / top_n / 引用 ref 列表。

## 路由

| 问题分层 | 触发的召回 | 备注 |
| --- | --- | --- |
| LOW | BM25 top-15 + Basic 整段 | 课程章节定位、术语、公式、参数 |
| HIGH | Global 整段 + BM25 top-5 | 课程整体方法论 / 知识体系 |
| MIXED | BM25 top-10 + Local + DRIFT + Global | 对比、章节小结、关系类问题 |

## 运行

```bash
python -m uvicorn graphrag_pipeline.utils.main:app --host 127.0.0.1 --port 8000
curl http://127.0.0.1:8000/v1/chat/completions \
    -H 'Content-Type: application/json' \
    -d '{"model":"graphrag-hybrid-search:latest","messages":[{"role":"user","content":"<问题>"}]}'
```

## 评测

参见 `scripts/qa_eval/run_hybrid_eval.py` 与 `compare_runs.py`，与 baseline 共用题面与评分器。
````

- [ ] **Step 10.2: 在 PROMPT_TUNING_PIPELINE.md 加一段 hybrid 入口**

在 `## QA 评测` 段落末尾追加：

```markdown
## Hybrid 检索

`scripts/hybrid_qa/` 提供 `graphrag-hybrid-search:latest` 模式：

- `classifier.py`：规则 + 示例 kNN 把问题分为低层 / 高层 / 混合；
- `bm25_index.py`：jieba + rank_bm25 索引 `text_units.parquet`；
- `reranker.py`：BGE-reranker-v2-m3 二阶段精排；
- `prompt_builder.py`：两段证据 Prompt 装配；
- `orchestrator.py`：把以上模块串成最终回答。

调用入口与四个 GraphRAG 原生模式一致，走 `utils/main.py` 的 `/v1/chat/completions`。
```

- [ ] **Step 10.3: 提交**

```bash
git add graphrag_pipeline/scripts/hybrid_qa/README.md \
        graphrag_pipeline/PROMPT_TUNING_PIPELINE.md
git commit -m "docs(hybrid_qa): 撰写 hybrid 模式说明与入口指引"
```

- [ ] **Step 10.4: 整体回归**

```bash
pytest graphrag_pipeline/tests -k "hybrid_qa or qa_eval" -v
```

Expected：本计划与 Plan 1 涉及的所有测试用例全绿。

---

## 自检清单

- 任务 7（问题分层器）：交付 `classifier.py` + exemplars + 单测；规则优先 + kNN 兜底，符合「轻量化机器学习算法」要求 → ✅ Task 3 覆盖。
- 任务 8（BM25）：交付 `bm25_index.py` + jieba 分词 + 缓存；orchestrator 按 layer 决定是否触发并选 top_k → ✅ Task 2 + Task 6 覆盖。
- 任务 9（轻量 reranker）：交付 `reranker.py` + BGE 包装 + 候选池上限 20 + top_n=8 → ✅ Task 4 + Task 6 覆盖。
- 任务 10（两段证据 + 提示词）：交付 `hybrid_search_system_prompt.txt` + `prompt_builder.py` + 「冲突时原文优先」规则 → ✅ Task 5 覆盖。
- Phase 4 复杂度控制原则：索引主流程零改动、规则优先、BM25 仅按需触发、reranker 上限 20、Global 不作为默认 → 上述各 Task 中显式遵循。
- 评测对比：`run_hybrid_eval` + `compare_runs` → ✅ Task 8 覆盖。
- 上线放行：Java 白名单 → ✅ Task 9 覆盖。
- 文档沉淀：模块 README + 流水线文档段落 → ✅ Task 10 覆盖。
