# ckqa-back

`backend/ckqa-back/` 是 CKQA 仓库中的 Java 后端一期编排入口，基于 Spring Boot 4.0.5、Java 21 与 MyBatis-Plus 3.5.16。

当前目标不是重写 `pdf_ingest/` 或 `graphrag_pipeline/`，而是在不破坏现有 Python 主链路的前提下，提供统一的 `/api/v1` 业务入口，先打通：

1. PDF 解析触发
2. GraphRAG 输入导出
3. 索引构建与陈旧任务恢复
4. GraphRAG 问答代理
5. 课程入口与系统健康检查
6. 管理端 live API 所需课程、资料、知识库和 QA 冒烟验证聚合接口

如果你需要了解整个仓库的主链路，请同时查看：

- [../../README.md](../../README.md)
- [../../docs/student-backend-graphrag-api-contract.md](../../docs/student-backend-graphrag-api-contract.md)
- [../../pdf_ingest/README.md](../../pdf_ingest/README.md)
- [../../graphrag_pipeline/README.md](../../graphrag_pipeline/README.md)

如果你需要回看已经完成并归档的实现背景，请优先查看：

- [../../docs/superpowers/archive/specs/2026-04-22-ckqa-back-async-qa-task-design.md](../../docs/superpowers/archive/specs/2026-04-22-ckqa-back-async-qa-task-design.md)
- [../../docs/superpowers/archive/plans/2026-04-22-ckqa-back-async-qa-task-implementation.md](../../docs/superpowers/archive/plans/2026-04-22-ckqa-back-async-qa-task-implementation.md)
- [../../docs/superpowers/archive/plans/2026-04-23-course-materials-material-objects-impl.md](../../docs/superpowers/archive/plans/2026-04-23-course-materials-material-objects-impl.md)

## 一期编排接口

当前已经落地的核心接口如下：

- `GET /api/v1/system/health`
- `GET /api/v1/system/readiness`
- `POST /api/v1/auth/admin/login`
- `POST /api/v1/auth/student/login`
- `POST /api/v1/auth/student/register`
- `GET /api/v1/auth/me`
- `POST /api/v1/auth/me/avatar`
- `GET /api/v1/user-avatars/default-user-avatar.svg`
- `GET /api/v1/courses`
- `GET /api/v1/courses/{courseId}`
- `POST /api/v1/courses/covers`
- `POST /api/v1/courses/{courseId}/cover`
- `GET /api/v1/courses/{courseId}/materials`
- `POST /api/v1/courses/{courseId}/materials`
- `GET /api/v1/courses/{courseId}/materials/{materialId}`
- `PATCH /api/v1/courses/{courseId}/materials/{materialId}`
- `DELETE /api/v1/courses/{courseId}/materials/{materialId}`
- `GET /api/v1/courses/{courseId}/pdf-files`
- `GET /api/v1/courses/{courseId}/knowledge-bases`
- `GET /api/v1/knowledge-bases`
- `GET /api/v1/knowledge-bases/{id}`
- `GET /api/v1/pdf-files/{id}`
- `GET /api/v1/pdf-files/{id}/results`
- `GET /api/v1/pdf-files/{id}/results/{resultId}/preview`
- `GET /api/v1/pdf-files/{id}/results/{resultId}/download`
- `POST /api/v1/pdf-files/{id}/parse`
- `POST /api/v1/pdf-files/{id}/export-graphrag`
- `POST /api/v1/knowledge-bases/{id}/index-runs`
- `GET /api/v1/knowledge-bases/{id}/index-runs`
- `POST /api/v1/knowledge-bases/{id}/build-runs`
- `GET /api/v1/knowledge-bases/{id}/build-runs`
- `POST /api/v1/knowledge-bases/{id}/build-runs/gc`
- `GET /api/v1/knowledge-base-build-runs/{id}`
- `DELETE /api/v1/knowledge-base-build-runs/{id}`
- `PUT /api/v1/knowledge-base-build-runs/{id}/material-selection`
- `POST /api/v1/knowledge-base-build-runs/{id}/parse-check`
- `POST /api/v1/knowledge-base-build-runs/{id}/graph-input`
- `POST /api/v1/knowledge-base-build-runs/{id}/prompt-confirmation`
- `POST /api/v1/knowledge-base-build-runs/{id}/index-runs`
- `POST /api/v1/knowledge-base-build-runs/{id}/qa-smoke`
- `GET /api/v1/index-runs/{id}`
- `GET /api/v1/index-runs/{id}/artifacts`
- `GET /api/v1/index-artifacts/{id}`
- `DELETE /api/v1/index-artifacts/{id}`
- `POST /api/v1/qa-sessions`
- `GET /api/v1/qa-sessions/{id}`
- `GET /api/v1/qa-sessions/{id}/messages`
- `POST /api/v1/qa-sessions/{id}/messages`
- `GET /api/v1/qa-sessions/{sessionId}/tasks/{taskId}`

