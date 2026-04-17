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
from concurrent.futures import ThreadPoolExecutor, as_completed
from pathlib import Path
from typing import Any, Sequence

from extraction_parser import parse_extraction_output
from extraction_schema import StructuredExtractionResult
from llm_client import build_llm_client
from prompt_loader import (
    default_schema_paths,
    load_candidate_prompts,
    load_samples,
    load_schema_catalog,
    resolve_manifest_path,
    resolve_samples_path,
)
from prompt_renderer import render_extraction_messages
from result_writer import ensure_parse_error_path, write_candidate_outputs, write_parse_errors


PROJECT_ROOT = Path(__file__).resolve().parents[1]


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
    if not samples:
        raise ValueError("样本列表为空，无法执行抽取")

    client = llm_client or build_llm_client(
        root=root,
        model=model,
        timeout_seconds=timeout_seconds,
        retries=retries,
    )
    resolved_model = model or getattr(client, "model_name", "") or "unknown-model"

    parse_error_path = ensure_parse_error_path(root=root, overwrite=overwrite)
    all_results: list[StructuredExtractionResult] = []
    per_candidate_summaries: list[dict[str, Any]] = []
    sample_order = {sample.get("sample_id"): index for index, sample in enumerate(samples)}

    for candidate in candidates:
        logger = _build_candidate_logger(root=root, candidate_name=candidate.name, overwrite=overwrite)
        logger.info("开始执行候选 Prompt：candidate=%s samples=%s", candidate.name, len(samples))

        results: list[StructuredExtractionResult] = []
        with ThreadPoolExecutor(max_workers=max(1, concurrency)) as executor:
            future_map = {
                executor.submit(
                    _run_single_extraction,
                    candidate=candidate,
                    sample=sample,
                    schema_catalog=schema_catalog,
                    model_name=resolved_model,
                    temperature=temperature,
                    max_tokens=max_tokens,
                    client=client,
                ): sample
                for sample in samples
            }
            for future in as_completed(future_map):
                sample = future_map[future]
                result = future.result()
                results.append(result)
                logger.info(
                    "样本执行完成：candidate=%s sample_id=%s status=%s error=%s",
                    candidate.name,
                    sample.get("sample_id"),
                    result.status,
                    result.error or "",
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
        "total_candidates": len(candidates),
        "total_samples": len(samples),
        "total_runs": len(all_results),
        "success": total_success,
        "parse_error": total_parse_error,
        "llm_error": total_llm_error,
        "parse_error_file": str(parse_error_path),
        "candidates": per_candidate_summaries,
    }


def _run_single_extraction(
    *,
    candidate: Any,
    sample: dict[str, Any],
    schema_catalog: Any,
    model_name: str,
    temperature: float,
    max_tokens: int | None,
    client: Any,
) -> StructuredExtractionResult:
    sample_id = str(sample.get("sample_id") or "").strip()
    messages = render_extraction_messages(
        candidate=candidate,
        sample=sample,
        schema_catalog=schema_catalog,
    )

    try:
        raw_output = client.create_chat_completion(
            messages=messages,
            model=model_name,
            temperature=temperature,
            max_tokens=max_tokens,
            metadata={
                "candidate": candidate.name,
                "sample_id": sample_id,
                "prompt_path": str(candidate.prompt_path),
            },
        )
    except Exception as exc:
        return StructuredExtractionResult(
            sample_id=sample_id,
            candidate=candidate.name,
            status="llm_error",
            entities=[],
            relationships=[],
            raw_output="",
            error=str(exc),
        )

    return parse_extraction_output(
        raw_output,
        sample_id=sample_id,
        candidate=candidate.name,
        schema_catalog=schema_catalog,
    )


def _build_candidate_logger(*, root: Path, candidate_name: str, overwrite: bool) -> logging.Logger:
    log_path = (root / "results" / "logs" / f"extraction_{candidate_name}.log").resolve()
    log_path.parent.mkdir(parents=True, exist_ok=True)
    if log_path.exists() and not overwrite:
        raise FileExistsError(f"日志文件已存在，若要覆盖请传 --overwrite：{log_path}")

    logger_name = f"candidate_extraction.{candidate_name}"
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
    )
    print(json.dumps(summary, ensure_ascii=False, indent=2))
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
