import importlib.util
import sys
import unittest
from pathlib import Path


SCRIPT_PATH = Path(__file__).resolve().parents[1] / "run_student_local_history_smoke.py"
SPEC = importlib.util.spec_from_file_location("run_student_local_history_smoke", SCRIPT_PATH)
MODULE = importlib.util.module_from_spec(SPEC)
sys.modules[SPEC.name] = MODULE
SPEC.loader.exec_module(MODULE)


class StudentLocalHistorySmokeScriptTest(unittest.TestCase):
    def test_context_or_memory_accepts_recent_context_without_memory(self):
        submission = {"contextApplied": True, "contextStrategy": "recent", "memoryApplied": False}
        detail = {"contextApplied": True, "contextStrategy": "recent", "memoryApplied": False}

        self.assertTrue(MODULE.context_or_memory_applied(submission, detail))

    def test_context_or_memory_requires_memory_sources_when_memory_claimed(self):
        submission = {"memoryApplied": True, "memorySourceCount": 0}
        detail = {"memoryApplied": True, "memorySourceCount": 0}

        self.assertFalse(MODULE.context_or_memory_applied(submission, detail))

    def test_context_or_memory_rejects_empty_follow_up_context(self):
        submission = {"contextApplied": False, "contextStrategy": "none", "memoryApplied": False}
        detail = {"contextApplied": False, "contextStrategy": "none", "memoryApplied": False}

        self.assertFalse(MODULE.context_or_memory_applied(submission, detail))


if __name__ == "__main__":
    unittest.main()
