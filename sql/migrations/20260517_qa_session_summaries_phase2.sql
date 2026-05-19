SET NAMES utf8mb4;

-- Phase 2：问答会话滚动摘要表。

CREATE TABLE IF NOT EXISTS `qa_session_summaries` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `session_id` bigint NOT NULL COMMENT '会话ID',
  `summary_text` longtext CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL COMMENT '滚动摘要正文',
  `summary_until_sequence_no` int NOT NULL COMMENT '摘要覆盖到的连续消息序号',
  `source_message_count` int NOT NULL DEFAULT 0 COMMENT '本次摘要使用的消息数量',
  `status` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT 'success' COMMENT '摘要状态：success/failed',
  `error_message` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL COMMENT '摘要失败原因',
  `model` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL COMMENT '摘要模型名称',
  `duration_ms` bigint NULL COMMENT '摘要调用耗时毫秒',
  `input_char_count` int NULL COMMENT '摘要输入字符数',
  `output_char_count` int NULL COMMENT '摘要输出字符数',
  `created_at` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_at` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  KEY `idx_qa_session_summaries_session_status_watermark` (`session_id`, `status`, `summary_until_sequence_no`),
  CONSTRAINT `fk_qa_session_summaries_session` FOREIGN KEY (`session_id`) REFERENCES `qa_sessions` (`id`) ON DELETE CASCADE ON UPDATE RESTRICT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='问答会话滚动摘要表';
