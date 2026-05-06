# CKQA 项目入口文档

> 审计日期：2026-05-06
> 目标：把仓库入口、模块边界、主链路和阅读顺序整理成一份可信的导航页。

CKQA 是一个面向课程资料的混合型问答系统。按当前仓库代码、目录和依赖配置来看，知识生产与问答能力的主链路仍然由两个 Python 模块承担：

1. `pdf_ingest/`
   负责课程 PDF 上传、MinerU 云解析、MinIO/MySQL 落库，以及标准化文档与 GraphRAG 输入导出。
2. `graphrag_pipeline/`
   负责拉取导出的 JSON、执行 Microsoft GraphRAG 建索引，并通过 FastAPI 提供 OpenAI 兼容问答接口。

其余目录目前属于编排入口、基础设施或配套前端/后端工作区：

- `infra/`：本地基础设施统一 Docker Compose 入口，管理 MySQL、MinIO、One API 和 Neo4j；运行态数据目录不入库。
- `sql/`：MySQL 初始化脚本与增量迁移脚本的仓库级来源。
- `frontend/apps/student-app/`：学员端前端原型，界面与路由更完整；登录注册已接入 Java `/api/v1/auth/student/*`，课程/问答等业务页仍在逐步接入真实契约。
- `frontend/apps/admin-app/`：管理员端/教师端共用控制台前端，核心运维页已经接入 Java `/api/v1`，并已完成 Element Plus + Pinia + Sass 样式基座迁移，覆盖系统健康、课程、资料上传与生命周期、知识库、构建向导、QA 冒烟验证和统一错误页。
- `backend/ckqa-back/`：Spring Boot 4 + Java 21 一期编排入口，承接 `/api/v1` 下的课程、PDF、知识库、索引、异步 QA、QA 冒烟会话和系统健康检查接口，但真实解析、索引和问答仍依赖两个 Python 模块。

如果文档、注释和代码不一致，请优先相信目录结构、脚本入口、`pyproject.toml` / `pom.xml` / `package.json` 里的真实定义。

## 一眼看懂仓库

| 板块 | 当前角色 | 状态 | 入口文档 |
| --- | --- | --- | --- |
| `pdf_ingest/` | PDF 解析与标准化导出 | 主链路，最完整 | [pdf_ingest/README.md](pdf_ingest/README.md) |
| `graphrag_pipeline/` | GraphRAG 建图、检索、API | 主链路，依赖运行环境 | [graphrag_pipeline/README.md](graphrag_pipeline/README.md) |
| `infra/` | 本地 Docker 基础设施 | 统一 compose 入口，数据目录默认保留现状 | [infra/README.md](infra/README.md) |
| `sql/` | MySQL schema 与迁移 | 仓库级数据库脚本来源 | [sql/ocqa.sql](sql/ocqa.sql) |
| `frontend/apps/student-app/` | 学员端前端原型 | 登录注册已接入 Java `/api/v1`，业务页继续演进 | [frontend/apps/student-app/README.md](frontend/apps/student-app/README.md) |
| `frontend/apps/admin-app/` | 管理端/教师端控制台前端 | 核心运维页已接 Java `/api/v1`，资料上传默认 200MB，样式基座已切到 Element Plus + Pinia + Sass，含 Playwright 故障注入验收 | [frontend/apps/admin-app/README.md](frontend/apps/admin-app/README.md) |
| `backend/ckqa-back/` | Java 编排后端 | 一期 `/api/v1` 编排接口，依赖 Python 主链路 | [backend/ckqa-back/README.md](backend/ckqa-back/README.md) |

## 本机启动前后端服务

开发阶段推荐把入口分成三层：

默认端口：GraphRAG API `8012`，Java 后端 `8080`，admin-app `5173`，student-app `5174`。

- 基础设施容器统一由根目录 `infra/docker-compose.yml` 管理。
- 前端用 `pnpm` 原生命令启动，不额外包一层 shell 脚本。
- 后端用 Java 编排层启动；开发态可让 Java 在启动时托管拉起 GraphRAG Python API。
- `pdf_ingest/` 当前没有常驻 HTTP 服务，Java 后端会在解析/导出动作发生时按需调用它的 CLI。

