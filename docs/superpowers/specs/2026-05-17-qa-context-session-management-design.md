# CKQA 问答上下文与会话管理设计稿

日期：2026-05-17

状态：Implementation-review Draft

适用范围：学生端正式问答、Java `/api/v1/qa-sessions`、GraphRAG `/v1/query-tasks`。本稿只设计问答上下文与会话管理，不改变知识库构建、索引激活和 GraphRAG 基础查询模式。

## 0. 外部审阅接纳记录

本轮根据外部审阅做了批判性收敛。接纳原则是：必须符合当前 CKQA 代码边界，不能把 GraphRAG 官方 query engine 的能力直接当成当前 Java -> Python 协议已经具备的能力。

已接纳并改入设计：

1. **索引版本策略改为 session 级固化**：正式会话创建时冻结 `indexRunId`，后续消息默认继续使用该索引。知识库 active index 改变时，Phase 1 提示继续旧会话或基于新索引新建会话；Phase 2 之后再评估 fork。
2. **拆开原始问题、检索问题、生成上下文**：不再把大段“历史上下文模板”默认塞进 GraphRAG CLI 的查询字符串。首版只把简洁的 `retrieval_query_text` 传给 Python，完整上下文先作为 Java/DB 侧快照记录。
3. **Phase 1/Phase 2 策略口径统一**：Phase 1 只有 `none/recent`，Phase 2 才引入 `summary_recent`。
4. **学生端会话列表不暴露 `userId` 查询参数**：学生正式 API 从登录态解析当前用户。当前 `CreateQaSessionRequest.userId` 属于现有兼容字段，后续应收敛为后端认证上下文。
5. **学生端响应不回传 prompt preview**：正式响应只返回上下文策略和大小估算；完整上下文、检索问题和调试文本只进入管理端或开发诊断接口。
6. **补充摘要并发与乱序规则**：摘要水位线只能推进到连续完成的 user/assistant 区间，摘要更新必须串行或 CAS 保护。
7. **补充 Phase 1 能力边界**：Phase 1 是“会话恢复 + 基础追问检索消歧”，不是完整多轮生成。
8. **补充历史旧 session 兼容策略**：`index_run_id` 为空的历史会话优先回填；无法可靠回填则只读并提示新建会话。
9. **收紧 rewrite 规则与诊断字段**：只有明显指代、存在成功上文主题、当前问题不完整时才触发，并记录改写原因和来源范围。
10. **延后 fork API 语义**：Phase 1 不提供 `/fork`，先用“基于新索引新建会话”；Phase 2 有摘要表后再定义最小 fork。

保留为后续项：

1. Python `/v1/query-tasks` 支持 `messages/history` 或双输入协议。当前实现仍是单 `prompt` + GraphRAG CLI。
2. 引入 LangGraph/LlamaIndex 运行时。当前 Java 编排层已经成型，首版不做双编排。
3. Redis。当前会话、消息、上下文和任务状态以 MySQL 为事实源；Redis 仅作为未来限流、实时通知、短期 task cache 的生产增强项。

## 1. 背景与目标

当前学生端已经接入真实 Java 异步问答链路，但“会话管理”和“上下文管理”仍处在最小闭环阶段：

1. 会话、消息、任务状态能持久化到 MySQL。
2. 前端能在一个页面生命周期内复用当前 `activeSession`。
3. 每次 GraphRAG 查询仍只接收当前这一个问题，历史对话不会进入下一轮检索或生成。

目标是把 CKQA 从“多条单轮问答挂在同一 session 下”升级为“课程知识库内的多轮学习问答”：

1. 学生可以恢复历史会话、继续追问。
2. 后端能根据会话历史构造受控上下文，而不是把全部历史无脑塞进 prompt。
3. GraphRAG 查询仍通过 Java 编排层发起，不让 student-app 直连 Python。
4. 首版先做可靠、可观测、可测试的短期上下文；长期记忆、跨课程记忆、智能语义路由放后续阶段。

Phase 1 的能力边界需要明确：它支持会话恢复与基础追问检索消歧，使“它”“这个算法”等指代型追问具备更稳定的 `retrieval_query_text`；但 GraphRAG 最终仍主要基于改写后的单轮检索问题生成答案。完整的“基于历史上下文生成答案”留到 Python 双输入协议、messages/history 支持或后续 GraphRAG query engine 接入阶段。

## 2. 当前实现分析

### 2.1 前端

`frontend/apps/student-app/src/views/qa/index.vue` 当前做了真实 API 接入：

