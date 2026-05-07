from pathlib import Path
from tempfile import TemporaryDirectory
import logging
import sys
import unittest


MODULE_DIR = Path(__file__).resolve().parents[1] / "scripts" / "pdf_processor"
sys.path.insert(0, str(MODULE_DIR))

from mineru_parser import PDFParserApp, ParseStatus  # noqa: E402


class StopAfterUploadName(Exception):
    pass


class RecordingDB:
    def __init__(self):
        self.statuses = []

    def update_parse_status(self, material_id, status, **kwargs):
        self.statuses.append((material_id, status, kwargs))

    def add_log(self, *args, **kwargs):
        pass


class RecordingStorage:
    def __init__(self):
        self.download_target = None

    def download_pdf_object(self, bucket, object_key, local_path):
        self.download_target = Path(local_path)
        self.download_target.parent.mkdir(parents=True, exist_ok=True)
        self.download_target.write_bytes(b"%PDF-1.7")


class RecordingMinerUParser:
    def __init__(self):
        self.upload_names = []
        self.upload_path = None

    def apply_upload_url(self, file_names):
        self.upload_names = list(file_names)
        return {"batch_id": "batch-1", "file_urls": ["https://upload.example/book.pdf"]}

    def upload_file(self, file_path, upload_url):
        self.upload_path = Path(file_path)
        raise StopAfterUploadName()


class TempConfig:
    def __init__(self, root):
        self.root = Path(root)

    def get_temp_path(self, *parts):
        return self.root.joinpath(*parts)


class MinerUParseUploadFileNameTest(unittest.TestCase):
    def test_parse_uses_original_pdf_name_for_mineru_when_display_name_has_no_suffix(self):
        with TemporaryDirectory() as tmp_dir:
            app = object.__new__(PDFParserApp)
            app.db = RecordingDB()
            app.storage = RecordingStorage()
            app.parser = RecordingMinerUParser()
            app.config = TempConfig(tmp_dir)
            app.logger = logging.getLogger("test")
            app._cleanup_parse_temp_files = lambda *args, **kwargs: None
            app._resolve_pdf_file = lambda *args, **kwargs: {
                "id": 9,
                "display_name": "联调-smoke-20260507161003-1.3.4 番外篇：中国古代的操作系统",
                "file_name": "联调-smoke-20260507161003-1.3.4 番外篇：中国古代的操作系统",
                "original_file_name": "1.3.4 番外篇：中国古代的操作系统.pdf",
                "parse_status": "failed",
                "minio_bucket": "course-artifacts",
                "minio_object_key": "course-materials/f8bc75.pdf",
            }

            with self.assertRaises(StopAfterUploadName):
                app.parse("crs-20260506-r4slkr", material_id=9)

            self.assertEqual(["1.3.4 番外篇：中国古代的操作系统.pdf"], app.parser.upload_names)
            self.assertEqual("1.3.4 番外篇：中国古代的操作系统.pdf", app.storage.download_target.name)
            self.assertEqual("1.3.4 番外篇：中国古代的操作系统.pdf", app.parser.upload_path.name)
            self.assertIn((9, ParseStatus.FAILED, {"error_msg": ""}), app.db.statuses)


if __name__ == "__main__":
    unittest.main()
