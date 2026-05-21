#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""清理课程画像路由 LanceDB 中的历史向量。"""

from __future__ import annotations

import argparse
import json
import os
import sys
from pathlib import Path


PROJECT_ROOT = Path(__file__).resolve().parents[1]
UTILS_DIR = PROJECT_ROOT / "utils"
if str(UTILS_DIR) not in sys.path:
    sys.path.insert(0, str(UTILS_DIR))

from course_routing import CourseRoutingService  # noqa: E402


def _load_env_file(path: Path) -> None:
    if not path.exists():
        raise FileNotFoundError(f"env 文件不存在: {path}")
    for raw_line in path.read_text(encoding="utf-8").splitlines():
        line = raw_line.strip()
        if not line or line.startswith("#") or "=" not in line:
            continue
        key, value = line.split("=", 1)
        key = key.strip()
        value = value.strip().strip("'").strip('"')
        if key and key not in os.environ:
            os.environ[key] = value


def _load_keep_vector_ids(args: argparse.Namespace) -> list[str]:
    vector_ids = list(args.keep_vector_id or [])
    if args.keep_vector_ids_file is None:
        return vector_ids
    raw = args.keep_vector_ids_file.read_text(encoding="utf-8").strip()
    if not raw:
        return vector_ids
    if raw.startswith("["):
        payload = json.loads(raw)
        vector_ids.extend(str(item) for item in payload)
    else:
        vector_ids.extend(line.strip() for line in raw.splitlines() if line.strip())
    return vector_ids


def main() -> int:
    parser = argparse.ArgumentParser(description="清理课程画像路由 LanceDB 历史向量")
    parser.add_argument("--env-file", type=Path, default=None, help="可选 .env 文件，缺省不读取")
    parser.add_argument("--course-id", action="append", default=[], help="只清理指定课程，可重复")
    parser.add_argument("--keep-vector-id", action="append", default=[], help="必须保留的当前 vector_id，可重复")
    parser.add_argument("--keep-vector-ids-file", type=Path, default=None, help="当前 vector_id 清单，支持 JSON 数组或逐行文本")
    parser.add_argument("--execute", action="store_true", help="实际删除；不传时只 dry-run")
    parser.add_argument("--output", type=Path, default=None, help="可选 JSON 报告输出路径")
    args = parser.parse_args()

    if args.env_file is not None:
        _load_env_file(args.env_file)

    service = CourseRoutingService.from_env()
    result = service.prune_stale_profiles(
        keep_vector_ids=_load_keep_vector_ids(args),
        course_ids=args.course_id,
        dry_run=not args.execute,
    )
    payload = result.model_dump()
    payload["tableName"] = service.config.table_name
    payload["lancedbUri"] = service.config.lancedb_uri
    text = json.dumps(payload, ensure_ascii=False, indent=2)
    if args.output is not None:
        args.output.parent.mkdir(parents=True, exist_ok=True)
        args.output.write_text(text + "\n", encoding="utf-8")
    else:
        print(text)
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
