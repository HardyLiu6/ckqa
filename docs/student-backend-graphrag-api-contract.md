# Student App 到后端与 GraphRAG API 契约

> 审查日期：2026-06-02
> 适用范围：`frontend/apps/student-app/`、`backend/ckqa-back/`、`graphrag_pipeline/`

本文档定义学员端前端、Java 编排后端和 GraphRAG Python 服务之间的最小稳定契约。当前约定是：`student-app` 只直接调用 `backend/ckqa-back` 的 `/api/v1` 业务接口；`backend/ckqa-back` 再按需调用 `graphrag_pipeline` 的内部任务接口。除调试工具外，前端不应直接调用 `graphrag_pipeline`。

## 角色边界

| 模块 | 角色 | 对外暴露给谁 |
| --- | --- | --- |
| `frontend/apps/student-app/` | 学员端页面、状态展示、任务事件流/轮询兜底、错误提示 | 用户浏览器 |
| `backend/ckqa-back/` | `/api/v1` 业务 API、统一响应、MySQL 状态、Python 编排 | `student-app` |
| `graphrag_pipeline/` | GraphRAG CLI 查询包装、异步查询任务、OpenAI 兼容调试接口 | `backend/ckqa-back` 或开发调试工具 |

### 架构决策

- 项目结构：保持现有 feature-first Java 包结构；前端业务 API 已按 `auth/courses/qa/graph` 拆成轻量封装，本契约作为这些封装继续演进的对齐依据。
- API 方式：前端到 Java 使用 REST JSON；Java 到 GraphRAG 也使用 REST JSON。
- 前端请求层：复用 `src/axios/index.js`，通过 `VITE_API_BASE_URL` 指向 Java `/api/v1`；业务接口封装在 `src/api/auth.js`、`src/api/courses.js`、`src/api/qa.js`、`src/api/graph.js`。
- 实时方式：问答任务优先使用 SSE 任务事件流，事件流不可用时回退到任务详情轮询。
- 鉴权策略：学生端登录注册走 Java `/api/v1/auth/*`；Pinia user store 保存 JWT 会话，Axios 自动注入 `Authorization` 和 `X-CKQA-User-Code`。少量请求体仍保留 `userId` 兼容字段，但前端通用列表、推荐、反馈等请求会优先依赖登录态。
- 错误处理：Java 统一返回 `ApiResponse`；前端 Axios 层会解包 `data` 并把 `message/detail` 归一成错误文案。

## 调用拓扑

```text
student-app
  -> GET/POST ${VITE_API_BASE_URL}/...
  -> GET ${VITE_API_BASE_URL}/qa-sessions/{sessionId}/tasks/{taskId}/events (SSE, 可回退轮询)
  -> backend/ckqa-back /api/v1
  -> MySQL: users / courses / course_materials / material_objects / knowledge_bases / qa_sessions / qa_messages / qa_retrieval_logs
  -> graphrag_pipeline /v1/query-tasks
  -> graphrag query --root . --method <local|global|drift|basic> "问题"
```

推荐开发配置：

```env
VITE_API_BASE_URL=http://127.0.0.1:8080/api/v1
VITE_API_TIMEOUT=10000
```

如果通过 Vite 代理转发，也应把代理前缀设计到 `/api/v1`，而不是直接指向 `http://127.0.0.1:8012/v1`。

## 学生端路由状态约定

`/qa/ask` 会使用 query 记录可恢复的问答上下文：

| query | 说明 |
| --- | --- |
| `courseId` | 当前课程。来自课程选择、知识图谱跳转或历史会话恢复。 |
| `sessionId` | 当前问答会话。存在时页面会拉取会话详情和消息列表。 |
| `mode` | 前端选择的问答模式。支持 `smart`、`basic`、`local`、`global`、`drift`、`hybrid_v0`，其中 `smart` 只在前端触发 `/qa-routing/recommend`。 |
| `topic` | 从知识图谱节点等入口带入的预填问题，不等同于已发送消息。 |

