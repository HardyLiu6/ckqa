from __future__ import annotations

import re
from dataclasses import asdict, dataclass, replace
from pathlib import Path
from typing import Any

import pandas as pd


_DATA_BLOCK_RE = re.compile(r"\[Data:\s*([^\]]+)\]", re.IGNORECASE)
_DATA_SEGMENT_RE = re.compile(
    r"(Sources|Text\s+Units?|Entities|Relationships|Reports|Hybrid)\s*\(([^)]*)\)",
    re.IGNORECASE,
)
_DATA_TOKEN_RE = re.compile(r"[A-Za-z0-9][A-Za-z0-9_-]*")
_METADATA_KEY_RE = re.compile(
    r"\b(document_type|chapter|section|subsection|heading_level|heading_path_text|"
    r"page_start|page_end|section_level|source_file|course_id):"
)
_SNIPPET_MAX_CHARS = 280
_TEXT_UNIT_PREFIX_MIN_CHARS = 8


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
        payload = asdict(self)
        payload["source_type"] = self.kind
        return payload


@dataclass(frozen=True, slots=True)
class ResolvedAnswerCitations:
    display_text: str
    sources: list[QueryCitationSource]


def resolve_answer_citations(
    answer: str | None,
    output_dir: Path | str | None,
    *,
    mode: str | None = None,
    fallback_query: str | None = None,
    fallback_text_unit_limit: int = 3,
) -> ResolvedAnswerCitations:
    """解析 GraphRAG `[Data: ...]` 引用，并转成学生可读来源。"""
    raw_answer = answer or ""
    refs_by_kind = _extract_refs_by_kind(raw_answer)
    source_refs = refs_by_kind.get("sources", [])
    rows_by_ref = _load_text_unit_rows(output_dir, source_refs)
    hybrid_refs = refs_by_kind.get("hybrid", [])
    hybrid_rows_by_ref = _load_text_unit_rows(output_dir, hybrid_refs)

    sources: list[QueryCitationSource] = []
    seen: set[tuple[str, str]] = set()
    for ref in source_refs:
        source_key = ("sources", ref)
        if source_key in seen:
            continue
        seen.add(source_key)
        row = rows_by_ref.get(ref)
        if row is None:
            continue
        sources.append(_source_from_text_unit(row, ref, len(sources) + 1, kind="graphrag_citation"))

    for ref in hybrid_refs:
        source_key = ("hybrid", ref)
        if source_key in seen:
            continue
        seen.add(source_key)
        row = hybrid_rows_by_ref.get(ref)
        if row is None:
            continue
        sources.append(_source_from_text_unit(row, ref, len(sources) + 1, kind="hybrid_text_unit"))

    for ref in refs_by_kind.get("reports", []):
        source_key = ("reports", ref)
        if source_key in seen:
            continue
        seen.add(source_key)
        row = _load_record_by_ref(output_dir, "community_reports.parquet", ref, ["human_readable_id", "id", "community"])
        if row is not None:
            sources.append(_source_from_report(row, ref, len(sources) + 1))

    for ref in refs_by_kind.get("entities", []):
        source_key = ("entities", ref)
        if source_key in seen:
            continue
        seen.add(source_key)
        row = _load_record_by_ref(output_dir, "entities.parquet", ref, ["human_readable_id", "id", "title", "name"])
        if row is not None:
            sources.append(_source_from_entity(row, ref, len(sources) + 1))

    for ref in refs_by_kind.get("relationships", []):
        source_key = ("relationships", ref)
        if source_key in seen:
            continue
        seen.add(source_key)
        row = _load_record_by_ref(output_dir, "relationships.parquet", ref, ["human_readable_id", "id"])
        if row is not None:
            sources.append(_source_from_relationship(row, ref, len(sources) + 1))

    fallback_sources: list[QueryCitationSource] = []
    if _should_add_global_fallback(mode, fallback_query, sources):
        fallback_rows = _rank_fallback_text_units(output_dir, fallback_query or "", fallback_text_unit_limit)
        existing_text_refs = {
            source.ref
            for source in sources
            if source.kind in {"graphrag_citation", "global_fallback_text_unit"}
        }
        for row in fallback_rows:
            fallback_ref = _normalize_ref_value(row.get("human_readable_id")) or _clean_string(row.get("id"))
            if not fallback_ref:
                fallback_ref = f"fallback-{len(sources) + 1}"
            if fallback_ref in existing_text_refs:
                continue
            source_key = ("fallback_text_units", fallback_ref)
            if source_key in seen:
                continue
            seen.add(source_key)
            existing_text_refs.add(fallback_ref)
            fallback_sources.append(
                _source_from_text_unit(
                    row,
                    fallback_ref,
                    len(sources) + len(fallback_sources) + 1,
                    kind="global_fallback_text_unit",
                )
            )

    drift_fallback_ranks: list[int] = []
    if _is_drift_mode(mode) and fallback_sources:
        sources = _rerank_sources([*fallback_sources, *sources])
        drift_fallback_ranks = [
            source.rank
            for source in sources
            if source.kind == "global_fallback_text_unit"
        ][:1]
    else:
        sources = _rerank_sources([*sources, *fallback_sources])

    display_text = _append_source_notes(
        _replace_data_blocks(raw_answer, sources, fallback_source_ranks=drift_fallback_ranks),
        sources,
    )
    return ResolvedAnswerCitations(display_text=display_text, sources=sources)


