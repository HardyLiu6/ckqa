# 管理端课程创建与教师绑定实施计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 落地“新建课程必须绑定教师、课程 ID 由系统生成”的新课程创建链路，并为旧课程补齐教师关系与后续 `course_id` 规范化迁移准备可验证路径。

**Architecture:** 后端把写操作从 `CourseLookupService` 拆到 `CourseCommandService`，由事务同时写入 `courses` 与初始教师 `course_memberships`；课程教师展示继续以 `course_memberships` 为事实来源。管理端只改 `frontend/apps/admin-app/`，通过远程教师 autocomplete 调用 Java `/api/v1/users`。旧数据迁移分为“补齐教师关系”和“课程标识规范化”两个阶段，Phase 3 只在新链路验证稳定后进入迁移窗口。

**Tech Stack:** Java 21, Spring Boot 4.0.5, MyBatis-Plus 3.5.16, MySQL 8, Maven, JUnit 5, Mockito, MockMvc, Vue 3, Vite, Element Plus, Pinia, Playwright, Node test runner, Python 3 + PyMySQL

---

## 设计来源

- Spec: `docs/superpowers/specs/2026-05-04-admin-app-course-creation-teacher-binding-design.md`
- Scope:
  - 后端：`backend/ckqa-back/`
  - 管理端：`frontend/apps/admin-app/`
  - 数据脚本：`scripts/normalize_course_teachers.py`
  - Schema 参考：`pdf_ingest/sql/ocqa.sql`
- 不修改：`frontend/apps/student-app/`

## 文件结构与职责

### 后端课程创建链路

- Modify: `backend/ckqa-back/src/main/java/org/ysu/ckqaback/course/dto/CourseCreateRequest.java`
  移除可写 `courseId`，新增必填 `teacherUserId`。
- Create: `backend/ckqa-back/src/main/java/org/ysu/ckqaback/course/CourseCommandService.java`
  负责课程创建主事务、教师校验、系统 `courseId` 重试生成、membership 写入。
- Create: `backend/ckqa-back/src/main/java/org/ysu/ckqaback/course/CourseIdGenerator.java`
  生成 `crs-YYYYMMDD-XXXXXX`，随机段为小写 base36 字符集 `a-z0-9`。
- Create: `backend/ckqa-back/src/main/java/org/ysu/ckqaback/course/CourseMembershipAuditWriter.java`
  监听课程创建审计事件，在主事务提交后尽力写入 `course_membership_events`，失败只记录日志。
- Create: `backend/ckqa-back/src/main/java/org/ysu/ckqaback/course/CourseMembershipAuditEvent.java`
  课程创建主事务内发布的审计事件，携带 membership ID、课程标识和操作者。
- Create: `backend/ckqa-back/src/main/java/org/ysu/ckqaback/course/dto/CourseTeacherResponse.java`
  课程列表、详情与创建响应里的教师摘要。
- Modify: `backend/ckqa-back/src/main/java/org/ysu/ckqaback/course/dto/CourseSummaryResponse.java`
  增加 `teachers` 与 `teacherCount`。
- Modify: `backend/ckqa-back/src/main/java/org/ysu/ckqaback/course/dto/CourseDetailResponse.java`
  增加 `teachers` 与 `teacherCount`。
- Modify: `backend/ckqa-back/src/main/java/org/ysu/ckqaback/course/CourseLookupService.java`
  删除 `createCourse()`，保留只读职责，并从 active teacher membership 组装教师展示。
- Modify: `backend/ckqa-back/src/main/java/org/ysu/ckqaback/controller/CoursesController.java`
  `POST /api/v1/courses` 改用 `CourseCommandService`。

### 后端教师候选查询

- Modify: `backend/ckqa-back/src/main/java/org/ysu/ckqaback/user/dto/UserQueryRequest.java`
  增加 `roleCode` 与 `keyword`。
- Modify: `backend/ckqa-back/src/main/java/org/ysu/ckqaback/service/UsersService.java`
  改为接收 `UserQueryRequest`，并增加 `hasRole(Long userId, String roleCode)`。
- Modify: `backend/ckqa-back/src/main/java/org/ysu/ckqaback/service/impl/UsersServiceImpl.java`
  支持 `roleCode + keyword + status` 分页过滤；教师候选空 keyword 时按 `user_code ASC, id ASC`。
- Modify: `backend/ckqa-back/src/main/java/org/ysu/ckqaback/mapper/UsersMapper.java`
  增加带角色过滤的分页查询与角色计数方法。
- Modify: `backend/ckqa-back/src/main/resources/mapper/UsersMapper.xml`
  增加 `user_roles` + `roles` join 查询。
- Modify: `backend/ckqa-back/src/main/java/org/ysu/ckqaback/controller/UsersController.java`
  列表接口把完整 `UserQueryRequest` 传入 service。

### 管理端

- Create: `frontend/apps/admin-app/src/api/users.js`
  封装 `GET /api/v1/users`，返回规范化分页数据。
- Modify: `frontend/apps/admin-app/src/views/pages/creation-form-model.js`
  课程创建表单移除 `courseId`，新增 `teacherUserId`，新增教师下拉选项转换方法。