其中 `/api/v1/pdf-files` 与 `/api/v1/courses/{courseId}/pdf-files` 目前都保留兼容语义：对外仍沿用旧路径，内部数据源已经切换为 `course_materials`，并通过 `material_objects` 复用同一份物理资料对象。新业务文档和后续前端接入应优先按“课程资料”理解。

统一响应格式示例：

```json
{
  "code": 200,
  "message": "操作成功",
  "data": {},
  "timestamp": "2026-04-21T21:00:00"
}
```

课程封面：

- `courses.cover_url` 为空或创建请求未传 `coverUrl` 时，后端返回默认封面 `/api/v1/course-covers/default-course-cover.svg`。
- `POST /api/v1/courses/covers` 接收 `multipart/form-data` 字段 `file`，用于创建课程前先上传封面并取得 `coverUrl`。
- `POST /api/v1/courses/{courseId}/cover` 接收同样的 `file` 字段，用于替换已有课程封面。
- 上传文件默认保存到 MinIO 的 `course-artifacts/course-covers/`，并通过 `/api/v1/course-covers/**` 由后端代理访问；仅支持 PNG、JPG、WEBP，默认上限 2MB。

用户头像：

- 登录态、用户列表与课程教师响应均返回非空 `avatarUrl`；用户未上传头像时返回 `/api/v1/user-avatars/default-user-avatar.svg`。
- `POST /api/v1/auth/me/avatar` 接收 `multipart/form-data` 字段 `file`，支持 PNG、JPG、WEBP，默认上限 2MB。
- 上传头像默认保存到 MinIO 的 `course-artifacts/user-avatars/`，数据库 `users.avatar_*` 只保存对象元信息。

课程资料：

- 新业务优先使用 `/api/v1/courses/{courseId}/materials` 系列接口做课程资料 CRUD；v1 上传仅接受 PDF。
- 单个 PDF 默认上限为 200MB；后端会在读取文件字节前根据 `COURSE_MATERIAL_MAX_FILE_SIZE_BYTES` 拒绝超限文件，同时 Spring multipart 默认限制也设置为 200MB。
- 上传资料会按文件 MD5 创建或复用 `material_objects`，再在 `course_materials` 中创建课程内资料记录；同一课程重复资料或重复展示名返回 409。
- 解析中的资料不能删除；旧 `/api/v1/courses/{courseId}/pdf-files` 与 `/api/v1/pdf-files/**` 仍保留给解析、结果查看和 GraphRAG 导出链路兼容使用。
- 资料详情响应会返回 `parseProgress`、`parseStage` 与 `parseProgressPercent`；当 `pdf_ingest` 在 MinerU `state=running` 阶段轮询到官方 `extract_progress.extracted_pages/total_pages` 时，`parseProgress` 会优先返回 `estimated=false` 的真实页级百分比、`extractedPages` 和 `totalPages`。解析产物响应会返回 `previewUrl` / `downloadUrl`，由 Java 后端代理读取 MinIO 对象后以 inline 或 attachment 方式输出。

鉴权：

- 后端已经接入 Spring Security Resource Server，`/api/v1/auth/admin/login`、`/api/v1/auth/student/login`、`/api/v1/auth/student/register`、`/api/v1/system/health`、`/api/v1/course-covers/**` 与 `/api/v1/user-avatars/**` 可匿名访问，其余 `/api/v1/**` 默认需要 `Authorization: Bearer <jwt>`。
- 管理端登录允许 `admin` / `teacher` 角色，学生端登录只允许 `student` 角色。
- 课程列表、课程详情和课程成员授权接口会优先读取 JWT 中的 `userCode`；`X-CKQA-User-Code` 仅作为本地测试与兼容兜底。
- 本地联调测试账号由 `sql/migrations/20260506_jwt_auth_credentials.sql` 补充密码哈希，演示密码统一为 `Ckqa@2026`。

## 目录说明

