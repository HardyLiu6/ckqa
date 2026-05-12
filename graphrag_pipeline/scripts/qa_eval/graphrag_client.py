from __future__ import annotations

import logging
import time
from dataclasses import dataclass
import ipaddress
from typing import Any
from urllib.parse import urlparse

import requests


LOGGER = logging.getLogger(__name__)

SUPPORTED_GRAPHRAG_MODELS = frozenset(
    {
        "graphrag-local-search:latest",
        "graphrag-global-search:latest",
        "graphrag-drift-search:latest",
        "graphrag-basic-search:latest",
    }
)


class GraphRagApiError(RuntimeError):
    """GraphRAG OpenAI-compatible API call failed after retries."""


@dataclass(frozen=True, slots=True)
class QueryResult:
    answer: str
    total_tokens: int | None
    elapsed_seconds: float
    raw: dict[str, Any]


class OpenAICompatibleClient:
    def __init__(
        self,
        *,
        base_url: str = "http://127.0.0.1:8000",
        request_timeout_seconds: float = 120.0,
        max_retries: int = 3,
        backoff_seconds: float = 2.0,
        allow_arbitrary_models: bool = False,
    ) -> None:
        self._base_url = base_url.rstrip("/")
        self._timeout = request_timeout_seconds
        self._max_retries = max(1, max_retries)
        self._backoff_seconds = max(0.0, backoff_seconds)
        self._allow_arbitrary_models = allow_arbitrary_models
        self._session = requests.Session()
        if _should_bypass_env_proxy(self._base_url):
            self._session.trust_env = False

    def query(
        self,
        *,
        model: str,
        prompt: str,
        api_key: str | None = None,
        temperature: float = 0.0,
    ) -> QueryResult:
        if not self._allow_arbitrary_models and model not in SUPPORTED_GRAPHRAG_MODELS:
            raise ValueError(f"unsupported GraphRAG model: {model}")

        url = f"{self._base_url}/v1/chat/completions"
        payload = {
            "model": model,
            "messages": [{"role": "user", "content": prompt}],
            "temperature": temperature,
            "stream": False,
        }
        headers: dict[str, str] = {}
        if api_key:
            headers["Authorization"] = f"Bearer {api_key}"

        last_error: str | None = None
        started_at = time.perf_counter()
        for attempt in range(1, self._max_retries + 1):
            try:
                response = self._session.post(
                    url,
                    json=payload,
                    headers=headers,
                    timeout=self._timeout,
                )
                response.raise_for_status()
                body = response.json()
                answer = body["choices"][0]["message"]["content"]
                if not isinstance(answer, str):
                    raise ValueError("response content must be a string")
                usage = body.get("usage") or {}
                return QueryResult(
                    answer=answer,
                    total_tokens=usage.get("total_tokens"),
                    elapsed_seconds=time.perf_counter() - started_at,
                    raw=body,
                )
            except (KeyError, IndexError, TypeError, ValueError, requests.RequestException) as exc:
                last_error = str(exc)
                LOGGER.warning(
                    "GraphRAG query failed model=%s attempt=%s/%s error=%s",
                    model,
                    attempt,
                    self._max_retries,
                    last_error,
                )
                if attempt < self._max_retries and self._backoff_seconds:
                    time.sleep(self._backoff_seconds * attempt)

        raise GraphRagApiError(
            f"query failed after {self._max_retries} attempts: {last_error or 'unknown error'}"
        )


GraphRagClient = OpenAICompatibleClient


def _should_bypass_env_proxy(base_url: str) -> bool:
    hostname = (urlparse(base_url).hostname or "").strip()
    if not hostname:
        return False
    if hostname.casefold() == "localhost":
        return True
    try:
        return ipaddress.ip_address(hostname).is_loopback
    except ValueError:
        return False
