from __future__ import annotations

import re
from dataclasses import asdict, dataclass
from pathlib import Path
from typing import Any

import pandas as pd


_DATA_BLOCK_RE = re.compile(r"\[Data:\s*([^\]]+)\]", re.IGNORECASE)
_DATA_SEGMENT_RE = re.compile(r"(Sources|Entities|Relationships|Reports)\s*\(([^)]*)\)", re.IGNORECASE)
_DATA_TOKEN_RE = re.compile(r"[A-Za-z0-9][A-Za-z0-9_-]*")
_METADATA_KEY_RE = re.compile(
    r"\b(document_type|chapter|section|subsection|heading_level|heading_path_text|"
    r"page_start|page_end|section_level|source_file|course_id):"
)
_SNIPPET_MAX_CHARS = 280


@dataclass(frozen=True, slots=True)
class QueryCitationSource:
    rank: int
    kind: str
    ref: str
    chunk_id: str
    document_key: str
    source_file: str
    heading_path: str
    page_start: int | None
    page_end: int | None
    snippet: str

    def to_dict(self) -> dict[str, Any]:
        return asdict(self)


@dataclass(frozen=True, slots=True)
class ResolvedAnswerCitations:
    display_text: str
    sources: list[QueryCitationSource]


def resolve_answer_citations(answer: str | None, output_dir: Path | str | None) -> ResolvedAnswerCitations:
    """解析 GraphRAG `[Data: Sources (...)]` 引用，并转成学生可读来源。"""
    raw_answer = answer or ""
    source_refs = _extract_source_refs(raw_answer)
    rows_by_ref = _load_text_unit_rows(output_dir, source_refs)

    sources: list[QueryCitationSource] = []
    seen: set[str] = set()
    for ref in source_refs:
        if ref in seen:
            continue
        seen.add(ref)
        row = rows_by_ref.get(ref)
        if row is None:
            continue
        metadata, body = _split_text_unit_metadata(str(row.get("text") or ""))
        source_file = metadata.get("source_file") or str(row.get("document_id") or "")
        heading_path = metadata.get("heading_path_text") or _join_heading_path(metadata)
        sources.append(
            QueryCitationSource(
                rank=len(sources) + 1,
                kind="source",
                ref=ref,
                chunk_id=str(row.get("id") or ""),
                document_key=source_file or str(row.get("document_id") or ref),
                source_file=source_file,
                heading_path=heading_path,
                page_start=_parse_int(metadata.get("page_start")),
                page_end=_parse_int(metadata.get("page_end")),
                snippet=_shorten_snippet(body),
            )
        )

    display_text = _append_source_notes(_replace_data_blocks(raw_answer, sources), sources)
    return ResolvedAnswerCitations(display_text=display_text, sources=sources)


def _extract_source_refs(answer: str) -> list[str]:
    refs: list[str] = []
    for block in _DATA_BLOCK_RE.findall(answer):
        for raw_kind, raw_ids in _DATA_SEGMENT_RE.findall(block):
            if raw_kind.lower() != "sources":
                continue
            for token in _DATA_TOKEN_RE.findall(raw_ids):
                if token.isdigit() and token not in refs:
                    refs.append(token)
    return refs


def _load_text_unit_rows(output_dir: Path | str | None, refs: list[str]) -> dict[str, dict[str, Any]]:
    if output_dir is None or not refs:
        return {}
    text_units_path = Path(output_dir) / "text_units.parquet"
    if not text_units_path.exists():
        return {}
    try:
        frame = pd.read_parquet(
            text_units_path,
            columns=["id", "human_readable_id", "text", "document_id"],
        )
    except Exception:
        return {}

    wanted = set(refs)
    rows: dict[str, dict[str, Any]] = {}
    for record in frame.to_dict(orient="records"):
        human_id = str(record.get("human_readable_id") or "").strip()
        if human_id in wanted and human_id not in rows:
            rows[human_id] = record
    return rows


def _split_text_unit_metadata(text: str) -> tuple[dict[str, str], str]:
    matches = list(_METADATA_KEY_RE.finditer(text))
    if not matches:
        return {}, text.strip()

    metadata: dict[str, str] = {}
    for index, match in enumerate(matches):
        key = match.group(1)
        value_start = match.end()
        value_end = matches[index + 1].start() if index + 1 < len(matches) else len(text)
        metadata[key] = text[value_start:value_end].strip(" .\n\t")

    last_match = matches[-1]
    tail = text[last_match.end():]
    _, separator, body_tail = tail.partition(". ")
    body = body_tail if separator else ""
    return metadata, body or text.strip()


def _join_heading_path(metadata: dict[str, str]) -> str:
    parts = [
        metadata.get("chapter"),
        metadata.get("section"),
        metadata.get("subsection"),
    ]
    return " > ".join(part for part in parts if part)


def _parse_int(value: str | None) -> int | None:
    if value is None:
        return None
    match = re.search(r"\d+", value)
    return int(match.group(0)) if match else None


def _shorten_snippet(text: str) -> str:
    normalized = re.sub(r"\s+", " ", text).strip()
    if len(normalized) <= _SNIPPET_MAX_CHARS:
        return normalized
    return normalized[: _SNIPPET_MAX_CHARS - 1].rstrip() + "…"


def _replace_data_blocks(answer: str, sources: list[QueryCitationSource]) -> str:
    ref_to_rank = {source.ref: source.rank for source in sources}

    def replace(match: re.Match[str]) -> str:
        block = match.group(1)
        ranks: list[int] = []
        for raw_kind, raw_ids in _DATA_SEGMENT_RE.findall(block):
            if raw_kind.lower() != "sources":
                continue
            for token in _DATA_TOKEN_RE.findall(raw_ids):
                rank = ref_to_rank.get(token)
                if rank is not None and rank not in ranks:
                    ranks.append(rank)
        if ranks:
            joined = "、".join(str(rank) for rank in ranks)
            return f"[来源 {joined}]"
        return "[已参考课程知识库]"

    return _DATA_BLOCK_RE.sub(replace, answer)


def _append_source_notes(answer: str, sources: list[QueryCitationSource]) -> str:
    if not sources:
        return answer
    lines = ["参考来源："]
    for source in sources:
        label_parts = []
        if source.source_file:
            label_parts.append(source.source_file)
        if source.heading_path:
            label_parts.append(source.heading_path)
        page_label = _page_label(source.page_start, source.page_end)
        if page_label:
            label_parts.append(page_label)
        label = " · ".join(label_parts) if label_parts else "课程知识库"
        lines.append(f"[来源 {source.rank}] {label}")
    return answer.rstrip() + "\n\n" + "\n".join(lines)


def _page_label(page_start: int | None, page_end: int | None) -> str:
    if page_start is None and page_end is None:
        return ""
    if page_start is not None and page_end is not None and page_start != page_end:
        return f"p{page_start}-{page_end}"
    page = page_start if page_start is not None else page_end
    return f"p{page}"
