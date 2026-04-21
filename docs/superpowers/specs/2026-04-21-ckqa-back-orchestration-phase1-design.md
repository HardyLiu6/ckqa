# CKQA Java 后端一期编排接入设计

- 日期：2026-04-21
- 范围：`backend/ckqa-back/`、`pdf_ingest/`、`graphrag_pipeline/`
- 目标：在不重构现有 Python 主链路的前提下，让 `backend/ckqa-back` 成为一期统一业务入口，打通“解析 -> 导出 -> 建索引 -> 问答”的最小后端编排闭环

## 1. 背景

当前仓库的真实业务成熟度已经比较明确：

1. `pdf_ingest/` 是 PDF 解析与标准化导出的主链路，入口集中在 `scripts/pdf_processor/mineru_parser.py`。
2. `graphrag_pipeline/` 是知识图谱索引与问答的主链路，索引入口依赖 `utils/fetch_from_minio.py` 与 `graphrag index`，问答入口依赖 `utils/main.py` 提供的 FastAPI 服务。
3. `backend/ckqa-back/` 已经具备 Spring Boot 4、MyBatis-Plus、统一 API 规范、全局异常处理和基础示例接口，但尚未承接真正的跨模块业务编排。

这意味着当前最稳妥的推进方向，不是把 Python 主链路重写到 Java，或把 Python 深度嵌入 Java 进程，而是让 Java 后端先承担统一编排层职责：

1. 提供统一 REST API
2. 做参数校验、资源校验和状态编排
3. 触发现有 Python CLI / HTTP 能力
4. 聚合数据库状态给调用方

## 2. 设计目标与边界

## 2.1 设计目标

本次设计只追求“一期最小闭环 + 低风险接入”，优先达成以下能力：

1. Java 后端能基于已有 `pdf_files` 记录触发 PDF 解析。
2. Java 后端能基于解析完成的 PDF 触发 GraphRAG 输入导出。
3. Java 后端能为某个知识库触发一次索引构建，并把运行状态落到 `index_runs`。
4. Java 后端能代理 GraphRAG 问答请求，并把会话、消息和检索日志落到数据库。
5. 提供一组最小可用的查询与健康检查接口，用于联调和排障。

## 2.2 非目标

本次明确不做以下内容：

1. 不重写 `pdf_ingest` 或 `graphrag_pipeline` 为 Java 原生实现。
2. 不把 Python 逻辑深度嵌入 Java 运行时。
3. 不在一期引入消息队列、作业中心或复杂调度系统。
4. 不重做 Java 版 PDF 上传到 MinIO 的完整上传链路。
5. 不实现前端接入、权限细分、SSE 流式问答透传或复杂检索命中分析。

## 3. 方案选型

## 3.1 方案 A：Java 编排层 + Python CLI / HTTP 协同

这是本次采用的方案。

### 方案描述

1. `backend/ckqa-back` 作为统一业务入口。
2. `pdf_ingest` 继续作为本机 CLI 执行器，由 Java 通过子进程调用。
3. `graphrag_pipeline` 的索引流程继续通过本机 CLI 执行。
4. `graphrag_pipeline` 的问答流程继续通过现有 FastAPI 提供 HTTP 能力，由 Java 代理调用。

### 选择原因

1. 改动最小，不破坏现有已跑通的 Python 主链路。
2. Java 后端可以快速补齐统一 API 和状态编排职责。
3. 两套 Python 环境 `courseKg` 与 `graphrag-oneapi` 可继续独立维护，避免过早合并运行时。
4. 出错定位更直接，便于一期快速联调。

## 3.2 方案 B：Java 薄网关，更多业务仍留在 Python

### 不采用原因

1. Java 后端会退化成路由转发层，无法真正承接统一业务入口的定位。
2. 后续会话管理、索引任务管理、权限和审计仍然难以统一。

## 3.3 方案 C：Python 能力深度内嵌到 Java

### 不采用原因

1. 会显著增加运行时复杂度和环境耦合。
2. 需要重构现有 CLI/服务边界，一期成本过高。
3. 与“最小可运行、最低复杂度”的目标不符。

## 4. 一期总体架构

## 4.1 模块边界

### `backend/ckqa-back`

角色：统一业务编排层。

职责：

1. 提供 `/api/v1` REST API。
2. 做路径参数、请求体和资源存在性校验。
3. 调用本机 Python CLI 或 GraphRAG FastAPI。
4. 在数据库中维护会话、索引运行和检索日志等业务状态。
5. 向调用方返回统一响应结构。

### `pdf_ingest`

角色：解析与导出执行器。

职责：

