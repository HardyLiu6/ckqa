from pathlib import Path
import unittest


SQL_PATH = Path(__file__).resolve().parents[1] / "sql" / "ocqa.sql"


class TestOCQABootstrapContract(unittest.TestCase):
    def setUp(self):
        self.text = SQL_PATH.read_text(encoding="utf-8")

    def test_foreign_key_checks_wrapped(self):
        self.assertIn("SET FOREIGN_KEY_CHECKS = 0;", self.text)
        self.assertIn("SET FOREIGN_KEY_CHECKS = 1;", self.text)

    def test_roles_seed_exists(self):
        self.assertIn("INSERT INTO `roles` (`role_code`, `role_name`, `description`)", self.text)
        self.assertIn("('user', '普通用户', '默认平台用户')", self.text)
        self.assertIn("('admin', '平台管理员', '拥有跨课程访问能力')", self.text)

    def test_permissions_seed_exists(self):
        self.assertIn("INSERT INTO `permissions` (`permission_code`, `permission_name`, `description`)", self.text)
        self.assertIn("('course.query', '课程问答访问', '允许访问课程问答')", self.text)
        self.assertIn("('system.admin_override', '管理员越权访问', '允许管理员绕过课程成员限制')", self.text)