def _extract_source_refs(answer: str) -> list[str]:
    return _extract_refs_by_kind(answer).get("sources", [])


def _extract_refs_by_kind(answer: str) -> dict[str, list[str]]:
    refs_by_kind: dict[str, list[str]] = {
        "sources": [],
        "reports": [],
        "entities": [],
        "relationships": [],
    }
    for block in _DATA_BLOCK_RE.findall(answer):
        for raw_kind, raw_ids in _DATA_SEGMENT_RE.findall(block):
            kind = _normalize_data_segment_kind(raw_kind)
            kind_refs = refs_by_kind.setdefault(kind, [])
            for token in _DATA_TOKEN_RE.findall(raw_ids):
                if token not in kind_refs:
                    kind_refs.append(token)
    return refs_by_kind


def _load_text_unit_rows(output_dir: Path | str | None, refs: list[str]) -> dict[str, dict[str, Any]]:
    if output_dir is None or not refs:
        return {}
    text_units_path = Path(output_dir) / "text_units.parquet"
    if not text_units_path.exists():
        return {}
    frame = _read_parquet(text_units_path, columns=["id", "human_readable_id", "text", "document_id"])
    if frame is None:
        return {}

    wanted = set(refs)
    rows: dict[str, dict[str, Any]] = {}
    for record in frame.to_dict(orient="records"):
        human_id = _normalize_ref_value(record.get("human_readable_id"))
        if human_id in wanted and human_id not in rows:
            rows[human_id] = record
        full_id = _clean_string(record.get("id"))
        for ref in wanted:
            if ref in rows:
                continue
            if full_id == ref or (len(ref) >= _TEXT_UNIT_PREFIX_MIN_CHARS and full_id.startswith(ref)):
                rows[ref] = record
    return rows


def _load_record_by_ref(
    output_dir: Path | str | None,
    file_name: str,
    ref: str,
    ref_columns: list[str],
) -> dict[str, Any] | None:
    if output_dir is None:
        return None
    parquet_path = Path(output_dir) / file_name
    if not parquet_path.exists():
        return None
    frame = _read_parquet(parquet_path)
    if frame is None:
        return None
    for record in frame.to_dict(orient="records"):
        for column in ref_columns:
            if _normalize_ref_value(record.get(column)) == ref:
                return record
    return None


def _read_parquet(path: Path, columns: list[str] | None = None) -> pd.DataFrame | None:
    try:
        if columns is None:
            return pd.read_parquet(path)
        return pd.read_parquet(path, columns=columns)
    except Exception:
        try:
            return pd.read_parquet(path)
        except Exception:
            return None


