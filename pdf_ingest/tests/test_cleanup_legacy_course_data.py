from pathlib import Path
import sys
import unittest


SCRIPT_DIR = Path(__file__).resolve().parents[1] / "scripts"
sys.path.insert(0, str(SCRIPT_DIR))

from cleanup_legacy_course_data import (  # noqa: E402
    MaterialObjectRef,
    build_minio_delete_plan,
    choose_target_course_ids,
)


class CleanupLegacyCourseDataTest(unittest.TestCase):
    def test_choose_target_course_ids_detects_non_new_course_ids_by_default(self):
        courses = ["os", "ds", "crs-20260504-101010"]

        self.assertEqual(["os", "ds"], choose_target_course_ids(courses, []))

    def test_build_minio_delete_plan_includes_course_prefixes_and_orphan_raw_objects(self):
        refs = [
            MaterialObjectRef(
                material_object_id=7,
                bucket="course-pdfs",
                object_key="materials/abc.pdf",
            )
        ]

        plan = build_minio_delete_plan(
            course_ids=["os"],
            pdf_bucket="course-pdfs",
            artifacts_bucket="course-artifacts",
            orphan_material_objects=refs,
        )

        self.assertEqual(
            [
                ("course-artifacts", "os/"),
                ("course-pdfs", "os/"),
            ],
            [(target.bucket, target.prefix) for target in plan.prefixes],
        )
        self.assertEqual(
            [("course-pdfs", "materials/abc.pdf")],
            [(target.bucket, target.object_key) for target in plan.objects],
        )


if __name__ == "__main__":
    unittest.main()
