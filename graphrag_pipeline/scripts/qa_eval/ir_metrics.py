from __future__ import annotations

from collections.abc import Iterable

import ir_measures
from ir_measures import AP, Qrel, RR, R, ScoredDoc, nDCG

from graphrag_pipeline.scripts.qa_eval.test_set_schema import TEXT_UNIT_ID_PREFIX_LEN


def _prefix_set(values: Iterable[str]) -> set[str]:
    return {str(value).strip()[:TEXT_UNIT_ID_PREFIX_LEN] for value in values if str(value).strip()}


def _ranked_prefixes(values: Iterable[str]) -> list[str]:
    ranked: list[str] = []
    for value in values:
        prefix = str(value).strip()[:TEXT_UNIT_ID_PREFIX_LEN]
        if prefix and prefix not in ranked:
            ranked.append(prefix)
    return ranked


def _zero_scores(question_id: str, cutoffs: list[int]) -> dict[str, float | str]:
    out: dict[str, float | str] = {"question_id": question_id}
    for cutoff in cutoffs:
        out[f"citation_recall_at_{cutoff}"] = 0.0
        out[f"citation_ndcg_at_{cutoff}"] = 0.0
    out["citation_rr"] = 0.0
    out["citation_map"] = 0.0
    return out


def score_ranked_citations(
    *,
    question_id: str,
    ranked_refs: list[str],
    gold_refs: list[str],
    cutoffs: list[int] | None = None,
) -> dict[str, float | str]:
    cutoffs = cutoffs or [1, 3, 5]
    gold = _prefix_set(gold_refs)
    ranked = _ranked_prefixes(ranked_refs)
    if not gold or not ranked:
        return _zero_scores(question_id, cutoffs)

    qrels = [Qrel(question_id, ref, 1) for ref in gold]
    run = [ScoredDoc(question_id, ref, float(len(ranked) - index)) for index, ref in enumerate(ranked)]
    metrics = []
    for cutoff in cutoffs:
        metrics.extend([R @ cutoff, nDCG @ cutoff])
    metrics.extend([RR, AP])
    values = ir_measures.calc_aggregate(metrics, qrels, run)

    out: dict[str, float | str] = {"question_id": question_id}
    for cutoff in cutoffs:
        out[f"citation_recall_at_{cutoff}"] = round(float(values[R @ cutoff]), 4)
        out[f"citation_ndcg_at_{cutoff}"] = round(float(values[nDCG @ cutoff]), 4)
    out["citation_rr"] = round(float(values[RR]), 4)
    out["citation_map"] = round(float(values[AP]), 4)
    return out