学生端通过 `src/views/qa/qa-route-query-model.js` 规范化这些字段。模块布局的 `RouterView` key 只跟路由名/路径和 params 相关，不跟 query/hash 相关；query-only 变化应由页面 watch 响应，避免整页重挂导致事件流、输入框或加载状态丢失。

## 通用 Java 响应格式

所有 `backend/ckqa-back` 业务接口统一返回：

```json
{
  "code": 200,
  "message": "操作成功",
  "data": {},
  "timestamp": "2026-04-23T15:30:00"
}
```

字段约定：

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| `code` | number | 业务码。成功固定为 `200`，不要等同于 HTTP 状态码。 |
| `message` | string | 面向前端展示或调试的默认消息。 |
| `data` | object / array / null | 实际业务数据。student-app 当前 Axios 层会默认返回这一层。 |
| `timestamp` | string | Java 响应时间，`LocalDateTime` 字符串，无时区偏移。 |

常见业务码：

| code | 语义 |
| ---: | --- |
| `200` | 操作成功 |
| `4000` | 参数错误 |
| `4001` | 参数校验失败 |
| `4044` | 用户不存在 |
| `4045` | PDF 文件不存在 |
| `4046` | 知识库不存在 |
| `4047` | 索引任务不存在 |
| `4048` | 问答会话不存在 |
| `4049` | 知识库构建流水线不存在 |
| `4096` | 问答会话已关闭 |
| `4097` | 知识库当前没有可用索引 |
| `4099` | 当前知识库已有构建流水线未完成 |
| `5000` | 服务器内部错误 |

## 前端应消费的 Java API

### 健康检查

```http
GET /api/v1/system/health
GET /api/v1/system/readiness
```

`data` 形态：

```json
{
  "up": true,
  "items": [
    {
      "name": "graphrag-api",
      "reachable": true,
      "ready": true,
      "message": "GraphRAG API 可访问"
    }
  ]
}
```

前端建议：

- `reachable=false` 表示依赖不可达。
- `GET /system/health` 是轻量健康检查，重点覆盖 `mysql`、`redis`、`pdf-ingest-root`、`graphrag-root`、`graphrag-build-runs-root`、`graphrag-api`、`graphrag-ready` 和 `neo4j`。Redis 只用于服务端读缓存和短期状态，失败时学生端 API 回源执行。Neo4j 用于学生端知识图谱只读浏览，不可达时图谱页应显式降级。
- `GET /system/readiness` 会额外检查共享 `GRAPHRAG_ROOT/output` 和 `output/lancedb`，它们主要用于手工 CLI 调试和兼容旧调用，不再是管理端 build-run 构建的唯一就绪条件。
- 学员端问答入口应优先关注知识库摘要里的 `activeIndexRunId` 以及后端后续聚合出的业务就绪状态。

### 课程资源入口

```http
GET /api/v1/courses/{courseId}/pdf-files
GET /api/v1/courses/{courseId}/knowledge-bases
```

说明：

- `/api/v1/courses/{courseId}/pdf-files` 仍保留旧路径名作为兼容入口。
- 后端内部真实数据源已经切到 `course_materials + material_objects`。

PDF 摘要：

```json
[
  {
    "id": 3,
    "materialId": 3,
    "materialObjectId": 11,
    "fileName": "book.pdf",
    "parseStatus": "done"
  }
]
```

知识库摘要：

```json
[
  {
    "id": 3,
    "kbCode": "os-main",
    "name": "操作系统知识库",
    "status": "active",
    "activeIndexRunId": 2
  }
]
```

前端建议：

- `knowledgeBaseId` 来自知识库摘要的 `id`。
- 只有 `activeIndexRunId != null` 的知识库才适合创建真实问答会话。

### 学生端知识图谱浏览入口

