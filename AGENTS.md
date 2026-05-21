# CKQA Contributor Guide

## Scope

This file applies to the whole repository.

## Working Language

- Use Chinese for comments, docs, prompts, and user-facing explanations unless the task explicitly requires English.

## Project Layout

This repository currently has seven notable areas, with the two Python modules as the main workflow:

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
   - Auth, course read APIs, QA sessions/task events, learning memory, mode recommendation, and knowledge graph browsing are now partially wired to Java `/api/v1`; community, analysis, and some user flows remain explicit placeholders.
4. `frontend/apps/admin-app/`
   - Shared admin/teacher Vue 3 + Vite console frontend.
   - Has theme tokens, route guards, dashboard, system health page, live course/material/knowledge-base pages, material detail parse-progress presentation, live parse-results detail, course material upload feedback, knowledge-base build wizard, QA smoke validation, unified 403/404/500 pages, and Playwright browser fault-injection tests.
   - Secondary unless the task explicitly targets frontend work or repo entry docs.
5. `backend/ckqa-back/`
   - Spring Boot 4.0.5 + Java 21 phase-1 orchestration backend.
   - Provides `/api/v1` course, PDF, knowledge-base, index, async QA, QA smoke session, and system health endpoints.
   - Still depends on `pdf_ingest/` and `graphrag_pipeline/` for real parsing, indexing, and QA work.
6. `infra/`
   - Repository-root Docker Compose entrypoint for local MySQL, MinIO, One API, and Neo4j.
   - Runtime data directories are ignored by Git; preserve bind mounts when changing deployment.
7. `sql/`
   - Repository-root MySQL schema and migration scripts.
   - `sql/ocqa.sql` is the database initialization source of truth.

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
- `infra/README.md` when touching Docker Compose, local container deployment, or data mount layout

If docs and code differ, trust the code and call out the mismatch.

## Module Guidance

### `infra/`

Important files:

- `docker-compose.yml`
- `.env.example`
- `README.md`

Environment and commands:

- Prepare local secrets with `cp infra/.env.example infra/.env`, then edit `infra/.env`.
- Start all local infrastructure: `cd infra && docker compose up -d`.
- Verify: `cd infra && docker compose ps`.

Notes:

- The unified compose manages `mysql`, `minio`, `one-api`, and `neo4j` with the existing local ports.
- MySQL and MinIO defaults preserve the current external bind mounts `/home/sunlight/mysql/data` and `/home/sunlight/minio/data`.
- Neo4j and One API data live under `infra/neo4j/neo4j/` and `infra/one-api/one-api/data/`; these paths are runtime data and must not be committed.
- Do not run `docker compose down -v` unless explicitly asked to destroy local data.

### `sql/`

Important files:

- `ocqa.sql`
- `migrations/20260423_course_materials.sql`
- `migrations/20260429_qa_session_type.sql`
- `migrations/20260504_role_user_test_data.sql`
- `migrations/20260505_kb_build_runs.sql`
- `migrations/20260506_course_cover.sql`
- `migrations/20260506_course_member_access_test_data.sql`
- `migrations/20260506_jwt_auth_credentials.sql`
- `migrations/20260506_user_avatar_course_material_management.sql`

Notes:

- `sql/ocqa.sql` is now at the repository root, not under `pdf_ingest/`.
- From `pdf_ingest/`, use `../sql/ocqa.sql` and `../sql/migrations/...`.

### `pdf_ingest/`

Important files:

- `scripts/pdf_processor/mineru_parser.py`
- `scripts/pdf_processor/graphrag_exporter.py`
- `scripts/pdf_processor/block_model.py`
- `scripts/pdf_processor/block_renderer.py`
- `scripts/pdf_processor/text_cleaner.py`
- `scripts/pdf_processor/db_service.py`
- `scripts/pdf_processor/storage_service.py`
- `scripts/cleanup_legacy_course_data.py`

Environment and commands:

- Conda env: `courseKg`
- Install: `pip install -e .`
- Dev install: `pip install -e ".[dev]"`
- Tests: `python -m pytest tests/`

Notes:

- Runtime config comes from `.env`, loaded by `Config.from_env()`.
- The shared `courseKg` environment already has `pytest` installed, so repository-local verification can run the test command directly. For a fresh environment, `pip install -e ".[dev]"` is still the reproducible setup.
- MySQL state flow matters: `pending -> processing -> done/failed`.
- A course may contain multiple PDFs. When there is more than one file, prefer `--material-id` for parse/status/download/export commands; `--file-id` / `--file-name` are compatibility inputs.
- MinIO object paths are part of the real interface. Be careful when changing filenames or storage layout.
- `export-graphrag` output is the main contract consumed by `graphrag_pipeline`.
- For local full reset before re-extraction, use `python scripts/cleanup_legacy_course_data.py --env-file .env` for dry-run and add `--execute` only after checking the plan. By default it treats course IDs not matching `crs-YYYYMMDD-HHMMSS` as legacy data and deletes matching DB/MinIO references.

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
- Multi-PDF sync: `python utils/fetch_from_minio.py <course_id> --material-id <id> --clean`
- Validation sync: `python utils/fetch_from_minio.py <course_id> --material-id <id> --json-file normalized_docs.json --clean`
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
- Shared `output/` contains both parquet data and `lancedb/`; both are required for manual CLI/debug serving. Java-managed admin build runs use isolated workspaces under `runtime/kb-build-runs/` instead.
- `GRAPHRAG_BUILD_RUNS_ROOT` defaults to `${GRAPHRAG_ROOT}/runtime/kb-build-runs`; keep this runtime path ignored by Git and do not expose absolute workspace paths to browser clients.
- Java passes build-run query context to Python as `indexRunId` plus backend-only `dataDirUri`; Python must reject absolute paths and path traversal outside `GRAPHRAG_BUILD_RUNS_ROOT`.
- Build-run concurrency and activation are controlled by `GRAPHRAG_ALLOW_CONCURRENT_KB_BUILDS` and `GRAPHRAG_AUTO_ACTIVATION_POLICY`; the default activation policy is `latest-build-only`.
- Neo4j and One API containers are managed by the repository-root `infra/docker-compose.yml`; do not reintroduce module-local compose files under `graphrag_pipeline/`.
- After a local reset, `input/`, `output/`, `prompts/candidates/`, prompt tuning reports, and `prompts/final/active_prompt.json` may be absent or empty. Regenerate them from the current course extraction instead of assuming old `os` / `ds` artifacts exist.
- The reset default prompt state is `base`, pointing directly to `prompts/*.txt`; only use `finalize_candidate_prompt.py` after new candidates have been generated.
- When updating active guidance files or runtime defaults, run `python scripts/audit_repo_drift.py --strict`.

