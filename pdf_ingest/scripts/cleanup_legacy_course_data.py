#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
清理旧版课程 ID 关联的 MySQL 与 MinIO 数据。

默认策略：

1. 从 `courses` 表读取全部课程。
2. 将不符合 `crs-YYYYMMDD-HHMMSS` 的课程 ID 视为旧版课程 ID。
3. 删除这些课程下的课程资料、解析结果、知识库、索引、问答会话与授权日志。
4. 删除 MinIO 中对应课程前缀，以及不再被其它课程引用的 raw PDF 对象。

脚本默认 dry-run，必须显式传入 `--execute` 才会修改外部系统。
"""

from __future__ import annotations

import argparse
import json
import os
import re
import sys
from dataclasses import dataclass
from pathlib import Path
from typing import Any, Iterable, Sequence

import pymysql
from dotenv import load_dotenv
from minio import Minio
from minio.error import S3Error
from pymysql.cursors import DictCursor


DEFAULT_NEW_COURSE_ID_REGEX = r"^crs-\d{8}-\d{6}$"


@dataclass(frozen=True)
class MaterialObjectRef:
    material_object_id: int
    bucket: str
    object_key: str


@dataclass(frozen=True)
class MinioPrefixTarget:
    bucket: str
    prefix: str


@dataclass(frozen=True)
class MinioObjectTarget:
    bucket: str
    object_key: str


@dataclass(frozen=True)
class MinioDeletePlan:
    prefixes: list[MinioPrefixTarget]
    objects: list[MinioObjectTarget]


@dataclass(frozen=True)
class CleanupPlan:
    course_ids: list[str]
    existing_course_ids: list[str]
    course_material_ids: list[int]
    parse_result_ids: list[int]
    parse_log_ids: list[int]
    material_object_ids: list[int]
    orphan_material_objects: list[MaterialObjectRef]
    course_membership_ids: list[int]
    knowledge_base_ids: list[int]
    index_run_ids: list[int]
    qa_session_ids: list[int]
    qa_retrieval_log_ids: list[int]
    authorization_audit_log_ids: list[int]


def unique_in_order(values: Iterable[Any]) -> list[Any]:
    seen: set[Any] = set()
    result: list[Any] = []
    for value in values:
        if value in seen:
            continue
        seen.add(value)
        result.append(value)
    return result


def choose_target_course_ids(
    all_course_ids: Sequence[str],
    explicit_course_ids: Sequence[str],
    *,
    new_course_id_regex: str = DEFAULT_NEW_COURSE_ID_REGEX,
) -> list[str]:
    if explicit_course_ids:
        return unique_in_order(explicit_course_ids)

    pattern = re.compile(new_course_id_regex)
    return [course_id for course_id in all_course_ids if not pattern.fullmatch(course_id)]


def build_minio_delete_plan(
    *,
    course_ids: Sequence[str],
    pdf_bucket: str,
    artifacts_bucket: str,
    orphan_material_objects: Sequence[MaterialObjectRef],
) -> MinioDeletePlan:
    prefixes: list[MinioPrefixTarget] = []
    for course_id in course_ids:
        prefixes.append(MinioPrefixTarget(artifacts_bucket, f"{course_id}/"))
        prefixes.append(MinioPrefixTarget(pdf_bucket, f"{course_id}/"))

    objects = [
        MinioObjectTarget(ref.bucket, ref.object_key)
        for ref in orphan_material_objects
        if ref.bucket and ref.object_key
    ]
    return MinioDeletePlan(prefixes=prefixes, objects=objects)


def load_runtime_env(env_file: Path) -> None:
    if env_file.exists():
        load_dotenv(env_file)


def connect_mysql() -> pymysql.connections.Connection:
    return pymysql.connect(
        host=os.getenv("MYSQL_HOST", "localhost"),
        port=int(os.getenv("MYSQL_PORT", "3306")),
        user=os.getenv("MYSQL_USER", "root"),
        password=os.getenv("MYSQL_PASSWORD", ""),
        database=os.getenv("MYSQL_DATABASE", "mineru_parser"),
        charset="utf8mb4",
        cursorclass=DictCursor,
        autocommit=False,
    )


def connect_minio() -> Minio:
    secure = os.getenv("MINIO_SECURE", "false").lower() in {"1", "true", "yes", "on"}
    return Minio(
        endpoint=os.getenv("MINIO_ENDPOINT", "localhost:9000"),
        access_key=os.getenv("MINIO_ACCESS_KEY", "admin"),
        secret_key=os.getenv("MINIO_SECRET_KEY", "12345678"),
        secure=secure,
    )


def pdf_bucket() -> str:
    return os.getenv("MINIO_BUCKET_PDF", "course-pdfs")


def artifacts_bucket() -> str:
    return os.getenv("MINIO_BUCKET_ARTIFACTS", "course-artifacts")


def in_clause(values: Sequence[Any]) -> str:
    return ", ".join(["%s"] * len(values))


def fetch_column(
    cursor: DictCursor,
    sql: str,
    params: Sequence[Any] = (),
    *,
    column: str = "id",
) -> list[Any]:
    cursor.execute(sql, tuple(params))
    return [row[column] for row in cursor.fetchall()]


def fetch_ids_by_in(
    cursor: DictCursor,
    *,
    table: str,
    column: str,
    values: Sequence[Any],
    id_column: str = "id",
) -> list[int]:
    if not values:
        return []
    cursor.execute(
        f"SELECT `{id_column}` FROM `{table}` WHERE `{column}` IN ({in_clause(values)})",
        tuple(values),
    )
    return [int(row[id_column]) for row in cursor.fetchall()]


def fetch_ids_by_or(
    cursor: DictCursor,
    *,
    table: str,
    clauses: Sequence[tuple[str, Sequence[Any]]],
    id_column: str = "id",
) -> list[int]:
    sql_clauses: list[str] = []
    params: list[Any] = []
    for column, values in clauses:
        if not values:
            continue
        sql_clauses.append(f"`{column}` IN ({in_clause(values)})")
        params.extend(values)

    if not sql_clauses:
        return []

    cursor.execute(
        f"SELECT `{id_column}` FROM `{table}` WHERE " + " OR ".join(sql_clauses),
        tuple(params),
    )
    return [int(row[id_column]) for row in cursor.fetchall()]


def delete_by_in(
    cursor: DictCursor,
    *,
    table: str,
    column: str,
    values: Sequence[Any],
) -> int:
    if not values:
        return 0
    cursor.execute(
        f"DELETE FROM `{table}` WHERE `{column}` IN ({in_clause(values)})",
        tuple(values),
    )
    return int(cursor.rowcount)


def update_active_index_references(
    cursor: DictCursor,
    *,
    knowledge_base_ids: Sequence[int],
    index_run_ids: Sequence[int],
) -> int:
    clauses: list[str] = []
    params: list[Any] = []
    if knowledge_base_ids:
        clauses.append(f"`id` IN ({in_clause(knowledge_base_ids)})")
        params.extend(knowledge_base_ids)
    if index_run_ids:
        clauses.append(f"`active_index_run_id` IN ({in_clause(index_run_ids)})")
        params.extend(index_run_ids)
    if not clauses:
        return 0

    cursor.execute(
        "UPDATE `knowledge_bases` SET `active_index_run_id` = NULL WHERE "
        + " OR ".join(clauses),
        tuple(params),
    )
    return int(cursor.rowcount)


def fetch_orphan_material_objects(
    cursor: DictCursor,
    *,
    course_ids: Sequence[str],
) -> list[MaterialObjectRef]:
    if not course_ids:
        return []

    placeholders = in_clause(course_ids)
    params = tuple(course_ids) + tuple(course_ids)
    cursor.execute(
        f"""
        SELECT DISTINCT
            mo.`id` AS material_object_id,
            mo.`minio_bucket` AS bucket,
            mo.`minio_object_key` AS object_key
        FROM `course_materials` cm
        JOIN `material_objects` mo ON mo.`id` = cm.`material_object_id`
        WHERE cm.`course_id` IN ({placeholders})
          AND NOT EXISTS (
              SELECT 1
              FROM `course_materials` keep_cm
              WHERE keep_cm.`material_object_id` = cm.`material_object_id`
                AND keep_cm.`course_id` NOT IN ({placeholders})
          )
        ORDER BY mo.`id`
        """,
        params,
    )
    return [
        MaterialObjectRef(
            material_object_id=int(row["material_object_id"]),
            bucket=row["bucket"],
            object_key=row["object_key"],
        )
        for row in cursor.fetchall()
    ]


def build_cleanup_plan(cursor: DictCursor, course_ids: Sequence[str]) -> CleanupPlan:
    existing_course_ids = (
        fetch_column(
            cursor,
            f"SELECT `course_id` FROM `courses` WHERE `course_id` IN ({in_clause(course_ids)}) ORDER BY `course_id`",
            tuple(course_ids),
            column="course_id",
        )
        if course_ids
        else []
    )
    course_material_ids = fetch_ids_by_in(
        cursor,
        table="course_materials",
        column="course_id",
        values=course_ids,
    )
    parse_result_ids = fetch_ids_by_or(
        cursor,
        table="parse_results",
        clauses=[
            ("course_id", course_ids),
            ("course_material_id", course_material_ids),
        ],
    )
    parse_log_ids = fetch_ids_by_in(
        cursor,
        table="parse_logs",
        column="course_material_id",
        values=course_material_ids,
    )
    orphan_material_objects = fetch_orphan_material_objects(cursor, course_ids=course_ids)
    course_membership_ids = fetch_ids_by_in(
        cursor,
        table="course_memberships",
        column="course_id",
        values=course_ids,
    )
    knowledge_base_ids = fetch_ids_by_in(
        cursor,
        table="knowledge_bases",
        column="course_id",
        values=course_ids,
    )
    index_run_ids = fetch_ids_by_in(
        cursor,
        table="index_runs",
        column="knowledge_base_id",
        values=knowledge_base_ids,
    )
    qa_session_ids = fetch_ids_by_or(
        cursor,
        table="qa_sessions",
        clauses=[
            ("course_id", course_ids),
            ("course_membership_id", course_membership_ids),
            ("knowledge_base_id", knowledge_base_ids),
        ],
    )
    qa_retrieval_log_ids = fetch_ids_by_or(
        cursor,
        table="qa_retrieval_logs",
        clauses=[
            ("course_id", course_ids),
            ("session_id", qa_session_ids),
            ("index_run_id", index_run_ids),
        ],
    )
    authorization_audit_log_ids = fetch_ids_by_or(
        cursor,
        table="authorization_audit_logs",
        clauses=[
            ("target_course_id", course_ids),
            ("target_session_id", qa_session_ids),
            ("course_membership_id", course_membership_ids),
        ],
    )

    return CleanupPlan(
        course_ids=list(course_ids),
        existing_course_ids=existing_course_ids,
        course_material_ids=course_material_ids,
        parse_result_ids=parse_result_ids,
        parse_log_ids=parse_log_ids,
        material_object_ids=[ref.material_object_id for ref in orphan_material_objects],
        orphan_material_objects=orphan_material_objects,
        course_membership_ids=course_membership_ids,
        knowledge_base_ids=knowledge_base_ids,
        index_run_ids=index_run_ids,
        qa_session_ids=qa_session_ids,
        qa_retrieval_log_ids=qa_retrieval_log_ids,
        authorization_audit_log_ids=authorization_audit_log_ids,
    )


def summarize_cleanup_plan(plan: CleanupPlan) -> dict[str, Any]:
    return {
        "course_ids": plan.course_ids,
        "database": {
            "courses": len(plan.existing_course_ids),
            "existing_courses": len(plan.existing_course_ids),
            "course_materials": len(plan.course_material_ids),
            "parse_results": len(plan.parse_result_ids),
            "parse_logs": len(plan.parse_log_ids),
            "material_objects_orphaned": len(plan.material_object_ids),
            "course_memberships": len(plan.course_membership_ids),
            "knowledge_bases": len(plan.knowledge_base_ids),
            "index_runs": len(plan.index_run_ids),
            "qa_sessions": len(plan.qa_session_ids),
            "qa_retrieval_logs": len(plan.qa_retrieval_log_ids),
            "authorization_audit_logs": len(plan.authorization_audit_log_ids),
        },
        "orphan_material_objects": [
            {
                "id": ref.material_object_id,
                "bucket": ref.bucket,
                "object_key": ref.object_key,
            }
            for ref in plan.orphan_material_objects
        ],
    }


def execute_database_cleanup(cursor: DictCursor, plan: CleanupPlan) -> dict[str, int]:
    counts: dict[str, int] = {}

    counts["knowledge_bases_active_index_run_cleared"] = update_active_index_references(
        cursor,
        knowledge_base_ids=plan.knowledge_base_ids,
        index_run_ids=plan.index_run_ids,
    )
    counts["authorization_audit_logs"] = delete_by_in(
        cursor,
        table="authorization_audit_logs",
        column="id",
        values=plan.authorization_audit_log_ids,
    )
    counts["qa_retrieval_hits"] = delete_by_in(
        cursor,
        table="qa_retrieval_hits",
        column="retrieval_log_id",
        values=plan.qa_retrieval_log_ids,
    )
    counts["qa_retrieval_logs"] = delete_by_in(
        cursor,
        table="qa_retrieval_logs",
        column="id",
        values=plan.qa_retrieval_log_ids,
    )
    counts["qa_sessions"] = delete_by_in(
        cursor,
        table="qa_sessions",
        column="id",
        values=plan.qa_session_ids,
    )
    counts["index_artifacts"] = delete_by_in(
        cursor,
        table="index_artifacts",
        column="index_run_id",
        values=plan.index_run_ids,
    )
    counts["index_runs"] = delete_by_in(
        cursor,
        table="index_runs",
        column="id",
        values=plan.index_run_ids,
    )
    counts["kb_documents"] = delete_by_in(
        cursor,
        table="kb_documents",
        column="knowledge_base_id",
        values=plan.knowledge_base_ids,
    )
    counts["knowledge_bases"] = delete_by_in(
        cursor,
        table="knowledge_bases",
        column="id",
        values=plan.knowledge_base_ids,
    )
    counts["course_membership_events"] = delete_by_in(
        cursor,
        table="course_membership_events",
        column="course_membership_id",
        values=plan.course_membership_ids,
    )
    counts["course_memberships"] = delete_by_in(
        cursor,
        table="course_memberships",
        column="id",
        values=plan.course_membership_ids,
    )
    counts["parse_results"] = delete_by_in(
        cursor,
        table="parse_results",
        column="id",
        values=plan.parse_result_ids,
    )
    counts["parse_logs"] = delete_by_in(
        cursor,
        table="parse_logs",
        column="id",
        values=plan.parse_log_ids,
    )
    counts["course_materials"] = delete_by_in(
        cursor,
        table="course_materials",
        column="id",
        values=plan.course_material_ids,
    )
    counts["material_objects"] = delete_by_in(
        cursor,
        table="material_objects",
        column="id",
        values=plan.material_object_ids,
    )
    counts["courses"] = delete_by_in(
        cursor,
        table="courses",
        column="course_id",
        values=plan.course_ids,
    )

    return counts


def list_objects(client: Minio, bucket: str, prefix: str) -> list[str]:
    try:
        return [
            item.object_name
            for item in client.list_objects(bucket, prefix=prefix, recursive=True)
        ]
    except S3Error as exc:
        if exc.code == "NoSuchBucket":
            return []
        raise


def object_exists(client: Minio, bucket: str, object_key: str) -> bool:
    try:
        client.stat_object(bucket, object_key)
        return True
    except S3Error as exc:
        if exc.code in {"NoSuchKey", "NoSuchBucket"}:
            return False
        raise


def summarize_minio_plan(client: Minio, plan: MinioDeletePlan) -> dict[str, Any]:
    prefix_items = []
    for target in plan.prefixes:
        objects = list_objects(client, target.bucket, target.prefix)
        prefix_items.append(
            {
                "bucket": target.bucket,
                "prefix": target.prefix,
                "object_count": len(objects),
            }
        )

    object_items = []
    for target in plan.objects:
        object_items.append(
            {
                "bucket": target.bucket,
                "object_key": target.object_key,
                "exists": object_exists(client, target.bucket, target.object_key),
            }
        )

    return {"prefixes": prefix_items, "objects": object_items}


def execute_minio_cleanup(client: Minio, plan: MinioDeletePlan) -> dict[str, int]:
    deleted_prefix_objects = 0
    deleted_exact_objects = 0
    deleted_keys: set[tuple[str, str]] = set()

    for target in plan.prefixes:
        for object_key in list_objects(client, target.bucket, target.prefix):
            key = (target.bucket, object_key)
            if key in deleted_keys:
                continue
            client.remove_object(target.bucket, object_key)
            deleted_keys.add(key)
            deleted_prefix_objects += 1

    for target in plan.objects:
        key = (target.bucket, target.object_key)
        if key in deleted_keys:
            continue
        if not object_exists(client, target.bucket, target.object_key):
            continue
        client.remove_object(target.bucket, target.object_key)
        deleted_keys.add(key)
        deleted_exact_objects += 1

    return {
        "prefix_objects": deleted_prefix_objects,
        "exact_objects": deleted_exact_objects,
        "total_objects": deleted_prefix_objects + deleted_exact_objects,
    }


def verification_counts(cursor: DictCursor, course_ids: Sequence[str]) -> dict[str, int]:
    if not course_ids:
        return {}

    plan = build_cleanup_plan(cursor, course_ids)
    cursor.execute(
        f"SELECT COUNT(*) AS count FROM `courses` WHERE `course_id` IN ({in_clause(course_ids)})",
        tuple(course_ids),
    )
    course_count = int(cursor.fetchone()["count"])

    return {
        "courses": course_count,
        "course_materials": len(plan.course_material_ids),
        "parse_results": len(plan.parse_result_ids),
        "parse_logs": len(plan.parse_log_ids),
        "material_objects_orphaned": len(plan.material_object_ids),
        "course_memberships": len(plan.course_membership_ids),
        "knowledge_bases": len(plan.knowledge_base_ids),
        "index_runs": len(plan.index_run_ids),
        "qa_sessions": len(plan.qa_session_ids),
        "qa_retrieval_logs": len(plan.qa_retrieval_log_ids),
        "authorization_audit_logs": len(plan.authorization_audit_log_ids),
    }


def verify_minio_empty(client: Minio, plan: MinioDeletePlan) -> dict[str, Any]:
    return summarize_minio_plan(client, plan)


def parse_args(argv: Sequence[str]) -> argparse.Namespace:
    repo_root = Path(__file__).resolve().parents[2]
    default_env_file = repo_root / "pdf_ingest" / ".env"

    parser = argparse.ArgumentParser(
        description="清理旧版课程 ID 关联的数据库与 MinIO 数据，默认 dry-run。",
    )
    parser.add_argument(
        "--env-file",
        type=Path,
        default=default_env_file,
        help="加载 MySQL/MinIO 配置的 .env 文件，默认 pdf_ingest/.env。",
    )
    parser.add_argument(
        "--course-id",
        action="append",
        default=[],
        help="显式指定要清理的课程 ID；不传则自动清理非新版格式课程。",
    )
    parser.add_argument(
        "--new-course-id-regex",
        default=DEFAULT_NEW_COURSE_ID_REGEX,
        help="新版课程 ID 正则；不传 --course-id 时，未匹配该正则的课程会被视为旧课程。",
    )
    parser.add_argument(
        "--execute",
        action="store_true",
        help="真正执行删除；默认只输出计划。",
    )
    parser.add_argument(
        "--skip-minio",
        action="store_true",
        help="只清数据库，不清 MinIO。默认会同时清 MinIO。",
    )
    return parser.parse_args(argv)


def main(argv: Sequence[str] | None = None) -> int:
    args = parse_args(argv or sys.argv[1:])
    load_runtime_env(args.env_file)

    result: dict[str, Any] = {
        "mode": "execute" if args.execute else "dry-run",
        "env_file": str(args.env_file),
    }

    with connect_mysql() as conn:
        cursor = conn.cursor()
        all_course_ids = fetch_column(
            cursor,
            "SELECT `course_id` FROM `courses` ORDER BY `course_id`",
            column="course_id",
        )
        target_course_ids = choose_target_course_ids(
            all_course_ids,
            args.course_id,
            new_course_id_regex=args.new_course_id_regex,
        )
        plan = build_cleanup_plan(cursor, target_course_ids)
        minio_plan = build_minio_delete_plan(
            course_ids=target_course_ids,
            pdf_bucket=pdf_bucket(),
            artifacts_bucket=artifacts_bucket(),
            orphan_material_objects=plan.orphan_material_objects,
        )

        result["plan"] = summarize_cleanup_plan(plan)

        client: Minio | None = None
        if not args.skip_minio:
            client = connect_minio()
            result["minio_plan"] = summarize_minio_plan(client, minio_plan)

        if not args.execute:
            conn.rollback()
            print(json.dumps(result, ensure_ascii=False, indent=2))
            return 0

        db_counts = execute_database_cleanup(cursor, plan)
        conn.commit()
        result["database_deleted"] = db_counts

        if client is not None:
            result["minio_deleted"] = execute_minio_cleanup(client, minio_plan)
            result["minio_verify"] = verify_minio_empty(client, minio_plan)

        verify = verification_counts(cursor, target_course_ids)
        result["database_verify"] = verify
        print(json.dumps(result, ensure_ascii=False, indent=2))

        leftovers = sum(verify.values())
        if client is not None:
            for item in result["minio_verify"]["prefixes"]:
                leftovers += int(item["object_count"])
            for item in result["minio_verify"]["objects"]:
                leftovers += 1 if item["exists"] else 0
        return 2 if leftovers else 0


if __name__ == "__main__":
    raise SystemExit(main())
