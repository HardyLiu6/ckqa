import importlib.util
import os
import sys
import tempfile
import unittest
from pathlib import Path


SCRIPT_PATH = Path(__file__).resolve().parents[1] / "run_qa_routing_smoke_matrix.py"
SPEC = importlib.util.spec_from_file_location("run_qa_routing_smoke_matrix", SCRIPT_PATH)
MODULE = importlib.util.module_from_spec(SPEC)
sys.modules[SPEC.name] = MODULE
SPEC.loader.exec_module(MODULE)


class QaRoutingSmokeMatrixScriptTest(unittest.TestCase):
    def test_normalize_base_url_appends_api_prefix_once(self):
        self.assertEqual(
            MODULE.normalize_base_url("http://127.0.0.1:18081"),
            "http://127.0.0.1:18081/api/v1",
        )
        self.assertEqual(
            MODULE.normalize_base_url("http://127.0.0.1:18081/api/v1/"),
            "http://127.0.0.1:18081/api/v1",
        )

    def test_load_env_file_keeps_existing_environment(self):
        with tempfile.TemporaryDirectory() as directory:
            path = Path(directory) / ".env"
            path.write_text("CKQA_SCRIPT_TEST_VALUE=from-file\n", encoding="utf-8")
            os.environ["CKQA_SCRIPT_TEST_VALUE"] = "from-env"
            try:
                MODULE.load_env_file(str(path))
                self.assertEqual(os.environ["CKQA_SCRIPT_TEST_VALUE"], "from-env")
            finally:
                os.environ.pop("CKQA_SCRIPT_TEST_VALUE", None)

    def test_default_matrix_contains_hybrid_gated_case(self):
        cases = MODULE.load_matrix(None, None)
        ids = {item["id"] for item in cases}
        self.assertIn("hybrid-beta", ids)
        self.assertIn("hybrid-gated-fallback", ids)

    def test_execute_qa_requires_explicit_external_call_acknowledgement(self):
        with self.assertRaises(SystemExit) as raised:
            MODULE.main(["--execute-qa"])
        self.assertIn("configured model provider", str(raised.exception))


if __name__ == "__main__":
    unittest.main()
