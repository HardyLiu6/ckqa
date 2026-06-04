import subprocess
import tempfile
import unittest
from pathlib import Path


SCRIPT_PATH = Path(__file__).resolve().parents[1] / "run_local_backend.sh"


class RunLocalBackendScriptTest(unittest.TestCase):
    def test_dry_run_loads_crlf_env_values_with_spaces(self):
        with tempfile.TemporaryDirectory() as tmp_dir:
            env_file = Path(tmp_dir) / ".env"
            env_file.write_text(
                "\r\n".join(
                    [
                        "CKQA_EMAIL_MAILER_TYPE=smtp",
                        "QUERY_TASK_TIMEOUT_MESSAGE_LOCAL=local 模式正在结合课程上下文生成回答",
                        "GRAPHRAG_API_BASE_URL=http://127.0.0.1:8012",
                    ]
                )
                + "\r\n",
                encoding="utf-8",
            )

            result = subprocess.run(
                [
                    str(SCRIPT_PATH),
                    "--env-file",
                    str(env_file),
                    "--port",
                    "18081",
                    "--mailer-type",
                    "log",
                    "--dry-run",
                ],
                check=False,
                text=True,
                stdout=subprocess.PIPE,
                stderr=subprocess.PIPE,
            )

        self.assertEqual(result.returncode, 0, result.stderr)
        self.assertIn("SERVER_PORT=18081", result.stdout)
        self.assertIn("CKQA_EMAIL_MAILER_TYPE=log", result.stdout)
        self.assertIn("GRAPHRAG_API_BASE_URL=http://127.0.0.1:8012", result.stdout)


if __name__ == "__main__":
    unittest.main()
