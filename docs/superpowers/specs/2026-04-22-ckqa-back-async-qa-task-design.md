# CKQA Back Async QA Task Design

日期：2026-04-22

## 1. 背景

当前 CKQA 问答链路中，`POST /api/v1/qa-sessions/{id}/messages` 采用同步调用方式：

1. Java 后端直接调用 GraphRAG FastAPI 的 `/v1/chat/completions`
2. GraphRAG FastAPI 再同步等待 `graphrag query` 子进程执行完成
3. Java 在拿到完整响应后，才一次性写入 assistant 消息并返回

这条链路存在两个根本问题：

- `global` 查询耗时长时，外层更早因为 HTTP 读超时判失败，但这并不等价于 `graphrag query` 已经终止
- 前后端都无法判断“任务是否仍在正常运行”，也无法拿到 Graphrag 的中间反馈

本次设计目标不是优先提速，而是修正超时语义：只要 Graphrag 任务仍在运行并持续产生心跳，就不应被外层误判为失败。

## 2. 目标

本次设计要实现以下目标：

- 把问答接口改为异步任务模式，提交问题后立即返回 `taskId`
- 为任务提供独立资源路径，避免 `taskId` 与 `messageId` 语义混淆
- 允许前端轮询获取任务状态、编排阶段、最近 Graphrag 日志片段与最终结果
- 以“Python 侧子进程是否存活 + 心跳是否持续刷新”作为任务是否仍在运行的依据
- 保留现有 `qa_sessions` / `qa_messages` / `qa_retrieval_logs` 主体结构，优先做最小可运行演进
- 清理此前为了“调快 global”而加入、但与本次正确修复方向不一致的冗余改动

## 3. 非目标

本次不做以下事情：

- 不以“提升 global 查询速度”为主目标
- 不在 v1 实现任务取消接口
- 不引入消息队列、Redis、独立任务调度器等额外基础设施
- 不重构为 SSE / WebSocket 推送；前端先使用轮询
- 不实现“用户消息重试”产品功能，但会在数据结构上预留扩展能力

## 4. 关键决策

### 4.1 任务资源独立于消息资源

问答任务不是消息资源本身，轮询路径统一使用：

- `GET /api/v1/qa-sessions/{sessionId}/tasks/{taskId}`

不采用 `messages/{taskId}` 路径，避免后续“单条消息多次重试生成多个任务”时产生歧义。

### 4.2 `taskStatus` 与 `retrievalStatus` 分离

- `taskStatus` 表达异步任务生命周期
- `retrievalStatus` 表达检索结果语义

二者不可混用。

### 4.3 `progressStage` 只表达编排层阶段

`progressStage` 不承载 Graphrag 内部细粒度阶段，只表达后端编排层状态。建议首版枚举：

- `queued`
- `dispatching`
- `running`
- `persisting`
- `done`

Graphrag 内部输出全部进入 `latestLogs`，不继续扩张 `progressStage` 语义。

### 4.4 Python 侧主动刷新心跳

采用“Python 主动刷新心跳，Java 只读快照”的模型：

- Python 任务管理器在子进程存活期间主动刷新 `lastHeartbeatAt`
- 有新日志时同步刷新 `latestLogs`
- Java 只负责轮询 Python 状态接口并镜像到数据库

这样 `stale` 判定不依赖 Java 轮询频率，语义更稳定。

### 4.5 消息列表只补最小任务摘要

`GET /api/v1/qa-sessions/{id}/messages` 只补两个任务摘要字段：

- `taskStatus`
- `progressStage`

`latestLogs`、`errorMessage`、`assistantMessage` 只在任务详情接口返回。

### 4.6 日志 tail 有硬上限

`latestLogs` 采用 tail 文本，不存完整运行日志。应用层强制截断为：

- 最近 20 行
- 且总长度不超过 4000 个字符

截断策略采用“保留尾部最新内容，丢弃更早内容”。

### 4.7 任务上游 ID 命名

