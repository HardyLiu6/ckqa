# GraphRAG Pipeline

`graphrag_pipeline/` 是 CKQA 主链路中的建图、检索和问答服务模块。它消费 `pdf_ingest/` 导出的 JSON，构建 Microsoft GraphRAG 索引，并通过 FastAPI 提供 OpenAI 兼容接口。

## 模块定位

- 从 MinIO 拉取 `section_docs.json`、`page_docs.json` 或 `normalized_docs.json`
- 把输入写入 `input/`
- 运行 `graphrag index --root .`
- 读取 `output/*.parquet` 与 `output/lancedb/`
- 对外暴露 `/v1/chat/completions`、`/v1/models`、`/v1/query-tasks`、`/health`

Java 管理端的知识库构建会使用 `runtime/kb-build-runs/` 下的独立 workspace。共享 `input/` 和 `output/` 仍保留为手工 CLI 调试入口，不再是管理端并发构建的唯一运行态。

相关环境变量：

- `GRAPHRAG_BUILD_RUNS_ROOT`：Java 与 Python 共同识别的 build-run 根目录，未配置时按 `${GRAPHRAG_ROOT}/runtime/kb-build-runs` 理解。
- `GRAPHRAG_ALLOW_CONCURRENT_KB_BUILDS`：Java 后端是否允许同一知识库同时存在多个 `pending/running` build run。
- `GRAPHRAG_AUTO_ACTIVATION_POLICY`：成功索引的自动激活策略，默认 `latest-build-only`，避免较旧并发构建覆盖较新的激活索引。
- `GRAPHRAG_COURSE_ROUTER_LANCEDB_URI`：课程画像路由独立 LanceDB 目录，默认 `runtime/course-router/lancedb`，不混入正式 GraphRAG 索引。
- `GRAPHRAG_COURSE_ROUTER_TABLE`：课程画像路由表名，默认随模型生成，例如 `course_profiles_text_embedding_v4`；表名必须包含当前 embedding 模型 slug，避免 BGE-M3 与 v4 混表。
- `CKQA_COURSE_ROUTING_EXCLUDED_COURSE_IDS` / `CKQA_COURSE_ROUTING_EXCLUDED_COURSE_TAGS`：Java 侧学生课程路由排除规则；内部 smoke/demo 课程应走显式 ID 或 `course-routing-excluded` 标签，不按课程名模糊排除。

## 当前状态

- 属于主链路模块
- 真实依赖基线是 `graphrag==3.0.9`
- `utils/main.py` 已收口为纯 CLI 查询包装层
- API 运行时配置会优先读取仓库内 `.env` / 当前环境变量，并把查询统一委托给 GraphRAG CLI
- 当前本地旧索引、旧输入和旧调优产物已清空；重新抽取后需要先同步输入、重建索引，再启动正式问答验证
- Prompt 默认回到 `prompts/*.txt` 基础模板；`prompts/final/active_prompt.json` 只会在重新固化候选 Prompt 后生成

## 真实入口与关键文件

