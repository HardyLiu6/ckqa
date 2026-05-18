#!/usr/bin/env python3
from __future__ import annotations

import argparse
import csv
import json
import os
import re
import sys
from dataclasses import dataclass
from datetime import datetime
from pathlib import Path
from typing import Any, Sequence


DATA_SOURCE_PATTERN = re.compile(r"\s*\[Data:\s*Sources?\s*\([^)]+\)\]\s*", re.IGNORECASE)


@dataclass(frozen=True, slots=True)
class CleanPlanItem:
    table_name: str
    id_column: str
    content_column: str
    row_id: int
    session_id: int | None
    old_content: str
    new_content: str

    def to_report(self) -> dict[str, Any]:
        return {
            "tableName": self.table_name,
            "idColumn": self.id_column,
            "contentColumn": self.content_column,
            "rowId": self.row_id,
            "sessionId": self.session_id,
            "oldPreview": preview(self.old_content),
            "newPreview": preview(self.new_content),
        }


def clean_answer_text(value: str) -> str:
    cleaned = DATA_SOURCE_PATTERN.sub(" [已参考课程知识库]", value)
    cleaned = re.sub(r"(?:\s*\[已参考课程知识库\]){2,}", " [已参考课程知识库]", cleaned)
    cleaned = re.sub(r"\s+([，。！？；：、,.!?;:])", r"\1", cleaned)
    return cleaned.strip()


def build_clean_plan(rows: Sequence[dict[str, Any]]) -> list[CleanPlanItem]:
    plan: list[CleanPlanItem] = []
    for row in rows:
        old_content = str(row.get("content") or "")
        new_content = clean_answer_text(old_content)
        if new_content == old_content:
            continue
        plan.append(
            CleanPlanItem(
                table_name=str(row["table_name"]),
                id_column=str(row["id_column"]),
                content_column=str(row["content_column"]),
                row_id=int(row["row_id"]),
                session_id=optional_int(row.get("session_id")),
                old_content=old_content,
                new_content=new_content,
            )
        )
    return plan


def fetch_rows(connection, args: argparse.Namespace) -> list[dict[str, Any]]:
    rows = fetch_message_rows(connection, args)
    if column_exists(connection, "qa_retrieval_logs", "assistant_message_text"):
        rows.extend(fetch_retrieval_log_rows(connection, args))
    return rows


def fetch_message_rows(connection, args: argparse.Namespace) -> list[dict[str, Any]]:
    sql = """
SELECT
  'qa_messages' AS table_name,
  'id' AS id_column,
  'content' AS content_column,
  id AS row_id,
  session_id,
  content
FROM qa_messages
WHERE role = 'assistant'
  AND content LIKE %s
"""
    params: list[Any] = ["%[Data: Sources%"]
    if args.session_id is not None:
        sql += "  AND session_id = %s\n"
        params.append(args.session_id)
    if args.since:
        sql += "  AND created_at >= %s\n"
        params.append(args.since)
    sql += "ORDER BY id ASC\n"
    if args.limit:
        sql += "LIMIT %s\n"
        params.append(args.limit)
    with connection.cursor() as cursor:
        cursor.execute(sql, tuple(params))
        return list(cursor.fetchall())


def fetch_retrieval_log_rows(connection, args: argparse.Namespace) -> list[dict[str, Any]]:
    sql = """
SELECT
  'qa_retrieval_logs' AS table_name,
  'id' AS id_column,
  'assistant_message_text' AS content_column,
  id AS row_id,
  session_id,
  assistant_message_text AS content
FROM qa_retrieval_logs
WHERE assistant_message_text LIKE %s
"""
    params: list[Any] = ["%[Data: Sources%"]
    if args.session_id is not None:
        sql += "  AND session_id = %s\n"
        params.append(args.session_id)
    if args.since:
        sql += "  AND finished_at >= %s\n"
        params.append(args.since)
    sql += "ORDER BY id ASC\n"
    if args.limit:
        sql += "LIMIT %s\n"
        params.append(args.limit)
    with connection.cursor() as cursor:
        cursor.execute(sql, tuple(params))
        return list(cursor.fetchall())


def apply_updates(connection, plan: Sequence[CleanPlanItem]) -> int:
    updated = 0
    with connection.cursor() as cursor:
        for item in plan:
            sql = (
                f"UPDATE `{item.table_name}` "
                f"SET `{item.content_column}` = %s "
                f"WHERE `{item.id_column}` = %s AND `{item.content_column}` = %s"
            )
            updated += int(cursor.execute(sql, (item.new_content, item.row_id, item.old_content)))
    connection.commit()
    return updated


def column_exists(connection, table_name: str, column_name: str) -> bool:
    with connection.cursor() as cursor:
        cursor.execute(
            """
SELECT COUNT(*) AS count
FROM INFORMATION_SCHEMA.COLUMNS
WHERE TABLE_SCHEMA = DATABASE()
  AND TABLE_NAME = %s
  AND COLUMN_NAME = %s
""",
            (table_name, column_name),
        )
        row = cursor.fetchone() or {}
        return int(row.get("count") or 0) > 0


