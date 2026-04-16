#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
仓库根目录候选 Prompt 生成脚本入口
==================================

转发到 graphrag_pipeline 内的真实实现，便于从仓库根目录直接执行：

python scripts/generate_candidate_prompts.py --overwrite
"""

from __future__ import annotations

import runpy
from pathlib import Path


SCRIPT_PATH = (
    Path(__file__).resolve().parent.parent
    / "graphrag_pipeline"
    / "scripts"
    / "generate_candidate_prompts.py"
)


if __name__ == "__main__":
    runpy.run_path(str(SCRIPT_PATH), run_name="__main__")
