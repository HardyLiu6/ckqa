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
import sys
import time
import uuid
from typing import Dict, List, Literal, Optional, Union
from urllib.parse import urlparse
from zoneinfo import ZoneInfo

import uvicorn
from fastapi import FastAPI, HTTPException
from fastapi.middleware.cors import CORSMiddleware
from fastapi.responses import JSONResponse, StreamingResponse
from pydantic import BaseModel, Field

from api_runtime_config import load_api_runtime_config
from runtime_defaults import PROJECT_ROOT, PROJECT_VERSION, TARGET_GRAPHRAG_VERSION
from query_task_manager import QueryTaskManager, QueryTaskRequest


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
BUILD_RUNS_ROOT = APP_CONFIG.build_runs_root
API_TIME_ZONE = ZoneInfo("Asia/Shanghai")
SUPPORTED_QUERY_MODELS: dict[str, str] = {
    "graphrag-local-search:latest": "local",
    "graphrag-global-search:latest": "global",
    "graphrag-drift-search:latest": "drift",
    "graphrag-basic-search:latest": "basic",
}


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


def _build_query_env(request: QueryTaskRequest | None = None) -> dict[str, str]:
    """为 graphrag CLI 查询构建稳定的运行环境。"""
    env = os.environ.copy()
    has_task_data_dir = request is not None and request.data_dir is not None
    output_dir = request.data_dir if has_task_data_dir else OUTPUT_DIR
    env["GRAPHRAG_OUTPUT_DIR"] = str(output_dir)
    env["GRAPHRAG_STORAGE_DIR"] = str(output_dir)
    env["GRAPHRAG_LANCEDB_URI"] = str(output_dir / "lancedb") if has_task_data_dir else LANCEDB_URI
    if _should_bypass_env_proxy(env.get("GRAPHRAG_API_BASE")):
        no_proxy = _merge_no_proxy_hosts(
            env.get("NO_PROXY") or env.get("no_proxy"),
            ["127.0.0.1", "localhost", "::1"],
        )
        env["NO_PROXY"] = no_proxy
        env["no_proxy"] = no_proxy
    return env


def _build_query_cmd(request: QueryTaskRequest) -> list[str]:
    """按查询模式构造 graphrag CLI 参数。"""
    cmd = [
        sys.executable,
        "-m",
        "graphrag",
        "query",
        "--root",
        ".",
    ]
    if request.data_dir is not None:
        cmd.extend(["--data", str(request.data_dir)])
    cmd.extend([
        "--method",
        request.mode,
        request.prompt,
    ])
    return cmd


QUERY_TASK_MANAGER = QueryTaskManager(
    command_factory=_build_query_cmd,
    env_factory=_build_query_env,
    cwd=GRAPHRAG_ROOT,
    build_runs_root=BUILD_RUNS_ROOT,
)


