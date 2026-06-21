# CKQA Phase 3 LLM 追问改写、双输入协议与证据来源卡片实现计划

日期：2026-05-17

状态：Implemented Draft

## Summary

在 `.claude/worktrees/student-qa-integration` 隔离 worktree 内实现 Phase 3：Java 通过 One API 的 OpenAI-compatible `/chat/completions` 调用 `deepseek-v4-flash` 做追问独立问题改写；Java -> Python `/v1/query-tasks` 支持 `retrievalQuery + generationContext` 双输入；Python 仍只把 `retrievalQuery` 交给 GraphRAG CLI；Python 解析出的 `sources` 由 Java 写入 `qa_retrieval_hits`，学生端真实问答页展示来源卡片。

Phase 3 不切换 GraphRAG query engine，不实现长期记忆，不引入 Redis，不让 student-app 直连 Python，不实现 `/fork`。

## Key Changes

1. 后端 schema
   - 新增 `sql/migrations/20260517_qa_phase3_rewrite_evidence.sql`。
   - `qa_retrieval_logs` 增加 `standalone_query_text`、`rewrite_method`、`rewrite_model`、`rewrite_confidence`、`context_snapshot_version`。
   - `qa_retrieval_hits` 增加 `source_ref`、`source_file`、`heading_path`、`page_start`、`page_end`、`snippet`。
   - 同步更新 `sql/ocqa.sql`、实体和 Mapper XML。

2. LLM rewrite
   - 新增 `QaQuestionRewriteClient` / `QaQuestionRewriteClientPort`。
   - `QaQuestionRewriteService` 升级为 LLM rewrite + 规则 fallback。
   - 低置信度、非 JSON、空结果、过长结果、异常时回退规则改写或原问题。

3. Java -> Python 双输入协议
   - `GraphRagTaskClient` 请求体保留 `prompt`，并新增 `retrievalQuery` 与 `generationContext`。
   - Python `QueryTaskCreateRequest` 兼容旧 `prompt`，新请求优先使用 `retrievalQuery`。
   - GraphRAG CLI 仍只使用 `retrievalQuery`，`generationContext` 仅作为任务诊断字段保存和返回。

4. 证据来源落库与 API
   - 新增 `GraphRagSourceSnapshot`、`QaSourceResponse`。
   - `QaTaskWorker` 成功后把 snapshot sources 写入 `qa_retrieval_hits`，失败不影响问答成功。
   - `QaMessageResponse.sources` 支持 task 成功返回和历史消息恢复。

5. 学生端来源卡片
   - `qa-session-model.js` 规范化 `message.sources`。
   - `index.vue` 在 assistant Markdown 下方展示可折叠“参考来源”卡片。
   - 不展示 `retrievalQuery`、`generationContext`、`contextSnapshot` 或 prompt preview。

## Test Plan

已覆盖：

1. Java targeted tests
   - `QaQuestionRewriteClientTest`
   - `QaQuestionRewriteServiceTest`
   - `GraphRagTaskClientTest`
   - `QaTaskWorkerTest`
   - `QaWorkflowServiceTest`
2. Python tests
   - `tests/test_query_task_api.py`
   - `tests/test_query_task_manager.py`
3. 前端 Node tests
   - `tests/qa-session-model.test.js`
   - `tests/qa-real-page-markdown.test.js`

最终验证命令：

```bash
cd backend/ckqa-back && ./mvnw test
cd graphrag_pipeline && /home/sunlight/miniconda3/envs/graphrag-oneapi/bin/python -m pytest tests/
cd frontend/apps/student-app && node --test tests/*.test.js
cd frontend/apps/student-app && pnpm build
git diff --check -- sql backend/ckqa-back graphrag_pipeline frontend/apps/student-app docs/superpowers/plans
```

## Assumptions

1. One API 已暴露 `deepseek-v4-flash`，且支持非流式 chat completion。
2. LLM rewrite 只作为增强路径，失败时不能阻断学生问答。
3. Python 侧 sources 解析结果是 Phase 3 证据落库事实来源。
4. 学生端只展示正文 Markdown 与来源卡片，不展示内部检索和上下文诊断文本。
