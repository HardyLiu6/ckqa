SET NAMES utf8mb4;

-- Phase 1：问答会话恢复、索引版本固化与检索上下文诊断字段。

DROP PROCEDURE IF EXISTS `ckqa_add_qa_context_session_phase1_if_missing`;
DELIMITER //
CREATE PROCEDURE `ckqa_add_qa_context_session_phase1_if_missing`()
BEGIN
  IF NOT EXISTS (
    SELECT 1 FROM information_schema.columns
    WHERE table_schema = DATABASE()
      AND table_name = 'qa_sessions'
      AND column_name = 'index_run_id'
  ) THEN
    ALTER TABLE `qa_sessions`
      ADD COLUMN `index_run_id` bigint NULL DEFAULT NULL COMMENT '本会话固化的索引运行ID' AFTER `knowledge_base_id`;
  END IF;

  IF NOT EXISTS (
    SELECT 1 FROM information_schema.columns
    WHERE table_schema = DATABASE()
      AND table_name = 'qa_sessions'
      AND column_name = 'index_locked_at'
  ) THEN
    ALTER TABLE `qa_sessions`
      ADD COLUMN `index_locked_at` timestamp NULL DEFAULT NULL COMMENT '索引版本固化时间' AFTER `index_run_id`;
  END IF;

  IF NOT EXISTS (
    SELECT 1 FROM information_schema.statistics
    WHERE table_schema = DATABASE()
      AND table_name = 'qa_sessions'
      AND index_name = 'idx_sessions_index_run'
  ) THEN
    ALTER TABLE `qa_sessions`
      ADD INDEX `idx_sessions_index_run` (`index_run_id`);
  END IF;

  IF NOT EXISTS (
    SELECT 1 FROM information_schema.table_constraints
    WHERE constraint_schema = DATABASE()
      AND table_name = 'qa_sessions'
      AND constraint_name = 'fk_sessions_index_run'
  ) THEN
    ALTER TABLE `qa_sessions`
      ADD CONSTRAINT `fk_sessions_index_run` FOREIGN KEY (`index_run_id`) REFERENCES `index_runs` (`id`) ON DELETE SET NULL ON UPDATE RESTRICT;
  END IF;

  IF NOT EXISTS (
    SELECT 1 FROM information_schema.columns
    WHERE table_schema = DATABASE()
      AND table_name = 'qa_retrieval_logs'
      AND column_name = 'original_query_text'
  ) THEN
    ALTER TABLE `qa_retrieval_logs`
      ADD COLUMN `original_query_text` longtext CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL COMMENT '学生原始问题' AFTER `query_text`;
  END IF;

  IF NOT EXISTS (
    SELECT 1 FROM information_schema.columns
    WHERE table_schema = DATABASE()
      AND table_name = 'qa_retrieval_logs'
      AND column_name = 'retrieval_query_text'
  ) THEN
    ALTER TABLE `qa_retrieval_logs`
      ADD COLUMN `retrieval_query_text` longtext CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL COMMENT '实际发给GraphRAG的短检索问题' AFTER `original_query_text`;
  END IF;

  IF NOT EXISTS (
    SELECT 1 FROM information_schema.columns
    WHERE table_schema = DATABASE()
      AND table_name = 'qa_retrieval_logs'
      AND column_name = 'context_snapshot_text'
  ) THEN
    ALTER TABLE `qa_retrieval_logs`
      ADD COLUMN `context_snapshot_text` longtext CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL COMMENT '本轮上下文快照' AFTER `retrieval_query_text`;
  END IF;

  IF NOT EXISTS (
    SELECT 1 FROM information_schema.columns
    WHERE table_schema = DATABASE()
      AND table_name = 'qa_retrieval_logs'
      AND column_name = 'context_strategy'
  ) THEN
    ALTER TABLE `qa_retrieval_logs`
      ADD COLUMN `context_strategy` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL COMMENT '上下文策略' AFTER `context_snapshot_text`;
  END IF;

  IF NOT EXISTS (
    SELECT 1 FROM information_schema.columns
    WHERE table_schema = DATABASE()
      AND table_name = 'qa_retrieval_logs'
      AND column_name = 'context_message_range'
  ) THEN
    ALTER TABLE `qa_retrieval_logs`
      ADD COLUMN `context_message_range` varchar(128) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL COMMENT '上下文消息范围' AFTER `context_strategy`;
  END IF;

  IF NOT EXISTS (
    SELECT 1 FROM information_schema.columns
    WHERE table_schema = DATABASE()
      AND table_name = 'qa_retrieval_logs'
      AND column_name = 'context_char_count'
  ) THEN
    ALTER TABLE `qa_retrieval_logs`
      ADD COLUMN `context_char_count` int NULL DEFAULT NULL COMMENT '上下文字符数估算' AFTER `context_message_range`;
  END IF;

  IF NOT EXISTS (
    SELECT 1 FROM information_schema.columns
    WHERE table_schema = DATABASE()
      AND table_name = 'qa_retrieval_logs'
      AND column_name = 'rewrite_applied'
  ) THEN
    ALTER TABLE `qa_retrieval_logs`
      ADD COLUMN `rewrite_applied` tinyint(1) NOT NULL DEFAULT 0 COMMENT '是否应用追问改写' AFTER `context_char_count`;
  END IF;

  IF NOT EXISTS (
    SELECT 1 FROM information_schema.columns
    WHERE table_schema = DATABASE()
      AND table_name = 'qa_retrieval_logs'
      AND column_name = 'rewrite_reason'
  ) THEN
    ALTER TABLE `qa_retrieval_logs`
      ADD COLUMN `rewrite_reason` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL COMMENT '追问改写原因' AFTER `rewrite_applied`;
  END IF;

  IF NOT EXISTS (
    SELECT 1 FROM information_schema.columns
    WHERE table_schema = DATABASE()
      AND table_name = 'qa_retrieval_logs'
      AND column_name = 'rewrite_source_message_range'
  ) THEN
    ALTER TABLE `qa_retrieval_logs`
      ADD COLUMN `rewrite_source_message_range` varchar(128) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL COMMENT '追问改写来源消息范围' AFTER `rewrite_reason`;
  END IF;
END//
DELIMITER ;

CALL `ckqa_add_qa_context_session_phase1_if_missing`();

DROP PROCEDURE IF EXISTS `ckqa_add_qa_context_session_phase1_if_missing`;
