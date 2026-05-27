from __future__ import annotations

import asyncio
import inspect
import json
import re
from collections import deque
from concurrent.futures import ThreadPoolExecutor
from dataclasses import dataclass, field, fields, replace
from datetime import UTC, datetime, timedelta
from functools import partial
from itertools import count
from pathlib import Path
from typing import Any, Callable

from query_citation_resolver import QueryCitationSource, resolve_answer_citations


def utc_now() -> datetime:
    return datetime.now(UTC)


@dataclass(frozen=True, slots=True)
class QueryTaskRequest:
    mode: str
    prompt: str
    index_run_id: int | None
    data_dir_uri: str | None
    data_dir: Path | None
    retrieval_query: str | None = None
    generation_context: str | None = None
    query_engine_strategy: str = "cli"
    conversation_history: list[dict[str, str]] = field(default_factory=list)
    stream_response: bool = False
    stream_source: str = "none"


@dataclass(slots=True)
class QueryTaskSnapshot:
    python_task_id: str
    mode: str
    prompt: str
    task_status: str
    progress_stage: str
    process_alive: bool
    created_at: datetime
    started_at: datetime | None = None
    last_heartbeat_at: datetime | None = None
    finished_at: datetime | None = None
    latest_logs: list[str] | None = None
    result_text: str | None = None
    sources: list[dict[str, Any]] | None = None
    error_message: str | None = None
    return_code: int | None = None
    index_run_id: int | None = None
    data_dir_uri: str | None = None
    retrieval_query: str | None = None
    generation_context: str | None = None
    query_engine_strategy: str = "cli"
    conversation_history: list[dict[str, str]] = field(default_factory=list)
    history_fallback_reason: str | None = None
    history_applied: bool = False
    history_turns_used: int = 0
    streaming_enabled: bool = False
    streaming_provider: str | None = None
    streaming_fallback_reason: str | None = None
    streamed_text_length: int = 0


class QueryTaskSnapshotStore:
    """轻量文件持久化，仅用于 Python task 重启后诊断和终态回读。"""

    def __init__(
        self,
        store_dir: str | Path,
        *,
        retention_days: int = 7,
        retention_limit: int = 5000,
    ) -> None:
        self._store_dir = Path(store_dir)
        self._retention_days = retention_days if retention_days > 0 else 7
        self._retention_limit = retention_limit if retention_limit > 0 else 5000
        self._store_dir.mkdir(parents=True, exist_ok=True)

    def load(self) -> dict[str, QueryTaskSnapshot]:
        loaded: dict[str, QueryTaskSnapshot] = {}
        for path in sorted(self._store_dir.glob("*.json")):
            try:
                snapshot = _snapshot_from_json(json.loads(path.read_text(encoding="utf-8")))
            except (OSError, TypeError, ValueError, json.JSONDecodeError):
                continue
            loaded[snapshot.python_task_id] = snapshot
        self.cleanup(loaded)
        return loaded

    def save(self, snapshot: QueryTaskSnapshot) -> None:
        try:
            path = self._path_for(snapshot.python_task_id)
            path.parent.mkdir(parents=True, exist_ok=True)
            path.write_text(
                json.dumps(_snapshot_to_json(snapshot), ensure_ascii=False, indent=2),
                encoding="utf-8",
            )
        except OSError:
            # task 状态以 Java/MySQL 为事实源，Python 文件落盘失败不能阻断问答。
            return

    def cleanup(self, snapshots: dict[str, QueryTaskSnapshot] | None = None) -> None:
        snapshots = snapshots if snapshots is not None else self.load()
        now = utc_now()
        cutoff = now - timedelta(days=self._retention_days)
        ordered = sorted(
            snapshots.values(),
            key=lambda snapshot: snapshot.created_at,
            reverse=True,
        )
        keep_ids = {
            snapshot.python_task_id
            for snapshot in ordered[: self._retention_limit]
            if snapshot.created_at >= cutoff
        }
        for path in self._store_dir.glob("*.json"):
            task_id = path.stem
            if task_id in keep_ids:
                continue
            try:
                path.unlink()
            except OSError:
                continue
            snapshots.pop(task_id, None)

    def _path_for(self, python_task_id: str) -> Path:
        return self._store_dir / f"{python_task_id}.json"


def trim_log_tail(lines: list[str], *, max_lines: int, max_chars: int) -> list[str]:
    trimmed = list(lines[-max_lines:])
    while trimmed and len("\n".join(trimmed)) > max_chars:
        trimmed.pop(0)
    if not trimmed and lines:
        return [lines[-1][-max_chars:]]
    return trimmed