| 文件 | 作用 |
| --- | --- |
| `pyproject.toml` | 依赖与版本基线，当前以它为准 |
| `settings.yaml` | GraphRAG CLI 索引配置 |
| `.env` | GraphRAG CLI 读取的环境变量 |
| `utils/fetch_from_minio.py` | 从 MinIO 下载并展平 GraphRAG 输入 |
| `utils/main.py` | FastAPI 服务入口 |
| `scripts/README.md` | `scripts/` 分层导航；说明兼容入口与实现目录 |
| `scripts/build_prompt_tuning_samples.py` | 从输入文档构建 prompt tuning 样本 |
| `scripts/build_audit_extraction_set.py` | 从样本中构建小规模 audit 校准集 |
| `scripts/generate_candidate_prompts.py` | 统一生成候选 Prompt 与 manifest |
| `scripts/run_graphrag_prompt_tune.py` | 封装 GraphRAG 官方 prompt-tune 并整理 auto_tuned 候选 |
| `scripts/finalize_candidate_prompt.py` | 把候选 Prompt 固化到 `prompts/final/` 并激活为当前索引 Prompt |
| `scripts/run_candidate_extraction.py` | 批量执行候选 Prompt 抽取并输出统一结构化结果 |
| `scripts/extract_course_profile_hints.py` | 从 `section_docs.json` / `text_units.parquet` 抽取课程画像章节来源与关键词 hints |
| `scripts/cleanup_course_router_profiles.py` | 按显式 keep vector id 清理课程画像路由 LanceDB 历史向量，默认 dry-run |
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
python utils/fetch_from_minio.py <course_id> --clean
python utils/fetch_from_minio.py <course_id> --material-id <material_id> --clean
python utils/fetch_from_minio.py <course_id> --material-id <material_id> --json-file page_docs.json --clean
python utils/fetch_from_minio.py <course_id> --material-id <material_id> --json-file normalized_docs.json --input-dir ./tmp_validate/<course_id>/normalized --clean
python utils/fetch_from_minio.py <course_id> --material-id <material_id> --json-file section_docs.json --output-file material_<material_id>.section_docs.json
```

脚本当前行为：

- 默认拉取 `section_docs.json`
- 多资料课程下建议显式传 `--material-id`
- `--json-file` 指定 MinIO 源文件名，`--output-file` 只改变本地写入文件名，Java build-run 会用它把多个资料写入独立 `index/input/`
- 兼容历史 `*.jsonl` 文件并自动转为 JSON 数组
- 会尽量把 `course_id`、`source_file`、`document_type`、`heading_path_text`、`page_start`、`page_end`、`section_level` 等 metadata 提升到顶层

### 2. 选择当前索引使用的 Prompt

```bash
python scripts/finalize_candidate_prompt.py --candidate <candidate>
```

刚完成旧产物清理后，`.env` 默认指向 `prompts/` 下的基础模板，可以直接建索引。只有在重新生成并选定候选 Prompt 后，才需要执行上面的固化命令。

脚本当前行为：

- 把候选 Prompt 复制到 `prompts/final/<candidate>/`
- 更新 `.env` 中当前活动 Prompt 路径
- 写出 `prompts/final/active_prompt.json` 记录当前激活结果
- 候选目录缺失某些可选 Prompt 时，自动回退到 `prompts/` 下默认模板

### 3. 建索引

```bash
graphrag index --root .
```

当前 `settings.yaml` 已切换为：

- `input.type: json`
- `text_column: text`
- `title_column: title`
- 索引阶段 Prompt 路径由 `.env` 中的 `GRAPHRAG_*_PROMPT_FILE` 控制

### 4. 命令行查询

```bash
graphrag query --root . --method local "问题"
graphrag query --root . --method global "问题"
graphrag query --root . --method drift "问题"
graphrag query --root . --method basic "问题"
```

### 5. 启动 API

```bash
python utils/main.py
```

默认端口为 `8012`。

### 5.1 Hybrid v0 默认策略

`hybrid_v0` 现在默认采用 **BM25 证据注入 GraphRAG Basic**：

1. 使用 `v6` evidence selector 从 `text_units.parquet` 选取 BM25/text-unit 证据。
2. 把证据写入 `LOCAL_BM25_EVIDENCE` 段落并交给 GraphRAG `basic` 查询。
3. 解析 Basic 返回中的 `[Data:]` / `[Data: Hybrid(...)]` 来源。
4. 默认不再调用 DeepSeek/One API synthesis 后处理，也不启用 local fallback。

默认环境变量等价于：

```bash
CKQA_HYBRID_V0_EVIDENCE_STRATEGY=v6
CKQA_HYBRID_V0_ONE_SHOT_BASIC_INJECTION=true
CKQA_HYBRID_V0_DISABLE_SYNTHESIS=true
CKQA_HYBRID_V0_ENABLE_LOCAL_FALLBACK=false
```

如果需要回退旧的后处理 synthesis 路径，可以在 `.env` 或进程环境中显式设置：

```bash
CKQA_HYBRID_V0_DISABLE_SYNTHESIS=false
```

该设置只恢复 synthesis 后备能力，不会改变 `basic/local/global/drift` 的原有路径。

### 5.2 GraphRAG 原生流式生成

`/v1/query-tasks` 支持可选 `streamResponse=true` 与 `streamSource=native_graphrag`。该能力只供 Java 后端消费；浏览器仍只连接 Java `/api/v1/qa-sessions/{sessionId}/tasks/{taskId}/events`。

默认开启的原生流式模式为：

```bash
CKQA_GRAPHRAG_NATIVE_STREAMING_MODES=hybrid_v0,basic
```

`basic` 会调用 GraphRAG `basic_search_streaming`。`hybrid_v0` 仍保持当前质量路径：先用 `v6` BM25/text-unit evidence selector 构造 `LOCAL_BM25_EVIDENCE`，再把注入后的 Basic query 交给 GraphRAG `basic_search_streaming`；默认不调用 CKQA synthesis client。

Python 内部事件流：

```text
GET /v1/query-tasks/{taskId}/events
```

事件包括 `ack/status/delta/sources/done/error/heartbeat`。如果 GraphRAG 原生 streaming 不可用、artifact 缺失或连接中断，Java 会继续使用阶段 1 的状态流和最终答案分段，不影响 MySQL 中的最终 assistant message 与 sources。

### 5.3 课程画像 hints 抽取

课程画像路由会在 Java 生成基础画像时，额外从当前 active build-run 的 GraphRAG 输入/输出中抽取可复用的“章节来源 + 关键词 hints”。默认只读取两类稳定文本来源：

- `section_docs.json`：pdf_ingest 导出的章节级 GraphRAG 输入。
- `text_units.parquet`：GraphRAG 索引输出中的文本单元。

该流程不会读取 `entities.parquet`、`community_reports.parquet` 等派生产物，避免把抽取/聚类噪声写回课程画像。新增课程只要完成资料解析、GraphRAG 输入同步与索引构建，并把成功 build run 激活，Java 后端即可复用同一套 hints 流程。

手工抽取示例：

```bash
python scripts/extract_course_profile_hints.py \
  --course-id <course_id> \
  --section-docs runtime/kb-build-runs/user_0/kb_5/build_19/graph-input/section_docs.json \
  --data-dir runtime/kb-build-runs/user_0/kb_5/build_19/index/output \
  --keyword 操作系统 \
  --max-hints 24
