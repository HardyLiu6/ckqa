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
from extraction_eval.diagnose_gold_missing_relations import (
    classify_gold_relations,
    diagnose_eval_dir,
)
from extraction_eval.scoring_audit import AuditEntry


def _success_result(sample_id, entities, relationships) -> StructuredExtractionResult:
    return StructuredExtractionResult(
        sample_id=sample_id,
        candidate="default",
        status="success",
        entities=[ExtractionEntity(**entity) for entity in entities],
        relationships=[ExtractionRelationship(**relationship) for relationship in relationships],
        raw_output="{}",
    )


def _audit_entry(gold_entities, gold_relations) -> AuditEntry:
    return AuditEntry(
        gold_entities=list(gold_entities),
        gold_relations=list(gold_relations),
        gold_seed=True,
        gold_seed_version="manual_gold_seed_v1",
    )


class TestClassifyGoldRelations(unittest.TestCase):
    def test_all_seven_categories_are_covered_once(self):
        # gold 端：7 条关系，每条对应一个 category。
        gold_entities = [
            {"entity_id": "g1", "name": "进程", "type": "Concept", "alias": []},
            {"entity_id": "g2", "name": "程序控制块", "type": "Concept", "alias": []},
            {"entity_id": "g3", "name": "调度器", "type": "AlgorithmOrMethod", "alias": []},
            {"entity_id": "g4", "name": "状态转换图", "type": "FormulaOrDefinition", "alias": []},
            {"entity_id": "g5", "name": "第一章 引论", "type": "Chapter", "alias": []},
            # 下面两个 gold 实体故意不让抽取产物里出现。
            {"entity_id": "g6", "name": "操作系统教材", "type": "Course", "alias": []},
            {"entity_id": "g7", "name": "未抽实体B", "type": "Term", "alias": []},
        ]
        gold_relations = [
            {"relation_id": "r_hit", "source_entity_id": "g1", "target_entity_id": "g2", "type": "defined_by"},
            {"relation_id": "r_rev", "source_entity_id": "g1", "target_entity_id": "g3", "type": "implemented_by"},
            {"relation_id": "r_wrong", "source_entity_id": "g1", "target_entity_id": "g4", "type": "defined_by"},
            {"relation_id": "r_no_edge", "source_entity_id": "g1", "target_entity_id": "g5", "type": "appears_in"},
            {"relation_id": "r_src_miss", "source_entity_id": "g6", "target_entity_id": "g5", "type": "contains"},
            {"relation_id": "r_tgt_miss", "source_entity_id": "g1", "target_entity_id": "g7", "type": "defined_by"},
            {"relation_id": "r_both_miss", "source_entity_id": "g6", "target_entity_id": "g7", "type": "contains"},
        ]
        audit_index = {"s1": _audit_entry(gold_entities, gold_relations)}

        # 抽取端：缺 g6 和 g7，其余实体齐全，关系覆盖 hit/reversed/wrong_type 三种形态。
        entities = [
            {"id": "e1", "title": "进程", "type": "Concept", "alias": []},
            {"id": "e2", "title": "程序控制块", "type": "Concept", "alias": []},
            {"id": "e3", "title": "调度器", "type": "AlgorithmOrMethod", "alias": []},
            {"id": "e4", "title": "状态转换图", "type": "FormulaOrDefinition", "alias": []},
            {"id": "e5", "title": "第一章 引论", "type": "Chapter", "alias": []},
        ]
        relationships = [
            {"source": "进程", "target": "程序控制块", "type": "defined_by",
             "description": "", "evidence": ""},
            {"source": "调度器", "target": "进程", "type": "implemented_by",
             "description": "", "evidence": ""},
            {"source": "进程", "target": "状态转换图", "type": "related_to",
             "description": "", "evidence": ""},
        ]

        result = _success_result("s1", entities, relationships)
        report = classify_gold_relations(results=[result], audit_index=audit_index)

        counts = report["totals"]["counts"]
        self.assertEqual(report["totals"]["total"], 7)
        self.assertEqual(counts["hit"], 1)
        self.assertEqual(counts["direction_reversed"], 1)
        self.assertEqual(counts["wrong_type"], 1)
        self.assertEqual(counts["both_endpoints_present_but_not_connected"], 1)
        self.assertEqual(counts["source_endpoint_missing"], 1)
        self.assertEqual(counts["target_endpoint_missing"], 1)
        self.assertEqual(counts["both_endpoints_missing"], 1)

        by_rt = report["by_relation_type"]
        # defined_by 在 gold 里出现 3 次：r_hit、r_wrong、r_tgt_miss
        self.assertEqual(by_rt["defined_by"]["total"], 3)
        self.assertEqual(by_rt["defined_by"]["counts"]["hit"], 1)
        self.assertEqual(by_rt["defined_by"]["counts"]["wrong_type"], 1)
        self.assertEqual(by_rt["defined_by"]["counts"]["target_endpoint_missing"], 1)

    def test_parse_error_sample_counts_as_both_missing(self):
        gold_entities = [
            {"entity_id": "g1", "name": "进程", "type": "Concept", "alias": []},
            {"entity_id": "g2", "name": "PCB", "type": "Concept", "alias": []},
        ]
        gold_relations = [
            {"relation_id": "r1", "source_entity_id": "g1", "target_entity_id": "g2", "type": "defined_by"},
        ]
        audit_index = {"s1": _audit_entry(gold_entities, gold_relations)}
        result = StructuredExtractionResult(
            sample_id="s1",
            candidate="default",
            status="parse_error",
            entities=[],
            relationships=[],
            raw_output="",
        )
        report = classify_gold_relations(results=[result], audit_index=audit_index)
        self.assertEqual(report["totals"]["counts"]["both_endpoints_missing"], 1)
        self.assertEqual(report["totals"]["total"], 1)


