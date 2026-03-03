#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
从 MinIO 获取 GraphRAG 输入数据
================================
从 MinIO 下载 pdf_ingest 生成的 section_docs.jsonl，
将 JSONL 转换为 JSON 数组格式（.json）供 GraphRAG 索引使用。

GraphRAG 不支持 JSONL 格式，但支持 JSON 数组格式。
本脚本同时会将嵌套的 metadata 字段展平为顶层字段，
使 GraphRAG 的 metadata 收集功能可以直接读取 page_start、
page_end、section_level 等信息。

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
    "course_id",
    "source_file",
    "section_level",
    "page_start",
    "page_end",
]


def flatten_record(record: dict) -> dict:
    """
    将 JSONL 记录转换为 GraphRAG 兼容的扁平 JSON 对象。

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
    flat = {
        "title": record.get("title", ""),
        "text": record.get("text", ""),
    }

    metadata = record.get("metadata", {})
    for field in _METADATA_FIELDS_TO_FLATTEN:
        if field in metadata:
            flat[field] = metadata[field]

    return flat


# ===================== 核心逻辑 =====================

def fetch_and_convert(
    course_id: str,
    input_dir: Path,
    clean: bool = False,
    graphrag_prefix: str = "graphrag",
    jsonl_filename: str = "section_docs.jsonl",
) -> dict:
    """
    从 MinIO 下载 section_docs.jsonl 并转换为 JSON 数组文件。

    Args:
        course_id: 课程 ID
        input_dir: GraphRAG input 目录
        clean: 是否清空旧的 .json 输入文件
        graphrag_prefix: MinIO 中的前缀路径
        jsonl_filename: JSONL 文件名

    Returns:
        {status, course_id, documents_count, output_file, input_dir}
    """
    client = get_minio_client()
    bucket = get_bucket()

    # MinIO 对象路径: {course_id}/graphrag/section_docs.jsonl
    object_key = f"{course_id}/{graphrag_prefix}/{jsonl_filename}"

    print(f"[MinIO] 正在下载 {bucket}/{object_key} ...")

    # 1) 下载 JSONL 到临时文件
    tmp_dir = Path(tempfile.mkdtemp(prefix=f"graphrag_fetch_{course_id}_"))
    tmp_jsonl = tmp_dir / jsonl_filename

    try:
        client.fget_object(
            bucket_name=bucket,
            object_name=object_key,
            file_path=str(tmp_jsonl),
        )
    except S3Error as e:
        shutil.rmtree(tmp_dir, ignore_errors=True)
        if e.code == "NoSuchKey":
            print(
                f"[错误] MinIO 中未找到 {object_key}。"
                f"请先在 pdf_ingest 中运行 export-graphrag 命令。",
                file=sys.stderr,
            )
            return {"status": "not_found", "course_id": course_id}
        raise

    print(f"[MinIO] 下载完成: {tmp_jsonl} ({tmp_jsonl.stat().st_size} bytes)")

    # 2) 准备 input 目录
    input_dir.mkdir(parents=True, exist_ok=True)

    if clean:
        # 删除旧的 .json 和 .txt 文件（兼容旧版本生成的 .txt）
        old_files = list(input_dir.glob("*.json")) + list(input_dir.glob("*.txt"))
        for f in old_files:
            f.unlink()
        print(f"[清理] 已删除 {len(old_files)} 个旧输入文件")

    # 3) 解析 JSONL → JSON 数组
    documents = []
    skipped = 0

    with open(tmp_jsonl, "r", encoding="utf-8") as f:
        for line_no, line in enumerate(f, start=1):
            line = line.strip()
            if not line:
                continue

            try:
                record = json.loads(line)
            except json.JSONDecodeError as e:
                print(f"[警告] 第 {line_no} 行 JSON 解析失败: {e}", file=sys.stderr)
                continue

            text = record.get("text", "")
            if not text.strip():
                print(f"[跳过] 第 {line_no} 行 text 为空: {record.get('title', '?')}")
                skipped += 1
                continue

            flat = flatten_record(record)
            documents.append(flat)

    # 4) 写入 JSON 数组文件
    # 输出文件名: section_docs.jsonl → section_docs.json
    output_filename = jsonl_filename.replace(".jsonl", ".json")
    output_path = input_dir / output_filename

    with open(output_path, "w", encoding="utf-8") as f:
        json.dump(documents, f, ensure_ascii=False, indent=2)

    # 5) 清理临时目录
    shutil.rmtree(tmp_dir, ignore_errors=True)

    print(f"\n[完成] 共转换 {len(documents)} 条文档 (跳过 {skipped} 条空文档)")
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
  python utils/fetch_from_minio.py os --jsonl-file page_docs.jsonl
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
        "--jsonl-file",
        default="section_docs.jsonl",
        help="JSONL 文件名 (默认: section_docs.jsonl, 也可用 page_docs.jsonl)",
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

    result = fetch_and_convert(
        course_id=args.course_id,
        input_dir=input_dir,
        clean=args.clean,
        jsonl_filename=args.jsonl_file,
    )

    if result["status"] == "not_found":
        return 1
    return 0


if __name__ == "__main__":
    sys.exit(main())
