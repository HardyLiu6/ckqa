from __future__ import annotations

import json
import math
import pickle
import unicodedata
from collections import Counter
from dataclasses import dataclass, field
from functools import lru_cache
from hashlib import sha256
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


@dataclass(frozen=True, slots=True)
class TextUnitBm25Config:
    user_terms: tuple[str, ...] = ()
    k1: float = 1.5
    b: float = 0.75
    enable_query_expansion: bool = True
    exclude_exercises: bool = False

    def normalized(self) -> "TextUnitBm25Config":
        return TextUnitBm25Config(
            user_terms=tuple(_normalize_terms(self.user_terms)),
            k1=float(self.k1),
            b=float(self.b),
            enable_query_expansion=bool(self.enable_query_expansion),
            exclude_exercises=bool(self.exclude_exercises),
        )


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
    config: TextUnitBm25Config = field(default_factory=TextUnitBm25Config)

    @property
    def size(self) -> int:
        return len(self.refs)

    def search(self, query: str, top_k: int = 10) -> list[EvidenceCandidate]:
        searchable_query = self.expanded_query_for_debug(query)
        query_tokens = _tokenize(searchable_query, user_terms=self.config.user_terms)
        if top_k <= 0 or not query_tokens:
            return []

        scores = self.bm25.get_scores(query_tokens)
        ranked = sorted(enumerate(scores), key=lambda item: item[1], reverse=True)

        candidates: list[EvidenceCandidate] = []
        for index, score in ranked:
            if self.config.exclude_exercises and _is_exercise_text(self.texts[index]):
                continue
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
            if len(candidates) >= top_k:
                break
        return candidates

    def expanded_query_for_debug(self, query: str) -> str:
        return _expand_query(query) if self.config.enable_query_expansion else query

    def tokenize_for_debug(self, text: str) -> list[str]:
        return _tokenize(text, user_terms=self.config.user_terms)

    def global_tokenize_for_debug(self, text: str) -> list[str]:
        return _tokenize(text)


def build_text_unit_bm25(
    parquet_path: Path,
    *,
    cache_dir: Path,
    config: TextUnitBm25Config | None = None,
) -> TextUnitBm25:
    config = (config or TextUnitBm25Config()).normalized()
    resolved_path = parquet_path.resolve()
    cache_dir.mkdir(parents=True, exist_ok=True)
    cache_path = cache_dir / "text_unit_bm25.pkl"
    meta_path = cache_dir / "text_unit_bm25.meta.json"
    meta = _build_meta(resolved_path, config)

    cached = _load_cache(cache_path, meta_path, meta)
    if cached is not None:
        return cached

    frame = pd.read_parquet(resolved_path, columns=["id", "text"])
    refs = [str(value) for value in frame["id"].tolist()]
    texts = ["" if pd.isna(value) else str(value) for value in frame["text"].tolist()]
    index = build_text_unit_bm25_from_texts(refs=refs, texts=texts, config=config)

    with cache_path.open("wb") as file:
        pickle.dump(index, file)
    meta_path.write_text(json.dumps(meta, ensure_ascii=False, indent=2), encoding="utf-8")
    return index


def build_text_unit_bm25_from_texts(
    *,
    refs: list[str],
    texts: list[str],
    config: TextUnitBm25Config | None = None,
) -> TextUnitBm25:
    config = (config or TextUnitBm25Config()).normalized()
    tokenizer = _make_tokenizer(config.user_terms)
    tokens = [_tokenize(text, tokenizer=tokenizer) for text in texts]
    return build_text_unit_bm25_from_tokens(refs=refs, texts=texts, tokens=tokens, config=config)