上游 Python 任务 ID 字段命名采用 `python_task_id`，不使用 `engine_task_id`，避免未来引擎切换时语义模糊。

## 5. 总体方案

整体链路拆成两段：

1. Java API 层：提交任务、持久化状态、轮询 Python 状态、落最终消息
2. Python GraphRAG 层：创建并托管真实 `graphrag query` 子进程，提供任务状态查询

执行流程如下：

1. 前端调用 `POST /api/v1/qa-sessions/{id}/messages`
2. Java 校验会话、知识库状态
3. Java 写入一条 `user` 消息
4. Java 在 `qa_retrieval_logs` 中创建一条异步任务记录，初始状态 `pending`
5. Java 立即返回：
   - `userMessage`
   - `taskId`
   - `taskStatus=pending`
6. Java 后台线程调用 Python `POST /v1/query-tasks`
7. Python 创建后台任务并启动 `graphrag query` 子进程
8. Java 周期性轮询 Python `GET /v1/query-tasks/{taskId}`
9. Java 将 `taskStatus`、`progressStage`、`latestLogs`、`lastHeartbeatAt` 镜像回 MySQL
10. Python 任务成功后返回完整结果
11. Java 落一条 `assistant` 消息，并把任务状态更新为 `success`
12. 前端通过 `GET /api/v1/qa-sessions/{sessionId}/tasks/{taskId}` 获取最终结果

## 6. API 设计

### 6.1 Java 对外 API

#### 6.1.1 提交问题

`POST /api/v1/qa-sessions/{id}/messages`

请求体沿用当前：

```json
{
  "mode": "global",
  "content": "请概括这套图谱的主题"
}
```

响应体改为异步受理结果：

```json
{
  "userMessage": {
    "id": 101,
    "role": "user",
    "content": "请概括这套图谱的主题"
  },
  "taskId": 9001,
  "taskStatus": "pending",
  "progressStage": "queued",
  "retrievalStatus": null,
  "createdAt": "2026-04-22T15:20:31"
}
```

兼容性说明：

- 该接口不再同步返回 `assistantMessage`
- 前端必须改用任务轮询模式获取最终答案

#### 6.1.2 查询任务详情

`GET /api/v1/qa-sessions/{sessionId}/tasks/{taskId}`

响应体建议：

```json
{
  "taskId": 9001,
  "userMessageId": 101,
  "assistantMessageId": 102,
  "taskStatus": "running",
  "progressStage": "running",
  "retrievalStatus": null,
  "mode": "global",
  "queryText": "请概括这套图谱的主题",
  "latestLogs": [
    "2026-04-22 15:20:35 started graphrag query --method global ...",
    "2026-04-22 15:20:51 process alive, awaiting output"
  ],
  "startedAt": "2026-04-22T15:20:35",
  "lastHeartbeatAt": "2026-04-22T15:21:05",
  "finishedAt": null,
  "assistantMessage": null,
  "errorMessage": null
}
```

成功时：

- `taskStatus=success`
- 返回 `assistantMessage`
- `retrievalStatus=success|partial`

失败时：

- `taskStatus=failed|stale`
- 返回 `errorMessage`

#### 6.1.3 查询消息列表

`GET /api/v1/qa-sessions/{id}/messages`

每条消息仅补最小任务摘要：

```json
{
  "id": 101,
  "role": "user",
  "content": "请概括这套图谱的主题",
  "taskStatus": "running",
  "progressStage": "running"
}
```

补充约定：

- 仅 `role=user` 的消息补 `taskStatus` / `progressStage`
- `assistant` / `system` / `tool` 消息这两个字段返回 `null`

不在消息列表接口返回 `latestLogs`、`assistantMessage`、`errorMessage`。

### 6.2 Python 内部 API

#### 6.2.1 提交 Graphrag 任务

`POST /v1/query-tasks`

请求体：

```json
{
  "mode": "global",
  "prompt": "请概括这套图谱的主题"
}
```

响应体：

