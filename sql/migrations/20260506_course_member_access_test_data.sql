SET NAMES utf8mb4;

-- CKQA course member access test-data migration.
-- 生成课程成员管理联调所需的真实用户、课程和课程授权关系；当前不生成 password_hash。

ALTER TABLE `users`
  MODIFY COLUMN `password_hash` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci
    NULL DEFAULT NULL COMMENT '密码哈希（登录注册未接入时可为空）';

START TRANSACTION;

INSERT INTO `permissions` (`permission_code`, `permission_name`, `description`)
VALUES
  ('course:read', '查看课程', '允许查看授权范围内的课程'),
  ('membership:read', '查看课程成员', '允许查看课程成员列表'),
  ('membership:write', '维护课程成员', '允许添加、停用或移除课程成员'),
  ('user:read', '查看用户', '允许查询用户列表用于授权')
ON DUPLICATE KEY UPDATE
  `permission_name` = VALUES(`permission_name`),
  `description` = VALUES(`description`);

INSERT INTO `role_permissions` (`role_id`, `permission_id`)
SELECT r.id, p.id
FROM `roles` r
JOIN `permissions` p ON p.permission_code IN ('course:read')
WHERE r.role_code = 'student'
ON DUPLICATE KEY UPDATE `permission_id` = VALUES(`permission_id`);

INSERT INTO `role_permissions` (`role_id`, `permission_id`)
SELECT r.id, p.id
FROM `roles` r
JOIN `permissions` p ON p.permission_code IN ('course:read', 'membership:read', 'membership:write', 'user:read')
WHERE r.role_code = 'teacher'
ON DUPLICATE KEY UPDATE `permission_id` = VALUES(`permission_id`);

INSERT INTO `role_permissions` (`role_id`, `permission_id`)
SELECT r.id, p.id
FROM `roles` r
JOIN `permissions` p ON p.permission_code IN ('course:read', 'membership:read', 'membership:write', 'user:read')
WHERE r.role_code = 'admin'
ON DUPLICATE KEY UPDATE `permission_id` = VALUES(`permission_id`);

INSERT INTO `users` (`user_code`, `username`, `display_name`, `status`, `extra_metadata`)
VALUES
  ('STU2026001', 'student.zhouzh', '周子涵', 'active', '{"test_data": true, "role": "student", "student_no": "2026001", "grade": "2026级", "major": "计算机科学与技术"}'),
  ('STU2026002', 'student.zhaoyn', '赵一诺', 'active', '{"test_data": true, "role": "student", "student_no": "2026002", "grade": "2026级", "major": "软件工程"}'),
  ('STU2026003', 'student.sunhr', '孙浩然', 'active', '{"test_data": true, "role": "student", "student_no": "2026003", "grade": "2025级", "major": "数据科学与大数据技术"}'),
  ('TCH2026001', 'teacher.zhangwb', '张文博', 'active', '{"test_data": true, "role": "teacher", "employee_no": "T2026001", "department": "计算机学院", "title": "副教授"}'),
  ('TCH2026002', 'teacher.lisy', '李思雨', 'active', '{"test_data": true, "role": "teacher", "employee_no": "T2026002", "department": "软件学院", "title": "讲师"}'),
  ('ADM2026001', 'admin.heqh', '何启航', 'active', '{"test_data": true, "role": "admin", "employee_no": "A2026001", "department": "教务信息化中心", "duty": "系统配置管理"}')
ON DUPLICATE KEY UPDATE
  `username` = VALUES(`username`),
  `display_name` = VALUES(`display_name`),
  `status` = VALUES(`status`),
  `extra_metadata` = VALUES(`extra_metadata`);

INSERT INTO `courses` (`course_id`, `course_name`, `description`, `status`, `access_policy`)
VALUES
  ('crs-20260506-113000', '课程成员联调演示课', '用于验证课程成员列表、课程访问授权和教师数据范围。', 'active', 'restricted'),
  ('crs-20260506-113500', '公开访问演示课', '用于验证 public 课程不依赖课程成员关系即可访问。', 'active', 'public')
ON DUPLICATE KEY UPDATE
  `course_name` = VALUES(`course_name`),
  `description` = VALUES(`description`),
  `status` = VALUES(`status`),
  `access_policy` = VALUES(`access_policy`);

INSERT INTO `user_roles` (`user_id`, `role_id`)
SELECT u.id, r.id
FROM `users` u
JOIN `roles` r ON (
  (u.`user_code` BETWEEN 'STU2026001' AND 'STU2026003' AND r.`role_code` = 'student')
  OR (u.`user_code` BETWEEN 'TCH2026001' AND 'TCH2026002' AND r.`role_code` = 'teacher')
  OR (u.`user_code` = 'ADM2026001' AND r.`role_code` = 'admin')
)
ON DUPLICATE KEY UPDATE `role_id` = VALUES(`role_id`);

UPDATE `users`
SET `password_hash` = NULL
WHERE `user_code` IN (
  'STU2026001',
  'STU2026002',
  'STU2026003',
  'TCH2026001',
  'TCH2026002',
  'ADM2026001'
);

INSERT INTO `course_memberships` (
  `user_id`,
  `course_id`,
  `membership_role`,
  `status`,
  `access_source`,
  `source_ref_type`,
  `source_ref_id`,
  `joined_at`,
  `effective_from`,
  `granted_by_user_id`,
  `change_reason`,
  `extra_metadata`
)
SELECT
  member_user.id,
  seed.course_id,
  seed.membership_role,
  seed.status,
  seed.access_source,
  'seed',
  '20260506-course-member-access',
  NOW(),
  NOW(),
  grantor.id,
  seed.change_reason,
  JSON_OBJECT('test_data', true, 'seed_batch', '20260506-course-member-access')
FROM (
  SELECT 'TCH2026001' AS user_code, 'crs-20260506-113000' AS course_id, 'teacher' AS membership_role, 'active' AS status, 'manual' AS access_source, '演示课主讲教师' AS change_reason
  UNION ALL SELECT 'TCH2026002', 'crs-20260506-113000', 'assistant', 'active', 'manual', '演示课助教'
  UNION ALL SELECT 'STU2026001', 'crs-20260506-113000', 'student', 'active', 'imported', '班级名单导入'
  UNION ALL SELECT 'STU2026002', 'crs-20260506-113000', 'student', 'pending', 'imported', '待确认学生'
  UNION ALL SELECT 'STU2026003', 'crs-20260506-113000', 'student', 'suspended', 'manual', '停用状态样例'
  UNION ALL SELECT 'TCH2026002', 'crs-20260506-113500', 'teacher', 'active', 'manual', '公开课维护教师'
) seed
JOIN `users` member_user ON member_user.user_code = seed.user_code
JOIN `courses` c ON c.course_id = seed.course_id
LEFT JOIN `users` grantor ON grantor.user_code = 'ADM2026001'
ON DUPLICATE KEY UPDATE
  `membership_role` = VALUES(`membership_role`),
  `status` = VALUES(`status`),
  `access_source` = VALUES(`access_source`),
  `source_ref_type` = VALUES(`source_ref_type`),
  `source_ref_id` = VALUES(`source_ref_id`),
  `effective_from` = COALESCE(`course_memberships`.`effective_from`, VALUES(`effective_from`)),
  `effective_to` = IF(VALUES(`status`) = 'active', NULL, `course_memberships`.`effective_to`),
  `granted_by_user_id` = VALUES(`granted_by_user_id`),
  `change_reason` = VALUES(`change_reason`),
  `extra_metadata` = VALUES(`extra_metadata`);

COMMIT;
