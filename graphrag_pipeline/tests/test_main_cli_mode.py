#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
main.py 纯 CLI 模式测试
======================
约束 API 服务不再尝试旧 internal API，只保留 graphrag CLI 查询路径。
"""

from __future__ import annotations

import unittest
from pathlib import Path


_PROJECT_ROOT = Path(__file__).resolve().parent.parent


class TestMainCliMode(unittest.TestCase):
    """main.py 应明确收敛到纯 CLI 模式。"""

    def test_main_source_no_longer_references_internal_loader_or_internal_mode(self):
        text = (_PROJECT_ROOT / "utils" / "main.py").read_text(encoding="utf-8")

        self.assertNotIn("graphrag_internal_loader", text)
        self.assertNotIn("GRAPHRAG_INTERNALS_AVAILABLE", text)
        self.assertNotIn("load_context(", text)
        self.assertNotIn("setup_llm_and_embedder(", text)
        self.assertNotIn("setup_search_engines(", text)
        self.assertNotIn('"compat_mode": "internal_api"', text)

    def test_main_source_health_reports_cli_mode(self):
        text = (_PROJECT_ROOT / "utils" / "main.py").read_text(encoding="utf-8")

        self.assertIn('"compat_mode": "cli_query"', text)

    def test_main_source_uses_positional_query_argument_for_graphrag_3(self):
        text = (_PROJECT_ROOT / "utils" / "main.py").read_text(encoding="utf-8")

        self.assertIn('"query"', text)
        self.assertNotIn('"--query"', text)

    def test_main_source_applies_global_search_runtime_strategy_and_loopback_no_proxy(self):
        text = (_PROJECT_ROOT / "utils" / "main.py").read_text(encoding="utf-8")

        self.assertIn('"--community-level"', text)
        self.assertIn('"--response-type"', text)
        self.assertIn('"--dynamic-community-selection"', text)
        self.assertIn('"--no-dynamic-community-selection"', text)
        self.assertIn('env["NO_PROXY"]', text)
        self.assertIn('env["no_proxy"]', text)


if __name__ == "__main__":
    unittest.main()
