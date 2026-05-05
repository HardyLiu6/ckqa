# GraphRAG 索引运行隔离与前端 API 设计稿

> 日期：2026-05-05
> 适用范围：`graphrag_pipeline/`、`backend/ckqa-back/`、`frontend/apps/admin-app/`、`sql/`
> 目标：按“方案 B”把 GraphRAG 索引构建从共享 `input/` / `output/` 改为按 `index_run` 隔离的本地运行目录，并补齐前端可用的知识库、索引运行、索引产物增删查改接口。

## 1. 背景与问题

当前 CKQA 主链路是：

```text
pdf_ingest -> MinIO/MySQL -> graphrag_pipeline/fetch_from_minio.py
  -> graphrag index --root .
  -> graphrag_pipeline/output/*.parquet + output/lancedb/
  -> graphrag_pipeline/utils/main.py
  -> backend/ckqa-back /api/v1
  -> admin-app / student-app
```

这个链路已经能跑通单个知识库构建和问答，但索引产物仍绑定在 `graphrag_pipeline/input/`、`output/`、`cache/`、`reports/` 这一组共享目录上。实际代码中：

1. `GraphRagIndexOrchestrator.fetchInput()` 调用 `python utils/fetch_from_minio.py <course_id> --clean`，没有传入本次运行独立的 `--input-dir`。
2. `GraphRagIndexOrchestrator.runIndex()` 调用 `python -m graphrag index --root .`，没有为本次运行注入独立的 `GRAPHRAG_INPUT_DIR`、`GRAPHRAG_STORAGE_DIR`、`GRAPHRAG_CACHE_DIR`、`GRAPHRAG_REPORTING_DIR`。
3. `graphrag_pipeline/utils/main.py` 在进程启动时只读取一套 `GRAPHRAG_OUTPUT_DIR` / `GRAPHRAG_LANCEDB_URI`，所有查询默认指向同一份输出。
4. Java 后端当前只阻止同一个 `knowledge_base_id` 同时存在 `running` 索引任务，不能阻止不同知识库同时写同一套 GraphRAG 本地目录。

因此，只要两个用户同时为不同知识库触发索引，后启动的任务就可能清空或覆盖前一个任务的输入、parquet、LanceDB、cache 和报告文件。更严重的是，`knowledge_bases.active_index_run_id` 虽然记录了“哪个运行是可用版本”，但当前运行时查询并没有真正按该 run 定位产物目录。

## 2. 设计目标

本设计稿要解决四类问题：

1. **构建隔离**：每个 `index_run` 拥有独立 `input/output/cache/reports/lancedb`，不同知识库或不同用户并发构建互不覆盖。
2. **版本可切换**：成功构建后显式更新 `knowledge_bases.active_index_run_id`，查询总是使用 active run 对应产物，而不是使用“当前目录里的最新文件”。
3. **前端可管理**：Java `/api/v1` 对前端暴露稳定的知识库、索引运行、索引产物 CRUD 或动作接口，前端不直接访问 GraphRAG Python `/v1`。
4. **最小可落地**：首轮继续使用 GraphRAG 3.0.9 CLI 和现有 `settings.yaml` 变量机制，不重写 GraphRAG 内部 API，不把 MinIO 作为热查询目录。

本轮不做：

1. 不实现方案 C 的 MinIO 归档上传与远端恢复。
2. 不引入多机调度器、任务队列或分布式构建服务。
3. 不让浏览器直接传本地文件路径或访问 Python GraphRAG API。
4. 不把 GraphRAG 产物塞进 MySQL；MySQL 只保存控制面和产物元数据。

## 3. 方案比较

### 方案 B1：每次运行独立目录，仅后端内部使用

为每个 `index_run` 创建独立本地目录，后端构建和查询都按 active run 找到目录。前端只能看到运行状态和业务摘要，看不到产物列表。

优点：实现最小。

缺点：前端无法做构建结果诊断、历史版本清理、产物完整性展示。

### 方案 B2：每次运行独立目录，并登记 `index_artifacts`

