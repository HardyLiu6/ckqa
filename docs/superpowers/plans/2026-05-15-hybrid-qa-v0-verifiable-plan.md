# 课程问答 Hybrid v0 可验证模式实施计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 在保留原 `2026-05-12-hybrid-qa-mode.md` 设计不变的前提下，先落地一个轻量、可评测、可回滚的 `hybrid-v0`：以 `BM25 + Basic` 为主路径，`Local` 只作为低置信 fallback，并加入“答案-证据”BGE-M3 语义支撑检测，让 hybrid 是否值得继续投入有真实数据依据。

**Architecture:** 新增 `graphrag_pipeline/scripts/hybrid_qa/` 的 v0 子能力，但不接入 Global / DRIFT 默认路径，不改 GraphRAG 索引流程，不调用易漂移的 GraphRAG 内部 Python API。v0 查询链路为：问题分层 -> text unit BM25 召回 -> Basic 查询 -> 引用解析映射回 text units -> 两段证据 prompt -> LLM 生成 -> BGE-M3 answer-vs-evidence guardrail -> 必要时 Local fallback -> 返回答案与 diagnostics。所有线上语义检测只比较“答案 vs 已选证据”，离线评测继续复用 `algorithmic_scorer` 的 gold-based BGE-M3 coverage。

**Tech Stack:** Python 3.10+、Pydantic 2、pandas、rank-bm25、jieba、FlagEmbedding / BGE-M3、numpy、pytest；复用现有 `qa_eval` 的 `run_loader`、`citation_extractor`、`text_unit_lookup`、`semantic_similarity`、`algorithmic_scorer` 与 `significance_reporter`。

---

## 为什么不直接执行原 Hybrid 计划

最新真实评测显示：

| 模式 | overall `effective_score_experimental` | 平均延迟 | 结论 |
| --- | ---: | ---: | --- |
| `basic` | 0.7500 | 49.8s | 当前默认主路径 |
| `local` | 0.6224 | 74.3s | 可作为 fallback / augmentation |
| `global` | 0.4589 | 247.6s | 暂不进入默认路由 |
| `drift` | 1 条 lite run 超时 | 360s | 暂不进入默认路由 |

因此原计划中 “HIGH -> Global” 和 “MIXED -> Local + DRIFT + Global” 现在会把 hybrid v0 直接拖慢并引入不稳定性。v0 的目标不是一次做完整 multi-route RAG，而是先验证：**BM25 补充原文证据 + Basic 主答 + Local fallback + 语义支撑检测** 是否能优于当前 basic/local baseline。

---

## File Structure

| 路径 | 责任 |
| --- | --- |
| `graphrag_pipeline/pyproject.toml` | 新增 `hybrid` optional extra，包含 `rank-bm25`；复用 `eval` extra 中已有的 `FlagEmbedding` / `jieba` / `scikit-learn` |
| `graphrag_pipeline/scripts/hybrid_qa/__init__.py` | hybrid qa 包入口 |
| `graphrag_pipeline/scripts/hybrid_qa/types.py` | `HybridLayer`、`EvidenceCandidate`、`HybridV0Answer`、`HybridDiagnostics` |
| `graphrag_pipeline/scripts/hybrid_qa/bm25_text_units.py` | 从当前 run/index 的 `text_units.parquet` 构建 text-unit BM25 索引 |
| `graphrag_pipeline/scripts/hybrid_qa/question_classifier.py` | 规则优先的问题层级分类：low / mixed / high |
| `graphrag_pipeline/scripts/hybrid_qa/community_report_store.py` | 可选读取 `community_reports.parquet`，只做离线高层证据片段，不跑 Global 查询 |
| `graphrag_pipeline/scripts/hybrid_qa/evidence_guardrail.py` | BGE-M3 answer-vs-evidence 语义支撑检测 |
| `graphrag_pipeline/scripts/hybrid_qa/prompt_builder.py` | 两段证据 prompt 装配 |
| `graphrag_pipeline/scripts/hybrid_qa/basic_local_client.py` | 复用 `utils/main.py` 的 CLI 查询路径调用 basic/local，并设置 timeout budget |
| `graphrag_pipeline/scripts/hybrid_qa/orchestrator_v0.py` | 串接 BM25、Basic、Local fallback、prompt、guardrail |
| `graphrag_pipeline/prompts/hybrid_v0_system_prompt.txt` | v0 两段证据提示词 |
| `graphrag_pipeline/utils/main.py` | 注册 `graphrag-hybrid-v0-search:latest`，lazy singleton 调用 orchestrator |
| `graphrag_pipeline/scripts/qa_eval/run_hybrid_v0_eval.py` | 只跑 hybrid-v0 模式的 eval runner |
| `graphrag_pipeline/scripts/qa_eval/compare_algorithmic_runs.py` | 使用 `algorithmic_scoring.csv` 对比 baseline 与 hybrid-v0 |
| `graphrag_pipeline/tests/test_hybrid_qa_v0_*.py` | v0 单元测试与 mock 集成测试 |
| `graphrag_pipeline/tests/test_qa_eval_hybrid_v0_*.py` | v0 eval / compare 测试 |
| `graphrag_pipeline/README.md` | 增加 hybrid-v0 运行和评测说明 |

---

## 完成判据

1. `conda run -n graphrag-oneapi python -m pytest graphrag_pipeline/tests/test_hybrid_qa_v0_*.py graphrag_pipeline/tests/test_qa_eval_hybrid_v0_*.py -v` 全绿。
2. `curl http://127.0.0.1:8000/v1/models` 返回包含 `graphrag-hybrid-v0-search:latest`。
3. `python -m graphrag_pipeline.scripts.qa_eval.run_hybrid_v0_eval --run-id <run_id>` 能跑完 `qa_test_set.jsonl`。
4. hybrid-v0 run 能继续跑：
   - `algorithmic_scorer`
   - `significance_reporter`
   - `compare_algorithmic_runs`
5. 在 32 题样本上，hybrid-v0 至少满足：
   - `error_count == 0`
   - 平均 `elapsed_seconds <= local` 当前均值 74.3s 的 1.25 倍，即 `<= 93s`
   - `semantic_coverage_f1` 不低于 basic 均值 0.9903 超过 0.03，即 `>= 0.960`
   - `citation_recall_at_3` 不低于 local 均值 0.1990，优先追 basic 均值 0.5432
6. 若上述任一指标失败，`hybrid-v0` 只能保留为实验模式，不允许作为默认学生问答路由。

---

## Task 1: 依赖边界与基础类型

**Files:**
- Modify: `graphrag_pipeline/pyproject.toml`
- Create: `graphrag_pipeline/scripts/hybrid_qa/__init__.py`
- Create: `graphrag_pipeline/scripts/hybrid_qa/types.py`
- Create: `graphrag_pipeline/tests/test_hybrid_qa_v0_types.py`

- [ ] **Step 1.1: 写依赖与类型测试**

写到 `graphrag_pipeline/tests/test_hybrid_qa_v0_types.py`：

