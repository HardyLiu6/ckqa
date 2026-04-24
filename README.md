# CKQA 项目入口文档

> 审计日期：2026-04-23
> 目标：把仓库入口、模块边界、主链路和阅读顺序整理成一份可信的导航页。

CKQA 是一个面向课程资料的混合型问答系统。按当前仓库代码、目录和依赖配置来看，知识生产与问答能力的主链路仍然由两个 Python 模块承担：

1. `pdf_ingest/`
   负责课程 PDF 上传、MinerU 云解析、MinIO/MySQL 落库，以及标准化文档与 GraphRAG 输入导出。
2. `graphrag_pipeline/`
   负责拉取导出的 JSON、执行 Microsoft GraphRAG 建索引，并通过 FastAPI 提供 OpenAI 兼容问答接口。

其余目录目前属于编排入口或配套原型：

- `frontend/apps/student-app/`：学员端前端原型，界面与路由更完整，但当前仍以本地状态和占位路由为主，尚未接入稳定后端契约。
- `frontend/apps/admin-app/`：管理端前端原型，仍接近 Vite/Vue 起步页。
- `backend/ckqa-back/`：Spring Boot 4 + Java 21 一期编排入口，已经承接 `/api/v1` 下的课程、PDF、索引、异步问答和系统健康检查接口，但仍依赖 `pdf_ingest/` 与 `graphrag_pipeline/` 提供真实处理能力。

如果文档、注释和代码不一致，请优先相信目录结构、脚本入口、`pyproject.toml` / `pom.xml` / `package.json` 里的真实定义。

## 一眼看懂仓库

| 板块 | 当前角色 | 状态 | 入口文档 |
| --- | --- | --- | --- |
| `pdf_ingest/` | PDF 解析与标准化导出 | 主链路，最完整 | [pdf_ingest/README.md](pdf_ingest/README.md) |
| `graphrag_pipeline/` | GraphRAG 建图、检索、API | 主链路，依赖运行环境 | [graphrag_pipeline/README.md](graphrag_pipeline/README.md) |
| `frontend/apps/student-app/` | 学员端前端原型 | 独立原型，已有最小请求层但未接稳定业务契约 | [frontend/apps/student-app/README.md](frontend/apps/student-app/README.md) |
| `frontend/apps/admin-app/` | 管理端前端原型 | 独立原型，未接主链路 | [frontend/apps/admin-app/README.md](frontend/apps/admin-app/README.md) |
| `backend/ckqa-back/` | Java 编排后端 | 一期 `/api/v1` 编排接口，依赖 Python 主链路 | [backend/ckqa-back/README.md](backend/ckqa-back/README.md) |

## 本次审计结论

- 当前唯一稳定的知识生产与问答能力主链路仍然是 `pdf_ingest -> graphrag_pipeline`。
- `graphrag_pipeline` 的 GraphRAG 版本基线统一以 `pyproject.toml` 为准，当前固定为 `graphrag==3.0.9`。
- `backend/ckqa-back/` 已经不再是空骨架；它是 Java 一期编排入口，统一响应体为 `code / message / data / timestamp`，业务成功码为 `200`，但真实 PDF 解析、索引和问答仍调用两个 Python 模块。
- `frontend/apps/student-app/` 已经是一个比 `admin-app` 更完整的学员端原型，包含落地页、首页、问答页、课程页与 Pinia/Vue Router 基础结构，并已有最小 Axios 请求层；当前仍未接入稳定业务 API。
- `frontend/apps/admin-app/` 当前代码仍接近 Vite/Vue 起步页，不应被视为正式管理台。
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
- [backend/ckqa-back/README.md](backend/ckqa-back/README.md)

## 目录导航

