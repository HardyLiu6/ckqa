from pathlib import Path
import unittest


SQL_PATH = Path(__file__).resolve().parents[2] / "sql" / "ocqa.sql"


class TestOCQAAccessSchemaContract(unittest.TestCase):
    def setUp(self):
        self.text = SQL_PATH.read_text(encoding="utf-8")

    def test_access_tables_exist(self):
        for table_name in [
            "users",
            "roles",
            "permissions",
            "user_roles",
            "role_permissions",
            "auth_identities",
            "course_memberships",
            "course_membership_events",
        ]:
            self.assertIn(f"CREATE TABLE `{table_name}`", self.text)

    def test_courses_has_access_columns(self):
        self.assertIn("`status` enum('active','inactive','archived')", self.text)
        self.assertIn("`access_policy` enum('restricted','public')", self.text)

    def test_membership_and_identity_uniqueness_exist(self):
        self.assertIn("UNIQUE INDEX `uk_user_course`(`user_id` ASC, `course_id` ASC)", self.text)
        self.assertIn("UNIQUE INDEX `uk_provider_user`(`provider` ASC, `provider_user_id` ASC)", self.text)

    def test_membership_fk_targets_exist(self):
        self.assertIn("CONSTRAINT `fk_course_memberships_user`", self.text)
        self.assertIn("CONSTRAINT `fk_course_memberships_course`", self.text)