### 0. 一键启动基础设施容器

首次使用先准备本机密钥文件：

```bash
cd infra
cp .env.example .env
# 编辑 infra/.env，填入当前 MySQL root 密码、MinIO 账号和密码
```

统一启动：

```bash
cd infra
docker compose up -d
docker compose ps
```

如果当前机器已经存在旧容器，第一次切换到统一 compose 时只删除容器对象，不删除数据目录：

```bash
docker stop neo4j one-api mysql minio
docker rm neo4j one-api mysql minio

cd infra
docker compose up -d
```

当前数据保留策略见 [infra/README.md](infra/README.md)：MySQL 继续挂载 `/home/sunlight/mysql/data`，MinIO 继续挂载 `/home/sunlight/minio/data`，One API 和 Neo4j 数据已经随 `infra/` 迁到仓库根目录下。

### 1. 一键启动前端

```bash
# 仓库根目录
pnpm --dir frontend/apps/admin-app install
pnpm --dir frontend/apps/student-app install

pnpm dev:admin
pnpm dev:student
```

也可以进入具体应用目录启动：

```bash
cd frontend/apps/admin-app
pnpm dev:local

cd frontend/apps/student-app
pnpm dev:local
```

admin-app 和 student-app 开发态默认从浏览器请求同源 `/api/v1`，由 Vite 代理到 Java 后端 `http://127.0.0.1:8080`。这样在远程开发或端口转发场景下，浏览器不需要直接访问后端所在环境的 `8080`。

### 2. 一键启动 Java 后端和托管 GraphRAG API

```bash
cd backend/ckqa-back

export MYSQL_HOST=127.0.0.1
export MYSQL_PORT=23306
export MYSQL_DATABASE=ocqa
export MYSQL_USER=root
export MYSQL_PASSWORD="${MYSQL_PASSWORD:?请先设置 MYSQL_PASSWORD}"

export MINIO_ENDPOINT=localhost:9000
export MINIO_ACCESS_KEY=admin
export MINIO_SECRET_KEY=12345678
export MINIO_SECURE=false
export COURSE_COVER_BUCKET=course-artifacts
export COURSE_COVER_OBJECT_PREFIX=course-covers
export COURSE_MATERIAL_BUCKET=course-artifacts
export COURSE_MATERIAL_OBJECT_PREFIX=course-materials
export COURSE_MATERIAL_MAX_FILE_SIZE_BYTES=209715200
export CKQA_MULTIPART_MAX_FILE_SIZE=200MB
export CKQA_MULTIPART_MAX_REQUEST_SIZE=200MB
export PDF_INGEST_ROOT=/home/sunlight/Projects/ckqa/pdf_ingest
export GRAPHRAG_ROOT=/home/sunlight/Projects/ckqa/graphrag_pipeline
export GRAPHRAG_API_HOST=127.0.0.1
export GRAPHRAG_API_PORT=8012
export GRAPHRAG_API_BASE_URL=http://127.0.0.1:8012
export GRAPHRAG_API_MANAGED_ENABLED=true
export CKQA_JWT_SECRET=please-change-this-local-jwt-secret-at-least-32-chars

./mvnw spring-boot:run
```

开启 `GRAPHRAG_API_MANAGED_ENABLED=true` 后，Java 后端会在启动阶段检查 `GRAPHRAG_API_BASE_URL/health`：如果 GraphRAG API 已经可访问，就复用外部服务；如果不可访问，就在 `GRAPHRAG_ROOT` 下启动 `utils/main.py`。Java 进程退出时会同步停止由它托管启动的 GraphRAG 子进程。

如果希望 GraphRAG API 继续手工独立启动，把 `GRAPHRAG_API_MANAGED_ENABLED` 设为 `false` 或不设置即可。

下面保留手工启动顺序，方便排查 Java 托管启动以外的环境问题。

### 3. 手工排障启动顺序

#### 3.1 确认基础依赖