class TestDiagnoseEvalDir(unittest.TestCase):
    def test_writes_summary_and_aggregate(self):
        with tempfile.TemporaryDirectory() as tmp_dir:
            root = Path(tmp_dir)
            eval_dir = root / "results" / "extraction_eval" / "runs" / "demo"
            eval_dir.mkdir(parents=True)
            audit_path = root / "audit.json"

            gold_entities = [
                {"entity_id": "g1", "name": "进程", "type": "Concept", "alias": []},
                {"entity_id": "g2", "name": "PCB", "type": "Term", "alias": []},
            ]
            gold_relations = [
                {"relation_id": "r1", "source_entity_id": "g1", "target_entity_id": "g2", "type": "defined_by"},
            ]
            audit_payload = {
                "audit_samples": [
                    {
                        "source_sample_id": "s1",
                        "gold_entities": gold_entities,
                        "gold_relations": gold_relations,
                        "gold_seed": True,
                        "gold_seed_version": "manual_gold_seed_v1",
                    }
                ]
            }
            audit_path.write_text(json.dumps(audit_payload, ensure_ascii=False), encoding="utf-8")

            eval_payload = {
                "candidate": "default",
                "results": [
                    {
                        "sample_id": "s1",
                        "candidate": "default",
                        "status": "success",
                        "entities": [
                            {"id": "e1", "title": "进程", "type": "Concept", "alias": [],
                             "definition_text": "", "description": "", "evidence": ""},
                            {"id": "e2", "title": "PCB", "type": "Term", "alias": [],
                             "definition_text": "", "description": "", "evidence": ""},
                        ],
                        "relationships": [
                            {"source": "进程", "target": "PCB", "type": "defined_by",
                             "description": "", "evidence": ""}
                        ],
                        "raw_output": "{}",
                    }
                ],
            }
            (eval_dir / "default.json").write_text(
                json.dumps(eval_payload, ensure_ascii=False), encoding="utf-8"
            )

            summary = diagnose_eval_dir(
                root=root,
                eval_dir=eval_dir,
                audit_path=audit_path,
                run_id="demo_diagnose",
                overwrite=True,
            )

            self.assertEqual(summary["candidate_count"], 1)
            self.assertEqual(summary["aggregate_across_candidates"]["counts"]["hit"], 1)
            report_dir = root / "results" / "reports" / "extraction_missing_relations" / "runs" / "demo_diagnose"
            self.assertTrue((report_dir / "summary.json").exists())
            self.assertTrue((report_dir / "summary.md").exists())


if __name__ == "__main__":
    unittest.main()
