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
  `status` enum('active','inactive','archived') CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT 'active' COMMENT '课程状态',
  `access_policy` enum('restricted','public') CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT 'restricted' COMMENT '访问策略',
  `created_at` timestamp NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_at` timestamp NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`) USING BTREE,
  UNIQUE INDEX `uk_course_id`(`course_id` ASC) USING BTREE,
  INDEX `idx_created_at`(`created_at` ASC) USING BTREE,
  INDEX `idx_courses_is_deleted`(`is_deleted` ASC) USING BTREE
) ENGINE = InnoDB AUTO_INCREMENT = 2 CHARACTER SET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = '课程表' ROW_FORMAT = Dynamic;

-- ----------------------------
-- Table structure for material_objects
-- ----------------------------
DROP TABLE IF EXISTS `material_objects`;
CREATE TABLE `material_objects` (
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
) ENGINE = InnoDB AUTO_INCREMENT = 2 CHARACTER SET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = '资料对象表' ROW_FORMAT = Dynamic;

-- ----------------------------
-- Table structure for course_materials
-- ----------------------------
DROP TABLE IF EXISTS `course_materials`;
CREATE TABLE `course_materials` (
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
) ENGINE = InnoDB AUTO_INCREMENT = 2 CHARACTER SET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = '课程资料关系表' ROW_FORMAT = Dynamic;

-- ----------------------------
-- Table structure for parse_logs
-- ----------------------------
DROP TABLE IF EXISTS `parse_logs`;
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
) ENGINE = InnoDB AUTO_INCREMENT = 7 CHARACTER SET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = '解析日志表' ROW_FORMAT = Dynamic;

-- ----------------------------
-- Table structure for parse_results
-- ----------------------------
DROP TABLE IF EXISTS `parse_results`;
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
) ENGINE = InnoDB AUTO_INCREMENT = 406 CHARACTER SET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = '解析结果表' ROW_FORMAT = Dynamic;