MySQL、MinIO、One API、Neo4j 等容器由根目录 `infra/docker-compose.yml` 统一管理。至少先确认 MySQL 暴露在 `127.0.0.1:23306`，`GRAPHRAG_ROOT` 指向可用的 `graphrag_pipeline/`，并为构建流水线准备本地运行目录 `graphrag_pipeline/runtime/kb-build-runs/`。共享 `graphrag_pipeline/output/` 现在主要作为 CLI 手工排障路径；管理端构建向导会优先使用每个 build run 自己的 `index/output/`。

```bash
docker ps
```

如果当前机器的 MySQL root 密码来自容器环境变量，后端启动时可以这样注入，避免把密码写入仓库文件：

```bash
export MYSQL_PASSWORD="$(docker exec mysql printenv MYSQL_ROOT_PASSWORD)"
```

#### 3.2 手工启动 GraphRAG Python API

```bash
cd graphrag_pipeline

GRAPHRAG_API_HOST=127.0.0.1 \
GRAPHRAG_API_PORT=8012 \
GRAPHRAG_OUTPUT_DIR="$PWD/output" \
GRAPHRAG_STORAGE_DIR="$PWD/output" \
GRAPHRAG_LANCEDB_URI="$PWD/output/lancedb" \
conda run -n graphrag-oneapi python utils/main.py
```

验收：

```bash
curl http://127.0.0.1:8012/health
curl http://127.0.0.1:8012/v1/models
```

如果 `8012` 被占用，可以把 `GRAPHRAG_API_PORT` 改成其他端口，并在后端启动时同步修改 `GRAPHRAG_API_BASE_URL`。

#### 3.3 手工启动 Java 后端

另开终端：

```bash
cd backend/ckqa-back

export MYSQL_HOST=127.0.0.1
export MYSQL_PORT=23306
export MYSQL_DATABASE=ocqa
export MYSQL_USER=root
export MYSQL_PASSWORD="${MYSQL_PASSWORD:?请先设置 MYSQL_PASSWORD}"

export MINIO_ENDPOINT=localhost:9000
export MINIO_ACCESS_KEY=admin
export MINIO_SECRET_KEY=12345678
export MINIO_SECURE=false
export COURSE_COVER_BUCKET=course-artifacts
export COURSE_COVER_OBJECT_PREFIX=course-covers
export COURSE_MATERIAL_BUCKET=course-artifacts
export COURSE_MATERIAL_OBJECT_PREFIX=course-materials
export COURSE_MATERIAL_MAX_FILE_SIZE_BYTES=209715200
export CKQA_MULTIPART_MAX_FILE_SIZE=200MB
export CKQA_MULTIPART_MAX_REQUEST_SIZE=200MB
export PDF_INGEST_ROOT=/home/sunlight/Projects/ckqa/pdf_ingest
export GRAPHRAG_ROOT=/home/sunlight/Projects/ckqa/graphrag_pipeline
export GRAPHRAG_OUTPUT_DIR=/home/sunlight/Projects/ckqa/graphrag_pipeline/output
export GRAPHRAG_LANCEDB_URI=/home/sunlight/Projects/ckqa/graphrag_pipeline/output/lancedb
export GRAPHRAG_BUILD_RUNS_ROOT=/home/sunlight/Projects/ckqa/graphrag_pipeline/runtime/kb-build-runs
export GRAPHRAG_ALLOW_CONCURRENT_KB_BUILDS=true
export GRAPHRAG_AUTO_ACTIVATION_POLICY=latest-build-only
export GRAPHRAG_API_BASE_URL=http://127.0.0.1:8012

./mvnw spring-boot:run
```

验收：

```bash
curl http://127.0.0.1:8080/api/v1/system/health
curl http://127.0.0.1:8080/api/v1/courses
curl http://127.0.0.1:8080/api/v1/knowledge-bases
```

`/api/v1/system/health` 现在保持轻量，重点确认 `mysql`、`pdf-ingest-root`、`graphrag-root`、`graphrag-build-runs-root`、`graphrag-api`、`graphrag-ready`。如果需要检查共享 CLI 调试产物，再调用 `/api/v1/system/readiness`；其中 `graphrag-output` 只代表 `GRAPHRAG_ROOT/output` 与 `output/lancedb` 的手工排障状态，不再是管理端 build-run 流程的唯一就绪条件。

#### 3.4 手工启动 admin-app 前端

