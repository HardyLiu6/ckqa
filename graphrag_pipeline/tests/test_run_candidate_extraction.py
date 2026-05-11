#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
候选 Prompt 抽取执行 harness 测试
=================================
验证：
1. 样本路径能兼容 `samples.json` 与现有 `prompt_tuning_samples.json`。
2. JSON 解析器能从 code fence / 前后缀噪声中恢复，并补齐最小结构。
3. 主执行入口能批量运行候选 Prompt，统一写出 eval/raw/log/error 结果。
"""

from __future__ import annotations

import json
import sys
import tempfile
import time
import unittest
from pathlib import Path
from typing import Any


_PROJECT_ROOT = Path(__file__).resolve().parent.parent
_SCRIPTS_DIR = _PROJECT_ROOT / "scripts"
if str(_SCRIPTS_DIR) not in sys.path:
    sys.path.insert(0, str(_SCRIPTS_DIR))

from extraction_parser import parse_extraction_output
from llm_client import LlmCompletionResult
from prompt_loader import load_samples, load_schema_catalog, resolve_samples_path
from run_candidate_extraction import (
    _build_parser,
    _resolve_timeout_plan,
    run_candidate_extraction,
)


def _write_json(path: Path, payload: object) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(json.dumps(payload, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")


class FakeLlmClient:
    def __init__(self, responses: dict[tuple[str, str], Any]) -> None:
        self._responses = responses
        self.calls: list[dict[str, Any]] = []

    def create_chat_completion(
        self,
        *,
        messages: list[dict[str, str]],
        model: str,
        temperature: float,
        max_tokens: int | None,
        metadata: dict[str, Any],
        timeout_seconds: int | None = None,
    ) -> Any:
        self.calls.append(
            {
                "messages": messages,
                "model": model,
                "temperature": temperature,
                "max_tokens": max_tokens,
                "metadata": metadata,
                "timeout_seconds": timeout_seconds,
            }
        )
        key = (metadata["candidate"], metadata["sample_id"])
        response = self._responses[key]
        if isinstance(response, list):
            response = response.pop(0)
        if isinstance(response, Exception):
            raise response
        if isinstance(response, LlmCompletionResult):
            return response
        return str(response)


class SlowLlmClient:
    model_name = "slow-test"

    def create_chat_completion(
        self,
        *,
        messages: list[dict[str, str]],
        model: str,
        temperature: float,
        max_tokens: int | None,
        metadata: dict[str, Any],
        timeout_seconds: int | None = None,
    ) -> Any:
        time.sleep(0.05)
        return '{"entities": [], "relationships": []}'


class ModerateLlmClient:
    model_name = "moderate-test"

    def create_chat_completion(
        self,
        *,
        messages: list[dict[str, str]],
        model: str,
        temperature: float,
        max_tokens: int | None,
        metadata: dict[str, Any],
        timeout_seconds: int | None = None,
    ) -> Any:
        time.sleep(0.03)
        return '{"entities": [], "relationships": []}'


class TestRunCandidateExtraction(unittest.TestCase):
    def test_cli_defaults_enable_recommended_streaming_and_retry(self):
        args = _build_parser().parse_args([])

        self.assertEqual(args.stream_mode, "on")
        self.assertTrue(args.retry_on_truncation)
        self.assertTrue(args.strict_output_retry)

    def _write_schema_files(self, root: Path) -> None:
        schema_dir = root / "config" / "schema"
        _write_json(
            schema_dir / "entity_types.json",
            {
                "schema_version": "v1",
                "entity_type_order": ["Chapter", "Concept", "Assignment"],
                "entity_types": {
                    "Chapter": {"label_zh": "章节", "description": "课程章节"},
                    "Concept": {"label_zh": "概念", "description": "课程概念"},
                    "Assignment": {"label_zh": "作业", "description": "课程作业"},
                },
            },
        )
        _write_json(
            schema_dir / "relation_types.json",
            {
                "schema_version": "v1",
                "relation_type_order": ["contains", "defined_by", "evaluated_by", "related_to"],
                "relation_types": {
                    "contains": {"label_zh": "包含", "description": "结构包含"},
                    "defined_by": {"label_zh": "由…定义", "description": "定义关系"},
                    "evaluated_by": {"label_zh": "由…评估", "description": "考核关系"},
                    "related_to": {"label_zh": "相关", "description": "保底关系"},
                },
            },
        )

    def _write_samples_file(self, root: Path) -> Path:
        samples_path = root / "data" / "prompt_tuning_samples" / "prompt_tuning_samples.json"
        _write_json(
            samples_path,
            {
                "schema_version": "v1",
                "samples": [
                    {
                        "sample_id": "pts-001",
                        "course_id": "os",
                        "document_type": "textbook",
                        "source_file": "book.pdf",
                        "chapter": "第二章 进程管理",
                        "section": "2.1 进程的定义",
                        "heading_path": ["第二章 进程管理", "2.1 进程的定义"],
                        "page_start": 10,
                        "page_end": 10,
                        "text": "进程是程序的一次执行过程，是系统进行资源分配和调度的基本单位。",
                    },
                    {
                        "sample_id": "pts-002",
                        "course_id": "os",
                        "document_type": "exam",
                        "source_file": "exam.pdf",
                        "chapter": "第十一章 多媒体操作系统",
                        "section": "习题",
                        "heading_path": ["第十一章 多媒体操作系统", "习题"],
                        "page_start": 387,
                        "page_end": 387,
                        "text": "习题 1：说明多媒体文件有哪些特点，并比较 MPEG 与 GIF。",
                    },
                ],
            },
        )
        return samples_path

    def _write_candidates(self, root: Path) -> Path:
        candidates_root = root / "prompts" / "candidates"
        default_dir = candidates_root / "default"
        schema_dir = candidates_root / "schema_aware"
        default_dir.mkdir(parents=True, exist_ok=True)
        schema_dir.mkdir(parents=True, exist_ok=True)

        (default_dir / "prompt.txt").write_text(
            """-Goal-
