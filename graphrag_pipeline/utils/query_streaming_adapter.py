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

try:
    from graphrag.callbacks.query_callbacks import QueryCallbacks
except Exception:  # pragma: no cover - 仅用于缺少 GraphRAG 运行依赖的静态导入场景
    class QueryCallbacks:  # type: ignore[no-redef]
        """GraphRAG QueryCallbacks 的轻量兜底基类。"""

        pass


_DATA_BLOCK_RE = re.compile(r"\[Data:\s*[^\]]*\]", re.IGNORECASE)


@dataclass(frozen=True, slots=True)
class NativeStreamingConfig:
    enabled_modes: set[str] = field(default_factory=lambda: {"basic", "local", "global", "drift", "hybrid_v0"})
    response_type: str = "Multiple Paragraphs"
    hybrid_evidence_chars: int = 6000

    @classmethod
    def from_env(cls) -> "NativeStreamingConfig":
        raw_modes = os.getenv("CKQA_GRAPHRAG_NATIVE_STREAMING_MODES") or "basic,local,global,drift,hybrid_v0"
        modes = {mode.strip() for mode in raw_modes.split(",") if mode.strip()}
        return cls(
            enabled_modes=modes or {"basic", "local", "global", "drift", "hybrid_v0"},
            response_type=os.getenv("CKQA_GRAPHRAG_NATIVE_STREAMING_RESPONSE_TYPE") or "Multiple Paragraphs",
            hybrid_evidence_chars=_parse_positive_int(
                os.getenv("CKQA_HYBRID_V0_BASIC_INJECTION_EVIDENCE_CHARS"),
                default=6000,
            ),
        )


@dataclass(frozen=True, slots=True)
class NativeStreamingChunk:
    text: str = ""
    sources: list[dict[str, Any]] = field(default_factory=list)
    event: str = "delta"
    progress: dict[str, Any] | None = None


