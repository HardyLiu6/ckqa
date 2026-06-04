ALTER TABLE `qa_sessions`
  ADD COLUMN `is_favorite` tinyint(1) NOT NULL DEFAULT 0 COMMENT '是否收藏会话' AFTER `status`;
