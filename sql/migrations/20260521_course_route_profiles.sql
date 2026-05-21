-- 课程画像 text-embedding-v4 路由：画像表与判定日志表。

CREATE TABLE IF NOT EXISTS `course_route_profiles` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `course_id` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '课程ID',
  `profile_text` text CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '课程画像文本',
  `profile_hash` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '课程画像hash',
  `embedding_model` varchar(128) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT 'embedding模型',
  `embedding_dimensions` int NOT NULL COMMENT '向量维度',
  `lancedb_table` varchar(128) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT 'LanceDB表名',
  `vector_id` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT 'LanceDB向量ID',
  `status` enum('active','failed','archived') CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT 'active' COMMENT '画像状态',
  `last_embedded_at` timestamp NULL DEFAULT NULL COMMENT '最近向量更新时间',
  `created_at` timestamp NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_at` timestamp NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`) USING BTREE,
  UNIQUE INDEX `uk_course_route_profile_model`(`course_id` ASC, `embedding_model` ASC, `embedding_dimensions` ASC) USING BTREE,
  INDEX `idx_course_route_profile_status`(`status` ASC, `updated_at` ASC) USING BTREE,
  CONSTRAINT `fk_course_route_profile_course` FOREIGN KEY (`course_id`) REFERENCES `courses` (`course_id`) ON DELETE RESTRICT ON UPDATE RESTRICT
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = '课程路由画像表' ROW_FORMAT = Dynamic;

CREATE TABLE IF NOT EXISTS `course_route_decisions` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `user_id` bigint NULL DEFAULT NULL COMMENT '用户ID',
  `question_hash` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '问题hash',
  `question_text` text CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '原始问题',
  `status` enum('matched','needs_confirmation','no_match') CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '路由状态',
  `selected_course_id` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '自动选中的课程ID',
  `confidence` decimal(8,6) NOT NULL DEFAULT 0 COMMENT 'top1置信度',
  `margin` decimal(8,6) NOT NULL DEFAULT 0 COMMENT 'top1与top2分差',
  `candidates_json` json DEFAULT NULL COMMENT '候选课程快照',
  `embedding_model` varchar(128) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT 'embedding模型',
  `created_at` timestamp NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_at` timestamp NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`) USING BTREE,
  INDEX `idx_course_route_decisions_user_created`(`user_id` ASC, `created_at` ASC) USING BTREE,
  INDEX `idx_course_route_decisions_status`(`status` ASC, `created_at` ASC) USING BTREE
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = '课程路由判定日志表' ROW_FORMAT = Dynamic;
