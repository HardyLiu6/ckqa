-- CKQA GraphRAG build-run isolation schema
-- Date: 2026-05-05

CREATE TABLE IF NOT EXISTS `knowledge_base_build_runs` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `knowledge_base_id` bigint NOT NULL COMMENT '知识库ID',
  `course_id` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '课程ID快照',
  `requested_by_user_id` bigint NULL DEFAULT NULL COMMENT '发起用户ID',
  `build_version` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '构建版本',
  `status` enum('pending','running','success','failed','interrupted','archived') CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT 'pending' COMMENT '流水线状态',
  `current_stage` enum('material_selection','parse','graph_input_export','prompt','index','qa_smoke','done') CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT 'material_selection' COMMENT '当前阶段',
  `qa_status` enum('pending','running','success','failed','skipped') CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT 'skipped' COMMENT '问答验证状态',
  `activation_policy` enum('manual','index_success') CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT 'index_success' COMMENT '自动激活策略',
  `selected_material_ids` json DEFAULT NULL COMMENT '本次构建资料选择快照',
  `active_index_run_id` bigint NULL DEFAULT NULL COMMENT '当前由该构建承载的激活索引运行',
  `workspace_uri` varchar(512) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '相对 GRAPHRAG_BUILD_RUNS_ROOT 的工作区路径',
  `build_metadata` json DEFAULT NULL COMMENT '构建元数据',
  `started_at` timestamp NULL DEFAULT NULL COMMENT '开始时间',
  `finished_at` timestamp NULL DEFAULT NULL COMMENT '结束时间',
  `created_at` timestamp NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_at` timestamp NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`) USING BTREE,
  UNIQUE KEY `uk_kb_build_version` (`knowledge_base_id`, `build_version`) USING BTREE,
  KEY `idx_kb_build_status` (`knowledge_base_id`, `status`) USING BTREE,
  KEY `idx_kb_build_user_status` (`requested_by_user_id`, `status`) USING BTREE,
  KEY `idx_kb_build_created` (`knowledge_base_id`, `created_at`, `id`) USING BTREE,
  KEY `idx_kb_build_active_index` (`knowledge_base_id`, `active_index_run_id`) USING BTREE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='知识库构建流水线表';

SET @has_fk_kb_build_runs_kb := (
  SELECT COUNT(1) FROM information_schema.REFERENTIAL_CONSTRAINTS
  WHERE CONSTRAINT_SCHEMA = DATABASE()
    AND CONSTRAINT_NAME = 'fk_kb_build_runs_kb'
);
SET @sql := IF(@has_fk_kb_build_runs_kb = 0,
  'ALTER TABLE `knowledge_base_build_runs` ADD CONSTRAINT `fk_kb_build_runs_kb` FOREIGN KEY (`knowledge_base_id`) REFERENCES `knowledge_bases` (`id`) ON DELETE RESTRICT ON UPDATE RESTRICT',
  'SELECT 1');
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @has_build_run_id := (
  SELECT COUNT(1) FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'index_runs' AND COLUMN_NAME = 'build_run_id'
);
SET @sql := IF(@has_build_run_id = 0,
  'ALTER TABLE `index_runs` ADD COLUMN `build_run_id` bigint NULL DEFAULT NULL COMMENT ''所属知识库构建流水线ID'' AFTER `knowledge_base_id`',
  'SELECT 1');
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @has_idx_index_runs_build_run := (
  SELECT COUNT(1) FROM information_schema.STATISTICS
  WHERE TABLE_SCHEMA = DATABASE()
    AND TABLE_NAME = 'index_runs'
    AND INDEX_NAME = 'idx_index_runs_build_run'
);
SET @sql := IF(@has_idx_index_runs_build_run = 0,
  'CREATE INDEX `idx_index_runs_build_run` ON `index_runs` (`build_run_id`)',
  'SELECT 1');
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @has_fk_index_runs_build_run := (
  SELECT COUNT(1) FROM information_schema.REFERENTIAL_CONSTRAINTS
  WHERE CONSTRAINT_SCHEMA = DATABASE()
    AND CONSTRAINT_NAME = 'fk_index_runs_build_run'
);
SET @sql := IF(@has_fk_index_runs_build_run = 0,
  'ALTER TABLE `index_runs` ADD CONSTRAINT `fk_index_runs_build_run` FOREIGN KEY (`build_run_id`) REFERENCES `knowledge_base_build_runs` (`id`) ON DELETE SET NULL ON UPDATE RESTRICT',
  'SELECT 1');
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

ALTER TABLE `index_artifacts`
  MODIFY COLUMN `artifact_type` enum('input_json','output_dir','parquet','lancedb','report','cache','manifest','log','qa_smoke','graphrag_output','other') CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '产物类型';

SET @has_display_name := (
  SELECT COUNT(1) FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'index_artifacts' AND COLUMN_NAME = 'display_name'
);
SET @sql := IF(@has_display_name = 0,
  'ALTER TABLE `index_artifacts` ADD COLUMN `display_name` varchar(255) NULL DEFAULT NULL COMMENT ''前端展示名'' AFTER `artifact_type`',
  'SELECT 1');
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @has_storage_scope := (
  SELECT COUNT(1) FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'index_artifacts' AND COLUMN_NAME = 'storage_scope'
);
SET @sql := IF(@has_storage_scope = 0,
  'ALTER TABLE `index_artifacts` ADD COLUMN `storage_scope` enum(''local'',''minio'') NOT NULL DEFAULT ''local'' COMMENT ''存储位置类型'' AFTER `storage_uri`',
  'SELECT 1');
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @has_artifact_status := (
  SELECT COUNT(1) FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'index_artifacts' AND COLUMN_NAME = 'artifact_status'
);
SET @sql := IF(@has_artifact_status = 0,
  'ALTER TABLE `index_artifacts` ADD COLUMN `artifact_status` enum(''ready'',''partial'',''missing'',''deleted'') NOT NULL DEFAULT ''ready'' COMMENT ''产物状态'' AFTER `storage_scope`',
  'SELECT 1');
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;
