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
from typing import Any, Iterable
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
class LlmCompletionResult:
    """统一封装一次 LLM 完成结果，便于执行器做诊断与重试决策。"""

    content: str
    finish_reason: str | None
    usage: dict[str, Any] | None
    request_mode: str
    reasoning_seen: bool = False
    raw_chunks: int = 0


@dataclass(frozen=True)
class OpenAICompatibleLlmConfig:
    api_base: str
    api_key: str
    model: str
    timeout_seconds: int = 120
    retries: int = 2
    enable_json_mode: bool = True
    stream_mode: str = "on"
    idle_timeout_seconds: int = 30


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
        timeout_seconds: int | None = None,
    ) -> LlmCompletionResult:
        request_model = model or self._config.model
        endpoint = _build_chat_completions_url(self._config.api_base)
        use_stream = self._config.stream_mode == "on"
        payload = {
            "model": request_model,
            "messages": messages,
            "temperature": temperature,
            "stream": use_stream,
        }
        if max_tokens is not None:
            payload["max_tokens"] = max_tokens
        if use_stream:
            payload["stream_options"] = {"include_usage": True}

        return self._post_with_retry(
            endpoint=endpoint,
            payload=payload,
            metadata=metadata,
            enable_json_mode=self._config.enable_json_mode,
            timeout_seconds=timeout_seconds,
        )

    def _post_with_retry(
        self,
        *,
        endpoint: str,
        payload: dict[str, Any],
        metadata: dict[str, Any],
        enable_json_mode: bool,
        timeout_seconds: int | None,
    ) -> LlmCompletionResult:
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
                        timeout=_resolve_timeout(
                            total_timeout_seconds=timeout_seconds or self._config.timeout_seconds,
                            idle_timeout_seconds=self._config.idle_timeout_seconds,
                            is_streaming=bool(current_payload.get("stream")),
                        ),
                        stream=bool(current_payload.get("stream")),
                    )
            except requests.RequestException as exc:
                last_error = f"请求失败：{exc}"
                if attempt >= attempts:
                    break
                time.sleep(min(2 * attempt, 5))
                continue

            with closing(response):
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
                    if current_payload.get("stream"):
                        result = _parse_stream_events(
                            response.iter_lines(decode_unicode=False),
                            max_total_seconds=timeout_seconds or self._config.timeout_seconds,
                        )
                    else:
                        response_payload = response.json()
                        result = _parse_non_stream_response(response_payload, request_mode="sync")
                except LlmClientError as exc:
                    last_error = str(exc)
                    if attempt >= attempts:
                        break
                    time.sleep(min(2 * attempt, 5))
                    continue
                except (ValueError, json.JSONDecodeError) as exc:
                    last_error = f"响应不是合法 JSON：{exc}"
                    if attempt >= attempts:
                        break
                    time.sleep(min(2 * attempt, 5))
                    continue

            if result.content:
                return result

            last_error = f"响应中缺少有效内容，metadata={metadata}"
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
    stream_mode: str = "on",
    idle_timeout_seconds: int = 30,
) -> OpenAICompatibleLlmClient:
    _load_project_dotenv_once(root)
    api_base = (os.environ.get("GRAPHRAG_API_BASE") or os.environ.get("OPENAI_API_BASE") or "").strip()
    api_key = (
        os.environ.get("GRAPHRAG_CHAT_API_KEY")
        or os.environ.get("OPENAI_API_KEY")
        or os.environ.get("ONEAPI_API_KEY")
        or ""
    ).strip()
    resolved_model = (
        model
        or os.environ.get("GRAPHRAG_EXTRACTION_MODEL")
        or os.environ.get("GRAPHRAG_CHAT_MODEL")
        or os.environ.get("OPENAI_MODEL")
        or ""
    ).strip()

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
            stream_mode=stream_mode,
            idle_timeout_seconds=idle_timeout_seconds,
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


def _parse_non_stream_response(payload: dict[str, Any], *, request_mode: str) -> LlmCompletionResult:
    choices = payload.get("choices")
    finish_reason = None
    if isinstance(choices, list) and choices and isinstance(choices[0], dict):
        raw_finish_reason = choices[0].get("finish_reason")
        if isinstance(raw_finish_reason, str):
            finish_reason = raw_finish_reason

    return LlmCompletionResult(
        content=_extract_message_content(payload),
        finish_reason=finish_reason,
        usage=payload.get("usage") if isinstance(payload.get("usage"), dict) else None,
        request_mode=request_mode,
    )


def _parse_stream_events(lines: Iterable[str], *, max_total_seconds: float | None = None) -> LlmCompletionResult:
    content_parts: list[str] = []
    finish_reason = None
    usage = None
    reasoning_seen = False
    raw_chunks = 0
    started_at = time.monotonic()

    for raw_line in lines:
        if max_total_seconds is not None and time.monotonic() - started_at >= max_total_seconds:
            raise LlmClientError(f"流式响应超过总超时 {max_total_seconds:g} 秒")

        line = raw_line.decode("utf-8") if isinstance(raw_line, bytes) else str(raw_line)
        stripped = line.strip()
        if not stripped or not stripped.startswith("data:"):
            continue

        payload_text = stripped[5:].strip()
        if payload_text == "[DONE]":
            break

        raw_chunks += 1
        event = json.loads(payload_text)
        if isinstance(event.get("usage"), dict):
            usage = event["usage"]

        choices = event.get("choices")
        if not isinstance(choices, list) or not choices or not isinstance(choices[0], dict):
            continue

        choice = choices[0]
        raw_finish_reason = choice.get("finish_reason")
        if isinstance(raw_finish_reason, str):
            finish_reason = raw_finish_reason

        delta = choice.get("delta") if isinstance(choice.get("delta"), dict) else {}
        if isinstance(delta.get("reasoning_content"), str) and delta.get("reasoning_content"):
            reasoning_seen = True
        if isinstance(delta.get("content"), str):
            content_parts.append(delta["content"])

    return LlmCompletionResult(
        content="".join(content_parts).strip(),
        finish_reason=finish_reason,
        usage=usage,
        request_mode="stream",
        reasoning_seen=reasoning_seen,
        raw_chunks=raw_chunks,
    )


def _resolve_timeout(
    *,
    total_timeout_seconds: int,
    idle_timeout_seconds: int,
    is_streaming: bool,
) -> int | tuple[int, int]:
    if not is_streaming:
        return total_timeout_seconds
    connect_timeout = max(1, min(total_timeout_seconds, 10))
    read_timeout = max(1, idle_timeout_seconds)
    return (connect_timeout, read_timeout)


def _truncate(text: str, limit: int = 500) -> str:
    collapsed = " ".join((text or "").split())
    if len(collapsed) <= limit:
        return collapsed
    return f"{collapsed[: limit - 1].rstrip()}…"
