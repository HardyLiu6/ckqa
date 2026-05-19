from __future__ import annotations

import argparse
import json
from dataclasses import asdict, dataclass
from pathlib import Path
from typing import Iterable

import numpy as np
import pandas as pd
from sklearn.cluster import KMeans
from sklearn.feature_extraction.text import TfidfVectorizer
from sklearn.metrics.pairwise import cosine_similarity


DEFAULT_OUTPUT_ROOT = Path("graphrag_pipeline/output")
DEFAULT_TEXT_UNITS_PATH = DEFAULT_OUTPUT_ROOT / "text_units.parquet"
DEFAULT_SEEDS_OUTPUT_PATH = Path("graphrag_pipeline/data/eval/qa_candidate_seeds.jsonl")


@dataclass(slots=True)
class SeedBuilderConfig:
    text_units_path: Path | None = None
    output_path: Path = DEFAULT_SEEDS_OUTPUT_PATH
    max_items: int = 80
    cluster_count: int = 12
    random_seed: int = 42


@dataclass(slots=True)
class CandidateSeed:
    id: str
    suggested_category: str
    source_text_unit_ids: list[str]
    source_preview: str
    coverage_cluster: int
    selection_score: float
    writing_hint: str


def _normalize_text(value: object) -> str:
    return str(value or "").strip().replace("\n", " ")


def _suggest_category(text: str) -> str:
    if any(token in text for token in ("总结", "概述", "流程", "体系", "方法论")):
        return "global_overview"
    if any(token in text for token in ("关系", "依赖", "对比", "区别", "联系")):
        return "relation_reasoning"
    if any(token in text for token in ("章", "节", "本章", "小结")):
        return "chapter_summary"
    return "factual_lookup"


def resolve_default_text_units_path(output_root: Path = DEFAULT_OUTPUT_ROOT) -> Path:
    top_level = output_root / "text_units.parquet"
    candidates = sorted(
        (
            path
            for path in [top_level, *output_root.glob("*/text_units.parquet")]
            if path.is_file()
        ),
        key=lambda path: (path.stat().st_mtime_ns, str(path)),
        reverse=True,
    )
    if not candidates:
        raise FileNotFoundError(
            f"未找到默认 text_units.parquet：{top_level} 或 {output_root}/*/text_units.parquet。"
            "请先确认现有 GraphRAG output，或通过 --text-units 显式指定。"
        )

    latest_mtime = candidates[0].stat().st_mtime_ns
    latest_candidates = [candidate for candidate in candidates if candidate.stat().st_mtime_ns == latest_mtime]
    if len(latest_candidates) > 1:
        formatted = ", ".join(str(candidate) for candidate in latest_candidates)
        raise FileNotFoundError(
            f"发现多个同样最新的 text_units.parquet 候选，无法安全选择：{formatted}。"
            "请通过 --text-units 显式指定。"
        )

    return candidates[0]


def _mmr_order(vectors, candidate_indices: Iterable[int], *, limit: int, lambda_mult: float = 0.7) -> list[int]:
    candidates = list(candidate_indices)
    if not candidates or limit <= 0:
        return []

    centroid = np.asarray(vectors[candidates].mean(axis=0)).reshape(1, -1)
    relevance = cosine_similarity(vectors[candidates], centroid).reshape(-1)
    selected: list[int] = []

    while candidates and len(selected) < limit:
        if not selected:
            best_local = int(np.argmax(relevance))
        else:
            similarity_to_selected = cosine_similarity(vectors[candidates], vectors[selected]).max(axis=1)
            mmr = lambda_mult * relevance - (1.0 - lambda_mult) * similarity_to_selected
            best_local = int(np.argmax(mmr))

        selected.append(candidates.pop(best_local))
        relevance = np.delete(relevance, best_local)

    return selected


def build_candidate_seeds(config: SeedBuilderConfig) -> list[CandidateSeed]:
    text_units_path = (
        resolve_default_text_units_path(DEFAULT_OUTPUT_ROOT)
        if config.text_units_path is None
        else config.text_units_path
    )

    df = pd.read_parquet(text_units_path)
    rows = [
        {"id": str(row["id"])[:12], "text": _normalize_text(row["text"])}
        for _, row in df.iterrows()
        if _normalize_text(row.get("text"))
    ]
    if not rows:
        raise ValueError(f"no usable text rows in {text_units_path}")

    texts = [row["text"] for row in rows]
    vectorizer = TfidfVectorizer(analyzer="char_wb", ngram_range=(2, 4), max_features=8000)
    vectors = vectorizer.fit_transform(texts)
    cluster_count = max(1, min(config.cluster_count, len(rows)))
    labels = KMeans(n_clusters=cluster_count, random_state=config.random_seed, n_init="auto").fit_predict(vectors)

    per_cluster_limit = max(1, int(np.ceil(config.max_items / cluster_count)))
    selected_indices: list[int] = []
    for cluster in range(cluster_count):
        indices = [idx for idx, label in enumerate(labels) if label == cluster]
        selected_indices.extend(_mmr_order(vectors, indices, limit=per_cluster_limit))
    selected_indices = selected_indices[: config.max_items]

    seeds: list[CandidateSeed] = []
    for serial, idx in enumerate(selected_indices, start=1):
        text = rows[idx]["text"]
        suggested_category = _suggest_category(text)
        seed = CandidateSeed(
            id=f"S{serial:03d}",
            suggested_category=suggested_category,
            source_text_unit_ids=[rows[idx]["id"]],
            source_preview=text[:220],
            coverage_cluster=int(labels[idx]),
            selection_score=1.0,
            writing_hint=f"围绕该 text unit 写 1 道 {suggested_category} 题，并补充 gold_answer_summary。",
        )
        seeds.append(seed)

    config.output_path.parent.mkdir(parents=True, exist_ok=True)
    config.output_path.write_text(
        "\n".join(json.dumps(asdict(seed), ensure_ascii=False) for seed in seeds) + "\n",
        encoding="utf-8",
    )
    return seeds


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--text-units", type=Path, default=None)
    parser.add_argument("--output", type=Path, default=DEFAULT_SEEDS_OUTPUT_PATH)
    parser.add_argument("--max-items", type=int, default=80)
    parser.add_argument("--cluster-count", type=int, default=12)
    parser.add_argument("--random-seed", type=int, default=42)
    args = parser.parse_args()

    seeds = build_candidate_seeds(
        SeedBuilderConfig(
            text_units_path=args.text_units,
            output_path=args.output,
            max_items=args.max_items,
            cluster_count=args.cluster_count,
            random_seed=args.random_seed,
        )
    )
    print(f"wrote {len(seeds)} candidate seeds to {args.output}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