-- ----------------------------
-- Table structure for users
-- ----------------------------
DROP TABLE IF EXISTS `users`;
CREATE TABLE `users` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `user_code` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '稳定业务ID',
  `username` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '登录用户名',
  `display_name` varchar(128) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '展示名称',
  `password_hash` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '密码哈希',
  `status` enum('active','disabled','locked','pending') CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT 'active' COMMENT '用户状态',
  `last_login_at` timestamp NULL DEFAULT NULL COMMENT '最后登录时间',
  `extra_metadata` json DEFAULT NULL COMMENT '扩展元数据',
  `created_at` timestamp NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_at` timestamp NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `is_deleted` tinyint(1) NOT NULL DEFAULT 0 COMMENT '逻辑删除',
  PRIMARY KEY (`id`) USING BTREE,
  UNIQUE INDEX `uk_user_code`(`user_code` ASC) USING BTREE,
  UNIQUE INDEX `uk_username`(`username` ASC) USING BTREE
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = '平台用户表' ROW_FORMAT = Dynamic;

-- ----------------------------
-- Table structure for roles
-- ----------------------------
DROP TABLE IF EXISTS `roles`;
CREATE TABLE `roles` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `role_code` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '角色编码',
  `role_name` varchar(128) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '角色名称',
  `description` text CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL COMMENT '角色说明',
  `created_at` timestamp NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_at` timestamp NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`) USING BTREE,
  UNIQUE INDEX `uk_role_code`(`role_code` ASC) USING BTREE
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = '角色表' ROW_FORMAT = Dynamic;

-- ----------------------------
-- Table structure for permissions
-- ----------------------------
DROP TABLE IF EXISTS `permissions`;
CREATE TABLE `permissions` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `permission_code` varchar(128) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '权限编码',
  `permission_name` varchar(128) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '权限名称',
  `description` text CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL COMMENT '权限说明',
  PRIMARY KEY (`id`) USING BTREE,
  UNIQUE INDEX `uk_permission_code`(`permission_code` ASC) USING BTREE
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = '权限表' ROW_FORMAT = Dynamic;

-- ----------------------------
-- Table structure for user_roles
-- ----------------------------
DROP TABLE IF EXISTS `user_roles`;
CREATE TABLE `user_roles` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `user_id` bigint NOT NULL COMMENT '用户ID',
  `role_id` bigint NOT NULL COMMENT '角色ID',
  `created_at` timestamp NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  PRIMARY KEY (`id`) USING BTREE,
  UNIQUE INDEX `uk_user_role`(`user_id` ASC, `role_id` ASC) USING BTREE,
  CONSTRAINT `fk_user_roles_user` FOREIGN KEY (`user_id`) REFERENCES `users` (`id`) ON DELETE CASCADE ON UPDATE RESTRICT,
  CONSTRAINT `fk_user_roles_role` FOREIGN KEY (`role_id`) REFERENCES `roles` (`id`) ON DELETE CASCADE ON UPDATE RESTRICT
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = '用户角色关联表' ROW_FORMAT = Dynamic;

-- ----------------------------
-- Table structure for role_permissions
-- ----------------------------
DROP TABLE IF EXISTS `role_permissions`;
CREATE TABLE `role_permissions` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `role_id` bigint NOT NULL COMMENT '角色ID',
  `permission_id` bigint NOT NULL COMMENT '权限ID',
  `created_at` timestamp NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  PRIMARY KEY (`id`) USING BTREE,
  UNIQUE INDEX `uk_role_permission`(`role_id` ASC, `permission_id` ASC) USING BTREE,
  CONSTRAINT `fk_role_permissions_role` FOREIGN KEY (`role_id`) REFERENCES `roles` (`id`) ON DELETE CASCADE ON UPDATE RESTRICT,
  CONSTRAINT `fk_role_permissions_permission` FOREIGN KEY (`permission_id`) REFERENCES `permissions` (`id`) ON DELETE CASCADE ON UPDATE RESTRICT
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = '角色权限关联表' ROW_FORMAT = Dynamic;

-- ----------------------------
-- Table structure for auth_identities
-- ----------------------------
DROP TABLE IF EXISTS `auth_identities`;
CREATE TABLE `auth_identities` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `user_id` bigint NOT NULL COMMENT '用户ID',
  `provider` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '身份提供方',
  `provider_user_id` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '提供方用户ID',
  `identity_key` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '身份键',
  `credential_meta` json DEFAULT NULL COMMENT '凭据元数据',
  `created_at` timestamp NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_at` timestamp NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`) USING BTREE,
  UNIQUE INDEX `uk_provider_user`(`provider` ASC, `provider_user_id` ASC) USING BTREE,
  CONSTRAINT `fk_auth_identities_user` FOREIGN KEY (`user_id`) REFERENCES `users` (`id`) ON DELETE CASCADE ON UPDATE RESTRICT
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = '认证身份扩展表' ROW_FORMAT = Dynamic;

-- ----------------------------
-- Table structure for course_memberships
-- ----------------------------
DROP TABLE IF EXISTS `course_memberships`;
CREATE TABLE `course_memberships` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `user_id` bigint NOT NULL COMMENT '用户ID',
  `course_id` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '课程ID',
  `membership_role` enum('student','teacher','assistant') CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT 'student' COMMENT '课程内角色',
  `status` enum('active','pending','suspended','removed') CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT 'pending' COMMENT '成员状态',
  `access_source` enum('manual','imported','self_join','sync') CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT 'manual' COMMENT '授权来源',
  `source_ref_type` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '来源类型',
  `source_ref_id` varchar(128) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '来源ID',
  `joined_at` timestamp NULL DEFAULT NULL COMMENT '加入时间',
  `expires_at` timestamp NULL DEFAULT NULL COMMENT '过期时间',
  `effective_from` timestamp NULL DEFAULT NULL COMMENT '生效开始时间',
  `effective_to` timestamp NULL DEFAULT NULL COMMENT '生效结束时间',
  `granted_by_user_id` bigint NULL DEFAULT NULL COMMENT '授权人',
  `revoked_by_user_id` bigint NULL DEFAULT NULL COMMENT '撤销人',
  `change_reason` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '变更原因',
  `extra_metadata` json DEFAULT NULL COMMENT '扩展元数据',
  `created_at` timestamp NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_at` timestamp NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`) USING BTREE,
  UNIQUE INDEX `uk_user_course`(`user_id` ASC, `course_id` ASC) USING BTREE,
  INDEX `idx_course_memberships_course_status`(`course_id` ASC, `status` ASC) USING BTREE,
  CONSTRAINT `fk_course_memberships_user` FOREIGN KEY (`user_id`) REFERENCES `users` (`id`) ON DELETE CASCADE ON UPDATE RESTRICT,
  CONSTRAINT `fk_course_memberships_course` FOREIGN KEY (`course_id`) REFERENCES `courses` (`course_id`) ON DELETE RESTRICT ON UPDATE RESTRICT,
  CONSTRAINT `fk_course_memberships_granted_by` FOREIGN KEY (`granted_by_user_id`) REFERENCES `users` (`id`) ON DELETE SET NULL ON UPDATE RESTRICT,
  CONSTRAINT `fk_course_memberships_revoked_by` FOREIGN KEY (`revoked_by_user_id`) REFERENCES `users` (`id`) ON DELETE SET NULL ON UPDATE RESTRICT
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = '课程成员关系表' ROW_FORMAT = Dynamic;

-- ----------------------------
-- Table structure for course_membership_events
-- ----------------------------
DROP TABLE IF EXISTS `course_membership_events`;
CREATE TABLE `course_membership_events` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `course_membership_id` bigint NOT NULL COMMENT '课程成员关系ID',
  `event_type` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '事件类型',
  `old_status` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '旧状态',
  `new_status` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '新状态',
  `operator_user_id` bigint NULL DEFAULT NULL COMMENT '操作人',
  `change_reason` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '变更原因',
  `event_payload` json DEFAULT NULL COMMENT '事件载荷',
  `created_at` timestamp NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  PRIMARY KEY (`id`) USING BTREE,
  INDEX `idx_membership_events_membership_created`(`course_membership_id` ASC, `created_at` ASC) USING BTREE,
  CONSTRAINT `fk_membership_events_membership` FOREIGN KEY (`course_membership_id`) REFERENCES `course_memberships` (`id`) ON DELETE CASCADE ON UPDATE RESTRICT,
  CONSTRAINT `fk_membership_events_operator` FOREIGN KEY (`operator_user_id`) REFERENCES `users` (`id`) ON DELETE SET NULL ON UPDATE RESTRICT
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = '课程成员关系事件表' ROW_FORMAT = Dynamic;

-- ----------------------------
-- Table structure for knowledge_bases
-- ----------------------------
DROP TABLE IF EXISTS `knowledge_bases`;
CREATE TABLE `knowledge_bases` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `course_id` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '课程ID',
  `kb_code` varchar(128) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '知识库编码',
  `name` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '知识库名称',
  `status` enum('draft','active','archived') CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT 'draft' COMMENT '知识库状态',
  `active_index_run_id` bigint NULL DEFAULT NULL COMMENT '当前激活索引运行ID',
  `description` text CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL COMMENT '知识库说明',
  `created_at` timestamp NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_at` timestamp NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`) USING BTREE,
  UNIQUE INDEX `uk_course_kb_code`(`course_id` ASC, `kb_code` ASC) USING BTREE,
  CONSTRAINT `fk_knowledge_bases_course` FOREIGN KEY (`course_id`) REFERENCES `courses` (`course_id`) ON DELETE RESTRICT ON UPDATE RESTRICT
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = '课程知识库表' ROW_FORMAT = Dynamic;

-- ----------------------------
-- Table structure for kb_documents
-- ----------------------------
DROP TABLE IF EXISTS `kb_documents`;
CREATE TABLE `kb_documents` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `knowledge_base_id` bigint NOT NULL COMMENT '知识库ID',
  `source_type` enum('parse_result','normalized_doc','section_doc','page_doc','manual') CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '文档来源类型',
  `source_ref_id` varchar(128) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '来源记录ID',
  `document_key` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '文档键',
  `title` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '文档标题',
  `storage_uri` varchar(512) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '对象存储路径',
  `created_at` timestamp NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  PRIMARY KEY (`id`) USING BTREE,
  UNIQUE INDEX `uk_kb_document_key`(`knowledge_base_id` ASC, `document_key` ASC) USING BTREE,
  CONSTRAINT `fk_kb_documents_kb` FOREIGN KEY (`knowledge_base_id`) REFERENCES `knowledge_bases` (`id`) ON DELETE CASCADE ON UPDATE RESTRICT
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = '知识库文档映射表' ROW_FORMAT = Dynamic;

-- ----------------------------
-- Table structure for index_runs
-- ----------------------------
DROP TABLE IF EXISTS `index_runs`;
CREATE TABLE `index_runs` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `knowledge_base_id` bigint NOT NULL COMMENT '知识库ID',
  `engine` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '索引引擎',
  `index_version` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '索引版本',
  `status` enum('pending','running','success','failed','archived') CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT 'pending' COMMENT '运行状态',
  `started_at` timestamp NULL DEFAULT NULL COMMENT '开始时间',
  `finished_at` timestamp NULL DEFAULT NULL COMMENT '结束时间',
  `run_metadata` json DEFAULT NULL COMMENT '运行元数据',
  `created_at` timestamp NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  PRIMARY KEY (`id`) USING BTREE,
  UNIQUE INDEX `uk_kb_index_version`(`knowledge_base_id` ASC, `index_version` ASC) USING BTREE,
  INDEX `idx_index_runs_kb_status`(`knowledge_base_id` ASC, `status` ASC) USING BTREE,
  INDEX `idx_index_runs_status_started`(`status` ASC, `started_at` ASC) USING BTREE,
  CONSTRAINT `fk_index_runs_kb` FOREIGN KEY (`knowledge_base_id`) REFERENCES `knowledge_bases` (`id`) ON DELETE CASCADE ON UPDATE RESTRICT
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = '索引运行表' ROW_FORMAT = Dynamic;

-- ----------------------------
-- Table structure for index_artifacts
-- ----------------------------
DROP TABLE IF EXISTS `index_artifacts`;
CREATE TABLE `index_artifacts` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `index_run_id` bigint NOT NULL COMMENT '索引运行ID',
  `artifact_type` enum('graphrag_output','parquet','lancedb','report','other') CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '产物类型',
  `storage_uri` varchar(512) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '产物路径',
  `file_size` bigint NULL DEFAULT 0 COMMENT '文件大小',
  `checksum` varchar(128) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '校验值',
  `created_at` timestamp NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  PRIMARY KEY (`id`) USING BTREE,
  INDEX `idx_index_artifacts_run_type`(`index_run_id` ASC, `artifact_type` ASC) USING BTREE,
  CONSTRAINT `fk_index_artifacts_run` FOREIGN KEY (`index_run_id`) REFERENCES `index_runs` (`id`) ON DELETE CASCADE ON UPDATE RESTRICT
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = '索引产物表' ROW_FORMAT = Dynamic;

-- ----------------------------
-- Table structure for qa_sessions
-- ----------------------------
DROP TABLE IF EXISTS `qa_sessions`;
CREATE TABLE `qa_sessions` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `session_code` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '会话编码',
  `user_id` bigint NOT NULL COMMENT '用户ID',
  `course_id` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '课程ID',
  `course_membership_id` bigint NULL DEFAULT NULL COMMENT '课程成员ID',
  `knowledge_base_id` bigint NULL DEFAULT NULL COMMENT '知识库ID',
  `session_type` enum('formal','smoke') CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT 'formal' COMMENT '会话类型：formal正式问答，smoke构建冒烟验证',
  `title` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '会话标题',
  `status` enum('active','archived','deleted') CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT 'active' COMMENT '会话状态',
  `last_message_at` timestamp NULL DEFAULT NULL COMMENT '最后消息时间',
  `created_at` timestamp NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_at` timestamp NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`) USING BTREE,
  UNIQUE INDEX `uk_session_code`(`session_code` ASC) USING BTREE,
  INDEX `idx_sessions_user_created`(`user_id` ASC, `created_at` ASC) USING BTREE,
  CONSTRAINT `fk_sessions_user` FOREIGN KEY (`user_id`) REFERENCES `users` (`id`) ON DELETE CASCADE ON UPDATE RESTRICT,
  CONSTRAINT `fk_sessions_course` FOREIGN KEY (`course_id`) REFERENCES `courses` (`course_id`) ON DELETE SET NULL ON UPDATE RESTRICT,
  CONSTRAINT `fk_sessions_membership` FOREIGN KEY (`course_membership_id`) REFERENCES `course_memberships` (`id`) ON DELETE SET NULL ON UPDATE RESTRICT,
  CONSTRAINT `fk_sessions_kb` FOREIGN KEY (`knowledge_base_id`) REFERENCES `knowledge_bases` (`id`) ON DELETE SET NULL ON UPDATE RESTRICT
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = '问答会话表' ROW_FORMAT = Dynamic;

-- ----------------------------
-- Table structure for qa_messages
-- ----------------------------
DROP TABLE IF EXISTS `qa_messages`;
CREATE TABLE `qa_messages` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `session_id` bigint NOT NULL COMMENT '会话ID',
  `role` enum('system','user','assistant','tool') CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '消息角色',
  `sequence_no` int NOT NULL COMMENT '消息序号',
  `content` longtext CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '消息原始内容',
  `content_text` longtext CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL COMMENT '可检索文本',
  `token_count` int NULL DEFAULT NULL COMMENT 'Token数',
  `created_at` timestamp NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  PRIMARY KEY (`id`) USING BTREE,
  UNIQUE INDEX `uk_session_sequence`(`session_id` ASC, `sequence_no` ASC) USING BTREE,
  CONSTRAINT `fk_messages_session` FOREIGN KEY (`session_id`) REFERENCES `qa_sessions` (`id`) ON DELETE CASCADE ON UPDATE RESTRICT
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = '问答消息表' ROW_FORMAT = Dynamic;

-- ----------------------------
-- Table structure for qa_retrieval_logs
-- ----------------------------
DROP TABLE IF EXISTS `qa_retrieval_logs`;
CREATE TABLE `qa_retrieval_logs` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `session_id` bigint NOT NULL COMMENT '会话ID',
  `course_id` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '课程ID',
  `index_run_id` bigint NULL DEFAULT NULL COMMENT '索引运行ID',
  `query_mode` enum('local','global','drift','basic') CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '查询模式',
  `query_text` longtext CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '查询文本',
  `retrieval_status` enum('success','partial','failed') CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '检索状态',
  `error_message` text CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL COMMENT '错误信息',
  `created_at` timestamp NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  PRIMARY KEY (`id`) USING BTREE,
  INDEX `idx_retrieval_course_created`(`course_id` ASC, `created_at` ASC) USING BTREE,
  CONSTRAINT `fk_retrieval_logs_session` FOREIGN KEY (`session_id`) REFERENCES `qa_sessions` (`id`) ON DELETE CASCADE ON UPDATE RESTRICT,
  CONSTRAINT `fk_retrieval_logs_course` FOREIGN KEY (`course_id`) REFERENCES `courses` (`course_id`) ON DELETE SET NULL ON UPDATE RESTRICT,
  CONSTRAINT `fk_retrieval_logs_index_run` FOREIGN KEY (`index_run_id`) REFERENCES `index_runs` (`id`) ON DELETE SET NULL ON UPDATE RESTRICT
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = '问答检索日志表' ROW_FORMAT = Dynamic;

ALTER TABLE `qa_retrieval_logs`
  MODIFY COLUMN `query_mode` enum('local','global','drift','basic') CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '查询模式',
  MODIFY COLUMN `retrieval_status` enum('success','partial','failed') CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL COMMENT '检索状态',
  ADD COLUMN `user_message_id` bigint NULL COMMENT '用户消息ID' AFTER `session_id`,
  ADD COLUMN `assistant_message_id` bigint NULL COMMENT '助手消息ID' AFTER `user_message_id`,
  ADD COLUMN `task_seq` int NOT NULL DEFAULT 1 COMMENT '同一用户消息下的任务序号' AFTER `assistant_message_id`,
  ADD COLUMN `task_status` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT 'legacy' COMMENT '异步任务状态' AFTER `task_seq`,
  ADD COLUMN `progress_stage` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT 'legacy' COMMENT '编排阶段' AFTER `task_status`,
  ADD COLUMN `python_task_id` varchar(128) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL COMMENT 'Python侧任务ID' AFTER `progress_stage`,
  ADD COLUMN `latest_logs` text CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL COMMENT '最近日志tail' AFTER `python_task_id`,
  ADD COLUMN `started_at` timestamp NULL DEFAULT NULL COMMENT '开始时间' AFTER `latest_logs`,
  ADD COLUMN `last_heartbeat_at` timestamp NULL DEFAULT NULL COMMENT '最近心跳时间' AFTER `started_at`,
  ADD COLUMN `finished_at` timestamp NULL DEFAULT NULL COMMENT '完成时间' AFTER `last_heartbeat_at`,
  ADD INDEX `idx_retrieval_logs_session_created` (`session_id`, `created_at`),
  ADD INDEX `idx_retrieval_logs_user_message_seq` (`user_message_id`, `task_seq`),
  ADD INDEX `idx_retrieval_logs_task_status_heartbeat` (`task_status`, `last_heartbeat_at`),
  ADD UNIQUE KEY `uk_retrieval_logs_python_task_id` (`python_task_id`),
  ADD CONSTRAINT `fk_retrieval_logs_user_message` FOREIGN KEY (`user_message_id`) REFERENCES `qa_messages` (`id`) ON DELETE CASCADE ON UPDATE RESTRICT,
  ADD CONSTRAINT `fk_retrieval_logs_assistant_message` FOREIGN KEY (`assistant_message_id`) REFERENCES `qa_messages` (`id`) ON DELETE SET NULL ON UPDATE RESTRICT;

-- ----------------------------
-- Table structure for qa_retrieval_hits
-- ----------------------------
DROP TABLE IF EXISTS `qa_retrieval_hits`;
CREATE TABLE `qa_retrieval_hits` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `retrieval_log_id` bigint NOT NULL COMMENT '检索日志ID',
  `document_key` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '命中文档键',
  `chunk_id` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '命中块ID',
  `rank_position` int NOT NULL COMMENT '排序位置',
  `score` decimal(12, 6) NULL DEFAULT NULL COMMENT '召回分数',
  `created_at` timestamp NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  PRIMARY KEY (`id`) USING BTREE,
  INDEX `idx_hits_log_rank`(`retrieval_log_id` ASC, `rank_position` ASC) USING BTREE,
  CONSTRAINT `fk_retrieval_hits_log` FOREIGN KEY (`retrieval_log_id`) REFERENCES `qa_retrieval_logs` (`id`) ON DELETE CASCADE ON UPDATE RESTRICT
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = '问答命中文档表' ROW_FORMAT = Dynamic;

-- ----------------------------
-- Table structure for authorization_audit_logs
-- ----------------------------
DROP TABLE IF EXISTS `authorization_audit_logs`;
CREATE TABLE `authorization_audit_logs` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `actor_user_id` bigint NOT NULL COMMENT '执行判定的用户ID',
  `target_course_id` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '目标课程ID',
  `target_session_id` bigint NULL DEFAULT NULL COMMENT '目标会话ID',
  `course_membership_id` bigint NULL DEFAULT NULL COMMENT '命中的课程成员关系ID',
  `action` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '动作',
  `decision` enum('allow','deny') CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '判定结果',
  `decision_reason` varchar(128) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '判定原因',
  `extra_metadata` json DEFAULT NULL COMMENT '补充元数据',
  `created_at` timestamp NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  PRIMARY KEY (`id`) USING BTREE,
  INDEX `idx_auth_audit_actor_created`(`actor_user_id` ASC, `created_at` ASC) USING BTREE,
  CONSTRAINT `fk_auth_audit_actor` FOREIGN KEY (`actor_user_id`) REFERENCES `users` (`id`) ON DELETE CASCADE ON UPDATE RESTRICT,
  CONSTRAINT `fk_auth_audit_course` FOREIGN KEY (`target_course_id`) REFERENCES `courses` (`course_id`) ON DELETE SET NULL ON UPDATE RESTRICT,
  CONSTRAINT `fk_auth_audit_session` FOREIGN KEY (`target_session_id`) REFERENCES `qa_sessions` (`id`) ON DELETE SET NULL ON UPDATE RESTRICT,
  CONSTRAINT `fk_auth_audit_membership` FOREIGN KEY (`course_membership_id`) REFERENCES `course_memberships` (`id`) ON DELETE SET NULL ON UPDATE RESTRICT
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = '授权判定审计表' ROW_FORMAT = Dynamic;

INSERT INTO `roles` (`role_code`, `role_name`, `description`)
VALUES
  ('user', '普通用户', '默认平台用户'),
  ('admin', '平台管理员', '拥有跨课程访问能力')
ON DUPLICATE KEY UPDATE `role_name` = VALUES(`role_name`), `description` = VALUES(`description`);

INSERT INTO `permissions` (`permission_code`, `permission_name`, `description`)
VALUES
  ('course.query', '课程问答访问', '允许访问课程问答'),
  ('course.manage_members', '课程成员管理', '允许维护课程成员关系'),
  ('kb.manage_index', '知识库索引管理', '允许维护知识库和索引版本'),
  ('system.admin_override', '管理员越权访问', '允许管理员绕过课程成员限制')
ON DUPLICATE KEY UPDATE `permission_name` = VALUES(`permission_name`), `description` = VALUES(`description`);

INSERT INTO `role_permissions` (`role_id`, `permission_id`)
SELECT r.id, p.id
FROM `roles` r
JOIN `permissions` p ON p.permission_code = 'course.query'
WHERE r.role_code = 'user'
ON DUPLICATE KEY UPDATE `permission_id` = VALUES(`permission_id`);

INSERT INTO `role_permissions` (`role_id`, `permission_id`)
SELECT r.id, p.id
FROM `roles` r
JOIN `permissions` p ON p.permission_code IN (
  'course.query',
  'course.manage_members',
  'kb.manage_index',
  'system.admin_override'
)
WHERE r.role_code = 'admin'
ON DUPLICATE KEY UPDATE `permission_id` = VALUES(`permission_id`);

ALTER TABLE `knowledge_bases`
  ADD CONSTRAINT `fk_knowledge_bases_active_index_run`
  FOREIGN KEY (`active_index_run_id`) REFERENCES `index_runs` (`id`)
  ON DELETE SET NULL
  ON UPDATE RESTRICT;

-- ----------------------------
-- View structure for v_course_parse_overview
-- ----------------------------
DROP VIEW IF EXISTS `v_course_parse_overview`;
CREATE ALGORITHM = UNDEFINED SQL SECURITY DEFINER VIEW `v_course_parse_overview` AS select `c`.`course_id` AS `course_id`,`c`.`course_name` AS `course_name`,`cm`.`id` AS `course_material_id`,`cm`.`id` AS `pdf_file_id`,`cm`.`display_name` AS `display_name`,`cm`.`display_name` AS `file_name`,`mo`.`file_md5` AS `file_md5`,`mo`.`file_size` AS `file_size`,`cm`.`material_type` AS `material_type`,`cm`.`parse_status` AS `parse_status`,`cm`.`upload_time` AS `upload_time`,`cm`.`parse_started_at` AS `parse_started_at`,`cm`.`parse_finished_at` AS `parse_finished_at`,timestampdiff(SECOND,`cm`.`parse_started_at`,`cm`.`parse_finished_at`) AS `parse_duration_seconds`,(select count(0) from `parse_results` `pr` where (`pr`.`course_material_id` = `cm`.`id`)) AS `result_file_count` from ((`courses` `c` left join `course_materials` `cm` on((`c`.`course_id` = `cm`.`course_id`))) left join `material_objects` `mo` on((`cm`.`material_object_id` = `mo`.`id`)));

SET FOREIGN_KEY_CHECKS = 1;