- Modify: `frontend/apps/admin-app/src/views/pages/ModulePage.vue`
  新建课程弹窗加入远程教师选择器，提交 payload 不再包含 `courseId`。
- Modify: `frontend/apps/admin-app/src/views/pages/module-loaders.js`
  课程列表加入“授课教师”列，行数据展示 `teachers` / `teacherCount`。
- Modify: `frontend/apps/admin-app/src/views/pages/module-content.js`
  表格列配置与筛选列索引随教师列后移。
- Modify: `frontend/apps/admin-app/src/app-shell.test.js`
  更新 API、表单、表格列与远程教师搜索测试。
- Modify: `frontend/apps/admin-app/e2e/local-operation-errors.spec.js`
  覆盖教师候选加载失败/为空时的本地错误态。

### 旧课程教师补齐

- Create: `scripts/normalize_course_teachers.py`
  dry-run/apply 脚本，按 CSV 映射补齐缺失 active teacher membership。
- Create: `docs/migrations/course_teacher_mapping.example.csv`
  映射文件示例。
- Create: `pdf_ingest/tests/test_normalize_course_teachers.py`
  覆盖 mapping 校验、缺失教师、非 active teacher、fallback 开关与 dry-run 报告。

### 后续 `course_id` 规范化迁移

- No code change in Phase 1 branch.
- Phase 3 进入前必须单独生成迁移执行单，包含 `SHOW CREATE TABLE` 审计、MinIO 对象清单、GraphRAG 重建耗时试迁移结果。
- 当前已确认 `course_materials.course_id` 与 `parse_results.course_id` 只有索引没有外键，Phase 3 迁移必须手动 `UPDATE` 并做孤儿扫描。

---

## Phase 1: 后端新建课程链路

### Task 1: 写后端失败测试锁定新契约

**Files:**
- Create: `backend/ckqa-back/src/test/java/org/ysu/ckqaback/course/CourseCommandServiceTest.java`
- Create: `backend/ckqa-back/src/test/java/org/ysu/ckqaback/course/CourseMembershipAuditWriterTest.java`
- Modify: `backend/ckqa-back/src/test/java/org/ysu/ckqaback/course/CourseLookupServiceTest.java`
- Modify: `backend/ckqa-back/src/test/java/org/ysu/ckqaback/controller/CoursesControllerWebMvcTest.java`
- Modify: `backend/ckqa-back/src/test/java/org/ysu/ckqaback/controller/UsersControllerWebMvcTest.java`

- [ ] **Step 1: 新增 `CourseCommandServiceTest`**

覆盖以下断言：

1. `CourseCreateRequest` 不需要 `courseId`。
2. 自动生成 `courseId` 匹配 `^crs-[0-9]{8}-[a-z0-9]{6}$`。
3. 成功创建时保存 `courses`，再保存 active teacher membership。
4. membership 字段包含：
   - `membershipRole = "teacher"`
   - `status = "active"`
   - `accessSource = "manual"`
   - `changeReason = "COURSE_CREATION_INITIAL_TEACHER"`
5. 非 teacher 角色用户被拒绝。
6. disabled teacher 被拒绝。
7. `course_membershipsService.save()` 失败时异常向外抛出，课程事务由 Spring 回滚。
8. membership 保存成功后发布 `CourseMembershipAuditEvent`。
9. `CourseMembershipAuditWriterTest` 直接调用 writer 方法，验证事件写入失败时异常被吞掉并记录 warn 日志。
10. 生成器连续返回冲突 ID 5 次时返回 409 业务错误。

关键 mock 形状：

```java
CourseCreateRequest request = new CourseCreateRequest();
request.setCourseName("数据库系统");
request.setTeacherUserId(8L);
request.setAccessPolicy("restricted");

when(usersService.getRequiredById(8L)).thenReturn(activeTeacher());
when(usersService.hasRole(8L, "teacher")).thenReturn(true);
when(courseIdGenerator.generate()).thenReturn("crs-20260504-7f3k2a");
when(coursesService.save(any(Courses.class))).thenAnswer(invocation -> {
    Courses saved = invocation.getArgument(0);
    saved.setId(12L);
    return true;
});
when(courseMembershipsService.save(any(CourseMemberships.class))).thenAnswer(invocation -> {
    CourseMemberships saved = invocation.getArgument(0);
    saved.setId(21L);
    return true;
});
```

membership 保存失败场景使用：

```java
doThrow(new RuntimeException("DB error"))
    .when(courseMembershipsService).save(any(CourseMemberships.class));
```

该场景验证的是异常从 command service 向外抛出，并由 Spring 事务拦截器触发回滚；service 内部不能 catch membership 保存异常后继续返回成功响应。

`@TransactionalEventListener(phase = AFTER_COMMIT)` 依赖 Spring 事务上下文，不能在 Mockito-only 的 `CourseCommandServiceTest` 中验证监听器自动触发。单元测试分工固定为：

1. `CourseCommandServiceTest` 只验证 command service 在 membership 保存成功后调用 `ApplicationEventPublisher.publishEvent(...)`。
2. `CourseMembershipAuditWriterTest` 直接调用 `writeInitialTeacherBoundEventSafely(event)`，mock `courseMembershipEventsService.save(...)` 抛异常，并断言 writer 不向外抛出。

