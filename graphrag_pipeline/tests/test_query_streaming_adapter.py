#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""GraphRAG 原生流式适配层测试。"""

from __future__ import annotations

import os
import sys
import unittest
import inspect
import asyncio
from pathlib import Path
from unittest.mock import patch

import pandas as pd


_PROJECT_ROOT = Path(__file__).resolve().parent.parent
_MODULE_DIR = _PROJECT_ROOT / "utils"
if str(_MODULE_DIR) not in sys.path:
    sys.path.insert(0, str(_MODULE_DIR))

from query_streaming_adapter import EvidenceCandidate, HybridLayer, NativeGraphRagStreamingAdapter, NativeStreamingConfig
from query_task_manager import QueryTaskRequest


def _request_for_mode(mode: str) -> QueryTaskRequest:
    return QueryTaskRequest(
        mode=mode,
        prompt="测试问题",
        index_run_id=None,
        data_dir_uri="user_1/kb_1/build_1/index/output",
        data_dir=Path("/tmp/ckqa-test-output"),
        stream_response=True,
        stream_source="native_graphrag",
    )


class TestQueryStreamingAdapter(unittest.IsolatedAsyncioTestCase):
    def test_default_native_streaming_modes_cover_all_qa_modes(self):
        with patch.dict(os.environ, {"CKQA_GRAPHRAG_NATIVE_STREAMING_MODES": ""}):
            config = NativeStreamingConfig.from_env()

        self.assertEqual(config.enabled_modes, {"basic", "local", "global", "drift", "hybrid_v0"})
        adapter = NativeGraphRagStreamingAdapter(root_dir=_PROJECT_ROOT, config=config)
        for mode in ("basic", "local", "global", "drift", "hybrid_v0"):
            with self.subTest(mode=mode):
                self.assertTrue(adapter.supports(_request_for_mode(mode)))

    async def test_basic_streaming_emits_context_progress_from_callbacks(self):
        async def fake_basic(**kwargs):
            callbacks = kwargs.get("callbacks")
            self.assertIsNotNone(callbacks)
            callback = callbacks[0] if isinstance(callbacks, list) else callbacks
            maybe_awaitable = callback.on_context({
                "text_units": [
                    {
                        "id": "tu-1",
                        "text": "操作系统通过进程管理和存储管理组织计算机资源。",
                        "source_file": "操作系统教材",
                        "page_start": 9,
                    }
                ]
            })
            if inspect.isawaitable(maybe_awaitable):
                await maybe_awaitable
            yield "回答正文"

        adapter = NativeGraphRagStreamingAdapter(
            root_dir=_PROJECT_ROOT,
            config=NativeStreamingConfig(),
            search_functions={"basic": fake_basic},
        )
        with patch("query_streaming_adapter._load_graphrag_config", return_value=object()), \
                patch("query_streaming_adapter._read_parquet", return_value=pd.DataFrame()):
            chunks = [chunk async for chunk in adapter.stream(_request_for_mode("basic"))]

        self.assertEqual(chunks[0].event, "progress")
        self.assertEqual(chunks[0].progress["type"], "retrieval_started")
        self.assertEqual(chunks[0].progress["mode"], "basic")
        self.assertIn("正在检索课程片段", chunks[0].progress["summary"])
        self.assertEqual(chunks[1].progress["type"], "context_selected")
        self.assertIn("课程片段", chunks[1].progress["summary"])
        self.assertEqual(chunks[1].progress["evidence"][0]["title"], "操作系统教材")
        self.assertEqual(chunks[2].text, "回答正文")

    async def test_basic_streaming_emits_answer_running_when_first_token_is_delayed(self):
        async def fake_basic(**kwargs):
            callbacks = kwargs.get("callbacks")
            self.assertIsNotNone(callbacks)
            callback = callbacks[0] if isinstance(callbacks, list) else callbacks
            callback.on_context({
                "text_units": [
                    {"id": "tu-1", "text": "进程是程序的一次执行过程。"},
                    {"id": "tu-2", "text": "线程是进程内的执行单元。"},
                ]
            })
            await asyncio.sleep(0.08)
            yield "回答正文"

        adapter = NativeGraphRagStreamingAdapter(
            root_dir=_PROJECT_ROOT,
            config=NativeStreamingConfig(),
            search_functions={"basic": fake_basic},
        )
        with patch.dict(os.environ, {"CKQA_GRAPHRAG_PROGRESS_HEARTBEAT_SECONDS": "0.02"}), \
                patch("query_streaming_adapter._load_graphrag_config", return_value=object()), \
                patch("query_streaming_adapter._read_parquet", return_value=pd.DataFrame()):
            iterator = adapter.stream(_request_for_mode("basic"))
            first_chunk = await asyncio.wait_for(anext(iterator), timeout=0.05)
            second_chunk = await asyncio.wait_for(anext(iterator), timeout=0.05)
            third_chunk = await asyncio.wait_for(anext(iterator), timeout=0.05)
            remaining_chunks = [chunk async for chunk in iterator]

        self.assertEqual(first_chunk.progress["type"], "retrieval_started")
        self.assertEqual(second_chunk.progress["type"], "context_selected")
        self.assertEqual(third_chunk.progress["type"], "answer_running")
        self.assertIn("仍在基于 2 个课程片段组织回答", third_chunk.progress["summary"])
        self.assertEqual(remaining_chunks[-1].text, "回答正文")

    async def test_global_streaming_emits_map_reduce_progress_from_callbacks(self):
        async def fake_global(**kwargs):
            callbacks = kwargs.get("callbacks")
            self.assertIsNotNone(callbacks)
            callback = callbacks[0] if isinstance(callbacks, list) else callbacks
            maybe_awaitable = callback.on_map_response_start([
                {"title": "操作系统定义报告"},
                {"title": "操作系统发展报告"},
            ])
            if inspect.isawaitable(maybe_awaitable):
                await maybe_awaitable
            maybe_awaitable = callback.on_reduce_response_start([
                {"response": "操作系统负责资源管理"},
            ])
            if inspect.isawaitable(maybe_awaitable):
                await maybe_awaitable
            yield "全局总结"

        adapter = NativeGraphRagStreamingAdapter(
            root_dir=_PROJECT_ROOT,
            config=NativeStreamingConfig(),
            search_functions={"global": fake_global},
        )
        frames = {
            "entities": pd.DataFrame(),
            "communities": pd.DataFrame(),
            "community_reports": pd.DataFrame(),
            "text_units": pd.DataFrame(),
            "relationships": pd.DataFrame(),
            "covariates": None,
        }
        with patch("query_streaming_adapter._load_graphrag_config", return_value=object()), \
                patch("query_streaming_adapter._load_common_frames", return_value=frames):
            chunks = [chunk async for chunk in adapter.stream(_request_for_mode("global"))]

        progress = [chunk.progress for chunk in chunks if chunk.event == "progress"]
        self.assertEqual([item["type"] for item in progress], ["retrieval_started", "map_started", "reduce_started"])
        self.assertIn("课程整体脉络", progress[0]["summary"])
        self.assertIn("2 个课程报告批次", progress[1]["summary"])
        self.assertIn("综合", progress[2]["summary"])
        self.assertEqual(chunks[-1].text, "全局总结")

    async def test_global_streaming_cleans_report_group_string_evidence(self):
        async def fake_global(**kwargs):
            callbacks = kwargs.get("callbacks")
            self.assertIsNotNone(callbacks)
            callback = callbacks[0] if isinstance(callbacks, list) else callbacks
            callback.on_map_response_start([
                (
                    "report group: id|title|occurrence weight|content|rank "
                    "175|'进程'核心概念及其资源管理与同步生态体系|0.15677966101694915|"
                    "# '进程'核心概念及其资源管理与同步生态体系 本社区以操作系统核心实体“进程”为绝对中心，"
                    "构建了一个高度内聚的知识生态网络。"
                )
            ])
            yield "全局总结"

        adapter = NativeGraphRagStreamingAdapter(
            root_dir=_PROJECT_ROOT,
            config=NativeStreamingConfig(),
            search_functions={"global": fake_global},
        )
        frames = {
            "entities": pd.DataFrame(),
            "communities": pd.DataFrame(),
            "community_reports": pd.DataFrame(),
            "text_units": pd.DataFrame(),
            "relationships": pd.DataFrame(),
            "covariates": None,
        }
        with patch("query_streaming_adapter._load_graphrag_config", return_value=object()), \
                patch("query_streaming_adapter._load_common_frames", return_value=frames):
            chunks = [chunk async for chunk in adapter.stream(_request_for_mode("global"))]

        map_started = next(
            chunk.progress for chunk in chunks
            if chunk.event == "progress" and chunk.progress["type"] == "map_started"
        )
        evidence = map_started["evidence"][0]
        self.assertEqual(evidence["kind"], "report_group")
        self.assertIn("进程", evidence["title"])
        self.assertNotIn("id|title|occurrence", evidence["snippet"])
        self.assertIn("知识生态网络", evidence["snippet"])

    async def test_global_streaming_splits_large_answer_chunk(self):
        async def fake_global(**kwargs):
            yield "全局总结第一段。全局总结第二段。全局总结第三段。"

        adapter = NativeGraphRagStreamingAdapter(
            root_dir=_PROJECT_ROOT,
            config=NativeStreamingConfig(),
            search_functions={"global": fake_global},
        )
        frames = {
            "entities": pd.DataFrame(),
            "communities": pd.DataFrame(),
            "community_reports": pd.DataFrame(),
            "text_units": pd.DataFrame(),
            "relationships": pd.DataFrame(),
            "covariates": None,
        }
        with patch.dict(os.environ, {
            "CKQA_GRAPHRAG_STREAM_DELTA_CHARS": "8",
            "CKQA_GRAPHRAG_STREAM_DELTA_SLEEP_SECONDS": "0",
        }), patch("query_streaming_adapter._load_graphrag_config", return_value=object()), \
                patch("query_streaming_adapter._load_common_frames", return_value=frames):
            chunks = [chunk async for chunk in adapter.stream(_request_for_mode("global"))]

        text_chunks = [chunk.text for chunk in chunks if chunk.text]
        self.assertGreater(len(text_chunks), 1)
        self.assertTrue(all(len(part) <= 8 for part in text_chunks))
        self.assertEqual("".join(text_chunks), "全局总结第一段。全局总结第二段。全局总结第三段。")

    async def test_progress_callback_is_emitted_before_delayed_first_answer_token(self):
        async def fake_global(**kwargs):
            callbacks = kwargs.get("callbacks")
            self.assertIsNotNone(callbacks)
            callback = callbacks[0] if isinstance(callbacks, list) else callbacks
            callback.on_map_response_start([
                {"title": "操作系统第一章整体报告"},
                {"title": "操作系统发展历史报告"},
            ])
            await asyncio.sleep(0.2)
            yield "全局总结"

        adapter = NativeGraphRagStreamingAdapter(
            root_dir=_PROJECT_ROOT,
            config=NativeStreamingConfig(),
            search_functions={"global": fake_global},
        )
        frames = {
            "entities": pd.DataFrame(),
            "communities": pd.DataFrame(),
            "community_reports": pd.DataFrame(),
            "text_units": pd.DataFrame(),
            "relationships": pd.DataFrame(),
            "covariates": None,
        }
        with patch("query_streaming_adapter._load_graphrag_config", return_value=object()), \
                patch("query_streaming_adapter._load_common_frames", return_value=frames):
            iterator = adapter.stream(_request_for_mode("global"))
            first_chunk = await asyncio.wait_for(anext(iterator), timeout=0.05)
            remaining_chunks = [chunk async for chunk in iterator]

        self.assertEqual(first_chunk.event, "progress")
        self.assertEqual(first_chunk.progress["type"], "retrieval_started")
        self.assertIn("课程整体脉络", first_chunk.progress["summary"])
        self.assertEqual(remaining_chunks[0].progress["type"], "map_started")
        self.assertIn("2 个课程报告批次", remaining_chunks[0].progress["summary"])
        self.assertEqual(remaining_chunks[-1].text, "全局总结")

    async def test_long_global_phase_emits_periodic_progress_heartbeat(self):
        async def fake_global(**kwargs):
            callbacks = kwargs.get("callbacks")
            self.assertIsNotNone(callbacks)
            callback = callbacks[0] if isinstance(callbacks, list) else callbacks
            callback.on_map_response_start([
                {"title": "操作系统第一章整体报告"},
                {"title": "操作系统发展历史报告"},
            ])
            await asyncio.sleep(0.08)
            yield "全局总结"

        adapter = NativeGraphRagStreamingAdapter(
            root_dir=_PROJECT_ROOT,
            config=NativeStreamingConfig(),
            search_functions={"global": fake_global},
        )
        frames = {
            "entities": pd.DataFrame(),
            "communities": pd.DataFrame(),
            "community_reports": pd.DataFrame(),
            "text_units": pd.DataFrame(),
            "relationships": pd.DataFrame(),
            "covariates": None,
        }
        with patch.dict(os.environ, {"CKQA_GRAPHRAG_PROGRESS_HEARTBEAT_SECONDS": "0.02"}), \
                patch("query_streaming_adapter._load_graphrag_config", return_value=object()), \
                patch("query_streaming_adapter._load_common_frames", return_value=frames):
            iterator = adapter.stream(_request_for_mode("global"))
            first_chunk = await asyncio.wait_for(anext(iterator), timeout=0.05)
            second_chunk = await asyncio.wait_for(anext(iterator), timeout=0.05)
            remaining_chunks = [chunk async for chunk in iterator]

        self.assertEqual(first_chunk.progress["type"], "retrieval_started")
        self.assertEqual(second_chunk.event, "progress")
        self.assertEqual(second_chunk.progress["type"], "map_started")
        self.assertIn("正在汇总 2 个课程报告批次", second_chunk.progress["summary"])
        running_chunk = next(chunk for chunk in remaining_chunks if chunk.event == "progress" and chunk.progress["type"] == "map_running")
        self.assertIn("仍在汇总 2 个课程报告批次", running_chunk.progress["summary"])
        self.assertEqual(remaining_chunks[-1].text, "全局总结")

    async def test_global_streaming_infers_reduce_progress_after_map_before_first_token(self):
        async def fake_global(**kwargs):
            callbacks = kwargs.get("callbacks")
            self.assertIsNotNone(callbacks)
            callback = callbacks[0] if isinstance(callbacks, list) else callbacks
            callback.on_map_response_start([
                {"title": "操作系统第一章整体报告"},
                {"title": "进程管理主题报告"},
            ])
            callback.on_map_response_end([
                {"response": [{"answer": "进程管理负责调度。", "score": 80}]},
                {"response": [{"answer": "同步互斥用于协作。", "score": 70}]},
            ])
            callback.on_context({
                "reports": [
                    {"title": "操作系统第一章整体报告", "summary": "OS 总体目标。"},
                    {"title": "进程管理主题报告", "summary": "进程、线程、同步。"},
                ]
            })
            await asyncio.sleep(0.08)
            yield "全局总结"

        adapter = NativeGraphRagStreamingAdapter(
            root_dir=_PROJECT_ROOT,
            config=NativeStreamingConfig(),
            search_functions={"global": fake_global},
        )
        frames = {
            "entities": pd.DataFrame(),
            "communities": pd.DataFrame(),
            "community_reports": pd.DataFrame(),
            "text_units": pd.DataFrame(),
            "relationships": pd.DataFrame(),
            "covariates": None,
        }
        with patch.dict(os.environ, {"CKQA_GRAPHRAG_PROGRESS_HEARTBEAT_SECONDS": "0.02"}), \
                patch("query_streaming_adapter._load_graphrag_config", return_value=object()), \
                patch("query_streaming_adapter._load_common_frames", return_value=frames):
            chunks = [chunk async for chunk in adapter.stream(_request_for_mode("global"))]

        progress = [chunk.progress for chunk in chunks if chunk.event == "progress"]
        progress_types = [item["type"] for item in progress]
        self.assertIn("reduce_started", progress_types)
        self.assertIn("reduce_running", progress_types)
        reduce_started = next(item for item in progress if item["type"] == "reduce_started")
        self.assertIn("综合课程报告", reduce_started["summary"])
        reduce_running = next(item for item in progress if item["type"] == "reduce_running")
        self.assertIn("稍后会开始输出回答", reduce_running["summary"])
        self.assertEqual(chunks[-1].text, "全局总结")

    async def test_local_streaming_emits_answer_running_with_context_metrics(self):
        async def fake_local(**kwargs):
            callbacks = kwargs.get("callbacks")
            self.assertIsNotNone(callbacks)
            callback = callbacks[0] if isinstance(callbacks, list) else callbacks
            callback.on_context({
                "entities": [{"title": "进程"}, {"title": "线程"}],
                "relationships": [{"description": "进程包含线程"}],
                "text_units": [{"text": "线程共享进程资源。"}],
            })
            await asyncio.sleep(0.08)
            yield "局部回答"

        adapter = NativeGraphRagStreamingAdapter(
            root_dir=_PROJECT_ROOT,
            config=NativeStreamingConfig(),
            search_functions={"local": fake_local},
        )
        frames = {
            "entities": pd.DataFrame(),
            "communities": pd.DataFrame(),
            "community_reports": pd.DataFrame(),
            "text_units": pd.DataFrame(),
            "relationships": pd.DataFrame(),
            "covariates": None,
        }
        with patch.dict(os.environ, {"CKQA_GRAPHRAG_PROGRESS_HEARTBEAT_SECONDS": "0.02"}), \
                patch("query_streaming_adapter._load_graphrag_config", return_value=object()), \
                patch("query_streaming_adapter._load_common_frames", return_value=frames):
            chunks = [chunk async for chunk in adapter.stream(_request_for_mode("local"))]

        progress = [chunk.progress for chunk in chunks if chunk.event == "progress"]
        self.assertEqual(progress[0]["type"], "retrieval_started")
        self.assertIn("课程概念、关系和片段", progress[0]["summary"])
        self.assertTrue(any(item["type"] == "answer_running" for item in progress))
        answer_running = next(item for item in progress if item["type"] == "answer_running")
        self.assertIn("课程概念", answer_running["summary"])
        self.assertEqual(chunks[-1].text, "局部回答")

    async def test_hybrid_streaming_emits_retrieval_started_before_context_selected(self):
        async def fake_basic(**kwargs):
            callbacks = kwargs.get("callbacks")
            self.assertIsNotNone(callbacks)
            callback = callbacks[0] if isinstance(callbacks, list) else callbacks
            callback.on_context({"text_units": [{"text": "混合检索证据。"}]})
            await asyncio.sleep(0.08)
            yield "混合回答"

        class FakeBm25:
            def search(self, question, top_k):  # noqa: ANN001 - 测试桩
                return [
                    EvidenceCandidate(
                        source="操作系统教材",
                        ref="tu-1",
                        text="进程管理负责资源分配和调度。",
                        score=2.0,
                        layer=HybridLayer.LOW,
                        metadata={"ref": "tu-1"},
                    )
                ]

        class FakeOrchestrator:
            bm25_top_k = 1
            bm25 = FakeBm25()

        adapter = NativeGraphRagStreamingAdapter(
            root_dir=_PROJECT_ROOT,
            config=NativeStreamingConfig(),
            hybrid_orchestrator_factory=lambda data_dir: FakeOrchestrator(),
            search_functions={"basic": fake_basic},
        )
        with patch.dict(os.environ, {"CKQA_GRAPHRAG_PROGRESS_HEARTBEAT_SECONDS": "0.02"}), \
                patch("query_streaming_adapter._load_graphrag_config", return_value=object()), \
                patch("query_streaming_adapter._read_parquet", return_value=pd.DataFrame()):
            chunks = [chunk async for chunk in adapter.stream(_request_for_mode("hybrid_v0"))]

        progress = [chunk.progress for chunk in chunks if chunk.event == "progress"]
        self.assertEqual(progress[0]["type"], "retrieval_started")
        self.assertIn("混合证据", progress[0]["summary"])
        self.assertEqual(progress[1]["type"], "context_selected")
        self.assertIn("混合检索依据", progress[1]["summary"])
        self.assertTrue(any(item["type"] == "answer_running" for item in progress))


if __name__ == "__main__":
    unittest.main()
