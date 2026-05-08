#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
候选 Prompt 抽取执行与结构化解析
=================================

步骤 7：
1. 读取课程样本与候选 Prompt manifest。
2. 对每个 sample × candidate 执行一次抽取。
3. 强制模型按统一 JSON 输出，并做容错解析。
4. 产出 eval/raw/log/error 文件，供后续自动评测直接读取。
"""

from __future__ import annotations

import argparse
import json
import logging
import time
from concurrent.futures import FIRST_COMPLETED, ThreadPoolExecutor, wait
from pathlib import Path
from typing import Any, Sequence

from .extraction_parser import parse_extraction_output
from .extraction_schema import StructuredExtractionResult
from .llm_client import LlmCompletionResult, build_llm_client
from .prompt_loader import (
    default_schema_paths,
    is_fallback_auto_tuned_entry,
    load_candidate_prompts,
    load_samples,
    load_schema_catalog,
    resolve_manifest_path,
    resolve_samples_path,
)
from .prompt_renderer import render_extraction_messages
from .result_writer import ensure_parse_error_path, write_candidate_outputs, write_parse_errors


PROJECT_ROOT = Path(__file__).resolve().parents[2]


def run_candidate_extraction(
    *,
    root: Path,
    samples_file: str | Path | None,
    manifest_file: str | Path | None,
    candidate_names: Sequence[str] | None,
    model: str | None,
    limit: int | None,
    concurrency: int,
    temperature: float,
    max_tokens: int | None,
    retries: int,
    overwrite: bool,
    llm_client: Any | None = None,
    timeout_seconds: int = 120,
    stream_mode: str = "on",
    idle_timeout_seconds: int = 30,
    retry_on_truncation: bool = True,
    retry_max_tokens: int | None = None,
    high_risk_timeout: int = 240,
    sample_timeout_seconds: float | None = 300,
    max_entities: int | None = None,
    max_relationships: int | None = None,
    run_id: str | None = None,
    candidate_view_mode: str = "compact",
    max_prompt_chars: int = 1800,
    include_fallback_auto_tuned: bool = False,
) -> dict[str, Any]:
    root = root.resolve()
    samples_path = resolve_samples_path(samples_file, root=root)
    manifest_path = resolve_manifest_path(manifest_file, root=root)
    entity_schema_path, relation_schema_path = default_schema_paths(root=root)
    schema_catalog = load_schema_catalog(
        entity_schema_path=entity_schema_path,
        relation_schema_path=relation_schema_path,
    )
    samples = load_samples(samples_path, limit=limit)
    candidates = load_candidate_prompts(
        manifest_path,
        root=root,
        candidate_names=candidate_names,
    )
    skipped_candidates: list[str] = []
    if candidate_names is None and not include_fallback_auto_tuned:
        runnable_candidates = []
        for candidate in candidates:
            if is_fallback_auto_tuned_entry(candidate.manifest_entry):
                skipped_candidates.append(candidate.name)
                continue
            runnable_candidates.append(candidate)
        candidates = runnable_candidates
        if not candidates:
            raise ValueError("没有可执行的候选 Prompt；auto_tuned 当前只是 fallback 占位")
    if not samples:
        raise ValueError("样本列表为空，无法执行抽取")

    effective_timeout_seconds, effective_high_risk_timeout, effective_retries = (
        _resolve_timeout_plan(
            timeout_seconds=timeout_seconds,
            high_risk_timeout=high_risk_timeout,
            retries=retries,
            sample_timeout_seconds=sample_timeout_seconds,
        )
    )

    client = llm_client or build_llm_client(
        root=root,
        model=model,
        timeout_seconds=effective_timeout_seconds,
        retries=effective_retries,
        stream_mode=stream_mode,
        idle_timeout_seconds=idle_timeout_seconds,
    )
    resolved_model = model or getattr(client, "model_name", "") or "unknown-model"

    parse_error_path = ensure_parse_error_path(root=root, overwrite=overwrite, run_id=run_id)
    all_results: list[StructuredExtractionResult] = []
    per_candidate_summaries: list[dict[str, Any]] = []
    sample_order = {sample.get("sample_id"): index for index, sample in enumerate(samples)}

    for candidate in candidates:
        logger = _build_candidate_logger(
            root=root,
            candidate_name=candidate.name,
            overwrite=overwrite,
            run_id=run_id,
        )
        logger.info("开始执行候选 Prompt：candidate=%s samples=%s", candidate.name, len(samples))

        results = _run_candidate_samples(
            candidate=candidate,
            samples=samples,
            schema_catalog=schema_catalog,
            model_name=resolved_model,
            temperature=temperature,
            max_tokens=max_tokens,
            client=client,
            timeout_seconds=effective_timeout_seconds,
            retry_on_truncation=retry_on_truncation,
            retry_max_tokens=retry_max_tokens,
            high_risk_timeout=effective_high_risk_timeout,
            max_entities=max_entities,
            max_relationships=max_relationships,
            candidate_view_mode=candidate_view_mode,
            max_prompt_chars=max_prompt_chars,
            concurrency=concurrency,
            sample_timeout_seconds=sample_timeout_seconds,
            logger=logger,
        )

        results.sort(key=lambda item: sample_order.get(item.sample_id, 10**9))
        candidate_output = write_candidate_outputs(
            root=root,
            candidate_name=candidate.name,
            results=results,
            samples_file=samples_path,
            manifest_file=manifest_path,
            model=resolved_model,
            overwrite=overwrite,
            run_id=run_id,
        )
        logger.info("候选 Prompt 执行完成：candidate=%s summary=%s", candidate.name, candidate_output["summary"])
        _close_logger(logger)

        all_results.extend(results)
        per_candidate_summaries.append(
            {
                "candidate": candidate.name,
                "summary": candidate_output["summary"],
                "paths": {name: str(path) for name, path in candidate_output["paths"].items()},
            }
        )

    write_parse_errors(path=parse_error_path, results=all_results)
    total_success = sum(1 for item in all_results if item.status == "success")
    total_parse_error = sum(1 for item in all_results if item.status == "parse_error")
    total_llm_error = sum(1 for item in all_results if item.status == "llm_error")

    return {
        "status": "success",
        "root": str(root),
        "samples_file": str(samples_path),
        "manifest_file": str(manifest_path),
        "model": resolved_model,
        "run_id": run_id,
        "runtime": {
            "request_timeout_seconds": effective_timeout_seconds,
            "request_retries": effective_retries,
            "high_risk_timeout_seconds": effective_high_risk_timeout,
            "sample_timeout_seconds": sample_timeout_seconds,
            "stream_mode": stream_mode,
            "fallback_auto_tuned_skipped": bool(skipped_candidates),
            "candidate_view_mode": candidate_view_mode,
            "max_prompt_chars": max_prompt_chars,
        },
        "total_candidates": len(candidates),
        "total_samples": len(samples),
        "total_runs": len(all_results),
        "success": total_success,
        "parse_error": total_parse_error,
        "llm_error": total_llm_error,
        "skipped_candidates": skipped_candidates,
        "parse_error_file": str(parse_error_path),
        "candidates": per_candidate_summaries,
    }


def _resolve_timeout_plan(
    *,
    timeout_seconds: int,
    high_risk_timeout: int,
    retries: int,
    sample_timeout_seconds: float | None,
) -> tuple[int, int, int]:
    """把请求超时和重试预算压到单样本超时内。

    线程本身无法强制杀掉正在进行的 requests 调用，因此必须让底层请求预算
    小于 sample timeout，避免主流程写完超时结果后仍被长尾请求拖住。
    """

    if sample_timeout_seconds is None:
        return timeout_seconds, high_risk_timeout, retries

    sample_budget = max(1, int(sample_timeout_seconds))
    effective_retries = max(0, retries)
    while effective_retries > 0:
        retry_sleep_budget = sum(min(2 * attempt, 5) for attempt in range(1, effective_retries + 1))
        request_budget = int((sample_budget - retry_sleep_budget) / (effective_retries + 1))
        if request_budget >= 1:
            return (
                max(1, min(timeout_seconds, request_budget)),
                max(1, min(high_risk_timeout, request_budget)),
                effective_retries,
            )
        effective_retries -= 1

    return (
        max(1, min(timeout_seconds, sample_budget)),
        max(1, min(high_risk_timeout, sample_budget)),
        0,
    )


def _run_candidate_samples(
    *,
    candidate: Any,
    samples: list[dict[str, Any]],
    schema_catalog: Any,
    model_name: str,
    temperature: float,
    max_tokens: int | None,
    client: Any,
    timeout_seconds: int,
    retry_on_truncation: bool,
    retry_max_tokens: int | None,
    high_risk_timeout: int,
    max_entities: int | None,
    max_relationships: int | None,
    candidate_view_mode: str,
    max_prompt_chars: int,
    concurrency: int,
    sample_timeout_seconds: float | None,
    logger: logging.Logger,
) -> list[StructuredExtractionResult]:
    results: list[StructuredExtractionResult] = []
    executor = ThreadPoolExecutor(max_workers=max(1, concurrency))
    timed_out = False
    try:
        pending: set[Any] = set()
        future_map: dict[Any, dict[str, Any]] = {}
        deadlines: dict[Any, float] = {}
        sample_iter = iter(samples)

        def submit_next_sample() -> bool:
            try:
                sample = next(sample_iter)
            except StopIteration:
                return False

            future = executor.submit(
                _run_single_extraction,
                candidate=candidate,
                sample=sample,
                schema_catalog=schema_catalog,
                model_name=model_name,
                temperature=temperature,
                max_tokens=max_tokens,
                client=client,
                timeout_seconds=timeout_seconds,
                retry_on_truncation=retry_on_truncation,
                retry_max_tokens=retry_max_tokens,
                high_risk_timeout=high_risk_timeout,
                max_entities=max_entities,
                max_relationships=max_relationships,
                candidate_view_mode=candidate_view_mode,
                max_prompt_chars=max_prompt_chars,
            )
            pending.add(future)
            future_map[future] = sample
            if sample_timeout_seconds is not None:
                deadlines[future] = time.monotonic() + sample_timeout_seconds
            return True

        for _ in range(max(1, concurrency)):
            if not submit_next_sample():
                break

        while pending:
            wait_timeout = 0.1
            if sample_timeout_seconds is not None and deadlines:
                now = time.monotonic()
                next_deadline = min(deadlines[future] for future in pending)
                wait_timeout = max(0.0, min(wait_timeout, next_deadline - now))

            done, _ = wait(pending, timeout=wait_timeout, return_when=FIRST_COMPLETED)
            now = time.monotonic()
            for future in done:
                pending.remove(future)
                sample = future_map[future]
                if sample_timeout_seconds is not None and now >= deadlines.get(future, now + 1):
                    timed_out = True
                    result = _build_sample_timeout_result(
                        candidate_name=candidate.name,
                        sample=sample,
                        sample_timeout_seconds=sample_timeout_seconds,
                    )
                else:
                    result = future.result()
                results.append(result)
                _log_sample_result(logger, candidate.name, sample, result)
                submit_next_sample()

            if sample_timeout_seconds is None:
                continue

            expired = [future for future in pending if now >= deadlines.get(future, now + 1)]
            for future in expired:
                pending.remove(future)
                future.cancel()
                timed_out = True
                sample = future_map[future]
                result = _build_sample_timeout_result(
                    candidate_name=candidate.name,
                    sample=sample,
                    sample_timeout_seconds=sample_timeout_seconds,
                )
                results.append(result)
                _log_sample_result(logger, candidate.name, sample, result)
                submit_next_sample()
    finally:
        executor.shutdown(wait=not timed_out, cancel_futures=timed_out)
    return results


def _log_sample_result(
    logger: logging.Logger,
    candidate_name: str,
    sample: dict[str, Any],
    result: StructuredExtractionResult,
) -> None:
    logger.info(
        "样本执行完成：candidate=%s sample_id=%s status=%s error=%s",
        candidate_name,
        sample.get("sample_id"),
        result.status,
        result.error or "",
    )


def _build_sample_timeout_result(
    *,
    candidate_name: str,
    sample: dict[str, Any],
    sample_timeout_seconds: float,
) -> StructuredExtractionResult:
    sample_id = str(sample.get("sample_id") or "").strip()
    return StructuredExtractionResult(
        sample_id=sample_id,
        candidate=candidate_name,
        status="llm_error",
        entities=[],
        relationships=[],
        raw_output="",
        error=f"样本执行超时：超过 {sample_timeout_seconds:g} 秒未返回",
        llm_debug={
            "error_type": "sample_timeout",
            "sample_timeout_seconds": sample_timeout_seconds,
        },
    )


def _run_single_extraction(
    *,
    candidate: Any,
    sample: dict[str, Any],
    schema_catalog: Any,
    model_name: str,
    temperature: float,
    max_tokens: int | None,
    client: Any,
    timeout_seconds: int,
    retry_on_truncation: bool,
    retry_max_tokens: int | None,
    high_risk_timeout: int,
    max_entities: int | None,
    max_relationships: int | None,
    candidate_view_mode: str,
    max_prompt_chars: int,
) -> StructuredExtractionResult:
    sample_id = str(sample.get("sample_id") or "").strip()
    messages = render_extraction_messages(
        candidate=candidate,
        sample=sample,
        schema_catalog=schema_catalog,
        max_entities=max_entities,
        max_relationships=max_relationships,
        candidate_view_mode=candidate_view_mode,
        max_prompt_chars=max_prompt_chars,
    )
    attempt_settings = [(max_tokens, timeout_seconds)]
    if retry_on_truncation:
        attempt_settings.append((retry_max_tokens or max_tokens, high_risk_timeout))

    first_failure_result: StructuredExtractionResult | None = None

    for attempt_index, (attempt_max_tokens, attempt_timeout) in enumerate(attempt_settings, start=1):
        try:
            raw_result = client.create_chat_completion(
                messages=messages,
                model=model_name,
                temperature=temperature,
                max_tokens=attempt_max_tokens,
                metadata={
                    "candidate": candidate.name,
                    "sample_id": sample_id,
                    "prompt_path": str(candidate.prompt_path),
                },
                timeout_seconds=attempt_timeout,
            )
        except Exception as exc:
            if first_failure_result is not None:
                return _merge_retry_error(
                    first_failure_result,
                    retry_error=str(exc),
                    attempt_index=attempt_index,
                    timeout_seconds=attempt_timeout,
                    max_tokens=attempt_max_tokens,
                )
            return StructuredExtractionResult(
                sample_id=sample_id,
                candidate=candidate.name,
                status="llm_error",
                entities=[],
                relationships=[],
                raw_output="",
                error=str(exc),
                llm_debug={
                    "attempt_index": attempt_index,
                    "timeout_seconds": attempt_timeout,
                    "max_tokens": attempt_max_tokens,
                },
            )

        llm_result = _coerce_llm_result(raw_result)
        parsed_result = parse_extraction_output(
            llm_result.content,
            sample_id=sample_id,
            candidate=candidate.name,
            schema_catalog=schema_catalog,
        )
        enriched_result = parsed_result.model_copy(
            update={
                "llm_debug": {
                    "finish_reason": llm_result.finish_reason,
                    "usage": llm_result.usage,
                    "request_mode": llm_result.request_mode,
                    "reasoning_seen": llm_result.reasoning_seen,
                    "raw_chunks": llm_result.raw_chunks,
                    "attempt_index": attempt_index,
                    "timeout_seconds": attempt_timeout,
                    "max_tokens": attempt_max_tokens,
                }
            }
        )

        if (
            attempt_index == 1
            and retry_on_truncation
            and _should_retry_truncation(enriched_result, llm_result)
        ):
            first_failure_result = enriched_result
            continue

        return enriched_result

    return first_failure_result or StructuredExtractionResult(
        sample_id=sample_id,
        candidate=candidate.name,
        status="parse_error",
        entities=[],
        relationships=[],
        raw_output="",
        error="抽取执行失败，但未返回可用结果",
    )


def _build_candidate_logger(
    *,
    root: Path,
    candidate_name: str,
    overwrite: bool,
    run_id: str | None = None,
) -> logging.Logger:
    log_root = root / "results" / "logs"
    if run_id:
        log_root = log_root / "runs" / run_id
    log_path = (log_root / f"extraction_{candidate_name}.log").resolve()
    log_path.parent.mkdir(parents=True, exist_ok=True)
    if log_path.exists() and not overwrite:
        raise FileExistsError(f"日志文件已存在，若要覆盖请传 --overwrite：{log_path}")

    logger_name = f"candidate_extraction.{run_id or 'default'}.{candidate_name}"
    logger = logging.getLogger(logger_name)
    logger.setLevel(logging.INFO)
    logger.propagate = False
    logger.handlers.clear()

    handler = logging.FileHandler(log_path, mode="w", encoding="utf-8")
    handler.setFormatter(logging.Formatter("%(asctime)s %(levelname)s %(message)s"))
    logger.addHandler(handler)
    return logger


def _close_logger(logger: logging.Logger) -> None:
    for handler in list(logger.handlers):
        handler.flush()
        handler.close()
        logger.removeHandler(handler)


def _build_parser() -> argparse.ArgumentParser:
    parser = argparse.ArgumentParser(description="批量运行候选 Prompt 抽取并输出统一 JSON 结果")
    parser.add_argument("--samples", help="样本文件路径，默认自动兼容 samples.json / prompt_tuning_samples.json")
    parser.add_argument("--manifest", help="候选 Prompt manifest 路径")
    parser.add_argument(
        "--candidate",
        action="append",
        help="指定候选 Prompt 名称，可重复传入或使用逗号分隔；默认执行 manifest 中全部候选",
    )
    parser.add_argument("--model", help="覆盖默认模型名；未指定时读取环境变量")
    parser.add_argument("--limit", type=int, help="仅执行前 N 条样本")
    parser.add_argument("--concurrency", type=int, default=4, help="每个候选 Prompt 内部的样本并发数")
    parser.add_argument("--temperature", type=float, default=0.0, help="LLM temperature")
    parser.add_argument("--max-tokens", type=int, default=2000, help="LLM max_tokens")
    parser.add_argument("--retries", type=int, default=2, help="LLM 请求重试次数")
    parser.add_argument("--timeout", type=int, default=120, help="LLM 请求超时时间（秒）")
    parser.add_argument(
        "--stream-mode",
        choices=["off", "on"],
        default="on",
        help="是否使用 SSE 流式调用 LLM；默认开启，可显式切回 off",
    )
    parser.add_argument("--idle-timeout", type=int, default=30, help="流式模式下的空闲超时（秒）")
    parser.add_argument(
        "--retry-on-truncation",
        action=argparse.BooleanOptionalAction,
        default=True,
        help="遇到 length/truncated_json 时自动重试一次；默认开启，可用 --no-retry-on-truncation 关闭",
    )
    parser.add_argument("--retry-max-tokens", type=int, default=4000, help="自动重试时使用的 max_tokens")
    parser.add_argument("--high-risk-timeout", type=int, default=240, help="高风险样本自动重试时使用的超时")
    parser.add_argument("--sample-timeout", type=float, default=300, help="单个样本最大执行秒数，超时写入 llm_error")
    parser.add_argument("--max-entities", type=int, default=6, help="限制输出实体数量")
    parser.add_argument("--max-relationships", type=int, default=6, help="限制输出关系数量")
    parser.add_argument("--run-id", help="将抽取结果写入 results/*/runs/<run-id>/")
    parser.add_argument(
        "--candidate-view",
        choices=["compact", "full"],
        default="compact",
        help="候选 Prompt 注入方式：默认 compact，只保留策略摘要；full 用于调试完整候选",
    )
    parser.add_argument("--max-prompt-chars", type=int, default=1800, help="compact 模式下候选策略最大字符数")
    parser.add_argument(
        "--include-fallback-auto-tuned",
        action="store_true",
        help="默认跳过 fallback_default_copy 的 auto_tuned；传入后才参与抽取",
    )
    parser.add_argument("--overwrite", action="store_true", help="覆盖已有结果文件")
    return parser


def main(argv: Sequence[str] | None = None) -> int:
    args = _build_parser().parse_args(argv)
    summary = run_candidate_extraction(
        root=PROJECT_ROOT,
        samples_file=args.samples,
        manifest_file=args.manifest,
        candidate_names=args.candidate,
        model=args.model,
        limit=args.limit,
        concurrency=args.concurrency,
        temperature=args.temperature,
        max_tokens=args.max_tokens,
        retries=args.retries,
        overwrite=args.overwrite,
        timeout_seconds=args.timeout,
        stream_mode=args.stream_mode,
        idle_timeout_seconds=args.idle_timeout,
        retry_on_truncation=args.retry_on_truncation,
        retry_max_tokens=args.retry_max_tokens,
        high_risk_timeout=args.high_risk_timeout,
        sample_timeout_seconds=args.sample_timeout,
        max_entities=args.max_entities,
        max_relationships=args.max_relationships,
        run_id=args.run_id,
        candidate_view_mode=args.candidate_view,
        max_prompt_chars=args.max_prompt_chars,
        include_fallback_auto_tuned=args.include_fallback_auto_tuned,
    )
    print(json.dumps(summary, ensure_ascii=False, indent=2))
    return 0

def _coerce_llm_result(raw_result: Any) -> LlmCompletionResult:
    if isinstance(raw_result, LlmCompletionResult):
        return raw_result
    return LlmCompletionResult(
        content=str(raw_result),
        finish_reason=None,
        usage=None,
        request_mode="sync",
    )


def _should_retry_truncation(result: StructuredExtractionResult, llm_result: LlmCompletionResult) -> bool:
    return llm_result.finish_reason == "length" or result.parser_error_code == "truncated_json"


def _merge_retry_error(
    result: StructuredExtractionResult,
    *,
    retry_error: str,
    attempt_index: int,
    timeout_seconds: int,
    max_tokens: int | None,
) -> StructuredExtractionResult:
    debug_info = dict(result.llm_debug or {})
    debug_info["retry_error"] = retry_error
    debug_info["retry_attempt_index"] = attempt_index
    debug_info["retry_timeout_seconds"] = timeout_seconds
    debug_info["retry_max_tokens"] = max_tokens
    return result.model_copy(update={"llm_debug": debug_info})


if __name__ == "__main__":
    raise SystemExit(main())