def merge_log_tail(
    preserved_lines: list[str],
    stream_lines: list[str],
    *,
    max_lines: int,
    max_chars: int,
) -> list[str]:
    """合并历史策略诊断与 CLI 输出，优先保留回退原因。"""
    preserved = list(preserved_lines)
    if len(preserved) >= max_lines:
        return trim_log_tail(preserved, max_lines=max_lines, max_chars=max_chars)
    stream_tail = list(stream_lines[-(max_lines - len(preserved)):])
    merged = preserved + stream_tail
    while merged and len("\n".join(merged)) > max_chars:
        if len(merged) > len(preserved):
            merged.pop(len(preserved))
        else:
            merged.pop(0)
            preserved = preserved[1:]
    return merged


class QueryTaskManager:
    def __init__(
        self,
        *,
        heartbeat_interval_seconds: float = 5.0,
        max_log_lines: int = 20,
        max_log_chars: int = 4000,
        command_factory: Callable[[QueryTaskRequest], list[str]],
        env_factory: Callable[[QueryTaskRequest], dict[str, str]],
        cwd: str | Path,
        build_runs_root: str | Path | None = None,
        hybrid_answer_runner: Callable[[QueryTaskRequest], Any] | None = None,
        history_adapter_factory: Callable[[], Any] | None = None,
        native_streaming_runner: Callable[[QueryTaskRequest], Any] | None = None,
        task_store_dir: str | Path | None = None,
        task_store_retention_days: int = 7,
        task_store_retention_limit: int = 5000,
        stream_replay_max_chars: int = 64000,
    ) -> None:
        self._heartbeat_interval_seconds = heartbeat_interval_seconds
        self._max_log_lines = max_log_lines
        self._max_log_chars = max_log_chars
        self._command_factory = command_factory
        self._env_factory = env_factory
        self._hybrid_answer_runner = hybrid_answer_runner
        self._history_adapter_factory = history_adapter_factory
        self._native_streaming_runner = native_streaming_runner
        self._cwd = str(cwd)
        self._build_runs_root = Path(build_runs_root).resolve() if build_runs_root is not None else None
        self._counter = count(1)
        self._task_store = QueryTaskSnapshotStore(
            task_store_dir,
            retention_days=task_store_retention_days,
            retention_limit=task_store_retention_limit,
        ) if task_store_dir is not None else None
        self._tasks: dict[str, QueryTaskSnapshot] = self._task_store.load() if self._task_store is not None else {}
        self._task_requests: dict[str, QueryTaskRequest] = {}
        self._task_event_replay: dict[str, list[dict[str, Any]]] = {}
        self._task_event_replay_chars: dict[str, int] = {}
        self._task_event_subscribers: dict[str, set[asyncio.Queue[dict[str, Any]]]] = {}
        self._stream_replay_max_chars = max(1000, stream_replay_max_chars)
        self._lock = asyncio.Lock()

    async def create_task(
        self,
        mode: str,
        prompt: str,
        *,
        index_run_id: int | None = None,
        data_dir_uri: str | None = None,
        retrieval_query: str | None = None,
        generation_context: str | None = None,
        query_engine_strategy: str = "cli",
        conversation_history: list[dict[str, str]] | None = None,
        stream_response: bool = False,
        stream_source: str = "none",
    ) -> QueryTaskSnapshot:
        data_dir = self.resolve_data_dir_uri(data_dir_uri)
        effective_retrieval_query = (retrieval_query or prompt).strip()
        effective_strategy = _normalize_query_engine_strategy(query_engine_strategy)
        normalized_history = _normalize_conversation_history_payload(conversation_history)
        effective_stream_source = _normalize_stream_source(stream_source)
        streaming_enabled = bool(stream_response) and effective_stream_source != "none"
        python_task_id = self._next_task_id()
        snapshot = QueryTaskSnapshot(
            python_task_id=python_task_id,
            mode=mode,
            prompt=prompt,
            retrieval_query=effective_retrieval_query,
            generation_context=generation_context,
            task_status="pending",
            progress_stage="queued",
            process_alive=False,
            created_at=utc_now(),
            latest_logs=[],
            index_run_id=index_run_id,
            data_dir_uri=data_dir_uri,
            query_engine_strategy=effective_strategy,
            conversation_history=normalized_history,
            streaming_enabled=streaming_enabled,
            streaming_provider=effective_stream_source if streaming_enabled else None,
        )
        request = QueryTaskRequest(
            mode=mode,
            prompt=prompt,
            index_run_id=index_run_id,
            data_dir_uri=data_dir_uri,
            data_dir=data_dir,
            retrieval_query=effective_retrieval_query,
            generation_context=generation_context,
            query_engine_strategy=effective_strategy,
            conversation_history=normalized_history,
            stream_response=streaming_enabled,
            stream_source=effective_stream_source,
        )
        async with self._lock:
            self._tasks[python_task_id] = snapshot
            self._task_requests[python_task_id] = request
            self._persist_snapshot(snapshot)
            self._publish_event(python_task_id, "status", _snapshot_status_payload(snapshot))
            asyncio.create_task(self._run_task(python_task_id))
        return replace(snapshot)

    def get_snapshot(self, python_task_id: str) -> QueryTaskSnapshot | None:
        snapshot = self._tasks.get(python_task_id)
        if snapshot is None:
            return None
        return replace(snapshot, latest_logs=list(snapshot.latest_logs or []))

    def subscribe_events(self, python_task_id: str) -> tuple[list[dict[str, Any]], asyncio.Queue[dict[str, Any]], Callable[[], None]]:
        if python_task_id not in self._tasks:
            raise KeyError(python_task_id)
        queue: asyncio.Queue[dict[str, Any]] = asyncio.Queue()
        self._task_event_subscribers.setdefault(python_task_id, set()).add(queue)
        replay = list(self._task_event_replay.get(python_task_id, []))

        def unsubscribe() -> None:
            subscribers = self._task_event_subscribers.get(python_task_id)
            if subscribers is not None:
                subscribers.discard(queue)
                if not subscribers:
                    self._task_event_subscribers.pop(python_task_id, None)

        return replay, queue, unsubscribe

    async def _update_task(self, python_task_id: str, **changes) -> None:
        async with self._lock:
            snapshot = self._tasks[python_task_id]
            updated = replace(snapshot, **changes)
            self._tasks[python_task_id] = updated
            self._persist_snapshot(updated)
            self._publish_event(python_task_id, "status", _snapshot_status_payload(updated))

    def _next_task_id(self) -> str:
        while True:
            task_id = f"qt_{utc_now():%Y%m%d_%H%M%S}_{next(self._counter):03d}"
            if task_id not in self._tasks:
                return task_id

    def _persist_snapshot(self, snapshot: QueryTaskSnapshot) -> None:
        if self._task_store is not None:
            self._task_store.save(snapshot)

    def _publish_event(self, python_task_id: str, event: str, data: dict[str, Any]) -> None:
        payload = {"event": event, "data": data}
        replay = self._task_event_replay.setdefault(python_task_id, [])
        if event == "status":
            for existing in list(replay):
                if existing.get("event") == "status":
                    replay.remove(existing)
                    self._task_event_replay_chars[python_task_id] = self._task_event_replay_chars.get(
                        python_task_id,
                        0,
                    ) - len(json.dumps(existing, ensure_ascii=False))
        replay.append(payload)
        self._task_event_replay_chars[python_task_id] = self._task_event_replay_chars.get(python_task_id, 0) + len(
            json.dumps(payload, ensure_ascii=False)
        )
        while replay and self._task_event_replay_chars.get(python_task_id, 0) > self._stream_replay_max_chars:
            removed = replay.pop(0)
            self._task_event_replay_chars[python_task_id] -= len(json.dumps(removed, ensure_ascii=False))

        for queue in list(self._task_event_subscribers.get(python_task_id, set())):
            queue.put_nowait(payload)

    async def _run_task(self, python_task_id: str) -> None:
        logs: deque[str] = deque(maxlen=self._max_log_lines * 4)
        try:
            request = self._task_requests[python_task_id]
            if request.stream_response:
                fallback_logs = await self._try_run_native_streaming_task(python_task_id, request)
                if fallback_logs is None:
                    return
                logs.extend(fallback_logs)
            if request.mode == "hybrid_v0":
                await self._run_hybrid_task(python_task_id, request)
                return
            if request.mode == "local" and request.query_engine_strategy == "local_history":
                fallback_logs = await self._try_run_local_history_task(python_task_id, request)
                if fallback_logs is None:
                    return
                logs.extend(fallback_logs)
            await self._run_cli_task(python_task_id, request, initial_logs=list(logs))
        except Exception as exc:  # noqa: BLE001 - 任务管理器需要把运行时异常转成可观测终态
            latest_logs = trim_log_tail(list(logs), max_lines=self._max_log_lines, max_chars=self._max_log_chars)
            await self._update_task(
                python_task_id,
                task_status="failed",
                progress_stage="done",
                process_alive=False,
                latest_logs=latest_logs,
                error_message=str(exc),
                finished_at=utc_now(),
            )
            self._publish_event(
                python_task_id,
                "error",
                {"taskStatus": "failed", "message": str(exc)},
            )

    async def _run_cli_task(
        self,
        python_task_id: str,
        request: QueryTaskRequest,
        *,
        initial_logs: list[str] | None = None,
    ) -> None:
        logs: deque[str] = deque(maxlen=self._max_log_lines * 4)
        stdout_lines: list[str] = []
        heartbeat: asyncio.Task[None] | None = None
        started_log = f"started graphrag query --method {request.mode}"
        preserved_logs = trim_log_tail(
            [*(initial_logs or []), started_log],
            max_lines=self._max_log_lines,
            max_chars=self._max_log_chars,
        )
        try:
            cmd = self._command_factory(request)
            process = await asyncio.create_subprocess_exec(
                *cmd,
                cwd=self._cwd,
                env=self._env_factory(request),
                stdout=asyncio.subprocess.PIPE,
                stderr=asyncio.subprocess.PIPE,
            )
            await self._update_task(
                python_task_id,
                task_status="running",
                progress_stage="running",
                process_alive=True,
                started_at=utc_now(),
                # 任务进入 running 时立即写入首次心跳，后续定时器继续刷新。
                last_heartbeat_at=utc_now(),
                latest_logs=preserved_logs,
            )

            heartbeat = asyncio.create_task(self._heartbeat_loop(python_task_id, process))
            stdout_task = asyncio.create_task(self._read_stream(process.stdout, python_task_id, logs, stdout_lines))
            stderr_task = asyncio.create_task(self._read_stream(process.stderr, python_task_id, logs, None))

            await asyncio.gather(stdout_task, stderr_task)
            return_code = await process.wait()

            finished_log = f"finished graphrag query --method {request.mode} exit_code={return_code}"
            final_logs = merge_log_tail(
                preserved_logs,
                list(logs),
                max_lines=self._max_log_lines,
                max_chars=self._max_log_chars,
            )
            final_logs = trim_log_tail(
                [*final_logs, finished_log],
                max_lines=self._max_log_lines,
                max_chars=self._max_log_chars,
            )
            stdout_text = "\n".join(stdout_lines)
            resolved_answer = resolve_answer_citations(
                stdout_text,
                request.data_dir,
                mode=request.mode,
                fallback_query=request.retrieval_query or request.prompt,
            )
            await self._update_task(
                python_task_id,
                task_status="success" if return_code == 0 else "failed",
                progress_stage="done",
                process_alive=False,
                latest_logs=final_logs,
                result_text=resolved_answer.display_text if return_code == 0 else None,
                sources=_serialize_sources(resolved_answer.sources) if return_code == 0 else None,
                error_message=None if return_code == 0 else f"graphrag query exit code={return_code}",
                return_code=return_code,
                finished_at=utc_now(),
            )
            if return_code == 0:
                self._publish_event(
                    python_task_id,
                    "sources",
                    {"sources": _serialize_sources(resolved_answer.sources)},
                )
                self._publish_event(python_task_id, "done", {"taskStatus": "success"})
            else:
                self._publish_event(
                    python_task_id,
                    "error",
                    {"taskStatus": "failed", "message": f"graphrag query exit code={return_code}"},
                )
        finally:
            if heartbeat is not None:
                heartbeat.cancel()

    async def _try_run_native_streaming_task(
        self,
        python_task_id: str,
        request: QueryTaskRequest,
    ) -> list[str] | None:
        started_log = f"started native streaming query task provider={request.stream_source}"
        await self._update_task(
            python_task_id,
            task_status="running",
            progress_stage="streaming",
            process_alive=True,
            started_at=utc_now(),
            last_heartbeat_at=utc_now(),
            latest_logs=[started_log],
            streaming_enabled=True,
            streaming_provider=request.stream_source,
        )
        if self._native_streaming_runner is None:
            return await self._record_streaming_fallback(
                python_task_id,
                started_log,
                "native streaming runner is not configured",
            )
        raw_chunks: list[str] = []
        visible_chunks: list[str] = []
        response_sources: list[dict[str, Any]] = []
        delta_filter = _DataCitationDeltaFilter()
        latest_progress_log = "waiting first streaming chunk"
        try:
            stream_result = self._native_streaming_runner(request)
            if inspect.isawaitable(stream_result):
                stream_result = await stream_result
            async for chunk in stream_result:
                raw_text = _stream_chunk_text(chunk)
                chunk_sources = _stream_chunk_sources(chunk)
                if chunk_sources:
                    response_sources = chunk_sources
                if not raw_text:
                    continue
                raw_chunks.append(raw_text)
                visible = delta_filter.feed(raw_text)
                if visible:
                    visible_chunks.append(visible)
                    latest_progress_log = f"streamed chunk count={len(visible_chunks)}"
                    self._publish_event(python_task_id, "delta", {"text": visible})
                    await self._update_task(
                        python_task_id,
                        last_heartbeat_at=utc_now(),
                        latest_logs=trim_log_tail(
                            [started_log, latest_progress_log],
                            max_lines=self._max_log_lines,
                            max_chars=self._max_log_chars,
                        ),
                        streamed_text_length=sum(len(part) for part in visible_chunks),
                    )
            tail = delta_filter.finish()
            if tail:
                visible_chunks.append(tail)
                latest_progress_log = f"streamed chunk count={len(visible_chunks)}"
                self._publish_event(python_task_id, "delta", {"text": tail})

            raw_answer = "".join(raw_chunks)
            resolved_answer = resolve_answer_citations(
                raw_answer,
                request.data_dir,
                mode=request.mode,
                fallback_query=request.retrieval_query or request.prompt,
            )
            final_sources = _serialize_sources(resolved_answer.sources) if resolved_answer.sources else response_sources
            finished_log = "finished native streaming query task"
            await self._update_task(
                python_task_id,
                task_status="success",
                progress_stage="done",
                process_alive=False,
                latest_logs=trim_log_tail(
                    [started_log, latest_progress_log, finished_log],
                    max_lines=self._max_log_lines,
                    max_chars=self._max_log_chars,
                ),
                result_text=resolved_answer.display_text,
                sources=final_sources,
                error_message=None,
                return_code=0,
                finished_at=utc_now(),
                streamed_text_length=sum(len(part) for part in visible_chunks),
            )
            self._publish_event(python_task_id, "sources", {"sources": final_sources})
            self._publish_event(python_task_id, "done", {"taskStatus": "success"})
            return None
        except Exception as exc:  # noqa: BLE001 - native streaming 失败时回退原有非流式链路
            return await self._record_streaming_fallback(python_task_id, started_log, str(exc))

    async def _record_streaming_fallback(
        self,
        python_task_id: str,
        started_log: str,
        reason: str,
    ) -> list[str]:
        fallback_reason = reason or "native streaming unavailable"
        fallback_log = f"fallback native streaming to non-streaming task: {fallback_reason}"
        logs = [started_log, fallback_log]
        await self._update_task(
            python_task_id,
            process_alive=False,
            latest_logs=logs,
            streaming_fallback_reason=fallback_reason,
            streamed_text_length=0,
        )
        self._publish_event(python_task_id, "status", {"streamingFallbackReason": fallback_reason})
        return logs

    async def _try_run_local_history_task(
        self,
        python_task_id: str,
        request: QueryTaskRequest,
    ) -> list[str] | None:
        started_log = "started local_history query task"
        await self._update_task(
            python_task_id,
            task_status="running",
            progress_stage="running",
            process_alive=True,
            started_at=utc_now(),
            last_heartbeat_at=utc_now(),
            latest_logs=[started_log],
        )
        try:
            if self._history_adapter_factory is None:
                return await self._record_history_fallback(
                    python_task_id,
                    started_log,
                    "local_history adapter is not configured",
                )
            adapter = self._history_adapter_factory()
            readiness = adapter.readiness(request.data_dir_uri)
            if inspect.isawaitable(readiness):
                readiness = await readiness
            if not bool(getattr(readiness, "supported", False)):
                return await self._record_history_fallback(
                    python_task_id,
                    started_log,
                    _history_readiness_fallback_reason(readiness),
                )

            query_call = partial(
                adapter.query,
                data_dir_uri=request.data_dir_uri,
                query=request.retrieval_query or request.prompt,
                conversation_history=request.conversation_history,
                max_turns=None,
                user_turns_only=True,
                return_candidate_context=False,
            )
            with ThreadPoolExecutor(max_workers=1, thread_name_prefix="ckqa-local-history") as executor:
                result = await asyncio.get_running_loop().run_in_executor(executor, query_call)
            if inspect.isawaitable(result):
                result = await result
            if not bool(getattr(result, "supported", False)):
                return await self._record_history_fallback(
                    python_task_id,
                    started_log,
                    _history_result_fallback_reason(result),
                )

            finished_log = "finished local_history query task"
            answer = getattr(result, "answer", None) or getattr(result, "raw_answer", None) or ""
            await self._update_task(
                python_task_id,
                task_status="success",
                progress_stage="done",
                process_alive=False,
                latest_logs=[started_log, finished_log],
                result_text=str(answer),
                sources=list(getattr(result, "sources", []) or []),
                error_message=None,
                return_code=0,
                finished_at=utc_now(),
                history_fallback_reason=None,
                history_applied=bool(getattr(result, "history_applied", False)),
                history_turns_used=int(getattr(result, "history_turns_used", 0) or 0),
            )
            self._publish_event(
                python_task_id,
                "sources",
                {"sources": list(getattr(result, "sources", []) or [])},
            )
            self._publish_event(python_task_id, "done", {"taskStatus": "success"})
            return None
        except Exception as exc:  # noqa: BLE001 - history 策略失败时必须回退 CLI local
            return await self._record_history_fallback(python_task_id, started_log, str(exc))

    async def _record_history_fallback(
        self,
        python_task_id: str,
        started_log: str,
        reason: str,
    ) -> list[str]:
        fallback_reason = reason or "local_history unavailable"
        fallback_log = f"fallback local_history to cli: {fallback_reason}"
        logs = [started_log, fallback_log]
        await self._update_task(
            python_task_id,
            process_alive=False,
            latest_logs=logs,
            history_fallback_reason=fallback_reason,
            history_applied=False,
            history_turns_used=0,
        )
        return logs

    async def _run_hybrid_task(self, python_task_id: str, request: QueryTaskRequest) -> None:
        if self._hybrid_answer_runner is None:
            raise RuntimeError("hybrid_v0 task runner is not configured")
        await self._update_task(
            python_task_id,
            task_status="running",
            progress_stage="running",
            process_alive=True,
            started_at=utc_now(),
            last_heartbeat_at=utc_now(),
            latest_logs=["started hybrid_v0 query task"],
        )
        result = self._hybrid_answer_runner(request)
        if inspect.isawaitable(result):
            result = await result
        raw_answer = str(getattr(result, "answer", "") or "")
        resolved_answer = resolve_answer_citations(
            raw_answer,
            request.data_dir,
            mode=request.mode,
            fallback_query=request.retrieval_query or request.prompt,
        )
        hybrid_sources = _serialize_hybrid_sources(list(getattr(result, "sources", []) or []))
        response_sources = _serialize_sources(resolved_answer.sources) if resolved_answer.sources else hybrid_sources
        await self._update_task(
            python_task_id,
            task_status="success",
            progress_stage="done",
            process_alive=False,
            latest_logs=["started hybrid_v0 query task", "finished hybrid_v0 query task"],
            result_text=resolved_answer.display_text,
            sources=response_sources,
            error_message=None,
            return_code=0,
            finished_at=utc_now(),
        )
        self._publish_event(python_task_id, "sources", {"sources": response_sources})
        self._publish_event(python_task_id, "done", {"taskStatus": "success"})

    def _resolve_data_dir(self, data_dir_uri: str | None) -> Path | None:
        return self.resolve_data_dir_uri(data_dir_uri)

    def resolve_data_dir_uri(self, data_dir_uri: str | None) -> Path | None:
        if not data_dir_uri:
            return None
        if self._build_runs_root is None:
            raise ValueError("GRAPHRAG_BUILD_RUNS_ROOT 未配置")

        return resolve_build_run_data_dir_uri(data_dir_uri, self._build_runs_root)

    async def _heartbeat_loop(self, python_task_id: str, process) -> None:
        while process.returncode is None:
            await asyncio.sleep(self._heartbeat_interval_seconds)
            if process.returncode is None:
                await self._update_task(
                    python_task_id,
                    last_heartbeat_at=utc_now(),
                )

    async def _read_stream(
        self,
        stream,
        python_task_id: str,
        logs: deque[str],
        captured_lines: list[str] | None,
    ) -> None:
        while True:
            line = await stream.readline()
            if not line:
                return
            text = line.decode("utf-8", errors="ignore").rstrip("\r\n")
            if not text.strip():
                continue
            logs.append(text)
            if captured_lines is not None:
                captured_lines.append(text)
            await self._update_task(
                python_task_id,
                latest_logs=trim_log_tail(list(logs), max_lines=self._max_log_lines, max_chars=self._max_log_chars),
                last_heartbeat_at=utc_now(),
            )


