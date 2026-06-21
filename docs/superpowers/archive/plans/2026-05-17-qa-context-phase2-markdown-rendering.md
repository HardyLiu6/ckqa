# CKQA Phase 2 上下文摘要与 Markdown 学习化渲染计划

## Summary

在 `.claude/worktrees/student-qa-integration` 隔离 worktree 内实现 Phase 2：后端增加滚动摘要能力，使用 Java 调 One API 的 OpenAI-compatible `/chat/completions`，默认模型 `deepseek-v4-flash`；前端真实问答页增加受控 Markdown 渲染组件，采用 `markdown-it + DOMPurify`，让 GraphRAG 回答以学习材料形式展示，同时防 XSS。

Phase 2 仍不扩 Python `/v1/query-tasks` 协议，不让 student-app 直连 Python，不引入 Redis，不实现 `/fork`。

## Key Changes

1. 后端新增 `qa_session_summaries` 表，记录滚动摘要、水位线、来源消息数和失败原因。
2. 后端新增 One API 摘要 client 与摘要触发服务，assistant 成功入库后异步检查是否需要摘要。
3. `QaContextAssembler` 支持 `summary_recent`，但 Python GraphRAG 仍只收到短 `retrieval_query_text`。
4. 学生端真实问答页新增受控 Markdown 渲染，只用于 assistant 消息，user 消息继续纯文本展示。
5. Markdown 支持标题、粗体、列表、代码、引用、链接和来源标记，并用 DOMPurify 白名单清洗。

## Test Plan

1. `cd backend/ckqa-back && ./mvnw test`
2. `cd frontend/apps/student-app && node --test tests/*.test.js`
3. `cd frontend/apps/student-app && pnpm build`
4. `git diff --check -- sql backend/ckqa-back frontend/apps/student-app`