```http
GET /api/v1/knowledge-bases/{knowledgeBaseId}/graph/overview
GET /api/v1/knowledge-bases/{knowledgeBaseId}/graph/entities/{entityId}
GET /api/v1/knowledge-bases/{knowledgeBaseId}/graph/entities/{entityId}/neighborhood
```

前端行为：

- 图谱页先通过课程 query 或默认课程选择可用知识库，优先使用 `activeIndexRunId != null` 的知识库。
- `/graph/overview` 用于顶层社区、Top-N 实体和社区间关系的初始画布。
- `/graph/entities/{entityId}` 用于实体详情；`/neighborhood` 用于展开实体邻域。
- 从实体详情跳转问答时，应写入 `/qa/ask?courseId=<courseId>&topic=<entityName>`，由问答页恢复课程上下文并预填问题。
- Neo4j 不可达或图谱接口失败时，页面应显示可读错误和重试入口，不要静默回退到本地 mock。

### PDF 与索引运维入口

这些接口主要用于后续课程管理或调试页面，学员端问答页面一般不需要直接触发。

| 方法 | 路径 | 说明 |
| --- | --- | --- |
| `GET` | `/api/v1/pdf-files/{id}` | 查看 PDF 解析状态 |
| `GET` | `/api/v1/pdf-files/{id}/results` | 查看 MinerU/标准化/GraphRAG 导出产物 |
| `POST` | `/api/v1/pdf-files/{id}/parse` | 触发 PDF 解析，当前是同步长任务 |
| `POST` | `/api/v1/pdf-files/{id}/export-graphrag` | 触发 GraphRAG JSON 导出 |
| `POST` | `/api/v1/knowledge-bases/{id}/index-runs` | 兼容入口：内部会桥接为 build run 后再创建索引任务 |
| `GET` | `/api/v1/knowledge-bases/{id}/index-runs` | 查看知识库索引任务列表 |
| `GET` | `/api/v1/index-runs/{id}` | 查看单个索引任务 |

管理端知识库构建向导使用 build-run API，而不是让浏览器直接接触本机目录：

| 方法 | 路径 | 说明 |
| --- | --- | --- |
| `POST` | `/api/v1/knowledge-bases/{id}/build-runs` | 创建一次知识库构建流水线 |
| `GET` | `/api/v1/knowledge-bases/{id}/build-runs` | 分页查看构建流水线 |
| `POST` | `/api/v1/knowledge-bases/{id}/build-runs/gc` | 清理旧构建流水线，默认 dry-run |
| `GET` | `/api/v1/knowledge-base-build-runs/{id}` | 查看构建详情 |
| `DELETE` | `/api/v1/knowledge-base-build-runs/{id}` | 归档单个构建流水线，默认保留本地 artifacts |
| `PUT` | `/api/v1/knowledge-base-build-runs/{id}/material-selection` | 更新本次构建资料选择 |
| `POST` | `/api/v1/knowledge-base-build-runs/{id}/parse-check` | 确认资料解析状态 |
| `POST` | `/api/v1/knowledge-base-build-runs/{id}/graph-input` | 同步 GraphRAG 输入到独立 workspace |
| `POST` | `/api/v1/knowledge-base-build-runs/{id}/prompt-confirmation` | 确认 Prompt 策略 |
| `POST` | `/api/v1/knowledge-base-build-runs/{id}/index-runs` | 在 build-run workspace 内建索引 |
| `POST` | `/api/v1/knowledge-base-build-runs/{id}/qa-smoke` | 使用该 build run 的索引发起 QA 冒烟验证 |

学生端当前不直接触发这些构建接口；它只消费已经激活的知识库索引。后端不会把 `${GRAPHRAG_BUILD_RUNS_ROOT}` 下的绝对路径暴露给浏览器。

这些 `/api/v1/pdf-files/*` 路径同样是兼容保留名；`{id}` 当前实际对应 `course_materials.id`，响应体会额外补出 `materialId` 与 `materialObjectId`。

导出请求体：

```json
{
  "mode": "section",
  "withPageDocs": true,
  "force": false
}
```

