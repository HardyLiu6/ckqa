-- QA KG 主题实体弱绑定 v1：仅写入运维诊断字段，不改变学生端响应或 GraphRAG query-task 协议。
-- 所有新增列均可空，兼容历史检索日志。

DROP PROCEDURE IF EXISTS `ckqa_add_qa_topic_entity_binding_v1_columns_if_missing`;

DELIMITER //
CREATE PROCEDURE `ckqa_add_qa_topic_entity_binding_v1_columns_if_missing`()
BEGIN
  IF NOT EXISTS (
    SELECT 1 FROM information_schema.columns
    WHERE table_schema = DATABASE()
      AND table_name = 'qa_retrieval_logs'
      AND column_name = 'topic_entity_binding_applied'
  ) THEN
    ALTER TABLE `qa_retrieval_logs`
      ADD COLUMN `topic_entity_binding_applied` tinyint(1) NULL COMMENT '本轮是否应用KG主题实体弱绑定' AFTER `semantic_state_json`;
  END IF;

  IF NOT EXISTS (
    SELECT 1 FROM information_schema.columns
    WHERE table_schema = DATABASE()
      AND table_name = 'qa_retrieval_logs'
      AND column_name = 'topic_entity_binding_status'
  ) THEN
    ALTER TABLE `qa_retrieval_logs`
      ADD COLUMN `topic_entity_binding_status` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL COMMENT '主题实体弱绑定状态' AFTER `topic_entity_binding_applied`;
  END IF;

  IF NOT EXISTS (
    SELECT 1 FROM information_schema.columns
    WHERE table_schema = DATABASE()
      AND table_name = 'qa_retrieval_logs'
      AND column_name = 'topic_entity_binding_strategy'
  ) THEN
    ALTER TABLE `qa_retrieval_logs`
      ADD COLUMN `topic_entity_binding_strategy` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL COMMENT '主题实体弱绑定策略' AFTER `topic_entity_binding_status`;
  END IF;

  IF NOT EXISTS (
    SELECT 1 FROM information_schema.columns
    WHERE table_schema = DATABASE()
      AND table_name = 'qa_retrieval_logs'
      AND column_name = 'topic_entity_candidate_count'
  ) THEN
    ALTER TABLE `qa_retrieval_logs`
      ADD COLUMN `topic_entity_candidate_count` int NULL COMMENT '主题实体记录的TopN候选数量' AFTER `topic_entity_binding_strategy`;
  END IF;

  IF NOT EXISTS (
    SELECT 1 FROM information_schema.columns
    WHERE table_schema = DATABASE()
      AND table_name = 'qa_retrieval_logs'
      AND column_name = 'topic_entity_top_score'
  ) THEN
    ALTER TABLE `qa_retrieval_logs`
      ADD COLUMN `topic_entity_top_score` decimal(5,4) NULL COMMENT '主题实体Top候选得分' AFTER `topic_entity_candidate_count`;
  END IF;

  IF NOT EXISTS (
    SELECT 1 FROM information_schema.columns
    WHERE table_schema = DATABASE()
      AND table_name = 'qa_retrieval_logs'
      AND column_name = 'topic_entity_selected_id'
  ) THEN
    ALTER TABLE `qa_retrieval_logs`
      ADD COLUMN `topic_entity_selected_id` varchar(128) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL COMMENT '主题实体选中ID' AFTER `topic_entity_top_score`;
  END IF;

  IF NOT EXISTS (
    SELECT 1 FROM information_schema.columns
    WHERE table_schema = DATABASE()
      AND table_name = 'qa_retrieval_logs'
      AND column_name = 'topic_entity_selected_name'
  ) THEN
    ALTER TABLE `qa_retrieval_logs`
      ADD COLUMN `topic_entity_selected_name` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL COMMENT '主题实体选中名称' AFTER `topic_entity_selected_id`;
  END IF;

  IF NOT EXISTS (
    SELECT 1 FROM information_schema.columns
    WHERE table_schema = DATABASE()
      AND table_name = 'qa_retrieval_logs'
      AND column_name = 'topic_entity_selected_type'
  ) THEN
    ALTER TABLE `qa_retrieval_logs`
      ADD COLUMN `topic_entity_selected_type` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL COMMENT '主题实体选中类型' AFTER `topic_entity_selected_name`;
  END IF;

  IF NOT EXISTS (
    SELECT 1 FROM information_schema.columns
    WHERE table_schema = DATABASE()
      AND table_name = 'qa_retrieval_logs'
      AND column_name = 'topic_entity_candidates_json'
  ) THEN
    ALTER TABLE `qa_retrieval_logs`
      ADD COLUMN `topic_entity_candidates_json` longtext CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL COMMENT '主题实体脱敏候选JSON' AFTER `topic_entity_selected_type`;
  END IF;

  IF NOT EXISTS (
    SELECT 1 FROM information_schema.columns
    WHERE table_schema = DATABASE()
      AND table_name = 'qa_retrieval_logs'
      AND column_name = 'topic_entity_fallback_reason'
  ) THEN
    ALTER TABLE `qa_retrieval_logs`
      ADD COLUMN `topic_entity_fallback_reason` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL COMMENT '主题实体弱绑定兜底原因' AFTER `topic_entity_candidates_json`;
  END IF;

  IF NOT EXISTS (
    SELECT 1 FROM information_schema.columns
    WHERE table_schema = DATABASE()
      AND table_name = 'qa_retrieval_logs'
      AND column_name = 'topic_entity_lookup_duration_ms'
  ) THEN
    ALTER TABLE `qa_retrieval_logs`
      ADD COLUMN `topic_entity_lookup_duration_ms` bigint NULL COMMENT '主题实体查询耗时毫秒' AFTER `topic_entity_fallback_reason`;
  END IF;
END//
DELIMITER ;

CALL `ckqa_add_qa_topic_entity_binding_v1_columns_if_missing`();

DROP PROCEDURE IF EXISTS `ckqa_add_qa_topic_entity_binding_v1_columns_if_missing`;
