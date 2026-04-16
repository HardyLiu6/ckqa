# ckqa-back

`backend/ckqa-back/` 是 CKQA 仓库中的 Java 后端骨架工程，基于 Spring Boot 4.0.5 与 Java 21。当前它还不是主业务入口，而是一个最小可启动的预留服务。

## 当前状态

- 技术栈：Spring Boot 4.0.5、Java 21、Maven Wrapper
- 代码规模：只有启动类、默认配置和默认测试
- 当前角色：后端接口层预留骨架，不承接正式业务能力

如果你当前目标是跑通 CKQA 主链路，请优先查看：

- [../../README.md](../../README.md)
- [../../pdf_ingest/README.md](../../pdf_ingest/README.md)
- [../../graphrag_pipeline/README.md](../../graphrag_pipeline/README.md)

## 真实入口与关键文件

| 文件 | 作用 |
| --- | --- |
| `pom.xml` | Maven 依赖与 Java 版本配置 |
| `src/main/java/org/ysu/ckqaback/CkqaBackApplication.java` | Spring Boot 启动类 |
| `src/main/resources/application.properties` | 当前仅包含应用名配置 |
| `src/test/java/org/ysu/ckqaback/CkqaBackApplicationTests.java` | 默认上下文加载测试 |

## 环境准备

```bash
cd backend/ckqa-back
java -version
./mvnw -version
```

建议环境：

- Java 21
- 本机可执行 `./mvnw`

## 常用命令

### 启动应用

```bash
./mvnw spring-boot:run
```

### 运行测试

```bash
./mvnw test
```

### 构建可执行包

```bash
./mvnw clean package
```

## 当前依赖概况

`pom.xml` 里已经引入了：

- `spring-boot-starter-webmvc`
- `spring-boot-starter-data-redis`
- `mysql-connector-j`
- `lombok`

但这些依赖目前还没有对应的业务层、接口层和配置落地。

## 现阶段要避免的误判

- 不要把它当成已经接管 CKQA 的统一 API 网关
- 不要假设它已经接入 MySQL、Redis 或 GraphRAG
- 不要把默认依赖列表理解成“功能已经完成”

## 如果后续要把它做成正式后端

建议先按最小复杂度补齐这些内容：

1. 明确它是直连 `graphrag_pipeline`，还是只做代理与编排层。
2. 定义课程、文件、导出、问答等核心接口。
3. 增加外部配置而不是继续停留在默认骨架。
4. 补充基础分层、异常处理和集成测试。
