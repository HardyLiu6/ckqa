from __future__ import annotations

import json
from unittest.mock import patch

import pytest
import requests

from graphrag_pipeline.scripts.qa_eval.graphrag_client import (
    GraphRagApiError,
    OpenAICompatibleClient,
    QueryResult,
    SUPPORTED_GRAPHRAG_MODELS,
)


class _FakeResponse:
    def __init__(self, status_code: int, payload: dict):
        self.status_code = status_code
        self._payload = payload
        self.text = json.dumps(payload)

    def json(self) -> dict:
        return self._payload

    def raise_for_status(self) -> None:
        if self.status_code >= 400:
            raise requests.HTTPError(self.text)


def test_supported_graphrag_models_contains_four_baseline_modes():
    assert SUPPORTED_GRAPHRAG_MODELS == frozenset(
        {
            "graphrag-local-search:latest",
            "graphrag-global-search:latest",
            "graphrag-drift-search:latest",
            "graphrag-basic-search:latest",
        }
    )


def test_client_returns_answer_text():
    payload = {
        "choices": [
            {
                "message": {
                    "role": "assistant",
                    "content": "DBSCAN 的核心超参数是 eps 和 MinPts。",
                }
            }
        ],
        "usage": {"total_tokens": 128},
    }
    with patch.object(
        requests.Session, "post", return_value=_FakeResponse(200, payload)
    ) as mock:
        client = OpenAICompatibleClient(
            base_url="http://127.0.0.1:8000",
            request_timeout_seconds=5,
            allow_arbitrary_models=False,
        )
        result: QueryResult = client.query(
            model="graphrag-local-search:latest", prompt="测试"
        )
        assert result.answer.startswith("DBSCAN")
        assert result.total_tokens == 128
        assert result.elapsed_seconds >= 0
        assert result.raw == payload
        assert mock.call_count == 1
        assert mock.call_args.kwargs["json"]["messages"] == [
            {"role": "user", "content": "测试"}
        ]


def test_client_bypasses_env_proxy_for_loopback_base_url():
    client = OpenAICompatibleClient(base_url="http://127.0.0.1:8000")
    assert client._session.trust_env is False


def test_client_rejects_non_string_answer_content():
    payload = {
        "choices": [{"message": {"role": "assistant", "content": None}}],
        "usage": {"total_tokens": 5},
    }
    with patch.object(requests.Session, "post", return_value=_FakeResponse(200, payload)):
        client = OpenAICompatibleClient(
            base_url="http://127.0.0.1:8000",
            request_timeout_seconds=5,
            max_retries=1,
            backoff_seconds=0.0,
        )
        with pytest.raises(GraphRagApiError):
            client.query(model="graphrag-local-search:latest", prompt="测试")


def test_client_raises_after_max_retries():
    with patch.object(
        requests.Session, "post", return_value=_FakeResponse(500, {"error": "boom"})
    ) as mock:
        client = OpenAICompatibleClient(
            base_url="http://127.0.0.1:8000",
            request_timeout_seconds=5,
            max_retries=2,
            backoff_seconds=0.0,
            allow_arbitrary_models=False,
        )
        with pytest.raises(GraphRagApiError):
            client.query(model="graphrag-local-search:latest", prompt="测试")
        assert mock.call_count == 2


def test_client_retries_transient_failure_then_returns_answer():
    payload = {
        "choices": [{"message": {"role": "assistant", "content": "ok"}}],
        "usage": {"total_tokens": 3},
    }
    with patch.object(
        requests.Session,
        "post",
        side_effect=[requests.Timeout("slow"), _FakeResponse(200, payload)],
    ) as mock:
        client = OpenAICompatibleClient(
            base_url="http://127.0.0.1:8000",
            request_timeout_seconds=5,
            max_retries=2,
            backoff_seconds=0.0,
            allow_arbitrary_models=False,
        )
        result = client.query(model="graphrag-basic-search:latest", prompt="测试")
        assert result.answer == "ok"
        assert mock.call_count == 2


def test_client_rejects_unknown_graphrag_model():
    client = OpenAICompatibleClient(
        base_url="http://127.0.0.1:8000",
        allow_arbitrary_models=False,
    )
    with pytest.raises(ValueError):
        client.query(model="not-a-real-model", prompt="测试")


def test_client_allows_arbitrary_judge_models():
    payload = {
        "choices": [{"message": {"role": "assistant", "content": "{\"score\":1}"}}],
        "usage": {"total_tokens": 50},
    }
    with patch.object(requests.Session, "post", return_value=_FakeResponse(200, payload)):
        judge_client = OpenAICompatibleClient(
            base_url="http://judge.example.com",
            request_timeout_seconds=10,
            allow_arbitrary_models=True,
        )
        result = judge_client.query(model="gpt-4o-mini", prompt="...", api_key="sk-test")
        assert result.answer == "{\"score\":1}"
