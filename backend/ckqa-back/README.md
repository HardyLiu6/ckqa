# ckqa-back

`backend/ckqa-back/` 是 CKQA 仓库中的 Java 后端一期编排入口，基于 Spring Boot 4.0.5、Java 21 与 MyBatis-Plus 3.5.16。

当前目标不是重写 `pdf_ingest/` 或 `graphrag_pipeline/`，而是在不破坏现有 Python 主链路的前提下，提供统一的 `/api/v1` 业务入口，先打通：

1. PDF 解析触发
2. GraphRAG 输入导出
3. 索引构建与陈旧任务恢复
4. GraphRAG 问答代理
5. 课程入口与系统健康检查

如果你需要了解整个仓库的主链路，请同时查看：

- [../../README.md](../../README.md)
- [../../pdf_ingest/README.md](../../pdf_ingest/README.md)
- [../../graphrag_pipeline/README.md](../../graphrag_pipeline/README.md)

## 一期编排接口

当前已经落地的核心接口如下：

- `GET /api/v1/system/health`
- `GET /api/v1/courses/{courseId}/pdf-files`
- `GET /api/v1/courses/{courseId}/knowledge-bases`
- `GET /api/v1/pdf-files/{id}`
- `GET /api/v1/pdf-files/{id}/results`
- `POST /api/v1/pdf-files/{id}/parse`
- `POST /api/v1/pdf-files/{id}/export-graphrag`
- `POST /api/v1/knowledge-bases/{id}/index-runs`
- `GET /api/v1/knowledge-bases/{id}/index-runs`
- `GET /api/v1/index-runs/{id}`
- `POST /api/v1/qa-sessions`
- `GET /api/v1/qa-sessions/{id}`
- `GET /api/v1/qa-sessions/{id}/messages`
- `POST /api/v1/qa-sessions/{id}/messages`
- `GET /api/v1/qa-sessions/{sessionId}/tasks/{taskId}`

统一响应格式示例：

```json
{
  "code": 200,
  "message": "操作成功",
  "data": {},
  "timestamp": "2026-04-21T21:00:00"
}
```

## 目录说明

| 路径 | 作用 |
| --- | --- |
| `src/main/java/org/ysu/ckqaback/api/` | 路由常量、统一响应体、业务响应码 |
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

export PDF_INGEST_PYTHON=/path/to/courseKg/bin/python
export PDF_INGEST_ROOT=/home/sunlight/Projects/ckqa/pdf_ingest

export GRAPHRAG_PYTHON=/path/to/graphrag-oneapi/bin/python
export GRAPHRAG_ROOT=/home/sunlight/Projects/ckqa/graphrag_pipeline
export GRAPHRAG_API_BASE_URL=http://127.0.0.1:8012

export PARSE_TIMEOUT_SECONDS=900
export EXPORT_TIMEOUT_SECONDS=300
export FETCH_TIMEOUT_SECONDS=300
export INDEX_TIMEOUT_SECONDS=1800
export QUERY_TIMEOUT_SECONDS=120
export QUERY_TASK_POLLING_INTERVAL_SECONDS=5
export QUERY_TASK_STALE_SECONDS=30
export INDEX_STALE_SECONDS=2400
```

这些变量也已经在 `.env.example` 中给出示例。

## 启动顺序

一期联调推荐按下面顺序启动：

1. 启动 MySQL
2. 启动 `graphrag_pipeline/utils/main.py`
3. 根据需要确认 `pdf_ingest/` 和 `graphrag_pipeline/` 根目录、Python 解释器路径已配置
4. 启动 `backend/ckqa-back`

启动 Java 后端：

```bash
cd backend/ckqa-back
./mvnw spring-boot:run
```

## 健康检查说明

`GET /api/v1/system/health` 当前会返回细分子项，而不是单一布尔值，重点包括：

- `mysql`
- `pdf-ingest-root`
- `graphrag-root`
- `graphrag-output`
- `graphrag-api`
- `graphrag-ready`

其中：

- `reachable` 表示依赖可连通或路径存在
- `ready` 表示依赖具备处理真实业务的前提条件

例如 `graphrag-output` 会检查 `GRAPHRAG_ROOT/output` 与 `output/lancedb` 是否存在。

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

轮询任务：

```bash
TASK_ID=$(curl -s -X POST http://127.0.0.1:8080/api/v1/qa-sessions/5/messages \
  -H 'Content-Type: application/json' \
  -d '{"mode":"global","content":"请概括这套图谱的主题"}' | jq -r '.data.taskId')

curl -s http://127.0.0.1:8080/api/v1/qa-sessions/5/tasks/$TASK_ID
```

### 运行测试

```bash
./mvnw test
```

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
- 问答链路已改成异步任务模式，修的是“超时语义”，不是 `global` 查询速度
- Python 任务快照目前仍是进程内内存态，Python 服务重启会导致 Java 把对应任务标记为 `failed`
- `qa_retrieval_hits` 尚未落地
- `system/health` 目前是“就绪前置条件检查”，不是完整语义级问答探活
- Java 侧还没有承接上传链路，仍以已有 `pdf_files` 记录为起点