def _serialize_sources(sources: list[QueryCitationSource]) -> list[dict[str, Any]]:
    return [source.to_dict() for source in sources]


def _snapshot_to_json(snapshot: QueryTaskSnapshot) -> dict[str, Any]:
    payload: dict[str, Any] = {}
    for field in fields(QueryTaskSnapshot):
        value = getattr(snapshot, field.name)
        payload[_camelize(field.name)] = _json_value(value)
    return payload


def _snapshot_from_json(payload: dict[str, Any]) -> QueryTaskSnapshot:
    kwargs: dict[str, Any] = {}
    for field in fields(QueryTaskSnapshot):
        raw_value = payload.get(_camelize(field.name), payload.get(field.name))
        if field.name in {
            "created_at",
            "started_at",
            "last_heartbeat_at",
            "finished_at",
        }:
            kwargs[field.name] = _parse_datetime(raw_value)
        else:
            kwargs[field.name] = raw_value
    if kwargs.get("created_at") is None:
        raise ValueError("createdAt is required")
    kwargs["latest_logs"] = list(kwargs.get("latest_logs") or [])
    kwargs["sources"] = list(kwargs.get("sources") or []) if kwargs.get("sources") is not None else None
    kwargs["query_engine_strategy"] = _normalize_query_engine_strategy(kwargs.get("query_engine_strategy") or "cli")
    kwargs["conversation_history"] = _normalize_conversation_history_payload(kwargs.get("conversation_history"))
    kwargs["history_fallback_reason"] = kwargs.get("history_fallback_reason") or None
    kwargs["history_applied"] = bool(kwargs.get("history_applied") or False)
    kwargs["streaming_enabled"] = bool(kwargs.get("streaming_enabled") or False)
    kwargs["streaming_provider"] = kwargs.get("streaming_provider") or None
    kwargs["streaming_fallback_reason"] = kwargs.get("streaming_fallback_reason") or None
    try:
        kwargs["history_turns_used"] = int(kwargs.get("history_turns_used") or 0)
    except (TypeError, ValueError):
        kwargs["history_turns_used"] = 0
    try:
        kwargs["streamed_text_length"] = int(kwargs.get("streamed_text_length") or 0)
    except (TypeError, ValueError):
        kwargs["streamed_text_length"] = 0
    # 进程重启后旧 snapshot 只用于诊断和回读，不能继续声称子进程仍存活。
    kwargs["process_alive"] = False
    return QueryTaskSnapshot(**kwargs)