在 B1 基础上，索引成功后扫描本次运行目录，把 `input_json`、`parquet`、`lancedb`、`report`、`manifest` 等记录写入 `index_artifacts`。前端通过 Java `/api/v1` 查询产物摘要和清理能力。

优点：贴合现有 `index_runs` / `index_artifacts` / `active_index_run_id` 表结构，能支持前端管理。

缺点：需要补充后端 artifact 服务和少量 schema 迁移。

### 方案 B3：为每个 active run 启动独立 GraphRAG API 进程

每个可用知识库启动一个绑定目录的 Python API 服务，Java 后端按知识库路由到不同端口。

优点：Python 侧代码变化小，每个 API 进程天然绑定一套目录。

缺点：端口、生命周期、资源占用和进程治理复杂，超出当前本地最小可运行目标。

推荐采用 **方案 B2**。它在不推翻当前 CLI 包装层的前提下解决真实并发覆盖，并能把必要状态暴露给前端。

## 4. 运行目录规范

新增后端配置：

```properties
ckqa.integration.graphrag.runs-root=${GRAPHRAG_RUNS_ROOT:}
ckqa.integration.graphrag.retention.keep-success-runs=${GRAPHRAG_KEEP_SUCCESS_RUNS:3}
ckqa.integration.graphrag.retention.keep-failed-runs=${GRAPHRAG_KEEP_FAILED_RUNS:3}
```

默认 `runs-root`：

```text
{GRAPHRAG_ROOT}/runtime/index-runs
```

单次运行目录：

```text
runtime/index-runs/
  kb_{knowledge_base_id}/
    run_{index_run_id}/
      input/
      output/
        lancedb/
        *.parquet
      cache/
      reports/
      logs/
      manifest.json
```

目录含义：

| 路径 | 作用 | 是否暴露给前端 |
| --- | --- | --- |
| `input/` | 本次 GraphRAG JSON 输入 | 只暴露摘要，不暴露本地绝对路径 |
| `output/` | GraphRAG parquet 输出根目录 | 只暴露产物类型、大小、状态 |
| `output/lancedb/` | 本次 LanceDB 向量库 | 只暴露“向量库存在/大小/校验状态” |
| `cache/` | 本次 LLM/cache 文件 | 默认不展示明细，可用于排障 |
| `reports/` | GraphRAG 报告 | 可展示报告产物摘要 |
| `logs/` | 过程日志尾部或落盘日志 | 前端通过任务详情查看最新日志摘要 |
| `manifest.json` | 本次运行的稳定元数据快照 | 可转成 `runMetadata` / artifact 摘要展示 |

`runtime/index-runs/` 必须加入忽略规则，不提交 Git。

## 5. GraphRAG 配置策略

保留 `graphrag_pipeline/settings.yaml` 当前变量化方式：

```yaml
input_storage:
  type: file
  base_dir: ${GRAPHRAG_INPUT_DIR}

output_storage:
  type: file
  base_dir: ${GRAPHRAG_STORAGE_DIR}

reporting:
  type: file
  base_dir: ${GRAPHRAG_REPORTING_DIR}

cache:
  type: json
  storage:
    type: file
    base_dir: ${GRAPHRAG_CACHE_DIR}

vector_store:
  type: lancedb
  db_uri: ${GRAPHRAG_STORAGE_DIR}/lancedb
```

索引构建时由 Java `GraphRagIndexOrchestrator` 为子进程注入：

```text
GRAPHRAG_INPUT_DIR={run_root}/input
GRAPHRAG_OUTPUT_DIR={run_root}/output
GRAPHRAG_STORAGE_DIR={run_root}/output
GRAPHRAG_REPORTING_DIR={run_root}/reports
GRAPHRAG_CACHE_DIR={run_root}/cache
GRAPHRAG_LANCEDB_URI={run_root}/output/lancedb
```

命令仍为：

```bash
python -m graphrag index --root .
```

这样保留 GraphRAG CLI 路径，但让每个 CLI 进程读取自己的输入并写自己的输出。

## 6. 输入同步设计

