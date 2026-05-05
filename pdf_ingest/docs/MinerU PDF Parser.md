# MinerU PDF Parser v2.0

将课程PDF文件上传到MinerU云端进行解析，获取结构化JSON结果。

## 特性

- ✅ **MD5去重**：上传前校验文件MD5，避免重复存储
- ✅ **课程资料复用**：`material_objects` 负责物理对象去重，`course_materials` 负责课程内资料关系
- ✅ **MinIO存储**：PDF文件和解析结果统一存储在MinIO
- ✅ **MySQL元数据**：完整的课程资料、解析状态和日志管理
- ✅ **状态追踪**：记录解析进度和日志

## 架构图

```
┌─────────────────────────────────────────────────────────────────┐
│                         用户上传 PDF                              │
└─────────────────────────────────────────────────────────────────┘
                                │
                                ▼
┌─────────────────────────────────────────────────────────────────┐
│                      计算文件 MD5                                 │
└─────────────────────────────────────────────────────────────────┘
                                │
                                ▼
┌─────────────────────────────────────────────────────────────────┐
│         检查 material_objects 中是否存在相同 MD5                    │
│           ┌──── 存在 ────┐          ┌──── 不存在 ────┐           │
│           ▼              │          │                ▼           │
│    返回可复用物理对象     │          │         上传到 MinIO        │
│    并关联课程资料         │          │         写入 MySQL 记录     │
└─────────────────────────────────────────────────────────────────┘
                                │
                                ▼
┌─────────────────────────────────────────────────────────────────┐
│                     调用 MinerU API 解析                          │
│              1. 从 MinIO 下载 PDF 到临时目录                       │
│              2. 上传到 MinerU 云端                                │
│              3. 轮询等待解析完成                                   │
│              4. 下载解析结果                                      │
│              5. 上传结果到 MinIO                                  │
│              6. 更新 MySQL 状态                                   │
└─────────────────────────────────────────────────────────────────┘
```

## 目录结构

```
project_root/
├── data/                           # 本地数据目录（可选）
│   └── {课程id}/
│       ├── book.pdf
│       └── slides.pdf
├── scripts/
│   └── pdf_processor/
│       ├── mineru_parser.py        # 主脚本
│       ├── storage_service.py      # MinIO服务
│       └── db_service.py           # MySQL服务
├── sql/
│   └── init.sql                    # 数据库初始化脚本
├── .env                            # 配置文件
├── .env.example                    # 配置模板
├── .temp/                          # 临时文件目录（自动创建）
└── requirements.txt
```

## 快速开始

### 1. 安装依赖

```bash
pip install -r requirements.txt
```

### 2. 配置环境

复制配置模板：
```bash
cp .env.example .env
```

编辑 `.env` 文件，填写配置：
```env
# MinerU API
MINERU_API_TOKEN=your_token_here

# MinIO
MINIO_ENDPOINT=localhost:9000
MINIO_ACCESS_KEY=minioadmin
MINIO_SECRET_KEY=minioadmin

# MySQL
MYSQL_HOST=localhost
MYSQL_PORT=23306
MYSQL_USER=root
MYSQL_PASSWORD=your_password
MYSQL_DATABASE=ocqa
```

### 3. 初始化数据库

```bash
mysql -h 127.0.0.1 -P 23306 -u root -p ocqa < ../sql/ocqa.sql
```

### 4. 使用

```bash
# 上传PDF文件
python scripts/pdf_processor/mineru_parser.py upload os -f data/os/book.pdf

# 同一课程可上传多份PDF（按真实文件名区分）
python scripts/pdf_processor/mineru_parser.py upload os -f data/os/slides.pdf

# 上传并立即解析
python scripts/pdf_processor/mineru_parser.py upload os -f data/os/book.pdf --parse

# 列出课程下的资料，获得 material_id / course_material_id
python scripts/pdf_processor/mineru_parser.py list

# 解析指定课程资料（推荐）
python scripts/pdf_processor/mineru_parser.py parse os --material-id 3

# 兼容旧参数
python scripts/pdf_processor/mineru_parser.py parse os --file-id 3

# 解析指定课程资料
python scripts/pdf_processor/mineru_parser.py parse os --file-name book.pdf

# 查看指定课程资料状态
python scripts/pdf_processor/mineru_parser.py status os --material-id 3

# 下载指定课程资料解析结果
python scripts/pdf_processor/mineru_parser.py download os --material-id 3 -o ./output

# 导出指定课程资料的 GraphRAG 输入
python scripts/pdf_processor/mineru_parser.py export-graphrag os --material-id 3 --mode section

# 列出所有课程
python scripts/pdf_processor/mineru_parser.py list
```

