-- QA 长期记忆治理 v1：拆分长期记忆/最近历史计数，并记录脱敏来源诊断。
-- 所有新增列均可空，兼容历史检索日志；不得写入 memory_text 或最近会话正文。

DROP PROCEDURE IF EXISTS `ckqa_add_qa_memory_governance_v1_columns_if_missing`;

DELIMITER //
CREATE PROCEDURE `ckqa_add_qa_memory_governance_v1_columns_if_missing`()
BEGIN
  IF NOT EXISTS (
    SELECT 1 FROM information_schema.columns
    WHERE table_schema = DATABASE()
      AND table_name = 'qa_retrieval_logs'
      AND column_name = 'memory_governance_version'
  ) THEN
    ALTER TABLE `qa_retrieval_logs`
      ADD COLUMN `memory_governance_version` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL COMMENT '长期记忆治理版本，仅用于脱敏诊断' AFTER `memory_size_chars`;
  END IF;

  IF NOT EXISTS (
    SELECT 1 FROM information_schema.columns
    WHERE table_schema = DATABASE()
      AND table_name = 'qa_retrieval_logs'
      AND column_name = 'memory_long_term_count'
  ) THEN
    ALTER TABLE `qa_retrieval_logs`
      ADD COLUMN `memory_long_term_count` int NULL COMMENT '本轮实际注入的长期记忆条数，脱敏诊断字段，不含原文' AFTER `memory_governance_version`;
  END IF;

  IF NOT EXISTS (
    SELECT 1 FROM information_schema.columns
    WHERE table_schema = DATABASE()
      AND table_name = 'qa_retrieval_logs'
      AND column_name = 'memory_recent_history_count'
  ) THEN
    ALTER TABLE `qa_retrieval_logs`
      ADD COLUMN `memory_recent_history_count` int NULL COMMENT '本轮实际注入的最近会话历史条数，脱敏诊断字段，不含原文' AFTER `memory_long_term_count`;
  END IF;

  IF NOT EXISTS (
    SELECT 1 FROM information_schema.columns
    WHERE table_schema = DATABASE()
      AND table_name = 'qa_retrieval_logs'
      AND column_name = 'memory_injection_reason'
  ) THEN
    ALTER TABLE `qa_retrieval_logs`
      ADD COLUMN `memory_injection_reason` varchar(128) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL COMMENT '长期记忆注入原因，脱敏诊断字段，不含原文' AFTER `memory_recent_history_count`;
  END IF;

  IF NOT EXISTS (
    SELECT 1 FROM information_schema.columns
    WHERE table_schema = DATABASE()
      AND table_name = 'qa_retrieval_logs'
      AND column_name = 'memory_sources_json'
  ) THEN
    ALTER TABLE `qa_retrieval_logs`
      ADD COLUMN `memory_sources_json` json NULL COMMENT '长期记忆脱敏来源JSON，仅含ID/hash/字符数等诊断字段，不含memory_text或会话正文' AFTER `memory_injection_reason`;
  END IF;
END//
DELIMITER ;

CALL `ckqa_add_qa_memory_governance_v1_columns_if_missing`();

DROP PROCEDURE IF EXISTS `ckqa_add_qa_memory_governance_v1_columns_if_missing`;
