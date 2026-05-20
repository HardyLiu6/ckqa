-- Local History 长期学习记忆：偏好、记忆条目与任务诊断字段。
-- 该迁移只新增可空诊断列和独立记忆表，不改变既有问答答案与任务状态。

DELIMITER //
CREATE PROCEDURE `ckqa_add_qa_local_history_memory_columns_if_missing`()
BEGIN
  IF NOT EXISTS (
    SELECT 1 FROM information_schema.columns
    WHERE table_schema = DATABASE()
      AND table_name = 'qa_retrieval_logs'
      AND column_name = 'memory_applied'
  ) THEN
    ALTER TABLE `qa_retrieval_logs`
      ADD COLUMN `memory_applied` tinyint(1) NOT NULL DEFAULT 0 COMMENT '是否应用长期记忆' AFTER `routing_snapshot_json`;
  END IF;

  IF NOT EXISTS (
    SELECT 1 FROM information_schema.columns
    WHERE table_schema = DATABASE()
      AND table_name = 'qa_retrieval_logs'
      AND column_name = 'memory_strategy'
  ) THEN
    ALTER TABLE `qa_retrieval_logs`
      ADD COLUMN `memory_strategy` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL COMMENT '长期记忆策略' AFTER `memory_applied`;
  END IF;

  IF NOT EXISTS (
    SELECT 1 FROM information_schema.columns
    WHERE table_schema = DATABASE()
      AND table_name = 'qa_retrieval_logs'
      AND column_name = 'memory_scope'
  ) THEN
    ALTER TABLE `qa_retrieval_logs`
      ADD COLUMN `memory_scope` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL COMMENT '长期记忆隔离范围' AFTER `memory_strategy`;
  END IF;

  IF NOT EXISTS (
    SELECT 1 FROM information_schema.columns
    WHERE table_schema = DATABASE()
      AND table_name = 'qa_retrieval_logs'
      AND column_name = 'memory_source_count'
  ) THEN
    ALTER TABLE `qa_retrieval_logs`
      ADD COLUMN `memory_source_count` int NULL COMMENT '长期记忆来源数量' AFTER `memory_scope`;
  END IF;

  IF NOT EXISTS (
    SELECT 1 FROM information_schema.columns
    WHERE table_schema = DATABASE()
      AND table_name = 'qa_retrieval_logs'
      AND column_name = 'memory_size_chars'
  ) THEN
    ALTER TABLE `qa_retrieval_logs`
      ADD COLUMN `memory_size_chars` int NULL COMMENT '长期记忆上下文字符数' AFTER `memory_source_count`;
  END IF;

  IF NOT EXISTS (
    SELECT 1 FROM information_schema.columns
    WHERE table_schema = DATABASE()
      AND table_name = 'qa_retrieval_logs'
      AND column_name = 'query_engine_strategy'
  ) THEN
    ALTER TABLE `qa_retrieval_logs`
      ADD COLUMN `query_engine_strategy` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL COMMENT 'Python查询引擎策略' AFTER `memory_size_chars`;
  END IF;

  IF NOT EXISTS (
    SELECT 1 FROM information_schema.columns
    WHERE table_schema = DATABASE()
      AND table_name = 'qa_retrieval_logs'
      AND column_name = 'history_fallback_reason'
  ) THEN
    ALTER TABLE `qa_retrieval_logs`
      ADD COLUMN `history_fallback_reason` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL COMMENT '历史上下文降级原因' AFTER `query_engine_strategy`;
  END IF;

  IF NOT EXISTS (
    SELECT 1 FROM information_schema.columns
    WHERE table_schema = DATABASE()
      AND table_name = 'qa_retrieval_logs'
      AND column_name = 'memory_history_json'
  ) THEN
    ALTER TABLE `qa_retrieval_logs`
      ADD COLUMN `memory_history_json` longtext CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL COMMENT '内部传递给Python的历史上下文JSON' AFTER `history_fallback_reason`;
  END IF;

  IF NOT EXISTS (
    SELECT 1 FROM information_schema.statistics
    WHERE table_schema = DATABASE()
      AND table_name = 'qa_retrieval_logs'
      AND index_name = 'idx_retrieval_logs_memory_strategy'
  ) THEN
    ALTER TABLE `qa_retrieval_logs`
      ADD INDEX `idx_retrieval_logs_memory_strategy` (`memory_applied`, `query_engine_strategy`);
  END IF;
