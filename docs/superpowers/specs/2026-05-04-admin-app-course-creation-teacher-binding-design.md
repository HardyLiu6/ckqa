# 管理端课程创建与教师绑定设计

日期：2026-05-04
范围：`frontend/apps/admin-app/`、`backend/ckqa-back/`、`pdf_ingest/sql/ocqa.sql` 与后续数据迁移脚本

## 1. 背景

当前管理端“新建课程”已经接入 Java `/api/v1/courses`，但创建表单仍要求用户填写 `courseId`。后端 `CourseCreateRequest` 也把 `courseId` 作为必填字段，并在 `CourseLookupService.createCourse()` 中直接写入 `courses.course_id`。

这与新的课程体系目标不一致：

1. 一门课程不能没有教师。
2. `courseId` 是系统内跨模块稳定标识，不应由普通用户手填。
3. 前端本轮修改范围限定在管理员端，不改学生端。
4. 新课程体系跑通后，需要对现有数据完成迁移和规范化，避免系统长期混用“用户手填 ID”和“系统生成 ID”。

现有数据模型已经具备承载该设计的基础：

1. `courses.id` 是数据库自增主键。
2. `courses.course_id` 是当前跨模块业务标识，被资料、知识库、问答、授权审计等表引用。
3. `course_memberships` 已存在 `membership_role enum('student','teacher','assistant')`，可以表达课程内教师关系。
4. 种子数据已包含全局 `teacher` 角色和一组教师用户。

## 2. 外部系统参考

本设计借鉴三个方向，但不照搬完整教务模型：

1. Canvas 将对象内部 ID 和 SIS 外部标识分开，API 可以通过内部 ID 或 `sis_course_id:` 形式引用课程。参考：https://developerdocs.instructure.com/services/canvas/basics/file.object_ids
2. Canvas 的课程人员关系通过 enrollment 表达，角色包含学生、教师、TA 等，而不是把教师直接固化为课程主表字段。参考：https://developerdocs.instructure.com/services/canvas/resources/enrollments
3. Moodle 区分课程全名、短名和 Course ID number，其中外部编号更多用于外部系统匹配，通常不作为学生主要可见信息。参考：https://docs.moodle.org/32/en/Course_settings

技术标识生成参考：

1. MySQL `AUTO_INCREMENT` 用于系统生成行身份。参考：https://dev.mysql.com/doc/refman/8.0/en/example-auto-increment.html
2. UUIDv7 提供按时间排序的全局唯一标识思路，但本项目首版不需要完整 UUID 长度。参考：https://www.rfc-editor.org/rfc/rfc9562.html

## 3. 设计目标

1. 管理端新建课程不再出现可编辑 `courseId` 输入框。
2. 管理端新建课程必须选择一名有效教师。
3. 后端创建课程时自动生成稳定、URL 安全、可读的 `courseId`。
4. 课程创建和教师 membership 写入必须在同一事务中完成。
5. 新课程创建成功后，课程详情、课程列表和后续资料/知识库/问答流程继续使用生成后的 `courseId`。
6. 现有旧课程数据先补齐教师关系，再在新体系验证稳定后执行课程标识规范化迁移。
7. 本轮前端实现只改 `frontend/apps/admin-app/`，不修改 `student-app`。

## 4. 非目标

1. 不引入完整教务系统模型，例如课程目录、学期、教学班、行政班、选课批次。
2. 不在首版支持新建课程时选择多个教师。首版只选择一名“初始教师”，后续通过课程成员管理扩展多教师/助教。
3. 不把教师直接加到 `courses` 主表作为唯一事实来源。
4. 不把浏览器正式流程直接接到 GraphRAG Python `/v1`，仍以 Java `/api/v1` 为正式边界。
5. 不改学生端界面和学生端请求层。

## 5. 课程标识体系

### 5.1 标识定义

| 标识 | 存储位置 | 生成方式 | 用途 |
| --- | --- | --- | --- |
| 数据库主键 | `courses.id` | MySQL 自增 | 内部行身份，不给用户填写 |
| 系统课程标识 | `courses.course_id` / API `courseId` | 后端生成 | 跨模块引用、URL、资料/知识库/问答关联 |
| 课程名称 | `courses.course_name` / API `courseName` | 用户填写 | 主要展示名称 |
| 外部课程编号 | 暂不新增字段 | 未来可选扩展 | 对接教务系统或人工课程目录编号 |

