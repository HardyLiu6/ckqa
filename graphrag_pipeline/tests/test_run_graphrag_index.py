from __future__ import annotations

import json
import subprocess
import sys
import tempfile
import unittest
from contextlib import redirect_stdout
from io import StringIO
from pathlib import Path


_PROJECT_ROOT = Path(__file__).resolve().parent.parent
_SCRIPTS_DIR = _PROJECT_ROOT / "scripts"
if str(_SCRIPTS_DIR) not in sys.path:
    sys.path.insert(0, str(_SCRIPTS_DIR))

from run_graphrag_index import run_graphrag_index


class TestRunGraphragIndex(unittest.TestCase):
    def test_default_command_does_not_include_no_cache(self):
        with tempfile.TemporaryDirectory() as tmp:
            root = Path(tmp)
            executed: list[list[str]] = []

            def fake_runner(command, cwd):
                executed.append(list(command))
                return subprocess.CompletedProcess(command, 0)

            exit_code = run_graphrag_index(["--root", str(root)], runner=fake_runner)

            self.assertEqual(exit_code, 0)
            self.assertEqual(executed, [["graphrag", "index", "--root", str(root.resolve())]])
            self.assertNotIn("--no-cache", executed[0])

    def test_no_cache_is_rejected_by_default_and_runner_is_not_called(self):
        with tempfile.TemporaryDirectory() as tmp:
            executed: list[list[str]] = []

            def fake_runner(command, cwd):
                executed.append(list(command))
                return subprocess.CompletedProcess(command, 0)

            exit_code = run_graphrag_index(
                ["--root", tmp, "--", "--no-cache"],
                runner=fake_runner,
            )

            self.assertEqual(exit_code, 2)
            self.assertEqual(executed, [])

    def test_allow_no_cache_passes_extra_argument_to_runner(self):
        with tempfile.TemporaryDirectory() as tmp:
            root = Path(tmp)
            executed: list[list[str]] = []

            def fake_runner(command, cwd):
                executed.append(list(command))
                return subprocess.CompletedProcess(command, 0)

            exit_code = run_graphrag_index(
                ["--root", str(root), "--allow-no-cache", "--", "--no-cache"],
                runner=fake_runner,
            )

            self.assertEqual(exit_code, 0)
            self.assertEqual(
                executed,
                [["graphrag", "index", "--root", str(root.resolve()), "--no-cache"]],
            )

    def test_preflight_counts_extract_graph_cache_files(self):
        with tempfile.TemporaryDirectory() as tmp:
            root = Path(tmp)
            cache_dir = root / "cache" / "extract_graph"
            cache_dir.mkdir(parents=True)
            (cache_dir / "a.json").write_text("{}", encoding="utf-8")
            (cache_dir / "b.json").write_text("{}", encoding="utf-8")
            (root / "output").mkdir()
            (root / "output" / "nodes.parquet").write_text("", encoding="utf-8")
            (root / "output" / "lancedb").mkdir()

            def fake_runner(command, cwd):
                return subprocess.CompletedProcess(command, 0)

            stdout = StringIO()
            with redirect_stdout(stdout):
                exit_code = run_graphrag_index(["--root", str(root)], runner=fake_runner)

            self.assertEqual(exit_code, 0)
            summary = json.loads(stdout.getvalue().splitlines()[0])
            self.assertEqual(summary["cache_dir"], str((root / "cache").resolve()))
            self.assertEqual(summary["extract_graph_cache_count"], 2)
            self.assertEqual(summary["output_dir"], str((root / "output").resolve()))
            self.assertEqual(summary["existing_output_files"], ["lancedb", "nodes.parquet"])

    def test_dry_run_writes_index_report_without_executing(self):
        with tempfile.TemporaryDirectory() as tmp:
            root = Path(tmp)
            active_prompt = root / "prompts" / "final" / "active_prompt.json"
            active_prompt.parent.mkdir(parents=True)
            active_prompt.write_text(
                json.dumps({"candidate": "schema_aware", "run_id": "material_7_full"}, ensure_ascii=False),
                encoding="utf-8",
            )
            output_dir = root / "output"
            output_dir.mkdir()
            (output_dir / "stats.json").write_text(
                json.dumps(
                    {
                        "num_documents": 3,
                        "total_runtime": 12.5,
                        "workflows": {"extract_graph": {"overall": 8.0}},
                    },
                    ensure_ascii=False,
                ),
                encoding="utf-8",
            )
            (output_dir / "documents.parquet").write_text("", encoding="utf-8")
            (output_dir / "lancedb").mkdir()
            report_file = root / "results" / "reports" / "index_runs" / "idx-001.json"
            executed: list[list[str]] = []

            def fake_runner(command, cwd):
                executed.append(list(command))
                return subprocess.CompletedProcess(command, 0)

            exit_code = run_graphrag_index(
                [
                    "--root",
                    str(root),
                    "--run-id",
                    "idx-001",
                    "--report-file",
                    str(report_file),
                    "--dry-run",
                ],
                runner=fake_runner,
            )

            self.assertEqual(exit_code, 0)
            self.assertEqual(executed, [])
            report = json.loads(report_file.read_text(encoding="utf-8"))
            self.assertEqual(report["run_id"], "idx-001")
            self.assertTrue(report["dry_run"])
            self.assertEqual(report["returncode"], 0)
            self.assertEqual(report["command"], ["graphrag", "index", "--root", str(root.resolve())])
            self.assertEqual(report["preflight"]["root"], str(root.resolve()))
            self.assertTrue(report["active_prompt_snapshot"]["exists"])
            self.assertEqual(report["active_prompt_snapshot"]["payload"]["candidate"], "schema_aware")
            self.assertEqual(report["output_stats"]["summary"]["num_documents"], 3)
            self.assertEqual(report["output_stats"]["summary"]["workflow_count"], 1)
            self.assertTrue(report["output_artifacts"]["documents.parquet"]["exists"])
            self.assertTrue(report["output_artifacts"]["lancedb"]["exists"])
            self.assertFalse(report["output_artifacts"]["entities.parquet"]["exists"])

    def test_index_report_records_runner_returncode(self):
        with tempfile.TemporaryDirectory() as tmp:
            root = Path(tmp)
            report_file = root / "idx-failed.json"
            executed: list[list[str]] = []

            def fake_runner(command, cwd):
                executed.append(list(command))
                return subprocess.CompletedProcess(command, 9)

            exit_code = run_graphrag_index(
                [
                    "--root",
                    str(root),
                    "--run-id",
                    "idx-failed",
                    "--report-file",
                    str(report_file),
                ],
                runner=fake_runner,
            )

            self.assertEqual(exit_code, 9)
            self.assertEqual(executed, [["graphrag", "index", "--root", str(root.resolve())]])
            report = json.loads(report_file.read_text(encoding="utf-8"))
            self.assertEqual(report["run_id"], "idx-failed")
            self.assertEqual(report["returncode"], 9)
            self.assertFalse(report["dry_run"])
            self.assertEqual(report["command"], executed[0])


if __name__ == "__main__":
    unittest.main()
