from pathlib import Path
import sys
import tempfile
import unittest


MODULE_DIR = Path(__file__).resolve().parents[1] / "scripts" / "pdf_processor"
sys.path.insert(0, str(MODULE_DIR))

from mineru_parser import PDFParserApp  # noqa: E402


class FakeStorage:
    def __init__(self):
        self.uploads = []
        self.deleted_artifacts = []

    @staticmethod
    def calculate_md5(file_path: str) -> str:
        return "same-md5"

    def upload_material_object(self, file_path: str, file_md5: str, file_name: str) -> dict:
        self.uploads.append((file_path, file_md5, file_name))
        return {
            "bucket": "course-pdfs",
            "object_key": f"materials/{file_md5}.pdf",
            "md5": file_md5,
            "size": Path(file_path).stat().st_size,
        }

    def delete_artifacts(self, course_id: str, relative_prefix: str = ""):
        self.deleted_artifacts.append((course_id, relative_prefix))


class FakeDB:
    def __init__(self):
        self.material_objects = {}
        self.course_materials = {}
        self.logs = []
        self.next_object_id = 1
        self.next_material_id = 1

    def create_course(self, course_id, course_name=None, description=None):
        return 1

    def get_material_object_by_md5(self, file_md5):
        return self.material_objects.get(file_md5)

    def check_md5_exists(self, file_md5):
        return self.get_material_object_by_md5(file_md5)

    def create_material_object(self, original_file_name, file_md5, file_size, minio_bucket, minio_object_key, mime_type="application/pdf"):
        material_object = {
            "id": self.next_object_id,
            "original_file_name": original_file_name,
            "file_md5": file_md5,
            "file_size": file_size,
            "minio_bucket": minio_bucket,
            "minio_object_key": minio_object_key,
            "mime_type": mime_type,
        }
        self.material_objects[file_md5] = material_object
        self.next_object_id += 1
        return material_object["id"]

    def get_course_material_by_object(self, course_id, material_object_id):
        return self.course_materials.get((course_id, material_object_id))

    def get_course_material_by_course(self, course_id, display_name=None):
        if display_name is None:
            matches = [row for (cid, _), row in self.course_materials.items() if cid == course_id]
            return matches[-1] if matches else None
        for row in self.course_materials.values():
            if row["course_id"] == course_id and row["display_name"] == display_name:
                return row
        return None

    def get_course_materials_by_course(self, course_id):
        return [row for (cid, _), row in self.course_materials.items() if cid == course_id]

    def create_course_material(self, course_id, material_object_id, display_name, material_type="textbook"):
        row = {
            "id": self.next_material_id,
            "course_id": course_id,
            "material_object_id": material_object_id,
            "display_name": display_name,
            "file_name": display_name,
            "file_md5": "same-md5",
            "file_size": 4,
            "minio_bucket": "course-pdfs",
            "minio_object_key": "materials/same-md5.pdf",
            "parse_status": "pending",
            "material_type": material_type,
        }
        self.course_materials[(course_id, material_object_id)] = row
        self.next_material_id += 1
        return row["id"]

    def delete_course_material(self, course_material_id):
        for key, row in list(self.course_materials.items()):
            if row["id"] == course_material_id:
                del self.course_materials[key]

    def add_log(self, course_material_id, message, level="info"):
        self.logs.append((course_material_id, level, message))


class CourseMaterialReuseTest(unittest.TestCase):
    def make_app(self):
        app = object.__new__(PDFParserApp)
        app.storage = FakeStorage()
        app.db = FakeDB()
        app.logger = type("Logger", (), {"info": lambda *args, **kwargs: None, "warning": lambda *args, **kwargs: None})()
        return app

    def write_pdf(self):
        temp_dir = tempfile.TemporaryDirectory()
        path = Path(temp_dir.name) / "book.pdf"
        path.write_bytes(b"%PDF")
        return temp_dir, path

    def test_same_material_can_be_reused_by_different_courses(self):
        app = self.make_app()
        temp_dir, path = self.write_pdf()
        self.addCleanup(temp_dir.cleanup)

        first = app.upload("os", str(path))
        second = app.upload("java", str(path))

        self.assertEqual("success", first["status"])
        self.assertEqual("success", second["status"])
        self.assertEqual(1, first["material_object_id"])
        self.assertEqual(1, second["material_object_id"])
        self.assertNotEqual(first["course_material_id"], second["course_material_id"])
        self.assertEqual(1, len(app.storage.uploads))

    def test_upload_result_keeps_legacy_file_fields(self):
        app = self.make_app()
        temp_dir, path = self.write_pdf()
        self.addCleanup(temp_dir.cleanup)

        result = app.upload("os", str(path))

        self.assertEqual("success", result["status"])
        self.assertEqual(result["course_material_id"], result["file_id"])
        self.assertEqual(1, result["course_material_id"])
        self.assertEqual("book.pdf", result["display_name"])
        self.assertEqual("book.pdf", result["file_name"])

    def test_same_course_duplicate_returns_existing_relation_without_force(self):
        app = self.make_app()
        temp_dir, path = self.write_pdf()
        self.addCleanup(temp_dir.cleanup)

        first = app.upload("os", str(path))
        second = app.upload("os", str(path))

        self.assertEqual("success", first["status"])
        self.assertEqual("duplicate", second["status"])
        self.assertEqual(first["course_material_id"], second["course_material_id"])
        self.assertEqual(1, len(app.storage.uploads))

    def test_force_replaces_only_current_course_material_relation(self):
        app = self.make_app()
        temp_dir, path = self.write_pdf()
        self.addCleanup(temp_dir.cleanup)

        first = app.upload("os", str(path))
        app.upload("java", str(path))
        forced = app.upload("os", str(path), force=True)

        self.assertEqual("success", forced["status"])
        self.assertEqual(first["material_object_id"], forced["material_object_id"])
        self.assertNotEqual(first["course_material_id"], forced["course_material_id"])
        self.assertIn(("os", f"pdf_{first['course_material_id']}"), app.storage.deleted_artifacts)
        self.assertIn(("os", f"graphrag/pdf_{first['course_material_id']}"), app.storage.deleted_artifacts)
        self.assertIn(("os", f"material_{first['course_material_id']}"), app.storage.deleted_artifacts)
        self.assertIn(("os", f"graphrag/material_{first['course_material_id']}"), app.storage.deleted_artifacts)
        self.assertEqual(1, len(app.storage.uploads))


if __name__ == "__main__":
    unittest.main()
