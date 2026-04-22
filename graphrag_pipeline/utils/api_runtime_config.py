#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
GraphRAG API 运行时配置
======================
收口 main.py 的运行时参数，当前仅保留纯 CLI 模式所需配置。
"""

from __future__ import annotations

import os
from dataclasses import dataclass
from pathlib import Path
from typing import Mapping

from runtime_defaults import DEFAULT_OUTPUT_DIR, PROJECT_ROOT

try:
    from dotenv import load_dotenv
except ImportError:  # pragma: no cover - 依赖可选
    load_dotenv = None


_DOTENV_LOADED = False


def _load_project_dotenv_once() -> None:
    """尽量加载仓库内 `.env`，但不覆盖外部显式传入的环境变量。"""
    global _DOTENV_LOADED
    if _DOTENV_LOADED:
        return
    _DOTENV_LOADED = True
    if load_dotenv is None:
        return
    load_dotenv(PROJECT_ROOT / ".env", override=False)


def _resolve_repo_path(raw_value: str | None, default: Path) -> Path:
    if raw_value is None or not raw_value.strip():
        return default.resolve()

    path = Path(raw_value).expanduser()
    if not path.is_absolute():
        path = PROJECT_ROOT / path
    return path.resolve()


def _resolve_lancedb_uri(raw_value: str | None, output_dir: Path) -> str:
    if raw_value is None or not raw_value.strip():
        return str((output_dir / "lancedb").resolve())

    value = raw_value.strip()
    if "://" in value:
        return value
    return str(_resolve_repo_path(value, output_dir / "lancedb"))


def _parse_int(raw_value: str | None, default: int) -> int:
    if raw_value is None or not raw_value.strip():
        return default
    return int(raw_value)


def _parse_bool(raw_value: str | None, default: bool) -> bool:
    if raw_value is None or not raw_value.strip():
        return default

    normalized = raw_value.strip().casefold()
    if normalized in {"1", "true", "yes", "on"}:
        return True
    if normalized in {"0", "false", "no", "off"}:
        return False
    raise ValueError(f"无法解析布尔环境变量值: {raw_value!r}")


@dataclass(frozen=True)
class ApiRuntimeConfig:
    """GraphRAG API 运行时配置快照。"""

    output_dir: Path
    lancedb_uri: str
    api_host: str
    api_port: int
    global_search_community_level: int
    global_search_dynamic_selection: bool
    global_search_response_type: str

    @property
    def input_dir(self) -> str:
        return str(self.output_dir)


def load_api_runtime_config(
    environ: Mapping[str, str] | None = None,
    *,
    load_dotenv_file: bool = True,
) -> ApiRuntimeConfig:
    """
    从环境变量解析 API 运行时配置。

    解析规则：
    1. 显式传入的 `environ`
    2. 当前进程环境变量
    3. 仓库内 `.env`（仅在未显式传 `environ` 时尝试加载）
    4. 仓库默认值
    """
    if environ is None and load_dotenv_file:
        _load_project_dotenv_once()

    env = os.environ if environ is None else environ

    output_dir = _resolve_repo_path(
        env.get("GRAPHRAG_OUTPUT_DIR") or env.get("GRAPHRAG_STORAGE_DIR"),
        DEFAULT_OUTPUT_DIR,
    )
    lancedb_uri = _resolve_lancedb_uri(env.get("GRAPHRAG_LANCEDB_URI"), output_dir)

    return ApiRuntimeConfig(
        output_dir=output_dir,
        lancedb_uri=lancedb_uri,
        api_host=(env.get("GRAPHRAG_API_HOST") or "0.0.0.0").strip(),
        api_port=_parse_int(env.get("GRAPHRAG_API_PORT") or env.get("PORT"), 8012),
        global_search_community_level=_parse_int(
            env.get("GRAPHRAG_GLOBAL_SEARCH_COMMUNITY_LEVEL"),
            0,
        ),
        global_search_dynamic_selection=_parse_bool(
            env.get("GRAPHRAG_GLOBAL_SEARCH_DYNAMIC_SELECTION"),
            True,
        ),
        global_search_response_type=(
            env.get("GRAPHRAG_GLOBAL_SEARCH_RESPONSE_TYPE") or "Single Paragraph"
        ).strip(),
    )
