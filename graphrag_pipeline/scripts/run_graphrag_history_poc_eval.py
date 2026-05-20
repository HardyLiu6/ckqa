#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""GraphRAG query engine conversation history PoC 评估脚本。

默认 dry-run，只生成可比较报告，不发起 GraphRAG query engine / 模型调用。
真实调用必须显式设置 `CKQA_HISTORY_POC_LIVE=1`，避免误外发课程片段。
"""

from __future__ import annotations

import argparse
import json
import logging
import os
import sys
import time
from contextlib import contextmanager
from dataclasses import asdict
from datetime import datetime
from pathlib import Path
from typing import Any

PROJECT_ROOT = Path(__file__).resolve().parents[1]
REPO_ROOT = PROJECT_ROOT.parent
UTILS_DIR = PROJECT_ROOT / "utils"
if str(UTILS_DIR) not in sys.path:
    sys.path.insert(0, str(UTILS_DIR))

from api_runtime_config import load_api_runtime_config  # noqa: E402
from query_engine_history_poc import (  # noqa: E402
    HistoryPocConfig,
    QueryEngineHistoryPocAdapter,
    load_history_poc_config_from_env,
)
from runtime_defaults import TARGET_GRAPHRAG_VERSION  # noqa: E402


DEFAULT_EVAL_JSONL = PROJECT_ROOT / "data" / "eval" / "操作系统课程问答验证整合习题集_v1.jsonl"
DEFAULT_OUTPUT_ROOT = REPO_ROOT / "docs" / "reports" / "graphrag-history-poc"
GRAPHRAG_WARNING_LOGGERS = (
    "graphrag.query.structured_search.local_search.mixed_context",
    "graphrag.query.context_builder.conversation_history",
)


def load_eval_cases(path: Path, *, limit: int) -> list[dict[str, Any]]:
    if not path.exists():
        return _fallback_cases(limit)
    followup_cases: list[dict[str, Any]] = []
    fallback_cases: list[dict[str, Any]] = []
    for line in path.read_text(encoding="utf-8").splitlines():
        if not line.strip():
            continue
        payload = json.loads(line)
        question = str(payload.get("question") or "").strip()
        if not question:
            continue
        if _looks_like_followup(question):
            followup_cases.append(payload)
        elif len(fallback_cases) < limit:
            fallback_cases.append(payload)
        if len(followup_cases) >= limit:
            break
    cases = followup_cases[:limit]
    if len(cases) < limit:
        cases.extend(fallback_cases[: limit - len(cases)])
    return cases or _fallback_cases(limit)


def _fallback_cases(limit: int) -> list[dict[str, Any]]:
    cases = [
        {
            "id": "history-poc-deadlock-followup",
            "question": "它和资源分配图有什么关系？",
            "gold_answer_summary": "死锁可以通过资源分配图中的环路和资源等待关系辅助判断。",
            "gold_entities": ["死锁", "资源分配图"],
        },
        {
            "id": "history-poc-scheduling-followup",
            "question": "这个算法为什么会导致饥饿？",
            "gold_answer_summary": "优先级或短作业优先等调度策略可能让部分进程长期得不到运行机会。",
            "gold_entities": ["调度算法", "饥饿"],
        },
    ]
    return cases[:limit]


def _looks_like_followup(question: str) -> bool:
    stripped = question.strip()
    if stripped.startswith(("它", "这个", "上面", "前者", "后者", "刚才", "继续")):
        return True
    return any(marker in stripped for marker in ("上一轮", "上一个", "刚才提到", "前面说"))


def build_history_for_case(case: dict[str, Any]) -> list[dict[str, str]]:
    entities = [str(item) for item in case.get("gold_entities") or [] if str(item).strip()]
    topic = entities[0] if entities else "上一轮主题"
    return [
        {"role": "user", "content": f"请解释{topic}。"},
        {
            "role": "assistant",
            "content": str(case.get("gold_answer_summary") or f"{topic} 是本课程中的关键概念。"),
        },
    ]


def build_generation_context(history: list[dict[str, str]]) -> str:
    lines = ["最近对话："]
    role_label = {"user": "学生", "assistant": "助手"}
    for turn in history:
        role = role_label.get(turn["role"], turn["role"])
        lines.append(f"{role}：{turn['content']}")
    return "\n".join(lines)


def build_followup_query_for_case(case: dict[str, Any]) -> str:
    question = str(case.get("question") or "").strip()
    if _looks_like_followup(question):
        return question
    entities = [str(item) for item in case.get("gold_entities") or [] if str(item).strip()]
    if len(entities) >= 2:
        return f"它和{entities[1]}有什么关系？"
    return "它在操作系统课程中是什么意思？"


def build_ckqa_retrieval_query(question: str, history: list[dict[str, str]], case: dict[str, Any]) -> str:
    if not _looks_like_followup(question):
        return question
    entities = [str(item) for item in case.get("gold_entities") or [] if str(item).strip()]
    topic = entities[0] if entities else ""
    if not topic:
        for turn in reversed(history):
            if turn["role"] == "user":
                topic = turn["content"].replace("请解释", "").rstrip("。")
                break
    return f"关于上一轮主题「{topic or '上一轮主题'}」：{question}"


def build_ckqa_formal_hybrid_placeholder(question: str, history: list[dict[str, str]], case: dict[str, Any]) -> dict[str, Any]:
    generation_context = build_generation_context(history)
    return {
        "status": "not_executed",
        "mode": "hybrid_v0",
        "retrievalQuery": build_ckqa_retrieval_query(question, history, case),
        "generationContextChars": len(generation_context),
        "note": "本报告先固化与正式 CKQA rewrite/hybrid 可对齐的输入；真实 hybrid baseline 需单独授权后执行。",
    }


def score_answer_quality(case: dict[str, Any], answer: str | None, sources: list[dict[str, Any]] | None) -> dict[str, float]:
    answer = answer or ""
    must_terms = [str(item) for item in case.get("must_cite_terms") or [] if str(item).strip()]
    negative_terms = [str(item) for item in case.get("negative_terms") or [] if str(item).strip()]
    gold_refs = [str(item)[:12] for item in case.get("gold_text_unit_ids") or [] if str(item).strip()]
    source_refs = [str(source.get("chunk_id") or "")[:12] for source in (sources or []) if str(source.get("chunk_id") or "").strip()]
    must_hit = 1.0 if not must_terms else sum(1 for term in must_terms if term in answer) / len(must_terms)
    negative_hit = 1.0 if any(term in answer for term in negative_terms) else 0.0
    if not gold_refs:
        source_recall = 0.0
    else:
        source_recall = len(set(gold_refs).intersection(source_refs[:3])) / len(set(gold_refs))
    return {
        "mustCiteHit": round(float(must_hit), 4),
        "negativeHit": round(float(negative_hit), 4),
        "sourceRecallAt3": round(float(source_recall), 4),
    }


@contextmanager
def capture_graphrag_warning_messages():
    messages: list[str] = []

    class ListHandler(logging.Handler):
        def emit(self, record: logging.LogRecord) -> None:
            messages.append(record.getMessage())

    handlers: list[tuple[logging.Logger, ListHandler]] = []
    try:
        for logger_name in GRAPHRAG_WARNING_LOGGERS:
            logger = logging.getLogger(logger_name)
            handler = ListHandler(level=logging.WARNING)
            logger.addHandler(handler)
            handlers.append((logger, handler))
        yield messages
    finally:
        for logger, handler in handlers:
            logger.removeHandler(handler)


def attach_warning_diagnostics(local_history: dict[str, Any], warning_messages: list[str]) -> None:
    diagnostics = dict(local_history.get("diagnostics") or {})
    diagnostics["warningMessages"] = warning_messages
    diagnostics["tokenLimitWarningCount"] = sum(
        1 for message in warning_messages if "Reached token limit" in message
    )
    local_history["diagnostics"] = diagnostics


def build_report_case(
    case: dict[str, Any],
    *,
    live: bool,
    adapter: QueryEngineHistoryPocAdapter | None,
    data_dir_uri: str | None,
    run_hybrid_baseline: bool = False,
    hybrid_baseline_runner=None,
) -> dict[str, Any]:
    source_question = str(case.get("question") or "").strip()
    question = build_followup_query_for_case(case)
    history = build_history_for_case(case)
    base_payload = {
        "id": case.get("id") or case.get("question_id") or source_question[:24],
        "question": question,
        "sourceQuestion": source_question,
        "conversationHistory": history,
        "cliLocal": {
            "status": "not_executed",
            "note": "当前 CLI local 不接收 conversation history；正式链路依赖 Java retrievalQuery 改写。",
        },
        "ckqaDoubleInput": {
            "status": "not_executed",
            "note": "当前 CKQA 双输入会保存 generationContext，但 basic/local/global/drift CLI 仍只查询 retrievalQuery。",
        },
        "ckqaFormalHybrid": build_ckqa_formal_hybrid_placeholder(question, history, case),
        "qualitySignals": {
            "localHistory": score_answer_quality(case, None, []),
            "ckqaFormalHybrid": None,
        },
    }
    if not live:
        base_payload["localHistory"] = {
            "status": "dry_run_skipped",
            "historyTurns": len(history),
            "note": "设置 CKQA_HISTORY_POC_LIVE=1 后才执行真实 GraphRAG LocalSearch history。",
        }
        return base_payload
    assert adapter is not None
    with capture_graphrag_warning_messages() as warning_messages:
        result = adapter.query(
            data_dir_uri=data_dir_uri,
            query=question,
            conversation_history=history,
            user_turns_only=True,
            return_candidate_context=False,
        )
    local_history = result.to_dict()
    attach_warning_diagnostics(local_history, warning_messages)
    if local_history.get("supported") and local_history.get("answer"):
        local_history["status"] = "success"
    elif local_history.get("errorMessage"):
        local_history["status"] = "failed"
    else:
        local_history["status"] = "not_supported"
    base_payload["localHistory"] = local_history
    base_payload["qualitySignals"]["localHistory"] = score_answer_quality(
        case,
        local_history.get("answer"),
        local_history.get("sources") or [],
    )
    if run_hybrid_baseline:
        runner = hybrid_baseline_runner or run_ckqa_hybrid_baseline
        hybrid_result = runner(
            question=base_payload["ckqaFormalHybrid"]["retrievalQuery"],
            generation_context=build_generation_context(history),
            data_dir_uri=data_dir_uri,
        )
        base_payload["ckqaFormalHybrid"] = {
            **base_payload["ckqaFormalHybrid"],
            **hybrid_result,
        }
        base_payload["qualitySignals"]["ckqaFormalHybrid"] = score_answer_quality(
            case,
            hybrid_result.get("answer"),
            hybrid_result.get("sources") or [],
        )
    return base_payload


def run_ckqa_hybrid_baseline(*, question: str, generation_context: str, data_dir_uri: str | None) -> dict[str, Any]:
    started = time.perf_counter()
    try:
        from main import _get_hybrid_v0_orchestrator
        from query_citation_resolver import resolve_answer_citations
        from query_task_manager import resolve_build_run_data_dir_uri

        runtime_config = load_api_runtime_config()
        data_dir = (
            resolve_build_run_data_dir_uri(data_dir_uri, runtime_config.build_runs_root)
            if data_dir_uri
            else runtime_config.output_dir
        )
        result = _get_hybrid_v0_orchestrator(data_dir).answer(question, generation_context=generation_context)
        resolved_answer = resolve_answer_citations(result.answer, data_dir)
        sources = [source.to_dict() for source in resolved_answer.sources]
        if not sources:
            sources = [
                {
                    "rank": rank,
                    "source_type": candidate.source,
                    "chunk_id": candidate.ref,
                    "snippet": candidate.text[:280],
                }
                for rank, candidate in enumerate(result.sources, start=1)
            ]
        return {
            "status": "success",
            "answer": resolved_answer.display_text,
            "rawAnswer": result.answer,
            "sources": sources,
            "elapsedMs": int((time.perf_counter() - started) * 1000),
            "diagnostics": result.diagnostics.to_dict(),
        }
    except Exception as exc:  # noqa: BLE001 - baseline 失败不能影响 LocalSearch PoC 报告
        return {
            "status": "failed",
            "answer": None,
            "rawAnswer": None,
            "sources": [],
            "elapsedMs": int((time.perf_counter() - started) * 1000),
            "errorMessage": str(exc),
        }


def write_report(report_dir: Path, report: dict[str, Any]) -> None:
    report_dir.mkdir(parents=True, exist_ok=True)
    (report_dir / "report.json").write_text(json.dumps(report, ensure_ascii=False, indent=2), encoding="utf-8")
    lines = [
        "# GraphRAG History PoC",
        "",
        f"- live: `{str(report['live']).lower()}`",
        f"- caseCount: `{report['caseCount']}`",
        f"- graphragVersion: `{report['graphragVersion']}`",
        f"- dataDirUri: `{report.get('dataDirUri') or ''}`",
        "",
        "## Cases",
        "",
        "| case | local history | local ms | local sources | local recall@3 | token warnings | CKQA hybrid | hybrid ms | hybrid sources | hybrid recall@3 |",
        "| --- | --- | ---: | ---: | ---: | ---: | --- | ---: | ---: | ---: |",
    ]
    for item in report["cases"]:
        local_history = item["localHistory"]
        hybrid = item.get("ckqaFormalHybrid", {})
        quality_signals = item.get("qualitySignals", {})
        local_quality = quality_signals.get("localHistory", {})
        hybrid_quality = quality_signals.get("ckqaFormalHybrid") or {}
        lines.append(
            "| "
            f"`{item['id']}` {item['question']} | "
            f"{local_history['status']} | "
            f"{local_history.get('elapsedMs') or 0} | "
            f"{len(local_history.get('sources') or [])} | "
            f"{local_quality.get('sourceRecallAt3', 0.0)} | "
            f"{local_history.get('diagnostics', {}).get('tokenLimitWarningCount', 0)} | "
            f"{hybrid.get('status', 'not_executed')} | "
            f"{hybrid.get('elapsedMs') or 0} | "
            f"{len(hybrid.get('sources') or [])} | "
            f"{hybrid_quality.get('sourceRecallAt3', 0.0)} |"
        )
    (report_dir / "summary.md").write_text("\n".join(lines) + "\n", encoding="utf-8")


def parse_args(argv: list[str] | None = None) -> argparse.Namespace:
    parser = argparse.ArgumentParser(description="Run GraphRAG LocalSearch conversation history PoC evaluation.")
    parser.add_argument("--dry-run", action="store_true", help="只生成报告，不发起真实 query engine 调用。")
    parser.add_argument("--eval-jsonl", type=Path, default=DEFAULT_EVAL_JSONL)
    parser.add_argument("--output-root", type=Path, default=DEFAULT_OUTPUT_ROOT)
    parser.add_argument("--run-label", default="main")
    parser.add_argument("--limit", type=int, default=12)
    parser.add_argument("--data-dir-uri", default=None)
    parser.add_argument(
        "--run-hybrid-baseline",
        action="store_true",
        help="同时运行 CKQA hybrid_v0 baseline；会触发额外 GraphRAG/One API 调用。",
    )
    return parser.parse_args(argv)


def main(argv: list[str] | None = None) -> Path:
    args = parse_args(argv)
    live = bool(os.getenv("CKQA_HISTORY_POC_LIVE") == "1" and not args.dry_run)
    timestamp = datetime.now().strftime("%Y%m%d-%H%M%S")
    report_dir = args.output_root / f"{timestamp}-{args.run_label}"
    cases = load_eval_cases(args.eval_jsonl, limit=max(1, args.limit))
    adapter: QueryEngineHistoryPocAdapter | None = None
    if live:
        runtime_config = load_api_runtime_config()
        config = load_history_poc_config_from_env()
        if not config.enabled:
            config = HistoryPocConfig(
                enabled=True,
                max_turns=config.max_turns,
                max_history_chars=config.max_history_chars,
                max_context_tokens=config.max_context_tokens,
                top_k_entities=config.top_k_entities,
                top_k_relationships=config.top_k_relationships,
                return_context=config.return_context,
            )
        adapter = QueryEngineHistoryPocAdapter(
            root_dir=PROJECT_ROOT,
            output_dir=runtime_config.output_dir,
            build_runs_root=runtime_config.build_runs_root,
            config=config,
        )
    report = {
        "live": live,
        "generatedAt": timestamp,
        "graphragVersion": TARGET_GRAPHRAG_VERSION,
        "dataDirUri": args.data_dir_uri,
        "historyPocConfig": asdict(adapter.config) if adapter is not None else None,
        "caseCount": len(cases),
        "cases": [
            build_report_case(case, live=live, adapter=adapter, data_dir_uri=args.data_dir_uri)
            if not args.run_hybrid_baseline
            else build_report_case(
                case,
                live=live,
                adapter=adapter,
                data_dir_uri=args.data_dir_uri,
                run_hybrid_baseline=True,
            )
            for case in cases
        ],
    }
    write_report(report_dir, report)
    print(report_dir)
    return report_dir


if __name__ == "__main__":
    main()