```text
ckqa/
├── AGENTS.md
├── README.md
├── docs/
├── scripts/
│   └── audit_repo_drift.py
├── pdf_ingest/
│   ├── README.md
│   ├── CLAUDE.md
│   ├── scripts/pdf_processor/
│   ├── tests/
│   ├── docs/
│   └── sql/
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

### `pdf_ingest/`

- 角色：课程 PDF 解析、标准化与导出
- 主入口：`scripts/pdf_processor/mineru_parser.py`
- 关键产物：`normalized_docs.json`、`section_docs.json`、`page_docs.json`
- 运行环境：`courseKg`
- 文档入口：[pdf_ingest/README.md](pdf_ingest/README.md)

### `graphrag_pipeline/`

- 角色：GraphRAG 输入同步、索引、问答 API
- 主入口：`utils/fetch_from_minio.py`、`utils/main.py`
- 配套脚本：`scripts/build_prompt_tuning_samples.py`、`scripts/build_audit_extraction_set.py`、`scripts/generate_candidate_prompts.py`、`scripts/run_graphrag_prompt_tune.py`、`scripts/finalize_candidate_prompt.py`
- 脚本分层：实现代码见 `graphrag_pipeline/scripts/README.md`，根目录同名脚本保留兼容入口
- 关键产物：`input/*.json`、`output/*.parquet`、`output/lancedb/`
- 运行环境：`graphrag-oneapi`
- 文档入口：[graphrag_pipeline/README.md](graphrag_pipeline/README.md)

### `frontend/apps/student-app/`

- 角色：学员端前端原型
- 主入口：`src/main.js`、`src/router/index.js`
- 当前状态：页面、路由和交互原型比 `admin-app` 更完整，但多数数据仍来自本地 store，未与主链路建立稳定契约
- 文档入口：[frontend/apps/student-app/README.md](frontend/apps/student-app/README.md)

### `frontend/apps/admin-app/`

- 角色：前端页面原型与占位
- 主入口：`src/App.vue`
- 当前状态：仍是原型工程，尚未接主链路
- 文档入口：[frontend/apps/admin-app/README.md](frontend/apps/admin-app/README.md)

### `backend/ckqa-back/`

- 角色：Java 一期编排后端
- 主入口：`src/main/java/org/ysu/ckqaback/CkqaBackApplication.java`
- 当前状态：已经具备 `/api/v1` 统一响应、MyBatis-Plus 标准表代码、PDF/索引/异步问答编排和系统健康检查；仍不是 Python 主链路的替代实现
- 文档入口：[backend/ckqa-back/README.md](backend/ckqa-back/README.md)

## 端到端最短跑通路径

下面这条路径是当前最稳妥的最小可运行方案。

### 1. 解析课程 PDF

```bash
cd pdf_ingest
conda activate courseKg
pip install -e ".[dev]"

python scripts/pdf_processor/mineru_parser.py upload os -f data/os/book.pdf --parse
python scripts/pdf_processor/mineru_parser.py export-graphrag os --material-id 3 --mode section --with-page-docs
```

当前共享开发环境里的 `courseKg` 已安装 `pytest`，进入模块目录后可直接执行 `python -m pytest tests/` 做快速验证；如果是新环境，仍建议按上面的开发依赖安装方式准备。

### 2. 同步到 GraphRAG 输入目录

```bash
cd ../graphrag_pipeline
conda activate graphrag-oneapi
pip install -e ".[all]"

python utils/fetch_from_minio.py os --material-id 3 --clean
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
- `graphrag_pipeline` 当前活动 Prompt 由 `.env` 与 `prompts/final/active_prompt.json` 协同记录；如果切换了候选 Prompt，需要先执行 `python scripts/finalize_candidate_prompt.py --candidate <name>`，再重建索引。
- `graphrag_pipeline/output/` 里的 parquet 与 `output/lancedb/` 缺一不可。
- `backend/ckqa-back/` 的问答链路通过 `graphrag_pipeline` 的 `/v1/query-tasks` 异步任务接口工作；跨服务时间字段对外统一按 `Asia/Shanghai` 的无偏移 `LocalDateTime` 字符串解释。
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
- [backend/ckqa-back/README.md](backend/ckqa-back/README.md)