1. 基于已有 `pdf_files` 记录执行解析。
2. 将解析结果、导出结果继续写回 MinIO、MySQL 和 `parse_results`。
3. 继续作为解析主状态源。

### `graphrag_pipeline`

角色：索引执行器与问答服务。

职责：

1. 拉取 GraphRAG 输入并执行索引。
2. 通过 FastAPI 提供 OpenAI 兼容问答接口。
3. 保持现有 `utils/main.py`、`fetch_from_minio.py` 和 CLI 工作流不变。

### `MySQL`

角色：统一结构化状态源。

职责：

1. 保存 `pdf_files`、`parse_results`、`knowledge_bases`、`index_runs`、`qa_sessions`、`qa_messages`、`qa_retrieval_logs` 等结构化事实。
2. 作为 Java 后端查询与聚合状态的主依据。

## 4.2 Java 内部新增编排边界

一期建议只新增以下 4 类能力，不大改现有分层：

1. `ProcessRunner`
   统一执行本机外部命令，负责命令组装、工作目录、环境变量、超时、中止、stdout/stderr 收集。
2. `PdfIngestOrchestrator`
   统一封装 `mineru_parser.py` 的 `parse`、`export-graphrag` 等调用。
3. `GraphRagIndexOrchestrator`
   统一封装 `fetch_from_minio.py` 与 `graphrag index`。
4. `GraphRagQueryClient`
   统一调用 `graphrag_pipeline` FastAPI `/v1/chat/completions`。

控制器、服务和编排层之间保持如下关系：

```text
Controller
  -> 业务 Service
      -> Orchestrator / QueryClient / Mapper / ServiceImpl
          -> Python CLI / HTTP / MySQL
```

## 5. 一期 API 设计

## 5.1 PDF 解析相关接口

### `POST /api/v1/pdf-files/{id}/parse`

作用：按已有 `pdf_file_id` 触发 PDF 解析。

执行前约束：

1. `pdf_file_id` 必须存在。
2. `parse_status` 仅允许为 `pending` 或 `failed`。
3. 当状态为 `processing` 或 `done` 时返回 `409`。

### `POST /api/v1/pdf-files/{id}/export-graphrag`

作用：触发 GraphRAG 输入导出。

执行前约束：

1. `pdf_file_id` 必须存在。
2. `parse_status` 必须为 `done`。

默认行为：

1. 默认导出 `section` 模式。
2. 可通过请求参数决定是否附带 `page_docs`。

### `GET /api/v1/pdf-files/{id}`

作用：查询 PDF 文件当前状态。

返回重点：

1. 基本文件信息
2. `parse_status`
3. `parse_started_at`
4. `parse_finished_at`
5. `parse_error_msg`

### `GET /api/v1/pdf-files/{id}/results`

作用：查询该 PDF 对应的 `parse_results` 记录，用于确认解析与导出产物。

## 5.2 索引相关接口

### `POST /api/v1/knowledge-bases/{id}/index-runs`

作用：为某个知识库发起一次建索引。

执行前约束：

1. `knowledge_base_id` 必须存在。
2. 同一 `knowledge_base_id` 不允许同时存在 `running` 状态的索引任务。

执行行为：

1. 先在 `index_runs` 中插入一条初始记录。
2. 调用 `fetch_from_minio.py` 拉取输入。
3. 调用 `graphrag index --root .` 构建索引。
4. 更新 `index_runs` 的最终状态。

### `GET /api/v1/index-runs/{id}`

作用：查询单个索引任务状态。

返回重点：

1. `status`
2. `engine`
3. `index_version`
4. `started_at`
5. `finished_at`
6. `run_metadata`

### `GET /api/v1/knowledge-bases/{id}/index-runs`

作用：查询某个知识库的索引历史。

一期要求：

1. 支持按创建时间倒序返回最近若干条记录。
2. 不要求一期实现复杂分页和筛选。

## 5.3 问答相关接口

### `POST /api/v1/qa-sessions`

作用：创建问答会话。

请求建议字段：

1. `userId`
2. `courseId`
3. `knowledgeBaseId`
4. `title`

执行前约束：

1. `userId` 必须存在。
2. `knowledgeBaseId` 如存在，则必须存在于 `knowledge_bases`。

### `POST /api/v1/qa-sessions/{id}/messages`

作用：向某个会话提交用户问题，并由 Java 代理 GraphRAG 问答。

执行前约束：

1. `qa_session_id` 必须存在。
2. 会话状态必须为 `active`。
3. 会话关联知识库必须存在至少一条可用索引记录。

执行行为：

