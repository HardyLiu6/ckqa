# CKQA 项目入口文档

CKQA 是一个面向课程资料的知识图谱问答项目，当前主线实现由两个 Python 模块组成：

1. `pdf_ingest/`
   负责上传课程 PDF、调用 MinerU 云 API 解析、将原始文件和解析产物写入 MinIO、将状态和元数据写入 MySQL，并导出 GraphRAG 可直接 ingest 的 JSON。
2. `graphrag_pipeline/`
   负责从 MinIO 拉取 `pdf_ingest` 导出的 JSON，使用 Microsoft GraphRAG `2.7.0` 建立索引，并通过 FastAPI 提供 OpenAI 兼容问答接口。
3. `backend/ckqa-back/`
   当前仍是 Spring Boot 骨架项目，不是主业务实现入口。

本文档以当前仓库代码实现为准；如果与子模块中的旧文档冲突，请优先相信代码。

## 仓库结构

```text
ckqa/
├── pdf_ingest/           # PDF 解析与导出
├── graphrag_pipeline/    # GraphRAG 建图与问答
├── backend/ckqa-back/    # Java 骨架
├── AGENTS.md             # 仓库协作约定
└── README.md             # 当前入口文档
```

## 当前主流程

```text
课程 PDF
  -> pdf_ingest 上传到 MinIO，登记到 MySQL
  -> 调用 MinerU 云 API 解析
  -> 解析结果回写 MinIO / MySQL
  -> 导出 section_docs.json / page_docs.json
  -> graphrag_pipeline 从 MinIO 拉取 JSON 到 input/
  -> graphrag index --root .
  -> output/*.parquet + output/lancedb/
  -> FastAPI /v1/chat/completions
```

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
- 将 `content_list.json` 清洗、聚合为 GraphRAG 输入 JSON。

关键实现点：
- PDF 按全局 `MD5` 去重，同一份文件不能同时归属多个课程。
- 同一课程下允许多份 PDF，但同课程内文件名唯一。
- 多 PDF 场景下，后续 `parse/status/download/export-graphrag` 最好显式传 `--file-id` 或 `--file-name`。
- 导出结果默认写到 MinIO 的 `graphrag/pdf_<file_id>/` 下。

导出的 GraphRAG 输入目前是 JSON 数组，不是旧流程里的 `txt` 或 `jsonl`。典型记录类似：

```json
{
  "title": "os-前言",
  "text": "......",
  "course_id": "os",
  "source_file": "book.pdf",
  "section_level": 0,
  "page_start": 0,
  "page_end": 12
}
```

### 2. `graphrag_pipeline`

关键入口：
- [settings.yaml](graphrag_pipeline/settings.yaml)
- [fetch_from_minio.py](graphrag_pipeline/utils/fetch_from_minio.py)
- [main.py](graphrag_pipeline/utils/main.py)

核心职责：
- 从 MinIO 获取 `section_docs.json` 或 `page_docs.json`。
- 将输入写入 `input/`，供 GraphRAG `2.7.0` 建索引。
- 读取 `output/` 下的 parquet 和 `lancedb/`，构建本地搜索与全局搜索引擎。
- 暴露 OpenAI 兼容接口。

当前实现细节：
- `settings.yaml` 已配置 `input.file_type: json`，GraphRAG 直接读取 JSON。
- `fetch_from_minio.py` 会优先下载 JSON；若发现旧版 `jsonl`，会自动转成 JSON 数组。
- FastAPI 支持的模型名是：
  - `graphrag-local-search:latest`
  - `graphrag-global-search:latest`
  - `full-model:latest`
- API 服务依赖 `output/` 下的 parquet 文件和 `output/lancedb/`，缺一不可。

### 3. `backend/ckqa-back`

当前只有最小 Spring Boot 启动类和空测试，尚未承接主要业务逻辑，可暂时视为预留模块。

## 快速上手

### 先决条件

- Python `>=3.10`
- MySQL
- MinIO
- MinerU API 凭据
- GraphRAG 所需的 LLM / Embedding 服务

建议先分别阅读：
- [pdf_ingest/CLAUDE.md](pdf_ingest/CLAUDE.md)
- [graphrag_pipeline/CLAUDE.md](graphrag_pipeline/CLAUDE.md)

### 阶段一：PDF 解析与导出

进入 `pdf_ingest/` 后，使用项目约定环境：

```bash
conda activate courseKg
pip install -e ".[dev]"
```

初始化数据库：

```bash
mysql -u root -p < sql/ocqa.sql
```

准备 `.env` 后，常用命令如下：

