from pathlib import Path
import sys
import unittest


MODULE_DIR = Path(__file__).resolve().parents[1] / "scripts" / "pdf_processor"
sys.path.insert(0, str(MODULE_DIR))

from mineru_parser import PDFParserApp, ParseStatus  # noqa: E402


class StopAfterClaim(Exception):
    pass


class ClaimingDB:
    def update_parse_status(self, material_id, status, **kwargs):
        if status == ParseStatus.PROCESSING:
            raise StopAfterClaim()

    def add_log(self, *args, **kwargs):
        pass


class MinerUParseProcessingClaimTest(unittest.TestCase):
    def make_app(self):
        app = object.__new__(PDFParserApp)
        app.db = ClaimingDB()
        app._resolve_pdf_file = lambda *args, **kwargs: {
            "id": 8,
            "display_name": "book.pdf",
            "file_name": "book.pdf",
            "parse_status": "processing",
        }
        return app

    def test_parse_rejects_processing_material_by_default(self):
        app = self.make_app()

        with self.assertRaisesRegex(Exception, "文件正在解析中"):
            app.parse("os", material_id=8)

    def test_parse_allows_java_claimed_processing_material(self):
        app = self.make_app()

        with self.assertRaises(StopAfterClaim):
            app.parse("os", material_id=8, allow_claimed_processing=True)


if __name__ == "__main__":
    unittest.main()
