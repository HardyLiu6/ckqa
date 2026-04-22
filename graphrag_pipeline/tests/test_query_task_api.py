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
from pathlib import Path

from fastapi import HTTPException


_PROJECT_ROOT = Path(__file__).resolve().parent.parent
_MODULE_DIR = _PROJECT_ROOT / "utils"
if str(_MODULE_DIR) not in sys.path:
    sys.path.insert(0, str(_MODULE_DIR))

from main import QueryTaskCreateRequest, create_app
from query_task_manager import QueryTaskSnapshot, utc_now


class _FakeQueryTaskManager:
    def __init__(self) -> None:
        self.created_requests: list[tuple[str, str]] = []

    async def create_task(self, mode: str, prompt: str) -> QueryTaskSnapshot:
        self.created_requests.append((mode, prompt))
        return QueryTaskSnapshot(
            python_task_id="qt_20260422_000001_001",
            mode=mode,
            prompt=prompt,
            task_status="pending",
            progress_stage="queued",
            process_alive=False,
            created_at=utc_now(),
            latest_logs=[],
        )

    def get_snapshot(self, python_task_id: str) -> QueryTaskSnapshot | None:
        if python_task_id != "qt_20260422_000001_001":
            return None
        return QueryTaskSnapshot(
            python_task_id=python_task_id,
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
            submit_endpoint(QueryTaskCreateRequest(mode="global", prompt="请概括这套图谱的主题"))
        )
        self.assertEqual(submit_response.status_code, 200)
        submit_payload = json.loads(submit_response.body)
        self.assertEqual(submit_payload["pythonTaskId"], "qt_20260422_000001_001")
        self.assertEqual(submit_payload["progressStage"], "queued")
        self.assertEqual(task_manager.created_requests, [("global", "请概括这套图谱的主题")])

        fetch_response = asyncio.run(detail_endpoint("qt_20260422_000001_001"))
        self.assertEqual(fetch_response.status_code, 200)
        fetch_payload = json.loads(fetch_response.body)
        self.assertEqual(fetch_payload["taskStatus"], "running")
        self.assertTrue(fetch_payload["processAlive"])

    def test_get_query_task_returns_404_when_snapshot_missing(self):
        app = create_app(task_manager=_FakeQueryTaskManager())
        detail_endpoint = _get_route_endpoint(app, "/v1/query-tasks/{taskId}", "GET")

        with self.assertRaises(HTTPException) as context:
            asyncio.run(detail_endpoint("qt_missing"))
        self.assertEqual(context.exception.status_code, 404)
        self.assertEqual(context.exception.detail, "Query task not found")


if __name__ == "__main__":
    unittest.main()
