# 管理端真实数据接入设计稿

- 日期：2026-04-28
- 范围：`frontend/apps/admin-app/`、`backend/ckqa-back/`
- 目标：从 `/app/health` 与 `/app/courses` 开始，把管理端 mock 页面逐步切到 Java `/api/v1`，并优先完成知识库构建流程页面与后端的真实衔接。
- 边界：前端正式业务请求只访问 Java `/api/v1`；不直接请求 GraphRAG Python `/v1`；本稿不覆盖正式登录、资料上传 UI、复杂 RBAC 编辑器和全量审计页面。
- 修订说明：2026-04-28 根据评审意见补充同步长任务兜底、路由参数语义、多资料边界、区块级错误、分页、QA 轮询、`force`、CORS、幂等性、错误码和 DTO 分层约束。

---

## 1. 当前事实

### 1.1 前端事实

`frontend/apps/admin-app/` 已经具备可接真实数据的骨架：

1. `src/axios/index.js` 默认指向 `http://127.0.0.1:8080/api/v1`，并保留认证头注入和错误收敛。
2. `/app/health` 的 `HealthView.vue` 已调用 `GET /system/health`，页面上 `DataSourceChip` 已使用 `source="live"`。
3. `/app/courses`、课程详情、资料详情、知识库列表、知识库详情、构建向导、索引运行详情等仍由 `src/views/pages/module-content.js` 提供 mock 行和 mock 流程。
4. `ModulePage.vue` 目前只消费静态配置，不具备按路由加载 API、处理 loading/error/empty、刷新 `DataSourceChip` 的能力。
5. `DataSourceChip` 已支持 `mock`、`live`、`skeleton`、`comingSoon` 四种语义，当前不需要新增 `partialLive` 之类的新来源类型。

### 1.2 后端事实

`backend/ckqa-back/` 已经提供一部分知识库构建闭环所需接口：

| 能力 | 已有接口 | 现状 |
| --- | --- | --- |
| 系统健康 | `GET /api/v1/system/health` | 可直接用于 `/app/health` |
| 课程下资料 | `GET /api/v1/courses/{courseId}/pdf-files` | 可用于课程详情和构建向导资料选择 |
| 课程下知识库 | `GET /api/v1/courses/{courseId}/knowledge-bases` | 可用于课程详情 |
| 资料详情 | `GET /api/v1/pdf-files/{id}` | 内部已按 `course_materials` 读取 |
| 解析结果 | `GET /api/v1/pdf-files/{id}/results` | 可用于解析产物页 |
| 触发解析 | `POST /api/v1/pdf-files/{id}/parse` | 同步长任务，前端需显示执行中 |
| 导出 GraphRAG 输入 | `POST /api/v1/pdf-files/{id}/export-graphrag` | 默认 `mode=section`，可带 `withPageDocs` 与 `force` |
| 创建索引运行 | `POST /api/v1/knowledge-bases/{id}/index-runs` | 同步长任务，成功后后端自动更新 `activeIndexRunId` |
| 索引运行列表 | `GET /api/v1/knowledge-bases/{id}/index-runs` | 可用于知识库详情和构建向导日志区 |
| 索引运行详情 | `GET /api/v1/index-runs/{id}` | 可用于索引运行详情 |
| 冒烟问答 | `POST /api/v1/qa-sessions`、`POST /api/v1/qa-sessions/{id}/messages`、`GET /api/v1/qa-sessions/{sessionId}/tasks/{taskId}` | 可作为构建后验证 |

当前缺口是：`/app/courses` 需要课程列表聚合数据，但后端还没有 `GET /api/v1/courses`；知识库列表页和知识库详情页也缺少直接读取 `knowledge_bases` 的列表/详情接口。

---

## 2. 推荐方案

### 2.1 方案对比

| 方案 | 做法 | 优点 | 风险 |
| --- | --- | --- | --- |
| A. 只用已有接口 | 前端保留课程 ID 配置，再逐个请求课程资料和知识库 | 后端改动少 | `/app/courses` 仍有硬编码课程入口，不算真正 live |
| B. 最小 Java 聚合读接口 + 复用已有工作流接口 | 补 `GET /courses`、`GET /courses/{courseId}`、`GET /knowledge-bases`、`GET /knowledge-bases/{id}`，操作仍复用现有 parse/export/index/qa 接口 | 最小可运行，能让核心页面真实落地 | 需要补少量 DTO、Service 和 WebMvc 测试 |
| C. 一次性做完整 Admin BFF | 为所有页面设计专用聚合接口 | 页面消费最舒服 | 超过当前需求，容易拖慢核心闭环 |

推荐采用 **方案 B**。它能让 `/app/courses` 与知识库构建主流程先变成真实数据页面，同时不把后端扩展成新的大 BFF。

### 2.2 接入顺序

1. 先稳住 `/app/health`：保持 Java health live，补自动加载、刷新时间、错误/空态一致性。
2. 接 `/app/courses`：新增课程列表聚合接口，前端表格从 mock rows 切到 API rows，`DataSourceChip` 改成 `live`。
3. 接课程详情和资料详情：从课程进入资料列表、知识库列表、资料详情、解析结果。
4. 接知识库构建流程：资料选择、解析检查、导出 GraphRAG 输入、创建索引并确认自动激活、问答冒烟验证。
5. 再逐步扩展知识库列表、知识库详情、索引运行详情和问答运维列表。

---

## 3. 最小后端接口设计

### 3.1 课程列表

```http
GET /api/v1/courses?page=1&size=20&keyword=os&status=active
```

返回仍使用统一 `ApiResponse<ApiPageData<CourseSummaryResponse>>`。`CourseSummaryResponse` 只用于列表轻量摘要，不承担详情页扩展字段：

