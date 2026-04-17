#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
LLM 客户端测试
==============
验证本地 OpenAI 兼容网关请求不会错误继承环境代理。
"""

from __future__ import annotations

import sys
import unittest
from pathlib import Path


_PROJECT_ROOT = Path(__file__).resolve().parent.parent
_SCRIPTS_DIR = _PROJECT_ROOT / "scripts"
if str(_SCRIPTS_DIR) not in sys.path:
    sys.path.insert(0, str(_SCRIPTS_DIR))

from llm_client import _should_bypass_env_proxy, _session_for_endpoint


class TestLlmClient(unittest.TestCase):
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