```python
from __future__ import annotations

import tomllib
from pathlib import Path

from graphrag_pipeline.scripts.hybrid_qa.types import (
    EvidenceCandidate,
    HybridDiagnostics,
    HybridLayer,
)


def test_hybrid_extra_keeps_heavy_deps_optional():
    pyproject = tomllib.loads(Path("graphrag_pipeline/pyproject.toml").read_text(encoding="utf-8"))
    extras = pyproject["project"]["optional-dependencies"]
    hybrid_extra = "\n".join(extras["hybrid"])

    assert "rank-bm25" in hybrid_extra
    assert "FlagEmbedding" in "\n".join(extras["eval"])
    assert "rank-bm25" not in "\n".join(pyproject["project"]["dependencies"])


def test_evidence_candidate_dedup_key_normalizes_text():
    a = EvidenceCandidate(source="bm25", ref="tu-1", text=" 操作系统\n 是 第一层软件 ", score=0.8, layer=HybridLayer.LOW)
    b = EvidenceCandidate(source="basic-citation", ref="tu-2", text="操作系统 是 第一层软件", score=0.5, layer=HybridLayer.LOW)

    assert a.dedup_key == b.dedup_key


def test_diagnostics_defaults_are_stable():
    diagnostics = HybridDiagnostics(layer=HybridLayer.MIXED, classifier_confidence=0.7)

    assert diagnostics.layer is HybridLayer.MIXED
    assert diagnostics.used_local_fallback is False
    assert diagnostics.guardrail_status == "not_checked"
```

- [ ] **Step 1.2: 跑测试确认失败**

Run:

```bash
conda run -n graphrag-oneapi python -m pytest graphrag_pipeline/tests/test_hybrid_qa_v0_types.py -v
```

Expected: FAIL，`KeyError: 'hybrid'` 或 ImportError。

- [ ] **Step 1.3: 新增 optional extra**

修改 `graphrag_pipeline/pyproject.toml` 的 `[project.optional-dependencies]`：

```toml
hybrid = [
    # Hybrid v0 lexical recall. BGE-M3 / jieba / sklearn are inherited from eval when using all.
    "rank-bm25>=0.2.2",
]
all = [
    "graphrag-pipeline[ml,graph,scraper,eval,hybrid]",
]
```

不要把 `rank-bm25` 放进主依赖，避免默认 API server 安装面继续变大。

- [ ] **Step 1.4: 写基础类型**

写到 `graphrag_pipeline/scripts/hybrid_qa/__init__.py`：

```python
"""Hybrid QA v0: BM25 + Basic primary path + Local fallback + evidence guardrail."""
```

写到 `graphrag_pipeline/scripts/hybrid_qa/types.py`：

```python
from __future__ import annotations

import re
from dataclasses import dataclass, field
from enum import Enum
from typing import Any


class HybridLayer(str, Enum):
    LOW = "low"
    MIXED = "mixed"
    HIGH = "high"


_SPACE_RE = re.compile(r"\s+")


@dataclass(frozen=True, slots=True)
class EvidenceCandidate:
    source: str
    ref: str
    text: str
    score: float
    layer: HybridLayer
    metadata: dict[str, Any] = field(default_factory=dict)

    @property
    def dedup_key(self) -> str:
        return _SPACE_RE.sub(" ", self.text.strip()).casefold()


@dataclass(frozen=True, slots=True)
class HybridDiagnostics:
    layer: HybridLayer
    classifier_confidence: float
    used_local_fallback: bool = False
    guardrail_status: str = "not_checked"
    guardrail_score: float = 0.0
    low_evidence_count: int = 0
    high_evidence_count: int = 0
    elapsed_seconds: float = 0.0
    errors: list[str] = field(default_factory=list)


@dataclass(frozen=True, slots=True)
class HybridV0Answer:
    answer: str
    diagnostics: HybridDiagnostics
```

- [ ] **Step 1.5: 跑测试通过**

Run:

```bash
conda run -n graphrag-oneapi python -m pytest graphrag_pipeline/tests/test_hybrid_qa_v0_types.py -v
```

Expected: 3 PASS。

- [ ] **Step 1.6: 提交**

```bash
git add graphrag_pipeline/pyproject.toml \
        graphrag_pipeline/scripts/hybrid_qa/__init__.py \
        graphrag_pipeline/scripts/hybrid_qa/types.py \
        graphrag_pipeline/tests/test_hybrid_qa_v0_types.py
git commit -m "feat(hybrid_qa): 定义 hybrid v0 依赖边界与基础类型"
```

---

## Task 2: Text Unit BM25 召回

**Files:**
- Create: `graphrag_pipeline/scripts/hybrid_qa/bm25_text_units.py`
- Create: `graphrag_pipeline/tests/test_hybrid_qa_v0_bm25_text_units.py`

- [ ] **Step 2.1: 写 BM25 失败测试**

写到 `graphrag_pipeline/tests/test_hybrid_qa_v0_bm25_text_units.py`：

```python
from __future__ import annotations

from pathlib import Path

import pandas as pd

from graphrag_pipeline.scripts.hybrid_qa.bm25_text_units import build_text_unit_bm25
from graphrag_pipeline.scripts.hybrid_qa.types import HybridLayer


def _write_text_units(tmp_path: Path) -> Path:
    path = tmp_path / "text_units.parquet"
    pd.DataFrame(
        [
            {"id": "tu-001", "text": "操作系统是配置在计算机硬件上的第一层软件。"},
            {"id": "tu-002", "text": "DBSCAN 使用 eps 和 MinPts 描述密度聚类。"},
            {"id": "tu-003", "text": "K-Means 通过迭代更新簇中心完成聚类。"},
        ]
    ).to_parquet(path)
    return path


def test_text_unit_bm25_returns_low_layer_evidence(tmp_path: Path):
    index = build_text_unit_bm25(_write_text_units(tmp_path), cache_dir=tmp_path / "cache")

    matches = index.search("操作系统 第一层软件", top_k=2)

    assert matches[0].ref == "tu-001"
    assert matches[0].layer is HybridLayer.LOW
    assert matches[0].source == "bm25"


def test_text_unit_bm25_cache_reused_when_source_unchanged(tmp_path: Path):
    text_units = _write_text_units(tmp_path)
    cache_dir = tmp_path / "cache"

    first = build_text_unit_bm25(text_units, cache_dir=cache_dir)
    second = build_text_unit_bm25(text_units, cache_dir=cache_dir)

    assert first.size == second.size == 3
    assert (cache_dir / "text_unit_bm25.pkl").exists()
```

- [ ] **Step 2.2: 跑测试确认失败**

Run:

```bash
conda run -n graphrag-oneapi python -m pytest graphrag_pipeline/tests/test_hybrid_qa_v0_bm25_text_units.py -v
```

Expected: FAIL，ImportError。

- [ ] **Step 2.3: 实现 BM25**

写到 `graphrag_pipeline/scripts/hybrid_qa/bm25_text_units.py`：

