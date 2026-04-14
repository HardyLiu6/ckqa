# CKQA 项目入口文档

CKQA 是一个面向课程资料的知识图谱问答项目。当前仓库里最重要的主线仍然是两个 Python 模块：

1. `pdf_ingest/`
   负责上传课程 PDF、调用 MinerU 云 API 解析、把原始文件和解析产物写入 MinIO、把状态和元数据写入 MySQL，并导出标准化文档与 GraphRAG 输入。
2. `graphrag_pipeline/`
   负责从 MinIO 拉取 `pdf_ingest` 导出的 JSON，使用 Microsoft GraphRAG 建索引，并通过 FastAPI 提供 OpenAI 兼容问答接口。

仓库里还有两个次要区域：

3. `frontend/apps/admin-app/`
   一个独立的 Vue 3 + Vite 管理端原型，目前还没有接进主链路。
4. `backend/ckqa-back/`
   一个 Spring Boot 4 骨架工程，当前不是主业务入口。

本文档以当前仓库代码实现为准；如果与旧文档、旧注释冲突，请优先相信代码和依赖配置。

## 先看哪几份文档

建议按这个顺序进入项目：

1. [AGENTS.md](AGENTS.md)
2. [pdf_ingest/CLAUDE.md](pdf_ingest/CLAUDE.md)
3. [graphrag_pipeline/CLAUDE.md](graphrag_pipeline/CLAUDE.md)

按需补充：

- [docs/标准化导出验证说明.md](docs/标准化导出验证说明.md)
- [pdf_ingest/docs/MinerU PDF Parser.md](pdf_ingest/docs/MinerU%20PDF%20Parser.md)
- [graphrag_pipeline/README.md](graphrag_pipeline/README.md)
- [frontend/apps/admin-app/README.md](frontend/apps/admin-app/README.md)

## 仓库结构

```text
ckqa/
├── pdf_ingest/                  # PDF 解析、标准化、GraphRAG 导出
├── graphrag_pipeline/           # 建图、索引、问答 API
├── frontend/apps/admin-app/     # Vue 3 + Vite 管理端原型
├── backend/ckqa-back/           # Spring Boot 4 骨架
├── docs/                        # 跨模块文档与验证说明
├── AGENTS.md                    # 仓库协作约定
├── .codex                       # Codex 项目入口配置
└── README.md                    # 当前入口文档
```

## 当前主流程

```text
课程 PDF
  -> pdf_ingest 上传到 MinIO，登记到 MySQL
  -> 调用 MinerU 云 API 解析
  -> 清洗并导出 normalized_docs.json / section_docs.json / page_docs.json
  -> graphrag_pipeline 从 MinIO 拉取 JSON 到 input/
  -> graphrag index --root .
  -> output/*.parquet + output/lancedb/
  -> FastAPI /v1/chat/completions
```

## 关键现实情况

- `graphrag_pipeline/pyproject.toml` 当前固定的是 `graphrag==3.0.9`。仓库里仍有一些旧注释、旧 README 写着 `2.7.0`，那部分已经不是最新事实。
- `graphrag_pipeline/utils/main.py` 还保留了 2.x 风格内部 API 的导入逻辑，但如果导入失败，会自动退回到 GraphRAG CLI 查询兼容模式。
- GraphRAG 输入主流程已经是 `json`，不是旧流程里的 `input/*.txt`。
- `pdf_ingest` 对下游最重要的导出物是 `section_docs.json` / `page_docs.json`；`normalized_docs.json` 更适合人工验收、抽样检查和字段对照。

## 模块职责

### 1. `pdf_ingest`

关键入口：

- [mineru_parser.py](pdf_ingest/scripts/pdf_processor/mineru_parser.py)
- [graphrag_exporter.py](pdf_ingest/scripts/pdf_processor/graphrag_exporter.py)
- [db_service.py](pdf_ingest/scripts/pdf_processor/db_service.py)
- [storage_service.py](pdf_ingest/scripts/pdf_processor/storage_service.py)

核心职责：

- 管理课程与 PDF 文件元数据。
- 调用 MinerU API 解析 PDF。
- 将原始 PDF 与解析产物存入 MinIO。
- 使用 MySQL 跟踪解析状态 `pending -> processing -> done/failed`。
- 导出 `normalized_docs.json` 与 GraphRAG 投影 JSON。

当前实现要点：

- PDF 按全局 `MD5` 去重，同一份文件不能同时归属多个课程。
- 同一课程下允许多份 PDF，但同课程内文件名唯一。
- 多 PDF 场景下，后续 `parse/status/download/export-graphrag` 最好显式传 `--file-id` 或 `--file-name`。
- 默认导出目录前缀是 MinIO 中的 `graphrag/pdf_<file_id>/`，可通过 `--output-prefix` 调整。

典型命令：

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

### 2. `graphrag_pipeline`

关键入口：

- [pyproject.toml](graphrag_pipeline/pyproject.toml)
- [settings.yaml](graphrag_pipeline/settings.yaml)
- [fetch_from_minio.py](graphrag_pipeline/utils/fetch_from_minio.py)
- [main.py](graphrag_pipeline/utils/main.py)

