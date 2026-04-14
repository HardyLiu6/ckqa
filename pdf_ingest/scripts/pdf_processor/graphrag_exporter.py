#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
GraphRAG 导出器
===============
从 MinerU 解析产物生成两类输出：

1. 标准课程文档 `normalized_docs.json`
2. GraphRAG 可 ingest 的投影 JSON（如 `section_docs.json` / `page_docs.json`）

当前阶段的目标是先统一“标准文档承载层”和 metadata 保留策略，
在不打断现有 GraphRAG 消费链路的前提下，为后续标题切分、去噪、
表格/公式/图片规则收敛提供稳定中间层。
"""

from __future__ import annotations

import json
import logging
import re
import shutil
import tempfile
from dataclasses import dataclass, field
from pathlib import Path
from typing import Any, Callable, Dict, List, Optional, Tuple

from block_model import Block, BlockType, load_content_list_file, parse_content_list
from block_renderer import BlockRendererRegistry, RenderResult
from normalized_document import (
    DOCUMENT_SCHEMA_VERSION,
    DocumentType,
    NormalizedDocument,
)
from text_cleaner import clean_blocks
from db_service import DatabaseService, ParseStatus, ResultType
from storage_service import MinIOService

logger = logging.getLogger("GraphRAGExporter")


# GraphRAG 会从顶层字段读取 metadata，因此需要将部分 metadata 展平。
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
]


# ===================== 数据结构 =====================


@dataclass
class GraphRAGDocument:
    """GraphRAG 输入文档。"""

    title: str
    text: str
    metadata: Dict[str, Any] = field(default_factory=dict)

    def to_graphrag_dict(self) -> Dict[str, Any]:
        """序列化为 GraphRAG 兼容记录，同时保留嵌套 metadata。"""
        record = {
            "title": self.title,
            "text": self.text,
            "metadata": self.metadata,
        }
        for field in _METADATA_FIELDS_TO_FLATTEN:
            if field in self.metadata:
                record[field] = self.metadata[field]
        return record


@dataclass
class RenderedBlockGroup:
    """一组 blocks 渲染后的聚合结果。"""

    text: str
    block_ids: List[str] = field(default_factory=list)
    image_refs: List[str] = field(default_factory=list)
    tables_meta: List[Dict[str, Any]] = field(default_factory=list)
    images_meta: List[Dict[str, Any]] = field(default_factory=list)
    pages: List[int] = field(default_factory=list)
    equation_count: int = 0


@dataclass
class ExportOptions:
    """导出选项。"""

    mode: str = "section"             # section / page
    force: bool = False               # 强制覆盖已有导出
    semantic_table: bool = True       # 表格语义化 (列名=值 描述，利于实体抽取)
    with_page_docs: bool = False      # 同时生成 page 模式文档
    max_chars: Optional[int] = None   # 每个文档最大字符数 (0=不限)
    output_prefix: str = "graphrag"   # 输出前缀路径
    table_to_markdown: bool = True    # 表格 HTML → Markdown (否则纯文本)


# ===================== 核心导出器 =====================


class GraphRAGExporter:
    """GraphRAG 文档导出器。"""

    GRAPHRAG_FILE_PREFIX = "graphrag_"  # DB file_name 前缀，用于幂等检测
    NORMALIZED_OUTPUT_NAME = "normalized_docs.json"

    # 标题编号 → 层级推断正则
    _TITLE_LEVEL_PATTERNS = [
        (re.compile(r"^第[一二三四五六七八九十百千\d]+章"), 1),
        (re.compile(r"^第[一二三四五六七八九十百千\d]+[节篇]"), 2),
        (re.compile(r"^\d+\.\d+\.\d+"), 3),
        (re.compile(r"^\d+\.\d+"), 2),
    ]

    # 章节边界模式：只有章/节/小节编号标题才会切分新章节
    _SECTION_BOUNDARY_RE = re.compile(
        r"^("
        r"第[一二三四五六七八九十百千\d]+[章节篇]"
        r"|\d+\.\d+"
        r")"
    )

    def __init__(self, db: DatabaseService, storage: MinIOService, config: Any):
        self.db = db
        self.storage = storage
        self.config = config
        self._renderer_registry: Optional[BlockRendererRegistry] = None

    # -------------------- 公开入口 --------------------

    def export(self, pdf_file: Dict[str, Any], options: ExportOptions) -> Dict[str, Any]:
        """
        端到端导出流程。

        Returns:
            {
              status,
              course_id,
              pdf_file_id,
              raw_blocks,
              cleaned_blocks,
              documents_count,
              normalized_documents_count,
              output_files,
            }
        """
        if self.db is None or self.storage is None:
            raise RuntimeError("GraphRAG 导出依赖 db 与 storage 服务")

        course_id: str = pdf_file["course_id"]
        file_id: int = pdf_file["id"]
        source_file: str = pdf_file["file_name"]

        if pdf_file["parse_status"] != ParseStatus.DONE.value:
            raise RuntimeError(
                f"课程 {course_id} 解析未完成 (当前状态: {pdf_file['parse_status']})"
            )

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
        logger.info("[%s] GraphRAG 导出开始", course_id)

        parse_results = self.db.get_parse_results(file_id)

        content_list_record = self._find_record(
            parse_results,
            ResultType.CONTENT_LIST_JSON,
            "content_list",
            ".json",
        )
        if not content_list_record:
            raise RuntimeError(f"课程 {course_id} 缺少 content_list.json 解析产物，无法导出")

        markdown_record = self._find_record(
            parse_results,
            ResultType.MARKDOWN,
            None,
            ".md",
        )

        tmp_dir = Path(tempfile.mkdtemp(prefix=f"graphrag_{course_id}_"))
        try:
            content_list_path = self._download_record(course_id, content_list_record, tmp_dir)
            markdown_path: Optional[Path] = None
            if markdown_record:
                markdown_path = self._download_record(course_id, markdown_record, tmp_dir)

            content_list_data = load_content_list_file(content_list_path)
            blocks = parse_content_list(
                content_list_data,
                course_id=course_id,
                source_file=source_file,
                semantic_table=options.semantic_table,
            )
            raw_count = len(blocks)
            logger.info("[%s] 解析得到 %s 个 blocks", course_id, raw_count)

            blocks = clean_blocks(blocks)
            cleaned_count = len(blocks)
            logger.info("[%s] 清洗后 %s 个 blocks", course_id, cleaned_count)

            md_text = None
            if markdown_path and markdown_path.exists():
                md_text = markdown_path.read_text(encoding="utf-8")

            cl_trace = {
                "content_list_file_name": content_list_record["file_name"],
                "content_list_minio_key": content_list_record["minio_object_key"],
            }

            output_files: List[Dict[str, Any]] = []

            normalized_docs = self._aggregate_normalized_section(
                blocks=blocks,
                course_id=course_id,
                file_id=file_id,
                source_file=source_file,
                md_text=md_text,
                cl_trace=cl_trace,
                options=options,
            )
            normalized_count = len(normalized_docs)

            self._persist_output_file(
                docs=normalized_docs,
                serializer=self._write_normalized_json,
                tmp_dir=tmp_dir,
                course_id=course_id,
                file_id=file_id,
                out_name=self.NORMALIZED_OUTPUT_NAME,
                output_files=output_files,
                mode="normalized",
                force=options.force,
                output_prefix=options.output_prefix,
            )

            modes_to_run = self._build_modes_to_run(options)
            graphrag_doc_count = 0

            for mode in modes_to_run:
                if mode == "section":
                    docs = self._project_normalized_documents(normalized_docs)
                    out_name = "section_docs.json"
                else:
                    page_docs = self._aggregate_normalized_page(
                        blocks=blocks,
                        course_id=course_id,
                        file_id=file_id,
                        source_file=source_file,
                        cl_trace=cl_trace,
                        options=options,
                    )
                    docs = self._project_normalized_documents(page_docs)
                    out_name = "page_docs.json"

                graphrag_doc_count += len(docs)
                self._persist_output_file(
                    docs=docs,
                    serializer=self._write_json,
                    tmp_dir=tmp_dir,
                    course_id=course_id,
                    file_id=file_id,
                    out_name=out_name,
                    output_files=output_files,
                    mode=mode,
                    force=options.force,
                    output_prefix=options.output_prefix,
                )

            summary = (
                f"GraphRAG导出完成: 原始块数={raw_count}, 清洗后={cleaned_count}, "
                f"标准文档数={normalized_count}, GraphRAG文档数={graphrag_doc_count}, "
                f"输出文件={len(output_files)}"
            )
            self.db.add_log(file_id, summary)
            logger.info("[%s] %s", course_id, summary)

            return {
                "status": "success",
                "course_id": course_id,
                "pdf_file_id": file_id,
                "raw_blocks": raw_count,
                "cleaned_blocks": cleaned_count,
                "documents_count": graphrag_doc_count,
                "normalized_documents_count": normalized_count,
                "output_files": output_files,
            }

        except Exception as e:
            self.db.add_log(file_id, f"GraphRAG导出失败: {e}", level="error")
            logger.error("[%s] GraphRAG导出失败: %s", course_id, e)
            raise
        finally:
            shutil.rmtree(tmp_dir, ignore_errors=True)

    # -------------------- 文件定位 --------------------

    @staticmethod
    def _find_record(
        records: List[Dict[str, Any]],
        result_type: ResultType,
        name_keyword: Optional[str],
        suffix: Optional[str],
    ) -> Optional[Dict[str, Any]]:
        """
        从 parse_results 中查找匹配记录。
        策略：先按 result_type 精确匹配；若无则按文件名模糊匹配。
        """
        for record in records:
            if record["result_type"] == result_type.value:
                return record

        if name_keyword or suffix:
            for record in records:
                file_name = (record.get("file_name") or "").lower()
                match_kw = (not name_keyword) or (name_keyword.lower() in file_name)
                match_suffix = (not suffix) or file_name.endswith(suffix.lower())
                if match_kw and match_suffix:
                    return record

        return None

    def _download_record(
        self,
        course_id: str,
        record: Dict[str, Any],
        tmp_dir: Path,
    ) -> Path:
        """按需从 MinIO 下载单个文件，返回本地路径。"""
        minio_key: str = record["minio_object_key"]
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
        self,
        file_id: int,
        options: ExportOptions,
    ) -> Optional[List[Dict[str, Any]]]:
        """查找是否已存在完整导出记录。"""
        if self.db is None:
            return None

        results = self.db.get_parse_results(file_id)
        existing = {
            result["file_name"]: {
                "file_name": result["file_name"],
                "minio_object_key": result["minio_object_key"],
                "file_size": result["file_size"],
            }
            for result in results
            if (result.get("file_name") or "").startswith(self.GRAPHRAG_FILE_PREFIX)
        }

        required_names = [
            f"{self.GRAPHRAG_FILE_PREFIX}{name}"
            for name in self._expected_output_names(options)
        ]

        if not all(name in existing for name in required_names):
            return None

        return [existing[name] for name in required_names]

    def _expected_output_names(self, options: ExportOptions) -> List[str]:
        """根据导出选项计算期望输出文件集合。"""
        names = [self.NORMALIZED_OUTPUT_NAME]

        if options.mode == "page":
            names.append("page_docs.json")
        else:
            names.append("section_docs.json")

        if options.with_page_docs and "page_docs.json" not in names:
            names.append("page_docs.json")

        return names

    def _delete_existing_records(self, file_id: int, out_name: str) -> None:
        """删除指定输出名的旧 GraphRAG 记录。"""
        if self.db is None:
            return

        target_name = f"{self.GRAPHRAG_FILE_PREFIX}{out_name}"
        results = self.db.get_parse_results(file_id)
        for record in results:
            if record.get("file_name") != target_name:
                continue
            with self.db.get_connection() as conn:
                cursor = conn.cursor()
                cursor.execute("DELETE FROM parse_results WHERE id = %s", (record["id"],))

    def _persist_output_file(
        self,
        docs: List[Any],
        serializer: Callable[[List[Any], Path], None],
        tmp_dir: Path,
        course_id: str,
        file_id: int,
        out_name: str,
        output_files: List[Dict[str, Any]],
        mode: str,
        force: bool,
        output_prefix: str,
    ) -> None:
        """写本地文件、上传 MinIO 并登记 DB 记录。"""
        out_path = tmp_dir / out_name
        serializer(docs, out_path)

        relative_path = f"{output_prefix}/pdf_{file_id}/{out_name}"
        upload_result = self.storage.upload_artifact(course_id, str(out_path), relative_path)

        if force:
            self._delete_existing_records(file_id, out_name)

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
            "mode": mode,
            "file_name": db_file_name,
            "minio_object_key": upload_result["object_key"],
            "doc_count": len(docs),
            "file_size": upload_result["size"],
            "result_id": result_id,
        })

    @staticmethod
    def _build_modes_to_run(options: ExportOptions) -> List[str]:
        """计算本次需要生成的 GraphRAG 投影模式。"""
        modes = ["page"] if options.mode == "page" else ["section"]
        if options.with_page_docs and "page" not in modes:
            modes.append("page")
        return modes

    # -------------------- 标题辅助 --------------------

    @staticmethod
    def _parse_md_headings(md_text: str) -> Dict[str, int]:
        """
        从 markdown 文本解析标题行，返回 {归一化标题文本: 层级} 映射。
        """
        heading_re = re.compile(r"^(#{1,6})\s+(.+)$", re.MULTILINE)
        result: Dict[str, int] = {}
        for match in heading_re.finditer(md_text):
            level = len(match.group(1))
            title_text = match.group(2).strip()
            normalized = re.sub(r"\s+", "", title_text.lower())
            if normalized:
                result[normalized] = level
        return result

    def _infer_title_level(
        self,
        title_text: str,
        md_headings: Optional[Dict[str, int]],
    ) -> int:
        """
        推断标题层级：
        1. 优先用标题编号模式推断
        2. 若模式未匹配，用 markdown 标题映射
        3. 默认 level=1
        """
        text = title_text.strip()

        for pattern, level in self._TITLE_LEVEL_PATTERNS:
            if pattern.match(text):
                return level

        if md_headings:
            normalized = re.sub(r"\s+", "", text.lower())
            md_level = md_headings.get(normalized)
            if md_level is not None:
                return md_level

        return 1

    def _is_section_boundary(self, block: Block) -> bool:
        """判断一个 TITLE 块是否构成章节边界。"""
        text = block.text.strip()
        if not text:
            return False
        return bool(self._SECTION_BOUNDARY_RE.match(text))

    def _heuristic_title_indices(self, blocks: List[Block]) -> List[int]:
        """
        启发式标题识别（仅在 blocks 中无 TITLE 类型时 fallback 使用）。
        只识别章/节编号模式，不做过于激进的匹配。
        """
        indices = []
        for index, block in enumerate(blocks):
            if block.block_type != BlockType.TEXT:
                continue
            text = block.text.strip()
            if not text or len(text) > 80:
                continue
            if self._SECTION_BOUNDARY_RE.match(text):
                indices.append(index)
        return indices

    # -------------------- 标准文档聚合 --------------------

    def _aggregate_normalized_section(
        self,
        blocks: List[Block],
        course_id: str,
        file_id: int,
        source_file: str,
        md_text: Optional[str],
        cl_trace: Dict[str, str],
        options: ExportOptions,
    ) -> List[NormalizedDocument]:
        """
        章节模式聚合为标准课程文档。

        当前阶段只统一“标准文档承载层”和 metadata 保留策略。
        目录页过滤、标题去页码等规则会在后续阶段继续收敛。
        """
        md_headings: Optional[Dict[str, int]] = None
        if md_text:
            md_headings = self._parse_md_headings(md_text)

        title_indices = [
            index for index, block in enumerate(blocks)
            if block.block_type == BlockType.TITLE and self._is_section_boundary(block)
        ]

        if not title_indices:
            title_indices = self._heuristic_title_indices(blocks)

        if not title_indices:
            doc = self._blocks_to_normalized_doc(
                blocks=blocks,
                course_id=course_id,
                file_id=file_id,
                source_file=source_file,
                heading_path=["全文"],
                doc_unit="section",
                cl_trace=cl_trace,
                options=options,
                source_section_level=1,
                drop_leading_title=False,
            )
            return self._split_normalized_doc_by_chars(doc, options.max_chars)

        sections: List[Tuple[int, int]] = []
        for index, title_index in enumerate(title_indices):
            end = title_indices[index + 1] if index + 1 < len(title_indices) else len(blocks)
            sections.append((title_index, end))

        docs: List[NormalizedDocument] = []

        if title_indices[0] > 0:
            pre_blocks = blocks[:title_indices[0]]
            pre_doc = self._blocks_to_normalized_doc(
                blocks=pre_blocks,
                course_id=course_id,
                file_id=file_id,
                source_file=source_file,
                heading_path=["前言"],
                doc_unit="section",
                cl_trace=cl_trace,
                options=options,
                source_section_level=0,
                drop_leading_title=False,
            )
            docs.extend(self._split_normalized_doc_by_chars(pre_doc, options.max_chars))

        heading_stack: List[str] = []
        for start, end in sections:
            sec_blocks = blocks[start:end]
            title_block = blocks[start]
            section_title = title_block.text.strip() or "未命名章节"
            source_section_level = self._infer_title_level(section_title, md_headings)

            keep_depth = max(source_section_level - 1, 0)
            heading_stack = heading_stack[:keep_depth]
            heading_stack.append(section_title)

            doc = self._blocks_to_normalized_doc(
                blocks=sec_blocks,
                course_id=course_id,
                file_id=file_id,
                source_file=source_file,
                heading_path=list(heading_stack),
                doc_unit="section",
                cl_trace=cl_trace,
                options=options,
                source_section_level=source_section_level,
                drop_leading_title=True,
            )
            docs.extend(self._split_normalized_doc_by_chars(doc, options.max_chars))

        return docs

    def _aggregate_normalized_page(
        self,
        blocks: List[Block],
        course_id: str,
        file_id: int,
        source_file: str,
        cl_trace: Dict[str, str],
        options: ExportOptions,
    ) -> List[NormalizedDocument]:
        """页面模式聚合为标准课程文档。"""
        page_groups: Dict[Optional[int], List[Block]] = {}
        for block in blocks:
            page_groups.setdefault(block.page, []).append(block)

        docs: List[NormalizedDocument] = []
        for raw_page_no in sorted(page_groups.keys(), key=lambda value: value if value is not None else -1):
            page_blocks = page_groups[raw_page_no]
            human_page_no = (raw_page_no + 1) if raw_page_no is not None else 1

            doc = self._blocks_to_normalized_doc(
                blocks=page_blocks,
                course_id=course_id,
                file_id=file_id,
                source_file=source_file,
                heading_path=[f"page-{human_page_no}"],
                doc_unit="page",
                cl_trace=cl_trace,
                options=options,
                source_section_level=None,
                page_no=human_page_no,
                drop_leading_title=False,
            )
            docs.extend(self._split_normalized_doc_by_chars(doc, options.max_chars))

        return docs

    # -------------------- GraphRAG 投影 --------------------

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
        """章节模式聚合：由标准文档投影生成 GraphRAG 文档。"""
        normalized_docs = self._aggregate_normalized_section(
            blocks=blocks,
            course_id=course_id,
            file_id=file_id,
            source_file=source_file,
            md_text=md_text,
            cl_trace=cl_trace,
            options=options,
        )
        return self._project_normalized_documents(normalized_docs)

    def _aggregate_page(
        self,
        blocks: List[Block],
        course_id: str,
        file_id: int,
        source_file: str,
        cl_trace: Dict[str, str],
        options: ExportOptions,
    ) -> List[GraphRAGDocument]:
        """页面模式聚合：由标准文档投影生成 GraphRAG 文档。"""
        normalized_docs = self._aggregate_normalized_page(
            blocks=blocks,
            course_id=course_id,
            file_id=file_id,
            source_file=source_file,
            cl_trace=cl_trace,
            options=options,
        )
        return self._project_normalized_documents(normalized_docs)

    def _project_normalized_documents(
        self,
        docs: List[NormalizedDocument],
    ) -> List[GraphRAGDocument]:
        """将标准课程文档投影为 GraphRAG 文档。"""
        return [self._project_normalized_document(doc) for doc in docs]

    def _project_normalized_document(self, doc: NormalizedDocument) -> GraphRAGDocument:
        """将单个标准课程文档投影为 GraphRAG 输入文档。"""
        heading_title = doc.heading_path[-1] if doc.heading_path else "未命名章节"
        metadata: Dict[str, Any] = {
            "id": doc.id,
            "course_id": doc.course_id,
            "source_file": doc.source_file,
            "document_type": doc.document_type.value,
            "chapter": doc.chapter,
            "section": doc.section,
            "subsection": doc.subsection,
            "heading_level": doc.heading_level,
            "heading_path": list(doc.heading_path),
            "page_start": doc.page_start,
            "page_end": doc.page_end,
        }
        metadata.update(doc.metadata)

        return GraphRAGDocument(
            title=self._build_doc_title(doc.course_id, heading_title),
            text=self._build_projection_text(heading_title, doc.content),
            metadata=metadata,
        )

    @staticmethod
    def _build_projection_text(heading_title: str, content: str) -> str:
        """GraphRAG 投影文本保留标题前缀，兼容现有检索习惯。"""
        normalized_title = heading_title.strip() or "未命名章节"
        normalized_content = (content or "").strip()
        if not normalized_content:
            return normalized_title
        if normalized_content.startswith(normalized_title):
            return normalized_content
        return f"{normalized_title}\n\n{normalized_content}"

    # -------------------- Block 渲染 --------------------

    def _get_renderer_registry(self, options: ExportOptions) -> BlockRendererRegistry:
        """按 ExportOptions 懒创建/缓存 BlockRendererRegistry。"""
        if self._renderer_registry is None:
            self._renderer_registry = BlockRendererRegistry.create_default(
                prefer_markdown=options.table_to_markdown,
            )
        return self._renderer_registry

    def _render_block_group(
        self,
        blocks: List[Block],
        options: Optional[ExportOptions] = None,
    ) -> RenderedBlockGroup:
        """将一组 blocks 渲染为正文与结构化统计。"""
        if options is None:
            options = ExportOptions()
        registry = self._get_renderer_registry(options)

        text_parts: List[str] = []
        block_ids: List[str] = []
        image_refs: List[str] = []
        tables_meta: List[Dict[str, Any]] = []
        images_meta: List[Dict[str, Any]] = []
        pages: List[int] = []
        equation_count = 0

        for block in blocks:
            result: RenderResult = registry.render(block)

            if result.text:
                text_parts.append(result.text)
            else:
                logger.debug("block %s 渲染结果为空，跳过文本部分", block.block_id)

            block_ids.append(block.block_id)

            if block.page is not None:
                pages.append(block.page)

            if block.block_type == BlockType.IMAGE and block.asset_ref:
                image_refs.append(block.asset_ref)

            if block.block_type == BlockType.EQUATION:
                equation_count += 1

            if not result.metadata:
                continue

            meta_type = result.metadata.get("type")
            if meta_type == "table":
                tables_meta.append(result.metadata)
            elif meta_type == "image":
                images_meta.append(result.metadata)

        return RenderedBlockGroup(
            text="\n\n".join(text_parts).strip(),
            block_ids=block_ids,
            image_refs=image_refs,
            tables_meta=tables_meta,
            images_meta=images_meta,
            pages=pages,
            equation_count=equation_count,
        )

    def _blocks_to_normalized_doc(
        self,
        blocks: List[Block],
        course_id: str,
        file_id: int,
        source_file: str,
        heading_path: List[str],
        doc_unit: str,
        cl_trace: Dict[str, str],
        options: Optional[ExportOptions] = None,
        source_section_level: Optional[int] = None,
        page_no: Optional[int] = None,
        drop_leading_title: bool = False,
    ) -> NormalizedDocument:
        """将一组 blocks 合并为单个标准课程文档。"""
        if not heading_path:
            heading_path = ["未命名章节"]

        content_blocks = list(blocks)
        if (
            drop_leading_title
            and content_blocks
            and content_blocks[0].block_type == BlockType.TITLE
        ):
            content_blocks = content_blocks[1:]

        rendered = self._render_block_group(content_blocks, options)
        source_page_indices = sorted(set(rendered.pages))

        if not source_page_indices:
            source_page_indices = sorted(
                {
                    block.page
                    for block in blocks
                    if block.page is not None
                }
            )

        if not source_page_indices and page_no is not None:
            source_page_indices = [page_no - 1]

        if source_page_indices:
            page_start = source_page_indices[0] + 1
            page_end = source_page_indices[-1] + 1
        else:
            page_start = page_no or 1
            page_end = page_no or 1

        chapter = heading_path[0] if len(heading_path) >= 1 else None
        section = heading_path[1] if len(heading_path) >= 2 else None
        subsection = heading_path[2] if len(heading_path) >= 3 else None

        metadata: Dict[str, Any] = {
            "schema_version": DOCUMENT_SCHEMA_VERSION,
            "pdf_file_id": file_id,
            "doc_unit": doc_unit,
            "block_ids": rendered.block_ids,
            "mineru_artifacts": cl_trace,
            "source_page_indices": source_page_indices,
            "has_table": bool(rendered.tables_meta),
            "has_equation": rendered.equation_count > 0,
            "has_image": bool(rendered.images_meta),
            "table_count": len(rendered.tables_meta),
            "equation_count": rendered.equation_count,
            "image_count": len(rendered.images_meta),
        }

        if source_section_level is not None:
            metadata["section_level"] = source_section_level

        if page_no is not None:
            metadata["page_no"] = page_no

        if rendered.image_refs:
            metadata["image_refs"] = rendered.image_refs

        if rendered.tables_meta:
            metadata["tables"] = rendered.tables_meta

        if rendered.images_meta:
            metadata["images"] = rendered.images_meta

        content = rendered.text or heading_path[-1]

        return NormalizedDocument(
            id=self._build_normalized_doc_id(course_id, source_file, heading_path),
            source_file=source_file,
            document_type=self._infer_document_type(source_file),
            course_id=course_id,
            chapter=chapter,
            section=section,
            subsection=subsection,
            heading_level=len(heading_path),
            heading_path=list(heading_path),
            content=content,
            page_start=page_start,
            page_end=page_end,
            metadata=metadata,
        )

    # -------------------- 文档 ID 与类型 --------------------

    @staticmethod
    def _build_doc_title(course_id: str, section_title: str) -> str:
        """为 GraphRAG 生成语义化标题。"""
        normalized = section_title.strip() or "未命名章节"
        return f"{course_id}-{normalized}"

    @staticmethod
    def _normalize_id_part(value: str) -> str:
        """将标题或文件名归一化为稳定 ID 片段。"""
        normalized = value.strip().lower()
        normalized = re.sub(r"\s+", "-", normalized)
        normalized = re.sub(r"[^\w\u4e00-\u9fff\.-]+", "-", normalized)
        normalized = re.sub(r"-{2,}", "-", normalized).strip("-")
        return normalized or "untitled"

    def _build_normalized_doc_id(
        self,
        course_id: str,
        source_file: str,
        heading_path: List[str],
    ) -> str:
        """构造标准文档稳定 ID。"""
        file_part = self._normalize_id_part(Path(source_file).stem)
        path_part = "__".join(self._normalize_id_part(item) for item in heading_path)
        return f"{course_id}:{file_part}:{path_part}"

    @staticmethod
    def _infer_document_type(source_file: str) -> DocumentType:
        """基于文件名做轻量文档类型识别。"""
        name = source_file.strip().lower()
        if any(token in name for token in ("slides", "slide", "ppt", "pptx", "课件")):
            return DocumentType.SLIDES
        if any(token in name for token in ("syllabus", "大纲", "教学计划")):
            return DocumentType.SYLLABUS
        if any(token in name for token in ("lab", "实验", "实验指导")):
            return DocumentType.LAB
        if any(token in name for token in ("notes", "note", "笔记")):
            return DocumentType.NOTES
        if any(token in name for token in ("exam", "试卷", "题库")):
            return DocumentType.EXAM
        if any(token in name for token in ("reference", "参考")):
            return DocumentType.REFERENCE
        if any(token in name for token in ("book", "教材", "课本")):
            return DocumentType.TEXTBOOK
        return DocumentType.UNKNOWN

    # -------------------- 长文档拆分 --------------------

    @staticmethod
    def _split_normalized_doc_by_chars(
        doc: NormalizedDocument,
        max_chars: Optional[int],
    ) -> List[NormalizedDocument]:
        """按 max_chars 拆分标准课程文档，按段落边界切割。"""
        if not max_chars:
            return [doc]

        paragraphs = doc.content.split("\n\n")
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

        docs: List[NormalizedDocument] = []
        for index, chunk in enumerate(chunks):
            sub_meta = dict(doc.metadata)
            sub_meta["chunk_index"] = index
            sub_meta["chunk_total"] = len(chunks)
            docs.append(NormalizedDocument(
                id=f"{doc.id}:part{index + 1}",
                source_file=doc.source_file,
                document_type=doc.document_type,
                course_id=doc.course_id,
                chapter=doc.chapter,
                section=doc.section,
                subsection=doc.subsection,
                heading_level=doc.heading_level,
                heading_path=list(doc.heading_path),
                content=chunk,
                page_start=doc.page_start,
                page_end=doc.page_end,
                metadata=sub_meta,
            ))
        return docs

    # -------------------- JSON 输出 --------------------

    @staticmethod
    def _write_json(docs: List[GraphRAGDocument], path: Path) -> None:
        """将 GraphRAG 文档列表写入 JSON 数组文件。"""
        path.parent.mkdir(parents=True, exist_ok=True)
        with open(path, "w", encoding="utf-8") as file:
            json.dump(
                [doc.to_graphrag_dict() for doc in docs],
                file,
                ensure_ascii=False,
                indent=2,
            )

    @staticmethod
    def _write_normalized_json(docs: List[NormalizedDocument], path: Path) -> None:
        """将标准课程文档写入 JSON 数组文件。"""
        path.parent.mkdir(parents=True, exist_ok=True)
        with open(path, "w", encoding="utf-8") as file:
            json.dump(
                [doc.to_dict() for doc in docs],
                file,
                ensure_ascii=False,
                indent=2,
            )
