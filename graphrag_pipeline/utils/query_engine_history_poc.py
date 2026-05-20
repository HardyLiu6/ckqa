"""GraphRAG query engine conversation history 适配层。

该模块继续服务 P3-D1 实验端点，同时可被 `/v1/query-tasks` 的
`local_history` 任务策略复用。正式问答仍以 Java/MySQL 为事实源，
任务策略失败时由调用方回退到 CLI local 查询。
"""

from __future__ import annotations

import asyncio
import inspect
import os
import time
from dataclasses import dataclass, field
from pathlib import Path
from typing import Any, Callable

from fastapi import HTTPException

from query_citation_resolver import resolve_answer_citations
from query_task_manager import resolve_build_run_data_dir_uri
from runtime_defaults import TARGET_GRAPHRAG_VERSION


METHOD_NAME = "local_history_poc"
REQUIRED_ARTIFACTS = (
    "text_units.parquet",
    "entities.parquet",
    "relationships.parquet",
    "communities.parquet",
    "community_reports.parquet",
    "lancedb/entity_description.lance",
)
ALLOWED_HISTORY_ROLES = {"user", "assistant"}


@dataclass(frozen=True, slots=True)
class HistoryPocConfig:
    enabled: bool = False
    max_turns: int = 3
    max_history_chars: int = 3000
    max_context_tokens: int = 32000
    top_k_entities: int = 6
    top_k_relationships: int = 6
    return_context: bool = False


@dataclass(frozen=True, slots=True)
class HistoryPocReadiness:
    enabled: bool
    supported: bool
    method: str = METHOD_NAME
    status: str = "not_ready"
    data_dir_uri: str | None = None
    missing: list[str] = field(default_factory=list)
    diagnostics: dict[str, Any] = field(default_factory=dict)
    error_message: str | None = None

    def to_dict(self) -> dict[str, Any]:
        return {
            "enabled": self.enabled,
            "supported": self.supported,
            "method": self.method,
            "status": self.status,
            "dataDirUri": self.data_dir_uri,
            "missing": list(self.missing),
            "diagnostics": dict(self.diagnostics),
            "errorMessage": self.error_message,
        }


@dataclass(frozen=True, slots=True)
class HistoryPocResult:
    enabled: bool
    supported: bool
    method: str
    answer: str | None
    raw_answer: str | None
    sources: list[dict[str, Any]]
    history_applied: bool
    history_turns_used: int
    elapsed_ms: int
    diagnostics: dict[str, Any]
    candidate_context_summary: dict[str, Any] | None = None
    error_message: str | None = None

    def to_dict(self) -> dict[str, Any]:
        return {
            "enabled": self.enabled,
            "supported": self.supported,
            "method": self.method,
            "answer": self.answer,
            "rawAnswer": self.raw_answer,
            "sources": list(self.sources),
            "historyApplied": self.history_applied,
            "historyTurnsUsed": self.history_turns_used,
            "elapsedMs": self.elapsed_ms,
            "diagnostics": dict(self.diagnostics),
            "candidateContextSummary": self.candidate_context_summary,
            "errorMessage": self.error_message,
        }


def parse_bool_env(value: str | None, *, default: bool = False) -> bool:
    if value is None:
        return default
    return value.strip().casefold() in {"1", "true", "yes", "y", "on"}


def parse_int_env(value: str | None, *, default: int) -> int:
    if value is None:
        return default
    try:
        parsed = int(value)
    except ValueError:
        return default
    return parsed if parsed > 0 else default


def load_history_poc_config_from_env(*, default_enabled: bool = False) -> HistoryPocConfig:
    return HistoryPocConfig(
        enabled=parse_bool_env(os.getenv("CKQA_GRAPHRAG_HISTORY_POC_ENABLED"), default=default_enabled),
        max_turns=parse_int_env(os.getenv("CKQA_GRAPHRAG_HISTORY_POC_MAX_TURNS"), default=3),
        max_history_chars=parse_int_env(os.getenv("CKQA_GRAPHRAG_HISTORY_POC_MAX_HISTORY_CHARS"), default=3000),
        max_context_tokens=parse_int_env(os.getenv("CKQA_GRAPHRAG_HISTORY_POC_MAX_CONTEXT_TOKENS"), default=32000),
        top_k_entities=parse_int_env(os.getenv("CKQA_GRAPHRAG_HISTORY_POC_TOP_K_ENTITIES"), default=6),
        top_k_relationships=parse_int_env(os.getenv("CKQA_GRAPHRAG_HISTORY_POC_TOP_K_RELATIONSHIPS"), default=6),
        return_context=parse_bool_env(os.getenv("CKQA_GRAPHRAG_HISTORY_POC_RETURN_CONTEXT"), default=False),
    )


