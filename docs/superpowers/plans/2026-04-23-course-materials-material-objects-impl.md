# Course Materials And Material Objects Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 将课程资料接入模型从 `courses -> pdf_files` 演进为 `courses <-> course_materials <-> material_objects`，支持同一份教材资料被多门课程复用，同时保持现有解析、导出、Java 编排和 GraphRAG 下游能力完整。

**Architecture:** `material_objects` 存储全局唯一的物理资料对象，按 `file_md5` 去重并指向 MinIO 原始文件；`course_materials` 存储课程与资料对象的关联、课程内展示名、资料类型、解析状态和 MinerU 批次信息。解析结果、日志和 GraphRAG 导出以 `course_material_id` 隔离，CLI 与 Java 旧 `pdf-files` 入口保留兼容别名，避免一次性打断现有调用方。

**Tech Stack:** MySQL 8, Python 3.10+, PyMySQL, DBUtils, MinIO Python SDK, pytest/unittest, Java 21, Spring Boot 4.0.5, MyBatis-Plus 3.5.16, Maven Wrapper.

---

## Review Fixes Incorporated

- Live migration must be defensive around existing foreign keys: old `parse_results.pdf_file_id` / `parse_logs.pdf_file_id` constraints may exist in an upgraded database even if the current `ocqa.sql` snapshot does not define them.
- The migration script must leave the migrated schema aligned with `ocqa.sql`, but the new material tables intentionally do not add database-level FK constraints; relationship integrity is enforced by service logic plus unique/lookup indexes.
- MySQL DDL causes implicit commits, so the plan separates structural changes from DML transactions and requires preflight checks plus a disposable-schema smoke before touching a real database.
- Java workflow tests move to Task 6, after entity and service classes exist, so Task 5 can remain a model-groundwork task.
- `scripts/audit_repo_drift.py` already exists at the repository root; documentation tasks reuse it instead of creating a new script.
- Compatibility cleanup for `pdf_{id}` and new `material_{id}` artifact paths is explicitly tested and marked as a temporary compatibility bridge.

## File Structure

- Modify: `pdf_ingest/sql/ocqa.sql`
  负责定义 `material_objects`、`course_materials`、`parse_results.course_material_id`、`parse_logs.course_material_id`、`v_course_parse_overview`，并移除 `pdf_files.file_md5` 全局唯一旧模型。
- Create: `pdf_ingest/sql/migrations/20260423_course_materials.sql`
  负责从已有 `pdf_files` 数据迁移到新表；迁移脚本使用幂等 DDL/DML，便于已有本地库升级。
- Modify: `pdf_ingest/tests/test_ocqa_business_schema_contract.py`
  负责保护新业务 schema 合同，包括多对多表、唯一约束、查询索引、无新增 FK 约束边界和解析表字段。
- Modify: `pdf_ingest/tests/test_ocqa_docs_contract.py`
  负责保护 README 与设计文档对新模型的描述不漂移。
- Create: `pdf_ingest/tests/test_course_material_reuse.py`
  负责用假 DB/假 MinIO 验证同一资料跨课程复用、同课程重复处理、`force` 只清理当前课程资料。
- Modify: `pdf_ingest/scripts/pdf_processor/db_service.py`
  负责新增 material object 与 course material 的 CRUD，并保留旧方法名作为兼容包装。
- Modify: `pdf_ingest/scripts/pdf_processor/storage_service.py`
  负责新增按 MD5 上传/复用原始资料对象的方法，并支持按 bucket/object key 下载源文件。
- Modify: `pdf_ingest/scripts/pdf_processor/mineru_parser.py`
  负责将 upload/parse/status/download/list/export-graphrag 的内部主键切到 `course_material_id`，同时保留 `--file-id`/`--file-name` 参数兼容。
- Modify: `pdf_ingest/scripts/pdf_processor/graphrag_exporter.py`
  负责把导出逻辑中的 `pdf_file_id` 语义切到课程资料 ID，并在输出 metadata 中同时保留 `course_material_id` 和兼容字段 `pdf_file_id`。
- Modify: `graphrag_pipeline/utils/fetch_from_minio.py`
  负责兼容 `material_{id}` 和旧 `pdf_{id}` namespaced 路径；默认仍可用 `--pdf-file-id`，新增 `--material-id`。
- Modify: `backend/ckqa-back/src/test/java/org/ysu/ckqaback/support/codegen/MybatisPlusCodeGenerator.java`
  负责把生成表清单从 `pdf_files` 改为 `material_objects` 与 `course_materials`，并保留手写兼容 Controller。
- Create: `backend/ckqa-back/src/main/java/org/ysu/ckqaback/entity/MaterialObjects.java`
  负责映射 `material_objects` 表。
- Create: `backend/ckqa-back/src/main/java/org/ysu/ckqaback/entity/CourseMaterials.java`
  负责映射 `course_materials` 表。
- Modify: `backend/ckqa-back/src/main/java/org/ysu/ckqaback/entity/ParseResults.java`
  负责将字段从 `pdfFileId` 迁移为 `courseMaterialId`，并为旧 DTO 输出提供兼容读取。
- Modify: `backend/ckqa-back/src/main/java/org/ysu/ckqaback/entity/ParseLogs.java`
  负责将字段从 `pdfFileId` 迁移为 `courseMaterialId`。
- Create: `backend/ckqa-back/src/main/java/org/ysu/ckqaback/mapper/MaterialObjectsMapper.java`
  负责 `material_objects` 的 MyBatis-Plus Mapper。
- Create: `backend/ckqa-back/src/main/java/org/ysu/ckqaback/mapper/CourseMaterialsMapper.java`
  负责 `course_materials` 的 MyBatis-Plus Mapper。
- Create: `backend/ckqa-back/src/main/java/org/ysu/ckqaback/service/MaterialObjectsService.java`
  负责 material object 查询。
- Create: `backend/ckqa-back/src/main/java/org/ysu/ckqaback/service/CourseMaterialsService.java`
  负责课程资料查询、抢占解析状态、失败回写。
- Create: `backend/ckqa-back/src/main/java/org/ysu/ckqaback/service/impl/MaterialObjectsServiceImpl.java`
  负责 material object 服务实现。
- Create: `backend/ckqa-back/src/main/java/org/ysu/ckqaback/service/impl/CourseMaterialsServiceImpl.java`
  负责课程资料服务实现。
- Modify: `backend/ckqa-back/src/main/java/org/ysu/ckqaback/pdf/PdfWorkflowService.java`
  负责把内部依赖从 `PdfFilesService` 切到 `CourseMaterialsService`，并保留旧类名入口语义。
- Modify: `backend/ckqa-back/src/main/java/org/ysu/ckqaback/integration/pdf/PdfIngestOrchestrator.java`
  负责调用 `mineru_parser.py parse/export-graphrag --material-id`。
- Modify: `backend/ckqa-back/src/main/java/org/ysu/ckqaback/pdf/dto/PdfFileResponse.java`
  负责旧 API 输出兼容，额外暴露 `materialId` 与 `materialObjectId`。
- Modify: `backend/ckqa-back/src/main/java/org/ysu/ckqaback/pdf/dto/ParseResultResponse.java`
  负责输出 `courseMaterialId`，同时保留 `pdfFileId` 兼容字段。
- Modify: `backend/ckqa-back/src/main/java/org/ysu/ckqaback/course/CourseLookupService.java`
  负责课程资料列表从 `course_materials` 查询。
- Modify: `backend/ckqa-back/src/main/java/org/ysu/ckqaback/course/dto/CoursePdfFileSummaryResponse.java`
  负责旧课程 PDF 列表 DTO 兼容新字段。
- Modify: `backend/ckqa-back/src/test/java/org/ysu/ckqaback/pdf/PdfWorkflowServiceTest.java`
  负责 Java 编排服务的状态抢占、失败回写、导出锁兼容测试。
- Modify: `backend/ckqa-back/src/test/java/org/ysu/ckqaback/controller/PdfFilesControllerWebMvcTest.java`
  负责旧 `/api/v1/pdf-files` 路由兼容测试。
- Modify: `backend/ckqa-back/src/test/java/org/ysu/ckqaback/controller/CoursesControllerWebMvcTest.java`
  负责旧 `/api/v1/courses/{courseId}/pdf-files` 课程资料列表兼容测试。
- Modify: `README.md`
  负责更新仓库主链路描述。
- Modify: `pdf_ingest/README.md`
  负责更新课程资料多对多、命令示例和验证说明。
- Modify: `pdf_ingest/CLAUDE.md`
  负责更新 agent 约束中的数据库模型和状态机说明。
- Modify: `pdf_ingest/docs/MinerU PDF Parser.md`
  负责更新详细使用文档中关于全局 MD5 唯一限制的旧描述。
- Modify: `backend/ckqa-back/README.md`
  负责更新 Java 编排入口对 `course_materials` 的说明和兼容边界。

## Implementation Tasks

### Task 1: Schema Contract For Material Reuse

**Files:**
- Modify: `pdf_ingest/tests/test_ocqa_business_schema_contract.py`
- Modify: `pdf_ingest/tests/test_ocqa_docs_contract.py`
- Modify: `pdf_ingest/sql/ocqa.sql`
- Create: `pdf_ingest/sql/migrations/20260423_course_materials.sql`

- [x] **Step 1: Write failing schema contract tests**

Add these tests to `pdf_ingest/tests/test_ocqa_business_schema_contract.py`:

```python
    def test_material_object_and_course_material_tables_exist(self):
        self.assertIn("CREATE TABLE `material_objects`", self.text)
        self.assertIn("CREATE TABLE `course_materials`", self.text)
        self.assertIn("`file_md5` char(32)", self.text)
        self.assertIn("UNIQUE INDEX `uk_material_objects_md5`(`file_md5` ASC)", self.text)
        self.assertIn("`material_object_id` bigint NOT NULL", self.text)
        self.assertIn("UNIQUE INDEX `uk_course_material_object`(`course_id` ASC, `material_object_id` ASC)", self.text)
        self.assertIn("UNIQUE INDEX `uk_course_material_display_name`(`course_id` ASC, `display_name` ASC)", self.text)

    def test_parse_tables_reference_course_materials(self):
        self.assertIn("`course_material_id` bigint NOT NULL COMMENT '关联的课程资料ID'", self.text)
        self.assertIn("INDEX `idx_course_material_id`(`course_material_id` ASC)", self.text)

    def test_pdf_files_table_is_removed_from_schema_truth(self):
        self.assertNotIn("CREATE TABLE `pdf_files`", self.text)
        self.assertNotIn("UNIQUE INDEX `uk_file_md5`(`file_md5` ASC)", self.text)

    def test_material_tables_do_not_add_database_foreign_keys(self):
        self.assertNotIn("fk_course_materials_course", self.text)
        self.assertNotIn("fk_course_materials_material_object", self.text)
        self.assertNotIn("fk_parse_results_course_material", self.text)
        self.assertNotIn("fk_parse_logs_course_material", self.text)
```

Add this assertion to `test_business_tables_exist`:

```python
            "material_objects",
            "course_materials",
```

Add this migration-script guard to the same test file so the live upgrade path cannot drift from `ocqa.sql`:

```python
    def test_course_material_migration_script_drops_old_fk_without_adding_new_fk(self):
        migration = (
            Path(__file__).resolve().parents[1]
            / "sql"
            / "migrations"
            / "20260423_course_materials.sql"
        ).read_text(encoding="utf-8")
        self.assertIn("CALL `ckqa_drop_fk_if_exists`('parse_results', 'fk_parse_results_pdf_file')", migration)
        self.assertIn("CALL `ckqa_drop_fk_if_exists`('parse_logs', 'fk_parse_logs_pdf_file')", migration)
        self.assertIn("START TRANSACTION;", migration)
        self.assertIn("COMMIT;", migration)
        self.assertNotIn("ckqa_add_fk_if_missing", migration)
        self.assertNotIn("fk_course_materials_course", migration)
        self.assertNotIn("fk_course_materials_material_object", migration)
        self.assertNotIn("fk_parse_results_course_material", migration)
        self.assertNotIn("fk_parse_logs_course_material", migration)
        self.assertIn("idx_material_objects_created_at", migration)
        self.assertIn("idx_course_materials_course_status", migration)
        self.assertIn("idx_course_materials_upload_time", migration)
        self.assertIn("AUTO_INCREMENT = ", migration)
        self.assertIn("DROP TABLE IF EXISTS `pdf_files`", migration)
```

