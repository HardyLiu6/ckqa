# CKQA 智能推荐路由低置信度、smoke 固化与前端联动实施计划

## Summary

在主分支完成智能推荐路由短期闭环：保留现有 152 条全量路由回归集，新增 80 条边界题/负样本专项集；后端推荐接口增加置信度分档、手动切换提示和运维复核优先级；QA 任务持久化学生端智能推荐快照；smoke 报告默认写入版本化目录；学生端 Smart 模式以 Java 后端推荐为准，并在 Hybrid warmup 未 ready 时自动降级到 fallback。

本轮不新增在线 LLM 路由，不改变 QA 主链路，不把 Hybrid 设为默认推荐。

## Key Changes

- 后端 `/api/v1/qa-routing/recommend` 响应新增 `confidenceBand`、`manualSwitchSuggested`、`reviewPriority`，低置信区间固定为 `0.50 <= confidence < 0.65`。
- `qa_retrieval_logs` 新增可空诊断列，保存 `routing_confidence`、`routing_confidence_band`、`routing_review_priority`、`routing_snapshot_json`。
- 管理端 QA 运维列表/详情增加低置信度筛选、复核优先级展示和智能推荐快照。
- `run_qa_routing_smoke_matrix.py` 默认输出到 `docs/reports/qa-routing-smoke/<yyyyMMdd-HHmmss>-<label>/`，支持 `--compare-to` 生成调整前后比较。
- 学生端 Smart 模式发送前调用后端推荐；若后端推荐 `hybrid_v0` 但 warmup 未 ready，则智能推荐路径自动降级到 `fallbackMode || local`，手动 Hybrid 只提示不静默改写。

## Test Plan

- 后端：`QaModeRoutingServiceTest`、`QaModeRoutingEvaluationTest`、`QaRoutingControllerWebMvcTest`、`QaOperationsControllerWebMvcTest`、`QaWorkflowServiceTest`。
- 脚本：`python -m unittest scripts/tests/test_run_qa_routing_smoke_matrix.py`。
- 学生端：`node --test tests/*.test.js`、`pnpm test:e2e`。
- 管理端：`pnpm test`、`pnpm build`。
- 全局：`git diff --check -- backend/ckqa-back frontend/apps/student-app frontend/apps/admin-app docs sql`。

## Assumptions

- 现有 152 条路由集继续作为全量回归集；80 条专项集只用于边界和负样本回归。
- 前端本地规则只做即时预览和后端失败 fallback。
- Smoke 默认不提交 QA 生成任务，除非显式加 `--execute-qa --i-understand-external-model-calls`。