### 创建问答会话

```http
POST /api/v1/qa-sessions
Content-Type: application/json
```

请求体：

```json
{
  "userId": 3,
  "courseId": "os",
  "knowledgeBaseId": 3,
  "title": "操作系统问答"
}
```

`data` 形态：

```json
{
  "id": 5,
  "sessionCode": "qa-0001",
  "userId": 3,
  "courseId": "os",
  "knowledgeBaseId": 3,
  "title": "操作系统问答",
  "status": "active",
  "lastMessageAt": null,
  "createdAt": "2026-04-23T15:30:00"
}
```

约束：

- `userId` 必填且必须大于 `0`。
- `knowledgeBaseId` 可空，但不绑定知识库的会话不能发送真实问答消息。
- `status` 为 `active` 时才允许继续发送消息。

### 发送问答消息

```http
POST /api/v1/qa-sessions/{sessionId}/messages
Content-Type: application/json
```

请求体：

```json
{
  "mode": "basic",
  "content": "请概括这套图谱的主题"
}
```

发送到 Java 后端的 `mode` 支持：

- `basic`
- `local`
- `global`
- `drift`
- `hybrid_v0`

`smart` 是 student-app 的前端选择项，不直接作为最终查询模式提交；前端会先调用 `/api/v1/qa-routing/recommend` 获取推荐结果，再落到上述后端模式之一。`full` 已归档为后续扩展模式，前端不得展示为可选项。

提交成功后的 `data`：

```json
{
  "userMessage": {
    "id": 101,
    "sessionId": 5,
    "role": "user",
    "sequenceNo": 1,
    "content": "请概括这套图谱的主题",
    "createdAt": "2026-04-23T15:31:00",
    "mode": "basic",
    "taskStatus": null,
    "progressStage": null
  },
  "taskId": 9001,
  "taskStatus": "pending",
  "progressStage": "queued",
  "retrievalStatus": null,
  "createdAt": "2026-04-23T15:31:00",
  "mode": "basic",
  "recommendedPollingIntervalSeconds": 10,
  "staleTimeoutSeconds": 300,
  "timeoutMessage": "basic 模式沿用轻量查询策略；任务心跳超过阈值未更新后会被标记为 stale。"
}
```

前端行为：

- 发送成功后立即把 `userMessage` 追加到本地消息流。
- 记录 `taskId`，按 `recommendedPollingIntervalSeconds` 调用任务详情接口。
- 在终态前不要自行构造 assistant 消息，assistant 消息只以任务详情或消息列表返回为准。

### 订阅问答任务事件流

```http
GET /api/v1/qa-sessions/{sessionId}/tasks/{taskId}/events?afterEventSeq=<lastSeenEventSeq>
Accept: text/event-stream
Authorization: Bearer <token>
```

学生端当前事件处理约定：

| event | 前端行为 |
| --- | --- |
| `open` | 客户端连接成功回调，不是服务端 SSE event；用于标记任务进入 streaming 状态。 |
| `ack` | 服务端确认已建立当前 session/task 事件流。 |
| `status` | 更新 `pendingTask`、用户消息上的 `taskStatus/progressStage`。 |
| `heartbeat` | 记录最近事件流心跳。 |
| `progress` | 更新检索进度轨迹，通常携带 `eventSeq`，前端展示在 `QaRetrievalTrace`。 |
| `delta` | 追加临时流式文本。 |
| `sources` | 更新来源列表。 |
| `message` | 使用后端返回的 assistant 消息更新消息流。 |
| `done` | 结束事件流，必要时再拉取任务详情或消息列表补齐 assistant 消息。 |
| `error` | 展示错误；网络错误时切回轮询。 |

事件流不可用、连接关闭或浏览器环境不支持 `AbortController` 时，前端必须回退到下面的任务详情轮询接口。若前端已经收到带 `eventSeq` 的 `progress` / `delta`，重连时应把最后一个已消费序号作为 `afterEventSeq` 查询参数传回 Java，避免重复拼接已展示文本；后端会把该序号继续传给 Python `/v1/query-tasks/{pythonTaskId}/events`。