| 路径 | 作用 |
| --- | --- |
| `src/main/java/org/ysu/ckqaback/api/` | 路由常量、统一响应体、业务响应码 |
| `src/main/java/org/ysu/ckqaback/auth/` | Spring Security JWT 登录、注册、令牌签发与当前用户解析 |
| `src/main/java/org/ysu/ckqaback/exception/` | 业务异常与全局异常处理 |
| `src/main/java/org/ysu/ckqaback/integration/` | Python CLI 调用、GraphRAG HTTP 调用、数据库命名锁、运行配置 |
| `src/main/java/org/ysu/ckqaback/pdf/` | PDF 查询、解析触发、GraphRAG 导出工作流与 DTO |
| `src/main/java/org/ysu/ckqaback/index/` | 索引任务创建、陈旧任务恢复、索引响应 DTO |
| `src/main/java/org/ysu/ckqaback/course/` | 课程下 PDF / 知识库入口查询 |
| `src/main/java/org/ysu/ckqaback/qa/` | 问答会话、消息、GraphRAG 代理工作流与 DTO |
| `src/main/java/org/ysu/ckqaback/system/` | 系统健康检查接口与状态 DTO |
| `src/test/java/org/ysu/ckqaback/` | 单元测试与 WebMvc 测试 |
| `src/main/resources/application.properties` | MySQL、MyBatis-Plus 与一期集成配置 |
| `.env.example` | 本地运行所需环境变量示例 |

## 外部依赖配置

运行应用前，请先准备 MySQL 与 Python 集成配置：

```bash
export MYSQL_HOST=localhost
export MYSQL_PORT=23306
export MYSQL_DATABASE=ocqa
export MYSQL_USER=root
export MYSQL_PASSWORD=your-password

export CKQA_JWT_SECRET=please-change-this-local-jwt-secret-at-least-32-chars
export CKQA_JWT_ISSUER=ckqa-back
export CKQA_JWT_TTL=PT8H

export MINIO_ENDPOINT=localhost:9000
export MINIO_ACCESS_KEY=admin
export MINIO_SECRET_KEY=12345678
export MINIO_SECURE=false
export COURSE_COVER_BUCKET=course-artifacts
export COURSE_COVER_OBJECT_PREFIX=course-covers
export COURSE_COVER_MAX_FILE_SIZE_BYTES=2097152
export COURSE_MATERIAL_BUCKET=course-artifacts
export COURSE_MATERIAL_OBJECT_PREFIX=course-materials
export COURSE_MATERIAL_MAX_FILE_SIZE_BYTES=209715200
export CKQA_MULTIPART_MAX_FILE_SIZE=200MB
export CKQA_MULTIPART_MAX_REQUEST_SIZE=200MB

# 可留空；后端会自动优先使用 ~/miniconda3/envs/courseKg/bin/python，
# 如需强制指定解释器，再填写真实绝对路径。
export PDF_INGEST_PYTHON=
export PDF_INGEST_ROOT=/home/sunlight/Projects/ckqa/pdf_ingest

# 可留空；后端会自动优先使用 ~/miniconda3/envs/graphrag-oneapi/bin/python。
export GRAPHRAG_PYTHON=
export GRAPHRAG_ROOT=/home/sunlight/Projects/ckqa/graphrag_pipeline
export GRAPHRAG_OUTPUT_DIR=/home/sunlight/Projects/ckqa/graphrag_pipeline/output
export GRAPHRAG_LANCEDB_URI=/home/sunlight/Projects/ckqa/graphrag_pipeline/output/lancedb
export GRAPHRAG_BUILD_RUNS_ROOT=/home/sunlight/Projects/ckqa/graphrag_pipeline/runtime/kb-build-runs
export GRAPHRAG_ALLOW_CONCURRENT_KB_BUILDS=true
export GRAPHRAG_AUTO_ACTIVATION_POLICY=latest-build-only
export GRAPHRAG_API_HOST=127.0.0.1
export GRAPHRAG_API_PORT=8012
export GRAPHRAG_API_BASE_URL=http://127.0.0.1:8012
export GRAPHRAG_API_MANAGED_ENABLED=true

export PARSE_TIMEOUT_SECONDS=900
export EXPORT_TIMEOUT_SECONDS=300
export FETCH_TIMEOUT_SECONDS=300
export INDEX_TIMEOUT_SECONDS=1800
export QUERY_TIMEOUT_SECONDS=120
export QUERY_TASK_POLLING_INTERVAL_SECONDS=10
export QUERY_TASK_STALE_SECONDS=300
export QUERY_TASK_POLLING_INTERVAL_SECONDS_LOCAL=10
export QUERY_TASK_POLLING_INTERVAL_SECONDS_GLOBAL=30
export QUERY_TASK_POLLING_INTERVAL_SECONDS_DRIFT=30
export QUERY_TASK_STALE_SECONDS_LOCAL=300
export QUERY_TASK_STALE_SECONDS_GLOBAL=1800
export QUERY_TASK_STALE_SECONDS_DRIFT=1800
export INDEX_STALE_SECONDS=2400
```

