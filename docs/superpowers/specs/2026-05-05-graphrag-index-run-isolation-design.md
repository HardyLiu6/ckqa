# GraphRAG 知识库构建流水线隔离与前端 API 设计稿

> 日期：2026-05-05
> 适用范围：`graphrag_pipeline/`、`backend/ckqa-back/`、`frontend/apps/admin-app/`、`sql/`
> 目标：把“每个用户发起的每个知识库构建流水线”从共享目录和共享临时状态中隔离出来，覆盖资料选择、解析/导出确认、Prompt 策略、GraphRAG 索引、产物登记、问答验证全过程，并补齐前端可用的增删查改和动作接口。

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

前一版设计重点解决 `graphrag index` 写共享 `input/`、`output/`、`cache/`、`reports/` 的问题。这个判断是必要的，但还不够完整。知识库构建不是单一索引命令，而是一条由用户触发、前端向导推进、Java 编排、Python CLI 执行、MinIO/MySQL 持久化共同组成的流水线。

当前风险包括：

1. `GraphRagIndexOrchestrator.fetchInput()` 调用 `python utils/fetch_from_minio.py <course_id> --clean`，没有传入本次运行独立的 `--input-dir`。
2. `GraphRagIndexOrchestrator.runIndex()` 调用 `python -m graphrag index --root .`，没有为本次运行注入独立的 `GRAPHRAG_INPUT_DIR`、`GRAPHRAG_STORAGE_DIR`、`GRAPHRAG_CACHE_DIR`、`GRAPHRAG_REPORTING_DIR`。
3. `graphrag_pipeline/utils/main.py` 在进程启动时只读取一套 `GRAPHRAG_OUTPUT_DIR` / `GRAPHRAG_LANCEDB_URI`，所有查询默认指向同一份输出。
4. admin-app 构建向导当前主要靠 URL query 和前端状态表达资料选择、确认状态和步骤推进，后端没有一个持久的“本次构建流水线”对象。
5. `index_runs` 只能表达索引阶段，不能表达用户 A 和用户 B 同时为同一个知识库或不同知识库发起的两条构建流水线。
6. 多资料构建时，如果每份资料都写成本地 `section_docs.json`，即使索引目录隔离，也可能在同一目录内被后一次输入同步覆盖。

因此，隔离边界应提升为：

```text
user_id + knowledge_base_id + build_run_id
```

`index_run` 是这条 build run 的一个阶段，不是完整构建流水线本身。

## 2. 设计目标

本设计稿要解决五类问题：

1. **流水线隔离**：每个用户发起的每次知识库构建都有独立 `build_run` 控制记录和本地 workspace，资料选择、导出输入副本、Prompt 快照、索引产物、日志、QA 冒烟验证互不覆盖。
2. **阶段可追踪**：解析、导出、Prompt、索引、问答验证都挂到同一个 build run 下，前端看到的是一条完整流水线，而不是散落的单点任务。
3. **索引可切换**：索引成功后生成独立 `index_run` 和 `index_artifacts`，只有显式激活的 success run 才能成为 `knowledge_bases.active_index_run_id`。
4. **前端可管理**：Java `/api/v1` 对前端暴露知识库、构建流水线、索引运行、索引产物的 CRUD 和动作接口，浏览器不直接访问 GraphRAG Python `/v1`。
5. **最小可落地**：首轮继续使用 GraphRAG 3.0.9 CLI、现有 `settings.yaml` 变量机制和现有 pdf_ingest MinIO 产物，不重写 GraphRAG 内部 API，不把 MinIO 作为热查询目录。

本轮不做：

1. 不实现方案 C 的 GraphRAG 索引产物 MinIO 归档上传与远端恢复。
2. 不引入多机调度器、消息队列或独立构建服务。
3. 不让浏览器直接传本地文件路径或访问 Python GraphRAG API。
4. 不把 GraphRAG parquet、LanceDB 或长日志塞进 MySQL；MySQL 只保存控制面和产物元数据。
5. 不把课程资料解析结果从现有 `course_materials` / `parse_results` / MinIO 命名空间迁走；这些仍是上游事实源。

## 3. 核心对象模型

### 3.1 三层运行对象

```text
knowledge_bases
  -> knowledge_base_build_runs   用户发起的一次完整构建流水线
       -> index_runs             该流水线中的 GraphRAG 索引阶段
            -> index_artifacts   该索引运行产生的本地产物
       -> qa_sessions / qa_retrieval_logs   该流水线的问答验证阶段
```

对象职责：

