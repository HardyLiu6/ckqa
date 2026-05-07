SET @has_kb_course_archive_previous_status := (
  SELECT COUNT(*)
  FROM INFORMATION_SCHEMA.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE()
    AND TABLE_NAME = 'knowledge_bases'
    AND COLUMN_NAME = 'course_archive_previous_status'
);

SET @sql := IF(@has_kb_course_archive_previous_status = 0,
  'ALTER TABLE `knowledge_bases` ADD COLUMN `course_archive_previous_status` enum(''draft'',''active'') CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT ''课程归档联动前的知识库状态'' AFTER `status`',
  'SELECT ''knowledge_bases.course_archive_previous_status already exists'' AS message'
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;
