#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""GraphRAG 原生流式适配层测试。"""

from __future__ import annotations

import os
import sys
import unittest
import inspect
from pathlib import Path
from unittest.mock import patch

import pandas as pd


_PROJECT_ROOT = Path(__file__).resolve().parent.parent
_MODULE_DIR = _PROJECT_ROOT / "utils"
if str(_MODULE_DIR) not in sys.path:
    sys.path.insert(0, str(_MODULE_DIR))

from query_streaming_adapter import NativeGraphRagStreamingAdapter, NativeStreamingConfig
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
        self.assertEqual(chunks[0].progress["type"], "context_selected")
        self.assertEqual(chunks[0].progress["mode"], "basic")
        self.assertIn("课程片段", chunks[0].progress["summary"])
        self.assertEqual(chunks[0].progress["evidence"][0]["title"], "操作系统教材")
        self.assertEqual(chunks[1].text, "回答正文")

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
        self.assertEqual([item["type"] for item in progress], ["map_started", "reduce_started"])
        self.assertIn("2 组课程报告", progress[0]["summary"])
        self.assertIn("综合", progress[1]["summary"])
        self.assertEqual(chunks[-1].text, "全局总结")


if __name__ == "__main__":
    unittest.main()