### 轮询问答任务

```http
GET /api/v1/qa-sessions/{sessionId}/tasks/{taskId}
```

运行中 `data` 示例：

```json
{
  "taskId": 9001,
  "userMessageId": 101,
  "assistantMessageId": null,
  "taskStatus": "running",
  "progressStage": "running",
  "retrievalStatus": "running",
  "mode": "global",
  "queryText": "请概括这套图谱的主题",
  "latestLogs": ["started graphrag query --method global"],
  "startedAt": "2026-04-23T15:31:05",
  "lastHeartbeatAt": "2026-04-23T15:32:05",
  "finishedAt": null,
  "assistantMessage": null,
  "errorMessage": null,
  "recommendedPollingIntervalSeconds": 30,
  "staleTimeoutSeconds": 1800,
  "timeoutMessage": "global 模式实测可能需要 10 到 20 分钟；建议前端低频轮询并展示长耗时提示。"
}
```

成功终态 `data` 示例：

```json
{
  "taskId": 9001,
  "userMessageId": 101,
  "assistantMessageId": 102,
  "taskStatus": "success",
  "progressStage": "done",
  "retrievalStatus": "success",
  "mode": "basic",
  "queryText": "请概括这套图谱的主题",
  "latestLogs": ["done"],
  "startedAt": "2026-04-23T15:31:05",
  "lastHeartbeatAt": "2026-04-23T15:32:05",
  "finishedAt": "2026-04-23T15:32:20",
  "assistantMessage": {
    "id": 102,
    "sessionId": 5,
    "role": "assistant",
    "sequenceNo": 2,
    "content": "图谱主题集中在操作系统概念网络。",
    "createdAt": "2026-04-23T15:32:20",
    "mode": "basic",
    "taskStatus": null,
    "progressStage": null
  },
  "errorMessage": null,
  "recommendedPollingIntervalSeconds": 10,
  "staleTimeoutSeconds": 300,
  "timeoutMessage": "basic 模式沿用轻量查询策略；任务心跳超过阈值未更新后会被标记为 stale。"
}
```

前端轮询规则：

- 终态集合：`success`、`failed`、`stale`。
- `taskStatus=success` 且 `assistantMessage != null` 时，把 `assistantMessage` 追加或替换到消息流。
- `taskStatus=failed` 时展示 `errorMessage`，并保留用户消息的失败状态。
- `taskStatus=stale` 且 `progressStage=done` 表示心跳超时回收，不代表 GraphRAG 给出了答案。
- 只要 `lastHeartbeatAt` 持续更新，`global` / `drift` 的长耗时不应被前端硬判失败。

### 查询会话与消息

```http
GET /api/v1/qa-sessions/{id}
GET /api/v1/qa-sessions/{id}/messages
```

消息列表 `data` 示例：

```json
[
  {
    "id": 101,
    "sessionId": 5,
    "role": "user",
    "sequenceNo": 1,
    "content": "请概括这套图谱的主题",
    "createdAt": "2026-04-23T15:31:00",
    "mode": "global",
    "taskStatus": "running",
    "progressStage": "running"
  },
  {
    "id": 102,
    "sessionId": 5,
    "role": "assistant",
    "sequenceNo": 2,
    "content": "图谱主题集中在操作系统概念网络。",
    "createdAt": "2026-04-23T15:32:20",
    "mode": "global",
    "taskStatus": null,
    "progressStage": null
  }
]
```

前端建议：

- 刷新页面后先拉取会话和消息列表。
- 用户消息和 assistant 消息都携带 `mode`，表示该条消息关联任务实际使用的查询模式；前端模式标签必须读取 `message.mode`，不要用当前选择器或会话默认模式回填历史消息。
- 用户消息上的 `taskStatus` / `progressStage` 只表示该用户消息关联的最新问答任务摘要。
- assistant 消息本身不携带任务状态。