```python
from __future__ import annotations

import json
import os
import pickle
import re
from dataclasses import dataclass
from pathlib import Path

import jieba
import pandas as pd
from rank_bm25 import BM25Okapi

from graphrag_pipeline.scripts.hybrid_qa.types import EvidenceCandidate, HybridLayer


_PUNCT_RE = re.compile(r"^[\\s\\.,，。;；:：()（）\\[\\]【】\"'`]+$")


def _tokenize(text: str) -> list[str]:
    tokens: list[str] = []
    for token in jieba.cut_for_search(text or ""):
        token = token.strip().casefold()
        if token and not _PUNCT_RE.match(token):
            tokens.append(token)
    return tokens


@dataclass(slots=True)
class TextUnitBm25:
    refs: list[str]
    texts: list[str]
    tokens: list[list[str]]
    bm25: BM25Okapi

    @property
    def size(self) -> int:
        return len(self.refs)

    def search(self, query: str, *, top_k: int = 10) -> list[EvidenceCandidate]:
        query_tokens = _tokenize(query)
        if not query_tokens:
            return []
        scores = self.bm25.get_scores(query_tokens)
        ranked = sorted(enumerate(scores), key=lambda item: item[1], reverse=True)
        out: list[EvidenceCandidate] = []
        for index, score in ranked[:top_k]:
            if float(score) <= 0:
                continue
            out.append(
                EvidenceCandidate(
                    source="bm25",
                    ref=self.refs[index],
                    text=self.texts[index],
                    score=round(float(score), 4),
                    layer=HybridLayer.LOW,
                )
            )
        return out


def _cache_files(cache_dir: Path) -> tuple[Path, Path]:
    return cache_dir / "text_unit_bm25.pkl", cache_dir / "text_unit_bm25.meta.json"


def _load_cache(parquet_path: Path, cache_dir: Path) -> TextUnitBm25 | None:
    pkl, meta = _cache_files(cache_dir)
    if not pkl.exists() or not meta.exists():
        return None
    payload = json.loads(meta.read_text(encoding="utf-8"))
    if payload.get("parquet_path") != str(parquet_path.resolve()):
        return None
    if payload.get("parquet_mtime") != os.path.getmtime(parquet_path):
        return None
    with pkl.open("rb") as fh:
        return pickle.load(fh)


def _store_cache(index: TextUnitBm25, parquet_path: Path, cache_dir: Path) -> None:
    cache_dir.mkdir(parents=True, exist_ok=True)
    pkl, meta = _cache_files(cache_dir)
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


def build_text_unit_bm25(parquet_path: Path, *, cache_dir: Path) -> TextUnitBm25:
    cached = _load_cache(parquet_path, cache_dir)
    if cached is not None:
        return cached
    frame = pd.read_parquet(parquet_path, columns=["id", "text"])
    refs = frame["id"].astype(str).tolist()
    texts = frame["text"].fillna("").astype(str).tolist()
    tokens = [_tokenize(text) for text in texts]
    index = TextUnitBm25(refs=refs, texts=texts, tokens=tokens, bm25=BM25Okapi(tokens))
    _store_cache(index, parquet_path, cache_dir)
    return index
```

- [ ] **Step 2.4: 跑测试通过**

Run:

```bash
conda run -n graphrag-oneapi python -m pytest graphrag_pipeline/tests/test_hybrid_qa_v0_bm25_text_units.py -v
```

Expected: 2 PASS。

- [ ] **Step 2.5: 提交**

```bash
git add graphrag_pipeline/scripts/hybrid_qa/bm25_text_units.py \
        graphrag_pipeline/tests/test_hybrid_qa_v0_bm25_text_units.py
git commit -m "feat(hybrid_qa): 增加 text unit BM25 召回"
```

---

## Task 3: 问题分层器（规则优先）

**Files:**
- Create: `graphrag_pipeline/scripts/hybrid_qa/question_classifier.py`
- Create: `graphrag_pipeline/tests/test_hybrid_qa_v0_question_classifier.py`

- [ ] **Step 3.1: 写分类器测试**

写到 `graphrag_pipeline/tests/test_hybrid_qa_v0_question_classifier.py`：

```python
from __future__ import annotations

from graphrag_pipeline.scripts.hybrid_qa.question_classifier import classify_question
from graphrag_pipeline.scripts.hybrid_qa.types import HybridLayer


def test_classifies_factual_lookup_as_low():
    result = classify_question("操作系统的定义是什么？")
    assert result.layer is HybridLayer.LOW
    assert result.confidence >= 0.6


def test_classifies_relation_question_as_mixed():
    result = classify_question("K-Means 和 DBSCAN 的区别是什么？")
    assert result.layer is HybridLayer.MIXED


def test_classifies_course_overview_as_high():
    result = classify_question("这门课程贯穿始终的方法论主线是什么？")
    assert result.layer is HybridLayer.HIGH


def test_question_classifier_records_rule_hits():
    result = classify_question("请总结第 3 章的主要内容")
    assert result.diagnostics["rule_hits"]
```

- [ ] **Step 3.2: 跑测试确认失败**

Run:

```bash
conda run -n graphrag-oneapi python -m pytest graphrag_pipeline/tests/test_hybrid_qa_v0_question_classifier.py -v
```

Expected: FAIL，ImportError。

- [ ] **Step 3.3: 实现规则分类**

写到 `graphrag_pipeline/scripts/hybrid_qa/question_classifier.py`：

```python
from __future__ import annotations

import re
from dataclasses import dataclass

from graphrag_pipeline.scripts.hybrid_qa.types import HybridLayer


LOW_PATTERNS = [
    re.compile(r"定义|是什么|全称|参数|默认值|第\\s*[一二三四五六七八九十\\d]+\\s*[章节页]"),
    re.compile(r"哪里|哪一页|哪一节|列出|指出"),
]
MIXED_PATTERNS = [
    re.compile(r"区别|差异|联系|关系|对比|为什么|为何"),
    re.compile(r"总结第|概括第|归纳第"),
]
HIGH_PATTERNS = [
    re.compile(r"整体|总体|全局|贯穿|主线|脉络|方法论|知识体系"),
    re.compile(r"这门课|本课程|全课程"),
]


@dataclass(frozen=True, slots=True)
class QuestionClassification:
    layer: HybridLayer
    confidence: float
    diagnostics: dict[str, list[str]]


def _hits(question: str, patterns: list[re.Pattern[str]]) -> list[str]:
    return [pattern.pattern for pattern in patterns if pattern.search(question)]


def classify_question(question: str) -> QuestionClassification:
    low_hits = _hits(question, LOW_PATTERNS)
    mixed_hits = _hits(question, MIXED_PATTERNS)
    high_hits = _hits(question, HIGH_PATTERNS)
    scores = {
        HybridLayer.LOW: len(low_hits),
        HybridLayer.MIXED: len(mixed_hits),
        HybridLayer.HIGH: len(high_hits),
    }
    if scores[HybridLayer.HIGH] >= 1 and scores[HybridLayer.LOW] == 0:
        layer = HybridLayer.HIGH
    else:
        layer = max(scores, key=scores.get)
    if scores[layer] == 0:
        layer = HybridLayer.LOW
        confidence = 0.5
    else:
        confidence = min(0.95, 0.55 + 0.2 * scores[layer])
    return QuestionClassification(
        layer=layer,
        confidence=confidence,
        diagnostics={"rule_hits": low_hits + mixed_hits + high_hits},
    )
