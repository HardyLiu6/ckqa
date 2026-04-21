# ckqa-back

`backend/ckqa-back/` 是 CKQA 仓库中的 Java 后端工程，基于 Spring Boot 4.0.5 与 Java 21。当前它仍不是 CKQA 的主业务入口，但已经完成了 MyBatis-Plus 接入、MySQL 配置和基于 `ocqa` 库表的标准代码生成基线。

## 当前状态

- 技术栈：Spring Boot 4.0.5、Java 21、MyBatis-Plus 3.5.16、Maven Wrapper
- 当前能力：支持通过环境变量连接 MySQL，支持全表生成 `entity/mapper/service/controller`
- 当前角色：后端接口层开发基线，尚未承接正式业务接口实现

如果你当前目标是跑通 CKQA 主链路，请优先查看：

- [../../README.md](../../README.md)
- [../../pdf_ingest/README.md](../../pdf_ingest/README.md)
- [../../graphrag_pipeline/README.md](../../graphrag_pipeline/README.md)

## 真实入口与关键文件

| 文件 | 作用 |
| --- | --- |
| `pom.xml` | Maven 依赖与 Java 版本配置 |
| `src/main/java/org/ysu/ckqaback/CkqaBackApplication.java` | Spring Boot 启动类 |
| `src/main/java/org/ysu/ckqaback/config/MybatisPlusConfig.java` | MyBatis-Plus 分页插件与基础配置 |
| `src/main/java/org/ysu/ckqaback/codegen/MybatisPlusCodeGenerator.java` | 基于真实 MySQL 库表的代码生成入口 |
| `src/main/resources/application.properties` | 数据源与 MyBatis-Plus 运行配置 |
| `.env.example` | 运行应用与生成代码所需的环境变量示例 |

## 环境准备

```bash
cd backend/ckqa-back
java -version
./mvnw -version
```

建议环境：

- Java 21
- 本机可执行 `./mvnw`

## 环境变量

运行应用或执行代码生成前，请先准备以下环境变量：

```bash
export MYSQL_HOST=localhost
export MYSQL_PORT=23306
export MYSQL_DATABASE=ocqa
export MYSQL_USER=root
export MYSQL_PASSWORD=你的密码
```

也可以参考本目录下的 `.env.example` 手动配置到 IDE 运行参数中。

## 常用命令

### 生成全表标准代码

```bash
./mvnw -q -DskipTests compile exec:java \
  -Dexec.mainClass=org.ysu.ckqaback.codegen.MybatisPlusCodeGenerator \
  -Dexec.args="--overwrite=true"
```

如果只想重生部分表，可以传入 `--tables=`：

```bash
./mvnw -q -DskipTests compile exec:java \
  -Dexec.mainClass=org.ysu.ckqaback.codegen.MybatisPlusCodeGenerator \
  -Dexec.args="--tables=users,roles --overwrite=true"
```

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
- `mybatis-plus-spring-boot4-starter`
- `mybatis-plus-jsqlparser`
- `mybatis-plus-generator`
- `mysql-connector-j`
- `lombok`

并已落地：

- MyBatis-Plus `@MapperScan` 与分页插件配置
- 基于环境变量的 MySQL 数据源配置
- 21 张表对应的 `entity/mapper/service/serviceImpl/controller/xml` 生成结果
- 基础测试与可重复执行的代码生成入口

## 现阶段要避免的误判

- 不要把它当成已经接管 CKQA 的统一 API 网关
- 不要把自动生成的 Controller 当成已经完成业务语义设计的正式接口
- 不要假设它已经接好了 GraphRAG 调用链或完整权限体系

## 如果后续要把它做成正式后端

建议先按最小复杂度补齐这些内容：

1. 定义真正的业务 API，而不是直接暴露生成出来的空控制器。
2. 为 JSON 字段、枚举字段和审计字段补充更明确的建模与转换策略。
3. 补充统一响应结构、异常处理和参数校验。
4. 明确它与 `graphrag_pipeline`、`pdf_ingest` 的接口边界。
