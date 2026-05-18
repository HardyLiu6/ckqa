SET NAMES utf8mb4;

-- P2：为滚动摘要补充低成本审计字段，便于统计模型、耗时与字符规模。

SET @has_summary_model := (
  SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE()
    AND TABLE_NAME = 'qa_session_summaries'
    AND COLUMN_NAME = 'model'
);
SET @sql := IF(@has_summary_model = 0,
  'ALTER TABLE `qa_session_summaries` ADD COLUMN `model` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL COMMENT ''摘要模型名称'' AFTER `error_message`',
  'SELECT ''qa_session_summaries.model already exists'' AS message'
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @has_summary_duration_ms := (
  SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE()
    AND TABLE_NAME = 'qa_session_summaries'
    AND COLUMN_NAME = 'duration_ms'
);
SET @sql := IF(@has_summary_duration_ms = 0,
  'ALTER TABLE `qa_session_summaries` ADD COLUMN `duration_ms` bigint NULL COMMENT ''摘要调用耗时毫秒'' AFTER `model`',
  'SELECT ''qa_session_summaries.duration_ms already exists'' AS message'
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @has_summary_input_chars := (
  SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE()
    AND TABLE_NAME = 'qa_session_summaries'
    AND COLUMN_NAME = 'input_char_count'
);
SET @sql := IF(@has_summary_input_chars = 0,
  'ALTER TABLE `qa_session_summaries` ADD COLUMN `input_char_count` int NULL COMMENT ''摘要输入字符数'' AFTER `duration_ms`',
  'SELECT ''qa_session_summaries.input_char_count already exists'' AS message'
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @has_summary_output_chars := (
  SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE()
    AND TABLE_NAME = 'qa_session_summaries'
    AND COLUMN_NAME = 'output_char_count'
);
SET @sql := IF(@has_summary_output_chars = 0,
  'ALTER TABLE `qa_session_summaries` ADD COLUMN `output_char_count` int NULL COMMENT ''摘要输出字符数'' AFTER `input_char_count`',
  'SELECT ''qa_session_summaries.output_char_count already exists'' AS message'
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;
