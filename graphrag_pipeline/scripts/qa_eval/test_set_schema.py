from __future__ import annotations

from enum import Enum

from pydantic import BaseModel, Field, field_validator


TEXT_UNIT_ID_PREFIX_LEN = 12


class QuestionCategory(str, Enum):
    FACTUAL_LOOKUP = "factual_lookup"
    RELATION_REASONING = "relation_reasoning"
    CHAPTER_SUMMARY = "chapter_summary"
    GLOBAL_OVERVIEW = "global_overview"


class QaTestItem(BaseModel):
    id: str = Field(pattern=r"^Q\d{3,5}$")
    category: QuestionCategory
    question: str = Field(min_length=4, max_length=400)
    gold_answer_summary: str = Field(min_length=2, max_length=600)
    gold_entities: list[str] = Field(default_factory=list)
    gold_text_unit_ids: list[str] = Field(default_factory=list)
    must_cite_terms: list[str] = Field(default_factory=list)
    negative_terms: list[str] = Field(default_factory=list)
    notes: str | None = Field(default=None, max_length=600)

    @field_validator("gold_entities", "must_cite_terms", "negative_terms", mode="before")
    @classmethod
    def _strip_terms(cls, value: object) -> list[str]:
        if value is None:
            return []
        if not isinstance(value, list):
            return value
        return [str(item).strip() for item in value if item is not None and str(item).strip()]

    @field_validator("gold_text_unit_ids", mode="before")
    @classmethod
    def _normalize_text_unit_ids(cls, value: object) -> list[str]:
        if value is None:
            return []
        if not isinstance(value, list):
            return value
        normalized: list[str] = []
        for raw in value:
            if raw is None:
                continue
            trimmed = str(raw).strip()
            if trimmed:
                normalized.append(trimmed[:TEXT_UNIT_ID_PREFIX_LEN])
        return normalized
