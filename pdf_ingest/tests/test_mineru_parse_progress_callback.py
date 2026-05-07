from pathlib import Path
from types import SimpleNamespace
import sys
import unittest


MODULE_DIR = Path(__file__).resolve().parents[1] / "scripts" / "pdf_processor"
sys.path.insert(0, str(MODULE_DIR))

import mineru_parser  # noqa: E402
from mineru_parser import MinerUParser  # noqa: E402


class FakeResponse:
    def __init__(self, payload):
        self.status_code = 200
        self._payload = payload

    def json(self):
        return self._payload


class MinerUParseProgressCallbackTest(unittest.TestCase):
    def test_get_batch_results_reports_running_extract_progress(self):
        parser = MinerUParser(SimpleNamespace(
            api_token="token",
            api_base_url="https://mineru.example/api/v4",
            timeout=30,
            poll_interval=9,
            progress_poll_interval=1,
        ))
        progress_events = []
        calls = []
        sleeps = []

        payloads = [
            {
                "code": 0,
                "data": {
                    "extract_result": [{
                        "state": "running",
                        "extract_progress": {
                            "extracted_pages": 2,
                            "total_pages": 4,
                            "start_time": "2026-05-07 10:20:00",
                        },
                    }],
                },
            },
            {
                "code": 0,
                "data": {
                    "extract_result": [{
                        "state": "done",
                        "file_name": "book.pdf",
                    }],
                },
            },
        ]

        def fake_get(url, headers=None):
            calls.append((url, headers))
            return FakeResponse(payloads.pop(0))

        original_get = mineru_parser.requests.get
        original_sleep = mineru_parser.time.sleep
        try:
            mineru_parser.requests.get = fake_get
            mineru_parser.time.sleep = sleeps.append
            result = parser.get_batch_results("batch-1", on_progress=progress_events.append)
        finally:
            mineru_parser.requests.get = original_get
            mineru_parser.time.sleep = original_sleep

        self.assertEqual("book.pdf", result["extract_result"][0]["file_name"])
        self.assertEqual(2, len(calls))
        self.assertEqual([1], sleeps)
        self.assertEqual([
            {
                "extracted_pages": 2,
                "total_pages": 4,
                "start_time": "2026-05-07 10:20:00",
            },
        ], progress_events)


if __name__ == "__main__":
    unittest.main()