def _source_from_text_unit(
    row: dict[str, Any],
    ref: str,
    rank: int,
    *,
    kind: str,
) -> QueryCitationSource:
    metadata, body = _split_text_unit_metadata(_clean_string(row.get("text")))
    source_file = metadata.get("source_file") or _clean_string(row.get("document_id"))
    heading_path = metadata.get("heading_path_text") or _join_heading_path(metadata)
    chunk_id = _clean_string(row.get("id"))
    return QueryCitationSource(
        rank=rank,
        kind=kind,
        ref=ref,
        chunk_id=chunk_id,
        document_key=source_file or _clean_string(row.get("document_id")) or ref,
        source_file=source_file,
        heading_path=heading_path,
        page_start=_parse_int(metadata.get("page_start")),
        page_end=_parse_int(metadata.get("page_end")),
        snippet=_shorten_snippet(body),
    )


def _source_from_report(row: dict[str, Any], ref: str, rank: int) -> QueryCitationSource:
    title = _first_text(row, ["title", "heading", "name"]) or f"社区报告 {ref}"
    report_id = _first_text(row, ["id", "community"]) or ref
    snippet = _first_text(row, ["summary", "full_content", "content", "full_text", "description"])
    return QueryCitationSource(
        rank=rank,
        kind="graphrag_report",
        ref=ref,
        chunk_id=report_id,
        document_key=report_id,
        source_file="课程知识图谱报告",
        heading_path=title,
        page_start=None,
        page_end=None,
        snippet=_shorten_snippet(snippet),
    )


def _source_from_entity(row: dict[str, Any], ref: str, rank: int) -> QueryCitationSource:
    name = _first_text(row, ["title", "name", "id"]) or f"实体 {ref}"
    entity_id = _first_text(row, ["id", "human_readable_id"]) or ref
    snippet = _first_text(row, ["description", "summary", "text"])
    return QueryCitationSource(
        rank=rank,
        kind="graphrag_entity",
        ref=ref,
        chunk_id=entity_id,
        document_key=entity_id,
        source_file="课程知识图谱实体",
        heading_path=name,
        page_start=None,
        page_end=None,
        snippet=_shorten_snippet(snippet),
    )


def _source_from_relationship(row: dict[str, Any], ref: str, rank: int) -> QueryCitationSource:
    source = _first_text(row, ["source", "source_title", "source_name"])
    target = _first_text(row, ["target", "target_title", "target_name"])
    title = " -> ".join(part for part in [source, target] if part) or f"关系 {ref}"
    relationship_id = _first_text(row, ["id", "human_readable_id"]) or ref
    snippet = _first_text(row, ["description", "summary", "text"])
    return QueryCitationSource(
        rank=rank,
        kind="graphrag_relationship",
        ref=ref,
        chunk_id=relationship_id,
        document_key=relationship_id,
        source_file="课程知识图谱关系",
        heading_path=title,
        page_start=None,
        page_end=None,
        snippet=_shorten_snippet(snippet),
    )


def _should_add_global_fallback(
    mode: str | None,
    fallback_query: str | None,
    sources: list[QueryCitationSource],
) -> bool:
    normalized_mode = (mode or "").lower()
    if normalized_mode not in {"global", "drift"}:
        return False
    if not (fallback_query or "").strip():
        return False
    if normalized_mode == "drift":
        return True
    return not any(source.kind == "graphrag_citation" for source in sources)


def _is_drift_mode(mode: str | None) -> bool:
    return (mode or "").lower() == "drift"


def _rerank_sources(sources: list[QueryCitationSource]) -> list[QueryCitationSource]:
    return [replace(source, rank=index + 1) for index, source in enumerate(sources)]


def _rank_fallback_text_units(
    output_dir: Path | str | None,
    query: str,
    limit: int,
) -> list[dict[str, Any]]:
    if output_dir is None or limit <= 0:
        return []
    text_units_path = Path(output_dir) / "text_units.parquet"
    if not text_units_path.exists():
        return []
    frame = _read_parquet(text_units_path, columns=["id", "human_readable_id", "text", "document_id"])
    if frame is None:
        return []
    query_terms = _tokenize_for_score(query)
    if not query_terms:
        return []

    scored: list[tuple[int, int, dict[str, Any]]] = []
    for index, record in enumerate(frame.to_dict(orient="records")):
        text = _clean_string(record.get("text"))
        score = _score_text(query_terms, text)
        if score <= 0:
            continue
        scored.append((score, index, record))
    scored.sort(key=lambda item: (-item[0], item[1]))
    return [record for _, _, record in scored[:limit]]


