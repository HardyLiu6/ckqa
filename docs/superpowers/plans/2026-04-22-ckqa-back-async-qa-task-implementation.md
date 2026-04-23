# CKQA Back Async QA Task Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 把现有同步问答链路改成“提交即返回 taskId、后台持续轮询 Python 任务状态、最终落库 assistant 消息”的异步任务闭环，并清理此前偏离正确方向的 global 调参残留代码。

**Architecture:** 保持现有 `backend/ckqa-back` 与 `graphrag_pipeline` 的总体分层不变。Python 侧新增一个进程内 `QueryTaskManager` 托管真实 `graphrag query` 子进程并主动刷新心跳；Java 侧新增 GraphRAG 任务客户端、后台任务 worker 与基于 `qa_retrieval_logs` 的任务持久化层，对外提供提交、查询详情、消息列表摘要三类接口。

**Tech Stack:** Python 3.10+, FastAPI, asyncio, Spring Boot 4.0.5, Java 21, MyBatis-Plus 3.5.16, RestClient, JUnit 5, Mockito, MockMvc, MySQL

---

## 范围判断

这份 spec 虽然同时涉及 Python、Java、SQL 三块，但它们是同一个“异步问答任务闭环”的强耦合子系统：只做其中一块都无法形成可冒烟的最小可运行版本，所以继续使用一份 implementation plan 即可，不再拆成多个独立计划。

## 文件结构与职责

以下命令默认都在 worktree 根目录执行，也就是：

```bash
cd /home/sunlight/Projects/ckqa/.worktrees/ckqa-back-phase1
```

### Python 任务管理与 API

- Create: `graphrag_pipeline/utils/query_task_manager.py`
  负责管理 `pythonTaskId`、子进程生命周期、日志 tail、心跳刷新与任务快照查询。
- Modify: `graphrag_pipeline/utils/main.py`
  负责注入 `QueryTaskManager`、新增 `/v1/query-tasks` 与 `/v1/query-tasks/{taskId}`，并保留现有 `/v1/chat/completions` 兼容入口。
- Modify: `graphrag_pipeline/utils/api_runtime_config.py`
  负责移除错误方向的 `GRAPHRAG_GLOBAL_SEARCH_*` 运行时参数，只保留真正需要的 API 启动配置。
- Modify: `graphrag_pipeline/settings.yaml`
  负责删除 `dynamic_search_use_summary` / `dynamic_search_max_level` 这两个仅服务于错误调参方向的配置。

### Python 测试

- Create: `graphrag_pipeline/tests/test_query_task_manager.py`
  覆盖任务生命周期、心跳刷新、日志截断、失败状态。
- Create: `graphrag_pipeline/tests/test_query_task_api.py`
  覆盖 `/v1/query-tasks` 提交与查询接口。
- Modify: `graphrag_pipeline/tests/test_main_cli_mode.py`
  保留 CLI 查询与 loopback `NO_PROXY` 断言，移除对 global runtime strategy 参数的断言。
- Modify: `graphrag_pipeline/tests/test_api_runtime_config.py`
  移除已废弃的 `GRAPHRAG_GLOBAL_SEARCH_*` 断言。
- Modify: `graphrag_pipeline/tests/test_runtime_defaults.py`
  改成断言 `settings.yaml` 不再包含错误方向的 dynamic global 配置。

### Java 集成与任务编排

- Create: `backend/ckqa-back/src/main/java/org/ysu/ckqaback/integration/graphrag/GraphRagTaskClient.java`
  负责调用 Python `POST /v1/query-tasks` 与 `GET /v1/query-tasks/{taskId}`。
- Create: `backend/ckqa-back/src/main/java/org/ysu/ckqaback/integration/graphrag/GraphRagTaskCreateResult.java`
  负责承接 Python 提交任务响应。
- Create: `backend/ckqa-back/src/main/java/org/ysu/ckqaback/integration/graphrag/GraphRagTaskSnapshot.java`
  负责承接 Python 任务状态快照。
- Create: `backend/ckqa-back/src/main/java/org/ysu/ckqaback/config/AsyncTaskExecutorConfig.java`
  负责定义问答后台任务线程池。
- Create: `backend/ckqa-back/src/main/java/org/ysu/ckqaback/qa/QaTaskWorker.java`
  负责后台创建 Python 任务、轮询状态、镜像日志心跳、写最终 assistant 消息。
- Modify: `backend/ckqa-back/src/main/java/org/ysu/ckqaback/qa/QaWorkflowService.java`
  负责提交任务、查询任务详情、拼装消息列表任务摘要。
- Create: `backend/ckqa-back/src/main/java/org/ysu/ckqaback/qa/dto/QaTaskSubmissionResponse.java`
  负责 `POST /qa-sessions/{id}/messages` 的异步受理响应。
- Create: `backend/ckqa-back/src/main/java/org/ysu/ckqaback/qa/dto/QaTaskDetailResponse.java`
  负责 `GET /qa-sessions/{sessionId}/tasks/{taskId}` 的详情响应。
- Modify: `backend/ckqa-back/src/main/java/org/ysu/ckqaback/qa/dto/QaMessageResponse.java`
  负责在消息列表中补充 `taskStatus` 与 `progressStage`。
- Modify: `backend/ckqa-back/src/main/java/org/ysu/ckqaback/controller/QaSessionsController.java`
  负责改造提交接口返回值，并新增任务详情接口。
- Modify: `backend/ckqa-back/src/main/java/org/ysu/ckqaback/integration/config/CkqaIntegrationProperties.java`
  负责新增 `query-task-interval-seconds` 与 `query-task-stale-seconds` 配置。
- Modify: `backend/ckqa-back/src/main/resources/application.properties`
  负责暴露新的轮询与 stale 阈值环境变量。

### Java 持久化与数据库

- Modify: `backend/ckqa-back/src/main/java/org/ysu/ckqaback/entity/QaRetrievalLogs.java`
  负责扩展任务字段映射。
- Modify: `backend/ckqa-back/src/main/java/org/ysu/ckqaback/service/QaRetrievalLogsService.java`
  负责新增任务创建、状态同步、详情查询、按 user message 查询摘要等方法。
- Modify: `backend/ckqa-back/src/main/java/org/ysu/ckqaback/service/impl/QaRetrievalLogsServiceImpl.java`
  负责落地上述任务相关数据库操作。
- Modify: `pdf_ingest/sql/ocqa.sql`
  负责把 `qa_retrieval_logs` 扩展成异步任务表。

### Java 测试与文档

- Create: `backend/ckqa-back/src/test/java/org/ysu/ckqaback/integration/graphrag/GraphRagTaskClientTest.java`
  覆盖 Python 任务接口客户端。
- Create: `backend/ckqa-back/src/test/java/org/ysu/ckqaback/qa/QaTaskWorkerTest.java`
  覆盖后台任务成功、stale、Python 快照丢失等路径。
- Modify: `backend/ckqa-back/src/test/java/org/ysu/ckqaback/qa/QaWorkflowServiceTest.java`
  改成覆盖“提交即返回 taskId”和消息列表摘要。
- Modify: `backend/ckqa-back/src/test/java/org/ysu/ckqaback/controller/QaSessionsControllerWebMvcTest.java`
  覆盖新接口契约。
- Modify: `backend/ckqa-back/.env.example`
  记录新的轮询与 stale 配置项。
- Modify: `backend/ckqa-back/README.md`
  记录 Java 异步问答接口与运行方式。
- Modify: `graphrag_pipeline/README.md`
  记录 Python 内部任务接口与冒烟方式。

### 计划内删除

- Delete: `backend/ckqa-back/src/main/java/org/ysu/ckqaback/integration/graphrag/GraphRagQueryClient.java`
- Delete: `backend/ckqa-back/src/main/java/org/ysu/ckqaback/integration/graphrag/GraphRagChatResult.java`
- Delete: `backend/ckqa-back/src/test/java/org/ysu/ckqaback/integration/graphrag/GraphRagQueryClientTest.java`
- Delete: `backend/ckqa-back/src/main/java/org/ysu/ckqaback/qa/dto/QaRoundResponse.java`

## Task 1: 搭起 Python 查询任务管理器

**Files:**
- Create: `graphrag_pipeline/utils/query_task_manager.py`
- Create: `graphrag_pipeline/tests/test_query_task_manager.py`
- Modify: `graphrag_pipeline/utils/main.py`

- [x] **Step 1: 先写失败测试，固定任务管理器的核心语义**