```json
{
  "code": 200,
  "message": "操作成功",
  "data": {
    "items": [
      {
        "id": 1,
        "courseId": "os",
        "courseName": "操作系统",
        "description": "操作系统课程资料知识库",
        "status": "active",
        "accessPolicy": "course_member",
        "materialCount": 3,
        "parsedMaterialCount": 2,
        "failedMaterialCount": 0,
        "knowledgeBaseCount": 1,
        "activeKnowledgeBaseCount": 1,
        "latestIndexRunId": 18,
        "latestIndexRunStatus": "success",
        "updatedAt": "2026-04-28T10:30:00"
      }
    ],
    "page": 1,
    "size": 20,
    "total": 1,
    "pages": 1
  },
  "timestamp": "2026-04-28T10:31:00"
}
```

实现建议：

1. 在 `CoursesController` 增加 `GET` 根路径和 `GET /{courseId}`。
2. 新增 `CourseSummaryResponse`、`CourseDetailResponse` 与 `CourseQueryRequest`。
3. 聚合字段优先从 `courses`、`course_materials`、`knowledge_bases`、`index_runs` 计算。
4. 首版可以用 Service 层查询后内存聚合，课程量变大后再下沉到 Mapper SQL。

路由参数约定：

1. 前端路由 `/app/courses/:courseId` 和后端路径 `/api/v1/courses/{courseId}` 始终使用字符串业务 ID，例如 `os`。
2. `CourseSummaryResponse.id` 和 `CourseDetailResponse.id` 是数据库内部主键，只作为调试、表格辅助字段和后续管理接口入参保留，不作为课程详情路由参数。
3. `course_materials`、`knowledge_bases`、`qa_sessions`、`course_memberships` 等表已经通过字符串 `course_id` 关联课程；知识库、资料、索引运行等资源详情路由继续使用各自数字主键。
4. 前端从课程列表进入详情时使用 `row.courseId`，不要使用 `row.id`。

### 3.2 课程详情

```http
GET /api/v1/courses/{courseId}
```

返回 `CourseDetailResponse`。首版字段可以包含 `CourseSummaryResponse` 的全部字段，并额外保留详情页可扩展字段，例如完整 `description`、访问策略说明、课程成员统计或后续权限摘要；列表接口不因详情页扩展而变重。详情页再并行请求：

```http
GET /api/v1/courses/{courseId}/pdf-files
GET /api/v1/courses/{courseId}/knowledge-bases
```

这样课程详情页不需要等待一个大型详情接口，后续扩展成员、问答会话、日志也更自然。

### 3.3 知识库列表与详情

```http
GET /api/v1/knowledge-bases?page=1&size=20&courseId=os&status=active
GET /api/v1/knowledge-bases/{id}
```

列表和详情 DTO 同样分离，避免详情页扩展字段拖重列表接口：

| DTO | 用途 |
| --- | --- |
| `KnowledgeBaseSummaryResponse` | `GET /api/v1/knowledge-bases` 列表项，保留列表筛选和表格展示所需字段 |
| `KnowledgeBaseDetailResponse` | `GET /api/v1/knowledge-bases/{id}` 详情，首版可包含 Summary 全部字段，并为后续索引运行历史摘要、文档映射统计、成员/权限摘要预留扩展 |

`KnowledgeBaseSummaryResponse` 建议字段：

| 字段 | 说明 |
| --- | --- |
| `id` | 知识库主键 |
| `courseId` | 所属课程 ID |
| `kbCode` | 知识库编码 |
| `name` | 知识库名称 |
| `status` | `draft` / `active` / `archived` |
| `activeIndexRunId` | 当前激活索引运行 ID |
| `description` | 说明 |
| `latestIndexRunId` | 最近索引运行 ID |
| `latestIndexRunStatus` | 最近索引运行状态 |
| `createdAt` / `updatedAt` | 时间字段 |

首版不新增“手动激活索引”接口，因为现有 `IndexWorkflowService.createIndexRun()` 在索引成功后已经自动写入 `activeIndexRunId`。构建向导不保留独立激活步骤，而是在索引创建步骤的成功态中回读并展示 `activeIndexRunId`。

---

## 4. 前端架构设计

### 4.1 请求层

在 `src/api/` 下增加轻量 API 模块，避免页面里散落字符串路径：

| 文件 | 职责 |
| --- | --- |
| `src/api/client.js` | `unwrapApiResponse()`、分页结构归一、统一错误对象 |
| `src/api/system.js` | `getSystemHealth()` |
| `src/api/courses.js` | `listCourses()`、`getCourse()`、`listCourseMaterials()`、`listCourseKnowledgeBases()` |
| `src/api/materials.js` | `getMaterial()`、`listParseResults()`、`startParse()`、`exportGraphRag()` |
| `src/api/knowledge-bases.js` | `listKnowledgeBases()`、`getKnowledgeBase()`、`listIndexRuns()`、`createIndexRun()`、`getIndexRun()` |
| `src/api/qa.js` | 构建后冒烟验证所需的会话、消息、任务轮询接口 |

`axios/index.js` 继续只负责底层客户端，业务解包和字段归一放到 `src/api/`，这样 `HealthView.vue` 也能从手写 `response.data?.data` 迁移到统一工具。

业务错误码处理：

下表中的 `4093`-`4097` 是当前后端 `ApiResultCode` 已有枚举；Phase 2 联调前仍需用 WebMvc 测试确认对应接口实际返回这些业务码和匹配的 HTTP 状态。`401` / `403` 属于认证接入后的前端策略，当前开发态登录阶段可先保留路由兜底。

