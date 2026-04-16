#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
仓库漂移审计测试
================
验证仓库级审计脚本可以发现活跃文档/脚本中的版本与命令漂移。
"""

from __future__ import annotations

import sys
import tempfile
import unittest
from pathlib import Path


_REPO_ROOT = Path(__file__).resolve().parents[2]
_SCRIPTS_DIR = _REPO_ROOT / "scripts"
if str(_SCRIPTS_DIR) not in sys.path:
    sys.path.insert(0, str(_SCRIPTS_DIR))

from audit_repo_drift import audit_repo


class TestRepoDriftAudit(unittest.TestCase):
    """仓库级活跃文件审计应稳定发现漂移。"""

    def test_audit_detects_legacy_version_secret_bad_3d_command_and_internal_api_drift(self):
        with tempfile.TemporaryDirectory() as tmp_dir:
            root = Path(tmp_dir)
            (root / "graphrag_pipeline").mkdir()
            (root / "graphrag_pipeline" / "pyproject.toml").write_text(
                '\n'.join(
                    [
                        '[project]',
                        'name = "graphrag-pipeline"',
                        'version = "2.0.1"',
                        'dependencies = [',
                        '    "graphrag==3.0.9",',
                        ']',
                    ]
                ),
                encoding="utf-8",
            )
            (root / "README.md").write_text(
                (
                    "当前仍使用 graphrag==2.7.0\n"
                    "python utils/graphrag3dknowledge.py --input output --port 8080\n"
                    'compat_mode: "internal_api"\n'
                ),
                encoding="utf-8",
            )
            (root / "live.py").write_text(
                'API_KEY = "sk-abcdefghijklmnopqrstuvwxyz123456"\n',
                encoding="utf-8",
            )

            findings = audit_repo(
                root,
                target_paths=[Path("README.md"), Path("live.py")],
            )

            messages = "\n".join(finding.message for finding in findings)
            self.assertIn("旧 GraphRAG 基线", messages)
            self.assertIn("--directory", messages)
            self.assertIn("旧 internal API", messages)
            self.assertIn("疑似硬编码敏感 token", messages)

    def test_current_repo_passes_active_drift_audit(self):
        findings = audit_repo(_REPO_ROOT)
        self.assertEqual(findings, [])


if __name__ == "__main__":
    unittest.main()