```python
#!/usr/bin/env python3
# -*- coding: utf-8 -*-

from __future__ import annotations

import asyncio
import os
import sys
import unittest
from pathlib import Path

_PROJECT_ROOT = Path(__file__).resolve().parent.parent
_MODULE_DIR = _PROJECT_ROOT / "utils"
if str(_MODULE_DIR) not in sys.path:
    sys.path.insert(0, str(_MODULE_DIR))

from query_task_manager import QueryTaskManager


def _python_cmd(code: str) -> list[str]:
    return [sys.executable, "-c", code]


class TestQueryTaskManager(unittest.IsolatedAsyncioTestCase):
    async def test_refreshes_heartbeat_until_process_finishes(self):
        manager = QueryTaskManager(
            heartbeat_interval_seconds=0.05,
            command_factory=lambda mode, prompt: _python_cmd(
                "import time; print('started', flush=True); time.sleep(0.2); print('done', flush=True)"
            ),
            env_factory=lambda: os.environ.copy(),
            cwd=_PROJECT_ROOT,
        )

        created = await manager.create_task("global", "图谱主题")
        await asyncio.sleep(0.08)

        running = manager.get_snapshot(created.python_task_id)
        self.assertEqual(running.task_status, "running")
        self.assertEqual(running.progress_stage, "running")
        self.assertIsNotNone(running.last_heartbeat_at)

        await asyncio.sleep(0.25)
        finished = manager.get_snapshot(created.python_task_id)
        self.assertEqual(finished.task_status, "success")
        self.assertEqual(finished.progress_stage, "done")
        self.assertIn("started", finished.latest_logs)
        self.assertIn("done", finished.result_text)

    async def test_trims_latest_logs_and_marks_failed_on_non_zero_exit(self):
        manager = QueryTaskManager(
            heartbeat_interval_seconds=0.05,
            max_log_lines=3,
            max_log_chars=40,
            command_factory=lambda mode, prompt: _python_cmd(
                "import sys; [print(f'line-{i}', flush=True) for i in range(6)]; sys.exit(3)"
            ),
            env_factory=lambda: os.environ.copy(),
            cwd=_PROJECT_ROOT,
        )

        created = await manager.create_task("local", "线程")
        await asyncio.sleep(0.2)

        snapshot = manager.get_snapshot(created.python_task_id)
        self.assertEqual(snapshot.task_status, "failed")
        self.assertEqual(snapshot.return_code, 3)
        self.assertLessEqual(len(snapshot.latest_logs), 3)
        self.assertTrue("\n".join(snapshot.latest_logs).endswith("line-5"))


if __name__ == "__main__":
    unittest.main()
```

- [x] **Step 2: 运行测试确认它先失败**

Run: `cd graphrag_pipeline && python -m pytest tests/test_query_task_manager.py -q`

Expected: FAIL，报 `ModuleNotFoundError: No module named 'query_task_manager'`，或者 `QueryTaskManager` / `create_task` / `get_snapshot` 未定义。

- [x] **Step 3: 写最小实现，让 Python 侧先具备真实子进程托管能力**

```python
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
        self._task_handles: dict[str, asyncio.Task[None]] = {}
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
            self._task_handles[python_task_id] = asyncio.create_task(self._run_task(python_task_id))
        return replace(snapshot)

    def get_snapshot(self, python_task_id: str) -> QueryTaskSnapshot | None:
        snapshot = self._tasks.get(python_task_id)
        return None if snapshot is None else replace(snapshot, latest_logs=list(snapshot.latest_logs or []))

    async def _run_task(self, python_task_id: str) -> None:
        snapshot = self._tasks[python_task_id]
        cmd = self._command_factory(snapshot.mode, snapshot.prompt)
        process = await asyncio.create_subprocess_exec(
            *cmd,
            cwd=self._cwd,
            env=self._env_factory(),
            stdout=asyncio.subprocess.PIPE,
            stderr=asyncio.subprocess.PIPE,
        )
        self._tasks[python_task_id] = replace(
            snapshot,
            task_status="running",
            progress_stage="running",
            process_alive=True,
            started_at=utc_now(),
            # 任务切到 running 时先写入一次初始心跳，后续由 heartbeat loop 续期。
            last_heartbeat_at=utc_now(),
        )

        logs: deque[str] = deque(maxlen=self._max_log_lines * 4)
        stdout_lines: list[str] = []
        heartbeat = asyncio.create_task(self._heartbeat_loop(python_task_id, process))
        stdout_task = asyncio.create_task(self._read_stream(process.stdout, python_task_id, logs, stdout_lines))
        stderr_task = asyncio.create_task(self._read_stream(process.stderr, python_task_id, logs, None))

        await asyncio.gather(stdout_task, stderr_task)
        return_code = await process.wait()
        heartbeat.cancel()

        final_logs = trim_log_tail(list(logs), max_lines=self._max_log_lines, max_chars=self._max_log_chars)
        stdout_text = "\n".join(stdout_lines).strip()
        self._tasks[python_task_id] = replace(
            self._tasks[python_task_id],
            task_status="success" if return_code == 0 else "failed",
            progress_stage="done",
            process_alive=False,
            latest_logs=final_logs,
            result_text=stdout_text if return_code == 0 else None,
            error_message=None if return_code == 0 else f"graphrag query exit code={return_code}",
            return_code=return_code,
            finished_at=utc_now(),
        )

    async def _heartbeat_loop(self, python_task_id: str, process) -> None:
        while process.returncode is None:
            await asyncio.sleep(self._heartbeat_interval_seconds)
            if process.returncode is None:
                self._tasks[python_task_id] = replace(
                    self._tasks[python_task_id],
                    last_heartbeat_at=utc_now(),
                )

    async def _read_stream(self, stream, python_task_id: str, logs: deque[str], captured_lines: list[str] | None) -> None:
        while True:
            line = await stream.readline()
            if not line:
                return
            text = line.decode("utf-8", errors="ignore").strip()
            if not text:
                continue
            logs.append(text)
            if captured_lines is not None:
                captured_lines.append(text)
            self._tasks[python_task_id] = replace(
                self._tasks[python_task_id],
                latest_logs=trim_log_tail(list(logs), max_lines=self._max_log_lines, max_chars=self._max_log_chars),
                last_heartbeat_at=utc_now(),
            )
```

- [x] **Step 4: 重新运行测试，确认心跳、日志 tail、成功/失败状态都已跑通**

Run: `cd graphrag_pipeline && python -m pytest tests/test_query_task_manager.py -q`

Expected: PASS，两个测试都通过。

- [ ] **Step 5: 提交这一小步**

```bash
git add graphrag_pipeline/utils/query_task_manager.py graphrag_pipeline/tests/test_query_task_manager.py graphrag_pipeline/utils/main.py
git commit -m "feat: add graphrag query task manager"
```

## Task 2: 接上 Python 任务 API，并清理错误方向的 global 调参残留

**Files:**
- Create: `graphrag_pipeline/tests/test_query_task_api.py`
- Modify: `graphrag_pipeline/utils/main.py`
- Modify: `graphrag_pipeline/utils/api_runtime_config.py`
- Modify: `graphrag_pipeline/settings.yaml`
- Modify: `graphrag_pipeline/tests/test_main_cli_mode.py`
- Modify: `graphrag_pipeline/tests/test_api_runtime_config.py`
- Modify: `graphrag_pipeline/tests/test_runtime_defaults.py`

- [x] **Step 1: 先用 API 测试和配置测试把边界固定住**

```python
#!/usr/bin/env python3
# -*- coding: utf-8 -*-

from __future__ import annotations

import sys
import unittest
from pathlib import Path

from fastapi.testclient import TestClient

_PROJECT_ROOT = Path(__file__).resolve().parent.parent
_MODULE_DIR = _PROJECT_ROOT / "utils"
if str(_MODULE_DIR) not in sys.path:
    sys.path.insert(0, str(_MODULE_DIR))

from main import create_app
from query_task_manager import QueryTaskSnapshot, utc_now


class FakeTaskManager:
    def __init__(self) -> None:
        self.created: list[tuple[str, str]] = []

    async def create_task(self, mode: str, prompt: str) -> QueryTaskSnapshot:
        self.created.append((mode, prompt))
        return QueryTaskSnapshot(
            python_task_id="qt_20260422_001",
            mode=mode,
            prompt=prompt,
            task_status="pending",
            progress_stage="queued",
            process_alive=False,
            created_at=utc_now(),
            latest_logs=[],
        )

    def get_snapshot(self, task_id: str) -> QueryTaskSnapshot | None:
        if task_id != "qt_20260422_001":
            return None
        return QueryTaskSnapshot(
            python_task_id=task_id,
            mode="global",
            prompt="请概括这套图谱的主题",
            task_status="running",
            progress_stage="running",
            process_alive=True,
            created_at=utc_now(),
            started_at=utc_now(),
            last_heartbeat_at=utc_now(),
            latest_logs=["started graphrag query --method global"],
        )


class TestQueryTaskApi(unittest.TestCase):
    def test_submit_and_get_query_task(self):
        client = TestClient(create_app(task_manager=FakeTaskManager()))

        submit = client.post(
            "/v1/query-tasks",
            json={"mode": "global", "prompt": "请概括这套图谱的主题"},
        )
        self.assertEqual(submit.status_code, 200)
        self.assertEqual(submit.json()["pythonTaskId"], "qt_20260422_001")
        self.assertEqual(submit.json()["progressStage"], "queued")

        detail = client.get("/v1/query-tasks/qt_20260422_001")
        self.assertEqual(detail.status_code, 200)
        self.assertEqual(detail.json()["taskStatus"], "running")
        self.assertTrue(detail.json()["processAlive"])

    def test_get_query_task_returns_404_when_snapshot_missing(self):
        client = TestClient(create_app(task_manager=FakeTaskManager()))

        response = client.get("/v1/query-tasks/qt_missing")
        self.assertEqual(response.status_code, 404)
```