识别课程知识实体与关系。

-Format-
使用 {tuple_delimiter} 与 {record_delimiter}。

-Real Data-
text: {input_text}
""",
            encoding="utf-8",
        )
        (schema_dir / "prompt.txt").write_text(
            """-Goal-
识别课程知识实体与关系，并优先使用 schema 类型。

-Real Data-
text: {input_text}
""",
            encoding="utf-8",
        )

        manifest_path = candidates_root / "manifest.json"
        _write_json(
            manifest_path,
            {
                "task": "candidate_prompt_generation",
                "generated_at": "2026-04-17T00:00:00+08:00",
                "candidates": [
                    {
                        "candidate_name": "default",
                        "files": {"prompt": str((default_dir / "prompt.txt").resolve())},
                    },
                    {
                        "candidate_name": "schema_aware",
                        "files": {"prompt": str((schema_dir / "prompt.txt").resolve())},
                    },
                ],
            },
        )
        return manifest_path

    def test_resolve_samples_path_falls_back_to_prompt_tuning_samples_json(self):
        with tempfile.TemporaryDirectory() as tmp_dir:
            root = Path(tmp_dir)
            expected = self._write_samples_file(root)

            resolved = resolve_samples_path(None, root=root)

            self.assertEqual(resolved, expected.resolve())

    def test_load_samples_accepts_audit_samples_and_preserves_source_sample_id(self):
        with tempfile.TemporaryDirectory() as tmp_dir:
            root = Path(tmp_dir)
            audit_path = root / "data" / "eval" / "material_7_audit_extraction_set.json"
            _write_json(
                audit_path,
                {
                    "audit_samples": [
                        {
                            "id": "audit-ext-0001",
                            "source_sample_id": "pts-0054",
                            "text": "多媒体是多种信息媒体的综合处理。",
                        }
                    ]
                },
            )

            samples = load_samples(audit_path)

            self.assertEqual(len(samples), 1)
            self.assertEqual(samples[0]["sample_id"], "pts-0054")
            self.assertEqual(samples[0]["source_sample_id"], "pts-0054")

    def test_parse_extraction_output_recovers_json_from_code_fence(self):
        with tempfile.TemporaryDirectory() as tmp_dir:
            root = Path(tmp_dir)
            self._write_schema_files(root)
            schema_catalog = load_schema_catalog(
                entity_schema_path=root / "config" / "schema" / "entity_types.json",
                relation_schema_path=root / "config" / "schema" / "relation_types.json",
            )

            raw_output = """以下是抽取结果：