### 6.1 索引运行请求

当前 `POST /api/v1/knowledge-bases/{id}/index-runs` 没有请求体，会按课程拉取输入。改为接受请求体：

```json
{
  "materialIds": [3, 4],
  "jsonFile": "section_docs.json",
  "activateOnSuccess": true,
  "forceRebuild": false
}
```

字段规则：

| 字段 | 默认值 | 说明 |
| --- | --- | --- |
| `materialIds` | 空 | 前端选择的课程资料 ID。为空时后端按该课程已完成 GraphRAG 导出的资料集合自动推断；多资料课程不允许退回旧的非命名空间输入。 |
| `jsonFile` | `section_docs.json` | 首轮只正式支持 `section_docs.json`；`page_docs.json` / `normalized_docs.json` 可作为后续验证输入。 |
| `activateOnSuccess` | `true` | 成功后是否自动切换为 active run。 |
| `forceRebuild` | `false` | 首轮保留字段；不做输入内容指纹复用。 |

### 6.2 多资料同名输入避免覆盖

`fetch_from_minio.py` 当前会把每份资料下载为同一个 `section_docs.json`。如果一个知识库选了多份资料，反复调用会覆盖。需要新增一个最小 CLI 参数：

```bash
python utils/fetch_from_minio.py <course_id> \
  --material-id <material_id> \
  --input-dir <run_root>/input \
  --output-file section_docs.material_<material_id>.json
```

行为：

1. `--output-file` 只控制写入本地 `input/` 的文件名，不改变 MinIO 源文件名。
2. `--clean` 只在本次运行首次同步前执行一次。
3. 每个 `material_id` 生成一个独立 JSON 文件，GraphRAG 的 `input_storage` 会读取 `input/` 下全部 JSON。
4. 如果未传 `materialIds`，后端必须先从课程资料表中解析出可用资料，再按同样方式逐个同步，不再依赖 `course_id/graphrag/section_docs.json` 这种旧的课程级单文件路径。

## 7. 索引构建流程

```text
前端 POST /api/v1/knowledge-bases/{id}/index-runs
  -> Java 校验知识库、课程资料、已有 running 任务
  -> 创建 index_runs(pending)
  -> 计算 run_root
  -> 创建 input/output/cache/reports/logs
  -> 标记 index_runs running
  -> 拉取每份 material 的 GraphRAG 输入到 run_root/input
  -> 写 manifest.json 的初始版本
  -> 注入本次运行环境变量
  -> 执行 python -m graphrag index --root .
  -> 扫描 output/reports/lancedb/input/manifest
  -> 写 index_artifacts
  -> markSuccess / markFailed
  -> activateOnSuccess=true 时更新 knowledge_bases.active_index_run_id
  -> 返回 IndexRunDetailResponse
```

成功切换规则：

1. 只有 `index_runs.status=success` 才能成为 active run。
2. `active_index_run_id` 必须属于同一个 `knowledge_base_id`。
3. active 切换必须在 artifact 写入成功之后。
4. 如果切换失败，`index_run` 保持 `success`，但 `knowledge_bases.active_index_run_id` 不变，并在 `run_metadata.errorSummary` 中记录“索引成功但激活失败”。

失败处理：

1. 输入拉取失败：`status=failed`，保留 `input/` 中已拉取文件和错误 metadata，便于排障。
2. 索引命令失败：`status=failed`，保留 `output/` / `reports/` 残留文件，artifact 可记录为 `status=partial`。
3. 任务超时或后端重启：沿用现有 stale recovery，把运行标记为 failed，并保留 run_root。

## 8. 查询路由设计

前端仍只调用 Java `/api/v1/qa-sessions` 和 `/messages`。Java 后端根据会话绑定知识库读取 `knowledge_bases.active_index_run_id`，再读取该 run 的 `output_dir` / `lancedb` artifact。

Python GraphRAG 内部任务接口增加内部字段：

```json
{
  "mode": "local",
  "prompt": "请解释进程调度的基本思想",
  "indexRunId": 18,
  "dataDir": "/.../runtime/index-runs/kb_5/run_18/output"
}
```