同时把现有测试改成这组断言：

```python
config = load_api_runtime_config({}, load_dotenv_file=False)
assert not hasattr(config, "global_search_community_level")
assert not hasattr(config, "global_search_dynamic_selection")
assert not hasattr(config, "global_search_response_type")
```

```python
self.assertNotIn("dynamic_search_use_summary", text)
self.assertNotIn("dynamic_search_max_level", text)
```

```python
self.assertNotIn('"--community-level"', text)
self.assertNotIn('"--response-type"', text)
self.assertNotIn('"--dynamic-community-selection"', text)
self.assertIn('env["NO_PROXY"]', text)
```

- [x] **Step 2: 运行目标测试，确认新接口和配置收敛还没实现**

Run: `cd graphrag_pipeline && python -m pytest tests/test_query_task_api.py tests/test_api_runtime_config.py tests/test_main_cli_mode.py tests/test_runtime_defaults.py -q`

Expected: FAIL，原因包括 `create_app` 不存在、`/v1/query-tasks` 未注册，以及旧测试仍然要求 `global_search_*` 字段存在。

- [x] **Step 3: 在 FastAPI 层接出新接口，并删掉错误方向的 global 运行时调参**

```python
@dataclass(frozen=True)
class ApiRuntimeConfig:
    output_dir: Path
    lancedb_uri: str
    api_host: str
    api_port: int


def _build_query_cmd(method: str, prompt: str) -> list[str]:
    return [
        "graphrag",
        "query",
        "--root",
        ".",
        "--method",
        method,
        prompt,
    ]


class QueryTaskCreateRequest(BaseModel):
    mode: str
    prompt: str


def create_app(task_manager: QueryTaskManager | None = None) -> FastAPI:
    app = FastAPI(...)
    manager = task_manager or QueryTaskManager(
        heartbeat_interval_seconds=5.0,
        command_factory=_build_query_cmd,
        env_factory=_build_query_env,
        cwd=GRAPHRAG_ROOT,
    )

    @app.post("/v1/query-tasks")
    async def create_query_task(request: QueryTaskCreateRequest):
        snapshot = await manager.create_task(request.mode, request.prompt)
        return {
            "pythonTaskId": snapshot.python_task_id,
            "taskStatus": snapshot.task_status,
            "progressStage": snapshot.progress_stage,
            "createdAt": snapshot.created_at.isoformat(),
        }

    @app.get("/v1/query-tasks/{task_id}")
    async def get_query_task(task_id: str):
        snapshot = manager.get_snapshot(task_id)
        if snapshot is None:
            raise HTTPException(status_code=404, detail="Query task not found")
        return {
            "pythonTaskId": snapshot.python_task_id,
            "taskStatus": snapshot.task_status,
            "progressStage": snapshot.progress_stage,
            "processAlive": snapshot.process_alive,
            "lastHeartbeatAt": snapshot.last_heartbeat_at.isoformat() if snapshot.last_heartbeat_at else None,
            "latestLogs": snapshot.latest_logs or [],
            "resultText": snapshot.result_text,
            "errorMessage": snapshot.error_message,
            "returnCode": snapshot.return_code,
            "startedAt": snapshot.started_at.isoformat() if snapshot.started_at else None,
            "finishedAt": snapshot.finished_at.isoformat() if snapshot.finished_at else None,
        }

    return app


app = create_app()
```

`settings.yaml` 直接删掉这两行：

```yaml
dynamic_search_use_summary: true
dynamic_search_max_level: 1
```

`test_main_cli_mode.py` 里的目标变成“保留位置参数 + 保留 `NO_PROXY` 保护 + 不再拼接 global strategy 参数”。

- [x] **Step 4: 重新跑 Python 任务接口与配置测试**

Run: `cd graphrag_pipeline && python -m pytest tests/test_query_task_manager.py tests/test_query_task_api.py tests/test_api_runtime_config.py tests/test_main_cli_mode.py tests/test_runtime_defaults.py -q`

Expected: PASS，所有目标测试通过。

- [ ] **Step 5: 提交 Python API 改造与清理**

```bash
git add graphrag_pipeline/utils/main.py graphrag_pipeline/utils/api_runtime_config.py graphrag_pipeline/settings.yaml graphrag_pipeline/tests/test_query_task_manager.py graphrag_pipeline/tests/test_query_task_api.py graphrag_pipeline/tests/test_api_runtime_config.py graphrag_pipeline/tests/test_main_cli_mode.py graphrag_pipeline/tests/test_runtime_defaults.py
git commit -m "feat: add graphrag query task api"
```

## Task 3: 增加 Java 侧的 Python 任务客户端与轮询配置

**Files:**
- Create: `backend/ckqa-back/src/main/java/org/ysu/ckqaback/integration/graphrag/GraphRagTaskClient.java`
- Create: `backend/ckqa-back/src/main/java/org/ysu/ckqaback/integration/graphrag/GraphRagTaskCreateResult.java`
- Create: `backend/ckqa-back/src/main/java/org/ysu/ckqaback/integration/graphrag/GraphRagTaskSnapshot.java`
- Create: `backend/ckqa-back/src/test/java/org/ysu/ckqaback/integration/graphrag/GraphRagTaskClientTest.java`
- Modify: `backend/ckqa-back/src/main/java/org/ysu/ckqaback/integration/config/CkqaIntegrationProperties.java`
- Modify: `backend/ckqa-back/src/main/resources/application.properties`

- [x] **Step 1: 先写 Java 客户端测试，固定 Python 任务接口契约**

```java
package org.ysu.ckqaback.integration.graphrag;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.time.Duration;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;
import static org.springframework.http.HttpStatus.NOT_FOUND;

class GraphRagTaskClientTest {

    @Test
    void shouldCreatePythonTask() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        server.expect(requestTo("http://127.0.0.1:8012/v1/query-tasks"))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withSuccess("""
                        {
                          "pythonTaskId": "qt_20260422_001",
                          "taskStatus": "pending",
                          "progressStage": "queued",
                          "createdAt": "2026-04-22T15:20:34"
                        }
                        """, MediaType.APPLICATION_JSON));

        GraphRagTaskClient client = new GraphRagTaskClient(builder, "http://127.0.0.1:8012", Duration.ofSeconds(5));
        GraphRagTaskCreateResult result = client.createTask("global", "请概括这套图谱的主题");

        assertThat(result.pythonTaskId()).isEqualTo("qt_20260422_001");
        assertThat(result.taskStatus()).isEqualTo("pending");
    }

    @Test
    void shouldReturnEmptyWhenPythonTaskSnapshotIsMissing() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        server.expect(requestTo("http://127.0.0.1:8012/v1/query-tasks/qt_missing"))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withStatus(NOT_FOUND));

        GraphRagTaskClient client = new GraphRagTaskClient(builder, "http://127.0.0.1:8012", Duration.ofSeconds(5));
        Optional<GraphRagTaskSnapshot> snapshot = client.getTask("qt_missing");

        assertThat(snapshot).isEmpty();
    }
}
```

- [x] **Step 2: 先跑测试，确认新的任务客户端还不存在**

Run: `cd backend/ckqa-back && ./mvnw -q -Dtest=GraphRagTaskClientTest test`

Expected: FAIL，因为 `GraphRagTaskClient`、`GraphRagTaskCreateResult`、`GraphRagTaskSnapshot` 还不存在。

- [x] **Step 3: 实现 Java 任务客户端，并把轮询间隔 / stale 阈值配置补齐**

```java
public record GraphRagTaskCreateResult(
        String pythonTaskId,
        String taskStatus,
        String progressStage,
        LocalDateTime createdAt
) {
}
```

