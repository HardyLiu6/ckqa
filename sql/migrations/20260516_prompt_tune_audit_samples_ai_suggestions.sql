-- CKQA 手动调优标注样本：AI 候选持久化（Phase 3）
-- Date: 2026-05-16
-- 新增：prompt_tune_audit_samples.ai_suggested_entities / ai_suggested_relations
-- 背景：单次 LLM 抽取耗时 ~130 秒、token ~21k，候选不入库时刷新/切样本即丢失，
--      用户被迫重跑。改为持久化为 sample 二级状态。

-- ----------------------------
-- 添加 ai_suggested_entities 列
-- ----------------------------
SET @has_col_ai_entities := (
  SELECT COUNT(1) FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE()
    AND TABLE_NAME = 'prompt_tune_audit_samples'
    AND COLUMN_NAME = 'ai_suggested_entities'
);
SET @sql := IF(@has_col_ai_entities = 0,
  'ALTER TABLE `prompt_tune_audit_samples`
   ADD COLUMN `ai_suggested_entities` json NULL DEFAULT NULL
   COMMENT ''AI 候选实体列表（GraphRAG 抽取，未审阅状态；用户接受/拒绝后从此数组移除）''
   AFTER `gold_relations`',
  'SELECT 1');
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- ----------------------------
-- 添加 ai_suggested_relations 列
-- ----------------------------
SET @has_col_ai_relations := (
  SELECT COUNT(1) FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE()
    AND TABLE_NAME = 'prompt_tune_audit_samples'
    AND COLUMN_NAME = 'ai_suggested_relations'
);
SET @sql := IF(@has_col_ai_relations = 0,
  'ALTER TABLE `prompt_tune_audit_samples`
   ADD COLUMN `ai_suggested_relations` json NULL DEFAULT NULL
   COMMENT ''AI 候选关系列表（GraphRAG 抽取，未审阅状态；用户接受/拒绝后从此数组移除）''
   AFTER `ai_suggested_entities`',
  'SELECT 1');
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;
