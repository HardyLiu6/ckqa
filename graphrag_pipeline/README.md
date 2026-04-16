# GraphRAG Pipeline

`graphrag_pipeline/` 是 CKQA 主链路中的建图、检索和问答服务模块。它消费 `pdf_ingest/` 导出的 JSON，构建 Microsoft GraphRAG 索引，并通过 FastAPI 提供 OpenAI 兼容接口。

## 模块定位

- 从 MinIO 拉取 `section_docs.json`、`page_docs.json` 或 `normalized_docs.json`
- 把输入写入 `input/`
- 运行 `graphrag index --root .`
- 读取 `output/*.parquet` 与 `output/lancedb/`
- 对外暴露 `/v1/chat/completions`、`/v1/models`、`/health`

## 当前状态

- 属于主链路模块
- 真实依赖基线是 `graphrag==3.0.9`
- `utils/main.py` 仍保留部分 2.x 时代的注释和内部导入兼容逻辑
- API 运行时配置和 GraphRAG CLI 配置目前分离，启动前需要人工核对

## 真实入口与关键文件

| 文件 | 作用 |
| --- | --- |
| `pyproject.toml` | 依赖与版本基线，当前以它为准 |
| `settings.yaml` | GraphRAG CLI 索引配置 |
| `.env` | GraphRAG CLI 读取的环境变量 |
| `utils/fetch_from_minio.py` | 从 MinIO 下载并展平 GraphRAG 输入 |
| `utils/main.py` | FastAPI 服务入口 |
| `tests/test_fetch_from_minio.py` | 输入同步脚本测试 |
| `tests/test_build_audit_extraction_set.py` | 抽样审计构建脚本测试 |
| `prompts/` | GraphRAG 提示词模板 |

## 环境准备

```bash
cd graphrag_pipeline
conda activate graphrag-oneapi
pip install -e ".[all]"
```

环境要求：

- Python `>=3.10, <3.13`
- OpenAI 兼容的聊天模型与向量接口
- 如需 Neo4j，可再准备 Docker

## 快速开始

### 1. 同步输入

```bash
python utils/fetch_from_minio.py os --clean
python utils/fetch_from_minio.py os --pdf-file-id 3 --clean
python utils/fetch_from_minio.py os --pdf-file-id 3 --json-file page_docs.json --clean
python utils/fetch_from_minio.py os --pdf-file-id 3 --json-file normalized_docs.json --input-dir ./tmp_validate/os/normalized --clean
```

脚本当前行为：

- 默认拉取 `section_docs.json`
- 多 PDF 课程下建议显式传 `--pdf-file-id`
- 兼容历史 `*.jsonl` 文件并自动转为 JSON 数组
- 会尽量把 `course_id`、`source_file`、`document_type`、`heading_path_text`、`page_start`、`page_end`、`section_level` 等 metadata 提升到顶层

### 2. 建索引

```bash
graphrag index --root .
```

当前 `settings.yaml` 已切换为：

- `input.type: json`
- `text_column: text`
- `title_column: title`

### 3. 命令行查询

```bash
graphrag query --root . --method local --query "问题"
graphrag query --root . --method global --query "问题"
```

### 4. 启动 API

```bash
python utils/main.py
```

默认端口为 `8012`。

## API 说明

### 端点

- `GET /health`
- `GET /v1/models`
- `POST /v1/chat/completions`

### 当前模型名

- `graphrag-local-search:latest`
- `graphrag-global-search:latest`
- `full-model:latest`

### 请求示例

```bash
curl -X POST http://localhost:8012/v1/chat/completions \
  -H "Content-Type: application/json" \
  -d '{
    "model": "graphrag-local-search:latest",
    "messages": [{"role": "user", "content": "你的问题"}],
    "stream": false
  }'
```

## 使用时要注意

### 1. 版本真相以 `pyproject.toml` 为准

当前固定的是 `graphrag==3.0.9`。`description` 字段、文件头注释以及部分旧说明里仍能看到 `2.7.0`，这些都属于历史残留。

### 2. API 服务和 CLI 不是一套配置来源

- `graphrag index` / `graphrag query` 主要走 `settings.yaml` + `.env`
- `python utils/main.py` 主要走文件顶部的硬编码常量

如果索引结果和 API 服务行为不一致，先检查这两套配置是否漂移。

### 3. `utils/main.py` 有两条运行路径

- GraphRAG 内部 Python API 导入成功时，优先走内部对象
- 导入失败时，回退到 `graphrag query --root . --method ...` 的 CLI 兼容模式

### 4. 服务依赖不只是 parquet

运行 API 时通常需要同时存在：

- `output/*.parquet`
- `output/lancedb/`

缺少其中一类产物，服务很可能无法正常工作。

## 验证方式

### 运行测试

```bash
python -m pytest tests/
```

### 调用 API 测试脚本

```bash
python utils/apiTest.py
```

### 验证输入同步

```bash
python utils/fetch_from_minio.py os --pdf-file-id 3 --json-file normalized_docs.json --input-dir ./tmp_validate/os/normalized --clean
```

## 辅助工具

### Neo4j 导入

```bash
cd infra/neo4j
docker-compose up -d
cd ../..

python utils/neo4jTest.py --folder output
```

### 3D 知识图谱可视化

```bash
python utils/graphrag3dknowledge.py --input output --port 8080
```

### 网页抓取

```bash
python utils/spider.py --url https://example.com --output ./crawled_data --max-pages 50
```

## 相关文档

- [CLAUDE.md](CLAUDE.md)
- [PROMPT_TUNING_PIPELINE.md](PROMPT_TUNING_PIPELINE.md)
- [../docs/标准化导出验证说明.md](../docs/标准化导出验证说明.md)
- [../docs/MIGRATION_GRAPHRAG_3_0_9.md](../docs/MIGRATION_GRAPHRAG_3_0_9.md)
- [../README.md](../README.md)