```java
public record GraphRagTaskSnapshot(
        String pythonTaskId,
        String taskStatus,
        String progressStage,
        boolean processAlive,
        LocalDateTime lastHeartbeatAt,
        List<String> latestLogs,
        String resultText,
        String errorMessage,
        Integer returnCode,
        LocalDateTime startedAt,
        LocalDateTime finishedAt
) {
    public boolean isTerminal() {
        return "success".equals(taskStatus) || "failed".equals(taskStatus);
    }
}
```

```java
@Service
public class GraphRagTaskClient {

    private final RestClient restClient;

    @Autowired
    public GraphRagTaskClient(RestClient.Builder builder, CkqaIntegrationProperties properties) {
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        Duration timeout = Duration.ofSeconds(properties.getPolling().getQueryTaskIntervalSeconds());
        requestFactory.setConnectTimeout((int) timeout.toMillis());
        requestFactory.setReadTimeout((int) timeout.toMillis());
        this.restClient = builder.requestFactory(requestFactory)
                .baseUrl(properties.getGraphrag().getApiBaseUrl())
                .build();
    }

    GraphRagTaskClient(RestClient.Builder builder, String baseUrl, Duration timeout) {
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout((int) timeout.toMillis());
        requestFactory.setReadTimeout((int) timeout.toMillis());
        this.restClient = builder.requestFactory(requestFactory).baseUrl(baseUrl).build();
    }

    public GraphRagTaskCreateResult createTask(String mode, String prompt) {
        return restClient.post()
                .uri("/v1/query-tasks")
                .contentType(MediaType.APPLICATION_JSON)
                .body(Map.of("mode", mode, "prompt", prompt))
                .retrieve()
                .body(GraphRagTaskCreateResult.class);
    }

    public Optional<GraphRagTaskSnapshot> getTask(String pythonTaskId) {
        try {
            GraphRagTaskSnapshot snapshot = restClient.get()
                    .uri("/v1/query-tasks/{taskId}", pythonTaskId)
                    .retrieve()
                    .body(GraphRagTaskSnapshot.class);
            return Optional.ofNullable(snapshot);
        } catch (HttpClientErrorException.NotFound exception) {
            return Optional.empty();
        }
    }
}
```

```java
@Getter
@Setter
@ConfigurationProperties(prefix = "ckqa.integration")
public class CkqaIntegrationProperties {

    private final GraphRagProperties graphrag = new GraphRagProperties();
    private final TimeoutProperties timeout = new TimeoutProperties();
    private final PollingProperties polling = new PollingProperties();

    public static class TimeoutProperties {
        private long parseSeconds = 900L;
        private long exportSeconds = 300L;
        private long fetchSeconds = 300L;
        private long indexSeconds = 1800L;
        private long indexStaleSeconds = 2400L;
        private long queryTaskStaleSeconds = 300L;
        private Map<String, Long> queryTaskModeStaleSeconds = new LinkedHashMap<>(
            Map.of("local", 300L, "basic", 300L, "global", 1800L, "drift", 1800L)
        );
        private Map<String, String> queryTaskModeTimeoutMessages = new LinkedHashMap<>();
    }

    public static class PollingProperties {
        private long queryTaskIntervalSeconds = 10L;
        private Map<String, Long> queryTaskModeIntervalSeconds = new LinkedHashMap<>(
            Map.of("local", 10L, "basic", 10L, "global", 30L, "drift", 30L)
        );
    }
}
```

`application.properties` 增加：

```properties
ckqa.integration.polling.query-task-interval-seconds=${QUERY_TASK_POLLING_INTERVAL_SECONDS:10}
ckqa.integration.polling.query-task-mode-interval-seconds.drift=${QUERY_TASK_POLLING_INTERVAL_SECONDS_DRIFT:30}
ckqa.integration.timeout.query-task-stale-seconds=${QUERY_TASK_STALE_SECONDS:300}
ckqa.integration.timeout.query-task-mode-stale-seconds.drift=${QUERY_TASK_STALE_SECONDS_DRIFT:1800}
```

- [x] **Step 4: 跑 Java 客户端测试，确认 Python 任务接口的读写契约已经稳定**

Run: `cd backend/ckqa-back && ./mvnw -q -Dtest=GraphRagTaskClientTest test`

Expected: PASS。

- [ ] **Step 5: 提交 Java 任务客户端与配置**

```bash
git add backend/ckqa-back/src/main/java/org/ysu/ckqaback/integration/graphrag/GraphRagTaskClient.java backend/ckqa-back/src/main/java/org/ysu/ckqaback/integration/graphrag/GraphRagTaskCreateResult.java backend/ckqa-back/src/main/java/org/ysu/ckqaback/integration/graphrag/GraphRagTaskSnapshot.java backend/ckqa-back/src/test/java/org/ysu/ckqaback/integration/graphrag/GraphRagTaskClientTest.java backend/ckqa-back/src/main/java/org/ysu/ckqaback/integration/config/CkqaIntegrationProperties.java backend/ckqa-back/src/main/resources/application.properties
git commit -m "feat: add java graphrag task client"
```

## Task 4: 扩展 `qa_retrieval_logs` 并落地 Java 异步问答 worker

**Files:**
- Modify: `pdf_ingest/sql/ocqa.sql`
- Modify: `backend/ckqa-back/src/main/java/org/ysu/ckqaback/entity/QaRetrievalLogs.java`
- Modify: `backend/ckqa-back/src/main/java/org/ysu/ckqaback/service/QaRetrievalLogsService.java`
- Modify: `backend/ckqa-back/src/main/java/org/ysu/ckqaback/service/impl/QaRetrievalLogsServiceImpl.java`
- Create: `backend/ckqa-back/src/main/java/org/ysu/ckqaback/config/AsyncTaskExecutorConfig.java`
- Create: `backend/ckqa-back/src/main/java/org/ysu/ckqaback/qa/QaTaskWorker.java`
- Create: `backend/ckqa-back/src/main/java/org/ysu/ckqaback/qa/dto/QaTaskSubmissionResponse.java`
- Create: `backend/ckqa-back/src/main/java/org/ysu/ckqaback/qa/dto/QaTaskDetailResponse.java`
- Modify: `backend/ckqa-back/src/main/java/org/ysu/ckqaback/qa/dto/QaMessageResponse.java`
- Modify: `backend/ckqa-back/src/main/java/org/ysu/ckqaback/qa/QaWorkflowService.java`
- Create: `backend/ckqa-back/src/test/java/org/ysu/ckqaback/qa/QaTaskWorkerTest.java`
- Modify: `backend/ckqa-back/src/test/java/org/ysu/ckqaback/qa/QaWorkflowServiceTest.java`
- Delete: `backend/ckqa-back/src/main/java/org/ysu/ckqaback/integration/graphrag/GraphRagQueryClient.java`
- Delete: `backend/ckqa-back/src/main/java/org/ysu/ckqaback/integration/graphrag/GraphRagChatResult.java`
- Delete: `backend/ckqa-back/src/main/java/org/ysu/ckqaback/qa/dto/QaRoundResponse.java`

- [x] **Step 1: 先用 worker 测试和 workflow 测试钉住异步受理语义**