另开终端：

```bash
cd frontend/apps/admin-app
pnpm install
pnpm dev:local
```

浏览器访问：

```text
http://127.0.0.1:5173/app/health
http://127.0.0.1:5173/app/courses
http://127.0.0.1:5173/app/knowledge-bases
```

登录页使用真实 JWT 鉴权。本地测试账号来自 `sql/migrations/20260506_jwt_auth_credentials.sql`：管理员端 `admin.heqh / Ckqa@2026`，教师端 `teacher.zhangwb / Ckqa@2026`，学生端 `student.zhouzh / Ckqa@2026`。

### 4. 前端/后端回归验证

```bash
cd frontend/apps/admin-app
pnpm test
pnpm build
pnpm test:e2e
```

```bash
cd backend/ckqa-back
./mvnw test
```

```bash
cd pdf_ingest
conda run -n courseKg python -m pytest tests/test_ocqa_business_schema_contract.py -q
```

## 本次审计结论

- 当前唯一稳定的知识生产与问答能力主链路仍然是 `pdf_ingest -> graphrag_pipeline`。
- `graphrag_pipeline` 的 GraphRAG 版本基线统一以 `pyproject.toml` 为准，当前固定为 `graphrag==3.0.9`。
- `backend/ckqa-back/` 已经不再是空骨架；它是 Java 一期编排入口，统一响应体为 `code / message / data / timestamp`，业务成功码为 `200`，课程、资料、知识库、索引和 QA 冒烟验证都通过 `/api/v1` 暴露给前端，但真实 PDF 解析、索引和问答仍调用两个 Python 模块。
- `frontend/apps/student-app/` 仍是学员端原型，包含落地页、首页、问答页、课程页与 Pinia/Vue Router 基础结构；登录注册已接入 Java `/api/v1`，其余业务页仍在逐步接入稳定 API。
- `frontend/apps/admin-app/` 已不再是起步页原型；它现在是一个独立可运行的管理员/教师共用控制台前端，已具备 Element Plus + Pinia + Sass 样式基座、主题系统、路由守卫、工作台、系统健康页、课程/资料/知识库 live 页面、资料上传校验、构建向导、QA 冒烟验证、统一 403/404/500 错误页和 Playwright 浏览器级故障注入验收。
- 文档阅读时要区分“主流程模块”和“占位模块”，不要把尚未集成的板块误判为可直接投入使用。

## 人类与机器入口

- 人类阅读入口：优先读本文件，再按模块进入各自 `README.md`，它们负责解释模块角色、运行路径和边界。
- 机器阅读入口：优先读 [AGENTS.md](AGENTS.md)、[.codex](.codex)、[pdf_ingest/CLAUDE.md](pdf_ingest/CLAUDE.md)、[graphrag_pipeline/CLAUDE.md](graphrag_pipeline/CLAUDE.md)，它们负责约束 Agent 的默认判断、命令、边界和安全规则。
- 如果人类文档与机器文档冲突，以当前代码、依赖配置和可运行命令为准，并在改动时同步修正文档。

## 主流程

```text
课程 PDF
  -> pdf_ingest 将原始资料对象按 MD5 存入 MinIO/material_objects，并在 course_materials 中登记课程资料关系
  -> 调用 MinerU 云 API 解析
  -> 导出 normalized_docs.json / section_docs.json / page_docs.json
  -> graphrag_pipeline 从 MinIO 拉取 JSON 到 input/
  -> graphrag index --root .
  -> output/*.parquet + output/lancedb/
  -> FastAPI /v1/chat/completions
```

这条链路也是当前最值得维护和继续补强的项目主线。

## 建议阅读顺序

第一次进入仓库，建议按这个顺序建立上下文：

1. [AGENTS.md](AGENTS.md)
2. [pdf_ingest/README.md](pdf_ingest/README.md)
3. [pdf_ingest/CLAUDE.md](pdf_ingest/CLAUDE.md)
4. [graphrag_pipeline/README.md](graphrag_pipeline/README.md)
5. [graphrag_pipeline/CLAUDE.md](graphrag_pipeline/CLAUDE.md)

按需补充：

