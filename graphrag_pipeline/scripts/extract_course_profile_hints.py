#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""从 section_docs/text_units 抽取课程画像 hints。"""

from __future__ import annotations

import argparse
import json
import sys
from pathlib import Path


PROJECT_ROOT = Path(__file__).resolve().parents[1]
UTILS_DIR = PROJECT_ROOT / "utils"
if str(UTILS_DIR) not in sys.path:
    sys.path.insert(0, str(UTILS_DIR))

from course_profile_hints import extract_course_profile_hints  # noqa: E402


def main() -> int:
    parser = argparse.ArgumentParser(description="抽取课程画像章节来源和关键词 hints")
    parser.add_argument("--course-id", required=True, help="课程 ID")
    parser.add_argument("--section-docs", action="append", default=[], help="section_docs.json 路径，可重复")
    parser.add_argument("--text-units", action="append", default=[], help="text_units.parquet 路径，可重复")
    parser.add_argument("--data-dir", action="append", default=[], help="GraphRAG index/output 目录，可重复")
    parser.add_argument("--keyword", action="append", default=[], help="额外种子关键词，可重复")
    parser.add_argument("--max-hints", type=int, default=24, help="最多输出 hints 数量")
    parser.add_argument("--output", type=Path, default=None, help="可选输出 JSON 文件")
    args = parser.parse_args()

    result = extract_course_profile_hints(
        course_id=args.course_id,
        section_docs_paths=[Path(item) for item in args.section_docs],
        text_units_paths=[Path(item) for item in args.text_units],
        data_dirs=[Path(item) for item in args.data_dir],
        seed_keywords=args.keyword,
        max_hints=args.max_hints,
    )
    payload = result.model_dump(by_alias=True)
    text = json.dumps(payload, ensure_ascii=False, indent=2)
    if args.output is not None:
        args.output.parent.mkdir(parents=True, exist_ok=True)
        args.output.write_text(text + "\n", encoding="utf-8")
    else:
        print(text)
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
