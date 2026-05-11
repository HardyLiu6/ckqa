#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""使用 GraphRAG 原生 GraphExtractor 执行候选 Prompt 抽取实验。

与 run_candidate_extraction.py 的区别：
- 直接复用 graphrag.index.operations.extract_graph.graph_extractor.GraphExtractor
- 输出格式为 GraphRAG 原生 tuple 文本（`("entity"<|>NAME<|>TYPE<|>DESC)##...`）
- 支持 gleaning（多轮追问）
- 实体名会被 .upper() 大写化（与真实 graphrag index 行为一致）
- 最终输出仍转为本项目的 StructuredExtractionResult JSON 格式，兼容现有评分管线

用法：
    python scripts/run_native_extraction.py \\
        --samples-file data/eval/material_7_audit_extraction_set.json \\
        --prompt prompts/extract_graph.txt \\
        --entity-types "Concept,Term,KnowledgePoint,FormulaOrDefinition,AlgorithmOrMethod,..." \\
        --max-gleanings 1 \\
        --run-id native_default_gleaning1 \\
        --candidate-name default
"""

from __future__ import annotations

import argparse
import asyncio
import json
import logging
import os
import re
import time
import traceback
from pathlib import Path
from typing import Any, Sequence

import pandas as pd
from dotenv import load_dotenv

from graphrag.index.operations.extract_graph.graph_extractor import GraphExtractor
from graphrag_llm.completion import create_completion
from graphrag_llm.config import ModelConfig

from .extraction_schema import (
    ExtractionEntity,
    ExtractionRelationship,
    StructuredExtractionResult,
)
from .prompt_loader import load_samples, resolve_samples_path
from .result_writer import write_candidate_outputs

PROJECT_ROOT = Path(__file__).resolve().parents[2]
logger = logging.getLogger(__name__)

_RELATION_TYPE_PREFIX_RE = re.compile(r"^\[type=([^\]]+)\]")


def _extract_relation_type_from_description(description: str) -> str:
    """从 description 提取 [type=xxx] 前缀中的关系类型；无前缀则返回空字符串。"""
    match = _RELATION_TYPE_PREFIX_RE.match(description.strip())
    if match:
        return match.group(1).strip()
    return ""


def _load_env(root: Path) -> None:
    env_path = root / ".env"
    if env_path.exists():
        load_dotenv(env_path, override=False)


def _build_model_config() -> ModelConfig:
    """从环境变量构造 ModelConfig。"""
    return ModelConfig(
        model_provider="openai",
        model=os.environ.get("GRAPHRAG_EXTRACTION_MODEL", "deepseek-v4-flash"),
        api_base=os.environ.get("GRAPHRAG_API_BASE", "http://127.0.0.1:3301/v1"),
        api_key=os.environ.get("GRAPHRAG_CHAT_API_KEY", ""),
        auth_method="api_key",
    )


def _dataframes_to_structured_result(
    *,
    entities_df: pd.DataFrame,
    relationships_df: pd.DataFrame,
    sample_id: str,
    candidate: str,
    raw_output: str = "",
    usage: dict[str, Any] | None = None,
    entity_type_map: dict[str, str] | None = None,
    strict: bool = False,
) -> StructuredExtractionResult:
    """把 GraphExtractor 返回的 DataFrame 转成 StructuredExtractionResult。

    entity_type_map 用于把 GraphRAG 原生 `_process_result` 强制大写化后的 type
    （如 `COURSE`）映射回 schema 定义的 PascalCase（如 `Course`）。这是 GraphRAG
    解析器的确定性行为，映射规则单调（每个 upper 值只对应一个 schema 命名），
    不掩盖任何 prompt 缺陷，因此在任何 mode 下默认开启。

    strict=True 时禁用**引号剥离**等可能掩盖 prompt 格式缺陷的容错，让输出原样
    参与评分；strict=False（默认）则剥去成对引号以便诊断轻微抖动。
    """

    type_map = entity_type_map or {}

    def _maybe_strip_quotes(text: str) -> str:
        if strict:
            return text
        stripped = text.strip()
        for quote in ('"', "'", "“", "”", "‘", "’"):
            if stripped.startswith(quote) and stripped.endswith(quote) and len(stripped) > 1:
                stripped = stripped[1:-1].strip()
                break
        return stripped

    def _canonicalize_entity_type(raw_type: str) -> str:
        """把 entity_type 从 GraphRAG 原生大写化后映射回 schema 命名。

        规则：
        1. 先剥去 type 字段两端的引号（模型可能输出 `"CONCEPT"`）。
           这一步和 strict 模式无关，因为 schema 永远不会把引号当作合法
           type 字符，剥引号是纯粹的规范化。
        2. 用 upper 作为 map key 查表；命中则取 schema canonical 命名。
        3. 未命中（比如模型给了同义词 `TOPIC`）时保留原值，交由
           entity_type_valid_rate / schema_hit_rate 如实反映 schema 越界。
        """
        stripped = raw_type.strip()
        for quote in ('"', "'", "“", "”", "‘", "’"):
            if stripped.startswith(quote) and stripped.endswith(quote) and len(stripped) > 1:
                stripped = stripped[1:-1].strip()
                break
        upper = stripped.upper()
        return type_map.get(upper, stripped)

    entities: list[ExtractionEntity] = []
    for idx, row in entities_df.iterrows():
        raw_title = str(row.get("title") or "")
        raw_type = str(row.get("type") or "")
        title = _maybe_strip_quotes(raw_title)
        normalized_type = _canonicalize_entity_type(raw_type)
        entities.append(ExtractionEntity(
            id=f"native-{idx}",
            title=title,
            type=normalized_type,
            alias=[],
            definition_text="",
            description=str(row.get("description") or ""),
            evidence="",
        ))

    relationships: list[ExtractionRelationship] = []
    for _, row in relationships_df.iterrows():
        description = str(row.get("description") or "")
        rel_type = _extract_relation_type_from_description(description)
        source = _maybe_strip_quotes(str(row.get("source") or ""))
        target = _maybe_strip_quotes(str(row.get("target") or ""))
        relationships.append(ExtractionRelationship(
            source=source,
            target=target,
            type=rel_type,
            description=description,
            evidence="",
        ))

    return StructuredExtractionResult(
        sample_id=sample_id,
        candidate=candidate,
        status="success",
        entities=entities,
        relationships=relationships,
        raw_output=raw_output,
        llm_debug={"usage": usage} if usage else None,
    )


async def _extract_one_sample(
    *,
    extractor: GraphExtractor,
    text: str,
    entity_types: list[str],
    sample_id: str,
    candidate: str,
    entity_type_map: dict[str, str],
    strict: bool = False,
) -> StructuredExtractionResult:
    """对单个样本执行原生抽取。"""
    try:
        entities_df, relationships_df = await extractor(
            text=text.strip(),
            entity_types=entity_types,
            source_id=sample_id,
        )
        return _dataframes_to_structured_result(
            entities_df=entities_df,
            relationships_df=relationships_df,
            sample_id=sample_id,
            candidate=candidate,
            entity_type_map=entity_type_map,
            strict=strict,
        )
    except Exception as e:
        logger.error("抽取失败 sample_id=%s: %s", sample_id, e)
        return StructuredExtractionResult(
            sample_id=sample_id,
            candidate=candidate,
            status="llm_error",
            entities=[],
            relationships=[],
            raw_output="",
            error=str(e),
        )


async def run_native_extraction(
    *,
    root: Path,
    samples_file: str | Path | None,
    prompt_path: str | Path,
    entity_types: list[str],
    candidate_name: str,
    max_gleanings: int = 1,
    limit: int | None = None,
    run_id: str | None = None,
    overwrite: bool = False,
    strict: bool = False,
) -> dict[str, Any]:
    """执行原生 GraphExtractor 抽取实验。"""

    root = root.resolve()
    _load_env(root)

    samples_path = resolve_samples_path(samples_file, root=root)
    samples = load_samples(samples_path, limit=limit)
    if not samples:
        raise ValueError("样本列表为空")

    prompt_file = Path(prompt_path)
    if not prompt_file.is_absolute():
        prompt_file = (root / prompt_file).resolve()
    prompt_text = prompt_file.read_text(encoding="utf-8")

    model_config = _build_model_config()
    model = create_completion(model_config)

    extractor = GraphExtractor(
        model=model,
        prompt=prompt_text,
        max_gleanings=max_gleanings,
    )

    # 构建大写 -> 原始 case 的映射表
    entity_type_map: dict[str, str] = {t.upper(): t for t in entity_types}

    logger.info(
        "开始原生抽取：candidate=%s samples=%d max_gleanings=%d strict=%s",
        candidate_name,
        len(samples),
        max_gleanings,
        strict,
    )

    results: list[StructuredExtractionResult] = []
    for i, sample in enumerate(samples):
        sample_id = str(sample.get("sample_id") or f"sample-{i}")
        text = str(sample.get("text") or "")
        if not text.strip():
            results.append(StructuredExtractionResult(
                sample_id=sample_id,
                candidate=candidate_name,
                status="parse_error",
                entities=[],
                relationships=[],
                raw_output="",
                error="empty text",
            ))
            continue

        logger.info("  [%d/%d] %s ...", i + 1, len(samples), sample_id)
        result = await _extract_one_sample(
            extractor=extractor,
            text=text,
            entity_types=entity_types,
            sample_id=sample_id,
            candidate=candidate_name,
            entity_type_map=entity_type_map,
            strict=strict,
        )
        results.append(result)

    output = write_candidate_outputs(
        root=root,
        candidate_name=candidate_name,
        results=results,
        samples_file=samples_path,
        manifest_file=prompt_file,  # 用 prompt 文件路径代替 manifest
        model=model_config.model,
        overwrite=overwrite,
        run_id=run_id,
    )

    success_count = sum(1 for r in results if r.status == "success")
    logger.info("原生抽取完成：success=%d/%d", success_count, len(results))

    return {
        "status": "success",
        "candidate": candidate_name,
        "run_id": run_id,
        "sample_count": len(results),
        "success_count": success_count,
        "prompt_path": str(prompt_file),
        "max_gleanings": max_gleanings,
        "entity_types": entity_types,
        "output_paths": {name: str(path) for name, path in output["paths"].items()},
    }


def _build_parser() -> argparse.ArgumentParser:
    parser = argparse.ArgumentParser(
        description="使用 GraphRAG 原生 GraphExtractor 执行候选 Prompt 抽取实验"
    )
    parser.add_argument(
        "--root",
        default=str(PROJECT_ROOT),
        help="GraphRAG 模块根目录",
    )
    parser.add_argument(
        "--samples-file",
        default=None,
        help="样本文件路径（默认 data/eval/material_7_audit_extraction_set.json）",
    )
    parser.add_argument(
        "--prompt",
        required=True,
        help="Prompt 文件路径（如 prompts/extract_graph.txt 或 prompts/candidates/default/prompt.txt）",
    )
    parser.add_argument(
        "--entity-types",
        required=True,
        help="逗号分隔的实体类型列表",
    )
    parser.add_argument(
        "--candidate-name",
        required=True,
        help="候选名称（用于输出文件命名）",
    )
    parser.add_argument(
        "--max-gleanings",
        type=int,
        default=1,
        help="Gleaning 轮数（默认 1）",
    )
    parser.add_argument("--limit", type=int, default=None, help="限制样本数")
    parser.add_argument("--run-id", default=None, help="自定义 run_id")
    parser.add_argument("--overwrite", action="store_true", help="覆盖已有输出")
    parser.add_argument(
        "--strict",
        action="store_true",
        help="严格模式：不做引号剥离、不做 type 容错映射，暴露 prompt 真实缺陷（用于评估 prompt 格式稳定性）",
    )
    return parser


def main(argv: Sequence[str] | None = None) -> int:
    logging.basicConfig(level=logging.INFO, format="%(asctime)s %(levelname)s %(message)s")
    args = _build_parser().parse_args(argv)
    entity_types = [t.strip() for t in args.entity_types.split(",") if t.strip()]
    summary = asyncio.run(run_native_extraction(
        root=Path(args.root),
        samples_file=args.samples_file,
        prompt_path=args.prompt,
        entity_types=entity_types,
        candidate_name=args.candidate_name,
        max_gleanings=args.max_gleanings,
        limit=args.limit,
        run_id=args.run_id,
        overwrite=args.overwrite,
        strict=args.strict,
    ))
    print(json.dumps(summary, ensure_ascii=False, indent=2))
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