| HTTP / 业务场景 | 前端处理 |
| --- | --- |
| HTTP `401` | 跳转开发态登录页；保留当前 URL 作为后续返回目标 |
| HTTP `403` | 跳转或局部显示无权限状态，不自动重试 |
| HTTP `404` 或业务码 `4044`-`4048` | 主资源不存在时回列表页并提示；子资源不存在时显示区块级空/错误状态 |
| 业务码 `4093` 解析状态冲突 | 重新拉取 `GET /pdf-files/{id}`，按最新 `parseStatus` 更新按钮，不立即重复 POST |
| 业务码 `4094` 导出任务锁定 | 进入导出确认中，轮询 `GET /pdf-files/{id}/results` |
| 业务码 `4095` 索引任务运行中 | 进入索引确认中，轮询最近索引运行 |
| 业务码 `4096` 问答会话关闭 | 停止发送消息，提示重新创建会话 |
| 业务码 `4097` 知识库未 ready | 阻塞问答冒烟步骤，引导先完成索引 |
| `4000` / `4001` | 显示参数或校验错误，不自动重试 |
| `5000` / `5003` / `5004` | 通用错误面板 + 重试或查看日志入口 |

### 4.2 页面数据模型

`ModulePage.vue` 从“纯静态配置渲染器”升级为“配置 + 数据加载器”：

1. `module-content.js` 保留页面标题、按钮文案、列定义、步骤定义等结构配置。
2. 新增 `module-loaders.js`，按 `route.name` 返回数据加载函数。
3. 页面进入时根据路由和 loader 请求真实数据。
4. loader 返回统一结构：

```js
{
  source: 'live',
  refreshedAt: '2026-04-28T10:31:00.000Z',
  requestState: 'success',
  rows: [],
  pagination: { page: 1, size: 20, total: 0, pages: 0 },
  facts: [],
  workflowSteps: [],
  blocks: {},
  raw: {}
}
```

5. `pagination` 只在 table 页面必填；overview / workflow 页面可返回 `null`。
6. `blocks` 用于详情页局部状态，例如 `course`、`materials`、`knowledgeBases` 各自维护 `loading/error/success/empty`，避免一个子区块失败拖垮整页。
7. 尚未接入的页面继续使用 `module-content.js` 的 mock 配置，`DataSourceChip` 保持 `mock`，不伪装成 live。

### 4.3 `DataSourceChip` 语义

| 页面状态 | `source` | 展示 |
| --- | --- | --- |
| 已走 Java API，最近一次请求成功 | `live` | 实时数据，显示刷新时间 |
| 已走 Java API，但正在加载 | `live` | 实时数据，不显示刷新时间或保留上次刷新时间 |
| 已走 Java API，但请求失败 | `live` | 实时数据，同时显示页面错误面板 |
| 尚未接接口 | `mock` | 示例数据 |
| 路由已存在但页面未开放 | `comingSoon` | 未开放 |
| 纯骨架/空实现 | `skeleton` | 页面骨架 |

用户这次明确要求“把 `DataSourceChip` 改成 live”，具体落地规则是：只要页面的数据来源已经改为 Java `/api/v1`，就把该页面的 chip 改为 `live`；未接接口的页面继续保留 `mock`，避免误导。

### 4.4 操作按钮与空态文案

首版没有后端能力支撑的主操作不渲染为普通可点击按钮：

| 操作 | 当前处理 | 文案 |
| --- | --- | --- |
| 新建课程 | 禁用按钮 + title/tooltip | “课程创建接口未开放，当前仅支持读取已有课程。” |
| 新建知识库 | 禁用按钮 + title/tooltip | “知识库创建接口未开放，当前请使用已有知识库完成构建联调。” |
| 上传资料 | 不在页面主操作中展示 | 等 Java 上传链路确认后再开放 |

空数据文案分环境：

1. 开发态可显示“暂无课程。请确认 MySQL 已导入测试数据，或后端已提供课程创建接口。”
2. 生产态显示“暂无课程。请联系管理员创建课程后再进行资料与知识库维护。”
3. 文案由前端按 `import.meta.env.DEV` 或后续运行时配置选择，不把开发诊断文案暴露给正式用户。

### 4.5 CORS 与本地代理

本地开发默认前端端口与 Java 端口不同，必须明确跨域方案：

1. 优先方案是在 Java 后端增加受控 CORS 配置，只允许 `http://127.0.0.1:*`、`http://localhost:*` 等本地开发来源，生产环境按部署域名收紧。
2. 备选方案是在 `frontend/apps/admin-app/vite.config.js` 配置 `/api/v1` 代理，并把 `VITE_API_BASE_URL` 改为 `/api/v1`。
3. 文档和 `.env.example` 应同步说明两种方式只能选一种作为默认，避免同一环境同时打开 CORS 与代理造成路径判断混乱。

### 4.6 长任务状态模块

`src/views/pages/long-task-state.js` 是纯函数 + composable 风格的共享模块，避免各页面各写一套轮询逻辑：

| 导出 | 职责 |
| --- | --- |
| `LONG_TASK_LIMITS` | 解析、导出、索引的前端轮询上限和间隔常量 |
| `createLongTaskController(options)` | 创建可取消的轮询控制器，内部持有 `AbortController` 与 timer |
| `resolveLongTaskState(snapshot)` | 把资源快照归一为 `running/success/failed/unknown` |
| `shouldStartFallback(error)` | 判断 Axios timeout、网络错误、504 是否需要进入资源轮询 |

调用边界：

1. `module-loaders.js` 只负责初始数据加载，不直接执行长任务。
2. `ModulePage.vue` 或后续拆出的 workflow 组件在用户点击解析/导出/索引按钮时调用 `createLongTaskController()`。
3. 控制器只关心“触发函数、轮询函数、成功判断、失败判断、上限时间”，不直接依赖具体页面 DOM。
4. 组件卸载时必须调用控制器 `cancel()`。

---

## 5. 页面设计

### 5.1 `/app/health`

目标：把已经 live 的健康页打磨成稳定联调入口。

数据源：

```http
GET /api/v1/system/health
```

页面行为：

