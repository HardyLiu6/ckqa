-- 允许正式 QA 链路记录 hybrid_v0 查询模式。
-- 该迁移只扩展 qa_retrieval_logs.query_mode 的枚举值，不修改历史数据。

ALTER TABLE `qa_retrieval_logs`
  MODIFY COLUMN `query_mode` enum('local','global','drift','basic','hybrid_v0')
  CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '查询模式';
