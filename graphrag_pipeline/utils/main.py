"""
GraphRAG FastAPI 服务器

该脚本提供一个 OpenAI 兼容的 FastAPI 接口。
当前实现仅保留 graphrag CLI 查询路径，不再尝试易漂移的内部 Python API。
"""

from __future__ import annotations

import asyncio
import ipaddress
import json
import logging
import os
import re
import time
import uuid
from typing import Dict, List, Optional, Union
from urllib.parse import urlparse

import uvicorn
from fastapi import FastAPI, HTTPException
from fastapi.middleware.cors import CORSMiddleware
from fastapi.responses import JSONResponse, StreamingResponse
from pydantic import BaseModel, Field

from api_runtime_config import load_api_runtime_config
from runtime_defaults import PROJECT_ROOT, PROJECT_VERSION, TARGET_GRAPHRAG_VERSION


logging.basicConfig(
    level=logging.INFO,
    format="%(asctime)s - %(name)s - %(levelname)s - %(message)s",
)
logger = logging.getLogger(__name__)


APP_CONFIG = load_api_runtime_config()
GRAPHRAG_ROOT = PROJECT_ROOT
OUTPUT_DIR = APP_CONFIG.output_dir
INPUT_DIR = APP_CONFIG.input_dir
LANCEDB_URI = APP_CONFIG.lancedb_uri


def _should_bypass_env_proxy(api_base: str | None) -> bool:
    """仅在模型网关位于本机回环地址时绕过环境代理。"""
    hostname = (urlparse(api_base or "").hostname or "").strip()
    if not hostname:
        return False
    if hostname.casefold() == "localhost":
        return True
    try:
        return ipaddress.ip_address(hostname).is_loopback
    except ValueError:
        return False


def _merge_no_proxy_hosts(current_value: str | None, extra_hosts: list[str]) -> str:
    merged: list[str] = []
    for raw_item in (current_value or "").split(","):
        item = raw_item.strip()
        if item and item not in merged:
            merged.append(item)
    for host in extra_hosts:
        if host not in merged:
            merged.append(host)
    return ",".join(merged)


def _build_query_env() -> dict[str, str]:
    """为 graphrag CLI 查询构建稳定的运行环境。"""
    env = os.environ.copy()
    env["GRAPHRAG_OUTPUT_DIR"] = str(OUTPUT_DIR)
    env["GRAPHRAG_STORAGE_DIR"] = str(OUTPUT_DIR)
    env["GRAPHRAG_LANCEDB_URI"] = LANCEDB_URI
    if _should_bypass_env_proxy(env.get("GRAPHRAG_API_BASE")):
        no_proxy = _merge_no_proxy_hosts(
            env.get("NO_PROXY") or env.get("no_proxy"),
            ["127.0.0.1", "localhost", "::1"],
        )
        env["NO_PROXY"] = no_proxy
        env["no_proxy"] = no_proxy
    return env


def _build_query_cmd(method: str, prompt: str) -> list[str]:
    """按查询模式构造 graphrag CLI 参数。"""
    cmd = [
        "graphrag",
        "query",
        "--root",
        ".",
        "--method",
        method,
    ]
    if method == "global":
        cmd.extend(
            [
                "--community-level",
                str(APP_CONFIG.global_search_community_level),
                "--response-type",
                APP_CONFIG.global_search_response_type,
            ]
        )
        cmd.append(
            "--dynamic-community-selection"
            if APP_CONFIG.global_search_dynamic_selection
            else "--no-dynamic-community-selection"
        )
    cmd.append(prompt)
    return cmd


async def run_graphrag_query_cli(method: str, prompt: str) -> str:
    """通过 graphrag CLI 执行查询。"""
    cmd = _build_query_cmd(method, prompt)

    process = await asyncio.create_subprocess_exec(
        *cmd,
        cwd=str(GRAPHRAG_ROOT),
        env=_build_query_env(),
        stdout=asyncio.subprocess.PIPE,
        stderr=asyncio.subprocess.PIPE,
    )
    stdout, stderr = await process.communicate()

    if process.returncode != 0:
        err = stderr.decode("utf-8", errors="ignore").strip()
        raise RuntimeError(f"graphrag query 执行失败(method={method}): {err}")

    output = stdout.decode("utf-8", errors="ignore").strip()
    if not output:
        return ""

    lines = [line for line in output.splitlines() if line.strip()]
    return "\n".join(lines)


class Message(BaseModel):
    role: str
    content: str


class ChatCompletionRequest(BaseModel):
    model: str
    messages: List[Message]
    temperature: Optional[float] = 1.0
    top_p: Optional[float] = 1.0
    n: Optional[int] = 1
    stream: Optional[bool] = False
    stop: Optional[Union[str, List[str]]] = None
    max_tokens: Optional[int] = None
    presence_penalty: Optional[float] = 0
    frequency_penalty: Optional[float] = 0
    logit_bias: Optional[Dict[str, float]] = None
    user: Optional[str] = None


class ChatCompletionResponseChoice(BaseModel):
    index: int
    message: Message
    finish_reason: Optional[str] = None


class Usage(BaseModel):
    prompt_tokens: int
    completion_tokens: int
    total_tokens: int


class ChatCompletionResponse(BaseModel):
    id: str = Field(default_factory=lambda: f"chatcmpl-{uuid.uuid4().hex}")
    object: str = "chat.completion"
    created: int = Field(default_factory=lambda: int(time.time()))
    model: str
    choices: List[ChatCompletionResponseChoice]
    usage: Usage
    system_fingerprint: Optional[str] = None