约束：

1. `indexRunId` / `dataDir` 只由 Java 后端传给 Python，浏览器不可直接传。
2. Python 侧校验 `dataDir` 必须位于允许的 `GRAPHRAG_RUNS_ROOT` 之下。
3. 查询命令使用：

```bash
graphrag query --root . --data <run_output_dir> --method <mode> "<prompt>"
```

4. 查询任务环境同时注入：

```text
GRAPHRAG_OUTPUT_DIR={run_root}/output
GRAPHRAG_STORAGE_DIR={run_root}/output
GRAPHRAG_LANCEDB_URI={run_root}/output/lancedb
```

`QueryTaskManager` 需要从“全局 env_factory”演进为“每个任务保存自己的 IndexRunContext”，避免一个 Python API 进程只能绑定一套输出目录。

## 9. 数据库设计

### 9.1 复用现有表

继续使用：

1. `knowledge_bases.active_index_run_id`
2. `index_runs`
3. `index_artifacts`
4. `qa_retrieval_logs.index_run_id`

### 9.2 `index_runs.run_metadata`

建议写入结构化 JSON：

```json
{
  "command": "python -m graphrag index --root .",
  "elapsedSeconds": 542,
  "exitCode": 0,
  "runRoot": "runtime/index-runs/kb_5/run_18",
  "inputDir": "runtime/index-runs/kb_5/run_18/input",
  "outputDir": "runtime/index-runs/kb_5/run_18/output",
  "reportsDir": "runtime/index-runs/kb_5/run_18/reports",
  "cacheDir": "runtime/index-runs/kb_5/run_18/cache",
  "lancedbUri": "runtime/index-runs/kb_5/run_18/output/lancedb",
  "materialIds": [3, 4],
  "jsonFile": "section_docs.json",
  "activateOnSuccess": true,
  "artifactCount": 12,
  "staleTimeoutRecovered": false,
  "errorSummary": null
}
```

对前端响应时不直接暴露本地绝对路径；可暴露相对路径或 `artifactId`。

### 9.3 `index_artifacts`

建议扩展 `artifact_type` 枚举：

```text
input_json
output_dir
parquet
lancedb
report
cache
manifest
log
other
```

建议新增可选字段：

```sql
ALTER TABLE index_artifacts
  ADD COLUMN display_name varchar(255) NULL COMMENT '前端展示名',
  ADD COLUMN storage_scope enum('local','minio') NOT NULL DEFAULT 'local' COMMENT '存储位置类型',
  ADD COLUMN artifact_status enum('ready','partial','missing','deleted') NOT NULL DEFAULT 'ready' COMMENT '产物状态';
```

首轮仍可把 `storage_uri` 写为本地相对路径，例如：

```text
runtime/index-runs/kb_5/run_18/output/lancedb
```

## 10. Java 后端 API 设计

所有响应继续使用统一 envelope：

```json
{
  "code": 200,
  "message": "操作成功",
  "data": {},
  "timestamp": "2026-05-05T20:30:00"
}
```

### 10.1 知识库 CRUD

#### 列表

```http
GET /api/v1/knowledge-bases?page=1&size=20&status=active&keyword=os&courseId=os
```

返回扩展摘要：

```json
{
  "items": [
    {
      "id": 5,
      "courseId": "os",
      "kbCode": "os-main",
      "name": "操作系统主知识库",
      "status": "active",
      "activeIndexRunId": 18,
      "latestIndexRunId": 19,
      "latestIndexRunStatus": "failed",
      "artifactReady": true,
      "canAsk": true,
      "updatedAt": "2026-05-05T20:30:00"
    }
  ],
  "current": 1,
  "size": 20,
  "total": 1,
  "pages": 1
}
```

#### 创建

```http
POST /api/v1/knowledge-bases
```

```json
{
  "courseId": "os",
  "kbCode": "os-main",
  "name": "操作系统主知识库",
  "description": "面向操作系统课程的知识库",
  "status": "draft"
}
```