Update `pdf_ingest/tests/test_ocqa_docs_contract.py` so docs must mention the new model:

```python
    def test_readme_mentions_course_material_reuse_model(self):
        text = README_PATH.read_text(encoding="utf-8")
        self.assertIn("material_objects", text)
        self.assertIn("course_materials", text)
        self.assertIn("同一份资料可以被多门课程复用", text)
        self.assertNotIn("同一份 PDF 不能同时归属多个课程", text)
```

- [ ] **Step 2: Run tests to verify they fail**

Run:

```bash
cd /home/sunlight/Projects/ckqa/pdf_ingest
python -m pytest tests/test_ocqa_business_schema_contract.py tests/test_ocqa_docs_contract.py -q
```

Expected: FAIL because `material_objects` and `course_materials` are not in `ocqa.sql` and README yet.

- [x] **Step 3: Replace old `pdf_files` schema with new material schema**

In `pdf_ingest/sql/ocqa.sql`, replace the old `parse_logs`, `parse_results`, and `pdf_files` block with this structure:

```sql
DROP TABLE IF EXISTS `parse_logs`;
DROP TABLE IF EXISTS `parse_results`;
DROP TABLE IF EXISTS `course_materials`;
DROP TABLE IF EXISTS `material_objects`;

CREATE TABLE `material_objects` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `original_file_name` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '首次上传时的原始文件名',
  `file_md5` char(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '文件MD5哈希值',
  `file_size` bigint NOT NULL COMMENT '文件大小（字节）',
  `mime_type` varchar(128) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT 'application/pdf' COMMENT '文件MIME类型',
  `minio_bucket` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT 'MinIO存储桶名称',
  `minio_object_key` varchar(512) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT 'MinIO对象键（路径）',
  `created_at` timestamp NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_at` timestamp NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`) USING BTREE,
  UNIQUE INDEX `uk_material_objects_md5`(`file_md5` ASC) USING BTREE,
  INDEX `idx_material_objects_created_at`(`created_at` ASC) USING BTREE
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = '资料物理对象表' ROW_FORMAT = Dynamic;

CREATE TABLE `course_materials` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `course_id` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '课程ID',
  `material_object_id` bigint NOT NULL COMMENT '资料物理对象ID',
  `display_name` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '课程内展示文件名',
  `material_type` enum('textbook','handout','slides','lab_guide','exam','reference','other') CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT 'textbook' COMMENT '课程资料类型',
  `parse_status` enum('pending','processing','done','failed') CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT 'pending' COMMENT '解析状态',
  `parse_started_at` timestamp NULL DEFAULT NULL COMMENT '解析开始时间',
  `parse_finished_at` timestamp NULL DEFAULT NULL COMMENT '解析完成时间',
  `parse_error_msg` text CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL COMMENT '解析错误信息',
  `mineru_batch_id` varchar(128) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT 'MinerU批次ID',
  `upload_time` timestamp NULL DEFAULT CURRENT_TIMESTAMP COMMENT '课程资料接入时间',
  `created_at` timestamp NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_at` timestamp NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`) USING BTREE,
  UNIQUE INDEX `uk_course_material_object`(`course_id` ASC, `material_object_id` ASC) USING BTREE,
  UNIQUE INDEX `uk_course_material_display_name`(`course_id` ASC, `display_name` ASC) USING BTREE,
  INDEX `idx_course_materials_course_status`(`course_id` ASC, `parse_status` ASC) USING BTREE,
  INDEX `idx_course_materials_upload_time`(`upload_time` ASC) USING BTREE
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = '课程资料关联表' ROW_FORMAT = Dynamic;

CREATE TABLE `parse_results`  (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `course_material_id` bigint NOT NULL COMMENT '关联的课程资料ID',
  `course_id` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '课程ID',
  `result_type` enum('content_list_json','model_json','layout_json','markdown','image','origin_pdf','other') CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '结果类型',
  `file_name` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '文件名',
  `minio_bucket` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT 'MinIO存储桶',
  `minio_object_key` varchar(512) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT 'MinIO对象键',
  `file_size` bigint NULL DEFAULT 0 COMMENT '文件大小（字节）',
  `created_at` timestamp NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  PRIMARY KEY (`id`) USING BTREE,
  INDEX `idx_course_material_id`(`course_material_id` ASC) USING BTREE,
  INDEX `idx_course_id`(`course_id` ASC) USING BTREE,
  INDEX `idx_result_type`(`result_type` ASC) USING BTREE
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = '解析结果表' ROW_FORMAT = Dynamic;

CREATE TABLE `parse_logs`  (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `course_material_id` bigint NOT NULL COMMENT '关联的课程资料ID',
  `log_level` enum('info','warning','error') CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT 'info' COMMENT '日志级别',
  `log_message` text CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '日志内容',
  `created_at` timestamp NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  PRIMARY KEY (`id`) USING BTREE,
  INDEX `idx_course_material_id`(`course_material_id` ASC) USING BTREE,
  INDEX `idx_log_level`(`log_level` ASC) USING BTREE,
  INDEX `idx_created_at`(`created_at` ASC) USING BTREE
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = '解析日志表' ROW_FORMAT = Dynamic;
```

Update `v_course_parse_overview` so it joins `course_materials` and `material_objects`, and provides both new and legacy aliases:

```sql
CREATE ALGORITHM = UNDEFINED SQL SECURITY DEFINER VIEW `v_course_parse_overview` AS
select
  `c`.`course_id` AS `course_id`,
  `c`.`course_name` AS `course_name`,
  `cm`.`id` AS `course_material_id`,
  `cm`.`id` AS `pdf_file_id`,
  `cm`.`display_name` AS `display_name`,
  `cm`.`display_name` AS `file_name`,
  `mo`.`file_md5` AS `file_md5`,
  `mo`.`file_size` AS `file_size`,
  `cm`.`material_type` AS `material_type`,
  `cm`.`parse_status` AS `parse_status`,
  `cm`.`upload_time` AS `upload_time`,
  `cm`.`parse_started_at` AS `parse_started_at`,
  `cm`.`parse_finished_at` AS `parse_finished_at`,
  timestampdiff(SECOND,`cm`.`parse_started_at`,`cm`.`parse_finished_at`) AS `parse_duration_seconds`,
  (select count(0) from `parse_results` `pr` where (`pr`.`course_material_id` = `cm`.`id`)) AS `result_file_count`
from ((`courses` `c`
left join `course_materials` `cm` on((`c`.`course_id` = `cm`.`course_id`)))
left join `material_objects` `mo` on((`cm`.`material_object_id` = `mo`.`id`)));
```

- [x] **Step 4: Add live migration script**

Create `pdf_ingest/sql/migrations/20260423_course_materials.sql`:

```sql
SET NAMES utf8mb4;

-- 迁移安全边界：
-- 1. MySQL DDL 会隐式提交，CREATE/ALTER/DROP 不能被外层事务完整回滚。
-- 2. 下方 START TRANSACTION / COMMIT 只保护从 pdf_files 复制到新表的 DML 数据迁移。
-- 3. 真实库执行前必须先在 disposable schema 跑完整脚本，并确认旧 FK 已删除、没有新增 FK、列名、视图和 AUTO_INCREMENT。

DROP PROCEDURE IF EXISTS `ckqa_drop_fk_if_exists`;
DROP PROCEDURE IF EXISTS `ckqa_add_index_if_missing`;
DROP PROCEDURE IF EXISTS `ckqa_rename_index_if_exists`;
DROP PROCEDURE IF EXISTS `ckqa_change_pdf_column_if_needed`;

DELIMITER $$

CREATE PROCEDURE `ckqa_drop_fk_if_exists`(
  IN table_name_param varchar(64),
  IN fk_name_param varchar(64)
)
BEGIN
  IF EXISTS (
    SELECT 1
    FROM information_schema.TABLE_CONSTRAINTS
    WHERE CONSTRAINT_SCHEMA = DATABASE()
      AND TABLE_NAME = table_name_param
      AND CONSTRAINT_NAME = fk_name_param
      AND CONSTRAINT_TYPE = 'FOREIGN KEY'
  ) THEN
    SET @drop_fk_sql = CONCAT('ALTER TABLE `', table_name_param, '` DROP FOREIGN KEY `', fk_name_param, '`');
    PREPARE drop_fk_stmt FROM @drop_fk_sql;
    EXECUTE drop_fk_stmt;
    DEALLOCATE PREPARE drop_fk_stmt;
  END IF;
END$$

CREATE PROCEDURE `ckqa_add_index_if_missing`(
  IN table_name_param varchar(64),
  IN index_name_param varchar(64),
  IN index_clause_param text
)
BEGIN
  IF NOT EXISTS (
    SELECT 1
    FROM information_schema.STATISTICS
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME = table_name_param
      AND INDEX_NAME = index_name_param
  ) THEN
    SET @add_index_sql = CONCAT('ALTER TABLE `', table_name_param, '` ', index_clause_param);
    PREPARE add_index_stmt FROM @add_index_sql;
    EXECUTE add_index_stmt;
    DEALLOCATE PREPARE add_index_stmt;
  END IF;
END$$

CREATE PROCEDURE `ckqa_rename_index_if_exists`(
  IN table_name_param varchar(64),
  IN old_index_name_param varchar(64),
  IN new_index_name_param varchar(64)
)
BEGIN
  IF EXISTS (
    SELECT 1
    FROM information_schema.STATISTICS
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME = table_name_param
      AND INDEX_NAME = old_index_name_param
  ) AND NOT EXISTS (
    SELECT 1
    FROM information_schema.STATISTICS
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME = table_name_param
      AND INDEX_NAME = new_index_name_param
  ) THEN
    SET @rename_index_sql = CONCAT(
      'ALTER TABLE `', table_name_param, '` RENAME INDEX `',
      old_index_name_param, '` TO `', new_index_name_param, '`'
    );
    PREPARE rename_index_stmt FROM @rename_index_sql;
    EXECUTE rename_index_stmt;
    DEALLOCATE PREPARE rename_index_stmt;
  END IF;
END$$

CREATE PROCEDURE `ckqa_change_pdf_column_if_needed`(
  IN table_name_param varchar(64)
)
BEGIN
  IF EXISTS (
    SELECT 1
    FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME = table_name_param
      AND COLUMN_NAME = 'pdf_file_id'
  ) AND NOT EXISTS (
    SELECT 1
    FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME = table_name_param
      AND COLUMN_NAME = 'course_material_id'
  ) THEN
    SET @change_column_sql = CONCAT(
      'ALTER TABLE `', table_name_param,
      '` CHANGE COLUMN `pdf_file_id` `course_material_id` bigint NOT NULL COMMENT ''关联的课程资料ID'''
    );
    PREPARE change_column_stmt FROM @change_column_sql;
    EXECUTE change_column_stmt;
    DEALLOCATE PREPARE change_column_stmt;
  END IF;
END$$

DELIMITER ;

-- 旧库可能存在这些外键；即使当前 ocqa.sql 快照未定义，也要先兼容删除。
CALL `ckqa_drop_fk_if_exists`('parse_results', 'fk_parse_results_pdf_file');
CALL `ckqa_drop_fk_if_exists`('parse_logs', 'fk_parse_logs_pdf_file');

DROP VIEW IF EXISTS `v_course_parse_overview`;

CREATE TABLE IF NOT EXISTS `material_objects` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `original_file_name` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '首次上传时的原始文件名',
  `file_md5` char(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '文件MD5哈希值',
  `file_size` bigint NOT NULL COMMENT '文件大小（字节）',
  `mime_type` varchar(128) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT 'application/pdf' COMMENT '文件MIME类型',
  `minio_bucket` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT 'MinIO存储桶名称',
  `minio_object_key` varchar(512) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT 'MinIO对象键（路径）',
  `created_at` timestamp NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_at` timestamp NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`) USING BTREE,
  UNIQUE INDEX `uk_material_objects_md5`(`file_md5` ASC) USING BTREE
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = '资料物理对象表' ROW_FORMAT = Dynamic;

