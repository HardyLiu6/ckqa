from __future__ import annotations

import json
import sys
import tempfile
import unittest
from pathlib import Path

_PROJECT_ROOT = Path(__file__).resolve().parent.parent
_SCRIPTS_DIR = _PROJECT_ROOT / "scripts"
if str(_SCRIPTS_DIR) not in sys.path:
    sys.path.insert(0, str(_SCRIPTS_DIR))

from extraction_schema import ExtractionEntity, ExtractionRelationship, StructuredExtractionResult
from extraction_eval.relationship_postprocessor import (
    postprocess_eval_dir,
    postprocess_relationships,
    run_postprocess_from_args,
)
from scoring_metrics import analyze_endpoint_validity


def _success_result(entities, relationships) -> StructuredExtractionResult:
    return StructuredExtractionResult(
        sample_id="pts-postprocess",
        candidate="default",
        status="success",
        entities=[ExtractionEntity(**entity) for entity in entities],
        relationships=[ExtractionRelationship(**relationship) for relationship in relationships],
        raw_output="{}",
    )


RELATION_SCHEMA = {
    "contains": {
        "source_types": ["Course", "Chapter", "Section", "KnowledgePoint", "Concept", "AlgorithmOrMethod"],
        "target_types": ["Chapter", "Section", "KnowledgePoint", "Concept", "Term", "FormulaOrDefinition", "AlgorithmOrMethod"],
    },
    "belongs_to": {
        "source_types": ["KnowledgePoint", "Concept", "Term", "AlgorithmOrMethod"],
        "target_types": ["Course", "Chapter", "Section"],
    },
    "related_to": {
        "source_types": ["Concept", "Term", "AlgorithmOrMethod"],
        "target_types": ["Concept", "Term", "AlgorithmOrMethod"],
    },
    "defined_by": {
        "source_types": ["KnowledgePoint", "Concept", "Term", "AlgorithmOrMethod"],
        "target_types": ["FormulaOrDefinition", "Term"],
    },
    "implemented_by": {
        "source_types": ["KnowledgePoint", "Concept", "AlgorithmOrMethod", "Experiment", "Assignment"],
        "target_types": ["AlgorithmOrMethod", "ToolOrPlatform"],
    },
    "applied_in": {
        "source_types": ["KnowledgePoint", "Concept", "Term", "FormulaOrDefinition", "AlgorithmOrMethod"],
        "target_types": ["KnowledgePoint", "Concept", "Section", "Experiment", "Assignment", "ToolOrPlatform"],
    },
    "evaluated_by": {
        "source_types": ["Course", "Chapter", "Section", "KnowledgePoint", "Concept", "Term", "AlgorithmOrMethod", "Experiment"],
        "target_types": ["Assignment"],
    },
    "appears_in": {
        "source_types": ["KnowledgePoint", "Concept", "Term", "FormulaOrDefinition", "AlgorithmOrMethod", "ToolOrPlatform"],
        "target_types": ["Course", "Chapter", "Section", "Experiment", "Assignment", "ToolOrPlatform"],
    },
    "depends_on": {
        "source_types": ["Chapter", "Section", "KnowledgePoint", "Concept", "Term", "FormulaOrDefinition", "AlgorithmOrMethod", "Experiment", "Assignment"],
        "target_types": ["Chapter", "Section", "KnowledgePoint", "Concept", "Term", "FormulaOrDefinition", "AlgorithmOrMethod", "Experiment", "Assignment"],
    },
}