END//
DELIMITER ;

CALL `ckqa_add_qa_local_history_memory_columns_if_missing`();

DROP PROCEDURE IF EXISTS `ckqa_add_qa_local_history_memory_columns_if_missing`;

CREATE TABLE IF NOT EXISTS `qa_memory_preferences` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `user_id` bigint NOT NULL COMMENT '用户ID',
  `course_id` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '课程ID',
  `knowledge_base_id` bigint NOT NULL COMMENT '知识库ID',
  `index_run_id` bigint NOT NULL COMMENT '索引运行ID',
  `enabled` tinyint(1) NOT NULL DEFAULT 0 COMMENT '是否开启跨对话学习记忆',
  `created_at` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_at` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`) USING BTREE,
  UNIQUE KEY `uk_qa_memory_preferences_scope` (`user_id`, `course_id`, `knowledge_base_id`, `index_run_id`) USING BTREE,
  INDEX `idx_qa_memory_preferences_course` (`course_id`, `knowledge_base_id`, `index_run_id`) USING BTREE,
  CONSTRAINT `fk_qa_memory_preferences_user` FOREIGN KEY (`user_id`) REFERENCES `users` (`id`) ON DELETE CASCADE ON UPDATE RESTRICT,
  CONSTRAINT `fk_qa_memory_preferences_course` FOREIGN KEY (`course_id`) REFERENCES `courses` (`course_id`) ON DELETE CASCADE ON UPDATE RESTRICT,
  CONSTRAINT `fk_qa_memory_preferences_kb` FOREIGN KEY (`knowledge_base_id`) REFERENCES `knowledge_bases` (`id`) ON DELETE CASCADE ON UPDATE RESTRICT,
  CONSTRAINT `fk_qa_memory_preferences_index_run` FOREIGN KEY (`index_run_id`) REFERENCES `index_runs` (`id`) ON DELETE CASCADE ON UPDATE RESTRICT
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = '问答跨对话学习记忆偏好表' ROW_FORMAT = Dynamic;

CREATE TABLE IF NOT EXISTS `qa_learning_memories` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `user_id` bigint NOT NULL COMMENT '用户ID',
  `course_id` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '课程ID',
  `knowledge_base_id` bigint NOT NULL COMMENT '知识库ID',
  `index_run_id` bigint NOT NULL COMMENT '索引运行ID',
  `memory_type` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT 'learning_preference' COMMENT '记忆类型',
  `memory_text` text CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '记忆正文，不作为课程事实来源',
  `source_session_id` bigint NULL COMMENT '来源会话ID',
  `source_message_id` bigint NULL COMMENT '来源消息ID',
  `status` enum('active','deleted') CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT 'active' COMMENT '状态',
  `created_at` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_at` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`) USING BTREE,
  INDEX `idx_qa_learning_memories_scope` (`user_id`, `course_id`, `knowledge_base_id`, `index_run_id`, `status`, `updated_at`) USING BTREE,
  INDEX `idx_qa_learning_memories_source_session` (`source_session_id`) USING BTREE,
  CONSTRAINT `fk_qa_learning_memories_user` FOREIGN KEY (`user_id`) REFERENCES `users` (`id`) ON DELETE CASCADE ON UPDATE RESTRICT,
  CONSTRAINT `fk_qa_learning_memories_course` FOREIGN KEY (`course_id`) REFERENCES `courses` (`course_id`) ON DELETE CASCADE ON UPDATE RESTRICT,
  CONSTRAINT `fk_qa_learning_memories_kb` FOREIGN KEY (`knowledge_base_id`) REFERENCES `knowledge_bases` (`id`) ON DELETE CASCADE ON UPDATE RESTRICT,
  CONSTRAINT `fk_qa_learning_memories_index_run` FOREIGN KEY (`index_run_id`) REFERENCES `index_runs` (`id`) ON DELETE CASCADE ON UPDATE RESTRICT,
  CONSTRAINT `fk_qa_learning_memories_session` FOREIGN KEY (`source_session_id`) REFERENCES `qa_sessions` (`id`) ON DELETE SET NULL ON UPDATE RESTRICT,
  CONSTRAINT `fk_qa_learning_memories_message` FOREIGN KEY (`source_message_id`) REFERENCES `qa_messages` (`id`) ON DELETE SET NULL ON UPDATE RESTRICT
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = '问答跨对话学习记忆表' ROW_FORMAT = Dynamic;
