SET NAMES utf8mb4;

-- ============================================================================
-- CKQA 课程与用户演示数据
-- 起草日期：2026-06-02
--
-- 用途：
--   为本地演示补齐一组可看的课程、用户、角色与课程成员关系。
--
-- 边界：
--   - 只写入基础展示/授权数据：courses、users、auth_identities、
--     user_roles、course_memberships。
--   - 不写入知识库、知识库构建、索引、问答会话、问答消息、检索日志等
--     业务流程表，避免制造不存在的运行链路。
--   - 所有新增记录都带 demo_seed/test_data 标记，方便后续识别。
--
-- 演示账号统一测试密码：Ckqa@2026
-- ============================================================================

START TRANSACTION;

SET @ckqa_demo_seed = '20260602-course-user-demo';
SET @ckqa_demo_password_hash = 'pbkdf2$210000$Y2txYS1qd3QtZGVtby0yNg==$cUUPGgqb4Q6w27xbIWMJ8Wwz9gxGFg2cq7Bb34BJEaU=';

INSERT INTO `courses` (
  `is_deleted`,
  `course_id`,
  `course_name`,
  `description`,
  `category`,
  `tags`,
  `objectives`,
  `audience`,
  `difficulty`,
  `estimated_hours`,
  `cover_url`,
  `status`,
  `access_policy`
)
VALUES
  (
    0,
    'crs-20260602-090001',
    'Python 程序设计 2026 夏',
    '面向初学者的程序设计入门课程，覆盖 Python 基础语法、函数、面向对象、文件处理、异常处理与小型项目实践。',
    '程序设计',
    JSON_ARRAY('Python', '编程基础', '函数', '面向对象', '文件处理', '项目实践'),
    JSON_ARRAY(
      '掌握 Python 基础语法与常用数据结构',
      '能够拆解问题并编写可运行的小程序',
      '理解函数、模块和面向对象的基本思想',
      '完成一个课程管理或数据处理类小项目'
    ),
    JSON_ARRAY('零基础编程学习者', '需要补齐程序设计基础的学生', '准备进入数据分析或后端开发方向的同学'),
    'beginner',
    48,
    '/api/v1/course-covers/default-course-cover.svg',
    'active',
    'restricted'
  ),
  (
    0,
    'crs-20260602-090002',
    '数据结构与算法实践 2026 夏',
    '围绕线性表、树、图、散列表、排序、搜索与复杂度分析展开，配合实验题训练算法实现与性能分析能力。',
    '计算机基础',
    JSON_ARRAY('数据结构', '算法', '复杂度分析', '树', '图', '排序', '搜索'),
    JSON_ARRAY(
      '理解常见数据结构的存储方式和适用场景',
      '能够分析基础算法的时间与空间复杂度',
      '能够用代码实现树、图、排序和搜索相关算法',
      '形成针对题目选择合适数据结构的判断能力'
    ),
    JSON_ARRAY('已具备一门编程语言基础的学生', '准备算法竞赛或技术面试的同学', '希望强化计算机基础的学习者'),
    'intermediate',
    64,
    '/api/v1/course-covers/default-course-cover.svg',
    'active',
    'restricted'
  ),
  (
    0,
    'crs-20260602-090003',
    '人工智能导论 2026 夏',
    '介绍人工智能的基本问题、搜索、知识表示、机器学习、神经网络与生成式 AI 应用，强调概念理解和案例分析。',
    '人工智能',
    JSON_ARRAY('人工智能', '机器学习', '神经网络', '搜索', '知识表示', '生成式 AI'),
    JSON_ARRAY(
      '理解人工智能问题建模和搜索方法',
      '了解机器学习与神经网络的基本流程',
      '能够阅读和讨论典型 AI 应用案例',
      '认识生成式 AI 工具的能力边界和使用风险'
    ),
    JSON_ARRAY('希望了解 AI 基础概念的学生', '跨专业选修人工智能课程的学习者', '准备进入机器学习方向的同学'),
    'beginner',
    40,
    '/api/v1/course-covers/default-course-cover.svg',
    'active',
    'public'
  )