```

- [ ] **Step 3.4: 跑测试通过**

Run:

```bash
conda run -n graphrag-oneapi python -m pytest graphrag_pipeline/tests/test_hybrid_qa_v0_question_classifier.py -v
```

Expected: 4 PASS。

- [ ] **Step 3.5: 提交**

```bash
git add graphrag_pipeline/scripts/hybrid_qa/question_classifier.py \
        graphrag_pipeline/tests/test_hybrid_qa_v0_question_classifier.py
git commit -m "feat(hybrid_qa): 增加 hybrid v0 问题分层器"
```

---

## Task 4: 在线答案-证据语义支撑检测

**Files:**
- Create: `graphrag_pipeline/scripts/hybrid_qa/evidence_guardrail.py`
- Create: `graphrag_pipeline/tests/test_hybrid_qa_v0_evidence_guardrail.py`

本任务是 v0 的“语义检测”核心。它不使用 gold answer；线上只检测生成答案是否能被已选 evidence 支撑。

- [ ] **Step 4.1: 写 guardrail 测试**

写到 `graphrag_pipeline/tests/test_hybrid_qa_v0_evidence_guardrail.py`：

```python
from __future__ import annotations

from unittest.mock import patch

import numpy as np

from graphrag_pipeline.scripts.hybrid_qa.evidence_guardrail import (
    EvidenceGuardrailConfig,
    check_answer_supported_by_evidence,
)
from graphrag_pipeline.scripts.hybrid_qa.types import EvidenceCandidate, HybridLayer


def test_guardrail_passes_when_answer_chunks_are_supported():
    evidence = [EvidenceCandidate(source="bm25", ref="tu-1", text="操作系统是第一层软件。", score=1, layer=HybridLayer.LOW)]
    with patch(
        "graphrag_pipeline.scripts.hybrid_qa.evidence_guardrail._answer_evidence_similarity",
        return_value=np.asarray([[0.8]], dtype=np.float32),
    ):
        result = check_answer_supported_by_evidence(
            "操作系统是第一层软件。",
            evidence,
            config=EvidenceGuardrailConfig(similarity_threshold=0.62),
        )
    assert result.status == "pass"
    assert result.supported_ratio == 1.0


def test_guardrail_fails_when_answer_has_unsupported_claims():
    evidence = [EvidenceCandidate(source="bm25", ref="tu-1", text="操作系统管理硬件资源。", score=1, layer=HybridLayer.LOW)]
    with patch(
        "graphrag_pipeline.scripts.hybrid_qa.evidence_guardrail._answer_evidence_similarity",
        return_value=np.asarray([[0.2], [0.7]], dtype=np.float32),
    ):
        result = check_answer_supported_by_evidence(
            "操作系统是应用软件。操作系统管理硬件资源。",
            evidence,
            config=EvidenceGuardrailConfig(similarity_threshold=0.62, min_supported_ratio=0.75),
        )
    assert result.status == "fail"
    assert result.supported_ratio == 0.5
```

- [ ] **Step 4.2: 跑测试确认失败**

Run:

```bash
conda run -n graphrag-oneapi python -m pytest graphrag_pipeline/tests/test_hybrid_qa_v0_evidence_guardrail.py -v
```

Expected: FAIL，ImportError。

- [ ] **Step 4.3: 实现 guardrail**

写到 `graphrag_pipeline/scripts/hybrid_qa/evidence_guardrail.py`：

```python
from __future__ import annotations

from dataclasses import dataclass

import numpy as np

from graphrag_pipeline.scripts.hybrid_qa.types import EvidenceCandidate
from graphrag_pipeline.scripts.qa_eval.semantic_similarity import (
    DEFAULT_BGE_M3_MODEL,
    SemanticScoringConfig,
    _encode_dense_chunks,
    split_semantic_chunks,
)


@dataclass(frozen=True, slots=True)
class EvidenceGuardrailConfig:
    similarity_threshold: float = 0.62
    min_supported_ratio: float = 0.75
    max_chunk_chars: int = 220
    bge_m3_model: str | None = None
    bge_device: str | None = None
    bge_use_fp16: bool = False
    bge_batch_size: int = 8


@dataclass(frozen=True, slots=True)
class EvidenceGuardrailResult:
    status: str
    supported_ratio: float
    unsupported_count: int
    answer_chunk_count: int


def _answer_evidence_similarity(
    answer_chunks: list[str],
    evidence_chunks: list[str],
    config: EvidenceGuardrailConfig,
) -> np.ndarray:
    if not answer_chunks or not evidence_chunks:
        return np.empty((0, 0), dtype=np.float32)
    semantic_config = SemanticScoringConfig(
        bge_m3_model=config.bge_m3_model or DEFAULT_BGE_M3_MODEL,
        bge_device=config.bge_device,
        bge_use_fp16=config.bge_use_fp16,
        bge_batch_size=config.bge_batch_size,
        max_chunk_chars=config.max_chunk_chars,
    )
    model_name = semantic_config.bge_m3_model or DEFAULT_BGE_M3_MODEL
    answer_vectors = _encode_dense_chunks(
        answer_chunks,
        model_name=model_name,
        max_length=semantic_config.bge_max_length,
        device=semantic_config.bge_device,
        use_fp16=semantic_config.bge_use_fp16,
        batch_size=semantic_config.bge_batch_size,
    )
    evidence_vectors = _encode_dense_chunks(
        evidence_chunks,
        model_name=model_name,
        max_length=semantic_config.bge_max_length,
        device=semantic_config.bge_device,
        use_fp16=semantic_config.bge_use_fp16,
        batch_size=semantic_config.bge_batch_size,
    )
    return answer_vectors @ evidence_vectors.T


def check_answer_supported_by_evidence(
    answer: str,
    evidence: list[EvidenceCandidate],
    *,
    config: EvidenceGuardrailConfig | None = None,
) -> EvidenceGuardrailResult:
    config = config or EvidenceGuardrailConfig()
    answer_chunks = split_semantic_chunks(answer, max_chunk_chars=config.max_chunk_chars)
    evidence_chunks: list[str] = []
    for item in evidence:
        evidence_chunks.extend(split_semantic_chunks(item.text, max_chunk_chars=config.max_chunk_chars))
    matrix = _answer_evidence_similarity(answer_chunks, evidence_chunks, config)
    if matrix.size == 0 or not answer_chunks:
        return EvidenceGuardrailResult("fail", 0.0, len(answer_chunks), len(answer_chunks))
    best = matrix.max(axis=1)
    supported = best >= config.similarity_threshold
    supported_ratio = round(float(supported.mean()), 4)
    status = "pass" if supported_ratio >= config.min_supported_ratio else "fail"
    return EvidenceGuardrailResult(
        status=status,
        supported_ratio=supported_ratio,
        unsupported_count=int((~supported).sum()),
        answer_chunk_count=len(answer_chunks),
    )