`export-graphrag` 当前会先导出标准化中间结果 `normalized_docs.json`，再投影生成 GraphRAG 兼容文件 `section_docs.json` 或 `page_docs.json`。推荐新参数使用 `--material-id`；旧 `--file-id` / `--pdf-file-id` 仍保留兼容入口。

手工验证这些导出结果时，建议配合仓库根目录文档 `../../docs/标准化导出验证说明.md` 一起使用。

## 命令详解

### upload - 上传PDF文件

```bash
python scripts/pdf_processor/mineru_parser.py upload <course_id> -f <file_path> [options]

参数:
  course_id          课程ID，如: os, cs61b
  -f, --file         PDF文件路径（必需）
  --force            强制覆盖同课程下同名文件
  --parse            上传后立即解析
```

**MD5去重机制**：
- 上传前计算文件MD5
- 检查数据库中是否已存在相同MD5的物理资料对象
- 如已存在，复用已有 `material_objects` 记录，并把当前课程关联到同一份资料上
- 同课程下允许上传多份 PDF，按真实文件名区分
- 使用 `--force` 可覆盖同课程下的同名文件
- 当前 `file_md5` 只约束物理资料对象去重，同一份 PDF 可以被多门课程复用

### parse - 解析PDF

```bash
python scripts/pdf_processor/mineru_parser.py parse <course_id> [--material-id <id> | --file-id <id> | --file-name <name>]
```

如果课程下有多份 PDF，优先通过 `--material-id` 指定；`--file-id` 和 `--file-name` 仍保留兼容。

解析流程：
1. 从MinIO下载PDF到临时目录
2. 上传到MinerU云端
3. 轮询等待解析完成
4. 下载解析结果
5. 上传结果到MinIO
6. 清理临时文件

### status - 查看状态

```bash
python scripts/pdf_processor/mineru_parser.py status <course_id> [--material-id <id> | --file-id <id> | --file-name <name>]
```

返回信息包括：
- 文件ID与文件名
- 文件信息（MD5、大小）
- 解析状态（pending/processing/done/failed）
- 解析结果列表
- 最近日志

### download - 下载结果

```bash
python scripts/pdf_processor/mineru_parser.py download <course_id> [--material-id <id> | --file-id <id> | --file-name <name>] -o <output_dir>
```

### list - 列出所有课程

```bash
python scripts/pdf_processor/mineru_parser.py list
```

`list` 会返回课程下每个课程资料的 `material_id`、`display_name` 和 `parse_status`，多文件场景下建议先执行一次。

## 数据库设计

### 表结构

| 表名 | 说明 |
|------|------|
| `courses` | 课程基本信息 |
| `material_objects` | 物理资料对象信息，包含MD5、MinIO路径、文件大小 |
| `course_materials` | 课程资料关系，包含课程内展示名、资料类型、解析状态 |
| `parse_results` | 解析结果文件信息 |
| `parse_logs` | 解析过程日志 |

### ER图

```
courses (1) ──────< (N) course_materials (N) ──────> (1) material_objects
                             │
                             └──────< (N) parse_results / parse_logs
```

## MinIO存储结构

```
course-pdfs/                    # PDF文件存储桶
├── os/
│   └── book.pdf
│   └── slides.pdf
├── cs61b/
│   └── lecture1.pdf
└── ...

course-artifacts/               # 解析结果存储桶
├── os/
│   ├── material_3/
│   │   ├── images/
│   │   │   └── *.png
│   │   ├── *_content_list.json
│   │   ├── *_model.json
│   │   ├── layout.json
│   │   └── full.md
│   └── graphrag/
│       └── material_3/
│           ├── normalized_docs.json
│           ├── section_docs.json
│           └── page_docs.json
└── ...
```

