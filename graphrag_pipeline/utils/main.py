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
import subprocess
import sys
import time
import uuid
from datetime import datetime
from pathlib import Path
from typing import Any, Dict, List, Literal, Optional, Union
from urllib.parse import urlparse
from zoneinfo import ZoneInfo

import uvicorn
from fastapi import FastAPI, HTTPException
from fastapi.encoders import jsonable_encoder
from fastapi.middleware.cors import CORSMiddleware
from fastapi.responses import JSONResponse, StreamingResponse
from pydantic import BaseModel, Field

from api_runtime_config import load_api_runtime_config
from runtime_defaults import PROJECT_ROOT, PROJECT_VERSION, TARGET_GRAPHRAG_VERSION
from query_task_manager import QueryTaskManager, QueryTaskRequest
from query_engine_history_poc import (
    QueryEngineHistoryPocAdapter,
    query_in_thread,
    load_history_poc_config_from_env,
)
from course_routing import (
    CourseProfileInput as CourseProfileRouteItem,
    ProfileHintsRequest as CourseRoutingProfileHintsRouteRequest,
    CourseRoutingService,
    RecommendRequest as CourseRoutingRecommendRouteRequest,
)


logging.basicConfig(
    level=logging.INFO,
    format="%(asctime)s - %(name)s - %(levelname)s - %(message)s",
)
logger = logging.getLogger(__name__)


APP_CONFIG = load_api_runtime_config()
GRAPHRAG_ROOT = PROJECT_ROOT
PACKAGE_PARENT = GRAPHRAG_ROOT.parent
if str(PACKAGE_PARENT) not in sys.path:
    sys.path.insert(0, str(PACKAGE_PARENT))
from query_streaming_adapter import NativeGraphRagStreamingAdapter, NativeStreamingConfig  # noqa: E402

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
    "graphrag-hybrid-v0-search:latest": "hybrid_v0",
}
_HYBRID_V0_ORCHESTRATORS: dict[str, Any] = {}


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
        request.retrieval_query or request.prompt,
    ])
    return cmd


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


class _CliGraphRagDraftClient:
    """为 Hybrid v0 提供同步 GraphRAG 草稿查询客户端。"""

    def __init__(self, *, timeout_seconds: float = 240.0, data_dir: Path | None = None) -> None:
        self.timeout_seconds = timeout_seconds
        self.data_dir = data_dir

    def query_basic(self, question: str):
        return self._query("basic", question)

    def query_local(self, question: str):
        return self._query("local", question)

    def _query(self, mode: str, prompt: str):
        from graphrag_pipeline.scripts.hybrid_qa.basic_local_client import GraphRagDraft

        request = QueryTaskRequest(mode, prompt, None, None, self.data_dir, retrieval_query=prompt)
        started_at = time.perf_counter()
        try:
            process = subprocess.run(
                _build_query_cmd(request),
                cwd=str(GRAPHRAG_ROOT),
                env=_build_query_env(request),
                check=False,
                capture_output=True,
                text=True,
                timeout=self.timeout_seconds,
            )
        except subprocess.TimeoutExpired as exc:
            elapsed_seconds = time.perf_counter() - started_at
            return GraphRagDraft(
                mode=mode,
                answer="",
                elapsed_seconds=elapsed_seconds,
                error=f"graphrag query 超时(method={mode}, timeout={self.timeout_seconds}s): {exc}",
            )
        elapsed_seconds = time.perf_counter() - started_at
        stdout = (process.stdout or "").strip()
        stderr = (process.stderr or "").strip()
        if process.returncode != 0:
            error = f"graphrag query 执行失败(method={mode}): {stderr or stdout}"
            return GraphRagDraft(mode=mode, answer="", elapsed_seconds=elapsed_seconds, error=error)
        lines = [line for line in stdout.splitlines() if line.strip()]
        return GraphRagDraft(mode=mode, answer="\n".join(lines), elapsed_seconds=elapsed_seconds)


def _parse_bool_env(value: str | None, *, default: bool = False) -> bool:
    if value is None:
        return default
    return value.strip().casefold() in {"1", "true", "yes", "y", "on"}


def _parse_int_env(value: str | None, *, default: int) -> int:
    if value is None:
        return default
    try:
        parsed = int(value)
    except ValueError:
        logger.warning("忽略无效整数环境变量值: %s", value)
        return default
    return parsed if parsed > 0 else default


