-- CKQA 手动调优 04 步评分运行表
-- Date: 2026-05-17
-- 异步任务持久化：每次 04 步触发评分时插一条 pending，worker 跑完写终态。

CREATE TABLE IF NOT EXISTS `prompt_tune_extraction_eval_runs` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `build_run_id` bigint NOT NULL COMMENT '所属构建流水线ID',
  `knowledge_base_id` bigint NOT NULL COMMENT '所属知识库ID',
  `selected_candidate_ids` json NOT NULL COMMENT '用户在 03 步勾选的候选 ID 列表（["default","auto_tuned",...]）',
  `seed` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '本次评分启动时 build run 的 seed 快照（system_default / graphrag_tuned / null），由 Phase 4.5 引入',
  `status` enum('pending','running','success','failed','cancelling','cancelled') CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT 'pending' COMMENT '任务状态',
  `progress_stage` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT 'queued' COMMENT '当前阶段：queued/extracting/scoring/done',
  `extracting_candidate_id` varchar(128) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '当前正在抽取的候选ID',
  `finished_candidates` json NULL DEFAULT NULL COMMENT '已完成候选ID数组（按完成顺序）',
  `candidate_failures` json NULL DEFAULT NULL COMMENT '失败候选结构化清单：[{"candidateId":"default","stage":"extract","reason":"..."}]',
  `eval_dir` varchar(512) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '相对 GRAPHRAG_BUILD_RUNS_ROOT 的评分产物目录路径',
  `report_json` mediumtext CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL COMMENT 'top_candidates.json 内容快照',
  `error_message` text CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL COMMENT '失败原因摘要',
  `latest_logs` text CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL COMMENT '末尾若干行 stdout/stderr 拼接',
  `triggered_by_user_id` bigint NULL DEFAULT NULL COMMENT '触发用户ID',
  `started_at` timestamp NULL DEFAULT NULL COMMENT '开始时间',
  `finished_at` timestamp NULL DEFAULT NULL COMMENT '结束时间',
  `last_heartbeat_at` timestamp NULL DEFAULT NULL COMMENT '最近一次心跳时间',
  `created_at` timestamp NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_at` timestamp NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`) USING BTREE,
  KEY `idx_eval_runs_build_run_status` (`build_run_id`, `status`) USING BTREE,
  KEY `idx_eval_runs_kb_status` (`knowledge_base_id`, `status`) USING BTREE,
  KEY `idx_eval_runs_status_heartbeat` (`status`, `last_heartbeat_at`) USING BTREE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='手动调优 04 步评分运行表';

-- 外键：build_run 删除时评分记录保留为孤儿（RESTRICT，不级联），便于审计
SET @has_fk_eval_runs_build_run := (
  SELECT COUNT(1) FROM information_schema.REFERENTIAL_CONSTRAINTS
  WHERE CONSTRAINT_SCHEMA = DATABASE()
    AND CONSTRAINT_NAME = 'fk_eval_runs_build_run'
);
SET @sql := IF(@has_fk_eval_runs_build_run = 0,
  'ALTER TABLE `prompt_tune_extraction_eval_runs` ADD CONSTRAINT `fk_eval_runs_build_run` FOREIGN KEY (`build_run_id`) REFERENCES `knowledge_base_build_runs` (`id`) ON DELETE RESTRICT ON UPDATE RESTRICT',
  'SELECT 1');
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @has_fk_eval_runs_kb := (
  SELECT COUNT(1) FROM information_schema.REFERENTIAL_CONSTRAINTS
  WHERE CONSTRAINT_SCHEMA = DATABASE()
    AND CONSTRAINT_NAME = 'fk_eval_runs_kb'
);
SET @sql := IF(@has_fk_eval_runs_kb = 0,
  'ALTER TABLE `prompt_tune_extraction_eval_runs` ADD CONSTRAINT `fk_eval_runs_kb` FOREIGN KEY (`knowledge_base_id`) REFERENCES `knowledge_bases` (`id`) ON DELETE RESTRICT ON UPDATE RESTRICT',
  'SELECT 1');
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;
