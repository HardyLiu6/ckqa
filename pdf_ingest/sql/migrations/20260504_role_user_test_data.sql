SET NAMES utf8mb4;

-- CKQA role and user test-data migration.
-- 用于已有 ocqa 库补齐三类平台角色，并生成每类 5 个测试用户。
-- 当前未接入真实登录/注册流程，测试用户的 password_hash 统一保留为空字符串。

START TRANSACTION;

INSERT INTO `roles` (`role_code`, `role_name`, `description`)
VALUES
  ('student', '学生', '课程学习、课程问答与个人课程数据访问角色'),
  ('teacher', '教师', '课程资料、知识库、索引与问答运行管理角色'),
  ('admin', '系统管理员', '用户、角色、权限与系统配置管理角色')
ON DUPLICATE KEY UPDATE `role_name` = VALUES(`role_name`), `description` = VALUES(`description`);

INSERT INTO `permissions` (`permission_code`, `permission_name`, `description`)
VALUES
  ('course.query', '课程问答访问', '允许访问课程问答'),
  ('course.manage_members', '课程成员管理', '允许维护课程成员关系'),
  ('kb.manage_index', '知识库索引管理', '允许维护知识库和索引版本'),
  ('system.admin_override', '管理员越权访问', '允许管理员绕过课程成员限制')
ON DUPLICATE KEY UPDATE `permission_name` = VALUES(`permission_name`), `description` = VALUES(`description`);

INSERT INTO `role_permissions` (`role_id`, `permission_id`)
SELECT student_role.id, rp.permission_id
FROM `role_permissions` rp
JOIN `roles` old_role ON old_role.id = rp.role_id AND old_role.role_code = 'user'
JOIN `roles` student_role ON student_role.role_code = 'student'
ON DUPLICATE KEY UPDATE `permission_id` = VALUES(`permission_id`);

INSERT INTO `user_roles` (`user_id`, `role_id`)
SELECT ur.user_id, student_role.id
FROM `user_roles` ur
JOIN `roles` old_role ON old_role.id = ur.role_id AND old_role.role_code = 'user'
JOIN `roles` student_role ON student_role.role_code = 'student'
ON DUPLICATE KEY UPDATE `role_id` = VALUES(`role_id`);

DELETE rp
FROM `role_permissions` rp
JOIN `roles` old_role ON old_role.id = rp.role_id
WHERE old_role.role_code = 'user';

DELETE ur
FROM `user_roles` ur
JOIN `roles` old_role ON old_role.id = ur.role_id
WHERE old_role.role_code = 'user';

DELETE FROM `roles`
WHERE `role_code` = 'user';

INSERT INTO `role_permissions` (`role_id`, `permission_id`)
SELECT r.id, p.id
FROM `roles` r
JOIN `permissions` p ON p.permission_code = 'course.query'
WHERE r.role_code = 'student'
ON DUPLICATE KEY UPDATE `permission_id` = VALUES(`permission_id`);

INSERT INTO `role_permissions` (`role_id`, `permission_id`)
SELECT r.id, p.id
FROM `roles` r
JOIN `permissions` p ON p.permission_code IN (
  'course.query',
  'course.manage_members',
  'kb.manage_index'
)
WHERE r.role_code = 'teacher'
ON DUPLICATE KEY UPDATE `permission_id` = VALUES(`permission_id`);

INSERT INTO `role_permissions` (`role_id`, `permission_id`)
SELECT r.id, p.id
FROM `roles` r
JOIN `permissions` p ON p.permission_code IN (
  'course.query',
  'course.manage_members',
  'kb.manage_index',
  'system.admin_override'
)
WHERE r.role_code = 'admin'
ON DUPLICATE KEY UPDATE `permission_id` = VALUES(`permission_id`);

