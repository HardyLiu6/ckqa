from __future__ import annotations

import re
from collections.abc import Iterable
from dataclasses import dataclass, field
from pathlib import Path

import pandas as pd

from graphrag_pipeline.scripts.qa_eval.test_set_schema import TEXT_UNIT_ID_PREFIX_LEN


@dataclass(frozen=True, slots=True)
class TextUnitSnippet:
    prefix: str
    text: str


@dataclass(slots=True)
class TextUnitLookup:
    by_prefix: dict[str, str]

    @classmethod
    def from_parquet(cls, path: Path | str) -> "TextUnitLookup":
        return load_text_unit_lookup(Path(path))

    def get(self, prefix: str) -> str | None:
        key = (prefix or "").strip()[:TEXT_UNIT_ID_PREFIX_LEN]
        return self.by_prefix.get(key)

    def fetch_many(self, prefixes: list[str]) -> list[TextUnitSnippet]:
        snippets: list[TextUnitSnippet] = []
        for raw in prefixes:
            key = (raw or "").strip()[:TEXT_UNIT_ID_PREFIX_LEN]
            if not key:
                continue
            text = self.by_prefix.get(key)
            if text is None:
                continue
            snippets.append(TextUnitSnippet(prefix=key, text=text))
        return snippets

    def render_for_prompt(self, prefixes: list[str], *, max_chars: int = 600) -> str:
        if not prefixes:
            return "（未提供 gold_text_unit_ids）"
        lines: list[str] = []
        for snippet in self.fetch_many(prefixes):
            body = snippet.text.strip()
            if len(body) > max_chars:
                body = body[: max(0, max_chars - 3)] + "..."
            lines.append(f"[{snippet.prefix}] {body}")
        return "\n\n".join(lines) if lines else "（gold_text_unit_ids 在 text_units.parquet 中未命中）"


_DATA_BLOCK_RE = re.compile(r"\[Data:\s*([^\]]+)\]", re.IGNORECASE)
_DATA_SEGMENT_RE = re.compile(
    r"(Sources|Entities|Relationships|Reports)\s*\(([^)]*)\)",
    re.IGNORECASE,
)
_DATA_NUMBER_RE = re.compile(r"\d+")
_TEXT_UNIT_TOKEN_RE = re.compile(r"[0-9a-fA-F]{8,}")


@dataclass(slots=True)
class DataCitationLookup:
    sources_by_human_id: dict[str, list[str]] = field(default_factory=dict)
    entities_by_human_id: dict[str, list[str]] = field(default_factory=dict)
    relationships_by_human_id: dict[str, list[str]] = field(default_factory=dict)
    reports_by_human_id: dict[str, list[str]] = field(default_factory=dict)

    def resolve_answer_refs(self, answer: str) -> list[str]:
        refs: list[str] = []
        seen: set[str] = set()
        for kind, tokens in extract_data_citations_from_answer(answer).items():
            mapping = self._mapping_for_kind(kind)
            for token in tokens:
                for prefix in mapping.get(token, []):
                    if prefix in seen:
                        continue
                    seen.add(prefix)
                    refs.append(prefix)
        return refs

    def _mapping_for_kind(self, kind: str) -> dict[str, list[str]]:
        normalized = kind.lower()
        if normalized == "sources":
            return self.sources_by_human_id
        if normalized == "entities":
            return self.entities_by_human_id
        if normalized == "relationships":
            return self.relationships_by_human_id
        if normalized == "reports":
            return self.reports_by_human_id
        return {}


def extract_data_citations_from_answer(answer: str) -> dict[str, list[str]]:
    citations: dict[str, list[str]] = {}
    for block in _DATA_BLOCK_RE.findall(answer):
        for raw_kind, raw_ids in _DATA_SEGMENT_RE.findall(block):
            kind = raw_kind.lower()
            bucket = citations.setdefault(kind, [])
            for token in _DATA_NUMBER_RE.findall(raw_ids):
                if token not in bucket:
                    bucket.append(token)
    return citations


def load_text_unit_lookup(parquet_path: Path) -> TextUnitLookup:
    frame = pd.read_parquet(parquet_path, columns=["id", "text"])
    by_prefix: dict[str, str] = {}
    for raw_id, text in zip(frame["id"].astype(str), frame["text"].astype(str)):
        prefix = raw_id[:TEXT_UNIT_ID_PREFIX_LEN]
        if prefix:
            by_prefix.setdefault(prefix, text)
    return TextUnitLookup(by_prefix=by_prefix)


