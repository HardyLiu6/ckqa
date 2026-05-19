-- 智能推荐路由闭环：记录低置信度分档与前端诊断快照。
-- 该迁移只新增可空诊断列，不改变既有问答任务状态和答案数据。

DELIMITER //
CREATE PROCEDURE `ckqa_add_qa_routing_confidence_columns_if_missing`()
BEGIN
  IF NOT EXISTS (
    SELECT 1 FROM information_schema.columns
    WHERE table_schema = DATABASE()
      AND table_name = 'qa_retrieval_logs'
      AND column_name = 'routing_confidence'
  ) THEN
    ALTER TABLE `qa_retrieval_logs`
      ADD COLUMN `routing_confidence` decimal(5,4) NULL COMMENT '智能推荐置信度' AFTER `context_snapshot_version`;
  END IF;

  IF NOT EXISTS (
    SELECT 1 FROM information_schema.columns
    WHERE table_schema = DATABASE()
      AND table_name = 'qa_retrieval_logs'
      AND column_name = 'routing_confidence_band'
  ) THEN
    ALTER TABLE `qa_retrieval_logs`
      ADD COLUMN `routing_confidence_band` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL COMMENT '智能推荐置信度分档' AFTER `routing_confidence`;
  END IF;

  IF NOT EXISTS (
    SELECT 1 FROM information_schema.columns
    WHERE table_schema = DATABASE()
      AND table_name = 'qa_retrieval_logs'
      AND column_name = 'routing_review_priority'
  ) THEN
    ALTER TABLE `qa_retrieval_logs`
      ADD COLUMN `routing_review_priority` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL COMMENT '智能推荐复核优先级' AFTER `routing_confidence_band`;
  END IF;

  IF NOT EXISTS (
    SELECT 1 FROM information_schema.columns
    WHERE table_schema = DATABASE()
      AND table_name = 'qa_retrieval_logs'
      AND column_name = 'routing_snapshot_json'
  ) THEN
    ALTER TABLE `qa_retrieval_logs`
      ADD COLUMN `routing_snapshot_json` longtext CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL COMMENT '学生端智能推荐诊断快照JSON' AFTER `routing_review_priority`;
  END IF;

  IF NOT EXISTS (
    SELECT 1 FROM information_schema.statistics
    WHERE table_schema = DATABASE()
      AND table_name = 'qa_retrieval_logs'
      AND index_name = 'idx_retrieval_logs_routing_review'
  ) THEN
    ALTER TABLE `qa_retrieval_logs`
      ADD INDEX `idx_retrieval_logs_routing_review` (`routing_confidence_band`, `routing_review_priority`);
  END IF;
END//
DELIMITER ;

CALL `ckqa_add_qa_routing_confidence_columns_if_missing`();

DROP PROCEDURE IF EXISTS `ckqa_add_qa_routing_confidence_columns_if_missing`;