def normalize_conversation_history(
    turns: list[dict[str, str]] | None,
    *,
    max_turns: int = 5,
    max_chars: int = 2500,
) -> list[dict[str, str]]:
    """校验并裁剪 history。

    `max_turns` 在 PoC 请求里按消息条数处理，GraphRAG 内部仍会按 QA turn 再做窗口控制。
    """

    normalized: list[dict[str, str]] = []
    for turn in turns or []:
        role = str(turn.get("role", "")).strip().casefold()
        content = str(turn.get("content", "")).strip()
        if not role and not content:
            continue
        if role not in ALLOWED_HISTORY_ROLES:
            raise ValueError(f"非法 conversation role: {role}")
        if not content:
            continue
        normalized.append({"role": role, "content": content})

    max_turns = max(1, max_turns)
    max_chars = max(1, max_chars)
    recent = normalized[-max_turns:]
    kept_reversed: list[dict[str, str]] = []
    used_chars = 0
    for turn in reversed(recent):
        turn_chars = len(turn["content"])
        if used_chars + turn_chars > max_chars:
            continue
        kept_reversed.append(turn)
        used_chars += turn_chars
    return list(reversed(kept_reversed))


class QueryEngineHistoryPocAdapter:
    def __init__(
        self,
        *,
        root_dir: Path,
        output_dir: Path,
        build_runs_root: Path | None,
        config: HistoryPocConfig,
        import_probe: Callable[[], bool] | None = None,
        search_engine_builder: Callable[[Path], Any] | Callable[[Path, bool], Any] | None = None,
        conversation_history_factory: Callable[[list[dict[str, str]]], Any] | None = None,
    ) -> None:
        self.root_dir = root_dir
        self.output_dir = output_dir
        self.build_runs_root = build_runs_root
        self.config = config
        self._import_probe = import_probe or self._probe_imports
        self._search_engine_builder = search_engine_builder
        self._conversation_history_factory = conversation_history_factory or self._build_conversation_history

    def readiness(self, data_dir_uri: str | None) -> HistoryPocReadiness:
        diagnostics = self._diagnostics(data_dir_uri)
        if not self.config.enabled:
            return HistoryPocReadiness(
                enabled=False,
                supported=False,
                status="disabled",
                data_dir_uri=data_dir_uri,
                diagnostics=diagnostics,
            )
        try:
            data_dir = self._resolve_data_dir(data_dir_uri)
            import_ready = self._import_probe()
        except HTTPException:
            raise
        except Exception as exc:  # noqa: BLE001 - PoC readiness 要把内部漂移转成诊断
            return HistoryPocReadiness(
                enabled=True,
                supported=False,
                status="not_ready",
                data_dir_uri=data_dir_uri,
                diagnostics=diagnostics,
                error_message=str(exc),
            )
        missing = _missing_artifacts(data_dir)
        supported = import_ready and not missing
        return HistoryPocReadiness(
            enabled=True,
            supported=supported,
            status="ready" if supported else "not_ready",
            data_dir_uri=data_dir_uri,
            missing=missing,
            diagnostics=diagnostics,
        )

    def query(
        self,
        *,
        data_dir_uri: str | None,
        query: str,
        conversation_history: list[dict[str, str]] | None,
        max_turns: int | None = None,
        user_turns_only: bool = True,
        return_candidate_context: bool | None = None,
    ) -> HistoryPocResult:
        if not self.config.enabled:
            raise HTTPException(status_code=403, detail="GraphRAG history PoC is disabled")
        query = (query or "").strip()
        if not query:
            raise HTTPException(status_code=422, detail="query is required")
        started = time.perf_counter()
        diagnostics = self._diagnostics(data_dir_uri)
        try:
            data_dir = self._resolve_data_dir(data_dir_uri)
            # 单元测试可以注入 fake search engine；真实 PoC 路径仍必须检查 artifact 完整性。
            missing = [] if self._search_engine_builder is not None else _missing_artifacts(data_dir)
            if missing:
                return HistoryPocResult(
                    enabled=True,
                    supported=False,
                    method=METHOD_NAME,
                    answer=None,
                    raw_answer=None,
                    sources=[],
                    history_applied=False,
                    history_turns_used=0,
                    elapsed_ms=_elapsed_ms(started),
                    diagnostics=diagnostics,
                    error_message="missing artifacts: " + ", ".join(missing),
                )
            history_turns = normalize_conversation_history(
                conversation_history,
                max_turns=max_turns or self.config.max_turns,
                max_chars=self.config.max_history_chars,
            )
            history = self._conversation_history_factory(history_turns) if history_turns else None
            search_engine = self._build_search_engine(
                data_dir,
                return_candidate_context=(
                    self.config.return_context if return_candidate_context is None else return_candidate_context
                ),
            )
            result = _resolve_search_result(search_engine.search(query=query, conversation_history=history))
            context_summary = _summarize_context_data(getattr(result, "context_data", None))
            if not (self.config.return_context if return_candidate_context is None else return_candidate_context):
                context_summary = None
            raw_answer = _response_to_text(getattr(result, "response", ""))
            resolved_answer = resolve_answer_citations(raw_answer, data_dir)
            return HistoryPocResult(
                enabled=True,
                supported=True,
                method=METHOD_NAME,
                answer=resolved_answer.display_text,
                raw_answer=raw_answer,
                sources=[source.to_dict() for source in resolved_answer.sources],
                history_applied=history is not None,
                history_turns_used=len(history_turns),
                elapsed_ms=_elapsed_ms(started),
                diagnostics={
                    **diagnostics,
                    "userTurnsOnly": user_turns_only,
                    "maxContextTokens": self.config.max_context_tokens,
                    "topKEntities": self.config.top_k_entities,
                    "topKRelationships": self.config.top_k_relationships,
                    "rawDataCitationPresent": "[Data:" in raw_answer,
                },
                candidate_context_summary=context_summary,
            )
        except HTTPException:
            raise
        except Exception as exc:  # noqa: BLE001 - PoC 查询失败不能影响正式链路
            return HistoryPocResult(
                enabled=True,
                supported=False,
                method=METHOD_NAME,
                answer=None,
                raw_answer=None,
                sources=[],
                history_applied=False,
                history_turns_used=0,
                elapsed_ms=_elapsed_ms(started),
                diagnostics=diagnostics,
                error_message=str(exc),
            )

    def _resolve_data_dir(self, data_dir_uri: str | None) -> Path:
        if data_dir_uri:
            if self.build_runs_root is None:
                raise HTTPException(status_code=400, detail="GRAPHRAG_BUILD_RUNS_ROOT 未配置")
            try:
                return resolve_build_run_data_dir_uri(data_dir_uri, self.build_runs_root)
            except ValueError as exc:
                raise HTTPException(status_code=400, detail=str(exc)) from exc
        return self.output_dir.resolve()

    def _build_search_engine(self, data_dir: Path, *, return_candidate_context: bool) -> Any:
        if self._search_engine_builder is not None:
            try:
                return self._search_engine_builder(data_dir, return_candidate_context=return_candidate_context)
            except TypeError:
                return self._search_engine_builder(data_dir)
        return _build_real_local_search_engine(self.root_dir, data_dir, self.config, return_candidate_context)

    def _diagnostics(self, data_dir_uri: str | None) -> dict[str, Any]:
        return {
            "graphragVersion": TARGET_GRAPHRAG_VERSION,
            "adapter": "LocalSearch",
            "dataDirUri": data_dir_uri,
        }

    @staticmethod
    def _probe_imports() -> bool:
        _import_history_poc_components()
        return True

    @staticmethod
    def _build_conversation_history(turns: list[dict[str, str]]) -> Any:
        from graphrag.query.context_builder.conversation_history import ConversationHistory

        return ConversationHistory.from_list(turns)


