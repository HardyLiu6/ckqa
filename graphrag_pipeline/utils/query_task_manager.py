from __future__ import annotations

import asyncio
from collections import deque
from dataclasses import dataclass, replace
from datetime import UTC, datetime
from itertools import count
from pathlib import Path
from typing import Callable


def utc_now() -> datetime:
    return datetime.now(UTC)


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
    error_message: str | None = None
    return_code: int | None = None


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
        command_factory: Callable[[str, str], list[str]],
        env_factory: Callable[[], dict[str, str]],
        cwd: str | Path,
    ) -> None:
        self._heartbeat_interval_seconds = heartbeat_interval_seconds
        self._max_log_lines = max_log_lines
        self._max_log_chars = max_log_chars
        self._command_factory = command_factory
        self._env_factory = env_factory
        self._cwd = str(cwd)
        self._counter = count(1)
        self._tasks: dict[str, QueryTaskSnapshot] = {}
        self._lock = asyncio.Lock()

    async def create_task(self, mode: str, prompt: str) -> QueryTaskSnapshot:
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
        )
        async with self._lock:
            self._tasks[python_task_id] = snapshot
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
            snapshot = self._tasks[python_task_id]
            cmd = self._command_factory(snapshot.mode, snapshot.prompt)
            process = await asyncio.create_subprocess_exec(
                *cmd,
                cwd=self._cwd,
                env=self._env_factory(),
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
            await self._update_task(
                python_task_id,
                task_status="success" if return_code == 0 else "failed",
                progress_stage="done",
                process_alive=False,
                latest_logs=final_logs,
                result_text=stdout_text if return_code == 0 else None,
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
