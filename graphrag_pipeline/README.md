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
- `utils/main.py` 已收口为纯 CLI 查询包装层
- API 运行时配置会优先读取仓库内 `.env` / 当前环境变量，并把查询统一委托给 GraphRAG CLI

## 真实入口与关键文件

| 文件 | 作用 |
| --- | --- |
| `pyproject.toml` | 依赖与版本基线，当前以它为准 |
| `settings.yaml` | GraphRAG CLI 索引配置 |
| `.env` | GraphRAG CLI 读取的环境变量 |
| `utils/fetch_from_minio.py` | 从 MinIO 下载并展平 GraphRAG 输入 |
| `utils/main.py` | FastAPI 服务入口 |
| `scripts/build_prompt_tuning_samples.py` | 从输入文档构建 prompt tuning 样本 |
| `scripts/build_audit_extraction_set.py` | 从样本中构建小规模 audit 校准集 |
| `scripts/generate_candidate_prompts.py` | 统一生成候选 Prompt 与 manifest |
| `scripts/run_graphrag_prompt_tune.py` | 封装 GraphRAG 官方 prompt-tune 并整理 auto_tuned 候选 |
| `scripts/run_candidate_extraction.py` | 批量执行候选 Prompt 抽取并输出统一结构化结果 |
| `tests/test_fetch_from_minio.py` | 输入同步脚本测试 |
| `tests/test_build_audit_extraction_set.py` | 抽样审计构建脚本测试 |
| `prompts/` | GraphRAG 提示词模板 |

## 环境准备

```bash
cd graphrag_pipeline
conda activate graphrag-oneapi
pip install -e ".[all]"
```

当前共享开发环境里的 `graphrag-oneapi` 已额外安装 `pytest`，因此仓库内默认可直接运行 `python -m pytest tests/`。如果是新环境，请在安装项目依赖后再补装 `pytest`。

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

### 5. Prompt 调优辅助脚本

```bash
python scripts/build_prompt_tuning_samples.py
python scripts/build_audit_extraction_set.py
python scripts/generate_candidate_prompts.py --overwrite
python scripts/run_graphrag_prompt_tune.py --dry_run
```

当前约定是：模块专属脚本放在 `graphrag_pipeline/scripts/`，仓库根目录 `scripts/` 只保留仓库级工具，例如漂移审计。

### 6. 候选 Prompt 抽取评测输入生成

```bash
python scripts/run_candidate_extraction.py --limit 5 --overwrite
```

当前默认已启用推荐配置：

- `--stream-mode on`
- `--retry-on-truncation`

如果你想显式展开完整命令，可以写成：

```bash
python scripts/run_candidate_extraction.py \
  --limit 5 \
  --overwrite \
  --stream-mode on \
  --retry-on-truncation \
  --retry-max-tokens 4000 \
  --high-risk-timeout 240 \
  --max-entities 12 \
  --max-relationships 12
```

运行建议：

- 使用 `one-api + 阿里百炼` 时，默认会启用流式与截断自动重试
- 如需回退旧行为，可显式传 `--stream-mode off --no-retry-on-truncation`
- `--max-entities` / `--max-relationships` 可限制高扇出样本的输出规模
- 输出文件默认写入 `results/extraction_eval/`、`results/extraction_raw/`、`results/errors/`

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

当前固定的是 `graphrag==3.0.9`。仓库内涉及 GraphRAG 版本判断时，统一以 `pyproject.toml` 为准。

### 2. API 服务通过 CLI 执行查询

- `graphrag index` / `graphrag query` 主要走 `settings.yaml` + `.env`
- `python utils/main.py` 会优先读取仓库内 `.env` / 当前环境变量，默认指向仓库内 `output/`
- API 侧常用覆盖项包括 `GRAPHRAG_OUTPUT_DIR`、`GRAPHRAG_LANCEDB_URI`、`GRAPHRAG_API_HOST`、`GRAPHRAG_API_PORT`

如果索引结果和 API 服务行为不一致，先检查 `.env` / 环境变量里的输出目录是否和当前索引产物一致。

### 3. `utils/main.py` 当前只有一条运行路径

- FastAPI 收到请求后，统一调用 `graphrag query --root . --method ...`
- `/health` 中的 `compat_mode` 固定返回 `cli_query`

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

如果只想快速验证单个脚本，可执行 `python -m pytest tests/test_fetch_from_minio.py`。

### 调用 API 测试脚本

```bash
python utils/apiTest.py
```

### 运行仓库级漂移审计

```bash
python ../scripts/audit_repo_drift.py --strict
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
python utils/graphrag3dknowledge.py --directory output --port 8080
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
