#!/usr/bin/env python3
"""把手工激活的 GraphRAG output 迁移为标准 build-run 工作区。

脚本默认 dry-run；只有传入 --execute 才会复制文件并更新 MySQL。
它不会删除原始 output 目录，便于迁移后保留人工回滚线索。
"""

from __future__ import annotations

import argparse
import json
import os
import shutil
from datetime import datetime, timezone
from pathlib import Path
from typing import Any


LAYOUT_DIRS = [
    "selection",
    "parse",
    "graph-input",
    "prompt",
    "index/input",
    "index/output",
    "index/cache",
    "index/reports",
    "index/logs",
    "qa-smoke",
]


def load_env_file(path: Path) -> None:
    if not path.exists():
        return
    for raw_line in path.read_text(encoding="utf-8").splitlines():
        line = raw_line.strip()
        if not line or line.startswith("#") or "=" not in line:
            continue
        key, value = line.split("=", 1)
        key = key.strip()
        value = value.strip().strip('"').strip("'")
        if key:
            os.environ.setdefault(key, value)


def connect_mysql(args: argparse.Namespace):
    try:
        import pymysql
        from pymysql.cursors import DictCursor
    except ImportError as exc:
        raise SystemExit("缺少 PyMySQL，请先在当前 Python 环境安装 PyMySQL。") from exc

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


def parse_json(value: Any, fallback: Any) -> Any:
    if value is None:
        return fallback
    if isinstance(value, (dict, list)):
        return value
    try:
        return json.loads(value)
    except (TypeError, json.JSONDecodeError):
        return fallback


def fetch_one(cursor, sql: str, params: tuple[Any, ...]) -> dict[str, Any]:
    cursor.execute(sql, params)
    row = cursor.fetchone()
    if not row:
        raise SystemExit(f"未找到记录: {sql} {params}")
    return row


def ensure_empty_or_force(path: Path, force: bool) -> None:
    if not path.exists():
        return
    has_content = any(path.iterdir())
    if has_content and not force:
        raise SystemExit(f"目标目录已存在且非空，请确认后使用 --force: {path}")


def copy_tree_contents(source: Path, target: Path) -> None:
    target.mkdir(parents=True, exist_ok=True)
    for child in source.iterdir():
        dest = target / child.name
        if child.is_dir():
            shutil.copytree(child, dest, dirs_exist_ok=True)
        else:
            shutil.copy2(child, dest)


def file_size(path: Path) -> int:
    return path.stat().st_size if path.exists() and path.is_file() else 0


def storage_uri(workspace_uri: str, relative_path: str) -> str:
    return f"{workspace_uri}/{relative_path.replace(os.sep, '/')}"


def build_artifact_rows(index_run_id: int, workspace_root: Path, workspace_uri: str) -> list[dict[str, Any]]:
    rows: list[dict[str, Any]] = []

    def add(artifact_type: str, relative_path: str) -> None:
        path = workspace_root / relative_path
        rows.append(
            {
                "index_run_id": index_run_id,
                "artifact_type": artifact_type,
                "display_name": path.name or artifact_type,
                "storage_uri": storage_uri(workspace_uri, relative_path),
                "storage_scope": "local",
                "artifact_status": "ready" if path.exists() else "missing",
                "file_size": file_size(path),
            }
        )

    input_dir = workspace_root / "index/input"
    if input_dir.is_dir():
        for path in sorted(input_dir.glob("*.json")):
            add("input_json", str(path.relative_to(workspace_root)))

    add("output_dir", "index/output")
    add("lancedb", "index/output/lancedb")

    output_dir = workspace_root / "index/output"
    if output_dir.is_dir():
        for path in sorted(output_dir.glob("*.parquet")):
            add("parquet", str(path.relative_to(workspace_root)))

    reports_dir = workspace_root / "index/reports"
    if reports_dir.is_dir():
        for path in sorted(reports_dir.iterdir()):
            add("report", str(path.relative_to(workspace_root)))

    add("log", "index/logs/process.log")
    add("manifest", "manifest.json")
    return rows


def write_manifest(
    manifest_path: Path,
    *,
    build_run: dict[str, Any],
    index_run: dict[str, Any],
    knowledge_base: dict[str, Any],
    workspace_uri: str,
    source_output_dir: Path,
    input_json: Path,
) -> None:
    manifest = {
        "source": "manual_index_migrated_to_build_run",
        "migratedAt": datetime.now(timezone.utc).isoformat(),
        "courseId": build_run.get("course_id"),
        "knowledgeBaseId": knowledge_base.get("id"),
        "knowledgeBaseName": knowledge_base.get("name"),
        "buildRunId": build_run.get("id"),
        "indexRunId": index_run.get("id"),
        "buildVersion": build_run.get("build_version"),
        "indexVersion": index_run.get("index_version"),
        "selectedMaterialIds": parse_json(build_run.get("selected_material_ids"), []),
        "workspaceUri": workspace_uri,
        "originalOutputDir": str(source_output_dir),
        "inputJson": str(input_json),
        "notes": [
            "原始 output 目录未删除。",
            "Python 查询时 dataDirUri 应指向 workspaceUri/index/output。",
        ],
    }
    manifest_path.write_text(json.dumps(manifest, ensure_ascii=False, indent=2), encoding="utf-8")


