from __future__ import annotations

import asyncio
from collections import deque
from dataclasses import dataclass, replace
from datetime import UTC, datetime
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
    ) -> None:
        self._heartbeat_interval_seconds = heartbeat_interval_seconds
        self._max_log_lines = max_log_lines
        self._max_log_chars = max_log_chars
        self._command_factory = command_factory
        self._env_factory = env_factory
        self._cwd = str(cwd)
        self._build_runs_root = Path(build_runs_root).resolve() if build_runs_root is not None else None
        self._counter = count(1)
        self._tasks: dict[str, QueryTaskSnapshot] = {}
        self._task_requests: dict[str, QueryTaskRequest] = {}
        self._lock = asyncio.Lock()

    async def create_task(
        self,
        mode: str,
        prompt: str,
        *,
        index_run_id: int | None = None,
        data_dir_uri: str | None = None,
    ) -> QueryTaskSnapshot:
        data_dir = self._resolve_data_dir(data_dir_uri)
        python_task_id = f"qt_{utc_now():%Y%m%d_%H%M%S}_{next(self._counter):03d}"
        snapshot = QueryTaskSnapshot(
            python_task_id=python_task_id,
            mode=mode,
            prompt=prompt,
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
        )
        async with self._lock:
            self._tasks[python_task_id] = snapshot
            self._task_requests[python_task_id] = request
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
            self._tasks[python_task_id] = replace(snapshot, **changes)

    async def _run_task(self, python_task_id: str) -> None:
        logs: deque[str] = deque(maxlen=self._max_log_lines * 4)
        stdout_lines: list[str] = []
        heartbeat: asyncio.Task[None] | None = None
        try:
            request = self._task_requests[python_task_id]
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

    def _resolve_data_dir(self, data_dir_uri: str | None) -> Path | None:
        if not data_dir_uri:
            return None
        if self._build_runs_root is None:
            raise ValueError("GRAPHRAG_BUILD_RUNS_ROOT 未配置")

        data_dir_path = Path(data_dir_uri)
        if data_dir_path.is_absolute():
            raise ValueError("dataDirUri 超出允许的构建根目录")

        root = self._build_runs_root.resolve()
        resolved = (root / data_dir_path).resolve()
        if root not in (resolved, *resolved.parents):
            raise ValueError("dataDirUri 超出允许的构建根目录")
        return resolved

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