首版继续沿用现有 `courses.course_id` 字段承载系统课程标识，避免立即重构所有下游表。

### 5.2 新 `courseId` 生成规范

推荐格式：

```text
crs-YYYYMMDD-XXXXXX
```

示例：

```text
crs-20260504-7f3k2a
```

规则：

1. `crs`：固定前缀，表示 course。
2. `YYYYMMDD`：创建日期，使用后端业务时间；CKQA 后端当前按 Asia/Shanghai 口径处理跨服务业务时间。
3. `XXXXXX`：6 位小写 base32/base36 随机串，只包含 `a-z0-9`。
4. 总长度 19，满足当前 `varchar(64)` 与前端 URL 编码需求。
5. 后端生成后必须查询 `courses.course_id` 唯一性，冲突时重试，最多 5 次。
6. 若 5 次仍冲突，返回明确业务错误，提示稍后重试。

不建议使用课程名称拼音、教师姓名、专业简称来生成 `courseId`，因为这些字段会变化、存在重名，也容易让用户误以为可编辑。

## 6. 课程与教师关系

### 6.1 事实来源

课程教师关系以 `course_memberships` 为事实来源：

```text
course_memberships.course_id = courses.course_id
course_memberships.user_id = users.id
course_memberships.membership_role = 'teacher'
course_memberships.status = 'active'
```

`courses` 主表不新增 `teacher_id`。课程详情/列表需要展示教师时，通过 `course_memberships -> users` 查询当前 active teacher。

### 6.2 初始教师

新建课程时选择的教师称为“初始教师”。

初始教师写入一条 active membership：

| 字段 | 值 |
| --- | --- |
| `user_id` | 表单选择的教师用户 ID |
| `course_id` | 后端生成的课程标识 |
| `membership_role` | `teacher` |
| `status` | `active` |
| `access_source` | `manual` |
| `joined_at` | 当前时间 |
| `effective_from` | 当前时间 |
| `granted_by_user_id` | 当前认证用户可用时写入；开发态未接真实用户上下文时可为空 |
| `change_reason` | `课程创建时绑定初始教师` |

后续增加第二名教师或助教时，仍使用 `course_memberships`，不改变课程主表。

## 7. 后端 API 设计

### 7.1 教师候选查询

推荐扩展现有用户列表接口：

```http
GET /api/v1/users?roleCode=teacher&status=active&page=1&size=200
```

原因：

1. 教师本质是拥有全局 `teacher` 角色的用户。
2. 复用现有 `/api/v1/users` 分页契约，减少新端点数量。
3. 管理端未来在用户管理、角色管理、课程成员管理里也可复用 `roleCode` 过滤。

实现要求：

1. `UserQueryRequest` 增加 `roleCode`，允许 `student|teacher|admin`。
2. `UsersService.pageUsers()` 在 `roleCode` 非空时通过 `user_roles` + `roles` 过滤。
3. 默认行为不变；不传 `roleCode` 时仍返回原有用户分页。

### 7.2 创建课程请求

当前请求：

```json
{
  "courseId": "db",
  "courseName": "数据库系统",
  "description": "数据库课程资料",
  "status": "active",
  "accessPolicy": "restricted"
}
```

调整为：

```json
{
  "courseName": "数据库系统",
  "teacherUserId": 8,
  "description": "数据库课程资料",
  "status": "active",
  "accessPolicy": "restricted"
}
```

字段规则：

| 字段 | 是否必填 | 规则 |
| --- | --- | --- |
| `courseName` | 是 | 非空，最长 255 |
| `teacherUserId` | 是 | 必须存在、状态为 active、拥有全局 teacher 角色 |
| `description` | 否 | 最长 2000 |
| `status` | 否 | `active|inactive|archived`，默认 `active` |
| `accessPolicy` | 否 | `restricted|public`，默认 `restricted` |

`courseId` 从请求 DTO 中移除，或保留为只读兼容字段但忽略输入。首选直接移除，避免前端和外部调用继续依赖手填能力。

### 7.3 创建课程响应

响应继续返回生成后的 `courseId`，并增加教师摘要：

```json
{
  "id": 12,
  "courseId": "crs-20260504-7f3k2a",
  "courseName": "数据库系统",
  "description": "数据库课程资料",
  "status": "active",
  "accessPolicy": "restricted",
  "teachers": [
    {
      "userId": 8,
      "userCode": "TCH2026001",
      "username": "teacher.zhangwb",
      "displayName": "张文博"
    }
  ],
  "teacherCount": 1,
  "materialCount": 0,
  "knowledgeBaseCount": 0,
  "createdAt": "2026-05-04T10:30:00",
  "updatedAt": "2026-05-04T10:30:00"
}
```

