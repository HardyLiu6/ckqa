-- CKQA GraphRAG 自动调优运行表
-- Date: 2026-05-14
-- 缓存按 cache_key（基于 selected_materials 的 PDF MD5 集合派生 sha256）查找；
-- 同一 cache_key 命中 success 即视为已经调优过，可直接复用 candidate_dir 下的 extract_graph.txt。

CREATE TABLE IF NOT EXISTS `prompt_tune_runs` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `knowledge_base_id` bigint NOT NULL COMMENT '所属知识库ID',
  `build_run_id` bigint NULL DEFAULT NULL COMMENT '触发本次调优的构建流水线ID（可空，便于复用历史缓存）',
  `course_id` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '课程ID快照',
  `selected_material_ids` json NULL DEFAULT NULL COMMENT '本次调优选用的资料ID列表',
  `cache_key` varchar(128) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '缓存键：sorted(materialId:fileMd5) 的 sha256 hex',
  `status` enum('pending','running','success','failed','cancelled') CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT 'pending' COMMENT '调优状态',
  `progress_stage` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT 'queued' COMMENT '当前阶段：queued/fetch_input/prompt_tune/done',
  `candidate_dir` varchar(512) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '相对 GRAPHRAG_BUILD_RUNS_ROOT 的产物目录路径',
  `prompt_sha256` varchar(80) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '产出的 extract_graph.txt 内容 sha256',
  `latest_logs` text CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL COMMENT '末尾若干行 stdout/stderr 拼接',
  `error_message` text CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL COMMENT '失败原因摘要',
  `triggered_by_user_id` bigint NULL DEFAULT NULL COMMENT '触发用户ID',
  `started_at` timestamp NULL DEFAULT NULL COMMENT '开始时间',
  `finished_at` timestamp NULL DEFAULT NULL COMMENT '结束时间',
  `last_heartbeat_at` timestamp NULL DEFAULT NULL COMMENT '最近一次心跳时间',
  `created_at` timestamp NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_at` timestamp NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`) USING BTREE,
  KEY `idx_prompt_tune_kb_status` (`knowledge_base_id`, `status`) USING BTREE,
  KEY `idx_prompt_tune_cache_status` (`cache_key`, `status`) USING BTREE,
  KEY `idx_prompt_tune_build_run` (`build_run_id`) USING BTREE,
  KEY `idx_prompt_tune_created` (`created_at`, `id`) USING BTREE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='GraphRAG 提示词自动调优运行表';

-- 外键：删除知识库时调优记录保留为孤儿（不级联），便于审计；删除时通过 SET NULL 解绑 build_run_id。
SET @has_fk_prompt_tune_kb := (
  SELECT COUNT(1) FROM information_schema.REFERENTIAL_CONSTRAINTS
  WHERE CONSTRAINT_SCHEMA = DATABASE()
    AND CONSTRAINT_NAME = 'fk_prompt_tune_runs_kb'
);
SET @sql := IF(@has_fk_prompt_tune_kb = 0,
  'ALTER TABLE `prompt_tune_runs` ADD CONSTRAINT `fk_prompt_tune_runs_kb` FOREIGN KEY (`knowledge_base_id`) REFERENCES `knowledge_bases` (`id`) ON DELETE RESTRICT ON UPDATE RESTRICT',
  'SELECT 1');
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @has_fk_prompt_tune_build_run := (
  SELECT COUNT(1) FROM information_schema.REFERENTIAL_CONSTRAINTS
  WHERE CONSTRAINT_SCHEMA = DATABASE()
    AND CONSTRAINT_NAME = 'fk_prompt_tune_runs_build_run'
);
SET @sql := IF(@has_fk_prompt_tune_build_run = 0,
  'ALTER TABLE `prompt_tune_runs` ADD CONSTRAINT `fk_prompt_tune_runs_build_run` FOREIGN KEY (`build_run_id`) REFERENCES `knowledge_base_build_runs` (`id`) ON DELETE SET NULL ON UPDATE RESTRICT',
  'SELECT 1');
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;