1. 页面加载课程列表，按课程/知识库选择问答范围。
2. 如果用户没有手动选课，使用前端规则按课程名、课程描述、课程 ID 匹配课程。
3. 首次发送时调用 `POST /qa-sessions` 创建会话；后续如果课程和知识库没变，复用页面内的 `activeSession`。
4. 发送消息后轮询 `GET /qa-sessions/{sessionId}/tasks/{taskId}`，成功后展示后端返回的 `assistantMessage`。

限制：

1. `activeSession` 只在当前 Vue 页面内存里，刷新页面、切路由、重新进入问答页后不会恢复。
2. 已有 `listQaMessages()` wrapper，但当前主问答页没有会话列表、会话选择、继续历史会话入口。
3. 切换课程或知识库会直接 `resetConversation()`，没有归档或保存 UI 选择状态。
4. 旧的 Pinia `stores/qa.js` 仍是 mock 问题列表，不参与真实会话。

### 2.2 Java 后端

当前正式链路：

1. `POST /api/v1/qa-sessions` 创建 `qa_sessions`。
2. `POST /api/v1/qa-sessions/{id}/messages`：
   - 校验 session active；
   - 校验 session 绑定知识库；
   - 读取知识库当前 `active_index_run_id`；
   - 追加 user message；
   - 创建 `qa_retrieval_logs` pending task；
   - 事务提交后异步 dispatch。
3. `QaTaskWorker` 读取 `index_artifacts.output_dir`，把 `mode + queryText + indexRunId + dataDirUri` 发给 Python `/v1/query-tasks`。
4. Python 成功后，Java 追加 assistant message，并把 retrieval log 标为 success。

限制：

1. `QaWorkflowService.sendMessage()` 没有读取历史消息来构造下一轮上下文。
2. `GraphRagTaskClient.createTask()` 只传 `mode`、`prompt`、`indexRunId`、`dataDirUri`。
3. `qa_messages.content_text` 和 `token_count` 已预留，但目前基本等同原始内容，未用于上下文预算。
4. `qa_retrieval_hits` 表存在，但当前任务链路没有把 GraphRAG 命中证据结构化写入。
5. 会话所有权、课程权限、会话列表/归档/改名能力还不完整。
6. `qa_sessions` 当前没有保存本会话使用的 `indexRunId`；正式问答发送时会重新读取知识库当前 active index。这会导致同一会话可能跨索引版本，需要在 Phase 1 修正。

### 2.3 Python GraphRAG 服务

当前 `/v1/query-tasks` 请求体只有：

```json
{
  "mode": "basic",
  "prompt": "当前问题",
  "indexRunId": 9,
  "dataDirUri": "relative/output/path"
}
```

`QueryTaskManager` 把 task snapshot 放在进程内存中，使用 GraphRAG CLI 执行 `graphrag query --method <mode> <prompt>`。

限制：

1. Python task 状态不是持久化状态，Python 重启后 Java 只能把任务判为丢失或失败。
2. OpenAI-compatible `/v1/chat/completions` 虽然有 `messages` 字段，但实现只取最后一条消息内容。
3. GraphRAG CLI 调用没有显式 chat history 参数；如果要利用历史，需要先在 Java/Python 侧把历史改写成“当前轮检索问题”或扩展当前 `/v1/query-tasks` 协议。

### 2.4 数据库

`sql/ocqa.sql` 已有基础表：

1. `qa_sessions`：用户、课程、知识库、类型、标题、状态、最后消息时间。
2. `qa_messages`：会话内消息、角色、序号、内容、可检索文本、token 数。
3. `qa_retrieval_logs`：每个用户消息对应的异步任务、模式、query、Python task、状态、心跳、错误、assistant message。
4. `qa_retrieval_hits`：预留命中文档记录。

这些表能支撑“会话恢复”和“上下文可观测”，但还缺少上下文快照、摘要、水位线和有效查询记录。
另外，`qa_sessions` 缺少 `index_run_id` 会话级固化字段，这是多轮上下文可回放前必须补齐的最小 schema 变更。

## 3. 成熟方案检索结论

### 3.1 OpenAI Responses：线程式状态与上下文窗口管理

OpenAI 官方文档把多轮对话拆成两类做法：传 `previous_response_id` 形成 threaded conversation，或手动维护输入上下文。官方也强调上下文窗口包含 input、output 和 reasoning tokens，长对话必须管理窗口大小，否则会超限或被截断。Responses API 还提供 compaction / context management 能力，用于长会话压缩。

