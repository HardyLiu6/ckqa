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
    "course_material_id",
    "pdf_file_id",
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
    soft_max_chars: int = 2200        # 未显式设置 max_chars 时的节级软切分上限
    min_chunk_chars: int = 500        # 尽量避免产出过短 chunk
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
    _TOCISH_TITLE_RE = re.compile(
        r"^(?:"
        r"第[一二三四五六七八九十百千\d]+[章节篇]"
        r"|(?:\d+\.)+\d*"
        r"|习题"
        r"|参考文献"
        r")"
    )
    _SPECIAL_SECTION_TITLE_RE = re.compile(
        r"^(?:"
        r"本章小结|小结|总结"
        r"|习题|思考题|练习题|复习题"
        r"|参考文献|附录(?:[\sA-Z一二三四五六七八九十\d].*)?"
        r")$"
    )
    _FUZZY_CHAPTER_TITLE_RE = re.compile(r"^第\S{1,6}章(?:\s|$)")
    _TRAILING_PAGE_NOISE_RE = re.compile(
        r"^(?P<title>.*?)(?:\s*(?:\.{2,}|…{2,}|·{2,})\s*|\s+)(?P<page>\d+)\s*$"
    )
    _MAX_GENERIC_TITLE_LEN = 120
    _SOFT_CHUNK_TRIGGER_FACTOR = 1.35
    _SENTENCE_SPLIT_RE = re.compile(r"(?<=[。！？；.!?;])")
    _CLAUSE_SPLIT_RE = re.compile(r"(?<=[，、,:：])")
    _DOCUMENT_TYPE_FILENAME_HINTS = [
        (DocumentType.SLIDES, ("slides", "slide", "ppt", "pptx", "课件")),
        (DocumentType.SYLLABUS, ("syllabus", "大纲", "教学计划", "课程说明")),
        (DocumentType.LAB, ("lab", "实验", "实验指导")),
        (DocumentType.NOTES, ("notes", "note", "笔记", "讲义", "lecture", "handout")),
        (DocumentType.EXAM, ("exam", "试卷", "题库", "习题集")),
        (DocumentType.REFERENCE, ("reference", "参考", "论文", "paper")),
        (DocumentType.TEXTBOOK, ("book", "教材", "课本", "教科书")),
    ]
    _DOCUMENT_TYPE_CONTENT_HINTS = [
        (
            DocumentType.SYLLABUS,
            ("教学大纲", "课程目标", "课程简介", "课程性质", "学时", "学分", "考核方式", "教学安排"),
        ),
        (
            DocumentType.LAB,
            ("实验目的", "实验要求", "实验内容", "实验步骤", "实验原理", "实验报告"),
        ),
        (
            DocumentType.NOTES,
            ("课程讲义", "授课讲义", "课堂笔记", "复习提纲", "学习笔记"),
        ),
        (
            DocumentType.EXAM,
            ("试卷", "考试时间", "一、选择题", "二、填空题", "参考答案", "标准答案"),
        ),
        (
            DocumentType.REFERENCE,
            ("参考文献", "doi", "关键词", "摘要", "abstract", "作者简介"),
        ),
        (
            DocumentType.TEXTBOOK,
            ("本教材", "全书共分", "编著", "出版社", "教材", "图书在版编目", "isbn", "第4版", "第四版"),
        ),
    ]

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
        file_id: int = int(pdf_file["id"])
        course_material_id: int = int(pdf_file.get("course_material_id") or file_id)
        source_file: str = pdf_file.get("display_name") or pdf_file["file_name"]

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
                    "course_material_id": course_material_id,
                    "pdf_file_id": file_id,
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
                "course_material_id": course_material_id,
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
        return {
            normalized: level
            for normalized, (level, _) in GraphRAGExporter._parse_md_heading_catalog(md_text).items()
        }

    @staticmethod
    def _parse_md_heading_catalog(md_text: str) -> Dict[str, Tuple[int, str]]:
        """
        从 markdown 文本解析标题行，返回：
        {归一化标题文本: (层级, 规范标题文本)}

        这里保留“规范标题文本”，供 OCR 轻微失真的标题做回填纠正。
        """
        heading_re = re.compile(r"^(#{1,6})\s+(.+)$", re.MULTILINE)
        result: Dict[str, Tuple[int, str]] = {}
        for match in heading_re.finditer(md_text):
            level = len(match.group(1))
            title_text = GraphRAGExporter._normalize_heading_text(match.group(2).strip())
            normalized = re.sub(r"\s+", "", title_text.lower())
            if normalized:
                result[normalized] = (level, title_text)
        return result

    @classmethod
    def _is_probable_chapter_title(
        cls,
        title_text: str,
        text_level: Optional[int] = None,
    ) -> bool:
        """
        识别“像章标题但编号可能被 OCR 轻微破坏”的标题。

        典型场景：
        - `第八章` 被识别成 `第几章`
        - 正式标题块存在，但不再匹配标准编号正则

        这类标题若被忽略，会导致章末“习题”只能挂到最后一节下面。
        """
        text = cls._normalize_heading_text(title_text)
        if not text:
            return False
        if cls._TITLE_LEVEL_PATTERNS[0][0].match(text):
            return True
        if text_level is None or text_level > 1:
            return False
        return bool(cls._FUZZY_CHAPTER_TITLE_RE.match(text))

    @classmethod
    def _extract_chapter_title_suffix(cls, title_text: str) -> str:
        """
        提取“第X章 标题”中的章名尾部，用于和 markdown 标题做保守对齐。
        """
        text = cls._normalize_heading_text(title_text)
        if not text or "章" not in text:
            return ""
        _, suffix = text.split("章", 1)
        return re.sub(r"\s+", "", suffix.strip().lower())

    def _canonicalize_section_title(
        self,
        title_text: str,
        source_section_level: int,
        md_heading_catalog: Optional[Dict[str, Tuple[int, str]]],
    ) -> str:
        """
        使用 markdown 标题目录对当前标题做轻量规范化。

        当前只做保守修正：
        1. 精确命中 markdown 标题时，直接采用 markdown 的规范写法
        2. 对 OCR 轻微损坏的章标题（如 `第几章 ...`），若章名尾部能唯一匹配 markdown，
           则回填为 markdown 中的标准章标题
        """
        normalized_title = self._normalize_heading_text(title_text) or "未命名章节"
        if not md_heading_catalog:
            return normalized_title

        normalized_key = re.sub(r"\s+", "", normalized_title.lower())
        exact_match = md_heading_catalog.get(normalized_key)
        if exact_match is not None:
            exact_title = exact_match[1]
            if (
                source_section_level != 1
                or not self._is_probable_chapter_title(normalized_title, text_level=1)
                or self._TITLE_LEVEL_PATTERNS[0][0].match(self._normalize_heading_text(exact_title))
            ):
                return exact_title

        if source_section_level != 1:
            return normalized_title

        if not self._is_probable_chapter_title(normalized_title, text_level=1):
            return normalized_title

        suffix = self._extract_chapter_title_suffix(normalized_title)
        if not suffix:
            return normalized_title

        candidates = [
            canonical_title
            for _, (level, canonical_title) in md_heading_catalog.items()
            if level == 1 and self._extract_chapter_title_suffix(canonical_title) == suffix
        ]

        unique_candidates = list(dict.fromkeys(candidates))
        standard_candidates = [
            title
            for title in unique_candidates
            if self._TITLE_LEVEL_PATTERNS[0][0].match(self._normalize_heading_text(title))
        ]

        if len(standard_candidates) == 1:
            return standard_candidates[0]

        if len(unique_candidates) == 1:
            return unique_candidates[0]

        return normalized_title

    def _infer_title_level(
        self,
        title_text: str,
        md_headings: Optional[Dict[str, int]],
        text_level: Optional[int] = None,
    ) -> int:
        """
        推断标题层级：
        1. 优先使用 MinerU 原生 text_level
        2. 再用标题编号模式推断
        3. 若模式未匹配，用 markdown 标题映射
        4. 默认 level=1
        """
        text = self._normalize_heading_text(title_text)

        for pattern, level in self._TITLE_LEVEL_PATTERNS:
            if pattern.match(text):
                return level

        if self._is_probable_chapter_title(text, text_level=text_level):
            return 1

        if text_level is not None and text_level > 0:
            return text_level

        if md_headings:
            normalized = re.sub(r"\s+", "", text.lower())
            md_level = md_headings.get(normalized)
            if md_level is not None:
                return md_level

        return 1

    def _is_section_boundary(self, block: Block) -> bool:
        """判断一个 TITLE 块是否构成章节边界。"""
        text = self._normalize_heading_text(block.text)
        if not text:
            return False
        if self._SECTION_BOUNDARY_RE.match(text):
            return True
        return block.text_level is not None and block.text_level > 0

    @classmethod
    def _normalize_heading_text(cls, text: str) -> str:
        """清理标题中的目录点线、尾部页码和多余空白。"""
        normalized = re.sub(r"\s+", " ", (text or "").strip())
        normalized = re.sub(r"^[-•]\s*", "", normalized)

        match = cls._TRAILING_PAGE_NOISE_RE.match(normalized)
        if match and cls._TOCISH_TITLE_RE.match(match.group("title").strip()):
            normalized = match.group("title").strip()

        normalized = re.sub(r"(?:\.{2,}|…{2,}|·{2,})\s*$", "", normalized).strip()
        return normalized

    def _heuristic_title_indices(self, blocks: List[Block]) -> List[int]:
        """
        启发式标题识别（仅在 blocks 中无 TITLE 类型时 fallback 使用）。
        只识别章/节编号模式，不做过于激进的匹配。
        """
        indices = []
        for index, block in enumerate(blocks):
            if block.block_type != BlockType.TEXT:
                continue
            text = self._normalize_heading_text(block.text)
            if not text or len(text) > 80:
                continue
            if self._SECTION_BOUNDARY_RE.match(text):
                indices.append(index)
        return indices

    @classmethod
    def _is_special_section_title(cls, title_text: str) -> bool:
        """判断是否为应保留为独立 section 的功能性标题。"""
        normalized = cls._normalize_heading_text(title_text)
        if not normalized:
            return False
        return bool(cls._SPECIAL_SECTION_TITLE_RE.match(normalized))

    def _select_title_indices(
        self,
        blocks: List[Block],
        md_headings: Optional[Dict[str, int]],
    ) -> List[int]:
        """
        选择章节边界标题。

        规则尽量兼顾教材和非教材文档：
        1. 优先保留带编号的显式章节标题
        2. 若无编号，但存在 MinerU TITLE + text_level，则选择最高两层标题
        3. 若 markdown 中有标题映射，则使用较浅层标题
        4. 最后退化为全部 TITLE 块
        """
        title_candidates: List[Tuple[int, Block, str]] = []
        numbered_indices: List[int] = []
        special_indices: List[int] = []

        for index, block in enumerate(blocks):
            if block.block_type != BlockType.TITLE:
                continue
            text = self._normalize_heading_text(block.text)
            if not text or len(text) > self._MAX_GENERIC_TITLE_LEN:
                continue

            title_candidates.append((index, block, text))
            if self._SECTION_BOUNDARY_RE.match(text):
                numbered_indices.append(index)
            elif self._is_probable_chapter_title(text, text_level=block.text_level):
                numbered_indices.append(index)
            elif self._is_special_section_title(text):
                special_indices.append(index)

        if numbered_indices:
            return sorted(set(numbered_indices + special_indices))

        if not title_candidates:
            return self._heuristic_title_indices(blocks)

        level_candidates = [
            (index, block, text)
            for index, block, text in title_candidates
            if block.text_level is not None and block.text_level > 0
        ]
        if level_candidates:
            min_level = min(block.text_level for _, block, _ in level_candidates)
            level_cutoff = min_level + 1
            selected = [
                index
                for index, block, _ in title_candidates
                if (block.text_level or min_level) <= level_cutoff
            ]
            if selected:
                return selected

        if md_headings:
            selected = []
            for index, _, text in title_candidates:
                normalized = re.sub(r"\s+", "", text.lower())
                md_level = md_headings.get(normalized)
                if md_level is not None and md_level <= 3:
                    selected.append(index)
            if selected:
                return selected

        return [index for index, _, _ in title_candidates]

    def _resolve_effective_section_level(
        self,
        section_title: str,
        title_block: Block,
        md_headings: Optional[Dict[str, int]],
        heading_stack: List[Tuple[int, str]],
        document_type: DocumentType,
    ) -> int:
        """
        为当前 section 计算有效层级。

        对教材中的“习题 / 参考文献 / 本章小结”等功能性标题做一层保守修正：
        - 它们通常不应覆盖为新的根层级
        - 更合理的是挂在当前最高可用结构标题之下
        """
        inferred_level = self._infer_title_level(
            section_title,
            md_headings,
            text_level=title_block.text_level,
        )

        if document_type != DocumentType.TEXTBOOK:
            return inferred_level

        if not self._is_special_section_title(section_title):
            return inferred_level

        if not heading_stack:
            return inferred_level

        parent_anchor_level = max(heading_stack[0][0], 1)
        return max(2, parent_anchor_level + 1)

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
        md_heading_catalog: Optional[Dict[str, Tuple[int, str]]] = None
        if md_text:
            md_heading_catalog = self._parse_md_heading_catalog(md_text)
            md_headings = {
                normalized: level
                for normalized, (level, _) in md_heading_catalog.items()
            }
        document_type = self._infer_document_type(source_file, blocks)

        title_indices = self._select_title_indices(blocks, md_headings)

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
                document_type=document_type,
                drop_leading_title=False,
            )
            return self._split_normalized_doc_by_chars(doc, options)

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
                document_type=document_type,
                drop_leading_title=False,
            )
            docs.extend(self._split_normalized_doc_by_chars(pre_doc, options))

        heading_stack: List[Tuple[int, str]] = []
        for start, end in sections:
            sec_blocks = blocks[start:end]
            title_block = blocks[start]
            raw_section_title = self._normalize_heading_text(title_block.text) or "未命名章节"
            source_section_level = self._resolve_effective_section_level(
                raw_section_title,
                title_block,
                md_headings,
                heading_stack,
                document_type,
            )
            section_title = self._canonicalize_section_title(
                raw_section_title,
                source_section_level,
                md_heading_catalog,
            )

            while heading_stack and heading_stack[-1][0] >= source_section_level:
                heading_stack.pop()
            heading_stack.append((source_section_level, section_title))

            doc = self._blocks_to_normalized_doc(
                blocks=sec_blocks,
                course_id=course_id,
                file_id=file_id,
                source_file=source_file,
                heading_path=[item[1] for item in heading_stack],
                doc_unit="section",
                cl_trace=cl_trace,
                options=options,
                source_section_level=source_section_level,
                document_type=document_type,
                drop_leading_title=True,
            )
            docs.extend(self._split_normalized_doc_by_chars(doc, options))

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
        document_type = self._infer_document_type(source_file, blocks)
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
                document_type=document_type,
                drop_leading_title=False,
            )
            docs.extend(self._split_normalized_doc_by_chars(doc, options))

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
        document_type: Optional[DocumentType] = None,
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

        heading_level = (
            source_section_level
            if source_section_level is not None and source_section_level > 0
            else len(heading_path)
        )
        chapter, section, subsection = self._resolve_heading_fields(
            heading_path=heading_path,
            heading_level=heading_level,
            doc_unit=doc_unit,
        )

        metadata: Dict[str, Any] = {
            "schema_version": DOCUMENT_SCHEMA_VERSION,
            "course_material_id": file_id,
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
        metadata["content_char_count"] = len(content)

        return NormalizedDocument(
            id=self._build_normalized_doc_id(course_id, source_file, heading_path),
            source_file=source_file,
            document_type=document_type or self._infer_document_type(source_file),
            course_id=course_id,
            chapter=chapter,
            section=section,
            subsection=subsection,
            heading_level=heading_level,
            heading_path=list(heading_path),
            content=content,
            page_start=page_start,
            page_end=page_end,
            metadata=metadata,
        )

    @staticmethod
    def _resolve_heading_fields(
        heading_path: List[str],
        heading_level: int,
        doc_unit: str,
    ) -> Tuple[Optional[str], Optional[str], Optional[str]]:
        """根据真实层级将 heading_path 对齐到 chapter/section/subsection。"""
        if doc_unit == "page" or not heading_path:
            return None, None, None

        chapter: Optional[str] = None
        section: Optional[str] = None
        subsection: Optional[str] = None

        start_level = max(1, heading_level - len(heading_path) + 1)
        for index, item in enumerate(heading_path):
            level = start_level + index
            if level == 1:
                chapter = item
            elif level == 2:
                section = item
            elif level == 3:
                subsection = item

        return chapter, section, subsection

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

    @classmethod
    def _infer_document_type(
        cls,
        source_file: str,
        blocks: Optional[List[Block]] = None,
    ) -> DocumentType:
        """基于文件名优先、正文内容补充的方式推断文档类型。"""
        name = source_file.strip().lower()
        filename_hit = cls._match_document_type_by_tokens(
            name,
            cls._DOCUMENT_TYPE_FILENAME_HINTS,
        )
        if filename_hit is not None:
            return filename_hit

        if not blocks:
            return DocumentType.UNKNOWN

        sample_text = cls._build_document_type_sample(blocks)
        if not sample_text:
            return DocumentType.UNKNOWN

        content_hit = cls._match_document_type_by_tokens(
            sample_text,
            cls._DOCUMENT_TYPE_CONTENT_HINTS,
        )
        if content_hit is not None:
            return content_hit

        return DocumentType.UNKNOWN

    @staticmethod
    def _match_document_type_by_tokens(
        text: str,
        hints: List[Tuple[DocumentType, Tuple[str, ...]]],
    ) -> Optional[DocumentType]:
        """按 token 匹配文档类型。"""
        normalized = text.lower()
        for doc_type, tokens in hints:
            if any(token.lower() in normalized for token in tokens):
                return doc_type
        return None

    @staticmethod
    def _build_document_type_sample(blocks: List[Block]) -> str:
        """抽取文档前部文本作为类型推断样本。"""
        parts: List[str] = []
        total_chars = 0

        for block in blocks:
            if block.page is not None and block.page > 10:
                break
            if block.block_type not in (BlockType.TEXT, BlockType.TITLE, BlockType.LIST):
                continue

            text = (block.text or "").strip()
            if not text:
                continue

            parts.append(text)
            total_chars += len(text)
            if total_chars >= 6000 or len(parts) >= 120:
                break

        return "\n".join(parts)

    # -------------------- 长文档拆分 --------------------

    def _split_normalized_doc_by_chars(
        self,
        doc: NormalizedDocument,
        options: ExportOptions,
    ) -> List[NormalizedDocument]:
        """按显式或软切分阈值拆分标准课程文档。"""
        max_chars = self._resolve_chunk_limit(doc, options)
        if not max_chars:
            return [doc]

        units = self._build_chunk_units(doc.content, max_chars)
        chunks = self._pack_chunk_units(
            units=units,
            max_chars=max_chars,
            min_chunk_chars=max(options.min_chunk_chars, 1),
        )

        if len(chunks) <= 1:
            return [doc]

        docs: List[NormalizedDocument] = []
        strategy = "explicit" if options.max_chars else "soft"
        for index, chunk in enumerate(chunks):
            sub_meta = dict(doc.metadata)
            sub_meta["chunk_index"] = index
            sub_meta["chunk_total"] = len(chunks)
            sub_meta["chunk_char_count"] = len(chunk)
            sub_meta["chunk_strategy"] = strategy
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

    def _resolve_chunk_limit(
        self,
        doc: NormalizedDocument,
        options: ExportOptions,
    ) -> Optional[int]:
        """计算文档拆分阈值。"""
        if options.max_chars:
            return max(200, options.max_chars)

        if doc.metadata.get("doc_unit") != "section":
            return None

        soft_limit = max(400, options.soft_max_chars)
        content_length = len((doc.content or "").strip())
        if content_length <= int(soft_limit * self._SOFT_CHUNK_TRIGGER_FACTOR):
            return None

        return soft_limit

    def _build_chunk_units(self, content: str, max_chars: int) -> List[str]:
        """将正文按段落/句子拆为更稳定的切分单元。"""
        paragraphs = [
            para.strip()
            for para in re.split(r"\n{2,}", content or "")
            if para.strip()
        ]
        if not paragraphs:
            return []

        units: List[str] = []
        for para in paragraphs:
            if len(para) <= max_chars:
                units.append(para)
                continue
            units.extend(self._split_oversized_text(para, max_chars))
        return units

    def _split_oversized_text(self, text: str, max_chars: int) -> List[str]:
        """对超长段落继续按句子、分句或硬切分。"""
        for splitter in (self._SENTENCE_SPLIT_RE, self._CLAUSE_SPLIT_RE):
            parts = [
                part.strip()
                for part in splitter.split(text)
                if part and part.strip()
            ]
            if len(parts) <= 1:
                continue

            chunks: List[str] = []
            current = ""
            for part in parts:
                if current and len(current) + len(part) > max_chars:
                    chunks.append(current.strip())
                    current = part
                else:
                    current += part
            if current.strip():
                chunks.append(current.strip())

            if all(len(chunk) <= max_chars for chunk in chunks):
                return chunks

        return [
            text[start:start + max_chars].strip()
            for start in range(0, len(text), max_chars)
            if text[start:start + max_chars].strip()
        ]

    @staticmethod
    def _pack_chunk_units(
        units: List[str],
        max_chars: int,
        min_chunk_chars: int,
    ) -> List[str]:
        """将切分单元打包为最终 chunk，尽量避免过短尾块。"""
        if not units:
            return []

        chunks: List[str] = []
        current: List[str] = []
        current_len = 0

        for unit in units:
            unit_len = len(unit) + (2 if current else 0)
            if current and current_len + unit_len > max_chars and current_len >= min_chunk_chars:
                chunks.append("\n\n".join(current))
                current = [unit]
                current_len = len(unit)
            else:
                current.append(unit)
                current_len += unit_len

        if current:
            tail = "\n\n".join(current)
            if chunks and len(tail) < min_chunk_chars:
                chunks[-1] = f"{chunks[-1]}\n\n{tail}".strip()
            else:
                chunks.append(tail)

        return chunks

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
