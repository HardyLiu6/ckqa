from pathlib import Path
import sys
import tempfile
import unittest


MODULE_DIR = Path(__file__).resolve().parents[1] / "scripts" / "pdf_processor"
sys.path.insert(0, str(MODULE_DIR))

from graphrag_exporter import GraphRAGExporter  # noqa: E402


class FakeStorage:
    def __init__(self):
        self.uploads = []

    def upload_artifact(self, course_id: str, local_path: str, relative_path: str) -> dict:
        self.uploads.append((course_id, relative_path))
        return {
            "bucket": "course-artifacts",
            "object_key": f"{course_id}/{relative_path}",
            "size": Path(local_path).stat().st_size,
        }


class FakeDB:
    def __init__(self):
        self.rows = []

    def create_parse_result(self, **kwargs):
        self.rows.append(kwargs)
        return len(self.rows)


class GraphRAGExporterPathTest(unittest.TestCase):
    def test_persist_output_uses_material_namespace(self):
        storage = FakeStorage()
        db = FakeDB()
        exporter = GraphRAGExporter(db=db, storage=storage, config=None)
        output_files = []

        with tempfile.TemporaryDirectory() as tmp:
            exporter._persist_output_file(
                docs=[{"title": "one"}],
                serializer=lambda docs, path: path.write_text("[]", encoding="utf-8"),
                tmp_dir=Path(tmp),
                course_id="os",
                file_id=7,
                out_name="section_docs.json",
                output_files=output_files,
                mode="section",
                force=False,
                output_prefix="graphrag",
            )

        self.assertEqual([("os", "graphrag/material_7/section_docs.json")], storage.uploads)
        self.assertEqual("os/graphrag/material_7/section_docs.json", db.rows[0]["minio_object_key"])
        self.assertEqual("graphrag_section_docs.json", output_files[0]["file_name"])


if __name__ == "__main__":
    unittest.main()
