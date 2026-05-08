#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
候选 Prompt / 样本 / Schema 加载器
=================================
"""

from __future__ import annotations

import json
from dataclasses import dataclass
from pathlib import Path
from typing import Any, Iterable, Optional, Sequence

from .extraction_schema import SchemaCatalog, SchemaTypeInfo


DEFAULT_SAMPLES_CANDIDATES: tuple[str, ...] = (
    "data/prompt_tuning_samples/samples.json",
    "data/prompt_tuning_samples/prompt_tuning_samples.json",
    "data/prompt_tuning_samples/prompt_tuning_samples.preview.json",
)
DEFAULT_MANIFEST_PATH = "prompts/candidates/manifest.json"
DEFAULT_ENTITY_SCHEMA_PATH = "config/schema/entity_types.json"
DEFAULT_RELATION_SCHEMA_PATH = "config/schema/relation_types.json"


@dataclass(frozen=True)
class CandidatePrompt:
    """候选 Prompt 元数据。"""

    name: str
    prompt_path: Path
    prompt_text: str
    manifest_entry: dict[str, Any]


def is_fallback_auto_tuned_entry(entry: dict[str, Any]) -> bool:
    """判断候选是否只是 auto_tuned 占位回退，避免默认实验误评伪候选。"""

    return (
        str(entry.get("candidate_name") or "").strip() == "auto_tuned"
        and str(entry.get("source_type") or "").strip() == "fallback_default_copy"
    )


def _read_json(path: Path) -> Any:
    return json.loads(path.read_text(encoding="utf-8"))


def _resolve_path(path: str | Path | None, *, root: Path) -> Optional[Path]:
    if path is None:
        return None
    candidate = path if isinstance(path, Path) else Path(path)
    if candidate.is_absolute():
        return candidate.resolve()
    return (root / candidate).resolve()


def resolve_samples_path(path: str | Path | None, *, root: Path) -> Path:
    """兼容新旧样本文件命名。"""

    explicit = _resolve_path(path, root=root)
    if explicit is not None:
        if not explicit.exists():
            raise FileNotFoundError(f"样本文件不存在：{explicit}")
        return explicit

    for relative_path in DEFAULT_SAMPLES_CANDIDATES:
        candidate = (root / relative_path).resolve()
        if candidate.exists():
            return candidate

    raise FileNotFoundError("未找到样本文件，请通过 --samples 指定路径")


def resolve_manifest_path(path: str | Path | None, *, root: Path) -> Path:
    explicit = _resolve_path(path, root=root) or (root / DEFAULT_MANIFEST_PATH).resolve()
    if not explicit.exists():
        raise FileNotFoundError(f"manifest 文件不存在：{explicit}")
    return explicit


def load_samples(samples_path: Path, *, limit: int | None = None) -> list[dict[str, Any]]:
    payload = _read_json(samples_path)
    if isinstance(payload, list):
        records = [_normalize_sample_record(item) for item in payload if isinstance(item, dict)]
    elif isinstance(payload, dict):
        raw_records = (
            payload.get("samples")
            or payload.get("audit_samples")
            or payload.get("records")
            or payload.get("items")
            or []
        )
        if not isinstance(raw_records, list):
            raise ValueError(f"无法从样本文件识别样本列表：{samples_path}")
        records = [_normalize_sample_record(item) for item in raw_records if isinstance(item, dict)]
    else:
        raise ValueError(f"样本文件格式不支持：{samples_path}")

    if limit is not None:
        return records[: max(0, limit)]
    return records


def _normalize_sample_record(record: dict[str, Any]) -> dict[str, Any]:
    normalized = dict(record)
    if not str(normalized.get("sample_id") or "").strip():
        sample_id = str(normalized.get("source_sample_id") or normalized.get("id") or "").strip()
        if sample_id:
            normalized["sample_id"] = sample_id
    return normalized


def load_schema_catalog(
    *,
    entity_schema_path: Path,
    relation_schema_path: Path,
) -> SchemaCatalog:
    entity_payload = _read_json(entity_schema_path)
    relation_payload = _read_json(relation_schema_path)

    entity_order = entity_payload.get("entity_type_order") or list((entity_payload.get("entity_types") or {}).keys())
    relation_order = relation_payload.get("relation_type_order") or list(
        (relation_payload.get("relation_types") or {}).keys()
    )

    entity_types = _build_schema_types(entity_order, entity_payload.get("entity_types") or {})
    relation_types = _build_schema_types(relation_order, relation_payload.get("relation_types") or {})

    return SchemaCatalog(
        schema_version=str(entity_payload.get("schema_version") or relation_payload.get("schema_version") or "v1"),
        entity_types=entity_types,
        relation_types=relation_types,
        entity_schema_path=str(entity_schema_path.resolve()),
        relation_schema_path=str(relation_schema_path.resolve()),
    )


def _build_schema_types(order: Sequence[str], items: dict[str, Any]) -> list[SchemaTypeInfo]:
    result: list[SchemaTypeInfo] = []
    visited: set[str] = set()

    for name in list(order) + list(items.keys()):
        if name in visited:
            continue
        visited.add(name)
        payload = items.get(name) if isinstance(items.get(name), dict) else {}
        result.append(
            SchemaTypeInfo(
                name=name,
                label_zh=str(payload.get("label_zh") or name),
                description=str(payload.get("description") or payload.get("extraction_hint") or "").strip(),
            )
        )
    return result


def load_candidate_prompts(
    manifest_path: Path,
    *,
    root: Path,
    candidate_names: Sequence[str] | None = None,
) -> list[CandidatePrompt]:
    manifest_payload = _read_json(manifest_path)
    raw_candidates = manifest_payload.get("candidates")
    if not isinstance(raw_candidates, list):
        raise ValueError(f"manifest 缺少 candidates 列表：{manifest_path}")

    requested = _normalize_candidate_names(candidate_names)
    loaded: list[CandidatePrompt] = []
    seen_names: set[str] = set()

    for entry in raw_candidates:
        if not isinstance(entry, dict):
            continue
        candidate_name = str(entry.get("candidate_name") or "").strip()
        if not candidate_name:
            continue
        if requested and candidate_name not in requested:
            continue
        prompt_path = _resolve_candidate_prompt_path(entry, root=root)
        prompt_text = prompt_path.read_text(encoding="utf-8")
        loaded.append(
            CandidatePrompt(
                name=candidate_name,
                prompt_path=prompt_path,
                prompt_text=prompt_text,
                manifest_entry=entry,
            )
        )
        seen_names.add(candidate_name)

    if requested:
        missing = [name for name in requested if name not in seen_names]
        if missing:
            raise ValueError(f"manifest 中未找到候选 Prompt：{', '.join(missing)}")

    if not loaded:
        raise ValueError("没有可执行的候选 Prompt")
    return loaded


def _normalize_candidate_names(candidate_names: Sequence[str] | None) -> list[str]:
    if not candidate_names:
        return []
    normalized: list[str] = []
    for raw_name in candidate_names:
        for item in str(raw_name).split(","):
            name = item.strip()
            if name and name not in normalized:
                normalized.append(name)
    return normalized


def _resolve_candidate_prompt_path(entry: dict[str, Any], *, root: Path) -> Path:
    files_payload = entry.get("files") if isinstance(entry.get("files"), dict) else {}
    path_candidates: list[Path] = []

    prompt_file = files_payload.get("prompt") or entry.get("prompt_file")
    if prompt_file:
        resolved = _resolve_path(prompt_file, root=root)
        if resolved is not None:
            path_candidates.append(resolved)

    candidate_name = str(entry.get("candidate_name") or "").strip()
    if candidate_name:
        path_candidates.append((root / "prompts" / "candidates" / candidate_name / "prompt.txt").resolve())
        path_candidates.append((root / "prompts" / "candidates" / candidate_name / "extract_graph.txt").resolve())

    for path in path_candidates:
        if path.exists() and path.is_file():
            return path

    raise FileNotFoundError(f"无法定位候选 Prompt 文件：{candidate_name or entry}")


def default_schema_paths(*, root: Path) -> tuple[Path, Path]:
    return (
        (root / DEFAULT_ENTITY_SCHEMA_PATH).resolve(),
        (root / DEFAULT_RELATION_SCHEMA_PATH).resolve(),
    )
