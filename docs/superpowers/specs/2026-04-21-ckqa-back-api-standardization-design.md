# CKQA Back API 统一与 `users` 示例接口设计

## 背景

`backend/ckqa-back/` 当前已经完成：

- Spring Boot 4.0.5 + MyBatis-Plus 3.5.16 接入
- 基于 `ocqa` MySQL 的全表代码生成
- 21 张表对应的 `entity / mapper / service / controller / mapper xml` 骨架

但当前仍有几个明显问题：

1. 代码生成器属于开发辅助代码，仍放在 `src/main/java`，会污染业务主代码目录。
2. 生成出来的控制器仅有空的 `@RestController` 与默认路径，API 风格尚未统一。
3. 缺少统一返回体、全局异常处理和参数校验基线。
4. 没有一个真正可调用的示例接口来证明这套规范是可运行的。

## 目标

本次以“完整统一、最小落地”为目标，完成以下工作：

1. 将 MyBatis-Plus 代码生成器迁移到测试代码目录，保留其可重复执行能力。
2. 统一所有现有控制器的 API 路由风格为 `/api/v1/<resource>`。
3. 新增统一返回体与统一错误返回结构。
4. 新增全局异常处理机制。
5. 新增参数校验基线，统一使用 `jakarta.validation`。
6. 以 `users` 模块实现一组真实可调用的示例接口，作为后续其它模块的模板。

## 非目标

本次不做以下内容：

- 不把 21 个空控制器一次性都实现成完整 CRUD。
- 不引入鉴权、JWT、RBAC 运行时逻辑。
- 不引入复杂 DTO 映射框架。
- 不实现跨模块复杂业务编排。

## 核心决策

### 1. 路由版本方案

采用稳妥型版本化路由：

```text
/api/v1/<resource>
```

原因：

- 当前后端正在从骨架走向真正可消费 API，提前建立主版本边界更稳妥。
- 后续如果字段、语义或响应结构发生不兼容变化，可以平滑增加 `/api/v2/...`。

### 2. 资源命名规范

统一采用：

- 复数资源名
- kebab-case
- 显式常量管理

示例：

- `/api/v1/users`
- `/api/v1/roles`
- `/api/v1/course-memberships`
- `/api/v1/qa-messages`

## 统一后的 API 路由清单

本次会统一以下现有控制器的基础路由：

- `AuthIdentitiesController` -> `/api/v1/auth-identities`
- `AuthorizationAuditLogsController` -> `/api/v1/authorization-audit-logs`
- `CourseMembershipEventsController` -> `/api/v1/course-membership-events`
- `CourseMembershipsController` -> `/api/v1/course-memberships`
- `CoursesController` -> `/api/v1/courses`
- `IndexArtifactsController` -> `/api/v1/index-artifacts`
- `IndexRunsController` -> `/api/v1/index-runs`
- `KbDocumentsController` -> `/api/v1/kb-documents`
- `KnowledgeBasesController` -> `/api/v1/knowledge-bases`
- `ParseLogsController` -> `/api/v1/parse-logs`
- `ParseResultsController` -> `/api/v1/parse-results`
- `PdfFilesController` -> `/api/v1/pdf-files`
- `PermissionsController` -> `/api/v1/permissions`
- `QaMessagesController` -> `/api/v1/qa-messages`
- `QaRetrievalHitsController` -> `/api/v1/qa-retrieval-hits`
- `QaRetrievalLogsController` -> `/api/v1/qa-retrieval-logs`
- `QaSessionsController` -> `/api/v1/qa-sessions`
- `RolePermissionsController` -> `/api/v1/role-permissions`
- `RolesController` -> `/api/v1/roles`
- `UserRolesController` -> `/api/v1/user-roles`
- `UsersController` -> `/api/v1/users`

## 代码边界调整

### 1. 生成器迁移

将代码生成器从主代码迁移到测试代码目录：

```text
src/main/java/org/ysu/ckqaback/codegen/MybatisPlusCodeGenerator.java
    ->
src/test/java/org/ysu/ckqaback/support/codegen/MybatisPlusCodeGenerator.java
```

迁移后原则：

- 生成器保留 `main` 方法，后续仍可直接运行。
- 生成器属于开发支撑代码，不进入生产主包。
- 参数解析与生成器测试也一并放在测试域。

对应命令会切换为 test classpath 方式执行，例如：

```bash
./mvnw -q -DskipTests test-compile exec:java \
  -Dexec.classpathScope=test \
  -Dexec.mainClass=org.ysu.ckqaback.support.codegen.MybatisPlusCodeGenerator \
  -Dexec.args="--overwrite=true"
```

### 2. 业务主代码结构

新增业务基础设施包：