```java
package org.ysu.ckqaback.qa;

import org.junit.jupiter.api.Test;
import org.ysu.ckqaback.entity.QaMessages;
import org.ysu.ckqaback.entity.QaRetrievalLogs;
import org.ysu.ckqaback.integration.graphrag.GraphRagTaskClient;
import org.ysu.ckqaback.integration.graphrag.GraphRagTaskCreateResult;
import org.ysu.ckqaback.integration.graphrag.GraphRagTaskSnapshot;
import org.ysu.ckqaback.service.QaMessagesService;
import org.ysu.ckqaback.service.QaRetrievalLogsService;
import org.ysu.ckqaback.service.QaSessionsService;

import org.springframework.core.task.TaskExecutor;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.mock;

class QaTaskWorkerTest {

    @Test
    void shouldPersistAssistantMessageWhenPythonTaskSucceeds() {
        GraphRagTaskClient taskClient = mock(GraphRagTaskClient.class);
        QaRetrievalLogsService retrievalLogsService = mock(QaRetrievalLogsService.class);
        QaMessagesService messagesService = mock(QaMessagesService.class);
        QaSessionsService sessionsService = mock(QaSessionsService.class);
        TaskExecutor taskExecutor = Runnable::run;

        QaTaskWorker worker = new QaTaskWorker(
                taskExecutor,
                taskClient,
                retrievalLogsService,
                messagesService,
                sessionsService,
                Duration.ofSeconds(5),
                Duration.ofSeconds(30)
        );

        QaRetrievalLogs task = new QaRetrievalLogs();
        task.setId(9001L);
        task.setSessionId(5L);
        task.setQueryMode("global");
        task.setQueryText("请概括这套图谱的主题");
        task.setUserMessageId(101L);

        given(retrievalLogsService.getRequiredTask(5L, 9001L)).willReturn(task);
        given(taskClient.createTask("global", "请概括这套图谱的主题"))
                .willReturn(new GraphRagTaskCreateResult("qt_20260422_001", "pending", "queued", LocalDateTime.now()));
        given(taskClient.getTask("qt_20260422_001"))
                .willReturn(Optional.of(new GraphRagTaskSnapshot(
                        "qt_20260422_001",
                        "success",
                        "done",
                        false,
                        LocalDateTime.now(),
                        List.of("done"),
                        "图谱主题集中在操作系统概念网络",
                        null,
                        0,
                        LocalDateTime.now(),
                        LocalDateTime.now()
                )));

        QaMessages assistant = new QaMessages();
        assistant.setId(102L);
        given(messagesService.appendAssistantMessage(5L, "图谱主题集中在操作系统概念网络")).willReturn(assistant);

        worker.processTask(5L, 9001L);

        then(retrievalLogsService).should().bindPythonTask(9001L, "qt_20260422_001", "pending", "queued");
        then(messagesService).should().appendAssistantMessage(5L, "图谱主题集中在操作系统概念网络");
        then(retrievalLogsService).should().markSuccess(anyLong(), anyLong(), contains("done"), "success");
    }

    @Test
    void shouldFailTaskWhenPythonSnapshotDisappearsAfterDispatch() {
        GraphRagTaskClient taskClient = mock(GraphRagTaskClient.class);
        QaRetrievalLogsService retrievalLogsService = mock(QaRetrievalLogsService.class);
        QaMessagesService messagesService = mock(QaMessagesService.class);
        QaSessionsService sessionsService = mock(QaSessionsService.class);
        TaskExecutor taskExecutor = Runnable::run;

        QaTaskWorker worker = new QaTaskWorker(
                taskExecutor,
                taskClient,
                retrievalLogsService,
                messagesService,
                sessionsService,
                Duration.ofSeconds(5),
                Duration.ofSeconds(30)
        );

        QaRetrievalLogs task = new QaRetrievalLogs();
        task.setId(9001L);
        task.setSessionId(5L);
        task.setQueryMode("global");
        task.setQueryText("请概括这套图谱的主题");

        given(retrievalLogsService.getRequiredTask(5L, 9001L)).willReturn(task);
        given(taskClient.createTask("global", "请概括这套图谱的主题"))
                .willReturn(new GraphRagTaskCreateResult("qt_20260422_001", "pending", "queued", LocalDateTime.now()));
        given(taskClient.getTask("qt_20260422_001")).willReturn(Optional.empty());

        worker.processTask(5L, 9001L);

        then(retrievalLogsService).should().markFailed(9001L, "failed", "Python 任务快照丢失或服务已重启", "");
    }

    @Test
    void shouldMarkTaskStaleWhenHeartbeatTimeoutExceeded() {
        GraphRagTaskClient taskClient = mock(GraphRagTaskClient.class);
        QaRetrievalLogsService retrievalLogsService = mock(QaRetrievalLogsService.class);
        QaMessagesService messagesService = mock(QaMessagesService.class);
        QaSessionsService sessionsService = mock(QaSessionsService.class);
        TaskExecutor taskExecutor = Runnable::run;

        QaTaskWorker worker = new QaTaskWorker(
                taskExecutor,
                taskClient,
                retrievalLogsService,
                messagesService,
                sessionsService,
                Duration.ofSeconds(5),
                Duration.ofSeconds(30)
        );

        QaRetrievalLogs task = new QaRetrievalLogs();
        task.setId(9001L);
        task.setSessionId(5L);
        task.setQueryMode("global");
        task.setQueryText("请概括这套图谱的主题");

        given(retrievalLogsService.getRequiredTask(5L, 9001L)).willReturn(task);
        given(taskClient.createTask("global", "请概括这套图谱的主题"))
                .willReturn(new GraphRagTaskCreateResult("qt_20260422_001", "pending", "queued", LocalDateTime.now()));
        given(taskClient.getTask("qt_20260422_001"))
                .willReturn(Optional.of(new GraphRagTaskSnapshot(
                        "qt_20260422_001",
                        "running",
                        "running",
                        true,
                        LocalDateTime.now().minusSeconds(31),
                        List.of("process alive"),
                        null,
                        null,
                        null,
                        LocalDateTime.now().minusMinutes(1),
                        null
                )));

        worker.processTask(5L, 9001L);

        then(retrievalLogsService).should().markFailed(9001L, "stale", "任务心跳超时", "process alive");
    }
}
```

`QaWorkflowServiceTest` 里的目标改成：

```java
QaTaskSubmissionResponse response = workflowService.sendMessage(5L, new CreateQaMessageRequest("global", "请概括这套图谱的主题"));

assertThat(response.getTaskStatus()).isEqualTo("pending");
assertThat(response.getProgressStage()).isEqualTo("queued");
then(qaTaskWorker).should().dispatch(5L, 9001L);
then(qaMessagesService).should(never()).appendAssistantMessage(anyLong(), anyString());
```

- [x] **Step 2: 运行目标测试，确认 Java 侧还停留在同步问答语义**

Run: `cd backend/ckqa-back && ./mvnw -q -Dtest=QaWorkflowServiceTest,QaTaskWorkerTest test`

Expected: FAIL，因为当前 `QaWorkflowService.sendMessage()` 仍然同步调用 `GraphRagQueryClient.query()`，`QaTaskWorker`、新 DTO 和新持久化方法都还不存在。

- [x] **Step 3: 扩表、改实体、加 worker，并把 `sendMessage` 改成“先落任务、后后台执行”**

`ocqa.sql` 的核心改动直接写成增量式结构：

```sql
ALTER TABLE `qa_retrieval_logs`
  MODIFY COLUMN `retrieval_status` enum('success','partial','failed') CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL COMMENT '检索状态',
  ADD COLUMN `user_message_id` bigint NOT NULL COMMENT '用户消息ID' AFTER `session_id`,
  ADD COLUMN `assistant_message_id` bigint NULL COMMENT '助手消息ID' AFTER `user_message_id`,
  ADD COLUMN `task_seq` int NOT NULL DEFAULT 1 COMMENT '同一用户消息下的任务序号' AFTER `assistant_message_id`,
  ADD COLUMN `task_status` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT 'legacy' COMMENT '异步任务状态，存量历史数据标记为legacy' AFTER `task_seq`,
  ADD COLUMN `progress_stage` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT 'legacy' COMMENT '编排阶段，存量历史数据标记为legacy' AFTER `task_status`,
  ADD COLUMN `python_task_id` varchar(128) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL COMMENT 'Python侧任务ID' AFTER `progress_stage`,
  ADD COLUMN `latest_logs` text CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL COMMENT '最近日志tail' AFTER `python_task_id`,
  ADD COLUMN `started_at` timestamp NULL DEFAULT NULL COMMENT '开始时间' AFTER `latest_logs`,
  ADD COLUMN `last_heartbeat_at` timestamp NULL DEFAULT NULL COMMENT '最近心跳时间' AFTER `started_at`,
  ADD COLUMN `finished_at` timestamp NULL DEFAULT NULL COMMENT '完成时间' AFTER `last_heartbeat_at`,
  ADD INDEX `idx_retrieval_logs_session_created` (`session_id`, `created_at`),
  ADD INDEX `idx_retrieval_logs_user_message_seq` (`user_message_id`, `task_seq`),
  ADD INDEX `idx_retrieval_logs_task_status_heartbeat` (`task_status`, `last_heartbeat_at`),
  ADD UNIQUE KEY `uk_retrieval_logs_python_task_id` (`python_task_id`),
  ADD CONSTRAINT `fk_retrieval_logs_user_message` FOREIGN KEY (`user_message_id`) REFERENCES `qa_messages` (`id`) ON DELETE CASCADE ON UPDATE RESTRICT,
  ADD CONSTRAINT `fk_retrieval_logs_assistant_message` FOREIGN KEY (`assistant_message_id`) REFERENCES `qa_messages` (`id`) ON DELETE SET NULL ON UPDATE RESTRICT;
```

Java 侧对应改成：

