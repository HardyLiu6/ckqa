from __future__ import annotations

from dataclasses import dataclass
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


def load_text_unit_lookup(parquet_path: Path) -> TextUnitLookup:
    frame = pd.read_parquet(parquet_path, columns=["id", "text"])
    by_prefix: dict[str, str] = {}
    for raw_id, text in zip(frame["id"].astype(str), frame["text"].astype(str)):
        prefix = raw_id[:TEXT_UNIT_ID_PREFIX_LEN]
        if prefix:
            by_prefix.setdefault(prefix, text)
    return TextUnitLookup(by_prefix=by_prefix)
