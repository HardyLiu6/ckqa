SET NAMES utf8mb4;

-- 用户头像对象存储字段与课程资料正式管理接口配套迁移。

SET @ckqa_sql = (
  SELECT IF(
    COUNT(*) = 0,
    'ALTER TABLE `users` ADD COLUMN `avatar_bucket` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT ''头像MinIO存储桶'' AFTER `last_login_at`',
    'SELECT 1'
  )
  FROM information_schema.columns
  WHERE table_schema = DATABASE()
    AND table_name = 'users'
    AND column_name = 'avatar_bucket'
);
PREPARE stmt FROM @ckqa_sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @ckqa_sql = (
  SELECT IF(
    COUNT(*) = 0,
    'ALTER TABLE `users` ADD COLUMN `avatar_object_key` varchar(512) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT ''头像MinIO对象键'' AFTER `avatar_bucket`',
    'SELECT 1'
  )
  FROM information_schema.columns
  WHERE table_schema = DATABASE()
    AND table_name = 'users'
    AND column_name = 'avatar_object_key'
);
PREPARE stmt FROM @ckqa_sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @ckqa_sql = (
  SELECT IF(
    COUNT(*) = 0,
    'ALTER TABLE `users` ADD COLUMN `avatar_content_type` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT ''头像内容类型'' AFTER `avatar_object_key`',
    'SELECT 1'
  )
  FROM information_schema.columns
  WHERE table_schema = DATABASE()
    AND table_name = 'users'
    AND column_name = 'avatar_content_type'
);
PREPARE stmt FROM @ckqa_sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @ckqa_sql = (
  SELECT IF(
    COUNT(*) = 0,
    'ALTER TABLE `users` ADD COLUMN `avatar_updated_at` timestamp NULL DEFAULT NULL COMMENT ''头像更新时间'' AFTER `avatar_content_type`',
    'SELECT 1'
  )
  FROM information_schema.columns
  WHERE table_schema = DATABASE()
    AND table_name = 'users'
    AND column_name = 'avatar_updated_at'
);
PREPARE stmt FROM @ckqa_sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

