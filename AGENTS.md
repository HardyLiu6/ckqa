# CKQA Contributor Guide

## Scope

This file applies to the whole repository.

## Working Language

- Use Chinese for comments, docs, prompts, and user-facing explanations unless the task explicitly requires English.

## Project Layout

This repository has three modules:

1. `pdf_ingest/`
   - Main PDF processing pipeline.
   - Parses course PDFs through MinerU cloud API.
   - Stores files in MinIO and metadata/state in MySQL.
   - Exports GraphRAG-compatible JSONL.
2. `graphrag_pipeline/`
   - Main knowledge graph Q&A pipeline.
   - Built on Microsoft GraphRAG `2.7.0`.
   - Builds indexes and serves an OpenAI-compatible FastAPI endpoint.
3. `backend/ckqa-back/`
   - Small Spring Boot skeleton project.
   - Not the primary implementation focus unless the task explicitly targets Java backend work.

## Read First

Before making meaningful changes, read:

1. `pdf_ingest/CLAUDE.md`
2. `graphrag_pipeline/CLAUDE.md`

Read these when needed for more detail:

- `pdf_ingest/docs/MinerU PDF Parser.md`
- `graphrag_pipeline/README.md`

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
- MySQL state flow matters: `pending -> processing -> done/failed`.
- MinIO object paths are part of the real interface. Be careful when changing filenames or storage layout.
- `export-graphrag` output is the main contract consumed by `graphrag_pipeline`.

### `graphrag_pipeline/`

Important files:

- `utils/main.py`
- `settings.yaml`
- `.env`
- `utils/fetch_from_minio.py`
- `utils/neo4jTest.py`
- `utils/graphrag3dknowledge.py`

Environment and commands:

- Conda env: `graphrag-oneapi`
- Install: `pip install -e ".[all]"`
- Index: `graphrag index --root .`
- Query local: `graphrag query --root . --method local --query "问题"`
- Query global: `graphrag query --root . --method global --query "问题"`
- API server: `python utils/main.py`

Notes:

- Keep `graphrag==2.7.0` pinned unless the task explicitly includes an upgrade.
- `settings.yaml` and `.env` are used by GraphRAG CLI.
- `utils/main.py` also has hardcoded runtime config that is not automatically synced with `.env`.
- `output/` contains both parquet data and `lancedb/`; both are required for serving.

### `backend/ckqa-back/`

- Java 21 + Spring Boot 4 skeleton.
- Treat it as secondary unless the user explicitly wants backend Java work.

## Cross-Module Contract

Primary flow:

1. `pdf_ingest` parses PDF and exports GraphRAG input.
2. `graphrag_pipeline` consumes that input and builds/searches the graph.

Any change to exported metadata, naming, or storage structure must be checked for downstream GraphRAG compatibility.

## Safety Rules

- Do not casually edit `.env` files, generated outputs, caches, or IDE metadata.
- Do not expose or reuse real secrets, tokens, database passwords, or service credentials found in the repo.
- Prefer minimal, scoped changes in the relevant module.
- If a task is ambiguous, assume the Python pipelines are the main target before touching the Java skeleton.