def merge_metadata(existing: Any, updates: dict[str, Any]) -> str:
    metadata = parse_json(existing, {})
    if not isinstance(metadata, dict):
        metadata = {"legacyMetadata": metadata}
    metadata.update(updates)
    return json.dumps(metadata, ensure_ascii=False)


def migrate_files(
    *,
    source_output_dir: Path,
    input_json: Path,
    workspace_root: Path,
    workspace_uri: str,
    build_run: dict[str, Any],
    index_run: dict[str, Any],
    knowledge_base: dict[str, Any],
    force: bool,
) -> None:
    ensure_empty_or_force(workspace_root, force)
    for relative in LAYOUT_DIRS:
        (workspace_root / relative).mkdir(parents=True, exist_ok=True)

    copy_tree_contents(source_output_dir, workspace_root / "index/output")

    reports_source = source_output_dir / "reports"
    if reports_source.is_dir():
        copy_tree_contents(reports_source, workspace_root / "index/reports")

    shutil.copy2(input_json, workspace_root / "index/input" / input_json.name)
    shutil.copy2(input_json, workspace_root / "graph-input" / input_json.name)

    selected_materials = build_run.get("selected_material_ids") or "[]"
    (workspace_root / "selection/selected_materials.json").write_text(selected_materials, encoding="utf-8")

    process_log = source_output_dir / "index.log"
    if not process_log.exists():
        process_log = reports_source / "indexing-engine.log"
    if process_log.exists():
        shutil.copy2(process_log, workspace_root / "index/logs/process.log")
    else:
        (workspace_root / "index/logs/process.log").write_text(
            "manual migration: original process log not found\n",
            encoding="utf-8",
        )

    write_manifest(
        workspace_root / "manifest.json",
        build_run=build_run,
        index_run=index_run,
        knowledge_base=knowledge_base,
        workspace_uri=workspace_uri,
        source_output_dir=source_output_dir,
        input_json=input_json,
    )


def update_database(
    connection,
    *,
    args: argparse.Namespace,
    build_run: dict[str, Any],
    index_run: dict[str, Any],
    workspace_uri: str,
    artifact_rows: list[dict[str, Any]],
) -> None:
    now = datetime.now(timezone.utc).isoformat()
    build_metadata = merge_metadata(
        build_run.get("build_metadata"),
        {
            "source": "manual_db_activation_migrated_to_build_run",
            "originalOutputDir": args.source_output_dir,
            "workspaceUri": workspace_uri,
            "artifactMigrationAt": now,
        },
    )
    run_metadata = merge_metadata(
        index_run.get("run_metadata"),
        {
            "source": "manual_db_activation_migrated_to_build_run",
            "workspaceUri": workspace_uri,
            "dataDirUri": f"{workspace_uri}/index/output",
            "artifactMigrationAt": now,
        },
    )

    with connection.cursor() as cursor:
        cursor.execute(
            """
            UPDATE knowledge_base_build_runs
               SET workspace_uri=%s,
                   active_index_run_id=%s,
                   build_metadata=%s,
                   updated_at=NOW()
             WHERE id=%s
            """,
            (workspace_uri, args.index_run_id, build_metadata, args.build_run_id),
        )
        cursor.execute(
            """
            UPDATE index_runs
               SET build_run_id=%s,
                   run_metadata=%s
             WHERE id=%s
            """,
            (args.build_run_id, run_metadata, args.index_run_id),
        )
        cursor.execute("DELETE FROM index_artifacts WHERE index_run_id=%s", (args.index_run_id,))
        cursor.executemany(
            """
            INSERT INTO index_artifacts
                (index_run_id, artifact_type, display_name, storage_uri, storage_scope, artifact_status, file_size)
            VALUES
                (%(index_run_id)s, %(artifact_type)s, %(display_name)s, %(storage_uri)s,
                 %(storage_scope)s, %(artifact_status)s, %(file_size)s)
            """,
            artifact_rows,
        )
    connection.commit()


def positive_int(value: str) -> int:
    parsed = int(value)
    if parsed <= 0:
        raise argparse.ArgumentTypeError("必须是正整数")
    return parsed