| 对象 | 职责 | 是否前端可管理 |
| --- | --- | --- |
| `knowledge_bases` | 知识库业务实体和 active index 指针 | 是 |
| `knowledge_base_build_runs` | 一次用户发起的构建流水线，记录选择、阶段、workspace、结果 | 是 |
| `index_runs` | GraphRAG 索引执行记录，属于某个 build run 或历史兼容直接运行 | 是 |
| `index_artifacts` | 索引运行产物索引，不保存大文件 | 是 |
| `qa_sessions` / `qa_retrieval_logs` | 问答验证和正式问答记录 | 是，按现有 QA 接口延续 |

### 3.2 隔离粒度

首轮隔离粒度固定为一次 build run：

```text
user_id + knowledge_base_id + build_run_id
```

含义：

1. 同一用户连续构建同一知识库，得到不同 build run，互不覆盖。
2. 不同用户同时构建同一知识库，得到不同 build run，互不覆盖；是否允许同时激活由后端 active 切换规则控制。
3. 不同知识库同时构建，天然分到不同 workspace。
4. 同一 build run 内的索引阶段生成一个或多个 `index_runs`；首轮限制为一个 build run 同时最多一个 running index run。

## 4. 方案比较

### 方案 B1：只隔离 index run

每个 `index_run` 创建独立本地目录，后端索引和查询都按 active run 找目录。

优点：实现最小。

缺点：资料选择、Prompt 确认、导出输入副本、QA 验证仍没有后端流水线对象；前端无法表达“这次构建”的完整状态。

### 方案 B2：隔离 build run，index run 作为其中一个阶段

新增 `knowledge_base_build_runs`，每次用户发起构建时创建独立 workspace。索引阶段仍创建 `index_runs`，并通过 `build_run_id` 关联回完整流水线。

优点：隔离范围和真实业务流程一致，前端向导能直接绑定后端 build run，后续实现 MinIO 归档也有明确归属。

缺点：需要新增表、DTO、控制器和前端 API 模块。

### 方案 B3：每个 active run 启动独立 GraphRAG API 进程

每个可用知识库启动一个绑定目录的 Python API 服务，Java 后端按知识库路由到不同端口。

优点：Python 侧查询代码变化较小。

缺点：端口、生命周期、资源占用和进程治理复杂，不适合当前本地最小闭环。

推荐采用 **方案 B2**。它把隔离边界放在用户构建流水线，而不是单个索引命令上，能避免后续继续在前端状态、临时输入和 QA 验证上互相干扰。

## 5. 运行目录规范

新增后端配置：

```properties
ckqa.integration.graphrag.build-runs-root=${GRAPHRAG_BUILD_RUNS_ROOT:}
ckqa.integration.graphrag.retention.keep-success-build-runs=${GRAPHRAG_KEEP_SUCCESS_BUILD_RUNS:3}
ckqa.integration.graphrag.retention.keep-failed-build-runs=${GRAPHRAG_KEEP_FAILED_BUILD_RUNS:3}
```

默认 `build-runs-root`：

```text
{GRAPHRAG_ROOT}/runtime/kb-build-runs
```

单次构建流水线 workspace：

```text
runtime/kb-build-runs/
  user_{user_id}/
    kb_{knowledge_base_id}/
      build_{build_run_id}/
        selection/
          selected_materials.json
        parse/
          requested_tasks.json
          status_snapshot.json
        graph-input/
          material_{material_id}.section_docs.json
          material_{material_id}.page_docs.json
          export_snapshot.json
        prompt/
          active_prompt_snapshot.json
          prompt_decision.json
        index/
          input/
          output/
            lancedb/
            *.parquet
          cache/
          reports/
          logs/
        qa-smoke/
          request.json
          response.json
          latest_logs.txt
        manifest.json
```

目录含义：

| 路径 | 作用 | 是否暴露给前端 |
| --- | --- | --- |
| `selection/` | 本次构建选择的课程资料快照 | 暴露资料 ID、名称、状态摘要 |
| `parse/` | 本次构建触发或读取的解析状态快照 | 暴露解析状态和错误摘要 |
| `graph-input/` | 从 MinIO 拉取并重命名后的 GraphRAG 输入副本 | 暴露数量、资料映射、完整性 |
| `prompt/` | 本次构建使用的 Prompt 策略快照 | 暴露策略名、确认状态 |
| `index/input/` | GraphRAG CLI 本次索引读取目录 | 只暴露摘要 |
| `index/output/` | 本次 GraphRAG parquet 输出根目录 | 暴露产物类型、大小、状态 |
| `index/output/lancedb/` | 本次 LanceDB 向量库 | 暴露存在性、大小、校验状态 |
| `index/cache/` | 本次索引 LLM/cache 文件 | 默认不展示明细 |
| `index/reports/` | GraphRAG 报告 | 可展示报告产物摘要 |
| `index/logs/` | 索引阶段日志 | 前端通过任务详情查看尾部 |
| `qa-smoke/` | 本次构建的问答验证输入和结果 | 暴露验证状态、问题、回答摘要 |
| `manifest.json` | build run 稳定元数据快照 | 转成 `runMetadata` / artifact 摘要展示 |