### 模式推荐、混合检索预热、学习记忆与反馈

学生端问答页还会消费这些 Java `/api/v1` 辅助接口：

| 方法 | 路径 | 说明 |
| --- | --- | --- |
| `POST` | `/qa-routing/domain-check` | 课程问答域校验。前端在创建新 session 或发送消息前调用，用于已解析课程/知识库后的明显非课程问题拦截。 |
| `POST` | `/qa-routing/recommend` | 智能模式推荐。前端传 `courseId`、`knowledgeBaseId`、`sessionId`、`question`、`betaHybridEnabled`、`hasConversationContext`，并用返回的 `recommendedMode/confidence/reasons/fallbackMode` 决定最终模式。 |
| `POST` | `/qa-sessions/hybrid-warmup` | `hybrid_v0` 混合检索预热；未 ready 时智能模式可降级到 fallback，手动 hybrid 则保留选择并提示。 |
| `GET` | `/qa-memory/preferences` | 查询当前课程/知识库下的学习记忆偏好。 |
| `PUT` | `/qa-memory/preferences` | 开启或关闭学习记忆。当前前端只在 `local` 模式且用户开启记忆时提交 `memoryPolicy=auto`。 |
| `GET` | `/qa-memory/items` | 查询学习记忆条目，用于展示和删除。 |
| `DELETE` | `/qa-memory/items/{id}` | 删除单条学习记忆。 |
| `POST` | `/qa-message-feedback` | 提交 assistant 消息反馈。 |
| `DELETE` | `/qa-message-feedback/{messageId}` | 取消某条消息反馈。 |

前端约束：

- 这些接口仍通过 `src/api/qa.js` 统一封装，不在页面中手写 URL。
- 推荐和反馈请求不应依赖浏览器传入的 `userId`；登录态由 Axios 统一注入。
- 学习记忆只在已选择 `courseId` 与 `knowledgeBaseId` 后启用。

#### 课程问答域校验

`POST /api/v1/qa-routing/domain-check` 是学生端提交问题前的轻量规则守卫，用于课程资料已经解析、知识库已经可用之后，识别明显不属于当前课程问答域的问题。它仍属于 Java `/api/v1` 契约；浏览器不直接调用 GraphRAG Python 服务。

请求体：

```json
{
  "courseId": "os",
  "knowledgeBaseId": 3,
  "sessionId": 5,
  "question": "今天晚上吃什么",
  "hasConversationContext": false
}
```

字段约定：

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| `courseId` | string | 当前课程 ID。首次提问前可只传课程和知识库上下文。 |
| `knowledgeBaseId` | number | 当前课程下已激活、可问答的知识库 ID。 |
| `sessionId` | number / null | 已有会话继续追问时传入；首次提问、尚未创建 session 时可为空。 |
| `question` | string | 用户准备提交的问题。 |
| `hasConversationContext` | boolean | 当前问题是否有可用的会话上下文；续聊追问时通常为 `true`。 |

响应 `data`：

```json
{
  "status": "out_of_scope",
  "reasonCode": "campus_life",
  "message": "这个问题更像校园生活咨询，不属于当前课程资料问答范围。",
  "strategy": "rule_domain_guard_v1"
}
```

字段约定：

| 字段 | 说明 |
| --- | --- |
| `status` | `allowed` 或 `out_of_scope`。 |
| `reasonCode` | 规则命中的原因编码；允许继续时可为 `course_or_uncertain`。 |
| `message` | 前端可直接展示的提示文案。 |
| `strategy` | 固定为 `rule_domain_guard_v1`。 |

前端行为：

- `status=allowed` 时，按原流程继续创建新 session 或发送消息。
- `status=out_of_scope` 时，不创建 session、不发送消息、不启动 task，直接展示后端返回的 `message`。
- 接口不可用、超时、返回体不可解析或 Axios 层无法识别 `status` 时，采用 fail-open：继续提交问题，避免因为守卫异常阻断正常课程问答。
- 该接口只做明显非课程问题的前置拦截；弱课程意图、上下文追问或无法确定的问题应继续进入后续推荐和问答流程。

