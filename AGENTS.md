# CKQA Contributor Guide

## Scope

This file applies to the whole repository.

## Working Language

- Use Chinese for comments, docs, prompts, and user-facing explanations unless the task explicitly requires English.

## Project Layout

This repository currently has five notable areas, with the two Python modules as the main workflow:

1. `pdf_ingest/`
   - Main PDF processing pipeline.
   - Parses course PDFs through MinerU cloud API.
   - Stores files in MinIO and metadata/state in MySQL.
   - Exports `normalized_docs.json` plus GraphRAG-ready JSON.
2. `graphrag_pipeline/`
   - Main knowledge graph Q&A pipeline.
   - Dependency source of truth is `pyproject.toml`, currently pinned to Microsoft GraphRAG `3.0.9`.
   - Builds indexes and serves an OpenAI-compatible FastAPI endpoint.
3. `frontend/apps/student-app/`
   - Student-facing Vue 3 + Vite prototype managed directly inside the CKQA root repository.
   - Richer than `admin-app`, with Element Plus, Pinia, Vue Router, and multiple page prototypes.
   - Still not part of the production workflow; many routes are placeholders and the minimal Axios layer is not wired into a stable business contract.
4. `frontend/apps/admin-app/`
   - Shared admin/teacher Vue 3 + Vite console frontend.
   - Already has theme tokens, route guards, dashboard, system health page, and config-driven table/overview/workflow page templates.
   - Secondary unless the task explicitly targets frontend work or repo entry docs.
5. `backend/ckqa-back/`
   - Spring Boot 4.0.5 + Java 21 phase-1 orchestration backend.
   - Provides `/api/v1` course, PDF, index, async QA, and system health endpoints.
   - Still depends on `pdf_ingest/` and `graphrag_pipeline/` for real parsing, indexing, and QA work.

## Read First

Before making meaningful changes, read:

1. `README.md`
2. `pdf_ingest/CLAUDE.md`
3. `graphrag_pipeline/CLAUDE.md`

Read these when needed for more detail:

- `docs/标准化导出验证说明.md`
- `pdf_ingest/docs/MinerU PDF Parser.md`
- `graphrag_pipeline/README.md`
- `frontend/apps/student-app/README.md`
- `docs/admin-teacher-frontend-structure.md` when touching admin-app information architecture, routes, or RBAC
- `backend/ckqa-back/README.md`
- `docs/student-backend-graphrag-api-contract.md` when touching student/backend/GraphRAG API integration

If docs and code differ, trust the code and call out the mismatch.

## Module Guidance

### `pdf_ingest/`

Important files:

- `scripts/pdf_processor/mineru_parser.py`
- `scripts/pdf_processor/graphrag_exporter.py`
- `scripts/pdf_processor/block_model.py`
- `scripts/pdf_processor/block_renderer.py`
- `scripts/pdf_processor/text_cleaner.py`
- `scripts/pdf_processor/db_service.py`
- `scripts/pdf_processor/storage_service.py`

Environment and commands:

- Conda env: `courseKg`
- Install: `pip install -e .`
- Dev install: `pip install -e ".[dev]"`
- Tests: `python -m pytest tests/`

Notes:

- Runtime config comes from `.env`, loaded by `Config.from_env()`.
- The shared `courseKg` environment already has `pytest` installed, so repository-local verification can run the test command directly. For a fresh environment, `pip install -e ".[dev]"` is still the reproducible setup.
- MySQL state flow matters: `pending -> processing -> done/failed`.
- A course may contain multiple PDFs. When there is more than one file, prefer `--file-id` or `--file-name` for parse/status/download/export commands.
- MinIO object paths are part of the real interface. Be careful when changing filenames or storage layout.
- `export-graphrag` output is the main contract consumed by `graphrag_pipeline`.

### `graphrag_pipeline/`

Important files:

- `pyproject.toml`
- `scripts/build_prompt_tuning_samples.py`
- `scripts/build_audit_extraction_set.py`
- `scripts/generate_candidate_prompts.py`
- `scripts/run_graphrag_prompt_tune.py`
- `utils/main.py`
- `settings.yaml`
- `.env`
- `utils/fetch_from_minio.py`
- `utils/neo4jTest.py`
- `utils/graphrag3dknowledge.py`

Environment and commands:

