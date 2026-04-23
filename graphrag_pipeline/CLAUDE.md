# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

Knowledge graph Q&A system built around Microsoft GraphRAG, currently pinned to `graphrag==3.0.9` in `pyproject.toml`. The project uses an OpenAI-compatible LLM / embedding endpoint (often routed through OneAPI) and exposes an OpenAI-compatible FastAPI API with local, global, and mixed search modes.

**Language:** Chinese (comments, prompts, docs, and commit messages are in Chinese).
**Build system:** `pyproject.toml` (setuptools) with `.env` / `settings.yaml` for GraphRAG CLI, plus a small runtime config layer in `utils/main.py`.

标准化导出验证流程见 `../docs/标准化导出验证说明.md`。

## Common Commands

```bash
# Environment setup
conda activate graphrag-oneapi
pip install -e ".[all]"

# Shared CKQA development environment already has pytest installed.
# For a fresh environment, install pytest separately after project deps.
pip install pytest

# Pull GraphRAG input exported by pdf_ingest
python utils/fetch_from_minio.py os --clean
python utils/fetch_from_minio.py os --pdf-file-id 3 --clean
python utils/fetch_from_minio.py os --pdf-file-id 3 --json-file page_docs.json --clean
python utils/fetch_from_minio.py os --pdf-file-id 3 --json-file normalized_docs.json --input-dir ./tmp_validate/os/normalized --clean

# Build/rebuild the knowledge graph index
graphrag index --root .

# Query via CLI
graphrag query --root . --method local --query "问题"
graphrag query --root . --method global --query "问题"

# Start the API server (port 8012)
python utils/main.py

# Prompt tuning helpers
python scripts/build_prompt_tuning_samples.py
python scripts/build_audit_extraction_set.py
python scripts/generate_candidate_prompts.py --overwrite
python scripts/run_graphrag_prompt_tune.py --dry_run
python scripts/finalize_candidate_prompt.py --candidate auto_tuned
python scripts/score_extraction_results.py --overwrite

# Run tests
python -m pytest tests/

# Run API tests
python utils/apiTest.py

# Audit active docs/config drift from repo root
python ../scripts/audit_repo_drift.py --strict

# Neo4j: start database, then import graph data
cd infra/neo4j && docker-compose up -d && cd ../..
python utils/neo4jTest.py --folder output

# 3D knowledge graph visualization
python utils/graphrag3dknowledge.py --directory output --port 8080

# Web crawler
python utils/spider.py --url https://example.com --output ./crawled_data --max-pages 50
```

## Architecture

### Data Pipeline

```text
pdf_ingest 导出的 json
  -> utils/fetch_from_minio.py 拉取并展平 metadata
  -> input/*.json
  -> graphrag index --root .
  -> output/ (parquet + lancedb)
  -> utils/main.py
  -> OpenAI-compatible API
```

1. **Input sync**: `utils/fetch_from_minio.py` 从 MinIO 下载 `section_docs.json` / `page_docs.json` / `normalized_docs.json`，并把 GraphRAG 常用 metadata 提升到顶层。
2. **Indexing**: `graphrag index` 读取 `input/` 里的 JSON 数组，生成 parquet 文件和 `output/lancedb/`。
3. **Serving**: `utils/main.py` 启动 FastAPI 服务，对外暴露 `/v1/chat/completions`、`/v1/models`、`/health`。
4. **Search routing**: API 请求中的 `model` 用来选择搜索模式：
   - `graphrag-local-search:latest`
   - `graphrag-global-search:latest`
   - `graphrag-drift-search:latest`
   - `graphrag-basic-search:latest`

### Compatibility Reality

- `pyproject.toml` 中的 GraphRAG 版本是真相，当前是 `3.0.9`。
- `utils/main.py` 已经收口为纯 `graphrag query --root . --method ...` 包装层。
- `/health` 中的 `compat_mode` 固定为 `cli_query`。
- 涉及版本判断时，统一以 `pyproject.toml` 和 `/health` 返回值为准。

### Key Files