`runtime/kb-build-runs/` 必须加入忽略规则，不提交 Git。

## 6. GraphRAG 配置策略

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

索引阶段由 Java `GraphRagIndexOrchestrator` 为子进程注入：

```text
GRAPHRAG_INPUT_DIR={build_workspace}/index/input
GRAPHRAG_OUTPUT_DIR={build_workspace}/index/output
GRAPHRAG_STORAGE_DIR={build_workspace}/index/output
GRAPHRAG_REPORTING_DIR={build_workspace}/index/reports
GRAPHRAG_CACHE_DIR={build_workspace}/index/cache
GRAPHRAG_LANCEDB_URI={build_workspace}/index/output/lancedb
```

命令仍为：

```bash
python -m graphrag index --root .
```

这样保留 GraphRAG CLI 路径，但让每个 CLI 进程读取自己的输入并写自己的输出。

## 7. 构建流水线阶段设计

首轮 build run 阶段：

| 阶段 | stage | 本地隔离内容 | 后端事实源 |
| --- | --- | --- | --- |
| 选择资料 | `material_selection` | `selection/selected_materials.json` | `course_materials`、`material_objects` |
| 解析检查/触发 | `parse` | `parse/status_snapshot.json` | `course_materials.parse_status`、`parse_results`、`parse_logs` |
| 导出图谱输入 | `graph_input_export` | `graph-input/*.json`、`export_snapshot.json` | MinIO `course-artifacts/{course}/graphrag/material_{id}/...` |
| Prompt 策略确认 | `prompt` | `prompt/prompt_decision.json`、`active_prompt_snapshot.json` | `graphrag_pipeline/.env`、`prompts/final/active_prompt.json` |
| 创建索引 | `index` | `index/input`、`index/output`、`index/cache`、`index/reports` | `index_runs`、`index_artifacts` |
| 问答验证 | `qa_smoke` | `qa-smoke/request.json`、`response.json` | `qa_sessions`、`qa_messages`、`qa_retrieval_logs` |
| 完成 | `done` | `manifest.json` 终态 | `knowledge_bases.active_index_run_id` |

阶段推进规则：

1. 每个阶段只读取本 build run 的 workspace 和数据库中明确关联的业务事实。
2. 共享上游事实源可以复用，例如同一 `course_material_id` 的解析结果和 MinIO 导出产物，但本 build run 必须保存自己的选择快照和输入副本。
3. 前端切换页面、刷新或另一个用户同时构建时，必须通过 `buildRunId` 恢复状态，而不是依赖浏览器 sessionStorage 作为唯一状态源。
4. active index 的切换只发生在索引产物登记成功之后。

## 8. 输入同步设计

### 8.1 创建 build run

新增接口：

```http
POST /api/v1/knowledge-bases/{id}/build-runs
```

请求：

```json
{
  "materialIds": [3, 4],
  "jsonFile": "section_docs.json",
  "promptStrategy": "active",
  "activateOnSuccess": true
}
```

字段规则：

| 字段 | 默认值 | 说明 |
| --- | --- | --- |
| `materialIds` | 空 | 前端选择的课程资料 ID。为空时后端按该课程已完成 GraphRAG 导出的资料集合自动推断；多资料课程不允许退回旧的非命名空间输入。 |
| `jsonFile` | `section_docs.json` | 首轮正式支持 `section_docs.json`；`page_docs.json` / `normalized_docs.json` 可作为后续验证输入。 |
| `promptStrategy` | `active` | 使用当前活动 Prompt。首轮不在这里做候选调优，只记录快照。 |
| `activateOnSuccess` | `true` | 索引成功后是否自动切换为 active run。 |

创建后立即生成 workspace 和 `selection/selected_materials.json`，并返回 `buildRunId`。

### 8.2 多资料同名输入避免覆盖

`fetch_from_minio.py` 当前会把每份资料下载为同一个 `section_docs.json`。需要新增最小 CLI 参数：

