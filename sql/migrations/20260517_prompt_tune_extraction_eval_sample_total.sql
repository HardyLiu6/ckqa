-- 04 步评分总样本数：替代后端硬编码的 20。
-- 背景：build_audit_extraction_set.py --sample_size 默认 20，但当原始 prompt_tuning_samples
-- 总条数 < 20 时，audit_with_gold.json 实际只会含 N (N<20) 条，前端却一直显示 0/20，
-- 用户看不出"已经没更多样本可跑"。
-- 修复：worker 启动时按 audit_with_gold.json 真实长度写入 sample_total；service 投影 status
-- 时用此值替换硬编码 20。

ALTER TABLE prompt_tune_extraction_eval_runs
  ADD COLUMN sample_total INT NULL DEFAULT NULL
  COMMENT '本次评分使用的 audit 样本数（=audit_with_gold.json 长度）。worker 启动时回填。'
  AFTER selected_candidate_ids;
