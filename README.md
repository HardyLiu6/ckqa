# CKQA · 课程知识图谱问答平台

[English](README.en.md) | [开发文档](#开发文档) | [快速开始](#快速开始)

CKQA（Course Knowledge Question Answering）是一套面向课程资料的知识生产与问答平台。它将课程 PDF 解析为可追溯的标准化文本，构建 Microsoft GraphRAG 知识图谱，并通过学生端、管理端和 Java API 提供课程问答、知识库构建与运维能力。

> 一期基线已经完成：PDF 解析、课程资料管理、知识库构建、GraphRAG 问答、异步流式任务、学生端与管理端的核心业务闭环均可在本地联调。项目仍持续演进；未开放的页面和能力会在界面与模块文档中明确标注。

## 核心能力

| 能力 | 说明 |
| --- | --- |
| 课程资料处理 | 上传 PDF、调用 MinerU 解析、记录页级进度，并将原文件和元数据分别保存到 MinIO 与 MySQL。 |
| 标准化导出 | 生成 `normalized_docs.json` 供人工验收，并生成 `section_docs.json` / `page_docs.json` 供下游建图。 |
| 知识库构建 | 按课程和构建批次隔离 GraphRAG 输入、输出、日志与 QA smoke 快照，避免不同索引相互污染。 |
| 课程问答 | 支持 `basic`、`local`、`global`、`drift` 与 `hybrid_v0`；学生端可查看检索进度、流式回答和来源。 |
| 智能编排 | Java `/api/v1` 统一承接认证、课程路由、模式推荐、异步 QA、SSE 断线恢复及管理端运维接口。 |
| 可观测运维 | 管理端提供课程、资料、解析进度、构建向导、QA smoke、问答检索日志、来源人工复核和系统健康检查。 |

## 架构概览

```text
课程 PDF
  │
  ▼
pdf_ingest ── MinerU / MinIO / MySQL
  │  normalized_docs.json / section_docs.json / page_docs.json
  ▼
graphrag_pipeline ── Microsoft GraphRAG 3.0.9 / LanceDB / Neo4j（可选）
  │  内部 query-task、streaming、课程画像接口
  ▼
backend/ckqa-back ── Spring Boot 4 / Java 21 / /api/v1
  ├── frontend/apps/student-app   学员端
  └── frontend/apps/admin-app     管理员与教师端
```

浏览器正式边界始终是 Java `/api/v1`。Python GraphRAG 服务提供内部查询与构建能力，由 Java 编排；前端不直接调用 Python `/v1` 接口。

## 技术栈

- Python 3.10+：PDF 处理、FastAPI、Microsoft GraphRAG `3.0.9`
- Java 21：Spring Boot `4.0.5`、MyBatis-Plus
- Vue 3 + Vite：Element Plus、Pinia、Vue Router、Sass
- MySQL、MinIO、Redis、Neo4j（可选图谱浏览）、One API/OpenAI 兼容模型服务
- Docker Compose：本地基础设施统一入口

## 快速开始

完整运行需要可用的 MinerU 与 OpenAI 兼容模型服务凭据。所有本地密钥只放在各模块 `.env` 中，切勿提交到仓库。

### 前置条件

- Docker 与 Docker Compose
- Python/Conda：建议分别准备 `courseKg` 和 `graphrag-oneapi` 环境
- JDK 21
- Node.js `^20.19.0 || >=22.12.0` 与 pnpm

### 1. 克隆并启动基础设施

```bash
git clone <your-fork-or-repository-url> ckqa
cd ckqa

cp infra/.env.example infra/.env
# 编辑 infra/.env，设置本机 MySQL、MinIO 与模型代理相关配置
docker compose --env-file infra/.env -f infra/docker-compose.yml up -d
docker compose --env-file infra/.env -f infra/docker-compose.yml ps
```

基础设施包括 MySQL、MinIO、One API、Neo4j 和 Redis。数据卷策略、端口及安全注意事项见 [infra/README.md](infra/README.md)。

### 2. 准备 Python 主链路

```bash
cd pdf_ingest
conda activate courseKg
pip install -e ".[dev]"

cd ../graphrag_pipeline
conda activate graphrag-oneapi
pip install -e ".[all]"
pip install pytest
```

分别配置 `pdf_ingest/.env` 与 `graphrag_pipeline/.env` 后，可按下列顺序验证知识生产链路：

```bash
# 在 pdf_ingest/
python scripts/pdf_processor/mineru_parser.py upload <course_id> -f <course.pdf> --parse
python scripts/pdf_processor/mineru_parser.py export-graphrag <course_id> --material-id <material_id> --mode section --with-page-docs

# 在 graphrag_pipeline/
python utils/fetch_from_minio.py <course_id> --material-id <material_id> --clean
graphrag index --root .
python utils/main.py
```

更多参数、输入输出约定和验收方式请看 [PDF Ingest 文档](pdf_ingest/README.md) 与 [GraphRAG Pipeline 文档](graphrag_pipeline/README.md)。

### 3. 启动后端与前端

```bash
# 终端 1：在 backend/ckqa-back/ 配置 .env 后启动 Java 编排层
cd backend/ckqa-back
scripts/run_local_backend.sh --mailer-type log

# 终端 2：启动管理端
pnpm --dir ../../frontend/apps/admin-app install
pnpm --dir ../../frontend/apps/admin-app dev:local

# 终端 3：启动学生端
pnpm --dir ../../frontend/apps/student-app install
pnpm --dir ../../frontend/apps/student-app dev:local
```

后端可以通过 `GRAPHRAG_API_MANAGED_ENABLED=true` 管理 GraphRAG API；也可先独立启动 `graphrag_pipeline/utils/main.py`。后端配置项、健康检查和完整联调顺序见 [后端 README](backend/ckqa-back/README.md)。

默认开发地址：管理端 `http://127.0.0.1:5173`、学生端 `http://127.0.0.1:5174`、后端 `http://127.0.0.1:8080`、GraphRAG API `http://127.0.0.1:8012`。

### 4. 验证

```bash
# 基础设施与 Java 服务
curl http://127.0.0.1:8080/api/v1/system/health

# 各模块回归
cd pdf_ingest && python -m pytest tests/
cd ../graphrag_pipeline && python -m pytest tests/
cd ../frontend/apps/admin-app && pnpm test && pnpm build
cd ../../../backend/ckqa-back && ./mvnw test

# 活跃文档与关键入口漂移审计（仓库根目录）
cd ../..
python scripts/audit_repo_drift.py --strict
```

测试受本机外部服务、模型凭据和已有索引数据影响；遇到失败时，请先根据模块 README 核对环境而不是直接修改运行态产物。

## 仓库结构

| 路径 | 职责 | 入口文档 |
| --- | --- | --- |
| `pdf_ingest/` | PDF 解析、文本清洗、标准化和 GraphRAG 导出 | [README](pdf_ingest/README.md) |
| `graphrag_pipeline/` | 输入同步、索引、GraphRAG 查询和内部任务服务 | [README](graphrag_pipeline/README.md) |
| `backend/ckqa-back/` | Java 业务编排与浏览器 API 边界 | [README](backend/ckqa-back/README.md) |
| `frontend/apps/student-app/` | 学员端课程与问答体验 | [README](frontend/apps/student-app/README.md) |
| `frontend/apps/admin-app/` | 管理员/教师的课程、构建和 QA 运维控制台 | [README](frontend/apps/admin-app/README.md) |
| `infra/` | 本地 MySQL、MinIO、One API、Neo4j、Redis Compose | [README](infra/README.md) |
| `sql/` | MySQL 初始化基线和增量迁移 | [ocqa.sql](sql/ocqa.sql) |

## 开发文档

| 读者与场景 | 建议入口 |
| --- | --- |
| 想了解产品和快速运行项目的开发者 | 本文档与 [English README](README.en.md) |
| 修改 PDF 解析、导出或课程资料模型 | [pdf_ingest/README.md](pdf_ingest/README.md)、[pdf_ingest/CLAUDE.md](pdf_ingest/CLAUDE.md) |
| 修改索引、检索、Prompt 或 GraphRAG API | [graphrag_pipeline/README.md](graphrag_pipeline/README.md)、[graphrag_pipeline/CLAUDE.md](graphrag_pipeline/CLAUDE.md) |
| 修改学生端、Java 后端与 GraphRAG 的联调边界 | [学生端 API 契约](docs/student-backend-graphrag-api-contract.md) |
| 修改管理端业务页面或 QA 运维 | [admin-app README](frontend/apps/admin-app/README.md) |
| 供贡献者和编码代理理解仓库约束 | [AGENTS.md](AGENTS.md)、[.codex](.codex) |
| 审计标准化导出质量 | [标准化导出验证说明](docs/标准化导出验证说明.md) |

## 贡献约定

- 新的浏览器业务接口必须经过 Java `/api/v1`；不要将前端直连到 Python GraphRAG 内部接口。
- 涉及 `normalized_docs.json`、GraphRAG metadata、MinIO 路径或资料命名的修改，必须检查 `pdf_ingest` 与 `graphrag_pipeline` 的契约兼容性。
- 不要提交 `.env`、索引输出、缓存、运行时目录、`node_modules` 或包含真实凭据的数据。
- 修改活跃入口文档、运行配置或 GraphRAG 版本后，运行 `python scripts/audit_repo_drift.py --strict`。

## 当前边界

- `hybrid_v0` 是 Java 内部业务模式：将 BM25 证据注入 GraphRAG Basic；它不是 OpenAI 兼容模型名。
- `smart` 仅是学生端的推荐入口，最终会落到 `basic`、`local`、`global`、`drift` 或 `hybrid_v0` 之一。
- 课程学习内容、社区、全局搜索、细粒度 RBAC 编辑和完整审计分析等非一期范围功能会显式显示为未开放，而不是伪造为可用能力。
- Redis 与图谱构建产物均不是课程与问答业务事实的唯一来源；MySQL、MinIO 及 GraphRAG 索引职责应保持清晰。

欢迎通过 Issue 或 Pull Request 讨论课程资料处理、GraphRAG 检索质量与产品体验改进。
