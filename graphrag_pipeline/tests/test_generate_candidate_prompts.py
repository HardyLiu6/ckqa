#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
候选 Prompt 生成脚本测试
========================
验证候选 Prompt 生成模块能够：
1. 统一生成四类候选 Prompt 目录与 manifest。
2. 在缺少 auto-tuned 输出时稳定降级，不中断执行。
3. 在缺少默认 Prompt 或 audit gold 时退回最小可运行版本。
"""

from __future__ import annotations

import json
import sys
import tempfile
import unittest
from pathlib import Path


_PROJECT_ROOT = Path(__file__).resolve().parent.parent
_SCRIPT_DIR = _PROJECT_ROOT / "scripts"
if str(_SCRIPT_DIR) not in sys.path:
    sys.path.insert(0, str(_SCRIPT_DIR))

from generate_candidate_prompts import generate_candidate_prompts


def _write_json(path: Path, payload: object) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(json.dumps(payload, ensure_ascii=False, indent=2), encoding="utf-8")


class TestGenerateCandidatePrompts(unittest.TestCase):
    def _write_schema_files(self, schema_dir: Path) -> None:
        entity_payload = {
            "schema_version": "v1",
            "domain": "course_qa",
            "scenario": "课程知识图谱抽取",
            "entity_type_order": ["Course", "Chapter", "Concept", "AlgorithmOrMethod", "Experiment", "Assignment"],
            "entity_types": {
                "Course": {
                    "label_zh": "课程",
                    "description": "课程顶层对象",
                    "canonical_name_rule": "优先使用课程正式名称",
                    "positive_signals": ["课程名称"],
                    "negative_signals": ["通知标题"],
                },
                "Chapter": {
                    "label_zh": "章节",
                    "description": "课程结构章节",
                    "canonical_name_rule": "保留章节编号",
                    "positive_signals": ["第x章"],
                    "negative_signals": ["目录页伪标题"],
                },
                "Concept": {
                    "label_zh": "概念",
                    "description": "课程中的理论概念",
                    "canonical_name_rule": "使用课程内稳定概念名",
                    "positive_signals": ["定义"],
                    "negative_signals": ["空泛短语"],
                },
                "AlgorithmOrMethod": {
                    "label_zh": "方法/算法",
                    "description": "稳定的方法、算法或机制",
                    "canonical_name_rule": "使用固定算法名称",
                    "positive_signals": ["算法", "方法"],
                    "negative_signals": ["临时动作描述"],
                },
                "Experiment": {
                    "label_zh": "实验",
                    "description": "稳定实验任务",
                    "canonical_name_rule": "保留实验标题",
                    "positive_signals": ["实验步骤"],
                    "negative_signals": ["实验通知"],
                },
                "Assignment": {
                    "label_zh": "作业",
                    "description": "稳定作业或题组",
                    "canonical_name_rule": "保留作业标题",
                    "positive_signals": ["作业要求"],
                    "negative_signals": ["截止提醒"],
                },
            },
        }
        relation_payload = {
            "schema_version": "v1",
            "domain": "course_qa",
            "scenario": "课程知识图谱抽取",
            "relation_type_order": ["contains", "defined_by", "implemented_by", "evaluated_by", "related_to"],
            "relation_types": {
                "contains": {
                    "label_zh": "包含",
                    "description": "表示结构或容器关系",
                    "extraction_hint": "用于课程包含章节、章节包含概念",
                    "source_types": ["Course", "Chapter"],
                    "target_types": ["Chapter", "Concept", "Experiment", "Assignment"],
                },
                "defined_by": {
                    "label_zh": "由…定义",
                    "description": "表示概念由定义或公式界定",
                    "extraction_hint": "用于定义关系",
                    "source_types": ["Concept"],
                    "target_types": ["Concept"],
                },
                "implemented_by": {
                    "label_zh": "由…实现",
                    "description": "表示对象由某方法实现",
                    "extraction_hint": "用于方法实现关系",
                    "source_types": ["Concept", "Experiment"],
                    "target_types": ["AlgorithmOrMethod"],
                },
                "evaluated_by": {
                    "label_zh": "由…评估",
                    "description": "表示知识点由作业或实验考核",
                    "extraction_hint": "用于作业/实验考核关系",
                    "source_types": ["Concept"],
                    "target_types": ["Assignment", "Experiment"],
                },
                "related_to": {
                    "label_zh": "相关",
                    "description": "弱语义保底关系",
                    "extraction_hint": "无法确定更具体关系时使用",
                    "source_types": ["Concept", "AlgorithmOrMethod"],
                    "target_types": ["Concept", "AlgorithmOrMethod"],
                },
            },
        }
        rules_text = """# 课程领域实体与关系抽取规则

