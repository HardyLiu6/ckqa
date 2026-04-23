SET NAMES utf8mb4;

-- CKQA course material reuse migration.
-- 注意：MySQL DDL 会隐式提交，下面的事务只保护旧数据复制 DML。
-- 在真实库执行前，请先在 disposable schema 上完整演练并备份生产数据。

DELIMITER $$

DROP PROCEDURE IF EXISTS `ckqa_drop_fk_if_exists` $$
CREATE PROCEDURE `ckqa_drop_fk_if_exists`(
  IN p_table_name varchar(64),
  IN p_fk_name varchar(64)
)
BEGIN
  IF EXISTS (
    SELECT 1
    FROM information_schema.TABLE_CONSTRAINTS
    WHERE CONSTRAINT_SCHEMA = DATABASE()
      AND TABLE_NAME = p_table_name
      AND CONSTRAINT_NAME = p_fk_name
      AND CONSTRAINT_TYPE = 'FOREIGN KEY'
  ) THEN
    SET @ckqa_sql = CONCAT('ALTER TABLE `', p_table_name, '` DROP FOREIGN KEY `', p_fk_name, '`');
    PREPARE ckqa_stmt FROM @ckqa_sql;
    EXECUTE ckqa_stmt;
    DEALLOCATE PREPARE ckqa_stmt;
  END IF;
END $$

DROP PROCEDURE IF EXISTS `ckqa_add_index_if_missing` $$
CREATE PROCEDURE `ckqa_add_index_if_missing`(
  IN p_table_name varchar(64),
  IN p_index_name varchar(64),
  IN p_index_definition text
)
BEGIN
  IF NOT EXISTS (
    SELECT 1
    FROM information_schema.STATISTICS
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME = p_table_name
      AND INDEX_NAME = p_index_name
  ) THEN
    SET @ckqa_sql = CONCAT('ALTER TABLE `', p_table_name, '` ADD ', p_index_definition);
    PREPARE ckqa_stmt FROM @ckqa_sql;
    EXECUTE ckqa_stmt;
    DEALLOCATE PREPARE ckqa_stmt;
  END IF;
END $$

DROP PROCEDURE IF EXISTS `ckqa_rename_index_if_exists` $$
CREATE PROCEDURE `ckqa_rename_index_if_exists`(
  IN p_table_name varchar(64),
  IN p_old_index_name varchar(64),
  IN p_new_index_name varchar(64)
)
BEGIN
  IF EXISTS (
    SELECT 1
    FROM information_schema.STATISTICS
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME = p_table_name
      AND INDEX_NAME = p_old_index_name
  ) AND NOT EXISTS (
    SELECT 1
    FROM information_schema.STATISTICS
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME = p_table_name
      AND INDEX_NAME = p_new_index_name
  ) THEN
    SET @ckqa_sql = CONCAT(
      'ALTER TABLE `', p_table_name, '` RENAME INDEX `',
      p_old_index_name, '` TO `', p_new_index_name, '`'
    );
    PREPARE ckqa_stmt FROM @ckqa_sql;
    EXECUTE ckqa_stmt;
    DEALLOCATE PREPARE ckqa_stmt;
  END IF;
END $$

DROP PROCEDURE IF EXISTS `ckqa_change_pdf_column_if_needed` $$
CREATE PROCEDURE `ckqa_change_pdf_column_if_needed`(
  IN p_table_name varchar(64)
)
BEGIN
  IF EXISTS (
    SELECT 1
    FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME = p_table_name
      AND COLUMN_NAME = 'pdf_file_id'
  ) AND NOT EXISTS (
    SELECT 1
    FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME = p_table_name
      AND COLUMN_NAME = 'course_material_id'
  ) THEN
    SET @ckqa_sql = CONCAT(
      'ALTER TABLE `', p_table_name,
      '` CHANGE COLUMN `pdf_file_id` `course_material_id` bigint NOT NULL COMMENT ''关联的课程资料ID'''
    );
    PREPARE ckqa_stmt FROM @ckqa_sql;
    EXECUTE ckqa_stmt;
    DEALLOCATE PREPARE ckqa_stmt;
  END IF;
END $$

DELIMITER ;

CALL `ckqa_drop_fk_if_exists`('parse_results', 'fk_parse_results_pdf_file');
CALL `ckqa_drop_fk_if_exists`('parse_logs', 'fk_parse_logs_pdf_file');

DROP VIEW IF EXISTS `v_course_parse_overview`;