- Conda env: `graphrag-oneapi`
- Install: `pip install -e ".[all]"`
- Tests: `python -m pytest tests/`
- Input sync: `python utils/fetch_from_minio.py <course_id> --clean`
- Multi-PDF sync: `python utils/fetch_from_minio.py <course_id> --pdf-file-id <id> --clean`
- Validation sync: `python utils/fetch_from_minio.py <course_id> --pdf-file-id <id> --json-file normalized_docs.json --clean`
- Index: `graphrag index --root .`
- Query local: `graphrag query --root . --method local "问题"`
- Query global: `graphrag query --root . --method global "问题"`
- Query drift: `graphrag query --root . --method drift "问题"`
- Query basic: `graphrag query --root . --method basic "问题"`
- API server: `python utils/main.py`

Notes:

- Trust `pyproject.toml` as the GraphRAG version source of truth; it is currently pinned to `3.0.9`.
- Repository-root `scripts/` should only keep repo-level tooling such as drift audit; GraphRAG-specific workflow scripts belong under `graphrag_pipeline/scripts/`.
- `settings.yaml` and `.env` are used by GraphRAG CLI.
- The shared `graphrag-oneapi` environment already has `pytest` installed, so tests can run directly. For a fresh environment, install project deps first and then add `pytest` separately because `pyproject.toml` does not currently declare a dev extra.
- `utils/main.py` reads repo-local `.env` / environment variables, defaults to the repo-local `output/` directory, and always delegates search to `graphrag query` in CLI mode.
- GraphRAG input is now direct `json`; `fetch_from_minio.py` only keeps `jsonl` conversion for backward compatibility.
- `output/` contains both parquet data and `lancedb/`; both are required for serving.
- When updating active guidance files or runtime defaults, run `python scripts/audit_repo_drift.py --strict`.

### `frontend/apps/student-app/`

- Vue 3 + Vite standalone student-side prototype.
- Preferred commands: `pnpm install`, `pnpm dev`, `pnpm build`, `pnpm preview`, `pnpm format`
- `package.json` currently uses the repo-aligned package name `student-app`.
- `package.json` currently declares Node `^20.19.0 || >=22.12.0`.
- Treat `node_modules/` as generated dependencies, not source.
- Treat this directory as part of the main CKQA repository, not as a separate nested Git repository.
- Current route tree is broader than the actual implemented views; unopened routes should use the explicit "未开放" status page rather than blank pages.
- `src/axios/index.js` now contains a minimal Axios wrapper and env-based runtime config, but there is still no stable business API contract wired into the views.

### `frontend/apps/admin-app/`

- Vue 3 + Vite standalone admin/teacher console frontend.
- Preferred commands: `pnpm install`, `pnpm test`, `pnpm build`, `pnpm dev`, `pnpm preview`
- Treat `node_modules/` as generated dependencies, not source.
- Java `/api/v1` remains the formal browser boundary; do not wire formal UI flows directly to GraphRAG Python `/v1`.
- Current state: shell, theme system, dashboard, health page, login/status pages, and config-driven business page templates are implemented; except for `/app/health`, most pages still use explicit mock data or upcoming-state placeholders.

### `backend/ckqa-back/`

- Java 21 + Spring Boot 4.0.5 + MyBatis-Plus 3.5.16 phase-1 orchestration backend.
- Unified response envelope is `code / message / data / timestamp`; business success code is `200`.
- Async QA uses Python GraphRAG `/v1/query-tasks`; cross-service task timestamps are exposed as Asia/Shanghai offset-free `LocalDateTime` strings.
- Treat it as secondary to the Python parsing/indexing chain unless the user explicitly wants Java backend, `/api/v1`, orchestration, or frontend integration work.

## Cross-Module Contract

Primary flow:

1. `pdf_ingest` parses PDF and exports GraphRAG input.
2. `graphrag_pipeline` consumes that input and builds/searches the graph.

Any change to exported metadata, naming, or storage structure must be checked for downstream GraphRAG compatibility.

## Safety Rules

- Do not casually edit `.env` files, generated outputs, caches, `node_modules/`, or IDE metadata.
- Do not expose or reuse real secrets, tokens, database passwords, or service credentials found in the repo.
- Prefer minimal, scoped changes in the relevant module.
- If a task is ambiguous, assume the Python pipelines are the main target before touching Java orchestration or frontend prototypes.