```bash
python utils/fetch_from_minio.py <course_id> \
  --material-id <material_id> \
  --input-dir <build_workspace>/graph-input \
  --output-file material_<material_id>.section_docs.json
```

行为：

1. `--output-file` 只控制写入本地目录的文件名，不改变 MinIO 源文件名。
2. 每个 `material_id` 生成独立 JSON 文件。
3. GraphRAG 索引前，后端把 `graph-input/` 中本次确认的文件复制或链接到 `index/input/`。
4. `index/input/` 只包含本 build run 确认过的输入，不读取共享 `graphrag_pipeline/input/`。

## 9. 索引构建流程

```text
前端 POST /api/v1/knowledge-bases/{id}/build-runs
  -> Java 创建 knowledge_base_build_runs(pending)
  -> 创建 build workspace
  -> 保存资料选择快照

前端 POST /api/v1/knowledge-base-build-runs/{buildRunId}/graph-input
  -> Java 校验资料属于该知识库课程
  -> 按 material_id 拉取 GraphRAG 输入到 graph-input/
  -> 写 export_snapshot.json
  -> 标记 build_run.stage=graph_input_export

前端 POST /api/v1/knowledge-base-build-runs/{buildRunId}/prompt-confirmation
  -> Java 保存 Prompt 快照和确认结果
  -> 标记 build_run.stage=prompt

前端 POST /api/v1/knowledge-base-build-runs/{buildRunId}/index-runs
  -> Java 创建 index_runs(pending, build_run_id)
  -> 准备 index/input, index/output, index/cache, index/reports, index/logs
  -> 把 graph-input 中确认的 JSON 放入 index/input
  -> 标记 index_runs running, build_run.stage=index
  -> 注入本次运行环境变量
  -> 执行 python -m graphrag index --root .
  -> 扫描 index/output/reports/lancedb/input/manifest
  -> 写 index_artifacts
  -> markSuccess / markFailed
  -> activateOnSuccess=true 时更新 knowledge_bases.active_index_run_id
  -> 更新 build_run 终态或等待 qa_smoke
```

成功切换规则：

1. 只有 `index_runs.status=success` 才能成为 active run。
2. `index_runs.knowledge_base_id` 和 `knowledge_base_build_runs.knowledge_base_id` 必须一致。
3. `active_index_run_id` 必须属于同一个 `knowledge_base_id`。
4. active 切换必须在 artifact 写入成功之后。
5. 如果切换失败，`index_run` 保持 `success`，但 `knowledge_bases.active_index_run_id` 不变，并在 metadata 中记录“索引成功但激活失败”。

失败处理：

1. 输入拉取失败：`build_run.status=failed` 或阶段状态为 `failed`，保留 `graph-input/` 中已拉取文件和错误 metadata。
2. 索引命令失败：`index_run.status=failed`，保留 `index/output`、`index/reports` 残留文件，artifact 可记录为 `partial`。
3. QA 验证失败：不影响已成功的 index run，但 `build_run.qaStatus=failed`，前端可选择重新验证或完成构建。
4. 后端重启：沿用 stale recovery，把超时的 running index run 标记为 failed；build run 根据最新阶段恢复为 `failed` 或 `interrupted`。

## 10. 查询路由设计

前端仍只调用 Java `/api/v1/qa-sessions` 和 `/messages`。Java 后端根据会话绑定知识库读取 `knowledge_bases.active_index_run_id`，再读取该 run 的 `output_dir` / `lancedb` artifact。

Python GraphRAG 内部任务接口增加内部字段：

```json
{
  "mode": "local",
  "prompt": "请解释进程调度的基本思想",
  "indexRunId": 18,
  "dataDir": "/.../runtime/kb-build-runs/user_2/kb_5/build_27/index/output"
}
```

约束：

1. `indexRunId` / `dataDir` 只由 Java 后端传给 Python，浏览器不可直接传。
2. Python 侧校验 `dataDir` 必须位于允许的 `GRAPHRAG_BUILD_RUNS_ROOT` 之下。
3. 查询命令使用：

```bash
graphrag query --root . --data <run_output_dir> --method <mode> "<prompt>"
```

4. 查询任务环境同时注入：

```text
GRAPHRAG_OUTPUT_DIR={build_workspace}/index/output
GRAPHRAG_STORAGE_DIR={build_workspace}/index/output
GRAPHRAG_LANCEDB_URI={build_workspace}/index/output/lancedb
```

`QueryTaskManager` 需要从“全局 env_factory”演进为“每个任务保存自己的 IndexRunContext”，避免一个 Python API 进程只能绑定一套输出目录。

## 11. 数据库设计

### 11.1 新增 `knowledge_base_build_runs`