```

- [ ] **Step 4.4: 跑测试通过**

Run:

```bash
conda run -n graphrag-oneapi python -m pytest graphrag_pipeline/tests/test_hybrid_qa_v0_evidence_guardrail.py -v
```

Expected: 2 PASS。

- [ ] **Step 4.5: 提交**

```bash
git add graphrag_pipeline/scripts/hybrid_qa/evidence_guardrail.py \
        graphrag_pipeline/tests/test_hybrid_qa_v0_evidence_guardrail.py
git commit -m "feat(hybrid_qa): 增加答案证据语义支撑检测"
```

---

## Task 5: 两段证据 Prompt 与 Basic/Local 客户端

**Files:**
- Create: `graphrag_pipeline/prompts/hybrid_v0_system_prompt.txt`
- Create: `graphrag_pipeline/scripts/hybrid_qa/prompt_builder.py`
- Create: `graphrag_pipeline/scripts/hybrid_qa/basic_local_client.py`
- Create: `graphrag_pipeline/tests/test_hybrid_qa_v0_prompt_builder.py`
- Create: `graphrag_pipeline/tests/test_hybrid_qa_v0_basic_local_client.py`

- [ ] **Step 5.1: 写 prompt 模板**

写到 `graphrag_pipeline/prompts/hybrid_v0_system_prompt.txt`：

```text
你是课程问答助手。只能基于 EVIDENCE 回答，不能编造。

回答规则：
1. 先给直接结论，再列依据。
2. LOW-LAYER EVIDENCE 是课程原文片段，优先级最高。
3. HIGH-LAYER EVIDENCE 是课程报告或已有 GraphRAG 草稿，只能作为背景，不得覆盖 LOW-LAYER。
4. 如果证据不足，回答“依据现有资料无法回答”。
5. 回答末尾必须列引用，格式为 [Data: Hybrid(ref1, ref2, ref3)]。

---LOW-LAYER EVIDENCE---
{low_layer_text}

---HIGH-LAYER EVIDENCE---
{high_layer_text}

---QUESTION---
{question}
```

- [ ] **Step 5.2: 写 prompt builder 测试**

写到 `graphrag_pipeline/tests/test_hybrid_qa_v0_prompt_builder.py`：

```python
from __future__ import annotations

from pathlib import Path

from graphrag_pipeline.scripts.hybrid_qa.prompt_builder import build_hybrid_v0_prompt
from graphrag_pipeline.scripts.hybrid_qa.types import EvidenceCandidate, HybridLayer


def test_build_prompt_renders_low_and_high_layers(tmp_path: Path):
    template = tmp_path / "tpl.txt"
    template.write_text("LOW={low_layer_text}\nHIGH={high_layer_text}\nQ={question}", encoding="utf-8")
    low = [EvidenceCandidate(source="bm25", ref="tu-1", text="操作系统是第一层软件。", score=1, layer=HybridLayer.LOW)]
    high = [EvidenceCandidate(source="basic-draft", ref="basic", text="操作系统管理硬件资源。", score=0.5, layer=HybridLayer.HIGH)]

    prompt = build_hybrid_v0_prompt("操作系统是什么？", low, high, template_path=template)

    assert "[bm25:tu-1]" in prompt
    assert "[basic-draft:basic]" in prompt
    assert "Q=操作系统是什么？" in prompt
```

- [ ] **Step 5.3: 写 Basic/Local client 测试**

写到 `graphrag_pipeline/tests/test_hybrid_qa_v0_basic_local_client.py`：

```python
from __future__ import annotations

import pytest

from graphrag_pipeline.scripts.hybrid_qa.basic_local_client import GraphRagDraft


def test_graphrag_draft_as_high_layer_candidate():
    draft = GraphRagDraft(mode="basic", answer="操作系统是第一层软件。", elapsed_seconds=1.2)
    candidate = draft.as_candidate()

    assert candidate.source == "basic-draft"
    assert candidate.ref == "basic"
    assert candidate.text == "操作系统是第一层软件。"
```

- [ ] **Step 5.4: 跑测试确认失败**

Run:

```bash
conda run -n graphrag-oneapi python -m pytest \
  graphrag_pipeline/tests/test_hybrid_qa_v0_prompt_builder.py \
  graphrag_pipeline/tests/test_hybrid_qa_v0_basic_local_client.py -v
```

Expected: FAIL，ImportError。

- [ ] **Step 5.5: 实现 prompt builder 和 client 类型**

写到 `graphrag_pipeline/scripts/hybrid_qa/prompt_builder.py`：

```python
from __future__ import annotations

from pathlib import Path

from graphrag_pipeline.scripts.hybrid_qa.types import EvidenceCandidate


DEFAULT_TEMPLATE = Path("graphrag_pipeline/prompts/hybrid_v0_system_prompt.txt")


def _render(items: list[EvidenceCandidate]) -> str:
    if not items:
        return "（无证据）"
    return "\n\n".join(f"[{item.source}:{item.ref}] score={item.score:.4f}\n{item.text}" for item in items)


def build_hybrid_v0_prompt(
    question: str,
    low_layer: list[EvidenceCandidate],
    high_layer: list[EvidenceCandidate],
    *,
    template_path: Path = DEFAULT_TEMPLATE,
) -> str:
    template = template_path.read_text(encoding="utf-8")
    return (
        template.replace("{low_layer_text}", _render(low_layer))
        .replace("{high_layer_text}", _render(high_layer))
        .replace("{question}", question)
    )
```

写到 `graphrag_pipeline/scripts/hybrid_qa/basic_local_client.py`：

```python
from __future__ import annotations

from dataclasses import dataclass

from graphrag_pipeline.scripts.hybrid_qa.types import EvidenceCandidate, HybridLayer


@dataclass(frozen=True, slots=True)
class GraphRagDraft:
    mode: str
    answer: str
    elapsed_seconds: float
    error: str = ""

    def as_candidate(self) -> EvidenceCandidate:
        return EvidenceCandidate(
            source=f"{self.mode}-draft",
            ref=self.mode,
            text=self.answer,
            score=0.0,
            layer=HybridLayer.HIGH,
            metadata={"elapsed_seconds": self.elapsed_seconds, "error": self.error},
        )
```

后续 Task 6 再把该 client 连接到 `utils.main.run_graphrag_query_cli`，本任务只固定输出形状。

- [ ] **Step 5.6: 跑测试通过**

Run:

```bash
conda run -n graphrag-oneapi python -m pytest \
  graphrag_pipeline/tests/test_hybrid_qa_v0_prompt_builder.py \
  graphrag_pipeline/tests/test_hybrid_qa_v0_basic_local_client.py -v