INSERT INTO `users` (`user_code`, `username`, `display_name`, `password_hash`, `status`, `extra_metadata`)
VALUES
  ('STU2026001', 'student.zhouzh', '周子涵', '', 'active', '{"test_data": true, "role": "student", "student_no": "2026001", "grade": "2026级", "major": "计算机科学与技术"}'),
  ('STU2026002', 'student.zhaoyn', '赵一诺', '', 'active', '{"test_data": true, "role": "student", "student_no": "2026002", "grade": "2026级", "major": "软件工程"}'),
  ('STU2026003', 'student.sunhr', '孙浩然', '', 'active', '{"test_data": true, "role": "student", "student_no": "2026003", "grade": "2025级", "major": "数据科学与大数据技术"}'),
  ('STU2026004', 'student.wujy', '吴佳怡', '', 'active', '{"test_data": true, "role": "student", "student_no": "2026004", "grade": "2025级", "major": "人工智能"}'),
  ('STU2026005', 'student.zhengmx', '郑明轩', '', 'active', '{"test_data": true, "role": "student", "student_no": "2026005", "grade": "2024级", "major": "计算机科学与技术"}'),
  ('TCH2026001', 'teacher.zhangwb', '张文博', '', 'active', '{"test_data": true, "role": "teacher", "employee_no": "T2026001", "department": "计算机学院", "title": "副教授"}'),
  ('TCH2026002', 'teacher.lisy', '李思雨', '', 'active', '{"test_data": true, "role": "teacher", "employee_no": "T2026002", "department": "软件学院", "title": "讲师"}'),
  ('TCH2026003', 'teacher.wangjn', '王嘉宁', '', 'active', '{"test_data": true, "role": "teacher", "employee_no": "T2026003", "department": "计算机学院", "title": "教授"}'),
  ('TCH2026004', 'teacher.chenxl', '陈晓琳', '', 'active', '{"test_data": true, "role": "teacher", "employee_no": "T2026004", "department": "人工智能学院", "title": "副教授"}'),
  ('TCH2026005', 'teacher.liuzy', '刘志远', '', 'active', '{"test_data": true, "role": "teacher", "employee_no": "T2026005", "department": "网络空间安全学院", "title": "讲师"}'),
  ('ADM2026001', 'admin.heqh', '何启航', '', 'active', '{"test_data": true, "role": "admin", "employee_no": "A2026001", "department": "教务信息化中心", "duty": "系统配置管理"}'),
  ('ADM2026002', 'admin.gaomy', '高明远', '', 'active', '{"test_data": true, "role": "admin", "employee_no": "A2026002", "department": "教务信息化中心", "duty": "用户与权限管理"}'),
  ('ADM2026003', 'admin.linshy', '林书瑶', '', 'active', '{"test_data": true, "role": "admin", "employee_no": "A2026003", "department": "数据治理中心", "duty": "数据质量巡检"}'),
  ('ADM2026004', 'admin.majj', '马俊杰', '', 'active', '{"test_data": true, "role": "admin", "employee_no": "A2026004", "department": "平台运维中心", "duty": "运行监控"}'),
  ('ADM2026005', 'admin.tangrc', '唐若晨', '', 'active', '{"test_data": true, "role": "admin", "employee_no": "A2026005", "department": "平台运维中心", "duty": "服务发布与应急"}')
ON DUPLICATE KEY UPDATE
  `username` = VALUES(`username`),
  `display_name` = VALUES(`display_name`),
  `password_hash` = VALUES(`password_hash`),
  `status` = VALUES(`status`),
  `extra_metadata` = VALUES(`extra_metadata`);

DELETE ur
FROM `user_roles` ur
JOIN `users` u ON u.id = ur.user_id
JOIN `roles` r ON r.id = ur.role_id
WHERE (
    u.`user_code` BETWEEN 'STU2026001' AND 'STU2026005'
    OR u.`user_code` BETWEEN 'TCH2026001' AND 'TCH2026005'
    OR u.`user_code` BETWEEN 'ADM2026001' AND 'ADM2026005'
  )
  AND NOT (
    (u.`user_code` BETWEEN 'STU2026001' AND 'STU2026005' AND r.`role_code` = 'student')
    OR (u.`user_code` BETWEEN 'TCH2026001' AND 'TCH2026005' AND r.`role_code` = 'teacher')
    OR (u.`user_code` BETWEEN 'ADM2026001' AND 'ADM2026005' AND r.`role_code` = 'admin')
  );

INSERT INTO `user_roles` (`user_id`, `role_id`)
SELECT u.id, r.id
FROM `users` u
JOIN `roles` r ON (
  (u.`user_code` BETWEEN 'STU2026001' AND 'STU2026005' AND r.`role_code` = 'student')
  OR (u.`user_code` BETWEEN 'TCH2026001' AND 'TCH2026005' AND r.`role_code` = 'teacher')
  OR (u.`user_code` BETWEEN 'ADM2026001' AND 'ADM2026005' AND r.`role_code` = 'admin')
)
ON DUPLICATE KEY UPDATE `role_id` = VALUES(`role_id`);

COMMIT;
