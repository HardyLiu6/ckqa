SET NAMES utf8mb4;

-- 为 course_materials 增加 MinerU 页级解析进度字段。

DROP PROCEDURE IF EXISTS `ckqa_add_course_material_parse_progress_if_missing`;
DELIMITER //
CREATE PROCEDURE `ckqa_add_course_material_parse_progress_if_missing`()
BEGIN
  IF NOT EXISTS (
    SELECT 1 FROM information_schema.columns
    WHERE table_schema = DATABASE()
      AND table_name = 'course_materials'
      AND COLUMN_NAME = 'parse_progress_extracted_pages'
  ) THEN
    ALTER TABLE `course_materials`
      ADD COLUMN `parse_progress_extracted_pages` int NULL DEFAULT NULL COMMENT 'MinerU已解析页数' AFTER `mineru_batch_id`;
  END IF;

  IF NOT EXISTS (
    SELECT 1 FROM information_schema.columns
    WHERE table_schema = DATABASE()
      AND table_name = 'course_materials'
      AND COLUMN_NAME = 'parse_progress_total_pages'
  ) THEN
    ALTER TABLE `course_materials`
      ADD COLUMN `parse_progress_total_pages` int NULL DEFAULT NULL COMMENT 'MinerU总页数' AFTER `parse_progress_extracted_pages`;
  END IF;

  IF NOT EXISTS (
    SELECT 1 FROM information_schema.columns
    WHERE table_schema = DATABASE()
      AND table_name = 'course_materials'
      AND COLUMN_NAME = 'parse_progress_percent'
  ) THEN
    ALTER TABLE `course_materials`
      ADD COLUMN `parse_progress_percent` tinyint unsigned NULL DEFAULT NULL COMMENT 'MinerU页级解析进度百分比' AFTER `parse_progress_total_pages`;
  END IF;

  IF NOT EXISTS (
    SELECT 1 FROM information_schema.columns
    WHERE table_schema = DATABASE()
      AND table_name = 'course_materials'
      AND COLUMN_NAME = 'parse_progress_started_at'
  ) THEN
    ALTER TABLE `course_materials`
      ADD COLUMN `parse_progress_started_at` timestamp NULL DEFAULT NULL COMMENT 'MinerU页级解析开始时间' AFTER `parse_progress_percent`;
  END IF;

  IF NOT EXISTS (
    SELECT 1 FROM information_schema.columns
    WHERE table_schema = DATABASE()
      AND table_name = 'course_materials'
      AND COLUMN_NAME = 'parse_progress_updated_at'
  ) THEN
    ALTER TABLE `course_materials`
      ADD COLUMN `parse_progress_updated_at` timestamp NULL DEFAULT NULL COMMENT 'MinerU页级解析进度更新时间' AFTER `parse_progress_started_at`;
  END IF;
END//
DELIMITER ;

CALL `ckqa_add_course_material_parse_progress_if_missing`();

DROP PROCEDURE IF EXISTS `ckqa_add_course_material_parse_progress_if_missing`;

DROP VIEW IF EXISTS `v_course_parse_overview`;
CREATE ALGORITHM = UNDEFINED SQL SECURITY DEFINER VIEW `v_course_parse_overview` AS
SELECT
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
  `cm`.`parse_progress_percent` AS `parse_progress_percent`,
  `cm`.`parse_progress_extracted_pages` AS `parse_progress_extracted_pages`,
  `cm`.`parse_progress_total_pages` AS `parse_progress_total_pages`,
  `cm`.`parse_progress_started_at` AS `parse_progress_started_at`,
  `cm`.`parse_progress_updated_at` AS `parse_progress_updated_at`,
  `cm`.`upload_time` AS `upload_time`,
  `cm`.`parse_started_at` AS `parse_started_at`,
  `cm`.`parse_finished_at` AS `parse_finished_at`,
  timestampdiff(SECOND, `cm`.`parse_started_at`, `cm`.`parse_finished_at`) AS `parse_duration_seconds`,
  (SELECT count(0) FROM `parse_results` `pr` WHERE (`pr`.`course_material_id` = `cm`.`id`)) AS `result_file_count`
FROM ((`courses` `c`
  LEFT JOIN `course_materials` `cm` ON ((`c`.`course_id` = `cm`.`course_id`)))
  LEFT JOIN `material_objects` `mo` ON ((`cm`.`material_object_id` = `mo`.`id`)));
