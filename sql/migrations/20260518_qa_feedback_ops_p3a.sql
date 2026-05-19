-- P3-A：问答反馈闭环与运维标注。
-- 该迁移只新增反馈/标注表，不修改既有问答链路数据。

CREATE TABLE IF NOT EXISTS `qa_message_feedback` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `message_id` bigint NOT NULL COMMENT '助手消息ID',
  `retrieval_log_id` bigint NOT NULL COMMENT '检索日志ID',
  `session_id` bigint NOT NULL COMMENT '会话ID',
  `user_id` bigint NOT NULL COMMENT '反馈用户ID',
  `course_id` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL COMMENT '课程ID',
  `knowledge_base_id` bigint NULL COMMENT '知识库ID',
  `rating` enum('helpful','unhelpful','needs_improvement') CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '反馈结论',
  `tags` json NULL COMMENT '反馈标签JSON数组',
  `comment` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL COMMENT '补充说明',
  `created_at` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_at` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`) USING BTREE,
  UNIQUE KEY `uk_qa_message_feedback_user_message` (`user_id`, `message_id`) USING BTREE,
  INDEX `idx_qa_message_feedback_message` (`message_id`) USING BTREE,
  INDEX `idx_qa_message_feedback_log` (`retrieval_log_id`) USING BTREE,
  INDEX `idx_qa_message_feedback_course_rating` (`course_id`, `rating`) USING BTREE,
  CONSTRAINT `fk_qa_message_feedback_message` FOREIGN KEY (`message_id`) REFERENCES `qa_messages` (`id`) ON DELETE CASCADE ON UPDATE RESTRICT,
  CONSTRAINT `fk_qa_message_feedback_log` FOREIGN KEY (`retrieval_log_id`) REFERENCES `qa_retrieval_logs` (`id`) ON DELETE CASCADE ON UPDATE RESTRICT,
  CONSTRAINT `fk_qa_message_feedback_session` FOREIGN KEY (`session_id`) REFERENCES `qa_sessions` (`id`) ON DELETE CASCADE ON UPDATE RESTRICT,
  CONSTRAINT `fk_qa_message_feedback_user` FOREIGN KEY (`user_id`) REFERENCES `users` (`id`) ON DELETE CASCADE ON UPDATE RESTRICT,
  CONSTRAINT `fk_qa_message_feedback_kb` FOREIGN KEY (`knowledge_base_id`) REFERENCES `knowledge_bases` (`id`) ON DELETE SET NULL ON UPDATE RESTRICT
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = '问答消息学生反馈表' ROW_FORMAT = Dynamic;

CREATE TABLE IF NOT EXISTS `qa_source_reviews` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `retrieval_hit_id` bigint NOT NULL COMMENT '来源命中ID',
  `retrieval_log_id` bigint NOT NULL COMMENT '检索日志ID',
  `reviewer_user_id` bigint NOT NULL COMMENT '标注用户ID',
  `relevance` enum('relevant','partially_relevant','irrelevant','unknown') CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT 'unknown' COMMENT '来源相关性',
  `citation_quality` enum('supports_claim','weak_support','wrong_source','duplicate','unknown') CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT 'unknown' COMMENT '引用质量',
  `note` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL COMMENT '标注意见',
  `created_at` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_at` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`) USING BTREE,
  UNIQUE KEY `uk_qa_source_reviews_hit_reviewer` (`retrieval_hit_id`, `reviewer_user_id`) USING BTREE,
  INDEX `idx_qa_source_reviews_log` (`retrieval_log_id`) USING BTREE,
  INDEX `idx_qa_source_reviews_reviewer` (`reviewer_user_id`) USING BTREE,
  CONSTRAINT `fk_qa_source_reviews_hit` FOREIGN KEY (`retrieval_hit_id`) REFERENCES `qa_retrieval_hits` (`id`) ON DELETE CASCADE ON UPDATE RESTRICT,
  CONSTRAINT `fk_qa_source_reviews_log` FOREIGN KEY (`retrieval_log_id`) REFERENCES `qa_retrieval_logs` (`id`) ON DELETE CASCADE ON UPDATE RESTRICT,
  CONSTRAINT `fk_qa_source_reviews_user` FOREIGN KEY (`reviewer_user_id`) REFERENCES `users` (`id`) ON DELETE CASCADE ON UPDATE RESTRICT
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = '问答来源人工标注表' ROW_FORMAT = Dynamic;