```java
@Getter
@Setter
@ToString
@TableName("qa_retrieval_logs")
public class QaRetrievalLogs implements Serializable {
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;
    @TableField("session_id")
    private Long sessionId;
    @TableField("user_message_id")
    private Long userMessageId;
    @TableField("assistant_message_id")
    private Long assistantMessageId;
    @TableField("task_seq")
    private Integer taskSeq;
    @TableField("task_status")
    private String taskStatus;
    @TableField("progress_stage")
    private String progressStage;
    @TableField("python_task_id")
    private String pythonTaskId;
    @TableField("latest_logs")
    private String latestLogs;
    @TableField("started_at")
    private LocalDateTime startedAt;
    @TableField("last_heartbeat_at")
    private LocalDateTime lastHeartbeatAt;
    @TableField("finished_at")
    private LocalDateTime finishedAt;
    @TableField("retrieval_status")
    private String retrievalStatus;
    @TableField("error_message")
    private String errorMessage;
}
```

```java
public interface QaRetrievalLogsService extends IService<QaRetrievalLogs> {

    QaRetrievalLogs createPendingTask(Long sessionId, String courseId, Long indexRunId, Long userMessageId, String mode, String queryText);

    QaRetrievalLogs getRequiredTask(Long sessionId, Long taskId);

    void bindPythonTask(Long taskId, String pythonTaskId, String taskStatus, String progressStage);

    void syncRunningSnapshot(Long taskId, GraphRagTaskSnapshot snapshot);

    void markSuccess(Long taskId, Long assistantMessageId, String latestLogs, String retrievalStatus);

    // 保持四参数签名，latestLogs 固定作为最后一个参数传递。
    void markFailed(Long taskId, String taskStatus, String errorMessage, String latestLogs);

    Map<Long, QaRetrievalLogs> findLatestByUserMessageIds(List<Long> userMessageIds);
}
```

```java
@Configuration
public class AsyncTaskExecutorConfig {

    @Bean(name = "qaTaskExecutor")
    public TaskExecutor qaTaskExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setThreadNamePrefix("qa-task-");
        executor.setCorePoolSize(2);
        executor.setMaxPoolSize(4);
        executor.setQueueCapacity(100);
        executor.initialize();
        return executor;
    }
}
```

```java
@Service
public class QaTaskWorker {

    private final TaskExecutor qaTaskExecutor;
    private final GraphRagTaskClient graphRagTaskClient;
    private final QaRetrievalLogsService qaRetrievalLogsService;
    private final QaMessagesService qaMessagesService;
    private final QaSessionsService qaSessionsService;
    private final Duration pollInterval;
    private final Duration staleThreshold;

    @Autowired
    public QaTaskWorker(
            @Qualifier("qaTaskExecutor") TaskExecutor qaTaskExecutor,
            GraphRagTaskClient graphRagTaskClient,
            QaRetrievalLogsService qaRetrievalLogsService,
            QaMessagesService qaMessagesService,
            QaSessionsService qaSessionsService,
            CkqaIntegrationProperties properties
    ) {
        this(
                qaTaskExecutor,
                graphRagTaskClient,
                qaRetrievalLogsService,
                qaMessagesService,
                qaSessionsService,
                Duration.ofSeconds(properties.getPolling().getQueryTaskIntervalSeconds()),
                Duration.ofSeconds(properties.getTimeout().getQueryTaskStaleSeconds())
        );
    }

    QaTaskWorker(
            TaskExecutor qaTaskExecutor,
            GraphRagTaskClient graphRagTaskClient,
            QaRetrievalLogsService qaRetrievalLogsService,
            QaMessagesService qaMessagesService,
            QaSessionsService qaSessionsService,
            Duration pollInterval,
            Duration staleThreshold
    ) {
        this.qaTaskExecutor = qaTaskExecutor;
        this.graphRagTaskClient = graphRagTaskClient;
        this.qaRetrievalLogsService = qaRetrievalLogsService;
        this.qaMessagesService = qaMessagesService;
        this.qaSessionsService = qaSessionsService;
        this.pollInterval = pollInterval;
        this.staleThreshold = staleThreshold;
    }

    public void dispatch(Long sessionId, Long taskId) {
        qaTaskExecutor.execute(() -> processTask(sessionId, taskId));
    }

    public void processTask(Long sessionId, Long taskId) {
        QaRetrievalLogs task = qaRetrievalLogsService.getRequiredTask(sessionId, taskId);
        GraphRagTaskCreateResult created = graphRagTaskClient.createTask(task.getQueryMode(), task.getQueryText());
        qaRetrievalLogsService.bindPythonTask(taskId, created.pythonTaskId(), created.taskStatus(), created.progressStage());

        while (true) {
            Optional<GraphRagTaskSnapshot> snapshotOpt = graphRagTaskClient.getTask(created.pythonTaskId());
            if (snapshotOpt.isEmpty()) {
                qaRetrievalLogsService.markFailed(taskId, "failed", "Python 任务快照丢失或服务已重启", "");
                return;
            }

            GraphRagTaskSnapshot snapshot = snapshotOpt.get();
            qaRetrievalLogsService.syncRunningSnapshot(taskId, snapshot);

            if ("success".equals(snapshot.taskStatus())) {
                QaMessages assistant = qaMessagesService.appendAssistantMessage(sessionId, snapshot.resultText());
                qaSessionsService.touchLastMessageAt(sessionId);
                qaRetrievalLogsService.markSuccess(taskId, assistant.getId(), String.join("\n", snapshot.latestLogs()), "success");
                return;
            }

            if ("failed".equals(snapshot.taskStatus())) {
                qaRetrievalLogsService.markFailed(taskId, "failed", snapshot.errorMessage(), String.join("\n", snapshot.latestLogs()));
                return;
            }

            if ("running".equals(snapshot.taskStatus()) && !snapshot.processAlive()) {
                qaRetrievalLogsService.markFailed(taskId, "failed", "Python 任务进程已结束但未返回终态", String.join("\n", snapshot.latestLogs()));
                return;
            }

            if (snapshot.lastHeartbeatAt() != null
                    && Duration.between(snapshot.lastHeartbeatAt(), LocalDateTime.now()).compareTo(staleThreshold) > 0) {
                qaRetrievalLogsService.markFailed(taskId, "stale", "任务心跳超时", String.join("\n", snapshot.latestLogs()));
                return;
            }

            try {
                Thread.sleep(pollInterval.toMillis());
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
                qaRetrievalLogsService.markFailed(taskId, "failed", "Java 后台任务被中断", String.join("\n", snapshot.latestLogs()));
                return;
            }
        }
    }
}
```

`QaWorkflowService.sendMessage()` 改成：

```java
public QaTaskSubmissionResponse sendMessage(Long sessionId, CreateQaMessageRequest request) {
    QaSessions session = qaSessionsService.getRequiredById(sessionId);
    if (!"active".equals(session.getStatus())) {
        throw new BusinessException(ApiResultCode.QA_SESSION_NOT_ACTIVE, HttpStatus.CONFLICT);
    }
    if (session.getKnowledgeBaseId() == null) {
        throw new BusinessException(ApiResultCode.KNOWLEDGE_BASE_NOT_READY, HttpStatus.CONFLICT, "问答会话未绑定知识库");
    }
    KnowledgeBases knowledgeBase = knowledgeBasesService.getRequiredById(session.getKnowledgeBaseId());
    if (knowledgeBase.getActiveIndexRunId() == null) {
        throw new BusinessException(ApiResultCode.KNOWLEDGE_BASE_NOT_READY, HttpStatus.CONFLICT);
    }

    QaMessages userMessage = qaMessagesService.appendUserMessage(sessionId, request.getContent());
    qaSessionsService.touchLastMessageAt(sessionId);

    QaRetrievalLogs task = qaRetrievalLogsService.createPendingTask(
            sessionId,
            session.getCourseId(),
            knowledgeBase.getActiveIndexRunId(),
            userMessage.getId(),
            request.getMode(),
            request.getContent()
    );
    qaTaskWorker.dispatch(sessionId, task.getId());

    return QaTaskSubmissionResponse.of(
            QaMessageResponse.fromEntity(userMessage),
            task.getId(),
            task.getTaskStatus(),
            task.getProgressStage(),
            null,
            task.getCreatedAt()
    );
}
```

- [x] **Step 4: 重新跑 worker 与 workflow 测试，确认 Java 侧已经变成异步受理**

Run: `cd backend/ckqa-back && ./mvnw -q -Dtest=QaWorkflowServiceTest,QaTaskWorkerTest test`

Expected: PASS。

- [ ] **Step 5: 提交数据库与 Java 异步 worker 改造**

