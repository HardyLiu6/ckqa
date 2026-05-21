#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
课程画像路由服务测试
====================
验证课程画像向量化、表隔离与推荐排序的内部契约。
"""

from __future__ import annotations

import asyncio
import os
import sys
import tempfile
import unittest
from pathlib import Path
from unittest.mock import patch


_PROJECT_ROOT = Path(__file__).resolve().parent.parent
_MODULE_DIR = _PROJECT_ROOT / "utils"
if str(_MODULE_DIR) not in sys.path:
    sys.path.insert(0, str(_MODULE_DIR))

from course_routing import (
    CourseProfileInput,
    ProfileHintsRequest,
    CourseRoutingConfig,
    CourseRoutingService,
    InMemoryCourseProfileStore,
    OpenAICompatibleEmbeddingClient,
    RecommendRequest,
    model_slug,
)


class _FakeEmbeddingClient:
    def __init__(self, vectors: dict[str, list[float]]) -> None:
        self.vectors = vectors
        self.calls: list[tuple[str, str]] = []

    async def embed(self, text: str) -> list[float]:
        self.calls.append(("text-embedding-v4", text))
        return self.vectors[text]


class _DuplicateSearchStore:
    def readiness(self) -> dict[str, object]:
        return {"backend": "fake", "available": True}

    def search(self, vector, *, course_ids, limit):  # noqa: ANN001
        return [
            {
                "course_id": "course-os",
                "course_name": "操作系统",
                "profile_hash": "hash-os-new",
                "_confidence": 0.91,
            },
            {
                "course_id": "course-os",
                "course_name": "操作系统",
                "profile_hash": "hash-os-old",
                "_confidence": 0.88,
            },
            {
                "course_id": "course-db",
                "course_name": "数据库",
                "profile_hash": "hash-db",
                "_confidence": 0.72,
            },
        ][:limit]


class TestCourseRoutingEmbeddingService(unittest.TestCase):
    def test_config_derives_model_isolated_table_and_runtime_lancedb_default(self):
        config = CourseRoutingConfig.from_env(
            {
                "GRAPHRAG_API_BASE": "http://127.0.0.1:3301/v1",
                "GRAPHRAG_EMBEDDING_MODEL": "text-embedding-v4",
                "GRAPHRAG_COURSE_ROUTER_LANCEDB_URI": "runtime/course-router/lancedb",
            },
            root_dir=_PROJECT_ROOT,
        )

        self.assertEqual(config.api_base, "http://127.0.0.1:3301/v1")
        self.assertEqual(config.embedding_model, "text-embedding-v4")
        self.assertEqual(config.embedding_dimension, 1024)
        self.assertEqual(config.embedding_output_type, "dense")
        self.assertEqual(config.table_name, "course_profiles_text_embedding_v4")
        self.assertEqual(config.lancedb_uri, str((_PROJECT_ROOT / "runtime" / "course-router" / "lancedb").resolve()))
        self.assertEqual(model_slug("BAAI/bge-m3"), "baai_bge_m3")

    def test_embedding_client_uses_openai_compatible_embeddings_endpoint_without_exposing_key(self):
        config = CourseRoutingConfig.from_env(
            {
                "GRAPHRAG_API_BASE": "http://one-api.local/v1",
                "GRAPHRAG_EMBEDDING_API_KEY": "sk-secret",
                "GRAPHRAG_EMBEDDING_MODEL": "text-embedding-v4",
            },
            root_dir=_PROJECT_ROOT,
        )
        captured: dict[str, object] = {}

        class _Response:
            def raise_for_status(self) -> None:
                return None

            def json(self) -> dict[str, object]:
                return {"data": [{"embedding": [0.25] * 1024}]}

        def fake_post(url, *, headers, json, timeout):  # noqa: A002
            captured.update({"url": url, "headers": headers, "json": json, "timeout": timeout})
            return _Response()

        client = OpenAICompatibleEmbeddingClient(config, post=fake_post)
        vector = asyncio.run(client.embed("操作系统课程画像"))

        self.assertEqual(len(vector), 1024)
        self.assertEqual(captured["url"], "http://one-api.local/v1/embeddings")
        self.assertEqual(captured["headers"], {"Authorization": "Bearer sk-secret"})
        self.assertEqual(captured["json"], {
            "model": "text-embedding-v4",
            "input": "操作系统课程画像",
            "dimensions": 1024,
            "encoding_format": "float",
            "output_type": "dense",
        })

    def test_upsert_and_recommend_rank_profiles_with_course_filter(self):
        vectors = {
            "操作系统：进程 调度 死锁 内存": [1.0, 0.0, 0.0],
            "数据库：事务 索引 SQL": [0.0, 1.0, 0.0],
            "死锁和进程调度怎么理解？": [0.9, 0.1, 0.0],
        }
        store = InMemoryCourseProfileStore(expected_model="text-embedding-v4", expected_table="course_profiles_text_embedding_v4")
        service = CourseRoutingService(
            config=CourseRoutingConfig(
                api_base="http://127.0.0.1:3301/v1",
                embedding_model="text-embedding-v4",
                embedding_api_key="",
                embedding_dimension=3,
                embedding_output_type="dense",
                lancedb_uri=":memory:",
                table_name="course_profiles_text_embedding_v4",
            ),
            embedding_client=_FakeEmbeddingClient(vectors),
            store=store,
        )

        upsert_result = asyncio.run(
            service.upsert_profiles(
                [
                    CourseProfileInput(
                        courseId="course-os",
                        courseName="操作系统",
                        profileText="操作系统：进程 调度 死锁 内存",
                        profileHash="hash-os",
                        metadata={"teacher": "张老师"},
                    ),
                    CourseProfileInput(
                        courseId="course-db",
                        courseName="数据库",
                        profileText="数据库：事务 索引 SQL",
                        profileHash="hash-db",
                        metadata={},
                    ),
                ]
            )
        )

        self.assertEqual([item.courseId for item in upsert_result.items], ["course-os", "course-db"])
        self.assertEqual(upsert_result.items[0].vectorId, "course-os:text_embedding_v4:hash-os")

        recommend_result = asyncio.run(
            service.recommend(
                RecommendRequest(
                    question="死锁和进程调度怎么理解？",
                    courseIds=["course-os", "course-db"],
                    limit=1,
                )
            )
        )

        self.assertEqual(len(recommend_result.candidates), 1)
        self.assertEqual(recommend_result.candidates[0].courseId, "course-os")
        self.assertEqual(recommend_result.candidates[0].courseName, "操作系统")
        self.assertGreater(recommend_result.candidates[0].confidence, 0.99)
        self.assertEqual(recommend_result.candidates[0].profileHash, "hash-os")
        self.assertIn("课程画像相似度", recommend_result.candidates[0].reason)

    def test_recommend_deduplicates_multiple_vectors_for_same_course(self):
        service = CourseRoutingService(
            config=CourseRoutingConfig(
                api_base="http://127.0.0.1:3301/v1",
                embedding_model="text-embedding-v4",
                embedding_api_key="",
                embedding_dimension=3,
                embedding_output_type="dense",
                lancedb_uri=":memory:",
                table_name="course_profiles_text_embedding_v4",
            ),
            embedding_client=_FakeEmbeddingClient({"什么是进程": [1.0, 0.0, 0.0]}),
            store=_DuplicateSearchStore(),
        )

        recommend_result = asyncio.run(
            service.recommend(
                RecommendRequest(
                    question="什么是进程",
                    courseIds=["course-os", "course-db"],
                    limit=2,
                )
            )
        )

        self.assertEqual([candidate.courseId for candidate in recommend_result.candidates], ["course-os", "course-db"])
        self.assertEqual(recommend_result.candidates[0].profileHash, "hash-os-new")

    def test_prune_stale_profiles_deletes_only_rows_not_in_explicit_keep_set(self):
        store = InMemoryCourseProfileStore(
            expected_model="text-embedding-v4",
            expected_table="course_profiles_text_embedding_v4",
        )
        current = {
            "vector_id": "course-os:text_embedding_v4:hash-new",
            "course_id": "course-os",
            "course_name": "操作系统",
            "profile_text": "操作系统新画像",
            "profile_hash": "hash-new",
            "metadata": {},
            "embedding_model": "text-embedding-v4",
            "embedding_dimension": 3,
            "table_name": "course_profiles_text_embedding_v4",
            "vector": [1.0, 0.0, 0.0],
        }
        stale = {
            **current,
            "vector_id": "course-os:text_embedding_v4:hash-old",
            "profile_text": "操作系统旧画像",
            "profile_hash": "hash-old",
        }
        other_course = {
            **current,
            "vector_id": "course-db:text_embedding_v4:hash-db",
            "course_id": "course-db",
            "course_name": "数据库",
            "profile_text": "数据库画像",
            "profile_hash": "hash-db",
            "vector": [0.0, 1.0, 0.0],
        }
        store.upsert(current)
        store.upsert(stale)
        store.upsert(other_course)
        service = CourseRoutingService(
            config=CourseRoutingConfig(
                api_base="http://127.0.0.1:3301/v1",
                embedding_model="text-embedding-v4",
                embedding_api_key="",
                embedding_dimension=3,
                embedding_output_type="dense",
                lancedb_uri=":memory:",
                table_name="course_profiles_text_embedding_v4",
            ),
            embedding_client=_FakeEmbeddingClient({}),
            store=store,
        )

        dry_run = service.prune_stale_profiles(
            keep_vector_ids=["course-os:text_embedding_v4:hash-new"],
            course_ids=["course-os"],
            dry_run=True,
        )
        self.assertEqual(dry_run.staleVectorIds, ["course-os:text_embedding_v4:hash-old"])
        self.assertEqual(dry_run.deletedCount, 0)

        result = service.prune_stale_profiles(
            keep_vector_ids=["course-os:text_embedding_v4:hash-new"],
            course_ids=["course-os"],
            dry_run=False,
        )

        self.assertEqual(result.deletedCount, 1)
        remaining = store.list_profiles(course_ids=None)
        self.assertEqual(
            sorted(row["vector_id"] for row in remaining),
            ["course-db:text_embedding_v4:hash-db", "course-os:text_embedding_v4:hash-new"],
        )

    def test_prune_stale_profiles_requires_explicit_keep_vector_ids(self):
        service = CourseRoutingService(
            config=CourseRoutingConfig(
                api_base="http://127.0.0.1:3301/v1",
                embedding_model="text-embedding-v4",
                embedding_api_key="",
                embedding_dimension=3,
                embedding_output_type="dense",
                lancedb_uri=":memory:",
                table_name="course_profiles_text_embedding_v4",
            ),
            embedding_client=_FakeEmbeddingClient({}),
            store=InMemoryCourseProfileStore(
                expected_model="text-embedding-v4",
                expected_table="course_profiles_text_embedding_v4",
            ),
        )

        with self.assertRaisesRegex(ValueError, "keep_vector_ids"):
            service.prune_stale_profiles(keep_vector_ids=[], course_ids=["course-os"], dry_run=True)

    def test_store_rejects_mismatched_embedding_model_or_table(self):
        store = InMemoryCourseProfileStore(expected_model="text-embedding-v4", expected_table="course_profiles_text_embedding_v4")

        with self.assertRaises(ValueError):
            store.upsert(
                {
                    "vector_id": "course-1:baai_bge_m3:hash",
                    "course_id": "course-1",
                    "course_name": "测试课",
                    "profile_text": "测试",
                    "profile_hash": "hash",
                    "metadata": {},
                    "embedding_model": "BAAI/bge-m3",
                    "embedding_dimension": 1024,
                    "table_name": "course_profiles_baai_bge_m3",
                    "vector": [1.0, 0.0, 0.0],
                }
            )

    def test_profile_hints_rejects_data_dir_uri_that_resolves_outside_build_runs_root(self):
        service = CourseRoutingService(
            config=CourseRoutingConfig(
                api_base="http://127.0.0.1:3301/v1",
                embedding_model="text-embedding-v4",
                embedding_api_key="",
                embedding_dimension=3,
                embedding_output_type="dense",
                lancedb_uri=":memory:",
                table_name="course_profiles_text_embedding_v4",
            ),
            embedding_client=_FakeEmbeddingClient({}),
            store=InMemoryCourseProfileStore(
                expected_model="text-embedding-v4",
                expected_table="course_profiles_text_embedding_v4",
            ),
        )
        with tempfile.TemporaryDirectory() as tmp:
            build_runs_root = Path(tmp) / "kb-build-runs"
            outside = Path(tmp) / "outside"
            build_runs_root.mkdir()
            outside.mkdir()
            try:
                os.symlink(outside, build_runs_root / "escaped")
            except OSError as exc:
                self.skipTest(f"当前文件系统不支持符号链接: {exc}")

            with patch.dict(os.environ, {"GRAPHRAG_BUILD_RUNS_ROOT": str(build_runs_root)}):
                with self.assertRaisesRegex(ValueError, "安全相对路径"):
                    asyncio.run(
                        service.extract_profile_hints(
                            ProfileHintsRequest(
                                courseId="course-os",
                                dataDirUris=["escaped"],
                            )
                        )
                    )


if __name__ == "__main__":
    unittest.main()
