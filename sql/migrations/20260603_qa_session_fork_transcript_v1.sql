SET NAMES utf8mb4;

-- Phase 5：会话 fork 与完整 transcript 管理 v1。
-- 仅新增可空追踪字段、transcript 版本字段和查询索引，不复制或约束检索日志/命中/反馈。

DROP PROCEDURE IF EXISTS `ckqa_add_qa_session_fork_transcript_v1_if_missing`;
DELIMITER //
CREATE PROCEDURE `ckqa_add_qa_session_fork_transcript_v1_if_missing`()
BEGIN
  IF NOT EXISTS (
    SELECT 1 FROM information_schema.columns
    WHERE table_schema = DATABASE()
      AND table_name = 'qa_sessions'
      AND column_name = 'parent_session_id'
  ) THEN
    ALTER TABLE `qa_sessions`
      ADD COLUMN `parent_session_id` bigint NULL DEFAULT NULL COMMENT '父会话ID，仅用于会话分支追踪' AFTER `session_type`;
  END IF;

  IF NOT EXISTS (
    SELECT 1 FROM information_schema.columns
    WHERE table_schema = DATABASE()
      AND table_name = 'qa_sessions'
      AND column_name = 'forked_from_message_id'
  ) THEN
    ALTER TABLE `qa_sessions`
      ADD COLUMN `forked_from_message_id` bigint NULL DEFAULT NULL COMMENT '分支来源消息ID' AFTER `parent_session_id`;
  END IF;

  IF NOT EXISTS (
    SELECT 1 FROM information_schema.columns
    WHERE table_schema = DATABASE()
      AND table_name = 'qa_sessions'
      AND column_name = 'forked_from_sequence_no'
  ) THEN
    ALTER TABLE `qa_sessions`
      ADD COLUMN `forked_from_sequence_no` int NULL DEFAULT NULL COMMENT '分支来源消息序号' AFTER `forked_from_message_id`;
  END IF;

  IF NOT EXISTS (
    SELECT 1 FROM information_schema.columns
    WHERE table_schema = DATABASE()
      AND table_name = 'qa_sessions'
      AND column_name = 'fork_reason'
  ) THEN
    ALTER TABLE `qa_sessions`
      ADD COLUMN `fork_reason` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '分支原因' AFTER `forked_from_sequence_no`;
  END IF;

  IF NOT EXISTS (
    SELECT 1 FROM information_schema.columns
    WHERE table_schema = DATABASE()
      AND table_name = 'qa_sessions'
      AND column_name = 'transcript_version'
  ) THEN
    ALTER TABLE `qa_sessions`
      ADD COLUMN `transcript_version` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT 'v1' COMMENT 'transcript契约版本' AFTER `fork_reason`;
  END IF;

  IF NOT EXISTS (
    SELECT 1 FROM information_schema.statistics
    WHERE table_schema = DATABASE()
      AND table_name = 'qa_sessions'
      AND index_name = 'idx_qa_sessions_parent'
  ) THEN
    ALTER TABLE `qa_sessions`
      ADD INDEX `idx_qa_sessions_parent` (`parent_session_id`);
  END IF;

  IF NOT EXISTS (
    SELECT 1 FROM information_schema.statistics
    WHERE table_schema = DATABASE()
      AND table_name = 'qa_sessions'
      AND index_name = 'idx_qa_sessions_forked_from_message'
  ) THEN
    ALTER TABLE `qa_sessions`
      ADD INDEX `idx_qa_sessions_forked_from_message` (`forked_from_message_id`);
  END IF;

  IF NOT EXISTS (
    SELECT 1 FROM information_schema.columns
    WHERE table_schema = DATABASE()
      AND table_name = 'qa_messages'
      AND column_name = 'copied_from_message_id'
  ) THEN
    ALTER TABLE `qa_messages`
      ADD COLUMN `copied_from_message_id` bigint NULL DEFAULT NULL COMMENT 'fork复制来源消息ID' AFTER `token_count`;
  END IF;

  IF NOT EXISTS (
    SELECT 1 FROM information_schema.statistics
    WHERE table_schema = DATABASE()
      AND table_name = 'qa_messages'
      AND index_name = 'idx_qa_messages_copied_from'
  ) THEN
    ALTER TABLE `qa_messages`
      ADD INDEX `idx_qa_messages_copied_from` (`copied_from_message_id`);
  END IF;
END//
DELIMITER ;

CALL `ckqa_add_qa_session_fork_transcript_v1_if_missing`();

DROP PROCEDURE IF EXISTS `ckqa_add_qa_session_fork_transcript_v1_if_missing`;
