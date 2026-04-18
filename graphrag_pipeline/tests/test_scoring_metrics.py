from __future__ import annotations

import sys
import unittest
from pathlib import Path

_PROJECT_ROOT = Path(__file__).resolve().parent.parent
_SCRIPTS_DIR = _PROJECT_ROOT / "scripts"
if str(_SCRIPTS_DIR) not in sys.path:
    sys.path.insert(0, str(_SCRIPTS_DIR))

from extraction_schema import StructuredExtractionResult
from scoring_metrics import compute_parse_success_rate


def _result(sample_id: str, status: str) -> StructuredExtractionResult:
    return StructuredExtractionResult(
        sample_id=sample_id,
        candidate="c",
        status=status,
        entities=[],
        relationships=[],
        raw_output="",
    )


class TestParseSuccessRate(unittest.TestCase):
    def test_all_success(self):
        results = [_result("s1", "success"), _result("s2", "success")]
        self.assertEqual(compute_parse_success_rate(results), 1.0)

    def test_mixed(self):
        results = [
            _result("s1", "success"),
            _result("s2", "parse_error"),
            _result("s3", "llm_error"),
            _result("s4", "success"),
        ]
        self.assertEqual(compute_parse_success_rate(results), 0.5)

    def test_empty(self):
        self.assertEqual(compute_parse_success_rate([]), 0.0)


if __name__ == "__main__":
    unittest.main()
