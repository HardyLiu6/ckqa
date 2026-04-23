#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
API 运行时配置测试
=================
验证 main.py 运行时配置已收口到环境变量与仓库默认路径。
"""

from __future__ import annotations

import sys
import unittest
from pathlib import Path


_PROJECT_ROOT = Path(__file__).resolve().parent.parent
_MODULE_DIR = _PROJECT_ROOT / "utils"
if str(_MODULE_DIR) not in sys.path:
    sys.path.insert(0, str(_MODULE_DIR))

from api_runtime_config import load_api_runtime_config


class TestApiRuntimeConfig(unittest.TestCase):
    """运行时配置应优先对齐仓库环境变量约定。"""

    def test_prefers_environment_variables_and_resolves_repo_relative_paths(self):
        config = load_api_runtime_config(
            {
                "GRAPHRAG_STORAGE_DIR": "custom-output",
                "GRAPHRAG_LANCEDB_URI": "custom-output/custom-lancedb",
                "GRAPHRAG_API_HOST": "127.0.0.1",
                "GRAPHRAG_API_PORT": "18012",
            },
            load_dotenv_file=False,
        )

        self.assertEqual(config.output_dir, (_PROJECT_ROOT / "custom-output").resolve())
        self.assertEqual(
            config.lancedb_uri,
            str((_PROJECT_ROOT / "custom-output" / "custom-lancedb").resolve()),
        )
        self.assertEqual(config.api_host, "127.0.0.1")
        self.assertEqual(config.api_port, 18012)
        self.assertFalse(hasattr(config, "global_search_community_level"))
        self.assertFalse(hasattr(config, "global_search_dynamic_selection"))
        self.assertFalse(hasattr(config, "global_search_response_type"))

    def test_defaults_are_safe_and_do_not_expose_internal_api_fields(self):
        config = load_api_runtime_config({}, load_dotenv_file=False)

        self.assertEqual(config.api_host, "0.0.0.0")
        self.assertEqual(config.api_port, 8012)
        self.assertFalse(hasattr(config, "global_search_community_level"))
        self.assertFalse(hasattr(config, "global_search_dynamic_selection"))
        self.assertFalse(hasattr(config, "global_search_response_type"))
        self.assertFalse(hasattr(config, "chat_api_key"))
        self.assertFalse(hasattr(config, "embedding_api_key"))


if __name__ == "__main__":
    unittest.main()
