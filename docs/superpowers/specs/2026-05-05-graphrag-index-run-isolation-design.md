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
6. 首轮不把 GraphRAG 构建改成 `graphrag.api.build_index()` 内部 API 调用，不引入 Spring Statemachine。它们可以作为后续演进方向，但当前实现先保持 CLI 可验证闭环。

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
ckqa.integration.graphrag.concurrent-builds-enabled=${GRAPHRAG_ALLOW_CONCURRENT_KB_BUILDS:true}
ckqa.integration.graphrag.auto-activation-policy=${GRAPHRAG_AUTO_ACTIVATION_POLICY:latest-build-only}
ckqa.integration.graphrag.retention.keep-success-build-runs=${GRAPHRAG_KEEP_SUCCESS_BUILD_RUNS:3}
ckqa.integration.graphrag.retention.keep-failed-build-runs=${GRAPHRAG_KEEP_FAILED_BUILD_RUNS:3}
ckqa.integration.graphrag.retention.auto-cleanup-enabled=${GRAPHRAG_AUTO_CLEANUP_BUILD_RUNS:false}
```

默认 `build-runs-root`：

```text
{GRAPHRAG_ROOT}/runtime/kb-build-runs
```

单次构建流水线 workspace：

```text
{GRAPHRAG_BUILD_RUNS_ROOT}/
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

路径持久化规则：

1. `GRAPHRAG_BUILD_RUNS_ROOT` 是唯一绝对路径锚点，只存在于配置、运行时环境和服务层。
2. `knowledge_base_build_runs.workspace_uri`、`index_artifacts.storage_uri`、`run_metadata.workspaceUri` 等数据库字段只存相对于 `GRAPHRAG_BUILD_RUNS_ROOT` 的路径，例如 `user_2/kb_5/build_27/index/output/lancedb`。
3. 服务层读写文件时把相对路径解析到 `GRAPHRAG_BUILD_RUNS_ROOT` 下，并必须校验 `normalize()` 后仍位于该根目录内。
4. Java `/api/v1` 对前端默认只暴露 `artifactId`、产物类型、大小、状态和可读的相对标识；不返回服务器绝对路径。
5. 旧的 `graphrag_pipeline/input/`、`output/`、`cache/`、`reports/` 仍相对于 `GRAPHRAG_ROOT`，只作为本地 CLI 调试路径，不写入新业务 run 的 `workspace_uri`。

清理与保留策略：

1. 首轮默认不自动删除 workspace，`auto-cleanup-enabled=false`，避免误删正在排查的索引产物。
2. 手动清理通过 build run 删除/归档接口或 `POST /api/v1/knowledge-bases/{id}/build-runs/gc` 触发。
3. 若开启自动清理，仅在 build run 成功或失败进入终态后异步触发同知识库范围 GC；新建 build run 时可以做一次轻量扫描，但不阻塞创建接口。
4. GC 永远跳过 running build run、running index run、当前 active index run 所属 build run，以及 `artifact_status=ready` 且仍被 active run 引用的产物。
5. 超出保留数量的 success/failed build run 先标记 `archived`，再删除 workspace，并把关联 `index_artifacts.artifact_status` 更新为 `deleted`。

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

GraphRAG Python API 评估结论：

1. GraphRAG 官方文档提供了 `graphrag.api.build_index(config=...)` 的库调用方式，理论上可以减少 Java 侧子进程环境变量注入。
2. 本仓库当前已把 GraphRAG 3.0.9 查询与构建路径收口到 CLI，`utils/main.py` 也明确委托 `graphrag query`；首轮继续沿用这个稳定面，降低版本漂移风险。
3. GitHub issue #1680 记录过 `build_index()` 下 `storage.base_dir` 相对路径解析与 `root_dir` 不一致的问题。即使该问题在后续版本中被修复，CKQA 也要先做本地 3.0.9 回归，再考虑切换。
4. 后续若迁移到 Python API，应让每个 build workspace 拥有生成后的绝对路径配置副本，或者继续使用环境变量展开后的绝对目录，不能重新回到共享 `output/`。

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

### 7.1 状态转移与重试规则

首轮不引入 Spring Statemachine，使用 `current_stage` enum、`build_metadata.stageSummary` 和服务层显式 Guard 实现状态机。状态转移必须集中在 `KnowledgeBaseBuildRunService`，不能散落在 Controller 中。