def _missing_artifacts(data_dir: Path) -> list[str]:
    return [relative for relative in REQUIRED_ARTIFACTS if not (data_dir / relative).exists()]


def _elapsed_ms(started: float) -> int:
    return int((time.perf_counter() - started) * 1000)


def _response_to_text(response: Any) -> str:
    if isinstance(response, str):
        return response
    return str(response)


def _resolve_search_result(result: Any) -> Any:
    if inspect.isawaitable(result):
        return asyncio.run(result)
    return result


def _summarize_context_data(context_data: Any) -> dict[str, Any] | None:
    if context_data is None:
        return None
    if isinstance(context_data, dict):
        summary: dict[str, Any] = {}
        for key, value in context_data.items():
            if hasattr(value, "shape"):
                summary[str(key)] = {"rows": int(value.shape[0]), "columns": int(value.shape[1])}
            elif isinstance(value, list):
                summary[str(key)] = {"count": len(value)}
            else:
                summary[str(key)] = {"type": type(value).__name__}
        return summary
    if isinstance(context_data, list):
        return {"items": {"count": len(context_data)}}
    return {"context": {"type": type(context_data).__name__}}


def _import_history_poc_components() -> None:
    from graphrag.query.context_builder.conversation_history import ConversationHistory  # noqa: F401
    from graphrag.query.factory import get_local_search_engine  # noqa: F401
    from graphrag.query.structured_search.local_search.search import LocalSearch  # noqa: F401


