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
            command_factory=lambda request: _python_cmd(
                "import time; print('started', flush=True); time.sleep(0.2); print('done', flush=True)"
            ),
            env_factory=lambda request: os.environ.copy(),
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
            command_factory=lambda request: _python_cmd(
                "import sys; [print(f'line-{i}', flush=True) for i in range(6)]; sys.exit(3)"
            ),
            env_factory=lambda request: os.environ.copy(),
            cwd=_PROJECT_ROOT,
        )

        created = await manager.create_task("local", "线程")
        await asyncio.sleep(0.2)

        snapshot = manager.get_snapshot(created.python_task_id)
        self.assertEqual(snapshot.task_status, "failed")
        self.assertEqual(snapshot.return_code, 3)
        self.assertLessEqual(len(snapshot.latest_logs), 3)
        self.assertTrue("\n".join(snapshot.latest_logs).endswith("line-5"))

    async def test_marks_task_failed_when_process_startup_raises(self):
        manager = QueryTaskManager(
            heartbeat_interval_seconds=0.05,
            command_factory=lambda request: (_ for _ in ()).throw(RuntimeError("boom on startup")),
            env_factory=lambda request: os.environ.copy(),
            cwd=_PROJECT_ROOT,
        )

        created = await manager.create_task("global", "图谱主题")
        await asyncio.sleep(0.05)

        snapshot = manager.get_snapshot(created.python_task_id)
        self.assertEqual(snapshot.task_status, "failed")
        self.assertEqual(snapshot.progress_stage, "done")
        self.assertFalse(snapshot.process_alive)
        self.assertEqual(snapshot.error_message, "boom on startup")
        self.assertIsNotNone(snapshot.finished_at)

    async def test_includes_stderr_output_in_latest_logs(self):
        manager = QueryTaskManager(
            heartbeat_interval_seconds=0.05,
            command_factory=lambda request: _python_cmd(
                "import sys; sys.stderr.write('warn\\n'); sys.stderr.flush(); print('done', flush=True)"
            ),
            env_factory=lambda request: os.environ.copy(),
            cwd=_PROJECT_ROOT,
        )

        created = await manager.create_task("global", "图谱主题")
        await asyncio.sleep(0.15)

        snapshot = manager.get_snapshot(created.python_task_id)
        self.assertEqual(snapshot.task_status, "success")
        self.assertIn("warn", snapshot.latest_logs)
        self.assertEqual(snapshot.result_text, "done")

    async def test_preserves_indented_stdout_in_result_text(self):
        manager = QueryTaskManager(
            heartbeat_interval_seconds=0.05,
            command_factory=lambda request: _python_cmd(
                "print('  section', flush=True); print('    detail', flush=True)"
            ),
            env_factory=lambda request: os.environ.copy(),
            cwd=_PROJECT_ROOT,
        )

        created = await manager.create_task("global", "图谱主题")
        await asyncio.sleep(0.15)

        snapshot = manager.get_snapshot(created.python_task_id)
        self.assertEqual(snapshot.task_status, "success")
        self.assertEqual(snapshot.result_text, "  section\n    detail")

    async def test_uses_task_specific_data_dir_uri(self):
        manager = QueryTaskManager(
            heartbeat_interval_seconds=0.05,
            command_factory=lambda request: _python_cmd("print('ok', flush=True)"),
            env_factory=lambda request: {"GRAPHRAG_STORAGE_DIR": str(request.data_dir)},
            cwd=_PROJECT_ROOT,
            build_runs_root=_PROJECT_ROOT / "runtime" / "kb-build-runs",
        )

        created = await manager.create_task(
            "basic",
            "问题",
            index_run_id=18,
            data_dir_uri="user_2/kb_5/build_27/index/output",
        )
        await asyncio.sleep(0.15)

        snapshot = manager.get_snapshot(created.python_task_id)
        self.assertEqual(snapshot.task_status, "success")
        self.assertEqual(snapshot.index_run_id, 18)
        self.assertEqual(snapshot.data_dir_uri, "user_2/kb_5/build_27/index/output")

    async def test_rejects_task_data_dir_path_escape(self):
        manager = QueryTaskManager(
            heartbeat_interval_seconds=0.05,
            command_factory=lambda request: _python_cmd("print('ok', flush=True)"),
            env_factory=lambda request: {},
            cwd=_PROJECT_ROOT,
            build_runs_root=_PROJECT_ROOT / "runtime" / "kb-build-runs",
        )

        with self.assertRaises(ValueError):
            await manager.create_task("basic", "问题", index_run_id=18, data_dir_uri="../outside")


if __name__ == "__main__":
    unittest.main()
