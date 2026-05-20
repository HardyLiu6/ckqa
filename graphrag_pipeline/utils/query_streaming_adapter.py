from __future__ import annotations

import os
import re
from collections.abc import AsyncGenerator, Callable
from dataclasses import dataclass, field
from pathlib import Path
from typing import Any

import pandas as pd

from graphrag_pipeline.scripts.hybrid_qa.prompt_builder import build_hybrid_v0_basic_injection_prompt
from graphrag_pipeline.scripts.hybrid_qa.types import EvidenceCandidate
from query_task_manager import QueryTaskRequest


_DATA_BLOCK_RE = re.compile(r"\[Data:\s*[^\]]*\]", re.IGNORECASE)


@dataclass(frozen=True, slots=True)
class NativeStreamingConfig:
    enabled_modes: set[str] = field(default_factory=lambda: {"hybrid_v0", "basic"})
    response_type: str = "Multiple Paragraphs"
    hybrid_evidence_chars: int = 6000

    @classmethod
    def from_env(cls) -> "NativeStreamingConfig":
        raw_modes = os.getenv("CKQA_GRAPHRAG_NATIVE_STREAMING_MODES") or "hybrid_v0,basic"
        modes = {mode.strip() for mode in raw_modes.split(",") if mode.strip()}
        return cls(
            enabled_modes=modes or {"hybrid_v0", "basic"},
            response_type=os.getenv("CKQA_GRAPHRAG_NATIVE_STREAMING_RESPONSE_TYPE") or "Multiple Paragraphs",
            hybrid_evidence_chars=_parse_positive_int(
                os.getenv("CKQA_HYBRID_V0_BASIC_INJECTION_EVIDENCE_CHARS"),
                default=6000,
            ),
        )


@dataclass(frozen=True, slots=True)
class NativeStreamingChunk:
    text: str
    sources: list[dict[str, Any]] = field(default_factory=list)


class NativeGraphRagStreamingAdapter:
    """GraphRAG Python Query API streaming 适配层。

    首版默认用于 `basic` 与 `hybrid_v0`。`local/global/drift` 的调用入口
    保持可用，但由 `CKQA_GRAPHRAG_NATIVE_STREAMING_MODES` 控制灰度开启。
    """

    def __init__(
        self,
        *,
        root_dir: Path,
        config: NativeStreamingConfig | None = None,
        hybrid_orchestrator_factory: Callable[[Path | None], Any] | None = None,
        search_functions: dict[str, Callable[..., AsyncGenerator[str, None]]] | None = None,
    ) -> None:
        self.root_dir = root_dir
        self.config = config or NativeStreamingConfig.from_env()
        self._hybrid_orchestrator_factory = hybrid_orchestrator_factory
        self._search_functions = search_functions

    def supports(self, request: QueryTaskRequest) -> bool:
        return request.mode in self.config.enabled_modes and request.mode in {
            "hybrid_v0",
            "basic",
            "local",
            "global",
            "drift",
        }

    async def stream(self, request: QueryTaskRequest) -> AsyncGenerator[NativeStreamingChunk, None]:
        if not self.supports(request):
            raise ValueError(f"native streaming does not support mode={request.mode}")
        data_dir = request.data_dir
        if data_dir is None:
            raise ValueError("native streaming requires dataDirUri")

        query = request.retrieval_query or request.prompt
        hybrid_sources: list[dict[str, Any]] = []
        effective_mode = request.mode
        if request.mode == "hybrid_v0":
            query, hybrid_sources = self._build_hybrid_basic_query(request, data_dir)
            effective_mode = "basic"

        async for text in self._stream_graphrag(effective_mode, data_dir, query):
            if text:
                yield NativeStreamingChunk(text=str(text), sources=hybrid_sources)
        if hybrid_sources:
            yield NativeStreamingChunk(text="", sources=hybrid_sources)

    def _build_hybrid_basic_query(self, request: QueryTaskRequest, data_dir: Path) -> tuple[str, list[dict[str, Any]]]:
        if self._hybrid_orchestrator_factory is None:
            raise ValueError("hybrid orchestrator factory is not configured")
        orchestrator = self._hybrid_orchestrator_factory(data_dir)
        question = request.retrieval_query or request.prompt
        low_candidates = orchestrator.bm25.search(question, top_k=orchestrator.bm25_top_k)
        prompt = build_hybrid_v0_basic_injection_prompt(
            question,
            low_candidates,
            max_evidence_chars=self.config.hybrid_evidence_chars,
        )
        return prompt, [_evidence_candidate_to_source(index, candidate) for index, candidate in enumerate(low_candidates, 1)]

    async def _stream_graphrag(self, mode: str, data_dir: Path, query: str) -> AsyncGenerator[str, None]:
        functions = self._search_functions or _load_streaming_functions()
        config = _load_graphrag_config(self.root_dir, data_dir)
        if mode == "basic":
            text_units = _read_parquet(data_dir, "text_units")
            async for chunk in functions["basic"](
                config=config,
                text_units=text_units,
                response_type=self.config.response_type,
                query=query,
            ):
                yield str(chunk)
            return

        dataframes = _load_common_frames(data_dir)
        if mode == "local":
            async for chunk in functions["local"](
                config=config,
                entities=dataframes["entities"],
                communities=dataframes["communities"],
                community_reports=dataframes["community_reports"],
                text_units=dataframes["text_units"],
                relationships=dataframes["relationships"],
                covariates=dataframes.get("covariates"),
                community_level=2,
                response_type=self.config.response_type,
                query=query,
            ):
                yield str(chunk)
            return
        if mode == "global":
            async for chunk in functions["global"](
                config=config,
                entities=dataframes["entities"],
                communities=dataframes["communities"],
                community_reports=dataframes["community_reports"],
                community_level=2,
                dynamic_community_selection=False,
                response_type=self.config.response_type,
                query=query,
            ):
                yield str(chunk)
            return
        if mode == "drift":
            async for chunk in functions["drift"](
                config=config,
                entities=dataframes["entities"],
                communities=dataframes["communities"],
                community_reports=dataframes["community_reports"],
                text_units=dataframes["text_units"],
                relationships=dataframes["relationships"],
                community_level=2,
                response_type=self.config.response_type,
                query=query,
            ):
                yield str(chunk)
            return
        raise ValueError(f"unsupported native streaming mode: {mode}")