对 CKQA 的启发：

1. 会话状态和每轮模型调用状态要分开建模。
2. 不应默认把全量历史放入每轮请求。
3. 需要明确上下文预算、水位线和压缩策略。
4. 如果未来用 Responses API 做摘要或问题改写，系统级约束和课程范围仍应由 CKQA 每轮显式传入，不能假设外部服务自动继承所有业务指令。

参考：

- https://developers.openai.com/api/docs/guides/conversation-state
- https://developers.openai.com/api/reference/resources/responses/methods/create

### 3.2 LangGraph：thread_id + checkpoint 持久化

LangGraph 官方方案把短期记忆定义为 thread-level persistence，用同一个 `thread_id` 复用会话状态；生产环境建议使用数据库-backed checkpointer。它还区分短期 memory 和跨 session 的长期 memory。

对 CKQA 的启发：

1. `qa_sessions.id/session_code` 应成为 CKQA 的 thread identity。
2. 每轮任务应记录当时使用的上下文快照，方便回放和排障。
3. 长期记忆不要在第一版混入，避免学生之间、课程之间的信息污染。

参考：

- https://docs.langchain.com/oss/python/langgraph/add-memory

### 3.3 LlamaIndex：短期 FIFO、长期 Memory Block、远程存储

LlamaIndex `Memory` 把短期消息缓冲和长期 memory blocks 分开，支持 token limit、flush size，并能接远程数据库。它也明确区分 workflow `Context` 和聊天 `Memory`：前者是运行时流程状态，后者是聊天历史。

对 CKQA 的启发：

1. `qa_retrieval_logs` 是任务运行上下文，不等同于聊天上下文。
2. 会话上下文应有独立的 summary / memory 表或字段。
3. 首版可以只做短期 FIFO + rolling summary，不急着做向量化长期记忆。

参考：

- https://developers.llamaindex.ai/python/framework/module_guides/deploying/agents/memory/
- https://developers.llamaindex.ai/python/framework-api-reference/chat_engines/context/

### 3.4 Semantic Kernel：Chat History Reducer

Semantic Kernel 官方把长历史处理抽象为 reducer，内置 truncation 和 summarization reducer，并强调保留 system messages。它的设计重点不是“存不存历史”，而是“每次请求前怎样把历史缩到可用窗口”。

对 CKQA 的启发：

1. 上下文构造应作为后端独立组件，而不是散在 controller 或前端。
2. 策略至少需要 `none`、`recent`、`summary` 三种。
3. 系统约束、课程范围、知识库索引版本必须永远保留，不能被压缩掉。

参考：

- https://learn.microsoft.com/en-us/semantic-kernel/concepts/ai-services/chat-completion/chat-history

### 3.5 Microsoft GraphRAG：检索上下文与查询模式

GraphRAG 官方 local/global search 的 query engine 层具备 conversation history 相关能力，其中 local search 会把知识图谱结构数据和原文文本块组合成 LLM 上下文，DRIFT search 则把 global insight 与 local refinement 结合，用更高成本换更丰富答案。

对 CKQA 的启发：

1. GraphRAG 官方能力存在，但 CKQA 当前 Java -> Python `/v1/query-tasks` 协议与 CLI 封装尚未暴露 `conversation history` 入参。
2. 多轮上下文首版应在 Java 编排层显式生成简洁的 `retrieval_query_text`，再交给 GraphRAG CLI。
3. `global/drift` 成本更高，首版上下文策略应优先服务 `basic/local`，`global/drift` 仍保守使用。

参考：

- https://microsoft.github.io/graphrag/query/local_search/
- https://microsoft.github.io/graphrag/query/drift_search/

## 4. 设计原则

1. **Java 是正式业务边界**：student-app 只调用 `/api/v1`，不直连 Python `/v1`。
2. **会话状态、任务状态、检索上下文分层**：不要把 `qa_sessions`、`qa_retrieval_logs`、GraphRAG prompt 混成一个概念。
3. **短期上下文优先**：第一版只做同一 session 内的最近消息，不做摘要和长期记忆。
4. **强范围绑定**：上下文永远绑定 `userId + courseId + knowledgeBaseId + sessionIndexRunId`。正式会话创建时固化 `indexRunId`，Phase 1 中切知识库或切索引版本就新建会话；Phase 2 后再评估显式 fork。
5. **检索与生成分离**：原始问题、检索问题、生成上下文分开记录。当前 Python 协议只接收单 `prompt` 时，优先传短而明确的检索问题。
6. **可观测优先**：每轮任务要能回看“原始问题、检索问题、上下文策略、上下文消息范围、摘要水位线”。
7. **成本可控**：默认 `basic/local` 才启用短期上下文增强；`global/drift` 首版不带完整历史，Phase 2 后最多只带摘要生成的短上下文。

