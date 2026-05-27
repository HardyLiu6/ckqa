-- QA 流式恢复：持久化运行中部分回答与事件游标。
-- 仅新增可空/默认诊断列，不改变历史问答消息主流程。

DROP PROCEDURE IF EXISTS `ckqa_add_qa_stream_resume_columns_if_missing`;
DELIMITER //
CREATE PROCEDURE `ckqa_add_qa_stream_resume_columns_if_missing`()
BEGIN
  IF NOT EXISTS (
    SELECT 1 FROM information_schema.columns
    WHERE table_schema = DATABASE()
      AND table_name = 'qa_retrieval_logs'
      AND column_name = 'partial_response_text'
  ) THEN
    ALTER TABLE `qa_retrieval_logs`
      ADD COLUMN `partial_response_text` longtext CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL COMMENT '运行中已生成的可恢复回答文本' AFTER `latest_logs`;
  END IF;

  IF NOT EXISTS (
    SELECT 1 FROM information_schema.columns
    WHERE table_schema = DATABASE()
      AND table_name = 'qa_retrieval_logs'
      AND column_name = 'stream_event_seq'
  ) THEN
    ALTER TABLE `qa_retrieval_logs`
      ADD COLUMN `stream_event_seq` bigint NOT NULL DEFAULT 0 COMMENT '已同步的流式事件序号' AFTER `partial_response_text`;
  END IF;
END//
DELIMITER ;

CALL `ckqa_add_qa_stream_resume_columns_if_missing`();

DROP PROCEDURE IF EXISTS `ckqa_add_qa_stream_resume_columns_if_missing`;