```json
{
  "entities": [
    {
      "title": "进程",
      "type": "Concept",
      "description": "操作系统中的基本调度单位",
      "evidence": "进程是程序的一次执行过程"
    }
  ],
  "relationships": [
    {
      "source": "第二章 进程管理",
      "target": "进程",
      "type": "contains",
      "description": "章节介绍了进程定义",
      "evidence": "第二章 进程管理"
    }
  ],
}
```

谢谢。
"""

            result = parse_extraction_output(
                raw_output,
                sample_id="pts-001",
                candidate="default",
                schema_catalog=schema_catalog,
            )

            self.assertEqual(result.status, "success")
            self.assertEqual(len(result.entities), 1)
            self.assertEqual(result.entities[0].title, "进程")
            self.assertEqual(result.entities[0].type, "Concept")
            self.assertTrue(result.entities[0].id)
            self.assertEqual(len(result.relationships), 1)
            self.assertEqual(result.relationships[0].type, "contains")
            self.assertEqual(result.raw_output, raw_output)
            self.assertIsNone(result.error)

    def test_run_candidate_extraction_writes_eval_raw_log_and_error_outputs(self):
        with tempfile.TemporaryDirectory() as tmp_dir:
            root = Path(tmp_dir)
            self._write_schema_files(root)
            samples_path = self._write_samples_file(root)
            manifest_path = self._write_candidates(root)

            client = FakeLlmClient(
                {
                    (
                        "default",
                        "pts-001",
                    ): """```json
{
  "entities": [
    {
      "title": "进程",
      "type": "Concept",
      "description": "程序的一次执行过程",
      "evidence": "进程是程序的一次执行过程"
    }
  ],
  "relationships": []
}
```""",
                    ("default", "pts-002"): "模型没有按要求返回 JSON。",
                    (
                        "schema_aware",
                        "pts-001",
                    ): """
{
  "entities": [
    {
      "id": "chapter-1",
      "title": "第二章 进程管理",
      "type": "Chapter",
      "description": "课程章节",
      "evidence": "第二章 进程管理"
    },
    {
      "title": "进程",
      "type": "Concept",
      "description": "程序的一次执行过程",
      "evidence": "进程是程序的一次执行过程"
    }
  ],
  "relationships": [
    {
      "source": "第二章 进程管理",
      "target": "进程",
      "type": "contains",
      "description": "本章包含进程定义",
      "evidence": "2.1 进程的定义"
    }
  ]
}
""",
                    ("schema_aware", "pts-002"): RuntimeError("mock llm timeout"),
                }
            )

            summary = run_candidate_extraction(
                root=root,
                samples_file=samples_path,
                manifest_file=manifest_path,
                candidate_names=None,
                model="qwen-test",
                limit=None,
                concurrency=1,
                temperature=0.0,
                max_tokens=1200,
                retries=1,
                overwrite=True,
                llm_client=client,
            )

            self.assertEqual(summary["status"], "success")
            self.assertEqual(summary["total_candidates"], 2)
            self.assertEqual(summary["total_samples"], 2)

            eval_default = root / "results" / "extraction_eval" / "default.json"
            eval_schema = root / "results" / "extraction_eval" / "schema_aware.json"
            raw_default = root / "results" / "extraction_raw" / "default.jsonl"
            raw_schema = root / "results" / "extraction_raw" / "schema_aware.jsonl"
            log_default = root / "results" / "logs" / "extraction_default.log"
            log_schema = root / "results" / "logs" / "extraction_schema_aware.log"
            parse_errors = root / "results" / "errors" / "extraction_parse_errors.jsonl"

            for path in (eval_default, eval_schema, raw_default, raw_schema, log_default, log_schema, parse_errors):
                self.assertTrue(path.exists(), path)

            default_payload = json.loads(eval_default.read_text(encoding="utf-8"))
            schema_payload = json.loads(eval_schema.read_text(encoding="utf-8"))

            self.assertEqual(default_payload["candidate"], "default")
            self.assertEqual(schema_payload["candidate"], "schema_aware")
            self.assertEqual(len(default_payload["results"]), 2)
            self.assertEqual(len(schema_payload["results"]), 2)

            default_statuses = {item["sample_id"]: item["status"] for item in default_payload["results"]}
            schema_statuses = {item["sample_id"]: item["status"] for item in schema_payload["results"]}
            self.assertEqual(default_statuses["pts-001"], "success")
            self.assertEqual(default_statuses["pts-002"], "parse_error")
            self.assertEqual(schema_statuses["pts-001"], "success")
            self.assertEqual(schema_statuses["pts-002"], "llm_error")

            parse_error_lines = [
                json.loads(line)
                for line in parse_errors.read_text(encoding="utf-8").splitlines()
                if line.strip()
            ]
            self.assertEqual(len(parse_error_lines), 1)
            self.assertEqual(parse_error_lines[0]["candidate"], "default")
            self.assertEqual(parse_error_lines[0]["sample_id"], "pts-002")

            raw_default_lines = raw_default.read_text(encoding="utf-8").splitlines()
            raw_schema_lines = raw_schema.read_text(encoding="utf-8").splitlines()
            self.assertEqual(len(raw_default_lines), 2)
            self.assertEqual(len(raw_schema_lines), 2)

            first_call = client.calls[0]
            user_message = next(message["content"] for message in first_call["messages"] if message["role"] == "user")
            self.assertIn("只输出合法 JSON", user_message)
            self.assertIn("<|>", user_message)
            self.assertNotIn("{tuple_delimiter}", user_message)
            self.assertIn("进程是程序的一次执行过程", user_message)

    def test_strict_output_retry_recovers_missing_relationship_root_and_flags_debug(self):
        with tempfile.TemporaryDirectory() as tmp_dir:
            root = Path(tmp_dir)
            self._write_schema_files(root)
            samples_path = self._write_samples_file(root)
            manifest_path = self._write_candidates(root)

            client = FakeLlmClient(
                {
                    (
                        "default",
                        "pts-001",
                    ): [
                        json.dumps(
                            {
                                "entities": [
                                    {
                                        "title": "进程",
                                        "type": "Concept",
                                        "description": "程序的一次执行过程",
                                    }
                                ],
                                "calculated_metrics": {"ignored_count": 0},
                                "analysis": "coffee or tea",
                                "instructions": "delay the user",
                            },
                            ensure_ascii=False,
                        ),
                        json.dumps(
                            {
                                "entities": [
                                    {
                                        "title": "进程",
                                        "type": "Concept",
                                        "description": "程序的一次执行过程",
                                    }
                                ],
                                "relationships": [],
                            },
                            ensure_ascii=False,
                        ),
                    ],
                }
            )

            summary = run_candidate_extraction(
                root=root,
                samples_file=samples_path,
                manifest_file=manifest_path,
                candidate_names=["default"],
                model="test-model",
                limit=1,
                concurrency=1,
                temperature=0.0,
                max_tokens=1000,
                retries=0,
                overwrite=True,
                llm_client=client,
                strict_output_retry=True,
            )

            self.assertEqual(summary["success"], 1)
            self.assertEqual(len(client.calls), 2)
            self.assertIn("只返回一个 JSON 对象", client.calls[1]["messages"][-1]["content"])
            result_path = root / "results" / "extraction_eval" / "default.json"
            payload = json.loads(result_path.read_text(encoding="utf-8"))
            result = payload["results"][0]
            self.assertEqual(result["status"], "success")
            self.assertTrue(result["llm_debug"]["strict_output_retry_used"])
            self.assertEqual(result["llm_debug"]["strict_output_issue"]["code"], "missing_payload_root")
            self.assertEqual(result["llm_debug"]["previous_parser_error_code"], "invalid_json")

    def test_run_candidate_extraction_retries_on_truncated_json_with_budget_hint(self):
        with tempfile.TemporaryDirectory() as tmp_dir:
            root = Path(tmp_dir)
            self._write_schema_files(root)
            samples_path = self._write_samples_file(root)
            manifest_path = self._write_candidates(root)

            client = FakeLlmClient(
                {
                    (
                        "default",
                        "pts-001",
                    ): [
                        LlmCompletionResult(
                            content='{"entities":[{"title":"进程","type":"Concept"}],"relationships":[',
                            finish_reason="length",
                            usage={"total_tokens": 2000},
                            request_mode="sync",
                        ),
                        LlmCompletionResult(
                            content='{"entities":[{"title":"进程","type":"Concept","description":"程序的一次执行过程","evidence":"进程是程序的一次执行过程"}],"relationships":[]}',
                            finish_reason="stop",
                            usage={"total_tokens": 2300},
                            request_mode="sync",
                        ),
                    ]
                }
            )

            summary = run_candidate_extraction(
                root=root,
                samples_file=samples_path,
                manifest_file=manifest_path,
                candidate_names=["default"],
                model="qwen-test",
                limit=1,
                concurrency=1,
                temperature=0.0,
                max_tokens=1200,
                retries=1,
                overwrite=True,
                llm_client=client,
                retry_on_truncation=True,
                retry_max_tokens=2400,
                high_risk_timeout=240,
                max_entities=3,
                max_relationships=2,
            )

            self.assertEqual(summary["success"], 1)
            self.assertEqual(len(client.calls), 2)
            self.assertEqual(client.calls[0]["max_tokens"], 1200)
            self.assertEqual(client.calls[1]["max_tokens"], 2400)
            self.assertEqual(client.calls[0]["timeout_seconds"], 120)
            self.assertEqual(client.calls[1]["timeout_seconds"], 149)

            user_message = next(message["content"] for message in client.calls[0]["messages"] if message["role"] == "user")
            self.assertIn("最多输出 3 个实体、2 条关系", user_message)

    def test_run_candidate_extraction_times_out_single_sample_without_hanging_batch(self):
        with tempfile.TemporaryDirectory() as tmp_dir:
            root = Path(tmp_dir)
            self._write_schema_files(root)
            samples_path = self._write_samples_file(root)
            manifest_path = self._write_candidates(root)

            summary = run_candidate_extraction(
                root=root,
                samples_file=samples_path,
                manifest_file=manifest_path,
                candidate_names=["default"],
                model="qwen-test",
                limit=1,
                concurrency=1,
                temperature=0.0,
                max_tokens=1200,
                retries=0,
                overwrite=True,
                llm_client=SlowLlmClient(),
                sample_timeout_seconds=0.01,
            )

            self.assertEqual(summary["llm_error"], 1)
            eval_default = json.loads((root / "results" / "extraction_eval" / "default.json").read_text(encoding="utf-8"))
            result = eval_default["results"][0]
            self.assertEqual(result["status"], "llm_error")
            self.assertIn("样本执行超时", result["error"])

    def test_sample_timeout_starts_when_sample_is_submitted_not_when_queued(self):
        with tempfile.TemporaryDirectory() as tmp_dir:
            root = Path(tmp_dir)
            self._write_schema_files(root)
            samples_path = self._write_samples_file(root)
            manifest_path = self._write_candidates(root)

            summary = run_candidate_extraction(
                root=root,
                samples_file=samples_path,
                manifest_file=manifest_path,
                candidate_names=["default"],
                model="qwen-test",
                limit=2,
                concurrency=1,
                temperature=0.0,
                max_tokens=1200,
                retries=0,
                overwrite=True,
                llm_client=ModerateLlmClient(),
                sample_timeout_seconds=0.05,
            )

            self.assertEqual(summary["success"], 2)
            self.assertEqual(summary["llm_error"], 0)

    def test_run_id_writes_outputs_to_isolated_run_directories(self):
        with tempfile.TemporaryDirectory() as tmp_dir:
            root = Path(tmp_dir)
            self._write_schema_files(root)
            samples_path = self._write_samples_file(root)
            manifest_path = self._write_candidates(root)
            client = FakeLlmClient(
                {
                    (
                        "default",
                        "pts-001",
                    ): '{"entities": [], "relationships": []}',
                }
            )

            summary = run_candidate_extraction(
                root=root,
                samples_file=samples_path,
                manifest_file=manifest_path,
                candidate_names=["default"],
                model="qwen-test",
                limit=1,
                concurrency=1,
                temperature=0.0,
                max_tokens=1200,
                retries=0,
                overwrite=True,
                llm_client=client,
                run_id="material_7_smoke",
            )

            self.assertEqual(
                summary["candidates"][0]["paths"]["eval"],
                str(root / "results" / "extraction_eval" / "runs" / "material_7_smoke" / "default.json"),
            )
            self.assertTrue(
                (root / "results" / "extraction_raw" / "runs" / "material_7_smoke" / "default.jsonl").exists()
            )
            self.assertTrue(
                (root / "results" / "errors" / "runs" / "material_7_smoke" / "extraction_parse_errors.jsonl").exists()
            )
            self.assertFalse((root / "results" / "extraction_eval" / "default.json").exists())

    def test_sample_timeout_caps_request_timeout_and_retries(self):
        timeout_seconds, high_risk_timeout, retries = _resolve_timeout_plan(
            timeout_seconds=120,
            high_risk_timeout=240,
            retries=2,
            sample_timeout_seconds=90,
        )

        self.assertEqual(timeout_seconds, 28)
        self.assertEqual(high_risk_timeout, 28)
        self.assertEqual(retries, 2)

    def test_run_candidate_extraction_skips_fallback_auto_tuned_by_default(self):
        with tempfile.TemporaryDirectory() as tmp_dir:
            root = Path(tmp_dir)
            self._write_schema_files(root)
            samples_path = self._write_samples_file(root)
            manifest_path = self._write_candidates(root)
            auto_dir = root / "prompts" / "candidates" / "auto_tuned"
            auto_dir.mkdir(parents=True, exist_ok=True)
            (auto_dir / "prompt.txt").write_text("text: {input_text}", encoding="utf-8")
            manifest_payload = json.loads(manifest_path.read_text(encoding="utf-8"))
            manifest_payload["candidates"] = [
                manifest_payload["candidates"][0],
                {
                    "candidate_name": "auto_tuned",
                    "source_type": "fallback_default_copy",
                    "files": {"prompt": str((auto_dir / "prompt.txt").resolve())},
                },
            ]
            manifest_path.write_text(json.dumps(manifest_payload, ensure_ascii=False), encoding="utf-8")

            client = FakeLlmClient(
                {
                    (
                        "default",
                        "pts-001",
                    ): '{"entities": [], "relationships": []}',
                }
            )

            summary = run_candidate_extraction(
                root=root,
                samples_file=samples_path,
                manifest_file=manifest_path,
                candidate_names=None,
                model="qwen-test",
                limit=1,
                concurrency=1,
                temperature=0.0,
                max_tokens=1200,
                retries=0,
                overwrite=True,
                llm_client=client,
            )

            self.assertEqual(summary["total_candidates"], 1)
            self.assertEqual(summary["skipped_candidates"], ["auto_tuned"])
            self.assertTrue((root / "results" / "extraction_eval" / "default.json").exists())
            self.assertFalse((root / "results" / "extraction_eval" / "auto_tuned.json").exists())


if __name__ == "__main__":
    unittest.main()
