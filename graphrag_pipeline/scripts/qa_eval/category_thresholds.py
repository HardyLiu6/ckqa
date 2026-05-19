from __future__ import annotations

from dataclasses import dataclass
from typing import Mapping

from graphrag_pipeline.scripts.qa_eval.test_set_schema import QuestionCategory


@dataclass(frozen=True, slots=True)
class LengthThreshold:
    too_short: int
    expected_min: int
    expected_max: int
    too_long: int


CATEGORY_LENGTH_THRESHOLDS: Mapping[QuestionCategory, LengthThreshold] = {
    QuestionCategory.FACTUAL_LOOKUP: LengthThreshold(20, 80, 250, 400),
    QuestionCategory.RELATION_REASONING: LengthThreshold(60, 150, 450, 600),
    QuestionCategory.CHAPTER_SUMMARY: LengthThreshold(120, 300, 800, 1200),
    QuestionCategory.GLOBAL_OVERVIEW: LengthThreshold(120, 300, 700, 1000),
}


def length_score(category: QuestionCategory, chars: int) -> float:
    threshold = CATEGORY_LENGTH_THRESHOLDS[category]
    if chars <= threshold.too_short or chars >= threshold.too_long:
        return 0.0
    if threshold.expected_min <= chars <= threshold.expected_max:
        return 1.0
    return 0.5