CREATE TABLE IF NOT EXISTS `material_objects` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `original_file_name` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '原始文件名',
  `file_md5` char(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '文件MD5哈希值',
  `file_size` bigint NOT NULL COMMENT '文件大小（字节）',
  `mime_type` varchar(128) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT 'application/pdf' COMMENT 'MIME类型',
  `minio_bucket` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT 'MinIO存储桶名称',
  `minio_object_key` varchar(512) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT 'MinIO对象键（路径）',
  `created_at` timestamp NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_at` timestamp NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`) USING BTREE,
  UNIQUE INDEX `uk_material_objects_md5`(`file_md5` ASC) USING BTREE,
  INDEX `idx_material_objects_created_at`(`created_at` ASC) USING BTREE
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = '资料对象表' ROW_FORMAT = Dynamic;

CREATE TABLE IF NOT EXISTS `course_materials` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `course_id` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '课程ID',
  `material_object_id` bigint NOT NULL COMMENT '资料对象ID',
  `display_name` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '课程内展示名称',
  `material_type` enum('textbook','handout','slides','lab_guide','exam','reference','other') CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT 'textbook' COMMENT '资料类型',
  `parse_status` enum('pending','processing','done','failed') CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT 'pending' COMMENT '解析状态',
  `parse_started_at` timestamp NULL DEFAULT NULL COMMENT '解析开始时间',
  `parse_finished_at` timestamp NULL DEFAULT NULL COMMENT '解析完成时间',
  `parse_error_msg` text CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL COMMENT '解析错误信息',
  `mineru_batch_id` varchar(128) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT 'MinerU批次ID',
  `upload_time` timestamp NULL DEFAULT CURRENT_TIMESTAMP COMMENT '上传时间',
  `created_at` timestamp NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_at` timestamp NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`) USING BTREE,
  UNIQUE INDEX `uk_course_material_object`(`course_id` ASC, `material_object_id` ASC) USING BTREE,
  UNIQUE INDEX `uk_course_material_display_name`(`course_id` ASC, `display_name` ASC) USING BTREE,
  INDEX `idx_course_materials_course_status`(`course_id` ASC, `parse_status` ASC) USING BTREE,
  INDEX `idx_course_materials_upload_time`(`upload_time` ASC) USING BTREE
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = '课程资料关系表' ROW_FORMAT = Dynamic;

CALL `ckqa_add_index_if_missing`(
  'material_objects',
  'idx_material_objects_created_at',
  'INDEX `idx_material_objects_created_at`(`created_at` ASC)'
);
CALL `ckqa_add_index_if_missing`(
  'course_materials',
  'idx_course_materials_course_status',
  'INDEX `idx_course_materials_course_status`(`course_id` ASC, `parse_status` ASC)'
);
CALL `ckqa_add_index_if_missing`(
  'course_materials',
  'idx_course_materials_upload_time',
  'INDEX `idx_course_materials_upload_time`(`upload_time` ASC)'
);

START TRANSACTION;

INSERT INTO `material_objects` (
  `id`,
  `original_file_name`,
  `file_md5`,
  `file_size`,
  `mime_type`,
  `minio_bucket`,
  `minio_object_key`,
  `created_at`,
  `updated_at`
)
SELECT
  pf.`id`,
  pf.`file_name`,
  pf.`file_md5`,
  pf.`file_size`,
  'application/pdf',
  pf.`minio_bucket`,
  pf.`minio_object_key`,
  COALESCE(pf.`created_at`, pf.`upload_time`, CURRENT_TIMESTAMP),
  COALESCE(pf.`updated_at`, pf.`created_at`, pf.`upload_time`, CURRENT_TIMESTAMP)
FROM `pdf_files` pf
ON DUPLICATE KEY UPDATE
  `original_file_name` = VALUES(`original_file_name`),
  `file_size` = VALUES(`file_size`),
  `mime_type` = VALUES(`mime_type`),
  `minio_bucket` = VALUES(`minio_bucket`),
  `minio_object_key` = VALUES(`minio_object_key`),
  `updated_at` = VALUES(`updated_at`);

INSERT INTO `course_materials` (
  `id`,
  `course_id`,
  `material_object_id`,
  `display_name`,
  `material_type`,
  `parse_status`,
  `parse_started_at`,
  `parse_finished_at`,
  `parse_error_msg`,
  `mineru_batch_id`,
  `upload_time`,
  `created_at`,
  `updated_at`
)
SELECT
  pf.`id`,
  pf.`course_id`,
  mo.`id`,
  pf.`file_name`,
  'textbook',
  COALESCE(pf.`parse_status`, 'pending'),
  pf.`parse_started_at`,
  pf.`parse_finished_at`,
  pf.`parse_error_msg`,
  pf.`mineru_batch_id`,
  pf.`upload_time`,
  COALESCE(pf.`created_at`, pf.`upload_time`, CURRENT_TIMESTAMP),
  COALESCE(pf.`updated_at`, pf.`created_at`, pf.`upload_time`, CURRENT_TIMESTAMP)