def _json_value(value: Any) -> Any:
    if isinstance(value, datetime):
        return value.isoformat()
    return value


def _parse_datetime(value: Any) -> datetime | None:
    if value is None:
        return None
    if isinstance(value, datetime):
        return value
    parsed = datetime.fromisoformat(str(value).replace("Z", "+00:00"))
    return parsed if parsed.tzinfo is not None else parsed.replace(tzinfo=UTC)


def _camelize(name: str) -> str:
    parts = name.split("_")
    return parts[0] + "".join(part.title() for part in parts[1:])


def _normalize_query_engine_strategy(value: Any) -> str:
    strategy = str(value or "cli").strip().casefold()
    if strategy not in {"cli", "local_history"}:
        raise ValueError(f"非法 query_engine_strategy: {strategy}")
    return strategy


def _normalize_stream_source(value: Any) -> str:
    source = str(value or "none").strip().casefold()
    if source not in {"none", "native_graphrag"}:
        raise ValueError(f"非法 stream_source: {source}")
    return source


def _normalize_conversation_history_payload(value: Any) -> list[dict[str, str]]:
    normalized: list[dict[str, str]] = []
    for turn in value or []:
        if not isinstance(turn, dict):
            raise ValueError("conversation_history item must be object")
        role = str(turn.get("role", "")).strip().casefold()
        content = str(turn.get("content", "")).strip()
        if role not in {"user", "assistant"}:
            raise ValueError(f"非法 conversation_history role: {role}")
        if not content:
            continue
        normalized.append({"role": role, "content": content})
    return normalized


