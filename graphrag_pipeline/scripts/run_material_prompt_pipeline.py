#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""兼容入口：委托到 `scripts/prompt_tuning/`。"""

from __future__ import annotations

from _compat_wrapper import export_module

main = export_module("prompt_tuning.run_material_prompt_pipeline", globals())


if __name__ == "__main__":
    raise SystemExit(main())
