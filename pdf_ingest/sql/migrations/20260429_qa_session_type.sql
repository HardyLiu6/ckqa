SET NAMES utf8mb4;

-- CKQA QA session type migration.
-- 为已有 qa_sessions 表补充 session_type，用于稳定区分正式问答与构建冒烟验证。
-- MySQL DDL 会隐式提交；执行前请先备份真实库并在 disposable schema 上演练。

DELIMITER $$

DROP PROCEDURE IF EXISTS `ckqa_add_qa_session_type_if_missing` $$
CREATE PROCEDURE `ckqa_add_qa_session_type_if_missing`()
BEGIN
  IF NOT EXISTS (
    SELECT 1
    FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME = 'qa_sessions'
      AND COLUMN_NAME = 'session_type'
  ) THEN
    ALTER TABLE `qa_sessions` ADD COLUMN `session_type` enum('formal','smoke') CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT 'formal' COMMENT '会话类型：formal正式问答，smoke构建冒烟验证'
      AFTER `knowledge_base_id`;
  END IF;
END $$

DELIMITER ;

CALL `ckqa_add_qa_session_type_if_missing`();

DROP PROCEDURE IF EXISTS `ckqa_add_qa_session_type_if_missing`;