writer 容错测试形状：

```java
CourseMembershipAuditEvent event = new CourseMembershipAuditEvent(21L, "crs-20260504-7f3k2a", 8L, null);
doThrow(new RuntimeException("audit db error"))
    .when(courseMembershipEventsService).save(any(CourseMembershipEvents.class));

assertThatCode(() -> auditWriter.writeInitialTeacherBoundEventSafely(event))
    .doesNotThrowAnyException();
```

如后续需要验证 `AFTER_COMMIT` 真实触发时机，应新增 Spring 集成测试并使用真实事务提交；不要把该语义伪装成 Mockito 单元测试。

- [ ] **Step 2: 调整 `CourseLookupServiceTest`**

把创建课程相关测试移到 `CourseCommandServiceTest`；保留并扩展只读测试：

1. 课程列表返回 `teachers` 与 `teacherCount`。
2. 无 active teacher 时返回空数组与 `teacherCount = 0`。
3. 多名教师时返回稳定顺序，按 membership `id ASC` 或用户 `userCode ASC` 保持可预测。

- [ ] **Step 3: 调整 `CoursesControllerWebMvcTest`**

`POST /courses` 请求体改为：

```json
{
  "courseName": "数据库系统",
  "teacherUserId": 8,
  "description": "数据库课程资料",
  "accessPolicy": "restricted"
}
```

断言：

1. 请求体不含 `courseId` 仍通过。
2. 响应包含生成后的 `courseId`。
3. 响应包含 `teachers[0].userId = 8`。
4. 请求体缺 `teacherUserId` 返回参数校验失败。

- [ ] **Step 4: 调整 `UsersControllerWebMvcTest`**

覆盖：

1. `/api/v1/users?roleCode=teacher&status=active&keyword=zhang&page=1&size=20` 把完整查询对象传到 service。
2. `roleCode` 只允许 `student|teacher|admin`。
3. `keyword` 长度超限返回参数校验失败。

- [ ] **Step 5: 运行后端定向测试确认失败**

Run:

```bash
cd backend/ckqa-back
./mvnw -q -Dtest=CourseCommandServiceTest,CourseMembershipAuditWriterTest,CourseLookupServiceTest,CoursesControllerWebMvcTest,UsersControllerWebMvcTest test
```

Expected: FAIL，失败原因应集中在新类、新字段、新 service 方法尚未实现。

### Task 2: 实现教师候选查询与角色校验

**Files:**
- Modify: `backend/ckqa-back/src/main/java/org/ysu/ckqaback/user/dto/UserQueryRequest.java`
- Modify: `backend/ckqa-back/src/main/java/org/ysu/ckqaback/service/UsersService.java`
- Modify: `backend/ckqa-back/src/main/java/org/ysu/ckqaback/service/impl/UsersServiceImpl.java`
- Modify: `backend/ckqa-back/src/main/java/org/ysu/ckqaback/mapper/UsersMapper.java`
- Modify: `backend/ckqa-back/src/main/resources/mapper/UsersMapper.xml`
- Modify: `backend/ckqa-back/src/main/java/org/ysu/ckqaback/controller/UsersController.java`

- [ ] **Step 1: 扩展请求 DTO**

`UserQueryRequest` 新增：

```java
@Size(max = 128, message = "keyword长度不能超过128")
private String keyword;

@Pattern(regexp = "student|teacher|admin", message = "roleCode取值不合法")
private String roleCode;
```

- [ ] **Step 2: 调整 service 契约**

把 controller 使用的分页方法收口为：

```java
IPage<Users> pageUsers(UserQueryRequest request);

boolean hasRole(Long userId, String roleCode);
```

- [ ] **Step 3: 增加 mapper 查询**

`UsersMapper` 增加：

```java
IPage<Users> selectUserPage(
        Page<Users> page,
        @Param("username") String username,
        @Param("status") String status,
        @Param("roleCode") String roleCode,
        @Param("keyword") String keyword
);

long countUserRole(@Param("userId") Long userId, @Param("roleCode") String roleCode);
```

XML 查询规则：

1. `roleCode` 非空时 join `user_roles ur` 与 `roles r`，并过滤 `r.role_code = #{roleCode}`。
2. `keyword` 非空时匹配 `u.user_code`、`u.username`、`u.display_name`。
3. `username` 只作为向下兼容字段保留，新调用方应使用 `keyword`。
4. `UsersServiceImpl` 必须先做互斥归一化：当 `keyword` 非空时传给 mapper 的 `username` 置为 `null`；只有 `keyword` 为空时才使用 legacy `username`。
5. XML 中为 `username` 条件加注释说明 legacy 语义，避免后续误以为 `username` 与 `keyword` 应该同时 AND。
6. `status` 非空时过滤 `u.status`。
7. 排序：
   - `roleCode` 非空：`u.user_code ASC, u.id ASC`
   - `roleCode` 为空：`u.created_at DESC, u.id DESC`

- [ ] **Step 4: 更新 controller**

`UsersController.listUsers()` 改为：