class NativeGraphRagStreamingAdapter:
    """GraphRAG Python Query API streaming 适配层。

    默认覆盖 `basic/local/global/drift/hybrid_v0` 五种学生端 QA 模式；
    可通过 `CKQA_GRAPHRAG_NATIVE_STREAMING_MODES` 缩小或调整启用范围。
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

        if request.mode == "hybrid_v0" and hybrid_sources:
            yield NativeStreamingChunk(
                event="progress",
                progress=_progress_event(
                    "context_selected",
                    request.mode,
                    f"已选取 {len(hybrid_sources)} 个课程片段作为混合检索依据。",
                    {"textUnitCount": len(hybrid_sources), "strategy": "hybrid_v0"},
                    [_source_to_evidence(source) for source in hybrid_sources[:5]],
                ),
            )

        async for chunk in self._stream_graphrag(effective_mode, data_dir, query, display_mode=request.mode):
            if chunk.event == "progress":
                yield chunk
                continue
            if chunk.text:
                yield NativeStreamingChunk(text=str(chunk.text), sources=hybrid_sources or chunk.sources)
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

    async def _stream_graphrag(
        self,
        mode: str,
        data_dir: Path,
        query: str,
        *,
        display_mode: str | None = None,
    ) -> AsyncGenerator[NativeStreamingChunk, None]:
        functions = self._search_functions or _load_streaming_functions()
        config = _load_graphrag_config(self.root_dir, data_dir)
        callbacks = _GraphRagProgressCallbacks(display_mode or mode)
        if mode == "basic":
            text_units = _read_parquet(data_dir, "text_units")
            async for chunk in _stream_with_progress(
                functions["basic"],
                callbacks,
                config=config,
                text_units=text_units,
                response_type=self.config.response_type,
                query=query,
            ):
                yield chunk
            return

        dataframes = _load_common_frames(data_dir)
        if mode == "local":
            async for chunk in _stream_with_progress(
                    functions["local"],
                    callbacks,
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
                yield chunk
            return
        if mode == "global":
            async for chunk in _stream_with_progress(
                    functions["global"],
                    callbacks,
                    config=config,
                    entities=dataframes["entities"],
                    communities=dataframes["communities"],
                    community_reports=dataframes["community_reports"],
                    community_level=2,
                    dynamic_community_selection=False,
                    response_type=self.config.response_type,
                    query=query,
            ):
                yield chunk
            return
        if mode == "drift":
            async for chunk in _stream_with_progress(
                    functions["drift"],
                    callbacks,
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
                yield chunk
            return
        raise ValueError(f"unsupported native streaming mode: {mode}")


class _GraphRagProgressCallbacks(QueryCallbacks):
    """把 GraphRAG QueryCallbacks 转成学生可读的检索过程事件。"""

    def __init__(self, mode: str) -> None:
        self._mode = mode
        self._events: list[dict[str, Any]] = []

    def drain(self) -> list[dict[str, Any]]:
        events = self._events
        self._events = []
        return events

    def on_context(self, context: Any) -> None:
        metrics, evidence = _context_metrics_and_evidence(context)
        if metrics.get("reportCount", 0) > 0:
            summary = f"已选取 {metrics['reportCount']} 份课程报告作为回答依据。"
        elif metrics.get("textUnitCount", 0) > 0:
            summary = f"已选取 {metrics['textUnitCount']} 个课程片段作为回答依据。"
        elif metrics.get("entityCount", 0) > 0 or metrics.get("relationshipCount", 0) > 0:
            summary = (
                f"已选取 {metrics.get('entityCount', 0)} 个课程概念"
                f"和 {metrics.get('relationshipCount', 0)} 条概念关系作为上下文。"
            )
        else:
            summary = "已构建课程知识库上下文，准备生成回答。"
        self._events.append(_progress_event("context_selected", self._mode, summary, metrics, evidence))

    def on_map_response_start(self, map_response_contexts: list[Any]) -> None:
        count = len(map_response_contexts or [])
        self._events.append(
            _progress_event(
                "map_started",
                self._mode,
                f"正在汇总 {count} 组课程报告，先提取与问题相关的要点。",
                {"reportGroupCount": count},
                [_context_item_to_evidence(item, "report_group") for item in list(map_response_contexts or [])[:5]],
            )
        )

    def on_map_response_end(self, map_response_outputs: list[Any]) -> None:
        count = len(map_response_outputs or [])
        self._events.append(
            _progress_event(
                "map_finished",
                self._mode,
                f"已完成 {count} 组课程报告的初步归纳，准备合并共同结论。",
                {"mapResultCount": count},
                [_context_item_to_evidence(item, "map_result") for item in list(map_response_outputs or [])[:5]],
            )
        )

    def on_reduce_response_start(self, reduce_response_context: Any) -> None:
        metrics, evidence = _context_metrics_and_evidence(reduce_response_context)
        if not metrics:
            metrics = {"contextCount": _safe_len(reduce_response_context)}
        self._events.append(
            _progress_event(
                "reduce_started",
                self._mode,
                "正在综合课程报告和片段，形成最终回答。",
                metrics,
                evidence,
            )
        )

    def on_reduce_response_end(self, reduce_response_output: str) -> None:
        self._events.append(
            _progress_event(
                "reduce_finished",
                self._mode,
                "已完成课程知识库综合，准备输出最终回答。",
                {"outputChars": len(str(reduce_response_output or ""))},
                [],
            )
        )

    def on_llm_new_token(self, token) -> None:  # noqa: ANN001 - GraphRAG callback 签名由上游定义
        return None


async def _stream_with_progress(
    search_function: Callable[..., AsyncGenerator[Any, None]],
    callbacks: _GraphRagProgressCallbacks,
    **kwargs: Any,
) -> AsyncGenerator[NativeStreamingChunk, None]:
    async for chunk in search_function(**kwargs, callbacks=[callbacks]):
        for event in callbacks.drain():
            yield NativeStreamingChunk(event="progress", progress=event)
        yield NativeStreamingChunk(text=str(chunk))
    for event in callbacks.drain():
        yield NativeStreamingChunk(event="progress", progress=event)


def _progress_event(
    event_type: str,
    mode: str,
    summary: str,
    metrics: dict[str, Any] | None,
    evidence: list[dict[str, Any]] | None,
) -> dict[str, Any]:
    return {
        "type": event_type,
        "mode": mode,
        "summary": summary,
        "metrics": _json_ready_dict(metrics or {}),
        "evidence": [item for item in list(evidence or []) if item],
    }


def _context_metrics_and_evidence(context: Any) -> tuple[dict[str, Any], list[dict[str, Any]]]:
    sections = _context_sections(context)
    metrics: dict[str, Any] = {}
    evidence: list[dict[str, Any]] = []
    for key, value in sections.items():
        records = _records_from_context_value(value)
        count = len(records) if records else _safe_len(value)
        metric_key = _metric_key_for_context(key)
        if count:
            metrics[metric_key] = metrics.get(metric_key, 0) + count
        for record in records[:5]:
            evidence_item = _context_item_to_evidence(record, _evidence_kind_for_context(key))
            if evidence_item:
                evidence.append(evidence_item)
        if len(evidence) >= 5:
            break
    if not metrics and context is not None:
        metrics["contextCount"] = _safe_len(context)
    return metrics, evidence[:5]


def _context_sections(context: Any) -> dict[str, Any]:
    if isinstance(context, pd.DataFrame):
        return {"text_units": context}
    if isinstance(context, dict):
        return context
    if isinstance(context, list):
        return {"items": context}
    if isinstance(context, str):
        return {"context": [context]}
    return {"context": [context]} if context is not None else {}


def _records_from_context_value(value: Any) -> list[Any]:
    if value is None:
        return []
    if isinstance(value, pd.DataFrame):
        return value.head(8).to_dict(orient="records")
    if isinstance(value, dict):
        nested = value.get("records") or value.get("data") or value.get("items")
        if isinstance(nested, list):
            return nested
        return [value]
    if isinstance(value, list):
        return value
    if isinstance(value, tuple):
        return list(value)
    if isinstance(value, str):
        return [value]
    return [value]


def _context_item_to_evidence(item: Any, kind: str) -> dict[str, Any]:
    if item is None:
        return {}
    if isinstance(item, str):
        return {
            "kind": kind,
            "title": kind.replace("_", " "),
            "snippet": _shorten(item, 180),
        }
    if isinstance(item, dict):
        title = (
            item.get("title")
            or item.get("source_file")
            or item.get("document_key")
            or item.get("id")
            or item.get("human_readable_id")
            or kind.replace("_", " ")
        )
        snippet = (
            item.get("snippet")
            or item.get("summary")
            or item.get("text")
            or item.get("content")
            or item.get("description")
            or item.get("response")
            or ""
        )
        evidence = {
            "kind": kind,
            "title": str(title),
            "snippet": _shorten(str(snippet), 180),
        }
        for source_key, target_key in (
            ("id", "id"),
            ("human_readable_id", "ref"),
            ("ref", "ref"),
            ("source_file", "sourceFile"),
            ("page_start", "pageStart"),
            ("page_end", "pageEnd"),
        ):
            if item.get(source_key) is not None:
                evidence[target_key] = item.get(source_key)
        return evidence
    title = getattr(item, "title", None) or getattr(item, "source_file", None) or getattr(item, "id", None) or kind
    snippet = getattr(item, "summary", None) or getattr(item, "text", None) or getattr(item, "response", None) or ""
    return {
        "kind": kind,
        "title": str(title),
        "snippet": _shorten(str(snippet), 180),
    }


def _source_to_evidence(source: dict[str, Any]) -> dict[str, Any]:
    return {
        "kind": str(source.get("source_type") or source.get("kind") or "source"),
        "title": str(source.get("source_file") or source.get("document_key") or source.get("ref") or "课程片段"),
        "snippet": _shorten(str(source.get("snippet") or ""), 180),
        "ref": source.get("ref"),
        "pageStart": source.get("page_start"),
        "pageEnd": source.get("page_end"),
    }


def _metric_key_for_context(key: str) -> str:
    normalized = key.strip().casefold()
    if "report" in normalized or "communit" in normalized:
        return "reportCount"
    if "text" in normalized or "source" in normalized or "unit" in normalized:
        return "textUnitCount"
    if "entity" in normalized:
        return "entityCount"
    if "relationship" in normalized:
        return "relationshipCount"
    return "contextCount"


def _evidence_kind_for_context(key: str) -> str:
    normalized = key.strip().casefold()
    if "report" in normalized or "communit" in normalized:
        return "report"
    if "entity" in normalized:
        return "entity"
    if "relationship" in normalized:
        return "relationship"
    if "text" in normalized or "source" in normalized or "unit" in normalized:
        return "text_unit"
    return "context"


def _safe_len(value: Any) -> int:
    try:
        return len(value)
    except TypeError:
        return 1 if value is not None else 0


def _json_ready_dict(value: dict[str, Any]) -> dict[str, Any]:
    ready: dict[str, Any] = {}
    for key, item in value.items():
        if isinstance(item, (str, int, float, bool)) or item is None:
            ready[str(key)] = item
        else:
            ready[str(key)] = str(item)
    return ready


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
