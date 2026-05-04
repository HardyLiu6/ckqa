#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
幂等修复本地课程资料种子数据。

该脚本面向“本地数据库经常重置，但 MinIO 中仍保留课程资料对象和解析产物”的场景：

1. 确认 PDF 原件位于 `course-pdfs/materials/<md5>.pdf`。
2. 确认解析产物位于 `course-artifacts/<course>/material_<id>/...`。
3. 确认 GraphRAG 导出位于 `course-artifacts/<course>/graphrag/material_<id>/...`。
4. 幂等写入 `courses`、`material_objects`、`course_materials`、`parse_results`。

默认参数对应当前本机的操作系统教材种子；其它课程可通过 CLI 参数覆盖。
"""

from __future__ import annotations

import argparse
import hashlib
import os
from dataclasses import dataclass
from pathlib import Path
from typing import Iterable, Sequence

import pymysql
from dotenv import load_dotenv
from minio import Minio
from minio.commonconfig import CopySource
from minio.error import S3Error
from pymysql.cursors import DictCursor


DEFAULT_COURSE_ID = "os"
DEFAULT_COURSE_NAME = "操作系统"
DEFAULT_DESCRIPTION = "操作系统课程本地联调种子数据"
DEFAULT_MATERIAL_ID = 3
DEFAULT_DISPLAY_NAME = "计算机操作系统.pdf"
DEFAULT_FILE_MD5 = "7b1fbd89d16197bd4708b970c8fc8ff6"

GRAPHRAG_RESULT_PREFIX = "graphrag_"


@dataclass(frozen=True)
class MinioObject:
    object_key: str
    size: int


@dataclass(frozen=True)
class ParseResultSpec:
    course_material_id: int
    course_id: str
    result_type: str
    file_name: str
    minio_bucket: str
    minio_object_key: str
    file_size: int


@dataclass(frozen=True)
class ParseResultRow:
    row_id: int
    minio_object_key: str
    file_name: str
    result_type: str
    file_size: int


@dataclass(frozen=True)
class ParseResultUpdate:
    row_id: int
    spec: ParseResultSpec


@dataclass(frozen=True)
class ParseResultPlan:
    to_insert: list[ParseResultSpec]
    to_update: list[ParseResultUpdate]
    to_delete_ids: list[int]

    @property
    def change_count(self) -> int:
        return len(self.to_insert) + len(self.to_update) + len(self.to_delete_ids)


def infer_result_type(file_name: str) -> str:
    """与 `db_service.infer_result_type` 保持一致的轻量推断。"""
    lower = file_name.lower()
    if "content_list" in lower and lower.endswith(".json"):
        return "content_list_json"
    if "model" in lower and lower.endswith(".json"):
        return "model_json"
    if "layout" in lower and lower.endswith(".json"):
        return "layout_json"
    if lower.endswith(".md"):
        return "markdown"
    if lower.endswith((".png", ".jpg", ".jpeg", ".gif")):
        return "image"
    if lower.endswith(".pdf") and "origin" in lower:
        return "origin_pdf"
    return "other"


def build_parse_result_specs(
    *,
    course_id: str,
    material_id: int,
    artifacts_bucket: str,
    objects: Iterable[MinioObject],
    graphrag_prefix: str = "graphrag",
) -> list[ParseResultSpec]:
    """从 MinIO 新命名空间对象生成 parse_results 目标状态。"""
    material_prefix = f"{course_id}/material_{material_id}/"
    graphrag_material_prefix = f"{course_id}/{graphrag_prefix}/material_{material_id}/"
    specs: list[ParseResultSpec] = []

    def sort_key(item: MinioObject) -> tuple[int, str]:
        return (0 if item.object_key.startswith(material_prefix) else 1, item.object_key)

    for obj in sorted(objects, key=sort_key):
        if obj.object_key.startswith(graphrag_material_prefix):
            base_name = Path(obj.object_key).name
            file_name = f"{GRAPHRAG_RESULT_PREFIX}{base_name}"
        elif obj.object_key.startswith(material_prefix):
            file_name = Path(obj.object_key).name
        else:
            continue

        specs.append(
            ParseResultSpec(
                course_material_id=material_id,
                course_id=course_id,
                result_type=infer_result_type(file_name),
                file_name=file_name,
                minio_bucket=artifacts_bucket,
                minio_object_key=obj.object_key,
                file_size=obj.size,
            )
        )

    return specs


def plan_parse_result_reconciliation(
    *,
    existing: Sequence[ParseResultRow],
    expected: Sequence[ParseResultSpec],
    managed_prefixes: Sequence[str],
) -> ParseResultPlan:
    """计算 parse_results 从当前状态到目标状态的最小修复动作。"""
    expected_by_key = {item.minio_object_key: item for item in expected}
    existing_by_key: dict[str, list[ParseResultRow]] = {}
    for row in existing:
        existing_by_key.setdefault(row.minio_object_key, []).append(row)
    candidate_rows_by_file_name: dict[str, list[ParseResultRow]] = {}
    for row in existing:
        if any(row.minio_object_key.startswith(prefix) for prefix in managed_prefixes):
            candidate_rows_by_file_name.setdefault(row.file_name, []).append(row)

    to_insert: list[ParseResultSpec] = []
    to_update: list[ParseResultUpdate] = []
    to_delete_ids: set[int] = set()
    protected_update_ids: set[int] = set()

    for object_key, spec in expected_by_key.items():
        rows = existing_by_key.get(object_key, [])
        if not rows:
            migration_candidates = [
                row
                for row in candidate_rows_by_file_name.get(spec.file_name, [])
                if row.minio_object_key not in expected_by_key
            ]
            if not migration_candidates:
                to_insert.append(spec)
                continue
            keeper = migration_candidates[0]
            to_update.append(ParseResultUpdate(keeper.row_id, spec))
            protected_update_ids.add(keeper.row_id)
            for duplicate in migration_candidates[1:]:
                to_delete_ids.add(duplicate.row_id)
            continue

        keeper = rows[0]
        if (
            keeper.file_name != spec.file_name
            or keeper.result_type != spec.result_type
            or keeper.file_size != spec.file_size
        ):
            to_update.append(ParseResultUpdate(keeper.row_id, spec))

        for duplicate in rows[1:]:
            to_delete_ids.add(duplicate.row_id)

    for row in existing:
        if row.minio_object_key in expected_by_key:
            continue
        if row.row_id in protected_update_ids:
            continue
        if any(row.minio_object_key.startswith(prefix) for prefix in managed_prefixes):
            to_delete_ids.add(row.row_id)

    return ParseResultPlan(
        to_insert=to_insert,
        to_update=to_update,
        to_delete_ids=sorted(to_delete_ids),
    )


def plan_prefix_repairs(
    *,
    objects: Sequence[MinioObject],
    legacy_prefix: str,
    material_prefix: str,
) -> list[tuple[str, str]]:
    """计算从旧 `pdf_<id>` 前缀补齐新 `material_<id>` 前缀所需复制动作。"""
    existing_keys = {obj.object_key for obj in objects}
    repairs: list[tuple[str, str]] = []
    for obj in sorted(objects, key=lambda item: item.object_key):
        if not obj.object_key.startswith(legacy_prefix):
            continue
        target_key = f"{material_prefix}{obj.object_key[len(legacy_prefix):]}"
        if target_key not in existing_keys:
            repairs.append((obj.object_key, target_key))
    return repairs


def load_env(env_file: Path | None) -> None:
    if env_file:
        load_dotenv(env_file)
        return
    default_env = Path(__file__).resolve().parents[1] / ".env"
    if default_env.exists():
        load_dotenv(default_env)


def bool_env(name: str, default: bool = False) -> bool:
    value = os.getenv(name)
    if value is None:
        return default
    return value.lower() in {"1", "true", "yes", "on"}


def create_minio_client() -> Minio:
    return Minio(
        endpoint=os.getenv("MINIO_ENDPOINT", "localhost:9000"),
        access_key=os.getenv("MINIO_ACCESS_KEY", "admin"),
        secret_key=os.getenv("MINIO_SECRET_KEY", "12345678"),
        secure=bool_env("MINIO_SECURE", False),
    )


def connect_mysql():
    return pymysql.connect(
        host=os.getenv("MYSQL_HOST", "localhost"),
        port=int(os.getenv("MYSQL_PORT", "3306")),
        user=os.getenv("MYSQL_USER", "root"),
        password=os.getenv("MYSQL_PASSWORD", ""),
        database=os.getenv("MYSQL_DATABASE", "mineru_parser"),
        charset="utf8mb4",
        cursorclass=DictCursor,
    )


def stat_object(client: Minio, bucket: str, object_key: str):
    try:
        return client.stat_object(bucket, object_key)
    except S3Error as exc:
        if exc.code in {"NoSuchKey", "NoSuchObject"}:
            return None
        raise


def list_objects(client: Minio, bucket: str, prefixes: Sequence[str]) -> list[MinioObject]:
    objects: list[MinioObject] = []
    for prefix in prefixes:
        for obj in client.list_objects(bucket, prefix=prefix, recursive=True):
            if obj.object_name:
                objects.append(MinioObject(obj.object_name, int(obj.size or 0)))
    return objects


def object_md5(client: Minio, bucket: str, object_key: str) -> str:
    response = client.get_object(bucket, object_key)
    try:
        digest = hashlib.md5()
        for chunk in response.stream(1024 * 1024):
            digest.update(chunk)
        return digest.hexdigest()
    finally:
        response.close()
        response.release_conn()


def ensure_pdf_object(
    *,
    client: Minio,
    pdf_bucket: str,
    object_key: str,
    legacy_object_key: str,
    expected_md5: str,
    dry_run: bool,
    verify_md5: bool,
) -> int:
    current = stat_object(client, pdf_bucket, object_key)
    if current is None:
        legacy = stat_object(client, pdf_bucket, legacy_object_key)
        if legacy is None:
            raise RuntimeError(
                f"PDF 对象不存在: {pdf_bucket}/{object_key}，且找不到旧对象 {pdf_bucket}/{legacy_object_key}"
            )
        if not dry_run:
            client.copy_object(pdf_bucket, object_key, CopySource(pdf_bucket, legacy_object_key))
        current = legacy

    if verify_md5:
        actual_md5 = object_md5(client, pdf_bucket, object_key)
        if actual_md5 != expected_md5:
            raise RuntimeError(f"PDF MD5 不一致: expected={expected_md5}, actual={actual_md5}")

    return int(current.size or 0)


def copy_missing_prefix_objects(
    *,
    client: Minio,
    bucket: str,
    repairs: Sequence[tuple[str, str]],
    dry_run: bool,
) -> None:
    for source_key, target_key in repairs:
        if not dry_run:
            client.copy_object(bucket, target_key, CopySource(bucket, source_key))


def remove_legacy_objects(
    *,
    client: Minio,
    bucket: str,
    prefixes: Sequence[str],
    dry_run: bool,
) -> int:
    removed = 0
    for prefix in prefixes:
        for obj in client.list_objects(bucket, prefix=prefix, recursive=True):
            if not obj.object_name:
                continue
            removed += 1
            if not dry_run:
                client.remove_object(bucket, obj.object_name)
    return removed


def upsert_course(cursor, *, course_id: str, course_name: str, description: str) -> None:
    cursor.execute(
        """
        INSERT INTO courses (course_id, course_name, description, status, access_policy, is_deleted)
        VALUES (%s, %s, %s, 'active', 'restricted', 0)
        ON DUPLICATE KEY UPDATE
          course_name = VALUES(course_name),
          description = VALUES(description),
          status = VALUES(status),
          access_policy = VALUES(access_policy),
          is_deleted = VALUES(is_deleted)
        """,
        (course_id, course_name, description),
    )


def upsert_material_object(
    cursor,
    *,
    original_file_name: str,
    file_md5: str,
    file_size: int,
    minio_bucket: str,
    minio_object_key: str,
) -> int:
    cursor.execute(
        """
        INSERT INTO material_objects
          (original_file_name, file_md5, file_size, mime_type, minio_bucket, minio_object_key)
        VALUES (%s, %s, %s, 'application/pdf', %s, %s)
        ON DUPLICATE KEY UPDATE
          original_file_name = VALUES(original_file_name),
          file_size = VALUES(file_size),
          mime_type = VALUES(mime_type),
          minio_bucket = VALUES(minio_bucket),
          minio_object_key = VALUES(minio_object_key)
        """,
        (original_file_name, file_md5, file_size, minio_bucket, minio_object_key),
    )
    cursor.execute("SELECT id FROM material_objects WHERE file_md5 = %s", (file_md5,))
    row = cursor.fetchone()
    return int(row["id"])


def upsert_course_material(
    cursor,
    *,
    material_id: int,
    course_id: str,
    material_object_id: int,
    display_name: str,
    material_type: str,
    parse_status: str,
) -> int:
    cursor.execute(
        """
        INSERT INTO course_materials
          (id, course_id, material_object_id, display_name, material_type, parse_status,
           parse_started_at, parse_finished_at, parse_error_msg)
        VALUES (%s, %s, %s, %s, %s, %s, NULL, CURRENT_TIMESTAMP, NULL)
        ON DUPLICATE KEY UPDATE
          material_object_id = VALUES(material_object_id),
          display_name = VALUES(display_name),
          material_type = VALUES(material_type),
          parse_status = VALUES(parse_status),
          parse_finished_at = VALUES(parse_finished_at),
          parse_error_msg = VALUES(parse_error_msg)
        """,
        (material_id, course_id, material_object_id, display_name, material_type, parse_status),
    )
    cursor.execute(
        "SELECT id FROM course_materials WHERE course_id = %s AND material_object_id = %s",
        (course_id, material_object_id),
    )
    row = cursor.fetchone()
    if not row:
        raise RuntimeError("course_materials 修复后未找到目标课程资料关系")
    actual_id = int(row["id"])
    if actual_id != material_id:
        raise RuntimeError(
            f"课程资料 ID 为 {actual_id}，但 MinIO 命名空间需要 material_{material_id}；请先处理 ID 冲突"
        )
    return actual_id


def fetch_existing_parse_results(cursor, material_id: int) -> list[ParseResultRow]:
    cursor.execute(
        """
        SELECT id, minio_object_key, file_name, result_type, file_size
        FROM parse_results
        WHERE course_material_id = %s
        ORDER BY id
        """,
        (material_id,),
    )
    return [
        ParseResultRow(
            row_id=int(row["id"]),
            minio_object_key=row["minio_object_key"],
            file_name=row["file_name"],
            result_type=row["result_type"],
            file_size=int(row["file_size"] or 0),
        )
        for row in cursor.fetchall()
    ]


def apply_parse_result_plan(cursor, plan: ParseResultPlan) -> None:
    for row_id in plan.to_delete_ids:
        cursor.execute("DELETE FROM parse_results WHERE id = %s", (row_id,))

    for update in plan.to_update:
        spec = update.spec
        cursor.execute(
            """
            UPDATE parse_results
            SET course_id = %s,
                result_type = %s,
                file_name = %s,
                minio_bucket = %s,
                minio_object_key = %s,
                file_size = %s
            WHERE id = %s
            """,
            (
                spec.course_id,
                spec.result_type,
                spec.file_name,
                spec.minio_bucket,
                spec.minio_object_key,
                spec.file_size,
                update.row_id,
            ),
        )

    for spec in plan.to_insert:
        cursor.execute(
            """
            INSERT INTO parse_results
              (course_material_id, course_id, result_type, file_name,
               minio_bucket, minio_object_key, file_size)
            VALUES (%s, %s, %s, %s, %s, %s, %s)
            """,
            (
                spec.course_material_id,
                spec.course_id,
                spec.result_type,
                spec.file_name,
                spec.minio_bucket,
                spec.minio_object_key,
                spec.file_size,
            ),
        )


def ensure_no_legacy_db_refs(cursor, *, material_id: int, legacy_prefixes: Sequence[str]) -> None:
    conditions = " OR ".join(["minio_object_key LIKE %s" for _ in legacy_prefixes])
    params = [f"{prefix}%" for prefix in legacy_prefixes]
    cursor.execute(
        f"""
        SELECT COUNT(*) AS count
        FROM parse_results
        WHERE course_material_id = %s AND ({conditions})
        """,
        [material_id, *params],
    )
    row = cursor.fetchone()
    if int(row["count"] or 0) > 0:
        raise RuntimeError("仍有 parse_results 指向旧 pdf_{id} 命名空间，拒绝清理旧对象")


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(description="幂等修复本地课程资料种子数据")
    parser.add_argument("--env-file", type=Path, default=None, help="环境变量文件，默认读取 pdf_ingest/.env")
    parser.add_argument("--course-id", default=DEFAULT_COURSE_ID)
    parser.add_argument("--course-name", default=DEFAULT_COURSE_NAME)
    parser.add_argument("--description", default=DEFAULT_DESCRIPTION)
    parser.add_argument("--material-id", type=int, default=DEFAULT_MATERIAL_ID)
    parser.add_argument("--display-name", default=DEFAULT_DISPLAY_NAME)
    parser.add_argument("--material-type", default="textbook")
    parser.add_argument("--parse-status", choices=["pending", "processing", "done", "failed"], default="done")
    parser.add_argument("--file-md5", default=DEFAULT_FILE_MD5)
    parser.add_argument("--pdf-bucket", default=None)
    parser.add_argument("--artifacts-bucket", default=None)
    parser.add_argument("--pdf-object-key", default=None)
    parser.add_argument("--legacy-pdf-object-key", default=None)
    parser.add_argument("--graphrag-prefix", default="graphrag")
    parser.add_argument("--cleanup-legacy", action="store_true", help="确认 DB 已切新路径后删除旧 pdf_{id} 产物前缀")
    parser.add_argument("--verify-pdf-md5", action="store_true", help="读取 PDF 全量内容并校验 MD5")
    parser.add_argument("--dry-run", action="store_true", help="只打印计划，不写 DB/MinIO")
    return parser.parse_args()


def main() -> int:
    args = parse_args()
    load_env(args.env_file)

    pdf_bucket = args.pdf_bucket or os.getenv("MINIO_BUCKET_PDF", "course-pdfs")
    artifacts_bucket = args.artifacts_bucket or os.getenv("MINIO_BUCKET_ARTIFACTS", "course-artifacts")
    pdf_object_key = args.pdf_object_key or f"materials/{args.file_md5}.pdf"
    legacy_pdf_object_key = args.legacy_pdf_object_key or f"{args.course_id}/{args.display_name}"

    material_prefix = f"{args.course_id}/material_{args.material_id}/"
    legacy_material_prefix = f"{args.course_id}/pdf_{args.material_id}/"
    graphrag_material_prefix = f"{args.course_id}/{args.graphrag_prefix}/material_{args.material_id}/"
    legacy_graphrag_prefix = f"{args.course_id}/{args.graphrag_prefix}/pdf_{args.material_id}/"
    managed_prefixes = [
        material_prefix,
        graphrag_material_prefix,
        legacy_material_prefix,
        legacy_graphrag_prefix,
    ]

    client = create_minio_client()
    file_size = ensure_pdf_object(
        client=client,
        pdf_bucket=pdf_bucket,
        object_key=pdf_object_key,
        legacy_object_key=legacy_pdf_object_key,
        expected_md5=args.file_md5,
        dry_run=args.dry_run,
        verify_md5=args.verify_pdf_md5,
    )

    all_artifact_objects = list_objects(client, artifacts_bucket, managed_prefixes)
    repairs = []
    repairs.extend(
        plan_prefix_repairs(
            objects=all_artifact_objects,
            legacy_prefix=legacy_material_prefix,
            material_prefix=material_prefix,
        )
    )
    repairs.extend(
        plan_prefix_repairs(
            objects=all_artifact_objects,
            legacy_prefix=legacy_graphrag_prefix,
            material_prefix=graphrag_material_prefix,
        )
    )
    copy_missing_prefix_objects(client=client, bucket=artifacts_bucket, repairs=repairs, dry_run=args.dry_run)

    if args.dry_run:
        size_by_key = {obj.object_key: obj.size for obj in all_artifact_objects}
        simulated_repaired_objects = [
            MinioObject(target_key, size_by_key[source_key])
            for source_key, target_key in repairs
            if source_key in size_by_key
        ]
        current_objects = [
            obj
            for obj in [*all_artifact_objects, *simulated_repaired_objects]
            if obj.object_key.startswith(material_prefix)
            or obj.object_key.startswith(graphrag_material_prefix)
        ]
    else:
        current_objects = list_objects(client, artifacts_bucket, [material_prefix, graphrag_material_prefix])
    if not current_objects:
        raise RuntimeError("未找到 material_{id} 命名空间下的解析产物，无法生成 parse_results")
    expected_specs = build_parse_result_specs(
        course_id=args.course_id,
        material_id=args.material_id,
        artifacts_bucket=artifacts_bucket,
        objects=current_objects,
        graphrag_prefix=args.graphrag_prefix,
    )

    conn = connect_mysql()
    try:
        with conn.cursor() as cursor:
            existing_rows = fetch_existing_parse_results(cursor, args.material_id)
            result_plan = plan_parse_result_reconciliation(
                existing=existing_rows,
                expected=expected_specs,
                managed_prefixes=managed_prefixes,
            )

            print(
                "repair plan: "
                f"copy={len(repairs)}, insert={len(result_plan.to_insert)}, "
                f"update={len(result_plan.to_update)}, delete={len(result_plan.to_delete_ids)}"
            )

            if args.dry_run:
                conn.rollback()
                return 0

            upsert_course(
                cursor,
                course_id=args.course_id,
                course_name=args.course_name,
                description=args.description,
            )
            material_object_id = upsert_material_object(
                cursor,
                original_file_name=args.display_name,
                file_md5=args.file_md5,
                file_size=file_size,
                minio_bucket=pdf_bucket,
                minio_object_key=pdf_object_key,
            )
            upsert_course_material(
                cursor,
                material_id=args.material_id,
                course_id=args.course_id,
                material_object_id=material_object_id,
                display_name=args.display_name,
                material_type=args.material_type,
                parse_status=args.parse_status,
            )
            apply_parse_result_plan(cursor, result_plan)

            legacy_removed = 0
            if args.cleanup_legacy:
                ensure_no_legacy_db_refs(
                    cursor,
                    material_id=args.material_id,
                    legacy_prefixes=[legacy_material_prefix, legacy_graphrag_prefix],
                )
                legacy_removed = remove_legacy_objects(
                    client=client,
                    bucket=artifacts_bucket,
                    prefixes=[legacy_material_prefix, legacy_graphrag_prefix],
                    dry_run=False,
                )
                legacy_pdf = stat_object(client, pdf_bucket, legacy_pdf_object_key)
                if legacy_pdf is not None and legacy_pdf_object_key != pdf_object_key:
                    client.remove_object(pdf_bucket, legacy_pdf_object_key)
                    legacy_removed += 1

            if repairs or result_plan.change_count or legacy_removed:
                cursor.execute(
                    """
                    INSERT INTO parse_logs (course_material_id, log_level, log_message)
                    VALUES (%s, 'info', %s)
                    """,
                    (
                        args.material_id,
                        "本地课程资料种子修复脚本已执行：MinIO material_{id} 命名空间与数据库元数据已对齐。",
                    ),
                )

        conn.commit()
    except Exception:
        conn.rollback()
        raise
    finally:
        conn.close()

    print(f"done: parse_results={len(expected_specs)}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
