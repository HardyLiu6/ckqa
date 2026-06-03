-- QA SessionSemanticState v1：持久化会话语义状态诊断快照。
-- 所有新增列均可空，兼容历史摘要与检索日志。

DROP PROCEDURE IF EXISTS `ckqa_add_session_semantic_state_v1_columns_if_missing`;

DELIMITER //
CREATE PROCEDURE `ckqa_add_session_semantic_state_v1_columns_if_missing`()
BEGIN
  IF NOT EXISTS (
    SELECT 1 FROM information_schema.columns
    WHERE table_schema = DATABASE()
      AND table_name = 'qa_session_summaries'
      AND column_name = 'semantic_state_version'
  ) THEN
    ALTER TABLE `qa_session_summaries`
      ADD COLUMN `semantic_state_version` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL COMMENT '会话语义状态版本' AFTER `active_topics_json`;
  END IF;

  IF NOT EXISTS (
    SELECT 1 FROM information_schema.columns
    WHERE table_schema = DATABASE()
      AND table_name = 'qa_session_summaries'
      AND column_name = 'semantic_state_json'
  ) THEN
    ALTER TABLE `qa_session_summaries`
      ADD COLUMN `semantic_state_json` longtext CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL COMMENT '会话语义状态JSON' AFTER `semantic_state_version`;
  END IF;

  IF NOT EXISTS (
    SELECT 1 FROM information_schema.columns
    WHERE table_schema = DATABASE()
      AND table_name = 'qa_retrieval_logs'
      AND column_name = 'semantic_state_version'
  ) THEN
    ALTER TABLE `qa_retrieval_logs`
      ADD COLUMN `semantic_state_version` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL COMMENT '会话语义状态版本' AFTER `topic_stack_json`;
  END IF;

  IF NOT EXISTS (
    SELECT 1 FROM information_schema.columns
    WHERE table_schema = DATABASE()
      AND table_name = 'qa_retrieval_logs'
      AND column_name = 'semantic_state_json'
  ) THEN
    ALTER TABLE `qa_retrieval_logs`
      ADD COLUMN `semantic_state_json` longtext CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL COMMENT '会话语义状态JSON' AFTER `semantic_state_version`;
  END IF;
END//
DELIMITER ;

CALL `ckqa_add_session_semantic_state_v1_columns_if_missing`();

DROP PROCEDURE IF EXISTS `ckqa_add_session_semantic_state_v1_columns_if_missing`;