这些变量也已经在 `.env.example` 中给出示例。

问答异步任务支持按 `mode` 覆盖前端轮询建议和 stale 阈值：

- 默认值：`QUERY_TASK_POLLING_INTERVAL_SECONDS` / `QUERY_TASK_STALE_SECONDS`
- 可选覆盖：`QUERY_TASK_POLLING_INTERVAL_SECONDS_LOCAL|GLOBAL|BASIC|DRIFT`
- 可选覆盖：`QUERY_TASK_STALE_SECONDS_LOCAL|GLOBAL|BASIC|DRIFT`
- 可选文案：`QUERY_TASK_TIMEOUT_MESSAGE_LOCAL|GLOBAL|BASIC|DRIFT`

2026-04-23 使用 main 分支现有图谱单轮实测同一问题：

| mode | 实测耗时 | 默认前端轮询建议 | 默认 stale 阈值 |
| --- | ---: | ---: | ---: |
| `local` | 约 110 秒 | 10 秒 | 300 秒 |
| `global` | 约 800 秒 | 30 秒 | 1800 秒 |
| `drift` | 约 725 秒 | 30 秒 | 1800 秒 |

这里的 stale 阈值是“心跳长期未更新”的回收阈值，不是强制总耗时上限；只要 Python 任务仍持续刷新心跳，长查询会继续等待终态。

## 启动顺序

一期联调推荐按下面顺序启动：

1. 通过仓库根目录 `infra/docker-compose.yml` 启动 MySQL、MinIO、One API、Neo4j、Redis
2. 根据需要确认 `pdf_ingest/` 和 `graphrag_pipeline/` 根目录、Python 解释器路径已配置
3. 启动 `backend/ckqa-back`

基础设施启动：

```bash
cd ../../infra
cp .env.example .env
# 编辑 .env，填入当前 MySQL root 密码、MinIO 账号密码和可选 REDIS_PASSWORD
docker compose up -d
docker compose ps
```

开发态可以让 Java 后端托管启动 GraphRAG API：设置 `GRAPHRAG_API_MANAGED_ENABLED=true` 后，Spring Boot 启动阶段会先探测 `GRAPHRAG_API_BASE_URL/health`，服务已存在则复用，服务不可达则在 `GRAPHRAG_ROOT` 下启动 `utils/main.py`。`pdf_ingest/` 当前没有常驻 HTTP 服务，仍由 Java 后端在解析/导出动作发生时按需调用 CLI。

启动 Java 后端：

```bash
cd backend/ckqa-back

export MYSQL_HOST=127.0.0.1
export MYSQL_PORT=23306
export MYSQL_DATABASE=ocqa
export MYSQL_USER=root
export MYSQL_PASSWORD="${MYSQL_PASSWORD:?请先设置 MYSQL_PASSWORD}"
export CKQA_REDIS_HOST=127.0.0.1
export CKQA_REDIS_PORT=16379
export REDIS_PASSWORD="${REDIS_PASSWORD:-}"
export CKQA_STUDENT_CACHE_ENABLED=true

export PDF_INGEST_ROOT=/home/sunlight/Projects/ckqa/pdf_ingest
export GRAPHRAG_ROOT=/home/sunlight/Projects/ckqa/graphrag_pipeline
export GRAPHRAG_OUTPUT_DIR=/home/sunlight/Projects/ckqa/graphrag_pipeline/output
export GRAPHRAG_LANCEDB_URI=/home/sunlight/Projects/ckqa/graphrag_pipeline/output/lancedb
export GRAPHRAG_BUILD_RUNS_ROOT=/home/sunlight/Projects/ckqa/graphrag_pipeline/runtime/kb-build-runs
export GRAPHRAG_ALLOW_CONCURRENT_KB_BUILDS=true
export GRAPHRAG_AUTO_ACTIVATION_POLICY=latest-build-only
export GRAPHRAG_API_HOST=127.0.0.1
export GRAPHRAG_API_PORT=8012
export GRAPHRAG_API_BASE_URL=http://127.0.0.1:8012
export GRAPHRAG_API_MANAGED_ENABLED=true
export COURSE_MATERIAL_MAX_FILE_SIZE_BYTES=209715200
export CKQA_MULTIPART_MAX_FILE_SIZE=200MB
export CKQA_MULTIPART_MAX_REQUEST_SIZE=200MB

./mvnw spring-boot:run
```

