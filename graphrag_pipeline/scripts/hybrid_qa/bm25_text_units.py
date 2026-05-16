from __future__ import annotations

import json
import math
import pickle
import unicodedata
from collections import Counter
from dataclasses import dataclass, field
from pathlib import Path
from typing import Protocol

import jieba
import pandas as pd

from graphrag_pipeline.scripts.hybrid_qa.types import EvidenceCandidate, HybridLayer

try:
    from rank_bm25 import BM25Okapi
except ImportError:  # pragma: no cover - 覆盖取决于本地 optional extra 是否安装
    BM25Okapi = None


class _Bm25Index(Protocol):
    def get_scores(self, query_tokens: list[str]) -> list[float]:
        ...


@dataclass(slots=True)
class _SimpleBm25Okapi:
    corpus: list[list[str]]
    k1: float = 1.5
    b: float = 0.75
    _doc_freqs: list[Counter[str]] = field(init=False, repr=False)
    _doc_lens: list[int] = field(init=False, repr=False)
    _avgdl: float = field(init=False, repr=False)
    _idf: dict[str, float] = field(init=False, repr=False)

    def __post_init__(self) -> None:
        self._doc_freqs = [Counter(tokens) for tokens in self.corpus]
        self._doc_lens = [len(tokens) for tokens in self.corpus]
        self._avgdl = sum(self._doc_lens) / len(self._doc_lens) if self._doc_lens else 0.0
        doc_counts = Counter(token for tokens in self.corpus for token in set(tokens))
        doc_count = len(self.corpus)
        self._idf = {
            token: math.log(1 + (doc_count - freq + 0.5) / (freq + 0.5))
            for token, freq in doc_counts.items()
        }

    def get_scores(self, query_tokens: list[str]) -> list[float]:
        if not self.corpus or not query_tokens:
            return [0.0 for _ in self.corpus]

        scores: list[float] = []
        for freqs, doc_len in zip(self._doc_freqs, self._doc_lens, strict=True):
            score = 0.0
            for token in query_tokens:
                tf = freqs.get(token, 0)
                if tf == 0:
                    continue
                denom = tf + self.k1 * (1 - self.b + self.b * doc_len / self._avgdl)
                score += self._idf.get(token, 0.0) * tf * (self.k1 + 1) / denom
            scores.append(score)
        return scores


@dataclass(slots=True)
class TextUnitBm25:
    refs: list[str]
    texts: list[str]
    tokens: list[list[str]]
    bm25: _Bm25Index

    @property
    def size(self) -> int:
        return len(self.refs)

    def search(self, query: str, top_k: int = 10) -> list[EvidenceCandidate]:
        query_tokens = _tokenize(_expand_query(query))
        if top_k <= 0 or not query_tokens:
            return []

        scores = self.bm25.get_scores(query_tokens)
        ranked = sorted(enumerate(scores), key=lambda item: item[1], reverse=True)

        candidates: list[EvidenceCandidate] = []
        for index, score in ranked[:top_k]:
            if float(score) <= 0:
                continue
            candidates.append(
                EvidenceCandidate(
                    source="bm25",
                    ref=self.refs[index],
                    text=self.texts[index],
                    score=float(score),
                    layer=HybridLayer.LOW,
                    metadata={"rank": len(candidates) + 1},
                )
            )
        return candidates


def build_text_unit_bm25(parquet_path: Path, *, cache_dir: Path) -> TextUnitBm25:
    resolved_path = parquet_path.resolve()
    cache_dir.mkdir(parents=True, exist_ok=True)
    cache_path = cache_dir / "text_unit_bm25.pkl"
    meta_path = cache_dir / "text_unit_bm25.meta.json"
    meta = _build_meta(resolved_path)

    cached = _load_cache(cache_path, meta_path, meta)
    if cached is not None:
        return cached

    frame = pd.read_parquet(resolved_path, columns=["id", "text"])
    refs = [str(value) for value in frame["id"].tolist()]
    texts = ["" if pd.isna(value) else str(value) for value in frame["text"].tolist()]
    tokens = [_tokenize(text) for text in texts]
    bm25 = BM25Okapi(tokens) if BM25Okapi is not None else _SimpleBm25Okapi(tokens)
    index = TextUnitBm25(refs=refs, texts=texts, tokens=tokens, bm25=bm25)

    with cache_path.open("wb") as file:
        pickle.dump(index, file)
    meta_path.write_text(json.dumps(meta, ensure_ascii=False, indent=2), encoding="utf-8")
    return index


def _load_cache(cache_path: Path, meta_path: Path, meta: dict[str, object]) -> TextUnitBm25 | None:
    if not cache_path.exists() or not meta_path.exists():
        return None
    try:
        cached_meta = json.loads(meta_path.read_text(encoding="utf-8"))
        if cached_meta != meta:
            return None
        with cache_path.open("rb") as file:
            cached = pickle.load(file)
    except (OSError, json.JSONDecodeError, pickle.PickleError, AttributeError):
        return None
    if not isinstance(cached, TextUnitBm25):
        return None
    return cached


def _build_meta(parquet_path: Path) -> dict[str, object]:
    stat = parquet_path.stat()
    return {
        "parquet_path": str(parquet_path),
        "mtime_ns": stat.st_mtime_ns,
    }


def _tokenize(text: str) -> list[str]:
    tokens: list[str] = []
    for token in jieba.cut_for_search(text):
        normalized = token.strip().casefold()
        if not normalized or _is_punctuation(normalized):
            continue
        tokens.append(normalized)
    return tokens


_QUERY_EXPANSIONS = (
    (
        ("文件系统", "文件管理", "文件"),
        "文件目录 文件控制块 索引结点 文件分配 文件存储空间 目录结构 "
        "文件目录 文件控制块 索引结点 文件分配 文件存储空间 目录结构",
    ),
    (
        ("i/o", "io", "输入输出", "设备管理"),
        "设备管理 设备分配 缓冲 设备处理 I/O系统 输入输出系统",
    ),
    (
        ("磁盘调度", "磁盘", "调度"),
        "寻道时间 旋转延迟 传输时间 磁盘调度算法 磁盘I/O速度",
    ),
)


def _expand_query(query: str) -> str:
    normalized = (query or "").casefold()
    additions: list[str] = []
    for triggers, expansion in _QUERY_EXPANSIONS:
        if any(trigger in normalized for trigger in triggers):
            additions.append(expansion)
    if not additions:
        return query
    return f"{query}\n{' '.join(additions)}"


def _is_punctuation(token: str) -> bool:
    return all(unicodedata.category(char).startswith(("P", "S")) for char in token)
