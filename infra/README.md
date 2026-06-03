# CKQA Infra

`infra/` 是 CKQA 本地基础设施的统一 Docker Compose 入口，当前管理：

- MySQL `8.0.36`
- MinIO `RELEASE.2025-04-22T22-12-26Z`
- One API `latest`
- Neo4j `2025.12.1`
- Redis `7.4-alpine`

## 数据保留策略

当前运行容器的挂载关系已经按现状保留：

| 服务 | 容器名 | 数据目录 |
| --- | --- | --- |
| MySQL | `mysql` | `/home/sunlight/mysql/data` |
| MinIO | `minio` | `/home/sunlight/minio/data` |
| One API | `one-api` | `infra/one-api/one-api/data` |
| Neo4j | `neo4j` | `infra/neo4j/neo4j/` |
| Redis | `redis` | `infra/redis/data` |

其中 One API 和 Neo4j 的旧数据目录已随 `graphrag_pipeline/infra` 整体移动到根 `infra/`；MySQL 和 MinIO 原本就在仓库外部，因此统一 compose 默认继续挂载原路径，避免首次切换时得到空库或空对象桶。

如果以后要把 MySQL/MinIO 数据也迁到 `infra/mysql/data`、`infra/minio/data`，先停止容器，再复制数据并修改 `infra/.env` 中的 `CKQA_MYSQL_DATA_DIR`、`CKQA_MINIO_DATA_DIR`。不要在数据库或对象存储运行时复制数据目录。

## 首次配置

```bash
cd infra
cp .env.example .env
```

编辑 `infra/.env`：

- `MYSQL_ROOT_PASSWORD` 必须填写当前 MySQL root 密码；复用旧数据时不要改成新密码。
- `MINIO_ROOT_USER`、`MINIO_ROOT_PASSWORD` 建议填写当前 MinIO 容器账号密码。
- Redis 本地调试固定匿名访问，只需要保留 `CKQA_REDIS_PORT`。
- 如需新环境全部放在 `infra/` 下，可以把 `CKQA_MYSQL_DATA_DIR` 改为 `./mysql/data`，把 `CKQA_MINIO_DATA_DIR` 改为 `./minio/data`。

## 启动

```bash
cd infra
docker compose up -d
docker compose ps
```

等价的仓库根目录写法：

```bash
docker compose --env-file infra/.env -f infra/docker-compose.yml up -d
docker compose --env-file infra/.env -f infra/docker-compose.yml ps
```

## 从旧容器切换到统一 Compose

旧容器和新 compose 使用相同容器名。第一次接管时需要先移除旧容器对象，但不要删除数据目录：

```bash
docker stop neo4j one-api mysql minio
docker rm neo4j one-api mysql minio

cd infra
docker compose up -d
docker compose ps
```

`docker rm` 只删除容器对象，不会删除当前使用的 bind mount 数据目录。不要执行 `docker compose down -v` 或手动删除数据目录。

## 验证

```bash
docker ps --format 'table {{.Names}}\t{{.Image}}\t{{.Ports}}\t{{.Status}}'
docker compose --env-file infra/.env -f infra/docker-compose.yml config
```

基础端口：

- MySQL: `127.0.0.1:23306`
- MinIO API: `127.0.0.1:9000`
- MinIO Console: `127.0.0.1:9001`
- One API: `127.0.0.1:3301`
- Neo4j Browser: `127.0.0.1:17474`
- Neo4j Bolt: `127.0.0.1:17687`
- Redis: `127.0.0.1:16379`

Redis 当前用途：

- 后端登录限频和邮箱验证码短期状态。
- 学生端服务端读缓存：课程列表、课程知识库列表、智能推荐结果、Hybrid warmup/readiness。

Redis 不存正式问答答案，也不替代 MySQL / GraphRAG Python 的事实源。缓存服务在 Redis 异常时会回源执行；排查连通性可用：

```bash
docker compose --env-file infra/.env -f infra/docker-compose.yml exec -T redis redis-cli PING
```

数据库初始化脚本位于仓库根目录 `sql/`。当 MySQL 数据目录为空并首次初始化时，compose 会把 `../sql` 只读挂载到 `/docker-entrypoint-initdb.d`，自动执行其中的 SQL 文件；已有数据库不会重复执行初始化脚本。