def _parse_float_env(value: str | None, *, default: float) -> float:
    if value is None:
        return default
    try:
        parsed = float(value)
    except ValueError:
        logger.warning("忽略无效浮点环境变量值: %s", value)
        return default
    return parsed if parsed > 0 else default


def _get_hybrid_v0_orchestrator(output_dir: Path | None = None):
    """按需构建 Hybrid v0 orchestrator，避免 API 启动时加载重依赖。"""
    resolved_output_dir = (output_dir or OUTPUT_DIR).resolve()
    cache_key = str(resolved_output_dir)
    if cache_key in _HYBRID_V0_ORCHESTRATORS:
        return _HYBRID_V0_ORCHESTRATORS[cache_key]

    from graphrag_pipeline.scripts.hybrid_qa.bm25_text_units import build_text_unit_bm25
    from graphrag_pipeline.scripts.hybrid_qa.evidence_guardrail import (
        EvidenceGuardrailConfig,
        check_answer_supported_by_evidence,
    )
    from graphrag_pipeline.scripts.hybrid_qa.evidence_fusion import EvidenceFusionConfig, fuse_basic_and_bm25_evidence
    from graphrag_pipeline.scripts.hybrid_qa.evidence_selector import (
        V6EvidenceSelectorConfig,
        build_v6_hybrid_evidence_selector,
    )
    from graphrag_pipeline.scripts.hybrid_qa.orchestrator_v0 import HybridFallbackPolicy, HybridV0Orchestrator
    from graphrag_pipeline.scripts.hybrid_qa.synthesis_client import OpenAICompatibleSynthesisClient
    from graphrag_pipeline.scripts.qa_eval.text_unit_lookup import load_data_citation_lookup, load_text_unit_lookup

    text_units_path = resolved_output_dir / "text_units.parquet"
    if not text_units_path.exists():
        raise FileNotFoundError(f"Hybrid v0 需要 text_units.parquet，但未找到: {text_units_path}")
    data_citation_lookup = load_data_citation_lookup(text_units_path)
    text_unit_lookup = load_text_unit_lookup(text_units_path)

    graph_client = _CliGraphRagDraftClient(
        timeout_seconds=float(_parse_int_env(os.environ.get("CKQA_HYBRID_V0_CLI_TIMEOUT_SECONDS"), default=240)),
        data_dir=resolved_output_dir,
    )
    guardrail_config = EvidenceGuardrailConfig(
        bge_m3_model=os.environ.get("CKQA_BGE_M3_MODEL") or None,
        bge_device=os.environ.get("CKQA_BGE_M3_DEVICE") or None,
        bge_use_fp16=_parse_bool_env(os.environ.get("CKQA_BGE_M3_FP16"), default=False),
        bge_batch_size=_parse_int_env(os.environ.get("CKQA_BGE_M3_BATCH_SIZE"), default=8),
    )
    fusion_config = EvidenceFusionConfig(
        bm25_anchor_top_k=_parse_int_env(os.environ.get("CKQA_HYBRID_V0_BM25_ANCHOR_TOP_K"), default=2),
    )
    evidence_strategy = (os.environ.get("CKQA_HYBRID_V0_EVIDENCE_STRATEGY") or "v6").strip().casefold()
    if evidence_strategy == "v6":
        bm25 = build_v6_hybrid_evidence_selector(
            text_units_path,
            config=V6EvidenceSelectorConfig(
                top_k=_parse_int_env(os.environ.get("CKQA_HYBRID_V0_BM25_TOP_K"), default=8),
                k1=_parse_float_env(os.environ.get("CKQA_HYBRID_V0_BM25_K1"), default=1.5),
                b=_parse_float_env(os.environ.get("CKQA_HYBRID_V0_BM25_B"), default=0.75),
                enable_dense_rerank=_parse_bool_env(
                    os.environ.get("CKQA_HYBRID_V0_ENABLE_DENSE_RERANK"),
                    default=False,
                ),
                dense_rerank_candidate_pool_k=_parse_int_env(
                    os.environ.get("CKQA_HYBRID_V0_DENSE_RERANK_POOL_K"),
                    default=20,
                ),
                dense_rerank_model=os.environ.get("CKQA_BGE_M3_MODEL") or None,
                dense_rerank_device=os.environ.get("CKQA_BGE_M3_DEVICE") or None,
                dense_rerank_use_fp16=_parse_bool_env(os.environ.get("CKQA_BGE_M3_FP16"), default=False),
                dense_rerank_batch_size=_parse_int_env(os.environ.get("CKQA_BGE_M3_BATCH_SIZE"), default=8),
            ),
        )
    else:
        bm25 = build_text_unit_bm25(text_units_path, cache_dir=resolved_output_dir / ".hybrid_v0_cache")
    orchestrator = HybridV0Orchestrator(
        bm25=bm25,
        graph_client=graph_client,
        guardrail=lambda answer, evidence: check_answer_supported_by_evidence(
            answer,
            evidence,
            config=guardrail_config,
        ),
        llm_complete=lambda prompt: OpenAICompatibleSynthesisClient().complete(prompt),
        citation_ref_resolver=data_citation_lookup.resolve_answer_refs,
        evidence_fusion=lambda question, basic_answer, bm25_candidates, citation_ref_resolver: (
            fuse_basic_and_bm25_evidence(
                question=question,
                basic_answer=basic_answer,
                bm25_candidates=bm25_candidates,
                text_unit_lookup=text_unit_lookup,
                citation_ref_resolver=citation_ref_resolver,
                config=fusion_config,
            )
        ),
        fallback_policy=HybridFallbackPolicy(
            enable_basic_evidence_injection=_parse_bool_env(
                os.environ.get("CKQA_HYBRID_V0_ONE_SHOT_BASIC_INJECTION"),
                default=True,
            ),
            disable_synthesis=_parse_bool_env(
                os.environ.get("CKQA_HYBRID_V0_DISABLE_SYNTHESIS"),
                default=True,
            ),
            enable_local_fallback=_parse_bool_env(
                os.environ.get("CKQA_HYBRID_V0_ENABLE_LOCAL_FALLBACK"),
                default=False,
            )
        ),
    )
    _HYBRID_V0_ORCHESTRATORS[cache_key] = orchestrator
    return orchestrator