| 动作 | 允许前置阶段 | 成功后阶段 | 失败后处理 | 可否重试 |
| --- | --- | --- | --- | --- |
| 创建 build run | 无 | `material_selection` | 创建失败不落库或标记 `failed` | 重新创建 |
| 更新资料选择 | `material_selection`、`parse` 失败、`graph_input_export` 失败 | `material_selection` | 保留旧选择和错误摘要 | 是，未创建 index run 前可重选 |
| 解析检查/触发 | `material_selection`、`parse` | `parse` 或 `graph_input_export` 待执行 | `stageSummary.parse=failed` | 是，可只刷新缺失 material |
| 同步图谱输入 | `material_selection`、`parse`、`graph_input_export` | `graph_input_export` | 保留已成功拉取文件，记录失败 material | 是，可按 material 局部重试 |
| 确认 Prompt | `graph_input_export`、`prompt` | `prompt` | `stageSummary.prompt=failed` | 是，可重新确认策略 |
| 创建索引运行 | `prompt`、`index` 失败 | `index` 或 `done` | `index_run=failed`，build run 保留在 `index` | 是，同一 build run 内无 running index run 时可再建一个 index run |
| QA 冒烟验证 | `index` success、`done`、`qa_smoke` 失败 | `done` | `qa_status=failed`，build `status` 不回退 | 是 |
| 归档/清理 | `success`、`failed`、`interrupted` | `archived` | 返回 409 或错误摘要 | 条件满足后可重试 |

阶段语义：

1. `prompt` 阶段首轮不能跳过；即使使用默认 `active` Prompt，也要写入 `active_prompt_snapshot.json` 和确认记录，避免后续无法复现本次构建。
2. `parse` 阶段可以只是“读取已完成解析状态并写快照”，不一定触发新解析任务。
3. `qa_smoke` 是索引后的质量验证阶段，不是索引产物是否可用的硬门槛；它的通过与否写入 `qa_status`。
4. `build_run.status=success` 表示索引阶段已成功、核心产物已登记。`qa_status=failed` 表示验证警告，不把 build run 改成 `failed`。
5. `build_run.status=interrupted` 只用于后端重启或进程丢失后的恢复结果；前端应提示可重新发起失败阶段。

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

`buildRunId` 与 `buildVersion` 规则：

1. `buildRunId` 使用 MySQL 自增 `bigint` 主键，是所有内部关联和 API 动作的稳定 ID。
2. `buildVersion` 是面向展示、审计和幂等排查的版本号，不承担主键职责。
3. 首轮格式使用 `kb{knowledgeBaseId}-{yyyyMMddHHmmssSSS}-{random4}`，例如 `kb5-20260505203000123-a7f3`。
4. `UNIQUE KEY (knowledge_base_id, build_version)` 仍保留；若极端并发下随机后缀冲突，服务层重试生成最多 3 次。
5. 不把 UUID v7 作为首轮强制方案，因为当前 Java 标准库没有内置 UUID v7 生成器，为一个展示字段引入额外依赖收益不高；后续如项目已有统一 ID 组件再切换。

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
3. GraphRAG 官方输入说明支持同一输入目录下多个 JSON 文件并合并为最终 documents 表，但 CKQA 实施时必须用 GraphRAG 3.0.9 做本地回归验证。
4. 首轮默认把 `graph-input/` 中本次确认的文件以唯一文件名复制到 `index/input/`，例如 `material_3.section_docs.json`、`material_4.section_docs.json`。
5. 如果本地验证发现多 JSON 文件行为与预期不一致，后端改为用 Jackson 把多个 JSON array 合并为单个 `build_{buildRunId}.section_docs.json`，并在 `export_snapshot.json` 记录来源 material 映射。
6. `index/input/` 只包含本 build run 确认过的输入，不读取共享 `graphrag_pipeline/input/`。

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
  -> 把 graph-input 中确认的 JSON 以唯一文件名放入 index/input
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
6. active 切换在数据库事务内执行，并对目标 `knowledge_bases` 行加锁或使用等价乐观锁条件更新；首轮不引入 Redis 分布式锁。
7. 默认允许同一知识库并发 build run，但 `auto-activation-policy=latest-build-only`：只有该知识库下创建时间或 ID 最新的 build run 才能自动激活。较早 build run 后完成时仍标记 `success`，但自动激活结果为 `skipped_newer_build_exists`。
8. 管理员通过 `POST /api/v1/knowledge-bases/{id}/active-index-run` 手动激活历史 success run 时，可以覆盖上述自动激活策略。

失败处理：