FROM `pdf_files` pf
JOIN `material_objects` mo ON mo.`file_md5` = pf.`file_md5`
ON DUPLICATE KEY UPDATE
  `course_id` = VALUES(`course_id`),
  `material_object_id` = VALUES(`material_object_id`),
  `display_name` = VALUES(`display_name`),
  `material_type` = VALUES(`material_type`),
  `parse_status` = VALUES(`parse_status`),
  `parse_started_at` = VALUES(`parse_started_at`),
  `parse_finished_at` = VALUES(`parse_finished_at`),
  `parse_error_msg` = VALUES(`parse_error_msg`),
  `mineru_batch_id` = VALUES(`mineru_batch_id`),
  `upload_time` = VALUES(`upload_time`),
  `updated_at` = VALUES(`updated_at`);

COMMIT;

CALL `ckqa_change_pdf_column_if_needed`('parse_results');
CALL `ckqa_change_pdf_column_if_needed`('parse_logs');
CALL `ckqa_rename_index_if_exists`('parse_results', 'idx_pdf_file_id', 'idx_course_material_id');
CALL `ckqa_rename_index_if_exists`('parse_logs', 'idx_pdf_file_id', 'idx_course_material_id');
CALL `ckqa_add_index_if_missing`(
  'parse_results',
  'idx_course_material_id',
  'INDEX `idx_course_material_id`(`course_material_id` ASC)'
);
CALL `ckqa_add_index_if_missing`(
  'parse_logs',
  'idx_course_material_id',
  'INDEX `idx_course_material_id`(`course_material_id` ASC)'
);

CREATE ALGORITHM = UNDEFINED SQL SECURITY DEFINER VIEW `v_course_parse_overview` AS
SELECT
  c.`course_id` AS `course_id`,
  c.`course_name` AS `course_name`,
  cm.`id` AS `course_material_id`,
  cm.`id` AS `pdf_file_id`,
  cm.`display_name` AS `display_name`,
  cm.`display_name` AS `file_name`,
  mo.`file_md5` AS `file_md5`,
  mo.`file_size` AS `file_size`,
  cm.`material_type` AS `material_type`,
  cm.`parse_status` AS `parse_status`,
  cm.`upload_time` AS `upload_time`,
  cm.`parse_started_at` AS `parse_started_at`,
  cm.`parse_finished_at` AS `parse_finished_at`,
  TIMESTAMPDIFF(SECOND, cm.`parse_started_at`, cm.`parse_finished_at`) AS `parse_duration_seconds`,
  (
    SELECT COUNT(0)
    FROM `parse_results` pr
    WHERE pr.`course_material_id` = cm.`id`
  ) AS `result_file_count`
FROM `courses` c
LEFT JOIN `course_materials` cm ON c.`course_id` = cm.`course_id`
LEFT JOIN `material_objects` mo ON cm.`material_object_id` = mo.`id`;

SET @ckqa_next_material_object_id = (
  SELECT COALESCE(MAX(`id`), 0) + 1 FROM `material_objects`
);
SET @ckqa_sql = CONCAT('ALTER TABLE `material_objects` AUTO_INCREMENT = ', @ckqa_next_material_object_id);
PREPARE ckqa_stmt FROM @ckqa_sql;
EXECUTE ckqa_stmt;
DEALLOCATE PREPARE ckqa_stmt;

SET @ckqa_next_course_material_id = (
  SELECT COALESCE(MAX(`id`), 0) + 1 FROM `course_materials`
);
SET @ckqa_sql = CONCAT('ALTER TABLE `course_materials` AUTO_INCREMENT = ', @ckqa_next_course_material_id);
PREPARE ckqa_stmt FROM @ckqa_sql;
EXECUTE ckqa_stmt;
DEALLOCATE PREPARE ckqa_stmt;

DROP TABLE IF EXISTS `pdf_files`;

DROP PROCEDURE IF EXISTS `ckqa_change_pdf_column_if_needed`;
DROP PROCEDURE IF EXISTS `ckqa_rename_index_if_exists`;
DROP PROCEDURE IF EXISTS `ckqa_add_index_if_missing`;
DROP PROCEDURE IF EXISTS `ckqa_drop_fk_if_exists`;