def format_response(response: str) -> str:
    """格式化响应文本，便于前端直接展示。"""
    paragraphs = re.split(r"\n{2,}", response)
    formatted_paragraphs: list[str] = []
    for para in paragraphs:
        if "```" in para:
            parts = para.split("```")
            for index, part in enumerate(parts):
                if index % 2 == 1:
                    parts[index] = f"\n```\n{part.strip()}\n```\n"
            para = "".join(parts)
        else:
            para = para.replace(". ", ".\n")
        formatted_paragraphs.append(para.strip())
    return "\n\n".join(formatted_paragraphs)


app = FastAPI(
    title="GraphRAG API Server",
    description=(
        "基于 Microsoft GraphRAG 的知识图谱问答服务"
        f"（当前依赖基线 {TARGET_GRAPHRAG_VERSION}，采用 CLI 查询模式）"
    ),
    version=PROJECT_VERSION,
)

app.add_middleware(
    CORSMiddleware,
    allow_origins=["*"],
    allow_credentials=True,
    allow_methods=["*"],
    allow_headers=["*"],
)


async def full_model_search(prompt: str) -> str:
    """组合本地检索与全局检索结果。"""
    local_text = format_response(await run_graphrag_query_cli("local", prompt))
    global_text = format_response(await run_graphrag_query_cli("global", prompt))

    formatted_result = "# 综合搜索结果:\n\n"
    formatted_result += "## 本地检索结果:\n"
    formatted_result += local_text + "\n\n"
    formatted_result += "## 全局检索结果:\n"
    formatted_result += global_text + "\n\n"
    return formatted_result


async def _resolve_query_response(model: str, prompt: str) -> str:
    if model == "full-model:latest":
        return await full_model_search(prompt)
    if model == "graphrag-global-search:latest":
        return format_response(await run_graphrag_query_cli("global", prompt))
    return format_response(await run_graphrag_query_cli("local", prompt))


@app.post("/v1/chat/completions")
async def chat_completions(request: ChatCompletionRequest):
    """OpenAI 兼容的聊天完成接口。"""
    try:
        logger.info("收到请求，模型: %s", request.model)
        prompt = request.messages[-1].content
        logger.info("处理提示: %s...", prompt[:100])

        formatted_response = await _resolve_query_response(request.model, prompt)
        logger.info("搜索完成，响应长度: %s", len(formatted_response))

        if request.stream:

            async def generate_stream():
                chunk_id = f"chatcmpl-{uuid.uuid4().hex}"
                lines = formatted_response.split("\n")
                for line in lines:
                    chunk = {
                        "id": chunk_id,
                        "object": "chat.completion.chunk",
                        "created": int(time.time()),
                        "model": request.model,
                        "choices": [
                            {
                                "index": 0,
                                "delta": {"content": line + "\n"},
                                "finish_reason": None,
                            }
                        ],
                    }
                    yield f"data: {json.dumps(chunk)}\n\n"
                    await asyncio.sleep(0.05)

                final_chunk = {
                    "id": chunk_id,
                    "object": "chat.completion.chunk",
                    "created": int(time.time()),
                    "model": request.model,
                    "choices": [
                        {
                            "index": 0,
                            "delta": {},
                            "finish_reason": "stop",
                        }
                    ],
                }
                yield f"data: {json.dumps(final_chunk)}\n\n"
                yield "data: [DONE]\n\n"

            return StreamingResponse(generate_stream(), media_type="text/event-stream")

        response = ChatCompletionResponse(
            model=request.model,
            choices=[
                ChatCompletionResponseChoice(
                    index=0,
                    message=Message(role="assistant", content=formatted_response),
                    finish_reason="stop",
                )
            ],
            usage=Usage(
                prompt_tokens=len(prompt.split()),
                completion_tokens=len(formatted_response.split()),
                total_tokens=len(prompt.split()) + len(formatted_response.split()),
            ),
        )
        return JSONResponse(content=response.model_dump())
    except Exception as exc:
        logger.error("处理请求时出错: %s", exc)
        raise HTTPException(status_code=500, detail=str(exc)) from exc


@app.get("/v1/models")
async def list_models():
    """获取可用模型列表。"""
    current_time = int(time.time())
    models = [
        {
            "id": "graphrag-local-search:latest",
            "object": "model",
            "created": current_time - 100000,
            "owned_by": "graphrag",
        },
        {
            "id": "graphrag-global-search:latest",
            "object": "model",
            "created": current_time - 95000,
            "owned_by": "graphrag",
        },
        {
            "id": "full-model:latest",
            "object": "model",
            "created": current_time - 80000,
            "owned_by": "combined",
        },
    ]
    return JSONResponse(content={"object": "list", "data": models})


@app.get("/health")
async def health_check():
    """健康检查接口。"""
    return {
        "status": "healthy",
        "version": PROJECT_VERSION,
        "graphrag_version_target": TARGET_GRAPHRAG_VERSION,
        "compat_mode": "cli_query",
        "local_search_ready": True,
        "global_search_ready": True,
        "output_dir": INPUT_DIR,
        "lancedb_uri": LANCEDB_URI,
    }


@app.get("/")
async def root():
    """根路径。"""
    return {
        "message": "GraphRAG API Server",
        "version": PROJECT_VERSION,
        "docs": "/docs",
        "health": "/health",
    }


if __name__ == "__main__":
    logger.info(
        (
            "在 %s:%s 启动 GraphRAG API 服务器（CLI 查询模式，"
            "global: community_level=%s, dynamic_selection=%s, response_type=%s）"
        ),
        APP_CONFIG.api_host,
        APP_CONFIG.api_port,
        APP_CONFIG.global_search_community_level,
        APP_CONFIG.global_search_dynamic_selection,
        APP_CONFIG.global_search_response_type,
    )
    uvicorn.run(app, host=APP_CONFIG.api_host, port=APP_CONFIG.api_port)