```

Expected: 2 PASS。

- [ ] **Step 5.7: 提交**

```bash
git add graphrag_pipeline/prompts/hybrid_v0_system_prompt.txt \
        graphrag_pipeline/scripts/hybrid_qa/prompt_builder.py \
        graphrag_pipeline/scripts/hybrid_qa/basic_local_client.py \
        graphrag_pipeline/tests/test_hybrid_qa_v0_prompt_builder.py \
        graphrag_pipeline/tests/test_hybrid_qa_v0_basic_local_client.py
git commit -m "feat(hybrid_qa): 增加 hybrid v0 两段证据提示词"
```

---

## Task 6: Hybrid v0 Orchestrator

**Files:**
- Create: `graphrag_pipeline/scripts/hybrid_qa/orchestrator_v0.py`
- Create: `graphrag_pipeline/tests/test_hybrid_qa_v0_orchestrator.py`

- [ ] **Step 6.1: 写 orchestrator 测试**

写到 `graphrag_pipeline/tests/test_hybrid_qa_v0_orchestrator.py`：

```python
from __future__ import annotations

from unittest.mock import MagicMock

from graphrag_pipeline.scripts.hybrid_qa.basic_local_client import GraphRagDraft
from graphrag_pipeline.scripts.hybrid_qa.orchestrator_v0 import HybridV0Orchestrator
from graphrag_pipeline.scripts.hybrid_qa.types import EvidenceCandidate, HybridLayer


def test_orchestrator_uses_basic_without_local_when_guardrail_passes():
    bm25 = MagicMock()
    bm25.search.return_value = [EvidenceCandidate("bm25", "tu-1", "操作系统是第一层软件。", 1, HybridLayer.LOW)]
    client = MagicMock()
    client.query_basic.return_value = GraphRagDraft("basic", "操作系统是第一层软件。[Data: Text Units (tu-1)]", 1.0)
    guardrail = MagicMock()
    guardrail.return_value.status = "pass"
    guardrail.return_value.supported_ratio = 1.0
    llm = MagicMock(return_value="最终答案 [Data: Hybrid(tu-1)]")

    result = HybridV0Orchestrator(bm25=bm25, graph_client=client, guardrail=guardrail, llm_complete=llm).answer("操作系统是什么？")

    assert result.answer.startswith("最终答案")
    assert result.diagnostics.used_local_fallback is False
    client.query_local.assert_not_called()


def test_orchestrator_uses_local_fallback_when_guardrail_fails():
    bm25 = MagicMock()
    bm25.search.return_value = [EvidenceCandidate("bm25", "tu-1", "操作系统管理硬件。", 1, HybridLayer.LOW)]
    client = MagicMock()
    client.query_basic.return_value = GraphRagDraft("basic", "操作系统是应用软件。", 1.0)
    client.query_local.return_value = GraphRagDraft("local", "操作系统管理硬件资源。", 2.0)
    guardrail = MagicMock()
    first = MagicMock(status="fail", supported_ratio=0.2)
    second = MagicMock(status="pass", supported_ratio=0.9)
    guardrail.side_effect = [first, second]
    llm = MagicMock(return_value="最终答案 [Data: Hybrid(tu-1)]")

    result = HybridV0Orchestrator(bm25=bm25, graph_client=client, guardrail=guardrail, llm_complete=llm).answer("操作系统是什么？")

    assert result.diagnostics.used_local_fallback is True
    client.query_local.assert_called_once()
```

- [ ] **Step 6.2: 跑测试确认失败**

Run:

```bash
conda run -n graphrag-oneapi python -m pytest graphrag_pipeline/tests/test_hybrid_qa_v0_orchestrator.py -v
```

Expected: FAIL，ImportError。

- [ ] **Step 6.3: 实现 orchestrator**

写到 `graphrag_pipeline/scripts/hybrid_qa/orchestrator_v0.py`：

```python
from __future__ import annotations

import time
from dataclasses import replace
from typing import Callable, Protocol

from graphrag_pipeline.scripts.hybrid_qa.basic_local_client import GraphRagDraft
from graphrag_pipeline.scripts.hybrid_qa.evidence_guardrail import EvidenceGuardrailResult
from graphrag_pipeline.scripts.hybrid_qa.prompt_builder import build_hybrid_v0_prompt
from graphrag_pipeline.scripts.hybrid_qa.question_classifier import classify_question
from graphrag_pipeline.scripts.hybrid_qa.types import EvidenceCandidate, HybridDiagnostics, HybridV0Answer


class Bm25Protocol(Protocol):
    def search(self, query: str, *, top_k: int = 10) -> list[EvidenceCandidate]: ...


class GraphClientProtocol(Protocol):
    def query_basic(self, question: str) -> GraphRagDraft: ...
    def query_local(self, question: str) -> GraphRagDraft: ...


GuardrailFn = Callable[[str, list[EvidenceCandidate]], EvidenceGuardrailResult]
LlmFn = Callable[[str], str]


class HybridV0Orchestrator:
    def __init__(
        self,
        *,
        bm25: Bm25Protocol,
        graph_client: GraphClientProtocol,
        guardrail: GuardrailFn,
        llm_complete: LlmFn,
        bm25_top_k: int = 8,
    ) -> None:
        self.bm25 = bm25
        self.graph_client = graph_client
        self.guardrail = guardrail
        self.llm_complete = llm_complete
        self.bm25_top_k = bm25_top_k

    def answer(self, question: str) -> HybridV0Answer:
        started = time.monotonic()
        classification = classify_question(question)
        low = self.bm25.search(question, top_k=self.bm25_top_k)
        basic = self.graph_client.query_basic(question)
        high = [basic.as_candidate()] if basic.answer else []
        prompt = build_hybrid_v0_prompt(question, low, high)
        answer = self.llm_complete(prompt)
        guardrail_result = self.guardrail(answer, low + high)
        used_local = False

        if guardrail_result.status == "fail":
            local = self.graph_client.query_local(question)
            if local.answer:
                used_local = True
                high.append(local.as_candidate())
                answer = self.llm_complete(build_hybrid_v0_prompt(question, low, high))
                guardrail_result = self.guardrail(answer, low + high)

        diagnostics = HybridDiagnostics(
            layer=classification.layer,
            classifier_confidence=classification.confidence,
            used_local_fallback=used_local,
            guardrail_status=guardrail_result.status,
            guardrail_score=guardrail_result.supported_ratio,
            low_evidence_count=len(low),
            high_evidence_count=len(high),
            elapsed_seconds=round(time.monotonic() - started, 4),
        )
        return HybridV0Answer(answer=answer, diagnostics=diagnostics)
```

- [ ] **Step 6.4: 跑测试通过**

Run:

```bash
conda run -n graphrag-oneapi python -m pytest graphrag_pipeline/tests/test_hybrid_qa_v0_orchestrator.py -v
```

Expected: 2 PASS。

- [ ] **Step 6.5: 提交**

```bash
git add graphrag_pipeline/scripts/hybrid_qa/orchestrator_v0.py \
        graphrag_pipeline/tests/test_hybrid_qa_v0_orchestrator.py