列表页可只展示第一名教师与教师数量；详情页展示完整教师列表。

### 7.4 后端事务流程

`CourseLookupService.createCourse()` 调整为事务方法：

1. 校验 `courseName`。
2. 校验 `teacherUserId`：
   - 用户存在。
   - `users.status = active`。
   - 用户拥有全局 `teacher` 角色。
3. 生成 `courseId`。
4. 插入 `courses`。
5. 插入 `course_memberships` 初始教师关系。
6. 可选插入 `course_membership_events`，记录创建时授权事件。
7. 返回课程详情响应。

如果第 5 步失败，课程插入必须回滚，避免出现“没有教师的课程”。

### 7.5 错误处理

| 场景 | HTTP 状态 | 业务消息 |
| --- | --- | --- |
| 未选择教师 | 400 | 请选择授课教师 |
| 教师不存在 | 404 | 教师用户不存在 |
| 用户不是教师角色 | 400 | 选择的用户不是教师 |
| 教师已停用 | 400 | 选择的教师不可用 |
| `courseId` 生成冲突耗尽重试 | 409 | 课程标识生成冲突，请稍后重试 |
| membership 写入失败 | 500 或现有统一异常 | 课程创建失败，请稍后重试 |

继续使用现有 `ApiResponse` 包装与业务成功码 `200`。

## 8. 管理端前端设计

### 8.1 范围

只修改 `frontend/apps/admin-app/`：

1. `ModulePage.vue`
2. `creation-form-model.js`
3. `module-content.js`
4. `module-loaders.js` 或现有 API service 文件
5. `src/api/users.js` 如当前没有用户 API 文件则新增
6. `app-shell.test.js` 与相关 E2E 测试

不修改 `frontend/apps/student-app/`。

### 8.2 新建课程弹窗

字段调整：

1. 移除“课程 ID”输入框。
2. 新增“授课教师”下拉选择，必填。
3. 保留“课程名称”“访问策略”“课程状态”“课程描述”。
4. 在表单底部或创建成功后的详情页展示系统生成的课程标识，只读展示，不允许编辑。

建议字段顺序：

1. 课程名称
2. 授课教师
3. 访问策略
4. 课程状态
5. 课程描述

### 8.3 教师选择器状态

打开新建课程弹窗时加载教师候选：

```http
GET /api/v1/users?roleCode=teacher&status=active&page=1&size=200
```

前端状态：

| 状态 | 表现 |
| --- | --- |
| loading | 教师下拉禁用，提示“正在加载教师” |
| success | 可选择教师 |
| empty | 禁用提交，提示“暂无可用教师，请先创建或启用教师账号” |
| failed | 禁用提交，展示接口错误，可重试 |

提交按钮禁用条件：

1. 正在提交。
2. `courseName` 为空。
3. `teacherUserId` 为空。
4. 教师候选加载中或加载失败。

### 8.4 课程列表与详情展示

课程列表建议增加“授课教师”列，放在“状态”前后均可：

```text
课程 | 授课教师 | 状态 | 资料进度 | 知识库 | 最近索引 | 更新时间
```

展示规则：

1. 无教师：显示“未绑定教师”，并给出警示状态。该情况只应存在于旧数据迁移前。
2. 一名教师：显示教师展示名。
3. 多名教师：显示第一名教师 + “等 N 人”。

课程详情页的课程成员区应继续指向成员管理，不在本次直接做完整成员编辑。

## 9. 现有数据迁移与规范化

迁移拆成两个阶段，避免直接改 `course_id` 导致资料、知识库、问答链路断裂。

### 9.1 阶段一：补齐教师关系

目标：让所有现有课程满足“至少一名 active teacher membership”。

建议新增迁移脚本，支持命令行参数：

```bash
python scripts/normalize_course_teachers.py \
  --mapping docs/migrations/course_teacher_mapping.csv \
  --dry-run

python scripts/normalize_course_teachers.py \
  --mapping docs/migrations/course_teacher_mapping.csv \
  --apply
```

映射文件格式：

```csv
course_id,teacher_user_code
os,TCH2026001
ds,TCH2026002
```

脚本规则：

