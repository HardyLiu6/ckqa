from __future__ import annotations

import re

from graphrag_pipeline.scripts.qa_eval.test_set_schema import TEXT_UNIT_ID_PREFIX_LEN
from graphrag_pipeline.scripts.qa_eval.text_unit_lookup import DataCitationLookup


TEXT_UNITS_BLOCK_RE = re.compile(r"Text Units?\s*\(([^)]*)\)", re.IGNORECASE)
REF_TOKEN_RE = re.compile(r"[A-Za-z0-9][A-Za-z0-9_-]{7,}")


def _append_unique(refs: list[str], raw: str) -> None:
    normalized = (raw or "").strip()[:TEXT_UNIT_ID_PREFIX_LEN]
    if normalized and normalized not in refs:
        refs.append(normalized)


def extract_text_unit_refs(
    answer: str,
    *,
    data_citation_lookup: DataCitationLookup | None = None,
) -> list[str]:
    refs: list[str] = []
    for block in TEXT_UNITS_BLOCK_RE.findall(answer or ""):
        for match in REF_TOKEN_RE.findall(block):
            _append_unique(refs, match)

    if data_citation_lookup is not None:
        for prefix in data_citation_lookup.resolve_answer_refs(answer or ""):
            _append_unique(refs, prefix)
    return refs
