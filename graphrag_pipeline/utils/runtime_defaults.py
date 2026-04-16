#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
graphrag_pipeline 运行时默认配置
================================
统一维护项目版本、目标 GraphRAG 版本与仓库内默认输出路径，减少多脚本漂移。
"""

from __future__ import annotations

import re
from pathlib import Path


PROJECT_ROOT = Path(__file__).resolve().parents[1]
PYPROJECT_PATH = PROJECT_ROOT / "pyproject.toml"
DEFAULT_OUTPUT_DIR = PROJECT_ROOT / "output"
DEFAULT_LANCEDB_URI = DEFAULT_OUTPUT_DIR / "lancedb"


def _extract_required_value(pattern: str, text: str, label: str) -> str:
    match = re.search(pattern, text, flags=re.MULTILINE)
    if match is None:
        raise RuntimeError(f"无法从 {PYPROJECT_PATH} 解析 {label}")
    return match.group(1)


_PYPROJECT_TEXT = PYPROJECT_PATH.read_text(encoding="utf-8")

PROJECT_VERSION = _extract_required_value(
    r'^version = "([^"]+)"$',
    _PYPROJECT_TEXT,
    "project.version",
)

TARGET_GRAPHRAG_VERSION = _extract_required_value(
    r'"graphrag==([^"]+)"',
    _PYPROJECT_TEXT,
    "graphrag dependency version",
)