- [docs/标准化导出验证说明.md](docs/标准化导出验证说明.md)
- [pdf_ingest/docs/MinerU PDF Parser.md](<pdf_ingest/docs/MinerU PDF Parser.md>)
- [pdf_ingest/docs/课程文本规范与预处理流程.md](pdf_ingest/docs/课程文本规范与预处理流程.md)
- [graphrag_pipeline/PROMPT_TUNING_PIPELINE.md](graphrag_pipeline/PROMPT_TUNING_PIPELINE.md)
- [frontend/apps/student-app/README.md](frontend/apps/student-app/README.md)
- [frontend/apps/admin-app/README.md](frontend/apps/admin-app/README.md)
- [docs/admin-teacher-frontend-structure.md](docs/admin-teacher-frontend-structure.md)
- [backend/ckqa-back/README.md](backend/ckqa-back/README.md)

## 目录导航

```text
ckqa/
├── AGENTS.md
├── README.md
├── docs/
├── scripts/
│   └── audit_repo_drift.py
├── infra/
│   ├── README.md
│   └── docker-compose.yml
├── sql/
│   ├── ocqa.sql
│   └── migrations/
├── pdf_ingest/
│   ├── README.md
│   ├── CLAUDE.md
│   ├── scripts/pdf_processor/
│   ├── tests/
│   └── docs/
├── graphrag_pipeline/
│   ├── README.md
│   ├── CLAUDE.md
│   ├── scripts/
│   ├── utils/
│   ├── tests/
│   ├── prompts/
│   ├── input/
│   └── output/
├── frontend/apps/admin-app/
│   ├── README.md
│   └── src/
├── frontend/apps/student-app/
│   ├── README.md
│   └── src/
└── backend/ckqa-back/
    ├── README.md
    ├── pom.xml
    └── src/
```

## 模块入口

### `infra/`

- 角色：本地 MySQL、MinIO、One API、Neo4j 统一容器入口
- 主入口：`docker-compose.yml`
- 运行态数据：MySQL 与 MinIO 保留本机既有外部挂载，Neo4j 与 One API 数据位于 `infra/` 下并被 Git 忽略
- 文档入口：[infra/README.md](infra/README.md)

### `sql/`

- 角色：仓库级 MySQL schema 与迁移脚本来源
- 主入口：`ocqa.sql`
- 当前迁移：课程资料、QA 会话类型、角色测试数据、知识库 build-run、课程封面、成员权限测试数据、JWT 凭据、用户头像和课程资料管理
- 使用边界：从 `pdf_ingest/` 或 Java 后端引用 SQL 时都应回到仓库根目录 `sql/`

### `pdf_ingest/`

- 角色：课程 PDF 解析、标准化与导出
- 主入口：`scripts/pdf_processor/mineru_parser.py`
- 关键产物：`normalized_docs.json`、`section_docs.json`、`page_docs.json`
- 本地清理：`scripts/cleanup_legacy_course_data.py` 可 dry-run / 执行清理旧版课程 ID 的 MySQL 与 MinIO 数据
- 运行环境：`courseKg`
- 文档入口：[pdf_ingest/README.md](pdf_ingest/README.md)

### `graphrag_pipeline/`

- 角色：GraphRAG 输入同步、索引、问答 API
- 主入口：`utils/fetch_from_minio.py`、`utils/main.py`
- 配套脚本：`scripts/build_prompt_tuning_samples.py`、`scripts/build_audit_extraction_set.py`、`scripts/generate_candidate_prompts.py`、`scripts/run_graphrag_prompt_tune.py`、`scripts/finalize_candidate_prompt.py`
- 脚本分层：实现代码见 `graphrag_pipeline/scripts/README.md`，根目录同名脚本保留兼容入口
- 关键产物：手工 CLI 调试使用 `input/*.json`、`output/*.parquet`、`output/lancedb/`；Java 管理端构建使用 `runtime/kb-build-runs/user_*/kb_*/build_*/index/input|output|logs`
- 当前重建前状态：旧输入、旧索引和旧调优产物已清空，默认 Prompt 回到 `prompts/*.txt` 基础模板
- 运行环境：`graphrag-oneapi`
- 文档入口：[graphrag_pipeline/README.md](graphrag_pipeline/README.md)