def build_text_unit_bm25_from_tokens(
    *,
    refs: list[str],
    texts: list[str],
    tokens: list[list[str]],
    config: TextUnitBm25Config | None = None,
) -> TextUnitBm25:
    config = (config or TextUnitBm25Config()).normalized()
    bm25 = (
        BM25Okapi(tokens, k1=config.k1, b=config.b)
        if BM25Okapi is not None
        else _SimpleBm25Okapi(tokens, k1=config.k1, b=config.b)
    )
    return TextUnitBm25(refs=refs, texts=texts, tokens=tokens, bm25=bm25, config=config)


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


def _build_meta(parquet_path: Path, config: TextUnitBm25Config) -> dict[str, object]:
    stat = parquet_path.stat()
    return {
        "parquet_path": str(parquet_path),
        "mtime_ns": stat.st_mtime_ns,
        "k1": config.k1,
        "b": config.b,
        "enable_query_expansion": config.enable_query_expansion,
        "exclude_exercises": config.exclude_exercises,
        "user_terms_count": len(config.user_terms),
        "user_terms_sha256": _terms_digest(config.user_terms),
    }


def _tokenize(
    text: str,
    *,
    user_terms: tuple[str, ...] = (),
    tokenizer: jieba.Tokenizer | None = None,
) -> list[str]:
    active_tokenizer = tokenizer or _make_tokenizer(user_terms)
    tokens: list[str] = []
    for token in active_tokenizer.cut_for_search(text):
        normalized = token.strip().casefold()
        if not normalized or _is_punctuation(normalized):
            continue
        tokens.append(normalized)
    return tokens


def _expand_query(query: str) -> str:
    normalized = (query or "").casefold()
    additions: list[str] = []
    if any(trigger in normalized for trigger in ("文件系统", "文件管理", "文件")):
        additions.append(
            "文件目录 文件控制块 索引结点 文件分配 文件存储空间 目录结构 "
            "文件目录 文件控制块 索引结点 文件分配 文件存储空间 目录结构"
        )
    if any(trigger in normalized for trigger in ("i/o", "io", "输入输出", "设备管理")):
        additions.append("设备管理 设备分配 缓冲 设备处理 I/O系统 输入输出系统")
    if "磁盘调度" in normalized or ("磁盘" in normalized and "调度" in normalized):
        additions.append("寻道时间 旋转延迟 传输时间 磁盘调度算法 磁盘I/O速度")
    if (
        "处理机管理" in normalized
        or "进程调度" in normalized
        or "作业调度" in normalized
        or ("处理机" in normalized and "调度" in normalized)
        or ("进程" in normalized and "线程" in normalized and "调度" in normalized)
    ):
        additions.append("处理机调度 作业调度 进程调度 线程调度 进程 线程 处理机")
    if not additions:
        return query
    return f"{query}\n{' '.join(additions)}"


def _is_punctuation(token: str) -> bool:
    return all(unicodedata.category(char).startswith(("P", "S")) for char in token)


def _is_exercise_text(text: str) -> bool:
    normalized = " ".join((text or "").casefold().split())
    return any(
        marker in normalized
        for marker in (
            "section: 习题",
            "section：习题",
            "section:习题",
            "section： 习题",
        )
    )


def _make_tokenizer(user_terms: tuple[str, ...] = ()) -> jieba.Tokenizer:
    return _make_tokenizer_cached(tuple(user_terms))


@lru_cache(maxsize=16)
def _make_tokenizer_cached(user_terms: tuple[str, ...] = ()) -> jieba.Tokenizer:
    if not user_terms:
        return jieba.dt
    tokenizer = jieba.Tokenizer()
    for term in user_terms:
        tokenizer.add_word(term, freq=2_000_000)
    return tokenizer


def _normalize_terms(terms: tuple[str, ...] | list[str]) -> list[str]:
    normalized: list[str] = []
    for raw in terms:
        term = str(raw or "").strip()
        if term and term not in normalized:
            normalized.append(term)
    return normalized


def _terms_digest(terms: tuple[str, ...]) -> str:
    payload = "\n".join(terms).encode("utf-8")
    return sha256(payload).hexdigest()
