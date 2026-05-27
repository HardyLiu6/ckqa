#!/usr/bin/env python3
# -*- coding: utf-8 -*-

from __future__ import annotations

import asyncio
import os
import sys
import tempfile
import unittest
from datetime import UTC, datetime, timedelta
from pathlib import Path

import pandas as pd

_PROJECT_ROOT = Path(__file__).resolve().parent.parent
_MODULE_DIR = _PROJECT_ROOT / "utils"
if str(_MODULE_DIR) not in sys.path:
    sys.path.insert(0, str(_MODULE_DIR))

from query_task_manager import QueryTaskManager, QueryTaskRequest, QueryTaskSnapshot, QueryTaskSnapshotStore


def _python_cmd(code: str) -> list[str]:
    return [sys.executable, "-c", code]


async def _wait_for_task_status(
    manager: QueryTaskManager,
    task_id: str,
    expected_status: str,
    *,
    timeout_seconds: float = 2.0,
):
    deadline = asyncio.get_running_loop().time() + timeout_seconds
    snapshot = manager.get_snapshot(task_id)
    while snapshot.task_status != expected_status and asyncio.get_running_loop().time() < deadline:
        await asyncio.sleep(0.02)
        snapshot = manager.get_snapshot(task_id)
    return snapshot


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

        finished = await _wait_for_task_status(manager, created.python_task_id, "success")
        self.assertEqual(finished.task_status, "success")
        self.assertEqual(finished.progress_stage, "done")
        self.assertIn("started graphrag query --method global", finished.latest_logs)
        self.assertIn("started", finished.latest_logs)
        self.assertIn("finished graphrag query --method global exit_code=0", finished.latest_logs)
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
        self.assertTrue(snapshot.latest_logs)
        self.assertIn("exit_code=3", "\n".join(snapshot.latest_logs))

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
        snapshot = await _wait_for_task_status(manager, created.python_task_id, "success")
        self.assertEqual(snapshot.task_status, "success")
        self.assertEqual(snapshot.result_text, "  section\n    detail")

    async def test_global_task_resolves_report_and_fallback_sources(self):
        with tempfile.TemporaryDirectory() as temp_dir:
            build_runs_root = Path(temp_dir) / "runtime" / "kb-build-runs"
            data_dir_uri = "user_2/kb_5/build_27/index/output"
            output_dir = build_runs_root / data_dir_uri
            output_dir.mkdir(parents=True)
            pd.DataFrame(
                [
                    {
                        "id": "community-7",
                        "human_readable_id": 7,
                        "title": "操作系统第一章主题报告",
                        "summary": "本报告概括操作系统定义、设计目标和发展历史。",
                    }
                ]
            ).to_parquet(output_dir / "community_reports.parquet")
            pd.DataFrame(
                [
                    {
                        "id": "text-unit-21",
                        "human_readable_id": 21,
                        "text": (
                            "source_file: 操作系统教材. heading_path_text: 第一章 > 操作系统目标. "
                            "page_start: 9. page_end: 10. 操作系统的发展目标包括方便性、有效性、可扩充性和开放性。"
                        ),
                        "document_id": "doc-os",
                    }
                ]
            ).to_parquet(output_dir / "text_units.parquet")

            manager = QueryTaskManager(
                heartbeat_interval_seconds=0.05,
                command_factory=lambda request: _python_cmd(
                    "print('第一章总结 [Data: Reports (7)]。', flush=True)"
                ),
                env_factory=lambda request: os.environ.copy(),
                cwd=_PROJECT_ROOT,
                build_runs_root=build_runs_root,
            )

            created = await manager.create_task(
                "global",
                "操作系统第一章发展目标",
                data_dir_uri=data_dir_uri,
            )
            await asyncio.sleep(0.2)

            snapshot = manager.get_snapshot(created.python_task_id)
            self.assertEqual(snapshot.task_status, "success")
            self.assertNotIn("[Data:", snapshot.result_text)
            self.assertIn("[来源 1]", snapshot.result_text)
            self.assertGreaterEqual(len(snapshot.sources), 2)
            self.assertEqual(snapshot.sources[0]["source_type"], "graphrag_report")
            self.assertEqual(snapshot.sources[1]["source_type"], "global_fallback_text_unit")

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
        await asyncio.sleep(0.25)

        snapshot = manager.get_snapshot(created.python_task_id)
        self.assertEqual(snapshot.task_status, "success")
        self.assertEqual(snapshot.index_run_id, 18)
        self.assertEqual(snapshot.data_dir_uri, "user_2/kb_5/build_27/index/output")

    async def test_hybrid_runner_receives_task_specific_data_dir(self):
        seen: list[QueryTaskRequest] = []

        async def hybrid_runner(request: QueryTaskRequest):
            seen.append(request)
            return type("HybridAnswer", (), {"answer": "hybrid ok", "sources": []})()

        manager = QueryTaskManager(
            heartbeat_interval_seconds=0.05,
            command_factory=lambda request: _python_cmd("raise SystemExit('should not run cli')"),
            env_factory=lambda request: {},
            cwd=_PROJECT_ROOT,
            build_runs_root=_PROJECT_ROOT / "runtime" / "kb-build-runs",
            hybrid_answer_runner=hybrid_runner,
        )

        created = await manager.create_task(
            "hybrid_v0",
            "原问题",
            index_run_id=18,
            data_dir_uri="user_2/kb_5/build_27/index/output",
            retrieval_query="独立检索问题",
        )
        await asyncio.sleep(0.15)

        snapshot = manager.get_snapshot(created.python_task_id)
        self.assertEqual(snapshot.task_status, "success")
        self.assertEqual(snapshot.result_text, "hybrid ok")
        self.assertEqual(seen[0].retrieval_query, "独立检索问题")
        self.assertEqual(seen[0].data_dir, _PROJECT_ROOT / "runtime" / "kb-build-runs" / "user_2/kb_5/build_27/index/output")

    async def test_hybrid_runner_receives_generation_context_without_cli(self):
        seen: list[QueryTaskRequest] = []

        async def hybrid_runner(request: QueryTaskRequest):
            seen.append(request)
            return type("HybridAnswer", (), {"answer": "hybrid ok", "sources": []})()

        manager = QueryTaskManager(
            heartbeat_interval_seconds=0.05,
            command_factory=lambda request: _python_cmd("raise SystemExit('should not run cli')"),
            env_factory=lambda request: {},
            cwd=_PROJECT_ROOT,
            hybrid_answer_runner=hybrid_runner,
        )

        created = await manager.create_task(
            "hybrid_v0",
            "它和资源分配图有什么关系？",
            retrieval_query="死锁和资源分配图有什么关系？",
            generation_context="最近对话：上一轮解释了死锁。",
        )
        await asyncio.sleep(0.15)

        snapshot = manager.get_snapshot(created.python_task_id)
        self.assertEqual(snapshot.task_status, "success")
        self.assertEqual(seen[0].retrieval_query, "死锁和资源分配图有什么关系？")
        self.assertEqual(seen[0].generation_context, "最近对话：上一轮解释了死锁。")

    async def test_local_history_success_uses_adapter_without_cli(self):
        command_calls: list[QueryTaskRequest] = []
        query_calls: list[dict[str, object]] = []

        class FakeHistoryAdapter:
            def readiness(self, data_dir_uri):
                return type("Readiness", (), {"supported": True, "error_message": None, "missing": []})()

            def query(self, **kwargs):
                query_calls.append(kwargs)
                return type(
                    "HistoryResult",
                    (),
                    {
                        "supported": True,
                        "answer": "history answer",
                        "raw_answer": "history answer",
                        "sources": [{"rank": 1, "ref": "156", "snippet": "死锁来源"}],
                        "history_applied": True,
                        "history_turns_used": 2,
                        "error_message": None,
                    },
                )()

        manager = QueryTaskManager(
            heartbeat_interval_seconds=0.05,
            command_factory=lambda request: command_calls.append(request) or _python_cmd("print('should not run')"),
            env_factory=lambda request: os.environ.copy(),
            cwd=_PROJECT_ROOT,
            history_adapter_factory=lambda: FakeHistoryAdapter(),
        )

        created = await manager.create_task(
            "local",
            "它和资源分配图有什么关系？",
            query_engine_strategy="local_history",
            conversation_history=[
                {"role": "user", "content": "什么是死锁？"},
                {"role": "assistant", "content": "死锁是进程互相等待资源。"},
            ],
        )
        await asyncio.sleep(0.15)

        snapshot = manager.get_snapshot(created.python_task_id)
        self.assertEqual(command_calls, [])
        self.assertEqual(snapshot.task_status, "success")
        self.assertEqual(snapshot.result_text, "history answer")
        self.assertEqual(snapshot.sources[0]["ref"], "156")
        self.assertEqual(snapshot.return_code, 0)
        self.assertTrue(snapshot.history_applied)
        self.assertEqual(snapshot.history_turns_used, 2)
        self.assertIsNone(snapshot.history_fallback_reason)
        self.assertEqual(
            snapshot.latest_logs,
            ["started local_history query task", "finished local_history query task"],
        )
        self.assertEqual(query_calls[0]["query"], "它和资源分配图有什么关系？")
        self.assertEqual(query_calls[0]["conversation_history"][0]["role"], "user")

    async def test_local_history_failure_falls_back_to_cli_and_records_reason(self):
        command_calls: list[QueryTaskRequest] = []

        class FakeHistoryAdapter:
            def readiness(self, data_dir_uri):
                return type("Readiness", (), {"supported": False, "error_message": "missing artifacts", "missing": []})()

            def query(self, **_kwargs):
                raise AssertionError("query should not run when readiness is unsupported")

        manager = QueryTaskManager(
            heartbeat_interval_seconds=0.05,
            command_factory=lambda request: command_calls.append(request) or _python_cmd("print('fallback answer', flush=True)"),
            env_factory=lambda request: os.environ.copy(),
            cwd=_PROJECT_ROOT,
            history_adapter_factory=lambda: FakeHistoryAdapter(),
        )

        created = await manager.create_task(
            "local",
            "问题",
            query_engine_strategy="local_history",
            conversation_history=[{"role": "user", "content": "上一轮"}],
        )
        await asyncio.sleep(0.2)

        snapshot = manager.get_snapshot(created.python_task_id)
        self.assertEqual(len(command_calls), 1)
        self.assertEqual(snapshot.task_status, "success")
        self.assertEqual(snapshot.result_text, "fallback answer")
        self.assertEqual(snapshot.return_code, 0)
        self.assertFalse(snapshot.history_applied)
        self.assertEqual(snapshot.history_turns_used, 0)
        self.assertEqual(snapshot.history_fallback_reason, "missing artifacts")
        self.assertTrue(any("fallback local_history to cli: missing artifacts" in line for line in snapshot.latest_logs))

    async def test_non_local_mode_with_local_history_strategy_still_uses_cli(self):
        command_calls: list[QueryTaskRequest] = []
        adapter_calls = 0

        def history_adapter_factory():
            nonlocal adapter_calls
            adapter_calls += 1
            return object()

        manager = QueryTaskManager(
            heartbeat_interval_seconds=0.05,
            command_factory=lambda request: command_calls.append(request) or _python_cmd("print('global answer', flush=True)"),
            env_factory=lambda request: os.environ.copy(),
            cwd=_PROJECT_ROOT,
            history_adapter_factory=history_adapter_factory,
        )

        created = await manager.create_task(
            "global",
            "问题",
            query_engine_strategy="local_history",
            conversation_history=[{"role": "user", "content": "上一轮"}],
        )
        await asyncio.sleep(0.15)

        snapshot = manager.get_snapshot(created.python_task_id)
        self.assertEqual(adapter_calls, 0)
        self.assertEqual(len(command_calls), 1)
        self.assertEqual(command_calls[0].mode, "global")
        self.assertEqual(snapshot.task_status, "success")
        self.assertEqual(snapshot.result_text, "global answer")
        self.assertFalse(snapshot.history_applied)

    async def test_persists_finished_snapshot_and_loads_it_after_restart(self):
        with tempfile.TemporaryDirectory() as temp_dir:
            store_dir = Path(temp_dir) / "query-tasks"
            manager = QueryTaskManager(
                heartbeat_interval_seconds=0.05,
                command_factory=lambda request: _python_cmd("print('persisted answer', flush=True)"),
                env_factory=lambda request: os.environ.copy(),
                cwd=_PROJECT_ROOT,
                build_runs_root=Path(temp_dir) / "runtime" / "kb-build-runs",
                task_store_dir=store_dir,
            )

            created = await manager.create_task(
                "basic",
                "原问题",
                index_run_id=18,
                data_dir_uri="user_2/kb_5/build_27/index/output",
                retrieval_query="独立问题",
                generation_context="最近对话",
                query_engine_strategy="local_history",
                conversation_history=[{"role": "user", "content": "上一轮"}],
            )
            await asyncio.sleep(0.2)

            reloaded_manager = QueryTaskManager(
                command_factory=lambda request: _python_cmd("raise SystemExit('should not run')"),
                env_factory=lambda request: os.environ.copy(),
                cwd=_PROJECT_ROOT,
                build_runs_root=Path(temp_dir) / "runtime" / "kb-build-runs",
                task_store_dir=store_dir,
            )
            snapshot = reloaded_manager.get_snapshot(created.python_task_id)

            self.assertIsNotNone(snapshot)
            self.assertEqual(snapshot.task_status, "success")
            self.assertEqual(snapshot.result_text, "persisted answer")
            self.assertEqual(snapshot.index_run_id, 18)
            self.assertEqual(snapshot.retrieval_query, "独立问题")
            self.assertEqual(snapshot.generation_context, "最近对话")
            self.assertEqual(snapshot.query_engine_strategy, "local_history")
            self.assertEqual(snapshot.conversation_history, [{"role": "user", "content": "上一轮"}])
            self.assertFalse(snapshot.history_applied)
            self.assertEqual(snapshot.history_turns_used, 0)

    async def test_snapshot_store_round_trips_history_diagnostics(self):
        with tempfile.TemporaryDirectory() as temp_dir:
            store = QueryTaskSnapshotStore(Path(temp_dir) / "query-tasks")
            snapshot = QueryTaskSnapshot(
                python_task_id="qt_history",
                mode="local",
                prompt="问题",
                task_status="success",
                progress_stage="done",
                process_alive=False,
                created_at=datetime.now(UTC),
                latest_logs=["started local_history query task", "finished local_history query task"],
                query_engine_strategy="local_history",
                conversation_history=[{"role": "user", "content": "上一轮"}],
                history_fallback_reason="missing artifacts",
                history_applied=True,
                history_turns_used=1,
            )

            store.save(snapshot)
            loaded = store.load()["qt_history"]

            self.assertEqual(loaded.query_engine_strategy, "local_history")
            self.assertEqual(loaded.conversation_history, [{"role": "user", "content": "上一轮"}])
            self.assertEqual(loaded.history_fallback_reason, "missing artifacts")
            self.assertTrue(loaded.history_applied)
            self.assertEqual(loaded.history_turns_used, 1)
            self.assertIsNone(loaded.partial_result_text)
            self.assertEqual(loaded.stream_event_seq, 0)

    async def test_ignores_corrupt_persisted_snapshot_and_keeps_new_tasks_available(self):
        with tempfile.TemporaryDirectory() as temp_dir:
            store_dir = Path(temp_dir) / "query-tasks"
            store_dir.mkdir(parents=True)
            (store_dir / "broken.json").write_text("{not-json", encoding="utf-8")

            manager = QueryTaskManager(
                heartbeat_interval_seconds=0.05,
                command_factory=lambda request: _python_cmd("print('ok', flush=True)"),
                env_factory=lambda request: os.environ.copy(),
                cwd=_PROJECT_ROOT,
                task_store_dir=store_dir,
            )

            created = await manager.create_task("basic", "问题")
            await asyncio.sleep(0.15)

            snapshot = manager.get_snapshot(created.python_task_id)
            self.assertEqual(snapshot.task_status, "success")
            self.assertEqual(snapshot.result_text, "ok")

    async def test_query_task_store_retention_keeps_recent_snapshot_count(self):
        with tempfile.TemporaryDirectory() as temp_dir:
            store_dir = Path(temp_dir) / "query-tasks"
            store_dir.mkdir(parents=True)
            for index in range(3):
                created_at = datetime.now(UTC) - timedelta(minutes=10 - index)
                (store_dir / f"old-{index}.json").write_text(
                    (
                        '{"pythonTaskId":"old-%d","mode":"basic","prompt":"q",'
                        '"taskStatus":"success","progressStage":"done","processAlive":false,'
                        '"createdAt":"%s","latestLogs":[]}'
                    ) % (index, created_at.isoformat()),
                    encoding="utf-8",
                )

            QueryTaskManager(
                command_factory=lambda request: _python_cmd("print('unused')"),
                env_factory=lambda request: os.environ.copy(),
                cwd=_PROJECT_ROOT,
                task_store_dir=store_dir,
                task_store_retention_limit=1,
            )

            remaining = sorted(path.name for path in store_dir.glob("*.json"))
            self.assertEqual(remaining, ["old-2.json"])

    async def test_hybrid_result_text_uses_student_readable_source_marks(self):
        with tempfile.TemporaryDirectory() as temp_dir:
            build_runs_root = Path(temp_dir) / "runtime" / "kb-build-runs"
            data_dir_uri = "user_2/kb_5/build_27/index/output"
            output_dir = build_runs_root / data_dir_uri
            output_dir.mkdir(parents=True)
            pd.DataFrame(
                [
                    {
                        "id": "text-unit-156-full-id",
                        "human_readable_id": 156,
                        "text": (
                            "source_file: 操作系统教材. heading_path_text: 第三章 > 死锁检测. "
                            "page_start: 123. page_end: 125. 资源分配图可以用于死锁检测。"
                        ),
                        "n_tokens": 20,
                        "document_id": "doc-os",
                        "entity_ids": [],
                        "relationship_ids": [],
                        "covariate_ids": [],
                    }
                ]
            ).to_parquet(output_dir / "text_units.parquet")

            async def hybrid_runner(request):
                return type(
                    "HybridAnswer",
                    (),
                    {"answer": "资源分配图可用于描述死锁状态 [Data: Sources (156)]。", "sources": []},
                )()

            manager = QueryTaskManager(
                heartbeat_interval_seconds=0.05,
                command_factory=lambda request: _python_cmd("raise SystemExit('should not run cli')"),
                env_factory=lambda request: {},
                cwd=_PROJECT_ROOT,
                build_runs_root=build_runs_root,
                hybrid_answer_runner=hybrid_runner,
            )

            created = await manager.create_task("hybrid_v0", "问题", data_dir_uri=data_dir_uri)
            await asyncio.sleep(0.15)

            snapshot = manager.get_snapshot(created.python_task_id)
            self.assertEqual(snapshot.task_status, "success")
            self.assertNotIn("[Data:", snapshot.result_text)
            self.assertIn("[来源 1]", snapshot.result_text)
            self.assertEqual(snapshot.sources[0]["ref"], "156")
            self.assertEqual(snapshot.sources[0]["source_type"], "graphrag_citation")

    async def test_serializes_hybrid_source_type_for_bm25_evidence(self):
        async def hybrid_runner(request):
            source = type(
                "EvidenceSource",
                (),
                {
                    "ref": "tu-bm25-001",
                    "source": "bm25-v6",
                    "text": "资源分配图片段",
                    "metadata": {"rank": 1},
                },
            )()
            return type("HybridAnswer", (), {"answer": "hybrid ok", "sources": [source]})()

        manager = QueryTaskManager(
            heartbeat_interval_seconds=0.05,
            command_factory=lambda request: _python_cmd("raise SystemExit('should not run cli')"),
            env_factory=lambda request: {},
            cwd=_PROJECT_ROOT,
            hybrid_answer_runner=hybrid_runner,
        )

        created = await manager.create_task("hybrid_v0", "问题")
        await asyncio.sleep(0.15)

        snapshot = manager.get_snapshot(created.python_task_id)
        self.assertEqual(snapshot.task_status, "success")
        self.assertEqual(snapshot.sources[0]["kind"], "bm25")
        self.assertEqual(snapshot.sources[0]["source_type"], "bm25")

    async def test_native_streaming_task_publishes_filtered_delta_and_final_snapshot(self):
        async def streaming_runner(request: QueryTaskRequest):
            yield type("Chunk", (), {"text": "死锁"})()
            yield type("Chunk", (), {"text": "[Data: Sources (156)]"})()
            yield type("Chunk", (), {"text": "会导致进程无法推进。"})()

        manager = QueryTaskManager(
            heartbeat_interval_seconds=0.01,
            command_factory=lambda request: _python_cmd("raise SystemExit('should not run cli')"),
            env_factory=lambda request: {},
            cwd=_PROJECT_ROOT,
            native_streaming_runner=streaming_runner,
        )

        created = await manager.create_task(
            "basic",
            "什么是死锁？",
            stream_response=True,
            stream_source="native_graphrag",
        )
        await asyncio.sleep(0.08)

        snapshot = manager.get_snapshot(created.python_task_id)
        self.assertEqual(snapshot.task_status, "success")
        self.assertTrue(snapshot.streaming_enabled)
        self.assertEqual(snapshot.streaming_provider, "native_graphrag")
        self.assertIsNone(snapshot.streaming_fallback_reason)
        self.assertIn("[已参考课程知识库]", snapshot.result_text)
        self.assertEqual(snapshot.partial_result_text, "死锁会导致进程无法推进。")
        self.assertGreater(snapshot.stream_event_seq, 0)

        replay, _, unsubscribe = manager.subscribe_events(created.python_task_id)
        unsubscribe()
        delta_text = "".join(item["data"].get("text", "") for item in replay if item["event"] == "delta")
        self.assertEqual(delta_text, "死锁会导致进程无法推进。")
        self.assertNotIn("[Data:", delta_text)
        self.assertTrue(all("seq" in item for item in replay))
        self.assertTrue(all(item["data"].get("eventSeq") == item["seq"] for item in replay))

    async def test_subscribe_events_can_resume_after_event_seq(self):
        async def streaming_runner(request: QueryTaskRequest):
            yield type("Chunk", (), {"text": "第一段。"})()
            yield type("Chunk", (), {"text": "第二段。"})()

        manager = QueryTaskManager(
            heartbeat_interval_seconds=0.01,
            command_factory=lambda request: _python_cmd("raise SystemExit('should not run cli')"),
            env_factory=lambda request: {},
            cwd=_PROJECT_ROOT,
            native_streaming_runner=streaming_runner,
        )

        created = await manager.create_task(
            "basic",
            "解释进程",
            stream_response=True,
            stream_source="native_graphrag",
        )
        await asyncio.sleep(0.08)

        replay, _, unsubscribe = manager.subscribe_events(created.python_task_id)
        unsubscribe()
        first_delta_seq = next(item["seq"] for item in replay if item["event"] == "delta")

        resumed, _, unsubscribe = manager.subscribe_events(created.python_task_id, after_event_seq=first_delta_seq)
        unsubscribe()

        resumed_delta_text = "".join(item["data"].get("text", "") for item in resumed if item["event"] == "delta")
        self.assertEqual(resumed_delta_text, "第二段。")
        self.assertTrue(all(item["seq"] > first_delta_seq for item in resumed))

    async def test_native_streaming_snapshot_keeps_full_visible_delta_when_replay_is_trimmed(self):
        chunks = [f"片段{i}。" for i in range(1, 8)]

        async def streaming_runner(request: QueryTaskRequest):
            for chunk in chunks:
                yield type("Chunk", (), {"text": chunk})()

        manager = QueryTaskManager(
            heartbeat_interval_seconds=0.01,
            command_factory=lambda request: _python_cmd("raise SystemExit('should not run cli')"),
            env_factory=lambda request: {},
            cwd=_PROJECT_ROOT,
            native_streaming_runner=streaming_runner,
            stream_replay_max_chars=1000,
        )

        created = await manager.create_task(
            "basic",
            "解释进程",
            stream_response=True,
            stream_source="native_graphrag",
        )
        await asyncio.sleep(0.1)

        replay, _, unsubscribe = manager.subscribe_events(created.python_task_id)
        unsubscribe()
        delta_text = "".join(item["data"].get("text", "") for item in replay if item["event"] == "delta")
        snapshot = manager.get_snapshot(created.python_task_id)
        self.assertEqual(snapshot.partial_result_text, "".join(chunks))
        self.assertTrue(delta_text)
        self.assertTrue("".join(chunks).endswith(delta_text))

    async def test_native_streaming_failure_falls_back_to_non_streaming_cli(self):
        async def streaming_runner(request: QueryTaskRequest):
            raise RuntimeError("stream failed")
            yield ""  # pragma: no cover

        command_calls: list[QueryTaskRequest] = []

        def command_factory(request: QueryTaskRequest):
            command_calls.append(request)
            return _python_cmd("print('fallback answer')")

        manager = QueryTaskManager(
            heartbeat_interval_seconds=0.01,
            command_factory=command_factory,
            env_factory=lambda request: {},
            cwd=_PROJECT_ROOT,
            native_streaming_runner=streaming_runner,
        )

        created = await manager.create_task(
            "basic",
            "什么是死锁？",
            stream_response=True,
            stream_source="native_graphrag",
        )
        await asyncio.sleep(0.15)

        snapshot = manager.get_snapshot(created.python_task_id)
        self.assertEqual(snapshot.task_status, "success")
        self.assertEqual(snapshot.result_text, "fallback answer")
        self.assertEqual(snapshot.streaming_fallback_reason, "stream failed")
        self.assertEqual(len(command_calls), 1)

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
