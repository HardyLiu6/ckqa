#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
从 MinIO 获取 GraphRAG 输入数据
================================
优先从 MinIO 下载 pdf_ingest 直接生成的 GraphRAG JSON 输入文件，
并写入本地 `input/` 目录供 GraphRAG 直接索引。

兼容说明：
- 新版本 pdf_ingest 直接产出 `section_docs.json` / `page_docs.json`
- 若 MinIO 中仍是历史 `*.jsonl` 文件，本脚本会自动转换为 JSON 数组

同时会尽量保留 pdf_ingest 导出的标准字段，并确保 GraphRAG
需要的 metadata 字段位于顶层，使 GraphRAG 的 metadata
收集功能可以直接读取 page_start、page_end、section_level 等信息。

用法:
    python utils/fetch_from_minio.py <course_id> [--clean] [--input-dir input]

示例:
    # 拉取 os 课程的数据（清空旧文件）
    python utils/fetch_from_minio.py os --clean

    # 拉取到自定义目录
    python utils/fetch_from_minio.py os --input-dir ./my_input
"""

import argparse
import json
import os
import shutil
import sys
import tempfile
from pathlib import Path

from dotenv import load_dotenv
from minio import Minio
from minio.error import S3Error


# ===================== 配置 =====================

def get_minio_client() -> Minio:
    """从环境变量创建 MinIO 客户端"""
    endpoint = os.getenv("MINIO_ENDPOINT", "localhost:9000")
    access_key = os.getenv("MINIO_ACCESS_KEY", "admin")
    secret_key = os.getenv("MINIO_SECRET_KEY", "12345678")
    secure = os.getenv("MINIO_SECURE", "false").lower() in ("true", "1", "yes", "on")

    return Minio(
        endpoint=endpoint,
        access_key=access_key,
        secret_key=secret_key,
        secure=secure,
    )


def get_bucket() -> str:
    """获取 artifacts 存储桶名称"""
    return os.getenv("MINIO_BUCKET_ARTIFACTS", "course-artifacts")


# ===================== 元数据展平 =====================

# 需要从嵌套 metadata 中提升到顶层的字段
_METADATA_FIELDS_TO_FLATTEN = [
    "id",
    "course_id",
    "source_file",
    "document_type",
    "chapter",
    "section",
    "subsection",
    "heading_level",
    "heading_path",
    "doc_unit",
    "section_level",
    "page_start",
    "page_end",
    "page_no",
    "has_table",
    "has_equation",
    "has_image",
    "table_count",
    "equation_count",
    "image_count",
    "chunk_index",
    "chunk_total",
    "chunk_char_count",
    "chunk_strategy",
]


def _build_heading_path_text(value) -> str:
    """将 heading_path 归一化为便于 GraphRAG 使用的文本。"""
    if isinstance(value, list):
        parts = [str(item).strip() for item in value if str(item).strip()]
        return " > ".join(parts)
    if isinstance(value, str):
        return value.strip()
    return ""


def _extract_primary_text(record: dict) -> str:
    """
    提取记录的主文本字段。

    兼容两类输入：
    1. GraphRAG 投影记录：使用 `text`
    2. 标准文档记录：使用 `content`
    """
    for field in ("text", "content"):
        value = record.get(field, "")
        if isinstance(value, str) and value.strip():
            return value
    return ""


def flatten_record(record: dict) -> dict:
    """
    将记录转换为 GraphRAG 兼容的扁平 JSON 对象。

    输入格式 (来自 pdf_ingest):
        {
            "title": "...",
            "text": "...",
            "metadata": {
                "course_id": "os",
                "source_file": "book.pdf",
                "section_level": 1,
                "page_start": 32,
                "page_end": 84,
                "block_ids": [...],   # 丢弃
                "images": [...],      # 丢弃
                ...
            }
        }

    输出格式:
        {
            "title": "...",
            "text": "...",
            "course_id": "os",
            "source_file": "book.pdf",
            "section_level": 1,
            "page_start": 32,
            "page_end": 84
        }
    """
    # 优先保留上游已经提供的顶层字段，避免未来标准字段再次在此处丢失。
    flat = dict(record)
    if "title" in record:
        flat["title"] = record.get("title", "")
    if "text" in record:
        flat["text"] = record.get("text", "")

    metadata = record.get("metadata", {})
    if isinstance(metadata, dict):
        for field in _METADATA_FIELDS_TO_FLATTEN:
            if field in metadata and field not in flat:
                flat[field] = metadata[field]

    for field in _METADATA_FIELDS_TO_FLATTEN:
        if field not in flat and field in record:
            flat[field] = record[field]

    if "heading_path_text" not in flat:
        heading_path = flat.get("heading_path")
        heading_path_text = _build_heading_path_text(heading_path)
        if heading_path_text:
            flat["heading_path_text"] = heading_path_text

    # 对标准文档保持原始 `content` 字段，同时为本地预览补一个标题。
    if "title" not in flat:
        derived_title = flat.get("heading_path_text", "")
        if derived_title:
            flat["title"] = derived_title

    return flat


# ===================== 核心逻辑 =====================

def _download_object(client: Minio, bucket: str, object_key: str, local_path: Path) -> bool:
    """下载单个 MinIO 对象。若对象不存在返回 False。"""
    try:
        client.fget_object(
            bucket_name=bucket,
            object_name=object_key,
            file_path=str(local_path),
        )
        return True
    except S3Error as e:
        if e.code == "NoSuchKey":
            return False
        raise


def _find_unique_namespaced_key(
    client: Minio,
    bucket: str,
    course_id: str,
    graphrag_prefix: str,
    filename: str,
) -> tuple[str | None, bool]:
    """
    在 `course_id/graphrag/pdf_*/` 下寻找唯一匹配文件。

    Returns:
        (object_key, ambiguous)
    """
    prefix = f"{course_id}/{graphrag_prefix}/"
    matches = []
    for obj in client.list_objects(bucket, prefix=prefix, recursive=True):
        object_name = obj.object_name
        if not object_name:
            continue
        if not object_name.endswith(f"/{filename}"):
            continue
        if "/pdf_" not in object_name:
            continue
        matches.append(object_name)

    if len(matches) == 1:
        return matches[0], False
    if len(matches) > 1:
        return None, True
    return None, False


def fetch_and_prepare(
    course_id: str,
    input_dir: Path,
    clean: bool = False,
    graphrag_prefix: str = "graphrag",
    json_filename: str = "section_docs.json",
    pdf_file_id: int | None = None,
) -> dict:
    """
    从 MinIO 下载 GraphRAG 输入文件，并在必要时兼容转换历史 JSONL。

    Args:
        course_id: 课程 ID
        input_dir: GraphRAG input 目录
        clean: 是否清空旧的 .json 输入文件
        graphrag_prefix: MinIO 中的前缀路径
        json_filename: GraphRAG 输入文件名

    Returns:
        {status, course_id, documents_count, output_file, input_dir}
    """
    client = get_minio_client()
    bucket = get_bucket()

    if json_filename.endswith(".jsonl"):
        output_filename = json_filename[:-1]
        preferred_filename = output_filename
        legacy_filename = json_filename
    elif json_filename.endswith(".json"):
        output_filename = json_filename
        preferred_filename = json_filename
        legacy_filename = json_filename[:-1] + "l"
    else:
        output_filename = f"{json_filename}.json"
        preferred_filename = output_filename
        legacy_filename = f"{json_filename}.jsonl"

    if pdf_file_id is not None:
        subdir = f"{graphrag_prefix}/pdf_{pdf_file_id}"
        preferred_key = f"{course_id}/{subdir}/{preferred_filename}"
        legacy_key = f"{course_id}/{subdir}/{legacy_filename}"
    else:
        preferred_key = f"{course_id}/{graphrag_prefix}/{preferred_filename}"
        legacy_key = f"{course_id}/{graphrag_prefix}/{legacy_filename}"

    print(f"[MinIO] 优先下载 {bucket}/{preferred_key} ...")

    # 1) 下载源文件到临时目录
    tmp_dir = Path(tempfile.mkdtemp(prefix=f"graphrag_fetch_{course_id}_"))
    tmp_input = tmp_dir / preferred_filename

    source_format = None
    if _download_object(client, bucket, preferred_key, tmp_input):
        source_format = "json"
        print(f"[MinIO] 下载完成: {tmp_input} ({tmp_input.stat().st_size} bytes)")
    else:
        if pdf_file_id is None:
            namespaced_key, ambiguous = _find_unique_namespaced_key(
                client, bucket, course_id, graphrag_prefix, preferred_filename
            )
            if ambiguous:
                shutil.rmtree(tmp_dir, ignore_errors=True)
                print(
                    f"[错误] 课程 {course_id} 下存在多份 GraphRAG 输入，请使用 --pdf-file-id 指定。",
                    file=sys.stderr,
                )
                return {"status": "ambiguous", "course_id": course_id}
            if namespaced_key:
                tmp_input = tmp_dir / preferred_filename
                if _download_object(client, bucket, namespaced_key, tmp_input):
                    source_format = "json"
                    print(
                        f"[MinIO] 在 namespaced 路径找到文件: {namespaced_key}"
                    )
                    print(f"[MinIO] 下载完成: {tmp_input} ({tmp_input.stat().st_size} bytes)")

        if source_format is None:
            tmp_input = tmp_dir / legacy_filename
            print(f"[MinIO] 未找到 {preferred_key}，尝试兼容历史文件 {legacy_key} ...")
            if _download_object(client, bucket, legacy_key, tmp_input):
                source_format = "jsonl"
                print(f"[MinIO] 下载完成: {tmp_input} ({tmp_input.stat().st_size} bytes)")
            else:
                if pdf_file_id is None:
                    namespaced_key, ambiguous = _find_unique_namespaced_key(
                        client, bucket, course_id, graphrag_prefix, legacy_filename
                    )
                    if ambiguous:
                        shutil.rmtree(tmp_dir, ignore_errors=True)
                        print(
                            f"[错误] 课程 {course_id} 下存在多份 GraphRAG 输入，请使用 --pdf-file-id 指定。",
                            file=sys.stderr,
                        )
                        return {"status": "ambiguous", "course_id": course_id}
                    if namespaced_key and _download_object(client, bucket, namespaced_key, tmp_input):
                        source_format = "jsonl"
                        print(
                            f"[MinIO] 在 namespaced 路径找到历史文件: {namespaced_key}"
                        )
                        print(f"[MinIO] 下载完成: {tmp_input} ({tmp_input.stat().st_size} bytes)")

            if source_format is None:
                shutil.rmtree(tmp_dir, ignore_errors=True)
                print(
                    f"[错误] MinIO 中未找到 {preferred_key} 或 {legacy_key}。"
                    f"请先在 pdf_ingest 中运行 export-graphrag 命令。",
                    file=sys.stderr,
                )
                return {"status": "not_found", "course_id": course_id}

    if source_format is None:
        shutil.rmtree(tmp_dir, ignore_errors=True)
        return {"status": "not_found", "course_id": course_id}

    # 2) 准备 input 目录
    input_dir.mkdir(parents=True, exist_ok=True)

    if clean:
        # 删除旧的 .json 和 .txt 文件（兼容旧版本生成的 .txt）
        old_files = list(input_dir.glob("*.json")) + list(input_dir.glob("*.txt"))
        for f in old_files:
            f.unlink()
        print(f"[清理] 已删除 {len(old_files)} 个旧输入文件")

    # 3) 解析输入文件，并确保记录为 GraphRAG 可读的扁平结构
    documents = []
    skipped = 0

    if source_format == "json":
        with open(tmp_input, "r", encoding="utf-8") as f:
            try:
                payload = json.load(f)
            except json.JSONDecodeError as e:
                shutil.rmtree(tmp_dir, ignore_errors=True)
                raise RuntimeError(f"JSON 文件解析失败: {tmp_input}: {e}") from e

        if not isinstance(payload, list):
            shutil.rmtree(tmp_dir, ignore_errors=True)
            raise RuntimeError(f"期望 JSON 数组，但实际为: {type(payload).__name__}")

        for idx, record in enumerate(payload, start=1):
            if not isinstance(record, dict):
                print(f"[警告] 第 {idx} 条记录不是对象，已跳过", file=sys.stderr)
                continue

            text = _extract_primary_text(record)
            if not text.strip():
                print(f"[跳过] 第 {idx} 条记录 text 为空: {record.get('title', '?')}")
                skipped += 1
                continue

            documents.append(flatten_record(record))
    else:
        with open(tmp_input, "r", encoding="utf-8") as f:
            for line_no, line in enumerate(f, start=1):
                line = line.strip()
                if not line:
                    continue

                try:
                    record = json.loads(line)
                except json.JSONDecodeError as e:
                    print(f"[警告] 第 {line_no} 行 JSON 解析失败: {e}", file=sys.stderr)
                    continue

                text = _extract_primary_text(record)
                if not text.strip():
                    print(f"[跳过] 第 {line_no} 行 text 为空: {record.get('title', '?')}")
                    skipped += 1
                    continue

                documents.append(flatten_record(record))

    # 4) 写入 JSON 数组文件
    output_path = input_dir / output_filename

    with open(output_path, "w", encoding="utf-8") as f:
        json.dump(documents, f, ensure_ascii=False, indent=2)

    # 5) 清理临时目录
    shutil.rmtree(tmp_dir, ignore_errors=True)

    action = "准备" if source_format == "json" else "转换"
    print(f"\n[完成] 共{action} {len(documents)} 条文档 (跳过 {skipped} 条空文档)")
    print(f"  → 输出文件: {output_path}")
    print(f"  → 文件大小: {output_path.stat().st_size:,} bytes")

    # 打印样例
    if documents:
        sample = documents[0]
        sample_keys = list(sample.keys())
        print(f"  → 字段: {sample_keys}")
        print(f"  → 第一条标题: {sample.get('title', '?')}")

    return {
        "status": "success",
        "course_id": course_id,
        "documents_count": len(documents),
        "skipped_count": skipped,
        "output_file": str(output_path),
        "input_dir": str(input_dir),
    }


# ===================== CLI =====================

def main() -> int:
    parser = argparse.ArgumentParser(
        description="从 MinIO 获取 pdf_ingest 导出的 GraphRAG 输入数据",
        formatter_class=argparse.RawDescriptionHelpFormatter,
        epilog="""
