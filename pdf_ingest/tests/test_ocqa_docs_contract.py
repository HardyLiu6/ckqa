from pathlib import Path
import unittest


README_PATH = Path(__file__).resolve().parents[1] / "README.md"
SPEC_PATH = (
    Path(__file__).resolve().parents[2]
    / "docs"
    / "superpowers"
    / "specs"
    / "2026-04-20-course-hybrid-qa-database-design.md"
)


class TestOCQADocsContract(unittest.TestCase):
    def test_readme_has_mysql_init_commands(self):
        text = README_PATH.read_text(encoding="utf-8")
        self.assertIn("mysql -h 127.0.0.1 -P 23306 -u root -p ocqa < sql/ocqa.sql", text)
        self.assertIn("SELECT role_code, role_name FROM roles ORDER BY id;", text)
        self.assertIn("SHOW TABLES LIKE 'course_memberships';", text)

    def test_spec_mentions_db_only_boundary(self):
        text = SPEC_PATH.read_text(encoding="utf-8")
        self.assertIn("当前实施边界说明", text)
        self.assertIn("本轮实施只覆盖 MySQL 数据库构建", text)
        self.assertIn("不包含 FastAPI、Spring Boot 或其他后端服务实现", text)
