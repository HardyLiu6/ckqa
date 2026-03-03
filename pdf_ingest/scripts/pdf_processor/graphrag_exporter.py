#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
GraphRAG 导出器
===============
从 MinerU 解析产物生成 GraphRAG 可 ingest 的 JSONL 输入文件。

流程:
    1. 从 DB 定位解析产物 (content_list.json + markdown)
    2. 按需从 MinIO 下载关键文件
    3. 解析 content_list.json → 统一 Block 列表
    4. 清洗去噪 (页眉页脚、空白、断行合并)
    5. 聚合为 documents (section / page 模式)
    6. 输出 JSONL + 上传 MinIO + 写入 DB
"""

from __future__ import annotations

import json
import logging
import re
import shutil
import tempfile
from dataclasses import dataclass, field
from pathlib import Path
from typing import Dict, Any, List, Optional, Tuple

from block_model import Block, BlockType, parse_content_list, load_content_list_file
from block_renderer import BlockRendererRegistry, RenderResult
from text_cleaner import clean_blocks
from db_service import DatabaseService, ResultType, ParseStatus
from storage_service import MinIOService

logger = logging.getLogger("GraphRAGExporter")


# ===================== 数据结构 =====================

@dataclass
class GraphRAGDocument:
    """GraphRAG 输入文档"""
    title: str
    text: str
    metadata: Dict[str, Any] = field(default_factory=dict)

    def to_jsonl_dict(self) -> Dict[str, Any]:
        return {
            "title": self.title,
            "text": self.text,
            "metadata": self.metadata,
        }


@dataclass
class ExportOptions:
    """导出选项"""
    mode: str = "section"             # section / page
    force: bool = False               # 强制覆盖已有导出
    semantic_table: bool = True       # 表格语义化 (列名=值 描述，利于实体抽取)
    with_page_docs: bool = False      # 同时生成 page 模式文档
    max_chars: Optional[int] = None   # 每个文档最大字符数 (0=不限)
    output_prefix: str = "graphrag"   # 输出前缀路径
    table_to_markdown: bool = True    # 表格 HTML → Markdown (否则纯文本)


# ===================== 核心导出器 =====================

class GraphRAGExporter:
    """GraphRAG 文档导出器"""

    GRAPHRAG_FILE_PREFIX = "graphrag_"  # DB file_name 前缀，用于幂等检测

    def __init__(self, db: DatabaseService, storage: MinIOService, config: Any):
        self.db = db
        self.storage = storage
        self.config = config
        self._renderer_registry: Optional[BlockRendererRegistry] = None

    # -------------------- 公开入口 --------------------

    def export(self, course_id: str, options: ExportOptions) -> Dict[str, Any]:
        """
        端到端导出流程。

        Returns:
            {status, course_id, documents_count, output_files: [...], minio_keys: [...]}
        """
        # 1) 定位 PDF 文件记录
        pdf_file = self.db.get_pdf_file_by_course(course_id)
        if not pdf_file:
            raise RuntimeError(f"课程 {course_id} 没有上传的 PDF 文件")

        file_id: int = pdf_file["id"]
        source_file: str = pdf_file["file_name"]  # book.pdf

        if pdf_file["parse_status"] != ParseStatus.DONE.value:
            raise RuntimeError(
                f"课程 {course_id} 解析未完成 (当前状态: {pdf_file['parse_status']})"
            )

        # 2) 幂等检查
        if not options.force:
            existing = self._find_existing_export(file_id, options)
            if existing:
                logger.info("已存在 GraphRAG 导出记录，跳过 (使用 --force 覆盖)")
                self.db.add_log(file_id, "GraphRAG导出跳过: 已存在导出记录")
                return {
                    "status": "exists",
                    "course_id": course_id,
                    "existing_records": existing,
                }

        self.db.add_log(file_id, "GraphRAG导出开始")
        logger.info(f"[{course_id}] GraphRAG 导出开始")

        # 3) 从 DB 获取解析产物列表
        parse_results = self.db.get_parse_results(file_id)

        # 4) 定位关键文件
        content_list_record = self._find_record(
            parse_results, ResultType.CONTENT_LIST_JSON, "content_list", ".json"
        )
        if not content_list_record:
            raise RuntimeError(
                f"课程 {course_id} 缺少 content_list.json 解析产物，无法导出"
            )

        markdown_record = self._find_record(
            parse_results, ResultType.MARKDOWN, None, ".md"
        )

        # 5) 按需下载到临时目录
        tmp_dir = Path(tempfile.mkdtemp(prefix=f"graphrag_{course_id}_"))
        try:
            content_list_path = self._download_record(
                course_id, content_list_record, tmp_dir
            )
            markdown_path = None
            if markdown_record:
                markdown_path = self._download_record(
                    course_id, markdown_record, tmp_dir
                )

            # 6) 解析 → Block
            content_list_data = load_content_list_file(content_list_path)
            blocks = parse_content_list(
                content_list_data,
                course_id=course_id,
                source_file=source_file,
                semantic_table=options.semantic_table,
            )
            raw_count = len(blocks)
            logger.info(f"[{course_id}] 解析得到 {raw_count} 个 blocks")

            # 7) 清洗
            blocks = clean_blocks(blocks)
            cleaned_count = len(blocks)
            logger.info(f"[{course_id}] 清洗后 {cleaned_count} 个 blocks")

            # 8) 读取 markdown (可选，用于增强标题层级)
            md_text = None
            if markdown_path and markdown_path.exists():
                md_text = markdown_path.read_text(encoding="utf-8")

            # 9) 聚合生成文档
            output_files: List[Dict[str, Any]] = []
            all_doc_count = 0

            # content_list minio 溯源信息
            cl_trace = {
                "content_list_file_name": content_list_record["file_name"],
                "content_list_minio_key": content_list_record["minio_object_key"],
            }

            modes_to_run = []
            if options.mode == "section":
                modes_to_run.append("section")
            elif options.mode == "page":
                modes_to_run.append("page")
            else:
                modes_to_run.append("section")

            if options.with_page_docs and "page" not in modes_to_run:
                modes_to_run.append("page")

            for m in modes_to_run:
                if m == "section":
                    docs = self._aggregate_section(
                        blocks, course_id, file_id, source_file,
                        md_text, cl_trace, options,
                    )
                    out_name = "section_docs.jsonl"
                else:
                    docs = self._aggregate_page(
                        blocks, course_id, file_id, source_file,
                        cl_trace, options,
                    )
                    out_name = "page_docs.jsonl"

                # 写 JSONL
                out_path = tmp_dir / out_name
                self._write_jsonl(docs, out_path)
                all_doc_count += len(docs)

                # 上传 MinIO
                relative_path = f"{options.output_prefix}/{out_name}"
                upload_result = self.storage.upload_artifact(
                    course_id, str(out_path), relative_path,
                )

                # 删除旧 DB 记录 (force 模式)
                if options.force:
                    self._delete_existing_records(file_id, out_name)

                # 写 DB 记录
                db_file_name = f"{self.GRAPHRAG_FILE_PREFIX}{out_name}"
                result_id = self.db.create_parse_result(
                    pdf_file_id=file_id,
                    course_id=course_id,
                    result_type=ResultType.OTHER,
                    file_name=db_file_name,
                    minio_bucket=upload_result["bucket"],
                    minio_object_key=upload_result["object_key"],
                    file_size=upload_result["size"],
                )

                output_files.append({
                    "mode": m,
                    "file_name": db_file_name,
                    "minio_object_key": upload_result["object_key"],
                    "doc_count": len(docs),
                    "file_size": upload_result["size"],
                    "result_id": result_id,
                })

            # 10) 日志
            summary = (
                f"GraphRAG导出完成: 原始块数={raw_count}, 清洗后={cleaned_count}, "
                f"文档数={all_doc_count}, 输出文件={len(output_files)}"
            )
            self.db.add_log(file_id, summary)
            logger.info(f"[{course_id}] {summary}")

            return {
                "status": "success",
                "course_id": course_id,
                "pdf_file_id": file_id,
                "raw_blocks": raw_count,
                "cleaned_blocks": cleaned_count,
                "documents_count": all_doc_count,
                "output_files": output_files,
            }

        except Exception as e:
            self.db.add_log(file_id, f"GraphRAG导出失败: {e}", level="error")
            logger.error(f"[{course_id}] GraphRAG导出失败: {e}")
            raise
        finally:
            shutil.rmtree(tmp_dir, ignore_errors=True)

    # -------------------- 文件定位 --------------------

    @staticmethod
    def _find_record(
        records: List[Dict],
        result_type: ResultType,
        name_keyword: Optional[str],
        suffix: Optional[str],
    ) -> Optional[Dict]:
        """
        从 parse_results 中查找匹配记录。
        策略: 先按 result_type 精确匹配；若无则按文件名模糊匹配。
        """
        # 精确匹配
        for r in records:
            if r["result_type"] == result_type.value:
                return r

        # 模糊 fallback
        if name_keyword or suffix:
            for r in records:
                fn = (r.get("file_name") or "").lower()
                match_kw = (not name_keyword) or (name_keyword.lower() in fn)
                match_sf = (not suffix) or fn.endswith(suffix.lower())
                if match_kw and match_sf:
                    return r

        return None

    def _download_record(
        self, course_id: str, record: Dict, tmp_dir: Path
    ) -> Path:
        """按需从 MinIO 下载单个文件，返回本地路径。"""
        minio_key: str = record["minio_object_key"]
        # 从 object_key 还原 relative_path: 去掉 "{course_id}/" 前缀
        prefix = f"{course_id}/"
        if minio_key.startswith(prefix):
            relative_path = minio_key[len(prefix):]
        else:
            relative_path = minio_key

        local_path = tmp_dir / record["file_name"]
        self.storage.download_artifact(course_id, relative_path, str(local_path))
        return local_path

    # -------------------- 幂等 --------------------

    def _find_existing_export(
        self, file_id: int, options: ExportOptions
    ) -> Optional[List[Dict]]:
        """查找已有的 GraphRAG 导出记录。"""
        results = self.db.get_parse_results(file_id)
        existing = [
            {
                "file_name": r["file_name"],
                "minio_object_key": r["minio_object_key"],
                "file_size": r["file_size"],
            }
            for r in results
            if (r.get("file_name") or "").startswith(self.GRAPHRAG_FILE_PREFIX)
        ]
        return existing if existing else None

    def _delete_existing_records(self, file_id: int, out_name: str):
        """删除指定输出名的旧 GraphRAG 记录。"""
        target_name = f"{self.GRAPHRAG_FILE_PREFIX}{out_name}"
        results = self.db.get_parse_results(file_id)
        for r in results:
            if r.get("file_name") == target_name:
                with self.db.get_connection() as conn:
                    cursor = conn.cursor()
                    cursor.execute(
                        "DELETE FROM parse_results WHERE id = %s", (r["id"],)
                    )

    # -------------------- Section 聚合 --------------------

    # 标题编号 → 层级推断正则
    _TITLE_LEVEL_PATTERNS = [
        # 第X章 → level 1
        (re.compile(r"^第[一二三四五六七八九十百千\d]+章"), 1),
        # 第X节/篇 → level 2
        (re.compile(r"^第[一二三四五六七八九十百千\d]+[节篇]"), 2),
        # X.Y.Z → level 3
        (re.compile(r"^\d+\.\d+\.\d+"), 3),
        # X.Y → level 2
        (re.compile(r"^\d+\.\d+"), 2),
    ]

    # 章节边界模式: 只有匹配这些模式的 TITLE 块才会切分新章节
    # 编号列表项 ("1. 方便性", "2. 有效性") 不是章节边界
    _SECTION_BOUNDARY_RE = re.compile(
        r"^("
        r"第[一二三四五六七八九十百千\d]+[章节篇]"  # 第X章/节/篇
        r"|\d+\.\d+"                                  # X.Y 或 X.Y.Z (节/小节编号)
        r")"
    )

    @staticmethod
    def _parse_md_headings(md_text: str) -> Dict[str, int]:
        """
        从 markdown 文本解析标题行，返回 {归一化标题文本: 层级} 映射。
        例如 '# 第一章 操作系统引论' → {'第一章操作系统引论': 1}
             '## 1.1 概述' → {'1.1概述': 2}
        """
        heading_re = re.compile(r"^(#{1,6})\s+(.+)$", re.MULTILINE)
        result: Dict[str, int] = {}
        for m in heading_re.finditer(md_text):
            level = len(m.group(1))
            title_text = m.group(2).strip()
            norm = re.sub(r"\s+", "", title_text.lower())
            if norm:
                result[norm] = level
        return result

    def _infer_title_level(
        self, title_text: str, md_headings: Optional[Dict[str, int]]
    ) -> int:
        """
        推断标题层级:
        1. 优先用标题编号模式推断
        2. 若模式未匹配，用 markdown 标题映射
        3. 默认: level 1
        """
        text = title_text.strip()

        for pattern, level in self._TITLE_LEVEL_PATTERNS:
            if pattern.match(text):
                return level

        if md_headings:
            norm = re.sub(r"\s+", "", text.lower())
            md_level = md_headings.get(norm)
            if md_level is not None:
                return md_level

        return 1

    def _is_section_boundary(self, block: Block) -> bool:
        """
        判断一个 TITLE 块是否构成章节边界。

        只有章/节/小节编号标题才是边界:
          - "第一章 操作系统引论" → True
          - "1.1 操作系统的目标和作用" → True
          - "1.1.1 操作系统的目标" → True
        编号列表项不是边界:
          - "1. 方便性" → False
          - "2. 有效性" → False
        """
        text = block.text.strip()
        if not text:
            return False
        return bool(self._SECTION_BOUNDARY_RE.match(text))

    def _aggregate_section(
        self,
        blocks: List[Block],
        course_id: str,
        file_id: int,
        source_file: str,
        md_text: Optional[str],
        cl_trace: Dict[str, str],
        options: ExportOptions,
    ) -> List[GraphRAGDocument]:
        """
        章节模式聚合:
        1. 从 TITLE 块中筛选出真正的章节边界 (章/节编号标题)
        2. 利用 markdown 标题增强 section_level
        3. 编号列表项 ("1. 方便性") 保留为章节内正文
        4. 每个章节 → 一个 GraphRAGDocument
        """
        md_headings: Optional[Dict[str, int]] = None
        if md_text:
            md_headings = self._parse_md_headings(md_text)

        # 从 TITLE 块中筛选真正的章节边界
        title_indices = [
            i for i, b in enumerate(blocks)
            if b.block_type == BlockType.TITLE and self._is_section_boundary(b)
        ]

        if not title_indices:
            # fallback: 启发式识别
            title_indices = self._heuristic_title_indices(blocks)

        if not title_indices:
            return [self._blocks_to_doc(
                blocks, course_id, file_id, source_file,
                section_title="全文", section_level=1,
                doc_unit="section", cl_trace=cl_trace,
                options=options,
            )]

        # 按标题切分
        sections: List[Tuple[int, int]] = []
        for i, ti in enumerate(title_indices):
            end = title_indices[i + 1] if i + 1 < len(title_indices) else len(blocks)
            sections.append((ti, end))

        # 标题之前的内容
        docs: List[GraphRAGDocument] = []
        if title_indices[0] > 0:
            pre_blocks = blocks[:title_indices[0]]
            docs.append(self._blocks_to_doc(
                pre_blocks, course_id, file_id, source_file,
                section_title="前言", section_level=0,
                doc_unit="section", cl_trace=cl_trace,
                options=options,
            ))

        for start, end in sections:
            sec_blocks = blocks[start:end]
            title_block = blocks[start]
            section_title = title_block.text.strip() or "未命名章节"
            section_level = self._infer_title_level(section_title, md_headings)

            doc = self._blocks_to_doc(
                sec_blocks, course_id, file_id, source_file,
                section_title=section_title,
                section_level=section_level,
                doc_unit="section", cl_trace=cl_trace,
                options=options,
            )

            if options.max_chars and len(doc.text) > options.max_chars:
                sub_docs = self._split_doc_by_chars(doc, options.max_chars)
                docs.extend(sub_docs)
            else:
                docs.append(doc)

        return docs

    def _heuristic_title_indices(self, blocks: List[Block]) -> List[int]:
        """
        启发式标题识别 (仅在 blocks 中无 TITLE 类型时 fallback 使用)。
        只识别章/节编号模式，不做过于激进的匹配。

        注意: 本方法不修改 blocks，仅返回候选标题的索引列表。
        """
        indices = []
        for i, b in enumerate(blocks):
            if b.block_type != BlockType.TEXT:
                continue
            text = b.text.strip()
            if not text or len(text) > 80:
                continue
            if self._SECTION_BOUNDARY_RE.match(text):
                indices.append(i)

        return indices

    # -------------------- Page 聚合 --------------------

    def _aggregate_page(
        self,
        blocks: List[Block],
        course_id: str,
        file_id: int,
        source_file: str,
        cl_trace: Dict[str, str],
        options: ExportOptions,
    ) -> List[GraphRAGDocument]:
        """
        页面模式聚合: 每页 → 一个文档
        """
        page_groups: Dict[Optional[int], List[Block]] = {}
        for b in blocks:
            page_groups.setdefault(b.page, []).append(b)

        docs: List[GraphRAGDocument] = []
        for page_no in sorted(page_groups.keys(), key=lambda x: x if x is not None else -1):
            page_blocks = page_groups[page_no]
            page_label = page_no if page_no is not None else 0

            doc = self._blocks_to_doc(
                page_blocks, course_id, file_id, source_file,
                section_title=f"page-{page_label}",
                section_level=None,
                doc_unit="page", cl_trace=cl_trace,
                page_no=page_label,
                options=options,
            )
            docs.append(doc)

        return docs

    # -------------------- Block → Document --------------------

    def _get_renderer_registry(self, options: ExportOptions) -> BlockRendererRegistry:
        """按 ExportOptions 懒创建/缓存 BlockRendererRegistry。"""
        if self._renderer_registry is None:
            self._renderer_registry = BlockRendererRegistry.create_default(
                prefer_markdown=options.table_to_markdown,
            )
        return self._renderer_registry

    def _blocks_to_doc(
        self,
        blocks: List[Block],
        course_id: str,
        file_id: int,
        source_file: str,
        section_title: str,
        section_level: Optional[int],
        doc_unit: str,
        cl_trace: Dict[str, str],
        page_no: Optional[int] = None,
        options: Optional[ExportOptions] = None,
    ) -> GraphRAGDocument:
        """
        将一组 blocks 合并为单个 GraphRAGDocument。

        使用 BlockRendererRegistry 逐 block 渲染:
          - 每种 block_type 由对应 Renderer 输出文本 + metadata
          - TABLE/IMAGE 的结构化信息收集到 doc.metadata
          - 未知 block_type 由 FallbackRenderer 兜底, 永不丢失
        """
        if options is None:
            options = ExportOptions()
        registry = self._get_renderer_registry(options)

        text_parts: List[str] = []
        block_ids: List[str] = []
        image_refs: List[str] = []
        tables_meta: List[Dict[str, Any]] = []
        images_meta: List[Dict[str, Any]] = []
        pages: List[int] = []

        for b in blocks:
            result: RenderResult = registry.render(b)

            # 渲染文本 (非空即加入)
            if result.text:
                text_parts.append(result.text)
            else:
                logger.debug("block %s 渲染结果为空, 跳过文本部分", b.block_id)

            block_ids.append(b.block_id)

            if b.page is not None:
                pages.append(b.page)

            # 收集资产引用 (向后兼容)
            if b.block_type == BlockType.IMAGE and b.asset_ref:
                image_refs.append(b.asset_ref)

            # 收集结构化元数据
            if result.metadata:
                meta_type = result.metadata.get("type")
                if meta_type == "table":
                    tables_meta.append(result.metadata)
                elif meta_type == "image":
                    images_meta.append(result.metadata)

        text = "\n\n".join(text_parts)
        title = f"{source_file}-{section_title}"

        metadata: Dict[str, Any] = {
            "course_id": course_id,
            "pdf_file_id": file_id,
            "source_file": source_file,
            "doc_unit": doc_unit,
            "section_title": section_title,
            "block_ids": block_ids,
            "mineru_artifacts": cl_trace,
        }

        if section_level is not None:
            metadata["section_level"] = section_level

        if pages:
            metadata["page_start"] = min(pages)
            metadata["page_end"] = max(pages)

        if page_no is not None:
            metadata["page_no"] = page_no

        if image_refs:
            metadata["image_refs"] = image_refs

        if tables_meta:
            metadata["tables"] = tables_meta

        if images_meta:
            metadata["images"] = images_meta

        return GraphRAGDocument(title=title, text=text, metadata=metadata)

    # -------------------- 长文档拆分 --------------------

    @staticmethod
    def _split_doc_by_chars(
        doc: GraphRAGDocument, max_chars: int
    ) -> List[GraphRAGDocument]:
        """按 max_chars 拆分长文档，按段落边界切割。"""
        paragraphs = doc.text.split("\n\n")
        chunks: List[str] = []
        current: List[str] = []
        current_len = 0

        for para in paragraphs:
            para_len = len(para) + 2  # \n\n
            if current and current_len + para_len > max_chars:
                chunks.append("\n\n".join(current))
                current = [para]
                current_len = para_len
            else:
                current.append(para)
                current_len += para_len

        if current:
            chunks.append("\n\n".join(current))

        if len(chunks) <= 1:
            return [doc]

        docs = []
        for i, chunk in enumerate(chunks):
            sub_meta = dict(doc.metadata)
            sub_meta["chunk_index"] = i
            sub_meta["chunk_total"] = len(chunks)
            docs.append(GraphRAGDocument(
                title=f"{doc.title}-part{i + 1}",
                text=chunk,
                metadata=sub_meta,
            ))
        return docs

    # -------------------- JSONL 输出 --------------------

    @staticmethod
    def _write_jsonl(docs: List[GraphRAGDocument], path: Path):
        """将文档列表写入 JSONL 文件。"""
        path.parent.mkdir(parents=True, exist_ok=True)
        with open(path, "w", encoding="utf-8") as f:
            for doc in docs:
                line = json.dumps(doc.to_jsonl_dict(), ensure_ascii=False)
                f.write(line + "\n")
