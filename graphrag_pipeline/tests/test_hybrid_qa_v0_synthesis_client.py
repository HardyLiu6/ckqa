from __future__ import annotations

from unittest.mock import Mock, patch

import pytest

from graphrag_pipeline.scripts.hybrid_qa.synthesis_client import (
    OpenAICompatibleSynthesisClient,
    SynthesisClientConfig,
)


def test_synthesis_client_posts_to_openai_compatible_chat_completions():
    response = Mock()
    response.json.return_value = {"choices": [{"message": {"content": "合成答案"}}]}
    response.raise_for_status.return_value = None

    with patch(
        "graphrag_pipeline.scripts.hybrid_qa.synthesis_client.requests.post",
        return_value=response,
    ) as post:
        answer = OpenAICompatibleSynthesisClient(
            SynthesisClientConfig(
                api_base="http://127.0.0.1:3000",
                api_key="test-key",
                model="deepseek-v4-flash",
                timeout_seconds=12.0,
            )
        ).complete("请基于证据回答")

    assert answer == "合成答案"
    post.assert_called_once()
    assert post.call_args.args[0] == "http://127.0.0.1:3000/v1/chat/completions"
    payload = post.call_args.kwargs["json"]
    assert payload["model"] == "deepseek-v4-flash"
    assert payload["messages"][0]["content"] == "请基于证据回答"
    assert post.call_args.kwargs["timeout"] == 12.0


def test_synthesis_client_accepts_api_base_with_v1_suffix():
    response = Mock()
    response.json.return_value = {"choices": [{"message": {"content": "合成答案"}}]}
    response.raise_for_status.return_value = None

    with patch(
        "graphrag_pipeline.scripts.hybrid_qa.synthesis_client.requests.post",
        return_value=response,
    ) as post:
        OpenAICompatibleSynthesisClient(
            SynthesisClientConfig(
                api_base="http://127.0.0.1:3301/v1",
                api_key="test-key",
                model="deepseek-v4-pro",
            )
        ).complete("请基于证据回答")

    assert post.call_args.args[0] == "http://127.0.0.1:3301/v1/chat/completions"


def test_synthesis_client_requires_api_base_key_and_model():
    with pytest.raises(ValueError, match="GRAPHRAG_API_BASE"):
        OpenAICompatibleSynthesisClient(
            SynthesisClientConfig(api_base="", api_key="key", model="model")
        )

    with pytest.raises(ValueError, match="GRAPHRAG_CHAT_API_KEY"):
        OpenAICompatibleSynthesisClient(
            SynthesisClientConfig(api_base="http://127.0.0.1:3000", api_key="", model="model")
        )

    with pytest.raises(ValueError, match="GRAPHRAG_QUERY_MODEL"):
        OpenAICompatibleSynthesisClient(
            SynthesisClientConfig(api_base="http://127.0.0.1:3000", api_key="key", model="")
        )
