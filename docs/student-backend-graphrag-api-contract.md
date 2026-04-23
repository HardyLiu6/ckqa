# Student App 到后端与 GraphRAG API 契约

> 审查日期：2026-04-23
> 适用范围：`frontend/apps/student-app/`、`backend/ckqa-back/`、`graphrag_pipeline/`

本文档定义学员端前端、Java 编排后端和 GraphRAG Python 服务之间的最小稳定契约。当前约定是：`student-app` 只直接调用 `backend/ckqa-back` 的 `/api/v1` 业务接口；`backend/ckqa-back` 再按需调用 `graphrag_pipeline` 的内部任务接口。除调试工具外，前端不应直接调用 `graphrag_pipeline`。

## 角色边界

| 模块 | 角色 | 对外暴露给谁 |
| --- | --- | --- |
| `frontend/apps/student-app/` | 学员端页面、状态展示、轮询任务、错误提示 | 用户浏览器 |
| `backend/ckqa-back/` | `/api/v1` 业务 API、统一响应、MySQL 状态、Python 编排 | `student-app` |
| `graphrag_pipeline/` | GraphRAG CLI 查询包装、异步查询任务、OpenAI 兼容调试接口 | `backend/ckqa-back` 或开发调试工具 |

### 架构决策

- 项目结构：保持现有 feature-first Java 包结构，前端暂不新增业务 API 模块，本契约先作为接入依据。
- API 方式：前端到 Java 使用 REST JSON；Java 到 GraphRAG 也使用 REST JSON。
- 前端请求层：复用 `src/axios/index.js`，通过 `VITE_API_BASE_URL` 指向 Java `/api/v1`。
- 实时方式：问答任务使用轮询，不使用 SSE 或 WebSocket。
- 鉴权策略：当前契约不包含真实登录鉴权，`userId` 仍由前端或测试种子显式提供。
- 错误处理：Java 统一返回 `ApiResponse`；前端 Axios 层会解包 `data` 并把 `message/detail` 归一成错误文案。

## 调用拓扑

```text
student-app
  -> GET/POST ${VITE_API_BASE_URL}/...
  -> backend/ckqa-back /api/v1
  -> MySQL: users / courses / pdf_files / knowledge_bases / qa_sessions / qa_messages / qa_retrieval_logs
  -> graphrag_pipeline /v1/query-tasks
  -> graphrag query --root . --method <local|global|drift|basic> "问题"
```

推荐开发配置：

```env
VITE_API_BASE_URL=http://127.0.0.1:8080/api/v1
VITE_API_TIMEOUT=10000
```

如果通过 Vite 代理转发，也应把代理前缀设计到 `/api/v1`，而不是直接指向 `http://127.0.0.1:8012/v1`。

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
| `4096` | 问答会话已关闭 |
| `4097` | 知识库当前没有可用索引 |
| `5000` | 服务器内部错误 |

## 前端应消费的 Java API

### 健康检查

```http
GET /api/v1/system/health
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
- `ready=false` 表示依赖可达但不满足业务前置条件，例如缺少 `output/lancedb/`。
- 学员端问答入口应优先关注 `graphrag-ready` 或后端后续聚合出的业务就绪状态。

### 课程资源入口

```http
GET /api/v1/courses/{courseId}/pdf-files
GET /api/v1/courses/{courseId}/knowledge-bases
```

PDF 摘要：

```json
[
  {
    "id": 3,
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

### PDF 与索引运维入口

这些接口主要用于后续课程管理或调试页面，学员端问答页面一般不需要直接触发。

| 方法 | 路径 | 说明 |
| --- | --- | --- |
| `GET` | `/api/v1/pdf-files/{id}` | 查看 PDF 解析状态 |
| `GET` | `/api/v1/pdf-files/{id}/results` | 查看 MinerU/标准化/GraphRAG 导出产物 |
| `POST` | `/api/v1/pdf-files/{id}/parse` | 触发 PDF 解析，当前是同步长任务 |
| `POST` | `/api/v1/pdf-files/{id}/export-graphrag` | 触发 GraphRAG JSON 导出 |
| `POST` | `/api/v1/knowledge-bases/{id}/index-runs` | 创建索引任务，当前是同步长任务 |
| `GET` | `/api/v1/knowledge-bases/{id}/index-runs` | 查看知识库索引任务列表 |
| `GET` | `/api/v1/index-runs/{id}` | 查看单个索引任务 |

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

`mode` 仅支持：

- `local`
- `global`
- `drift`
- `basic`

`full` 已归档为后续扩展模式，前端不得展示为可选项。

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
    "taskStatus": null,
    "progressStage": null
  }
]
```

前端建议：

- 刷新页面后先拉取会话和消息列表。
- 用户消息上的 `taskStatus` / `progressStage` 只表示该用户消息关联的最新问答任务摘要。
- assistant 消息本身不携带任务状态。

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
  "prompt": "请概括这套图谱的主题"
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

这些端点不是 student-app 的正式业务契约。正式学员端应通过 Java `/api/v1/qa-sessions` 创建会话、发送消息和轮询任务。

## 接入顺序建议

1. 配置 `VITE_API_BASE_URL=http://127.0.0.1:8080/api/v1`。
2. 页面加载时调用 `GET /system/health`，展示后端和 GraphRAG 就绪状态。
3. 进入课程页时调用 `GET /courses/{courseId}/knowledge-bases`，选取带 `activeIndexRunId` 的知识库。
4. 首次提问前调用 `POST /qa-sessions` 创建会话。
5. 发送问题调用 `POST /qa-sessions/{sessionId}/messages`。
6. 按响应里的 `recommendedPollingIntervalSeconds` 轮询 `GET /qa-sessions/{sessionId}/tasks/{taskId}`。
7. 任务成功后使用 `assistantMessage` 更新 UI；页面刷新时用 `GET /qa-sessions/{id}/messages` 还原消息流。

## 当前明确不在契约内

- 登录、JWT、刷新 token、权限拦截。
- 前端直传 PDF。
- 前端直接触发 GraphRAG `graphrag index`。
- WebSocket / SSE 流式问答。
- `full` 查询模式。
- `qa_retrieval_hits` 的引用来源展示。

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

```bash
curl --noproxy '*' -s http://127.0.0.1:8080/api/v1/qa-sessions/5/tasks/9001
```
