# CKQA 项目入口文档

> 审计日期：2026-04-15  
> 本文档基于当前仓库代码、目录和配置重新整理，用来回答三个最核心的问题：主入口在哪、哪些模块真的可用、最稳的跑通路径是什么。

CKQA 是一个面向课程的混合型问答项目。当前仓库里真正承担主业务链路的，仍然是两个 Python 模块：

1. `pdf_ingest/`
   负责上传课程 PDF、调用 MinerU 云 API 解析、写入 MinIO 与 MySQL，并导出标准化文档与 GraphRAG 输入。
2. `graphrag_pipeline/`
   负责拉取导出的 JSON、执行 Microsoft GraphRAG 建索引，并通过 FastAPI 提供 OpenAI 兼容问答接口。

其余两个目录目前仍是辅助或预留状态：

3. `frontend/apps/admin-app/`
   Vue 3 + Vite 管理端原型，尚未接入主链路。
4. `backend/ckqa-back/`
   Spring Boot 4 + Java 21 骨架工程，当前不是主业务入口。

如果文档、注释和代码不一致，请优先相信代码、目录和 `pyproject.toml` 的真实依赖定义。

## 本次审计结论

- 当前唯一稳定主链路仍然是 `pdf_ingest -> graphrag_pipeline`。
- `graphrag_pipeline/pyproject.toml` 中真实依赖固定为 `graphrag==3.0.9`，但仓库里仍残留多处 `2.7.0` 文案和注释。
- `frontend/apps/admin-app/` 目前是独立前端原型，`backend/ckqa-back/` 目前只是最小可启动骨架。
- 仓库中已经存在 `graphrag_pipeline/input/section_docs.json` 和部分 `graphrag_pipeline/output/*.parquet` 快照，但当前未看到完整的 `output/lancedb/` 产物，不能把仓库现状视为“API 已可直接启动”。
- 自动化测试目前主要集中在 `pdf_ingest/`；`graphrag_pipeline/` 当前只有 `tests/test_fetch_from_minio.py`，Java 后端只有默认启动测试。

## 建议阅读顺序

首次进入仓库，建议按这个顺序建立上下文：

1. [AGENTS.md](AGENTS.md)
2. [pdf_ingest/CLAUDE.md](pdf_ingest/CLAUDE.md)
3. [graphrag_pipeline/CLAUDE.md](graphrag_pipeline/CLAUDE.md)

按需补充阅读：

- [docs/标准化导出验证说明.md](docs/标准化导出验证说明.md)
- [pdf_ingest/docs/MinerU PDF Parser.md](pdf_ingest/docs/MinerU%20PDF%20Parser.md)
- [pdf_ingest/docs/课程文本规范与预处理流程.md](pdf_ingest/docs/课程文本规范与预处理流程.md)
- [graphrag_pipeline/README.md](graphrag_pipeline/README.md)
- [graphrag_pipeline/PROMPT_TUNING_PIPELINE.md](graphrag_pipeline/PROMPT_TUNING_PIPELINE.md)
- [frontend/apps/admin-app/README.md](frontend/apps/admin-app/README.md)

## 仓库结构

```text
ckqa/
├── AGENTS.md
├── README.md
├── .codex
├── ckqa.code-workspace
├── docs/
│   ├── 标准化导出验证说明.md
│   ├── MIGRATION_GRAPHRAG_3_0_9.md
│   ├── GRAPHRAG_3_0_9_VERIFICATION_CHECKLIST.md
│   └── GRAPHRAG_3_0_9_ROLLBACK_PLAN.md
├── pdf_ingest/
│   ├── CLAUDE.md
│   ├── pyproject.toml
│   ├── scripts/pdf_processor/
│   ├── tests/
│   ├── docs/
│   ├── data/
│   ├── output/
│   └── sql/
├── graphrag_pipeline/
│   ├── CLAUDE.md
│   ├── README.md
│   ├── pyproject.toml
│   ├── settings.yaml
│   ├── input/
│   ├── output/
│   ├── prompts/
│   ├── scripts/
│   ├── tests/
│   └── utils/
├── frontend/apps/admin-app/
└── backend/ckqa-back/
```

## 模块现状

### 1. `pdf_ingest/`

这是当前最完整、最接近生产主流程的模块。

关键入口：

