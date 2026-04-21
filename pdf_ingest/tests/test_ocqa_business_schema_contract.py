from pathlib import Path
import unittest


SQL_PATH = Path(__file__).resolve().parents[1] / "sql" / "ocqa.sql"


class TestOCQABusinessSchemaContract(unittest.TestCase):
    def setUp(self):
        self.text = SQL_PATH.read_text(encoding="utf-8")

    def test_business_tables_exist(self):
        for table_name in [
            "knowledge_bases",
            "kb_documents",
            "index_runs",
            "index_artifacts",
            "qa_sessions",
            "qa_messages",
            "qa_retrieval_logs",
            "qa_retrieval_hits",
            "authorization_audit_logs",
        ]:
            self.assertIn(f"CREATE TABLE `{table_name}`", self.text)

    def test_knowledge_base_active_index_exists(self):
        self.assertIn("`active_index_run_id` bigint NULL DEFAULT NULL", self.text)
        self.assertIn("CONSTRAINT `fk_knowledge_bases_active_index_run`", self.text)

    def test_session_and_retrieval_indexes_exist(self):
        self.assertIn("UNIQUE INDEX `uk_session_code`(`session_code` ASC)", self.text)
        self.assertIn("INDEX `idx_retrieval_course_created`(`course_id` ASC, `created_at` ASC)", self.text)

    def test_audit_decision_columns_exist(self):
        self.assertIn("`decision` enum('allow','deny')", self.text)
        self.assertIn("`decision_reason` varchar(128)", self.text)
