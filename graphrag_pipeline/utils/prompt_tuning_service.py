#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""提示词调优服务接口。

为后端（Java）提供生产环境可用的提示词调优 REST API。

核心流程：
1. 上传课程材料样本 → 构建样本池
2. 从样本池中抽样 → 构建 audit 评测集
3. 使用冻结的生产 prompt 执行抽取
4. 对抽取结果进行评分
5. 返回评分报告

设计原则：
- 生产 prompt 已冻结（frozen_v1），不会被自动重新生成
- 调优流程是"评估当前 prompt 在新课程上的表现"，而非"生成新 prompt"
- 如需生成新 prompt，需要人工介入（通过 generate_candidate_prompts.py）
"""

from __future__ import annotations

import json
import logging
import os
import sys
from pathlib import Path
from typing import Any

from pydantic import BaseModel, Field

# 确保 scripts 目录在 path 中
_PROJECT_ROOT = Path(__file__).resolve().parents[1]
_SCRIPTS_DIR = _PROJECT_ROOT / "scripts"
if str(_SCRIPTS_DIR) not in sys.path:
    sys.path.insert(0, str(_SCRIPTS_DIR))

logger = logging.getLogger(__name__)


# ---------------------------------------------------------------------------
# 配置
# ---------------------------------------------------------------------------

PRODUCTION_PROMPT_PATH = _PROJECT_ROOT / "prompts" / "candidates" / "schema_fewshot_distilled_v2_strict_tuple" / "prompt.txt"
PRODUCTION_PROMPT_MD5 = "14ab82b37aa379f6026287a491888a44"
DEFAULT_ENTITY_TYPES = [
    "Course", "Chapter", "Section", "KnowledgePoint", "Concept",
    "Term", "FormulaOrDefinition", "AlgorithmOrMethod",
    "Experiment", "Assignment", "ToolOrPlatform",
]
DEFAULT_MAX_GLEANINGS = 1
DEFAULT_CONCURRENCY = 20


# ---------------------------------------------------------------------------
# 请求/响应模型
# ---------------------------------------------------------------------------

class PromptTuningStatusResponse(BaseModel):
    """当前提示词调优状态。"""
    production_prompt: str = Field(description="生产 prompt 文件路径")
    production_prompt_md5: str = Field(description="prompt 文件 MD5")
    production_status: str = Field(description="冻结状态")
    frozen_at: str = Field(description="冻结时间")
    entity_types: list[str] = Field(description="支持的实体类型")
    relation_types: list[str] = Field(description="支持的关系类型")


class ExtractionRequest(BaseModel):
    """抽取请求。"""
    samples: list[dict[str, Any]] = Field(description="待抽取的样本列表，每个样本需包含 sample_id 和 text 字段")
    concurrency: int = Field(default=DEFAULT_CONCURRENCY, description="并发数")
    max_gleanings: int = Field(default=DEFAULT_MAX_GLEANINGS, description="Gleaning 轮数")


class ExtractionResponse(BaseModel):
    """抽取响应。"""
    status: str
    sample_count: int
    success_count: int
    results: list[dict[str, Any]]


class EvaluationRequest(BaseModel):
    """评估请求。"""
    extraction_results: list[dict[str, Any]] = Field(description="抽取结果列表")
    audit_samples: list[dict[str, Any]] = Field(description="带 gold 标注的评测样本")


class EvaluationResponse(BaseModel):
    """评估响应。"""
    status: str
    metrics: dict[str, Any]
    gate_passed: bool
    details: dict[str, Any] | None = None


# ---------------------------------------------------------------------------
# 服务实现
# ---------------------------------------------------------------------------

def get_prompt_tuning_status() -> PromptTuningStatusResponse:
    """获取当前提示词调优状态。"""
    # 加载 schema
    entity_schema_path = _PROJECT_ROOT / "config" / "schema" / "entity_types.json"
    relation_schema_path = _PROJECT_ROOT / "config" / "schema" / "relation_types.json"

    entity_types = list(json.loads(entity_schema_path.read_text(encoding="utf-8")).get("entity_types", {}).keys())
    relation_types = list(json.loads(relation_schema_path.read_text(encoding="utf-8")).get("relation_types", {}).keys())

    return PromptTuningStatusResponse(
        production_prompt=str(PRODUCTION_PROMPT_PATH.relative_to(_PROJECT_ROOT)),
        production_prompt_md5=PRODUCTION_PROMPT_MD5,
        production_status="frozen_v1",
        frozen_at="2026-05-12",
        entity_types=entity_types,
        relation_types=relation_types,
    )


async def run_extraction(request: ExtractionRequest) -> ExtractionResponse:
    """使用生产 prompt 执行抽取。"""
    from extraction_eval.run_native_extraction import run_native_extraction

    if not PRODUCTION_PROMPT_PATH.exists():
        raise FileNotFoundError(f"生产 prompt 不存在: {PRODUCTION_PROMPT_PATH}")

    # 写入临时样本文件
    import tempfile
    samples_payload = {"schema_version": "v1", "samples": request.samples}
    with tempfile.NamedTemporaryFile(
        mode="w", suffix=".json", dir=str(_PROJECT_ROOT / "data"),
        delete=False, encoding="utf-8",
    ) as f:
        json.dump(samples_payload, f, ensure_ascii=False)
        samples_file = f.name

    try:
        summary = await run_native_extraction(
            root=_PROJECT_ROOT,
            samples_file=samples_file,
            prompt_path=str(PRODUCTION_PROMPT_PATH),
            entity_types=DEFAULT_ENTITY_TYPES,
            candidate_name="production_v1",
            max_gleanings=request.max_gleanings,
            run_id=None,
            overwrite=True,
            strict=False,
            concurrency=request.concurrency,
        )

        # 读取结果
        output_paths = summary.get("output_paths", {})
        results_path = output_paths.get("eval_json")
        if results_path and Path(results_path).exists():
            results_data = json.loads(Path(results_path).read_text(encoding="utf-8"))
            results = results_data.get("results", [])
        else:
            results = []

        return ExtractionResponse(
            status="success",
            sample_count=summary.get("sample_count", 0),
            success_count=summary.get("success_count", 0),
            results=results,
        )
    finally:
        os.unlink(samples_file)


def run_evaluation(request: EvaluationRequest) -> EvaluationResponse:
    """对抽取结果进行评估。"""
    from extraction_eval.scoring_audit import (
        AuditEntry,
        compute_audit_entity_recall,
        compute_audit_entity_precision,
        compute_audit_relation_recall,
        compute_faithfulness_error_rate,
    )
    from extraction_eval.extraction_schema import StructuredExtractionResult

    # 构建 audit index
    audit_index: dict[str, AuditEntry] = {}
    for sample in request.audit_samples:
        sample_id = str(sample.get("source_sample_id") or sample.get("sample_id") or "").strip()
        if not sample_id:
            continue
        heading_path_list = sample.get("heading_path") or []
        metadata_ctx: dict[str, str] = {}
        if sample.get("course_id"):
            metadata_ctx["course_id"] = str(sample["course_id"])
        if sample.get("source_file"):
            metadata_ctx["source_file"] = str(sample["source_file"])
        if sample.get("chapter"):
            metadata_ctx["chapter"] = str(sample["chapter"])
        if sample.get("section"):
            metadata_ctx["section"] = str(sample["section"])
        if heading_path_list:
            metadata_ctx["heading_path"] = "|".join(str(h) for h in heading_path_list)

        audit_index[sample_id] = AuditEntry(
            gold_entities=list(sample.get("gold_entities") or []),
            gold_relations=list(sample.get("gold_relations") or []),
            gold_seed=bool(sample.get("gold_seed")),
            gold_seed_version=str(sample.get("gold_seed_version") or ""),
            source_text=str(sample.get("text") or ""),
            metadata_context=metadata_ctx,
        )

    # 解析抽取结果
    results = [StructuredExtractionResult(**r) for r in request.extraction_results]

    # 计算指标
    entity_recall = compute_audit_entity_recall(results, audit_index)
    entity_precision = compute_audit_entity_precision(results, audit_index)
    relation_recall = compute_audit_relation_recall(results, audit_index)
    faithfulness_err = compute_faithfulness_error_rate(results, audit_index)

    # Gate 判定
    gate_passed = faithfulness_err <= 0.15

    metrics = {
        "audit_entity_recall": round(entity_recall, 4),
        "audit_entity_precision": round(entity_precision, 4),
        "audit_relation_recall": round(relation_recall, 4),
        "faithfulness_error_rate": round(faithfulness_err, 4),
        "gate_passed": gate_passed,
        "sample_count": len(results),
        "success_count": sum(1 for r in results if r.status == "success"),
    }

    return EvaluationResponse(
        status="success",
        metrics=metrics,
        gate_passed=gate_passed,
    )


# ---------------------------------------------------------------------------
# FastAPI 路由注册
# ---------------------------------------------------------------------------

def register_prompt_tuning_routes(app):
    """将提示词调优路由注册到 FastAPI app。"""
    from fastapi import HTTPException

    @app.get("/v1/prompt-tuning/status")
    async def get_status():
        """获取当前提示词调优状态和配置。"""
        return get_prompt_tuning_status()

    @app.post("/v1/prompt-tuning/extract")
    async def extract(request: ExtractionRequest):
        """使用生产 prompt 对样本执行知识图谱抽取。"""
        if not request.samples:
            raise HTTPException(status_code=400, detail="samples 不能为空")
        for i, sample in enumerate(request.samples):
            if not sample.get("sample_id"):
                raise HTTPException(status_code=400, detail=f"samples[{i}] 缺少 sample_id")
            if not sample.get("text"):
                raise HTTPException(status_code=400, detail=f"samples[{i}] 缺少 text")
        try:
            return await run_extraction(request)
        except Exception as e:
            logger.exception("抽取失败")
            raise HTTPException(status_code=500, detail=str(e))

    @app.post("/v1/prompt-tuning/evaluate")
    async def evaluate(request: EvaluationRequest):
        """对抽取结果进行质量评估。"""
        if not request.extraction_results:
            raise HTTPException(status_code=400, detail="extraction_results 不能为空")
        try:
            return run_evaluation(request)
        except Exception as e:
            logger.exception("评估失败")
            raise HTTPException(status_code=500, detail=str(e))

    @app.get("/v1/prompt-tuning/prompt")
    async def get_prompt():
        """获取当前生产 prompt 的完整内容。"""
        if not PRODUCTION_PROMPT_PATH.exists():
            raise HTTPException(status_code=404, detail="生产 prompt 文件不存在")
        return {
            "prompt_text": PRODUCTION_PROMPT_PATH.read_text(encoding="utf-8"),
            "md5": PRODUCTION_PROMPT_MD5,
            "status": "frozen_v1",
            "path": str(PRODUCTION_PROMPT_PATH.relative_to(_PROJECT_ROOT)),
        }

    @app.get("/v1/prompt-tuning/schema")
    async def get_schema():
        """获取当前课程知识图谱 schema（实体类型 + 关系类型）。"""
        entity_path = _PROJECT_ROOT / "config" / "schema" / "entity_types.json"
        relation_path = _PROJECT_ROOT / "config" / "schema" / "relation_types.json"
        return {
            "entity_types": json.loads(entity_path.read_text(encoding="utf-8")),
            "relation_types": json.loads(relation_path.read_text(encoding="utf-8")),
        }