```java
IPage<Users> page = usersService.pageUsers(request);
```

- [ ] **Step 5: 运行用户接口测试**

Run:

```bash
cd backend/ckqa-back
./mvnw -q -Dtest=UsersControllerWebMvcTest test
```

Expected: PASS

### Task 3: 实现课程教师读模型

**Files:**
- Create: `backend/ckqa-back/src/main/java/org/ysu/ckqaback/course/dto/CourseTeacherResponse.java`
- Modify: `backend/ckqa-back/src/main/java/org/ysu/ckqaback/course/dto/CourseSummaryResponse.java`
- Modify: `backend/ckqa-back/src/main/java/org/ysu/ckqaback/course/dto/CourseDetailResponse.java`
- Modify: `backend/ckqa-back/src/main/java/org/ysu/ckqaback/course/CourseLookupService.java`

- [ ] **Step 1: 新增教师摘要 DTO**

字段固定为：

```java
private final Long userId;
private final String userCode;
private final String username;
private final String displayName;
```

- [ ] **Step 2: 响应 DTO 增加教师字段**

`CourseSummaryResponse` 与 `CourseDetailResponse` 增加：

```java
private final List<CourseTeacherResponse> teachers;
private final Long teacherCount;
```

初始化时对 null 使用 `List.of()` 与 `0L`，避免前端判空分叉。

- [ ] **Step 3: `CourseLookupService` 注入 membership 与 users service**

新增依赖：

```java
private final CourseMembershipsService courseMembershipsService;
private final UsersService usersService;
```

查询逻辑：

1. 列表页先得到分页前的课程集合。
2. 取出本页 `courseId` 后，先判断列表是否为空；为空时直接返回空教师 map，避免 MyBatis-Plus `in()` 空集合被跳过后误查全表。
3. 使用一次 `WHERE course_id IN (...) AND membership_role = 'teacher' AND status = 'active'` 查询本页 active teacher memberships，不能按课程逐条查。
4. 取出去重后的 `userId`，使用一次 `usersService.listByIds(userIds)` 查询对应用户，不能按 membership 逐条查。
5. 按 `course_id -> memberships -> users` 组装教师摘要。
6. 只展示 `users.status = active` 的教师。
7. 无教师时返回空列表。

- [ ] **Step 4: 运行课程只读测试**

Run:

```bash
cd backend/ckqa-back
./mvnw -q -Dtest=CourseLookupServiceTest test
```

Expected: PASS

### Task 4: 实现课程创建命令服务

**Files:**
- Modify: `backend/ckqa-back/src/main/java/org/ysu/ckqaback/course/dto/CourseCreateRequest.java`
- Create: `backend/ckqa-back/src/main/java/org/ysu/ckqaback/course/CourseIdGenerator.java`
- Create: `backend/ckqa-back/src/main/java/org/ysu/ckqaback/course/CourseMembershipAuditWriter.java`
- Create: `backend/ckqa-back/src/main/java/org/ysu/ckqaback/course/CourseMembershipAuditEvent.java`
- Create: `backend/ckqa-back/src/main/java/org/ysu/ckqaback/course/CourseCommandService.java`
- Modify: `backend/ckqa-back/src/main/java/org/ysu/ckqaback/course/CourseLookupService.java`
- Modify: `backend/ckqa-back/src/main/java/org/ysu/ckqaback/controller/CoursesController.java`
- Modify: `backend/ckqa-back/src/main/java/org/ysu/ckqaback/api/ApiResultCode.java`

- [ ] **Step 1: 改创建请求 DTO**

删除 `courseId` 字段与校验，新增：

```java
@NotNull(message = "请选择授课教师")
@Positive(message = "teacherUserId必须大于0")
private Long teacherUserId;
```

- [ ] **Step 2: 实现 `CourseIdGenerator`**

规则：

1. `ZoneId.of("Asia/Shanghai")`
2. `DateTimeFormatter.BASIC_ISO_DATE`
3. alphabet 固定为 `abcdefghijklmnopqrstuvwxyz0123456789`
4. 随机串长度 6

公开方法：

```java
public String generate();
```

- [ ] **Step 3: 实现审计事件与 `CourseMembershipAuditWriter`**

新增事件对象：

```java
public record CourseMembershipAuditEvent(
        Long courseMembershipId,
        String courseId,
        Long teacherUserId,
        Long operatorUserId
) {
}
```

方法：

```java
@TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
public void writeInitialTeacherBoundEventSafely(CourseMembershipAuditEvent event);
```

行为：

1. 创建 `CourseMembershipEvents`。
2. `eventType = "initial_teacher_bound"`。
3. `newStatus = "active"`。
4. `operatorUserId` 允许为 null。
5. `changeReason = "COURSE_CREATION_INITIAL_TEACHER"`。
6. 捕获 `RuntimeException` 并记录 warn 日志，不重新抛出。
7. `fallbackExecution` 保持默认 `false`，没有事务提交时不写审计事件，避免记录未提交或已回滚的授权。

- [ ] **Step 4: 实现 `CourseCommandService.createCourse()`**

主流程：

