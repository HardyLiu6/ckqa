SET NAMES utf8mb4;

-- CKQA 用户表补充联系信息字段：邮箱与手机号。
-- 仅作为个人中心展示和未来邮箱/手机验证登录的存储位；本期不强校验唯一性。
-- 邮箱 / 手机号唯一索引留待邮箱、手机号验证登录上线时再加（详见 遗留问题.md）。

ALTER TABLE users
    ADD COLUMN email VARCHAR(255) NULL COMMENT '联系邮箱（个人中心可编辑，唯一性留待邮箱登录上线时启用）' AFTER status,
    ADD COLUMN phone VARCHAR(32) NULL COMMENT '联系手机号（E.164 格式建议）' AFTER email,
    ADD COLUMN email_verified_at TIMESTAMP NULL DEFAULT NULL COMMENT '邮箱验证通过时间，未启用邮箱登录前保持 NULL' AFTER phone,
    ADD COLUMN phone_verified_at TIMESTAMP NULL DEFAULT NULL COMMENT '手机号验证通过时间，未启用手机登录前保持 NULL' AFTER email_verified_at;

-- 为后续邮箱 / 手机号登录改造预留普通索引，方便按字段查询，但不施加 UNIQUE。
-- 当邮箱/手机登录上线后，需要新增迁移脚本：清洗重复值后改为 UNIQUE。
CREATE INDEX idx_users_email ON users (email);
CREATE INDEX idx_users_phone ON users (phone);