1. 页面挂载后自动请求一次健康检查。
2. 保留“刷新健康”按钮，成功后更新 `refreshedAt`。
3. `DataSourceChip` 固定 `live`。
4. `HealthMatrix` 继续区分 `reachable` 与 `ready`。
5. 请求失败时展示错误面板，保留诊断日志区域，不清空上一次成功结果。
6. 原始 JSON 继续折叠展示，作为联调排障入口。

验收重点：

1. 后端未启动时显示 Java API 不可达错误。
2. 后端启动但 GraphRAG 未 ready 时，页面显示 degraded，不误判为 failed。
3. 健康页不再需要手动点击后才知道是否能连通。

### 5.2 `/app/courses`

目标：让课程列表成为第一个真正的业务 live 页面。

数据源：

```http
GET /api/v1/courses?page=1&size=20&keyword=&status=
```

表格列建议：

| 列 | 字段 | 说明 |
| --- | --- | --- |
| 课程 | `courseName` + `courseId` | 主入口，点击进入详情 |
| 状态 | `status` | active / archived 等 |
| 资料 | `materialCount`、`parsedMaterialCount`、`failedMaterialCount` | 体现解析进度 |
| 知识库 | `knowledgeBaseCount`、`activeKnowledgeBaseCount` | 体现构建基础 |
| 最近索引 | `latestIndexRunStatus`、`latestIndexRunId` | 体现是否可问答 |
| 更新时间 | `updatedAt` | 排查数据新旧 |

页面行为：

1. 首次进入自动加载课程列表。
2. `DataSourceChip` 使用 `live`。
3. 状态筛选和关键词搜索走查询参数；如果后端暂未支持复杂筛选，前端只传 `status` 和 `keyword`。
4. “新建课程”按钮首版显示为禁用或即将开放，因为后端当前没有课程创建接口。
5. 空数据时按第 4.4 节的开发态/生产态文案展示，不再显示 mock 课程。
6. 行点击进入 `/app/courses/:courseId`。

### 5.3 `/app/courses/:courseId`

目标：作为课程资料和知识库构建的真实入口。

数据源：

```http
GET /api/v1/courses/{courseId}
GET /api/v1/courses/{courseId}/pdf-files
GET /api/v1/courses/{courseId}/knowledge-bases
```

页面结构：

1. 顶部摘要：课程名、课程 ID、状态、资料数、知识库数、最近索引状态。
2. 资料区：列出资料 ID、文件名、解析状态、解析时间、错误摘要。
3. 知识库区：列出知识库 ID、名称、状态、激活索引 ID、最近索引状态。
4. 操作入口：
   - 资料行进入 `/app/materials/:materialId`。
   - 知识库行进入 `/app/knowledge-bases/:kbId`。
   - 知识库行提供“进入构建向导”入口。

局部降级：

1. `GET /courses/{courseId}` 失败时整页进入 error，因为页面标题和权限上下文都无法确认。
2. 资料列表失败时仅资料区显示错误和局部重试按钮，知识库区仍可正常展示。
3. 知识库列表失败时仅知识库区显示错误和局部重试按钮，资料区仍可正常展示。
4. 局部区块成功但为空时展示空态，不把空数组当错误。

### 5.4 `/app/materials/:materialId`

目标：把资料生命周期从 mock 概览切成真实操作页。

数据源：

```http
GET /api/v1/pdf-files/{id}
GET /api/v1/pdf-files/{id}/results
POST /api/v1/pdf-files/{id}/parse
POST /api/v1/pdf-files/{id}/export-graphrag
```

页面行为：

1. `parseStatus=pending|failed` 时允许触发解析。
2. `parseStatus=processing` 时禁用解析按钮，并提示同步长任务正在执行。
3. `parseStatus=done` 时允许导出 GraphRAG 输入。
4. 导出默认请求体：

```json
{
  "mode": "section",
  "withPageDocs": true,
  "force": false
}
```

5. `force=false` 是默认安全模式；仅当资料已重新解析、已有导出产物过期，或用户明确选择“覆盖旧导出”时才发送 `force=true`。
6. `force=true` 必须有二次确认文案：“将覆盖该资料已有 GraphRAG 导出产物，可能影响后续索引输入。”
7. 解析结果区只做只读列表，不内置 JSON 编辑器。

GraphRAG 投影产物识别标准：

1. `GET /api/v1/pdf-files/{id}/results` 返回 `ParseResultResponse` 列表，前端必须按 `fileName` 判断 GraphRAG 产物，不用 `results.length > 0` 代替。
2. GraphRAG 产物文件名以 `graphrag_` 开头。
3. 完整 section 导出至少包含 `graphrag_normalized_docs.json` 与 `graphrag_section_docs.json`。
4. `withPageDocs=true` 时还必须包含 `graphrag_page_docs.json`。
5. page 模式完成条件是 `graphrag_normalized_docs.json` 与 `graphrag_page_docs.json`。
6. 该判断与后端 `ParseResultsService.hasCompleteGraphRagExport()` 保持一致。

### 5.5 `/app/knowledge-bases`

目标：让知识库列表成为构建流程的入口，而不是静态 mock 页。

数据源：

```http
GET /api/v1/knowledge-bases?page=1&size=20&courseId=&status=
```

页面行为：

1. 展示知识库、所属课程、状态、激活索引、最近运行。
2. “新建知识库”首版禁用或即将开放，因为当前设计优先真实构建闭环，不先做创建表单。
3. 行点击进入 `/app/knowledge-bases/:kbId`。
4. 如果 `activeIndexRunId` 不为空，显示为可问答；为空则提示需要先构建索引。

### 5.6 `/app/knowledge-bases/:kbId/build`

目标：用真实后端状态驱动知识库构建向导。

首版资料选择边界：