def _clean_string(value: Any) -> str:
    if value is None:
        return ""
    try:
        missing = pd.isna(value)
        if bool(missing):
            return ""
    except (TypeError, ValueError):
        pass
    return str(value).strip()


def _normalize_ref_value(value: Any) -> str:
    text = _clean_string(value)
    if re.fullmatch(r"\d+\.0", text):
        return text[:-2]
    return text


def _first_text(row: dict[str, Any], columns: list[str]) -> str:
    for column in columns:
        value = _clean_string(row.get(column))
        if value:
            return value
    return ""


def _tokenize_for_score(text: str) -> set[str]:
    terms: set[str] = set()
    normalized = text.lower()
    for token in re.findall(r"[a-z0-9_]{2,}", normalized):
        terms.add(token)
    for sequence in re.findall(r"[\u4e00-\u9fff]{2,}", normalized):
        if len(sequence) <= 8:
            terms.add(sequence)
        for size in (2, 3, 4):
            for index in range(0, max(0, len(sequence) - size + 1)):
                terms.add(sequence[index:index + size])
    return terms


def _score_text(query_terms: set[str], text: str) -> int:
    normalized = text.lower()
    return sum(1 for term in query_terms if term in normalized)


def _split_text_unit_metadata(text: str) -> tuple[dict[str, str], str]:
    matches = list(_METADATA_KEY_RE.finditer(text))
    if not matches:
        return {}, text.strip()

    metadata: dict[str, str] = {}
    body = ""
    for index, match in enumerate(matches):
        key = match.group(1)
        value_start = match.end()
        value_end = matches[index + 1].start() if index + 1 < len(matches) else len(text)
        raw_value = text[value_start:value_end].strip(" \n\t")
        if index == len(matches) - 1:
            value_part, separator, body_tail = raw_value.partition(". ")
            metadata[key] = value_part.strip(" .\n\t")
            body = body_tail.strip() if separator else ""
        else:
            metadata[key] = raw_value.strip(" .\n\t")
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


def _replace_data_blocks(
    answer: str,
    sources: list[QueryCitationSource],
    *,
    fallback_source_ranks: list[int] | None = None,
) -> str:
    ref_to_rank = {
        _rank_key(_source_segment_kind(source.kind), source.ref): source.rank
        for source in sources
        if _source_segment_kind(source.kind)
    }

    def replace(match: re.Match[str]) -> str:
        block = match.group(1)
        ranks: list[int] = []
        for raw_kind, raw_ids in _DATA_SEGMENT_RE.findall(block):
            kind = _normalize_data_segment_kind(raw_kind)
            for token in _DATA_TOKEN_RE.findall(raw_ids):
                rank = ref_to_rank.get(_rank_key(kind, token))
                if rank is not None and rank not in ranks:
                    ranks.append(rank)
        if not ranks and fallback_source_ranks:
            for rank in fallback_source_ranks:
                if rank not in ranks:
                    ranks.append(rank)
        if ranks:
            joined = "、".join(str(rank) for rank in ranks)
            return f"[来源 {joined}]"
        return "[已参考课程知识库]"

    return _DATA_BLOCK_RE.sub(replace, answer)


def _rank_key(kind: str, ref: str) -> str:
    return f"{kind}:{ref}"


def _source_segment_kind(kind: str) -> str:
    return {
        "graphrag_citation": "sources",
        "graphrag_report": "reports",
        "graphrag_entity": "entities",
        "graphrag_relationship": "relationships",
        "hybrid_text_unit": "hybrid",
    }.get(kind, "")


def _normalize_data_segment_kind(raw_kind: str) -> str:
    normalized = re.sub(r"\s+", " ", raw_kind or "").strip().lower()
    if normalized in {"source", "sources", "text unit", "text units"}:
        return "sources"
    if normalized == "hybrid":
        return "hybrid"
    return normalized


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
