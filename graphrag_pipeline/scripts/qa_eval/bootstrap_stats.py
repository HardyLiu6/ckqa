from __future__ import annotations

from itertools import combinations
from typing import Iterable

import numpy as np
import pandas as pd


def bootstrap_mean_ci(
    values: Iterable[float],
    *,
    iterations: int = 2000,
    seed: int = 42,
    alpha: float = 0.05,
) -> tuple[float, float, float]:
    arr = np.asarray([float(value) for value in values], dtype=float)
    if arr.size == 0:
        return 0.0, 0.0, 0.0

    rng = np.random.default_rng(seed)
    sampled_means = np.array(
        [rng.choice(arr, size=arr.size, replace=True).mean() for _ in range(iterations)],
        dtype=float,
    )
    low = float(np.quantile(sampled_means, alpha / 2))
    high = float(np.quantile(sampled_means, 1 - alpha / 2))
    return round(low, 4), round(float(arr.mean()), 4), round(high, 4)


def paired_bootstrap_diff(
    df: pd.DataFrame,
    *,
    mode_a: str,
    mode_b: str,
    metric: str,
    iterations: int = 2000,
    seed: int = 42,
) -> dict[str, float | str]:
    pivot = df.pivot_table(index="question_id", columns="mode", values=metric, aggfunc="mean").dropna()
    if mode_a not in pivot.columns or mode_b not in pivot.columns or pivot.empty:
        return {
            "mode_a": mode_a,
            "mode_b": mode_b,
            "metric": metric,
            "mean_diff": 0.0,
            "win_rate": 0.0,
            "ci_low": 0.0,
            "ci_high": 0.0,
        }

    diffs = (pivot[mode_a] - pivot[mode_b]).to_numpy(dtype=float)
    rng = np.random.default_rng(seed)
    sampled = np.array(
        [rng.choice(diffs, size=diffs.size, replace=True).mean() for _ in range(iterations)],
        dtype=float,
    )
    return {
        "mode_a": mode_a,
        "mode_b": mode_b,
        "metric": metric,
        "mean_diff": round(float(diffs.mean()), 4),
        "win_rate": round(float((sampled > 0).mean()), 4),
        "ci_low": round(float(np.quantile(sampled, 0.025)), 4),
        "ci_high": round(float(np.quantile(sampled, 0.975)), 4),
    }


def pairwise_bootstrap_table(df: pd.DataFrame, *, metric: str) -> list[dict[str, float | str]]:
    modes = sorted(str(mode) for mode in df["mode"].dropna().unique())
    return [paired_bootstrap_diff(df, mode_a=a, mode_b=b, metric=metric) for a, b in combinations(modes, 2)]