建议新增表：

```sql
CREATE TABLE knowledge_base_build_runs (
  id bigint NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  knowledge_base_id bigint NOT NULL COMMENT '知识库ID',
  course_id varchar(64) NOT NULL COMMENT '课程ID快照',
  requested_by_user_id bigint NULL COMMENT '发起用户ID',
  build_version varchar(64) NOT NULL COMMENT '构建版本',
  status enum('pending','running','success','failed','archived') NOT NULL DEFAULT 'pending' COMMENT '流水线状态',
  current_stage enum('material_selection','parse','graph_input_export','prompt','index','qa_smoke','done') NOT NULL DEFAULT 'material_selection' COMMENT '当前阶段',
  selected_material_ids json DEFAULT NULL COMMENT '本次构建资料选择快照',
  active_index_run_id bigint NULL COMMENT '本次构建最终激活的索引运行',
  workspace_uri varchar(512) NULL COMMENT '本地工作区相对路径',
  build_metadata json DEFAULT NULL COMMENT '构建元数据',
  started_at timestamp NULL DEFAULT NULL COMMENT '开始时间',
  finished_at timestamp NULL DEFAULT NULL COMMENT '结束时间',
  created_at timestamp NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  updated_at timestamp NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (id),
  UNIQUE KEY uk_kb_build_version (knowledge_base_id, build_version),
  KEY idx_kb_build_status (knowledge_base_id, status),
  KEY idx_kb_build_user_status (requested_by_user_id, status),
  CONSTRAINT fk_kb_build_runs_kb FOREIGN KEY (knowledge_base_id) REFERENCES knowledge_bases(id)
);
```

`requested_by_user_id` 首轮可为空，用于兼容当前 dev 身份和未完全接入登录的场景；前端或后端身份接入稳定后再改成必填。

### 11.2 扩展 `index_runs`

建议新增：

```sql
ALTER TABLE index_runs
  ADD COLUMN build_run_id bigint NULL COMMENT '所属知识库构建流水线ID',
  ADD INDEX idx_index_runs_build_run (build_run_id),
  ADD CONSTRAINT fk_index_runs_build_run
    FOREIGN KEY (build_run_id) REFERENCES knowledge_base_build_runs(id);
```

兼容规则：

1. 历史 index run 的 `build_run_id` 可以为空。
2. 新建 index run 必须关联 build run。
3. 直接调用旧 `POST /api/v1/knowledge-bases/{id}/index-runs` 时，后端可自动创建一个兼容 build run，再创建 index run。

### 11.3 `index_runs.run_metadata`

建议写入结构化 JSON：

```json
{
  "command": "python -m graphrag index --root .",
  "elapsedSeconds": 542,
  "exitCode": 0,
  "buildRunId": 27,
  "workspace": "runtime/kb-build-runs/user_2/kb_5/build_27",
  "inputDir": "runtime/kb-build-runs/user_2/kb_5/build_27/index/input",
  "outputDir": "runtime/kb-build-runs/user_2/kb_5/build_27/index/output",
  "reportsDir": "runtime/kb-build-runs/user_2/kb_5/build_27/index/reports",
  "cacheDir": "runtime/kb-build-runs/user_2/kb_5/build_27/index/cache",
  "lancedbUri": "runtime/kb-build-runs/user_2/kb_5/build_27/index/output/lancedb",
  "materialIds": [3, 4],
  "jsonFile": "section_docs.json",
  "activateOnSuccess": true,
  "artifactCount": 12,
  "staleTimeoutRecovered": false,
  "errorSummary": null
}
```

对前端响应时不直接暴露本地绝对路径；可暴露相对路径或 `artifactId`。