强负样本：

| 问题 | 预期 |
| --- | --- |
| `今天晚上吃什么` | `out_of_scope` |
| `今天晚上食堂有什么菜？` | `out_of_scope` |
| `帮我写一首关于春天的短诗` | `out_of_scope` |
| `我的头像应该怎么换？` | `out_of_scope` |
| `这门课什么时候期末考试？` | `out_of_scope` |

## Java 到 GraphRAG 的内部契约

前端通常不直接使用本节端点。它们用于理解 Java 编排层如何和 Python 服务协作。

### 提交 Python 查询任务

```http
POST http://127.0.0.1:8012/v1/query-tasks
Content-Type: application/json
```

请求体：

```json
{
  "mode": "basic",
  "prompt": "请概括这套图谱的主题",
  "indexRunId": 18,
  "dataDirUri": "user_2/kb_5/build_27/index/output"
}
```

返回：

```json
{
  "pythonTaskId": "qt_20260423_153100_001",
  "taskStatus": "pending",
  "progressStage": "queued",
  "createdAt": "2026-04-23T15:31:00"
}
```

字段约定：

- `indexRunId` 用于 Java 和 Python 日志侧关联当前激活索引。
- `dataDirUri` 是相对 `GRAPHRAG_BUILD_RUNS_ROOT` 的后端内部路径，Python 会拒绝绝对路径、`..` 逃逸和反斜杠路径。
- 旧调用不传 `dataDirUri` 时，Python 仍会回退到共享 `output/`，该路径只作为 CLI 调试和兼容保留。

### 抽取课程画像 hints

```http
POST http://127.0.0.1:8012/v1/internal/course-routing/profile-hints
Content-Type: application/json
```

该端点只供 Java 内部课程路由画像生成使用。Python 会从 `section_docs.json` / `text_units.parquet` 抽取章节来源和关键词 hints，不读取 `entities` / `community_reports` 派生产物。

请求体：

```json
{
  "courseId": "crs-20260506-r4slkr",
  "dataDirUris": ["user_0/kb_5/build_19/index/output"],
  "seedKeywords": ["操作系统2026春", "操作系统"],
  "maxHints": 24
}
```

响应：

```json
{
  "items": [
    {
      "heading": "第六章 输入输出系统 > 6.3 中断机构和中断处理程序",
      "keywords": ["I/O", "设备驱动程序", "中断", "轮询"],
      "sourceType": "text_units",
      "sourceRef": "249",
      "score": 4.0
    }
  ],
  "sourceCounts": {
    "text_units": 1
  }
}
```

`dataDirUris` 仍必须是相对 `GRAPHRAG_BUILD_RUNS_ROOT` 的安全路径；Python 会拒绝绝对路径、`..` 逃逸和解析后落到根目录外的路径。

### 查询 Python 任务快照

```http
GET http://127.0.0.1:8012/v1/query-tasks/{pythonTaskId}
```

返回：

```json
{
  "pythonTaskId": "qt_20260423_153100_001",
  "taskStatus": "success",
  "progressStage": "done",
  "processAlive": false,
  "createdAt": "2026-04-23T15:31:00",
  "startedAt": "2026-04-23T15:31:05",
  "lastHeartbeatAt": "2026-04-23T15:32:05",
  "finishedAt": "2026-04-23T15:32:20",
  "latestLogs": ["done"],
  "resultText": "图谱主题集中在操作系统概念网络。",
  "errorMessage": null,
  "returnCode": 0
}
```

时间字段约定：

- Python 内部使用 UTC aware 时间。
- Python 对外 JSON 转为 `Asia/Shanghai` 的无偏移 `LocalDateTime` 字符串。
- Java DTO 按 `LocalDateTime` 解析，并用上海时区 `Clock` 做 stale 判断。
- 前端只展示字符串，不要自行添加 UTC 偏移或二次时区转换。

