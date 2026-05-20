-- ============================================================================
-- 课程信息架构 Phase 1：基础元数据扩展
-- 起草日期：2026-05-19
-- 关联文档：docs/2026-05-19-course-info-architecture-plan.md
--
-- 用途：
--   为 student-app 课程列表 / 详情页脱 mock 准备最小化字段集，并为 admin-app
--   课程表单字段扩展（PR 2）打基础。
--
-- 说明：
--   - tags / objectives / audience 用 JSON 字段存数组，后期升级到独立关联表
--     仅需数据迁移，不破坏 schema。
--   - difficulty 限定为 beginner/intermediate/advanced，支撑前端枚举展示。
--   - 数组上限（tags ≤ 20、objectives ≤ 12、audience ≤ 10）只在 DTO @Size 校验，
--     不在数据库层强制；后期放宽不需要 ALTER。
-- ============================================================================

ALTER TABLE `courses`
  ADD COLUMN `category` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL
    COMMENT '课程分类（自由输入），如：人工智能/前端开发' AFTER `description`,
  ADD COLUMN `tags` json NULL
    COMMENT '课程标签字符串数组，DTO 层限制最多 20 个' AFTER `category`,
  ADD COLUMN `objectives` json NULL
    COMMENT '学习目标字符串数组（"你将学到"），DTO 层限制最多 12 条' AFTER `tags`,
  ADD COLUMN `audience` json NULL
    COMMENT '适合人群字符串数组，DTO 层限制最多 10 条' AFTER `objectives`,
  ADD COLUMN `difficulty` enum('beginner','intermediate','advanced') CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL
    COMMENT '难度级别' AFTER `audience`,
  ADD COLUMN `estimated_hours` int NULL
    COMMENT '预计学习时长（小时），完整 LMS 上线前由教师手填' AFTER `difficulty`,
  ADD INDEX `idx_courses_category`(`category` ASC);