```text
src/main/java/org/ysu/ckqaback/
├── api/
│   ├── ApiPaths.java
│   ├── ApiResponse.java
│   ├── ApiErrorDetail.java
│   └── ApiResponseUtils.java
├── exception/
│   ├── BusinessException.java
│   └── GlobalExceptionHandler.java
├── user/
│   └── dto/
│       ├── UserCreateRequest.java
│       ├── UserQueryRequest.java
│       └── UserResponse.java
└── controller/
```

目标是让“API 规范代码”与“模块业务代码”分开，避免控制器内堆放杂项逻辑。

## 统一返回体设计

统一使用一个稳定结构：

```json
{
  "code": 200,
  "message": "操作成功",
  "data": {},
  "timestamp": "2026-04-21T11:00:00"
}
```

字段含义：

- `code`：业务状态码，`200` 表示成功
- `message`：人类可读消息
- `data`：业务数据
- `timestamp`：服务端响应时间

失败时仍返回相同骨架，例如：

```json
{
  "code": 4001,
  "message": "参数校验失败",
  "data": {
    "errors": [
      {
        "field": "username",
        "message": "用户名不能为空"
      }
    ]
  },
  "timestamp": "2026-04-21T11:00:00"
}
```

## 异常处理设计

引入统一异常体系：

- `BusinessException`
- `GlobalExceptionHandler`

统一处理以下异常：

- `MethodArgumentNotValidException`
- `BindException`
- `ConstraintViolationException`
- `HttpMessageNotReadableException`
- `IllegalArgumentException`
- `Exception`

处理原则：

- 参数错误返回 4xx 风格业务码
- 业务异常返回清晰消息
- 未知异常统一降级为通用错误消息，避免直接暴露堆栈内容

## 参数校验设计

新增 `spring-boot-starter-validation`，统一使用 `jakarta.validation`。

约束：

- 控制器请求体不直接暴露 entity
- 所有新增写接口都走 DTO
- 校验失败统一由全局异常处理器接管

本次 `users` 示例接口会使用：

- `@NotBlank`
- `@Size`
- `@Pattern`（如需要）
- `@Valid`
- `@Validated`

## `users` 示例接口设计

本次实现一组最小但完整可调用的接口：

### 1. 查询单个用户

```http
GET /api/v1/users/{id}
```

行为：

- 按主键查询
- 查不到返回业务异常
- 返回 `UserResponse`

### 2. 分页查询用户

```http
GET /api/v1/users?page=1&size=10&username=tom&status=active
```

行为：

- 基于 MyBatis-Plus `Page` 做分页
- 支持按 `username`、`status` 过滤
- 返回统一分页数据结构，放在 `ApiResponse.data` 中

### 3. 创建用户

```http
POST /api/v1/users
Content-Type: application/json
```

请求字段：

- `userCode`
- `username`
- `displayName`
- `passwordHash`
- `status` 可选

行为：

- 做参数校验
- 写入前校验 `userCode` 与 `username` 唯一性
- 默认 `status` 可为 `active`
- 返回创建后的 `UserResponse`

### 4. DTO 与脱敏

`UserResponse` 不暴露：

- `passwordHash`

返回仅保留对外必要字段：

- `id`
- `userCode`
- `username`
- `displayName`
- `status`
- `lastLoginAt`
- `createdAt`
- `updatedAt`

## 服务层设计

`users` 模块遵循最小复杂度分层：

- `UsersController`：接收请求、参数校验、返回统一响应
- `UsersService`：复用生成出来的 Service 接口
- `UsersServiceImpl`：补充最小业务方法
- `UsersMapper`：继续使用 MyBatis-Plus 基础能力

为了避免一次性推倒生成代码，本次会以“在生成代码上增量补方法”为主，不做大规模改造。

## 测试策略

按照 TDD 执行，至少覆盖：

1. 生成器迁移后的参数解析测试
2. 统一返回体工具测试
3. 全局异常处理的 WebMvc 测试
4. `users` 控制器成功场景测试
5. `users` 控制器参数校验失败测试

验证命令：

```bash
./mvnw test
./mvnw -DskipTests compile
./mvnw spring-boot:run
```

## 风险与控制

### 风险 1：一次性修改全部 controller 路由

控制方式：

- 仅统一类级 `@RequestMapping`
- 除 `users` 外，不在本次为其他空控制器增加复杂业务方法

### 风险 2：生成器迁移后命令不可用

控制方式：

- 同步更新 README
- 保留 `main` 方法
- 用 test classpath 运行并补测试验证

### 风险 3：统一返回体后 future API 风格锁定

控制方式：

- 返回体保持最小字段集：`code / message / data / timestamp`
- 错误详情统一收在 `data.errors`，避免后续大改顶层结构

## 交付结果

完成后应具备：

1. 已迁移到测试目录的代码生成器
2. 全部 controller 统一的 `/api/v1/<resource>` 路由
3. 统一返回体与统一错误响应
4. 全局异常处理与参数校验基线
5. `users` 模块的可调用示例接口
6. 对应测试、文档和运行命令