```bash
python scripts/pdf_processor/mineru_parser.py upload os -f data/os/book.pdf
python scripts/pdf_processor/mineru_parser.py upload os -f data/os/book.pdf --parse
python scripts/pdf_processor/mineru_parser.py parse os --file-id 3
python scripts/pdf_processor/mineru_parser.py status os --file-id 3
python scripts/pdf_processor/mineru_parser.py download os --file-id 3 -o ./output
python scripts/pdf_processor/mineru_parser.py export-graphrag os --file-id 3 --mode section
python scripts/pdf_processor/mineru_parser.py export-graphrag os --file-id 3 --mode section --with-page-docs
python scripts/pdf_processor/mineru_parser.py list
```

`export-graphrag` 重点参数：
- `--mode section`
  按章节聚合，通常是默认推荐方式。
- `--mode page`
  按页聚合。
- `--with-page-docs`
  在章节模式基础上额外生成页面模式文档。
- `--semantic-table` / `--no-semantic-table`
  控制表格是否语义化。
- `--force`
  覆盖已有导出。

### 阶段二：GraphRAG 建索引与提供 API

进入 `graphrag_pipeline/` 后，使用项目约定环境：

```bash
conda activate graphrag-oneapi
pip install -e ".[all]"
```

从 MinIO 同步导出结果：

```bash
python utils/fetch_from_minio.py os --clean
python utils/fetch_from_minio.py os --pdf-file-id 3 --clean
python utils/fetch_from_minio.py os --pdf-file-id 3 --json-file page_docs.json --clean
```

建立索引：

```bash
graphrag index --root .
```

命令行查询：

```bash
graphrag query --root . --method local --query "问题"
graphrag query --root . --method global --query "问题"
```

启动 API：

```bash
python utils/main.py
```

当前 API 主要端点：
- `GET /health`
- `GET /v1/models`
- `POST /v1/chat/completions`

## 端到端最短路径

如果你要从一份课程 PDF 快速走到问答接口，可以按这个顺序操作：

1. 在 `pdf_ingest/` 上传并解析 PDF。
2. 在 `pdf_ingest/` 运行 `export-graphrag` 生成 `section_docs.json`。
3. 在 `graphrag_pipeline/` 用 `fetch_from_minio.py` 拉取对应课程或指定 `pdf_file_id` 的 JSON。
4. 在 `graphrag_pipeline/` 执行 `graphrag index --root .`。
5. 在 `graphrag_pipeline/` 启动 `python utils/main.py`。

## 重要注意事项

### 1. 多 PDF 课程必须注意文件定位

当前代码已支持一个课程对应多份 PDF，但这也意味着很多操作会出现“同课程下有多份文件”的歧义。遇到这种情况时：

- `pdf_ingest` 侧优先使用 `--file-id`
- `graphrag_pipeline` 侧优先使用 `--pdf-file-id`

否则命令可能报歧义错误，或者只操作课程下最新的一份 PDF。

### 2. `graphrag_pipeline/utils/main.py` 使用硬编码配置

这一点非常重要。当前 API 服务运行时不会自动读取 `settings.yaml` 中的全部配置，也不会自动和 `.env` 保持同步。你需要检查 [main.py](graphrag_pipeline/utils/main.py) 顶部的硬编码常量，例如：

- `GRAPHRAG_ROOT`
- `LANCEDB_URI`
- `LLM_API_BASE`
- `LLM_MODEL`
- `EMBEDDING_MODEL`

如果这些值不符合当前环境，API 即使索引成功也可能无法正常启动或查询。当前代码中的默认路径还可能直接指向仓库外目录，启动前务必先核对。

### 3. 当前代码与旧文档存在少量分叉

已确认的分叉主要有：

- GraphRAG 输入当前以 `json` 为主，不再是旧版 `input/*.txt` 主流程。
- `fetch_from_minio.py` 已优先按 `course_id/graphrag/pdf_<file_id>/...` 查找导出结果。
- API 模型名当前是 `graphrag-local-search:latest`、`graphrag-global-search:latest`、`full-model:latest`。

### 4. `output/` 目录不是只有 parquet

`graphrag_pipeline/output/` 中既有 parquet，也有 `lancedb/`。当前 API 服务依赖两者同时存在，不能只保留 parquet。

### 5. 配置和密钥不应提交到文档

仓库里包含 `.env` 文件，但入口文档不应复述任何真实密钥、口令或令牌。配置时请只参考变量名，不要扩散已有敏感值。

## 相关文档

- [pdf_ingest/CLAUDE.md](pdf_ingest/CLAUDE.md)
- [pdf_ingest/docs/MinerU PDF Parser.md](pdf_ingest/docs/MinerU%20PDF%20Parser.md)
- [graphrag_pipeline/CLAUDE.md](graphrag_pipeline/CLAUDE.md)
- [graphrag_pipeline/README.md](graphrag_pipeline/README.md)
- [AGENTS.md](AGENTS.md)
