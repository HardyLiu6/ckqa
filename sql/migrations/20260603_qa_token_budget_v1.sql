-- QA 精确 token 预算 v1：记录 context/memory token 统计诊断字段。
-- 所有新增列均可空；tokenizer 失败时继续保留既有字符预算字段。

DROP PROCEDURE IF EXISTS `ckqa_add_qa_token_budget_v1_columns_if_missing`;

DELIMITER //
CREATE PROCEDURE `ckqa_add_qa_token_budget_v1_columns_if_missing`()
BEGIN
  IF NOT EXISTS (
    SELECT 1 FROM information_schema.columns
    WHERE table_schema = DATABASE()
      AND table_name = 'qa_retrieval_logs'
      AND column_name = 'context_token_count'
  ) THEN
    ALTER TABLE `qa_retrieval_logs`
      ADD COLUMN `context_token_count` int NULL COMMENT '上下文token统计诊断字段；失败时保留字符预算' AFTER `context_char_count`;
  END IF;

  IF NOT EXISTS (
    SELECT 1 FROM information_schema.columns
    WHERE table_schema = DATABASE()
      AND table_name = 'qa_retrieval_logs'
      AND column_name = 'context_tokenizer'
  ) THEN
    ALTER TABLE `qa_retrieval_logs`
      ADD COLUMN `context_tokenizer` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL COMMENT '上下文token统计器诊断字段；失败时保留字符预算' AFTER `context_token_count`;
  END IF;

  IF NOT EXISTS (
    SELECT 1 FROM information_schema.columns
    WHERE table_schema = DATABASE()
      AND table_name = 'qa_retrieval_logs'
      AND column_name = 'context_budget_fallback_reason'
  ) THEN
    ALTER TABLE `qa_retrieval_logs`
      ADD COLUMN `context_budget_fallback_reason` varchar(128) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL COMMENT '上下文token预算降级原因；失败时保留字符预算' AFTER `context_tokenizer`;
  END IF;

  IF NOT EXISTS (
    SELECT 1 FROM information_schema.columns
    WHERE table_schema = DATABASE()
      AND table_name = 'qa_retrieval_logs'
      AND column_name = 'memory_token_count'
  ) THEN
    ALTER TABLE `qa_retrieval_logs`
      ADD COLUMN `memory_token_count` int NULL COMMENT '长期记忆token统计诊断字段；失败时保留字符预算' AFTER `memory_size_chars`;
  END IF;

  IF NOT EXISTS (
    SELECT 1 FROM information_schema.columns
    WHERE table_schema = DATABASE()
      AND table_name = 'qa_retrieval_logs'
      AND column_name = 'memory_tokenizer'
  ) THEN
    ALTER TABLE `qa_retrieval_logs`
      ADD COLUMN `memory_tokenizer` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL COMMENT '长期记忆token统计器诊断字段；失败时保留字符预算' AFTER `memory_token_count`;
  END IF;

  IF NOT EXISTS (
    SELECT 1 FROM information_schema.columns
    WHERE table_schema = DATABASE()
      AND table_name = 'qa_retrieval_logs'
      AND column_name = 'memory_budget_fallback_reason'
  ) THEN
    ALTER TABLE `qa_retrieval_logs`
      ADD COLUMN `memory_budget_fallback_reason` varchar(128) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL COMMENT '长期记忆token预算降级原因；失败时保留字符预算' AFTER `memory_tokenizer`;
  END IF;
END//
DELIMITER ;

CALL `ckqa_add_qa_token_budget_v1_columns_if_missing`();

DROP PROCEDURE IF EXISTS `ckqa_add_qa_token_budget_v1_columns_if_missing`;