1. 标准化 `courseName`、`description`、`status`、`accessPolicy`。
2. 校验教师存在。
3. 校验教师 `status = active`。
4. 校验教师拥有全局 `teacher` 角色。
5. 循环最多 5 次生成 `courseId`。
6. 每次生成后先查 `courses.course_id` 是否存在。
7. 插入 `courses`。
8. 插入 active teacher membership。
9. membership 保存成功后，通过 `ApplicationEventPublisher.publishEvent()` 发布 `CourseMembershipAuditEvent`。
10. 返回带教师摘要的 `CourseDetailResponse`。

事务要求：

```java
@Transactional(propagation = Propagation.REQUIRED)
public CourseDetailResponse createCourse(CourseCreateRequest request) {
    ...
}
```

事务边界说明：

1. `CourseCommandService.createCourse()` 是 controller 直接调用的写入边界，默认预期没有外层业务事务。
2. 如果未来被另一个事务方法调用，课程创建、membership 写入与审计事件会跟随外层事务；审计监听器只在最终事务提交后执行。
3. 不使用 `REQUIRES_NEW`，避免外层事务回滚时课程和 membership 已提前提交。
4. 不把审计写入放到 controller，因为审计事件需要持久化后的 membership ID，且不能为回滚事务写事件。

并发唯一键冲突处理：

1. 唯一键重试只包住 `coursesService.save(course)` 这一段。
2. 只有确认异常来自 `courses.uk_course_id` 时才重试下一次生成。
3. membership 保存阶段的 `DuplicateKeyException`、`DataIntegrityViolationException` 或其他运行时异常一律不重试，直接向外抛出，由事务回滚 `courses` 插入。
4. 5 次耗尽后抛出 409，消息为“课程标识生成冲突，请稍后重试”。
5. 非 `uk_course_id` 唯一键冲突按原异常抛出。

- [ ] **Step 5: 调整 controller 注入**

`CoursesController` 同时注入：

```java
private final CourseLookupService courseLookupService;
private final CourseCommandService courseCommandService;
```

`POST /courses` 改为调用 `courseCommandService.createCourse(request)`。

- [ ] **Step 6: 运行课程创建测试**

Run:

```bash
cd backend/ckqa-back
./mvnw -q -Dtest=CourseCommandServiceTest,CourseMembershipAuditWriterTest,CoursesControllerWebMvcTest test
```

Expected: PASS

### Task 5: 后端全量回归

**Files:** 后端 Phase 1 涉及的全部文件

- [ ] **Step 1: 运行完整后端测试**

Run:

```bash
cd backend/ckqa-back
./mvnw test
```

Expected: PASS

- [ ] **Step 2: 编译打包**

Run:

```bash
cd backend/ckqa-back
./mvnw -DskipTests package
```

Expected: PASS

---

## Phase 1: 管理端课程创建 UI

### Task 6: 写管理端失败测试锁定 UI 契约

**Files:**
- Modify: `frontend/apps/admin-app/src/app-shell.test.js`
- Modify: `frontend/apps/admin-app/e2e/local-operation-errors.spec.js`

- [ ] **Step 1: 更新 API 边界测试**

断言：

1. `createCourse()` payload 为 `{ courseName, teacherUserId, description, status, accessPolicy }`。
2. 新增 `listUsers({ roleCode: "teacher", status: "active", keyword: "zhang", page: 1, size: 20 })` 调用 `/users`。

- [ ] **Step 2: 更新表单结构测试**

断言：

1. 课程创建区域没有“课程 ID”输入。
2. 课程创建区域没有 `creationForm.courseId` 绑定。
3. 课程创建区域存在 `creationForm.teacherUserId` 的远程 `el-select`。
4. 教师选择器具备 `filterable`、`remote` 与 `remote-method`。
5. 课程描述仍使用 Element Plus textarea。

- [ ] **Step 3: 更新 loader 测试**

断言课程列表列头包含“授课教师”，并且“授课教师”位于“状态”之前：

```js
assert.equal(columns.includes('授课教师'), true)
assert.ok(columns.indexOf('授课教师') < columns.indexOf('状态'))
```

并覆盖：

1. 无教师显示“未绑定教师”。
2. 一名教师显示 `displayName`。
3. 多名教师显示第一名教师和“等 N 人”。

- [ ] **Step 4: 更新 E2E 错误态**

在现有 fault-injection 流程中增加：

1. 教师候选接口失败时新建课程提交禁用。
2. 教师候选为空时提示“暂无可用教师，请先创建或启用教师账号”。

- [ ] **Step 5: 运行管理端定向测试确认失败**

Run:

```bash
cd frontend/apps/admin-app
pnpm test
```

Expected: FAIL，失败点应集中在未新增 `users.js`、表单仍含 `courseId`、教师列未实现。

### Task 7: 实现管理端 API 与表单模型

**Files:**
- Create: `frontend/apps/admin-app/src/api/users.js`
- Modify: `frontend/apps/admin-app/src/views/pages/creation-form-model.js`
- Modify: `frontend/apps/admin-app/src/app-shell.test.js`

- [ ] **Step 1: 新增 `listUsers` API**

`frontend/apps/admin-app/src/api/client.js` 已存在 `unwrapApiResponse(response)` 与 `normalizePageData(data)`：