## OpenAI 兼容接口的定位

`graphrag_pipeline` 仍保留 OpenAI 兼容接口用于调试和兼容：

```http
POST /v1/chat/completions
GET /v1/models
GET /health
```

当前公开模型：

- `graphrag-local-search:latest`
- `graphrag-global-search:latest`
- `graphrag-drift-search:latest`
- `graphrag-basic-search:latest`

这些端点不是 student-app 的正式业务契约。正式学员端应通过 Java `/api/v1/qa-sessions` 创建会话、发送消息，并优先消费任务事件流、必要时回退轮询。

## 接入顺序建议

1. 配置 `VITE_API_BASE_URL=http://127.0.0.1:8080/api/v1`。
2. 页面加载时调用 `GET /system/health`，展示后端和 GraphRAG 就绪状态。
3. 进入课程页时调用 `GET /courses/{courseId}/knowledge-bases`，选取带 `activeIndexRunId` 的知识库。
4. 用户提交问题后，先调用 `POST /qa-routing/domain-check` 做课程问答域校验；`allowed` 或 fail-open 时继续，`out_of_scope` 时展示 `message` 并停止本次提交。
5. 需要智能模式时调用 `POST /qa-routing/recommend`，把 `smart` 落到后端支持的实际模式。
6. 首次提问且尚无会话时，调用 `POST /qa-sessions` 创建会话。
7. 发送问题调用 `POST /qa-sessions/{sessionId}/messages`。
8. 优先连接 `GET /qa-sessions/{sessionId}/tasks/{taskId}/events` 消费 SSE 事件。
9. 事件流不可用时，按响应里的 `recommendedPollingIntervalSeconds` 轮询 `GET /qa-sessions/{sessionId}/tasks/{taskId}`。
10. 任务成功后使用 `assistantMessage` 或事件流 `message` 更新 UI；页面刷新时用 `GET /qa-sessions/{id}/messages` 还原消息流。

## 当前明确不在契约内

- 前端直传 PDF。
- 前端直接触发 GraphRAG `graphrag index`。
- `full` 查询模式。
- WebSocket 双向通道。
- 完整引用来源管理工作台；当前 `qa_retrieval_hits` / `qa_source_reviews` 已作为来源持久化和复核基础表存在，admin-app 已有问答运维列表，但检索日志详情与来源复核体验仍需继续补齐。

## 最小验证命令

启动顺序：

```bash
cd graphrag_pipeline
python utils/main.py
```

```bash
cd backend/ckqa-back
./mvnw spring-boot:run
```

健康检查：

```bash
curl --noproxy '*' http://127.0.0.1:8080/api/v1/system/health
```

最小问答链路：

```bash
curl --noproxy '*' -s -X POST http://127.0.0.1:8080/api/v1/qa-sessions \
  -H 'Content-Type: application/json' \
  -d '{"userId":3,"courseId":"os","knowledgeBaseId":3,"title":"操作系统问答"}'
```

下面示例中的 `5` 和 `9001` 需要替换为上一步实际返回的 `session.id` 和 `taskId`。

```bash
curl --noproxy '*' -s -X POST http://127.0.0.1:8080/api/v1/qa-sessions/5/messages \
  -H 'Content-Type: application/json' \
  -d '{"mode":"basic","content":"请概括这套图谱的主题"}'
```

如果要检查事件流，把返回的 `taskId` 替换到下面命令，并带上实际 JWT：

```bash
curl --noproxy '*' -N 'http://127.0.0.1:8080/api/v1/qa-sessions/5/tasks/9001/events?afterEventSeq=0' \
  -H 'Accept: text/event-stream' \
  -H 'Authorization: Bearer <token>'
```

```bash
curl --noproxy '*' -s http://127.0.0.1:8080/api/v1/qa-sessions/5/tasks/9001
```
