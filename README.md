# CKQA 项目入口文档

> 审计日期：2026-04-16
> 目标：把仓库入口、模块边界、主链路和阅读顺序整理成一份可信的导航页。

CKQA 是一个面向课程资料的混合型问答系统。按当前仓库代码、目录和依赖配置来看，真正承担主业务链路的是两个 Python 模块：

1. `pdf_ingest/`
   负责课程 PDF 上传、MinerU 云解析、MinIO/MySQL 落库，以及标准化文档与 GraphRAG 输入导出。
2. `graphrag_pipeline/`
   负责拉取导出的 JSON、执行 Microsoft GraphRAG 建索引，并通过 FastAPI 提供 OpenAI 兼容问答接口。

其余目录目前仍属于配套原型或预留骨架：

- `frontend/apps/admin-app/`：Vue 3 + Vite 前端原型，尚未接入主链路。
- `backend/ckqa-back/`：Spring Boot 4 + Java 21 骨架工程，尚未承接正式业务接口。

如果文档、注释和代码不一致，请优先相信目录结构、脚本入口、`pyproject.toml` / `pom.xml` / `package.json` 里的真实定义。

## 一眼看懂仓库

| 板块 | 当前角色 | 状态 | 入口文档 |
| --- | --- | --- | --- |
| `pdf_ingest/` | PDF 解析与标准化导出 | 主链路，最完整 | [pdf_ingest/README.md](pdf_ingest/README.md) |
| `graphrag_pipeline/` | GraphRAG 建图、检索、API | 主链路，依赖运行环境 | [graphrag_pipeline/README.md](graphrag_pipeline/README.md) |
| `frontend/apps/admin-app/` | 管理端前端原型 | 独立原型，未接主链路 | [frontend/apps/admin-app/README.md](frontend/apps/admin-app/README.md) |
| `backend/ckqa-back/` | Java 后端骨架 | 最小可启动骨架 | [backend/ckqa-back/README.md](backend/ckqa-back/README.md) |

## 本次审计结论

- 当前唯一稳定的业务主链路仍然是 `pdf_ingest -> graphrag_pipeline`。
- `graphrag_pipeline` 的 GraphRAG 版本基线统一以 `pyproject.toml` 为准，当前固定为 `graphrag==3.0.9`。
- `frontend/apps/admin-app/` 当前代码仍接近 Vite/Vue 起步页，不应被视为正式管理台。
- `backend/ckqa-back/` 当前只有启动类、默认配置和默认测试，不应被视为正式服务入口。
- 文档阅读时要区分“主流程模块”和“占位模块”，不要把尚未集成的板块误判为可直接投入使用。

## 主流程

```text
课程 PDF
  -> pdf_ingest 上传到 MinIO，登记到 MySQL
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
- [frontend/apps/admin-app/README.md](frontend/apps/admin-app/README.md)
- [backend/ckqa-back/README.md](backend/ckqa-back/README.md)

## 目录导航

```text
ckqa/
├── AGENTS.md
├── README.md
├── docs/
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
│   ├── utils/
│   ├── tests/
│   ├── prompts/
│   ├── input/
│   └── output/
├── frontend/apps/admin-app/
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
- 关键产物：`input/*.json`、`output/*.parquet`、`output/lancedb/`
- 运行环境：`graphrag-oneapi`
- 文档入口：[graphrag_pipeline/README.md](graphrag_pipeline/README.md)

### `frontend/apps/admin-app/`

- 角色：前端页面原型与占位
- 主入口：`src/App.vue`
- 当前状态：仍是原型工程，尚未接主链路
- 文档入口：[frontend/apps/admin-app/README.md](frontend/apps/admin-app/README.md)

### `backend/ckqa-back/`

- 角色：Java 后端预留骨架
- 主入口：`src/main/java/org/ysu/ckqaback/CkqaBackApplication.java`
- 当前状态：只有最小 Spring Boot 启动结构
- 文档入口：[backend/ckqa-back/README.md](backend/ckqa-back/README.md)

## 端到端最短跑通路径

下面这条路径是当前最稳妥的最小可运行方案。

### 1. 解析课程 PDF

```bash
cd pdf_ingest
conda activate courseKg
pip install -e ".[dev]"

python scripts/pdf_processor/mineru_parser.py upload os -f data/os/book.pdf --parse
python scripts/pdf_processor/mineru_parser.py export-graphrag os --file-id 3 --mode section --with-page-docs
```

### 2. 同步到 GraphRAG 输入目录

```bash
cd ../graphrag_pipeline
conda activate graphrag-oneapi
pip install -e ".[all]"

python utils/fetch_from_minio.py os --pdf-file-id 3 --clean
```

### 3. 建索引

```bash
graphrag index --root .
```

### 4. 启动问答 API

```bash
python utils/main.py
```

## 当前需要特别记住的事实

- 多 PDF 课程下，`pdf_ingest` 侧优先使用 `--file-id` 或 `--file-name`，`graphrag_pipeline` 侧优先使用 `--pdf-file-id`。
- `normalized_docs.json` 主要用于人工验收和字段保真检查；GraphRAG 默认更直接消费 `section_docs.json` / `page_docs.json`。
- `graphrag_pipeline/utils/main.py` 会优先读取仓库内 `.env` / 当前环境变量，默认输出目录是仓库内 `output/`，并统一通过 `graphrag query` 提供查询能力。
- `graphrag_pipeline/output/` 里的 parquet 与 `output/lancedb/` 缺一不可。
- 任何涉及导出字段、命名或 MinIO 路径的改动，都必须同时检查上下游契约兼容性。
- 活跃入口文档和关键脚本可通过 `python scripts/audit_repo_drift.py --strict` 做仓库级漂移审计。

## 文档地图

### 项目级

- [AGENTS.md](AGENTS.md)
- [docs/标准化导出验证说明.md](docs/标准化导出验证说明.md)
- [docs/MIGRATION_GRAPHRAG_3_0_9.md](docs/MIGRATION_GRAPHRAG_3_0_9.md)
- [docs/GRAPHRAG_3_0_9_VERIFICATION_CHECKLIST.md](docs/GRAPHRAG_3_0_9_VERIFICATION_CHECKLIST.md)
- [docs/GRAPHRAG_3_0_9_ROLLBACK_PLAN.md](docs/GRAPHRAG_3_0_9_ROLLBACK_PLAN.md)

### 模块级

- [pdf_ingest/README.md](pdf_ingest/README.md)
- [pdf_ingest/CLAUDE.md](pdf_ingest/CLAUDE.md)
- [graphrag_pipeline/README.md](graphrag_pipeline/README.md)
- [graphrag_pipeline/CLAUDE.md](graphrag_pipeline/CLAUDE.md)
- [frontend/apps/admin-app/README.md](frontend/apps/admin-app/README.md)
- [backend/ckqa-back/README.md](backend/ckqa-back/README.md)
