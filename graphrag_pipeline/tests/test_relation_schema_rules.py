from __future__ import annotations

import json
import unittest
from pathlib import Path


PROJECT_ROOT = Path(__file__).resolve().parent.parent


class TestRelationSchemaRules(unittest.TestCase):
    def test_material_7_schema_keeps_hard_relation_boundaries(self):
        payload = json.loads(
            (PROJECT_ROOT / "config" / "schema" / "relation_types.json").read_text(
                encoding="utf-8"
            )
        )
        relation_types = payload["relation_types"]

        contains = relation_types["contains"]
        self.assertIn("KnowledgePoint", contains["source_types"])
        self.assertIn("Concept", contains["source_types"])
        self.assertIn("AlgorithmOrMethod", contains["source_types"])
        self.assertEqual(
            contains["derivation_constraints"]["derive_inverse_only_when_source_types"],
            ["Course", "Chapter", "Section"],
        )

        self.assertEqual(
            relation_types["belongs_to"]["target_types"],
            ["Course", "Chapter", "Section"],
        )
        self.assertEqual(
            relation_types["appears_in"]["target_types"],
            ["Course", "Chapter", "Section", "Experiment", "Assignment"],
        )
        self.assertIn("Term", relation_types["defined_by"]["target_types"])

    def test_extraction_rules_document_defined_by_alias_and_contains_boundaries(self):
        text = (PROJECT_ROOT / "config" / "schema" / "extraction_rules.md").read_text(
            encoding="utf-8"
        )
        self.assertIn("知识对象之间的 `contains`", text)
        self.assertIn("不派生反向 `belongs_to`", text)
        self.assertIn("PCB defined_by Process Control Block", text)
        self.assertIn("alias", text)


if __name__ == "__main__":
    unittest.main()
