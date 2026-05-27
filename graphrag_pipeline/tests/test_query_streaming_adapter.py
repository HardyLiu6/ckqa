#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""GraphRAG 原生流式适配层测试。"""

from __future__ import annotations

import os
import sys
import unittest
from pathlib import Path
from unittest.mock import patch


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


class TestQueryStreamingAdapter(unittest.TestCase):
    def test_default_native_streaming_modes_cover_all_qa_modes(self):
        with patch.dict(os.environ, {"CKQA_GRAPHRAG_NATIVE_STREAMING_MODES": ""}):
            config = NativeStreamingConfig.from_env()

        self.assertEqual(config.enabled_modes, {"basic", "local", "global", "drift", "hybrid_v0"})
        adapter = NativeGraphRagStreamingAdapter(root_dir=_PROJECT_ROOT, config=config)
        for mode in ("basic", "local", "global", "drift", "hybrid_v0"):
            with self.subTest(mode=mode):
                self.assertTrue(adapter.supports(_request_for_mode(mode)))


if __name__ == "__main__":
    unittest.main()