### 11.4 `index_artifacts`

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
qa_smoke
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
runtime/kb-build-runs/user_2/kb_5/build_27/index/output/lancedb
```

## 12. Java 后端 API 设计

所有响应继续使用统一 envelope：

```json
{
  "code": 200,
  "message": "操作成功",
  "data": {},
  "timestamp": "2026-05-05T20:30:00"
}
```

### 12.1 知识库 CRUD

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
      "latestBuildRunId": 27,
      "latestBuildRunStatus": "success",
      "latestIndexRunId": 18,
      "latestIndexRunStatus": "success",
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
  "latestBuildRun": {
    "id": 27,
    "status": "success",
    "currentStage": "done"
  },
  "latestIndexRun": {
    "id": 18,
    "status": "success"
  },
  "buildRunCount": 4,
  "indexRunCount": 4,
  "successIndexRunCount": 2,
  "canCreateBuildRun": true,
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

1. 若存在 `running` build run 或 index run，返回 `409`。
2. 若存在 active run，默认只把知识库标记为 `archived`，不删除产物。
3. 若要物理清理产物，后续通过 build run 或 index run 清理接口执行，避免误删仍需审计的历史产物。

### 12.2 构建流水线 API

#### 创建构建流水线

```http
POST /api/v1/knowledge-bases/{id}/build-runs
```

```json
{
  "materialIds": [3, 4],
  "jsonFile": "section_docs.json",
  "promptStrategy": "active",
  "activateOnSuccess": true
}
```

返回：

```json
{
  "id": 27,
  "knowledgeBaseId": 5,
  "courseId": "os",
  "requestedByUserId": 2,
  "buildVersion": "build-20260505203000",
  "status": "pending",
  "currentStage": "material_selection",
  "selectedMaterialIds": [3, 4],
  "canContinue": true,
  "createdAt": "2026-05-05T20:30:00"
}
```

#### 查询知识库下构建流水线

```http
GET /api/v1/knowledge-bases/{id}/build-runs?status=running&page=1&size=20
```

返回 `ApiPageData<BuildRunSummaryResponse>`。

#### 查询构建流水线详情

```http
GET /api/v1/knowledge-base-build-runs/{id}
```

返回：

```json
{
  "id": 27,
  "knowledgeBaseId": 5,
  "status": "running",
  "currentStage": "index",
  "selectedMaterialIds": [3, 4],
  "stageSummary": {
    "materialSelection": "success",
    "parse": "success",
    "graphInputExport": "success",
    "prompt": "success",
    "index": "running",
    "qaSmoke": "pending"
  },
  "latestIndexRunId": 18,
  "artifactSummary": {
    "inputJsonCount": 2,
    "parquetCount": 0,
    "hasLancedb": false
  },
  "canArchive": false,
  "canDeleteArtifacts": false
}
```

#### 修改构建流水线

```http
PATCH /api/v1/knowledge-base-build-runs/{id}
```

首轮只允许：

```json
{
  "status": "archived"
}
```

约束：

1. running build run 不能直接归档。
2. 包含 active index run 的 build run 不能归档或清理。
3. failed/success 非 active build run 可归档。

#### 删除或清理构建流水线

```http
DELETE /api/v1/knowledge-base-build-runs/{id}?deleteWorkspace=false
```

首轮语义：

1. 不硬删数据库行，默认标记 `status=archived`。
2. `deleteWorkspace=true` 时清理本地 workspace，并把关联 artifact 标记为 `deleted`。
3. active run、running run 不允许删除。

### 12.3 构建阶段动作 API

#### 更新资料选择

```http
PUT /api/v1/knowledge-base-build-runs/{id}/material-selection
```

```json
{
  "materialIds": [3, 4]
}
```

#### 触发或刷新解析状态

```http
POST /api/v1/knowledge-base-build-runs/{id}/parse-check
```

```json
{
  "parseMissing": true
}
```

#### 导出或同步图谱输入

```http
POST /api/v1/knowledge-base-build-runs/{id}/graph-input
```

```json
{
  "jsonFile": "section_docs.json",
  "exportMissing": true
}
```

#### 确认 Prompt 策略

```http
POST /api/v1/knowledge-base-build-runs/{id}/prompt-confirmation
```

```json
{
  "promptStrategy": "active",
  "confirmed": true
}
```

#### 创建索引运行

```http
POST /api/v1/knowledge-base-build-runs/{id}/index-runs
```

```json
{
  "activateOnSuccess": true,
  "forceRebuild": false
}
```

#### 发起问答验证

```http
POST /api/v1/knowledge-base-build-runs/{id}/qa-smoke
```

```json
{
  "question": "请用一句话概括当前知识库的主要内容。",
  "mode": "basic"
}
```

### 12.4 索引运行 API

保留既有接口，并扩展响应：

```http
GET /api/v1/knowledge-bases/{id}/index-runs?status=success&page=1&size=20
GET /api/v1/index-runs/{id}
PATCH /api/v1/index-runs/{id}
DELETE /api/v1/index-runs/{id}?deleteArtifacts=false
POST /api/v1/knowledge-bases/{id}/active-index-run
```

兼容旧创建接口：

```http
POST /api/v1/knowledge-bases/{id}/index-runs
```

该接口首轮继续可用，但内部应自动创建一个 build run，再在该 build run 下创建 index run。前端新代码应优先使用 build run 接口。

### 12.5 索引产物 API

```http
GET /api/v1/index-runs/{id}/artifacts
GET /api/v1/index-artifacts/{id}
DELETE /api/v1/index-artifacts/{id}
```

产物响应不返回服务器绝对路径：

```json
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
```

## 13. 前端交互契约

admin-app 应继续使用 `frontend/apps/admin-app/src/api/client.js` 的 `unwrapApiResponse()` 和 `normalizePageData()`。

建议补齐 API 模块：

```js
listKnowledgeBases(params)
createKnowledgeBase(payload)
getKnowledgeBase(id)
updateKnowledgeBase(id, payload)
deleteKnowledgeBase(id)

