from pathlib import Path
import sys
import unittest


SCRIPT_DIR = Path(__file__).resolve().parents[1] / "scripts"
sys.path.insert(0, str(SCRIPT_DIR))

from repair_course_material_seed import (  # noqa: E402
    MinioObject,
    ParseResultRow,
    build_parse_result_specs,
    plan_parse_result_reconciliation,
    plan_prefix_repairs,
)


class RepairCourseMaterialSeedTest(unittest.TestCase):
    def test_build_parse_result_specs_uses_material_namespace_and_graphrag_file_prefix(self):
        objects = [
            MinioObject("os/material_3/content_list_v2.json", 12),
            MinioObject("os/graphrag/material_3/section_docs.json", 34),
        ]

        specs = build_parse_result_specs(
            course_id="os",
            material_id=3,
            artifacts_bucket="course-artifacts",
            objects=objects,
        )

        self.assertEqual(
            [
                ("content_list_v2.json", "content_list_json", "os/material_3/content_list_v2.json"),
                ("graphrag_section_docs.json", "other", "os/graphrag/material_3/section_docs.json"),
            ],
            [(spec.file_name, spec.result_type, spec.minio_object_key) for spec in specs],
        )

    def test_reconciliation_updates_existing_rows_and_deletes_duplicates_without_reinserting(self):
        expected = build_parse_result_specs(
            course_id="os",
            material_id=3,
            artifacts_bucket="course-artifacts",
            objects=[
                MinioObject("os/material_3/content_list_v2.json", 12),
                MinioObject("os/graphrag/material_3/section_docs.json", 34),
            ],
        )
        existing = [
            ParseResultRow(1, "os/material_3/content_list_v2.json", "content_list_v2.json", "other", 1),
            ParseResultRow(2, "os/material_3/content_list_v2.json", "content_list_v2.json", "other", 1),
            ParseResultRow(3, "os/graphrag/pdf_3/section_docs.json", "graphrag_section_docs.json", "other", 34),
        ]

        plan = plan_parse_result_reconciliation(
            existing=existing,
            expected=expected,
            managed_prefixes=[
                "os/material_3/",
                "os/graphrag/material_3/",
                "os/pdf_3/",
                "os/graphrag/pdf_3/",
            ],
        )

        self.assertEqual([], plan.to_insert)
        self.assertEqual([1, 3], [item.row_id for item in plan.to_update])
        self.assertEqual([2], plan.to_delete_ids)

    def test_prefix_repair_copies_legacy_objects_only_when_material_namespace_is_missing(self):
        objects = [
            MinioObject("os/pdf_3/a.json", 10),
            MinioObject("os/pdf_3/b.json", 20),
            MinioObject("os/material_3/b.json", 20),
        ]

        repairs = plan_prefix_repairs(
            objects=objects,
            legacy_prefix="os/pdf_3/",
            material_prefix="os/material_3/",
        )

        self.assertEqual([("os/pdf_3/a.json", "os/material_3/a.json")], repairs)


if __name__ == "__main__":
    unittest.main()