### `frontend/apps/student-app/`

- 角色：学员端前端原型
- 主入口：`src/main.js`、`src/router/index.js`
- 当前状态：页面、路由和交互原型比 `admin-app` 更完整，但多数数据仍来自本地 store，未与主链路建立稳定契约
- 文档入口：[frontend/apps/student-app/README.md](frontend/apps/student-app/README.md)

### `frontend/apps/admin-app/`

- 角色：管理员端/教师端共用控制台前端
- 主入口：`src/App.vue`
- 当前状态：已落地运维台壳层、主题系统、工作台、系统健康页、课程/资料/知识库 live 页面、课程资料上传、知识库构建向导、QA 冒烟验证、统一错误页和 Playwright E2E 故障注入；正式业务流只访问 Java `/api/v1`
- 文档入口：[frontend/apps/admin-app/README.md](frontend/apps/admin-app/README.md)
- 结构设计入口：[docs/admin-teacher-frontend-structure.md](docs/admin-teacher-frontend-structure.md)

### `backend/ckqa-back/`

- 角色：Java 一期编排后端
- 主入口：`src/main/java/org/ysu/ckqaback/CkqaBackApplication.java`
- 当前状态：已经具备 `/api/v1` 统一响应、MyBatis-Plus 标准表代码、课程/资料/知识库读接口、PDF/索引/异步问答编排、QA 冒烟会话和系统健康检查；仍不是 Python 主链路的替代实现
- 文档入口：[backend/ckqa-back/README.md](backend/ckqa-back/README.md)

## 端到端最短跑通路径

下面这条路径是当前最稳妥的最小可运行方案。

### 1. 解析课程 PDF

```bash
cd pdf_ingest
conda activate courseKg
pip install -e ".[dev]"

python scripts/pdf_processor/mineru_parser.py upload <course_id> -f data/<course_id>/book.pdf --parse
python scripts/pdf_processor/mineru_parser.py export-graphrag <course_id> --material-id <material_id> --mode section --with-page-docs
```

当前共享开发环境里的 `courseKg` 已安装 `pytest`，进入模块目录后可直接执行 `python -m pytest tests/` 做快速验证；如果是新环境，仍建议按上面的开发依赖安装方式准备。

### 2. 同步到 GraphRAG 输入目录

```bash
cd ../graphrag_pipeline
conda activate graphrag-oneapi
pip install -e ".[all]"

python utils/fetch_from_minio.py <course_id> --material-id <material_id> --clean
```

当前共享开发环境里的 `graphrag-oneapi` 也已安装 `pytest`，可直接执行 `python -m pytest tests/`；如果是新环境，仅执行 `pip install -e ".[all]"` 还不够，需再补装 `pytest`。

### 3. 建索引

```bash
graphrag index --root .
```

### 4. 启动问答 API

```bash
python utils/main.py
```

### 5. 可选：启动 Java 编排后端

如果需要验证 `/api/v1` 业务入口，先保持 GraphRAG API 可访问，再启动 Java 后端：

```bash
cd ../backend/ckqa-back
./mvnw spring-boot:run
```

常用入口：

```bash
curl http://127.0.0.1:8080/api/v1/system/health
```

## 当前需要特别记住的事实