### `frontend/apps/student-app/`

- Vue 3 + Vite standalone student-side prototype.
- Preferred commands: `pnpm install`, `pnpm dev`, `pnpm build`, `pnpm preview`, `pnpm format`
- `package.json` currently uses the repo-aligned package name `student-app`.
- `package.json` currently declares Node `^20.19.0 || >=22.12.0`.
- Treat `node_modules/` as generated dependencies, not source.
- Treat this directory as part of the main CKQA repository, not as a separate nested Git repository.
- Current route tree is broader than the actual implemented views; unopened routes should use the explicit "未开放" status page rather than blank pages.
- `src/axios/index.js` injects JWT auth headers from the Pinia user store; `src/api/auth.js`, `src/api/courses.js`, `src/api/qa.js`, and `src/api/graph.js` are the current Java `/api/v1` browser boundary.
- The QA page should preserve `courseId`, `sessionId`, `mode`, and `topic` in route query via `src/views/qa/qa-route-query-model.js`; avoid remounting the whole module view for query-only changes.
- QA task streaming prefers `/api/v1/qa-sessions/{sessionId}/tasks/{taskId}/events` and must keep task polling as the fallback path.

### `frontend/apps/admin-app/`

- Vue 3 + Vite standalone admin/teacher console frontend.
- Preferred commands: `pnpm install`, `pnpm test`, `pnpm test:e2e`, `pnpm build`, `pnpm dev`, `pnpm preview`
- Treat `node_modules/` as generated dependencies, not source.
- Java `/api/v1` remains the formal browser boundary; do not wire formal UI flows directly to GraphRAG Python `/v1`.
- Current state: shell, theme system, dashboard, health page, login/status pages, unified error pages, courses, course detail, material upload/lifecycle, live material detail, live parse-results detail, knowledge-base list/detail, build wizard, index detail, and QA smoke validation are implemented against Java `/api/v1`; unopened routes still use explicit "未开放" pages.
- Material detail and parse-results pages should stay aligned with Java `/api/v1/pdf-files/*` compatibility routes: the detail page surfaces parse status plus `parseProgress`, while the parse-results page remains a read-only artifact list unless the task explicitly expands that scope.
- Course material upload currently accepts PDF only and defaults to a single-file 200MB limit; keep `frontend/apps/admin-app/src/views/pages/material-file-model.js`, Java `CourseMaterialProperties`, and Spring multipart config aligned when changing this limit.
- Knowledge-base build wizard state should be driven by Java build-run APIs and the URL `buildRunId`, not by browser-only query/sessionStorage state once a build run exists.
- Playwright E2E uses mocked `/api/v1` fault injection to verify local operation error panels. Do not commit `test-results/`, `playwright-report/`, `dist/`, or `node_modules/`.

### `backend/ckqa-back/`

- Java 21 + Spring Boot 4.0.5 + MyBatis-Plus 3.5.16 phase-1 orchestration backend.
- Unified response envelope is `code / message / data / timestamp`; business success code is `200`.
- Async QA uses Python GraphRAG `/v1/query-tasks`; cross-service task timestamps are exposed as Asia/Shanghai offset-free `LocalDateTime` strings.
- Local admin-app integration expects MySQL, `PDF_INGEST_ROOT`, `GRAPHRAG_ROOT`, `GRAPHRAG_BUILD_RUNS_ROOT`, and `GRAPHRAG_API_BASE_URL` to be configured before `/api/v1/system/health` is fully ready.
- `/api/v1/system/health` is a lightweight dependency check and no longer requires shared `GRAPHRAG_ROOT/output/lancedb`; `/api/v1/system/readiness` includes that shared CLI/debug output check.
- Knowledge-base build runs isolate input, output, logs, artifacts, active-index metadata, and QA smoke snapshots under `GRAPHRAG_BUILD_RUNS_ROOT`.
- Course material upload v1 accepts PDF only and defaults to 200MB per file; `COURSE_MATERIAL_MAX_FILE_SIZE_BYTES`, `CKQA_MULTIPART_MAX_FILE_SIZE`, and `CKQA_MULTIPART_MAX_REQUEST_SIZE` must stay compatible with the admin-app upload validator.
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
