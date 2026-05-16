from __future__ import annotations

import os
from dataclasses import dataclass
from urllib.parse import urlparse

import requests


@dataclass(frozen=True, slots=True)
class SynthesisClientConfig:
    api_base: str | None = None
    api_key: str | None = None
    model: str | None = None
    timeout_seconds: float = 120.0

    @classmethod
    def from_env(cls) -> "SynthesisClientConfig":
        return cls(
            api_base=os.environ.get("GRAPHRAG_API_BASE"),
            api_key=os.environ.get("GRAPHRAG_CHAT_API_KEY"),
            model=os.environ.get("GRAPHRAG_QUERY_MODEL"),
            timeout_seconds=float(os.environ.get("CKQA_HYBRID_V0_SYNTHESIS_TIMEOUT_SECONDS") or 120.0),
        )


class OpenAICompatibleSynthesisClient:
    def __init__(self, config: SynthesisClientConfig | None = None) -> None:
        self.config = config or SynthesisClientConfig.from_env()
        if not (self.config.api_base or "").strip():
            raise ValueError("Hybrid synthesis requires GRAPHRAG_API_BASE.")
        if not (self.config.api_key or "").strip():
            raise ValueError("Hybrid synthesis requires GRAPHRAG_CHAT_API_KEY.")
        if not (self.config.model or "").strip():
            raise ValueError("Hybrid synthesis requires GRAPHRAG_QUERY_MODEL.")

    def complete(self, prompt: str) -> str:
        url = f"{_normalize_api_base(self.config.api_base or '')}/v1/chat/completions"
        response = requests.post(
            url,
            headers={
                "Authorization": f"Bearer {self.config.api_key}",
                "Content-Type": "application/json",
            },
            json={
                "model": self.config.model,
                "messages": [{"role": "user", "content": prompt}],
                "temperature": 0.1,
            },
            timeout=self.config.timeout_seconds,
        )
        response.raise_for_status()
        payload = response.json()
        try:
            return str(payload["choices"][0]["message"]["content"])
        except (KeyError, IndexError, TypeError) as exc:
            raise RuntimeError("OpenAI-compatible synthesis response is missing choices[0].message.content") from exc


def _normalize_api_base(api_base: str) -> str:
    normalized = api_base.rstrip("/")
    if urlparse(normalized).path.rstrip("/").endswith("/v1"):
        return normalized[:-3]
    return normalized
