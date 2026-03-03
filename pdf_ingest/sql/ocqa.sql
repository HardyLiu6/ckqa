/*
 Navicat Premium Dump SQL

 Source Server         : ocqa
 Source Server Type    : MySQL
 Source Server Version : 80036 (8.0.36)
 Source Host           : localhost:23306
 Source Schema         : ocqa

 Target Server Type    : MySQL
 Target Server Version : 80036 (8.0.36)
 File Encoding         : 65001

 Date: 14/02/2026 13:29:59
*/

SET NAMES utf8mb4;
SET FOREIGN_KEY_CHECKS = 0;

-- ----------------------------
-- Table structure for courses
-- ----------------------------
DROP TABLE IF EXISTS `courses`;
CREATE TABLE `courses`  (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `is_deleted` tinyint(1) NOT NULL DEFAULT 0 COMMENT '逻辑删除标记：0-未删除，1-已删除',
  `course_id` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '课程ID，如: os, cs61b',
  `course_name` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '课程名称',
  `description` text CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL COMMENT '课程描述',
  `created_at` timestamp NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_at` timestamp NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`) USING BTREE,
  UNIQUE INDEX `uk_course_id`(`course_id` ASC) USING BTREE,
  INDEX `idx_created_at`(`created_at` ASC) USING BTREE,
  INDEX `idx_courses_is_deleted`(`is_deleted` ASC) USING BTREE
) ENGINE = InnoDB AUTO_INCREMENT = 2 CHARACTER SET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = '课程表' ROW_FORMAT = Dynamic;

-- ----------------------------
-- Table structure for parse_logs
-- ----------------------------
DROP TABLE IF EXISTS `parse_logs`;
CREATE TABLE `parse_logs`  (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `pdf_file_id` bigint NOT NULL COMMENT '关联的PDF文件ID',
  `log_level` enum('info','warning','error') CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT 'info' COMMENT '日志级别',
  `log_message` text CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '日志内容',
  `created_at` timestamp NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  PRIMARY KEY (`id`) USING BTREE,
  INDEX `idx_pdf_file_id`(`pdf_file_id` ASC) USING BTREE,
  INDEX `idx_log_level`(`log_level` ASC) USING BTREE,
  INDEX `idx_created_at`(`created_at` ASC) USING BTREE
) ENGINE = InnoDB AUTO_INCREMENT = 7 CHARACTER SET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = '解析日志表' ROW_FORMAT = Dynamic;

-- ----------------------------
-- Table structure for parse_results
-- ----------------------------
DROP TABLE IF EXISTS `parse_results`;
CREATE TABLE `parse_results`  (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `pdf_file_id` bigint NOT NULL COMMENT '关联的PDF文件ID',
  `course_id` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '课程ID',
  `result_type` enum('content_list_json','model_json','layout_json','markdown','image','origin_pdf','other') CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '结果类型',
  `file_name` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '文件名',
  `minio_bucket` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT 'MinIO存储桶',
  `minio_object_key` varchar(512) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT 'MinIO对象键',
  `file_size` bigint NULL DEFAULT 0 COMMENT '文件大小（字节）',
  `created_at` timestamp NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  PRIMARY KEY (`id`) USING BTREE,
  INDEX `idx_pdf_file_id`(`pdf_file_id` ASC) USING BTREE,
  INDEX `idx_course_id`(`course_id` ASC) USING BTREE,
  INDEX `idx_result_type`(`result_type` ASC) USING BTREE
) ENGINE = InnoDB AUTO_INCREMENT = 406 CHARACTER SET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = '解析结果表' ROW_FORMAT = Dynamic;

-- ----------------------------
-- Table structure for pdf_files
-- ----------------------------
DROP TABLE IF EXISTS `pdf_files`;
CREATE TABLE `pdf_files`  (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `course_id` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '课程ID',
  `file_name` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '原始文件名',
  `file_md5` char(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '文件MD5哈希值',
  `file_size` bigint NOT NULL COMMENT '文件大小（字节）',
  `minio_bucket` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT 'MinIO存储桶名称',
  `minio_object_key` varchar(512) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT 'MinIO对象键（路径）',
  `upload_time` timestamp NULL DEFAULT CURRENT_TIMESTAMP COMMENT '上传时间',
  `parse_status` enum('pending','processing','done','failed') CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT 'pending' COMMENT '解析状态',
  `parse_started_at` timestamp NULL DEFAULT NULL COMMENT '解析开始时间',
  `parse_finished_at` timestamp NULL DEFAULT NULL COMMENT '解析完成时间',
  `parse_error_msg` text CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL COMMENT '解析错误信息',
  `mineru_batch_id` varchar(128) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT 'MinerU批次ID',
  `created_at` timestamp NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_at` timestamp NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`) USING BTREE,
  UNIQUE INDEX `uk_file_md5`(`file_md5` ASC) USING BTREE COMMENT 'MD5唯一索引，防止重复上传',
  UNIQUE INDEX `uk_course_file`(`course_id` ASC, `file_name` ASC) USING BTREE COMMENT '同一课程下文件名唯一',
  INDEX `idx_course_id`(`course_id` ASC) USING BTREE,
  INDEX `idx_parse_status`(`parse_status` ASC) USING BTREE,
  INDEX `idx_upload_time`(`upload_time` ASC) USING BTREE
) ENGINE = InnoDB AUTO_INCREMENT = 2 CHARACTER SET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = 'PDF文件表' ROW_FORMAT = Dynamic;

-- ----------------------------
-- View structure for v_course_parse_overview
-- ----------------------------
DROP VIEW IF EXISTS `v_course_parse_overview`;
CREATE ALGORITHM = UNDEFINED SQL SECURITY DEFINER VIEW `v_course_parse_overview` AS select `c`.`course_id` AS `course_id`,`c`.`course_name` AS `course_name`,`pf`.`id` AS `pdf_file_id`,`pf`.`file_name` AS `file_name`,`pf`.`file_md5` AS `file_md5`,`pf`.`file_size` AS `file_size`,`pf`.`parse_status` AS `parse_status`,`pf`.`upload_time` AS `upload_time`,`pf`.`parse_started_at` AS `parse_started_at`,`pf`.`parse_finished_at` AS `parse_finished_at`,timestampdiff(SECOND,`pf`.`parse_started_at`,`pf`.`parse_finished_at`) AS `parse_duration_seconds`,(select count(0) from `parse_results` `pr` where (`pr`.`pdf_file_id` = `pf`.`id`)) AS `result_file_count` from (`courses` `c` left join `pdf_files` `pf` on((`c`.`course_id` = `pf`.`course_id`)));

SET FOREIGN_KEY_CHECKS = 1;