核心职责：

- 从 MinIO 获取 `section_docs.json`、`page_docs.json` 或 `normalized_docs.json`。
- 把输入写入 `input/`，供 GraphRAG 直接建索引。
- 读取 `output/` 下的 parquet 和 `lancedb/`，构建搜索能力。
- 暴露 OpenAI 兼容接口。

当前实现细节：

- `settings.yaml` 已配置 `input.type: json`，GraphRAG 直接读取 JSON 数组。
- `fetch_from_minio.py` 会优先下载 JSON；若拿到旧版 `jsonl`，会自动转换为 JSON 数组。
- `fetch_from_minio.py` 会把关键 metadata 提升到顶层，便于 GraphRAG 收集 `page_start`、`page_end`、`section_level` 等字段。
- API 可用模型名是：
  - `graphrag-local-search:latest`
  - `graphrag-global-search:latest`
  - `full-model:latest`
- API 服务依赖 `output/` 下的 parquet 文件和 `output/lancedb/`，缺一不可。

典型命令：

```bash
cd graphrag_pipeline
conda activate graphrag-oneapi
pip install -e ".[all]"

python utils/fetch_from_minio.py os --clean
python utils/fetch_from_minio.py os --pdf-file-id 3 --clean
python utils/fetch_from_minio.py os --pdf-file-id 3 --json-file page_docs.json --clean
python utils/fetch_from_minio.py os --pdf-file-id 3 --json-file normalized_docs.json --clean

graphrag index --root .
graphrag query --root . --method local --query "问题"
graphrag query --root . --method global --query "问题"

python utils/main.py
```

当前 API 主要端点：

- `GET /health`
- `GET /v1/models`
- `POST /v1/chat/completions`

### 3. `frontend/apps/admin-app`

这是一个独立的 Vue 3 + Vite 原型项目，目前更多是前端占位和试验场，还没有接入主链路。

典型命令：

```bash
cd frontend/apps/admin-app
npm install
npm run dev
npm run build
```

当前注意事项：

- `node_modules/` 已在仓库中出现，但它是依赖目录，不是源码。
- 目前没有统一的前端 workspace、共享组件库或现成的后端对接配置。

### 4. `backend/ckqa-back`

当前只有最小 Spring Boot 4.0.5 启动骨架和初始化生成的帮助文档，尚未承接主要业务逻辑，可暂时视为预留模块。

## 端到端最短路径

如果你要从一份课程 PDF 快速走到问答接口，可以按这个顺序操作：

1. 在 `pdf_ingest/` 上传并解析 PDF。
2. 在 `pdf_ingest/` 运行 `export-graphrag` 生成 `section_docs.json`。
3. 在 `graphrag_pipeline/` 用 `fetch_from_minio.py` 拉取对应课程或指定 `pdf_file_id` 的 JSON。
4. 在 `graphrag_pipeline/` 执行 `graphrag index --root .`。
5. 在 `graphrag_pipeline/` 启动 `python utils/main.py`。

## 重要注意事项

### 1. 多 PDF 课程必须注意文件定位

当前代码已支持一个课程对应多份 PDF，这会带来课程内文件歧义。遇到这种情况时：

- `pdf_ingest` 侧优先使用 `--file-id`
- `graphrag_pipeline` 侧优先使用 `--pdf-file-id`

### 2. `graphrag_pipeline/utils/main.py` 使用硬编码运行时配置

这点非常关键。当前 API 服务运行时不会自动读取 `settings.yaml` 的全部配置，也不会自动和 `.env` 保持同步。启动前请先检查 `utils/main.py` 顶部的常量，例如：

- `GRAPHRAG_ROOT`
- `LANCEDB_URI`
- `LLM_API_BASE`
- `LLM_MODEL`
- `EMBEDDING_MODEL`

此外，文件中还存在硬编码凭据与仓库外路径的历史配置，不能把它们直接当成可复用示例。

### 3. 旧文档与当前实现仍有分叉

当前已经确认的分叉主要有：

- GraphRAG 依赖版本实际是 `3.0.9`，不是旧文档里反复出现的 `2.7.0`。
- GraphRAG 输入当前以 `json` 为主，不再是旧版 `txt` 主流程。
- `graphrag_pipeline` 目录里不存在旧 README 提到的 `back/` 目录，当前主入口是 `utils/main.py`。

### 4. 配置和密钥不应提交到文档

仓库里包含 `.env` 与硬编码配置，但入口文档不应复述任何真实密钥、口令或令牌。配置时请只参考变量名和配置位置，不要扩散已有敏感值。

## 相关文档

- [AGENTS.md](AGENTS.md)
- [.codex](.codex)
- [pdf_ingest/CLAUDE.md](pdf_ingest/CLAUDE.md)
- [pdf_ingest/docs/MinerU PDF Parser.md](pdf_ingest/docs/MinerU%20PDF%20Parser.md)
- [graphrag_pipeline/CLAUDE.md](graphrag_pipeline/CLAUDE.md)
- [graphrag_pipeline/README.md](graphrag_pipeline/README.md)
