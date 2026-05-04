#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""补齐旧课程缺失的 active teacher membership。"""

from __future__ import annotations

import argparse
import csv
import os
import sys
from dataclasses import dataclass
from pathlib import Path
from typing import Iterable, Mapping, TextIO


REQUIRED_MAPPING_COLUMNS = {"course_id", "teacher_user_code"}
INITIAL_TEACHER_REASON = "COURSE_CREATION_INITIAL_TEACHER"
REPORT_FIELDS = [
    "course_id",
    "action",
    "teacher_user_code",
    "teacher_user_id",
    "reason",
    "actor_status",
]

INSERT_MEMBERSHIP_SQL = """
INSERT INTO course_memberships (
  user_id,
  course_id,
  membership_role,
  status,
  access_source,
  joined_at,
  effective_from,
  granted_by_user_id,
  change_reason
) VALUES (
  %(user_id)s,
  %(course_id)s,
  'teacher',
  'active',
  'manual',
  NOW(),
  NOW(),
  %(granted_by_user_id)s,
  %(change_reason)s
)
"""


class MigrationError(RuntimeError):
    """迁移前置校验失败。"""


@dataclass(frozen=True)
class CourseRecord:
    course_id: str


@dataclass(frozen=True)
class UserRecord:
    user_id: int
    user_code: str
    status: str
    has_teacher_role: bool = False


@dataclass(frozen=True)
class ActorResolution:
    user_id: int | None
    status: str


@dataclass(frozen=True)
class MigrationReportRow:
    course_id: str
    action: str
    teacher_user_code: str
    teacher_user_id: int | None
    reason: str
    actor_status: str

    @property
    def is_error(self) -> bool:
        return self.action == "error"


def load_mapping(path: Path) -> dict[str, str]:
    with path.open("r", encoding="utf-8-sig", newline="") as handle:
        reader = csv.DictReader(handle)
        columns = set(reader.fieldnames or [])
        missing = REQUIRED_MAPPING_COLUMNS - columns
        if missing:
            missing_text = ", ".join(sorted(missing))
            raise MigrationError(f"CSV 缺少必需列: {missing_text}")

        mapping: dict[str, str] = {}
        for line_number, row in enumerate(reader, start=2):
            course_id = str(row.get("course_id") or "").strip()
            teacher_code = str(row.get("teacher_user_code") or "").strip()
            if not course_id or not teacher_code:
                raise MigrationError(f"CSV 第 {line_number} 行 course_id 与 teacher_user_code 不能为空")
            existing = mapping.get(course_id)
            if existing and existing != teacher_code:
                raise MigrationError(f"课程 {course_id} 出现多个不同 teacher_user_code")
            mapping[course_id] = teacher_code
        return mapping


def resolve_actor(users_by_code: Mapping[str, UserRecord], granted_by_user_code: str | None) -> ActorResolution:
    actor_code = (granted_by_user_code or "").strip()
    if not actor_code:
        return ActorResolution(user_id=None, status="actor_unavailable")

    actor = users_by_code.get(actor_code)
    if actor is None:
        raise MigrationError(f"授权人不存在: {actor_code}")
    if actor.status != "active":
        raise MigrationError(f"授权人不是 active 状态: {actor_code}")
    return ActorResolution(user_id=actor.user_id, status="actor_resolved")


def build_migration_report(
    *,
    courses: Iterable[CourseRecord],
    active_teacher_course_ids: set[str],
    mapping: Mapping[str, str],
    users_by_code: Mapping[str, UserRecord],
    fallback_teacher_user_code: str | None = None,
    granted_by_user_code: str | None = None,
) -> tuple[list[MigrationReportRow], ActorResolution]:
    actor = resolve_actor(users_by_code, granted_by_user_code)
    fallback_code = (fallback_teacher_user_code or "").strip()
    rows: list[MigrationReportRow] = []

    for course in sorted(courses, key=lambda item: item.course_id):
        course_id = course.course_id
        if course_id in active_teacher_course_ids:
            rows.append(MigrationReportRow(course_id, "skip", "", None, "active_teacher_exists", actor.status))
            continue

        teacher_code = str(mapping.get(course_id) or fallback_code).strip()
        if not teacher_code:
            rows.append(MigrationReportRow(course_id, "error", "", None, "mapping_missing", actor.status))
            continue

        teacher = users_by_code.get(teacher_code)
        if teacher is None:
            rows.append(MigrationReportRow(course_id, "error", teacher_code, None, "teacher_not_found", actor.status))
            continue
        if teacher.status != "active":
            rows.append(
                MigrationReportRow(
                    course_id,
                    "error",
                    teacher_code,
                    teacher.user_id,
                    "teacher_not_active",
                    actor.status,
                )
            )
            continue
        if not teacher.has_teacher_role:
            rows.append(
                MigrationReportRow(
                    course_id,
                    "error",
                    teacher_code,
                    teacher.user_id,
                    "teacher_role_missing",
                    actor.status,
                )
            )
            continue

        rows.append(
            MigrationReportRow(
                course_id,
                "insert",
                teacher_code,
                teacher.user_id,
                "planned",
                actor.status,
            )
        )

    return rows, actor


def build_insert_params(row: MigrationReportRow, actor_user_id: int | None) -> dict[str, object]:
    if row.action != "insert" or row.teacher_user_id is None:
        raise MigrationError(f"课程 {row.course_id} 不是可写入的 insert 计划")
    return {
        "user_id": row.teacher_user_id,
        "course_id": row.course_id,
        "granted_by_user_id": actor_user_id,
        "change_reason": INITIAL_TEACHER_REASON,
    }