```json
{
  "pythonTaskId": "qt_20260422_001",
  "taskStatus": "pending",
  "progressStage": "queued",
  "createdAt": "2026-04-22T15:20:34"
}
```

#### 6.2.2 查询 Graphrag 任务状态

`GET /v1/query-tasks/{taskId}`

响应体建议：

```json
{
  "pythonTaskId": "qt_20260422_001",
  "taskStatus": "running",
  "progressStage": "running",
  "processAlive": true,
  "lastHeartbeatAt": "2026-04-22T15:21:05",
  "latestLogs": [
    "started graphrag query --method global ...",
    "process alive, awaiting output"
  ],
  "resultText": null,
  "errorMessage": null,
  "returnCode": null,
  "startedAt": "2026-04-22T15:20:35",
  "finishedAt": null
}
```

## 7. 状态机设计

### 7.1 `taskStatus`

- `pending`：任务已创建，尚未由后台工作线程接管
- `running`：Python 子进程已创建并且仍存活
- `success`：已拿到最终结果，并成功持久化 assistant 消息
- `failed`：子进程异常退出、上游状态异常、响应解析失败或持久化失败
- `cancelled`：预留状态，取消入口待定，本次不实现
- `stale`：超过心跳阈值仍未收到有效心跳，视为异常挂起

### 7.2 `retrievalStatus`

- `success`
- `partial`
- `failed`

说明：

- `retrievalStatus` 仅在终态写入
- `pending/running/stale` 期间允许为 `null`

## 8. 数据库设计

### 8.1 复用 `qa_retrieval_logs` 作为任务表

现有 [qa_retrieval_logs](/home/sunlight/Projects/ckqa/.worktrees/ckqa-back-phase1/pdf_ingest/sql/ocqa.sql:375) 扩展为“任务 + 检索日志”双语义载体。

新增字段建议如下：

- `user_message_id bigint not null`
- `assistant_message_id bigint null`
- `task_seq int not null default 1`
- `task_status varchar(32) not null`
- `progress_stage varchar(32) not null`
- `python_task_id varchar(128) null`
- `latest_logs text null`
- `started_at timestamp null`
- `last_heartbeat_at timestamp null`
- `finished_at timestamp null`

保留并调整语义的字段：

- `retrieval_status`：允许为空，仅终态写入
- `error_message`：终态失败信息
- `query_mode`
- `query_text`
- `course_id`
- `index_run_id`
- `session_id`

### 8.2 关联关系

- `user_message_id -> qa_messages.id`
- `assistant_message_id -> qa_messages.id`

当前产品语义下：

- 一条 `user_message` 在 v1 只会创建一个任务

为未来重试预留：

- 同一 `user_message_id` 允许多条 task 记录
- 通过 `task_seq` 区分第几次任务

### 8.3 索引建议

- `idx_retrieval_logs_session_created (session_id, created_at)`
- `idx_retrieval_logs_user_message_seq (user_message_id, task_seq)`
- `idx_retrieval_logs_task_status_heartbeat (task_status, last_heartbeat_at)`
- `uk_retrieval_logs_python_task_id (python_task_id)`，仅在非空时唯一

## 9. Python 任务管理设计

### 9.1 任务管理器职责

GraphRAG FastAPI 新增一个进程内任务管理器，职责包括：

- 生成 `pythonTaskId`
- 启动 `graphrag query` 子进程
- 持续读取 `stdout/stderr`
- 维护最近日志 tail
- 在子进程存活期间主动刷新 `lastHeartbeatAt`
- 维护任务快照供 `GET /v1/query-tasks/{taskId}` 查询

### 9.2 心跳策略

- 任务启动后立即写一次心跳
- 子进程存活期间按固定间隔主动刷新心跳
- 有新日志时也刷新心跳

推荐默认值：

- Python 侧心跳间隔：5 秒

### 9.3 日志策略