## 5. 方案选型

### 方案 A：Java 编排层生成检索问题与上下文快照，保持 Python 查询协议基本不变（推荐）

Java 在发送 GraphRAG 任务前读取 `qa_messages`，生成简洁的 `retrievalQuery`，并把最近消息或摘要保存为 `contextSnapshot`。当前仍调用现有 `/v1/query-tasks`，Python 收到的 `prompt` 对应 `retrievalQuery`，不是完整聊天历史模板。

优点：

1. 不改 GraphRAG CLI 查询方式，风险最低。
2. 复用现有 `qa_sessions` / `qa_messages` / `qa_retrieval_logs`。
3. 容易在 Java 单元测试里固定上下文预算和边界行为。
4. 避免把大量历史模板混进 GraphRAG 检索 query，降低召回污染。

缺点：

1. 指代消解和问题改写质量受规则或轻量 LLM 调用影响。
2. GraphRAG 返回仍不带结构化 citations，命中证据需要后续补。
3. 当前单 `prompt` 协议不能把“检索输入”和“生成上下文”同时传给 Python，完整双输入需要后续协议扩展。

### 方案 B：扩展 Python `/v1/query-tasks` 支持双输入或 messages/history

Java 把 `retrievalQuery + generationContext` 或消息数组传给 Python，Python 根据模式调用不同 GraphRAG query engine 或内部 API。

优点：

1. 更贴近 GraphRAG/LlamaIndex 的 chat engine 形态。
2. 后续更适合做复杂路由、查询改写、证据融合。

缺点：

1. 当前项目刻意使用 GraphRAG CLI 规避内部 API 漂移，直接扩 Python 协议会提高维护成本。
2. Java 与 Python 的上下文策略边界会变模糊。

### 方案 C：引入 LangGraph/LlamaIndex 作为会话编排层

把问答做成 agent graph，由 checkpointer/memory 管理会话。

优点：

1. 成熟框架已有 memory、checkpoint、history reducer。
2. 长期看适合复杂 agent 工作流。

缺点：

1. 当前 CKQA 已有 Java 编排层，直接引入会形成双编排。
2. 对首版学生端多轮问答来说过重。

结论：首版采用方案 A。保留 B/C 的扩展点，但不在第一轮实现。

## 6. 推荐架构

### 6.1 分层模型

```text
student-app
  -> Java /api/v1/qa-sessions
     -> QaSessionService        会话生命周期
     -> QaContextAssembler      上下文预算、最近消息选择、快照记录
     -> QaQuestionRewriteService 当前问题改写为检索问题
     -> QaWorkflowService       消息与任务编排
     -> QaTaskWorker            调用 Python task API
        -> GraphRAG /v1/query-tasks
```

### 6.2 会话生命周期

新增或补齐 Java API：

1. `GET /api/v1/qa-sessions?courseId=&knowledgeBaseId=&status=&page=&size=`
   - 学生端历史会话列表。
   - 后端从登录态解析当前用户，默认只返回当前用户 `formal` 会话。
   - 不向学生端暴露 `userId` 查询参数；管理端跨用户检索应单独设计 admin endpoint。
2. `PATCH /api/v1/qa-sessions/{id}`
   - 支持改标题、归档、恢复。
3. `POST /api/v1/qa-sessions`
   - 正式学生端目标形态从登录态读取 `userId`。
   - 现有 `CreateQaSessionRequest.userId` 可作为过渡兼容字段，但服务端必须校验它与当前登录用户一致。
   - 创建正式会话时读取知识库当前 `activeIndexRunId` 并固化到 session；后续消息默认使用该 `session.indexRunId`。

前端行为：

1. 左侧或顶部提供“新建会话 / 历史会话”。
2. 选择历史会话后调用 `GET /qa-sessions/{id}/messages` 恢复消息流。
3. URL 带 `sessionId`，刷新后可恢复。
4. 切换课程/知识库时，不清空历史；而是新建会话或提示切换范围会开启新会话。
5. 若知识库 active index 已变更，前端在旧会话中提示两个动作：继续旧索引会话，或基于新索引开启新会话。
6. Phase 1 暂不提供 `/fork` API。Phase 2 有 `qa_session_summaries` 后再评估最小 fork，且默认只复制会话元数据；是否复制摘要另行设计，不复制原始消息和上下文快照。