CREATE TABLE IF NOT EXISTS `course_materials` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `course_id` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '课程ID',
  `material_object_id` bigint NOT NULL COMMENT '资料物理对象ID',
  `display_name` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '课程内展示文件名',
  `material_type` enum('textbook','handout','slides','lab_guide','exam','reference','other') CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT 'textbook' COMMENT '课程资料类型',
  `parse_status` enum('pending','processing','done','failed') CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT 'pending' COMMENT '解析状态',
  `parse_started_at` timestamp NULL DEFAULT NULL COMMENT '解析开始时间',
  `parse_finished_at` timestamp NULL DEFAULT NULL COMMENT '解析完成时间',
  `parse_error_msg` text CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL COMMENT '解析错误信息',
  `mineru_batch_id` varchar(128) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT 'MinerU批次ID',
  `upload_time` timestamp NULL DEFAULT CURRENT_TIMESTAMP COMMENT '课程资料接入时间',
  `created_at` timestamp NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_at` timestamp NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`) USING BTREE,
  UNIQUE INDEX `uk_course_material_object`(`course_id` ASC, `material_object_id` ASC) USING BTREE,
  UNIQUE INDEX `uk_course_material_display_name`(`course_id` ASC, `display_name` ASC) USING BTREE
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = '课程资料关联表' ROW_FORMAT = Dynamic;

CALL `ckqa_add_index_if_missing`(
  'material_objects',
  'idx_material_objects_created_at',
  'ADD INDEX `idx_material_objects_created_at`(`created_at` ASC) USING BTREE'
);
CALL `ckqa_add_index_if_missing`(
  'course_materials',
  'idx_course_materials_course_status',
  'ADD INDEX `idx_course_materials_course_status`(`course_id` ASC, `parse_status` ASC) USING BTREE'
);
CALL `ckqa_add_index_if_missing`(
  'course_materials',
  'idx_course_materials_upload_time',
  'ADD INDEX `idx_course_materials_upload_time`(`upload_time` ASC) USING BTREE'
);

START TRANSACTION;

INSERT IGNORE INTO `material_objects`
  (`original_file_name`, `file_md5`, `file_size`, `mime_type`, `minio_bucket`, `minio_object_key`, `created_at`, `updated_at`)
SELECT
  `file_name`, `file_md5`, `file_size`, 'application/pdf', `minio_bucket`, `minio_object_key`, `created_at`, `updated_at`
FROM `pdf_files`;

INSERT IGNORE INTO `course_materials`
  (`id`, `course_id`, `material_object_id`, `display_name`, `material_type`, `parse_status`, `parse_started_at`,
   `parse_finished_at`, `parse_error_msg`, `mineru_batch_id`, `upload_time`, `created_at`, `updated_at`)
SELECT
  `pf`.`id`, `pf`.`course_id`, `mo`.`id`, `pf`.`file_name`, 'textbook', `pf`.`parse_status`, `pf`.`parse_started_at`,
  `pf`.`parse_finished_at`, `pf`.`parse_error_msg`, `pf`.`mineru_batch_id`, `pf`.`upload_time`, `pf`.`created_at`, `pf`.`updated_at`
FROM `pdf_files` `pf`
JOIN `material_objects` `mo` ON `mo`.`file_md5` = `pf`.`file_md5`;

COMMIT;

CALL `ckqa_change_pdf_column_if_needed`('parse_results');
CALL `ckqa_change_pdf_column_if_needed`('parse_logs');
CALL `ckqa_rename_index_if_exists`('parse_results', 'idx_pdf_file_id', 'idx_course_material_id');
CALL `ckqa_rename_index_if_exists`('parse_logs', 'idx_pdf_file_id', 'idx_course_material_id');

CREATE ALGORITHM = UNDEFINED SQL SECURITY DEFINER VIEW `v_course_parse_overview` AS
select
  `c`.`course_id` AS `course_id`,
  `c`.`course_name` AS `course_name`,
  `cm`.`id` AS `course_material_id`,
  `cm`.`id` AS `pdf_file_id`,
  `cm`.`display_name` AS `display_name`,
  `cm`.`display_name` AS `file_name`,
  `mo`.`file_md5` AS `file_md5`,
  `mo`.`file_size` AS `file_size`,
  `cm`.`material_type` AS `material_type`,
  `cm`.`parse_status` AS `parse_status`,
  `cm`.`upload_time` AS `upload_time`,
  `cm`.`parse_started_at` AS `parse_started_at`,
  `cm`.`parse_finished_at` AS `parse_finished_at`,
  timestampdiff(SECOND,`cm`.`parse_started_at`,`cm`.`parse_finished_at`) AS `parse_duration_seconds`,
  (select count(0) from `parse_results` `pr` where (`pr`.`course_material_id` = `cm`.`id`)) AS `result_file_count`
from ((`courses` `c`
left join `course_materials` `cm` on((`c`.`course_id` = `cm`.`course_id`)))
left join `material_objects` `mo` on((`cm`.`material_object_id` = `mo`.`id`)));

SELECT COALESCE(MAX(`id`), 0) + 1 INTO @next_material_object_id FROM `material_objects`;
SET @reset_material_object_ai_sql = CONCAT('ALTER TABLE `material_objects` AUTO_INCREMENT = ', @next_material_object_id);
PREPARE reset_material_object_ai_stmt FROM @reset_material_object_ai_sql;
EXECUTE reset_material_object_ai_stmt;
DEALLOCATE PREPARE reset_material_object_ai_stmt;

SELECT COALESCE(MAX(`id`), 0) + 1 INTO @next_course_material_id FROM `course_materials`;
SET @reset_course_material_ai_sql = CONCAT('ALTER TABLE `course_materials` AUTO_INCREMENT = ', @next_course_material_id);
PREPARE reset_course_material_ai_stmt FROM @reset_course_material_ai_sql;
EXECUTE reset_course_material_ai_stmt;
DEALLOCATE PREPARE reset_course_material_ai_stmt;

DROP TABLE IF EXISTS `pdf_files`;

DROP PROCEDURE IF EXISTS `ckqa_drop_fk_if_exists`;
DROP PROCEDURE IF EXISTS `ckqa_add_index_if_missing`;
DROP PROCEDURE IF EXISTS `ckqa_rename_index_if_exists`;
DROP PROCEDURE IF EXISTS `ckqa_change_pdf_column_if_needed`;
```

- [x] **Step 5: Update docs required by schema contract**

In `pdf_ingest/README.md`, replace the old note:

```text
当前系统按全局 `MD5` 去重，同一份 PDF 不能同时归属多个课程。
```

with:

```text
当前系统使用 `material_objects` 按 MD5 对原始资料对象全局去重，并使用 `course_materials` 表达课程与资料的多对多引用关系。同一份资料可以被多门课程复用，但解析状态、解析产物与 GraphRAG 导出仍按课程资料关系独立管理。
```

Before replacing, verify the old note still exists so a future wording drift does not silently skip the change:

```bash
cd /home/sunlight/Projects/ckqa
rg -n "同一份 PDF 不能同时归属多个课程|material_objects|course_materials" pdf_ingest/README.md
```

Expected before edit: the old `同一份 PDF 不能同时归属多个课程` sentence is present.
Expected after edit: `material_objects` and `course_materials` are present, and the old incompatibility sentence is gone.

- [x] **Step 6: Run schema contract tests**

Run:

```bash
cd /home/sunlight/Projects/ckqa/pdf_ingest
python -m pytest tests/test_ocqa_business_schema_contract.py tests/test_ocqa_docs_contract.py -q
```

Expected: PASS.

- [ ] **Step 7: Commit schema task**

```bash
git add pdf_ingest/sql/ocqa.sql \
  pdf_ingest/sql/migrations/20260423_course_materials.sql \
  pdf_ingest/tests/test_ocqa_business_schema_contract.py \
  pdf_ingest/tests/test_ocqa_docs_contract.py \
  pdf_ingest/README.md
git commit -m "feat: model reusable course materials"
```

### Task 2: Python DB And Storage Services

**Files:**
- Create: `pdf_ingest/tests/test_course_material_reuse.py`
- Modify: `pdf_ingest/scripts/pdf_processor/db_service.py`
- Modify: `pdf_ingest/scripts/pdf_processor/storage_service.py`

- [x] **Step 1: Write failing Python service tests**

Create `pdf_ingest/tests/test_course_material_reuse.py`:

```python
from pathlib import Path
import sys
import tempfile
import unittest


MODULE_DIR = Path(__file__).resolve().parents[1] / "scripts" / "pdf_processor"
sys.path.insert(0, str(MODULE_DIR))

from mineru_parser import PDFParserApp  # noqa: E402


class FakeStorage:
    def __init__(self):
        self.uploads = []
        self.deleted_artifacts = []

    @staticmethod
    def calculate_md5(file_path: str) -> str:
        return "same-md5"

    def upload_material_object(self, file_path: str, file_md5: str, file_name: str) -> dict:
        self.uploads.append((file_path, file_md5, file_name))
        return {
            "bucket": "course-pdfs",
            "object_key": f"materials/{file_md5}.pdf",
            "md5": file_md5,
            "size": Path(file_path).stat().st_size,
        }

    def delete_artifacts(self, course_id: str, relative_prefix: str = ""):
        self.deleted_artifacts.append((course_id, relative_prefix))


class FakeDB:
    def __init__(self):
        self.material_objects = {}
        self.course_materials = {}
        self.logs = []
        self.next_object_id = 1
        self.next_material_id = 1

    def create_course(self, course_id, course_name=None, description=None):
        return 1

    def get_material_object_by_md5(self, file_md5):
        return self.material_objects.get(file_md5)

    def create_material_object(self, original_file_name, file_md5, file_size, minio_bucket, minio_object_key, mime_type="application/pdf"):
        material_object = {
            "id": self.next_object_id,
            "original_file_name": original_file_name,
            "file_md5": file_md5,
            "file_size": file_size,
            "minio_bucket": minio_bucket,
            "minio_object_key": minio_object_key,
            "mime_type": mime_type,
        }
        self.material_objects[file_md5] = material_object
        self.next_object_id += 1
        return material_object["id"]

    def get_course_material_by_object(self, course_id, material_object_id):
        return self.course_materials.get((course_id, material_object_id))

    def get_course_material_by_course(self, course_id, display_name=None):
        if display_name is None:
            matches = [row for (cid, _), row in self.course_materials.items() if cid == course_id]
            return matches[-1] if matches else None
        for row in self.course_materials.values():
            if row["course_id"] == course_id and row["display_name"] == display_name:
                return row
        return None

    def create_course_material(self, course_id, material_object_id, display_name, material_type="textbook"):
        row = {
            "id": self.next_material_id,
            "course_id": course_id,
            "material_object_id": material_object_id,
            "display_name": display_name,
            "file_name": display_name,
            "file_md5": "same-md5",
            "file_size": 4,
            "minio_bucket": "course-pdfs",
            "minio_object_key": "materials/same-md5.pdf",
            "parse_status": "pending",
            "material_type": material_type,
        }
        self.course_materials[(course_id, material_object_id)] = row
        self.next_material_id += 1
        return row["id"]

    def delete_course_material(self, course_material_id):
        for key, row in list(self.course_materials.items()):
            if row["id"] == course_material_id:
                del self.course_materials[key]

    def add_log(self, course_material_id, message, level="info"):
        self.logs.append((course_material_id, level, message))