#### 详情

```http
GET /api/v1/knowledge-bases/{id}
```

详情增加：

```json
{
  "activeIndexRun": {
    "id": 18,
    "status": "success",
    "artifactReady": true
  },
  "latestIndexRun": {
    "id": 19,
    "status": "failed"
  },
  "indexRunCount": 4,
  "successIndexRunCount": 2,
  "canCreateIndexRun": true,
  "canAsk": true
}
```

#### 修改

```http
PATCH /api/v1/knowledge-bases/{id}
```

允许修改：

```json
{
  "name": "操作系统课程主知识库",
  "description": "更新后的说明",
  "status": "active"
}
```

不允许通过此接口直接改 `activeIndexRunId`，active 切换必须使用专门动作接口。

#### 删除或归档

```http
DELETE /api/v1/knowledge-bases/{id}
```

首轮语义为软删除/归档：

1. 若存在 `running` index run，返回 `409`。
2. 若存在 active run，默认只把知识库标记为 `archived`，不删除产物。
3. 若要物理清理产物，后续通过索引运行清理接口执行，避免误删仍需审计的历史产物。

### 10.2 索引运行 API

#### 创建索引运行

```http
POST /api/v1/knowledge-bases/{id}/index-runs
```

```json
{
  "materialIds": [3, 4],
  "jsonFile": "section_docs.json",
  "activateOnSuccess": true,
  "forceRebuild": false
}
```

返回：

```json
{
  "id": 18,
  "knowledgeBaseId": 5,
  "engine": "graphrag",
  "indexVersion": "graphrag-20260505203000",
  "status": "success",
  "startedAt": "2026-05-05T20:30:00",
  "finishedAt": "2026-05-05T20:39:02",
  "materialIds": [3, 4],
  "artifactSummary": {
    "inputJsonCount": 2,
    "parquetCount": 8,
    "hasLancedb": true,
    "hasReports": true
  },
  "activated": true
}
```

#### 知识库下索引运行列表

```http
GET /api/v1/knowledge-bases/{id}/index-runs?status=success&page=1&size=20
```

首轮可以继续返回数组；为了前端分页管理，建议演进为 `ApiPageData<IndexRunSummaryResponse>`。

#### 索引运行详情

```http
GET /api/v1/index-runs/{id}
```

返回：

```json
{
  "id": 18,
  "knowledgeBaseId": 5,
  "engine": "graphrag",
  "indexVersion": "graphrag-20260505203000",
  "status": "success",
  "activeForKnowledgeBase": true,
  "materialIds": [3, 4],
  "runMetadata": {...},
  "artifactSummary": {...},
  "startedAt": "2026-05-05T20:30:00",
  "finishedAt": "2026-05-05T20:39:02"
}
```

#### 更新索引运行状态

```http
PATCH /api/v1/index-runs/{id}
```

首轮只允许：

```json
{
  "status": "archived"
}
```

约束：

1. `running` 不能直接改为 `archived`。
2. active run 不能归档。
3. failed/success 非 active run 可归档。

#### 激活历史成功运行

```http
POST /api/v1/knowledge-bases/{id}/active-index-run
```

```json
{
  "indexRunId": 18
}
```

约束：

1. run 必须属于该知识库。
2. run 必须为 `success`。
3. run 的 `lancedb` 和核心 parquet artifact 必须存在。

#### 删除索引运行

```http
DELETE /api/v1/index-runs/{id}?deleteArtifacts=false
```

首轮语义：

1. 不硬删数据库行，标记 `status=archived`。
2. `deleteArtifacts=true` 时清理本地 run_root，并把对应 artifact 标记为 `deleted`。
3. active run、running run 不允许删除。

### 10.3 索引产物 API

#### 按运行查询产物

```http
GET /api/v1/index-runs/{id}/artifacts
```

返回：

```json
[
  {
    "id": 101,
    "indexRunId": 18,
    "artifactType": "lancedb",
    "displayName": "向量库",
    "storageScope": "local",
    "artifactStatus": "ready",
    "fileSize": 23891234,
    "checksum": null,
    "createdAt": "2026-05-05T20:39:02"
  }
]
```