def build_parser() -> argparse.ArgumentParser:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--env-file", action="append", default=[], help="可重复指定的 .env 文件")
    parser.add_argument("--mysql-host", default=os.getenv("MYSQL_HOST", "127.0.0.1"))
    parser.add_argument("--mysql-port", type=int, default=int(os.getenv("MYSQL_PORT", "23306")))
    parser.add_argument("--mysql-user", default=os.getenv("MYSQL_USER", "root"))
    parser.add_argument("--mysql-password", default=os.getenv("MYSQL_PASSWORD") or os.getenv("MYSQL_ROOT_PASSWORD", ""))
    parser.add_argument("--mysql-database", default=os.getenv("MYSQL_DATABASE", "ocqa"))
    parser.add_argument("--knowledge-base-id", type=positive_int, required=True)
    parser.add_argument("--build-run-id", type=positive_int, required=True)
    parser.add_argument("--index-run-id", type=positive_int, required=True)
    parser.add_argument("--source-output-dir", required=True, help="手工激活 GraphRAG output 目录")
    parser.add_argument("--build-runs-root", required=True, help="标准 GRAPHRAG_BUILD_RUNS_ROOT")
    parser.add_argument("--input-json", required=True, help="本次构建的 GraphRAG 输入 JSON")
    parser.add_argument("--workspace-uri", default="", help="默认使用 user_{user}/kb_{kb}/build_{buildRun}")
    parser.add_argument("--force", action="store_true", help="允许目标目录已存在时覆盖同名文件")
    parser.add_argument("--execute", action="store_true", help="实际复制文件并更新数据库")
    return parser


def main() -> int:
    parser = build_parser()
    args = parser.parse_args()
    for env_file in args.env_file:
        load_env_file(Path(env_file))
    # env-file 需要在初次 parse 后加载，所以这里补一次环境默认值。
    args.mysql_host = args.mysql_host or os.getenv("MYSQL_HOST", "127.0.0.1")
    args.mysql_password = args.mysql_password or os.getenv("MYSQL_PASSWORD") or os.getenv("MYSQL_ROOT_PASSWORD", "")

    source_output_dir = Path(args.source_output_dir).expanduser().resolve()
    build_runs_root = Path(args.build_runs_root).expanduser().resolve()
    input_json = Path(args.input_json).expanduser().resolve()

    if not source_output_dir.is_dir():
        raise SystemExit(f"源 output 目录不存在: {source_output_dir}")
    if not input_json.is_file():
        raise SystemExit(f"输入 JSON 不存在: {input_json}")

    with connect_mysql(args) as connection:
        with connection.cursor() as cursor:
            knowledge_base = fetch_one(cursor, "SELECT * FROM knowledge_bases WHERE id=%s", (args.knowledge_base_id,))
            build_run = fetch_one(cursor, "SELECT * FROM knowledge_base_build_runs WHERE id=%s", (args.build_run_id,))
            index_run = fetch_one(cursor, "SELECT * FROM index_runs WHERE id=%s", (args.index_run_id,))
            cursor.execute(
                "SELECT artifact_type, storage_uri, artifact_status FROM index_artifacts WHERE index_run_id=%s ORDER BY id",
                (args.index_run_id,),
            )
            old_artifacts = cursor.fetchall()

        if build_run["knowledge_base_id"] != args.knowledge_base_id:
            raise SystemExit("build_run.knowledge_base_id 与参数不一致")
        if index_run["knowledge_base_id"] != args.knowledge_base_id:
            raise SystemExit("index_run.knowledge_base_id 与参数不一致")

        workspace_uri = args.workspace_uri.strip("/") if args.workspace_uri else (
            f"user_{build_run.get('requested_by_user_id') or 0}/kb_{args.knowledge_base_id}/build_{args.build_run_id}"
        )
        workspace_root = build_runs_root / workspace_uri

        print("迁移计划")
        print(f"- knowledgeBaseId: {args.knowledge_base_id} ({knowledge_base.get('name')})")
        print(f"- buildRunId: {args.build_run_id}, indexRunId: {args.index_run_id}")
        print(f"- sourceOutputDir: {source_output_dir}")
        print(f"- targetWorkspace: {workspace_root}")
        print(f"- workspaceUri: {workspace_uri}")
        print(f"- inputJson: {input_json}")
        print(f"- oldArtifactCount: {len(old_artifacts)}")

        if not args.execute:
            print("dry-run 完成；未复制文件，未更新数据库。添加 --execute 才会执行。")
            connection.rollback()
            return 0

        migrate_files(
            source_output_dir=source_output_dir,
            input_json=input_json,
            workspace_root=workspace_root,
            workspace_uri=workspace_uri,
            build_run=build_run,
            index_run=index_run,
            knowledge_base=knowledge_base,
            force=args.force,
        )
        artifacts = build_artifact_rows(args.index_run_id, workspace_root, workspace_uri)
        update_database(
            connection,
            args=args,
            build_run=build_run,
            index_run=index_run,
            workspace_uri=workspace_uri,
            artifact_rows=artifacts,
        )
        print(f"迁移完成；已登记 artifactCount={len(artifacts)}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