1. `unwrapApiResponse` 读取 CKQA 统一响应 `{ code, message, data, timestamp }`，只在 `code === 200` 时返回 `data`。
2. `normalizePageData` 兼容后端分页字段 `items/current/page/size/total/pages`，返回 `{ items, pagination, raw }`。
3. 本步骤只新增 `users.js`，不新建 client 工具。

实现：

```js
import { http } from '../axios/index.js'
import { normalizePageData, unwrapApiResponse } from './client.js'

export async function listUsers(params = {}, client = http) {
  return normalizePageData(unwrapApiResponse(await client.get('/users', { params })))
}
```

- [ ] **Step 2: 修改课程创建表单默认值**

`createCreationForm('course')` 返回：

```js
{
  courseName: '',
  teacherUserId: '',
  description: '',
  status: 'active',
  accessPolicy: 'restricted',
}
```

- [ ] **Step 3: 新增教师选项转换**

新增：

```js
export function resolveTeacherSelectOptions(users = []) {
  return users
    .map((user) => {
      const value = user.id ?? user.userId
      if (!value) return null
      const code = user.userCode ?? user.username ?? value
      const name = user.displayName ?? user.username ?? code
      return { value: Number(value), label: `${name}（${code}）` }
    })
    .filter(Boolean)
}
```

- [ ] **Step 4: 跑模型和 API 测试**

Run:

```bash
cd frontend/apps/admin-app
pnpm test
```

Expected: 与 API/model 相关测试 PASS，UI 结构测试仍可失败，等待 Task 8。

### Task 8: 实现管理端弹窗与课程列表教师展示

**Files:**
- Modify: `frontend/apps/admin-app/src/views/pages/ModulePage.vue`
- Modify: `frontend/apps/admin-app/src/views/pages/module-loaders.js`
- Modify: `frontend/apps/admin-app/src/views/pages/module-content.js`
- Modify: `frontend/apps/admin-app/e2e/local-operation-errors.spec.js`

- [ ] **Step 1: 增加教师候选状态**

`ModulePage.vue` 新增：

```js
const creationTeacherOptions = ref([])
const creationTeacherState = ref('idle')
const creationTeacherError = ref(null)
```

加载函数使用：

```js
await listUsers({
  roleCode: 'teacher',
  status: 'active',
  keyword,
  page: 1,
  size: 20,
})
```

- [ ] **Step 2: 打开课程弹窗时加载第一页教师**

`openCreationDialog('course')` 时调用 `loadCreationTeachers('')`。

知识库弹窗仍只加载课程候选，不受教师状态影响。

- [ ] **Step 3: 替换课程 ID 输入**

移除课程 ID 表单项，新增：

```vue
<el-form-item class="creation-field" label="授课教师" required>
  <el-select
    v-model="creationForm.teacherUserId"
    name="teacherUserId"
    filterable
    remote
    :remote-method="loadCreationTeachers"
    :loading="creationTeacherState === 'loading'"
    :disabled="creationTeacherState === 'loading' || creationTeacherState === 'failed'"
    placeholder="搜索教师姓名、用户名或工号"
  >
    <el-option
      v-for="option in creationTeacherOptions"
      :key="option.value"
      :label="option.label"
      :value="option.value"
    />
  </el-select>
</el-form-item>
```

- [ ] **Step 4: 调整提交禁用规则**

课程弹窗禁用条件增加：

1. `!creationForm.courseName.trim()`
2. `!creationForm.teacherUserId`
3. `creationTeacherState === 'loading'`
4. `creationTeacherState === 'failed'`
5. `creationTeacherState === 'empty'`

- [ ] **Step 5: 调整课程创建 payload**

提交 payload 改为：

```js
{
  courseName: creationForm.value.courseName.trim(),
  teacherUserId: creationForm.value.teacherUserId
    ? Number(creationForm.value.teacherUserId)
    : undefined,
  description: creationForm.value.description.trim() || undefined,
  status: creationForm.value.status,
  accessPolicy: creationForm.value.accessPolicy,
}
```

提交前必须已经通过 `teacherUserId` 前端禁用条件；这里仍保留 `undefined` 兜底，避免空字符串被 `Number('')` 转成 `0` 后触发不友好的 `@Positive` 错误。跳转目标只使用响应里的 `course.courseId`。响应缺失 `courseId` 时停留列表并显示接口契约错误。

- [ ] **Step 6: 增加课程列表教师列**

`COURSE_COLUMNS` 与 `module-content.js` 列配置改为：

```js
['课程', '授课教师', '状态', '资料进度', '知识库', '最近索引', '更新时间']
```

筛选列索引随之更新：

1. `status.columnIndex = 2`
2. `materialState.columnIndex = 3`
3. `indexState.columnIndex = 5`

`mapCourseRow()` 在课程名后插入 `createTeacherCell(course)`。

- [ ] **Step 7: 跑管理端测试与构建**

Run:

```bash
cd frontend/apps/admin-app
pnpm test
pnpm build
```

Expected: PASS

### Task 9: 管理端 E2E 回归

**Files:**
- Modify: `frontend/apps/admin-app/e2e/local-operation-errors.spec.js`