1. 输入拉取失败：`build_run.status=failed` 或阶段状态为 `failed`，保留 `graph-input/` 中已拉取文件和错误 metadata。
2. 索引命令失败：`index_run.status=failed`，保留 `index/output`、`index/reports` 残留文件，artifact 可记录为 `partial`。
3. QA 验证失败：不影响已成功的 index run，也不回滚 active 切换；`build_run.status` 保持 `success`，`qa_status=failed`，前端可选择重新验证或提示质量风险。
4. 后端重启：沿用 stale recovery，把超时的 running index run 标记为 failed；build run 根据最新阶段恢复为 `failed` 或 `interrupted`。

日志写入规则：

1. `GraphRagIndexOrchestrator` 不依赖 GraphRAG CLI 自己是否写日志文件，而是由 Java `ProcessRunner` 把子进程 stdout/stderr 同步写入 `index/logs/process.log`。
2. `ProcessRunner` 同时保留尾部日志，写入 `run_metadata.latestLogs` 或通过详情接口返回。
3. GraphRAG `reporting.base_dir` 仍指向 `index/reports`，用于保存 GraphRAG 自身报告；前端“最近日志”优先读 `index/logs/process.log`，报告列表来自 `index_artifacts`。

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
  status enum('pending','running','success','failed','interrupted','archived') NOT NULL DEFAULT 'pending' COMMENT '流水线状态',
  current_stage enum('material_selection','parse','graph_input_export','prompt','index','qa_smoke','done') NOT NULL DEFAULT 'material_selection' COMMENT '当前阶段',
  qa_status enum('pending','running','success','failed','skipped') NOT NULL DEFAULT 'skipped' COMMENT '问答验证状态',
  activation_policy enum('manual','index_success') NOT NULL DEFAULT 'index_success' COMMENT '自动激活策略',
  selected_material_ids json DEFAULT NULL COMMENT '本次构建资料选择快照',
  active_index_run_id bigint NULL COMMENT '本次构建最终激活的索引运行',
  workspace_uri varchar(512) NULL COMMENT '相对 GRAPHRAG_BUILD_RUNS_ROOT 的工作区路径',
  build_metadata json DEFAULT NULL COMMENT '构建元数据',
  started_at timestamp NULL DEFAULT NULL COMMENT '开始时间',
  finished_at timestamp NULL DEFAULT NULL COMMENT '结束时间',
  created_at timestamp NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  updated_at timestamp NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (id),
  UNIQUE KEY uk_kb_build_version (knowledge_base_id, build_version),
  KEY idx_kb_build_status (knowledge_base_id, status),
  KEY idx_kb_build_user_status (requested_by_user_id, status),
  KEY idx_kb_build_created (knowledge_base_id, created_at, id),
  CONSTRAINT fk_kb_build_runs_kb FOREIGN KEY (knowledge_base_id) REFERENCES knowledge_bases(id)
);
```

字段说明：

1. `requested_by_user_id` 首轮可为空，用于兼容当前 dev 身份和未完全接入登录的场景；前端或后端身份接入稳定后再改成必填。
2. `build_version` 由服务层生成，格式为 `kb{knowledgeBaseId}-{yyyyMMddHHmmssSSS}-{random4}`；唯一索引冲突时最多重试 3 次。
3. `workspace_uri` 只存相对 `GRAPHRAG_BUILD_RUNS_ROOT` 的路径，例如 `user_2/kb_5/build_27`。
4. `activation_policy=index_success` 对应请求中的 `activateOnSuccess=true`；`manual` 对应 `false`。
5. `build_metadata.stageSummary` 保存各阶段子状态、错误摘要、activation 结果和 QA 摘要，避免首轮新增过多阶段明细表。

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
  "workspaceUri": "user_2/kb_5/build_27",
  "inputDir": "user_2/kb_5/build_27/index/input",
  "outputDir": "user_2/kb_5/build_27/index/output",
  "reportsDir": "user_2/kb_5/build_27/index/reports",
  "cacheDir": "user_2/kb_5/build_27/index/cache",
  "lancedbUri": "user_2/kb_5/build_27/index/output/lancedb",
  "materialIds": [3, 4],
  "jsonFile": "section_docs.json",
  "activateOnSuccess": true,
  "activationPolicy": "index_success",
  "activationResult": "activated",
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
user_2/kb_5/build_27/index/output/lancedb
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
  "buildVersion": "kb5-20260505203000123-a7f3",
  "status": "pending",
  "currentStage": "material_selection",
  "qaStatus": "skipped",
  "activationPolicy": "index_success",
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

#### 清理历史构建工作区

```http
POST /api/v1/knowledge-bases/{id}/build-runs/gc
```

请求：

```json
{
  "deleteWorkspace": true,
  "dryRun": true
}
```

返回：

```json
{
  "candidateBuildRunIds": [12, 13],
  "archivedBuildRunIds": [],
  "deletedWorkspaceCount": 0,
  "skippedActiveBuildRunIds": [27]
}
```

约束：

1. `dryRun=true` 只返回候选项，不改数据库和文件系统。
2. `deleteWorkspace=true` 只作用于非 active、非 running、超出保留策略或已归档的 build run。
3. 前端首轮可把该能力放到知识库详情的“维护动作”，不放在构建向导主流程。

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
3. 是否限制“同一知识库同时只有一个 running build run”由 `concurrent-builds-enabled` 控制；默认允许并发构建。
4. 并发构建默认只允许“最新创建的 build run”自动激活，较早 build run 后完成时需要管理员手动激活。
5. active 切换必须在数据库事务内校验知识库归属、index run 成功状态、artifact ready 状态和最新 build run 策略；首轮使用 MySQL 行锁或乐观条件更新，不引入 Redis 锁。
6. 归档知识库前必须确认没有 running build run 或 running index run。
7. 归档 active run 所属 build run 必须先切换到其他 success run；首轮不支持清空 active run。
8. 创建索引运行时，所选资料必须已有 GraphRAG 输入导出产物；缺失时返回明确错误，由前端引导回“导出图谱输入”步骤。
9. 查询问答时，如果 active run 的产物缺失，返回 `KNOWLEDGE_BASE_NOT_READY`，并在详情中给出缺失 artifact 类型。

## 15. 健康检查调整

`GET /api/v1/system/health` 不能再只检查 `GRAPHRAG_ROOT/output` 和 `output/lancedb`，也不应该在每次健康检查里遍历所有知识库 active run。首轮拆成轻量健康检查和可用性检查：

### 15.1 轻量健康检查

1. `graphrag-root`：GraphRAG 项目根目录存在。
2. `graphrag-build-runs-root`：构建运行目录存在或可创建。
3. `graphrag-api`：Python API 可访问。
4. `graphrag-ready`：表示 GraphRAG 服务链路可运行，不等同于某个知识库已有可用索引。

这样本地刚清空索引时，系统可以是“服务可运行但知识库未就绪”，不会误报为路径损坏。

### 15.2 知识库可用性检查

新增可选接口：

```http
GET /api/v1/system/readiness
GET /api/v1/knowledge-bases/{id}/readiness
```

规则：

1. 全局 readiness 默认只检查是否存在至少一个 `active_index_run_id` 且其 `lancedb` artifact 状态为 `ready`，不逐个遍历大目录。
2. 单知识库 readiness 检查该知识库 active run 的必要 artifact 是否 ready，并可按需做文件存在性检查。
3. 文件系统深度扫描只在知识库详情、维护动作或后台巡检中触发，不放进高频 health endpoint。

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
5. 用两份最小 JSON 输入验证 GraphRAG 3.0.9 会合并 `index/input/` 下多个 JSON 文件；若失败，启用单文件合并 fallback 并补测试。
6. `dataDir` 校验覆盖路径穿越、软链接逃逸和绝对路径不在 `GRAPHRAG_BUILD_RUNS_ROOT` 下的拒绝场景。

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
7. `buildVersion` 在同毫秒并发创建时不会冲突，唯一索引冲突能重试。
8. 状态机 Guard 阻止未确认 Prompt 时创建索引、running build run 被归档、active build run 被删除。
9. 两个并发 build run 都 `activateOnSuccess=true` 时，只有最新 build run 自动激活，较早 run 记录 `skipped_newer_build_exists`。
10. `ProcessRunner` 将 stdout/stderr 写入 `index/logs/process.log`，详情接口能返回尾部日志。
11. GC dry-run、归档、删除 workspace、跳过 active/running run 的行为都有服务层测试。

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
6. 让较早 build run 后完成，确认它不会覆盖较新的 active run。
7. 通过手动 active 接口切换到另一个 success run，确认 `knowledge_bases.active_index_run_id` 显式切换。
8. 分别发起 QA smoke，确认 Java 传入不同 active run output。
9. 触发一次 GC dry-run，确认 active/running build run 被跳过。

## 18. 实施切分建议

后续实施计划应分为五步：

1. **构建流水线控制面**：新增 `knowledge_base_build_runs` schema、实体、service、controller、workspace 路径计算和状态机。
2. **GraphRAG 输入与索引隔离**：新增 `fetch_from_minio.py --output-file`，把 `graph-input` 和 `index/input/output/cache/reports` 全部绑定到 build workspace。
3. **产物登记与 active 查询**：补 `index_artifacts` 服务、artifact 扫描、Python query-task 每任务 dataDir、Java QA 传 active run context，并实现 latest-build-only 自动激活。
4. **前端 build run 接入**：admin-app 构建向导从 URL/sessionStorage 主导改为 `buildRunId` 主导，补 CRUD/API 模块和状态展示。
5. **日志、GC 与健康检查**：补 `index/logs/process.log`、GC dry-run/清理接口、轻量 health 与 readiness 拆分。
6. **文档与回归**：更新 README、AGENTS、模块 README，跑 Python/Java/admin-app 验证和 repo drift audit。

## 19. 验收标准

设计完成后的实现应满足：

1. 不同用户、不同知识库、不同 build run 的资料选择、输入副本、索引输出、日志和 QA 验证互不覆盖。
2. 同一知识库历史 success index run 可以被重新激活。
3. 问答查询读取的是 `active_index_run_id` 对应 index run 的产物。
4. 前端能完成知识库创建、查看、修改、归档；构建流水线创建、查看、推进、归档/清理；索引运行查看、归档/清理、激活；产物查看和受控清理。
5. 系统健康检查能区分“GraphRAG 服务可运行”和“某知识库已有可用索引”。
6. MinIO 首轮仍保存 pdf_ingest 导出的源产物和 GraphRAG 输入源；GraphRAG 索引产物首轮保留在本地 build workspace，不做远端上传。
7. 并发构建时自动激活不会让较早完成的旧 build run 覆盖更新 build run 的 active 指针。
8. QA smoke 失败不会把已成功索引回滚为 failed，但前端能看到 `qaStatus=failed` 和错误摘要。
9. GC 不会删除 active/running run 的 workspace，且 dry-run 输出与实际清理结果一致。

## 20. 审阅建议处理记录

| 建议 | 处理结论 | 说明 |
| --- | --- | --- |
| 明确 `build_run_id` 和 `build_version` 生成策略 | 采纳 | `id` 用 MySQL 自增 bigint；`build_version` 改为 `kb{id}-{yyyyMMddHHmmssSSS}-{random4}`，冲突重试。 |
| 数据库只存相对路径，服务层用根目录解析 | 采纳 | 新增路径持久化规则，所有 workspace/artifact URI 相对 `GRAPHRAG_BUILD_RUNS_ROOT`。 |
| 增加状态转移图和重试规则 | 采纳 | 新增 7.1 状态转移表，明确 Prompt 不跳过、QA 不阻断索引成功、失败阶段可重试。 |
| 明确并发构建和 active 锁策略 | 采纳 | 默认允许并发 build run；自动激活采用 `latest-build-only`；active 更新使用 MySQL 事务行锁或乐观条件更新，不引入 Redis。 |
| 明确 QA smoke 对终态和激活的影响 | 采纳 | 首轮 `activateOnSuccess` 在 index success 后生效；QA 失败只写 `qa_status=failed`，不回滚 active。 |
| 健康检查避免遍历所有 active artifact | 采纳 | `/system/health` 保持轻量，新增 readiness 语义用于全局或单知识库可用性检查。 |
| 补充 workspace GC 触发时机 | 采纳 | 默认手动/dry-run，自动清理默认关闭，只在终态后异步触发且跳过 active/running。 |
| 明确 `index/logs/` 写入方 | 采纳 | 由 Java `ProcessRunner` tee stdout/stderr 到 `index/logs/process.log`，不依赖 GraphRAG 自身日志。 |
| 明确多 material 输入组装 | 采纳 | 默认唯一文件名复制到 `index/input`，并要求 GraphRAG 3.0.9 多 JSON 回归；失败时合并成单 JSON array。 |
| 近期改用 `graphrag.api.build_index()` | 暂不采纳 | 官方 API 存在，但本仓库已把 3.0.9 路径收口到 CLI；同时存在过 `build_index()` storage 相对路径 bug。首轮继续 subprocess + env 注入，后续单独评估。 |
| 引入 Spring Statemachine | 暂不采纳 | Spring Statemachine 支持 machineId、Guard、持久化，但对当前 7 个顺序阶段偏重；首轮用 enum + 服务层 Guard，更符合最小可运行原则。 |
| UUID v7 作为 build version 必选方案 | 暂不采纳 | UUID v7 有排序优势，但 Java 标准库无内置实现；当前展示版本号用时间戳毫秒 + 随机后缀足够，避免新增依赖。 |