示例:
  python utils/fetch_from_minio.py os --clean
  python utils/fetch_from_minio.py os --input-dir ./my_input
  python utils/fetch_from_minio.py os --pdf-file-id 12
  python utils/fetch_from_minio.py os --json-file page_docs.json
  python utils/fetch_from_minio.py os --json-file page_docs.jsonl
        """,
    )

    parser.add_argument(
        "course_id",
        help="课程 ID (与 pdf_ingest 中使用的一致)",
    )
    parser.add_argument(
        "--input-dir",
        default="input",
        help="GraphRAG 输入目录 (默认: input)",
    )
    parser.add_argument(
        "--clean",
        action="store_true",
        help="清空输入目录中的旧 .json/.txt 文件",
    )
    parser.add_argument(
        "--pdf-file-id",
        type=int,
        default=None,
        help="指定课程下某一份 PDF 的 file_id；多 PDF 场景下建议显式传入",
    )
    parser.add_argument(
        "--json-file",
        "--jsonl-file",
        dest="json_filename",
        default="section_docs.json",
        help="输入文件名，默认 section_docs.json；也兼容历史 *.jsonl 文件",
    )
    parser.add_argument(
        "--env-file",
        default=None,
        help=".env 文件路径 (默认: 自动查找)",
    )

    args = parser.parse_args()

    # 加载 .env
    if args.env_file:
        load_dotenv(args.env_file)
    else:
        # 尝试从项目根目录加载
        project_root = Path(__file__).resolve().parent.parent
        env_path = project_root / ".env"
        if env_path.exists():
            load_dotenv(env_path)

    # 相对路径基于项目根目录
    input_dir = Path(args.input_dir)
    if not input_dir.is_absolute():
        project_root = Path(__file__).resolve().parent.parent
        input_dir = project_root / input_dir

    result = fetch_and_prepare(
        course_id=args.course_id,
        input_dir=input_dir,
        clean=args.clean,
        json_filename=args.json_filename,
        pdf_file_id=args.pdf_file_id,
    )

    if result["status"] in {"not_found", "ambiguous"}:
        return 1
    return 0


if __name__ == "__main__":
    sys.exit(main())