- [mineru_parser.py](pdf_ingest/scripts/pdf_processor/mineru_parser.py)
- [graphrag_exporter.py](pdf_ingest/scripts/pdf_processor/graphrag_exporter.py)
- [block_model.py](pdf_ingest/scripts/pdf_processor/block_model.py)
- [block_renderer.py](pdf_ingest/scripts/pdf_processor/block_renderer.py)
- [text_cleaner.py](pdf_ingest/scripts/pdf_processor/text_cleaner.py)
- [db_service.py](pdf_ingest/scripts/pdf_processor/db_service.py)
- [storage_service.py](pdf_ingest/scripts/pdf_processor/storage_service.py)
- [export_audit.py](pdf_ingest/scripts/pdf_processor/export_audit.py)

当前职责：

- 上传课程 PDF 到 MinIO
- 通过 MinerU 云 API 解析 PDF
- 用 MySQL 跟踪 `pending -> processing -> done/failed`
- 导出 `normalized_docs.json`
- 导出供下游使用的 `section_docs.json` / `page_docs.json`

当前实现要点：

- 一个课程可以有多份 PDF。
- 同一课程内文件名必须唯一。
- 系统按全局 `MD5` 去重，同一份 PDF 不能同时归属多个课程。
- 进入多 PDF 场景后，后续 `parse/status/download/export-graphrag` 最好显式传 `--file-id` 或 `--file-name`。
- `normalized_docs.json` 更适合人工验收与字段对照，`section_docs.json` / `page_docs.json` 才是下游 GraphRAG 直接消费的投影结果。

环境与命令：

```bash
cd pdf_ingest
conda activate courseKg
pip install -e ".[dev]"

python scripts/pdf_processor/mineru_parser.py upload os -f data/os/book.pdf --parse
python scripts/pdf_processor/mineru_parser.py parse os --file-id 3
python scripts/pdf_processor/mineru_parser.py status os --file-id 3
python scripts/pdf_processor/mineru_parser.py download os --file-id 3 -o ./output
python scripts/pdf_processor/mineru_parser.py export-graphrag os --file-id 3 --mode section
python scripts/pdf_processor/mineru_parser.py export-graphrag os --file-id 3 --mode section --with-page-docs

python -m pytest tests/
```

### 2. `graphrag_pipeline/`

这是当前知识图谱建索引与问答服务模块。

关键入口：

- [pyproject.toml](graphrag_pipeline/pyproject.toml)
- [settings.yaml](graphrag_pipeline/settings.yaml)
- [fetch_from_minio.py](graphrag_pipeline/utils/fetch_from_minio.py)
- [main.py](graphrag_pipeline/utils/main.py)

当前职责：

- 从 MinIO 拉取 `section_docs.json`、`page_docs.json` 或 `normalized_docs.json`
- 把输入写入 `input/`
- 执行 `graphrag index --root .`
- 读取 `output/*.parquet` 与 `output/lancedb/`
- 通过 FastAPI 暴露 OpenAI 兼容接口

当前实现要点：

- `settings.yaml` 已切到 `input.type: json`，GraphRAG 当前主流程直接消费 JSON 数组。
- `fetch_from_minio.py` 默认拉取 `section_docs.json`，也兼容历史 `*.jsonl` 文件并自动转换。
- 该脚本会尽量把 `course_id`、`source_file`、`document_type`、`heading_path_text`、`page_start`、`page_end`、`section_level` 等字段提升到顶层。
- `utils/main.py` 仍保留 2.x 风格内部 API 导入逻辑；如果内部导入失败，会退回到 CLI 查询兼容模式。
- 当前仓库里虽然已有 `input/section_docs.json` 和部分 `output/` 快照，但不能据此假设服务端可以直接工作，完整索引产物仍应重新生成。

环境与命令：

```bash
cd graphrag_pipeline
conda activate graphrag-oneapi
pip install -e ".[all]"

python utils/fetch_from_minio.py os --clean
python utils/fetch_from_minio.py os --pdf-file-id 3 --clean
python utils/fetch_from_minio.py os --pdf-file-id 3 --json-file page_docs.json --clean
python utils/fetch_from_minio.py os --pdf-file-id 3 --json-file normalized_docs.json --input-dir ./tmp_validate/os/normalized --clean

graphrag index --root .
graphrag query --root . --method local --query "问题"
graphrag query --root . --method global --query "问题"

python utils/main.py
```

当前 API 模型名：

- `graphrag-local-search:latest`
- `graphrag-global-search:latest`
- `full-model:latest`

当前 API 端点：

- `GET /health`
- `GET /v1/models`
- `POST /v1/chat/completions`

