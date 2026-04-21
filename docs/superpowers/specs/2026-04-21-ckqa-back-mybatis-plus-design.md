# CKQA Back MyBatis-Plus 接入与全表代码生成设计

## 背景

`backend/ckqa-back/` 当前还是最小 Spring Boot 4 骨架，已有 `mysql-connector-j` 依赖，但还没有数据源配置、MyBatis-Plus、代码生成器以及任何基于 `ocqa` 库表的 Java 数据访问代码。

仓库内已有 `pdf_ingest/sql/ocqa.sql`，可确认当前库至少包含以下 21 张表：

- `courses`
- `parse_logs`
- `parse_results`
- `pdf_files`
- `users`
- `roles`
- `permissions`
- `user_roles`
- `role_permissions`
- `auth_identities`
- `course_memberships`
- `course_membership_events`
- `knowledge_bases`
- `kb_documents`
- `index_runs`
- `index_artifacts`
- `qa_sessions`
- `qa_messages`
- `qa_retrieval_logs`
- `qa_retrieval_hits`
- `authorization_audit_logs`

## 目标

第一步以“最小可运行”为目标，在不改动现有整体架构的前提下完成以下能力：

1. 在 `backend/ckqa-back/` 中接入 MyBatis-Plus，并保持与 Spring Boot 4.0.5 / Java 21 兼容。
2. 通过环境变量连接现有 MySQL `ocqa` 库，不把真实密码硬编码进仓库。
3. 提供一套可重复执行的代码生成入口，按真实库表一次性生成标准的：
   - `entity`
   - `mapper`
   - `service`
   - `service/impl`
   - `controller`
   - `resources/mapper/*.xml`
4. 让应用具备基础 MyBatis-Plus 运行配置，后续可以直接在生成代码基础上继续开发业务。

## 非目标

本次不做以下内容：

- 不实现复杂业务接口与自定义查询。
- 不引入认证、安全、审计等完整后端框架能力。
- 不对生成后的 21 个控制器做业务级手工润色。
- 不修改 Python 主链路模块的数据库契约。

## 方案选择

采用“方案 1：全量标准平铺生成”。

原因：

- 与“先接数据库并生成一套标准代码”的目标最一致。
- 改动范围最小，不需要先做领域拆分和复杂模板定制。
- 生成结果可直接作为后续业务开发基线。

## 技术方案

### 1. 运行时接入

- 在 `pom.xml` 中新增：
  - `mybatis-plus-spring-boot4-starter`
  - `mybatis-plus-generator`
  - `freemarker`
  - 标准测试依赖 `spring-boot-starter-test`
- 保留已有 `mysql-connector-j`。
- 在应用启动类上补充 `@MapperScan`，扫描生成后的 `mapper` 包。
- 增加 MyBatis-Plus 基础配置类，注册分页拦截器。

### 2. 数据源配置

运行时通过环境变量装配：

- `MYSQL_HOST`
- `MYSQL_PORT`
- `MYSQL_DATABASE`
- `MYSQL_USER`
- `MYSQL_PASSWORD`

`application.properties` 中只保留占位符与通用 JDBC 参数，例如：

- `useUnicode=true`
- `characterEncoding=utf8`
- `serverTimezone=Asia/Shanghai`
- `useSSL=false`
- `allowPublicKeyRetrieval=true`
- `tinyInt1isBit=true`

其中 `tinyInt1isBit=true` 用于让 `tinyint(1)` 更稳定地映射为布尔字段，这是 MyBatis-Plus 官方代码生成文档明确提到的兼容点。

### 3. 代码生成入口

新增一个可直接运行的生成器类，采用 MyBatis-Plus 新版 `FastAutoGenerator`。

要求：

- 默认生成全部 21 张表。
- 支持命令行参数，避免把表名、输出路径完全写死。
- 默认父包为 `org.ysu.ckqaback`。
- Java 输出目录为 `src/main/java`。
- XML 输出目录为 `src/main/resources/mapper`。
- 支持 `--overwrite` 控制是否覆盖已有生成文件。
- 支持 `--tables` 指定逗号分隔表名，便于后续只重生部分表。

### 4. 生成策略

- 实体类启用 Lombok。
- 启用字段注解，减少特殊字段映射歧义。
- 控制器启用 REST 风格。
- Mapper 生成 `BaseResultMap` 与 `BaseColumnList`。
- 服务接口统一命名为 `XxxService`，实现类命名为 `XxxServiceImpl`。
- 对包含 `is_deleted` 的表启用逻辑删除字段识别。

### 5. 包结构

采用最小复杂度的标准平铺结构：

```text
backend/ckqa-back/src/main/java/org/ysu/ckqaback/
├── CkqaBackApplication.java
├── config/
│   └── MybatisPlusConfig.java
├── codegen/
│   └── MybatisPlusCodeGenerator.java
├── controller/
├── entity/
├── mapper/
└── service/
    └── impl/
```

对应 XML：

```text
backend/ckqa-back/src/main/resources/mapper/
```

## 测试与验证

优先做最小但有效的验证：

1. 为 MyBatis-Plus 配置增加测试，确认分页拦截器 Bean 能注册成功。
2. 为生成器参数解析增加测试，确认默认值与自定义参数可正确工作。
3. 使用 Maven 编译项目，确保接入依赖与生成代码可通过编译。
4. 运行代码生成器，验证 21 张表对应代码与 XML 成功落盘。
5. 启动应用，确认 Spring 上下文与 Mapper 扫描正常。

## 风险与控制

### 风险 1：Spring Boot 4 与 MyBatis-Plus 兼容问题

控制方式：

- 使用 MyBatis-Plus 的 Boot4 starter，而不是 Boot3 starter。
- 版本选择基于当前可查到的官方/中央仓库发布版本。

### 风险 2：真实数据库连通性失败

控制方式：

- 运行时与生成器都只通过环境变量读取连接信息。
- 生成前先做编译和参数解析验证，失败时能快速定位是依赖问题还是数据库问题。

### 风险 3：一次性生成 21 张表文件较多

控制方式：

- 保持包结构平铺，先保证可运行。
- 生成器支持按表筛选和覆盖控制，后续按需局部再生。

## 交付结果

完成后应具备以下产物：

1. MyBatis-Plus 已接入的 Spring Boot 4 工程。
2. 基于环境变量的 MySQL 连接配置。
3. 可重复执行的全表代码生成器。
4. 一套已生成的标准 CRUD 骨架代码。
5. 基础测试、运行说明和验证命令。
