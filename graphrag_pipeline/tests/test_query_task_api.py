#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
查询任务 API 测试
================
固定 `/v1/query-tasks` 的提交与查询契约，避免后续再次漂移。
"""

from __future__ import annotations

import asyncio
import json
import sys
import unittest
from datetime import UTC, datetime
from pathlib import Path
from types import SimpleNamespace
from unittest.mock import AsyncMock, patch

from fastapi import HTTPException
from pydantic import ValidationError


_PROJECT_ROOT = Path(__file__).resolve().parent.parent
_MODULE_DIR = _PROJECT_ROOT / "utils"
if str(_MODULE_DIR) not in sys.path:
    sys.path.insert(0, str(_MODULE_DIR))

from main import QueryTaskCreateRequest, _build_query_cmd, _resolve_query_response, create_app, format_response
from query_task_manager import QueryTaskManager, QueryTaskRequest, QueryTaskSnapshot


class _FakeQueryTaskManager:
    def __init__(self) -> None:
        self.created_requests: list[tuple[str, str, int | None, str | None, str | None, str | None]] = []
        self.created_at = datetime(2026, 4, 22, 12, 20, 34, tzinfo=UTC)
        self.started_at = datetime(2026, 4, 22, 12, 20, 35, tzinfo=UTC)
        self.last_heartbeat_at = datetime(2026, 4, 22, 12, 20, 36, tzinfo=UTC)

    async def create_task(
        self,
        mode: str,
        prompt: str,
        index_run_id: int | None = None,
        data_dir_uri: str | None = None,
        retrieval_query: str | None = None,
        generation_context: str | None = None,
    ) -> QueryTaskSnapshot:
        self.created_requests.append((mode, prompt, index_run_id, data_dir_uri, retrieval_query, generation_context))
        return QueryTaskSnapshot(
            python_task_id="qt_20260422_000001_001",
            mode=mode,
            prompt=prompt,
            index_run_id=index_run_id,
            data_dir_uri=data_dir_uri,
            task_status="pending",
            progress_stage="queued",
            process_alive=False,
            created_at=self.created_at,
            latest_logs=[],
            retrieval_query=retrieval_query or prompt,
            generation_context=generation_context,
        )

    def get_snapshot(self, python_task_id: str) -> QueryTaskSnapshot | None:
        if python_task_id != "qt_20260422_000001_001":
            return None
        return QueryTaskSnapshot(
            python_task_id=python_task_id,
            mode="drift",
            prompt="请概括这套图谱的主题",
            index_run_id=18,
            data_dir_uri="user_2/kb_5/build_27/index/output",
            task_status="running",
            progress_stage="running",
            process_alive=True,
            created_at=self.created_at,
            started_at=self.started_at,
            last_heartbeat_at=self.last_heartbeat_at,
            latest_logs=["started graphrag query --method global"],
            sources=[
                {
                    "rank": 1,
                    "ref": "156",
                    "source_file": "操作系统教材",
                    "heading_path": "第3章/死锁",
                    "page_start": 123,
                    "page_end": 124,
                    "snippet": "死锁来源片段",
                }
            ],
            retrieval_query="请概括这套图谱的主题",
            generation_context="最近对话",
        )


def _get_route_endpoint(app, path: str, method: str):
    for route in app.routes:
        if getattr(route, "path", None) == path and method in getattr(route, "methods", []):
            return route.endpoint
    raise AssertionError(f"未找到路由 {method} {path}")


class TestQueryTaskApi(unittest.TestCase):
    def test_submit_and_get_query_task(self):
        task_manager = _FakeQueryTaskManager()
        app = create_app(task_manager=task_manager)
        submit_endpoint = _get_route_endpoint(app, "/v1/query-tasks", "POST")
        detail_endpoint = _get_route_endpoint(app, "/v1/query-tasks/{taskId}", "GET")

        submit_response = asyncio.run(
            submit_endpoint(QueryTaskCreateRequest(mode="drift", prompt="请概括这套图谱的主题"))
        )
        self.assertEqual(submit_response.status_code, 200)
        submit_payload = json.loads(submit_response.body)
        self.assertEqual(submit_payload["pythonTaskId"], "qt_20260422_000001_001")
        self.assertEqual(submit_payload["progressStage"], "queued")
        self.assertEqual(submit_payload["createdAt"], "2026-04-22T20:20:34")
        self.assertEqual(task_manager.created_requests, [("drift", "请概括这套图谱的主题", None, None, "请概括这套图谱的主题", None)])

        fetch_response = asyncio.run(detail_endpoint("qt_20260422_000001_001"))
        self.assertEqual(fetch_response.status_code, 200)
        fetch_payload = json.loads(fetch_response.body)
        self.assertEqual(fetch_payload["taskStatus"], "running")
        self.assertTrue(fetch_payload["processAlive"])
        self.assertEqual(fetch_payload["createdAt"], "2026-04-22T20:20:34")
        self.assertEqual(fetch_payload["startedAt"], "2026-04-22T20:20:35")
        self.assertEqual(fetch_payload["lastHeartbeatAt"], "2026-04-22T20:20:36")
        self.assertEqual(fetch_payload["indexRunId"], 18)
        self.assertEqual(fetch_payload["dataDirUri"], "user_2/kb_5/build_27/index/output")
        self.assertEqual(fetch_payload["retrievalQuery"], "请概括这套图谱的主题")
        self.assertEqual(fetch_payload["generationContext"], "最近对话")
        self.assertEqual(fetch_payload["sources"][0]["source_file"], "操作系统教材")

    def test_submit_query_task_accepts_backend_index_context(self):
        task_manager = _FakeQueryTaskManager()
        app = create_app(task_manager=task_manager)
        submit_endpoint = _get_route_endpoint(app, "/v1/query-tasks", "POST")

        response = asyncio.run(
            submit_endpoint(
                QueryTaskCreateRequest(
                    mode="basic",
                    prompt="问题",
                    indexRunId=18,
                    dataDirUri="user_2/kb_5/build_27/index/output",
                )
            )
        )

        self.assertEqual(response.status_code, 200)
        self.assertEqual(
            task_manager.created_requests,
            [("basic", "问题", 18, "user_2/kb_5/build_27/index/output", "问题", None)],
        )

    def test_submit_query_task_accepts_retrieval_query_and_generation_context(self):
        task_manager = _FakeQueryTaskManager()
        app = create_app(task_manager=task_manager)
        submit_endpoint = _get_route_endpoint(app, "/v1/query-tasks", "POST")

        response = asyncio.run(
            submit_endpoint(
                QueryTaskCreateRequest(
                    mode="basic",
                    prompt="它和资源分配图有什么关系？",
                    retrievalQuery="死锁和资源分配图有什么关系？",
                    generationContext="最近对话：什么是死锁？",
                    indexRunId=18,
                    dataDirUri="user_2/kb_5/build_27/index/output",
                )
            )
        )

        self.assertEqual(response.status_code, 200)
        self.assertEqual(
            task_manager.created_requests,
            [
                (
                    "basic",
                    "它和资源分配图有什么关系？",
                    18,
                    "user_2/kb_5/build_27/index/output",
                    "死锁和资源分配图有什么关系？",
                    "最近对话：什么是死锁？",
                )
            ],
        )

    def test_submit_query_task_accepts_hybrid_v0_mode(self):
        task_manager = _FakeQueryTaskManager()
        app = create_app(task_manager=task_manager)
        submit_endpoint = _get_route_endpoint(app, "/v1/query-tasks", "POST")

        response = asyncio.run(
            submit_endpoint(
                QueryTaskCreateRequest(
                    mode="hybrid_v0",
                    prompt="它和资源分配图有什么关系？",
                    retrievalQuery="死锁和资源分配图有什么关系？",
                    generationContext="最近对话：什么是死锁？",
                )
            )
        )

        self.assertEqual(response.status_code, 200)
        self.assertEqual(
            task_manager.created_requests,
            [("hybrid_v0", "它和资源分配图有什么关系？", None, None, "死锁和资源分配图有什么关系？", "最近对话：什么是死锁？")],
        )

    def test_query_task_manager_runs_hybrid_without_cli_command(self):
        command_calls: list[QueryTaskRequest] = []

        async def run_case():
            async def hybrid_runner(request: QueryTaskRequest):
                return SimpleNamespace(
                    answer="hybrid answer",
                    sources=[
                        SimpleNamespace(ref="tu-001", source="bm25", text="死锁来源片段", metadata={"rank": 1})
                    ],
                )

            manager = QueryTaskManager(
                command_factory=lambda request: command_calls.append(request) or ["should-not-run"],
                env_factory=lambda request: {},
                cwd=_PROJECT_ROOT,
                hybrid_answer_runner=hybrid_runner,
            )
            snapshot = await manager.create_task("hybrid_v0", "原问题", retrieval_query="独立检索问题")
            for _ in range(20):
                current = manager.get_snapshot(snapshot.python_task_id)
                if current and current.task_status == "success":
                    return current
                await asyncio.sleep(0.01)
            raise AssertionError("hybrid task did not finish")

        snapshot = asyncio.run(run_case())

        self.assertEqual(command_calls, [])
        self.assertEqual(snapshot.result_text, "hybrid answer")
        self.assertEqual(snapshot.retrieval_query, "独立检索问题")
        self.assertEqual(snapshot.sources[0]["ref"], "tu-001")
        self.assertEqual(snapshot.sources[0]["snippet"], "死锁来源片段")

    def test_query_command_uses_current_python_module_invocation(self):
        cmd = _build_query_cmd(
            QueryTaskRequest(
                mode="basic",
                prompt="问题",
                index_run_id=18,
                data_dir_uri="user_2/kb_5/build_27/index/output",
                data_dir=_PROJECT_ROOT / "runtime" / "kb-build-runs" / "user_2/kb_5/build_27/index/output",
                retrieval_query="独立检索问题",
            )
        )

        self.assertEqual(cmd[:4], [sys.executable, "-m", "graphrag", "query"])
        self.assertIn("--data", cmd)
        self.assertEqual(cmd[-1], "独立检索问题")
        self.assertNotEqual(cmd[0], "graphrag")

    def test_get_query_task_returns_404_when_snapshot_missing(self):
        app = create_app(task_manager=_FakeQueryTaskManager())
        detail_endpoint = _get_route_endpoint(app, "/v1/query-tasks/{taskId}", "GET")

        with self.assertRaises(HTTPException) as context:
            asyncio.run(detail_endpoint("qt_missing"))
        self.assertEqual(context.exception.status_code, 404)
        self.assertEqual(context.exception.detail, "Query task not found")

    def test_models_list_exposes_local_global_drift_basic_hybrid_only(self):
        app = create_app(task_manager=_FakeQueryTaskManager())
        models_endpoint = _get_route_endpoint(app, "/v1/models", "GET")

        response = asyncio.run(models_endpoint())
        self.assertEqual(response.status_code, 200)
        payload = json.loads(response.body)
        model_ids = [item["id"] for item in payload["data"]]
        self.assertEqual(
            model_ids,
            [
                "graphrag-local-search:latest",
                "graphrag-global-search:latest",
                "graphrag-drift-search:latest",
                "graphrag-basic-search:latest",
                "graphrag-hybrid-v0-search:latest",
            ],
        )
        self.assertNotIn("full-model:latest", model_ids)

    def test_submit_query_task_rejects_archived_full_mode(self):
        with self.assertRaises(ValidationError):
            QueryTaskCreateRequest(mode="full", prompt="请概括这套图谱的主题")

    def test_resolve_query_response_supports_drift_and_basic_and_rejects_full(self):
        with patch("main.run_graphrag_query_cli", new=AsyncMock(side_effect=["drift answer", "basic answer"])):
            drift_response = asyncio.run(_resolve_query_response("graphrag-drift-search:latest", "主题"))
            basic_response = asyncio.run(_resolve_query_response("graphrag-basic-search:latest", "主题"))

        self.assertEqual(drift_response, format_response("drift answer"))
        self.assertEqual(basic_response, format_response("basic answer"))

        with self.assertRaises(ValueError) as context:
            asyncio.run(_resolve_query_response("full-model:latest", "主题"))
        self.assertIn("full-model:latest", str(context.exception))


if __name__ == "__main__":
    unittest.main()
