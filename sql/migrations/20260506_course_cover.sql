SET NAMES utf8mb4;

SET @has_courses_cover_url := (
  SELECT COUNT(1)
  FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE()
    AND TABLE_NAME = 'courses'
    AND COLUMN_NAME = 'cover_url'
);

SET @sql := IF(@has_courses_cover_url = 0,
  'ALTER TABLE `courses` ADD COLUMN `cover_url` varchar(512) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT ''/api/v1/course-covers/default-course-cover.svg'' COMMENT ''课程封面访问地址'' AFTER `description`',
  'SELECT 1'
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

UPDATE `courses`
SET `cover_url` = '/api/v1/course-covers/default-course-cover.svg'
WHERE `cover_url` IS NULL OR TRIM(`cover_url`) = '';