ON DUPLICATE KEY UPDATE
  `is_deleted` = VALUES(`is_deleted`),
  `course_name` = VALUES(`course_name`),
  `description` = VALUES(`description`),
  `category` = VALUES(`category`),
  `tags` = VALUES(`tags`),
  `objectives` = VALUES(`objectives`),
  `audience` = VALUES(`audience`),
  `difficulty` = VALUES(`difficulty`),
  `estimated_hours` = VALUES(`estimated_hours`),
  `cover_url` = VALUES(`cover_url`),
  `status` = VALUES(`status`),
  `access_policy` = VALUES(`access_policy`);

INSERT INTO `users` (
  `user_code`,
  `username`,
  `display_name`,
  `password_hash`,
  `status`,
  `email`,
  `extra_metadata`,
  `is_deleted`
)
VALUES
  (
    'DEMO2026060201',
    'demo.student.liwei',
    '李维',
    @ckqa_demo_password_hash,
    'active',
    'demo.student.liwei@example.com',
    JSON_OBJECT('test_data', true, 'demo_seed', @ckqa_demo_seed, 'role', 'student', 'student_no', 'D2026060201', 'grade', '2026级', 'major', '软件工程'),
    0
  ),
  (
    'DEMO2026060202',
    'demo.student.chenaq',
    '陈安琪',
    @ckqa_demo_password_hash,
    'active',
    'demo.student.chenaq@example.com',
    JSON_OBJECT('test_data', true, 'demo_seed', @ckqa_demo_seed, 'role', 'student', 'student_no', 'D2026060202', 'grade', '2026级', 'major', '计算机科学与技术'),
    0
  ),
  (
    'DEMO2026060203',
    'demo.student.luosy',
    '罗思远',
    @ckqa_demo_password_hash,
    'active',
    'demo.student.luosy@example.com',
    JSON_OBJECT('test_data', true, 'demo_seed', @ckqa_demo_seed, 'role', 'student', 'student_no', 'D2026060203', 'grade', '2025级', 'major', '人工智能'),
    0
  ),
  (
    'DEMO2026060204',
    'demo.teacher.huangqy',
    '黄清远',
    @ckqa_demo_password_hash,
    'active',
    'demo.teacher.huangqy@example.com',
    JSON_OBJECT('test_data', true, 'demo_seed', @ckqa_demo_seed, 'role', 'teacher', 'employee_no', 'DT2026060201', 'department', '计算机学院', 'title', '讲师'),
    0
  ),
  (
    'DEMO2026060205',
    'demo.teacher.zhouyn',
    '周雅宁',
    @ckqa_demo_password_hash,
    'active',
    'demo.teacher.zhouyn@example.com',
    JSON_OBJECT('test_data', true, 'demo_seed', @ckqa_demo_seed, 'role', 'teacher', 'employee_no', 'DT2026060202', 'department', '人工智能学院', 'title', '副教授'),
    0
  ),
  (
    'DEMO2026060206',
    'demo.admin.linqm',
    '林启明',
    @ckqa_demo_password_hash,
    'active',
    'demo.admin.linqm@example.com',
    JSON_OBJECT('test_data', true, 'demo_seed', @ckqa_demo_seed, 'role', 'admin', 'employee_no', 'DA2026060201', 'department', '教务信息化中心', 'duty', '演示数据维护'),
    0
  )
ON DUPLICATE KEY UPDATE
  `username` = VALUES(`username`),
  `display_name` = VALUES(`display_name`),
  `password_hash` = VALUES(`password_hash`),
  `status` = VALUES(`status`),
  `email` = VALUES(`email`),
  `extra_metadata` = VALUES(`extra_metadata`),
  `is_deleted` = VALUES(`is_deleted`);

INSERT INTO `auth_identities` (`user_id`, `provider`, `provider_user_id`, `identity_key`, `credential_meta`)
SELECT
  u.id,
  'local',
  u.username,
  u.user_code,
  JSON_OBJECT('credential', 'password', 'demo_seed', @ckqa_demo_seed)
