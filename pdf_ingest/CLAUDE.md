# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

CKG (Course Knowledge Graph) — a PDF processing pipeline that parses course materials via the MinerU cloud API, stores physical material objects in MinIO, tracks course-material relations and parse state in MySQL, and transforms parsed content into GraphRAG-compatible input format.

输出规范补充：课程文本标准 schema 与预处理目标见 `docs/课程文本规范与预处理流程.md`。
手工验证与抽样验收流程见 `../docs/标准化导出验证说明.md`。

**Language:** Python 3 (>=3.10)
**Build system:** `pyproject.toml` (setuptools) — script-based project with `.env` runtime configuration.

## Commands

```bash
# Activate conda environment
conda activate courseKg

# Shared CKQA development environment already has pytest installed.
# For a fresh environment, dev extras remain the reproducible setup.
pip install -e ".[dev]"

# Run the main CLI (all commands go through this entry point)
python scripts/pdf_processor/mineru_parser.py <command> [options]

# Key commands: upload, parse, status, download, export-graphrag, list
# Example: upload and parse a PDF
python scripts/pdf_processor/mineru_parser.py upload <course_id> -f data/<course_id>/book.pdf --parse
python scripts/pdf_processor/mineru_parser.py upload <course_id> -f data/<course_id>/slides.pdf
python scripts/pdf_processor/mineru_parser.py parse <course_id> --material-id <material_id>
python scripts/pdf_processor/mineru_parser.py export-graphrag <course_id> --material-id <material_id> --mode section

# Export to GraphRAG format
python scripts/pdf_processor/mineru_parser.py export-graphrag <course_id> --material-id <material_id> --mode section

# Audit exported documents (example)
python scripts/pdf_processor/export_audit.py ../graphrag_pipeline/tmp_validate/os/normalized/normalized_docs.json

# Run all tests
python -m pytest tests/

# Run a single test file
python -m pytest tests/test_block_renderer.py

# Clean legacy local course data before a full re-extraction
python scripts/cleanup_legacy_course_data.py --env-file .env
python scripts/cleanup_legacy_course_data.py --env-file .env --execute

# Initialize database from the repository root SQL directory
mysql -u root -p ocqa < ../sql/ocqa.sql
```

## Architecture

### Processing Pipeline

```
PDF → MinerU Cloud API → JSON (content_list.json)
    → Block Model → Text Cleaner → Block Renderer → GraphRAG Exporter
    → MinIO (file storage) + MySQL (metadata/state tracking)
```

### Core Modules (all in `scripts/pdf_processor/`)

| Module | Responsibility |
|--------|---------------|
| `mineru_parser.py` | CLI entry point, orchestration (`PDFParserApp`), MinerU API client (`MinerUParser`) |
| `block_model.py` | `Block` dataclass + `BlockType` enum — unified representation of parsed content |
| `block_renderer.py` | Pluggable renderer registry — abstract `BlockRenderer` base with per-type concrete renderers |
| `graphrag_exporter.py` | Aggregates blocks into standard normalized documents, then projects them into GraphRAG JSON (section mode: by heading hierarchy, page mode: by page number) |
| `text_cleaner.py` | Noise removal — strips headers, footers, blank blocks |
| `db_service.py` | MySQL service layer with connection pooling (pymysql + dbutils) |
| `storage_service.py` | MinIO service layer with MD5-based deduplication |

### Key Design Patterns

- **Service Layer**: `db_service.py` and `storage_service.py` encapsulate all external I/O
- **Pluggable Renderers**: `BlockRendererRegistry` maps `BlockType` → renderer; add new types by registering a renderer subclass
- **Config dataclass**: `Config.from_env()` loads settings from `.env` with multi-location search
- **State Machine**: PDF processing status tracked as `pending → processing → done/failed` in MySQL

### Database Schema (`../sql/ocqa.sql`)

```
courses (1) ──→ (N) course_materials (N) ──→ (1) material_objects
                          │
                          └──→ (N) parse_results / parse_logs
```

- `material_objects.file_md5` enables physical object deduplication
- `course_materials` records the course-level relation, display name, material type, and parse state
- `parse_results.course_material_id` and `parse_logs.course_material_id` isolate outputs and logs by course material
- One course can contain multiple materials; operational commands should use `--material-id` first, while `--file-id` / `--file-name` remain compatibility inputs
- `v_course_parse_overview` view aggregates stats
- `scripts/cleanup_legacy_course_data.py` defaults to dry-run and treats course IDs not matching `crs-YYYYMMDD-HHMMSS` as legacy local data; use `--execute` only after checking its MySQL / MinIO plan

## Dependencies

核心依赖：requests, PyMySQL, DBUtils, minio, python-dotenv（完整列表见 `pyproject.toml`）

```bash
# 安装依赖
conda activate courseKg
pip install -e .

# 安装开发依赖（含 pytest）
pip install -e ".[dev]"
```

当前共享开发环境里的 `courseKg` 已安装 `pytest`，所以仓库内默认可以直接执行 `python -m pytest tests/`。如果是重建环境，仍以 `pip install -e ".[dev]"` 作为可复现方式。

## Configuration

All runtime config lives in `.env`: MinerU API credentials, MinIO connection, MySQL connection, processing options (model version, language, OCR, formula/table detection), polling timeouts.