def _snapshot_status_payload(snapshot: QueryTaskSnapshot) -> dict[str, Any]:
    return {
        "pythonTaskId": snapshot.python_task_id,
        "taskStatus": snapshot.task_status,
        "progressStage": snapshot.progress_stage,
        "processAlive": snapshot.process_alive,
        "lastHeartbeatAt": _json_value(snapshot.last_heartbeat_at),
        "startedAt": _json_value(snapshot.started_at),
        "finishedAt": _json_value(snapshot.finished_at),
        "streamingEnabled": snapshot.streaming_enabled,
        "streamingProvider": snapshot.streaming_provider,
        "streamingFallbackReason": snapshot.streaming_fallback_reason,
        "streamedTextLength": snapshot.streamed_text_length,
    }


def _stream_chunk_text(chunk: Any) -> str:
    if isinstance(chunk, str):
        return chunk
    if isinstance(chunk, dict):
        return str(chunk.get("text") or "")
    return str(getattr(chunk, "text", "") or "")


def _stream_chunk_sources(chunk: Any) -> list[dict[str, Any]]:
    sources: Any
    if isinstance(chunk, dict):
        sources = chunk.get("sources")
    else:
        sources = getattr(chunk, "sources", None)
    return list(sources or []) if isinstance(sources, list) else []