#### 查询单个产物

```http
GET /api/v1/index-artifacts/{id}
```

返回同上，但不返回服务器绝对路径。

#### 删除单个产物

```http
DELETE /api/v1/index-artifacts/{id}
```

首轮只允许删除非 active run 的非核心产物；`lancedb` 和核心 parquet 应通过 `DELETE /api/v1/index-runs/{id}` 统一清理，避免前端误删后让某个 run 变成半可用。

## 11. 前端交互契约

admin-app 应继续使用 `frontend/apps/admin-app/src/api/client.js` 的 `unwrapApiResponse()` 和 `normalizePageData()`。

建议补齐 API 模块：

```js
listKnowledgeBases(params)
createKnowledgeBase(payload)
getKnowledgeBase(id)
updateKnowledgeBase(id, payload)
deleteKnowledgeBase(id)

createIndexRun(knowledgeBaseId, payload)
listKnowledgeBaseIndexRuns(knowledgeBaseId, params)
getIndexRun(id)
updateIndexRun(id, payload)
deleteIndexRun(id, options)
activateIndexRun(knowledgeBaseId, indexRunId)

listIndexRunArtifacts(indexRunId)
getIndexArtifact(id)
deleteIndexArtifact(id)
```

前端展示原则：

1. 知识库列表展示 `activeIndexRunId`、`latestIndexRunStatus`、`canAsk`、`artifactReady`，避免只显示后端原始状态。
2. 构建向导创建索引时必须传 `materialIds`，并展示本次构建所选资料。
3. 索引运行详情展示“运行状态、输入资料、产物摘要、是否为当前可用版本、最近日志/错误摘要”。
4. 删除按钮默认展示为“归档”或“清理产物”，不要让用户误解为立即删除课程资料或 MinIO 源文件。
5. 前端不展示服务器绝对路径，只展示产物类型、大小、创建时间、状态。

## 12. 权限与状态约束

首轮先沿用当前权限边界，不引入完整 RBAC 实现，但业务约束要在服务层落地：

1. 归档知识库前必须确认没有 `running` 索引运行。
2. 归档 active run 必须先切换到其他 success run 或清空 active run；首轮不支持清空 active run。
3. 创建索引运行时，`materialIds` 必须属于该知识库所在课程。
4. 创建索引运行时，所选资料必须已有 GraphRAG 输入导出产物；缺失时返回明确错误，由前端引导回“导出图谱输入”步骤。
5. 查询问答时，如果 active run 的产物缺失，返回 `KNOWLEDGE_BASE_NOT_READY`，并在详情中给出缺失 artifact 类型。

## 13. 健康检查调整

`GET /api/v1/system/health` 不能再只检查 `GRAPHRAG_ROOT/output` 和 `output/lancedb`。调整为：

1. `graphrag-root`：GraphRAG 项目根目录存在。
2. `graphrag-runs-root`：运行目录存在或可创建。
3. `graphrag-api`：Python API 可访问。
4. `graphrag-active-indexes`：数据库中 active run 的 artifact 是否可读。
5. `graphrag-ready`：至少存在一个可查询的 active run，或返回 `ready=false` 并提示“尚无可用索引”。

这样本地刚清空索引时，系统可以是“服务可运行但知识库未就绪”，不会误报为路径损坏。

## 14. 兼容与迁移

### 14.1 对旧目录的处理

旧 `graphrag_pipeline/input/`、`output/`、`cache/`、`reports/` 保留为手工 CLI 调试路径，不作为 Java 后端构建与前端问答的业务路径。

迁移后：

1. Java 后端新创建的索引运行只写 `runtime/index-runs/...`。
2. GraphRAG API 查询只接收 Java 传入的 active run output。
3. 文档中把旧共享目录标记为“开发调试默认路径”，避免再次作为业务产物目录。

### 14.2 对已有 `active_index_run_id`

如果数据库里已有 active run 但没有 `index_artifacts`：