FROM `users` u
WHERE u.user_code IN (
  'DEMO2026060201',
  'DEMO2026060202',
  'DEMO2026060203',
  'DEMO2026060204',
  'DEMO2026060205',
  'DEMO2026060206'
)
ON DUPLICATE KEY UPDATE
  `provider_user_id` = VALUES(`provider_user_id`),
  `identity_key` = VALUES(`identity_key`),
  `credential_meta` = VALUES(`credential_meta`);

INSERT INTO `user_roles` (`user_id`, `role_id`)
SELECT u.id, r.id
FROM `users` u
JOIN `roles` r ON (
  (u.user_code IN ('DEMO2026060201', 'DEMO2026060202', 'DEMO2026060203') AND r.role_code = 'student')
  OR (u.user_code IN ('DEMO2026060204', 'DEMO2026060205') AND r.role_code = 'teacher')
  OR (u.user_code = 'DEMO2026060206' AND r.role_code = 'admin')
)
ON DUPLICATE KEY UPDATE
  `role_id` = VALUES(`role_id`);

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
  @ckqa_demo_seed,
  CASE WHEN seed.status = 'active' THEN NOW() ELSE NULL END,
  NOW(),
  grantor.id,
  seed.change_reason,
  JSON_OBJECT('test_data', true, 'demo_seed', @ckqa_demo_seed)
FROM (
  SELECT 'DEMO2026060204' AS user_code, 'crs-20260602-090001' AS course_id, 'teacher' AS membership_role, 'active' AS status, 'manual' AS access_source, 'Python 演示课主讲教师' AS change_reason
  UNION ALL SELECT 'DEMO2026060201', 'crs-20260602-090001', 'student', 'active', 'imported', '演示班级名单导入'
  UNION ALL SELECT 'DEMO2026060202', 'crs-20260602-090001', 'student', 'active', 'imported', '演示班级名单导入'
  UNION ALL SELECT 'DEMO2026060205', 'crs-20260602-090002', 'teacher', 'active', 'manual', '数据结构演示课主讲教师'
  UNION ALL SELECT 'DEMO2026060201', 'crs-20260602-090002', 'student', 'active', 'imported', '演示班级名单导入'
  UNION ALL SELECT 'DEMO2026060203', 'crs-20260602-090002', 'student', 'pending', 'imported', '待确认选课状态样例'
  UNION ALL SELECT 'DEMO2026060205', 'crs-20260602-090003', 'teacher', 'active', 'manual', '人工智能导论维护教师'
  UNION ALL SELECT 'DEMO2026060202', 'crs-20260602-090003', 'student', 'active', 'self_join', '公开课自助加入样例'
  UNION ALL SELECT 'DEMO2026060203', 'crs-20260602-090003', 'student', 'active', 'self_join', '公开课自助加入样例'
) seed
JOIN `users` member_user ON member_user.user_code = seed.user_code
JOIN `courses` c ON c.course_id = seed.course_id
LEFT JOIN `users` grantor ON grantor.user_code = 'DEMO2026060206'
ON DUPLICATE KEY UPDATE
  `membership_role` = VALUES(`membership_role`),
  `status` = VALUES(`status`),
  `access_source` = VALUES(`access_source`),
  `source_ref_type` = VALUES(`source_ref_type`),
  `source_ref_id` = VALUES(`source_ref_id`),
  `joined_at` = COALESCE(`course_memberships`.`joined_at`, VALUES(`joined_at`)),
  `effective_from` = COALESCE(`course_memberships`.`effective_from`, VALUES(`effective_from`)),
  `effective_to` = IF(VALUES(`status`) = 'active', NULL, `course_memberships`.`effective_to`),
  `granted_by_user_id` = VALUES(`granted_by_user_id`),
  `change_reason` = VALUES(`change_reason`),
  `extra_metadata` = VALUES(`extra_metadata`);

COMMIT;