class _DataCitationDeltaFilter:
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
        tail = re.sub(r"\[Data:\s*[^\]]*\]", "", self._buffer, flags=re.IGNORECASE)
        if tail.casefold().startswith(self._PREFIX.casefold()):
            tail = ""
        self._buffer = ""
        return tail


def _partial_prefix_len(text: str, prefix: str) -> int:
    max_len = min(len(text), len(prefix) - 1)
    lower_text = text.casefold()
    lower_prefix = prefix.casefold()
    for length in range(max_len, 0, -1):
        if lower_prefix.startswith(lower_text[-length:]):
            return length
    return 0


def _history_readiness_fallback_reason(readiness: Any) -> str:
    error_message = str(getattr(readiness, "error_message", "") or "").strip()
    if error_message:
        return error_message
    missing = [str(item) for item in list(getattr(readiness, "missing", []) or []) if str(item).strip()]
    if missing:
        return "missing artifacts: " + ", ".join(missing)
    status = str(getattr(readiness, "status", "") or "not_ready").strip()
    return f"local_history readiness {status}"


def _history_result_fallback_reason(result: Any) -> str:
    error_message = str(getattr(result, "error_message", "") or "").strip()
    if error_message:
        return error_message
    return "local_history query did not return a supported result"


def _serialize_hybrid_sources(sources: list[Any]) -> list[dict[str, Any]]:
    serialized: list[dict[str, Any]] = []
    for index, source in enumerate(sources, start=1):
        raw_metadata = getattr(source, "metadata", {}) or {}
        metadata = raw_metadata if isinstance(raw_metadata, dict) else {}
        ref = str(getattr(source, "ref", "") or metadata.get("ref") or "").strip()
        text = str(getattr(source, "text", "") or metadata.get("text") or "").strip()
        source_name = str(getattr(source, "source", "") or metadata.get("source") or "hybrid_v0").strip()
        source_type = _normalize_hybrid_source_type(source_name)
        rank = metadata.get("rank")
        serialized.append(
            {
                "rank": int(rank) if isinstance(rank, int) else index,
                "kind": source_type,
                "source_type": source_type,
                "ref": ref,
                "chunk_id": ref,
                "document_key": ref,
                "source_file": source_name,
                "heading_path": metadata.get("heading_path") if isinstance(metadata, dict) else None,
                "page_start": metadata.get("page_start") if isinstance(metadata, dict) else None,
                "page_end": metadata.get("page_end") if isinstance(metadata, dict) else None,
                "snippet": text,
            }
        )
    return serialized


def resolve_build_run_data_dir_uri(data_dir_uri: str, build_runs_root: str | Path) -> Path:
    data_dir_path = Path(data_dir_uri)
    if data_dir_path.is_absolute():
        raise ValueError("dataDirUri 超出允许的构建根目录")

    root = Path(build_runs_root).resolve()
    resolved = (root / data_dir_path).resolve()
    if root not in (resolved, *resolved.parents):
        raise ValueError("dataDirUri 超出允许的构建根目录")
    return resolved


def _normalize_hybrid_source_type(source_name: str) -> str:
    normalized = source_name.strip().casefold().replace("-", "_")
    if "bm25" in normalized:
        return "bm25"
    if "basic" in normalized:
        return "basic_citation"
    if "fusion" in normalized or "fused" in normalized or "hybrid" in normalized:
        return "fusion"
    return "fusion"