## 核心要求
1. 只抽服务于课程问答的稳定实体与关系。
2. 优先使用最具体的关系类型，不要因为共现就建立关系。
3. 课程通知、图表残片、空泛短语默认不抽。
4. 同一课程内注意去重，不做跨课程自动合并。
"""

        _write_json(schema_dir / "entity_types.json", entity_payload)
        _write_json(schema_dir / "relation_types.json", relation_payload)
        (schema_dir / "extraction_rules.md").write_text(rules_text, encoding="utf-8")

    def _write_default_prompt(self, default_prompt_dir: Path) -> None:
        default_prompt_dir.mkdir(parents=True, exist_ok=True)
        (default_prompt_dir / "extract_graph.txt").write_text(
            """-Goal-
Given a text document, identify entities and relationships.

-Steps-
1. Identify all entities.
2. Identify all relationships.
3. Return output as tuples.

-Real Data-
entity_types: [legacy_entity]
text: {input_text}
output:
""",
            encoding="utf-8",
        )

    def _write_existing_manifest(self, manifest_path: Path) -> None:
        payload = {
            "task": "candidate_prompt_generation",
            "generated_at": "2026-04-16T16:00:00+08:00",
            "candidates": [
                {
                    "candidate_name": "auto_tuned",
                    "source_type": "graphrag_prompt_tune",
                    "generation_method": "graphrag_official_prompt_tune",
                    "graphrag_invocation": "python -m graphrag prompt-tune",
                    "notes": ["已有官方调优信息"],
                    "files": {"prompt": "/tmp/auto/prompt.txt"},
                }
            ],
        }
        _write_json(manifest_path, payload)

    def _build_samples_payload(self) -> dict:
        return {
            "schema_version": "v1",
            "generated_at": "2026-04-16T12:00:00+08:00",
            "samples": [
                {
                    "sample_id": "pts-001",
                    "source_doc_id": "doc-001",
                    "source_file": "book.pdf",
                    "document_type": "textbook",
                    "course_id": "os",
                    "chapter": "第二章 进程管理",
                    "section": "2.1 进程的定义",
                    "heading_path": ["第二章 进程管理", "2.1 进程的定义"],
                    "heading_level": 2,
                    "text": "进程是程序的一次执行过程，是系统进行资源分配和调度的基本单位。",
                    "text_length": 34,
                    "page_start": 10,
                    "page_end": 10,
                    "guessed_sample_type": "definition_or_formula",
                    "metadata_summary": {"has_equation": False},
                },
                {
                    "sample_id": "pts-002",
                    "source_doc_id": "doc-002",
                    "source_file": "lab.pdf",
                    "document_type": "lab",
                    "course_id": "os",
                    "chapter": "第三章 调度",
                    "section": "实验一 进程调度",
                    "heading_path": ["第三章 调度", "实验一 进程调度"],
                    "heading_level": 2,
                    "text": "实验步骤：实现时间片轮转调度算法，并比较不同时间片长度的影响。",
                    "text_length": 31,
                    "page_start": 21,
                    "page_end": 22,
                    "guessed_sample_type": "experiment_instruction",
                    "metadata_summary": {"has_equation": False},
                },
            ],
        }

    def _build_audit_payload(self, with_gold: bool) -> dict:
        return {
            "schema_version": "v1",
            "generated_at": "2026-04-16T12:30:00+08:00",
            "task": "audit_extraction_set",
            "audit_samples": [
                {
                    "id": "audit-001",
                    "source_sample_id": "pts-001",
                    "source_doc_id": "doc-001",
                    "source_file": "book.pdf",
                    "document_type": "textbook",
                    "course_id": "os",
                    "chapter": "第二章 进程管理",
                    "section": "2.1 进程的定义",
                    "heading_path": ["第二章 进程管理", "2.1 进程的定义"],
                    "heading_level": 2,
                    "text": "进程是程序的一次执行过程，是系统进行资源分配和调度的基本单位。",
                    "text_length": 34,
                    "page_start": 10,
                    "page_end": 10,
                    "guessed_sample_type": "definition_or_formula",
                    "audit_priority": "high",
                    "audit_reason": "定义样本",
                    "gold_entities": (
                        [
                            {
                                "entity_id": "ent-001",
                                "name": "第二章 进程管理",
                                "type": "Chapter",
                                "alias": [],
                                "span_text": "第二章 进程管理",
                                "normalized_name": "第二章 进程管理",
                                "notes": "",
                            },
                            {
                                "entity_id": "ent-002",
                                "name": "进程",
                                "type": "Concept",
                                "alias": [],
                                "span_text": "进程",
                                "normalized_name": "进程",
                                "notes": "",
                            },
                        ]
                        if with_gold
                        else []
                    ),
                    "gold_relations": (
                        [
                            {
                                "relation_id": "rel-001",
                                "source_entity_id": "ent-001",
                                "target_entity_id": "ent-002",
                                "type": "contains",
                                "evidence_text": "第二章 进程管理介绍进程概念",
                                "notes": "",
                            }
                        ]
                        if with_gold
                        else []
                    ),
                    "annotation_notes": "",
                    "reviewer_decision": "",
                    "reviewer_confidence": "",
                },
                {
                    "id": "audit-002",
                    "source_sample_id": "pts-002",
                    "source_doc_id": "doc-002",
                    "source_file": "lab.pdf",
                    "document_type": "lab",
                    "course_id": "os",
                    "chapter": "第三章 调度",
                    "section": "实验一 进程调度",
                    "heading_path": ["第三章 调度", "实验一 进程调度"],
                    "heading_level": 2,
                    "text": "实验步骤：实现时间片轮转调度算法，并比较不同时间片长度的影响。",
                    "text_length": 31,
                    "page_start": 21,
                    "page_end": 22,
                    "guessed_sample_type": "experiment_instruction",
                    "audit_priority": "high",
                    "audit_reason": "实验样本",
                    "gold_entities": (
                        [
                            {
                                "entity_id": "ent-101",
                                "name": "实验一 进程调度",
                                "type": "Experiment",
                                "alias": [],
                                "span_text": "实验一 进程调度",
                                "normalized_name": "实验一 进程调度",
                                "notes": "",
                            },
                            {
                                "entity_id": "ent-102",
                                "name": "时间片轮转调度算法",
                                "type": "AlgorithmOrMethod",
                                "alias": [],
                                "span_text": "时间片轮转调度算法",
                                "normalized_name": "时间片轮转调度算法",
                                "notes": "",
                            },
                        ]
                        if with_gold
                        else []
                    ),
                    "gold_relations": (
                        [
                            {
                                "relation_id": "rel-101",
                                "source_entity_id": "ent-101",
                                "target_entity_id": "ent-102",
                                "type": "implemented_by",
                                "evidence_text": "实验步骤要求实现时间片轮转调度算法",
                                "notes": "",
                            }
                        ]
                        if with_gold
                        else []
                    ),
                    "annotation_notes": "",
                    "reviewer_decision": "",
                    "reviewer_confidence": "",
                },
            ],
        }

    def test_generate_candidate_prompts_writes_four_candidates_and_manifest(self):
        with tempfile.TemporaryDirectory() as tmp_dir:
            root = Path(tmp_dir)
            schema_dir = root / "config" / "schema"
            default_prompt_dir = root / "prompts"
            output_dir = root / "prompts" / "candidates"
            samples_file = root / "data" / "prompt_tuning_samples" / "prompt_tuning_samples.json"
            audit_file = root / "data" / "eval" / "audit_extraction_set.json"
            report_file = root / "results" / "reports" / "prompt_generation_report.json"

            self._write_schema_files(schema_dir)
            self._write_default_prompt(default_prompt_dir)
            _write_json(samples_file, self._build_samples_payload())
            _write_json(audit_file, self._build_audit_payload(with_gold=True))

            result = generate_candidate_prompts(
                schema_dir=schema_dir,
                samples_file=samples_file,
                audit_file=audit_file,
                default_prompt_dir=default_prompt_dir,
                auto_tuned_prompt_dir=root / "missing-auto-tuned",
                output_dir=output_dir,
                fewshot_k=2,
                language="zh",
                overwrite=True,
                report_file=report_file,
            )

            manifest = result["manifest"]
            candidates = {item["candidate_name"]: item for item in manifest["candidates"]}

            self.assertEqual(manifest["task"], "candidate_prompt_generation")
            self.assertEqual(
                set(candidates.keys()),
                {"default", "auto_tuned", "schema_aware", "schema_fewshot"},
            )
            self.assertTrue((output_dir / "manifest.json").exists())
            self.assertTrue(report_file.exists())

            for name in candidates:
                self.assertTrue((output_dir / name / "prompt.txt").exists(), msg=f"{name} prompt 未生成")
                self.assertTrue((output_dir / name / "README.md").exists(), msg=f"{name} README 未生成")

            self.assertEqual(candidates["default"]["source_type"], "default_adapted")
            self.assertEqual(candidates["auto_tuned"]["source_type"], "fallback_default_copy")
            self.assertTrue(candidates["schema_aware"]["schema_used"])
            self.assertTrue(candidates["schema_fewshot"]["schema_used"])
            self.assertTrue(candidates["schema_fewshot"]["audit_used"])
            self.assertTrue(candidates["schema_fewshot"]["fewshot_used"])
            self.assertEqual(candidates["schema_fewshot"]["fewshot_example_count"], 2)
            self.assertIn("GraphRAG 官方 auto-tuned 输出不存在", "\n".join(candidates["auto_tuned"]["notes"]))

            default_text = (output_dir / "default" / "prompt.txt").read_text(encoding="utf-8")
            self.assertIn("Given a text document, identify entities and relationships.", default_text)
            self.assertIn("Course Baseline Constraints", default_text)
            self.assertIn("Course, Chapter, Concept", default_text)

            schema_aware_text = (output_dir / "schema_aware" / "prompt.txt").read_text(encoding="utf-8")
            self.assertIn("实体类型必须来自以下课程 Schema", schema_aware_text)
            self.assertIn("关系说明必须以 [type=<relation_type>]", schema_aware_text)
            self.assertIn("contains", schema_aware_text)
            self.assertIn("implemented_by", schema_aware_text)

            fewshot_text = (output_dir / "schema_fewshot" / "prompt.txt").read_text(encoding="utf-8")
            self.assertIn("Few-shot 示例", fewshot_text)
            self.assertIn("进程是程序的一次执行过程", fewshot_text)
            self.assertIn("实验步骤：实现时间片轮转调度算法", fewshot_text)

    def test_generate_candidate_prompts_falls_back_when_default_prompt_and_audit_gold_are_missing(self):
        with tempfile.TemporaryDirectory() as tmp_dir:
            root = Path(tmp_dir)
            schema_dir = root / "config" / "schema"
            samples_file = root / "data" / "prompt_tuning_samples" / "samples.json"
            audit_file = root / "data" / "eval" / "audit_extraction_set.json"
            output_dir = root / "prompts" / "candidates"
            auto_tuned_dir = output_dir / "auto_tuned"
            report_file = root / "results" / "reports" / "prompt_generation_report.json"

            self._write_schema_files(schema_dir)
            _write_json(samples_file, self._build_samples_payload())
            _write_json(audit_file, self._build_audit_payload(with_gold=False))
            auto_tuned_dir.mkdir(parents=True, exist_ok=True)
            (auto_tuned_dir / "extract_graph.txt").write_text(
                "-Goal-\nThis is an auto tuned prompt.\n-Auto Marker-\nAUTO_TUNED_BASE\n",
                encoding="utf-8",
            )
            (auto_tuned_dir / "README.md").write_text(
                "# auto_tuned\n\nOfficial README marker\n",
                encoding="utf-8",
            )
            self._write_existing_manifest(output_dir / "manifest.json")

            result = generate_candidate_prompts(
                schema_dir=schema_dir,
                samples_file=samples_file,
                audit_file=audit_file,
                default_prompt_dir=root / "missing-default-prompt",
                auto_tuned_prompt_dir=auto_tuned_dir,
                output_dir=output_dir,
                fewshot_k=3,
                language="zh",
                overwrite=True,
                report_file=report_file,
            )

            candidates = {item["candidate_name"]: item for item in result["manifest"]["candidates"]}

            self.assertEqual(candidates["default"]["source_type"], "generated_minimal_default")
            self.assertEqual(candidates["auto_tuned"]["source_type"], "graphrag_prompt_tune")
            self.assertEqual(candidates["schema_fewshot"]["fewshot_strategy"], "minimal_manual_examples")
            self.assertIn("未找到可复用的默认 Prompt", "\n".join(candidates["default"]["notes"]))
            self.assertIn("未发现可直接复用的 audit gold 标注", "\n".join(candidates["schema_fewshot"]["notes"]))

            default_text = (output_dir / "default" / "prompt.txt").read_text(encoding="utf-8")
            auto_tuned_text = (output_dir / "auto_tuned" / "prompt.txt").read_text(encoding="utf-8")
            schema_aware_text = (output_dir / "schema_aware" / "prompt.txt").read_text(encoding="utf-8")
            fewshot_text = (output_dir / "schema_fewshot" / "prompt.txt").read_text(encoding="utf-8")
            auto_tuned_readme = (output_dir / "auto_tuned" / "README.md").read_text(encoding="utf-8")

            self.assertIn("课程知识图谱抽取", default_text)
            self.assertIn("This is an auto tuned prompt.", auto_tuned_text)
            self.assertIn("AUTO_TUNED_BASE", schema_aware_text)
            self.assertIn("AUTO_TUNED_BASE", fewshot_text)
            self.assertIn("手写最小 few-shot 示例", fewshot_text)
            self.assertIn("Official README marker", auto_tuned_readme)
            self.assertTrue(report_file.exists())

            manifest = result["manifest"]
            auto_tuned_entry = next(item for item in manifest["candidates"] if item["candidate_name"] == "auto_tuned")
            self.assertEqual(auto_tuned_entry["generation_method"], "graphrag_official_prompt_tune")
            self.assertEqual(auto_tuned_entry["graphrag_invocation"], "python -m graphrag prompt-tune")

    def test_generate_candidate_prompts_does_not_treat_fallback_auto_tuned_as_official_base(self):
        with tempfile.TemporaryDirectory() as tmp_dir:
            root = Path(tmp_dir)
            schema_dir = root / "config" / "schema"
            default_prompt_dir = root / "prompts"
            output_dir = root / "prompts" / "candidates"
            auto_tuned_dir = output_dir / "auto_tuned"
            samples_file = root / "data" / "prompt_tuning_samples" / "prompt_tuning_samples.json"
            audit_file = root / "data" / "eval" / "audit_extraction_set.json"

            self._write_schema_files(schema_dir)
            self._write_default_prompt(default_prompt_dir)
            _write_json(samples_file, self._build_samples_payload())
            _write_json(audit_file, self._build_audit_payload(with_gold=True))

            auto_tuned_dir.mkdir(parents=True, exist_ok=True)
            (auto_tuned_dir / "prompt.txt").write_text(
                "-Goal-\nPLACEHOLDER_AUTO_TUNED\n",
                encoding="utf-8",
            )
            self._write_existing_manifest(output_dir / "manifest.json")
            manifest = json.loads((output_dir / "manifest.json").read_text(encoding="utf-8"))
            auto_tuned_entry = next(item for item in manifest["candidates"] if item["candidate_name"] == "auto_tuned")
            auto_tuned_entry["source_type"] = "fallback_default_copy"
            auto_tuned_entry.pop("generation_method", None)
            auto_tuned_entry.pop("graphrag_invocation", None)
            _write_json(output_dir / "manifest.json", manifest)

            result = generate_candidate_prompts(
                schema_dir=schema_dir,
                samples_file=samples_file,
                audit_file=audit_file,
                default_prompt_dir=default_prompt_dir,
                auto_tuned_prompt_dir=auto_tuned_dir,
                output_dir=output_dir,
                fewshot_k=2,
                language="zh",
                overwrite=True,
                report_file=root / "results" / "reports" / "prompt_generation_report.json",
            )

            candidates = {item["candidate_name"]: item for item in result["manifest"]["candidates"]}
            schema_aware_text = (output_dir / "schema_aware" / "prompt.txt").read_text(encoding="utf-8")
            fewshot_text = (output_dir / "schema_fewshot" / "prompt.txt").read_text(encoding="utf-8")

            auto_tuned_text = (output_dir / "auto_tuned" / "prompt.txt").read_text(encoding="utf-8")

            self.assertEqual(candidates["auto_tuned"]["source_type"], "fallback_default_copy")
            self.assertNotIn("PLACEHOLDER_AUTO_TUNED", auto_tuned_text)
            self.assertIn("Given a text document, identify entities and relationships.", auto_tuned_text)
            self.assertNotIn("PLACEHOLDER_AUTO_TUNED", schema_aware_text)
            self.assertNotIn("PLACEHOLDER_AUTO_TUNED", fewshot_text)
            self.assertIn("Given a text document, identify entities and relationships.", schema_aware_text)
            self.assertIn("官方 auto_tuned 占位候选尚未被真实 GraphRAG prompt-tune 结果覆盖", "\n".join(candidates["auto_tuned"]["notes"]))

    def test_generate_candidate_prompts_replaces_stale_manifest_notes_with_current_notes(self):
        with tempfile.TemporaryDirectory() as tmp_dir:
            root = Path(tmp_dir)
            schema_dir = root / "config" / "schema"
            default_prompt_dir = root / "prompts"
            output_dir = root / "prompts" / "candidates"
            auto_tuned_dir = output_dir / "auto_tuned"
            samples_file = root / "data" / "prompt_tuning_samples" / "prompt_tuning_samples.json"
            audit_file = root / "data" / "eval" / "audit_extraction_set.json"

            self._write_schema_files(schema_dir)
            self._write_default_prompt(default_prompt_dir)
            _write_json(samples_file, self._build_samples_payload())
            _write_json(audit_file, self._build_audit_payload(with_gold=True))

            auto_tuned_dir.mkdir(parents=True, exist_ok=True)
            (auto_tuned_dir / "extract_graph.txt").write_text(
                "-Goal-\nOfficial auto tuned prompt\n-Auto Marker-\nAUTO_TUNED_BASE\n",
                encoding="utf-8",
            )

            stale_manifest = {
                "task": "candidate_prompt_generation",
                "generated_at": "2026-04-16T16:00:00+08:00",
                "candidates": [
                    {
                        "candidate_name": "auto_tuned",
                        "source_type": "graphrag_prompt_tune",
                        "generation_method": "graphrag_official_prompt_tune",
                        "graphrag_invocation": "python -m graphrag prompt-tune",
                        "notes": [
                            "GraphRAG 官方 auto-tuned 输出不存在，将保留占位目录并回退到 default 候选 Prompt。",
                            "旧的占位说明",
                        ],
                    },
                    {
                        "candidate_name": "schema_aware",
                        "source_type": "schema_augmented",
                        "notes": [
                            "schema_aware 优先基于默认 GraphRAG Prompt自动增强；若 auto_tuned 缺失则回退到 default。",
                            "旧的 schema 备注",
                        ],
                    },
                    {
                        "candidate_name": "schema_fewshot",
                        "source_type": "schema_fewshot",
                        "notes": [
                            "schema_fewshot 继承 schema_aware，并继续沿用 默认 GraphRAG Prompt 作为底稿。",
                            "旧的 few-shot 备注",
                        ],
                    },
                ],
            }
            _write_json(output_dir / "manifest.json", stale_manifest)

            result = generate_candidate_prompts(
                schema_dir=schema_dir,
                samples_file=samples_file,
                audit_file=audit_file,
                default_prompt_dir=default_prompt_dir,
                auto_tuned_prompt_dir=auto_tuned_dir,
                output_dir=output_dir,
                fewshot_k=2,
                language="zh",
                overwrite=True,
                report_file=root / "results" / "reports" / "prompt_generation_report.json",
            )

            candidates = {item["candidate_name"]: item for item in result["manifest"]["candidates"]}

            self.assertEqual(candidates["auto_tuned"]["source_type"], "graphrag_prompt_tune")
            self.assertEqual(candidates["auto_tuned"]["generation_method"], "graphrag_official_prompt_tune")
            self.assertNotIn("旧的占位说明", candidates["auto_tuned"]["notes"])
            self.assertNotIn("回退到 default", "\n".join(candidates["auto_tuned"]["notes"]))
            self.assertIn("auto_tuned 候选由 GraphRAG 官方 prompt-tune 生成。", candidates["auto_tuned"]["notes"])

            self.assertEqual(
                candidates["schema_aware"]["notes"],
                [
                    "schema_aware 优先基于官方 auto_tuned Prompt自动增强；若 auto_tuned 缺失则回退到 default。",
                    "在基底 Prompt 上显式注入实体类型、关系类型和关键抽取规则摘要。",
                    "关系输出仍沿用 GraphRAG tuple 结构，但要求 relationship_description 以 [type=<relation_type>] 开头，便于后续评测解析。",
                ],
            )
            self.assertEqual(
                candidates["schema_fewshot"]["notes"][0],
                "schema_fewshot 继承 schema_aware，并继续沿用 官方 auto_tuned Prompt 作为底稿。",
            )
            self.assertNotIn("旧的 few-shot 备注", candidates["schema_fewshot"]["notes"])


if __name__ == "__main__":
    unittest.main()