def load_data_citation_lookup(text_units_parquet_path: Path) -> DataCitationLookup:
    index_output_dir = text_units_parquet_path.parent
    sources_by_human_id = _load_sources_by_human_id(text_units_parquet_path)
    entities_by_human_id = _load_refs_by_human_id(index_output_dir / "entities.parquet")
    relationships_by_human_id = _load_refs_by_human_id(index_output_dir / "relationships.parquet")
    reports_by_human_id = _load_reports_by_human_id(
        index_output_dir / "community_reports.parquet",
        index_output_dir / "communities.parquet",
    )
    return DataCitationLookup(
        sources_by_human_id=sources_by_human_id,
        entities_by_human_id=entities_by_human_id,
        relationships_by_human_id=relationships_by_human_id,
        reports_by_human_id=reports_by_human_id,
    )


def _load_sources_by_human_id(text_units_parquet_path: Path) -> dict[str, list[str]]:
    frame = pd.read_parquet(text_units_parquet_path, columns=["id", "human_readable_id"])
    by_human_id: dict[str, list[str]] = {}
    for raw_id, human_readable_id in zip(frame["id"], frame["human_readable_id"]):
        human_id = _normalize_human_readable_id(human_readable_id)
        if human_id is None:
            continue
        prefix = str(raw_id)[:TEXT_UNIT_ID_PREFIX_LEN]
        if prefix:
            bucket = by_human_id.setdefault(human_id, [])
            if prefix not in bucket:
                bucket.append(prefix)
    return by_human_id


def _load_refs_by_human_id(parquet_path: Path) -> dict[str, list[str]]:
    if not parquet_path.exists():
        return {}
    frame = pd.read_parquet(parquet_path, columns=["human_readable_id", "text_unit_ids"])
    by_human_id: dict[str, list[str]] = {}
    for human_readable_id, text_unit_ids in zip(frame["human_readable_id"], frame["text_unit_ids"]):
        human_id = _normalize_human_readable_id(human_readable_id)
        if human_id is None:
            continue
        prefixes = _coerce_prefixes(text_unit_ids)
        if not prefixes:
            continue
        bucket = by_human_id.setdefault(human_id, [])
        for prefix in prefixes:
            if prefix not in bucket:
                bucket.append(prefix)
    return by_human_id


def _load_reports_by_human_id(
    community_reports_path: Path,
    communities_path: Path,
) -> dict[str, list[str]]:
    if not community_reports_path.exists() or not communities_path.exists():
        return {}

    communities = pd.read_parquet(communities_path, columns=["community", "text_unit_ids"])
    text_units_by_community: dict[str, list[str]] = {}
    for community, text_unit_ids in zip(communities["community"], communities["text_unit_ids"]):
        community_id = _normalize_human_readable_id(community)
        if community_id is None:
            continue
        text_units_by_community[community_id] = _coerce_prefixes(text_unit_ids)

    community_reports = pd.read_parquet(
        community_reports_path,
        columns=["human_readable_id", "community"],
    )
    by_human_id: dict[str, list[str]] = {}
    for human_readable_id, community in zip(
        community_reports["human_readable_id"],
        community_reports["community"],
    ):
        report_id = _normalize_human_readable_id(human_readable_id)
        community_id = _normalize_human_readable_id(community)
        if report_id is None or community_id is None:
            continue
        prefixes = text_units_by_community.get(community_id, [])
        if not prefixes:
            continue
        bucket = by_human_id.setdefault(report_id, [])
        for prefix in prefixes:
            if prefix not in bucket:
                bucket.append(prefix)
    return by_human_id


def _normalize_human_readable_id(value: object) -> str | None:
    if value is None:
        return None
    try:
        if pd.isna(value):
            return None
    except (TypeError, ValueError):
        pass
    if isinstance(value, float) and value.is_integer():
        return str(int(value))
    text = str(value).strip()
    return text or None


def _coerce_prefixes(value: object) -> list[str]:
    if value is None:
        return []
    try:
        if pd.isna(value):
            return []
    except (TypeError, ValueError):
        pass

    if isinstance(value, str):
        items: Iterable[object] = [value]
    elif isinstance(value, Iterable):
        items = value
    elif hasattr(value, "tolist"):
        items = value.tolist()
    else:
        items = [value]

    prefixes: list[str] = []
    for item in items:
        for token in _TEXT_UNIT_TOKEN_RE.findall(str(item)):
            prefix = token[:TEXT_UNIT_ID_PREFIX_LEN]
            if prefix and prefix not in prefixes:
                prefixes.append(prefix)
    return prefixes