1. 先写入一条 `role=user` 的 `qa_messages`。
2. 调用 GraphRAG FastAPI 获取回答。
3. 成功时写入一条 `role=assistant` 的 `qa_messages`。
4. 同时写入一条 `qa_retrieval_logs`。

### `GET /api/v1/qa-sessions/{id}`

作用：查询单个会话基本信息。

### `GET /api/v1/qa-sessions/{id}/messages`

作用：查询某个会话的消息列表。

一期要求：

1. 按 `sequence_no` 正序返回。
2. 不要求一期实现复杂过滤。

## 5.4 资源列表与健康检查接口

### `GET /api/v1/courses/{courseId}/pdf-files`

作用：按课程列出已有 PDF 文件，作为解析操作入口。

### `GET /api/v1/courses/{courseId}/knowledge-bases`

作用：按课程列出知识库，作为索引与问答操作入口。

### `GET /api/v1/system/health`

作用：提供统一健康检查视图。

一期检查项：

1. Java 服务自身是否可用
2. MySQL 是否可访问
3. GraphRAG FastAPI 是否可访问
4. `pdf_ingest` Python 路径与目录是否存在
5. `graphrag_pipeline` Python 路径与目录是否存在

## 6. 一期端到端数据流

## 6.1 解析

```text
POST /api/v1/pdf-files/{id}/parse
  -> Java 校验 pdf_file_id 与 parse_status
  -> PdfIngestOrchestrator 调用 mineru_parser.py parse ...
  -> pdf_ingest 更新 pdf_files / parse_logs / parse_results
  -> Java 返回执行结果与最新状态
```

一期约束：

1. Java 不重复维护解析任务表。
2. `pdf_files` 与 `parse_results` 继续作为解析链路主状态源。

## 6.2 导出

```text
POST /api/v1/pdf-files/{id}/export-graphrag
  -> Java 校验 parse_status=done
  -> PdfIngestOrchestrator 调用 mineru_parser.py export-graphrag ...
  -> pdf_ingest 写回导出产物和 parse_results
  -> Java 返回执行结果与产物摘要
```

## 6.3 建索引

```text
POST /api/v1/knowledge-bases/{id}/index-runs
  -> Java 创建 index_runs 初始记录
  -> GraphRagIndexOrchestrator 调用 fetch_from_minio.py
  -> GraphRagIndexOrchestrator 调用 graphrag index --root .
  -> Java 更新 index_runs.status / started_at / finished_at / run_metadata
  -> 可选补写 index_artifacts
```

一期规定：

1. `index_runs` 是索引执行过程的唯一业务状态源。
2. 一期先允许 `index_artifacts` 仅做最小记录，不要求完整穷举所有产物。

## 6.4 问答

```text
POST /api/v1/qa-sessions/{id}/messages
  -> Java 写入 user message
  -> GraphRagQueryClient 调用 /v1/chat/completions
  -> 成功时写入 assistant message
  -> 写入 qa_retrieval_logs
  -> Java 返回本轮问答结果
```

一期规定：

1. `qa_messages` 负责保留完整会话内容。
2. `qa_retrieval_logs` 负责记录查询模式、查询文本、检索状态和错误摘要。
3. `qa_retrieval_hits` 暂不作为一期必交项。

## 7. 错误处理与状态策略

## 7.1 错误分类

一期统一按 4 类错误处理：

1. 参数或资源错误
   例如：资源不存在、状态不允许当前操作、会话不处于 `active`
2. 配置或环境错误
   例如：Python 路径缺失、脚本目录不存在、GraphRAG API 地址缺失
3. 外部执行错误
   例如：CLI 非 0 退出、FastAPI 返回 5xx、返回结构不符合预期
4. 超时错误
   例如：解析、索引或问答代理超时

## 7.2 API 返回策略

1. 参数或资源错误返回 `404` 或 `409`
2. 配置、环境或外部执行错误返回 `500`
3. 所有错误均复用现有统一返回体与全局异常处理机制
4. 不将完整 stderr 或敏感配置直接透传给前端

## 7.3 数据库状态写回策略

### `pdf_files`

1. 解析状态仍以 Python 流程写回的 `parse_status` 为准
2. Java 不额外维护平行的解析状态表

### `index_runs`

1. 初始状态写为 `pending`
2. 命令真正开始执行时更新为 `running`
3. 完成后更新为 `success` 或 `failed`
4. `run_metadata` 用于保存命令摘要、耗时和失败简述

### `qa_retrieval_logs`

1. 问答成功时写 `retrieval_status=success`
2. 问答超时或调用失败时写 `retrieval_status=failed`
3. 错误摘要写入 `error_message`

