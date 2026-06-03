-- QA 语义主题栈 v1：摘要主题锚点与检索日志诊断字段。
-- 所有新增列均可空，便于兼容历史会话和旧任务记录。

DROP PROCEDURE IF EXISTS `ckqa_add_qa_semantic_topic_stack_v1_columns_if_missing`;

DELIMITER //
CREATE PROCEDURE `ckqa_add_qa_semantic_topic_stack_v1_columns_if_missing`()
BEGIN
  IF NOT EXISTS (
    SELECT 1 FROM information_schema.columns
    WHERE table_schema = DATABASE()
      AND table_name = 'qa_session_summaries'
      AND column_name = 'latest_topic'
  ) THEN
    ALTER TABLE `qa_session_summaries`
      ADD COLUMN `latest_topic` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL COMMENT '摘要窗口解析出的最近主题' AFTER `error_message`;
  END IF;

  IF NOT EXISTS (
    SELECT 1 FROM information_schema.columns
    WHERE table_schema = DATABASE()
      AND table_name = 'qa_session_summaries'
      AND column_name = 'latest_topic_message_range'
  ) THEN
    ALTER TABLE `qa_session_summaries`
      ADD COLUMN `latest_topic_message_range` varchar(128) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL COMMENT '最近主题来源消息范围' AFTER `latest_topic`;
  END IF;

  IF NOT EXISTS (
    SELECT 1 FROM information_schema.columns
    WHERE table_schema = DATABASE()
      AND table_name = 'qa_session_summaries'
      AND column_name = 'active_topics_json'
  ) THEN
    ALTER TABLE `qa_session_summaries`
      ADD COLUMN `active_topics_json` longtext CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL COMMENT '摘要窗口可用主题栈JSON' AFTER `latest_topic_message_range`;
  END IF;

  IF NOT EXISTS (
    SELECT 1 FROM information_schema.columns
    WHERE table_schema = DATABASE()
      AND table_name = 'qa_retrieval_logs'
      AND column_name = 'requested_mode'
  ) THEN
    ALTER TABLE `qa_retrieval_logs`
      ADD COLUMN `requested_mode` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL COMMENT '学生端请求模式' AFTER `query_mode`;
  END IF;

  IF NOT EXISTS (
    SELECT 1 FROM information_schema.columns
    WHERE table_schema = DATABASE()
      AND table_name = 'qa_retrieval_logs'
      AND column_name = 'resolved_mode'
  ) THEN
    ALTER TABLE `qa_retrieval_logs`
      ADD COLUMN `resolved_mode` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL COMMENT '后端实际执行模式' AFTER `requested_mode`;
  END IF;

  IF NOT EXISTS (
    SELECT 1 FROM information_schema.columns
    WHERE table_schema = DATABASE()
      AND table_name = 'qa_retrieval_logs'
      AND column_name = 'resolved_topic'
  ) THEN
    ALTER TABLE `qa_retrieval_logs`
      ADD COLUMN `resolved_topic` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL COMMENT '本轮解析出的主题' AFTER `context_snapshot_version`;
  END IF;

  IF NOT EXISTS (
    SELECT 1 FROM information_schema.columns
    WHERE table_schema = DATABASE()
      AND table_name = 'qa_retrieval_logs'
      AND column_name = 'topic_source'
  ) THEN
    ALTER TABLE `qa_retrieval_logs`
      ADD COLUMN `topic_source` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL COMMENT '主题来源' AFTER `resolved_topic`;
  END IF;

  IF NOT EXISTS (
    SELECT 1 FROM information_schema.columns
    WHERE table_schema = DATABASE()
      AND table_name = 'qa_retrieval_logs'
      AND column_name = 'topic_confidence'
  ) THEN
    ALTER TABLE `qa_retrieval_logs`
      ADD COLUMN `topic_confidence` decimal(5,4) NULL COMMENT '主题解析置信度' AFTER `topic_source`;
  END IF;

  IF NOT EXISTS (
    SELECT 1 FROM information_schema.columns
    WHERE table_schema = DATABASE()
      AND table_name = 'qa_retrieval_logs'
      AND column_name = 'topic_stack_json'
  ) THEN
    ALTER TABLE `qa_retrieval_logs`
      ADD COLUMN `topic_stack_json` longtext CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL COMMENT '内部主题栈JSON' AFTER `topic_confidence`;
  END IF;
END//
DELIMITER ;

CALL `ckqa_add_qa_semantic_topic_stack_v1_columns_if_missing`();

DROP PROCEDURE IF EXISTS `ckqa_add_qa_semantic_topic_stack_v1_columns_if_missing`;
