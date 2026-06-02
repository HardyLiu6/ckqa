SET NAMES utf8mb4;

-- ============================================================================
-- 修复表注释乱码并清理历史脏数据
-- 起草日期：2026-06-02
--
-- 修复范围：
--   1. 修复部分增量迁移在非 utf8mb4 连接下写坏的表注释。
--   2. 删除 course_material_id=15 的孤儿解析结果和解析日志。
--   3. 安全物理删除已逻辑删除课程 crs-20260506-oz3jz6 的数据库残留。
--
-- 说明：
--   - 当前库中 crs-20260506-oz3jz6 只剩课程本体、课程成员和成员事件残留。
--   - 仍保留通用的课程资料临时表清理逻辑；如未来该课程又被误挂资料，
--     只会删除该课程独占的 material_objects，不影响被其他课程复用的物理资料对象。
-- ============================================================================

ALTER TABLE `knowledge_base_build_runs` COMMENT = '知识库构建流水线表';
ALTER TABLE `prompt_drafts` COMMENT = '手动调优历史草稿表';
ALTER TABLE `prompt_tune_audit_samples` COMMENT = '手动调优标注样本表';
ALTER TABLE `prompt_tune_extraction_eval_runs` COMMENT = '手动调优 04 步评分运行表';
ALTER TABLE `prompt_tune_runs` COMMENT = 'GraphRAG 提示词自动调优运行表';
ALTER TABLE `qa_learning_memories` COMMENT = '问答跨对话学习记忆表';
ALTER TABLE `qa_memory_preferences` COMMENT = '问答跨对话学习记忆偏好表';

START TRANSACTION;

SET @ckqa_cleanup_course_id = _utf8mb4'crs-20260506-oz3jz6' COLLATE utf8mb4_unicode_ci;
SET @ckqa_orphan_course_material_id = 15;

DELETE pr
FROM `parse_results` pr
LEFT JOIN `course_materials` cm ON cm.id = pr.course_material_id
WHERE pr.course_material_id = @ckqa_orphan_course_material_id
  AND cm.id IS NULL;

DELETE pl
FROM `parse_logs` pl
LEFT JOIN `course_materials` cm ON cm.id = pl.course_material_id
WHERE pl.course_material_id = @ckqa_orphan_course_material_id
  AND cm.id IS NULL;

CREATE TEMPORARY TABLE `tmp_ckqa_cleanup_materials` (
  `id` bigint NOT NULL PRIMARY KEY,
  `material_object_id` bigint NOT NULL
) ENGINE = MEMORY;

INSERT INTO `tmp_ckqa_cleanup_materials` (`id`, `material_object_id`)
SELECT `id`, `material_object_id`
FROM `course_materials`
WHERE `course_id` = @ckqa_cleanup_course_id;

CREATE TEMPORARY TABLE `tmp_ckqa_cleanup_memberships` (
  `id` bigint NOT NULL PRIMARY KEY
) ENGINE = MEMORY;

INSERT INTO `tmp_ckqa_cleanup_memberships` (`id`)
SELECT `id`
FROM `course_memberships`
WHERE `course_id` = @ckqa_cleanup_course_id;

DELETE FROM `parse_logs`
WHERE `course_material_id` IN (
  SELECT `id` FROM `tmp_ckqa_cleanup_materials`
);

DELETE FROM `parse_results`
WHERE `course_id` = @ckqa_cleanup_course_id
   OR `course_material_id` IN (
     SELECT `id` FROM `tmp_ckqa_cleanup_materials`
   );

DELETE FROM `course_materials`
WHERE `id` IN (
  SELECT `id` FROM `tmp_ckqa_cleanup_materials`
);

DELETE mo
FROM `material_objects` mo
JOIN `tmp_ckqa_cleanup_materials` cm ON cm.material_object_id = mo.id
LEFT JOIN `course_materials` still_used ON still_used.material_object_id = mo.id
WHERE still_used.id IS NULL;

DELETE FROM `authorization_audit_logs`
WHERE `target_course_id` = @ckqa_cleanup_course_id
   OR `course_membership_id` IN (
     SELECT `id` FROM `tmp_ckqa_cleanup_memberships`
   );

DELETE FROM `course_membership_events`
WHERE `course_membership_id` IN (
  SELECT `id` FROM `tmp_ckqa_cleanup_memberships`
);

DELETE FROM `course_memberships`
WHERE `id` IN (
  SELECT `id` FROM `tmp_ckqa_cleanup_memberships`
);

DELETE FROM `course_route_profiles`
WHERE `course_id` = @ckqa_cleanup_course_id;

DELETE FROM `course_route_decisions`
WHERE `selected_course_id` = @ckqa_cleanup_course_id;

DELETE FROM `courses`
WHERE `course_id` = @ckqa_cleanup_course_id;

DROP TEMPORARY TABLE IF EXISTS `tmp_ckqa_cleanup_memberships`;
DROP TEMPORARY TABLE IF EXISTS `tmp_ckqa_cleanup_materials`;

COMMIT;