- `stdout` 与 `stderr` 都纳入 `latestLogs`
- 保留最近 20 行
- 最终返回给 Java 前裁剪到 4000 字符以内
- Python 内部状态快照中可维护为字符串数组；落库到 `qa_retrieval_logs.latest_logs` 时统一拼接为换行文本
- Java 对外任务详情接口返回时，再把换行文本拆分回字符串数组

### 9.4 结果策略

子进程成功退出时：

- `taskStatus=success`
- `progressStage=done`
- 返回 `resultText`

子进程非零退出时：

- `taskStatus=failed`
- 返回 `errorMessage`
- 携带 `returnCode`

## 10. Java 编排设计

### 10.1 异步问答工作流

新增 Java 问答任务编排服务，职责包括：

- 创建 `userMessage`
- 创建初始 task 记录
- 后台调用 Python `POST /v1/query-tasks`
- 轮询 Python `GET /v1/query-tasks/{taskId}`
- 将状态镜像回 MySQL
- 成功时写 `assistantMessage`
- 失败时写 `errorMessage`

### 10.2 后台执行模型

Java 不再在请求线程里等待 GraphRAG 结果，而是在后台执行：

- 请求线程只负责“受理任务并返回”
- 后台线程负责状态推进和结果落库

首版可直接使用 Spring 本地线程池，不引入消息队列。

### 10.3 `stale` 判定

`stale` 判定放在 Java 侧，依据为：

- `taskStatus` 仍是 `pending|running`
- 当前时间减去 `lastHeartbeatAt` 超过阈值

配置项采用现有集成配置风格，建议新增：

- `ckqa.integration.timeout.query-task-stale-seconds=${QUERY_TASK_STALE_SECONDS:600}`

默认值建议 600 秒，即 10 分钟。

## 11. 冗余改动清理范围

本次实现需要清理此前为了“调快 global 查询”而加入、但与本次正确修复方向不一致的改动。

### 11.1 建议保留

- GraphRAG 3 的 CLI 位置参数适配
- `vector_store.db_uri` 跟随 `GRAPHRAG_STORAGE_DIR`
- 本地 loopback 模型网关的 `NO_PROXY` 保护

### 11.2 建议移除

- `ApiRuntimeConfig` 中新增的 `GRAPHRAG_GLOBAL_SEARCH_*` 配置
- `utils/main.py` 中基于这些配置拼接 global 查询策略参数的逻辑
- `settings.yaml` 中仅用于这次错误方向调参的 `dynamic_search_use_summary` / `dynamic_search_max_level`
- 对应只验证这些错误方向逻辑的测试断言

## 12. 测试与验证

### 12.1 Java 侧

- 控制器测试：
  - 提交消息返回 `taskId`
  - 查询任务详情返回状态与日志
  - 消息列表只返回 `taskStatus` / `progressStage`
- 服务测试：
  - 创建任务成功
  - Python 返回成功时落 assistant 消息
  - Python 返回失败时写错误信息
  - 超过阈值转 `stale`

### 12.2 Python 侧

- 提交任务接口测试
- 查询任务接口测试
- 子进程存活时主动刷新心跳
- 日志 tail 截断行为
- 子进程非零退出时状态转失败

### 12.3 冒烟验证

- 启动 Java 后端与 GraphRAG FastAPI
- 提交一条 `global` 问答
- 确认立即返回 `taskId`
- 轮询任务状态，观察 `running` 与 `latestLogs`
- 最终确认落库 assistant 消息
- 人为制造长时查询，确认在无新结果但持续心跳情况下不被误判失败

## 13. 风险与后续

### 13.1 已知风险

- Java 与 Python 双层任务状态存在短暂不一致窗口
- Python 任务状态目前为进程内内存态，Python 服务重启会丢失快照
- `latestLogs` 仅保留 tail，不能替代完整诊断日志

### 13.2 后续建议

- v2 可增加取消接口：`POST /tasks/{taskId}/cancel`
- v2 可考虑 Python 侧任务状态落盘或写数据库，提高重启恢复能力
- 若前端轮询压力上升，再评估 SSE 推送