- [ ] **Step 1: 运行浏览器错误态测试**

Run:

```bash
cd frontend/apps/admin-app
pnpm test:e2e
```

Expected: PASS

- [ ] **Step 2: 检查生成物未进入工作区**

Run:

```bash
git status --short
```

Expected: 不出现 `frontend/apps/admin-app/test-results/`、`playwright-report/`、`dist/`。

---

## Phase 2: 旧课程补齐教师

### Task 10: 实现教师补齐迁移脚本

**Files:**
- Create: `scripts/normalize_course_teachers.py`
- Create: `docs/migrations/course_teacher_mapping.example.csv`
- Create: `pdf_ingest/tests/test_normalize_course_teachers.py`

- [ ] **Step 1: 写脚本单元测试**

覆盖：

1. CSV 必须包含 `course_id,teacher_user_code`。
2. 缺少 mapping 的旧课程在 dry-run 报错。
3. mapping 指向不存在的 `teacher_user_code` 时报错。
4. mapping 指向非 active teacher 时报错。
5. 已有 active teacher membership 的课程跳过。
6. `--fallback-teacher-user-code` 只在显式传入时生效。
7. `--granted-by-user-code` 指向不存在用户时中止。
8. `--granted-by-user-code` 指向非 active 用户时中止。
9. 未传 `--granted-by-user-code` 时报告包含 `actor_unavailable`。
10. apply 模式生成的 insert 参数包含 `COURSE_CREATION_INITIAL_TEACHER`。

- [ ] **Step 2: 实现 CLI**

参数：

```bash
python scripts/normalize_course_teachers.py \
  --mapping docs/migrations/course_teacher_mapping.csv \
  --dry-run

python scripts/normalize_course_teachers.py \
  --mapping docs/migrations/course_teacher_mapping.csv \
  --apply
```

支持：

1. `--fallback-teacher-user-code`
2. `--granted-by-user-code`
3. `--db-host`
4. `--db-port`
5. `--db-name`
6. `--db-user`
7. `--db-password`

DB 参数默认从环境变量读取：

1. `DB_HOST`
2. `DB_PORT`
3. `DB_NAME`
4. `DB_USER`
5. `DB_PASSWORD`

- [ ] **Step 3: dry-run 输出迁移报告**

报告字段：

1. `course_id`
2. `action`
3. `teacher_user_code`
4. `teacher_user_id`
5. `reason`

没有错误时 exit code 为 0；任何课程无法安全迁移时 exit code 为 1。

- [ ] **Step 4: apply 写入 membership**

写入字段：

1. `user_id`
2. `course_id`
3. `membership_role = 'teacher'`
4. `status = 'active'`
5. `access_source = 'manual'`
6. `joined_at = NOW()`
7. `effective_from = NOW()`
8. `granted_by_user_id` 优先使用 `--granted-by-user-code` 解析出的用户 ID；未传时写 `NULL`
9. `change_reason = 'COURSE_CREATION_INITIAL_TEACHER'`

dry-run 与 apply 报告必须包含授权人状态：

1. 传入 `--granted-by-user-code` 且用户存在时标记 `actor_resolved`。
2. 传入 `--granted-by-user-code` 但用户不存在时中止迁移。
3. 传入 `--granted-by-user-code` 但用户状态不是 `active` 时中止迁移，避免把停用用户写成授权人。
4. 未传 `--granted-by-user-code` 时标记 `actor_unavailable` 警告。

正式生产环境执行前必须准备真实授权人方案；脚本默认 NULL 只用于补齐历史数据，且不能静默写入。

- [ ] **Step 5: 运行脚本测试**

Run:

```bash
python -m pytest pdf_ingest/tests/test_normalize_course_teachers.py -q
```

Expected: PASS

- [ ] **Step 6: 准备示例映射文件**

`docs/migrations/course_teacher_mapping.example.csv` 内容：

```csv
course_id,teacher_user_code
os,TCH2026001
ds,TCH2026002
```

### Task 11: Phase 2 数据验证流程

**Files:**
- No code change

- [ ] **Step 1: dry-run**

Run:

```bash
python scripts/normalize_course_teachers.py \
  --mapping docs/migrations/course_teacher_mapping.csv \
  --dry-run
```

Expected: 输出每门待补齐课程的计划，且没有缺失 mapping、教师不存在、教师角色不合法错误。

- [ ] **Step 2: apply**

Run:

```bash
python scripts/normalize_course_teachers.py \
  --mapping docs/migrations/course_teacher_mapping.csv \
  --apply
```

Expected: 只为缺少 active teacher membership 的课程插入 membership，已有教师课程跳过。

- [ ] **Step 3: SQL 验证**

Run:

```sql
SELECT c.course_id
FROM courses c
LEFT JOIN course_memberships cm
  ON cm.course_id = c.course_id
 AND cm.membership_role = 'teacher'
 AND cm.status = 'active'
WHERE cm.id IS NULL;
```

Expected: 结果为空。

---

## Phase 3: 历史 course_id 规范化迁移门禁

### Task 12: 迁移窗口前置审计

**Files:**
- No code change in this implementation branch

进入条件：