1. 当前后端 `POST /api/v1/pdf-files/{id}/export-graphrag` 是单资料接口，`GraphRagIndexOrchestrator.fetchInput()` 也按 `courseId --clean` 拉取输入；在课程下存在多份 GraphRAG 输入时，`fetch_from_minio.py` 会要求指定 `--material-id`。
2. 因此首版构建向导只支持选择一个“本次构建主资料”，不要在 UI 上提供多选。
3. 多资料构建作为后续能力，需要先补 Java 批量导出和 GraphRAG 输入合并/指定策略，不能在前端用多次单资料请求伪装成已支持。

步骤映射：

| 步骤 | 状态来源 | 动作接口 | 完成条件 |
| --- | --- | --- | --- |
| 选择课程资料 | `GET /courses/{courseId}/pdf-files` | 前端选择一个资料 | 已选择一个资料 |
| 解析状态检查 | `GET /pdf-files/{id}` | `POST /pdf-files/{id}/parse` | 选中资料 `parseStatus=done` |
| 导出 GraphRAG 输入 | `GET /pdf-files/{id}/results` | `POST /pdf-files/{id}/export-graphrag` | 返回成功，或结果列表出现 GraphRAG 投影产物 |
| 创建索引并确认激活 | `GET /knowledge-bases/{id}/index-runs`、`GET /knowledge-bases/{id}` | `POST /knowledge-bases/{id}/index-runs` | 返回 `status=success`，且知识库 `activeIndexRunId` 等于成功索引运行 ID |
| 问答冒烟验证 | QA 会话与任务接口 | 创建会话、发送消息、轮询任务 | 任务终态 `success` 且有 assistant 消息 |

重要约定：

1. 索引成功后后端已经自动激活，所以不再保留独立的“激活索引版本”步骤；在“创建索引并确认激活”步骤中显示“索引已构建并激活，ID: 18”。
2. 冒烟验证首版可以作为构建向导的最后一步；如果联调成本过高，可以先显示为可跳过步骤，但不能再用 mock 成功结果。
3. 每一步失败都展示 Java 返回的 `message`，并保留原始错误摘要入口。

多资料后续方案：

1. 如果要支持一次构建多个资料，推荐新增 `POST /api/v1/pdf-files/export-graphrag/batch`，请求体包含 `materialIds`、`mode`、`withPageDocs`、`force`。
2. 批量导出默认策略应为“并行执行、逐项记录、全成功才放行索引”；部分失败时展示成功/失败清单，不进入索引步骤。
3. Java 索引编排需同步支持 `materialIds` 或输入合并策略，否则即使批量导出成功，后续 `fetch_from_minio.py <courseId> --clean` 仍可能因多份输入失败。

---

## 6. 状态与错误处理

### 6.1 请求状态

每个 live 页面统一维护页面级状态：

| 状态 | 含义 | UI |
| --- | --- | --- |
| `idle` | 尚未发起请求 | 通常只在极短暂初始态出现 |
| `loading` | 正在请求 | 骨架屏或按钮 loading |
| `success` | 请求成功 | 渲染真实数据，更新刷新时间 |
| `empty` | 请求成功但无数据 | 展示空状态和下一步提示 |
| `error` | 请求失败 | 展示错误面板，保留重试按钮 |

详情页还需要维护区块级状态：

```js
{
  blocks: {
    course: { state: 'success', data: {} },
    materials: { state: 'error', error: {} },
    knowledgeBases: { state: 'success', data: [] }
  }
}
```

页面级 error 只用于主资源不可确认的情况，例如课程详情的 `GET /courses/{courseId}` 失败。资料列表、知识库列表、解析结果、索引运行列表等子资源失败时，只让对应区块进入 error，并保留局部重试按钮。

### 6.2 状态映射

复用 `status-model.js` 里的状态色：

1. `done`、`success`、`ready`、`reachable` 映射成功。
2. `processing`、`running`、`indexing` 映射运行中。
3. `pending`、`degraded`、`skipped` 映射警告。
4. `failed`、`error`、`timeout`、`unreachable` 映射危险。
5. 未知状态映射为 blocked，并显示原始状态字符串。

### 6.3 统一响应处理

前端只以 Java 统一响应的 `data` 作为业务数据。`unwrapApiResponse()` 不允许静默透传非标准响应：

1. 只有响应对象包含数值型 `code`、字符串 `message` 和 `data` 字段时，才返回 `data`。
2. 只有 `code === 200` 才视为业务成功；标准 envelope 但 `code !== 200` 时抛出业务错误对象，保留 `code`、`message`、`data` 和 HTTP `status`。
3. 如果后端返回 Spring 默认错误体、HTML 错误页、代理 504 或其他非 envelope 结构，抛出标准化错误对象：

```js
{
  message: '后端响应格式不符合 CKQA ApiResponse 契约',
  nonStandard: true,
  status,
  raw
}
```

4. 调用层只负责展示错误和重试入口，不把错误体当业务数据渲染。

### 6.4 同步长任务兜底

解析、导出和索引在当前 Java 后端仍是同步 HTTP 操作，但前端不能只依赖单个 HTTP 连接。所有长任务按钮统一采用“请求中 + 资源轮询兜底”的设计：

| 操作 | 触发接口 | 兜底轮询资源 | 超时后 UI |
| --- | --- | --- | --- |
| 解析资料 | `POST /pdf-files/{id}/parse` | `GET /pdf-files/{id}` | 显示“解析请求仍在确认中”，按 `parseStatus` 更新步骤 |
| 导出 GraphRAG | `POST /pdf-files/{id}/export-graphrag` | `GET /pdf-files/{id}/results` | 显示“导出请求仍在确认中”，轮询是否出现 GraphRAG 投影产物 |
| 创建索引 | `POST /knowledge-bases/{id}/index-runs` | `GET /knowledge-bases/{id}/index-runs` 和 `GET /knowledge-bases/{id}` | 显示“索引请求仍在确认中”，按最近运行与 `activeIndexRunId` 更新步骤 |

幂等性约定：