class CourseMaterialReuseTest(unittest.TestCase):
    def make_app(self):
        app = object.__new__(PDFParserApp)
        app.storage = FakeStorage()
        app.db = FakeDB()
        app.logger = type("Logger", (), {"info": lambda *args, **kwargs: None, "warning": lambda *args, **kwargs: None})()
        return app

    def write_pdf(self):
        temp_dir = tempfile.TemporaryDirectory()
        path = Path(temp_dir.name) / "book.pdf"
        path.write_bytes(b"%PDF")
        return temp_dir, path

    def test_same_material_can_be_reused_by_different_courses(self):
        app = self.make_app()
        temp_dir, path = self.write_pdf()
        self.addCleanup(temp_dir.cleanup)

        first = app.upload("os", str(path))
        second = app.upload("java", str(path))

        self.assertEqual("success", first["status"])
        self.assertEqual("success", second["status"])
        self.assertEqual(1, first["material_object_id"])
        self.assertEqual(1, second["material_object_id"])
        self.assertNotEqual(first["course_material_id"], second["course_material_id"])
        self.assertEqual(1, len(app.storage.uploads))

    def test_same_course_duplicate_returns_existing_relation_without_force(self):
        app = self.make_app()
        temp_dir, path = self.write_pdf()
        self.addCleanup(temp_dir.cleanup)

        first = app.upload("os", str(path))
        second = app.upload("os", str(path))

        self.assertEqual("success", first["status"])
        self.assertEqual("duplicate", second["status"])
        self.assertEqual(first["course_material_id"], second["course_material_id"])
        self.assertEqual(1, len(app.storage.uploads))

    def test_force_replaces_only_current_course_material_relation(self):
        app = self.make_app()
        temp_dir, path = self.write_pdf()
        self.addCleanup(temp_dir.cleanup)

        first = app.upload("os", str(path))
        app.upload("java", str(path))
        forced = app.upload("os", str(path), force=True)

        self.assertEqual("success", forced["status"])
        self.assertEqual(first["material_object_id"], forced["material_object_id"])
        self.assertNotEqual(first["course_material_id"], forced["course_material_id"])
        self.assertIn(("os", f"pdf_{first['course_material_id']}"), app.storage.deleted_artifacts)
        self.assertIn(("os", f"graphrag/pdf_{first['course_material_id']}"), app.storage.deleted_artifacts)
        self.assertIn(("os", f"material_{first['course_material_id']}"), app.storage.deleted_artifacts)
        self.assertIn(("os", f"graphrag/material_{first['course_material_id']}"), app.storage.deleted_artifacts)
        self.assertEqual(1, len(app.storage.uploads))


if __name__ == "__main__":
    unittest.main()
```

- [ ] **Step 2: Run tests to verify they fail**

Run:

```bash
cd /home/sunlight/Projects/ckqa/pdf_ingest
python -m pytest tests/test_course_material_reuse.py -q
```

Expected: FAIL because `upload_material_object`, `get_material_object_by_md5`, `create_material_object`, and `create_course_material` are not wired into `PDFParserApp.upload`.

- [x] **Step 3: Add DB service methods and compatibility wrappers**

In `pdf_ingest/scripts/pdf_processor/db_service.py`, add material methods:

```python
    def create_material_object(self, original_file_name: str, file_md5: str, file_size: int,
                               minio_bucket: str, minio_object_key: str,
                               mime_type: str = "application/pdf") -> int:
        """创建资料物理对象记录。"""
        with self.get_connection() as conn:
            cursor = conn.cursor()
            cursor.execute("""
                INSERT INTO material_objects
                    (original_file_name, file_md5, file_size, mime_type, minio_bucket, minio_object_key)
                VALUES (%s, %s, %s, %s, %s, %s)
                ON DUPLICATE KEY UPDATE
                    original_file_name = VALUES(original_file_name),
                    file_size = VALUES(file_size),
                    mime_type = VALUES(mime_type),
                    minio_bucket = VALUES(minio_bucket),
                    minio_object_key = VALUES(minio_object_key)
            """, (original_file_name, file_md5, file_size, mime_type, minio_bucket, minio_object_key))
            cursor.execute("SELECT id FROM material_objects WHERE file_md5 = %s", (file_md5,))
            return cursor.fetchone()["id"]

    def get_material_object_by_md5(self, file_md5: str) -> Optional[Dict]:
        """根据 MD5 查询资料物理对象。"""
        with self.get_connection() as conn:
            cursor = conn.cursor()
            cursor.execute("SELECT * FROM material_objects WHERE file_md5 = %s", (file_md5,))
            return cursor.fetchone()

    def get_material_object_by_id(self, material_object_id: int) -> Optional[Dict]:
        """根据 ID 查询资料物理对象。"""
        with self.get_connection() as conn:
            cursor = conn.cursor()
            cursor.execute("SELECT * FROM material_objects WHERE id = %s", (material_object_id,))
            return cursor.fetchone()

    def create_course_material(self, course_id: str, material_object_id: int,
                               display_name: str, material_type: str = "textbook") -> int:
        """创建课程资料关联记录。"""
        self.create_course(course_id)
        with self.get_connection() as conn:
            cursor = conn.cursor()
            cursor.execute("""
                INSERT INTO course_materials
                    (course_id, material_object_id, display_name, material_type)
                VALUES (%s, %s, %s, %s)
            """, (course_id, material_object_id, display_name, material_type))
            return cursor.lastrowid

    def get_course_material_by_id(self, course_material_id: int) -> Optional[Dict]:
        """根据 ID 获取课程资料，附带物理对象字段。"""
        with self.get_connection() as conn:
            cursor = conn.cursor()
            cursor.execute("""
                SELECT
                    cm.*,
                    cm.display_name AS file_name,
                    mo.file_md5,
                    mo.file_size,
                    mo.minio_bucket,
                    mo.minio_object_key,
                    mo.mime_type,
                    mo.original_file_name
                FROM course_materials cm
                JOIN material_objects mo ON mo.id = cm.material_object_id
                WHERE cm.id = %s
            """, (course_material_id,))
            return cursor.fetchone()

    def get_course_material_by_object(self, course_id: str, material_object_id: int) -> Optional[Dict]:
        """按课程和资料对象查询课程资料关联。"""
        with self.get_connection() as conn:
            cursor = conn.cursor()
            cursor.execute("""
                SELECT
                    cm.*,
                    cm.display_name AS file_name,
                    mo.file_md5,
                    mo.file_size,
                    mo.minio_bucket,
                    mo.minio_object_key,
                    mo.mime_type,
                    mo.original_file_name
                FROM course_materials cm
                JOIN material_objects mo ON mo.id = cm.material_object_id
                WHERE cm.course_id = %s AND cm.material_object_id = %s
            """, (course_id, material_object_id))
            return cursor.fetchone()

    def get_course_material_by_course(self, course_id: str, display_name: Optional[str] = None) -> Optional[Dict]:
        """根据课程和展示名查询课程资料；未传展示名时返回最新一条。"""
        with self.get_connection() as conn:
            cursor = conn.cursor()
            if display_name is not None:
                cursor.execute("""
                    SELECT
                        cm.*,
                        cm.display_name AS file_name,
                        mo.file_md5,
                        mo.file_size,
                        mo.minio_bucket,
                        mo.minio_object_key,
                        mo.mime_type,
                        mo.original_file_name
                    FROM course_materials cm
                    JOIN material_objects mo ON mo.id = cm.material_object_id
                    WHERE cm.course_id = %s AND cm.display_name = %s
                """, (course_id, display_name))
            else:
                cursor.execute("""
                    SELECT
                        cm.*,
                        cm.display_name AS file_name,
                        mo.file_md5,
                        mo.file_size,
                        mo.minio_bucket,
                        mo.minio_object_key,
                        mo.mime_type,
                        mo.original_file_name
                    FROM course_materials cm
                    JOIN material_objects mo ON mo.id = cm.material_object_id
                    WHERE cm.course_id = %s
                    ORDER BY cm.upload_time DESC, cm.id DESC
                    LIMIT 1
                """, (course_id,))
            return cursor.fetchone()

    def get_course_materials_by_course(self, course_id: str) -> List[Dict]:
        """获取课程下所有资料记录，附带物理对象字段。"""
        with self.get_connection() as conn:
            cursor = conn.cursor()
            cursor.execute("""
                SELECT
                    cm.*,
                    cm.display_name AS file_name,
                    mo.file_md5,
                    mo.file_size,
                    mo.minio_bucket,
                    mo.minio_object_key,
                    mo.mime_type,
                    mo.original_file_name
                FROM course_materials cm
                JOIN material_objects mo ON mo.id = cm.material_object_id
                WHERE cm.course_id = %s
                ORDER BY cm.upload_time DESC, cm.id DESC
            """, (course_id,))
            return cursor.fetchall()
```

Add compatibility wrappers so existing call sites can be updated incrementally:

```python
    def get_pdf_file_by_id(self, file_id: int) -> Optional[Dict]:
        """兼容旧命名：file_id 实际为 course_material_id。"""
        return self.get_course_material_by_id(file_id)

    def get_pdf_files_by_course(self, course_id: str) -> List[Dict]:
        """兼容旧命名：返回课程资料列表。"""
        return self.get_course_materials_by_course(course_id)

    def get_pdf_file_by_course(self, course_id: str, file_name: Optional[str] = None) -> Optional[Dict]:
        """兼容旧命名：file_name 实际为 display_name。"""
        return self.get_course_material_by_course(course_id, file_name)

    def check_md5_exists(self, file_md5: str) -> Optional[Dict]:
        """兼容旧命名：返回资料物理对象，而不是课程资料关系。"""
        return self.get_material_object_by_md5(file_md5)

    def delete_course_material(self, course_material_id: int):
        """删除课程资料关联记录，并级联删除解析结果与日志。"""
        with self.get_connection() as conn:
            cursor = conn.cursor()
            cursor.execute("DELETE FROM course_materials WHERE id = %s", (course_material_id,))

    def delete_pdf_file(self, file_id: int):
        """兼容旧命名：删除课程资料关联。"""
        self.delete_course_material(file_id)
```

Update parse-related methods to use `course_material_id`:

```python
    def update_parse_status(self, course_material_id: int, status: ParseStatus,
                            error_msg: Optional[str] = None, batch_id: Optional[str] = None):
        """更新课程资料解析状态。"""
        with self.get_connection() as conn:
            cursor = conn.cursor()
            updates = ["parse_status = %s"]
            params: List[Any] = [status.value]
            if status == ParseStatus.PROCESSING:
                updates.append("parse_started_at = NOW()")
            elif status in (ParseStatus.DONE, ParseStatus.FAILED):
                updates.append("parse_finished_at = NOW()")
            if error_msg is not None:
                updates.append("parse_error_msg = %s")
                params.append(error_msg)
            if batch_id is not None:
                updates.append("mineru_batch_id = %s")
                params.append(batch_id)
            params.append(course_material_id)
            cursor.execute(f"""
                UPDATE course_materials SET {', '.join(updates)} WHERE id = %s
            """, params)
```

Change `create_parse_result`, `get_parse_results`, `delete_parse_results`, `add_log`, and `get_logs` SQL from `pdf_file_id` to `course_material_id`.

- [x] **Step 4: Add storage material object helpers**

In `pdf_ingest/scripts/pdf_processor/storage_service.py`, add:

```python
    def upload_material_object(self, file_path: str, file_md5: str, file_name: str) -> dict:
        """
        上传可复用的资料物理对象。

        对象路径按 MD5 固定，避免同一文件内容在 MinIO 中重复存储。
        """
        suffix = Path(file_name).suffix.lower() or ".pdf"
        object_key = f"materials/{file_md5}{suffix}"
        file_size = os.path.getsize(file_path)
        with open(file_path, "rb") as f:
            self.client.put_object(
                bucket_name=self.config.bucket_pdf,
                object_name=object_key,
                data=f,
                length=file_size,
                content_type="application/pdf",
            )
        return {
            "bucket": self.config.bucket_pdf,
            "object_key": object_key,
            "md5": file_md5,
            "size": file_size,
        }

    def download_pdf_object(self, bucket: str, object_key: str, local_path: str) -> str:
        """按 bucket + object_key 下载原始资料对象。"""
        Path(local_path).parent.mkdir(parents=True, exist_ok=True)
        self.client.fget_object(
            bucket_name=bucket,
            object_name=object_key,
            file_path=local_path,
        )
        return local_path
```

- [x] **Step 5: Run targeted Python tests**

Run:

```bash
cd /home/sunlight/Projects/ckqa/pdf_ingest
python -m pytest tests/test_course_material_reuse.py -q
```

Expected: still FAIL because `PDFParserApp.upload` has not been switched to the new service methods.

- [ ] **Step 6: Commit service groundwork**

```bash
git add pdf_ingest/tests/test_course_material_reuse.py \
  pdf_ingest/scripts/pdf_processor/db_service.py \
  pdf_ingest/scripts/pdf_processor/storage_service.py
git commit -m "feat: add course material service primitives"
```

### Task 3: Python CLI Upload Parse Export Compatibility

**Files:**
- Modify: `pdf_ingest/scripts/pdf_processor/mineru_parser.py`
- Modify: `pdf_ingest/scripts/pdf_processor/graphrag_exporter.py`
- Modify: `pdf_ingest/tests/test_course_material_reuse.py`

- [x] **Step 1: Add CLI compatibility assertions to tests**

Append this test to `pdf_ingest/tests/test_course_material_reuse.py`:

```python
    def test_upload_result_keeps_legacy_file_id_alias(self):
        app = self.make_app()
        temp_dir, path = self.write_pdf()
        self.addCleanup(temp_dir.cleanup)

        result = app.upload("os", str(path))

        self.assertEqual(result["course_material_id"], result["file_id"])
        self.assertEqual("book.pdf", result["display_name"])
        self.assertEqual("book.pdf", result["file_name"])
