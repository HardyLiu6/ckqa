#!/usr/bin/env python3
# -*- coding: utf-8 -*-

from __future__ import annotations

import tempfile
import unittest
from pathlib import Path
from types import SimpleNamespace

from fastapi import HTTPException

_PROJECT_ROOT = Path(__file__).resolve().parent.parent
_MODULE_DIR = _PROJECT_ROOT / "utils"
import sys

if str(_MODULE_DIR) not in sys.path:
    sys.path.insert(0, str(_MODULE_DIR))

from query_engine_history_poc import (  # noqa: E402
    HistoryPocConfig,
    QueryEngineHistoryPocAdapter,
    _local_search_cli_overrides,
    normalize_conversation_history,
)


class _FakeSearchEngine:
    def __init__(self) -> None:
        self.calls: list[tuple[str, object | None]] = []

    def search(self, *, query: str, conversation_history=None):
        self.calls.append((query, conversation_history))
        return SimpleNamespace(
            response=f"answer for {query}",
            context_data={"entities": [1, 2]},
            completion_time=0.25,
        )


class _AsyncFakeSearchEngine(_FakeSearchEngine):
    async def search(self, *, query: str, conversation_history=None):
        return super().search(query=query, conversation_history=conversation_history)


class TestQueryEngineHistoryPoc(unittest.TestCase):
    def test_local_search_overrides_limit_history_context_and_topk(self):
        overrides = _local_search_cli_overrides(
            Path("/tmp/index-output"),
            HistoryPocConfig(
                max_turns=3,
                max_context_tokens=4200,
                top_k_entities=6,
                top_k_relationships=4,
            ),
        )

        self.assertEqual(overrides["local_search"]["conversation_history_max_turns"], 3)
        self.assertEqual(overrides["local_search"]["max_context_tokens"], 4200)
        self.assertEqual(overrides["local_search"]["top_k_entities"], 6)
        self.assertEqual(overrides["local_search"]["top_k_relationships"], 4)
        self.assertEqual(overrides["vector_store"]["db_uri"], "/tmp/index-output/lancedb")

    def test_normalize_history_keeps_recent_valid_turns_with_char_budget(self):
        turns = [
            {"role": "user", "content": "什么是死锁？"},
            {"role": "assistant", "content": "死锁是进程互相等待资源。"},
            {"role": "user", "content": "它和资源分配图有什么关系？"},
        ]

        normalized = normalize_conversation_history(turns, max_turns=1, max_chars=24)

        self.assertEqual(normalized, [{"role": "user", "content": "它和资源分配图有什么关系？"}])

    def test_normalize_history_rejects_illegal_role(self):
        with self.assertRaises(ValueError) as context:
            normalize_conversation_history([{"role": "system", "content": "secret"}])

        self.assertIn("非法 conversation role", str(context.exception))

    def test_readiness_rejects_path_escape(self):
        with tempfile.TemporaryDirectory() as temp_dir:
            adapter = QueryEngineHistoryPocAdapter(
                root_dir=_PROJECT_ROOT,
                output_dir=Path(temp_dir) / "output",
                build_runs_root=Path(temp_dir) / "runs",
                config=HistoryPocConfig(enabled=True),
            )

            with self.assertRaises(HTTPException) as context:
                adapter.readiness("../outside")

            self.assertEqual(context.exception.status_code, 400)

    def test_readiness_reports_missing_artifacts_without_raising(self):
        with tempfile.TemporaryDirectory() as temp_dir:
            output_dir = Path(temp_dir) / "output"
            output_dir.mkdir()
            adapter = QueryEngineHistoryPocAdapter(
                root_dir=_PROJECT_ROOT,
                output_dir=output_dir,
                build_runs_root=Path(temp_dir) / "runs",
                config=HistoryPocConfig(enabled=True),
                import_probe=lambda: True,
            )

            readiness = adapter.readiness(None)

            self.assertTrue(readiness.enabled)
            self.assertFalse(readiness.supported)
            self.assertIn("text_units.parquet", readiness.missing)
            self.assertIn("lancedb/entity_description.lance", readiness.missing)

    def test_query_uses_conversation_history_object_without_appending_to_query(self):
        search_engine = _FakeSearchEngine()
        seen_history: list[list[dict[str, str]]] = []

        def build_search_engine(_data_dir: Path, *, return_candidate_context: bool):
            return search_engine

        def make_history(turns):
            seen_history.append(turns)
            return {"history": turns}

        with tempfile.TemporaryDirectory() as temp_dir:
            output_dir = Path(temp_dir) / "output"
            output_dir.mkdir()
            adapter = QueryEngineHistoryPocAdapter(
                root_dir=_PROJECT_ROOT,
                output_dir=output_dir,
                build_runs_root=Path(temp_dir) / "runs",
                config=HistoryPocConfig(enabled=True),
                import_probe=lambda: True,
                search_engine_builder=build_search_engine,
                conversation_history_factory=make_history,
            )

            result = adapter.query(
                data_dir_uri=None,
                query="它和资源分配图有什么关系？",
                conversation_history=[
                    {"role": "user", "content": "什么是死锁？"},
                    {"role": "assistant", "content": "死锁是进程互相等待资源。"},
                ],
                max_turns=5,
                user_turns_only=True,
                return_candidate_context=True,
            )

        self.assertEqual(search_engine.calls[0][0], "它和资源分配图有什么关系？")
        self.assertEqual(search_engine.calls[0][1], {"history": seen_history[0]})
        self.assertNotIn("什么是死锁", search_engine.calls[0][0])
        self.assertTrue(result.history_applied)
        self.assertEqual(result.history_turns_used, 2)
        self.assertEqual(result.answer, "answer for 它和资源分配图有什么关系？")
        self.assertEqual(result.candidate_context_summary["entities"]["count"], 2)

    def test_query_cleans_data_citations_and_returns_sources(self):
        search_engine = _FakeSearchEngine()
        search_engine.search = lambda **_kwargs: SimpleNamespace(
            response="资源分配图可用于描述死锁状态 [Data: Sources (156)]。",
            context_data=None,
        )

        with tempfile.TemporaryDirectory() as temp_dir:
            output_dir = Path(temp_dir) / "output"
            output_dir.mkdir()
            adapter = QueryEngineHistoryPocAdapter(
                root_dir=_PROJECT_ROOT,
                output_dir=output_dir,
                build_runs_root=Path(temp_dir) / "runs",
                config=HistoryPocConfig(enabled=True),
                import_probe=lambda: True,
                search_engine_builder=lambda _data_dir, **_kwargs: search_engine,
            )

            result = adapter.query(
                data_dir_uri=None,
                query="它和资源分配图有什么关系？",
                conversation_history=[{"role": "user", "content": "什么是死锁？"}],
            )

        self.assertEqual(result.raw_answer, "资源分配图可用于描述死锁状态 [Data: Sources (156)]。")
        self.assertNotIn("[Data:", result.answer or "")
        self.assertIn("[已参考课程知识库]", result.answer or "")
        self.assertEqual(result.sources, [])

    def test_query_supports_async_local_search_result(self):
        search_engine = _AsyncFakeSearchEngine()

        with tempfile.TemporaryDirectory() as temp_dir:
            output_dir = Path(temp_dir) / "output"
            output_dir.mkdir()
            adapter = QueryEngineHistoryPocAdapter(
                root_dir=_PROJECT_ROOT,
                output_dir=output_dir,
                build_runs_root=Path(temp_dir) / "runs",
                config=HistoryPocConfig(enabled=True),
                import_probe=lambda: True,
                search_engine_builder=lambda _data_dir, **_kwargs: search_engine,
                conversation_history_factory=lambda turns: {"history": turns},
            )

            result = adapter.query(
                data_dir_uri=None,
                query="它和资源分配图有什么关系？",
                conversation_history=[{"role": "user", "content": "什么是死锁？"}],
            )

        self.assertTrue(result.supported)
        self.assertEqual(result.answer, "answer for 它和资源分配图有什么关系？")
        self.assertEqual(search_engine.calls[0][0], "它和资源分配图有什么关系？")


if __name__ == "__main__":
    unittest.main()
