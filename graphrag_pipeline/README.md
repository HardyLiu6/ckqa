# GraphRAG Pipeline

`graphrag_pipeline/` 是 CKQA 主链路中的建图与问答模块，负责消费 `pdf_ingest` 导出的 JSON，执行 GraphRAG 索引，并通过 FastAPI 提供 OpenAI 兼容接口。

当前请以代码和依赖配置为准：`pyproject.toml` 里固定的是 `graphrag==3.0.9`。仓库中仍有少量旧注释写着 `2.7.0`，那部分属于历史残留。

## 当前模块在做什么

- 从 MinIO 拉取 `section_docs.json`、`page_docs.json` 或 `normalized_docs.json`
- 将输入写入 `input/`
- 运行 `graphrag index --root .` 生成 parquet 与 `output/lancedb/`
- 通过 `utils/main.py` 暴露 OpenAI 兼容接口
- 支持 Neo4j 导入、3D 可视化和网页抓取等辅助工具

## 目录概览

```text
graphrag_pipeline/
├── .env
├── CLAUDE.md
├── README.md
├── pyproject.toml
├── requirements.txt
├── settings.yaml
├── infra/neo4j/
├── input/
├── output/
├── prompts/
├── reports/
├── tests/
└── utils/
    ├── apiTest.py
    ├── fetch_from_minio.py
    ├── graphrag3dknowledge.py
    ├── main.py
    ├── neo4jTest.py
    └── spider.py
```

说明：

- 主入口是 `utils/main.py`，当前目录中并不存在旧文档提到的 `back/`。
- `pyproject.toml` 是依赖与版本的主要真相来源。
- `requirements.txt` 仍在仓库中，但其中的文字注释有历史残留，不应优先于 `pyproject.toml`。

## 快速开始

### 1. 环境准备

```bash
conda activate graphrag-oneapi
pip install -e ".[all]"
```

环境要求：

- Python `>=3.10, <3.13`
- OpenAI 兼容的聊天 / 向量接口
- 如需 Neo4j，可额外准备 Docker

### 2. 准备输入

如果输入来自 `pdf_ingest`，优先使用同步脚本：

```bash
python utils/fetch_from_minio.py os --clean
python utils/fetch_from_minio.py os --pdf-file-id 3 --clean
python utils/fetch_from_minio.py os --pdf-file-id 3 --json-file page_docs.json --clean
python utils/fetch_from_minio.py os --pdf-file-id 3 --json-file normalized_docs.json --input-dir ./tmp_validate/os/normalized --clean
```

当前脚本行为：

- 默认拉取 `section_docs.json`
- 多 PDF 课程下建议显式传 `--pdf-file-id`
- 支持历史 `*.jsonl` 文件自动转成 JSON 数组
- 会尽量把 `document_type`、`chapter`、`section`、`heading_path_text`、`page_start`、`page_end`、`course_id` 等字段提升到顶层

### 3. 构建索引

```bash
graphrag index --root .
```

当前 `settings.yaml` 已配置：

- `input.type: json`
- `text_column: text`
- `title_column: title`
- 一组结构化 metadata 字段收集与 chunk prepend 规则

### 4. 命令行查询

```bash
graphrag query --root . --method local --query "问题"
graphrag query --root . --method global --query "问题"
```

### 5. 启动 API

```bash
python utils/main.py
```

默认端口是 `8012`。

## API 说明

主要端点：

- `GET /health`
- `GET /v1/models`
- `POST /v1/chat/completions`

当前模型名：

- `graphrag-local-search:latest`
- `graphrag-global-search:latest`
- `full-model:latest`

请求示例：

```bash
curl -X POST http://localhost:8012/v1/chat/completions \
  -H "Content-Type: application/json" \
  -d '{
    "model": "graphrag-local-search:latest",
    "messages": [{"role": "user", "content": "你的问题"}],
    "stream": false
  }'
```

## 当前实现的几个关键点

### 1. `utils/main.py` 有两套运行路径

- 如果 GraphRAG 内部 Python API 导入成功，服务会直接使用内部对象搭建搜索引擎。
- 如果内部导入失败，会退回到 `graphrag query --root . --method ...` 的 CLI 兼容模式。

这也是为什么代码里还能看到一些 `2.x` 风格的导入与注释，但实际依赖已经是 `3.0.9`。

### 2. API 运行时配置不是从 `.env` 自动同步的

`utils/main.py` 顶部仍有一组硬编码常量，例如：

- `GRAPHRAG_ROOT`
- `LANCEDB_URI`
- `LLM_API_BASE`
- `LLM_MODEL`
- `EMBEDDING_MODEL`

CLI 用的是 `settings.yaml` + `.env`，API 用的是这组常量。两边如果漂移，索引和服务行为就可能不一致。

### 3. `output/` 不能只留 parquet

API 服务依赖：

- `output/*.parquet`
- `output/lancedb/`

两者缺一不可。

## 辅助工具

### Neo4j 导入

```bash
cd infra/neo4j
docker-compose up -d
cd ../..

python utils/neo4jTest.py --folder output
```

### 3D 可视化

```bash
python utils/graphrag3dknowledge.py --input output --port 8080
```

### 网页抓取

```bash
python utils/spider.py --url https://example.com --output ./crawled_data --max-pages 50
```

### API 测试

```bash
python utils/apiTest.py
```

## 已知分叉与注意事项

- 当前依赖版本是 `3.0.9`，不要再把旧文档里的 `2.7.0` 当成基线。
- `utils/main.py` 中包含硬编码凭据与仓库外路径的历史配置，不能直接当作可复用示例。
- 如果你是在做导出验收或字段保真检查，建议同步 `normalized_docs.json`，并同时参考 `../docs/标准化导出验证说明.md`。