| 操作 | 当前后端行为 | 前端重试策略 | 后端后续要求 |
| --- | --- | --- | --- |
| 解析资料 | 只允许 `pending` / `failed` 抢占为 `processing`；其他状态返回 `PDF_PARSE_STATE_CONFLICT` | 只有 `parseStatus=failed` 时显示“重试解析”；`processing` / `done` 不重复 POST，只刷新详情 | 可进一步把 `processing` 返回当前状态作为幂等成功，但不是首版前提 |
| 导出 GraphRAG | `force=false` 且已有完整导出时直接返回成功；并发导出用 `pdf-export:{id}` 命名锁返回 `PDF_EXPORT_LOCKED` | `PDF_EXPORT_LOCKED` 时进入结果轮询；只有导出失败或用户确认覆盖时才允许再次 POST | 保持锁和完整产物判断；`force=true` 仍需二次确认 |
| 创建索引 | 同一知识库已有 running 任务时返回 `INDEX_RUN_ALREADY_RUNNING` | 进入索引运行列表轮询，不重复创建 | 后续如改异步任务，应返回可轮询的 `indexRunId` |

轮询阈值：

| 操作 | 前端轮询间隔 | 前端等待上限 | 来源 |
| --- | ---: | ---: | --- |
| 解析资料 | 10 秒 | 900 秒 | 对齐后端默认 `PARSE_TIMEOUT_SECONDS=900` |
| 导出 GraphRAG | 5 秒 | 300 秒 | 对齐后端默认 `EXPORT_TIMEOUT_SECONDS=300` |
| 创建索引 | 30 秒 | 1800 秒 | 对齐后端默认 `INDEX_TIMEOUT_SECONDS=1800` |

若后续后端提供运行时配置接口，前端应优先读取服务端返回值；在此之前，`LONG_TASK_LIMITS` 固化上述默认值，并在 `.env.example` 中注明这些值需与后端超时配置同步维护。

前端规则：

1. `VITE_API_TIMEOUT` 到期、浏览器网络中断、代理返回 504 时，不立即判定业务失败。
2. 如果能通过资源状态确认任务仍在 `processing` / `running`，页面转为“确认中”并继续低频轮询。
3. 如果资源状态进入 `failed`，展示失败原因和重试按钮。
4. 如果轮询超过资源对应后端超时阈值仍无变化，才提示用户刷新或查看日志。
5. 用户离开页面时取消前端轮询；再次进入页面时从资源状态恢复，而不是恢复旧的按钮 loading。
6. 对于索引，如果 `POST /index-runs` 超时且前端没有拿到新 `id`，使用 `GET /knowledge-bases/{id}/index-runs` 查最近运行；不要盲目重复创建，避免并发索引。

### 6.5 QA 冒烟轮询

问答冒烟验证使用后端异步任务返回的轮询建议，但前端仍要有默认值和取消机制：

1. `POST /api/v1/qa-sessions/{id}/messages` 的 `QaTaskSubmissionResponse` 和 `GET /api/v1/qa-sessions/{sessionId}/tasks/{taskId}` 的 `QaTaskDetailResponse` 都应包含 `mode`、`recommendedPollingIntervalSeconds`、`staleTimeoutSeconds` 和 `timeoutMessage`。
2. 轮询间隔优先使用响应里的 `recommendedPollingIntervalSeconds`；缺失或非法时按 mode 兜底：`local/basic=10s`，`global/drift=30s`。
3. 最大等待时间优先使用响应里的 `staleTimeoutSeconds`；缺失或非法时按 mode 兜底：`local/basic=300s`，`global/drift=1800s`。
4. mode 来源优先级：任务响应 `mode` > 发送消息请求中的 mode > 前端默认 `local`。
5. 到达最大等待时间后停止轮询，展示 `timeoutMessage`；若该字段缺失，显示“任务长时间未更新，请稍后刷新任务状态”。
6. 路由离开、组件卸载、用户点击取消时必须清理 `setTimeout` / `AbortController`。
7. 任务终态仅接受 `success` / `failed` / `timeout` / `stale` 等明确状态；未知状态继续按 blocked 或 warning 展示，不写入成功结论。

---

## 7. 测试与验证

### 7.1 前端单元验证

在 `frontend/apps/admin-app` 执行：

```bash
pnpm test
pnpm build
```

需要覆盖：

1. `/app/courses` 配置切为 `live`。
2. live loader 能把 `ApiPageData.items` 归一为表格 rows。
3. 健康页 normalizer 继续保留 `reachable` 和 `ready`。
4. loader 返回 `pagination`，表格组件能展示并触发翻页。
5. 课程详情子区块失败时只显示局部错误，不触发整页 error。
6. `unwrapApiResponse()` 遇到非标准 envelope 会抛出 `nonStandard` 错误。
7. 标准 envelope 中非 `code=200` 的业务错误会进入错误处理分支，而不是当成成功数据。
8. 构建向导步骤能根据真实状态变成 `done`、`running`、`blocked`、`failed`。
9. 长任务 HTTP 超时后会进入资源轮询状态，而不是直接业务失败。
10. `long-task-state.js` 能按超时/锁定/冲突场景进入正确状态，并在组件卸载时取消轮询。
11. QA 冒烟轮询会在组件卸载时清理定时器。
12. 未接入页面仍显示 `mock`，防止一口气全局改成 live。

### 7.2 后端回归

在 `backend/ckqa-back` 执行：

```bash
./mvnw test
./mvnw -DskipTests compile
```

需要新增或更新：

1. `CoursesControllerWebMvcTest`：覆盖 `GET /courses`、`GET /courses/{courseId}`、已有嵌套资料/知识库接口。
2. `KnowledgeBasesControllerWebMvcTest`：覆盖 `GET /knowledge-bases`、`GET /knowledge-bases/{id}`、已有索引运行接口。
3. Service 测试：覆盖课程汇总字段、知识库最近索引状态、空数据。
4. `PdfWorkflowServiceTest`：覆盖解析状态冲突、导出命名锁、已有完整 GraphRAG 导出时的幂等返回。
5. 如果增加 CORS 配置，需要覆盖允许本地开发 origin、拒绝非白名单 origin。

