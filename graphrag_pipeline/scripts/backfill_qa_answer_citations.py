#!/usr/bin/env python3
from __future__ import annotations

import argparse
import json
import os
import sys
from dataclasses import dataclass
from datetime import datetime
from pathlib import Path
from typing import Any, Iterable, Mapping, Sequence


PROJECT_ROOT = Path(__file__).resolve().parents[1]
REPO_ROOT = PROJECT_ROOT.parent
UTILS_DIR = PROJECT_ROOT / "utils"
if str(UTILS_DIR) not in sys.path:
    sys.path.insert(0, str(UTILS_DIR))

from query_citation_resolver import resolve_answer_citations

try:
    from dotenv import load_dotenv
except ImportError:  # pragma: no cover - 运行环境可选依赖
    load_dotenv = None


SELECT_CANDIDATES_SQL = """
SELECT
  m.id AS message_id,
  m.session_id AS session_id,
  m.sequence_no AS sequence_no,
  m.content AS content,
  COALESCE(l.index_run_id, s.index_run_id) AS index_run_id,
  a.storage_uri AS storage_uri
FROM qa_messages m
JOIN qa_retrieval_logs l ON l.assistant_message_id = m.id
JOIN qa_sessions s ON s.id = m.session_id
LEFT JOIN index_artifacts a
  ON a.index_run_id = COALESCE(l.index_run_id, s.index_run_id)
  AND a.artifact_type = 'output_dir'
  AND a.artifact_status = 'ready'
WHERE m.role = 'assistant'
  AND m.content LIKE %s
"""

UPDATE_MESSAGE_SQL = """
UPDATE qa_messages
SET content = %s,
    content_text = %s
WHERE id = %s AND content = %s
"""


@dataclass(frozen=True, slots=True)
class BackfillPlanItem:
    message_id: int
    session_id: int
    sequence_no: int
    index_run_id: int | None
    storage_uri: str | None
    output_dir: Path | None
    old_content: str
    new_content: str
    source_count: int
    warning: str | None = None

    def to_summary(self) -> dict[str, Any]:
        return {
            "messageId": self.message_id,
            "sessionId": self.session_id,
            "sequenceNo": self.sequence_no,
            "indexRunId": self.index_run_id,
            "storageUri": self.storage_uri,
            "outputDir": str(self.output_dir) if self.output_dir is not None else None,
            "sourceCount": self.source_count,
            "warning": self.warning,
            "oldPreview": _preview(self.old_content),
            "newPreview": _preview(self.new_content),
        }

    def to_backup_record(self) -> dict[str, Any]:
        record = self.to_summary()
        record["oldContent"] = self.old_content
        record["newContent"] = self.new_content
        return record


def build_backfill_plan(
    rows: Iterable[Mapping[str, Any]],
    *,
    output_root: Path,
    build_runs_root: Path | None,
) -> list[BackfillPlanItem]:
    plan: list[BackfillPlanItem] = []
    seen_message_ids: set[int] = set()
    for row in rows:
        message_id = int(row["message_id"])
        if message_id in seen_message_ids:
            continue
        seen_message_ids.add(message_id)
        old_content = str(row.get("content") or "")
        output_dir = resolve_output_dir(row.get("storage_uri"), output_root=output_root, build_runs_root=build_runs_root)
        resolved = resolve_answer_citations(old_content, output_dir)
        if resolved.display_text == old_content:
            continue
        plan.append(
            BackfillPlanItem(
                message_id=message_id,
                session_id=int(row["session_id"]),
                sequence_no=int(row["sequence_no"]),
                index_run_id=_optional_int(row.get("index_run_id")),
                storage_uri=_optional_str(row.get("storage_uri")),
                output_dir=output_dir,
                old_content=old_content,
                new_content=resolved.display_text,
                source_count=len(resolved.sources),
                warning=None if output_dir is not None else "missing_output_dir",
            )
        )
    return plan


def resolve_output_dir(
    storage_uri: Any,
    *,
    output_root: Path,
    build_runs_root: Path | None,
) -> Path | None:
    if storage_uri is None or not str(storage_uri).strip():
        return None

    raw_path = Path(str(storage_uri).strip()).expanduser()
    if raw_path.is_absolute():
        return raw_path.resolve() if raw_path.exists() else None

    candidates: list[Path] = []
    if build_runs_root is not None:
        candidates.append(build_runs_root / raw_path)
    candidates.append(output_root / raw_path)
    candidates.append(PROJECT_ROOT / "output" / raw_path)
    candidates.append(PROJECT_ROOT / raw_path)
    candidates.append(REPO_ROOT / raw_path)

    for candidate in candidates:
        resolved = candidate.resolve()
        if resolved.exists():
            return resolved
    return None


def apply_backfill_updates(connection, plan: Sequence[BackfillPlanItem]) -> int:
    updated = 0
    with connection.cursor() as cursor:
        for item in plan:
            affected = cursor.execute(
                UPDATE_MESSAGE_SQL,
                (item.new_content, item.new_content, item.message_id, item.old_content),
            )
            updated += int(affected)
    connection.commit()
    return updated


def fetch_candidate_rows(connection, *, limit: int | None, message_ids: Sequence[int], session_id: int | None) -> list[dict[str, Any]]:
    sql = SELECT_CANDIDATES_SQL
    params: list[Any] = ["%[Data:%"]
    if message_ids:
        placeholders = ", ".join(["%s"] * len(message_ids))
        sql += f"  AND m.id IN ({placeholders})\n"
        params.extend(message_ids)
    if session_id is not None:
        sql += "  AND m.session_id = %s\n"
        params.append(session_id)
    sql += "ORDER BY m.id ASC\n"
    if limit is not None:
        sql += "LIMIT %s\n"
        params.append(limit)

    with connection.cursor() as cursor:
        cursor.execute(sql, tuple(params))
        return list(cursor.fetchall())


