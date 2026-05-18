from __future__ import annotations

import asyncio
import inspect
import json
from collections import deque
from dataclasses import dataclass, fields, replace
from datetime import UTC, datetime, timedelta
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
        task_store_dir: str | Path | None = None,
        task_store_retention_days: int = 7,
        task_store_retention_limit: int = 5000,
    ) -> None:
        self._heartbeat_interval_seconds = heartbeat_interval_seconds
        self._max_log_lines = max_log_lines
        self._max_log_chars = max_log_chars
        self._command_factory = command_factory
        self._env_factory = env_factory
        self._hybrid_answer_runner = hybrid_answer_runner
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
    ) -> QueryTaskSnapshot:
        data_dir = self.resolve_data_dir_uri(data_dir_uri)
        effective_retrieval_query = (retrieval_query or prompt).strip()
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
        )
        request = QueryTaskRequest(
            mode=mode,
            prompt=prompt,
            index_run_id=index_run_id,
            data_dir_uri=data_dir_uri,
            data_dir=data_dir,
            retrieval_query=effective_retrieval_query,
            generation_context=generation_context,
        )
        async with self._lock:
            self._tasks[python_task_id] = snapshot
            self._task_requests[python_task_id] = request
            self._persist_snapshot(snapshot)
            asyncio.create_task(self._run_task(python_task_id))
        return replace(snapshot)

    def get_snapshot(self, python_task_id: str) -> QueryTaskSnapshot | None:
        snapshot = self._tasks.get(python_task_id)
        if snapshot is None:
            return None
        return replace(snapshot, latest_logs=list(snapshot.latest_logs or []))

    async def _update_task(self, python_task_id: str, **changes) -> None:
        async with self._lock:
            snapshot = self._tasks[python_task_id]
            updated = replace(snapshot, **changes)
            self._tasks[python_task_id] = updated
            self._persist_snapshot(updated)

    def _next_task_id(self) -> str:
        while True:
            task_id = f"qt_{utc_now():%Y%m%d_%H%M%S}_{next(self._counter):03d}"
            if task_id not in self._tasks:
                return task_id

    def _persist_snapshot(self, snapshot: QueryTaskSnapshot) -> None:
        if self._task_store is not None:
            self._task_store.save(snapshot)

    async def _run_task(self, python_task_id: str) -> None:
        logs: deque[str] = deque(maxlen=self._max_log_lines * 4)
        stdout_lines: list[str] = []
        heartbeat: asyncio.Task[None] | None = None
        try:
            request = self._task_requests[python_task_id]
            if request.mode == "hybrid_v0":
                await self._run_hybrid_task(python_task_id, request)
                return
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
            )

            heartbeat = asyncio.create_task(self._heartbeat_loop(python_task_id, process))
            stdout_task = asyncio.create_task(self._read_stream(process.stdout, python_task_id, logs, stdout_lines))
            stderr_task = asyncio.create_task(self._read_stream(process.stderr, python_task_id, logs, None))

            await asyncio.gather(stdout_task, stderr_task)
            return_code = await process.wait()

            final_logs = trim_log_tail(list(logs), max_lines=self._max_log_lines, max_chars=self._max_log_chars)
            stdout_text = "\n".join(stdout_lines)
            resolved_answer = resolve_answer_citations(stdout_text, request.data_dir)
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
        finally:
            if heartbeat is not None:
                heartbeat.cancel()

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
        resolved_answer = resolve_answer_citations(raw_answer, request.data_dir)
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