如果希望 GraphRAG API 继续手工独立启动，将 `GRAPHRAG_API_MANAGED_ENABLED` 设为 `false` 或不设置即可。

如果 MySQL root 密码保存在本机 `mysql` 容器环境变量中，可先执行：

```bash
export MYSQL_PASSWORD="$(docker exec mysql printenv MYSQL_ROOT_PASSWORD)"
```

本机验收：

```bash
curl http://127.0.0.1:8080/api/v1/system/health
curl http://127.0.0.1:8080/api/v1/courses
curl http://127.0.0.1:8080/api/v1/knowledge-bases
```

Redis 只作为学生端读路径缓存和登录/验证码短期状态，不是业务事实源。若 Redis 不可达，课程列表、知识库列表、智能推荐和 Hybrid warmup 会回源继续执行，只是 `/system/health` 的 `redis` 子项会显示不可达。

## 健康检查说明

`GET /api/v1/system/health` 当前会返回细分子项，而不是单一布尔值，重点包括：

- `mysql`
- `redis`
- `pdf-ingest-root`
- `graphrag-root`
- `graphrag-build-runs-root`
- `graphrag-api`
- `graphrag-ready`

其中：

- `reachable` 表示依赖可连通或路径存在
- `ready` 表示依赖具备处理真实业务的前提条件

`GET /api/v1/system/health` 是轻量健康检查，不再要求共享 `GRAPHRAG_ROOT/output/lancedb` 已存在。`GET /api/v1/system/readiness` 会额外包含 `graphrag-output`，用于手工 CLI 调试路径的就绪判断。

### 学生端 Redis 缓存

后端使用 Redis 缓存低风险学生端读模型，浏览器仍只调用 Java `/api/v1`，不会直连 Redis。当前缓存范围：

- `GET /api/v1/courses`：按当前登录用户和分页/筛选参数隔离，默认 TTL `PT60S`。
- `GET /api/v1/courses/{courseId}/knowledge-bases`：先校验课程可读，再按当前登录用户和课程隔离，默认 TTL `PT60S`。
- `POST /api/v1/qa-routing/recommend`：先校验会话/课程/知识库权限，再缓存纯路由结果，默认 TTL `PT5M`。
- `POST /api/v1/qa-sessions/hybrid-warmup`：按知识库、active index 和 build-run 输出目录缓存 readiness；ready 默认 `PT5M`，not ready 默认 `PT15S`。

不会缓存问答消息、task 轮询结果或最终 assistant answer，避免 pending/success 竞态和旧答案展示。配置项在 `.env.example` 中以 `CKQA_STUDENT_CACHE_*` 开头；如需临时关闭，设置 `CKQA_STUDENT_CACHE_ENABLED=false`。

## GraphRAG build-run 隔离

管理端知识库构建不再把所有输入、输出和日志写入共享 `graphrag_pipeline/input` / `output`。Java 后端会为每次构建创建独立 workspace：

```text
${GRAPHRAG_BUILD_RUNS_ROOT}/user_{userId}/kb_{knowledgeBaseId}/build_{buildRunId}/
  graph-input/
  index/input/
  index/output/
  index/logs/process.log
  qa-smoke/request.json
  qa-smoke/response.json
```

`GRAPHRAG_BUILD_RUNS_ROOT` 未配置时，默认解析为 `${GRAPHRAG_ROOT}/runtime/kb-build-runs`。`GRAPHRAG_ALLOW_CONCURRENT_KB_BUILDS=false` 时，同一知识库只允许一个 `pending/running` build run。`GRAPHRAG_AUTO_ACTIVATION_POLICY=latest-build-only` 时，只有最新 build run 的成功索引会自动激活；旧成功索引仍可通过手动激活接口切换。

## 常用命令

### 启动应用

```bash
./mvnw spring-boot:run
```

### 异步问答任务

提交问题：

```bash
curl -s -X POST http://127.0.0.1:8080/api/v1/qa-sessions/5/messages \
  -H 'Content-Type: application/json' \
  -d '{"mode":"global","content":"请概括这套图谱的主题"}'
```

