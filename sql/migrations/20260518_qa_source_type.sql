-- 为问答来源卡片补充来源类型，便于学生端展示 bm25 / GraphRAG citation / fusion 标签。
-- 该迁移只新增可空列，不修改历史来源数据。

DELIMITER //
CREATE PROCEDURE `ckqa_add_qa_source_type_if_missing`()
BEGIN
  IF NOT EXISTS (
    SELECT 1 FROM information_schema.columns
    WHERE table_schema = DATABASE()
      AND table_name = 'qa_retrieval_hits'
      AND column_name = 'source_type'
  ) THEN
    ALTER TABLE `qa_retrieval_hits`
      ADD COLUMN `source_type` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL COMMENT '来源类型' AFTER `chunk_id`;
  END IF;
END//
DELIMITER ;

CALL `ckqa_add_qa_source_type_if_missing`();

DROP PROCEDURE IF EXISTS `ckqa_add_qa_source_type_if_missing`;