### 3. `frontend/apps/admin-app/`

这是一个独立的 Vue 3 + Vite 原型项目，目前主要承担前端占位和页面实验用途。

当前事实：

- 技术栈是 Vue 3.5 + Vite 8。
- 目录下已有 `node_modules/`，但它是依赖目录，不是源码。
- 目前没有统一 workspace、共享组件库，也没有正式接入主链路 API。

典型命令：

```bash
cd frontend/apps/admin-app
npm install
npm run dev
npm run build
npm run preview
```

### 4. `backend/ckqa-back/`

这是一个最小 Spring Boot 4.0.5 骨架工程，使用 Java 21。

当前事实：

- 代码目前基本只有启动类和默认测试。
- `application.properties` 仍为空白起步状态。
- 现阶段不应把它视为主业务 API 层。

## 当前主流程

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

## 端到端最短路径

下述命令默认建立在“你已经激活对应 conda 环境，并且环境内存在 `python` 命令”的前提上。若未激活环境，当前机器上可能需要使用 `python3`。

### 1. 解析并导出课程 PDF

```bash
cd pdf_ingest
conda activate courseKg
pip install -e ".[dev]"

python scripts/pdf_processor/mineru_parser.py upload os -f data/os/book.pdf --parse
python scripts/pdf_processor/mineru_parser.py export-graphrag os --file-id 3 --mode section --with-page-docs
```

### 2. 同步 GraphRAG 输入并建索引

```bash
cd ../graphrag_pipeline
conda activate graphrag-oneapi
pip install -e ".[all]"

python utils/fetch_from_minio.py os --pdf-file-id 3 --clean
graphrag index --root .
```

### 3. 启动问答 API

```bash
python utils/main.py
```

## 当前最需要记住的注意事项

- 多 PDF 课程下，`pdf_ingest` 侧优先使用 `--file-id`，`graphrag_pipeline` 侧优先使用 `--pdf-file-id`。
- `normalized_docs.json` 主要用于人工验收、字段保真检查和问题定位；GraphRAG 默认更关注 `section_docs.json` / `page_docs.json`。
- `graphrag_pipeline/utils/main.py` 的 API 运行时配置并不会自动与 `.env`、`settings.yaml` 同步，启动前必须人工核对。
- `utils/main.py` 中仍包含历史路径和硬编码凭据示例，不应把这些值复制到新环境，也不应把它们当成推荐配置方式。
- `graphrag_pipeline/pyproject.toml` 的依赖版本是 `3.0.9`，但它的 `description` 字段以及若干脚本头注释仍然带有 `2.7.0` 历史文案，阅读时要区分“真实依赖”和“遗留说明”。
- `graphrag_pipeline/output/` 中的 parquet 与 `output/lancedb/` 是一起构成服务依赖的，缺一不可。
- 任何涉及导出字段、命名和 MinIO 路径的改动，都必须同时检查 `pdf_ingest` 与 `graphrag_pipeline` 的契约兼容性。

## 相关文档

- [AGENTS.md](AGENTS.md)
- [pdf_ingest/CLAUDE.md](pdf_ingest/CLAUDE.md)
- [pdf_ingest/docs/MinerU PDF Parser.md](pdf_ingest/docs/MinerU%20PDF%20Parser.md)
- [pdf_ingest/docs/课程文本规范与预处理流程.md](pdf_ingest/docs/课程文本规范与预处理流程.md)
- [docs/标准化导出验证说明.md](docs/标准化导出验证说明.md)
- [docs/MIGRATION_GRAPHRAG_3_0_9.md](docs/MIGRATION_GRAPHRAG_3_0_9.md)
- [docs/GRAPHRAG_3_0_9_VERIFICATION_CHECKLIST.md](docs/GRAPHRAG_3_0_9_VERIFICATION_CHECKLIST.md)
- [docs/GRAPHRAG_3_0_9_ROLLBACK_PLAN.md](docs/GRAPHRAG_3_0_9_ROLLBACK_PLAN.md)
- [graphrag_pipeline/CLAUDE.md](graphrag_pipeline/CLAUDE.md)
- [graphrag_pipeline/README.md](graphrag_pipeline/README.md)
- [graphrag_pipeline/PROMPT_TUNING_PIPELINE.md](graphrag_pipeline/PROMPT_TUNING_PIPELINE.md)
- [frontend/apps/admin-app/README.md](frontend/apps/admin-app/README.md)