```bash
git add pdf_ingest/sql/ocqa.sql backend/ckqa-back/src/main/java/org/ysu/ckqaback/entity/QaRetrievalLogs.java backend/ckqa-back/src/main/java/org/ysu/ckqaback/service/QaRetrievalLogsService.java backend/ckqa-back/src/main/java/org/ysu/ckqaback/service/impl/QaRetrievalLogsServiceImpl.java backend/ckqa-back/src/main/java/org/ysu/ckqaback/config/AsyncTaskExecutorConfig.java backend/ckqa-back/src/main/java/org/ysu/ckqaback/qa/QaTaskWorker.java backend/ckqa-back/src/main/java/org/ysu/ckqaback/qa/dto/QaTaskSubmissionResponse.java backend/ckqa-back/src/main/java/org/ysu/ckqaback/qa/dto/QaTaskDetailResponse.java backend/ckqa-back/src/main/java/org/ysu/ckqaback/qa/dto/QaMessageResponse.java backend/ckqa-back/src/main/java/org/ysu/ckqaback/qa/QaWorkflowService.java backend/ckqa-back/src/test/java/org/ysu/ckqaback/qa/QaTaskWorkerTest.java backend/ckqa-back/src/test/java/org/ysu/ckqaback/qa/QaWorkflowServiceTest.java
git rm backend/ckqa-back/src/main/java/org/ysu/ckqaback/integration/graphrag/GraphRagQueryClient.java backend/ckqa-back/src/main/java/org/ysu/ckqaback/integration/graphrag/GraphRagChatResult.java backend/ckqa-back/src/test/java/org/ysu/ckqaback/integration/graphrag/GraphRagQueryClientTest.java backend/ckqa-back/src/main/java/org/ysu/ckqaback/qa/dto/QaRoundResponse.java
git commit -m "feat: add async qa task workflow"
```

## Task 5: 改造 Java 对外接口，补齐任务详情与消息列表摘要

**Files:**
- Modify: `backend/ckqa-back/src/main/java/org/ysu/ckqaback/controller/QaSessionsController.java`
- Modify: `backend/ckqa-back/src/test/java/org/ysu/ckqaback/controller/QaSessionsControllerWebMvcTest.java`
- Modify: `backend/ckqa-back/src/main/java/org/ysu/ckqaback/qa/QaWorkflowService.java`
- Modify: `backend/ckqa-back/src/main/java/org/ysu/ckqaback/qa/dto/QaMessageResponse.java`

- [x] **Step 1: 用 WebMvc 测试把最终对外契约固定住**

```java
@Test
void shouldSubmitMessageAsAsyncTask() throws Exception {
    QaTaskSubmissionResponse response = QaTaskSubmissionResponse.of(
            QaMessageResponse.of(101L, 5L, "user", 1, "请概括这套图谱的主题", LocalDateTime.of(2026, 4, 22, 15, 20), null, null),
            9001L,
            "pending",
            "queued",
            null,
            LocalDateTime.of(2026, 4, 22, 15, 20, 31)
    );
    given(qaWorkflowService.sendMessage(eq(5L), any())).willReturn(response);

    mockMvc.perform(post(ApiPaths.QA_SESSIONS + "/5/messages")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""
                            {
                              "mode": "global",
                              "content": "请概括这套图谱的主题"
                            }
                            """))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.taskId").value(9001))
            .andExpect(jsonPath("$.data.taskStatus").value("pending"))
            .andExpect(jsonPath("$.data.assistantMessage").doesNotExist());
}

@Test
void shouldGetTaskDetail() throws Exception {
    QaTaskDetailResponse response = QaTaskDetailResponse.of(
            9001L,
            101L,
            102L,
            "success",
            "done",
            "success",
            "global",
            "请概括这套图谱的主题",
            List.of("started graphrag query --method global", "done"),
            LocalDateTime.of(2026, 4, 22, 15, 20, 35),
            LocalDateTime.of(2026, 4, 22, 15, 21, 5),
            LocalDateTime.of(2026, 4, 22, 15, 22, 0),
            QaMessageResponse.of(102L, 5L, "assistant", 2, "图谱主题集中在操作系统概念网络", LocalDateTime.of(2026, 4, 22, 15, 22, 0), null, null),
            null
    );
    given(qaWorkflowService.getTaskDetail(5L, 9001L)).willReturn(response);

    mockMvc.perform(get(ApiPaths.QA_SESSIONS + "/5/tasks/9001"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.taskStatus").value("success"))
            .andExpect(jsonPath("$.data.latestLogs[0]").value("started graphrag query --method global"));
}

@Test
void shouldListMessagesWithTaskSummaryOnlyOnUserMessages() throws Exception {
    given(qaWorkflowService.listMessages(5L)).willReturn(List.of(
            QaMessageResponse.of(101L, 5L, "user", 1, "请概括这套图谱的主题", LocalDateTime.of(2026, 4, 22, 15, 20), "running", "running"),
            QaMessageResponse.of(102L, 5L, "assistant", 2, "图谱主题集中在操作系统概念网络", LocalDateTime.of(2026, 4, 22, 15, 22), null, null)
    ));

    mockMvc.perform(get(ApiPaths.QA_SESSIONS + "/5/messages"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data[0].taskStatus").value("running"))
            .andExpect(jsonPath("$.data[0].progressStage").value("running"))
            .andExpect(jsonPath("$.data[1].taskStatus").isEmpty());
}
```

- [x] **Step 2: 运行 WebMvc 测试，确认控制器契约还没完成**

Run: `cd backend/ckqa-back && ./mvnw -q -Dtest=QaSessionsControllerWebMvcTest test`

Expected: FAIL，因为控制器还没有 `GET /qa-sessions/{sessionId}/tasks/{taskId}`，提交接口返回体也还是旧的同步结构。

- [x] **Step 3: 改控制器和查询逻辑，让前端能轮询任务详情并在消息列表看到最小摘要**

```java
@PostMapping("/{id}/messages")
public ApiResponse<QaTaskSubmissionResponse> sendMessage(
        @PathVariable @Positive(message = "id必须大于0") Long id,
        @Valid @RequestBody CreateQaMessageRequest request
) {
    return ApiResponseUtils.success(qaWorkflowService.sendMessage(id, request));
}

@GetMapping("/{sessionId}/tasks/{taskId}")
public ApiResponse<QaTaskDetailResponse> getTaskDetail(
        @PathVariable @Positive(message = "sessionId必须大于0") Long sessionId,
        @PathVariable @Positive(message = "taskId必须大于0") Long taskId
) {
    return ApiResponseUtils.success(qaWorkflowService.getTaskDetail(sessionId, taskId));
}
```

`QaMessageResponse` 改成：

```java
@Getter
public class QaMessageResponse {

    private final Long id;
    private final Long sessionId;
    private final String role;
    private final Integer sequenceNo;
    private final String content;
    private final LocalDateTime createdAt;
    private final String taskStatus;
    private final String progressStage;

    public static QaMessageResponse of(
            Long id,
            Long sessionId,
            String role,
            Integer sequenceNo,
            String content,
            LocalDateTime createdAt,
            String taskStatus,
            String progressStage
    ) {
        return new QaMessageResponse(id, sessionId, role, sequenceNo, content, createdAt, taskStatus, progressStage);
    }

    public static QaMessageResponse fromEntity(QaMessages message) {
        return of(
                message.getId(),
                message.getSessionId(),
                message.getRole(),
                message.getSequenceNo(),
                message.getContent(),
                message.getCreatedAt(),
                null,
                null
        );
    }
}
```

`QaWorkflowService.listMessages()` 的关键实现：

```java
public List<QaMessageResponse> listMessages(Long sessionId) {
    qaSessionsService.getRequiredById(sessionId);
    List<QaMessages> messages = qaMessagesService.listBySessionId(sessionId);
    List<Long> userMessageIds = messages.stream()
            .filter(message -> "user".equals(message.getRole()))
            .map(QaMessages::getId)
            .toList();
    Map<Long, QaRetrievalLogs> taskMap = qaRetrievalLogsService.findLatestByUserMessageIds(userMessageIds);

    return messages.stream()
            .map(message -> {
                QaRetrievalLogs task = "user".equals(message.getRole()) ? taskMap.get(message.getId()) : null;
                return QaMessageResponse.of(
                        message.getId(),
                        message.getSessionId(),
                        message.getRole(),
                        message.getSequenceNo(),
                        message.getContent(),
                        message.getCreatedAt(),
                        task == null ? null : task.getTaskStatus(),
                        task == null ? null : task.getProgressStage()
                );
            })
            .toList();
}
```

`QaWorkflowService.getTaskDetail()` 的关键实现：