### 6.3 上下文策略

定义 `QaContextPolicy`：

| 策略 | 用途 | 行为 |
| --- | --- | --- |
| `none` | 首轮、独立问题、全局综述 | 只使用当前问题 |
| `recent` | 常规追问 | 带最近 N 条消息，预算内截断 |
| `summary` | 长会话 | 会话摘要 + 最近 N 条消息 |
| `summary_recent` | 默认自动策略 | 摘要 + 最近消息 + 当前问题 |

Phase 1 默认策略：

1. 首轮：`none`
2. 二轮及以后：`recent`
3. `basic/local`：启用上下文
4. `global/drift`：默认 `none`，避免长耗时模式被历史噪声拖慢

Phase 2 默认策略：

1. 短会话：`recent`
2. 长会话：`summary_recent`
3. `global/drift`：最多只带短摘要，不带完整最近消息

### 6.4 检索问题与生成上下文构造

GraphRAG CLI 当前吃的是字符串，不是 messages array。因此首版不要假装它是聊天模型，也不要把完整历史上下文模板直接塞进查询字符串。后端必须在设计层拆开三类文本：

1. `original_query_text`：学生原始问题，原样保存。
2. `retrieval_query_text`：传给 GraphRAG CLI 的短检索问题，尽量独立、明确、少噪声。
3. `context_snapshot_text` / `generation_context_text`：用于诊断、后续双输入协议或摘要生成的上下文快照，Phase 1 默认不直接传给 Python。

Phase 1 的实际发送规则：

1. 如果当前问题已经独立，`retrieval_query_text = original_query_text`。
2. 如果当前问题包含明显指代，例如“它”“这个算法”“上面那个概念”，Java 可用最近消息做轻量指代补全，生成短查询，例如“关于上一轮主题‘死锁’：它和资源分配图有什么关系？”。
3. 不把课程、知识库、长摘要、完整最近对话拼成大 prompt 发送给 GraphRAG CLI。
4. 完整上下文只保存到 DB 诊断字段或后续快照表。

Phase 1 rewrite 只能在同时满足以下条件时触发：

1. 当前问题命中明显指代词，例如“它”“这个”“上面那个”“前者/后者”。
2. 同一 session 内存在最近一个成功完成的上文主题，来源必须是完整的 user/assistant 对。
3. 当前问题本身不是完整问题；如果已经包含明确课程概念、实体或章节名，不做规则改写。

rewrite 失败或置信度不足时，必须回退为 `retrieval_query_text = original_query_text`。所有 rewrite 都要记录 `rewrite_applied`、`rewrite_reason`、`rewrite_source_message_range`，用于区分“检索差”“改写差”和“原问题含糊”。

Phase 2/3 的生成上下文模板可以用于摘要、问题改写或未来双输入协议：

```text
你正在回答同一个课程知识库内的学生追问。

课程：{courseName}
知识库：{knowledgeBaseName}
会话摘要：
{summary or "无"}

最近对话：
学生：...
助手：...

当前问题：
{userQuestion}

请把当前问题理解为同一会话中的追问，回答时优先围绕当前问题；如果历史上下文无关，请忽略历史。
```

同时记录：

1. `originalQuery`：用户原问题。
2. `retrievalQuery`：实际传给 GraphRAG 的检索问题。
3. `contextSnapshot`：本轮用于改写和诊断的上下文快照。
4. `contextStrategy`：本轮策略。
5. `contextMessageRange`：使用了哪些消息序号。
6. `contextCharCount`：上下文字符数。
7. `rewriteApplied` / `rewriteReason` / `rewriteSourceMessageRange`：本轮是否做了追问改写、为何改写、依据哪些历史消息。

后续可升级为两步：

1. `QaQuestionRewriteService` 先把追问改写为独立问题。
2. Python `/v1/query-tasks` 支持 `retrievalQuery + generationContext` 双输入，或支持 messages/history。

这样可以减少“把聊天记录当检索关键词”带来的召回污染。

### 6.5 上下文预算

首版使用字符数估算，字段命名避免伪装成精确 token。后续接 tokenizer 后再新增 token estimate。

默认预算建议：