1. 先读取所有 `courses`。
2. 查询每门课是否已有 `course_memberships.status='active'` 且 `membership_role='teacher'`。
3. 已有教师的课程跳过。
4. 缺少教师的课程按 mapping 找教师。
5. mapping 缺失时 dry-run 明确报出，不自动猜测真实业务教师。
6. 对开发种子数据可提供 `--fallback-teacher-user-code TCH2026001`，但正式数据迁移不默认启用 fallback。
7. 写入 membership 后输出迁移报告。

该阶段不改变 `course_id`，风险最低，应先实施并验证。

### 9.2 阶段二：课程标识规范化

目标：将历史手填 `course_id` 迁移为系统生成规范，例如把 `os` 迁移为 `crs-20260504-7f3k2a`。

该阶段必须在新课程创建链路稳定后执行，且需要维护 old -> new 映射。

建议新增迁移映射表或迁移报告文件：

```text
old_course_id,new_course_id,status,migrated_at
os,crs-20260504-7f3k2a,planned,
```

需要同步更新的数据库位置：

1. `courses.course_id`
2. `course_materials.course_id`
3. `parse_results.course_id`
4. `course_memberships.course_id`
5. `knowledge_bases.course_id`
6. `qa_sessions.course_id`
7. `qa_retrieval_logs.course_id`
8. `authorization_audit_logs.target_course_id`
9. 其他通过审计脚本检出的 `course_id` 字段

需要同步处理的外部对象：

1. `course-artifacts` 中以旧 `course_id/` 开头的 MinIO 对象。
2. 兼容旧上传路径的 `course-pdfs` 中以旧 `course_id/` 开头的对象。
3. 本地临时输出目录和 GraphRAG 输入目录中以旧课程 ID 命名的目录。
4. 已构建 GraphRAG 索引中的 metadata。如果索引输入已经写入旧 `course_id`，需要重新 `fetch_from_minio` 并重建索引，不能只改 MySQL。

迁移步骤：

1. 停止课程资料解析、GraphRAG 导出、索引构建、问答任务等写入型流程。
2. 备份 MySQL、MinIO 相关对象列表和 GraphRAG 输入/输出目录。
3. dry-run 扫描所有旧课程 ID，生成 old -> new 映射并检查冲突。
4. 复制 MinIO 对象到新前缀，先不删除旧对象。
5. 对 MySQL 执行受控迁移：
   - 记录所有涉及 `course_id` 的表和行数。
   - 在迁移窗口内显式删除并重建指向 `courses.course_id` 的外键，不依赖静默的全局 `FOREIGN_KEY_CHECKS=0`。
   - 当前已知外键包括 `fk_course_memberships_course`、`fk_knowledge_bases_course`、`fk_sessions_course`、`fk_retrieval_logs_course`、`fk_auth_audit_course`。
   - 外键删除后按审计清单更新所有引用字段，再以原有 `ON UPDATE RESTRICT` 语义重建外键。
   - 每张表更新后校验旧 ID 行数是否归零、新 ID 行数是否匹配。
6. 重新拉取 GraphRAG 输入并重建索引。
7. 启动 Java 后端和 admin-app，验证课程列表、课程详情、资料列表、知识库列表、构建向导、QA 冒烟验证。
8. 验证通过后，再删除旧 MinIO 前缀和旧本地目录。

该阶段建议单独做实施计划，不和课程创建功能同一个提交混在一起。

### 9.3 兼容策略

阶段二完成前：

1. 新课程全部使用系统生成 `courseId`。
2. 旧课程继续可读、可操作。
3. 课程列表可对旧 ID 显示只读标识，不要求用户修改。
4. 迁移报告中标记哪些课程仍是 legacy ID。

阶段二完成后：

1. 所有 `courses.course_id` 均应匹配新规范。
2. 新建课程接口不接受外部传入 `courseId`。
3. 文档和测试中不再把 `os`、`ds` 作为推荐新建课程 ID，只作为旧数据或示例课程名称出现。

## 10. 测试与验证

### 10.1 后端测试

新增或调整测试：

1. `CourseLookupServiceTest`
   - 创建课程时不需要 request.courseId。
   - 自动生成的 `courseId` 匹配 `crs-YYYYMMDD-XXXXXX`。
   - 创建课程同时写入 active teacher membership。
   - 选择非教师用户时报错。
   - 选择停用教师时报错。
2. `CoursesControllerWebMvcTest`
   - POST `/api/v1/courses` 请求体不含 `courseId`。
   - 响应含生成后的 `courseId` 和教师摘要。
