SET NAMES utf8mb4;

-- CKQA JWT auth credential migration.
-- 为本地联调账号补充登录所需的 PBKDF2 密码哈希与本地认证身份。
-- 演示账号统一测试密码：Ckqa@2026

START TRANSACTION;

SET @ckqa_demo_password_hash = 'pbkdf2$210000$Y2txYS1qd3QtZGVtby0yNg==$cUUPGgqb4Q6w27xbIWMJ8Wwz9gxGFg2cq7Bb34BJEaU=';

INSERT INTO `permissions` (`permission_code`, `permission_name`, `description`)
VALUES
  ('material:read', '查看课程资料', '允许查看课程资料列表与资料状态'),
  ('material:write', '维护课程资料', '允许上传、编辑和删除课程资料记录'),
  ('material:parse', '触发资料解析', '允许触发课程资料解析任务'),
  ('material:export', '导出知识图谱输入', '允许导出 GraphRAG 构建输入'),
  ('kb:read', '查看知识库', '允许查看课程知识库与索引状态'),
  ('kb:write', '维护知识库', '允许创建和维护课程知识库'),
  ('kb:index', '构建知识库索引', '允许触发知识库索引构建'),
  ('kb:activate', '激活索引版本', '允许激活课程知识库索引版本'),
  ('qa:read', '查看问答', '允许访问课程问答与冒烟验证'),
  ('qa:log:read', '查看问答日志', '允许查看问答检索日志'),
  ('system:read', '查看系统状态', '允许查看系统健康与就绪状态')
ON DUPLICATE KEY UPDATE
  `permission_name` = VALUES(`permission_name`),
  `description` = VALUES(`description`);

INSERT INTO `role_permissions` (`role_id`, `permission_id`)
SELECT r.id, p.id
FROM `roles` r
JOIN `permissions` p ON p.permission_code IN (
  'course:read',
  'membership:read',
  'membership:write',
  'user:read',
  'material:read',
  'material:write',
  'material:parse',
  'material:export',
  'kb:read',
  'kb:write',
  'kb:index',
  'kb:activate',
  'qa:read',
  'qa:log:read',
  'system:read'
)
WHERE r.role_code IN ('teacher', 'admin')
ON DUPLICATE KEY UPDATE `permission_id` = VALUES(`permission_id`);

UPDATE `users`
SET `password_hash` = @ckqa_demo_password_hash,
    `status` = 'active'
WHERE `user_code` IN (
  'STU2026001',
  'STU2026002',
  'STU2026003',
  'TCH2026001',
  'TCH2026002',
  'ADM2026001'
);

INSERT INTO `auth_identities` (`user_id`, `provider`, `provider_user_id`, `identity_key`, `credential_meta`)
SELECT
  u.id,
  'local',
  u.username,
  u.user_code,
  JSON_OBJECT('credential', 'password', 'seed_batch', '20260506-jwt-auth')
FROM `users` u
WHERE u.`user_code` IN (
  'STU2026001',
  'STU2026002',
  'STU2026003',
  'TCH2026001',
  'TCH2026002',
  'ADM2026001'
)
ON DUPLICATE KEY UPDATE
  `identity_key` = VALUES(`identity_key`),
  `credential_meta` = VALUES(`credential_meta`);

COMMIT;