| 内容 | 预算 |
| --- | --- |
| Phase 2 会话摘要 | 800 中文字符以内 |
| 最近消息 | 最近 6 条，合计 1800 中文字符以内 |
| 当前问题 | 原样保留，不截断；超过 2000 字拒绝或提示精简 |
| retrieval_query_text | 默认不超过 800 中文字符 |
| context_snapshot_text | 默认不超过 3500 中文字符 |

压缩规则：

1. 永远保留当前问题。
2. 永远保留课程、知识库、session 固化的索引版本。
3. 最近消息从新到旧加入，超预算则停止。
4. 如果有摘要，摘要优先于更老的消息。
5. system/developer 级约束不可被摘要覆盖。

字段命名：

1. 数据库 Phase 1 使用 `context_char_count`。
2. API 使用 `contextSizeEstimate`，例如 `{ "chars": 1520 }`。
3. 如果后续接入 tokenizer，再增加 `context_token_estimate` 或 `approxTokenEstimate`，不复用字符估算字段。

### 6.6 摘要更新

Phase 1 不生成摘要，只使用 `recent`。Phase 2 新增 `QaSessionContextService`：

1. 每次 assistant 成功后检查消息长度和水位线。
2. 如果未摘要消息超过阈值，例如 12 条或 3000 字，生成 rolling summary。
3. 摘要只覆盖已完成的 user/assistant 对，不能覆盖 pending/failed 任务。
4. 摘要生成失败不影响主问答，只记录 warning。
5. `summary_until_sequence_no` 只能推进到连续完成区间。例如第 4 条消息的任务还在 pending，即使第 6 条消息先成功，也不能把摘要水位线推进到 6。
6. 摘要更新必须具备幂等性和并发保护。推荐按 session 串行化摘要 job，或使用 `WHERE summary_until_sequence_no = :oldWatermark` 的 CAS 更新。

摘要内容格式：

```text
本会话到第 {sequenceNo} 条消息为止：
- 学生关注的问题：
- 已解释的关键概念：
- 已给出的结论：
- 未解决或可继续追问：
```

推荐 Phase 2 使用独立 `qa_session_summaries` 表，而不是直接把长摘要反复写入 `qa_sessions`。如果实现压力较大，可以把 `qa_sessions.context_summary` 作为短期折中，但正式设计以独立摘要表为目标。

### 6.7 数据模型调整

Phase 1 最小改法：

1. 扩展 `qa_sessions`
   - `index_run_id` bigint null：正式会话创建时固化的索引运行 ID。
   - `index_locked_at` timestamp null：索引版本固化时间。
   - 外键指向 `index_runs(id)`，允许历史数据为空。
2. 扩展 `qa_retrieval_logs`
   - `original_query_text` longtext null
   - `retrieval_query_text` longtext null
   - `context_snapshot_text` longtext null
   - `context_strategy` varchar(32) null
   - `context_message_range` varchar(128) null
   - `context_char_count` int null
   - `rewrite_applied` tinyint(1) not null default 0
   - `rewrite_reason` varchar(255) null
   - `rewrite_source_message_range` varchar(128) null

Phase 2 推荐新增：

1. `qa_session_summaries`
   - `id`
   - `session_id`
   - `summary_text`
   - `summary_until_sequence_no`
   - `source_message_count`
   - `status`
   - `created_at`
   - `updated_at`
   - 支持多版本摘要、失败重试、审计和回放。

Phase 3 可选新增：

1. `qa_session_context_snapshots`
   - 每轮任务一条上下文快照。
   - 如果 `qa_retrieval_logs.context_snapshot_text` 成为高频大字段、让 retrieval log 表明显膨胀，或需要多版本上下文回放，就从 `qa_retrieval_logs` 迁移到此表。
2. `qa_retrieval_hits`
   - 继续复用已有表，补齐 GraphRAG 命中证据写入。

历史旧 session 兼容策略：

1. `index_run_id is null` 的历史 `formal` session 默认允许读取历史消息。
2. 用户继续发送前，后端优先尝试从该 session 最近一条 `task_status=success` 且 `index_run_id is not null` 的 `qa_retrieval_logs` 回填 `qa_sessions.index_run_id`。
3. 如果历史 session 存在多个不同成功 `index_run_id`，或没有任何成功 retrieval log 可用，则判定无法可靠回填。
4. 无法可靠回填的旧 session 只读，禁止直接追加新消息；前端提示“该会话创建于索引版本固化前，请基于当前索引新建会话”。
5. 回填过程必须幂等，且不改写历史 retrieval log 的 `index_run_id`。

当前代码影响：

