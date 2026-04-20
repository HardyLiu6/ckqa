#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""脚本兼容导出工具。"""

from __future__ import annotations

import importlib
from typing import Any


def export_module(module_name: str, target_globals: dict[str, Any]) -> Any:
    """把实现模块的公开符号转抄到兼容入口模块。"""

    module = importlib.import_module(module_name)
    exported_names = [name for name in dir(module) if not name.startswith("__")]
    for name in exported_names:
        target_globals[name] = getattr(module, name)
    target_globals["__all__"] = exported_names
    return getattr(module, "main", None)
