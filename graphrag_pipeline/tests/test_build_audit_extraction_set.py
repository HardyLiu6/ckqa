#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
build_audit_extraction_set 基础测试
==================================
验证 audit 校准集脚本能够：
1. 兼容读取带 samples 包装层的 prompt tuning 样本。
2. 生成稳定的 audit 输出字段。
3. 生成引用现有 schema 的标注说明。
"""

import json
import sys
import tempfile
import unittest
from pathlib import Path


_PROJECT_ROOT = Path(__file__).resolve().parent.parent
_SCRIPT_DIR = _PROJECT_ROOT / "scripts"
if str(_SCRIPT_DIR) not in sys.path:
    sys.path.insert(0, str(_SCRIPT_DIR))

from build_audit_extraction_set import build_audit_dataset, load_schema_catalog


class TestBuildAuditExtractionSet(unittest.TestCase):
    def setUp(self) -> None:
        self.schema_catalog = load_schema_catalog()

    def test_build_audit_dataset_supports_wrapped_samples_payload(self):
        samples = {
            "schema_version": "v1",
            "samples": [
                {
                    "sample_id": "pts-001",
                    "source_doc_id": "doc-001",
                    "source_file": "book-a.pdf",
                    "document_type": "textbook",
                    "course_id": "os",
                    "chapter": "第二章 进程的描述与控制",
                    "section": "2.1 前趋图和程序执行",
                    "heading_path": ["第二章 进程的描述与控制", "2.1 前趋图和程序执行"],
                    "heading_level": 2,
                    "text": "定义：进程是程序的一次执行过程。该定义用于解释进程与程序的区别。",
                    "text_length": 36,
                    "page_start": 12,
                    "page_end": 12,
                    "guessed_sample_type": "definition_or_formula",
                    "metadata_summary": {"has_equation": False},
                },
                {
                    "sample_id": "pts-002",
                    "source_doc_id": "doc-002",
                    "source_file": "book-a.pdf",
                    "document_type": "textbook",
                    "course_id": "os",
                    "chapter": "第二章 进程的描述与控制",
                    "section": "2.2 进程控制",
                    "heading_path": ["第二章 进程的描述与控制", "2.2 进程控制"],
                    "heading_level": 2,
                    "text": "算法步骤：首先创建 PCB，然后分配资源，最后进入调度队列。",
                    "text_length": 33,
                    "page_start": 18,
                    "page_end": 18,
                    "guessed_sample_type": "algorithm_or_method",
                    "metadata_summary": {"has_equation": False},
                },
                {
                    "sample_id": "pts-003",
                    "source_doc_id": "doc-003",
                    "source_file": "slides-b.pdf",
                    "document_type": "slides",
                    "course_id": "os",
                    "chapter": "第三章 处理机调度与死锁",
                    "section": "实验一 进程调度",
                    "heading_path": ["第三章 处理机调度与死锁", "实验一 进程调度"],
                    "heading_level": 2,
                    "text": "实验步骤：1. 配置环境；2. 编写代码；3. 观察结果；4. 分析现象。",
                    "text_length": 35,
                    "page_start": 30,
                    "page_end": 31,
                    "guessed_sample_type": "experiment_instruction",
                    "metadata_summary": {"has_equation": False},
                },
                {
                    "sample_id": "pts-004",
                    "source_doc_id": "doc-004",
                    "source_file": "slides-b.pdf",
                    "document_type": "slides",
                    "course_id": "os",
                    "chapter": "第三章 处理机调度与死锁",
                    "section": "习题",
                    "heading_path": ["第三章 处理机调度与死锁", "习题"],
                    "heading_level": 3,
                    "text": "作业要求：请分析时间片轮转算法与优先级调度算法的差异，并给出原因。",
                    "text_length": 38,
                    "page_start": 44,
                    "page_end": 44,
                    "guessed_sample_type": "assignment_requirement",
                    "metadata_summary": {"has_equation": False},
                },
            ],
        }

        with tempfile.TemporaryDirectory() as tmp_dir:
            input_file = Path(tmp_dir) / "samples.json"
            input_file.write_text(json.dumps(samples, ensure_ascii=False, indent=2), encoding="utf-8")

            dataset, report, guidelines = build_audit_dataset(
                input_file=input_file,
                sample_size=3,
                random_seed=7,
                prefer_balanced_sampling=True,
                priority_fields=["guessed_sample_type", "document_type", "chapter", "source_file"],
                schema_catalog=self.schema_catalog,
            )

        self.assertEqual(dataset["task"], "audit_extraction_set")
        self.assertEqual(dataset["stats"]["input_sample_count"], 4)
        self.assertEqual(dataset["stats"]["selected_count"], 3)
        self.assertEqual(report["selection_strategy"], "balanced_stratified")
        self.assertIn("Course", dataset["schema_reference"]["entity_type_names"])
        self.assertIn("contains", dataset["schema_reference"]["relation_type_names"])
        self.assertIn("实体类型来源于", guidelines)

        sample = dataset["audit_samples"][0]
        for key in (
            "id",
            "source_sample_id",
            "source_doc_id",
            "source_file",
            "document_type",
            "course_id",
            "chapter",
            "section",
            "heading_path",
            "heading_level",
            "text",
            "text_length",
            "page_start",
            "page_end",
            "audit_priority",
            "audit_reason",
            "gold_entities",
            "gold_relations",
            "annotation_notes",
            "reviewer_decision",
            "reviewer_confidence",
        ):
            self.assertIn(key, sample)

        self.assertIsInstance(sample["heading_path"], list)
        self.assertEqual(sample["gold_entities"], [])
        self.assertEqual(sample["gold_relations"], [])
        self.assertEqual(sample["annotation_notes"], "")
        self.assertEqual(sample["reviewer_decision"], "")
        self.assertEqual(sample["reviewer_confidence"], "")


if __name__ == "__main__":
    unittest.main()