createBuildRun(knowledgeBaseId, payload)
listKnowledgeBaseBuildRuns(knowledgeBaseId, params)
getBuildRun(id)
updateBuildRun(id, payload)
deleteBuildRun(id, options)
updateBuildRunMaterialSelection(id, payload)
checkBuildRunParse(id, payload)
syncBuildRunGraphInput(id, payload)
confirmBuildRunPrompt(id, payload)
createBuildRunIndexRun(id, payload)
runBuildRunQaSmoke(id, payload)

createIndexRun(knowledgeBaseId, payload) // 兼容旧入口
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

1. 构建向导进入时先创建或恢复 `buildRunId`，所有步骤都围绕后端 build run 状态推进。
2. sessionStorage 只做临时体验优化，不能作为唯一状态源。
3. 知识库列表展示 `latestBuildRunStatus`、`activeIndexRunId`、`latestIndexRunStatus`、`canAsk`、`artifactReady`。
4. 构建向导创建索引时不再直接传 `materialIds` 给 index endpoint，而是先把资料选择写入 build run。
5. 索引运行详情展示“所属 build run、运行状态、输入资料、产物摘要、是否为当前可用版本、最近日志/错误摘要”。
6. 删除按钮默认展示为“归档”或“清理工作区”，不要让用户误解为立即删除课程资料或 MinIO 源文件。
7. 前端不展示服务器绝对路径，只展示产物类型、大小、创建时间、状态。

## 14. 权限与状态约束

首轮先沿用当前权限边界，不引入完整 RBAC 实现，但业务约束要在服务层落地：

1. 创建 build run 时，`materialIds` 必须属于该知识库所在课程。
2. 同一知识库可以同时存在多个 build run，但同一个 build run 内最多一个 running index run。
3. 是否限制“同一知识库同时只有一个 running build run”由后端配置控制；默认允许并发构建但只允许显式 active 切换。
4. 归档知识库前必须确认没有 running build run 或 running index run。
5. 归档 active run 所属 build run 必须先切换到其他 success run；首轮不支持清空 active run。
6. 创建索引运行时，所选资料必须已有 GraphRAG 输入导出产物；缺失时返回明确错误，由前端引导回“导出图谱输入”步骤。
7. 查询问答时，如果 active run 的产物缺失，返回 `KNOWLEDGE_BASE_NOT_READY`，并在详情中给出缺失 artifact 类型。

## 15. 健康检查调整

`GET /api/v1/system/health` 不能再只检查 `GRAPHRAG_ROOT/output` 和 `output/lancedb`。调整为：

1. `graphrag-root`：GraphRAG 项目根目录存在。
2. `graphrag-build-runs-root`：构建运行目录存在或可创建。
3. `graphrag-api`：Python API 可访问。
4. `graphrag-active-indexes`：数据库中 active run 的 artifact 是否可读。
5. `graphrag-ready`：至少存在一个可查询的 active run，或返回 `ready=false` 并提示“尚无可用索引”。

这样本地刚清空索引时，系统可以是“服务可运行但知识库未就绪”，不会误报为路径损坏。

## 16. 兼容与迁移

### 16.1 对旧目录的处理

旧 `graphrag_pipeline/input/`、`output/`、`cache/`、`reports/` 保留为手工 CLI 调试路径，不作为 Java 后端构建与前端问答的业务路径。

迁移后：

1. Java 后端新创建的 build run 只写 `runtime/kb-build-runs/...`。
2. GraphRAG API 查询只接收 Java 传入的 active run output。
3. 文档中把旧共享目录标记为“开发调试默认路径”，避免再次作为业务产物目录。

### 16.2 对已有 `active_index_run_id`

如果数据库里已有 active run 但没有 `index_artifacts`：

1. 提供一次性修复脚本或后端维护命令，扫描当前 `graphrag_pipeline/output/` 并登记为某个历史 run 的 artifact。
2. 若无法确定归属，不自动迁移为 active；提示重新构建。
3. 可为这类历史 run 创建 `build_run_id=NULL` 的兼容响应，前端显示为“历史索引运行”。