1. `QaSessions` 实体、`QaSessionResponse`、`QaSessionsMapper.xml` 需要补 `indexRunId`。
2. `QaWorkflowService.createSession()` 需要在知识库 ready 时把当前 `activeIndexRunId` 写入 session。
3. `QaWorkflowService.sendMessage()` 正式会话默认使用 `session.indexRunId`，不再每轮重新读取知识库当前 active index。`session_type=smoke` 或构建冒烟链路可继续显式传入 `indexRunIdOverride`，但要在 retrieval log 中记录清楚。

### 6.8 API 响应调整

`POST /qa-sessions/{id}/messages` 返回增加：

```json
{
  "contextApplied": true,
  "contextStrategy": "recent",
  "contextSizeEstimate": {
    "chars": 1520
  }
}
```

`GET /qa-sessions/{id}/tasks/{taskId}` 返回增加同样的 context 字段，方便前端诊断。

学生端正式响应不返回 `retrieval_query_text`、`context_snapshot_text` 或 prompt preview，避免暴露内部提示词结构，也避免前端依赖不稳定诊断文本。完整调试信息只放在：

1. 管理端问答运维详情。
2. 开发环境专用诊断接口。
3. 后端日志或数据库排障字段。

## 7. 权限与隔离

1. 创建、读取、追加消息必须校验当前登录用户是 session owner。
2. session 绑定课程时，必须校验用户仍是课程 active member。
3. session 绑定知识库时，必须确认知识库属于该课程，且有 `activeIndexRunId`。
4. session 创建时固化的 `indexRunId` 必须属于该知识库，且创建时处于可用状态。
5. 任何上下文只来自同一个 `session_id`。
6. 第一版不做跨 session、跨课程、跨用户长期记忆。

## 8. 失败处理

1. 上下文组装失败：降级为 `none`，但记录 `contextStrategy=none_fallback`。
2. 检索问题改写失败：回退到 `original_query_text`，同时记录 warning。
3. 摘要生成失败：不阻断问答，保留旧摘要。
4. GraphRAG task stale：保持当前行为，用户消息显示 stale，assistant 不伪造。
5. 会话被归档：禁止追加消息，允许读历史。
6. 知识库 active index 改变：旧 session 继续使用创建时固化的 `session.indexRunId`；Phase 1 如果用户要使用新索引，必须新建会话。
7. 历史旧 session 无法回填 `indexRunId`：允许读取，禁止继续追加消息，提示用户基于当前索引新建会话。

## 9. 分阶段落地

### Phase 1：会话恢复与短期上下文

Phase 1 定位为“会话恢复 + 追问检索消歧”，不是完整多轮生成。它只保证指代型追问生成更稳定的短检索问题；完整历史上下文参与生成留待 Phase 3 的双输入协议或 messages/history 支持。

1. 后端增加会话列表 API。
2. 前端增加历史会话入口和 URL `sessionId` 恢复。
3. `qa_sessions` 固化 `index_run_id`，正式会话后续消息默认继续使用该索引。
4. Java 增加 `QaContextAssembler`，使用最近 6 条消息构造上下文快照。
5. Java 增加轻量 `QaQuestionRewriteService`，把明显指代型追问压成简洁 `retrieval_query_text`。
6. `qa_retrieval_logs` 记录 `original_query_text`、`retrieval_query_text`、`context_snapshot_text`、`context_strategy`、`context_char_count`。

验收：

1. 同一会话中问“它是什么意思”时，`retrieval_query_text` 能包含上一轮主题，且传给 GraphRAG 的仍是短检索问题。
2. 刷新页面后能恢复会话和继续提问。
3. 不同课程/知识库不会串上下文。
4. 知识库 active index 改变后，旧会话继续使用旧 `session.indexRunId`，新索引必须开启新会话。
5. `index_run_id is null` 的旧 session 能被可靠回填时可继续使用；无法可靠回填时只读并提示新建会话。

### Phase 2：滚动摘要

1. 新增 `qa_session_summaries` 或短期折中摘要字段。
2. Java 增加摘要更新任务。
3. 超过阈值后，旧消息压缩进摘要，最近消息仍保留。
4. 摘要水位线只推进到连续完成区间。
5. 摘要更新使用 session 串行 job 或 CAS 防并发覆盖。

验收：

1. 20 轮会话仍能在预算内构造检索问题和上下文快照。
2. 摘要失败不影响问答主链路。
3. 乱序任务完成时，摘要不会越过 pending/failed 的消息对。

### Phase 3：问题改写与证据快照

