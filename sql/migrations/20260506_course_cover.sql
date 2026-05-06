ALTER TABLE `courses`
  ADD COLUMN `cover_url` varchar(512) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci
    NOT NULL DEFAULT '/api/v1/course-covers/default-course-cover.svg'
    COMMENT '课程封面访问地址'
    AFTER `description`;

UPDATE `courses`
SET `cover_url` = '/api/v1/course-covers/default-course-cover.svg'
WHERE `cover_url` IS NULL OR TRIM(`cover_url`) = '';