```

- [ ] **Step 2: Run test to verify it fails**

Run:

```bash
cd /home/sunlight/Projects/ckqa/pdf_ingest
python -m pytest tests/test_course_material_reuse.py -q
```

Expected: FAIL because `PDFParserApp.upload` still returns only old fields and rejects cross-course duplicate MD5.

- [x] **Step 3: Update `PDFParserApp.upload`**

In `pdf_ingest/scripts/pdf_processor/mineru_parser.py`, replace the old MD5 duplicate section in `upload()` with this flow:

```python
        material_object = self.db.get_material_object_by_md5(file_md5)
        if material_object:
            material_object_id = int(material_object["id"])
            upload_result = {
                "bucket": material_object["minio_bucket"],
                "object_key": material_object["minio_object_key"],
                "md5": material_object["file_md5"],
                "size": material_object["file_size"],
            }
            self.logger.info("复用已存在的资料物理对象: material_object_id=%s", material_object_id)
        else:
            self.logger.info("上传资料物理对象到MinIO...")
            upload_result = self.storage.upload_material_object(
                str(file_path), file_md5, upload_file_name
            )
            material_object_id = self.db.create_material_object(
                original_file_name=upload_file_name,
                file_md5=file_md5,
                file_size=file_size,
                minio_bucket=upload_result["bucket"],
                minio_object_key=upload_result["object_key"],
                mime_type="application/pdf",
            )

        existing_relation = self.db.get_course_material_by_object(course_id, material_object_id)
        if existing_relation:
            if not force:
                self.logger.warning("课程已引用该资料对象")
                return {
                    "status": "duplicate",
                    "message": "课程已引用该资料",
                    "course_id": course_id,
                    "course_material_id": existing_relation["id"],
                    "file_id": existing_relation["id"],
                    "material_object_id": material_object_id,
                    "display_name": existing_relation["display_name"],
                    "file_name": existing_relation["display_name"],
                    "parse_status": existing_relation["parse_status"],
                }
            self.logger.info("强制覆盖当前课程资料关系")
            self._delete_material_related_data(existing_relation)

        same_name_material = self.db.get_course_material_by_course(course_id, upload_file_name)
        if same_name_material and int(same_name_material["material_object_id"]) != material_object_id:
            if not force:
                raise Exception(
                    f"课程 {course_id} 已存在同名资料 {upload_file_name}，使用 --force 覆盖该资料关系"
                )
            self._delete_material_related_data(same_name_material)

        course_material_id = self.db.create_course_material(
            course_id=course_id,
            material_object_id=material_object_id,
            display_name=upload_file_name,
            material_type="textbook",
        )

        self.db.add_log(course_material_id, f"课程资料接入成功: {upload_file_name}")
        self.logger.info(f"课程资料接入成功! material_id: {course_material_id}")

        return {
            "status": "success",
            "course_material_id": course_material_id,
            "file_id": course_material_id,
            "material_object_id": material_object_id,
            "course_id": course_id,
            "display_name": upload_file_name,
            "file_name": upload_file_name,
            "md5": file_md5,
            "size": file_size,
        }
```

Rename `_delete_pdf_related_data` to `_delete_material_related_data` and keep an alias:

```python
    def _delete_material_related_data(self, course_material: Dict[str, Any]) -> None:
        """删除某一门课程下的资料关系、解析产物和 GraphRAG 导出，不删除共享原始资料对象。"""
        course_id = course_material["course_id"]
        material_id = int(course_material["id"])
        # 兼容期同时清理旧 pdf_{id} 与新 material_{id} 路径；待 GraphRAG 同步入口完全切到 material_{id} 后可删除旧路径。
        self.storage.delete_artifacts(course_id, f"pdf_{material_id}")
        self.storage.delete_artifacts(course_id, f"graphrag/pdf_{material_id}")
        self.storage.delete_artifacts(course_id, f"material_{material_id}")
        self.storage.delete_artifacts(course_id, f"graphrag/material_{material_id}")
        self.db.delete_course_material(material_id)

    def _delete_pdf_related_data(self, pdf_file: Dict[str, Any]) -> None:
        """兼容旧命名：删除课程资料关系。"""
        self._delete_material_related_data(pdf_file)
```

- [x] **Step 4: Update resolve and parse paths**

Update `_resolve_pdf_file` to delegate to new course material queries but keep old parameter names:

```python
    def _resolve_pdf_file(
        self,
        course_id: str,
        file_id: Optional[int] = None,
        file_name: Optional[str] = None,
        material_id: Optional[int] = None,
        material_name: Optional[str] = None,
    ) -> Dict[str, Any]:
        """解析课程下要操作的具体课程资料，保留 file 参数兼容旧 CLI。"""
        resolved_id = material_id if material_id is not None else file_id
        resolved_name = material_name if material_name is not None else file_name
        if resolved_id is not None:
            material = self.db.get_course_material_by_id(resolved_id)
            if not material or material["course_id"] != course_id:
                raise Exception(f"课程 {course_id} 下不存在 material_id={resolved_id} 的资料")
            return material
        if resolved_name:
            material = self.db.get_course_material_by_course(course_id, resolved_name)
            if not material:
                raise Exception(f"课程 {course_id} 下不存在资料: {resolved_name}")
            return material
        materials = self.db.get_course_materials_by_course(course_id)
        if not materials:
            raise Exception(f"课程 {course_id} 没有接入的资料")
        if len(materials) == 1:
            return materials[0]
        choices = ", ".join(f"{row['id']}:{row['display_name']}" for row in materials[:10])
        raise Exception(
            "课程下存在多份资料，请使用 --material-id 或 --material-name 指定。"
            f" 兼容参数 --file-id/--file-name 仍可使用。可选资料: {choices}"
        )
```

In `parse()`, download by physical object:

```python
            source_file_name = pdf_file["display_name"]
            temp_pdf = self.config.get_temp_path(course_id, source_file_name)
            self.logger.info("从MinIO下载资料物理对象...")
            self.storage.download_pdf_object(
                pdf_file["minio_bucket"],
                pdf_file["minio_object_key"],
                str(temp_pdf),
            )
```

Keep return aliases:

```python
                "course_material_id": resolved_file_id,
                "file_id": resolved_file_id,
                "display_name": pdf_file["display_name"],
                "file_name": pdf_file["display_name"],
```

- [x] **Step 5: Add CLI arguments for material names**

For `parse`, `status`, `download`, and `export-graphrag` subcommands, add:

```python
    parse_parser.add_argument("--material-id", type=int, help="指定要处理的课程资料ID")
    parse_parser.add_argument("--material-name", help="指定要处理的课程资料展示名")
    status_parser.add_argument("--material-id", type=int, help="指定要查询的课程资料ID")
    status_parser.add_argument("--material-name", help="指定要查询的课程资料展示名")
    download_parser.add_argument("--material-id", type=int, help="指定要下载解析结果的课程资料ID")
    download_parser.add_argument("--material-name", help="指定要下载解析结果的课程资料展示名")
    export_parser.add_argument("--material-id", type=int, help="指定要导出的课程资料ID")
    export_parser.add_argument("--material-name", help="指定要导出的课程资料展示名")
```

When invoking app methods, pass both aliases for every affected subcommand:

```python
            result = app.parse(
                args.course_id,
                file_id=args.file_id,
                file_name=args.file_name,
                material_id=args.material_id,
                material_name=args.material_name,
            )

            result = app.status(
                args.course_id,
                file_id=args.file_id,
                file_name=args.file_name,
                material_id=args.material_id,
                material_name=args.material_name,
            )

            result = app.download(
                args.course_id,
                output_dir=args.output,
                file_id=args.file_id,
                file_name=args.file_name,
                material_id=args.material_id,
                material_name=args.material_name,
            )

            result = app.export_graphrag(
                args.course_id,
                mode=args.mode,
                file_id=args.file_id,
                file_name=args.file_name,
                material_id=args.material_id,
                material_name=args.material_name,
            )
```

Update method signatures for `parse`, `status`, `download`, and `export_graphrag` to accept `material_id` and `material_name`, then pass them to `_resolve_pdf_file`.

- [x] **Step 6: Update GraphRAG exporter metadata and DB calls**

In `pdf_ingest/scripts/pdf_processor/graphrag_exporter.py`, treat the incoming dict as course material:

```python
        course_id: str = pdf_file["course_id"]
        file_id: int = pdf_file["id"]
        source_file: str = pdf_file.get("display_name") or pdf_file.get("file_name")
```

When building metadata in normalized/projected documents, add:

```python
                "course_material_id": file_id,
                "pdf_file_id": file_id,
```

Keep persisted paths as `graphrag/pdf_{file_id}/{out_name}` for this phase so `graphrag_pipeline` remains compatible.

- [x] **Step 7: Run Python targeted tests**

Run:

```bash
cd /home/sunlight/Projects/ckqa/pdf_ingest
python -m pytest tests/test_course_material_reuse.py tests/test_normalization_baseline.py -q
python -m py_compile scripts/pdf_processor/mineru_parser.py scripts/pdf_processor/db_service.py scripts/pdf_processor/storage_service.py scripts/pdf_processor/graphrag_exporter.py
```

Expected: PASS.

- [ ] **Step 8: Commit Python CLI task**

```bash
git add pdf_ingest/scripts/pdf_processor/mineru_parser.py \
  pdf_ingest/scripts/pdf_processor/graphrag_exporter.py \
  pdf_ingest/tests/test_course_material_reuse.py
git commit -m "feat: support reusable materials in pdf ingest cli"
```

### Task 4: GraphRAG MinIO Fetch Compatibility

**Files:**
- Modify: `graphrag_pipeline/utils/fetch_from_minio.py`
- Create: `graphrag_pipeline/tests/test_fetch_from_minio_paths.py`

- [x] **Step 1: Write failing argument and path tests**

Create `graphrag_pipeline/tests/test_fetch_from_minio_paths.py`:

```python
from pathlib import Path
import sys


MODULE_DIR = Path(__file__).resolve().parents[1] / "utils"
sys.path.insert(0, str(MODULE_DIR))

import fetch_from_minio  # noqa: E402


def test_material_id_prefers_material_namespace_then_legacy_pdf_namespace():
    keys = fetch_from_minio.build_candidate_object_keys(
        course_id="os",
        graphrag_prefix="graphrag",
        filename="section_docs.json",
        pdf_file_id=None,
        material_id=7,
    )

    assert keys == [
        "os/graphrag/material_7/section_docs.json",
        "os/graphrag/pdf_7/section_docs.json",
    ]


def test_pdf_file_id_keeps_legacy_pdf_namespace_only():
    keys = fetch_from_minio.build_candidate_object_keys(
        course_id="os",
        graphrag_prefix="graphrag",
        filename="section_docs.json",
        pdf_file_id=7,
        material_id=None,
    )

    assert keys == ["os/graphrag/pdf_7/section_docs.json"]
```

- [ ] **Step 2: Run test to verify it fails**

Run:

```bash
cd /home/sunlight/Projects/ckqa/graphrag_pipeline
conda run -n graphrag-oneapi python -m pytest tests/test_fetch_from_minio_paths.py -q
```

Expected: FAIL because `build_candidate_object_keys` and `--material-id` do not exist.

- [x] **Step 3: Add key builder and material argument**

In `graphrag_pipeline/utils/fetch_from_minio.py`, add:

```python
def build_candidate_object_keys(
    course_id: str,
    graphrag_prefix: str,
    filename: str,
    pdf_file_id: int | None = None,
    material_id: int | None = None,
) -> list[str]:
    """构建 GraphRAG 输入候选路径；新 material_{id} 优先，旧 pdf_{id} 保留兼容。"""
    if material_id is not None:
        return [
            f"{course_id}/{graphrag_prefix}/material_{material_id}/{filename}",
            f"{course_id}/{graphrag_prefix}/pdf_{material_id}/{filename}",
        ]
    if pdf_file_id is not None:
        return [f"{course_id}/{graphrag_prefix}/pdf_{pdf_file_id}/{filename}"]
    return [f"{course_id}/{graphrag_prefix}/{filename}"]
