# MinerU PDF Parser v2.0

将课程PDF文件上传到MinerU云端进行解析，获取结构化JSON结果。

## 特性

- ✅ **MD5去重**：上传前校验文件MD5，避免重复存储
- ✅ **MinIO存储**：PDF文件和解析结果统一存储在MinIO
- ✅ **MySQL元数据**：完整的文件和解析状态管理
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
│              检查 MySQL 中是否存在相同 MD5                         │
│           ┌──── 存在 ────┐          ┌──── 不存在 ────┐           │
│           ▼              │          │                ▼           │
│    返回已存在信息         │          │         上传到 MinIO        │
│    (可复用解析结果)        │          │         写入 MySQL 记录     │
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
MYSQL_PORT=3306
MYSQL_USER=root
MYSQL_PASSWORD=your_password
MYSQL_DATABASE=mineru_parser
```

### 3. 初始化数据库

```bash
mysql -u root -p < sql/init.sql
```

### 4. 使用

```bash
# 上传PDF文件
python scripts/pdf_processor/mineru_parser.py upload os -f data/os/book.pdf

# 同一课程可上传多份PDF（按真实文件名区分）
python scripts/pdf_processor/mineru_parser.py upload os -f data/os/slides.pdf

# 上传并立即解析
python scripts/pdf_processor/mineru_parser.py upload os -f data/os/book.pdf --parse

# 列出课程下的文件，获得 file_id
python scripts/pdf_processor/mineru_parser.py list

# 解析指定文件
python scripts/pdf_processor/mineru_parser.py parse os --file-name book.pdf

# 查看指定文件状态
python scripts/pdf_processor/mineru_parser.py status os --file-id 3

# 下载指定文件解析结果
python scripts/pdf_processor/mineru_parser.py download os --file-id 3 -o ./output

# 导出指定文件的 GraphRAG 输入
python scripts/pdf_processor/mineru_parser.py export-graphrag os --file-id 3 --mode section

# 列出所有课程
python scripts/pdf_processor/mineru_parser.py list
```

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
- 检查数据库中是否已存在相同MD5的文件
- 如已存在，返回已有记录信息，避免重复上传
- 同课程下允许上传多份 PDF，按真实文件名区分
- 使用 `--force` 可覆盖同课程下的同名文件
- 当前 `file_md5` 仍是全局唯一，因此完全相同内容的 PDF 不能同时归属到多个课程

### parse - 解析PDF

```bash
python scripts/pdf_processor/mineru_parser.py parse <course_id> [--file-id <id> | --file-name <name>]
```

如果课程下有多份 PDF，必须通过 `--file-id` 或 `--file-name` 指定。

解析流程：
1. 从MinIO下载PDF到临时目录
2. 上传到MinerU云端
3. 轮询等待解析完成
4. 下载解析结果
5. 上传结果到MinIO
6. 清理临时文件

### status - 查看状态

```bash
python scripts/pdf_processor/mineru_parser.py status <course_id> [--file-id <id> | --file-name <name>]
```

返回信息包括：
- 文件ID与文件名
- 文件信息（MD5、大小）
- 解析状态（pending/processing/done/failed）
- 解析结果列表
- 最近日志

### download - 下载结果

```bash
python scripts/pdf_processor/mineru_parser.py download <course_id> [--file-id <id> | --file-name <name>] -o <output_dir>
```

### list - 列出所有课程

```bash
python scripts/pdf_processor/mineru_parser.py list
```

`list` 会返回课程下每个 PDF 的 `file_id`、`file_name` 和 `parse_status`，多文件场景下建议先执行一次。

## 数据库设计

### 表结构

| 表名 | 说明 |
|------|------|
| `courses` | 课程基本信息 |
| `pdf_files` | PDF文件信息，包含MD5、MinIO路径、解析状态 |
| `parse_results` | 解析结果文件信息 |
| `parse_logs` | 解析过程日志 |

### ER图

```
courses (1) ──────< (N) pdf_files (1) ──────< (N) parse_results
                          │
                          └──────< (N) parse_logs
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
│   ├── pdf_3/
│   │   ├── images/
│   │   │   └── *.png
│   │   ├── *_content_list.json
│   │   ├── *_model.json
│   │   ├── layout.json
│   │   └── full.md
│   └── graphrag/
│       └── pdf_3/
│           ├── section_docs.json
│           └── page_docs.json
└── ...
```

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

## Docker部署MinIO和MySQL

```yaml
# docker-compose.yml
version: '3.8'

services:
  minio:
    image: minio/minio:latest
    ports:
      - "9000:9000"
      - "9001:9001"
    environment:
      MINIO_ROOT_USER: minioadmin
      MINIO_ROOT_PASSWORD: minioadmin
    command: server /data --console-address ":9001"
    volumes:
      - minio_data:/data

  mysql:
    image: mysql:8.0
    ports:
      - "3306:3306"
    environment:
      MYSQL_ROOT_PASSWORD: your_password
      MYSQL_DATABASE: mineru_parser
    volumes:
      - mysql_data:/var/lib/mysql
      - ./sql/init.sql:/docker-entrypoint-initdb.d/init.sql

volumes:
  minio_data:
  mysql_data:
```

启动服务：
```bash
docker-compose up -d
```

## 常见问题

### Q: 上传时提示"文件已存在"怎么办？

这说明数据库中已有相同MD5的文件。可以：
1. 如果是同课程同名文件，使用 `--force` 覆盖
2. 如果只是重复内容，直接复用已有文件的解析结果
3. 如果是跨课程的相同文件内容，当前版本会拒绝上传，因为 `file_md5` 仍是全局唯一

### Q: 解析失败如何重试？

解析失败后状态会变为 `failed`，可以直接再次运行带 `--file-id` 或 `--file-name` 的 `parse` 命令重试。

### Q: 如何清理临时文件？

临时文件在解析完成后会自动清理。如需手动清理：
```bash
rm -rf .temp/
```
