-- CKQA 手动调优提示词流水线表
-- Date: 2026-05-15
-- 新增：prompt_tune_audit_samples（标注样本）、prompt_drafts（历史草稿）

-- ----------------------------
-- Table: prompt_tune_audit_samples
-- 存储 02 步标注 IDE 中每条 audit 样本的 gold 标注数据。
-- 一条样本属于一次 build run，通过 gold_stable_key 支持跨构建复用。
-- ----------------------------
CREATE TABLE IF NOT EXISTS `prompt_tune_audit_samples` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `build_run_id` bigint NOT NULL COMMENT '所属构建流水线ID',
  `knowledge_base_id` bigint NOT NULL COMMENT '所属知识库ID',
  `source_sample_id` varchar(128) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '来源样本ID（对应 prompt_tuning_samples 中的 id）',
  `text` mediumtext CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '样本原文',
  `heading_path` varchar(512) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '章节路径',
  `page_start` int NULL DEFAULT NULL COMMENT '起始页码',
  `page_end` int NULL DEFAULT NULL COMMENT '结束页码',
  `document_type` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '文档类型',
  `audit_priority` enum('high','medium','low') CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT 'medium' COMMENT '标注优先级',
  `audit_reason` varchar(256) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '被选为 audit 样本的原因',
  `hit_signals` json NULL DEFAULT NULL COMMENT '命中信号列表（如 definition_signal, formula_signal）',
  `gold_entities` json NULL DEFAULT NULL COMMENT '用户标注的实体列表',
  `gold_relations` json NULL DEFAULT NULL COMMENT '用户标注的关系列表',
  `annotation_notes` text CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL COMMENT '标注备注',
  `reviewer_decision` enum('pending','in_progress','completed','skipped') CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT 'pending' COMMENT '审阅状态',
  `reviewer_confidence` decimal(3,2) NULL DEFAULT NULL COMMENT '审阅者置信度（0-1）',
  `skip_reason` varchar(128) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '跳过原因',
  `gold_stable_key` varchar(256) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '稳定来源键（source_doc_id + page_start + page_end + text_hash），用于跨构建复用',
  `reused_from_build_run_id` bigint NULL DEFAULT NULL COMMENT '复用来源构建ID（历史标注复用时填写）',
  `created_at` timestamp NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_at` timestamp NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`) USING BTREE,
  KEY `idx_audit_samples_build_run` (`build_run_id`) USING BTREE,
  KEY `idx_audit_samples_kb_priority` (`knowledge_base_id`, `audit_priority`) USING BTREE,
  KEY `idx_audit_samples_stable_key` (`gold_stable_key`(128)) USING BTREE,
  KEY `idx_audit_samples_decision` (`build_run_id`, `reviewer_decision`) USING BTREE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='手动调优标注样本表';

-- 外键：build_run 删除时标注数据保留（RESTRICT），便于审计
SET @has_fk_audit_samples_build_run := (
  SELECT COUNT(1) FROM information_schema.REFERENTIAL_CONSTRAINTS
  WHERE CONSTRAINT_SCHEMA = DATABASE()
    AND CONSTRAINT_NAME = 'fk_audit_samples_build_run'
);
SET @sql := IF(@has_fk_audit_samples_build_run = 0,
  'ALTER TABLE `prompt_tune_audit_samples` ADD CONSTRAINT `fk_audit_samples_build_run` FOREIGN KEY (`build_run_id`) REFERENCES `knowledge_base_build_runs` (`id`) ON DELETE RESTRICT ON UPDATE RESTRICT',
  'SELECT 1');
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @has_fk_audit_samples_kb := (
  SELECT COUNT(1) FROM information_schema.REFERENTIAL_CONSTRAINTS
  WHERE CONSTRAINT_SCHEMA = DATABASE()
    AND CONSTRAINT_NAME = 'fk_audit_samples_kb'
);
SET @sql := IF(@has_fk_audit_samples_kb = 0,
  'ALTER TABLE `prompt_tune_audit_samples` ADD CONSTRAINT `fk_audit_samples_kb` FOREIGN KEY (`knowledge_base_id`) REFERENCES `knowledge_bases` (`id`) ON DELETE RESTRICT ON UPDATE RESTRICT',
  'SELECT 1');
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- ----------------------------
-- Table: prompt_drafts
-- 存储 05 步保存的历史草稿，供 01 步种子选择复用。
-- ----------------------------
CREATE TABLE IF NOT EXISTS `prompt_drafts` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `knowledge_base_id` bigint NOT NULL COMMENT '所属知识库ID',
  `name` varchar(128) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '草稿名',
  `description` text CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL COMMENT '说明',
  `seed` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '起始种子（system_default / graphrag_tuned / prompt_draft:N）',
  `candidate_id` varchar(128) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '来源候选标识符',
  `prompts_json` json NOT NULL COMMENT '多 key prompt 内容快照',
  `source_build_run_id` bigint NULL DEFAULT NULL COMMENT '来自哪次构建',
  `composite_score` decimal(5,4) NULL DEFAULT NULL COMMENT '评分时的综合分',
  `created_at` timestamp NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_at` timestamp NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`) USING BTREE,
  KEY `idx_prompt_drafts_kb` (`knowledge_base_id`) USING BTREE,
  KEY `idx_prompt_drafts_kb_created` (`knowledge_base_id`, `created_at` DESC) USING BTREE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='手动调优历史草稿表';

SET @has_fk_prompt_drafts_kb := (
  SELECT COUNT(1) FROM information_schema.REFERENTIAL_CONSTRAINTS
  WHERE CONSTRAINT_SCHEMA = DATABASE()
    AND CONSTRAINT_NAME = 'fk_prompt_drafts_kb'
);
SET @sql := IF(@has_fk_prompt_drafts_kb = 0,
  'ALTER TABLE `prompt_drafts` ADD CONSTRAINT `fk_prompt_drafts_kb` FOREIGN KEY (`knowledge_base_id`) REFERENCES `knowledge_bases` (`id`) ON DELETE RESTRICT ON UPDATE RESTRICT',
  'SELECT 1');
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @has_fk_prompt_drafts_build_run := (
  SELECT COUNT(1) FROM information_schema.REFERENTIAL_CONSTRAINTS
  WHERE CONSTRAINT_SCHEMA = DATABASE()
    AND CONSTRAINT_NAME = 'fk_prompt_drafts_build_run'
);
SET @sql := IF(@has_fk_prompt_drafts_build_run = 0,
  'ALTER TABLE `prompt_drafts` ADD CONSTRAINT `fk_prompt_drafts_build_run` FOREIGN KEY (`source_build_run_id`) REFERENCES `knowledge_base_build_runs` (`id`) ON DELETE SET NULL ON UPDATE RESTRICT',
  'SELECT 1');
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;
