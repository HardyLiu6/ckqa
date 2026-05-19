from __future__ import annotations

import tomllib
from pathlib import Path


PYPROJECT_PATH = Path(__file__).resolve().parents[1] / "pyproject.toml"


def test_eval_extra_declares_algorithmic_metric_dependencies():
    pyproject = tomllib.loads(PYPROJECT_PATH.read_text(encoding="utf-8"))
    extras = pyproject["project"]["optional-dependencies"]

    eval_extra = "\n".join(extras["eval"])
    assert "FlagEmbedding" in eval_extra
    assert "rouge-score" in eval_extra
    assert "jieba" in eval_extra
    assert "ir-measures" in eval_extra
    assert "scikit-learn" in eval_extra


def test_heavy_and_compat_metrics_are_kept_optional():
    pyproject = tomllib.loads(PYPROJECT_PATH.read_text(encoding="utf-8"))
    extras = pyproject["project"]["optional-dependencies"]

    eval_extra = "\n".join(extras["eval"])
    assert "bert-score" not in eval_extra
    assert "bert-score" in "\n".join(extras["semantic-compat"])
    assert "ragas" in extras
    assert "factuality-extra" in extras
    assert "ragas" not in "\n".join(extras["eval"])
