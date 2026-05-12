#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
LLM 客户端测试
==============
验证本地 OpenAI 兼容网关请求不会错误继承环境代理。
"""

from __future__ import annotations

import os
import sys
import unittest
from pathlib import Path
from unittest.mock import patch


_PROJECT_ROOT = Path(__file__).resolve().parent.parent
_SCRIPTS_DIR = _PROJECT_ROOT / "scripts"
if str(_SCRIPTS_DIR) not in sys.path:
    sys.path.insert(0, str(_SCRIPTS_DIR))

from llm_client import (
    LlmClientError,
    build_llm_client,
    _parse_non_stream_response,
    _parse_stream_events,
    _session_for_endpoint,
    _should_bypass_env_proxy,
)


class TestLlmClient(unittest.TestCase):
    def test_build_llm_client_prefers_extraction_model_for_candidate_extraction(self):
        with patch.dict(
            os.environ,
            {
                "GRAPHRAG_API_BASE": "http://127.0.0.1:3301/v1",
                "GRAPHRAG_CHAT_MODEL": "deepseek-v4-pro",
                "GRAPHRAG_EXTRACTION_MODEL": "deepseek-v4-flash",
            },
            clear=True,
        ):
            client = build_llm_client(
                root=_PROJECT_ROOT,
                model=None,
                timeout_seconds=30,
                retries=0,
            )

        self.assertEqual(client.model_name, "deepseek-v4-flash")

    def test_parse_non_stream_response_returns_finish_reason_and_usage(self):
        payload = {
            "choices": [
                {
                    "message": {"content": '{"entities": [], "relationships": []}'},
                    "finish_reason": "stop",
                }
            ],
            "usage": {"prompt_tokens": 12, "completion_tokens": 34, "total_tokens": 46},
        }

        result = _parse_non_stream_response(payload, request_mode="sync")

        self.assertEqual(result.content, '{"entities": [], "relationships": []}')
        self.assertEqual(result.finish_reason, "stop")
        self.assertEqual(result.usage["total_tokens"], 46)
        self.assertEqual(result.request_mode, "sync")

    def test_parse_stream_events_only_joins_delta_content(self):
        chunks = [
            'data: {"choices":[{"delta":{"reasoning_content":"先思考"},"finish_reason":null}]}'.encode("utf-8"),
            'data: {"choices":[{"delta":{"content":"{"},"finish_reason":null}]}'.encode("utf-8"),
            'data: {"choices":[{"delta":{"content":"\\"entities\\": []"},"finish_reason":null}]}'.encode("utf-8"),
            'data: {"choices":[{"delta":{"content":", \\"relationships\\": []}"},"finish_reason":"stop"}]}'.encode(
                "utf-8"
            ),
            'data: {"choices":[],"usage":{"total_tokens":99}}'.encode("utf-8"),
            b"data: [DONE]",
        ]

        result = _parse_stream_events(chunks)

        self.assertEqual(result.content, '{"entities": [], "relationships": []}')
        self.assertEqual(result.finish_reason, "stop")
        self.assertEqual(result.usage["total_tokens"], 99)
        self.assertEqual(result.request_mode, "stream")
        self.assertTrue(result.reasoning_seen)
        self.assertEqual(result.raw_chunks, 5)

    def test_parse_stream_events_stops_when_total_timeout_is_exceeded(self):
        def endless_reasoning_chunks():
            while True:
                yield b'data: {"choices":[{"delta":{"reasoning_content":"thinking"},"finish_reason":null}]}'

        with self.assertRaisesRegex(LlmClientError, "流式响应超过总超时"):
            _parse_stream_events(endless_reasoning_chunks(), max_total_seconds=0)

    def test_should_bypass_env_proxy_for_local_openai_compatible_gateway(self):
        self.assertTrue(_should_bypass_env_proxy("http://127.0.0.1:3000/v1/chat/completions"))
        self.assertTrue(_should_bypass_env_proxy("http://localhost:3000/v1/chat/completions"))
        self.assertTrue(_should_bypass_env_proxy("http://[::1]:3000/v1/chat/completions"))

    def test_should_not_bypass_env_proxy_for_remote_endpoint(self):
        self.assertFalse(_should_bypass_env_proxy("https://api.openai.com/v1/chat/completions"))
        self.assertFalse(_should_bypass_env_proxy("http://10.0.0.8:3000/v1/chat/completions"))

    def test_session_for_local_endpoint_disables_env_proxy(self):
        session = _session_for_endpoint("http://127.0.0.1:3000/v1/chat/completions")
        self.assertFalse(session.trust_env)

    def test_session_for_remote_endpoint_keeps_env_proxy_behavior(self):
        session = _session_for_endpoint("https://api.openai.com/v1/chat/completions")
        self.assertTrue(session.trust_env)


if __name__ == "__main__":
    unittest.main()
