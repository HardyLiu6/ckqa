#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""兼容入口：委托到 `scripts/extraction_eval/`。"""

from __future__ import annotations

from _compat_wrapper import export_module

main = export_module("extraction_eval.extraction_schema", globals())


if __name__ == "__main__" and main is not None:
    raise SystemExit(main())