```java
public QaTaskDetailResponse getTaskDetail(Long sessionId, Long taskId) {
    qaSessionsService.getRequiredById(sessionId);
    QaRetrievalLogs task = qaRetrievalLogsService.getRequiredTask(sessionId, taskId);
    QaMessageResponse assistantMessage = null;
    if (task.getAssistantMessageId() != null) {
        QaMessages message = qaMessagesService.getById(task.getAssistantMessageId());
        assistantMessage = QaMessageResponse.fromEntity(message);
    }
    List<String> latestLogs = StringUtils.hasText(task.getLatestLogs())
            ? Arrays.stream(task.getLatestLogs().split("\\R")).toList()
            : List.of();
    return QaTaskDetailResponse.of(
            task.getId(),
            task.getUserMessageId(),
            task.getAssistantMessageId(),
            task.getTaskStatus(),
            task.getProgressStage(),
            task.getRetrievalStatus(),
            task.getQueryMode(),
            task.getQueryText(),
            latestLogs,
            task.getStartedAt(),
            task.getLastHeartbeatAt(),
            task.getFinishedAt(),
            assistantMessage,
            task.getErrorMessage()
    );
}
```

- [x] **Step 4: 再跑一遍 WebMvc 测试，确认外部 API 契约和 spec 对齐**

Run: `cd backend/ckqa-back && ./mvnw -q -Dtest=QaSessionsControllerWebMvcTest test`

Expected: PASS。

- [ ] **Step 5: 提交 Java 接口层改造**

```bash
git add backend/ckqa-back/src/main/java/org/ysu/ckqaback/controller/QaSessionsController.java backend/ckqa-back/src/main/java/org/ysu/ckqaback/qa/QaWorkflowService.java backend/ckqa-back/src/main/java/org/ysu/ckqaback/qa/dto/QaMessageResponse.java backend/ckqa-back/src/test/java/org/ysu/ckqaback/controller/QaSessionsControllerWebMvcTest.java
git commit -m "feat: expose async qa task endpoints"
```

## Task 6: 补文档、环境变量与端到端验证步骤

**Files:**
- Modify: `backend/ckqa-back/.env.example`
- Modify: `backend/ckqa-back/README.md`
- Modify: `graphrag_pipeline/README.md`

- [x] **Step 1: 先补最小文档断言，避免配置项和接口用法再次漂移**

在 `backend/ckqa-back/README.md` 里新增这段最小运行说明：

````md
## 异步问答任务

提交问题：

```bash
TASK_ID=$(curl -s -X POST http://127.0.0.1:8080/api/v1/qa-sessions/5/messages \
  -H 'Content-Type: application/json' \
  -d '{"mode":"global","content":"请概括这套图谱的主题"}' | jq -r '.data.taskId')

curl -s http://127.0.0.1:8080/api/v1/qa-sessions/5/tasks/$TASK_ID
```
````

在 `backend/ckqa-back/.env.example` 里补：

```properties
QUERY_TASK_POLLING_INTERVAL_SECONDS=10
QUERY_TASK_STALE_SECONDS=300
QUERY_TASK_POLLING_INTERVAL_SECONDS_LOCAL=10
QUERY_TASK_POLLING_INTERVAL_SECONDS_GLOBAL=30
QUERY_TASK_POLLING_INTERVAL_SECONDS_DRIFT=30
QUERY_TASK_STALE_SECONDS_LOCAL=300
QUERY_TASK_STALE_SECONDS_GLOBAL=1800
QUERY_TASK_STALE_SECONDS_DRIFT=1800
```

在 `graphrag_pipeline/README.md` 里补：

```md
## 内部任务接口

- `POST /v1/query-tasks`
- `GET /v1/query-tasks/{taskId}`
```

- [x] **Step 2: 跑完整的 Python 和 Java 目标测试集**

Run: `cd graphrag_pipeline && python -m pytest tests/test_query_task_manager.py tests/test_query_task_api.py tests/test_api_runtime_config.py tests/test_main_cli_mode.py tests/test_runtime_defaults.py -q`

Expected: PASS。

Run: `cd backend/ckqa-back && ./mvnw -q -Dtest=GraphRagTaskClientTest,QaWorkflowServiceTest,QaTaskWorkerTest,QaSessionsControllerWebMvcTest test`

Expected: PASS。

- [x] **Step 3: 做一次真实 API 冒烟闭环，但不要重建索引**

先确保数据库 schema 已按 `ocqa.sql` 的新增列同步到现有 MySQL。这里不要直接重跑整份初始化脚本覆盖老库，而是把本次涉及的 `ALTER TABLE qa_retrieval_logs ...` 增量语句手工执行到现有数据库。

然后启动 Python 与 Java：

```bash
cd graphrag_pipeline
conda run -n graphrag-oneapi python utils/main.py
```

```bash
cd backend/ckqa-back
./mvnw spring-boot:run
```

提交真实问题：

```bash
SUBMIT_RESPONSE=$(curl -s -X POST http://127.0.0.1:8080/api/v1/qa-sessions/5/messages \
  -H 'Content-Type: application/json' \
  -d '{"mode":"global","content":"请概括这套图谱的主题"}')
TASK_ID=$(printf '%s' "$SUBMIT_RESPONSE" | jq -r '.data.taskId')
```

预期：

- 立即返回 `taskId`
- `taskStatus=pending`
- 没有 `assistantMessage`
- 如果环境里没有 `jq`，可改用 `python -c 'import json,sys; print(json.load(sys.stdin)["data"]["taskId"])'` 从 `SUBMIT_RESPONSE` 提取 `taskId`

轮询任务详情：

```bash
curl -s http://127.0.0.1:8080/api/v1/qa-sessions/5/tasks/$TASK_ID
```

预期：

- 中途能看到 `taskStatus=running`
- `latestLogs` 有 tail 日志
- `lastHeartbeatAt` 会变化
- 最终 `taskStatus=success`
- `assistantMessage` 非空

最后检查消息列表：

```bash
curl -s http://127.0.0.1:8080/api/v1/qa-sessions/5/messages
```

预期：

- `role=user` 的消息有 `taskStatus` / `progressStage`
- `role=assistant` 的这两个字段为 `null`

- [x] **Step 4: 提交文档与验证收尾**

```bash
git add backend/ckqa-back/.env.example backend/ckqa-back/README.md graphrag_pipeline/README.md
git commit -m "docs: document async qa task flow"
```

- [ ] **Step 5: 汇总剩余风险，作为交接说明写进最终说明**

最终交接时明确写出这三点：

```text
1. Python 任务快照仍然是进程内内存态，Python 重启会导致 Java 侧把任务标记为 failed。
2. 这次修的是超时语义，不是 global / drift 查询速度；慢但持续有心跳属于正常运行。
3. Java 侧只轮询 Python 任务状态，不负责重建索引；现有知识图谱与 Neo4j 数据可继续复用。
```

## 2026-04-23 遗留问题修补记录

- [x] `drift` 模式已单独配置前端轮询建议和 stale 阈值，接口响应会返回 `recommendedPollingIntervalSeconds`、`staleTimeoutSeconds`、`timeoutMessage`。
- [x] Java 启动阶段新增 QA stale recover，会回收超过对应 mode 阈值的历史 `pending` / `running` 任务，避免 `qa_retrieval_logs` 长期停在活动态。
- [x] 审阅时同步补齐 `QaRetrievalLogsMapper.xml` 的异步任务字段映射，避免 XML resultMap 与实体/表结构漂移。
- [x] 2026-04-23 使用 main 分支现有图谱实测：`local` 约 110 秒，`global` 约 800 秒，`drift` 约 725 秒；默认策略调整为 `local/basic` 10 秒轮询 + 300 秒 stale，`global/drift` 30 秒轮询 + 1800 秒 stale。

## 自检清单

- spec 覆盖检查：
  - `POST /qa-sessions/{id}/messages` 异步受理：Task 4、Task 5
  - `GET /qa-sessions/{sessionId}/tasks/{taskId}`：Task 5
  - 消息列表仅补 `taskStatus` / `progressStage`：Task 5
  - Python `/v1/query-tasks` / `GET /v1/query-tasks/{taskId}`：Task 2
  - Python 主动心跳 + Java stale 阈值：Task 1、Task 3、Task 4
  - `qa_retrieval_logs` 扩展、`taskId=id`、`task_seq=1`：Task 4
  - 清理错误方向 global 调参：Task 2
  - 文档与冒烟：Task 6
- placeholder 扫描：
  - 本文没有使用 `TBD`、`TODO`、`implement later`、`Write tests for the above` 这类占位语句。
- 类型一致性检查：
  - Java 对外 `taskId` 全程指 `qa_retrieval_logs.id`
  - Python 对内 `pythonTaskId` 全程独立
  - `taskStatus` 与 `retrievalStatus` 未混用
  - `progressStage` 只承载编排层状态
