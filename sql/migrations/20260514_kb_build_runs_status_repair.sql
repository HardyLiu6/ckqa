-- 一次性数据回填：修复历史遗留的"虚假运行中"buildRun 记录
--
-- 背景：在本次修复之前，KnowledgeBaseBuildRunService.updateStage() 会把所有阶段
-- （包括 material_selection / parse / graph_input_export / prompt 这些纯前端操作阶段）
-- 一律设成 status='running'。导致用户中途离开向导后留下大量"运行中"草稿，
-- 既无法在前端看出真实状态，也阻碍了归档/删除流程。
--
-- 修复后：updateStage 只更新阶段而不改 status，仅 index/qa_smoke 真正启动后台任务时
-- 才显式 setStatus('running')。
--
-- 本脚本用于回填历史数据：把 currentStage 不在 (index, qa_smoke, done) 且仍标记
-- status='running' 的记录改为 status='pending'，恢复"草稿/未真正运行"的语义。

UPDATE `knowledge_base_build_runs`
SET `status` = 'pending',
    `updated_at` = CURRENT_TIMESTAMP
WHERE `status` = 'running'
  AND `current_stage` IN ('material_selection', 'parse', 'graph_input_export', 'prompt');
