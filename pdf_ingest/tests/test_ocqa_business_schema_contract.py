from pathlib import Path
import re
import unittest


SQL_PATH = Path(__file__).resolve().parents[2] / "sql" / "ocqa.sql"
MIGRATION_PATH = (
    Path(__file__).resolve().parents[2]
    / "sql"
    / "migrations"
    / "20260423_course_materials.sql"
)
SESSION_TYPE_MIGRATION_PATH = (
    Path(__file__).resolve().parents[2]
    / "sql"
    / "migrations"
    / "20260429_qa_session_type.sql"
)
PARSE_PROGRESS_MIGRATION_PATH = (
    Path(__file__).resolve().parents[2]
    / "sql"
    / "migrations"
    / "20260507_course_material_parse_progress.sql"
)


class TestOCQABusinessSchemaContract(unittest.TestCase):
    def setUp(self):
        self.text = SQL_PATH.read_text(encoding="utf-8")

    def _create_table_block(self, table_name: str) -> str:
        pattern = re.compile(
            rf"CREATE TABLE `{re.escape(table_name)}`.*?\n\) ENGINE = InnoDB",
            flags=re.DOTALL,
        )
        match = pattern.search(self.text)
        self.assertIsNotNone(match, f"Missing CREATE TABLE block for {table_name}")
        return match.group(0)

    def test_business_tables_exist(self):
        for table_name in [
            "material_objects",
            "course_materials",
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
        self.assertIn("`session_type` enum('formal','smoke')", self.text)
        self.assertIn("INDEX `idx_retrieval_course_created`(`course_id` ASC, `created_at` ASC)", self.text)

    def test_qa_session_type_migration_is_idempotent(self):
        migration = SESSION_TYPE_MIGRATION_PATH.read_text(encoding="utf-8")
        self.assertIn("CREATE PROCEDURE `ckqa_add_qa_session_type_if_missing`", migration)
        self.assertIn("TABLE_NAME = 'qa_sessions'", migration)
        self.assertIn("COLUMN_NAME = 'session_type'", migration)
        self.assertIn("ALTER TABLE `qa_sessions` ADD COLUMN `session_type` enum('formal','smoke')", migration)
        self.assertIn("DROP PROCEDURE IF EXISTS `ckqa_add_qa_session_type_if_missing`", migration)

    def test_audit_decision_columns_exist(self):
        self.assertIn("`decision` enum('allow','deny')", self.text)
        self.assertIn("`decision_reason` varchar(128)", self.text)

    def test_material_reuse_tables_exist_without_database_fks(self):
        self.assertIn("CREATE TABLE `material_objects`", self.text)
        self.assertIn("`file_md5` char(32)", self.text)
        self.assertIn("UNIQUE INDEX `uk_material_objects_md5`(`file_md5` ASC)", self.text)
        self.assertIn("INDEX `idx_material_objects_created_at`(`created_at` ASC)", self.text)

        self.assertIn("CREATE TABLE `course_materials`", self.text)
        self.assertIn("`material_object_id` bigint NOT NULL", self.text)
        self.assertIn(
            "UNIQUE INDEX `uk_course_material_object`(`course_id` ASC, `material_object_id` ASC)",
            self.text,
        )
        self.assertIn(
            "UNIQUE INDEX `uk_course_material_display_name`(`course_id` ASC, `display_name` ASC)",
            self.text,
        )
        self.assertIn(
            "INDEX `idx_course_materials_course_status`(`course_id` ASC, `parse_status` ASC)",
            self.text,
        )
        self.assertIn("`parse_progress_extracted_pages` int NULL DEFAULT NULL", self.text)
        self.assertIn("`parse_progress_total_pages` int NULL DEFAULT NULL", self.text)
        self.assertIn("`parse_progress_percent` tinyint unsigned NULL DEFAULT NULL", self.text)
        self.assertIn("`parse_progress_started_at` timestamp NULL DEFAULT NULL", self.text)
        self.assertIn("`parse_progress_updated_at` timestamp NULL DEFAULT NULL", self.text)
        self.assertIn("INDEX `idx_course_materials_upload_time`(`upload_time` ASC)", self.text)

        self.assertNotIn("CREATE TABLE `pdf_files`", self.text)
        self.assertNotIn("`uk_file_md5`", self.text)

        for fk_name in [
            "fk_course_materials_course",
            "fk_course_materials_material_object",
            "fk_parse_results_course_material",
            "fk_parse_logs_course_material",
        ]:
            self.assertNotIn(fk_name, self.text)

    def test_material_reuse_tables_do_not_define_database_foreign_keys(self):
        for table_name in [
            "material_objects",
            "course_materials",
            "parse_results",
            "parse_logs",
        ]:
            with self.subTest(table_name=table_name):
                table_block = self._create_table_block(table_name).upper()
                self.assertNotIn("FOREIGN KEY", table_block)
                self.assertNotIn("REFERENCES", table_block)

    def test_parse_artifacts_reference_course_materials_without_database_fks(self):
        self.assertIn(
            "`course_material_id` bigint NOT NULL COMMENT '关联的课程资料ID'",
            self.text,
        )
        self.assertIn("INDEX `idx_course_material_id`(`course_material_id` ASC)", self.text)
        self.assertNotIn("`pdf_file_id` bigint NOT NULL COMMENT '关联的PDF文件ID'", self.text)
        self.assertNotIn("INDEX `idx_pdf_file_id`", self.text)
        self.assertNotIn("fk_parse_results_course_material", self.text)
        self.assertNotIn("fk_parse_logs_course_material", self.text)

    def test_course_parse_overview_uses_material_reuse_tables_with_legacy_aliases(self):
        self.assertIn("VIEW `v_course_parse_overview`", self.text)
        self.assertIn("`cm`.`id` AS `course_material_id`", self.text)
        self.assertIn("`cm`.`id` AS `pdf_file_id`", self.text)
        self.assertIn("`cm`.`display_name` AS `display_name`", self.text)
        self.assertIn("`cm`.`display_name` AS `file_name`", self.text)
        self.assertIn("`cm`.`material_type` AS `material_type`", self.text)
        self.assertIn("`cm`.`parse_progress_percent` AS `parse_progress_percent`", self.text)
        self.assertIn("`cm`.`parse_progress_extracted_pages` AS `parse_progress_extracted_pages`", self.text)
        self.assertIn("`cm`.`parse_progress_total_pages` AS `parse_progress_total_pages`", self.text)
        self.assertIn("`material_objects` `mo`", self.text)
        self.assertIn("`course_materials` `cm`", self.text)
        self.assertIn("`pr`.`course_material_id` = `cm`.`id`", self.text)
        self.assertNotIn("`pdf_files` `pf`", self.text)

    def test_course_materials_migration_guard(self):
        migration = MIGRATION_PATH.read_text(encoding="utf-8")
        self.assertIn("CREATE PROCEDURE `ckqa_migrate_pdf_files_if_exists`", migration)
        self.assertIn("information_schema.TABLES", migration)
        self.assertIn("TABLE_NAME = 'pdf_files'", migration)
        self.assertIn("START TRANSACTION;", migration)
        self.assertIn("COMMIT;", migration)
        self.assertIn(
            "CALL `ckqa_drop_fk_if_exists`('parse_results', 'fk_parse_results_pdf_file');",
            migration,
        )
        self.assertIn(
            "CALL `ckqa_drop_fk_if_exists`('parse_logs', 'fk_parse_logs_pdf_file');",
            migration,
        )
        self.assertIn("idx_material_objects_created_at", migration)
        self.assertIn("idx_course_materials_course_status", migration)
        self.assertIn("idx_course_materials_upload_time", migration)
        self.assertIn("AUTO_INCREMENT = ", migration)
        self.assertIn("DROP TABLE IF EXISTS `pdf_files`", migration)
        self.assertIn("DROP PROCEDURE IF EXISTS `ckqa_migrate_pdf_files_if_exists`", migration)
        self.assertNotIn("ckqa_add_fk_if_missing", migration)
        for fk_name in [
            "fk_course_materials_course",
            "fk_course_materials_material_object",
            "fk_parse_results_course_material",
            "fk_parse_logs_course_material",
        ]:
            self.assertNotIn(fk_name, migration)

    def test_course_material_parse_progress_migration_is_idempotent(self):
        migration = PARSE_PROGRESS_MIGRATION_PATH.read_text(encoding="utf-8")
        for column in [
            "parse_progress_extracted_pages",
            "parse_progress_total_pages",
            "parse_progress_percent",
            "parse_progress_started_at",
            "parse_progress_updated_at",
        ]:
            self.assertIn(f"`{column}`", migration)
            self.assertIn(f"COLUMN_NAME = '{column}'", migration)
        self.assertIn("CREATE PROCEDURE `ckqa_add_course_material_parse_progress_if_missing`", migration)
        self.assertIn("DROP PROCEDURE IF EXISTS `ckqa_add_course_material_parse_progress_if_missing`", migration)