兼容期内，旧 `pdf_3` 路径仍可能出现；抓取和导出逻辑会优先按 `material_3` 处理，再兼容旧命名。

其中：

1. `normalized_docs.json` 是标准化后的上游事实来源，保留章节层级、标准页码、文档类型与多模态元数据。
2. `section_docs.json` / `page_docs.json` 是面向 GraphRAG 的兼容投影。

当前阶段 4 已经补上的导出规则包括：

1. 目录页会在章节聚合前整体过滤，避免产生目录伪章节。
2. 标题尾部页码和目录点线会在聚合前清洗。
3. 表格正文不再默认输出 `[TABLE] ref=...` 占位符。
4. 图片正文不再默认输出 `[IMAGE] ref=...` 占位符，仅保留图题和图注。
5. 独立公式在正文中统一以 `公式：...` 形式出现。

当前阶段 5 补充的规则包括：

1. 会保守过滤前几页中的出版/版权噪声页，如 `CIP`、`ISBN`、`版权声明`、`出版社` 等强信号页。
2. 不再只依赖编号标题切分章节；对于讲义、课程说明、实验文档等无编号资料，也会使用 MinerU 的标题块和层级信息切分。
3. 超长 `section` 文档会自动按段落和句子做软切分，尽量稳定在适合 GraphRAG 建图的粒度范围内。

## 配置说明

| 配置项 | 说明 | 默认值 |
|--------|------|--------|
| `MINERU_API_TOKEN` | MinerU API Token | - |
| `MINERU_API_BASE_URL` | API基础URL | https://mineru.net/api/v4 |
| `MINIO_ENDPOINT` | MinIO地址 | localhost:9000 |
| `MINIO_ACCESS_KEY` | MinIO访问密钥 | minioadmin |
| `MINIO_SECRET_KEY` | MinIO秘密密钥 | minioadmin |
| `MINIO_SECURE` | 是否使用HTTPS | false |
| `MINIO_BUCKET_PDF` | PDF存储桶 | course-pdfs |
| `MINIO_BUCKET_ARTIFACTS` | 结果存储桶 | course-artifacts |
| `MYSQL_HOST` | MySQL主机 | localhost |
| `MYSQL_PORT` | MySQL端口 | 3306 |
| `MYSQL_USER` | MySQL用户 | root |
| `MYSQL_PASSWORD` | MySQL密码 | - |
| `MYSQL_DATABASE` | 数据库名 | mineru_parser |
| `MODEL_VERSION` | 模型版本 | vlm |
| `LANGUAGE` | 语言 | ch |
| `TIMEOUT` | 超时时间(秒) | 600 |
| `LOG_LEVEL` | 日志级别 | INFO |

## 获取 MinerU API Token

1. 访问 [https://mineru.net/](https://mineru.net/) 注册账号
2. 在 [https://mineru.net/apiManage](https://mineru.net/apiManage) 申请API权限
3. 审核通过后获取Token

## Docker 部署 MinIO 和 MySQL

CKQA 当前不再维护模块内独立 compose。MySQL、MinIO、One API、Neo4j 统一由仓库根目录 `infra/docker-compose.yml` 管理，数据保留策略见 `../../infra/README.md`。

首次配置：

```bash
cd ../../infra
cp .env.example .env
# 编辑 .env，填入当前 MySQL root 密码和 MinIO 账号密码
```

启动服务：

```bash
cd ../../infra
docker compose up -d mysql minio
```

## 常见问题

### Q: 上传时提示"文件已存在"怎么办？

这说明数据库中已有相同MD5的物理资料对象。可以：
1. 如果是同课程同名文件，使用 `--force` 覆盖
2. 如果只是重复内容，直接复用已有文件的解析结果
3. 如果是跨课程的相同文件内容，当前版本会复用同一个 `material_objects` 物理对象，并为当前课程新增一条 `course_materials` 关联

### Q: 解析失败如何重试？

解析失败后状态会变为 `failed`，可以直接再次运行带 `--material-id` 的 `parse` 命令重试；旧 `--file-id` / `--file-name` 也仍可兼容使用。

### Q: 如何清理临时文件？

临时文件在解析完成后会自动清理。如需手动清理：
```bash
rm -rf .temp/
```
