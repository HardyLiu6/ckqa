SET NAMES utf8mb4;

-- Phase 3：LLM 追问改写诊断字段与问答来源卡片证据字段。

DROP PROCEDURE IF EXISTS `ckqa_add_qa_phase3_rewrite_evidence_if_missing`;
DELIMITER //
CREATE PROCEDURE `ckqa_add_qa_phase3_rewrite_evidence_if_missing`()
BEGIN
  IF NOT EXISTS (
    SELECT 1 FROM information_schema.columns
    WHERE table_schema = DATABASE()
      AND table_name = 'qa_retrieval_logs'
      AND column_name = 'standalone_query_text'
  ) THEN
    ALTER TABLE `qa_retrieval_logs`
      ADD COLUMN `standalone_query_text` longtext CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL COMMENT '独立检索问题' AFTER `retrieval_query_text`;
  END IF;

  IF NOT EXISTS (
    SELECT 1 FROM information_schema.columns
    WHERE table_schema = DATABASE()
      AND table_name = 'qa_retrieval_logs'
      AND column_name = 'rewrite_method'
  ) THEN
    ALTER TABLE `qa_retrieval_logs`
      ADD COLUMN `rewrite_method` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL COMMENT '改写方法：none/rule/llm' AFTER `rewrite_source_message_range`;
  END IF;

  IF NOT EXISTS (
    SELECT 1 FROM information_schema.columns
    WHERE table_schema = DATABASE()
      AND table_name = 'qa_retrieval_logs'
      AND column_name = 'rewrite_model'
  ) THEN
    ALTER TABLE `qa_retrieval_logs`
      ADD COLUMN `rewrite_model` varchar(128) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL COMMENT '改写模型' AFTER `rewrite_method`;
  END IF;

  IF NOT EXISTS (
    SELECT 1 FROM information_schema.columns
    WHERE table_schema = DATABASE()
      AND table_name = 'qa_retrieval_logs'
      AND column_name = 'rewrite_confidence'
  ) THEN
    ALTER TABLE `qa_retrieval_logs`
      ADD COLUMN `rewrite_confidence` decimal(5,4) NULL COMMENT '改写置信度' AFTER `rewrite_model`;
  END IF;

  IF NOT EXISTS (
    SELECT 1 FROM information_schema.columns
    WHERE table_schema = DATABASE()
      AND table_name = 'qa_retrieval_logs'
      AND column_name = 'context_snapshot_version'
  ) THEN
    ALTER TABLE `qa_retrieval_logs`
      ADD COLUMN `context_snapshot_version` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL COMMENT '上下文快照版本' AFTER `rewrite_confidence`;
  END IF;

  IF NOT EXISTS (
    SELECT 1 FROM information_schema.columns
    WHERE table_schema = DATABASE()
      AND table_name = 'qa_retrieval_hits'
      AND column_name = 'source_ref'
  ) THEN
    ALTER TABLE `qa_retrieval_hits`
      ADD COLUMN `source_ref` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL COMMENT 'GraphRAG原始来源编号' AFTER `chunk_id`;
  END IF;

  IF NOT EXISTS (
    SELECT 1 FROM information_schema.columns
    WHERE table_schema = DATABASE()
      AND table_name = 'qa_retrieval_hits'
      AND column_name = 'source_file'
  ) THEN
    ALTER TABLE `qa_retrieval_hits`
      ADD COLUMN `source_file` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL COMMENT '来源文件名' AFTER `source_ref`;
  END IF;

  IF NOT EXISTS (
    SELECT 1 FROM information_schema.columns
    WHERE table_schema = DATABASE()
      AND table_name = 'qa_retrieval_hits'
      AND column_name = 'heading_path'
  ) THEN
    ALTER TABLE `qa_retrieval_hits`
      ADD COLUMN `heading_path` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL COMMENT '章节路径' AFTER `source_file`;
  END IF;

  IF NOT EXISTS (
    SELECT 1 FROM information_schema.columns
    WHERE table_schema = DATABASE()
      AND table_name = 'qa_retrieval_hits'
      AND column_name = 'page_start'
  ) THEN
    ALTER TABLE `qa_retrieval_hits`
      ADD COLUMN `page_start` int NULL COMMENT '起始页' AFTER `heading_path`;
  END IF;

  IF NOT EXISTS (
    SELECT 1 FROM information_schema.columns
    WHERE table_schema = DATABASE()
      AND table_name = 'qa_retrieval_hits'
      AND column_name = 'page_end'
  ) THEN
    ALTER TABLE `qa_retrieval_hits`
      ADD COLUMN `page_end` int NULL COMMENT '结束页' AFTER `page_start`;
  END IF;

  IF NOT EXISTS (
    SELECT 1 FROM information_schema.columns
    WHERE table_schema = DATABASE()
      AND table_name = 'qa_retrieval_hits'
      AND column_name = 'snippet'
  ) THEN
    ALTER TABLE `qa_retrieval_hits`
      ADD COLUMN `snippet` text CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL COMMENT '来源片段' AFTER `page_end`;
  END IF;
END//
DELIMITER ;

CALL `ckqa_add_qa_phase3_rewrite_evidence_if_missing`();

DROP PROCEDURE IF EXISTS `ckqa_add_qa_phase3_rewrite_evidence_if_missing`;