def _local_search_cli_overrides(data_dir: Path, config: HistoryPocConfig) -> dict[str, Any]:
    return {
        "output_storage": {"base_dir": str(data_dir)},
        "vector_store": {"db_uri": str(data_dir / "lancedb")},
        "local_search": {
            "conversation_history_max_turns": config.max_turns,
            "max_context_tokens": config.max_context_tokens,
            "top_k_entities": config.top_k_entities,
            "top_k_relationships": config.top_k_relationships,
        },
    }


def _build_real_local_search_engine(
    root_dir: Path,
    data_dir: Path,
    history_config: HistoryPocConfig,
    return_candidate_context: bool,
) -> Any:
    _import_history_poc_components()

    from graphrag.callbacks.noop_query_callbacks import NoopQueryCallbacks
    from graphrag.cli.query import _resolve_output_files
    from graphrag.config.embeddings import entity_description_embedding
    from graphrag.config.load_config import load_config
    from graphrag.query.factory import get_local_search_engine
    from graphrag.query.indexer_adapters import (
        read_indexer_covariates,
        read_indexer_entities,
        read_indexer_relationships,
        read_indexer_reports,
        read_indexer_text_units,
    )
    from graphrag.utils.api import get_embedding_store, load_search_prompt

    config = load_config(
        root_dir=root_dir,
        cli_overrides=_local_search_cli_overrides(data_dir, history_config),
    )
    dataframe_dict = _resolve_output_files(
        config=config,
        output_list=["communities", "community_reports", "text_units", "relationships", "entities"],
        optional_list=["covariates"],
    )
    communities = dataframe_dict["communities"]
    community_level = 2
    covariates = dataframe_dict.get("covariates")
    covariates_ = read_indexer_covariates(covariates) if covariates is not None else []
    callbacks: list[Any] | None = None
    if return_candidate_context:
        callbacks = [NoopQueryCallbacks()]
    return get_local_search_engine(
        config=config,
        reports=read_indexer_reports(dataframe_dict["community_reports"], communities, community_level),
        text_units=read_indexer_text_units(dataframe_dict["text_units"]),
        entities=read_indexer_entities(dataframe_dict["entities"], communities, community_level),
        relationships=read_indexer_relationships(dataframe_dict["relationships"]),
        covariates={"claims": covariates_},
        description_embedding_store=get_embedding_store(
            config=config.vector_store,
            embedding_name=entity_description_embedding,
        ),
        response_type="Multiple Paragraphs",
        system_prompt=load_search_prompt(config.local_search.prompt),
        callbacks=callbacks,
    )


async def query_in_thread(adapter: QueryEngineHistoryPocAdapter, **kwargs: Any) -> HistoryPocResult:
    if not adapter.config.enabled:
        return adapter.query(**kwargs)
    return await asyncio.to_thread(adapter.query, **kwargs)