class TestRelationshipPostprocessor(unittest.TestCase):
    def test_strict_mode_canonicalizes_alias_endpoints(self):
        result = _success_result(
            [
                {"id": "e1", "title": "文件分配表", "type": "Concept", "alias": ["FAT"]},
                {"id": "e2", "title": "簇", "type": "Concept"},
            ],
            [
                {
                    "source": "FAT",
                    "target": "簇",
                    "type": "related_to",
                    "description": "FAT 与簇相关。",
                    "evidence": "文件分配表 FAT 中引入簇。",
                }
            ],
        )

        processed, summary = postprocess_relationships([result], RELATION_SCHEMA, mode="strict")

        self.assertEqual(summary["actions"]["canonicalize_source_alias"], 1)
        self.assertEqual(processed[0].relationships[0].source, "文件分配表")
        self.assertEqual(analyze_endpoint_validity(processed, RELATION_SCHEMA)["invalid_count"], 0)

    def test_strict_mode_converts_concept_taxonomy_belongs_to_into_contains(self):
        result = _success_result(
            [
                {"id": "e1", "title": "媒体", "type": "Concept"},
                {"id": "e2", "title": "感觉媒体", "type": "Concept"},
            ],
            [
                {
                    "source": "感觉媒体",
                    "target": "媒体",
                    "type": "belongs_to",
                    "description": "感觉媒体是媒体的一种分类。",
                    "evidence": "媒体可分为以下六类：感觉媒体。",
                }
            ],
        )

        processed, summary = postprocess_relationships([result], RELATION_SCHEMA, mode="strict")

        self.assertEqual(summary["actions"]["convert_belongs_to_taxonomy_to_contains"], 1)
        relation = processed[0].relationships[0]
        self.assertEqual(relation.type, "contains")
        self.assertEqual(relation.source, "媒体")
        self.assertEqual(relation.target, "感觉媒体")
        self.assertEqual(analyze_endpoint_validity(processed, RELATION_SCHEMA)["valid_rate"], 1.0)

    def test_strict_mode_swaps_reversed_defined_by_endpoints(self):
        result = _success_result(
            [
                {"id": "e1", "title": "周转时间公式", "type": "FormulaOrDefinition"},
                {"id": "e2", "title": "周转时间", "type": "Concept"},
            ],
            [
                {
                    "source": "周转时间公式",
                    "target": "周转时间",
                    "type": "defined_by",
                    "description": "公式定义概念。",
                    "evidence": "周转时间 = 完成时间 - 提交时间",
                }
            ],
        )

        processed, summary = postprocess_relationships([result], RELATION_SCHEMA, mode="strict")

        self.assertEqual(summary["actions"]["swap_reversed_endpoints"], 1)
        relation = processed[0].relationships[0]
        self.assertEqual(relation.type, "defined_by")
        self.assertEqual(relation.source, "周转时间")
        self.assertEqual(relation.target, "周转时间公式")
        self.assertEqual(analyze_endpoint_validity(processed, RELATION_SCHEMA)["valid_rate"], 1.0)

    def test_strict_mode_swaps_reversed_applied_in_endpoints(self):
        result = _success_result(
            [
                {"id": "e1", "title": "死锁", "type": "Concept"},
                {"id": "e2", "title": "银行家算法", "type": "AlgorithmOrMethod"},
            ],
            [
                {
                    "source": "死锁",
                    "target": "银行家算法",
                    "type": "applied_in",
                    "description": "死锁以银行家算法为例。",
                    "evidence": "介绍死锁时以银行家算法为例。",
                }
            ],
        )

        processed, summary = postprocess_relationships([result], RELATION_SCHEMA, mode="strict")

        self.assertEqual(summary["actions"]["swap_reversed_endpoints"], 1)
        relation = processed[0].relationships[0]
        self.assertEqual(relation.source, "银行家算法")
        self.assertEqual(relation.target, "死锁")

    def test_strict_mode_retypes_section_appears_in_to_contains(self):
        result = _success_result(
            [
                {"id": "e1", "title": "7.1 文件和文件系统", "type": "Section"},
                {"id": "e2", "title": "数据项", "type": "Concept"},
            ],
            [
                {
                    "source": "7.1 文件和文件系统",
                    "target": "数据项",
                    "type": "appears_in",
                    "description": "本节介绍数据项、记录、文件。",
                    "evidence": "7.1 文件和文件系统 介绍数据项。",
                }
            ],
        )

        processed, summary = postprocess_relationships([result], RELATION_SCHEMA, mode="strict")

        self.assertEqual(summary["actions"]["retype_container_appears_in_to_contains"], 1)
        relation = processed[0].relationships[0]
        self.assertEqual(relation.type, "contains")
        self.assertEqual(relation.source, "7.1 文件和文件系统")
        self.assertEqual(relation.target, "数据项")

    def test_strict_mode_does_not_swap_depends_on_direction(self):
        result = _success_result(
            [
                {"id": "e1", "title": "A", "type": "Concept"},
                {"id": "e2", "title": "B", "type": "Concept"},
            ],
            [
                {
                    "source": "A",
                    "target": "B",
                    "type": "depends_on",
                    "description": "A 依赖 B。",
                    "evidence": "A 的理解依赖 B。",
                }
            ],
        )

        processed, summary = postprocess_relationships([result], RELATION_SCHEMA, mode="strict")

        # depends_on 不在 SWAP_ENDPOINTS_RELATION_TYPES 里，即便方向被翻也不动。
        self.assertEqual(summary["actions"].get("swap_reversed_endpoints", 0), 0)
        self.assertEqual(processed[0].relationships[0].source, "A")
        self.assertEqual(processed[0].relationships[0].target, "B")

    def test_strict_mode_drops_missing_and_semantic_defined_by_failures(self):
        result = _success_result(
            [
                {"id": "e1", "title": "连续组织方式", "type": "Concept"},
                {"id": "e2", "title": "进程", "type": "Concept"},
                {"id": "e3", "title": "PCB", "type": "Term"},
            ],
            [
                {
                    "source": "连续组织方式",
                    "target": "顺序文件",
                    "type": "related_to",
                    "description": "连续组织方式形成顺序文件。",
                    "evidence": "由连续组织方式所形成的顺序文件。",
                },
                {
                    "source": "进程",
                    "target": "PCB",
                    "type": "defined_by",
                    "description": "PCB 是进程存在的唯一标志。",
                    "evidence": "为什么说 PCB 是进程存在的唯一标志？",
                },
            ],
        )

        processed, summary = postprocess_relationships([result], RELATION_SCHEMA, mode="strict")

        self.assertEqual(processed[0].relationships, [])
        self.assertEqual(summary["dropped_relationship_count"], 2)
        self.assertEqual(summary["dropped_by_reason"]["missing_target"], 1)
        self.assertEqual(summary["dropped_by_reason"]["semantic_defined_by_term_needs_symbol_cue"], 1)

    def test_strict_closure_mode_adds_grounded_missing_target_entity(self):
        result = _success_result(
            [{"id": "e1", "title": "连续组织方式", "type": "Concept"}],
            [
                {
                    "source": "连续组织方式",
                    "target": "顺序文件",
                    "type": "related_to",
                    "description": "连续组织方式形成顺序文件。",
                    "evidence": "由连续组织方式所形成的顺序文件。",
                }
            ],
        )

        processed, summary = postprocess_relationships([result], RELATION_SCHEMA, mode="strict-closure")

        self.assertEqual(summary["actions"]["add_missing_target_entity"], 1)
        self.assertEqual(summary["created_entity_count"], 1)
        self.assertEqual(processed[0].entities[-1].title, "顺序文件")
        self.assertEqual(processed[0].entities[-1].type, "Concept")
        self.assertEqual(processed[0].relationships[0].target, "顺序文件")
        self.assertEqual(analyze_endpoint_validity(processed, RELATION_SCHEMA)["valid_rate"], 1.0)

    def test_strict_closure_mode_does_not_add_ungrounded_missing_endpoint(self):
        result = _success_result(
            [{"id": "e1", "title": "连续组织方式", "type": "Concept"}],
            [
                {
                    "source": "连续组织方式",
                    "target": "幻觉文件",
                    "type": "related_to",
                    "description": "连续组织方式形成文件。",
                    "evidence": "这里没有直接出现那个目标端点。",
                }
            ],
        )

        processed, summary = postprocess_relationships([result], RELATION_SCHEMA, mode="strict-closure")

        self.assertEqual(processed[0].relationships, [])
        self.assertEqual(summary["created_entity_count"], 0)
        self.assertEqual(summary["dropped_by_reason"]["missing_target"], 1)

    def test_postprocess_eval_dir_writes_diagnostic_run(self):
        with tempfile.TemporaryDirectory() as tmp_dir:
            root = Path(tmp_dir)
            eval_dir = root / "results" / "extraction_eval" / "runs" / "smoke"
            eval_dir.mkdir(parents=True)
            payload = {
                "task": "candidate_extraction",
                "candidate": "default",
                "model": "fake-model",
                "samples_file": str(root / "samples.json"),
                "manifest_file": str(root / "manifest.json"),
                "run_id": "smoke",
                "summary": {"total": 1, "success": 1, "parse_error": 0, "llm_error": 0},
                "results": [
                    _success_result(
                        [
                            {"id": "e1", "title": "媒体", "type": "Concept"},
                            {"id": "e2", "title": "感觉媒体", "type": "Concept"},
                        ],
                        [{"source": "感觉媒体", "target": "媒体", "type": "belongs_to"}],
                    ).model_dump(mode="json")
                ],
            }
            (eval_dir / "default.json").write_text(json.dumps(payload, ensure_ascii=False), encoding="utf-8")

            summary = postprocess_eval_dir(
                root=root,
                eval_dir=eval_dir,
                relation_schema=RELATION_SCHEMA,
                output_run_id="smoke-structured",
                mode="strict",
                overwrite=True,
            )

            output_file = root / "results" / "extraction_eval" / "runs" / "smoke-structured" / "default.json"
            self.assertTrue(output_file.exists())
            output_payload = json.loads(output_file.read_text(encoding="utf-8"))
            self.assertEqual(output_payload["postprocess"]["mode"], "strict")
            self.assertEqual(output_payload["results"][0]["relationships"][0]["type"], "contains")
            self.assertEqual(summary["candidate_summaries"]["default"]["actions"]["convert_belongs_to_taxonomy_to_contains"], 1)

    def test_cli_args_write_postprocessed_run(self):
        with tempfile.TemporaryDirectory() as tmp_dir:
            root = Path(tmp_dir)
            eval_dir = root / "results" / "extraction_eval" / "runs" / "smoke"
            eval_dir.mkdir(parents=True)
            schema_dir = root / "config" / "schema"
            schema_dir.mkdir(parents=True)
            relation_schema_path = schema_dir / "relation_types.json"
            relation_schema_path.write_text(
                json.dumps({"relation_types": RELATION_SCHEMA}, ensure_ascii=False),
                encoding="utf-8",
            )
            payload = {
                "task": "candidate_extraction",
                "candidate": "default",
                "model": "fake-model",
                "samples_file": str(root / "samples.json"),
                "manifest_file": str(root / "manifest.json"),
                "run_id": "smoke",
                "summary": {"total": 1, "success": 1, "parse_error": 0, "llm_error": 0},
                "results": [
                    _success_result(
                        [
                            {"id": "e1", "title": "媒体", "type": "Concept"},
                            {"id": "e2", "title": "感觉媒体", "type": "Concept"},
                        ],
                        [{"source": "感觉媒体", "target": "媒体", "type": "belongs_to"}],
                    ).model_dump(mode="json")
                ],
            }
            (eval_dir / "default.json").write_text(json.dumps(payload, ensure_ascii=False), encoding="utf-8")

            summary = run_postprocess_from_args(
                [
                    "--root",
                    str(root),
                    "--eval-dir",
                    str(eval_dir),
                    "--relation-schema",
                    str(relation_schema_path),
                    "--output-run-id",
                    "smoke-cli-structured",
                    "--overwrite",
                ]
            )

            self.assertEqual(summary["output_run_id"], "smoke-cli-structured")
            self.assertTrue(
                (root / "results" / "extraction_eval" / "runs" / "smoke-cli-structured" / "default.json").exists()
            )


if __name__ == "__main__":
    unittest.main()
