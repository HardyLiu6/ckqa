#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
导出质量验收脚本
================
对标准化导出结果做“全量统计 + 异常抽样”验收，帮助快速发现
目录伪章节、页码异常、占位符残留、字段缺失等问题。
"""

from __future__ import annotations

import argparse
import json
import re
import sys
from collections import Counter, defaultdict
from pathlib import Path
from typing import Any, Dict, Iterable, List, Sequence

from normalized_document import validate_normalized_document_dict


_TRAILING_PAGE_NOISE_RE = re.compile(
    r"(?:\.{2,}|…{2,}|·{2,})\s*\d+$|\s+\d+\s*$"
)
_PLACEHOLDER_PATTERNS = {
    "image_placeholder": "[IMAGE]",
    "table_placeholder": "[TABLE]",
}


def discover_export_files(
    inputs: Sequence[Path],
    filenames: Sequence[str],
) -> List[Path]:
    """从文件或目录参数中发现待验收的导出文件。"""
    discovered: List[Path] = []
    filename_set = set(filenames)

    for input_path in inputs:
        path = input_path.resolve()
        if not path.exists():
            continue
        if path.is_file():
            discovered.append(path)
            continue
        for file in path.rglob("*.json"):
            if not filename_set or file.name in filename_set:
                discovered.append(file.resolve())

    return sorted(set(discovered))


def load_export_file(path: Path) -> List[Dict[str, Any]]:
    """加载单个导出 JSON 文件。"""
    payload = json.loads(path.read_text(encoding="utf-8"))
    if not isinstance(payload, list):
        raise ValueError(f"{path} 不是 JSON 数组")

    records: List[Dict[str, Any]] = []
    for index, item in enumerate(payload, start=1):
        if not isinstance(item, dict):
            raise ValueError(f"{path} 第 {index} 条记录不是对象")
        records.append(item)
    return records


def infer_document_format(records: Sequence[Dict[str, Any]]) -> str:
    """推断导出文件格式。"""
    if not records:
        return "empty"

    sample = records[0]
    if "content" in sample and "heading_path" in sample:
        return "normalized"
    if "title" in sample and "text" in sample:
        return "graphrag"
    return "unknown"


def _derive_heading_title(record: Dict[str, Any], doc_format: str) -> str:
    if doc_format == "normalized":
        heading_path = record.get("heading_path")
        if isinstance(heading_path, list) and heading_path:
            return str(heading_path[-1]).strip()
        return ""
    return str(record.get("title", "")).strip()


def _derive_text(record: Dict[str, Any], doc_format: str) -> str:
    if doc_format == "normalized":
        return str(record.get("content", "")).strip()
    return str(record.get("text", "")).strip()


def _derive_page_start(record: Dict[str, Any]) -> Any:
    return record.get("page_start")


def _derive_page_end(record: Dict[str, Any]) -> Any:
    return record.get("page_end")


def _collect_issue(
    issue_samples: Dict[str, List[Dict[str, Any]]],
    issue_counts: Counter,
    issue_name: str,
    sample_size: int,
    payload: Dict[str, Any],
) -> None:
    issue_counts[issue_name] += 1
    if len(issue_samples[issue_name]) < sample_size:
        issue_samples[issue_name].append(payload)


def audit_records(
    records: Sequence[Dict[str, Any]],
    doc_format: str,
    sample_size: int = 5,
) -> Dict[str, Any]:
    """对单个导出文件中的记录做质量验收。"""
    issue_counts: Counter = Counter()
    issue_samples: Dict[str, List[Dict[str, Any]]] = defaultdict(list)
    course_counter: Counter = Counter()
    doc_type_counter: Counter = Counter()
    content_lengths: List[int] = []

    for index, record in enumerate(records, start=1):
        heading_title = _derive_heading_title(record, doc_format)
        content = _derive_text(record, doc_format)
        page_start = _derive_page_start(record)
        page_end = _derive_page_end(record)
        sample_payload = {
            "index": index,
            "title": heading_title,
            "page_start": page_start,
            "page_end": page_end,
        }

        if record.get("course_id"):
            course_counter[str(record["course_id"])] += 1
        if record.get("document_type"):
            doc_type_counter[str(record["document_type"])] += 1

        content_lengths.append(len(content))

        if not content:
            _collect_issue(issue_samples, issue_counts, "empty_content", sample_size, sample_payload)

        for issue_name, marker in _PLACEHOLDER_PATTERNS.items():
            if marker in content:
                _collect_issue(issue_samples, issue_counts, issue_name, sample_size, sample_payload)

        if heading_title and _TRAILING_PAGE_NOISE_RE.search(heading_title):
            _collect_issue(issue_samples, issue_counts, "noisy_heading", sample_size, sample_payload)

        if not isinstance(page_start, int) or not isinstance(page_end, int) or page_start < 1 or page_end < page_start:
            _collect_issue(issue_samples, issue_counts, "invalid_page_range", sample_size, sample_payload)

        if len(content) < 80:
            _collect_issue(issue_samples, issue_counts, "short_document", sample_size, sample_payload)

        if len(content) > 3000:
            _collect_issue(issue_samples, issue_counts, "long_document", sample_size, sample_payload)

        if doc_format == "normalized":
            errors = validate_normalized_document_dict(record)
            if errors:
                payload = dict(sample_payload)
                payload["errors"] = errors[:5]
                _collect_issue(issue_samples, issue_counts, "normalized_schema_error", sample_size, payload)

    length_stats = {
        "min": min(content_lengths) if content_lengths else 0,
        "median": _median(content_lengths),
        "max": max(content_lengths) if content_lengths else 0,
        "avg": round(sum(content_lengths) / len(content_lengths), 2) if content_lengths else 0,
    }

    return {
        "documents_count": len(records),
        "issue_counts": dict(issue_counts),
        "issue_samples": dict(issue_samples),
        "content_length_stats": length_stats,
        "course_distribution": dict(course_counter),
        "document_type_distribution": dict(doc_type_counter),
    }


def audit_export_file(path: Path, sample_size: int = 5) -> Dict[str, Any]:
    """验收单个导出文件。"""
    records = load_export_file(path)
    doc_format = infer_document_format(records)
    report = audit_records(records, doc_format=doc_format, sample_size=sample_size)
    report["file"] = str(path)
    report["format"] = doc_format
    return report


def build_aggregate_report(file_reports: Sequence[Dict[str, Any]]) -> Dict[str, Any]:
    """汇总多个文件的验收结果。"""
    total_issue_counts: Counter = Counter()
    total_documents = 0
    formats: Counter = Counter()

    for report in file_reports:
        total_documents += report.get("documents_count", 0)
        formats[report.get("format", "unknown")] += 1
        total_issue_counts.update(report.get("issue_counts", {}))

    return {
        "files_count": len(file_reports),
        "documents_count": total_documents,
        "format_distribution": dict(formats),
        "issue_counts": dict(total_issue_counts),
        "files": list(file_reports),
    }


def _median(values: Sequence[int]) -> float:
    if not values:
        return 0
    ordered = sorted(values)
    middle = len(ordered) // 2
    if len(ordered) % 2 == 1:
        return ordered[middle]
    return round((ordered[middle - 1] + ordered[middle]) / 2, 2)


def _print_report(report: Dict[str, Any]) -> None:
    print(f"[验收] 文件数: {report['files_count']}, 文档数: {report['documents_count']}")
    print(f"[验收] 格式分布: {report['format_distribution']}")
    print(f"[验收] 问题统计: {report['issue_counts']}")
    for file_report in report["files"]:
        print(
            f"\n- {file_report['file']}"
            f"\n  format={file_report['format']}"
            f", documents={file_report['documents_count']}"
            f", chars={file_report['content_length_stats']}"
        )
        if file_report["issue_counts"]:
            print(f"  issues={file_report['issue_counts']}")


def main(argv: Sequence[str] | None = None) -> int:
    parser = argparse.ArgumentParser(description="验收标准化导出结果")
    parser.add_argument(
        "paths",
        nargs="*",
        default=["."],
        help="待扫描的文件或目录，默认当前目录",
    )
    parser.add_argument(
        "--filename",
        action="append",
        default=["normalized_docs.json", "section_docs.json"],
        help="目录扫描时匹配的文件名，可重复传入",
    )
    parser.add_argument(
        "--sample-size",
        type=int,
        default=5,
        help="每类问题保留的抽样条数",
    )
    parser.add_argument(
        "--output",
        default=None,
        help="可选：将验收报告写入 JSON 文件",
    )

    args = parser.parse_args(argv)
    input_paths = [Path(item) for item in args.paths]
    files = discover_export_files(input_paths, filenames=args.filename)

    if not files:
        print("[验收] 未找到匹配的导出文件", file=sys.stderr)
        return 1

    file_reports = [audit_export_file(path, sample_size=args.sample_size) for path in files]
    report = build_aggregate_report(file_reports)
    _print_report(report)

    if args.output:
        output_path = Path(args.output)
        output_path.parent.mkdir(parents=True, exist_ok=True)
        output_path.write_text(
            json.dumps(report, ensure_ascii=False, indent=2),
            encoding="utf-8",
        )
        print(f"\n[验收] 报告已写入: {output_path}")

    return 0


if __name__ == "__main__":
    raise SystemExit(main())