- 多 PDF 课程下，`pdf_ingest` 侧优先使用 `--material-id`，旧 `--file-id` / `--file-name` 仅作为兼容入口；`graphrag_pipeline` 侧优先使用 `--material-id`。
- `normalized_docs.json` 主要用于人工验收和字段保真检查；GraphRAG 默认更直接消费 `section_docs.json` / `page_docs.json`。
- `graphrag_pipeline/utils/main.py` 会优先读取仓库内 `.env` / 当前环境变量，默认输出目录是仓库内 `output/`，并统一通过 `graphrag query` 提供查询能力。
- `graphrag_pipeline` 当前活动 Prompt 由 `.env` 中的 `GRAPHRAG_*_PROMPT_FILE` 决定；旧调优产物清理后默认候选为 `base`，指向 `prompts/*.txt`，只有重新固化候选 Prompt 后才会生成 `prompts/final/active_prompt.json`。
- 本地重新抽取前如果要清空旧课程运行态数据，使用 `cd pdf_ingest && python scripts/cleanup_legacy_course_data.py --env-file .env` 先 dry-run，再加 `--execute` 执行；默认会把不符合 `crs-YYYYMMDD-HHMMSS` 的课程 ID 当作旧课程。
- `graphrag_pipeline/output/` 里的 parquet 与 `output/lancedb/` 对手工 CLI 查询缺一不可；Java 管理端构建会把输入、输出、日志和 QA smoke 快照隔离到 `runtime/kb-build-runs/` 下。
- `backend/ckqa-back/` 的问答链路通过 `graphrag_pipeline` 的 `/v1/query-tasks` 异步任务接口工作；跨服务时间字段对外统一按 `Asia/Shanghai` 的无偏移 `LocalDateTime` 字符串解释。
- `backend/ckqa-back/` 与 `frontend/apps/admin-app/` 的课程资料上传上限已经统一为单个 PDF 默认 200MB；如果调整 `COURSE_MATERIAL_MAX_FILE_SIZE_BYTES` 或 Spring multipart 限制，需要同步前端 `material-file-model.js` 校验文案。
- 当前共享开发环境的两个 Python 环境 `courseKg` 与 `graphrag-oneapi` 都已安装 `pytest`，各模块目录下可直接运行 `python -m pytest tests/` 做基础回归。
- 仓库根目录 `scripts/` 现在只保留仓库级工具；模块专属脚本统一收口到各自子模块目录，例如 `graphrag_pipeline/scripts/`。
- `frontend/apps/student-app/` 现已按 CKQA 根仓库下的普通子目录管理；依赖安装与构建产物继续由该目录自己的 `.gitignore` 约束，包管理以 `pnpm-lock.yaml` 为准。
- `pdf_ingest` 和 `graphrag_pipeline` 都已进入课程资料模型：`material_objects` 按 MD5 去重物理对象，`course_materials` 维护课程内资料关系，解析状态、导出产物和日志按 `course_material_id` 隔离。
- 任何涉及导出字段、命名或 MinIO 路径的改动，都必须同时检查上下游契约兼容性。
- 活跃入口文档和关键脚本可通过 `python scripts/audit_repo_drift.py --strict` 做仓库级漂移审计。

## 文档地图

### 项目级

- [AGENTS.md](AGENTS.md)
- [docs/student-backend-graphrag-api-contract.md](docs/student-backend-graphrag-api-contract.md)
- [docs/标准化导出验证说明.md](docs/标准化导出验证说明.md)
- [docs/archive/MIGRATION_GRAPHRAG_3_0_9.md](docs/archive/MIGRATION_GRAPHRAG_3_0_9.md)（历史归档）
- [docs/archive/GRAPHRAG_3_0_9_VERIFICATION_CHECKLIST.md](docs/archive/GRAPHRAG_3_0_9_VERIFICATION_CHECKLIST.md)（历史归档）
- [docs/archive/GRAPHRAG_3_0_9_ROLLBACK_PLAN.md](docs/archive/GRAPHRAG_3_0_9_ROLLBACK_PLAN.md)（历史归档）

### 模块级

- [pdf_ingest/README.md](pdf_ingest/README.md)
- [pdf_ingest/CLAUDE.md](pdf_ingest/CLAUDE.md)
- [graphrag_pipeline/README.md](graphrag_pipeline/README.md)
- [graphrag_pipeline/CLAUDE.md](graphrag_pipeline/CLAUDE.md)
- [frontend/apps/student-app/README.md](frontend/apps/student-app/README.md)
- [frontend/apps/admin-app/README.md](frontend/apps/admin-app/README.md)
- [docs/admin-teacher-frontend-structure.md](docs/admin-teacher-frontend-structure.md)
- [backend/ckqa-back/README.md](backend/ckqa-back/README.md)
- [docs/superpowers/archive/README.md](docs/superpowers/archive/README.md)（已完成设计/计划归档索引）
  - 已归档：异步 QA 任务化设计/计划、课程资料模型实施计划、管理员端 UI 重构、管理端真实数据接入、Element Plus 样式重构。
