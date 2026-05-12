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

from extraction_eval.simulate_container_seed_injection import simulate_eval_dir


class TestSimulateContainerSeedInjection(unittest.TestCase):
    def test_container_gold_entities_are_injected_once_per_sample(self):
        with tempfile.TemporaryDirectory() as tmp:
            root = Path(tmp)
            eval_dir = root / "results" / "extraction_eval" / "runs" / "src"
            eval_dir.mkdir(parents=True)
            audit_path = root / "audit.json"

            audit_path.write_text(
                json.dumps(
                    {
                        "audit_samples": [
                            {
                                "source_sample_id": "s1",
                                "gold_entities": [
                                    {"entity_id": "g1", "name": "第一章 操作系统", "type": "Chapter", "alias": []},
                                    {"entity_id": "g2", "name": "1.1 引论", "type": "Section", "alias": []},
                                    {"entity_id": "g3", "name": "进程", "type": "Concept", "alias": []},
                                ],
                                "gold_relations": [],
                                "gold_seed": True,
                                "gold_seed_version": "manual_gold_seed_v1",
                            }
                        ]
                    },
                    ensure_ascii=False,
                ),
                encoding="utf-8",
            )
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
                            # 故意让某个容器型实体已存在，验证不会被重复注入
                            {"id": "e2", "title": "第一章 操作系统", "type": "Chapter", "alias": [],
                             "definition_text": "", "description": "", "evidence": ""},
                        ],
                        "relationships": [],
                        "raw_output": "",
                        "error": None,
                        "parser_error_code": None,
                        "llm_debug": None,
                    }
                ],
            }
            (eval_dir / "default.json").write_text(
                json.dumps(eval_payload, ensure_ascii=False), encoding="utf-8"
            )

            summary = simulate_eval_dir(
                root=root,
                eval_dir=eval_dir,
                audit_path=audit_path,
                output_run_id="sim_demo",
                overwrite=True,
            )

            self.assertEqual(summary["candidate_count"], 1)
            # gold 容器型实体两条，一条已存在；应只注入 1 条。
            self.assertEqual(summary["total_injected_entity_count"], 1)

            out_file = root / "results" / "extraction_eval" / "runs" / "sim_demo" / "default.json"
            self.assertTrue(out_file.exists())
            out_payload = json.loads(out_file.read_text(encoding="utf-8"))
            titles = {entity["title"] for entity in out_payload["results"][0]["entities"]}
            self.assertIn("1.1 引论", titles)
            self.assertIn("第一章 操作系统", titles)
            self.assertNotIn("未出现的实体", titles)
            # 关系不应被改动
            self.assertEqual(out_payload["results"][0]["relationships"], [])

    def test_inject_metadata_contains_derives_chapter_section_and_knowledge_edges(self):
        with tempfile.TemporaryDirectory() as tmp:
            root = Path(tmp)
            eval_dir = root / "results" / "extraction_eval" / "runs" / "src"
            eval_dir.mkdir(parents=True)
            audit_path = root / "audit.json"

            audit_path.write_text(
                json.dumps(
                    {
                        "audit_samples": [
                            {
                                "source_sample_id": "s1",
                                "gold_entities": [
                                    {"entity_id": "g1", "name": "第一章 引论", "type": "Chapter", "alias": []},
                                    {"entity_id": "g2", "name": "1.1 基础", "type": "Section", "alias": []},
                                ],
                                "gold_relations": [],
                                "gold_seed": True,
                                "gold_seed_version": "manual_gold_seed_v1",
                            }
                        ]
                    },
                    ensure_ascii=False,
                ),
                encoding="utf-8",
            )
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
                        ],
                        "relationships": [],
                        "raw_output": "",
                        "error": None,
                        "parser_error_code": None,
                        "llm_debug": None,
                    }
                ],
            }
            (eval_dir / "default.json").write_text(
                json.dumps(eval_payload, ensure_ascii=False), encoding="utf-8"
            )

            summary = simulate_eval_dir(
                root=root,
                eval_dir=eval_dir,
                audit_path=audit_path,
                output_run_id="sim_demo",
                overwrite=True,
                inject_metadata_contains=True,
            )

            self.assertEqual(summary["total_injected_entity_count"], 2)
            # 应派生：Chapter→Section、Section→Concept，共 2 条 contains
            self.assertEqual(summary["total_injected_metadata_relationship_count"], 2)

            out_file = root / "results" / "extraction_eval" / "runs" / "sim_demo" / "default.json"
            out_payload = json.loads(out_file.read_text(encoding="utf-8"))
            relationships = out_payload["results"][0]["relationships"]
            triples = {(r["source"], r["target"], r["type"]) for r in relationships}
            self.assertIn(("第一章 引论", "1.1 基础", "contains"), triples)
            self.assertIn(("1.1 基础", "进程", "contains"), triples)

    def test_inject_metadata_contains_does_not_duplicate_existing_contains(self):
        with tempfile.TemporaryDirectory() as tmp:
            root = Path(tmp)
            eval_dir = root / "results" / "extraction_eval" / "runs" / "src"
            eval_dir.mkdir(parents=True)
            audit_path = root / "audit.json"

            audit_path.write_text(
                json.dumps(
                    {
                        "audit_samples": [
                            {
                                "source_sample_id": "s1",
                                "gold_entities": [
                                    {"entity_id": "g1", "name": "第一章 引论", "type": "Chapter", "alias": []},
                                    {"entity_id": "g2", "name": "1.1 基础", "type": "Section", "alias": []},
                                ],
                                "gold_relations": [],
                                "gold_seed": True,
                                "gold_seed_version": "manual_gold_seed_v1",
                            }
                        ]
                    },
                    ensure_ascii=False,
                ),
                encoding="utf-8",
            )
            eval_payload = {
                "candidate": "default",
                "results": [
                    {
                        "sample_id": "s1",
                        "candidate": "default",
                        "status": "success",
                        "entities": [
                            {"id": "e1", "title": "第一章 引论", "type": "Chapter", "alias": [],
                             "definition_text": "", "description": "", "evidence": ""},
                            {"id": "e2", "title": "1.1 基础", "type": "Section", "alias": [],
                             "definition_text": "", "description": "", "evidence": ""},
                            {"id": "e3", "title": "进程", "type": "Concept", "alias": [],
                             "definition_text": "", "description": "", "evidence": ""},
                        ],
                        "relationships": [
                            # 模型已经有 Chapter→Section 这条边；派生应跳过它。
                            {"source": "第一章 引论", "target": "1.1 基础", "type": "contains",
                             "description": "", "evidence": ""}
                        ],
                        "raw_output": "",
                        "error": None,
                        "parser_error_code": None,
                        "llm_debug": None,
                    }
                ],
            }
            (eval_dir / "default.json").write_text(
                json.dumps(eval_payload, ensure_ascii=False), encoding="utf-8"
            )

            summary = simulate_eval_dir(
                root=root,
                eval_dir=eval_dir,
                audit_path=audit_path,
                output_run_id="sim_demo_2",
                overwrite=True,
                inject_metadata_contains=True,
            )

            # 只需补 Section→Concept 这 1 条
            self.assertEqual(summary["total_injected_metadata_relationship_count"], 1)


if __name__ == "__main__":
    unittest.main()
