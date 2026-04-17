#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
OpenAI 兼容 LLM 客户端
=====================
"""

from __future__ import annotations

import json
import os
import time
from contextlib import closing
from dataclasses import dataclass
import ipaddress
from pathlib import Path
from typing import Any
from urllib.parse import urlparse

import requests

try:
    from dotenv import load_dotenv
except ImportError:  # pragma: no cover - 依赖缺失时退化为纯环境变量
    load_dotenv = None


_DOTENV_LOADED = False


class LlmClientError(RuntimeError):
    """统一封装 LLM 调用异常。"""


@dataclass(frozen=True)
class OpenAICompatibleLlmConfig:
    api_base: str
    api_key: str
    model: str
    timeout_seconds: int = 120
    retries: int = 2
    enable_json_mode: bool = True


class OpenAICompatibleLlmClient:
    """最小 OpenAI 兼容 chat.completions 客户端。"""

    def __init__(self, config: OpenAICompatibleLlmConfig) -> None:
        self._config = config

    @property
    def model_name(self) -> str:
        return self._config.model

    def create_chat_completion(
        self,
        *,
        messages: list[dict[str, str]],
        model: str,
        temperature: float,
        max_tokens: int | None,
        metadata: dict[str, Any],
    ) -> str:
        request_model = model or self._config.model
        endpoint = _build_chat_completions_url(self._config.api_base)
        payload = {
            "model": request_model,
            "messages": messages,
            "temperature": temperature,
            "stream": False,
        }
        if max_tokens is not None:
            payload["max_tokens"] = max_tokens

        return self._post_with_retry(
            endpoint=endpoint,
            payload=payload,
            metadata=metadata,
            enable_json_mode=self._config.enable_json_mode,
        )

    def _post_with_retry(
        self,
        *,
        endpoint: str,
        payload: dict[str, Any],
        metadata: dict[str, Any],
        enable_json_mode: bool,
    ) -> str:
        headers = {"Content-Type": "application/json"}
        if self._config.api_key:
            headers["Authorization"] = f"Bearer {self._config.api_key}"

        attempts = max(1, self._config.retries + 1)
        last_error = ""
        current_payload = dict(payload)
        if enable_json_mode:
            current_payload["response_format"] = {"type": "json_object"}

        for attempt in range(1, attempts + 1):
            try:
                with closing(_session_for_endpoint(endpoint)) as session:
                    response = session.post(
                        endpoint,
                        headers=headers,
                        data=json.dumps(current_payload),
                        timeout=self._config.timeout_seconds,
                    )
            except requests.RequestException as exc:
                last_error = f"请求失败：{exc}"
                if attempt >= attempts:
                    break
                time.sleep(min(2 * attempt, 5))
                continue

            if response.status_code >= 400:
                if response.status_code == 400 and "response_format" in current_payload:
                    # 一些 OpenAI 兼容网关不支持 response_format，自动降级一次。
                    current_payload = dict(payload)
                    continue
                last_error = f"HTTP {response.status_code}: {_truncate(response.text)}"
                if attempt >= attempts:
                    break
                time.sleep(min(2 * attempt, 5))
                continue

            try:
                response_payload = response.json()
            except ValueError as exc:
                last_error = f"响应不是合法 JSON：{exc}"
                if attempt >= attempts:
                    break
                time.sleep(min(2 * attempt, 5))
                continue

            content = _extract_message_content(response_payload)
            if content:
                return content

            last_error = f"响应中缺少 message.content，metadata={metadata}"
            if attempt >= attempts:
                break
            time.sleep(min(2 * attempt, 5))

        raise LlmClientError(last_error or "未知 LLM 调用错误")


def build_llm_client(
    *,
    root: Path,
    model: str | None,
    timeout_seconds: int,
    retries: int,
) -> OpenAICompatibleLlmClient:
    _load_project_dotenv_once(root)
    api_base = (os.environ.get("GRAPHRAG_API_BASE") or os.environ.get("OPENAI_API_BASE") or "").strip()
    api_key = (
        os.environ.get("GRAPHRAG_CHAT_API_KEY")
        or os.environ.get("OPENAI_API_KEY")
        or os.environ.get("ONEAPI_API_KEY")
        or ""
    ).strip()
    resolved_model = (model or os.environ.get("GRAPHRAG_CHAT_MODEL") or os.environ.get("OPENAI_MODEL") or "").strip()

    if not api_base:
        raise LlmClientError("未配置 GRAPHRAG_API_BASE / OPENAI_API_BASE")
    if not resolved_model:
        raise LlmClientError("未配置模型名，请通过 --model 或环境变量传入")

    return OpenAICompatibleLlmClient(
        OpenAICompatibleLlmConfig(
            api_base=api_base,
            api_key=api_key,
            model=resolved_model,
            timeout_seconds=timeout_seconds,
            retries=retries,
            enable_json_mode=True,
        )
    )


def _load_project_dotenv_once(root: Path) -> None:
    global _DOTENV_LOADED
    if _DOTENV_LOADED or load_dotenv is None:
        return
    _DOTENV_LOADED = True
    load_dotenv(root / ".env", override=False)


def _build_chat_completions_url(api_base: str) -> str:
    normalized = api_base.rstrip("/")
    if normalized.endswith("/chat/completions"):
        return normalized
    if normalized.endswith("/v1"):
        return f"{normalized}/chat/completions"
    return f"{normalized}/v1/chat/completions"


def _session_for_endpoint(endpoint: str) -> requests.Session:
    session = requests.Session()
    if _should_bypass_env_proxy(endpoint):
        session.trust_env = False
    return session


def _should_bypass_env_proxy(endpoint: str) -> bool:
    hostname = (urlparse(endpoint).hostname or "").strip()
    if not hostname:
        return False
    if hostname.casefold() == "localhost":
        return True
    try:
        return ipaddress.ip_address(hostname).is_loopback
    except ValueError:
        return False


def _extract_message_content(payload: dict[str, Any]) -> str:
    choices = payload.get("choices")
    if not isinstance(choices, list) or not choices:
        return ""

    message = choices[0].get("message") if isinstance(choices[0], dict) else None
    if not isinstance(message, dict):
        return ""

    content = message.get("content")
    if isinstance(content, str):
        return content
    if isinstance(content, list):
        parts: list[str] = []
        for item in content:
            if isinstance(item, dict):
                text = item.get("text")
                if isinstance(text, str):
                    parts.append(text)
        return "\n".join(part for part in parts if part.strip()).strip()
    return ""


def _truncate(text: str, limit: int = 500) -> str:
    collapsed = " ".join((text or "").split())
    if len(collapsed) <= limit:
        return collapsed
    return f"{collapsed[: limit - 1].rstrip()}…"
