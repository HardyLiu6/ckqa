from __future__ import annotations

import os
import re
import asyncio
import time
import logging
import contextvars
import threading
from collections.abc import AsyncGenerator, Callable
from contextlib import suppress
from dataclasses import dataclass, field
from pathlib import Path
from typing import Any

import pandas as pd

from graphrag_pipeline.scripts.hybrid_qa.prompt_builder import build_hybrid_v0_basic_injection_prompt
from graphrag_pipeline.scripts.hybrid_qa.types import EvidenceCandidate, HybridLayer
from query_task_manager import QueryTaskRequest

try:
    from graphrag.callbacks.query_callbacks import QueryCallbacks
except Exception:  # pragma: no cover - 仅用于缺少 GraphRAG 运行依赖的静态导入场景
    class QueryCallbacks:  # type: ignore[no-redef]
        """GraphRAG QueryCallbacks 的轻量兜底基类。"""

        pass


_DATA_BLOCK_RE = re.compile(r"\[Data:\s*[^\]]*\]", re.IGNORECASE)
_TEXT_METADATA_KEY_RE = re.compile(
    r"\b(document_type|chapter|section|subsection|heading_level|heading_path_text|"
    r"page_start|page_end|section_level|source_file|course_id):"
)
_RATE_LIMIT_SIGNAL_RE = re.compile(
    r"("
    r"(?<!\d)429(?!\d)|"
    r"ratelimiterror|"
    r"rate[-_\s]*limit|"
    r"too\s+many\s+requests|"
    r"retry[-_\s]*after|"
    r"限流|"
    r"insufficient[_\s-]*quota|"
    r"quota\s+exceeded|"
    r"exceeded\b.{0,80}\bquota|"
    r"额度\s*(?:不足|已用尽|超限|超出|已超限)"
    r")",
    re.IGNORECASE,
)
_RETRY_AFTER_RE = re.compile(
    r"retry[-_\s]*after[^\d]{0,20}(\d+(?:\.\d+)?)",
    re.IGNORECASE,
)
_STATUS_429_RE = re.compile(r"(?<!\d)429(?!\d)")
_RATE_LIMIT_LOGGER_NAMES = (
    "graphrag",
    "fnllm",
    "openai",
)
_RATE_LIMIT_SUMMARY = "模型服务当前繁忙，系统正在等待重试窗口后继续处理课程内容。"
_ACTIVE_RATE_LIMIT_MONITOR: contextvars.ContextVar[object | None] = contextvars.ContextVar(
    "ckqa_active_rate_limit_monitor",
    default=None,
)


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
        yield NativeStreamingChunk(
            event="progress",
            progress=_retrieval_started_event(request.mode),
        )
        if request.mode == "hybrid_v0":
            query, hybrid_sources = self._build_hybrid_basic_query(request, data_dir)
            effective_mode = "basic"

        if request.mode == "hybrid_v0" and hybrid_sources:
            yield NativeStreamingChunk(
                event="progress",
                progress=_progress_event(
                    "hybrid_low_evidence_selected",
                    request.mode,
                    f"低层 BM25 已召回 {len(hybrid_sources)} 个候选课程片段，准备注入 GraphRAG Basic 做上下文融合。",
                    {
                        "textUnitCount": len(hybrid_sources),
                        "bm25EvidenceCount": len(hybrid_sources),
                        "strategy": "hybrid_v0",
                        "fusionStage": "bm25_low_layer",
                    },
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
        self._event_sink: Callable[[dict[str, Any]], None] | None = None
        self._active_phase: str | None = None
        self._active_metrics: dict[str, Any] = {}
        self._phase_started_at = time.monotonic()

    def drain(self) -> list[dict[str, Any]]:
        events = self._events
        self._events = []
        return events

    def attach_sink(self, sink: Callable[[dict[str, Any]], None]) -> None:
        self._event_sink = sink
        for event in self.drain():
            sink(event)

    def detach_sink(self) -> None:
        self._event_sink = None

    def heartbeat_event(self) -> dict[str, Any] | None:
        if self._active_phase == "map":
            count = int(self._active_metrics.get("reportGroupCount") or 0)
            elapsed = max(1, int(time.monotonic() - self._phase_started_at))
            return _progress_event(
                "map_running",
                self._mode,
                f"仍在逐组整理课程要点，已处理约 {elapsed} 秒；完成后会合并成完整回答。",
                {**self._active_metrics, "elapsedSeconds": elapsed},
                [],
            )
        if self._active_phase == "reduce":
            elapsed = max(1, int(time.monotonic() - self._phase_started_at))
            return _progress_event(
                "reduce_running",
                self._mode,
                _reduce_running_summary(self._mode, elapsed),
                {**self._active_metrics, "elapsedSeconds": elapsed},
                [],
            )
        if self._active_phase == "answer":
            elapsed = max(1, int(time.monotonic() - self._phase_started_at))
            return _progress_event(
                "answer_running",
                self._mode,
                _answer_running_summary(self._active_metrics, elapsed),
                {**self._active_metrics, "elapsedSeconds": elapsed},
                [],
            )
        return None

    def mark_answer_started(self) -> None:
        if self._active_phase in {"answer", "reduce"}:
            self._active_phase = None

    def on_context(self, context: Any) -> None:
        metrics, evidence = _context_metrics_and_evidence(context)
        if self._mode == "hybrid_v0":
            metrics = {**metrics, "strategy": "hybrid_v0", "fusionStage": "basic_context"}
            summary = _hybrid_context_selected_summary(metrics)
        elif metrics.get("reportCount", 0) > 0:
            summary = f"已选取 {metrics['reportCount']} 份课程报告作为回答依据。"
        elif metrics.get("textUnitCount", 0) > 0:
            summary = f"已选取 {metrics['textUnitCount']} 个课程片段作为回答依据。"
        elif metrics.get("entityCount", 0) > 0 or metrics.get("relationshipCount", 0) > 0:
            summary = _entity_relationship_context_summary(metrics)
        else:
            summary = "已构建课程知识库上下文，准备生成回答。"
        self._emit(_progress_event("context_selected", self._mode, summary, metrics, evidence))
        if self._mode in {"basic", "local", "hybrid_v0"}:
            self._set_active_phase("answer", metrics)

    def on_map_response_start(self, map_response_contexts: list[Any]) -> None:
        count = len(map_response_contexts or [])
        metrics = {"reportGroupCount": count}
        self._set_active_phase("map", metrics)
        self._emit(
            _progress_event(
                "map_started",
                self._mode,
                f"已找到 {count} 组相关课程内容，正在分别提炼要点。",
                metrics,
                [_context_item_to_evidence(item, "report_group") for item in list(map_response_contexts or [])[:5]],
            )
        )

    def on_map_response_end(self, map_response_outputs: list[Any]) -> None:
        count = len(map_response_outputs or [])
        self._active_phase = None
        self._emit(
            _progress_event(
                "map_finished",
                self._mode,
                f"已整理 {count} 组课程要点，准备合并重复内容和共同结论。",
                {"mapResultCount": count},
                [_context_item_to_evidence(item, "map_result") for item in list(map_response_outputs or [])[:5]],
            )
        )
        if self._mode == "global":
            metrics = {"mapResultCount": count}
            self._set_active_phase("reduce", metrics)
            self._emit(
                _progress_event(
                    "reduce_started",
                    self._mode,
                    "正在把分散的课程要点组织成完整回答。",
                    metrics,
                    [_context_item_to_evidence(item, "map_result") for item in list(map_response_outputs or [])[:5]],
                )
            )

    def on_reduce_response_start(self, reduce_response_context: Any) -> None:
        if self._mode == "drift":
            metrics, evidence = _drift_response_state_metrics_and_evidence(reduce_response_context)
            if metrics or evidence:
                self._set_active_phase("reduce", metrics)
                self._emit(
                    _progress_event(
                        "reduce_started",
                        self._mode,
                        _drift_reduce_started_summary(metrics),
                        metrics,
                        evidence,
                    )
                )
                return
        metrics, evidence = _context_metrics_and_evidence(reduce_response_context)
        if not metrics:
            metrics = {"contextCount": _safe_len(reduce_response_context)}
        self._set_active_phase("reduce", metrics)
        self._emit(
            _progress_event(
                "reduce_started",
                self._mode,
                "正在把分散的课程要点组织成完整回答。",
                metrics,
                evidence,
            )
        )

    def on_reduce_response_end(self, reduce_response_output: str) -> None:
        self._active_phase = None
        self._emit(
            _progress_event(
                "reduce_finished",
                self._mode,
                _reduce_finished_summary(self._mode),
                {"outputChars": len(str(reduce_response_output or ""))},
                [],
            )
        )

    def on_llm_new_token(self, token) -> None:  # noqa: ANN001 - GraphRAG callback 签名由上游定义
        return None

    def _emit(self, event: dict[str, Any]) -> None:
        if self._event_sink is not None:
            self._event_sink(event)
            return
        self._events.append(event)

    def _set_active_phase(self, phase: str, metrics: dict[str, Any]) -> None:
        self._active_phase = phase
        self._active_metrics = dict(metrics)
        self._phase_started_at = time.monotonic()


async def _stream_with_progress(
    search_function: Callable[..., AsyncGenerator[Any, None]],
    callbacks: _GraphRagProgressCallbacks,
    **kwargs: Any,
) -> AsyncGenerator[NativeStreamingChunk, None]:
    queue: asyncio.Queue[NativeStreamingChunk | BaseException | object] = asyncio.Queue()
    done = object()
    heartbeat_seconds = _progress_heartbeat_seconds()
    delta_chars = _stream_delta_chars()
    delta_sleep_seconds = _stream_delta_sleep_seconds()

    def push_progress(event: dict[str, Any]) -> None:
        queue.put_nowait(NativeStreamingChunk(event="progress", progress=event))

    callbacks.attach_sink(push_progress)
    rate_limit_monitor = _ScopedRateLimitLogMonitor(callbacks._mode, push_progress)

    async def produce() -> None:
        rate_limit_monitor.install()
        try:
            async for chunk in search_function(**kwargs, callbacks=[callbacks]):
                text = str(chunk)
                if text:
                    callbacks.mark_answer_started()
                parts = _split_stream_text(text, delta_chars)
                for index, part in enumerate(parts):
                    queue.put_nowait(NativeStreamingChunk(text=part))
                    if delta_sleep_seconds > 0 and index < len(parts) - 1:
                        await asyncio.sleep(delta_sleep_seconds)
        except Exception as exc:  # noqa: BLE001 - 透传给外层任务管理器处理回退
            rate_limit_monitor.emit_exception_if_rate_limited(exc)
            queue.put_nowait(exc)
        finally:
            rate_limit_monitor.uninstall()
            for event in callbacks.drain():
                queue.put_nowait(NativeStreamingChunk(event="progress", progress=event))
            queue.put_nowait(done)

    producer_task = asyncio.create_task(produce())
    try:
        while True:
            try:
                item = await asyncio.wait_for(queue.get(), timeout=heartbeat_seconds)
            except TimeoutError:
                heartbeat = callbacks.heartbeat_event()
                if heartbeat is not None:
                    yield NativeStreamingChunk(event="progress", progress=heartbeat)
                continue

            if item is done:
                break
            if isinstance(item, BaseException):
                raise item
            yield item
    finally:
        callbacks.detach_sink()
        if not producer_task.done():
            producer_task.cancel()
            with suppress(asyncio.CancelledError):
                await producer_task


class _ScopedRateLimitLogMonitor(logging.Handler):
    """单次 native streaming 查询内的模型限流日志监听器。"""

    def __init__(self, mode: str, sink: Callable[[dict[str, Any]], None]) -> None:
        super().__init__(level=logging.WARNING)
        self._mode = mode
        self._sink = sink
        self._installed_loggers: list[logging.Logger] = []
        self._occurrence_count = 0
        self._emitted_event = False
        self._context_id = object()
        self._context_token: contextvars.Token[object | None] | None = None
        self._loop: asyncio.AbstractEventLoop | None = None
        self._loop_thread_id: int | None = None

    def install(self) -> None:
        self._loop = asyncio.get_running_loop()
        self._loop_thread_id = threading.get_ident()
        self._context_token = _ACTIVE_RATE_LIMIT_MONITOR.set(self._context_id)
        for name in _RATE_LIMIT_LOGGER_NAMES:
            logger = logging.getLogger(name)
            logger.addHandler(self)
            self._installed_loggers.append(logger)

    def uninstall(self) -> None:
        for logger in self._installed_loggers:
            with suppress(ValueError):
                logger.removeHandler(self)
        self._installed_loggers = []
        if self._context_token is not None:
            _ACTIVE_RATE_LIMIT_MONITOR.reset(self._context_token)
            self._context_token = None

    def emit(self, record: logging.LogRecord) -> None:
        active_monitor = _ACTIVE_RATE_LIMIT_MONITOR.get()
        if active_monitor is not None and active_monitor is not self._context_id:
            return
        text = _log_record_detection_text(record)
        if not _is_rate_limit_signal(text):
            return
        self._occurrence_count += 1
        if self._emitted_event:
            return
        self._emitted_event = True
        self._emit_progress(_rate_limit_progress_event(self._mode, "graphrag_log", self._occurrence_count, text))

    def emit_exception_if_rate_limited(self, exc: BaseException) -> None:
        text = _exception_detection_text(exc)
        if not _is_rate_limit_signal(text):
            return
        self._occurrence_count += 1
        if self._emitted_event:
            return
        self._emitted_event = True
        self._emit_progress(_rate_limit_progress_event(self._mode, "native_exception", self._occurrence_count, text))

    def _emit_progress(self, event: dict[str, Any]) -> None:
        if self._loop is not None and self._loop_thread_id != threading.get_ident():
            self._loop.call_soon_threadsafe(self._sink, event)
            return
        self._sink(event)


def _log_record_detection_text(record: logging.LogRecord) -> str:
    parts = [record.getMessage()]
    if record.exc_info and record.exc_info[1] is not None:
        exc = record.exc_info[1]
        parts.append(type(exc).__name__)
        parts.append(str(exc))
    return " ".join(part for part in parts if part)


def _exception_detection_text(exc: BaseException) -> str:
    return f"{type(exc).__name__} {exc}"


def _is_rate_limit_signal(text: str) -> bool:
    return bool(text and _RATE_LIMIT_SIGNAL_RE.search(text))


def _rate_limit_progress_event(mode: str, source: str, occurrence_count: int, text: str) -> dict[str, Any]:
    metrics: dict[str, Any] = {
        "reasonType": "rate_limit",
        "source": source,
        "occurrenceCount": max(1, occurrence_count),
    }
    if _STATUS_429_RE.search(text):
        metrics["statusCode"] = 429
    retry_after = _extract_retry_after_seconds(text)
    if retry_after is not None:
        metrics["retryAfterSeconds"] = retry_after
    return _progress_event("model_rate_limit", mode, _RATE_LIMIT_SUMMARY, metrics, [])


def _extract_retry_after_seconds(text: str) -> int | None:
    match = _RETRY_AFTER_RE.search(text or "")
    if not match:
        return None
    try:
        value = float(match.group(1))
    except (TypeError, ValueError):
        return None
    if value < 0:
        return None
    return int(value)


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


def _retrieval_started_event(mode: str) -> dict[str, Any]:
    summaries = {
        "basic": "正在检索课程片段和向量匹配结果，准备构建回答依据。",
        "local": "正在检索课程概念、关系和片段，准备构建局部上下文。",
        "global": "正在从整门课程中寻找和问题相关的章节主题。",
        "drift": "正在沿课程报告和概念线索展开追问式检索。",
        "hybrid_v0": "正在检索混合证据：先召回本地 BM25 课程片段，再融合 GraphRAG Basic 课程上下文。",
    }
    return _progress_event(
        "retrieval_started",
        mode,
        summaries.get(mode, "正在读取课程知识库检索上下文。"),
        {"strategy": mode},
        [],
    )


def _answer_running_summary(metrics: dict[str, Any], elapsed: int) -> str:
    if str(metrics.get("fusionStage") or "") == "basic_context":
        return f"仍在融合 BM25 片段与 GraphRAG Basic 上下文组织回答，已处理约 {elapsed} 秒。"
    text_units = int(metrics.get("textUnitCount") or 0)
    entities = int(metrics.get("entityCount") or 0)
    relationships = int(metrics.get("relationshipCount") or 0)
    reports = int(metrics.get("reportCount") or 0)
    if text_units and not entities and not relationships:
        return f"仍在基于 {text_units} 个课程片段组织回答，已处理约 {elapsed} 秒。"
    if entities or relationships:
        parts: list[str] = []
        if entities:
            parts.append(f"{entities} 个课程概念")
        if relationships:
            parts.append(f"{relationships} 条概念关系")
        if text_units:
            parts.append(f"{text_units} 个课程片段")
        return f"仍在基于 {'、'.join(parts)} 组织回答，已处理约 {elapsed} 秒。"
    if reports:
        return f"仍在基于 {reports} 份课程报告组织回答，已处理约 {elapsed} 秒。"
    return f"仍在基于课程知识库上下文组织回答，已处理约 {elapsed} 秒。"


def _entity_relationship_context_summary(metrics: dict[str, Any]) -> str:
    entities = int(metrics.get("entityCount") or 0)
    relationships = int(metrics.get("relationshipCount") or 0)
    if entities and relationships:
        return f"已选取 {entities} 个课程概念和 {relationships} 条概念关系作为上下文。"
    if entities:
        return f"已选取 {entities} 个课程概念作为上下文。"
    if relationships:
        return f"已选取 {relationships} 条概念关系作为上下文。"
    return "已构建课程知识库上下文，准备生成回答。"


def _hybrid_context_selected_summary(metrics: dict[str, Any]) -> str:
    text_units = int(metrics.get("textUnitCount") or 0)
    if text_units:
        return f"GraphRAG Basic 已基于混合证据构建 {text_units} 个课程片段的融合上下文。"
    context_count = int(metrics.get("contextCount") or 0)
    if context_count:
        return f"GraphRAG Basic 已基于混合证据构建 {context_count} 组融合上下文。"
    return "GraphRAG Basic 已基于混合证据构建融合上下文。"


def _reduce_running_summary(mode: str, elapsed: int) -> str:
    if mode == "drift":
        return f"仍在汇总追问得到的课程依据，已处理约 {elapsed} 秒；马上会开始显示正文。"
    return f"仍在整理完整回答，已处理约 {elapsed} 秒；马上会开始显示正文。"


def _reduce_finished_summary(mode: str) -> str:
    if mode == "drift":
        return "追问结果已汇总，准备开始输出回答。"
    return "课程要点已整理完成，准备开始输出回答。"


def _progress_heartbeat_seconds() -> float | None:
    raw = os.getenv("CKQA_GRAPHRAG_PROGRESS_HEARTBEAT_SECONDS", "8")
    try:
        value = float(raw)
    except (TypeError, ValueError):
        value = 8.0
    if value <= 0:
        return None
    return value


def _stream_delta_chars() -> int:
    raw = os.getenv("CKQA_GRAPHRAG_STREAM_DELTA_CHARS", "48")
    try:
        value = int(raw)
    except (TypeError, ValueError):
        value = 48
    return max(1, value)


def _stream_delta_sleep_seconds() -> float:
    raw = os.getenv("CKQA_GRAPHRAG_STREAM_DELTA_SLEEP_SECONDS", "0.04")
    try:
        value = float(raw)
    except (TypeError, ValueError):
        value = 0.04
    return max(0.0, value)


def _split_stream_text(text: str, max_chars: int) -> list[str]:
    if not text:
        return []
    limit = max(1, int(max_chars or 64))
    if len(text) <= limit:
        return [text]
    return [text[index:index + limit] for index in range(0, len(text), limit)]


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


def _drift_response_state_metrics_and_evidence(context: Any) -> tuple[dict[str, Any], list[dict[str, Any]]]:
    nodes = _drift_response_nodes(context)
    if not nodes:
        return {}, []
    evidence: list[dict[str, Any]] = []
    answered_count = 0
    for node in nodes:
        item = _drift_node_to_evidence(node, answered_count + 1)
        if item:
            answered_count += 1
        if item and len(evidence) < 5:
            evidence.append(item)
    return {
        "driftNodeCount": len(nodes),
        "answeredNodeCount": answered_count,
        "pendingNodeCount": max(len(nodes) - answered_count, 0),
    }, evidence


def _drift_response_nodes(context: Any) -> list[Any]:
    if isinstance(context, dict):
        nodes = context.get("nodes")
        if isinstance(nodes, list):
            return nodes
        actions = context.get("actions")
        if isinstance(actions, list):
            return actions
        responses = context.get("responses")
        if isinstance(responses, list):
            return responses
    if isinstance(context, list):
        return context
    return []


def _drift_node_to_evidence(node: Any, index: int) -> dict[str, Any]:
    if isinstance(node, dict):
        title = (
            _clean_drift_text(node.get("query"))
            or _clean_drift_text(node.get("question"))
            or _clean_drift_text(node.get("title"))
            or _drift_fallback_title(node.get("id"), index)
        )
        snippet = _drift_node_answer_snippet(node)
        if not snippet:
            return {}
        evidence: dict[str, Any] = {
            "kind": "drift_answer",
            "title": _shorten(title or f"追问结果 {index}", 80),
            "snippet": _shorten(snippet, 180),
        }
        if node.get("id") is not None:
            evidence["ref"] = str(node.get("id"))
        if node.get("score") is not None:
            evidence["score"] = node.get("score")
        return evidence
    text = _drift_node_answer_snippet(node)
    if not text:
        return {}
    return {
        "kind": "drift_answer",
        "title": f"追问结果 {index}",
        "snippet": _shorten(text, 180),
    }


def _drift_node_answer_snippet(node: Any) -> str:
    if isinstance(node, dict):
        return (
            _clean_drift_text(node.get("answer"))
            or _clean_drift_text(node.get("intermediate_answer"))
            or _clean_drift_text(node.get("response"))
            or _clean_drift_text(node.get("summary"))
            or _clean_drift_text(node.get("text"))
            or _clean_drift_text(node.get("content"))
        )
    text = _clean_drift_text(node)
    return "" if _is_placeholder_context_text(text) else text


def _drift_fallback_title(raw_id: Any, index: int) -> str:
    text = _clean_drift_text(raw_id)
    if text and not _is_placeholder_context_text(text):
        return text
    return f"追问结果 {index}"


def _clean_drift_text(value: Any) -> str:
    if value is None:
        return ""
    return re.sub(r"\s+", " ", str(value)).strip()


def _is_placeholder_context_text(value: str) -> bool:
    return bool(value) and re.fullmatch(r"[\d\s,，、;；]+", value) is not None


def _drift_reduce_started_summary(metrics: dict[str, Any]) -> str:
    total = int(metrics.get("driftNodeCount") or 0)
    answered = int(metrics.get("answeredNodeCount") or 0)
    if total and answered:
        return f"已生成 {total} 条追问线索，完成 {answered} 条可用依据，正在汇总回答。"
    if total:
        return f"已生成 {total} 条追问线索，暂未形成可展示的追问答案，正在汇总已有课程依据。"
    return "已完成追问扩展，正在汇总课程依据。"


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
        if kind == "report_group":
            return _report_group_string_to_evidence(item)
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


def _report_group_string_to_evidence(item: str) -> dict[str, Any]:
    raw = str(item or "").strip()
    content = raw.removeprefix("report group:").strip()
    parts = [part.strip() for part in content.split("|") if part.strip()]
    title = ""
    snippet = ""
    ref = None
    rank_part = next((part for part in parts if part.casefold().startswith("rank ")), "")
    if rank_part:
        ref = rank_part.split(maxsplit=1)[-1].strip() or None
        rank_index = parts.index(rank_part)
        if len(parts) > rank_index + 1:
            title = parts[rank_index + 1]
    content_part = next((part for part in parts if part.startswith("#")), "")
    if content_part:
        snippet = content_part.lstrip("#").strip()
        if not title:
            title = snippet.split(" ", 1)[0]
    if not title and len(parts) >= 2:
        title = parts[1]
    title = _clean_report_group_text(title or "课程报告批次")
    snippet = _clean_report_group_text(snippet or raw)
    evidence = {
        "kind": "report_group",
        "title": _shorten(title, 80),
        "snippet": _shorten(snippet, 180),
    }
    if ref:
        evidence["ref"] = ref
    return evidence


def _clean_report_group_text(text: str) -> str:
    cleaned = str(text or "").strip()
    cleaned = cleaned.removeprefix("#").strip()
    cleaned = cleaned.replace("'", "").replace('"', "")
    cleaned = re.sub(r"\s+", " ", cleaned)
    return cleaned


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
    if "entity" in normalized or "entities" in normalized:
        return "entityCount"
    if "relationship" in normalized:
        return "relationshipCount"
    return "contextCount"


def _evidence_kind_for_context(key: str) -> str:
    normalized = key.strip().casefold()
    if "report" in normalized or "communit" in normalized:
        return "report"
    if "entity" in normalized or "entities" in normalized:
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
    inline_metadata, body = _split_text_metadata(str(candidate.text or ""))
    metadata = {**inline_metadata, **dict(candidate.metadata or {})}
    source_file = metadata.get("source_file") or candidate.source
    heading_path = metadata.get("heading_path") or metadata.get("heading_path_text") or ""
    return {
        "rank": rank,
        "kind": metadata.get("kind") or candidate.source,
        "source_type": metadata.get("source_type") or candidate.source,
        "ref": candidate.ref,
        "chunk_id": metadata.get("chunk_id") or candidate.ref,
        "document_key": metadata.get("document_key") or source_file or candidate.source,
        "source_file": source_file,
        "heading_path": heading_path,
        "page_start": _parse_metadata_int(metadata.get("page_start")),
        "page_end": _parse_metadata_int(metadata.get("page_end")),
        "snippet": _shorten(body or candidate.text, 280),
    }


def _split_text_metadata(text: str) -> tuple[dict[str, str], str]:
    matches = list(_TEXT_METADATA_KEY_RE.finditer(text or ""))
    if not matches:
        return {}, str(text or "").strip()

    metadata: dict[str, str] = {}
    body = ""
    for index, match in enumerate(matches):
        key = match.group(1)
        value_start = match.end()
        value_end = matches[index + 1].start() if index + 1 < len(matches) else len(text)
        raw_value = text[value_start:value_end].strip(" \n\t")
        if index == len(matches) - 1:
            value_part, separator, body_tail = raw_value.partition(". ")
            metadata[key] = value_part.strip(" .\n\t")
            body = body_tail.strip() if separator else ""
        else:
            metadata[key] = raw_value.strip(" .\n\t")
    return metadata, body or str(text or "").strip()


def _parse_metadata_int(value: Any) -> int | None:
    if value is None:
        return None
    match = re.search(r"\d+", str(value))
    return int(match.group(0)) if match else None


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