当前异步问答支持的 `mode`：

- `local`
- `global`
- `drift`
- `basic`

`full` 当前已归档为后续扩展模式，不在 Java 编排链路内公开支持。

轮询任务：

```bash
TASK_ID=$(curl -s -X POST http://127.0.0.1:8080/api/v1/qa-sessions/5/messages \
  -H 'Content-Type: application/json' \
  -d '{"mode":"global","content":"请概括这套图谱的主题"}' | jq -r '.data.taskId')

curl -s http://127.0.0.1:8080/api/v1/qa-sessions/5/tasks/$TASK_ID
```

任务时间字段约定：

- Java DTO 统一按 `Asia/Shanghai` 的 `LocalDateTime` 解析 Python 返回的任务时间
- `createdAt`、`startedAt`、`lastHeartbeatAt`、`finishedAt` 都是不带偏移量的本地时间字符串，例如 `2026-04-22T20:20:34`
- `recommendedPollingIntervalSeconds`、`staleTimeoutSeconds`、`timeoutMessage` 会随 `mode` 返回，前端可以直接用它们调整轮询节奏和超时提示
- Java 应用启动时会自动回收超过对应 mode stale 阈值的历史 `pending` / `running` 问答任务，避免 `qa_retrieval_logs` 长期停在活动态

### 运行测试

```bash
./mvnw test
```

### 本地 MinIO 解析产物 smoke

默认不随普通测试执行，需显式打开，并指向本机 MinIO 中已经存在的解析产物对象：

```bash
export CKQA_RUN_MINIO_PARSE_RESULT_SMOKE=true
export CKQA_SMOKE_PARSE_RESULT_OBJECT_KEY='crs-xxx/material_1/content_list.json'
# 可选：默认读取 COURSE_MATERIAL_BUCKET / COURSE_COVER_BUCKET / course-artifacts
export CKQA_SMOKE_PARSE_RESULT_BUCKET=course-artifacts
export CKQA_SMOKE_PARSE_RESULT_FILE_NAME=content_list.json
export CKQA_SMOKE_PARSE_RESULT_EXPECT_CONTAINS='content'

./mvnw -Dtest=PdfParseResultMinioSmokeTest test
```

该 smoke 会使用当前 `MINIO_ENDPOINT`、`MINIO_ACCESS_KEY`、`MINIO_SECRET_KEY`、`MINIO_SECURE` 读取真实对象，并通过 `/api/v1/pdf-files/{id}/results/{resultId}/preview` 与 `/download` 的控制器路径验证 inline / attachment 输出。

### 仅编译

```bash
./mvnw -DskipTests compile
```

### 生成全表标准代码

```bash
./mvnw -q -DskipTests test-compile exec:java \
  -Dexec.classpathScope=test \
  -Dexec.mainClass=org.ysu.ckqaback.support.codegen.MybatisPlusCodeGenerator \
  -Dexec.args="--overwrite=true"
```

## 最小联调顺序

建议按下面顺序验证一期闭环：

1. `GET /api/v1/system/health`
2. `GET /api/v1/courses/{courseId}/pdf-files`
3. `POST /api/v1/pdf-files/{id}/parse`
4. `POST /api/v1/pdf-files/{id}/export-graphrag`
5. `POST /api/v1/knowledge-bases/{id}/index-runs`
6. `POST /api/v1/qa-sessions`
7. `POST /api/v1/qa-sessions/{id}/messages`
8. `GET /api/v1/qa-sessions/{sessionId}/tasks/{taskId}`

## 回归验证

当前后端回归至少执行以下命令：

```bash
./mvnw -q test
./mvnw -q -DskipTests compile
```

如果需要做应用级启动验证，再额外执行：

```bash
./mvnw -q spring-boot:run
```

## 当前已知边界

- `parse` 与 `index` 仍是同步长任务，一期主要靠命令超时与陈旧任务恢复兜底
- 问答链路已改成异步任务模式，修的是“超时语义”，不是 `global` / `drift` 查询速度
- Python 任务快照目前仍是进程内内存态，Python 服务重启会导致 Java 把对应任务标记为 `failed`
- `qa_retrieval_hits` 尚未落地
- `system/health` 目前是“就绪前置条件检查”，不是完整语义级问答探活
- Java 侧还没有承接上传链路，仍以已有课程资料记录为起点；`/api/v1/pdf-files` 现在只是兼容路由，内部实际读写的是 `course_materials` / `material_objects`