1. 升级 `QaQuestionRewriteService`，可用 LLM 把追问改写成独立检索问题。
2. Python `/v1/query-tasks` 评估引入 `retrievalQuery + generationContext` 双输入或 messages/history。
3. 记录 `standalone_query` 和上下文快照版本。
4. 补齐 `qa_retrieval_hits` 写入，让 assistant 回答能展示证据来源。

验收：

1. 指代型追问召回更稳定。
2. 后台可以看到原问题、改写问题、命中证据和最终回答。

## 10. 测试计划

### 单元测试

1. `QaContextAssemblerTest`
   - 首轮返回 `none`。
   - 二轮保留最近消息。
   - 超预算时保留当前问题，裁掉更老消息。
   - 不同 session 的消息不会混入。
   - 输出 `context_char_count`，不输出伪精确 token 字段。
2. `QaSessionsServiceTest`
   - 会话列表按当前用户、course、status 过滤。
   - archived session 禁止追加消息。
   - 正式会话创建时固化 `indexRunId`。
   - 知识库 active index 变更后，旧会话仍返回旧 `indexRunId`。
   - `indexRunId` 为空的历史会话能从唯一成功 retrieval log 回填。
   - 无法可靠回填的历史会话禁止追加消息。
3. `QaQuestionRewriteServiceTest`
   - 只有明显指代词 + 成功上文主题 + 当前问题不完整时触发 rewrite。
   - 完整问题不触发 rewrite。
   - rewrite 记录 `rewrite_applied`、`rewrite_reason`、`rewrite_source_message_range`。
4. `QaWorkflowServiceTest`
   - `createPendingTask` 写入 original/retrieval/context 字段。
   - GraphRAG client 收到简洁 `retrieval_query_text`，不是完整上下文模板。
5. `QaSessionSummaryServiceTest`
   - 水位线只推进到连续完成消息。
   - 并发摘要更新只成功一次，重复执行幂等。

### 前端测试

1. 会话列表恢复：点击历史会话后调用 `GET /qa-sessions/{id}/messages`。
2. URL 恢复：`/qa?sessionId=20` 能恢复消息。
3. 切换知识库：提示新建会话，不把旧消息清空成不可恢复状态。
4. 知识库 active index 变更：旧会话提示“继续旧索引 / 新建新索引会话”。
5. 学生端接口不传 `userId` 查询历史会话。
6. 旧会话无法回填 `indexRunId` 时，消息可读但输入区提示新建会话。

### 集成验证

1. 启动 Java + GraphRAG。
2. 使用操作系统课程问：
   - “什么是死锁？”
   - “它和资源分配图有什么关系？”
3. 检查第二问的 `qa_retrieval_logs.retrieval_query_text` 包含上一轮主题，但不是完整历史大模板。
4. 检查刷新页面后仍能继续第三问。
5. 激活同一知识库的新索引后，旧会话继续使用旧 `session.index_run_id`。

## 11. 不做事项

1. 不把 `smart` 作为后端查询模式。
2. 不让 student-app 直接调用 GraphRAG Python。
3. 不在第一版引入 LangGraph/LlamaIndex 运行时。
4. 不做跨课程长期个性化记忆。
5. 不在 `global/drift` 默认携带完整历史。
6. 不在学生端正式 API 返回完整 prompt、上下文快照或检索问题预览。
7. 不在 Phase 1 引入 Redis；Redis 只作为后续生产增强项。
8. 不在 Phase 1 提供 `/qa-sessions/{id}/fork`；新索引场景先用新建会话。

## 12. 需要确认的问题

1. 历史旧 session 的 `index_run_id` 迁移/兼容策略是否采用“唯一成功 retrieval log 回填，否则只读新建会话”？
2. Phase 1 的轻量指代改写先用规则，还是允许调用 LLM 做独立问题改写？
3. Phase 2 摘要表直接采用 `qa_session_summaries`，还是先把 `context_summary` 临时放在 `qa_sessions` 上？
4. 前端历史会话入口放在问答页左侧会话栏，还是顶部下拉菜单？

推荐默认答案：

1. 采用“唯一成功 retrieval log 回填，否则只读新建会话”，避免历史会话跨索引污染。
2. Phase 1 先用规则改写，只处理明显指代；Phase 3 再引入 LLM rewrite。
3. 如果已经做 schema migration，Phase 2 直接采用 `qa_session_summaries`；只在赶进度时选择 `qa_sessions.context_summary` 折中。
4. 学生端采用左侧会话栏，移动端折叠为顶部抽屉。