```

Update `fetch_and_prepare()` signature to accept `material_id`:

```python
def fetch_and_prepare(
    course_id: str,
    input_dir: Path,
    clean: bool = False,
    graphrag_prefix: str = "graphrag",
    json_filename: str = "section_docs.json",
    pdf_file_id: int | None = None,
    material_id: int | None = None,
) -> dict:
```

Then replace the existing block from `if pdf_file_id is not None:` through the final `if source_format is None:` not-found return with this full block. Keep the existing filename-normalization code above it and the existing input parsing code below it.

```python
    preferred_keys = build_candidate_object_keys(
        course_id,
        graphrag_prefix,
        preferred_filename,
        pdf_file_id=pdf_file_id,
        material_id=material_id,
    )
    legacy_keys = build_candidate_object_keys(
        course_id,
        graphrag_prefix,
        legacy_filename,
        pdf_file_id=pdf_file_id,
        material_id=material_id,
    )

    print(f"[MinIO] 候选 GraphRAG 输入路径: {preferred_keys + legacy_keys}")

    # 1) 下载源文件到临时目录
    tmp_dir = Path(tempfile.mkdtemp(prefix=f"graphrag_fetch_{course_id}_"))
    source_format = None
    tmp_input = tmp_dir / preferred_filename

    for filename, candidate_keys, candidate_format in (
        (preferred_filename, preferred_keys, "json"),
        (legacy_filename, legacy_keys, "jsonl"),
    ):
        tmp_input = tmp_dir / filename
        for object_key in dict.fromkeys(candidate_keys):
            print(f"[MinIO] 尝试下载 {bucket}/{object_key} ...")
            if _download_object(client, bucket, object_key, tmp_input):
                source_format = candidate_format
                print(f"[MinIO] 下载完成: {tmp_input} ({tmp_input.stat().st_size} bytes)")
                break
        if source_format is not None:
            break

    if source_format is None and pdf_file_id is None and material_id is None:
        namespaced_key, ambiguous = _find_unique_namespaced_key(
            client, bucket, course_id, graphrag_prefix, preferred_filename
        )
        if ambiguous:
            shutil.rmtree(tmp_dir, ignore_errors=True)
            print(
                f"[错误] 课程 {course_id} 下存在多份 GraphRAG 输入，请使用 --material-id 或 --pdf-file-id 指定。",
                file=sys.stderr,
            )
            return {"status": "ambiguous", "course_id": course_id}
        if namespaced_key:
            tmp_input = tmp_dir / preferred_filename
            if _download_object(client, bucket, namespaced_key, tmp_input):
                source_format = "json"
                print(f"[MinIO] 在 namespaced 路径找到文件: {namespaced_key}")
                print(f"[MinIO] 下载完成: {tmp_input} ({tmp_input.stat().st_size} bytes)")

    if source_format is None and pdf_file_id is None and material_id is None:
        namespaced_key, ambiguous = _find_unique_namespaced_key(
            client, bucket, course_id, graphrag_prefix, legacy_filename
        )
        if ambiguous:
            shutil.rmtree(tmp_dir, ignore_errors=True)
            print(
                f"[错误] 课程 {course_id} 下存在多份 GraphRAG 历史输入，请使用 --material-id 或 --pdf-file-id 指定。",
                file=sys.stderr,
            )
            return {"status": "ambiguous", "course_id": course_id}
        if namespaced_key:
            tmp_input = tmp_dir / legacy_filename
            if _download_object(client, bucket, namespaced_key, tmp_input):
                source_format = "jsonl"
                print(f"[MinIO] 在 namespaced 路径找到历史文件: {namespaced_key}")
                print(f"[MinIO] 下载完成: {tmp_input} ({tmp_input.stat().st_size} bytes)")

    if source_format is None:
        shutil.rmtree(tmp_dir, ignore_errors=True)
        searched = ", ".join(dict.fromkeys(preferred_keys + legacy_keys))
        print(
            f"[错误] MinIO 中未找到候选文件: {searched}。"
            f"请先在 pdf_ingest 中运行 export-graphrag 命令。",
            file=sys.stderr,
        )
        return {"status": "not_found", "course_id": course_id}
```

Update `_find_unique_namespaced_key()` so automatic discovery recognizes both namespace styles. Replace this original context:

```python
        if not object_name.endswith(f"/{filename}"):
            continue
        if "/pdf_" not in object_name:
            continue
        matches.append(object_name)
```

with:

```python
        if not object_name.endswith(f"/{filename}"):
            continue
        if "/material_" not in object_name and "/pdf_" not in object_name:
            continue
        matches.append(object_name)
```

Add CLI arg:

```python
    parser.add_argument("--material-id", type=int, help="课程资料ID；优先读取 material_{id}，失败后兼容 pdf_{id}")
```

When calling `fetch_and_prepare`, pass:

```python
        pdf_file_id=args.pdf_file_id,
        material_id=args.material_id,
```

- [x] **Step 4: Run GraphRAG fetch tests**

Run:

```bash
cd /home/sunlight/Projects/ckqa/graphrag_pipeline
conda run -n graphrag-oneapi python -m pytest tests/test_fetch_from_minio_paths.py -q
```

Expected: PASS.

- [ ] **Step 5: Commit GraphRAG compatibility**

```bash
git add graphrag_pipeline/utils/fetch_from_minio.py graphrag_pipeline/tests/test_fetch_from_minio_paths.py
git commit -m "feat: accept course material ids for graphrag input fetch"
```

### Task 5: Java Entity And Service Migration

**Files:**
- Modify: `backend/ckqa-back/src/test/java/org/ysu/ckqaback/support/codegen/MybatisPlusCodeGenerator.java`
- Create: `backend/ckqa-back/src/main/java/org/ysu/ckqaback/entity/MaterialObjects.java`
- Create: `backend/ckqa-back/src/main/java/org/ysu/ckqaback/entity/CourseMaterials.java`
- Modify: `backend/ckqa-back/src/main/java/org/ysu/ckqaback/entity/ParseResults.java`
- Modify: `backend/ckqa-back/src/main/java/org/ysu/ckqaback/entity/ParseLogs.java`
- Create: `backend/ckqa-back/src/main/java/org/ysu/ckqaback/mapper/MaterialObjectsMapper.java`
- Create: `backend/ckqa-back/src/main/java/org/ysu/ckqaback/mapper/CourseMaterialsMapper.java`
- Create: `backend/ckqa-back/src/main/java/org/ysu/ckqaback/service/MaterialObjectsService.java`
- Create: `backend/ckqa-back/src/main/java/org/ysu/ckqaback/service/CourseMaterialsService.java`
- Create: `backend/ckqa-back/src/main/java/org/ysu/ckqaback/service/impl/MaterialObjectsServiceImpl.java`
- Create: `backend/ckqa-back/src/main/java/org/ysu/ckqaback/service/impl/CourseMaterialsServiceImpl.java`

- [x] **Step 1: Create Java entities and mappers**

Create `backend/ckqa-back/src/main/java/org/ysu/ckqaback/entity/MaterialObjects.java`:

```java
package org.ysu.ckqaback.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 资料物理对象表。
 */
@Getter
@Setter
@ToString
@TableName("material_objects")
public class MaterialObjects implements Serializable {

    private static final long serialVersionUID = 1L;

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    @TableField("original_file_name")
    private String originalFileName;

    @TableField("file_md5")
    private String fileMd5;

    @TableField("file_size")
    private Long fileSize;

    @TableField("mime_type")
    private String mimeType;

    @TableField("minio_bucket")
    private String minioBucket;

    @TableField("minio_object_key")
    private String minioObjectKey;

    @TableField("created_at")
    private LocalDateTime createdAt;

    @TableField("updated_at")
    private LocalDateTime updatedAt;
}
```

Create `backend/ckqa-back/src/main/java/org/ysu/ckqaback/entity/CourseMaterials.java`:

```java
package org.ysu.ckqaback.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 课程资料关联表。
 */
@Getter
@Setter
@ToString
@TableName("course_materials")
public class CourseMaterials implements Serializable {

    private static final long serialVersionUID = 1L;

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    @TableField("course_id")
    private String courseId;

    @TableField("material_object_id")
    private Long materialObjectId;

    @TableField("display_name")
    private String displayName;

    @TableField("material_type")
    private String materialType;

    @TableField("parse_status")
    private String parseStatus;

    @TableField("parse_started_at")
    private LocalDateTime parseStartedAt;

    @TableField("parse_finished_at")
    private LocalDateTime parseFinishedAt;

    @TableField("parse_error_msg")
    private String parseErrorMsg;

    @TableField("mineru_batch_id")
    private String mineruBatchId;

    @TableField("upload_time")
    private LocalDateTime uploadTime;

    @TableField("created_at")
    private LocalDateTime createdAt;

    @TableField("updated_at")
    private LocalDateTime updatedAt;
}
```

Create `MaterialObjectsMapper.java` and `CourseMaterialsMapper.java`:

```java
package org.ysu.ckqaback.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.ysu.ckqaback.entity.MaterialObjects;

/**
 * 资料物理对象 Mapper。
 */
@Mapper
public interface MaterialObjectsMapper extends BaseMapper<MaterialObjects> {
}
```

```java
package org.ysu.ckqaback.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.ysu.ckqaback.entity.CourseMaterials;

/**
 * 课程资料 Mapper。
 */
@Mapper
public interface CourseMaterialsMapper extends BaseMapper<CourseMaterials> {
}
```

- [x] **Step 2: Create Java services**

Create `CourseMaterialsService.java`:

```java
package org.ysu.ckqaback.service;

import com.baomidou.mybatisplus.extension.service.IService;
import org.ysu.ckqaback.entity.CourseMaterials;

import java.util.List;

/**
 * 课程资料服务。
 */
public interface CourseMaterialsService extends IService<CourseMaterials> {

    CourseMaterials getRequiredById(Long id);

    boolean claimParseStart(Long id);

    boolean markParseFailedIfStillProcessing(Long id, String errorMessage);

    List<CourseMaterials> listByCourseId(String courseId);
}
```

Create `CourseMaterialsServiceImpl.java`:

```java
package org.ysu.ckqaback.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.ysu.ckqaback.api.ApiResultCode;
import org.ysu.ckqaback.entity.CourseMaterials;
import org.ysu.ckqaback.exception.BusinessException;
import org.ysu.ckqaback.mapper.CourseMaterialsMapper;
import org.ysu.ckqaback.service.CourseMaterialsService;

import java.util.List;

/**
 * 课程资料服务实现。
 */
@Service
public class CourseMaterialsServiceImpl extends ServiceImpl<CourseMaterialsMapper, CourseMaterials> implements CourseMaterialsService {

    @Override
    public CourseMaterials getRequiredById(Long id) {
        CourseMaterials material = getById(id);
        if (material == null) {
            throw new BusinessException(ApiResultCode.PDF_FILE_NOT_FOUND, HttpStatus.NOT_FOUND);
        }
        return material;
    }

    @Override
    public boolean claimParseStart(Long id) {
        LambdaUpdateWrapper<CourseMaterials> wrapper = new LambdaUpdateWrapper<>();
        wrapper.eq(CourseMaterials::getId, id)
                .in(CourseMaterials::getParseStatus, "pending", "failed")
                .set(CourseMaterials::getParseStatus, "processing")
                .setSql("parse_started_at = NOW(), parse_finished_at = NULL, parse_error_msg = NULL");
        return baseMapper.update(null, wrapper) == 1;
    }

    @Override
    public boolean markParseFailedIfStillProcessing(Long id, String errorMessage) {
        LambdaUpdateWrapper<CourseMaterials> wrapper = new LambdaUpdateWrapper<>();
        wrapper.eq(CourseMaterials::getId, id)
                .eq(CourseMaterials::getParseStatus, "processing")
                .set(CourseMaterials::getParseStatus, "failed")
                .setSql("parse_finished_at = NOW()")
                .set(CourseMaterials::getParseErrorMsg, truncateByCodePoints(errorMessage, 500));
        return baseMapper.update(null, wrapper) == 1;
    }

    private String truncateByCodePoints(String value, int maxCodePoints) {
        if (value == null || value.codePointCount(0, value.length()) <= maxCodePoints) {
            return value;
        }
        return value.substring(0, value.offsetByCodePoints(0, maxCodePoints));
    }