git commit -m "feat(hybrid_qa): 增加 hybrid v0 编排器"
```

---

## Task 7: API 模式注册与 Lazy Singleton

**Files:**
- Modify: `graphrag_pipeline/utils/main.py`
- Create: `graphrag_pipeline/tests/test_hybrid_qa_v0_main_route.py`

- [ ] **Step 7.1: 写 main route 测试**

写到 `graphrag_pipeline/tests/test_hybrid_qa_v0_main_route.py`：

```python
from __future__ import annotations

import asyncio
from unittest.mock import AsyncMock, patch

from graphrag_pipeline.utils.main import SUPPORTED_QUERY_MODELS, _resolve_query_response


def test_hybrid_v0_model_id_registered():
    assert SUPPORTED_QUERY_MODELS["graphrag-hybrid-v0-search:latest"] == "hybrid_v0"


def test_hybrid_v0_path_skips_native_cli_modes():
    with patch("graphrag_pipeline.utils.main._run_hybrid_v0_query", new=AsyncMock(return_value="hybrid-v0-answer")) as hybrid, \
         patch("graphrag_pipeline.utils.main.run_graphrag_query_cli", new=AsyncMock(return_value="basic-answer")) as cli:
        answer = asyncio.run(_resolve_query_response("graphrag-hybrid-v0-search:latest", "操作系统是什么？"))

    assert "hybrid-v0-answer" in answer
    hybrid.assert_awaited_once()
    cli.assert_not_awaited()
```

- [ ] **Step 7.2: 跑测试确认失败**

Run:

```bash
conda run -n graphrag-oneapi python -m pytest graphrag_pipeline/tests/test_hybrid_qa_v0_main_route.py -v
```

Expected: FAIL，model id 未注册。

- [ ] **Step 7.3: 修改 `utils/main.py`**

只做最小接入：

```python
SUPPORTED_QUERY_MODELS: dict[str, str] = {
    "graphrag-local-search:latest": "local",
    "graphrag-global-search:latest": "global",
    "graphrag-drift-search:latest": "drift",
    "graphrag-basic-search:latest": "basic",
    "graphrag-hybrid-v0-search:latest": "hybrid_v0",
}
```

在 `_resolve_query_response` 里：

```python
if method == "hybrid_v0":
    raw = await _run_hybrid_v0_query(prompt)
else:
    raw = await run_graphrag_query_cli(method, prompt)
```

新增 `_run_hybrid_v0_query(prompt: str) -> str`，先用 lazy singleton 构建 orchestrator。构建时必须从 `APP_CONFIG.output_dir` 找 `text_units.parquet`，不要硬编码 `graphrag_pipeline/output/text_units.parquet`。

- [ ] **Step 7.4: 跑测试通过**

Run:

```bash
conda run -n graphrag-oneapi python -m pytest graphrag_pipeline/tests/test_hybrid_qa_v0_main_route.py -v
```

Expected: 2 PASS。

- [ ] **Step 7.5: 提交**

```bash
git add graphrag_pipeline/utils/main.py \
        graphrag_pipeline/tests/test_hybrid_qa_v0_main_route.py
git commit -m "feat(hybrid_qa): 注册 hybrid v0 查询模式"
```

---

## Task 8: Hybrid v0 评测与算法对比

**Files:**
- Create: `graphrag_pipeline/scripts/qa_eval/run_hybrid_v0_eval.py`
- Create: `graphrag_pipeline/scripts/qa_eval/compare_algorithmic_runs.py`
- Create: `graphrag_pipeline/tests/test_qa_eval_hybrid_v0_compare.py`

- [ ] **Step 8.1: 写 compare 测试**

写到 `graphrag_pipeline/tests/test_qa_eval_hybrid_v0_compare.py`：

```python
from __future__ import annotations

from pathlib import Path

import pandas as pd

from graphrag_pipeline.scripts.qa_eval.compare_algorithmic_runs import compare_algorithmic_runs


def test_compare_algorithmic_runs_writes_markdown(tmp_path: Path):
    baseline = tmp_path / "baseline"
    hybrid = tmp_path / "hybrid"
    baseline.mkdir()
    hybrid.mkdir()
    pd.DataFrame(
        [
            {"question_id": "Q001", "category": "factual_lookup", "mode": "basic", "effective_score_experimental": 0.8, "semantic_coverage_f1": 0.9, "citation_recall_at_3": 0.5, "elapsed_seconds": 40, "error_count": 0},
        ]
    ).to_csv(baseline / "algorithmic_scoring.csv", index=False)
    pd.DataFrame(
        [
            {"question_id": "Q001", "category": "factual_lookup", "mode": "hybrid_v0", "effective_score_experimental": 0.82, "semantic_coverage_f1": 0.92, "citation_recall_at_3": 0.6, "elapsed_seconds": 55, "error_count": 0},
        ]
    ).to_csv(hybrid / "algorithmic_scoring.csv", index=False)

    output = compare_algorithmic_runs(baseline, hybrid, output_path=tmp_path / "comparison.md")

    text = output.read_text(encoding="utf-8")
    assert "hybrid_v0" in text
    assert "effective_score_experimental" in text
```

- [ ] **Step 8.2: 跑测试确认失败**

Run:

```bash
conda run -n graphrag-oneapi python -m pytest graphrag_pipeline/tests/test_qa_eval_hybrid_v0_compare.py -v
```

Expected: FAIL，ImportError。

- [ ] **Step 8.3: 实现 compare**

写到 `graphrag_pipeline/scripts/qa_eval/compare_algorithmic_runs.py`：

```python
from __future__ import annotations

import argparse
from pathlib import Path

import pandas as pd


METRICS = [
    "effective_score_experimental",
    "semantic_coverage_f1",
    "citation_recall_at_3",
    "citation_rr",
    "elapsed_seconds",
    "error_count",
]


def _summarize(path: Path) -> pd.DataFrame:
    frame = pd.read_csv(path / "algorithmic_scoring.csv")
    existing = [metric for metric in METRICS if metric in frame.columns]
    return frame.groupby("mode", as_index=False)[existing].mean(numeric_only=True)


