from pathlib import Path
import re
import unittest


SQL_PATH = Path(__file__).resolve().parents[2] / "sql" / "ocqa.sql"
MIGRATION_PATH = (
    Path(__file__).resolve().parents[2]
    / "sql"
    / "migrations"
    / "20260504_role_user_test_data.sql"
)


class TestOCQABootstrapContract(unittest.TestCase):
    def setUp(self):
        self.text = SQL_PATH.read_text(encoding="utf-8")

    def test_foreign_key_checks_wrapped(self):
        self.assertIn("SET FOREIGN_KEY_CHECKS = 0;", self.text)
        self.assertIn("SET FOREIGN_KEY_CHECKS = 1;", self.text)

    def test_roles_seed_exists(self):
        self.assertIn("INSERT INTO `roles` (`role_code`, `role_name`, `description`)", self.text)
        self.assertIn("('student', '学生', '课程学习、课程问答与个人课程数据访问角色')", self.text)
        self.assertIn("('teacher', '教师', '课程资料、知识库、索引与问答运行管理角色')", self.text)
        self.assertIn("('admin', '系统管理员', '用户、角色、权限与系统配置管理角色')", self.text)
        self.assertNotIn("('user', '普通用户', '默认平台用户')", self.text)

    def test_permissions_seed_exists(self):
        self.assertIn("INSERT INTO `permissions` (`permission_code`, `permission_name`, `description`)", self.text)
        self.assertIn("('course.query', '课程问答访问', '允许访问课程问答')", self.text)
        self.assertIn("('system.admin_override', '管理员越权访问', '允许管理员绕过课程成员限制')", self.text)

    def test_realistic_user_seed_exists(self):
        self.assertIn("INSERT INTO `users` (`user_code`, `username`, `display_name`, `password_hash`, `status`, `extra_metadata`)", self.text)
        self.assertEqual(5, len(re.findall(r"\('STU2026\d{3}',", self.text)))
        self.assertEqual(5, len(re.findall(r"\('TCH2026\d{3}',", self.text)))
        self.assertEqual(5, len(re.findall(r"\('ADM2026\d{3}',", self.text)))
        self.assertIn("('STU2026001', 'student.zhouzh', '周子涵', '', 'active'", self.text)
        self.assertIn("('TCH2026001', 'teacher.zhangwb', '张文博', '', 'active'", self.text)
        self.assertIn("('ADM2026001', 'admin.heqh', '何启航', '', 'active'", self.text)

    def test_user_role_seed_exists(self):
        self.assertIn("INSERT INTO `user_roles` (`user_id`, `role_id`)", self.text)
        self.assertIn("u.`user_code` BETWEEN 'STU2026001' AND 'STU2026005'", self.text)
        self.assertIn("u.`user_code` BETWEEN 'TCH2026001' AND 'TCH2026005'", self.text)
        self.assertIn("u.`user_code` BETWEEN 'ADM2026001' AND 'ADM2026005'", self.text)

    def test_role_user_seed_migration_exists(self):
        migration = MIGRATION_PATH.read_text(encoding="utf-8")
        self.assertIn("CKQA role and user test-data migration", migration)
        self.assertIn("DELETE FROM `roles`", migration)
        self.assertIn("WHERE `role_code` = 'user'", migration)
        self.assertIn("('student', '学生', '课程学习、课程问答与个人课程数据访问角色')", migration)
        self.assertIn("('teacher', '教师', '课程资料、知识库、索引与问答运行管理角色')", migration)
        self.assertIn("('admin', '系统管理员', '用户、角色、权限与系统配置管理角色')", migration)
        self.assertEqual(5, len(re.findall(r"\('STU2026\d{3}',", migration)))
        self.assertEqual(5, len(re.findall(r"\('TCH2026\d{3}',", migration)))
        self.assertEqual(5, len(re.findall(r"\('ADM2026\d{3}',", migration)))
