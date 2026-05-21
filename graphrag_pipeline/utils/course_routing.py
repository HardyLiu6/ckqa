#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
课程画像路由服务
================
提供 Java 内部调用的课程画像向量写入与候选课程推荐能力。
"""

from __future__ import annotations

import math
import os
import re
from dataclasses import dataclass, field
from pathlib import Path
from typing import Any, Callable, Mapping, Sequence

import requests
from pydantic import BaseModel, Field

from runtime_defaults import PROJECT_ROOT
from course_profile_hints import CourseProfileHintsResult, extract_course_profile_hints


DEFAULT_EMBEDDING_MODEL = "text-embedding-v4"
DEFAULT_EMBEDDING_DIMENSION = 1024
DEFAULT_ROUTER_LANCEDB_DIR = PROJECT_ROOT / "runtime" / "course-router" / "lancedb"


def model_slug(model: str) -> str:
    """把模型名转换成可用于表名和向量 id 的稳定 slug。"""
    slug = re.sub(r"[^a-zA-Z0-9]+", "_", model.strip().lower()).strip("_")
    return slug or "unknown_model"


def default_table_name_for_model(model: str) -> str:
    return f"course_profiles_{model_slug(model)}"


def _resolve_path(raw_value: str | None, root_dir: Path, default: Path) -> str:
    if raw_value is None or not raw_value.strip():
        return str(default.resolve())
    value = raw_value.strip()
    if "://" in value or value == ":memory:":
        return value
    path = Path(value).expanduser()
    if not path.is_absolute():
        path = root_dir / path
    return str(path.resolve())


@dataclass(frozen=True)
class CourseRoutingConfig:
    """课程画像路由运行时配置，不包含任何对外可见的密钥字段。"""

    api_base: str
    embedding_model: str = DEFAULT_EMBEDDING_MODEL
    embedding_api_key: str = ""
    embedding_dimension: int = DEFAULT_EMBEDDING_DIMENSION
    embedding_output_type: str = "dense"
    lancedb_uri: str = field(default_factory=lambda: str(DEFAULT_ROUTER_LANCEDB_DIR.resolve()))
    table_name: str = default_table_name_for_model(DEFAULT_EMBEDDING_MODEL)
    request_timeout_seconds: float = 30.0

    @classmethod
    def from_env(
        cls,
        environ: Mapping[str, str] | None = None,
        *,
        root_dir: Path = PROJECT_ROOT,
    ) -> "CourseRoutingConfig":
        env = os.environ if environ is None else environ
        embedding_model = (env.get("GRAPHRAG_EMBEDDING_MODEL") or DEFAULT_EMBEDDING_MODEL).strip()
        table_name = (
            env.get("GRAPHRAG_COURSE_ROUTER_TABLE")
            or env.get("GRAPHRAG_COURSE_ROUTING_TABLE")
            or default_table_name_for_model(embedding_model)
        ).strip()
        expected_slug = model_slug(embedding_model)
        if expected_slug not in table_name:
            raise ValueError(f"课程画像表名必须包含 embedding 模型 slug: {expected_slug}")

        return cls(
            api_base=(env.get("GRAPHRAG_API_BASE") or "").strip().rstrip("/"),
            embedding_model=embedding_model,
            embedding_api_key=(env.get("GRAPHRAG_EMBEDDING_API_KEY") or "").strip(),
            embedding_dimension=int(
                env.get("GRAPHRAG_EMBEDDING_DIMENSION")
                or env.get("GRAPHRAG_EMBEDDING_DIMENSIONS")
                or DEFAULT_EMBEDDING_DIMENSION
            ),
            embedding_output_type=(env.get("GRAPHRAG_EMBEDDING_OUTPUT_TYPE") or "dense").strip() or "dense",
            lancedb_uri=_resolve_path(
                env.get("GRAPHRAG_COURSE_ROUTER_LANCEDB_URI") or env.get("GRAPHRAG_COURSE_ROUTING_LANCEDB_URI"),
                root_dir,
                root_dir / "runtime" / "course-router" / "lancedb",
            ),
            table_name=table_name,
        )


class CourseProfileInput(BaseModel):
    courseId: str = Field(min_length=1)
    courseName: str = Field(min_length=1)
    profileText: str = Field(min_length=1)
    profileHash: str = Field(min_length=1)
    metadata: dict[str, Any] = Field(default_factory=dict)


class CourseProfileVectorResult(BaseModel):
    courseId: str
    courseName: str
    profileHash: str
    vectorId: str


class CourseProfileUpsertResult(BaseModel):
    items: list[CourseProfileVectorResult]


class CourseProfilePruneResult(BaseModel):
    dryRun: bool
    inspectedCount: int
    deletedCount: int
    keptVectorIds: list[str]
    staleVectorIds: list[str]


class RecommendRequest(BaseModel):
    question: str = Field(min_length=1)
    courseIds: list[str] | None = None
    limit: int = Field(default=5, ge=1, le=50)


class ProfileHintsRequest(BaseModel):
    courseId: str = Field(min_length=1)
    dataDirUris: list[str] = Field(default_factory=list)
    sectionDocsPaths: list[str] = Field(default_factory=list)
    textUnitsPaths: list[str] = Field(default_factory=list)
    seedKeywords: list[str] = Field(default_factory=list)
    maxHints: int = Field(default=24, ge=1, le=200)


class CourseRecommendation(BaseModel):
    courseId: str
    courseName: str
    confidence: float
    reason: str
    profileHash: str


class RecommendResult(BaseModel):
    candidates: list[CourseRecommendation]


class OpenAICompatibleEmbeddingClient:
    """调用 OpenAI-compatible `/embeddings` 的轻量客户端。"""

    def __init__(
        self,
        config: CourseRoutingConfig,
        *,
        post: Callable[..., Any] | None = None,
    ) -> None:
        self.config = config
        self._post = post or requests.post

    async def embed(self, text: str) -> list[float]:
        # 课程画像路由是 Java 内部低频调用；保持同步请求可避免
        # 某些本地沙箱/conda 组合在关闭默认线程池时卡住。
        return self._embed_sync(text)

    def _embed_sync(self, text: str) -> list[float]:
        if not self.config.api_base:
            raise ValueError("GRAPHRAG_API_BASE is required for course routing embeddings.")
        headers = {"Authorization": f"Bearer {self.config.embedding_api_key}"} if self.config.embedding_api_key else {}
        payload: dict[str, Any] = {
            "model": self.config.embedding_model,
            "input": text,
            "dimensions": self.config.embedding_dimension,
            "encoding_format": "float",
        }
        if self.config.embedding_output_type:
            payload["output_type"] = self.config.embedding_output_type
        response = self._post(
            f"{self.config.api_base}/embeddings",
            headers=headers,
            json=payload,
            timeout=self.config.request_timeout_seconds,
        )
        response.raise_for_status()
        payload = response.json()
        data = payload.get("data") if isinstance(payload, dict) else None
        if not data:
            raise ValueError("Embedding response missing data.")
        vector = data[0].get("embedding")
        if not isinstance(vector, list):
            raise ValueError("Embedding response missing vector.")
        return [float(item) for item in vector]


def _cosine_similarity(left: Sequence[float], right: Sequence[float]) -> float:
    numerator = sum(a * b for a, b in zip(left, right))
    left_norm = math.sqrt(sum(a * a for a in left))
    right_norm = math.sqrt(sum(b * b for b in right))
    if left_norm == 0 or right_norm == 0:
        return 0.0
    return numerator / (left_norm * right_norm)


class InMemoryCourseProfileStore:
    """测试与无 LanceDB 场景使用的课程画像存储。"""

    def __init__(self, *, expected_model: str, expected_table: str) -> None:
        self.expected_model = expected_model
        self.expected_table = expected_table
        self._rows: dict[str, dict[str, Any]] = {}

    def readiness(self) -> dict[str, Any]:
        return {"backend": "memory", "available": True}

    def upsert(self, row: dict[str, Any]) -> str:
        self._validate_row(row)
        self._rows[row["vector_id"]] = dict(row)
        return row["vector_id"]

    def list_profiles(self, *, course_ids: set[str] | None) -> list[dict[str, Any]]:
        return [
            dict(row)
            for row in self._rows.values()
            if course_ids is None or row["course_id"] in course_ids
        ]

    def delete_profiles(self, vector_ids: Sequence[str]) -> int:
        deleted = 0
        for vector_id in vector_ids:
            if vector_id in self._rows:
                del self._rows[vector_id]
                deleted += 1
        return deleted

    def search(
        self,
        vector: Sequence[float],
        *,
        course_ids: set[str] | None,
        limit: int,
    ) -> list[dict[str, Any]]:
        rows = [
            row
            for row in self._rows.values()
            if course_ids is None or row["course_id"] in course_ids
        ]
        ranked = sorted(
            rows,
            key=lambda row: _cosine_similarity(vector, row["vector"]),
            reverse=True,
        )
        result: list[dict[str, Any]] = []
        for row in ranked[:limit]:
            item = dict(row)
            item["_confidence"] = _cosine_similarity(vector, row["vector"])
            result.append(item)
        return result

    def _validate_row(self, row: dict[str, Any]) -> None:
        if row.get("embedding_model") != self.expected_model:
            raise ValueError("embedding_model 与当前课程画像表不匹配")
        if row.get("table_name") != self.expected_table:
            raise ValueError("table_name 与当前课程画像表不匹配")


class LanceDBCourseProfileStore:
    """懒加载 LanceDB 的课程画像存储，避免核心安装缺 graph extra 时启动失败。"""

    def __init__(self, *, uri: str, table_name: str, expected_model: str, expected_dimension: int) -> None:
        self.uri = uri
        self.table_name = table_name
        self.expected_model = expected_model
        self.expected_dimension = expected_dimension
        self._db = None

    def readiness(self) -> dict[str, Any]:
        try:
            self._import_lancedb()
        except ImportError:
            return {"backend": "lancedb", "available": False, "missing": ["lancedb"]}
        return {"backend": "lancedb", "available": True}

    def upsert(self, row: dict[str, Any]) -> str:
        self._validate_row(row)
        table = self._get_or_create_table(row)
        try:
            table.delete(f"vector_id = {_lance_string_literal(row['vector_id'])}")
        except Exception:
            pass
        table.add([row])
        return row["vector_id"]

    def list_profiles(self, *, course_ids: set[str] | None) -> list[dict[str, Any]]:
        table = self._open_table()
        rows = self._read_all_rows(table)
        result: list[dict[str, Any]] = []
        for row in rows:
            if row.get("embedding_model") != self.expected_model:
                continue
            if row.get("table_name") != self.table_name:
                continue
            if int(row.get("embedding_dimension") or 0) != self.expected_dimension:
                continue
            if course_ids is not None and row.get("course_id") not in course_ids:
                continue
            result.append(dict(row))
        return result

    def delete_profiles(self, vector_ids: Sequence[str]) -> int:
        table = self._open_table()
        deleted = 0
        for vector_id in vector_ids:
            if not vector_id:
                continue
            table.delete(f"vector_id = {_lance_string_literal(vector_id)}")
            deleted += 1
        return deleted

    def search(
        self,
        vector: Sequence[float],
        *,
        course_ids: set[str] | None,
        limit: int,
    ) -> list[dict[str, Any]]:
        table = self._open_table()
        query = table.search(list(vector))
        try:
            query = query.metric("cosine")
        except AttributeError:
            pass
        rows = query.limit(max(limit * 5, limit)).to_list()
        result: list[dict[str, Any]] = []
        for row in rows:
            if row.get("embedding_model") != self.expected_model:
                continue
            if course_ids is not None and row.get("course_id") not in course_ids:
                continue
            item = dict(row)
            distance = float(item.get("_distance", 0.0) or 0.0)
            item["_confidence"] = max(0.0, min(1.0, 1.0 - distance))
            result.append(item)
            if len(result) >= limit:
                break
        return result

    def _import_lancedb(self):
        try:
            import lancedb  # type: ignore
        except ImportError as exc:  # pragma: no cover - 取决于安装 extra
            raise ImportError("lancedb is required for course routing store.") from exc
        return lancedb

    def _connect(self):
        if self._db is None:
            lancedb = self._import_lancedb()
            self._db = lancedb.connect(self.uri)
        return self._db

    def _open_table(self):
        db = self._connect()
        return db.open_table(self.table_name)

    def _get_or_create_table(self, row: dict[str, Any]):
        db = self._connect()
        try:
            table_names = set(db.table_names())
        except AttributeError:
            table_names = set()
        if self.table_name in table_names:
            return db.open_table(self.table_name)
        return db.create_table(self.table_name, data=[row])

    def _read_all_rows(self, table) -> list[dict[str, Any]]:  # noqa: ANN001
        try:
            return [dict(row) for row in table.to_arrow().to_pylist()]
        except AttributeError:
            return [dict(row) for row in table.to_pandas().to_dict("records")]

    def _validate_row(self, row: dict[str, Any]) -> None:
        if row.get("embedding_model") != self.expected_model:
            raise ValueError("embedding_model 与当前课程画像表不匹配")
        if row.get("table_name") != self.table_name:
            raise ValueError("table_name 与当前课程画像表不匹配")
        if int(row.get("embedding_dimension") or 0) != self.expected_dimension:
            raise ValueError("embedding_dimension 与当前课程画像表不匹配")


def _lance_string_literal(value: str) -> str:
    return "'" + value.replace("\\", "\\\\").replace("'", "\\'") + "'"


class CourseRoutingService:
    """课程画像路由应用服务。"""

    def __init__(
        self,
        *,
        config: CourseRoutingConfig,
        embedding_client: OpenAICompatibleEmbeddingClient | Any | None = None,
        store: InMemoryCourseProfileStore | LanceDBCourseProfileStore | None = None,
    ) -> None:
        self.config = config
        self.embedding_client = embedding_client or OpenAICompatibleEmbeddingClient(config)
        self.store = store or LanceDBCourseProfileStore(
            uri=config.lancedb_uri,
            table_name=config.table_name,
            expected_model=config.embedding_model,
            expected_dimension=config.embedding_dimension,
        )

    @classmethod
    def from_env(cls) -> "CourseRoutingService":
        config = CourseRoutingConfig.from_env()
        return cls(config=config)

    def readiness(self) -> dict[str, Any]:
        store_readiness = self.store.readiness()
        return {
            "ready": bool(self.config.api_base) and bool(store_readiness.get("available")),
            "status": "ready" if bool(self.config.api_base) and bool(store_readiness.get("available")) else "not_ready",
            "embeddingModel": self.config.embedding_model,
            "embeddingDimension": self.config.embedding_dimension,
            "embeddingOutputType": self.config.embedding_output_type,
            "lancedbUri": self.config.lancedb_uri,
            "tableName": self.config.table_name,
            "embeddingApiBaseConfigured": bool(self.config.api_base),
            "store": store_readiness,
        }

    async def upsert_profiles(self, profiles: Sequence[CourseProfileInput]) -> CourseProfileUpsertResult:
        items: list[CourseProfileVectorResult] = []
        for profile in profiles:
            vector = await self.embedding_client.embed(profile.profileText)
            self._validate_vector(vector)
            vector_id = f"{profile.courseId}:{model_slug(self.config.embedding_model)}:{profile.profileHash}"
            stored_id = self.store.upsert(
                {
                    "vector": vector,
                    "vector_id": vector_id,
                    "course_id": profile.courseId,
                    "course_name": profile.courseName,
                    "profile_text": profile.profileText,
                    "profile_hash": profile.profileHash,
                    "metadata": dict(profile.metadata or {}),
                    "embedding_model": self.config.embedding_model,
                    "embedding_dimension": self.config.embedding_dimension,
                    "table_name": self.config.table_name,
                }
            )
            items.append(
                CourseProfileVectorResult(
                    courseId=profile.courseId,
                    courseName=profile.courseName,
                    profileHash=profile.profileHash,
                    vectorId=stored_id,
                )
            )
        return CourseProfileUpsertResult(items=items)

    async def recommend(self, request: RecommendRequest) -> RecommendResult:
        vector = await self.embedding_client.embed(request.question)
        self._validate_vector(vector)
        rows = self.store.search(
            vector,
            course_ids=set(request.courseIds) if request.courseIds else None,
            limit=max(request.limit * 5, request.limit),
        )
        deduped_rows: list[dict[str, Any]] = []
        seen_course_ids: set[str] = set()
        for row in rows:
            course_id = str(row.get("course_id") or "")
            if not course_id or course_id in seen_course_ids:
                continue
            seen_course_ids.add(course_id)
            deduped_rows.append(row)
            if len(deduped_rows) >= request.limit:
                break
        candidates = [
            CourseRecommendation(
                courseId=row["course_id"],
                courseName=row["course_name"],
                confidence=round(float(row.get("_confidence", 0.0)), 6),
                reason=f"课程画像相似度 {float(row.get('_confidence', 0.0)):.3f}",
                profileHash=row["profile_hash"],
            )
            for row in deduped_rows
        ]
        return RecommendResult(candidates=candidates)

    def prune_stale_profiles(
        self,
        *,
        keep_vector_ids: Sequence[str],
        course_ids: Sequence[str] | None = None,
        dry_run: bool = True,
    ) -> CourseProfilePruneResult:
        keep_set = {item.strip() for item in keep_vector_ids if item and item.strip()}
        if not keep_set:
            raise ValueError("keep_vector_ids is required for safe course profile cleanup.")
        course_id_set = {item.strip() for item in course_ids or [] if item and item.strip()}
        rows = self.store.list_profiles(course_ids=course_id_set or None)
        stale_vector_ids: list[str] = []
        kept_vector_ids: list[str] = []
        for row in rows:
            vector_id = str(row.get("vector_id") or "")
            if not vector_id:
                continue
            if vector_id in keep_set:
                kept_vector_ids.append(vector_id)
            else:
                stale_vector_ids.append(vector_id)
        deleted_count = 0 if dry_run or not stale_vector_ids else self.store.delete_profiles(stale_vector_ids)
        return CourseProfilePruneResult(
            dryRun=dry_run,
            inspectedCount=len(rows),
            deletedCount=deleted_count,
            keptVectorIds=sorted(kept_vector_ids),
            staleVectorIds=sorted(stale_vector_ids),
        )

    async def extract_profile_hints(self, request: ProfileHintsRequest) -> CourseProfileHintsResult:
        data_dirs: list[Path] = []
        for item in request.dataDirUris:
            data_dirs.append(self._resolve_data_dir_uri(item))
        return extract_course_profile_hints(
            course_id=request.courseId,
            section_docs_paths=[Path(item) for item in request.sectionDocsPaths],
            text_units_paths=[Path(item) for item in request.textUnitsPaths],
            data_dirs=data_dirs,
            seed_keywords=request.seedKeywords,
            max_hints=request.maxHints,
        )

    def _validate_vector(self, vector: Sequence[float]) -> None:
        if len(vector) != self.config.embedding_dimension:
            raise ValueError(
                f"embedding 维度不匹配: expected={self.config.embedding_dimension}, actual={len(vector)}"
            )

    def _resolve_data_dir_uri(self, raw_value: str) -> Path:
        value = (raw_value or "").strip()
        if not value:
            raise ValueError("dataDirUri 不能为空")
        path = Path(value)
        if path.is_absolute() or ".." in path.parts:
            raise ValueError("dataDirUri 必须是 build-runs root 下的安全相对路径")
        build_runs_root = Path(os.getenv("GRAPHRAG_BUILD_RUNS_ROOT", PROJECT_ROOT / "runtime" / "kb-build-runs")).resolve()
        resolved = (build_runs_root / path).resolve()
        try:
            resolved.relative_to(build_runs_root)
        except ValueError as exc:
            raise ValueError("dataDirUri 必须是 build-runs root 下的安全相对路径") from exc
        return resolved