```

Java 内部调用时使用相对 `GRAPHRAG_BUILD_RUNS_ROOT` 的 `dataDirUri`，Python 会拒绝绝对路径、`..` 逃逸和解析后位于根目录外的路径。

### 5.4 课程画像 LanceDB 清理

课程画像路由的 LanceDB 表与正式 GraphRAG 索引隔离。画像 hash 更新后，推荐链路会按 `courseId` 去重；如需清理历史画像向量，使用显式 keep 清单，默认先 dry-run：

```bash
python scripts/cleanup_course_router_profiles.py \
  --env-file .env \
  --course-id <course_id> \
  --keep-vector-id <current_vector_id>
```

确认 `staleVectorIds` 后再执行：

```bash
python scripts/cleanup_course_router_profiles.py \
  --env-file .env \
  --course-id <course_id> \
  --keep-vector-id <current_vector_id> \
  --execute
```

不要在没有 `--keep-vector-id` 或 `--keep-vector-ids-file` 的情况下清理；脚本会直接拒绝，避免误删当前画像。

### 6. Prompt 调优辅助脚本

```bash
python scripts/build_prompt_tuning_samples.py
python scripts/build_audit_extraction_set.py
python scripts/generate_candidate_prompts.py --overwrite
python scripts/run_graphrag_prompt_tune.py --dry_run
python scripts/finalize_candidate_prompt.py --candidate <candidate>
```

当前约定是：模块专属脚本放在 `graphrag_pipeline/scripts/`，仓库根目录 `scripts/` 只保留仓库级工具，例如漂移审计。

为方便审阅，`graphrag_pipeline/scripts/` 内部实现现在按工作流分到：

- `scripts/prompt_tuning/`
- `scripts/extraction_eval/`

根目录同名脚本仍保留为兼容入口，所以现有 `python scripts/*.py` 命令默认不需要改。

### 7. 候选 Prompt 抽取评测输入生成

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
- `data/prompt_tuning_samples/`、`data/eval/audit_extraction_set.json`、`prompts/candidates/`、`prompts/final/` 和 `results/` 下的评测/报告文件都是可再生调优产物；重新抽取一版课程前可以清空，避免旧课程材料污染新评测

### 8. QA baseline 算法增强评测

本流程默认复用已经通过 smoke query 的 `graphrag_pipeline/output/` 产物与 baseline run，不运行 `graphrag index`。日常路由评测主线使用规则指标、标准 IR 指标、BGE-M3 分块语义覆盖、latency/error 与 bootstrap；LLM judge、BERTScore、RAGAS 和 factuality extra 都是可选复核。

安装可复现算法评测依赖：

```bash
pip install -e ".[eval]"
```

生成候选题材：

```bash
python -m graphrag_pipeline.scripts.qa_eval.algorithmic_seed_builder \
  --output graphrag_pipeline/data/eval/qa_candidate_seeds.jsonl \
  --max-items 80
```

在 baseline run 完成后追加算法评分与显著性分析：

```bash
python -m graphrag_pipeline.scripts.qa_eval.algorithmic_scorer --run-dir <run_dir>
python -m graphrag_pipeline.scripts.qa_eval.semantic_threshold_calibrator --run-dir <run_dir>
python -m graphrag_pipeline.scripts.qa_eval.significance_reporter --run-dir <run_dir>
python -m graphrag_pipeline.scripts.qa_eval.ragas_exporter --run-dir <run_dir>
python -m graphrag_pipeline.scripts.qa_eval.factuality_extra_exporter --run-dir <run_dir>
```

如果当前环境没有 BGE-M3 或只想跑快速回归，可以临时使用低成本 baseline：

```bash
python -m graphrag_pipeline.scripts.qa_eval.algorithmic_scorer --run-dir <run_dir> --cheap-only
```

BGE-M3 在有 CUDA 的机器上建议显式使用 GPU、fp16 与更大的 batch：

```bash
python -m graphrag_pipeline.scripts.qa_eval.algorithmic_scorer \
  --run-dir <run_dir> \
  --bge-device cuda \
  --bge-fp16 \
  --bge-batch-size 32
```

BERTScore 兼容指标只在需要与旧报告对齐时安装：

```bash
pip install -e ".[semantic-compat]"
```

SummaC / AlignScore / SCALE 只作为专项 factuality extra；其中 SCALE 可通过 `factuality-extra` extra 安装，SummaC 与 AlignScore 通常按上游仓库说明安装后读取 `factuality_extra_dataset.jsonl` 离线运行。

## API 说明

### 端点

- `GET /health`
- `GET /v1/models`
- `POST /v1/query-tasks`
- `GET /v1/query-tasks/{taskId}`
- `POST /v1/chat/completions`
- `GET /v1/internal/course-routing/readiness`
- `POST /v1/internal/course-routing/profiles/upsert`
- `POST /v1/internal/course-routing/profile-hints`
- `POST /v1/internal/course-routing/recommend`

### 内部任务接口

提交异步查询任务：

```bash
curl -X POST http://127.0.0.1:8012/v1/query-tasks \
  -H "Content-Type: application/json" \
  -d '{
    "mode": "global",
    "prompt": "请概括这套图谱的主题",
    "indexRunId": 18,
    "dataDirUri": "user_2/kb_5/build_27/index/output"
  }'
```

`indexRunId` 和 `dataDirUri` 是 Java 后端传入的内部字段。`dataDirUri` 必须位于 `GRAPHRAG_BUILD_RUNS_ROOT` 下，Python API 会拒绝绝对路径和目录逃逸；未传时仍查询共享 `output/`，用于 CLI 调试和兼容旧调用。

查询异步任务状态：

```bash
curl http://127.0.0.1:8012/v1/query-tasks/qt_20260422_000001_001
```

时间字段约定：

- Python 内部仍使用 UTC aware 时间跟踪任务生命周期
- 对外 JSON 中的 `createdAt`、`startedAt`、`lastHeartbeatAt`、`finishedAt` 统一序列化为 `Asia/Shanghai` 的无偏移 `LocalDateTime` 字符串，例如 `2026-04-22T20:20:34`
- Python 侧只负责托管真实 `graphrag query` 子进程并刷新心跳；按 `mode` 区分的前端轮询建议、stale 阈值和超时文案由 Java 后端返回给业务前端
- 2026-04-23 使用 main 分支现有图谱单轮实测：`local` 约 110 秒、`global` 约 800 秒、`drift` 约 725 秒；这些数值用于 Java 后端默认轮询/stale 策略，不代表 GraphRAG CLI 的固定上限

### 当前模型名

- `graphrag-local-search:latest`
- `graphrag-global-search:latest`
- `graphrag-drift-search:latest`
- `graphrag-basic-search:latest`

`full-model:latest` 当前已归档为后续扩展模式，不再作为公开支持模型对外宣称。

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
- 当前活动 Prompt 由 `.env` 中的 `GRAPHRAG_ACTIVE_PROMPT_CANDIDATE` 与各个 `GRAPHRAG_*_PROMPT_FILE` 共同决定；清理后默认候选名为 `base`，并直接指向 `prompts/*.txt`
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
python utils/fetch_from_minio.py <course_id> --material-id <material_id> --json-file normalized_docs.json --input-dir ./tmp_validate/<course_id>/normalized --clean
```

## 辅助工具

### Neo4j 导入

```bash
docker compose --env-file ../infra/.env -f ../infra/docker-compose.yml up -d neo4j

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
- [../docs/student-backend-graphrag-api-contract.md](../docs/student-backend-graphrag-api-contract.md)
- [../docs/标准化导出验证说明.md](../docs/标准化导出验证说明.md)
- [../docs/archive/MIGRATION_GRAPHRAG_3_0_9.md](../docs/archive/MIGRATION_GRAPHRAG_3_0_9.md)（历史归档）
- [../README.md](../README.md)