async def _run_hybrid_v0_answer(prompt_or_request: str | QueryTaskRequest):
    """运行 Hybrid v0 查询并保留答案诊断信息。"""
    if isinstance(prompt_or_request, QueryTaskRequest):
        prompt = prompt_or_request.retrieval_query or prompt_or_request.prompt
        output_dir = prompt_or_request.data_dir
        generation_context = prompt_or_request.generation_context
    else:
        prompt = prompt_or_request
        output_dir = None
        generation_context = None
    return await asyncio.to_thread(
        _get_hybrid_v0_orchestrator(output_dir).answer,
        prompt,
        generation_context,
    )


async def _run_hybrid_v0_query(prompt: str) -> str:
    """运行 Hybrid v0 查询并返回答案文本。"""
    result = await _run_hybrid_v0_answer(prompt)
    return result.answer


def _build_query_task_history_adapter() -> QueryEngineHistoryPocAdapter:
    """为正式 query task 策略构建 LocalSearch history adapter。"""
    return QueryEngineHistoryPocAdapter(
        root_dir=GRAPHRAG_ROOT,
        output_dir=OUTPUT_DIR,
        build_runs_root=BUILD_RUNS_ROOT,
        config=load_history_poc_config_from_env(default_enabled=True),
    )


def _build_native_streaming_adapter() -> NativeGraphRagStreamingAdapter:
    """为 `/v1/query-tasks` 构建 GraphRAG 原生 streaming adapter。"""
    return NativeGraphRagStreamingAdapter(
        root_dir=GRAPHRAG_ROOT,
        config=NativeStreamingConfig.from_env(),
        hybrid_orchestrator_factory=_get_hybrid_v0_orchestrator,
    )


def _run_native_streaming_answer(request: QueryTaskRequest):
    return _build_native_streaming_adapter().stream(request)


QUERY_TASK_MANAGER = QueryTaskManager(
    command_factory=_build_query_cmd,
    env_factory=_build_query_env,
    cwd=GRAPHRAG_ROOT,
    build_runs_root=BUILD_RUNS_ROOT,
    hybrid_answer_runner=_run_hybrid_v0_answer,
    history_adapter_factory=_build_query_task_history_adapter,
    native_streaming_runner=_run_native_streaming_answer,
    task_store_dir=os.getenv("GRAPHRAG_QUERY_TASK_STORE_DIR", str(GRAPHRAG_ROOT / "runtime" / "query-tasks")),
    task_store_retention_days=int(os.getenv("GRAPHRAG_QUERY_TASK_RETENTION_DAYS", "7")),
    task_store_retention_limit=int(os.getenv("GRAPHRAG_QUERY_TASK_RETENTION_LIMIT", "5000")),
    stream_replay_max_chars=int(os.getenv("CKQA_QA_PYTHON_STREAM_REPLAY_MAX_CHARS", "64000")),
)


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


