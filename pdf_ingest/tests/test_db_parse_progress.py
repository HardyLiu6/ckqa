from contextlib import contextmanager
from pathlib import Path
import sys
import unittest


MODULE_DIR = Path(__file__).resolve().parents[1] / "scripts" / "pdf_processor"
sys.path.insert(0, str(MODULE_DIR))

from db_service import DatabaseService  # noqa: E402


class FakeCursor:
    def __init__(self):
        self.calls = []

    def execute(self, sql, params=None):
        self.calls.append((sql, params))


class FakeConnection:
    def __init__(self):
        self.cursor_instance = FakeCursor()
        self.committed = False
        self.closed = False

    def cursor(self):
        return self.cursor_instance

    def commit(self):
        self.committed = True

    def rollback(self):
        raise AssertionError("rollback should not be called")

    def close(self):
        self.closed = True


class ParseProgressDatabaseService(DatabaseService):
    def __init__(self, connection):
        self.connection = connection

    @contextmanager
    def get_connection(self):
        yield self.connection
        self.connection.commit()
        self.connection.close()


class DatabaseParseProgressTest(unittest.TestCase):
    def test_update_parse_progress_persists_mineru_page_progress(self):
        connection = FakeConnection()
        service = ParseProgressDatabaseService(connection)

        service.update_parse_progress(
            7,
            {
                "extracted_pages": 3,
                "total_pages": 5,
                "start_time": "2026-05-07 10:20:00",
            },
        )

        self.assertTrue(connection.committed)
        sql, params = connection.cursor_instance.calls[0]
        self.assertIn("parse_progress_extracted_pages = %s", sql)
        self.assertIn("parse_progress_total_pages = %s", sql)
        self.assertIn("parse_progress_percent = %s", sql)
        self.assertIn("parse_progress_started_at = %s", sql)
        self.assertIn("parse_progress_updated_at = NOW()", sql)
        self.assertEqual((3, 5, 60, "2026-05-07 10:20:00", 7), params)

    def test_update_parse_progress_ignores_incomplete_progress_payload(self):
        connection = FakeConnection()
        service = ParseProgressDatabaseService(connection)

        service.update_parse_progress(7, {"extracted_pages": 1})

        self.assertEqual([], connection.cursor_instance.calls)


if __name__ == "__main__":
    unittest.main()