def write_backup(path: Path, plan: Sequence[BackfillPlanItem]) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    with path.open("w", encoding="utf-8") as handle:
        for item in plan:
            handle.write(json.dumps(item.to_backup_record(), ensure_ascii=False, default=str))
            handle.write("\n")


def connect_mysql(args: argparse.Namespace):
    try:
        import pymysql
        from pymysql.cursors import DictCursor
    except ImportError as exc:  # pragma: no cover - 依赖缺失时只在实际命令触发
        raise SystemExit("缺少 pymysql，请在当前环境安装后再执行 backfill。") from exc

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
    parser = argparse.ArgumentParser(
        description="Dry-run 或执行历史 QA 回答中的 GraphRAG Data Sources 引用清理。",
    )
    parser.add_argument("--execute", action="store_true", help="真正更新 qa_messages；默认只 dry-run。")
    parser.add_argument("--limit", type=int, default=50, help="最多扫描多少条候选消息；传 0 表示不限制。")
    parser.add_argument("--message-id", type=int, action="append", default=[], help="只处理指定消息，可重复。")
    parser.add_argument("--session-id", type=int, default=None, help="只处理指定 QA 会话。")
    parser.add_argument("--backup-file", type=Path, default=None, help="执行更新前写入 JSONL 备份。")
    parser.add_argument("--env-file", type=Path, action="append", default=[], help="额外加载环境变量文件。")
    parser.add_argument("--mysql-host", default=os.getenv("MYSQL_HOST", "localhost"))
    parser.add_argument("--mysql-port", type=int, default=int(os.getenv("MYSQL_PORT", "23306")))
    parser.add_argument("--mysql-user", default=os.getenv("MYSQL_USER", "root"))
    parser.add_argument("--mysql-password", default=os.getenv("MYSQL_PASSWORD") or os.getenv("MYSQL_ROOT_PASSWORD", ""))
    parser.add_argument("--mysql-database", default=os.getenv("MYSQL_DATABASE", "ocqa"))
    parser.add_argument(
        "--output-root",
        type=Path,
        default=Path(os.getenv("GRAPHRAG_OUTPUT_DIR") or os.getenv("GRAPHRAG_STORAGE_DIR") or PROJECT_ROOT / "output"),
        help="GraphRAG 普通 output 根目录。",
    )
    parser.add_argument(
        "--build-runs-root",
        type=Path,
        default=Path(os.getenv("GRAPHRAG_BUILD_RUNS_ROOT", PROJECT_ROOT / "runtime" / "kb-build-runs")),
        help="Java 管理的知识库构建运行根目录。",
    )
    return parser.parse_args(argv)


def parse_env_file_args(argv: Sequence[str] | None = None) -> list[Path]:
    parser = argparse.ArgumentParser(add_help=False)
    parser.add_argument("--env-file", type=Path, action="append", default=[])
    args, _ = parser.parse_known_args(argv)
    return list(args.env_file)


def main(argv: Sequence[str] | None = None) -> int:
    _load_default_env_files()
    _load_env_files(parse_env_file_args(argv))
    args = parse_args(argv)

    limit = None if args.limit == 0 else args.limit
    backup_file = args.backup_file or _default_backup_file()
    with connect_mysql(args) as connection:
        rows = fetch_candidate_rows(
            connection,
            limit=limit,
            message_ids=args.message_id,
            session_id=args.session_id,
        )
        plan = build_backfill_plan(
            rows,
            output_root=args.output_root.expanduser().resolve(),
            build_runs_root=args.build_runs_root.expanduser().resolve() if args.build_runs_root else None,
        )
        _print_plan(plan, execute=args.execute)
        if not args.execute:
            print("DRY-RUN: 未更新数据库。确认结果后加 --execute 才会写入 qa_messages。")
            return 0
        if not plan:
            print("EXECUTE: 没有需要更新的历史回答。")
            return 0
        write_backup(backup_file, plan)
        updated = apply_backfill_updates(connection, plan)
        print(f"EXECUTE: 已更新 {updated}/{len(plan)} 条 qa_messages，备份写入 {backup_file}")
    return 0


def _load_default_env_files() -> None:
    if load_dotenv is None:
        return
    for env_file in (PROJECT_ROOT / ".env", REPO_ROOT / "infra" / ".env"):
        if env_file.exists():
            load_dotenv(env_file, override=False)


def _load_env_files(env_files: Sequence[Path]) -> None:
    if load_dotenv is None:
        return
    for env_file in env_files:
        load_dotenv(env_file, override=True)


def _default_backup_file() -> Path:
    stamp = datetime.now().strftime("%Y%m%d_%H%M%S")
    return Path("/tmp") / f"ckqa_qa_message_citation_backfill_{stamp}.jsonl"


def _print_plan(plan: Sequence[BackfillPlanItem], *, execute: bool) -> None:
    mode = "EXECUTE" if execute else "DRY-RUN"
    print(f"{mode}: 计划更新 {len(plan)} 条历史回答。")
    for item in plan:
        print(json.dumps(item.to_summary(), ensure_ascii=False, default=str))


def _optional_int(value: Any) -> int | None:
    if value is None or value == "":
        return None
    return int(value)


def _optional_str(value: Any) -> str | None:
    if value is None:
        return None
    text = str(value).strip()
    return text or None


def _preview(text: str, max_chars: int = 160) -> str:
    normalized = " ".join(text.split())
    if len(normalized) <= max_chars:
        return normalized
    return normalized[: max_chars - 1].rstrip() + "…"


if __name__ == "__main__":
    raise SystemExit(main())