- **`pyproject.toml`**：依赖与版本基线，优先信它。
- **`scripts/README.md`**：`scripts/` 分层导航；说明兼容入口与实现目录。
- **`scripts/build_prompt_tuning_samples.py`**：从输入构建 prompt tuning 样本。
- **`scripts/build_audit_extraction_set.py`**：从样本构建 audit 校准集。
- **`scripts/generate_candidate_prompts.py`**：生成候选 Prompt 与 manifest。
- **`scripts/run_graphrag_prompt_tune.py`**：统一封装 GraphRAG 官方 prompt-tune。
- **`scripts/finalize_candidate_prompt.py`**：把候选 Prompt 固化到 `prompts/final/`，并更新 `.env` 中当前活动 Prompt 路径。
- **`utils/main.py`**：主 FastAPI 服务。默认读取仓库内 `output/`，统一通过 GraphRAG CLI 执行查询。
- **`settings.yaml`**：GraphRAG CLI 索引配置，使用 `.env` 变量。
- **`.env`**：GraphRAG CLI 所需的模型、目录和凭据变量。
- **`utils/fetch_from_minio.py`**：从 MinIO 下载 JSON 输入，兼容历史 `*.jsonl`。
- **`prompts/`**：GraphRAG 提示词模板。
- **`utils/neo4jTest.py`**：把 parquet 输出导入 Neo4j。
- **`utils/graphrag3dknowledge.py`**：从 parquet 构建 3D 可视化。
- **`utils/spider.py`**：网页抓取与 Markdown 转换工具。

### Configuration

当前主要有两层配置：

1. **`settings.yaml` + `.env`**
   - 供 `graphrag index` / `graphrag query` 等 CLI 使用。
   - `settings.yaml` 通过 `${GRAPHRAG_*}` 读取环境变量。
   - 索引阶段的抽取 / 摘要 / community report Prompt 也通过 `.env` 驱动，可先运行 `python scripts/finalize_candidate_prompt.py --candidate <name>` 切换当前活动 Prompt。
2. **`utils/main.py` 运行时配置**
   - 供 API 服务设置输出目录与监听地址。
   - 会优先读取仓库内 `.env` / 当前环境变量。
   - 默认输出目录是仓库内 `output/`，可通过环境变量覆盖。
   - 查询最终仍统一委托给 GraphRAG CLI。

如果 API 服务行为和 CLI 不一致，先检查 `.env` / 环境变量中的输出目录、LanceDB 路径和索引产物是否一致。

### Input Expectations

- GraphRAG 输入主流程是 `json`，不是旧版 `txt`。
- `pdf_ingest` 当前会输出：
  - `section_docs.json`
  - `page_docs.json`
  - `normalized_docs.json`
- `section_docs.json` / `page_docs.json` 是 GraphRAG 投影结果。
- `normalized_docs.json` 更适合验收、抽样检查和字段保真分析。
- 多 PDF 课程下，优先传 `--pdf-file-id` 避免歧义。

### Infrastructure

- **Neo4j**：Docker Compose 位于 `infra/neo4j/`。
- **LanceDB**：文件型向量库，路径通常在 `output/lancedb/`。
- **OpenAI-compatible endpoint / OneAPI**：通常由 GraphRAG CLI 读取 `.env` 中的模型配置。

## Dependencies

依赖定义在 `pyproject.toml`，按 optional-dependencies 分组：

- **核心**：fastapi, uvicorn, graphrag==3.0.9, pandas, numpy, tiktoken, pydantic, python-dotenv, aiohttp, requests
- **ml**：torch, transformers, scikit-learn, matplotlib, seaborn, nltk, spacy
- **graph**：networkx, plotly, lancedb, pyarrow, neo4j
- **scraper**：scrapy, beautifulsoup4, html2text
- **all**：安装以上全部

```bash
pip install -e .           # 仅核心依赖
pip install -e ".[graph]"  # 核心 + graph
pip install -e ".[all]"    # 全部依赖
```

- Python >=3.10, <3.13
- 建议优先参考 `pyproject.toml`，不要把旧版 `requirements.txt` 注释当成版本真相
- 当前共享开发环境里的 `graphrag-oneapi` 已额外安装 `pytest`，因此仓库内可以直接运行 `python -m pytest tests/`；如果是重建环境，请在安装项目依赖后再单独安装 `pytest`

## Important Notes

- `utils/main.py` 会优先读取 `.env` / 当前环境变量，并通过 CLI 查询复用同一套 GraphRAG 执行路径。
- 仓库根目录 `scripts/` 只保留仓库级工具；GraphRAG 调优与候选 Prompt 相关脚本统一维护在 `graphrag_pipeline/scripts/`。
- `graphrag_pipeline/scripts/` 内部实现按 `prompt_tuning/` 与 `extraction_eval/` 分组；根目录同名脚本保留为兼容入口。
- 仓库活跃入口文件与关键脚本可以用 `python ../scripts/audit_repo_drift.py --strict` 做快速审计。
- `settings.yaml` 已经配置 `input.type: json` 与一组 metadata 字段。
- 若已经完成提示词调优并选定候选 Prompt，先执行 `python scripts/finalize_candidate_prompt.py --candidate <name>`，再执行 `graphrag index --root .`。
- `output/` 中的 parquet 与 `lancedb/` 需要同时保留，API 服务依赖二者并存。