    @Override
    public List<CourseMaterials> listByCourseId(String courseId) {
        LambdaQueryWrapper<CourseMaterials> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(StringUtils.hasText(courseId), CourseMaterials::getCourseId, courseId)
                .orderByDesc(CourseMaterials::getUploadTime)
                .orderByDesc(CourseMaterials::getCreatedAt);
        return list(queryWrapper);
    }
}
```

Create `MaterialObjectsService.java` and `MaterialObjectsServiceImpl.java` with MyBatis-Plus default service:

```java
package org.ysu.ckqaback.service;

import com.baomidou.mybatisplus.extension.service.IService;
import org.ysu.ckqaback.entity.MaterialObjects;

/**
 * 资料物理对象服务。
 */
public interface MaterialObjectsService extends IService<MaterialObjects> {
}
```

```java
package org.ysu.ckqaback.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.springframework.stereotype.Service;
import org.ysu.ckqaback.entity.MaterialObjects;
import org.ysu.ckqaback.mapper.MaterialObjectsMapper;
import org.ysu.ckqaback.service.MaterialObjectsService;

/**
 * 资料物理对象服务实现。
 */
@Service
public class MaterialObjectsServiceImpl extends ServiceImpl<MaterialObjectsMapper, MaterialObjects> implements MaterialObjectsService {
}
```

- [x] **Step 3: Update Parse entity fields**

In `ParseResults.java`, rename `pdfFileId` field to:

```java
    /**
     * 关联的课程资料ID
     */
    @TableField("course_material_id")
    private Long courseMaterialId;
```

In `ParseLogs.java`, rename the same field to `courseMaterialId` with `@TableField("course_material_id")`.

- [x] **Step 4: Update code generator table list**

In `MybatisPlusCodeGenerator.java`, replace `"pdf_files"` with:

```java
            "material_objects",
            "course_materials",
```

- [x] **Step 5: Run Java compile target**

Run:

```bash
cd /home/sunlight/Projects/ckqa/backend/ckqa-back
./mvnw -q -DskipTests compile
```

Expected: FAIL until workflow, DTO, and tests are migrated in Task 6. Do not modify `PdfWorkflowServiceTest.java` in Task 5.

- [ ] **Step 6: Commit Java model groundwork**

```bash
git add backend/ckqa-back/src/main/java/org/ysu/ckqaback/entity/MaterialObjects.java \
  backend/ckqa-back/src/main/java/org/ysu/ckqaback/entity/CourseMaterials.java \
  backend/ckqa-back/src/main/java/org/ysu/ckqaback/entity/ParseResults.java \
  backend/ckqa-back/src/main/java/org/ysu/ckqaback/entity/ParseLogs.java \
  backend/ckqa-back/src/main/java/org/ysu/ckqaback/mapper/MaterialObjectsMapper.java \
  backend/ckqa-back/src/main/java/org/ysu/ckqaback/mapper/CourseMaterialsMapper.java \
  backend/ckqa-back/src/main/java/org/ysu/ckqaback/service/MaterialObjectsService.java \
  backend/ckqa-back/src/main/java/org/ysu/ckqaback/service/CourseMaterialsService.java \
  backend/ckqa-back/src/main/java/org/ysu/ckqaback/service/impl/MaterialObjectsServiceImpl.java \
  backend/ckqa-back/src/main/java/org/ysu/ckqaback/service/impl/CourseMaterialsServiceImpl.java \
  backend/ckqa-back/src/test/java/org/ysu/ckqaback/support/codegen/MybatisPlusCodeGenerator.java
git commit -m "feat: add course material java model"
```

### Task 6: Java Workflow And Legacy API Compatibility

**Files:**
- Modify: `backend/ckqa-back/src/main/java/org/ysu/ckqaback/pdf/PdfWorkflowService.java`
- Modify: `backend/ckqa-back/src/main/java/org/ysu/ckqaback/integration/pdf/PdfIngestOrchestrator.java`
- Modify: `backend/ckqa-back/src/main/java/org/ysu/ckqaback/pdf/dto/PdfFileResponse.java`
- Modify: `backend/ckqa-back/src/main/java/org/ysu/ckqaback/pdf/dto/ParseResultResponse.java`
- Modify: `backend/ckqa-back/src/main/java/org/ysu/ckqaback/course/CourseLookupService.java`
- Modify: `backend/ckqa-back/src/main/java/org/ysu/ckqaback/course/dto/CoursePdfFileSummaryResponse.java`
- Modify: `backend/ckqa-back/src/test/java/org/ysu/ckqaback/pdf/PdfWorkflowServiceTest.java`
- Modify: `backend/ckqa-back/src/test/java/org/ysu/ckqaback/controller/PdfFilesControllerWebMvcTest.java`
- Modify: `backend/ckqa-back/src/test/java/org/ysu/ckqaback/controller/CoursesControllerWebMvcTest.java`

- [x] **Step 1: Update workflow service tests**

Modify `backend/ckqa-back/src/test/java/org/ysu/ckqaback/pdf/PdfWorkflowServiceTest.java` imports and mocks from `PdfFiles`/`PdfFilesService` to `CourseMaterials`/`CourseMaterialsService`. The first test setup becomes:

```java
CourseMaterialsService courseMaterialsService = mock(CourseMaterialsService.class);
ParseResultsService parseResultsService = mock(ParseResultsService.class);
PdfIngestOrchestrator orchestrator = mock(PdfIngestOrchestrator.class);
DatabaseNamedLockService databaseNamedLockService = mock(DatabaseNamedLockService.class);

PdfWorkflowService workflowService = new PdfWorkflowService(courseMaterialsService, parseResultsService, orchestrator, databaseNamedLockService);
CourseMaterials material = new CourseMaterials();
material.setId(7L);
material.setCourseId("os");
material.setDisplayName("book.pdf");
material.setParseStatus("processing");

given(courseMaterialsService.getRequiredById(7L)).willReturn(material);
given(courseMaterialsService.claimParseStart(7L)).willReturn(false);
```

Update expectations to call:

```java
then(courseMaterialsService).should().markParseFailedIfStillProcessing(7L, "spawn failed");
```

- [ ] **Step 2: Run Java workflow test to verify it fails**

Run:

```bash
cd /home/sunlight/Projects/ckqa/backend/ckqa-back
./mvnw -q -Dtest=PdfWorkflowServiceTest test
```

Expected: FAIL because `PdfWorkflowService`, DTO mapping, and orchestrator method signatures still use `PdfFiles`.

- [x] **Step 3: Update workflow service implementation**

In `PdfWorkflowService.java`, replace `PdfFilesService` and `PdfFiles` with `CourseMaterialsService` and `CourseMaterials`. Constructor field becomes:

```java
    private final CourseMaterialsService courseMaterialsService;
```

`startParse` uses:

```java
        CourseMaterials material = courseMaterialsService.getRequiredById(pdfFileId);
        if (!courseMaterialsService.claimParseStart(pdfFileId)) {
            throw new BusinessException(ApiResultCode.PDF_PARSE_STATE_CONFLICT, HttpStatus.CONFLICT);
        }
```

Failure fallback uses:

```java
            courseMaterialsService.markParseFailedIfStillProcessing(pdfFileId, ex.getMessage());
```

Success response uses:

```java
        CourseMaterials refreshed = courseMaterialsService.getRequiredById(pdfFileId);
        return PdfOperationResponse.success(
                refreshed.getId(),
                refreshed.getCourseId(),
                refreshed.getDisplayName(),
                refreshed.getParseStatus(),
                "解析任务已启动"
        );
```

`exportGraphRag` uses `CourseMaterials material = courseMaterialsService.getRequiredById(pdfFileId);`.

- [x] **Step 4: Update orchestrator command**

In `PdfIngestOrchestrator.java`, replace method parameters with `CourseMaterials material`, and use `--material-id`:

```java
    public ProcessExecutionResult parse(CourseMaterials material) throws IOException, InterruptedException {
        List<String> command = List.of(
                properties.getPdfIngest().getPython(),
                "scripts/pdf_processor/mineru_parser.py",
                "parse",
                material.getCourseId(),
                "--material-id",
                String.valueOf(material.getId())
        );
        return processRunner.run(
                command,
                Path.of(properties.getPdfIngest().getRoot()),
                Map.of(),
                Duration.ofSeconds(properties.getTimeout().getParseSeconds()),
                ProcessContext.builder()
                        .operation("parse")
                        .pdfFileId(material.getId())
                        .build()
        );
    }
```

Use the same `--material-id` replacement in `exportGraphRag`.

- [x] **Step 5: Update DTO compatibility**

In `PdfFileResponse.java`, switch `fromEntity` to `CourseMaterials` and add fields:

```java
    private final Long materialId;
    private final Long materialObjectId;
```

Factory should set both legacy `id` and new `materialId` to `material.getId()`. `fileName` should use `material.getDisplayName()`.

In `ParseResultResponse.java`, expose both fields:

```java
    private final Long courseMaterialId;
    private final Long pdfFileId;
```

`fromEntity` maps both to `parseResult.getCourseMaterialId()`.

In `CoursePdfFileSummaryResponse.java`, add:

```java
    private final Long materialId;
    private final Long materialObjectId;
```

Keep `id` equal to `materialId` for legacy API clients.

- [x] **Step 6: Update course lookup**

In `CourseLookupService.java`, replace `PdfFilesService` with `CourseMaterialsService`:

```java
    private final CourseMaterialsService courseMaterialsService;

    public List<CoursePdfFileSummaryResponse> listCoursePdfFiles(String courseId) {
        return courseMaterialsService.listByCourseId(courseId).stream()
                .map(CoursePdfFileSummaryResponse::fromEntity)
                .toList();
    }
```

- [x] **Step 7: Run focused Java tests**

Run:

```bash
cd /home/sunlight/Projects/ckqa/backend/ckqa-back
./mvnw -q -Dtest=PdfWorkflowServiceTest,PdfFilesControllerWebMvcTest,CoursesControllerWebMvcTest test
```

Expected: PASS after updating tests to new entity/service while retaining old route names.

- [x] **Step 8: Compile Java backend**

Run:

```bash
cd /home/sunlight/Projects/ckqa/backend/ckqa-back
./mvnw -q -DskipTests compile
```

Expected: PASS.

- [ ] **Step 9: Commit Java workflow compatibility**

```bash
git add backend/ckqa-back/src/main/java/org/ysu/ckqaback/pdf/PdfWorkflowService.java \
  backend/ckqa-back/src/main/java/org/ysu/ckqaback/integration/pdf/PdfIngestOrchestrator.java \
  backend/ckqa-back/src/main/java/org/ysu/ckqaback/pdf/dto/PdfFileResponse.java \
  backend/ckqa-back/src/main/java/org/ysu/ckqaback/pdf/dto/ParseResultResponse.java \
  backend/ckqa-back/src/main/java/org/ysu/ckqaback/course/CourseLookupService.java \
  backend/ckqa-back/src/main/java/org/ysu/ckqaback/course/dto/CoursePdfFileSummaryResponse.java \
  backend/ckqa-back/src/test/java/org/ysu/ckqaback/pdf/PdfWorkflowServiceTest.java \
  backend/ckqa-back/src/test/java/org/ysu/ckqaback/controller/PdfFilesControllerWebMvcTest.java \
  backend/ckqa-back/src/test/java/org/ysu/ckqaback/controller/CoursesControllerWebMvcTest.java
