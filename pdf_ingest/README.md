# PDF Ingest

`pdf_ingest/` 是 CKQA 当前主链路中最完整的模块，负责把课程 PDF 变成可验收、可追踪、可供 GraphRAG 消费的标准化 JSON。

## 模块定位

- 上传课程 PDF 到 MinIO
- 调用 MinerU 云 API 解析 PDF
- 使用 MySQL 跟踪文件、批次和解析状态
- 导出 `normalized_docs.json`
- 导出下游使用的 `section_docs.json` / `page_docs.json`

如果你只想先跑通 CKQA，优先从这个模块开始。

## 当前状态

- 属于主链路模块
- 命令行入口稳定，测试覆盖相对完整
- 运行时配置主要来自 `.env`
- 与 `graphrag_pipeline/` 之间通过导出 JSON 和 MinIO 路径契约对接

## 真实入口与关键文件

| 文件 | 作用 |
| --- | --- |
| `scripts/pdf_processor/mineru_parser.py` | 主 CLI 入口，负责上传、解析、状态查询、下载与导出 |
| `scripts/pdf_processor/graphrag_exporter.py` | 聚合标准化文档并投影成 GraphRAG 输入 |
| `scripts/pdf_processor/block_model.py` | 统一块模型定义 |
| `scripts/pdf_processor/block_renderer.py` | 块渲染逻辑 |
| `scripts/pdf_processor/text_cleaner.py` | 文本清洗、去噪 |
| `scripts/pdf_processor/db_service.py` | MySQL 服务层 |
| `scripts/pdf_processor/storage_service.py` | MinIO 服务层 |
| `scripts/pdf_processor/export_audit.py` | 导出结果审计辅助脚本 |
| `sql/ocqa.sql` | 数据库初始化脚本 |

## 环境准备

```bash
cd pdf_ingest
conda activate courseKg
pip install -e ".[dev]"
```

当前共享开发环境里的 `courseKg` 已安装 `pytest`，所以仓库内默认可直接运行 `python -m pytest tests/`。如果是新环境，仍建议通过 `pip install -e ".[dev]"` 一次性补齐测试依赖。

环境要求：

- Python `>=3.10`
- MinIO
- MySQL
- MinerU API 凭据

运行时配置来自 `.env`，通过 `Config.from_env()` 加载。

## 数据库初始化

首次创建或重建本地数据库时，可直接执行：

```bash
cd pdf_ingest
mysql -h 127.0.0.1 -P 23306 -u root -p ocqa < sql/ocqa.sql
```

初始化完成后，建议先检查核心种子数据和关键表是否存在：

```sql
SELECT role_code, role_name FROM roles ORDER BY id;
SELECT permission_code FROM permissions ORDER BY id;
SHOW TABLES LIKE 'course_memberships';
SHOW TABLES LIKE 'knowledge_bases';
SHOW TABLES LIKE 'qa_sessions';
```

当前这套 schema 只负责数据库结构和基础种子，不代表运行态 API 已经接入这些表。

## 常用命令

### 上传 PDF

```bash
python scripts/pdf_processor/mineru_parser.py upload os -f data/os/book.pdf
python scripts/pdf_processor/mineru_parser.py upload os -f data/os/slides.pdf
python scripts/pdf_processor/mineru_parser.py upload os -f data/os/book.pdf --parse
```

### 解析、查看状态、下载结果

```bash
python scripts/pdf_processor/mineru_parser.py parse os --file-id 3
python scripts/pdf_processor/mineru_parser.py status os --file-id 3
python scripts/pdf_processor/mineru_parser.py download os --file-id 3 -o ./output
python scripts/pdf_processor/mineru_parser.py list
```

### 导出给 GraphRAG

```bash
python scripts/pdf_processor/mineru_parser.py export-graphrag os --file-id 3 --mode section
python scripts/pdf_processor/mineru_parser.py export-graphrag os --file-id 3 --mode section --with-page-docs
python scripts/pdf_processor/mineru_parser.py export-graphrag os --file-id 3 --mode page --force
python scripts/pdf_processor/mineru_parser.py export-graphrag os --file-id 3 --no-semantic-table
```

## 导出产物说明

### `normalized_docs.json`

- 用于人工验收、抽样检查、字段保真分析
- 更适合对照上游解析内容和标准化 schema

### `section_docs.json`

- 章节聚合模式输出
- 是下游 GraphRAG 默认最常用的输入

### `page_docs.json`

- 按页聚合模式输出
- 适合需要更强页码定位时使用

## 使用时要注意

- 一个课程可以有多份 PDF。
- 多 PDF 场景下，后续命令优先显式传 `--file-id` 或 `--file-name`。
- 当前系统按全局 `MD5` 去重，同一份 PDF 不能同时归属多个课程。
- MySQL 状态流转是 `pending -> processing -> done/failed`，不要轻易改动这条状态机。
- MinIO 中对象路径属于上下游真实接口的一部分，修改命名或目录结构时必须同时检查 `graphrag_pipeline/`。

## 验证方式

### 运行测试

```bash
python -m pytest tests/
```

如果只想快速验证某个文件，也可以直接执行 `python -m pytest tests/test_block_renderer.py`。

### 审计导出结果

```bash
python scripts/pdf_processor/export_audit.py ../graphrag_pipeline/tmp_validate/os/normalized/normalized_docs.json
```

### 运行仓库级漂移审计

```bash
python ../scripts/audit_repo_drift.py --strict
```

### 手工验收说明

- [../docs/标准化导出验证说明.md](../docs/标准化导出验证说明.md)
- [docs/课程文本规范与预处理流程.md](docs/课程文本规范与预处理流程.md)

## 与下游模块的关系

这个模块的主要合同输出是 GraphRAG 输入 JSON。下游 `graphrag_pipeline/` 默认通过 MinIO 拉取这些文件，再构建图索引和问答 API。

如果你修改了：

- 字段名
- metadata 结构
- 文件名
- MinIO 路径

就必须同步检查 `graphrag_pipeline/utils/fetch_from_minio.py` 和 `settings.yaml`。

## 相关文档

- [CLAUDE.md](CLAUDE.md)
- [docs/MinerU PDF Parser.md](<docs/MinerU PDF Parser.md>)
- [docs/课程文本规范与预处理流程.md](docs/课程文本规范与预处理流程.md)
- [../README.md](../README.md)