def apply_memberships(cursor, rows: Iterable[MigrationReportRow], actor_user_id: int | None) -> int:
    inserted = 0
    for row in rows:
        if row.action != "insert":
            continue
        cursor.execute(INSERT_MEMBERSHIP_SQL, build_insert_params(row, actor_user_id))
        inserted += 1
    return inserted


def write_report(rows: Iterable[MigrationReportRow], stream: TextIO) -> None:
    writer = csv.DictWriter(stream, fieldnames=REPORT_FIELDS)
    writer.writeheader()
    for row in rows:
        writer.writerow({
            "course_id": row.course_id,
            "action": row.action,
            "teacher_user_code": row.teacher_user_code,
            "teacher_user_id": "" if row.teacher_user_id is None else row.teacher_user_id,
            "reason": row.reason,
            "actor_status": row.actor_status,
        })


def fetch_courses(connection) -> list[CourseRecord]:
    with connection.cursor() as cursor:
        cursor.execute(
            """
            SELECT course_id
            FROM courses
            WHERE is_deleted = 0
            ORDER BY course_id ASC
            """
        )
        return [CourseRecord(str(row["course_id"])) for row in cursor.fetchall()]


def fetch_active_teacher_course_ids(connection) -> set[str]:
    with connection.cursor() as cursor:
        cursor.execute(
            """
            SELECT DISTINCT course_id
            FROM course_memberships
            WHERE membership_role = 'teacher'
              AND status = 'active'
            """
        )
        return {str(row["course_id"]) for row in cursor.fetchall()}


def fetch_users_by_codes(connection, user_codes: Iterable[str]) -> dict[str, UserRecord]:
    codes = sorted({str(code).strip() for code in user_codes if str(code).strip()})
    if not codes:
        return {}

    placeholders = ", ".join(["%s"] * len(codes))
    sql = f"""
        SELECT
          u.id,
          u.user_code,
          u.status,
          MAX(CASE WHEN r.role_code = 'teacher' THEN 1 ELSE 0 END) AS has_teacher_role
        FROM users u
        LEFT JOIN user_roles ur ON ur.user_id = u.id
        LEFT JOIN roles r ON r.id = ur.role_id
        WHERE u.is_deleted = 0
          AND u.user_code IN ({placeholders})
        GROUP BY u.id, u.user_code, u.status
    """
    with connection.cursor() as cursor:
        cursor.execute(sql, codes)
        return {
            str(row["user_code"]): UserRecord(
                user_id=int(row["id"]),
                user_code=str(row["user_code"]),
                status=str(row["status"]),
                has_teacher_role=bool(row["has_teacher_role"]),
            )
            for row in cursor.fetchall()
        }


def connect_mysql(args):
    import pymysql
    from pymysql.cursors import DictCursor

    return pymysql.connect(
        host=args.db_host,
        port=args.db_port,
        user=args.db_user,
        password=args.db_password,
        database=args.db_name,
        charset="utf8mb4",
        cursorclass=DictCursor,
    )


def parse_args(argv: list[str] | None = None):
    parser = argparse.ArgumentParser(description="补齐缺失 active teacher membership 的旧课程")
    parser.add_argument("--mapping", required=True, type=Path, help="课程到教师 user_code 的 CSV 映射")
    mode = parser.add_mutually_exclusive_group(required=True)
    mode.add_argument("--dry-run", action="store_true", help="只输出迁移报告，不写数据库")
    mode.add_argument("--apply", action="store_true", help="写入缺失的 teacher membership")
    parser.add_argument("--fallback-teacher-user-code", default=None, help="缺少课程映射时显式使用的兜底教师")
    parser.add_argument("--granted-by-user-code", default=None, help="写入 granted_by_user_id 的授权人 user_code")
    parser.add_argument("--db-host", default=os.getenv("DB_HOST", "127.0.0.1"))
    parser.add_argument("--db-port", default=int(os.getenv("DB_PORT", "3306")), type=int)
    parser.add_argument("--db-name", default=os.getenv("DB_NAME", "ocqa"))
    parser.add_argument("--db-user", default=os.getenv("DB_USER", "root"))
    parser.add_argument("--db-password", default=os.getenv("DB_PASSWORD", ""))
    return parser.parse_args(argv)


def main(argv: list[str] | None = None) -> int:
    args = parse_args(argv)
    mapping = load_mapping(args.mapping)

    connection = connect_mysql(args)
    try:
        courses = fetch_courses(connection)
        active_teacher_course_ids = fetch_active_teacher_course_ids(connection)
        user_codes = set(mapping.values())
        if args.fallback_teacher_user_code:
            user_codes.add(args.fallback_teacher_user_code)
        if args.granted_by_user_code:
            user_codes.add(args.granted_by_user_code)
        users_by_code = fetch_users_by_codes(connection, user_codes)

        rows, actor = build_migration_report(
            courses=courses,
            active_teacher_course_ids=active_teacher_course_ids,
            mapping=mapping,
            users_by_code=users_by_code,
            fallback_teacher_user_code=args.fallback_teacher_user_code,
            granted_by_user_code=args.granted_by_user_code,
        )
        write_report(rows, sys.stdout)

        if any(row.is_error for row in rows):
            return 1

        if args.apply:
            with connection.cursor() as cursor:
                apply_memberships(cursor, rows, actor.user_id)
            connection.commit()
        return 0
    except Exception:
        if args.apply:
            connection.rollback()
        raise
    finally:
        connection.close()


if __name__ == "__main__":
    raise SystemExit(main())