1. 提供一次性修复脚本或后端维护命令，扫描当前 `graphrag_pipeline/output/` 并登记为某个历史 run 的 artifact。
2. 若无法确定归属，不自动迁移为 active；提示重新构建。

### 14.3 对前端现有调用

保留现有接口：

1. `GET /api/v1/knowledge-bases`
2. `POST /api/v1/knowledge-bases`
3. `GET /api/v1/knowledge-bases/{id}`
4. `POST /api/v1/knowledge-bases/{id}/index-runs`
5. `GET /api/v1/knowledge-bases/{id}/index-runs`
6. `GET /api/v1/index-runs/{id}`

新增字段要向后兼容，前端可渐进读取。

## 15. 测试与验证

### 15.1 Python GraphRAG

1. `fetch_from_minio.py` 新增 `--output-file` 单元测试。
2. 多 material 同步到同一 `input_dir` 时不覆盖文件。
3. Python `/v1/query-tasks` 支持每任务 `dataDir`，并拒绝 runs-root 外路径。
4. `graphrag query` 命令构造包含 `--data <run_output_dir>`。

建议命令：

```bash
cd graphrag_pipeline
conda run -n graphrag-oneapi python -m pytest tests/
```

### 15.2 Java 后端

1. `GraphRagIndexOrchestrator` 对不同 run 注入不同目录环境变量。
2. `IndexWorkflowService` 创建 run_root、拉取多 material、写 artifact、激活 success run。
3. active run 查询上下文正确传给 `GraphRagTaskClient`。
4. 知识库 CRUD、索引运行 CRUD、artifact 查询/清理接口覆盖 WebMvc 测试。
5. active run 不能归档或删除。

建议命令：

```bash
cd backend/ckqa-back
./mvnw test
./mvnw -DskipTests compile
```

### 15.3 前端 admin-app

1. API 模块测试覆盖新增 CRUD 函数。
2. 知识库列表与详情能读取 `artifactReady` / `canAsk`。
3. 构建向导创建索引时传 `materialIds`。
4. 索引运行详情显示产物摘要和 active 状态。

建议命令：

```bash
cd frontend/apps/admin-app
pnpm test
pnpm build
```

### 15.4 集成烟测

最小真实验证：

1. 选两个不同知识库或两个不同 `knowledge_base_id`。
2. 并发触发两次 `POST /api/v1/knowledge-bases/{id}/index-runs`。
3. 确认生成两个不同 run_root。
4. 确认各自 `index_artifacts` 指向不同目录。
5. 确认各自 `active_index_run_id` 可独立切换。
6. 分别发起 QA smoke，确认 Java 传入不同 active run output。

## 16. 实施切分建议

后续实施计划应分为四步：

1. **后端存储隔离基础**：新增 runs-root 配置、run_root 计算、索引环境变量注入、`fetch_from_minio.py --output-file`。
2. **产物登记与 active 查询**：补 `index_artifacts` 服务、artifact 扫描、Python query-task 每任务 dataDir、Java QA 传 active run context。
3. **前端 CRUD 契约**：补知识库 PATCH/DELETE、index run PATCH/DELETE/activate、artifact GET/DELETE 与 admin-app API 模块。
4. **文档与回归**：更新 README、AGENTS、模块 README，跑 Python/Java/admin-app 验证和 repo drift audit。

## 17. 验收标准

设计完成后的实现应满足：

1. 不同知识库并发索引不会互相覆盖本地输入或输出。
2. 同一知识库历史 success run 可以被重新激活。
3. 问答查询读取的是 `active_index_run_id` 对应 run 的产物。
4. 前端能完成知识库创建、查看、修改、归档；索引运行创建、查看、归档/清理、激活；产物查看和受控清理。
5. 系统健康检查能区分“GraphRAG 服务可运行”和“某知识库已有可用索引”。
6. MinIO 仍只保存 pdf_ingest 导出的源产物和 GraphRAG 输入源；GraphRAG 索引产物首轮保留在本地 run_root，不做远端上传。