class QueryEngineHistoryTurn(BaseModel):
    role: Literal["user", "assistant"]
    content: str = Field(min_length=1)


class QueryTaskCreateRequest(BaseModel):
    mode: Literal["local", "global", "drift", "basic", "hybrid_v0"] = Field(default="local")
    prompt: str | None = None
    retrievalQuery: str | None = None
    generationContext: str | None = None
    queryEngineStrategy: Literal["cli", "local_history"] = Field(default="cli")
    conversationHistory: List[QueryEngineHistoryTurn] = Field(default_factory=list)
    streamResponse: bool = False
    streamSource: Literal["native_graphrag", "none"] = Field(default="none")
    indexRunId: int | None = None
    dataDirUri: str | None = None

    def effective_prompt(self) -> str:
        return (self.retrievalQuery or self.prompt or "").strip()


class QueryEngineHistoryRequest(BaseModel):
    dataDirUri: str | None = None
    query: str = Field(min_length=1)
    conversationHistory: List[QueryEngineHistoryTurn] = Field(default_factory=list)
    maxTurns: int | None = Field(default=None, ge=1, le=20)
    userTurnsOnly: bool = True
    returnCandidateContext: bool | None = None


class HybridWarmupRequest(BaseModel):
    dataDirUri: str | None = None


class CourseRoutingUpsertRouteRequest(BaseModel):
    profiles: List[CourseProfileRouteItem] = Field(default_factory=list, min_length=1)


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
    hybrid_diagnostics: Optional[dict[str, Any]] = None


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
        "latestLogs": _progress_event_summaries(snapshot.latest_logs),
        "progressEvents": [item for item in list(snapshot.latest_logs or []) if isinstance(item, dict)],
        "resultText": snapshot.result_text,
        "sources": list(snapshot.sources or []),
        "errorMessage": snapshot.error_message,
        "returnCode": snapshot.return_code,
        "indexRunId": snapshot.index_run_id,
        "dataDirUri": snapshot.data_dir_uri,
        "retrievalQuery": snapshot.retrieval_query,
        "generationContext": snapshot.generation_context,
        "queryEngineStrategy": snapshot.query_engine_strategy,
        "conversationHistory": list(snapshot.conversation_history or []),
        "historyFallbackReason": snapshot.history_fallback_reason,
        "historyApplied": snapshot.history_applied,
        "historyTurnsUsed": snapshot.history_turns_used,
        "streamingEnabled": snapshot.streaming_enabled,
        "streamingProvider": snapshot.streaming_provider,
        "streamingFallbackReason": snapshot.streaming_fallback_reason,
        "streamedTextLength": snapshot.streamed_text_length,
        "partialResultText": snapshot.partial_result_text,
        "streamEventSeq": snapshot.stream_event_seq,
    }


def _progress_event_summaries(logs) -> list[str]:
    summaries: list[str] = []
    for item in list(logs or []):
        if isinstance(item, dict):
            summary = str(item.get("summary") or "").strip()
            if summary:
                summaries.append(summary)
        else:
            text = str(item or "").strip()
            if text:
                summaries.append(text)
    return summaries


def _sse_event(event_name: str, payload: dict[str, Any], event_seq: int | None = None) -> str:
    event_id = f"id: {event_seq}\n" if event_seq is not None else ""
    return f"{event_id}event: {event_name}\ndata: {json.dumps(payload, ensure_ascii=False, default=str)}\n\n"


def _hybrid_readiness_payload(data_dir, data_dir_uri: str | None) -> dict[str, object]:
    resolved_output_dir = (data_dir or OUTPUT_DIR).resolve()
    text_units_ready = (resolved_output_dir / "text_units.parquet").exists()
    cached = str(resolved_output_dir) in _HYBRID_V0_ORCHESTRATORS
    missing: list[str] = []
    if not text_units_ready:
        missing.append("text_units.parquet")
    ready = text_units_ready and cached
    return {
        "ready": ready,
        "status": "ready" if ready else "not_ready",
        "dataDirUri": data_dir_uri,
        "cached": cached,
        "textUnitsReady": text_units_ready,
        "missing": missing,
    }