class DataCitationDeltaFilter:
    """过滤流式片段中的 `[Data: ...]`，避免把内部编号直接推给学生端。"""

    _PREFIX = "[Data:"

    def __init__(self) -> None:
        self._buffer = ""

    def feed(self, text: str) -> str:
        self._buffer += text or ""
        emitted: list[str] = []
        while self._buffer:
            start = self._buffer.casefold().find(self._PREFIX.casefold())
            if start < 0:
                keep = _partial_prefix_len(self._buffer, self._PREFIX)
                if keep:
                    emitted.append(self._buffer[:-keep])
                    self._buffer = self._buffer[-keep:]
                else:
                    emitted.append(self._buffer)
                    self._buffer = ""
                break
            if start > 0:
                emitted.append(self._buffer[:start])
                self._buffer = self._buffer[start:]
                continue
            end = self._buffer.find("]")
            if end < 0:
                break
            self._buffer = self._buffer[end + 1 :]
        return "".join(emitted)

    def finish(self) -> str:
        tail = _DATA_BLOCK_RE.sub("", self._buffer)
        if tail.casefold().startswith(self._PREFIX.casefold()):
            tail = ""
        self._buffer = ""
        return tail


def _load_streaming_functions() -> dict[str, Callable[..., AsyncGenerator[str, None]]]:
    from graphrag.api import (
        basic_search_streaming,
        drift_search_streaming,
        global_search_streaming,
        local_search_streaming,
    )

    return {
        "basic": basic_search_streaming,
        "local": local_search_streaming,
        "global": global_search_streaming,
        "drift": drift_search_streaming,
    }


def _load_graphrag_config(root_dir: Path, data_dir: Path) -> Any:
    from graphrag.config.load_config import load_config

    return load_config(
        root_dir=root_dir,
        cli_overrides={
            "output_storage": {"base_dir": str(data_dir)},
            "vector_store": {"db_uri": str(data_dir / "lancedb")},
        },
    )


def _load_common_frames(data_dir: Path) -> dict[str, pd.DataFrame | None]:
    return {
        "entities": _read_parquet(data_dir, "entities"),
        "communities": _read_parquet(data_dir, "communities"),
        "community_reports": _read_parquet(data_dir, "community_reports"),
        "text_units": _read_parquet(data_dir, "text_units"),
        "relationships": _read_parquet(data_dir, "relationships"),
        "covariates": _read_optional_parquet(data_dir, "covariates"),
    }


def _read_parquet(data_dir: Path, name: str) -> pd.DataFrame:
    return pd.read_parquet(data_dir / f"{name}.parquet")


def _read_optional_parquet(data_dir: Path, name: str) -> pd.DataFrame | None:
    path = data_dir / f"{name}.parquet"
    return pd.read_parquet(path) if path.exists() else None


def _evidence_candidate_to_source(rank: int, candidate: EvidenceCandidate) -> dict[str, Any]:
    metadata = dict(candidate.metadata or {})
    return {
        "rank": rank,
        "kind": metadata.get("kind") or candidate.source,
        "source_type": metadata.get("source_type") or candidate.source,
        "ref": candidate.ref,
        "chunk_id": metadata.get("chunk_id") or candidate.ref,
        "document_key": metadata.get("document_key") or metadata.get("source_file") or candidate.source,
        "source_file": metadata.get("source_file") or candidate.source,
        "heading_path": metadata.get("heading_path") or metadata.get("heading_path_text") or "",
        "page_start": metadata.get("page_start"),
        "page_end": metadata.get("page_end"),
        "snippet": _shorten(candidate.text, 280),
    }


def _shorten(text: str, max_chars: int) -> str:
    normalized = re.sub(r"\s+", " ", text or "").strip()
    if len(normalized) <= max_chars:
        return normalized
    return normalized[: max_chars - 1].rstrip() + "…"


def _partial_prefix_len(text: str, prefix: str) -> int:
    max_len = min(len(text), len(prefix) - 1)
    lower_text = text.casefold()
    lower_prefix = prefix.casefold()
    for length in range(max_len, 0, -1):
        if lower_prefix.startswith(lower_text[-length:]):
            return length
    return 0


def _parse_positive_int(value: str | None, *, default: int) -> int:
    try:
        parsed = int(value or "")
    except ValueError:
        return default
    return parsed if parsed > 0 else default