git commit -m "feat: route pdf workflow through course materials"
```

### Task 7: Documentation And Command Drift Cleanup

**Files:**
- Modify: `README.md`
- Modify: `pdf_ingest/README.md`
- Modify: `pdf_ingest/CLAUDE.md`
- Modify: `pdf_ingest/docs/MinerU PDF Parser.md`
- Modify: `backend/ckqa-back/README.md`

Note: `scripts/audit_repo_drift.py` already exists at the repository root. This task only runs it and fixes any flagged documentation drift; do not create a second drift-audit script.

- [x] **Step 1: Update root README main flow**

In `README.md`, replace the main-flow line:

```text
-> pdf_ingest 上传到 MinIO，登记到 MySQL
```

with:

```text
-> pdf_ingest 将原始资料对象按 MD5 存入 MinIO/material_objects，并在 course_materials 中登记课程资料关系
```

- [x] **Step 2: Update pdf_ingest guidance**

In `pdf_ingest/CLAUDE.md`, replace the architecture relationship:

```text
courses (1) ──→ (N) pdf_files (1) ──→ (N) parse_results
```

with:

```text
courses (1) ──→ (N) course_materials (N) ──→ (1) material_objects
course_materials (1) ──→ (N) parse_results / parse_logs
```

Replace the old MD5 note with:

```text
- `material_objects.file_md5` enables physical object deduplication.
- `course_materials` allows the same physical material object to be reused by multiple courses.
- Parse status belongs to `course_materials`, not to the shared physical object.
```

- [x] **Step 3: Update detailed MinerU parser doc**

In `pdf_ingest/docs/MinerU PDF Parser.md`, replace all statements that say identical content cannot belong to multiple courses with:

```text
当前版本支持同一份资料被多个课程复用。系统通过 `material_objects.file_md5` 对 MinIO 原始对象去重，并通过 `course_materials` 维护每门课程自己的资料引用、解析状态和导出产物。
```

Update command examples to mention both compatibility and new preferred options:

```bash
python scripts/pdf_processor/mineru_parser.py parse os --material-id 3
python scripts/pdf_processor/mineru_parser.py status os --material-id 3
python scripts/pdf_processor/mineru_parser.py export-graphrag os --material-id 3 --mode section
```

- [x] **Step 4: Update backend README compatibility boundary**

In `backend/ckqa-back/README.md`, add under "当前已知边界":

```text
- `/api/v1/pdf-files` 当前作为兼容路由保留，内部数据源已经演进为 `course_materials`。新业务文档中应优先使用“课程资料”语义，后续前端稳定后再新增或切换到 `/api/v1/course-materials`。
```

- [x] **Step 5: Run documentation contract and drift audit**

Run:

```bash
cd /home/sunlight/Projects/ckqa/pdf_ingest
python -m pytest tests/test_ocqa_docs_contract.py -q
cd /home/sunlight/Projects/ckqa
python scripts/audit_repo_drift.py --strict
```

Expected: PASS. If `audit_repo_drift.py` flags old `pdf_files.file_md5` limitations, update the flagged docs in the same task.

- [ ] **Step 6: Commit docs**

```bash
git add README.md \
  pdf_ingest/README.md \
  pdf_ingest/CLAUDE.md \
  "pdf_ingest/docs/MinerU PDF Parser.md" \
  backend/ckqa-back/README.md
git commit -m "docs: describe reusable course materials model"
```

### Task 8: Full Regression And Live Migration Smoke

**Files:**
- No required code edits unless a regression fails.

已确认的非 live 证据：
- `cd pdf_ingest && conda run -n courseKg python -m pytest tests/` 通过
- `cd graphrag_pipeline && conda run -n graphrag-oneapi python -m pytest tests/` 通过
- `cd backend/ckqa-back && ./mvnw -q test` 通过
- `cd backend/ckqa-back && ./mvnw -q -DskipTests compile` 通过
- `cd /home/sunlight/Projects/ckqa && python scripts/audit_repo_drift.py --strict` 通过

- [x] **Step 1: Run full pdf_ingest tests**

Run:

```bash
cd /home/sunlight/Projects/ckqa/pdf_ingest
python -m pytest tests/
```

Expected: all tests PASS.

- [x] **Step 2: Run GraphRAG tests**

Run:

```bash
cd /home/sunlight/Projects/ckqa/graphrag_pipeline
conda run -n graphrag-oneapi python -m pytest tests/
```

Expected: all tests PASS.

- [x] **Step 3: Run Java tests and compile**

Run:

```bash
cd /home/sunlight/Projects/ckqa/backend/ckqa-back
./mvnw -q test
./mvnw -q -DskipTests compile
```

Expected: both commands PASS.

- [x] **Step 4: Run SQL migration smoke on disposable schema when MySQL is available**

Run this only against a disposable or backed-up database:

```sql
SELECT TABLE_NAME, COLUMN_NAME, CONSTRAINT_NAME
FROM information_schema.KEY_COLUMN_USAGE
WHERE TABLE_SCHEMA = DATABASE()
  AND TABLE_NAME IN ('parse_results', 'parse_logs')
  AND COLUMN_NAME = 'pdf_file_id'
  AND REFERENCED_TABLE_NAME IS NOT NULL;
```

Expected before migration: either no rows, or only known old constraints such as `fk_parse_results_pdf_file` and `fk_parse_logs_pdf_file`. If other FK names appear on `pdf_file_id`, add them to the old-FK drop list before running it on a real database; do not add new FK constraints for the material schema.

```bash
mysql -h 127.0.0.1 -P 23306 -u root -p ocqa < /home/sunlight/Projects/ckqa/pdf_ingest/sql/migrations/20260423_course_materials.sql
```

Then verify:

```sql
SHOW TABLES LIKE 'material_objects';
SHOW TABLES LIKE 'course_materials';
SELECT COUNT(*) FROM material_objects;
SELECT COUNT(*) FROM course_materials;
SELECT course_id, course_material_id, file_name, file_md5, parse_status FROM v_course_parse_overview LIMIT 5;
SELECT COLUMN_NAME, COLUMN_COMMENT
FROM information_schema.COLUMNS
WHERE TABLE_SCHEMA = DATABASE()
  AND TABLE_NAME IN ('parse_results', 'parse_logs')
  AND COLUMN_NAME = 'course_material_id'
ORDER BY TABLE_NAME;
SELECT CONSTRAINT_NAME
FROM information_schema.TABLE_CONSTRAINTS
WHERE CONSTRAINT_SCHEMA = DATABASE()
  AND CONSTRAINT_NAME IN (
    'fk_parse_results_pdf_file',
    'fk_parse_logs_pdf_file',
    'fk_course_materials_course',
    'fk_course_materials_material_object',
    'fk_parse_results_course_material',
    'fk_parse_logs_course_material'
  )
ORDER BY CONSTRAINT_NAME;
SHOW TABLE STATUS LIKE 'course_materials';
```

Expected: tables exist; old rows are represented in new tables; overview returns both `course_material_id` and legacy `file_name` fields; the `course_material_id` column comment is `关联的课程资料ID` in both parse tables, proving the stored-procedure quote escaping executed correctly; the FK query returns no rows for both old and new FK names; `course_materials.Auto_increment` is greater than `MAX(id)`.

已完成：2026-04-24 使用 disposable DB `ocqa_task8_smoke_full_20260424` 通过 `/tmp/ckqa_task8_step4_smoke.py` 跑通全量表克隆 + migration smoke，结果为：
- `pre_fk ()`
- `material_objects_count = 1`
- `course_materials_count = 1`
- `v_course_parse_overview` 返回旧行 `task8_mig / 900001 / migration-smoke.pdf`
- `parse_logs` 与 `parse_results` 的 `course_material_id` 列注释均为 `关联的课程资料ID`
- 旧 FK 名与新 FK 名检查结果均为空
- `course_materials.Auto_increment = 900002`，大于 `MAX(id) = 900001`

- [x] **Step 5: Run CLI smoke with fake or local sample when services are available**

If MinIO/MySQL/MinerU credentials are available, run:

```bash
cd /home/sunlight/Projects/ckqa/pdf_ingest
python scripts/pdf_processor/mineru_parser.py upload os -f data/os/book.pdf
python scripts/pdf_processor/mineru_parser.py upload os2 -f data/os/book.pdf
python scripts/pdf_processor/mineru_parser.py list
```

Expected:

```text
first upload: status=success
second upload: status=success with same material_object_id and different course_material_id
list: both courses show their own material row
```

已完成：2026-04-24 使用本地样本 [pdf_ingest/data/os/计算机操作系统.pdf](/home/sunlight/Projects/ckqa/pdf_ingest/data/os/计算机操作系统.pdf) 在 disposable DB `ocqa_task8_smoke_full_20260424` 上跑通 CLI smoke：
- 首次上传课程 `task8smoke_a_full_1777028024` 成功，`course_material_id = 900002`，`material_object_id = 900002`
- 第二次上传课程 `task8smoke_b_full_1777028024` 成功，`course_material_id = 900003`，`material_object_id = 900002`
- `list` 同时返回两个课程各自的资料行
- SQL 复核显示 `material_objects` 中该 MD5 仅 1 行，`course_materials` 中两个课程各有 1 行且共用同一个 `material_object_id`

- [x] **Step 6: Run Java health and legacy route smoke when services are available**

Start Java backend after MySQL migration:

```bash
cd /home/sunlight/Projects/ckqa/backend/ckqa-back
./mvnw spring-boot:run
```

In a separate shell:

```bash
curl -s http://127.0.0.1:8080/api/v1/system/health
curl -s http://127.0.0.1:8080/api/v1/courses/os/pdf-files
```

Expected:

```text
/system/health returns code=200 envelope
/courses/os/pdf-files returns course material rows through the legacy route
```

已完成：2026-04-24 在 `backend/ckqa-back` 使用 migrated disposable DB `ocqa_task8_smoke_full_20260424` 启动 `./mvnw spring-boot:run`，并验证：
- `/api/v1/system/health` 返回 `code=200` envelope，且 `mysql` 项为 `reachable=true`、`ready=true`
- `/api/v1/courses/task8smoke_a_full_1777028024/pdf-files` 返回 `code=200`，数据中包含 `id=900002`、`materialId=900002`、`materialObjectId=900002`、`fileName=计算机操作系统.pdf`
- 随后在现有 `graphrag_pipeline/output` 基础上启动 GraphRAG API 后，`/api/v1/system/health` 返回 `up=true`，`graphrag-api` 与 `graphrag-ready` 均为 `reachable=true`、`ready=true`

执行备注：
- 第一次 Java smoke 失败的原因不是 schema 缺项，而是启动命令把 `.env` 路径误写成了 `../pdf_ingest/.env`；从 `backend/ckqa-back` 启动时正确路径应为 `../../pdf_ingest/.env`
- 为避免重建现有索引与覆盖已写入 Neo4j 的图数据，本轮额外使用 `/tmp/ckqa_graphrag_python_wrapper.sh` 对 `python -m graphrag index --root .` 做 no-op smoke：`fetch_from_minio.py os --clean` 仍真实执行，索引命令仅验证可编排执行并返回 `exitCode = 0`
- no-op smoke 后 `graphrag_pipeline/output` 文件指纹保持不变：`f414e393bc30d6db35e7e6a4bc1430ff828d511290cefecdad1c148101ea80b8`

- [ ] **Step 7: Final commit after regressions**

If any regression fixes were needed, commit them:

```bash
git add <changed-files>
git commit -m "test: verify reusable course materials regression"
```

If no files changed after regression, do not create an empty commit.

## Rollback Notes

- MySQL DDL implicitly commits. The migration transaction only protects the `INSERT ... SELECT` DML copy into `material_objects` and `course_materials`; failed DDL still requires restoring the disposable/real schema from backup.
- If the DML copy fails before `COMMIT`, fix the data issue, verify the transaction rolled back on the disposable schema, and rerun the migration script after cleaning any partially created DDL objects.
- If migration fails before `DROP TABLE pdf_files`, restore from database backup and rerun after fixing DDL.
- If migration fails after `DROP TABLE pdf_files`, restore from backup; do not try to reconstruct `pdf_files` from partial new tables.
- MinIO original objects are not deleted by this plan. `force` only removes course-scoped artifacts under `course_id/pdf_{id}`, `course_id/material_{id}`, `course_id/graphrag/pdf_{id}`, and `course_id/graphrag/material_{id}`.
- Java legacy routes remain named `pdf-files` in this phase, so frontend/API consumers should not need a route change.

## Final Verification Checklist

- [x] `cd pdf_ingest && python -m pytest tests/` passes.
- [x] `cd graphrag_pipeline && conda run -n graphrag-oneapi python -m pytest tests/` passes.
- [x] `cd backend/ckqa-back && ./mvnw -q test` passes.
- [x] `cd backend/ckqa-back && ./mvnw -q -DskipTests compile` passes.
- [x] `cd /home/sunlight/Projects/ckqa && python scripts/audit_repo_drift.py --strict` passes.
- [x] If live services are available, cross-course same-PDF upload creates one `material_objects` row and multiple `course_materials` rows.
- [x] Existing Java `/api/v1/pdf-files/{id}/parse` and `/api/v1/pdf-files/{id}/export-graphrag` continue to work as compatibility routes.