3. `UsersControllerWebMvcTest`
   - `roleCode=teacher` 能过滤教师候选。
   - 不传 `roleCode` 的原有用户列表行为保持不变。

建议后端验证命令：

```bash
cd backend/ckqa-back
./mvnw test
```

### 10.2 管理端测试

新增或调整测试：

1. 新建课程弹窗不渲染可编辑课程 ID 输入。
2. 新建课程弹窗打开时加载教师候选。
3. 教师候选为空时禁用提交并显示空态。
4. 成功提交时 payload 不包含 `courseId`，包含 `teacherUserId`。
5. 成功后跳转到后端返回的生成 `courseId` 详情页。
6. 课程列表能展示教师列。

建议前端验证命令：

```bash
cd frontend/apps/admin-app
pnpm test
pnpm build
pnpm test:e2e
```

### 10.3 数据迁移验证

阶段一验证：

```sql
SELECT c.course_id
FROM courses c
LEFT JOIN course_memberships cm
  ON cm.course_id = c.course_id
 AND cm.membership_role = 'teacher'
 AND cm.status = 'active'
WHERE cm.id IS NULL;
```

结果应为空。

阶段二验证：

1. 所有 `courses.course_id` 匹配 `^crs-[0-9]{8}-[a-z0-9]{6}$`。
2. 所有引用表不存在旧 `course_id`。
3. MinIO 新前缀对象数量与旧前缀迁移前清单一致。
4. GraphRAG 输入 JSON 中 metadata 的 `course_id` 已更新。
5. admin-app 课程详情、资料、知识库、构建向导、QA 冒烟验证均可访问。

## 11. 实施分期建议

### Phase 1：新建课程链路

1. 后端 DTO/API 调整。
2. 后端 `courseId` 生成器与事务创建。
3. 后端教师候选过滤。
4. admin-app 新建课程弹窗调整。
5. admin-app 课程列表教师展示。
6. 单元测试、构建测试和必要 E2E。

### Phase 2：旧课程补齐教师

1. 迁移脚本 dry-run。
2. 准备课程 -> 教师映射。
3. apply 写入缺失 teacher membership。
4. SQL 验证所有课程已有教师。

### Phase 3：历史 `course_id` 规范化

1. 生成 old -> new 映射。
2. 迁移 MinIO 和 DB 引用。
3. 重建 GraphRAG 输入和索引。
4. 全链路回归。
5. 清理旧前缀和旧示例文档。

## 12. 风险与控制

| 风险 | 控制 |
| --- | --- |
| 创建课程后 membership 写入失败导致无教师课程 | 使用事务包住课程和 membership 创建 |
| 教师候选误选学生或管理员 | 后端校验全局 `teacher` 角色，前端过滤只是辅助 |
| 旧 `course_id` 改名破坏资料/知识库/问答链路 | 标识规范化单独分期，先 dry-run 和备份，再迁移 |
| MinIO 对象前缀迁移后 GraphRAG 拉取失败 | 复制后先验证新前缀，不立即删除旧前缀 |
| 已构建索引仍带旧 metadata | 阶段二必须重新拉取输入并重建索引 |
| 前端误改学生端 | 本设计明确只改 admin-app |

## 13. 验收标准

1. 管理端新建课程表单没有可编辑课程 ID。
2. 管理端新建课程必须选择教师，否则不能提交。
3. 后端拒绝非教师用户作为初始教师。
4. 创建成功后 `courses` 有新课程，`course_memberships` 有对应 active teacher。
5. 返回的 `courseId` 符合 `crs-YYYYMMDD-XXXXXX`。
6. 课程列表和详情能展示教师信息。
7. 旧课程在阶段一迁移后全部具备 active teacher membership。
8. 阶段二迁移完成后，不再有旧格式 `course_id` 残留在 DB、MinIO 前缀和 GraphRAG 输入 metadata 中。
9. `./mvnw test`、`pnpm test`、`pnpm build` 通过；涉及浏览器流程时 `pnpm test:e2e` 通过。

## 14. 自查结论

本设计没有要求修改学生端；没有引入独立教师表；没有把教师字段固化进 `courses` 主表；没有把旧数据迁移和新建课程功能混成一次性高风险改动。旧数据规范化被明确拆成补齐教师关系与课程标识改名两个阶段，后者需要单独迁移窗口和全链路验证。