async def run_graphrag_query_cli(method: str, prompt: str) -> str:
    """通过 graphrag CLI 执行查询。"""
    request = QueryTaskRequest(method, prompt, None, None, None)
    cmd = _build_query_cmd(request)

    process = await asyncio.create_subprocess_exec(
        *cmd,
        cwd=str(GRAPHRAG_ROOT),
        env=_build_query_env(request),
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


class QueryTaskCreateRequest(BaseModel):
    mode: Literal["local", "global", "drift", "basic"] = Field(default="local")
    prompt: str
    indexRunId: int | None = None
    dataDirUri: str | None = None


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


def _serialize_api_local_datetime(value) -> str | None:
    """将内部 UTC aware 时间转成上海时间的无偏移 LocalDateTime 字符串。"""
    if value is None:
        return None
    if getattr(value, "tzinfo", None) is None:
        return value.isoformat(timespec="seconds")
    return value.astimezone(API_TIME_ZONE).replace(tzinfo=None).isoformat(timespec="seconds")


def _serialize_task_snapshot(snapshot) -> dict[str, object]:
    """将任务快照转成稳定的 JSON 返回结构。"""
    return {
        "pythonTaskId": snapshot.python_task_id,
        "taskStatus": snapshot.task_status,
        "progressStage": snapshot.progress_stage,
        "processAlive": snapshot.process_alive,
        "createdAt": _serialize_api_local_datetime(snapshot.created_at),
        "startedAt": _serialize_api_local_datetime(snapshot.started_at),
        "lastHeartbeatAt": _serialize_api_local_datetime(snapshot.last_heartbeat_at),
        "finishedAt": _serialize_api_local_datetime(snapshot.finished_at),
        "latestLogs": list(snapshot.latest_logs or []),
        "resultText": snapshot.result_text,
        "errorMessage": snapshot.error_message,
        "returnCode": snapshot.return_code,
        "indexRunId": snapshot.index_run_id,
        "dataDirUri": snapshot.data_dir_uri,
    }


def create_app(task_manager: QueryTaskManager | None = None) -> FastAPI:
    """创建 FastAPI 应用，允许测试注入任务管理器。"""
    active_task_manager = task_manager or QUERY_TASK_MANAGER

    app = FastAPI(
        title="GraphRAG API Server",
        description=(
            "基于 Microsoft GraphRAG 的知识图谱问答服务"
            f"（当前依赖基线 {TARGET_GRAPHRAG_VERSION}，采用 CLI 查询模式）"
        ),
        version=PROJECT_VERSION,
    )

    app.state.query_task_manager = active_task_manager

    app.add_middleware(
        CORSMiddleware,
        allow_origins=["*"],
        allow_credentials=True,
        allow_methods=["*"],
        allow_headers=["*"],
    )

    @app.post("/v1/query-tasks")
    async def submit_query_task(request: QueryTaskCreateRequest):
        """提交一个异步查询任务。"""
        snapshot = await active_task_manager.create_task(
            request.mode,
            request.prompt,
            index_run_id=request.indexRunId,
            data_dir_uri=request.dataDirUri,
        )
        return JSONResponse(
            content={
                "pythonTaskId": snapshot.python_task_id,
                "taskStatus": snapshot.task_status,
                "progressStage": snapshot.progress_stage,
                "createdAt": _serialize_api_local_datetime(snapshot.created_at),
            }
        )

    @app.get("/v1/query-tasks/{taskId}")
    async def get_query_task(taskId: str):
        """查询异步任务状态。"""
        snapshot = active_task_manager.get_snapshot(taskId)
        if snapshot is None:
            raise HTTPException(status_code=404, detail="Query task not found")
        return JSONResponse(content=_serialize_task_snapshot(snapshot))

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
        except HTTPException:
            raise
        except ValueError as exc:
            logger.warning("收到不支持的模型请求: %s", exc)
            raise HTTPException(status_code=400, detail=str(exc)) from exc
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
                "id": "graphrag-drift-search:latest",
                "object": "model",
                "created": current_time - 90000,
                "owned_by": "graphrag",
            },
            {
                "id": "graphrag-basic-search:latest",
                "object": "model",
                "created": current_time - 85000,
                "owned_by": "graphrag",
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
            "drift_search_ready": True,
            "basic_search_ready": True,
            "output_dir": INPUT_DIR,
            "lancedb_uri": LANCEDB_URI,
            "build_runs_root": str(BUILD_RUNS_ROOT),
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

    # 注册提示词调优路由
    try:
        from .prompt_tuning_service import register_prompt_tuning_routes
    except ImportError:
        from prompt_tuning_service import register_prompt_tuning_routes
    register_prompt_tuning_routes(app)

    return app


async def _resolve_query_response(model: str, prompt: str) -> str:
    if model == "full-model:latest":
        raise ValueError("模型 full-model:latest 已归档为后续扩展模式，当前请使用 local、global、drift 或 basic")

    method = SUPPORTED_QUERY_MODELS.get(model)
    if method is None:
        raise ValueError(f"不支持的模型: {model}")
    return format_response(await run_graphrag_query_cli(method, prompt))


app = create_app()


if __name__ == "__main__":
    logger.info("在 %s:%s 启动 GraphRAG API 服务器（CLI 查询模式）", APP_CONFIG.api_host, APP_CONFIG.api_port)
    uvicorn.run(app, host=APP_CONFIG.api_host, port=APP_CONFIG.api_port)
