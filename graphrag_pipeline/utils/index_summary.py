"""读取 graphrag index 产物，输出图谱体量与阶段耗时的轻量摘要。

供 Java 后端在 indexRun 成功后调用一次：
    python utils/index_summary.py --output-dir <build_run>/index/output

输出格式（写到 stdout，单行 JSON）：
{
  "entityCount": 40,
  "relationshipCount": 38,
  "communityCount": 8,
  "communityReportCount": 8,
  "documentCount": 9,
  "textUnitCount": 9,
  "totalRuntimeSeconds": 309.1,
  "workflowDurations": {
    "extract_graph": 83.5,
    "create_community_reports": 152.3,
    ...
  }
}

设计要点：
- 任何 parquet 缺失都不阻断；缺失的字段返回 null。
- 不引入新依赖；pandas / pyarrow 已在 graphrag-oneapi 环境内（GraphRAG 自身依赖）。
- 输出严格单行 JSON，方便 Java 直接 readValue。
"""

from __future__ import annotations

import argparse
import json
import sys
from pathlib import Path
from typing import Any

import pandas as pd


PARQUET_COUNTS = {
    "entityCount": "entities.parquet",
    "relationshipCount": "relationships.parquet",
    "communityCount": "communities.parquet",
    "communityReportCount": "community_reports.parquet",
    "documentCount": "documents.parquet",
    "textUnitCount": "text_units.parquet",
}


def _count_rows(path: Path) -> int | None:
    """读取 parquet 行数；缺失或读取失败返回 None。"""
    if not path.is_file():
        return None
    try:
        # 用 pyarrow 的 ParquetFile.metadata.num_rows，仅读 footer，避免反序列化所有列
        import pyarrow.parquet as pq  # 局部导入避免无 pyarrow 时模块整体崩
        return int(pq.ParquetFile(path).metadata.num_rows)
    except Exception:  # noqa: BLE001 - 任何错误都降级为 null
        try:
            # 兜底：用 pandas 读第一列计行数
            df = pd.read_parquet(path)
            return len(df)
        except Exception:  # noqa: BLE001
            return None


def _read_stats(path: Path) -> dict[str, Any]:
    """读 stats.json 提取总耗时与各 workflow 耗时。"""
    if not path.is_file():
        return {"totalRuntimeSeconds": None, "workflowDurations": {}}
    try:
        with path.open("r", encoding="utf-8") as fp:
            stats = json.load(fp)
    except Exception:  # noqa: BLE001
        return {"totalRuntimeSeconds": None, "workflowDurations": {}}

    workflows = stats.get("workflows", {}) or {}
    durations = {
        name: round(float(meta.get("overall", 0.0)), 3)
        for name, meta in workflows.items()
        if isinstance(meta, dict)
    }
    total = stats.get("total_runtime")
    return {
        "totalRuntimeSeconds": round(float(total), 3) if total is not None else None,
        "workflowDurations": durations,
    }


def summarize(output_dir: Path) -> dict[str, Any]:
    summary: dict[str, Any] = {}
    for key, filename in PARQUET_COUNTS.items():
        summary[key] = _count_rows(output_dir / filename)
    summary.update(_read_stats(output_dir / "stats.json"))
    return summary


def main() -> int:
    parser = argparse.ArgumentParser(description="GraphRAG 索引产物轻量摘要")
    parser.add_argument(
        "--output-dir",
        required=True,
        help="graphrag index 输出目录（包含 entities.parquet / stats.json 等）",
    )
    args = parser.parse_args()

    output_dir = Path(args.output_dir)
    if not output_dir.is_dir():
        print(json.dumps({"error": f"output dir not found: {output_dir}"}), file=sys.stderr)
        return 2

    summary = summarize(output_dir)
    # 单行 JSON 便于 Java 解析；不带换行更安全
    print(json.dumps(summary, ensure_ascii=False, separators=(",", ":")))
    return 0


if __name__ == "__main__":
    sys.exit(main())