### 7.3 本地联调验证

推荐启动顺序：

```bash
cd graphrag_pipeline
python utils/main.py
```

```bash
cd backend/ckqa-back
./mvnw spring-boot:run
```

```bash
cd frontend/apps/admin-app
pnpm dev
```

本地接口检查建议使用：

```bash
curl --noproxy '*' http://127.0.0.1:8080/api/v1/system/health
curl --noproxy '*' http://127.0.0.1:8080/api/v1/courses
curl --noproxy '*' http://127.0.0.1:8080/api/v1/knowledge-bases
```

如果浏览器请求被 CORS 拦截，优先检查当前环境采用的是 Java CORS 还是 Vite `/api/v1` 代理；不要同时修改前端 API_BASE_URL 和后端 CORS 白名单来绕过问题。

浏览器验收：

1. `/app/health` 首屏自动出现真实健康状态。
2. `/app/courses` 不再出现“操作系统 / 数据结构 / 计算机网络”的 mock rows，除非数据库真实存在这些课程。
3. `/app/courses/:courseId` 能看到该课程真实资料和知识库。
4. 从知识库进入构建向导后，每个步骤都能由真实接口推进或阻塞。
5. 所有已接入页面的 `DataSourceChip` 显示“实时数据”。

---

## 8. 预计修改文件

### 8.1 前端

| 文件 | 作用 |
| --- | --- |
| `frontend/apps/admin-app/src/api/client.js` | 统一解包 Java `ApiResponse`、处理分页和错误 |
| `frontend/apps/admin-app/src/api/system.js` | 系统健康 API |
| `frontend/apps/admin-app/src/api/courses.js` | 课程、课程资料、课程知识库 API |
| `frontend/apps/admin-app/src/api/materials.js` | 资料详情、解析、导出和结果 API |
| `frontend/apps/admin-app/src/api/knowledge-bases.js` | 知识库、索引运行 API |
| `frontend/apps/admin-app/src/api/qa.js` | 构建后冒烟问答 API |
| `frontend/apps/admin-app/src/views/pages/module-content.js` | 移除已接入页面的 mock rows，保留结构配置 |
| `frontend/apps/admin-app/src/views/pages/module-loaders.js` | 按路由加载真实数据并归一 UI 模型 |
| `frontend/apps/admin-app/src/views/pages/ModulePage.vue` | 支持 loading/error/empty/live refreshedAt 和真实 rows/steps |
| `frontend/apps/admin-app/src/views/pages/long-task-state.js` | 统一长任务超时后的资源轮询兜底模型 |
| `frontend/apps/admin-app/src/views/system/HealthView.vue` | 改成统一 API 模块，挂载自动刷新 |
| `frontend/apps/admin-app/src/app-shell.test.js` | 更新 `DataSourceChip`、loader、构建向导状态测试 |

### 8.2 后端

| 文件 | 作用 |
| --- | --- |
| `backend/ckqa-back/src/main/java/org/ysu/ckqaback/controller/CoursesController.java` | 增加课程列表和课程详情接口 |
| `backend/ckqa-back/src/main/java/org/ysu/ckqaback/course/CourseLookupService.java` | 聚合课程资料、知识库、最近索引摘要 |
| `backend/ckqa-back/src/main/java/org/ysu/ckqaback/course/dto/CourseSummaryResponse.java` | 课程列表轻量响应 DTO |
| `backend/ckqa-back/src/main/java/org/ysu/ckqaback/course/dto/CourseDetailResponse.java` | 课程详情响应 DTO，承载详情页扩展字段 |
| `backend/ckqa-back/src/main/java/org/ysu/ckqaback/course/dto/CourseQueryRequest.java` | 课程列表查询参数 |
| `backend/ckqa-back/src/main/java/org/ysu/ckqaback/controller/KnowledgeBasesController.java` | 增加知识库列表和详情接口 |
| `backend/ckqa-back/src/main/java/org/ysu/ckqaback/index/dto/KnowledgeBaseSummaryResponse.java` | 知识库列表轻量响应 DTO |
| `backend/ckqa-back/src/main/java/org/ysu/ckqaback/index/dto/KnowledgeBaseDetailResponse.java` | 知识库详情响应 DTO，承载详情页扩展字段 |
| `backend/ckqa-back/src/main/java/org/ysu/ckqaback/config/WebCorsConfig.java` | 如选择后端跨域方案，集中声明本地开发 CORS 白名单 |
| `backend/ckqa-back/src/test/java/org/ysu/ckqaback/controller/CoursesControllerWebMvcTest.java` | 覆盖新增课程接口 |
| `backend/ckqa-back/src/test/java/org/ysu/ckqaback/controller/KnowledgeBasesControllerWebMvcTest.java` | 覆盖新增知识库接口 |
| `backend/ckqa-back/src/test/java/org/ysu/ckqaback/pdf/PdfWorkflowServiceTest.java` | 扩展解析状态冲突、导出锁定和完整导出幂等返回覆盖 |

---

## 9. 分阶段实施建议

### Phase 1：健康页和课程列表

1. 补后端 `GET /courses`、`GET /courses/{courseId}`。
2. 前端新增 API 解包与 `courses` loader。
3. `/app/health` 改为自动加载。
4. `/app/courses` 切 live。
5. 建立前端业务错误码处理和 `CourseSummaryResponse` / `CourseDetailResponse` 分层。
6. 跑前端 `pnpm test && pnpm build`，后端 `./mvnw test`。

### Phase 2：课程详情和资料生命周期

