#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
运行时默认配置契约测试
====================
约束 graphrag_pipeline 的版本基线与仓库内默认路径，避免后续再次漂移。
"""

from __future__ import annotations

import sys
import unittest
from pathlib import Path


_PROJECT_ROOT = Path(__file__).resolve().parent.parent
_MODULE_DIR = _PROJECT_ROOT / "utils"
if str(_MODULE_DIR) not in sys.path:
    sys.path.insert(0, str(_MODULE_DIR))

from runtime_defaults import (
    DEFAULT_OUTPUT_DIR,
    PROJECT_ROOT,
    PROJECT_VERSION,
    TARGET_GRAPHRAG_VERSION,
)


class TestRuntimeDefaults(unittest.TestCase):
    """运行时默认值应与仓库结构和 pyproject 保持一致。"""

    def test_project_root_and_default_output_dir_follow_repo(self):
        self.assertEqual(PROJECT_ROOT, _PROJECT_ROOT)
        self.assertEqual(DEFAULT_OUTPUT_DIR, _PROJECT_ROOT / "output")

    def test_pyproject_declares_current_project_and_graphrag_versions(self):
        text = (_PROJECT_ROOT / "pyproject.toml").read_text(encoding="utf-8")

        self.assertIn(f'version = "{PROJECT_VERSION}"', text)
        self.assertIn(f'"graphrag=={TARGET_GRAPHRAG_VERSION}"', text)
        self.assertNotIn("2.7.0", text)

    def test_core_files_do_not_reference_removed_graphrag_baseline_or_external_repo(self):
        checked_files = [
            _PROJECT_ROOT / "README.md",
            _PROJECT_ROOT / "CLAUDE.md",
            _PROJECT_ROOT / "requirements.txt",
            _PROJECT_ROOT / "utils" / "main.py",
            _PROJECT_ROOT / "utils" / "apiTest.py",
            _PROJECT_ROOT / "utils" / "graphrag3dknowledge.py",
            _PROJECT_ROOT / "utils" / "neo4jTest.py",
        ]

        for path in checked_files:
            text = path.read_text(encoding="utf-8")
            self.assertNotIn("2.7.0", text, msg=f"{path} 仍包含旧版本号")
            self.assertNotIn(
                "/home/sunlight/Projects/graphrag-oneapi-exp",
                text,
                msg=f"{path} 仍包含仓库外默认路径",
            )

    def test_settings_prompt_paths_are_env_driven(self):
        text = (_PROJECT_ROOT / "settings.yaml").read_text(encoding="utf-8")

        self.assertIn("prompt: ${GRAPHRAG_ENTITY_EXTRACTION_PROMPT_FILE}", text)
        self.assertIn("prompt: ${GRAPHRAG_SUMMARIZE_DESCRIPTIONS_PROMPT_FILE}", text)
        self.assertIn("prompt: ${GRAPHRAG_CLAIM_EXTRACTION_PROMPT_FILE}", text)
        self.assertIn("graph_prompt: ${GRAPHRAG_COMMUNITY_REPORT_GRAPH_PROMPT_FILE}", text)
        self.assertIn("text_prompt: ${GRAPHRAG_COMMUNITY_REPORT_TEXT_PROMPT_FILE}", text)

    def test_settings_embedding_uses_float_encoding_for_oneapi_compat(self):
        text = (_PROJECT_ROOT / "settings.yaml").read_text(encoding="utf-8")

        self.assertIn("call_args:", text)
        self.assertIn("encoding_format: float", text)

    def test_settings_vector_store_tracks_storage_dir_instead_of_hardcoded_output(self):
        text = (_PROJECT_ROOT / "settings.yaml").read_text(encoding="utf-8")

        self.assertIn("db_uri: ${GRAPHRAG_STORAGE_DIR}/lancedb", text)
        self.assertNotIn("db_uri: output/lancedb", text)

    def test_settings_global_search_prefers_summary_based_dynamic_selection(self):
        text = (_PROJECT_ROOT / "settings.yaml").read_text(encoding="utf-8")

        self.assertIn("dynamic_search_use_summary: true", text)
        self.assertIn("dynamic_search_max_level: 1", text)

    def test_extract_claims_is_disabled_by_default_for_standard_index(self):
        text = (_PROJECT_ROOT / "settings.yaml").read_text(encoding="utf-8")

        self.assertIn("extract_claims:", text)
        self.assertIn("enabled: false", text)

    def test_extract_claims_prompt_uses_graphrag_3_0_9_literal_delimiters(self):
        text = (_PROJECT_ROOT / "prompts" / "extract_claims.txt").read_text(encoding="utf-8")

        self.assertIn("<|>", text)
        self.assertIn("##", text)
        self.assertIn("<|COMPLETE|>", text)
        self.assertNotIn("{tuple_delimiter}", text)
        self.assertNotIn("{record_delimiter}", text)
        self.assertNotIn("{completion_delimiter}", text)


if __name__ == "__main__":
    unittest.main()