1. Phase 1 已合并主干，并在目标环境连续稳定运行至少 5 个工作日。
2. 观察期内真实或运维演练的新建课程操作合计不少于 10 次，成功率 100%；如果真实业务量不足 10 次，需要由运维演练补足。
3. 观察期内新建课程、教师绑定、课程列表、课程详情没有 P0/P1 故障，且课程列表/详情 P99 响应时间不高于上线前基线的 150%。
4. Phase 2 已执行完成，且“缺少 active teacher membership 的课程”SQL 验证结果为空。
5. 本 Task 12 产出的审计报告已由后端/DB 负责人、GraphRAG 负责人和运维负责人 review 通过，并确认迁移维护窗口。

- [ ] **Step 1: 审计 DB 外键与无外键引用**

执行：

```sql
SHOW CREATE TABLE courses;
SHOW CREATE TABLE course_materials;
SHOW CREATE TABLE parse_results;
SHOW CREATE TABLE course_memberships;
SHOW CREATE TABLE knowledge_bases;
SHOW CREATE TABLE qa_sessions;
SHOW CREATE TABLE qa_retrieval_logs;
SHOW CREATE TABLE authorization_audit_logs;
```

必须记录：

1. `courses.course_id` 存在 `uk_course_id`。
2. `course_materials.course_id` 是否仍无外键。
3. `parse_results.course_id` 是否仍无外键。
4. 所有指向 `courses.course_id` 的外键名称与 `ON DELETE` / `ON UPDATE` 语义。

- [ ] **Step 2: 审计写入口**

迁移窗口前确认以下入口已切只读或维护态：

1. 课程资料上传。
2. 资料解析。
3. GraphRAG 导出。
4. 索引构建。
5. QA 任务创建。

确认 DB 中不存在仍会写旧 `course_id` 的 `pending`、`processing`、`running` 长任务。

- [ ] **Step 3: 试迁移一门低风险课程**

记录：

1. MinIO 对象 copy 耗时。
2. DB 更新耗时。
3. `fetch_from_minio.py` 耗时。
4. `graphrag index --root .` 耗时。
5. admin-app 课程详情、资料、知识库、构建向导、QA smoke 验证结果。

- [ ] **Step 4: 形成正式迁移执行单**

正式执行单必须包含：

1. old -> new 映射。
2. MySQL 备份点。
3. MinIO 对象清单。
4. GraphRAG 输入/输出目录备份。
5. 每张引用表旧 ID 行数。
6. 每张引用表新 ID 目标行数。
7. 回滚策略。
8. 迁移后删除旧 MinIO 前缀的时间点。

---

## 最终验证

### Task 13: 全量验证与工作区检查

**Files:** 本计划涉及的全部文件

- [ ] **Step 1: 后端全量测试**

Run:

```bash
cd backend/ckqa-back
./mvnw test
```

Expected: PASS

- [ ] **Step 2: 管理端单元测试与构建**

Run:

```bash
cd frontend/apps/admin-app
pnpm test
pnpm build
```

Expected: PASS

- [ ] **Step 3: 管理端 E2E**

Run:

```bash
cd frontend/apps/admin-app
pnpm test:e2e
```

Expected: PASS

- [ ] **Step 4: 迁移脚本测试**

Run:

```bash
python -m pytest pdf_ingest/tests/test_normalize_course_teachers.py -q
```

Expected: PASS

- [ ] **Step 5: 格式与生成物检查**

Run:

```bash
git diff --check
git status --short
```

Expected:

1. `git diff --check` 无输出。
2. 工作区不包含 `dist/`、`test-results/`、`playwright-report/`、`node_modules/`。
3. 变更只落在设计允许的后端、admin-app、迁移脚本与文档范围内。

## 实施顺序建议

1. 先提交本设计稿与实施计划作为基线。
2. 新建独立 feature 分支或 worktree 实施 Phase 1。
3. Phase 1 后端与管理端测试全部通过后，再执行 Phase 2 教师补齐脚本。
4. Phase 2 SQL 验证为空后，运行一次 admin-app 课程列表和课程详情人工冒烟。
5. Phase 3 只在新链路稳定后单独开迁移窗口，不与 Phase 1 代码提交混在一起。

## 验收标准

1. 管理端新建课程弹窗没有可编辑课程 ID。
2. 管理端新建课程必须选择教师才能提交。
3. 教师候选接口支持 `roleCode=teacher`、`status=active`、`keyword`，空 keyword 按 `user_code ASC, id ASC` 返回第一页。
4. 创建课程请求不包含 `courseId`，包含 `teacherUserId`。
5. 后端拒绝不存在、停用、非 teacher 角色的用户。
6. 后端生成 `crs-YYYYMMDD-XXXXXX` 格式课程 ID，并由 `uk_course_id` 兜底唯一性。
7. `courses` 与初始教师 `course_memberships` 在同一事务写入。
8. `course_membership_events` 写入失败不回滚课程创建。
9. 课程列表和详情响应包含 `teachers` 与 `teacherCount`。
10. 旧课程 Phase 2 迁移后全部具备 active teacher membership。
11. Phase 3 执行前已完成 DB 外键、无外键引用、MinIO、GraphRAG 重建成本审计。
