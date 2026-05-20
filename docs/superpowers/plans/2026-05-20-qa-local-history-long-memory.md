# CKQA Local History 长期记忆接入实施计划

日期：2026-05-20

状态：Implemented on `main`

## Summary

在主分支接入 GraphRAG LocalSearch conversation history 与跨对话学习记忆。长期记忆不是新的 QA mode，而是用户可显式开启/关闭的系统级能力；首版只在 `local` 或 `smart -> local` 场景中启用。

默认参数采用当前验证口径：

- `max_context_tokens=32000`
- `top_k_entities=6`
- `top_k_relationships=6`
- `max_turns=3`

本阶段不修改 `.claude/worktrees/student-qa-integration`，不把 Global/DRIFT/Hybrid 切到 GraphRAG conversation history，不让学生端直连 Python。

## Key Changes

### Python Local History

1. `/v1/query-tasks` 增加可选 `queryEngineStrategy=cli|local_history`。
2. `mode=local` 且策略为 `local_history` 时，调用 GraphRAG `LocalSearch.search(query, conversation_history=...)`。
3. `basic/global/drift/hybrid_v0` 首版仍走既有路径。
4. artifact、readiness、GraphRAG import 或执行异常时自动 fallback 到 CLI local，并在 snapshot 中记录 `historyFallbackReason`。
5. task snapshot 持久化 `queryEngineStrategy`、`historyApplied`、`historyTurnsUsed`、`conversationHistory` 和 fallback 诊断。

### Java 记忆偏好与上下文

1. 新增 `qa_memory_preferences`，按 `userId + courseId + knowledgeBaseId + indexRunId` 记录学生是否开启跨对话学习记忆。
2. 新增 `qa_learning_memories`，记录学习关注点、未解决问题和解释偏好，不把 assistant 回答当作课程事实。
3. `CreateQaMessageRequest.memoryPolicy` 支持 `default|off|auto`。
4. `QaMemoryContextService` 只在同一用户、课程、知识库和索引版本范围内组装记忆：
   - 最多 3 条长期记忆；
   - 长期记忆合计 1000 字以内；
   - 与最近 3 轮短期历史合并后裁剪到 3000 字。
5. `QaWorkflowService` 在 `mode=local` 且记忆允许时请求 Python `local_history`。

### 学生端

1. 问答页新增“跨对话学习记忆”开关。
2. 选定课程/知识库后加载并更新记忆偏好。
3. Local 发送时携带 `memoryPolicy`。
4. 非 Local 模式不展示为新模式，只展示是否使用学习记忆的状态。
5. 提供清理学习记忆入口。

### 管理端

1. QA 运维列表展示 `queryEngineStrategy`。
2. QA 运维详情展示：
   - 是否应用记忆；
   - 记忆策略；
   - 记忆来源数量；
   - 记忆范围；
   - Local history fallback 原因。
3. 管理端不默认展示学生私密记忆正文。

## Verification

实施时使用以下验证：

1. Python targeted tests:
   - `cd graphrag_pipeline && timeout 80 /home/sunlight/miniconda3/envs/graphrag-oneapi/bin/python -m pytest tests/test_query_engine_history_poc.py tests/test_query_task_api.py tests/test_query_task_manager.py`
2. Java targeted tests:
   - `cd backend/ckqa-back && ./mvnw test -Dtest=QaMemoryServiceTest,QaMemoryContextServiceTest,QaMemoryControllerWebMvcTest,QaWorkflowServiceTest,GraphRagTaskClientTest,QaTaskWorkerTest,QaSessionsControllerWebMvcTest`
3. Student app:
   - `cd frontend/apps/student-app && node --test tests/*.test.js`
   - `cd frontend/apps/student-app && pnpm build`
4. Admin app:
   - `cd frontend/apps/admin-app && pnpm test`
   - `cd frontend/apps/admin-app && pnpm build`

## Remaining Work

1. 扩大 Local history 对比评估到 20-30 条人工审定追问样本。
2. 结合学生反馈和管理端审核样本优化学习记忆生成、晋升、淘汰策略。
3. 明确教师/管理员是否可查看学生学习记忆正文的产品和隐私边界。
4. 评估 Redis 是否需要用于偏好/记忆读缓存；MySQL 继续作为事实源。