### 16.3 对前端现有调用

保留现有接口：

1. `GET /api/v1/knowledge-bases`
2. `POST /api/v1/knowledge-bases`
3. `GET /api/v1/knowledge-bases/{id}`
4. `POST /api/v1/knowledge-bases/{id}/index-runs`
5. `GET /api/v1/knowledge-bases/{id}/index-runs`
6. `GET /api/v1/index-runs/{id}`

新增字段要向后兼容，前端可渐进读取。新构建向导应迁到 build run API。

## 17. 测试与验证

### 17.1 Python GraphRAG

1. `fetch_from_minio.py` 新增 `--output-file` 单元测试。
2. 多 material 同步到同一 `graph-input` 时不覆盖文件。
3. Python `/v1/query-tasks` 支持每任务 `dataDir`，并拒绝 build-runs-root 外路径。
4. `graphrag query` 命令构造包含 `--data <run_output_dir>`。

建议命令：

```bash
cd graphrag_pipeline
conda run -n graphrag-oneapi python -m pytest tests/
```

### 17.2 Java 后端

1. `KnowledgeBaseBuildRunService` 创建 workspace、写资料选择快照、推进阶段。
2. `GraphRagIndexOrchestrator` 对不同 build run 注入不同目录环境变量。
3. `IndexWorkflowService` 在 build run 下创建 index run、拉取多 material、写 artifact、激活 success run。
4. active run 查询上下文正确传给 `GraphRagTaskClient`。
5. build run CRUD、知识库 CRUD、索引运行 CRUD、artifact 查询/清理接口覆盖 WebMvc 测试。
6. active run 所属 build run 不能归档或删除。

建议命令：

```bash
cd backend/ckqa-back
./mvnw test
./mvnw -DskipTests compile
```

### 17.3 前端 admin-app

1. API 模块测试覆盖 build run 与新增 CRUD 函数。
2. 知识库列表与详情能读取 `latestBuildRunStatus`、`artifactReady`、`canAsk`。
3. 构建向导所有步骤围绕 `buildRunId` 恢复和推进。
4. 索引运行详情显示所属 build run、产物摘要和 active 状态。

建议命令：

```bash
cd frontend/apps/admin-app
pnpm test
pnpm build
```

### 17.4 集成烟测

最小真实验证：

1. 使用两个用户身份或模拟两个 `requestedByUserId`。
2. 为同一个知识库并发创建两个 build run，选择不同资料组合。
3. 确认生成两个不同 workspace。
4. 分别推进到索引阶段，确认 `index/input`、`index/output`、`index/output/lancedb` 不互相覆盖。
5. 确认各自 `index_artifacts` 指向不同目录。
6. 分别激活两个 success run，确认 `knowledge_bases.active_index_run_id` 显式切换。
7. 分别发起 QA smoke，确认 Java 传入不同 active run output。

## 18. 实施切分建议

后续实施计划应分为五步：

1. **构建流水线控制面**：新增 `knowledge_base_build_runs` schema、实体、service、controller、workspace 路径计算和状态机。
2. **GraphRAG 输入与索引隔离**：新增 `fetch_from_minio.py --output-file`，把 `graph-input` 和 `index/input/output/cache/reports` 全部绑定到 build workspace。
3. **产物登记与 active 查询**：补 `index_artifacts` 服务、artifact 扫描、Python query-task 每任务 dataDir、Java QA 传 active run context。
4. **前端 build run 接入**：admin-app 构建向导从 URL/sessionStorage 主导改为 `buildRunId` 主导，补 CRUD/API 模块和状态展示。
5. **文档与回归**：更新 README、AGENTS、模块 README，跑 Python/Java/admin-app 验证和 repo drift audit。

## 19. 验收标准

设计完成后的实现应满足：

1. 不同用户、不同知识库、不同 build run 的资料选择、输入副本、索引输出、日志和 QA 验证互不覆盖。
2. 同一知识库历史 success index run 可以被重新激活。
3. 问答查询读取的是 `active_index_run_id` 对应 index run 的产物。
4. 前端能完成知识库创建、查看、修改、归档；构建流水线创建、查看、推进、归档/清理；索引运行查看、归档/清理、激活；产物查看和受控清理。
5. 系统健康检查能区分“GraphRAG 服务可运行”和“某知识库已有可用索引”。
6. MinIO 首轮仍保存 pdf_ingest 导出的源产物和 GraphRAG 输入源；GraphRAG 索引产物首轮保留在本地 build workspace，不做远端上传。
