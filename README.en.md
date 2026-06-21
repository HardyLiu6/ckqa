# CKQA · Course Knowledge Graph Q&A Platform

[简体中文](README.md) | [Documentation](#documentation) | [Quick start](#quick-start)

CKQA (Course Knowledge Question Answering) is a course-material knowledge production and question-answering platform. It turns course PDFs into traceable normalized text, builds Microsoft GraphRAG knowledge graphs, and exposes course Q&A, knowledge-base builds, and operations through student, admin, and Java API applications.

> The v1 baseline is complete: PDF processing, course-material management, knowledge-base builds, GraphRAG Q&A, asynchronous streaming tasks, and the core student/admin workflows can be integrated locally. The project is still evolving; out-of-scope pages and capabilities are explicitly labelled in the UI and module documentation.

## Highlights

| Capability | What it does |
| --- | --- |
| Course material processing | Uploads PDFs, invokes MinerU, records page-level progress, and stores objects and metadata in MinIO and MySQL. |
| Normalized exports | Produces `normalized_docs.json` for review and `section_docs.json` / `page_docs.json` for graph construction. |
| Knowledge-base builds | Isolates GraphRAG inputs, outputs, logs, and QA smoke snapshots by course and build run. |
| Course Q&A | Supports `basic`, `local`, `global`, `drift`, and `hybrid_v0`, with retrieval progress, streamed answers, and sources in the student app. |
| Service orchestration | Java `/api/v1` handles auth, course routing, mode recommendation, asynchronous QA, SSE resume, and admin operations. |
| Operational visibility | The admin app covers courses, materials, parsing progress, build wizards, QA smoke tests, retrieval logs, source reviews, and health checks. |

## Architecture

```text
Course PDF
  │
  ▼
pdf_ingest ── MinerU / MinIO / MySQL
  │  normalized_docs.json / section_docs.json / page_docs.json
  ▼
graphrag_pipeline ── Microsoft GraphRAG 3.0.9 / LanceDB / Neo4j (optional)
  │  internal query-task, streaming, and course-profile APIs
  ▼
backend/ckqa-back ── Spring Boot 4 / Java 21 / /api/v1
  ├── frontend/apps/student-app   Student experience
  └── frontend/apps/admin-app     Administrator and teacher console
```

Java `/api/v1` is the formal browser boundary. The Python GraphRAG service is orchestrated internally by Java; browser clients do not call Python `/v1` endpoints directly.

## Tech stack

- Python 3.10+: PDF processing, FastAPI, Microsoft GraphRAG `3.0.9`
- Java 21: Spring Boot `4.0.5`, MyBatis-Plus
- Vue 3 + Vite: Element Plus, Pinia, Vue Router, Sass
- MySQL, MinIO, Redis, Neo4j (optional graph browsing), and a One API/OpenAI-compatible model provider
- Docker Compose for local infrastructure

## Quick start

A complete installation needs valid MinerU and OpenAI-compatible model-provider credentials. Keep local secrets only in module `.env` files and never commit them.

### Prerequisites

- Docker and Docker Compose
- Python/Conda; separate `courseKg` and `graphrag-oneapi` environments are recommended
- JDK 21
- Node.js `^20.19.0 || >=22.12.0` and pnpm

### 1. Clone and start infrastructure

```bash
git clone <your-fork-or-repository-url> ckqa
cd ckqa

cp infra/.env.example infra/.env
# Edit infra/.env for your local MySQL, MinIO, and model-proxy settings.
docker compose --env-file infra/.env -f infra/docker-compose.yml up -d
docker compose --env-file infra/.env -f infra/docker-compose.yml ps
```

The stack includes MySQL, MinIO, One API, Neo4j, and Redis. Read [infra/README.md](infra/README.md) for ports, data-retention details, and safety notes.

### 2. Prepare the Python pipeline

```bash
cd pdf_ingest
conda activate courseKg
pip install -e ".[dev]"

cd ../graphrag_pipeline
conda activate graphrag-oneapi
pip install -e ".[all]"
pip install pytest
```

After configuring `pdf_ingest/.env` and `graphrag_pipeline/.env`, validate the production path in this order:

```bash
# From pdf_ingest/
python scripts/pdf_processor/mineru_parser.py upload <course_id> -f <course.pdf> --parse
python scripts/pdf_processor/mineru_parser.py export-graphrag <course_id> --material-id <material_id> --mode section --with-page-docs

# From graphrag_pipeline/
python utils/fetch_from_minio.py <course_id> --material-id <material_id> --clean
graphrag index --root .
python utils/main.py
```

See the [PDF Ingest guide](pdf_ingest/README.md) and [GraphRAG Pipeline guide](graphrag_pipeline/README.md) for parameters, data contracts, and validation procedures.

### 3. Start backend and web applications

```bash
# Terminal 1: configure backend/ckqa-back/.env, then start Java orchestration
cd backend/ckqa-back
scripts/run_local_backend.sh --mailer-type log

# Terminal 2: admin application
pnpm --dir ../../frontend/apps/admin-app install
pnpm --dir ../../frontend/apps/admin-app dev:local

# Terminal 3: student application
pnpm --dir ../../frontend/apps/student-app install
pnpm --dir ../../frontend/apps/student-app dev:local
```

The backend can manage the GraphRAG API with `GRAPHRAG_API_MANAGED_ENABLED=true`, or you can run `graphrag_pipeline/utils/main.py` separately. See the [backend README](backend/ckqa-back/README.md) for configuration, health checks, and complete integration steps.

Default local URLs: admin `http://127.0.0.1:5173`, student `http://127.0.0.1:5174`, backend `http://127.0.0.1:8080`, GraphRAG API `http://127.0.0.1:8012`.

### 4. Verify

```bash
# Infrastructure and Java service
curl http://127.0.0.1:8080/api/v1/system/health

# Module regression suites
cd pdf_ingest && python -m pytest tests/
cd ../graphrag_pipeline && python -m pytest tests/
cd ../frontend/apps/admin-app && pnpm test && pnpm build
cd ../../../backend/ckqa-back && ./mvnw test

# Active-document and entrypoint drift audit, from repository root
cd ../..
python scripts/audit_repo_drift.py --strict
```

Tests depend on local external services, model credentials, and existing indexes. When a check fails, confirm the module environment first instead of modifying generated runtime artifacts.

## Repository map

| Path | Responsibility | Entry documentation |
| --- | --- | --- |
| `pdf_ingest/` | PDF parsing, cleaning, normalization, and GraphRAG exports | [README](pdf_ingest/README.md) |
| `graphrag_pipeline/` | Input synchronization, indexing, GraphRAG queries, and internal task service | [README](graphrag_pipeline/README.md) |
| `backend/ckqa-back/` | Java orchestration and the browser API boundary | [README](backend/ckqa-back/README.md) |
| `frontend/apps/student-app/` | Student course and Q&A experience | [README](frontend/apps/student-app/README.md) |
| `frontend/apps/admin-app/` | Course, build, and QA operations console for administrators and teachers | [README](frontend/apps/admin-app/README.md) |
| `infra/` | Compose stack for MySQL, MinIO, One API, Neo4j, and Redis | [README](infra/README.md) |
| `sql/` | MySQL initialization baseline and incremental migrations | [ocqa.sql](sql/ocqa.sql) |

## Documentation

| Audience or task | Recommended entrypoint |
| --- | --- |
| Learn the product and run the project | This page or the [Chinese README](README.md) |
| Change PDF parsing, exports, or the course-material model | [pdf_ingest/README.md](pdf_ingest/README.md), [pdf_ingest/CLAUDE.md](pdf_ingest/CLAUDE.md) |
| Change indexing, retrieval, prompts, or the GraphRAG API | [graphrag_pipeline/README.md](graphrag_pipeline/README.md), [graphrag_pipeline/CLAUDE.md](graphrag_pipeline/CLAUDE.md) |
| Work across student app, Java backend, and GraphRAG | [Student API contract](docs/student-backend-graphrag-api-contract.md) |
| Change admin workflows or QA operations | [admin-app README](frontend/apps/admin-app/README.md) |
| Contribute with a coding agent | [AGENTS.md](AGENTS.md), [.codex](.codex) |
| Audit normalized exports | [Normalized export validation](docs/标准化导出验证说明.md) |

## Contribution conventions

- New browser-facing business APIs must go through Java `/api/v1`; do not connect frontend code directly to internal Python GraphRAG endpoints.
- Changes to `normalized_docs.json`, GraphRAG metadata, MinIO paths, or material naming must be checked against both `pdf_ingest` and `graphrag_pipeline` contracts.
- Do not commit `.env` files, index outputs, caches, runtime directories, `node_modules`, or data containing real credentials.
- Run `python scripts/audit_repo_drift.py --strict` after changing active entry docs, runtime defaults, or the GraphRAG version baseline.

## Current boundaries

- `hybrid_v0` is an internal Java business mode that injects BM25 evidence into GraphRAG Basic; it is not an OpenAI-compatible model name.
- `smart` is only the student-app recommendation entrypoint. It resolves to `basic`, `local`, `global`, `drift`, or `hybrid_v0` before a query runs.
- Learning content, community features, global search, fine-grained RBAC editing, and comprehensive audit analytics are outside the v1 scope and are explicitly marked as unavailable rather than presented as working functionality.
- MySQL, MinIO, and GraphRAG indexes have distinct responsibilities. Redis and derived graph artifacts are not the sole source of truth for course or Q&A business facts.

Issues and pull requests are welcome for course-material processing, GraphRAG retrieval quality, and product-experience improvements.