## 7.4 事务边界

一期不允许用长事务包裹外部命令。

采用以下原则：

1. 调外部命令前，用短事务写入初始状态
2. 外部命令执行过程中不持有数据库事务
3. 成功或失败后，再用短事务写回最终状态

这样可以降低锁持有时间，并避免长时间外部调用影响数据库事务稳定性。

## 8. 超时策略

一期默认采用同步调用，但必须设置明确超时。

建议值如下：

1. `parse`：15 分钟
2. `export-graphrag`：5 分钟
3. `fetch_from_minio`：5 分钟
4. `graphrag index`：30 分钟
5. GraphRAG FastAPI 连接超时：3 秒
6. GraphRAG FastAPI 读取超时：120 秒
7. `system/health` 外部探测超时：3 秒

同步执行的代价是接口响应时间偏长，但这是一期为尽快打通业务闭环所接受的权衡。后续若需要异步任务化，应复用本设计中的编排边界，不重写 Controller 接口语义。

## 9. 配置设计

一期不依赖 `conda activate`，而是显式配置两个 Python 解释器绝对路径。

建议统一配置前缀：

```text
ckqa.integration.*
```

建议配置项：

1. `ckqa.integration.pdf-ingest.python`
2. `ckqa.integration.pdf-ingest.root`
3. `ckqa.integration.graphrag.python`
4. `ckqa.integration.graphrag.root`
5. `ckqa.integration.graphrag.api-base-url`
6. `ckqa.integration.timeout.parse-seconds`
7. `ckqa.integration.timeout.export-seconds`
8. `ckqa.integration.timeout.fetch-seconds`
9. `ckqa.integration.timeout.index-seconds`
10. `ckqa.integration.timeout.query-seconds`

推荐命令调用形式：

### `pdf_ingest`

```bash
<pdf_python> scripts/pdf_processor/mineru_parser.py ...
```

### `graphrag_pipeline`

```bash
<graphrag_python> utils/fetch_from_minio.py ...
<graphrag_python> -m graphrag index --root .
```

这样可以避免依赖 shell 初始化逻辑，也更容易在健康检查中提前发现配置问题。

## 10. 一期测试与验收

## 10.1 单元测试

覆盖重点：

1. `ProcessRunner` 的成功、非 0 退出、超时和命令不存在场景
2. `PdfIngestOrchestrator` 的命令组装与状态校验
3. `GraphRagIndexOrchestrator` 的状态流转与失败分支
4. `GraphRagQueryClient` 的成功、超时和 5xx 处理

## 10.2 WebMvc 测试

覆盖重点：

1. Controller 参数校验
2. `404 / 409 / 500` 错误映射
3. 统一返回体结构

## 10.3 最小联调验证

联调路径固定为：

1. 启动 MySQL
2. 启动 `graphrag_pipeline/utils/main.py`
3. 启动 `backend/ckqa-back`
4. 调用 `GET /api/v1/system/health`
5. 调用 `POST /api/v1/pdf-files/{id}/parse`
6. 调用 `POST /api/v1/pdf-files/{id}/export-graphrag`
7. 调用 `POST /api/v1/knowledge-bases/{id}/index-runs`
8. 调用 `POST /api/v1/qa-sessions`
9. 调用 `POST /api/v1/qa-sessions/{id}/messages`

## 10.4 一期验收标准

满足以下 4 条即可判定一期编排闭环完成：

1. Java 后端能成功触发一次 PDF 解析，或在失败时返回明确原因。
2. Java 后端能成功触发一次索引构建，并正确写入 `index_runs` 状态。
3. Java 后端能成功代理一次问答，并把消息与检索日志写入数据库。
4. 任一失败场景都能在 API 返回、数据库状态和服务日志中定位到问题。

## 11. 已知遗留与后续演进方向

一期完成后，仍然存在以下已知遗留：

1. 长任务仍采用同步接口，吞吐与用户体验有限。
2. 没有任务重试、队列调度和统一 trace id。
3. `qa_retrieval_hits` 仍未落地。
4. Java 侧尚未承接文件上传能力，仍依赖已有 `pdf_files` 记录作为起点。

后续推荐的第二阶段演进顺序：

1. 将 `parse` 与 `index` 从同步执行升级为异步任务执行。
2. 为 `index_artifacts` 和 `qa_retrieval_hits` 补齐更完整的产物与命中记录。
3. 增加鉴权、课程访问控制和审计链路。
4. 视部署复杂度再决定是否将 `pdf_ingest` 也包装为稳定服务，而不是长期保留 CLI 触发模式。