def connect_mysql(args: argparse.Namespace):
    try:
        import pymysql
        from pymysql.cursors import DictCursor
    except ImportError as exc:
        raise SystemExit("缺少 pymysql，请在当前 Python 环境安装后再执行。") from exc

    return pymysql.connect(
        host=args.mysql_host,
        port=args.mysql_port,
        user=args.mysql_user,
        password=args.mysql_password,
        database=args.mysql_database,
        charset="utf8mb4",
        cursorclass=DictCursor,
        autocommit=False,
    )


def parse_args(argv: Sequence[str] | None = None) -> argparse.Namespace:
    parser = argparse.ArgumentParser(description="清理历史 QA 回答中的 [Data: Sources (...)] 旧格式标记。")
    parser.add_argument("--execute", action="store_true", help="真正写回数据库；默认只 dry-run。")
    parser.add_argument("--session-id", type=int, default=None, help="只处理指定 QA 会话。")
    parser.add_argument("--since", default=None, help="只处理该时间之后的数据，例如 2026-05-18 00:00:00。")
    parser.add_argument("--limit", type=int, default=100, help="最多扫描多少条候选记录；0 表示不限制。")
    parser.add_argument("--env-file", type=Path, action="append", default=[], help="额外加载 .env 文件，可重复。")
    parser.add_argument("--json-report", type=Path, default=None, help="写入 dry-run/执行报告 JSON。")
    parser.add_argument("--csv-report", type=Path, default=None, help="写入 dry-run/执行报告 CSV。")
    parser.add_argument("--mysql-host", default=os.getenv("MYSQL_HOST", "localhost"))
    parser.add_argument("--mysql-port", type=int, default=int(os.getenv("MYSQL_PORT", "23306")))
    parser.add_argument("--mysql-user", default=os.getenv("MYSQL_USER", "root"))
    parser.add_argument("--mysql-password", default=os.getenv("MYSQL_PASSWORD") or os.getenv("MYSQL_ROOT_PASSWORD", ""))
    parser.add_argument("--mysql-database", default=os.getenv("MYSQL_DATABASE", "ocqa"))
    args = parser.parse_args(argv)
    args.limit = None if args.limit == 0 else args.limit
    return args


def load_env_files(paths: Sequence[Path]) -> None:
    for path in paths:
        if not path.exists():
            continue
        for line in path.read_text(encoding="utf-8").splitlines():
            stripped = line.strip()
            if not stripped or stripped.startswith("#") or "=" not in stripped:
                continue
            key, value = stripped.split("=", 1)
            os.environ.setdefault(key.strip(), value.strip().strip("'\""))


def write_reports(plan: Sequence[CleanPlanItem], args: argparse.Namespace, updated: int) -> None:
    report = {
        "execute": args.execute,
        "matched": len(plan),
        "updated": updated,
        "generatedAt": datetime.now().isoformat(timespec="seconds"),
        "items": [item.to_report() for item in plan],
    }
    print(json.dumps(report, ensure_ascii=False, indent=2))
    if args.json_report:
        args.json_report.parent.mkdir(parents=True, exist_ok=True)
        args.json_report.write_text(json.dumps(report, ensure_ascii=False, indent=2), encoding="utf-8")
    if args.csv_report:
        args.csv_report.parent.mkdir(parents=True, exist_ok=True)
        with args.csv_report.open("w", encoding="utf-8", newline="") as handle:
            writer = csv.DictWriter(handle, fieldnames=["tableName", "contentColumn", "rowId", "sessionId", "oldPreview", "newPreview"])
            writer.writeheader()
            for item in plan:
                writer.writerow(item.to_report())


def preview(value: str, max_length: int = 120) -> str:
    text = value.replace("\n", " ").strip()
    return text if len(text) <= max_length else text[:max_length] + "..."


def optional_int(value: Any) -> int | None:
    if value is None:
        return None
    return int(value)


def main(argv: Sequence[str] | None = None) -> int:
    env_paths = parse_env_files(argv)
    load_env_files(env_paths)
    args = parse_args(argv)
    load_env_files(args.env_file)
    with connect_mysql(args) as connection:
        plan = build_clean_plan(fetch_rows(connection, args))
        updated = apply_updates(connection, plan) if args.execute else 0
    write_reports(plan, args, updated)
    return 0


def parse_env_files(argv: Sequence[str] | None = None) -> list[Path]:
    parser = argparse.ArgumentParser(add_help=False)
    parser.add_argument("--env-file", type=Path, action="append", default=[])
    args, _ = parser.parse_known_args(argv)
    return list(args.env_file)


if __name__ == "__main__":
    raise SystemExit(main(sys.argv[1:]))