def create_app(
    task_manager: QueryTaskManager | None = None,
    course_routing_service: CourseRoutingService | None = None,
) -> FastAPI:
    """创建 FastAPI 应用，允许测试注入任务管理器。"""
    active_task_manager = task_manager or QUERY_TASK_MANAGER
    active_course_routing_service = course_routing_service or CourseRoutingService.from_env()

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
        prompt = request.effective_prompt()
        if not prompt:
            raise HTTPException(status_code=422, detail="retrievalQuery or prompt is required")
        snapshot = await active_task_manager.create_task(
            request.mode,
            request.prompt or prompt,
            index_run_id=request.indexRunId,
            data_dir_uri=request.dataDirUri,
            retrieval_query=prompt,
            generation_context=request.generationContext,
            query_engine_strategy=request.queryEngineStrategy,
            conversation_history=[turn.model_dump() for turn in request.conversationHistory],
            stream_response=request.streamResponse,
            stream_source=request.streamSource,
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

    @app.get("/v1/query-tasks/{taskId}/events")
    async def stream_query_task_events(taskId: str, afterEventSeq: int = 0):
        """Java 后端内部消费的查询任务事件流。"""
        snapshot = active_task_manager.get_snapshot(taskId)
        if snapshot is None:
            raise HTTPException(status_code=404, detail="Query task not found")

        async def generate_events():
            yield _sse_event("ack", {"pythonTaskId": taskId})
            replay, queue, unsubscribe = active_task_manager.subscribe_events(taskId, after_event_seq=afterEventSeq)
            terminal = False
            try:
                for item in replay:
                    yield _sse_event(item["event"], item["data"], item.get("seq"))
                    if item["event"] in {"done", "error"}:
                        terminal = True
                current = active_task_manager.get_snapshot(taskId)
                if not terminal and current is not None and current.task_status in {"success", "failed"}:
                    yield _sse_event("status", _serialize_task_snapshot(current))
                    if current.task_status == "success":
                        yield _sse_event("sources", {"sources": list(current.sources or [])})
                        yield _sse_event("done", {"taskStatus": "success"})
                    else:
                        yield _sse_event("error", {"taskStatus": "failed", "message": current.error_message})
                    terminal = True
                while not terminal:
                    try:
                        item = await asyncio.wait_for(queue.get(), timeout=15)
                    except asyncio.TimeoutError:
                        yield _sse_event("heartbeat", {"pythonTaskId": taskId, "serverTime": _serialize_api_local_datetime(datetime.now(API_TIME_ZONE))})
                        continue
                    yield _sse_event(item["event"], item["data"], item.get("seq"))
                    if item["event"] in {"done", "error"}:
                        terminal = True
            finally:
                unsubscribe()

        return StreamingResponse(generate_events(), media_type="text/event-stream")

    def history_poc_adapter() -> QueryEngineHistoryPocAdapter:
        return QueryEngineHistoryPocAdapter(
            root_dir=GRAPHRAG_ROOT,
            output_dir=OUTPUT_DIR,
            build_runs_root=BUILD_RUNS_ROOT,
            config=load_history_poc_config_from_env(),
        )

    @app.get("/v1/experiments/query-engine-history/readiness")
    async def get_query_engine_history_readiness(dataDirUri: str | None = None):
        """实验性 GraphRAG LocalSearch conversation history readiness。"""
        readiness = history_poc_adapter().readiness(dataDirUri)
        return JSONResponse(content=readiness.to_dict())

    @app.post("/v1/experiments/query-engine-history/query")
    async def query_with_engine_history(request: QueryEngineHistoryRequest):
        """实验性 GraphRAG LocalSearch conversation history 查询。"""
        result = await query_in_thread(
            history_poc_adapter(),
            data_dir_uri=request.dataDirUri,
            query=request.query,
            conversation_history=[turn.model_dump() for turn in request.conversationHistory],
            max_turns=request.maxTurns,
            user_turns_only=request.userTurnsOnly,
            return_candidate_context=request.returnCandidateContext,
        )
        return JSONResponse(content=result.to_dict())

    @app.post("/v1/hybrid-v0/warmup")
    async def warmup_hybrid_v0(request: HybridWarmupRequest):
        """预热 Hybrid v0 的本地索引与 BM25/orchestrator cache，不调用 One API。"""
        try:
            data_dir = active_task_manager.resolve_data_dir_uri(request.dataDirUri)
        except ValueError as exc:
            raise HTTPException(status_code=400, detail=str(exc)) from exc
        payload = _hybrid_readiness_payload(data_dir, request.dataDirUri)
        if payload["textUnitsReady"]:
            try:
                _get_hybrid_v0_orchestrator(data_dir)
            except FileNotFoundError:
                return JSONResponse(content=payload)
            payload = _hybrid_readiness_payload(data_dir, request.dataDirUri)
            payload["cached"] = True
            payload["ready"] = True
            payload["status"] = "ready"
        return JSONResponse(content=payload)

    @app.get("/v1/hybrid-v0/readiness")
    async def get_hybrid_v0_readiness(dataDirUri: str | None = None):
        """查询 Hybrid v0 对指定 build-run 输出目录的本地可用性。"""
        try:
            data_dir = active_task_manager.resolve_data_dir_uri(dataDirUri)
        except ValueError as exc:
            raise HTTPException(status_code=400, detail=str(exc)) from exc
        return JSONResponse(content=_hybrid_readiness_payload(data_dir, dataDirUri))

    @app.get("/v1/internal/course-routing/readiness")
    async def get_course_routing_readiness():
        """Java 内部使用的课程画像路由可用性检查，不返回 API key。"""
        return JSONResponse(content=active_course_routing_service.readiness())

    @app.post("/v1/internal/course-routing/profiles/upsert")
    async def upsert_course_routing_profiles(request: CourseRoutingUpsertRouteRequest):
        """写入或更新课程画像向量。"""
        try:
            result = await active_course_routing_service.upsert_profiles(request.profiles)
        except ValueError as exc:
            raise HTTPException(status_code=400, detail=str(exc)) from exc
        return JSONResponse(content=jsonable_encoder(result))

    @app.post("/v1/internal/course-routing/profile-hints")
    async def extract_course_routing_profile_hints(request: CourseRoutingProfileHintsRouteRequest):
        """抽取课程画像章节来源和关键词 hints。"""
        try:
            result = await active_course_routing_service.extract_profile_hints(request)
        except ValueError as exc:
            raise HTTPException(status_code=400, detail=str(exc)) from exc
        return JSONResponse(content=jsonable_encoder(result))

    @app.post("/v1/internal/course-routing/recommend")
    async def recommend_course_routing(request: CourseRoutingRecommendRouteRequest):
        """根据问题推荐候选课程，阈值与分差判断由 Java 侧负责。"""
        try:
            result = await active_course_routing_service.recommend(request)
        except ValueError as exc:
            raise HTTPException(status_code=400, detail=str(exc)) from exc
        return JSONResponse(content=jsonable_encoder(result))

    @app.post("/v1/chat/completions")
    async def chat_completions(request: ChatCompletionRequest):
        """OpenAI 兼容的聊天完成接口。"""
        try:
            logger.info("收到请求，模型: %s", request.model)
            prompt = request.messages[-1].content
            logger.info("处理提示: %s...", prompt[:100])

            hybrid_diagnostics: dict[str, Any] | None = None
            if SUPPORTED_QUERY_MODELS.get(request.model) == "hybrid_v0":
                hybrid_result = await _run_hybrid_v0_answer(prompt)
                formatted_response = format_response(hybrid_result.answer)
                hybrid_diagnostics = hybrid_result.diagnostics.to_dict()
            else:
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
                hybrid_diagnostics=hybrid_diagnostics,
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
            {
                "id": "graphrag-hybrid-v0-search:latest",
                "object": "model",
                "created": current_time - 80000,
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
    if method == "hybrid_v0":
        return format_response(await _run_hybrid_v0_query(prompt))
    return format_response(await run_graphrag_query_cli(method, prompt))


app = create_app()


if __name__ == "__main__":
    logger.info("在 %s:%s 启动 GraphRAG API 服务器（CLI 查询模式）", APP_CONFIG.api_host, APP_CONFIG.api_port)
    uvicorn.run(app, host=APP_CONFIG.api_host, port=APP_CONFIG.api_port)