1. `/app/courses/:courseId` 接资料和知识库列表。
2. `/app/materials/:materialId` 接资料详情、解析结果、解析触发、GraphRAG 导出。
3. 解析/导出按钮按真实状态禁用，并接入幂等/冲突处理与 HTTP 超时后的资源轮询兜底。
4. 保持上传和课程创建为未开放。

### Phase 3：知识库列表、详情和构建向导

1. 补后端 `GET /knowledge-bases`、`GET /knowledge-bases/{id}`。
2. `/app/knowledge-bases`、`/app/knowledge-bases/:kbId` 切 live。
3. `/app/knowledge-bases/:kbId/build` 用真实状态驱动五步流程。
4. 索引成功后刷新知识库详情，确认 `activeIndexRunId`。

### Phase 4：问答冒烟验证

1. 构建向导最后一步创建 QA session。
2. 发送一条固定或用户输入的冒烟问题。
3. 按后端返回的 `recommendedPollingIntervalSeconds` 轮询任务。
4. 成功后提供进入问答会话详情的入口。

---

## 10. 遗留问题与下一步

1. 课程创建、知识库创建和资料上传接口不在当前最小闭环内，相关按钮应禁用或明确显示未开放。
2. 解析、导出和索引仍是同步长任务，前端首版必须加资源轮询兜底；后续应把它们改造成真正异步任务接口，返回任务 ID 后由前端统一轮询。
3. 当前登录仍是开发态身份切换，真实 RBAC 和课程数据范围需要后续接入认证接口后再收紧。
4. 课程列表聚合字段首版可由 Service 内存聚合完成；如果数据量增长，应迁移到 Mapper SQL 或专门视图。
5. 多资料知识库构建暂不作为首版能力；要支持多资料，需要同时补批量导出接口、GraphRAG 输入合并/指定策略和索引运行元数据。
6. 冒烟验证依赖已有可用知识库和 GraphRAG 输出，联调环境缺索引时应显示 blocked，而不是伪造成功。
7. 长任务等待上限首版以前端常量对齐后端默认配置；后续更稳妥的方式是 Java 暴露运行时配置接口，避免前后端配置漂移。

---

## 11. 评审意见处理结论

| 序号 | 结论 | 处理 |
| --- | --- | --- |
| 1. 同步长任务超时与中断 | 采纳 | 新增“同步长任务兜底”，要求 HTTP 超时后按资源状态轮询，离开页面取消轮询，索引超时后查最近运行避免重复创建。 |
| 2. 课程路由参数类型 | 采纳 | 明确课程路由始终使用字符串 `courseId`，数字 `id` 仅为数据库内部主键和辅助字段。 |
| 3. 多资料批量导出 | 部分采纳 | 当前代码不支持真实多资料构建，首版改为单资料选择；批量导出作为后续能力，需补 batch 接口和 GraphRAG 输入策略。 |
| 4. 激活步骤 UX | 采纳 | 删除独立“激活索引版本”步骤，合并为“创建索引并确认激活”。 |
| 5. 课程详情局部失败 | 采纳 | 新增区块级状态，子区块失败不拖垮整页。 |
| 6. `unwrapApiResponse()` 静默兜底 | 采纳 | 改为非标准 envelope 直接抛 `nonStandard` 错误，不渲染为业务数据。 |
| 7. QA 轮询上限和取消 | 采纳 | 补默认轮询间隔、最大等待时间、超时文案、组件卸载清理。 |
| 8. 分页状态 | 采纳 | loader 返回结构增加 `pagination`。 |
| 9. `force` 策略 | 采纳 | 明确默认 `false`，仅资料重解析或用户确认覆盖时 `true`，并要求二次确认。 |
| 10. 未开放按钮状态 | 采纳 | 明确禁用按钮、tooltip/title 文案和上传按钮不展示。 |
| 11. 空数据文案 | 采纳 | 增加开发态与生产态两套空态文案。 |
| 12. CORS | 采纳 | 增加 CORS / Vite 代理方案与验证提醒。 |
| 13. 解析/导出幂等性 | 采纳 | 明确解析当前只允许 `pending/failed` 触发，导出依靠完整产物判断和命名锁兜底，前端只在失败或锁定/冲突后的确认状态里刷新资源。 |
| 14. 长任务阈值数值 | 采纳 | 补 `parse=900s`、`export=300s`、`index=1800s` 和前端轮询间隔，来源对齐后端默认配置。 |
| 15. QA 字段来源 | 采纳 | 明确 submission/detail 响应都应包含 mode、轮询间隔、stale 阈值和超时文案，缺失时按请求 mode/default local 兜底。 |
| 16. GraphRAG 产物识别 | 采纳 | 明确按 `fileName` 的 `graphrag_` 前缀和具体文件名判断完整导出，不用结果数量判断。 |
| 17. 业务错误码策略 | 采纳 | 增加 HTTP 与业务码的前端处理表。 |
| 18. `long-task-state.js` 职责 | 采纳 | 定义为纯函数 + composable 风格共享模块，并说明与 loader / ModulePage 的调用边界。 |
| 19. 课程列表/详情 DTO 分离 | 采纳 | 改为 `CourseSummaryResponse` 列表轻量 DTO 和 `CourseDetailResponse` 详情 DTO。 |
| 20. 知识库列表/详情 DTO 分离 | 采纳 | 改为 `KnowledgeBaseSummaryResponse` 列表轻量 DTO 和 `KnowledgeBaseDetailResponse` 详情 DTO。 |
| 21. 业务码已有实现标注 | 采纳 | 标明 `4093`-`4097` 是当前 `ApiResultCode` 已有枚举，但联调前仍需测试确认接口实际返回。 |
| 22. `PdfWorkflowServiceTest` 文件清单 | 采纳 | 在后端预计修改文件中补列该测试类，作为扩展覆盖而不是新建未知文件。 |