def compare_algorithmic_runs(baseline_dir: Path, hybrid_dir: Path, *, output_path: Path) -> Path:
    baseline = _summarize(baseline_dir)
    hybrid = _summarize(hybrid_dir)
    merged = pd.concat([baseline, hybrid], ignore_index=True)
    lines = [
        "# Hybrid v0 Algorithmic Comparison",
        "",
        f"- baseline: `{baseline_dir}`",
        f"- hybrid: `{hybrid_dir}`",
        "",
        merged.to_markdown(index=False),
        "",
    ]
    output_path.write_text("\n".join(lines), encoding="utf-8")
    return output_path


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--baseline-dir", type=Path, required=True)
    parser.add_argument("--hybrid-dir", type=Path, required=True)
    parser.add_argument("--output", type=Path, required=True)
    args = parser.parse_args()
    print(compare_algorithmic_runs(args.baseline_dir, args.hybrid_dir, output_path=args.output))
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
```

- [ ] **Step 8.4: 实现 hybrid eval runner**

`run_hybrid_v0_eval.py` 应复用现有 baseline runner / OpenAI-compatible client，只把 mode 固定为 `graphrag-hybrid-v0-search:latest`，并允许 arbitrary model id。若当前 baseline client 没有 `allow_arbitrary_models`，先给 client 增加该参数并补测试。

- [ ] **Step 8.5: 跑测试通过**

Run:

```bash
conda run -n graphrag-oneapi python -m pytest graphrag_pipeline/tests/test_qa_eval_hybrid_v0_compare.py -v
```

Expected: 1 PASS。

- [ ] **Step 8.6: 真实评测命令**

```bash
HYBRID_ID=hybrid-v0-$(date +%Y%m%d-%H%M%S)
BASELINE_DIR=graphrag_pipeline/results/qa_eval/runs/baseline-lgb-canonical-20260513-1835

conda run -n graphrag-oneapi python -m graphrag_pipeline.scripts.qa_eval.run_hybrid_v0_eval \
  --run-id "$HYBRID_ID" \
  --base-url http://127.0.0.1:8000 \
  --test-set graphrag_pipeline/data/eval/qa_test_set.jsonl

conda run -n graphrag-oneapi python -m graphrag_pipeline.scripts.qa_eval.algorithmic_scorer \
  --run-dir "graphrag_pipeline/results/qa_eval/runs/$HYBRID_ID" \
  --bge-device cuda \
  --bge-fp16 \
  --bge-batch-size 32

conda run -n graphrag-oneapi python -m graphrag_pipeline.scripts.qa_eval.significance_reporter \
  --run-dir "graphrag_pipeline/results/qa_eval/runs/$HYBRID_ID"

conda run -n graphrag-oneapi python -m graphrag_pipeline.scripts.qa_eval.compare_algorithmic_runs \
  --baseline-dir "$BASELINE_DIR" \
  --hybrid-dir "graphrag_pipeline/results/qa_eval/runs/$HYBRID_ID" \
  --output "graphrag_pipeline/results/qa_eval/runs/$HYBRID_ID/hybrid_v0_comparison.md"
```

- [ ] **Step 8.7: 提交**

```bash
git add graphrag_pipeline/scripts/qa_eval/run_hybrid_v0_eval.py \
        graphrag_pipeline/scripts/qa_eval/compare_algorithmic_runs.py \
        graphrag_pipeline/tests/test_qa_eval_hybrid_v0_compare.py
git commit -m "feat(qa_eval): 增加 hybrid v0 评测与算法对比"
```

---

## Task 9: 文档与回滚策略

**Files:**
- Modify: `graphrag_pipeline/README.md`
- Modify: `graphrag_pipeline/scripts/README.md`
- Modify: `docs/superpowers/plans/2026-05-15-hybrid-qa-v0-verifiable-plan.md`

- [ ] **Step 9.1: 更新 README**

在 `graphrag_pipeline/README.md` 的 QA 评测或 API 模式段增加：

```markdown
### Hybrid v0 实验模式

`graphrag-hybrid-v0-search:latest` 是实验模式，不会替代 basic/local/global/drift。它默认使用 BM25 + Basic，只有答案-证据语义检测失败时才触发 Local fallback；Global 和 DRIFT 不进入默认链路。

安装依赖：

```bash
pip install -e ".[all,hybrid]"
```

评测：

```bash
python -m graphrag_pipeline.scripts.qa_eval.run_hybrid_v0_eval --run-id <run_id>
python -m graphrag_pipeline.scripts.qa_eval.algorithmic_scorer --run-dir graphrag_pipeline/results/qa_eval/runs/<run_id>
```
```

- [ ] **Step 9.2: 更新脚本文档**

在 `graphrag_pipeline/scripts/README.md` 增加 hybrid_qa 小节：

```markdown
| 脚本 | 用途 |
| --- | --- |
| `hybrid_qa/bm25_text_units.py` | 从 text_units 构建低层原文 BM25 召回 |
| `hybrid_qa/evidence_guardrail.py` | 用 BGE-M3 检查答案是否被证据支撑 |
| `hybrid_qa/orchestrator_v0.py` | 串接 BM25、Basic、Local fallback 与两段证据 prompt |
| `qa_eval/run_hybrid_v0_eval.py` | 跑 hybrid-v0 评测 |
| `qa_eval/compare_algorithmic_runs.py` | 对比 baseline 与 hybrid-v0 的算法指标 |
```

- [ ] **Step 9.3: 最终验证**

Run:

```bash
conda run -n graphrag-oneapi python -m pytest \
  graphrag_pipeline/tests/test_hybrid_qa_v0_*.py \
  graphrag_pipeline/tests/test_qa_eval_hybrid_v0_*.py -v

git diff --check
```

Expected: 全部 PASS，`git diff --check` 无输出。

- [ ] **Step 9.4: 提交**

```bash
git add graphrag_pipeline/README.md graphrag_pipeline/scripts/README.md \
        docs/superpowers/plans/2026-05-15-hybrid-qa-v0-verifiable-plan.md
git commit -m "docs(hybrid_qa): 补充 hybrid v0 可验证实施计划"
```

---

## 与原 Hybrid 计划的差异

| 维度 | 原计划 | Hybrid v0 |
| --- | --- | --- |
| 默认路径 | low/basic+BM25，high/global，mixed/local+drift+global | BM25 + Basic 默认，Local 仅 fallback |
| Global | high 默认调用 | 默认关闭，只读取本地 community report 片段时才作为高层证据候选 |
| DRIFT | mixed 默认调用 | 默认关闭，等超时问题修复后再纳入 |
| Reranker | BGE-reranker-v2-m3 默认二阶段精排 | v0 不默认引入 cross-encoder，先验证 BM25/Basic/Local |
| 语义检测 | 未区分在线/离线 | 离线用 gold-based BGE-M3；在线用 answer-vs-evidence guardrail |
| 评测 | entity_hit_rate + semantic_correctness | 复用 algorithmic_scorer：semantic coverage、citation IR、latency/error、bootstrap |
| 风险 | 多路召回慢、重模型显存压力、Global/DRIFT 不稳定 | 能先快速验证是否比 basic/local 值得继续 |

---

## 后续升级门槛

只有当 hybrid-v0 满足完成判据并在人工抽样里没有明显幻觉时，才进入下一阶段：

1. 加入 BGE-reranker-v2-m3，但只对去重后 top 20 候选精排。
2. 重新评测 Global 的稳定参数；Global 平均延迟降到 120s 内且 error 为 0 后，才允许 high layer 触发 Global。
3. DRIFT 单独修复超时；至少 30 条成功样本后，才允许 mixed layer 触发 DRIFT。
4. 把在线 guardrail 的 unsupported chunks 写入 `qa_retrieval_logs` 或 run diagnostics，供后续路由分类器训练。
